package org.fossify.gallery.helpers

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.abs

internal data class VideoSkipResult(
    val targetPositionMs: Long,
    val reachedEnd: Boolean,
)

internal object VideoPlaybackUtils {
    private const val MICROSECONDS_PER_MILLISECOND = 1000L

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
     * Resolves the active non-negative playback position from init, pause, and current progress state.
     */
    fun resolvePlaybackPositionMs(
        positionWhenInitMs: Long,
        positionAtPauseMs: Long,
        currentTimeMs: Long,
    ): Long {
        return when {
            positionWhenInitMs > 0L -> positionWhenInitMs
            positionAtPauseMs > 0L -> positionAtPauseMs
            currentTimeMs > 0L -> currentTimeMs
            else -> 0L
        }
    }

    /**
     * Returns the frame timestamp in microseconds for Glide's `RequestOptions.frameOf(...)` when
     * resuming an unencrypted video from a positive restored position, or `null` when the default
     * frame should be used (position <= 0 or encrypted vault loaders that ignore `frameOf`).
     */
    fun resolvePreviewFrameTimeMicros(
        positionMs: Long,
        isEncrypted: Boolean,
    ): Long? {
        if (isEncrypted || positionMs <= 0L) {
            return null
        }
        return positionMs * MICROSECONDS_PER_MILLISECOND
    }

    /**
     * Returns true when [org.fossify.gallery.fragments.VideoFragment] is in a valid pre-render state
     * to start a Glide preview decode.
     */
    fun canLoadDefaultVideoPreview(
        isAdded: Boolean,
        isViewDestroyed: Boolean,
        isBindingInitialized: Boolean,
        hasRenderedFirstFrame: Boolean,
        isPlaying: Boolean,
    ): Boolean {
        if (!isAdded || isViewDestroyed || !isBindingInitialized) {
            return false
        }
        return !hasRenderedFirstFrame && !isPlaying
    }

    /**
     * Decides whether a preview thumbnail should be loaded when no cached snapshot exists and the
     * item is not a panorama. A thumbnail always beats a black screen.
     */
    fun shouldLoadDefaultVideoPreview(
        hasCachedFrame: Boolean,
        isPanorama: Boolean,
    ): Boolean {
        return !hasCachedFrame && !isPanorama
    }

    /**
     * Returns true when an asynchronously decoded preview drawable arrived after playback already
     * rendered a frame, started playing, obtained a cached snapshot, or the view was destroyed.
     */
    fun isDefaultVideoPreviewObsolete(
        isViewDestroyed: Boolean,
        isBindingInitialized: Boolean,
        hasRenderedFirstFrame: Boolean,
        isPlaying: Boolean,
        hasCachedFrame: Boolean,
    ): Boolean {
        if (isViewDestroyed || !isBindingInitialized) {
            return true
        }
        return hasRenderedFirstFrame || isPlaying || hasCachedFrame
    }

    /**
     * Keeps an existing preview drawable visible while no cached snapshot exists so the cover never
     * collapses to a black screen before playback renders.
     */
    fun shouldKeepExistingPreviewDrawable(
        hasCachedFrame: Boolean,
    ): Boolean {
        return !hasCachedFrame
    }

    /**
     * Clears the preview view before player init only when no drawable is shown and no default
     * preview Glide request is currently in flight.
     */
    fun shouldClearInFlightPreviewOnPlayerInit(
        hasPreviewDrawable: Boolean,
        isShowingDefaultPreview: Boolean,
    ): Boolean {
        return !hasPreviewDrawable && !isShowingDefaultPreview
    }

    /**
     * Returns true when becoming visible should drain a pending safety-net preview request or load
     * a missing cover when no frame has rendered yet.
     */
    fun shouldEnsureDefaultPreviewOnMenuVisible(
        isPendingPreview: Boolean,
        hasRenderedFirstFrame: Boolean,
        isBindingInitialized: Boolean,
        hasPreviewDrawable: Boolean,
        isShowingDefaultPreview: Boolean,
    ): Boolean {
        if (isPendingPreview) {
            return true
        }
        if (!isBindingInitialized || hasRenderedFirstFrame) {
            return false
        }
        return !hasPreviewDrawable && !isShowingDefaultPreview
    }
}
