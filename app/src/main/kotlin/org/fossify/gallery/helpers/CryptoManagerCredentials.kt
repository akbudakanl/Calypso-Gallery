package org.fossify.gallery.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
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

internal const val ARGON2_MIN_MEMORY_KB = 64 * 1024
internal const val ARGON2_HIGH_MEMORY_KB = 128 * 1024
internal const val ARGON2_MIN_ITERATIONS = 3
internal const val ARGON2_MAX_CALIBRATED_ITERATIONS = 100
internal const val ARGON2_HEADER_MIN_MEMORY_KB = 8_192
internal const val ARGON2_HEADER_MAX_MEMORY_KB = 262_144
internal const val ARGON2_HEADER_MAX_ITERATIONS = 256
internal const val ARGON2_HEADER_MAX_PARALLELISM = 8

private const val ARGON2_KEY_BYTES = 32
private const val ARGON2_MAX_SALT_LEN = 256
private const val ARGON2_MAX_WRAPPED_LEN = 65_536
private const val ARGON2_MAX_KEYRING_LEN = 4_096
private const val ARGON2_KEYRING_MIN_SIZE = 34
private const val ARGON2_V3_MIN_VERSION = 3
private const val ARGON2_MIN_MEASURED_MS = 100
private const val ARGON2_MAX_CACHED_MS = 30_000
private const val ARGON2_DEFAULT_FALLBACK_STD_MS = 2_400
private const val ARGON2_MIN_TARGET_MS = 100
private const val ARGON2_MAX_TARGET_MS = 10_000
private const val GLIDE_TRIM_TIMEOUT_MS = 500L
private const val BYTES_PER_KIB = 1024L
private const val NS_PER_MS = 1_000_000L

private fun trimGlideMemoryCache(context: Context) {
    val appContext = context.applicationContext ?: return
    val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull() ?: return
    if (Looper.myLooper() == mainLooper) {
        runCatching { Glide.get(appContext).clearMemory() }
    } else {
        val latch = CountDownLatch(1)
        val posted = runCatching {
            Handler(mainLooper).post {
                try {
                    Glide.get(appContext).clearMemory()
                } catch (_: Throwable) {
                } finally {
                    latch.countDown()
                }
            }
        }.getOrDefault(false)
        if (posted) {
            runCatching { latch.await(GLIDE_TRIM_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        }
    }
    System.gc()
}

private fun charsToUtf8Bytes(secret: CharArray): ByteArray {
    val byteBuffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(secret))
    val exact = ByteArray(byteBuffer.remaining())
    byteBuffer.get(exact)
    if (byteBuffer.hasArray()) {
        byteBuffer.array().fill(0)
    }
    return exact
}

fun CryptoManager.deriveKeyArgon2id(
    secret: CharArray,
    salt: ByteArray,
    memoryKb: Int = ARGON2_MEMORY_KB,
    iterations: Int = ARGON2_ITERATIONS,
    parallelism: Int = ARGON2_PARALLELISM
): SecretKeySpec {
    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withIterations(iterations)
        .withMemoryAsKB(memoryKb)
        .withParallelism(parallelism)
        .build()
    val keyBytes = ByteArray(ARGON2_KEY_BYTES)
    val secretBytes = charsToUtf8Bytes(secret)
    try {
        try {
            val generator = Argon2BytesGenerator()
            generator.init(params)
            generator.generateBytes(secretBytes, keyBytes, 0, keyBytes.size)
        } catch (_: OutOfMemoryError) {
            keyBytes.fill(0)
            System.gc()
            val retryGenerator = Argon2BytesGenerator()
            retryGenerator.init(params)
            retryGenerator.generateBytes(secretBytes, keyBytes, 0, keyBytes.size)
        }
        return SecretKeySpec(keyBytes, "AES")
    } finally {
        secretBytes.fill(0)
        keyBytes.fill(0)
    }
}

fun CryptoManager.deriveKeyArgon2id(
    secret: String,
    salt: ByteArray,
    memoryKb: Int = ARGON2_MEMORY_KB,
    iterations: Int = ARGON2_ITERATIONS,
    parallelism: Int = ARGON2_PARALLELISM
): SecretKeySpec {
    val chars = secret.toCharArray()
    return try {
        deriveKeyArgon2id(chars, salt, memoryKb, iterations, parallelism)
    } finally {
        chars.fill('\u0000')
    }
}


internal data class UnwrappedVaultKeys(
    val vmk: ByteArray,
    val kek: ByteArray,
    val storedProtectionType: Int,
    val memoryKb: Int = CryptoManager.ARGON2_MEMORY_KB,
    val iterations: Int = CryptoManager.ARGON2_ITERATIONS
)

private class KdfCostParams(
    val iterations: Int,
    val memoryKb: Int,
    val parallelism: Int
)

private class ParsedVaultHeader(
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

private fun isValidVaultKdfParams(iterations: Int, memoryKb: Int, parallelism: Int): Boolean {
    return memoryKb in ARGON2_HEADER_MIN_MEMORY_KB..ARGON2_HEADER_MAX_MEMORY_KB &&
        iterations in 1..ARGON2_HEADER_MAX_ITERATIONS &&
        parallelism in 1..ARGON2_HEADER_MAX_PARALLELISM
}

private fun readExactLengthBytes(dis: DataInputStream, maxLen: Int): ByteArray? {
    val len = dis.readInt()
    if (len !in 1..maxLen) return null
    return ByteArray(len).also { dis.readFully(it) }
}

private fun parseVaultHeaderStream(dis: DataInputStream): ParsedVaultHeader? {
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

private fun readParsedVaultHeader(headerFile: File): ParsedVaultHeader? {
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

private fun CryptoManager.packKeyring(keyId: Short, kek: ByteArray): ByteArray =
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
                if (version >= ARGON2_V3_MIN_VERSION) {
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


private fun CryptoManager.deriveKeyArgon2idWithTrim(
    context: Context,
    secret: CharArray,
    salt: ByteArray,
    memoryKb: Int,
    iterations: Int,
    parallelism: Int
): SecretKeySpec {
    return try {
        deriveKeyArgon2id(secret, salt, memoryKb, iterations, parallelism)
    } catch (_: OutOfMemoryError) {
        trimGlideMemoryCache(context)
        deriveKeyArgon2id(secret, salt, memoryKb, iterations, parallelism)
    }
}

internal fun calculateTieredArgon2Params(
    targetMs: Int,
    std64Ms: Int,
    maxHeapKb: Long,
    measure128Ms: (() -> Long)? = null
): Triple<Int, Int, Boolean> {
    val safeStd64Ms = std64Ms.coerceAtLeast(ARGON2_MIN_MEASURED_MS)
    val maxTarget = maxOf(safeStd64Ms, ARGON2_MAX_TARGET_MS)
    val effectiveTargetMs = targetMs.coerceIn(safeStd64Ms, maxTarget)
    val canAllocate128Mib = (maxHeapKb / 2) >= ARGON2_HIGH_MEMORY_KB
    val estimated128FloorMs = safeStd64Ms * 2

    var memoryKb = ARGON2_MIN_MEMORY_KB
    var rawIter = Math.round(effectiveTargetMs / (safeStd64Ms.toDouble() / ARGON2_MIN_ITERATIONS)).toInt()
    var memoryClamped = effectiveTargetMs >= estimated128FloorMs && !canAllocate128Mib

    if (canAllocate128Mib && effectiveTargetMs >= estimated128FloorMs) {
        val measured128 = measure128Ms?.invoke() ?: estimated128FloorMs.toLong()
        if (measured128 > 0L) {
            memoryKb = ARGON2_HIGH_MEMORY_KB
            val perIter128Ms = measured128.toDouble() / ARGON2_MIN_ITERATIONS
            rawIter = Math.round(effectiveTargetMs / perIter128Ms).toInt()
        } else {
            memoryClamped = true
        }
    }

    val iterations = rawIter.coerceIn(ARGON2_MIN_ITERATIONS, ARGON2_MAX_CALIBRATED_ITERATIONS)
    val clamped = memoryClamped ||
        rawIter < ARGON2_MIN_ITERATIONS ||
        rawIter > ARGON2_MAX_CALIBRATED_ITERATIONS ||
        targetMs < safeStd64Ms
    return Triple(memoryKb, iterations, clamped)
}

fun CryptoManager.measureStandardArgon2Ms(context: Context, force: Boolean = false): Int {
    val cached = context.config.cachedStdArgon2Ms
    if (!force && cached in ARGON2_MIN_MEASURED_MS..ARGON2_MAX_CACHED_MS) {
        return cached
    }
    val salt = ByteArray(ARGON2_SALT_LENGTH)
    val probeChars = "cipher-calibration".toCharArray()
    val measured = try {
        val t0 = System.nanoTime()
        deriveKeyArgon2idWithTrim(
            context,
            probeChars,
            salt,
            ARGON2_MIN_MEMORY_KB,
            ARGON2_MIN_ITERATIONS,
            ARGON2_PARALLELISM
        )
        ((System.nanoTime() - t0) / NS_PER_MS).toInt().coerceIn(ARGON2_MIN_TARGET_MS, ARGON2_MAX_TARGET_MS)
    } catch (_: OutOfMemoryError) {
        ARGON2_DEFAULT_FALLBACK_STD_MS
    } finally {
        probeChars.fill('\u0000')
    }
    context.config.cachedStdArgon2Ms = measured
    if (!isVaultUnlocked) {
        val maxHeapKb = (Runtime.getRuntime().maxMemory() / BYTES_PER_KIB).coerceAtLeast(1L)
        val (memKb, iters, clamped) = calculateTieredArgon2Params(
            targetMs = context.config.unlockTargetMs,
            std64Ms = measured,
            maxHeapKb = maxHeapKb
        )
        context.config.lastCalibratedMemoryKb = memKb
        context.config.lastCalibratedIterations = iters
        context.config.argon2CalibrationClamped = clamped
    }
    return measured
}

private fun CryptoManager.measureHighMemoryArgon2Ms(context: Context): Long {
    val salt = ByteArray(ARGON2_SALT_LENGTH)
    val probeChars = "cipher-calibration".toCharArray()
    return try {
        val t0 = System.nanoTime()
        deriveKeyArgon2idWithTrim(
            context,
            probeChars,
            salt,
            ARGON2_HIGH_MEMORY_KB,
            ARGON2_MIN_ITERATIONS,
            ARGON2_PARALLELISM
        )
        ((System.nanoTime() - t0) / NS_PER_MS).coerceAtLeast(1L)
    } catch (_: OutOfMemoryError) {
        trimGlideMemoryCache(context)
        -1L
    } finally {
        probeChars.fill('\u0000')
    }
}

/**
 * Standart (64 MiB / 3 tur) tabanindan yukari kademeli (64->128 MiB) kalibrasyon yapar.
 * NOT: Agir is oldugu icin arka planda cagrilmalidir.
 */
internal fun CryptoManager.resolveArgon2Params(context: Context): Triple<Int, Int, Int> {
    val maxHeapKb = (Runtime.getRuntime().maxMemory() / BYTES_PER_KIB).coerceAtLeast(1L)
    val std64Ms = measureStandardArgon2Ms(context)
    val (memoryKb, iterations, clamped) = calculateTieredArgon2Params(
        targetMs = context.config.unlockTargetMs,
        std64Ms = std64Ms,
        maxHeapKb = maxHeapKb,
        measure128Ms = { measureHighMemoryArgon2Ms(context) }
    )
    context.config.argon2CalibrationClamped = clamped
    context.config.lastCalibratedMemoryKb = memoryKb
    context.config.lastCalibratedIterations = iterations
    return Triple(memoryKb, iterations, ARGON2_PARALLELISM)
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
        // baska cihazda ayni parametreler kullanilir, gercek sure degisir (KeePass gibi).
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
        cachedVaultRoot = vaultRoot
        encryptedFolderCache.clear()

        unlockedMasterKey?.fill(0)
        unlockedKek?.fill(0)
        unlockedMasterKey = localVmk.copyOf()
        unlockedKek = localKek.copyOf()
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



