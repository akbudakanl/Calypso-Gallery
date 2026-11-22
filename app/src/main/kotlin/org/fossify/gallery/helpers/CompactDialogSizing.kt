package org.fossify.gallery.helpers

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import org.fossify.gallery.R

/**
 * Shared responsive sizing for compact themed card dialogs ([VaultNoticeDialog],
 * [VaultChoiceDialog], [GrantAllFilesDialog]).
 *
 * Reads [@dimen/compact_dialog_max_width] — 330 dp on phones and 480 dp on tablets / unfolded
 * dual-screen foldables (`values-sw600dp/dimens.xml`) — clamps to the available viewport width,
 * and keeps the window width updated across live orientation or fold/unfold transitions.
 */
object CompactDialogSizing {
    private const val HORIZONTAL_MARGIN_DP = 32f
    private const val MIN_WIDTH_DP = 260f
    private const val MAX_SCROLL_VIEWPORT_FRACTION = 0.55f

    internal fun computeWidthPx(maxWidthPx: Int, widthPixels: Int, density: Float): Int {
        val horizontalMarginPx = (HORIZONTAL_MARGIN_DP * density).toInt()
        val minWidthPx = (MIN_WIDTH_DP * density).toInt()
        return minOf(maxWidthPx, (widthPixels - horizontalMarginPx).coerceAtLeast(minWidthPx))
    }

    internal fun computeScrollMaxHeightPx(preferredMaxHeightPx: Int, heightPixels: Int): Int {
        val viewportCap = (heightPixels * MAX_SCROLL_VIEWPORT_FRACTION).toInt()
        return if (viewportCap > 0) minOf(preferredMaxHeightPx, viewportCap) else preferredMaxHeightPx
    }

    fun resolveWidthPx(activity: Activity): Int {
        val maxWidthPx = activity.resources.getDimensionPixelSize(R.dimen.compact_dialog_max_width)
        val metrics = activity.resources.displayMetrics
        return computeWidthPx(maxWidthPx, metrics.widthPixels, metrics.density)
    }

    fun resolveScrollMaxHeightPx(activity: Activity, dimenRes: Int): Int {
        val preferred = activity.resources.getDimensionPixelSize(dimenRes)
        return computeScrollMaxHeightPx(preferred, activity.resources.displayMetrics.heightPixels)
    }

    /**
     * Applies the responsive card width to [dialog] immediately and keeps it updated across live
     * configuration changes (rotation, split-screen resize, foldable fold/unfold) until dismissal.
     */
    fun applyResponsiveWidth(
        activity: Activity,
        dialog: AlertDialog,
        onConfigurationChangedExtra: (() -> Unit)? = null,
        onDismissExtra: (() -> Unit)? = null,
    ) {
        fun updateWindowWidth() {
            if (!dialog.isShowing) return
            dialog.window?.setLayout(resolveWidthPx(activity), WindowManager.LayoutParams.WRAP_CONTENT)
        }

        dialog.window?.setLayout(resolveWidthPx(activity), WindowManager.LayoutParams.WRAP_CONTENT)

        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                updateWindowWidth()
                onConfigurationChangedExtra?.invoke()
            }

            override fun onLowMemory() = Unit
        }
        activity.registerComponentCallbacks(callbacks)
        dialog.setOnDismissListener {
            runCatching { activity.unregisterComponentCallbacks(callbacks) }
            onDismissExtra?.invoke()
        }
    }
}
