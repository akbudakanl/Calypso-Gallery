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
import org.fossify.commons.extensions.getAvailableStorageB
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
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.SmartConflictItem
import org.fossify.gallery.extensions.StandardConflictItem
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.resolveSmartContentConflicts
import org.fossify.gallery.extensions.resolveStandardFilenameConflicts
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateReferencesForFolderRename
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val COPY_BUFFER_SIZE = 64 * 1024
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
    val initialProgressDialog = CryptoProgressDialog(
        activity = this,
        max = 0,
        titleId = titleId,
        notificationChannelId = OperationProgressNotifier.CHANNEL_COPY_MOVE,
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
                notificationChannelId = OperationProgressNotifier.CHANNEL_COPY_MOVE,
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
                            proceedAfterConflicts(LinkedHashMap(), emptySet(), emptySet(), initialProgressDialog)
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
                            proceedAfterConflicts(LinkedHashMap(), precheckedIdenticalPaths, precheckedNonIdenticalPaths, initialProgressDialog)
                        }
                    }
                }
            }
            else -> {
                proceedAfterConflicts(LinkedHashMap(), emptySet(), emptySet(), initialProgressDialog)
            }
        }
    }

    if (!useStreamCopy) {
        startAfterSpaceCheck()
        return
    }

    ensureBackgroundThread {
        val availableSpace = destination.getAvailableStorageB()
        val sumToCopy = fileDirItems.sumOf { item ->
            if (item.size == 0L) {
                item.size = item.getProperSize(applicationContext, copyHidden)
            }
            item.size
        }
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                initialProgressDialog.dismiss()
                return@runOnUiThread
            }
            if (availableSpace != -1L && sumToCopy >= availableSpace) {
                initialProgressDialog.dismiss()
                val text = String.format(
                    getString(org.fossify.commons.R.string.no_space),
                    sumToCopy.formatSize(),
                    availableSpace.formatSize()
                )
                toast(text, Toast.LENGTH_LONG)
            } else {
                startAfterSpaceCheck()
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
        val runner = CopyMoveRunner(
            activity = this,
            config = config,
            progressDialog = progressDialog
        )
        val result = runner.execute(fileDirItems)

        runOnUiThread {
            progressDialog.dismiss()
            val finishUi = {
                showCopyMoveCompletionToast(
                    isCopyOperation = isCopyOperation,
                    taskSucceeded = result.succeeded,
                    transferredCount = result.transferredCount,
                    expectedCount = result.expectedCount,
                    skippedConflictCount = result.skippedConflictCount
                )
                if (result.succeeded) {
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
    skippedConflictCount: Int = 0
) {
    if (!taskSucceeded) {
        toast(org.fossify.commons.R.string.copy_move_failed)
        return
    }
    if (transferredCount == 0 && expectedCount == 0 && skippedConflictCount > 0) {
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
    val itemsToDeleteSdk30: ArrayList<FileDirItem>
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
    private var totalBytes = 0L
    private var transferredBytes = 0L
    private var lastUiUpdateMs = 0L

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
        if (srcFile.absolutePath in config.precheckedIdenticalPaths) return true
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

        val succeeded = try {
            for (item in items) {
                when (val outcome = resolveTopLevelDestination(item)) {
                    TopLevelResolution.SkippedConflict -> {
                        expectedCount--
                        skippedConflictCount++
                        processedItems++
                        publishProgress(forceUi = true, filename = item.name)
                    }
                    is TopLevelResolution.Proceed -> {
                        publishProgress(forceUi = true, filename = item.name)
                        transferTopLevelItem(item, outcome.destItem)
                        processedItems++
                        publishProgress(forceUi = true, filename = item.name)
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
            true
        } catch (e: Exception) {
            activity.showErrorToast(e)
            false
        }

        return CopyMoveResult(
            succeeded = succeeded,
            transferredCount = transferredFiles.size,
            expectedCount = expectedCount,
            skippedConflictCount = skippedConflictCount,
            itemsToDeleteSdk30 = itemsToDeleteSdk30
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

    private fun transferTopLevelItem(source: FileDirItem, dest: FileDirItem) {
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
                return
            }
        }
        copy(source, dest)
    }

    private fun copy(source: FileDirItem, dest: FileDirItem) {
        if (source.isDirectory) {
            copyDirectory(source, dest.path)
        } else {
            copyFile(source, dest)
        }
    }

    private fun copyDirectory(source: FileDirItem, destPath: String) {
        if (!activity.createDirectorySync(destPath)) {
            val msg = activity.getString(org.fossify.commons.R.string.could_not_create_folder)
            activity.showErrorToast(String.format(msg, destPath))
            return
        }

        when {
            activity.isPathOnOTG(source.path) -> {
                val children = activity.getDocumentFile(source.path)?.listFiles() ?: return
                copyDocumentChildren(source, destPath, children)
            }
            activity.isRestrictedSAFOnlyRoot(source.path) -> {
                activity.getAndroidSAFFileItems(source.path, true, false) { files ->
                    copyFileDirChildren(source, destPath, files)
                }
            }
            activity.isAccessibleWithSAFSdk30(source.path) -> {
                val children = activity.getDocumentSdk30(source.path)?.listFiles() ?: return
                copyDocumentChildren(source, destPath, children)
            }
            else -> {
                val childNames = File(source.path).list() ?: return
                copyLocalChildren(source, destPath, childNames)
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

    private fun copyFile(source: FileDirItem, dest: FileDirItem) {
        if (config.copyMediaOnly && !source.path.isMediaFile()) {
            transferredBytes += source.size
            return
        }

        val destParent = dest.getParentPath()
        if (!activity.createDirectorySync(destParent)) {
            val msg = activity.getString(org.fossify.commons.R.string.could_not_create_folder)
            activity.showErrorToast(String.format(msg, destParent))
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

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var transferSuccess = false
        try {
            if (!documents.containsKey(destParent) && activity.needsStupidWritePermissions(dest.path)) {
                documents[destParent] = activity.getDocumentFile(destParent)
            }
            outputStream = activity.getFileOutputStreamSync(
                dest.path,
                source.path.getMimeType(),
                documents[destParent]
            )
            inputStream = activity.getFileInputStreamSync(source.path)
            val bytesCopied = streamCopy(inputStream, outputStream, source.name)

            if (source.size == bytesCopied && activity.getDoesFilePathExist(dest.path)) {
                transferSuccess = true
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        } finally {
            closeQuietly(inputStream)
            closeQuietly(outputStream)
        }

        if (transferSuccess) {
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
                sourceFile.parentFile?.let { activity.cleanupEmptyPlaintextAlbumIfNeeded(it, forceIfEmpty = false) }
            }
        }
    }

    private fun streamCopy(
        inputStream: InputStream?,
        outputStream: OutputStream?,
        filename: String
    ): Long {
        if (inputStream == null || outputStream == null) {
            return 0L
        }
        var bytesCopied = 0L
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var bytesRead = inputStream.read(buffer)
        while (bytesRead >= 0) {
            outputStream.write(buffer, 0, bytesRead)
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
