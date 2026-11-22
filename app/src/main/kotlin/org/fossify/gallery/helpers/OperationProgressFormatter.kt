package org.fossify.gallery.helpers

import org.fossify.commons.activities.BaseSimpleActivity

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
 * File-operation progress is now posted by [org.fossify.gallery.helpers.operations.FileOperationService],
 * a foreground service whose notification is exempt from POST_NOTIFICATIONS on Android 13+.
 * No runtime prompt is needed before starting an operation, and the previous one-shot
 * "asked" flag (which silently disabled progress forever after a single denial) is gone.
 *
 * Kept as a thin adapter so the existing call sites keep working without restructuring;
 * it never blocks or delays the operation.
 */
fun BaseSimpleActivity.ensureNotificationPermissionThenRun(onReady: (notificationsAllowed: Boolean) -> Unit) {
    onReady(true)
}
