package org.fossify.gallery.helpers

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val STREAM_BUFFER_SIZE = 8192

private const val JPEG_MARKER_PREFIX = 0xFF
private const val JPEG_SOI = 0xD8
private const val JPEG_EOI = 0xD9
private const val JPEG_SOS = 0xDA
private const val JPEG_RST0 = 0xD0
private const val JPEG_RST7 = 0xD7
private const val JPEG_TEM = 0x01
private const val JPEG_APP13 = 0xED
private const val JPEG_COM = 0xFE
private const val JPEG_MIN_SEGMENT_LENGTH = 2

private const val PNG_SIGNATURE_SIZE = 8
private const val PNG_CHUNK_HEADER_SIZE = 8
private const val PNG_CRC_SIZE = 4
private const val PNG_TYPE_OFFSET = 4
private const val PNG_SIG_0 = 0x89.toByte()
private const val PNG_SIG_1 = 0x50.toByte()
private const val PNG_SIG_2 = 0x4E.toByte()
private const val PNG_SIG_3 = 0x47.toByte()
private const val PNG_SIG_4 = 0x0D.toByte()
private const val PNG_SIG_5 = 0x0A.toByte()
private const val PNG_SIG_6 = 0x1A.toByte()
private const val PNG_SIG_7 = 0x0A.toByte()

private val PNG_SIGNATURE = byteArrayOf(
    PNG_SIG_0, PNG_SIG_1, PNG_SIG_2, PNG_SIG_3,
    PNG_SIG_4, PNG_SIG_5, PNG_SIG_6, PNG_SIG_7
)
private val PNG_STRIPPED_CHUNKS = setOf("tEXt", "iTXt", "zTXt")

private const val RIFF_HEADER_SIZE = 12
private const val RIFF_CHUNK_HEADER_SIZE = 8
private const val RIFF_SIZE_OFFSET = 4
private const val VP8X_EXIF_FLAG = 0x08
private const val VP8X_XMP_FLAG = 0x04
private val WEBP_STRIPPED_CHUNKS = setOf("EXIF", "XMP ")

private const val BYTE_MASK = 0xFF
private const val SHIFT_8 = 8
private const val SHIFT_16 = 16
private const val SHIFT_24 = 24
private const val OFFSET_1 = 1
private const val OFFSET_2 = 2
private const val OFFSET_3 = 3

/**
 * Strips container-level metadata segments from outgoing images without re-encoding pixel data:
 * - JPEG: removes `APP13` (IPTC) and `COM` (comment) segments while keeping `ICC` profiles intact.
 * - PNG: removes `tEXt`, `iTXt`, and `zTXt` text chunks while keeping `iCCP`/`sRGB`/`gAMA`.
 * - WebP: removes `EXIF` and `XMP ` RIFF chunks and clears their `VP8X` header flags.
 */
object ImageContainerMetadataStripper {

    fun copyStripped(input: InputStream, output: OutputStream, extension: String) {
        when (extension.lowercase()) {
            "jpg", "jpeg" -> stripJpeg(input, output)
            "png" -> stripPng(input, output)
            "webp" -> stripWebp(input, output)
            else -> input.copyTo(output)
        }
    }

    private fun stripJpeg(input: InputStream, output: OutputStream) {
        val soiFirst = input.read()
        val soiSecond = input.read()
        if (soiFirst != JPEG_MARKER_PREFIX || soiSecond != JPEG_SOI) {
            throw IOException("Invalid JPEG header")
        }
        output.write(JPEG_MARKER_PREFIX)
        output.write(JPEG_SOI)

        var marker = readNextJpegMarker(input)
        while (marker != -1) {
            if (marker == JPEG_EOI || marker == JPEG_SOS) {
                output.write(JPEG_MARKER_PREFIX)
                output.write(marker)
                input.copyTo(output, STREAM_BUFFER_SIZE)
                return
            }
            processJpegSegment(input, output, marker)
            marker = readNextJpegMarker(input)
        }
    }

    private fun readNextJpegMarker(input: InputStream): Int {
        var byte = input.read()
        while (byte != -1 && byte != JPEG_MARKER_PREFIX) {
            byte = input.read()
        }
        if (byte == -1) return -1

        var marker = input.read()
        while (marker == JPEG_MARKER_PREFIX) {
            marker = input.read()
        }
        return marker
    }

    private fun processJpegSegment(input: InputStream, output: OutputStream, marker: Int) {
        if (marker == 0 || marker == JPEG_TEM || marker in JPEG_RST0..JPEG_RST7) {
            output.write(JPEG_MARKER_PREFIX)
            output.write(marker)
            return
        }
        val length = readUInt16BE(input)
        if (length < JPEG_MIN_SEGMENT_LENGTH) {
            throw IOException("Invalid JPEG segment length: $length")
        }
        val payloadLength = length - JPEG_MIN_SEGMENT_LENGTH
        if (marker == JPEG_APP13 || marker == JPEG_COM) {
            skipExact(input, payloadLength.toLong())
        } else {
            output.write(JPEG_MARKER_PREFIX)
            output.write(marker)
            output.write((length ushr SHIFT_8) and BYTE_MASK)
            output.write(length and BYTE_MASK)
            copyExact(input, output, payloadLength.toLong())
        }
    }

    private fun stripPng(input: InputStream, output: OutputStream) {
        val signature = ByteArray(PNG_SIGNATURE_SIZE)
        readExact(input, signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw IOException("Invalid PNG signature")
        }
        output.write(signature)

        val header = ByteArray(PNG_CHUNK_HEADER_SIZE)
        var firstByte = input.read()
        var reachedEnd = false
        while (firstByte != -1 && !reachedEnd) {
            header[0] = firstByte.toByte()
            readExact(input, header, offset = OFFSET_1, length = PNG_CHUNK_HEADER_SIZE - OFFSET_1)

            val length = readInt32BE(header, 0)
            if (length < 0) {
                throw IOException("Invalid PNG chunk length: $length")
            }
            val type = String(header, PNG_TYPE_OFFSET, PNG_TYPE_OFFSET, Charsets.US_ASCII)
            val totalPayloadAndCrc = length.toLong() + PNG_CRC_SIZE

            if (type in PNG_STRIPPED_CHUNKS) {
                skipExact(input, totalPayloadAndCrc)
            } else {
                output.write(header)
                copyExact(input, output, totalPayloadAndCrc)
            }
            reachedEnd = type == "IEND"
            if (!reachedEnd) {
                firstByte = input.read()
            }
        }
    }

    private fun stripWebp(input: InputStream, output: OutputStream) {
        val riffHeader = ByteArray(RIFF_HEADER_SIZE)
        readExact(input, riffHeader)
        val riffTag = String(riffHeader, 0, RIFF_SIZE_OFFSET, Charsets.US_ASCII)
        val webpTag = String(riffHeader, RIFF_CHUNK_HEADER_SIZE, RIFF_SIZE_OFFSET, Charsets.US_ASCII)
        if (riffTag != "RIFF" || webpTag != "WEBP") {
            throw IOException("Invalid WebP header")
        }

        val chunksBuffer = ByteArrayOutputStream()
        val chunkHeader = ByteArray(RIFF_CHUNK_HEADER_SIZE)
        var firstByte = input.read()
        while (firstByte != -1) {
            chunkHeader[0] = firstByte.toByte()
            readExact(input, chunkHeader, offset = OFFSET_1, length = RIFF_CHUNK_HEADER_SIZE - OFFSET_1)

            val fourCc = String(chunkHeader, 0, RIFF_SIZE_OFFSET, Charsets.US_ASCII)
            val chunkSize = readInt32LE(chunkHeader, RIFF_SIZE_OFFSET)
            if (chunkSize < 0) {
                throw IOException("Invalid WebP chunk size: $chunkSize")
            }
            val paddedSize = chunkSize.toLong() + (chunkSize and 1)
            writeWebpChunkIfKept(input, chunksBuffer, chunkHeader, fourCc, chunkSize, paddedSize)
            firstByte = input.read()
        }

        val bodyBytes = chunksBuffer.toByteArray()
        val totalRiffSize = RIFF_SIZE_OFFSET + bodyBytes.size
        writeInt32LE(riffHeader, RIFF_SIZE_OFFSET, totalRiffSize)
        output.write(riffHeader)
        output.write(bodyBytes)
    }

    private fun writeWebpChunkIfKept(
        input: InputStream,
        chunksBuffer: ByteArrayOutputStream,
        chunkHeader: ByteArray,
        fourCc: String,
        chunkSize: Int,
        paddedSize: Long
    ) {
        if (fourCc in WEBP_STRIPPED_CHUNKS) {
            skipExact(input, paddedSize)
            return
        }
        chunksBuffer.write(chunkHeader)
        if (fourCc == "VP8X" && chunkSize >= 1) {
            val payload = ByteArray(paddedSize.toInt())
            readExact(input, payload)
            val mask = (VP8X_EXIF_FLAG or VP8X_XMP_FLAG).inv()
            payload[0] = (payload[0].toInt() and mask).toByte()
            chunksBuffer.write(payload)
        } else {
            copyExact(input, chunksBuffer, paddedSize)
        }
    }
}

private fun readUInt16BE(input: InputStream): Int {
    val high = input.read()
    val low = input.read()
    if (high == -1 || low == -1) throw IOException("Unexpected EOF reading 16-bit integer")
    return (high shl SHIFT_8) or low
}

private fun readInt32BE(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and BYTE_MASK) shl SHIFT_24) or
        ((bytes[offset + OFFSET_1].toInt() and BYTE_MASK) shl SHIFT_16) or
        ((bytes[offset + OFFSET_2].toInt() and BYTE_MASK) shl SHIFT_8) or
        (bytes[offset + OFFSET_3].toInt() and BYTE_MASK)

private fun readInt32LE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and BYTE_MASK) or
        ((bytes[offset + OFFSET_1].toInt() and BYTE_MASK) shl SHIFT_8) or
        ((bytes[offset + OFFSET_2].toInt() and BYTE_MASK) shl SHIFT_16) or
        ((bytes[offset + OFFSET_3].toInt() and BYTE_MASK) shl SHIFT_24)

private fun writeInt32LE(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and BYTE_MASK).toByte()
    bytes[offset + OFFSET_1] = ((value ushr SHIFT_8) and BYTE_MASK).toByte()
    bytes[offset + OFFSET_2] = ((value ushr SHIFT_16) and BYTE_MASK).toByte()
    bytes[offset + OFFSET_3] = ((value ushr SHIFT_24) and BYTE_MASK).toByte()
}

private fun readExact(
    input: InputStream,
    dest: ByteArray,
    offset: Int = 0,
    length: Int = dest.size
) {
    var remaining = length
    var pos = offset
    while (remaining > 0) {
        val read = input.read(dest, pos, remaining)
        if (read == -1) throw IOException("Unexpected EOF")
        pos += read
        remaining -= read
    }
}

private fun skipExact(input: InputStream, count: Long) {
    var remaining = count
    val discard = ByteArray(STREAM_BUFFER_SIZE)
    while (remaining > 0L) {
        val toRead = minOf(remaining, discard.size.toLong()).toInt()
        val read = input.read(discard, 0, toRead)
        if (read == -1) throw IOException("Unexpected EOF while skipping bytes")
        remaining -= read
    }
}

private fun copyExact(input: InputStream, output: OutputStream, count: Long) {
    var remaining = count
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    while (remaining > 0L) {
        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
        val read = input.read(buffer, 0, toRead)
        if (read == -1) throw IOException("Unexpected EOF while copying bytes")
        output.write(buffer, 0, read)
        remaining -= read
    }
}
