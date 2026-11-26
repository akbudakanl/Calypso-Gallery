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
 * Resumes interrupted file and crypto operations extracted from [OperationRecovery].
 */
internal object OperationResumeExecutor {

    internal fun resume(
        context: Context,
        operationIds: Set<String>? = null,
        conflictOverride: OperationRecovery.ResumeConflictOverride? = null,
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
        conflictOverride: OperationRecovery.ResumeConflictOverride? = null,
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

    internal fun completePendingSourceDeletion(
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
}
