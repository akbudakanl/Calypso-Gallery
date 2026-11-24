package org.fossify.gallery

import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.VaultPullGestureHandler
import org.fossify.gallery.helpers.VaultPullTonePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPullTonePaletteTest {

    @Test
    fun builtInPresetsKeepExactPrimaryAndDistinctMutedTone() {
        val presets = listOf(
            "LIGHT" to AppearancePalette.LIGHT,
            "DARK" to AppearancePalette.DARK,
            "NAVY" to AppearancePalette.NAVY,
            "PAPER" to AppearancePalette.PAPER,
            "FOREST" to AppearancePalette.FOREST,
            "VIOLET" to AppearancePalette.VIOLET,
            "CORAL" to AppearancePalette.CORAL,
        )

        for ((name, preset) in presets) {
            val resolved = VaultPullTonePalette.resolve(preset)
            assertEquals("$name armedColor should equal preset primary", preset.primary, resolved.armedColor)
            assertEquals("$name surfaceColor should equal preset surface", preset.surface, resolved.surfaceColor)
            assertNotEquals("$name mutedColor should be calmer than armedColor", resolved.armedColor, resolved.mutedColor)

            val armedContrast = AppearancePalette.contrastRatio(resolved.armedColor, resolved.background)
            val mutedContrast = AppearancePalette.contrastRatio(resolved.mutedColor, resolved.background)
            assertTrue("$name armedContrast ($armedContrast) should exceed mutedContrast ($mutedContrast)", armedContrast > mutedContrast)
            assertTrue("$name mutedContrast ($mutedContrast) should remain legible", mutedContrast >= 1.85)
        }
    }

    @Test
    fun coloredPresetsNeverStartNearWhiteWhileDescending() {
        val forest = VaultPullTonePalette.resolve(AppearancePalette.FOREST)
        val navy = VaultPullTonePalette.resolve(AppearancePalette.NAVY)

        // Deep Forest muted shade must stay green-dominant (green channel clearly above red/blue)
        val forestR = (forest.mutedColor ushr 16) and 0xFF
        val forestG = (forest.mutedColor ushr 8) and 0xFF
        val forestB = forest.mutedColor and 0xFF
        assertTrue("Forest muted shade should remain green-dominant ($forestR,$forestG,$forestB)", forestG > forestR + 20 && forestG > forestB + 10)

        // Midnight Blue muted shade must stay blue-dominant (blue channel clearly above red)
        val navyR = (navy.mutedColor ushr 16) and 0xFF
        val navyB = navy.mutedColor and 0xFF
        assertTrue("Navy muted shade should remain blue-dominant ($navyR,$navyB)", navyB > navyR + 25)
    }

    @Test
    fun customThemesWithDiverseAndLowContrastColorsRemainLegible() {
        val customCases = listOf(
            // Vivid custom neon cyan on dark plum
            AppearancePalette.fromCustom(
                text = 0xFFF5F3FF.toInt(),
                background = 0xFF140B24.toInt(),
                primary = 0xFF22D3EE.toInt(),
                secondary = 0,
                appIcon = 0,
            ),
            // Warm coral on crisp white
            AppearancePalette.fromCustom(
                text = 0xFF1C1917.toInt(),
                background = 0xFFFFFFFF.toInt(),
                primary = 0xFFE11D48.toInt(),
                secondary = 0,
                appIcon = 0,
            ),
            // Low-contrast custom: dark navy primary on pure black background
            AppearancePalette.fromCustom(
                text = 0xFFFFFFFF.toInt(),
                background = 0xFF000000.toInt(),
                primary = 0xFF101840.toInt(),
                secondary = 0,
                appIcon = 0,
            ),
            // Low-contrast custom: pale yellow primary on pure white background
            AppearancePalette.fromCustom(
                text = 0xFF111111.toInt(),
                background = 0xFFFFFFFF.toInt(),
                primary = 0xFFFEF08A.toInt(),
                secondary = 0,
                appIcon = 0,
            ),
            // Mid-tone gray custom background
            AppearancePalette.fromCustom(
                text = 0xFF000000.toInt(),
                background = 0xFF7A7A7A.toInt(),
                primary = 0xFF1E3A8A.toInt(),
                secondary = 0,
                appIcon = 0,
            ),
        )

        for ((index, palette) in customCases.withIndex()) {
            val resolved = VaultPullTonePalette.resolve(palette)
            val armedContrast = AppearancePalette.contrastRatio(resolved.armedColor, resolved.background)
            val mutedContrast = AppearancePalette.contrastRatio(resolved.mutedColor, resolved.background)
            assertTrue("Custom case $index armedContrast=$armedContrast too low", armedContrast >= 2.6)
            assertTrue("Custom case $index mutedContrast=$mutedContrast too low", mutedContrast >= 1.5)
            assertTrue("Custom case $index auraPeakAlpha should stay visible", resolved.auraPeakAlpha >= 0.26f)
            assertTrue("Custom case $index auraMidAlpha should stay visible", resolved.auraMidAlpha >= 0.12f)
            assertTrue("Custom case $index rimAlpha should stay visible", resolved.rimAlpha >= 0.16f)
        }
    }

    @Test
    fun lightModeAndDeepDarkCustomThemesBoostGlowVisibility() {
        val light = VaultPullTonePalette.resolve(AppearancePalette.LIGHT)
        assertEquals("Light mode surface disc should use pure white surface", AppearancePalette.LIGHT.surface, light.surfaceColor)
        assertTrue("Light mode aura peak should be clearly visible (${light.auraPeakAlpha})", light.auraPeakAlpha >= 0.26f)
        assertTrue("Light mode rim should be clearly visible (${light.rimAlpha})", light.rimAlpha >= 0.16f)

        // Deep saturated violet primary on dark custom background has lower luminance than Forest/Navy
        val darkCustomViolet = VaultPullTonePalette.resolve(
            AppearancePalette.fromCustom(
                text = 0xFFF5F3FF.toInt(),
                background = 0xFF0A0A0F.toInt(),
                primary = 0xFF7C3AED.toInt(),
                secondary = 0,
                appIcon = 0,
            ),
        )
        val forest = VaultPullTonePalette.resolve(AppearancePalette.FOREST)
        assertTrue(
            "Deep custom dark primary should lift glow luminance above raw primary",
            AppearancePalette.contrastRatio(darkCustomViolet.glowColor, darkCustomViolet.background) >
                AppearancePalette.contrastRatio(darkCustomViolet.armedColor, darkCustomViolet.background),
        )
        assertTrue(
            "Deep custom dark primary should receive stronger aura alpha (${darkCustomViolet.auraPeakAlpha}) than bright preset (${forest.auraPeakAlpha})",
            darkCustomViolet.auraPeakAlpha > forest.auraPeakAlpha,
        )
    }

    @Test
    fun rubberBandPullRemainsLinearTo95dpAndDampsSmoothlyBelow115dp() {
        val density = 3f
        val thresholdDeltaY = (VaultPullGestureHandler.HOLD_OPEN_THRESHOLD_DP / VaultPullGestureHandler.TRANSLATION_FACTOR) * density

        // Zero / negative pull stays at 0
        assertEquals(0f, VaultPullGestureHandler.computeRubberBandTranslationPx(0f, density), 0.001f)
        assertEquals(0f, VaultPullGestureHandler.computeRubberBandTranslationPx(-50f, density), 0.001f)

        // Below threshold: exact linear 0.45x response
        val midPx = VaultPullGestureHandler.computeRubberBandTranslationPx(100f * density, density)
        assertEquals(45f * density, midPx, 0.01f)

        // Exactly at 95dp threshold
        val atThresholdDp = VaultPullGestureHandler.computeRubberBandTranslationPx(thresholdDeltaY, density) / density
        assertEquals(95f, atThresholdDp, 0.01f)

        // Beyond threshold: monotonically increases with strong damping, strictly bounded at 115dp
        val softOverDp = VaultPullGestureHandler.computeRubberBandTranslationPx(thresholdDeltaY + 60f * density, density) / density
        val hardOverDp = VaultPullGestureHandler.computeRubberBandTranslationPx(thresholdDeltaY + 400f * density, density) / density
        val extremeOverDp = VaultPullGestureHandler.computeRubberBandTranslationPx(thresholdDeltaY + 5000f * density, density) / density

        assertTrue("Soft overpull ($softOverDp) should exceed 95dp but stay well below linear", softOverDp in 95.1f..106f)
        assertTrue("Hard overpull ($hardOverDp) should exceed soft overpull ($softOverDp)", hardOverDp > softOverDp)
        assertTrue("Extreme overpull ($extremeOverDp) must never exceed 115dp", extremeOverDp <= 115f && extremeOverDp > hardOverDp)
    }
}
