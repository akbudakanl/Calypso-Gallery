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
 * Menu group and tinted icon resolution extracted from [CalypsoOverflowMenuPopup].
 */
internal object CalypsoOverflowMenuIcons {
    fun resolveMenuItemGroup(itemId: Int): String? {
        return when (itemId) {
            // Main & Media menus
            R.id.sort,
            R.id.show_all,
            R.id.show_albums,
            R.id.refresh_menu -> "primary"

            R.id.filter,
            R.id.toggle_filename,
            R.id.change_order,
            R.id.change_view_type,
            R.id.group -> "view"

            R.id.temporarily_show_hidden_folders,
            R.id.stop_showing_hidden_folders,
            R.id.temporarily_show_excluded,
            R.id.stop_showing_excluded,
            R.id.temporarily_show_hidden,
            R.id.stop_showing_hidden -> "visibility"

            R.id.create_new_folder,
            R.id.open_recycle_bin,
            R.id.restore_all_files,
            R.id.empty_recycle_bin,
            R.id.empty_disable_recycle_bin,
            R.id.slideshow -> "manage"

            // ViewPager (Fullscreen viewer) menu
            R.id.menu_shuffle,
            R.id.menu_properties,
            R.id.menu_rename -> "viewer_info"

            R.id.menu_copy_to_clipboard,
            R.id.menu_unhide,
            R.id.menu_hide,
            R.id.menu_copy_to,
            R.id.menu_move_to,
            R.id.menu_add_to_virtual_album,
            R.id.menu_remove_from_virtual_album,
            R.id.menu_restore_file -> "viewer_file"

            R.id.menu_rotate,
            R.id.menu_change_orientation -> "viewer_display"

            R.id.menu_open_with,
            R.id.menu_set_as,
            R.id.menu_slideshow -> "viewer_external"

            R.id.menu_show_on_map,
            R.id.menu_resize,
            R.id.menu_print,
            R.id.menu_create_shortcut -> "viewer_extra"

            R.id.menu_share,
            R.id.menu_edit,
            R.id.menu_delete -> "viewer_fallback"

            // CAB (Selection Action Mode) menus
            R.id.cab_properties,
            R.id.cab_rename,
            R.id.cab_edit,
            R.id.cab_change_cover_image,
            R.id.cab_select_photo,
            R.id.cab_use_default -> "cab_info"

            R.id.cab_hide,
            R.id.cab_unhide,
            R.id.cab_hide_folder,
            R.id.cab_unhide_folder,
            R.id.cab_exclude,
            R.id.cab_unexclude,
            R.id.cab_lock,
            R.id.cab_unlock,
            R.id.cab_copy_to,
            R.id.cab_move_to,
            R.id.cab_add_to_virtual_album,
            R.id.cab_remove_from_virtual_album,
            R.id.cab_add_to_favorites,
            R.id.cab_remove_from_favorites,
            R.id.cab_restore_recycle_bin_files,
            R.id.cab_empty_recycle_bin,
            R.id.cab_empty_disable_recycle_bin -> "cab_file"

            R.id.cab_rotate,
            R.id.cab_resize,
            R.id.cab_open_with,
            R.id.cab_set_as,
            R.id.cab_create_shortcut,
            R.id.cab_change_order -> "cab_transform"

            R.id.cab_select_all -> "cab_select"

            else -> null
        }
    }

    fun loadTintedDrawable(context: Context, resId: Int, tintColor: Int): Drawable? {
        return AppCompatResources.getDrawable(context, resId)?.mutate()?.apply {
            colorFilter = null
            setTintList(ColorStateList.valueOf(tintColor))
        }
    }

    fun resolveMenuItemIcon(context: Context, item: MenuItem, tintColor: Int): Drawable? {
        val resId = when (item.itemId) {
            // Main & Media menus
            R.id.sort -> R.drawable.ic_sort_view_calypso_vector
            R.id.show_all -> R.drawable.ic_files_vector
            R.id.show_albums -> R.drawable.ic_folders_vector
            R.id.refresh_menu -> R.drawable.ic_refresh_calypso_vector
            R.id.filter -> R.drawable.ic_filter_funnel_calypso_vector
            R.id.toggle_filename -> R.drawable.ic_filename_calypso_vector
            R.id.change_order -> org.fossify.commons.R.drawable.ic_drag_handle_vector
            R.id.change_view_type -> R.drawable.ic_view_type_calypso_vector
            R.id.group -> R.drawable.ic_group_calypso_vector
            R.id.temporarily_show_hidden_folders,
            R.id.stop_showing_hidden_folders -> R.drawable.ic_album_hidden_vector
            R.id.temporarily_show_excluded,
            R.id.stop_showing_excluded -> R.drawable.ic_album_excluded_vector
            R.id.temporarily_show_hidden -> R.drawable.ic_encrypt_calypso_vector
            R.id.stop_showing_hidden -> org.fossify.commons.R.drawable.ic_unhide_vector
            R.id.create_new_folder -> R.drawable.ic_new_folder_calypso_vector
            R.id.open_recycle_bin,
            R.id.empty_recycle_bin,
            R.id.empty_disable_recycle_bin -> org.fossify.commons.R.drawable.ic_delete_vector
            R.id.restore_all_files -> org.fossify.commons.R.drawable.ic_reset_vector
            R.id.slideshow,
            R.id.menu_slideshow -> R.drawable.ic_slideshow_vector
            R.id.settings -> R.drawable.ic_settings_tune

            // ViewPager (Fullscreen viewer) menu
            R.id.menu_save_as -> org.fossify.commons.R.drawable.ic_check_vector
            R.id.menu_add_to_favorites -> R.drawable.ic_star_outline_calypso_vector
            R.id.menu_remove_from_favorites -> R.drawable.ic_star_calypso_vector
            R.id.menu_shuffle -> R.drawable.ic_shuffle_calypso_vector
            R.id.menu_properties -> org.fossify.commons.R.drawable.ic_info_vector
            R.id.menu_rename -> org.fossify.commons.R.drawable.ic_rename_vector
            R.id.menu_copy_to_clipboard -> R.drawable.ic_clipboard_calypso_vector
            R.id.menu_hide -> R.drawable.ic_encrypt_calypso_vector
            R.id.menu_unhide -> org.fossify.commons.R.drawable.ic_unhide_vector
            R.id.menu_copy_to -> org.fossify.commons.R.drawable.ic_copy_vector
            R.id.menu_move_to -> org.fossify.commons.R.drawable.ic_move_vector
            R.id.menu_add_to_virtual_album -> R.drawable.ic_album_virtual_vector
            R.id.menu_remove_from_virtual_album -> org.fossify.commons.R.drawable.ic_minus_circle_vector
            R.id.menu_restore_file -> org.fossify.commons.R.drawable.ic_reset_vector
            R.id.menu_rotate,
            R.id.menu_rotate_right,
            R.id.menu_rotate_left,
            R.id.menu_rotate_one_eighty -> R.drawable.ic_rotate_right_vector
            R.id.menu_change_orientation,
            R.id.menu_default_orientation -> org.fossify.commons.R.drawable.ic_orientation_auto_vector
            R.id.menu_force_portrait -> org.fossify.commons.R.drawable.ic_orientation_portrait_vector
            R.id.menu_force_landscape,
            R.id.menu_force_landscape_reverse -> org.fossify.commons.R.drawable.ic_orientation_landscape_vector
            R.id.menu_open_with -> R.drawable.ic_open_with_calypso_vector
            R.id.menu_set_as -> org.fossify.commons.R.drawable.ic_set_as_vector
            R.id.menu_show_on_map -> org.fossify.commons.R.drawable.ic_place_vector
            R.id.menu_resize -> R.drawable.ic_aspect_ratio_vector
            R.id.menu_print -> R.drawable.ic_print_calypso_vector
            R.id.menu_create_shortcut -> org.fossify.commons.R.drawable.ic_link_vector
            R.id.menu_share -> org.fossify.commons.R.drawable.ic_share_vector
            R.id.menu_edit -> R.drawable.ic_edit_calypso_vector
            R.id.menu_delete -> org.fossify.commons.R.drawable.ic_delete_vector

            // CAB (Selection Action Mode) menus
            R.id.cab_confirm_selection -> org.fossify.commons.R.drawable.ic_check_vector
            R.id.cab_delete,
            org.fossify.commons.R.id.cab_remove,
            R.id.cab_empty_recycle_bin,
            R.id.cab_empty_disable_recycle_bin -> org.fossify.commons.R.drawable.ic_delete_vector
            R.id.cab_share -> org.fossify.commons.R.drawable.ic_share_vector
            R.id.cab_pin -> org.fossify.commons.R.drawable.ic_pin_filled_vector
            R.id.cab_unpin -> R.drawable.ic_unpin_vector
            R.id.cab_properties -> org.fossify.commons.R.drawable.ic_info_vector
            R.id.cab_rename -> org.fossify.commons.R.drawable.ic_rename_vector
            R.id.cab_hide -> R.drawable.ic_encrypt_calypso_vector
            R.id.cab_unhide,
            R.id.cab_unhide_folder -> org.fossify.commons.R.drawable.ic_unhide_vector
            R.id.cab_hide_folder -> R.drawable.ic_album_hidden_vector
            R.id.cab_exclude,
            R.id.cab_unexclude -> R.drawable.ic_album_excluded_vector
            R.id.cab_rotate,
            R.id.cab_rotate_right,
            R.id.cab_rotate_left,
            R.id.cab_rotate_one_eighty -> R.drawable.ic_rotate_right_vector
            R.id.cab_change_order -> org.fossify.commons.R.drawable.ic_drag_handle_vector
            R.id.cab_move_to_top -> org.fossify.commons.R.drawable.ic_move_to_top_vector
            R.id.cab_move_to_bottom -> org.fossify.commons.R.drawable.ic_move_to_bottom_vector
            R.id.cab_copy_to -> org.fossify.commons.R.drawable.ic_copy_vector
            R.id.cab_move_to -> org.fossify.commons.R.drawable.ic_move_vector
            R.id.cab_add_to_virtual_album,
            R.id.cab_select_photo -> R.drawable.ic_album_virtual_vector
            R.id.cab_remove_from_virtual_album -> org.fossify.commons.R.drawable.ic_minus_circle_vector
            R.id.cab_create_shortcut -> org.fossify.commons.R.drawable.ic_link_vector
            R.id.cab_open_with -> R.drawable.ic_open_with_calypso_vector
            R.id.cab_set_as -> org.fossify.commons.R.drawable.ic_set_as_vector
            R.id.cab_resize -> R.drawable.ic_aspect_ratio_vector
            R.id.cab_edit,
            R.id.cab_change_cover_image -> R.drawable.ic_edit_calypso_vector
            R.id.cab_add_to_favorites -> R.drawable.ic_star_outline_calypso_vector
            R.id.cab_remove_from_favorites -> R.drawable.ic_star_calypso_vector
            R.id.cab_restore_recycle_bin_files,
            R.id.cab_use_default -> org.fossify.commons.R.drawable.ic_reset_vector
            R.id.cab_lock -> org.fossify.commons.R.drawable.ic_lock_outlined_vector
            R.id.cab_unlock -> org.fossify.commons.R.drawable.ic_lock_vector
            R.id.cab_select_all -> org.fossify.commons.R.drawable.ic_select_all_vector
            else -> 0
        }
        if (resId != 0) {
            return loadTintedDrawable(context, resId, tintColor)
        }
        return item.icon?.constantState?.newDrawable()?.mutate()?.apply {
            colorFilter = null
            setTintList(ColorStateList.valueOf(tintColor))
        }
    }
}
