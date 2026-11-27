package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors

internal class IsolatedGifPrefetcher(
    private val appContext: Context,
    private val sessionId: Long,
    private val frameCount: Int,
    private val initialDurationMs: Int,
    private val slots: Array<IsolatedGifBufferTransport.ReadableGifSlot>,
    private val bitmaps: Array<Bitmap>,
    private val onInvalidate: () -> Unit,
) {
    private data class FetchTarget(
        val frameIndex: Int,
        val slotIndex: Int,
        val slot: IsolatedGifBufferTransport.ReadableGifSlot,
        val bitmap: Bitmap,
    )

    private val stateLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fetchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "calypso-gif-frame").apply { isDaemon = true }
    }

    private var frontIndex = 0
    private var currentFrameIndex = 0
    private var currentDelayMs = initialDurationMs
    private var prefetchedSlot: Int? = null
    private var prefetchedFrameIndex = 0
    private var prefetchedDurationMs = 0
    private var running = false
    private var prefetchInFlight = false
    private var waitingForFrame = false
    private var closed = false

    private val swapTick: Runnable = Runnable { swapToPrefetchedAndSchedule() }

    fun currentBitmap(): Bitmap? = synchronized(stateLock) {
        if (closed) null else bitmaps[frontIndex]
    }

    fun isRunning(): Boolean = synchronized(stateLock) { running && !closed }

    fun start() {
        var initialFetch: FetchTarget? = null
        val dueTimeMs = synchronized(stateLock) {
            if (closed || running || frameCount <= 1) {
                null
            } else {
                running = true
                waitingForFrame = false
                initialFetch = createFetchTargetLocked()
                SystemClock.uptimeMillis() + currentDelayMs.toLong()
            }
        }
        if (dueTimeMs != null) {
            mainHandler.removeCallbacks(swapTick)
            mainHandler.postAtTime(swapTick, dueTimeMs)
        }
        initialFetch?.let(::submitFetch)
    }

    fun stop() {
        synchronized(stateLock) {
            running = false
            waitingForFrame = false
        }
        mainHandler.removeCallbacks(swapTick)
    }

    fun close() {
        val shouldClean = synchronized(stateLock) {
            if (closed) {
                false
            } else {
                closed = true
                running = false
                waitingForFrame = false
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
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                }
            }
        }
        fetchExecutor.shutdown()
    }

    private fun swapToPrefetchedAndSchedule() {
        var nextFetch: FetchTarget? = null
        var nextDueTimeMs: Long? = null
        synchronized(stateLock) {
            if (!closed && running) {
                val readySlot = prefetchedSlot
                if (readySlot == null) {
                    waitingForFrame = true
                    nextFetch = createFetchTargetLocked()
                } else {
                    waitingForFrame = false
                    frontIndex = readySlot
                    currentFrameIndex = prefetchedFrameIndex
                    currentDelayMs = prefetchedDurationMs
                    prefetchedSlot = null
                    nextDueTimeMs = SystemClock.uptimeMillis() + currentDelayMs.toLong()
                    nextFetch = createFetchTargetLocked()
                }
            }
        }
        if (nextDueTimeMs != null) {
            onInvalidate()
            mainHandler.removeCallbacks(swapTick)
            mainHandler.postAtTime(swapTick, nextDueTimeMs!!)
        }
        nextFetch?.let(::submitFetch)
    }

    private fun createFetchTargetLocked(): FetchTarget? {
        val canPrefetch = !closed && running && frameCount > 1
        val isIdle = !prefetchInFlight && prefetchedSlot == null
        if (!canPrefetch || !isIdle) {
            return null
        }
        prefetchInFlight = true
        val nextFrame = IsolatedGifTimingPolicy.nextFrameIndex(currentFrameIndex, frameCount)
        val backSlot = IsolatedGifTimingPolicy.nextBufferSlot(frontIndex)
        return FetchTarget(nextFrame, backSlot, slots[backSlot], bitmaps[backSlot])
    }

    private fun submitFetch(target: FetchTarget) {
        runCatching {
            fetchExecutor.execute {
                val nextDuration = IsolatedGifClient.fetchGifFrameIntoSlot(
                    context = appContext,
                    sessionId = sessionId,
                    frameIndex = target.frameIndex,
                    slotIndex = target.slotIndex,
                    slot = target.slot,
                    targetBitmap = target.bitmap,
                )
                mainHandler.post { onPrefetchComplete(target, nextDuration) }
            }
        }.onFailure {
            synchronized(stateLock) {
                prefetchInFlight = false
                running = false
                waitingForFrame = false
            }
        }
    }

    private fun onPrefetchComplete(target: FetchTarget, nextDuration: Int?) {
        val swapNow = synchronized(stateLock) {
            prefetchInFlight = false
            if (closed || nextDuration == null) {
                running = false
                waitingForFrame = false
                false
            } else {
                prefetchedSlot = target.slotIndex
                prefetchedFrameIndex = target.frameIndex
                prefetchedDurationMs = nextDuration
                running && waitingForFrame
            }
        }
        if (swapNow) {
            swapToPrefetchedAndSchedule()
        }
    }
}
