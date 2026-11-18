package org.fossify.gallery.activities

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.FONT_TYPE_CUSTOM
import org.fossify.commons.helpers.FONT_TYPE_MONOSPACE
import org.fossify.commons.helpers.FONT_TYPE_SYSTEM_DEFAULT
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityAppearanceBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.SavedTheme
import org.fossify.gallery.helpers.SavedThemes

/**
 * Fork-local replacement for Commons' CustomizationActivity. Four appearance modes, four
 * editable color roles in Custom mode, saved custom palettes and an always-available font
 * setting.
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
        setupTopAppBar(binding.appearanceAppbar, NavigationIcon.Arrow)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmUnsavedChanges {
                    isEnabled = false
                    finish()
                }
            }
        })

        binding.appearanceThemeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isRefreshing) {
                return@setOnCheckedChangeListener
            }
            val mode = when (checkedId) {
                R.id.appearance_system -> Config.APPEARANCE_SYSTEM
                R.id.appearance_light -> Config.APPEARANCE_LIGHT
                R.id.appearance_dark -> Config.APPEARANCE_DARK
                R.id.appearance_custom -> Config.APPEARANCE_CUSTOM
                else -> return@setOnCheckedChangeListener
            }
            if (mode != Config.APPEARANCE_CUSTOM && hasUnsavedChanges()) {
                // Keep the radio on Custom while the dialog is open.
                isRefreshing = true
                binding.appearanceCustom.isChecked = true
                isRefreshing = false
                confirmUnsavedChanges {
                    config.appearanceMode = mode
                    applyAndRefresh()
                }
            } else {
                if (mode == Config.APPEARANCE_CUSTOM && config.appearanceMode != Config.APPEARANCE_CUSTOM) {
                    seedCustomColorsFromActivePalette()
                }
                config.appearanceMode = mode
                applyAndRefresh()
            }
        }

        binding.appearanceTextColorRow.setOnClickListener {
            pickCustomColor(config.customTextColor) { config.customTextColor = it }
        }
        binding.appearanceBackgroundColorRow.setOnClickListener {
            pickCustomColor(config.customBackgroundColor) { config.customBackgroundColor = it }
        }
        binding.appearancePrimaryColorRow.setOnClickListener {
            pickCustomColor(config.customPrimaryColor) { config.customPrimaryColor = it }
        }
        binding.appearanceAppIconColorRow.setOnClickListener {
            pickCustomColor(config.customAppIconColor) { config.customAppIconColor = it }
        }
        binding.appearanceSavedThemesRow.setOnClickListener {
            showSavedThemes()
        }
        binding.appearanceFontRow.setOnClickListener {
            showFontOptions()
        }

        setupAlbumIconToggles()
        refreshUi()
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

    private fun refreshUi() {
        isRefreshing = true
        val palette = AppearanceManager.apply(this, config)
        val isCustom = config.appearanceMode == Config.APPEARANCE_CUSTOM

        binding.appearanceSystem.isChecked = config.appearanceMode == Config.APPEARANCE_SYSTEM
        binding.appearanceLight.isChecked = config.appearanceMode == Config.APPEARANCE_LIGHT
        binding.appearanceDark.isChecked = config.appearanceMode == Config.APPEARANCE_DARK
        binding.appearanceCustom.isChecked = isCustom

        listOf(
            binding.appearanceDividerColors,
            binding.appearanceCustomColorsSectionLabel,
            binding.appearanceTextColorRow,
            binding.appearanceBackgroundColorRow,
            binding.appearancePrimaryColorRow,
            binding.appearanceAppIconColorRow,
            binding.appearanceSavedThemesRow,
        ).forEach { it.isVisible = isCustom }

        binding.appearanceTextColorSwatch.setBackgroundColor(config.customTextColor)
        binding.appearanceBackgroundColorSwatch.setBackgroundColor(config.customBackgroundColor)
        binding.appearancePrimaryColorSwatch.setBackgroundColor(config.customPrimaryColor)
        // Show the color actually applied to the launcher alias, which may be the nearest
        // supported md_* shade rather than the raw custom value.
        binding.appearanceAppIconColorSwatch.setBackgroundColor(config.appIconColor)

        val tint = ColorStateList.valueOf(palette.primary)
        listOf(
            binding.appearanceSystem,
            binding.appearanceLight,
            binding.appearanceDark,
            binding.appearanceCustom,
        ).forEach {
            it.buttonTintList = tint
            it.setTextColor(palette.text)
        }

        binding.appearanceFontValue.text = when (config.fontType) {
            FONT_TYPE_CUSTOM -> config.fontName.ifEmpty { getString(org.fossify.commons.R.string.custom) }
            FONT_TYPE_MONOSPACE -> getString(org.fossify.commons.R.string.font_monospace)
            else -> getString(R.string.font_system_default)
        }

        updateTextColors(binding.appearanceHolder)
        listOf(
            binding.appearanceThemeSectionLabel,
            binding.appearanceCustomColorsSectionLabel,
            binding.appearanceFontSectionLabel,
            binding.appearanceAlbumIconsSectionLabel,
        ).forEach { it.setTextColor(palette.primary) }
        isRefreshing = false
    }

    private fun applyAndRefresh() {
        val palette = AppearanceManager.apply(this, config)
        AppearanceManager.applyLauncherIcon(this, palette.appIcon)
        config.lastIconColor = config.appIconColor
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
        themes.add(current.copy(name = name))
        SavedThemes.save(config, themes)
        toast(R.string.theme_saved)
        return true
    }

    private fun confirmUnsavedChanges(action: () -> Unit) {
        if (!hasUnsavedChanges()) {
            action()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.discard_changes_question)
            .setPositiveButton(R.string.save_theme) { _, _ ->
                saveCurrentTheme()
                action()
            }
            .setNegativeButton(R.string.continue_without_saving) { _, _ ->
                action()
            }
            .show()
    }

    private fun applySavedTheme(theme: SavedTheme) {
        config.appearanceMode = Config.APPEARANCE_CUSTOM
        config.customTextColor = theme.textColor
        config.customBackgroundColor = theme.backgroundColor
        config.customPrimaryColor = theme.primaryColor
        config.customAppIconColor = theme.appIconColor
        applyAndRefresh()
    }

    private fun showSavedThemes() {
        val themes = SavedThemes.load(config)
        val builder = AlertDialog.Builder(this).setTitle(R.string.saved_themes)
        if (themes.isNotEmpty()) {
            builder.setItems(themes.map { it.name }.toTypedArray()) { _, which ->
                val theme = themes[which]
                confirmUnsavedChanges { applySavedTheme(theme) }
            }
        } else {
            builder.setMessage(R.string.no_saved_themes)
        }
        builder.setPositiveButton(R.string.save_theme) { _, _ -> saveCurrentTheme() }
        builder.show()
    }

    private fun showFontOptions() {
        val labels = arrayOf(
            getString(R.string.font_system_default),
            getString(org.fossify.commons.R.string.font_monospace),
            getString(org.fossify.commons.R.string.select_font_file),
        )
        AlertDialog.Builder(this)
            .setTitle(org.fossify.commons.R.string.app_font)
            .setItems(labels) { _, which ->
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
            .show()
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
