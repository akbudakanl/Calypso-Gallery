package org.fossify.gallery

import org.fossify.gallery.helpers.ImageContainerMetadataStripper
import org.fossify.gallery.helpers.SHARE_METADATA_MODE_ALL
import org.fossify.gallery.helpers.SHARE_METADATA_MODE_LOCATION
import org.fossify.gallery.helpers.ShareMetadataSanitizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ImageContainerMetadataStripperTest {

    @Test
    fun jpegStripsIptcAndCommentWhilePreservingIccAndScanData() {
        val app0 = jpegSegment(0xE0, byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0x00))
        val app2Icc = jpegSegment(0xE2, "ICC_PROFILE_DATA".toByteArray())
        val app13Iptc = jpegSegment(0xED, "IPTC_SECRET_AUTHOR".toByteArray())
        val comment = jpegSegment(0xFE, "SECRET_COMMENT".toByteArray())
        val scanAndEoi = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08, 1, 2, 3, 4, 0xFF.toByte(), 0xD9.toByte())

        val input = ByteArrayOutputStream().apply {
            write(0xFF)
            write(0xD8)
            write(app0)
            write(app2Icc)
            write(app13Iptc)
            write(comment)
            write(scanAndEoi)
        }.toByteArray()

        val output = ByteArrayOutputStream()
        ImageContainerMetadataStripper.copyStripped(ByteArrayInputStream(input), output, "jpg")
        val stripped = output.toByteArray()

        val expected = ByteArrayOutputStream().apply {
            write(0xFF)
            write(0xD8)
            write(app0)
            write(app2Icc)
            write(scanAndEoi)
        }.toByteArray()

        assertArrayEquals(expected, stripped)
        assertFalse(String(stripped).contains("IPTC_SECRET_AUTHOR"))
        assertFalse(String(stripped).contains("SECRET_COMMENT"))
        assertTrue(String(stripped).contains("ICC_PROFILE_DATA"))
    }

    @Test
    fun pngStripsTextChunksWhilePreservingIccpAndImageData() {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdr = pngChunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0))
        val iccp = pngChunk("iCCP", "sRGB_PROFILE".toByteArray())
        val text = pngChunk("tEXt", "Software\u0000SecretApp".toByteArray())
        val itxt = pngChunk("iTXt", "XML:com.adobe.xmp\u0000\u0000\u0000\u0000\u0000<xmp/>".toByteArray())
        val ztxt = pngChunk("zTXt", "Comment\u0000\u0000compressed".toByteArray())
        val idat = pngChunk("IDAT", byteArrayOf(10, 20, 30, 40))
        val iend = pngChunk("IEND", byteArrayOf())

        val input = ByteArrayOutputStream().apply {
            write(signature)
            write(ihdr)
            write(iccp)
            write(text)
            write(itxt)
            write(ztxt)
            write(idat)
            write(iend)
        }.toByteArray()

        val output = ByteArrayOutputStream()
        ImageContainerMetadataStripper.copyStripped(ByteArrayInputStream(input), output, "png")
        val stripped = output.toByteArray()

        val expected = ByteArrayOutputStream().apply {
            write(signature)
            write(ihdr)
            write(iccp)
            write(idat)
            write(iend)
        }.toByteArray()

        assertArrayEquals(expected, stripped)
    }

    @Test
    fun webpStripsExifAndXmpAndClearsVp8xFlagsAndUpdatesRiffSize() {
        val vp8xPayload = ByteArray(10).apply { this[0] = 0x2C } // ICCP(0x20) + EXIF(0x08) + XMP(0x04)
        val vp8x = riffChunk("VP8X", vp8xPayload)
        val iccp = riffChunk("ICCP", "ICC".toByteArray()) // odd size (3) -> padded to 4
        val vp8 = riffChunk("VP8 ", byteArrayOf(1, 2, 3, 4))
        val exif = riffChunk("EXIF", "GPS_LAT_LON".toByteArray())
        val xmp = riffChunk("XMP ", "<xmp>secret</xmp>".toByteArray())

        val body = ByteArrayOutputStream().apply {
            write(vp8x)
            write(iccp)
            write(vp8)
            write(exif)
            write(xmp)
        }.toByteArray()

        val input = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray())
            write(int32LE(4 + body.size))
            write("WEBP".toByteArray())
            write(body)
        }.toByteArray()

        val output = ByteArrayOutputStream()
        ImageContainerMetadataStripper.copyStripped(ByteArrayInputStream(input), output, "webp")
        val stripped = output.toByteArray()

        val strippedStr = String(stripped, Charsets.ISO_8859_1)
        assertFalse(strippedStr.contains("GPS_LAT_LON"))
        assertFalse(strippedStr.contains("<xmp>secret</xmp>"))
        assertTrue(strippedStr.contains("ICCP"))
        assertTrue(strippedStr.contains("VP8 "))

        // RIFF size at bytes 4..7 must equal stripped.size - 8
        val riffSize = readInt32LE(stripped, 4)
        assertEquals(stripped.size - 8, riffSize)

        // VP8X flags at byte 20 (12-byte RIFF header + 8-byte VP8X chunk header): 0x2C -> 0x20
        assertEquals(0x20, stripped[20].toInt() and 0xFF)
    }

    @Test
    fun shareDisplayNameIsAnonymizedOnlyInAllMetadataMode() {
        val allName = ShareMetadataSanitizer.buildShareDisplayName(
            sourceName = "IMG_20261128_001500.jpg",
            extension = "jpg",
            mode = SHARE_METADATA_MODE_ALL
        )
        assertTrue(allName.matches(Regex("[0-9a-f]{8}\\.jpg")))
        assertFalse(allName.contains("calypso", ignoreCase = true))
        assertFalse(allName.contains("shared", ignoreCase = true))
        assertFalse(allName.contains("20261128"))

        val locationName = ShareMetadataSanitizer.buildShareDisplayName(
            sourceName = "IMG_20261128_001500.jpg",
            extension = "jpg",
            mode = SHARE_METADATA_MODE_LOCATION
        )
        assertTrue(locationName.matches(Regex("IMG_20261128_001500_[0-9a-f]{8}\\.jpg")))
        assertFalse(locationName.contains("calypso", ignoreCase = true))
        assertFalse(locationName.contains("shared", ignoreCase = true))
    }

    private fun jpegSegment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return ByteArrayOutputStream().apply {
            write(0xFF)
            write(marker)
            write((length ushr 8) and 0xFF)
            write(length and 0xFF)
            write(payload)
        }.toByteArray()
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(int32BE(data.size))
            write(type.toByteArray(Charsets.US_ASCII))
            write(data)
            write(byteArrayOf(0, 0, 0, 0)) // dummy CRC for structural test
        }.toByteArray()

    private fun riffChunk(fourCc: String, data: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(fourCc.toByteArray(Charsets.US_ASCII))
            write(int32LE(data.size))
            write(data)
            if (data.size % 2 == 1) write(0)
        }.toByteArray()

    private fun int32BE(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun int32LE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
