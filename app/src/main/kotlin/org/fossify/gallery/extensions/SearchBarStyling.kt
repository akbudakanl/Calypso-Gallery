package org.fossify.gallery.extensions

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.helpers.AppearanceManager
import kotlin.math.roundToInt

private const val SEARCH_BAR_FILL_COLOR = 0x14535353
private const val SEARCH_BAR_CORNER_RADIUS_DP = 10f

/**
 * Builds the shared Calypso search bar background with subtly rounded corners (10dp) and a
 * 1dp theme-aware border so Main, Media, Search, Directory Picker, and Settings share one style.
 */
fun createCalypsoSearchBarBackground(context: Context): GradientDrawable {
    val palette = AppearanceManager.resolve(context, context.config)
    val density = context.resources.displayMetrics.density
    val strokePx = (1f * density).roundToInt().coerceAtLeast(1)
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = SEARCH_BAR_CORNER_RADIUS_DP * density
        setColor(SEARCH_BAR_FILL_COLOR)
        setStroke(strokePx, palette.border)
    }
}

/**
 * Replaces the pill-shaped tinted background applied by Commons' [MySearchMenu.updateColors]
 * with Calypso Gallery's flat rectangular 1dp-bordered search bar surface.
 */
fun MySearchMenu.applyCalypsoSearchBarStyle() {
    findViewById<View>(org.fossify.commons.R.id.toolbar_container)?.background =
        createCalypsoSearchBarBackground(context)
}
