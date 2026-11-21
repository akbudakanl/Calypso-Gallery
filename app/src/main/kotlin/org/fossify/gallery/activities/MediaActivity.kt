package org.fossify.gallery.activities

import android.app.WallpaperManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.deleteFiles
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.gallery.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.showKeyboard
import org.fossify.gallery.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.REQUEST_EDIT_IMAGE
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databinding.ActivityMediaBinding
import org.fossify.gallery.dialogs.ChangeGroupingDialog
import org.fossify.gallery.dialogs.ChangeSortingDialog
import org.fossify.gallery.dialogs.ChangeViewTypeDialog
import org.fossify.gallery.dialogs.FilterMediaDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.canAccessHiddenVirtualAlbums
import org.fossify.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossify.gallery.extensions.hasExcludedNormalFolders
import org.fossify.gallery.extensions.hasHiddenNormalFolders
import org.fossify.gallery.extensions.applyCalypsoSearchBarStyle
import org.fossify.gallery.extensions.keepHeaderPinned
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.captureScrollOffset
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.openSecretVaultPrompt
import org.fossify.gallery.extensions.promptVaultUnlock
import org.fossify.gallery.extensions.restoreScrollOffset
import org.fossify.gallery.extensions.deleteDBPath
import org.fossify.gallery.extensions.deleteVirtualAlbumItemFromAllAlbums
import org.fossify.gallery.extensions.getVirtualAlbumId
import org.fossify.gallery.extensions.removePathsFromVirtualAlbum
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.emptyAndDisableTheRecycleBin
import org.fossify.gallery.extensions.emptyTheRecycleBin
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getVirtualAlbumName
import org.fossify.gallery.extensions.isHiddenVirtualAlbumPath
import org.fossify.gallery.extensions.isPathHiddenOrEncrypted
import org.fossify.gallery.extensions.isVirtualAlbumPath
import org.fossify.gallery.extensions.getCachedMedia
import org.fossify.gallery.extensions.getFolderNameFromPath
import org.fossify.gallery.extensions.getHumanizedFilename
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.extensions.launchGesturePlayer
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.movePathsInRecycleBin
import org.fossify.gallery.extensions.openPath
import org.fossify.gallery.extensions.openVideoWithDefaultPlayer
import org.fossify.gallery.extensions.openRecycleBin
import org.fossify.gallery.extensions.restoreRecycleBinPaths
import org.fossify.gallery.extensions.showRecycleBinEmptyingDialog
import org.fossify.gallery.extensions.showRestoreConfirmationDialog
import org.fossify.gallery.extensions.tryDeleteFileDirItem
import org.fossify.gallery.extensions.updateWidgets
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.ExcludedMenuPolicy
import org.fossify.gallery.helpers.VAULT_RESUME_STATE
import org.fossify.gallery.helpers.VaultResumeState
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.GET_ANY_INTENT
import org.fossify.gallery.helpers.GET_IMAGE_INTENT
import org.fossify.gallery.helpers.GET_VIDEO_INTENT
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.IS_IN_RECYCLE_BIN
import org.fossify.gallery.helpers.MAX_COLUMN_COUNT
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.MediaUpdatePolicy
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.PICKED_PATHS
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.RandomOrderManager
import org.fossify.gallery.helpers.EXTRA_ALBUM_PATH
import org.fossify.gallery.helpers.EXTRA_EXITED_FROM_VAULT
import org.fossify.gallery.helpers.EXTRA_OPEN_SECRET_VAULT_FOLDERS
import org.fossify.gallery.helpers.EXTRA_SECRET_VAULT_FILES
import org.fossify.gallery.helpers.VaultExitBackHelper
import org.fossify.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SHOW_FAVORITES
import org.fossify.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossify.gallery.helpers.SHOW_VIRTUAL_ALBUM
import org.fossify.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossify.gallery.helpers.SKIP_AUTHENTICATION
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.VaultPullGestureHandler
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.VaultViewPolicy
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.OperationRecovery
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.io.File
import java.io.IOException
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.emptyVaultRecycleBin
import org.fossify.gallery.helpers.getOrCreateDefaultHiddenAlbum
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.isVaultRecycleBinPath
import org.fossify.gallery.helpers.moveEncryptedFilesToVaultRecycleBin
import org.fossify.gallery.helpers.permanentlyDeleteVaultRecycleBinFiles
import org.fossify.gallery.helpers.purgeTracesForFile
import org.fossify.gallery.helpers.restoreVaultRecycleBinFiles
import org.fossify.gallery.helpers.secureWipeAndDeleteFile

class MediaActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    private var mPath = ""
    private var mIsGetImageIntent = false
    private var mIsGetVideoIntent = false
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    private var mPendingMediaRefresh = false
    private var mAllowPickingMultiple = false
    private var mShowAll = false
    private var mLoadedInitialPhotos = false
    private var mShowLoadingIndicator = true
    private var mWasFullscreenViewOpen = false
    private var mLastSearchedText = ""
    private var mCurrentAlbumDisplayName = ""

    // MySearchMenu's frame has a fixed single-row height; album headers need room for a title
    // plus the item-count line or the second row gets clipped ("half visible" count).
    private var albumToolbarHeader: View? = null
    private var defaultTopBarHeight = -1
    private var defaultTopBarPaddingStart = -1
    private var defaultTopBarPaddingEnd = -1
    private var defaultContentInsetStart = -1
    private var defaultContentInsetEnd = -1
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    private var mIsVaultOpening = false
    private var mIsVaultFilesView = false
    private var mIsSwitchingVaultView = false
    private val mVaultBackHelper = VaultExitBackHelper()
    private var mLastRandomSessionGeneration = RandomOrderManager.getSessionGeneration()
    private var mHasNormalExcludedFolders = false
    private var mHasNormalHiddenFolders = false
    private var mExcludedCheckGeneration = 0

    // "Continue where I left off" after the vault keys were wiped from RAM (30 min in background / process restart).
    // mVaultResumeBlob is the encrypted state from savedInstanceState, mVaultResume the decrypted/live one.
    private var mVaultResumeBlob: String? = null
    private var mVaultResume: VaultResumeState? = null
    private var mIsVaultResumePrompting = false

    private val vaultPullGesture by lazy {
        VaultPullGestureHandler(
            containerView = binding.mediaRefreshLayout,
            indicatorView = binding.vaultPullIndicator,
            iconView = binding.vaultPullIcon,
            textView = binding.vaultPullText,
            isListAtTop = { !binding.mediaGrid.canScrollVertically(-1) },
            primaryColor = { getProperPrimaryColor() },
            textColor = { getProperTextColor() },
            dispatchToChildren = { event -> super.dispatchTouchEvent(event) },
            onVaultOpen = {
                mIsVaultOpening = true
                triggerSecretVaultMenu()
            }
        )
    }

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowFileTypes = true
    private var mStoredRoundedCorners = false
    private var mStoredMarkFavoriteItems = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredThumbnailSpacing = 0

    private val binding by viewBinding(ActivityMediaBinding::inflate)

    companion object {
        private const val ALBUM_HEADER_HEIGHT_DP = 76
        var mMedia = ArrayList<ThumbnailItem>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        mVaultResumeBlob = savedInstanceState?.getString(VAULT_RESUME_STATE)

        intent.apply {
            mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
            mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
            mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
            mAllowPickingMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            mIsVaultFilesView = getBooleanExtra(EXTRA_SECRET_VAULT_FILES, false)
            if (getBooleanExtra(EXTRA_EXITED_FROM_VAULT, false)) {
                removeExtra(EXTRA_EXITED_FROM_VAULT)
                mVaultBackHelper.markExited()
            }
        }

        binding.mediaRefreshLayout.setOnRefreshListener {
            RandomOrderManager.invalidateMediaScope(getRandomOrderScopeKey())
            getMedia()
        }
        try {
            mPath = intent.getStringExtra(DIRECTORY) ?: ""
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
            return
        }

        setupOptionsMenu()
        refreshMenuItems()
        storeStateVariables()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.mediaMenu),
            padBottomImeAndSystem = listOf(binding.mediaGrid)
        )

        registerFileUpdateListener()

        binding.mediaEmptyTextPlaceholder2.setOnClickListener {
            showFilterMediaDialog()
        }

        updateWidgets()
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (VaultSessionManager.isReauthPending()) {
            return
        }
        if (mIsVaultFilesView) {
            config.showAll = false
        }
        val requiresVault = requiresVaultAccess()
        applyVaultScreenProtection(requiresVault)
        if (requiresVault && (!canAccessHiddenVirtualAlbums() || !CryptoManager.isVaultUnlocked)) {
            if (VaultSessionManager.consumeUnlockDeclined()) {
                exitLockedVaultFilesView()
                return
            }
            // The vault keys were wiped from RAM: hide stale content, ask for the password again and continue
            // in this album instead of closing it.
            resumeAfterVaultWipe()
            return
        }

        updateMenuColors()
        updateExcludedFoldersAvailability()
        if (mStoredAnimateGifs != config.animateGifs) {
            getMediaAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getMediaAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }

        if (mStoredShowFileTypes != config.showThumbnailFileTypes) {
            getMediaAdapter()?.updateShowFileTypes(config.showThumbnailFileTypes)
        }

        if (mStoredTextColor != getProperTextColor()) {
            getMediaAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getMediaAdapter()?.updatePrimaryColor()
        }

        if (
            mStoredThumbnailSpacing != config.thumbnailSpacing
            || mStoredRoundedCorners != config.fileRoundedCorners
            || mStoredMarkFavoriteItems != config.markFavoriteItems
        ) {
            binding.mediaGrid.adapter = null
            setupAdapter()
        }

        refreshMenuItems()

        binding.mediaFastscroller.updateColors(primaryColor)
        mIsVaultOpening = false
        binding.mediaRefreshLayout.isEnabled = false // pull-to-refresh kaldırıldı; refresh taşma menüsünde
        getMediaAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.loadingIndicator.setIndicatorColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder.setTextColor(getProperTextColor())
        binding.mediaEmptyTextPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder2.bringToFront()
        applyMediaGridPadding()
        updateAlbumHeader()

        val wasFullscreenViewOpen = mWasFullscreenViewOpen
        mWasFullscreenViewOpen = false
        val currentRandomGeneration = RandomOrderManager.getSessionGeneration()
        val randomSessionExpired = mLastRandomSessionGeneration != currentRandomGeneration
        if (randomSessionExpired) {
            mLastRandomSessionGeneration = currentRandomGeneration
            if (config.getEffectiveMediaSorting(mPath, mShowAll) and SORT_BY_RANDOM != 0) {
                mLoadedInitialPhotos = false
            }
        }
        if (wasFullscreenViewOpen && !randomSessionExpired && mLoadedInitialPhotos && mMedia.isNotEmpty()) {
            setupAdapter()
        }

        if (shouldSkipAuthentication()) {
            tryLoadGallery()
        } else {
            handleLockedFolderOpening(mPath) { success ->
                if (success) {
                    tryLoadGallery()
                } else {
                    finish()
                }
            }
        }
        if (CryptoManager.isVaultUnlocked) {
            OperationRecovery.maybePromptResume(this)
        }
    }

    override fun onPause() {
        super.onPause()
        mVaultBackHelper.cancelToast()
        mIsGettingMedia = false
        mPendingMediaRefresh = false
        binding.mediaRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)

        if (!mMedia.isEmpty()) {
            mCurrAsyncTask?.stopFetching()
        }
    }

    override fun onStop() {
        super.onStop()

        if (config.tempSkipDeleteConfirmation) {
            mTempShowHiddenHandler.postDelayed({
                config.resetTempDeleteConfirmation()
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!requiresVaultAccess()) return

        // Encrypted with the vault key: unreadable while locked, readable again after the password was entered.
        if (CryptoManager.isVaultUnlocked) {
            buildVaultResumeState().encrypt(this)?.let { outState.putString(VAULT_RESUME_STATE, it) }
        } else {
            (mVaultResumeBlob ?: mVaultResume?.encrypt(this))?.let { outState.putString(VAULT_RESUME_STATE, it) }
        }
    }

    private fun requiresVaultAccess() =
        mIsVaultFilesView ||
            (mPath.isVirtualAlbumPath() && isHiddenVirtualAlbumPath(mPath)) ||
            (mPath.isNotEmpty() && !mPath.isVirtualAlbumPath() && CryptoManager.isEncryptedFolderPath(this, mPath))

    private fun buildVaultResumeState(): VaultResumeState {
        val scroll = binding.mediaGrid.captureScrollOffset()
        val albumName = try {
            if (mPath.isVirtualAlbumPath()) "" else getFolderNameFromPath(mPath)
        } catch (_: Exception) {
            ""
        }
        return VaultResumeState(
            albumPath = mPath,
            albumName = albumName,
            sorting = config.getEffectiveMediaSorting(mPath, mShowAll),
            viewType = config.getFolderViewType(mPath),
            grouping = config.getFolderGrouping(mPath),
            scrollPosition = scroll?.first ?: 0,
            scrollOffset = scroll?.second ?: 0
        )
    }

    private fun resumeAfterVaultWipe() {
        if (mIsVaultResumePrompting) return
        if (mVaultResume == null) {
            mVaultResume = buildVaultResumeState()
        }

        // no stale decrypted thumbnails / names on screen or in RAM while the password is asked for
        binding.mediaGrid.adapter = null
        mMedia = ArrayList()
        mLoadedInitialPhotos = false
        mShowLoadingIndicator = true

        mIsVaultResumePrompting = true
        promptVaultUnlock { success ->
            mIsVaultResumePrompting = false
            if (!success) {
                VaultSessionManager.markUnlockDeclined()
                exitLockedVaultFilesView()
                return@promptVaultUnlock
            }

            // process restart case: the live state is gone, read the encrypted blob now that the key is back
            VaultResumeState.decrypt(mVaultResumeBlob)?.takeIf { it.albumPath == mPath }?.let { mVaultResume = it }
            mVaultResumeBlob = null
            applyResumedViewSettings()
            refreshMenuItems()
            tryLoadGallery()
        }
    }

    private fun applyResumedViewSettings() {
        val state = mVaultResume ?: return
        if (mShowAll) {
            if (config.filesSorting != state.sorting) config.filesSorting = state.sorting
        } else {
            if (config.getFolderSorting(mPath) != state.sorting) config.saveCustomSorting(mPath, state.sorting)
        }
        if (config.getFolderViewType(mPath) != state.viewType) config.saveFolderViewType(mPath, state.viewType)
        if (config.getFolderGrouping(mPath) != state.grouping) config.saveFolderGrouping(mPath, state.grouping)
    }

    private fun applyPendingScroll() {
        val state = mVaultResume ?: return
        val itemCount = binding.mediaGrid.adapter?.itemCount ?: 0
        if (itemCount <= state.scrollPosition) return
        mVaultResume = null
        binding.mediaGrid.post {
            if (!isDestroyed && !isFinishing) {
                binding.mediaGrid.restoreScrollOffset(state.scrollPosition, state.scrollOffset)
            }
        }
    }

    override fun onDestroy() {
        unregisterFileUpdateListener()
        // only a real exit wipes the vault right away; a system-initiated destroy keeps it until the timeout
        if ((config.showAll || mIsVaultFilesView) && !mIsSwitchingVaultView && !isChangingConfigurations && isFinishing) {
            config.temporarilyShowHidden = false
            config.temporarilyShowVaultExcluded = false
            config.resetTempDeleteConfirmation()
            CryptoManager.lockVault()
            VaultSessionManager.onVaultExited()
        }

        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onMediaStoreContentChanged(changedFolderPaths: Set<String>, isWildcardChange: Boolean) {
        if (isDestroyed || isFinishing) {
            return
        }
        val affectsCurrentView = isWildcardChange ||
            mShowAll ||
            mPath == FAVORITES ||
            mPath.isVirtualAlbumPath() ||
            changedFolderPaths.any { it.equals(mPath, ignoreCase = true) }

        if (affectsCurrentView && !binding.mediaMenu.isSearchOpen) {
            if (mIsGettingMedia) {
                mPendingMediaRefresh = true
            } else {
                getMedia()
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mediaMenu.isSearchOpen) {
            binding.mediaMenu.closeSearch()
            true
        } else if (mIsVaultFilesView && !isThirdPartyPickIntent()) {
            mVaultBackHelper.handleBackPress(this) { exitVaultToNormalGallery() }
            true
        } else if (mVaultBackHelper.isWithinExitGuard()) {
            true
        } else {
            if (config.showAll) {
                appLockManager.lock()
            }

            false
        }
    }

    private fun isThirdPartyPickIntent() =
        mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent

    private fun exitLockedVaultFilesView() {
        if (mIsVaultFilesView && !isThirdPartyPickIntent()) {
            exitVaultToNormalGallery()
        } else {
            finish()
        }
    }

    private fun exitVaultToNormalGallery() {
        if (mIsSwitchingVaultView) return
        hideKeyboard()
        mIsSwitchingVaultView = true
        mVaultBackHelper.markExited()
        config.temporarilyShowHidden = false
        config.temporarilyShowVaultExcluded = false
        config.resetTempDeleteConfirmation()
        CryptoManager.lockVault()
        CryptoManager.clearAppImageCaches(this)
        VaultSessionManager.onVaultExited()

        if (VaultViewPolicy.shouldExitVaultToFilesView(isExitingFromFilesView = true)) {
            config.showAll = true
            Intent(this, MediaActivity::class.java).apply {
                putExtra(DIRECTORY, "")
                putExtra(EXTRA_EXITED_FROM_VAULT, true)
                startActivity(this)
            }
        } else {
            config.showAll = false
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_EXITED_FROM_VAULT, true)
                startActivity(this)
            }
        }
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        val isVaultRecycleBin = CryptoManager.isVaultRecycleBinPath(this@MediaActivity, mPath)
        binding.mediaMenu.requireToolbar().menu.apply {
            // Albums keep only the magnifier and overflow in the bar; sorting moves into overflow.
            findItem(R.id.sort).setShowAsAction(
                if (mShowAll) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER
            )
            findItem(R.id.show_albums).isVisible = mShowAll
            findItem(R.id.group).isVisible = !config.scrollHorizontally

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN || isVaultRecycleBin
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN || isVaultRecycleBin

            findItem(R.id.create_new_folder).isVisible =
                !mShowAll && mPath != RECYCLE_BIN && !isVaultRecycleBin && mPath != FAVORITES && !mPath.isVirtualAlbumPath()
            findItem(R.id.open_recycle_bin).isVisible =
                config.useRecycleBin && mPath != RECYCLE_BIN && !isVaultRecycleBin && !mIsVaultFilesView

            findItem(R.id.temporarily_show_hidden).isVisible = false
            findItem(R.id.stop_showing_hidden).isVisible =
                !mIsVaultFilesView && (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            // Same two visibility toggles the album screen offers, available in the files view too.
            val excludedVisibility = ExcludedMenuPolicy.resolve(
                hasExcludedInCurrentScope = !mIsVaultFilesView && mHasNormalExcludedFolders,
                isCurrentlyShowingExcluded = !mIsVaultFilesView && config.temporarilyShowExcluded
            )
            findItem(R.id.temporarily_show_excluded).isVisible = excludedVisibility.showTemporarilyShowExcluded
            findItem(R.id.stop_showing_excluded).isVisible = excludedVisibility.showStopShowingExcluded

            val hiddenFoldersVisibility = ExcludedMenuPolicy.resolve(
                hasExcludedInCurrentScope = !mIsVaultFilesView && mHasNormalHiddenFolders,
                isCurrentlyShowingExcluded = !mIsVaultFilesView && config.temporarilyShowHiddenFolders
            )
            findItem(R.id.temporarily_show_hidden_folders).isVisible = hiddenFoldersVisibility.showTemporarilyShowExcluded
            findItem(R.id.stop_showing_hidden_folders).isVisible = hiddenFoldersVisibility.showStopShowingExcluded

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            findItem(R.id.toggle_filename).isVisible = viewType == VIEW_TYPE_GRID
        }
    }

    private fun setupOptionsMenu() {
        binding.mediaMenu.requireToolbar().inflateMenu(R.menu.menu_media)
        binding.mediaMenu.toggleHideOnScroll(false)
        binding.mediaMenu.setupMenu()

        binding.mediaMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            searchQueryChanged(text)
            updateAlbumHeader()
            binding.mediaRefreshLayout.isEnabled = false // pull-to-refresh kaldırıldı; refresh taşma menüsünde
        }

        // The album header turns into the search field; refresh its visibility on both transitions.
        binding.mediaMenu.onSearchOpenListener = {
            applyToolbarMode()
            updateAlbumHeader()
        }
        binding.mediaMenu.onSearchClosedListener = {
            applyToolbarMode()
            updateAlbumHeader()
        }

        binding.mediaMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.refresh_menu -> {
                    RandomOrderManager.invalidateMediaScope(getRandomOrderScopeKey())
                    getMedia()
                    true
                }
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.empty_recycle_bin -> emptyRecycleBin()
                R.id.empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
                R.id.restore_all_files -> restoreAllFiles()
                R.id.toggle_filename -> toggleFilenameVisibility()
                R.id.show_albums -> switchToFolderView()
                R.id.change_view_type -> changeViewType()
                R.id.group -> showGroupByDialog()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.temporarily_show_excluded, R.id.stop_showing_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.temporarily_show_hidden_folders, R.id.stop_showing_hidden_folders -> tryToggleTemporarilyShowHiddenFolders()
                R.id.slideshow -> startSlideshow()
                R.id.settings -> launchSettings()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            hideKeyboard()
            Intent(this, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium ?: return
                putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
                putExtra(PATH, item.path)
                // A slideshow started from a virtual album must keep that album's item list and
                // sort scope instead of falling back to the file's real parent folder.
                putExtra(EXTRA_ALBUM_PATH, mPath)
                putExtra(SHOW_ALL, mShowAll)
                putExtra(EXTRA_SECRET_VAULT_FILES, mIsVaultFilesView)
                putExtra(SHOW_FAVORITES, mPath == FAVORITES)
                putExtra(SHOW_VIRTUAL_ALBUM, if (mPath.isVirtualAlbumPath()) mPath else "")
                putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
                putExtra(IS_FROM_GALLERY, true)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                startActivity(this)
            }
        }
    }

    private fun updateMenuColors() {
        binding.mediaMenu.updateColors()
        binding.mediaMenu.applyCalypsoSearchBarStyle()
        binding.mediaMenu.keepHeaderPinned()
        applyToolbarMode()
        updateAlbumHeader()
    }

    /**
     * Album views use a clean, fixed header: back arrow + title/count, a magnifier that opens
     * the in-place search bar, and overflow (Sort now lives there). All-files views keep the
     * regular search bar. Both headers are pinned to the top instead of scrolling away.
     */
    /**
     * Opens the in-place search directly. The stock magnifier's own click listener is remapped to
     * "back" in album mode (useArrowIcon), so routing through it would exit the album instead of
     * opening search. Everything here is the local equivalent of MySearchMenu's private openSearch().
     */
    private fun openAlbumSearch() {
        if (binding.mediaMenu.isSearchOpen) return
        val field = findViewById<EditText>(org.fossify.commons.R.id.top_toolbar_search) ?: return
        val icon = findViewById<ImageView>(org.fossify.commons.R.id.top_toolbar_search_icon) ?: return

        binding.mediaMenu.isSearchOpen = true
        icon.setImageResource(org.fossify.commons.R.drawable.ic_arrow_left_vector)
        icon.contentDescription = getString(org.fossify.commons.R.string.back)
        applyToolbarMode()
        updateAlbumHeader()
        binding.mediaMenu.applyCalypsoSearchBarStyle()
        field.beVisible()
        icon.beVisible()
        // Post the focus/keyboard request so the IME sees the finished visibility change.
        field.post {
            field.requestFocus()
            showKeyboard(field)
        }
    }

    private fun applyToolbarMode() {
        val palette = AppearanceManager.resolve(this, config)
        val toolbar = binding.mediaMenu.requireToolbar()
        val searchField = findViewById<View>(org.fossify.commons.R.id.top_toolbar_search)
        val searchIcon = findViewById<View>(org.fossify.commons.R.id.top_toolbar_search_icon)
        val toolbarContainer = findViewById<View>(org.fossify.commons.R.id.toolbar_container)
        val topBar = findViewById<View>(org.fossify.commons.R.id.search_bar_container)

        if (defaultTopBarHeight < 0) {
            defaultTopBarHeight = topBar?.layoutParams?.height ?: -1
            defaultTopBarPaddingStart = topBar?.paddingStart ?: -1
            defaultTopBarPaddingEnd = topBar?.paddingEnd ?: -1
            defaultContentInsetStart = toolbar.contentInsetStart
            defaultContentInsetEnd = toolbar.contentInsetEnd
        }

        topBar?.let { bar ->
            if (mShowAll) {
                bar.layoutParams.height = defaultTopBarHeight
                bar.setPadding(defaultTopBarPaddingStart, bar.paddingTop, defaultTopBarPaddingEnd, bar.paddingBottom)
                toolbar.setContentInsetsAbsolute(defaultContentInsetStart, defaultContentInsetEnd)
            } else {
                // MySearchMenu reserves exactly one actionBar row. Give the two-line album
                // header enough room so the item-count line isn't cut in half.
                bar.layoutParams.height = (ALBUM_HEADER_HEIGHT_DP * resources.displayMetrics.density).toInt()
                bar.setPadding(0, bar.paddingTop, 0, bar.paddingBottom)
                toolbar.setContentInsetsAbsolute(0, (8f * resources.displayMetrics.density).toInt())
            }
            bar.requestLayout()
        }

        val header = albumToolbarHeader()
        val isSearchOpen = binding.mediaMenu.isSearchOpen
        // MySearchMenu opens search when this field gains focus, so it must stay focusable even
        // while the album header is showing. GONE blocks focus; INVISIBLE does not.
        if (mShowAll || isSearchOpen) {
            searchField?.beVisible()
        } else {
            searchField?.visibility = View.INVISIBLE
        }
        // The stock icon becomes the back arrow while searching, so keep it available to close.
        searchIcon?.beVisibleIf(mShowAll || isSearchOpen)
        header.beVisibleIf(!mShowAll && !isSearchOpen)
        // While searching the pill must contain only the field and its back arrow; the album
        // toolbar (and its overflow) would otherwise leave an empty bar above the grid.
        toolbar.beVisibleIf(mShowAll || !isSearchOpen)

        // The custom header provides its own back button. Suppress MySearchMenu's navigation
        // icon so we never end up with two arrows, and clear the stock title/subtitle so the
        // custom (properly aligned) text is the only one drawn.
        toolbar.navigationIcon = null
        toolbar.layoutParams.width = if (mShowAll) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
        toolbar.title = null
        toolbar.subtitle = null

        if (!mShowAll) {
            header.findViewById<TextView>(R.id.album_toolbar_title).setTextColor(palette.text)
            header.findViewById<TextView>(R.id.album_toolbar_subtitle).setTextColor(palette.secondary)
            header.findViewById<ImageButton>(R.id.album_toolbar_back).imageTintList =
                ColorStateList.valueOf(palette.text)
            // Same drawable and tint as the main screen magnifier.
            header.findViewById<ImageButton>(R.id.album_toolbar_search).apply {
                imageTintList = ColorStateList.valueOf(palette.text)
                setOnClickListener { openAlbumSearch() }
            }
            // In album mode the stock icon is forced into "arrow = back" mode, so closing search
            // leaves it as an arrow. Restore the magnifier for the next idle state.
            if (!isSearchOpen) {
                findViewById<ImageView>(org.fossify.commons.R.id.top_toolbar_search_icon)?.setImageResource(
                    org.fossify.commons.R.drawable.ic_search_vector
                )
            }
            if (isSearchOpen) {
                binding.mediaMenu.applyCalypsoSearchBarStyle()
            } else {
                toolbarContainer?.background = null
            }
        }

        toolbar.requestLayout()
    }

    /** Inflates and attaches the two-line album header once, reusing it on later refreshes. */
    private fun albumToolbarHeader(): View {
        albumToolbarHeader?.let { return it }
        val toolbar = binding.mediaMenu.requireToolbar()
        val header = layoutInflater.inflate(R.layout.album_toolbar_header, toolbar, false)
        val params = Toolbar.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.START }

        toolbar.addView(header, params)
        header.findViewById<View>(R.id.album_toolbar_back).setOnClickListener { performDefaultBack() }
        albumToolbarHeader = header
        return header
    }

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowFileTypes = showThumbnailFileTypes
            mStoredMarkFavoriteItems = markFavoriteItems
            mStoredThumbnailSpacing = thumbnailSpacing
            mStoredRoundedCorners = fileRoundedCorners
            // A virtual album is a file-bound collection, not a physical folder. Keep its own
            // album scope even with "Show all folders content" enabled, so it is not replaced
            // by the global media list.
            mShowAll = (showAll || mIsVaultFilesView) && mPath != RECYCLE_BIN && !mPath.isVirtualAlbumPath()
        }
    }

    private fun searchQueryChanged(text: String) {
        ensureBackgroundThread {
            try {
                val filtered = mMedia
                    .filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(applicationContext).groupMedia(
                    media = filtered as ArrayList<Medium>, path = mPath
                )
                runOnUiThread {
                    if (grouped.isEmpty()) {
                        binding.mediaEmptyTextPlaceholder.text =
                            getString(org.fossify.commons.R.string.no_items_found)
                        binding.mediaEmptyTextPlaceholder.beVisible()
                        binding.mediaFastscroller.beGone()
                    } else {
                        binding.mediaEmptyTextPlaceholder.beGone()
                        binding.mediaFastscroller.beVisible()
                    }

                    handleGridSpacing(grouped)
                    getMediaAdapter()?.updateMedia(grouped)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryLoadGallery() {
        requestMediaPermissions {
            if (mPath.isVirtualAlbumPath() && !mShowAll) {
                // the album name lives in the database, it cannot be read on the main thread
                ensureBackgroundThread {
                    if (isHiddenVirtualAlbumPath(mPath) && !canAccessHiddenVirtualAlbums()) {
                        runOnUiThread { finish() }
                        return@ensureBackgroundThread
                    }
                    val albumName = getVirtualAlbumName(mPath) ?: ""
                    runOnUiThread {
                        if (!isDestroyed && !isFinishing) {
                            mCurrentAlbumDisplayName = albumName
                            binding.mediaMenu.updateHintText(
                                getString(org.fossify.commons.R.string.search_in_placeholder, albumName)
                            )
                            updateAlbumHeader()
                        }
                    }
                }
            } else {
                val dirName = when {
                    mPath == FAVORITES -> getString(org.fossify.commons.R.string.favorites)
                    mPath == RECYCLE_BIN || CryptoManager.isVaultRecycleBinPath(this, mPath) -> getString(org.fossify.commons.R.string.recycle_bin)
                    mPath == config.OTGPath -> getString(org.fossify.commons.R.string.usb)
                    CryptoManager.isEncryptedFolderPath(this, mPath) -> getFolderNameFromPath(mPath)
                    else -> getHumanizedFilename(mPath)
                }

                mCurrentAlbumDisplayName = if (mShowAll) "" else dirName
                val searchHint = if (mShowAll) {
                    getString(org.fossify.commons.R.string.search_files)
                } else {
                    getString(org.fossify.commons.R.string.search_in_placeholder, dirName)
                }

                binding.mediaMenu.updateHintText(searchHint)
                updateAlbumHeader()
            }

            if (!mShowAll) {
                binding.mediaMenu.toggleForceArrowBackIcon(true)
                binding.mediaMenu.onNavigateBackClickListener = {
                    performDefaultBack()
                }
            }

            if (mShowLoadingIndicator) {
                binding.loadingIndicator.show()
                mShowLoadingIndicator = false
            }

            getMedia()
            setupLayoutManager()
        }
    }

    private fun applyMediaGridPadding() {
        val useSidePadding = config.fileRoundedCorners && config.viewTypeFiles != VIEW_TYPE_LIST
        val sidePx = if (useSidePadding) (6f * resources.displayMetrics.density).toInt() else 0
        binding.mediaGrid.setPadding(
            sidePx,
            binding.mediaGrid.paddingTop,
            sidePx,
            binding.mediaGrid.paddingBottom
        )
    }

    private fun updateAlbumHeader() {
        // The album title now lives in the fixed toolbar, so the in-grid header is no longer used.
        binding.mediaAlbumHeader.beGone()
        val shouldShow = !mShowAll && mCurrentAlbumDisplayName.isNotEmpty() && mLastSearchedText.isEmpty() &&
            !binding.mediaMenu.isSearchOpen
        val header = albumToolbarHeader()
        val titleView = header.findViewById<TextView>(R.id.album_toolbar_title)
        val subtitleView = header.findViewById<TextView>(R.id.album_toolbar_subtitle)

        if (!shouldShow) {
            titleView.text = null
            subtitleView.text = null
            return
        }

        val itemCount = mMedia.count { it is Medium }
        titleView.text = mCurrentAlbumDisplayName
        subtitleView.text = when {
            mPath.isVirtualAlbumPath() -> getString(R.string.media_header_virtual_album, itemCount)
            CryptoManager.isEncryptedFolderPath(this, mPath) && !CryptoManager.isVaultRecycleBinPath(this, mPath) ->
                getString(R.string.media_header_encrypted_album, itemCount)
            else -> getString(R.string.media_header_items_count, itemCount)
        }
    }

    private fun getMediaAdapter() = binding.mediaGrid.adapter as? MediaAdapter

    private fun setupAdapter() {
        if (!mShowAll && isDirEmpty()) {
            return
        }

        val currAdapter = binding.mediaGrid.adapter
        if (currAdapter == null) {
            initZoomListener()
            MediaAdapter(
                activity = this,
                media = mMedia.clone() as ArrayList<ThumbnailItem>,
                listener = this,
                isAGetIntent = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                allowMultiplePicks = mAllowPickingMultiple,
                path = mPath,
                recyclerView = binding.mediaGrid
            ) {
                if (it is Medium && !isFinishing) {
                    itemClicked(it.path)
                }
            }.apply {
                setupZoomListener(mZoomListener)
                binding.mediaGrid.adapter = this
            }

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            if (viewType == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                binding.mediaGrid.scheduleLayoutAnimation()
            }

            setupLayoutManager()
            handleGridSpacing()
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mMedia)
            handleGridSpacing()
        } else {
            searchQueryChanged(mLastSearchedText)
        }

        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.mediaFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getMedia()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun getRandomOrderScopeKey(): String = when {
        mIsVaultFilesView -> "vault_show_all"
        mShowAll -> SHOW_ALL
        else -> mPath
    }

    private fun showSortingDialog() {
        val isAlbumView = !mShowAll && mPath.isNotEmpty()
        ChangeSortingDialog(this, false, isAlbumView, if (isAlbumView) mPath else SHOW_ALL) {
            RandomOrderManager.invalidateMediaScope(getRandomOrderScopeKey())
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mLoadedInitialPhotos = false
            binding.mediaRefreshLayout.isRefreshing = true
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun emptyRecycleBin() {
        showRecycleBinEmptyingDialog {
            if (CryptoManager.isVaultRecycleBinPath(this, mPath)) {
                ensureBackgroundThread {
                    CryptoManager.emptyVaultRecycleBin(this)
                    runOnUiThread {
                        finish()
                    }
                }
            } else {
                emptyTheRecycleBin {
                    finish()
                }
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyAndDisableTheRecycleBin {
                finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        showRestoreConfirmationDialog(paths.size) {
            if (CryptoManager.isVaultRecycleBinPath(this, mPath)) {
                ensureBackgroundThread {
                    CryptoManager.restoreVaultRecycleBinFiles(this, paths)
                    runOnUiThread {
                        finish()
                    }
                }
            } else {
                restoreRecycleBinPaths(paths) {
                    ensureBackgroundThread {
                        directoryDB.deleteDirPath(RECYCLE_BIN)
                    }
                    finish()
                }
            }
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun switchToFolderView() {
        hideKeyboard()
        config.showAll = false
        if (mIsVaultFilesView) {
            VaultSessionManager.markPreserveVaultOnSwitch()
            mIsSwitchingVaultView = true
            Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_SECRET_VAULT_FOLDERS, true)
                startActivity(this)
            }
            finish()
            return
        }

        if (CryptoManager.isVaultUnlocked && config.temporarilyShowHidden) {
            VaultSessionManager.markPreserveVaultOnSwitch()
            mIsSwitchingVaultView = true
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, false, mPath) {
            refreshMenuItems()
            setupLayoutManager()
            binding.mediaGrid.adapter = null
            setupAdapter()
        }
    }

    private fun showGroupByDialog() {
        ChangeGroupingDialog(this, mPath) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders) {
            val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                    }
                }
            }
        }
    }

    private fun getMedia() {
        if (mIsGettingMedia) {
            return
        }

        mIsGettingMedia = true
        if (mLoadedInitialPhotos || mIsVaultFilesView) {
            if (!mLoadedInitialPhotos) {
                binding.mediaRefreshLayout.isRefreshing = true
            }
            startAsyncTask()
        } else {
            getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent,
                enableRandom = true
            ) {
                if (isDestroyed || isFinishing) {
                    mIsGettingMedia = false
                    return@getCachedMedia
                }
                if (it.isEmpty()) {
                    runOnUiThread {
                        binding.mediaRefreshLayout.isRefreshing = true
                    }
                } else {
                    gotMedia(it, true)
                }
                runOnUiThread {
                    if (!isDestroyed && !isFinishing && mIsGettingMedia) {
                        startAsyncTask()
                    }
                }
            }
        }

        mLoadedInitialPhotos = true
    }

    private fun startAsyncTask() {
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(
            context = applicationContext,
            mPath = mPath,
            isPickImage = mIsGetImageIntent && !mIsGetVideoIntent,
            isPickVideo = mIsGetVideoIntent && !mIsGetImageIntent,
            showAll = mShowAll,
            isSecretVaultView = mIsVaultFilesView
        ) {
            ensureBackgroundThread {
                val oldMedia = mMedia.clone() as ArrayList<ThumbnailItem>
                val newMedia = it
                try {
                    gotMedia(newMedia, false)

                    // remove cached files that are no longer valid for whatever reason
                    val newPaths = newMedia.mapNotNull { it as? Medium }.mapTo(HashSet()) { it.path }
                    oldMedia
                        .mapNotNull { it as? Medium }
                        .filter { !newPaths.contains(it.path) }
                        .forEach {
                            if (mPath == FAVORITES && !favoritesDB.isFavorite(it.path)) {
                                mediaDB.updateFavorite(it.path, false)
                            } else if (!mPath.isVirtualAlbumPath() && !mIsVaultFilesView) {
                                // items disappearing from a virtual album were only un-referenced, the files are still valid
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                } catch (e: Exception) {
                }
            }
        }

        mCurrAsyncTask!!.execute()
    }

    private fun isDirEmpty(): Boolean {
        return if (mMedia.isEmpty() && config.filterMedia > 0) {
            if (mPath.isVirtualAlbumPath()) {
                // an empty virtual album is a valid state, show a hint instead of closing the screen
                binding.mediaGrid.adapter = null
                binding.mediaEmptyTextPlaceholder.setText(R.string.virtual_album_empty)
                binding.mediaEmptyTextPlaceholder.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
                binding.mediaFastscroller.beGone()
                return true
            }

            if (mPath != FAVORITES && mPath != RECYCLE_BIN) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
            }

            if (mPath == FAVORITES) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(FAVORITES)
                }
            }

            if (mPath == RECYCLE_BIN) {
                binding.mediaEmptyTextPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
                binding.mediaEmptyTextPlaceholder.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            } else {
                finish()
            }

            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        ensureBackgroundThread {
            try {
                directoryDB.deleteDirPath(mPath)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createNewFolder() {
        CreateNewFolderDialog(this, mPath) {
            config.tempFolderPath = it
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleTemporarilyShowHidden(true)
                }
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowHidden = show
        if (!show) {
            config.temporarilyShowVaultExcluded = false
            CryptoManager.lockVault()
            VaultSessionManager.onVaultExited()
            if ((mPath.isVirtualAlbumPath() && isHiddenVirtualAlbumPath(mPath)) ||
                (mPath.isNotEmpty() && !mPath.isVirtualAlbumPath() && CryptoManager.isEncryptedFolderPath(this, mPath))
            ) {
                finish()
                return
            }
        }
        getMedia()
        refreshMenuItems()
    }

    private fun tryToggleTemporarilyShowExcluded() {
        if (mIsVaultFilesView) return
        if (config.temporarilyShowExcluded) {
            toggleTemporarilyShowExcluded(false)
        } else {
            handleExcludedFolderPasswordProtection { toggleTemporarilyShowExcluded(true) }
        }
    }

    private fun toggleTemporarilyShowExcluded(show: Boolean) {
        config.temporarilyShowExcluded = show
        getMedia()
        refreshMenuItems()
    }

    private fun tryToggleTemporarilyShowHiddenFolders() {
        if (mIsVaultFilesView) return
        if (config.temporarilyShowHiddenFolders) {
            toggleTemporarilyShowHiddenFolders(false)
        } else if (isRPlus() && !isExternalStorageManager()) {
            GrantAllFilesDialog(this)
        } else {
            toggleTemporarilyShowHiddenFolders(true)
        }
    }

    private fun toggleTemporarilyShowHiddenFolders(show: Boolean) {
        config.temporarilyShowHiddenFolders = show
        getMedia()
        refreshMenuItems()
    }

    private fun updateExcludedFoldersAvailability() {
        val generation = ++mExcludedCheckGeneration
        ensureBackgroundThread {
            if (isDestroyed || isFinishing) return@ensureBackgroundThread
            val hasExcluded = hasExcludedNormalFolders()
            val hasHidden = hasHiddenNormalFolders()
            runOnUiThread {
                if (generation != mExcludedCheckGeneration || isDestroyed || isFinishing) return@runOnUiThread
                mHasNormalExcludedFolders = hasExcluded
                mHasNormalHiddenFolders = hasHidden
                refreshMenuItems()
            }
        }
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem> = mMedia) {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val useGridPosition = media.firstOrNull() is ThumbnailSection

            var currentGridDecoration: GridSpacingItemDecoration? = null
            if (binding.mediaGrid.itemDecorationCount > 0) {
                currentGridDecoration =
                    binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
                currentGridDecoration.items = media
            }

            val newGridDecoration = GridSpacingItemDecoration(
                spanCount = spanCount,
                spacing = spacing,
                isScrollingHorizontally = config.scrollHorizontally,
                addSideSpacing = config.fileRoundedCorners,
                items = media,
                useGridPosition = useGridPosition
            )
            if (currentGridDecoration.toString() != newGridDecoration.toString()) {
                if (currentGridDecoration != null) {
                    binding.mediaGrid.removeItemDecoration(currentGridDecoration)
                }
                binding.mediaGrid.addItemDecoration(newGridDecoration)
            }
        }
    }

    private fun initZoomListener() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun increaseColumnCount() {
        config.mediaColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.mediaColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount = config.mediaColumnCnt
        handleGridSpacing()
        refreshMenuItems()
        getMediaAdapter()?.apply {
            notifyItemRangeChanged(0, media.size)
        }
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    private fun itemClicked(path: String) {
        hideKeyboard()
        if (isSetWallpaperIntent()) {
            toast(R.string.setting_wallpaper)

            val wantedWidth = wallpaperDesiredMinimumWidth
            val wantedHeight = wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                .override((wantedWidth * ratio).toInt(), wantedHeight)
                .fitCenter()
                .apply {
                    if (CryptoManager.isEncryptedFile(path)) {
                        diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        skipMemoryCache(true)
                    }
                }

            Glide.with(this)
                .asBitmap()
                .load(File(path))
                .apply(options)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        try {
                            WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                            setResult(RESULT_OK)
                        } catch (ignored: IOException) {
                        }

                        finish()
                    }
                })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = path.toUri()
                setResult(RESULT_OK, this)
            }
            finish()
        } else {
            mWasFullscreenViewOpen = true
            if (!path.isVideoFast()) {
                openInViewPager(path)
                return
            }

            when (config.videoPlayerType) {
                VIDEO_PLAYER_SYSTEM -> openSystemDefaultPlayer(path)
                VIDEO_PLAYER_APP -> if (config.gestureVideoPlayer) launchGesturePlayer(path) else openInViewPager(path)
                else -> openInViewPager(path) // unreachable by design
            }
        }
    }

    private fun openInViewPager(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
            putExtra(PATH, path)
            // Carry the album identity explicitly: the viewer pages through this album's items
            // and uses its sort setting, regardless of the file's real filesystem folder.
            putExtra(EXTRA_ALBUM_PATH, mPath)
            putExtra(SHOW_ALL, mShowAll)
            putExtra(EXTRA_SECRET_VAULT_FILES, mIsVaultFilesView)
            putExtra(SHOW_FAVORITES, mPath == FAVORITES)
            putExtra(SHOW_VIRTUAL_ALBUM, if (mPath.isVirtualAlbumPath()) mPath else "")
            putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
            putExtra(IS_FROM_GALLERY, true)
            startActivity(this)
        }
    }

    private fun openSystemDefaultPlayer(path: String) {
        openVideoWithDefaultPlayer(
            path = path,
            albumPath = mPath,
            showAll = mShowAll,
            extras = hashMapOf(SHOW_FAVORITES to (mPath == FAVORITES)).apply {
                if (path.startsWith(recycleBinPath)) put(IS_IN_RECYCLE_BIN, true)
                if (shouldSkipAuthentication()) put(SKIP_AUTHENTICATION, true)
            }
        )
    }

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean) {
        if (!isFromCache) {
            mIsGettingMedia = false
            checkLastMediaChanged()
        }

        val previousByPath = if (!isFromCache && !mPath.isVirtualAlbumPath() && !mIsVaultFilesView && mMedia.isNotEmpty()) {
            val map = HashMap<String, Medium>(mMedia.size)
            for (item in mMedia) {
                if (item is Medium) {
                    map[item.path] = item
                }
            }
            map
        } else {
            emptyMap()
        }

        runOnUiThread {
            if (isDestroyed || isFinishing) {
                return@runOnUiThread
            }
            if (!isFromCache && mMedia.isNotEmpty()) {
                MediaUpdatePolicy.mergeCachedMediaMetadata(media, mMedia)
            }
            mMedia = media
            binding.loadingIndicator.hide()
            binding.mediaRefreshLayout.isRefreshing = false
            binding.mediaEmptyTextPlaceholder.beVisibleIf(media.isEmpty() && !isFromCache)
            binding.mediaEmptyTextPlaceholder2.beVisibleIf(media.isEmpty() && !isFromCache)

            if (binding.mediaEmptyTextPlaceholder.isVisible()) {
                binding.mediaEmptyTextPlaceholder.text = getString(R.string.no_media_with_filters)
            }
            binding.mediaFastscroller.beVisibleIf(binding.mediaEmptyTextPlaceholder.isGone())
            updateAlbumHeader()
            setupAdapter()
            applyPendingScroll()

            if (!isFromCache && mPendingMediaRefresh) {
                mPendingMediaRefresh = false
                getMedia()
            }
        }

        mLatestMediaId = getLatestMediaId()
        mLatestMediaDateId = getLatestMediaByDateId()
        if (!isFromCache && !mPath.isVirtualAlbumPath() && !mIsVaultFilesView) {
            val mediaToInsert = media
                .asSequence()
                .filter { it is Medium && it.deletedTS == 0L && !isPathHiddenOrEncrypted(it.path) }
                .map { it as Medium }
                .filter { fresh ->
                    val prev = previousByPath[fresh.path]
                    prev == null ||
                        prev.modified != fresh.modified ||
                        prev.taken != fresh.taken ||
                        prev.size != fresh.size ||
                        prev.isFavorite != fresh.isFavorite ||
                        prev.videoDuration != fresh.videoDuration ||
                        prev.name != fresh.name
                }
                .toList()
            if (mediaToInsert.isNotEmpty()) {
                ensureBackgroundThread {
                    try {
                        mediaDB.insertAll(mediaToInsert)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val filtered = fileDirItems
            .filter { !getIsPathDirectory(it.path) && (it.path.isMediaFile() || CryptoManager.isEncryptedFile(it.path)) } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        val isVaultRecycleBin = CryptoManager.isVaultRecycleBinPath(this, mPath)

        if (isVaultRecycleBin) {
            val deletingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.deleting_items,
                filtered.size,
                filtered.size
            )
            toast(deletingItems)
            ensureBackgroundThread {
                val paths = filtered.map { it.path }
                CryptoManager.permanentlyDeleteVaultRecycleBinFiles(this, paths)
                runOnUiThread {
                    mMedia.removeAll { filtered.map { f -> f.path }.contains((it as? Medium)?.path) }
                    getMediaAdapter()?.updateMedia(mMedia)
                    if (mMedia.isEmpty()) {
                        finish()
                    }
                }
            }
            return
        }

        if (
            config.useRecycleBin
            && !skipRecycleBin
            && !filtered.first().path.startsWith(recycleBinPath)
        ) {
            val movingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.moving_items_into_bin,
                filtered.size,
                filtered.size
            )
            toast(movingItems)

            val encItems = filtered.filter { CryptoManager.isEncryptedFile(it.path) }
            val plainItems = filtered.filter { !CryptoManager.isEncryptedFile(it.path) }

            ensureBackgroundThread {
                ForegroundOperationRegistry.withOperation(this, "delete") {
                    if (encItems.isNotEmpty()) {
                        CryptoManager.moveEncryptedFilesToVaultRecycleBin(this, encItems.map { it.path })
                    }

                    if (plainItems.isNotEmpty()) {
                        val hiddenPlain = plainItems.filter { isPathHiddenOrEncrypted(it.path) || mPath.isVirtualAlbumPath() }
                        val normalPlain = plainItems.filter { !hiddenPlain.contains(it) }

                        if (hiddenPlain.isNotEmpty()) {
                            val hiddenPaths = ArrayList(hiddenPlain.map { it.path })
                            movePathsInRecycleBin(hiddenPaths) { success ->
                                ensureBackgroundThread {
                                    hiddenPlain.forEach {
                                        try { File(it.path).delete() } catch (_: Exception) {}
                                        deleteDBPath(it.path)
                                        CryptoManager.purgeTracesForFile(this, it.path)
                                    }
                                }
                            }
                        }

                        if (normalPlain.isNotEmpty()) {
                            runOnUiThread {
                                movePathsInRecycleBin(normalPlain.map { it.path } as ArrayList<String>) {
                                    if (it) {
                                        deleteFilteredFiles(ArrayList(normalPlain))
                                    } else {
                                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    }
                                }
                            }
                        }
                    }

                    runOnUiThread {
                        mMedia.removeAll { filtered.map { f -> f.path }.contains((it as? Medium)?.path) }
                        getMediaAdapter()?.updateMedia(mMedia)
                        if (mPath.isVirtualAlbumPath()) {
                            val albumId = mPath.getVirtualAlbumId()
                            if (albumId != null) {
                                removePathsFromVirtualAlbum(albumId, filtered.map { it.path })
                            }
                        }
                        if (mMedia.isEmpty()) {
                            handleEmptyMediaAfterDelete()
                        }
                    }
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(
                org.fossify.commons.R.plurals.deleting_items,
                filtered.size,
                filtered.size
            )
            toast(deletingItems)

            val encItems = filtered.filter { CryptoManager.isEncryptedFile(it.path) }
            val plainItems = filtered.filter { !CryptoManager.isEncryptedFile(it.path) }

            ensureBackgroundThread {
                ForegroundOperationRegistry.withOperation(this, "delete") {
                    if (encItems.isNotEmpty()) {
                        for (item in encItems) {
                            if (ForegroundOperationRegistry.isCancelled) break
                            val f = File(item.path)
                            CryptoManager.secureWipeAndDeleteFile(this, f)
                            deleteDBPath(item.path)
                        }
                    }

                    if (plainItems.isNotEmpty()) {
                        val hiddenPlain = plainItems.filter { isPathHiddenOrEncrypted(it.path) || mPath.isVirtualAlbumPath() }
                        val normalPlain = plainItems.filter { !hiddenPlain.contains(it) }

                        if (hiddenPlain.isNotEmpty()) {
                            for (item in hiddenPlain) {
                                if (ForegroundOperationRegistry.isCancelled) break
                                try { File(item.path).delete() } catch (_: Exception) {}
                                deleteDBPath(item.path)
                                CryptoManager.purgeTracesForFile(this, item.path)
                            }
                        }

                        if (normalPlain.isNotEmpty()) {
                            runOnUiThread {
                                deleteFilteredFiles(ArrayList(normalPlain), isPermanentDelete = true)
                            }
                        }
                    }

                    runOnUiThread {
                        mMedia.removeAll { filtered.map { f -> f.path }.contains((it as? Medium)?.path) }
                        getMediaAdapter()?.updateMedia(mMedia)
                        if (mPath.isVirtualAlbumPath()) {
                            val albumId = mPath.getVirtualAlbumId()
                            if (albumId != null) {
                                removePathsFromVirtualAlbum(albumId, filtered.map { it.path })
                            }
                        }
                        if (mMedia.isEmpty()) {
                            handleEmptyMediaAfterDelete()
                        }
                    }
                }
            }
        }
    }

    private fun handleEmptyMediaAfterDelete() {
        if (mIsVaultFilesView) {
            runOnUiThread {
                gotMedia(ArrayList(), false)
            }
        } else {
            deleteDirectoryIfEmpty()
            deleteDBDirectory()
            finish()
        }
    }

    private fun shouldSkipAuthentication(): Boolean {
        return intent.getBooleanExtra(SKIP_AUTHENTICATION, false)
    }

    private fun deleteFilteredFiles(
        filtered: ArrayList<FileDirItem>,
        isPermanentDelete: Boolean = !config.useRecycleBin
    ) {
        deleteFiles(filtered) {
            if (!it) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return@deleteFiles
            }

            mMedia.removeAll { filtered.map { it.path }.contains((it as? Medium)?.path) }

            ensureBackgroundThread {
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || isPermanentDelete) {
                        deleteDBPath(it.path)
                    }
                }
            }

            if (mMedia.isEmpty()) {
                handleEmptyMediaAfterDelete()
            }
        }
    }

    override fun refreshItems() {
        getMedia()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {
        var currentGridPosition = 0
        media.forEach {
            if (it is Medium) {
                it.gridPosition = currentGridPosition++
            } else if (it is ThumbnailSection) {
                currentGridPosition = 0
            }
        }

        if (binding.mediaGrid.itemDecorationCount > 0) {
            val currentGridDecoration =
                binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
            currentGridDecoration.items = media
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val isVaultFolder = (mPath.isNotEmpty() && !mPath.isVirtualAlbumPath() && CryptoManager.isEncryptedFolderPath(this, mPath)) ||
            CryptoManager.isVaultRecycleBinPath(this, mPath)

        // The gesture must also run with horizontal scrolling enabled; it provides
        // pull-to-refresh in the all-files view and pull-to-open-vault here.
        if (mShowAll && !mIsVaultFilesView && !mIsVaultOpening && !isVaultFolder) {
            if (vaultPullGesture.handleTouchEvent(ev)) {
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun triggerSecretVaultMenu() {
        openSecretVaultPrompt(
            onCancelled = { mIsVaultOpening = false },
            onVaultReady = {
                if (VaultViewPolicy.shouldOpenVaultInFilesView(isFromFilesView = true)) {
                    openVaultFilesView()
                } else {
                    mIsVaultOpening = false
                }
            }
        )
    }

    private fun openVaultFilesView() {
        ensureBackgroundThread {
            CryptoManager.getOrCreateDefaultHiddenAlbum(this)
            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    mIsVaultOpening = false
                    return@runOnUiThread
                }
                mIsSwitchingVaultView = true
                config.showAll = false
                config.temporarilyShowHidden = true
                Intent(this, MediaActivity::class.java).apply {
                    putExtra(DIRECTORY, "")
                    putExtra(EXTRA_SECRET_VAULT_FILES, true)
                    putExtra(SKIP_AUTHENTICATION, true)
                    if (isThirdPartyPickIntent()) {
                        if (mIsGetImageIntent) putExtra(GET_IMAGE_INTENT, true)
                        if (mIsGetVideoIntent) putExtra(GET_VIDEO_INTENT, true)
                        if (mIsGetAnyIntent) putExtra(GET_ANY_INTENT, true)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mAllowPickingMultiple)
                        addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                    }
                    hideKeyboard()
                    startActivity(this)
                    overridePendingTransition(R.anim.vault_shutter_enter, R.anim.vault_shutter_exit)
                    finish()
                }
            }
        }
    }
}
