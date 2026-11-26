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
import androidx.media3.extractor.mp4.FragmentedMp4Extractor

/**
 * Wraps [FragmentedMp4Extractor] so fragmented MP4 files without `sidx` boxes (and often
 * without `mehd` / `mvhd` duration) still produce a valid [ChunkIndex] with exact duration
 * and fragment-level seeking.
 */
@UnstableApi
internal class SeekableFragmentedMp4Extractor(
    private val delegate: Extractor,
    private val cacheKey: SeekIndexCacheKey? = null,
) : Extractor {

    private enum class Phase { SCAN, RETURN_TO_START, DELEGATE }

    private data class TrackInfo(
        val trackId: Int,
        val timescale: Long,
        val handlerType: Int,
        var defaultSampleDuration: Long = 0L,
    )

    private data class FragmentEntry(
        val offset: Long,
        val timeUs: Long,
        val durationUs: Long,
    )

    private var phase = Phase.SCAN
    private var scanCompleted = false
    private var scanPos = 0L
    private var primaryTrack: TrackInfo? = null
    private var runningDecodeTimeTicks = 0L
    private val fragments = ArrayList<FragmentEntry>()
    private var fallbackSeekMap: SeekMap? = null

    private val headerBuf = ByteArray(16)

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

        if (!scanCompleted && scanPos == 0L && applyCachedResultIfPresent()) {
            return delegate.read(input, seekPosition)
        }

        val inputLength = input.length
        if (inputLength == C.LENGTH_UNSET.toLong() || inputLength < 8L) {
            scanCompleted = true
            phase = Phase.DELEGATE
            return delegate.read(input, seekPosition)
        }

        if (input.position != scanPos) {
            seekPosition.position = scanPos
            return Extractor.RESULT_SEEK
        }

        return try {
            scanStep(input, inputLength, seekPosition)
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
            // ProgressiveMediaPeriod$ExtractingLoadable starts with pendingExtractorSeek=true
            // and calls seek(0, 0) before the very first read(). Keep Phase.SCAN active (and
            // reset any partial scan state from an aborted load) so the fallback index is built.
            phase = Phase.SCAN
            scanPos = 0L
            primaryTrack = null
            runningDecodeTimeTicks = 0L
            fragments.clear()
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

    private fun scanStep(
        input: ExtractorInput,
        inputLength: Long,
        seekPosition: PositionHolder,
    ): Int {
        while (scanPos + 8L <= inputLength) {
            val boxStart = scanPos
            if (!input.readFully(headerBuf, 0, 8, /* allowEndOfInput= */ true)) {
                break
            }
            val size32 = readUint32(headerBuf, 0)
            val boxType = readInt32(headerBuf, 4)

            val headerSize: Long
            val boxEnd: Long
            when (size32) {
                0L -> {
                    headerSize = 8L
                    boxEnd = inputLength
                }
                1L -> {
                    if (boxStart + 16L > inputLength) {
                        return finishScan(inputLength, input, seekPosition)
                    }
                    input.readFully(headerBuf, 8, 8)
                    val size64 = readInt64(headerBuf, 8)
                    if (size64 < 16L) {
                        return abortToDelegate(input, seekPosition)
                    }
                    headerSize = 16L
                    boxEnd = boxStart + size64
                }
                else -> {
                    if (size32 < 8L) {
                        return abortToDelegate(input, seekPosition)
                    }
                    headerSize = 8L
                    boxEnd = boxStart + size32
                }
            }

            if (boxEnd <= boxStart || boxEnd > inputLength) {
                return abortToDelegate(input, seekPosition)
            }

            val payloadSize = boxEnd - (boxStart + headerSize)

            when (boxType) {
                TYPE_SIDX -> {
                    if (sidxHasReferences(input, payloadSize)) {
                        // Native FragmentedMp4Extractor (with FLAG_MERGE_FRAGMENTED_SIDX) handles sidx.
                        fallbackSeekMap = null
                        SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.NoFallbackNeeded)
                        return abortToDelegate(input, seekPosition)
                    }
                }
                TYPE_MOOV -> {
                    if (payloadSize in 1..MAX_MOOV_BYTES) {
                        val payload = ByteArray(payloadSize.toInt())
                        input.readFully(payload, 0, payload.size)
                        parseMoov(payload)
                    }
                }
                TYPE_MOOF -> {
                    if (primaryTrack != null && payloadSize in 1..MAX_MOOF_BYTES && fragments.size < MAX_FRAGMENTS) {
                        val payload = ByteArray(payloadSize.toInt())
                        input.readFully(payload, 0, payload.size)
                        parseMoof(boxStart, payload)
                    }
                }
            }

            scanPos = boxEnd
            val remaining = boxEnd - input.position
            if (remaining > 0L) {
                if (remaining <= MAX_INLINE_SKIP_BYTES) {
                    input.skipFully(remaining.toInt())
                } else {
                    seekPosition.position = boxEnd
                    return Extractor.RESULT_SEEK
                }
            }
        }

        return finishScan(inputLength, input, seekPosition)
    }

    private fun sidxHasReferences(input: ExtractorInput, payloadSize: Long): Boolean {
        if (payloadSize < 24L) return false
        val toRead = minOf(payloadSize, 32L).toInt()
        val buf = ByteArray(toRead)
        input.readFully(buf, 0, toRead)
        val version = buf[0].toInt() and 0xFF
        val refCountOffset = if (version == 0) 22 else 30
        if (refCountOffset + 2 > toRead) return false
        val refCount = ((buf[refCountOffset].toInt() and 0xFF) shl 8) or (buf[refCountOffset + 1].toInt() and 0xFF)
        return refCount > 0
    }

    private fun parseMoov(data: ByteArray) {
        val tracks = ArrayList<TrackInfo>()
        val trexDefaults = HashMap<Int, Long>()

        forEachChildBox(data, 0, data.size) { type, start, end ->
            when (type) {
                TYPE_TRAK -> parseTrak(data, start, end)?.let { tracks.add(it) }
                TYPE_MVEX -> parseMvex(data, start, end, trexDefaults)
            }
        }

        for (track in tracks) {
            trexDefaults[track.trackId]?.let { track.defaultSampleDuration = it }
        }

        primaryTrack = tracks.firstOrNull { it.handlerType == HANDLER_VIDE && it.timescale > 0L }
            ?: tracks.firstOrNull { it.handlerType == HANDLER_SOUN && it.timescale > 0L }
            ?: tracks.firstOrNull { it.timescale > 0L }
    }

    private fun parseTrak(data: ByteArray, start: Int, end: Int): TrackInfo? {
        var trackId = -1
        var timescale = 0L
        var handlerType = 0

        forEachChildBox(data, start, end) { type, childStart, childEnd ->
            when (type) {
                TYPE_TKHD -> {
                    if (childEnd - childStart >= 16) {
                        val version = data[childStart].toInt() and 0xFF
                        val idOffset = childStart + if (version == 0) 12 else 20
                        if (idOffset + 4 <= childEnd) {
                            trackId = readInt32(data, idOffset)
                        }
                    }
                }
                TYPE_MDIA -> {
                    forEachChildBox(data, childStart, childEnd) { mdiaType, mStart, mEnd ->
                        when (mdiaType) {
                            TYPE_MDHD -> {
                                if (mEnd - mStart >= 16) {
                                    val version = data[mStart].toInt() and 0xFF
                                    val tsOffset = mStart + if (version == 0) 12 else 20
                                    if (tsOffset + 4 <= mEnd) {
                                        timescale = readUint32(data, tsOffset)
                                    }
                                }
                            }
                            TYPE_HDLR -> {
                                if (mStart + 12 <= mEnd) {
                                    handlerType = readInt32(data, mStart + 8)
                                }
                            }
                        }
                    }
                }
            }
        }

        return if (trackId >= 0 && timescale > 0L) {
            TrackInfo(trackId = trackId, timescale = timescale, handlerType = handlerType)
        } else {
            null
        }
    }

    private fun parseMvex(data: ByteArray, start: Int, end: Int, outDefaults: MutableMap<Int, Long>) {
        forEachChildBox(data, start, end) { type, childStart, childEnd ->
            if (type == TYPE_TREX && childStart + 16 <= childEnd) {
                val trackId = readInt32(data, childStart + 4)
                val defaultDuration = readUint32(data, childStart + 12)
                outDefaults[trackId] = defaultDuration
            }
        }
    }

    private fun parseMoof(moofOffset: Long, data: ByteArray) {
        val track = primaryTrack ?: return
        var matchedTraf = false
        var tfdtTicks: Long? = null
        var trafDurationTicks = 0L

        forEachChildBox(data, 0, data.size) { type, trafStart, trafEnd ->
            if (type != TYPE_TRAF) return@forEachChildBox

            var trafTrackId = -1
            var tfhdDefaultDuration: Long? = null
            var localTfdt: Long? = null
            var localDurationTicks = 0L
            var hasTrun = false

            // First pass: find tfhd (track_id and default_sample_duration)
            forEachChildBox(data, trafStart, trafEnd) { childType, cStart, cEnd ->
                if (childType == TYPE_TFHD && cStart + 8 <= cEnd) {
                    val tfFlags = readUint24(data, cStart + 1)
                    trafTrackId = readInt32(data, cStart + 4)
                    var cursor = cStart + 8
                    if ((tfFlags and 0x000001) != 0) cursor += 8 // base-data-offset
                    if ((tfFlags and 0x000002) != 0) cursor += 4 // sample-description-index
                    if ((tfFlags and 0x000008) != 0 && cursor + 4 <= cEnd) {
                        tfhdDefaultDuration = readUint32(data, cursor)
                    }
                }
            }

            if (trafTrackId != track.trackId) return@forEachChildBox
            matchedTraf = true
            val effectiveDefaultDuration = tfhdDefaultDuration ?: track.defaultSampleDuration

            // Second pass: read tfdt and trun
            forEachChildBox(data, trafStart, trafEnd) { childType, cStart, cEnd ->
                when (childType) {
                    TYPE_TFDT -> {
                        if (cStart + 8 <= cEnd) {
                            val version = data[cStart].toInt() and 0xFF
                            localTfdt = if (version == 0) {
                                readUint32(data, cStart + 4)
                            } else if (cStart + 12 <= cEnd) {
                                readInt64(data, cStart + 4).coerceAtLeast(0L)
                            } else {
                                null
                            }
                        }
                    }
                    TYPE_TRUN -> {
                        if (cStart + 8 <= cEnd) {
                            hasTrun = true
                            val trunFlags = readUint24(data, cStart + 1)
                            val sampleCount = readUint32(data, cStart + 4)
                            var cursor = cStart + 8
                            if ((trunFlags and 0x000001) != 0) cursor += 4 // data-offset
                            if ((trunFlags and 0x000004) != 0) cursor += 4 // first-sample-flags
                            val hasDuration = (trunFlags and 0x000100) != 0
                            if (hasDuration) {
                                var entrySize = 4
                                if ((trunFlags and 0x000200) != 0) entrySize += 4
                                if ((trunFlags and 0x000400) != 0) entrySize += 4
                                if ((trunFlags and 0x000800) != 0) entrySize += 4
                                var i = 0L
                                while (i < sampleCount && cursor + 4 <= cEnd) {
                                    localDurationTicks += readUint32(data, cursor)
                                    cursor += entrySize
                                    i++
                                }
                            } else {
                                localDurationTicks += sampleCount * effectiveDefaultDuration
                            }
                        }
                    }
                }
            }

            if (localTfdt != null && tfdtTicks == null) {
                tfdtTicks = localTfdt
            }
            if (hasTrun) {
                trafDurationTicks += localDurationTicks
            }
        }

        if (!matchedTraf) return

        val startTicks = tfdtTicks ?: runningDecodeTimeTicks
        runningDecodeTimeTicks = startTicks + trafDurationTicks
        val timeUs = Util.scaleLargeTimestamp(startTicks, C.MICROS_PER_SECOND, track.timescale)
        val durationUs = Util.scaleLargeTimestamp(trafDurationTicks, C.MICROS_PER_SECOND, track.timescale)
        fragments.add(FragmentEntry(offset = moofOffset, timeUs = timeUs, durationUs = durationUs))
    }

    private fun finishScan(
        inputLength: Long,
        input: ExtractorInput,
        seekPosition: PositionHolder,
    ): Int {
        val chunkIndex = buildChunkIndex(fragments, inputLength)
        fallbackSeekMap = chunkIndex
        if (chunkIndex != null) {
            SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.Hit(chunkIndex))
        } else {
            SeekableExtractorsFactory.putCachedResult(cacheKey, CachedSeekResult.NoFallbackNeeded)
        }
        return abortToDelegate(input, seekPosition)
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

    private fun buildChunkIndex(entries: List<FragmentEntry>, inputLength: Long): ChunkIndex? {
        if (entries.isEmpty()) return null
        val n = entries.size
        val sizes = IntArray(n)
        val offsets = LongArray(n)
        val durationsUs = LongArray(n)
        val timesUs = LongArray(n)

        var lastTimeUs = 0L
        for (i in 0 until n) {
            val entry = entries[i]
            offsets[i] = entry.offset
            val t = if (i == 0) entry.timeUs.coerceAtLeast(0L) else maxOf(entry.timeUs, lastTimeUs)
            timesUs[i] = t
            lastTimeUs = t
        }

        for (i in 0 until n) {
            val nextOffset = if (i + 1 < n) offsets[i + 1] else inputLength
            sizes[i] = (nextOffset - offsets[i]).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            val d = if (i + 1 < n && timesUs[i + 1] > timesUs[i]) {
                timesUs[i + 1] - timesUs[i]
            } else {
                entries[i].durationUs
            }
            durationsUs[i] = d.coerceAtLeast(1L)
        }

        val totalDurationUs = timesUs[n - 1] + durationsUs[n - 1]
        if (totalDurationUs <= 0L) return null
        return ChunkIndex(sizes, offsets, durationsUs, timesUs)
    }

    private inline fun forEachChildBox(
        data: ByteArray,
        start: Int,
        end: Int,
        action: (type: Int, payloadStart: Int, boxEnd: Int) -> Unit,
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val size32 = readUint32(data, pos)
            val type = readInt32(data, pos + 4)
            val headerSize: Int
            val boxEndLong: Long
            when (size32) {
                0L -> {
                    headerSize = 8
                    boxEndLong = end.toLong()
                }
                1L -> {
                    if (pos + 16 > end) return
                    val size64 = readInt64(data, pos + 8)
                    if (size64 < 16L) return
                    headerSize = 16
                    boxEndLong = pos.toLong() + size64
                }
                else -> {
                    if (size32 < 8L) return
                    headerSize = 8
                    boxEndLong = pos.toLong() + size32
                }
            }
            if (boxEndLong <= pos.toLong() || boxEndLong > end.toLong()) return
            val boxEnd = boxEndLong.toInt()
            action(type, pos + headerSize, boxEnd)
            pos = boxEnd
        }
    }

    companion object {
        private const val TYPE_MOOV = 0x6D6F6F76
        private const val TYPE_MOOF = 0x6D6F6F66
        private const val TYPE_SIDX = 0x73696478
        private const val TYPE_TRAK = 0x7472616B
        private const val TYPE_TKHD = 0x746B6864
        private const val TYPE_MDIA = 0x6D646961
        private const val TYPE_MDHD = 0x6D646864
        private const val TYPE_HDLR = 0x68646C72
        private const val TYPE_MVEX = 0x6D766578
        private const val TYPE_TREX = 0x74726578
        private const val TYPE_TRAF = 0x74726166
        private const val TYPE_TFHD = 0x74666864
        private const val TYPE_TFDT = 0x74666474
        private const val TYPE_TRUN = 0x7472756E
        private const val HANDLER_VIDE = 0x76696465
        private const val HANDLER_SOUN = 0x736F756E

        private const val MAX_MOOV_BYTES = 4 * 1024 * 1024L
        private const val MAX_MOOF_BYTES = 2 * 1024 * 1024L
        private const val MAX_FRAGMENTS = 50_000
    }
}
