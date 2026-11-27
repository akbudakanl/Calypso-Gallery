package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

private const val ROTATION_90 = 90
private const val ROTATION_180 = 180
private const val ROTATION_270 = 270
private const val HEADER_SNIFF_BYTES = 512

private data class ProbedMetadata(
    val format: IsolatedMediaFormat,
    val width: Int,
    val height: Int,
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

            val meta = inspectBytes(bytes, request.nameHint)
                ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)

            val target = resolveTargetSize(meta, request)
                ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)

            encodeStaticResult(bytes, meta, target, request.flags)
        } catch (_: Throwable) {
            IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }
}

private fun resolveTargetSize(meta: ProbedMetadata, request: IsolatedDecodeRequest): Pair<Int, Int>? {
    val allowLarge = (request.flags and IsolatedDecodeRequest.FLAG_ALLOW_LARGE_OUTPUT) != 0
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
    return IsolatedFormatSniffer.computeBoundedTargetSize(
        srcWidth = meta.width,
        srcHeight = meta.height,
        reqWidth = request.maxWidth,
        reqHeight = request.maxHeight,
        maxDimension = maxDim,
        maxPixels = maxPixels,
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
        transformed = finalizeBitmap(decoded, if (applyExif) readExifOrientation(bytes) else 0, target)
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
    val rotation = readExifRotationDegrees(bytes)
    val dims: Pair<Int, Int>? = when (format) {
        IsolatedMediaFormat.JXL,
        IsolatedMediaFormat.WEBP,
        IsolatedMediaFormat.GIF -> NativeMediaDecoder.probeBounds(format, bytes)
        IsolatedMediaFormat.PNG,
        IsolatedMediaFormat.JPEG -> NativeMediaDecoder.probeBounds(format, bytes) ?: probePlatformBounds(bytes)
        IsolatedMediaFormat.AVIF -> probeAvifBounds(bytes)
        IsolatedMediaFormat.SVG -> probeSvgBounds(bytes)
        else -> probePlatformBounds(bytes)
    }
    return dims?.let { (w, h) -> ProbedMetadata(format, w, h, rotation) }
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

private fun finalizeBitmap(source: Bitmap, exifOrientation: Int, target: Pair<Int, Int>): Bitmap {
    val oriented = applyExifOrientation(source, exifOrientation)
    val (targetW, targetH) = target
    val scaled = if (oriented.width > targetW || oriented.height > targetH) {
        val scale = minOf(
            targetW.toFloat() / oriented.width.toFloat(),
            targetH.toFloat() / oriented.height.toFloat(),
        )
        val newW = (oriented.width * scale).roundToInt().coerceAtLeast(1)
        val newH = (oriented.height * scale).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(oriented, newW, newH, true)
    } else {
        oriented
    }
    if (scaled !== oriented && oriented !== source) {
        oriented.recycle()
    }
    return if (scaled.config == Bitmap.Config.ARGB_8888) {
        scaled
    } else {
        scaled.copy(Bitmap.Config.ARGB_8888, false).also {
            if (scaled !== source) scaled.recycle()
        }
    }
}

private fun readExifOrientation(bytes: ByteArray): Int {
    return runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
}

private fun readExifRotationDegrees(bytes: ByteArray): Int {
    return when (readExifOrientation(bytes)) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> ROTATION_90
        ExifInterface.ORIENTATION_ROTATE_180 -> ROTATION_180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> ROTATION_270
        else -> 0
    }
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    var modified = true
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(ROTATION_180.toFloat())
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(ROTATION_180.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(ROTATION_90.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(ROTATION_90.toFloat())
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(ROTATION_270.toFloat())
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(ROTATION_270.toFloat())
        else -> modified = false
    }
    if (!modified || matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
