package org.fossify.gallery.helpers.operations

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.copyOrMoveEncryptedFileToDirectory
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.isEncryptedFolder
import org.fossify.gallery.helpers.purgeMediaTracesPreservingReferences
import org.fossify.gallery.helpers.secureWipeAndDeleteFile
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.models.PendingOperation
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ADR 0003 §7/§9 — process-death recovery.
 *
 * Called at app start (and after vault unlock for encrypted operations). It removes orphan staging
 * files, resets interrupted items to PENDING so they are rewritten from their intact source, and
 * offers one dialog to continue every interrupted operation.
 */
object OperationRecovery {

    @Volatile
    private var reconciled = false

    @Volatile
    private var promptShown = false

    private val promptCheckInFlight = AtomicBoolean(false)

    /** Test/debug only: allow a fresh reconciliation pass in the same process. */
    fun resetForTests() {
        reconciled = false
        promptShown = false
        promptCheckInFlight.set(false)
    }

    /** Number of interrupted operations still needing a user decision. */
    fun reconcile(context: Context): Int {
        if (ForegroundOperationRegistry.isActive) return 0
        if (!reconciled) {
            reconciled = true
            cleanupOrphanStaging(context)
            // Fix MediaStore/Room/manifest references of items that were already committed before
            // the process died. Guarded internally so each operation is processed at most once.
            runCatching { MetadataReconciler.reconcile(context.applicationContext) }
            OperationJournal.activeItems().forEach { item ->
                if (ForegroundOperationRegistry.isActive) return 0
                if (item.state != OperationJournal.STATE_PENDING) {
                    val ctx = OperationContext(
                        operationId = item.operationId,
                        itemIndex = item.itemIndex,
                        kind = item.kind,
                        batch = OperationJournal.decodeBatch(item.batchPayload)
                    )
                    OperationJournal.resetToPending(ctx)
                }
            }
        }

        // Prune operations whose items are all terminal (e.g. completed COPYs or FAILED batches)
        // so stale rows never trigger a false "interrupted operation" dialog.
        val grouped = OperationJournal.allItems().groupBy { it.operationId }
        var resumableOpCount = 0
        grouped.forEach { (operationId, items) ->
            if (operationId.isBlank()) return@forEach
            val batch = OperationJournal.decodeBatch(items.first().batchPayload)
            if (items.any { OperationJournal.needsResume(it, batch) }) {
                resumableOpCount++
            } else {
                OperationJournal.clear(operationId)
            }
        }
        return resumableOpCount
    }

    /**
     * Staging files are never committed under the final name, so every `.stage_*` file found after
     * a process death is garbage. Ciphertext files can be deleted normally; anything that does not
     * start with the vault magic may hold plaintext and is securely wiped (ADR 0002 §7).
     */
    fun cleanupOrphanStaging(context: Context) {
        if (ForegroundOperationRegistry.isActive) return
        val roots = LinkedHashSet<File>()
        OperationJournal.allItems().forEach { item ->
            File(item.destPath).parentFile?.let { roots.add(it) }
            if (item.stagingPath.isNotBlank() && !item.stagingPath.startsWith("content://")) {
                File(item.stagingPath).parentFile?.let { roots.add(it) }
            }
            // SAF staging documents are recorded as content:// URIs in stagingPath.
            if (item.stagingPath.startsWith("content://")) {
                runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(item.stagingPath))?.delete()
                }
            }
        }
        runCatching { File(context.internalStoragePath) }.getOrNull()?.let { roots.add(it) }
        runCatching { CryptoManager.findVaultRoot(context) }.getOrNull()?.let { roots.add(it) }

        roots.forEach { root ->
            if (ForegroundOperationRegistry.isActive) return
            if (!root.exists() || !root.isDirectory) return@forEach
            runCatching {
                root.walkTopDown()
                    .maxDepth(2)
                    .filter { it.isFile && AtomicStaging.isStagingFile(it.name) }
                    .forEach { staging ->
                        AtomicStaging.discard(
                            staging = staging,
                            containsPlaintext = !isCiphertext(staging),
                            context = context.applicationContext
                        )
                    }
            }
        }
    }

    private fun isCiphertext(file: File): Boolean = runCatching {
        val magic = ByteArray(8)
        RandomAccessFile(file, "r").use { it.readFully(magic) }
        String(magic, Charsets.US_ASCII) == "FGVLTENC"
    }.getOrDefault(false)

    /** Continue every interrupted operation, one item at a time. */
    fun resume(context: Context, onDone: (Boolean) -> Unit) {
        resume(context, onProgress = null, onDone = onDone)
    }

    fun resume(
        context: Context,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onDone: (Boolean) -> Unit
    ) {
        val items = OperationJournal.resumableItems()
        if (items.isEmpty()) {
            onDone(true)
            return
        }

        val acquired = ForegroundOperationRegistry.acquire(context, "resume")
        if (!acquired) {
            onDone(false)
            return
        }
        var allSucceeded = true
        var anyCryptoTouched = false
        var processed = 0
        val total = items.size
        try {
            items.groupBy { it.operationId }.forEach { (operationId, operationItems) ->
                if (ForegroundOperationRegistry.isCancelled) {
                    allSucceeded = false
                    return@forEach
                }
                val batch = OperationJournal.decodeBatch(operationItems.first().batchPayload)
                var opCryptoTouched = false
                operationItems.sortedBy { it.itemIndex }.forEach { item ->
                    if (ForegroundOperationRegistry.isCancelled) {
                        allSucceeded = false
                        return@forEach
                    }
                    if (!OperationJournal.needsResume(item, batch)) return@forEach
                    if (item.kind == OperationJournal.KIND_ENCRYPT ||
                        item.kind == OperationJournal.KIND_DECRYPT ||
                        item.destPath.endsWith(".bin") ||
                        item.sourcePath.endsWith(".bin")
                    ) {
                        opCryptoTouched = true
                        anyCryptoTouched = true
                    }
                    val ctx = OperationContext(operationId, item.itemIndex, item.kind, batch)
                    val succeeded = try {
                        resumeOne(context, item, ctx, batch)
                    } catch (t: Throwable) {
                        OperationJournal.markFailed(ctx, t.message ?: t::class.java.simpleName)
                        false
                    }
                    if (!succeeded) allSucceeded = false
                    processed++
                    onProgress?.invoke(processed, total)
                }

                if (opCryptoTouched) {
                    finalizeAlbumRootsIfEmpty(context, batch)
                }
                runCatching { MetadataReconciler.reconcile(context.applicationContext) }

                val remaining = OperationJournal.itemsFor(operationId)
                if (remaining.none { OperationJournal.needsResume(it, batch) }) {
                    OperationJournal.clear(operationId)
                }
            }
            if (anyCryptoTouched) {
                runCatching { CryptoManager.clearAppImageCaches(context) }
            }
        } finally {
            ForegroundOperationRegistry.release(context, "resume")
        }
        onDone(allSucceeded)
    }

    private fun finalizeAlbumRootsIfEmpty(context: Context, batch: BatchOperationInfo?) {
        val sourceRoot = batch?.sourceRoot?.takeIf { it.isNotBlank() } ?: return
        if (!batch.isMove && !batch.wipeSource && !batch.deleteSource) return
        val sourceDir = File(sourceRoot)
        if (!sourceDir.exists() || !sourceDir.isDirectory) return
        runCatching {
            if (CryptoManager.isEncryptedFolder(sourceDir)) {
                CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(context, sourceDir)
            } else {
                (context as? BaseSimpleActivity)?.cleanupEmptyPlaintextAlbumIfNeeded(sourceDir, forceIfEmpty = false)
            }
        }
    }

    private fun resumeOne(
        context: Context,
        item: PendingOperation,
        ctx: OperationContext,
        batch: BatchOperationInfo?
    ): Boolean {
        val source = File(item.sourcePath)
        val dest = File(item.destPath)
        val destDir = dest.parentFile ?: return false

        // An item committed before death whose source still needs deletion/wiping.
        if (item.state == OperationJournal.STATE_VERIFIED && OperationJournal.requiresSourceDeletion(item, batch)) {
            if (dest.exists() && (!dest.name.endsWith(".bin") || CryptoManager.verifyFileIntegrity(dest))) {
                return completePendingSourceDeletion(context, item, source, dest, ctx)
            }
            if (!source.exists()) {
                OperationJournal.markFailed(ctx, "missing source and destination")
                return false
            }
            // Output was lost (e.g. partial rollback): reset and rewrite from intact source.
            OperationJournal.resetToPending(ctx)
        }

        if (!source.exists()) {
            // If rename fast-path succeeded right before death, destination already landed.
            if (item.destPath != item.sourcePath && dest.exists() &&
                (!dest.name.endsWith(".bin") || CryptoManager.verifyFileIntegrity(dest))
            ) {
                OperationJournal.markVerified(ctx, MediaIdentity.computeFingerprint(dest), dest.absolutePath)
                if (OperationJournal.requiresSourceDeletion(item, batch)) {
                    OperationJournal.markSourceDeleted(ctx)
                }
                return true
            }
            OperationJournal.markFailed(ctx, "source missing")
            return false
        }

        if (source.isDirectory) {
            if (item.kind == OperationJournal.KIND_COPY || item.kind == OperationJournal.KIND_MOVE) {
                if (!dest.exists() && !dest.mkdirs()) {
                    OperationJournal.markFailed(ctx, "could not create folder")
                    return false
                }
                OperationJournal.markVerified(ctx, "", dest.absolutePath)
                if ((batch?.isMove == true || item.kind == OperationJournal.KIND_MOVE) && source.list().isNullOrEmpty()) {
                    source.delete()
                    OperationJournal.markSourceDeleted(ctx)
                }
                return true
            }
            OperationJournal.markSkipped(ctx)
            return true
        }

        val conflictResolution = item.batchConflictResolution.toIntOrNull() ?: CONFLICT_KEEP_BOTH
        if (conflictResolution == CONFLICT_SKIP) {
            OperationJournal.markSkipped(ctx)
            return true
        }

        return when (item.kind) {
            OperationJournal.KIND_ENCRYPT -> {
                if (CryptoManager.isEncryptedFile(source.name)) {
                    CryptoManager.copyOrMoveEncryptedFileToDirectory(
                        context = context,
                        sourceBin = source,
                        targetDir = destDir,
                        isMove = batch?.isMove ?: true,
                        conflictResolution = conflictResolution,
                        skipDuplicateCheck = false,
                        operation = ctx
                    ) != null
                } else {
                    CryptoManager.encryptFileToDirectory(
                        context = context,
                        sourceFile = source,
                        targetDir = destDir,
                        wipeSource = batch?.wipeSource ?: true,
                        conflictResolution = conflictResolution,
                        skipDuplicateCheck = false,
                        deleteSourceOnIdentical = batch?.wipeSource ?: true,
                        operation = ctx
                    ) != null
                }
            }

            OperationJournal.KIND_DECRYPT -> CryptoManager.decryptFileToDirectory(
                context = context,
                encryptedFile = source,
                targetDir = destDir,
                deleteEncryptedSource = batch?.deleteSource ?: true,
                conflictResolution = conflictResolution,
                skipDuplicateCheck = false,
                deleteSourceOnIdentical = batch?.deleteSource ?: true,
                operation = ctx
            ) != null

            OperationJournal.KIND_COPY, OperationJournal.KIND_MOVE -> {
                if (source.name.endsWith(".bin") || dest.name.endsWith(".bin")) {
                    CryptoManager.copyOrMoveEncryptedFileToDirectory(
                        context = context,
                        sourceBin = source,
                        targetDir = destDir,
                        isMove = item.kind == OperationJournal.KIND_MOVE || batch?.isMove == true,
                        conflictResolution = conflictResolution,
                        skipDuplicateCheck = false,
                        operation = ctx
                    ) != null
                } else {
                    resumePlainCopy(context, item, ctx, batch)
                }
            }

            else -> false
        }
    }

    private fun completePendingSourceDeletion(
        context: Context,
        item: PendingOperation,
        source: File,
        dest: File,
        ctx: OperationContext
    ): Boolean {
        if (!source.exists()) {
            OperationJournal.markSourceDeleted(ctx)
            return true
        }
        return when (item.kind) {
            OperationJournal.KIND_ENCRYPT -> {
                val sourcePath = source.absolutePath
                val plainIdentity = runCatching { MediaIdentity.ofWithFolder(context, source) }.getOrNull()
                runCatching {
                    context.markReferencesOnEncrypt(
                        sourcePath = sourcePath,
                        encPath = dest.absolutePath,
                        plaintextIdentity = plainIdentity,
                        markPlaintextDormant = true
                    )
                }
                val wiped = CryptoManager.secureWipeAndDeleteFile(
                    file = source,
                    context = context,
                    syncParentDir = true,
                    purgeTraces = false
                )
                if (wiped) {
                    runCatching { CryptoManager.purgeMediaTracesPreservingReferences(context, sourcePath) }
                    OperationJournal.markSourceDeleted(ctx)
                } else {
                    OperationJournal.markFailed(ctx, "source wipe failed")
                }
                wiped
            }

            OperationJournal.KIND_DECRYPT -> {
                val encPath = source.absolutePath
                runCatching {
                    context.markReferencesOnDecrypt(
                        encPath = encPath,
                        decryptedFile = dest,
                        hideStealthReferences = true
                    )
                }
                runCatching { context.mediaDB.deleteMediumPath(encPath) }
                val deleted = source.delete()
                if (deleted) {
                    OperationJournal.markSourceDeleted(ctx)
                } else {
                    OperationJournal.markFailed(ctx, "encrypted source delete failed")
                }
                deleted
            }

            OperationJournal.KIND_MOVE -> {
                runCatching { context.updateDBMediaPath(source.absolutePath, dest.absolutePath) }
                if (source.name.endsWith(".bin")) {
                    runCatching { context.mediaDB.deleteMediumPath(source.absolutePath) }
                }
                val deleted = source.delete()
                if (deleted) {
                    OperationJournal.markSourceDeleted(ctx)
                } else {
                    OperationJournal.markFailed(ctx, "source delete failed")
                }
                deleted
            }

            else -> {
                OperationJournal.markSourceDeleted(ctx)
                true
            }
        }
    }

    private fun resumePlainCopy(
        context: Context,
        item: PendingOperation,
        ctx: OperationContext,
        batch: BatchOperationInfo?
    ): Boolean {
        val source = File(item.sourcePath)
        val dest = File(item.destPath)
        val staging = AtomicStaging.fileFor(dest, ctx)
        OperationJournal.markInFlight(ctx, staging.absolutePath)
        return try {
            dest.parentFile?.mkdirs()
            source.copyTo(staging, overwrite = true, bufferSize = 256 * 1024)
            AtomicStaging.commit(staging, dest)
            OperationJournal.markVerified(ctx, MediaIdentity.computeFingerprint(dest), dest.absolutePath)
            if (batch?.isMove == true || item.kind == OperationJournal.KIND_MOVE) {
                runCatching { context.updateDBMediaPath(source.absolutePath, dest.absolutePath) }
                source.delete()
                OperationJournal.markSourceDeleted(ctx)
            }
            true
        } catch (e: Exception) {
            AtomicStaging.discard(staging, containsPlaintext = true, context = context.applicationContext)
            OperationJournal.markFailed(ctx, e.message ?: "resume copy failed")
            false
        }
    }

    /** One combined prompt per process, shown on app start or — for crypto items — after unlock. */
    fun maybePromptResume(activity: BaseSimpleActivity) {
        if (promptShown || ForegroundOperationRegistry.isActive) return
        if (!promptCheckInFlight.compareAndSet(false, true)) return

        val appContext = activity.applicationContext
        ensureBackgroundThread {
            try {
                val pendingCount = reconcile(appContext)
                if (pendingCount == 0) return@ensureBackgroundThread
                val resumable = OperationJournal.resumableItems()
                if (resumable.isEmpty()) return@ensureBackgroundThread

                val hasEncrypted = resumable.any {
                    it.kind == OperationJournal.KIND_ENCRYPT ||
                        it.kind == OperationJournal.KIND_DECRYPT ||
                        it.destPath.endsWith(".bin") ||
                        it.sourcePath.endsWith(".bin")
                }
                // Encrypted operations cannot resume without the vault key; wait for unlock.
                if (hasEncrypted && !CryptoManager.isVaultUnlocked) return@ensureBackgroundThread

                val totalItems = resumable.size
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (promptShown || ForegroundOperationRegistry.isActive) return@runOnUiThread
                    if (hasEncrypted && !CryptoManager.isVaultUnlocked) return@runOnUiThread

                    promptShown = true
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.resume_operations_title)
                        .setMessage(activity.getString(R.string.resume_operations_message, pendingCount))
                        .setPositiveButton(R.string.resume_continue) { _, _ ->
                            val progressDialog = CryptoProgressDialog(
                                activity = activity,
                                max = totalItems,
                                titleId = R.string.resume_operations_title,
                                enableNotification = false
                            )
                            ensureBackgroundThread {
                                resume(
                                    context = activity,
                                    onProgress = { current, total ->
                                        activity.runOnUiThread {
                                            progressDialog.updateProgress(current, total)
                                        }
                                    }
                                ) { success ->
                                    activity.runOnUiThread {
                                        try {
                                            progressDialog.dismiss()
                                        } catch (_: Exception) {}
                                        activity.toast(
                                            if (success) R.string.resume_done else R.string.resume_failed
                                        )
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.resume_later, null)
                        .show()
                }
            } finally {
                promptCheckInFlight.set(false)
            }
        }
    }
}
