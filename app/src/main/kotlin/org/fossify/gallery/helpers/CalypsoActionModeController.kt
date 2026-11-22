package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/**
 * Implements Tasarım A ("Calypso Floating Pill Bar & More Options Card") for multi-select
 * Contextual Action Bars across [ org.fossify.gallery.adapters.DirectoryAdapter ],
 * [ org.fossify.gallery.adapters.MediaAdapter ], [ org.fossify.gallery.adapters.ManageFoldersAdapter ],
 * and [ org.fossify.gallery.adapters.ManageHiddenFoldersAdapter ].
 *
 * Key properties:
 * - Replaces the legacy black full-width ActionMode box with a 28dp floating pill container
 *   using [AppearancePalette.surface] fill and 1dp [AppearancePalette.border] stroke on top of
 *   a seamless [AppearancePalette.background] backdrop (matching [org.fossify.gallery.extensions.applyCalypsoSearchBarStyle]).
 * - Preserves [MyRecyclerViewAdapter]'s internal counter [TextView] reference inside a high-contrast
 *   [AppearancePalette.primary] / [AppearancePalette.onPrimary] capsule badge (`3 / 24`).
 * - Renders only the approved bar action icons on the right side of the pill and opens a
 *   theme-aware 18dp rounded card popup for `More options (⋮)` with `Özellikler` (`Properties`)
 *   at the first row.
 */
class CalypsoActionModeController(
    private val activity: BaseSimpleActivity,
    private val adapter: MyRecyclerViewAdapter,
    private val barActionIds: Set<Int>
) {
    private var outerWrapper: FrameLayout? = null
    private var pillContainer: LinearLayout? = null
    private var actionsContainer: LinearLayout? = null
    private var counterTextView: TextView? = null
    private val overflowPopup = CalypsoOverflowMenuPopup(activity)
    private var hiddenSearchBarContainer: View? = null

    fun onActionModeCreated(actMode: ActionMode?) {
        val currentActMode = actMode ?: return
        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        val strokeWidth = (1f * density).toInt().coerceAtLeast(1)

        val originalCounter = (currentActMode.customView as? TextView)
            ?: currentActMode.customView?.findViewById(org.fossify.commons.R.id.actionbar_title)
            ?: return
        counterTextView = originalCounter
        (originalCounter.parent as? ViewGroup)?.removeView(originalCounter)

        val horizontalPadding = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val pillHeightPx = (48f * density).toInt()

        val wrapper = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(palette.background)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
        }

        val pill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pillHeightPx,
                Gravity.CENTER_VERTICAL
            )
            val pillSidePad = (6f * density).toInt()
            setPadding(pillSidePad, 0, pillSidePad, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f * density
                setColor(palette.surface)
                setStroke(strokeWidth, palette.border)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = 3f * density
        }

        val closeButtonSize = (36f * density).toInt()
        val closeButton = ImageButton(activity).apply {
            layoutParams = LinearLayout.LayoutParams(closeButtonSize, closeButtonSize)
            background = createCircularRipple(palette)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val iconPad = (8f * density).toInt()
            setPadding(iconPad, iconPad, iconPad, iconPad)
            setImageDrawable(loadTintedDrawable(R.drawable.ic_calypso_close_vector, palette.text))
            contentDescription = activity.getString(org.fossify.commons.R.string.close)
            setOnClickListener {
                dismissOverflowPopup()
                adapter.finishActMode()
            }
        }
        pill.addView(closeButton)

        styleCounterBadge(originalCounter, palette, density)
        val counterParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (30f * density).toInt()
        ).apply {
            marginStart = (4f * density).toInt()
        }
        pill.addView(originalCounter, counterParams)

        val spacer = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        pill.addView(spacer)

        val rightActions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        pill.addView(rightActions)
        wrapper.addView(pill)

        outerWrapper = wrapper
        pillContainer = pill
        actionsContainer = rightActions

        currentActMode.customView = wrapper

        // Hide the underlying search bar / album header so it never peeks out behind the pill.
        activity.findViewById<View>(org.fossify.commons.R.id.search_bar_container)?.let { topBar ->
            if (topBar.visibility == View.VISIBLE) {
                hiddenSearchBarContainer = topBar
                topBar.visibility = View.INVISIBLE
            }
        }

        configureActionBarContextView(palette, density)
    }

    fun onPrepareActionMode(menu: Menu) {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        configureActionBarContextView(palette, density)

        counterTextView?.let { styleCounterBadge(it, palette, density) }

        val rightActions = actionsContainer ?: return
        rightActions.removeAllViews()

        val barItems = ArrayList<MenuItem>()
        val overflowItems = ArrayList<MenuItem>()

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (!item.isVisible) continue
            if (item.itemId in barActionIds) {
                barItems.add(item)
            } else {
                overflowItems.add(item)
            }
        }

        val btnSize = (36f * density).toInt()
        val btnGap = (2f * density).toInt()
        val iconPad = (8f * density).toInt()

        for (item in barItems) {
            val btn = ImageButton(activity).apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    marginStart = btnGap
                }
                background = createCircularRipple(palette)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(iconPad, iconPad, iconPad, iconPad)
                setImageDrawable(resolveMenuItemIcon(item, palette.text))
                contentDescription = item.title
                TooltipCompat.setTooltipText(this, item.title)
                setOnClickListener {
                    dismissOverflowPopup()
                    adapter.actionItemPressed(item.itemId)
                }
            }
            rightActions.addView(btn)
        }

        if (overflowItems.isNotEmpty()) {
            val moreTitle = activity.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description)
            val moreBtn = ImageButton(activity).apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    marginStart = btnGap
                }
                background = createCircularRipple(palette)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(iconPad, iconPad, iconPad, iconPad)
                setImageDrawable(loadTintedDrawable(R.drawable.ic_more_vertical_calypso_vector, palette.text))
                contentDescription = moreTitle
                TooltipCompat.setTooltipText(this, moreTitle)
                setOnClickListener {
                    toggleOverflowPopup(overflowItems)
                }
            }
            rightActions.addView(moreBtn)
        } else {
            dismissOverflowPopup()
        }
    }

    fun onActionModeDestroyed() {
        dismissOverflowPopup()
        hiddenSearchBarContainer?.visibility = View.VISIBLE
        hiddenSearchBarContainer = null
        outerWrapper = null
        pillContainer = null
        actionsContainer = null
        counterTextView = null
    }

    fun dismissOverflowPopup() {
        overflowPopup.dismiss()
    }

    private fun styleCounterBadge(counter: TextView, palette: AppearancePalette, density: Float) {
        val fillDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * density
            setColor(palette.primary)
        }
        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * density
            setColor(Color.WHITE)
        }
        val rippleColor = ColorUtils.setAlphaComponent(palette.onPrimary, 0x26)
        counter.background = RippleDrawable(ColorStateList.valueOf(rippleColor), fillDrawable, maskDrawable)
        counter.setTextColor(palette.onPrimary)
        counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        counter.setTypeface(counter.typeface, Typeface.BOLD)
        counter.letterSpacing = -0.01f
        counter.gravity = Gravity.CENTER
        counter.includeFontPadding = false
        counter.minWidth = 0
        counter.minimumWidth = 0
        counter.minHeight = 0
        counter.minimumHeight = 0
        val hPad = (12f * density).toInt()
        counter.setPadding(hPad, 0, hPad, 0)
    }

    private fun configureActionBarContextView(palette: AppearancePalette, density: Float) {
        val actionModeBar = activity.findViewById<ActionBarContextView>(
            androidx.appcompat.R.id.action_mode_bar
        ) ?: return

        val topBar = activity.findViewById<View>(org.fossify.commons.R.id.search_bar_container)
        val topBarHeight = when {
            topBar != null && topBar.height > 0 -> topBar.height
            topBar != null && (topBar.layoutParams?.height ?: 0) > 0 -> topBar.layoutParams.height
            else -> (64f * density).toInt()
        }
        val targetHeight = maxOf((62f * density).toInt(), topBarHeight)

        actionModeBar.setBackgroundColor(palette.background)
        actionModeBar.setPadding(0, 0, 0, 0)
        actionModeBar.setContentHeight(targetHeight)
        actionModeBar.outlineProvider = null

        // Collapse AppCompat's stock close button so our custom pill occupies the full width.
        actionModeBar.findViewById<View>(androidx.appcompat.R.id.action_mode_close_button)?.apply {
            if (this is ImageView) {
                setImageDrawable(null)
            }
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                width = 0
                height = 0
                leftMargin = 0
                rightMargin = 0
                marginStart = 0
                marginEnd = 0
            }
            visibility = View.GONE
        }

        // Detach AppCompat's stock ActionMenuView so it does not reserve width on the right.
        for (i in actionModeBar.childCount - 1 downTo 0) {
            val child = actionModeBar.getChildAt(i)
            if (child is ActionMenuView) {
                actionModeBar.removeViewAt(i)
            }
        }

        actionModeBar.requestLayout()
    }

    private fun toggleOverflowPopup(overflowItems: List<MenuItem>) {
        val anchor = pillContainer ?: outerWrapper ?: return
        overflowPopup.toggle(anchor = anchor, items = overflowItems) { chosen ->
            adapter.actionItemPressed(chosen.itemId)
        }
    }

    private fun createCircularRipple(palette: AppearancePalette): Drawable {
        val isLight = AppearancePalette.isLightBackground(palette.background)
        val rippleColor = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.16f else 0.22f)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    private fun resolveMenuItemIcon(item: MenuItem, tintColor: Int): Drawable? {
        return CalypsoOverflowMenuPopup.resolveMenuItemIcon(activity, item, tintColor)
    }

    private fun loadTintedDrawable(resId: Int, tintColor: Int): Drawable? {
        return CalypsoOverflowMenuPopup.loadTintedDrawable(activity, resId, tintColor)
    }

    companion object {
        val DIRECTORY_BAR_ACTION_IDS = setOf(
            R.id.cab_delete,
            R.id.cab_pin,
            R.id.cab_unpin,
            R.id.cab_move_to_top,
            R.id.cab_move_to_bottom
        )

        val MEDIA_BAR_ACTION_IDS = setOf(
            R.id.cab_confirm_selection,
            R.id.cab_delete,
            R.id.cab_share
        )

        val MANAGE_FOLDERS_BAR_ACTION_IDS = setOf(
            org.fossify.commons.R.id.cab_remove
        )

        val HIDDEN_FOLDERS_BAR_ACTION_IDS = setOf(
            R.id.cab_unhide
        )
    }
}
