package org.fossify.gallery.dialogs

import android.content.res.ColorStateList
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.databinding.DialogDeleteAutoBackupsBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager

class DeleteAutoBackupsDialog(
    private val activity: BaseSimpleActivity,
    private val messageText: String,
    private val onYes: () -> Unit,
    private val onNo: () -> Unit
) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogDeleteAutoBackupsBinding.inflate(activity.layoutInflater)
        val palette = AppearanceManager.resolve(activity, activity.config)
        val destructiveRed = activity.getDestructiveRedColor()
        val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.text, 0x1F))

        binding.deleteAutoBackupsMessage.text = messageText

        binding.deleteAutoBackupsBtnYes.apply {
            text = activity.getString(org.fossify.commons.R.string.yes)
            backgroundTintList = ColorStateList.valueOf(destructiveRed)
            setTextColor(Color.WHITE)
            this.rippleColor = rippleColor
            setOnClickListener {
                dialog?.dismiss()
                onYes()
            }
        }

        binding.deleteAutoBackupsBtnNo.apply {
            text = activity.getString(org.fossify.commons.R.string.no)
            strokeColor = ColorStateList.valueOf(palette.primary)
            setTextColor(palette.text)
            this.rippleColor = rippleColor
            setOnClickListener {
                dialog?.dismiss()
                onNo()
            }
        }

        val builder = activity.getAlertDialogBuilder()
        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            dialog = alertDialog
        }
    }
}
