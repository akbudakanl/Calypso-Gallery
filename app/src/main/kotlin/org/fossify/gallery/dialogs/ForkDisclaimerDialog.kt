package org.fossify.gallery.dialogs

import android.widget.TextView
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity

/**
 * One-time notice shown before the app requests any runtime permission on a fresh install.
 * It cannot be dismissed with Back or by tapping outside: the user has to acknowledge it.
 */
class ForkDisclaimerDialog(
    private val activity: SimpleActivity,
    onAcknowledged: () -> Unit,
) {
    init {
        if (!activity.isFinishing && !activity.isDestroyed) {
            val promptView = activity.layoutInflater.inflate(
                org.fossify.commons.R.layout.dialog_message,
                null,
            )
            promptView.findViewById<TextView>(org.fossify.commons.R.id.message)
                .setText(R.string.fork_disclaimer_message)

            activity.getAlertDialogBuilder()
                .setCancelable(false)
                .setPositiveButton(R.string.fork_disclaimer_acknowledge) { dialog, _ ->
                    dialog.dismiss()
                    onAcknowledged()
                }
                .apply {
                    activity.setupDialogStuff(
                        view = promptView,
                        dialog = this,
                        titleId = R.string.fork_disclaimer_title,
                        cancelOnTouchOutside = false,
                    )
                }
        }
    }
}
