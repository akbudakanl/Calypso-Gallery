package org.fossify.gallery.helpers

import android.content.Context
import androidx.biometric.BiometricManager
import org.fossify.gallery.R

/** Raw `canAuthenticate` result so callers can explain *why* biometrics are unavailable. */
fun CryptoManager.getBiometricAuthenticatorStatus(context: Context): Int {
    return try {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    } catch (_: Throwable) {
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN
    }
}

/**
 * Maps the unavailability cause to an honest, user-facing explanation. The app only accepts
 * STRONG biometrics backed by a hardware key, so a weak face unlock is deliberately rejected.
 */
internal fun biometricUnavailableReason(
    canAuthenticateResult: Int,
    keyAvailable: Boolean
): Int = when (canAuthenticateResult) {
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
        R.string.vault_biometric_unavailable_no_hardware

    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
        R.string.vault_biometric_unavailable_none_enrolled

    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
        R.string.vault_biometric_unavailable_hw_unavailable

    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
        R.string.vault_biometric_unavailable_security_update

    BiometricManager.BIOMETRIC_SUCCESS -> if (keyAvailable) {
        R.string.vault_biometric_unavailable_generic
    } else {
        R.string.vault_biometric_unavailable_no_secure_key
    }

    else -> R.string.vault_biometric_unavailable_generic
}
