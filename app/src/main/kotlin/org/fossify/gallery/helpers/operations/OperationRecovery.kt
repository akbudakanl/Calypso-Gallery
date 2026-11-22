package org.fossify.gallery.helpers.operations

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.views.MyTextView
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.deleteSkippedCryptoSourcesBlocking
import org.fossify.gallery.dialogs.getSkippedEncryptFilesMode
import org.fossify.gallery.dialogs.resolveSkippedCryptoFiles
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_DELETE
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_KEEP
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

    private val promptedOperationIds = java.util.Collections.synchronizedSet(HashSet<String>())

    private val promptCheckInFlight = AtomicBoolean(false)

    internal enum class PromptMode {
        NONE,
        STANDARD
    }

    internal data class PromptSelection(
        val operationIds: Set<String>,
        val items: List<PendingOperation>,
        val mode: PromptMode,
        val distinctKinds: List<String>
    )

    internal data class PendingSkippedDialog(
        val operationId: String,
        val isEncrypt: Boolean,
        val skippedFiles: List<File>,
        val identicalTargetMap: Map<String, File>,
        val forceCleanupEmptyAlbum: Boolean
    )

    /** Test/debug only: allow a fresh reconciliation pass in the same process. */
    fun resetForTests() {
        reconciled = false
        promptedOperationIds.clear()
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
        // while keeping pending Phase-1 ops and deferred skipped-file decision batches intact.
        val grouped = OperationJournal.allItems().groupBy { it.operationId }
        var resumableOpCount = 0
        grouped.forEach { (operationId, items) ->
            if (operationId.isBlank()) return@forEach
            val batch = OperationJournal.decodeBatch(items.first().batchPayload)
            val hasResume = items.any { OperationJournal.needsResume(it, batch) }
            val hasDeferredSkip = items.any { OperationJournal.hasDeferredSkippedWork(it) }
            if (hasResume) {
                resumableOpCount++
            } else if (!hasDeferredSkip) {
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
        operationIds: Set<String>? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onDone: (Boolean) -> Unit
    ) {
        val rawItems = OperationJournal.resumableItems()
        val items = if (operationIds == null) rawItems else rawItems.filter { it.operationId in operationIds }
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
                    finalizeAlbumRootsIfEmpty(context, batch, operationId)
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

    private fun finalizeAlbumRootsIfEmpty(
        context: Context,
        batch: BatchOperationInfo?,
        operationId: String
    ) {
        val sourceRoot = batch?.sourceRoot?.takeIf { it.isNotBlank() } ?: return
        if (!batch.isMove && !batch.wipeSource && !batch.deleteSource) return
        val sourceDir = File(sourceRoot)
        if (!sourceDir.exists()) {
            if (!batch.mergeTargetRoot.isNullOrBlank()) {
                OperationJournal.markSourceAlbumFolded(operationId, sourceRoot)
            }
            return
        }
        if (!sourceDir.isDirectory) return
        runCatching {
            val cleaned = if (CryptoManager.isEncryptedFolder(sourceDir)) {
                CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(context, sourceDir)
            } else {
                (context as? BaseSimpleActivity)?.cleanupEmptyPlaintextAlbumIfNeeded(sourceDir, forceIfEmpty = false) ?: false
            }
            if (cleaned && !batch.mergeTargetRoot.isNullOrBlank()) {
                OperationJournal.markSourceAlbumFolded(operationId, sourceRoot)
            }
        }
    }

    private fun resumeOne(
        context: Context,
        item: PendingOperation,
        ctx: OperationContext,
        batch: BatchOperationInfo?
    ): Boolean {
        if (batch != null && batch.sourceRoot.isNotBlank() && batch.sourceRoot in batch.foldedRoots()) {
            OperationJournal.markSkipped(ctx)
            return true
        }

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

        // ADR 0003 §4: if the first attempt's committed output already exists and matches, reuse it.
        tryReuseExistingVerifiedDestination(context, item, source, dest, ctx, batch)?.let { return it }

        val conflictResolution = item.batchConflictResolution.toIntOrNull() ?: CONFLICT_KEEP_BOTH
        if (conflictResolution == CONFLICT_SKIP) {
            OperationJournal.markSkipped(ctx)
            return true
        }

        val pinnedName = item.resolvedDestName.takeIf { it.isNotEmpty() && !CryptoManager.isEncryptedFile(it) }

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
                        operation = ctx,
                        forcedDesiredName = pinnedName
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
                        operation = ctx,
                        forcedOriginalName = item.resolvedDestName.takeIf { it.isNotEmpty() }
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
                operation = ctx,
                forcedDestName = pinnedName
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
                        operation = ctx,
                        forcedDesiredName = pinnedName
                    ) != null
                } else {
                    resumePlainCopy(context, item, ctx, batch, conflictResolution)
                }
            }

            else -> false
        }
    }

    private fun tryReuseExistingVerifiedDestination(
        context: Context,
        item: PendingOperation,
        source: File,
        dest: File,
        ctx: OperationContext,
        batch: BatchOperationInfo?
    ): Boolean? {
        if (item.destPath == item.sourcePath || !dest.exists() || !dest.isFile) return null
        if (source.name.endsWith(".bin") || dest.name.endsWith(".bin")) {
            return null
        }
        if (dest.length() != source.length()) return null
        val sourceFp = item.sourceFingerprint.ifEmpty { MediaIdentity.computeFingerprint(source) }
        val destFp = MediaIdentity.computeFingerprint(dest)
        if (sourceFp.isEmpty() || destFp != sourceFp) return null

        OperationJournal.markVerified(ctx, destFp, dest.absolutePath)
        return if (OperationJournal.requiresSourceDeletion(item, batch)) {
            completePendingSourceDeletion(context, item, source, dest, ctx)
        } else {
            true
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
        batch: BatchOperationInfo?,
        conflictResolution: Int
    ): Boolean {
        val source = File(item.sourcePath)
        val initialDest = File(item.destPath)
        val destDir = initialDest.parentFile ?: return false
        val preferredName = item.resolvedDestName.takeIf { it.isNotEmpty() } ?: initialDest.name
        val candidateDest = File(destDir, preferredName)
        val dest = if (candidateDest.exists() && candidateDest.absolutePath != source.absolutePath) {
            when (conflictResolution) {
                CONFLICT_SKIP -> {
                    OperationJournal.markSkipped(ctx)
                    return true
                }
                CONFLICT_OVERWRITE -> candidateDest
                else -> CryptoManager.TargetPlainIndex(destDir).getAlternativeFile(preferredName)
            }
        } else {
            candidateDest
        }
        val staging = AtomicStaging.fileFor(dest, ctx)
        val sourceFingerprint = item.sourceFingerprint.ifEmpty { MediaIdentity.computeFingerprint(source) }
        val sourceLength = source.length()
        OperationJournal.markInFlight(
            ctx = ctx,
            stagingPath = staging.absolutePath,
            resolvedDestName = dest.name,
            actualDestPath = dest.absolutePath,
            sourceFingerprint = sourceFingerprint
        )
        return try {
            destDir.mkdirs()
            source.copyTo(staging, overwrite = true, bufferSize = 256 * 1024)
            AtomicStaging.commit(staging, dest)
            val destFingerprint = MediaIdentity.computeFingerprint(dest)
            if (sourceFingerprint.isEmpty() || destFingerprint.isEmpty() ||
                dest.length() != sourceLength || destFingerprint != sourceFingerprint
            ) {
                runCatching { CryptoManager.secureWipeAndDeleteFile(file = dest, context = context) }
                throw java.io.IOException("resume plain copy verification failed")
            }
            OperationJournal.markVerified(ctx, destFingerprint, dest.absolutePath)
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

    /**
     * Completes already-encrypted Phase-2 secure wipes silently in background once the vault is
     * unlocked: the user already requested encryption + secure source cleanup and the `.bin`
     * destination is re-verified before touching the plaintext original.
     */
    internal fun resumeSilentPhase2EncryptWipes(context: Context): Int {
        val resumable = OperationJournal.resumableItems()
        if (resumable.isEmpty()) return 0

        val byOp = resumable.groupBy { it.operationId }
        val phase2Items = ArrayList<Triple<String, BatchOperationInfo?, PendingOperation>>()
        byOp.forEach { (opId, items) ->
            val batch = OperationJournal.decodeBatch(items.first().batchPayload)
            items.sortedBy { it.itemIndex }.forEach { item ->
                if (OperationJournal.isEncryptPhase2Wipe(item, batch)) {
                    phase2Items.add(Triple(opId, batch, item))
                }
            }
        }
        if (phase2Items.isEmpty()) return 0

        var completedCount = 0
        val touchedOps = LinkedHashMap<String, BatchOperationInfo?>()
        ForegroundOperationRegistry.withOperation(context, "encrypt-phase2-wipe") {
            phase2Items.forEach { (opId, batch, item) ->
                if (ForegroundOperationRegistry.isCancelled) return@forEach
                val source = File(item.sourcePath)
                val dest = File(item.destPath)
                val ctx = OperationContext(opId, item.itemIndex, item.kind, batch)
                touchedOps[opId] = batch
                if (dest.exists() && (!dest.name.endsWith(".bin") || CryptoManager.verifyFileIntegrity(dest))) {
                    if (completePendingSourceDeletion(context, item, source, dest, ctx)) {
                        completedCount++
                    }
                } else if (!source.exists()) {
                    OperationJournal.markFailed(ctx, "missing source and destination")
                } else {
                    OperationJournal.resetToPending(ctx)
                }
            }
            touchedOps.forEach { (opId, batch) ->
                finalizeAlbumRootsIfEmpty(context, batch, opId)
                runCatching { MetadataReconciler.reconcile(context.applicationContext) }
                val remaining = OperationJournal.itemsFor(opId)
                if (remaining.none {
                        OperationJournal.needsResume(it, batch) || OperationJournal.hasDeferredSkippedWork(it)
                    }
                ) {
                    OperationJournal.clear(opId)
                }
            }
            if (completedCount > 0) {
                runCatching { CryptoManager.clearAppImageCaches(context) }
            }
        }
        return completedCount
    }

    /**
     * Handles persisted post-transfer skipped crypto file decisions:
     * - decrypt batches are cleared silently: skipped files are left in place, never wiped or asked about;
     * - approved deletes or setting == DELETE run silently in background without prompting;
     * - setting == KEEP clears silently;
     * - unanswered ASK batches are returned so UI can re-show [resolveSkippedCryptoFiles].
     */
    internal fun processPendingSkippedCryptoWork(context: Context): List<PendingSkippedDialog> {
        val deferred = OperationJournal.allItems().filter { OperationJournal.hasDeferredSkippedWork(it) }
        if (deferred.isEmpty()) return emptyList()

        val dialogsToReopen = ArrayList<PendingSkippedDialog>()
        deferred.groupBy { it.operationId }.forEach { (opId, rows) ->
            val first = rows.first()
            val isEncrypt = first.kind == OperationJournal.KIND_ENCRYPT
            val batch = OperationJournal.decodeBatch(first.batchPayload)
            val forceCleanupEmptyAlbum = batch?.wipeSource == true

            val skippedFiles = rows.map { File(it.sourcePath) }.filter { it.exists() && it.isFile }
            if (skippedFiles.isEmpty()) {
                OperationJournal.clear(opId)
                return@forEach
            }
            val identicalTargetMap = LinkedHashMap<String, File>()
            rows.forEach { row ->
                if (row.destPath.isNotBlank()) {
                    identicalTargetMap[File(row.sourcePath).absolutePath] = File(row.destPath)
                }
            }

            val allApproved = rows.all { it.state == OperationJournal.STATE_DELETE_SKIPPED_APPROVED }
            val mode = if (isEncrypt) {
                runCatching { context.getSkippedEncryptFilesMode() }.getOrDefault(-1)
            } else {
                -1
            }
            when {
                !isEncrypt -> OperationJournal.clear(opId)
                allApproved || mode == CRYPTO_SKIPPED_FILES_DELETE -> {
                    ForegroundOperationRegistry.withOperation(context, "skipped-crypto-wipe") {
                        deleteSkippedCryptoSourcesBlocking(
                            context = context,
                            skippedFiles = skippedFiles,
                            identicalTargetMap = identicalTargetMap,
                            forceCleanupEmptyAlbum = forceCleanupEmptyAlbum
                        )
                    }
                    OperationJournal.clear(opId)
                }
                mode == CRYPTO_SKIPPED_FILES_KEEP -> {
                    OperationJournal.clear(opId)
                }
                else -> {
                    dialogsToReopen.add(
                        PendingSkippedDialog(
                            operationId = opId,
                            isEncrypt = isEncrypt,
                            skippedFiles = skippedFiles,
                            identicalTargetMap = identicalTargetMap,
                            forceCleanupEmptyAlbum = forceCleanupEmptyAlbum
                        )
                    )
                }
            }
        }
        return dialogsToReopen
    }

    internal fun selectPromptBatch(isVaultUnlocked: Boolean): PromptSelection {
        val resumable = OperationJournal.resumableItems()
        if (resumable.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val batchCache = HashMap<String, BatchOperationInfo?>()
        val promptableItems = resumable.filter { item ->
            val batch = batchCache.getOrPut(item.operationId) {
                OperationJournal.decodeBatch(item.batchPayload)
            }
            !OperationJournal.isEncryptPhase2Wipe(item, batch)
        }
        if (promptableItems.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val byOp = promptableItems.groupBy { it.operationId }
        val eligibleEntries = byOp.filter { (opId, items) ->
            if (opId in promptedOperationIds) return@filter false
            val isVaultOp = items.any {
                it.kind == OperationJournal.KIND_ENCRYPT ||
                    it.kind == OperationJournal.KIND_DECRYPT ||
                    it.destPath.endsWith(".bin") ||
                    it.sourcePath.endsWith(".bin")
            }
            if (isVaultUnlocked) true else !isVaultOp
        }
        if (eligibleEntries.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val selectedItems = eligibleEntries.values.flatten()
        val distinctKinds = selectedItems.map { it.kind }.distinct()
        return PromptSelection(
            operationIds = eligibleEntries.keys.toSet(),
            items = selectedItems,
            mode = PromptMode.STANDARD,
            distinctKinds = distinctKinds
        )
    }

    internal fun discardOperations(operationIds: Set<String>) {
        operationIds.forEach { OperationJournal.clear(it) }
    }

    private fun formatOperationKindsLabel(context: Context, kinds: List<String>): String {
        val labels = kinds.mapNotNull { kind ->
            when (kind) {
                OperationJournal.KIND_COPY -> context.getString(R.string.resume_op_copy)
                OperationJournal.KIND_MOVE -> context.getString(R.string.resume_op_move)
                OperationJournal.KIND_ENCRYPT -> context.getString(R.string.resume_op_encrypt)
                OperationJournal.KIND_DECRYPT -> context.getString(R.string.resume_op_decrypt)
                else -> null
            }
        }.distinct()
        return if (labels.isNotEmpty()) labels.joinToString(", ") else context.getString(R.string.resume_op_copy)
    }

    private fun showPendingSkippedDialogIfReady(
        activity: BaseSimpleActivity,
        pendingSkipDialogs: List<PendingSkippedDialog>
    ) {
        val next = pendingSkipDialogs.firstOrNull { it.operationId !in promptedOperationIds } ?: return
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            if (!CryptoManager.isVaultUnlocked) return@runOnUiThread
            if (!promptedOperationIds.add(next.operationId)) return@runOnUiThread

            activity.resolveSkippedCryptoFiles(
                isEncrypt = next.isEncrypt,
                skippedFiles = next.skippedFiles,
                identicalTargetMap = next.identicalTargetMap,
                forceCleanupEmptyAlbum = next.forceCleanupEmptyAlbum,
                existingSkipOpId = next.operationId
            ) {
                if (pendingSkipDialogs.size > 1) {
                    showPendingSkippedDialogIfReady(activity, pendingSkipDialogs.drop(1))
                }
            }
        }
    }

    /**
     * Recovers interrupted operations cleanly by layer:
     * 1. Already-encrypted Phase-2 secure wipes run silently in background upon vault unlock.
     * 2. Approved/policy-delete skipped crypto files run silently in background; unanswered ASK
     *    batches re-open [resolveSkippedCryptoFiles] on unlock.
     * 3. Incomplete Phase-1 transfers show a one-time Yes / No prompt (plain on start, vault on unlock).
     */
    fun maybePromptResume(activity: BaseSimpleActivity) {
        if (ForegroundOperationRegistry.isActive) return
        if (!promptCheckInFlight.compareAndSet(false, true)) return

        val appContext = activity.applicationContext
        ensureBackgroundThread {
            try {
                reconcile(appContext)
                val vaultUnlocked = CryptoManager.isVaultUnlocked
                val pendingSkipDialogs = if (vaultUnlocked) {
                    resumeSilentPhase2EncryptWipes(appContext)
                    processPendingSkippedCryptoWork(appContext)
                } else {
                    emptyList()
                }

                val selection = selectPromptBatch(vaultUnlocked)
                if (selection.mode == PromptMode.NONE || selection.items.isEmpty()) {
                    if (vaultUnlocked && pendingSkipDialogs.isNotEmpty()) {
                        showPendingSkippedDialogIfReady(activity, pendingSkipDialogs)
                    }
                    return@ensureBackgroundThread
                }

                val targetOpIds = selection.operationIds
                val totalItems = selection.items.size
                val opLabel = formatOperationKindsLabel(appContext, selection.distinctKinds)
                val promptMessage = appContext.getString(R.string.resume_operation_interrupted_message, opLabel, totalItems)
                val abortedMessage = appContext.getString(R.string.resume_operation_aborted_message, opLabel)

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (ForegroundOperationRegistry.isActive) return@runOnUiThread
                    if (targetOpIds.any { it in promptedOperationIds }) return@runOnUiThread
                    promptedOperationIds.addAll(targetOpIds)

                    val padding = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
                    val promptView = MyTextView(activity).apply {
                        setPadding(padding, padding, padding, 0)
                        text = promptMessage
                    }
                    activity.getAlertDialogBuilder()
                        .setCancelable(false)
                        .setPositiveButton(org.fossify.commons.R.string.yes) { _, _ ->
                            val progressDialog = CryptoProgressDialog(
                                activity = activity,
                                max = totalItems,
                                titleId = R.string.resume_operations_title,
                                enableNotification = false
                            )
                            ensureBackgroundThread {
                                resume(
                                    context = activity,
                                    operationIds = targetOpIds,
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
                                        if (vaultUnlocked && pendingSkipDialogs.isNotEmpty()) {
                                            showPendingSkippedDialogIfReady(activity, pendingSkipDialogs)
                                        }
                                    }
                                }
                            }
                        }
                        .setNegativeButton(org.fossify.commons.R.string.no) { _, _ ->
                            ensureBackgroundThread {
                                discardOperations(targetOpIds)
                            }
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                val abortedView = MyTextView(activity).apply {
                                    setPadding(padding, padding, padding, 0)
                                    text = abortedMessage
                                }
                                activity.getAlertDialogBuilder()
                                    .setCancelable(false)
                                    .setPositiveButton(org.fossify.commons.R.string.ok, null)
                                    .apply {
                                        activity.setupDialogStuff(
                                            abortedView,
                                            this,
                                            titleId = R.string.resume_operations_title,
                                            cancelOnTouchOutside = false
                                        )
                                    }
                            }
                        }
                        .apply {
                            activity.setupDialogStuff(
                                promptView,
                                this,
                                titleId = R.string.resume_operations_title,
                                cancelOnTouchOutside = false
                            )
                        }
                }
            } finally {
                promptCheckInFlight.set(false)
            }
        }
    }
}
