package org.fossify.gallery.helpers

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.mkv.MatroskaExtractor
import java.io.EOFException

/**
 * Wraps [MatroskaExtractor] so WebM/MKV files missing `Cues` or `Duration` metadata
 * still produce a valid [ChunkIndex] with accurate duration and cluster-level seeking.
 */
@UnstableApi
internal class SeekableMatroskaExtractor(
    private val delegate: Extractor,
    private val cacheKey: SeekIndexCacheKey? = null,
) : Extractor {

    private enum class Phase { FIND_SEGMENT, SCAN_LEVEL1, SCAN_LAST_CLUSTER, RETURN_TO_START, DELEGATE }

    private data class ClusterEntry(
        val offset: Long,
        val dataStart: Long,
        val end: Long,
        val timeUs: Long,
    )

    private var phase = Phase.FIND_SEGMENT
    private var scanCompleted = false
    private var scanPos = 0L
    private var segmentEnd = 0L
    private var timecodeScaleNs = 1_000_000L
    private var hasCues = false
    private var hasDuration = false
    private var headerDurationUs = C.TIME_UNSET
    private val clusters = ArrayList<ClusterEntry>()
    private var fallbackSeekMap: SeekMap? = null

    private val scratch = ByteArray(64)

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun init(output: ExtractorOutput) {
        delegate.init(SeekMapInterceptingOutput(output) { fallbackSeekMap })
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (phase == Phase.DELEGATE) {
            return delegate.read(input, seekPosition)
        }

        if (phase == Phase.RETURN_TO_START) {
            phase = Phase.DELEGATE
            if (input.position != 0L) {
                seekPosition.position = 0L
                return Extractor.RESULT_SEEK
            }
            return delegate.read(input, seekPosition)
        }

        if (!scanCompleted && phase == Phase.FIND_SEGMENT && scanPos == 0L && applyCachedResultIfPresent()) {
            return delegate.read(input, seekPosition)
        }

        val inputLength = input.length
        if (inputLength == C.LENGTH_UNSET.toLong() || inputLength < 16L) {
            scanCompleted = true
            phase = Phase.DELEGATE
            return delegate.read(input, seekPosition)
        }

        if (input.position != scanPos) {
            seekPosition.position = scanPos
            return Extractor.RESULT_SEEK
        }

        return try {
            when (phase) {
                Phase.FIND_SEGMENT -> findSegment(input, inputLength, seekPosition)
                Phase.SCAN_LEVEL1 -> scanLevel1(input, seekPosition)
                Phase.SCAN_LAST_CLUSTER -> scanLastClusterAndFinish(input, seekPosition)
                else -> abortToDelegate(input, seekPosition)
            }
        } catch (_: Exception) {
            abortToDelegate(input, seekPosition)
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        if (!scanCompleted && position == 0L) {
            if (applyCachedResultIfPresent()) {
                delegate.seek(position, timeUs)
                return
            }
            phase = Phase.FIND_SEGMENT
            scanPos = 0L
            segmentEnd = 0L
            timecodeScaleNs = 1_000_000L
            hasCues = false
            hasDuration = false
            headerDurationUs = C.TIME_UNSET
            clusters.clear()
            fallbackSeekMap = null
            delegate.seek(position, timeUs)
            return
        }
        scanCompleted = true
        phase = Phase.DELEGATE
        delegate.seek(position, timeUs)
    }

    private fun applyCachedResultIfPresent(): Boolean {
        return when (val cached = SeekableExtractorsFactory.getCachedResult(cacheKey)) {
            is CachedSeekResult.Hit -> {
                fallbackSeekMap = cached.chunkIndex
                scanCompleted = true
                phase = Phase.DELEGATE
                true
            }
            is CachedSeekResult.NoFallbackNeeded -> {
                fallbackSeekMap = null
                scanCompleted = true
                phase = Phase.DELEGATE
                true
            }
            null -> false
        }
    }

    override fun release() {
        delegate.release()
    }

    internal fun buildFallbackSeekMapForTesting(): SeekMap? = fallbackSeekMap

    private fun findSegment(
        input: ExtractorInput,
        inputLength: Long,
        seekPosition: PositionHolder,
    ): Int {
        while (scanPos < inputLength) {
            val id = readEbmlId(input) ?: return abortToDelegate(input, seekPosition)
            val size = readEbmlSize(input) ?: return abortToDelegate(input, seekPosition)
            val dataStart = input.position

            if (id == ID_SEGMENT) {
                segmentEnd = if (size == UNKNOWN_SIZE) inputLength else minOf(dataStart + size, inputLength)
                scanPos = dataStart
                phase = Phase.SCAN_LEVEL1
                return scanLevel1(input, seekPosition)
            }

            if (size == UNKNOWN_SIZE) {
                return abortToDelegate(input, seekPosition)
            }
            val nextPos = dataStart + size
            if (nextPos <= scanPos || nextPos > inputLength) {
                return abortToDelegate(input, seekPosition)
            }
            scanPos = nextPos
            val remaining = nextPos - input.position
            if (remaining > 0L) {
                if (remaining <= MAX_INLINE_SKIP_BYTES) {
                    input.skipFully(remaining.toInt())
                } else {
                    seekPosition.position = nextPos
                    return Extractor.RESULT_SEEK
                }
            }
        }
        return abortToDelegate(input, seekPosition)
    }

    private fun scanLevel1(input: ExtractorInput, seekPosition: PositionHolder): Int {
        while (scanPos < segmentEnd) {
            val elemStart = scanPos
            val id = readEbmlId(input) ?: break
            val size = readEbmlSize(input) ?: break
            val dataStart = input.position

            if (id == ID_CLUSTER && hasCues && hasDuration) {
                // Standard Matroska file with both Cues and Duration: let MatroskaExtractor handle natively.
                fallbackSeekMap = null
                SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.NoFallbackNeeded)
                return abortToDelegate(input, seekPosition)
            }

            if (size == UNKNOWN_SIZE) {
                // Unknown-size Cluster/element cannot be skipped in O(1); stop scanning here.
                break
            }

            val elemEnd = minOf(dataStart + size, segmentEnd)
            if (elemEnd <= elemStart) {
                break
            }

            when (id) {
                ID_SEEK_HEAD -> {
                    if (size <= MAX_METADATA_BYTES) {
                        val buf = ByteArray(size.toInt())
                        input.readFully(buf, 0, buf.size)
                        if (seekHeadContainsCues(buf)) {
                            hasCues = true
                        }
                    }
                }
                ID_INFO -> {
                    if (size <= MAX_METADATA_BYTES) {
                        val buf = ByteArray(size.toInt())
                        input.readFully(buf, 0, buf.size)
                        parseInfo(buf)
                    }
                }
                ID_CUES -> {
                    hasCues = true
                }
                ID_CLUSTER -> {
                    if (clusters.size < MAX_CLUSTERS) {
                        val peekLen = minOf(size, scratch.size.toLong()).toInt()
                        input.readFully(scratch, 0, peekLen)
                        val clusterTimecode = parseClusterTimecode(scratch, peekLen)
                        if (clusterTimecode != null) {
                            val timeUs = Util.scaleLargeTimestamp(clusterTimecode, timecodeScaleNs, 1000L)
                            clusters.add(
                                ClusterEntry(
                                    offset = elemStart,
                                    dataStart = dataStart,
                                    end = elemEnd,
                                    timeUs = timeUs,
                                )
                            )
                        }
                    }
                }
            }

            scanPos = elemEnd
            val remaining = elemEnd - input.position
            if (remaining > 0L) {
                if (remaining <= MAX_INLINE_SKIP_BYTES) {
                    input.skipFully(remaining.toInt())
                } else {
                    seekPosition.position = elemEnd
                    return Extractor.RESULT_SEEK
                }
            }
        }

        if (clusters.isEmpty() || (hasCues && hasDuration)) {
            fallbackSeekMap = null
            SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.NoFallbackNeeded)
            return abortToDelegate(input, seekPosition)
        }

        val lastCluster = clusters.last()
        val lastClusterSize = lastCluster.end - lastCluster.dataStart
        if (lastClusterSize in 1..MAX_LAST_CLUSTER_BYTES) {
            phase = Phase.SCAN_LAST_CLUSTER
            scanPos = lastCluster.dataStart
            seekPosition.position = lastCluster.dataStart
            return Extractor.RESULT_SEEK
        }

        val chunkIndex = buildChunkIndex(lastBlockMaxRelUs = 0L)
        fallbackSeekMap = chunkIndex
        if (chunkIndex != null) {
            SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.Hit(chunkIndex))
        } else {
            SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.NoFallbackNeeded)
        }
        return abortToDelegate(input, seekPosition)
    }

    private fun scanLastClusterAndFinish(input: ExtractorInput, seekPosition: PositionHolder): Int {
        val lastCluster = clusters.last()
        val size = (lastCluster.end - lastCluster.dataStart).toInt()
        val buf = ByteArray(size)
        input.readFully(buf, 0, size)
        val maxRelTicks = scanClusterMaxRelativeTimecode(buf)
        val maxRelUs = if (maxRelTicks > 0L) {
            Util.scaleLargeTimestamp(maxRelTicks, timecodeScaleNs, 1000L)
        } else {
            0L
        }
        val chunkIndex = buildChunkIndex(maxRelUs)
        fallbackSeekMap = chunkIndex
        if (chunkIndex != null) {
            SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.Hit(chunkIndex))
        } else {
            SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.NoFallbackNeeded)
        }
        return abortToDelegate(input, seekPosition)
    }

    private fun buildChunkIndex(lastBlockMaxRelUs: Long): ChunkIndex? {
        val n = clusters.size
        if (n == 0) return null

        val sizes = IntArray(n)
        val offsets = LongArray(n)
        val durationsUs = LongArray(n)
        val timesUs = LongArray(n)

        var lastTimeUs = 0L
        for (i in 0 until n) {
            offsets[i] = clusters[i].offset
            val t = if (i == 0) clusters[i].timeUs.coerceAtLeast(0L) else maxOf(clusters[i].timeUs, lastTimeUs)
            timesUs[i] = t
            lastTimeUs = t
        }

        var sumGapUs = 0L
        var gapCount = 0
        for (i in 0 until n - 1) {
            val nextOffset = offsets[i + 1]
            sizes[i] = (nextOffset - offsets[i]).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            val gap = (timesUs[i + 1] - timesUs[i]).coerceAtLeast(1L)
            durationsUs[i] = gap
            sumGapUs += gap
            gapCount++
        }

        sizes[n - 1] = (clusters[n - 1].end - offsets[n - 1]).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val lastDurationFromHeader = if (headerDurationUs != C.TIME_UNSET && headerDurationUs > timesUs[n - 1]) {
            headerDurationUs - timesUs[n - 1]
        } else {
            0L
        }
        val avgGapUs = if (gapCount > 0) (sumGapUs / gapCount) else 1_000_000L
        val lastDurationUs = maxOf(lastBlockMaxRelUs, lastDurationFromHeader).let {
            if (it > 0L) it else avgGapUs.coerceAtLeast(1L)
        }
        durationsUs[n - 1] = lastDurationUs

        val totalDurationUs = timesUs[n - 1] + durationsUs[n - 1]
        if (totalDurationUs <= 0L) return null
        return ChunkIndex(sizes, offsets, durationsUs, timesUs)
    }

    private fun abortToDelegate(input: ExtractorInput, seekPosition: PositionHolder): Int {
        scanCompleted = true
        phase = Phase.RETURN_TO_START
        if (input.position != 0L) {
            seekPosition.position = 0L
            return Extractor.RESULT_SEEK
        }
        phase = Phase.DELEGATE
        return delegate.read(input, seekPosition)
    }

    private fun seekHeadContainsCues(buf: ByteArray): Boolean {
        var pos = 0
        while (pos < buf.size) {
            val idRes = readBufEbmlId(buf, pos) ?: break
            pos = idRes.nextPos
            val sizeRes = readBufEbmlSize(buf, pos) ?: break
            pos = sizeRes.nextPos
            val elemEnd = (pos + sizeRes.value.toInt()).coerceAtMost(buf.size)
            if (idRes.value.toInt() == ID_SEEK) {
                var inner = pos
                while (inner < elemEnd) {
                    val innerId = readBufEbmlId(buf, inner) ?: break
                    inner = innerId.nextPos
                    val innerSize = readBufEbmlSize(buf, inner) ?: break
                    inner = innerSize.nextPos
                    val innerEnd = (inner + innerSize.value.toInt()).coerceAtMost(elemEnd)
                    if (innerId.value.toInt() == ID_SEEK_ID && innerEnd - inner == 4) {
                        val targetId = readInt32(buf, inner)
                        if (targetId == ID_CUES) {
                            return true
                        }
                    }
                    inner = innerEnd
                }
            }
            pos = elemEnd
        }
        return false
    }

    private fun parseInfo(buf: ByteArray) {
        var rawDuration: Double? = null
        var pos = 0
        while (pos < buf.size) {
            val idRes = readBufEbmlId(buf, pos) ?: break
            pos = idRes.nextPos
            val sizeRes = readBufEbmlSize(buf, pos) ?: break
            pos = sizeRes.nextPos
            val len = sizeRes.value.toInt()
            if (len < 0 || pos + len > buf.size) break
            when (idRes.value.toInt()) {
                ID_TIMECODE_SCALE -> {
                    val scale = readBufUint(buf, pos, len)
                    if (scale > 0L) {
                        timecodeScaleNs = scale
                    }
                }
                ID_DURATION -> {
                    rawDuration = when (len) {
                        4 -> Float.fromBits(readInt32(buf, pos)).toDouble()
                        8 -> Double.fromBits(readInt64(buf, pos))
                        else -> null
                    }
                }
            }
            pos += len
        }
        if (rawDuration != null && rawDuration > 0.0 && rawDuration.isFinite()) {
            hasDuration = true
            headerDurationUs = (rawDuration * timecodeScaleNs / 1000.0).toLong()
        }
    }

    private fun parseClusterTimecode(buf: ByteArray, limit: Int): Long? {
        var pos = 0
        while (pos < limit) {
            val idRes = readBufEbmlId(buf, pos, limit) ?: return null
            pos = idRes.nextPos
            val sizeRes = readBufEbmlSize(buf, pos, limit) ?: return null
            pos = sizeRes.nextPos
            val len = sizeRes.value.toInt()
            if (idRes.value.toInt() == ID_CLUSTER_TIMECODE) {
                if (len < 0 || pos + len > limit) return null
                return readBufUint(buf, pos, len)
            }
            if (sizeRes.value == UNKNOWN_SIZE || len < 0 || pos + len > limit) return null
            pos += len
        }
        return null
    }

    private fun scanClusterMaxRelativeTimecode(buf: ByteArray): Long {
        var maxRel = 0L
        var pos = 0
        while (pos < buf.size) {
            val idRes = readBufEbmlId(buf, pos) ?: break
            pos = idRes.nextPos
            val sizeRes = readBufEbmlSize(buf, pos) ?: break
            pos = sizeRes.nextPos
            val len = sizeRes.value.toInt()
            if (sizeRes.value == UNKNOWN_SIZE || len < 0 || pos + len > buf.size) break
            val elemEnd = pos + len
            when (idRes.value.toInt()) {
                ID_SIMPLE_BLOCK -> {
                    val rel = parseBlockRelativeTimecode(buf, pos, elemEnd)
                    if (rel > maxRel) maxRel = rel
                }
                ID_BLOCK_GROUP -> {
                    var inner = pos
                    var blockRel = 0L
                    var blockDur = 0L
                    while (inner < elemEnd) {
                        val innerId = readBufEbmlId(buf, inner, elemEnd) ?: break
                        inner = innerId.nextPos
                        val innerSize = readBufEbmlSize(buf, inner, elemEnd) ?: break
                        inner = innerSize.nextPos
                        val innerLen = innerSize.value.toInt()
                        if (innerSize.value == UNKNOWN_SIZE || innerLen < 0 || inner + innerLen > elemEnd) break
                        when (innerId.value.toInt()) {
                            ID_BLOCK -> blockRel = parseBlockRelativeTimecode(buf, inner, inner + innerLen)
                            ID_BLOCK_DURATION -> blockDur = readBufUint(buf, inner, innerLen)
                        }
                        inner += innerLen
                    }
                    val endTicks = blockRel + blockDur
                    if (endTicks > maxRel) maxRel = endTicks
                }
            }
            pos = elemEnd
        }
        return maxRel
    }

    private fun parseBlockRelativeTimecode(buf: ByteArray, start: Int, end: Int): Long {
        // Track number VINT followed by signed 16-bit big-endian relative timecode.
        val trackVint = readBufEbmlSize(buf, start, end) ?: return 0L
        val tcPos = trackVint.nextPos
        if (tcPos + 2 > end) return 0L
        val rel = ((buf[tcPos].toInt() shl 8) or (buf[tcPos + 1].toInt() and 0xFF)).toShort().toLong()
        return rel.coerceAtLeast(0L)
    }

    private fun readEbmlId(input: ExtractorInput): Int? {
        if (!input.readFully(scratch, 0, 1, /* allowEndOfInput= */ true)) return null
        val first = scratch[0].toInt() and 0xFF
        val length = vintLength(first)
        if (length !in 1..4) return null
        if (length > 1) {
            try {
                input.readFully(scratch, 1, length - 1)
            } catch (_: EOFException) {
                return null
            }
        }
        var value = first
        for (i in 1 until length) {
            value = (value shl 8) or (scratch[i].toInt() and 0xFF)
        }
        return value
    }

    private fun readEbmlSize(input: ExtractorInput): Long? {
        if (!input.readFully(scratch, 0, 1, /* allowEndOfInput= */ true)) return null
        val first = scratch[0].toInt() and 0xFF
        val length = vintLength(first)
        if (length !in 1..8) return null
        if (length > 1) {
            try {
                input.readFully(scratch, 1, length - 1)
            } catch (_: EOFException) {
                return null
            }
        }
        val mask = (0xFF ushr length)
        var value = (first and mask).toLong()
        var allOnes = (first and mask) == mask
        for (i in 1 until length) {
            val b = scratch[i].toInt() and 0xFF
            value = (value shl 8) or b.toLong()
            if (b != 0xFF) allOnes = false
        }
        return if (allOnes) UNKNOWN_SIZE else value
    }

    private data class VintResult(val value: Long, val nextPos: Int)

    private fun readBufEbmlId(buf: ByteArray, pos: Int, limit: Int = buf.size): VintResult? {
        if (pos >= limit) return null
        val first = buf[pos].toInt() and 0xFF
        val length = vintLength(first)
        if (length !in 1..4 || pos + length > limit) return null
        var value = first.toLong()
        for (i in 1 until length) {
            value = (value shl 8) or (buf[pos + i].toInt() and 0xFF).toLong()
        }
        return VintResult(value, pos + length)
    }

    private fun readBufEbmlSize(buf: ByteArray, pos: Int, limit: Int = buf.size): VintResult? {
        if (pos >= limit) return null
        val first = buf[pos].toInt() and 0xFF
        val length = vintLength(first)
        if (length !in 1..8 || pos + length > limit) return null
        val mask = (0xFF ushr length)
        var value = (first and mask).toLong()
        var allOnes = (first and mask) == mask
        for (i in 1 until length) {
            val b = buf[pos + i].toInt() and 0xFF
            value = (value shl 8) or b.toLong()
            if (b != 0xFF) allOnes = false
        }
        return VintResult(if (allOnes) UNKNOWN_SIZE else value, pos + length)
    }

    private fun vintLength(firstByte: Int): Int {
        if (firstByte == 0) return -1
        var mask = 0x80
        for (len in 1..8) {
            if ((firstByte and mask) != 0) return len
            mask = mask ushr 1
        }
        return -1
    }

    private fun readBufUint(buf: ByteArray, pos: Int, len: Int): Long {
        if (len <= 0 || len > 8 || pos + len > buf.size) return 0L
        var value = 0L
        for (i in 0 until len) {
            value = (value shl 8) or (buf[pos + i].toInt() and 0xFF).toLong()
        }
        return value
    }

    companion object {
        private const val UNKNOWN_SIZE = -1L

        private const val ID_SEGMENT = 0x18538067
        private const val ID_SEEK_HEAD = 0x114D9B74
        private const val ID_SEEK = 0x4DBB
        private const val ID_SEEK_ID = 0x53AB
        private const val ID_INFO = 0x1549A966
        private const val ID_TIMECODE_SCALE = 0x2AD7B1
        private const val ID_DURATION = 0x4489
        private const val ID_CUES = 0x1C53BB6B
        private const val ID_CLUSTER = 0x1F43B675
        private const val ID_CLUSTER_TIMECODE = 0xE7
        private const val ID_SIMPLE_BLOCK = 0xA3
        private const val ID_BLOCK_GROUP = 0xA0
        private const val ID_BLOCK = 0xA1
        private const val ID_BLOCK_DURATION = 0x9B

        private const val MAX_METADATA_BYTES = 64 * 1024L
        private const val MAX_LAST_CLUSTER_BYTES = 2 * 1024 * 1024L
        private const val MAX_CLUSTERS = 50_000
    }
}
