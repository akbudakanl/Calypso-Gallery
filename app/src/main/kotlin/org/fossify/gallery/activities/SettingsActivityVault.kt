package org.fossify.gallery.activities

import android.view.ViewTreeObserver
import androidx.biometric.BiometricManager
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogExportVaultBinding
import org.fossify.gallery.dialogs.BruteForceDialog
import org.fossify.gallery.dialogs.DeleteSecretsDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.dialogs.VaultNoticeDialog
import org.fossify.gallery.dialogs.VaultSecurityDialog
import org.fossify.gallery.extensions.bindSettingInfoIcon
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.promptCreateVault
import org.fossify.gallery.extensions.promptExportEncryptedVault
import org.fossify.gallery.extensions.promptSyncVaultFromExternal
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.extensions.promptVaultSetupChoice
import org.fossify.gallery.extensions.showVaultPasswordSavedDialog
import org.fossify.gallery.helpers.BiometricKeyProbe
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.SHARE_METADATA_MODE_ALL
import org.fossify.gallery.helpers.SHARE_METADATA_MODE_LOCATION
import org.fossify.gallery.helpers.SHARE_METADATA_MODE_OFF
import org.fossify.gallery.helpers.VAULT_PIN_DOTS_MAX_LENGTH
import org.fossify.gallery.helpers.VaultArchiveManager
import org.fossify.gallery.helpers.VaultAuthInputHelper
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.deleteExistingVault
import org.fossify.gallery.helpers.formatPasswordReminderInterval
import org.fossify.gallery.helpers.getBiometricAuthenticatorStatus
import org.fossify.gallery.helpers.getVaultProtectionType
import org.fossify.gallery.helpers.isConfiguredCustomVaultPath
import org.fossify.gallery.helpers.isConfiguredVaultTemporarilyUnavailable
import org.fossify.gallery.helpers.probeBiometricKey
import org.fossify.gallery.helpers.resetPasswordReminderState
import org.fossify.gallery.helpers.verifyVaultOnDisk

internal fun SettingsActivity.doesVaultExist(): Boolean {
    return CryptoManager.verifyVaultOnDisk(this)
}

internal fun SettingsActivity.registerVaultSectionScrollRecheck() {
    isVaultSectionNearViewport = false
    if (vaultSectionScrollListener == null) {
        // Use a ViewTreeObserver listener: setOnScrollChangeListener() is intentionally left free
        // (setting up the material scroll listener would tint the top bar on scroll).
        vaultSectionScrollListener = ViewTreeObserver.OnScrollChangedListener {
            // Use whichever vault section row is currently visible as the viewport anchor.
            val anchor = when {
                binding.settingsVaultLocationHolder.isShown -> binding.settingsVaultLocationHolder
                binding.settingsCreateVaultHolder.isShown -> binding.settingsCreateVaultHolder
                else -> return@OnScrollChangedListener
            }
            val scroll = binding.settingsNestedScrollview

            val anchorLoc = IntArray(2)
            val scrollLoc = IntArray(2)
            anchor.getLocationOnScreen(anchorLoc)
            scroll.getLocationOnScreen(scrollLoc)

            val lookAhead = scroll.height / 2
            val labelTop = anchorLoc[1]
            val labelBottom = anchorLoc[1] + anchor.height
            val near = labelTop < scrollLoc[1] + scroll.height + lookAhead && labelBottom > scrollLoc[1] - lookAhead

            if (near && !isVaultSectionNearViewport) {
                // The "Encrypted vault" section is about to appear: check the disk before it is drawn.
                scroll.post {
                    setupVaultSettings()
                    refreshRowDecorations()
                }
            }
            isVaultSectionNearViewport = near
        }
    }
    val observer = binding.settingsNestedScrollview.viewTreeObserver
    val listener = vaultSectionScrollListener ?: return
    if (observer.isAlive) {
        observer.removeOnScrollChangedListener(listener)
        observer.addOnScrollChangedListener(listener)
    }
}

internal fun SettingsActivity.unregisterVaultSectionScrollRecheck() {
    val listener = vaultSectionScrollListener ?: return
    val observer = binding.settingsNestedScrollview.viewTreeObserver
    if (observer.isAlive) {
        observer.removeOnScrollChangedListener(listener)
    }
}

internal fun SettingsActivity.setupVaultSettings() {
    val vaultExists = doesVaultExist()
    val hasStoragePermission = !(isRPlus() && !isExternalStorageManager())

    // 0) Kasa konumu yalnızca kasa varsa görünür (birden fazla kasa arasında geçiş için).
    binding.settingsVaultLocationHolder.beVisibleIf(hasStoragePermission && vaultExists)

    // 1) Şifreli kasa oluştur (bu sadece şifreli kasa oluşturulmamış ise gösterilir;
    // Tüm Dosyalara erişim izni yoksa da görünür, dokunulduğunda tema uyumlu izin ekranı açılır)
    binding.settingsCreateVaultHolder.beVisibleIf(!vaultExists || !hasStoragePermission)

    // 1b) Harici diskten kasayı eşitle
    binding.settingsSyncVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

    // 2) Şifreli kasa şifresini değiştir
    binding.settingsChangeVaultPasswordHolder.beVisibleIf(hasStoragePermission && vaultExists)

    // 3) Şifreli kasaya biyometrik ile giriş — satır her zaman görünür, ancak uygun
    // donanım/anahtar yoksa pasif ve sağındaki i ikonuyla nedeni açıklanır.
    // onResume bu metodları yeniden çağırdığı için Android ayarlarından biyometrik
    // açılıp kapatıldığında görünüm değişir; ek bildirim/popup gösterilmez.
    biometricStatus = CryptoManager.getBiometricAuthenticatorStatus(this)
    // Keep the last known good value while the device is locked or the Keystore is busy:
    // a transient probe failure must not make the row look permanently unavailable.
    when (CryptoManager.probeBiometricKey(this)) {
        BiometricKeyProbe.READY -> biometricKeyAvailable = true
        BiometricKeyProbe.UNAVAILABLE -> biometricKeyAvailable = false
        BiometricKeyProbe.DEFERRED -> Unit
    }
    biometricUsable = biometricStatus == BiometricManager.BIOMETRIC_SUCCESS && biometricKeyAvailable
    binding.settingsVaultBiometricHolder.beVisibleIf(hasStoragePermission && vaultExists)
    binding.settingsVaultBiometricAutoReturnHolder.beVisibleIf(
        hasStoragePermission && vaultExists && biometricUsable && config.isVaultBiometricEnabled
    )
    binding.settingsVaultPasswordReminderHolder.beVisibleIf(hasStoragePermission && vaultExists)
    // An already archived vault cannot be archived again (ADR 0007).
    val activeArchived = VaultArchiveManager.isActiveVaultArchived(this)
    binding.settingsArchiveVaultHolder.beVisibleIf(
        hasStoragePermission && vaultExists && !activeArchived
    )
    binding.settingsUnarchiveVaultHolder.beVisibleIf(
        hasStoragePermission && vaultExists && activeArchived
    )

    // 4) Şifreli kasada ekran görüntüsü almayı yasakla
    binding.settingsBlockVaultScreenshotsHolder.beVisibleIf(hasStoragePermission && vaultExists)

    // 4b) Tuş basımlarını gizleme yalnızca PIN kullanan kasalarda anlamlı
    binding.settingsHideVaultKeyPressesHolder.beVisibleIf(
        hasStoragePermission && vaultExists &&
            CryptoManager.getVaultProtectionType(this) == PROTECTION_PIN
    )

    // 4c) Nokta göstergesi: PIN kullanan kasalarda gösterilir; PIN uzunluğu ayar kapalıyken
    // diske yazılmadığı için uzunluk kontrolü ayar açılırken PIN doğrulaması sırasında yapılır.
    val pinDotsApplicable = vaultExists &&
        CryptoManager.getVaultProtectionType(this) == PROTECTION_PIN
    if (!config.showVaultPinDots) {
        // Eski sürümden kalma açıkta duran vault_pin_length kaydı varsa temizle
        config.vaultPinLength = 0
    }
    binding.settingsShowVaultPinDotsHolder.beVisibleIf(hasStoragePermission && pinDotsApplicable)

    // 5) Şifreli kasayı boşalt
    binding.settingsEmptyVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

    // 6) Şifreli kasayı dışa aktar
    binding.settingsExportVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

    // 7) Şifreleme çakışma ve atlanan dosya ayarları yalnızca şifreli kasa oluşturulduysa görünür
    binding.settingsSkippedEncryptFilesHolder.beVisibleIf(hasStoragePermission && vaultExists)
    binding.settingsAlbumCryptoConflictHolder.beVisibleIf(hasStoragePermission && vaultExists)

    setupArgon2Profile()
    setupVaultLocation()
    setupCreateVault()
    setupExportVault()
    setupSyncVault()
    setupChangeVaultPassword()
    setupVaultBiometric()
    setupVaultBiometricAutoReturn()
    setupVaultPasswordReminder()
    setupArchiveVault()
    setupUnarchiveVault()
    setupBlockVaultScreenshots()
    setupHideVaultKeyPresses()
    setupShowVaultPinDots()
    setupEmptyVault()
    // Teklif penceresinden biyometrik kurulduysa anahtar hemen yansısın.
    updateVaultBiometricSwitch()
}

/**
 * Vault-wide switches and the destructive "empty vault" action always re-verify the password, even when the
 * vault is already unlocked because the person is viewing an encrypted album. The ordinary vault-session state
 * is intentionally ignored here.
 */
internal fun SettingsActivity.requireVaultPassword(
    allowBiometrics: Boolean = true,
    verifyOnly: Boolean = false,
    onAuthorized: () -> Unit
) {
    if (!doesVaultExist()) return
    val currentType = CryptoManager.getVaultProtectionType(this)
        ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
    VaultSecurityDialog(
        activity = this,
        requiredHash = config.hiddenPasswordHash,
        showTabIndex = currentType,
        minimalist = false,
        allowBiometrics = allowBiometrics,
        verifyOnly = verifyOnly,
    ) { _, _, success ->
        if (success) onAuthorized()
    }
}

private fun SettingsActivity.setupBlockVaultScreenshots() {
    binding.settingsBlockVaultScreenshots.isChecked = config.blockVaultScreenshots
    binding.settingsBlockVaultScreenshotsHolder.setOnClickListener {
        requireVaultPassword(verifyOnly = true) {
            binding.settingsBlockVaultScreenshots.toggle()
            config.blockVaultScreenshots = binding.settingsBlockVaultScreenshots.isChecked
        }
    }
}

private fun SettingsActivity.setupHideVaultKeyPresses() {
    binding.settingsHideVaultKeyPresses.isChecked = config.hideVaultKeyPresses
    binding.settingsHideVaultKeyPressesHolder.setOnClickListener {
        requireVaultPassword(verifyOnly = true) {
            binding.settingsHideVaultKeyPresses.toggle()
            config.hideVaultKeyPresses = binding.settingsHideVaultKeyPresses.isChecked
        }
    }
}

private fun SettingsActivity.setupShowVaultPinDots() {
    binding.settingsShowVaultPinDots.isChecked = config.showVaultPinDots
    binding.settingsShowVaultPinDotsHolder.setOnClickListener {
        val enabling = !config.showVaultPinDots
        val proceedWithAuth = {
            // Ayar açılırken PIN uzunluğunu öğrenmek için biyometrik yerine PIN girişi zorunlu tutulur;
            // ayar kapatılırken biyometrik doğrulama da kullanılabilir ve uzunluk diskten silinir.
            requireVaultPassword(allowBiometrics = !enabling, verifyOnly = true) {
                if (enabling) {
                    config.showVaultPinDots = true
                    val enteredLen = VaultAuthInputHelper.lastAuthenticatedPinLength
                    if (enteredLen in 1..VAULT_PIN_DOTS_MAX_LENGTH) {
                        config.vaultPinLength = enteredLen
                        binding.settingsShowVaultPinDots.isChecked = true
                    } else {
                        config.showVaultPinDots = false
                        binding.settingsShowVaultPinDots.isChecked = false
                    }
                } else {
                    config.showVaultPinDots = false
                    binding.settingsShowVaultPinDots.isChecked = false
                }
            }
        }

        // The side-by-side preview is a one-time introduction: show it only on the first
        // enable. Every later attempt - including turning the setting back on after it was
        // disabled - must go straight to password verification, otherwise the toggle would be
        // stuck once the notice flag has been persisted.
        if (enabling && !config.pinDotsNoticeShown) {
            VaultNoticeDialog(
                activity = this,
                title = getString(R.string.pin_dots_notice_title),
                message = "",
                noteText = getString(R.string.pin_dots_notice_note),
                positiveText = getString(R.string.onboarding_continue),
                negativeText = getString(org.fossify.commons.R.string.cancel),
                cancelable = true,
                canceledOnTouchOutside = true,
                showPinDotsPreview = true,
                showNoteShieldIcon = true,
                filledPositiveButton = true,
                onPositive = {
                    config.pinDotsNoticeShown = true
                    proceedWithAuth()
                },
            )
        } else {
            proceedWithAuth()
        }
    }
}

internal fun SettingsActivity.setupShareMetadata() {
    fun refreshSummary() {
        binding.settingsShareMetadataValue.text = getString(
            when (config.shareMetadataMode) {
                SHARE_METADATA_MODE_ALL -> R.string.share_metadata_all
                SHARE_METADATA_MODE_OFF -> R.string.share_metadata_off
                else -> R.string.share_metadata_location_only
            }
        )
    }
    refreshSummary()

    binding.settingsShareMetadataHolder.setOnClickListener {
        RadioGroupDialog(
            activity = this,
            items = arrayListOf(
                RadioItem(SHARE_METADATA_MODE_OFF, getString(R.string.share_metadata_off)),
                RadioItem(SHARE_METADATA_MODE_LOCATION, getString(R.string.share_metadata_location_only)),
                RadioItem(SHARE_METADATA_MODE_ALL, getString(R.string.share_metadata_all))
            ),
            checkedItemId = config.shareMetadataMode,
            titleId = R.string.settings_share_metadata_title,
        ) {
            config.shareMetadataMode = it as Int
            refreshSummary()
        }
    }
}

private fun SettingsActivity.setupArgon2Profile() {
    // Bu ayar yalnızca kasa mevcutsa ve tüm dosyalara erişim izni varsa görünür.
    val hasStoragePermission = !isRPlus() || isExternalStorageManager()
    binding.settingsArgon2ProfileHolder.beVisibleIf(hasStoragePermission && doesVaultExist())
    fun refreshSummary() {
        val summary = BruteForceDialog.summary(this)
        // Settings and first-time vault setup share the same dial and level wording.
        val level = if (summary.isEmpty()) {
            ""
        } else {
            BruteForceDialog.levelForCurrentTarget(this)
        }
        binding.settingsArgon2ProfileValue.text =
            if (level.isEmpty()) summary else "$summary · $level"
        binding.settingsArgon2ProfileValue.beGoneIf(summary.isEmpty())
    }
    refreshSummary()
    // No silent background probing: the 64 MiB / 3 rounds measurement runs only when the
    // user taps the row (BruteForceDialog.show), with a visible progress dialog.

    binding.settingsArgon2ProfileHolder.setOnClickListener {
        val prevTargetMs = config.unlockTargetMs
        val prevMemKb = config.lastCalibratedMemoryKb
        val prevIter = config.lastCalibratedIterations
        val prevClamped = config.argon2CalibrationClamped

        BruteForceDialog.show(this) {
            refreshSummary()
            if (doesVaultExist()) {
                val currentType = CryptoManager.getVaultProtectionType(this)
                    ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) {
                        PROTECTION_PASSWORD
                    } else {
                        PROTECTION_PIN
                    }
                VaultSecurityDialog(
                    activity = this,
                    requiredHash = config.hiddenPasswordHash,
                    showTabIndex = currentType,
                    minimalist = false,
                    allowBiometrics = false,
                    rewrapKdfOnUnlock = true
                ) { _, _, success ->
                    if (success) {
                        VaultSessionManager.lockVaultIfOutsideSession(this)
                        refreshSummary()
                        toast(R.string.kdf_settings_updated_successfully)
                    } else {
                        config.unlockTargetMs = prevTargetMs
                        config.lastCalibratedMemoryKb = prevMemKb
                        config.lastCalibratedIterations = prevIter
                        config.argon2CalibrationClamped = prevClamped
                        refreshSummary()
                    }
                }
            }
        }
    }
}

private fun SettingsActivity.setupVaultLocation() {
    binding.settingsVaultLocation.text = when {
        CryptoManager.isConfiguredVaultTemporarilyUnavailable(this) -> {
            getString(R.string.vault_location_unavailable_summary, humanizePath(config.vaultRootPath))
        }
        CryptoManager.isConfiguredCustomVaultPath(this) -> {
            humanizePath(config.vaultRootPath)
        }
        else -> {
            getString(R.string.vault_location_standard_summary)
        }
    }

    binding.settingsVaultLocationHolder.setOnClickListener {
        val hasConfiguredVault = doesVaultExist() || CryptoManager.isConfiguredVaultTemporarilyUnavailable(this)
        promptVaultSetupChoice(
            confirmBeforeCreatingNew = hasConfiguredVault,
            onCreated = {
                setupVaultSettings()
            },
            onCancelled = {
                setupVaultSettings()
            }
        )
    }
}

private fun SettingsActivity.setupCreateVault() {
    binding.settingsCreateVaultHolder.setOnClickListener {
        if (isRPlus() && !isExternalStorageManager()) {
            GrantAllFilesDialog(this)
            return@setOnClickListener
        }
        if (doesVaultExist()) return@setOnClickListener
        promptCreateVault(
            onCreated = {
                VaultSessionManager.lockVaultIfOutsideSession(this)
                setupVaultSettings()
            },
            onCancelled = {
                VaultSessionManager.lockVaultIfOutsideSession(this)
                setupVaultSettings()
            },
            // Refresh the rows as soon as the vault is on disk. The following dialogs can be
            // dismissed with Back (or abandoned), so their completion is not a reliable
            // signal for updating the settings screen.
            onVaultCreated = { setupVaultSettings() },
        )
    }
}

private fun SettingsActivity.setupExportVault() {
    binding.settingsExportVaultHolder.setOnClickListener {
        if (!doesVaultExist()) return@setOnClickListener

        val dialogBinding = DialogExportVaultBinding.inflate(layoutInflater)
        dialogBinding.exportVaultOnlyHeaderRadio.isChecked = true
        dialogBinding.exportVaultEntireFolderRadio.isChecked = false

        val selectOnlyHeader = {
            dialogBinding.exportVaultOnlyHeaderRadio.isChecked = true
            dialogBinding.exportVaultEntireFolderRadio.isChecked = false
        }
        val selectEntireFolder = {
            dialogBinding.exportVaultOnlyHeaderRadio.isChecked = false
            dialogBinding.exportVaultEntireFolderRadio.isChecked = true
        }

        dialogBinding.exportVaultOnlyHeaderRow.setOnClickListener { selectOnlyHeader() }
        dialogBinding.exportVaultOnlyHeaderRadio.setOnClickListener { selectOnlyHeader() }
        dialogBinding.exportVaultEntireFolderRadio.setOnClickListener { selectEntireFolder() }

        bindSettingInfoIcon(
            iconView = dialogBinding.exportVaultOnlyHeaderInfo,
            titleResId = R.string.export_vault_only_header,
            messageResId = R.string.export_vault_only_header_info
        )

        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                // Export is a copy, so the vault password is not needed. A device-owner check
                // keeps casual access to an unlocked phone from extracting the vault files.
                requireDeviceOwner {
                    promptExportEncryptedVault(
                        onlyVaultHeader = dialogBinding.exportVaultOnlyHeaderRadio.isChecked
                    ) {
                        setupVaultSettings()
                    }
                }
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                setupDialogStuff(dialogBinding.root, this, R.string.export_encrypted_vault)
            }
    }
}

private fun SettingsActivity.setupSyncVault() {
    bindSettingInfoIcon(
        iconView = binding.settingsSyncVaultInfo,
        titleResId = R.string.sync_vault_from_external,
        messageResId = R.string.sync_vault_select_source_prompt
    )
    binding.settingsSyncVaultHolder.setOnClickListener {
        if (!doesVaultExist()) return@setOnClickListener
        // While the person is inside an encrypted album the key already sits in RAM and stays there until they
        // leave that album, so no extra prompt is needed. Outside that session every visit must re-enter the
        // password first, and the key is wiped again when the flow ends.
        val inEncryptedAlbum = config.temporarilyShowHidden
        promptSyncVaultFromExternal(
            forcePasswordPrompt = !inEncryptedAlbum,
            onSynced = { setupVaultSettings() }
        )
    }
}

private fun SettingsActivity.setupChangeVaultPassword() {
    binding.settingsChangeVaultPasswordHolder.setOnClickListener {
        if (!doesVaultExist()) return@setOnClickListener

        val currentType = CryptoManager.getVaultProtectionType(this)
            ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
        VaultSecurityDialog(
            activity = this,
            requiredHash = config.hiddenPasswordHash,
            showTabIndex = currentType,
            minimalist = false,
            allowBiometrics = false
        ) { _, _, authSuccess ->
            if (authSuccess) {
                promptChangeVaultPassword()
            }
        }
    }
}

private fun SettingsActivity.promptChangeVaultPassword() {
    VaultSecurityDialog(
        activity = this,
        requiredHash = "",
        showTabIndex = SHOW_ALL_TABS,
        minimalist = false,
        isChangePasswordMode = true,
        onCancelCallback = {
            VaultSessionManager.lockVaultIfOutsideSession(this)
        }
    ) { _, _, changeSuccess ->
        if (changeSuccess) {
            updateVaultBiometricSwitch()
            toast(R.string.password_changed_successfully)
            // 3a → 3b akışı unlockedMasterKey'i kullanır. Anahtarı ancak teklif
            // tamamlandıktan (veya Geri ile atlandıktan) sonra RAM'den sil; aksi
            // halde completeBiometricSetup() sessizce false döner.
            showVaultPasswordSavedDialog {
                VaultSessionManager.lockVaultIfOutsideSession(this)
            }
        } else {
            VaultSessionManager.lockVaultIfOutsideSession(this)
        }
    }
}

private fun SettingsActivity.setupVaultPasswordReminder() {
    fun updateValue() {
        binding.settingsVaultPasswordReminder.text =
            formatPasswordReminderInterval(this, config.vaultPasswordReminderDays)
    }
    updateValue()
    binding.settingsVaultPasswordReminderHolder.setOnClickListener {
        val days = Config.PASSWORD_REMINDER_DAYS_OPTIONS
        val items = days.map { value ->
            RadioItem(value, formatPasswordReminderInterval(this, value))
        } as ArrayList<RadioItem>

        RadioGroupDialog(
            activity = this@setupVaultPasswordReminder,
            items = items,
            checkedItemId = config.vaultPasswordReminderDays,
            titleId = R.string.vault_password_reminder_dialog_title,
        ) {
            config.vaultPasswordReminderDays = it as Int
            resetPasswordReminderState(config)
            updateValue()
        }
    }
}

private fun SettingsActivity.setupEmptyVault() {
    binding.settingsEmptyVaultHolder.setOnClickListener {
        if (!doesVaultExist()) return@setOnClickListener

        requireVaultPassword(allowBiometrics = false) {
            DeleteSecretsDialog(
                this,
                getString(R.string.empty_encrypted_vault_warning),
                titleId = R.string.empty_encrypted_vault,
            ) {
                ensureBackgroundThread {
                    CryptoManager.deleteExistingVault(this)
                    runOnUiThread {
                        toast(R.string.encrypted_vault_emptied)
                        setupVaultSettings()
                    }
                }
            }
        }
    }
}
