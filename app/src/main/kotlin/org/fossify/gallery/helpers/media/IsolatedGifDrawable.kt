package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * Software double-buffered `Drawable` displaying GIF frames rendered in [IsolatedMediaDecoderService].
 */
class IsolatedGifDrawable internal constructor(
    private val appContext: Context,
    private val sessionId: Long,
    private val meta: GifMetadata,
    private val slots: Array<IsolatedGifBufferTransport.ReadableGifSlot>,
    private val bitmaps: Array<Bitmap>,
) : Drawable(), Animatable, Closeable {

    internal data class GifMetadata(
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val initialDurationMs: Int,
    )

    private val stateLock = Any()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fetchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "calypso-gif-frame").apply { isDaemon = true }
    }

    private var frontIndex = 0
    private var currentFrameIndex = 0
    private var currentDelayMs = meta.initialDurationMs
    private var running = false
    private var fetchInFlight = false
    private var closed = false

    private val fetchTick: Runnable = Runnable {
        val target = synchronized(stateLock) {
            if (closed || !running || fetchInFlight) {
                return@Runnable
            }
            if (meta.frameCount <= 1) {
                return@Runnable
            }
            fetchInFlight = true
            val nextFrame = IsolatedGifTimingPolicy.nextFrameIndex(currentFrameIndex, meta.frameCount)
            val backSlot = IsolatedGifTimingPolicy.nextBufferSlot(frontIndex)
            FetchTarget(nextFrame, backSlot, slots[backSlot], bitmaps[backSlot])
        }

        runCatching {
            fetchExecutor.execute {
                val startMs = SystemClock.uptimeMillis()
                val nextDuration = IsolatedGifClient.fetchGifFrameIntoSlot(
                    context = appContext,
                    sessionId = sessionId,
                    frameIndex = target.frameIndex,
                    slotIndex = target.slotIndex,
                    slot = target.slot,
                    targetBitmap = target.bitmap,
                )
                val elapsedMs = (SystemClock.uptimeMillis() - startMs).coerceAtLeast(0L)
                mainHandler.post {
                    val delayToSchedule: Long?
                    synchronized(stateLock) {
                        fetchInFlight = false
                        if (closed) return@post
                        if (nextDuration != null) {
                            frontIndex = target.slotIndex
                            currentFrameIndex = target.frameIndex
                            currentDelayMs = nextDuration
                        }
                        delayToSchedule = if (running && nextDuration != null) {
                            (currentDelayMs.toLong() - elapsedMs).coerceAtLeast(0L)
                        } else {
                            if (nextDuration == null) running = false
                            null
                        }
                    }
                    if (nextDuration != null) {
                        invalidateSelf()
                    }
                    if (delayToSchedule != null) {
                        mainHandler.removeCallbacks(fetchTick)
                        mainHandler.postDelayed(fetchTick, delayToSchedule)
                    }
                }
            }
        }.onFailure {
            synchronized(stateLock) {
                fetchInFlight = false
                running = false
            }
        }
    }

    private data class FetchTarget(
        val frameIndex: Int,
        val slotIndex: Int,
        val slot: IsolatedGifBufferTransport.ReadableGifSlot,
        val bitmap: Bitmap,
    )

    override fun draw(canvas: Canvas) {
        synchronized(stateLock) {
            if (closed) return
            val bitmap = bitmaps[frontIndex]
            if (!bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, null, bounds, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = meta.width

    override fun getIntrinsicHeight(): Int = meta.height

    override fun start() {
        val scheduleDelay: Long? = synchronized(stateLock) {
            if (closed || running || meta.frameCount <= 1) {
                null
            } else {
                running = true
                currentDelayMs.toLong()
            }
        }
        if (scheduleDelay != null) {
            mainHandler.removeCallbacks(fetchTick)
            mainHandler.postDelayed(fetchTick, scheduleDelay)
        }
    }

    override fun stop() {
        synchronized(stateLock) {
            running = false
        }
        mainHandler.removeCallbacks(fetchTick)
    }

    override fun isRunning(): Boolean = synchronized(stateLock) { running && !closed }

    override fun close() {
        val shouldClean = synchronized(stateLock) {
            if (closed) {
                false
            } else {
                closed = true
                running = false
                true
            }
        }
        if (!shouldClean) return

        mainHandler.removeCallbacksAndMessages(null)
        IsolatedGifClient.closeGifSessionAsync(appContext, sessionId)
        runCatching {
            fetchExecutor.execute {
                synchronized(stateLock) {
                    slots.forEach { slot -> runCatching { slot.close() } }
                    bitmaps.forEach { bmp ->
                        if (!bmp.isRecycled) {
                            bmp.recycle()
                        }
                    }
                }
            }
        }
        fetchExecutor.shutdown()
    }
}
