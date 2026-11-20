package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.extensions.config

/**
 * Instagram-style bottom bar used for the "tap back again to exit the vault" prompt.
 *
 * This used to be a [android.widget.Toast] with a custom view, but from Android 11 onwards
 * custom toast views are deprecated (and `setGravity`/`setMargin` are no-ops for apps targeting
 * 30+), so the designed bar never actually appeared. It is now a real in-app view attached to the
 * activity's decor view: same palette-driven look, pinned bottom with margins, animated in and
 * auto-dismissed after the same 3500 ms as `Toast.LENGTH_LONG`.
 */
object VaultExitToast {
    /** Handle returned by [show] so the caller can dismiss the bar early. */
    class Handle(private val dismiss: () -> Unit) {
        fun cancel() = dismiss()
    }

    fun show(context: Context, message: CharSequence): Handle {
        val activity = context.findActivity() ?: return Handle {}
        val decor = activity.window.decorView as? ViewGroup ?: return Handle {}
        val density = context.resources.displayMetrics.density
        val sideMargin = (SIDE_MARGIN_DP * density).toInt()
        val verticalPadding = (VERTICAL_PADDING_DP * density).toInt()
        val bottomOffset = (BOTTOM_OFFSET_DP * density).toInt()
        val palette = AppearanceManager.resolve(context, context.config)

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = ENTER_TRAVEL_DP * density
            elevation = 6f * density
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

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            leftMargin = sideMargin
            rightMargin = sideMargin
            bottomMargin = bottomOffset
        }
        decor.addView(bar, params)

        val handler = Handler(Looper.getMainLooper())
        var dismissed = false
        val remove = {
            if (!dismissed) {
                dismissed = true
                handler.removeCallbacksAndMessages(null)
                bar.animate().cancel()
                (bar.parent as? ViewGroup)?.removeView(bar)
            }
        }
        val autoDismiss = Runnable {
            bar.animate()
                .alpha(0f)
                .translationY(ENTER_TRAVEL_DP * density)
                .setDuration(EXIT_ANIM_MS)
                .withEndAction(remove)
                .start()
        }

        bar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTER_ANIM_MS)
            .start()
        handler.postDelayed(autoDismiss, VISIBLE_DURATION_MS)

        return Handle(remove)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private const val SIDE_MARGIN_DP = 20f
    private const val VERTICAL_PADDING_DP = 14f
    // Clears the 64dp bottom action bar (plus a small gap) in the full-screen media view.
    private const val BOTTOM_OFFSET_DP = 88f
    private const val CORNER_RADIUS_DP = 10f
    private const val TEXT_SIZE_SP = 14f
    private const val ENTER_TRAVEL_DP = 12f
    private const val ENTER_ANIM_MS = 180L
    private const val EXIT_ANIM_MS = 180L
    private const val VISIBLE_DURATION_MS = 3500L
}
