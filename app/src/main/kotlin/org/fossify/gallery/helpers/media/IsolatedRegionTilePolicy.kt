package org.fossify.gallery.helpers.media

data class TileRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Pure-Kotlin bounds sanitization and sample-size enforcement for isolated region/tile decoding.
 */
object IsolatedRegionTilePolicy {
    fun sanitizeTileRect(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): TileRect? {
        if (!IsolatedFormatSniffer.isValidSourceDimensions(imageWidth, imageHeight)) {
            return null
        }
        val clampedLeft = left.coerceIn(0, imageWidth)
        val clampedTop = top.coerceIn(0, imageHeight)
        val clampedRight = right.coerceIn(0, imageWidth)
        val clampedBottom = bottom.coerceIn(0, imageHeight)
        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
            return null
        }
        return TileRect(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }

    fun enforceTileSampleSize(
        rect: TileRect,
        requestedSampleSize: Int,
        maxPixels: Long = IsolatedFormatSniffer.MAX_OUTPUT_PIXELS,
        maxDim: Int = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
    ): Int {
        val rectW = rect.right - rect.left
        val rectH = rect.bottom - rect.top
        var sample = requestedSampleSize.coerceAtLeast(1)
        while (exceedsBudget(rectW, rectH, sample, maxDim, maxPixels)) {
            sample *= 2
        }
        return sample
    }

    private fun exceedsBudget(
        rectW: Int,
        rectH: Int,
        sample: Int,
        maxDim: Int,
        maxPixels: Long,
    ): Boolean {
        val outW = (rectW / sample).coerceAtLeast(1)
        val outH = (rectH / sample).coerceAtLeast(1)
        return outW > maxDim || outH > maxDim || outW.toLong() * outH.toLong() > maxPixels
    }
}
