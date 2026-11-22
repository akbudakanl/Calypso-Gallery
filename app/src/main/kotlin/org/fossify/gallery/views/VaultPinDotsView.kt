package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Draws one dot slot per PIN digit: solid dots for the digits already entered and thin outlined
 * rings for the remaining slots. Mirrors the six-slot indicator in the 02-vault store screenshot.
 *
 * The view is purely presentational - it never stores or derives the secret itself.
 */
class VaultPinDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var slotCount: Int = 0
        private set
    private var filledCount = 0
    private val dotPulse = FloatArray(MAX_SLOTS)

    private var dotDiameter = 0f
    private var dotStep = 0f
    private var ringInset = 0f

    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        val density = resources.displayMetrics.density
        dotDiameter = DOT_DIAMETER_DP * density
        dotStep = DOT_STEP_DP * density
        val strokeWidth = RING_STROKE_DP * density
        ringInset = strokeWidth / 2f
        ringStrokePaint.strokeWidth = strokeWidth
        filledPaint.color = Color.WHITE
        ringFillPaint.color = Color.TRANSPARENT
        ringStrokePaint.color = Color.GRAY
    }

    /** [count] is the total number of slots; [filled] how many of them the user has entered. */
    fun setDots(count: Int, filled: Int) {
        val nextCount = count.coerceIn(0, MAX_SLOTS)
        val nextFilled = filled.coerceIn(0, nextCount)
        if (slotCount == nextCount && filledCount == nextFilled) return
        slotCount = nextCount
        filledCount = nextFilled
        invalidate()
    }

    /** One-shot confirmation pulse used by the shutter unlock motion. */
    fun pulseDot(index: Int) {
        if (index !in 0 until slotCount) return
        ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 380L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                dotPulse[index] = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setColors(filledColor: Int, ringFillColor: Int, ringStrokeColor: Int) {
        filledPaint.color = filledColor
        ringFillPaint.color = ringFillColor
        ringStrokePaint.color = ringStrokeColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slotCount <= 0 || width <= 0 || height <= 0) return

        val radius = dotDiameter / 2f
        // Keep long PINs inside the box on narrow screens instead of clipping the last slots.
        val available = (width - paddingLeft - paddingRight - dotDiameter).coerceAtLeast(0f)
        val step = if (slotCount > 1) minOf(dotStep, available / (slotCount - 1)) else dotStep
        val totalWidth = dotDiameter + (slotCount - 1) * step
        var centerX = (width - totalWidth) / 2f + radius
        val centerY = height / 2f

        for (index in 0 until slotCount) {
            val pulseRadius = radius * (1f + 0.55f * dotPulse[index])
            if (index < filledCount) {
                canvas.drawCircle(centerX, centerY, pulseRadius, filledPaint)
            } else {
                val ringRadius = pulseRadius - ringInset
                if (ringFillPaint.color != Color.TRANSPARENT) {
                    canvas.drawCircle(centerX, centerY, ringRadius, ringFillPaint)
                }
                canvas.drawCircle(centerX, centerY, ringRadius, ringStrokePaint)
            }
            centerX += step
        }
    }

    companion object {
        private const val DOT_DIAMETER_DP = 11f
        private const val DOT_STEP_DP = 25f
        private const val RING_STROKE_DP = 1.5f
        private const val MAX_SLOTS = 14
    }
}
