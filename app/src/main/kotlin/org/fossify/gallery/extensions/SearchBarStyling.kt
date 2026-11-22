package org.fossify.gallery.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.AppBarLayout
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.helpers.AppearanceManager

/**
 * Shared Calypso search bar fill color across Main, Media, Search, Directory Picker, and Settings:
 * uses [AppearancePalette.surface] so every theme (Dark, Light, Midnight Blue, Warm Paper, Deep
 * Forest, Custom) renders a matching elevated search container.
 */
fun resolveCalypsoSearchBarFillColor(context: Context): Int {
    return AppearanceManager.resolve(context, context.config).surface
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
 * seamless [AppearancePalette.background] behind the bar, [AppearancePalette.surface] fill with a
 * crisp 1dp [AppearancePalette.border] stroke, and subtle secondary tint on the magnifying glass
 * and hint text.
 */
fun MySearchMenu.applyCalypsoSearchBarStyle() {
    val palette = AppearanceManager.resolve(context, context.config)
    val fillColor = palette.surface
    val borderColor = palette.border
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
    val hasCalypsoOverflow = toolbar.menu?.findItem(R.id.calypso_overflow) != null
    toolbar.overflowIcon = if (hasCalypsoOverflow) {
        null
    } else {
        ContextCompat.getDrawable(context, R.drawable.ic_more_vertical_calypso_vector)
            ?.mutate()?.apply { setTintList(ColorStateList.valueOf(actionIconColor)) }
    }
    toolbar.menu?.findItem(R.id.calypso_overflow)?.icon = ContextCompat.getDrawable(context, R.drawable.ic_more_vertical_calypso_vector)
        ?.mutate()?.apply { setTintList(ColorStateList.valueOf(actionIconColor)) }
    toolbar.menu?.findItem(R.id.sort)?.icon = ContextCompat.getDrawable(context, R.drawable.ic_sort_view_calypso_vector)
        ?.mutate()?.apply { setTintList(ColorStateList.valueOf(actionIconColor)) }

    // The overflow button is an ImageView owned by the toolbar/ActionMenuView. Some AppCompat
    // paths tint it with a stock white ColorStateList, which made it invisible in Light mode.
    // Refresh every icon view with the same color as the magnifier and sort icons, including
    // views created after this call.
    fun tintIcons(view: View) {
        if (view is ImageView && view.id != org.fossify.commons.R.id.top_toolbar_search_icon) {
            view.imageTintList = ColorStateList.valueOf(actionIconColor)
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) tintIcons(view.getChildAt(i))
        }
    }
    tintIcons(toolbar)
    toolbar.post { tintIcons(toolbar) }
}

/**
 * Keeps the search bar / album header inside [MySearchMenu] (an [AppBarLayout]) pinned at the
 * top. Any inherited scroll flags are cleared so the bar can never collapse into the empty strip
 * that appeared when the header was allowed to scroll away.
 */
fun MySearchMenu.keepHeaderPinned() {
    val container = findViewById<View>(org.fossify.commons.R.id.search_bar_container) ?: return
    val params = container.layoutParams as? AppBarLayout.LayoutParams ?: return
    params.scrollFlags = 0
    container.layoutParams = params
}
