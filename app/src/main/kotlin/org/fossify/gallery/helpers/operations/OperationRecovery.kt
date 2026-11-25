package org.fossify.gallery.helpers.operations

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.recycleBinPath
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
import org.fossify.gallery.extensions.TransferPairSpec
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.ensureVaultUnlocked
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.resolveCryptoTransferConflicts
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_DELETE
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_KEEP
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DeleteOperationEngine
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.OperationNotificationCategory
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.copyOrMoveEncryptedFileToDirectory
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.isEncryptedFolder
import org.fossify.gallery.helpers.purgeMediaTracesPreservingReferences
import org.fossify.gallery.helpers.reconcileVaultRecycleBin
import org.fossify.gallery.helpers.resolvePlainMediaType
import org.fossify.gallery.helpers.secureWipeAndDeleteFile
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.models.Medium
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

    internal data class ResumeConflictOverride(
        val resolutionsBySourcePath: Map<String, Int> = emptyMap(),
        val identicalSourcePaths: Set<String> = emptySet(),
        val identicalTargetsBySourcePath: Map<String, File> = emptyMap(),
        val nonIdenticalSourcePaths: Set<String> = emptySet(),
        val ignoreRecordedConflictResolutions: Boolean = false
    )

    /** Test/debug only: allow a fresh reconciliation pass in the same process. */
    fun resetForTests() {
        reconciled = false
        promptedOperationIds.clear()
        promptCheckInFlight.set(false)
    }

    private fun reconcileRecycleBinAfterCrash(context: Context) {
        val binRoot = File(context.recycleBinPath)
        if (!binRoot.exists()) return
        val deletedMedia = runCatching { context.mediaDB.getDeletedMedia() }.getOrDefault(emptyList())
        val registeredOrigPaths = deletedMedia.mapTo(HashSet(deletedMedia.size)) {
            it.path.removePrefix(RECYCLE_BIN)
        }
        val deletedTs = System.currentTimeMillis()
        DeleteOperationEngine.reconcileRecycleBinState(
            recycleBinRoot = binRoot,
            registeredDeletedOrigPaths = registeredOrigPaths,
            doesOriginalExist = { origPath -> File(origPath).exists() },
            adoptMissingDbEntry = { origPath, binFile ->
                val medium = Medium(
                    id = null,
                    name = origPath.getFilenameFromPath(),
                    path = "$RECYCLE_BIN$origPath",
                    parentPath = origPath.getParentPath(),
                    modified = binFile.lastModified(),
                    taken = binFile.lastModified(),
                    size = binFile.length(),
                    type = resolvePlainMediaType(origPath),
                    videoDuration = 0,
                    isFavorite = false,
                    deletedTS = deletedTs,
                    mediaStoreId = 0L
                )
                runCatching { context.mediaDB.insert(medium) }
            }
        )
    }

    private fun reconcileVaultRecycleBinAfterCrash(context: Context) {
        if (!CryptoManager.isVaultUnlocked) return
        runCatching { CryptoManager.reconcileVaultRecycleBin(context) }
    }

    /**
     * Silently reconciles interrupted COPY and MOVE batches without prompting the user:
     * - Plain/unlocked MOVE items that were already committed + verified (`STATE_VERIFIED`)
     *   finish deleting their leftover source so no duplicates remain.
     * - Locked vault `.bin` MOVE items waiting in `STATE_VERIFIED` are deferred until vault unlock.
     * - Untouched sources for unstarted `PENDING` / `IN_FLIGHT` COPY or MOVE items are preserved
     *   intact; their journal rows are cleared so the user can manually copy/move the rest.
     */
    internal fun reconcileSilentCopyMoveAfterCrash(
        context: Context,
        isVaultUnlocked: Boolean
    ): Int {
        if (ForegroundOperationRegistry.isActive) return 0
        val grouped = OperationJournal.allItems().groupBy { it.operationId }
        if (grouped.isEmpty()) return 0

        var completedSourceDeletes = 0
        val dirtyFolders = LinkedHashSet<String>()

        grouped.forEach { (operationId, items) ->
            if (operationId.isBlank()) return@forEach
            val isCopyMoveOp = items.all {
                it.kind == OperationJournal.KIND_COPY || it.kind == OperationJournal.KIND_MOVE
            }
            if (!isCopyMoveOp) return@forEach

            val batch = OperationJournal.decodeBatch(items.first().batchPayload)
            var waitingOnLockedVault = false

            items.sortedBy { it.itemIndex }.forEach { item ->
                if (ForegroundOperationRegistry.isActive) return completedSourceDeletes
                val isMoveDeletionPending = item.state == OperationJournal.STATE_VERIFIED &&
                    OperationJournal.requiresSourceDeletion(item, batch)
                if (!isMoveDeletionPending) return@forEach

                val source = File(item.sourcePath)
                val dest = File(item.destPath)
                val isVaultItem = source.name.endsWith(".bin") || dest.name.endsWith(".bin")
                if (isVaultItem && !isVaultUnlocked) {
                    waitingOnLockedVault = true
                    return@forEach
                }

                val destVerified = dest.exists() && if (dest.name.endsWith(".bin")) {
                    CryptoManager.verifyFileIntegrity(dest)
                } else {
                    item.destFingerprint.isEmpty() || MediaIdentity.computeFingerprint(dest) == item.destFingerprint
                }
                val ctx = OperationContext(operationId, item.itemIndex, item.kind, batch)
                if (destVerified && completePendingSourceDeletion(context, item, source, dest, ctx)) {
                    completedSourceDeletes++
                    source.parent?.let { dirtyFolders.add(it) }
                    dest.parent?.let { dirtyFolders.add(it) }
                } else if (!source.exists()) {
                    OperationJournal.markSourceDeleted(ctx)
                }
            }

            if (waitingOnLockedVault) {
                OperationJournal.clearAfterCompletionOrCancel(operationId)
            } else {
                OperationJournal.clear(operationId)
            }
        }

        if (completedSourceDeletes > 0) {
            runCatching { MetadataReconciler.reconcile(context.applicationContext) }
            if (dirtyFolders.isNotEmpty()) {
                MediaIndexCoordinator.markFoldersDirty(dirtyFolders)
            }
        }
        return completedSourceDeletes
    }

    /** Number of interrupted operations still needing a user decision. */
    fun reconcile(context: Context): Int {
        if (ForegroundOperationRegistry.isActive) return 0
        val appContext = context.applicationContext
        if (!reconciled) {
            reconciled = true
            cleanupOrphanStaging(context)
            runCatching { reconcileRecycleBinAfterCrash(appContext) }
            runCatching { reconcileVaultRecycleBinAfterCrash(appContext) }
            // Fix MediaStore/Room/manifest references of items that were already committed before
            // the process died. Guarded internally so each operation is processed at most once.
            runCatching { MetadataReconciler.reconcile(appContext) }
            runCatching { reconcileSilentCopyMoveAfterCrash(appContext, CryptoManager.isVaultUnlocked) }
            OperationJournal.activeItems().forEach { item ->
                if (ForegroundOperationRegistry.isActive) return 0
                if (item.kind != OperationJournal.KIND_ENCRYPT && item.kind != OperationJournal.KIND_DECRYPT) {
                    return@forEach
                }
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

        // Prune operations whose items are all terminal while keeping pending Encrypt/Decrypt ops
        // and deferred skipped-file decision batches intact.
        val grouped = OperationJournal.allItems().groupBy { it.operationId }
        var resumableOpCount = 0
        grouped.forEach { (operationId, items) ->
            if (operationId.isBlank()) return@forEach
            val isCryptoOp = items.any {
                it.kind == OperationJournal.KIND_ENCRYPT || it.kind == OperationJournal.KIND_DECRYPT
            }
            if (!isCryptoOp) {
                val batch = OperationJournal.decodeBatch(items.first().batchPayload)
                val hasVerifiedMove = items.any {
                    it.state == OperationJournal.STATE_VERIFIED && OperationJournal.requiresSourceDeletion(it, batch)
                }
                if (!hasVerifiedMove) {
                    OperationJournal.clear(operationId)
                }
                return@forEach
            }
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
        resume(
            context = context,
            operationIds = null,
            conflictOverride = null,
            skippedEncryptCollector = null,
            skippedIdenticalMap = null,
            onProgress = null,
            onDone = onDone
        )
    }

    internal fun resume(
        context: Context,
        operationIds: Set<String>? = null,
        conflictOverride: ResumeConflictOverride? = null,
        skippedEncryptCollector: MutableList<File>? = null,
        skippedIdenticalMap: MutableMap<String, File>? = null,
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
        val touchedFolders = LinkedHashSet<String>()
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
                    File(item.sourcePath).parent?.let { touchedFolders.add(it) }
                    File(item.destPath).parent?.let { touchedFolders.add(it) }
                    val ctx = OperationContext(operationId, item.itemIndex, item.kind, batch)
                    val succeeded = try {
                        resumeOne(
                            context = context,
                            item = item,
                            ctx = ctx,
                            batch = batch,
                            conflictOverride = conflictOverride,
                            skippedEncryptCollector = skippedEncryptCollector,
                            skippedIdenticalMap = skippedIdenticalMap
                        )
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
            if (touchedFolders.isNotEmpty()) {
                MediaIndexCoordinator.markFoldersDirty(touchedFolders)
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
        batch: BatchOperationInfo?,
        conflictOverride: ResumeConflictOverride? = null,
        skippedEncryptCollector: MutableList<File>? = null,
        skippedIdenticalMap: MutableMap<String, File>? = null
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

        val hasFreshConflictOverride = conflictOverride?.ignoreRecordedConflictResolutions == true
        if (!hasFreshConflictOverride) {
            // ADR 0003 §4: if the first attempt's committed output already exists and matches, reuse it.
            tryReuseExistingVerifiedDestination(context, item, source, dest, ctx, batch)?.let { return it }
        }

        val sourceKey = source.absolutePath
        val conflictResolution = if (hasFreshConflictOverride && conflictOverride != null) {
            if (sourceKey in conflictOverride.identicalSourcePaths) {
                OperationJournal.markSkipped(ctx)
                if (item.kind == OperationJournal.KIND_ENCRYPT && (batch?.wipeSource != false)) {
                    skippedEncryptCollector?.add(source)
                    conflictOverride.identicalTargetsBySourcePath[sourceKey]?.let { target ->
                        skippedIdenticalMap?.put(sourceKey, target)
                    }
                }
                return true
            }
            conflictOverride.resolutionsBySourcePath[sourceKey] ?: CONFLICT_KEEP_BOTH
        } else {
            item.batchConflictResolution.toIntOrNull() ?: CONFLICT_KEEP_BOTH
        }

        if (conflictResolution == CONFLICT_SKIP) {
            OperationJournal.markSkipped(ctx)
            if (hasFreshConflictOverride && item.kind == OperationJournal.KIND_ENCRYPT && (batch?.wipeSource != false)) {
                skippedEncryptCollector?.add(source)
            }
            return true
        }

        val pinnedName = if (hasFreshConflictOverride) {
            null
        } else {
            item.resolvedDestName.takeIf { it.isNotEmpty() && !CryptoManager.isEncryptedFile(it) }
        }
        val skipDupCheck = if (hasFreshConflictOverride && conflictOverride != null) {
            val standardMode = runCatching {
                context.config.conflictHandlingMode == CONFLICT_HANDLING_STANDARD_DIALOG
            }.getOrDefault(false)
            standardMode || (sourceKey in conflictOverride.nonIdenticalSourcePaths)
        } else {
            false
        }
        val deleteIdentical = if (hasFreshConflictOverride) false else null

        return when (item.kind) {
            OperationJournal.KIND_ENCRYPT -> {
                if (CryptoManager.isEncryptedFile(source.name)) {
                    CryptoManager.copyOrMoveEncryptedFileToDirectory(
                        context = context,
                        sourceBin = source,
                        targetDir = destDir,
                        isMove = batch?.isMove ?: true,
                        conflictResolution = conflictResolution,
                        skipDuplicateCheck = skipDupCheck,
                        deleteSourceOnIdentical = deleteIdentical ?: true,
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
                        skipDuplicateCheck = skipDupCheck,
                        deleteSourceOnIdentical = deleteIdentical ?: (batch?.wipeSource ?: true),
                        operation = ctx,
                        forcedOriginalName = if (hasFreshConflictOverride) null else item.resolvedDestName.takeIf { it.isNotEmpty() }
                    ) != null
                }
            }

            OperationJournal.KIND_DECRYPT -> CryptoManager.decryptFileToDirectory(
                context = context,
                encryptedFile = source,
                targetDir = destDir,
                deleteEncryptedSource = batch?.deleteSource ?: true,
                conflictResolution = conflictResolution,
                skipDuplicateCheck = skipDupCheck,
                deleteSourceOnIdentical = deleteIdentical ?: (batch?.deleteSource ?: true),
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
                        skipDuplicateCheck = skipDupCheck,
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
        val dirtyFolders = LinkedHashSet<String>()
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
                        source.parent?.let { dirtyFolders.add(it) }
                        dest.parent?.let { dirtyFolders.add(it) }
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
                if (dirtyFolders.isNotEmpty()) {
                    MediaIndexCoordinator.markFoldersDirty(dirtyFolders)
                }
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

    internal fun selectPromptBatch(@Suppress("UNUSED_PARAMETER") isVaultUnlocked: Boolean): PromptSelection {
        val resumable = OperationJournal.resumableItems()
        if (resumable.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val batchCache = HashMap<String, BatchOperationInfo?>()
        val promptableItems = resumable.filter { item ->
            if (item.kind != OperationJournal.KIND_ENCRYPT && item.kind != OperationJournal.KIND_DECRYPT) {
                return@filter false
            }
            val batch = batchCache.getOrPut(item.operationId) {
                OperationJournal.decodeBatch(item.batchPayload)
            }
            !OperationJournal.isEncryptPhase2Wipe(item, batch)
        }
        if (promptableItems.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val byOp = promptableItems.groupBy { it.operationId }
        val eligibleEntries = byOp.filter { (opId, _) ->
            opId !in promptedOperationIds
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
        operationIds.forEach { OperationJournal.clearAfterCompletionOrCancel(it) }
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
        pendingSkipDialogs: List<PendingSkippedDialog>,
        onAllDone: () -> Unit = {}
    ) {
        val next = pendingSkipDialogs.firstOrNull { it.operationId !in promptedOperationIds }
        if (next == null) {
            onAllDone()
            return
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                onAllDone()
                return@runOnUiThread
            }
            if (!CryptoManager.isVaultUnlocked) {
                onAllDone()
                return@runOnUiThread
            }
            if (!promptedOperationIds.add(next.operationId)) {
                showPendingSkippedDialogIfReady(activity, pendingSkipDialogs.drop(1), onAllDone)
                return@runOnUiThread
            }

            activity.resolveSkippedCryptoFiles(
                isEncrypt = next.isEncrypt,
                skippedFiles = next.skippedFiles,
                identicalTargetMap = next.identicalTargetMap,
                forceCleanupEmptyAlbum = next.forceCleanupEmptyAlbum,
                existingSkipOpId = next.operationId
            ) { _ ->
                if (pendingSkipDialogs.size > 1) {
                    showPendingSkippedDialogIfReady(activity, pendingSkipDialogs.drop(1), onAllDone)
                } else {
                    onAllDone()
                }
            }
        }
    }

    /**
     * Recovers interrupted operations cleanly by layer:
     * 1. Orphan `.stage_*` / `.tmp_*` / plain recycle-bin temp files are cleaned silently;
     *    COPY and MOVE operations silently settle any verified source deletes without prompting.
     * 2. Upon vault unlock, already-encrypted Phase-2 secure wipes run silently in background.
     * 3. Approved/policy-delete skipped crypto files run silently in background; unanswered ASK
     *    batches re-open [resolveSkippedCryptoFiles] on unlock.
     * 4. Incomplete Phase-1 ENCRYPT / DECRYPT transfers prompt Yes/No on app launch; tapping Yes
     *    requests the vault password via [ensureVaultUnlocked] and freshly re-asks every conflict.
     */
    fun maybePromptResume(activity: BaseSimpleActivity) {
        if (ForegroundOperationRegistry.isActive) return
        if (!promptCheckInFlight.compareAndSet(false, true)) return

        val appContext = activity.applicationContext
        ensureBackgroundThread {
            try {
                reconcile(appContext)
                val vaultUnlocked = CryptoManager.isVaultUnlocked
                val initialSkipDialogs = if (vaultUnlocked) {
                    reconcileVaultRecycleBinAfterCrash(appContext)
                    reconcileSilentCopyMoveAfterCrash(appContext, isVaultUnlocked = true)
                    resumeSilentPhase2EncryptWipes(appContext)
                    processPendingSkippedCryptoWork(appContext)
                } else {
                    emptyList()
                }

                val selection = selectPromptBatch(vaultUnlocked)
                if (selection.mode == PromptMode.NONE || selection.items.isEmpty()) {
                    if (vaultUnlocked && initialSkipDialogs.isNotEmpty()) {
                        showPendingSkippedDialogIfReady(activity, initialSkipDialogs)
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
                            activity.ensureVaultUnlocked(
                                onCancelled = {
                                    promptedOperationIds.removeAll(targetOpIds)
                                }
                            ) {
                                ensureBackgroundThread {
                                    reconcileVaultRecycleBinAfterCrash(appContext)
                                    reconcileSilentCopyMoveAfterCrash(appContext, isVaultUnlocked = true)
                                    resumeSilentPhase2EncryptWipes(appContext)
                                    val unlockedSkipDialogs = processPendingSkippedCryptoWork(appContext)
                                    val combinedSkipDialogs = (initialSkipDialogs + unlockedSkipDialogs)
                                        .distinctBy { it.operationId }

                                    val remainingItems = OperationJournal.resumableItems()
                                        .filter { it.operationId in targetOpIds }
                                    val transferItems = remainingItems.filter {
                                        it.state != OperationJournal.STATE_VERIFIED && File(it.sourcePath).isFile
                                    }

                                    val finishSession: () -> Unit = {
                                        if (combinedSkipDialogs.isNotEmpty()) {
                                            showPendingSkippedDialogIfReady(activity, combinedSkipDialogs) {
                                                VaultSessionManager.lockVaultIfOutsideSession(activity)
                                            }
                                        } else {
                                            VaultSessionManager.lockVaultIfOutsideSession(activity)
                                        }
                                    }

                                    activity.runOnUiThread {
                                        if (activity.isFinishing || activity.isDestroyed) {
                                            VaultSessionManager.lockVaultIfOutsideSession(activity)
                                            return@runOnUiThread
                                        }
                                        if (remainingItems.isEmpty()) {
                                            finishSession()
                                            return@runOnUiThread
                                        }

                                        activity.resolveCryptoTransferConflicts(
                                            allowFastLargeCheck = true,
                                            buildPairs = {
                                                transferItems.mapNotNull { item ->
                                                    val srcFile = File(item.sourcePath)
                                                    val targetDir = File(item.destPath).parentFile ?: return@mapNotNull null
                                                    val isTargetEncrypted = (item.kind == OperationJournal.KIND_ENCRYPT) ||
                                                        item.destPath.endsWith(".bin")
                                                    TransferPairSpec(
                                                        sourceFile = srcFile,
                                                        targetDir = targetDir,
                                                        isTargetEncrypted = isTargetEncrypted,
                                                        desiredPlainName = if (!isTargetEncrypted && CryptoManager.isEncryptedFile(srcFile.name)) {
                                                            null
                                                        } else {
                                                            srcFile.name
                                                        }
                                                    )
                                                }
                                            }
                                        ) { scan, _ ->
                                            if (activity.isFinishing || activity.isDestroyed) {
                                                VaultSessionManager.lockVaultIfOutsideSession(activity)
                                                return@resolveCryptoTransferConflicts
                                            }
                                            activity.ensureNotificationPermissionThenRun {
                                                if (activity.isFinishing || activity.isDestroyed) {
                                                    VaultSessionManager.lockVaultIfOutsideSession(activity)
                                                    return@ensureNotificationPermissionThenRun
                                                }
                                                val progressDialog = CryptoProgressDialog(
                                                    activity = activity,
                                                    max = remainingItems.size,
                                                    titleId = R.string.resume_operations_title,
                                                    notificationChannelId = OperationNotificationCategory.CHANNEL_CRYPTO,
                                                    enableNotification = true
                                                )
                                                val skippedEncryptFiles = ArrayList<File>()
                                                val skippedIdenticalMap = LinkedHashMap<String, File>()
                                                var lastUiProgressMs = 0L
                                                val conflictOverride = ResumeConflictOverride(
                                                    resolutionsBySourcePath = scan.resolutions,
                                                    identicalSourcePaths = scan.identicalPaths,
                                                    identicalTargetsBySourcePath = scan.identicalTargets,
                                                    nonIdenticalSourcePaths = scan.nonIdenticalPaths,
                                                    ignoreRecordedConflictResolutions = true
                                                )
                                                ensureBackgroundThread {
                                                    resume(
                                                        context = activity,
                                                        operationIds = targetOpIds,
                                                        conflictOverride = conflictOverride,
                                                        skippedEncryptCollector = skippedEncryptFiles,
                                                        skippedIdenticalMap = skippedIdenticalMap,
                                                        onProgress = { current, total ->
                                                            val now = System.currentTimeMillis()
                                                            if (current == 0 || current >= total || now - lastUiProgressMs >= 80L) {
                                                                lastUiProgressMs = now
                                                                activity.runOnUiThread {
                                                                    progressDialog.updateProgress(current, total)
                                                                }
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
                                                            if (skippedEncryptFiles.isNotEmpty() &&
                                                                !activity.isFinishing && !activity.isDestroyed
                                                            ) {
                                                                activity.resolveSkippedCryptoFiles(
                                                                    isEncrypt = true,
                                                                    skippedFiles = skippedEncryptFiles,
                                                                    identicalTargetMap = skippedIdenticalMap,
                                                                    forceCleanupEmptyAlbum = false
                                                                ) { _ ->
                                                                    finishSession()
                                                                }
                                                            } else {
                                                                finishSession()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
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
