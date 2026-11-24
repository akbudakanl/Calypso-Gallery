package org.fossify.gallery.helpers

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
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
 * [ExtractorsFactory] that improves duration detection and seekability for local and
 * encrypted videos whose containers lack a seek table or duration header:
 * - Enables constant-bitrate seeking and fragmented `sidx` merging on [DefaultExtractorsFactory].
 * - Wraps [FragmentedMp4Extractor] to build a [ChunkIndex] from `moof` boxes (`tfdt`/`trun`)
 *   when no `sidx` index is present.
 * - Wraps [MatroskaExtractor] to build a [ChunkIndex] from `Cluster` timecodes when `Cues`
 *   or `Duration` metadata is missing.
 */
@UnstableApi
class SeekableExtractorsFactory : ExtractorsFactory {
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
                is FragmentedMp4Extractor -> SeekableFragmentedMp4Extractor(extractor)
                is MatroskaExtractor -> SeekableMatroskaExtractor(extractor)
                else -> extractor
            }
        }
    }
}

@UnstableApi
private class SeekMapInterceptingOutput(
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

private const val MAX_INLINE_SKIP_BYTES = 64 * 1024L

/**
 * Wraps [FragmentedMp4Extractor] so fragmented MP4 files without `sidx` boxes (and often
 * without `mehd` / `mvhd` duration) still produce a valid [ChunkIndex] with exact duration
 * and fragment-level seeking.
 */
@UnstableApi
internal class SeekableFragmentedMp4Extractor(
    private val delegate: Extractor,
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
        fallbackSeekMap = buildChunkIndex(fragments, inputLength)
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

/**
 * Wraps [MatroskaExtractor] so WebM/MKV files missing `Cues` or `Duration` metadata
 * still produce a valid [ChunkIndex] with accurate duration and cluster-level seeking.
 */
@UnstableApi
internal class SeekableMatroskaExtractor(
    private val delegate: Extractor,
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

        fallbackSeekMap = buildChunkIndex(lastBlockMaxRelUs = 0L)
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
        fallbackSeekMap = buildChunkIndex(maxRelUs)
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

private fun readUint24(buf: ByteArray, offset: Int): Int {
    return ((buf[offset].toInt() and 0xFF) shl 16) or
        ((buf[offset + 1].toInt() and 0xFF) shl 8) or
        (buf[offset + 2].toInt() and 0xFF)
}

private fun readInt32(buf: ByteArray, offset: Int): Int {
    return ((buf[offset].toInt() and 0xFF) shl 24) or
        ((buf[offset + 1].toInt() and 0xFF) shl 16) or
        ((buf[offset + 2].toInt() and 0xFF) shl 8) or
        (buf[offset + 3].toInt() and 0xFF)
}

private fun readUint32(buf: ByteArray, offset: Int): Long {
    return readInt32(buf, offset).toLong() and 0xFFFFFFFFL
}

private fun readInt64(buf: ByteArray, offset: Int): Long {
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
