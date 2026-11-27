package org.fossify.gallery

import org.fossify.gallery.helpers.media.IsolatedFormatSniffer
import org.fossify.gallery.helpers.media.IsolatedMediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolatedFormatSnifferTest {

    @Test
    fun sniffFormatDetectsCommonAndHighRiskFormats() {
        assertEquals(
            IsolatedMediaFormat.JPEG,
            IsolatedFormatSniffer.sniffFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())),
        )
        assertEquals(
            IsolatedMediaFormat.PNG,
            IsolatedFormatSniffer.sniffFormat(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            ),
        )
        assertEquals(
            IsolatedMediaFormat.GIF,
            IsolatedFormatSniffer.sniffFormat("GIF89a".encodeToByteArray()),
        )
        assertEquals(
            IsolatedMediaFormat.WEBP,
            IsolatedFormatSniffer.sniffFormat("RIFF\u0000\u0000\u0000\u0000WEBP".encodeToByteArray()),
        )
        assertEquals(
            IsolatedMediaFormat.AVIF,
            IsolatedFormatSniffer.sniffFormat("\u0000\u0000\u0000\u0018ftypavif".encodeToByteArray()),
        )
        assertEquals(
            IsolatedMediaFormat.HEIC,
            IsolatedFormatSniffer.sniffFormat("\u0000\u0000\u0000\u0018ftypheic".encodeToByteArray()),
        )
        assertEquals(
            IsolatedMediaFormat.JXL,
            IsolatedFormatSniffer.sniffFormat(byteArrayOf(0xFF.toByte(), 0x0A, 0x00)),
        )
        assertEquals(
            IsolatedMediaFormat.JXL,
            IsolatedFormatSniffer.sniffFormat(
                byteArrayOf(0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20, 0x0D, 0x0A, 0x87.toByte(), 0x0A),
            ),
        )
        assertEquals(
            IsolatedMediaFormat.SVG,
            IsolatedFormatSniffer.sniffFormat("  <svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".encodeToByteArray()),
        )
        assertEquals(
            IsolatedMediaFormat.RAW,
            IsolatedFormatSniffer.sniffFormat(byteArrayOf(0x49, 0x49, 0x2A, 0x00), "capture.DNG"),
        )
        assertEquals(
            IsolatedMediaFormat.UNKNOWN,
            IsolatedFormatSniffer.sniffFormat(byteArrayOf(0x01, 0x02, 0x03, 0x04), "unknown.bin"),
        )
    }

    @Test
    fun rejectsDecompressionBombDimensions() {
        assertFalse(IsolatedFormatSniffer.isValidSourceDimensions(0, 100))
        assertFalse(IsolatedFormatSniffer.isValidSourceDimensions(70_000, 100))
        assertFalse(IsolatedFormatSniffer.isValidSourceDimensions(40_000, 40_000))
        assertTrue(IsolatedFormatSniffer.isValidSourceDimensions(4096, 3072))

        assertNull(IsolatedFormatSniffer.computeBoundedTargetSize(50_000, 50_000, 512, 512))
    }

    @Test
    fun computesBoundedTargetsAndSampleSize() {
        assertEquals(4, IsolatedFormatSniffer.computeSampleSize(4000, 3000, 1000, 750))
        val target = IsolatedFormatSniffer.computeBoundedTargetSize(8000, 6000, 4096, 4096)
        assertEquals(4096 to 3072, target)
    }

    @Test
    fun videoHintAllowlistMatchesVideoExtensionsOnly() {
        assertTrue(IsolatedFormatSniffer.isVideoFileHint("/storage/emulated/0/DCIM/clip.MP4"))
        assertTrue(IsolatedFormatSniffer.isVideoFileHint("movie.mkv"))
        assertFalse(IsolatedFormatSniffer.isVideoFileHint("photo.jpg"))
        assertFalse(IsolatedFormatSniffer.isVideoFileHint("vector.svg"))
        assertFalse(IsolatedFormatSniffer.isVideoFileHint("deadbeef.bin"))
    }
}
