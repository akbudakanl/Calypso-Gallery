package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_HEADER_MAGIC
import java.io.*
import java.util.Base64


/**
 * Extracted from [CryptoManagerCredentials]. Portable `.vault` header parsing and native Rust session unlock.
 */

private const val ARGON2_MAX_SALT_LEN = 256
private const val ARGON2_MAX_WRAPPED_LEN = 65_536
private const val ARGON2_MAX_KEYRING_LEN = 4_096


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

private fun applyUnlockConfigFromHeader(
    context: Context,
    parsed: ParsedVaultHeader,
    secretLength: Int
) {
    context.config.hiddenPasswordHash = "argon2id_aes256_gcm"
    context.config.isHiddenPasswordProtectionOn = true
    context.config.lastCalibratedMemoryKb = parsed.kdf.memoryKb
    context.config.lastCalibratedIterations = parsed.kdf.iterations
    if (parsed.storedProtectionType in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)) {
        context.config.hiddenProtectionType = parsed.storedProtectionType
    }
    if (context.config.showVaultPinDots &&
        parsed.storedProtectionType == PROTECTION_PIN &&
        secretLength in 1..VAULT_PIN_DOTS_MAX_LENGTH
    ) {
        context.config.vaultPinLength = secretLength
    } else if (!context.config.showVaultPinDots || secretLength > VAULT_PIN_DOTS_MAX_LENGTH) {
        context.config.showVaultPinDots = false
    }
}

internal fun CryptoManager.unlockFromHeaderFile(
    context: Context,
    headerFile: File,
    secret: CharArray
): Boolean {
    val parsed = readParsedVaultHeader(headerFile) ?: return false
    val headerBytes = runCatching { headerFile.readBytes() }.getOrNull()
    if (headerBytes == null) {
        parsed.wipe()
        return false
    }
    val secretBytes = NativeVaultSession.secretCharsToUtf8Bytes(secret)
    val unlocked = try {
        val kdfStartedAt = System.nanoTime()
        val ok = try {
            NativeVaultSession.unlockVaultHeaderNative(headerBytes, secretBytes)
        } catch (_: OutOfMemoryError) {
            trimGlideMemoryCache(context)
            NativeVaultSession.unlockVaultHeaderNative(headerBytes, secretBytes)
        }
        val measuredUnlockMs = ((System.nanoTime() - kdfStartedAt) / NS_PER_MS).toInt()
        if (ok && measuredUnlockMs > 0) {
            context.config.vaultUnlockMeasuredMs = measuredUnlockMs
        }
        ok
    } catch (_: OutOfMemoryError) {
        System.gc()
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: IllegalStateException) {
        false
    } finally {
        secretBytes.fill(0)
    }
    return try {
        if (unlocked) {
            applyUnlockConfigFromHeader(context, parsed, secret.size)
        }
        unlocked
    } finally {
        parsed.wipe()
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

internal fun writeVaultHeaderBytes(headerFile: File, headerBytes: ByteArray) {
    val parentDir = headerFile.parentFile
    val tmpFile = if (parentDir != null) {
        File(parentDir, "${headerFile.name}.tmp")
    } else {
        File("${headerFile.path}.tmp")
    }
    FileOutputStream(tmpFile).use { fos ->
        fos.write(headerBytes)
        fos.flush()
        fos.fd.sync()
    }
    if (!tmpFile.renameTo(headerFile)) {
        tmpFile.copyTo(headerFile, overwrite = true)
        tmpFile.delete()
    }
}

internal fun CryptoManager.persistRewrappedVaultHeader(
    context: Context,
    vaultRoot: File,
    headerFile: File,
    newHeaderBytes: ByteArray
): Boolean {
    val newParsed = DataInputStream(ByteArrayInputStream(newHeaderBytes)).use { parseVaultHeaderStream(it) }
        ?: return false
    return try {
        writeVaultHeaderBytes(headerFile, newHeaderBytes)
        val data = Base64.getEncoder().encodeToString(newParsed.salt) + ":" +
            Base64.getEncoder().encodeToString(newParsed.ivPdk) + ":" +
            Base64.getEncoder().encodeToString(newParsed.wrappedVmK)
        context.config.vaultWrappedKeyData = data
        context.config.hiddenPasswordHash = "argon2id_aes256_gcm"
        context.config.vaultRootPath = vaultRoot.absolutePath
        // Re-wrapped with new KDF params: drop the measurement taken under the old params.
        context.config.vaultUnlockMeasuredMs = 0
        cachedVaultRoot = vaultRoot
        true
    } finally {
        newParsed.wipe()
    }
}

/**
 * .vault başlığı: PDK -> VMK ve VMK -> keyring olmak üzere iki sarma katmanı.
 * Duzen:
 *   magic(5) | version(1) | kdf(1) | iter(4) | mem(4) | par(4) | prot(1) |
 *   saltLen(4) | salt | ivPdk(12) | wrappedVmKLen(4) | wrappedVmK |
 *   ivVmk(12) | keyringLen(4) | keyring
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
    val bytes = ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use { dos ->
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
        }
        baos.toByteArray()
    }
    writeVaultHeaderBytes(headerFile, bytes)
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

