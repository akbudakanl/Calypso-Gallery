package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

private const val ROTATION_90 = 90
private const val ROTATION_180 = 180
private const val ROTATION_270 = 270
private const val HALF_DIVISOR = 2f

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
    val swapsAxes = swapsAxesOrientation(exifOrientation)
    val postRotW = if (swapsAxes) source.height else source.width
    val postRotH = if (swapsAxes) source.width else source.height
    val (targetW, targetH) = target
    val (newW, newH) = if (postRotW > targetW || postRotH > targetH) {
        val scale = minOf(targetW.toFloat() / postRotW.toFloat(), targetH.toFloat() / postRotH.toFloat())
        (postRotW * scale).roundToInt().coerceAtLeast(1) to (postRotH * scale).roundToInt().coerceAtLeast(1)
    } else {
        postRotW to postRotH
    }

    val needsOrientation = needsOrientationTransform(exifOrientation)
    val needsScale = newW != postRotW || newH != postRotH
    val finalBitmap = if (!needsOrientation && !needsScale && source.config == Bitmap.Config.ARGB_8888) {
        source
    } else {
        renderTransformedBitmap(source, exifOrientation, postRotW, postRotH, newW, newH)
    }
    IsolatedHdrTransport.transformGainmap(source, finalBitmap, exifOrientation, targetW, targetH)
    return finalBitmap
}

private fun renderTransformedBitmap(
    source: Bitmap,
    exifOrientation: Int,
    postRotW: Int,
    postRotH: Int,
    newW: Int,
    newH: Int,
): Bitmap {
    val matrix = Matrix()
    matrix.setTranslate(-source.width / HALF_DIVISOR, -source.height / HALF_DIVISOR)
    applyOrientationToMatrix(matrix, exifOrientation)
    if (newW != postRotW || newH != postRotH) {
        matrix.postScale(newW.toFloat() / postRotW.toFloat(), newH.toFloat() / postRotH.toFloat())
    }
    matrix.postTranslate(newW / HALF_DIVISOR, newH / HALF_DIVISOR)

    val cs = source.colorSpace?.takeIf { it.model == ColorSpace.Model.RGB }
        ?: ColorSpace.get(ColorSpace.Named.SRGB)
    val dest = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888, true, cs)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    Canvas(dest).drawBitmap(source, matrix, paint)
    return dest
}

private fun needsOrientationTransform(orientation: Int): Boolean {
    return when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
        ExifInterface.ORIENTATION_ROTATE_180,
        ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_TRANSVERSE,
        ExifInterface.ORIENTATION_ROTATE_270 -> true
        else -> false
    }
}

private fun applyOrientationToMatrix(matrix: Matrix, orientation: Int): Boolean {
    var modified = true
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(ROTATION_180.toFloat())
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.postRotate(ROTATION_180.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(ROTATION_90.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(ROTATION_90.toFloat())
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(ROTATION_270.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(ROTATION_270.toFloat())
        else -> modified = false
    }
    return modified
}

internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    if (!applyOrientationToMatrix(matrix, orientation) || matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
