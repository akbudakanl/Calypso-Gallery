package org.fossify.gallery.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogManageExtraViewerActionsBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.EXTRA_VIEWER_ACTION_CREATE_SHORTCUT
import org.fossify.gallery.helpers.EXTRA_VIEWER_ACTION_PRINT
import org.fossify.gallery.helpers.EXTRA_VIEWER_ACTION_RESIZE
import org.fossify.gallery.helpers.EXTRA_VIEWER_ACTION_SHOW_ON_MAP

class ManageExtraViewerActionsDialog(
    val activity: BaseSimpleActivity,
    val callback: (result: Int) -> Unit
) {
    private val binding = DialogManageExtraViewerActionsBinding.inflate(activity.layoutInflater)

    init {
        val actions = activity.config.extraViewerActions
        binding.apply {
            manageExtraActionShowOnMap.isChecked = actions and EXTRA_VIEWER_ACTION_SHOW_ON_MAP != 0
            manageExtraActionResize.isChecked = actions and EXTRA_VIEWER_ACTION_RESIZE != 0
            manageExtraActionPrint.isChecked = actions and EXTRA_VIEWER_ACTION_PRINT != 0
            manageExtraActionCreateShortcut.isChecked = actions and EXTRA_VIEWER_ACTION_CREATE_SHORTCUT != 0
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.manage_extra_viewer_actions)
            }
    }

    private fun dialogConfirmed() {
        var result = 0
        binding.apply {
            if (manageExtraActionShowOnMap.isChecked) result = result or EXTRA_VIEWER_ACTION_SHOW_ON_MAP
            if (manageExtraActionResize.isChecked) result = result or EXTRA_VIEWER_ACTION_RESIZE
            if (manageExtraActionPrint.isChecked) result = result or EXTRA_VIEWER_ACTION_PRINT
            if (manageExtraActionCreateShortcut.isChecked) result = result or EXTRA_VIEWER_ACTION_CREATE_SHORTCUT
        }

        activity.config.extraViewerActions = result
        callback(result)
    }
}
