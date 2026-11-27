package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Gainmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Sanitizes and transports HDR [Gainmap] and Wide Color Gamut [ColorSpace] metadata across the
 * `isolatedProcess="true"` Binder boundary without parsing compressed ICC/MPF/XMP headers in the
 * main process.
 */
object IsolatedHdrTransport {
    const val COLOR_SPACE_SRGB = 0
    const val COLOR_SPACE_DISPLAY_P3 = 1
    const val COLOR_SPACE_BT2020 = 2
    const val COLOR_SPACE_DCI_P3 = 3
    const val COLOR_SPACE_ADOBE_RGB = 4

    const val GAINMAP_META_FLOAT_COUNT = 17

    private const val META_RATIO_MIN_END = 3
    private const val META_RATIO_MAX_END = 6
    private const val META_GAMMA_END = 9
    private const val META_EPS_SDR_END = 12
    private const val META_MIN_TRANSITION_IDX = 15
    private const val META_FULL_HDR_IDX = 16
    private const val RGB_COMPONENTS = 3

    private const val MIN_GAINMAP_RATIO = 0.001f
    private const val MAX_GAINMAP_RATIO = 64f
    private const val MIN_GAMMA = 0.01f
    private const val MAX_GAMMA = 10f
    private const val MIN_EPSILON = 1e-6f
    private const val MAX_EPSILON = 1f
    private const val MIN_HDR_TRANSITION = 1f
    private const val MAX_HDR_DISPLAY_RATIO = 64f

    fun extractColorSpaceId(bitmap: Bitmap): Int {
        val cs = bitmap.colorSpace ?: return COLOR_SPACE_SRGB
        return when (cs) {
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3) -> COLOR_SPACE_DISPLAY_P3
            ColorSpace.get(ColorSpace.Named.BT2020) -> COLOR_SPACE_BT2020
            ColorSpace.get(ColorSpace.Named.DCI_P3) -> COLOR_SPACE_DCI_P3
            ColorSpace.get(ColorSpace.Named.ADOBE_RGB) -> COLOR_SPACE_ADOBE_RGB
            else -> COLOR_SPACE_SRGB
        }
    }

    fun resolveColorSpace(colorSpaceId: Int): ColorSpace {
        val named = when (colorSpaceId) {
            COLOR_SPACE_DISPLAY_P3 -> ColorSpace.Named.DISPLAY_P3
            COLOR_SPACE_BT2020 -> ColorSpace.Named.BT2020
            COLOR_SPACE_DCI_P3 -> ColorSpace.Named.DCI_P3
            COLOR_SPACE_ADOBE_RGB -> ColorSpace.Named.ADOBE_RGB
            else -> ColorSpace.Named.SRGB
        }
        return ColorSpace.get(named)
    }

    fun sanitizeGainmapMeta(raw: FloatArray?): FloatArray? {
        if (raw == null || raw.size != GAINMAP_META_FLOAT_COUNT) return null
        if (!raw.all { it.isFinite() }) return null

        val out = FloatArray(GAINMAP_META_FLOAT_COUNT)
        for (i in 0 until RGB_COMPONENTS) {
            val rMin = raw[i].coerceIn(MIN_GAINMAP_RATIO, MAX_GAINMAP_RATIO)
            val rMax = raw[META_RATIO_MIN_END + i].coerceIn(maxOf(rMin, MIN_GAINMAP_RATIO), MAX_GAINMAP_RATIO)
            out[i] = rMin
            out[META_RATIO_MIN_END + i] = rMax
            out[META_RATIO_MAX_END + i] = raw[META_RATIO_MAX_END + i].coerceIn(MIN_GAMMA, MAX_GAMMA)
            out[META_GAMMA_END + i] = raw[META_GAMMA_END + i].coerceIn(MIN_EPSILON, MAX_EPSILON)
            out[META_EPS_SDR_END + i] = raw[META_EPS_SDR_END + i].coerceIn(MIN_EPSILON, MAX_EPSILON)
        }
        val minTransition = raw[META_MIN_TRANSITION_IDX].coerceIn(MIN_HDR_TRANSITION, MAX_HDR_DISPLAY_RATIO)
        out[META_MIN_TRANSITION_IDX] = minTransition
        out[META_FULL_HDR_IDX] = raw[META_FULL_HDR_IDX].coerceIn(minTransition, MAX_HDR_DISPLAY_RATIO)
        return out
    }

    internal fun transformGainmap(
        source: Bitmap,
        target: Bitmap,
        exifOrientation: Int,
        targetW: Int,
        targetH: Int,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!source.hasGainmap()) return
        val srcGainmap = source.gainmap ?: return
        val srcContents = srcGainmap.gainmapContents
        if (srcContents.isRecycled) return

        val oriented = applyExifOrientation(srcContents, exifOrientation)
        val maxGmW = minOf(targetW, target.width).coerceAtLeast(1)
        val maxGmH = minOf(targetH, target.height).coerceAtLeast(1)
        val scaled = GainmapPayloadCodec.scaleGainmapBitmap(oriented, maxGmW, maxGmH)
        if (scaled !== oriented && oriented !== srcContents) {
            oriented.recycle()
        }
        target.gainmap = Gainmap(srcGainmap, scaled)
    }

    internal fun packGainmapIfRequested(bitmap: Bitmap, flags: Int): IsolatedHdrPayload? {
        if ((flags and IsolatedDecodeRequest.FLAG_INCLUDE_GAINMAP) == 0) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        if (!bitmap.hasGainmap()) return null
        val gainmap = bitmap.gainmap ?: return null
        val contents = gainmap.gainmapContents
        if (contents.isRecycled) return null

        val isAlpha8 = contents.config == Bitmap.Config.ALPHA_8
        val transferBmp = if (isAlpha8 || contents.config == Bitmap.Config.ARGB_8888) {
            contents
        } else {
            contents.copy(Bitmap.Config.ARGB_8888, false)
        } ?: return null

        return try {
            GainmapPayloadCodec.packPayload(gainmap, transferBmp)
        } finally {
            if (transferBmp !== contents && !transferBmp.isRecycled) {
                transferBmp.recycle()
            }
        }
    }

    internal fun unpackAndAttachGainmap(baseBitmap: Bitmap, result: IsolatedDecodeResult) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val payload = result.hdrPayload ?: return
        val gmBitmap = GainmapPayloadCodec.decodeValidatedGainmapBitmap(baseBitmap, payload) ?: return
        val sanitizedMeta = sanitizeGainmapMeta(payload.gainmapMeta)
        if (sanitizedMeta == null) {
            gmBitmap.recycle()
            return
        }
        val gainmap = Gainmap(gmBitmap)
        GainmapPayloadCodec.applyMeta(gainmap, sanitizedMeta)
        baseBitmap.gainmap = gainmap
    }
}

private object GainmapPayloadCodec {
    private const val META_RATIO_MIN_END = 3
    private const val META_RATIO_MAX_END = 6
    private const val META_GAMMA_END = 9
    private const val META_EPS_SDR_END = 12
    private const val META_MIN_TRANSITION_IDX = 15
    private const val META_FULL_HDR_IDX = 16
    private const val ALPHA8_BYTES_PER_PIXEL = 1L
    private const val ARGB8888_BYTES_PER_PIXEL = 4L

    fun scaleGainmapBitmap(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (src.width <= maxW && src.height <= maxH) return src
        val scale = minOf(maxW.toFloat() / src.width.toFloat(), maxH.toFloat() / src.height.toFloat())
        val newW = (src.width * scale).roundToInt().coerceAtLeast(1)
        val newH = (src.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun packPayload(gainmap: Gainmap, transferBmp: Bitmap): IsolatedHdrPayload? {
        val meta = IsolatedHdrTransport.sanitizeGainmapMeta(readMeta(gainmap)) ?: return null
        val byteCount = transferBmp.byteCount
        if (byteCount <= 0) return null
        val packed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            IsolatedPixelTransport.SharedMemoryTransportApi27.writeBitmap("calypso-gm", transferBmp)
                ?.let { handle ->
                    IsolatedPixelTransport.PackedPixels(byteCount = byteCount, sharedOutputHandle = handle)
                }
        } else {
            val bytes = ByteArray(byteCount)
            transferBmp.copyPixelsToBuffer(ByteBuffer.wrap(bytes))
            IsolatedPixelTransport.packRgbaBytes("calypso-gm", bytes)
        } ?: return null
        return IsolatedHdrPayload(
            gainmapWidth = transferBmp.width,
            gainmapHeight = transferBmp.height,
            gainmapByteCount = byteCount,
            gainmapHandle = packed.sharedOutputHandle,
            gainmapPipePfd = packed.pixelPipePfd,
            gainmapMeta = meta,
        )
    }

    fun decodeValidatedGainmapBitmap(baseBitmap: Bitmap, payload: IsolatedHdrPayload): Bitmap? {
        val gmW = payload.gainmapWidth
        val gmH = payload.gainmapHeight
        val pixels = gmW.toLong() * gmH.toLong()
        val validDims = gmW in 1..baseBitmap.width &&
            gmH in 1..baseBitmap.height &&
            pixels <= IsolatedPixelTransport.MAX_TRANSFER_PIXELS
        val config = if (validDims) resolveGainmapConfig(pixels, payload.gainmapByteCount.toLong()) else null
        return config?.let { cfg ->
            val handle = payload.gainmapHandle
            val pfd = payload.gainmapPipePfd
            when {
                handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
                    readGainmapFromSharedMemory(handle, gmW, gmH, payload.gainmapByteCount, cfg)
                pfd != null -> readGainmapFromPipe(pfd, gmW, gmH, payload.gainmapByteCount, cfg)
                else -> null
            }
        }
    }

    private fun readGainmapFromPipe(
        pfd: ParcelFileDescriptor,
        width: Int,
        height: Int,
        expectedByteCount: Int,
        config: Bitmap.Config,
    ): Bitmap? {
        val bytes = IsolatedPixelTransport.PipeTransport.readBoundedFromPfd(
            pfd = pfd,
            maxBytes = expectedByteCount,
            expectedLength = expectedByteCount.toLong(),
        )?.takeIf { it.size == expectedByteCount } ?: return null
        return runCatching {
            Bitmap.createBitmap(width, height, config).apply {
                copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            }
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun readGainmapFromSharedMemory(
        handle: Parcelable,
        width: Int,
        height: Int,
        expectedByteCount: Int,
        config: Bitmap.Config,
    ): Bitmap? {
        val shm = handle as? SharedMemory ?: return null
        if (shm.size < expectedByteCount) return null
        var mapped: ByteBuffer? = null
        return try {
            mapped = shm.mapReadOnly()
            if (mapped.remaining() < expectedByteCount) return null
            Bitmap.createBitmap(width, height, config).apply {
                copyPixelsFromBuffer(mapped)
            }
        } catch (_: Throwable) {
            null
        } finally {
            if (mapped != null) runCatching { SharedMemory.unmap(mapped) }
        }
    }

    private fun resolveGainmapConfig(pixels: Long, byteCount: Long): Bitmap.Config? {
        return when (byteCount) {
            pixels * ALPHA8_BYTES_PER_PIXEL -> Bitmap.Config.ALPHA_8
            pixels * ARGB8888_BYTES_PER_PIXEL -> Bitmap.Config.ARGB_8888
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun readMeta(gm: Gainmap): FloatArray {
        val ratioMin = gm.ratioMin
        val ratioMax = gm.ratioMax
        val gamma = gm.gamma
        val epsSdr = gm.epsilonSdr
        val epsHdr = gm.epsilonHdr
        return floatArrayOf(
            ratioMin[0], ratioMin[1], ratioMin[2],
            ratioMax[0], ratioMax[1], ratioMax[2],
            gamma[0], gamma[1], gamma[2],
            epsSdr[0], epsSdr[1], epsSdr[2],
            epsHdr[0], epsHdr[1], epsHdr[2],
            gm.minDisplayRatioForHdrTransition,
            gm.displayRatioForFullHdr,
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun applyMeta(gm: Gainmap, meta: FloatArray) {
        gm.setRatioMin(meta[0], meta[1], meta[2])
        gm.setRatioMax(meta[META_RATIO_MIN_END], meta[META_RATIO_MIN_END + 1], meta[META_RATIO_MIN_END + 2])
        gm.setGamma(meta[META_RATIO_MAX_END], meta[META_RATIO_MAX_END + 1], meta[META_RATIO_MAX_END + 2])
        gm.setEpsilonSdr(meta[META_GAMMA_END], meta[META_GAMMA_END + 1], meta[META_GAMMA_END + 2])
        gm.setEpsilonHdr(meta[META_EPS_SDR_END], meta[META_EPS_SDR_END + 1], meta[META_EPS_SDR_END + 2])
        gm.minDisplayRatioForHdrTransition = meta[META_MIN_TRANSITION_IDX]
        gm.displayRatioForFullHdr = meta[META_FULL_HDR_IDX]
    }
}
