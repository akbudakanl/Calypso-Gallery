package org.fossify.gallery.dialogs

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.showBiometricPasswordNoteDialog
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.completeBiometricSetup
import org.fossify.gallery.helpers.ensureBiometricKeyAvailable
import org.fossify.gallery.helpers.getBiometricEncryptCipher
import org.fossify.gallery.helpers.readVaultKdfParams

/**
 * One-time biometric setup shown right after the first vault is created / the password changes,
 * when a Class 3 biometric is enrolled. It goes straight to the themed 3b card; cancelling or
 * closing it leaves biometrics off and the prompt is not repeated until the next vault setup.
 */
object VaultBiometricOfferDialog {
    /** Set by Settings so the toggle reflects enrolment immediately. */
    var onBiometricChanged: (() -> Unit)? = null

    fun showIfNeeded(activity: Activity) {
        val config = activity.config
        if (config.vaultBiometricOfferShown) return
        if (CryptoManager.readVaultKdfParams(activity) == null) return

        // Only offer enrollment when a strong biometric is actually enrolled and a hardware key
        // can be created. Otherwise say nothing during setup: no note, no config change. The
        // user can discover the state later in Settings (passive row + info icon). The offer is
        // not marked as shown, so a later creation re-checks.
        if (!CryptoManager.ensureBiometricKeyAvailable(activity)) return

        // Mark and default to off before showing: exiting without an answer counts as a decline.
        config.vaultBiometricOfferShown = true
        config.isVaultBiometricEnabled = false
        config.vaultBiometricWrappedBundle = ""

        // Go straight to the themed 3b card — the old platform "offer" dialog duplicated the
        // same explanation and is no longer shown.
        startEnrollment(activity)
    }

    private fun startEnrollment(activity: Activity) {
        val host = activity as? FragmentActivity ?: return

        fun launchSystemPrompt() {
            val cipher = CryptoManager.getBiometricEncryptCipher() ?: return
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.vault_biometric_offer_title))
                .setDescription(activity.getString(R.string.vault_biometric_offer_message))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(activity.getString(android.R.string.cancel))
                .build()
            val prompt = BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authCipher = result.cryptoObject?.cipher ?: return
                        if (CryptoManager.completeBiometricSetup(activity, authCipher)) {
                            onBiometricChanged?.invoke()
                        }
                    }
                }
            )
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }

        // Full explanation lives in the theme-aware card (HTML screen 3b); the system prompt
        // stays short because Android draws it with its own, non-themable style.
        if (activity is BaseSimpleActivity) {
            activity.showBiometricPasswordNoteDialog(
                title = activity.getString(R.string.vault_biometric_offer_title),
                message = activity.getString(R.string.vault_biometric_offer_message),
                note = activity.getString(R.string.vault_biometric_setup_password_note),
                onContinue = { launchSystemPrompt() },
                onCancel = {},
            )
        } else {
            launchSystemPrompt()
        }
    }
}
