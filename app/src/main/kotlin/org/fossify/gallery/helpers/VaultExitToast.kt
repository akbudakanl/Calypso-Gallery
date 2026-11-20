package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.extensions.config

/**
 * Instagram-style bottom bar used for the "tap back again to exit the vault" prompt.
 *
 * A system text toast is drawn by SystemUI at its own position and always carries the
 * package icon, so it cannot match the app's design. This bar is drawn in-app and pinned
 * to the bottom. Its colors follow the active [AppearancePalette]: filled with `primary`
 * and labeled with `onPrimary`, which stays readable in Light, Dark, System and Custom
 * modes. [Toast.LENGTH_LONG] is exactly 3500 ms on Android.
 */
object VaultExitToast {
    @Suppress("DEPRECATION")
    fun show(context: Context, message: CharSequence): Toast {
        val palette = AppearanceManager.resolve(context, context.config)
        val density = context.resources.displayMetrics.density
        val sideMargin = (SIDE_MARGIN_DP * density).toInt()
        val verticalPadding = (VERTICAL_PADDING_DP * density).toInt()
        val bottomOffset = (BOTTOM_OFFSET_DP * density).toInt()

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // Custom toast views use WRAP_CONTENT; this makes the bar span the screen with margins.
            minimumWidth = context.resources.displayMetrics.widthPixels - sideMargin * 2
            setPadding(sideMargin, verticalPadding, sideMargin, verticalPadding)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CORNER_RADIUS_DP * density
                setColor(palette.primary)
            }
        }

        bar.addView(
            MyTextView(context).apply {
                this.text = message
                setTextColor(palette.onPrimary)
                textSize = TEXT_SIZE_SP
                gravity = Gravity.CENTER
            }
        )

        return Toast(context).apply {
            view = bar
            duration = Toast.LENGTH_LONG // 3500 ms
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, bottomOffset)
            show()
        }
    }

    private const val SIDE_MARGIN_DP = 20f
    private const val VERTICAL_PADDING_DP = 14f
    private const val BOTTOM_OFFSET_DP = 24f
    private const val CORNER_RADIUS_DP = 10f
    private const val TEXT_SIZE_SP = 14f
}
