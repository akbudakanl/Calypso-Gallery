package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityVaultLockBinding
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.EXTRA_EXITED_FROM_VAULT
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.VaultAuthInputHelper
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.VaultViewPolicy
import org.fossify.gallery.helpers.canUnlockWithBiometrics
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.getVaultProtectionType
import org.fossify.gallery.helpers.isDeviceLocked

/**
 * Isolated full-screen lock gate shown when the user returns from background or turns the screen
 * back on while the vault is unlocked (inside the vault or keeping encrypted albums visible).
 */
class VaultLockActivity : SimpleActivity() {

    private lateinit var binding: ActivityVaultLockBinding
    private lateinit var inputHelper: VaultAuthInputHelper
    private var currentType: Int = PROTECTION_PIN
    private var autoBiometricTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VaultSessionManager.setLockScreenOpen(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityVaultLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val backgroundColor = getProperBackgroundColor()
        val textColor = getProperTextColor()
        binding.vaultLockRoot.setBackgroundColor(backgroundColor)
        binding.vaultLockRoot.filterTouchesWhenObscured = true
        binding.vaultLockBackBtn.applyColorFilter(textColor)
        binding.vaultLockBackBtn.filterTouchesWhenObscured = true
        binding.vaultLockBackBtn.setOnClickListener {
            exitVaultToNormalGallery()
        }
        binding.vaultLockIcon.applyColorFilter(textColor)
        binding.vaultLockTitle.setTextColor(textColor)

        val securityBinding = binding.vaultSecurityContent
        securityBinding.vaultTabs.beGone()
        securityBinding.vaultStatusLabel.beGone()
        securityBinding.vaultPasswordInputLayout.hint = getString(R.string.enter_password)

        currentType = CryptoManager.getVaultProtectionType(this)
            ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN

        inputHelper = VaultAuthInputHelper(this, securityBinding)
        updateTextColors(binding.vaultLockRoot)
        inputHelper.applyThemeColors()
        inputHelper.applyTouchFilter()
        inputHelper.setupPinInput(::handleSecretSubmission)
        inputHelper.setupPasswordInput(::handleSecretSubmission)
        inputHelper.showProtectionType(currentType, window)
        inputHelper.setupBiometricButton(enabled = true) {
            onAuthenticated()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun isVaultAssistProtectionActive(): Boolean = true

    override fun onResume() {
        super.onResume()
        inputHelper.resetBiometricPromptState()
        applyVaultScreenProtection(true)
        maybeAutoPromptBiometricUnlock()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            maybeAutoPromptBiometricUnlock()
        }
    }

    private fun maybeAutoPromptBiometricUnlock() {
        if (autoBiometricTriggered || isFinishing || isDestroyed) return
        if (!config.autoBiometricPromptOnVaultReturn) return
        if (!hasWindowFocus() || CryptoManager.isDeviceLocked(this)) return
        if (!CryptoManager.canUnlockWithBiometrics(this)) return
        autoBiometricTriggered = true
        binding.root.post {
            if (!isFinishing && !isDestroyed && hasWindowFocus() && !CryptoManager.isDeviceLocked(this)) {
                inputHelper.promptBiometricUnlock(isAutoTrigger = true) {
                    onAuthenticated()
                }
            }
        }
    }

    override fun onDestroy() {
        VaultSessionManager.setLockScreenOpen(false)
        super.onDestroy()
    }

    private fun handleSecretSubmission(secret: CharArray) {
        inputHelper.unlockWithSecretAsync(
            secret = secret,
            currentType = currentType,
            onPreExecute = { binding.vaultLockBackBtn.isEnabled = false },
            onPostExecute = { binding.vaultLockBackBtn.isEnabled = true }
        ) { success ->
            if (success) {
                onAuthenticated()
            } else {
                binding.vaultSecurityContent.vaultStatusLabel.beGone()
            }
        }
    }

    private fun exitVaultToNormalGallery() {
        hideKeyboard()
        config.temporarilyShowHidden = false
        config.temporarilyShowVaultExcluded = false
        config.resetTempDeleteConfirmation()
        CryptoManager.lockVault()
        CryptoManager.clearAppImageCaches(this)
        VaultSessionManager.onVaultExited()
        VaultSessionManager.markUnlockDeclined()
        applyVaultScreenProtection(false)

        val showFilesView = VaultViewPolicy.shouldShowFilesOnAppLaunch(config.homeScreenViewOnLaunch)
        config.showAll = showFilesView
        val targetIntent = if (showFilesView) {
            Intent(this, MediaActivity::class.java).apply {
                putExtra(DIRECTORY, "")
                putExtra(EXTRA_EXITED_FROM_VAULT, true)
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_EXITED_FROM_VAULT, true)
            }
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(targetIntent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun onAuthenticated() {
        VaultSessionManager.onVaultReauthenticated(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
