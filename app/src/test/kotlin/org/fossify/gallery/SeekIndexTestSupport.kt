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
internal object SeekIndexTestFixtures {

    internal fun buildPlayableSyntheticFmp4(): SyntheticMp4 {
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

    internal class InMemoryExtractorInput(private val data: ByteArray) : ExtractorInput {
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

    internal data class SyntheticMp4(val bytes: ByteArray, val moofOffsets: List<Long>)

    internal fun buildSyntheticFmp4(
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

    internal fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(8 + payload.size)
        DataOutputStream(out).apply {
            writeInt(8 + payload.size)
            writeBytes(type)
            write(payload)
        }
        return out.toByteArray()
    }

    internal data class SyntheticWebm(val bytes: ByteArray, val clusterOffsets: List<Long>)

    internal fun buildSyntheticWebmWithoutCuesOrDuration(
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

    internal fun ebmlElement(id: Int, payload: ByteArray): ByteArray {
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

    internal fun encodeEbmlSize(size: Int): ByteArray {
        // Use 4-byte VINT (0x10xxxxxx) for simplicity (supports up to ~256MB)
        val v = 0x10000000 or (size and 0x0FFFFFFF)
        return intToBytes(v, 4)
    }

    internal fun intToBytes(value: Int, count: Int): ByteArray {
        val res = ByteArray(count)
        for (i in 0 until count) {
            res[count - 1 - i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
        return res
    }
}
