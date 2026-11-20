package org.fossify.gallery.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Lock glyph used by the vault "shutter" unlock motion.
 *
 * The shackle can be lifted independently of the body so the true/false feedback of a PIN
 * entry feels mechanical instead of a plain icon swap. Purely presentational - it never
 * stores or derives any secret.
 */
class VaultShutterLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0 = closed, 1 = fully lifted shackle. */
    var openProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** 0..1 confirmation glow burst behind the glyph. */
    var glow: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val keyholePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun setColors(strokeColor: Int, surfaceColor: Int, keyholeColor: Int) {
        outlinePaint.color = strokeColor
        bodyPaint.color = surfaceColor
        keyholePaint.color = keyholeColor
        glowPaint.color = ColorUtils.setAlphaComponent(strokeColor, 26)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val s = minOf(width, height) / 48f
        val cx = width / 2f
        val cy = height / 2f
        outlinePaint.strokeWidth = 2.4f * s

        if (glow > 0f) {
            canvas.drawCircle(cx, cy - 2f * s, (17f + glow * 9f) * s, glowPaint)
        }

        // Shackle: an inverted U that pivots around its lower-left joint when opening.
        canvas.save()
        canvas.translate(cx - 9f * s, cy - 5f * s)
        canvas.rotate(-26f * openProgress)
        val shackle = Path().apply {
            addArc(RectF(-9f * s, -18f * s, 9f * s, 0f), 180f, 180f)
        }
        canvas.drawPath(shackle, outlinePaint)
        val jointPaint = Paint(outlinePaint).apply { style = Paint.Style.FILL }
        canvas.drawCircle(-9f * s, 0f, 2.4f * s, jointPaint)
        canvas.drawCircle(9f * s, 0f, 2.4f * s, jointPaint)
        canvas.restore()

        // Body + keyhole stay fixed, so the motion reads as a real latch.
        val body = RectF(cx - 14f * s, cy - 5f * s, cx + 14f * s, cy + 14f * s)
        canvas.drawRoundRect(body, 4f * s, 4f * s, bodyPaint)
        canvas.drawRoundRect(body, 4f * s, 4f * s, outlinePaint)
        canvas.drawCircle(cx, cy + 2f * s, 2.6f * s, keyholePaint)
        val stem = RectF(cx - 1f * s, cy + 3f * s, cx + 1f * s, cy + 9f * s)
        canvas.drawRoundRect(stem, s, s, keyholePaint)
    }

}
