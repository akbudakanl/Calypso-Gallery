package org.fossify.gallery.dialogs

import android.app.Activity
import android.widget.LinearLayout
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.completeBiometricSetup
import org.fossify.gallery.helpers.getBiometricEncryptCipher
import org.fossify.gallery.helpers.getOrCreateBiometricKey
import org.fossify.gallery.helpers.isBiometricHardwareAvailable
import org.fossify.gallery.helpers.readVaultKdfParams

/**
 * One-time offer shown right after the first vault is created. The master key is already unlocked
 * at that point, so accepting does not ask for the password a second time. Declining, back, or
 * leaving the app without answering leaves biometrics off; the question is never repeated.
 */
object VaultBiometricOfferDialog {
    /** Set by Settings so the toggle reflects enrolment immediately. */
    var onBiometricChanged: (() -> Unit)? = null

    fun showIfNeeded(activity: Activity) {
        val config = activity.config
        if (config.vaultBiometricOfferShown) return
        if (CryptoManager.readVaultKdfParams(activity) == null) return

        // No TEE/StrongBox (or no strong biometric enrolled): the app never uses biometrics here.
        // Do not mark the offer as shown - a later creation on the same/other device re-checks.
        if (!CryptoManager.isBiometricHardwareAvailable(activity) || CryptoManager.getOrCreateBiometricKey() == null) {
            config.isVaultBiometricEnabled = false
            config.vaultBiometricWrappedBundle = ""
            return
        }

        // Mark and default to off before showing: exiting without an answer counts as a decline.
        config.vaultBiometricOfferShown = true
        config.isVaultBiometricEnabled = false
        config.vaultBiometricWrappedBundle = ""

        val palette = org.fossify.gallery.helpers.AppearanceManager.resolve(activity, config)
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24f * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(MyTextView(activity).apply {
                text = activity.getString(R.string.vault_biometric_offer_message)
                setTextColor(palette.text)
            })
        }

        activity.getAlertDialogBuilder()
            .setCancelable(false)
            .setNegativeButton(R.string.vault_biometric_offer_decline, null)
            .setPositiveButton(R.string.vault_biometric_offer_accept) { dialog, _ ->
                startEnrollment(activity)
                dialog.dismiss()
            }
            .apply {
                activity.setupDialogStuff(view, this, titleId = R.string.vault_biometric_offer_title)
            }
    }

    private fun startEnrollment(activity: Activity) {
        val host = activity as? FragmentActivity ?: return
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
}
