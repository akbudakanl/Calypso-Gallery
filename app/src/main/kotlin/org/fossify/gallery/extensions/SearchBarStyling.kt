package org.fossify.gallery.extensions

import android.content.Context
import android.graphics.Color
import android.view.View
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette

private const val LIGHT_SEARCH_BAR_FILL_COLOR = 0x14535353

/**
 * Shared Calypso search bar fill color across Main, Media, Search, Directory Picker, and Settings:
 * subtle translucent neutral gray on light themes and [AppearancePalette.surface] on dark themes.
 */
fun resolveCalypsoSearchBarFillColor(context: Context): Int {
    val palette = AppearanceManager.resolve(context, context.config)
    val isDark = AppearancePalette.contrastRatio(palette.background, Color.WHITE) >
        AppearancePalette.contrastRatio(palette.background, Color.BLACK)
    return if (isDark) palette.surface else LIGHT_SEARCH_BAR_FILL_COLOR
}

/**
 * Keeps the original Commons pill search bar shape and layout intact, replacing only its
 * fill tint with Calypso Gallery's neutral surface color.
 */
fun MySearchMenu.applyCalypsoSearchBarStyle() {
    findViewById<View>(org.fossify.commons.R.id.toolbar_container)
        ?.background
        ?.applyColorFilter(resolveCalypsoSearchBarFillColor(context))
}
