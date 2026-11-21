package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
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
import org.fossify.gallery.databinding.ActivityAppUnlockBinding

/**
 * Pure-Kotlin replacement for Commons' AppLockActivity.
 *
 * Always uses `tab_biometric_id` (AndroidX Biometric) for [PROTECTION_FINGERPRINT] on every API
 * level (API 26–36), eliminating Commons' legacy Reprint fingerprint fallback on API < 30.
 */
class AppUnlockActivity : EdgeToEdgeActivity(), HashListener {

    private lateinit var binding: ActivityAppUnlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        overrideActivityTransition(
            org.fossify.commons.R.anim.fadein,
            org.fossify.commons.R.anim.fadeout,
            false
        )
        setupTheme()
        super.onCreate(savedInstanceState)

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
        } else {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        overrideActivityTransition(
            org.fossify.commons.R.anim.fadein,
            org.fossify.commons.R.anim.fadeout,
            false
        )
    }

    override fun finish() {
        super.finish()
        overrideActivityTransition(
            org.fossify.commons.R.anim.fadein,
            org.fossify.commons.R.anim.fadeout,
            true
        )
    }

    override fun receivedHash(hash: String, type: Int) {
        appLockManager.unlock()
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
