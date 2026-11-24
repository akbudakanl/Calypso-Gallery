package org.fossify.gallery.helpers

import androidx.media3.common.C
import androidx.media3.common.Player

internal data class VideoSkipResult(
    val targetPositionMs: Long,
    val reachedEnd: Boolean,
)

internal object VideoPlaybackUtils {
    /**
     * Converts a player or metadata duration in milliseconds into a safe non-negative duration.
     * Treats [C.TIME_UNSET] and any non-positive value as unknown (`0L`).
     */
    fun sanitizeDurationMs(durationMs: Long): Long {
        return if (durationMs == C.TIME_UNSET || durationMs <= 0L) 0L else durationMs
    }

    /**
     * Resolves the best known duration from the player and a cached/metadata fallback.
     */
    fun effectiveDurationMs(playerDurationMs: Long, fallbackDurationMs: Long): Long {
        val fromPlayer = sanitizeDurationMs(playerDurationMs)
        if (fromPlayer > 0L) {
            return fromPlayer
        }
        return sanitizeDurationMs(fallbackDurationMs)
    }

    /**
     * Converts a duration in milliseconds into a safe [android.widget.SeekBar] max value.
     */
    fun toSeekBarMax(durationMs: Long): Int {
        return sanitizeDurationMs(durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Converts a playback position in milliseconds into a safe [android.widget.SeekBar] progress value.
     */
    fun toSeekBarProgress(positionMs: Long, seekBarMax: Int): Int {
        if (positionMs <= 0L || seekBarMax <= 0) {
            return 0
        }
        return positionMs.coerceAtMost(seekBarMax.toLong()).toInt()
    }

    /**
     * Computes the target position for a forward or backward skip without collapsing to `0L`
     * when the container duration is unknown (`C.TIME_UNSET` or `0L`).
     */
    fun computeSkipResult(
        currentPosMs: Long,
        skipMs: Long,
        durationMs: Long,
        forward: Boolean,
    ): VideoSkipResult {
        val safeCurr = currentPosMs.coerceAtLeast(0L)
        val safeSkip = skipMs.coerceAtLeast(0L)
        val safeDuration = sanitizeDurationMs(durationMs)

        if (forward && safeDuration > 0L && safeCurr + safeSkip >= safeDuration) {
            return VideoSkipResult(targetPositionMs = safeDuration, reachedEnd = true)
        }

        val rawTarget = if (forward) safeCurr + safeSkip else safeCurr - safeSkip
        val clampedTarget = if (safeDuration > 0L) {
            rawTarget.coerceIn(0L, safeDuration)
        } else {
            rawTarget.coerceAtLeast(0L)
        }
        return VideoSkipResult(targetPositionMs = clampedTarget, reachedEnd = false)
    }

    /**
     * Computes whether playback has ended without treating [C.TIME_UNSET] (negative) as elapsed.
     */
    fun isVideoEnded(
        skippedToEnd: Boolean,
        playbackState: Int?,
        currentPosMs: Long,
        durationMs: Long,
    ): Boolean {
        if (skippedToEnd || playbackState == Player.STATE_ENDED) {
            return true
        }
        val safeDuration = sanitizeDurationMs(durationMs)
        return safeDuration > 0L && currentPosMs > 0L && currentPosMs >= safeDuration
    }
}
