package org.fossify.gallery.helpers

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_POST_NOTIFICATIONS
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.operations.FileOperationService

/**
 * Pure formatting helpers shared by the progress dialog and the foreground-service notification.
 */
object OperationProgressFormatter {
    private const val PERCENT_MAX = 100

    fun computePercent(current: Int, max: Int): Int {
        if (max <= 0) {
            return 0
        }
        val clamped = current.coerceIn(0, max)
        return ((clamped.toFloat() / max) * PERCENT_MAX).toInt().coerceIn(0, PERCENT_MAX)
    }

    fun formatCountSummary(
        current: Int,
        total: Int,
        barCurrent: Int = current,
        barMax: Int = total
    ): String {
        val percent = when {
            barMax > 0 -> computePercent(barCurrent, barMax)
            total > 0 -> computePercent(current, total)
            else -> 0
        }
        return "$current / $total ($percent%)"
    }

    fun resolveNotificationContentText(
        current: Int,
        max: Int,
        indeterminate: Boolean,
        explicitText: String
    ): String {
        if (explicitText.isNotEmpty()) {
            return explicitText
        }
        if (indeterminate) {
            return ""
        }
        return formatCountSummary(current = current, total = max, barCurrent = current, barMax = max)
    }
}

/**
 * Ensures Android 13+ notification permission has been requested once before starting a foreground
 * file operation so the two-tier [org.fossify.gallery.helpers.operations.FileOperationService]
 * notification is actually visible when the user backgrounds the app.
 */
fun BaseSimpleActivity.ensureNotificationPermissionThenRun(onReady: (notificationsAllowed: Boolean) -> Unit) {
    if (FileOperationService.ANDROID_NOTIFICATIONS_SUSPENDED) {
        onReady(false)
        return
    }
    if (!isTiramisuPlus() || hasPermission(PERMISSION_POST_NOTIFICATIONS)) {
        onReady(true)
        return
    }
    if (!config.notificationsEnabled || config.wasNotificationPermissionAsked) {
        onReady(false)
        return
    }
    config.wasNotificationPermissionAsked = true
    handleNotificationPermission { granted ->
        onReady(granted)
    }
}
