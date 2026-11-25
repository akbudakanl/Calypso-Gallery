package org.fossify.gallery.views

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
import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.VaultPullTonePalette

/**
 * Dedicated Canvas renderer for the AD-1 sequential light handoff pull-to-vault indicator.
 *
 * Anchored flush at the top of the scrolling content holder (never translated vertically), so
 * parent `clipChildren` can never slice the lock or its radial aura mid-air:
 *  1. Phase 1 (pulling -> threshold): a subtle horizontal light slit opens at `y = 0` directly
 *     under the pinned search bar in the theme's `primary` hue and closes completely as the lock
 *     passes through.
 *  2. Phase 2 (after the slit has closed): a soft, ringless radial aura blooms behind the settled
 *     lock while the lock smoothly shifts from a calmer same-hue tone to the crisp armed tone.
 */
class VaultPullIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var tonePalette: VaultPullTonePalette =
        VaultPullTonePalette.resolve(AppearancePalette.DARK)

    /** Current downward translation of the list below, in pixels. */
    var pullTranslationPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value.coerceAtLeast(0f)
                invalidate()
            }
        }

    /** True once the pull has crossed the hold-open threshold. */
    var thresholdActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 0..1+ drop settle progress (may overshoot slightly to ~1.04). */
    var dropProgress: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 0..1 intensity of the top light slit (closes before the center aura begins). */
    var slitEnergy: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** 0..1 tonal shift for the top slit (muted -> vivid primary). */
    var slitToneBoost: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** 0..1 constant-hue tonal shift for the lock icon (muted -> armed). */
    var toneProgress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** 0..1 bloom progress of the ringless center aura (starts only after slit closes). */
    var auraProgress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** 0..1 calm breathing oscillation while armed and held (ambient pulse, no size pop). */
    var breathePhase: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val discFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val discRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lockStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lockFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val shacklePath = Path()
    private val shackleArcRect = RectF()
    private val lockBodyRect = RectF()
    private val keyholeStemRect = RectF()

    private var cachedMaskBg: Int = 0
    private var cachedMaskHeight: Float = -1f
    private var topMaskShader: LinearGradient? = null

    fun applyTonePalette(newPalette: VaultPullTonePalette) {
        if (tonePalette != newPalette) {
            tonePalette = newPalette
            topMaskShader = null
            invalidate()
        }
    }

    fun resetVisualState() {
        pullTranslationPx = 0f
        thresholdActive = false
        dropProgress = 0f
        slitEnergy = 0f
        slitToneBoost = 0f
        toneProgress = 0f
        auraProgress = 0f
        breathePhase = 0f
        alpha = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (!thresholdActive && slitEnergy <= 0.005f) return

        val density = resources.displayMetrics.density
        val cx = w / 2f
        val pal = tonePalette

        // Phase 2: Lock descent + sequential ringless radial aura
        if (thresholdActive) {
            val minCenterY = MIN_CENTER_Y_DP * density
            val targetY = (pullTranslationPx / 2f).coerceAtLeast(minCenterY)
            val startY = -START_ABOVE_DP * density
            val cy = startY + (targetY - startY) * dropProgress
            val exitAlpha = ((cy + EXIT_FADE_LEAD_DP * density) / (EXIT_FADE_SPAN_DP * density))
                .coerceIn(0f, 1f)

            if (auraProgress > 0.005f) {
                drawCenterAura(canvas, cx, cy, density, pal)
            }

            val lockColor = VaultPullTonePalette.blendRgb(
                pal.mutedColor,
                pal.armedColor,
                toneProgress,
            )
            val lockSize = LOCK_SIZE_DP * density
            drawLockGlyph(canvas, cx, cy, lockSize, lockColor, exitAlpha)

            // Soft top feather mask over the emerging lock only (drawn beneath the slit so the
            // slit's own glow never gets a dark band underneath the hairline).
            drawTopFeatherMask(canvas, w, density, pal)
        }

        // Phase 1: Horizontal light slit + smooth elliptical downward glow flush at y = 0
        if (slitEnergy > 0.005f) {
            drawTopSlit(canvas, cx, density, pal)
        }
    }

    private fun drawTopSlit(
        canvas: Canvas,
        cx: Float,
        density: Float,
        pal: VaultPullTonePalette,
    ) {
        val slitColor = VaultPullTonePalette.blendRgb(pal.mutedColor, pal.glowColor, slitToneBoost)
        val slitW = (SLIT_MIN_WIDTH_DP + slitEnergy * (SLIT_MAX_WIDTH_DP - SLIT_MIN_WIDTH_DP)) * density
        val rx = (slitW * 0.56f).coerceAtLeast(1f)
        val ry = (SLIT_BEAM_DEPTH_DP * (0.65f + 0.35f * slitEnergy) * density).coerceAtLeast(1f)

        val peakBeamAlpha = pal.slitPeakAlpha * slitEnergy
        val midBeamAlpha = pal.slitMidAlpha * slitEnergy

        // True elliptical radial dome: alpha reaches exact 0 along the curved boundary (rx, ry),
        // preventing any flat horizontal cutoff at the bottom of the glow.
        canvas.save()
        canvas.translate(cx, 0f)
        canvas.scale(1f, ry / rx)
        shaderPaint.shader = RadialGradient(
            0f,
            0f,
            rx,
            intArrayOf(
                VaultPullTonePalette.withAlpha(slitColor, peakBeamAlpha),
                VaultPullTonePalette.withAlpha(slitColor, midBeamAlpha),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(0f, 0f, rx, shaderPaint)
        canvas.restore()

        val lineAlpha = (if (pal.isLightBackground) 0.84f else 0.92f) * slitEnergy
        val halfSlit = slitW / 2f
        shaderPaint.shader = LinearGradient(
            cx - halfSlit,
            0f,
            cx + halfSlit,
            0f,
            intArrayOf(
                Color.TRANSPARENT,
                VaultPullTonePalette.withAlpha(slitColor, lineAlpha),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.50f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(cx - halfSlit, 0f, cx + halfSlit, SLIT_THICKNESS_DP * density, shaderPaint)
        shaderPaint.shader = null
    }

    private fun drawCenterAura(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        pal: VaultPullTonePalette,
    ) {
        val ea = auraProgress
        val discRadius = (DISC_RADIUS_DP * (0.86f + 0.14f * ea)) * density
        val outerRadius = ((AURA_RADIUS_DP * (0.76f + 0.24f * ea)) + breathePhase * AURA_BREATHE_DP) * density
        if (outerRadius <= 1f) return

        val auraColor = VaultPullTonePalette.blendRgb(
            pal.mutedColor,
            pal.glowColor,
            0.55f + 0.45f * ea,
        )
        val breatheBoost = if (pal.isLightBackground) 0.05f else 0.08f
        val peakAlpha = (pal.auraPeakAlpha + breatheBoost * breathePhase) * ea
        val midAlpha = (pal.auraMidAlpha + breatheBoost * 0.40f * breathePhase) * ea

        // Backlit radial aura radiating cleanly from behind the elevated surface disc
        val innerStop = ((discRadius * 0.72f) / outerRadius).coerceIn(0.15f, 0.55f)
        val midStop = (innerStop + (1f - innerStop) * 0.48f).coerceIn(innerStop + 0.05f, 0.88f)
        shaderPaint.shader = RadialGradient(
            cx,
            cy,
            outerRadius,
            intArrayOf(
                VaultPullTonePalette.withAlpha(auraColor, peakAlpha),
                VaultPullTonePalette.withAlpha(auraColor, peakAlpha),
                VaultPullTonePalette.withAlpha(auraColor, midAlpha),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, innerStop, midStop, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, outerRadius, shaderPaint)
        shaderPaint.shader = null

        // Unified Surface-filled circular disc matching the search pill material across all themes
        discFillPaint.color = VaultPullTonePalette.withAlpha(pal.surfaceColor, 0.96f * ea)
        canvas.drawCircle(cx, cy, discRadius, discFillPaint)

        // Delicate theme-tinted hairline rim around the disc
        val rimAlpha = (pal.rimAlpha + (if (pal.isLightBackground) 0.06f else 0.08f) * breathePhase) * ea
        discRimPaint.strokeWidth = DISC_RIM_STROKE_DP * density
        discRimPaint.color = VaultPullTonePalette.withAlpha(pal.glowColor, rimAlpha)
        canvas.drawCircle(cx, cy, discRadius, discRimPaint)
    }

    private fun drawTopFeatherMask(
        canvas: Canvas,
        widthPx: Float,
        density: Float,
        pal: VaultPullTonePalette,
    ) {
        val maskH = TOP_MASK_HEIGHT_DP * density
        if (topMaskShader == null || cachedMaskBg != pal.background || cachedMaskHeight != maskH) {
            cachedMaskBg = pal.background
            cachedMaskHeight = maskH
            topMaskShader = LinearGradient(
                0f,
                0f,
                0f,
                maskH,
                VaultPullTonePalette.withAlpha(pal.background, 0.88f),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        maskPaint.shader = topMaskShader
        canvas.drawRect(0f, 0f, widthPx, maskH, maskPaint)
    }

    private fun drawLockGlyph(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        color: Int,
        alphaFraction: Float,
    ) {
        if (alphaFraction <= 0.005f) return
        val tinted = VaultPullTonePalette.withAlpha(color, alphaFraction)
        val u = sizePx / 24f
        val ox = cx - 12f * u
        val oy = cy - 12f * u

        lockStrokePaint.strokeWidth = 1.9f * u
        lockStrokePaint.color = tinted
        lockFillPaint.color = tinted

        // Exact ic_onboarding_vault_lock.xml paths in a 24x24 coordinate space
        shacklePath.reset()
        shacklePath.moveTo(ox + 8.31f * u, oy + 8.54f * u)
        shacklePath.lineTo(ox + 8.31f * u, oy + 6.46f * u)
        shackleArcRect.set(
            ox + 8.31f * u,
            oy + (6.46f - 3.69f) * u,
            ox + (8.31f + 7.38f) * u,
            oy + (6.46f + 3.69f) * u,
        )
        shacklePath.arcTo(shackleArcRect, 180f, 180f, false)
        shacklePath.lineTo(ox + 15.69f * u, oy + 8.54f * u)
        canvas.drawPath(shacklePath, lockStrokePaint)

        lockBodyRect.set(ox + 6.46f * u, oy + 8.31f * u, ox + 17.54f * u, oy + 18.46f * u)
        canvas.drawRoundRect(lockBodyRect, 2.31f * u, 2.31f * u, lockStrokePaint)

        canvas.drawCircle(ox + 12f * u, oy + 12.23f * u, 1.0f * u, lockFillPaint)
        keyholeStemRect.set(ox + 11.58f * u, oy + 13.1f * u, ox + 12.42f * u, oy + 15.42f * u)
        canvas.drawRoundRect(keyholeStemRect, 0.42f * u, 0.42f * u, lockFillPaint)
    }

    private companion object {
        const val LOCK_SIZE_DP = 32f
        const val DISC_RADIUS_DP = 25f
        const val DISC_RIM_STROKE_DP = 1.1f
        const val MIN_CENTER_Y_DP = 48f
        const val START_ABOVE_DP = 16f
        const val EXIT_FADE_LEAD_DP = 4f
        const val EXIT_FADE_SPAN_DP = 24f
        const val SLIT_MIN_WIDTH_DP = 32f
        const val SLIT_MAX_WIDTH_DP = 104f
        const val SLIT_BEAM_DEPTH_DP = 42f
        const val SLIT_THICKNESS_DP = 2f
        const val AURA_RADIUS_DP = 46f
        const val AURA_BREATHE_DP = 4.5f
        const val TOP_MASK_HEIGHT_DP = 12f
    }
}
