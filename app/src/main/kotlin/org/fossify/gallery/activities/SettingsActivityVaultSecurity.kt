package org.fossify.gallery.activities

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.App
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.AppSecurityDialog
import org.fossify.gallery.dialogs.DeleteSecretsDialog
import org.fossify.gallery.dialogs.VaultBiometricOfferDialog
import org.fossify.gallery.dialogs.VaultNoticeDialog
import org.fossify.gallery.dialogs.VaultSecurityDialog
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.showBiometricPasswordNoteDialog
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.VaultArchiveManager
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.biometricUnavailableReason
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.clearBiometricKey
import org.fossify.gallery.helpers.completeBiometricSetup
import org.fossify.gallery.helpers.getBiometricEncryptCipher
import org.fossify.gallery.helpers.getVaultProtectionType

internal fun SettingsActivity.setupVaultBiometric() {
    VaultBiometricOfferDialog.onBiometricChanged = {
        runOnUiThread { setupVaultSettings() }
    }
    //Visibility is owned by setupVaultSettings(): the row stays visible whenever a vault
    //exists so the info icon can explain why it is passive. Do not re-apply the old hardware/key gate here.
    binding.settingsVaultBiometricInfo.setOnClickListener { showBiometricUnavailableDialog() }
    updateVaultBiometricSwitch()
    binding.settingsVaultBiometricHolder.setOnClickListener {
        if (!doesVaultExist()) {
            toast(R.string.enable_hidden_protection_first_prompt)
            return@setOnClickListener
        }

        if (config.isVaultBiometricEnabled) {
            // Turning biometrics off must be confirmed by biometrics (or device credential):
            // the person may rely on it because they forgot the vault password.
            confirmDisableBiometric {
                CryptoManager.clearBiometricKey(this)
                config.temporarilyShowHidden = false
                config.temporarilyShowVaultExcluded = false
                CryptoManager.lockVault(this@setupVaultBiometric)
                CryptoManager.clearAppImageCaches(this)
                VaultSessionManager.onVaultExited()
                applyVaultScreenProtection(false)
                updateVaultBiometricSwitch()
            }
        } else {
            if (!biometricUsable) {
                showBiometricUnavailableDialog()
                return@setOnClickListener
            }

            val wasVaultUnlocked = CryptoManager.isVaultUnlocked
            val tabToShow = CryptoManager.getVaultProtectionType(this) ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
            VaultSecurityDialog(this, config.hiddenPasswordHash, tabToShow, minimalist = false) { _, _, success ->
                if (success) {
                    showBiometricEnrollmentPrompt(lockVaultAfter = !wasVaultUnlocked && !config.temporarilyShowHidden)
                }
            }
        }
    }
}

internal fun SettingsActivity.setupVaultBiometricAutoReturn() {
    binding.settingsVaultBiometricAutoReturn.isChecked = config.autoBiometricPromptOnVaultReturn
    binding.settingsVaultBiometricAutoReturnHolder.setOnClickListener {
        if (!doesVaultExist() || !config.isVaultBiometricEnabled) return@setOnClickListener
        // Kasa genelindeki güvenlik anahtarları gibi bu ayar da yeniden doğrulama ister.
        // Parola/PIN veya biyometrik ile açma ve kapatma aynı şekilde onaylanır.
        requireVaultPassword(verifyOnly = true) {
            binding.settingsVaultBiometricAutoReturn.toggle()
            config.autoBiometricPromptOnVaultReturn = binding.settingsVaultBiometricAutoReturn.isChecked
        }
    }
}

internal fun SettingsActivity.setupArchiveVault() {
    binding.settingsArchiveVaultHolder.setOnClickListener {
        if (!doesVaultExist() || VaultArchiveManager.isActiveVaultArchived(this)) return@setOnClickListener
        requireDeviceOwner {
            DeleteSecretsDialog(
                this,
                getString(R.string.archive_vault_confirm),
                titleId = R.string.archive_vault,
            ) {
                CryptoManager.lockVault(this@setupArchiveVault)
                val entry = VaultArchiveManager.archiveActiveVault(this)
                if (entry == null) {
                    toast(R.string.archive_vault_failed)
                    setupVaultSettings()
                    return@DeleteSecretsDialog
                }

                VaultNoticeDialog(
                    activity = this,
                    title = getString(R.string.archive_vault),
                    message = getString(R.string.archive_vault_success, humanizePath(entry.path)),
                    positiveText = getString(org.fossify.commons.R.string.ok),
                    negativeText = getString(R.string.copy_path),
                    cancelable = true,
                    onPositive = {},
                    onNegative = {
                        val clipboard = getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(getString(R.string.archive_vault), entry.path))
                        // Vault-derived path: clearKeys(context) must wipe it on lock.
                        CryptoManager.vaultClipboardTouched = true
                    },
                )

                // Arşivleme sonrası yalnızca kasa konumunu gösteren diyalog çıkar;
                // yeni şifreli kasa oluşturma diyaloğu açılmaz.
                setupVaultSettings()
            }
        }
    }
}

/**
 * Archiving is a move, not a deletion, so the vault password is not required. On a device with
 * a secure lock screen we still verify the person holding the phone: Class 3 biometric first,
 * with the device PIN/pattern/password as a fallback. With no secure lock screen there is no
 * owner signal to check, so the 10-second confirmation of DeleteSecretsDialog is the only gate.
 */
internal fun deviceOwnerAuthenticators(): Int {
    return if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) {
        // AndroidX Biometric's AuthenticatorUtils.isSupportedCombination() rejects
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL on API 28-29 (throws IllegalArgumentException).
        // These prompts are non-crypto device-owner checks that already permit DEVICE_CREDENTIAL.
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

internal fun SettingsActivity.requireDeviceOwner(onConfirmed: () -> Unit) {
    val keyguard = getSystemService(KeyguardManager::class.java)
    if (keyguard?.isDeviceSecure != true) {
        onConfirmed()
        return
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.device_owner_verify_title))
        .setSubtitle(getString(R.string.device_owner_verify_subtitle))
        .setAllowedAuthenticators(deviceOwnerAuthenticators())
        .build()
    BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onConfirmed()
            }
        }
    ).authenticate(promptInfo)
}

internal fun SettingsActivity.setupUnarchiveVault() {
    binding.settingsUnarchiveVaultHolder.setOnClickListener {
        val entry = VaultArchiveManager.activeArchivedEntry(this) ?: return@setOnClickListener
        if (!VaultArchiveManager.canUnarchive(this, entry)) {
            VaultNoticeDialog(
                activity = this,
                title = getString(R.string.unarchive_vault),
                message = getString(R.string.unarchive_vault_blocked),
                positiveText = getString(R.string.unarchive_vault_merge),
                negativeText = getString(org.fossify.commons.R.string.ok),
                cancelable = true,
                onPositive = {
                    VaultNoticeDialog(
                        activity = this,
                        title = getString(R.string.unarchive_vault_merge),
                        message = getString(R.string.unarchive_vault_merge_steps),
                        positiveText = getString(org.fossify.commons.R.string.ok),
                        cancelable = true,
                        onPositive = {},
                    )
                },
                onNegative = {},
            )
            return@setOnClickListener
        }

        requireDeviceOwner {
            DeleteSecretsDialog(
                this,
                getString(R.string.unarchive_vault_confirm),
                titleId = R.string.unarchive_vault,
            ) {
                val result = VaultArchiveManager.restoreArchivedVault(this, entry)
                if (result == VaultArchiveManager.UnarchiveResult.OK) {
                    toast(R.string.unarchive_vault_success)
                } else {
                    toast(R.string.archive_vault_failed)
                }
                setupVaultSettings()
            }
        }
    }
}

/** Reason dialog opened from the info icon (or from a tap on the passive row). */
private fun SettingsActivity.showBiometricUnavailableDialog() {
    // A toast would cut off the explanation; show it in a theme-aware dialog.
    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.vault_biometric_unavailable_title),
        message = getString(biometricUnavailableReason(biometricStatus, biometricKeyAvailable)),
        positiveText = getString(org.fossify.commons.R.string.ok),
        cancelable = true,
        onPositive = {},
    )
}

internal fun SettingsActivity.updateVaultBiometricSwitch() {
    val vaultExists = doesVaultExist()
    val hasStoragePermission = !isRPlus() || isExternalStorageManager()
    val biometricEnabled = vaultExists && config.isVaultBiometricEnabled
    // Passive (grey) while no strong biometric is usable. When the user later enables one in
    // Android settings, onResume re-evaluates this and the switch becomes active but stays
    // off unless the user explicitly turns it on.
    binding.settingsVaultBiometric.isEnabled = vaultExists && biometricUsable
    binding.settingsVaultBiometric.alpha = if (biometricUsable) 1.0f else 0.5f
    binding.settingsVaultBiometricHolder.alpha = if (vaultExists) 1.0f else 0.4f
    binding.settingsVaultBiometric.isChecked = biometricEnabled
    binding.settingsVaultBiometricAutoReturnHolder.beVisibleIf(
        hasStoragePermission && biometricEnabled && biometricUsable
    )
    binding.settingsVaultBiometricAutoReturn.isChecked = config.autoBiometricPromptOnVaultReturn

    // Info icon replaces the always-visible explanation text. No popup is shown on state
    // change; the user opens it deliberately.
    binding.settingsVaultBiometricInfo.beVisibleIf(vaultExists && !biometricUsable)
    if (!isSettingUpItems) {
        refreshRowDecorations()
    }
}

private fun SettingsActivity.confirmDisableBiometric(onConfirmed: () -> Unit) {
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.biometric_access_to_encrypted_vault))
        .setSubtitle(getString(R.string.biometric_disable_confirmation))
        .setAllowedAuthenticators(deviceOwnerAuthenticators())
        .build()
    BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onConfirmed()
        }
    ).authenticate(promptInfo)
}

private fun SettingsActivity.showBiometricEnrollmentPrompt(lockVaultAfter: Boolean) {
    fun launchSystemPrompt() {
        val cipher = CryptoManager.getBiometricEncryptCipher(this@showBiometricEnrollmentPrompt)
        if (cipher == null) {
            if (lockVaultAfter) {
                CryptoManager.lockVault(this@showBiometricEnrollmentPrompt)
                applyVaultScreenProtection(false)
            }
            toast(org.fossify.commons.R.string.an_error_occurred)
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_access_to_encrypted_vault))
            .setSubtitle(getString(R.string.biometric_setup_prompt))
            .setNegativeButtonText(getString(org.fossify.commons.R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authCipher = result.cryptoObject?.cipher
                    if (authCipher != null && CryptoManager.completeBiometricSetup(this@showBiometricEnrollmentPrompt, authCipher)) {
                        updateVaultBiometricSwitch()
                        toast(org.fossify.commons.R.string.fingerprint_setup_successfully)
                    } else {
                        CryptoManager.clearBiometricKey(this@showBiometricEnrollmentPrompt)
                        updateVaultBiometricSwitch()
                        toast(org.fossify.commons.R.string.an_error_occurred)
                    }
                    if (lockVaultAfter) {
                        CryptoManager.lockVault(this@showBiometricEnrollmentPrompt)
                        applyVaultScreenProtection(false)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    CryptoManager.clearBiometricKey(this@showBiometricEnrollmentPrompt)
                    if (lockVaultAfter) {
                        CryptoManager.lockVault(this@showBiometricEnrollmentPrompt)
                        applyVaultScreenProtection(false)
                    }
                    updateVaultBiometricSwitch()
                }
            }
        )
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    // Full explanation is shown in the themed card first (HTML screen 3b).
    showBiometricPasswordNoteDialog(
        title = getString(R.string.vault_biometric_offer_title),
        message = getString(R.string.vault_biometric_offer_message),
        note = getString(R.string.vault_biometric_setup_password_note),
        onContinue = { launchSystemPrompt() },
        onCancel = {
            // The key was promoted for enrollment; do not leave it in session RAM on cancel.
            VaultSessionManager.lockVaultIfOutsideSession(this@showBiometricEnrollmentPrompt)
            applyVaultScreenProtection(false)
        },
    )
}

internal fun SettingsActivity.setupExcludedItemPasswordProtection() {
    binding.settingsExcludedItemPasswordProtectionHolder.beGoneIf(isRPlus() && !isExternalStorageManager())
    binding.settingsExcludedItemPasswordProtection.isChecked = config.isExcludedPasswordProtectionOn
    binding.settingsExcludedItemPasswordProtectionHolder.setOnClickListener {
        val tabToShow = if (config.isExcludedPasswordProtectionOn) config.excludedProtectionType else SHOW_ALL_TABS
        AppSecurityDialog(this, config.excludedPasswordHash, tabToShow) { hash, type, success ->
            if (success) {
                val hasPasswordProtection = config.isExcludedPasswordProtectionOn
                binding.settingsExcludedItemPasswordProtection.isChecked = !hasPasswordProtection
                config.isExcludedPasswordProtectionOn = !hasPasswordProtection
                config.excludedPasswordHash = if (hasPasswordProtection) "" else hash
                config.excludedProtectionType = type

                if (config.isExcludedPasswordProtectionOn) {
                    val confirmationTextId = if (config.excludedProtectionType == PROTECTION_FINGERPRINT)
                        org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                    ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                }
            }
        }
    }
}

internal fun SettingsActivity.setupAppPasswordProtection() {
    binding.settingsAppPasswordProtection.isChecked = config.isAppPasswordProtectionOn
    updateAppLockModeRow()

    binding.settingsAppPasswordProtectionHolder.setOnClickListener {
        val tabToShow = if (config.isAppPasswordProtectionOn) config.appProtectionType else SHOW_ALL_TABS
        AppSecurityDialog(this, config.appPasswordHash, tabToShow) { hash, type, success ->
            if (success) {
                val hasPasswordProtection = config.isAppPasswordProtectionOn
                binding.settingsAppPasswordProtection.isChecked = !hasPasswordProtection
                config.isAppPasswordProtectionOn = !hasPasswordProtection
                config.appPasswordHash = if (hasPasswordProtection) "" else hash
                config.appProtectionType = type

                if (config.isAppPasswordProtectionOn) {
                    App.markAppUnlocked()
                    val confirmationTextId = if (config.appProtectionType == PROTECTION_FINGERPRINT)
                        org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                    ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                }
                updateAppLockModeRow()
            }
        }
    }

    binding.settingsSecurityCard.findViewById<android.view.View>(R.id.settings_app_lock_mode_holder)?.setOnClickListener {
        val items = arrayListOf(
            RadioItem(Config.APP_LOCK_MODE_COLD_START, getString(R.string.app_lock_mode_cold_start)),
            RadioItem(Config.APP_LOCK_MODE_EVERY_ENTRY, getString(R.string.app_lock_mode_every_entry))
        )
        RadioGroupDialog(
            activity = this@setupAppPasswordProtection,
            items = items,
            checkedItemId = config.appLockMode,
            titleId = R.string.app_lock_mode_label
        ) {
            config.appLockMode = it as Int
            updateAppLockModeRow()
        }
    }
}

private fun SettingsActivity.updateAppLockModeRow() {
    val holder = binding.settingsSecurityCard.findViewById<android.view.View>(R.id.settings_app_lock_mode_holder)
    val valueView = binding.settingsSecurityCard.findViewById<MyTextView>(R.id.settings_app_lock_mode)
    holder?.beVisibleIf(config.isAppPasswordProtectionOn)
    valueView?.text = getString(
        if (config.appLockMode == Config.APP_LOCK_MODE_EVERY_ENTRY) {
            R.string.app_lock_mode_every_entry
        } else {
            R.string.app_lock_mode_cold_start
        }
    )
}

internal fun SettingsActivity.setupFileDeletionPasswordProtection() {
    binding.settingsFileDeletionPasswordProtection.isChecked = config.isDeletePasswordProtectionOn
    binding.settingsFileDeletionPasswordProtectionHolder.setOnClickListener {
        val tabToShow = if (config.isDeletePasswordProtectionOn) config.deleteProtectionType else SHOW_ALL_TABS
        AppSecurityDialog(this, config.deletePasswordHash, tabToShow) { hash, type, success ->
            if (success) {
                val hasPasswordProtection = config.isDeletePasswordProtectionOn
                binding.settingsFileDeletionPasswordProtection.isChecked = !hasPasswordProtection
                config.isDeletePasswordProtectionOn = !hasPasswordProtection
                config.deletePasswordHash = if (hasPasswordProtection) "" else hash
                config.deleteProtectionType = type

                if (config.isDeletePasswordProtectionOn) {
                    val confirmationTextId = if (config.deleteProtectionType == PROTECTION_FINGERPRINT)
                        org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                    ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                }
            }
        }
    }
}
