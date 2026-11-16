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
import org.bouncycastle.crypto.macs.Poly1305
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
import org.fossify.gallery.helpers.vault.VaultContainer.CTR_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import org.fossify.gallery.helpers.vault.VaultContainer.MAC_TAG_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.MAGIC_V1
import org.fossify.gallery.helpers.vault.VaultContainer.MAGIC_V2
import org.fossify.gallery.helpers.vault.VaultContainer.META_MAGIC_SYM
import org.fossify.gallery.helpers.vault.VaultContainer.META_MAGIC_V2
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_HEADER_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_STR_PREFIX
import org.fossify.gallery.helpers.vault.VaultContainer.VERSION_SYMMETRIC
import org.fossify.gallery.helpers.vault.VaultContainer.VERSION_SYMMETRIC_AUTH
import org.fossify.gallery.helpers.vault.VaultContainer.VERSION_V5_AUTH
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.DOMAIN_CONTENT_ENC_KEY
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.DOMAIN_CONTENT_MAC_KEY
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.DOMAIN_HEADER_KEY
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.deriveSubkey
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
 * Extracted from [CryptoManager]. Feature group: CryptoManagerCredentials.
 */

fun CryptoManager.deriveKeyArgon2id(
    secret: String,
    salt: ByteArray,
    memoryKb: Int = ARGON2_MEMORY_KB,
    iterations: Int = ARGON2_ITERATIONS,
    parallelism: Int = ARGON2_PARALLELISM
): SecretKeySpec {
    val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withIterations(iterations)
        .withMemoryAsKB(memoryKb)
        .withParallelism(parallelism)
    val generator = Argon2BytesGenerator()
    generator.init(builder.build())
    val keyBytes = ByteArray(32)
    val secretBytes = secret.toByteArray(Charsets.UTF_8)
    try {
        generator.generateBytes(secretBytes, keyBytes, 0, keyBytes.size)
        val spec = SecretKeySpec(keyBytes, "AES")
        return spec
    } finally {
        secretBytes.fill(0)
        keyBytes.fill(0)
    }
}


internal fun CryptoManager.deriveWrappingKey(secret: String, salt: ByteArray): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val chars = secret.toCharArray()
    val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
    try {
        val keyBytes = factory.generateSecret(spec).encoded
        val result = SecretKeySpec(keyBytes, "AES")
        keyBytes.fill(0)
        return result
    } finally {
        spec.clearPassword()
        chars.fill('\u0000')
    }
}


internal fun CryptoManager.writeVaultHeader(
    headerFile: File,
    salt: ByteArray,
    memoryKb: Int,
    iterations: Int,
    parallelism: Int,
    iv: ByteArray,
    wrappedBundle: ByteArray,
    protectionType: Int = PROTECTION_PATTERN
) {
    FileOutputStream(headerFile).use { fos ->
        DataOutputStream(fos).use { dos ->
            dos.write(VAULT_HEADER_MAGIC.toByteArray(Charsets.US_ASCII))
            dos.writeByte(3) // Version 3
            dos.writeByte(1) // KDF: 1 = Argon2id
            dos.writeInt(iterations)
            dos.writeInt(memoryKb)
            dos.writeInt(parallelism)
            dos.writeByte(protectionType)
            dos.writeInt(salt.size)
            dos.write(salt)
            dos.write(iv)
            dos.writeInt(wrappedBundle.size)
            dos.write(wrappedBundle)
            dos.flush()
            fos.fd.sync()
        }
    }
}


internal fun CryptoManager.unlockFromHeaderFile(context: Context, headerFile: File, secret: String): Boolean {
    if (!headerFile.exists()) return false
    return try {
        FileInputStream(headerFile).use { fis ->
            DataInputStream(BufferedInputStream(fis)).use { dis ->
                val magicBytes = ByteArray(VAULT_HEADER_MAGIC.length)
                dis.readFully(magicBytes)
                if (String(magicBytes, Charsets.US_ASCII) != VAULT_HEADER_MAGIC) return false

                val version = dis.readByte().toInt()
                val kdfType = dis.readByte()
                val iterations = dis.readInt()
                val memoryKb = dis.readInt()
                val parallelism = dis.readInt()
                val storedProtectionType = if (version >= 3) dis.readByte().toInt() else -1
                val saltLen = dis.readInt()
                if (saltLen <= 0 || saltLen > 256) return false
                val salt = ByteArray(saltLen)
                dis.readFully(salt)
                val iv = ByteArray(GCM_IV_LENGTH)
                dis.readFully(iv)
                val wrappedLen = dis.readInt()
                if (wrappedLen <= 0 || wrappedLen > 65536) return false
                val wrapped = ByteArray(wrappedLen)
                dis.readFully(wrapped)

                if (kdfType == 1.toByte()) {
                    if (memoryKb !in 8_192..262_144 || iterations !in 1..10 || parallelism !in 1..8) {
                        return false
                    }
                }

                val wrappingKey = if (kdfType == 1.toByte()) {
                    deriveKeyArgon2id(secret, salt, memoryKb, iterations, parallelism)
                } else {
                    deriveWrappingKey(secret, salt)
                }

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                val bundle = cipher.doFinal(wrapped)
                if (bundle.size < 32) {
                    bundle.fill(0)
                    return false
                }
                unlockedMasterKey = bundle.copyOf(32)
                bundle.fill(0)



                context.config.hiddenPasswordHash = "argon2id_aes256_gcm_v2"
                context.config.isHiddenPasswordProtectionOn = true
                if (storedProtectionType in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)) {
                    context.config.hiddenProtectionType = storedProtectionType
                }
                if (version < 3) {
                    try {
                        setupOrUpdateCredentials(context, secret, context.config.hiddenProtectionType)
                    } catch (_: Exception) {}
                }
                true
            }
        }
    } catch (_: Exception) {
        false
    }
}


fun CryptoManager.getVaultProtectionType(context: Context): Int? {
    val vaultRoot = findVaultRoot(context) ?: return null
    val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
    if (!headerFile.exists()) return null
    return try {
        FileInputStream(headerFile).use { fis ->
            DataInputStream(BufferedInputStream(fis)).use { dis ->
                val magicBytes = ByteArray(VAULT_HEADER_MAGIC.length)
                dis.readFully(magicBytes)
                if (String(magicBytes, Charsets.US_ASCII) != VAULT_HEADER_MAGIC) return null
                val version = dis.readByte().toInt()
                if (version >= 3) {
                    dis.readByte() // kdfType
                    dis.readInt()  // iterations
                    dis.readInt()  // memoryKb
                    dis.readInt()  // parallelism
                    val protType = dis.readByte().toInt()
                    if (protType in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)) {
                        protType
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}


/**
 * Initializes or updates the user's hidden item password/PIN/pattern credentials.
 * Uses Argon2id (32 MB, 3 iterations, 2 threads) + AES-256-GCM.
 * The master bundle (MasterKey + PrivateKey) is written directly to .vault in the root folder.
 */
fun CryptoManager.setupOrUpdateCredentials(
    context: Context,
    secret: String,
    protectionType: Int,
    customTargetDir: File? = null
): String {
    vaultSetupInProgress = true
    try {
        // A vault that no longer exists on disk must never leak its old keys into a new one.
        if (
            customTargetDir == null &&
            canAccessSharedStorage(context) &&
            !vaultExistsOnDisk(context) &&
            !isConfiguredVaultTemporarilyUnavailable(context)
        ) {
            resetStaleVaultState(context)
        }
        return setupOrUpdateCredentialsInternal(context, secret, protectionType, customTargetDir)
    } finally {
        vaultSetupInProgress = false
    }
}


internal fun CryptoManager.setupOrUpdateCredentialsInternal(
    context: Context,
    secret: String,
    protectionType: Int,
    customTargetDir: File? = null
): String {
    if (customTargetDir != null) {
        if (resolveExistingVaultInDirectory(customTargetDir) != null) {
            throw IllegalStateException("Vault already exists at target location! Unlock instead of overwriting.")
        }
        prepareVaultLocationSwitch(context, null)
    } else if (hasExistingVault(context) && !isVaultUnlocked) {
        throw IllegalStateException("Vault is locked! Cannot update credentials without unlocking first.")
    }
    val masterKey = unlockedMasterKey ?: ByteArray(32).also { secureRandom.nextBytes(it) }

    val salt = ByteArray(ARGON2_SALT_LENGTH).also { secureRandom.nextBytes(it) }
    val wrappingKey = deriveKeyArgon2id(secret, salt, ARGON2_MEMORY_KB, ARGON2_ITERATIONS, ARGON2_PARALLELISM)
    val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    val wrapped = cipher.doFinal(masterKey)

    clearBiometricKey(context)

    // Write vault root header (.vault) on disk for full multi-device portability
    val vaultRoot = createVaultRoot(context, customTargetDir)
        ?: throw IOException("Failed to create writable vault directory")
    val vaultHeader = File(vaultRoot, VAULT_HEADER_FILE)
    writeVaultHeader(vaultHeader, salt, ARGON2_MEMORY_KB, ARGON2_ITERATIONS, ARGON2_PARALLELISM, iv, wrapped, protectionType)

    val data = Base64.getEncoder().encodeToString(salt) + ":" +
        Base64.getEncoder().encodeToString(iv) + ":" +
        Base64.getEncoder().encodeToString(wrapped)
    context.config.vaultWrappedKeyData = data
    context.config.vaultRootPath = vaultRoot.absolutePath
    cachedVaultRoot = vaultRoot
    encryptedFolderCache.clear()

    unlockedMasterKey = masterKey.copyOf()
    val marker = "argon2id_aes256_gcm_v2"
    context.config.hiddenPasswordHash = marker
    context.config.hiddenProtectionType = protectionType
    context.config.isHiddenPasswordProtectionOn = true

    try {
        getOrCreateDefaultHiddenAlbum(context)
    } catch (_: Exception) {
    }

    // Ensure any existing hidden virtual albums have their names encrypted in SQLite
    ensureBackgroundThread {
        try {
            getOrCreateDefaultHiddenAlbum(context)
            context.virtualAlbumsDB.getAllAlbums().filter { it.isHidden && !isEncryptedVaultString(it.name) }.forEach { album ->
                val albumId = album.id ?: return@forEach
                val encName = encryptStringForVault(context, album.name)
                context.virtualAlbumsDB.updateAlbumVisibilityAndName(albumId, true, encName)
            }
        } catch (_: Exception) {
        }
    }

    return marker
}


fun CryptoManager.isEncryptedVaultString(text: String): Boolean {
    return text.startsWith(VAULT_STR_PREFIX)
}


fun CryptoManager.encryptStringForVault(context: Context, plainText: String): String {
    if (isEncryptedVaultString(plainText)) return plainText
    val masterKey = unlockedMasterKey ?: return plainText
    val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
    val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val payload = ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use { dos ->
            dos.writeByte(VERSION_SYMMETRIC.toInt())
            dos.write(iv)
            dos.writeInt(encrypted.size)
            dos.write(encrypted)
        }
        baos.toByteArray()
    }
    return VAULT_STR_PREFIX + Base64.getEncoder().encodeToString(payload)
}


fun CryptoManager.decryptStringFromVault(storedText: String): String? {
    if (!isEncryptedVaultString(storedText)) return storedText
    val masterKey = unlockedMasterKey ?: return null
    val b64 = storedText.removePrefix(VAULT_STR_PREFIX)
    return try {
        val raw = Base64.getMimeDecoder().decode(b64)
        DataInputStream(ByteArrayInputStream(raw)).use { dis ->
            val version = dis.readByte()
            if (version != VERSION_SYMMETRIC) return null
            val iv = ByteArray(GCM_IV_LENGTH)
            dis.readFully(iv)
            val len = dis.readInt()
            if (len <= 0 || len > 8192) return null
            val encrypted = ByteArray(len)
            dis.readFully(encrypted)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}


fun CryptoManager.unlockWithSecret(context: Context, secret: String): Boolean {
    val vaultRoot = locateVaultRootOnDisk(context)
    if (vaultRoot == null) {
        // The vault is gone: there is nothing to unlock. Drop every leftover credential instead
        // of re-creating the vault from SharedPreferences.
        verifyVaultOnDisk(context)
        return false
    }

    val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
    if (isValidVaultHeader(headerFile)) {
        val unlocked = unlockFromHeaderFile(context, headerFile, secret)
        if (unlocked) {
            context.config.vaultRootPath = vaultRoot.absolutePath
            cachedVaultRoot = vaultRoot
        }
        return unlocked
    }

    // Legacy vault (older app versions): the root holds encrypted content but has no .vault yet.
    return unlockLegacyVaultFromPrefs(context, secret)
}


/**
 * Only reachable for a legacy vault whose folder physically exists (see isLegacyVaultRoot).
 * On success the missing .vault header is written into that existing folder.
 */
internal fun CryptoManager.unlockLegacyVaultFromPrefs(context: Context, secret: String): Boolean {
    val parts = context.config.vaultWrappedKeyData.split(":")
    if (parts.size != 3) return false
    return try {
        val salt = Base64.getMimeDecoder().decode(parts[0])
        val iv = Base64.getMimeDecoder().decode(parts[1])
        val ciphertext = Base64.getMimeDecoder().decode(parts[2])

        fun tryDecrypt(wrappingKey: SecretKeySpec): ByteArray? = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }

        val bundle = tryDecrypt(deriveKeyArgon2id(secret, salt))
            ?: tryDecrypt(deriveWrappingKey(secret, salt))
            ?: return false

        if (bundle.size < 32) {
            bundle.fill(0)
            return false
        }
        unlockedMasterKey = bundle.copyOf(32)
        bundle.fill(0)

        // Upgrade to Argon2id and write the .vault header into the existing root
        setupOrUpdateCredentials(context, secret, context.config.hiddenProtectionType)
        true
    } catch (_: Exception) {
        false
    }
}

