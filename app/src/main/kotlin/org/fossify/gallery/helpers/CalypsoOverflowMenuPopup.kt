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
 * - Balanced minimalist scale (258dp width, 43dp rows, 14sp regular text, 17dp icons).
 * - Always unfolds cleanly downward from the top-right anchor within screen bounds (never jumps to
 *   random screen coordinates).
 * - Clean left-aligned icons with zero inner dividers.
 * - Distinct tinted bottom footer band for Settings (`Ayarlar`) with matching secondary-tinted icon
 *   and medium label. It intentionally has no trailing chevron: Settings is a direct page, not a submenu.
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

    fun showMenu(
        anchor: View,
        @MenuRes menuRes: Int,
        alignRightInsetDp: Float = 0f,
        onItemSelected: (MenuItem) -> Unit
    ) {
        val backingMenu = PopupMenu(activity, anchor).apply {
            activity.menuInflater.inflate(menuRes, menu)
        }.menu
        val items = ArrayList<MenuItem>(backingMenu.size())
        for (i in 0 until backingMenu.size()) {
            items.add(backingMenu.getItem(i))
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
                cornerRadius = 16f * density
                setColor(palette.surface)
                setStroke(strokeWidth, palette.border)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = 12f * density
        }

        val scrollView = MaxHeightScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val listPad = (6f * density).toInt()
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
            elevation = 12f * density
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

            val footerReservePx = if (settingsItem != null) (56f * density).toInt() else 0
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
                    val mH = (12f * density).toInt()
                    val mV = (3.5f * density).toInt()
                    setMargins(mH, mV, mH, mV)
                }
                setBackgroundColor(ColorUtils.setAlphaComponent(palette.border, 0xB0))
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
        val hMargin = (12f * density).toInt()
        val vMargin = (3.5f * density).toInt()
        return View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1f * density).toInt().coerceAtLeast(1)
            ).apply {
                setMargins(hMargin, vMargin, hMargin, vMargin)
            }
            setBackgroundColor(ColorUtils.setAlphaComponent(palette.border, 0xB0))
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
            setBackgroundColor(ColorUtils.setAlphaComponent(palette.border, 0xB0))
        }
        wrapper.addView(topRule)

        val rowHost = FrameLayout(activity).apply {
            val hPad = (6f * density).toInt()
            val vPad = (4.5f * density).toInt()
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
        val padH = (12f * density).toInt()
        val padV = (9f * density).toInt()
        val iconSize = (17f * density).toInt()

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
                    marginStart = (12f * density).toInt()
                }
                text = title
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE_SP)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
        val padH = (12f * density).toInt()
        val padV = (9f * density).toInt()
        val iconSize = (17f * density).toInt()
        val iconTint = palette.secondary

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
                    marginStart = (12f * density).toInt()
                }
                text = item.title
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TEXT_SIZE_SP)
                typeface = if (isSettingsFooter) {
                    Typeface.create("sans-serif-medium", Typeface.NORMAL)
                } else {
                    Typeface.create("sans-serif", Typeface.NORMAL)
                }
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(titleView)

            if (item.hasSubMenu()) {
                val chevronSize = (14.5f * density).toInt()
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
            cornerRadius = 11f * density
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
        const val POPUP_WIDTH_DP = 258f
        const val ROW_MIN_HEIGHT_DP = 43f
        const val ROW_TEXT_SIZE_SP = 14f

        fun isSettingsItem(itemId: Int): Boolean {
            return itemId == R.id.settings
        }

        fun resolveMenuItemGroup(itemId: Int): String? =
            CalypsoOverflowMenuIcons.resolveMenuItemGroup(itemId)

        fun loadTintedDrawable(context: Context, resId: Int, tintColor: Int): Drawable? =
            CalypsoOverflowMenuIcons.loadTintedDrawable(context, resId, tintColor)

        fun resolveMenuItemIcon(context: Context, item: MenuItem, tintColor: Int): Drawable? =
            CalypsoOverflowMenuIcons.resolveMenuItemIcon(context, item, tintColor)
    }
}
