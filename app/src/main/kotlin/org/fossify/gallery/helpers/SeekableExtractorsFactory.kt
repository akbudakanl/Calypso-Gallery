package org.fossify.gallery.helpers

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * Cache key identifying a local video file's exact size and modification timestamp so
 * cached [ChunkIndex] seek tables are invalidated automatically if the file changes.
 */
data class SeekIndexCacheKey(val path: String, val size: Long, val lastModified: Long) {
    companion object {
        fun fromPath(path: String): SeekIndexCacheKey? {
            if (path.isEmpty() || path.startsWith("content://") || path.startsWith("android.resource://")) {
                return null
            }
            val file = File(path)
            if (!file.isFile) return null
            val size = file.length()
            if (size <= 0L) return null
            return SeekIndexCacheKey(path = path, size = size, lastModified = file.lastModified())
        }
    }
}

@UnstableApi
internal sealed interface CachedSeekResult {
    class Hit(val chunkIndex: ChunkIndex) : CachedSeekResult
    data object NoFallbackNeeded : CachedSeekResult
}

/**
 * [ExtractorsFactory] that improves duration detection and seekability for local and
 * encrypted videos whose containers lack a seek table or duration header:
 * - Enables constant-bitrate seeking and fragmented `sidx` merging on [DefaultExtractorsFactory].
 * - Wraps [FragmentedMp4Extractor] to build a [ChunkIndex] from `moof` boxes (`tfdt`/`trun`)
 *   when no `sidx` index is present.
 * - Wraps [MatroskaExtractor] to build a [ChunkIndex] from `Cluster` timecodes when `Cues`
 *   or `Duration` metadata is missing.
 */
@UnstableApi
class SeekableExtractorsFactory(
    private val cacheKey: SeekIndexCacheKey? = null,
) : ExtractorsFactory {
    private val baseFactory = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setFragmentedMp4ExtractorFlags(FragmentedMp4Extractor.FLAG_MERGE_FRAGMENTED_SIDX)

    override fun createExtractors(): Array<Extractor> = wrap(baseFactory.createExtractors())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: MutableMap<String, MutableList<String>>,
    ): Array<Extractor> = wrap(baseFactory.createExtractors(uri, responseHeaders))

    private fun wrap(extractors: Array<Extractor>): Array<Extractor> {
        return Array(extractors.size) { index ->
            when (val extractor = extractors[index]) {
                is FragmentedMp4Extractor -> SeekableFragmentedMp4Extractor(extractor, cacheKey)
                is MatroskaExtractor -> SeekableMatroskaExtractor(extractor, cacheKey)
                else -> extractor
            }
        }
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 128
        private val lock = Any()
        private val cache = object : LinkedHashMap<SeekIndexCacheKey, CachedSeekResult>(
            MAX_CACHE_ENTRIES,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<SeekIndexCacheKey, CachedSeekResult>?,
            ): Boolean = size > MAX_CACHE_ENTRIES
        }

        fun clearCache() {
            synchronized(lock) {
                cache.clear()
            }
        }

        fun clearEncryptedCache() {
            synchronized(lock) {
                val iter = cache.entries.iterator()
                while (iter.hasNext()) {
                    if (CryptoManager.isEncryptedFile(iter.next().key.path)) {
                        iter.remove()
                    }
                }
            }
        }

        internal fun cacheSizeForTesting(): Int = synchronized(lock) {
            cache.size
        }

        internal fun getCachedResult(key: SeekIndexCacheKey?): CachedSeekResult? {
            if (key == null) return null
            return synchronized(lock) {
                cache[key]
            }
        }

        internal fun putCachedResult(key: SeekIndexCacheKey?, result: CachedSeekResult) {
            if (key == null) return
            synchronized(lock) {
                cache[key] = result
            }
        }
    }
}

@UnstableApi
internal class SeekMapInterceptingOutput(
    private val delegate: ExtractorOutput,
    private val fallbackSeekMapProvider: () -> SeekMap?,
) : ExtractorOutput {
    override fun track(id: Int, type: Int): TrackOutput = delegate.track(id, type)

    override fun endTracks() = delegate.endTracks()

    override fun seekMap(seekMap: SeekMap) {
        val fallback = fallbackSeekMapProvider()
        val shouldReplace = fallback != null && (
            !seekMap.isSeekable ||
                (seekMap.durationUs == C.TIME_UNSET && fallback.durationUs != C.TIME_UNSET)
            )
        delegate.seekMap(if (shouldReplace) fallback else seekMap)
    }
}

internal const val MAX_INLINE_SKIP_BYTES = 64 * 1024L

internal fun readUint24(buf: ByteArray, offset: Int): Int {
    return ((buf[offset].toInt() and 0xFF) shl 16) or
        ((buf[offset + 1].toInt() and 0xFF) shl 8) or
        (buf[offset + 2].toInt() and 0xFF)
}

internal fun readInt32(buf: ByteArray, offset: Int): Int {
    return ((buf[offset].toInt() and 0xFF) shl 24) or
        ((buf[offset + 1].toInt() and 0xFF) shl 16) or
        ((buf[offset + 2].toInt() and 0xFF) shl 8) or
        (buf[offset + 3].toInt() and 0xFF)
}

internal fun readUint32(buf: ByteArray, offset: Int): Long {
    return readInt32(buf, offset).toLong() and 0xFFFFFFFFL
}

internal fun readInt64(buf: ByteArray, offset: Int): Long {
    var result = 0L
    for (i in 0 until 8) {
        result = (result shl 8) or (buf[offset + i].toInt() and 0xFF).toLong()
    }
    return result
}

/**
 * Lightweight fallback duration probe for local video files when Android MediaStore /
 * MediaMetadataRetriever return `0` (e.g., fragmented MP4 without `sidx`/`mehd` or
 * WebM/MKV without `Duration`/`Cues`). Returns `0L` if duration cannot be determined.
 */
@androidx.annotation.OptIn(UnstableApi::class)
fun probeContainerDurationMs(file: File): Long {
    if (!file.isFile || file.length() < 16L) return 0L
    return runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(12)
            raf.readFully(magic)
            raf.seek(0L)

            val isMp4 = magic[4] == 'f'.code.toByte() &&
                magic[5] == 't'.code.toByte() &&
                magic[6] == 'y'.code.toByte() &&
                magic[7] == 'p'.code.toByte()
            val isEbml = (magic[0].toInt() and 0xFF) == 0x1A &&
                (magic[1].toInt() and 0xFF) == 0x45 &&
                (magic[2].toInt() and 0xFF) == 0xDF &&
                (magic[3].toInt() and 0xFF) == 0xA3

            if (!isMp4 && !isEbml) return@use 0L

            val stopDelegate = StopDelegateExtractor()
            val wrapper: Extractor = if (isMp4) {
                SeekableFragmentedMp4Extractor(stopDelegate)
            } else {
                SeekableMatroskaExtractor(stopDelegate)
            }
            val input = RandomAccessFileExtractorInput(raf)
            val holder = PositionHolder()
            var steps = 0
            while (steps++ < 100_000) {
                val res = wrapper.read(input, holder)
                when (res) {
                    Extractor.RESULT_SEEK -> input.seekTo(holder.position)
                    Extractor.RESULT_END_OF_INPUT -> break
                }
            }
            val seekMap = when (wrapper) {
                is SeekableFragmentedMp4Extractor -> wrapper.buildFallbackSeekMapForTesting()
                is SeekableMatroskaExtractor -> wrapper.buildFallbackSeekMapForTesting()
                else -> null
            }
            val durationUs = seekMap?.durationUs ?: C.TIME_UNSET
            if (durationUs != C.TIME_UNSET && durationUs > 0L) durationUs / 1000L else 0L
        }
    }.getOrDefault(0L)
}

fun probeContainerDurationSeconds(file: File): Int {
    val ms = probeContainerDurationMs(file)
    return if (ms > 0L) (ms / 1000.0).roundToInt().coerceAtLeast(1) else 0
}

@UnstableApi
private class StopDelegateExtractor : Extractor {
    override fun sniff(input: ExtractorInput): Boolean = true
    override fun init(output: ExtractorOutput) {}
    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = Extractor.RESULT_END_OF_INPUT
    override fun seek(position: Long, timeUs: Long) {}
    override fun release() {}
}

@UnstableApi
internal class RandomAccessFileExtractorInput(
    private val raf: RandomAccessFile,
) : ExtractorInput {
    private val fileLength = raf.length()
    private var pos = 0L

    fun seekTo(position: Long) {
        pos = position.coerceIn(0L, fileLength)
        raf.seek(pos)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = raf.read(buffer, offset, length)
        if (read == -1) return C.RESULT_END_OF_INPUT
        pos += read
        return read
    }

    override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
        var total = 0
        while (total < length) {
            val read = raf.read(target, offset + total, length - total)
            if (read == -1) {
                if (total == 0 && allowEndOfInput) return false
                throw EOFException()
            }
            total += read
            pos += read
        }
        return true
    }

    override fun readFully(target: ByteArray, offset: Int, length: Int) {
        readFully(target, offset, length, allowEndOfInput = false)
    }

    override fun skip(length: Int): Int {
        val skipped = minOf(length.toLong(), fileLength - pos).toInt()
        if (skipped <= 0) return C.RESULT_END_OF_INPUT
        seekTo(pos + skipped)
        return skipped
    }

    override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
        if (pos + length > fileLength) {
            if (allowEndOfInput) return false
            throw EOFException()
        }
        seekTo(pos + length)
        return true
    }

    override fun skipFully(length: Int) {
        skipFully(length, allowEndOfInput = false)
    }

    override fun peek(target: ByteArray, offset: Int, length: Int): Int = throw UnsupportedOperationException()
    override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean =
        throw UnsupportedOperationException()
    override fun peekFully(target: ByteArray, offset: Int, length: Int) = throw UnsupportedOperationException()
    override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean =
        throw UnsupportedOperationException()
    override fun advancePeekPosition(length: Int) = throw UnsupportedOperationException()
    override fun resetPeekPosition() {}
    override fun getPeekPosition(): Long = pos
    override fun getPosition(): Long = pos
    override fun getLength(): Long = fileLength
    override fun <E : Throwable> setRetryPosition(position: Long, e: E): Unit = throw e
}
