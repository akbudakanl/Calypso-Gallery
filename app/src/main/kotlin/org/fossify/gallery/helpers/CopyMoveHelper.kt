package org.fossify.gallery.helpers

import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.canManageMedia
import org.fossify.commons.extensions.createDirectorySync
import org.fossify.commons.extensions.deleteFileBg
import org.fossify.commons.extensions.deleteFromMediaStore
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAndroidSAFFileItems
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDocumentSdk30
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileOutputStreamSync
import org.fossify.commons.extensions.getFileUrisFromFileDirItems
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.isRecycleBinPath
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.SmartConflictItem
import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.BatchOperationInfo
import org.fossify.gallery.helpers.operations.JournalItem
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.operations.OperationFaults
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.SpaceEstimator
import org.fossify.gallery.helpers.operations.SpaceConfirmation
import org.fossify.gallery.helpers.operations.TransientOperationWaiter
import java.io.IOException
import java.security.MessageDigest
import org.fossify.gallery.extensions.StandardConflictItem
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.resolveSmartContentConflicts
import org.fossify.gallery.extensions.resolveStandardFilenameConflicts
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateReferencesForFolderRename
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val COPY_BUFFER_SIZE = 256 * 1024
private const val BYTES_PER_KB = 1000L
private const val UI_PROGRESS_UPDATE_INTERVAL_MS = 100L

/**
 * Executes unencrypted copy/move operations with both an in-app progress bar dialog and the
 * standard background notification progress bar.
 */
fun BaseSimpleActivity.copyMoveFilesWithProgress(
    fileDirItems: ArrayList<FileDirItem>,
    source: String,
    destination: String,
    isCopyOperation: Boolean,
    copyMediaOnly: Boolean,
    copyHidden: Boolean,
    callback: (destinationPath: String) -> Unit
) {
    if (fileDirItems.isEmpty()) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }
    if (source == destination) {
        toast(org.fossify.commons.R.string.source_and_destination_same)
        return
    }
    if (!getDoesFilePathExist(destination)) {
        toast(org.fossify.commons.R.string.invalid_destination)
        return
    }

    handleSAFDialog(destination) { safGranted ->
        if (!safGranted) {
            toast(org.fossify.commons.R.string.copy_move_failed)
            return@handleSAFDialog
        }
        handleSAFDialogSdk30(destination) { sdk30Granted ->
            if (!sdk30Granted) {
                toast(org.fossify.commons.R.string.copy_move_failed)
                return@handleSAFDialogSdk30
            }

            val useStreamCopy = isCopyOperation || needsStreamCopyMove(source, destination)
            if (isCopyOperation && canManageMedia() && !fileDirItems.first().isRecycleBinPath(this)) {
                val uris = getFileUrisFromFileDirItems(fileDirItems)
                updateSDK30Uris(uris) { updated ->
                    if (updated) {
                        prepareAndRunCopyMove(
                            fileDirItems = fileDirItems,
                            destination = destination,
                            isCopyOperation = isCopyOperation,
                            useStreamCopy = useStreamCopy,
                            copyMediaOnly = copyMediaOnly,
                            copyHidden = copyHidden,
                            callback = callback
                        )
                    } else {
                        toast(org.fossify.commons.R.string.copy_move_failed)
                    }
                }
            } else {
                prepareAndRunCopyMove(
                    fileDirItems = fileDirItems,
                    destination = destination,
                    isCopyOperation = isCopyOperation,
                    useStreamCopy = useStreamCopy,
                    copyMediaOnly = copyMediaOnly,
                    copyHidden = copyHidden,
                    callback = callback
                )
            }
        }
    }
}

private fun BaseSimpleActivity.needsStreamCopyMove(source: String, destination: String): Boolean {
    return isPathOnOTG(source) || isPathOnOTG(destination) ||
        isPathOnSD(source) || isPathOnSD(destination) ||
        isRestrictedSAFOnlyRoot(source) || isRestrictedSAFOnlyRoot(destination) ||
        isAccessibleWithSAFSdk30(source) || isAccessibleWithSAFSdk30(destination)
}

private fun BaseSimpleActivity.prepareAndRunCopyMove(
    fileDirItems: ArrayList<FileDirItem>,
    destination: String,
    isCopyOperation: Boolean,
    useStreamCopy: Boolean,
    copyMediaOnly: Boolean,
    copyHidden: Boolean,
    callback: (destinationPath: String) -> Unit
) {
    val titleId = if (isCopyOperation) {
        org.fossify.commons.R.string.copying
    } else {
        org.fossify.commons.R.string.moving
    }
    // Pre-flight phase: conflict detection / byte comparison can take a long time. Show a
    // dedicated dialog so the user never sees the real transfer stuck at 0%.
    val initialProgressDialog = CryptoProgressDialog(
        activity = this,
        max = 0,
        titleId = R.string.checking_files_before_transfer,
        notificationChannelId = OperationNotificationCategory.CHANNEL_COPY_MOVE,
        notificationIconRes = org.fossify.commons.R.drawable.ic_copy_vector,
        enableNotification = false
    )

    val startAfterSpaceCheck = {
        val proceedAfterConflicts = {
            conflictResolutions: LinkedHashMap<String, Int>,
            precheckedIdenticalPaths: Set<String>,
            precheckedNonIdenticalPaths: Set<String>,
            existingProgressDialog: CryptoProgressDialog? ->
            val activeDialog = existingProgressDialog ?: CryptoProgressDialog(
                activity = this,
                max = fileDirItems.size,
                titleId = titleId,
                notificationChannelId = OperationNotificationCategory.CHANNEL_COPY_MOVE,
                notificationIconRes = org.fossify.commons.R.drawable.ic_copy_vector,
                enableNotification = false
            )
            ensureNotificationPermissionThenRun { notificationsAllowed ->
                if (isFinishing || isDestroyed) {
                    activeDialog.dismiss()
                    return@ensureNotificationPermissionThenRun
                }
                activeDialog.ensureNotificationStarted(notificationsAllowed)
                activeDialog.updateProgress(0, fileDirItems.size)
                executeCopyMoveWithProgress(
                    fileDirItems = fileDirItems,
                    destination = destination,
                    isCopyOperation = isCopyOperation,
                    useStreamCopy = useStreamCopy,
                    copyMediaOnly = copyMediaOnly,
                    copyHidden = copyHidden,
                    conflictResolutions = conflictResolutions,
                    precheckedIdenticalPaths = precheckedIdenticalPaths,
                    precheckedNonIdenticalPaths = precheckedNonIdenticalPaths,
                    progressDialog = activeDialog,
                    callback = callback
                )
            }
        }

        when (config.conflictHandlingMode) {
            CONFLICT_HANDLING_STANDARD_DIALOG -> {
                ensureBackgroundThread {
                    val standardConflicts = ArrayList<StandardConflictItem>()
                    val targetIndexes = HashMap<String, CryptoManager.TargetPlainIndex>()
                    val destDir = File(destination)

                    fun getIndex(dir: File): CryptoManager.TargetPlainIndex {
                        return targetIndexes.getOrPut(dir.absolutePath) {
                            CryptoManager.TargetPlainIndex(dir)
                        }
                    }

                    fun collectStandardFileConflict(srcFile: File, targetDir: File, displayName: String) {
                        val resolutionKey = File(targetDir, displayName).absolutePath
                        val exists = getIndex(targetDir).findExact(displayName) != null || getDoesFilePathExist(resolutionKey)
                        if (exists) {
                            standardConflicts.add(
                                StandardConflictItem(
                                    resolutionKey = resolutionKey,
                                    fileDirItem = FileDirItem(
                                        path = srcFile.absolutePath,
                                        name = displayName,
                                        isDirectory = false,
                                        modified = srcFile.lastModified(),
                                        size = srcFile.length()
                                    )
                                )
                            )
                        }
                    }

                    fun collectStandardDirConflicts(sourceDir: File, targetDir: File) {
                        if (!targetDir.exists() || !targetDir.isDirectory) return
                        sourceDir.listFiles()?.forEach { child ->
                            if (child.isDirectory) {
                                collectStandardDirConflicts(child, File(targetDir, child.name))
                            } else if (!copyMediaOnly || child.absolutePath.isMediaFile()) {
                                collectStandardFileConflict(child, targetDir, child.name)
                            }
                        }
                    }

                    for (item in fileDirItems) {
                        val srcFile = File(item.path)
                        if (item.isDirectory) {
                            collectStandardDirConflicts(srcFile, File(destDir, item.name))
                        } else {
                            collectStandardFileConflict(srcFile, destDir, item.name)
                        }
                    }

                    if (standardConflicts.isNotEmpty()) {
                        runOnUiThread {
                            initialProgressDialog.dismiss()
                            resolveStandardFilenameConflicts(standardConflicts) { resolutions ->
                                proceedAfterConflicts(resolutions, emptySet(), emptySet(), null)
                            }
                        }
                    } else {
                        runOnUiThread {
                            initialProgressDialog.dismiss()
                            proceedAfterConflicts(LinkedHashMap(), emptySet(), emptySet(), null)
                        }
                    }
                }
            }
            CONFLICT_HANDLING_SMART_DIALOG -> {
                ensureBackgroundThread {
                    val conflicts = ArrayList<SmartConflictItem>()
                    val precheckedIdenticalPaths = HashSet<String>()
                    val precheckedNonIdenticalPaths = HashSet<String>()
                    val targetIndexes = HashMap<String, CryptoManager.TargetPlainIndex>()
                    val destDir = File(destination)

                    fun getIndex(dir: File): CryptoManager.TargetPlainIndex {
                        return targetIndexes.getOrPut(dir.absolutePath) {
                            CryptoManager.TargetPlainIndex(dir)
                        }
                    }

                    val useFastSampling = config.fastLargeDuplicateCheck

                    fun collectLocalConflicts(sourceDir: File, targetDir: File) {
                        if (!targetDir.exists() || !targetDir.isDirectory) return
                        val index = getIndex(targetDir)
                        sourceDir.listFiles()?.forEach { child ->
                            if (child.isDirectory) {
                                collectLocalConflicts(child, File(targetDir, child.name))
                            } else if (!copyMediaOnly || child.absolutePath.isMediaFile()) {
                                val check = CryptoManager.inspectTransferConflict(
                                    sourceFile = child,
                                    targetDir = targetDir,
                                    isTargetEncrypted = false,
                                    plainIndex = index,
                                    fastSampling = useFastSampling,
                                    context = applicationContext
                                )
                                when (check.status) {
                                    CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                                        precheckedIdenticalPaths.add(child.absolutePath)
                                    }
                                    CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                                        precheckedNonIdenticalPaths.add(child.absolutePath)
                                        val resolutionKey = File(targetDir, child.name).absolutePath
                                        conflicts.add(
                                            SmartConflictItem(
                                                resolutionKey = resolutionKey,
                                                displayName = check.displayName ?: child.name,
                                                sourcePath = child.absolutePath,
                                                existingPath = check.existingFile?.absolutePath ?: resolutionKey,
                                                useFastLargeDuplicateCheck = useFastSampling
                                            )
                                        )
                                    }
                                    CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
                                }
                            }
                        }
                    }

                    for (item in fileDirItems) {
                        val srcFile = File(item.path)
                        if (item.isDirectory) {
                            collectLocalConflicts(srcFile, File(destDir, item.name))
                        } else {
                            val index = getIndex(destDir)
                            val check = CryptoManager.inspectTransferConflict(
                                sourceFile = srcFile,
                                targetDir = destDir,
                                isTargetEncrypted = false,
                                plainIndex = index,
                                fastSampling = useFastSampling,
                                context = applicationContext
                            )
                            when (check.status) {
                                CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                                    precheckedIdenticalPaths.add(srcFile.absolutePath)
                                }
                                CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                                    precheckedNonIdenticalPaths.add(srcFile.absolutePath)
                                    val resolutionKey = File(destDir, item.name).absolutePath
                                    conflicts.add(
                                        SmartConflictItem(
                                            resolutionKey = resolutionKey,
                                            displayName = check.displayName ?: item.name,
                                            sourcePath = srcFile.absolutePath,
                                            existingPath = check.existingFile?.absolutePath ?: resolutionKey,
                                            useFastLargeDuplicateCheck = useFastSampling
                                        )
                                    )
                                }
                                CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
                            }
                        }
                    }

                    if (conflicts.isNotEmpty()) {
                        runOnUiThread {
                            initialProgressDialog.dismiss()
                            resolveSmartContentConflicts(conflicts) { resolutions ->
                                proceedAfterConflicts(resolutions, precheckedIdenticalPaths, precheckedNonIdenticalPaths, null)
                            }
                        }
                    } else {
                        runOnUiThread {
                            initialProgressDialog.dismiss()
                            proceedAfterConflicts(LinkedHashMap(), precheckedIdenticalPaths, precheckedNonIdenticalPaths, null)
                        }
                    }
                }
            }
            else -> {
                initialProgressDialog.dismiss()
                proceedAfterConflicts(LinkedHashMap(), emptySet(), emptySet(), null)
            }
        }
    }

    if (!useStreamCopy) {
        startAfterSpaceCheck()
        return
    }

    ensureBackgroundThread {
        fileDirItems.forEach { item ->
            if (item.size == 0L) {
                item.size = item.getProperSize(applicationContext, copyHidden)
            }
        }
        val probe = SpaceEstimator.probe(destination)
        val estimate = SpaceEstimator.forPlain(fileDirItems.map { it.size })
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                initialProgressDialog.dismiss()
                return@runOnUiThread
            }
            when {
                probe.verifiable && !SpaceEstimator.hasEnough(probe.availableBytes, estimate.peakBytes) -> {
                    initialProgressDialog.dismiss()
                    val text = String.format(
                        getString(org.fossify.commons.R.string.no_space),
                        estimate.peakBytes.formatSize(),
                        probe.availableBytes.formatSize()
                    )
                    toast(text, Toast.LENGTH_LONG)
                }
                !probe.verifiable -> {
                    // ADR 0003 §3a: SAF/OTG cannot report free space — require explicit consent.
                    SpaceConfirmation.askAsync(
                        context = this,
                        probe = probe,
                        estimate = estimate,
                        onApproved = { startAfterSpaceCheck() },
                        onRejected = { initialProgressDialog.dismiss() }
                    )
                }
                else -> {
                    initialProgressDialog.dismiss()
                    startAfterSpaceCheck()
                }
            }
        }
    }
}

private fun BaseSimpleActivity.executeCopyMoveWithProgress(
    fileDirItems: ArrayList<FileDirItem>,
    destination: String,
    isCopyOperation: Boolean,
    useStreamCopy: Boolean,
    copyMediaOnly: Boolean,
    copyHidden: Boolean,
    conflictResolutions: LinkedHashMap<String, Int>,
    precheckedIdenticalPaths: Set<String>,
    precheckedNonIdenticalPaths: Set<String>,
    progressDialog: CryptoProgressDialog,
    callback: (destinationPath: String) -> Unit
) {
    val config = CopyMoveConfig(
        destination = destination,
        isCopyOperation = isCopyOperation,
        useStreamCopy = useStreamCopy,
        copyMediaOnly = copyMediaOnly,
        copyHidden = copyHidden,
        conflictResolutions = conflictResolutions,
        precheckedIdenticalPaths = precheckedIdenticalPaths,
        precheckedNonIdenticalPaths = precheckedNonIdenticalPaths
    )

    ensureBackgroundThread {
        val result = ForegroundOperationRegistry.withOperation(this, "copy-move") {
            val runner = CopyMoveRunner(
                activity = this,
                config = config,
                progressDialog = progressDialog
            )
            runner.execute(fileDirItems)
        }
        if (result == null) {
            runOnUiThread {
                progressDialog.dismiss()
                toast(org.fossify.commons.R.string.copy_move_failed)
            }
            return@ensureBackgroundThread
        }

        runOnUiThread {
            progressDialog.dismiss()
            val finishUi = {
                showCopyMoveCompletionToast(
                    isCopyOperation = isCopyOperation,
                    taskSucceeded = result.succeeded,
                    transferredCount = result.transferredCount,
                    expectedCount = result.expectedCount,
                    skippedConflictCount = result.skippedConflictCount,
                    identicalSkippedCount = result.identicalSkippedCount
                )
                if (result.succeeded) {
                    val sourceFolders = fileDirItems.mapNotNull { item ->
                        if (item.isDirectory) item.path else item.getParentPath()
                    }.filter { it.isNotEmpty() }
                    MediaIndexCoordinator.markFoldersDirty(sourceFolders + destination)
                    callback(destination)
                }
            }
            if (result.itemsToDeleteSdk30.isNotEmpty()) {
                val uris = getFileUrisFromFileDirItems(result.itemsToDeleteSdk30)
                deleteSDK30Uris(uris) { deleted ->
                    if (deleted) {
                        ensureBackgroundThread {
                            result.itemsToDeleteSdk30.forEach { deleteFromMediaStore(it.path) }
                        }
                    }
                    finishUi()
                }
            } else {
                finishUi()
            }
        }
    }
}

private fun BaseSimpleActivity.showCopyMoveCompletionToast(
    isCopyOperation: Boolean,
    taskSucceeded: Boolean,
    transferredCount: Int,
    expectedCount: Int,
    skippedConflictCount: Int = 0,
    identicalSkippedCount: Int = 0
) {
    if (!taskSucceeded) {
        toast(org.fossify.commons.R.string.copy_move_failed)
        return
    }
    if (transferredCount == 0 && expectedCount == 0 && skippedConflictCount > 0) {
        showSkippedIdenticalToast(identicalSkippedCount)
        return
    }
    val allSucceeded = transferredCount >= expectedCount
    val isSingle = transferredCount == 1
    val msgId = if (isCopyOperation) {
        when {
            !allSucceeded -> org.fossify.commons.R.string.copying_success_partial
            isSingle -> org.fossify.commons.R.string.copying_success_one
            else -> org.fossify.commons.R.string.copying_success
        }
    } else {
        when {
            !allSucceeded -> org.fossify.commons.R.string.moving_success_partial
            isSingle -> org.fossify.commons.R.string.moving_success_one
            else -> org.fossify.commons.R.string.moving_success
        }
    }
    toast(msgId)
    showSkippedIdenticalToast(identicalSkippedCount)
}

private data class CopyMoveConfig(
    val destination: String,
    val isCopyOperation: Boolean,
    val useStreamCopy: Boolean,
    val copyMediaOnly: Boolean,
    val copyHidden: Boolean,
    val conflictResolutions: LinkedHashMap<String, Int>,
    val precheckedIdenticalPaths: Set<String> = emptySet(),
    val precheckedNonIdenticalPaths: Set<String> = emptySet()
)

private data class CopyMoveResult(
    val succeeded: Boolean,
    val transferredCount: Int,
    val expectedCount: Int,
    val skippedConflictCount: Int,
    val itemsToDeleteSdk30: ArrayList<FileDirItem>,
    val identicalSkippedCount: Int = 0
)

private sealed class TopLevelResolution {
    data object SkippedConflict : TopLevelResolution()
    data class Proceed(val destItem: FileDirItem) : TopLevelResolution()
}

/** A copied destination that still needs to be indexed by MediaScanner. */
private data class PendingRescan(val path: String, val lastModified: Long, val dateTaken: Long)

private class CopyMoveRunner(
    private val activity: BaseSimpleActivity,
    private val config: CopyMoveConfig,
    private val progressDialog: CryptoProgressDialog
) {
    private val transferredFiles = ArrayList<FileDirItem>()
    private val itemsToDeleteSdk30 = ArrayList<FileDirItem>()
    private val movedPathsToRescan = ArrayList<String>()
    // Stream-copied files are collected here and indexed in one bulk pass at the end instead of
    // firing one MediaScanner request per file. Each request used to change the MediaStore ids and
    // trigger a full album re-scan via MainActivity.checkLastMediaChanged().
    private val copiedPathsToRescan = ArrayList<PendingRescan>()
    private val documents = LinkedHashMap<String, DocumentFile?>()
    private val destIndexMap = HashMap<String, CryptoManager.TargetPlainIndex>()

    private var totalItems = 0
    private var processedItems = 0
    /** Silently skipped files whose name, bytes and timestamps all match at the destination. */
    private var identicalSkippedCount = 0
    private var totalBytes = 0L
    private var transferredBytes = 0L
    private var lastUiUpdateMs = 0L

    /** ADR 0003: one journal context per batch, one item index per physical write. */
    private var operation: OperationContext = OperationContext.NONE
    private var nextItemIndex = 0

    private fun getDestIndex(dir: File): CryptoManager.TargetPlainIndex {
        return destIndexMap.getOrPut(dir.absolutePath) {
            val preExistingPaths = dir.listFiles()?.mapTo(HashSet()) { it.absolutePath } ?: emptySet()
            CryptoManager.TargetPlainIndex(dir, preExistingPaths)
        }
    }

    private fun isDuplicatePlainInTarget(
        srcFile: File,
        desiredName: String,
        destIndex: CryptoManager.TargetPlainIndex
    ): Boolean {
        if (!srcFile.isFile) return false
        if (activity.config.conflictHandlingMode == CONFLICT_HANDLING_STANDARD_DIALOG) return false
        if (srcFile.absolutePath in config.precheckedIdenticalPaths) {
            identicalSkippedCount++
            return true
        }
        if (srcFile.absolutePath in config.precheckedNonIdenticalPaths) return false

        val candidates = destIndex.findCandidates(desiredName, includeCollisionVariants = true)
        if (candidates.isEmpty()) return false

        val srcLastMod = srcFile.lastModified()
        val srcSize = srcFile.length()
        var resolvedSrcDateTaken: Long? = null
        fun getSrcDateTaken(): Long {
            return resolvedSrcDateTaken ?: CryptoManager.resolvePlaintextDateTaken(
                context = activity.applicationContext,
                sourceFile = srcFile,
                fallbackLastModified = srcLastMod
            ).also { resolvedSrcDateTaken = it }
        }

        val gateByDate = activity.applicationContext.shouldGateDuplicatesByDate()
        return candidates.any { existing ->
            if (!existing.isFile || existing.length() != srcSize) return@any false
            val existingLastMod = existing.lastModified()
            if (gateByDate && !CryptoManager.areTimestampsEquivalent(srcLastMod, existingLastMod)) return@any false
            val existingTaken = CryptoManager.resolvePlaintextDateTaken(
                context = activity.applicationContext,
                sourceFile = existing,
                fallbackLastModified = existingLastMod
            )
            if (gateByDate && !CryptoManager.areMediaTimestampsEquivalent(
                    srcLastMod,
                    getSrcDateTaken(),
                    existingLastMod,
                    existingTaken
                )
            ) {
                return@any false
            }
            val identical = CryptoManager.arePlainFilesIdentical(
                srcFile,
                existing,
                fastSampling = activity.config.fastLargeDuplicateCheck
            )
            if (identical) {
                identicalSkippedCount++
                CryptoManager.applyDuplicateDateToPlainFile(
                    context = activity.applicationContext,
                    target = existing,
                    lastModified = srcLastMod,
                    dateTaken = getSrcDateTaken()
                )
            }
            identical
        }
    }

    fun execute(items: ArrayList<FileDirItem>): CopyMoveResult {
        totalItems = items.size
        var expectedCount = items.size
        var skippedConflictCount = 0
        totalBytes = computeTotalBytes(items)

        operation = OperationContext(
            operationId = OperationJournal.newOperationId(),
            kind = if (config.isCopyOperation) OperationJournal.KIND_COPY else OperationJournal.KIND_MOVE,
            batch = BatchOperationInfo(
                destRoot = config.destination,
                peakBytes = totalBytes,
                isMove = !config.isCopyOperation,
                wipeSource = false,
                deleteSource = !config.isCopyOperation
            )
        )
        val destDir = File(config.destination)
        nextItemIndex = items.size
        OperationJournal.beginBatch(
            operation,
            items.map {
                val targetPath = File(destDir, it.name).absolutePath
                val resolution = config.conflictResolutions[targetPath] ?: CONFLICT_KEEP_BOTH
                JournalItem(
                    sourcePath = it.path,
                    destPath = targetPath,
                    sourceSize = it.size,
                    resolvedDestName = it.name,
                    kind = operation.kind,
                    conflictResolution = resolution.toString()
                )
            }
        )

        val succeeded = try {
            for ((index, item) in items.withIndex()) {
                if (ForegroundOperationRegistry.isCancelled) {
                    operation.operationId?.let { OperationJournal.clear(it) }
                    return CopyMoveResult(
                        succeeded = false,
                        transferredCount = transferredFiles.size,
                        expectedCount = expectedCount,
                        skippedConflictCount = skippedConflictCount,
                        itemsToDeleteSdk30 = itemsToDeleteSdk30,
                        identicalSkippedCount = identicalSkippedCount
                    )
                }
                val itemCtx = OperationContext(operation.operationId, index, operation.kind, operation.batch)
                when (val outcome = resolveTopLevelDestination(item)) {
                    TopLevelResolution.SkippedConflict -> {
                        OperationJournal.markSkipped(itemCtx)
                        expectedCount--
                        skippedConflictCount++
                        processedItems++
                        publishProgress(forceUi = true, filename = item.name)
                    }
                    is TopLevelResolution.Proceed -> {
                        publishProgress(forceUi = true, filename = item.name)
                        transferTopLevelItem(item, outcome.destItem, topLevelIndex = index)
                        processedItems++
                        publishProgress(forceUi = true, filename = item.name)
                        if (OperationFaults.abortAfterItem(operation, processedItems - 1)) {
                            throw IOException("Fault injection: simulated process death after item ${processedItems - 1}")
                        }
                    }
                }
            }
            // One bulk MediaScanner pass for every transferred file (rename fast paths plus
            // stream copies) instead of one request per copied file. A single pass changes the
            // MediaStore ids once, so the album grid is reloaded once instead of N times.
            val allPathsToRescan = ArrayList<String>(movedPathsToRescan.size + copiedPathsToRescan.size)
            allPathsToRescan.addAll(movedPathsToRescan)
            allPathsToRescan.addAll(copiedPathsToRescan.map { it.path })
            if (allPathsToRescan.isNotEmpty()) {
                activity.rescanPaths(allPathsToRescan) {
                    if (activity.baseConfig.keepLastModified) {
                        copiedPathsToRescan.forEach {
                            updateLastModifiedValues(it.path, it.lastModified, it.dateTaken)
                        }
                    }
                }
            }
            operation.operationId?.let { OperationJournal.clear(it) }
            true
        } catch (e: Exception) {
            if (OperationFaults.isSimulatedAbort(e)) {
                throw e
            }
            activity.showErrorToast(e)
            false
        }

        return CopyMoveResult(
            succeeded = succeeded,
            transferredCount = transferredFiles.size,
            expectedCount = expectedCount,
            skippedConflictCount = skippedConflictCount,
            itemsToDeleteSdk30 = itemsToDeleteSdk30,
            identicalSkippedCount = identicalSkippedCount
        )
    }

    private fun computeTotalBytes(items: ArrayList<FileDirItem>): Long {
        val destDir = File(config.destination)
        val destIndex = getDestIndex(destDir)
        var bytes = 0L
        for (item in items) {
            if (!item.isDirectory && item.path in config.precheckedIdenticalPaths) {
                continue
            }
            if (item.size == 0L) {
                item.size = item.getProperSize(activity.applicationContext, config.copyHidden)
            }
            val targetPath = File(destDir, item.name).absolutePath
            val exists = destIndex.containsName(item.name) || activity.getDoesFilePathExist(targetPath)
            val resolution = config.conflictResolutions[targetPath] ?: CONFLICT_KEEP_BOTH
            if (resolution != CONFLICT_SKIP || !exists) {
                bytes += item.size
            }
        }
        return bytes
    }

    private fun resolveTopLevelDestination(item: FileDirItem): TopLevelResolution {
        val destDir = File(config.destination)
        val targetPath = File(destDir, item.name).absolutePath
        var destItem = FileDirItem(targetPath, item.name, item.isDirectory)

        if (item.isDirectory) {
            return TopLevelResolution.Proceed(destItem)
        }

        val destIndex = getDestIndex(destDir)
        val srcFile = File(item.path)
        if (isDuplicatePlainInTarget(srcFile, item.name, destIndex)) {
            return TopLevelResolution.SkippedConflict
        }

        val existingExact = destIndex.findExact(item.name)
        if (existingExact == null && !activity.getDoesFilePathExist(targetPath)) {
            destIndex.recordFile(File(targetPath))
            return TopLevelResolution.Proceed(destItem)
        }

        when (config.conflictResolutions[targetPath] ?: CONFLICT_KEEP_BOTH) {
            CONFLICT_SKIP -> return TopLevelResolution.SkippedConflict
            CONFLICT_KEEP_BOTH -> {
                val altFile = destIndex.getAlternativeFile(item.name)
                destIndex.recordFile(altFile)
                destItem = FileDirItem(altFile.path, altFile.name, false)
            }
            CONFLICT_OVERWRITE -> {
                if (!config.useStreamCopy) {
                    File(destItem.path).delete()
                }
            }
        }
        return TopLevelResolution.Proceed(destItem)
    }

    private fun transferTopLevelItem(source: FileDirItem, dest: FileDirItem, topLevelIndex: Int) {
        if (!config.useStreamCopy && !source.isDirectory) {
            val srcFile = File(source.path)
            val dstFile = File(dest.path)
            if (!dstFile.exists() && srcFile.renameTo(dstFile)) {
                if (!activity.baseConfig.keepLastModified) {
                    dstFile.setLastModified(System.currentTimeMillis())
                }
                if (!config.isCopyOperation) {
                    runCatching { activity.updateDBMediaPath(source.path, dstFile.absolutePath) }
                    srcFile.parentFile?.let { activity.cleanupEmptyPlaintextAlbumIfNeeded(it, forceIfEmpty = false) }
                }
                movedPathsToRescan.add(dstFile.absolutePath)
                activity.deleteFromMediaStore(source.path)
                transferredFiles.add(source)
                transferredBytes += source.size
                val itemCtx = OperationContext(operation.operationId, topLevelIndex, operation.kind, operation.batch)
                OperationJournal.markVerified(itemCtx, "", dstFile.absolutePath)
                if (!config.isCopyOperation) {
                    OperationJournal.markSourceDeleted(itemCtx)
                }
                return
            }
        }
        copy(source, dest, topLevelIndex = topLevelIndex)
    }

    private fun copy(source: FileDirItem, dest: FileDirItem, topLevelIndex: Int? = null) {
        if (source.isDirectory) {
            copyDirectory(source, dest.path, topLevelIndex = topLevelIndex)
        } else {
            copyFile(source, dest, topLevelIndex = topLevelIndex)
        }
    }

    private fun copyDirectory(source: FileDirItem, destPath: String, topLevelIndex: Int? = null) {
        val dirCtx = topLevelIndex?.let {
            OperationContext(operation.operationId, it, operation.kind, operation.batch)
        }
        if (!activity.createDirectorySync(destPath)) {
            val msg = activity.getString(org.fossify.commons.R.string.could_not_create_folder)
            activity.showErrorToast(String.format(msg, destPath))
            dirCtx?.let { OperationJournal.markFailed(it, "could not create folder") }
            return
        }

        when {
            activity.isPathOnOTG(source.path) -> {
                val children = activity.getDocumentFile(source.path)?.listFiles() ?: run {
                    dirCtx?.let { OperationJournal.markSkipped(it) }
                    return
                }
                copyDocumentChildren(source, destPath, children)
            }
            activity.isRestrictedSAFOnlyRoot(source.path) -> {
                activity.getAndroidSAFFileItems(source.path, true, false) { files ->
                    copyFileDirChildren(source, destPath, files)
                }
            }
            activity.isAccessibleWithSAFSdk30(source.path) -> {
                val children = activity.getDocumentSdk30(source.path)?.listFiles() ?: run {
                    dirCtx?.let { OperationJournal.markSkipped(it) }
                    return
                }
                copyDocumentChildren(source, destPath, children)
            }
            else -> {
                val childNames = File(source.path).list() ?: run {
                    dirCtx?.let { OperationJournal.markSkipped(it) }
                    return
                }
                copyLocalChildren(source, destPath, childNames)
            }
        }
        dirCtx?.let {
            OperationJournal.markVerified(it, "", destPath)
            if (!config.isCopyOperation && !File(source.path).exists()) {
                OperationJournal.markSourceDeleted(it)
            }
        }
    }

    private fun copyDocumentChildren(
        source: FileDirItem,
        destPath: String,
        children: Array<DocumentFile>
    ) {
        children.forEach { child ->
            val childName = child.name ?: return@forEach
            val targetPath = "$destPath/$childName"
            if (!File(targetPath).exists()) {
                val srcChild = FileDirItem(
                    path = "${source.path}/$childName",
                    name = childName,
                    isDirectory = child.isDirectory,
                    size = child.length()
                )
                copy(srcChild, FileDirItem(targetPath, childName, child.isDirectory))
            }
        }
        transferredFiles.add(source)
    }

    private fun copyFileDirChildren(
        source: FileDirItem,
        destPath: String,
        children: List<FileDirItem>
    ) {
        children.forEach { file ->
            val targetPath = "$destPath/${file.name}"
            if (!activity.getDoesFilePathExist(targetPath)) {
                val srcChild = FileDirItem(
                    path = "${source.path}/${file.name}",
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = file.size
                )
                copy(srcChild, FileDirItem(targetPath, file.name, file.isDirectory))
            }
        }
        transferredFiles.add(source)
    }

    private fun copyLocalChildren(
        source: FileDirItem,
        destPath: String,
        childNames: Array<String>
    ) {
        val destDir = File(destPath)
        val destIndex = getDestIndex(destDir)
        var anyChildSkipped = false
        var anyChildTransferred = false

        childNames.forEach { childName ->
            val srcFile = File(source.path, childName)
            val srcChild = srcFile.toFileDirItem(activity)
            if (srcFile.isDirectory) {
                val targetDirPath = File(destDir, childName).absolutePath
                copy(srcChild, FileDirItem(targetDirPath, childName, true))
                anyChildTransferred = true
            } else {
                val existing = destIndex.findExact(childName)
                val isIdentical = isDuplicatePlainInTarget(srcFile, childName, destIndex)

                if (isIdentical) {
                    anyChildSkipped = true
                } else {
                    val exactTarget = existing ?: File(destDir, childName)
                    val targetFile = if (existing != null || activity.getDoesFilePathExist(exactTarget.absolutePath)) {
                        when (config.conflictResolutions[exactTarget.absolutePath] ?: CONFLICT_KEEP_BOTH) {
                            CONFLICT_SKIP -> {
                                anyChildSkipped = true
                                null
                            }
                            CONFLICT_OVERWRITE -> exactTarget
                            else -> destIndex.getAlternativeFile(childName).also { destIndex.recordFile(it) }
                        }
                    } else {
                        exactTarget.also { destIndex.recordFile(it) }
                    }
                    if (targetFile != null) {
                        val dstChild = FileDirItem(targetFile.absolutePath, targetFile.name, false)
                        copy(srcChild, dstChild)
                        anyChildTransferred = true
                    }
                }
            }
        }
        if (!config.isCopyOperation && !anyChildSkipped) {
            if (activity.cleanupEmptyPlaintextAlbumIfNeeded(File(source.path), forceIfEmpty = true)) {
                runCatching { activity.updateReferencesForFolderRename(source.path, destPath) }
            }
        }
        if (anyChildTransferred || !anyChildSkipped) {
            transferredFiles.add(source)
        }
    }

    private fun copyFile(source: FileDirItem, dest: FileDirItem, topLevelIndex: Int? = null) {
        val itemIndex = topLevelIndex ?: nextItemIndex++
        val itemContext = OperationContext(
            operationId = operation.operationId,
            itemIndex = itemIndex,
            kind = operation.kind,
            batch = operation.batch
        )

        if (config.copyMediaOnly && !source.path.isMediaFile()) {
            OperationJournal.markSkipped(itemContext)
            transferredBytes += source.size
            return
        }

        val destParent = dest.getParentPath()
        if (!activity.createDirectorySync(destParent)) {
            val msg = activity.getString(org.fossify.commons.R.string.could_not_create_folder)
            activity.showErrorToast(String.format(msg, destParent))
            OperationJournal.markFailed(itemContext, "could not create folder")
            transferredBytes += source.size
            return
        }

        val sourceFile = File(source.path)
        val capturedLastModified = sourceFile.lastModified().takeIf { it > 0L } ?: source.modified
        val capturedDateTaken = if (activity.baseConfig.keepLastModified) {
            CryptoManager.resolvePlaintextDateTaken(
                context = activity.applicationContext,
                sourceFile = sourceFile,
                fallbackLastModified = capturedLastModified
            )
        } else {
            0L
        }

        if (!documents.containsKey(destParent) && activity.needsStupidWritePermissions(dest.path)) {
            documents[destParent] = activity.getDocumentFile(destParent)
        }

        // Direct File targets use a File sibling; SAF targets use a sibling DocumentFile.
        val destDocument = documents[destParent]
        if (destDocument == null) {
            val ready = TransientOperationWaiter.awaitDestinationReady(
                context = activity,
                ctx = itemContext,
                destDir = File(destParent),
                requiredBytes = source.size
            )
            if (!ready) {
                return
            }
        }

        val fileStaging = if (destDocument == null) {
            AtomicStaging.fileFor(File(dest.path), itemContext)
        } else {
            null
        }
        val safStaging = if (destDocument != null) {
            runCatching {
                destDocument.createFile(source.path.getMimeType(), AtomicStaging.safStageName(itemContext))
            }.getOrNull()
        } else {
            null
        }
        if (destDocument != null && safStaging == null) {
            OperationJournal.markFailed(itemContext, "could not create SAF staging document")
            return
        }
        val precomputedDirectFingerprint = if (safStaging == null) {
            MediaIdentity.computeFingerprint(sourceFile)
        } else {
            ""
        }

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var transferSuccess = false
        var committed = false
        try {
            val (bytesCopied, sourceFingerprint) = TransientOperationWaiter.runWithTransientRetry(
                context = activity,
                ctx = itemContext,
                destDir = File(destParent),
                requiredBytes = source.size,
                maxAttempts = if (destDocument == null) 2 else 1,
                onDiscardAttempt = {
                    closeQuietly(inputStream)
                    closeQuietly(outputStream)
                    inputStream = null
                    outputStream = null
                    if (fileStaging != null) {
                        AtomicStaging.discard(fileStaging, containsPlaintext = true, context = activity.applicationContext)
                    }
                    if (safStaging != null) {
                        safStaging.uri.let {
                            runCatching { activity.contentResolver.openOutputStream(it, "wt")?.close() }
                        }
                    }
                }
            ) {
                OperationJournal.markInFlight(
                    ctx = itemContext,
                    stagingPath = fileStaging?.absolutePath ?: safStaging?.uri?.toString().orEmpty(),
                    resolvedDestName = dest.name,
                    actualDestPath = dest.path,
                    sourceFingerprint = precomputedDirectFingerprint
                )
                val attemptDigest = if (safStaging != null) {
                    runCatching { MessageDigest.getInstance("SHA-256") }.getOrNull()
                } else {
                    null
                }
                outputStream = when {
                    fileStaging != null -> FileOutputStream(fileStaging)
                    safStaging != null -> activity.contentResolver.openOutputStream(safStaging.uri)
                    else -> activity.getFileOutputStreamSync(dest.path, source.path.getMimeType(), destDocument)
                }
                inputStream = activity.getFileInputStreamSync(source.path)
                val copied = streamCopy(inputStream, outputStream, source.name, attemptDigest)
                closeQuietly(inputStream)
                closeQuietly(outputStream)
                inputStream = null
                outputStream = null
                val fp = attemptDigest?.digest()?.joinToString("") { "%02x".format(it) }
                    ?: precomputedDirectFingerprint
                copied to fp
            }

            val landed = when {
                fileStaging != null -> fileStaging.exists() && fileStaging.length() == source.size
                safStaging != null -> safStaging.length() == source.size
                else -> activity.getDoesFilePathExist(dest.path)
            }
            if (source.size == bytesCopied && landed) {
                if (fileStaging != null) {
                    AtomicStaging.commit(fileStaging, File(dest.path))
                    committed = true
                } else if (safStaging != null) {
                    // DocumentFile.renameTo is a directory-entry operation. No copy+delete fallback:
                    // on failure the item is FAILED and the source stays intact (ADR 0003 §3a).
                    if (!AtomicStaging.commitSaf(safStaging, File(dest.path).name)) {
                        throw IOException("SAF atomic rename failed")
                    }
                    committed = true
                }
                val destFingerprint = if (safStaging != null) {
                    AtomicStaging.safFingerprint(activity, safStaging.uri)
                } else {
                    MediaIdentity.computeFingerprint(File(dest.path))
                }
                if (sourceFingerprint.isEmpty() || destFingerprint == sourceFingerprint) {
                    OperationJournal.markVerified(itemContext, destFingerprint, dest.path)
                    transferSuccess = true
                } else {
                    OperationJournal.markFailed(itemContext, "copied fingerprint mismatch")
                }
            } else {
                OperationJournal.markFailed(itemContext, "incomplete copy")
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
            OperationJournal.markFailed(itemContext, e.message ?: "copy failed")
        } finally {
            closeQuietly(inputStream)
            closeQuietly(outputStream)
        }

        if (!transferSuccess) {
            if (fileStaging != null) {
                AtomicStaging.discard(fileStaging, containsPlaintext = true, context = activity.applicationContext)
            }
            if (safStaging != null) AtomicStaging.discardSaf(safStaging)
            if (committed && destDocument == null) runCatching { File(dest.path).delete() }
            return
        }

        transferredFiles.add(source)
        finalizeCopiedFile(dest, capturedLastModified, capturedDateTaken)
        if (!config.isCopyOperation) {
            runCatching { activity.updateDBMediaPath(source.path, dest.path) }
            if (activity.baseConfig.keepLastModified && capturedDateTaken > 0L) {
                CryptoManager.persistDecryptedDateTaken(
                    context = activity.applicationContext,
                    outFile = File(dest.path),
                    dateTaken = capturedDateTaken,
                    lastModified = capturedLastModified
                )
            }
            deleteSourceFile(source)
            OperationJournal.markSourceDeleted(itemContext)
            sourceFile.parentFile?.let { activity.cleanupEmptyPlaintextAlbumIfNeeded(it, forceIfEmpty = false) }
        }
    }

    private fun streamCopy(
        inputStream: InputStream?,
        outputStream: OutputStream?,
        filename: String,
        digest: MessageDigest? = null
    ): Long {
        if (inputStream == null || outputStream == null) {
            return 0L
        }
        var bytesCopied = 0L
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var bytesRead = inputStream.read(buffer)
        while (bytesRead >= 0) {
            outputStream.write(buffer, 0, bytesRead)
            digest?.update(buffer, 0, bytesRead)
            bytesCopied += bytesRead
            transferredBytes += bytesRead
            publishProgress(forceUi = false, filename = filename)
            bytesRead = inputStream.read(buffer)
        }
        outputStream.flush()
        return bytesCopied
    }

    private fun finalizeCopiedFile(dest: FileDirItem, lastModified: Long, dateTaken: Long) {
        if (activity.baseConfig.keepLastModified) {
            updateLastModifiedValues(dest.path, lastModified, dateTaken)
        }
        // Defer indexing to the single bulk rescan in execute(). The metadata is re-applied
        // once that scan completes, since MediaScanner may overwrite file timestamps.
        copiedPathsToRescan.add(PendingRescan(dest.path, lastModified, dateTaken))
    }

    private fun closeQuietly(stream: java.io.Closeable?) {
        try {
            stream?.close()
        } catch (_: Exception) {
        }
    }

    private fun deleteSourceFile(source: FileDirItem) {
        if (activity.isRestrictedWithSAFSdk30(source.path) && !activity.canManageMedia()) {
            itemsToDeleteSdk30.add(source)
        } else {
            activity.deleteFileBg(source, allowDeleteFolder = false, isDeletingMultipleFiles = false)
            activity.deleteFromMediaStore(source.path)
        }
    }

    private fun updateLastModifiedValues(destPath: String, lastModified: Long, dateTaken: Long) {
        if (lastModified > 0L) {
            File(destPath).setLastModified(lastModified)
        }
        CryptoManager.persistDecryptedDateTaken(
            context = activity.applicationContext,
            outFile = File(destPath),
            dateTaken = dateTaken,
            lastModified = lastModified
        )
    }

    private fun publishProgress(forceUi: Boolean, filename: String) {
        val totalKb = (totalBytes / BYTES_PER_KB).toInt()
        val currentKb = (transferredBytes / BYTES_PER_KB).toInt()
        val barMax = if (totalKb > 0) totalKb else maxOf(totalItems, 1)
        val barCurrent = if (totalKb > 0) currentKb.coerceIn(0, barMax) else processedItems.coerceIn(0, barMax)

        progressDialog.updateNotifierOnly(current = barCurrent, max = barMax, contentText = filename)

        val now = System.currentTimeMillis()
        if (forceUi || now - lastUiUpdateMs >= UI_PROGRESS_UPDATE_INTERVAL_MS) {
            lastUiUpdateMs = now
            activity.runOnUiThread {
                progressDialog.updateProgress(
                    current = processedItems,
                    total = totalItems,
                    barCurrent = barCurrent,
                    barMax = barMax,
                    notificationText = filename
                )
            }
        }
    }
}
