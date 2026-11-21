package org.fossify.gallery.activities

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.isSystemInDarkMode
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FONT_TYPE_CUSTOM
import org.fossify.commons.helpers.FONT_TYPE_MONOSPACE
import org.fossify.commons.helpers.FONT_TYPE_SYSTEM_DEFAULT
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityAppearanceBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.SavedTheme
import org.fossify.gallery.helpers.SavedThemes
import org.fossify.gallery.helpers.SettingsCardUi

/**
 * Fork-local replacement for Commons' CustomizationActivity. Separates automatic system tracking
 * from first-class palettes (Light, Dark, Midnight Blue, Warm Paper, Deep Forest, Custom),
 * provides custom color pickers with contrast checks, saved themes with swatches, storage backup,
 * font selection, and album-type icon toggles.
 */
class AppearanceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAppearanceBinding::inflate)
    private var isRefreshing = false

    private val fontPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleFontFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.appearanceAppbar),
            padBottomSystem = listOf(binding.appearanceScrollview),
        )
        setupTopAppBar(
            binding.appearanceAppbar,
            NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor(),
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmUnsavedChanges {
                    isEnabled = false
                    finish()
                }
            }
        })

        setupFollowSystemRow()
        setupThemeRows()
        setupCustomColorRows()
        setupFontRow()
        setupAlbumIconToggles()
        setupBackupRow()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(
            binding.appearanceAppbar,
            NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor(),
        )
    }

    private fun setupFollowSystemRow() {
        binding.appearanceFollowSystemHolder.setOnClickListener {
            if (isRefreshing) return@setOnClickListener
            val isCurrentlySystem = config.appearanceMode == Config.APPEARANCE_SYSTEM
            if (!isCurrentlySystem) {
                confirmUnsavedChanges {
                    config.lastManualAppearanceMode = config.appearanceMode
                    config.appearanceMode = Config.APPEARANCE_SYSTEM
                    applyAndRefresh()
                }
            } else {
                val restoredMode = resolveManualFallbackMode()
                config.appearanceMode = restoredMode
                applyAndRefresh()
            }
        }
    }

    private fun resolveManualFallbackMode(): Int {
        val candidate = config.lastManualAppearanceMode
        val validModes = setOf(
            Config.APPEARANCE_LIGHT,
            Config.APPEARANCE_DARK,
            Config.APPEARANCE_NAVY,
            Config.APPEARANCE_PAPER,
            Config.APPEARANCE_FOREST,
            Config.APPEARANCE_CUSTOM,
        )
        if (candidate in validModes) {
            return candidate
        }
        return if (isSystemInDarkMode()) Config.APPEARANCE_DARK else Config.APPEARANCE_LIGHT
    }

    private fun setupThemeRows() {
        val rows = listOf(
            binding.appearanceLightRow to Config.APPEARANCE_LIGHT,
            binding.appearanceDarkRow to Config.APPEARANCE_DARK,
            binding.appearanceNavyRow to Config.APPEARANCE_NAVY,
            binding.appearancePaperRow to Config.APPEARANCE_PAPER,
            binding.appearanceForestRow to Config.APPEARANCE_FOREST,
            binding.appearanceCustomRow to Config.APPEARANCE_CUSTOM,
        )
        for ((row, mode) in rows) {
            row.setOnClickListener {
                if (isRefreshing) return@setOnClickListener
                if (mode != Config.APPEARANCE_CUSTOM && hasUnsavedChanges()) {
                    confirmUnsavedChanges {
                        selectManualTheme(mode)
                    }
                } else {
                    selectManualTheme(mode)
                }
            }
        }
    }

    private fun selectManualTheme(mode: Int) {
        if (mode == Config.APPEARANCE_CUSTOM && config.appearanceMode != Config.APPEARANCE_CUSTOM) {
            seedCustomColorsFromActivePalette()
        }
        config.lastManualAppearanceMode = mode
        config.appearanceMode = mode
        applyAndRefresh()
    }

    private fun setupCustomColorRows() {
        binding.appearanceTextColorRow.setOnClickListener {
            pickCustomColor(config.customTextColor) { config.customTextColor = it }
        }
        binding.appearanceBackgroundColorRow.setOnClickListener {
            pickCustomColor(config.customBackgroundColor) { config.customBackgroundColor = it }
        }
        binding.appearancePrimaryColorRow.setOnClickListener {
            pickCustomColor(config.customPrimaryColor) { config.customPrimaryColor = it }
        }
        binding.appearanceSavedThemesRow.setOnClickListener {
            showSavedThemes()
        }
    }

    private fun setupFontRow() {
        binding.appearanceFontRow.setOnClickListener {
            showFontOptions()
        }
    }

    private fun setupAlbumIconToggles() {
        binding.appearanceShowVirtualAlbumIcon.isChecked = config.showVirtualAlbumIcon
        binding.appearanceShowVirtualAlbumIconHolder.setOnClickListener {
            binding.appearanceShowVirtualAlbumIcon.toggle()
            config.showVirtualAlbumIcon = binding.appearanceShowVirtualAlbumIcon.isChecked
        }
        binding.appearanceShowFavoritesAlbumIcon.isChecked = config.showFavoritesAlbumIcon
        binding.appearanceShowFavoritesAlbumIconHolder.setOnClickListener {
            binding.appearanceShowFavoritesAlbumIcon.toggle()
            config.showFavoritesAlbumIcon = binding.appearanceShowFavoritesAlbumIcon.isChecked
        }
        binding.appearanceShowExcludedAlbumIcon.isChecked = config.showExcludedAlbumIcon
        binding.appearanceShowExcludedAlbumIconHolder.setOnClickListener {
            binding.appearanceShowExcludedAlbumIcon.toggle()
            config.showExcludedAlbumIcon = binding.appearanceShowExcludedAlbumIcon.isChecked
        }
        binding.appearanceShowHiddenAlbumIcon.isChecked = config.showHiddenAlbumIcon
        binding.appearanceShowHiddenAlbumIconHolder.setOnClickListener {
            binding.appearanceShowHiddenAlbumIcon.toggle()
            config.showHiddenAlbumIcon = binding.appearanceShowHiddenAlbumIcon.isChecked
        }
    }

    private fun setupBackupRow() {
        binding.appearanceBackupSavedThemes.isChecked = config.autoBackupSavedThemes
        binding.appearanceBackupSavedThemesHolder.setOnClickListener {
            binding.appearanceBackupSavedThemes.toggle()
            val enabled = binding.appearanceBackupSavedThemes.isChecked
            config.autoBackupSavedThemes = enabled
            if (enabled) {
                SavedThemes.backupAllThemesToStorage(this, SavedThemes.load(config))
            }
        }
    }

    private fun refreshUi() {
        isRefreshing = true
        val palette = AppearanceManager.apply(this, config)
        val mode = config.appearanceMode
        val isSystem = mode == Config.APPEARANCE_SYSTEM
        val isCustom = mode == Config.APPEARANCE_CUSTOM

        binding.appearanceFollowSystem.isChecked = isSystem

        val customPreviewPalette = AppearancePalette.fromCustom(
            text = config.customTextColor,
            background = config.customBackgroundColor,
            primary = config.customPrimaryColor,
            secondary = config.customAccentColor,
            appIcon = config.customAppIconColor,
        )
        val themeEntries = listOf(
            Triple(binding.appearanceLightRow, binding.appearanceLightSwatch1 to binding.appearanceLightSwatch2, AppearancePalette.LIGHT to (binding.appearanceLightRadio to Config.APPEARANCE_LIGHT)),
            Triple(binding.appearanceDarkRow, binding.appearanceDarkSwatch1 to binding.appearanceDarkSwatch2, AppearancePalette.DARK to (binding.appearanceDarkRadio to Config.APPEARANCE_DARK)),
            Triple(binding.appearanceNavyRow, binding.appearanceNavySwatch1 to binding.appearanceNavySwatch2, AppearancePalette.NAVY to (binding.appearanceNavyRadio to Config.APPEARANCE_NAVY)),
            Triple(binding.appearancePaperRow, binding.appearancePaperSwatch1 to binding.appearancePaperSwatch2, AppearancePalette.PAPER to (binding.appearancePaperRadio to Config.APPEARANCE_PAPER)),
            Triple(binding.appearanceForestRow, binding.appearanceForestSwatch1 to binding.appearanceForestSwatch2, AppearancePalette.FOREST to (binding.appearanceForestRadio to Config.APPEARANCE_FOREST)),
            Triple(binding.appearanceCustomRow, binding.appearanceCustomSwatch1 to binding.appearanceCustomSwatch2, customPreviewPalette to (binding.appearanceCustomRadio to Config.APPEARANCE_CUSTOM)),
        )
        for ((row, swatches, paletteAndRadio) in themeEntries) {
            val (entryPalette, radioAndMode) = paletteAndRadio
            val (radioView, entryMode) = radioAndMode
            val selected = !isSystem && mode == entryMode
            row.alpha = if (isSystem) 0.65f else 1f
            swatches.first.background = SettingsCardUi.createSwatchDrawable(this, entryPalette.background, palette.border, 5f)
            swatches.second.background = SettingsCardUi.createSwatchDrawable(this, entryPalette.primary, palette.border, 5f)
            radioView.background = SettingsCardUi.createRadioCircleDrawable(this, selected, palette)
        }

        binding.appearanceCustomColorsSectionLabel.isVisible = isCustom
        binding.appearanceCustomColorsCard.isVisible = isCustom
        binding.appearanceTextColorSwatch.background = SettingsCardUi.createSwatchDrawable(this, config.customTextColor, palette.border, 7f)
        binding.appearanceBackgroundColorSwatch.background = SettingsCardUi.createSwatchDrawable(this, config.customBackgroundColor, palette.border, 7f)
        binding.appearancePrimaryColorSwatch.background = SettingsCardUi.createSwatchDrawable(this, config.customPrimaryColor, palette.border, 7f)

        binding.appearanceFontValue.text = when (config.fontType) {
            FONT_TYPE_CUSTOM -> config.fontName.ifEmpty { getString(org.fossify.commons.R.string.custom) }
            FONT_TYPE_MONOSPACE -> getString(org.fossify.commons.R.string.font_monospace)
            else -> getString(R.string.font_system_default)
        }

        updateTextColors(binding.appearanceHolder)

        val mutedColor = SettingsCardUi.mutedTextColor(palette)
        binding.appearanceFollowSystemSummary.setTextColor(mutedColor)
        binding.appearanceFontValue.setTextColor(mutedColor)

        val chevronTint = ColorStateList.valueOf(mutedColor)
        binding.appearanceSavedThemesChevron.imageTintList = chevronTint
        binding.appearanceFontChevron.imageTintList = chevronTint

        SettingsCardUi.styleCardGroup(binding.appearanceAutoCard, palette, eyebrow = binding.appearanceAutoSectionLabel)
        SettingsCardUi.styleCardGroup(binding.appearanceThemeCard, palette, eyebrow = binding.appearanceThemeSectionLabel)
        if (isCustom) {
            SettingsCardUi.styleCardGroup(binding.appearanceCustomColorsCard, palette, eyebrow = binding.appearanceCustomColorsSectionLabel)
        }
        SettingsCardUi.styleEyebrow(binding.appearancePreviewSectionLabel, palette)
        SettingsCardUi.styleCardGroup(binding.appearanceFontCard, palette, eyebrow = binding.appearanceFontSectionLabel)
        SettingsCardUi.styleCardGroup(binding.appearanceAlbumIconsCard, palette, eyebrow = binding.appearanceAlbumIconsSectionLabel)
        SettingsCardUi.styleCardGroup(binding.appearanceBackupCard, palette, eyebrow = binding.appearanceBackupSectionLabel)

        // Live theme preview card ("Your memories, your colors" + "Sample button" + Custom-only contrast)
        val density = resources.displayMetrics.density
        binding.appearancePreviewCard.background = SettingsCardUi.createCardBackground(this, palette, 18f)
        binding.appearancePreviewCard.clipToOutline = true
        binding.appearancePreviewText.setTextColor(palette.text)
        binding.appearancePreviewButton.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(palette.primary)
        }
        binding.appearancePreviewButton.setTextColor(palette.onPrimary)

        val textContrast = AppearancePalette.contrastRatio(palette.text, palette.background)
        val buttonContrast = AppearancePalette.contrastRatio(palette.onPrimary, palette.primary)
        val lowContrast = textContrast < 4.5 || buttonContrast < 4.5
        binding.appearanceContrastWarning.isVisible = isCustom
        if (isCustom) {
            if (lowContrast) {
                binding.appearanceContrastWarning.text =
                    getString(R.string.appearance_contrast_warning, textContrast, buttonContrast)
                binding.appearanceContrastWarning.setTextColor(SettingsCardUi.dangerColor(palette))
                binding.appearanceContrastWarning.setTypeface(binding.appearanceContrastWarning.typeface, Typeface.BOLD)
            } else {
                binding.appearanceContrastWarning.text =
                    getString(R.string.appearance_contrast_ok, textContrast, buttonContrast)
                binding.appearanceContrastWarning.setTextColor(palette.secondary)
                binding.appearanceContrastWarning.setTypeface(null, Typeface.NORMAL)
            }
        }
        isRefreshing = false
    }

    private fun applyAndRefresh() {
        AppearanceManager.apply(this, config)
        recreate()
    }

    private fun pickCustomColor(initialColor: Int, save: (Int) -> Unit) {
        ColorPickerDialog(
            this,
            initialColor,
            false,
            false,
            { },
            { positive, color ->
                if (positive) {
                    if (config.appearanceMode != Config.APPEARANCE_CUSTOM) {
                        seedCustomColorsFromActivePalette()
                    }
                    config.lastManualAppearanceMode = Config.APPEARANCE_CUSTOM
                    config.appearanceMode = Config.APPEARANCE_CUSTOM
                    save(color)
                    applyAndRefresh()
                }
            },
        )
    }

    /**
     * Entering Custom must start from the palette that is currently active. Otherwise stale
     * custom values would make the app ask to save "changes" the user never made.
     */
    private fun seedCustomColorsFromActivePalette() {
        val active = AppearanceManager.resolve(this, config)
        config.customTextColor = active.text
        config.customBackgroundColor = active.background
        config.customPrimaryColor = active.primary
        config.customAccentColor = active.secondary
        config.customAppIconColor = active.appIcon
    }

    private fun currentCustomColors(): SavedTheme = SavedTheme(
        name = "",
        textColor = config.customTextColor,
        backgroundColor = config.customBackgroundColor,
        primaryColor = config.customPrimaryColor,
        appIconColor = config.customAppIconColor,
    )

    private fun isCurrentBuiltInOrSaved(): Boolean {
        val current = currentCustomColors()
        if (SavedThemes.isBuiltIn(
                current.textColor,
                current.backgroundColor,
                current.primaryColor,
                current.appIconColor,
            )
        ) {
            return true
        }
        return SavedThemes.load(config).any {
            it.matches(
                current.textColor,
                current.backgroundColor,
                current.primaryColor,
                current.appIconColor,
            )
        }
    }

    private fun hasUnsavedChanges(): Boolean =
        config.appearanceMode == Config.APPEARANCE_CUSTOM && !isCurrentBuiltInOrSaved()

    private fun saveCurrentTheme(): Boolean {
        val current = currentCustomColors()
        if (isCurrentBuiltInOrSaved()) {
            toast(R.string.theme_already_saved)
            return false
        }
        val themes = SavedThemes.load(config)
        val name = "${getString(R.string.custom_theme_name)} ${themes.size + 1}"
        val saved = current.copy(name = name)
        themes.add(saved)
        SavedThemes.save(config, themes)
        SavedThemes.backupThemeToStorage(this, saved)
        toast(R.string.theme_saved)
        return true
    }

    private fun confirmUnsavedChanges(action: () -> Unit) {
        if (!hasUnsavedChanges()) {
            action()
            return
        }
        val padding = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val messageView = MyTextView(this).apply {
            setPadding(padding, padding, padding, 0)
            setText(R.string.discard_changes_question)
        }
        getAlertDialogBuilder()
            .setPositiveButton(R.string.save_theme) { _, _ ->
                saveCurrentTheme()
                action()
            }
            .setNegativeButton(R.string.continue_without_saving) { _, _ ->
                action()
            }
            .apply {
                setupDialogStuff(messageView, this)
            }
    }

    private fun applySavedTheme(theme: SavedTheme) {
        config.lastManualAppearanceMode = Config.APPEARANCE_CUSTOM
        config.appearanceMode = Config.APPEARANCE_CUSTOM
        config.customTextColor = theme.textColor
        config.customBackgroundColor = theme.backgroundColor
        config.customPrimaryColor = theme.primaryColor
        config.customAppIconColor = theme.appIconColor
        applyAndRefresh()
    }

    private fun buildThemedDialogListView(items: List<String>, onItemClick: (Int) -> Unit): ListView {
        val palette = AppearanceManager.resolve(this, config)
        return ListView(this).apply {
            adapter = object : ArrayAdapter<String>(this@AppearanceActivity, android.R.layout.simple_list_item_1, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    (view as? TextView)?.setTextColor(palette.text)
                    return view
                }
            }
            divider = ColorDrawable(ColorUtils.setAlphaComponent(palette.text, 0x26))
            dividerHeight = 1
            setOnItemClickListener { _, _, which, _ -> onItemClick(which) }
        }
    }

    private fun showSavedThemes() {
        SavedThemes.restoreMissingThemesFromStorage(this, config)
        val themes = SavedThemes.load(config)
        val palette = AppearanceManager.resolve(this, config)
        var dialog: AlertDialog? = null
        val contentView: View = if (themes.isNotEmpty()) {
            buildSavedThemesListView(themes, palette) { which ->
                dialog?.dismiss()
                val theme = themes[which]
                confirmUnsavedChanges { applySavedTheme(theme) }
            }
        } else {
            val padding = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            MyTextView(this).apply {
                setPadding(padding, padding, padding, 0)
                setText(R.string.no_saved_themes)
                setTextColor(palette.text)
            }
        }
        getAlertDialogBuilder()
            .setPositiveButton(R.string.save_theme) { _, _ -> saveCurrentTheme() }
            .apply {
                setupDialogStuff(contentView, this, R.string.saved_themes) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun buildSavedThemesListView(
        themes: List<SavedTheme>,
        palette: AppearancePalette,
        onItemClick: (Int) -> Unit,
    ): ListView {
        val density = resources.displayMetrics.density
        val padH = (18f * density).toInt()
        val padV = (13f * density).toInt()
        val swatchSize = (14f * density).toInt()
        val swatchGap = (4f * density).toInt()
        val labelGap = (12f * density).toInt()
        val radioSize = (18f * density).toInt()

        return ListView(this).apply {
            adapter = object : BaseAdapter() {
                override fun getCount(): Int = themes.size
                override fun getItem(position: Int): SavedTheme = themes[position]
                override fun getItemId(position: Int): Long = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val theme = themes[position]
                    val derived = AppearancePalette.fromCustom(
                        text = theme.textColor,
                        background = theme.backgroundColor,
                        primary = theme.primaryColor,
                        secondary = 0,
                        appIcon = theme.appIconColor,
                    )
                    val isSelected = config.appearanceMode == Config.APPEARANCE_CUSTOM &&
                        theme.matches(
                            config.customTextColor,
                            config.customBackgroundColor,
                            config.customPrimaryColor,
                            config.customAppIconColor,
                        )

                    val row = (convertView as? LinearLayout) ?: LinearLayout(this@AppearanceActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(padH, padV, padH, padV)

                        addView(View(context), LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                            marginEnd = swatchGap
                        })
                        addView(View(context), LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                            marginEnd = swatchGap
                        })
                        addView(View(context), LinearLayout.LayoutParams(swatchSize, swatchSize).apply {
                            marginEnd = labelGap
                        })
                        addView(MyTextView(context).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = labelGap
                        })
                        addView(View(context), LinearLayout.LayoutParams(radioSize, radioSize))
                    }

                    row.getChildAt(0).background = SettingsCardUi.createSwatchDrawable(context, derived.background, palette.border, 5f)
                    row.getChildAt(1).background = SettingsCardUi.createSwatchDrawable(context, derived.surface, palette.border, 5f)
                    row.getChildAt(2).background = SettingsCardUi.createSwatchDrawable(context, derived.primary, palette.border, 5f)
                    val titleView = row.getChildAt(3) as MyTextView
                    titleView.text = theme.name
                    titleView.setTextColor(palette.text)
                    row.getChildAt(4).background = SettingsCardUi.createRadioCircleDrawable(context, isSelected, palette)
                    return row
                }
            }
            divider = ColorDrawable(SettingsCardUi.dividerColor(palette))
            dividerHeight = (1f * density).toInt().coerceAtLeast(1)
            setOnItemClickListener { _, _, which, _ -> onItemClick(which) }
        }
    }

    private fun showFontOptions() {
        val labels = listOf(
            getString(R.string.font_system_default),
            getString(org.fossify.commons.R.string.font_monospace),
            getString(org.fossify.commons.R.string.select_font_file),
        )
        var dialog: AlertDialog? = null
        val listView = buildThemedDialogListView(labels) { which ->
            dialog?.dismiss()
            when (which) {
                0 -> {
                    config.fontType = FONT_TYPE_SYSTEM_DEFAULT
                    config.fontName = ""
                    applyAndRefresh()
                }
                1 -> {
                    config.fontType = FONT_TYPE_MONOSPACE
                    config.fontName = ""
                    applyAndRefresh()
                }
                2 -> fontPicker.launch("*/*")
            }
        }
        getAlertDialogBuilder().apply {
            setupDialogStuff(listView, this, org.fossify.commons.R.string.app_font) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun handleFontFile(uri: Uri) {
        val rawName = uri.lastPathSegment?.substringAfterLast('/') ?: "custom_font.ttf"
        val fileName = if (rawName.contains('.')) rawName else "$rawName.ttf"
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }
        if (!FontHelper.saveFontData(this, bytes, fileName)) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }
        val magic = bytes.copyOfRange(0, minOf(4, bytes.size))
        val validMagic = magic.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
            String(magic) == "OTTO" || String(magic) == "ttcf" || String(magic) == "wOFF"
        val valid = validMagic && try {
            FontHelper.getTypeface(this, FONT_TYPE_CUSTOM, fileName)
            true
        } catch (_: Exception) {
            false
        }
        if (!valid) {
            FontHelper.deleteFont(this, fileName)
            toast(org.fossify.commons.R.string.invalid_font_file)
            return
        }
        config.fontType = FONT_TYPE_CUSTOM
        config.fontName = fileName
        applyAndRefresh()
    }
}
