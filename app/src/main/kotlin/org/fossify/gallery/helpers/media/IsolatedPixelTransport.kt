package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.SharedMemory
import android.system.OsConstants
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Transfers encoded input bytes and decoded software ARGB_8888 pixel buffers across the
 * `isolatedProcess="true"` Binder boundary without touching GPU/gralloc HALs.
 */
object IsolatedPixelTransport {
    private const val BYTES_PER_PIXEL = 4L
    private const val PIPE_CHUNK_BYTES = 64 * 1024
    private const val MAX_TRANSFER_DIM = 16_384

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
        return PipeTransport.readBoundedFromPfd(pfd, maxBytes)
    }

    fun packBitmap(name: String, bitmap: Bitmap): PackedPixels? {
        val software = ensureSoftwareArgb8888(bitmap) ?: return null
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

    fun unpackBitmap(result: IsolatedDecodeResult): Bitmap? {
        val width = result.width
        val height = result.height
        val expectedBytes = result.byteCount
        val validDimensions = width in 1..MAX_TRANSFER_DIM && height in 1..MAX_TRANSFER_DIM
        val validSize = validDimensions && expectedBytes.toLong() == width.toLong() * height.toLong() * BYTES_PER_PIXEL
        if (!validSize) return null

        val sharedHandle = result.sharedOutputHandle
        return when {
            sharedHandle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
                SharedMemoryTransportApi27.readBitmap(sharedHandle, width, height, expectedBytes)
            result.pixelPipePfd != null ->
                PipeTransport.readBitmapFromPipe(result.pixelPipePfd, width, height, expectedBytes)
            else -> null
        }
    }

    fun closeHandle(handle: Parcelable?) {
        if (handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            SharedMemoryTransportApi27.closeHandle(handle)
        }
    }

    private fun ensureSoftwareArgb8888(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled) return null
        val needsCopy = bitmap.config != Bitmap.Config.ARGB_8888 ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE)
        return if (needsCopy) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private object SharedMemoryTransportApi27 {
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

        fun readBitmap(handle: Parcelable, width: Int, height: Int, expectedByteCount: Int): Bitmap? {
            val shm = handle as? SharedMemory ?: return null
            if (shm.size < expectedByteCount) return null
            var mapped: ByteBuffer? = null
            return try {
                mapped = shm.mapReadOnly()
                if (mapped.remaining() < expectedByteCount) return null
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
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

    private object PipeTransport {
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

        fun readBoundedFromPfd(pfd: ParcelFileDescriptor, maxBytes: Int): ByteArray? {
            return runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(PIPE_CHUNK_BYTES)
                    var total = 0
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > maxBytes) return@use null
                        out.write(buf, 0, read)
                    }
                    out.toByteArray()
                }
            }.getOrNull()
        }

        fun readBitmapFromPipe(
            pfd: ParcelFileDescriptor,
            width: Int,
            height: Int,
            expectedByteCount: Int,
        ): Bitmap? {
            val bytes = readBoundedFromPfd(pfd, expectedByteCount) ?: return null
            if (bytes.size != expectedByteCount) return null
        return runCatching {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
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

    internal fun readRawPixelsFromPipe(
        pfd: ParcelFileDescriptor,
        target: Bitmap,
        expectedByteCount: Int,
    ): Boolean = PipeTransport.readRawPixelsIntoBitmap(pfd, target, expectedByteCount)
}
