package org.fossify.gallery.extensions

import android.os.Environment
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.getDefaultCopyDestinationPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.EncryptConfirmationDialog
import org.fossify.gallery.dialogs.PickDirectoryDialog
import org.fossify.gallery.dialogs.resolveAlbumCryptoOptions
import org.fossify.gallery.dialogs.resolveSkippedCryptoFiles
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CopyMovePolicy
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DELETE_UI_PROGRESS_INTERVAL_MS
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.PickDirectoryMode
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.decryptFolder
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.helpers.findMergeTargetForDecryptFolder
import org.fossify.gallery.helpers.getDecryptedFolderName
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.operations.BatchOperationInfo
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.JournalItem
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationFaults
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.previewDecryptAlbumConflict
import org.fossify.gallery.helpers.resolveDecryptTargetInPickedDirectory
import org.fossify.gallery.helpers.resolveOriginDecryptTargetFolder
import org.fossify.gallery.helpers.resolvePickedPathToFile
import org.fossify.gallery.helpers.showSkippedIdenticalToast
import java.io.File

private const val MAX_DECRYPT_PREVIEW_ALBUMS = 4

fun BaseSimpleActivity.tryDecryptFilesTo(
    fileDirItems: ArrayList<FileDirItem>,
    currentDirectoryPath: String = "",
    callback: (destinationPath: String) -> Unit
) {
    if (fileDirItems.isEmpty()) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    ensureVaultUnlocked {
        val source = currentDirectoryPath.ifEmpty { fileDirItems[0].getParentPath() }
        PickDirectoryDialog(
            activity = this,
            sourcePath = source,
            showOtherFolderButton = true,
            showFavoritesBin = false,
            isPickingCopyMoveDestination = true,
            isPickingFolderForWidget = false,
            isCopyOperation = false,
            isHiddenCopySource = false,
            mode = PickDirectoryMode.DECRYPT_MOVE
        ) { destination ->
            EncryptConfirmationDialog(
                activity = this,
                message = getString(R.string.confirm_decrypt_hidden_file),
                infoTitleResId = R.string.decrypt_info_title,
                infoMessageResId = R.string.decrypt_info_message,
                isDecrypt = true
            ) {
                val destDir = File(destination)
                val initialProgressDialog = CryptoProgressDialog(
                    activity = this,
                    max = fileDirItems.size,
                    titleId = R.string.decrypting_files,
                    enableNotification = false
                )
                val runWithResolutions = {
                    conflictResolutions: LinkedHashMap<String, Int>,
                    precheckedIndex: CryptoManager.TargetPlainIndex?,
                    precheckedIdenticalTargets: Map<String, File>,
                    precheckedIdenticalPaths: Set<String>,
                    precheckedNonIdenticalPaths: Set<String>,
                    existingProgressDialog: CryptoProgressDialog? ->
                    val progressDialog = existingProgressDialog ?: CryptoProgressDialog(
                        activity = this,
                        max = fileDirItems.size,
                        titleId = R.string.decrypting_files,
                        enableNotification = false
                    )
                    ensureNotificationPermissionThenRun { notificationsAllowed ->
                        progressDialog.ensureNotificationStarted(notificationsAllowed)
                        ensureBackgroundThread {
                            val acquired = ForegroundOperationRegistry.acquire(this, "crypto-decrypt")
                            if (!acquired) {
                                runOnUiThread {
                                    try { progressDialog.dismiss() } catch (_: Exception) {}
                                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                                }
                                return@ensureBackgroundThread
                            }
                            try {
                            if (!destDir.exists()) {
                                destDir.mkdirs()
                            }
                            val execPlainIndex = precheckedIndex ?: CryptoManager.TargetPlainIndex(destDir)
                            val sourceDirs = LinkedHashSet<File>()
                            val skippedSourceFiles = ArrayList<File>()
                            val identicalTargetMap = HashMap<String, File>()
                            var processedCount = 0
                            var skippedCount = 0
                            var allSuccess = true
                            var anyFavoriteUpdated = false
                            var lastUiProgressMs = 0L

                            val encDecryptItems = fileDirItems.filter { CryptoManager.isEncryptedFile(it.name) }
                            val batchOp = if (encDecryptItems.isNotEmpty()) {
                                OperationContext(
                                    operationId = OperationJournal.newOperationId(),
                                    kind = OperationJournal.KIND_DECRYPT,
                                    batch = BatchOperationInfo(
                                        sourceRoot = encDecryptItems.first().getParentPath(),
                                        destRoot = destDir.absolutePath,
                                        isMove = true,
                                        wipeSource = false,
                                        deleteSource = true
                                    )
                                ).also { op ->
                                    OperationJournal.beginBatch(
                                        op,
                                        encDecryptItems.map { item ->
                                            val absPath = File(item.path).absolutePath
                                            JournalItem(
                                                sourcePath = item.path,
                                                destPath = File(destDir, item.name).absolutePath,
                                                sourceSize = item.size,
                                                resolvedDestName = item.name,
                                                kind = OperationJournal.KIND_DECRYPT,
                                                conflictResolution = (conflictResolutions[absPath] ?: CONFLICT_KEEP_BOTH).toString()
                                            )
                                        }
                                    )
                                }
                            } else {
                                OperationContext.NONE
                            }
                            val encIndexByPath = encDecryptItems.mapIndexed { idx, item -> item.path to idx }.toMap()

                            for (item in fileDirItems) {
                                if (ForegroundOperationRegistry.isCancelled) {
                                    allSuccess = false
                                    break
                                }
                                val srcFile = File(item.path)
                                val itemOp = encIndexByPath[item.path]?.let { idx ->
                                    OperationContext(batchOp.operationId, idx, OperationJournal.KIND_DECRYPT, batchOp.batch)
                                } ?: OperationContext.NONE
                                if (!srcFile.exists()) {
                                    OperationJournal.markFailed(itemOp, "source missing")
                                    continue
                                }
                                srcFile.parentFile?.let { sourceDirs.add(it) }
                                val resolution = conflictResolutions[srcFile.absolutePath] ?: CONFLICT_KEEP_BOTH

                                val isEnc = CryptoManager.isEncryptedFile(srcFile.name)
                                val desiredHiddenName = if (!isEnc && srcFile.name.startsWith('.')) {
                                    srcFile.name.substring(1)
                                } else {
                                    null
                                }

                                if (resolution == CONFLICT_SKIP) {
                                    OperationJournal.markSkipped(itemOp)
                                    skippedCount++
                                    skippedSourceFiles.add(srcFile)
                                } else {
                                    val identicalTarget = findIdenticalTransferTarget(
                                        sourceFile = srcFile,
                                        targetDir = destDir,
                                        isTargetEncrypted = false,
                                        plainIndex = execPlainIndex,
                                        desiredPlainName = desiredHiddenName,
                                        precheckedIdenticalTargets = precheckedIdenticalTargets,
                                        precheckedIdenticalPaths = precheckedIdenticalPaths,
                                        precheckedNonIdenticalPaths = precheckedNonIdenticalPaths
                                    )
                                    if (identicalTarget != null) {
                                        OperationJournal.markSkipped(itemOp)
                                        skippedCount++
                                        skippedSourceFiles.add(srcFile)
                                        identicalTargetMap[srcFile.absolutePath] = identicalTarget
                                    } else {
                                        val decFile = if (isEnc) {
                                            CryptoManager.decryptFileToDirectory(
                                                context = this,
                                                encryptedFile = srcFile,
                                                targetDir = destDir,
                                                deleteEncryptedSource = true,
                                                conflictResolution = resolution,
                                                targetIndex = execPlainIndex,
                                                skipDuplicateCheck = true,
                                                deleteSourceOnIdentical = false,
                                                operation = itemOp
                                            )
                                        } else if (desiredHiddenName != null) {
                                            val exactTarget = execPlainIndex.findExact(desiredHiddenName) ?: File(destDir, desiredHiddenName)
                                            val target = if (exactTarget.exists()) {
                                                when (resolution) {
                                                    CONFLICT_OVERWRITE -> {
                                                        exactTarget.delete()
                                                        execPlainIndex.removeFile(exactTarget)
                                                        exactTarget
                                                    }
                                                    else -> execPlainIndex.getAlternativeFile(desiredHiddenName)
                                                }
                                            } else {
                                                exactTarget
                                            }
                                            if (srcFile.renameTo(target)) {
                                                execPlainIndex.recordFile(target)
                                                updateDBMediaPath(srcFile.absolutePath, target.absolutePath)
                                                rescanPaths(arrayListOf(target.absolutePath))
                                                target
                                            } else {
                                                null
                                            }
                                        } else {
                                            null
                                        }

                                        if (decFile != null && decFile.exists()) {
                                            if (isEnc && !srcFile.exists()) {
                                                runCatching {
                                                    favoritesDB.updateFavorite(
                                                        decFile.name,
                                                        decFile.absolutePath,
                                                        decFile.parentFile?.absolutePath ?: destination,
                                                        srcFile.absolutePath
                                                    )
                                                    anyFavoriteUpdated = true
                                                }
                                            }
                                            processedCount++
                                        } else {
                                            allSuccess = false
                                        }
                                    }
                                }

                                val doneCount = processedCount + skippedCount
                                val now = System.currentTimeMillis()
                                if (doneCount >= fileDirItems.size || now - lastUiProgressMs >= 80L) {
                                    lastUiProgressMs = now
                                    runOnUiThread {
                                        progressDialog.updateProgress(doneCount, fileDirItems.size)
                                    }
                                }
                            }

                            batchOp.operationId?.let { OperationJournal.clearAfterCompletionOrCancel(it) }
                            if (anyFavoriteUpdated) {
                                runCatching { ReferenceBackupManager.saveFavorites(this) }
                            }
                            sourceDirs.forEach { dir ->
                                CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(this, dir)
                            }

                            CryptoManager.clearAppImageCaches(this)
                            MediaIndexCoordinator.markFoldersDirty(
                                sourceDirs.map { it.absolutePath } + destination,
                                includeFavorites = anyFavoriteUpdated
                            )
                            runOnUiThread {
                                try {
                                    progressDialog.dismiss()
                                } catch (_: Exception) {}

                                if (allSuccess && processedCount > 0) {
                                    toast(if (processedCount == 1) R.string.file_decrypted_successfully else R.string.files_decrypted_successfully)
                                } else if (allSuccess && skippedCount > 0) {
                                    // Skipped conflicting items cleanly per user choice
                                } else {
                                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                                }
                                showSkippedIdenticalToast(identicalTargetMap.size)
                                resolveSkippedCryptoFiles(
                                    isEncrypt = false,
                                    skippedFiles = skippedSourceFiles,
                                    identicalTargetMap = identicalTargetMap
                                ) {
                                    callback(destination)
                                }
                            }
                            } finally {
                                ForegroundOperationRegistry.release(this, "crypto-decrypt")
                            }
                        }
                    }
                }

                resolveCryptoTransferConflicts(
                    progressDialog = initialProgressDialog,
                    buildPairs = {
                        fileDirItems.mapNotNull { item ->
                            val srcFile = File(item.path)
                            if (!srcFile.exists() || !srcFile.isFile) return@mapNotNull null
                            val desiredName = if (!CryptoManager.isEncryptedFile(srcFile.name) && srcFile.name.startsWith('.')) {
                                srcFile.name.substring(1)
                            } else {
                                null
                            }
                            TransferPairSpec(srcFile, destDir, isTargetEncrypted = false, desiredPlainName = desiredName)
                        }
                    }
                ) { scan, activeProgressDialog ->
                    runWithResolutions(
                        scan.resolutions,
                        scan.plainIndexes[destDir.absolutePath],
                        scan.identicalTargets,
                        scan.identicalPaths,
                        scan.nonIdenticalPaths,
                        activeProgressDialog
                    )
                }
            }
        }
    }
}

fun BaseSimpleActivity.tryDecryptFolders(
    folderPaths: List<String>,
    callback: (destinationPath: String) -> Unit
) {
    if (folderPaths.isEmpty()) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    ensureVaultUnlocked {
        val singlePath = folderPaths.singleOrNull()
        val singleOrigin = singlePath?.let { CryptoManager.resolveOriginDecryptTargetFolder(this, File(it)) }
        if (singlePath == null || singleOrigin == null) {
            openDecryptFolderPickerWithConfirmation(folderPaths, callback)
            return@ensureVaultUnlocked
        }

        val originTargets = mapOf<String, File?>(singlePath to singleOrigin)
        val (message, destinationSummary) = buildDecryptConfirmationText(folderPaths, originTargets)
        EncryptConfirmationDialog(
            activity = this,
            message = message,
            infoTitleResId = R.string.decrypt_info_title,
            infoMessageResId = R.string.decrypt_info_message,
            isDecrypt = true,
            destinationLabel = getString(R.string.decrypt_destination_label),
            destinationValue = destinationSummary,
            neutralButtonTextResId = R.string.decrypt_to_other_location,
            onNeutralClick = {
                pickDecryptDestinationAndExecute(
                    folderPaths = folderPaths,
                    originTargets = originTargets,
                    overrideAll = true,
                    callback = callback
                )
            }
        ) {
            executeFolderDecryption(folderPaths, mapOf(singlePath to singleOrigin), callback)
        }
    }
}

fun BaseSimpleActivity.tryDecryptFoldersTo(
    folderPaths: List<String>,
    callback: (destinationPath: String) -> Unit
) {
    if (folderPaths.isEmpty()) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    ensureVaultUnlocked {
        openDecryptFolderPickerWithConfirmation(folderPaths, callback)
    }
}

private fun BaseSimpleActivity.openPhysicalDecryptParentPicker(
    sourcePath: String,
    onPicked: (destinationPath: String) -> Unit
) {
    val pickerShowHidden = CopyMovePolicy.shouldShowHiddenMediaInPicker(
        mode = PickDirectoryMode.DECRYPT_MOVE,
        showHiddenMedia = config.showHiddenMedia,
        shouldShowHidden = config.shouldShowHidden
    )
    val rawDefaultPath = getDefaultCopyDestinationPath(pickerShowHidden, sourcePath)
    val defaultPath = if (
        CryptoManager.isEncryptedFolderPath(this, rawDefaultPath) ||
        isEncryptedVaultAlbum(rawDefaultPath)
    ) {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.absolutePath
            ?: internalStoragePath
    } else {
        rawDefaultPath
    }
    FilePickerDialog(
        this,
        defaultPath,
        false,
        pickerShowHidden,
        true,
        true
    ) { picked ->
        if (CryptoManager.isEncryptedFolderPath(this, picked) || isEncryptedVaultAlbum(picked)) {
            toast(R.string.cannot_target_encrypted_album)
            return@FilePickerDialog
        }
        config.lastCopyPath = picked
        handleLockedFolderOpening(picked) { success ->
            if (success) {
                onPicked(picked)
            }
        }
    }
}

private fun BaseSimpleActivity.openDecryptFolderPickerWithConfirmation(
    folderPaths: List<String>,
    callback: (destinationPath: String) -> Unit
) {
    val source = if (folderPaths.size == 1) folderPaths.first() else ""
    openPhysicalDecryptParentPicker(source) { destination ->
        val pickedDir = CryptoManager.resolvePickedPathToFile(this, destination) ?: File(destination)
        val resolved = folderPaths.associateWith { path ->
            val folder = File(path)
            val albumName = CryptoManager.getDecryptedFolderName(folder, this) ?: folder.name
            CryptoManager.resolveDecryptTargetInPickedDirectory(pickedDir, albumName)
        }
        val (message, destinationSummary) = buildDecryptConfirmationText(folderPaths, resolved)
        EncryptConfirmationDialog(
            activity = this,
            message = message,
            infoTitleResId = R.string.decrypt_info_title,
            infoMessageResId = R.string.decrypt_info_message,
            isDecrypt = true,
            destinationLabel = getString(R.string.decrypt_destination_label),
            destinationValue = destinationSummary
        ) {
            executeFolderDecryption(folderPaths, resolved, callback)
        }
    }
}

private fun BaseSimpleActivity.buildDecryptConfirmationText(
    folderPaths: List<String>,
    resolvedTargets: Map<String, File?>
): Pair<String, String> {
    val firstFolder = File(folderPaths.first())
    val firstAlbumName = CryptoManager.getDecryptedFolderName(firstFolder, this) ?: firstFolder.name
    val message = if (folderPaths.size == 1) {
        getString(R.string.confirm_decrypt_album, firstAlbumName)
    } else {
        getString(R.string.confirm_decrypt_albums, folderPaths.size)
    }

    val toBeSelected = getString(R.string.decrypt_destination_to_be_selected)
    val destinationSummary = if (folderPaths.size == 1) {
        resolvedTargets[folderPaths.first()]?.let { humanizePath(it.absolutePath) } ?: toBeSelected
    } else if (resolvedTargets.values.all { it == null }) {
        toBeSelected
    } else {
        val shown = folderPaths.take(MAX_DECRYPT_PREVIEW_ALBUMS).joinToString("\n") { path ->
            val folder = File(path)
            val albumName = CryptoManager.getDecryptedFolderName(folder, this) ?: folder.name
            val dest = resolvedTargets[path]?.let { humanizePath(it.absolutePath) } ?: toBeSelected
            "• $albumName → $dest"
        }
        if (folderPaths.size > MAX_DECRYPT_PREVIEW_ALBUMS) {
            "$shown\n+${folderPaths.size - MAX_DECRYPT_PREVIEW_ALBUMS}"
        } else {
            shown
        }
    }
    return message to destinationSummary
}

private fun BaseSimpleActivity.pickDecryptDestinationAndExecute(
    folderPaths: List<String>,
    originTargets: Map<String, File?>,
    overrideAll: Boolean,
    callback: (destinationPath: String) -> Unit
) {
    val source = if (folderPaths.size == 1) folderPaths.first() else ""
    openPhysicalDecryptParentPicker(source) { destination ->
        val pickedDir = CryptoManager.resolvePickedPathToFile(this, destination) ?: File(destination)
        val resolved = folderPaths.associateWith { path ->
            val existingOrigin = if (overrideAll) null else originTargets[path]
            if (existingOrigin != null) {
                existingOrigin
            } else {
                val folder = File(path)
                val albumName = CryptoManager.getDecryptedFolderName(folder, this) ?: folder.name
                CryptoManager.resolveDecryptTargetInPickedDirectory(pickedDir, albumName)
            }
        }
        executeFolderDecryption(folderPaths, resolved, callback)
    }
}

private fun BaseSimpleActivity.executeFolderDecryption(
    folderPaths: List<String>,
    resolvedTargets: Map<String, File>,
    callback: (destinationPath: String) -> Unit
) {
    val conflicts = folderPaths.mapNotNull { path ->
        CryptoManager.previewDecryptAlbumConflict(this, path, resolvedTargets[path])
    }
    resolveAlbumCryptoOptions(this, conflicts) { albumOptions ->
        val isMerge = albumOptions.resolution == CryptoManager.AlbumConflictResolution.MERGE
        val isStandardMode = config.conflictHandlingMode == CONFLICT_HANDLING_STANDARD_DIALOG
        val initialProgressDialog = CryptoProgressDialog(
            activity = this,
            max = 0,
            titleId = R.string.decrypting_folder,
            enableNotification = false
        )
        resolveCryptoTransferConflicts(
            progressDialog = initialProgressDialog,
            buildPairs = {
                if (!isMerge) {
                    emptyList()
                } else {
                    val pairs = ArrayList<TransferPairSpec>()
                    for (folderPath in folderPaths) {
                        val mergeTarget = CryptoManager.findMergeTargetForDecryptFolder(
                            this,
                            folderPath,
                            resolvedTargets[folderPath]
                        )
                        if (mergeTarget != null) {
                            val (resolvedTargetDir, encFiles) = mergeTarget
                            for (encFile in encFiles) {
                                pairs.add(TransferPairSpec(encFile, resolvedTargetDir, isTargetEncrypted = false))
                            }
                        }
                    }
                    pairs
                }
            }
        ) { scan, activeProgressDialog ->
            val progressDialog = activeProgressDialog ?: CryptoProgressDialog(
                activity = this,
                max = 0,
                titleId = R.string.decrypting_folder,
                enableNotification = false
            )
            ensureNotificationPermissionThenRun { notificationsAllowed ->
                progressDialog.ensureNotificationStarted(notificationsAllowed)
                ensureBackgroundThread {
                    val acquired = ForegroundOperationRegistry.acquire(this, "album-decrypt")
                    if (!acquired) {
                        runOnUiThread {
                            try { progressDialog.dismiss() } catch (_: Exception) {}
                            toast(org.fossify.commons.R.string.unknown_error_occurred)
                        }
                        return@ensureBackgroundThread
                    }
                    try {
                    val totalFilesCount = folderPaths.sumOf { folderPath ->
                        File(folderPath).listFiles()?.count { it.isFile && CryptoManager.isEncryptedFile(it.name) } ?: 0
                    }
                    runOnUiThread {
                        progressDialog.updateProgress(0, totalFilesCount)
                    }
                    var globalProcessed = 0
                    var allSuccess = true
                    var lastDestination = ""
                    val deferredSkippedFiles = java.util.Collections.synchronizedList(ArrayList<File>())
                    val deferredIdenticalTargets = java.util.Collections.synchronizedMap(LinkedHashMap<String, File>())

                    var lastUiProgressMs = 0L
                    for (folderPath in folderPaths) {
                        if (ForegroundOperationRegistry.isCancelled) {
                            allSuccess = false
                            break
                        }
                        var folderLastCurrent = 0
                        val folderOptions = albumOptions.copy(
                            deleteIdenticalSource = false,
                            conflictResolutions = scan.resolutions,
                            skipDuplicateCheck = isStandardMode,
                            precheckedNonIdenticalPaths = scan.nonIdenticalPaths,
                            deferredSkippedSourceCollector = if (isMerge) deferredSkippedFiles else null,
                            deferredIdenticalTargetMap = if (isMerge) deferredIdenticalTargets else null
                        )
                        val result = try {
                            CryptoManager.decryptFolder(
                                context = this,
                                folderPath = folderPath,
                                targetFolderOverride = resolvedTargets[folderPath],
                                options = folderOptions
                            ) { current, _ ->
                                val diff = current - folderLastCurrent
                                if (diff > 0) {
                                    globalProcessed += diff
                                    folderLastCurrent = current
                                    val now = System.currentTimeMillis()
                                    if (globalProcessed >= totalFilesCount ||
                                        now - lastUiProgressMs >= DELETE_UI_PROGRESS_INTERVAL_MS
                                    ) {
                                        lastUiProgressMs = now
                                        runOnUiThread {
                                            progressDialog.updateProgress(globalProcessed, totalFilesCount)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (OperationFaults.isSimulatedAbort(e)) throw e
                            null
                        }
                        if (result == null) {
                            allSuccess = false
                        } else if (lastDestination.isEmpty()) {
                            lastDestination = result
                        }
                    }

                    CryptoManager.clearAppImageCaches(this)
                    val skippedSnapshot = deferredSkippedFiles.toList()
                    val identicalMapSnapshot = HashMap(deferredIdenticalTargets)
                    runOnUiThread {
                        try {
                            progressDialog.dismiss()
                        } catch (_: Exception) {}

                        if (allSuccess && totalFilesCount > skippedSnapshot.size) {
                            toast(R.string.folder_decrypted_successfully)
                        } else if (allSuccess && skippedSnapshot.isNotEmpty()) {
                            // Skipped files handled by post-operation prompt below
                        } else if (allSuccess) {
                            toast(R.string.folder_decrypted_successfully)
                        } else {
                            toast(org.fossify.commons.R.string.unknown_error_occurred)
                        }
                        showSkippedIdenticalToast(identicalMapSnapshot.size)
                        resolveSkippedCryptoFiles(
                            isEncrypt = false,
                            skippedFiles = skippedSnapshot,
                            identicalTargetMap = identicalMapSnapshot,
                            forceCleanupEmptyAlbum = true
                        ) {
                            callback(lastDestination)
                        }
                    }
                    } finally {
                        ForegroundOperationRegistry.release(this, "album-decrypt")
                    }
                }
            }
        }
    }
}
