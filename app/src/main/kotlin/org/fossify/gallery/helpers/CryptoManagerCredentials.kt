package org.fossify.gallery.helpers

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.virtualAlbumsDB
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_HEADER_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_STR_PREFIX
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.deriveMetadataKey
import java.io.*
import java.nio.CharBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import android.os.Build
import javax.crypto.spec.SecretKeySpec

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerCredentials.
 */

private const val ARGON2_MAX_SALT_LEN = 256
private const val ARGON2_MAX_WRAPPED_LEN = 65_536
private const val ARGON2_MAX_KEYRING_LEN = 4_096
private const val ARGON2_KEYRING_MIN_SIZE = 34

/**
 * Initializes or updates the user's hidden item password/PIN/pattern credentials.
 * Uses calibrated Argon2id (64-256 MiB floor-to-tier, >= 3 iterations, 2 parallelism) + AES-256-GCM.
 * The master bundle (MasterKey + PrivateKey) is written directly to .vault in the root folder.
 */
fun CryptoManager.setupOrUpdateCredentials(
    context: Context,
    secret: CharArray,
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

fun CryptoManager.setupOrUpdateCredentials(
    context: Context,
    secret: String,
    protectionType: Int,
    customTargetDir: File? = null
): String {
    val chars = secret.toCharArray()
    return try {
        setupOrUpdateCredentials(context, chars, protectionType, customTargetDir)
    } finally {
        chars.fill('\u0000')
    }
}


internal fun CryptoManager.setupOrUpdateCredentialsInternal(
    context: Context,
    secret: CharArray,
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
    val localVmk = unlockedMasterKey?.copyOf() ?: ByteArray(ARGON2_KEY_BYTES).also { secureRandom.nextBytes(it) }
    val localKek = if (isNewVault) {
        ByteArray(ARGON2_KEY_BYTES).also { secureRandom.nextBytes(it) }
    } else {
        unlockedKek?.copyOf()
            ?: loadKekFromVaultHeaderWithVmk(context, localVmk)
            ?: run {
                localVmk.fill(0)
                throw IllegalStateException("Vault KEK is not loaded; cannot update credentials safely.")
            }
    }

    val salt = ByteArray(ARGON2_SALT_LENGTH).also { secureRandom.nextBytes(it) }
    var wrappedVmK: ByteArray? = null
    var packedKeyring: ByteArray? = null
    var wrappedKeyring: ByteArray? = null
    try {
        // Brute-force direnci: hedef acilis suresi seciliyse cihazda tek bir referans
        // olcumu yapip iterasyon sayisini hedefe gore ayarlariz. Sonuc .vault'a yazilir;
        // baska cihazda ayni parametreler kullanilir, gercek sure degisir.
        val (argon2MemoryKb, argon2Iterations, argon2Parallelism) = resolveArgon2Params(context)
        val wrappingKey = deriveKeyArgon2idWithTrim(
            context, secret, salt, argon2MemoryKb, argon2Iterations, argon2Parallelism
        )
        val ivPdk = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, ivPdk))
        wrappedVmK = cipher.doFinal(localVmk)

        val ivVmk = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val keyringCipher = Cipher.getInstance("AES/GCM/NoPadding")
        keyringCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(localVmk, "AES"), GCMParameterSpec(GCM_TAG_BITS, ivVmk))
        packedKeyring = packKeyring(1, localKek)
        wrappedKeyring = keyringCipher.doFinal(packedKeyring)

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
        // New header params: let the time be re-estimated for this device and re-measured on unlock.
        context.config.vaultUnlockMeasuredMs = 0
        cachedVaultRoot = vaultRoot
        encryptedFolderCache.clear()

        unlockedMasterKey?.fill(0)
        unlockedKek?.fill(0)
        unlockedMasterKey = localVmk.copyOf()
        unlockedKek = localKek.copyOf()
        CliBridgeManager.ensureActiveBridge(context)
    } finally {
        localVmk.fill(0)
        localKek.fill(0)
        salt.fill(0)
        wrappedVmK?.fill(0)
        packedKeyring?.fill(0)
        wrappedKeyring?.fill(0)
    }
    val marker = "argon2id_aes256_gcm"
    context.config.hiddenPasswordHash = marker
    context.config.hiddenProtectionType = protectionType
    // The dot indicator only makes sense for short PINs; never persist PIN length on disk unless
    // the user has explicitly turned on the per-digit dot setting.
    if (protectionType != PROTECTION_PIN || secret.size > VAULT_PIN_DOTS_MAX_LENGTH) {
        context.config.showVaultPinDots = false
    } else if (context.config.showVaultPinDots) {
        context.config.vaultPinLength = secret.size
    } else {
        context.config.vaultPinLength = 0
    }
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


/**
 * [purpose] kaydın içinde açıkta durur; tek başına bir kimlik doğrulaması değildir. Bu yüzden
 * çağıran beklediği amacı geçirmeli ve eşleşmezse kayıt reddedilmelidir. Aksi halde geçerli bir
 * `FVSTR:` blob'u kategori değiştirilerek (örn. albüm adı yerine JSON yedeği) kopyalanabilir.
 */
fun CryptoManager.decryptStringFromVault(storedText: String, expectedPurpose: String): String? {
    if (!isEncryptedVaultString(storedText)) return storedText

    val kek = activeKek() ?: return null
    return try {
        val raw = Base64.getMimeDecoder().decode(storedText.removePrefix(VAULT_STR_PREFIX))
        DataInputStream(ByteArrayInputStream(raw)).use { dis ->
            val version = dis.readByte()
            if (version != VaultContainer.VAULT_FORMAT_VERSION) return null
            dis.readUnsignedShort() // keyId
            val purpose = dis.readUTF()
            if (purpose != expectedPurpose) return null
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


fun CryptoManager.unlockWithSecret(context: Context, secret: CharArray): Boolean {
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

fun CryptoManager.unlockWithSecret(context: Context, secret: String): Boolean {
    val chars = secret.toCharArray()
    return try {
        unlockWithSecret(context, chars)
    } finally {
        chars.fill('\u0000')
    }
}

/**
 * Authenticates [secret] against the current `.vault` header without touching the
 * in-memory unlocked keys. Used to reject a "new" password identical to the current one.
 * Runs the same (potentially slow) KDF, so callers must use a background thread.
 */
fun CryptoManager.isCurrentVaultSecret(context: Context, secret: CharArray): Boolean {
    val vaultRoot = locateVaultRootOnDisk(context) ?: return false
    val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
    if (!isValidVaultHeader(headerFile)) return false
    val keys = try {
        unwrapVaultKeysEphemeral(headerFile, secret, context) ?: return false
    } catch (_: Throwable) {
        return false
    }
    keys.vmk.fill(0)
    keys.kek.fill(0)
    return true
}

/**
 * Authenticates [secret] against the current `.vault` header under the existing KDF and
 * atomically re-wraps the same VMK/KEK under a newly derived PDK using current KDF settings.
 */
fun CryptoManager.rewrapVaultHeaderWithCurrentKdf(context: Context, secret: CharArray): Boolean {
    val vaultRoot = locateVaultRootOnDisk(context) ?: return false
    val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
    if (!isValidVaultHeader(headerFile)) return false
    val keys = unwrapVaultKeysEphemeral(headerFile, secret, context) ?: return false

    val salt = ByteArray(ARGON2_SALT_LENGTH).also { secureRandom.nextBytes(it) }
    var wrappedVmK: ByteArray? = null
    var packedKeyring: ByteArray? = null
    var wrappedKeyring: ByteArray? = null
    return try {
        val (argon2MemoryKb, argon2Iterations, argon2Parallelism) = resolveArgon2Params(context)
        val wrappingKey = deriveKeyArgon2idWithTrim(
            context, secret, salt, argon2MemoryKb, argon2Iterations, argon2Parallelism
        )
        val ivPdk = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, ivPdk))
        wrappedVmK = cipher.doFinal(keys.vmk)

        val ivVmk = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val keyringCipher = Cipher.getInstance("AES/GCM/NoPadding")
        keyringCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.vmk, "AES"), GCMParameterSpec(GCM_TAG_BITS, ivVmk))
        packedKeyring = packKeyring(1, keys.kek)
        wrappedKeyring = keyringCipher.doFinal(packedKeyring)

        val protectionType = getVaultProtectionType(context) ?: keys.storedProtectionType
        writeVaultHeader(
            headerFile, salt, argon2MemoryKb, argon2Iterations, argon2Parallelism,
            ivPdk, wrappedVmK, ivVmk, wrappedKeyring, protectionType
        )
        val data = Base64.getEncoder().encodeToString(salt) + ":" +
            Base64.getEncoder().encodeToString(ivPdk) + ":" +
            Base64.getEncoder().encodeToString(wrappedVmK)
        context.config.vaultWrappedKeyData = data
        context.config.hiddenPasswordHash = "argon2id_aes256_gcm"
        context.config.vaultRootPath = vaultRoot.absolutePath
        // Re-wrapped with new KDF params: drop the measurement taken under the old params.
        context.config.vaultUnlockMeasuredMs = 0
        cachedVaultRoot = vaultRoot
        true
    } catch (_: OutOfMemoryError) {
        trimGlideMemoryCache(context)
        false
    } catch (_: Exception) {
        false
    } finally {
        keys.vmk.fill(0)
        keys.kek.fill(0)
        salt.fill(0)
        wrappedVmK?.fill(0)
        packedKeyring?.fill(0)
        wrappedKeyring?.fill(0)
    }
}



