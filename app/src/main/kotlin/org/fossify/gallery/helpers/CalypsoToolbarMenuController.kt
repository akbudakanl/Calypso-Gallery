package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.annotation.MenuRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config


/**
 * Replaces a [Toolbar]'s stock Android overflow menu with [CalypsoOverflowMenuPopup] while
 * keeping all menu XML definitions and visibility mutations on a standard [Menu] instance.
 *
 * Only currently-visible bar action items plus a single `R.id.calypso_overflow` (`⋮`) action
 * button are projected into `toolbar.menu`, so AppCompat's legacy overflow popup is never created.
 */
class CalypsoToolbarMenuController(
    private val activity: Activity,
    private val toolbar: Toolbar,
    @MenuRes menuRes: Int,
    private val isBarAction: (MenuItem) -> Boolean,
    private val anchorViewProvider: () -> View,
    private val alignRightInsetDpProvider: () -> Float = { 0f },
    private val barIconColorProvider: () -> Int,
    private val onItemSelected: (Int) -> Boolean
) {
    private val backingPopupMenu = PopupMenu(activity, toolbar).apply {
        activity.menuInflater.inflate(menuRes, menu)
    }
    private val overflowPopup = CalypsoOverflowMenuPopup(activity)

    /** Logical full menu inflated from XML; mutate item visibility/titles here, then call [syncToolbar]. */
    val menu: Menu
        get() = backingPopupMenu.menu

    init {
        toolbar.overflowIcon = null
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.calypso_overflow) {
                toggleOverflow()
                true
            } else {
                overflowPopup.dismiss()
                onItemSelected(item.itemId)
            }
        }
    }

    fun syncToolbar() {
        val iconColor = barIconColorProvider()
        val barItems = ArrayList<MenuItem>()
        var hasVisibleOverflow = false

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (!item.isVisible) continue
            if (isBarAction(item)) {
                barItems.add(item)
            } else {
                hasVisibleOverflow = true
            }
        }

        val targetMenu = toolbar.menu
        targetMenu.clear()
        toolbar.overflowIcon = null

        for (item in barItems) {
            targetMenu.add(item.groupId, item.itemId, item.order, item.title).apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                icon = CalypsoOverflowMenuPopup.resolveMenuItemIcon(activity, item, iconColor)
            }
        }

        if (hasVisibleOverflow) {
            val moreDesc = activity.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description)
            targetMenu.add(Menu.NONE, R.id.calypso_overflow, Menu.NONE, moreDesc).apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                icon = CalypsoOverflowMenuPopup.loadTintedDrawable(
                    activity,
                    R.drawable.ic_more_vertical_calypso_vector,
                    iconColor
                )
            }
        } else {
            overflowPopup.dismiss()
        }
    }

    fun toggleOverflow() {
        val visibleOverflow = ArrayList<MenuItem>()
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.isVisible && !isBarAction(item)) {
                visibleOverflow.add(item)
            }
        }
        if (visibleOverflow.isEmpty()) {
            overflowPopup.dismiss()
            return
        }
        val anchor = anchorViewProvider()
        overflowPopup.toggle(
            anchor = anchor,
            items = visibleOverflow,
            alignRightInsetDp = alignRightInsetDpProvider()
        ) { chosen ->
            onItemSelected(chosen.itemId)
        }
    }

    fun dismiss() {
        overflowPopup.dismiss()
    }
}
