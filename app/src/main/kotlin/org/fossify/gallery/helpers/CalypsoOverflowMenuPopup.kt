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
 * Shared Tasarım A ("Kesintisiz Liste & Tonlu Ayarlar Şeridi") Calypso overflow menu popup.
 *
 * Used across:
 * - Main screen & All-Files search bar (`MainActivity`, `MediaActivity`)
 * - Album two-line header (`MediaActivity`)
 * - Fullscreen media viewer (`ViewPagerActivity`)
 * - Multi-select Contextual Action Bar (`CalypsoActionModeController`)
 *
 * Design guarantees:
 * - Comfortable, native-proportioned scale (274dp width, 46dp rows, 14.5sp medium text, 20dp icons).
 * - Always unfolds cleanly downward from the top-right anchor within screen bounds (never jumps to
 *   random screen coordinates).
 * - Clean left-aligned icons with zero inner dividers.
 * - Distinct tinted bottom footer band for Settings (`Ayarlar`) with primary-tinted icon and bold
 *   label. It intentionally has no trailing chevron: Settings is a direct page, not a submenu.
 */
class CalypsoOverflowMenuPopup(private val activity: Activity) {

    private var popupWindow: PopupWindow? = null

    val isShowing: Boolean
        get() = popupWindow?.isShowing == true

    fun dismiss() {
        runCatching {
            if (popupWindow?.isShowing == true) {
                popupWindow?.dismiss()
            }
        }
        popupWindow = null
    }

    fun toggle(
        anchor: View,
        items: List<MenuItem>,
        alignRightInsetDp: Float = 0f,
        onItemSelected: (MenuItem) -> Unit
    ) {
        if (isShowing) {
            dismiss()
            return
        }
        show(anchor, items, alignRightInsetDp, onItemSelected)
    }

    fun show(
        anchor: View,
        items: List<MenuItem>,
        alignRightInsetDp: Float = 0f,
        onItemSelected: (MenuItem) -> Unit
    ) {
        dismiss()
        val visibleItems = items.filter { it.isVisible }
        if (visibleItems.isEmpty() || anchor.windowToken == null) return

        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        val displayFrame = Rect()
        anchor.getWindowVisibleDisplayFrame(displayFrame)
        val screenWidth = if (displayFrame.width() > 0) {
            displayFrame.width()
        } else {
            activity.resources.displayMetrics.widthPixels
        }
        val screenBottom = if (displayFrame.bottom > 0) {
            displayFrame.bottom
        } else {
            activity.resources.displayMetrics.heightPixels
        }

        val popupWidth = minOf(
            (POPUP_WIDTH_DP * density).toInt(),
            (screenWidth - (24f * density).toInt()).coerceAtLeast((220f * density).toInt())
        )
        val strokeWidth = (1f * density).toInt().coerceAtLeast(1)

        val anchorLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)
        val anchorBottom = anchorLocation[1] + anchor.height
        val anchorRight = anchorLocation[0] + anchor.width

        val yOffset = (6f * density).toInt()
        val popupY = anchorBottom + yOffset
        val rightEdgeMargin = (12f * density).toInt()
        val desiredRight = anchorRight - (alignRightInsetDp * density).toInt()
        val clampedRight = desiredRight.coerceAtMost(screenWidth - rightEdgeMargin)
        val popupX = (clampedRight - popupWidth).coerceAtLeast(rightEdgeMargin)

        val availableBelow = (screenBottom - popupY - (16f * density).toInt())
            .coerceAtLeast((180f * density).toInt())

        val cardContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(palette.surface)
                setStroke(strokeWidth, palette.border)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = 16f * density
        }

        val scrollView = MaxHeightScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val listPad = (7f * density).toInt()
        val listLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(listPad, listPad, listPad, listPad)
        }
        scrollView.addView(
            listLayout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val footerContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        cardContainer.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        cardContainer.addView(
            footerContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val popup = PopupWindow(
            cardContainer,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isClippingEnabled = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 18f * density
            animationStyle = 0
            setOnDismissListener {
                if (popupWindow === this) {
                    popupWindow = null
                }
            }
        }

        lateinit var populateRoot: (List<MenuItem>) -> Unit
        lateinit var populateSubMenu: (CharSequence?, List<MenuItem>, List<MenuItem>) -> Unit

        populateRoot = { rootItems ->
            listLayout.removeAllViews()
            footerContainer.removeAllViews()

            val settingsItem = rootItems.firstOrNull { isSettingsItem(it.itemId) }
            val mainItems = rootItems.filterNot { isSettingsItem(it.itemId) }

            val footerReservePx = if (settingsItem != null) (64f * density).toInt() else 0
            scrollView.maxHeightPx = (availableBelow - footerReservePx).coerceAtLeast((120f * density).toInt())

            var previousGroup: String? = null
            for (item in mainItems) {
                if (!item.isVisible) continue
                val currentGroup = resolveMenuItemGroup(item.itemId)
                if (previousGroup != null && currentGroup != null && currentGroup != previousGroup) {
                    listLayout.addView(buildInsetDivider(palette, density))
                }
                val row = buildRow(
                    item = item,
                    palette = palette,
                    density = density,
                    isSettingsFooter = false
                ) {
                    val subMenu = item.subMenu
                    if (item.hasSubMenu() && subMenu != null && subMenu.size() > 0) {
                        val subItems = ArrayList<MenuItem>()
                        for (idx in 0 until subMenu.size()) {
                            val subItem = subMenu.getItem(idx)
                            if (subItem.isVisible) subItems.add(subItem)
                        }
                        if (subItems.isNotEmpty()) {
                            populateSubMenu(item.title, subItems, rootItems)
                            return@buildRow
                        }
                    }
                    popup.dismiss()
                    onItemSelected(item)
                }
                listLayout.addView(row)
                previousGroup = currentGroup
            }

            if (settingsItem != null) {
                footerContainer.visibility = View.VISIBLE
                footerContainer.addView(buildSettingsFooterBand(settingsItem, palette, density) {
                    popup.dismiss()
                    onItemSelected(settingsItem)
                })
            } else {
                footerContainer.visibility = View.GONE
            }
            scrollView.scrollTo(0, 0)
        }

        populateSubMenu = { title, subItems, parentItems ->
            listLayout.removeAllViews()
            footerContainer.removeAllViews()
            footerContainer.visibility = View.GONE
            scrollView.maxHeightPx = availableBelow

            val backRow = buildSubMenuBackRow(title, palette, density) {
                populateRoot(parentItems)
            }
            listLayout.addView(backRow)

            val divider = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1f * density).toInt().coerceAtLeast(1)
                ).apply {
                    val mH = (10f * density).toInt()
                    val mV = (4f * density).toInt()
                    setMargins(mH, mV, mH, mV)
                }
                setBackgroundColor(palette.border)
            }
            listLayout.addView(divider)

            for (subItem in subItems) {
                if (!subItem.isVisible) continue
                val row = buildRow(
                    item = subItem,
                    palette = palette,
                    density = density,
                    isSettingsFooter = false
                ) {
                    popup.dismiss()
                    onItemSelected(subItem)
                }
                listLayout.addView(row)
            }
            scrollView.scrollTo(0, 0)
        }

        populateRoot(visibleItems)
        popupWindow = popup

        // Smooth downward unfold anchored from top-right.
        cardContainer.pivotX = popupWidth.toFloat()
        cardContainer.pivotY = 0f
        cardContainer.alpha = 0f
        cardContainer.scaleX = 0.96f
        cardContainer.scaleY = 0.90f
        cardContainer.translationY = -8f * density

        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, popupX, popupY)

        cardContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(200L)
            .setInterpolator(PathInterpolator(0.2f, 1.05f, 0.34f, 1f))
            .start()
    }

    private fun buildInsetDivider(palette: AppearancePalette, density: Float): View {
        val hMargin = (13f * density).toInt()
        val vMargin = (4f * density).toInt()
        return View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1f * density).toInt().coerceAtLeast(1)
            ).apply {
                setMargins(hMargin, vMargin, hMargin, vMargin)
            }
            setBackgroundColor(ColorUtils.setAlphaComponent(palette.border, 0xE6))
        }
    }

    private fun buildSettingsFooterBand(
        item: MenuItem,
        palette: AppearancePalette,
        density: Float,
        onClick: () -> Unit
    ): View {
        val isLight = AppearancePalette.isLightBackground(palette.background)
        val bandColor = if (isLight) {
            ColorUtils.blendARGB(palette.surface, palette.primary, 0.055f)
        } else {
            ColorUtils.blendARGB(palette.surface, palette.text, 0.045f)
        }

        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(bandColor)
        }

        val topRule = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1f * density).toInt().coerceAtLeast(1)
            )
            setBackgroundColor(palette.border)
        }
        wrapper.addView(topRule)

        val rowHost = FrameLayout(activity).apply {
            val hPad = (7f * density).toInt()
            val vPad = (6f * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }
        rowHost.addView(
            buildRow(
                item = item,
                palette = palette,
                density = density,
                isSettingsFooter = true,
                onClick = onClick
            )
        )
        wrapper.addView(rowHost)
        return wrapper
    }

    private fun buildSubMenuBackRow(
        title: CharSequence?,
        palette: AppearancePalette,
        density: Float,
        onBack: () -> Unit
    ): View {
        val padH = (13f * density).toInt()
        val padV = (10f * density).toInt()
        val iconSize = (18f * density).toInt()

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (44f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(padH, padV, padH, padV)
            background = createRowRipple(palette, density)
            isClickable = true
            isFocusable = true

            val backIcon = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(
                    loadTintedDrawable(
                        activity,
                        org.fossify.commons.R.drawable.ic_arrow_left_vector,
                        palette.primary
                    )
                )
            }
            addView(backIcon)

            val titleView = MyTextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (13f * density).toInt()
                }
                text = title
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(titleView)

            setOnClickListener { onBack() }
        }
    }

    private fun buildRow(
        item: MenuItem,
        palette: AppearancePalette,
        density: Float,
        isSettingsFooter: Boolean,
        onClick: () -> Unit
    ): View {
        val padH = (13f * density).toInt()
        val padV = (10.5f * density).toInt()
        val iconSize = (18f * density).toInt()
        val iconTint = if (isSettingsFooter) palette.primary else palette.secondary

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (ROW_MIN_HEIGHT_DP * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(padH, padV, padH, padV)
            background = createRowRipple(palette, density)
            isClickable = true
            isFocusable = true

            val iconView = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(resolveMenuItemIcon(activity, item, iconTint))
            }
            addView(iconView)

            val titleView = MyTextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (13f * density).toInt()
                }
                text = item.title
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE_SP)
                typeface = if (isSettingsFooter) {
                    Typeface.create("sans-serif-medium", Typeface.BOLD)
                } else {
                    Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(titleView)

            if (item.hasSubMenu()) {
                val chevronSize = (15f * density).toInt()
                val chevronView = ImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(chevronSize, chevronSize).apply {
                        marginStart = (8f * density).toInt()
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(
                        loadTintedDrawable(
                            activity,
                            org.fossify.commons.R.drawable.ic_chevron_right_unpadded_vector,
                            palette.secondary
                        )
                    )
                }
                addView(chevronView)
            }

            setOnClickListener { onClick() }
        }
    }

    private fun createRowRipple(palette: AppearancePalette, density: Float): Drawable {
        val isLight = AppearancePalette.isLightBackground(palette.background)
        val rippleColor = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.12f else 0.18f)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    private class MaxHeightScrollView(context: Context) : ScrollView(context) {
        var maxHeightPx: Int = Int.MAX_VALUE

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val constrainedSpec = if (maxHeightPx < Int.MAX_VALUE) {
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            } else {
                heightMeasureSpec
            }
            super.onMeasure(widthMeasureSpec, constrainedSpec)
        }
    }

    companion object {
        const val POPUP_WIDTH_DP = 274f
        const val ROW_MIN_HEIGHT_DP = 46f
        const val ROW_TEXT_SIZE_SP = 14.5f

        fun isSettingsItem(itemId: Int): Boolean {
            return itemId == R.id.settings
        }

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
}

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
