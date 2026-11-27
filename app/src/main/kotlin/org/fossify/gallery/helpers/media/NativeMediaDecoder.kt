package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import java.nio.ByteBuffer

internal data class RustGifOpenInfo(
    val handle: Long,
    val width: Int,
    val height: Int,
    val frameCount: Int,
)

internal class RustGifFrame(
    val durationMs: Int,
    val rgba: ByteArray,
)

internal class RustDecodedRgba(
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rgba: ByteArray,
)

/**
 * Pure-Rust isolated-process media decoder bridge (`gif`, `jxl-oxide`).
 */
internal object NativeMediaDecoder {
    private const val FORMAT_GIF = 4
    private const val FORMAT_JXL = 5

    private const val SCALE_MODE_FIT_INSIDE = 0
    private const val SCALE_MODE_FILL_MIN_SIDE = 1
    private const val BOUNDS_LEN = 2
    private const val STATIC_META_LEN = 4
    private const val STATIC_META_SRC_W = 2
    private const val STATIC_META_SRC_H = 3
    private const val GIF_INFO_LEN = 3
    private const val RGBA_BYTES_PER_PIXEL = 4L

    private val loaded: Boolean by lazy {
        runCatching { System.loadLibrary("calypso_media") }.isSuccess
    }

    fun probeBounds(format: IsolatedMediaFormat, bytes: ByteArray): Pair<Int, Int>? {
        val code = toFormatCode(format)
        val raw = if (code != null && bytes.isNotEmpty() && loaded) {
            runCatching { NativeMediaJni.probeBoundsNative(code, bytes) }.getOrNull()
        } else {
            null
        }
        if (raw == null || raw.size < BOUNDS_LEN) {
            return null
        }
        return if (raw[0] > 0 && raw[1] > 0) raw[0] to raw[1] else null
    }

    fun decodeStatic(
        format: IsolatedMediaFormat,
        bytes: ByteArray,
        targetW: Int,
        targetH: Int,
    ): Bitmap? {
        val code = toFormatCode(format) ?: return null
        if (bytes.isEmpty() || !loaded) return null
        val dims = IntArray(BOUNDS_LEN)
        val rgba = runCatching {
            NativeMediaJni.decodeStaticRgbaNative(code, bytes, targetW, targetH, dims)
        }.getOrNull() ?: return null

        val w = dims[0]
        val h = dims[1]
        val expectedBytes = w.toLong() * h.toLong() * RGBA_BYTES_PER_PIXEL
        if (w <= 0 || h <= 0 || rgba.size.toLong() != expectedBytes) {
            return null
        }
        return runCatching {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(ByteBuffer.wrap(rgba))
            }
        }.getOrNull()
    }

    fun decodeStaticRgba(
        format: IsolatedMediaFormat,
        bytes: ByteArray,
        targetW: Int,
        targetH: Int,
        exifOrientation: Int = 1,
        fillMinSide: Boolean = false,
    ): RustDecodedRgba? {
        val code = toFormatCode(format) ?: return null
        if (bytes.isEmpty() || !loaded) return null
        val meta = IntArray(STATIC_META_LEN)
        val scaleMode = if (fillMinSide) SCALE_MODE_FILL_MIN_SIDE else SCALE_MODE_FIT_INSIDE
        val rgba = runCatching {
            NativeMediaJni.decodeStaticRgbaExNative(code, bytes, targetW, targetH, exifOrientation, scaleMode, meta)
        }.getOrNull() ?: return null

        val w = meta[0]
        val h = meta[1]
        val expectedBytes = w.toLong() * h.toLong() * RGBA_BYTES_PER_PIXEL
        if (w <= 0 || h <= 0 || rgba.size.toLong() != expectedBytes) {
            return null
        }
        return RustDecodedRgba(
            width = w,
            height = h,
            sourceWidth = meta[STATIC_META_SRC_W],
            sourceHeight = meta[STATIC_META_SRC_H],
            rgba = rgba,
        )
    }

    fun openGifSession(bytes: ByteArray, maxWidth: Int, maxHeight: Int): RustGifOpenInfo? {
        if (bytes.isEmpty() || !loaded) return null
        val info = IntArray(GIF_INFO_LEN)
        val handle = runCatching {
            NativeMediaJni.openGifSessionNative(bytes, maxWidth, maxHeight, info)
        }.getOrDefault(0L)
        if (handle <= 0L) return null
        val validDims = info[0] > 0 && info[1] > 0 && info[2] > 0
        if (!validDims) {
            closeGifSession(handle)
            return null
        }
        return RustGifOpenInfo(
            handle = handle,
            width = info[0],
            height = info[1],
            frameCount = info[2],
        )
    }

    fun renderGifFrame(handle: Long, frameIndex: Int): RustGifFrame? {
        if (handle <= 0L || frameIndex < 0 || !loaded) return null
        val meta = IntArray(1)
        val rgba = runCatching {
            NativeMediaJni.renderGifFrameNative(handle, frameIndex, meta)
        }.getOrNull() ?: return null
        return RustGifFrame(durationMs = meta[0], rgba = rgba)
    }

    fun renderGifFrameIntoDirectBuffer(handle: Long, frameIndex: Int, buffer: ByteBuffer): Int? {
        val validArgs = handle > 0L && frameIndex >= 0
        if (!validArgs || !buffer.isDirect || !loaded) return null
        val meta = IntArray(1)
        val ok = runCatching {
            NativeMediaJni.renderGifFrameIntoBufferNative(handle, frameIndex, buffer, meta)
        }.getOrDefault(false)
        return if (ok) meta[0] else null
    }

    fun closeGifSession(handle: Long) {
        if (handle > 0L && loaded) {
            runCatching { NativeMediaJni.closeGifSessionNative(handle) }
        }
    }

    fun closeAllGifSessions() {
        if (loaded) {
            runCatching { NativeMediaJni.closeAllGifSessionsNative() }
        }
    }

    private fun toFormatCode(format: IsolatedMediaFormat): Int? = when (format) {
        IsolatedMediaFormat.GIF -> FORMAT_GIF
        IsolatedMediaFormat.JXL -> FORMAT_JXL
        else -> null
    }
}
