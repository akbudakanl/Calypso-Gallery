package org.fossify.gallery.dialogs

import android.os.CountDownTimer
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogDeleteSecretsBinding

class DeleteSecretsDialog(
    private val activity: BaseSimpleActivity,
    private val messageText: String? = null,
    private val onConfirmed: () -> Unit
) {
    private var dialog: AlertDialog? = null
    private var countDownTimer: CountDownTimer? = null

    init {
        val binding = DialogDeleteSecretsBinding.inflate(activity.layoutInflater)
        if (messageText != null) {
            binding.secretsWarningMessage.text = messageText
        }
        binding.secretsConfirmCheckbox.isEnabled = false
        binding.secretsConfirmCheckbox.isChecked = false
        binding.secretsBtnYes.isEnabled = false

        binding.secretsBtnYes.text = activity.getString(org.fossify.commons.R.string.yes)
        binding.secretsBtnNo.text = activity.getString(org.fossify.commons.R.string.no)

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

        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            dialog = alertDialog
        }
    }
}
