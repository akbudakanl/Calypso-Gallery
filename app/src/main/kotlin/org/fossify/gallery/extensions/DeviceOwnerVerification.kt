package org.fossify.gallery.extensions

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.fossify.gallery.R

/**
 * Device-owner check for changes to the vault *configuration* (switching or archiving
 * vaults). It never unlocks vault content. Devices without a secure lock have no owner
 * signal to check, so the guard is intentionally skipped there.
 *
 * Mirrors SettingsActivity.requireDeviceOwner(): the same API 28-29 authenticator
 * combination is used to avoid AndroidX's invalid STRONG | CREDENTIAL combination.
 */
fun FragmentActivity.verifyDeviceOwner(onConfirmed: () -> Unit) {
    val keyguard = getSystemService(KeyguardManager::class.java)
    if (keyguard?.isDeviceSecure != true) {
        onConfirmed()
        return
    }
    val authenticators = if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.device_owner_verify_title))
        .setSubtitle(getString(R.string.device_owner_verify_subtitle))
        .setAllowedAuthenticators(authenticators)
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
