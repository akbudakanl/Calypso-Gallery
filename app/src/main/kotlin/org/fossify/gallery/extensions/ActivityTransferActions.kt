package org.fossify.gallery.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.EncryptConfirmationDialog
import org.fossify.gallery.dialogs.PickDirectoryDialog
import org.fossify.gallery.dialogs.PickVirtualAlbumDialog
import org.fossify.gallery.dialogs.resolveSkippedCryptoFiles
import org.fossify.gallery.helpers.CopyMovePolicy
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.OperationNotificationCategory
import org.fossify.gallery.helpers.PickDirectoryMode
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.copyMoveFilesWithProgress
import org.fossify.gallery.helpers.copyOrMoveEncryptedFileToDirectory
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.helpers.isEncryptedFolder
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.isSupportedMediaName
import org.fossify.gallery.helpers.isVaultMediaPath
import org.fossify.gallery.helpers.isVaultRecycleBinPath
import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.BatchOperationInfo
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.JournalItem
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationFaults
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.operations.TransientOperationWaiter
import org.fossify.gallery.helpers.secureWipeAndDeleteFile
import org.fossify.gallery.helpers.showSkippedIdenticalToast
import java.io.File
import java.io.IOException

fun BaseSimpleActivity.tryAddFilesToVirtualAlbum(
    fileDirItems: ArrayList<FileDirItem>,
    currentDirectoryPath: String = "",
    callback: (destinationPath: String) -> Unit
) {
    if (fileDirItems.isEmpty()) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    val hasEncrypted = fileDirItems.any { CryptoManager.isVaultMediaPath(this, it.path) }
    val hasUnencrypted = fileDirItems.any { !CryptoManager.isVaultMediaPath(this, it.path) }
    if (hasEncrypted && hasUnencrypted) {
        toast(R.string.cannot_mix_encrypted_and_unencrypted)
        return
    }

    val validItems = fileDirItems.filterTo(ArrayList()) {
        !it.path.startsWith(recycleBinPath) && !CryptoManager.isVaultRecycleBinPath(this, it.path)
    }
    if (validItems.isEmpty()) {
        return
    }

    val source = currentDirectoryPath.ifEmpty { validItems[0].getParentPath() }
    val isHiddenSource = CryptoManager.isEncryptedFolderPath(this, source) ||
        (source.isVirtualAlbumPath() && isHiddenVirtualAlbumPath(source)) ||
        hasEncrypted
    val sourceAlbumId = currentDirectoryPath.getVirtualAlbumId()
    val sourceHint = if (currentDirectoryPath.isNotEmpty() &&
        currentDirectoryPath != FAVORITES &&
        currentDirectoryPath != RECYCLE_BIN
    ) {
        currentDirectoryPath
    } else {
        validItems[0].getParentPath()
    }

    PickVirtualAlbumDialog(
        activity = this,
        sourceAlbumId = sourceAlbumId,
        isHiddenSource = isHiddenSource,
        sourcePath = sourceHint
    ) { albumId, albumName ->
        val destination = getVirtualAlbumPath(albumId)
        ensureNotificationPermissionThenRun {
            VirtualAlbumConflictHelper.add(this, albumId, validItems, isHiddenSource) { added ->
                val resolvedName = albumName.ifEmpty { getVirtualAlbumName(destination) ?: "" }
                val msgRes = if (added > 0) R.string.added_to_virtual_album else R.string.already_in_virtual_album
                toast(getString(msgRes, resolvedName))
                callback(destination)
            }
        }
    }
}

fun BaseSimpleActivity.tryCopyMoveFilesTo(
    fileDirItems: ArrayList<FileDirItem>,
    isCopyOperation: Boolean,
    currentDirectoryPath: String = "",
    callback: (destinationPath: String) -> Unit
) {
    if (fileDirItems.isEmpty()) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
        return
    }

    if (!isCopyOperation && CopyMovePolicy.isVirtualOrFavoritesPath(currentDirectoryPath)) {
        toast(R.string.virtual_album_cannot_be_modified)
        return
    }

    val source = if (currentDirectoryPath.isVirtualAlbumPath()) {
        currentDirectoryPath
    } else if (currentDirectoryPath.isNotEmpty()) {
        currentDirectoryPath
    } else {
        fileDirItems[0].getParentPath()
    }
    // Only vault content is protected. A file from a hidden folder is explicitly referenced by the
    // user and stays visible in the target.
    val isHiddenCopySource = CryptoManager.isVaultMediaPath(this, source) ||
        fileDirItems.any { CryptoManager.isVaultMediaPath(this, it.path) }
    val pickerMode = CopyMovePolicy.resolveCopyMoveMode(isCopyOperation, isHiddenCopySource)

    PickDirectoryDialog(
        activity = this,
        sourcePath = source,
        showOtherFolderButton = true,
        showFavoritesBin = false,
        isPickingCopyMoveDestination = true,
        isPickingFolderForWidget = false,
        isCopyOperation = isCopyOperation,
        isHiddenCopySource = isHiddenCopySource,
        mode = pickerMode
    ) {
        val destination = it
        if (destination.isVirtualAlbumPath()) {
            toast(R.string.virtual_album_cannot_be_modified)
            return@PickDirectoryDialog
        }

        val isDestEncrypted = CryptoManager.isEncryptedFolderPath(this, destination)
        val hasEncryptedSources = fileDirItems.any { item -> CryptoManager.isEncryptedFile(item.path) }

        val isSourceHiddenPhysical = !source.isVirtualAlbumPath() && (CryptoManager.isEncryptedFolderPath(this, source) || isPathHiddenOrEncrypted(source) || hasEncryptedSources)
        val isSourceHiddenVirtual = source.isVirtualAlbumPath() && (isHiddenVirtualAlbumPath(source) || isPathHiddenOrEncrypted(source) || hasEncryptedSources)
        val isDestNonHiddenPhysical = !destination.isVirtualAlbumPath() && !isDestEncrypted && !destination.trimEnd('/').substringAfterLast('/').startsWith(".")

        val needsDecryptionConfirmation = (isSourceHiddenPhysical || isSourceHiddenVirtual) && isDestNonHiddenPhysical
        // Files that are copied into the encrypted vault get encrypted, so they need a confirmation
        val needsEncryptionConfirmation = isDestEncrypted &&
            fileDirItems.any { !CryptoManager.isEncryptedFile(it.path) }

        val proceedWithCopyMove = {
            if (isDestEncrypted || hasEncryptedSources) {
                val cryptoItems = fileDirItems.filter {
                    // only formats supported by the gallery may ever be encrypted
                    !isDestEncrypted || CryptoManager.isEncryptedFile(it.name) || CryptoManager.isSupportedMediaName(it.name)
                }
                val hasUnencryptedToEncrypt = isDestEncrypted && cryptoItems.any { !CryptoManager.isEncryptedFile(it.name) }
                val progressTitleId = when {
                    hasUnencryptedToEncrypt -> R.string.encrypting_files
                    isDestEncrypted -> if (isCopyOperation) org.fossify.commons.R.string.copying else org.fossify.commons.R.string.moving
                    else -> R.string.decrypting_files
                }
                val operationOwner = when {
                    isDestEncrypted && hasUnencryptedToEncrypt -> "crypto-encrypt"
                    !isDestEncrypted && hasEncryptedSources -> "crypto-decrypt"
                    else -> "copy-move"
                }
                val dialogChannel = if (operationOwner == "copy-move") {
                    OperationNotificationCategory.CHANNEL_COPY_MOVE
                } else {
                    OperationNotificationCategory.CHANNEL_CRYPTO
                }

                val destDir = File(destination)
                val initialProgressDialog = CryptoProgressDialog(
                    activity = this,
                    max = cryptoItems.size,
                    titleId = progressTitleId,
                    notificationChannelId = dialogChannel,
                    enableNotification = false
                )
                val runCryptoWithResolutions = {
                    conflictResolutions: LinkedHashMap<String, Int>,
                    precheckedPlainIndex: CryptoManager.TargetPlainIndex?,
                    precheckedEncIndex: CryptoManager.TargetEncryptedIndex?,
                    precheckedIdenticalTargets: Map<String, File>,
                    precheckedIdenticalPaths: Set<String>,
                    precheckedNonIdenticalPaths: Set<String>,
                    existingProgressDialog: CryptoProgressDialog? ->
                    val progressDialog = existingProgressDialog ?: CryptoProgressDialog(
                        activity = this,
                        max = cryptoItems.size,
                        titleId = progressTitleId,
                        notificationChannelId = dialogChannel,
                        enableNotification = false
                    )
                    ensureNotificationPermissionThenRun { notificationsAllowed ->
                        progressDialog.ensureNotificationStarted(notificationsAllowed)
                        ensureBackgroundThread {
                            val acquired = ForegroundOperationRegistry.acquire(this, operationOwner)
                            if (!acquired) {
                                runOnUiThread {
                                    try { progressDialog.dismiss() } catch (_: Exception) {}
                                    toast(org.fossify.commons.R.string.copy_move_failed)
                                    VaultSessionManager.lockVaultIfOutsideSession(this)
                                }
                                return@ensureBackgroundThread
                            }
                            try {
                            if (!destDir.exists()) {
                                destDir.mkdirs()
                            }
                            val plainIndex = if (!isDestEncrypted) {
                                precheckedPlainIndex ?: CryptoManager.TargetPlainIndex(destDir)
                            } else {
                                null
                            }
                            val encIndex = if (isDestEncrypted) {
                                precheckedEncIndex ?: CryptoManager.TargetEncryptedIndex(destDir)
                            } else {
                                null
                            }
                            val sourceDirs = LinkedHashSet<File>()
                            val pathsToRescan = ArrayList<String>()
                            val skippedEncryptFiles = ArrayList<File>()
                            val skippedDecryptFiles = ArrayList<File>()
                            val identicalEncryptTargetMap = HashMap<String, File>()
                            val identicalDecryptTargetMap = HashMap<String, File>()
                            var anyPlaintextWiped = false
                            var anyFavoriteUpdated = false
                            var lastUiProgressMs = 0L

                            // ADR 0003: one batch journal for the whole crypto copy/move, with per-item kind.
                            fun itemKindFor(path: String): String {
                                val srcEnc = CryptoManager.isEncryptedFile(path)
                                return when {
                                    isDestEncrypted && !srcEnc -> OperationJournal.KIND_ENCRYPT
                                    !isDestEncrypted && srcEnc -> OperationJournal.KIND_DECRYPT
                                    isCopyOperation -> OperationJournal.KIND_COPY
                                    else -> OperationJournal.KIND_MOVE
                                }
                            }
                            val batchKind = when {
                                isDestEncrypted && hasUnencryptedToEncrypt -> OperationJournal.KIND_ENCRYPT
                                !isDestEncrypted && cryptoItems.any { CryptoManager.isEncryptedFile(it.path) } -> OperationJournal.KIND_DECRYPT
                                isCopyOperation -> OperationJournal.KIND_COPY
                                else -> OperationJournal.KIND_MOVE
                            }
                            val batchOperation = OperationContext(
                                operationId = OperationJournal.newOperationId(),
                                kind = batchKind,
                                batch = BatchOperationInfo(
                                    sourceRoot = currentDirectoryPath,
                                    destRoot = destination,
                                    isMove = !isCopyOperation,
                                    wipeSource = isDestEncrypted && !isCopyOperation,
                                    deleteSource = !isCopyOperation
                                )
                            )
                            OperationJournal.beginBatch(
                                batchOperation,
                                cryptoItems.map {
                                    val absPath = File(it.path).absolutePath
                                    JournalItem(
                                        sourcePath = it.path,
                                        destPath = File(destDir, it.name).absolutePath,
                                        sourceSize = it.size,
                                        resolvedDestName = it.name,
                                        kind = itemKindFor(it.path),
                                        conflictResolution = (conflictResolutions[absPath] ?: CONFLICT_KEEP_BOTH).toString()
                                    )
                                }
                            )

                            for ((itemIndex, item) in cryptoItems.withIndex()) {
                                if (ForegroundOperationRegistry.isCancelled) break
                                val srcFile = File(item.path)
                                val now = System.currentTimeMillis()
                                if (itemIndex == 0 || now - lastUiProgressMs >= 80L) {
                                    lastUiProgressMs = now
                                    runOnUiThread { progressDialog.updateProgress(itemIndex, cryptoItems.size) }
                                }
                                val itemKind = itemKindFor(item.path)
                                val itemOperation = OperationContext(
                                    operationId = batchOperation.operationId,
                                    itemIndex = itemIndex,
                                    kind = itemKind,
                                    batch = batchOperation.batch
                                )
                                if (!srcFile.exists()) {
                                    OperationJournal.markFailed(itemOperation, "source missing")
                                    continue
                                }
                                srcFile.parentFile?.let { sourceDirs.add(it) }
                                val resolution = conflictResolutions[srcFile.absolutePath] ?: CONFLICT_KEEP_BOTH

                                val identicalTarget = findIdenticalTransferTarget(
                                    sourceFile = srcFile,
                                    targetDir = destDir,
                                    isTargetEncrypted = isDestEncrypted,
                                    plainIndex = plainIndex,
                                    encryptedIndex = encIndex,
                                    precheckedIdenticalTargets = precheckedIdenticalTargets,
                                    precheckedIdenticalPaths = precheckedIdenticalPaths,
                                    precheckedNonIdenticalPaths = precheckedNonIdenticalPaths,
                                    allowFastSampling = true
                                )
                                if (identicalTarget != null) {
                                    // Settled duplicate: source stays intact, no I/O was attempted.
                                    OperationJournal.markSkipped(itemOperation)
                                    if (!isCopyOperation) {
                                        if (isDestEncrypted && !CryptoManager.isEncryptedFile(srcFile.name)) {
                                            skippedEncryptFiles.add(srcFile)
                                            identicalEncryptTargetMap[srcFile.absolutePath] = identicalTarget
                                        } else if (!isDestEncrypted && CryptoManager.isEncryptedFile(srcFile.name)) {
                                            skippedDecryptFiles.add(srcFile)
                                            identicalDecryptTargetMap[srcFile.absolutePath] = identicalTarget
                                        }
                                    }
                                    continue
                                }

                                if (isDestEncrypted && !CryptoManager.isEncryptedFile(srcFile.name)) {
                                    if (resolution == CONFLICT_SKIP) {
                                        OperationJournal.markSkipped(itemOperation)
                                        if (!isCopyOperation) {
                                            skippedEncryptFiles.add(srcFile)
                                        }
                                    } else {
                                        val encFile = CryptoManager.encryptFileToDirectory(
                                            context = this,
                                            sourceFile = srcFile,
                                            targetDir = destDir,
                                            wipeSource = !isCopyOperation,
                                            clearCachesNow = false,
                                            conflictResolution = resolution,
                                            targetIndex = encIndex,
                                            skipDuplicateCheck = true,
                                            deleteSourceOnIdentical = false,
                                            operation = itemOperation
                                        )
                                        if (!isCopyOperation && encFile != null) {
                                            anyPlaintextWiped = true
                                        }
                                    }
                                } else if (!isDestEncrypted && CryptoManager.isEncryptedFile(srcFile.name)) {
                                    if (resolution == CONFLICT_SKIP) {
                                        OperationJournal.markSkipped(itemOperation)
                                        if (!isCopyOperation) {
                                            skippedDecryptFiles.add(srcFile)
                                        }
                                    } else {
                                        val decFile = CryptoManager.decryptFileToDirectory(
                                            context = this,
                                            encryptedFile = srcFile,
                                            targetDir = destDir,
                                            deleteEncryptedSource = !isCopyOperation,
                                            conflictResolution = resolution,
                                            targetIndex = plainIndex,
                                            skipDuplicateCheck = true,
                                            deleteSourceOnIdentical = false,
                                            operation = itemOperation
                                        )
                                        if (!isCopyOperation && decFile != null && decFile.exists()) {
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
                                    }
                                } else if (isDestEncrypted && CryptoManager.isEncryptedFile(srcFile.name)) {
                                    val movedOrCopiedBin = CryptoManager.copyOrMoveEncryptedFileToDirectory(
                                        context = this,
                                        sourceBin = srcFile,
                                        targetDir = destDir,
                                        isMove = !isCopyOperation,
                                        conflictResolution = resolution,
                                        targetIndex = encIndex,
                                        skipDuplicateCheck = true,
                                        operation = itemOperation
                                    )
                                    if (!isCopyOperation && movedOrCopiedBin != null && movedOrCopiedBin.exists()) {
                                        runCatching {
                                            favoritesDB.updateFavorite(
                                                movedOrCopiedBin.name,
                                                movedOrCopiedBin.absolutePath,
                                                movedOrCopiedBin.parentFile?.absolutePath ?: destination,
                                                srcFile.absolutePath
                                            )
                                            anyFavoriteUpdated = true
                                        }
                                    }
                                } else {
                                    // Plaintext-to-plaintext within a mixed batch
                                    val oldSrcPath = srcFile.absolutePath
                                    val activePlainIndex = plainIndex!!
                                    val exactTarget = activePlainIndex.findExact(srcFile.name) ?: File(destDir, srcFile.name)
                                    val targetFile = if (exactTarget.exists() && oldSrcPath != exactTarget.absolutePath) {
                                        when (resolution) {
                                            CONFLICT_SKIP -> {
                                                OperationJournal.markSkipped(itemOperation)
                                                null
                                            }
                                            CONFLICT_OVERWRITE -> {
                                                exactTarget.delete()
                                                activePlainIndex.removeFile(exactTarget)
                                                exactTarget
                                            }
                                            else -> activePlainIndex.getAlternativeFile(srcFile.name)
                                        }
                                    } else {
                                        exactTarget
                                    }
                                    if (targetFile != null && oldSrcPath == targetFile.absolutePath) {
                                        OperationJournal.markSkipped(itemOperation)
                                    } else if (targetFile != null) {
                                        if (!TransientOperationWaiter.awaitDestinationReady(this, itemOperation, destDir, srcFile.length())) {
                                            continue
                                        }
                                        val plainStaging = AtomicStaging.fileFor(targetFile, itemOperation)
                                        val sourceFingerprint = MediaIdentity.computeFingerprint(srcFile)
                                        val sourceLength = srcFile.length()
                                        OperationJournal.markInFlight(
                                            itemOperation,
                                            plainStaging.absolutePath,
                                            resolvedDestName = targetFile.name,
                                            actualDestPath = targetFile.absolutePath,
                                            sourceFingerprint = sourceFingerprint
                                        )
                                        try {
                                            srcFile.copyTo(plainStaging, overwrite = true, bufferSize = 256 * 1024)
                                            AtomicStaging.commit(plainStaging, targetFile)
                                            val destFingerprint = MediaIdentity.computeFingerprint(targetFile)
                                            if (sourceFingerprint.isEmpty() || destFingerprint.isEmpty() ||
                                                targetFile.length() != sourceLength || destFingerprint != sourceFingerprint
                                            ) {
                                                // Read-back verification failed: keep the source, remove the
                                                // unverified plaintext destination with a secure wipe.
                                                CryptoManager.secureWipeAndDeleteFile(this, targetFile)
                                                throw IOException("plain copy verification failed")
                                            }
                                            OperationJournal.markVerified(
                                                itemOperation,
                                                destFingerprint,
                                                targetFile.absolutePath
                                            )
                                            targetFile.setLastModified(srcFile.lastModified())
                                            activePlainIndex.recordFile(targetFile)
                                            if (!isCopyOperation) {
                                                runCatching { updateDBMediaPath(oldSrcPath, targetFile.absolutePath) }
                                                CryptoManager.secureWipeAndDeleteFile(this, srcFile)
                                                OperationJournal.markSourceDeleted(itemOperation)
                                                anyPlaintextWiped = true
                                            }
                                            pathsToRescan.add(targetFile.absolutePath)
                                        } catch (e: Exception) {
                                            AtomicStaging.discard(plainStaging, containsPlaintext = true, context = this)
                                            OperationJournal.markFailed(itemOperation, e.message ?: "plain copy failed")
                                        }
                                    }
                                }
                                if (OperationFaults.abortAfterItem(batchOperation, itemIndex)) {
                                    throw IOException("Fault injection: simulated process death after item $itemIndex")
                                }
                            }

                            batchOperation.operationId?.let { OperationJournal.clearAfterCompletionOrCancel(it) }
                            if (anyFavoriteUpdated) {
                                runCatching { ReferenceBackupManager.saveFavorites(this) }
                            }
                            if (pathsToRescan.isNotEmpty()) {
                                runCatching { rescanPaths(pathsToRescan) }
                            }
                            if (!isCopyOperation) {
                                sourceDirs.forEach { dir ->
                                    if (CryptoManager.isEncryptedFolder(dir)) {
                                        CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(this, dir)
                                    } else {
                                        cleanupEmptyPlaintextAlbumIfNeeded(dir, forceIfEmpty = false)
                                    }
                                }
                            }
                            if (anyPlaintextWiped) {
                                CryptoManager.clearAppImageCaches(this)
                            }
                            MediaIndexCoordinator.markFoldersDirty(
                                sourceDirs.map { it.absolutePath } + destination,
                                includeFavorites = anyFavoriteUpdated
                            )
                            runOnUiThread {
                                progressDialog.updateProgress(cryptoItems.size, cryptoItems.size)
                                progressDialog.dismiss()
                                showSkippedIdenticalToast(
                                    identicalEncryptTargetMap.size + identicalDecryptTargetMap.size
                                )
                                resolveSkippedCryptoFiles(
                                    isEncrypt = true,
                                    skippedFiles = skippedEncryptFiles,
                                    identicalTargetMap = identicalEncryptTargetMap
                                ) {
                                    resolveSkippedCryptoFiles(
                                        isEncrypt = false,
                                        skippedFiles = skippedDecryptFiles,
                                        identicalTargetMap = identicalDecryptTargetMap
                                    ) {
                                        VaultSessionManager.lockVaultIfOutsideSession(this)
                                        callback(destination)
                                    }
                                }
                            }
                            } finally {
                                ForegroundOperationRegistry.release(this, operationOwner)
                            }
                        }
                    }
                }

                resolveCryptoTransferConflicts(
                    allowFastLargeCheck = true,
                    progressDialog = initialProgressDialog,
                    buildPairs = {
                        cryptoItems.mapNotNull { item ->
                            val srcFile = File(item.path)
                            if (srcFile.exists() && srcFile.isFile) {
                                TransferPairSpec(srcFile, destDir, isTargetEncrypted = isDestEncrypted)
                            } else {
                                null
                            }
                        }
                    }
                ) { scan, activeProgressDialog ->
                    runCryptoWithResolutions(
                        scan.resolutions,
                        scan.plainIndexes[destDir.absolutePath],
                        scan.encIndexes[destDir.absolutePath],
                        scan.identicalTargets,
                        scan.identicalPaths,
                        scan.nonIdenticalPaths,
                        activeProgressDialog
                    )
                }
            } else {
                val physicalSource = if (source.isVirtualAlbumPath()) {
                    CopyMovePolicy.resolveVirtualAlbumCopyPhysicalSource(
                        itemParentPaths = fileDirItems.map { it.getParentPath() },
                        destinationPath = destination,
                        fallbackSource = source
                    )
                } else {
                    source
                }
                handleSAFDialog(physicalSource) {
                    if (it) {
                        copyMoveFilesWithProgress(fileDirItems, physicalSource.trimEnd('/'), destination, isCopyOperation, true, config.shouldShowHidden, callback)
                    }
                }
            }
        }

        if (needsDecryptionConfirmation) {
            EncryptConfirmationDialog(
                activity = this,
                message = getString(R.string.confirm_decrypt_hidden_file),
                infoTitleResId = R.string.decrypt_info_title,
                infoMessageResId = R.string.decrypt_info_message,
                isDecrypt = true
            ) {
                proceedWithCopyMove()
            }
        } else if (needsEncryptionConfirmation) {
            EncryptConfirmationDialog(
                activity = this,
                message = getString(R.string.confirm_encrypt_files_to_vault),
                infoMessageResId = R.string.encrypt_files_info_message
            ) {
                proceedWithCopyMove()
            }
        } else {
            proceedWithCopyMove()
        }
    }
}
