package org.fossify.gallery.helpers

import android.app.KeyguardManager
import android.content.Context
import java.util.Base64
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import java.io.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import android.os.Build
import androidx.biometric.BiometricManager
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerBiometrics.
 */

sealed class BiometricCipherResult {
    data class Ready(val cipher: Cipher) : BiometricCipherResult()
    data object KeyInvalidated : BiometricCipherResult()
    data object DeviceLocked : BiometricCipherResult()
    data object TransientError : BiometricCipherResult()
    data object Unavailable : BiometricCipherResult()
}

internal enum class BiometricHardwareBacking {
    HARDWARE_BACKED,
    SOFTWARE_ONLY,
    TRANSIENT_ERROR
}

fun CryptoManager.isDeviceLocked(context: Context): Boolean {
    return try {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        keyguardManager?.isDeviceLocked == true
    } catch (_: Throwable) {
        false
    }
}

internal fun classifyBiometricCipherFailure(
    error: Throwable,
    deviceLocked: Boolean,
    isKeyInvalidatedError: (Throwable) -> Boolean = { it is KeyPermanentlyInvalidatedException },
    isUserNotAuthenticatedError: (Throwable) -> Boolean = { it is UserNotAuthenticatedException }
): BiometricCipherResult {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < 8) {
        if (isKeyInvalidatedError(current)) {
            return BiometricCipherResult.KeyInvalidated
        }
        current = current.cause
        depth++
    }
    var authCheck: Throwable? = error
    depth = 0
    while (authCheck != null && depth < 8) {
        if (isUserNotAuthenticatedError(authCheck)) {
            return BiometricCipherResult.DeviceLocked
        }
        authCheck = authCheck.cause
        depth++
    }
    return if (deviceLocked) {
        BiometricCipherResult.DeviceLocked
    } else {
        BiometricCipherResult.TransientError
    }
}

fun CryptoManager.isBiometricHardwareAvailable(context: Context): Boolean {
    return getBiometricAuthenticatorStatus(context) == BiometricManager.BIOMETRIC_SUCCESS
}

internal fun CryptoManager.checkBiometricKeyHardwareBacking(key: SecretKey): BiometricHardwareBacking {
    return try {
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as? KeyInfo
            ?: return BiometricHardwareBacking.TRANSIENT_ERROR
        val hardwareBacked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            keyInfo.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
        } else {
            @Suppress("DEPRECATION")
            keyInfo.isInsideSecureHardware
        }
        if (hardwareBacked) {
            BiometricHardwareBacking.HARDWARE_BACKED
        } else {
            BiometricHardwareBacking.SOFTWARE_ONLY
        }
    } catch (_: Throwable) {
        BiometricHardwareBacking.TRANSIENT_ERROR
    }
}


internal fun CryptoManager.isBiometricKeyHardwareBacked(key: SecretKey): Boolean {
    return checkBiometricKeyHardwareBacking(key) == BiometricHardwareBacking.HARDWARE_BACKED
}


fun CryptoManager.canUnlockWithBiometrics(context: Context): Boolean {
    if (!context.config.isVaultBiometricEnabled ||
        context.config.vaultBiometricWrappedBundle.isEmpty() ||
        !isBiometricHardwareAvailable(context)
    ) {
        return false
    }
    return try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val key = keyStore.getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey ?: return false
        checkBiometricKeyHardwareBacking(key) != BiometricHardwareBacking.SOFTWARE_ONLY
    } catch (_: Throwable) {
        true
    }
}


fun CryptoManager.getOrCreateBiometricKey(context: Context): SecretKey? {
    return try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(BIOMETRIC_KEYSTORE_ALIAS)) {
            generateBiometricKeyInternal(keyStore)
        }
        val key = keyStore.getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey ?: return null
        when (checkBiometricKeyHardwareBacking(key)) {
            BiometricHardwareBacking.HARDWARE_BACKED -> key
            BiometricHardwareBacking.SOFTWARE_ONLY -> {
                // Never destroy a key because of a transient probe. While the keyguard is up,
                // KeyInfo can be unreadable and a wrong SOFTWARE_ONLY verdict would make the
                // wrapped vault key unrecoverable. Deletion is only safe when the device is open.
                if (!isDeviceLocked(context)) {
                    keyStore.deleteEntry(BIOMETRIC_KEYSTORE_ALIAS)
                }
                null
            }
            BiometricHardwareBacking.TRANSIENT_ERROR -> null
        }
    } catch (_: Throwable) {
        null
    }
}


internal fun CryptoManager.generateBiometricKeyInternal(keyStore: KeyStore) {
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    fun buildSpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        if (strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    try {
        keyGenerator.init(buildSpec(strongBox = true))
        keyGenerator.generateKey()
    } catch (_: Throwable) {
        keyGenerator.init(buildSpec(strongBox = false))
        keyGenerator.generateKey()
    }
}


fun CryptoManager.getBiometricEncryptCipher(context: Context): Cipher? {
    val key = getOrCreateBiometricKey(context) ?: return null
    return try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher
    } catch (_: Throwable) {
        null
    }
}


fun CryptoManager.completeBiometricSetup(context: Context, cipher: Cipher): Boolean {
    val masterKey = NativeVaultSession.exportVmkForBiometricWrapNative() ?: return false
    var encrypted: ByteArray? = null
    var iv: ByteArray? = null
    var combined: ByteArray? = null
    return try {
        encrypted = cipher.doFinal(masterKey)
        iv = cipher.iv
        combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        context.config.vaultBiometricWrappedBundle = Base64.getEncoder().encodeToString(combined)
        context.config.isVaultBiometricEnabled = true
        // The password was entered just before enrollment; start the reminder window here.
        markVaultPasswordEntered(context.config)
        true
    } catch (_: Throwable) {
        false
    } finally {
        masterKey.fill(0)
        encrypted?.fill(0)
        iv?.fill(0)
        combined?.fill(0)
    }
}


fun CryptoManager.getBiometricDecryptCipherResult(context: Context): BiometricCipherResult {
    if (!context.config.isVaultBiometricEnabled) {
        return BiometricCipherResult.Unavailable
    }
    val wrappedB64 = context.config.vaultBiometricWrappedBundle
    if (wrappedB64.isEmpty()) {
        return BiometricCipherResult.Unavailable
    }
    val combined = try {
        Base64.getMimeDecoder().decode(wrappedB64)
    } catch (_: Throwable) {
        return BiometricCipherResult.Unavailable
    }
    if (combined.size <= GCM_IV_LENGTH) {
        combined.fill(0)
        return BiometricCipherResult.Unavailable
    }
    val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
    combined.fill(0)

    return try {
        if (isDeviceLocked(context)) {
            return BiometricCipherResult.DeviceLocked
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val key = keyStore.getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey
            ?: return BiometricCipherResult.Unavailable
        when (checkBiometricKeyHardwareBacking(key)) {
            BiometricHardwareBacking.SOFTWARE_ONLY -> {
                clearBiometricKey(context)
                return BiometricCipherResult.KeyInvalidated
            }
            BiometricHardwareBacking.TRANSIENT_ERROR -> {
                return BiometricCipherResult.TransientError
            }
            BiometricHardwareBacking.HARDWARE_BACKED -> Unit
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        BiometricCipherResult.Ready(cipher)
    } catch (e: Throwable) {
        val result = classifyBiometricCipherFailure(e, isDeviceLocked(context))
        if (result == BiometricCipherResult.KeyInvalidated) {
            clearBiometricKey(context)
        }
        result
    } finally {
        iv.fill(0)
    }
}


fun CryptoManager.getBiometricDecryptCipher(context: Context): Cipher? {
    return (getBiometricDecryptCipherResult(context) as? BiometricCipherResult.Ready)?.cipher
}


fun CryptoManager.completeBiometricUnlock(context: Context, cipher: Cipher): Boolean {
    if (!verifyVaultOnDisk(context)) return false
    val vaultRoot = locateVaultRootOnDisk(context) ?: findVaultRoot(context) ?: return false
    val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
    if (!isValidVaultHeader(headerFile)) return false
    val headerBytes = runCatching { headerFile.readBytes() }.getOrNull() ?: return false

    val wrappedB64 = context.config.vaultBiometricWrappedBundle
    if (wrappedB64.isEmpty()) return false
    val combined = try {
        Base64.getMimeDecoder().decode(wrappedB64)
    } catch (_: Throwable) {
        return false
    }
    if (combined.size <= GCM_IV_LENGTH) {
        combined.fill(0)
        return false
    }
    val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
    combined.fill(0)

    var bundle: ByteArray? = null
    var vmk: ByteArray? = null
    return try {
        bundle = cipher.doFinal(encrypted)
        if (bundle.size < 32) {
            return false
        }
        vmk = bundle.copyOf(32)
        if (!NativeVaultSession.unlockVaultWithVmkNative(headerBytes, vmk)) {
            return false
        }
        context.config.hiddenPasswordHash = "argon2id_aes256_gcm"
        context.config.isHiddenPasswordProtectionOn = true
        context.config.vaultRootPath = vaultRoot.absolutePath
        cachedVaultRoot = vaultRoot
        true
    } catch (_: Throwable) {
        false
    } finally {
        encrypted.fill(0)
        bundle?.fill(0)
        vmk?.fill(0)
    }
}


fun CryptoManager.clearBiometricKey(context: Context) {
    context.config.isVaultBiometricEnabled = false
    context.config.vaultBiometricWrappedBundle = ""
    try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(BIOMETRIC_KEYSTORE_ALIAS)) {
            keyStore.deleteEntry(BIOMETRIC_KEYSTORE_ALIAS)
        }
    } catch (_: Throwable) {}
}
