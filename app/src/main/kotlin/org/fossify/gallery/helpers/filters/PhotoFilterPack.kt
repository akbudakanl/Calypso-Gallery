package org.fossify.gallery.helpers.filters

/**
 * Pure-Kotlin preset filter pack mirroring the classic Calypso/Fossify Gallery editor filters.
 */
@Suppress("MagicNumber")
object PhotoFilterPack {
    fun getDefaultFilters(): List<PhotoFilter> = warmAndVividPresets() + classicAndMonochromePresets()

    private fun warmAndVividPresets(): List<PhotoFilter> = listOf(
        PhotoFilter(
            name = "Struck",
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 0, 97 to 120, 150 to 176, 255 to 255),
                red = listOf(0 to 0, 128 to 132, 255 to 255),
                blue = listOf(0 to 12, 255 to 240),
            ),
            saturation = 1.15f,
        ),
        PhotoFilter(
            name = "Clarendon",
            contrast = 1.18f,
            saturation = 1.25f,
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 0, 75 to 60, 185 to 204, 255 to 255),
                blue = listOf(0 to 8, 255 to 250),
            ),
        ),
        PhotoFilter(
            name = "OldMan",
            brightness = 14f,
            saturation = 0.35f,
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 18, 255 to 228),
                red = listOf(0 to 25, 255 to 240),
                blue = listOf(0 to 0, 255 to 205),
            ),
            vignetteAlpha = 0.42f,
        ),
        PhotoFilter(
            name = "Mars",
            brightness = 6f,
            contrast = 1.22f,
            colorOverlay = ColorOverlaySpec(depth = 0.14f, red = 0.90f, green = 0.35f, blue = 0.12f),
        ),
        PhotoFilter(
            name = "Rise",
            brightness = 18f,
            colorOverlay = ColorOverlaySpec(depth = 0.12f, red = 0.95f, green = 0.72f, blue = 0.38f),
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 10, 128 to 144, 255 to 250),
            ),
        ),
        PhotoFilter(
            name = "April",
            brightness = 8f,
            saturation = 1.18f,
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 0, 80 to 72, 175 to 188, 255 to 255),
                green = listOf(0 to 0, 128 to 136, 255 to 255),
            ),
        ),
        PhotoFilter(
            name = "Amazon",
            contrast = 1.10f,
            saturation = 1.15f,
            colorOverlay = ColorOverlaySpec(depth = 0.11f, red = 0.18f, green = 0.68f, blue = 0.42f),
        ),
        PhotoFilter(
            name = "StarLit",
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 0, 110 to 139, 255 to 255),
            ),
        ),
    )

    private fun classicAndMonochromePresets(): List<PhotoFilter> = listOf(
        PhotoFilter(
            name = "Whisper",
            contrast = 1.08f,
            colorOverlay = ColorOverlaySpec(depth = 0.10f, red = 0.35f, green = 0.25f, blue = 0.65f),
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 8, 128 to 132, 255 to 245),
            ),
            vignetteAlpha = 0.28f,
        ),
        PhotoFilter(
            name = "Lime",
            colorOverlay = ColorOverlaySpec(depth = 0.12f, red = 0.65f, green = 0.85f, blue = 0.20f),
            toneCurve = ToneCurveSpec(
                blue = listOf(0 to 30, 165 to 114, 255 to 255),
            ),
        ),
        PhotoFilter(
            name = "Haan",
            saturation = 1.10f,
            toneCurve = ToneCurveSpec(
                green = listOf(0 to 10, 120 to 138, 255 to 255),
                blue = listOf(0 to 0, 128 to 118, 255 to 235),
            ),
            vignetteAlpha = 0.35f,
        ),
        PhotoFilter(
            name = "BlueMess",
            brightness = 18f,
            toneCurve = ToneCurveSpec(
                red = listOf(0 to 0, 95 to 124, 160 to 176, 255 to 255),
                blue = listOf(0 to 24, 255 to 255),
            ),
        ),
        PhotoFilter(
            name = "Adele",
            contrast = 1.15f,
            saturation = 0f,
        ),
        PhotoFilter(
            name = "Cruz",
            contrast = 1.25f,
            saturation = 0f,
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 0, 90 to 110, 180 to 205, 255 to 255),
            ),
        ),
        PhotoFilter(
            name = "Metropolis",
            contrast = 1.32f,
            saturation = 0f,
            vignetteAlpha = 0.40f,
        ),
        PhotoFilter(
            name = "Audrey",
            contrast = 1.28f,
            saturation = 0f,
            toneCurve = ToneCurveSpec(
                rgb = listOf(0 to 12, 85 to 70, 175 to 198, 255 to 252),
            ),
        ),
    )
}
