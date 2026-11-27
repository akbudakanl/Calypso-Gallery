package org.fossify.gallery.helpers.filters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-Kotlin non-destructive photo filter backed by Android [ColorMatrix], bounded LUT passes,
 * and radial vignette shading.
 */
data class PhotoFilter(
    val name: String,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val colorOverlay: ColorOverlaySpec? = null,
    val toneCurve: ToneCurveSpec? = null,
    val vignetteAlpha: Float = 0f,
) {
    private val hasColorAdjustments: Boolean
        get() = brightness != 0f || contrast != 1f || saturation != 1f

    val isIdentity: Boolean
        get() = !hasColorAdjustments && colorOverlay == null && toneCurve == null && vignetteAlpha <= 0f

    fun apply(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        if (isIdentity) {
            canvas.drawBitmap(source, 0f, 0f, null)
            return output
        }

        val matrixPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (hasColorAdjustments || colorOverlay != null) {
            val matrixValues = PhotoFilterMath.buildColorMatrixValues(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                overlay = colorOverlay,
            )
            matrixPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixValues))
        }
        canvas.drawBitmap(source, 0f, 0f, matrixPaint)

        toneCurve?.let { applyToneCurveInBatches(output, width, height, it) }
        if (vignetteAlpha > 0f) {
            drawVignette(canvas, width, height, vignetteAlpha)
        }
        return output
    }

    private fun applyToneCurveInBatches(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        spec: ToneCurveSpec,
    ) {
        if (width <= 0 || height <= 0) return
        val (rLut, gLut, bLut) = PhotoFilterMath.buildChannelLuts(spec)
        val rowPixels = IntArray(width * minOf(ROW_BATCH_HEIGHT, height))
        var y = 0
        while (y < height) {
            val rows = minOf(ROW_BATCH_HEIGHT, height - y)
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, rows)
            val activeCount = width * rows
            if (activeCount == rowPixels.size) {
                PhotoFilterMath.applyLutsToPixels(rowPixels, rLut, gLut, bLut)
            } else {
                val slice = rowPixels.copyOf(activeCount)
                PhotoFilterMath.applyLutsToPixels(slice, rLut, gLut, bLut)
                slice.copyInto(rowPixels)
            }
            bitmap.setPixels(rowPixels, 0, width, 0, y, width, rows)
            y += rows
        }
    }

    private fun drawVignette(
        canvas: Canvas,
        width: Int,
        height: Int,
        alphaFraction: Float,
    ) {
        val radius = max(width, height) * VIGNETTE_RADIUS_FACTOR
        if (radius <= 0f) return
        val edgeAlpha = (alphaFraction.coerceIn(0f, 1f) * PhotoFilterMath.LUT_MAX_INDEX).roundToInt()
        val shader = RadialGradient(
            width / 2f,
            height / 2f,
            radius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(edgeAlpha / 2, 0, 0, 0),
                Color.argb(edgeAlpha, 0, 0, 0),
            ),
            floatArrayOf(0f, VIGNETTE_INNER_STOP, VIGNETTE_MID_STOP, 1f),
            Shader.TileMode.CLAMP,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        private const val ROW_BATCH_HEIGHT = 64
        private const val VIGNETTE_RADIUS_FACTOR = 0.72f
        private const val VIGNETTE_INNER_STOP = 0.45f
        private const val VIGNETTE_MID_STOP = 0.78f
    }
}
