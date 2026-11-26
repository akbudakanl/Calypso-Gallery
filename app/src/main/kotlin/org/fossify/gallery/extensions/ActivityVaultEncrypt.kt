package org.fossify.gallery.extensions

import android.os.Environment
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.renameFile
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.EncryptConfirmationDialog
import org.fossify.gallery.dialogs.PickVaultAlbumDialog
import org.fossify.gallery.dialogs.VaultSecurityDialog
import org.fossify.gallery.dialogs.resolveSkippedCryptoFiles
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.helpers.getDecryptedFolderName
import org.fossify.gallery.helpers.getVaultProtectionType
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.operations.BatchOperationInfo
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.JournalItem
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationFaults
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.showSkippedIdenticalToast
import java.io.File

fun BaseSimpleActivity.toggleFileVisibility(oldPath: String, hide: Boolean, callback: ((newPath: String) -> Unit)? = null) {
    val srcFile = File(oldPath)
    if (!srcFile.exists()) {
        callback?.invoke(oldPath)
        return
    }

    if (hide) {
        if (CryptoManager.isEncryptedFile(oldPath)) {
            callback?.invoke(oldPath)
            return
        }

        // The file is encrypted, so the confirmation has to be shown before the vault password is
        // requested.
        EncryptConfirmationDialog(
            activity = this,
            message = getString(R.string.confirm_encrypt_file),
            infoMessageResId = R.string.encrypt_files_info_message,
            onCancel = {
                callback?.invoke(oldPath)
            }
        ) {
            encryptFileToVault(oldPath, callback)
        }
    } else {
        if (CryptoManager.isEncryptedFile(oldPath)) {
            if (!CryptoManager.isVaultUnlocked) {
                val initialType = CryptoManager.getVaultProtectionType(this)
                    ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) {
                        PROTECTION_PASSWORD
                    } else {
                        PROTECTION_PIN
                    }
                VaultSecurityDialog(
                    activity = this,
                    requiredHash = config.hiddenPasswordHash,
                    showTabIndex = initialType,
                    minimalist = true
                ) { _, type, success ->
                    if (success) {
                        config.hiddenProtectionType = type
                        toggleFileVisibility(oldPath, false, callback)
                    } else {
                        callback?.invoke(oldPath)
                    }
                }
                return
            }

            val targetDir = if (CryptoManager.isEncryptedFolderPath(this, srcFile.parent ?: "")) {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val originalFolderName = CryptoManager.getDecryptedFolderName(srcFile.parentFile ?: File("")) ?: "Restored"
                File(picturesDir, originalFolderName).apply { if (!exists()) mkdirs() }
            } else {
                srcFile.parentFile ?: File(oldPath.getParentPath())
            }

            val initialProgressDialog = CryptoProgressDialog(
                activity = this,
                max = 1,
                titleId = R.string.decrypting_files,
                enableNotification = false
            )
            val runSingleDecryptWithResolution = {
                conflictResolution: Int,
                precheckedIndex: CryptoManager.TargetPlainIndex?,
                precheckedIdenticalTargets: Map<String, File>,
                precheckedIdenticalPaths: Set<String>,
                precheckedNonIdenticalPaths: Set<String>,
                existingProgressDialog: CryptoProgressDialog? ->
                val progressDialog = existingProgressDialog ?: CryptoProgressDialog(
                    activity = this,
                    max = 1,
                    titleId = R.string.decrypting_files,
                    enableNotification = false
                )
                ensureNotificationPermissionThenRun { notificationsAllowed ->
                    progressDialog.ensureNotificationStarted(notificationsAllowed)
                    ensureBackgroundThread {
                        val acquired = ForegroundOperationRegistry.acquire(this, "crypto-decrypt")
                        if (!acquired) {
                            runOnUiThread {
                                progressDialog.dismiss()
                                toast(org.fossify.commons.R.string.unknown_error_occurred)
                                callback?.invoke(oldPath)
                            }
                            return@ensureBackgroundThread
                        }
                        try {
                            val identicalTarget = if (conflictResolution == CONFLICT_SKIP) {
                                null
                            } else {
                                findIdenticalTransferTarget(
                                    sourceFile = srcFile,
                                    targetDir = targetDir,
                                    isTargetEncrypted = false,
                                    plainIndex = precheckedIndex,
                                    precheckedIdenticalTargets = precheckedIdenticalTargets,
                                    precheckedIdenticalPaths = precheckedIdenticalPaths,
                                    precheckedNonIdenticalPaths = precheckedNonIdenticalPaths
                                )
                            }
                            val isSkipped = conflictResolution == CONFLICT_SKIP || identicalTarget != null
                            val itemOp = if (isSkipped) {
                                OperationContext.NONE
                            } else {
                                val batchOp = OperationContext(
                                    operationId = OperationJournal.newOperationId(),
                                    kind = OperationJournal.KIND_DECRYPT,
                                    batch = BatchOperationInfo(
                                        sourceRoot = srcFile.parent ?: "",
                                        destRoot = targetDir.absolutePath,
                                        isMove = true,
                                        wipeSource = false,
                                        deleteSource = true
                                    )
                                )
                                OperationJournal.beginBatch(
                                    batchOp,
                                    listOf(
                                        JournalItem(
                                            sourcePath = srcFile.absolutePath,
                                            destPath = File(targetDir, srcFile.name).absolutePath,
                                            sourceSize = srcFile.length(),
                                            resolvedDestName = srcFile.name,
                                            kind = OperationJournal.KIND_DECRYPT,
                                            conflictResolution = conflictResolution.toString()
                                        )
                                    )
                                )
                                OperationContext(batchOp.operationId, 0, OperationJournal.KIND_DECRYPT, batchOp.batch)
                            }
                            val decryptedFile = if (isSkipped) {
                                null
                            } else {
                                try {
                                    CryptoManager.decryptFileToDirectory(
                                        context = this,
                                        encryptedFile = srcFile,
                                        targetDir = targetDir,
                                        deleteEncryptedSource = true,
                                        conflictResolution = conflictResolution,
                                        targetIndex = precheckedIndex,
                                        skipDuplicateCheck = true,
                                        deleteSourceOnIdentical = false,
                                        operation = itemOp
                                    )
                                } catch (e: Exception) {
                                    if (OperationFaults.isSimulatedAbort(e)) throw e
                                    null
                                } finally {
                                    itemOp.operationId?.let { OperationJournal.clearAfterCompletionOrCancel(it) }
                                }
                            }
                            val resultPath = decryptedFile?.absolutePath ?: oldPath
                            CryptoManager.clearAppImageCaches(this)
                            MediaIndexCoordinator.markFoldersDirty(listOfNotNull(srcFile.parent, targetDir.absolutePath))
                            runOnUiThread {
                                progressDialog.updateProgress(if (isSkipped || decryptedFile != null) 1 else 0, 1)
                                progressDialog.dismiss()
                                showSkippedIdenticalToast(if (identicalTarget != null) 1 else 0)
                                if (isSkipped) {
                                    val identicalMap = if (identicalTarget != null) {
                                        mapOf(srcFile.absolutePath to identicalTarget)
                                    } else {
                                        emptyMap()
                                    }
                                    resolveSkippedCryptoFiles(
                                        isEncrypt = false,
                                        skippedFiles = listOf(srcFile),
                                        identicalTargetMap = identicalMap
                                    ) { deletedCount ->
                                        callback?.invoke(if (deletedCount > 0) "" else oldPath)
                                    }
                                } else {
                                    if (decryptedFile != null && decryptedFile.exists()) {
                                        toast(R.string.file_decrypted_successfully)
                                    } else {
                                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    }
                                    callback?.invoke(resultPath)
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
                buildPairs = { listOf(TransferPairSpec(srcFile, targetDir, isTargetEncrypted = false)) }
            ) { scan, activeProgressDialog ->
                val resolution = scan.resolutions[srcFile.absolutePath] ?: CONFLICT_KEEP_BOTH
                runSingleDecryptWithResolution(
                    resolution,
                    scan.plainIndexes[targetDir.absolutePath],
                    scan.identicalTargets,
                    scan.identicalPaths,
                    scan.nonIdenticalPaths,
                    activeProgressDialog
                )
            }
        } else if (srcFile.name.startsWith('.')) {
            val path = oldPath.getParentPath()
            val newFilename = srcFile.name.substring(1)
            val newPath = "$path/$newFilename"
            renameFile(oldPath, newPath, false) { _, _ ->
                runOnUiThread {
                    callback?.invoke(newPath)
                }
                ensureBackgroundThread {
                    updateDBMediaPath(oldPath, newPath)
                }
            }
        } else {
            callback?.invoke(oldPath)
        }
    }
}

/**
 * Encrypts a single file and moves it into the encrypted vault. The vault has to be created and
 * unlocked before the file can be encrypted.
 */
private fun BaseSimpleActivity.encryptFileToVault(oldPath: String, callback: ((newPath: String) -> Unit)? = null) {
    val srcFile = File(oldPath)

    if (!hasVaultCreated()) {
        promptCreateVault(
            onCreated = {
                encryptFileToVault(oldPath, callback)
            },
            onCancelled = {
                callback?.invoke(oldPath)
            }
        )
        return
    }

    if (!CryptoManager.isVaultUnlocked) {
        val initialType = CryptoManager.getVaultProtectionType(this)
            ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) {
                PROTECTION_PASSWORD
            } else {
                PROTECTION_PIN
            }
        VaultSecurityDialog(
            activity = this,
            requiredHash = config.hiddenPasswordHash,
            showTabIndex = initialType,
            minimalist = true
        ) { _, type, success ->
            if (success) {
                config.hiddenProtectionType = type
                encryptFileToVault(oldPath, callback)
            } else {
                callback?.invoke(oldPath)
            }
        }
        return
    }

    PickVaultAlbumDialog(
        activity = this,
        onCancel = {
            VaultSessionManager.lockVaultIfOutsideSession(this)
            callback?.invoke(oldPath)
        }
    ) { targetDir, albumName ->
        val initialProgressDialog = CryptoProgressDialog(
            activity = this,
            max = 1,
            titleId = R.string.encrypting_files,
            enableNotification = false
        )
        val runSingleEncryptWithResolution = {
            conflictResolution: Int,
            precheckedEncIndex: CryptoManager.TargetEncryptedIndex?,
            precheckedIdenticalTargets: Map<String, File>,
            precheckedIdenticalPaths: Set<String>,
            precheckedNonIdenticalPaths: Set<String>,
            existingProgressDialog: CryptoProgressDialog? ->
            val progressDialog = existingProgressDialog ?: CryptoProgressDialog(
                activity = this,
                max = 1,
                titleId = R.string.encrypting_files,
                enableNotification = false
            )
            ensureNotificationPermissionThenRun { notificationsAllowed ->
                progressDialog.ensureNotificationStarted(notificationsAllowed)
                ensureBackgroundThread {
                    val acquired = ForegroundOperationRegistry.acquire(this, "crypto-encrypt")
                    if (!acquired) {
                        runOnUiThread {
                            progressDialog.dismiss()
                            toast(org.fossify.commons.R.string.unknown_error_occurred)
                            VaultSessionManager.lockVaultIfOutsideSession(this)
                            callback?.invoke(oldPath)
                        }
                        return@ensureBackgroundThread
                    }
                    try {
                        val identicalTarget = if (conflictResolution == CONFLICT_SKIP) {
                            null
                        } else {
                            findIdenticalTransferTarget(
                                sourceFile = srcFile,
                                targetDir = targetDir,
                                isTargetEncrypted = true,
                                encryptedIndex = precheckedEncIndex,
                                precheckedIdenticalTargets = precheckedIdenticalTargets,
                                precheckedIdenticalPaths = precheckedIdenticalPaths,
                                precheckedNonIdenticalPaths = precheckedNonIdenticalPaths
                            )
                        }
                        val isSkipped = conflictResolution == CONFLICT_SKIP || identicalTarget != null
                        val itemOp = if (isSkipped) {
                            OperationContext.NONE
                        } else {
                            val batchOp = OperationContext(
                                operationId = OperationJournal.newOperationId(),
                                kind = OperationJournal.KIND_ENCRYPT,
                                batch = BatchOperationInfo(
                                    sourceRoot = srcFile.parent ?: "",
                                    destRoot = targetDir.absolutePath,
                                    isMove = true,
                                    wipeSource = true,
                                    deleteSource = true
                                )
                            )
                            OperationJournal.beginBatch(
                                batchOp,
                                listOf(
                                    JournalItem(
                                        sourcePath = srcFile.absolutePath,
                                        destPath = File(targetDir, srcFile.name).absolutePath,
                                        sourceSize = srcFile.length(),
                                        resolvedDestName = srcFile.name,
                                        kind = OperationJournal.KIND_ENCRYPT,
                                        conflictResolution = conflictResolution.toString()
                                    )
                                )
                            )
                            OperationContext(batchOp.operationId, 0, OperationJournal.KIND_ENCRYPT, batchOp.batch)
                        }
                        val encryptedFile = if (isSkipped) {
                            null
                        } else {
                            try {
                                CryptoManager.encryptFileToDirectory(
                                    context = this,
                                    sourceFile = srcFile,
                                    targetDir = targetDir,
                                    wipeSource = true,
                                    clearCachesNow = true,
                                    conflictResolution = conflictResolution,
                                    targetIndex = precheckedEncIndex,
                                    skipDuplicateCheck = true,
                                    deleteSourceOnIdentical = false,
                                    operation = itemOp
                                )
                            } catch (e: Exception) {
                                if (OperationFaults.isSimulatedAbort(e)) throw e
                                null
                            } finally {
                                itemOp.operationId?.let { OperationJournal.clearAfterCompletionOrCancel(it) }
                            }
                        }
                        val resultPath = encryptedFile?.absolutePath ?: oldPath
                        MediaIndexCoordinator.markFoldersDirty(listOfNotNull(srcFile.parent, targetDir.absolutePath))
                        runOnUiThread {
                            progressDialog.updateProgress(if (isSkipped || (encryptedFile != null && encryptedFile.exists())) 1 else 0, 1)
                            progressDialog.dismiss()
                            showSkippedIdenticalToast(if (identicalTarget != null) 1 else 0)
                            if (isSkipped) {
                                val identicalMap = if (identicalTarget != null) {
                                    mapOf(srcFile.absolutePath to identicalTarget)
                                } else {
                                    emptyMap()
                                }
                                resolveSkippedCryptoFiles(
                                    isEncrypt = true,
                                    skippedFiles = listOf(srcFile),
                                    identicalTargetMap = identicalMap
                                ) { deletedCount ->
                                    VaultSessionManager.lockVaultIfOutsideSession(this)
                                    callback?.invoke(if (deletedCount > 0) "" else oldPath)
                                }
                            } else {
                                if (encryptedFile != null && encryptedFile.exists()) {
                                    toast(getString(R.string.file_hidden_to_album, albumName))
                                } else {
                                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                                }
                                VaultSessionManager.lockVaultIfOutsideSession(this)
                                callback?.invoke(resultPath)
                            }
                        }
                    } finally {
                        ForegroundOperationRegistry.release(this, "crypto-encrypt")
                    }
                }
            }
        }

        resolveCryptoTransferConflicts(
            progressDialog = initialProgressDialog,
            buildPairs = { listOf(TransferPairSpec(srcFile, targetDir, isTargetEncrypted = true)) }
        ) { scan, activeProgressDialog ->
            val resolution = scan.resolutions[srcFile.absolutePath] ?: CONFLICT_KEEP_BOTH
            runSingleEncryptWithResolution(
                resolution,
                scan.encIndexes[targetDir.absolutePath],
                scan.identicalTargets,
                scan.identicalPaths,
                scan.nonIdenticalPaths,
                activeProgressDialog
            )
        }
    }
}

fun BaseSimpleActivity.ensureVaultUnlocked(
    forcePrompt: Boolean = false,
    onCancelled: () -> Unit = {},
    onUnlocked: () -> Unit
) {
    // forcePrompt is used by settings that must re-verify the password on every visit even when the vault key
    // happens to still be in RAM from another operation. Being inside an encrypted album (temporarilyShowHidden)
    // is handled by the caller, which simply skips the force prompt while that session is active.
    if (!forcePrompt && CryptoManager.isVaultUnlocked) {
        onUnlocked()
        return
    }

    val initialType = CryptoManager.getVaultProtectionType(this)
        ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) {
            PROTECTION_PASSWORD
        } else {
            PROTECTION_PIN
        }
    VaultSecurityDialog(
        activity = this,
        requiredHash = config.hiddenPasswordHash,
        showTabIndex = initialType,
        minimalist = true
    ) { _, type, success ->
        if (success) {
            config.hiddenProtectionType = type
            onUnlocked()
        } else {
            onCancelled()
        }
    }
}
