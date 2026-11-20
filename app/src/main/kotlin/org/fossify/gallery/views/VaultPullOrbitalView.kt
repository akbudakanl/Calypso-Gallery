package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Pull-to-open indicator that replaces the plain rotating lock icon.
 *
 * Two counter-rotating orbital arcs follow the pull distance, the lock in the middle scales with
 * it, and once the gesture is armed the arcs fade into a confirmation check. Purely visual -
 * no secret material is ever held here.
 */
class VaultPullOrbitalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0..1, driven by pullTranslation / holdThreshold. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** 0..1 cross-fade from orbiting arcs to the armed check mark. */
    var armedProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private var armAnimator: ValueAnimator? = null

    private val arcA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(90f, 30f, 12f, 30f), 0f)
    }
    private val arcB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(40f, 22f, 8f, 22f), 0f)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f
        strokeCap = Paint.Cap.ROUND
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setColors(strokeColor: Int, accentColor: Int, keyholeColor: Int) {
        strokePaint.color = strokeColor
        arcB.color = strokeColor
        bodyPaint.color = keyholeColor
        keyPaint.color = strokeColor
        arcA.color = accentColor
        checkPaint.color = strokeColor
        invalidate()
    }

    fun animateArm() {
        armAnimator?.cancel()
        armAnimator = ValueAnimator.ofFloat(armedProgress, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { armedProgress = it.animatedValue as Float }
            start()
        }
    }

    fun resetState() {
        armAnimator?.cancel()
        armAnimator = null
        armedProgress = 0f
        progress = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val scale = minOf(width, height) / 180f
        val armed = armedProgress
        val arcAlpha = (255 * (1f - 0.82f * armed)).toInt().coerceIn(0, 255)
        arcA.alpha = arcAlpha
        arcB.alpha = arcAlpha

        canvas.save()
        canvas.translate(width / 2f - 90f * scale, height / 2f - 90f * scale)
        canvas.scale(scale, scale)

        val outer = RectF(28f, 28f, 152f, 152f)
        val inner = RectF(14f, 14f, 166f, 166f)

        canvas.save()
        canvas.rotate(540f * progress, 90f, 90f)
        canvas.drawArc(outer, 0f, 360f, false, arcA)
        canvas.restore()

        canvas.save()
        canvas.rotate(-420f * progress, 90f, 90f)
        canvas.drawArc(inner, 0f, 360f, false, arcB)
        canvas.restore()

        // Centre lock follows the same scale curve as the prototype.
        val lockScale = 0.94f + 0.12f * progress
        canvas.save()
        canvas.translate(90f, 90f)
        canvas.scale(lockScale, lockScale)
        canvas.translate(-90f, -90f)
        canvas.drawArc(RectF(64f, 26f, 116f, 78f), 180f, 180f, false, strokePaint)
        canvas.drawLine(64f, 52f, 64f, 67f, strokePaint)
        canvas.drawLine(116f, 52f, 116f, 67f, strokePaint)
        canvas.drawRoundRect(RectF(54f, 64f, 126f, 122f), 12f, 12f, bodyPaint)
        canvas.drawRoundRect(RectF(54f, 64f, 126f, 122f), 12f, 12f, strokePaint)
        canvas.drawCircle(90f, 86f, 4.5f, keyPaint)
        canvas.drawRoundRect(RectF(88f, 88f, 92f, 99f), 2f, 2f, keyPaint)
        canvas.restore()

        if (armed > 0f) {
            checkPaint.alpha = (255 * armed).toInt().coerceIn(0, 255)
            val check = Path().apply {
                moveTo(72f, 90f)
                lineTo(84f, 102f)
                lineTo(108f, 76f)
            }
            canvas.drawPath(check, checkPaint)
        }

        canvas.restore()
    }
}
