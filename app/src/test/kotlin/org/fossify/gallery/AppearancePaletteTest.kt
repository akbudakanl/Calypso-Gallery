package org.fossify.gallery

import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppearancePaletteTest {

    @Test
    fun resolveSystemMatchedModeMatchesSystemDarkFlag() {
        assertEquals(Config.APPEARANCE_DARK, AppearanceManager.resolveSystemMatchedMode(true))
        assertEquals(Config.APPEARANCE_LIGHT, AppearanceManager.resolveSystemMatchedMode(false))
    }

    @Test
    fun navyPaletteKeepsDetailsInBlueFamilyWithAccessibleContrast() {
        val navy = AppearancePalette.NAVY
        assertHueInRange("NAVY.secondary", navy.secondary, 200f, 230f)
        assertHueInRange("NAVY.surface", navy.surface, 200f, 230f)
        assertHueInRange("NAVY.border", navy.border, 200f, 230f)
        assertWcagAa("NAVY", navy)
    }

    @Test
    fun forestPaletteKeepsDetailsInGreenFamilyWithAccessibleContrast() {
        val forest = AppearancePalette.FOREST
        assertHueInRange("FOREST.secondary", forest.secondary, 140f, 170f)
        assertHueInRange("FOREST.surface", forest.surface, 140f, 170f)
        assertHueInRange("FOREST.border", forest.border, 140f, 170f)
        assertWcagAa("FOREST", forest)
    }

    @Test
    fun paperPaletteKeepsDetailsInWarmBrownFamilyWithAccessibleContrast() {
        val paper = AppearancePalette.PAPER
        assertHueInRange("PAPER.secondary", paper.secondary, 15f, 45f)
        assertWcagAa("PAPER", paper)
    }

    @Test
    fun fromCustomReturnsPresetsVerbatimAndDerivesHarmoniousCustomSecondary() {
        val presets = listOf(
            AppearancePalette.LIGHT,
            AppearancePalette.DARK,
            AppearancePalette.NAVY,
            AppearancePalette.PAPER,
            AppearancePalette.FOREST,
        )
        for (preset in presets) {
            assertEquals(
                preset,
                AppearancePalette.fromCustom(preset.text, preset.background, preset.primary, 0, preset.appIcon),
            )
            assertEquals(
                preset,
                AppearancePalette.fromCustom(
                    preset.text,
                    preset.background,
                    preset.primary,
                    preset.secondary,
                    preset.appIcon,
                ),
            )
        }

        val customBlue = AppearancePalette.fromCustom(
            text = 0xFFE8F1FF.toInt(),
            background = 0xFF0A1428.toInt(),
            primary = 0xFF60A5FA.toInt(),
            secondary = 0,
            appIcon = 0,
        )
        assertHueInRange("customBlue.secondary", customBlue.secondary, 200f, 230f)
        val secondaryContrast = AppearancePalette.contrastRatio(customBlue.secondary, customBlue.background)
        assertTrue("Expected customBlue secondary contrast >= 4.5, got $secondaryContrast", secondaryContrast >= 4.5)
    }

    @Test
    fun lightAndDarkPalettesKeepAccessibleOnPrimaryContrast() {
        assertWcagAa("LIGHT", AppearancePalette.LIGHT)
        assertWcagAa("DARK", AppearancePalette.DARK)
    }

    @Test
    fun appearancePaletteDialogsAvoidMyTextViewSoSetupDialogStuffCannotClobberOnPrimary() {
        val guardedFiles = listOf(
            "src/main/res/layout/dialog_grant_all_files.xml",
            "src/main/res/layout/dialog_vault_notice.xml",
            "src/main/kotlin/org/fossify/gallery/dialogs/VaultChoiceDialog.kt",
        )
        for (relativePath in guardedFiles) {
            val file = File(relativePath).takeIf { it.exists() } ?: File("app", relativePath)
            assertTrue("Missing $relativePath", file.exists())
            val content = file.readText()
            assertFalse(
                "$relativePath must not instantiate or inflate MyTextView; setupDialogStuff() overwrites MyTextView text color with config.textColor",
                content.contains("org.fossify.commons.views.MyTextView") || content.contains("MyTextView("),
            )
        }
    }

    private fun assertWcagAa(label: String, palette: AppearancePalette) {
        val pairs = listOf(
            "text/background" to (palette.text to palette.background),
            "text/surface" to (palette.text to palette.surface),
            "secondary/background" to (palette.secondary to palette.background),
            "secondary/surface" to (palette.secondary to palette.surface),
            "onPrimary/primary" to (palette.onPrimary to palette.primary),
            "onSecondary/secondary" to (palette.onSecondary to palette.secondary),
        )
        for ((pairName, colors) in pairs) {
            val ratio = AppearancePalette.contrastRatio(colors.first, colors.second)
            assertTrue("$label $pairName contrast $ratio < 4.5", ratio >= 4.5)
        }
    }

    private fun assertHueInRange(label: String, color: Int, minHue: Float, maxHue: Float) {
        val hue = hueDegrees(color)
        assertTrue("$label hue $hue outside [$minHue, $maxHue]", hue in minHue..maxHue)
    }

    private fun hueDegrees(color: Int): Float {
        val r = ((color ushr 16) and 0xFF) / 255f
        val g = ((color ushr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta == 0f) return 0f
        val rawHue = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> (b - r) / delta + 2f
            else -> (r - g) / delta + 4f
        } * 60f
        return if (rawHue < 0f) rawHue + 360f else rawHue
    }
}
