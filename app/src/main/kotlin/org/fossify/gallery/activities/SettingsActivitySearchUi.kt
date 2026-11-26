package org.fossify.gallery.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.text.Editable
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
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.applyThemedCursor
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchAbout
import org.fossify.gallery.extensions.refreshFontRecursively
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.SettingsCardUi

private data class SearchResultEntry(
    @DrawableRes val iconRes: Int,
    val title: String,
    val breadcrumb: String,
    val onClick: () -> Unit,
)

internal fun SettingsActivity.setupSettingsSearch() {
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

/** Re-apply the font to views inflated before the font was changed in Appearance. */
internal fun SettingsActivity.refreshSettingsFont() {
    binding.root.refreshFontRecursively()
}

internal fun SettingsActivity.refreshSettingsSearchColors() {
    val palette = AppearanceManager.resolve(this, config)
    val mutedColor = SettingsCardUi.mutedTextColor(palette)
    binding.settingsSearchContainer.background = SettingsCardUi.createSearchBackground(this, palette)
    binding.settingsSearchContainer.clipToOutline = true
    binding.settingsSearch.setTextColor(palette.text)
    binding.settingsSearch.setHintTextColor(mutedColor)
    // Plain EditText outside MySearchMenu: needs the same palette-aware caret override.
    binding.settingsSearch.applyThemedCursor()
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
internal fun SettingsActivity.applySettingsSearch() {
    if (activeCategory != null) return

    val query = activeSettingsSearch.lowercase()
    binding.settingsSearchClear.isVisible = query.isNotEmpty()
    if (query.isEmpty()) {
        binding.settingsHubCards.isVisible = true
        binding.settingsSearchResultsEyebrow.isVisible = false
        binding.settingsSearchResultsCard.isVisible = false
        binding.settingsSearchEmpty.isVisible = false
        return
    }

    binding.settingsHubCards.isVisible = false
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

private fun SettingsActivity.collectSearchResults(query: String): List<SearchResultEntry> {
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
        R.string.theme_velvet_violet to org.fossify.commons.R.string.theme,
        R.string.theme_soft_coral to org.fossify.commons.R.string.theme,
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
private fun SettingsActivity.buildHubCardRow(
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
        background = SettingsCardUi.createIconChipBackground(this@buildHubCardRow, palette)
        layoutParams = LinearLayout.LayoutParams(chipSize, chipSize).apply { marginEnd = gap }
        addView(
            AppCompatImageView(this@buildHubCardRow).apply {
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
            MyTextView(this@buildHubCardRow).apply {
                text = title
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            },
        )
        if (subtitle != null) {
            addView(
                MyTextView(this@buildHubCardRow).apply {
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

private fun SettingsActivity.isSettingsHolderRow(view: View): Boolean {
    if (view !is ViewGroup || view.id == View.NO_ID) return false
    val name = resources.getResourceEntryName(view.id)
    return name.startsWith("settings_") && name.endsWith("_holder")
}

/**
 * Styles the active screen: in Hub mode, renders the 11 categories across 4 spacing-separated
 * rounded cards with squircle icon chips and chevrons; in Category mode, adds truthful chevrons
 * on sub-screen rows and renders each logical section inside its own rounded card with hairline dividers.
 */
internal fun SettingsActivity.refreshRowDecorations() {
    val palette = AppearanceManager.resolve(this, config)
    val mutedColor = SettingsCardUi.mutedTextColor(palette)
    val chevronTint = ColorStateList.valueOf(mutedColor)

    if (activeCategory == null) {
        val specByKey = categorySpecs.associateBy { it.key }
        fun addCategoryRow(card: LinearLayout, key: String) {
            val spec = specByKey.getValue(key)
            card.addView(
                buildHubCardRow(
                    iconRes = spec.iconRes,
                    title = getString(spec.titleRes),
                    subtitle = null,
                    palette = palette,
                    onClick = { openCategory(spec.key) },
                ),
            )
        }

        val hubCards = listOf(
            binding.settingsHubGeneralCard,
            binding.settingsHubMediaCard,
            binding.settingsHubSecurityStorageCard,
            binding.settingsHubAboutCard,
        )
        hubCards.forEach { it.removeAllViews() }

        binding.settingsHubGeneralCard.addView(
            buildHubCardRow(
                iconRes = R.drawable.ic_section_appearance,
                title = getString(R.string.appearance_title),
                subtitle = null,
                palette = palette,
                onClick = { startActivity(Intent(this, AppearanceActivity::class.java)) },
            ),
        )
        addCategoryRow(binding.settingsHubGeneralCard, SettingsActivity.CATEGORY_APPLICATION)

        addCategoryRow(binding.settingsHubMediaCard, SettingsActivity.CATEGORY_VIEWER)
        addCategoryRow(binding.settingsHubMediaCard, SettingsActivity.CATEGORY_VIDEO)
        addCategoryRow(binding.settingsHubMediaCard, SettingsActivity.CATEGORY_THUMBNAILS)

        addCategoryRow(binding.settingsHubSecurityStorageCard, SettingsActivity.CATEGORY_SECURITY)
        addCategoryRow(binding.settingsHubSecurityStorageCard, SettingsActivity.CATEGORY_FILES)
        addCategoryRow(binding.settingsHubSecurityStorageCard, SettingsActivity.CATEGORY_RECYCLE)
        addCategoryRow(binding.settingsHubSecurityStorageCard, SettingsActivity.CATEGORY_MIGRATING)

        binding.settingsHubAboutCard.addView(
            buildHubCardRow(
                iconRes = R.drawable.ic_section_about,
                title = getString(org.fossify.commons.R.string.about),
                subtitle = null,
                palette = palette,
                onClick = { launchAbout() },
            ),
        )

        hubCards.forEach { SettingsCardUi.styleCardGroup(it, palette, insetStartDp = 60f) }
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
                if (row.getChildAt(j).tag == SettingsActivity.ROW_DECORATION_TAG) {
                    row.removeViewAt(j)
                }
            }
            if (row.isVisible && !hasSwitch(row) && row.id !in SettingsActivity.ACTION_ROW_IDS) {
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

private fun SettingsActivity.createChevron(row: ViewGroup, palette: AppearancePalette): ImageView {
    val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
    val tint = ColorStateList.valueOf(SettingsCardUi.mutedTextColor(palette))
    return AppCompatImageView(this).apply {
        tag = SettingsActivity.ROW_DECORATION_TAG
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
