package org.fossify.gallery.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.*
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivitySettingsBinding
import org.fossify.gallery.dialogs.*
import org.fossify.gallery.extensions.*
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.AlbumCover
import org.fossify.gallery.dialogs.ChangeLanguageDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val PICK_IMPORT_SOURCE_INTENT = 1
        private const val SELECT_EXPORT_FAVORITES_FILE_INTENT = 2
        private const val SELECT_IMPORT_FAVORITES_FILE_INTENT = 3
        private const val ROW_DECORATION_TAG = "settings_row_decoration"

        /** Rows that perform an immediate action instead of opening a sub-screen. */
        private val ACTION_ROW_IDS = setOf(
            R.id.settings_create_vault_holder,
            R.id.settings_export_vault_holder,
            R.id.settings_sync_vault_holder,
            R.id.settings_empty_vault_holder,
            R.id.settings_empty_recycle_bin_holder,
            R.id.settings_clear_cache_holder,
            R.id.settings_export_favorites_holder,
            R.id.settings_import_favorites_holder,
            R.id.settings_export_holder,
            R.id.settings_import_holder,
        )

        private val SECTION_LABEL_IDS = setOf(
            R.id.settings_color_customization_section_label,
            R.id.settings_general_settings_label,
            R.id.settings_videos_label,
            R.id.settings_thumbnails_label,
            R.id.settings_fullscreen_media_label,
            R.id.settings_security_label,
            R.id.settings_file_operations_label,
            R.id.settings_recycle_bin_label,
            R.id.settings_migrating_label,
            R.id.settings_vault_subheading,
            R.id.settings_fullscreen_subheading,
            R.id.settings_deep_zoom_subheading,
        )

        private val SECTION_ICONS = listOf(
            R.drawable.ic_section_appearance,
            R.drawable.ic_section_application,
            R.drawable.ic_section_video,
            R.drawable.ic_section_thumbnails,
            R.drawable.ic_section_viewer,
            R.drawable.ic_section_security,
            R.drawable.ic_section_files,
            R.drawable.ic_section_recycle,
            R.drawable.ic_section_migration,
        )
    }

    private var mRecycleBinContentSize = 0L
    private var activeSettingsSearch = ""
    private val originalSettingsVisibility = mutableMapOf<View, Int>()
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.settingsAppbar),
            padBottomSystem = listOf(binding.settingsNestedScrollview)
        )
        setupSettingsSearch()
    }

    override fun onResume() {
        super.onResume()
        // Keep the top bar in the normal background color. The commons scroll listener
        // would animate it to the primary color on the first downward scroll.
        setupTopAppBar(
            binding.settingsAppbar,
            NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor()
        )
        setupSettingItems()
        registerVaultSectionScrollRecheck()
    }

    override fun onPause() {
        super.onPause()
        unregisterVaultSectionScrollRecheck()
    }

    private fun setupSettingItems() {
        setupCustomizeColors()
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


        setupExcludedItemPasswordProtection()
        setupAppPasswordProtection()
        setupFileDeletionPasswordProtection()
        setupVaultSettings()
        setupConflictHandlingMode()
        setupSkippedEncryptFilesMode()
        setupSkippedDecryptFilesMode()
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
        setupEnablePullToRefresh()
        setupAllowZoomingImages()
        setupShowHighestQuality()
        setupAllowOneToOneZoom()
        setupAllowInstantChange()
        setupShowExtendedDetails()
        setupHideExtendedDetails()
        setupManageExtendedDetails()
        setupSkipDeleteConfirmation()
        setupDeleteOriginalFromFavoritesAndVirtualAlbums()
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

        val mainLabels = listOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsVideosLabel,
            binding.settingsThumbnailsLabel,
            binding.settingsFullscreenMediaLabel,
            binding.settingsSecurityLabel,
            binding.settingsFileOperationsLabel,
            binding.settingsRecycleBinLabel,
            binding.settingsMigratingLabel
        )
        mainLabels.zip(SECTION_ICONS).forEach { (label, iconRes) ->
            label.setTextColor(getProperPrimaryColor())
            label.isAllCaps = true
            label.textSize = 13f
            label.letterSpacing = 0.08f
            label.setTypeface(null, android.graphics.Typeface.BOLD)
            val density = resources.displayMetrics
            val end = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.settings_label_start_margin)
            val top = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, density).toInt()
            val bottom = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, density).toInt()
            // SettingsSectionLabelStyle already applies layout_marginStart; keep start padding at 0 so the icon aligns with row labels.
            label.setPaddingRelative(0, top, end, bottom)
            label.compoundDrawablePadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, density,
            ).toInt()
            ContextCompat.getDrawable(this, iconRes)?.mutate()?.apply {
                setTint(getProperPrimaryColor())
                label.setCompoundDrawablesRelativeWithIntrinsicBounds(this, null, null, null)
            }
        }

        listOf(
            binding.settingsVaultSubheading,
            binding.settingsFullscreenSubheading,
            binding.settingsDeepZoomSubheading,
        ).forEach {
            it.setTextColor(ColorUtils.setAlphaComponent(getProperTextColor(), 170))
            it.setTypeface(null, android.graphics.Typeface.BOLD)
        }
        refreshRowDecorations()
        applySettingsSearch()
    }

    private fun setupSettingsSearch() {
        binding.settingsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                activeSettingsSearch = s?.toString()?.trim().orEmpty()
                applySettingsSearch()
            }
        })
        binding.settingsSearch.setBackgroundColor(resolveCalypsoSearchBarFillColor(this))
        binding.settingsSearch.setTextColor(getProperTextColor())
        binding.settingsSearch.setHintTextColor(
            ColorUtils.blendARGB(getProperTextColor(), getProperBackgroundColor(), 0.45f),
        )
        binding.settingsSearch.compoundDrawablesRelative[0]?.setTint(getProperPrimaryColor())
    }

    private fun viewText(view: View): String {
        val result = StringBuilder()
        fun collect(current: View) {
            if (current is TextView) {
                result.append(current.text).append(' ')
            }
            if (current is ViewGroup) {
                for (i in 0 until current.childCount) {
                    collect(current.getChildAt(i))
                }
            }
        }
        collect(view)
        return result.toString()
    }

    /** Filters settings rows by their visible labels; section headers follow their rows. */
    private fun applySettingsSearch() {
        val holder = binding.settingsHolder
        val children = (0 until holder.childCount).map { holder.getChildAt(it) }
        if (activeSettingsSearch.isEmpty()) {
            originalSettingsVisibility.forEach { (view, visibility) -> view.visibility = visibility }
            originalSettingsVisibility.clear()
            return
        }
        if (originalSettingsVisibility.isEmpty()) {
            children.forEach { originalSettingsVisibility[it] = it.visibility }
        }
        val query = activeSettingsSearch.lowercase()
        val isLabel = { view: View -> view is TextView && view.id in SECTION_LABEL_IDS }
        val isRow = { view: View ->
            view is ViewGroup && view.id != View.NO_ID &&
                resources.getResourceEntryName(view.id).let { it.startsWith("settings_") && it.endsWith("_holder") }
        }

        children.forEach { child ->
            if (child === binding.settingsSearch) {
                return@forEach
            }
            if (isRow(child)) {
                child.isVisible = viewText(child).lowercase().contains(query) &&
                    originalSettingsVisibility[child] == View.VISIBLE
            }
        }
        for (index in children.indices) {
            val label = children[index]
            if (!isLabel(label)) {
                continue
            }
            var hasVisibleRow = false
            for (nextIndex in index + 1 until children.size) {
                val next = children[nextIndex]
                if (isLabel(next)) {
                    break
                }
                if (isRow(next) && next.isVisible) {
                    hasVisibleRow = true
                    break
                }
            }
            label.isVisible = hasVisibleRow && originalSettingsVisibility[label] == View.VISIBLE
        }
        // Dividers would be orphaned during filtering; section headers carry the grouping.
        children.filterIsInstance<ImageView>().forEach { it.isVisible = false }
    }

    /**
     * Fork-specific settings row language: a chevron on rows that actually open a sub-screen.
     * Switch rows and immediate actions keep their own affordance so the chevron stays truthful.
     * Section separation is handled by the divider views declared in the layout.
     */
    private fun refreshRowDecorations() {
        val holder = binding.settingsHolder
        for (i in holder.childCount - 1 downTo 0) {
            val child = holder.getChildAt(i)
            if (child.tag == ROW_DECORATION_TAG) {
                holder.removeViewAt(i)
            } else if (child is ViewGroup) {
                for (j in child.childCount - 1 downTo 0) {
                    if (child.getChildAt(j).tag == ROW_DECORATION_TAG) {
                        child.removeViewAt(j)
                    }
                }
            }
        }

        val palette = AppearanceManager.resolve(this, config)
        val visibleChildren = (0 until holder.childCount)
            .map { holder.getChildAt(it) }
            .filter { it.isVisible && it.tag != ROW_DECORATION_TAG }
        val isRow = { view: View ->
            view is ViewGroup && view.id != View.NO_ID &&
                resources.getResourceEntryName(view.id).let { it.startsWith("settings_") && it.endsWith("_holder") }
        }

        for (current in visibleChildren) {
            if (!isRow(current)) {
                continue
            }
            val row = current as ViewGroup
            if (!hasSwitch(row) && row.id !in ACTION_ROW_IDS) {
                row.addView(createChevron(row, palette))
            }
        }
    }

    private fun hasSwitch(view: ViewGroup): Boolean {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is CompoundButton || (child is ViewGroup && hasSwitch(child))) {
                return true
            }
        }
        return false
    }

    private fun createChevron(row: ViewGroup, palette: org.fossify.gallery.helpers.AppearancePalette): ImageView {
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, resources.displayMetrics).toInt()
        val tint = ColorStateList.valueOf(ColorUtils.blendARGB(palette.text, palette.background, 0.4f))
        return AppCompatImageView(this).apply {
            tag = ROW_DECORATION_TAG
            setImageResource(R.drawable.ic_settings_chevron)
            imageTintList = tint
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Branch on the row's own type: its layoutParams describe how the row sits in the
            // outer LinearLayout, so inspecting those would always pick the wrong LayoutParams
            // class and drop the chevron at the top-left corner of the row.
            layoutParams = if (row is ConstraintLayout) {
                ConstraintLayout.LayoutParams(size, size).apply {
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    rightMargin = margin
                }
            } else {
                RelativeLayout.LayoutParams(size, size).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                    rightMargin = margin
                }
            }
        }
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

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
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

            RadioGroupDialog(this@SettingsActivity, items, config.fileLoadingPriority) {
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

    private fun toggleHiddenItems() {
        binding.settingsShowHiddenItems.toggle()
        config.showHiddenMedia = binding.settingsShowHiddenItems.isChecked
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
                checkedItemId = config.homeScreenViewOnLaunch
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
                checkedItemId = config.videoPlayerType
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
                checkedItemId = config.videoDoubleTapFastForward
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

    private fun doesVaultExist(): Boolean {
        return CryptoManager.verifyVaultOnDisk(this)
    }

    private var isVaultSectionNearViewport = false

    // Use a ViewTreeObserver listener: setOnScrollChangeListener() is intentionally left free
    // (setting up the material scroll listener would tint the top bar on scroll).
    private val vaultSectionScrollListener = android.view.ViewTreeObserver.OnScrollChangedListener {
        // The section header was merged into "Security & encrypted vault"; the always-visible
        // vault location row is now the anchor for the deferred on-disk vault check.
        val anchor = binding.settingsVaultLocationHolder
        val scroll = binding.settingsNestedScrollview
        if (!anchor.isShown) {
            isVaultSectionNearViewport = false
            return@OnScrollChangedListener
        }

        val anchorLoc = IntArray(2)
        val scrollLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        scroll.getLocationOnScreen(scrollLoc)

        val lookAhead = scroll.height / 2
        val labelTop = anchorLoc[1]
        val labelBottom = anchorLoc[1] + anchor.height
        val near = labelTop < scrollLoc[1] + scroll.height + lookAhead && labelBottom > scrollLoc[1] - lookAhead

        if (near && !isVaultSectionNearViewport) {
            // The "Encrypted vault" section is about to appear: check the disk before it is drawn.
            scroll.post {
                setupVaultSettings()
                refreshRowDecorations()
            }
        }
        isVaultSectionNearViewport = near
    }

    private fun registerVaultSectionScrollRecheck() {
        isVaultSectionNearViewport = false
        val observer = binding.settingsNestedScrollview.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnScrollChangedListener(vaultSectionScrollListener)
            observer.addOnScrollChangedListener(vaultSectionScrollListener)
        }
    }

    private fun unregisterVaultSectionScrollRecheck() {
        val observer = binding.settingsNestedScrollview.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnScrollChangedListener(vaultSectionScrollListener)
        }
    }

    private fun setupVaultSettings() {
        val vaultExists = doesVaultExist()
        val hasStoragePermission = !(isRPlus() && !isExternalStorageManager())


        // 0) Kasa konumu (depolama izni varsa her zaman gösterilir)
        binding.settingsVaultLocationHolder.beVisibleIf(hasStoragePermission)

        // 1) Şifreli kasa oluştur (bu sadece şifreli kasa oluşturulmamış ise gösterilir)
        binding.settingsCreateVaultHolder.beVisibleIf(hasStoragePermission && !vaultExists)

        // 1b) Harici diskten kasayı eşitle
        binding.settingsSyncVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 2) Şifreli kasa şifresini değiştir
        binding.settingsChangeVaultPasswordHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 3) Şifreli kasaya biyometrik ile giriş (sadece bu kaydırılabilir)
        binding.settingsVaultBiometricHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 4) Gizli kasada ekran görüntüsü almayı yasakla
        binding.settingsBlockVaultScreenshotsHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 5) Şifreli kasayı boşalt
        binding.settingsEmptyVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 6) Şifreli kasayı dışa aktar
        binding.settingsExportVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

        setupArgon2Profile()
        setupVaultLocation()
        setupCreateVault()
        setupExportVault()
        setupSyncVault()
        setupChangeVaultPassword()
        setupVaultBiometric()
        setupBlockVaultScreenshots()
        setupEmptyVault()
    }

    /**
     * Vault-wide switches and the destructive "empty vault" action always re-verify the password, even when the
     * vault is already unlocked because the person is viewing an encrypted album. The ordinary vault-session state
     * is intentionally ignored here.
     */
    private fun requireVaultPassword(onAuthorized: () -> Unit) {
        if (!doesVaultExist()) return
        val currentType = CryptoManager.getVaultProtectionType(this)
            ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
        VaultSecurityDialog(this, config.hiddenPasswordHash, currentType, minimalist = false) { _, _, success ->
            if (success) onAuthorized()
        }
    }

    private fun setupBlockVaultScreenshots() {
        binding.settingsBlockVaultScreenshots.isChecked = config.blockVaultScreenshots
        binding.settingsBlockVaultScreenshotsHolder.setOnClickListener {
            requireVaultPassword {
                binding.settingsBlockVaultScreenshots.toggle()
                config.blockVaultScreenshots = binding.settingsBlockVaultScreenshots.isChecked
            }
        }
    }

    private fun setupArgon2Profile() {
        fun refreshSummary() {
            binding.settingsArgon2ProfileValue.text =
                org.fossify.gallery.dialogs.BruteForceDialog.summary(this)
        }
        refreshSummary()
        if (config.cachedStdArgon2Ms <= 0) {
            ensureBackgroundThread {
                CryptoManager.measureStandardArgon2Ms(this)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        refreshSummary()
                    }
                }
            }
        }

        binding.settingsArgon2ProfileHolder.setOnClickListener {
            val prevTargetMs = config.unlockTargetMs
            val prevMemKb = config.lastCalibratedMemoryKb
            val prevIter = config.lastCalibratedIterations
            val prevClamped = config.argon2CalibrationClamped

            org.fossify.gallery.dialogs.BruteForceDialog.show(this) {
                refreshSummary()
                if (doesVaultExist()) {
                    val currentType = CryptoManager.getVaultProtectionType(this)
                        ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) {
                            PROTECTION_PASSWORD
                        } else {
                            PROTECTION_PIN
                        }
                    VaultSecurityDialog(
                        activity = this,
                        requiredHash = config.hiddenPasswordHash,
                        showTabIndex = currentType,
                        minimalist = false,
                        allowBiometrics = false,
                        rewrapKdfOnUnlock = true
                    ) { _, _, success ->
                        if (success) {
                            VaultSessionManager.lockVaultIfOutsideSession(this)
                            refreshSummary()
                            toast(R.string.kdf_settings_updated_successfully)
                        } else {
                            config.unlockTargetMs = prevTargetMs
                            config.lastCalibratedMemoryKb = prevMemKb
                            config.lastCalibratedIterations = prevIter
                            config.argon2CalibrationClamped = prevClamped
                            refreshSummary()
                        }
                    }
                }
            }
        }
    }

    private fun setupVaultLocation() {
        binding.settingsVaultLocation.text = when {
            CryptoManager.isConfiguredVaultTemporarilyUnavailable(this) -> {
                getString(R.string.vault_location_unavailable_summary, humanizePath(config.vaultRootPath))
            }
            CryptoManager.isConfiguredCustomVaultPath(this) -> {
                humanizePath(config.vaultRootPath)
            }
            else -> {
                getString(R.string.vault_location_standard_summary)
            }
        }

        binding.settingsVaultLocationHolder.setOnClickListener {
            val hasConfiguredVault = doesVaultExist() || CryptoManager.isConfiguredVaultTemporarilyUnavailable(this)
            promptVaultSetupChoice(
                confirmBeforeCreatingNew = hasConfiguredVault,
                onCreated = {
                    setupVaultSettings()
                },
                onCancelled = {
                    setupVaultSettings()
                }
            )
        }
    }

    private fun setupCreateVault() {
        binding.settingsCreateVaultHolder.setOnClickListener {
            if (doesVaultExist()) return@setOnClickListener
            promptCreateVault(
                onCreated = {
                    VaultSessionManager.lockVaultIfOutsideSession(this)
                    setupVaultSettings()
                },
                onCancelled = {
                    VaultSessionManager.lockVaultIfOutsideSession(this)
                    setupVaultSettings()
                }
            )
        }
    }

    private fun setupExportVault() {
        binding.settingsExportVaultHolder.setOnClickListener {
            if (!doesVaultExist()) return@setOnClickListener

            val dialogBinding = org.fossify.gallery.databinding.DialogExportVaultBinding.inflate(layoutInflater)
            dialogBinding.exportVaultOnlyHeaderRadio.isChecked = true
            dialogBinding.exportVaultEntireFolderRadio.isChecked = false

            val selectOnlyHeader = {
                dialogBinding.exportVaultOnlyHeaderRadio.isChecked = true
                dialogBinding.exportVaultEntireFolderRadio.isChecked = false
            }
            val selectEntireFolder = {
                dialogBinding.exportVaultOnlyHeaderRadio.isChecked = false
                dialogBinding.exportVaultEntireFolderRadio.isChecked = true
            }

            dialogBinding.exportVaultOnlyHeaderRow.setOnClickListener { selectOnlyHeader() }
            dialogBinding.exportVaultOnlyHeaderRadio.setOnClickListener { selectOnlyHeader() }
            dialogBinding.exportVaultEntireFolderRadio.setOnClickListener { selectEntireFolder() }

            bindSettingInfoIcon(
                iconView = dialogBinding.exportVaultOnlyHeaderInfo,
                titleResId = R.string.export_vault_only_header,
                messageResId = R.string.export_vault_only_header_info
            )

            getAlertDialogBuilder()
                .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                    promptExportEncryptedVault(
                        onlyVaultHeader = dialogBinding.exportVaultOnlyHeaderRadio.isChecked
                    ) {
                        setupVaultSettings()
                    }
                }
                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                .apply {
                    setupDialogStuff(dialogBinding.root, this, R.string.export_encrypted_vault)
                }
        }
    }

    private fun setupSyncVault() {
        bindSettingInfoIcon(
            iconView = binding.settingsSyncVaultInfo,
            titleResId = R.string.sync_vault_from_external,
            messageResId = R.string.sync_vault_select_source_prompt
        )
        binding.settingsSyncVaultHolder.setOnClickListener {
            if (!doesVaultExist()) return@setOnClickListener
            // While the person is inside an encrypted album the key already sits in RAM and stays there until they
            // leave that album, so no extra prompt is needed. Outside that session every visit must re-enter the
            // password first, and the key is wiped again when the flow ends.
            val inEncryptedAlbum = config.temporarilyShowHidden
            promptSyncVaultFromExternal(
                forcePasswordPrompt = !inEncryptedAlbum,
                onSynced = { setupVaultSettings() }
            )
        }
    }

    private fun setupChangeVaultPassword() {
        binding.settingsChangeVaultPasswordHolder.setOnClickListener {
            if (!doesVaultExist()) return@setOnClickListener

            val currentType = CryptoManager.getVaultProtectionType(this)
                ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
            VaultSecurityDialog(
                activity = this,
                requiredHash = config.hiddenPasswordHash,
                showTabIndex = currentType,
                minimalist = false,
                allowBiometrics = false
            ) { _, _, authSuccess ->
                if (authSuccess) {
                    promptChangeVaultPassword()
                }
            }
        }
    }

    private fun promptChangeVaultPassword() {
        VaultSecurityDialog(
            activity = this,
            requiredHash = "",
            showTabIndex = SHOW_ALL_TABS,
            minimalist = false,
            isChangePasswordMode = true,
            onCancelCallback = {
                VaultSessionManager.lockVaultIfOutsideSession(this)
            }
        ) { _, _, changeSuccess ->
            VaultSessionManager.lockVaultIfOutsideSession(this)
            if (changeSuccess) {
                updateVaultBiometricSwitch()
                toast(R.string.password_changed_successfully)
            }
        }
    }

    private fun setupVaultBiometric() {
        binding.settingsVaultBiometricHolder.beGoneIf(isRPlus() && !isExternalStorageManager())
        updateVaultBiometricSwitch()
        binding.settingsVaultBiometricHolder.setOnClickListener {
            if (!doesVaultExist()) {
                toast(R.string.enable_hidden_protection_first_prompt)
                return@setOnClickListener
            }

            if (config.isVaultBiometricEnabled) {
                CryptoManager.clearBiometricKey(this)
                config.temporarilyShowHidden = false
                config.temporarilyShowVaultExcluded = false
                CryptoManager.lockVault()
                CryptoManager.clearAppImageCaches(this)
                VaultSessionManager.onVaultExited()
                applyVaultScreenProtection(false)
                updateVaultBiometricSwitch()
            } else {
                if (!CryptoManager.isBiometricHardwareAvailable(this) || CryptoManager.getOrCreateBiometricKey() == null) {
                    toast(R.string.no_biometric_hardware_or_enrolled)
                    return@setOnClickListener
                }

                val wasVaultUnlocked = CryptoManager.isVaultUnlocked
                val tabToShow = CryptoManager.getVaultProtectionType(this) ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
                VaultSecurityDialog(this, config.hiddenPasswordHash, tabToShow, minimalist = false) { _, _, success ->
                    if (success) {
                        showBiometricEnrollmentPrompt(lockVaultAfter = !wasVaultUnlocked && !config.temporarilyShowHidden)
                    }
                }
            }
        }
    }

    private fun updateVaultBiometricSwitch() {
        val vaultExists = doesVaultExist()
        binding.settingsVaultBiometric.isEnabled = vaultExists
        binding.settingsVaultBiometricHolder.alpha = if (vaultExists) 1.0f else 0.4f
        binding.settingsVaultBiometric.isChecked = vaultExists && config.isVaultBiometricEnabled
    }

    private fun showBiometricEnrollmentPrompt(lockVaultAfter: Boolean) {
        val cipher = CryptoManager.getBiometricEncryptCipher()
        if (cipher == null) {
            if (lockVaultAfter) {
                CryptoManager.lockVault()
                applyVaultScreenProtection(false)
            }
            toast(org.fossify.commons.R.string.an_error_occurred)
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_access_to_encrypted_vault))
            .setSubtitle(getString(R.string.biometric_setup_prompt))
            .setNegativeButtonText(getString(org.fossify.commons.R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authCipher = result.cryptoObject?.cipher
                    if (authCipher != null && CryptoManager.completeBiometricSetup(this@SettingsActivity, authCipher)) {
                        updateVaultBiometricSwitch()
                        toast(org.fossify.commons.R.string.fingerprint_setup_successfully)
                    } else {
                        CryptoManager.clearBiometricKey(this@SettingsActivity)
                        updateVaultBiometricSwitch()
                        toast(org.fossify.commons.R.string.an_error_occurred)
                    }
                    if (lockVaultAfter) {
                        CryptoManager.lockVault()
                        applyVaultScreenProtection(false)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    CryptoManager.clearBiometricKey(this@SettingsActivity)
                    if (lockVaultAfter) {
                        CryptoManager.lockVault()
                        applyVaultScreenProtection(false)
                    }
                    updateVaultBiometricSwitch()
                }
            }
        )
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    private fun setupEmptyVault() {
        binding.settingsEmptyVaultHolder.setOnClickListener {
            if (!doesVaultExist()) return@setOnClickListener

            requireVaultPassword {
                DeleteSecretsDialog(this, getString(R.string.empty_encrypted_vault_warning)) {
                    ensureBackgroundThread {
                        CryptoManager.deleteExistingVault(this)
                        runOnUiThread {
                            toast(R.string.encrypted_vault_emptied)
                            setupVaultSettings()
                        }
                    }
                }
            }
        }
    }

    private fun setupExcludedItemPasswordProtection() {
        binding.settingsExcludedItemPasswordProtectionHolder.beGoneIf(isRPlus() && !isExternalStorageManager())
        binding.settingsExcludedItemPasswordProtection.isChecked = config.isExcludedPasswordProtectionOn
        binding.settingsExcludedItemPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isExcludedPasswordProtectionOn) config.excludedProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.excludedPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isExcludedPasswordProtectionOn
                    binding.settingsExcludedItemPasswordProtection.isChecked = !hasPasswordProtection
                    config.isExcludedPasswordProtectionOn = !hasPasswordProtection
                    config.excludedPasswordHash = if (hasPasswordProtection) "" else hash
                    config.excludedProtectionType = type

                    if (config.isExcludedPasswordProtectionOn) {
                        val confirmationTextId = if (config.excludedProtectionType == PROTECTION_FINGERPRINT)
                            org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun setupAppPasswordProtection() {
        binding.settingsAppPasswordProtection.isChecked = config.isAppPasswordProtectionOn
        binding.settingsAppPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isAppPasswordProtectionOn) config.appProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.appPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isAppPasswordProtectionOn
                    binding.settingsAppPasswordProtection.isChecked = !hasPasswordProtection
                    config.isAppPasswordProtectionOn = !hasPasswordProtection
                    config.appPasswordHash = if (hasPasswordProtection) "" else hash
                    config.appProtectionType = type

                    if (config.isAppPasswordProtectionOn) {
                        val confirmationTextId = if (config.appProtectionType == PROTECTION_FINGERPRINT)
                            org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun setupFileDeletionPasswordProtection() {
        binding.settingsFileDeletionPasswordProtection.isChecked = config.isDeletePasswordProtectionOn
        binding.settingsFileDeletionPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isDeletePasswordProtectionOn) config.deleteProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.deletePasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isDeletePasswordProtectionOn
                    binding.settingsFileDeletionPasswordProtection.isChecked = !hasPasswordProtection
                    config.isDeletePasswordProtectionOn = !hasPasswordProtection
                    config.deletePasswordHash = if (hasPasswordProtection) "" else hash
                    config.deleteProtectionType = type

                    if (config.isDeletePasswordProtectionOn) {
                        val confirmationTextId = if (config.deleteProtectionType == PROTECTION_FINGERPRINT)
                            org.fossify.commons.R.string.fingerprint_setup_successfully else org.fossify.commons.R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, org.fossify.commons.R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun setupConflictHandlingMode() {
        binding.settingsConflictHandlingMode.text = getConflictHandlingModeText()
        binding.settingsConflictHandlingModeHolder.setOnClickListener {
            val dialogBinding = org.fossify.gallery.databinding.DialogConflictHandlingModeBinding.inflate(layoutInflater)
            when (config.conflictHandlingMode) {
                CONFLICT_HANDLING_SMART_DIALOG -> dialogBinding.conflictHandlingSmartDialog.isChecked = true
                CONFLICT_HANDLING_AUTO -> dialogBinding.conflictHandlingAuto.isChecked = true
                else -> dialogBinding.conflictHandlingStandardDialog.isChecked = true
            }
            dialogBinding.fastLargeDuplicateCheckCheckbox.isChecked = config.fastLargeDuplicateCheck
            bindSettingInfoIcon(
                iconView = dialogBinding.fastLargeDuplicateCheckInfo,
                titleResId = R.string.fast_large_duplicate_check_info_title,
                messageResId = R.string.fast_large_duplicate_check_info_message
            )

            val tieBreakRadios = listOf(
                dialogBinding.dateTieBreakKeepTarget,
                dialogBinding.dateTieBreakUseSource,
                dialogBinding.dateTieBreakAsk
            )
            when (config.dateTieBreakMode) {
                DATE_TIE_BREAK_USE_SOURCE -> dialogBinding.dateTieBreakUseSource.isChecked = true
                DATE_TIE_BREAK_ASK -> dialogBinding.dateTieBreakAsk.isChecked = true
                else -> dialogBinding.dateTieBreakKeepTarget.isChecked = true
            }
            bindSettingInfoIcon(
                iconView = dialogBinding.dateTieBreakAskInfo,
                titleResId = R.string.date_tie_break_ask_info_title,
                messageResId = R.string.date_tie_break_ask_info_message
            )

            fun updateDependentState() {
                val usesDuplicateCheck = !dialogBinding.conflictHandlingStandardDialog.isChecked
                dialogBinding.fastLargeDuplicateCheckCheckbox.isEnabled = usesDuplicateCheck
                dialogBinding.fastLargeDuplicateCheckRow.alpha = if (usesDuplicateCheck) 1f else 0.45f
                dialogBinding.fastLargeDuplicateCheckHint.alpha = if (usesDuplicateCheck) 0.75f else 0.35f

                dialogBinding.dateTieBreakLabel.alpha = if (usesDuplicateCheck) 1f else 0.45f
                tieBreakRadios.forEach { radio ->
                    radio.isEnabled = usesDuplicateCheck
                    radio.alpha = if (usesDuplicateCheck) 1f else 0.45f
                }
                dialogBinding.dateTieBreakAskInfo.alpha = if (usesDuplicateCheck) 1f else 0.45f
                dialogBinding.dateTieBreakHint.alpha = if (usesDuplicateCheck) 0.75f else 0.35f
            }
            updateDependentState()

            dialogBinding.conflictHandlingRadioGroup.setOnCheckedChangeListener { _, _ ->
                updateDependentState()
            }
            tieBreakRadios.forEach { radio ->
                radio.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        tieBreakRadios.filter { it !== radio }.forEach { it.isChecked = false }
                    }
                }
            }

            getAlertDialogBuilder()
                .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                    config.conflictHandlingMode = when {
                        dialogBinding.conflictHandlingSmartDialog.isChecked -> CONFLICT_HANDLING_SMART_DIALOG
                        dialogBinding.conflictHandlingAuto.isChecked -> CONFLICT_HANDLING_AUTO
                        else -> CONFLICT_HANDLING_STANDARD_DIALOG
                    }
                    config.fastLargeDuplicateCheck = dialogBinding.fastLargeDuplicateCheckCheckbox.isChecked
                    config.dateTieBreakMode = when {
                        dialogBinding.dateTieBreakAsk.isChecked -> DATE_TIE_BREAK_ASK
                        dialogBinding.dateTieBreakUseSource.isChecked -> DATE_TIE_BREAK_USE_SOURCE
                        else -> DATE_TIE_BREAK_KEEP_TARGET
                    }
                    binding.settingsConflictHandlingMode.text = getConflictHandlingModeText()
                }
                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                .apply {
                    setupDialogStuff(dialogBinding.root, this, R.string.conflict_handling_explanation_title)
                }
        }
    }

    private fun getConflictHandlingModeText() = getString(
        when (config.conflictHandlingMode) {
            CONFLICT_HANDLING_SMART_DIALOG -> R.string.conflict_handling_smart_dialog_summary
            CONFLICT_HANDLING_AUTO -> R.string.conflict_handling_auto_summary
            else -> R.string.conflict_handling_standard_dialog_summary
        }
    )

    private fun setupSkippedEncryptFilesMode() {
        setupSkippedFilesSetting(
            holderView = binding.settingsSkippedEncryptFilesHolder,
            valueView = binding.settingsSkippedEncryptFiles,
            isEncrypt = true
        )
    }

    private fun setupSkippedDecryptFilesMode() {
        setupSkippedFilesSetting(
            holderView = binding.settingsSkippedDecryptFilesHolder,
            valueView = binding.settingsSkippedDecryptFiles,
            isEncrypt = false
        )
    }

    private fun setupSkippedFilesSetting(
        holderView: android.view.View,
        valueView: MyTextView,
        isEncrypt: Boolean
    ) {
        valueView.text = getSkippedCryptoFilesModeText(isEncrypt)
        holderView.setOnClickListener {
            showSkippedCryptoFilesSettingsDialog(this, isEncrypt = isEncrypt) {
                valueView.text = getSkippedCryptoFilesModeText(isEncrypt)
            }
        }
    }

    private fun getSkippedCryptoFilesModeText(isEncrypt: Boolean): String {
        val mode = if (isEncrypt) config.skippedEncryptFilesMode else config.skippedDecryptFilesMode
        return getString(
            when (mode) {
                CRYPTO_SKIPPED_FILES_KEEP -> R.string.skipped_crypto_files_keep
                CRYPTO_SKIPPED_FILES_DELETE -> R.string.skipped_crypto_files_delete
                else -> R.string.skipped_crypto_files_ask
            }
        )
    }

    private fun setupAlbumCryptoConflictMode() {
        binding.settingsAlbumCryptoConflict.text = getAlbumCryptoConflictModeText()
        binding.settingsAlbumCryptoConflictHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ALBUM_CRYPTO_CONFLICT_ASK, getString(R.string.album_crypto_conflict_ask)),
                RadioItem(ALBUM_CRYPTO_CONFLICT_MERGE, getString(R.string.album_crypto_conflict_merge)),
                RadioItem(ALBUM_CRYPTO_CONFLICT_RENAME, getString(R.string.album_crypto_conflict_rename))
            )
            RadioGroupDialog(this@SettingsActivity, items, config.albumCryptoConflictMode) {
                config.albumCryptoConflictMode = it as Int
                binding.settingsAlbumCryptoConflict.text = getAlbumCryptoConflictModeText()
            }
        }
    }

    private fun getAlbumCryptoConflictModeText() = getString(
        when (config.albumCryptoConflictMode) {
            ALBUM_CRYPTO_CONFLICT_MERGE -> R.string.album_crypto_conflict_merge
            ALBUM_CRYPTO_CONFLICT_RENAME -> R.string.album_crypto_conflict_rename
            else -> R.string.album_crypto_conflict_ask
        }
    )

    private fun setupDeleteEmptyFolders() {
        binding.settingsDeleteEmptyFolders.isChecked = config.deleteEmptyFolders
        binding.settingsDeleteEmptyFoldersHolder.setOnClickListener {
            binding.settingsDeleteEmptyFolders.toggle()
            config.deleteEmptyFolders = binding.settingsDeleteEmptyFolders.isChecked
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

    private fun setupEnablePullToRefresh() {
        binding.settingsEnablePullToRefresh.isChecked = config.enablePullToRefresh
        binding.settingsEnablePullToRefreshHolder.setOnClickListener {
            binding.settingsEnablePullToRefresh.toggle()
            config.enablePullToRefresh = binding.settingsEnablePullToRefresh.isChecked
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

            RadioGroupDialog(this@SettingsActivity, items, config.screenRotation) {
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

    private fun setupUseRecycleBin() {
        updateRecycleBinButtons()
        binding.settingsUseRecycleBin.isChecked = config.useRecycleBin
        binding.settingsUseRecycleBinHolder.setOnClickListener {
            binding.settingsUseRecycleBin.toggle()
            config.useRecycleBin = binding.settingsUseRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun setupShowRecycleBin() {
        binding.settingsShowRecycleBin.isChecked = config.showRecycleBinAtFolders
        binding.settingsShowRecycleBinHolder.setOnClickListener {
            binding.settingsShowRecycleBin.toggle()
            config.showRecycleBinAtFolders = binding.settingsShowRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun setupShowRecycleBinLast() {
        binding.settingsShowRecycleBinLast.isChecked = config.showRecycleBinLast
        binding.settingsShowRecycleBinLastHolder.setOnClickListener {
            binding.settingsShowRecycleBinLast.toggle()
            config.showRecycleBinLast = binding.settingsShowRecycleBinLast.isChecked
            if (config.showRecycleBinLast) {
                config.removePinnedFolders(setOf(RECYCLE_BIN))
            }
        }
    }

    private fun updateRecycleBinButtons() {
        binding.settingsShowRecycleBinLastHolder.beVisibleIf(config.useRecycleBin && config.showRecycleBinAtFolders)
        binding.settingsEmptyRecycleBinHolder.beVisibleIf(config.useRecycleBin)
        binding.settingsShowRecycleBinHolder.beVisibleIf(config.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() {
        ensureBackgroundThread {
            try {
                val plainSize = mediaDB.getDeletedMedia().sumByLong { medium ->
                    val size = medium.size
                    if (size == 0L) {
                        val path = medium.path.removePrefix(RECYCLE_BIN).prependIndent(recycleBinPath)
                        File(path).length()
                    } else {
                        size
                    }
                }
                val vaultSize = if (CryptoManager.isVaultUnlocked) {
                    CryptoManager.getVaultRecycleBinSize(this)
                } else {
                    0L
                }
                mRecycleBinContentSize = plainSize + vaultSize
            } catch (ignored: Exception) {
            }

            runOnUiThread {
                binding.settingsEmptyRecycleBinSize.text = mRecycleBinContentSize.formatSize()
            }
        }

        binding.settingsEmptyRecycleBinHolder.setOnClickListener {
            if (mRecycleBinContentSize == 0L) {
                toast(org.fossify.commons.R.string.recycle_bin_empty)
            } else {
                showRecycleBinEmptyingDialog {
                    emptyTheRecycleBin()
                    if (CryptoManager.isVaultUnlocked) {
                        ensureBackgroundThread {
                            CryptoManager.emptyVaultRecycleBin(this)
                        }
                    }
                    mRecycleBinContentSize = 0L
                    binding.settingsEmptyRecycleBinSize.text = 0L.formatSize()
                }
            }
        }
    }

    private fun setupClearCache() {
        ensureBackgroundThread {
            val size = cacheDir.getProperSize(true).formatSize()
            runOnUiThread {
                binding.settingsClearCacheSize.text = size
            }
        }

        binding.settingsClearCacheHolder.setOnClickListener {
            ensureBackgroundThread {
                cacheDir.deleteRecursively()
                runOnUiThread {
                    binding.settingsClearCacheSize.text = cacheDir.getProperSize(true).formatSize()
                }
            }
        }
    }

    private fun setupExportFavorites() {
        binding.settingsExportFavoritesHolder.setOnClickListener {
            ensureBackgroundThread {
                val hasExportData = FavoritesAndVirtualAlbumsBackupManager.hasDataToExport(this)
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    if (!hasExportData) {
                        toast(org.fossify.commons.R.string.no_items_found)
                        return@runOnUiThread
                    }
                    startExportFavoritesFlow()
                }
            }
        }
    }

    private fun startExportFavoritesFlow() {
        if (isQPlus()) {
            ExportFavoritesDialog(this, getExportFavoritesFilename(), true) { _, filename ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, filename)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, SELECT_EXPORT_FAVORITES_FILE_INTENT)
                    } catch (_: ActivityNotFoundException) {
                        toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE) {
                if (it) {
                    ExportFavoritesDialog(this, getExportFavoritesFilename(), false) { path, _ ->
                        val file = File(path)
                        getFileOutputStream(file.toFileDirItem(this), true) { stream ->
                            exportFavoritesTo(stream)
                        }
                    }
                }
            }
        }
    }

    private fun exportFavoritesTo(outputStream: OutputStream?) {
        if (outputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        ensureBackgroundThread {
            try {
                val exported = FavoritesAndVirtualAlbumsBackupManager.exportToStream(this, outputStream)
                toast(
                    if (exported) {
                        org.fossify.commons.R.string.exporting_successful
                    } else {
                        org.fossify.commons.R.string.no_items_found
                    }
                )
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun getExportFavoritesFilename(): String {
        val appName = baseConfig.appId.removeSuffix(".debug").removePrefix("org.fossify.")
        return "$appName-favorites_${getCurrentFormattedDateTime()}"
    }

    private fun setupImportFavorites() {
        binding.settingsImportFavoritesHolder.setOnClickListener {
            if (isQPlus()) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "text/plain"
                        )
                    )
                    try {
                        startActivityForResult(this, SELECT_IMPORT_FAVORITES_FILE_INTENT)
                    } catch (_: ActivityNotFoundException) {
                        toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            } else {
                handlePermission(PERMISSION_READ_STORAGE) {
                    if (it) {
                        FilePickerDialog(this) {
                            ensureBackgroundThread {
                                importFavorites(File(it).inputStream())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importFavorites(inputStream: InputStream?) {
        if (inputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        ensureBackgroundThread {
            val bytes = try {
                inputStream.use { it.readBytes() }
            } catch (e: Exception) {
                showErrorToast(e)
                return@ensureBackgroundThread
            }

            val pkg = FavoritesAndVirtualAlbumsBackupManager.parseImportPackage(bytes)
            if (pkg.favorites.isEmpty() && pkg.virtualAlbums.isEmpty()) {
                toast(org.fossify.commons.R.string.no_entries_for_importing)
                return@ensureBackgroundThread
            }

            val shouldPromptConflict =
                pkg.favorites.isNotEmpty() && FavoritesAndVirtualAlbumsBackupManager.hasExistingFavorites(this)

            if (shouldPromptConflict) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    ImportFavoritesConflictDialog(
                        activity = this,
                        onConfirm = { mode -> applyFavoritesAndAlbumsImport(pkg, mode) }
                    )
                }
            } else {
                applyFavoritesAndAlbumsImport(pkg, FavoritesImportMode.MERGE)
            }
        }
    }

    private fun applyFavoritesAndAlbumsImport(pkg: ParsedImportPackage, favoritesMode: FavoritesImportMode) {
        ensureBackgroundThread {
            val result = FavoritesAndVirtualAlbumsBackupManager.applyImport(this, pkg, favoritesMode)
            val hasImportedAnything =
                result.processedFavoritesCount > 0 || result.importedVirtualAlbumsCount > 0
            toast(
                if (hasImportedAnything) {
                    org.fossify.commons.R.string.importing_successful
                } else {
                    org.fossify.commons.R.string.no_entries_for_importing
                }
            )
        }
    }

    private fun setupExportSettings() {
        binding.settingsExportHolder.setOnClickListener {
            val configItems = LinkedHashMap<String, Any>().apply {
                put(TEXT_COLOR, config.textColor)
                put(BACKGROUND_COLOR, config.backgroundColor)
                put(PRIMARY_COLOR, config.primaryColor)
                put(ACCENT_COLOR, config.accentColor)
                put(APP_ICON_COLOR, config.appIconColor)
                put(USE_ENGLISH, config.useEnglish)
                put(WAS_USE_ENGLISH_TOGGLED, config.wasUseEnglishToggled)
                put(WIDGET_BG_COLOR, config.widgetBgColor)
                put(WIDGET_TEXT_COLOR, config.widgetTextColor)
                put(DATE_FORMAT, config.dateFormat)
                put(USE_24_HOUR_FORMAT, config.use24HourFormat)
                put(INCLUDED_FOLDERS, TextUtils.join(",", config.includedFolders))
                put(EXCLUDED_FOLDERS, TextUtils.join(",", config.excludedFolders))
                put(SHOW_HIDDEN_MEDIA, config.showHiddenMedia)
                put(BLOCK_VAULT_SCREENSHOTS, config.blockVaultScreenshots)
                put(UNLOCK_TARGET_MS, config.unlockTargetMs)
                put(ARGON2_CALIBRATION_CLAMPED, config.argon2CalibrationClamped)
                put(LAST_CALIBRATED_MEMORY_KB, config.lastCalibratedMemoryKb)
                put(LAST_CALIBRATED_ITERATIONS, config.lastCalibratedIterations)
                put(FILE_LOADING_PRIORITY, config.fileLoadingPriority)
                put(AUTOPLAY_VIDEOS, config.autoplayVideos)
                put(REMEMBER_LAST_VIDEO_POSITION, config.rememberLastVideoPosition)
                put(LOOP_VIDEOS, config.loopVideos)
                put(GESTURE_VIDEO_PLAYER, config.gestureVideoPlayer)
                put(VIDEO_PLAYER_TYPE, config.videoPlayerType)
                put(VIDEO_DOUBLE_TAP_FAST_FORWARD, config.videoDoubleTapFastForward)
                put(ALLOW_VIDEO_GESTURES, config.allowVideoGestures)
                put(ANIMATE_GIFS, config.animateGifs)
                put(CROP_THUMBNAILS, config.cropThumbnails)
                put(SHOW_THUMBNAIL_VIDEO_DURATION, config.showThumbnailVideoDuration)
                put(SHOW_THUMBNAIL_FILE_TYPES, config.showThumbnailFileTypes)
                put(MARK_FAVORITE_ITEMS, config.markFavoriteItems)
                put(SCROLL_HORIZONTALLY, config.scrollHorizontally)
                put(ENABLE_PULL_TO_REFRESH, config.enablePullToRefresh)
                put(MAX_BRIGHTNESS, config.maxBrightness)
                put(ULTRA_HDR_RENDERING, config.ultraHdrRendering)
                put(BLACK_BACKGROUND, config.blackBackground)
                put(HIDE_SYSTEM_UI, config.hideSystemUI)
                put(ALLOW_INSTANT_CHANGE, config.allowInstantChange)
                put(KEEP_SCREEN_ON, config.keepScreenOn)
                put(ALLOW_PHOTO_GESTURES, config.allowPhotoGestures)
                put(ALLOW_DOWN_GESTURE, config.allowDownGesture)
                put(ALLOW_ROTATING_WITH_GESTURES, config.allowRotatingWithGestures)
                put(SHOW_NOTCH, config.showNotch)
                put(SCREEN_ROTATION, config.screenRotation)
                put(ALLOW_ZOOMING_IMAGES, config.allowZoomingImages)
                put(SHOW_HIGHEST_QUALITY, config.showHighestQuality)
                put(ALLOW_ONE_TO_ONE_ZOOM, config.allowOneToOneZoom)
                put(SHOW_EXTENDED_DETAILS, config.showExtendedDetails)
                put(HIDE_EXTENDED_DETAILS, config.hideExtendedDetails)
                put(EXTENDED_DETAILS, config.extendedDetails)
                put(DELETE_EMPTY_FOLDERS, config.deleteEmptyFolders)
                put(CONFLICT_HANDLING_MODE, config.conflictHandlingMode)
                put(FAST_LARGE_DUPLICATE_CHECK, config.fastLargeDuplicateCheck)
                put(DATE_TIE_BREAK_MODE, config.dateTieBreakMode)
                put(ALBUM_CRYPTO_CONFLICT_MODE, config.albumCryptoConflictMode)
                put(SKIPPED_ENCRYPT_FILES_MODE, config.skippedEncryptFilesMode)
                put(SKIPPED_DECRYPT_FILES_MODE, config.skippedDecryptFilesMode)
                put(KEEP_LAST_MODIFIED, config.keepLastModified)
                put(SKIP_DELETE_CONFIRMATION, config.skipDeleteConfirmation)
                put(DELETE_ORIGINAL_FROM_FAVORITES_AND_VIRTUAL_ALBUMS, config.deleteOriginalFromFavoritesAndVirtualAlbums)
                put(ALLOW_BATCH_RENAMING, config.allowBatchRenaming)
                put(BOTTOM_ACTIONS, config.bottomActions)
                put(VISIBLE_BOTTOM_ACTIONS, config.visibleBottomActions)
                put(USE_RECYCLE_BIN, config.useRecycleBin)
                put(SHOW_RECYCLE_BIN_AT_FOLDERS, config.showRecycleBinAtFolders)
                put(SHOW_RECYCLE_BIN_LAST, config.showRecycleBinLast)
                put(SORT_ORDER, config.sorting)
                put(DIRECTORY_SORT_ORDER, config.directorySorting)
                put(FILES_SORT_ORDER, config.filesSorting)
                put(GROUP_BY, config.groupBy)
                put(GROUP_DIRECT_SUBFOLDERS, config.groupDirectSubfolders)
                put(PINNED_FOLDERS, TextUtils.join(",", config.pinnedFolders))
                put(DISPLAY_FILE_NAMES, config.displayFileNames)
                put(FILTER_MEDIA, config.filterMedia)
                put(DIR_COLUMN_CNT, config.dirColumnCnt)
                put(MEDIA_COLUMN_CNT, config.mediaColumnCnt)
                put(SHOW_ALL, config.showAll)
                put(SHOW_WIDGET_FOLDER_NAME, config.showWidgetFolderName)
                put(VIEW_TYPE_FILES, config.viewTypeFiles)
                put(VIEW_TYPE_FOLDERS, config.viewTypeFolders)
                put(SLIDESHOW_INTERVAL, config.slideshowInterval)
                put(SLIDESHOW_INCLUDE_VIDEOS, config.slideshowIncludeVideos)
                put(SLIDESHOW_INCLUDE_GIFS, config.slideshowIncludeGIFs)
                put(SLIDESHOW_RANDOM_ORDER, config.slideshowRandomOrder)
                put(SLIDESHOW_MOVE_BACKWARDS, config.slideshowMoveBackwards)
                put(SLIDESHOW_LOOP, config.loopSlideshow)
                put(LAST_EDITOR_CROP_ASPECT_RATIO, config.lastEditorCropAspectRatio)
                put(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X, config.lastEditorCropOtherAspectRatioX)
                put(LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y, config.lastEditorCropOtherAspectRatioY)
                put(LAST_CONFLICT_RESOLUTION, config.lastConflictResolution)
                put(LAST_CONFLICT_APPLY_TO_ALL, config.lastConflictApplyToAll)
                put(EDITOR_BRUSH_COLOR, config.editorBrushColor)
                put(EDITOR_BRUSH_HARDNESS, config.editorBrushHardness)
                put(EDITOR_BRUSH_SIZE, config.editorBrushSize)
                put(ALBUM_COVERS, config.albumCovers)
                put(FOLDER_THUMBNAIL_STYLE, config.folderStyle)
                put(FOLDER_MEDIA_COUNT, config.showFolderMediaCount)
                put(LIMIT_FOLDER_TITLE, config.limitFolderTitle)
                put(SHOW_VIRTUAL_ALBUM_ICON, config.showVirtualAlbumIcon)
                put(SHOW_FAVORITES_ALBUM_ICON, config.showFavoritesAlbumIcon)
                put(SHOW_EXCLUDED_ALBUM_ICON, config.showExcludedAlbumIcon)
                put(SHOW_HIDDEN_ALBUM_ICON, config.showHiddenAlbumIcon)
                put(THUMBNAIL_SPACING, config.thumbnailSpacing)
                put(FILE_ROUNDED_CORNERS, config.fileRoundedCorners)
                put(HOME_SCREEN_VIEW_ON_LAUNCH, config.homeScreenViewOnLaunch)
            }

            exportSettings(configItems)
        }
    }

    private fun setupImportSettings() {
        binding.settingsImportHolder.setOnClickListener {
            if (isQPlus()) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                }
            } else {
                handlePermission(PERMISSION_READ_STORAGE) {
                    if (it) {
                        FilePickerDialog(this) {
                            ensureBackgroundThread {
                                parseFile(File(it).inputStream())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseFile(inputStream: InputStream?) {
        if (inputStream == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        var importedItems = 0
        val configValues = LinkedHashMap<String, Any>()
        inputStream.bufferedReader().use {
            while (true) {
                try {
                    val line = it.readLine() ?: break
                    val split = line.split("=".toRegex(), 2)
                    if (split.size == 2) {
                        configValues[split[0]] = split[1]
                    }
                    importedItems++
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        }

        for ((key, value) in configValues) {
            when (key) {
                TEXT_COLOR -> config.textColor = value.toInt()
                BACKGROUND_COLOR -> config.backgroundColor = value.toInt()
                PRIMARY_COLOR -> config.primaryColor = value.toInt()
                ACCENT_COLOR -> config.accentColor = value.toInt()
                APP_ICON_COLOR -> {
                    if (getAppIconColors().contains(value.toInt())) {
                        config.appIconColor = value.toInt()
                        checkAppIconColor()
                    }
                }

                USE_ENGLISH -> config.useEnglish = value.toBoolean()
                WAS_USE_ENGLISH_TOGGLED -> config.wasUseEnglishToggled = value.toBoolean()
                WIDGET_BG_COLOR -> config.widgetBgColor = value.toInt()
                WIDGET_TEXT_COLOR -> config.widgetTextColor = value.toInt()
                DATE_FORMAT -> config.dateFormat = value.toString()
                USE_24_HOUR_FORMAT -> config.use24HourFormat = value.toBoolean()
                INCLUDED_FOLDERS -> config.addIncludedFolders(value.toStringSet())
                EXCLUDED_FOLDERS -> config.addExcludedFolders(value.toStringSet())
                SHOW_HIDDEN_MEDIA -> config.showHiddenMedia = value.toBoolean()
                BLOCK_VAULT_SCREENSHOTS -> config.blockVaultScreenshots = value.toBoolean()
                UNLOCK_TARGET_MS -> config.unlockTargetMs = value.toInt()
                ARGON2_CALIBRATION_CLAMPED -> config.argon2CalibrationClamped = value.toBoolean()
                LAST_CALIBRATED_MEMORY_KB -> config.lastCalibratedMemoryKb = value.toInt()
                LAST_CALIBRATED_ITERATIONS -> config.lastCalibratedIterations = value.toInt()
                FILE_LOADING_PRIORITY -> config.fileLoadingPriority = value.toInt()
                AUTOPLAY_VIDEOS -> config.autoplayVideos = value.toBoolean()
                REMEMBER_LAST_VIDEO_POSITION -> config.rememberLastVideoPosition = value.toBoolean()
                LOOP_VIDEOS -> config.loopVideos = value.toBoolean()
                GESTURE_VIDEO_PLAYER -> config.gestureVideoPlayer = value.toBoolean()
                VIDEO_PLAYER_TYPE -> config.videoPlayerType = value.toInt()
                VIDEO_DOUBLE_TAP_FAST_FORWARD -> config.videoDoubleTapFastForward = value.toInt()
                ALLOW_VIDEO_GESTURES -> config.allowVideoGestures = value.toBoolean()
                ANIMATE_GIFS -> config.animateGifs = value.toBoolean()
                CROP_THUMBNAILS -> config.cropThumbnails = value.toBoolean()
                SHOW_THUMBNAIL_VIDEO_DURATION -> config.showThumbnailVideoDuration = value.toBoolean()
                SHOW_THUMBNAIL_FILE_TYPES -> config.showThumbnailFileTypes = value.toBoolean()
                MARK_FAVORITE_ITEMS -> config.markFavoriteItems = value.toBoolean()
                SCROLL_HORIZONTALLY -> config.scrollHorizontally = value.toBoolean()
                ENABLE_PULL_TO_REFRESH -> config.enablePullToRefresh = value.toBoolean()
                MAX_BRIGHTNESS -> config.maxBrightness = value.toBoolean()
                ULTRA_HDR_RENDERING -> config.ultraHdrRendering = value.toBoolean()
                BLACK_BACKGROUND -> config.blackBackground = value.toBoolean()
                HIDE_SYSTEM_UI -> config.hideSystemUI = value.toBoolean()
                ALLOW_INSTANT_CHANGE -> config.allowInstantChange = value.toBoolean()
                KEEP_SCREEN_ON -> config.keepScreenOn = value.toBoolean()
                ALLOW_PHOTO_GESTURES -> config.allowPhotoGestures = value.toBoolean()
                ALLOW_DOWN_GESTURE -> config.allowDownGesture = value.toBoolean()
                ALLOW_ROTATING_WITH_GESTURES -> config.allowRotatingWithGestures = value.toBoolean()
                SHOW_NOTCH -> config.showNotch = value.toBoolean()
                SCREEN_ROTATION -> config.screenRotation = value.toInt()
                ALLOW_ZOOMING_IMAGES -> config.allowZoomingImages = value.toBoolean()
                SHOW_HIGHEST_QUALITY -> config.showHighestQuality = value.toBoolean()
                ALLOW_ONE_TO_ONE_ZOOM -> config.allowOneToOneZoom = value.toBoolean()
                SHOW_EXTENDED_DETAILS -> config.showExtendedDetails = value.toBoolean()
                HIDE_EXTENDED_DETAILS -> config.hideExtendedDetails = value.toBoolean()
                EXTENDED_DETAILS -> config.extendedDetails = value.toInt()
                DELETE_EMPTY_FOLDERS -> config.deleteEmptyFolders = value.toBoolean()
                CONFLICT_HANDLING_MODE -> config.conflictHandlingMode = value.toInt()
                FAST_LARGE_DUPLICATE_CHECK -> config.fastLargeDuplicateCheck = value.toBoolean()
                DATE_TIE_BREAK_MODE -> config.dateTieBreakMode = value.toInt()
                ALBUM_CRYPTO_CONFLICT_MODE -> config.albumCryptoConflictMode = value.toInt()
                SKIPPED_ENCRYPT_FILES_MODE -> config.skippedEncryptFilesMode = value.toInt()
                SKIPPED_DECRYPT_FILES_MODE -> config.skippedDecryptFilesMode = value.toInt()
                KEEP_LAST_MODIFIED -> config.keepLastModified = value.toBoolean()
                SKIP_DELETE_CONFIRMATION -> config.skipDeleteConfirmation = value.toBoolean()
                DELETE_ORIGINAL_FROM_FAVORITES_AND_VIRTUAL_ALBUMS -> config.deleteOriginalFromFavoritesAndVirtualAlbums = value.toBoolean()
                ALLOW_BATCH_RENAMING -> config.allowBatchRenaming = value.toBoolean()
                BOTTOM_ACTIONS -> config.bottomActions = value.toBoolean()
                VISIBLE_BOTTOM_ACTIONS -> config.visibleBottomActions = value.toInt()
                USE_RECYCLE_BIN -> config.useRecycleBin = value.toBoolean()
                SHOW_RECYCLE_BIN_AT_FOLDERS -> config.showRecycleBinAtFolders = value.toBoolean()
                SHOW_RECYCLE_BIN_LAST -> config.showRecycleBinLast = value.toBoolean()
                SORT_ORDER -> config.sorting = value.toInt()
                DIRECTORY_SORT_ORDER -> config.directorySorting = value.toInt()
                FILES_SORT_ORDER -> config.filesSorting = value.toInt()
                GROUP_BY -> config.groupBy = value.toInt()
                GROUP_DIRECT_SUBFOLDERS -> config.groupDirectSubfolders = value.toBoolean()
                PINNED_FOLDERS -> config.addPinnedFolders(value.toStringSet())
                DISPLAY_FILE_NAMES -> config.displayFileNames = value.toBoolean()
                FILTER_MEDIA -> config.filterMedia = value.toInt()
                DIR_COLUMN_CNT -> config.dirColumnCnt = value.toInt()
                MEDIA_COLUMN_CNT -> config.mediaColumnCnt = value.toInt()
                SHOW_ALL -> config.showAll = value.toBoolean()
                SHOW_WIDGET_FOLDER_NAME -> config.showWidgetFolderName = value.toBoolean()
                VIEW_TYPE_FILES -> config.viewTypeFiles = value.toInt()
                VIEW_TYPE_FOLDERS -> config.viewTypeFolders = value.toInt()
                SLIDESHOW_INTERVAL -> config.slideshowInterval = value.toInt()
                SLIDESHOW_INCLUDE_VIDEOS -> config.slideshowIncludeVideos = value.toBoolean()
                SLIDESHOW_INCLUDE_GIFS -> config.slideshowIncludeGIFs = value.toBoolean()
                SLIDESHOW_RANDOM_ORDER -> config.slideshowRandomOrder = value.toBoolean()
                SLIDESHOW_MOVE_BACKWARDS -> config.slideshowMoveBackwards = value.toBoolean()
                SLIDESHOW_LOOP -> config.loopSlideshow = value.toBoolean()
                LAST_EDITOR_CROP_ASPECT_RATIO -> config.lastEditorCropAspectRatio = value.toInt()
                LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_X -> config.lastEditorCropOtherAspectRatioX = value.toString().toFloat()
                LAST_EDITOR_CROP_OTHER_ASPECT_RATIO_Y -> config.lastEditorCropOtherAspectRatioY = value.toString().toFloat()
                LAST_CONFLICT_RESOLUTION -> config.lastConflictResolution = value.toInt()
                LAST_CONFLICT_APPLY_TO_ALL -> config.lastConflictApplyToAll = value.toBoolean()
                EDITOR_BRUSH_COLOR -> config.editorBrushColor = value.toInt()
                EDITOR_BRUSH_HARDNESS -> config.editorBrushHardness = value.toString().toFloat()
                EDITOR_BRUSH_SIZE -> config.editorBrushSize = value.toString().toFloat()
                FOLDER_THUMBNAIL_STYLE -> config.folderStyle = value.toInt()
                FOLDER_MEDIA_COUNT -> config.showFolderMediaCount = value.toInt()
                LIMIT_FOLDER_TITLE -> config.limitFolderTitle = value.toBoolean()
                SHOW_VIRTUAL_ALBUM_ICON -> config.showVirtualAlbumIcon = value.toBoolean()
                SHOW_FAVORITES_ALBUM_ICON -> config.showFavoritesAlbumIcon = value.toBoolean()
                SHOW_EXCLUDED_ALBUM_ICON -> config.showExcludedAlbumIcon = value.toBoolean()
                SHOW_HIDDEN_ALBUM_ICON -> config.showHiddenAlbumIcon = value.toBoolean()
                THUMBNAIL_SPACING -> config.thumbnailSpacing = value.toInt()
                FILE_ROUNDED_CORNERS -> config.fileRoundedCorners = value.toBoolean()
                HOME_SCREEN_VIEW_ON_LAUNCH -> config.homeScreenViewOnLaunch = value.toInt()
                ALBUM_COVERS -> {
                    val existingCovers = config.parseAlbumCovers()
                    val existingCoverPaths = existingCovers.map { it.path }.toMutableList() as ArrayList<String>

                    val listType = object : TypeToken<List<AlbumCover>>() {}.type
                    val covers = Gson().fromJson<ArrayList<AlbumCover>>(value.toString(), listType) ?: ArrayList(1)
                    covers.filter { !existingCoverPaths.contains(it.path) && getDoesFilePathExist(it.tmb) }.forEach {
                        existingCovers.add(it)
                    }

                    config.albumCovers = Gson().toJson(existingCovers)
                }
            }
        }

        toast(if (configValues.size > 0) org.fossify.commons.R.string.settings_imported_successfully else org.fossify.commons.R.string.no_entries_for_importing)
        runOnUiThread {
            setupSettingItems()
        }
    }
}
