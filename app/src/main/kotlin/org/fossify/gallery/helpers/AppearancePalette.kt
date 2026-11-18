package org.fossify.gallery.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getAppIconColors
import org.fossify.commons.helpers.appIconColorStrings
import org.fossify.commons.extensions.isSystemInDarkMode
import org.fossify.gallery.extensions.config

/**
 * The two brand palettes plus the derivation rules used by the Custom mode.
 *
 * `onPrimary` / `onSecondary` are explicit on-color roles: the Dark primary is a light
 * indigo, so white text on it would only have ~2:1 contrast. Surface and Border are
 * derived in Custom mode so a user-picked background never leaves a mismatched card.
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
        val LIGHT = AppearancePalette(
            text = Color.parseColor("#18181B"),
            background = Color.parseColor("#FAFAFA"),
            primary = Color.parseColor("#27272A"),
            secondary = Color.parseColor("#52525B"),
            // Minimalist dark launcher icon matches the neutral Calypso Gallery identity.
            appIcon = Color.parseColor("#09090B"),
            surface = Color.parseColor("#FFFFFF"),
            border = Color.parseColor("#E4E4E7"),
            onPrimary = Color.WHITE,
            onSecondary = Color.WHITE,
        )

        val DARK = AppearancePalette(
            text = Color.parseColor("#FAFAFA"),
            background = Color.parseColor("#09090B"),
            primary = Color.parseColor("#E4E4E7"),
            secondary = Color.parseColor("#A1A1AA"),
            appIcon = Color.parseColor("#09090B"),
            surface = Color.parseColor("#18181B"),
            border = Color.parseColor("#27272A"),
            onPrimary = Color.parseColor("#09090B"),
            onSecondary = Color.parseColor("#09090B"),
        )

        /** Build a coherent palette from the four user-editable Custom colors. */
        fun fromCustom(text: Int, background: Int, primary: Int, secondary: Int, appIcon: Int): AppearancePalette {
            val surface = if (isLight(background)) {
                ColorUtils.blendARGB(background, Color.WHITE, 0.55f)
            } else {
                ColorUtils.blendARGB(background, Color.WHITE, 0.08f)
            }
            val border = ColorUtils.blendARGB(surface, text, 0.16f)
            val effectiveSecondary = if (secondary != 0) secondary else shiftHue(primary, 35f)
            val effectiveIcon = if (appIcon != 0) appIcon else primary
            return AppearancePalette(
                text = text,
                background = background,
                primary = primary,
                secondary = effectiveSecondary,
                appIcon = effectiveIcon,
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
            return 0.2126 * channelLuminance(Color.red(color)) +
                0.7152 * channelLuminance(Color.green(color)) +
                0.0722 * channelLuminance(Color.blue(color))
        }

        private fun isLight(color: Int): Boolean =
            contrastRatio(color, Color.WHITE) < contrastRatio(color, Color.BLACK)

        private fun contrastText(background: Int): Int = if (
            contrastRatio(Color.WHITE, background) >= contrastRatio(DARK.text, background)
        ) {
            Color.WHITE
        } else {
            DARK.text
        }

        private fun shiftHue(color: Int, degrees: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] = (hsv[0] + degrees) % 360f
            return Color.HSVToColor(hsv)
        }
    }
}

/** Resolves the active palette and writes it into Commons' config keys. */
object AppearanceManager {
    fun resolve(context: Context, config: Config): AppearancePalette = when (config.appearanceMode) {
        Config.APPEARANCE_SYSTEM -> if (context.isSystemInDarkMode()) AppearancePalette.DARK else AppearancePalette.LIGHT
        Config.APPEARANCE_LIGHT -> AppearancePalette.LIGHT
        Config.APPEARANCE_DARK -> AppearancePalette.DARK
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
        return candidates.minWithOrNull(
            compareBy { candidate ->
                val dr = Color.red(candidate) - Color.red(target)
                val dg = Color.green(candidate) - Color.green(target)
                val db = Color.blue(candidate) - Color.blue(target)
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
