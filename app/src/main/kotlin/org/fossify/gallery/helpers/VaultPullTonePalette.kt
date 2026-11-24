package org.fossify.gallery.helpers

/**
 * Constant-hue tonal colors and perceptual glow parameters for the unified Surface Disc L1
 * pull-to-vault indicator (AD-1 sequential light handoff).
 *
 * Keeps the active theme's `primary` hue constant throughout the gesture while shifting smoothly
 * from a calmer muted tone while descending to the crisp armed tone when ready.
 *
 * The settled lock sits inside an elevated circular disc filled with `surfaceColor` (matching the
 * pinned search pill material) and framed by a backlit radial aura in `glowColor`, giving every
 * theme — including monochrome Light mode and arbitrary Custom themes — identical clarity and
 * depth without special-case exceptions.
 */
data class VaultPullTonePalette(
    val background: Int,
    val surfaceColor: Int,
    val mutedColor: Int,
    val armedColor: Int,
    val glowColor: Int,
    val isLightBackground: Boolean,
    val auraPeakAlpha: Float,
    val auraMidAlpha: Float,
    val rimAlpha: Float,
    val slitPeakAlpha: Float,
    val slitMidAlpha: Float,
) {
    companion object {
        private const val OPAQUE_MASK = 0xFF000000.toInt()
        private const val RGB_MASK = 0x00FFFFFF
        private const val PURE_WHITE = 0xFFFFFFFF.toInt()
        private const val PURE_BLACK = 0xFF000000.toInt()

        private const val MIN_ARMED_CONTRAST = 2.6
        private const val TARGET_SAFEGUARD_CONTRAST = 3.2
        private const val MIN_MUTED_CONTRAST = 1.85

        fun resolve(palette: AppearancePalette): VaultPullTonePalette =
            resolve(
                background = palette.background,
                surface = palette.surface,
                primary = palette.primary,
                text = palette.text,
            )

        fun resolve(background: Int, primary: Int, text: Int, surface: Int? = null): VaultPullTonePalette {
            val bg = opaque(background)
            val rawPrimary = opaque(primary)
            val rawText = opaque(text)
            val isLightBg = AppearancePalette.isLightBackground(bg)
            val resolvedSurface = if (surface != null) {
                opaque(surface)
            } else if (isLightBg) {
                blendRgb(bg, PURE_WHITE, 0.55f)
            } else {
                blendRgb(bg, PURE_WHITE, 0.08f)
            }

            val armed = resolveReadableArmedColor(bg, rawPrimary, rawText, isLightBg)
            val muted = resolveMutedSameHueColor(bg, armed, isLightBg)
            val glow = resolveGlowColor(bg, armed, isLightBg)

            // Deeper/saturated custom primaries on dark backgrounds (or lighter primaries on light
            // backgrounds) have lower contrast against the canvas and need stronger alpha to match
            // the perceived brilliance of bright presets like Dark, Navy, Forest, and Violet.
            val glowContrast = AppearancePalette.contrastRatio(glow, bg)
            val lowContrastBoost = if (isLightBg) {
                ((7.5 - glowContrast) / 4.5).toFloat().coerceIn(0f, 1f)
            } else {
                ((9.0 - glowContrast) / 5.5).toFloat().coerceIn(0f, 1f)
            }

            val auraPeakAlpha = if (isLightBg) {
                0.26f + 0.09f * lowContrastBoost
            } else {
                0.40f + 0.14f * lowContrastBoost
            }
            val auraMidAlpha = if (isLightBg) {
                0.12f + 0.04f * lowContrastBoost
            } else {
                0.18f + 0.07f * lowContrastBoost
            }
            val rimAlpha = if (isLightBg) {
                0.16f + 0.06f * lowContrastBoost
            } else {
                0.28f + 0.08f * lowContrastBoost
            }
            val slitPeakAlpha = if (isLightBg) {
                0.32f + 0.10f * lowContrastBoost
            } else {
                0.40f + 0.12f * lowContrastBoost
            }
            val slitMidAlpha = if (isLightBg) {
                0.13f + 0.05f * lowContrastBoost
            } else {
                0.16f + 0.06f * lowContrastBoost
            }

            return VaultPullTonePalette(
                background = bg,
                surfaceColor = resolvedSurface,
                mutedColor = muted,
                armedColor = armed,
                glowColor = glow,
                isLightBackground = isLightBg,
                auraPeakAlpha = auraPeakAlpha,
                auraMidAlpha = auraMidAlpha,
                rimAlpha = rimAlpha,
                slitPeakAlpha = slitPeakAlpha,
                slitMidAlpha = slitMidAlpha,
            )
        }

        fun blendRgb(from: Int, to: Int, fraction: Float): Int {
            val t = fraction.coerceIn(0f, 1f)
            val inv = 1f - t
            val r = (((from ushr 16) and 0xFF) * inv + ((to ushr 16) and 0xFF) * t).toInt()
            val g = (((from ushr 8) and 0xFF) * inv + ((to ushr 8) and 0xFF) * t).toInt()
            val b = ((from and 0xFF) * inv + (to and 0xFF) * t).toInt()
            return OPAQUE_MASK or (r shl 16) or (g shl 8) or b
        }

        fun withAlpha(color: Int, alphaFraction: Float): Int {
            val alpha = (alphaFraction.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            return (alpha shl 24) or (color and RGB_MASK)
        }

        private fun resolveReadableArmedColor(
            bg: Int,
            rawPrimary: Int,
            rawText: Int,
            isLightBg: Boolean,
        ): Int {
            if (AppearancePalette.contrastRatio(rawPrimary, bg) >= MIN_ARMED_CONTRAST) {
                return rawPrimary
            }

            val pole = if (isLightBg) PURE_BLACK else PURE_WHITE
            val steps = floatArrayOf(0.18f, 0.34f, 0.50f, 0.66f, 0.80f)
            for (step in steps) {
                val lifted = blendRgb(rawPrimary, pole, step)
                if (AppearancePalette.contrastRatio(lifted, bg) >= TARGET_SAFEGUARD_CONTRAST) {
                    return lifted
                }
            }

            return if (AppearancePalette.contrastRatio(rawText, bg) >= TARGET_SAFEGUARD_CONTRAST) {
                rawText
            } else {
                pole
            }
        }

        private fun resolveMutedSameHueColor(
            bg: Int,
            armed: Int,
            isLightBg: Boolean,
        ): Int {
            val preferredBlend = if (isLightBg) 0.48f else 0.44f
            val candidates = floatArrayOf(preferredBlend, 0.34f, 0.24f, 0.16f)
            for (blend in candidates) {
                val candidate = blendRgb(armed, bg, blend)
                if (AppearancePalette.contrastRatio(candidate, bg) >= MIN_MUTED_CONTRAST) {
                    return candidate
                }
            }
            return blendRgb(armed, bg, 0.12f)
        }

        private fun resolveGlowColor(bg: Int, armed: Int, isLightBg: Boolean): Int {
            if (isLightBg) return armed
            val contrast = AppearancePalette.contrastRatio(armed, bg)
            if (contrast >= 8.5) return armed
            val lift = (((8.5 - contrast) / 5.5) * 0.26).toFloat().coerceIn(0f, 0.26f)
            return blendRgb(armed, PURE_WHITE, lift)
        }

        private fun opaque(color: Int): Int = OPAQUE_MASK or (color and RGB_MASK)
    }
}
