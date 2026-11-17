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
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.deriveMetadataKey
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


internal fun CryptoManager.unlockFromHeaderFile(context: Context, headerFile: File, secret: String): Boolean {
    if (!headerFile.exists()) return false
    return try {
        FileInputStream(headerFile).use { fis ->
            DataInputStream(BufferedInputStream(fis)).use { dis ->
                val magicBytes = ByteArray(VAULT_HEADER_MAGIC.length)
                dis.readFully(magicBytes)
                if (String(magicBytes, Charsets.US_ASCII) != VAULT_HEADER_MAGIC) return false

                val version = dis.readByte().toInt()
                if (version != VaultContainer.VAULT_FORMAT_VERSION.toInt()) return false
                val kdfType = dis.readByte()
                if (kdfType != 1.toByte()) return false
                val iterations = dis.readInt()
                val memoryKb = dis.readInt()
                val parallelism = dis.readInt()
                val storedProtectionType = dis.readByte().toInt()
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

                // PDK ile sarilmis VMK'nin ardindan VMK ile sarilmis keyring tasir.
                val ivVmk = ByteArray(GCM_IV_LENGTH)
                dis.readFully(ivVmk)
                val keyringLen = dis.readInt()
                if (keyringLen <= 0 || keyringLen > 4096) return false
                val wrappedKeyring = ByteArray(keyringLen)
                dis.readFully(wrappedKeyring)

                if (memoryKb !in 8_192..262_144 || iterations !in 1..10 || parallelism !in 1..8) {
                    return false
                }
                val wrappingKey = deriveKeyArgon2id(secret, salt, memoryKb, iterations, parallelism)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                val bundle = cipher.doFinal(wrapped)
                if (bundle.size < 32) {
                    bundle.fill(0)
                    return false
                }
                val vmk = bundle.copyOf(32)
                bundle.fill(0)

                val keyringCipher = Cipher.getInstance("AES/GCM/NoPadding")
                keyringCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(vmk, "AES"), GCMParameterSpec(GCM_TAG_BITS, ivVmk))
                val keyring = try {
                    keyringCipher.doFinal(wrappedKeyring)
                } catch (_: Exception) {
                    vmk.fill(0)
                    return false
                }
                val parsed = unpackKeyring(keyring)
                keyring.fill(0)
                if (parsed == null) {
                    vmk.fill(0)
                    return false
                }
                unlockedMasterKey = vmk
                unlockedKek = parsed.second

                context.config.hiddenPasswordHash = "argon2id_aes256_gcm"
                context.config.isHiddenPasswordProtectionOn = true
                if (storedProtectionType in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)) {
                    context.config.hiddenProtectionType = storedProtectionType
                }
                true
            }
        }
    } catch (_: Exception) {
        false
    }
}


/** `.vault` başlığının kanonik formatta olduğunu doğrular. */
internal fun readVaultHeaderVersion(file: File): Int {
    if (!file.isFile) return -1
    return try {
        FileInputStream(file).use { fis ->
            val magic = ByteArray(VAULT_HEADER_MAGIC.length)
            if (fis.read(magic) != magic.size) return -1
            if (String(magic, Charsets.US_ASCII) != VAULT_HEADER_MAGIC) return -1
            val v = fis.read()
            if (v == VaultContainer.VAULT_FORMAT_VERSION.toInt()) v else -1
        }
    } catch (_: Exception) {
        -1
    }
}

/**
 * .vault başlığı: PDK -> VMK ve VMK -> keyring olmak üzere iki sarma katmanı.
 * Duzen:
 *   magic(6) | version(4) | kdf(1) | iter(4) | mem(4) | par(4) | prot(1) |
 *   saltLen(4) | salt | ivPdk(12) | wrappedVmKLen(4) | wrappedVmK |
 *   ivVmk(12) | keyringLen(4) | keyring
 * keyring icerigi: activeKeyId(u16) | KEK(32)
 */
internal fun CryptoManager.writeVaultHeader(
    headerFile: File,
    salt: ByteArray,
    memoryKb: Int,
    iterations: Int,
    parallelism: Int,
    ivPdk: ByteArray,
    wrappedVmK: ByteArray,
    ivVmk: ByteArray,
    wrappedKeyring: ByteArray,
    protectionType: Int = PROTECTION_PATTERN
) {
    FileOutputStream(headerFile).use { fos ->
        DataOutputStream(fos).use { dos ->
            dos.write(VAULT_HEADER_MAGIC.toByteArray(Charsets.US_ASCII))
            dos.writeByte(4)
            dos.writeByte(1) // Argon2id
            dos.writeInt(iterations)
            dos.writeInt(memoryKb)
            dos.writeInt(parallelism)
            dos.writeByte(protectionType)
            dos.writeInt(salt.size)
            dos.write(salt)
            dos.write(ivPdk)
            dos.writeInt(wrappedVmK.size)
            dos.write(wrappedVmK)
            dos.write(ivVmk)
            dos.writeInt(wrappedKeyring.size)
            dos.write(wrappedKeyring)
            dos.flush()
            fos.fd.sync()
        }
    }
}

private fun CryptoManager.packKeyring(keyId: Short, kek: ByteArray): ByteArray =
    ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use { dos ->
            dos.writeShort(keyId.toInt())
            dos.write(kek)
        }
        baos.toByteArray()
    }

private fun CryptoManager.unpackKeyring(bytes: ByteArray): Pair<Short, ByteArray>? {
    if (bytes.size < 34) return null
    DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
        val keyId = dis.readUnsignedShort().toShort()
        val kek = ByteArray(32)
        dis.readFully(kek)
        return keyId to kek
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


/**
 * "Kasa acilis suresi" modunda hedef sureye gore Argon2 parametrelerini kalibre eder.
 * Tek referans olcumu (~1-2 sn) yeterlidir; iterasyon = hedef / iterasyonBasiSure.
 * Hazir mod modundaysa profilin sabit degerleri dondurulur.
 * NOT: Agir is oldugu icin arka planda cagrilmalidir.
 */
internal fun CryptoManager.resolveArgon2Params(context: Context): Triple<Int, Int, Int> {
    if (context.config.bruteForceMode == 1) {
        context.config.argon2CalibrationClamped = false
        return Triple(
            context.config.argon2MemoryKb,
            context.config.argon2Iterations,
            context.config.argon2Parallelism
        )
    }

    val targetMs = context.config.unlockTargetMs
    val maxHeapKb = (Runtime.getRuntime().maxMemory() / 1024L).coerceAtLeast(1L)
    // Low-end cihazlarda tek seferlik ayirma heap'in yarisiyla sinirli;
    // 128 MiB istense bile maxMemory kucukse 64 MiB'ya dusulur.
    val desiredMemoryKb = if (targetMs > 5_000) 128 * 1024 else 64 * 1024
    var memoryKb = desiredMemoryKb.coerceAtMost((maxHeapKb / 2).toInt()).coerceAtLeast(64 * 1024)
    val referenceSalt = ByteArray(16)

    val measuredMs = try {
        val t0 = System.nanoTime()
        deriveKeyArgon2id("cipher-calibration", referenceSalt, memoryKb, 2, 2)
        ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
    } catch (_: OutOfMemoryError) {
        // 128 MiB ayirilamadi: 64 MiB'ya dusup tekrar dene; hala olmazsa OWASP asgari.
        memoryKb = 64 * 1024
        System.gc()
        val t0 = System.nanoTime()
        try {
            deriveKeyArgon2id("cipher-calibration", referenceSalt, memoryKb, 2, 2)
            ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: OutOfMemoryError) {
            1L
        }
    }

    val perIterationMs = measuredMs / 2.0
    val rawIterations = targetMs / perIterationMs
    val iterations = rawIterations.toInt().coerceIn(2, 16)
    context.config.argon2CalibrationClamped =
        rawIterations < 2 || rawIterations > 16 || memoryKb < desiredMemoryKb
    context.config.lastCalibratedMemoryKb = memoryKb
    context.config.lastCalibratedIterations = iterations
    return Triple(memoryKb, iterations, 2)
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
    // Kanonik kasa: VMK -> keyring -> KEK. Sifre degistirildiğinde mevcut VMK/KEK korunur.
    val isNewVault = unlockedMasterKey == null
    val vmk = unlockedMasterKey ?: ByteArray(32).also { secureRandom.nextBytes(it) }
    val kek = if (isNewVault) ByteArray(32).also { secureRandom.nextBytes(it) }
        else (unlockedKek ?: ByteArray(32).also { secureRandom.nextBytes(it) })

    val salt = ByteArray(ARGON2_SALT_LENGTH).also { secureRandom.nextBytes(it) }
    // Brute-force direnci: hedef acilis suresi seciliyse cihazda tek bir referans
    // olcumu yapip iterasyon sayisini hedefe gore ayarlariz. Sonuc .vault'a yazilir;
    // baska cihazda ayni parametreler kullanilir, gercek sure degisir (KeePass gibi).
    val (argon2MemoryKb, argon2Iterations, argon2Parallelism) = resolveArgon2Params(context)
    val wrappingKey = deriveKeyArgon2id(secret, salt, argon2MemoryKb, argon2Iterations, argon2Parallelism)
    val ivPdk = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, ivPdk))
    val wrappedVmK = cipher.doFinal(vmk)

    val ivVmk = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
    val keyringCipher = Cipher.getInstance("AES/GCM/NoPadding")
    keyringCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(vmk, "AES"), GCMParameterSpec(GCM_TAG_BITS, ivVmk))
    val wrappedKeyring = keyringCipher.doFinal(packKeyring(1, kek))

    clearBiometricKey(context)

    // Write vault root header (.vault) on disk for full multi-device portability
    val vaultRoot = createVaultRoot(context, customTargetDir)
        ?: throw IOException("Failed to create writable vault directory")
    val vaultHeader = File(vaultRoot, VAULT_HEADER_FILE)
    writeVaultHeader(
        vaultHeader, salt, argon2MemoryKb, argon2Iterations, argon2Parallelism,
        ivPdk, wrappedVmK, ivVmk, wrappedKeyring, protectionType
    )

    val data = Base64.getEncoder().encodeToString(salt) + ":" +
        Base64.getEncoder().encodeToString(ivPdk) + ":" +
        Base64.getEncoder().encodeToString(wrappedVmK)
    context.config.vaultWrappedKeyData = data
    context.config.vaultRootPath = vaultRoot.absolutePath
    cachedVaultRoot = vaultRoot
    encryptedFolderCache.clear()

    unlockedMasterKey = vmk.copyOf()
    unlockedKek = kek?.copyOf()
    val marker = "argon2id_aes256_gcm"
    context.config.hiddenPasswordHash = marker
    context.config.hiddenProtectionType = protectionType
    context.config.isHiddenPasswordProtectionOn = true

    if (isNewVault) {
        // A genuinely new vault gets a fresh VMK/KEK. Hidden virtual albums left over in Room DB
        // from a previous vault store their names encrypted with the OLD key and point into the old
        // vault, so they are unusable here and can no longer be decrypted (their names would show
        // as random "FVSTR:…" strings). Drop them up front so the next syncVirtualAlbums() cannot
        // re-export them into this vault's JSON/ folder as empty, unreadable albums. Switching to a
        // different vault already does this via prepareVaultLocationSwitch; creating a brand-new
        // one (especially at the default location) previously did not.
        runCatching { VirtualAlbumSyncManager.evictHiddenVirtualAlbumsFromDbCache(context) }
    }

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
                val encName = encryptStringForVault(context, album.name, "album")
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


/**
 * Kasaya ozel kayit: kayda ozgel HKDF anahtari ve AAD icin purpose kullanilir;
 * purpose acikta saklanir, boylece decrypt tarafi baglami yeniden kurabilir.
 */
fun CryptoManager.encryptStringForVault(
    context: Context?,
    plainText: String,
    purpose: String = "generic"
): String {
    if (isEncryptedVaultString(plainText)) return plainText
    val kek = activeKek() ?: return plainText
    val ctx = "CalypsoGallery:Metadata:AESGCM|$purpose"
    val key = deriveMetadataKey(kek, ctx)
    return try {
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(ctx.toByteArray(Charsets.UTF_8))
        }
        val enc = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeByte(VaultContainer.VAULT_FORMAT_VERSION.toInt())
                dos.writeShort(1) // keyId
                dos.writeUTF(purpose)
                dos.write(iv)
                dos.writeInt(enc.size)
                dos.write(enc)
            }
            baos.toByteArray()
        }
        VAULT_STR_PREFIX + Base64.getEncoder().encodeToString(payload)
    } finally {
        key.fill(0)
    }
}


fun CryptoManager.decryptStringFromVault(storedText: String): String? {
    if (!isEncryptedVaultString(storedText)) return storedText

    val kek = activeKek() ?: return null
    return try {
        val raw = Base64.getMimeDecoder().decode(storedText.removePrefix(VAULT_STR_PREFIX))
        DataInputStream(ByteArrayInputStream(raw)).use { dis ->
            val version = dis.readByte()
            if (version != VaultContainer.VAULT_FORMAT_VERSION) return null
            dis.readUnsignedShort() // keyId
            val purpose = dis.readUTF()
            val iv = ByteArray(GCM_IV_LENGTH)
            dis.readFully(iv)
            val len = dis.readInt()
            if (len <= 0 || len > 8192) return null
            val enc = ByteArray(len)
            dis.readFully(enc)
            val ctx = "CalypsoGallery:Metadata:AESGCM|$purpose"
            val key = deriveMetadataKey(kek, ctx)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                    updateAAD(ctx.toByteArray(Charsets.UTF_8))
                }
                String(cipher.doFinal(enc), Charsets.UTF_8)
            } finally {
                key.fill(0)
            }
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
    if (!isValidVaultHeader(headerFile)) return false

    val unlocked = unlockFromHeaderFile(context, headerFile, secret)
    if (unlocked) {
        context.config.vaultRootPath = vaultRoot.absolutePath
        cachedVaultRoot = vaultRoot
    }
    return unlocked
}



