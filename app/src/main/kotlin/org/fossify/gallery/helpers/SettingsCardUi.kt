package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible

/**
 * Shared visual tokens and card builders for the minimalist Settings, Appearance, and About screens.
 */
object SettingsCardUi {
    private const val CARD_DIVIDER_TAG = "calypso_card_divider"

    fun dividerColor(palette: AppearancePalette): Int {
        val light = AppearancePalette.isLightBackground(palette.background)
        return ColorUtils.blendARGB(palette.surface, palette.text, if (light) 0.08f else 0.10f)
    }

    fun eyebrowColor(palette: AppearancePalette): Int =
        ColorUtils.blendARGB(palette.text, palette.background, 0.42f)

    fun mutedTextColor(palette: AppearancePalette): Int =
        ColorUtils.blendARGB(palette.text, palette.background, 0.40f)

    fun dangerColor(palette: AppearancePalette): Int =
        if (AppearancePalette.isLightBackground(palette.background)) {
            Color.parseColor("#DC2626")
        } else {
            Color.parseColor("#F87171")
        }

    fun createCardBackground(
        context: Context,
        palette: AppearancePalette,
        cornerRadiusDp: Float = 18f,
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * density
            setColor(palette.surface)
            setStroke(strokePx, palette.border)
        }
    }

    fun createThemeTileBackground(
        context: Context,
        selected: Boolean,
        palette: AppearancePalette,
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val cornerRadiusPx = 14f * density
        val strokePx = ((if (selected) 2f else 1f) * density).toInt().coerceAtLeast(1)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(palette.surface)
            setStroke(strokePx, if (selected) palette.primary else palette.border)
        }
        val isLight = AppearancePalette.isLightBackground(palette.background)
        val rippleColor = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.12f else 0.18f)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    fun createSearchBackground(context: Context, palette: AppearancePalette): GradientDrawable =
        createCardBackground(context, palette, cornerRadiusDp = 14f)

    fun createIconChipBackground(context: Context, palette: AppearancePalette): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val light = AppearancePalette.isLightBackground(palette.background)
        val chipColor = ColorUtils.blendARGB(palette.surface, palette.primary, if (light) 0.09f else 0.13f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * density
            setColor(chipColor)
        }
    }

    fun createSwatchDrawable(
        context: Context,
        color: Int,
        borderColor: Int,
        cornerRadiusDp: Float = 5f,
    ): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * density
            setColor(color)
            setStroke(strokePx, borderColor)
        }
    }

    fun createRadioCircleDrawable(
        context: Context,
        selected: Boolean,
        palette: AppearancePalette,
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val strokePx = (2f * density).toInt().coerceAtLeast(2)
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(strokePx, if (selected) palette.primary else mutedTextColor(palette))
        }
        if (!selected) {
            return ring
        }
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primary)
        }
        val inset = (5f * density).toInt()
        return LayerDrawable(arrayOf(ring, dot)).apply {
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    fun styleEyebrow(label: TextView, palette: AppearancePalette) {
        label.setTextColor(eyebrowColor(palette))
        label.isAllCaps = true
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        label.letterSpacing = 0.06f
        label.setTypeface(label.typeface, Typeface.BOLD)
    }

    /**
     * Applies the rounded card background, removes stale dividers, inserts hairline dividers
     * strictly between adjacent visible rows, and hides empty cards (along with their eyebrow).
     */
    fun styleCardGroup(
        card: LinearLayout,
        palette: AppearancePalette,
        insetStartDp: Float = 14f,
        eyebrow: View? = null,
    ) {
        val context = card.context
        val density = context.resources.displayMetrics.density

        for (i in card.childCount - 1 downTo 0) {
            if (card.getChildAt(i).tag == CARD_DIVIDER_TAG) {
                card.removeViewAt(i)
            }
        }

        val visibleChildren = (0 until card.childCount)
            .map { card.getChildAt(it) }
            .filter { it.isVisible }

        if (visibleChildren.isEmpty()) {
            card.isVisible = false
            eyebrow?.isVisible = false
            return
        }

        card.isVisible = true
        if (eyebrow != null) {
            eyebrow.isVisible = true
            if (eyebrow is TextView) {
                styleEyebrow(eyebrow, palette)
            }
        }

        card.background = createCardBackground(context, palette, cornerRadiusDp = 18f)
        card.clipToOutline = true

        val divColor = dividerColor(palette)
        val startMargin = (insetStartDp * density).toInt()
        val endMargin = (14f * density).toInt()
        val heightPx = (1f * density).toInt().coerceAtLeast(1)

        for (idx in visibleChildren.size - 1 downTo 1) {
            val childAfter = visibleChildren[idx]
            val insertAt = card.indexOfChild(childAfter)
            if (insertAt <= 0) continue

            val divider = View(context).apply {
                tag = CARD_DIVIDER_TAG
                setBackgroundColor(divColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    heightPx,
                ).apply {
                    marginStart = startMargin
                    marginEnd = endMargin
                }
            }
            card.addView(divider, insertAt)
        }
    }
}
