package org.fossify.gallery.activities

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.dialogs.ChangeDateTimeFormatDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
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
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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

        private const val PICK_IMPORT_SOURCE_INTENT = 1
        private const val SELECT_EXPORT_FAVORITES_FILE_INTENT = 2
        private const val SELECT_IMPORT_FAVORITES_FILE_INTENT = 3
        private const val ROW_DECORATION_TAG = "settings_row_decoration"

        /** Rows that perform an immediate action instead of opening a sub-screen. */
        private val ACTION_ROW_IDS = setOf(
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

    private data class CategorySpec(
        val key: String,
        @StringRes val titleRes: Int,
        @DrawableRes val iconRes: Int,
        val container: LinearLayout,
        val groups: List<Pair<LinearLayout, TextView>>,
    )

    private data class SearchResultEntry(
        @DrawableRes val iconRes: Int,
        val title: String,
        val breadcrumb: String,
        val onClick: () -> Unit,
    )

    private var mRecycleBinContentSize = 0L
    private var activeSettingsSearch = ""
    private var isSettingUpItems = false
    private var pendingHighlightViewId = 0
    private var biometricStatus = BiometricManager.BIOMETRIC_STATUS_UNKNOWN
    private var biometricKeyAvailable = false
    private var userPresentReceiver: BroadcastReceiver? = null
    private var biometricUsable = false
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    private val activeCategory: String?
        get() = intent.getStringExtra(EXTRA_SETTINGS_CATEGORY)

    private val categorySpecs: List<CategorySpec> by lazy {
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
                key = CATEGORY_NOTIFICATIONS,
                titleRes = R.string.notifications_settings,
                iconRes = R.drawable.ic_section_notifications,
                container = binding.settingsCategoryNotifications,
                groups = listOf(
                    binding.settingsNotificationsCard to binding.settingsNotificationsEyebrow,
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

    private fun openCategory(categoryKey: String, highlightViewId: Int = 0) {
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

    private fun setupSettingItems() {
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
            setupNotifications()
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

    private fun setupSettingsSearch() {
        binding.settingsSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                activeSettingsSearch = s?.toString()?.trim().orEmpty()
                applySettingsSearch()
            }
        })
        binding.settingsSearchClear.setOnClickListener {
            binding.settingsSearch.setText("")
        }
        refreshSettingsSearchColors()
    }

    /**
     * Commons' applyFontToTextView returns early for Typeface.DEFAULT, so returning from a custom
     * font to the system font would leave the old typeface on already inflated views. Apply the
     * typeface explicitly here, preserving each view's bold/italic style.
     */
    private fun refreshSettingsFont() {
        val typeface = if (config.fontType == FONT_TYPE_SYSTEM_DEFAULT && config.fontName.isEmpty()) {
            Typeface.DEFAULT
        } else {
            FontHelper.getTypeface(this, config.fontType, config.fontName) ?: Typeface.DEFAULT
        }
        fun apply(view: View) {
            if (view is TextView) {
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = Typeface.create(typeface, style)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    apply(view.getChildAt(i))
                }
            }
        }
        apply(binding.root)
    }

    private fun refreshSettingsSearchColors() {
        val palette = AppearanceManager.resolve(this, config)
        val mutedColor = SettingsCardUi.mutedTextColor(palette)
        binding.settingsSearchContainer.background = SettingsCardUi.createSearchBackground(this, palette)
        binding.settingsSearchContainer.clipToOutline = true
        binding.settingsSearch.setTextColor(palette.text)
        binding.settingsSearch.setHintTextColor(mutedColor)
        binding.settingsSearchIcon.imageTintList = ColorStateList.valueOf(mutedColor)
        binding.settingsSearchClear.imageTintList = ColorStateList.valueOf(mutedColor)
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

    private fun extractRowPrimaryTitle(row: View): String {
        var found = ""
        fun walk(current: View) {
            if (found.isNotEmpty()) return
            if (current is TextView && !current.text.isNullOrBlank()) {
                found = current.text.toString().trim()
                return
            }
            if (current is ViewGroup) {
                for (i in 0 until current.childCount) {
                    walk(current.getChildAt(i))
                }
            }
        }
        walk(row)
        return found
    }

    /** Cross-category search rendered inside the Hub screen. */
    private fun applySettingsSearch() {
        if (activeCategory != null) return

        val query = activeSettingsSearch.lowercase()
        binding.settingsSearchClear.isVisible = query.isNotEmpty()
        if (query.isEmpty()) {
            binding.settingsHubCard.isVisible = true
            binding.settingsSearchResultsEyebrow.isVisible = false
            binding.settingsSearchResultsCard.isVisible = false
            binding.settingsSearchEmpty.isVisible = false
            return
        }

        binding.settingsHubCard.isVisible = false
        val palette = AppearanceManager.resolve(this, config)
        val results = collectSearchResults(query)
        val card = binding.settingsSearchResultsCard
        card.removeAllViews()

        if (results.isEmpty()) {
            binding.settingsSearchResultsEyebrow.isVisible = false
            card.isVisible = false
            binding.settingsSearchEmpty.setTextColor(SettingsCardUi.mutedTextColor(palette))
            binding.settingsSearchEmpty.isVisible = true
            return
        }

        binding.settingsSearchEmpty.isVisible = false
        for (entry in results) {
            card.addView(buildHubCardRow(entry.iconRes, entry.title, entry.breadcrumb, palette, entry.onClick))
        }
        SettingsCardUi.styleCardGroup(
            card = card,
            palette = palette,
            insetStartDp = 60f,
            eyebrow = binding.settingsSearchResultsEyebrow,
        )
        refreshSettingsFont()
    }

    private fun collectSearchResults(query: String): List<SearchResultEntry> {
        val results = mutableListOf<SearchResultEntry>()
        val aboutTitle = getString(org.fossify.commons.R.string.about)
        val appearanceTitle = getString(R.string.appearance_title)

        // 1. Top-level category matches
        if (aboutTitle.lowercase().contains(query)) {
            results += SearchResultEntry(R.drawable.ic_section_about, aboutTitle, aboutTitle) { launchAbout() }
        }
        if (appearanceTitle.lowercase().contains(query)) {
            results += SearchResultEntry(R.drawable.ic_section_appearance, appearanceTitle, appearanceTitle) {
                startActivity(Intent(this, AppearanceActivity::class.java))
            }
        }
        for (spec in categorySpecs) {
            val catTitle = getString(spec.titleRes)
            if (catTitle.lowercase().contains(query)) {
                results += SearchResultEntry(spec.iconRes, catTitle, catTitle) { openCategory(spec.key) }
            }
        }

        // 2. External screen items (About & Appearance)
        val aboutKeywords = listOf(
            R.string.about_read_gpl_license to R.string.about_license_heading,
            R.string.about_fork_source to R.string.about_source_heading,
            R.string.about_faq to R.string.about_help_heading,
            R.string.about_third_party_licenses to R.string.about_help_heading,
        )
        for ((titleRes, groupRes) in aboutKeywords) {
            val title = getString(titleRes)
            if (title.lowercase().contains(query)) {
                results += SearchResultEntry(
                    iconRes = R.drawable.ic_section_about,
                    title = title,
                    breadcrumb = "$aboutTitle › ${getString(groupRes)}",
                ) { launchAbout() }
            }
        }

        val appearanceKeywords = listOf(
            R.string.follow_system_theme to R.string.appearance_automatic,
            org.fossify.commons.R.string.light_theme to org.fossify.commons.R.string.theme,
            org.fossify.commons.R.string.dark_theme to org.fossify.commons.R.string.theme,
            R.string.theme_midnight_blue to org.fossify.commons.R.string.theme,
            R.string.theme_warm_paper to org.fossify.commons.R.string.theme,
            R.string.theme_deep_forest to org.fossify.commons.R.string.theme,
            R.string.custom_colors to R.string.custom_colors,
            R.string.saved_themes to R.string.custom_colors,
            org.fossify.commons.R.string.app_font to org.fossify.commons.R.string.font,
            R.string.album_type_icons to R.string.album_type_icons,
            R.string.backup_saved_themes_to_storage to R.string.appearance_backup_section,
        )
        for ((titleRes, groupRes) in appearanceKeywords) {
            val title = getString(titleRes)
            if (title.lowercase().contains(query)) {
                results += SearchResultEntry(
                    iconRes = R.drawable.ic_section_appearance,
                    title = title,
                    breadcrumb = "$appearanceTitle › ${getString(groupRes)}",
                ) { startActivity(Intent(this, AppearanceActivity::class.java)) }
            }
        }

        // 3. All visible rows inside the 8 category containers
        for (spec in categorySpecs) {
            val catTitle = getString(spec.titleRes)
            for ((card, eyebrow) in spec.groups) {
                val groupTitle = eyebrow.text?.toString()?.trim().orEmpty()
                for (i in 0 until card.childCount) {
                    val row = card.getChildAt(i)
                    if (!row.isVisible || !isSettingsHolderRow(row)) continue
                    if (viewText(row).lowercase().contains(query)) {
                        val title = extractRowPrimaryTitle(row)
                        if (title.isNotEmpty()) {
                            val breadcrumb = if (groupTitle.isNotEmpty()) "$catTitle › $groupTitle" else catTitle
                            results += SearchResultEntry(spec.iconRes, title, breadcrumb) {
                                openCategory(spec.key, highlightViewId = row.id)
                            }
                        }
                    }
                }
            }
        }

        return results
    }

    /**
     * Builds a card row with a squircle icon chip, label (plus optional breadcrumb subtitle), and chevron.
     * Shared by the main Settings hub (single-line) and cross-category search results (with breadcrumb).
     */
    private fun buildHubCardRow(
        iconRes: Int,
        title: String,
        subtitle: String?,
        palette: AppearancePalette,
        onClick: () -> Unit,
    ): View {
        val density = resources.displayMetrics.density
        val isCompact = subtitle == null
        val padH = (14f * density).toInt()
        val padV = ((if (isCompact) 10f else 11f) * density).toInt()
        val chipSize = (34f * density).toInt()
        val iconSize = (18f * density).toInt()
        val gap = (12f * density).toInt()
        val mutedColor = SettingsCardUi.mutedTextColor(palette)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ((if (isCompact) 52f else 54f) * density).toInt()
            setPadding(padH, padV, padH, padV)
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onClick() }
        }

        val chip = FrameLayout(this).apply {
            background = SettingsCardUi.createIconChipBackground(this@SettingsActivity, palette)
            layoutParams = LinearLayout.LayoutParams(chipSize, chipSize).apply { marginEnd = gap }
            addView(
                AppCompatImageView(this@SettingsActivity).apply {
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(palette.primary)
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                },
            )
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = gap
            }
            addView(
                MyTextView(this@SettingsActivity).apply {
                    text = title
                    setTextColor(palette.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                },
            )
            if (subtitle != null) {
                addView(
                    MyTextView(this@SettingsActivity).apply {
                        text = subtitle
                        setTextColor(mutedColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    },
                )
            }
        }

        val chevron = AppCompatImageView(this).apply {
            setImageResource(R.drawable.ic_settings_chevron)
            imageTintList = ColorStateList.valueOf(mutedColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }

        row.addView(chip)
        row.addView(textCol)
        row.addView(chevron)
        return row
    }

    private fun isSettingsHolderRow(view: View): Boolean {
        if (view !is ViewGroup || view.id == View.NO_ID) return false
        val name = resources.getResourceEntryName(view.id)
        return name.startsWith("settings_") && name.endsWith("_holder")
    }

    /**
     * Styles the active screen: in Hub mode, renders the 10-row single rounded card with squircle
     * icon chips and chevrons; in Category mode, adds truthful chevrons on sub-screen rows and
     * renders each logical section inside its own rounded card with hairline dividers.
     */
    private fun refreshRowDecorations() {
        val palette = AppearanceManager.resolve(this, config)
        val mutedColor = SettingsCardUi.mutedTextColor(palette)
        val chevronTint = ColorStateList.valueOf(mutedColor)

        if (activeCategory == null) {
            val hubCard = binding.settingsHubCard
            hubCard.removeAllViews()
            hubCard.addView(
                buildHubCardRow(
                    iconRes = R.drawable.ic_section_about,
                    title = getString(org.fossify.commons.R.string.about),
                    subtitle = null,
                    palette = palette,
                    onClick = { launchAbout() },
                ),
            )
            hubCard.addView(
                buildHubCardRow(
                    iconRes = R.drawable.ic_section_appearance,
                    title = getString(R.string.appearance_title),
                    subtitle = null,
                    palette = palette,
                    onClick = { startActivity(Intent(this, AppearanceActivity::class.java)) },
                ),
            )
            for (spec in categorySpecs) {
                hubCard.addView(
                    buildHubCardRow(
                        iconRes = spec.iconRes,
                        title = getString(spec.titleRes),
                        subtitle = null,
                        palette = palette,
                        onClick = { openCategory(spec.key) },
                    ),
                )
            }

            SettingsCardUi.styleCardGroup(hubCard, palette, insetStartDp = 60f)
            refreshSettingsFont()
            return
        }

        val activeSpec = categorySpecs.firstOrNull { it.key == activeCategory } ?: return
        for ((card, eyebrow) in activeSpec.groups) {
            for (i in 0 until card.childCount) {
                val child = card.getChildAt(i)
                if (!isSettingsHolderRow(child)) continue
                val row = child as ViewGroup
                for (j in row.childCount - 1 downTo 0) {
                    if (row.getChildAt(j).tag == ROW_DECORATION_TAG) {
                        row.removeViewAt(j)
                    }
                }
                if (row.isVisible && !hasSwitch(row) && row.id !in ACTION_ROW_IDS) {
                    row.addView(createChevron(row, palette))
                }
            }
            SettingsCardUi.styleCardGroup(card, palette, insetStartDp = 14f, eyebrow = eyebrow)
        }

        val dangerColor = SettingsCardUi.dangerColor(palette)
        binding.settingsEmptyVault.setTextColor(dangerColor)
        binding.settingsEmptyRecycleBinLabel.setTextColor(dangerColor)
        binding.settingsVaultBiometricInfo.imageTintList = chevronTint
        binding.settingsSyncVaultInfo.imageTintList = chevronTint
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

    private fun createChevron(row: ViewGroup, palette: AppearancePalette): ImageView {
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
        val tint = ColorStateList.valueOf(SettingsCardUi.mutedTextColor(palette))
        return AppCompatImageView(this).apply {
            tag = ROW_DECORATION_TAG
            setImageResource(R.drawable.ic_settings_chevron)
            imageTintList = tint
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Rows inside rounded cards already carry 14dp end padding; align flush with that edge.
            layoutParams = if (row is ConstraintLayout) {
                ConstraintLayout.LayoutParams(size, size).apply {
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
            } else {
                RelativeLayout.LayoutParams(size, size).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
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

    private fun doesVaultExist(): Boolean {
        return CryptoManager.verifyVaultOnDisk(this)
    }

    private var isVaultSectionNearViewport = false

    // Use a ViewTreeObserver listener: setOnScrollChangeListener() is intentionally left free
    // (setting up the material scroll listener would tint the top bar on scroll).
    private val vaultSectionScrollListener = android.view.ViewTreeObserver.OnScrollChangedListener {
        // Use whichever vault section row is currently visible as the viewport anchor.
        val anchor = when {
            binding.settingsVaultLocationHolder.isShown -> binding.settingsVaultLocationHolder
            binding.settingsCreateVaultHolder.isShown -> binding.settingsCreateVaultHolder
            else -> return@OnScrollChangedListener
        }
        val scroll = binding.settingsNestedScrollview

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


        // 0) Kasa konumu yalnızca kasa varsa görünür (birden fazla kasa arasında geçiş için).
        binding.settingsVaultLocationHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 1) Şifreli kasa oluştur (bu sadece şifreli kasa oluşturulmamış ise gösterilir;
        // Tüm Dosyalara erişim izni yoksa da görünür, dokunulduğunda tema uyumlu izin ekranı açılır)
        binding.settingsCreateVaultHolder.beVisibleIf(!vaultExists || !hasStoragePermission)

        // 1b) Harici diskten kasayı eşitle
        binding.settingsSyncVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 2) Şifreli kasa şifresini değiştir
        binding.settingsChangeVaultPasswordHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 3) Şifreli kasaya biyometrik ile giriş — satır her zaman görünür, ancak uygun
        // donanım/anahtar yoksa pasif ve sağındaki i ikonuyla nedeni açıklanır.
        // onResume bu metodları yeniden çağırdığı için Android ayarlarından biyometrik
        // açılıp kapatıldığında görünüm değişir; ek bildirim/popup gösterilmez.
        biometricStatus = CryptoManager.getBiometricAuthenticatorStatus(this)
        // Keep the last known good value while the device is locked or the Keystore is busy:
        // a transient probe failure must not make the row look permanently unavailable.
        when (CryptoManager.probeBiometricKey(this)) {
            BiometricKeyProbe.READY -> biometricKeyAvailable = true
            BiometricKeyProbe.UNAVAILABLE -> biometricKeyAvailable = false
            BiometricKeyProbe.DEFERRED -> Unit
        }
        biometricUsable = biometricStatus == BiometricManager.BIOMETRIC_SUCCESS && biometricKeyAvailable
        binding.settingsVaultBiometricHolder.beVisibleIf(hasStoragePermission && vaultExists)
        binding.settingsVaultBiometricAutoReturnHolder.beVisibleIf(
            hasStoragePermission && vaultExists && biometricUsable && config.isVaultBiometricEnabled
        )
        binding.settingsVaultPasswordReminderHolder.beVisibleIf(hasStoragePermission && vaultExists)
        // An already archived vault cannot be archived again (ADR 0007).
        val activeArchived = VaultArchiveManager.isActiveVaultArchived(this)
        binding.settingsArchiveVaultHolder.beVisibleIf(
            hasStoragePermission && vaultExists && !activeArchived
        )
        binding.settingsUnarchiveVaultHolder.beVisibleIf(
            hasStoragePermission && vaultExists && activeArchived
        )

        // 4) Şifreli kasada ekran görüntüsü almayı yasakla
        binding.settingsBlockVaultScreenshotsHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 4b) Tuş basımlarını gizleme yalnızca PIN kullanan kasalarda anlamlı
        binding.settingsHideVaultKeyPressesHolder.beVisibleIf(
            hasStoragePermission && vaultExists &&
                CryptoManager.getVaultProtectionType(this) == PROTECTION_PIN
        )

        // 4c) Nokta göstergesi: PIN kullanan kasalarda gösterilir; PIN uzunluğu ayar kapalıyken
        // diske yazılmadığı için uzunluk kontrolü ayar açılırken PIN doğrulaması sırasında yapılır.
        val pinDotsApplicable = vaultExists &&
            CryptoManager.getVaultProtectionType(this) == PROTECTION_PIN
        if (!config.showVaultPinDots) {
            // Eski sürümden kalma açıkta duran vault_pin_length kaydı varsa temizle
            config.vaultPinLength = 0
        }
        binding.settingsShowVaultPinDotsHolder.beVisibleIf(hasStoragePermission && pinDotsApplicable)

        // 5) Şifreli kasayı boşalt
        binding.settingsEmptyVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 6) Şifreli kasayı dışa aktar
        binding.settingsExportVaultHolder.beVisibleIf(hasStoragePermission && vaultExists)

        // 7) Şifreleme çakışma ve atlanan dosya ayarları yalnızca şifreli kasa oluşturulduysa görünür
        binding.settingsSkippedEncryptFilesHolder.beVisibleIf(hasStoragePermission && vaultExists)
        binding.settingsAlbumCryptoConflictHolder.beVisibleIf(hasStoragePermission && vaultExists)

        setupArgon2Profile()
        setupVaultLocation()
        setupCreateVault()
        setupExportVault()
        setupSyncVault()
        setupChangeVaultPassword()
        setupVaultBiometric()
        setupVaultBiometricAutoReturn()
        setupVaultPasswordReminder()
        setupArchiveVault()
        setupUnarchiveVault()
        setupBlockVaultScreenshots()
        setupHideVaultKeyPresses()
        setupShowVaultPinDots()
        setupEmptyVault()
        // Teklif penceresinden biyometrik kurulduysa anahtar hemen yansısın.
        updateVaultBiometricSwitch()
    }

    /**
     * Vault-wide switches and the destructive "empty vault" action always re-verify the password, even when the
     * vault is already unlocked because the person is viewing an encrypted album. The ordinary vault-session state
     * is intentionally ignored here.
     */
    private fun requireVaultPassword(
        allowBiometrics: Boolean = true,
        verifyOnly: Boolean = false,
        onAuthorized: () -> Unit
    ) {
        if (!doesVaultExist()) return
        val currentType = CryptoManager.getVaultProtectionType(this)
            ?: if (config.hiddenProtectionType == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
        VaultSecurityDialog(
            activity = this,
            requiredHash = config.hiddenPasswordHash,
            showTabIndex = currentType,
            minimalist = false,
            allowBiometrics = allowBiometrics,
            verifyOnly = verifyOnly,
        ) { _, _, success ->
            if (success) onAuthorized()
        }
    }

    private fun setupBlockVaultScreenshots() {
        binding.settingsBlockVaultScreenshots.isChecked = config.blockVaultScreenshots
        binding.settingsBlockVaultScreenshotsHolder.setOnClickListener {
            requireVaultPassword(verifyOnly = true) {
                binding.settingsBlockVaultScreenshots.toggle()
                config.blockVaultScreenshots = binding.settingsBlockVaultScreenshots.isChecked
            }
        }
    }

    private fun setupHideVaultKeyPresses() {
        binding.settingsHideVaultKeyPresses.isChecked = config.hideVaultKeyPresses
        binding.settingsHideVaultKeyPressesHolder.setOnClickListener {
            requireVaultPassword(verifyOnly = true) {
                binding.settingsHideVaultKeyPresses.toggle()
                config.hideVaultKeyPresses = binding.settingsHideVaultKeyPresses.isChecked
            }
        }
    }

    private fun setupShowVaultPinDots() {
        binding.settingsShowVaultPinDots.isChecked = config.showVaultPinDots
        binding.settingsShowVaultPinDotsHolder.setOnClickListener {
            val enabling = !config.showVaultPinDots
            val proceedWithAuth = {
                // Ayar açılırken PIN uzunluğunu öğrenmek için biyometrik yerine PIN girişi zorunlu tutulur;
                // ayar kapatılırken biyometrik doğrulama da kullanılabilir ve uzunluk diskten silinir.
                requireVaultPassword(allowBiometrics = !enabling, verifyOnly = true) {
                    if (enabling) {
                        config.showVaultPinDots = true
                        val enteredLen = VaultAuthInputHelper.lastAuthenticatedPinLength
                        if (enteredLen in 1..VAULT_PIN_DOTS_MAX_LENGTH) {
                            config.vaultPinLength = enteredLen
                            binding.settingsShowVaultPinDots.isChecked = true
                        } else {
                            config.showVaultPinDots = false
                            binding.settingsShowVaultPinDots.isChecked = false
                        }
                    } else {
                        config.showVaultPinDots = false
                        binding.settingsShowVaultPinDots.isChecked = false
                    }
                }
            }

            // The side-by-side preview is a one-time introduction: show it only on the first
            // enable. Every later attempt - including turning the setting back on after it was
            // disabled - must go straight to password verification, otherwise the toggle would be
            // stuck once the notice flag has been persisted.
            if (enabling && !config.pinDotsNoticeShown) {
                org.fossify.gallery.dialogs.VaultNoticeDialog(
                    activity = this,
                    title = getString(R.string.pin_dots_notice_title),
                    message = "",
                    noteText = getString(R.string.pin_dots_notice_note),
                    positiveText = getString(R.string.onboarding_continue),
                    negativeText = getString(org.fossify.commons.R.string.cancel),
                    cancelable = true,
                    canceledOnTouchOutside = true,
                    showPinDotsPreview = true,
                    showNoteShieldIcon = true,
                    filledPositiveButton = true,
                    onPositive = {
                        config.pinDotsNoticeShown = true
                        proceedWithAuth()
                    },
                )
            } else {
                proceedWithAuth()
            }
        }
    }

    private fun setupShareMetadata() {
        fun refreshSummary() {
            binding.settingsShareMetadataValue.text = getString(
                when (config.shareMetadataMode) {
                    SHARE_METADATA_MODE_ALL -> R.string.share_metadata_all
                    SHARE_METADATA_MODE_OFF -> R.string.share_metadata_off
                    else -> R.string.share_metadata_location_only
                }
            )
        }
        refreshSummary()

        binding.settingsShareMetadataHolder.setOnClickListener {
            RadioGroupDialog(
                activity = this,
                items = arrayListOf(
                    RadioItem(SHARE_METADATA_MODE_OFF, getString(R.string.share_metadata_off)),
                    RadioItem(SHARE_METADATA_MODE_LOCATION, getString(R.string.share_metadata_location_only)),
                    RadioItem(SHARE_METADATA_MODE_ALL, getString(R.string.share_metadata_all))
                ),
                checkedItemId = config.shareMetadataMode,
                titleId = R.string.settings_share_metadata_title,
            ) {
                config.shareMetadataMode = it as Int
                refreshSummary()
            }
        }
    }

    private fun setupArgon2Profile() {
        // Bu ayar yalnızca kasa mevcutsa ve tüm dosyalara erişim izni varsa görünür.
        val hasStoragePermission = !isRPlus() || isExternalStorageManager()
        binding.settingsArgon2ProfileHolder.beVisibleIf(hasStoragePermission && doesVaultExist())
        fun refreshSummary() {
            val summary = org.fossify.gallery.dialogs.BruteForceDialog.summary(this)
            binding.settingsArgon2ProfileValue.text = summary
            binding.settingsArgon2ProfileValue.beGoneIf(summary.isEmpty())
        }
        refreshSummary()
        // No silent background probing: the 64 MiB / 3 rounds measurement runs only when the
        // user taps the row (BruteForceDialog.show), with a visible progress dialog.

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
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
                return@setOnClickListener
            }
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
                    // Export is a copy, so the vault password is not needed. A device-owner check
                    // keeps casual access to an unlocked phone from extracting the vault files.
                    requireDeviceOwner {
                        promptExportEncryptedVault(
                            onlyVaultHeader = dialogBinding.exportVaultOnlyHeaderRadio.isChecked
                        ) {
                            setupVaultSettings()
                        }
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
            if (changeSuccess) {
                updateVaultBiometricSwitch()
                toast(R.string.password_changed_successfully)
                // 3a → 3b akışı unlockedMasterKey'i kullanır. Anahtarı ancak teklif
                // tamamlandıktan (veya Geri ile atlandıktan) sonra RAM'den sil; aksi
                // halde completeBiometricSetup() sessizce false döner.
                showVaultPasswordSavedDialog {
                    VaultSessionManager.lockVaultIfOutsideSession(this)
                }
            } else {
                VaultSessionManager.lockVaultIfOutsideSession(this)
            }
        }
    }

    private fun setupVaultBiometric() {
        org.fossify.gallery.dialogs.VaultBiometricOfferDialog.onBiometricChanged = {
            runOnUiThread { setupVaultSettings() }
        }
        //Visibility is owned by setupVaultSettings(): the row stays visible whenever a vault
        //exists so the info icon can explain why it is passive. Do not re-apply the old hardware/key gate here.
        binding.settingsVaultBiometricInfo.setOnClickListener { showBiometricUnavailableDialog() }
        updateVaultBiometricSwitch()
        binding.settingsVaultBiometricHolder.setOnClickListener {
            if (!doesVaultExist()) {
                toast(R.string.enable_hidden_protection_first_prompt)
                return@setOnClickListener
            }

            if (config.isVaultBiometricEnabled) {
                // Turning biometrics off must be confirmed by biometrics (or device credential):
                // the person may rely on it because they forgot the vault password.
                confirmDisableBiometric {
                    CryptoManager.clearBiometricKey(this)
                    config.temporarilyShowHidden = false
                    config.temporarilyShowVaultExcluded = false
                    CryptoManager.lockVault(this@SettingsActivity)
                    CryptoManager.clearAppImageCaches(this)
                    VaultSessionManager.onVaultExited()
                    applyVaultScreenProtection(false)
                    updateVaultBiometricSwitch()
                }
            } else {
                if (!biometricUsable) {
                    showBiometricUnavailableDialog()
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

    private fun setupVaultBiometricAutoReturn() {
        binding.settingsVaultBiometricAutoReturn.isChecked = config.autoBiometricPromptOnVaultReturn
        binding.settingsVaultBiometricAutoReturnHolder.setOnClickListener {
            if (!doesVaultExist() || !config.isVaultBiometricEnabled) return@setOnClickListener
            binding.settingsVaultBiometricAutoReturn.toggle()
            config.autoBiometricPromptOnVaultReturn = binding.settingsVaultBiometricAutoReturn.isChecked
        }
    }

    private fun setupArchiveVault() {
        binding.settingsArchiveVaultHolder.setOnClickListener {
            if (!doesVaultExist() || VaultArchiveManager.isActiveVaultArchived(this)) return@setOnClickListener
            requireDeviceOwner {
                DeleteSecretsDialog(
                    this,
                    getString(R.string.archive_vault_confirm),
                    titleId = R.string.archive_vault,
                ) {
                    CryptoManager.lockVault(this@SettingsActivity)
                    val entry = VaultArchiveManager.archiveActiveVault(this)
                    if (entry == null) {
                        toast(R.string.archive_vault_failed)
                        setupVaultSettings()
                        return@DeleteSecretsDialog
                    }

                    VaultNoticeDialog(
                        activity = this,
                        title = getString(R.string.archive_vault),
                        message = getString(R.string.archive_vault_success, humanizePath(entry.path)),
                        positiveText = getString(org.fossify.commons.R.string.ok),
                        negativeText = getString(R.string.copy_path),
                        cancelable = true,
                        onPositive = {},
                        onNegative = {
                            val clipboard = getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText(getString(R.string.archive_vault), entry.path))
                            // Vault-derived path: clearKeys(context) must wipe it on lock.
                            CryptoManager.vaultClipboardTouched = true
                        },
                    )

                    promptWhereToCreateVault(
                        onVaultReady = { setupVaultSettings() },
                        onBack = { setupVaultSettings() },
                    )
                }
            }
        }
    }

    /**
     * Archiving is a move, not a deletion, so the vault password is not required. On a device with
     * a secure lock screen we still verify the person holding the phone: Class 3 biometric first,
     * with the device PIN/pattern/password as a fallback. With no secure lock screen there is no
     * owner signal to check, so the 10-second confirmation of DeleteSecretsDialog is the only gate.
     */
    private fun deviceOwnerAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) {
            // AndroidX Biometric's AuthenticatorUtils.isSupportedCombination() rejects
            // BIOMETRIC_STRONG | DEVICE_CREDENTIAL on API 28-29 (throws IllegalArgumentException).
            // These prompts are non-crypto device-owner checks that already permit DEVICE_CREDENTIAL.
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    private fun requireDeviceOwner(onConfirmed: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            onConfirmed()
            return
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.device_owner_verify_title))
            .setSubtitle(getString(R.string.device_owner_verify_subtitle))
            .setAllowedAuthenticators(deviceOwnerAuthenticators())
            .build()
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onConfirmed()
                }
            }
        ).authenticate(promptInfo)
    }

    private fun setupUnarchiveVault() {
        binding.settingsUnarchiveVaultHolder.setOnClickListener {
            val entry = VaultArchiveManager.activeArchivedEntry(this) ?: return@setOnClickListener
            if (!VaultArchiveManager.canUnarchive(entry)) {
                VaultNoticeDialog(
                    activity = this,
                    title = getString(R.string.unarchive_vault),
                    message = getString(R.string.unarchive_vault_blocked),
                    positiveText = getString(R.string.unarchive_vault_merge),
                    negativeText = getString(org.fossify.commons.R.string.ok),
                    cancelable = true,
                    onPositive = {
                        VaultNoticeDialog(
                            activity = this,
                            title = getString(R.string.unarchive_vault_merge),
                            message = getString(R.string.unarchive_vault_merge_steps),
                            positiveText = getString(org.fossify.commons.R.string.ok),
                            cancelable = true,
                            onPositive = {},
                        )
                    },
                    onNegative = {},
                )
                return@setOnClickListener
            }

            DeleteSecretsDialog(this, getString(R.string.unarchive_vault_confirm)) {
                requireDeviceOwner {
                    val result = VaultArchiveManager.restoreArchivedVault(this, entry)
                    if (result == VaultArchiveManager.UnarchiveResult.OK) {
                        toast(R.string.unarchive_vault_success)
                    } else {
                        toast(R.string.archive_vault_failed)
                    }
                    setupVaultSettings()
                }
            }
        }
    }

    private fun setupVaultPasswordReminder() {
        fun updateValue() {
            binding.settingsVaultPasswordReminder.text =
                formatPasswordReminderInterval(this, config.vaultPasswordReminderDays)
        }
        updateValue()
        binding.settingsVaultPasswordReminderHolder.setOnClickListener {
            val days = Config.PASSWORD_REMINDER_DAYS_OPTIONS
            val items = days.map { value ->
                RadioItem(value, formatPasswordReminderInterval(this, value))
            } as ArrayList<RadioItem>

            RadioGroupDialog(
                activity = this,
                items = items,
                checkedItemId = config.vaultPasswordReminderDays,
                titleId = R.string.vault_password_reminder_dialog_title,
            ) {
                config.vaultPasswordReminderDays = it as Int
                resetPasswordReminderState(config)
                updateValue()
            }
        }
    }

    /** Reason dialog opened from the info icon (or from a tap on the passive row). */
    private fun showBiometricUnavailableDialog() {
        // A toast would cut off the explanation; show it in a theme-aware dialog.
        VaultNoticeDialog(
            activity = this,
            title = getString(R.string.vault_biometric_unavailable_title),
            message = getString(biometricUnavailableReason(biometricStatus, biometricKeyAvailable)),
            positiveText = getString(org.fossify.commons.R.string.ok),
            cancelable = true,
            onPositive = {},
        )
    }

    private fun updateVaultBiometricSwitch() {
        val vaultExists = doesVaultExist()
        val hasStoragePermission = !isRPlus() || isExternalStorageManager()
        val biometricEnabled = vaultExists && config.isVaultBiometricEnabled
        // Passive (grey) while no strong biometric is usable. When the user later enables one in
        // Android settings, onResume re-evaluates this and the switch becomes active but stays
        // off unless the user explicitly turns it on.
        binding.settingsVaultBiometric.isEnabled = vaultExists && biometricUsable
        binding.settingsVaultBiometric.alpha = if (biometricUsable) 1.0f else 0.5f
        binding.settingsVaultBiometricHolder.alpha = if (vaultExists) 1.0f else 0.4f
        binding.settingsVaultBiometric.isChecked = biometricEnabled
        binding.settingsVaultBiometricAutoReturnHolder.beVisibleIf(
            hasStoragePermission && biometricEnabled && biometricUsable
        )
        binding.settingsVaultBiometricAutoReturn.isChecked = config.autoBiometricPromptOnVaultReturn

        // Info icon replaces the always-visible explanation text. No popup is shown on state
        // change; the user opens it deliberately.
        binding.settingsVaultBiometricInfo.beVisibleIf(vaultExists && !biometricUsable)
        if (!isSettingUpItems) {
            refreshRowDecorations()
        }
    }

    private fun confirmDisableBiometric(onConfirmed: () -> Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_access_to_encrypted_vault))
            .setSubtitle(getString(R.string.biometric_disable_confirmation))
            .setAllowedAuthenticators(deviceOwnerAuthenticators())
            .build()
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onConfirmed()
            }
        ).authenticate(promptInfo)
    }

    private fun showBiometricEnrollmentPrompt(lockVaultAfter: Boolean) {
        fun launchSystemPrompt() {
            val cipher = CryptoManager.getBiometricEncryptCipher(this@SettingsActivity)
            if (cipher == null) {
                if (lockVaultAfter) {
                    CryptoManager.lockVault(this@SettingsActivity)
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
                            CryptoManager.lockVault(this@SettingsActivity)
                            applyVaultScreenProtection(false)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        CryptoManager.clearBiometricKey(this@SettingsActivity)
                        if (lockVaultAfter) {
                            CryptoManager.lockVault(this@SettingsActivity)
                            applyVaultScreenProtection(false)
                        }
                        updateVaultBiometricSwitch()
                    }
                }
            )
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }

        // Full explanation is shown in the themed card first (HTML screen 3b).
        showBiometricPasswordNoteDialog(
            title = getString(R.string.vault_biometric_offer_title),
            message = getString(R.string.vault_biometric_offer_message),
            note = getString(R.string.vault_biometric_setup_password_note),
            onContinue = { launchSystemPrompt() },
            onCancel = {
                // The key was promoted for enrollment; do not leave it in session RAM on cancel.
                VaultSessionManager.lockVaultIfOutsideSession(this@SettingsActivity)
                applyVaultScreenProtection(false)
            },
        )
    }

    private fun setupEmptyVault() {
        binding.settingsEmptyVaultHolder.setOnClickListener {
            if (!doesVaultExist()) return@setOnClickListener

            requireVaultPassword(allowBiometrics = false) {
                DeleteSecretsDialog(
                    this,
                    getString(R.string.empty_encrypted_vault_warning),
                    titleId = R.string.empty_encrypted_vault,
                ) {
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
            AppSecurityDialog(this, config.excludedPasswordHash, tabToShow) { hash, type, success ->
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
            AppSecurityDialog(this, config.appPasswordHash, tabToShow) { hash, type, success ->
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
            AppSecurityDialog(this, config.deletePasswordHash, tabToShow) { hash, type, success ->
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
                val isAutomatic = dialogBinding.conflictHandlingAuto.isChecked
                // Automatic (Experimental) never shows a dialog, so "Always ask" is not offered
                // there. If it was selected before switching modes, fall back to the default.
                val askAllowed = usesDuplicateCheck && !isAutomatic
                dialogBinding.fastLargeDuplicateCheckCheckbox.isEnabled = usesDuplicateCheck
                dialogBinding.fastLargeDuplicateCheckRow.alpha = if (usesDuplicateCheck) 1f else 0.45f
                dialogBinding.fastLargeDuplicateCheckHint.alpha = if (usesDuplicateCheck) 0.75f else 0.35f

                dialogBinding.dateTieBreakLabel.alpha = if (usesDuplicateCheck) 1f else 0.45f
                tieBreakRadios.forEach { radio ->
                    radio.isEnabled = if (radio === dialogBinding.dateTieBreakAsk) askAllowed else usesDuplicateCheck
                    radio.alpha = if (radio.isEnabled) 1f else 0.45f
                }
                dialogBinding.dateTieBreakAskInfo.isEnabled = askAllowed
                dialogBinding.dateTieBreakAskInfo.alpha = if (askAllowed) 1f else 0.45f
                dialogBinding.dateTieBreakHint.alpha = if (usesDuplicateCheck) 0.75f else 0.35f

                if (isAutomatic && dialogBinding.dateTieBreakAsk.isChecked) {
                    dialogBinding.dateTieBreakAsk.isChecked = false
                    dialogBinding.dateTieBreakKeepTarget.isChecked = true
                }
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
        val valueView = binding.settingsSkippedEncryptFiles
        val updateValue = {
            valueView.text = getString(
                when (config.skippedEncryptFilesMode) {
                    CRYPTO_SKIPPED_FILES_KEEP -> R.string.skipped_crypto_files_keep
                    CRYPTO_SKIPPED_FILES_DELETE -> R.string.skipped_crypto_files_delete
                    else -> R.string.skipped_crypto_files_ask
                }
            )
        }
        updateValue()
        binding.settingsSkippedEncryptFilesHolder.setOnClickListener {
            showSkippedCryptoFilesSettingsDialog(this) { updateValue() }
        }
    }

    private fun setupAlbumCryptoConflictMode() {
        binding.settingsAlbumCryptoConflict.text = getAlbumCryptoConflictModeText()
        binding.settingsAlbumCryptoConflictHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(ALBUM_CRYPTO_CONFLICT_ASK, getString(R.string.album_crypto_conflict_ask)),
                RadioItem(ALBUM_CRYPTO_CONFLICT_MERGE, getString(R.string.album_crypto_conflict_merge)),
                RadioItem(ALBUM_CRYPTO_CONFLICT_RENAME, getString(R.string.album_crypto_conflict_rename))
            )
            RadioGroupDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.albumCryptoConflictMode,
                titleId = R.string.album_crypto_conflict_mode_label,
            ) {
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

    private fun setupNotifications() {
        fun refreshNotificationPermissionRow() {
            val needsPermission = isTiramisuPlus() && !hasPermission(PERMISSION_POST_NOTIFICATIONS)
            binding.settingsNotificationPermissionHolder.isVisible = needsPermission
            binding.settingsNotificationPermissionAction.setText(
                if (needsPermission) R.string.notification_permission_request else R.string.notification_permission_granted
            )
        }

        refreshNotificationPermissionRow()
        binding.settingsNotificationPermissionHolder.setOnClickListener {
            handleNotificationPermission { refreshNotificationPermissionRow() }
        }

        fun bindSwitch(
            holder: android.view.View,
            switch: org.fossify.commons.views.MyMaterialSwitch,
            getValue: () -> Boolean,
            setValue: (Boolean) -> Unit
        ) {
            switch.isChecked = getValue()
            holder.setOnClickListener {
                switch.toggle()
                setValue(switch.isChecked)
            }
        }

        bindSwitch(
            binding.settingsNotificationsEnabledHolder,
            binding.settingsNotificationsEnabled,
            { config.notificationsEnabled },
            { config.notificationsEnabled = it }
        )
        bindSwitch(
            binding.settingsNotifyCopyMoveHolder,
            binding.settingsNotifyCopyMove,
            { config.notifyCopyMove },
            { config.notifyCopyMove = it }
        )
        bindSwitch(
            binding.settingsNotifyCryptoHolder,
            binding.settingsNotifyCrypto,
            { config.notifyCrypto },
            { config.notifyCrypto = it }
        )
        bindSwitch(
            binding.settingsNotifyDeleteHolder,
            binding.settingsNotifyDelete,
            { config.notifyDelete },
            { config.notifyDelete = it }
        )
        bindSwitch(
            binding.settingsNotifyVirtualAlbumHolder,
            binding.settingsNotifyVirtualAlbum,
            { config.notifyVirtualAlbum },
            { config.notifyVirtualAlbum = it }
        )
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

    private fun setupAutoBackupFavoritesAndVirtualAlbums() {
        binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked = config.autoBackupFavoritesAndVirtualAlbums
        var isCheckingExistingBackups = false
        binding.settingsAutoBackupFavoritesAndVirtualAlbumsHolder.setOnClickListener {
            if (isCheckingExistingBackups) {
                return@setOnClickListener
            }

            val currentlyEnabled = binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked
            if (!currentlyEnabled) {
                binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked = true
                config.autoBackupFavoritesAndVirtualAlbums = true
                config.hasCompletedInitialFavoritesRestore = true
                config.hasCompletedInitialVirtualAlbumsRestore = true
                ensureBackgroundThread {
                    ReferenceBackupManager.saveFavorites(this)
                    VirtualAlbumSyncManager.backupAllPublicAlbums(this)
                }
                return@setOnClickListener
            }

            isCheckingExistingBackups = true
            ensureBackgroundThread {
                val hasFavoritesBackup = ReferenceBackupManager.hasValidFavoritesBackup(this)
                val hasVirtualAlbumsBackup = VirtualAlbumSyncManager.hasValidPublicVirtualAlbumBackups(this)
                runOnUiThread {
                    isCheckingExistingBackups = false
                    if (isDestroyed || isFinishing) {
                        return@runOnUiThread
                    }

                    val disableAutoBackup = {
                        binding.settingsAutoBackupFavoritesAndVirtualAlbums.isChecked = false
                        config.autoBackupFavoritesAndVirtualAlbums = false
                        config.hasCompletedInitialFavoritesRestore = true
                        config.hasCompletedInitialVirtualAlbumsRestore = true
                    }

                    if (!hasFavoritesBackup && !hasVirtualAlbumsBackup) {
                        disableAutoBackup()
                        return@runOnUiThread
                    }

                    val targetText = when {
                        hasFavoritesBackup && hasVirtualAlbumsBackup -> getString(R.string.delete_auto_backup_target_both)
                        hasFavoritesBackup -> getString(R.string.delete_auto_backup_target_favorites)
                        else -> getString(R.string.delete_auto_backup_target_virtual_albums)
                    }
                    val message = getString(R.string.delete_auto_backup_confirm, targetText)
                    DeleteAutoBackupsDialog(
                        activity = this,
                        messageText = message,
                        onYes = {
                            disableAutoBackup()
                            ensureBackgroundThread {
                                if (hasFavoritesBackup) {
                                    ReferenceBackupManager.deleteFavoritesBackup(this)
                                }
                                if (hasVirtualAlbumsBackup) {
                                    VirtualAlbumSyncManager.deletePublicVirtualAlbumBackups(this)
                                }
                            }
                        },
                        onNo = {
                            disableAutoBackup()
                        }
                    )
                }
            }
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
        if (!isSettingUpItems) {
            refreshRowDecorations()
        }
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
                put(HIDE_VAULT_KEY_PRESSES, config.hideVaultKeyPresses)
                put(AUTO_BIOMETRIC_PROMPT_ON_VAULT_RETURN, config.autoBiometricPromptOnVaultReturn)
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
                put(SHOW_EXTRA_VIEWER_ACTIONS, config.showExtraViewerActions)
                put(EXTRA_VIEWER_ACTIONS, config.extraViewerActions)
                put(DELETE_EMPTY_FOLDERS, config.deleteEmptyFolders)
                put(CONFLICT_HANDLING_MODE, config.conflictHandlingMode)
                put(FAST_LARGE_DUPLICATE_CHECK, config.fastLargeDuplicateCheck)
                put(NOTIFICATIONS_ENABLED, config.notificationsEnabled)
                put(NOTIFY_COPY_MOVE, config.notifyCopyMove)
                put(NOTIFY_CRYPTO, config.notifyCrypto)
                put(NOTIFY_DELETE, config.notifyDelete)
                put(NOTIFY_VIRTUAL_ALBUM, config.notifyVirtualAlbum)
                put(DATE_TIE_BREAK_MODE, config.dateTieBreakMode)
                put(ALBUM_CRYPTO_CONFLICT_MODE, config.albumCryptoConflictMode)
                put(SKIPPED_ENCRYPT_FILES_MODE, config.skippedEncryptFilesMode)
                put(KEEP_LAST_MODIFIED, config.keepLastModified)
                put(SKIP_DELETE_CONFIRMATION, config.skipDeleteConfirmation)
                put(DELETE_ORIGINAL_FROM_FAVORITES_AND_VIRTUAL_ALBUMS, config.deleteOriginalFromFavoritesAndVirtualAlbums)
                put(ALLOW_BATCH_RENAMING, config.allowBatchRenaming)
                put(BOTTOM_ACTIONS, config.bottomActions)
                put(VISIBLE_BOTTOM_ACTIONS, config.visibleBottomActions)
                put(SHOW_BOTTOM_ACTION_NAMES, config.showBottomActionNames)
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
                HIDE_VAULT_KEY_PRESSES -> config.hideVaultKeyPresses = value.toBoolean()
                AUTO_BIOMETRIC_PROMPT_ON_VAULT_RETURN -> config.autoBiometricPromptOnVaultReturn = value.toBoolean()
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
                SHOW_EXTRA_VIEWER_ACTIONS -> config.showExtraViewerActions = value.toBoolean()
                EXTRA_VIEWER_ACTIONS -> config.extraViewerActions = value.toInt()
                DELETE_EMPTY_FOLDERS -> config.deleteEmptyFolders = value.toBoolean()
                CONFLICT_HANDLING_MODE -> config.conflictHandlingMode = value.toInt()
                FAST_LARGE_DUPLICATE_CHECK -> config.fastLargeDuplicateCheck = value.toBoolean()
                NOTIFICATIONS_ENABLED -> config.notificationsEnabled = value.toBoolean()
                NOTIFY_COPY_MOVE -> config.notifyCopyMove = value.toBoolean()
                NOTIFY_CRYPTO -> config.notifyCrypto = value.toBoolean()
                NOTIFY_DELETE -> config.notifyDelete = value.toBoolean()
                NOTIFY_VIRTUAL_ALBUM -> config.notifyVirtualAlbum = value.toBoolean()
                DATE_TIE_BREAK_MODE -> config.dateTieBreakMode = value.toInt()
                ALBUM_CRYPTO_CONFLICT_MODE -> config.albumCryptoConflictMode = value.toInt()
                SKIPPED_ENCRYPT_FILES_MODE -> config.skippedEncryptFilesMode = value.toInt()
                KEEP_LAST_MODIFIED -> config.keepLastModified = value.toBoolean()
                SKIP_DELETE_CONFIRMATION -> config.skipDeleteConfirmation = value.toBoolean()
                DELETE_ORIGINAL_FROM_FAVORITES_AND_VIRTUAL_ALBUMS -> config.deleteOriginalFromFavoritesAndVirtualAlbums = value.toBoolean()
                ALLOW_BATCH_RENAMING -> config.allowBatchRenaming = value.toBoolean()
                BOTTOM_ACTIONS -> config.bottomActions = value.toBoolean()
                VISIBLE_BOTTOM_ACTIONS -> config.visibleBottomActions = value.toInt()
                SHOW_BOTTOM_ACTION_NAMES -> config.showBottomActionNames = value.toBoolean()
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
