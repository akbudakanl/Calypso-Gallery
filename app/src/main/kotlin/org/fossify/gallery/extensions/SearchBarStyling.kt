package org.fossify.gallery.extensions

import android.view.View
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette

private const val LIGHT_SEARCH_BAR_FILL_COLOR = 0x14535353

/**
 * Keeps the original Commons pill search bar shape and layout intact, replacing only its
 * fill tint with Calypso Gallery's neutral surface color (matching Settings on light themes
 * and [AppearancePalette.surface] on dark themes).
 */
fun MySearchMenu.applyCalypsoSearchBarStyle() {
    val palette = AppearanceManager.resolve(context, context.config)
    val isDark = AppearancePalette.contrastRatio(palette.background, android.graphics.Color.WHITE) >
        AppearancePalette.contrastRatio(palette.background, android.graphics.Color.BLACK)
    val fillColor = if (isDark) palette.surface else LIGHT_SEARCH_BAR_FILL_COLOR
    findViewById<View>(org.fossify.commons.R.id.toolbar_container)?.background?.applyColorFilter(fillColor)
}
