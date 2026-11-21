package org.fossify.gallery.activities

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityVaultLockBinding
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.EXTRA_EXITED_FROM_VAULT
import org.fossify.gallery.helpers.OnboardingUiHelper
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.VaultAuthInputHelper
import org.fossify.gallery.helpers.VaultLockEmblemHelper
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.VaultShutterAnimator
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
    private var haloPulseAnimator: Animator? = null
    private var shutterAnimator: VaultShutterAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VaultSessionManager.setLockScreenOpen(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityVaultLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val palette = AppearanceManager.resolve(this, config)

        OnboardingUiHelper.applySystemBars(window, palette)
        binding.vaultLockRoot.setBackgroundColor(palette.background)
        binding.vaultLockBackdrop.applyPalette(palette)
        binding.vaultLockRoot.filterTouchesWhenObscured = true

        // Apply system bar, display cutout, and IME insets cleanly to scroll container and top-left back button
        val baseScrollLeft = binding.vaultLockScroll.paddingLeft
        val baseScrollTop = binding.vaultLockScroll.paddingTop
        val baseScrollRight = binding.vaultLockScroll.paddingRight
        val baseScrollBottom = binding.vaultLockScroll.paddingBottom
        val backParams = binding.vaultLockBackBtn.layoutParams as android.widget.FrameLayout.LayoutParams
        val baseBackTop = backParams.topMargin
        val baseBackStart = backParams.marginStart
        ViewCompat.setOnApplyWindowInsetsListener(binding.vaultLockRoot) { _, insets ->
            val safeBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            binding.vaultLockScroll.setPadding(
                baseScrollLeft + safeBars.left,
                baseScrollTop + safeBars.top,
                baseScrollRight + safeBars.right,
                baseScrollBottom + maxOf(safeBars.bottom, imeBottom)
            )
            val horizontalStartInset = if (binding.vaultLockRoot.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                safeBars.right
            } else {
                safeBars.left
            }
            backParams.topMargin = baseBackTop + safeBars.top
            backParams.marginStart = baseBackStart + horizontalStartInset
            binding.vaultLockBackBtn.layoutParams = backParams
            insets
        }
        ViewCompat.requestApplyInsets(binding.vaultLockRoot)

        binding.vaultLockBackBtn.applyColorFilter(palette.text)
        binding.vaultLockBackBtn.filterTouchesWhenObscured = true
        binding.vaultLockBackBtn.setOnClickListener {
            exitVaultToNormalGallery()
        }

        val securityBinding = binding.vaultSecurityContent
        securityBinding.vaultLockEmblem.beVisible()
        securityBinding.vaultLockTitle.beVisible()
        securityBinding.vaultLockTitle.setTextColor(palette.text)
        VaultLockEmblemHelper.applyStyle(
            context = this,
            palette = palette,
            haloView = securityBinding.vaultLockHalo,
            coreView = securityBinding.vaultLockEmblemCore,
            lockView = securityBinding.vaultUnlockEmblem
        )
        haloPulseAnimator = VaultLockEmblemHelper.startHaloPulse(securityBinding.vaultLockHalo)

        securityBinding.vaultTabs.beGone()
        securityBinding.vaultStatusLabel.beGone()
        securityBinding.vaultPasswordInputLayout.hint = getString(R.string.enter_password)

        currentType = CryptoManager.getVaultProtectionType(this)
            ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN

        inputHelper = VaultAuthInputHelper(this, securityBinding)
        updateTextColors(binding.vaultLockRoot)
        inputHelper.applyThemeColors(translucentSurfaces = true)
        inputHelper.applyTouchFilter()
        inputHelper.setupPinInput(::handleSecretSubmission)
        inputHelper.setupPasswordInput(::handleSecretSubmission)
        inputHelper.showProtectionType(currentType, window)
        inputHelper.setupBiometricButton(enabled = true) {
            onAuthenticated()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consume the system back gesture/key without leaving this gate. The only in-app
                // exit is the top-left arrow (exitVaultToNormalGallery), which locks the vault and
                // returns to the gallery. The arrow is disabled while the KDF runs, so there is no
                // window in which the person can leave and still have a late unlock applied.
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
        haloPulseAnimator?.cancel()
        haloPulseAnimator = null
        shutterAnimator?.cancel()
        shutterAnimator = null
        VaultSessionManager.setLockScreenOpen(false)
        super.onDestroy()
    }

    private fun handleSecretSubmission(secret: CharArray) {
        inputHelper.unlockWithSecretAsync(
            secret = secret,
            currentType = currentType,
            onPreExecute = {
                binding.vaultLockBackBtn.isEnabled = false
                // Arrow colour is set from palette.text in onCreate; only its availability changes.
                binding.vaultLockBackBtn.alpha = VaultAuthInputHelper.DISABLED_BACK_ALPHA
                binding.vaultLockBackBtn.contentDescription = getString(R.string.unlocking_encrypted_vault)
            },
            onPostExecute = {
                binding.vaultLockBackBtn.isEnabled = true
                binding.vaultLockBackBtn.alpha = 1f
                binding.vaultLockBackBtn.contentDescription = getString(org.fossify.commons.R.string.back)
            }
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
        if (!ValueAnimator.areAnimatorsEnabled()) {
            VaultSessionManager.onVaultReauthenticated(this)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }

        haloPulseAnimator?.cancel()
        haloPulseAnimator = null
        binding.vaultLockBackBtn.isEnabled = false
        val securityBinding = binding.vaultSecurityContent
        val isPassword = currentType == PROTECTION_PASSWORD
        securityBinding.vaultUnlockSweep.visibility = View.INVISIBLE
        securityBinding.vaultUnlockSweepPassword.visibility = View.INVISIBLE
        val animator = VaultShutterAnimator(
            lock = securityBinding.vaultUnlockEmblem,
            dots = if (isPassword) null else securityBinding.vaultPinDots,
            sweep = if (isPassword) securityBinding.vaultUnlockSweepPassword else securityBinding.vaultUnlockSweep,
            panel = binding.vaultLockContainer,
            pulseView = if (isPassword) securityBinding.vaultPasswordInputLayout else null,
            sweepColor = AppearanceManager.resolve(this, config).primary,
        )
        shutterAnimator = animator
        animator.play {
            VaultSessionManager.onVaultReauthenticated(this)
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
