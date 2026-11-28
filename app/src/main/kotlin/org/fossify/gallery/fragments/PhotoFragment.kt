package org.fossify.gallery.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.updateLayoutParams
import androidx.exifinterface.media.ExifInterface
import androidx.viewpager.widget.ViewPager
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270
import androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90
import com.alexvasilkov.gestures.GestureController
import com.alexvasilkov.gestures.State
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.davemorrissey.labs.subscaleview.DecoderFactory
import com.davemorrissey.labs.subscaleview.ImageDecoder
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.fadeOut
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.portrait
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.DEFAULT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.R
import org.fossify.gallery.activities.BaseViewerActivity
import org.fossify.gallery.activities.PhotoActivity
import org.fossify.gallery.activities.PhotoVideoActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.adapters.PortraitPhotosAdapter
import org.fossify.gallery.databinding.PagerPhotoItemBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getBottomActionsHeight
import org.fossify.gallery.extensions.sendFakeClick
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.MAX_ZOOM_EQUALITY_TOLERANCE
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.MyGlideImageDecoder
import org.fossify.gallery.helpers.PicassoRegionDecoder
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.models.Medium
import org.fossify.gallery.helpers.media.IsolatedGifClient
import org.fossify.gallery.helpers.media.IsolatedGifDrawable
import org.fossify.gallery.helpers.media.IsolatedGlideOptions
import org.fossify.gallery.helpers.media.PhotoSwipeOptimizer
import org.fossify.gallery.helpers.media.orientationToDegrees
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

class PhotoFragment : ViewPagerFragment() {
    private val DEFAULT_DOUBLE_TAP_ZOOM = 2f
    private val SAME_ASPECT_RATIO_THRESHOLD = 0.01

    var mCurrentRotationDegrees = 0
    private var mIsFragmentVisible = false
    private var mIsFullscreen = false
    private var mWasInit = false
    private var mIsPanorama = false
    private var mIsSubsamplingVisible = false    // checking view.visibility is unreliable, use an extra variable for it
    private var mShouldResetImage = false
    private var mCurrentPortraitPhotoPath = ""
    private var mOriginalPath = ""
    private var mImageOrientation = -1
    private var mLoadZoomableViewHandler = Handler()
    private var mScreenWidth = 0
    private var mScreenHeight = 0
    private var mCurrentGestureViewZoom = 1f
    private var mInitialZoom = 1f
    private var mHasInitialZoom = false
    private var mIsolatedGifDrawable: IsolatedGifDrawable? = null
    private var mIsLoadingGif = false
    private var mIsAnimatedMedia = false
    private var mGifLoadGeneration = 0
    private var mPendingZoomableViewOnIdle = false
    private var mPendingColorModeOnIdle = false

    private var mStoredShowExtendedDetails = false
    private var mStoredHideExtendedDetails = false
    private var mStoredAllowDeepZoomableImages = false
    private var mStoredShowHighestQuality = false
    private var mStoredExtendedDetails = 0

    private lateinit var mView: ViewGroup
    private lateinit var binding: PagerPhotoItemBinding
    private lateinit var mMedium: Medium

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val activity = requireActivity()
        val arguments = requireArguments()

        binding = PagerPhotoItemBinding.inflate(inflater, container, false)
        mView = binding.root
        if (!arguments.getBoolean(SHOULD_INIT_FRAGMENT, true)) {
            return mView
        }

        mMedium = arguments.getSerializable(MEDIUM) as Medium
        mOriginalPath = mMedium.path

        binding.apply {
            subsamplingView.setOnClickListener { photoClicked() }
            gesturesView.setOnClickListener { photoClicked() }
            instantPrevItem.setOnClickListener { listener?.goToPrevItem() }
            instantNextItem.setOnClickListener { listener?.goToNextItem() }
            panoramaOutline.setOnClickListener { openPanorama() }

            instantPrevItem.parentView = container
            instantNextItem.parentView = container

            photoBrightnessController.initialize(activity, slideInfo, true, container, singleTap = { x, y ->
                mView.apply {
                    if (subsamplingView.isVisible()) {
                        subsamplingView.sendFakeClick(x, y)
                    } else {
                        gesturesView.sendFakeClick(x, y)
                    }
                }
            })

            setupGesturesViewStateListener()
            gesturesView.setOnTouchListener { v, event ->
                val allowDownGesture = context.config.allowDownGesture
                if (allowDownGesture && abs(mCurrentGestureViewZoom - mInitialZoom) < MAX_ZOOM_EQUALITY_TOLERANCE) {
                    handleEvent(event)
                }
                false
            }

            subsamplingView.setOnTouchListener { v, event ->
                if (subsamplingView.isZoomedOut() && context.config.allowDownGesture) handleEvent(event)
                false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.photoHolder) { _, insets ->
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
            binding.bottomActionsDummy.updateLayoutParams<ViewGroup.LayoutParams> {
                height = resources.getBottomActionsHeight() + system.bottom
            }
            insets
        }

        checkScreenDimensions()
        storeStateVariables()
        if (!mIsFragmentVisible && activity is PhotoActivity) {
            mIsFragmentVisible = true
        }

        if (mMedium.path.startsWith("content://") && !mMedium.path.startsWith("content://mms/")) {
            mMedium.path = requireContext().getRealPathFromURI(Uri.parse(mOriginalPath)) ?: mMedium.path
            if (isRPlus() && !isExternalStorageManager() && mMedium.path.startsWith("/storage/") && mMedium.isHidden()) {
                mMedium.path = mOriginalPath
            }

            if (mMedium.path.isEmpty()) {
                mIsFullscreen = listener?.isFullScreen() == true
                if (mIsFullscreen) {
                    binding.bottomActionsDummy.beGone()
                }
                mWasInit = true
                updateInstantSwitchWidths()
                ensureBackgroundThread {
                    if (cacheEmptyContentUriPath()) {
                        activity?.runOnUiThread {
                            if (isAdded) {
                                listener?.applyAspectRatioOrientation(mMedium.path, 0, 0)
                                loadImage()
                                initExtendedDetails()
                            }
                        }
                    } else {
                        activity?.runOnUiThread {
                            activity?.toast(org.fossify.commons.R.string.unknown_error_occurred)
                        }
                    }
                }
                return mView
            }
        }

        mIsFullscreen = listener?.isFullScreen() == true
        if (mIsFullscreen) {
            binding.bottomActionsDummy.beGone()
        }

        // Rotate the screen to the aspect ratio of the photo as soon as its dimensions are known,
        // so that the photo is not shown in the wrong orientation.
        listener?.applyAspectRatioOrientation(mMedium.path, 0, 0)

        loadImage()
        initExtendedDetails()
        mWasInit = true
        updateInstantSwitchWidths()

        return mView
    }

    private fun cacheEmptyContentUriPath(): Boolean {
        val ctx = context ?: return false
        val cached = PhotoSwipeOptimizer.cacheEmptyContentUriToFile(ctx, mOriginalPath) ?: return false
        mMedium.path = cached
        return true
    }

    override fun onPause() {
        super.onPause()
        mIsolatedGifDrawable?.stop()
        storeStateVariables()
    }

    override fun onResume() {
        super.onResume()
        val config = requireContext().config
        if (mWasInit && (config.showExtendedDetails != mStoredShowExtendedDetails || config.extendedDetails != mStoredExtendedDetails)) {
            initExtendedDetails()
        }

        if (mWasInit) {
            if (config.allowZoomingImages != mStoredAllowDeepZoomableImages || config.showHighestQuality != mStoredShowHighestQuality) {
                mIsSubsamplingVisible = false
                binding.subsamplingView.beGone()
                loadImage()
            } else if (mIsAnimatedMedia) {
                val existingGif = mIsolatedGifDrawable
                if (existingGif == null) {
                    loadGif()
                } else {
                    existingGif.start()
                }
            } else if (mIsSubsamplingVisible && mShouldResetImage) {
                binding.subsamplingView.onGlobalLayout {
                    binding.subsamplingView.resetView()
                }
            }
            mShouldResetImage = false
        }

        val allowPhotoGestures = config.allowPhotoGestures
        val allowInstantChange = config.allowInstantChange

        binding.apply {
            photoBrightnessController.beVisibleIf(allowPhotoGestures)
            instantPrevItem.beVisibleIf(allowInstantChange)
            instantNextItem.beVisibleIf(allowInstantChange)
        }

        reapplyColorModeIfNeeded()
        storeStateVariables()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mGifLoadGeneration++
        mIsLoadingGif = false
        clearIsolatedGifDrawable()
        if (activity?.isDestroyed == false) {
            binding.subsamplingView.recycle()

            try {
                if (context != null) {
                    Glide.with(requireContext()).clear(binding.gesturesView)
                }
            } catch (ignored: Exception) {
            }
        }

        mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!mWasInit) {
            return
        }

        if (mIsAnimatedMedia) {
            mView.onGlobalLayout {
                if (activity != null) {
                    measureScreen()
                    Handler().postDelayed({
                        binding.gesturesView.controller.resetState()
                        loadGif(forceReload = true)
                    }, 50)
                }
            }
        } else {
            hideZoomableView()
            loadImage()
        }

        measureScreen()
        initExtendedDetails()
        updateInstantSwitchWidths()
        mShouldResetImage = true
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        mIsFragmentVisible = menuVisible
        if (mWasInit) {
            if (mIsAnimatedMedia) {
                if (menuVisible && isResumed) {
                    mIsolatedGifDrawable?.start()
                } else if (!menuVisible && listener?.isPagerScrolling() != true) {
                    mIsolatedGifDrawable?.stop()
                }
            } else {
                photoFragmentVisibilityChanged(menuVisible)
            }
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        if (!mWasInit) return
        when (state) {
            ViewPager.SCROLL_STATE_DRAGGING -> {
                mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
                if (mIsAnimatedMedia && isResumed) mIsolatedGifDrawable?.start()
            }
            ViewPager.SCROLL_STATE_IDLE -> {
                if (mIsAnimatedMedia) {
                    if (mIsFragmentVisible && isResumed) mIsolatedGifDrawable?.start() else mIsolatedGifDrawable?.stop()
                } else if (mIsFragmentVisible) {
                    if (mPendingColorModeOnIdle) {
                        mPendingColorModeOnIdle = false
                        applyProperColorMode(binding.gesturesView.drawable)
                    }
                    if (mPendingZoomableViewOnIdle) {
                        mPendingZoomableViewOnIdle = false
                        scheduleZoomableView()
                    }
                } else {
                    mPendingZoomableViewOnIdle = false
                    mPendingColorModeOnIdle = false
                }
            }
        }
    }

    private fun storeStateVariables() {
        requireContext().config.apply {
            mStoredShowExtendedDetails = showExtendedDetails
            mStoredHideExtendedDetails = hideExtendedDetails
            mStoredAllowDeepZoomableImages = allowZoomingImages
            mStoredShowHighestQuality = showHighestQuality
            mStoredExtendedDetails = extendedDetails
        }
    }

    private fun checkScreenDimensions() {
        if (mScreenWidth == 0 || mScreenHeight == 0) {
            measureScreen()
        }
    }

    private fun measureScreen() {
        val metrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getRealMetrics(metrics)
        mScreenWidth = metrics.widthPixels
        mScreenHeight = metrics.heightPixels
    }

    private fun photoFragmentVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            if (listener?.isPagerScrolling() == true) {
                mPendingColorModeOnIdle = true
                mPendingZoomableViewOnIdle = true
            } else {
                mPendingColorModeOnIdle = false
                applyProperColorMode(binding.gesturesView.drawable)
                scheduleZoomableView()
            }
        } else {
            mPendingColorModeOnIdle = false
            mPendingZoomableViewOnIdle = false
            hideZoomableView()
        }
    }

    private fun degreesForRotation(orientation: Int) = orientationToDegrees(orientation)

    private fun loadImage() {
        mHasInitialZoom = false
        checkScreenDimensions()

        if (mMedium.isPortrait() && context != null) {
            showPortraitStripe()
        }

        val pathToLoad = getPathToLoad(mMedium)
        if (!mMedium.isGIF() && !mMedium.isSVG() && PhotoSwipeOptimizer.isDefinitelyStaticByPath(pathToLoad)) {
            mIsAnimatedMedia = false
            loadBitmap()
            return
        }

        ensureBackgroundThread {
            val ctx = context
            val isAnimated = mMedium.isGIF() ||
                (ctx != null && IsolatedGifClient.isAnimatedMediaCandidate(ctx, pathToLoad))
            mIsAnimatedMedia = isAnimated
            activity?.runOnUiThread {
                when {
                    isAnimated -> loadGif()
                    mMedium.isSVG() -> loadSVG()
                    else -> loadBitmap()
                }
            }
        }
    }

    private fun clearIsolatedGifDrawable() {
        val existing = mIsolatedGifDrawable ?: return
        mIsolatedGifDrawable = null
        if (view != null) {
            binding.gesturesView.setImageDrawable(null)
        }
        existing.close()
    }

    private fun loadGif(forceReload: Boolean = false) {
        if (!forceReload && (mIsolatedGifDrawable != null || mIsLoadingGif)) {
            return
        }

        val ctx = context ?: return
        if (forceReload) {
            clearIsolatedGifDrawable()
        }
        val generation = ++mGifLoadGeneration
        mIsLoadingGif = true
        val pathToLoad = getPathToLoad(mMedium)

        val maxGifDim = maxOf(mScreenWidth, mScreenHeight)
            .coerceIn(0, org.fossify.gallery.helpers.media.IsolatedGifTimingPolicy.MAX_GIF_DIMENSION)
        ensureBackgroundThread {
            val drawable = runCatching {
                IsolatedGifClient.openGifSession(ctx, pathToLoad, maxGifDim, maxGifDim)
            }.getOrNull()

            activity?.runOnUiThread {
                if (!isAdded || context == null || generation != mGifLoadGeneration) {
                    drawable?.close()
                    if (generation == mGifLoadGeneration) {
                        mIsLoadingGif = false
                    }
                    return@runOnUiThread
                }

                mIsLoadingGif = false
                if (drawable == null) {
                    loadBitmap()
                    return@runOnUiThread
                }

                clearIsolatedGifDrawable()
                mIsolatedGifDrawable = drawable
                binding.gesturesView.beVisible()
                binding.gesturesView.setImageDrawable(drawable)
                applyProperColorMode(drawable)
                if (PhotoSwipeOptimizer.shouldKeepAnimatedPlaying(
                        mIsFragmentVisible,
                        isResumed,
                        listener?.isPagerScrolling() == true,
                    )
                ) {
                    drawable.start()
                }
            }
        }
    }

    private fun loadSVG() {
        loadBitmap(addZoomableView = false)
    }

    private fun loadBitmap(addZoomableView: Boolean = true) {
        mHasInitialZoom = false
        if (context == null) return
        loadWithGlide(getFilePathToShow(), addZoomableView)
    }

    private fun loadWithGlide(path: String, addZoomableView: Boolean) {
        val priority = if (mIsFragmentVisible) Priority.IMMEDIATE else Priority.NORMAL
        val isEncrypted = CryptoManager.isEncryptedFile(path)
        val includeGainmap = ColorModeHelper.isGainmapSupported() && (context?.config?.ultraHdrRendering ?: true)
        val options = RequestOptions()
            .signature(mMedium.getKey())
            .format(DecodeFormat.PREFER_ARGB_8888)
            .set(IsolatedGlideOptions.INCLUDE_GAINMAP, includeGainmap)
            .priority(priority)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(isEncrypted)
            .fitCenter()
            .run {
                PhotoSwipeOptimizer.resolveGlideTargetDimensions(
                    mScreenWidth,
                    mScreenHeight,
                    mStoredShowHighestQuality,
                )?.let { (w, h) -> override(w, h) } ?: this
            }
            .run {
                if (mCurrentRotationDegrees != 0) {
                    transform(Rotate(mCurrentRotationDegrees))
                } else {
                    this
                }
            }

        Glide.with(requireContext())
            .load(path)
            .apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    resetColorModeIfVisible()
                    if (activity != null && !activity!!.isDestroyed && !activity!!.isFinishing) {
                        handleImageLoadError()
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    applyProperColorMode(resource)
                    val allowZoomingImages = context?.config?.allowZoomingImages ?: true
                    val keepHdrGesturesZoom = ColorModeHelper.shouldPreferGesturesZoomForHdr(
                        resource,
                        context?.config?.ultraHdrRendering ?: true,
                    )
                    binding.gesturesView.controller.settings.isZoomEnabled =
                        mMedium.isRaw() ||
                        mCurrentRotationDegrees != 0 ||
                        allowZoomingImages == false ||
                        keepHdrGesturesZoom
                    if (mIsFragmentVisible && addZoomableView && !keepHdrGesturesZoom) {
                        scheduleZoomableView()
                    }
                    return false
                }
            }).into(binding.gesturesView)
    }

    private fun handleImageLoadError() {
        val context = context ?: return
        resetColorModeIfVisible()
        if (mMedium.path != mOriginalPath) {
            mMedium.path = mOriginalPath
            loadImage()
        } else {
            binding.errorMessageHolder.errorMessage.apply {
                setTextColor(if (context.config.blackBackground) Color.WHITE else context.getProperTextColor())
                fadeIn()
            }
        }
    }

    private fun setupGesturesViewStateListener() {
        binding.gesturesView.controller.addOnStateChangeListener(object : GestureController.OnStateChangeListener {
            override fun onStateChanged(state: State) {
                val settings = binding.gesturesView.controller.settings
                if (settings.hasImageSize() && settings.hasViewportSize() && !mHasInitialZoom) {
                    val zoomByWidth = settings.viewportWidth.toFloat() / settings.imageWidth
                    val zoomByHeight = settings.viewportHeight.toFloat() / settings.imageHeight
                    val fitZoom = maxOf(zoomByWidth, zoomByHeight)
                    mInitialZoom = state.zoom
                    var target = fitZoom
                    if (abs(target - mInitialZoom) < MAX_ZOOM_EQUALITY_TOLERANCE) {
                        target = mInitialZoom * DEFAULT_DOUBLE_TAP_ZOOM
                    }
                    settings.doubleTapZoom = target.coerceAtMost(settings.maxZoom)
                    mHasInitialZoom = true
                }

                mCurrentGestureViewZoom = state.zoom
            }
        })
    }

    private fun showPortraitStripe() {
        val files = File(mMedium.parentPath).listFiles()?.toMutableList() as? ArrayList<File>
        if (files != null) {
            val screenWidth = requireContext().realScreenSize.x
            val itemWidth =
                resources.getDimension(R.dimen.portrait_photos_stripe_height).toInt() + resources.getDimension(org.fossify.commons.R.dimen.one_dp)
                    .toInt()
            val sideWidth = screenWidth / 2 - itemWidth / 2
            val fakeItemsCnt = ceil(sideWidth / itemWidth.toDouble()).toInt()

            val paths = fillPhotoPaths(files, fakeItemsCnt)
            var curWidth = itemWidth
            while (curWidth < screenWidth) {
                curWidth += itemWidth
            }

            val sideElementWidth = curWidth - screenWidth
            val adapter = PortraitPhotosAdapter(requireContext(), paths, sideElementWidth) { position, x ->
                if (mIsFullscreen) {
                    return@PortraitPhotosAdapter
                }

                binding.photoPortraitStripe.smoothScrollBy((x + itemWidth / 2) - screenWidth / 2, 0)
                if (paths[position] != mCurrentPortraitPhotoPath) {
                    mCurrentPortraitPhotoPath = paths[position]
                    hideZoomableView()
                    loadBitmap()
                }
            }

            binding.photoPortraitStripe.adapter = adapter

            val coverIndex = getCoverImageIndex(paths)
            if (coverIndex != -1) {
                mCurrentPortraitPhotoPath = paths[coverIndex]
                setupStripeUpListener(adapter, screenWidth, itemWidth)

                binding.photoPortraitStripe.onGlobalLayout {
                    binding.photoPortraitStripe.scrollBy((coverIndex - fakeItemsCnt) * itemWidth, 0)
                    adapter.setCurrentPhoto(coverIndex)
                    binding.photoPortraitStripeWrapper.beVisible()
                    if (mIsFullscreen) {
                        binding.photoPortraitStripeWrapper.alpha = 0f
                    }
                }
            }
        }
    }

    private fun fillPhotoPaths(files: ArrayList<File>, fakeItemsCnt: Int): ArrayList<String> {
        val paths = ArrayList<String>()
        for (i in 0 until fakeItemsCnt) {
            paths.add("")
        }

        files.forEach {
            paths.add(it.absolutePath)
        }

        for (i in 0 until fakeItemsCnt) {
            paths.add("")
        }
        return paths
    }

    private fun getCoverImageIndex(paths: ArrayList<String>): Int {
        var coverIndex = -1
        paths.forEachIndexed { index, path ->
            if (path.contains("cover", true)) {
                coverIndex = index
            }
        }

        if (coverIndex == -1) {
            paths.forEachIndexed { index, path ->
                if (path.isNotEmpty()) {
                    coverIndex = index
                }
            }
        }
        return coverIndex
    }

    private fun setupStripeUpListener(adapter: PortraitPhotosAdapter, screenWidth: Int, itemWidth: Int) {
        binding.photoPortraitStripe.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                var closestIndex = -1
                var closestDistance = Integer.MAX_VALUE
                val center = screenWidth / 2
                for ((key, value) in adapter.views) {
                    val distance = Math.abs(value.x.toInt() + itemWidth / 2 - center)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestIndex = key
                    }
                }

                Handler().postDelayed({
                    adapter.performClickOn(closestIndex)
                }, 100)
            }
            false
        }
    }

    private fun getFilePathToShow() = if (mMedium.isPortrait()) mCurrentPortraitPhotoPath else getPathToLoad(mMedium)

    private fun openPanorama() {
        TODO("Panorama is not yet implemented.")
    }

    private fun scheduleZoomableView() {
        mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
        if (listener?.isPagerScrolling() == true) {
            mPendingZoomableViewOnIdle = true
            return
        }
        mLoadZoomableViewHandler.postDelayed({
            if (canAttachZoomableView()) {
                addZoomableView()
            }
        }, PhotoSwipeOptimizer.ZOOMABLE_VIEW_IDLE_DELAY_MS)
    }

    private fun canAttachZoomableView(): Boolean {
        val zoomAllowed = context?.config?.allowZoomingImages == true
        val isZoomableMedia = mMedium.isImage() || mMedium.isPortrait()
        val keepHdrGesturesZoom = ColorModeHelper.shouldPreferGesturesZoomForHdr(
            binding.gesturesView.drawable,
            context?.config?.ultraHdrRendering ?: true,
        )
        return mIsFragmentVisible &&
            listener?.isPagerScrolling() != true &&
            zoomAllowed &&
            isZoomableMedia &&
            !mIsSubsamplingVisible &&
            !keepHdrGesturesZoom
    }

    private fun addZoomableView() {
        if (mImageOrientation == -1) {
            val path = getFilePathToShow()
            val cachedOrient = PhotoSwipeOptimizer.cachedExifOrientation(path)
            if (cachedOrient != null) {
                mImageOrientation = cachedOrient
            } else {
                val ctx = context ?: return
                ensureBackgroundThread {
                    val resolved = PhotoSwipeOptimizer.readImageOrientation(ctx, path)
                    activity?.runOnUiThread {
                        mImageOrientation = resolved
                        if (isAdded && canAttachZoomableView()) {
                            addZoomableView()
                        }
                    }
                }
                return
            }
        }
        val rotation = degreesForRotation(mImageOrientation)
        mIsSubsamplingVisible = true
        val config = requireContext().config
        val showHighestQuality = config.showHighestQuality
        val minTileDpi = if (showHighestQuality) -1 else getMinTileDpi()

        val bitmapDecoder = object : DecoderFactory<ImageDecoder> {
            override fun make() = MyGlideImageDecoder(rotation, mMedium.getKey())
        }

        val regionDecoder = object : DecoderFactory<ImageRegionDecoder> {
            override fun make() = PicassoRegionDecoder(showHighestQuality, mScreenWidth, mScreenHeight, minTileDpi)
        }

        var newOrientation = (rotation + mCurrentRotationDegrees) % 360
        if (newOrientation < 0) {
            newOrientation += 360
        }

        binding.subsamplingView.apply {
            setMaxTileSize(if (showHighestQuality) Integer.MAX_VALUE else 4096)
            setMinimumTileDpi(minTileDpi)
            background = ColorDrawable(Color.TRANSPARENT)
            bitmapDecoderFactory = bitmapDecoder
            regionDecoderFactory = regionDecoder
            maxScale = 10f
            beVisible()
            rotationEnabled = config.allowRotatingWithGestures
            isOneToOneZoomEnabled = config.allowOneToOneZoom
            orientation = newOrientation
            setImage(getFilePathToShow())

            onImageEventListener = object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onReady() {
                    background = ColorDrawable(
                        if (config.blackBackground) {
                            Color.BLACK
                        } else {
                            context.getProperBackgroundColor()
                        }
                    )

                    val useWidth = if (mImageOrientation == ORIENTATION_ROTATE_90 || mImageOrientation == ORIENTATION_ROTATE_270) sHeight else sWidth
                    val useHeight = if (mImageOrientation == ORIENTATION_ROTATE_90 || mImageOrientation == ORIENTATION_ROTATE_270) sWidth else sHeight
                    doubleTapZoomScale = getDoubleTapZoomScale(useWidth, useHeight)
                }

                override fun onImageLoadError(e: Exception) {
                    binding.gesturesView.controller.settings.isZoomEnabled = true
                    background = ColorDrawable(Color.TRANSPARENT)
                    mIsSubsamplingVisible = false
                    beGone()
                }

                override fun onImageRotation(degrees: Int) {
                    val fullRotation = (rotation + degrees) % 360
                    val useWidth = if (fullRotation == 90 || fullRotation == 270) sHeight else sWidth
                    val useHeight = if (fullRotation == 90 || fullRotation == 270) sWidth else sHeight
                    doubleTapZoomScale = getDoubleTapZoomScale(useWidth, useHeight)
                    mCurrentRotationDegrees = (mCurrentRotationDegrees + degrees) % 360
                    loadBitmap(false)

                    // ugly, but it works
                    (activity as? ViewPagerActivity)?.refreshMenuItems()
                    (activity as? PhotoVideoActivity)?.refreshMenuItems()
                }

                override fun onUpEvent() {
                    mShouldResetImage = false
                }
            }
        }
    }

    private fun getMinTileDpi(): Int = PhotoSwipeOptimizer.resolveMinTileDpi(requireContext())

    private fun getDoubleTapZoomScale(width: Int, height: Int): Float {
        val bitmapAspectRatio = height / width.toFloat()
        val screenAspectRatio = mScreenHeight / mScreenWidth.toFloat()

        return if (context == null || Math.abs(bitmapAspectRatio - screenAspectRatio) < SAME_ASPECT_RATIO_THRESHOLD) {
            DEFAULT_DOUBLE_TAP_ZOOM
        } else if (requireContext().portrait && bitmapAspectRatio <= screenAspectRatio) {
            mScreenHeight / height.toFloat()
        } else if (requireContext().portrait && bitmapAspectRatio > screenAspectRatio) {
            mScreenWidth / width.toFloat()
        } else if (!requireContext().portrait && bitmapAspectRatio >= screenAspectRatio) {
            mScreenWidth / width.toFloat()
        } else if (!requireContext().portrait && bitmapAspectRatio < screenAspectRatio) {
            mScreenHeight / height.toFloat()
        } else {
            DEFAULT_DOUBLE_TAP_ZOOM
        }
    }

    fun rotateImageViewBy(degrees: Int) {
        if (mIsSubsamplingVisible) {
            binding.subsamplingView.rotateBy(degrees)
        } else {
            mCurrentRotationDegrees = (mCurrentRotationDegrees + degrees) % 360
            mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
            mIsSubsamplingVisible = false
            loadBitmap()
        }
    }

    private fun initExtendedDetails() {
        // Encrypted files never show any extended details, no matter how the setting is configured.
        if (requireContext().config.showExtendedDetails && !CryptoManager.isEncryptedFile(mMedium.path)) {
            ensureBackgroundThread {
                val details = getMediumExtendedDetails(mMedium)
                activity?.runOnUiThread {
                    binding.photoDetails.apply {
                        text = details
                        beVisibleIf(text.isNotEmpty())
                        val hideExtendedDetails = context?.config?.hideExtendedDetails == true
                        alpha = if (!hideExtendedDetails || !mIsFullscreen) 1f else 0f
                        (activity as? BaseViewerActivity)?.applyProperHorizontalInsets(this)
                    }
                }
            }
        } else {
            binding.photoDetails.beGone()
        }
    }

    private fun hideZoomableView() {
        if (context?.config?.allowZoomingImages == true) {
            mIsSubsamplingVisible = false
            binding.subsamplingView.recycle()
            binding.subsamplingView.beGone()
            mLoadZoomableViewHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun photoClicked() {
        listener?.fragmentClicked()
    }

    private fun updateInstantSwitchWidths() {
        binding.instantPrevItem.layoutParams.width = mScreenWidth / 7
        binding.instantNextItem.layoutParams.width = mScreenWidth / 7
    }

    override fun fullscreenToggled(isFullscreen: Boolean) {
        this.mIsFullscreen = isFullscreen
        binding.apply {
            photoDetails.apply {
                if (mStoredShowExtendedDetails && isVisible() && context != null && resources != null) {
                    if (mStoredHideExtendedDetails) {
                        animate().alpha(if (isFullscreen) 0f else 1f).start()
                    }
                }
            }

            if (isFullscreen) {
                bottomActionsDummy.fadeOut(DEFAULT_ANIMATION_DURATION)
            } else {
                bottomActionsDummy.beVisible()
            }

            if (mIsPanorama) {
                panoramaOutline.animate().alpha(if (isFullscreen) 0f else 1f).start()
                panoramaOutline.isClickable = !isFullscreen
            }

            if (mWasInit && mMedium.isPortrait()) {
                photoPortraitStripeWrapper.animate().alpha(if (isFullscreen) 0f else 1f).start()
            }
        }
    }

    private fun applyProperColorMode(resource: Drawable?) {
        PhotoSwipeOptimizer.applyProperColorMode(activity, resource, mIsFragmentVisible)
    }

    private fun resetColorModeIfVisible() {
        if (mIsFragmentVisible) ColorModeHelper.resetColorMode(activity)
    }

    private fun reapplyColorModeIfNeeded() {
        PhotoSwipeOptimizer.reapplyColorModeIfNeeded(
            activity, mWasInit, mIsFragmentVisible, binding.gesturesView.drawable, binding.gesturesView.isVisible(),
        )
    }
}
