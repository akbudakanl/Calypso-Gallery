package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Double-buffered frame slot transport between `IsolatedMediaDecoderService` and the UI process.
 * Uses `SharedMemory` on API 27+ (zero per-frame FD churn) and falls back to bounded pipes on API 26.
 */
internal object IsolatedGifBufferTransport {
    private const val BYTES_PER_PIXEL = 4L

    fun createWritableSlot(name: String, width: Int, height: Int): WritableGifSlot? {
        val byteCountLong = width.toLong() * height.toLong() * BYTES_PER_PIXEL
        if (byteCountLong <= 0L || byteCountLong > Int.MAX_VALUE) {
            return null
        }
        val byteCount = byteCountLong.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val shm = GifSharedMemoryApi27.createWritable(name, byteCount) ?: return null
            return WritableGifSlot(byteCount = byteCount, shmHandle = shm.handle, api27Slot = shm)
        }
        return WritableGifSlot(byteCount = byteCount, shmHandle = null, api27Slot = null)
    }

    fun createReadableSlot(width: Int, height: Int, handle: Parcelable?): ReadableGifSlot? {
        val byteCountLong = width.toLong() * height.toLong() * BYTES_PER_PIXEL
        if (byteCountLong <= 0L || byteCountLong > Int.MAX_VALUE) {
            return null
        }
        val byteCount = byteCountLong.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && handle != null) {
            val mapped = GifSharedMemoryApi27.openReadOnly(handle, byteCount) ?: return null
            return ReadableGifSlot(byteCount = byteCount, api27Slot = mapped)
        }
        return ReadableGifSlot(byteCount = byteCount, api27Slot = null)
    }

    internal class WritableGifSlot(
        private val byteCount: Int,
        val shmHandle: Parcelable?,
        private val api27Slot: GifSharedMemoryApi27.WritableMapping?,
    ) : Closeable {

        fun writeRgbaBytes(rgba: ByteArray): Pair<Boolean, ParcelFileDescriptor?> {
            if (rgba.size != byteCount) {
                return false to null
            }
            if (api27Slot != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                return api27Slot.writeRgba(rgba, byteCount) to null
            }
            val pipe = IsolatedPixelTransport.packInputPipe(rgba)
            return (pipe != null) to pipe
        }

        fun directByteBuffer(): ByteBuffer? {
            if (api27Slot == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                return null
            }
            return api27Slot.prepareDirectBuffer(byteCount)
        }

        fun writeFrame(bitmap: Bitmap): Pair<Boolean, ParcelFileDescriptor?> {
            if (bitmap.isRecycled) return false to null
            val needsCopy = bitmap.config != Bitmap.Config.ARGB_8888 ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE)
            val software = if (needsCopy) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false to null
            } else {
                bitmap
            }
            return try {
                if (software.byteCount != byteCount) {
                    false to null
                } else if (api27Slot != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    api27Slot.writeBitmap(software) to null
                } else {
                    val pipe = IsolatedPixelTransport.packBitmap("calypso-gif-pipe", software)?.pixelPipePfd
                    (pipe != null) to pipe
                }
            } finally {
                if (software !== bitmap) {
                    software.recycle()
                }
            }
        }

        override fun close() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                api27Slot?.close()
            }
        }
    }

    internal class ReadableGifSlot(
        private val byteCount: Int,
        private val api27Slot: GifSharedMemoryApi27.ReadOnlyMapping?,
    ) : Closeable {

        fun readInto(target: Bitmap, pixelPipePfd: ParcelFileDescriptor?): Boolean {
            if (target.isRecycled || target.byteCount != byteCount) {
                runCatching { pixelPipePfd?.close() }
                return false
            }
            if (api27Slot != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                runCatching { pixelPipePfd?.close() }
                return api27Slot.readInto(target, byteCount)
            }
            val pfd = pixelPipePfd ?: return false
            return IsolatedPixelTransport.PipeTransport.readRawPixelsIntoBitmap(pfd, target, byteCount)
        }

        override fun close() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                api27Slot?.close()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    internal object GifSharedMemoryApi27 {
        class WritableMapping(
            val handle: SharedMemory,
            private var buffer: ByteBuffer?,
        ) : Closeable {
            fun writeRgba(rgba: ByteArray, byteCount: Int): Boolean {
                val mapped = buffer ?: return false
                if (mapped.capacity() < byteCount || rgba.size < byteCount) return false
                return runCatching {
                    mapped.clear()
                    mapped.put(rgba, 0, byteCount)
                    true
                }.getOrDefault(false)
            }

            fun prepareDirectBuffer(byteCount: Int): ByteBuffer? {
                val mapped = buffer ?: return null
                if (!mapped.isDirect || mapped.capacity() < byteCount) return null
                mapped.clear()
                mapped.limit(byteCount)
                return mapped
            }

            fun writeBitmap(bitmap: Bitmap): Boolean {
                val mapped = buffer ?: return false
                return runCatching {
                    mapped.clear()
                    bitmap.copyPixelsToBuffer(mapped)
                    true
                }.getOrDefault(false)
            }

            override fun close() {
                buffer?.let { runCatching { SharedMemory.unmap(it) } }
                buffer = null
                runCatching { handle.close() }
            }
        }

        class ReadOnlyMapping(
            private val handle: SharedMemory,
            private var buffer: ByteBuffer?,
        ) : Closeable {
            fun readInto(target: Bitmap, byteCount: Int): Boolean {
                val mapped = buffer ?: return false
                if (mapped.capacity() < byteCount) return false
                return runCatching {
                    mapped.position(0)
                    mapped.limit(byteCount)
                    target.copyPixelsFromBuffer(mapped)
                    true
                }.getOrDefault(false)
            }

            override fun close() {
                buffer?.let { runCatching { SharedMemory.unmap(it) } }
                buffer = null
                runCatching { handle.close() }
            }
        }

        fun createWritable(name: String, byteCount: Int): WritableMapping? {
            var shm: SharedMemory? = null
            return try {
                shm = SharedMemory.create(name, byteCount)
                val mapped = shm.mapReadWrite()
                WritableMapping(shm, mapped)
            } catch (_: Throwable) {
                runCatching { shm?.close() }
                null
            }
        }

        fun openReadOnly(handle: Parcelable, byteCount: Int): ReadOnlyMapping? {
            val shm = handle as? SharedMemory ?: return null
            if (shm.size < byteCount) {
                runCatching { shm.close() }
                return null
            }
            return try {
                val mapped = shm.mapReadOnly()
                ReadOnlyMapping(shm, mapped)
            } catch (_: Throwable) {
                runCatching { shm.close() }
                null
            }
        }
    }
}
