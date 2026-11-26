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
 * Extracted from [CryptoManagerCredentials]. Argon2id KDF derivation and device calibration.
 */

internal const val ARGON2_MIN_MEMORY_KB = 64 * 1024
internal const val ARGON2_HIGH_MEMORY_KB = 128 * 1024
internal const val ARGON2_ULTRA_MEMORY_KB = 256 * 1024
internal const val ARGON2_ULTRA_MIN_TARGET_MS = 3_000
internal const val ARGON2_128_MIN_TOTAL_RAM_MIB = 1_536L
internal const val ARGON2_256_MIN_TOTAL_RAM_MIB = 5_120L
internal const val ARGON2_MIN_ITERATIONS = 3
internal const val ARGON2_MAX_CALIBRATED_ITERATIONS = 100
internal const val ARGON2_HEADER_MIN_MEMORY_KB = 8_192
internal const val ARGON2_HEADER_MAX_MEMORY_KB = 262_144
internal const val ARGON2_HEADER_MAX_ITERATIONS = 256
internal const val ARGON2_HEADER_MAX_PARALLELISM = 8

internal const val ARGON2_KEY_BYTES = 32
internal const val ARGON2_MIN_MEASURED_MS = 20
private const val ARGON2_MAX_CACHED_MS = 30_000
private const val ARGON2_DEFAULT_FALLBACK_STD_MS = 2_400
internal const val ARGON2_MAX_TARGET_MS = 10_000
private const val GLIDE_TRIM_TIMEOUT_MS = 500L
private const val BYTES_PER_MIB = 1024L * 1024L
internal const val NS_PER_MS = 1_000_000L
private const val TIER_256_STD_MULTIPLIER = 4

internal fun ensureKdfEngineFresh(config: Config) {
    if (config.kdfEngineVersion != KDF_ENGINE_RUST_V1) {
        config.cachedStdArgon2Ms = 0
        config.vaultUnlockMeasuredMs = 0
        config.kdfEngineVersion = KDF_ENGINE_RUST_V1
    }
}

internal fun trimGlideMemoryCache(context: Context) {
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
    val secretBytes = charsToUtf8Bytes(secret)
    val keyBytes = try {
        try {
            NativeArgon2.deriveKeyArgon2idNative(
                secretBytes,
                salt,
                memoryKb,
                iterations,
                parallelism,
                ARGON2_KEY_BYTES
            )
        } catch (_: OutOfMemoryError) {
            System.gc()
            NativeArgon2.deriveKeyArgon2idNative(
                secretBytes,
                salt,
                memoryKb,
                iterations,
                parallelism,
                ARGON2_KEY_BYTES
            )
        }
    } finally {
        secretBytes.fill(0)
    }
    return try {
        SecretKeySpec(keyBytes, "AES")
    } finally {
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

/** Parolanin bilinmesini gerektirmeyen, tasinabilir .vault basligindaki KDF maliyeti. */
data class VaultKdfParams(
    val memoryKb: Int,
    val iterations: Int,
    val parallelism: Int
)

/**
 * Reverse calculation: given the stored Argon2 cost and this device's measured 64 MiB / 3 rounds baseline,
 * estimate the real unlock time on THIS device. Memory scales roughly linearly, so 128 MiB
 * costs about twice as much per round as 64 MiB.
 */
fun estimateVaultUnlockMs(std64Ms: Int, params: VaultKdfParams): Int {
    val safeStd64Ms = std64Ms.coerceAtLeast(ARGON2_MIN_MEASURED_MS).toDouble()
    val memoryFactor = (params.memoryKb.toDouble() / ARGON2_MIN_MEMORY_KB).coerceAtLeast(1.0)
    val perIterationMs = safeStd64Ms / ARGON2_MIN_ITERATIONS * memoryFactor
    return Math.round(perIterationMs * params.iterations).toInt().coerceAtLeast(1)
}

/**
 * The time to show for the EXISTING vault on the current device:
 * measured during a real unlock when available, otherwise estimated from the portable
 * header cost and this device's standard KDF speed. Returns 0 when a vault is not available
 * or the local baseline has not been measured yet.
 */
fun CryptoManager.effectiveVaultUnlockMs(
    context: Context,
    knownParams: VaultKdfParams? = null
): Int {
    ensureKdfEngineFresh(context.config)
    val measured = context.config.vaultUnlockMeasuredMs
    if (measured > 0) return measured
    val std64Ms = context.config.cachedStdArgon2Ms
    if (std64Ms <= 0) return 0
    val params = knownParams ?: readVaultKdfParams(context) ?: return 0
    return estimateVaultUnlockMs(std64Ms, params)
}

internal fun CryptoManager.deriveKeyArgon2idWithTrim(
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

internal data class DeviceMemoryCapabilities(
    val totalRamMib: Long,
    val is64Bit: Boolean
) {
    val canAllocate128Mib: Boolean get() = totalRamMib >= ARGON2_128_MIN_TOTAL_RAM_MIB
    val canAllocate256Mib: Boolean get() = is64Bit && totalRamMib >= ARGON2_256_MIN_TOTAL_RAM_MIB
}

internal fun deviceMemoryCapabilities(context: Context): DeviceMemoryCapabilities {
    val totalRamMib = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@runCatching 0L
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem / BYTES_PER_MIB
    }.getOrDefault(0L)
    val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    return DeviceMemoryCapabilities(totalRamMib = totalRamMib, is64Bit = is64Bit)
}

internal fun calculateTieredArgon2Params(
    targetMs: Int,
    std64Ms: Int,
    capabilities: DeviceMemoryCapabilities,
    measure128Ms: (() -> Long)? = null,
    measure256Ms: (() -> Long)? = null
): Triple<Int, Int, Boolean> {
    val safeStd64Ms = std64Ms.coerceAtLeast(ARGON2_MIN_MEASURED_MS)
    val maxTarget = maxOf(safeStd64Ms, ARGON2_MAX_TARGET_MS)
    val effectiveTargetMs = targetMs.coerceIn(safeStd64Ms, maxTarget)
    val estimated128FloorMs = safeStd64Ms * 2
    val estimated256FloorMs = safeStd64Ms * TIER_256_STD_MULTIPLIER
    val ultraTierThresholdMs = maxOf(estimated256FloorMs, ARGON2_ULTRA_MIN_TARGET_MS)

    var memoryKb = ARGON2_MIN_MEMORY_KB
    var rawIter = Math.round(effectiveTargetMs / (safeStd64Ms.toDouble() / ARGON2_MIN_ITERATIONS)).toInt()
    var memoryClamped = false

    if (effectiveTargetMs >= ultraTierThresholdMs && capabilities.canAllocate256Mib) {
        val measured256 = measure256Ms?.invoke() ?: estimated256FloorMs.toLong()
        if (measured256 > 0L) {
            memoryKb = ARGON2_ULTRA_MEMORY_KB
            val perIter256Ms = measured256.toDouble() / ARGON2_MIN_ITERATIONS
            rawIter = Math.round(effectiveTargetMs / perIter256Ms).toInt()
        } else {
            memoryClamped = true
        }
    }

    if (memoryKb == ARGON2_MIN_MEMORY_KB && effectiveTargetMs >= estimated128FloorMs) {
        if (capabilities.canAllocate128Mib) {
            val measured128 = measure128Ms?.invoke() ?: estimated128FloorMs.toLong()
            if (measured128 > 0L) {
                memoryKb = ARGON2_HIGH_MEMORY_KB
                val perIter128Ms = measured128.toDouble() / ARGON2_MIN_ITERATIONS
                rawIter = Math.round(effectiveTargetMs / perIter128Ms).toInt()
            } else {
                memoryClamped = true
            }
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
    ensureKdfEngineFresh(context.config)
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
        ((System.nanoTime() - t0) / NS_PER_MS).toInt().coerceIn(ARGON2_MIN_MEASURED_MS, ARGON2_MAX_TARGET_MS)
    } catch (_: OutOfMemoryError) {
        ARGON2_DEFAULT_FALLBACK_STD_MS
    } finally {
        probeChars.fill('\u0000')
    }
    context.config.cachedStdArgon2Ms = measured
    if (!isVaultUnlocked) {
        val capabilities = deviceMemoryCapabilities(context)
        val (memKb, iters, clamped) = calculateTieredArgon2Params(
            targetMs = context.config.unlockTargetMs,
            std64Ms = measured,
            capabilities = capabilities
        )
        context.config.lastCalibratedMemoryKb = memKb
        context.config.lastCalibratedIterations = iters
        context.config.argon2CalibrationClamped = clamped
    }
    return measured
}

private fun CryptoManager.measureMemoryTierArgon2Ms(context: Context, memoryKb: Int): Long {
    val salt = ByteArray(ARGON2_SALT_LENGTH)
    val probeChars = "cipher-calibration".toCharArray()
    return try {
        val t0 = System.nanoTime()
        deriveKeyArgon2idWithTrim(
            context,
            probeChars,
            salt,
            memoryKb,
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

private fun CryptoManager.measureHighMemoryArgon2Ms(context: Context): Long =
    measureMemoryTierArgon2Ms(context, ARGON2_HIGH_MEMORY_KB)

private fun CryptoManager.measureUltraMemoryArgon2Ms(context: Context): Long =
    measureMemoryTierArgon2Ms(context, ARGON2_ULTRA_MEMORY_KB)

/**
 * Standart (64 MiB / 3 tur) tabanindan yukari kademeli (64->128->256 MiB) kalibrasyon yapar.
 * NOT: Agir is oldugu icin arka planda cagrilmalidir.
 */
internal fun CryptoManager.resolveArgon2Params(context: Context): Triple<Int, Int, Int> {
    val capabilities = deviceMemoryCapabilities(context)
    val std64Ms = measureStandardArgon2Ms(context)
    val (memoryKb, iterations, clamped) = calculateTieredArgon2Params(
        targetMs = context.config.unlockTargetMs,
        std64Ms = std64Ms,
        capabilities = capabilities,
        measure128Ms = { measureHighMemoryArgon2Ms(context) },
        measure256Ms = { measureUltraMemoryArgon2Ms(context) }
    )
    context.config.argon2CalibrationClamped = clamped
    context.config.lastCalibratedMemoryKb = memoryKb
    context.config.lastCalibratedIterations = iterations
    return Triple(memoryKb, iterations, ARGON2_PARALLELISM)
}
