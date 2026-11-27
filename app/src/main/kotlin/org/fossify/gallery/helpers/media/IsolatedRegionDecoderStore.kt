package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

private const val MAX_REGION_SESSIONS = 4
private const val SESSION_CAPACITY = 8
private const val SESSION_LOAD_FACTOR = 0.75f

/**
 * Manages bounded [BitmapRegionDecoder] sessions inside [IsolatedMediaDecoderService].
 */
object IsolatedRegionDecoderStore {
    private class RegionSession(
        val decoder: BitmapRegionDecoder,
        val lock: Any = Any(),
    )

    private val nextId = AtomicLong(1L)
    private val mapLock = Any()
    private val sessions = LinkedHashMap<Long, RegionSession>(SESSION_CAPACITY, SESSION_LOAD_FACTOR, true)

    fun openSession(request: IsolatedDecodeRequest): IsolatedRegionOpenResult {
        return try {
            val bytes = IsolatedPixelTransport.unpackInputBytes(
                request,
                IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES,
            ) ?: return IsolatedRegionOpenResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)

            val decoder = createRegionDecoder(bytes)
                ?: return IsolatedRegionOpenResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)

            if (!IsolatedFormatSniffer.isValidSourceDimensions(decoder.width, decoder.height)) {
                runCatching { decoder.recycle() }
                return IsolatedRegionOpenResult(IsolatedDecodeResult.STATUS_TOO_LARGE)
            }
            val sessionId = registerSession(RegionSession(decoder))
            IsolatedRegionOpenResult(
                status = IsolatedDecodeResult.STATUS_OK,
                sessionId = sessionId,
                width = decoder.width,
                height = decoder.height,
            )
        } catch (_: Throwable) {
            IsolatedRegionOpenResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }
    }

    fun decodeTile(
        sessionId: Long,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        sampleSize: Int,
    ): IsolatedDecodeResult {
        val session = lookupSession(sessionId)
            ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
        var tileBitmap: Bitmap? = null
        return try {
            tileBitmap = decodeBitmapFromSession(session, left, top, right, bottom, sampleSize)
            val bmp = tileBitmap ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
            val packed = IsolatedPixelTransport.packBitmap("calypso-tile", bmp)
                ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
            IsolatedDecodeResult(
                status = IsolatedDecodeResult.STATUS_OK,
                width = bmp.width,
                height = bmp.height,
                byteCount = packed.byteCount,
                sharedOutputHandle = packed.sharedOutputHandle,
                pixelPipePfd = packed.pixelPipePfd,
            )
        } catch (_: Throwable) {
            IsolatedDecodeResult(IsolatedDecodeResult.STATUS_DECODE_FAILED)
        } finally {
            tileBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun decodeBitmapFromSession(
        session: RegionSession,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        sampleSize: Int,
    ): Bitmap? {
        return synchronized(session.lock) {
            val decoder = session.decoder
            if (decoder.isRecycled) return null
            val safeRect = IsolatedRegionTilePolicy.sanitizeTileRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                imageWidth = decoder.width,
                imageHeight = decoder.height,
            ) ?: return null
            val safeSample = IsolatedRegionTilePolicy.enforceTileSampleSize(safeRect, sampleSize)
            val options = BitmapFactory.Options().apply {
                inSampleSize = safeSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoder.decodeRegion(
                Rect(safeRect.left, safeRect.top, safeRect.right, safeRect.bottom),
                options,
            )
        }
    }

    fun closeSession(sessionId: Long) {
        val removed = synchronized(mapLock) { sessions.remove(sessionId) } ?: return
        synchronized(removed.lock) {
            runCatching {
                if (!removed.decoder.isRecycled) removed.decoder.recycle()
            }
        }
    }

    fun closeAll() {
        val all = synchronized(mapLock) {
            sessions.values.toList().also { sessions.clear() }
        }
        for (session in all) {
            synchronized(session.lock) {
                runCatching {
                    if (!session.decoder.isRecycled) session.decoder.recycle()
                }
            }
        }
    }

    private fun registerSession(session: RegionSession): Long {
        val id = nextId.getAndIncrement()
        val evicted = synchronized(mapLock) {
            sessions[id] = session
            if (sessions.size > MAX_REGION_SESSIONS) {
                val oldestKey = sessions.keys.firstOrNull()
                if (oldestKey != null && oldestKey != id) sessions.remove(oldestKey) else null
            } else {
                null
            }
        }
        if (evicted != null) {
            synchronized(evicted.lock) {
                runCatching { if (!evicted.decoder.isRecycled) evicted.decoder.recycle() }
            }
        }
        return id
    }

    private fun lookupSession(sessionId: Long): RegionSession? {
        return synchronized(mapLock) { sessions[sessionId] }
    }

    private fun createRegionDecoder(bytes: ByteArray): BitmapRegionDecoder? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(ByteArrayInputStream(bytes))
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            }
        }.getOrNull()
    }
}
