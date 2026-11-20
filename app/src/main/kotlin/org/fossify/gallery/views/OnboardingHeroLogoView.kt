package org.fossify.gallery.views

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import org.fossify.gallery.R
import org.fossify.gallery.helpers.AppearancePalette
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated Calypso Gallery hero emblem for the first-launch onboarding screen:
 * ambient pulse rings, two flanking mini-cards (vault lock & virtual albums) that fan out
 * and gently float, and the central Calypso viewfinder logo whose frame draws itself
 * before the signature aperture orb pops into place.
 */
class OnboardingHeroLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private var palette: AppearancePalette = AppearancePalette.DARK
    private var logoBgColor = Color.parseColor("#161619")
    private var logoFgColor = Color.parseColor("#FAFAFA")
    private var logoBorderColor = Color.parseColor("#2E2E35")
    private var haloFillColor = ColorUtils.setAlphaComponent(Color.WHITE, 16)
    private var haloRingColor = ColorUtils.setAlphaComponent(Color.WHITE, 34)
    private var cardBgColor = Color.parseColor("#141417")
    private var cardBorderColor = Color.parseColor("#3F3F46")
    private var cardIconColor = Color.parseColor("#A1A1AA")

    private val leftIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_onboarding_vault_lock)?.mutate()
    private val rightIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_onboarding_virtual_albums)?.mutate()

    // Animation progress values
    private var badgeProgress = 0f
    private var frameDrawProgress = 0f
    private var orbScale = 0f
    private var fanLeftProgress = 0f
    private var fanRightProgress = 0f
    private var ambientPhase = 0f
    private var haloPhase1 = 0f
    private var haloPhase2 = 0f

    private var entranceSet: AnimatorSet? = null
    private val loopAnimators = mutableListOf<Animator>()
    private var hasStarted = false

    // Reusable drawing objects (no allocations inside onDraw)
    private val cardRect = RectF()
    private val badgeRect = RectF()
    private val logoFrameFullPath = Path()
    private val logoFrameSegmentPath = Path()
    private val logoFrameMeasure = PathMeasure()
    private var builtLogoSize = -1f

    private val haloInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val haloOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }
    private val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val haloGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val logoFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val logoOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        applyPalette(AppearancePalette.DARK)
    }

    fun applyPalette(newPalette: AppearancePalette) {
        palette = newPalette
        val isLightBg = AppearancePalette.contrastRatio(newPalette.background, Color.WHITE) <
            AppearancePalette.contrastRatio(newPalette.background, Color.BLACK)

        if (isLightBg) {
            logoBgColor = Color.parseColor("#09090B")
            logoFgColor = Color.WHITE
            logoBorderColor = ColorUtils.setAlphaComponent(Color.parseColor("#09090B"), 26)
            haloFillColor = ColorUtils.setAlphaComponent(newPalette.text, 18)
            haloRingColor = ColorUtils.setAlphaComponent(newPalette.text, 38)
            cardBgColor = newPalette.surface
            cardBorderColor = ColorUtils.blendARGB(newPalette.border, newPalette.text, 0.14f)
            cardIconColor = newPalette.secondary
        } else {
            logoBgColor = ColorUtils.blendARGB(newPalette.background, Color.WHITE, 0.06f)
            logoFgColor = newPalette.text
            logoBorderColor = ColorUtils.blendARGB(newPalette.border, Color.WHITE, 0.08f)
            haloFillColor = ColorUtils.setAlphaComponent(newPalette.text, 16)
            haloRingColor = ColorUtils.setAlphaComponent(newPalette.text, 34)
            cardBgColor = newPalette.surface
            cardBorderColor = ColorUtils.blendARGB(newPalette.border, newPalette.text, 0.12f)
            cardIconColor = newPalette.secondary
        }

        cardFillPaint.color = cardBgColor
        cardStrokePaint.color = cardBorderColor
        haloGlowPaint.color = haloFillColor
        badgeFillPaint.color = logoBgColor
        badgeStrokePaint.color = logoBorderColor
        logoFramePaint.color = logoFgColor
        logoOrbPaint.color = logoFgColor

        leftIcon?.let { DrawableCompat.setTint(it, cardIconColor) }
        rightIcon?.let { DrawableCompat.setTint(it, cardIconColor) }
        invalidate()
    }

    fun startAnimation() {
        stopAnimations()
        hasStarted = true

        if (!ValueAnimator.areAnimatorsEnabled()) {
            badgeProgress = 1f
            frameDrawProgress = 1f
            orbScale = 1f
            fanLeftProgress = 1f
            fanRightProgress = 1f
            haloPhase1 = 0.25f
            haloPhase2 = 0.75f
            invalidate()
            return
        }

        badgeProgress = 0f
        frameDrawProgress = 0f
        orbScale = 0f
        fanLeftProgress = 0f
        fanRightProgress = 0f

        val badgeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560L
            startDelay = 50L
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                badgeProgress = it.animatedValue as Float
                invalidate()
            }
        }

        val frameAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            startDelay = 190L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                frameDrawProgress = it.animatedValue as Float
                invalidate()
            }
        }

        val orbAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 480L
            startDelay = 580L
            interpolator = OvershootInterpolator(1.8f)
            addUpdateListener {
                orbScale = it.animatedValue as Float
                invalidate()
            }
        }

        val leftFanAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            startDelay = 260L
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                fanLeftProgress = it.animatedValue as Float
                invalidate()
            }
        }

        val rightFanAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 720L
            startDelay = 340L
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener {
                fanRightProgress = it.animatedValue as Float
                invalidate()
            }
        }

        entranceSet = AnimatorSet().apply {
            playTogether(badgeAnim, frameAnim, orbAnim, leftFanAnim, rightFanAnim)
            start()
        }

        val floatLoop = ValueAnimator.ofFloat(0f, (2.0 * PI).toFloat()).apply {
            duration = 5800L
            startDelay = 950L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                ambientPhase = it.animatedValue as Float
                invalidate()
            }
        }

        val halo1 = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4600L
            startDelay = 450L
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                haloPhase1 = it.animatedValue as Float
                invalidate()
            }
        }

        val halo2 = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4600L
            startDelay = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                haloPhase2 = it.animatedValue as Float
                invalidate()
            }
        }

        loopAnimators.addAll(listOf(floatLoop, halo1, halo2))
        loopAnimators.forEach { it.start() }
    }

    fun stopAnimations() {
        entranceSet?.cancel()
        entranceSet = null
        loopAnimators.forEach { it.cancel() }
        loopAnimators.clear()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!hasStarted) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        hasStarted = false
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (210f * density).toInt()
        val desiredH = (122f * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val scaleUnit = min(width / (210f * density), height / (122f * density)).coerceAtMost(1f)
        val d = density * scaleUnit

        // 1. Ambient pulsing rings
        drawHaloRing(canvas, cx, cy, baseRadius = 50f * d, phase = haloPhase1, paint = haloInnerPaint)
        drawHaloRing(canvas, cx, cy, baseRadius = 64f * d, phase = haloPhase2, paint = haloOuterPaint)

        // 2. Flanking mini-cards
        val driftLeft = sin(ambientPhase.toDouble()).toFloat()
        val driftRight = sin(ambientPhase.toDouble() + 1.4).toFloat()

        drawMiniCard(
            canvas = canvas,
            cx = cx,
            cy = cy,
            progress = fanLeftProgress,
            targetTx = -50f * d - 2f * d * driftLeft,
            targetTy = 4f * d - 3f * d * driftLeft,
            targetAngle = -12f - 1.5f * driftLeft,
            cardW = 50f * d,
            cardH = 60f * d,
            radius = 13f * d,
            icon = leftIcon,
            iconSize = (20f * d).toInt()
        )

        drawMiniCard(
            canvas = canvas,
            cx = cx,
            cy = cy,
            progress = fanRightProgress,
            targetTx = 50f * d + 2f * d * driftRight,
            targetTy = 4f * d - 3f * d * driftRight,
            targetAngle = 12f + 1.5f * driftRight,
            cardW = 50f * d,
            cardH = 60f * d,
            radius = 13f * d,
            icon = rightIcon,
            iconSize = (20f * d).toInt()
        )

        // 3. Central Calypso badge
        if (badgeProgress <= 0f) return
        val badgeSize = 74f * d
        val badgeScale = 0.82f + 0.18f * badgeProgress
        val badgeTy = (1f - badgeProgress) * 8f * d
        val badgeRadius = 21f * d

        canvas.save()
        canvas.translate(cx, cy + badgeTy)
        canvas.scale(badgeScale, badgeScale)

        val alpha = (badgeProgress * 255f).toInt().coerceIn(0, 255)

        // Outer subtle glow halo
        val haloPad = 6f * d
        badgeRect.set(
            -badgeSize / 2f - haloPad,
            -badgeSize / 2f - haloPad,
            badgeSize / 2f + haloPad,
            badgeSize / 2f + haloPad
        )
        haloGlowPaint.alpha = (Color.alpha(haloFillColor) * badgeProgress).toInt()
        canvas.drawRoundRect(badgeRect, badgeRadius + haloPad * 0.6f, badgeRadius + haloPad * 0.6f, haloGlowPaint)

        // Badge body
        badgeRect.set(-badgeSize / 2f, -badgeSize / 2f, badgeSize / 2f, badgeSize / 2f)
        badgeFillPaint.alpha = alpha
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeFillPaint)
        badgeStrokePaint.alpha = (Color.alpha(logoBorderColor) * badgeProgress).toInt()
        canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeStrokePaint)

        // 4. Calypso logo geometry (mapped from 512x512 SVG viewport scaled by 1.42 around 256,256)
        ensureLogoPath(badgeSize)
        val strokeW = (22f / 512f) * 1.42f * badgeSize
        logoFramePaint.strokeWidth = strokeW
        logoFramePaint.alpha = alpha

        if (frameDrawProgress > 0f) {
            logoFrameSegmentPath.reset()
            val stopDist = logoFrameMeasure.length * frameDrawProgress.coerceIn(0f, 1f)
            logoFrameMeasure.getSegment(0f, stopDist, logoFrameSegmentPath, true)
            canvas.drawPath(logoFrameSegmentPath, logoFramePaint)
        }

        // Signature top-right orb (cx=320, cy=192, r=54 in 512x512 scaled by 1.42)
        if (orbScale > 0f) {
            val orbCx = ((320f - 256f) / 512f) * 1.42f * badgeSize
            val orbCy = ((192f - 256f) / 512f) * 1.42f * badgeSize
            val orbR = (54f / 512f) * 1.42f * badgeSize * orbScale.coerceAtLeast(0f)
            logoOrbPaint.alpha = (orbScale.coerceIn(0f, 1f) * 255f).toInt()
            canvas.drawCircle(orbCx, orbCy, orbR, logoOrbPaint)
        }

        canvas.restore()
    }

    private fun ensureLogoPath(badgeSize: Float) {
        if (builtLogoSize == badgeSize) return
        builtLogoSize = badgeSize
        logoFrameFullPath.reset()

        // Rect x=171..341, y=171..341, rx=29 in 512x512 scaled by 1.42 around center 256
        val half = ((85f / 512f) * 1.42f) * badgeSize
        val rx = ((29f / 512f) * 1.42f) * badgeSize
        val rect = RectF(-half, -half, half, half)
        logoFrameFullPath.addRoundRect(rect, rx, rx, Path.Direction.CW)
        logoFrameMeasure.setPath(logoFrameFullPath, false)
    }

    private fun drawHaloRing(canvas: Canvas, cx: Float, cy: Float, baseRadius: Float, phase: Float, paint: Paint) {
        if (phase <= 0f || phase >= 1f) return
        val alphaFactor = if (phase < 0.35f) {
            phase / 0.35f
        } else {
            (1f - phase) / 0.65f
        }
        val radius = baseRadius * (0.84f + 0.40f * phase)
        paint.color = haloRingColor
        paint.alpha = (Color.alpha(haloRingColor) * alphaFactor).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun drawMiniCard(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        progress: Float,
        targetTx: Float,
        targetTy: Float,
        targetAngle: Float,
        cardW: Float,
        cardH: Float,
        radius: Float,
        icon: Drawable?,
        iconSize: Int
    ) {
        if (progress <= 0f) return
        val tx = targetTx * progress
        val ty = (6f * density) * (1f - progress) + targetTy * progress
        val angle = targetAngle * progress
        val scale = 0.80f + 0.20f * progress
        val alpha = (progress * 0.92f * 255f).toInt().coerceIn(0, 255)

        canvas.save()
        canvas.translate(cx + tx, cy + ty)
        canvas.rotate(angle)
        canvas.scale(scale, scale)

        cardRect.set(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f)
        cardFillPaint.alpha = alpha
        cardStrokePaint.alpha = alpha
        canvas.drawRoundRect(cardRect, radius, radius, cardFillPaint)
        canvas.drawRoundRect(cardRect, radius, radius, cardStrokePaint)

        icon?.let {
            val half = iconSize / 2
            it.setBounds(-half, -half, half, half)
            it.alpha = alpha
            it.draw(canvas)
        }
        canvas.restore()
    }
}
