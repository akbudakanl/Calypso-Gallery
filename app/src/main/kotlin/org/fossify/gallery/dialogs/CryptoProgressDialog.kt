package org.fossify.gallery.dialogs

import org.fossify.gallery.extensions.secureVaultWindow

import android.app.Activity
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogCryptoProgressBinding
import org.fossify.gallery.helpers.OperationProgressFormatter
import org.fossify.gallery.helpers.OperationProgressNotifier

class CryptoProgressDialog(
    private val activity: Activity,
    private val max: Int,
    private val titleId: Int = R.string.encrypting_folder,
    private val notificationChannelId: String = OperationProgressNotifier.CHANNEL_CRYPTO,
    private val notificationIconRes: Int = defaultNotificationIcon(titleId),
    enableNotification: Boolean = true
) {
    private var dialog: AlertDialog? = null

    @Volatile
    private var isDismissed = false
    private val binding = DialogCryptoProgressBinding.inflate(activity.layoutInflater)
    private var notifier: OperationProgressNotifier? = null
    private var lastCurrent = 0
    private var lastTotal = max
    private var lastBarCurrent = 0
    private var lastBarMax = max
    private var lastNotificationText: String? = null

    init {
        binding.cryptoProgressMessage.setText(titleId)
        binding.cryptoProgressBar.max = maxOf(max, 1)
        binding.cryptoProgressBar.progress = 0
        binding.cryptoProgressBar.isIndeterminate = max <= 0
        val initialSummary = OperationProgressFormatter.formatCountSummary(current = 0, total = max)
        binding.cryptoProgressCount.text = initialSummary

        if (enableNotification) {
            ensureNotificationStarted(true)
        }

        activity.getAlertDialogBuilder().apply {
            setCancelable(false)
            activity.setupDialogStuff(
                view = binding.root,
                dialog = this,
                titleId = titleId,
                cancelOnTouchOutside = false
            ) { alertDialog -> alertDialog.secureVaultWindow()
                if (isDismissed || activity.isFinishing || activity.isDestroyed) {
                    try {
                        alertDialog.dismiss()
                    } catch (_: Exception) {
                    }
                } else {
                    dialog = alertDialog
                    alertDialog.setCanceledOnTouchOutside(false)
                    alertDialog.setCancelable(false)
                    alertDialog.setOnKeyListener { _, keyCode, _ ->
                        keyCode == KeyEvent.KEYCODE_BACK
                    }
                }
            }
        }
    }

    fun ensureNotificationStarted(enable: Boolean) {
        if (!enable || isDismissed || notifier != null) {
            return
        }
        val title = activity.getString(titleId)
        val channelName = if (notificationChannelId == OperationProgressNotifier.CHANNEL_COPY_MOVE) {
            OperationProgressNotifier.CHANNEL_COPY_MOVE
        } else {
            activity.getString(R.string.encrypt_decrypt_notifications)
        }
        val isIndeterminate = lastBarMax <= 0 && lastTotal <= 0
        val safeBarMax = maxOf(lastBarMax, 1)
        val clampedBarCurrent = lastBarCurrent.coerceIn(0, safeBarMax)
        val summary = OperationProgressFormatter.formatCountSummary(
            current = lastCurrent,
            total = lastTotal,
            barCurrent = clampedBarCurrent,
            barMax = lastBarMax
        )
        notifier = OperationProgressNotifier(
            context = activity.applicationContext,
            channelId = notificationChannelId,
            channelName = channelName,
            title = title,
            smallIconRes = notificationIconRes
        ).apply {
            update(
                current = clampedBarCurrent,
                max = if (isIndeterminate) 0 else safeBarMax,
                contentText = lastNotificationText ?: summary
            )
            start()
        }
    }

    fun updateProgress(
        current: Int,
        total: Int,
        barCurrent: Int = current,
        barMax: Int = total,
        notificationText: String? = null
    ) {
        if (isDismissed) {
            return
        }
        lastCurrent = current
        lastTotal = total
        lastBarCurrent = barCurrent
        lastBarMax = barMax
        lastNotificationText = notificationText

        val isIndeterminate = barMax <= 0 && total <= 0
        val safeBarMax = maxOf(barMax, 1)
        val clampedBarCurrent = barCurrent.coerceIn(0, safeBarMax)
        binding.cryptoProgressBar.isIndeterminate = isIndeterminate
        binding.cryptoProgressBar.max = safeBarMax
        binding.cryptoProgressBar.progress = clampedBarCurrent

        val countSummary = OperationProgressFormatter.formatCountSummary(
            current = current,
            total = total,
            barCurrent = clampedBarCurrent,
            barMax = barMax
        )
        binding.cryptoProgressCount.text = countSummary
        notifier?.update(
            current = clampedBarCurrent,
            max = if (isIndeterminate) 0 else safeBarMax,
            contentText = notificationText ?: countSummary
        )
    }

    fun updateNotifierOnly(current: Int, max: Int, contentText: String) {
        notifier?.update(current = current, max = max, contentText = contentText)
    }

    fun dismiss() {
        isDismissed = true
        notifier?.finish()
        try {
            dialog?.dismiss()
        } catch (_: Exception) {
        }
        dialog = null
    }

    companion object {
        private fun defaultNotificationIcon(titleId: Int): Int {
            return when (titleId) {
                R.string.decrypting_files,
                R.string.decrypting_folder -> org.fossify.commons.R.drawable.ic_unhide_vector
                R.string.encrypting_files,
                R.string.encrypting_folder -> org.fossify.commons.R.drawable.ic_hide_vector
                else -> org.fossify.commons.R.drawable.ic_copy_vector
            }
        }
    }
}
