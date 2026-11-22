package org.fossify.gallery.dialogs

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.CompactDialogSizing
import org.fossify.gallery.helpers.SettingsCardUi

/**
 * Theme-aware single-choice dialog used by the encrypted vault setup flows (Stage 1: Create new /
 * Use existing / Select archived vault, Stage 2: Default location / Custom location, and the
 * archived vault picker).
 *
 * Uses [AppearancePalette] for the card surface, border, radio circles, ripple states, optional
 * header info icon, and action button so Light, Dark, Midnight Blue, Warm Paper, Deep Forest, and
 * user-defined Custom palettes all render consistently without Commons' green radio/button tint.
 */
class VaultChoiceDialog(
    private val activity: Activity,
    private val title: String,
    private val items: List<RadioItem>,
    private val checkedItemId: Int = -1,
    private val infoIconRes: Int = 0,
    private val infoContentDescription: String? = null,
    private val onInfoClick: (() -> Unit)? = null,
    private val cancelCallback: (() -> Unit)? = null,
    private val callback: (selectedId: Int) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private var wasSelected = false

    init {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val res = activity.resources
        val density = res.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        val padH = res.getDimensionPixelSize(R.dimen.compact_dialog_padding)
        val padTop = res.getDimensionPixelSize(R.dimen.compact_dialog_padding_top)
        val padBottom = res.getDimensionPixelSize(R.dimen.compact_dialog_padding_bottom)
        val cardRadiusPx = res.getDimension(R.dimen.compact_dialog_corner_radius)
        val titleTextPx = res.getDimension(R.dimen.compact_dialog_title_text_size)
        val choiceTextPx = res.getDimension(R.dimen.compact_dialog_choice_text_size)
        val actionTextPx = res.getDimension(R.dimen.compact_dialog_action_medium_text_size)
        val rowMinHeightPx = res.getDimensionPixelSize(R.dimen.compact_dialog_choice_row_min_height)
        val radioSizePx = res.getDimensionPixelSize(R.dimen.compact_dialog_choice_radio_size)
        val infoBtnSizePx = res.getDimensionPixelSize(R.dimen.compact_dialog_choice_info_button_size)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padTop, padH, padBottom)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cardRadiusPx
                setColor(palette.surface)
                setStroke(dp(1f).coerceAtLeast(1), palette.border)
            }
        }

        // Title header (with optional trailing info button)
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12f)
            }
        }

        val titleView = TextView(activity).apply {
            text = title
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, titleTextPx)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleView)

        if (infoIconRes != 0 && onInfoClick != null) {
            val infoBtn = AppCompatImageView(activity).apply {
                setImageResource(infoIconRes)
                imageTintList = ColorStateList.valueOf(palette.primary)
                contentDescription = infoContentDescription ?: title
                val pad = dp(8f)
                setPadding(pad, pad, pad, pad)
                background = createCircularRipple(palette)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(infoBtnSizePx, infoBtnSizePx).apply {
                    marginStart = dp(8f)
                }
                setOnClickListener { onInfoClick.invoke() }
            }
            headerRow.addView(infoBtn)
        }
        root.addView(headerRow)

        // Scrollable options list inside a subtle grouped surface container
        val listCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
                val isLight = AppearancePalette.isLightBackground(palette.background)
                setColor(ColorUtils.blendARGB(palette.surface, palette.background, if (isLight) 0.55f else 0.35f))
                setStroke(dp(1f).coerceAtLeast(1), palette.border)
            }
            clipToOutline = true
        }

        val divColor = SettingsCardUi.dividerColor(palette)
        items.forEachIndexed { index, item ->
            val isSelected = item.id == checkedItemId
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = rowMinHeightPx
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                background = createRowRipple(palette, density)
                isClickable = true
                isFocusable = true
            }

            val radioIndicator = ImageView(activity).apply {
                setImageDrawable(SettingsCardUi.createRadioCircleDrawable(activity, isSelected, palette))
                layoutParams = LinearLayout.LayoutParams(radioSizePx, radioSizePx).apply {
                    marginEnd = dp(12f)
                }
            }

            val labelView = TextView(activity).apply {
                text = item.title
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, choiceTextPx)
                if (isSelected) {
                    setTypeface(typeface, Typeface.BOLD)
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(radioIndicator)
            row.addView(labelView)
            row.setOnClickListener {
                if (wasSelected) return@setOnClickListener
                wasSelected = true
                radioIndicator.setImageDrawable(
                    SettingsCardUi.createRadioCircleDrawable(activity, true, palette)
                )
                dialog?.dismiss()
                callback(item.id)
            }
            listCard.addView(row)

            if (index < items.lastIndex) {
                val divider = View(activity).apply {
                    setBackgroundColor(divColor)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1f).coerceAtLeast(1),
                    ).apply {
                        marginStart = dp(46f)
                        marginEnd = dp(14f)
                    }
                }
                listCard.addView(divider)
            }
        }

        val scrollView = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                listCard,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Bottom actions row (Cancel)
        val actionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(14f)
            }
        }

        // Plain TextView so setupDialogStuff()'s updateTextColors() keeps palette.primary.
        val cancelBtn = TextView(activity).apply {
            text = activity.getString(org.fossify.commons.R.string.cancel)
            setTextColor(palette.primary)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, actionTextPx)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = createRowRipple(palette, density, cornerRadiusDp = 999f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog?.dismiss()
                if (!wasSelected) {
                    cancelCallback?.invoke()
                }
            }
        }
        actionsRow.addView(cancelBtn)
        root.addView(actionsRow)

        fun clampScrollHeight() {
            scrollView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            scrollView.requestLayout()
            scrollView.post {
                val maxHeight = CompactDialogSizing.resolveScrollMaxHeightPx(
                    activity,
                    R.dimen.compact_dialog_choice_scroll_max_height,
                )
                if (scrollView.height > maxHeight) {
                    scrollView.layoutParams.height = maxHeight
                    scrollView.requestLayout()
                }
            }
        }
        clampScrollHeight()

        val builder = activity.getAlertDialogBuilder()
            .setView(root)
            .setCancelable(true)

        activity.setupDialogStuff(root, builder) { alertDialog ->
            dialog = alertDialog
            alertDialog.setCanceledOnTouchOutside(true)
            alertDialog.setOnCancelListener {
                if (!wasSelected) {
                    cancelCallback?.invoke()
                }
            }
            CompactDialogSizing.applyResponsiveWidth(
                activity = activity,
                dialog = alertDialog,
                onConfigurationChangedExtra = { clampScrollHeight() },
            )
        }
    }

    private fun createRowRipple(
        palette: AppearancePalette,
        density: Float,
        cornerRadiusDp: Float = 10f,
    ): RippleDrawable {
        val isLight = AppearancePalette.isLightBackground(palette.background)
        val rippleColor = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.14f else 0.22f)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * density
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }

    private fun createCircularRipple(palette: AppearancePalette): RippleDrawable {
        val isLight = AppearancePalette.isLightBackground(palette.background)
        val rippleColor = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.16f else 0.24f)
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
    }
}
