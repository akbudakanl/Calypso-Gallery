@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package org.fossify.gallery.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.viewpager.widget.ViewPager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource as GlideDataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.fadeOut
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getVideoResolution
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.setDrawablesRelativeWithIntrinsicBounds
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.DEFAULT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.VideoActivity
import org.fossify.gallery.databinding.PagerVideoItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getActionBarHeight
import org.fossify.gallery.extensions.getBottomActionsHeight
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.extensions.getFriendlyMessage
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.parseFileChannel
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EXOPLAYER_MAX_BUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_MIN_BUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_PLAYBACK_BUFFER_MS
import org.fossify.gallery.helpers.EXOPLAYER_REBUFFER_BUFFER_MS
import org.fossify.gallery.helpers.EncryptedMediaDataSource
import org.fossify.gallery.helpers.MAX_ZOOM_EQUALITY_TOLERANCE
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.helpers.SeekIndexCacheKey
import org.fossify.gallery.helpers.SeekableExtractorsFactory
import org.fossify.gallery.helpers.VIDEO_FRAME_CACHE_TTL_MS
import org.fossify.gallery.helpers.VIDEO_FRAME_POSITION_TOLERANCE_MS
import org.fossify.gallery.helpers.VideoPlaybackUtils
import org.fossify.gallery.interfaces.PlaybackSpeedListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.views.MediaSideScroll
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import kotlin.math.abs

class VideoFragment : ViewPagerFragment(), TextureView.SurfaceTextureListener,
    SeekBar.OnSeekBarChangeListener, PlaybackSpeedListener {
    companion object {
        private const val PROGRESS = "progress"
        private const val UPDATE_INTERVAL_MS = 250L
        private const val TOUCH_HOLD_DURATION_MS = 500L
        private const val TOUCH_HOLD_SPEED_MULTIPLIER = 2.0f
        private const val TOUCH_SLOP_DIVIDER = 3
        private const val CACHED_FRAME_MAX_DIMENSION = 720f
        private val RESUME_SEEK_PARAMETERS = SeekParameters(350_000L, 150_000L)

        /** Position of the frame stored for each path, so a stale frame is never reused. */
        private val sVideoFramePositions = HashMap<String, Long>()
        private val sVideoFrameTimestamps = HashMap<String, Long>()

        @Volatile
        private var sEncryptedCacheGeneration = 0L

        val sVideoFrameCache = object : androidx.collection.LruCache<String, Bitmap>(16 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount
            }
        }

        private fun elapsedNowMs(): Long =
            runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }

        internal fun putCachedFrame(
            path: String,
            bitmap: Bitmap,
            positionMs: Long,
            nowElapsedMs: Long = elapsedNowMs(),
        ) {
            pruneExpiredFrameCache(nowElapsedMs)
            sVideoFrameCache.put(path, bitmap)
            sVideoFramePositions[path] = positionMs
            sVideoFrameTimestamps[path] = nowElapsedMs
        }

        internal fun getCachedFrame(path: String, nowElapsedMs: Long = elapsedNowMs()): Bitmap? {
            val bitmap = sVideoFrameCache.get(path) ?: run {
                sVideoFramePositions.remove(path)
                sVideoFrameTimestamps.remove(path)
                return null
            }
            if (bitmap.isRecycled) {
                sVideoFrameCache.remove(path)
                sVideoFramePositions.remove(path)
                sVideoFrameTimestamps.remove(path)
                return null
            }
            val capturedAt = sVideoFrameTimestamps[path]
            if (VideoPlaybackUtils.isFrameCacheEntryExpired(capturedAt, nowElapsedMs, VIDEO_FRAME_CACHE_TTL_MS)) {
                sVideoFrameCache.remove(path)
                sVideoFramePositions.remove(path)
                sVideoFrameTimestamps.remove(path)
                if (CryptoManager.isEncryptedFile(path)) {
                    runCatching { bitmap.eraseColor(0) }
                }
                return null
            }
            return bitmap
        }

        fun pruneExpiredFrameCache(nowElapsedMs: Long = elapsedNowMs()) {
            val snapshot = sVideoFrameCache.snapshot()
            for ((key, bmp) in snapshot) {
                val capturedAt = sVideoFrameTimestamps[key]
                if (bmp.isRecycled || VideoPlaybackUtils.isFrameCacheEntryExpired(capturedAt, nowElapsedMs, VIDEO_FRAME_CACHE_TTL_MS)) {
                    sVideoFrameCache.remove(key)
                    sVideoFramePositions.remove(key)
                    sVideoFrameTimestamps.remove(key)
                    if (!bmp.isRecycled && CryptoManager.isEncryptedFile(key)) {
                        runCatching { bmp.eraseColor(0) }
                    }
                }
            }
        }

        internal fun removeCachedFrame(path: String) {
            val bitmap = sVideoFrameCache.remove(path)
            sVideoFramePositions.remove(path)
            sVideoFrameTimestamps.remove(path)
            if (bitmap != null && !bitmap.isRecycled && CryptoManager.isEncryptedFile(path)) {
                runCatching { bitmap.eraseColor(0) }
            }
        }

        /** Zeroizes and evicts only encrypted vault frames (ADR 0002) while preserving normal gallery frames. */
        fun clearEncryptedFrameCache() {
            sEncryptedCacheGeneration++
            val snapshot = sVideoFrameCache.snapshot()
            for ((key, bmp) in snapshot) {
                if (CryptoManager.isEncryptedFile(key)) {
                    if (!bmp.isRecycled) {
                        runCatching { bmp.eraseColor(0) }
                    }
                    sVideoFrameCache.remove(key)
                    sVideoFramePositions.remove(key)
                    sVideoFrameTimestamps.remove(key)
                }
            }
        }

        fun clearFrameCache() {
            sEncryptedCacheGeneration++
            try {
                sVideoFrameCache.snapshot().values.forEach { bmp ->
                    if (!bmp.isRecycled) {
                        bmp.eraseColor(0)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            sVideoFrameCache.evictAll()
            sVideoFramePositions.clear()
            sVideoFrameTimestamps.clear()
        }
    }

    /** Last captured frame for this video. Kept stable so it never visibly refreshes. */
    private fun cachedFrame(): Bitmap? = getCachedFrame(mMedium.path)

    /** Show a non-null bitmap on the preview cover without flickering or resetting the same bitmap. */
    private fun showPreviewBitmap(bitmap: Bitmap) {
        if (!::binding.isInitialized || mIsViewDestroyed || mConfig.gestureVideoPlayer || mIsPanorama || bitmap.isRecycled) {
            return
        }
        if (mIsShowingDefaultPreview) {
            if (isAdded) {
                Glide.with(this).clear(binding.videoPreview)
            }
            mIsShowingDefaultPreview = false
        }
        if (CryptoManager.isEncryptedFile(mMedium.path)) {
            mShownEncryptedCacheGeneration = sEncryptedCacheGeneration
        }
        binding.videoPreview.alpha = 1f
        if ((binding.videoPreview.drawable as? BitmapDrawable)?.bitmap !== bitmap) {
            binding.videoPreview.setImageBitmap(bitmap)
        }
        binding.videoPreview.beVisible()
    }

    /** Always keep a real frame on the cover when available: never a blank cover on first autoplay. */
    private fun ensureCachedPreviewShown() {
        if (!::binding.isInitialized || mIsViewDestroyed || mConfig.gestureVideoPlayer || mIsPanorama) return
        if (CryptoManager.isEncryptedFile(mMedium.path) && mShownEncryptedCacheGeneration != sEncryptedCacheGeneration) {
            mShownEncryptedCacheGeneration = sEncryptedCacheGeneration
            mIsShowingDefaultPreview = false
            if (binding.videoPreview.drawable != null) {
                binding.videoPreview.setImageDrawable(null)
                binding.videoPreview.beGone()
            }
        }
        val bitmap = cachedFrame()
        if (bitmap != null) {
            showPreviewBitmap(bitmap)
        } else if (binding.videoPreview.drawable != null) {
            if (VideoPlaybackUtils.shouldKeepExistingPreviewDrawable(hasCachedFrame = false)) {
                binding.videoPreview.alpha = 1f
                binding.videoPreview.beVisible()
            } else {
                if (isAdded) {
                    Glide.with(this).clear(binding.videoPreview)
                }
                binding.videoPreview.setImageDrawable(null)
                mIsShowingDefaultPreview = false
                binding.videoPreview.beGone()
            }
        }
    }

    /** Keep videoPreview non-clickable in normal playback so all touches pass through to videoSurfaceFrame. */
    private fun updatePreviewOverlayStyle() {
        if (!::binding.isInitialized) return
        val previewHandlesTouch = mConfig.gestureVideoPlayer || mIsPanorama
        binding.videoPreview.isClickable = previewHandlesTouch
        binding.videoPreview.isFocusable = previewHandlesTouch
        val bgColor = if (mConfig.blackBackground) Color.BLACK else requireContext().getProperBackgroundColor()
        binding.videoPreview.setBackgroundColor(bgColor)
    }

    /** Hide the preview cover immediately once ExoPlayer renders a real frame onto videoSurfaceFrame. */
    private fun revealRenderedSurface() {
        if (!::binding.isInitialized || mIsViewDestroyed || mConfig.gestureVideoPlayer || mIsPanorama) return
        if (binding.videoSurfaceFrame.alpha != 1f) {
            binding.videoSurfaceFrame.alpha = 1f
        }
        if (!binding.videoPreview.isVisible()) {
            return
        }
        if (mIsShowingDefaultPreview) {
            if (isAdded) {
                Glide.with(this).clear(binding.videoPreview)
            } else {
                binding.videoPreview.setImageDrawable(null)
            }
            mIsShowingDefaultPreview = false
        }
        binding.videoPreview.beGone()
        binding.videoPreview.alpha = 1f
    }

    private var mIsFullscreen = false
    private var mWasFragmentInit = false
    private var mIsPanorama = false
    private var mIsFragmentVisible = false
    private var mIsDragged = false
    private var mWasVideoStarted = false
    private var mWasPlayerInited = false
    private var mWasLastPositionRestored = false
    private var mPlayOnPrepared = false
    private var mIsPlayerPrepared = false
    private var mCurrTime = 0L
    private var mDuration = 0L
    private var mPositionWhenInit = 0L
    private var mPositionAtPause = 0L
    var mIsPlaying = false
    private var mSkippedToEnd = false

    private class DeferredPlayerRelease(
        val player: ExoPlayer,
        val surface: Surface?,
        var surfaceTexture: SurfaceTexture?,
    )

    private var mExoPlayer: ExoPlayer? = null
    private var mUsingSharedPlayer = false
    private var mPlayerListener: Player.Listener? = null
    private var mVideoSurface: Surface? = null
    private var mDeferredSurfaceTexture: SurfaceTexture? = null
    private var mActiveDeferredRelease: DeferredPlayerRelease? = null
    private var mPendingAutoplayOnIdle = false
    private var mPendingInitOnIdle = false
    private var mPendingFrameCaptureOnIdle = false
    private var mPendingPreviewOnIdle = false
    private var mPendingMetadataProbe = false
    private var mIsCapturingFrame = false
    private var mHasRenderedFirstFrame = false
    private var mIsShowingDefaultPreview = false
    private var mShownEncryptedCacheGeneration = -1L
    private var mCapturePendingTexture: SurfaceTexture? = null
    private var mPendingCaptureCompletion: (() -> Unit)? = null
    private var mIsViewDestroyed = false
    private var mVideoSize = Point(1, 1)
    private var mTimerHandler = Handler(Looper.getMainLooper())

    // True once the player reported the size of the video.
    private var mVideoSizeKnown = false
    // True if the playback was requested, but it waits for the screen to be rotated.
    private var mPendingPlayStart = false

    private var mStoredShowExtendedDetails = false
    private var mStoredHideExtendedDetails = false
    private var mStoredBottomActions = true
    private var mStoredExtendedDetails = 0
    private var mStoredRememberLastVideoPosition = false
    private var mOriginalPlaybackSpeed = 1f
    private var mIsLongPressActive = false
    private var mHasAudio = true
    // The album-wide preference (config.muteVideos) is adopted by a video only when it starts.
    // Already started videos keep their own audio state until they are restarted, mirroring
    // upstream Gallery where every page owns a separate ExoPlayer.
    private var mIsMuted = false
    private var mHasStarted = false

    private val mTouchHoldRunnable = Runnable {
        mView.parent.requestDisallowInterceptTouchEvent(true)
        // This code runs after the delay, only if the user is still holding down.
        mIsLongPressActive = true
        mOriginalPlaybackSpeed = mExoPlayer?.playbackParameters?.speed ?: mConfig.playbackSpeed
        mView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        updatePlaybackSpeed(TOUCH_HOLD_SPEED_MULTIPLIER)

        mPlaybackSpeedPill.fadeIn()
    }

    private lateinit var mTimeHolder: View
    private lateinit var mBrightnessSideScroll: MediaSideScroll
    private lateinit var mVolumeSideScroll: MediaSideScroll
    private lateinit var binding: PagerVideoItemBinding
    private lateinit var mView: View
    private lateinit var mMedium: Medium
    private lateinit var mConfig: Config
    private var mTextureView: TextureView? = null
    private lateinit var mCurrTimeView: TextView
    private lateinit var mPlayPauseButton: ImageView
    private lateinit var mSeekBar: SeekBar
    private lateinit var mPlaybackSpeedPill: TextView
    private var mTouchSlop = 0
    private var mInitialX = 0f
    private var mInitialY = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val activity = requireActivity()
        val arguments = requireArguments()

        mIsViewDestroyed = false
        mMedium = arguments.getSerializable(MEDIUM) as Medium
        mConfig = context.config
        mTouchSlop = (ViewConfiguration.get(context).scaledTouchSlop) / TOUCH_SLOP_DIVIDER
        binding = PagerVideoItemBinding.inflate(inflater, container, false).apply {
            panoramaOutline.setOnClickListener { openPanorama() }
            bottomVideoTimeHolder.videoCurrTime.setOnClickListener { skip(false) }
            bottomVideoTimeHolder.videoDuration.setOnClickListener { skip(true) }
            videoHolder.setOnClickListener { toggleFullscreen() }
            videoPreview.setOnClickListener {
                if (mConfig.gestureVideoPlayer || mIsPanorama) {
                    toggleFullscreen()
                }
            }
            bottomVideoTimeHolder.videoPlaybackSpeed.setOnClickListener { showPlaybackSpeedPicker() }
            bottomVideoTimeHolder.videoToggleMute.setOnClickListener {
                mIsMuted = !mIsMuted
                mConfig.muteVideos = mIsMuted
                updatePlayerMuteState(showToast = true)
            }

            videoSurfaceFrame.controller.settings.swallowDoubleTaps = true

            videoPlayOutline.setOnClickListener {
                if (mConfig.gestureVideoPlayer) activity.launchGesturePlayer(mMedium.path) else togglePlayPause()
            }

            mPlayPauseButton = bottomVideoTimeHolder.videoTogglePlayPause
            mPlayPauseButton.setOnClickListener {
                togglePlayPause()
            }

            mSeekBar = bottomVideoTimeHolder.videoSeekbar
            mPlaybackSpeedPill = playbackSpeedPill
            mSeekBar.setOnSeekBarChangeListener(this@VideoFragment)
            // adding an empty click listener just to avoid ripple animation at toggling fullscreen
            mSeekBar.setOnClickListener { }

            mTimeHolder = bottomVideoTimeHolder.videoTimeHolder
            mCurrTimeView = bottomVideoTimeHolder.videoCurrTime
            mBrightnessSideScroll = videoBrightnessController
            mVolumeSideScroll = videoVolumeController
            mBrightnessSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }
            mVolumeSideScroll.onVerticalScroll = {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
            }

            val gestureDetector =
                GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!mConfig.allowInstantChange) {
                            toggleFullscreen()
                            return true
                        }

                        val viewWidth = root.width
                        val instantWidth = viewWidth / 7
                        val clickedX = e.rawX
                        when {
                            clickedX <= instantWidth -> listener?.goToPrevItem()
                            clickedX >= viewWidth - instantWidth -> listener?.goToNextItem()
                            else -> toggleFullscreen()
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        handleDoubleTap(e.rawX)
                        return true
                    }
                })

            videoPreview.setOnTouchListener { view, event ->
                if ((mConfig.gestureVideoPlayer || mIsPanorama) && mConfig.allowDownGesture) {
                    handleEvent(event)
                }
                false
            }

            videoSurfaceFrame.setOnTouchListener { view, event ->
                val isZoomedOut = abs(videoSurfaceFrame.controller.state.zoom - 1f) < MAX_ZOOM_EQUALITY_TOLERANCE
                if (mConfig.allowDownGesture && isZoomedOut) {
                    handleEvent(event)
                }
                handleTouchHoldEvent(event)
                if (mIsLongPressActive) {
                    return@setOnTouchListener true
                }

                gestureDetector.onTouchEvent(event)
                false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoHolder) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())

            val pillTopMargin = system.top + resources.getActionBarHeight(context) +
                resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()
            (mPlaybackSpeedPill.layoutParams as? RelativeLayout.LayoutParams)?.apply {
                setMargins(
                    0, pillTopMargin, 0, 0
                )
            }

            binding.bottomActionsDummy.updateLayoutParams<ViewGroup.LayoutParams> {
                height = resources.getBottomActionsHeight() + system.bottom
            }
            insets
        }

        mView = binding.root
        // Keep videoSurfaceFrame visible with alpha=1f so TextureView always allocates its SurfaceTexture.
        // videoPreview sits on top of videoSurfaceFrame as the temporary cover until a frame renders.
        binding.videoSurfaceFrame.alpha = 1f
        binding.videoSurfaceFrame.beGoneIf(mConfig.gestureVideoPlayer || mIsPanorama)
        updatePreviewOverlayStyle()

        if (!arguments.getBoolean(SHOULD_INIT_FRAGMENT, true)) {
            return mView
        }

        storeStateVariables()

        // setMenuVisibility is not called at VideoActivity (third party intent)
        if (!mIsFragmentVisible && activity is VideoActivity) {
            mIsFragmentVisible = true
        }

        // Restore any previously saved or session playback position BEFORE choosing a preview cover:
        // unvisited videos (position 0, no cached frame) show their default frame-0 thumbnail
        // instead of a black screen, whereas videos resuming mid-playback without a cached snapshot
        // avoid flashing frame 0.
        if (!mIsPanorama && savedInstanceState != null) {
            val savedProgress = savedInstanceState.getLong(PROGRESS, 0L)
            if (savedProgress > 0L) {
                mCurrTime = savedProgress
                mPositionWhenInit = savedProgress
                mPositionAtPause = savedProgress
            }
        }
        if (mStoredRememberLastVideoPosition) {
            restoreLastVideoSavedPosition()
        }
        restoreSessionPlaybackPosition()

        mIsShowingDefaultPreview = false
        val isEncrypted = CryptoManager.isEncryptedFile(mMedium.path)
        val cachedFrame = cachedFrame()
        val shouldLoadDefaultPreview = VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
            hasCachedFrame = cachedFrame != null,
            isPanorama = mIsPanorama,
        )
        if (cachedFrame != null) {
            showPreviewBitmap(cachedFrame)
            mPendingPreviewOnIdle = false
        } else if (shouldLoadDefaultPreview) {
            // Start Glide immediately (off the main thread) even if the pager is scrolling so
            // neighbour thumbnails are ready before swipe settle instead of showing black.
            loadVideoPreview()
        } else {
            mPendingPreviewOnIdle = false
            binding.videoPreview.beGone()
        }

        // With autoplay the center play icon must never linger on screen: playback starts or
        // resumes on menu visibility, so a briefly visible icon looks like a glitch. Hide it
        // right away, before the player has even been prepared. Also keep it hidden once
        // the video has already been started so pausing leaves the frame unobstructed.
        if ((mConfig.autoplayVideos && !mConfig.gestureVideoPlayer && !mIsPanorama) || mWasVideoStarted) {
            binding.videoPlayOutline.beGone()
        }

        mIsFullscreen = listener?.isFullScreen() == true
        initTimeHolder()
        // checkIfPanorama() TODO: Implement panorama using a FOSS library
        mPendingMetadataProbe = true

        if (mIsPanorama) {
            binding.apply {
                panoramaOutline.beVisible()
                videoPlayOutline.beGone()
                mVolumeSideScroll.beGone()
                mBrightnessSideScroll.beGone()
                Glide.with(context)
                    .load(mMedium.path)
                    .diskCacheStrategy(if (isEncrypted) com.bumptech.glide.load.engine.DiskCacheStrategy.NONE else com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                    .skipMemoryCache(isEncrypted)
                    .into(videoPreview)
            }
        }

        if (!mIsPanorama) {
            mWasFragmentInit = true
            setVideoSize()

            binding.apply {
                mBrightnessSideScroll.initialize(
                    activity,
                    slideInfo,
                    true,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToPrevItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        doSkip(false)
                    })
                mVolumeSideScroll.initialize(
                    activity,
                    slideInfo,
                    false,
                    container,
                    singleTap = { x, y ->
                        if (mConfig.allowInstantChange) {
                            listener?.goToNextItem()
                        } else {
                            toggleFullscreen()
                        }
                    },
                    doubleTap = { x, y ->
                        doSkip(true)
                    })
            }
        }

        setupVideoDuration()

        if (mIsFragmentVisible) {
            if (listener?.isPagerScrolling() == true) {
                if (mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
                    mPendingAutoplayOnIdle = true
                } else {
                    mPendingInitOnIdle = true
                }
            } else {
                if (mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
                    playVideo()
                } else {
                    initExoPlayer()
                }
                probeMetadataIfNeeded()
            }
        }
        return mView
    }

    override fun onResume() {
        super.onResume()
        mConfig =
            requireContext().config      // make sure we get a new config, in case the user changed something in the app settings
        requireActivity().updateTextColors(binding.videoHolder)
        val allowVideoGestures = mConfig.allowVideoGestures
        mTextureView?.beGoneIf(mConfig.gestureVideoPlayer || mIsPanorama)
        binding.videoSurfaceFrame.beGoneIf(mConfig.gestureVideoPlayer || mIsPanorama)
        binding.videoSurfaceFrame.alpha = 1f
        updatePreviewOverlayStyle()
        if (CryptoManager.isEncryptedFile(mMedium.path) && mShownEncryptedCacheGeneration != sEncryptedCacheGeneration) {
            mShownEncryptedCacheGeneration = sEncryptedCacheGeneration
            mIsShowingDefaultPreview = false
            if (binding.videoPreview.drawable != null) {
                binding.videoPreview.setImageDrawable(null)
                binding.videoPreview.beGone()
            }
        }

        mVolumeSideScroll.beVisibleIf(allowVideoGestures && !mIsPanorama)
        mBrightnessSideScroll.beVisibleIf(allowVideoGestures && !mIsPanorama)
        mExoPlayer?.repeatMode = if (mConfig.loopVideos && listener?.isSlideShowActive() == false) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

        checkExtendedDetails()
        initTimeHolder()
        storeStateVariables()

        // Recovery for a start request that got stranded while the activity was in the
        // background: bring the app back and the video resumes instead of staying black.
        if (mIsFragmentVisible && mIsPlayerPrepared && mPlayOnPrepared &&
            mConfig.autoplayVideos && !mConfig.gestureVideoPlayer
        ) {
            mPlayOnPrepared = false
            playVideo()
        }
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        mPendingAutoplayOnIdle = false
        mPendingInitOnIdle = false
        mPendingFrameCaptureOnIdle = false
        mPendingPreviewOnIdle = false
        pauseVideo(deferFrameCapture = false)
        if (mStoredRememberLastVideoPosition && mIsFragmentVisible && mWasVideoStarted) {
            saveVideoProgress()
        }
    }

    override fun onDestroyView() {
        mIsViewDestroyed = true
        mHasRenderedFirstFrame = false
        mIsShowingDefaultPreview = false
        if (::binding.isInitialized) {
            binding.videoPreview.animate().cancel()
        }
        mPendingAutoplayOnIdle = false
        mPendingInitOnIdle = false
        mPendingPreviewOnIdle = false
        mPendingMetadataProbe = false
        if (mUsingSharedPlayer) {
            releaseExoPlayer(allowDefer = false)
        } else if (mPendingFrameCaptureOnIdle) {
            mPendingFrameCaptureOnIdle = false
            captureFrameSnapshot()
        }
        mPendingFrameCaptureOnIdle = false
        detachTextureView()
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activity?.isChangingConfigurations == false) {
            cleanup()
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (mIsFragmentVisible && !menuVisible) {
            mPendingAutoplayOnIdle = false
            mPendingInitOnIdle = false
            mPendingPreviewOnIdle = false
            pauseVideo(deferFrameCapture = false)
            // Freeze the last real frame on screen while this page is offscreen if no live frame
            // is currently displayed. Otherwise keep the live paused TextureView until PixelCopy
            // finishes and detachTextureView() replaces it with the cached frame.
            if (VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(mHasRenderedFirstFrame, mTextureView != null)) {
                ensureCachedPreviewShown()
            }
        }

        mIsFragmentVisible = menuVisible
        if (!menuVisible) {
            return
        }

        // On the way back the cached frame is visible immediately when the surface is not yet ready.
        if (VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(mHasRenderedFirstFrame, mTextureView != null)) {
            ensureCachedPreviewShown()
        }
        val hasPreviewDrawable = ::binding.isInitialized && binding.videoPreview.drawable != null
        if (VideoPlaybackUtils.shouldEnsureDefaultPreviewOnMenuVisible(
                isPendingPreview = mPendingPreviewOnIdle,
                hasRenderedFirstFrame = mHasRenderedFirstFrame,
                isBindingInitialized = ::binding.isInitialized,
                hasPreviewDrawable = hasPreviewDrawable,
                isShowingDefaultPreview = mIsShowingDefaultPreview,
            )
        ) {
            loadVideoPreview()
        }
        mPendingFrameCaptureOnIdle = false
        val isScrolling = listener?.isPagerScrolling() == true
        if (mWasFragmentInit) {
            ensureTextureViewAttached()
        }
        if (mPlayOnPrepared && mIsPlayerPrepared) {
            // A playback request that became ready while the page was hidden must resume here.
            if (isScrolling) {
                mPendingAutoplayOnIdle = true
            } else {
                mPlayOnPrepared = false
                playVideo()
            }
        } else if (mWasFragmentInit && mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
            if (isScrolling) {
                mPendingAutoplayOnIdle = true
            } else {
                playVideo()
            }
        } else if (mWasFragmentInit && mPositionAtPause > 0L && mExoPlayer == null) {
            if (isScrolling) {
                mPendingInitOnIdle = true
            } else {
                initExoPlayer()
            }
        }
        if (!isScrolling && mWasFragmentInit) {
            probeMetadataIfNeeded()
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (!mWasFragmentInit) {
            return
        }

        when (state) {
            ViewPager.SCROLL_STATE_DRAGGING -> {
                // A drag alone does not mean the page will change. Keep the video playing until
                // setMenuVisibility(false) confirms this fragment is no longer the active one.
                // Pausing here would stop playback on every swipe that is released before the
                // neighbouring page becomes current (and briefly cut audio/frame on all swipes).
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                stopHoldSpeedMultiplierGesture()
                if (mPendingPreviewOnIdle) {
                    loadVideoPreview()
                }
            }

            ViewPager.SCROLL_STATE_IDLE -> {
                if (mIsFragmentVisible) {
                    mPendingFrameCaptureOnIdle = false
                    ensureTextureViewAttached()
                    if (mPendingPreviewOnIdle) {
                        loadVideoPreview()
                    }
                    if (mPendingAutoplayOnIdle) {
                        mPendingAutoplayOnIdle = false
                        if (mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
                            playVideo()
                        }
                    } else if (mPlayOnPrepared && mIsPlayerPrepared) {
                        mPlayOnPrepared = false
                        playVideo()
                    } else if (mPendingInitOnIdle) {
                        mPendingInitOnIdle = false
                        if (mPositionAtPause > 0L && mExoPlayer == null) {
                            initExoPlayer()
                        }
                    }
                    probeMetadataIfNeeded()
                } else {
                    mPendingAutoplayOnIdle = false
                    mPendingInitOnIdle = false
                    if (mPendingPreviewOnIdle) {
                        loadVideoPreview()
                    }
                    if (mPendingFrameCaptureOnIdle) {
                        mPendingFrameCaptureOnIdle = false
                        captureFrameSnapshot {
                            if (!mIsFragmentVisible && !mIsViewDestroyed) {
                                if (mUsingSharedPlayer) {
                                    releaseExoPlayer(allowDefer = false)
                                }
                                detachTextureView()
                            }
                        }
                    } else if (!mIsCapturingFrame) {
                        if (mUsingSharedPlayer) {
                            releaseExoPlayer(allowDefer = false)
                        }
                        detachTextureView()
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setVideoSize()
        binding.videoSurfaceFrame.onGlobalLayout {
            binding.videoSurfaceFrame.controller.resetState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(PROGRESS, mCurrTime)
    }

    private fun storeStateVariables() {
        mConfig.apply {
            mStoredShowExtendedDetails = showExtendedDetails
            mStoredHideExtendedDetails = hideExtendedDetails
            mStoredExtendedDetails = extendedDetails
            mStoredBottomActions = bottomActions
            mStoredRememberLastVideoPosition = rememberLastVideoPosition
        }
    }

    private fun saveVideoProgress() {
        if (!videoEnded()) {
            if (mExoPlayer != null) {
                mConfig.saveLastVideoPosition(
                    mMedium.path,
                    mExoPlayer!!.currentPosition.toInt() / 1000
                )
            } else {
                mConfig.saveLastVideoPosition(mMedium.path, mPositionAtPause.toInt() / 1000)
            }
        }
    }

    private fun restoreLastVideoSavedPosition() {
        mWasLastPositionRestored = true
        val seconds = mConfig.getLastVideoPosition(mMedium.path)
        if (seconds > 0) {
            mPositionAtPause = seconds * 1000L
            setPosition(seconds * 1000L)
        }
    }

    private val mTimerRunnable = object : Runnable {
        override fun run() {
            if (mExoPlayer != null && !mIsDragged && mIsPlaying) {
                mCurrTime = mExoPlayer!!.currentPosition.coerceAtLeast(0L)
                mSeekBar.progress = VideoPlaybackUtils.toSeekBarProgress(mCurrTime, mSeekBar.max)
                mCurrTimeView.text = mCurrTime.getFormattedDuration()
                mTimerHandler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun startTimer() {
        stopTimer()
        if (mIsPlaying) {
            mTimerHandler.post(mTimerRunnable)
        }
    }

    private fun stopTimer() {
        mTimerHandler.removeCallbacks(mTimerRunnable)
    }

    private fun showVideoControls() {
        binding.videoPlayOutline.beGone()
        mPlayPauseButton.beVisible()
        binding.bottomVideoTimeHolder.videoToggleMute.beVisible()
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.beVisible()
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
            "${DecimalFormat("#.##").format(mConfig.playbackSpeed)}x"
        syncMuteStateOnStart()
    }

    private fun setupTimeHolder() {
        val safeDuration = VideoPlaybackUtils.sanitizeDurationMs(mDuration)
        mSeekBar.max = VideoPlaybackUtils.toSeekBarMax(safeDuration)
        binding.bottomVideoTimeHolder.videoDuration.text = safeDuration.getFormattedDuration()
    }

    /** Decodes the default or restored-position thumbnail off the main thread via Glide. */
    private fun loadVideoPreview() {
        mPendingPreviewOnIdle = false
        val canLoad = VideoPlaybackUtils.canLoadDefaultVideoPreview(
            isAdded = isAdded,
            isViewDestroyed = mIsViewDestroyed,
            isBindingInitialized = ::binding.isInitialized,
            hasRenderedFirstFrame = mHasRenderedFirstFrame,
            isPlaying = mIsPlaying,
        )
        if (!canLoad || !VideoPlaybackUtils.shouldLoadDefaultVideoPreview(cachedFrame() != null, mIsPanorama)) {
            return
        }
        val isEncrypted = CryptoManager.isEncryptedFile(mMedium.path)
        val playbackPosMs = VideoPlaybackUtils.resolvePlaybackPositionMs(mPositionWhenInit, mPositionAtPause, mCurrTime)
        val frameTimeMicros = VideoPlaybackUtils.resolvePreviewFrameTimeMicros(playbackPosMs, isEncrypted)
        mIsShowingDefaultPreview = true
        binding.videoPreview.alpha = 1f
        binding.videoPreview.beVisible()
        Glide.with(this)
            .load(mMedium.path)
            .diskCacheStrategy(
                if (isEncrypted) com.bumptech.glide.load.engine.DiskCacheStrategy.NONE
                else com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC
            )
            .skipMemoryCache(isEncrypted)
            .let { builder ->
                if (frameTimeMicros != null) {
                    builder.apply(RequestOptions.frameOf(frameTimeMicros))
                } else {
                    builder
                }
            }
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    if (mIsShowingDefaultPreview && ::binding.isInitialized && binding.videoPreview.drawable == null) {
                        mIsShowingDefaultPreview = false
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: GlideDataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    val isObsolete = VideoPlaybackUtils.isDefaultVideoPreviewObsolete(
                        isViewDestroyed = mIsViewDestroyed,
                        isBindingInitialized = ::binding.isInitialized,
                        hasRenderedFirstFrame = mHasRenderedFirstFrame,
                        isPlaying = mIsPlaying,
                        hasCachedFrame = cachedFrame() != null,
                    )
                    if (isObsolete) {
                        mIsShowingDefaultPreview = false
                        return true
                    }
                    mIsShowingDefaultPreview = true
                    if (isEncrypted) {
                        mShownEncryptedCacheGeneration = sEncryptedCacheGeneration
                    }
                    binding.videoPreview.alpha = 1f
                    binding.videoPreview.beVisible()
                    return false
                }
            })
            .into(binding.videoPreview)
    }

    fun shouldKeepScreenOn(): Boolean =
        mIsPlaying || mPlayOnPrepared || mPendingPlayStart || mPendingAutoplayOnIdle

    private fun ensureTextureViewAttached(): TextureView? {
        mTextureView?.let { return it }
        val ctx = context ?: return null
        if (!::binding.isInitialized || mConfig.gestureVideoPlayer || mIsPanorama) {
            return null
        }
        val textureView = TextureView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            surfaceTextureListener = this@VideoFragment
        }
        mTextureView = textureView
        binding.videoSurfaceFrame.beVisible()
        binding.videoSurfaceFrame.alpha = 1f
        binding.videoSurfaceFrame.addView(textureView)
        setVideoSize()
        binding.videoSurfaceFrame.controller.resetState()
        return textureView
    }

    private fun detachTextureView() {
        val textureView = mTextureView ?: return
        mHasRenderedFirstFrame = false
        if (!mIsCapturingFrame) {
            textureView.surfaceTextureListener = null
        }
        if (!mIsViewDestroyed && ::binding.isInitialized) {
            binding.videoSurfaceFrame.removeView(textureView)
            // Keep the cached last frame visible instead of an empty/black surface frame.
            ensureCachedPreviewShown()
        }
        mTextureView = null
    }

    fun detachFromSharedPlayer() {
        if (!mUsingSharedPlayer && mExoPlayer == null) {
            return
        }

        val player = mExoPlayer
        mPlayerListener?.let { listener -> player?.removeListener(listener) }
        mPlayerListener = null
        stopTimer()

        if (player != null) {
            val ended = videoEnded()
            val pos = if (ended) 0L else player.currentPosition.coerceAtLeast(0L)
            mPositionAtPause = pos
            if (ended) {
                mCurrTime = 0L
                mPositionWhenInit = 0L
                removeCachedFrame(mMedium.path)
            }
            listener?.saveVideoPlaybackPosition(mMedium.path, pos)
            player.playWhenReady = false
            player.volume = 0f
            player.setVideoSurface(null)
        }

        val shouldCaptureBeforeRelease = mPendingFrameCaptureOnIdle || mIsCapturingFrame
        if (shouldCaptureBeforeRelease) {
            mPendingFrameCaptureOnIdle = false
            val surfaceToRelease = mVideoSurface
            val textureToRelease = mDeferredSurfaceTexture
            captureFrameSnapshot {
                if (mVideoSurface === surfaceToRelease) {
                    mVideoSurface = null
                }
                surfaceToRelease?.release()
                if (mDeferredSurfaceTexture === textureToRelease) {
                    mDeferredSurfaceTexture = null
                }
                textureToRelease?.release()
                if (!mIsFragmentVisible && !mIsViewDestroyed) {
                    detachTextureView()
                }
            }
            if (mIsCapturingFrame) {
                mVideoSurface = null
                mDeferredSurfaceTexture = null
            } else {
                mVideoSurface?.release()
                mVideoSurface = null
                mDeferredSurfaceTexture?.release()
                mDeferredSurfaceTexture = null
            }
        } else if (!mIsCapturingFrame) {
            mVideoSurface?.release()
            mVideoSurface = null
            mDeferredSurfaceTexture?.release()
            mDeferredSurfaceTexture = null
        }

        mExoPlayer = null
        mIsPlayerPrepared = false
        mPlayOnPrepared = false
        mPendingPlayStart = false
        mIsPlaying = false
        mUsingSharedPlayer = false
        mHasRenderedFirstFrame = false

        if (mWasFragmentInit && !mIsViewDestroyed && ::mPlayPauseButton.isInitialized) {
            mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_play_outline_vector)
        }
        ensureCachedPreviewShown()
    }

    private fun initExoPlayer(playOnInit: Boolean = false) {
        val shouldSkipInit = activity == null || mConfig.gestureVideoPlayer || mIsPanorama || mExoPlayer != null
        if (shouldSkipInit) return

        val textureView = ensureTextureViewAttached()

        // Keep an existing preview drawable or an in-flight default preview request so
        // the cover stays visible until ExoPlayer renders its first frame.
        if (VideoPlaybackUtils.shouldClearInFlightPreviewOnPlayerInit(
                hasPreviewDrawable = binding.videoPreview.drawable != null,
                isShowingDefaultPreview = mIsShowingDefaultPreview,
            )
        ) {
            mIsShowingDefaultPreview = false
            Glide.with(this).clear(binding.videoPreview)
        }

        val isContentUri = mMedium.path.startsWith("content://")
        val isEncrypted = CryptoManager.isEncryptedFile(mMedium.path)
        val uri = if (isContentUri) Uri.parse(mMedium.path) else Uri.fromFile(File(mMedium.path))

        val extractorsFactory = SeekableExtractorsFactory(SeekIndexCacheKey.fromPath(mMedium.path))
        val mediaSource: MediaSource = if (isEncrypted) {
            val factory = EncryptedMediaDataSource.Factory()
            ProgressiveMediaSource.Factory(factory, extractorsFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        } else {
            val factory = androidx.media3.datasource.DefaultDataSource.Factory(requireContext())
            ProgressiveMediaSource.Factory(factory, extractorsFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        }

        val initialPosition = VideoPlaybackUtils.resolvePlaybackPositionMs(
            positionWhenInitMs = mPositionWhenInit,
            positionAtPauseMs = mPositionAtPause,
            currentTimeMs = mCurrTime,
        )
        mPositionWhenInit = 0L

        val shouldStartPlayback = playOnInit || (mConfig.autoplayVideos && mIsFragmentVisible)
        mPlayOnPrepared = shouldStartPlayback

        val shared = listener?.acquireSharedExoPlayer(this)
        val player = if (shared != null) {
            mUsingSharedPlayer = true
            shared.apply {
                playWhenReady = false
                if (textureView?.surfaceTexture == null) {
                    setVideoSurface(null)
                }
            }
        } else {
            mUsingSharedPlayer = false
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    EXOPLAYER_MIN_BUFFER_MS,
                    EXOPLAYER_MAX_BUFFER_MS,
                    EXOPLAYER_PLAYBACK_BUFFER_MS,
                    EXOPLAYER_REBUFFER_BUFFER_MS
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            ExoPlayer.Builder(requireContext())
                // EXACT is required for correct skips: CLOSEST_SYNC snaps to the nearest keyframe,
                // so a 10 s forward skip could land at 8 s. Restore seeks are already skipped when
                // not needed below, so there is no redundant seek on setup.
                .setSeekParameters(SeekParameters.EXACT)
                .setLoadControl(loadControl)
                .build()
        }

        val startImmediately = shouldStartPlayback && !shouldWaitForOrientation()
        mExoPlayer = player.apply {
            repeatMode = if (mConfig.loopVideos && listener?.isSlideShowActive() == false) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            setPlaybackSpeed(mConfig.playbackSpeed)
            // Use slightly relaxed seek tolerance when resuming mid-video so ExoPlayer starts on a
            // nearby keyframe instead of decoding an entire GOP before rendering the first frame.
            setSeekParameters(if (initialPosition > 0L) RESUME_SEEK_PARAMETERS else SeekParameters.EXACT)
            setMediaSource(mediaSource, initialPosition)
            setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), false
            )
            initListeners()
            mHasRenderedFirstFrame = false
            textureView?.surfaceTexture?.let { surfaceTexture ->
                attachSurface(this, surfaceTexture)
            }
        }

        syncMuteStateOnStart()

        if (shouldStartPlayback) {
            mWasVideoStarted = true
            mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_pause_outline_vector)
            showVideoControls()
            if (listener?.updateVideoKeepScreenOn(true) != true) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // The playback itself is started once the player is ready. If orientation rotation is
            // not blocking us, set playWhenReady=true before prepare() to avoid an extra UI-thread
            // round-trip on STATE_READY.
            mPlayOnPrepared = true
            if (startImmediately) {
                player.playWhenReady = true
            }
        }

        player.prepare()
    }

    private fun ExoPlayer.initListeners() {
        mPlayerListener?.let { removeListener(it) }
        val playerListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                mHasRenderedFirstFrame = true
                // Fade the preview cover out over the real frame once this page is visible.
                if (mIsFragmentVisible) {
                    revealRenderedSurface()
                }
                if (mIsPlaying || mPlayOnPrepared || mExoPlayer?.isPlaying == true || mWasVideoStarted) {
                    binding.videoPlayOutline.beGone()
                } else if (mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
                    // With autoplay the video always resumes when the page becomes visible,
                    // so a paused frame here is just a transient state before that.
                    binding.videoPlayOutline.beGone()
                } else {
                    binding.videoPlayOutline.beVisible()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                @Player.DiscontinuityReason reason: Int,
            ) {
                // Reset progress views when video loops.
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    mSeekBar.progress = 0
                    mCurrTimeView.text = 0.getFormattedDuration()
                }
            }

            override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> videoPrepared()
                    Player.STATE_ENDED -> videoCompleted()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    mIsPlaying = true
                    startTimer()
                    if (VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                            isFragmentVisible = mIsFragmentVisible,
                            hasRenderedFirstFrame = mHasRenderedFirstFrame,
                            hasValidSurface = mVideoSurface?.isValid == true,
                        )
                    ) {
                        revealRenderedSurface()
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                mVideoSize.x = videoSize.width
                mVideoSize.y = (videoSize.height / videoSize.pixelWidthHeightRatio).toInt()
                setVideoSize()
                mVideoSizeKnown = true

                // The player knows the exact display size of the video, so the screen can be
                // rotated to its aspect ratio right before the playback starts.
                applyAspectRatioOrientation()
                startPendingPlayback()
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                binding.errorMessageHolder.errorMessage.apply {
                    if (error != null) {
                        if (mIsShowingDefaultPreview && isAdded) {
                            Glide.with(this@VideoFragment).clear(binding.videoPreview)
                        }
                        mIsShowingDefaultPreview = false
                        binding.videoPreview.alpha = 1f
                        binding.videoPreview.beGone()
                        binding.videoPlayOutline.beGone()
                        text = error.getFriendlyMessage(context)
                        setTextColor(if (context.config.blackBackground) Color.WHITE else context.getProperTextColor())
                        fadeIn()
                    } else {
                        beGone()
                        if (!mWasVideoStarted && !mIsPlaying && !mPlayOnPrepared) {
                            binding.videoPlayOutline.beVisible()
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                mHasAudio = tracks.containsType(C.TRACK_TYPE_AUDIO)
                updatePlayerMuteState()
            }
        }
        mPlayerListener = playerListener
        addListener(playerListener)
    }

    private fun toggleFullscreen() {
        listener?.fragmentClicked()
    }

    private fun handleDoubleTap(x: Float) {
        val viewWidth = mView.width
        val instantWidth = viewWidth / 7
        when {
            x <= instantWidth -> doSkip(false)
            x >= viewWidth - instantWidth -> doSkip(true)
            else -> togglePlayPause()
        }
    }

    private fun checkExtendedDetails() {
        // Encrypted files never show any extended details, no matter how the setting is configured.
        if (mConfig.showExtendedDetails && !CryptoManager.isEncryptedFile(mMedium.path)) {
            // getMediumExtendedDetails() reads Exif and queries MediaStore. Doing that in onResume
            // blocks the main thread exactly when a new video page settles; PhotoFragment already
            // does the same work in the background.
            val path = mMedium.path
            ensureBackgroundThread {
                val details = getMediumExtendedDetails(mMedium)
                activity?.runOnUiThread {
                    if (!isAdded || mMedium.path != path) {
                        return@runOnUiThread
                    }
                    binding.videoDetails.apply {
                        text = details
                        beVisibleIf(details.isNotEmpty())
                        alpha = if (!mConfig.hideExtendedDetails || !mIsFullscreen) 1f else 0f
                        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(this)
                    }
                }
            }
        } else {
            binding.videoDetails.beGone()
        }
    }

    private fun initTimeHolder() {
        mTimeHolder.beGoneIf(mIsFullscreen)
        mTimeHolder.alpha = if (mIsFullscreen) 0f else 1f
        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(mTimeHolder)
    }

    private fun checkIfPanorama() {
        try {
            val fis = FileInputStream(File(mMedium.path))
            fis.use {
                requireContext().parseFileChannel(mMedium.path, it.channel, 0, 0, 0) {
                    mIsPanorama = true
                }
            }
        } catch (ignored: Exception) {
        } catch (ignored: OutOfMemoryError) {
        }
    }

    private fun openPanorama() {
        TODO("Panorama is not yet implemented.")
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        mIsFullscreen = isFullscreen

        mSeekBar.setOnSeekBarChangeListener(if (mIsFullscreen) null else this)
        arrayOf(
            binding.bottomVideoTimeHolder.videoCurrTime,
            binding.bottomVideoTimeHolder.videoDuration,
            binding.bottomVideoTimeHolder.videoTogglePlayPause,
            binding.bottomVideoTimeHolder.videoPlaybackSpeed,
            binding.bottomVideoTimeHolder.videoToggleMute
        ).forEach {
            it.isClickable = !mIsFullscreen
        }

        mTimeHolder.animate().cancel()
        binding.bottomActionsDummy.animate().cancel()
        if (isFullscreen) {
            mTimeHolder.fadeOut(DEFAULT_ANIMATION_DURATION)
            binding.bottomActionsDummy.fadeOut(DEFAULT_ANIMATION_DURATION)
        } else {
            binding.bottomActionsDummy.beVisible()
            mTimeHolder.fadeIn(DEFAULT_ANIMATION_DURATION)
        }

        binding.videoDetails.apply {
            if (mStoredShowExtendedDetails && isVisible() && context != null && resources != null) {
                if (mStoredHideExtendedDetails) {
                    animate().alpha(if (isFullscreen) 0f else 1f).start()
                }
            }
        }
    }

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        childFragmentManager.beginTransaction().add(fragment, fragment::class.java.simpleName)
            .commit()
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        val isSlow = speed < 1f
        if (isSlow != binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag as? Boolean) {
            binding.bottomVideoTimeHolder.videoPlaybackSpeed.tag = isSlow

            val drawableId =
                if (isSlow) R.drawable.ic_playback_speed_slow_vector else R.drawable.ic_playback_speed_vector
            context?.let {
                binding.bottomVideoTimeHolder.videoPlaybackSpeed
                    .setDrawablesRelativeWithIntrinsicBounds(
                        AppCompatResources.getDrawable(it, drawableId)
                    )
            }
        }

        @SuppressLint("SetTextI18n")
        binding.bottomVideoTimeHolder.videoPlaybackSpeed.text =
            "${DecimalFormat("#.##").format(speed)}x"
        mExoPlayer?.setPlaybackSpeed(speed)
    }

    private fun skip(forward: Boolean) {
        if (mIsPanorama) {
            return
        } else if (mExoPlayer == null) {
            playVideo()
            return
        }

        mPositionAtPause = 0L
        doSkip(forward)
    }

    private fun doSkip(forward: Boolean) {
        val player = mExoPlayer ?: return

        val curr = player.currentPosition.coerceAtLeast(0L)
        val duration = VideoPlaybackUtils.effectiveDurationMs(player.duration, mDuration)
        val skipMs = mConfig.videoDoubleTapFastForward * 1000L
        val isLooping = (mConfig.loopVideos && listener?.isSlideShowActive() != true) ||
            player.repeatMode == Player.REPEAT_MODE_ONE

        val skipResult = VideoPlaybackUtils.computeSkipResult(
            currentPosMs = curr,
            skipMs = skipMs,
            durationMs = duration,
            forward = forward,
        )

        if (skipResult.reachedEnd) {
            if (isLooping) {
                player.setSeekParameters(SeekParameters.EXACT)
                setPosition(0L)
            } else {
                // ExoPlayer clamps a seek-to-end to the last frame and keeps playing. Finish the
                // video ourselves so the play icon appears and pressing play restarts from 0.
                player.playWhenReady = false
                mSkippedToEnd = true
                videoCompleted()
            }
            return
        }

        player.setSeekParameters(SeekParameters.EXACT)
        setPosition(skipResult.targetPositionMs)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            val newPosition = progress.toLong()
            if (mExoPlayer != null) {
                if (!mWasPlayerInited) {
                    mPositionWhenInit = newPosition
                }
                mExoPlayer?.setSeekParameters(SeekParameters.EXACT)
                setPosition(newPosition)
            }

            if (mExoPlayer == null) {
                mPositionAtPause = newPosition
                playVideo()
            }
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (mExoPlayer == null) {
            return
        }

        mExoPlayer!!.playWhenReady = false
        mIsDragged = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (mIsPanorama) {
            openPanorama()
            return
        }

        if (mExoPlayer == null) {
            return
        }

        if (mIsPlaying) {
            mExoPlayer!!.playWhenReady = true
        }

        mIsDragged = false
    }

    private fun togglePlayPause() {
        if (activity == null || !isAdded) {
            return
        }

        if (mIsPlaying) {
            pauseVideo()
        } else {
            playVideo()
        }
    }

    /**
     * A video takes the album-wide preference only when it actually starts for the first time,
     * or when it is played again after it ended. Videos that already started keep their own
     * state, so a preference change is never applied instantly to a playing video.
     */
    private fun syncMuteStateOnStart(restarted: Boolean = false) {
        if (restarted || !mHasStarted) {
            mHasStarted = true
            mIsMuted = mConfig.muteVideos
        }
        updatePlayerMuteState()
    }

    private fun updatePlayerMuteState(showToast: Boolean = false) {
        if (mHasAudio) {
            if (mIsMuted) mExoPlayer?.mute() else mExoPlayer?.unmute()
        } else if (showToast && mWasVideoStarted) {
            activity?.toast(R.string.video_no_sound)
        }

        // The icon must describe the audio the user will actually hear. Reading the player keeps
        // it correct after the shared player was detached (which resets its volume to 0) and
        // re-acquired by another page; fall back to this page's state before a player exists.
        val isMutedNow = mExoPlayer?.volume?.let { it == 0f } ?: mIsMuted
        val drawableId = when {
            !mHasAudio -> R.drawable.ic_vector_no_sound
            isMutedNow -> R.drawable.ic_vector_speaker_off
            else -> R.drawable.ic_vector_speaker_on
        }

        binding.bottomVideoTimeHolder.videoToggleMute.setImageResource(drawableId)
    }

    fun playVideo() {
        if (mExoPlayer == null) {
            initExoPlayer(playOnInit = true)
            return
        }

        if (shouldWaitForOrientation()) {
            // The screen has to be rotated to the aspect ratio of the video first, otherwise the
            // playback would start in the wrong orientation.
            mPendingPlayStart = true
            val orientationListener = listener
            if (orientationListener == null) {
                startPendingPlayback()
                return
            }

            orientationListener.applyAspectRatioOrientation(mMedium.path, 0, 0) {
                startPendingPlayback()
            }
            return
        }

        startPlayback()
    }

    /**
     * True if the screen has to be rotated to the aspect ratio of the video before the playback
     * may start.
     */
    private fun shouldWaitForOrientation(): Boolean {
        if (mVideoSizeKnown || mConfig.gestureVideoPlayer) {
            return false
        }

        return mConfig.screenRotation == ROTATE_BY_ASPECT_RATIO
    }

    /**
     * Starts the playback that was waiting for the screen to be rotated.
     */
    private fun startPendingPlayback() {
        if (!mPendingPlayStart || !isAdded) {
            return
        }

        mPendingPlayStart = false
        startPlayback()
    }

    /**
     * Rotates the screen to the aspect ratio of the video. The media player knows the exact
     * display size of the video, so the rotation can be applied right before the playback starts.
     */
    private fun applyAspectRatioOrientation() {
        if (mVideoSize.x <= 0 || mVideoSize.y <= 0) {
            return
        }

        listener?.applyAspectRatioOrientation(mMedium.path, mVideoSize.x, mVideoSize.y) {
            startPendingPlayback()
        }
    }

    private fun startPlayback() {
        // Playback is starting (or resuming), so the center play icon must go immediately,
        // not only once the first frame is rendered.
        binding.videoPlayOutline.beGone()

        val textureView = ensureTextureViewAttached()
        val player = mExoPlayer
        if (player != null && mVideoSurface?.isValid != true) {
            textureView?.surfaceTexture?.let { surfaceTexture ->
                attachSurface(player, surfaceTexture)
            }
        }

        val wasEnded = videoEnded()
        if (wasEnded) {
            setPosition(0)
            // Replaying a finished video picks up the current album preference again.
            syncMuteStateOnStart(restarted = true)
        }

        if (mStoredRememberLastVideoPosition && !mWasLastPositionRestored) {
            mWasLastPositionRestored = true
            restoreLastVideoSavedPosition()
        }

        if (!wasEnded || !mConfig.loopVideos) {
            mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_pause_outline_vector)
        }

        if (!mWasVideoStarted) {
            showVideoControls()
        }

        mWasVideoStarted = true
        if (mIsPlayerPrepared) {
            mIsPlaying = true
            mPlayOnPrepared = false
        } else {
            mPlayOnPrepared = true
        }
        if (mExoPlayer?.playbackState == Player.STATE_IDLE) {
            mExoPlayer?.prepare()
        }
        mExoPlayer?.playWhenReady = true
        if (VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = mIsFragmentVisible,
                hasRenderedFirstFrame = mHasRenderedFirstFrame,
                hasValidSurface = mVideoSurface?.isValid == true,
            )
        ) {
            revealRenderedSurface()
        }
        startTimer()
        if (listener?.updateVideoKeepScreenOn(true) != true) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun pauseVideo(allowFrameCapture: Boolean = true, deferFrameCapture: Boolean = false) {
        if (mExoPlayer == null) {
            return
        }

        mPlayOnPrepared = false
        // A playback that was waiting for the screen rotation must not start anymore.
        mPendingPlayStart = false
        mIsPlaying = false
        stopTimer()
        val ended = videoEnded()
        if (!ended) {
            mExoPlayer?.playWhenReady = false
        }

        mPlayPauseButton.setImageResource(org.fossify.commons.R.drawable.ic_play_outline_vector)
        if (listener?.updateVideoKeepScreenOn(false) != true) {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        mPositionAtPause = if (ended) 0L else (mExoPlayer?.currentPosition ?: 0L)
        listener?.saveVideoPlaybackPosition(mMedium.path, mPositionAtPause)

        if (ended) {
            removeCachedFrame(mMedium.path)
        } else if ((mPositionAtPause > 0L || mHasRenderedFirstFrame) && allowFrameCapture) {
            if (deferFrameCapture) {
                mPendingFrameCaptureOnIdle = true
            } else {
                captureFrameSnapshot()
            }
        }
    }

    private fun captureFrameSnapshot(onComplete: (() -> Unit)? = null) {
        val textureView = mTextureView
        val surface = mVideoSurface
        if (!mWasFragmentInit || textureView == null || !textureView.isAvailable ||
            textureView.width <= 0 || textureView.height <= 0 ||
            surface == null || !surface.isValid
        ) {
            onComplete?.invoke()
            return
        }
        if (mIsCapturingFrame) {
            // Never issue a second PixelCopy against the same surface; queue the follow-up
            // (surface release / detach) until the in-flight readback has completed.
            val pending = mPendingCaptureCompletion
            mPendingCaptureCompletion = if (pending != null && onComplete != null) {
                { pending(); onComplete() }
            } else {
                onComplete ?: pending
            }
            return
        }
        // Reuse a snapshot only when it still matches the position we are leaving at. A frame
        // captured during an earlier visit is stale and caused the "old thumbnail -> new
        // thumbnail" flash when swiping back to the video.
        val capturePosition = if (mPositionAtPause > 0L) mPositionAtPause else mCurrTime
        val cached = getCachedFrame(mMedium.path)
        val cachedPosition = sVideoFramePositions[mMedium.path]
        if (cached != null && !cached.isRecycled &&
            VideoPlaybackUtils.isCachedFramePositionFresh(
                cachedPositionMs = cachedPosition,
                capturePositionMs = capturePosition,
                toleranceMs = VIDEO_FRAME_POSITION_TOLERANCE_MS,
            )
        ) {
            // Replace even an already loaded generic thumbnail: the cached bitmap is the frame
            // at the real last position, which is what must be shown on the way back.
            if (!mIsFragmentVisible && !mIsViewDestroyed && ::binding.isInitialized) {
                showPreviewBitmap(cached)
            }
            onComplete?.invoke()
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onComplete?.invoke()
            return
        }

        try {
            // Use ONE scale factor for both axes so aspect ratio is always preserved, and keep
            // the longest edge capped so the readback is fast and fits comfortably in sVideoFrameCache.
            val sourceWidth = textureView.width
            val sourceHeight = textureView.height
            val scale = minOf(1f, CACHED_FRAME_MAX_DIMENSION / maxOf(sourceWidth, sourceHeight))
            val scaleWidth = maxOf(1, (sourceWidth * scale).toInt())
            val scaleHeight = maxOf(1, (sourceHeight * scale).toInt())
            val bmp = Bitmap.createBitmap(scaleWidth, scaleHeight, Bitmap.Config.ARGB_8888)
            val path = mMedium.path
            mIsCapturingFrame = true
            android.view.PixelCopy.request(
                surface,
                bmp,
                { result ->
                    mIsCapturingFrame = false
                    if (result == android.view.PixelCopy.SUCCESS) {
                        putCachedFrame(path, bmp, capturePosition)
                        if (!mIsFragmentVisible && !mIsViewDestroyed && isAdded && ::binding.isInitialized) {
                            showPreviewBitmap(bmp)
                        }
                    } else {
                        bmp.recycle()
                    }
                    // The TextureView may have been removed while PixelCopy was pending. Release
                    // the retained SurfaceTexture only if it was not reused by a reattached view.
                    releaseCaptureTextureIfDetached()
                    val queued = mPendingCaptureCompletion
                    mPendingCaptureCompletion = null
                    queued?.invoke()
                    onComplete?.invoke()
                },
                mTimerHandler
            )
        } catch (_: Throwable) {
            mIsCapturingFrame = false
            releaseCaptureTextureIfDetached()
            onComplete?.invoke()
        }
    }

    private fun releaseCaptureTextureIfDetached() {
        val texture = mCapturePendingTexture ?: return
        mCapturePendingTexture = null
        if (texture !== mTextureView?.surfaceTexture) {
            try {
                texture.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun restoreSessionPlaybackPosition() {
        val position = listener?.getVideoPlaybackPosition(mMedium.path) ?: return
        if (position <= 0L) {
            return
        }

        mCurrTime = position
        mPositionWhenInit = position
        mPositionAtPause = position
    }

    private fun videoEnded(): Boolean {
        val player = mExoPlayer
        val currentPos = player?.currentPosition ?: 0L
        val duration = VideoPlaybackUtils.effectiveDurationMs(player?.duration ?: C.TIME_UNSET, mDuration)
        return VideoPlaybackUtils.isVideoEnded(
            skippedToEnd = mSkippedToEnd,
            playbackState = player?.playbackState,
            currentPosMs = currentPos,
            durationMs = duration,
        )
    }

    private fun setPosition(milliseconds: Long) {
        val safeMs = milliseconds.coerceAtLeast(0L)
        mSkippedToEnd = false
        mCurrTime = safeMs
        mExoPlayer?.seekTo(safeMs)
        mSeekBar.progress = VideoPlaybackUtils.toSeekBarProgress(safeMs, mSeekBar.max)
        mCurrTimeView.text = safeMs.getFormattedDuration()

        if (!mIsPlaying) {
            mPositionAtPause = safeMs
        }
    }

    private fun setupVideoDuration() {
        val durationFromMedium = mMedium.videoDuration.times(1000L).coerceAtLeast(0L)
        if (durationFromMedium > 0L) {
            mDuration = durationFromMedium
            setupTimeHolder()
            val targetPosition = when {
                mPositionWhenInit > 0L -> mPositionWhenInit
                mPositionAtPause > 0L -> mPositionAtPause
                mCurrTime > 0L -> mCurrTime
                else -> 0L
            }
            if (targetPosition > 0L) {
                setPosition(targetPosition)
            }
        }
    }

    private fun probeMetadataIfNeeded() {
        if (!mPendingMetadataProbe || !isAdded) {
            return
        }
        // On an autoplay page ExoPlayer itself reports duration and video size a moment later.
        // Running MediaMetadataRetriever/getVideoResolution at the same time just competes with
        // the player for I/O and delays the first rendered frame after every swipe-back.
        if (mIsFragmentVisible && mConfig.autoplayVideos && !mConfig.gestureVideoPlayer) {
            mPendingMetadataProbe = false
            return
        }

        mPendingMetadataProbe = false

        val needResolution = !mVideoSizeKnown && mExoPlayer == null
        val needDuration = mDuration <= 0L && mExoPlayer == null
        if (!needResolution && !needDuration) {
            return
        }

        val path = mMedium.path
        val appContext = context?.applicationContext ?: return
        ensureBackgroundThread {
            val resolution = if (needResolution) activity?.getVideoResolution(path) else null
            val probedDuration = if (needDuration) {
                if (CryptoManager.isEncryptedFile(path)) {
                    (CryptoManager.readMetadata(File(path))?.videoDuration ?: 0).times(1000L).coerceAtLeast(0L)
                } else {
                    appContext.getDuration(path)?.times(1000L)?.coerceAtLeast(0L) ?: 0L
                }
            } else {
                0L
            }

            activity?.runOnUiThread {
                if (!isAdded || mIsViewDestroyed || mMedium.path != path) {
                    return@runOnUiThread
                }
                if (needResolution) {
                    if (resolution != null) {
                        mVideoSize.x = resolution.x
                        mVideoSize.y = resolution.y
                        listener?.applyAspectRatioOrientation(path, resolution.x, resolution.y)
                    } else if (mIsFragmentVisible) {
                        listener?.applyAspectRatioOrientation(path, 0, 0)
                    }
                }
                if (needDuration) {
                    if (probedDuration > 0L && mDuration <= 0L) {
                        mDuration = probedDuration
                        setupTimeHolder()
                    } else if (mDuration <= 0L) {
                        setupTimeHolder()
                    }
                    if (!mIsPlayerPrepared && !mWasVideoStarted) {
                        val targetPosition = when {
                            mPositionWhenInit > 0L -> mPositionWhenInit
                            mPositionAtPause > 0L -> mPositionAtPause
                            mCurrTime > 0L -> mCurrTime
                            else -> 0L
                        }
                        if (targetPosition > 0L) {
                            if (mExoPlayer == null) {
                                setPosition(targetPosition)
                            } else {
                                mSeekBar.progress = VideoPlaybackUtils.toSeekBarProgress(targetPosition, mSeekBar.max)
                                mCurrTimeView.text = targetPosition.getFormattedDuration()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun videoPrepared() {
        mExoPlayer?.setSeekParameters(SeekParameters.EXACT)
        val playerDuration = VideoPlaybackUtils.sanitizeDurationMs(mExoPlayer?.duration ?: C.TIME_UNSET)
        if (playerDuration > 0L && (mDuration <= 0L || abs(mDuration - playerDuration) >= 1000L)) {
            mDuration = playerDuration
            setupTimeHolder()
        } else if (mDuration <= 0L) {
            setupTimeHolder()
        }
        val currentPos = (mExoPlayer?.currentPosition ?: mCurrTime).coerceAtLeast(0L)
        mCurrTime = currentPos
        mSeekBar.progress = VideoPlaybackUtils.toSeekBarProgress(currentPos, mSeekBar.max)
        mCurrTimeView.text = currentPos.getFormattedDuration()

        mIsPlayerPrepared = true
        val shouldStart = mPlayOnPrepared || mExoPlayer?.playWhenReady == true
        if (shouldStart && !mIsPlaying) {
            if (mIsFragmentVisible) {
                mPlayOnPrepared = false
                updatePlaybackSpeed(mConfig.playbackSpeed)
                playVideo()
            } else {
                // The page was left while the player was preparing. Keep the request so
                // setMenuVisibility(true) can start it instead of dropping it forever.
                mPlayOnPrepared = true
            }
        } else {
            mPlayOnPrepared = false
            listener?.updateVideoKeepScreenOn(shouldKeepScreenOn())
        }
        mWasPlayerInited = true
        // Do NOT hide the preview here: STATE_READY can arrive before the (possibly new)
        // TextureView has rendered anything. Hiding it now is the black-flash source.
        // onRenderedFirstFrame() is the only place that may remove the preview.
    }
    private fun videoCompleted() {
        val player = mExoPlayer
        if (!isAdded || player == null) {
            return
        }

        val completedDuration = VideoPlaybackUtils.effectiveDurationMs(
            player.duration,
            maxOf(mDuration, player.currentPosition.coerceAtLeast(0L)),
        )
        if (completedDuration > 0L && mDuration <= 0L) {
            mDuration = completedDuration
            setupTimeHolder()
        }
        mCurrTime = completedDuration
        if (listener?.videoEnded() == false && mConfig.loopVideos) {
            playVideo()
        } else {
            mSeekBar.progress = mSeekBar.max
            mCurrTimeView.text = completedDuration.getFormattedDuration()
            pauseVideo()
        }
    }

    private fun cleanup() {
        mPendingAutoplayOnIdle = false
        mPendingInitOnIdle = false
        mPendingFrameCaptureOnIdle = false
        mPendingPreviewOnIdle = false
        pauseVideo(allowFrameCapture = false)
        releaseExoPlayer(allowDefer = true)

        if (mWasFragmentInit) {
            mCurrTimeView.text = 0.getFormattedDuration()
            mSeekBar.progress = 0
            stopTimer()
            mTimerHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun attachSurface(player: ExoPlayer, surfaceTexture: SurfaceTexture) {
        val oldSurface = mVideoSurface
        val oldDeferredTexture = mDeferredSurfaceTexture
        val surface = Surface(surfaceTexture)
        mHasRenderedFirstFrame = false
        mVideoSurface = surface
        mDeferredSurfaceTexture = null
        player.setVideoSurface(surface)
        oldSurface?.release()
        if (oldDeferredTexture != null && oldDeferredTexture !== surfaceTexture) {
            oldDeferredTexture.release()
        }
    }

    private fun releaseExoPlayer(allowDefer: Boolean = false) {
        if (mUsingSharedPlayer) {
            detachFromSharedPlayer()
            listener?.releaseSharedExoPlayerOwnership(this)
            return
        }

        mIsPlayerPrepared = false
        mPlayOnPrepared = false
        mHasRenderedFirstFrame = false
        val player = mExoPlayer ?: return
        mPlayerListener?.let { player.removeListener(it) }
        mPlayerListener = null
        val surface = mVideoSurface
        val surfaceTexture = mDeferredSurfaceTexture
        mExoPlayer = null
        mVideoSurface = null
        mDeferredSurfaceTexture = null

        // Stop playback and silence audio immediately without blocking the UI thread.
        player.playWhenReady = false
        player.volume = 0f

        val holder = DeferredPlayerRelease(player, surface, surfaceTexture)
        val releaseAction: () -> Unit = {
            if (mActiveDeferredRelease === holder) {
                mActiveDeferredRelease = null
            }
            try {
                holder.player.setVideoSurface(null)
                holder.player.stop()
                holder.player.release()
            } catch (_: Throwable) {
            }
            try {
                holder.surface?.release()
            } catch (_: Throwable) {
            }
            try {
                holder.surfaceTexture?.release()
            } catch (_: Throwable) {
            }
        }

        if (allowDefer && listener?.isPagerScrolling() == true) {
            mActiveDeferredRelease = holder
            listener?.deferPlayerRelease(releaseAction)
        } else {
            releaseAction()
        }
        ensureCachedPreviewShown()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = mIsFragmentVisible,
                isViewDestroyed = mIsViewDestroyed,
                hasPlayer = mExoPlayer != null,
                isPlaying = mIsPlaying,
                isPlayerPrepared = mIsPlayerPrepared,
                playWhenReady = mExoPlayer?.playWhenReady == true,
                hasValidSurface = mVideoSurface?.isValid == true,
                isMatchingTexture = surface === mTextureView?.surfaceTexture,
            )
        ) {
            mHasRenderedFirstFrame = true
            revealRenderedSurface()
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mHasRenderedFirstFrame = false
        val deferred = mActiveDeferredRelease
        if (deferred != null) {
            deferred.surfaceTexture = surface
            if (!mIsViewDestroyed && ::binding.isInitialized) {
                ensureCachedPreviewShown()
            }
            return false
        }
        // PixelCopy is asynchronous. Destroying the SurfaceTexture while a readback is still in
        // flight makes it fail, so the cached last frame is never produced and the next visit
        // falls back to the generic thumbnail / black. Keep it until the callback releases it.
        if (mIsCapturingFrame) {
            mCapturePendingTexture = surface
            if (!mIsViewDestroyed && ::binding.isInitialized) {
                ensureCachedPreviewShown()
            }
            return false
        }
        if (mExoPlayer != null && !mUsingSharedPlayer) {
            mDeferredSurfaceTexture = surface
            if (!mIsViewDestroyed && ::binding.isInitialized) {
                ensureCachedPreviewShown()
            }
            return false
        }
        if (mUsingSharedPlayer) {
            mExoPlayer?.setVideoSurface(null)
            mVideoSurface?.release()
            mVideoSurface = null
        }
        if (!mIsViewDestroyed && ::binding.isInitialized) {
            ensureCachedPreviewShown()
        }
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        mExoPlayer?.let { attachSurface(it, surface) }
    }

    private fun setVideoSize() {
        val textureView = mTextureView ?: return
        if (activity == null || mConfig.gestureVideoPlayer || mVideoSize.x <= 0 || mVideoSize.y <= 0) return

        val videoProportion = mVideoSize.x.toFloat() / mVideoSize.y.toFloat()
        val display = requireActivity().windowManager.defaultDisplay
        val screenWidth: Int
        val screenHeight: Int

        val realMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        screenWidth = realMetrics.widthPixels
        screenHeight = realMetrics.heightPixels

        val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()

        val lp = (textureView.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
        if (videoProportion > screenProportion) {
            lp.width = screenWidth
            lp.height = (screenWidth.toFloat() / videoProportion).toInt()
        } else {
            lp.width = (videoProportion * screenHeight.toFloat()).toInt()
            lp.height = screenHeight
        }
        textureView.layoutParams = lp
    }

    private fun handleTouchHoldEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (mIsPlaying && event.pointerCount == 1) {
                    mInitialX = event.x
                    mInitialY = event.y
                    mTimerHandler.postDelayed(mTouchHoldRunnable, TOUCH_HOLD_DURATION_MS)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.x - mInitialX)
                val deltaY = abs(event.y - mInitialY)
                if (!mIsLongPressActive && (deltaX > mTouchSlop || deltaY > mTouchSlop)) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!mIsLongPressActive) {
                    mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mTimerHandler.removeCallbacks(mTouchHoldRunnable)
                stopHoldSpeedMultiplierGesture()
            }
        }
    }

    private fun stopHoldSpeedMultiplierGesture() {
        if (mIsLongPressActive) {
            updatePlaybackSpeed(mOriginalPlaybackSpeed)
            mIsLongPressActive = false
            mPlaybackSpeedPill.fadeOut()
        }
    }
}
