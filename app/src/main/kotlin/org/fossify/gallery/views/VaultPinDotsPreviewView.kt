package org.fossify.gallery.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.helpers.AppearancePalette
import kotlin.math.sin

/**
 * Side-by-side live comparison view shown in the "Alternative PIN appearance" notice dialog
 * (ADR 0008).
 *
 * Both mini vault panels enter a 6-digit PIN (`2 - 5 - 8 - 0 - 4 - 9`), press the confirm key `✓`,
 * and play the shutter unlock animation:
 *  - Left card (Default / Off): only entered digits appear as bullets (`•`); on unlock, the light
 *    sweep line travels across the open corridor between the lock emblem and the bullets.
 *  - Right card (Per-digit dots / On): 6 empty rings are drawn inside a rounded rectangular box
 *    from the very start; on unlock, the dots pulse sequentially without a crossing sweep line.
 */
class VaultPinDotsPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var palette: AppearancePalette = AppearancePalette.DARK
    private var cardBgColor = Color.DKGRAY
    private var cardBorderColor = Color.GRAY
    private var surfaceColor = Color.BLACK
    private var haloStrokeColor = Color.GRAY
    private var glowFillColor = Color.GRAY

    private val cardRect = RectF()
    private val cardClipPath = Path()
    private val coreRect = RectF()
    private val slotBoxRect = RectF()
    private val keyRect = RectF()
    private val lockBodyRect = RectF()
    private val lockStemRect = RectF()
    private val lockArcRect = RectF()
    private val lockShacklePath = Path()
    private val sweepRect = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var animator: ValueAnimator? = null
    private var elapsedMs: Float = 0f

    private val shackleOvershoot = OvershootInterpolator(1.6f)
    private val decelerate = DecelerateInterpolator()

    fun applyPalette(newPalette: AppearancePalette) {
        palette = newPalette
        val isLight = AppearancePalette.isLightBackground(newPalette.background)
        surfaceColor = newPalette.surface
        cardBgColor = ColorUtils.blendARGB(
            newPalette.surface,
            newPalette.text,
            if (isLight) 0.045f else 0.06f,
        )
        cardBorderColor = newPalette.border
        haloStrokeColor = ColorUtils.setAlphaComponent(newPalette.text, 44)
        glowFillColor = ColorUtils.setAlphaComponent(newPalette.primary, 40)
        invalidate()
    }

    fun start() {
        stop()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // Static mid-entry frame (2 digits entered) when system animations are disabled
            elapsedMs = 1550f
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(0f, CYCLE_DURATION_MS).apply {
            duration = CYCLE_DURATION_MS.toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = null
            addUpdateListener {
                elapsedMs = it.animatedValue as Float
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
        val scale = minOf(
            w / (BASELINE_WIDTH_DP * density),
            h / (BASELINE_HEIGHT_DP * density),
        ).coerceAtLeast(1f)
        fun dp(v: Float) = v * density * scale

        val gap = dp(10f)
        val cardW = (w - gap) / 2f
        val cardH = h

        // Draw Left Card (Default / Off) and Right Card (Enabled / On)
        drawMiniVaultCard(
            canvas = canvas,
            left = 0f,
            top = 0f,
            cardW = cardW,
            cardH = cardH,
            isPinDotsMode = false,
            dp = ::dp,
        )
        drawMiniVaultCard(
            canvas = canvas,
            left = cardW + gap,
            top = 0f,
            cardW = cardW,
            cardH = cardH,
            isPinDotsMode = true,
            dp = ::dp,
        )
    }

    private fun drawMiniVaultCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        cardW: Float,
        cardH: Float,
        isPinDotsMode: Boolean,
        dp: (Float) -> Float,
    ) {
        val strokePx = dp(1f).coerceAtLeast(1f)
        val halfStroke = strokePx / 2f
        val cardRadius = dp(15f)

        cardRect.set(left + halfStroke, top + halfStroke, left + cardW - halfStroke, top + cardH - halfStroke)
        fillPaint.color = cardBgColor
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, fillPaint)

        cardClipPath.reset()
        cardClipPath.addRoundRect(cardRect, cardRadius, cardRadius, Path.Direction.CW)

        // Compute animation timeline state from elapsedMs
        val enteredCount = when {
            elapsedMs < START_TYPING_MS -> 0
            else -> (((elapsedMs - START_TYPING_MS) / KEY_INTERVAL_MS).toInt() + 1).coerceIn(0, 6)
        }

        // Determine currently pressed key (digit 0..5 or '✓' at index 6)
        var pressedKeyLabel: String? = null
        for (i in PIN_SEQ.indices) {
            val pressStart = START_TYPING_MS + i * KEY_INTERVAL_MS
            if (elapsedMs in pressStart..(pressStart + KEY_PRESS_DURATION_MS)) {
                pressedKeyLabel = PIN_SEQ[i]
                break
            }
        }
        if (elapsedMs in OK_PRESS_MS..(OK_PRESS_MS + KEY_PRESS_DURATION_MS)) {
            pressedKeyLabel = "✓"
        }

        // Unlock phase (starts at UNLOCK_START_MS)
        val unlockElapsed = (elapsedMs - UNLOCK_START_MS).coerceAtLeast(0f)
        val isUnlocking = elapsedMs >= UNLOCK_START_MS

        val openProgress = if (isUnlocking) {
            val t = (unlockElapsed / 420f).coerceIn(0f, 1f)
            shackleOvershoot.getInterpolation(t)
        } else {
            0f
        }

        val lockGlow = if (isUnlocking) {
            val t = (unlockElapsed / 700f).coerceIn(0f, 1f)
            decelerate.getInterpolation(t)
        } else {
            0f
        }

        // Panel exit upward (starts at UNLOCK_START_MS + 780ms, duration 480ms)
        val panelExitT = if (elapsedMs >= PANEL_EXIT_START_MS) {
            decelerate.getInterpolation(((elapsedMs - PANEL_EXIT_START_MS) / PANEL_EXIT_DURATION_MS).coerceIn(0f, 1f))
        } else {
            0f
        }
        val panelAlpha = (1f - panelExitT).coerceIn(0f, 1f)
        val panelOffsetY = -dp(18f) * panelExitT

        val saveClip = canvas.save()
        canvas.clipPath(cardClipPath)
        canvas.translate(0f, panelOffsetY)

        val centerX = left + cardW / 2f

        // 1. Framed Lock Emblem (halo + rounded core + shutter lock glyph)
        val emblemCenterY = top + dp(34f)
        val haloPhase = (elapsedMs % 3200f) / 3200f
        val haloScale = 0.88f + 0.34f * haloPhase
        val haloAlphaFactor = sin(haloPhase * Math.PI).toFloat().coerceIn(0f, 1f) * 0.7f * panelAlpha
        strokePaint.strokeWidth = strokePx
        strokePaint.color = ColorUtils.setAlphaComponent(
            palette.text,
            (44 * haloAlphaFactor).toInt().coerceIn(0, 255),
        )
        canvas.drawCircle(centerX, emblemCenterY, dp(22f) * haloScale, strokePaint)

        val coreSize = dp(36f)
        val coreRadius = dp(11.5f)
        coreRect.set(
            centerX - coreSize / 2f,
            emblemCenterY - coreSize / 2f,
            centerX + coreSize / 2f,
            emblemCenterY + coreSize / 2f,
        )
        fillPaint.color = applyAlpha(surfaceColor, panelAlpha)
        canvas.drawRoundRect(coreRect, coreRadius, coreRadius, fillPaint)
        strokePaint.strokeWidth = strokePx
        strokePaint.color = applyAlpha(cardBorderColor, panelAlpha)
        canvas.drawRoundRect(coreRect, coreRadius, coreRadius, strokePaint)

        drawShutterLock(
            canvas = canvas,
            cx = centerX,
            cy = emblemCenterY,
            sizePx = dp(22f),
            openProgress = openProgress,
            glow = lockGlow,
            alpha = panelAlpha,
        )

        // 2. Open gap between Lock Emblem and PIN characters (where the sweep line travels in Left card)
        val sweepY = emblemCenterY + coreSize / 2f + dp(7.5f)
        if (!isPinDotsMode && isUnlocking) {
            val sweepDelay = 300f
            val sweepDuration = 520f
            if (unlockElapsed in sweepDelay..(sweepDelay + sweepDuration)) {
                val st = ((unlockElapsed - sweepDelay) / sweepDuration).coerceIn(0f, 1f)
                val eased = decelerate.getInterpolation(st)
                val sweepW = dp(42f)
                val sweepH = dp(3f)
                val travel = cardW + sweepW
                val sweepLeft = left - sweepW + travel * eased
                val sweepAlpha = (sin(st * Math.PI).toFloat() * panelAlpha).coerceIn(0f, 1f)
                val sweepColor = applyAlpha(palette.primary, sweepAlpha)
                sweepPaint.shader = LinearGradient(
                    sweepLeft,
                    sweepY,
                    sweepLeft + sweepW,
                    sweepY,
                    intArrayOf(Color.TRANSPARENT, sweepColor, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                sweepRect.set(sweepLeft, sweepY - sweepH / 2f, sweepLeft + sweepW, sweepY + sweepH / 2f)
                canvas.drawRoundRect(sweepRect, sweepH / 2f, sweepH / 2f, sweepPaint)
                sweepPaint.shader = null
            }
        }

        // 3. PIN Indicator Corridor (below the open sweep gap)
        val corridorCenterY = sweepY + dp(20f)
        val dotStep = dp(13.5f)
        val dotRadius = dp(3.3f)
        val totalDotsW = dotStep * 5f
        val firstDotX = centerX - totalDotsW / 2f

        if (!isPinDotsMode) {
            // Left card: borderless legacy bullets (only entered digits appear)
            fillPaint.color = applyAlpha(palette.text, panelAlpha)
            for (i in 0 until enteredCount) {
                val dx = firstDotX + i * dotStep
                val entryStart = START_TYPING_MS + i * KEY_INTERVAL_MS
                val popT = ((elapsedMs - entryStart) / 140f).coerceIn(0f, 1f)
                val r = dotRadius * (0.4f + 0.6f * popT)
                canvas.drawCircle(dx, corridorCenterY, r, fillPaint)
            }
        } else {
            // Right card: rounded rectangular box with 6 fixed ring/dot slots
            val boxMarginH = dp(10f)
            val boxH = dp(28f)
            val boxRadius = dp(8.5f)
            slotBoxRect.set(
                left + boxMarginH,
                corridorCenterY - boxH / 2f,
                left + cardW - boxMarginH,
                corridorCenterY + boxH / 2f,
            )
            fillPaint.color = applyAlpha(surfaceColor, panelAlpha)
            canvas.drawRoundRect(slotBoxRect, boxRadius, boxRadius, fillPaint)
            strokePaint.strokeWidth = strokePx
            strokePaint.color = applyAlpha(cardBorderColor, panelAlpha)
            canvas.drawRoundRect(slotBoxRect, boxRadius, boxRadius, strokePaint)

            val ringStroke = dp(1.35f)
            for (i in 0 until 6) {
                val dx = firstDotX + i * dotStep
                var pulseFactor = 0f
                if (isUnlocking) {
                    val pulseStart = i * 70f
                    val pulseDuration = 340f
                    if (unlockElapsed in pulseStart..(pulseStart + pulseDuration)) {
                        val pt = (unlockElapsed - pulseStart) / pulseDuration
                        pulseFactor = sin(pt * Math.PI).toFloat().coerceIn(0f, 1f)
                    }
                }
                val r = dotRadius * (1f + 0.52f * pulseFactor)
                if (i < enteredCount) {
                    fillPaint.color = applyAlpha(palette.text, panelAlpha)
                    canvas.drawCircle(dx, corridorCenterY, r, fillPaint)
                } else {
                    strokePaint.strokeWidth = ringStroke
                    strokePaint.color = applyAlpha(palette.secondary, panelAlpha)
                    canvas.drawCircle(dx, corridorCenterY, r - ringStroke / 2f, strokePaint)
                }
            }
        }

        // 4. Mini 3x4 Keypad
        val keypadTop = corridorCenterY + dp(23f)
        val keyW = dp(31f)
        val keyH = dp(21f)
        val keyGap = dp(4f)
        val gridW = keyW * 3f + keyGap * 2f
        val gridLeft = centerX - gridW / 2f
        val keyRadius = dp(6f)
        keyTextPaint.textSize = dp(9.5f)
        val textFontMetrics = keyTextPaint.fontMetrics
        val textBaselineOffset = -(textFontMetrics.ascent + textFontMetrics.descent) / 2f

        for (row in 0 until 4) {
            for (col in 0 until 3) {
                val label = KEYPAD_LABELS[row * 3 + col]
                val isPressed = label == pressedKeyLabel
                val scale = if (isPressed) 0.90f else 1f

                val kx = gridLeft + col * (keyW + keyGap) + keyW / 2f
                val ky = keypadTop + row * (keyH + keyGap) + keyH / 2f
                val kw = keyW * scale
                val kh = keyH * scale
                keyRect.set(kx - kw / 2f, ky - kh / 2f, kx + kw / 2f, ky + kh / 2f)

                when (label) {
                    "⌫" -> {
                        keyTextPaint.isFakeBoldText = false
                        keyTextPaint.color = applyAlpha(palette.secondary, panelAlpha)
                        canvas.drawText(label, kx, ky + textBaselineOffset, keyTextPaint)
                    }
                    "✓" -> {
                        fillPaint.color = applyAlpha(palette.primary, panelAlpha)
                        canvas.drawRoundRect(keyRect, keyRadius, keyRadius, fillPaint)
                        keyTextPaint.isFakeBoldText = true
                        keyTextPaint.color = applyAlpha(palette.onPrimary, panelAlpha)
                        canvas.drawText(label, kx, ky + textBaselineOffset, keyTextPaint)
                    }
                    else -> {
                        val keyBg = if (isPressed) {
                            ColorUtils.blendARGB(surfaceColor, palette.primary, 0.22f)
                        } else {
                            surfaceColor
                        }
                        fillPaint.color = applyAlpha(keyBg, panelAlpha)
                        canvas.drawRoundRect(keyRect, keyRadius, keyRadius, fillPaint)
                        strokePaint.strokeWidth = strokePx
                        strokePaint.color = applyAlpha(cardBorderColor, panelAlpha)
                        canvas.drawRoundRect(keyRect, keyRadius, keyRadius, strokePaint)

                        keyTextPaint.isFakeBoldText = false
                        keyTextPaint.color = applyAlpha(palette.text, panelAlpha)
                        canvas.drawText(label, kx, ky + textBaselineOffset, keyTextPaint)
                    }
                }
            }
        }

        canvas.restoreToCount(saveClip)

        // Outer border of the mini vault card (active border on the right card)
        strokePaint.strokeWidth = strokePx
        strokePaint.color = if (isPinDotsMode) palette.primary else cardBorderColor
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, strokePaint)
    }

    private fun drawShutterLock(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        openProgress: Float,
        glow: Float,
        alpha: Float,
    ) {
        val u = sizePx / 104f
        val ox = cx - 52f * u
        val oy = cy - 46f * u

        if (glow > 0f) {
            fillPaint.color = applyAlpha(glowFillColor, alpha)
            canvas.drawCircle(ox + 52f * u, oy + 46f * u, (26f + glow * 12f) * u, fillPaint)
        }

        val strokeColor = applyAlpha(palette.text, alpha)
        val bodyFill = applyAlpha(surfaceColor, alpha)

        // Shackle pivoting around lower-left joint (36, 37)
        canvas.save()
        canvas.translate(ox + 36f * u, oy + 37f * u)
        canvas.rotate(-26f * openProgress)
        strokePaint.strokeWidth = 5.0f * u
        strokePaint.color = strokeColor
        lockShacklePath.reset()
        lockShacklePath.moveTo(0f, 0f)
        lockShacklePath.lineTo(0f, -9f * u)
        lockArcRect.set(0f, -25f * u, 32f * u, 7f * u)
        lockShacklePath.arcTo(lockArcRect, 180f, 180f, false)
        lockShacklePath.lineTo(32f * u, 0f)
        canvas.drawPath(lockShacklePath, strokePaint)
        canvas.restore()

        // Lock body + keyhole
        strokePaint.strokeWidth = 4.6f * u
        lockBodyRect.set(ox + 28f * u, oy + 36f * u, ox + 76f * u, oy + 80f * u)
        fillPaint.color = bodyFill
        canvas.drawRoundRect(lockBodyRect, 10f * u, 10f * u, fillPaint)
        strokePaint.color = strokeColor
        canvas.drawRoundRect(lockBodyRect, 10f * u, 10f * u, strokePaint)

        fillPaint.color = strokeColor
        canvas.drawCircle(ox + 52f * u, oy + 53f * u, 3.8f * u, fillPaint)
        lockStemRect.set(ox + 50.5f * u, oy + 54.5f * u, ox + 53.5f * u, oy + 63.5f * u)
        canvas.drawRoundRect(lockStemRect, 1.4f * u, 1.4f * u, fillPaint)
    }

    private fun applyAlpha(color: Int, factor: Float): Int {
        val baseAlpha = Color.alpha(color)
        val nextAlpha = (baseAlpha * factor).toInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(color, nextAlpha)
    }

    private companion object {
        const val BASELINE_WIDTH_DP = 286f
        const val BASELINE_HEIGHT_DP = 236f
        val PIN_SEQ = arrayOf("2", "5", "8", "0", "4", "9")
        val KEYPAD_LABELS = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")

        const val START_TYPING_MS = 850f
        const val KEY_INTERVAL_MS = 310f
        const val KEY_PRESS_DURATION_MS = 150f
        const val OK_PRESS_MS = START_TYPING_MS + 6 * KEY_INTERVAL_MS + 260f
        const val UNLOCK_START_MS = OK_PRESS_MS + 160f
        const val PANEL_EXIT_START_MS = UNLOCK_START_MS + 780f
        const val PANEL_EXIT_DURATION_MS = 480f
        const val CYCLE_DURATION_MS = UNLOCK_START_MS + 1750f
    }
}
