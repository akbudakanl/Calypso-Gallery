package org.fossify.gallery.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.fossify.commons.extensions.getAppIconColors
import org.fossify.commons.helpers.appIconColorStrings
import org.fossify.commons.extensions.isSystemInDarkMode
import org.fossify.gallery.extensions.config

/**
 * Brand palettes plus the derivation rules used by Custom mode.
 *
 * `onPrimary` / `onSecondary` are explicit on-color roles so bright primaries (such as Dark,
 * Midnight Blue, or Deep Forest) get dark foregrounds instead of unreadable white text. Preset
 * surfaces, borders, and secondary detail hues are hand-tuned per theme family so cards, dividers,
 * subtitles, and icons harmonize with their palette.
 */
data class AppearancePalette(
    val text: Int,
    val background: Int,
    val primary: Int,
    val secondary: Int,
    val appIcon: Int,
    val surface: Int,
    val border: Int,
    val onPrimary: Int,
    val onSecondary: Int,
) {
    companion object {
        private const val PURE_WHITE = 0xFFFFFFFF.toInt()
        private const val PURE_BLACK = 0xFF000000.toInt()

        const val LIGHT_TEXT = 0xFF18181B.toInt()
        const val LIGHT_BACKGROUND = 0xFFFAFAFA.toInt()
        const val LIGHT_PRIMARY = 0xFF27272A.toInt()

        const val DARK_TEXT = 0xFFFAFAFA.toInt()
        const val DARK_BACKGROUND = 0xFF000000.toInt()
        const val DARK_PRIMARY = 0xFFE4E4E7.toInt()

        const val NAVY_TEXT = 0xFFEFF6FF.toInt()
        const val NAVY_BACKGROUND = 0xFF0B1220.toInt()
        const val NAVY_PRIMARY = 0xFF93C5FD.toInt()

        const val PAPER_TEXT = 0xFF29231E.toInt()
        const val PAPER_BACKGROUND = 0xFFF4EFE6.toInt()
        const val PAPER_PRIMARY = 0xFF7C5438.toInt()

        const val FOREST_TEXT = 0xFFECFDF5.toInt()
        const val FOREST_BACKGROUND = 0xFF0C1713.toInt()
        const val FOREST_PRIMARY = 0xFF6EE7B7.toInt()

        const val VIOLET_TEXT = 0xFFF5F3FF.toInt()
        const val VIOLET_BACKGROUND = 0xFF0E0A18.toInt()
        const val VIOLET_PRIMARY = 0xFFA78BFA.toInt()

        const val CORAL_TEXT = 0xFF2B1A18.toInt()
        const val CORAL_BACKGROUND = 0xFFFFF7F5.toInt()
        const val CORAL_PRIMARY = 0xFFC44047.toInt()

        val LIGHT = AppearancePalette(
            text = LIGHT_TEXT,
            background = LIGHT_BACKGROUND,
            primary = LIGHT_PRIMARY,
            secondary = 0xFF52525B.toInt(),
            // Minimalist dark launcher icon matches the neutral Calypso Gallery identity.
            appIcon = 0xFF09090B.toInt(),
            surface = PURE_WHITE,
            border = 0xFFE4E4E7.toInt(),
            onPrimary = PURE_WHITE,
            onSecondary = PURE_WHITE,
        )

        val DARK = AppearancePalette(
            text = DARK_TEXT,
            background = DARK_BACKGROUND,
            primary = DARK_PRIMARY,
            secondary = 0xFFA1A1AA.toInt(),
            appIcon = PURE_BLACK,
            surface = 0xFF18181B.toInt(),
            border = 0xFF27272A.toInt(),
            onPrimary = PURE_BLACK,
            onSecondary = PURE_BLACK,
        )

        val NAVY = AppearancePalette(
            text = NAVY_TEXT,
            background = NAVY_BACKGROUND,
            primary = NAVY_PRIMARY,
            secondary = 0xFF8BA7CC.toInt(),
            appIcon = DARK.appIcon,
            surface = 0xFF131D31.toInt(),
            border = 0xFF243451.toInt(),
            onPrimary = PURE_BLACK,
            onSecondary = PURE_BLACK,
        )

        val PAPER = AppearancePalette(
            text = PAPER_TEXT,
            background = PAPER_BACKGROUND,
            primary = PAPER_PRIMARY,
            secondary = 0xFF6E5B4B.toInt(),
            appIcon = LIGHT.appIcon,
            surface = 0xFFFAF7F2.toInt(),
            border = 0xFFDDD5C9.toInt(),
            onPrimary = PURE_WHITE,
            onSecondary = PURE_WHITE,
        )

        val FOREST = AppearancePalette(
            text = FOREST_TEXT,
            background = FOREST_BACKGROUND,
            primary = FOREST_PRIMARY,
            secondary = 0xFF86B5A1.toInt(),
            appIcon = DARK.appIcon,
            surface = 0xFF13241E.toInt(),
            border = 0xFF243D34.toInt(),
            onPrimary = PURE_BLACK,
            onSecondary = PURE_BLACK,
        )

        val VIOLET = AppearancePalette(
            text = VIOLET_TEXT,
            background = VIOLET_BACKGROUND,
            primary = VIOLET_PRIMARY,
            secondary = 0xFFA697CC.toInt(),
            appIcon = DARK.appIcon,
            surface = 0xFF1A132B.toInt(),
            border = 0xFF2F234B.toInt(),
            onPrimary = PURE_BLACK,
            onSecondary = PURE_BLACK,
        )

        val CORAL = AppearancePalette(
            text = CORAL_TEXT,
            background = CORAL_BACKGROUND,
            primary = CORAL_PRIMARY,
            secondary = 0xFF7D5350.toInt(),
            appIcon = LIGHT.appIcon,
            surface = PURE_WHITE,
            border = 0xFFF1DDD8.toInt(),
            onPrimary = PURE_WHITE,
            onSecondary = PURE_WHITE,
        )

        fun isLightBackground(color: Int): Boolean = isLight(color)

        /** Build a coherent palette from the four user-editable Custom colors. */
        fun fromCustom(text: Int, background: Int, primary: Int, secondary: Int, appIcon: Int): AppearancePalette {
            // Seeding Custom from a built-in mode copies the preset's primary colors into the raw
            // custom fields. Deriving surface/border/secondary from those values would NOT reproduce
            // the hand-tuned preset, so return the matching preset verbatim. Any real edit falls
            // through to the derived palette below and is reported as an unsaved change.
            val builtInPresets = listOf(LIGHT, DARK, NAVY, PAPER, FOREST, VIOLET, CORAL)
            for (preset in builtInPresets) {
                if ((secondary == 0 || secondary == preset.secondary) &&
                    text == preset.text &&
                    background == preset.background &&
                    primary == preset.primary
                ) {
                    return preset
                }
            }

            val surface = if (isLight(background)) {
                blendArgb(background, PURE_WHITE, 0.55f)
            } else {
                blendArgb(background, PURE_WHITE, 0.08f)
            }
            val border = blendArgb(surface, text, 0.16f)
            val effectiveSecondary = if (secondary != 0) {
                secondary
            } else {
                deriveSecondary(text, background, primary)
            }
            return AppearancePalette(
                text = text,
                background = background,
                primary = primary,
                secondary = effectiveSecondary,
                appIcon = DARK.appIcon,
                surface = surface,
                border = border,
                onPrimary = contrastText(primary),
                onSecondary = contrastText(effectiveSecondary),
            )
        }

        /** WCAG 2.x contrast ratio in the range 1.0 .. 21.0. */
        fun contrastRatio(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            val hi = kotlin.math.max(la, lb)
            val lo = kotlin.math.min(la, lb)
            return (hi + 0.05) / (lo + 0.05)
        }

        private fun luminance(color: Int): Double {
            val channelLuminance = { value: Int ->
                val c = value / 255.0
                if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            val red = (color ushr 16) and 0xFF
            val green = (color ushr 8) and 0xFF
            val blue = color and 0xFF
            return 0.2126 * channelLuminance(red) +
                0.7152 * channelLuminance(green) +
                0.0722 * channelLuminance(blue)
        }

        private fun isLight(color: Int): Boolean =
            contrastRatio(color, PURE_WHITE) < contrastRatio(color, PURE_BLACK)

        private fun contrastText(background: Int): Int = if (
            contrastRatio(PURE_WHITE, background) >= contrastRatio(PURE_BLACK, background)
        ) {
            PURE_WHITE
        } else {
            PURE_BLACK
        }

        private fun deriveSecondary(text: Int, background: Int, primary: Int): Int {
            val mutedText = blendArgb(text, background, 0.34f)
            return blendArgb(mutedText, primary, 0.25f)
        }

        private fun blendArgb(from: Int, to: Int, ratio: Float): Int {
            val inverse = 1f - ratio
            val a = (((from ushr 24) and 0xFF) * inverse + ((to ushr 24) and 0xFF) * ratio).toInt()
            val r = (((from ushr 16) and 0xFF) * inverse + ((to ushr 16) and 0xFF) * ratio).toInt()
            val g = (((from ushr 8) and 0xFF) * inverse + ((to ushr 8) and 0xFF) * ratio).toInt()
            val b = ((from and 0xFF) * inverse + (to and 0xFF) * ratio).toInt()
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

/** Resolves the active palette and writes it into Commons' config keys. */
object AppearanceManager {
    fun resolveSystemMatchedMode(isSystemDark: Boolean): Int =
        if (isSystemDark) Config.APPEARANCE_DARK else Config.APPEARANCE_LIGHT

    fun resolve(context: Context, config: Config): AppearancePalette = when (config.appearanceMode) {
        Config.APPEARANCE_SYSTEM -> when (resolveSystemMatchedMode(context.isSystemInDarkMode())) {
            Config.APPEARANCE_DARK -> AppearancePalette.DARK
            else -> AppearancePalette.LIGHT
        }
        Config.APPEARANCE_LIGHT -> AppearancePalette.LIGHT
        Config.APPEARANCE_DARK -> AppearancePalette.DARK
        Config.APPEARANCE_NAVY -> AppearancePalette.NAVY
        Config.APPEARANCE_PAPER -> AppearancePalette.PAPER
        Config.APPEARANCE_FOREST -> AppearancePalette.FOREST
        Config.APPEARANCE_VIOLET -> AppearancePalette.VIOLET
        Config.APPEARANCE_CORAL -> AppearancePalette.CORAL
        Config.APPEARANCE_CUSTOM -> AppearancePalette.fromCustom(
            text = config.customTextColor,
            background = config.customBackgroundColor,
            primary = config.customPrimaryColor,
            secondary = config.customAccentColor,
            appIcon = config.customAppIconColor,
        )
        else -> AppearancePalette.LIGHT
    }

    /**
     * Applies the palette to the fields that Commons' `getProperTextColor()`,
     * `getProperBackgroundColor()` and `getProperPrimaryColor()` read. Global theming is
     * disabled so a Fossify-wide theme cannot silently override this fork's palette.
     */
    fun apply(context: Context, config: Config): AppearancePalette {
        val palette = resolve(context, config)
        config.isGlobalThemeEnabled = false
        config.isSystemThemeEnabled = false
        config.textColor = palette.text
        config.backgroundColor = palette.background
        config.primaryColor = palette.primary
        config.accentColor = palette.secondary
        // Commons can only switch launcher icons between its predefined md_* colors. Using a
        // brand color directly leaves every alias disabled and makes checkAppIconColor() throw,
        // so select the closest supported shade instead.
        config.appIconColor = nearestSupportedIconColor(context, palette.appIcon)
        return palette
    }

    private fun nearestSupportedIconColor(context: Context, target: Int): Int {
        val candidates = context.getAppIconColors()
        if (candidates.isEmpty()) {
            return target
        }
        val targetRed = (target ushr 16) and 0xFF
        val targetGreen = (target ushr 8) and 0xFF
        val targetBlue = target and 0xFF
        return candidates.minWithOrNull(
            compareBy { candidate ->
                val dr = ((candidate ushr 16) and 0xFF) - targetRed
                val dg = ((candidate ushr 8) and 0xFF) - targetGreen
                val db = (candidate and 0xFF) - targetBlue
                dr * dr + dg * dg + db * db
            },
        ) ?: target
    }

    /**
     * Switches the launcher icon alias using the real runtime package name. Commons' own
     * checkAppIconColor() strips ".debug" from the application id and therefore addresses an
     * alias in a package that is not installed in debug builds.
     */
    fun applyLauncherIcon(context: Context, color: Int) {
        val colors = context.getAppIconColors()
        val names = appIconColorStrings
        if (colors.size != names.size || colors.isEmpty()) {
            return
        }
        val selected = colors.indexOf(nearestSupportedIconColor(context, color))
        if (selected < 0) {
            return
        }
        val packageName = context.packageName
        // The main SplashActivity is not exported and has no launcher filter; only aliases do.
        for (index in colors.indices) {
            val alias = "$packageName.activities.SplashActivity.${names[index]}"
            val state = if (index == selected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(packageName, alias),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            } catch (_: Exception) {
                // A launcher may reject alias changes; the app keeps working with the current icon.
            }
        }
    }
}
