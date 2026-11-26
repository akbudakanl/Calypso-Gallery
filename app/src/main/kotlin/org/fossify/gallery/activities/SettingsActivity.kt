package org.fossify.gallery.activities

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import org.fossify.commons.dialogs.ChangeDateTimeFormatDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isPiePlus
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivitySettingsBinding
import org.fossify.gallery.dialogs.ChangeFileThumbnailStyleDialog
import org.fossify.gallery.dialogs.ChangeFolderThumbnailStyleDialog
import org.fossify.gallery.dialogs.ChangeLanguageDialog
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.dialogs.ManageExtendedDetailsDialog
import org.fossify.gallery.dialogs.ManageExtraViewerActionsDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossify.gallery.extensions.handleMediaManagementPrompt
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.FOLDER_STYLE_SQUARE
import org.fossify.gallery.helpers.HOME_VIEW_FILES
import org.fossify.gallery.helpers.HOME_VIEW_FOLDERS
import org.fossify.gallery.helpers.PRIORITY_COMPROMISE
import org.fossify.gallery.helpers.PRIORITY_SPEED
import org.fossify.gallery.helpers.PRIORITY_VALIDITY
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.ROTATE_BY_DEVICE_ROTATION
import org.fossify.gallery.helpers.ROTATE_BY_SYSTEM_SETTING
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import java.util.Locale

class SettingsActivity : SimpleActivity() {
    companion object {
        const val EXTRA_SETTINGS_CATEGORY = "extra_settings_category"
        const val EXTRA_HIGHLIGHT_VIEW_ID = "extra_highlight_view_id"

        const val CATEGORY_APPLICATION = "application"
        const val CATEGORY_SECURITY = "security"
        const val CATEGORY_VIEWER = "viewer"
        const val CATEGORY_VIDEO = "video"
        const val CATEGORY_THUMBNAILS = "thumbnails"
        const val CATEGORY_FILES = "files"
        const val CATEGORY_RECYCLE = "recycle"
        const val CATEGORY_MIGRATING = "migrating"
        const val CATEGORY_NOTIFICATIONS = "notifications"

        internal const val PICK_IMPORT_SOURCE_INTENT = 1
        internal const val SELECT_EXPORT_FAVORITES_FILE_INTENT = 2
        internal const val SELECT_IMPORT_FAVORITES_FILE_INTENT = 3
        internal const val ROW_DECORATION_TAG = "settings_row_decoration"

        /** Rows that perform an immediate action instead of opening a sub-screen. */
        internal val ACTION_ROW_IDS = setOf(
            R.id.settings_create_vault_holder,
            R.id.settings_change_vault_password_holder,
            R.id.settings_export_vault_holder,
            R.id.settings_sync_vault_holder,
            R.id.settings_empty_vault_holder,
            R.id.settings_archive_vault_holder,
            R.id.settings_empty_recycle_bin_holder,
            R.id.settings_clear_cache_holder,
            R.id.settings_export_favorites_holder,
            R.id.settings_import_favorites_holder,
            R.id.settings_export_holder,
            R.id.settings_import_holder,
        )
    }

    internal data class CategorySpec(
        val key: String,
        @StringRes val titleRes: Int,
        @DrawableRes val iconRes: Int,
        val container: LinearLayout,
        val groups: List<Pair<LinearLayout, TextView>>,
    )

    internal var mRecycleBinContentSize = 0L
    internal var activeSettingsSearch = ""
    internal var isSettingUpItems = false
    internal var pendingHighlightViewId = 0
    internal var biometricStatus = BiometricManager.BIOMETRIC_STATUS_UNKNOWN
    internal var biometricKeyAvailable = false
    private var userPresentReceiver: BroadcastReceiver? = null
    internal var biometricUsable = false
    internal var isVaultSectionNearViewport = false
    internal var vaultSectionScrollListener: ViewTreeObserver.OnScrollChangedListener? = null
    internal val binding by viewBinding(ActivitySettingsBinding::inflate)

    internal val activeCategory: String?
        get() = intent.getStringExtra(EXTRA_SETTINGS_CATEGORY)

    internal val categorySpecs: List<CategorySpec> by lazy {
        listOf(
            CategorySpec(
                key = CATEGORY_APPLICATION,
                titleRes = R.string.application_settings,
                iconRes = R.drawable.ic_section_application,
                container = binding.settingsCategoryApplication,
                groups = listOf(
                    binding.settingsApplicationGeneralCard to binding.settingsApplicationGeneralEyebrow,
                    binding.settingsApplicationFoldersCard to binding.settingsApplicationFoldersEyebrow,
                ),
            ),
            CategorySpec(
                key = CATEGORY_SECURITY,
                titleRes = R.string.security_and_vault_settings,
                iconRes = R.drawable.ic_section_security,
                container = binding.settingsCategorySecurity,
                groups = listOf(
                    binding.settingsSecurityCard to binding.settingsSecurityEyebrow,
                    binding.settingsSecureSharingCard to binding.settingsSecureSharingEyebrow,
                    binding.settingsVaultCard to binding.settingsVaultEyebrow,
                    binding.settingsVaultManagementCard to binding.settingsVaultManagementEyebrow,
                ),
            ),
            CategorySpec(
                key = CATEGORY_VIEWER,
                titleRes = R.string.media_viewer_settings,
                iconRes = R.drawable.ic_section_viewer,
                container = binding.settingsCategoryViewer,
                groups = listOf(
                    binding.settingsFullscreenCard to binding.settingsFullscreenEyebrow,
                    binding.settingsDeepZoomCard to binding.settingsDeepZoomEyebrow,
                ),
            ),
            CategorySpec(
                key = CATEGORY_VIDEO,
                titleRes = R.string.video_playback_settings,
                iconRes = R.drawable.ic_section_video,
                container = binding.settingsCategoryVideo,
                groups = listOf(
                    binding.settingsVideoPlaybackCard to binding.settingsVideoPlaybackEyebrow,
                    binding.settingsVideoGesturesCard to binding.settingsVideoGesturesEyebrow,
                ),
            ),
            CategorySpec(
                key = CATEGORY_THUMBNAILS,
                titleRes = R.string.thumbnails_and_scrolling_settings,
                iconRes = R.drawable.ic_section_thumbnails,
                container = binding.settingsCategoryThumbnails,
                groups = listOf(
                    binding.settingsThumbnailsDisplayCard to binding.settingsThumbnailsDisplayEyebrow,
                    binding.settingsThumbnailsStyleCard to binding.settingsThumbnailsStyleEyebrow,
                ),
            ),
            CategorySpec(
                key = CATEGORY_FILES,
                titleRes = org.fossify.commons.R.string.file_operations,
                iconRes = R.drawable.ic_section_files,
                container = binding.settingsCategoryFiles,
                groups = listOf(
                    binding.settingsFilesBehaviorCard to binding.settingsFilesBehaviorEyebrow,
                    binding.settingsFilesConflictsCard to binding.settingsFilesConflictsEyebrow,
                ),
            ),
            CategorySpec(
                key = CATEGORY_RECYCLE,
                titleRes = org.fossify.commons.R.string.recycle_bin,
                iconRes = R.drawable.ic_section_recycle,
                container = binding.settingsCategoryRecycle,
                groups = listOf(
                    binding.settingsRecycleBinCard to binding.settingsRecycleBinEyebrow,
                    binding.settingsRecycleCleanupCard to binding.settingsRecycleCleanupEyebrow,
                ),
            ),
            CategorySpec(
                key = CATEGORY_MIGRATING,
                titleRes = org.fossify.commons.R.string.migrating,
                iconRes = R.drawable.ic_section_migration,
                container = binding.settingsCategoryMigrating,
                groups = listOf(
                    binding.settingsMigratingStorageCard to binding.settingsMigratingStorageEyebrow,
                    binding.settingsMigratingBackupCard to binding.settingsMigratingBackupEyebrow,
                    binding.settingsMigratingVaultCard to binding.settingsMigratingVaultEyebrow,
                ),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.settingsAppbar),
            padBottomSystem = listOf(binding.settingsNestedScrollview),
        )

        pendingHighlightViewId = intent.getIntExtra(EXTRA_HIGHLIGHT_VIEW_ID, 0)
        applyActiveCategoryVisibility()
        setupSettingsSearch()
    }

    override fun onResume() {
        super.onResume()
        // Keep the top bar in the normal background color. The commons scroll listener
        // would animate it to the primary color on the first downward scroll.
        setupTopAppBar(
            binding.settingsAppbar,
            NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor(),
        )
        binding.settingsToolbar.title = resolveToolbarTitle()
        setupSettingItems()
        registerBiometricStateReceiver()
        if (activeCategory == CATEGORY_SECURITY) {
            registerVaultSectionScrollRecheck()
        }
        // Returning from Appearance recreates that activity but not this one, so the search bar
        // colors and TextViews inflated with the previous font would otherwise stay stale.
        refreshSettingsSearchColors()
        refreshSettingsFont()
        scrollToHighlightedSettingIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        unregisterBiometricStateReceiver()
        unregisterVaultSectionScrollRecheck()
    }

    /**
     * Android does not notify apps when a biometric is enrolled or removed. `onResume` covers
     * leaving and re-entering Settings; this receiver covers screen-off/on while Settings stays
     * in the foreground, which does not necessarily trigger `onResume`.
     */
    private fun registerBiometricStateReceiver() {
        if (userPresentReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT && !isFinishing && !isDestroyed) {
                    setupVaultSettings()
                }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            userPresentReceiver = receiver
        }
    }

    private fun unregisterBiometricStateReceiver() {
        userPresentReceiver?.let { runCatching { unregisterReceiver(it) } }
        userPresentReceiver = null
    }

    private fun resolveToolbarTitle(): String {
        val cat = activeCategory ?: return getString(org.fossify.commons.R.string.settings)
        val spec = categorySpecs.firstOrNull { it.key == cat }
        return spec?.let { getString(it.titleRes) } ?: getString(org.fossify.commons.R.string.settings)
    }

    private fun applyActiveCategoryVisibility() {
        val cat = activeCategory
        binding.settingsHubContainer.isVisible = cat == null
        for (spec in categorySpecs) {
            spec.container.isVisible = spec.key == cat
        }
        binding.settingsToolbar.title = resolveToolbarTitle()
    }

    private fun scrollToHighlightedSettingIfNeeded() {
        val targetId = pendingHighlightViewId
        if (targetId <= 0 || activeCategory == null) return
        pendingHighlightViewId = 0
        binding.settingsNestedScrollview.post {
            val targetView = findViewById<View>(targetId)
            if (targetView != null && targetView.isShown) {
                val targetLoc = IntArray(2)
                val scrollLoc = IntArray(2)
                targetView.getLocationOnScreen(targetLoc)
                binding.settingsNestedScrollview.getLocationOnScreen(scrollLoc)
                val delta = targetLoc[1] - scrollLoc[1] - (binding.settingsNestedScrollview.height / 4)
                binding.settingsNestedScrollview.smoothScrollBy(0, delta)
            }
        }
    }

    internal fun openCategory(categoryKey: String, highlightViewId: Int = 0) {
        hideKeyboard()
        startActivity(
            Intent(this, SettingsActivity::class.java).apply {
                putExtra(EXTRA_SETTINGS_CATEGORY, categoryKey)
                if (highlightViewId > 0) {
                    putExtra(EXTRA_HIGHLIGHT_VIEW_ID, highlightViewId)
                }
            },
        )
    }

    internal fun setupSettingItems() {
        isSettingUpItems = true
        try {
            setupUseEnglish()
            setupLanguage()
            setupChangeDateTimeFormat()
            setupFileLoadingPriority()
            setupManageIncludedFolders()
            setupManageExcludedFolders()
            setupManageHiddenFolders()
            setupHomeScreenViewOnLaunch()
            setupShowHiddenItems()
            setupAutoplayVideos()
            setupRememberLastVideo()
            setupLoopVideos()
            setupOpenVideosOnSeparateScreen()
            setupOnVideoTap()
            setupVideoDoubleTapSeekDuration()
            setupMaxBrightness()
            setupUltraHdrRendering()
            setupCropThumbnails()
            setupAnimateGifs()
            setupDarkBackground()
            setupScrollHorizontally()
            setupScreenRotation()
            setupHideSystemUI()
            setupShowBottomActionNames()

            setupExcludedItemPasswordProtection()
            setupAppPasswordProtection()
            setupFileDeletionPasswordProtection()
            setupShareMetadata()
            setupVaultSettings()
            setupConflictHandlingMode()
            setupSkippedEncryptFilesMode()
            setupAlbumCryptoConflictMode()
            setupDeleteEmptyFolders()
            setupKeepScreenOn()
            setupAllowPhotoGestures()
            setupAllowVideoGestures()
            setupAllowDownGesture()
            setupAllowRotatingWithGestures()
            setupShowNotch()
            setupFileThumbnailStyle()
            setupFolderThumbnailStyle()
            setupKeepLastModified()
            setupAllowZoomingImages()
            setupShowHighestQuality()
            setupAllowOneToOneZoom()
            setupAllowInstantChange()
            setupShowExtendedDetails()
            setupHideExtendedDetails()
            setupManageExtendedDetails()
            setupShowExtraViewerActions()
            setupManageExtraViewerActions()
            setupSkipDeleteConfirmation()
            setupDeleteOriginalFromFavoritesAndVirtualAlbums()
            setupAutoBackupFavoritesAndVirtualAlbums()
            setupUseRecycleBin()
            setupShowRecycleBin()
            setupShowRecycleBinLast()
            setupEmptyRecycleBin()
            updateTextColors(binding.settingsHolder)
            setupClearCache()
            setupExportFavorites()
            setupImportFavorites()
            setupExportSettings()
            setupImportSettings()
        } finally {
            isSettingUpItems = false
        }

        refreshRowDecorations()
        applySettingsSearch()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val inputStream = contentResolver.openInputStream(resultData.data!!)
            parseFile(inputStream)
        } else if (requestCode == SELECT_EXPORT_FAVORITES_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val outputStream = contentResolver.openOutputStream(resultData.data!!)
            exportFavoritesTo(outputStream)
        } else if (requestCode == SELECT_IMPORT_FAVORITES_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            val inputStream = contentResolver.openInputStream(resultData.data!!)
            importFavorites(inputStream)
        }
    }

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beGone()
    }

    private fun setupLanguage() {
        binding.settingsUseEnglishHolder.beGone()
        binding.settingsLanguageHolder.beVisible()
        binding.settingsLanguage.text = getCurrentLanguageDisplayName()
        binding.settingsLanguageHolder.setOnClickListener {
            ChangeLanguageDialog(this) {
                binding.settingsLanguage.text = getCurrentLanguageDisplayName()
            }
        }
    }

    private fun getCurrentLanguageDisplayName(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val locale = appLocales.get(0)
            if (locale != null) {
                return ChangeLanguageDialog.getNativeDisplayName(locale)
            }
        } else if (config.useEnglish) {
            return "English"
        }
        if (appLocales.isEmpty) {
            val currentLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                resources.configuration.locales.get(0) ?: Locale.getDefault()
            } else {
                @Suppress("DEPRECATION")
                resources.configuration.locale ?: Locale.getDefault()
            }
            config.lastSystemLocaleTag = currentLoc.toLanguageTag()
        }
        val systemLangLabel = ChangeLanguageDialog.getSystemLanguageLabel(this)
        return "${getString(R.string.system_default)} - $systemLangLabel"
    }

    private fun setupChangeDateTimeFormat() {
        binding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFileLoadingPriority() {
        binding.settingsFileLoadingPriorityHolder.beGoneIf(isRPlus() && !isExternalStorageManager())
        binding.settingsFileLoadingPriority.text = getFileLoadingPriorityText()
        binding.settingsFileLoadingPriorityHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(PRIORITY_SPEED, getString(R.string.speed)),
                RadioItem(PRIORITY_COMPROMISE, getString(R.string.compromise)),
                RadioItem(PRIORITY_VALIDITY, getString(R.string.avoid_showing_invalid_files))
            )

            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.fileLoadingPriority,
                titleId = R.string.file_loading_priority,
            ) {
                config.fileLoadingPriority = it as Int
                binding.settingsFileLoadingPriority.text = getFileLoadingPriorityText()
            }
        }
    }

    private fun getFileLoadingPriorityText() = getString(
        when (config.fileLoadingPriority) {
            PRIORITY_SPEED -> R.string.speed
            PRIORITY_COMPROMISE -> R.string.compromise
            else -> R.string.avoid_showing_invalid_files
        }
    )

    private fun setupManageIncludedFolders() {
        if (isRPlus() && !isExternalStorageManager()) {
            binding.settingsManageIncludedFolders.text =
                "${getString(R.string.manage_included_folders)} (${getString(org.fossify.commons.R.string.no_permission)})"
        } else {
            binding.settingsManageIncludedFolders.setText(R.string.manage_included_folders)
        }

        binding.settingsManageIncludedFoldersHolder.setOnClickListener {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                startActivity(Intent(this, IncludedFoldersActivity::class.java))
            }
        }
    }

    private fun setupManageExcludedFolders() {
        binding.settingsManageExcludedFoldersHolder.setOnClickListener {
            handleExcludedFolderPasswordProtection {
                startActivity(Intent(this, ExcludedFoldersActivity::class.java))
            }
        }
    }

    private fun setupManageHiddenFolders() {
        binding.settingsManageHiddenFoldersHolder.beVisible()
        binding.settingsManageHiddenFoldersHolder.setOnClickListener {
            startActivity(Intent(this, HiddenFoldersActivity::class.java))
        }
    }

    private fun setupShowHiddenItems() {
        config.showHiddenMedia = false
        binding.settingsShowHiddenItemsHolder.beGone()
    }

    private fun setupHomeScreenViewOnLaunch() {
        binding.settingsHomeScreenViewOnLaunch.text = getHomeScreenViewOnLaunchText()
        binding.settingsHomeScreenViewOnLaunchHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(HOME_VIEW_FOLDERS, getString(R.string.show_folders)),
                RadioItem(HOME_VIEW_FILES, getString(R.string.show_files))
            )

            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.homeScreenViewOnLaunch,
                titleId = R.string.main_screen_view_on_launch,
            ) {
                config.homeScreenViewOnLaunch = it as Int
                binding.settingsHomeScreenViewOnLaunch.text = getHomeScreenViewOnLaunchText()
            }
        }
    }

    private fun getHomeScreenViewOnLaunchText() = getString(
        when (config.homeScreenViewOnLaunch) {
            HOME_VIEW_FILES -> R.string.show_files
            else -> R.string.show_folders
        }
    )

    private fun setupAutoplayVideos() {
        binding.settingsAutoplayVideos.isChecked = config.autoplayVideos
        binding.settingsAutoplayVideosHolder.setOnClickListener {
            binding.settingsAutoplayVideos.toggle()
            config.autoplayVideos = binding.settingsAutoplayVideos.isChecked
        }
    }

    private fun setupRememberLastVideo() {
        binding.settingsRememberLastVideoPosition.isChecked = config.rememberLastVideoPosition
        binding.settingsRememberLastVideoPositionHolder.setOnClickListener {
            binding.settingsRememberLastVideoPosition.toggle()
            config.rememberLastVideoPosition = binding.settingsRememberLastVideoPosition.isChecked
        }
    }

    private fun setupLoopVideos() {
        binding.settingsLoopVideos.isChecked = config.loopVideos
        binding.settingsLoopVideosHolder.setOnClickListener {
            binding.settingsLoopVideos.toggle()
            config.loopVideos = binding.settingsLoopVideos.isChecked
        }
    }

    private fun setupOpenVideosOnSeparateScreen() {
        binding.settingsOpenVideosOnSeparateScreen.isChecked = config.gestureVideoPlayer
        binding.settingsOpenVideosOnSeparateScreenHolder.setOnClickListener {
            binding.settingsOpenVideosOnSeparateScreen.toggle()
            config.gestureVideoPlayer = binding.settingsOpenVideosOnSeparateScreen.isChecked
        }
    }

    private fun setupOnVideoTap() {
        binding.settingsOnVideoTap.text = getVideoPlayerTypeText()
        binding.settingsOnVideoTapHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(VIDEO_PLAYER_APP, getString(R.string.open_in_app_player)),
                RadioItem(VIDEO_PLAYER_SYSTEM, getString(R.string.open_system_default_player))
            )

            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.videoPlayerType,
                titleId = R.string.on_video_tap,
            ) {
                config.videoPlayerType = it as Int
                binding.settingsOnVideoTap.text = getVideoPlayerTypeText()
            }
        }
    }

    private fun getVideoPlayerTypeText() = getString(
        when (config.videoPlayerType) {
            VIDEO_PLAYER_APP -> R.string.open_in_app_player
            else -> R.string.open_system_default_player
        }
    )

    private fun setupVideoDoubleTapSeekDuration() {
        binding.settingsVideoDoubleTapSeekDuration.text = getVideoSeekDurationText()
        binding.settingsVideoDoubleTapSeekDurationHolder.setOnClickListener {
            val durations = listOf(3, 5, 10, 15)
            val items = durations.map { duration ->
                RadioItem(duration, String.format(getString(R.string.video_seek_duration_seconds), duration))
            } as ArrayList<RadioItem>

            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.videoDoubleTapFastForward,
                titleId = R.string.video_double_tap_duration,
            ) {
                config.videoDoubleTapFastForward = it as Int
                binding.settingsVideoDoubleTapSeekDuration.text = getVideoSeekDurationText()
            }
        }
    }

    private fun getVideoSeekDurationText(): String {
        return String.format(getString(R.string.video_seek_duration_seconds), config.videoDoubleTapFastForward)
    }

    private fun setupMaxBrightness() {
        binding.settingsMaxBrightness.isChecked = config.maxBrightness
        binding.settingsMaxBrightnessHolder.setOnClickListener {
            binding.settingsMaxBrightness.toggle()
            config.maxBrightness = binding.settingsMaxBrightness.isChecked
        }
    }

    private fun setupUltraHdrRendering() {
        binding.settingsUltraHdrRenderingHolder.beVisibleIf(ColorModeHelper.isGainmapSupported())
        binding.settingsUltraHdrRendering.isChecked = config.ultraHdrRendering
        binding.settingsUltraHdrRenderingHolder.setOnClickListener {
            binding.settingsUltraHdrRendering.toggle()
            config.ultraHdrRendering = binding.settingsUltraHdrRendering.isChecked
        }
    }

    private fun setupCropThumbnails() {
        binding.settingsCropThumbnails.isChecked = config.cropThumbnails
        binding.settingsCropThumbnailsHolder.setOnClickListener {
            binding.settingsCropThumbnails.toggle()
            config.cropThumbnails = binding.settingsCropThumbnails.isChecked
        }
    }

    private fun setupAnimateGifs() {
        binding.settingsAnimateGifs.isChecked = config.animateGifs
        binding.settingsAnimateGifsHolder.setOnClickListener {
            binding.settingsAnimateGifs.toggle()
            config.animateGifs = binding.settingsAnimateGifs.isChecked
        }
    }

    private fun setupDarkBackground() {
        binding.settingsBlackBackground.isChecked = config.blackBackground
        binding.settingsBlackBackgroundHolder.setOnClickListener {
            binding.settingsBlackBackground.toggle()
            config.blackBackground = binding.settingsBlackBackground.isChecked
        }
    }

    private fun setupScrollHorizontally() {
        binding.settingsScrollHorizontally.isChecked = config.scrollHorizontally
        binding.settingsScrollHorizontallyHolder.setOnClickListener {
            binding.settingsScrollHorizontally.toggle()
            config.scrollHorizontally = binding.settingsScrollHorizontally.isChecked
        }
    }

    private fun setupHideSystemUI() {
        binding.settingsHideSystemUi.isChecked = config.hideSystemUI
        binding.settingsHideSystemUiHolder.setOnClickListener {
            binding.settingsHideSystemUi.toggle()
            config.hideSystemUI = binding.settingsHideSystemUi.isChecked
        }
    }

    private fun setupShowBottomActionNames() {
        binding.settingsShowBottomActionNames.isChecked = config.showBottomActionNames
        binding.settingsShowBottomActionNamesHolder.setOnClickListener {
            binding.settingsShowBottomActionNames.toggle()
            config.showBottomActionNames = binding.settingsShowBottomActionNames.isChecked
        }
    }

    private fun setupKeepScreenOn() {
        binding.settingsKeepScreenOnFullscreenPhotos.isChecked = config.keepScreenOn
        binding.settingsKeepScreenOnFullscreenPhotosHolder.setOnClickListener {
            binding.settingsKeepScreenOnFullscreenPhotos.toggle()
            config.keepScreenOn = binding.settingsKeepScreenOnFullscreenPhotos.isChecked
        }
    }

    private fun setupAllowPhotoGestures() {
        binding.settingsAllowPhotoGestures.isChecked = config.allowPhotoGestures
        binding.settingsAllowPhotoGesturesHolder.setOnClickListener {
            binding.settingsAllowPhotoGestures.toggle()
            config.allowPhotoGestures = binding.settingsAllowPhotoGestures.isChecked
        }
    }

    private fun setupAllowVideoGestures() {
        binding.settingsAllowVideoGestures.isChecked = config.allowVideoGestures
        binding.settingsAllowVideoGesturesHolder.setOnClickListener {
            binding.settingsAllowVideoGestures.toggle()
            config.allowVideoGestures = binding.settingsAllowVideoGestures.isChecked
        }
    }

    private fun setupAllowDownGesture() {
        binding.settingsAllowDownGesture.isChecked = config.allowDownGesture
        binding.settingsAllowDownGestureHolder.setOnClickListener {
            binding.settingsAllowDownGesture.toggle()
            config.allowDownGesture = binding.settingsAllowDownGesture.isChecked
        }
    }

    private fun setupAllowRotatingWithGestures() {
        binding.settingsAllowRotatingWithGestures.isChecked = config.allowRotatingWithGestures
        binding.settingsAllowRotatingWithGesturesHolder.setOnClickListener {
            binding.settingsAllowRotatingWithGestures.toggle()
            config.allowRotatingWithGestures = binding.settingsAllowRotatingWithGestures.isChecked
        }
    }

    private fun setupShowNotch() {
        binding.settingsShowNotchHolder.beVisibleIf(isPiePlus())
        binding.settingsShowNotch.isChecked = config.showNotch
        binding.settingsShowNotchHolder.setOnClickListener {
            binding.settingsShowNotch.toggle()
            config.showNotch = binding.settingsShowNotch.isChecked
        }
    }

    private fun setupFileThumbnailStyle() {
        binding.settingsFileThumbnailStyleHolder.setOnClickListener {
            ChangeFileThumbnailStyleDialog(this)
        }
    }

    private fun setupFolderThumbnailStyle() {
        binding.settingsFolderThumbnailStyle.text = getFolderStyleText()
        binding.settingsFolderThumbnailStyleHolder.setOnClickListener {
            ChangeFolderThumbnailStyleDialog(this) {
                binding.settingsFolderThumbnailStyle.text = getFolderStyleText()
            }
        }
    }

    private fun getFolderStyleText() = getString(
        when (config.folderStyle) {
            FOLDER_STYLE_SQUARE -> R.string.square
            else -> R.string.rounded_corners
        }
    )

    private fun setupKeepLastModified() {
        binding.settingsKeepLastModified.isChecked = config.keepLastModified
        binding.settingsKeepLastModifiedHolder.setOnClickListener {
            handleMediaManagementPrompt {
                binding.settingsKeepLastModified.toggle()
                config.keepLastModified = binding.settingsKeepLastModified.isChecked
            }
        }
    }

    private fun setupAllowZoomingImages() {
        binding.settingsAllowZoomingImages.isChecked = config.allowZoomingImages
        updateDeepZoomToggleButtons()
        binding.settingsAllowZoomingImagesHolder.setOnClickListener {
            binding.settingsAllowZoomingImages.toggle()
            config.allowZoomingImages = binding.settingsAllowZoomingImages.isChecked
            updateDeepZoomToggleButtons()
        }
    }

    private fun updateDeepZoomToggleButtons() {
        binding.settingsAllowRotatingWithGesturesHolder.beVisibleIf(config.allowZoomingImages)
        binding.settingsShowHighestQualityHolder.beVisibleIf(config.allowZoomingImages)
        binding.settingsAllowOneToOneZoomHolder.beVisibleIf(config.allowZoomingImages)
        if (!isSettingUpItems) {
            refreshRowDecorations()
        }
    }

    private fun setupShowHighestQuality() {
        binding.settingsShowHighestQuality.isChecked = config.showHighestQuality
        binding.settingsShowHighestQualityHolder.setOnClickListener {
            binding.settingsShowHighestQuality.toggle()
            config.showHighestQuality = binding.settingsShowHighestQuality.isChecked
        }
    }

    private fun setupAllowOneToOneZoom() {
        binding.settingsAllowOneToOneZoom.isChecked = config.allowOneToOneZoom
        binding.settingsAllowOneToOneZoomHolder.setOnClickListener {
            binding.settingsAllowOneToOneZoom.toggle()
            config.allowOneToOneZoom = binding.settingsAllowOneToOneZoom.isChecked
        }
    }

    private fun setupAllowInstantChange() {
        binding.settingsAllowInstantChange.isChecked = config.allowInstantChange
        binding.settingsAllowInstantChangeHolder.setOnClickListener {
            binding.settingsAllowInstantChange.toggle()
            config.allowInstantChange = binding.settingsAllowInstantChange.isChecked
        }
    }

    private fun setupShowExtendedDetails() {
        binding.settingsShowExtendedDetails.isChecked = config.showExtendedDetails
        updateExtendedDetailsButtons()
        binding.settingsShowExtendedDetailsHolder.setOnClickListener {
            binding.settingsShowExtendedDetails.toggle()
            config.showExtendedDetails = binding.settingsShowExtendedDetails.isChecked
            updateExtendedDetailsButtons()
        }
    }

    private fun setupHideExtendedDetails() {
        binding.settingsHideExtendedDetails.isChecked = config.hideExtendedDetails
        binding.settingsHideExtendedDetailsHolder.setOnClickListener {
            binding.settingsHideExtendedDetails.toggle()
            config.hideExtendedDetails = binding.settingsHideExtendedDetails.isChecked
        }
    }

    private fun setupManageExtendedDetails() {
        binding.settingsManageExtendedDetailsHolder.setOnClickListener {
            ManageExtendedDetailsDialog(this) {
                if (config.extendedDetails == 0) {
                    binding.settingsShowExtendedDetailsHolder.callOnClick()
                }
            }
        }
    }

    private fun updateExtendedDetailsButtons() {
        binding.settingsManageExtendedDetailsHolder.beVisibleIf(config.showExtendedDetails)
        binding.settingsHideExtendedDetailsHolder.beVisibleIf(config.showExtendedDetails)
        if (!isSettingUpItems) {
            refreshRowDecorations()
        }
    }

    private fun setupShowExtraViewerActions() {
        binding.settingsShowExtraViewerActions.isChecked = config.showExtraViewerActions
        updateExtraViewerActionsButton()
        binding.settingsShowExtraViewerActionsHolder.setOnClickListener {
            binding.settingsShowExtraViewerActions.toggle()
            config.showExtraViewerActions = binding.settingsShowExtraViewerActions.isChecked
            updateExtraViewerActionsButton()
        }
    }

    private fun setupManageExtraViewerActions() {
        binding.settingsManageExtraViewerActionsHolder.setOnClickListener {
            ManageExtraViewerActionsDialog(this) {
                if (config.extraViewerActions == 0) {
                    binding.settingsShowExtraViewerActionsHolder.callOnClick()
                }
            }
        }
    }

    private fun updateExtraViewerActionsButton() {
        binding.settingsManageExtraViewerActionsHolder.beVisibleIf(config.showExtraViewerActions)
        if (!isSettingUpItems) {
            refreshRowDecorations()
        }
    }

    private fun setupSkipDeleteConfirmation() {
        binding.settingsSkipDeleteConfirmation.isChecked = config.skipDeleteConfirmation
        binding.settingsSkipDeleteConfirmationHolder.setOnClickListener {
            binding.settingsSkipDeleteConfirmation.toggle()
            config.skipDeleteConfirmation = binding.settingsSkipDeleteConfirmation.isChecked
        }
    }

    private fun setupDeleteOriginalFromFavoritesAndVirtualAlbums() {
        binding.settingsDeleteOriginalFromFavoritesAndVirtualAlbums.isChecked = config.deleteOriginalFromFavoritesAndVirtualAlbums
        binding.settingsDeleteOriginalFromFavoritesAndVirtualAlbumsHolder.setOnClickListener {
            binding.settingsDeleteOriginalFromFavoritesAndVirtualAlbums.toggle()
            config.deleteOriginalFromFavoritesAndVirtualAlbums = binding.settingsDeleteOriginalFromFavoritesAndVirtualAlbums.isChecked
        }
    }

    private fun setupScreenRotation() {
        binding.settingsScreenRotation.text = getScreenRotationText()
        binding.settingsScreenRotationHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ROTATE_BY_SYSTEM_SETTING, getString(R.string.screen_rotation_system_setting)),
                RadioItem(ROTATE_BY_DEVICE_ROTATION, getString(R.string.screen_rotation_device_rotation)),
                RadioItem(ROTATE_BY_ASPECT_RATIO, getString(R.string.screen_rotation_aspect_ratio))
            )

            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.screenRotation,
                titleId = R.string.screen_rotation_by,
            ) {
                config.screenRotation = it as Int
                binding.settingsScreenRotation.text = getScreenRotationText()
            }
        }
    }

    private fun getScreenRotationText() = getString(
        when (config.screenRotation) {
            ROTATE_BY_SYSTEM_SETTING -> R.string.screen_rotation_system_setting
            ROTATE_BY_DEVICE_ROTATION -> R.string.screen_rotation_device_rotation
            else -> R.string.screen_rotation_aspect_ratio
        }
    )
}
