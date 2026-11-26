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
 * Extracted from [CryptoManagerCredentials]. Portable `.vault` header parsing, keyring packing, and key unwrapping.
 */

private const val ARGON2_MAX_SALT_LEN = 256
private const val ARGON2_MAX_WRAPPED_LEN = 65_536
private const val ARGON2_MAX_KEYRING_LEN = 4_096
private const val ARGON2_KEYRING_MIN_SIZE = 34

internal data class UnwrappedVaultKeys(
    val vmk: ByteArray,
    val kek: ByteArray,
    val storedProtectionType: Int,
    val memoryKb: Int = CryptoManager.ARGON2_MEMORY_KB,
    val iterations: Int = CryptoManager.ARGON2_ITERATIONS
)


internal class KdfCostParams(
    val iterations: Int,
    val memoryKb: Int,
    val parallelism: Int
)

internal class ParsedVaultHeader(
    val kdf: KdfCostParams,
    val storedProtectionType: Int,
    val salt: ByteArray,
    val ivPdk: ByteArray,
    val wrappedVmK: ByteArray,
    val ivVmk: ByteArray,
    val wrappedKeyring: ByteArray
) {
    fun wipe() {
        salt.fill(0)
        ivPdk.fill(0)
        wrappedVmK.fill(0)
        ivVmk.fill(0)
        wrappedKeyring.fill(0)
    }
}

internal fun isValidVaultKdfParams(iterations: Int, memoryKb: Int, parallelism: Int): Boolean {
    return memoryKb in ARGON2_HEADER_MIN_MEMORY_KB..ARGON2_HEADER_MAX_MEMORY_KB &&
        iterations in 1..ARGON2_HEADER_MAX_ITERATIONS &&
        parallelism in 1..ARGON2_HEADER_MAX_PARALLELISM
}

internal fun readExactLengthBytes(dis: DataInputStream, maxLen: Int): ByteArray? {
    val len = dis.readInt()
    if (len !in 1..maxLen) return null
    return ByteArray(len).also { dis.readFully(it) }
}

internal fun parseVaultHeaderStream(dis: DataInputStream): ParsedVaultHeader? {
    val magicBytes = ByteArray(VAULT_HEADER_MAGIC.length)
    dis.readFully(magicBytes)
    val version = dis.readByte().toInt()
    val kdfType = dis.readByte()
    val headerValid = String(magicBytes, Charsets.US_ASCII) == VAULT_HEADER_MAGIC &&
        version == VaultContainer.VAULT_FORMAT_VERSION.toInt() &&
        kdfType == 1.toByte()
    if (!headerValid) return null

    val iterations = dis.readInt()
    val memoryKb = dis.readInt()
    val parallelism = dis.readInt()
    val storedProtectionType = dis.readByte().toInt()
    val salt = readExactLengthBytes(dis, ARGON2_MAX_SALT_LEN) ?: return null
    val ivPdk = ByteArray(GCM_IV_LENGTH).also { dis.readFully(it) }
    val wrappedVmK = readExactLengthBytes(dis, ARGON2_MAX_WRAPPED_LEN) ?: run {
        salt.fill(0)
        return null
    }
    val ivVmk = ByteArray(GCM_IV_LENGTH).also { dis.readFully(it) }
    val wrappedKeyring = readExactLengthBytes(dis, ARGON2_MAX_KEYRING_LEN) ?: run {
        salt.fill(0)
        wrappedVmK.fill(0)
        return null
    }
    return if (isValidVaultKdfParams(iterations, memoryKb, parallelism)) {
        ParsedVaultHeader(
            kdf = KdfCostParams(iterations = iterations, memoryKb = memoryKb, parallelism = parallelism),
            storedProtectionType = storedProtectionType,
            salt = salt,
            ivPdk = ivPdk,
            wrappedVmK = wrappedVmK,
            ivVmk = ivVmk,
            wrappedKeyring = wrappedKeyring
        )
    } else {
        salt.fill(0)
        wrappedVmK.fill(0)
        wrappedKeyring.fill(0)
        null
    }
}

internal fun readParsedVaultHeader(headerFile: File): ParsedVaultHeader? {
    if (!headerFile.exists()) return null
    return try {
        FileInputStream(headerFile).use { fis ->
            DataInputStream(BufferedInputStream(fis)).use { dis ->
                parseVaultHeaderStream(dis)
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun CryptoManager.unwrapKeyringWithVmk(
    vmk: ByteArray,
    ivVmk: ByteArray,
    wrappedKeyring: ByteArray
): ByteArray? {
    var keyring: ByteArray? = null
    return try {
        val keyringCipher = Cipher.getInstance("AES/GCM/NoPadding")
        keyringCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(vmk, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, ivVmk)
        )
        keyring = keyringCipher.doFinal(wrappedKeyring)
        unpackKeyring(keyring)?.second
    } catch (_: Exception) {
        null
    } finally {
        keyring?.fill(0)
    }
}

internal fun CryptoManager.loadKekFromVaultHeaderWithVmk(
    context: Context,
    vmk: ByteArray
): ByteArray? {
    val vaultRoot = locateVaultRootOnDisk(context) ?: findVaultRoot(context) ?: return null
    val header = readParsedVaultHeader(File(vaultRoot, VAULT_HEADER_FILE)) ?: return null
    return try {
        unwrapKeyringWithVmk(vmk, header.ivVmk, header.wrappedKeyring)
    } finally {
        header.wipe()
    }
}

internal fun CryptoManager.unwrapVaultKeysEphemeral(
    headerFile: File,
    secret: CharArray,
    context: Context? = null
): UnwrappedVaultKeys? {
    val header = readParsedVaultHeader(headerFile) ?: return null
    var bundle: ByteArray? = null
    return try {
        val kdfStartedAt = System.nanoTime()
        val wrappingKey = try {
            deriveKeyArgon2id(
                secret,
                header.salt,
                header.kdf.memoryKb,
                header.kdf.iterations,
                header.kdf.parallelism
            )
        } catch (_: OutOfMemoryError) {
            if (context != null) {
                trimGlideMemoryCache(context)
            } else {
                System.gc()
            }
            deriveKeyArgon2id(
                secret,
                header.salt,
                header.kdf.memoryKb,
                header.kdf.iterations,
                header.kdf.parallelism
            )
        }
        val measuredUnlockMs = ((System.nanoTime() - kdfStartedAt) / NS_PER_MS).toInt()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, header.ivPdk))
        bundle = cipher.doFinal(header.wrappedVmK)
        if (bundle.size < ARGON2_KEY_BYTES) {
            return null
        }
        val vmk = bundle.copyOf(ARGON2_KEY_BYTES)
        val kek = unwrapKeyringWithVmk(vmk, header.ivVmk, header.wrappedKeyring)
        if (kek == null) {
            vmk.fill(0)
            null
        } else {
            // A successful unlock on this device gives the exact KDF cost for the current
            // vault header; it overrides the estimate used before/after reinstall or transfer.
            if (context != null && measuredUnlockMs > 0) {
                context.config.vaultUnlockMeasuredMs = measuredUnlockMs
            }
            UnwrappedVaultKeys(
                vmk = vmk,
                kek = kek,
                storedProtectionType = header.storedProtectionType,
                memoryKb = header.kdf.memoryKb,
                iterations = header.kdf.iterations
            )
        }
    } catch (_: OutOfMemoryError) {
        System.gc()
        null
    } catch (_: Exception) {
        null
    } finally {
        header.wipe()
        bundle?.fill(0)
    }
}

/**
 * ADR 0002 §5.1: unlocks a vault key only for a one-off operation, guarantees zeroization on exit,
 * and never promotes the unwrapped keys to [unlockedMasterKey] / [unlockedKek].
 */
fun <T> CryptoManager.withEphemeralVaultKey(
    context: Context,
    secret: CharArray,
    block: (vmk: ByteArray, kek: ByteArray) -> T
): T? {
    val vaultRoot = locateVaultRootOnDisk(context) ?: return null
    val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
    if (!isValidVaultHeader(headerFile)) return null
    val keys = unwrapVaultKeysEphemeral(headerFile, secret, context) ?: return null
    return try {
        block(keys.vmk, keys.kek)
    } finally {
        keys.vmk.fill(0)
        keys.kek.fill(0)
    }
}

internal fun CryptoManager.unlockFromHeaderFile(
    context: Context,
    headerFile: File,
    secret: CharArray
): Boolean {
    val keys = unwrapVaultKeysEphemeral(headerFile, secret, context) ?: return false
    unlockedMasterKey = keys.vmk
    unlockedKek = keys.kek

    context.config.hiddenPasswordHash = "argon2id_aes256_gcm"
    context.config.isHiddenPasswordProtectionOn = true
    context.config.lastCalibratedMemoryKb = keys.memoryKb
    context.config.lastCalibratedIterations = keys.iterations
    if (keys.storedProtectionType in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)) {
        context.config.hiddenProtectionType = keys.storedProtectionType
    }
    if (context.config.showVaultPinDots &&
        keys.storedProtectionType == PROTECTION_PIN &&
        secret.size in 1..VAULT_PIN_DOTS_MAX_LENGTH
    ) {
        context.config.vaultPinLength = secret.size
    } else if (!context.config.showVaultPinDots || secret.size > VAULT_PIN_DOTS_MAX_LENGTH) {
        context.config.showVaultPinDots = false
    }
    return true
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
    val parentDir = headerFile.parentFile
    val tmpFile = if (parentDir != null) {
        File(parentDir, "${headerFile.name}.tmp")
    } else {
        File("${headerFile.path}.tmp")
    }
    FileOutputStream(tmpFile).use { fos ->
        DataOutputStream(fos).use { dos ->
            dos.write(VAULT_HEADER_MAGIC.toByteArray(Charsets.US_ASCII))
            dos.writeByte(VaultContainer.VAULT_FORMAT_VERSION.toInt())
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
    if (!tmpFile.renameTo(headerFile)) {
        tmpFile.copyTo(headerFile, overwrite = true)
        tmpFile.delete()
    }
}

internal fun CryptoManager.packKeyring(keyId: Short, kek: ByteArray): ByteArray =
    ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use { dos ->
            dos.writeShort(keyId.toInt())
            dos.write(kek)
        }
        baos.toByteArray()
    }

private fun CryptoManager.unpackKeyring(bytes: ByteArray): Pair<Short, ByteArray>? {
    if (bytes.size < ARGON2_KEYRING_MIN_SIZE) return null
    DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
        val keyId = dis.readUnsignedShort().toShort()
        val kek = ByteArray(ARGON2_KEY_BYTES)
        dis.readFully(kek)
        return keyId to kek
    }
}

/**
 * Reads the protection type that was written into the portable `.vault` header. The canonical
 * header parser is reused so this can never drift from the format the writer actually produces.
 */
internal fun readVaultProtectionType(headerFile: File): Int? {
    if (!headerFile.exists()) return null
    val header = readParsedVaultHeader(headerFile) ?: return null
    return try {
        header.storedProtectionType.takeIf {
            it in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)
        }
    } finally {
        header.wipe()
    }
}

fun CryptoManager.getVaultProtectionType(context: Context): Int? {
    val vaultRoot = findVaultRoot(context) ?: return null
    return readVaultProtectionType(File(vaultRoot, VAULT_HEADER_FILE))
}

/** Reads the portable KDF cost from the current vault header without needing the password. */
fun CryptoManager.readVaultKdfParams(context: Context): VaultKdfParams? {
    val vaultRoot = findVaultRoot(context) ?: return null
    val header = readParsedVaultHeader(File(vaultRoot, VAULT_HEADER_FILE)) ?: return null
    return try {
        VaultKdfParams(
            memoryKb = header.kdf.memoryKb,
            iterations = header.kdf.iterations,
            parallelism = header.kdf.parallelism
        )
    } finally {
        header.wipe()
    }
}

