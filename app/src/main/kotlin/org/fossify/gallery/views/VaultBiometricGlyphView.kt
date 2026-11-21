package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import org.fossify.gallery.helpers.AppearancePalette

/**
 * "İz dalgası" — the fingerprint ridges used on the 3b biometric setup card.
 *
 * Timeline (matches the approved HTML prototype):
 *  1. 0–1010 ms  : seven ridges are drawn with trimPathEnd-style segments, 450 ms each,
 *                  staggered by 90 ms (core first, outer arcs last).
 *  2. 850–1250 ms: all ridges darken from 30% to full opacity.
 *  3. 1250–2150 ms: the background circle fades in and its glow rises to 12 dp then settles at 4 dp.
 *
 * The circle appears only once, after the ridges are complete — there is no inner ring or wave.
 * When system animations are disabled the final state is drawn immediately, like the HTML's
 * reduced-motion rule.
 */
class VaultBiometricGlyphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class Ridge(val measure: PathMeasure, val order: Int)

    private val ridges: List<Ridge> = RIDGES.map { (data, order) ->
        val path = PathParser.createPathFromPathData(data)
        Ridge(PathMeasure(path, false), order)
    }
    private val ridgeProgress = FloatArray(ridges.size)
    private val segment = Path()

    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.1f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var ridgeAlpha = 0.3f
    private var circleAlpha = 0f
    private var glowRadius = 0f
    private var glowAlpha = 0f
    private var primary = Color.WHITE
    private var container = Color.LTGRAY

    private val animators = mutableListOf<ValueAnimator>()

    fun applyPalette(palette: AppearancePalette) {
        primary = palette.primary
        container = ColorUtils.blendARGB(palette.surface, palette.primary, 0.08f)
        ridgePaint.color = primary
        invalidate()
    }

    /** Restarts the ridge → darken → circle/glow sequence. */
    fun start() {
        cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            showFinal()
            return
        }

        ridgeProgress.fill(0f)
        ridgeAlpha = 0.3f
        circleAlpha = 0f
        glowRadius = 0f
        glowAlpha = 0f
        invalidate()

        ridges.forEachIndexed { index, ridge ->
            animators += ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DRAW_DURATION_MS
                startDelay = ridge.order * RIDGE_STAGGER_MS
                addUpdateListener {
                    ridgeProgress[index] = it.animatedValue as Float
                    invalidate()
                }
            }
        }

        animators += ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = DARKEN_DURATION_MS
            startDelay = DARKEN_START_MS
            addUpdateListener {
                ridgeAlpha = it.animatedValue as Float
                invalidate()
            }
        }

        animators += ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CIRCLE_DURATION_MS
            startDelay = CIRCLE_START_MS
            addUpdateListener {
                val t = it.animatedValue as Float
                circleAlpha = t
                glowRadius = if (t < GLOW_PEAK_AT) {
                    GLOW_MAX_DP * (t / GLOW_PEAK_AT)
                } else {
                    GLOW_MAX_DP - (GLOW_MAX_DP - GLOW_SETTLED_DP) * ((t - GLOW_PEAK_AT) / (1f - GLOW_PEAK_AT))
                }
                glowAlpha = GLOW_ALPHA_PEAK - (GLOW_ALPHA_PEAK - GLOW_ALPHA_SETTLED) * t
                invalidate()
            }
        }

        animators.forEach { it.start() }
    }

    /** Static end state for reduced-motion / animators-disabled devices. */
    fun showFinal() {
        cancel()
        ridgeProgress.fill(1f)
        ridgeAlpha = 1f
        circleAlpha = 1f
        glowRadius = GLOW_SETTLED_DP
        glowAlpha = GLOW_ALPHA_SETTLED
        invalidate()
    }

    private fun cancel() {
        animators.forEach { it.cancel() }
        animators.clear()
    }

    override fun onDetachedFromWindow() {
        cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = width / VIEWBOX.toFloat()
        if (scale <= 0f) return

        canvas.save()
        canvas.scale(scale, scale)
        val center = VIEWBOX / 2f
        val radius = center

        if (circleAlpha > 0f) {
            if (glowRadius > 0f && glowAlpha > 0f) {
                glowPaint.shader = RadialGradient(
                    center, center, radius + glowRadius,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(primary, (glowAlpha * 255).toInt()),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
                glowPaint.alpha = (circleAlpha * 255).toInt()
                canvas.drawCircle(center, center, radius + glowRadius, glowPaint)
            }
            circlePaint.color = ColorUtils.setAlphaComponent(container, (circleAlpha * 255).toInt())
            canvas.drawCircle(center, center, radius, circlePaint)
        }

        ridgePaint.alpha = (ridgeAlpha * 255).toInt()
        ridges.forEachIndexed { index, ridge ->
            val length = ridge.measure.length
            if (length <= 0f || ridgeProgress[index] <= 0f) return@forEachIndexed
            segment.reset()
            ridge.measure.getSegment(0f, length * ridgeProgress[index], segment, true)
            canvas.drawPath(segment, ridgePaint)
        }
        canvas.restore()
    }

    private companion object {
        const val VIEWBOX = 48f
        const val DRAW_DURATION_MS = 450L
        const val RIDGE_STAGGER_MS = 90L
        const val DARKEN_START_MS = 850L
        const val DARKEN_DURATION_MS = 400L
        const val CIRCLE_START_MS = 1250L
        const val CIRCLE_DURATION_MS = 900L
        const val GLOW_PEAK_AT = 0.35f
        const val GLOW_MAX_DP = 12f
        const val GLOW_SETTLED_DP = 4f
        const val GLOW_ALPHA_PEAK = 0.38f
        const val GLOW_ALPHA_SETTLED = 0.16f

        /** Path data and stagger order from the approved HTML prototype (viewBox 48x48). */
        val RIDGES: List<Pair<String, Int>> = listOf(
            "M20 38v-9a4 4 0 0 1 8 0v3c0 3-.4 5-1.2 7" to 0,
            "M16 40v-11a8 8 0 0 1 16 0v4c0 2.5-.3 4.5-.9 6.5" to 1,
            "M12 39v-10a12 12 0 0 1 24 0v5c0 2-.3 3.5-.7 5" to 2,
            "M8.5 35v-6a15.5 15.5 0 0 1 31 0v2" to 3,
            "M11.5 12.5A17 17 0 0 1 36.5 12.5" to 4,
            "M8.5 41v-3" to 3,
            "M39.5 34v4" to 3,
        )
    }
}
