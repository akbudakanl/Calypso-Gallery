package org.fossify.gallery

import org.fossify.gallery.helpers.filters.ColorOverlaySpec
import org.fossify.gallery.helpers.filters.PhotoFilterMath
import org.fossify.gallery.helpers.filters.PhotoFilterPack
import org.fossify.gallery.helpers.filters.ToneCurveSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFilterMathTest {

    @Test
    fun colorMatrix_identityValuesMatchIdentityMatrix() {
        val actual = PhotoFilterMath.buildColorMatrixValues(
            brightness = 0f,
            contrast = 1f,
            saturation = 1f,
            overlay = null,
        )
        val expected = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        assertArrayEquals(expected, actual, 1e-5f)
    }

    @Test
    fun colorMatrix_zeroSaturationUsesRec709WeightsAndOverlayBlends() {
        val gray = PhotoFilterMath.buildColorMatrixValues(
            brightness = 10f,
            contrast = 1f,
            saturation = 0f,
            overlay = null,
        )
        assertEquals(0.213f, gray[0], 1e-4f)
        assertEquals(0.715f, gray[1], 1e-4f)
        assertEquals(0.072f, gray[2], 1e-4f)
        assertEquals(10f, gray[4], 1e-4f)

        val tinted = PhotoFilterMath.buildColorMatrixValues(
            brightness = 0f,
            contrast = 1f,
            saturation = 1f,
            overlay = ColorOverlaySpec(depth = 0.5f, red = 1f, green = 0f, blue = 0f),
        )
        assertEquals(0.5f, tinted[0], 1e-4f)
        assertEquals(127.5f, tinted[4], 1e-3f)
        assertEquals(0f, tinted[9], 1e-4f)
    }

    @Test
    fun toneCurve_lutBoundsAndChannelCompositionPreserveAlpha() {
        val lut = PhotoFilterMath.buildToneLut(listOf(0 to 0, 128 to 192, 255 to 255))
        assertEquals(PhotoFilterMath.LUT_SIZE, lut.size)
        assertEquals(0, lut[0])
        assertEquals(192, lut[128])
        assertEquals(255, lut[255])
        assertTrue(lut.all { it in 0..PhotoFilterMath.LUT_MAX_INDEX })

        val (rLut, gLut, bLut) = PhotoFilterMath.buildChannelLuts(
            ToneCurveSpec(
                rgb = listOf(0 to 0, 255 to 255),
                red = listOf(0 to 10, 255 to 250),
            ),
        )
        val px = intArrayOf((0x80 shl 24) or (0 shl 16) or (100 shl 8) or 200)
        PhotoFilterMath.applyLutsToPixels(px, rLut, gLut, bLut)
        assertEquals((0x80 shl 24) or (10 shl 16) or (100 shl 8) or 200, px[0])
    }

    @Test
    fun presetPack_providesAllSixteenNonIdentityPresets() {
        val presets = PhotoFilterPack.getDefaultFilters()
        assertEquals(16, presets.size)
        assertEquals(16, presets.map { it.name }.toSet().size)
        for (preset in presets) {
            assertFalse("${preset.name} must not be identity", preset.isIdentity)
        }
    }
}
