package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import kotlin.math.abs

private const val HEADER_SNIFF_BYTES = 512
private const val MIN_EXIF_THUMB_SIDE = 160
private const val THUMB_MIN_RATIO_NUM = 2
private const val THUMB_MIN_RATIO_DEN = 3
private const val EXIF_ASPECT_TOLERANCE = 0.08

private val RUST_STATIC_FORMATS = setOf(
    IsolatedMediaFormat.JPEG,
    IsolatedMediaFormat.PNG,
    IsolatedMediaFormat.WEBP,
    IsolatedMediaFormat.GIF,
    IsolatedMediaFormat.JXL,
)

private data class ProbedMetadata(
    val format: IsolatedMediaFormat,
    val width: Int,
    val height: Int,
    val exifOrientation: Int,
    val rotationDegrees: Int,
)

/**
 * Executes all native/third-party image decoding exclusively inside [IsolatedMediaDecoderService].
 */
object IsolatedMediaDecoders {
    fun probeBounds(request: IsolatedDecodeRequest): IsolatedBoundsResult {
        return try {
            val bytes = IsolatedPixelTransport.unpackInputBytes(
                request,
                IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES,
            ) ?: return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)

            val meta = inspectBytes(bytes, request.nameHint)
                ?: return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)

            if (!IsolatedFormatSniffer.isValidSourceDimensions(meta.width, meta.height)) {
                return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
            }
            IsolatedBoundsResult(
                status = IsolatedDecodeResult.STATUS_OK,
                width = meta.width,
                height = meta.height,
                rotationDegrees = meta.rotationDegrees,
            )
        } catch (_: Throwable) {
            IsolatedBoundsResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    fun decodeStatic(request: IsolatedDecodeRequest): IsolatedDecodeResult {
        return try {
            val bytes = IsolatedPixelTransport.unpackInputBytes(
                request,
                IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES,
            ) ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)

            decodeStaticBytes(bytes, request)
        } catch (_: Throwable) {
            IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }
}

private fun decodeStaticBytes(bytes: ByteArray, request: IsolatedDecodeRequest): IsolatedDecodeResult {
    if (bytes.isEmpty()) return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
    val header = bytes.copyOfRange(0, minOf(bytes.size, HEADER_SNIFF_BYTES))
    val format = IsolatedFormatSniffer.sniffFormat(header, request.nameHint)
    val exif = parseExifOnce(bytes, format)
    val exifOrientation = readExifOrientation(exif)
    val rotationDegrees = orientationToDegrees(exifOrientation)

    if (format in RUST_STATIC_FORMATS) {
        tryFastRustStaticDecode(bytes, format, exif, exifOrientation, rotationDegrees, request)?.let {
            return it
        }
    }

    val dims = probeFormatBounds(format, bytes)
        ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
    val meta = ProbedMetadata(format, dims.first, dims.second, exifOrientation, rotationDegrees)
    val target = resolveTargetSize(meta, request)
        ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
    return encodeStaticResult(bytes, meta, target, request.flags)
}

private fun tryFastRustStaticDecode(
    bytes: ByteArray,
    format: IsolatedMediaFormat,
    exif: ExifInterface?,
    exifOrientation: Int,
    rotationDegrees: Int,
    request: IsolatedDecodeRequest,
): IsolatedDecodeResult? {
    val flags = request.flags
    val applyExif = (flags and IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION) != 0
    val fillMinSide = (flags and IsolatedDecodeRequest.FLAG_CENTER_CROP) != 0
    val effectiveExif = if (applyExif) exifOrientation else ExifInterface.ORIENTATION_NORMAL
    val preferThumb = (flags and IsolatedDecodeRequest.FLAG_PREFER_EMBEDDED_PREVIEW) != 0
    val hasRequestedBounds = request.maxWidth > 0 && request.maxHeight > 0

    if (format == IsolatedMediaFormat.JPEG && preferThumb && hasRequestedBounds) {
        tryDecodeJpegEmbeddedPreview(
            bytes = bytes,
            exif = exif,
            effectiveExif = effectiveExif,
            rotationDegrees = rotationDegrees,
            reqW = request.maxWidth,
            reqH = request.maxHeight,
            fillMinSide = fillMinSide,
        )?.let { return it }
    }

    val decoded = NativeMediaDecoder.decodeStaticRgba(
        format = format,
        bytes = bytes,
        targetW = request.maxWidth,
        targetH = request.maxHeight,
        exifOrientation = effectiveExif,
        fillMinSide = fillMinSide,
    ) ?: return null
    val packed = IsolatedPixelTransport.packRgbaBytes("calypso-dec", decoded.rgba) ?: return null
    return IsolatedDecodeResult(
        status = IsolatedDecodeResult.STATUS_OK,
        width = decoded.width,
        height = decoded.height,
        sourceWidth = decoded.sourceWidth,
        sourceHeight = decoded.sourceHeight,
        rotationDegrees = rotationDegrees,
        byteCount = packed.byteCount,
        sharedOutputHandle = packed.sharedOutputHandle,
        pixelPipePfd = packed.pixelPipePfd,
    )
}

private fun tryDecodeJpegEmbeddedPreview(
    bytes: ByteArray,
    exif: ExifInterface?,
    effectiveExif: Int,
    rotationDegrees: Int,
    reqW: Int,
    reqH: Int,
    fillMinSide: Boolean,
): IsolatedDecodeResult? {
    val thumbBytes = runCatching { exif?.thumbnailBytes }.getOrNull()?.takeIf { it.isNotEmpty() }
    val thumbDims = thumbBytes?.let { IsolatedJpegHeaderParser.probeDimensions(it) }
    val fullDims = if (thumbDims != null) IsolatedJpegHeaderParser.probeDimensions(bytes) else null
    val thumbExif = if (thumbDims != null && fullDims != null) {
        resolveEmbeddedJpegOrientation(thumbDims, fullDims, reqW, reqH, effectiveExif)
    } else {
        null
    }
    val decoded = if (thumbBytes != null && thumbExif != null) {
        NativeMediaDecoder.decodeStaticRgba(
            format = IsolatedMediaFormat.JPEG,
            bytes = thumbBytes,
            targetW = reqW,
            targetH = reqH,
            exifOrientation = thumbExif,
            fillMinSide = fillMinSide,
        )
    } else {
        null
    }
    val packed = decoded?.let { IsolatedPixelTransport.packRgbaBytes("calypso-dec", it.rgba) }
    return if (decoded != null && fullDims != null && packed != null) {
        IsolatedDecodeResult(
            status = IsolatedDecodeResult.STATUS_OK,
            width = decoded.width,
            height = decoded.height,
            sourceWidth = fullDims.first,
            sourceHeight = fullDims.second,
            rotationDegrees = rotationDegrees,
            byteCount = packed.byteCount,
            sharedOutputHandle = packed.sharedOutputHandle,
            pixelPipePfd = packed.pixelPipePfd,
        )
    } else {
        null
    }
}

private fun resolveEmbeddedJpegOrientation(
    thumbDims: Pair<Int, Int>,
    fullDims: Pair<Int, Int>,
    reqW: Int,
    reqH: Int,
    effectiveExif: Int,
): Int? {
    val minReq = (minOf(reqW, reqH) * THUMB_MIN_RATIO_NUM / THUMB_MIN_RATIO_DEN).coerceAtLeast(MIN_EXIF_THUMB_SIDE)
    if (minOf(thumbDims.first, thumbDims.second) < minReq) return null
    val fullRatio = fullDims.first.toDouble() / fullDims.second.toDouble()
    val thumbRatio = thumbDims.first.toDouble() / thumbDims.second.toDouble()
    val matchesNormal = abs(thumbRatio - fullRatio) <= EXIF_ASPECT_TOLERANCE
    val matchesSwapped = abs((1.0 / thumbRatio) - fullRatio) <= EXIF_ASPECT_TOLERANCE
    return when {
        matchesSwapped -> ExifInterface.ORIENTATION_NORMAL
        matchesNormal -> effectiveExif
        else -> null
    }
}

private fun resolveTargetSize(meta: ProbedMetadata, request: IsolatedDecodeRequest): Pair<Int, Int>? {
    val allowLarge = (request.flags and IsolatedDecodeRequest.FLAG_ALLOW_LARGE_OUTPUT) != 0
    val applyExif = (request.flags and IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION) != 0
    val fillMinSide = (request.flags and IsolatedDecodeRequest.FLAG_CENTER_CROP) != 0
    val maxDim = if (meta.format == IsolatedMediaFormat.SVG) {
        IsolatedFormatSniffer.MAX_SVG_DIMENSION
    } else {
        IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION
    }
    val maxPixels = if (allowLarge) {
        IsolatedFormatSniffer.MAX_OUTPUT_PIXELS * 2
    } else {
        IsolatedFormatSniffer.MAX_OUTPUT_PIXELS
    }
    val swapsAxes = applyExif && swapsAxesOrientation(meta.exifOrientation)
    val reqW = if (swapsAxes) request.maxHeight else request.maxWidth
    val reqH = if (swapsAxes) request.maxWidth else request.maxHeight
    return IsolatedFormatSniffer.computeBoundedTargetSize(
        srcWidth = meta.width,
        srcHeight = meta.height,
        reqWidth = reqW,
        reqHeight = reqH,
        maxDimension = maxDim,
        maxPixels = maxPixels,
        fillMinSide = fillMinSide,
    )
}

private fun encodeStaticResult(
    bytes: ByteArray,
    meta: ProbedMetadata,
    target: Pair<Int, Int>,
    flags: Int,
): IsolatedDecodeResult {
    var decoded: Bitmap? = null
    var transformed: Bitmap? = null
    return try {
        decoded = decodeBitmapForFormat(bytes, meta, target.first, target.second, flags)
            ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)

        val applyExif = (flags and IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION) != 0
        val effectiveOrientation = if (applyExif) meta.exifOrientation else ExifInterface.ORIENTATION_NORMAL
        val finalTarget = if (swapsAxesOrientation(effectiveOrientation)) {
            target.second to target.first
        } else {
            target
        }
        transformed = finalizeIsolatedBitmap(decoded, effectiveOrientation, finalTarget)
        val packed = IsolatedPixelTransport.packBitmap("calypso-dec", transformed)
            ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)

        IsolatedDecodeResult(
            status = IsolatedDecodeResult.STATUS_OK,
            width = transformed.width,
            height = transformed.height,
            sourceWidth = meta.width,
            sourceHeight = meta.height,
            rotationDegrees = meta.rotationDegrees,
            byteCount = packed.byteCount,
            sharedOutputHandle = packed.sharedOutputHandle,
            pixelPipePfd = packed.pixelPipePfd,
        )
    } finally {
        if (transformed != null && transformed !== decoded && !transformed.isRecycled) {
            transformed.recycle()
        }
        if (decoded != null && !decoded.isRecycled) {
            decoded.recycle()
        }
    }
}

private fun inspectBytes(bytes: ByteArray, nameHint: String): ProbedMetadata? {
    if (bytes.isEmpty()) return null
    val header = bytes.copyOfRange(0, minOf(bytes.size, HEADER_SNIFF_BYTES))
    val format = IsolatedFormatSniffer.sniffFormat(header, nameHint)
    val exif = parseExifOnce(bytes, format)
    val orientation = readExifOrientation(exif)
    val rotation = orientationToDegrees(orientation)
    val dims = probeFormatBounds(format, bytes)
    return dims?.let { (w, h) -> ProbedMetadata(format, w, h, orientation, rotation) }
}

private fun probeFormatBounds(format: IsolatedMediaFormat, bytes: ByteArray): Pair<Int, Int>? {
    return when (format) {
        IsolatedMediaFormat.JXL,
        IsolatedMediaFormat.WEBP,
        IsolatedMediaFormat.GIF -> NativeMediaDecoder.probeBounds(format, bytes)
        IsolatedMediaFormat.PNG,
        IsolatedMediaFormat.JPEG -> NativeMediaDecoder.probeBounds(format, bytes) ?: probePlatformBounds(bytes)
        IsolatedMediaFormat.AVIF -> probeAvifBounds(bytes)
        IsolatedMediaFormat.SVG -> probeSvgBounds(bytes)
        else -> probePlatformBounds(bytes)
    }
}

private fun decodeBitmapForFormat(
    bytes: ByteArray,
    meta: ProbedMetadata,
    targetW: Int,
    targetH: Int,
    flags: Int,
): Bitmap? {
    return when (meta.format) {
        IsolatedMediaFormat.WEBP,
        IsolatedMediaFormat.GIF,
        IsolatedMediaFormat.JXL -> NativeMediaDecoder.decodeStatic(meta.format, bytes, targetW, targetH)
        IsolatedMediaFormat.PNG,
        IsolatedMediaFormat.JPEG -> NativeMediaDecoder.decodeStatic(meta.format, bytes, targetW, targetH)
            ?: decodePlatform(bytes, meta.width, meta.height, targetW, targetH)
        IsolatedMediaFormat.AVIF -> decodeAvif(bytes, targetW, targetH)
        IsolatedMediaFormat.SVG -> decodeSvg(bytes, targetW, targetH)
        IsolatedMediaFormat.RAW -> decodeRaw(bytes, meta.width, meta.height, targetW, targetH, flags)
        else -> decodePlatform(bytes, meta.width, meta.height, targetW, targetH)
    }
}
