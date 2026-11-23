package org.fossify.gallery.extensions

import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files
import android.provider.MediaStore.Images
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FileConflictDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.gallery.dialogs.AppSecurityDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.activities.AboutActivity
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.activities.VideoPlayerActivity
import org.fossify.gallery.dialogs.AllFilesPermissionDialog
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.EncryptConfirmationDialog
import org.fossify.gallery.dialogs.resolveAlbumCryptoOptions
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.dialogs.PickDirectoryDialog
import org.fossify.gallery.dialogs.PickVaultAlbumDialog
import org.fossify.gallery.dialogs.PickVirtualAlbumDialog
import org.fossify.gallery.dialogs.ResizeMultipleImagesDialog
import org.fossify.gallery.dialogs.ResizeWithPathDialog
import org.fossify.gallery.dialogs.SmartConflictItem
import org.fossify.gallery.dialogs.SmartFileConflictDialog
import org.fossify.gallery.dialogs.VaultBiometricOfferDialog
import org.fossify.gallery.dialogs.VaultChoiceDialog
import org.fossify.gallery.dialogs.VaultNoticeDialog
import org.fossify.gallery.dialogs.VaultSecurityDialog
import org.fossify.gallery.dialogs.resolveSkippedCryptoFiles
import org.fossify.gallery.helpers.CIPHER_APP_DIR_NAME
import org.fossify.gallery.helpers.CLI_STAGING_DIR_NAME
import org.fossify.gallery.helpers.CLI_WRITING_SENTINEL
import org.fossify.gallery.helpers.CliBridgeManager
import org.fossify.gallery.helpers.SECRETS_DIR_NAME
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.VAULT_ACTION_ARCHIVED
import org.fossify.gallery.helpers.VaultArchiveManager
import org.fossify.gallery.helpers.VaultExportHeaderCheck
import org.fossify.gallery.helpers.checkVaultExportTarget
import org.fossify.gallery.helpers.CONFLICT_HANDLING_AUTO
import org.fossify.gallery.helpers.CONFLICT_HANDLING_SMART_DIALOG
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CopyMovePolicy
import org.fossify.gallery.helpers.ArchivedVault
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.EXTRA_ALBUM_PATH
import org.fossify.gallery.helpers.ENCRYPTED_BIN_EXT
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.PickDirectoryMode
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.TEMP_FOLDER_NAME
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.extensions.VirtualAlbumConflictHelper
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.helpers.copyMoveFilesWithProgress
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.models.DateTaken
import java.io.*
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.net.toUri
import org.fossify.gallery.helpers.canWriteToDirectory
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.clearBiometricKey
import org.fossify.gallery.helpers.clearCachedVaultRoot
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.applyEncryptedCachePolicy
import org.fossify.gallery.helpers.copyOrMoveEncryptedFileToDirectory
import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.BatchOperationInfo
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.JournalItem
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationFaults
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.operations.TransientOperationWaiter
import java.io.IOException
import java.time.Instant
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.decryptFolder
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.ensureCipherRootDirectory
import org.fossify.gallery.helpers.exportEncryptedVault
import org.fossify.gallery.helpers.findCandidateExternalVaultRoots
import org.fossify.gallery.helpers.findMergeTargetForDecryptFolder
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.getCipherRootDirectory
import org.fossify.gallery.helpers.getDecryptedFolderName
import org.fossify.gallery.helpers.getDefaultVaultRoot
import org.fossify.gallery.helpers.getOrCreateDefaultHiddenAlbum
import org.fossify.gallery.helpers.getVaultProtectionType
import org.fossify.gallery.helpers.inspectStandardFilenameConflict
import org.fossify.gallery.helpers.inspectTransferConflict
import org.fossify.gallery.helpers.isConfiguredVaultTemporarilyUnavailable
import org.fossify.gallery.helpers.isEncryptedFolder
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.isSupportedMediaName
import org.fossify.gallery.helpers.isVaultMediaPath
import org.fossify.gallery.helpers.isVaultRecycleBinPath
import org.fossify.gallery.helpers.listExternalStorageVolumeRoots
import org.fossify.gallery.helpers.previewDecryptAlbumConflict
import org.fossify.gallery.helpers.resolveExistingVaultInDirectory
import org.fossify.gallery.helpers.resolveExportVaultTargetDir
import org.fossify.gallery.helpers.resolvePickedPathToFile
import org.fossify.gallery.helpers.resolveSyncSourceRoot
import org.fossify.gallery.helpers.secureWipeAndDeleteFile
import org.fossify.gallery.helpers.ShareMetadataSanitizationException
import org.fossify.gallery.helpers.ShareMetadataSanitizer
import org.fossify.gallery.helpers.setVaultRootLocation
import org.fossify.gallery.helpers.showSkippedIdenticalToast
import org.fossify.gallery.helpers.syncVaultFromExternalDirectory
import org.fossify.gallery.helpers.verifyVaultOnDisk

fun Activity.sharePath(path: String) {
    ensureBackgroundThread {
        val shared = sanitizeForSharing(listOf(path)) ?: return@ensureBackgroundThread
        sharePathIntent(shared.first(), BuildConfig.APPLICATION_ID)
    }
}

fun Activity.sharePaths(paths: ArrayList<String>) {
    ensureBackgroundThread {
        val shared = sanitizeForSharing(paths) ?: return@ensureBackgroundThread
        sharePathsIntent(ArrayList(shared), BuildConfig.APPLICATION_ID)
    }
}

/** Returns null when a metadata-free copy could not be made; in that case sharing is aborted. */
private fun Activity.sanitizeForSharing(paths: List<String>): List<String>? = try {
    ShareMetadataSanitizer.sanitizedPaths(this, paths)
} catch (_: ShareMetadataSanitizationException) {
    runOnUiThread { toast(R.string.share_metadata_failed) }
    null
} catch (_: Throwable) {
    paths
}

fun Activity.shareMediumPath(path: String) {
    sharePath(path)
}

fun Activity.shareMediaPaths(paths: ArrayList<String>) {
    sharePaths(paths)
}

fun Activity.setAs(path: String) {
    setAsIntent(path, BuildConfig.APPLICATION_ID)
}

fun Activity.openPath(path: String, forceChooser: Boolean, extras: HashMap<String, Boolean> = HashMap()) {
    // commons only adds IS_FROM_GALLERY for org.fossify.gallery(.debug). This fork uses
    // com.calypso.gallery, so mark gallery-originated ACTION_VIEW intents here. Otherwise the
    // viewer relay treats a directly opened video as external and hides Shuffle, and drops mMedia.
    extras[IS_FROM_GALLERY] = true
    openPathIntent(path, forceChooser, BuildConfig.APPLICATION_ID, extras = extras)
}

/**
 * ACTION_VIEW for a video that must keep its gallery context. The plain commons openPath helper
 * cannot carry String extras, which made the viewer lose the album path and therefore the album's
 * random sort - hiding Shuffle when the video was opened directly.
 */
fun Activity.openVideoWithDefaultPlayer(
    path: String,
    albumPath: String,
    showAll: Boolean,
    extras: HashMap<String, Boolean> = HashMap()
) {
    ensureBackgroundThread {
        val uri = getFinalUriFromPath(path, BuildConfig.APPLICATION_ID) ?: return@ensureBackgroundThread
        val mimeType = getUriMimeType(path, uri)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(REAL_FILE_PATH, path)
            putExtra(IS_FROM_GALLERY, true)
            putExtra(EXTRA_ALBUM_PATH, albumPath)
            putExtra(SHOW_ALL, showAll)
            extras.forEach { (key, value) -> putExtra(key, value) }
            startActivity(this)
        }
    }
}

fun Activity.launchGesturePlayer(path: String, extras: HashMap<String, Boolean> = HashMap()) {
    ensureBackgroundThread {
        val newUri = getFinalUriFromPath(path, BuildConfig.APPLICATION_ID)
        if (newUri == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return@ensureBackgroundThread
        }

        val mimeType = getUriMimeType(path, newUri)
        runOnUiThread {
            Intent(applicationContext, VideoPlayerActivity::class.java).apply {
                setDataAndType(newUri, mimeType)
                for ((key, value) in extras) putExtra(key, value)
                startActivity(this)
            }
        }
    }
}

fun Activity.openEditor(path: String, forceChooser: Boolean = false) {
    val newPath = path.removePrefix("file://")
    openEditorIntent(newPath, forceChooser, BuildConfig.APPLICATION_ID)
}

fun Activity.launchCamera() {
    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    launchActivityIntent(intent)
}

fun SimpleActivity.launchSettings() {
    hideKeyboard()
    startActivity(Intent(applicationContext, SettingsActivity::class.java))
}

fun SimpleActivity.launchAbout() {
    hideKeyboard()
    startActivity(Intent(applicationContext, AboutActivity::class.java))
}

fun BaseSimpleActivity.handleMediaManagementPrompt(callback: () -> Unit) {
    if (canManageMedia() || isExternalStorageManager()) {
        callback()
    } else if (isRPlus() && resources.getBoolean(R.bool.require_all_files_access) && !config.avoidShowingAllFilesPrompt) {
        if (Environment.isExternalStorageManager()) {
            callback()
        } else {
            var messagePrompt = getString(org.fossify.commons.R.string.access_storage_prompt)
            messagePrompt += if (isSPlus()) {
                "\n\n${getString(R.string.media_management_alternative)}"
            } else {
                "\n\n${getString(R.string.alternative_media_access)}"
            }

            AllFilesPermissionDialog(this, messagePrompt, callback = { success ->
                if (success) {
                    launchGrantAllFilesIntent()
                }
            }, neutralPressed = {
                config.avoidShowingAllFilesPrompt = true
                callback()
            })
        }
    } else {
        callback()
    }
}

fun BaseSimpleActivity.launchGrantAllFilesIntent() {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.addCategory("android.intent.category.DEFAULT")
        intent.data = "package:$packageName".toUri()
        startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent()
        intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
        try {
            startActivity(intent)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun AppCompatActivity.showSystemUI() {
    window.showBars()
}

fun AppCompatActivity.hideSystemUI() {
    window.hideBars(transient = false)
}

fun BaseSimpleActivity.addNoMedia(path: String, callback: () -> Unit) {
    val file = File(path, NOMEDIA)
    if (getDoesFilePathExist(file.absolutePath)) {
        config.addKnownHiddenFolder(path)
        callback()
        return
    }

    if (needsStupidWritePermissions(path)) {
        handleSAFDialog(file.absolutePath) {
            if (!it) {
                return@handleSAFDialog
            }

            val fileDocument = getDocumentFile(path)
            if (fileDocument?.exists() == true && fileDocument.isDirectory) {
                fileDocument.createFile("", NOMEDIA)
                addNoMediaIntoMediaStore(file.absolutePath)
                config.addKnownHiddenFolder(path)
                callback()
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                callback()
            }
        }
    } else {
        try {
            if (file.createNewFile()) {
                config.addKnownHiddenFolder(path)
                ensureBackgroundThread {
                    addNoMediaIntoMediaStore(file.absolutePath)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
        callback()
    }
}

fun BaseSimpleActivity.addNoMediaIntoMediaStore(path: String) {
    try {
        val content = ContentValues().apply {
            put(Files.FileColumns.TITLE, NOMEDIA)
            put(Files.FileColumns.DATA, path)
            put(Files.FileColumns.MEDIA_TYPE, Files.FileColumns.MEDIA_TYPE_NONE)
        }
        contentResolver.insert(Files.getContentUri("external"), content)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun BaseSimpleActivity.removeNoMedia(path: String, callback: (() -> Unit)? = null) {
    config.removeKnownHiddenFolder(path)
    val file = File(path, NOMEDIA)
    if (!getDoesFilePathExist(file.absolutePath)) {
        deleteFromMediaStore(file.absolutePath) {
            rescanFolderMedia(path)
        }
        callback?.invoke()
        return
    }

    tryDeleteFileDirItem(file.toFileDirItem(applicationContext), false, false) {
        callback?.invoke()
        deleteFromMediaStore(file.absolutePath) { needsRescan ->
            if (needsRescan) {
                rescanAndDeletePath(path) {
                    rescanFolderMedia(path)
                }
            } else {
                rescanFolderMedia(path)
            }
        }
    }
}

private const val VAULT_ACTION_CREATE_NEW = 1
private const val VAULT_ACTION_USE_EXISTING = 2
private const val ARCHIVED_ITEM_ID_BASE = 1000
private const val INVALID_ARCHIVE_ITEM_ID_BASE = 2000
private const val DIALOG_MESSAGE_TEXT_SIZE_SP = 14f
private const val VAULT_LOCATION_OPTION_STANDARD = 1
private const val VAULT_LOCATION_OPTION_CUSTOM = 2

private data class VaultConfigSnapshot(
    val vaultRootPath: String,
    val wrappedKeyData: String,
    val hiddenPasswordHash: String,
    val isHiddenPasswordProtectionOn: Boolean,
    val isVaultBiometricEnabled: Boolean,
    val biometricWrappedBundle: String
)

fun BaseSimpleActivity.hasVaultCreated(): Boolean {
    // The vault exists only while its .vault header is physically on disk. This is re-checked on
    // every call (no cached "true"). If the vault folder was removed externally, all leftover
    // credentials, the biometric key and the in-memory keys are wiped, so the user is offered
    // "create vault" instead of a password prompt for a vault that does not exist.
    return CryptoManager.verifyVaultOnDisk(this)
}

/**
 * Handles the pull-to-open vault entry flow (verifying on-disk vault existence, storage permission,
 * password prompt or vault creation) while keeping the calling activity visible behind the dialog.
 */
fun BaseSimpleActivity.openSecretVaultPrompt(
    onDialogShowingChanged: (Boolean) -> Unit = {},
    onMissingVaultReset: () -> Unit = {},
    onCancelled: () -> Unit = {},
    onVaultReady: (alreadyUnlocked: Boolean) -> Unit
) {
    val vaultExists = hasVaultCreated()
    if (!vaultExists) {
        onMissingVaultReset()
    }

    if (vaultExists && config.temporarilyShowHidden && CryptoManager.isVaultUnlocked) {
        onVaultReady(true)
        return
    }

    if (isRPlus() && !isExternalStorageManager()) {
        onDialogShowingChanged(false)
        onCancelled()
        GrantAllFilesDialog(this)
        return
    }

    onDialogShowingChanged(true)
    if (vaultExists) {
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
            minimalist = true,
            onCancelCallback = {
                onDialogShowingChanged(false)
                onCancelled()
            }
        ) { _, type, success ->
            if (success) {
                onDialogShowingChanged(false)
                config.hiddenProtectionType = type
                onVaultReady(false)
            }
        }
    } else if (CryptoManager.isConfiguredVaultTemporarilyUnavailable(this)) {
        promptUnavailableExternalVault(
            onRetryAvailable = {
                openSecretVaultPrompt(
                    onDialogShowingChanged = onDialogShowingChanged,
                    onMissingVaultReset = onMissingVaultReset,
                    onCancelled = onCancelled,
                    onVaultReady = onVaultReady
                )
            },
            onLocationChanged = {
                onDialogShowingChanged(false)
                onVaultReady(false)
            },
            onCancelled = {
                onDialogShowingChanged(false)
                onCancelled()
            }
        )
    } else {
        promptCreateVault(
            onCreated = {
                onDialogShowingChanged(false)
                onVaultReady(false)
            },
            onCancelled = {
                onDialogShowingChanged(false)
                onCancelled()
            }
        )
    }
}

fun BaseSimpleActivity.promptUnavailableExternalVault(
    onRetryAvailable: () -> Unit,
    onLocationChanged: () -> Unit,
    onCancelled: () -> Unit = {}
) {
    val messageView = layoutInflater.inflate(org.fossify.commons.R.layout.dialog_message, null)
    val configuredPath = config.vaultRootPath
    val displayPath = if (configuredPath.isNotEmpty()) humanizePath(configuredPath) else ""
    messageView.findViewById<TextView>(org.fossify.commons.R.id.message).text =
        getString(R.string.external_vault_unavailable_prompt, displayPath)

    getAlertDialogBuilder()
        .setPositiveButton(R.string.retry) { _, _ ->
            if (hasVaultCreated()) {
                onRetryAvailable()
            } else if (CryptoManager.isConfiguredVaultTemporarilyUnavailable(this)) {
                promptUnavailableExternalVault(onRetryAvailable, onLocationChanged, onCancelled)
            } else {
                onCancelled()
            }
        }
        .setNeutralButton(R.string.change_vault_location) { _, _ ->
            promptVaultSetupChoice(
                confirmBeforeCreatingNew = false,
                onCreated = onLocationChanged,
                onCancelled = {
                    promptUnavailableExternalVault(onRetryAvailable, onLocationChanged, onCancelled)
                }
            )
        }
        .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ ->
            onCancelled()
        }
        .setOnCancelListener {
            onCancelled()
        }
        .apply {
            setupDialogStuff(messageView, this, R.string.external_vault_unavailable_title)
        }
}

fun BaseSimpleActivity.promptCreateVault(
    onCreated: () -> Unit,
    onCancelled: () -> Unit = {},
    // Fired as soon as the vault exists on disk, before the post-creation dialogs. Callers such
    // as Settings use it to refresh their rows even if the user backs out of / leaves those
    // dialogs instead of acknowledging the final tutorial.
    onVaultCreated: () -> Unit = {},
) {
    if (CryptoManager.isConfiguredVaultTemporarilyUnavailable(this)) {
        promptUnavailableExternalVault(
            onRetryAvailable = {
                if (CryptoManager.isVaultUnlocked) {
                    onCreated()
                } else {
                    ensureVaultUnlocked(onCancelled = onCancelled, onUnlocked = onCreated)
                }
            },
            onLocationChanged = onCreated,
            onCancelled = onCancelled
        )
        return
    }

    promptVaultSetupChoice(
        confirmBeforeCreatingNew = false,
        onCreated = onCreated,
        onCancelled = onCancelled,
        onVaultCreated = onVaultCreated,
    )
}

fun BaseSimpleActivity.promptVaultSetupChoice(
    confirmBeforeCreatingNew: Boolean,
    onCreated: () -> Unit,
    onCancelled: () -> Unit = {},
    onVaultCreated: () -> Unit = {},
) {
    val items = arrayListOf(
        RadioItem(VAULT_ACTION_CREATE_NEW, getString(R.string.create_new_vault_option)),
        RadioItem(VAULT_ACTION_USE_EXISTING, getString(R.string.use_existing_vault_option))
    )
    if (VaultArchiveManager.archivedEntries(this).isNotEmpty()) {
        items.add(RadioItem(VAULT_ACTION_ARCHIVED, getString(R.string.select_archived_vault)))
    }

    VaultChoiceDialog(
        activity = this,
        title = getString(R.string.vault_setup_action_title),
        items = items,
        checkedItemId = -1,
        cancelCallback = {
            onCancelled()
        }
    ) { selected ->
        val reopenStage1 = {
            promptVaultSetupChoice(confirmBeforeCreatingNew, onCreated, onCancelled, onVaultCreated)
        }
        when (selected) {
            VAULT_ACTION_CREATE_NEW -> {
                promptWhereToCreateVault(
                    confirmBeforeCreatingNew = confirmBeforeCreatingNew,
                    onVaultReady = onCreated,
                    onBack = reopenStage1,
                    onVaultCreated = onVaultCreated,
                )
            }
            VAULT_ACTION_USE_EXISTING -> {
                pickExistingVaultAndUnlock(
                    onVaultReady = onCreated,
                    onBack = reopenStage1
                )
            }
            VAULT_ACTION_ARCHIVED -> {
                showArchivedVaultPicker(onVaultReady = onCreated, onBack = reopenStage1)
            }
            else -> onCancelled()
        }
    }
}

private fun BaseSimpleActivity.showArchivedVaultPicker(onVaultReady: () -> Unit, onBack: () -> Unit) {
    val valid = VaultArchiveManager.archivedEntries(this)
    val validIds = valid.map { it.vault.id }.toHashSet()
    val invalid = config.archivedVaults.filterNot { validIds.contains(it.id) }

    val items = ArrayList<RadioItem>()
    val labels = ArrayList<String>()
    valid.forEachIndexed { index, entry ->
        val label = if (entry.dateKnown) {
            getString(R.string.archived_vault_indexed, index + 1)
        } else {
            getString(R.string.archived_vault_unknown_date)
        }
        labels.add(label)
        items.add(RadioItem(ARCHIVED_ITEM_ID_BASE + index, label))
    }
    invalid.forEachIndexed { index, _ ->
        labels.add(getString(R.string.archived_vault_invalid))
        items.add(RadioItem(INVALID_ARCHIVE_ITEM_ID_BASE + index, labels.last()))
    }
    if (items.isEmpty()) {
        onBack()
        return
    }

    fun handleSelection(id: Int) {
        if (id >= INVALID_ARCHIVE_ITEM_ID_BASE) {
            val entry = invalid[id - INVALID_ARCHIVE_ITEM_ID_BASE]
            VaultNoticeDialog(
                activity = this,
                title = getString(R.string.archived_vault_invalid),
                message = getString(R.string.archived_vault_invalid_message, humanizePath(entry.path)),
                positiveText = getString(org.fossify.commons.R.string.ok),
                negativeText = getString(R.string.copy_path),
                cancelable = true,
                onPositive = {},
                onNegative = {
                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText(getString(R.string.archived_vault_invalid), entry.path)
                    )
                    // Vault-derived path: clearKeys(context) must wipe it on lock.
                    CryptoManager.vaultClipboardTouched = true
                },
            )
            return
        }

        val entry = valid[id - ARCHIVED_ITEM_ID_BASE].vault
        // Activation happens in place; moving back is a separate, explicit "Unarchive vault" action.
        if (!VaultArchiveManager.activateArchivedVault(this, entry)) {
            toast(R.string.archive_vault_failed)
            onBack()
            return
        }
        VaultSecurityDialog(
            activity = this,
            requiredHash = config.hiddenPasswordHash,
            showTabIndex = SHOW_ALL_TABS,
            minimalist = false,
        ) { _, _, success ->
            if (success) {
                toast(R.string.archived_vault_activated)
                onVaultReady()
            } else {
                showArchivedVaultPicker(onVaultReady, onBack)
            }
        }
    }

    fun showArchiveSizeInfo() {
        val totalBytes = VaultArchiveManager.archiveRoot().listFiles()
            ?.filter { it.isDirectory }
            ?.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() }
            ?: 0L
        VaultNoticeDialog(
            activity = this,
            title = getString(R.string.archive_total_size_title),
            message = getString(
                R.string.archive_total_size,
                android.text.format.Formatter.formatFileSize(this, totalBytes)
            ),
            positiveText = getString(org.fossify.commons.R.string.ok),
            cancelable = true,
            onPositive = {},
        )
    }

    VaultChoiceDialog(
        activity = this,
        title = getString(R.string.select_archived_vault),
        items = items,
        checkedItemId = -1,
        infoIconRes = org.fossify.commons.R.drawable.ic_info_vector,
        infoContentDescription = getString(R.string.archive_total_size_title),
        onInfoClick = { showArchiveSizeInfo() },
        cancelCallback = { onBack() },
    ) { selectedId ->
        handleSelection(selectedId)
    }
}

fun BaseSimpleActivity.promptWhereToCreateVault(
    confirmBeforeCreatingNew: Boolean = false,
    onVaultReady: () -> Unit,
    onBack: () -> Unit,
    onVaultCreated: () -> Unit = {},
) {
    val items = arrayListOf(
        RadioItem(VAULT_LOCATION_OPTION_STANDARD, getString(R.string.vault_location_default_short)),
        RadioItem(VAULT_LOCATION_OPTION_CUSTOM, getString(R.string.vault_location_custom_short))
    )

    VaultChoiceDialog(
        activity = this,
        title = getString(R.string.where_to_create_vault_title),
        items = items,
        checkedItemId = -1,
        cancelCallback = {
            onBack()
        }
    ) { selected ->
        val reopenStage2 = {
            promptWhereToCreateVault(confirmBeforeCreatingNew, onVaultReady, onBack, onVaultCreated)
        }
        when (selected) {
            VAULT_LOCATION_OPTION_STANDARD -> {
                handleCreateTargetSelected(
                    targetDir = null,
                    confirmBeforeCreatingNew = confirmBeforeCreatingNew,
                    onVaultReady = onVaultReady,
                    onBack = reopenStage2,
                    onPickAnother = reopenStage2,
                    onVaultCreated = onVaultCreated,
                )
            }
            VAULT_LOCATION_OPTION_CUSTOM -> {
                pickCustomFolderForCreateVault(
                    confirmBeforeCreatingNew = confirmBeforeCreatingNew,
                    onVaultReady = onVaultReady,
                    onBack = reopenStage2,
                    onVaultCreated = onVaultCreated,
                )
            }
            else -> onBack()
        }
    }
}

private fun BaseSimpleActivity.pickCustomFolderForCreateVault(
    confirmBeforeCreatingNew: Boolean,
    onVaultReady: () -> Unit,
    onBack: () -> Unit,
    onVaultCreated: () -> Unit,
) {
    pickCustomVaultDirectory(
        initialPath = defaultCustomVaultPickerPath(),
        onPicked = { pickedDir ->
            handleCreateTargetSelected(
                targetDir = pickedDir,
                confirmBeforeCreatingNew = confirmBeforeCreatingNew,
                onVaultReady = onVaultReady,
                onBack = onBack,
                onPickAnother = {
                    pickCustomFolderForCreateVault(
                        confirmBeforeCreatingNew,
                        onVaultReady,
                        onBack,
                        onVaultCreated,
                    )
                },
                onVaultCreated = onVaultCreated,
            )
        },
        onCancelled = onBack
    )
}

private fun BaseSimpleActivity.pickExistingVaultAndUnlock(
    onVaultReady: () -> Unit,
    onBack: () -> Unit
) {
    val initialDir = CryptoManager.findCandidateExternalVaultRoots(this)
        .firstOrNull()
        ?.let { it.parentFile ?: it }
        ?.takeIf { it.exists() && it.isDirectory }
        ?.absolutePath
        ?: config.vaultRootPath
            .takeIf { it.isNotEmpty() }
            ?.let { File(it).parentFile }
            ?.takeIf { it.exists() && it.isDirectory }
            ?.absolutePath
        ?: CryptoManager.listExternalStorageVolumeRoots(this)
            .firstOrNull { it.exists() && it.isDirectory }
            ?.absolutePath
        ?: internalStoragePath

    pickCustomVaultDirectory(
        initialPath = initialDir,
        onPicked = { pickedDir ->
            val existingVault = CryptoManager.resolveExistingVaultInDirectory(pickedDir)
            if (existingVault == null) {
                showNoExistingVaultDialog(
                    onRetryPick = { pickExistingVaultAndUnlock(onVaultReady, onBack) },
                    onBack = onBack
                )
            } else {
                unlockCandidateVault(
                    existingVault = existingVault,
                    onVaultReady = onVaultReady,
                    onBack = { pickExistingVaultAndUnlock(onVaultReady, onBack) }
                )
            }
        },
        onCancelled = onBack
    )
}

private fun BaseSimpleActivity.showNoExistingVaultDialog(
    onRetryPick: () -> Unit,
    onBack: () -> Unit
) {
    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.vault_setup_action_title),
        message = getString(R.string.no_existing_vault_at_location),
        positiveText = getString(org.fossify.commons.R.string.ok),
        negativeText = getString(org.fossify.commons.R.string.cancel),
        cancelable = true,
        onPositive = { onRetryPick() },
        onNegative = { onBack() },
        onCancel = { onBack() },
    )
}

private fun BaseSimpleActivity.defaultCustomVaultPickerPath(): String {
    return config.vaultRootPath
        .takeIf { it.isNotEmpty() }
        ?.let { File(it).parentFile }
        ?.takeIf { it.exists() && it.isDirectory }
        ?.absolutePath
        ?: CryptoManager.listExternalStorageVolumeRoots(this)
            .firstOrNull { it.exists() && it.isDirectory }
            ?.absolutePath
        ?: internalStoragePath
}

private fun BaseSimpleActivity.pickCustomVaultDirectory(
    initialPath: String,
    requireWritable: Boolean = true,
    onPicked: (File) -> Unit,
    onCancelled: () -> Unit
) {
    var selectionConfirmed = false
    val picker = FilePickerDialog(
        activity = this,
        currPath = initialPath,
        pickFile = false,
        showHidden = config.shouldShowHidden,
        showFAB = true,
        canAddShowHiddenButton = true,
        enforceStorageRestrictions = false
    ) { pickedPath ->
        selectionConfirmed = true
        validatePickedVaultDirectory(pickedPath, requireWritable, onPicked, onCancelled)
    }

    val attachedDismiss = runCatching {
        val dialogField = FilePickerDialog::class.java.getDeclaredField("mDialog")
        dialogField.isAccessible = true
        val alertDialog = dialogField.get(picker) as? androidx.appcompat.app.AlertDialog
        alertDialog?.setOnDismissListener {
            window?.decorView?.post {
                if (!selectionConfirmed) {
                    onCancelled()
                }
            }
        }
        alertDialog != null
    }.getOrDefault(false)

    if (!attachedDismiss) {
        onCancelled()
    }
}

private fun BaseSimpleActivity.validatePickedVaultDirectory(
    pickedPath: String,
    requireWritable: Boolean = true,
    onValid: (File) -> Unit,
    onCancelled: () -> Unit
) {
    val resolvedDir = CryptoManager.resolvePickedPathToFile(this, pickedPath)
    if (resolvedDir == null) {
        showVaultLocationNoticeDialog(R.string.usb_direct_filesystem_required, onCancelled)
        return
    }

    if (requireWritable) {
        if (!resolvedDir.exists()) {
            resolvedDir.mkdirs()
        }
        if (!resolvedDir.isDirectory || !CryptoManager.canWriteToDirectory(resolvedDir)) {
            showVaultLocationNoticeDialog(R.string.vault_location_not_writable, onCancelled)
            return
        }
    } else {
        if (!resolvedDir.exists() || !resolvedDir.isDirectory || !resolvedDir.canRead()) {
            showVaultLocationNoticeDialog(R.string.vault_location_not_readable, onCancelled)
            return
        }
    }

    onValid(resolvedDir)
}

private fun BaseSimpleActivity.showVaultLocationNoticeDialog(messageResId: Int, onDone: () -> Unit) {
    // Use the theme-aware card; Commons' platform buttons would otherwise stay green.
    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.vault_location),
        message = getString(messageResId),
        positiveText = getString(org.fossify.commons.R.string.ok),
        cancelable = true,
        onPositive = onDone,
        onCancel = onDone,
    )
}

private fun BaseSimpleActivity.unlockCandidateVault(
    existingVault: File,
    onVaultReady: () -> Unit,
    onBack: () -> Unit
) {
    val currentActiveRoot = CryptoManager.findVaultRoot(this)?.absolutePath
    if (currentActiveRoot != null && currentActiveRoot.equals(existingVault.absolutePath, ignoreCase = true) && CryptoManager.isVaultUnlocked) {
        onVaultReady()
        return
    }

    val snapshot = VaultConfigSnapshot(
        vaultRootPath = config.vaultRootPath,
        wrappedKeyData = config.vaultWrappedKeyData,
        hiddenPasswordHash = config.hiddenPasswordHash,
        isHiddenPasswordProtectionOn = config.isHiddenPasswordProtectionOn,
        isVaultBiometricEnabled = config.isVaultBiometricEnabled,
        biometricWrappedBundle = config.vaultBiometricWrappedBundle
    )
    val switchingToDifferentVault = snapshot.vaultRootPath.isNotEmpty() &&
        !snapshot.vaultRootPath.trimEnd('/', '\\').equals(existingVault.absolutePath.trimEnd('/', '\\'), ignoreCase = true)

    CryptoManager.lockVault(this)
    CryptoManager.setVaultRootLocation(this, existingVault)
    if (switchingToDifferentVault) {
        // Keep previous biometric bundle out of the unlock prompt for the candidate vault;
        // restored in full if the user backs out before unlocking.
        config.isVaultBiometricEnabled = false
    }

    toast(getString(R.string.existing_vault_detected_at_location, humanizePath(existingVault.absolutePath)))

    val initialType = CryptoManager.getVaultProtectionType(this)
        ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN

    VaultSecurityDialog(
        activity = this,
        requiredHash = "existing_vault",
        showTabIndex = initialType,
        minimalist = false,
        onCancelCallback = {
            CryptoManager.lockVault(this)
            config.vaultRootPath = snapshot.vaultRootPath
            config.vaultWrappedKeyData = snapshot.wrappedKeyData
            config.hiddenPasswordHash = snapshot.hiddenPasswordHash
            config.isHiddenPasswordProtectionOn = snapshot.isHiddenPasswordProtectionOn
            config.isVaultBiometricEnabled = snapshot.isVaultBiometricEnabled
            config.vaultBiometricWrappedBundle = snapshot.biometricWrappedBundle
            if (snapshot.vaultRootPath.isNotEmpty()) {
                CryptoManager.setVaultRootLocation(this, File(snapshot.vaultRootPath))
            } else {
                CryptoManager.clearCachedVaultRoot()
            }
            onBack()
        }
    ) { _, type, success ->
        if (success) {
            config.hiddenProtectionType = type
            ensureBackgroundThread {
                if (switchingToDifferentVault) {
                    CryptoManager.clearBiometricKey(this)
                    VirtualAlbumSyncManager.evictHiddenVirtualAlbumsFromDbCache(this)
                    CryptoManager.clearAppImageCaches(this)
                }
                CryptoManager.getOrCreateDefaultHiddenAlbum(this)
                VirtualAlbumSyncManager.syncVirtualAlbums(this, force = true)
                runOnUiThread {
                    onVaultReady()
                }
            }
        }
    }
}

private fun BaseSimpleActivity.handleCreateTargetSelected(
    targetDir: File?,
    confirmBeforeCreatingNew: Boolean,
    onVaultReady: () -> Unit,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onVaultCreated: () -> Unit,
) {
    val lookupDir = targetDir ?: CryptoManager.getDefaultVaultRoot()
    val existingVault = CryptoManager.resolveExistingVaultInDirectory(lookupDir)
    if (existingVault != null) {
        VaultNoticeDialog(
            activity = this,
            title = getString(R.string.vault_setup_action_title),
            message = getString(R.string.unlock_existing_vault_at_location_prompt, humanizePath(existingVault.absolutePath)),
            positiveText = getString(org.fossify.commons.R.string.yes),
            negativeText = getString(org.fossify.commons.R.string.no),
            cancelable = true,
            onPositive = {
                unlockCandidateVault(
                    existingVault = existingVault,
                    onVaultReady = onVaultReady,
                    onBack = onPickAnother
                )
            },
            onNegative = { onPickAnother() },
            onCancel = { onBack() },
        )
        return
    }

    val switchingFromAnotherVault = hasVaultCreated() || CryptoManager.isConfiguredVaultTemporarilyUnavailable(this)
    val startCreationDialog = {
        val customDir = if (targetDir == null && switchingFromAnotherVault) {
            CryptoManager.ensureCipherRootDirectory()
            CryptoManager.getDefaultVaultRoot()
        } else {
            targetDir
        }
        VaultSecurityDialog(
            activity = this,
            requiredHash = "",
            showTabIndex = SHOW_ALL_TABS,
            minimalist = false,
            customTargetDir = customDir,
            onCancelCallback = onBack
        ) { hash, type, success ->
            if (success) {
                config.isHiddenPasswordProtectionOn = true
                config.hiddenPasswordHash = hash
                config.hiddenProtectionType = type
                ensureBackgroundThread {
                    CryptoManager.getOrCreateDefaultHiddenAlbum(this)
                    VirtualAlbumSyncManager.syncVirtualAlbums(this, force = true)
                    runOnUiThread {
                        toast(org.fossify.commons.R.string.protection_setup_successfully)
                        // Refresh callers (e.g. Settings) as soon as the vault really exists on disk.
                        // The post-creation dialogs below can be backed out of or abandoned, so the
                        // final tutorial's callback is not a reliable place to sync the UI state.
                        onVaultCreated()
                        showVaultPasswordSavedDialog(isInitialCreation = true) { onVaultReady() }
                    }
                }
            }
        }
    }

    val targetDisplayDir = if (targetDir == null) {
        CryptoManager.getDefaultVaultRoot()
    } else if (targetDir.name.equals(SECRETS_DIR_NAME, ignoreCase = true)) {
        targetDir
    } else {
        File(targetDir, SECRETS_DIR_NAME)
    }

    if (confirmBeforeCreatingNew && switchingFromAnotherVault) {
        VaultNoticeDialog(
            activity = this,
            title = getString(R.string.vault_setup_action_title),
            message = getString(R.string.create_new_vault_at_location_prompt, humanizePath(targetDisplayDir.absolutePath)),
            positiveText = getString(org.fossify.commons.R.string.yes),
            negativeText = getString(org.fossify.commons.R.string.no),
            cancelable = true,
            onPositive = { startCreationDialog() },
            onNegative = { onBack() },
            onCancel = { onBack() },
        )
    } else {
        startCreationDialog()
    }
}

private fun BaseSimpleActivity.defaultSyncSourcePickerPath(): String {
    return CryptoManager.findCandidateExternalVaultRoots(this)
        .firstOrNull()
        ?.let { it.parentFile ?: it }
        ?.takeIf { it.exists() && it.isDirectory }
        ?.absolutePath
        ?: CryptoManager.listExternalStorageVolumeRoots(this)
            .firstOrNull { it.exists() && it.isDirectory }
            ?.absolutePath
        ?: internalStoragePath
}

private fun BaseSimpleActivity.defaultCliPickerPath(): String? {
    val internalCli = File(File(internalStoragePath, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME)
    if (internalCli.isDirectory) {
        return internalCli.parentFile?.absolutePath
    }
    for (volume in CryptoManager.listExternalStorageVolumeRoots(this)) {
        val nestedCli = File(File(volume, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME)
        val directCli = File(volume, CLI_STAGING_DIR_NAME)
        if (nestedCli.isDirectory || directCli.isDirectory) {
            return volume.absolutePath
        }
    }
    return null
}

fun BaseSimpleActivity.promptSyncVaultFromExternal(
    forcePasswordPrompt: Boolean = false,
    onSynced: (() -> Unit)? = null,
    onCancelled: (() -> Unit)? = null
) {
    if (!hasVaultCreated()) {
        toast(R.string.enable_hidden_protection_first_prompt)
        return
    }

    // Outside an active encrypted-album session the vault is unlocked only for the duration of this flow.
    // Wiping the key on every exit (success or cancel) guarantees the next visit has to prompt again.
    val wipeKeysOnExit: () -> Unit = { VaultSessionManager.lockVaultIfOutsideSession(this) }
    val onFlowCancelled: () -> Unit = {
        wipeKeysOnExit()
        onCancelled?.invoke()
    }
    val onFlowFinished: () -> Unit = {
        wipeKeysOnExit()
        onSynced?.invoke()
    }

    ensureVaultUnlocked(forcePrompt = forcePasswordPrompt, onCancelled = onFlowCancelled) {
        // The bridge is prepared on demand: no separate settings row is needed.
        CliBridgeManager.ensureActiveBridge(this)
        val internalCli = File(File(internalStoragePath, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME)

        // Removes ONLY the files listed in the consumed manifest and their
        // now-empty album folders. Unrelated files in the staging folder stay.
        val clearStagedEntries = { stagingDir: File, entries: List<String> ->
            if (stagingDir.isDirectory) {
                val base = runCatching { stagingDir.canonicalFile }.getOrDefault(stagingDir)
                entries.forEach { rel ->
                    runCatching {
                        val bin = File(stagingDir, rel)
                        val canonical = bin.canonicalFile
                        if (canonical.path.startsWith(base.path + File.separator) && bin.isFile) {
                            bin.delete()
                            val parent = bin.parentFile
                            if (parent != null && parent != stagingDir && parent.listFiles().isNullOrEmpty()) {
                                parent.delete()
                            }
                        }
                    }
                }
            }
        }

        val runImport = runImport@{ sourceDir: File ->
            val manifestEntries: List<String> = runCatching {
                val manifest = File(sourceDir, CryptoManager.PENDING_SYNC_FILE_NAME)
                if (manifest.isFile) {
                    manifest.readLines().map { it.trim().replace('\\', '/') }
                        .filter { it.isNotEmpty() && !it.startsWith("/") && !it.contains("..") }
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())

            val resolvedSource = CryptoManager.resolveSyncSourceRoot(sourceDir)
            val activeVaultRoot = CryptoManager.findVaultRoot(this)
            val sameVault = activeVaultRoot != null && runCatching {
                resolvedSource.canonicalPath == activeVaultRoot.canonicalPath
            }.getOrDefault(false)
            if (sameVault) {
                showVaultLocationNoticeDialog(R.string.vault_sync_same_folder_error, onFlowCancelled)
                return@runImport
            }

            ensureNotificationPermissionThenRun { notificationsAllowed ->
                val progressDialog = CryptoProgressDialog(
                    activity = this,
                    max = 0,
                    titleId = R.string.syncing_vault_progress,
                    enableNotification = notificationsAllowed
                )
                ensureBackgroundThread {
                    val result = CryptoManager.syncVaultFromExternalDirectory(
                        context = this,
                        sourceDir = sourceDir
                    ) { processed, total ->
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                progressDialog.updateProgress(processed, total)
                            }
                        }
                    }
                    runOnUiThread {
                        progressDialog.dismiss()
                        if (isFinishing || isDestroyed) {
                            onFlowFinished()
                            return@runOnUiThread
                        }
                        when {
                            result == null -> toast(org.fossify.commons.R.string.unknown_error_occurred)
                            result.copiedCount == 0 &&
                                result.renamedCount == 0 &&
                                result.skippedDuplicateCount == 0 &&
                                result.failedCount == 0 -> toast(R.string.vault_sync_nothing_to_sync)
                            else -> {
                                val summaryView = layoutInflater.inflate(
                                    org.fossify.commons.R.layout.dialog_message,
                                    null
                                )
                                summaryView.findViewById<TextView>(org.fossify.commons.R.id.message).text =
                                    getString(
                                        R.string.vault_sync_summary_message,
                                        result.copiedCount,
                                        result.renamedCount,
                                        result.skippedDuplicateCount,
                                        result.failedCount
                                    )
                                getAlertDialogBuilder()
                                    .setPositiveButton(org.fossify.commons.R.string.ok, null)
                                    .apply {
                                        setupDialogStuff(
                                            summaryView,
                                            this,
                                            titleId = R.string.sync_vault_from_external
                                        )
                                    }
                            }
                        }
                        // Only the app-owned CLI staging folder is cleaned up; an external
                        // disk (possibly a full vault backup) is never modified by import.
                        if (result != null && result.failedCount == 0 &&
                            result.copiedCount + result.renamedCount + result.skippedDuplicateCount > 0 &&
                            runCatching { sourceDir.canonicalPath == internalCli.canonicalPath }.getOrDefault(false)
                        ) {
                            clearStagedEntries(sourceDir, manifestEntries)
                        }
                        onFlowFinished()
                    }
                }
            }
        }

        val activeFingerprint = CliBridgeManager.currentFingerprint()
        val pendingBridges = CliBridgeManager.bridges(this).filter {
            File(it.dir, CryptoManager.PENDING_SYNC_FILE_NAME).isFile
        }

        fun importBridge(bridge: CliBridgeManager.Bridge) {
            if (activeFingerprint != null && !bridge.fingerprint.equals(activeFingerprint, ignoreCase = true)) {
                showVaultLocationNoticeDialog(R.string.export_vault_different_vault, onFlowCancelled)
                return
            }
            val marker = File(bridge.dir, CLI_WRITING_SENTINEL)
            if (marker.isFile) {
                val raw = runCatching { marker.readText(Charsets.UTF_8).trim() }.getOrDefault("")
                val startedMs = runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                val isStale = startedMs != null && System.currentTimeMillis() - startedMs > 30 * 60 * 1000L
                if (startedMs == null || !isStale) {
                    showVaultLocationNoticeDialog(R.string.vault_sync_cli_busy, onFlowCancelled)
                    return
                }
                getAlertDialogBuilder()
                    .setTitle(R.string.vault_sync_stale_marker_title)
                    .setMessage(String.format(getString(R.string.vault_sync_stale_marker_message), raw))
                    .setNegativeButton(R.string.vault_sync_stale_marker_keep, null)
                    .setPositiveButton(R.string.vault_sync_stale_marker_remove) { _, _ ->
                        marker.delete()
                        runImport(bridge.dir)
                    }
                    .show()
            } else {
                runImport(bridge.dir)
            }
        }

        val startImport = {
            // No folder picker and no chooser: the bridge marked active wins.
            val target = pendingBridges.firstOrNull { it.active } ?: pendingBridges.firstOrNull()
            when {
                target != null -> importBridge(target)

                internalCli.isDirectory && File(internalCli, CLI_WRITING_SENTINEL).exists() ->
                    showVaultLocationNoticeDialog(R.string.vault_sync_cli_busy, onFlowCancelled)

                internalCli.isDirectory &&
                    File(internalCli, CryptoManager.PENDING_SYNC_FILE_NAME).isFile ->
                    runImport(internalCli)

                else -> {
                    toast(R.string.vault_sync_nothing_to_sync)
                    onFlowCancelled()
                }
            }
        }

        startImport()
    }
}

fun BaseSimpleActivity.promptExportEncryptedVault(
    onlyVaultHeader: Boolean,
    onExported: (() -> Unit)? = null
) {
    if (!hasVaultCreated()) {
        toast(R.string.enable_hidden_protection_first_prompt)
        return
    }

    // Export is a pure file copy. No vault password is needed; the caller applies the shared
    // device-owner check (Class 3 biometric or device credential) before reaching this point.
    run {
        pickCustomVaultDirectory(
            initialPath = defaultSyncSourcePickerPath(),
            requireWritable = true,
            onPicked = { pickedDir ->
                val targetExportDir = CryptoManager.resolveExportVaultTargetDir(pickedDir)
                val activeVaultRoot = CryptoManager.findVaultRoot(this)
                if (activeVaultRoot != null) {
                    when (checkVaultExportTarget(activeVaultRoot, targetExportDir)) {
                        VaultExportHeaderCheck.SAFE -> Unit
                        VaultExportHeaderCheck.DIFFERENT_VAULT -> {
                            showVaultLocationNoticeDialog(R.string.export_vault_different_vault, {})
                            return@pickCustomVaultDirectory
                        }
                        VaultExportHeaderCheck.UNKNOWN_ENCRYPTED_CONTENT -> {
                            showVaultLocationNoticeDialog(R.string.export_vault_unknown_content, {})
                            return@pickCustomVaultDirectory
                        }
                    }
                }
                val invalidTarget = activeVaultRoot != null && runCatching {
                    val exportCanonical = targetExportDir.canonicalPath
                    val vaultCanonical = activeVaultRoot.canonicalPath
                    exportCanonical == vaultCanonical || exportCanonical.startsWith("$vaultCanonical${File.separator}")
                }.getOrDefault(false)

                if (invalidTarget) {
                    showVaultLocationNoticeDialog(R.string.vault_export_same_folder_error, {})
                    return@pickCustomVaultDirectory
                }

                ensureNotificationPermissionThenRun { notificationsAllowed ->
                    val progressDialog = CryptoProgressDialog(
                        activity = this,
                        max = 0,
                        titleId = R.string.exporting_vault_progress,
                        enableNotification = notificationsAllowed
                    )
                    ensureBackgroundThread {
                        val result = CryptoManager.exportEncryptedVault(
                            context = this,
                            targetDir = pickedDir,
                            onlyVaultHeader = onlyVaultHeader
                        ) { processed, total ->
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    progressDialog.updateProgress(processed, total)
                                }
                            }
                        }
                        runOnUiThread {
                            progressDialog.dismiss()
                            if (!isFinishing && !isDestroyed) {
                                when {
                                    result == null -> toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    result.failedFilesCount > 0 && result.exportedFilesCount == 0 -> {
                                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    }
                                    else -> toast(org.fossify.commons.R.string.exporting_successful)
                                }
                            }
                            onExported?.invoke()
                        }
                    }
                }
            },
            onCancelled = {}
        )
    }
}

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
                                    itemOp.operationId?.let { OperationJournal.clear(it) }
                                }
                            }
                            val resultPath = decryptedFile?.absolutePath ?: oldPath
                            CryptoManager.clearAppImageCaches(this)
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
            renameFile(oldPath, newPath, false) { success, useAndroid30Way ->
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
                                itemOp.operationId?.let { OperationJournal.clear(it) }
                            }
                        }
                        val resultPath = encryptedFile?.absolutePath ?: oldPath
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

data class StandardConflictItem(
    val resolutionKey: String,
    val fileDirItem: FileDirItem
)

data class TransferPairSpec(
    val sourceFile: File,
    val targetDir: File,
    val isTargetEncrypted: Boolean,
    val desiredPlainName: String? = null
)

data class TransferConflictScanResult(
    val resolutions: LinkedHashMap<String, Int>,
    val identicalPaths: HashSet<String>,
    val nonIdenticalPaths: HashSet<String>,
    val plainIndexes: HashMap<String, CryptoManager.TargetPlainIndex>,
    val encIndexes: HashMap<String, CryptoManager.TargetEncryptedIndex>,
    val identicalTargets: HashMap<String, File> = HashMap()
)

fun Activity.findIdenticalTransferTarget(
    sourceFile: File,
    targetDir: File,
    isTargetEncrypted: Boolean,
    plainIndex: CryptoManager.TargetPlainIndex? = null,
    encryptedIndex: CryptoManager.TargetEncryptedIndex? = null,
    desiredPlainName: String? = null,
    precheckedIdenticalTargets: Map<String, File> = emptyMap(),
    precheckedIdenticalPaths: Set<String> = emptySet(),
    precheckedNonIdenticalPaths: Set<String> = emptySet(),
    allowFastSampling: Boolean = false
): File? {
    if (config.conflictHandlingMode == CONFLICT_HANDLING_STANDARD_DIALOG) return null
    val srcPath = sourceFile.absolutePath
    precheckedIdenticalTargets[srcPath]?.let { return it }
    if (srcPath in precheckedNonIdenticalPaths) return null

    val useFastSampling = allowFastSampling &&
        (CryptoManager.isEncryptedFile(sourceFile.name) == isTargetEncrypted) &&
        config.fastLargeDuplicateCheck
    val check = CryptoManager.inspectTransferConflict(
        sourceFile = sourceFile,
        targetDir = targetDir,
        isTargetEncrypted = isTargetEncrypted,
        plainIndex = plainIndex,
        encryptedIndex = encryptedIndex,
        desiredPlainName = desiredPlainName,
        fastSampling = useFastSampling,
        context = this
    )
    if (check.status == CryptoManager.TransferConflictStatus.IDENTICAL_SKIP) {
        return check.existingFile
    }
    if (srcPath in precheckedIdenticalPaths) {
        return check.existingFile ?: File(targetDir, desiredPlainName ?: sourceFile.name)
    }
    return null
}

data class SmartConflictScanResult(
    val conflicts: ArrayList<SmartConflictItem>,
    val identicalPaths: HashSet<String>,
    val nonIdenticalPaths: HashSet<String>,
    val plainIndex: CryptoManager.TargetPlainIndex?,
    val encIndex: CryptoManager.TargetEncryptedIndex?
)

fun BaseSimpleActivity.resolveStandardFilenameConflicts(
    conflicts: List<StandardConflictItem>,
    callback: (LinkedHashMap<String, Int>) -> Unit
) {
    val resolutions = LinkedHashMap<String, Int>()
    if (conflicts.isEmpty()) {
        runOnUiThread {
            callback(resolutions)
        }
        return
    }

    fun askNext(index: Int) {
        if (index >= conflicts.size || isFinishing || isDestroyed) {
            callback(resolutions)
            return
        }
        val conflict = conflicts[index]
        FileConflictDialog(
            activity = this,
            fileDirItem = conflict.fileDirItem,
            showApplyToAllCheckbox = conflicts.size > 1
        ) { resolution, applyToAll ->
            resolutions[conflict.resolutionKey] = resolution
            if (applyToAll) {
                for (i in (index + 1) until conflicts.size) {
                    resolutions[conflicts[i].resolutionKey] = resolution
                }
                callback(resolutions)
            } else {
                askNext(index + 1)
            }
        }
    }

    runOnUiThread {
        askNext(0)
    }
}

fun BaseSimpleActivity.resolveCryptoTransferConflicts(
    allowFastLargeCheck: Boolean = false,
    progressDialog: CryptoProgressDialog? = null,
    buildPairs: () -> List<TransferPairSpec>,
    onResolved: (TransferConflictScanResult, CryptoProgressDialog?) -> Unit
) {
    val mode = config.conflictHandlingMode
    if (mode == CONFLICT_HANDLING_AUTO) {
        onResolved(TransferConflictScanResult(LinkedHashMap(), HashSet(), HashSet(), HashMap(), HashMap()), progressDialog)
        return
    }

    ensureBackgroundThread {
        val pairs = buildPairs()
        if (pairs.isEmpty()) {
            runOnUiThread {
                onResolved(TransferConflictScanResult(LinkedHashMap(), HashSet(), HashSet(), HashMap(), HashMap()), progressDialog)
            }
            return@ensureBackgroundThread
        }

        val plainIndexes = HashMap<String, CryptoManager.TargetPlainIndex>()
        val encIndexes = HashMap<String, CryptoManager.TargetEncryptedIndex>()

        fun getPlainIndex(dir: File): CryptoManager.TargetPlainIndex {
            return plainIndexes.getOrPut(dir.absolutePath) { CryptoManager.TargetPlainIndex(dir) }
        }

        fun getEncIndex(dir: File): CryptoManager.TargetEncryptedIndex {
            return encIndexes.getOrPut(dir.absolutePath) { CryptoManager.TargetEncryptedIndex(dir) }
        }

        if (mode == CONFLICT_HANDLING_STANDARD_DIALOG) {
            val standardConflicts = ArrayList<StandardConflictItem>()
            for (pair in pairs) {
                val pIdx = if (!pair.isTargetEncrypted) getPlainIndex(pair.targetDir) else null
                val eIdx = if (pair.isTargetEncrypted) getEncIndex(pair.targetDir) else null
                val conflictName = CryptoManager.inspectStandardFilenameConflict(
                    sourceFile = pair.sourceFile,
                    targetDir = pair.targetDir,
                    isTargetEncrypted = pair.isTargetEncrypted,
                    plainIndex = pIdx,
                    encryptedIndex = eIdx,
                    desiredPlainName = pair.desiredPlainName
                )
                if (conflictName != null) {
                    standardConflicts.add(
                        StandardConflictItem(
                            resolutionKey = pair.sourceFile.absolutePath,
                            fileDirItem = FileDirItem(
                                path = pair.sourceFile.absolutePath,
                                name = conflictName,
                                isDirectory = false,
                                modified = pair.sourceFile.lastModified(),
                                size = pair.sourceFile.length()
                            )
                        )
                    )
                }
            }
            if (standardConflicts.isNotEmpty()) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    resolveStandardFilenameConflicts(standardConflicts) { resolutions ->
                        onResolved(TransferConflictScanResult(resolutions, HashSet(), HashSet(), plainIndexes, encIndexes), null)
                    }
                }
            } else {
                runOnUiThread {
                    onResolved(TransferConflictScanResult(LinkedHashMap(), HashSet(), HashSet(), plainIndexes, encIndexes), progressDialog)
                }
            }
        } else {
            val smartConflicts = ArrayList<SmartConflictItem>()
            val identicalPaths = HashSet<String>()
            val nonIdenticalPaths = HashSet<String>()
            val identicalTargets = HashMap<String, File>()
            for (pair in pairs) {
                val pIdx = if (!pair.isTargetEncrypted) getPlainIndex(pair.targetDir) else null
                val eIdx = if (pair.isTargetEncrypted) getEncIndex(pair.targetDir) else null
                val useFastSampling = allowFastLargeCheck &&
                    (CryptoManager.isEncryptedFile(pair.sourceFile.name) == pair.isTargetEncrypted) &&
                    config.fastLargeDuplicateCheck
                val check = CryptoManager.inspectTransferConflict(
                    sourceFile = pair.sourceFile,
                    targetDir = pair.targetDir,
                    isTargetEncrypted = pair.isTargetEncrypted,
                    plainIndex = pIdx,
                    encryptedIndex = eIdx,
                    desiredPlainName = pair.desiredPlainName,
                    fastSampling = useFastSampling,
                    context = this
                )
                when (check.status) {
                    CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                        identicalPaths.add(pair.sourceFile.absolutePath)
                        check.existingFile?.let { identicalTargets[pair.sourceFile.absolutePath] = it }
                    }
                    CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                        nonIdenticalPaths.add(pair.sourceFile.absolutePath)
                        val displayName = check.displayName ?: pair.sourceFile.name
                        val existingPath = check.existingFile?.absolutePath ?: File(pair.targetDir, displayName).absolutePath
                        smartConflicts.add(
                            SmartConflictItem(
                                resolutionKey = pair.sourceFile.absolutePath,
                                displayName = displayName,
                                sourcePath = pair.sourceFile.absolutePath,
                                existingPath = existingPath,
                                useFastLargeDuplicateCheck = useFastSampling
                            )
                        )
                    }
                    CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
                }
            }
            if (smartConflicts.isNotEmpty()) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    resolveSmartContentConflicts(smartConflicts) { resolutions ->
                        onResolved(TransferConflictScanResult(resolutions, identicalPaths, nonIdenticalPaths, plainIndexes, encIndexes, identicalTargets), null)
                    }
                }
            } else {
                runOnUiThread {
                    onResolved(TransferConflictScanResult(LinkedHashMap(), identicalPaths, nonIdenticalPaths, plainIndexes, encIndexes, identicalTargets), progressDialog)
                }
            }
        }
    }
}

fun BaseSimpleActivity.scanSmartTransferConflicts(
    sourceFiles: List<File>,
    targetDir: File,
    isTargetEncrypted: Boolean,
    useFastSampling: Boolean = false,
    resolveDesiredName: ((File) -> String?)? = null
): SmartConflictScanResult {
    val plainIndex = if (!isTargetEncrypted) CryptoManager.TargetPlainIndex(targetDir) else null
    val encIndex = if (isTargetEncrypted) CryptoManager.TargetEncryptedIndex(targetDir) else null
    val conflicts = ArrayList<SmartConflictItem>()
    val identicalPaths = HashSet<String>()
    val nonIdenticalPaths = HashSet<String>()

    for (srcFile in sourceFiles) {
        val check = CryptoManager.inspectTransferConflict(
            sourceFile = srcFile,
            targetDir = targetDir,
            isTargetEncrypted = isTargetEncrypted,
            plainIndex = plainIndex,
            encryptedIndex = encIndex,
            desiredPlainName = resolveDesiredName?.invoke(srcFile),
            fastSampling = useFastSampling,
            context = this
        )
        when (check.status) {
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                identicalPaths.add(srcFile.absolutePath)
            }
            CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                nonIdenticalPaths.add(srcFile.absolutePath)
                val displayName = check.displayName ?: srcFile.name
                val existingPath = check.existingFile?.absolutePath ?: File(targetDir, displayName).absolutePath
                conflicts.add(
                    SmartConflictItem(
                        resolutionKey = srcFile.absolutePath,
                        displayName = displayName,
                        sourcePath = srcFile.absolutePath,
                        existingPath = existingPath,
                        useFastLargeDuplicateCheck = useFastSampling
                    )
                )
            }
            CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
        }
    }
    return SmartConflictScanResult(conflicts, identicalPaths, nonIdenticalPaths, plainIndex, encIndex)
}

fun BaseSimpleActivity.resolveSmartContentConflicts(
    conflicts: List<SmartConflictItem>,
    callback: (LinkedHashMap<String, Int>) -> Unit
) {
    val resolutions = LinkedHashMap<String, Int>()
    if (config.conflictHandlingMode != CONFLICT_HANDLING_SMART_DIALOG || conflicts.isEmpty()) {
        runOnUiThread {
            callback(resolutions)
        }
        return
    }

    fun askNext(index: Int) {
        if (index >= conflicts.size || isFinishing || isDestroyed) {
            callback(resolutions)
            return
        }
        val conflict = conflicts[index]
        SmartFileConflictDialog(
            activity = this,
            conflict = conflict,
            currentIndex = index,
            totalCount = conflicts.size
        ) { resolution, applyToAll ->
            resolutions[conflict.resolutionKey] = resolution
            if (applyToAll) {
                for (i in (index + 1) until conflicts.size) {
                    resolutions[conflicts[i].resolutionKey] = resolution
                }
                callback(resolutions)
            } else {
                askNext(index + 1)
            }
        }
    }

    runOnUiThread {
        askNext(0)
    }
}

fun BaseSimpleActivity.cleanupEmptyPlaintextAlbumIfNeeded(
    folder: File,
    forceIfEmpty: Boolean = false
): Boolean {
    if (!folder.exists() || !folder.isDirectory) return false
    if (!forceIfEmpty && !config.deleteEmptyFolders) return false
    val cleanPath = folder.absolutePath.trimEnd('/')
    if (cleanPath.isEmpty()) return false
    if (cleanPath.equals(internalStoragePath.trimEnd('/'), ignoreCase = true)) return false
    if (cleanPath.isDownloadsFolder()) return false
    val cipherRoot = CryptoManager.getCipherRootDirectory().absolutePath.trimEnd('/')
    if (cleanPath.equals(cipherRoot, ignoreCase = true)) return false
    if (CryptoManager.isEncryptedFolder(folder)) return false

    val children = folder.listFiles() ?: return false
    if (children.any { it.isDirectory || it.name != NOMEDIA }) {
        return false
    }

    File(folder, NOMEDIA).takeIf { it.exists() }?.let { noMedia ->
        runCatching { noMedia.delete() }
    }
    val deleted = runCatching { folder.delete() }.getOrDefault(false)
    if (deleted || !folder.exists()) {
        runCatching { directoryDB.deleteDirPath(folder.absolutePath) }
        return true
    }
    return false
}

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

                            batchOp.operationId?.let { OperationJournal.clear(it) }
                            if (anyFavoriteUpdated) {
                                runCatching { ReferenceBackupManager.saveFavorites(this) }
                            }
                            sourceDirs.forEach { dir ->
                                CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(this, dir)
                            }

                            CryptoManager.clearAppImageCaches(this)
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
        confirmAndDecryptFolders(folderPaths, targetDir = null, callback = callback)
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
        val source = if (folderPaths.size == 1) folderPaths.first() else ""
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
            confirmAndDecryptFolders(folderPaths, targetDir = File(destination), callback = callback)
        }
    }
}

private fun BaseSimpleActivity.confirmAndDecryptFolders(
    folderPaths: List<String>,
    targetDir: File?,
    callback: (destinationPath: String) -> Unit
) {
    EncryptConfirmationDialog(
        activity = this,
        message = getString(R.string.confirm_decrypt_hidden_file),
        infoTitleResId = R.string.decrypt_info_title,
        infoMessageResId = R.string.decrypt_info_message,
        isDecrypt = true
    ) {
        val conflicts = folderPaths.mapNotNull {
            CryptoManager.previewDecryptAlbumConflict(this, it, targetDir)
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
                            val mergeTarget = CryptoManager.findMergeTargetForDecryptFolder(this, folderPath, targetDir)
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
                        var lastDestination = targetDir?.absolutePath ?: ""
                        val deferredSkippedFiles = java.util.Collections.synchronizedList(ArrayList<File>())
                        val deferredIdenticalTargets = java.util.Collections.synchronizedMap(LinkedHashMap<String, File>())

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
                                    targetFolderOverride = targetDir,
                                    options = folderOptions
                                ) { current, _ ->
                                    val diff = current - folderLastCurrent
                                    if (diff > 0) {
                                        globalProcessed += diff
                                        folderLastCurrent = current
                                        runOnUiThread {
                                            progressDialog.updateProgress(globalProcessed, totalFilesCount)
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
}

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
        val owner = "virtual-album-add"
        val acquired = ForegroundOperationRegistry.acquire(this, owner)
        if (acquired) {
            ForegroundOperationRegistry.updateProgress(this, 0, validItems.size, null)
        }
        VirtualAlbumConflictHelper.add(this, albumId, validItems, isHiddenSource) { added ->
            if (acquired) {
                ForegroundOperationRegistry.updateProgress(this, validItems.size, validItems.size, null)
                ForegroundOperationRegistry.release(this, owner)
            }
            val resolvedName = albumName.ifEmpty { getVirtualAlbumName(destination) ?: "" }
            val msgRes = if (added > 0) R.string.added_to_virtual_album else R.string.already_in_virtual_album
            toast(getString(msgRes, resolvedName))
            callback(destination)
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

                val destDir = File(destination)
                val initialProgressDialog = CryptoProgressDialog(
                    activity = this,
                    max = cryptoItems.size,
                    titleId = progressTitleId,
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
                        enableNotification = false
                    )
                    ensureNotificationPermissionThenRun { notificationsAllowed ->
                        progressDialog.ensureNotificationStarted(notificationsAllowed)
                        ensureBackgroundThread {
                            val acquired = ForegroundOperationRegistry.acquire(this, "crypto-copy-move")
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
                                                throw java.io.IOException("plain copy verification failed")
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

                            batchOperation.operationId?.let { OperationJournal.clear(it) }
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
                                ForegroundOperationRegistry.release(this, "crypto-copy-move")
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
                    fileDirItems.firstOrNull()?.getParentPath() ?: source
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

fun BaseSimpleActivity.tryDeleteFileDirItem(
    fileDirItem: FileDirItem, allowDeleteFolder: Boolean = false, deleteFromDatabase: Boolean,
    callback: ((wasSuccess: Boolean) -> Unit)? = null
) {
    deleteFile(fileDirItem, allowDeleteFolder, isDeletingMultipleFiles = false) {
        if (deleteFromDatabase) {
            ensureBackgroundThread {
                deleteDBPath(fileDirItem.path)
                runOnUiThread {
                    callback?.invoke(it)
                }
            }
        } else {
            callback?.invoke(it)
        }
    }
}

fun BaseSimpleActivity.movePathsInRecycleBin(paths: ArrayList<String>, callback: ((wasSuccess: Boolean) -> Unit)?) {
    ensureBackgroundThread {
        ForegroundOperationRegistry.withOperation(this, "recycle-move") {
            var pathsCnt = paths.size
            val OTGPath = config.OTGPath

            for (source in paths) {
                if (ForegroundOperationRegistry.isCancelled) {
                    return@withOperation
                }
                if (OTGPath.isNotEmpty() && source.startsWith(OTGPath)) {
                    var inputStream: InputStream? = null
                    var out: OutputStream? = null
                    try {
                        val destination = "$recycleBinPath/$source"
                        val fileDocument = getSomeDocumentFile(source)
                        inputStream = applicationContext.contentResolver.openInputStream(fileDocument?.uri!!)
                        out = getFileOutputStreamSync(destination, source.getMimeType())

                        var copiedSize = 0L
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = inputStream!!.read(buffer)
                        while (bytes >= 0) {
                            out!!.write(buffer, 0, bytes)
                            copiedSize += bytes
                            bytes = inputStream.read(buffer)
                        }

                        out?.flush()

                        if (fileDocument.getItemSize(true) == copiedSize && getDoesFilePathExist(destination)) {
                            mediaDB.updateDeleted("$RECYCLE_BIN$source", System.currentTimeMillis(), source)
                            pathsCnt--
                        }
                    } catch (e: Exception) {
                        showErrorToast(e)
                        return@withOperation
                    } finally {
                        inputStream?.close()
                        out?.close()
                    }
                } else {
                    val file = File(source)
                    val internalFile = File(recycleBinPath, source)
                    val lastModified = file.lastModified()
                    try {
                        if (file.copyRecursively(internalFile, true)) {
                            mediaDB.updateDeleted("$RECYCLE_BIN$source", System.currentTimeMillis(), source)
                            pathsCnt--

                            if (config.keepLastModified && lastModified != 0L) {
                                internalFile.setLastModified(lastModified)
                            }
                        }
                    } catch (e: Exception) {
                        showErrorToast(e)
                        return@withOperation
                    }
                }
            }
            callback?.invoke(pathsCnt == 0)
        }
    }
}

fun BaseSimpleActivity.restoreRecycleBinPath(path: String, callback: () -> Unit) {
    restoreRecycleBinPaths(arrayListOf(path), callback)
}

fun BaseSimpleActivity.restoreRecycleBinPaths(paths: ArrayList<String>, callback: () -> Unit) {
    ensureBackgroundThread {
        ForegroundOperationRegistry.withOperation(this, "recycle-restore") {
            val newPaths = ArrayList<String>()
            val restoredPairs = ArrayList<Pair<String, String>>()
            var shownRestoringToPictures = false
            for (source in paths) {
                if (ForegroundOperationRegistry.isCancelled) {
                    break
                }
                val origPath = source.removePrefix(recycleBinPath)
                var destination = origPath

                val destinationParent = destination.getParentPath()
                if (isRestrictedWithSAFSdk30(destinationParent) && !isInDownloadDir(destinationParent)) {
                    // if the file is not writeable on SDK30+, change it to Pictures
                    val picturesDirectory = getPicturesDirectoryPath(destination)
                    destination = File(picturesDirectory, destination.getFilenameFromPath()).path
                    if (!shownRestoringToPictures) {
                        toast(getString(R.string.restore_to_path, humanizePath(picturesDirectory)))
                        shownRestoringToPictures = true
                    }
                }

                val lastModified = File(source).lastModified()

                val isShowingSAF = handleSAFDialog(destination) {}
                if (isShowingSAF) {
                    return@withOperation
                }

                val isShowingSAFSdk30 = handleSAFDialogSdk30(destination) {}
                if (isShowingSAFSdk30) {
                    return@withOperation
                }

                if (getDoesFilePathExist(destination)) {
                    val newFile = getAlternativeFile(File(destination))
                    destination = newFile.path
                }

                var inputStream: InputStream? = null
                var out: OutputStream? = null
                try {
                    out = getFileOutputStreamSync(destination, source.getMimeType())
                    inputStream = getFileInputStreamSync(source)

                    var copiedSize = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytes = inputStream!!.read(buffer)
                    while (bytes >= 0) {
                        out!!.write(buffer, 0, bytes)
                        copiedSize += bytes
                        bytes = inputStream.read(buffer)
                    }

                    out?.flush()

                    if (File(source).length() == copiedSize) {
                        mediaDB.updateDeleted(destination.removePrefix(recycleBinPath), 0, "$RECYCLE_BIN${source.removePrefix(recycleBinPath)}")
                        restoredPairs.add(origPath to destination)
                    }
                    newPaths.add(destination)

                    if (config.keepLastModified && lastModified != 0L) {
                        File(destination).setLastModified(lastModified)
                    }
                } catch (e: Exception) {
                    showErrorToast(e)
                } finally {
                    inputStream?.close()
                    out?.close()
                }
            }

            if (restoredPairs.isNotEmpty()) {
                restoreReferencesAfterRecycleBinRestore(restoredPairs)
            }

            runOnUiThread {
                callback()
            }

            rescanPaths(newPaths) {
                fixDateTaken(newPaths, false)
            }
        }
    }
}

fun BaseSimpleActivity.emptyTheRecycleBin(callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
        ForegroundOperationRegistry.withOperation(this, "recycle-empty") {
            try {
                val deletedOrigPaths = try {
                    mediaDB.getDeletedMedia().map { it.path.removePrefix(RECYCLE_BIN) }
                } catch (_: Exception) {
                    emptyList()
                }
                recycleBin.deleteRecursively()
                mediaDB.clearRecycleBin()
                directoryDB.deleteRecycleBin()
                if (deletedOrigPaths.isNotEmpty()) {
                    deleteDBPaths(deletedOrigPaths)
                }
                toast(org.fossify.commons.R.string.recycle_bin_emptied)
                callback?.invoke()
            } catch (e: Exception) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        }
    }
}

fun BaseSimpleActivity.emptyAndDisableTheRecycleBin(callback: () -> Unit) {
    ensureBackgroundThread {
        emptyTheRecycleBin {
            config.useRecycleBin = false
            callback()
        }
    }
}

/** Shown right after vault creation / password change, before any biometric offer. */
fun BaseSimpleActivity.showVaultPasswordSavedDialog(
    isInitialCreation: Boolean = false,
    onDone: () -> Unit,
) {
    // Tapping outside does not dismiss it; Back does. Both Back and the confirmation button
    // continue to the one-time biometric offer (3b), followed by the 3c tutorial when creating
    // a vault. Backing out of 3a used to skip 3b entirely, which also skipped the callbacks that
    // refresh the vault settings screen — the "create vault" row stayed visible until the
    // settings category was reopened. Declining/backing out of 3b still leaves biometrics off
    // (the offer defaults it to off) and then finishes setup through 3c / onDone.
    val finishSetup: () -> Unit = {
        if (isInitialCreation && !isFinishing && !isDestroyed) {
            showVaultPullTutorialDialog { onDone() }
        } else {
            onDone()
        }
    }

    val proceedToBiometricOffer: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            config.vaultBiometricOfferShown = false
            VaultBiometricOfferDialog.showIfNeeded(this) {
                finishSetup()
            }
        } else {
            onDone()
        }
    }

    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.vault_password_saved_title),
        message = getString(R.string.vault_password_saved_message) +
            "\n\n" + getString(R.string.vault_password_reminder_setup_note),
        positiveText = getString(R.string.vault_password_saved_confirm),
        cancelable = true,
        onPositive = proceedToBiometricOffer,
        onCancel = proceedToBiometricOffer,
    )
}

/**
 * Screen 3c (Template A): Live mini gallery & top-scroll pull tutorial shown at the end of the
 * vault creation flow so the user discovers how to open the hidden encrypted vault.
 */
fun BaseSimpleActivity.showVaultPullTutorialDialog(onDone: () -> Unit = {}) {
    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.vault_pull_tutorial_title),
        message = android.text.Html.fromHtml(
            getString(R.string.vault_pull_tutorial_message),
            android.text.Html.FROM_HTML_MODE_LEGACY,
        ),
        noteText = getString(R.string.vault_pull_tutorial_note),
        positiveText = getString(R.string.vault_pull_tutorial_confirm),
        // The final tutorial is the last mandatory step of vault setup: Back must not skip it.
        // Completion goes through the positive button only.
        cancelable = false,
        showPullTutorial = true,
        filledPositiveButton = true,
        alignSingleActionEnd = true,
        onPositive = onDone,
    )
}

/**
 * Theme-aware card shown before the system biometric enrollment prompt (HTML screen 3b).
 * The system prompt itself is drawn by Android and cannot be restyled, so the full explanation
 * lives here and the prompt only carries its own title/description.
 */
fun BaseSimpleActivity.showBiometricPasswordNoteDialog(
    title: String,
    message: String,
    note: String,
    onContinue: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    VaultNoticeDialog(
        activity = this,
        title = title,
        message = message,
        noteText = note,
        positiveText = getString(R.string.vault_biometric_setup_continue),
        negativeText = getString(org.fossify.commons.R.string.cancel),
        showPositiveCheckIcon = true,
        cancelable = true,
        showBiometricGlyph = true,
        onPositive = onContinue,
        onNegative = onCancel ?: {},
        onCancel = onCancel ?: {},
    )
}

fun BaseSimpleActivity.showRecycleBinEmptyingDialog(callback: () -> Unit) {
    ConfirmationDialog(
        this,
        "",
        org.fossify.commons.R.string.empty_recycle_bin_confirmation,
        org.fossify.commons.R.string.yes,
        org.fossify.commons.R.string.no
    ) {
        callback()
    }
}

fun BaseSimpleActivity.showRestoreConfirmationDialog(count: Int, callback: () -> Unit) {
    ConfirmationDialog(
        activity = this,
        message = resources.getQuantityString(R.plurals.restore_confirmation, count, count),
        positive = org.fossify.commons.R.string.yes,
        negative = org.fossify.commons.R.string.no
    ) {
        callback()
    }
}

fun BaseSimpleActivity.updateFavoritePaths(fileDirItems: ArrayList<FileDirItem>, destination: String) {
    ensureBackgroundThread {
        fileDirItems.forEach {
            val newPath = "$destination/${it.name}"
            updateDBMediaPath(it.path, newPath)
            // A moved folder takes its content with it, so every reference below it has to follow.
            if (it.isDirectory) {
                updateReferencesForFolderRename(it.path, newPath)
            }
        }
        // Files that were moved without the app knowing about it are repaired by their identity.
        repairReferencesAfterExternalChange()
    }
}

fun Activity.hasNavBar(): Boolean {
    val display = windowManager.defaultDisplay

    val realDisplayMetrics = DisplayMetrics()
    display.getRealMetrics(realDisplayMetrics)

    val displayMetrics = DisplayMetrics()
    display.getMetrics(displayMetrics)

    return (realDisplayMetrics.widthPixels - displayMetrics.widthPixels > 0) || (realDisplayMetrics.heightPixels - displayMetrics.heightPixels > 0)
}

fun AppCompatActivity.fixDateTaken(
    paths: ArrayList<String>,
    showToasts: Boolean,
    hasRescanned: Boolean = false,
    callback: (() -> Unit)? = null
) {
    val BATCH_SIZE = 50
    if (showToasts && !hasRescanned) {
        toast(R.string.fixing)
    }

    val pathsToRescan = ArrayList<String>()
    try {
        var didUpdateFile = false
        val operations = ArrayList<ContentProviderOperation>()

        ensureBackgroundThread {
            val dateTakens = ArrayList<DateTaken>()

            for (path in paths) {
                try {
                    val dateTime: String = ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: ExifInterface(path).getAttribute(ExifInterface.TAG_DATETIME) ?: continue

                    // some formats contain a "T" in the middle, some don't
                    // sample dates: 2015-07-26T14:55:23, 2018:09:05 15:09:05
                    val t = if (dateTime.substring(10, 11) == "T") "\'T\'" else " "
                    val separator = dateTime.substring(4, 5)
                    val format = "yyyy${separator}MM${separator}dd${t}kk:mm:ss"
                    val formatter = SimpleDateFormat(format, Locale.getDefault())
                    val timestamp = formatter.parse(dateTime).time

                    val uri = getFileUri(path)
                    ContentProviderOperation.newUpdate(uri).apply {
                        val selection = "${Images.Media.DATA} = ?"
                        val selectionArgs = arrayOf(path)
                        withSelection(selection, selectionArgs)
                        withValue(Images.Media.DATE_TAKEN, timestamp)
                        operations.add(build())
                    }

                    if (operations.size % BATCH_SIZE == 0) {
                        contentResolver.applyBatch(MediaStore.AUTHORITY, operations)
                        operations.clear()
                    }

                    mediaDB.updateFavoriteDateTaken(path, timestamp)
                    didUpdateFile = true

                    val dateTaken = DateTaken(
                        null,
                        path,
                        path.getFilenameFromPath(),
                        path.getParentPath(),
                        timestamp,
                        (System.currentTimeMillis() / 1000).toInt(),
                        File(path).lastModified()
                    )
                    dateTakens.add(dateTaken)
                    if (!hasRescanned && getFileDateTaken(path) == 0L) {
                        pathsToRescan.add(path)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            if (!didUpdateFile) {
                if (showToasts) {
                    toast(R.string.no_date_takens_found)
                }

                runOnUiThread {
                    callback?.invoke()
                }
                return@ensureBackgroundThread
            }

            val resultSize = contentResolver.applyBatch(MediaStore.AUTHORITY, operations).size
            if (resultSize == 0) {
                didUpdateFile = false
            }

            if (hasRescanned || pathsToRescan.isEmpty()) {
                if (dateTakens.isNotEmpty()) {
                    dateTakensDB.insertAll(dateTakens)
                }

                runOnUiThread {
                    if (showToasts) {
                        toast(if (didUpdateFile) R.string.dates_fixed_successfully else org.fossify.commons.R.string.unknown_error_occurred)
                    }

                    callback?.invoke()
                }
            } else {
                rescanPaths(pathsToRescan) {
                    fixDateTaken(paths, showToasts, true, callback)
                }
            }
        }
    } catch (e: Exception) {
        if (showToasts) {
            showErrorToast(e)
        }
    }
}

fun BaseSimpleActivity.saveRotatedImageToFile(oldPath: String, newPath: String, degrees: Int, showToasts: Boolean, callback: () -> Unit) {
    var newDegrees = degrees
    if (newDegrees < 0) {
        newDegrees += 360
    }

    if (oldPath == newPath && oldPath.isJpg()) {
        if (tryRotateByExif(oldPath, newDegrees, showToasts, callback)) {
            return
        }
    }

    val tmpPath = "$recycleBinPath/.tmp_${newPath.getFilenameFromPath()}"
    val tmpFileDirItem = FileDirItem(tmpPath, tmpPath.getFilenameFromPath())
    try {
        getFileOutputStream(tmpFileDirItem) {
            if (it == null) {
                if (showToasts) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                return@getFileOutputStream
            }

            val oldLastModified = File(oldPath).lastModified()
            if (oldPath.isJpg()) {
                copyFile(oldPath, tmpPath)
                saveExifRotation(ExifInterface(tmpPath), newDegrees)
            } else {
                val inputstream = getFileInputStreamSync(oldPath)
                val bitmap = BitmapFactory.decodeStream(inputstream)
                saveFile(tmpPath, bitmap, it as FileOutputStream, newDegrees)
            }

            copyFile(tmpPath, newPath)
            rescanPaths(arrayListOf(newPath))
            fileRotatedSuccessfully(newPath, oldLastModified)

            it.flush()
            it.close()
            callback.invoke()
        }
    } catch (e: OutOfMemoryError) {
        if (showToasts) {
            toast(org.fossify.commons.R.string.out_of_memory_error)
        }
    } catch (e: Exception) {
        if (showToasts) {
            showErrorToast(e)
        }
    } finally {
        val tmpFile = File(tmpPath)
        if (tmpFile.exists() && (CryptoManager.isVaultUnlocked || CryptoManager.isEncryptedFile(oldPath))) {
            CryptoManager.secureWipeAndDeleteFile(tmpFile, this)
        }
        tryDeleteFileDirItem(tmpFileDirItem, false, true)
    }
}

fun Activity.tryRotateByExif(path: String, degrees: Int, showToasts: Boolean, callback: () -> Unit): Boolean {
    return try {
        val file = File(path)
        val oldLastModified = file.lastModified()
        if (saveImageRotation(path, degrees)) {
            fileRotatedSuccessfully(path, oldLastModified)
            callback.invoke()
            if (showToasts) {
                toast(org.fossify.commons.R.string.file_saved)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        // lets not show IOExceptions, rotating is saved just fine even with them
        if (showToasts && e !is IOException) {
            showErrorToast(e)
        }
        false
    }
}

fun Activity.fileRotatedSuccessfully(path: String, lastModified: Long) {
    if (config.keepLastModified && lastModified != 0L) {
        File(path).setLastModified(lastModified)
        updateLastModified(path, lastModified)
    }

    // we cannot refresh a specific image in Glide Cache, so just clear it all
    val glide = Glide.get(applicationContext)
    glide.clearDiskCache()
    runOnUiThread {
        glide.clearMemory()
    }
}

fun BaseSimpleActivity.copyFile(source: String, destination: String) {
    var inputStream: InputStream? = null
    var out: OutputStream? = null
    try {
        out = getFileOutputStreamSync(destination, source.getMimeType())
        inputStream = getFileInputStreamSync(source)
        inputStream!!.copyTo(out!!)
    } catch (e: Exception) {
        showErrorToast(e)
    } finally {
        inputStream?.close()
        out?.close()
    }
}

fun BaseSimpleActivity.ensureWriteAccess(path: String, callback: () -> Unit) {
    when {
        isRestrictedSAFOnlyRoot(path) -> {
            handleAndroidSAFDialog(path) {
                if (!it) {
                    return@handleAndroidSAFDialog
                }
                callback.invoke()
            }
        }

        needsStupidWritePermissions(path) -> {
            handleSAFDialog(path) {
                if (!it) {
                    return@handleSAFDialog
                }
                callback()
            }
        }

        isAccessibleWithSAFSdk30(path) -> {
            handleSAFDialogSdk30(path) {
                if (!it) {
                    return@handleSAFDialogSdk30
                }
                callback()
            }
        }

        else -> {
            callback()
        }
    }
}

fun BaseSimpleActivity.launchResizeMultipleImagesDialog(paths: List<String>, callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
        val imagePaths = mutableListOf<String>()
        val imageSizes = mutableListOf<Point>()
        for (path in paths) {
            val size = path.getImageResolution(this)
            if (size != null) {
                imagePaths.add(path)
                imageSizes.add(size)
            }
        }

        runOnUiThread {
            ResizeMultipleImagesDialog(this, imagePaths, imageSizes) {
                callback?.invoke()
            }
        }
    }
}

fun BaseSimpleActivity.launchResizeImageDialog(path: String, callback: (() -> Unit)? = null) {
    val originalSize = path.getImageResolution(this) ?: return
    ResizeWithPathDialog(this, originalSize, path) { newSize, newPath ->
        ensureBackgroundThread {
            val file = File(newPath)
            val pathLastModifiedMap = mapOf(file.absolutePath to file.lastModified())
            try {
                resizeImage(path, newPath, newSize) { success ->
                    if (success) {
                        toast(org.fossify.commons.R.string.file_saved)

                        val paths = arrayListOf(file.absolutePath)
                        rescanPathsAndUpdateLastModified(paths, pathLastModifiedMap) {
                            runOnUiThread {
                                callback?.invoke()
                            }
                        }
                    } else {
                        toast(R.string.image_editing_failed)
                    }
                }
            } catch (e: OutOfMemoryError) {
                toast(org.fossify.commons.R.string.out_of_memory_error)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun BaseSimpleActivity.resizeImage(oldPath: String, newPath: String, size: Point, callback: (success: Boolean) -> Unit) {
    var oldExif: ExifInterface?
    val inputStream = contentResolver.openInputStream(Uri.fromFile(File(oldPath)))
    oldExif = ExifInterface(inputStream!!)

    val newBitmap = Glide.with(applicationContext)
        .asBitmap()
        .load(oldPath)
        .apply(
            com.bumptech.glide.request.RequestOptions()
                .applyEncryptedCachePolicy(CryptoManager.isEncryptedFile(oldPath) || CryptoManager.isVaultUnlocked)
        )
        .submit(size.x, size.y)
        .get()

    val newFile = File(newPath)
    val newFileDirItem = FileDirItem(newPath, newPath.getFilenameFromPath())
    getFileOutputStream(newFileDirItem, true) { out ->
        if (out != null) {
            out.use {
                try {
                    newBitmap.compress(newFile.absolutePath.getCompressionFormat(), 90, out)

                    val newExif = ExifInterface(newFile.absolutePath)
                    oldExif.copyNonDimensionAttributesTo(newExif)
                } catch (ignored: Exception) {
                }

                callback(true)
            }
        } else {
            callback(false)
        }
    }
}

fun BaseSimpleActivity.rescanPathsAndUpdateLastModified(paths: ArrayList<String>, pathLastModifiedMap: Map<String, Long>, callback: () -> Unit) {
    fixDateTaken(paths, false)
    for (path in paths) {
        val file = File(path)
        val lastModified = pathLastModifiedMap[path]
        if (config.keepLastModified && lastModified != null && lastModified != 0L) {
            File(file.absolutePath).setLastModified(lastModified)
            updateLastModified(file.absolutePath, lastModified)
        }
    }
    rescanPaths(paths, callback)
}

fun saveFile(path: String, bitmap: Bitmap, out: FileOutputStream, degrees: Int) {
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    val bmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    bmp.compress(path.getCompressionFormat(), 90, out)
}

fun Activity.getShortcutImage(tmb: String, drawable: Drawable, callback: () -> Unit) {
    ensureBackgroundThread {
        val options = RequestOptions()
            .format(DecodeFormat.PREFER_ARGB_8888)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .fitCenter()

        val size = resources.getDimension(org.fossify.commons.R.dimen.shortcut_size).toInt()
        val builder = Glide.with(this)
            .asDrawable()
            .load(tmb)
            .apply(options)
            .centerCrop()
            .into(size, size)

        try {
            (drawable as LayerDrawable).setDrawableByLayerId(R.id.shortcut_image, builder.get())
        } catch (e: Exception) {
        }

        runOnUiThread {
            callback()
        }
    }
}

fun Activity.showFileOnMap(path: String) {
    val exif = try {
        if (path.startsWith("content://")) {
            ExifInterface(contentResolver.openInputStream(path.toUri())!!)
        } else {
            ExifInterface(path)
        }
    } catch (e: Exception) {
        showErrorToast(e)
        return
    }

    val latLon = FloatArray(2)
    if (exif.getLatLong(latLon)) {
        showLocationOnMap("${latLon[0]}, ${latLon[1]}")
    } else {
        toast(R.string.unknown_location)
    }
}

fun Activity.handleExcludedFolderPasswordProtection(callback: () -> Unit) {
    if (config.isExcludedPasswordProtectionOn) {
        AppSecurityDialog(this, config.excludedPasswordHash, config.excludedProtectionType) { _, _, success ->
            if (success) {
                callback()
            }
        }
    } else {
        callback()
    }
}

fun Activity.openRecycleBin() {
    Intent(this, MediaActivity::class.java).apply {
        putExtra(DIRECTORY, RECYCLE_BIN)
        startActivity(this)
    }
}

fun BaseSimpleActivity.writeBitmapToCache(
    source: Uri,
    bitmap: Bitmap,
    callback: (path: String?) -> Unit
) {
    val bytes = ByteArrayOutputStream()
    bitmap.compress(CompressFormat.PNG, 0, bytes)

    val folder = File(cacheDir, TEMP_FOLDER_NAME)
    if (!folder.exists()) {
        if (!folder.mkdir()) {
            callback(null)
            return
        }
    }

    val filename = applicationContext.getFilenameFromContentUri(source)
        ?: "tmp-${System.currentTimeMillis()}.jpg"
    val newPath = "$folder/$filename"
    val fileDirItem = FileDirItem(newPath, filename)
    getFileOutputStream(fileDirItem, true) {
        if (it != null) {
            try {
                it.write(bytes.toByteArray())
                callback(newPath)
            } catch (_: Exception) {
                callback(null)
            } finally {
                it.close()
            }
        } else {
            callback(null)
        }
    }
}

fun BaseSimpleActivity.ensureWritablePath(
    targetPath: String,
    confirmOverwrite: Boolean = true,
    onCancel: (() -> Unit)? = null,
    callback: (String) -> Unit,
) {
    fun proceedAfterGrants() {
        handleSAFDialogSdk30(targetPath) { granted ->
            if (!granted) {
                onCancel?.invoke()
                return@handleSAFDialogSdk30
            }
            callback(targetPath)
        }
    }

    fun requestGrantsThenProceed() {
        if (isRPlus() && !isExternalStorageManager()) {
            val fileDirItem = arrayListOf(File(targetPath).toFileDirItem(this))
            val fileUris = getFileUrisFromFileDirItems(fileDirItem)
            updateSDK30Uris(fileUris) { success ->
                if (success) proceedAfterGrants() else onCancel?.invoke()
            }
        } else {
            proceedAfterGrants()
        }
    }

    if (confirmOverwrite && getDoesFilePathExist(targetPath)) {
        val title = String.format(
            getString(org.fossify.commons.R.string.file_already_exists_overwrite),
            targetPath.getFilenameFromPath()
        )
        ConfirmationDialog(this, title) {
            requestGrantsThenProceed()
        }
    } else {
        requestGrantsThenProceed()
    }
}

fun Activity.proposeNewFilePath(uri: Uri): Pair<String, Boolean> {
    var newPath = applicationContext.getRealPathFromURI(uri) ?: ""
    if (newPath.startsWith("/mnt/")) {
        newPath = ""
    }

    var shouldAppendFilename = true
    if (newPath.isEmpty()) {
        val filename = applicationContext.getFilenameFromContentUri(uri) ?: ""
        if (filename.isNotEmpty()) {
            val path = if (intent.extras?.containsKey(REAL_FILE_PATH) == true) {
                intent.getStringExtra(REAL_FILE_PATH)?.getParentPath()
            } else {
                internalStoragePath
            }
            newPath = "$path/$filename"
            shouldAppendFilename = false
        }
    }

    if (newPath.isEmpty()) {
        newPath = "$internalStoragePath/${getCurrentFormattedDateTime()}.${
            uri.toString().getFilenameExtension()
        }"
        shouldAppendFilename = false
    }

    return Pair(newPath, shouldAppendFilename)
}

fun Activity.applyVaultScreenProtection(enable: Boolean, force: Boolean = false) {
    // The lock screen is always protected; other screens honour the user's preference.
    // While the whole-app password is enabled we always suppress task snapshots and screenshots:
    // the system captures the Recents preview when the app is left, which happens *before* the
    // 30-second grace window expires, so deciding at lock time would be too late.
    val shouldProtect = (enable && (force || config.blockVaultScreenshots)) ||
        config.isAppPasswordProtectionOn
    try {
        if (shouldProtect) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(shouldProtect)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!shouldProtect)
        }
        val decorView = window.decorView
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            decorView.importantForAutofill = if (shouldProtect) {
                android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            } else {
                android.view.View.IMPORTANT_FOR_AUTOFILL_AUTO
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            decorView.importantForContentCapture = if (shouldProtect) {
                android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
            } else {
                android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_AUTO
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            decorView.setAccessibilityDataSensitive(
                if (shouldProtect) {
                    android.view.View.ACCESSIBILITY_DATA_SENSITIVE_YES
                } else {
                    android.view.View.ACCESSIBILITY_DATA_SENSITIVE_AUTO
                }
            )
        }
    } catch (_: Throwable) {
    }
}

/**
 * Tints an inline settings info icon and opens a scrollable explanation dialog when tapped.
 */
fun Activity.bindSettingInfoIcon(
    iconView: android.widget.ImageView,
    @androidx.annotation.StringRes titleResId: Int,
    @androidx.annotation.StringRes messageResId: Int
) {
    iconView.applyColorFilter(getProperTextColor())
    iconView.contentDescription = getString(titleResId)
    iconView.setOnClickListener {
        showSettingInfoDialog(titleResId, messageResId)
    }
}

/**
 * Displays a themed scrollable information dialog describing a setting's purpose and trade-offs.
 */
fun Activity.showSettingInfoDialog(
    @androidx.annotation.StringRes titleResId: Int,
    @androidx.annotation.StringRes messageResId: Int
) {
    val paddingPx = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
    val mediumPadPx = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
    val messageView = org.fossify.commons.views.MyTextView(this).apply {
        text = getString(messageResId)
        setTextColor(getProperTextColor())
        setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(org.fossify.commons.R.dimen.normal_text_size)
        )
        setPadding(paddingPx, mediumPadPx, paddingPx, mediumPadPx)
    }
    val scrollView = android.widget.ScrollView(this).apply {
        addView(messageView)
    }
    getAlertDialogBuilder()
        .setPositiveButton(org.fossify.commons.R.string.ok, null)
        .apply {
            setupDialogStuff(scrollView, this, titleResId)
        }
}
