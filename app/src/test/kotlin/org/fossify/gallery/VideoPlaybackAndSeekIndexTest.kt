package org.fossify.gallery

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.helpers.RandomAccessFileExtractorInput
import org.fossify.gallery.helpers.SeekableFragmentedMp4Extractor
import org.fossify.gallery.helpers.SeekableMatroskaExtractor
import org.fossify.gallery.helpers.VideoPlaybackUtils
import org.fossify.gallery.helpers.probeContainerDurationMs
import org.fossify.gallery.helpers.probeContainerDurationSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

@OptIn(UnstableApi::class)
class VideoPlaybackAndSeekIndexTest {

    @Test
    fun getFormattedDurationGuardsTimeUnsetAndNegativeValues() {
        assertEquals("00:00", C.TIME_UNSET.getFormattedDuration())
        assertEquals("00:00", (-5_000L).getFormattedDuration())
        assertEquals("00:00", 0L.getFormattedDuration())
        assertEquals("01:05", 65_000L.getFormattedDuration())
    }

    @Test
    fun playbackUtilsSanitizeAndSkipWithoutResettingToZero() {
        assertEquals(0L, VideoPlaybackUtils.sanitizeDurationMs(C.TIME_UNSET))
        assertEquals(0, VideoPlaybackUtils.toSeekBarMax(C.TIME_UNSET))
        assertEquals(45_000L, VideoPlaybackUtils.effectiveDurationMs(C.TIME_UNSET, 45_000L))
        assertEquals(30_000L, VideoPlaybackUtils.effectiveDurationMs(30_000L, 45_000L))

        // Unknown duration: forward and backward skips must not clamp to 0L
        val fwdUnknown = VideoPlaybackUtils.computeSkipResult(
            currentPosMs = 15_000L,
            skipMs = 10_000L,
            durationMs = C.TIME_UNSET,
            forward = true,
        )
        assertFalse(fwdUnknown.reachedEnd)
        assertEquals(25_000L, fwdUnknown.targetPositionMs)

        val bwdUnknown = VideoPlaybackUtils.computeSkipResult(
            currentPosMs = 15_000L,
            skipMs = 10_000L,
            durationMs = C.TIME_UNSET,
            forward = false,
        )
        assertFalse(bwdUnknown.reachedEnd)
        assertEquals(5_000L, bwdUnknown.targetPositionMs)

        // Known duration: forward past end signals reachedEnd
        val fwdEnd = VideoPlaybackUtils.computeSkipResult(
            currentPosMs = 25_000L,
            skipMs = 10_000L,
            durationMs = 30_000L,
            forward = true,
        )
        assertTrue(fwdEnd.reachedEnd)

        // isVideoEnded must not return true just because duration is C.TIME_UNSET
        assertFalse(
            VideoPlaybackUtils.isVideoEnded(
                skippedToEnd = false,
                playbackState = Player.STATE_READY,
                currentPosMs = 15_000L,
                durationMs = C.TIME_UNSET,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isVideoEnded(
                skippedToEnd = false,
                playbackState = Player.STATE_ENDED,
                currentPosMs = 15_000L,
                durationMs = C.TIME_UNSET,
            )
        )
    }

    @Test
    fun fragmentedMp4WithoutSidxBuildsExactChunkIndexAndReplacesUnseekableMap() {
        val built = buildSyntheticFmp4(
            timescale = 90_000,
            defaultSampleDuration = 3_000, // 30 fps
            fragments = listOf(
                Triple(0L, 90, 256), // 0s..3s, small mdat (inline skip)
                Triple(270_000L, 90, 96 * 1024), // 3s..6s, > 64KB mdat (RESULT_SEEK skip)
                Triple(540_000L, 60, 512), // 6s..8s
            ),
            includeSidx = false,
        )

        val tempFile = File.createTempFile("fmp4_nosidx_", ".mp4")
        try {
            tempFile.writeBytes(built.bytes)

            var emittedSeekMap: SeekMap? = null
            val delegate = object : Extractor {
                private lateinit var out: ExtractorOutput
                override fun sniff(input: ExtractorInput): Boolean = true
                override fun init(output: ExtractorOutput) {
                    out = output
                }
                override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
                    out.seekMap(SeekMap.Unseekable(C.TIME_UNSET))
                    return Extractor.RESULT_END_OF_INPUT
                }
                override fun seek(position: Long, timeUs: Long) {}
                override fun release() {}
            }

            val extractor = SeekableFragmentedMp4Extractor(delegate)
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {
                    emittedSeekMap = seekMap
                }
            })

            // Simulate ProgressiveMediaPeriod$ExtractingLoadable's initial seek(0, 0) before first read().
            extractor.seek(0L, 0L)

            RandomAccessFile(tempFile, "r").use { raf ->
                val input = RandomAccessFileExtractorInput(raf)
                val holder = PositionHolder()
                while (true) {
                    val res = extractor.read(input, holder)
                    if (res == Extractor.RESULT_SEEK) {
                        input.seekTo(holder.position)
                    } else if (res == Extractor.RESULT_END_OF_INPUT) {
                        break
                    }
                }
            }

            val seekMap = emittedSeekMap as? ChunkIndex
            assertNotNull("Expected ChunkIndex to replace SeekMap.Unseekable", seekMap)
            seekMap!!
            assertTrue(seekMap.isSeekable)
            assertEquals(8_000_000L, seekMap.durationUs)
            assertEquals(3, seekMap.length)
            assertEquals(built.moofOffsets[0], seekMap.offsets[0])
            assertEquals(built.moofOffsets[1], seekMap.offsets[1])
            assertEquals(built.moofOffsets[2], seekMap.offsets[2])
            assertEquals(built.moofOffsets[1], seekMap.getSeekPoints(4_500_000L).first.position)

            assertEquals(8_000L, probeContainerDurationMs(tempFile))
            assertEquals(8, probeContainerDurationSeconds(tempFile))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun fragmentedMp4WithSidxDefersToNativeExtractor() {
        val built = buildSyntheticFmp4(
            timescale = 90_000,
            defaultSampleDuration = 3_000,
            fragments = listOf(Triple(0L, 90, 256)),
            includeSidx = true,
        )
        val tempFile = File.createTempFile("fmp4_sidx_", ".mp4")
        try {
            tempFile.writeBytes(built.bytes)
            val delegate = object : Extractor {
                override fun sniff(input: ExtractorInput): Boolean = true
                override fun init(output: ExtractorOutput) {}
                override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = Extractor.RESULT_END_OF_INPUT
                override fun seek(position: Long, timeUs: Long) {}
                override fun release() {}
            }
            val extractor = SeekableFragmentedMp4Extractor(delegate)
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {}
            })
            RandomAccessFile(tempFile, "r").use { raf ->
                val input = RandomAccessFileExtractorInput(raf)
                val holder = PositionHolder()
                while (true) {
                    val res = extractor.read(input, holder)
                    if (res == Extractor.RESULT_SEEK) input.seekTo(holder.position)
                    else if (res == Extractor.RESULT_END_OF_INPUT) break
                }
            }
            assertNull(extractor.buildFallbackSeekMapForTesting())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun matroskaWithoutCuesOrDurationBuildsExactChunkIndex() {
        val built = buildSyntheticWebmWithoutCuesOrDuration(
            clusters = listOf(
                Triple(0L, 256, 2000),
                Triple(2_500L, 96 * 1024, 2400),
                Triple(5_000L, 512, 1_500), // ends at 5000 + 1500 = 6500 ms
            )
        )
        val tempFile = File.createTempFile("webm_nocues_", ".webm")
        try {
            tempFile.writeBytes(built.bytes)
            var emittedSeekMap: SeekMap? = null
            val delegate = object : Extractor {
                private lateinit var out: ExtractorOutput
                override fun sniff(input: ExtractorInput): Boolean = true
                override fun init(output: ExtractorOutput) {
                    out = output
                }
                override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
                    out.seekMap(SeekMap.Unseekable(C.TIME_UNSET))
                    return Extractor.RESULT_END_OF_INPUT
                }
                override fun seek(position: Long, timeUs: Long) {}
                override fun release() {}
            }

            val extractor = SeekableMatroskaExtractor(delegate)
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {
                    emittedSeekMap = seekMap
                }
            })

            // Simulate ProgressiveMediaPeriod$ExtractingLoadable's initial seek(0, 0) before first read().
            extractor.seek(0L, 0L)

            RandomAccessFile(tempFile, "r").use { raf ->
                val input = RandomAccessFileExtractorInput(raf)
                val holder = PositionHolder()
                while (true) {
                    val res = extractor.read(input, holder)
                    if (res == Extractor.RESULT_SEEK) input.seekTo(holder.position)
                    else if (res == Extractor.RESULT_END_OF_INPUT) break
                }
            }

            val seekMap = emittedSeekMap as? ChunkIndex
            assertNotNull("Expected ChunkIndex for Cues-less WebM", seekMap)
            seekMap!!
            assertTrue(seekMap.isSeekable)
            assertEquals(6_500_000L, seekMap.durationUs)
            assertEquals(3, seekMap.length)
            assertEquals(built.clusterOffsets[0], seekMap.offsets[0])
            assertEquals(built.clusterOffsets[1], seekMap.offsets[1])
            assertEquals(built.clusterOffsets[2], seekMap.offsets[2])
            assertEquals(built.clusterOffsets[1], seekMap.getSeekPoints(3_000_000L).first.position)

            assertEquals(6_500L, probeContainerDurationMs(tempFile))
            assertEquals(7, probeContainerDurationSeconds(tempFile))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun fragmentedMp4SniffsLavfStyleAndSurvivesInitialSeekZeroAndPartialRetry() {
        val fmp4 = buildPlayableSyntheticFmp4()
        val realExtractor = FragmentedMp4Extractor(
            androidx.media3.extractor.text.SubtitleParser.Factory.UNSUPPORTED,
            FragmentedMp4Extractor.FLAG_MERGE_FRAGMENTED_SIDX,
        )
        val sniffInput = InMemoryExtractorInput(fmp4.bytes)
        assertTrue("Real FragmentedMp4Extractor must sniff Lavf-style fMP4", realExtractor.sniff(sniffInput))

        var emittedSeekMap: SeekMap? = null
        val recordedDelegateSeeks = ArrayList<Pair<Long, Long>>()
        var delegateReads = 0
        val delegate = object : Extractor {
            private lateinit var out: ExtractorOutput
            override fun sniff(input: ExtractorInput): Boolean = true
            override fun init(output: ExtractorOutput) {
                out = output
            }
            override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
                delegateReads++
                if (delegateReads == 1) {
                    out.seekMap(SeekMap.Unseekable(C.TIME_UNSET))
                }
                return Extractor.RESULT_END_OF_INPUT
            }
            override fun seek(position: Long, timeUs: Long) {
                recordedDelegateSeeks.add(position to timeUs)
            }
            override fun release() {}
        }

        val extractor = SeekableFragmentedMp4Extractor(delegate)
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
            override fun endTracks() {}
            override fun seekMap(seekMap: SeekMap) {
                emittedSeekMap = seekMap
            }
        })

        // Simulate ExtractingLoadable initial seek(0, 0) + mid-scan cancel + retry seek(0, 0)
        val input = InMemoryExtractorInput(fmp4.bytes)
        extractor.seek(0L, 0L)
        extractor.seek(0L, 0L)

        val holder = PositionHolder()
        var steps = 0
        while (steps++ < 500) {
            val res = extractor.read(input, holder)
            if (res == Extractor.RESULT_SEEK) {
                input.seekTo(holder.position)
            } else if (res == Extractor.RESULT_END_OF_INPUT) {
                break
            }
        }

        val seekMap = emittedSeekMap as? ChunkIndex
        assertNotNull("Expected seekable ChunkIndex after initial seek(0, 0)", seekMap)
        seekMap!!
        assertTrue(seekMap.isSeekable)
        assertEquals(9_000_000L, seekMap.durationUs)

        // Subsequent user seek to 4.5s must delegate directly without rescanning
        val seekPoint = seekMap.getSeekPoints(4_500_000L).first
        assertEquals(3_000_000L, seekPoint.timeUs)
        assertEquals(fmp4.moofOffsets[1], seekPoint.position)

        extractor.seek(seekPoint.position, seekPoint.timeUs)
        input.seekTo(seekPoint.position)
        val postSeekRes = extractor.read(input, holder)
        assertEquals(Extractor.RESULT_END_OF_INPUT, postSeekRes)
        assertEquals(2, delegateReads)
        assertEquals(seekPoint.position to seekPoint.timeUs, recordedDelegateSeeks.last())
    }

    private fun buildPlayableSyntheticFmp4(): SyntheticMp4 {
        val timescale = 15_360 // matches user's Lavf ffprobe output!
        val sampleDuration = 512 // 30 fps (15360 / 512 = 30)
        val out = ByteArrayOutputStream()
        val moofOffsets = ArrayList<Long>()

        // ftyp (isom, Lavf style)
        val ftypPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeBytes("isom")
                writeInt(512)
                writeBytes("isomiso2avc1mp41")
            }
        }.toByteArray()
        out.write(mp4Box("ftyp", ftypPayload))

        // mvhd (duration = 0, like ffmpeg -movflags empty_moov)
        val mvhdPayload = ByteArray(100).apply {
            // timescale at offset 12..15 = 1000
            this[14] = 0x03
            this[15] = 0xE8.toByte()
            // rate = 0x00010000 at 20..23
            this[21] = 0x01
            // volume = 0x0100 at 24..25
            this[24] = 0x01
            // unity matrix
            this[37] = 0x01
            this[53] = 0x01
            this[68] = 0x40
            // next_track_id = 2 at 96..99
            this[99] = 0x02
        }

        val tkhdPayload = ByteArray(84).apply {
            this[3] = 0x03 // flags = enabled | in_movie
            this[15] = 0x01 // track_id = 1
            // unity matrix
            this[41] = 0x01
            this[57] = 0x01
            this[72] = 0x40
            // width = 540 << 16, height = 960 << 16
            this[76] = (540 shr 8).toByte()
            this[77] = (540 and 0xFF).toByte()
            this[80] = (960 shr 8).toByte()
            this[81] = (960 and 0xFF).toByte()
        }

        val mdhdPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0) // version + flags
                writeInt(0) // creation
                writeInt(0) // modification
                writeInt(timescale)
                writeInt(0) // duration = 0
                writeShort(0x55C4) // language und
                writeShort(0)
            }
        }.toByteArray()

        val hdlrPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0)
                writeInt(0)
                writeBytes("vide")
                writeInt(0)
                writeInt(0)
                writeInt(0)
                writeByte(0)
            }
        }.toByteArray()

        // Minimal avcC (Baseline profile, level 3.1, 4-byte NAL length)
        val avcCPayload = byteArrayOf(
            0x01, 0x42, 0x00, 0x1F, 0xFF.toByte(),
            0xE1.toByte(), 0x00, 0x04, 0x67, 0x42, 0x00, 0x1F,
            0x01, 0x00, 0x04, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte()
        )
        val avc1Body = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                write(ByteArray(6)) // reserved
                writeShort(1) // data_reference_index
                write(ByteArray(16)) // pre_defined + reserved
                writeShort(540) // width
                writeShort(960) // height
                writeInt(0x00480000) // horizresolution
                writeInt(0x00480000) // vertresolution
                writeInt(0) // reserved
                writeShort(1) // frame_count
                write(ByteArray(32)) // compressorname
                writeShort(0x0018) // depth
                writeShort(-1) // pre_defined
                write(mp4Box("avcC", avcCPayload))
            }
        }.toByteArray()
        val stsdPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0) // version + flags
                writeInt(1) // entry_count = 1
                write(mp4Box("avc1", avc1Body))
            }
        }.toByteArray()
        val emptyFullBox = ByteArray(8)
        val emptyStsz = ByteArray(12)
        val stbl = mp4Box(
            "stbl",
            mp4Box("stsd", stsdPayload) +
                mp4Box("stts", emptyFullBox) +
                mp4Box("stsc", emptyFullBox) +
                mp4Box("stsz", emptyStsz) +
                mp4Box("stco", emptyFullBox)
        )
        val minf = mp4Box("minf", stbl)
        val mdia = mp4Box("mdia", mp4Box("mdhd", mdhdPayload) + mp4Box("hdlr", hdlrPayload) + minf)
        val trak = mp4Box("trak", mp4Box("tkhd", tkhdPayload) + mdia)

        val trexPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0) // version + flags
                writeInt(1) // track_id
                writeInt(1) // default_sample_description_index
                writeInt(sampleDuration)
                writeInt(16) // default_sample_size
                writeInt(0x02000000) // default_sample_flags (sync)
            }
        }.toByteArray()
        val mvex = mp4Box("mvex", mp4Box("trex", trexPayload))
        out.write(mp4Box("moov", mp4Box("mvhd", mvhdPayload) + trak + mvex))

        // 3 fragments, 3s each = 9s total (90 samples per fragment * 512 ticks = 46080 ticks = 3s)
        val sampleCount = 90
        val sampleSize = 16
        for (fragIdx in 0 until 3) {
            moofOffsets.add(out.size().toLong())
            val mfhdPayload = ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    writeInt(0)
                    writeInt(fragIdx + 1)
                }
            }.toByteArray()
            val tfhdPayload = ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    // flags = 0x020000 (default-base-is-moof)
                    writeInt(0x00020000)
                    writeInt(1) // track_id
                }
            }.toByteArray()
            val tfdtPayload = ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    writeInt(0x01000000) // version 1
                    writeLong(fragIdx.toLong() * sampleCount * sampleDuration)
                }
            }.toByteArray()

            // First build moof with placeholder data_offset=0 to know exact moof size
            fun buildMoof(dataOffset: Int): ByteArray {
                val trunPayload = ByteArrayOutputStream().apply {
                    DataOutputStream(this).apply {
                        writeInt(0x00000001) // flags = data-offset-present
                        writeInt(sampleCount)
                        writeInt(dataOffset)
                    }
                }.toByteArray()
                val traf = mp4Box("traf", mp4Box("tfhd", tfhdPayload) + mp4Box("tfdt", tfdtPayload) + mp4Box("trun", trunPayload))
                return mp4Box("moof", mp4Box("mfhd", mfhdPayload) + traf)
            }
            val moofSize = buildMoof(0).size
            out.write(buildMoof(moofSize + 8))
            out.write(mp4Box("mdat", ByteArray(sampleCount * sampleSize)))
        }

        return SyntheticMp4(out.toByteArray(), moofOffsets)
    }

    private class InMemoryExtractorInput(private val data: ByteArray) : ExtractorInput {
        private var pos = 0
        private var peekPos = 0

        fun seekTo(position: Long) {
            pos = position.toInt().coerceIn(0, data.size)
            peekPos = pos
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos >= data.size) return C.RESULT_END_OF_INPUT
            val toRead = minOf(length, data.size - pos)
            System.arraycopy(data, pos, buffer, offset, toRead)
            pos += toRead
            peekPos = pos
            return toRead
        }

        override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            if (pos + length > data.size) {
                if (pos == data.size && allowEndOfInput) return false
                throw java.io.EOFException()
            }
            System.arraycopy(data, pos, target, offset, length)
            pos += length
            peekPos = pos
            return true
        }

        override fun readFully(target: ByteArray, offset: Int, length: Int) {
            readFully(target, offset, length, false)
        }

        override fun skip(length: Int): Int {
            if (pos >= data.size) return C.RESULT_END_OF_INPUT
            val skipped = minOf(length, data.size - pos)
            pos += skipped
            peekPos = pos
            return skipped
        }

        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
            if (pos + length > data.size) {
                if (allowEndOfInput) return false
                throw java.io.EOFException()
            }
            pos += length
            peekPos = pos
            return true
        }

        override fun skipFully(length: Int) {
            skipFully(length, false)
        }

        override fun peek(target: ByteArray, offset: Int, length: Int): Int {
            if (peekPos >= data.size) return C.RESULT_END_OF_INPUT
            val toRead = minOf(length, data.size - peekPos)
            System.arraycopy(data, peekPos, target, offset, toRead)
            peekPos += toRead
            return toRead
        }

        override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            if (peekPos + length > data.size) {
                if (allowEndOfInput) return false
                throw java.io.EOFException()
            }
            System.arraycopy(data, peekPos, target, offset, length)
            peekPos += length
            return true
        }

        override fun peekFully(target: ByteArray, offset: Int, length: Int) {
            peekFully(target, offset, length, false)
        }

        override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean {
            if (peekPos + length > data.size) {
                if (allowEndOfInput) return false
                throw java.io.EOFException()
            }
            peekPos += length
            return true
        }

        override fun advancePeekPosition(length: Int) {
            advancePeekPosition(length, false)
        }

        override fun resetPeekPosition() {
            peekPos = pos
        }

        override fun getPeekPosition(): Long = peekPos.toLong()
        override fun getPosition(): Long = pos.toLong()
        override fun getLength(): Long = data.size.toLong()
        override fun <E : Throwable> setRetryPosition(position: Long, e: E): Unit = throw e
    }

    // --- Synthetic container builders ---

    private data class SyntheticMp4(val bytes: ByteArray, val moofOffsets: List<Long>)

    private fun buildSyntheticFmp4(
        timescale: Int,
        defaultSampleDuration: Int,
        fragments: List<Triple<Long, Int, Int>>, // (tfdtTicks, sampleCount, mdatSize)
        includeSidx: Boolean,
    ): SyntheticMp4 {
        val out = ByteArrayOutputStream()
        val moofOffsets = ArrayList<Long>()

        // ftyp
        out.write(mp4Box("ftyp", byteArrayOf('i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), '5'.code.toByte(), 0, 0, 0, 1)))

        // moov (mvhd duration=0, trak video, mvex/trex)
        val tkhdPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0) // version=0, flags=0
                writeInt(0) // creation
                writeInt(0) // modification
                writeInt(1) // track_id = 1
                writeInt(0) // reserved
                writeInt(0) // duration = 0
            }
        }.toByteArray()
        val mdhdPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0) // version=0, flags=0
                writeInt(0) // creation
                writeInt(0) // modification
                writeInt(timescale)
                writeInt(0) // duration = 0
            }
        }.toByteArray()
        val hdlrPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0)
                writeInt(0)
                writeBytes("vide")
                writeInt(0)
            }
        }.toByteArray()
        val mdia = mp4Box("mdia", mp4Box("mdhd", mdhdPayload) + mp4Box("hdlr", hdlrPayload))
        val trak = mp4Box("trak", mp4Box("tkhd", tkhdPayload) + mdia)

        val trexPayload = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(0) // version + flags
                writeInt(1) // track_id = 1
                writeInt(1) // default_sample_description_index
                writeInt(defaultSampleDuration)
                writeInt(0)
                writeInt(0)
            }
        }.toByteArray()
        val mvex = mp4Box("mvex", mp4Box("trex", trexPayload))
        out.write(mp4Box("moov", trak + mvex))

        if (includeSidx) {
            val sidxPayload = ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    writeInt(0) // version=0, flags=0
                    writeInt(1) // ref_id
                    writeInt(timescale)
                    writeInt(0) // earliest_presentation_time
                    writeInt(0) // first_offset
                    writeShort(0) // reserved
                    writeShort(1) // reference_count = 1
                    writeInt(100)
                    writeInt(90_000)
                    writeInt(0)
                }
            }.toByteArray()
            out.write(mp4Box("sidx", sidxPayload))
        }

        for ((tfdtTicks, sampleCount, mdatSize) in fragments) {
            moofOffsets.add(out.size().toLong())
            val tfhdPayload = ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    writeInt(0) // version=0, flags=0 (inherit trex default duration)
                    writeInt(1) // track_id = 1
                }
            }.toByteArray()
            val tfdtPayload = ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    writeInt(0x01000000) // version=1
                    writeLong(tfdtTicks)
                }
            }.toByteArray()
            val trunPayload = ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    writeInt(0) // flags=0 -> sampleCount * defaultSampleDuration
                    writeInt(sampleCount)
                }
            }.toByteArray()
            val traf = mp4Box("traf", mp4Box("tfhd", tfhdPayload) + mp4Box("tfdt", tfdtPayload) + mp4Box("trun", trunPayload))
            out.write(mp4Box("moof", traf))
            out.write(mp4Box("mdat", ByteArray(mdatSize)))
        }

        return SyntheticMp4(out.toByteArray(), moofOffsets)
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(8 + payload.size)
        DataOutputStream(out).apply {
            writeInt(8 + payload.size)
            writeBytes(type)
            write(payload)
        }
        return out.toByteArray()
    }

    private data class SyntheticWebm(val bytes: ByteArray, val clusterOffsets: List<Long>)

    private fun buildSyntheticWebmWithoutCuesOrDuration(
        clusters: List<Triple<Long, Int, Int>>, // (clusterTimeMs, payloadBytes, lastBlockRelMs)
    ): SyntheticWebm {
        val out = ByteArrayOutputStream()
        val clusterOffsets = ArrayList<Long>()

        // EBML header (minimal)
        out.write(ebmlElement(0x1A45DFA3, byteArrayOf(0x42, 0x86.toByte(), 0x81.toByte(), 0x01)))

        // Segment with unknown size (0x01FFFFFFFFFFFFFF)
        out.write(intToBytes(0x18538067, 4))
        out.write(byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))

        // Info with TimecodeScale = 1_000_000 ns (NO Duration element!)
        val tcScale = ebmlElement(0x2AD7B1, intToBytes(1_000_000, 3))
        out.write(ebmlElement(0x1549A966, tcScale))

        // Clusters (NO Cues element!)
        for ((timeMs, payloadSize, lastBlockRelMs) in clusters) {
            clusterOffsets.add(out.size().toLong())
            val clusterBody = ByteArrayOutputStream()
            // Cluster Timecode (0xE7)
            clusterBody.write(ebmlElement(0xE7, intToBytes(timeMs.toInt(), 4)))
            // SimpleBlock (0xA3): track=1 (0x81), relTime (2B), flags (1B), payload
            val block = ByteArrayOutputStream()
            block.write(0x81) // track 1 VINT
            block.write((lastBlockRelMs shr 8) and 0xFF)
            block.write(lastBlockRelMs and 0xFF)
            block.write(0x80) // keyframe flag
            block.write(ByteArray(payloadSize))
            clusterBody.write(ebmlElement(0xA3, block.toByteArray()))
            out.write(ebmlElement(0x1F43B675, clusterBody.toByteArray()))
        }

        return SyntheticWebm(out.toByteArray(), clusterOffsets)
    }

    private fun ebmlElement(id: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val idBytes = when {
            id > 0xFFFFFF -> intToBytes(id, 4)
            id > 0xFFFF -> intToBytes(id, 3)
            id > 0xFF -> intToBytes(id, 2)
            else -> intToBytes(id, 1)
        }
        out.write(idBytes)
        out.write(encodeEbmlSize(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    private fun encodeEbmlSize(size: Int): ByteArray {
        // Use 4-byte VINT (0x10xxxxxx) for simplicity (supports up to ~256MB)
        val v = 0x10000000 or (size and 0x0FFFFFFF)
        return intToBytes(v, 4)
    }

    private fun intToBytes(value: Int, count: Int): ByteArray {
        val res = ByteArray(count)
        for (i in 0 until count) {
            res[count - 1 - i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
        return res
    }
}
