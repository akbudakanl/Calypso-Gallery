package org.fossify.gallery.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.helpers.AppearancePalette

/**
 * Purely cosmetic, procedural frosted-glass backdrop for [org.fossify.gallery.activities.VaultLockActivity].
 *
 * Security invariant: this view NEVER reads storage, thumbnails, MediaStore, caches, or encrypted
 * vault files. It synthesizes a deterministic abstract 2x3 tonal tile grid in memory and applies
 * a multi-pass optical blur so the lock screen has frosted gallery depth with zero information leak.
 */
class VaultAmbientBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var baseColor: Int = Color.BLACK
    private var isLightBackground: Boolean = false

    private var blurredBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destRect = Rect()

    fun applyPalette(palette: AppearancePalette) {
        val nextLight = ColorUtils.calculateLuminance(palette.background) > 0.5
        val paletteChanged = baseColor != palette.background || isLightBackground != nextLight
        baseColor = palette.background
        isLightBackground = nextLight
        if (paletteChanged || blurredBitmap == null) {
            rebuildBlurredBitmap()
            rebuildVignette(width, height)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        destRect.set(0, 0, w, h)
        if (blurredBitmap == null) {
            rebuildBlurredBitmap()
        }
        rebuildVignette(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(baseColor)

        val bmp = blurredBitmap ?: return
        if (width <= 0 || height <= 0) return

        bitmapPaint.alpha = if (isLightBackground) LIGHT_BACKDROP_ALPHA else DARK_BACKDROP_ALPHA
        canvas.drawBitmap(bmp, null, destRect, bitmapPaint)

        if (vignettePaint.shader != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blurredBitmap?.recycle()
        blurredBitmap = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (blurredBitmap == null) {
            rebuildBlurredBitmap()
        }
    }

    private fun rebuildVignette(w: Int, h: Int) {
        if (w <= 0 || h <= 0) {
            vignettePaint.shader = null
            return
        }
        val cx = w * 0.5f
        val cy = h * 0.44f
        val radius = maxOf(w, h) * 0.72f
        val centerAlpha = if (isLightBackground) 32 else 24
        val midAlpha = if (isLightBackground) 80 else 88
        val edgeAlpha = if (isLightBackground) 188 else 190
        vignettePaint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                ColorUtils.setAlphaComponent(baseColor, centerAlpha),
                ColorUtils.setAlphaComponent(baseColor, midAlpha),
                ColorUtils.setAlphaComponent(baseColor, edgeAlpha)
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun rebuildBlurredBitmap() {
        blurredBitmap?.recycle()
        val w = SYNTH_WIDTH
        val h = SYNTH_HEIGHT
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(baseColor)

        // Synthetic desaturated editorial color pairs evoking a frosted 2x3 gallery grid
        val tileGradients = if (isLightBackground) {
            arrayOf(
                intArrayOf(0xFF8C7A68.toInt(), 0xFF5D6A78.toInt()),
                intArrayOf(0xFF5A7385.toInt(), 0xFF8E9AA4.toInt()),
                intArrayOf(0xFFA8845B.toInt(), 0xFF6B5A4E.toInt()),
                intArrayOf(0xFF4F6B5D.toInt(), 0xFF7D9186.toInt()),
                intArrayOf(0xFF6C5D7A.toInt(), 0xFF8D7868.toInt()),
                intArrayOf(0xFF63768C.toInt(), 0xFF8F8378.toInt())
            )
        } else {
            arrayOf(
                intArrayOf(0xFFA89480.toInt(), 0xFF4C5B6E.toInt()),
                intArrayOf(0xFF4E6B80.toInt(), 0xFF7E8C98.toInt()),
                intArrayOf(0xFFB88E5E.toInt(), 0xFF5E4A3C.toInt()),
                intArrayOf(0xFF3E6352.toInt(), 0xFF687D70.toInt()),
                intArrayOf(0xFF755D82.toInt(), 0xFF9C7B65.toInt()),
                intArrayOf(0xFF546E8A.toInt(), 0xFF8A7A6E.toInt())
            )
        }

        val padX = 5f
        val padTop = 10f
        val gap = 4.5f
        val tileW = (w - padX * 2f - gap) / 2f
        val tileH = tileW * 1.18f
        val corner = 6f
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()

        for (index in tileGradients.indices) {
            val col = index % 2
            val row = index / 2
            val left = padX + col * (tileW + gap)
            val top = padTop + row * (tileH + gap)
            rect.set(left, top, left + tileW, top + tileH)
            val colors = tileGradients[index]
            tilePaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                colors[0],
                colors[1],
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, corner, corner, tilePaint)
        }

        applyBoxBlur(bitmap, BLUR_RADIUS, BLUR_PASSES)
        blurredBitmap = bitmap
    }

    private fun applyBoxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        val temp = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val windowSize = radius * 2 + 1
        repeat(passes) {
            // Horizontal pass: pixels -> temp
            for (y in 0 until h) {
                val rowOffset = y * w
                var aSum = 0
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (i in -radius..radius) {
                    val c = pixels[rowOffset + i.coerceIn(0, w - 1)]
                    aSum += (c ushr 24) and 0xFF
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (x in 0 until w) {
                    temp[rowOffset + x] =
                        ((aSum / windowSize) shl 24) or
                            ((rSum / windowSize) shl 16) or
                            ((gSum / windowSize) shl 8) or
                            (bSum / windowSize)
                    val addColor = pixels[rowOffset + (x + radius + 1).coerceIn(0, w - 1)]
                    val subColor = pixels[rowOffset + (x - radius).coerceIn(0, w - 1)]
                    aSum += ((addColor ushr 24) and 0xFF) - ((subColor ushr 24) and 0xFF)
                    rSum += ((addColor ushr 16) and 0xFF) - ((subColor ushr 16) and 0xFF)
                    gSum += ((addColor ushr 8) and 0xFF) - ((subColor ushr 8) and 0xFF)
                    bSum += (addColor and 0xFF) - (subColor and 0xFF)
                }
            }

            // Vertical pass: temp -> pixels
            for (x in 0 until w) {
                var aSum = 0
                var rSum = 0
                var gSum = 0
                var bSum = 0
                for (i in -radius..radius) {
                    val c = temp[i.coerceIn(0, h - 1) * w + x]
                    aSum += (c ushr 24) and 0xFF
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                }
                for (y in 0 until h) {
                    pixels[y * w + x] =
                        ((aSum / windowSize) shl 24) or
                            ((rSum / windowSize) shl 16) or
                            ((gSum / windowSize) shl 8) or
                            (bSum / windowSize)
                    val addColor = temp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                    val subColor = temp[(y - radius).coerceIn(0, h - 1) * w + x]
                    aSum += ((addColor ushr 24) and 0xFF) - ((subColor ushr 24) and 0xFF)
                    rSum += ((addColor ushr 16) and 0xFF) - ((subColor ushr 16) and 0xFF)
                    gSum += ((addColor ushr 8) and 0xFF) - ((subColor ushr 8) and 0xFF)
                    bSum += (addColor and 0xFF) - (subColor and 0xFF)
                }
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    companion object {
        private const val SYNTH_WIDTH = 72
        private const val SYNTH_HEIGHT = 128
        private const val BLUR_RADIUS = 5
        private const val BLUR_PASSES = 2
        private const val DARK_BACKDROP_ALPHA = 92
        private const val LIGHT_BACKDROP_ALPHA = 56
    }
}
