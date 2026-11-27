package org.fossify.gallery.helpers

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Remuxes `.mp4` and `.mov` videos without re-encoding video/audio frames so that container
 * metadata (`©xyz` GPS location, device model, creation date, and vendor `udta`/`meta` atoms)
 * is stripped while keeping visual quality and orientation intact.
 */
object VideoMetadataSanitizer {

    private const val DEFAULT_BUFFER_SIZE = 1024 * 1024
    private const val KEY_FRAME_FLAG = 1

    fun remuxWithoutMetadata(source: File, destinationPfd: ParcelFileDescriptor) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(source.absolutePath)
            muxer = MediaMuxer(
                destinationPfd.fileDescriptor,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            readOrientationHint(source)?.let { muxer.setOrientationHint(it) }

            val (trackMap, maxBufferSize) = configureTracks(extractor, muxer)
            if (trackMap.isEmpty()) {
                throw IOException("No audio or video tracks found in ${source.name}")
            }

            muxer.start()
            muxerStarted = true
            copySamples(extractor, muxer, trackMap, maxBufferSize)
        } finally {
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun configureTracks(
        extractor: MediaExtractor,
        muxer: MediaMuxer
    ): Pair<Map<Int, Int>, Int> {
        val trackMap = HashMap<Int, Int>()
        var maxBufferSize = DEFAULT_BUFFER_SIZE

        for (trackIndex in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (!mime.startsWith("video/") && !mime.startsWith("audio/")) {
                continue
            }
            trackMap[trackIndex] = muxer.addTrack(format)
            extractor.selectTrack(trackIndex)
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                maxBufferSize = maxOf(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
        }
        return trackMap to maxBufferSize
    }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackMap: Map<Int, Int>,
        maxBufferSize: Int
    ) {
        val buffer = ByteBuffer.allocate(maxBufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        var trackIndex = extractor.sampleTrackIndex
        var sampleSize = if (trackIndex >= 0) extractor.readSampleData(buffer, 0) else -1
        while (trackIndex >= 0 && sampleSize >= 0) {
            val muxerTrack = trackMap[trackIndex]
            if (muxerTrack != null) {
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = mapSampleFlags(extractor.sampleFlags)
                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
            }
            extractor.advance()
            trackIndex = extractor.sampleTrackIndex
            sampleSize = if (trackIndex >= 0) extractor.readSampleData(buffer, 0) else -1
        }
    }

    private fun readOrientationHint(source: File): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun mapSampleFlags(extractorFlags: Int): Int {
        var flags = 0
        if ((extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or KEY_FRAME_FLAG
        }
        return flags
    }
}
