package org.fossify.gallery

import org.fossify.gallery.helpers.media.IsolatedRegionTilePolicy
import org.fossify.gallery.helpers.media.TileRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsolatedRegionTilePolicyTest {

    @Test
    fun sanitizeTileRectClampsAndRejectsInvalidBounds() {
        assertEquals(
            TileRect(0, 10, 500, 400),
            IsolatedRegionTilePolicy.sanitizeTileRect(-20, 10, 500, 900, 1000, 400),
        )
        assertNull(IsolatedRegionTilePolicy.sanitizeTileRect(100, 100, 100, 200, 1000, 1000))
        assertNull(IsolatedRegionTilePolicy.sanitizeTileRect(300, 100, 200, 200, 1000, 1000))
        assertNull(IsolatedRegionTilePolicy.sanitizeTileRect(0, 0, 10, 10, 0, 1000))
    }

    @Test
    fun enforceTileSampleSizeKeepsWithinBudget() {
        val smallTile = TileRect(0, 0, 512, 512)
        assertEquals(1, IsolatedRegionTilePolicy.enforceTileSampleSize(smallTile, 0))
        assertEquals(2, IsolatedRegionTilePolicy.enforceTileSampleSize(smallTile, 2))

        val hugeTile = TileRect(0, 0, 16_384, 16_384)
        assertEquals(
            8,
            IsolatedRegionTilePolicy.enforceTileSampleSize(
                rect = hugeTile,
                requestedSampleSize = 1,
                maxPixels = 4_194_304L,
                maxDim = 4096,
            ),
        )
    }
}
