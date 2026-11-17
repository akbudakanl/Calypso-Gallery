package org.fossify.gallery.helpers.operations

import android.os.StatFs
import org.fossify.commons.extensions.getAvailableStorageB
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import java.io.File

enum class SpaceConfidence { EXACT, ESTIMATED, UNKNOWN }

data class SpaceEstimate(
    val requiredBytes: Long,
    val peakBytes: Long,
    val confidence: SpaceConfidence = SpaceConfidence.EXACT
)

data class EncryptedSizeInput(
    val originalSize: Long,
    val originalName: String,
    val thumbnailSize: Int = 0,
    val segmentSize: Int = TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE
)

data class SpaceProbe(
    val availableBytes: Long,
    val verifiable: Boolean,
    val reason: String = ""
)

/**
 * ADR 0003 §3a: one place for the storage estimate of every operation type.
 *
 * `requiredBytes` is the space a single item needs; `peakBytes` is the maximum simultaneously
 * occupied space of the whole batch. Album encrypt/decrypt write every output before wiping any
 * source, so their peak is the sum of all outputs.
 */
object SpaceEstimator {

    const val MIN_MARGIN_BYTES = 64L * 1024L * 1024L

    fun forPlain(sizes: Collection<Long>): SpaceEstimate {
        val total = sizes.sumOf { it.coerceAtLeast(0L) }
        return SpaceEstimate(total, total, SpaceConfidence.EXACT)
    }

    fun forEncrypted(items: Collection<EncryptedSizeInput>): SpaceEstimate {
        val sizes = items.map { VaultContainerWriter.estimateEncryptedSize(it) }
        val total = sizes.sumOf { it }
        return SpaceEstimate(total, total, SpaceConfidence.EXACT)
    }

    /** Decrypt output size is exact: it is stored in the container header. */
    fun forDecrypt(originalSizes: Collection<Long>): SpaceEstimate = forPlain(originalSizes)

    /** `.bin`-to-`.bin` copies move the container verbatim, so the size is exact. */
    fun forEncryptedCopy(sizes: Collection<Long>): SpaceEstimate = forPlain(sizes)

    fun requiredWithMargin(requiredBytes: Long): Long =
        requiredBytes + maxOf(MIN_MARGIN_BYTES, requiredBytes / 100L)

    fun hasEnough(availableBytes: Long, requiredBytes: Long): Boolean =
        availableBytes > 0L && availableBytes >= requiredWithMargin(requiredBytes)

    /**
     * Resolve free space on the destination volume. SAF/OTG paths often return -1 from the
     * commons helper; in that case StatFs is tried and the caller is told that the value is not
     * reliable (ADR §3a) instead of silently skipping the check.
     */
    fun probe(path: String): SpaceProbe {
        val reported = runCatching { path.getAvailableStorageB() }.getOrDefault(-1L)
        if (reported > 0L) return SpaceProbe(reported, true)
        val statFs = runCatching {
            val file = File(path)
            val target = if (file.exists()) file else file.parentFile
            if (target != null && target.exists()) StatFs(target.absolutePath).availableBytes else -1L
        }.getOrDefault(-1L)
        return if (statFs > 0L) {
            SpaceProbe(statFs, false, "SAF/OTG")
        } else {
            SpaceProbe(-1L, false, "unknown")
        }
    }
}
