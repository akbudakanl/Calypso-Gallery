package org.fossify.gallery.dialogs

import org.fossify.gallery.extensions.secureVaultWindow

import android.app.Activity
import android.content.res.ColorStateList
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogCryptoProgressBinding
import org.fossify.gallery.helpers.OperationNotificationCategory
import org.fossify.gallery.helpers.OperationProgressFormatter
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry

/**
 * Progress dialog for file operations. Progress is mirrored into the single foreground-service
 * notification through [ForegroundOperationRegistry]; the old independent progress notification
 * has been removed so users no longer see two competing notifications for one operation.
 */
class CryptoProgressDialog(
    private val activity: Activity,
    private val max: Int,
    private val titleId: Int = R.string.encrypting_folder,
    private val notificationChannelId: String = OperationNotificationCategory.CHANNEL_CRYPTO,
    private val notificationIconRes: Int = defaultNotificationIcon(titleId),
    @Suppress("UNUSED_PARAMETER") enableNotification: Boolean = true
) {
    private val category: String = OperationNotificationCategory.fromChannel(notificationChannelId)
    private var dialog: AlertDialog? = null

    @Volatile
    private var isDismissed = false
    private val binding = DialogCryptoProgressBinding.inflate(activity.layoutInflater)

    init {
        val primaryColor = activity.getProperPrimaryColor()
        val primaryTint = ColorStateList.valueOf(primaryColor)
        val trackTint = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 64))
        binding.cryptoProgressMessage.setText(titleId)
        binding.cryptoProgressBar.apply {
            progressTintList = primaryTint
            indeterminateTintList = primaryTint
            progressBackgroundTintList = trackTint
            this.max = maxOf(this@CryptoProgressDialog.max, 1)
            progress = 0
            isIndeterminate = this@CryptoProgressDialog.max <= 0
        }
        val initialSummary = OperationProgressFormatter.formatCountSummary(current = 0, total = max)
        binding.cryptoProgressCount.text = initialSummary

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

    /** Kept for call-site compatibility; progress is always published to the foreground service. */
    fun ensureNotificationStarted(enable: Boolean) = Unit

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
        val isIndeterminate = barMax <= 0 && total <= 0
        val safeBarMax = maxOf(barMax, 1)
        val clampedBarCurrent = barCurrent.coerceIn(0, safeBarMax)

        val countSummary = OperationProgressFormatter.formatCountSummary(
            current = current,
            total = total,
            barCurrent = clampedBarCurrent,
            barMax = barMax
        )
        // Privacy: only an anonymous count is ever sent to the notification. File names, paths,
        // album names and vault contents stay inside the on-screen dialog.
        val text = countSummary

        // Callers may publish progress from a background thread (e.g. delete loops).
        // Views must only ever be touched on the main thread; the notification is safe
        // to post directly and gives immediate feedback even before the UI catches up.
        ForegroundOperationRegistry.updateProgress(
            context = activity.applicationContext,
            category = category,
            current = clampedBarCurrent,
            max = if (isIndeterminate) 0 else safeBarMax,
            text = text
        )
        activity.runOnUiThread {
            if (isDismissed) return@runOnUiThread
            binding.cryptoProgressBar.isIndeterminate = isIndeterminate
            binding.cryptoProgressBar.max = safeBarMax
            binding.cryptoProgressBar.progress = clampedBarCurrent
            binding.cryptoProgressCount.text = countSummary
        }
    }

    fun updateNotifierOnly(
        current: Int,
        max: Int,
        @Suppress("UNUSED_PARAMETER") contentText: String,
        summaryText: String? = null
    ) {
        // contentText is a file name in practice — never expose it on the lock screen / shade.
        ForegroundOperationRegistry.updateProgress(
            context = activity.applicationContext,
            category = category,
            current = current,
            max = max,
            text = summaryText
        )
    }

    fun dismiss() {
        isDismissed = true
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
