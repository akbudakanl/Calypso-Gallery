package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

private const val ROTATION_90 = 90
private const val ROTATION_180 = 180
private const val ROTATION_270 = 270

internal fun parseExifOnce(bytes: ByteArray, format: IsolatedMediaFormat): ExifInterface? {
    if (format == IsolatedMediaFormat.GIF || format == IsolatedMediaFormat.SVG) return null
    return runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
}

internal fun readExifOrientation(exif: ExifInterface?): Int {
    if (exif == null) return ExifInterface.ORIENTATION_NORMAL
    return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
}

internal fun orientationToDegrees(orientation: Int): Int {
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> ROTATION_90
        ExifInterface.ORIENTATION_ROTATE_180 -> ROTATION_180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> ROTATION_270
        else -> 0
    }
}

internal fun swapsAxesOrientation(orientation: Int): Boolean {
    return orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE
}

internal fun finalizeIsolatedBitmap(source: Bitmap, exifOrientation: Int, target: Pair<Int, Int>): Bitmap {
    val oriented = applyExifOrientation(source, exifOrientation)
    val (targetW, targetH) = target
    val scaled = if (oriented.width > targetW || oriented.height > targetH) {
        val scale = minOf(
            targetW.toFloat() / oriented.width.toFloat(),
            targetH.toFloat() / oriented.height.toFloat(),
        )
        val newW = (oriented.width * scale).roundToInt().coerceAtLeast(1)
        val newH = (oriented.height * scale).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(oriented, newW, newH, true)
    } else {
        oriented
    }
    if (scaled !== oriented && oriented !== source) {
        oriented.recycle()
    }
    return if (scaled.config == Bitmap.Config.ARGB_8888) {
        scaled
    } else {
        scaled.copy(Bitmap.Config.ARGB_8888, false).also {
            if (scaled !== source) scaled.recycle()
        }
    }
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    var modified = true
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(ROTATION_180.toFloat())
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(ROTATION_180.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(ROTATION_90.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(ROTATION_90.toFloat())
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(ROTATION_270.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(ROTATION_270.toFloat())
        else -> modified = false
    }
    if (!modified || matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
