package org.fossify.gallery.dialogs

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.CountDownTimer
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogDeleteSecretsBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager

class DeleteSecretsDialog(
    private val activity: BaseSimpleActivity,
    private val messageText: String? = null,
    private val titleId: Int = 0,
    private val onConfirmed: () -> Unit
) {
    private var dialog: AlertDialog? = null
    private var countDownTimer: CountDownTimer? = null

    init {
        val binding = DialogDeleteSecretsBinding.inflate(activity.layoutInflater)
        val palette = AppearanceManager.resolve(activity, activity.config)
        val destructiveRed = activity.getDestructiveRedColor()
        val disabledBg = ColorUtils.setAlphaComponent(palette.text, 0x1F)
        val disabledText = ColorUtils.setAlphaComponent(palette.text, 0x61)
        val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.text, 0x1F))

        if (messageText != null) {
            binding.secretsWarningMessage.text = messageText
        }
        binding.secretsConfirmCheckbox.isEnabled = false
        binding.secretsConfirmCheckbox.isChecked = false
        binding.secretsBtnYes.isEnabled = false

        binding.secretsBtnYes.apply {
            text = activity.getString(org.fossify.commons.R.string.yes)
            backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(disabledBg, destructiveRed)
            )
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(disabledText, Color.WHITE)
                )
            )
            this.rippleColor = rippleColor
        }
        binding.secretsBtnNo.apply {
            text = activity.getString(org.fossify.commons.R.string.no)
            strokeColor = ColorStateList.valueOf(palette.primary)
            setTextColor(palette.text)
            this.rippleColor = rippleColor
        }

        val baseConfirmText = activity.getString(R.string.confirm_checkbox_label)
        binding.secretsConfirmCheckbox.text = "$baseConfirmText (10)"

        countDownTimer = object : CountDownTimer(10000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999) / 1000).toInt()
                binding.secretsConfirmCheckbox.text = "$baseConfirmText ($seconds)"
            }

            override fun onFinish() {
                binding.secretsConfirmCheckbox.text = baseConfirmText
                binding.secretsConfirmCheckbox.isEnabled = true
            }
        }.start()

        binding.secretsConfirmCheckbox.setOnCheckedChangeListener { _, isChecked ->
            binding.secretsBtnYes.isEnabled = isChecked
        }

        binding.secretsBtnYes.setOnClickListener {
            if (binding.secretsConfirmCheckbox.isChecked) {
                countDownTimer?.cancel()
                dialog?.dismiss()
                onConfirmed()
            }
        }

        binding.secretsBtnNo.setOnClickListener {
            countDownTimer?.cancel()
            dialog?.dismiss()
        }

        val builder = activity.getAlertDialogBuilder()
            .setOnDismissListener {
                countDownTimer?.cancel()
            }

        activity.setupDialogStuff(binding.root, builder, titleId = titleId) { alertDialog ->
            dialog = alertDialog
            binding.secretsConfirmCheckbox.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(disabledText, palette.text)
                )
            )
        }
    }
}
