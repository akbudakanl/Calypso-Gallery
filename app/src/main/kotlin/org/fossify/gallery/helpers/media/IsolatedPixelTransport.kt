package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.SharedMemory
import android.system.OsConstants
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Transfers encoded input bytes and decoded software ARGB_8888 pixel buffers across the
 * `isolatedProcess="true"` Binder boundary without touching GPU/gralloc HALs.
 */
object IsolatedPixelTransport {
    private const val BYTES_PER_PIXEL = 4L
    private const val PIPE_CHUNK_BYTES = 64 * 1024
    private const val MAX_TRANSFER_DIM = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION
    internal const val MAX_TRANSFER_PIXELS = IsolatedFormatSniffer.MAX_OUTPUT_PIXELS * 2L
    private const val MAX_TRANSFER_BYTES = MAX_TRANSFER_PIXELS * BYTES_PER_PIXEL

    data class PackedPixels(
        val byteCount: Int,
        val sharedOutputHandle: Parcelable? = null,
        val pixelPipePfd: ParcelFileDescriptor? = null,
    )

    fun packInputBytes(name: String, bytes: ByteArray): Parcelable? {
        if (bytes.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            SharedMemoryTransportApi27.writeBytes(name, bytes)
        } else {
            null
        }
    }

    fun packInputStream(name: String, input: InputStream, exactLength: Long): Parcelable? {
        val maxInMemory = IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES.toLong()
        if (exactLength !in 1..maxInMemory || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return null
        }
        return SharedMemoryTransportApi27.writeStream(name, input, exactLength.toInt())
    }

    fun packDirectRgba(name: String, byteCount: Int, writer: (ByteBuffer) -> Boolean): PackedPixels? {
        if (byteCount <= 0 || byteCount.toLong() > MAX_TRANSFER_BYTES) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val handle = SharedMemoryTransportApi27.writeDirect(name, byteCount, writer) ?: return null
        return PackedPixels(byteCount = byteCount, sharedOutputHandle = handle)
    }

    fun packInputPipe(bytes: ByteArray): ParcelFileDescriptor? {
        if (bytes.isEmpty()) return null
        return PipeTransport.writeBytesToPipe(bytes)
    }

    fun unpackInputBytes(request: IsolatedDecodeRequest, maxBytes: Int): ByteArray? {
        val sharedHandle = request.sharedInputHandle
        if (sharedHandle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return SharedMemoryTransportApi27.readBytes(sharedHandle, maxBytes)
        }
        val pfd = request.inputPfd ?: return null
        return PipeTransport.readBoundedFromPfd(pfd, maxBytes, request.inputLength)
    }

    fun packRgbaBytes(name: String, rgba: ByteArray): PackedPixels? {
        if (rgba.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val handle = SharedMemoryTransportApi27.writeBytes(name, rgba) ?: return null
            PackedPixels(byteCount = rgba.size, sharedOutputHandle = handle)
        } else {
            val pfd = PipeTransport.writeBytesToPipe(rgba) ?: return null
            PackedPixels(byteCount = rgba.size, pixelPipePfd = pfd)
        }
    }

    fun packBitmap(name: String, bitmap: Bitmap): PackedPixels? {
        val software = ensureSoftwareArgb8888Bitmap(bitmap) ?: return null
        return try {
            val byteCount = software.byteCount
            if (byteCount <= 0) return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val handle = SharedMemoryTransportApi27.writeBitmap(name, software) ?: return null
                PackedPixels(byteCount = byteCount, sharedOutputHandle = handle)
            } else {
                val pfd = PipeTransport.writeBitmapToPipe(software) ?: return null
                PackedPixels(byteCount = byteCount, pixelPipePfd = pfd)
            }
        } finally {
            if (software !== bitmap) {
                software.recycle()
            }
        }
    }

    internal fun isValidTransferDimensions(width: Int, height: Int, byteCount: Int): Boolean {
        if (width !in 1..MAX_TRANSFER_DIM || height !in 1..MAX_TRANSFER_DIM) return false
        val pixels = width.toLong() * height.toLong()
        if (pixels > MAX_TRANSFER_PIXELS) return false
        return byteCount > 0 && byteCount.toLong() == pixels * BYTES_PER_PIXEL
    }

    fun unpackBitmap(
        result: IsolatedDecodeResult,
        bitmapSupplier: ((Int, Int) -> Bitmap?)? = null,
    ): Bitmap? {
        val width = result.width
        val height = result.height
        val expectedBytes = result.byteCount
        if (!isValidTransferDimensions(width, height, expectedBytes)) return null

        val colorSpace = IsolatedHdrTransport.resolveColorSpace(result.colorSpaceId)
        val allowPoolReuse = result.colorSpaceId == IsolatedHdrTransport.COLOR_SPACE_SRGB &&
            result.hdrPayload == null
        val effectiveSupplier = if (allowPoolReuse) bitmapSupplier else null
        val sharedHandle = result.sharedOutputHandle
        val bitmap = when {
            sharedHandle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
                SharedMemoryTransportApi27.readBitmap(
                    handle = sharedHandle,
                    width = width,
                    height = height,
                    expectedByteCount = expectedBytes,
                    colorSpace = colorSpace,
                    bitmapSupplier = effectiveSupplier,
                )
            result.pixelPipePfd != null ->
                PipeTransport.readBitmapFromPipe(
                    pfd = result.pixelPipePfd,
                    width = width,
                    height = height,
                    expectedByteCount = expectedBytes,
                    colorSpace = colorSpace,
                    bitmapSupplier = effectiveSupplier,
                )
            else -> null
        } ?: return null

        IsolatedHdrTransport.unpackAndAttachGainmap(bitmap, result)
        return bitmap
    }

    fun closeHandle(handle: Parcelable?) {
        if (handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            SharedMemoryTransportApi27.closeHandle(handle)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    internal object SharedMemoryTransportApi27 {
        fun writeBytes(name: String, bytes: ByteArray): Parcelable? {
            var shm: SharedMemory? = null
            var mapped: ByteBuffer? = null
            return try {
                shm = SharedMemory.create(name, bytes.size)
                mapped = shm.mapReadWrite()
                mapped.put(bytes)
                SharedMemory.unmap(mapped)
                mapped = null
                shm.setProtect(OsConstants.PROT_READ)
                shm
            } catch (_: Throwable) {
                if (mapped != null) runCatching { SharedMemory.unmap(mapped) }
                runCatching { shm?.close() }
                null
            }
        }

        fun readBytes(handle: Parcelable, maxBytes: Int): ByteArray? {
            val shm = handle as? SharedMemory ?: return null
            val size = shm.size
            if (size <= 0 || size > maxBytes) return null
            var mapped: ByteBuffer? = null
            return try {
                mapped = shm.mapReadOnly()
                ByteArray(size).also { out -> mapped.get(out) }
            } catch (_: Throwable) {
                null
            } finally {
                if (mapped != null) runCatching { SharedMemory.unmap(mapped) }
            }
        }

        fun writeBitmap(name: String, bitmap: Bitmap): Parcelable? {
            val byteCount = bitmap.byteCount
            var shm: SharedMemory? = null
            var mapped: ByteBuffer? = null
            return try {
                shm = SharedMemory.create(name, byteCount)
                mapped = shm.mapReadWrite()
                bitmap.copyPixelsToBuffer(mapped)
                SharedMemory.unmap(mapped)
                mapped = null
                shm.setProtect(OsConstants.PROT_READ)
                shm
            } catch (_: Throwable) {
                if (mapped != null) runCatching { SharedMemory.unmap(mapped) }
                runCatching { shm?.close() }
                null
            }
        }

        fun writeStream(name: String, input: InputStream, expectedLength: Int): Parcelable? {
            var shm: SharedMemory? = null
            var mapped: ByteBuffer? = null
            val scratch = ByteArray(PIPE_CHUNK_BYTES)
            return try {
                shm = SharedMemory.create(name, expectedLength)
                mapped = shm.mapReadWrite()
                var total = 0
                while (total < expectedLength) {
                    val toRead = minOf(scratch.size, expectedLength - total)
                    val read = input.read(scratch, 0, toRead)
                    if (read <= 0) break
                    mapped.put(scratch, 0, read)
                    total += read
                }
                if (total != expectedLength || input.read() >= 0) {
                    SharedMemory.unmap(mapped)
                    mapped = null
                    shm.close()
                    shm = null
                    null
                } else {
                    SharedMemory.unmap(mapped)
                    mapped = null
                    shm.setProtect(OsConstants.PROT_READ)
                    shm
                }
            } catch (_: Throwable) {
                if (mapped != null) runCatching { SharedMemory.unmap(mapped) }
                runCatching { shm?.close() }
                null
            } finally {
                scratch.fill(0)
            }
        }

        fun writeDirect(name: String, byteCount: Int, writer: (ByteBuffer) -> Boolean): Parcelable? {
            var shm: SharedMemory? = null
            var mapped: ByteBuffer? = null
            return try {
                shm = SharedMemory.create(name, byteCount)
                mapped = shm.mapReadWrite()
                val ok = writer(mapped)
                SharedMemory.unmap(mapped)
                mapped = null
                if (!ok) {
                    shm.close()
                    shm = null
                    null
                } else {
                    shm.setProtect(OsConstants.PROT_READ)
                    shm
                }
            } catch (_: Throwable) {
                if (mapped != null) runCatching { SharedMemory.unmap(mapped) }
                runCatching { shm?.close() }
                null
            }
        }

        @Suppress("LongParameterList")
        fun readBitmap(
            handle: Parcelable,
            width: Int,
            height: Int,
            expectedByteCount: Int,
            colorSpace: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB),
            bitmapSupplier: ((Int, Int) -> Bitmap?)? = null,
        ): Bitmap? {
            val shm = handle as? SharedMemory ?: return null
            if (shm.size < expectedByteCount || shm.size.toLong() > MAX_TRANSFER_BYTES) return null
            var mapped: ByteBuffer? = null
            return try {
                mapped = shm.mapReadOnly()
                if (mapped.remaining() < expectedByteCount) return null
                acquireOrCreatePixelBitmap(width, height, colorSpace, bitmapSupplier).apply {
                    copyPixelsFromBuffer(mapped)
                }
            } catch (_: Throwable) {
                null
            } finally {
                if (mapped != null) runCatching { SharedMemory.unmap(mapped) }
            }
        }

        fun closeHandle(handle: Parcelable) {
            runCatching { (handle as? SharedMemory)?.close() }
        }
    }

    internal object PipeTransport {
        fun writeBytesToPipe(bytes: ByteArray): ParcelFileDescriptor? {
            val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull() ?: return null
            val readSide = pipe[0]
            val writeSide = pipe[1]
            Thread({
                runCatching {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                        var offset = 0
                        while (offset < bytes.size) {
                            val chunk = minOf(PIPE_CHUNK_BYTES, bytes.size - offset)
                            out.write(bytes, offset, chunk)
                            offset += chunk
                        }
                    }
                }
            }, "calypso-ipc-pipe-bytes").apply { isDaemon = true }.start()
            return readSide
        }

        fun writeBitmapToPipe(bitmap: Bitmap): ParcelFileDescriptor? {
            val buffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
            return writeBytesToPipe(buffer.array())
        }

        fun readBoundedFromPfd(
            pfd: ParcelFileDescriptor,
            maxBytes: Int,
            expectedLength: Long = 0L,
        ): ByteArray? {
            return runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    if (expectedLength in 1..maxBytes.toLong()) {
                        readPreallocatedFromStream(input, expectedLength.toInt(), maxBytes)
                    } else {
                        readChunkedFromStream(input, maxBytes)
                    }
                }
            }.getOrNull()
        }

        private fun readPreallocatedFromStream(
            input: ParcelFileDescriptor.AutoCloseInputStream,
            expected: Int,
            maxBytes: Int,
        ): ByteArray? {
            val fixed = ByteArray(expected)
            var filled = 0
            while (filled < expected) {
                val read = input.read(fixed, filled, expected - filled)
                if (read <= 0) break
                filled += read
            }
            if (filled < expected) {
                return fixed.copyOf(filled)
            }
            val extraByte = input.read()
            if (extraByte < 0) {
                return fixed
            }
            if (expected + 1 > maxBytes) {
                return null
            }
            val out = ByteArrayOutputStream(expected + PIPE_CHUNK_BYTES)
            out.write(fixed, 0, expected)
            out.write(extraByte)
            return appendRemainingChunks(input, out, expected + 1, maxBytes)
        }

        private fun readChunkedFromStream(
            input: ParcelFileDescriptor.AutoCloseInputStream,
            maxBytes: Int,
        ): ByteArray? {
            return appendRemainingChunks(input, ByteArrayOutputStream(), 0, maxBytes)
        }

        private fun appendRemainingChunks(
            input: ParcelFileDescriptor.AutoCloseInputStream,
            out: ByteArrayOutputStream,
            initialTotal: Int,
            maxBytes: Int,
        ): ByteArray? {
            val buf = ByteArray(PIPE_CHUNK_BYTES)
            var total = initialTotal
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buf, 0, read)
            }
            return out.toByteArray()
        }

        @Suppress("LongParameterList")
        fun readBitmapFromPipe(
            pfd: ParcelFileDescriptor,
            width: Int,
            height: Int,
            expectedByteCount: Int,
            colorSpace: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB),
            bitmapSupplier: ((Int, Int) -> Bitmap?)? = null,
        ): Bitmap? {
            val bytes = readBoundedFromPfd(pfd, expectedByteCount) ?: return null
            if (bytes.size != expectedByteCount) return null
            return runCatching {
                acquireOrCreatePixelBitmap(width, height, colorSpace, bitmapSupplier).apply {
                    copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
                }
            }.getOrNull()
        }

        fun readRawPixelsIntoBitmap(pfd: ParcelFileDescriptor, target: Bitmap, expectedByteCount: Int): Boolean {
            val bytes = readBoundedFromPfd(pfd, expectedByteCount) ?: return false
            if (bytes.size != expectedByteCount || target.isRecycled || target.byteCount != expectedByteCount) {
                return false
            }
            return runCatching {
                target.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
                true
            }.getOrDefault(false)
        }
    }
}

private fun ensureSoftwareArgb8888Bitmap(bitmap: Bitmap): Bitmap? {
    if (bitmap.isRecycled) return null
    val needsCopy = bitmap.config != Bitmap.Config.ARGB_8888 ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE)
    return if (needsCopy) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
}

private fun acquireOrCreatePixelBitmap(
    width: Int,
    height: Int,
    colorSpace: ColorSpace,
    bitmapSupplier: ((Int, Int) -> Bitmap?)?,
): Bitmap {
    val reused = bitmapSupplier?.invoke(width, height)?.takeIf {
        !it.isRecycled &&
            it.isMutable &&
            it.config == Bitmap.Config.ARGB_8888 &&
            it.width == width &&
            it.height == height &&
            it.isPremultiplied
    }
    return reused ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, true, colorSpace)
}
