package org.fossify.gallery.helpers

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import java.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.dateTakensDB
import org.fossify.gallery.extensions.deleteDBPaths
import org.fossify.gallery.extensions.deleteVirtualAlbumItemFromAllAlbums
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.updateVirtualAlbumItemPath
import org.fossify.gallery.extensions.virtualAlbumsDB
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.FOLDER_META_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_HEADER_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_STR_PREFIX
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import android.media.MediaScannerConnection
import android.os.Build
import androidx.biometric.BiometricManager
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.models.DateTaken
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.fossify.gallery.helpers.CryptoManager.TargetPlainIndex
import org.fossify.gallery.helpers.CryptoManager.TargetEncryptedIndex
import org.fossify.gallery.helpers.CryptoManager.TransferConflictStatus
import org.fossify.gallery.helpers.CryptoManager.TransferConflictCheck
import org.fossify.gallery.helpers.CryptoManager.SmartConflictDifference
import org.fossify.gallery.helpers.CryptoManager.SmartConflictComparison
import org.fossify.gallery.helpers.CryptoManager.VaultExportResult
import org.fossify.gallery.helpers.CryptoManager.AlbumConflictResolution
import org.fossify.gallery.helpers.CryptoManager.AlbumCryptoOptions
import org.fossify.gallery.helpers.CryptoManager.AlbumConflictPreview
import org.fossify.gallery.helpers.CryptoManager.StreamCompareMacResult
import org.fossify.gallery.helpers.CryptoManager.SyncPendingItem

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerBiometrics.
 */

fun CryptoManager.isBiometricHardwareAvailable(context: Context): Boolean {
    return try {
        val biometricManager = BiometricManager.from(context)
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Throwable) {
        false
    }
}


internal fun CryptoManager.isBiometricKeyHardwareBacked(key: SecretKey): Boolean {
    return try {
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as? KeyInfo ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            keyInfo.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
        } else {
            @Suppress("DEPRECATION")
            keyInfo.isInsideSecureHardware
        }
    } catch (_: Throwable) {
        false
    }
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
        isBiometricKeyHardwareBacked(key)
    } catch (_: Throwable) {
        false
    }
}


fun CryptoManager.getOrCreateBiometricKey(): SecretKey? {
    return try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(BIOMETRIC_KEYSTORE_ALIAS)) {
            generateBiometricKeyInternal(keyStore)
        }
        val key = keyStore.getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey ?: return null
        if (!isBiometricKeyHardwareBacked(key)) {
            keyStore.deleteEntry(BIOMETRIC_KEYSTORE_ALIAS)
            return null
        }
        key
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


fun CryptoManager.getBiometricEncryptCipher(): Cipher? {
    val key = getOrCreateBiometricKey() ?: return null
    return try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher
    } catch (_: Throwable) {
        null
    }
}


fun CryptoManager.completeBiometricSetup(context: Context, cipher: Cipher): Boolean {
    val masterKey = unlockedMasterKey ?: return false
    return try {
        val encrypted = cipher.doFinal(masterKey)
        val iv = cipher.iv
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        context.config.vaultBiometricWrappedBundle = Base64.getEncoder().encodeToString(combined)
        context.config.isVaultBiometricEnabled = true
        true
    } catch (_: Throwable) {
        false
    }
}


fun CryptoManager.getBiometricDecryptCipher(context: Context): Cipher? {
    val wrappedB64 = context.config.vaultBiometricWrappedBundle
    if (wrappedB64.isEmpty()) return null
    val combined = try {
        Base64.getMimeDecoder().decode(wrappedB64)
    } catch (_: Throwable) {
        return null
    }
    if (combined.size <= GCM_IV_LENGTH) return null
    val iv = combined.copyOfRange(0, GCM_IV_LENGTH)

    return try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val key = keyStore.getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey ?: return null
        if (!isBiometricKeyHardwareBacked(key)) {
            clearBiometricKey(context)
            return null
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher
    } catch (e: KeyPermanentlyInvalidatedException) {
        clearBiometricKey(context)
        null
    } catch (_: Throwable) {
        null
    }
}


fun CryptoManager.completeBiometricUnlock(context: Context, cipher: Cipher): Boolean {
    if (!verifyVaultOnDisk(context)) return false
    val wrappedB64 = context.config.vaultBiometricWrappedBundle
    if (wrappedB64.isEmpty()) return false
    val combined = try {
        Base64.getMimeDecoder().decode(wrappedB64)
    } catch (_: Throwable) {
        return false
    }
    if (combined.size <= GCM_IV_LENGTH) return false
    val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

    return try {
        val bundle = cipher.doFinal(encrypted)
        if (bundle.size < 32) {
            bundle.fill(0)
            return false
        }
        unlockedMasterKey = bundle.copyOf(32)
        bundle.fill(0)
        context.config.hiddenPasswordHash = "argon2id_aes256_gcm"
        context.config.isHiddenPasswordProtectionOn = true
        findVaultRoot(context)?.let { context.config.vaultRootPath = it.absolutePath }
        true
    } catch (_: Throwable) {
        false
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
