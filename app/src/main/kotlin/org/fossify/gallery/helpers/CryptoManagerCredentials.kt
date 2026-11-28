package org.fossify.gallery.helpers

import android.content.Context
import java.util.Base64
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.virtualAlbumsDB
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_STR_PREFIX
import java.io.*

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerCredentials.
 */

private const val VAULT_STR_KEY_ID_BYTES = 2
private const val BYTE_MASK = 0xff
private const val BITS_PER_BYTE = 8
private const val MAX_VAULT_STR_CIPHER_BYTES = 8192

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
    // Kanonik kasa: VMK -> keyring -> KEK. Sifre degistirildiğinde Rust oturumundaki VMK/KEK korunur.
    val isNewVault = !isVaultUnlocked
    val secretBytes = NativeVaultSession.secretCharsToUtf8Bytes(secret)
    var parsedHeader: ParsedVaultHeader? = null
    var committed = false
    try {
        val (argon2MemoryKb, argon2Iterations, argon2Parallelism) = resolveArgon2Params(context)
        val headerBytes = try {
            NativeVaultSession.createOrUpdateVaultHeaderNative(
                secretBytes, argon2MemoryKb, argon2Iterations, argon2Parallelism, protectionType, isNewVault
            )
        } catch (_: OutOfMemoryError) {
            trimGlideMemoryCache(context)
            NativeVaultSession.createOrUpdateVaultHeaderNative(
                secretBytes, argon2MemoryKb, argon2Iterations, argon2Parallelism, protectionType, isNewVault
            )
        }
        parsedHeader = DataInputStream(ByteArrayInputStream(headerBytes)).use { parseVaultHeaderStream(it) }
            ?: throw IOException("Native vault header serialization failed")

        clearBiometricKey(context)

        // Write vault root header (.vault) on disk for full multi-device portability
        val vaultRoot = createVaultRoot(context, customTargetDir)
            ?: throw IOException("Failed to create writable vault directory")
        val vaultHeader = File(vaultRoot, VAULT_HEADER_FILE)
        writeVaultHeaderBytes(vaultHeader, headerBytes)

        val data = Base64.getEncoder().encodeToString(parsedHeader.salt) + ":" +
            Base64.getEncoder().encodeToString(parsedHeader.ivPdk) + ":" +
            Base64.getEncoder().encodeToString(parsedHeader.wrappedVmK)
        context.config.vaultWrappedKeyData = data
        context.config.vaultRootPath = vaultRoot.absolutePath
        // New header params: let the time be re-estimated for this device and re-measured on unlock.
        context.config.vaultUnlockMeasuredMs = 0
        cachedVaultRoot = vaultRoot
        encryptedFolderCache.clear()

        CliBridgeManager.ensureActiveBridge(context)
        committed = true
    } finally {
        if (!committed && isNewVault) {
            NativeVaultSession.lockSessionNative()
        }
        secretBytes.fill(0)
        parsedHeader?.wipe()
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
    if (!isVaultUnlocked) return plainText
    val packed = NativeVaultSession.encryptVaultStringNative(
        purpose.toByteArray(Charsets.UTF_8),
        plainText.toByteArray(Charsets.UTF_8)
    )
    if (packed.size <= VAULT_STR_KEY_ID_BYTES + GCM_IV_LENGTH) return plainText
    val keyId = ((packed[0].toInt() and BYTE_MASK) shl BITS_PER_BYTE) or (packed[1].toInt() and BYTE_MASK)
    val iv = packed.copyOfRange(VAULT_STR_KEY_ID_BYTES, VAULT_STR_KEY_ID_BYTES + GCM_IV_LENGTH)
    val enc = packed.copyOfRange(VAULT_STR_KEY_ID_BYTES + GCM_IV_LENGTH, packed.size)
    val payload = ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use { dos ->
            dos.writeByte(VaultContainer.VAULT_FORMAT_VERSION.toInt())
            dos.writeShort(keyId)
            dos.writeUTF(purpose)
            dos.write(iv)
            dos.writeInt(enc.size)
            dos.write(enc)
        }
        baos.toByteArray()
    }
    return VAULT_STR_PREFIX + Base64.getEncoder().encodeToString(payload)
}


/**
 * [purpose] kaydın içinde açıkta durur; tek başına bir kimlik doğrulaması değildir. Bu yüzden
 * çağıran beklediği amacı geçirmeli ve eşleşmezse kayıt reddedilmelidir. Aksi halde geçerli bir
 * `FVSTR:` blob'u kategori değiştirilerek (örn. albüm adı yerine JSON yedeği) kopyalanabilir.
 */
fun CryptoManager.decryptStringFromVault(storedText: String, expectedPurpose: String): String? {
    if (!isEncryptedVaultString(storedText)) return storedText
    if (!isVaultUnlocked) return null

    return try {
        val raw = Base64.getMimeDecoder().decode(storedText.removePrefix(VAULT_STR_PREFIX))
        DataInputStream(ByteArrayInputStream(raw)).use { dis ->
            val version = dis.readByte()
            dis.readUnsignedShort() // keyId
            val purpose = dis.readUTF()
            val iv = ByteArray(GCM_IV_LENGTH).also { dis.readFully(it) }
            val len = dis.readInt()
            val valid = version == VaultContainer.VAULT_FORMAT_VERSION &&
                purpose == expectedPurpose &&
                len in 1..MAX_VAULT_STR_CIPHER_BYTES
            if (!valid) {
                null
            } else {
                val enc = ByteArray(len).also { dis.readFully(it) }
                NativeVaultSession.decryptVaultStringNative(
                    purpose.toByteArray(Charsets.UTF_8),
                    iv,
                    enc
                )?.let { String(it, Charsets.UTF_8) }
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
    val headerBytes = runCatching { headerFile.readBytes() }.getOrNull() ?: return false
    val secretBytes = NativeVaultSession.secretCharsToUtf8Bytes(secret)
    return try {
        NativeVaultSession.verifyVaultSecretNative(headerBytes, secretBytes)
    } catch (_: Throwable) {
        false
    } finally {
        secretBytes.fill(0)
    }
}

/**
 * Authenticates [secret] against the current `.vault` header under the existing KDF and
 * atomically re-wraps the same VMK/KEK under a newly derived PDK using current KDF settings.
 */
fun CryptoManager.rewrapVaultHeaderWithCurrentKdf(context: Context, secret: CharArray): Boolean {
    val vaultRoot = locateVaultRootOnDisk(context) ?: return false
    val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
    if (!isValidVaultHeader(headerFile)) return false
    val existingParsed = readParsedVaultHeader(headerFile) ?: return false
    val storedProtectionType = existingParsed.storedProtectionType
    existingParsed.wipe()
    val headerBytes = runCatching { headerFile.readBytes() }.getOrNull() ?: return false
    val secretBytes = NativeVaultSession.secretCharsToUtf8Bytes(secret)
    return try {
        val (argon2MemoryKb, argon2Iterations, argon2Parallelism) = resolveArgon2Params(context)
        val protectionType = getVaultProtectionType(context) ?: storedProtectionType
        val newHeaderBytes = try {
            NativeVaultSession.rewrapVaultHeaderNative(
                headerBytes, secretBytes, argon2MemoryKb, argon2Iterations, argon2Parallelism, protectionType
            )
        } catch (_: OutOfMemoryError) {
            trimGlideMemoryCache(context)
            NativeVaultSession.rewrapVaultHeaderNative(
                headerBytes, secretBytes, argon2MemoryKb, argon2Iterations, argon2Parallelism, protectionType
            )
        }
        val persisted = newHeaderBytes != null &&
            persistRewrappedVaultHeader(context, vaultRoot, headerFile, newHeaderBytes)
        if (persisted) {
            CliBridgeManager.syncRewrappedHeader(context, vaultRoot, headerBytes)
        }
        persisted
    } catch (_: OutOfMemoryError) {
        trimGlideMemoryCache(context)
        false
    } catch (_: Exception) {
        false
    } finally {
        secretBytes.fill(0)
    }
}



