package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.biometric.auth.AuthPromptHost
import org.fossify.commons.activities.EdgeToEdgeActivity
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getThemeId
import org.fossify.commons.extensions.overrideActivityTransition
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.interfaces.HashListener
import org.fossify.commons.interfaces.SecurityTab
import org.fossify.gallery.App
import org.fossify.gallery.databinding.ActivityAppUnlockBinding
import org.fossify.gallery.extensions.styleBiometricIdTab

/**
 * Pure-Kotlin replacement for Commons' AppLockActivity.
 *
 * Always uses `tab_biometric_id` (AndroidX Biometric) for [PROTECTION_FINGERPRINT] on every API
 * level (API 26–36), eliminating Commons' legacy Reprint fingerprint fallback on API < 30.
 */
class AppUnlockActivity : EdgeToEdgeActivity(), HashListener {

    private lateinit var binding: ActivityAppUnlockBinding
    private var biometricTabView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // No fade: the privacy curtain must stay opaque until this lock screen is fully drawn.
        overrideActivityTransition(0, 0, false)
        setupTheme()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }

        binding = ActivityAppUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(
            padTopSystem = listOf(binding.root),
            padBottomSystem = listOf(binding.root)
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                appLockManager.lock()
                finishAffinity()
            }
        })

        setupSecurityTab()
    }

    override fun onResume() {
        super.onResume()
        if (appLockManager.isLocked()) {
            setupTheme()
            biometricTabView?.let { styleBiometricIdTab(it, this) }
        } else {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        overrideActivityTransition(0, 0, false)
    }

    override fun finish() {
        super.finish()
        overrideActivityTransition(0, 0, true)
    }

    override fun onDestroy() {
        if (isFinishing) {
            App.markAppLockGateClosed()
        }
        super.onDestroy()
    }

    override fun receivedHash(hash: String, type: Int) {
        appLockManager.unlock()
        App.markAppUnlocked()
        setResult(RESULT_OK)
        finish()
    }

    private fun setupTheme() {
        setTheme(getThemeId(showTransparentTop = true))
        window.decorView.setBackgroundColor(getProperBackgroundColor())
    }

    private fun setupSecurityTab() {
        val protectionType = baseConfig.appProtectionType
        val layoutId = when (protectionType) {
            PROTECTION_PATTERN -> org.fossify.commons.R.layout.tab_pattern
            PROTECTION_PIN -> org.fossify.commons.R.layout.tab_pin
            PROTECTION_FINGERPRINT -> org.fossify.commons.R.layout.tab_biometric_id
            else -> org.fossify.commons.R.layout.tab_pin
        }
        val tabView = LayoutInflater.from(this).inflate(layoutId, binding.appUnlockContainer, false)
        if (protectionType == PROTECTION_FINGERPRINT) {
            biometricTabView = tabView
            styleBiometricIdTab(tabView, this)
        }
        binding.appUnlockContainer.addView(
            tabView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val securityTab = tabView as SecurityTab
        securityTab.initTab(
            requiredHash = baseConfig.appPasswordHash,
            listener = this,
            scrollView = binding.appUnlockScrollview,
            biometricPromptHost = AuthPromptHost(this),
            showBiometricAuthentication = protectionType == PROTECTION_FINGERPRINT
        )
        securityTab.visibilityChanged(true)
    }
}
