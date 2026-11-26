package org.fossify.gallery.extensions

import android.widget.TextView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.dialogs.VaultChoiceDialog
import org.fossify.gallery.dialogs.VaultNoticeDialog
import org.fossify.gallery.dialogs.VaultSecurityDialog
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.SECRETS_DIR_NAME
import org.fossify.gallery.helpers.VAULT_ACTION_ARCHIVED
import org.fossify.gallery.helpers.VaultArchiveManager
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.helpers.canWriteToDirectory
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.ensureCipherRootDirectory
import org.fossify.gallery.helpers.findCandidateExternalVaultRoots
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.getDefaultVaultRoot
import org.fossify.gallery.helpers.getOrCreateDefaultHiddenAlbum
import org.fossify.gallery.helpers.getVaultProtectionType
import org.fossify.gallery.helpers.hasExistingVault
import org.fossify.gallery.helpers.isConfiguredVaultTemporarilyUnavailable
import org.fossify.gallery.helpers.listExternalStorageVolumeRoots
import org.fossify.gallery.helpers.resolveExistingVaultInDirectory
import org.fossify.gallery.helpers.resolvePickedPathToFile
import org.fossify.gallery.helpers.selectVaultWithoutUnlock
import org.fossify.gallery.helpers.verifyVaultOnDisk
import java.io.File

private const val VAULT_ACTION_CREATE_NEW = 1
private const val VAULT_ACTION_USE_EXISTING = 2
private const val ARCHIVED_ITEM_ID_BASE = 1000
private const val INVALID_ARCHIVE_ITEM_ID_BASE = 2000
private const val DIALOG_MESSAGE_TEXT_SIZE_SP = 14f
private const val VAULT_LOCATION_OPTION_STANDARD = 1
private const val VAULT_LOCATION_OPTION_CUSTOM = 2

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
    // Recursive re-openings after Back must not ask again.
    verifyDeviceOwnerOnEntry: Boolean = true,
) {
    // While a vault is active, merely opening "vault location" is a configuration change
    // entry point. Verify the device owner here, before any choice is made, instead of
    // waiting until a different vault has been picked.
    if (verifyDeviceOwnerOnEntry &&
        config.vaultRootPath.isNotEmpty() && CryptoManager.hasExistingVault(this)
    ) {
        verifyDeviceOwner {
            promptVaultSetupChoice(
                confirmBeforeCreatingNew,
                onCreated,
                onCancelled,
                onVaultCreated,
                verifyDeviceOwnerOnEntry = false,
            )
        }
        return
    }
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
            promptVaultSetupChoice(
                confirmBeforeCreatingNew,
                onCreated,
                onCancelled,
                onVaultCreated,
                verifyDeviceOwnerOnEntry = false,
            )
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
        // The vault is only selected here; its password is asked when it is opened.
        toast(R.string.archived_vault_activated)
        finishVaultSelection(onVaultReady)
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
                selectCandidateVault(
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

internal fun BaseSimpleActivity.pickCustomVaultDirectory(
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

internal fun BaseSimpleActivity.showVaultLocationNoticeDialog(messageResId: Int, onDone: () -> Unit) {
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

private fun BaseSimpleActivity.selectCandidateVault(
    existingVault: File,
    onVaultReady: () -> Unit,
    onBack: () -> Unit
) {
    val currentActiveRoot = CryptoManager.findVaultRoot(this)?.absolutePath
    if (currentActiveRoot != null && currentActiveRoot.equals(existingVault.absolutePath, ignoreCase = true) &&
        CryptoManager.isVaultUnlocked
    ) {
        onVaultReady()
        return
    }

    // Selecting a vault only registers its path and public header metadata. The password is
    // asked when the vault is opened, so no credential prompt is shown at this point.
    CryptoManager.lockVault(this)
    if (!CryptoManager.selectVaultWithoutUnlock(this, existingVault)) {
        onBack()
        return
    }
    toast(getString(R.string.existing_vault_detected_at_location, humanizePath(existingVault.absolutePath)))
    finishVaultSelection(onVaultReady)
}

/** Shared post-selection work; safe to run while the vault is still locked. */
private fun BaseSimpleActivity.finishVaultSelection(onVaultReady: () -> Unit) {
    ensureBackgroundThread {
        VirtualAlbumSyncManager.evictHiddenVirtualAlbumsFromDbCache(this)
        CryptoManager.clearAppImageCaches(this)
        CryptoManager.getOrCreateDefaultHiddenAlbum(this)
        VirtualAlbumSyncManager.syncVirtualAlbums(this, force = true)
        runOnUiThread {
            if (!isFinishing && !isDestroyed) onVaultReady()
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
                selectCandidateVault(
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
