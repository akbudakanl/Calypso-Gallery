package org.fossify.gallery.helpers

import android.widget.Toast
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.canManageMedia
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileUrisFromFileDirItems
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.isRecycleBinPath
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.SmartConflictItem
import org.fossify.gallery.extensions.StandardConflictItem
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.resolveSmartContentConflicts
import org.fossify.gallery.extensions.resolveStandardFilenameConflicts
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.SpaceConfirmation
import org.fossify.gallery.helpers.operations.SpaceEstimator
import java.io.File
import java.io.IOException

internal const val COPY_BUFFER_SIZE = 256 * 1024
internal const val BYTES_PER_KB = 1000L
internal const val UI_PROGRESS_UPDATE_INTERVAL_MS = 100L

internal class CopyMoveCancelledException : IOException("Copy/move cancelled")

internal data class CopyMoveConfig(
    val destination: String,
    val isCopyOperation: Boolean,
    val useStreamCopy: Boolean,
    val copyMediaOnly: Boolean,
    val copyHidden: Boolean,
    val conflictResolutions: LinkedHashMap<String, Int>,
    val precheckedIdenticalPaths: Set<String> = emptySet(),
    val precheckedNonIdenticalPaths: Set<String> = emptySet()
)

internal data class CopyMoveResult(
    val succeeded: Boolean,
    val cancelled: Boolean,
    val transferredCount: Int,
    val expectedCount: Int,
    val skippedConflictCount: Int,
    val identicalSkippedCount: Int = 0
)

internal sealed class TopLevelResolution {
    data object SkippedConflict : TopLevelResolution()
    data class Proceed(val destItem: FileDirItem) : TopLevelResolution()
}

/** A copied destination that still needs to be indexed by MediaScanner. */
internal data class PendingRescan(val path: String, val lastModified: Long, val dateTaken: Long)

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
            val needsUpdateSdk30Uris = isCopyOperation &&
                canManageMedia() &&
                !isExternalStorageManager() &&
                fileDirItems.size <= SDK30_DELETE_CHUNK_SIZE &&
                !fileDirItems.first().isRecycleBinPath(this)
            if (needsUpdateSdk30Uris) {
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
            if (!result.cancelled) {
                showCopyMoveCompletionToast(
                    isCopyOperation = isCopyOperation,
                    taskSucceeded = result.succeeded,
                    transferredCount = result.transferredCount,
                    expectedCount = result.expectedCount,
                    skippedConflictCount = result.skippedConflictCount,
                    identicalSkippedCount = result.identicalSkippedCount
                )
            }
            if (result.succeeded || result.transferredCount > 0) {
                val sourceFolders = fileDirItems.mapNotNull { item ->
                    if (item.isDirectory) item.path else item.getParentPath()
                }.filter { it.isNotEmpty() }
                MediaIndexCoordinator.markFoldersDirty(sourceFolders + destination)
                callback(destination)
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
