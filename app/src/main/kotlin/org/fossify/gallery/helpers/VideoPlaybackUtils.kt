package org.fossify.gallery.helpers

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.abs

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

    /**
     * Returns true when a preview cover should be shown during visibility changes because the
     * video surface has not yet rendered a live frame or no TextureView is currently attached.
     */
    fun shouldShowPreviewCoverOnVisibilityChange(
        hasRenderedFirstFrame: Boolean,
        hasAttachedTextureView: Boolean,
    ): Boolean {
        return !hasRenderedFirstFrame || !hasAttachedTextureView
    }

    /**
     * Returns true when resuming playback on an already-rendered valid surface should reveal the
     * video surface immediately without waiting for a new [Player.Listener.onRenderedFirstFrame].
     */
    fun shouldRevealOnPlaybackResume(
        isFragmentVisible: Boolean,
        hasRenderedFirstFrame: Boolean,
        hasValidSurface: Boolean,
    ): Boolean {
        return isFragmentVisible && hasRenderedFirstFrame && hasValidSurface
    }

    /**
     * Safety-net check for [android.view.TextureView.SurfaceTextureListener.onSurfaceTextureUpdated]
     * to reveal the surface if a frame was delivered to the active texture while the preview cover
     * remained visible.
     */
    fun shouldRevealOnSurfaceTextureUpdated(
        isFragmentVisible: Boolean,
        isViewDestroyed: Boolean,
        hasPlayer: Boolean,
        isPlaying: Boolean,
        isPlayerPrepared: Boolean,
        playWhenReady: Boolean,
        hasValidSurface: Boolean,
        isMatchingTexture: Boolean,
    ): Boolean {
        return isFragmentVisible &&
            !isViewDestroyed &&
            hasPlayer &&
            (isPlaying || (isPlayerPrepared && playWhenReady)) &&
            hasValidSurface &&
            isMatchingTexture
    }

    /**
     * Returns true once a cached video frame snapshot is missing or older than [ttlMs].
     */
    fun isFrameCacheEntryExpired(
        capturedAtElapsedMs: Long?,
        nowElapsedMs: Long,
        ttlMs: Long = VIDEO_FRAME_CACHE_TTL_MS,
    ): Boolean {
        if (capturedAtElapsedMs == null || ttlMs <= 0L) {
            return true
        }
        val ageMs = nowElapsedMs - capturedAtElapsedMs
        return ageMs < 0L || ageMs >= ttlMs
    }

    /**
     * Returns true only when [cachedPositionMs] matches [capturePositionMs] within [toleranceMs].
     */
    fun isCachedFramePositionFresh(
        cachedPositionMs: Long?,
        capturePositionMs: Long,
        toleranceMs: Long = VIDEO_FRAME_POSITION_TOLERANCE_MS,
    ): Boolean {
        if (cachedPositionMs == null || toleranceMs < 0L) {
            return false
        }
        return abs(cachedPositionMs - capturePositionMs) <= toleranceMs
    }

    /**
     * Decides whether the generic starting thumbnail (frame 0) should be loaded as the cover.
     * In autoplay mode, unvisited videos starting from 0:00 load frame 0 so swiping into them
     * shows the starting frame instead of a black screen, whereas videos resuming mid-playback
     * without a cached snapshot skip frame 0 to avoid a mismatched thumbnail flash.
     */
    fun shouldLoadDefaultVideoPreview(
        hasCachedFrame: Boolean,
        hasRestoredPosition: Boolean,
        isAutoplay: Boolean,
        isGesturePlayer: Boolean,
        isPanorama: Boolean,
    ): Boolean {
        if (hasCachedFrame || isPanorama) {
            return false
        }
        if (isAutoplay && !isGesturePlayer) {
            return !hasRestoredPosition
        }
        return true
    }

    /**
     * Keeps an existing preview drawable visible when no cached snapshot exists, unless it is the
     * frame-0 default thumbnail and playback has already progressed past 0:00.
     */
    fun shouldKeepExistingPreviewDrawable(
        hasCachedFrame: Boolean,
        isShowingDefaultPreview: Boolean,
        hasNonZeroPlaybackPosition: Boolean,
    ): Boolean {
        if (hasCachedFrame) {
            return false
        }
        return !isShowingDefaultPreview || !hasNonZeroPlaybackPosition
    }
}
