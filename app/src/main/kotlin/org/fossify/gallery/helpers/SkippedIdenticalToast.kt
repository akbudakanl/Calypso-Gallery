package org.fossify.gallery.helpers

import android.content.Context
import android.widget.Toast
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/**
 * Smart dialog and Automatic mode silently drop files whose name, bit-for-bit content and
 * timestamps are already present at the destination. Those skips never reach a dialog, so a
 * short toast at the end of the operation keeps the user informed about them.
 *
 * Callers must only pass the number of *silent* identical skips. Identical-content files with
 * different dates that were surfaced in [org.fossify.gallery.dialogs.SmartFileConflictDialog]
 * while "Always ask" is selected are deliberately excluded: the user already saw and confirmed
 * those in the thumbnail comparison dialog.
 *
 * The Standard dialog never compares contents, so it never produces silent identical skips and
 * is excluded here as well.
 */
fun Context.showSkippedIdenticalToast(count: Int) {
    if (count <= 0) return
    if (config.conflictHandlingMode == CONFLICT_HANDLING_STANDARD_DIALOG) return
    toast(
        resources.getQuantityString(R.plurals.identical_files_skipped, count, count),
        Toast.LENGTH_SHORT
    )
}
