package org.fossify.gallery.helpers.media

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class IsolatedMediaFormat {
    JPEG,
    PNG,
    GIF,
    WEBP,
    AVIF,
    JXL,
    HEIC,
    SVG,
    RAW,
    UNKNOWN,
}

private const val MIN_JPEG_HEADER = 3
private const val MIN_GIF_HEADER = 6
private const val MIN_PNG_HEADER = 8
private const val MIN_RIFF_HEADER = 12
private const val MIN_FTYP_HEADER = 12
private const val FTYP_OFFSET = 4
private const val BRAND_OFFSET = 8
private const val BRAND_LENGTH = 4
private const val RIFF_WEBP_OFFSET = 8
private const val JXL_CODESTREAM_LEN = 2
private const val JXL_CONTAINER_LEN = 12
private const val SVG_SCAN_BYTES = 512
private const val BYTE_MASK = 0xFF
private const val JPEG_MARKER_START = 0xFF
private const val JPEG_MARKER_SOI = 0xD8
private const val JXL_MARKER_SECOND = 0x0A
private const val PANORAMA_SIDE_MULT = 4

@Suppress("MagicNumber")
private val PNG_MAGIC = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)

@Suppress("MagicNumber")
private val JXL_CONTAINER_MAGIC = byteArrayOf(
    0x00,
    0x00,
    0x00,
    0x0C,
    0x4A,
    0x58,
    0x4C,
    0x20,
    0x0D,
    0x0A,
    0x87.toByte(),
    0x0A,
)

private val AVIF_BRANDS = setOf("avif", "avis")
private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
private val RAW_EXTENSIONS = setOf(
    "dng", "cr2", "cr3", "nef", "nrw", "arw", "orf", "rw2", "raf", "pef", "srw", "raw",
)
private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "webm", "3gp", "3gpp", "3g2", "mov", "avi", "m4v", "flv", "f4v", "wmv",
    "ts", "mts", "m2ts", "mpg", "mpeg", "m2v", "mpeg4", "mp4v", "ogv", "ogm", "vob", "asf",
    "rm", "rmvb", "divx", "qt",
)

/**
 * Pure-Kotlin format sniffing, decompression-bomb bounds checks, and video allowlist checks.
 */
object IsolatedFormatSniffer {
    const val MAX_SOURCE_DIMENSION = 65_536
    const val MAX_SOURCE_PIXELS = 300_000_000L
    const val MAX_OUTPUT_DIMENSION = 4096
    const val MAX_OUTPUT_PIXELS = 16_777_216L
    const val MAX_SVG_DIMENSION = 2048
    const val DEFAULT_SVG_DIMENSION = 512
    const val MAX_IN_MEMORY_INPUT_BYTES = 100 * 1024 * 1024
    const val MAX_STREAMING_INPUT_BYTES = 512L * 1024L * 1024L

    fun sniffFormat(header: ByteArray, nameHint: String = ""): IsolatedMediaFormat {
        var detected = sniffBinaryFormat(header)
        if (detected == IsolatedMediaFormat.UNKNOWN && isSvgPayload(header, nameHint)) {
            detected = IsolatedMediaFormat.SVG
        }
        if (detected == IsolatedMediaFormat.UNKNOWN && extensionOf(nameHint) in RAW_EXTENSIONS) {
            detected = IsolatedMediaFormat.RAW
        }
        return detected
    }

    fun isValidSourceDimensions(width: Int, height: Int): Boolean {
        if (width !in 1..MAX_SOURCE_DIMENSION || height !in 1..MAX_SOURCE_DIMENSION) {
            return false
        }
        return width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS
    }

    fun computeSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (minOf(srcWidth, srcHeight, reqWidth, reqHeight) <= 0) return 1
        var sampleSize = 1
        while (srcWidth / (sampleSize * 2) >= reqWidth && srcHeight / (sampleSize * 2) >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun computeBoundedTargetSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
        maxDimension: Int = MAX_OUTPUT_DIMENSION,
        maxPixels: Long = MAX_OUTPUT_PIXELS,
        fillMinSide: Boolean = false,
    ): Pair<Int, Int>? {
        if (!isValidSourceDimensions(srcWidth, srcHeight)) return null
        val boundedMaxDim = maxDimension.coerceAtLeast(1)
        val capW = if (reqWidth > 0) minOf(reqWidth, boundedMaxDim) else boundedMaxDim
        val capH = if (reqHeight > 0) minOf(reqHeight, boundedMaxDim) else boundedMaxDim

        val wRatio = capW.toDouble() / srcWidth.toDouble()
        val hRatio = capH.toDouble() / srcHeight.toDouble()
        val doFillMin = fillMinSide && reqWidth > 0 && reqHeight > 0
        val scale = if (doFillMin) {
            val maxCapW = minOf(capW * PANORAMA_SIDE_MULT, boundedMaxDim)
            val maxCapH = minOf(capH * PANORAMA_SIDE_MULT, boundedMaxDim)
            val hardCap = minOf(
                1.0,
                maxCapW.toDouble() / srcWidth.toDouble(),
                maxCapH.toDouble() / srcHeight.toDouble(),
            )
            minOf(maxOf(wRatio, hRatio), hardCap)
        } else {
            minOf(1.0, wRatio, hRatio)
        }
        var targetW = (srcWidth * scale).roundToInt().coerceIn(1, boundedMaxDim)
        var targetH = (srcHeight * scale).roundToInt().coerceIn(1, boundedMaxDim)

        val pixelCount = targetW.toLong() * targetH.toLong()
        if (pixelCount > maxPixels && maxPixels > 0L) {
            val pixelScale = sqrt(maxPixels.toDouble() / pixelCount.toDouble())
            targetW = (targetW * pixelScale).toInt().coerceAtLeast(1)
            targetH = (targetH * pixelScale).toInt().coerceAtLeast(1)
        }
        return targetW to targetH
    }

    fun isVideoFileHint(pathOrName: String): Boolean = extensionOf(pathOrName) in VIDEO_EXTENSIONS

    fun hasAnimatedContainerHint(header: ByteArray): Boolean {
        return AnimatedHeaderSniffer.isAnimated(header)
    }
}

private object AnimatedHeaderSniffer {
    private const val PNG_CHUNK_HEADER_BYTES = 8
    private const val PNG_CRC_BYTES = 4
    private const val BYTE_3_OFFSET = 3
    private const val SHIFT_24 = 24
    private const val SHIFT_16 = 16
    private const val SHIFT_8 = 8
    private const val WEBP_VP8X_OFFSET = 12
    private const val WEBP_VP8X_FLAGS_OFFSET = 20
    private const val WEBP_VP8X_MIN_HEADER = 21
    private const val WEBP_ANIM_FLAG = 0x02

    fun isAnimated(header: ByteArray): Boolean {
        return when {
            isGifHeader(header) -> true
            matchesPrefix(header, PNG_MAGIC, MIN_PNG_HEADER) -> hasApngActlChunk(header)
            isWebpHeader(header) -> isAnimatedWebpHeader(header)
            else -> false
        }
    }

    private fun hasApngActlChunk(header: ByteArray): Boolean {
        var offset = MIN_PNG_HEADER
        var found = false
        var stop = false
        while (!stop && offset + PNG_CHUNK_HEADER_BYTES <= header.size) {
            val chunkLen = readBeInt(header, offset)
            val chunkType = asciiSlice(header, offset + BRAND_LENGTH, BRAND_LENGTH)
            val step = PNG_CHUNK_HEADER_BYTES.toLong() + chunkLen.toLong() + PNG_CRC_BYTES.toLong()
            val isTerminal = chunkType == "IDAT" || chunkType == "IEND"
            if (chunkType == "acTL" && chunkLen >= 0) {
                found = true
                stop = true
            } else if (chunkLen < 0 || isTerminal || step > (header.size - offset).toLong()) {
                stop = true
            } else {
                offset += step.toInt()
            }
        }
        return found
    }

    private fun isAnimatedWebpHeader(header: ByteArray): Boolean {
        if (header.size < WEBP_VP8X_MIN_HEADER) return false
        if (asciiSlice(header, WEBP_VP8X_OFFSET, BRAND_LENGTH) != "VP8X") return false
        val flags = header[WEBP_VP8X_FLAGS_OFFSET].toInt() and BYTE_MASK
        return (flags and WEBP_ANIM_FLAG) != 0
    }

    private fun readBeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and BYTE_MASK) shl SHIFT_24) or
            ((bytes[offset + 1].toInt() and BYTE_MASK) shl SHIFT_16) or
            ((bytes[offset + 2].toInt() and BYTE_MASK) shl SHIFT_8) or
            (bytes[offset + BYTE_3_OFFSET].toInt() and BYTE_MASK)
    }
}

private fun sniffBinaryFormat(header: ByteArray): IsolatedMediaFormat {
    return when {
        isJpegHeader(header) -> IsolatedMediaFormat.JPEG
        matchesPrefix(header, PNG_MAGIC, MIN_PNG_HEADER) -> IsolatedMediaFormat.PNG
        isGifHeader(header) -> IsolatedMediaFormat.GIF
        isWebpHeader(header) -> IsolatedMediaFormat.WEBP
        isJxlHeader(header) -> IsolatedMediaFormat.JXL
        else -> sniffIsoBmffFormat(header)
    }
}

private fun sniffIsoBmffFormat(header: ByteArray): IsolatedMediaFormat {
    if (header.size < MIN_FTYP_HEADER) return IsolatedMediaFormat.UNKNOWN
    val boxType = asciiSlice(header, FTYP_OFFSET, BRAND_LENGTH)
    if (boxType != "ftyp") return IsolatedMediaFormat.UNKNOWN
    val majorBrand = asciiSlice(header, BRAND_OFFSET, BRAND_LENGTH)
    return when (majorBrand) {
        in AVIF_BRANDS -> IsolatedMediaFormat.AVIF
        in HEIC_BRANDS -> IsolatedMediaFormat.HEIC
        else -> IsolatedMediaFormat.UNKNOWN
    }
}

private fun extensionOf(pathOrName: String): String {
    val clean = if (pathOrName.startsWith("content://")) {
        pathOrName.substringBefore('?').substringBefore('#')
    } else {
        pathOrName
    }
    val lastDot = clean.lastIndexOf('.')
    if (lastDot < 0 || lastDot == clean.lastIndex) return ""
    return clean.substring(lastDot + 1).lowercase(Locale.US)
}

private fun isJpegHeader(header: ByteArray): Boolean {
    if (header.size < MIN_JPEG_HEADER) return false
    val b0 = header[0].toInt() and BYTE_MASK
    val b1 = header[1].toInt() and BYTE_MASK
    val b2 = header[2].toInt() and BYTE_MASK
    return b0 == JPEG_MARKER_START && b1 == JPEG_MARKER_SOI && b2 == JPEG_MARKER_START
}

private fun isGifHeader(header: ByteArray): Boolean {
    if (header.size < MIN_GIF_HEADER) return false
    val tag = asciiSlice(header, 0, MIN_GIF_HEADER)
    return tag == "GIF87a" || tag == "GIF89a"
}

private fun isWebpHeader(header: ByteArray): Boolean {
    if (header.size < MIN_RIFF_HEADER) return false
    return asciiSlice(header, 0, BRAND_LENGTH) == "RIFF" &&
        asciiSlice(header, RIFF_WEBP_OFFSET, BRAND_LENGTH) == "WEBP"
}

private fun isJxlHeader(header: ByteArray): Boolean {
    val isCodestream = header.size >= JXL_CODESTREAM_LEN &&
        (header[0].toInt() and BYTE_MASK) == JPEG_MARKER_START &&
        (header[1].toInt() and BYTE_MASK) == JXL_MARKER_SECOND
    return isCodestream || matchesPrefix(header, JXL_CONTAINER_MAGIC, JXL_CONTAINER_LEN)
}

private fun isSvgPayload(header: ByteArray, nameHint: String): Boolean {
    if (header.isEmpty()) return false
    val slice = header.copyOfRange(0, minOf(header.size, SVG_SCAN_BYTES))
    val text = slice.decodeToString().trimStart('\uFEFF', ' ', '\t', '\r', '\n').lowercase(Locale.US)
    return text.startsWith("<svg") ||
        (text.startsWith("<?xml") && (text.contains("<svg") || extensionOf(nameHint) == "svg"))
}

private fun matchesPrefix(header: ByteArray, magic: ByteArray, length: Int): Boolean {
    if (header.size < length) return false
    for (i in 0 until length) {
        if (header[i] != magic[i]) return false
    }
    return true
}

private fun asciiSlice(bytes: ByteArray, offset: Int, length: Int): String {
    return bytes.copyOfRange(offset, offset + length).decodeToString()
}
