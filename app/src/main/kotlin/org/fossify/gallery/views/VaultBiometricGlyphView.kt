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
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser
import org.fossify.gallery.helpers.AppearancePalette
import kotlin.math.exp

/**
 * "İz dalgası" — the fingerprint ridges used on the 3b biometric setup card.
 *
 * Timeline (target design):
 *  1. 0–810 ms   : seven ridges are drawn with trimPathEnd-style segments, 450 ms each,
 *                  staggered by 90 ms (core first, outer arcs last), cubic-bezier(0.4, 0, 0.2, 1).
 *  2. 850–1250 ms: all ridges darken from 30% to full opacity, ease-in-out.
 *  3. 1250–2150 ms: the 48 dp background circle fades in around the centered 28 dp SVG icon
 *                  while its outer CSS-style box-shadow glow rises to 18 dp blur / 5 dp spread
 *                  at 35% (38% alpha) then settles at 8 dp blur / 1 dp spread (16% alpha).
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
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowStopPositions = FloatArray(GLOW_STOPS)
    private val glowStopColors = IntArray(GLOW_STOPS)

    private var ridgeAlpha = 0.3f
    private var circleAlpha = 0f
    private var glowBlurDp = 0f
    private var glowSpreadDp = 0f
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
        glowBlurDp = 0f
        glowSpreadDp = 0f
        glowAlpha = 0f
        invalidate()

        ridges.forEachIndexed { index, ridge ->
            animators += ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DRAW_DURATION_MS
                startDelay = ridge.order * RIDGE_STAGGER_MS
                interpolator = DRAW_INTERPOLATOR
                addUpdateListener {
                    ridgeProgress[index] = it.animatedValue as Float
                    invalidate()
                }
            }
        }

        animators += ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = DARKEN_DURATION_MS
            startDelay = DARKEN_START_MS
            interpolator = DARKEN_INTERPOLATOR
            addUpdateListener {
                ridgeAlpha = it.animatedValue as Float
                invalidate()
            }
        }

        // CSS keyframe timing functions apply per keyframe interval (0%->35% and 35%->100%).
        animators += ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CIRCLE_DURATION_MS
            startDelay = CIRCLE_START_MS
            interpolator = null
            addUpdateListener {
                val rawT = it.animatedValue as Float
                if (rawT <= GLOW_PEAK_AT) {
                    val segT = CIRCLE_INTERPOLATOR.getInterpolation(
                        (rawT / GLOW_PEAK_AT).coerceIn(0f, 1f)
                    )
                    circleAlpha = segT
                    glowBlurDp = GLOW_PEAK_BLUR_DP * segT
                    glowSpreadDp = GLOW_PEAK_SPREAD_DP * segT
                    glowAlpha = GLOW_ALPHA_PEAK * segT
                } else {
                    val segT = CIRCLE_INTERPOLATOR.getInterpolation(
                        ((rawT - GLOW_PEAK_AT) / (1f - GLOW_PEAK_AT)).coerceIn(0f, 1f)
                    )
                    circleAlpha = 1f
                    glowBlurDp = GLOW_PEAK_BLUR_DP + (GLOW_SETTLED_BLUR_DP - GLOW_PEAK_BLUR_DP) * segT
                    glowSpreadDp = GLOW_PEAK_SPREAD_DP + (GLOW_SETTLED_SPREAD_DP - GLOW_PEAK_SPREAD_DP) * segT
                    glowAlpha = GLOW_ALPHA_PEAK + (GLOW_ALPHA_SETTLED - GLOW_ALPHA_PEAK) * segT
                }
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
        glowBlurDp = GLOW_SETTLED_BLUR_DP
        glowSpreadDp = GLOW_SETTLED_SPREAD_DP
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
        val circleScale = minOf(width, height) / CIRCLE_SIZE_DP
        if (circleScale <= 0f) return

        // Step 1: Draw the 48 dp background circle and its CSS-accurate Gaussian box-shadow glow
        // in the 48x48 dp coordinate space so the circle never touches the 28 dp inner fingerprint.
        if (circleAlpha > 0f) {
            canvas.save()
            canvas.scale(circleScale, circleScale)
            val center = CIRCLE_SIZE_DP / 2f
            val circleRadius = center

            if ((glowBlurDp > 0f || glowSpreadDp > 0f) && glowAlpha > 0f) {
                drawCssBoxShadowGlow(canvas, center, circleRadius)
            }

            circlePaint.color = ColorUtils.setAlphaComponent(
                container,
                (circleAlpha * 255f).toInt().coerceIn(0, 255),
            )
            canvas.drawCircle(center, center, circleRadius, circlePaint)
            canvas.restore()
        }

        // Step 2: Center the 28x28 dp SVG (authored in a 48x48 viewBox) inside the 48x48 dp circle,
        // matching HTML `.icon { width: 48px; height: 48px }` and `.icon svg { width: 28px; height: 28px }`.
        val insetPx = ((CIRCLE_SIZE_DP - SVG_SIZE_DP) / 2f) * circleScale
        val svgScale = (SVG_SIZE_DP * circleScale) / VIEWBOX
        canvas.save()
        canvas.translate(insetPx, insetPx)
        canvas.scale(svgScale, svgScale)

        ridgePaint.alpha = (ridgeAlpha * 255f).toInt().coerceIn(0, 255)
        ridges.forEachIndexed { index, ridge ->
            val length = ridge.measure.length
            if (length <= 0f || ridgeProgress[index] <= 0f) return@forEachIndexed
            segment.reset()
            ridge.measure.getSegment(0f, length * ridgeProgress[index], segment, true)
            canvas.drawPath(segment, ridgePaint)
        }
        canvas.restore()
    }

    /**
     * Emulates CSS `box-shadow: 0 0 <blur> <spread> color` around a circle of [circleRadius].
     *
     * In CSS, spread expands the shadow shape to `circleRadius + spread`, and a Gaussian blur
     * with standard deviation `sigma = blur / 2` convolves the step edge so that opacity at the
     * expanded edge is ~0.5 of peak, higher inside (covered by the opaque circle), and decays
     * smoothly with a Gaussian tail outside.
     */
    private fun drawCssBoxShadowGlow(canvas: Canvas, center: Float, circleRadius: Float) {
        val spreadRadius = circleRadius + glowSpreadDp
        // Sigma for CSS blur radius is ~0.5 * blur; extend gradient out to 2.2 * sigma (~1.1 * blur)
        // where Gaussian falloff reaches zero smoothly without a harsh cutoff.
        val blurExtent = glowBlurDp * 1.15f
        val outerRadius = spreadRadius + blurExtent
        if (outerRadius <= circleRadius) return

        val innerStop = (circleRadius / outerRadius).coerceIn(0f, 0.99f)
        val sigma = (glowBlurDp * 0.5f).coerceAtLeast(0.001f)

        for (i in 0 until GLOW_STOPS) {
            val frac = i.toFloat() / (GLOW_STOPS - 1)
            val r = circleRadius + frac * (outerRadius - circleRadius)
            glowStopPositions[i] = (r / outerRadius).coerceIn(innerStop, 1f)

            // Distance outside the circle perimeter relative to the spread boundary:
            // inside the spread ring (r <= spreadRadius) the unblurred shadow is full strength,
            // softened by the Gaussian convolution near the spread edge; outside, it follows a
            // smooth Gaussian decay.
            val falloff = if (i == GLOW_STOPS - 1) {
                0f
            } else {
                val distFromSpread = (r - spreadRadius).coerceAtLeast(0f)
                val normalized = distFromSpread / sigma
                exp(-0.5f * normalized * normalized)
            }
            val a = (glowAlpha * circleAlpha * falloff * 255f).toInt().coerceIn(0, 255)
            glowStopColors[i] = ColorUtils.setAlphaComponent(primary, a)
        }

        glowPaint.shader = RadialGradient(
            center,
            center,
            outerRadius,
            glowStopColors,
            glowStopPositions,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(center, center, outerRadius, glowPaint)
    }

    private companion object {
        const val CIRCLE_SIZE_DP = 48f
        const val SVG_SIZE_DP = 28f
        const val VIEWBOX = 48f
        const val DRAW_DURATION_MS = 450L
        const val RIDGE_STAGGER_MS = 90L
        const val DARKEN_START_MS = 850L
        const val DARKEN_DURATION_MS = 400L
        const val CIRCLE_START_MS = 1250L
        const val CIRCLE_DURATION_MS = 900L
        const val GLOW_PEAK_AT = 0.35f
        const val GLOW_PEAK_BLUR_DP = 18f
        const val GLOW_PEAK_SPREAD_DP = 5f
        const val GLOW_SETTLED_BLUR_DP = 8f
        const val GLOW_SETTLED_SPREAD_DP = 1f
        const val GLOW_ALPHA_PEAK = 0.38f
        const val GLOW_ALPHA_SETTLED = 0.16f
        const val GLOW_STOPS = 12

        val DRAW_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        val DARKEN_INTERPOLATOR = PathInterpolator(0.42f, 0f, 0.58f, 1f)
        val CIRCLE_INTERPOLATOR = DecelerateInterpolator()

        /** Path data and stagger order from the target design (viewBox 48x48). */
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
