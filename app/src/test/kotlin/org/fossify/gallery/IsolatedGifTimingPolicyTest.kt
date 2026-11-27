package org.fossify.gallery

import org.fossify.gallery.helpers.media.IsolatedGifTimingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsolatedGifTimingPolicyTest {

    @Test
    fun normalizeFrameDelayMs_clampsOutOfRangeValues() {
        assertEquals(
            IsolatedGifTimingPolicy.DEFAULT_FRAME_DELAY_MS,
            IsolatedGifTimingPolicy.normalizeFrameDelayMs(0),
        )
        assertEquals(
            IsolatedGifTimingPolicy.DEFAULT_FRAME_DELAY_MS,
            IsolatedGifTimingPolicy.normalizeFrameDelayMs(-50),
        )
        assertEquals(
            IsolatedGifTimingPolicy.MIN_FRAME_DELAY_MS,
            IsolatedGifTimingPolicy.normalizeFrameDelayMs(5),
        )
        assertEquals(80, IsolatedGifTimingPolicy.normalizeFrameDelayMs(80))
        assertEquals(
            IsolatedGifTimingPolicy.MAX_FRAME_DELAY_MS,
            IsolatedGifTimingPolicy.normalizeFrameDelayMs(60_000),
        )
    }

    @Test
    fun nextFrameIndex_wrapsCircularly() {
        assertEquals(0, IsolatedGifTimingPolicy.nextFrameIndex(0, 0))
        assertEquals(0, IsolatedGifTimingPolicy.nextFrameIndex(0, 1))
        assertEquals(1, IsolatedGifTimingPolicy.nextFrameIndex(0, 3))
        assertEquals(2, IsolatedGifTimingPolicy.nextFrameIndex(1, 3))
        assertEquals(0, IsolatedGifTimingPolicy.nextFrameIndex(2, 3))
    }

    @Test
    fun nextBufferSlot_alternatesDoubleBuffers() {
        assertEquals(1, IsolatedGifTimingPolicy.nextBufferSlot(0))
        assertEquals(0, IsolatedGifTimingPolicy.nextBufferSlot(1))
    }

    @Test
    fun computeGifFrameDimensions_rejectsInvalidAndEnforcesPixelBudget() {
        assertNull(IsolatedGifTimingPolicy.computeGifFrameDimensions(0, 100))
        assertNull(IsolatedGifTimingPolicy.computeGifFrameDimensions(100, -1))

        assertEquals(
            640 to 480,
            IsolatedGifTimingPolicy.computeGifFrameDimensions(640, 480),
        )
        assertEquals(
            1024 to 512,
            IsolatedGifTimingPolicy.computeGifFrameDimensions(2048, 1024, reqWidth = 1024, reqHeight = 1024),
        )

        val capped = IsolatedGifTimingPolicy.computeGifFrameDimensions(4000, 4000)!!
        assertEquals(2048 to 2048, capped)
        assertEquals(
            true,
            capped.first.toLong() * capped.second.toLong() <= IsolatedGifTimingPolicy.MAX_GIF_PIXELS,
        )
    }
}
