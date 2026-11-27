package org.fossify.gallery.helpers.media

private const val BYTE_MASK = 0xFF
private const val BYTE_SHIFT = 8
private const val JPEG_MARKER_PREFIX = 0xFF
private const val JPEG_SOI = 0xD8
private const val JPEG_EOI = 0xD9
private const val JPEG_SOS = 0xDA
private const val JPEG_RST0 = 0xD0
private const val JPEG_RST7 = 0xD7
private const val JPEG_SOF0 = 0xC0
private const val JPEG_SOF3 = 0xC3
private const val JPEG_SOF5 = 0xC5
private const val JPEG_SOF7 = 0xC7
private const val JPEG_SOF9 = 0xC9
private const val JPEG_SOF11 = 0xCB
private const val JPEG_SOF13 = 0xCD
private const val JPEG_SOF15 = 0xCF
private const val MIN_JPEG_HEADER_BYTES = 3
private const val SOF_MIN_BODY = 7
private const val SOF_HEIGHT_OFFSET = 3
private const val SOF_WIDTH_OFFSET = 5

/**
 * Pure-Kotlin SOF header dimension parser used to validate EXIF thumbnail aspect ratios without
 * spinning up a native decoder.
 */
internal object IsolatedJpegHeaderParser {
    fun probeDimensions(bytes: ByteArray): Pair<Int, Int>? {
        val isJpeg = bytes.size >= MIN_JPEG_HEADER_BYTES &&
            byteAt(bytes, 0) == JPEG_MARKER_PREFIX &&
            byteAt(bytes, 1) == JPEG_SOI
        if (!isJpeg) return null

        var pos = 2
        var result: Pair<Int, Int>? = null
        var scanning = true
        while (scanning && pos + 1 < bytes.size) {
            pos = skipToMarkerCode(bytes, pos)
            if (pos < 0 || pos >= bytes.size) {
                scanning = false
            } else {
                val marker = byteAt(bytes, pos++)
                val next = handleMarker(bytes, marker, pos)
                result = next.dimensions
                pos = next.nextPos
                scanning = next.keepScanning
            }
        }
        return result
    }

    private data class MarkerStep(
        val nextPos: Int,
        val keepScanning: Boolean,
        val dimensions: Pair<Int, Int>? = null,
    )

    private fun handleMarker(bytes: ByteArray, marker: Int, pos: Int): MarkerStep {
        if (marker == 0 || marker in JPEG_RST0..JPEG_RST7) {
            return MarkerStep(nextPos = pos, keepScanning = true)
        }
        if (marker == JPEG_EOI || marker == JPEG_SOS || pos + 2 > bytes.size) {
            return MarkerStep(nextPos = pos, keepScanning = false)
        }
        val segLen = readU16Be(bytes, pos)
        if (segLen < 2 || pos + segLen > bytes.size) {
            return MarkerStep(nextPos = pos, keepScanning = false)
        }
        if (isSofMarker(marker) && segLen >= SOF_MIN_BODY) {
            val h = readU16Be(bytes, pos + SOF_HEIGHT_OFFSET)
            val w = readU16Be(bytes, pos + SOF_WIDTH_OFFSET)
            if (w > 0 && h > 0) {
                return MarkerStep(nextPos = pos + segLen, keepScanning = false, dimensions = w to h)
            }
        }
        return MarkerStep(nextPos = pos + segLen, keepScanning = true)
    }

    private fun skipToMarkerCode(bytes: ByteArray, start: Int): Int {
        var pos = start
        while (pos < bytes.size && byteAt(bytes, pos) != JPEG_MARKER_PREFIX) {
            pos++
        }
        while (pos < bytes.size && byteAt(bytes, pos) == JPEG_MARKER_PREFIX) {
            pos++
        }
        return if (pos < bytes.size) pos else -1
    }

    private fun isSofMarker(marker: Int): Boolean {
        return marker in JPEG_SOF0..JPEG_SOF3 ||
            marker in JPEG_SOF5..JPEG_SOF7 ||
            marker in JPEG_SOF9..JPEG_SOF11 ||
            marker in JPEG_SOF13..JPEG_SOF15
    }

    private fun byteAt(bytes: ByteArray, idx: Int): Int = bytes[idx].toInt() and BYTE_MASK

    private fun readU16Be(bytes: ByteArray, idx: Int): Int {
        return (byteAt(bytes, idx) shl BYTE_SHIFT) or byteAt(bytes, idx + 1)
    }
}
