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
 * Crash reconciliation and orphan staging cleanup extracted from [OperationRecovery].
 */
internal object OperationReconciler {

    @Volatile
    internal var reconciled = false

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

    internal fun reconcileVaultRecycleBinAfterCrash(context: Context) {
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
                if (destVerified && OperationResumeExecutor.completePendingSourceDeletion(context, item, source, dest, ctx)) {
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
}
