package org.fossify.gallery.helpers.media

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.exifinterface.media.ExifInterface
import java.util.concurrent.Callable

private const val FD_HEADER_SNIFF_BYTES = 512

private val FD_STREAMABLE_FORMATS = setOf(
    IsolatedMediaFormat.JPEG,
    IsolatedMediaFormat.HEIC,
    IsolatedMediaFormat.AVIF,
    IsolatedMediaFormat.RAW,
    IsolatedMediaFormat.UNKNOWN,
)

/**
 * Handles >100 MiB RAW/TIFF/JPEG/HEIC inputs inside [IsolatedMediaDecoderService] directly from a
 * seekable [ParcelFileDescriptor] without reading the entire container into a [ByteArray].
 */
internal object IsolatedFdStreamingCodecs {
    fun shouldStreamFromPfd(request: IsolatedDecodeRequest): Boolean {
        return request.sharedInputHandle == null &&
            request.inputPfd != null &&
            request.inputLength > IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES
    }

    fun tryProbeBoundsFromSeekablePfd(request: IsolatedDecodeRequest): IsolatedBoundsResult? {
        if (request.sharedInputHandle != null || request.inputPfd == null) return null
        if (request.inputLength > IsolatedFormatSniffer.MAX_STREAMING_INPUT_BYTES) {
            return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
        }
        val pfd = request.inputPfd
        return try {
            val header = PfdStreamHelpers.readHeaderAtStart(pfd) ?: return null
            val format = IsolatedFormatSniffer.sniffFormat(header, request.nameHint)
            when {
                !isFormatAllowed(format, request.flags) -> IsolatedBoundsResult(IsolatedDecodeResult.STATUS_UNSUPPORTED)
                format in FD_STREAMABLE_FORMATS -> probeVerifiedBounds(pfd, format)
                request.inputLength > IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES ->
                    IsolatedBoundsResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
                else -> null
            }
        } catch (_: Throwable) {
            IsolatedBoundsResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    fun tryDecodeStaticFromSeekablePfd(request: IsolatedDecodeRequest): IsolatedDecodeResult? {
        if (request.sharedInputHandle != null || request.inputPfd == null) return null
        if (request.inputLength > IsolatedFormatSniffer.MAX_STREAMING_INPUT_BYTES) {
            return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
        }
        val pfd = request.inputPfd
        return try {
            val header = PfdStreamHelpers.readHeaderAtStart(pfd) ?: return null
            val format = IsolatedFormatSniffer.sniffFormat(header, request.nameHint)
            when {
                !isFormatAllowed(format, request.flags) -> IsolatedDecodeResult(IsolatedDecodeResult.STATUS_UNSUPPORTED)
                format in FD_STREAMABLE_FORMATS -> decodeVerifiedPfd(pfd, format, request)
                request.inputLength > IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES ->
                    IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
                else -> null
            }
        } catch (_: Throwable) {
            IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    fun probeBoundsFromPfd(request: IsolatedDecodeRequest): IsolatedBoundsResult {
        if (request.inputLength > IsolatedFormatSniffer.MAX_STREAMING_INPUT_BYTES) {
            return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
        }
        val pfd = request.inputPfd ?: return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
        return try {
            val header = PfdStreamHelpers.readHeaderAtStart(pfd)
            val format = header?.let { IsolatedFormatSniffer.sniffFormat(it, request.nameHint) }
            when {
                format == null -> IsolatedBoundsResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
                !isFormatAllowed(format, request.flags) -> IsolatedBoundsResult(IsolatedDecodeResult.STATUS_UNSUPPORTED)
                format !in FD_STREAMABLE_FORMATS -> IsolatedBoundsResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
                else -> probeVerifiedBounds(pfd, format)
            }
        } catch (_: Throwable) {
            IsolatedBoundsResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    fun decodeStaticFromPfd(request: IsolatedDecodeRequest): IsolatedDecodeResult {
        if (request.inputLength > IsolatedFormatSniffer.MAX_STREAMING_INPUT_BYTES) {
            return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
        }
        val pfd = request.inputPfd ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
        return try {
            val header = PfdStreamHelpers.readHeaderAtStart(pfd)
            val format = header?.let { IsolatedFormatSniffer.sniffFormat(it, request.nameHint) }
            when {
                format == null -> IsolatedDecodeResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
                !isFormatAllowed(format, request.flags) -> IsolatedDecodeResult(IsolatedDecodeResult.STATUS_UNSUPPORTED)
                format !in FD_STREAMABLE_FORMATS -> IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
                else -> decodeVerifiedPfd(pfd, format, request)
            }
        } catch (_: Throwable) {
            IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    private fun probeVerifiedBounds(pfd: ParcelFileDescriptor, format: IsolatedMediaFormat): IsolatedBoundsResult {
        val dims = PfdStreamHelpers.probePlatformBoundsFromPfd(pfd)
            ?: return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        if (!IsolatedFormatSniffer.isValidSourceDimensions(dims.first, dims.second)) {
            return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
        }
        val orientation = readExifOrientation(PfdStreamHelpers.readExifFromPfd(pfd, format))
        return IsolatedBoundsResult(
            status = IsolatedDecodeResult.STATUS_OK,
            width = dims.first,
            height = dims.second,
            rotationDegrees = orientationToDegrees(orientation),
        )
    }

    private fun decodeVerifiedPfd(
        pfd: ParcelFileDescriptor,
        format: IsolatedMediaFormat,
        request: IsolatedDecodeRequest,
    ): IsolatedDecodeResult {
        val dims = PfdStreamHelpers.probePlatformBoundsFromPfd(pfd)
            ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        val exif = PfdStreamHelpers.readExifFromPfd(pfd, format)
        val exifOrientation = readExifOrientation(exif)
        val target = PfdStreamHelpers.resolveStreamingTargetSize(dims.first, dims.second, exifOrientation, request)
            ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_TOO_LARGE)

        val applyExif = (request.flags and IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION) != 0
        val effectiveOrientation = if (applyExif) exifOrientation else ExifInterface.ORIENTATION_NORMAL
        val finalTarget = if (swapsAxesOrientation(effectiveOrientation)) {
            target.second to target.first
        } else {
            target
        }

        val decoded = PfdStreamHelpers.decodeBitmapFromPfd(pfd, format, dims, target, request.flags, exif)
            ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        return PfdStreamHelpers.finalizeAndPack(
            decoded = decoded,
            srcDims = dims,
            rotationDegrees = orientationToDegrees(exifOrientation),
            effectiveOrientation = effectiveOrientation,
            finalTarget = finalTarget,
            flags = request.flags,
        )
    }
}

private object PfdStreamHelpers {
    fun finalizeAndPack(
        decoded: Bitmap,
        srcDims: Pair<Int, Int>,
        rotationDegrees: Int,
        effectiveOrientation: Int,
        finalTarget: Pair<Int, Int>,
        flags: Int,
    ): IsolatedDecodeResult {
        var transformed: Bitmap? = null
        return try {
            transformed = finalizeIsolatedBitmap(decoded, effectiveOrientation, finalTarget)
            val packed = IsolatedPixelTransport.packBitmap("calypso-dec", transformed)
            if (packed == null) {
                IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
            } else {
                IsolatedDecodeResult(
                    status = IsolatedDecodeResult.STATUS_OK,
                    width = transformed.width,
                    height = transformed.height,
                    sourceWidth = srcDims.first,
                    sourceHeight = srcDims.second,
                    rotationDegrees = rotationDegrees,
                    byteCount = packed.byteCount,
                    sharedOutputHandle = packed.sharedOutputHandle,
                    pixelPipePfd = packed.pixelPipePfd,
                    colorSpaceId = IsolatedHdrTransport.extractColorSpaceId(transformed),
                    hdrPayload = IsolatedHdrTransport.packGainmapIfRequested(transformed, flags),
                )
            }
        } finally {
            if (transformed != null && transformed !== decoded && !transformed.isRecycled) {
                transformed.recycle()
            }
            if (!decoded.isRecycled) {
                decoded.recycle()
            }
        }
    }

    fun decodeBitmapFromPfd(
        pfd: ParcelFileDescriptor,
        format: IsolatedMediaFormat,
        srcDims: Pair<Int, Int>,
        target: Pair<Int, Int>,
        flags: Int,
        exif: ExifInterface?,
    ): Bitmap? {
        if (format == IsolatedMediaFormat.RAW && (flags and IsolatedDecodeRequest.FLAG_PREFER_EMBEDDED_PREVIEW) != 0) {
            decodeEmbeddedPreview(exif, target)?.let { return it }
        }
        val sampleSize = IsolatedFormatSniffer.computeSampleSize(
            srcDims.first,
            srcDims.second,
            target.first,
            target.second,
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val factoryBmp = if (rewindPfd(pfd)) {
            runCatching { BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts) }.getOrNull()
        } else {
            null
        }
        return factoryBmp ?: decodeImageDecoderFromPfd(pfd, target.first, target.second)
    }

    private fun decodeEmbeddedPreview(exif: ExifInterface?, target: Pair<Int, Int>): Bitmap? {
        val thumbBytes = runCatching { exif?.thumbnailBytes }.getOrNull()
        if (thumbBytes == null || thumbBytes.isEmpty()) return null
        val thumbDims = probePlatformBounds(thumbBytes) ?: return null
        return decodePlatform(thumbBytes, thumbDims.first, thumbDims.second, target.first, target.second)
    }

    private fun decodeImageDecoderFromPfd(pfd: ParcelFileDescriptor, targetW: Int, targetH: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            val source = ImageDecoder.createSource(
                Callable {
                    val dup = ParcelFileDescriptor.dup(pfd.fileDescriptor)
                    rewindPfd(dup)
                    AssetFileDescriptor(dup, 0L, AssetFileDescriptor.UNKNOWN_LENGTH)
                },
            )
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val size = info.size
                if (size.width > 0 && size.height > 0) {
                    IsolatedFormatSniffer.computeBoundedTargetSize(
                        size.width,
                        size.height,
                        targetW,
                        targetH,
                    )?.let { bounded ->
                        decoder.setTargetSize(bounded.first, bounded.second)
                    }
                }
            }
        }.getOrNull()
    }

    fun readHeaderAtStart(pfd: ParcelFileDescriptor): ByteArray? {
        if (!rewindPfd(pfd)) return null
        return runCatching {
            val buf = ByteArray(FD_HEADER_SNIFF_BYTES)
            var total = 0
            while (total < buf.size) {
                val read = Os.pread(pfd.fileDescriptor, buf, total, buf.size - total, total.toLong())
                if (read <= 0) break
                total += read
            }
            if (total > 0) buf.copyOf(total) else null
        }.getOrNull()
    }

    fun readExifFromPfd(pfd: ParcelFileDescriptor, format: IsolatedMediaFormat): ExifInterface? {
        if (format == IsolatedMediaFormat.GIF || format == IsolatedMediaFormat.SVG) return null
        if (!rewindPfd(pfd)) return null
        return runCatching {
            ExifInterface(pfd.fileDescriptor).also { rewindPfd(pfd) }
        }.getOrNull()
    }

    fun probePlatformBoundsFromPfd(pfd: ParcelFileDescriptor): Pair<Int, Int>? {
        if (!rewindPfd(pfd)) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts) }
        rewindPfd(pfd)
        return if (opts.outWidth > 0 && opts.outHeight > 0) {
            opts.outWidth to opts.outHeight
        } else {
            null
        }
    }

    fun resolveStreamingTargetSize(
        srcW: Int,
        srcH: Int,
        exifOrientation: Int,
        request: IsolatedDecodeRequest,
    ): Pair<Int, Int>? {
        val allowLarge = (request.flags and IsolatedDecodeRequest.FLAG_ALLOW_LARGE_OUTPUT) != 0
        val applyExif = (request.flags and IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION) != 0
        val fillMinSide = (request.flags and IsolatedDecodeRequest.FLAG_CENTER_CROP) != 0
        val maxPixels = if (allowLarge) {
            IsolatedPixelTransport.MAX_TRANSFER_PIXELS
        } else {
            IsolatedFormatSniffer.MAX_OUTPUT_PIXELS
        }
        val swapsAxes = applyExif && swapsAxesOrientation(exifOrientation)
        val reqW = if (swapsAxes) request.maxHeight else request.maxWidth
        val reqH = if (swapsAxes) request.maxWidth else request.maxHeight
        return IsolatedFormatSniffer.computeBoundedTargetSize(
            srcWidth = srcW,
            srcHeight = srcH,
            reqWidth = reqW,
            reqHeight = reqH,
            maxDimension = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
            maxPixels = maxPixels,
            fillMinSide = fillMinSide,
        )
    }

    private fun rewindPfd(pfd: ParcelFileDescriptor): Boolean {
        return runCatching {
            Os.lseek(pfd.fileDescriptor, 0L, OsConstants.SEEK_SET)
            true
        }.getOrDefault(false)
    }
}
