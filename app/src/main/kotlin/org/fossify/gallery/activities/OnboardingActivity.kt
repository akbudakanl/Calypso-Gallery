package org.fossify.gallery.activities

import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import org.fossify.commons.extensions.canManageMedia
import org.fossify.commons.extensions.hasAllPermissions
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.openDeviceSettings
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityOnboardingBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchGrantAllFilesIntent
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.OnboardingUiHelper
import org.fossify.gallery.helpers.getPermissionsToRequest

/**
 * Full-screen onboarding and permission flow with 3 distinct screens:
 *  1. [MODE_WELCOME] — First-launch Calypso welcome with animated hero logo.
 *  2. [MODE_MEDIA_PERMISSION_REQUIRED] — Shown when media permissions are denied or partial;
 *     persists across app restarts so users never bounce back to Welcome.
 *  3. [MODE_ALL_FILES_ACCESS] — Shown after media permissions are granted when All Files Access
 *     is recommended on Android 11+.
 */
class OnboardingActivity : SimpleActivity() {

    companion object {
        const val EXTRA_START_MODE = "extra_start_mode"
        const val EXTRA_ALL_FILES_HANDLED = "extra_all_files_handled"

        const val MODE_WELCOME = 0
        const val MODE_MEDIA_PERMISSION_REQUIRED = 1
        const val MODE_ALL_FILES_ACCESS = 2

        private const val STATE_CURRENT_MODE = "state_current_mode"

        fun createIntent(context: Context, startMode: Int): Intent {
            return Intent(context, OnboardingActivity::class.java).apply {
                putExtra(EXTRA_START_MODE, startMode)
            }
        }
    }

    private val binding by viewBinding(ActivityOnboardingBinding::inflate)
    private var currentMode = MODE_WELCOME
    private var activePulseAnimator: Animator? = null
    private var allFilesHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val palette = AppearanceManager.resolve(this, config)
        OnboardingUiHelper.applySystemBars(window, palette)
        binding.onboardingContainer.setBackgroundColor(palette.background)

        OnboardingUiHelper.applyInsets(
            binding.paneWelcome.onboardingWelcomeScroll,
            binding.paneWelcome.onboardingWelcomeFooter
        )
        OnboardingUiHelper.applyInsets(
            binding.paneMediaPermission.onboardingMediaScroll,
            binding.paneMediaPermission.onboardingMediaFooter
        )
        OnboardingUiHelper.applyInsets(
            binding.paneAllFiles.onboardingAllFilesScroll,
            binding.paneAllFiles.onboardingAllFilesFooter
        )

        OnboardingUiHelper.styleWelcomePane(this, binding.paneWelcome, palette)
        OnboardingUiHelper.styleMediaPermissionPane(this, binding.paneMediaPermission, palette)?.cancel()
        OnboardingUiHelper.styleAllFilesPane(this, binding.paneAllFiles, palette)?.cancel()

        setupClickListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentMode) {
                    MODE_ALL_FILES_ACCESS -> {
                        config.avoidShowingAllFilesPrompt = true
                        allFilesHandled = true
                        finishWithSuccess()
                    }
                    else -> {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            }
        })

        val initialMode = savedInstanceState?.getInt(STATE_CURRENT_MODE)
            ?: intent.getIntExtra(EXTRA_START_MODE, MODE_WELCOME)

        showMode(initialMode, animateTransition = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_MODE, currentMode)
    }

    override fun onResume() {
        super.onResume()
        when (currentMode) {
            MODE_MEDIA_PERMISSION_REQUIRED -> {
                if (hasAllPermissions(getPermissionsToRequest())) {
                    config.showPermissionRationale = false
                    advanceAfterMediaGranted()
                }
            }
            MODE_ALL_FILES_ACCESS -> {
                if (!shouldShowAllFilesStep()) {
                    allFilesHandled = true
                    finishWithSuccess()
                }
            }
        }
    }

    override fun onDestroy() {
        activePulseAnimator?.cancel()
        activePulseAnimator = null
        binding.paneWelcome.onboardingHeroLogo.stopAnimations()
        super.onDestroy()
    }

    private fun setupClickListeners() {
        binding.paneWelcome.onboardingWelcomeContinueBtn.setOnClickListener {
            if (isAskingPermissions) return@setOnClickListener
            config.hasSeenOnboarding = true
            if (hasAllPermissions(getPermissionsToRequest())) {
                advanceAfterMediaGranted()
            } else {
                handlePartialMediaPermissions(getPermissionsToRequest(), force = true) { granted ->
                    if (granted) {
                        config.showPermissionRationale = false
                        advanceAfterMediaGranted()
                    } else {
                        config.showPermissionRationale = true
                        showMode(MODE_MEDIA_PERMISSION_REQUIRED, animateTransition = true)
                    }
                }
            }
        }

        binding.paneMediaPermission.onboardingMediaSettingsBtn.setOnClickListener {
            openDeviceSettings()
        }

        binding.paneMediaPermission.onboardingMediaCloseBtn.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.paneAllFiles.onboardingAllFilesGrantBtn.setOnClickListener {
            launchGrantAllFilesIntent()
        }

        binding.paneAllFiles.onboardingAllFilesSkipBtn.setOnClickListener {
            config.avoidShowingAllFilesPrompt = true
            allFilesHandled = true
            finishWithSuccess()
        }
    }

    private fun advanceAfterMediaGranted() {
        if (shouldShowAllFilesStep()) {
            showMode(MODE_ALL_FILES_ACCESS, animateTransition = true)
        } else {
            allFilesHandled = true
            finishWithSuccess()
        }
    }

    private fun shouldShowAllFilesStep(): Boolean {
        return isRPlus() &&
            resources.getBoolean(R.bool.require_all_files_access) &&
            !canManageMedia() &&
            !isExternalStorageManager() &&
            !Environment.isExternalStorageManager() &&
            !config.avoidShowingAllFilesPrompt
    }

    private fun showMode(mode: Int, animateTransition: Boolean) {
        currentMode = mode
        activePulseAnimator?.cancel()
        activePulseAnimator = null

        val palette = AppearanceManager.resolve(this, config)
        val welcomeView = binding.paneWelcome.root
        val mediaView = binding.paneMediaPermission.root
        val allFilesView = binding.paneAllFiles.root

        val targetView = when (mode) {
            MODE_WELCOME -> welcomeView
            MODE_MEDIA_PERMISSION_REQUIRED -> mediaView
            MODE_ALL_FILES_ACCESS -> allFilesView
            else -> welcomeView
        }

        listOf(welcomeView, mediaView, allFilesView).forEach { pane ->
            if (pane != targetView) {
                pane.animate().cancel()
                pane.isVisible = false
            }
        }

        if (mode != MODE_WELCOME) {
            binding.paneWelcome.onboardingHeroLogo.stopAnimations()
        }

        targetView.isVisible = true
        if (animateTransition) {
            targetView.alpha = 0f
            targetView.animate().alpha(1f).setDuration(220L).start()
        } else {
            targetView.alpha = 1f
        }

        when (mode) {
            MODE_WELCOME -> {
                OnboardingUiHelper.animateWelcomeEntrance(this, binding.paneWelcome)
            }
            MODE_MEDIA_PERMISSION_REQUIRED -> {
                activePulseAnimator = OnboardingUiHelper.styleMediaPermissionPane(this, binding.paneMediaPermission, palette)
            }
            MODE_ALL_FILES_ACCESS -> {
                activePulseAnimator = OnboardingUiHelper.styleAllFilesPane(this, binding.paneAllFiles, palette)
            }
        }
    }

    private fun finishWithSuccess() {
        val data = Intent().putExtra(EXTRA_ALL_FILES_HANDLED, allFilesHandled)
        setResult(RESULT_OK, data)
        finish()
    }
}
