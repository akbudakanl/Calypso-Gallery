package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
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
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.helpers.CachedSeekResult
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.RandomAccessFileExtractorInput
import org.fossify.gallery.helpers.SeekIndexCacheKey
import org.fossify.gallery.helpers.SeekableExtractorsFactory
import org.fossify.gallery.helpers.SeekableFragmentedMp4Extractor
import org.fossify.gallery.helpers.SeekableMatroskaExtractor
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.VIDEO_FRAME_CACHE_TTL_MS
import org.fossify.gallery.helpers.VIDEO_FRAME_POSITION_TOLERANCE_MS
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.VideoPlaybackUtils
import org.fossify.gallery.helpers.probeContainerDurationMs
import org.fossify.gallery.helpers.probeContainerDurationSeconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

@OptIn(UnstableApi::class)
class VideoPlaybackAndSeekIndexTest {

    @Before
    fun clearSeekIndexCacheBefore() {
        SeekableExtractorsFactory.clearCache()
    }

    @After
    fun clearSeekIndexCacheAfter() {
        SeekableExtractorsFactory.clearCache()
    }

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

    @Test
    fun fragmentedMp4CachesChunkIndexAndReusesWithoutRescanning() {
        val built = buildSyntheticFmp4(
            timescale = 90_000,
            defaultSampleDuration = 3_000,
            fragments = listOf(
                Triple(0L, 90, 256),
                Triple(270_000L, 90, 96 * 1024),
                Triple(540_000L, 60, 512),
            ),
            includeSidx = false,
        )
        val key = SeekIndexCacheKey(path = "/vault/video.mp4", size = built.bytes.size.toLong(), lastModified = 12345L)

        fun runWithKey(cacheKey: SeekIndexCacheKey): Triple<SeekMap?, InMemoryExtractorInput, Int> {
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
            val extractor = SeekableFragmentedMp4Extractor(delegate, cacheKey)
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {
                    emittedSeekMap = seekMap
                }
            })
            extractor.seek(0L, 0L)
            val input = InMemoryExtractorInput(built.bytes)
            val holder = PositionHolder()
            var seekResults = 0
            while (true) {
                val res = extractor.read(input, holder)
                if (res == Extractor.RESULT_SEEK) {
                    seekResults++
                    input.seekTo(holder.position)
                } else if (res == Extractor.RESULT_END_OF_INPUT) {
                    break
                }
            }
            return Triple(emittedSeekMap, input, seekResults)
        }

        // First run: cache miss -> scans container and populates cache
        val (firstMap, firstInput, firstSeeks) = runWithKey(key)
        val firstChunk = firstMap as? ChunkIndex
        assertNotNull(firstChunk)
        assertEquals(8_000_000L, firstChunk!!.durationUs)
        assertTrue(firstInput.bytesRead > 0L)
        assertTrue(firstSeeks > 0)
        assertEquals(1, SeekableExtractorsFactory.cacheSizeForTesting())

        // Second run with same key: cache hit -> zero scan bytes read and replaces Unseekable seekMap
        val (secondMap, secondInput, secondSeeks) = runWithKey(key)
        val secondChunk = secondMap as? ChunkIndex
        assertNotNull("Cached ChunkIndex must still replace SeekMap.Unseekable", secondChunk)
        assertEquals(8_000_000L, secondChunk!!.durationUs)
        assertEquals(3, secondChunk.length)
        assertEquals(0L, secondInput.bytesRead)
        assertEquals(0L, secondInput.bytesSkipped)
        assertEquals(0, secondSeeks)

        // Changing size or lastModified causes cache miss and fresh scan
        val (_, sizeMissInput, sizeMissSeeks) = runWithKey(key.copy(size = key.size + 1L))
        assertTrue(sizeMissInput.bytesRead > 0L)
        assertTrue(sizeMissSeeks > 0)
        assertEquals(2, SeekableExtractorsFactory.cacheSizeForTesting())

        val (_, mtimeMissInput, mtimeMissSeeks) = runWithKey(key.copy(lastModified = key.lastModified + 1000L))
        assertTrue(mtimeMissInput.bytesRead > 0L)
        assertTrue(mtimeMissSeeks > 0)
        assertEquals(3, SeekableExtractorsFactory.cacheSizeForTesting())

        // clearCache() wipes all entries and forces fresh scan on next run
        SeekableExtractorsFactory.clearCache()
        assertEquals(0, SeekableExtractorsFactory.cacheSizeForTesting())
        val (_, afterClearInput, afterClearSeeks) = runWithKey(key)
        assertTrue(afterClearInput.bytesRead > 0L)
        assertTrue(afterClearSeeks > 0)
        assertEquals(1, SeekableExtractorsFactory.cacheSizeForTesting())
    }

    @Test
    fun matroskaCachesChunkIndexAndReusesWithoutRescanning() {
        val built = buildSyntheticWebmWithoutCuesOrDuration(
            clusters = listOf(
                Triple(0L, 256, 2000),
                Triple(2_500L, 96 * 1024, 2400),
                Triple(5_000L, 512, 1_500),
            )
        )
        val key = SeekIndexCacheKey(path = "/vault/clip.webm", size = built.bytes.size.toLong(), lastModified = 98765L)

        fun runWithKey(cacheKey: SeekIndexCacheKey): Triple<SeekMap?, InMemoryExtractorInput, Int> {
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
            val extractor = SeekableMatroskaExtractor(delegate, cacheKey)
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {
                    emittedSeekMap = seekMap
                }
            })
            val input = InMemoryExtractorInput(built.bytes)
            val holder = PositionHolder()
            var seekResults = 0
            while (true) {
                val res = extractor.read(input, holder)
                if (res == Extractor.RESULT_SEEK) {
                    seekResults++
                    input.seekTo(holder.position)
                } else if (res == Extractor.RESULT_END_OF_INPUT) {
                    break
                }
            }
            return Triple(emittedSeekMap, input, seekResults)
        }

        val (firstMap, firstInput, firstSeeks) = runWithKey(key)
        val firstChunk = firstMap as? ChunkIndex
        assertNotNull(firstChunk)
        assertEquals(6_500_000L, firstChunk!!.durationUs)
        assertTrue(firstInput.bytesRead > 0L)
        assertTrue(firstSeeks > 0)

        // Cache hit: zero scan bytes read and ChunkIndex still replaces Unseekable seekMap
        val (secondMap, secondInput, secondSeeks) = runWithKey(key)
        val secondChunk = secondMap as? ChunkIndex
        assertNotNull(secondChunk)
        assertEquals(6_500_000L, secondChunk!!.durationUs)
        assertEquals(3, secondChunk.length)
        assertEquals(0L, secondInput.bytesRead)
        assertEquals(0L, secondInput.bytesSkipped)
        assertEquals(0, secondSeeks)
    }

    @Test
    fun seekIndexCacheHandlesNoFallbackIoFailureFromPathAndLruEviction() {
        // 1. NoFallbackNeeded cached for fMP4 with sidx, skipped on second run
        val withSidx = buildSyntheticFmp4(
            timescale = 90_000,
            defaultSampleDuration = 3_000,
            fragments = listOf(Triple(0L, 90, 256)),
            includeSidx = true,
        )
        val sidxKey = SeekIndexCacheKey("/vault/sidx.mp4", withSidx.bytes.size.toLong(), 100L)
        repeat(2) { runIndex ->
            val extractor = SeekableFragmentedMp4Extractor(
                delegate = object : Extractor {
                    override fun sniff(input: ExtractorInput): Boolean = true
                    override fun init(output: ExtractorOutput) {}
                    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = Extractor.RESULT_END_OF_INPUT
                    override fun seek(position: Long, timeUs: Long) {}
                    override fun release() {}
                },
                cacheKey = sidxKey,
            )
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {}
            })
            val input = InMemoryExtractorInput(withSidx.bytes)
            val holder = PositionHolder()
            while (true) {
                val res = extractor.read(input, holder)
                if (res == Extractor.RESULT_SEEK) input.seekTo(holder.position)
                else if (res == Extractor.RESULT_END_OF_INPUT) break
            }
            if (runIndex == 0) {
                assertTrue(input.bytesRead > 0L)
            } else {
                assertEquals(0L, input.bytesRead)
            }
            assertNull(extractor.buildFallbackSeekMapForTesting())
        }

        // 2. Unexpected IOException during scan must NOT cache NoFallbackNeeded
        SeekableExtractorsFactory.clearCache()
        val ioFailKey = SeekIndexCacheKey("/vault/iofail.mp4", 1024L, 200L)
        val failingInput = object : ExtractorInput by InMemoryExtractorInput(ByteArray(1024)) {
            override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
                throw IOException("simulated read failure")
            }
        }
        val failingExtractor = SeekableFragmentedMp4Extractor(
            delegate = object : Extractor {
                override fun sniff(input: ExtractorInput): Boolean = true
                override fun init(output: ExtractorOutput) {}
                override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = Extractor.RESULT_END_OF_INPUT
                override fun seek(position: Long, timeUs: Long) {}
                override fun release() {}
            },
            cacheKey = ioFailKey,
        )
        failingExtractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = throw UnsupportedOperationException()
            override fun endTracks() {}
            override fun seekMap(seekMap: SeekMap) {}
        })
        assertEquals(Extractor.RESULT_END_OF_INPUT, failingExtractor.read(failingInput, PositionHolder()))
        assertEquals(0, SeekableExtractorsFactory.cacheSizeForTesting())

        // 3. SeekIndexCacheKey.fromPath guards and valid file handling
        assertNull(SeekIndexCacheKey.fromPath(""))
        assertNull(SeekIndexCacheKey.fromPath("content://media/external/video/media/123"))
        assertNull(SeekIndexCacheKey.fromPath("android.resource://org.fossify.gallery/raw/sample"))
        val emptyFile = File.createTempFile("seek_key_empty_", ".mp4")
        val nonEmptyFile = File.createTempFile("seek_key_valid_", ".mp4")
        try {
            nonEmptyFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            assertNull(SeekIndexCacheKey.fromPath(emptyFile.absolutePath))
            assertNull(SeekIndexCacheKey.fromPath(nonEmptyFile.absolutePath + ".missing"))
            val validKey = SeekIndexCacheKey.fromPath(nonEmptyFile.absolutePath)
            assertNotNull(validKey)
            assertEquals(nonEmptyFile.absolutePath, validKey!!.path)
            assertEquals(4L, validKey.size)
            assertEquals(nonEmptyFile.lastModified(), validKey.lastModified)
        } finally {
            emptyFile.delete()
            nonEmptyFile.delete()
        }

        // 4. LRU capacity capped at 128 entries
        SeekableExtractorsFactory.clearCache()
        val dummyChunk = ChunkIndex(intArrayOf(100), longArrayOf(0L), longArrayOf(1_000_000L), longArrayOf(0L))
        for (i in 0 until 130) {
            SeekableExtractorsFactory.putCachedResult(
                SeekIndexCacheKey("/vault/v_$i.mp4", 100L + i, 500L),
                org.fossify.gallery.helpers.CachedSeekResult.Hit(dummyChunk),
            )
        }
        assertEquals(128, SeekableExtractorsFactory.cacheSizeForTesting())
        assertNull(SeekableExtractorsFactory.getCachedResult(SeekIndexCacheKey("/vault/v_0.mp4", 100L, 500L)))
        assertNull(SeekableExtractorsFactory.getCachedResult(SeekIndexCacheKey("/vault/v_1.mp4", 101L, 500L)))
        assertNotNull(SeekableExtractorsFactory.getCachedResult(SeekIndexCacheKey("/vault/v_2.mp4", 102L, 500L)))
    }

    @Test
    fun surfaceAndPreviewRevealHelpersHandleQuickResumesAndSafetyNet() {
        // Preview cover is needed on visibility change unless a live TextureView already rendered a frame.
        assertFalse(
            VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(
                hasRenderedFirstFrame = true,
                hasAttachedTextureView = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(
                hasRenderedFirstFrame = false,
                hasAttachedTextureView = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(
                hasRenderedFirstFrame = true,
                hasAttachedTextureView = false,
            )
        )

        // Resuming an already-rendered valid surface reveals immediately without waiting for onRenderedFirstFrame().
        assertTrue(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = true,
                hasRenderedFirstFrame = true,
                hasValidSurface = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = false,
                hasRenderedFirstFrame = true,
                hasValidSurface = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = true,
                hasRenderedFirstFrame = false,
                hasValidSurface = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = true,
                hasRenderedFirstFrame = true,
                hasValidSurface = false,
            )
        )

        // onSurfaceTextureUpdated safety net only reveals when visible, active, valid, and matching.
        assertTrue(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = true,
                isPlayerPrepared = false,
                playWhenReady = false,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = false,
                isPlayerPrepared = true,
                playWhenReady = true,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = false,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = true,
                isPlayerPrepared = true,
                playWhenReady = true,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = false,
                isPlayerPrepared = true,
                playWhenReady = false,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = true,
                isPlayerPrepared = true,
                playWhenReady = true,
                hasValidSurface = true,
                isMatchingTexture = false,
            )
        )
    }

    @Test
    fun frameCacheTtlLasts20MinutesAndExpiresCleanly() {
        assertEquals(20 * 60 * 1000L, VIDEO_FRAME_CACHE_TTL_MS)
        val capturedAt = 1_000L

        assertFalse(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt,
            )
        )
        assertFalse(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt + VIDEO_FRAME_CACHE_TTL_MS - 1L,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt + VIDEO_FRAME_CACHE_TTL_MS,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = null,
                nowElapsedMs = capturedAt,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt + 5_000L,
                nowElapsedMs = capturedAt,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt,
                ttlMs = 0L,
            )
        )
    }

    @Test
    fun defaultPreviewAndExactLastFrameToleranceRulesBehaveAsExpected() {
        assertEquals(500L, VIDEO_FRAME_POSITION_TOLERANCE_MS)

        // Exact last frame tolerance: within 500ms is fresh, >500ms forces a fresh snapshot.
        assertTrue(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 5_000L))
        assertTrue(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 5_500L))
        assertFalse(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 5_501L))
        assertFalse(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 10_000L))
        assertFalse(VideoPlaybackUtils.isCachedFramePositionFresh(null, 5_000L))

        // Unvisited autoplay video (0:00, no cached frame) loads default frame-0 thumbnail.
        assertTrue(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = false,
                hasRestoredPosition = false,
                isAutoplay = true,
                isGesturePlayer = false,
                isPanorama = false,
            )
        )
        // Visited video with cached last frame keeps the cached frame instead of loading frame 0.
        assertFalse(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = true,
                hasRestoredPosition = true,
                isAutoplay = true,
                isGesturePlayer = false,
                isPanorama = false,
            )
        )
        // Visited autoplay video resuming mid-playback without a cached frame skips frame 0.
        assertFalse(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = false,
                hasRestoredPosition = true,
                isAutoplay = true,
                isGesturePlayer = false,
                isPanorama = false,
            )
        )
        // Non-autoplay paused video still loads default preview when no cached snapshot exists.
        assertTrue(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = false,
                hasRestoredPosition = true,
                isAutoplay = false,
                isGesturePlayer = false,
                isPanorama = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = true,
                hasRestoredPosition = false,
                isAutoplay = false,
                isGesturePlayer = false,
                isPanorama = false,
            )
        )
        // Panorama uses its own loader, not default video preview.
        assertFalse(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = false,
                hasRestoredPosition = false,
                isAutoplay = true,
                isGesturePlayer = false,
                isPanorama = true,
            )
        )

        // Existing preview drawable retention: never let frame-0 default thumbnail resurface after playback progressed.
        assertTrue(
            VideoPlaybackUtils.shouldKeepExistingPreviewDrawable(
                hasCachedFrame = false,
                isShowingDefaultPreview = true,
                hasNonZeroPlaybackPosition = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldKeepExistingPreviewDrawable(
                hasCachedFrame = false,
                isShowingDefaultPreview = true,
                hasNonZeroPlaybackPosition = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldKeepExistingPreviewDrawable(
                hasCachedFrame = false,
                isShowingDefaultPreview = false,
                hasNonZeroPlaybackPosition = true,
            )
        )
    }

    @Test
    fun clearEncryptedCacheOnlyEvictsEncryptedEntriesAndPreservesNormalGalleryOnBackground() {
        val dummyChunk = ChunkIndex(intArrayOf(100), longArrayOf(0L), longArrayOf(1_000_000L), longArrayOf(0L))
        val normalMp4 = SeekIndexCacheKey("/storage/emulated/0/DCIM/clip.mp4", 1024L, 100L)
        val normalWebm = SeekIndexCacheKey("/storage/emulated/0/Movies/clip.webm", 2048L, 200L)
        val encBin = SeekIndexCacheKey("/storage/emulated/0/Calypso Gallery/Secrets/.fv_1.bin", 4096L, 300L)
        val encFenc = SeekIndexCacheKey("/storage/emulated/0/Calypso Gallery/Secrets/clip.fenc", 8192L, 400L)

        SeekableExtractorsFactory.putCachedResult(normalMp4, CachedSeekResult.Hit(dummyChunk))
        SeekableExtractorsFactory.putCachedResult(normalWebm, CachedSeekResult.Hit(dummyChunk))
        SeekableExtractorsFactory.putCachedResult(encBin, CachedSeekResult.Hit(dummyChunk))
        SeekableExtractorsFactory.putCachedResult(encFenc, CachedSeekResult.Hit(dummyChunk))

        SeekableExtractorsFactory.clearEncryptedCache()

        assertNotNull(SeekableExtractorsFactory.getCachedResult(normalMp4))
        assertNotNull(SeekableExtractorsFactory.getCachedResult(normalWebm))
        assertNull(SeekableExtractorsFactory.getCachedResult(encBin))
        assertNull(SeekableExtractorsFactory.getCachedResult(encFenc))

        // Backgrounding / screen-off outside a vault session must preserve normal seek caches,
        // while backgrounding inside an unlocked vault wipes encrypted entries.
        val tempRoot = java.nio.file.Files.createTempDirectory("video_session_cache_test").toFile()
        try {
            val prefs = LinkedHashMap<String, Any?>()
            val testContext = object : ContextWrapper(null) {
                private val sp = object : SharedPreferences {
                    override fun getAll(): MutableMap<String, *> = prefs.toMutableMap()
                    override fun getString(key: String?, defValue: String?): String? = prefs[key] as? String ?: defValue
                    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
                    override fun getInt(key: String?, defValue: Int): Int = prefs[key] as? Int ?: defValue
                    override fun getLong(key: String?, defValue: Long): Long = prefs[key] as? Long ?: defValue
                    override fun getFloat(key: String?, defValue: Float): Float = prefs[key] as? Float ?: defValue
                    override fun getBoolean(key: String?, defValue: Boolean): Boolean = prefs[key] as? Boolean ?: defValue
                    override fun contains(key: String?): Boolean = prefs.containsKey(key)
                    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
                    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
                    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                        private val pending = LinkedHashMap<String, Any?>()
                        private var clearAll = false
                        override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
                        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
                        override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
                        override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
                        override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
                        override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
                        override fun remove(key: String?) = apply { if (key != null) pending[key] = this }
                        override fun clear() = apply { clearAll = true }
                        override fun commit(): Boolean { apply(); return true }
                        override fun apply() {
                            if (clearAll) prefs.clear()
                            pending.forEach { (k, v) -> if (v === this) prefs.remove(k) else prefs[k] = v }
                        }
                    }
                }
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = sp
                override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }
                override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }
                override fun getExternalCacheDir(): File = File(tempRoot, "ext_cache").apply { mkdirs() }
                override fun getPackageName(): String = "org.fossify.gallery.debug"
            }

            VaultSessionManager.onVaultExited()
            CryptoManager.clearKeys()
            SeekableExtractorsFactory.putCachedResult(normalMp4, CachedSeekResult.Hit(dummyChunk))

            VaultSessionManager.onScreenOff(testContext)
            VaultSessionManager.onAppBackgrounded(testContext)
            assertNotNull(SeekableExtractorsFactory.getCachedResult(normalMp4))

            val vaultDir = File(tempRoot, "Secrets").apply { mkdirs() }
            File(vaultDir, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
            testContext.config.vaultRootPath = vaultDir.absolutePath
            testContext.config.temporarilyShowHidden = true
            CryptoManager.cachedVaultRoot = vaultDir
            CryptoManager.unlockedMasterKey = ByteArray(32) { 5 }
            SeekableExtractorsFactory.putCachedResult(encBin, CachedSeekResult.Hit(dummyChunk))

            VaultSessionManager.onAppBackgrounded(testContext)
            assertFalse(CryptoManager.isVaultUnlocked)
            assertNull(SeekableExtractorsFactory.getCachedResult(encBin))
            assertNotNull(SeekableExtractorsFactory.getCachedResult(normalMp4))
        } finally {
            VaultSessionManager.onVaultExited()
            CryptoManager.clearKeys()
            CryptoManager.cachedVaultRoot = null
            tempRoot.deleteRecursively()
        }
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
        var bytesRead = 0L
            private set
        var bytesSkipped = 0L
            private set

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
            bytesRead += toRead
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
            bytesRead += length
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
            bytesSkipped += skipped
            return skipped
        }

        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
            if (pos + length > data.size) {
                if (allowEndOfInput) return false
                throw java.io.EOFException()
            }
            pos += length
            peekPos = pos
            bytesSkipped += length
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
