package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.VaultPullTonePalette

/**
 * Live mini gallery & top-scroll pull gesture simulation (Template A / Screen 3c).
 *
 * Mirrors [org.fossify.gallery.helpers.VaultPullGestureHandler] inside a self-contained preview:
 *  1. The finger touch indicator appears below the pinned search bar and pulls the 3x2 album grid
 *     downward across the hold threshold.
 *  2. Once the threshold is crossed, the lock glyph drops in from above with an overshoot settle
 *     and a one-shot primary-colored circular ring burst.
 *  3. When the drop and ring settle, the lock glyph switches to [AppearancePalette.primary] and
 *     the finger releases.
 *
 * Every color is derived dynamically from [AppearancePalette] so Light, Dark, Midnight Blue,
 * Warm Paper, Deep Forest, and user-created Custom themes all render with proper contrast.
 */
class VaultPullTutorialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var palette: AppearancePalette = AppearancePalette.LIGHT
    private var tonePalette: VaultPullTonePalette = VaultPullTonePalette.resolve(AppearancePalette.LIGHT)
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Viewport & surface colors
    private var viewportBgColor = Color.LTGRAY
    private var viewportBorderColor = Color.GRAY
    private var searchSurfaceColor = Color.WHITE
    private var searchBorderColor = Color.GRAY
    private var tileColorA = Color.LTGRAY
    private var tileColorB = Color.GRAY
    private var tileBorderColor = Color.GRAY
    private var tileCaptionColor = Color.DKGRAY

    private val viewportRect = RectF()
    private val viewportPath = Path()
    private val searchBarRect = RectF()
    private val searchLineRect = RectF()
    private val tileRect = RectF()
    private val captionRect = RectF()
    private val lockBodyRect = RectF()
    private val lockArcRect = RectF()
    private val lockShacklePath = Path()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var animator: ValueAnimator? = null
    private var progress: Float = 0f

    private val pullInterpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val easeOutInterpolator = DecelerateInterpolator()

    private fun dropSettleFraction(t: Float): Float {
        return if (t < 0.62f) {
            val u = t / 0.62f
            val quad = if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f) * (-2f * u + 2f) / 2f
            quad * 1.04f
        } else {
            val u = (t - 0.62f) / 0.38f
            val inv = 1f - u
            1.04f - 0.04f * (1f - inv * inv * inv)
        }
    }

    fun applyPalette(newPalette: AppearancePalette) {
        palette = newPalette
        tonePalette = VaultPullTonePalette.resolve(newPalette)
        val isLight = AppearancePalette.isLightBackground(newPalette.background)

        viewportBgColor = newPalette.background
        viewportBorderColor = newPalette.border
        searchSurfaceColor = newPalette.surface
        searchBorderColor = newPalette.border

        tileColorA = ColorUtils.blendARGB(
            newPalette.surface,
            newPalette.text,
            if (isLight) 0.08f else 0.12f,
        )
        tileColorB = ColorUtils.blendARGB(
            newPalette.surface,
            newPalette.primary,
            if (isLight) 0.13f else 0.18f,
        )
        tileBorderColor = ColorUtils.blendARGB(newPalette.border, newPalette.text, 0.08f)
        tileCaptionColor = ColorUtils.setAlphaComponent(newPalette.secondary, 120)
        invalidate()
    }

    fun start() {
        stop()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // Static settled "Release to open" state when system animations are disabled
            progress = 0.62f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CYCLE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = null
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        fun dp(v: Float) = v * density

        val cornerRadius = dp(14f)
        val strokePx = dp(1f).coerceAtLeast(1f)
        val halfStroke = strokePx / 2f

        viewportRect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
        viewportPath.reset()
        viewportPath.addRoundRect(viewportRect, cornerRadius, cornerRadius, Path.Direction.CW)

        // 1. Viewport background
        fillPaint.color = viewportBgColor
        canvas.drawRoundRect(viewportRect, cornerRadius, cornerRadius, fillPaint)

        val clipSave = canvas.save()
        canvas.clipPath(viewportPath)

        // 2. Pinned search bar at top
        val searchMarginH = dp(10f)
        val searchTop = dp(8f)
        val searchHeight = dp(28f)
        val searchBottom = searchTop + searchHeight
        searchBarRect.set(searchMarginH, searchTop, w - searchMarginH, searchBottom)

        fillPaint.color = searchSurfaceColor
        canvas.drawRoundRect(searchBarRect, searchHeight / 2f, searchHeight / 2f, fillPaint)
        strokePaint.strokeWidth = strokePx
        strokePaint.color = searchBorderColor
        canvas.drawRoundRect(searchBarRect, searchHeight / 2f, searchHeight / 2f, strokePaint)

        // Magnifier circle + placeholder line inside search bar
        val magCenterX = searchBarRect.left + dp(14f)
        val magCenterY = searchBarRect.centerY()
        strokePaint.strokeWidth = dp(1.5f)
        strokePaint.color = palette.secondary
        canvas.drawCircle(magCenterX, magCenterY, dp(4f), strokePaint)

        fillPaint.color = searchBorderColor
        val lineLeft = magCenterX + dp(10f)
        val lineHalfH = dp(2.5f)
        searchLineRect.set(lineLeft, magCenterY - lineHalfH, lineLeft + dp(56f), magCenterY + lineHalfH)
        canvas.drawRoundRect(searchLineRect, lineHalfH, lineHalfH, fillPaint)

        // Compute timeline states from progress (0..1 over 4200 ms)
        val gridPullFraction = when {
            progress < 0.10f -> 0f
            progress < 0.32f -> pullInterpolator.getInterpolation((progress - 0.10f) / 0.22f)
            progress < 0.74f -> 1f
            progress < 0.86f -> 1f - pullInterpolator.getInterpolation((progress - 0.74f) / 0.12f)
            else -> 0f
        }
        val maxPullPx = dp(66f)
        val gridTranslationY = gridPullFraction * maxPullPx

        // 3. Vault pull indicator (AD-1 sequential light handoff + constant-hue tonal shift)
        val indicatorCenterY = searchBottom + (dp(8f) + gridTranslationY) / 2f
        val centerX = w / 2f
        val slitY = searchBottom + dp(2f)

        // Phase 1: Horizontal light slit under search bar opens on pull and closes BEFORE aura begins
        val slitEnergy = when {
            progress in 0.16f..0.26f -> ((progress - 0.16f) / 0.10f).coerceIn(0f, 1f) * 0.72f
            progress in 0.26f..0.37f -> {
                val closeT = easeOutInterpolator.getInterpolation(((progress - 0.26f) / 0.11f).coerceIn(0f, 1f))
                0.88f * (1f - closeT)
            }
            else -> 0f
        }
        if (slitEnergy > 0.005f) {
            val slitW = dp(20f + slitEnergy * 44f)
            val rx = (slitW * 0.56f).coerceAtLeast(1f)
            val ry = dp(22f * (0.65f + 0.35f * slitEnergy)).coerceAtLeast(1f)
            val slitColor = VaultPullTonePalette.blendRgb(
                tonePalette.mutedColor,
                tonePalette.glowColor,
                if (progress >= 0.26f) 0.85f else 0.5f,
            )
            canvas.save()
            canvas.clipRect(0f, slitY, w, h)
            canvas.translate(centerX, slitY)
            canvas.scale(1f, ry / rx)
            shaderPaint.shader = RadialGradient(
                0f,
                0f,
                rx,
                intArrayOf(
                    VaultPullTonePalette.withAlpha(slitColor, tonePalette.slitPeakAlpha * slitEnergy),
                    VaultPullTonePalette.withAlpha(slitColor, tonePalette.slitMidAlpha * slitEnergy),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(0f, 0f, rx, shaderPaint)
            canvas.restore()

            val halfSlit = slitW / 2f
            shaderPaint.shader = LinearGradient(
                centerX - halfSlit,
                slitY,
                centerX + halfSlit,
                slitY,
                intArrayOf(
                    Color.TRANSPARENT,
                    VaultPullTonePalette.withAlpha(slitColor, (if (tonePalette.isLightBackground) 0.84f else 0.92f) * slitEnergy),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(centerX - halfSlit, slitY, centerX + halfSlit, slitY + dp(1.5f), shaderPaint)
            shaderPaint.shader = null
        }

        if (progress in 0.26f..0.85f) {
            val lockAlpha: Float
            val lockY: Float
            when {
                progress < 0.42f -> {
                    val t = ((progress - 0.26f) / 0.16f).coerceIn(0f, 1f)
                    val dropT = dropSettleFraction(t)
                    val startY = slitY - dp(10f)
                    lockY = startY + (indicatorCenterY - startY) * dropT
                    lockAlpha = ((lockY - (slitY - dp(2f))) / dp(14f)).coerceIn(0f, 1f)
                }
                progress < 0.74f -> {
                    lockAlpha = 1f
                    lockY = indicatorCenterY
                }
                else -> {
                    val t = ((progress - 0.74f) / 0.11f).coerceIn(0f, 1f)
                    lockAlpha = 1f - t
                    lockY = indicatorCenterY
                }
            }

            // Phase 2: Ringless soft radial aura blooms AFTER the top slit has closed (0.39..0.74)
            val auraProgress = when {
                progress < 0.39f -> 0f
                progress < 0.52f -> easeOutInterpolator.getInterpolation(((progress - 0.39f) / 0.13f).coerceIn(0f, 1f))
                progress < 0.74f -> 1f
                else -> 1f - ((progress - 0.74f) / 0.11f).coerceIn(0f, 1f)
            }
            if (auraProgress > 0.005f) {
                val discR = dp(15f * (0.86f + 0.14f * auraProgress))
                val auraR = dp(28f * (0.76f + 0.24f * auraProgress)).coerceAtLeast(1f)
                val auraColor = VaultPullTonePalette.blendRgb(
                    tonePalette.mutedColor,
                    tonePalette.glowColor,
                    0.55f + 0.45f * auraProgress,
                )
                val innerStop = ((discR * 0.72f) / auraR).coerceIn(0.15f, 0.55f)
                val midStop = (innerStop + (1f - innerStop) * 0.48f).coerceIn(innerStop + 0.05f, 0.88f)
                shaderPaint.shader = RadialGradient(
                    centerX,
                    lockY,
                    auraR,
                    intArrayOf(
                        VaultPullTonePalette.withAlpha(auraColor, tonePalette.auraPeakAlpha * auraProgress),
                        VaultPullTonePalette.withAlpha(auraColor, tonePalette.auraPeakAlpha * auraProgress),
                        VaultPullTonePalette.withAlpha(auraColor, tonePalette.auraMidAlpha * auraProgress),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, innerStop, midStop, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(centerX, lockY, auraR, shaderPaint)
                shaderPaint.shader = null

                fillPaint.color = VaultPullTonePalette.withAlpha(tonePalette.surfaceColor, 0.96f * auraProgress)
                canvas.drawCircle(centerX, lockY, discR, fillPaint)
                strokePaint.strokeWidth = dp(0.9f)
                strokePaint.color = VaultPullTonePalette.withAlpha(tonePalette.glowColor, tonePalette.rimAlpha * auraProgress)
                canvas.drawCircle(centerX, lockY, discR, strokePaint)
            }

            // Constant-hue tonal shift: muted primary while dropping -> vivid primary when armed
            val toneProgress = easeOutInterpolator.getInterpolation(
                ((progress - 0.36f) / 0.16f).coerceIn(0f, 1f)
            )
            val baseLockColor = VaultPullTonePalette.blendRgb(
                tonePalette.mutedColor,
                tonePalette.armedColor,
                toneProgress,
            )
            val lockColor = VaultPullTonePalette.withAlpha(baseLockColor, lockAlpha)
            drawLockGlyph(canvas, centerX, lockY, dp(22f), lockColor)
        }

        // 4. Album grid (3 columns x 2 rows) shifted downward by gridTranslationY
        val gridTop = searchBottom + dp(8f) + gridTranslationY
        val gridGap = dp(7f)
        val gridWidth = w - searchMarginH * 2f
        val tileWidth = (gridWidth - gridGap * 2f) / 3f
        val tileHeight = tileWidth * 0.88f
        val tileRadius = dp(9f)

        for (row in 0 until 2) {
            for (col in 0 until 3) {
                val left = searchMarginH + col * (tileWidth + gridGap)
                val top = gridTop + row * (tileHeight + gridGap)
                val right = left + tileWidth
                val bottom = top + tileHeight
                if (top > h) continue

                tileRect.set(left, top, right, bottom)
                val isAlt = (row + col) % 2 == 1
                fillPaint.color = if (isAlt) tileColorB else tileColorA
                canvas.drawRoundRect(tileRect, tileRadius, tileRadius, fillPaint)

                strokePaint.strokeWidth = strokePx
                strokePaint.color = tileBorderColor
                canvas.drawRoundRect(tileRect, tileRadius, tileRadius, strokePaint)

                // Small album title placeholder bar at bottom-left of each tile
                fillPaint.color = tileCaptionColor
                val capLeft = left + dp(6f)
                val capBottom = bottom - dp(6f)
                val capH = dp(3.5f)
                val capW = tileWidth * if (col == 1) 0.45f else 0.58f
                captionRect.set(capLeft, capBottom - capH, capLeft + capW, capBottom)
                canvas.drawRoundRect(captionRect, capH / 2f, capH / 2f, fillPaint)
            }
        }

        // 5. Finger pull gesture indicator
        if (progress in 0.06f..0.77f) {
            val fingerAlpha: Float
            val fingerScale: Float
            val fingerY: Float
            val startY = searchBottom + dp(20f)
            val endY = startY + dp(62f)

            when {
                progress < 0.12f -> {
                    val t = (progress - 0.06f) / 0.06f
                    fingerAlpha = t
                    fingerScale = 0.85f + 0.15f * t
                    fingerY = startY - dp(6f) * (1f - t)
                }
                progress < 0.32f -> {
                    val t = pullInterpolator.getInterpolation((progress - 0.12f) / 0.20f)
                    fingerAlpha = 1f
                    fingerScale = 1f - 0.05f * t
                    fingerY = startY + (endY - startY) * t
                }
                progress < 0.68f -> {
                    fingerAlpha = 1f
                    fingerScale = 0.95f
                    fingerY = endY
                }
                else -> {
                    val t = ((progress - 0.68f) / 0.09f).coerceIn(0f, 1f)
                    fingerAlpha = 1f - t
                    fingerScale = 0.95f + 0.30f * t
                    fingerY = endY
                }
            }

            val outerR = dp(12.5f) * fingerScale
            val innerR = dp(4f) * fingerScale
            fillPaint.color = ColorUtils.setAlphaComponent(
                palette.primary,
                (fingerAlpha * 56f).toInt().coerceIn(0, 255),
            )
            canvas.drawCircle(centerX, fingerY, outerR, fillPaint)

            strokePaint.strokeWidth = dp(1.5f)
            strokePaint.color = ColorUtils.setAlphaComponent(
                palette.primary,
                (fingerAlpha * 230f).toInt().coerceIn(0, 255),
            )
            canvas.drawCircle(centerX, fingerY, outerR, strokePaint)

            fillPaint.color = ColorUtils.setAlphaComponent(
                palette.primary,
                (fingerAlpha * 240f).toInt().coerceIn(0, 255),
            )
            canvas.drawCircle(centerX, fingerY, innerR, fillPaint)
        }

        canvas.restoreToCount(clipSave)

        // 6. Viewport outer border
        strokePaint.strokeWidth = strokePx
        strokePaint.color = viewportBorderColor
        canvas.drawRoundRect(viewportRect, cornerRadius, cornerRadius, strokePaint)
    }

    private fun drawLockGlyph(canvas: Canvas, cx: Float, cy: Float, sizePx: Float, color: Int) {
        // 24-unit viewport matching ic_onboarding_vault_lock.xml
        val u = sizePx / 24f
        val ox = cx - 12f * u
        val oy = cy - 12f * u

        strokePaint.strokeWidth = 1.9f * u
        strokePaint.color = color

        // Shackle: M8.31,8.54 V6.46 a3.69,3.69 0 0,1 7.38,0 v2.08
        lockShacklePath.reset()
        lockShacklePath.moveTo(ox + 8.31f * u, oy + 8.54f * u)
        lockShacklePath.lineTo(ox + 8.31f * u, oy + 6.46f * u)
        lockArcRect.set(
            ox + 8.31f * u,
            oy + (6.46f - 3.69f) * u,
            ox + (8.31f + 7.38f) * u,
            oy + (6.46f + 3.69f) * u,
        )
        lockShacklePath.arcTo(lockArcRect, 180f, 180f, false)
        lockShacklePath.lineTo(ox + 15.69f * u, oy + 8.54f * u)
        canvas.drawPath(lockShacklePath, strokePaint)

        // Body: 6.46..17.54 x 8.31..18.46, rx 2.31
        lockBodyRect.set(ox + 6.46f * u, oy + 8.31f * u, ox + 17.54f * u, oy + 18.46f * u)
        canvas.drawRoundRect(lockBodyRect, 2.31f * u, 2.31f * u, strokePaint)

        // Keyhole dot + stem
        fillPaint.color = color
        canvas.drawCircle(ox + 12f * u, oy + 12.23f * u, 1.0f * u, fillPaint)
        canvas.drawLine(ox + 12f * u, oy + 13.0f * u, ox + 12f * u, oy + 15.0f * u, strokePaint)
    }

    private companion object {
        const val CYCLE_DURATION_MS = 5000L
    }
}
