package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

internal fun probePlatformBounds(bytes: ByteArray): Pair<Int, Int>? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    return if (opts.outWidth > 0 && opts.outHeight > 0) {
        opts.outWidth to opts.outHeight
    } else {
        null
    }
}

internal fun decodePlatform(
    bytes: ByteArray,
    srcW: Int,
    srcH: Int,
    targetW: Int,
    targetH: Int,
): Bitmap? {
    val sampleSize = IsolatedFormatSniffer.computeSampleSize(srcW, srcH, targetW, targetH)
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    if (bmp != null) return bmp
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return decodeWithImageDecoder(bytes, targetW, targetH)
    }
    return null
}

internal fun decodeWithImageDecoder(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return runCatching {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val size = info.size
            if (size.width > 0 && size.height > 0) {
                val bounded = IsolatedFormatSniffer.computeBoundedTargetSize(
                    size.width,
                    size.height,
                    targetW,
                    targetH,
                )
                if (bounded != null) {
                    decoder.setTargetSize(bounded.first, bounded.second)
                }
            }
        }
    }.getOrNull()
}

internal fun decodeRaw(
    bytes: ByteArray,
    srcW: Int,
    srcH: Int,
    targetW: Int,
    targetH: Int,
    flags: Int,
): Bitmap? {
    val preferPreview = (flags and IsolatedDecodeRequest.FLAG_PREFER_EMBEDDED_PREVIEW) != 0
    if (preferPreview) {
        decodeEmbeddedRawPreview(bytes, targetW, targetH)?.let { return it }
    }
    return decodePlatform(bytes, srcW, srcH, targetW, targetH)
}

private fun decodeEmbeddedRawPreview(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
    val embedded = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).thumbnailBytes
    }.getOrNull() ?: return null
    if (embedded.isEmpty()) return null
    val (ew, eh) = probePlatformBounds(embedded) ?: return null
    return decodePlatform(embedded, ew, eh, targetW, targetH)
}
