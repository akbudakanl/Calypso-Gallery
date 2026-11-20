package org.fossify.gallery.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.core.content.ContextCompat
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette

private val DARK_SEARCH_BAR_FILL = Color.parseColor("#18181B")
private val DARK_SEARCH_BAR_BORDER = Color.parseColor("#27272A")

/**
 * Shared Calypso search bar fill color across Main, Media, Search, Directory Picker, and Settings:
 * exact #18181B neutral gray in dark mode (matching 01-gallery) and [AppearancePalette.surface] in light mode.
 */
fun resolveCalypsoSearchBarFillColor(context: Context): Int {
    val palette = AppearanceManager.resolve(context, context.config)
    val isDark = AppearancePalette.contrastRatio(palette.background, Color.WHITE) >
        AppearancePalette.contrastRatio(palette.background, Color.BLACK)
    return if (isDark) DARK_SEARCH_BAR_FILL else palette.surface
}

/**
 * Magnifying-glass color shared by every Calypso search bar. While a search is expanded the icon
 * uses [AppearancePalette.text] (black in light mode); idle bars fall back to the softer
 * [AppearancePalette.secondary]. Keeping this in one place makes the Settings search field use
 * exactly the same icon color as the Main/Media/Search pill bars.
 */
fun resolveCalypsoSearchIconColor(context: Context, isSearchOpen: Boolean = true): Int {
    val palette = AppearanceManager.resolve(context, context.config)
    return if (isSearchOpen) palette.text else palette.secondary
}

/**
 * Applies Calypso Gallery's refined pill search bar styling:
 * seamless [AppearancePalette.background] behind the bar, exact #18181B dark fill (or light surface)
 * with a crisp 1dp border, and subtle secondary tint on the magnifying glass and hint text.
 */
fun MySearchMenu.applyCalypsoSearchBarStyle() {
    val palette = AppearanceManager.resolve(context, context.config)
    val isDark = AppearancePalette.contrastRatio(palette.background, Color.WHITE) >
        AppearancePalette.contrastRatio(palette.background, Color.BLACK)
    val fillColor = if (isDark) DARK_SEARCH_BAR_FILL else palette.surface
    val borderColor = if (isDark) DARK_SEARCH_BAR_BORDER else palette.border
    val density = context.resources.displayMetrics.density
    val strokeWidth = (1f * density).toInt().coerceAtLeast(1)

    setBackgroundColor(palette.background)

    findViewById<View>(org.fossify.commons.R.id.toolbar_container)?.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 28f * density
        setColor(fillColor)
        setStroke(strokeWidth, borderColor)
    }

    findViewById<ImageView>(org.fossify.commons.R.id.top_toolbar_search_icon)?.let { icon ->
        icon.imageTintList = ColorStateList.valueOf(resolveCalypsoSearchIconColor(context, isSearchOpen))
    }

    findViewById<EditText>(org.fossify.commons.R.id.top_toolbar_search)?.let { input ->
        input.setTextColor(palette.text)
        input.setHintTextColor(palette.secondary)
    }

    val actionIconColor = resolveCalypsoSearchIconColor(context, isSearchOpen)
    val toolbar = requireToolbar()
    toolbar.overflowIcon = ContextCompat.getDrawable(context, R.drawable.ic_more_vertical_calypso_vector)
        ?.mutate()?.apply { setTint(actionIconColor) }
    toolbar.menu?.findItem(R.id.sort)?.icon = ContextCompat.getDrawable(context, R.drawable.ic_sort_view_calypso_vector)
        ?.mutate()?.apply { setTint(actionIconColor) }
}
