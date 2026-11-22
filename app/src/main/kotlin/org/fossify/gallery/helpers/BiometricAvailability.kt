package org.fossify.gallery.helpers

import android.content.Context
import androidx.biometric.BiometricManager
import org.fossify.gallery.R
import javax.crypto.Cipher

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
 * Three-way result of probing the vault biometric key.
 *
 * Boolean-only probing conflated "device locked / transient Keystore error" with "no hardware
 * key", which made the Settings row randomly turn passive on screen-on. [DEFERRED] keeps the
 * last known good UI state until the keyguard is gone; only [UNAVAILABLE] flips it.
 */
internal enum class BiometricKeyProbe {
    READY,
    DEFERRED,
    UNAVAILABLE
}

/**
 * Returns a usable hardware-backed key only when a strong biometric is actually enrolled.
 * A key created while no credential was enrolled is invalidated by that later enrollment
 * (`setInvalidatedByBiometricEnrollment(true)`), so it is cleared here to allow a clean setup
 * once the user adds a fingerprint / Class 3 face in Android settings. Clearing also resets
 * `isVaultBiometricEnabled`, which keeps the setting default-off after re-enrollment.
 */
internal fun CryptoManager.probeBiometricKey(context: Context): BiometricKeyProbe {
    if (getBiometricAuthenticatorStatus(context) != BiometricManager.BIOMETRIC_SUCCESS) {
        return BiometricKeyProbe.UNAVAILABLE
    }
    if (isDeviceLocked(context)) return BiometricKeyProbe.DEFERRED

    val key = getOrCreateBiometricKey(context) ?: return if (isDeviceLocked(context)) {
        BiometricKeyProbe.DEFERRED
    } else {
        BiometricKeyProbe.UNAVAILABLE
    }

    return try {
        Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key)
        BiometricKeyProbe.READY
    } catch (e: Throwable) {
        when (classifyBiometricCipherFailure(e, isDeviceLocked(context))) {
            BiometricCipherResult.KeyInvalidated -> {
                clearBiometricKey(context)
                if (getOrCreateBiometricKey(context) != null) {
                    BiometricKeyProbe.READY
                } else {
                    BiometricKeyProbe.UNAVAILABLE
                }
            }
            BiometricCipherResult.DeviceLocked,
            BiometricCipherResult.TransientError -> BiometricKeyProbe.DEFERRED
            else -> BiometricKeyProbe.UNAVAILABLE
        }
    }
}

/** Kept for existing callers that only need a boolean verdict. */
internal fun CryptoManager.ensureBiometricKeyAvailable(context: Context): Boolean =
    probeBiometricKey(context) == BiometricKeyProbe.READY

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
