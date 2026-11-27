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

internal sealed class RustStaticDecodeOutcome {
    data class Decoded(val data: RustDecodedRgba) : RustStaticDecodeOutcome()
    data object DeferToPlatform : RustStaticDecodeOutcome()
    data object Failed : RustStaticDecodeOutcome()
}

internal class RustStaticDirectMeta(
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

internal sealed class RustStaticDirectOutcome {
    data class Decoded(val meta: RustStaticDirectMeta) : RustStaticDirectOutcome()
    data object DeferToPlatform : RustStaticDirectOutcome()
    data object BufferTooSmall : RustStaticDirectOutcome()
    data object Failed : RustStaticDirectOutcome()
}

/**
 * Pure-Rust isolated-process media decoder bridge (`png`, `image-webp`, `gif`, `jxl-oxide`).
 */
internal object NativeMediaDecoder {
    private const val FORMAT_PNG = 2
    private const val FORMAT_WEBP = 3
    private const val FORMAT_GIF = 4
    private const val FORMAT_JXL = 5

    private const val SCALE_MODE_FIT_INSIDE = 0
    private const val SCALE_MODE_FILL_MIN_SIDE = 1
    private const val BOUNDS_LEN = 2
    private const val STATIC_META_LEN = 5
    private const val STATIC_META_SRC_W = 2
    private const val STATIC_META_SRC_H = 3
    private const val STATIC_META_STATUS = 4
    private const val NATIVE_STATUS_OK = 0
    private const val NATIVE_STATUS_DEFER_PLATFORM = 1
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
    ): RustStaticDecodeOutcome {
        val code = toFormatCode(format) ?: return RustStaticDecodeOutcome.Failed
        if (bytes.isEmpty()) return RustStaticDecodeOutcome.Failed
        if (!loaded) {
            return if (format == IsolatedMediaFormat.PNG || format == IsolatedMediaFormat.WEBP) {
                RustStaticDecodeOutcome.DeferToPlatform
            } else {
                RustStaticDecodeOutcome.Failed
            }
        }
        val meta = IntArray(STATIC_META_LEN)
        val scaleMode = if (fillMinSide) SCALE_MODE_FILL_MIN_SIDE else SCALE_MODE_FIT_INSIDE
        val rgba = runCatching {
            NativeMediaJni.decodeStaticRgbaExNative(code, bytes, targetW, targetH, exifOrientation, scaleMode, meta)
        }.getOrNull()
        if (rgba == null) {
            return if (meta[STATIC_META_STATUS] == NATIVE_STATUS_DEFER_PLATFORM) {
                RustStaticDecodeOutcome.DeferToPlatform
            } else {
                RustStaticDecodeOutcome.Failed
            }
        }

        val w = meta[0]
        val h = meta[1]
        val expectedBytes = w.toLong() * h.toLong() * RGBA_BYTES_PER_PIXEL
        val validHeader = meta[STATIC_META_STATUS] == NATIVE_STATUS_OK && w > 0 && h > 0
        if (!validHeader || rgba.size.toLong() != expectedBytes) {
            return RustStaticDecodeOutcome.Failed
        }
        return RustStaticDecodeOutcome.Decoded(
            RustDecodedRgba(
                width = w,
                height = h,
                sourceWidth = meta[STATIC_META_SRC_W],
                sourceHeight = meta[STATIC_META_SRC_H],
                rgba = rgba,
            ),
        )
    }

    @Suppress("LongParameterList")
    fun decodeStaticRgbaIntoDirectBuffer(
        format: IsolatedMediaFormat,
        bytes: ByteArray,
        targetW: Int,
        targetH: Int,
        exifOrientation: Int = 1,
        fillMinSide: Boolean = false,
        directBuffer: ByteBuffer,
    ): RustStaticDirectOutcome {
        val code = toFormatCode(format) ?: return RustStaticDirectOutcome.Failed
        if (bytes.isEmpty() || !directBuffer.isDirect) return RustStaticDirectOutcome.Failed
        if (!loaded) {
            return if (format == IsolatedMediaFormat.PNG || format == IsolatedMediaFormat.WEBP) {
                RustStaticDirectOutcome.DeferToPlatform
            } else {
                RustStaticDirectOutcome.Failed
            }
        }
        val meta = IntArray(STATIC_META_LEN)
        val scaleMode = if (fillMinSide) SCALE_MODE_FILL_MIN_SIDE else SCALE_MODE_FIT_INSIDE
        val ok = runCatching {
            NativeMediaJni.decodeStaticRgbaIntoBufferNative(
                code,
                bytes,
                targetW,
                targetH,
                exifOrientation,
                scaleMode,
                directBuffer,
                meta,
            )
        }.getOrDefault(false)
        return mapDirectBufferOutcome(ok, meta)
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
        IsolatedMediaFormat.PNG -> FORMAT_PNG
        IsolatedMediaFormat.WEBP -> FORMAT_WEBP
        IsolatedMediaFormat.GIF -> FORMAT_GIF
        IsolatedMediaFormat.JXL -> FORMAT_JXL
        else -> null
    }
}

private const val DIRECT_META_SRC_W = 2
private const val DIRECT_META_SRC_H = 3
private const val DIRECT_META_STATUS = 4
private const val DIRECT_STATUS_OK = 0
private const val DIRECT_STATUS_DEFER_PLATFORM = 1
private const val DIRECT_STATUS_BUFFER_TOO_SMALL = 3

private fun mapDirectBufferOutcome(ok: Boolean, meta: IntArray): RustStaticDirectOutcome {
    if (!ok) {
        return when (meta[DIRECT_META_STATUS]) {
            DIRECT_STATUS_DEFER_PLATFORM -> RustStaticDirectOutcome.DeferToPlatform
            DIRECT_STATUS_BUFFER_TOO_SMALL -> RustStaticDirectOutcome.BufferTooSmall
            else -> RustStaticDirectOutcome.Failed
        }
    }
    val w = meta[0]
    val h = meta[1]
    if (meta[DIRECT_META_STATUS] != DIRECT_STATUS_OK || w <= 0 || h <= 0) {
        return RustStaticDirectOutcome.Failed
    }
    return RustStaticDirectOutcome.Decoded(
        RustStaticDirectMeta(
            width = w,
            height = h,
            sourceWidth = meta[DIRECT_META_SRC_W],
            sourceHeight = meta[DIRECT_META_SRC_H],
        ),
    )
}
