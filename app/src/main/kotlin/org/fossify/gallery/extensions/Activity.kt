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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.squareup.picasso.Picasso
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FileConflictDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.activities.VideoPlayerActivity
import org.fossify.gallery.dialogs.AllFilesPermissionDialog
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.EncryptConfirmationDialog
import org.fossify.gallery.dialogs.resolveAlbumCryptoOptions
import org.fossify.gallery.dialogs.resolveCryptoDuplicateSourceOption
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.dialogs.PickDirectoryDialog
import org.fossify.gallery.dialogs.PickVaultAlbumDialog
import org.fossify.gallery.dialogs.ResizeMultipleImagesDialog
import org.fossify.gallery.dialogs.ResizeWithPathDialog
import org.fossify.gallery.dialogs.VaultSecurityDialog
import org.fossify.gallery.helpers.CIPHER_SECRETS_DIR_NAME
import org.fossify.gallery.helpers.CONFLICT_HANDLING_SMART_DIALOG
import org.fossify.gallery.helpers.CopyMovePolicy
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.ENCRYPTED_BIN_EXT
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.PickDirectoryMode
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.TEMP_FOLDER_NAME
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.helpers.copyMoveFilesWithProgress
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.models.DateTaken
import java.io.*
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.net.toUri

fun Activity.sharePath(path: String) {
    sharePathIntent(path, BuildConfig.APPLICATION_ID)
}

fun Activity.sharePaths(paths: ArrayList<String>) {
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
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
    openPathIntent(path, forceChooser, BuildConfig.APPLICATION_ID, extras = extras)
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
    val licenses = LICENSE_GLIDE or LICENSE_CROPPER or LICENSE_RTL or LICENSE_SUBSAMPLING or LICENSE_PATTERN or LICENSE_REPRINT or LICENSE_GIF_DRAWABLE or
        LICENSE_PICASSO or LICENSE_EXOPLAYER or LICENSE_SANSELAN or LICENSE_FILTERS or LICENSE_GESTURE_VIEWS or LICENSE_APNG

    val faqItems = arrayListOf(
        FAQItem(R.string.faq_3_title, R.string.faq_3_text),
        FAQItem(R.string.faq_12_title, R.string.faq_12_text),
        FAQItem(R.string.faq_7_title, R.string.faq_7_text),
        FAQItem(R.string.faq_14_title, R.string.faq_14_text),
        FAQItem(R.string.faq_1_title, R.string.faq_1_text),
        FAQItem(org.fossify.commons.R.string.faq_5_title_commons, org.fossify.commons.R.string.faq_5_text_commons),
        FAQItem(R.string.faq_5_title, R.string.faq_5_text),
        FAQItem(R.string.faq_4_title, R.string.faq_4_text),
        FAQItem(R.string.faq_6_title, R.string.faq_6_text),
        FAQItem(R.string.faq_8_title, R.string.faq_8_text),
        FAQItem(R.string.faq_10_title, R.string.faq_10_text),
        FAQItem(R.string.faq_11_title, R.string.faq_11_text),
        FAQItem(R.string.faq_13_title, R.string.faq_13_text),
        FAQItem(R.string.faq_15_title, R.string.faq_15_text),
        FAQItem(R.string.faq_2_title, R.string.faq_2_text),
        FAQItem(R.string.faq_18_title, R.string.faq_18_text),
        FAQItem(org.fossify.commons.R.string.faq_9_title_commons, org.fossify.commons.R.string.faq_9_text_commons),
    )

    if (resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)) {
        faqItems.add(FAQItem(org.fossify.commons.R.string.faq_2_title_commons, org.fossify.commons.R.string.faq_2_text_commons))
        faqItems.add(FAQItem(org.fossify.commons.R.string.faq_6_title_commons, org.fossify.commons.R.string.faq_6_text_commons))
        faqItems.add(FAQItem(org.fossify.commons.R.string.faq_7_title_commons, org.fossify.commons.R.string.faq_7_text_commons))
        faqItems.add(FAQItem(org.fossify.commons.R.string.faq_10_title_commons, org.fossify.commons.R.string.faq_10_text_commons))
    }

    if (isRPlus() && !isExternalStorageManager()) {
        faqItems.add(0, FAQItem(R.string.faq_16_title, "${getString(R.string.faq_16_text)} ${getString(R.string.faq_16_text_extra)}"))
        faqItems.add(1, FAQItem(R.string.faq_17_title, R.string.faq_17_text))
        faqItems.removeIf { it.text == R.string.faq_7_text }
        faqItems.removeIf { it.text == R.string.faq_14_text }
        faqItems.removeIf { it.text == R.string.faq_8_text }
    }

    startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
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
                callback()
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                callback()
            }
        }
    } else {
        try {
            if (file.createNewFile()) {
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
    onCancelled: () -> Unit = {}
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
        onCancelled = onCancelled
    )
}

fun BaseSimpleActivity.promptVaultSetupChoice(
    confirmBeforeCreatingNew: Boolean,
    onCreated: () -> Unit,
    onCancelled: () -> Unit = {}
) {
    val items = arrayListOf(
        RadioItem(VAULT_ACTION_CREATE_NEW, getString(R.string.create_new_vault_option)),
        RadioItem(VAULT_ACTION_USE_EXISTING, getString(R.string.use_existing_vault_option))
    )

    RadioGroupDialog(
        activity = this,
        items = items,
        checkedItemId = -1,
        titleId = R.string.vault_setup_action_title,
        cancelCallback = {
            onCancelled()
        }
    ) { selected ->
        val reopenStage1 = {
            promptVaultSetupChoice(confirmBeforeCreatingNew, onCreated, onCancelled)
        }
        when (selected as Int) {
            VAULT_ACTION_CREATE_NEW -> {
                promptWhereToCreateVault(
                    confirmBeforeCreatingNew = confirmBeforeCreatingNew,
                    onVaultReady = onCreated,
                    onBack = reopenStage1
                )
            }
            VAULT_ACTION_USE_EXISTING -> {
                pickExistingVaultAndUnlock(
                    onVaultReady = onCreated,
                    onBack = reopenStage1
                )
            }
            else -> onCancelled()
        }
    }
}

fun BaseSimpleActivity.promptWhereToCreateVault(
    confirmBeforeCreatingNew: Boolean = false,
    onVaultReady: () -> Unit,
    onBack: () -> Unit
) {
    val items = arrayListOf(
        RadioItem(VAULT_LOCATION_OPTION_STANDARD, getString(R.string.vault_location_default_short)),
        RadioItem(VAULT_LOCATION_OPTION_CUSTOM, getString(R.string.vault_location_custom_short))
    )

    RadioGroupDialog(
        activity = this,
        items = items,
        checkedItemId = -1,
        titleId = R.string.where_to_create_vault_title,
        cancelCallback = {
            onBack()
        }
    ) { selected ->
        val reopenStage2 = {
            promptWhereToCreateVault(confirmBeforeCreatingNew, onVaultReady, onBack)
        }
        when (selected as Int) {
            VAULT_LOCATION_OPTION_STANDARD -> {
                handleCreateTargetSelected(
                    targetDir = null,
                    confirmBeforeCreatingNew = confirmBeforeCreatingNew,
                    onVaultReady = onVaultReady,
                    onBack = reopenStage2,
                    onPickAnother = reopenStage2
                )
            }
            VAULT_LOCATION_OPTION_CUSTOM -> {
                pickCustomFolderForCreateVault(
                    confirmBeforeCreatingNew = confirmBeforeCreatingNew,
                    onVaultReady = onVaultReady,
                    onBack = reopenStage2
                )
            }
            else -> onBack()
        }
    }
}

private fun BaseSimpleActivity.pickCustomFolderForCreateVault(
    confirmBeforeCreatingNew: Boolean,
    onVaultReady: () -> Unit,
    onBack: () -> Unit
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
                    pickCustomFolderForCreateVault(confirmBeforeCreatingNew, onVaultReady, onBack)
                }
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
    val messageView = layoutInflater.inflate(org.fossify.commons.R.layout.dialog_message, null)
    messageView.findViewById<TextView>(org.fossify.commons.R.id.message).setText(R.string.no_existing_vault_at_location)
    var buttonHandled = false
    getAlertDialogBuilder()
        .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
            buttonHandled = true
            onRetryPick()
        }
        .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ ->
            buttonHandled = true
            onBack()
        }
        .apply {
            setupDialogStuff(messageView, this) { alertDialog ->
                alertDialog.setOnDismissListener {
                    if (!buttonHandled) {
                        onBack()
                    }
                }
            }
        }
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
    val messageView = layoutInflater.inflate(org.fossify.commons.R.layout.dialog_message, null)
    messageView.findViewById<TextView>(org.fossify.commons.R.id.message).setText(messageResId)
    getAlertDialogBuilder()
        .setPositiveButton(org.fossify.commons.R.string.ok, null)
        .apply {
            setupDialogStuff(messageView, this) { alertDialog ->
                alertDialog.setOnDismissListener {
                    onDone()
                }
            }
        }
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

    CryptoManager.lockVault()
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
            CryptoManager.lockVault()
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
    onPickAnother: () -> Unit
) {
    val lookupDir = targetDir ?: CryptoManager.getDefaultVaultRoot()
    val existingVault = CryptoManager.resolveExistingVaultInDirectory(lookupDir)
    if (existingVault != null) {
        val messageView = layoutInflater.inflate(org.fossify.commons.R.layout.dialog_message, null)
        messageView.findViewById<TextView>(org.fossify.commons.R.id.message).text =
            getString(R.string.unlock_existing_vault_at_location_prompt, humanizePath(existingVault.absolutePath))
        var buttonHandled = false
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.yes) { _, _ ->
                buttonHandled = true
                unlockCandidateVault(
                    existingVault = existingVault,
                    onVaultReady = onVaultReady,
                    onBack = onPickAnother
                )
            }
            .setNegativeButton(org.fossify.commons.R.string.no) { _, _ ->
                buttonHandled = true
                onPickAnother()
            }
            .apply {
                setupDialogStuff(messageView, this) { alertDialog ->
                    alertDialog.setOnDismissListener {
                        if (!buttonHandled) {
                            onBack()
                        }
                    }
                }
            }
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
                        onVaultReady()
                    }
                }
            }
        }
    }

    val targetDisplayDir = if (targetDir == null) {
        CryptoManager.getDefaultVaultRoot()
    } else if (targetDir.name.equals(CIPHER_SECRETS_DIR_NAME, ignoreCase = true)) {
        targetDir
    } else {
        File(targetDir, CIPHER_SECRETS_DIR_NAME)
    }

    if (confirmBeforeCreatingNew && switchingFromAnotherVault) {
        val messageView = layoutInflater.inflate(org.fossify.commons.R.layout.dialog_message, null)
        messageView.findViewById<TextView>(org.fossify.commons.R.id.message).text =
            getString(R.string.create_new_vault_at_location_prompt, humanizePath(targetDisplayDir.absolutePath))
        var buttonHandled = false
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.yes) { _, _ ->
                buttonHandled = true
                startCreationDialog()
            }
            .setNegativeButton(org.fossify.commons.R.string.no) { _, _ ->
                buttonHandled = true
                onBack()
            }
            .apply {
                setupDialogStuff(messageView, this) { alertDialog ->
                    alertDialog.setOnDismissListener {
                        if (!buttonHandled) {
                            onBack()
                        }
                    }
                }
            }
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

fun BaseSimpleActivity.promptSyncVaultFromExternal(onSynced: (() -> Unit)? = null) {
    if (!hasVaultCreated()) {
        toast(R.string.enable_hidden_protection_first_prompt)
        return
    }

    ensureVaultUnlocked {
        val promptView = layoutInflater.inflate(org.fossify.commons.R.layout.dialog_message, null)
        promptView.findViewById<TextView>(org.fossify.commons.R.id.message)
            .setText(R.string.sync_vault_select_source_prompt)
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                pickCustomVaultDirectory(
                    initialPath = defaultSyncSourcePickerPath(),
                    requireWritable = false,
                    onPicked = { pickedDir ->
                        val resolvedSource = CryptoManager.resolveSyncSourceRoot(pickedDir)
                        val activeVaultRoot = CryptoManager.findVaultRoot(this)
                        val sameVault = activeVaultRoot != null && runCatching {
                            resolvedSource.canonicalPath == activeVaultRoot.canonicalPath
                        }.getOrDefault(false)
                        if (sameVault) {
                            showVaultLocationNoticeDialog(R.string.vault_sync_same_folder_error, {})
                            return@pickCustomVaultDirectory
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
                                    sourceDir = pickedDir
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
                                        onSynced?.invoke()
                                        return@runOnUiThread
                                    }
                                    when {
                                        result == null -> toast(
                                            if (sameVault) {
                                                R.string.vault_sync_same_folder_error
                                            } else {
                                                org.fossify.commons.R.string.unknown_error_occurred
                                            }
                                        )
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
                                    onSynced?.invoke()
                                }
                            }
                        }
                    },
                    onCancelled = {}
                )
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                setupDialogStuff(promptView, this, titleId = R.string.sync_vault_from_external)
            }
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

            // Block the UI with a progress bar until decryption finishes, same as during encryption.
            resolveCryptoDuplicateSourceOption(
                activity = this,
                isEncrypt = false,
                hasIdenticalInTarget = {
                    CryptoManager.hasAnyIdenticalTransferMatch(
                        sourceTargetPairs = listOf(srcFile to targetDir),
                        isTargetEncrypted = false,
                        fastSampling = config.fastLargeDuplicateCheck,
                        context = this
                    )
                },
                onCancelled = { callback?.invoke(oldPath) }
            ) { deleteSourceOnIdentical ->
                ensureNotificationPermissionThenRun { notificationsAllowed ->
                    val progressDialog = CryptoProgressDialog(
                        activity = this,
                        max = 1,
                        titleId = R.string.decrypting_files,
                        enableNotification = notificationsAllowed
                    )
                    ensureBackgroundThread {
                        val decryptedFile = try {
                            CryptoManager.decryptFileToDirectory(
                                context = this,
                                encryptedFile = srcFile,
                                targetDir = targetDir,
                                deleteEncryptedSource = true,
                                deleteSourceOnIdentical = deleteSourceOnIdentical
                            )
                        } catch (e: Exception) {
                            null
                        }
                        val resultPath = decryptedFile?.absolutePath ?: oldPath
                        CryptoManager.clearAppImageCaches(this)
                        runOnUiThread {
                            progressDialog.updateProgress(if (decryptedFile != null) 1 else 0, 1)
                            progressDialog.dismiss()
                            if (decryptedFile != null && decryptedFile.exists()) {
                                toast(R.string.file_decrypted_successfully)
                            } else {
                                toast(org.fossify.commons.R.string.unknown_error_occurred)
                            }
                            callback?.invoke(resultPath)
                        }
                    }
                }
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

    PickVaultAlbumDialog(this) { targetDir, albumName ->
        resolveCryptoDuplicateSourceOption(
            activity = this,
            isEncrypt = true,
            hasIdenticalInTarget = {
                CryptoManager.hasAnyIdenticalTransferMatch(
                    sourceTargetPairs = listOf(srcFile to targetDir),
                    isTargetEncrypted = true,
                    fastSampling = config.fastLargeDuplicateCheck,
                    context = this
                )
            },
            onCancelled = { callback?.invoke(oldPath) }
        ) { deleteSourceOnIdentical ->
            ensureNotificationPermissionThenRun { notificationsAllowed ->
                val progressDialog = CryptoProgressDialog(
                    activity = this,
                    max = 1,
                    titleId = R.string.encrypting_files,
                    enableNotification = notificationsAllowed
                )
                ensureBackgroundThread {
                    val encryptedFile = CryptoManager.encryptFileToDirectory(
                        context = this,
                        sourceFile = srcFile,
                        targetDir = targetDir,
                        wipeSource = true,
                        clearCachesNow = true,
                        deleteSourceOnIdentical = deleteSourceOnIdentical
                    )
                    val resultPath = encryptedFile?.absolutePath ?: oldPath
                    runOnUiThread {
                        progressDialog.updateProgress(if (encryptedFile != null && encryptedFile.exists()) 1 else 0, 1)
                        progressDialog.dismiss()
                        if (encryptedFile != null && encryptedFile.exists()) {
                            toast(getString(R.string.file_hidden_to_album, albumName))
                        } else {
                            toast(org.fossify.commons.R.string.unknown_error_occurred)
                        }
                        callback?.invoke(resultPath)
                    }
                }
            }
        }
    }
}

fun BaseSimpleActivity.ensureVaultUnlocked(
    onCancelled: () -> Unit = {},
    onUnlocked: () -> Unit
) {
    if (CryptoManager.isVaultUnlocked) {
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

fun BaseSimpleActivity.resolveSmartContentConflicts(
    conflicts: List<Pair<String, String>>,
    callback: (LinkedHashMap<String, Int>) -> Unit
) {
    val resolutions = LinkedHashMap<String, Int>()
    if (config.conflictHandlingMode != CONFLICT_HANDLING_SMART_DIALOG || conflicts.isEmpty()) {
        runOnUiThread {
            callback(resolutions)
        }
        return
    }

    val showApplyToAll = conflicts.size > 1
    fun askNext(index: Int) {
        if (index >= conflicts.size || isFinishing || isDestroyed) {
            callback(resolutions)
            return
        }
        val (key, displayName) = conflicts[index]
        val fileDirItem = FileDirItem(key, displayName, false)
        FileConflictDialog(this, fileDirItem, showApplyToAll) { resolution, applyToAll ->
            resolutions[key] = resolution
            if (applyToAll) {
                for (i in (index + 1) until conflicts.size) {
                    resolutions[conflicts[i].first] = resolution
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
                    val runWithResolutions = {
                        conflictResolutions: LinkedHashMap<String, Int>,
                        precheckedIndex: CryptoManager.TargetPlainIndex?,
                        precheckedIdenticalPaths: Set<String>,
                        precheckedNonIdenticalPaths: Set<String> ->
                        resolveCryptoDuplicateSourceOption(
                            activity = this,
                            isEncrypt = false,
                            hasIdenticalInTarget = {
                                when {
                                    precheckedIdenticalPaths.isNotEmpty() -> true
                                    precheckedNonIdenticalPaths.size >= fileDirItems.size -> false
                                    else -> {
                                        val pairs = fileDirItems.mapNotNull { item ->
                                            val f = File(item.path)
                                            if (f.exists() && f.isFile) f to destDir else null
                                        }
                                        CryptoManager.hasAnyIdenticalTransferMatch(
                                            sourceTargetPairs = pairs,
                                            isTargetEncrypted = false,
                                            fastSampling = config.fastLargeDuplicateCheck,
                                            context = this
                                        )
                                    }
                                }
                            }
                        ) { deleteSourceOnIdentical ->
                        ensureNotificationPermissionThenRun { notificationsAllowed ->
                            val progressDialog = CryptoProgressDialog(
                                activity = this,
                                max = fileDirItems.size,
                                titleId = R.string.decrypting_files,
                                enableNotification = notificationsAllowed
                            )
                            ensureBackgroundThread {
                                if (!destDir.exists()) {
                                    destDir.mkdirs()
                                }
                                val execPlainIndex = precheckedIndex ?: CryptoManager.TargetPlainIndex(destDir)
                                val sourceDirs = LinkedHashSet<File>()
                                var processedCount = 0
                                var skippedCount = 0
                                var allSuccess = true
                                var anyFavoriteUpdated = false
                                var lastUiProgressMs = 0L

                                for (item in fileDirItems) {
                                    val srcFile = File(item.path)
                                    if (!srcFile.exists()) continue
                                    srcFile.parentFile?.let { sourceDirs.add(it) }
                                    val resolution = conflictResolutions[srcFile.absolutePath] ?: CONFLICT_KEEP_BOTH

                                    val isEnc = CryptoManager.isEncryptedFile(srcFile.name)
                                    val desiredHiddenName = if (!isEnc && srcFile.name.startsWith('.')) {
                                        srcFile.name.substring(1)
                                    } else {
                                        null
                                    }

                                    val isHiddenIdenticalSkip = !isEnc && (
                                        srcFile.absolutePath in precheckedIdenticalPaths || (
                                            srcFile.absolutePath !in precheckedNonIdenticalPaths &&
                                                CryptoManager.inspectTransferConflict(
                                                    sourceFile = srcFile,
                                                    targetDir = destDir,
                                                    isTargetEncrypted = false,
                                                    plainIndex = execPlainIndex,
                                                    desiredPlainName = desiredHiddenName,
                                                    fastSampling = config.fastLargeDuplicateCheck
                                                ).status == CryptoManager.TransferConflictStatus.IDENTICAL_SKIP
                                            )
                                        )

                                    if (isHiddenIdenticalSkip) {
                                        if (deleteSourceOnIdentical && desiredHiddenName != null) {
                                            val existingTarget = execPlainIndex.findExact(desiredHiddenName) ?: File(destDir, desiredHiddenName)
                                            if (srcFile.delete()) {
                                                updateDBMediaPath(srcFile.absolutePath, existingTarget.absolutePath)
                                                processedCount++
                                            } else {
                                                allSuccess = false
                                            }
                                        } else {
                                            skippedCount++
                                        }
                                    } else {
                                        val decFile = if (isEnc) {
                                            CryptoManager.decryptFileToDirectory(
                                                context = this,
                                                encryptedFile = srcFile,
                                                targetDir = destDir,
                                                deleteEncryptedSource = true,
                                                conflictResolution = resolution,
                                                targetIndex = execPlainIndex,
                                                skipDuplicateCheck = srcFile.absolutePath in precheckedNonIdenticalPaths,
                                                deleteSourceOnIdentical = deleteSourceOnIdentical
                                            )
                                        } else if (desiredHiddenName != null) {
                                            val exactTarget = execPlainIndex.findExact(desiredHiddenName) ?: File(destDir, desiredHiddenName)
                                            val target = if (exactTarget.exists()) {
                                                when (resolution) {
                                                    CONFLICT_SKIP -> null
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
                                            if (target != null && srcFile.renameTo(target)) {
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
                                        } else if (resolution == CONFLICT_SKIP) {
                                            skippedCount++
                                        } else {
                                            allSuccess = false
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
                                    callback(destination)
                                }
                            }
                        }
                        }
                    }

                    if (config.conflictHandlingMode == CONFLICT_HANDLING_SMART_DIALOG) {
                        ensureBackgroundThread {
                            val plainIndex = CryptoManager.TargetPlainIndex(destDir)
                            val conflicts = ArrayList<Pair<String, String>>()
                            val identicalPaths = HashSet<String>()
                            val nonIdenticalPaths = HashSet<String>()
                            for (item in fileDirItems) {
                                val srcFile = File(item.path)
                                val desiredName = if (!CryptoManager.isEncryptedFile(srcFile.name) && srcFile.name.startsWith('.')) {
                                    srcFile.name.substring(1)
                                } else {
                                    null
                                }
                                val check = CryptoManager.inspectTransferConflict(
                                    sourceFile = srcFile,
                                    targetDir = destDir,
                                    isTargetEncrypted = false,
                                    plainIndex = plainIndex,
                                    desiredPlainName = desiredName,
                                    fastSampling = config.fastLargeDuplicateCheck,
                                    context = this
                                )
                                when (check.status) {
                                    CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                                        identicalPaths.add(srcFile.absolutePath)
                                    }
                                    CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                                        nonIdenticalPaths.add(srcFile.absolutePath)
                                        conflicts.add(srcFile.absolutePath to (check.displayName ?: srcFile.name))
                                    }
                                    CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
                                }
                            }
                            resolveSmartContentConflicts(conflicts) { resolutions ->
                                runWithResolutions(resolutions, plainIndex, identicalPaths, nonIdenticalPaths)
                            }
                        }
                    } else {
                        runWithResolutions(LinkedHashMap(), null, emptySet(), emptySet())
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
            ensureNotificationPermissionThenRun { notificationsAllowed ->
                val totalFilesCount = folderPaths.sumOf { folderPath ->
                    File(folderPath).listFiles()?.count { it.isFile && CryptoManager.isEncryptedFile(it.name) } ?: 0
                }
                val progressDialog = CryptoProgressDialog(
                    activity = this,
                    max = totalFilesCount,
                    titleId = R.string.decrypting_folder,
                    enableNotification = notificationsAllowed
                )
                ensureBackgroundThread {
                    var globalProcessed = 0
                    var allSuccess = true
                    var lastDestination = targetDir?.absolutePath ?: ""

                    for (folderPath in folderPaths) {
                        var folderLastCurrent = 0
                        val result = try {
                            CryptoManager.decryptFolder(
                                context = this,
                                folderPath = folderPath,
                                targetFolderOverride = targetDir,
                                options = albumOptions
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
                        } catch (_: Exception) {
                            null
                        }
                        if (result == null) {
                            allSuccess = false
                        } else if (lastDestination.isEmpty()) {
                            lastDestination = result
                        }
                    }

                    CryptoManager.clearAppImageCaches(this)
                    runOnUiThread {
                        try {
                            progressDialog.dismiss()
                        } catch (_: Exception) {}

                        if (allSuccess) {
                            toast(R.string.folder_decrypted_successfully)
                        } else {
                            toast(org.fossify.commons.R.string.unknown_error_occurred)
                        }
                        callback(lastDestination)
                    }
                }
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
            if (!isCopyOperation) {
                toast(R.string.virtual_album_cannot_be_modified)
                return@PickDirectoryDialog
            }
            val albumId = destination.getVirtualAlbumId() ?: return@PickDirectoryDialog
            ensureBackgroundThread {
                // Only vault content makes the album hidden. A file coming from a hidden folder is
                // explicitly referenced by the user, so it does not hide the album.
                val hasVaultSource = fileDirItems.any { CryptoManager.isVaultMediaPath(this, it.path) }
                val added = addFileDirItemsToVirtualAlbum(
                    albumId = albumId,
                    fileDirItems = fileDirItems,
                    forceMarkAlbumHidden = hasVaultSource
                )
                val albumName = getVirtualAlbumName(destination) ?: ""
                runOnUiThread {
                    val msgRes = if (added > 0) R.string.added_to_virtual_album else R.string.already_in_virtual_album
                    toast(getString(msgRes, albumName))
                    callback(destination)
                }
            }
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
                val runCryptoWithResolutions = {
                    conflictResolutions: LinkedHashMap<String, Int>,
                    precheckedPlainIndex: CryptoManager.TargetPlainIndex?,
                    precheckedEncIndex: CryptoManager.TargetEncryptedIndex?,
                    precheckedIdenticalPaths: Set<String>,
                    precheckedNonIdenticalPaths: Set<String> ->
                    ensureNotificationPermissionThenRun { notificationsAllowed ->
                        val progressDialog = CryptoProgressDialog(
                            activity = this,
                            max = cryptoItems.size,
                            titleId = progressTitleId,
                            enableNotification = notificationsAllowed
                        )
                        ensureBackgroundThread {
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
                            var anyPlaintextWiped = false
                            var anyFavoriteUpdated = false
                            var lastUiProgressMs = 0L

                            for ((itemIndex, item) in cryptoItems.withIndex()) {
                                val srcFile = File(item.path)
                                val now = System.currentTimeMillis()
                                if (itemIndex == 0 || now - lastUiProgressMs >= 80L) {
                                    lastUiProgressMs = now
                                    runOnUiThread { progressDialog.updateProgress(itemIndex, cryptoItems.size) }
                                }
                                if (!srcFile.exists()) continue
                                srcFile.parentFile?.let { sourceDirs.add(it) }
                                val resolution = conflictResolutions[srcFile.absolutePath] ?: CONFLICT_KEEP_BOTH

                                val isIdenticalSkip = srcFile.absolutePath in precheckedIdenticalPaths || (
                                    srcFile.absolutePath !in precheckedNonIdenticalPaths &&
                                        CryptoManager.inspectTransferConflict(
                                            sourceFile = srcFile,
                                            targetDir = destDir,
                                            isTargetEncrypted = isDestEncrypted,
                                            plainIndex = plainIndex,
                                            encryptedIndex = encIndex,
                                            fastSampling = config.fastLargeDuplicateCheck
                                        ).status == CryptoManager.TransferConflictStatus.IDENTICAL_SKIP
                                    )
                                if (isIdenticalSkip) {
                                    continue
                                }

                                if (isDestEncrypted && !CryptoManager.isEncryptedFile(srcFile.name)) {
                                    val encFile = CryptoManager.encryptFileToDirectory(
                                        context = this,
                                        sourceFile = srcFile,
                                        targetDir = destDir,
                                        wipeSource = !isCopyOperation,
                                        clearCachesNow = false,
                                        conflictResolution = resolution,
                                        targetIndex = encIndex,
                                        skipDuplicateCheck = true
                                    )
                                    if (!isCopyOperation && encFile != null) {
                                        anyPlaintextWiped = true
                                    }
                                } else if (!isDestEncrypted && CryptoManager.isEncryptedFile(srcFile.name)) {
                                    val decFile = CryptoManager.decryptFileToDirectory(
                                        context = this,
                                        encryptedFile = srcFile,
                                        targetDir = destDir,
                                        deleteEncryptedSource = !isCopyOperation,
                                        conflictResolution = resolution,
                                        targetIndex = plainIndex,
                                        skipDuplicateCheck = true
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
                                } else if (isDestEncrypted && CryptoManager.isEncryptedFile(srcFile.name)) {
                                    val movedOrCopiedBin = CryptoManager.copyOrMoveEncryptedFileToDirectory(
                                        context = this,
                                        sourceBin = srcFile,
                                        targetDir = destDir,
                                        isMove = !isCopyOperation,
                                        conflictResolution = resolution,
                                        targetIndex = encIndex,
                                        skipDuplicateCheck = true
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
                                            CONFLICT_SKIP -> null
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
                                    if (targetFile != null && oldSrcPath != targetFile.absolutePath) {
                                        srcFile.copyTo(targetFile, overwrite = true, bufferSize = 256 * 1024)
                                        targetFile.setLastModified(srcFile.lastModified())
                                        activePlainIndex.recordFile(targetFile)
                                        if (!isCopyOperation) {
                                            runCatching { updateDBMediaPath(oldSrcPath, targetFile.absolutePath) }
                                            CryptoManager.secureWipeAndDeleteFile(this, srcFile)
                                            anyPlaintextWiped = true
                                        }
                                        pathsToRescan.add(targetFile.absolutePath)
                                    }
                                }
                            }

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
                                callback(destination)
                            }
                        }
                    }
                }

                if (config.conflictHandlingMode == CONFLICT_HANDLING_SMART_DIALOG) {
                    ensureBackgroundThread {
                        val plainIndex = if (!isDestEncrypted) CryptoManager.TargetPlainIndex(destDir) else null
                        val encIndex = if (isDestEncrypted) CryptoManager.TargetEncryptedIndex(destDir) else null
                        val conflicts = ArrayList<Pair<String, String>>()
                        val identicalPaths = HashSet<String>()
                        val nonIdenticalPaths = HashSet<String>()
                        for (item in cryptoItems) {
                            val srcFile = File(item.path)
                            val check = CryptoManager.inspectTransferConflict(
                                sourceFile = srcFile,
                                targetDir = destDir,
                                isTargetEncrypted = isDestEncrypted,
                                plainIndex = plainIndex,
                                encryptedIndex = encIndex,
                                fastSampling = config.fastLargeDuplicateCheck,
                                context = this
                            )
                            when (check.status) {
                                CryptoManager.TransferConflictStatus.IDENTICAL_SKIP -> {
                                    identicalPaths.add(srcFile.absolutePath)
                                }
                                CryptoManager.TransferConflictStatus.NAME_CONFLICT -> {
                                    nonIdenticalPaths.add(srcFile.absolutePath)
                                    conflicts.add(srcFile.absolutePath to (check.displayName ?: srcFile.name))
                                }
                                CryptoManager.TransferConflictStatus.NO_CONFLICT -> Unit
                            }
                        }
                        resolveSmartContentConflicts(conflicts) { resolutions ->
                            runCryptoWithResolutions(resolutions, plainIndex, encIndex, identicalPaths, nonIdenticalPaths)
                        }
                    }
                } else {
                    runCryptoWithResolutions(LinkedHashMap(), null, null, emptySet(), emptySet())
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
                message = getString(R.string.confirm_encrypt_files_to_vault)
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
        var pathsCnt = paths.size
        val OTGPath = config.OTGPath

        for (source in paths) {
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
                    return@ensureBackgroundThread
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
                    return@ensureBackgroundThread
                }
            }
        }
        callback?.invoke(pathsCnt == 0)
    }
}

fun BaseSimpleActivity.restoreRecycleBinPath(path: String, callback: () -> Unit) {
    restoreRecycleBinPaths(arrayListOf(path), callback)
}

fun BaseSimpleActivity.restoreRecycleBinPaths(paths: ArrayList<String>, callback: () -> Unit) {
    ensureBackgroundThread {
        val newPaths = ArrayList<String>()
        val restoredPairs = ArrayList<Pair<String, String>>()
        var shownRestoringToPictures = false
        for (source in paths) {
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
                return@ensureBackgroundThread
            }

            val isShowingSAFSdk30 = handleSAFDialogSdk30(destination) {}
            if (isShowingSAFSdk30) {
                return@ensureBackgroundThread
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

fun BaseSimpleActivity.emptyTheRecycleBin(callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
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

fun BaseSimpleActivity.emptyAndDisableTheRecycleBin(callback: () -> Unit) {
    ensureBackgroundThread {
        emptyTheRecycleBin {
            config.useRecycleBin = false
            callback()
        }
    }
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

    Picasso.get().invalidate(path.getFileKey(lastModified))
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

    val newBitmap = Glide.with(applicationContext).asBitmap().load(oldPath).submit(size.x, size.y).get()

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
        SecurityDialog(this, config.excludedPasswordHash, config.excludedProtectionType) { _, _, success ->
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

fun Activity.applyVaultScreenProtection(enable: Boolean) {
    val shouldProtect = enable && config.blockVaultScreenshots
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