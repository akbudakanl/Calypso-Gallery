package org.fossify.gallery.dialogs

import android.app.Activity
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogCryptoConfirmationBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette

/**
 * Asks for confirmation before encrypting or decrypting an album or file(s),
 * with an info icon that opens a detailed explanation dialog.
 *
 * For encryption, "No" is placed on the left side and "Yes" on the right side.
 * For decryption ([isDecrypt] = true), "No" remains on the right side and "Yes" on the left side.
 */
class EncryptConfirmationDialog(
    val activity: Activity,
    val message: String,
    @StringRes val infoTitleResId: Int = R.string.encrypt_info_title,
    @StringRes val infoMessageResId: Int = R.string.encrypt_info_message,
    val isDecrypt: Boolean = false,
    val onCancel: (() -> Unit)? = null,
    val callback: () -> Unit
) {
    init {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val binding = DialogCryptoConfirmationBinding.inflate(activity.layoutInflater)
        binding.confirmationMessage.apply {
            text = message
            setTextColor(activity.getProperTextColor())
        }
        binding.confirmationInfo.apply {
            applyColorFilter(activity.getProperTextColor())
            contentDescription = activity.getString(infoTitleResId)
            setOnClickListener {
                showInfoDialog(palette)
            }
        }

        val builder = activity.getAlertDialogBuilder()
            .setView(binding.root)
        if (isDecrypt) {
            builder
                .setPositiveButton(org.fossify.commons.R.string.no) { _, _ -> onCancel?.invoke() }
                .setNegativeButton(org.fossify.commons.R.string.yes) { _, _ -> callback() }
        } else {
            builder
                .setNegativeButton(org.fossify.commons.R.string.no) { _, _ -> onCancel?.invoke() }
                .setPositiveButton(org.fossify.commons.R.string.yes) { _, _ -> callback() }
        }

        // Commons' AlertDialog theme still uses the pre-fork green colorPrimary for its
        // buttons. Override them with the active palette so both answers match the app.
        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).forEach { which ->
                alertDialog.getButton(which)?.setTextColor(palette.primary)
            }
        }
    }

    private fun showInfoDialog(palette: AppearancePalette) {
        val paddingPx = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val scrollView = ScrollView(activity)
        val messageView = TextView(activity).apply {
            text = activity.getString(infoMessageResId)
            setTextColor(activity.getProperTextColor())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(0f, 1.15f)
            setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)
        }
        scrollView.addView(messageView)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply {
                activity.setupDialogStuff(scrollView, this, infoTitleResId) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(palette.primary)
                }
            }
    }
}
