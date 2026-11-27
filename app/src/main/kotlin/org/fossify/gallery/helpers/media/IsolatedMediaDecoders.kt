package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface

private const val HEADER_SNIFF_BYTES = 512

private val RUST_STATIC_FORMATS = setOf(
    IsolatedMediaFormat.PNG,
    IsolatedMediaFormat.WEBP,
    IsolatedMediaFormat.GIF,
    IsolatedMediaFormat.JXL,
)

private val PLATFORM_DEFER_ALLOWED_FORMATS = setOf(
    IsolatedMediaFormat.PNG,
    IsolatedMediaFormat.WEBP,
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
        if (IsolatedFdStreamingCodecs.shouldStreamFromPfd(request)) {
            return IsolatedFdStreamingCodecs.probeBoundsFromPfd(request)
        }
        return try {
            val bytes = IsolatedPixelTransport.unpackInputBytes(
                request,
                IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES,
            ) ?: return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
            probeBoundsBytes(bytes, request)
        } catch (_: Throwable) {
            IsolatedBoundsResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    fun decodeStatic(request: IsolatedDecodeRequest): IsolatedDecodeResult {
        if (IsolatedFdStreamingCodecs.shouldStreamFromPfd(request)) {
            return IsolatedFdStreamingCodecs.decodeStaticFromPfd(request)
        }
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

internal fun isFormatAllowed(format: IsolatedMediaFormat, flags: Int): Boolean {
    return format != IsolatedMediaFormat.SVG || (flags and IsolatedDecodeRequest.FLAG_ALLOW_SVG) != 0
}

private fun probeBoundsBytes(bytes: ByteArray, request: IsolatedDecodeRequest): IsolatedBoundsResult {
    if (bytes.isEmpty()) return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
    val header = bytes.copyOfRange(0, minOf(bytes.size, HEADER_SNIFF_BYTES))
    val format = IsolatedFormatSniffer.sniffFormat(header, request.nameHint)
    if (!isFormatAllowed(format, request.flags)) {
        return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_UNSUPPORTED)
    }
    val dims = probeFormatBounds(format, bytes)
        ?: return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
    val orientation = readExifOrientation(parseExifOnce(bytes, format))
    val rotation = orientationToDegrees(orientation)
    return if (!IsolatedFormatSniffer.isValidSourceDimensions(dims.first, dims.second)) {
        IsolatedBoundsResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
    } else {
        IsolatedBoundsResult(
            status = IsolatedDecodeResult.STATUS_OK,
            width = dims.first,
            height = dims.second,
            rotationDegrees = rotation,
        )
    }
}

private fun decodeStaticBytes(bytes: ByteArray, request: IsolatedDecodeRequest): IsolatedDecodeResult {
    if (bytes.isEmpty()) return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
    val header = bytes.copyOfRange(0, minOf(bytes.size, HEADER_SNIFF_BYTES))
    val format = IsolatedFormatSniffer.sniffFormat(header, request.nameHint)
    if (!isFormatAllowed(format, request.flags)) {
        return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_UNSUPPORTED)
    }
    return decodeAllowedStaticBytes(bytes, format, request)
}

private fun decodeAllowedStaticBytes(
    bytes: ByteArray,
    format: IsolatedMediaFormat,
    request: IsolatedDecodeRequest,
): IsolatedDecodeResult {
    val exif = parseExifOnce(bytes, format)
    val exifOrientation = readExifOrientation(exif)
    val rotationDegrees = orientationToDegrees(exifOrientation)

    if (format in RUST_STATIC_FORMATS) {
        val outcome = tryFastRustStaticDecode(bytes, format, exifOrientation, rotationDegrees, request)
        val canDefer = outcome is RustFastOutcome.DeferToPlatform && format in PLATFORM_DEFER_ALLOWED_FORMATS
        if (!canDefer) {
            return (outcome as? RustFastOutcome.Success)?.result
                ?: IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    return decodeViaPlatformPath(bytes, format, exifOrientation, rotationDegrees, request)
}

private fun decodeViaPlatformPath(
    bytes: ByteArray,
    format: IsolatedMediaFormat,
    exifOrientation: Int,
    rotationDegrees: Int,
    request: IsolatedDecodeRequest,
): IsolatedDecodeResult {
    val dims = probeFormatBounds(format, bytes)
        ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
    val meta = ProbedMetadata(format, dims.first, dims.second, exifOrientation, rotationDegrees)
    val target = resolveTargetSize(meta, request)
        ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
    return encodeStaticResult(bytes, meta, target, request.flags)
}

private sealed class RustFastOutcome {
    data class Success(val result: IsolatedDecodeResult) : RustFastOutcome()
    data object DeferToPlatform : RustFastOutcome()
    data object Failed : RustFastOutcome()
}

private fun tryFastRustStaticDecode(
    bytes: ByteArray,
    format: IsolatedMediaFormat,
    exifOrientation: Int,
    rotationDegrees: Int,
    request: IsolatedDecodeRequest,
): RustFastOutcome {
    val flags = request.flags
    val applyExif = (flags and IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION) != 0
    val fillMinSide = (flags and IsolatedDecodeRequest.FLAG_CENTER_CROP) != 0
    val effectiveExif = if (applyExif) exifOrientation else ExifInterface.ORIENTATION_NORMAL

    val decoded = when (
        val outcome = NativeMediaDecoder.decodeStaticRgba(
            format = format,
            bytes = bytes,
            targetW = request.maxWidth,
            targetH = request.maxHeight,
            exifOrientation = effectiveExif,
            fillMinSide = fillMinSide,
        )
    ) {
        is RustStaticDecodeOutcome.Decoded -> outcome.data
        is RustStaticDecodeOutcome.DeferToPlatform -> return RustFastOutcome.DeferToPlatform
        is RustStaticDecodeOutcome.Failed -> return RustFastOutcome.Failed
    }
    val packed = IsolatedPixelTransport.packRgbaBytes("calypso-dec", decoded.rgba)
        ?: return RustFastOutcome.Failed
    return RustFastOutcome.Success(
        IsolatedDecodeResult(
            status = IsolatedDecodeResult.STATUS_OK,
            width = decoded.width,
            height = decoded.height,
            sourceWidth = decoded.sourceWidth,
            sourceHeight = decoded.sourceHeight,
            rotationDegrees = rotationDegrees,
            byteCount = packed.byteCount,
            sharedOutputHandle = packed.sharedOutputHandle,
            pixelPipePfd = packed.pixelPipePfd,
        ),
    )
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
        val colorSpaceId = IsolatedHdrTransport.extractColorSpaceId(transformed)
        val hdrPayload = IsolatedHdrTransport.packGainmapIfRequested(transformed, flags)

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
            colorSpaceId = colorSpaceId,
            hdrPayload = hdrPayload,
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

private fun probeFormatBounds(format: IsolatedMediaFormat, bytes: ByteArray): Pair<Int, Int>? {
    return when (format) {
        IsolatedMediaFormat.PNG,
        IsolatedMediaFormat.WEBP,
        IsolatedMediaFormat.JXL,
        IsolatedMediaFormat.GIF -> NativeMediaDecoder.probeBounds(format, bytes)
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
        IsolatedMediaFormat.GIF,
        IsolatedMediaFormat.JXL -> NativeMediaDecoder.decodeStatic(meta.format, bytes, targetW, targetH)
        IsolatedMediaFormat.AVIF -> decodeAvif(bytes, targetW, targetH)
        IsolatedMediaFormat.SVG -> {
            if ((flags and IsolatedDecodeRequest.FLAG_ALLOW_SVG) != 0) {
                decodeSvg(bytes, targetW, targetH)
            } else {
                null
            }
        }
        IsolatedMediaFormat.RAW -> decodeRaw(bytes, meta.width, meta.height, targetW, targetH, flags)
        else -> decodePlatform(bytes, meta.width, meta.height, targetW, targetH)
    }
}
