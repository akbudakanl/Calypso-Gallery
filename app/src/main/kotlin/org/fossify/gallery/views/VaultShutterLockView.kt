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
 * Geometry follows the approved lock prototype: a wide dome with short legs, a softly rounded
 * body and a pill keyhole, all authored in the prototype's 104-unit coordinate space. The shackle
 * can be lifted independently of the body so the true/false feedback of a PIN entry feels
 * mechanical instead of a plain icon swap. Purely presentational - it never stores or derives
 * any secret.
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
        strokeJoin = Paint.Join.ROUND
    }
    private val keyholePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val shacklePath = Path()
    private val shackleArcRect = RectF()
    private val bodyRect = RectF()
    private val stemRect = RectF()

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

        val u = minOf(width, height) / ART_SIZE
        // Centre on the glyph's actual painted bounds (x 28..76, y 12..80 -> centre 52,46),
        // not the prototype's artboard origin. Mapping y=58 left the lock visibly high.
        val ox = width / 2f - 52f * u
        val oy = height / 2f - 46f * u

        if (glow > 0f) {
            canvas.drawCircle(ox + 52f * u, oy + 46f * u, (26f + glow * 12f) * u, glowPaint)
        }

        // Shackle: inverted U with a 32-unit-wide dome and 9-unit legs. It pivots around the
        // lower-left joint when the vault unlocks. forceMoveTo=false joins the leg to the arc
        // exactly at (0, -9u), so there is no gap and the apex stays well inside the view.
        canvas.save()
        canvas.translate(ox + 36f * u, oy + 37f * u)
        canvas.rotate(-26f * openProgress)
        outlinePaint.strokeWidth = 2.6f * u
        shacklePath.reset()
        shacklePath.moveTo(0f, 0f)
        shacklePath.lineTo(0f, -9f * u)
        shackleArcRect.set(0f, -25f * u, 32f * u, 7f * u)
        shacklePath.arcTo(shackleArcRect, 180f, 180f, false)
        shacklePath.lineTo(32f * u, 0f)
        canvas.drawPath(shacklePath, outlinePaint)
        canvas.restore()

        // Body + keyhole stay fixed, so the motion reads as a real latch.
        outlinePaint.strokeWidth = 2.4f * u
        bodyRect.set(ox + 28f * u, oy + 36f * u, ox + 76f * u, oy + 80f * u)
        canvas.drawRoundRect(bodyRect, 10f * u, 10f * u, bodyPaint)
        canvas.drawRoundRect(bodyRect, 10f * u, 10f * u, outlinePaint)
        canvas.drawCircle(ox + 52f * u, oy + 53f * u, 3.6f * u, keyholePaint)
        stemRect.set(ox + 50.6f * u, oy + 54.5f * u, ox + 53.4f * u, oy + 63.5f * u)
        canvas.drawRoundRect(stemRect, 1.4f * u, 1.4f * u, keyholePaint)
    }

    companion object {
        private const val ART_SIZE = 104f
    }
}
