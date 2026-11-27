package org.fossify.gallery.helpers.media

import kotlin.math.roundToInt

/**
 * Pure-Kotlin timing, frame-index, and pixel-budget policy for isolated animated GIF playback.
 */
object IsolatedGifTimingPolicy {
    const val MAX_GIF_DIMENSION = 2048
    const val MAX_GIF_PIXELS = 4_194_304L
    const val MIN_FRAME_DELAY_MS = 20
    const val DEFAULT_FRAME_DELAY_MS = 100
    const val MAX_FRAME_DELAY_MS = 10_000
    const val DOUBLE_BUFFER_SLOTS = 2

    fun normalizeFrameDelayMs(rawDelayMs: Int): Int {
        if (rawDelayMs <= 0) {
            return DEFAULT_FRAME_DELAY_MS
        }
        return rawDelayMs.coerceIn(MIN_FRAME_DELAY_MS, MAX_FRAME_DELAY_MS)
    }

    fun nextFrameIndex(currentFrame: Int, frameCount: Int): Int {
        if (frameCount <= 0) {
            return 0
        }
        return (currentFrame + 1).mod(frameCount)
    }

    fun nextBufferSlot(currentSlot: Int): Int {
        return (currentSlot + 1).mod(DOUBLE_BUFFER_SLOTS)
    }

    fun computeGifFrameDimensions(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int = 0,
        reqHeight: Int = 0,
    ): Pair<Int, Int>? {
        if (srcWidth <= 0 || srcHeight <= 0) {
            return null
        }

        val capW = if (reqWidth > 0) minOf(reqWidth, MAX_GIF_DIMENSION) else MAX_GIF_DIMENSION
        val capH = if (reqHeight > 0) minOf(reqHeight, MAX_GIF_DIMENSION) else MAX_GIF_DIMENSION
        val initialScale = minOf(1.0, capW.toDouble() / srcWidth.toDouble(), capH.toDouble() / srcHeight.toDouble())
        var outWidth = (srcWidth * initialScale).roundToInt().coerceAtLeast(1)
        var outHeight = (srcHeight * initialScale).roundToInt().coerceAtLeast(1)

        val pixelCount = outWidth.toLong() * outHeight.toLong()
        if (pixelCount > MAX_GIF_PIXELS) {
            val pixelScale = kotlin.math.sqrt(MAX_GIF_PIXELS.toDouble() / pixelCount.toDouble())
            outWidth = (outWidth * pixelScale).toInt().coerceAtLeast(1)
            outHeight = (outHeight * pixelScale).toInt().coerceAtLeast(1)
        }
        return outWidth to outHeight
    }
}
