package org.fossify.gallery.dialogs

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogCryptoConfirmationBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette

private const val DEST_CORNER_RADIUS_DP = 10f
private const val DEST_PAD_H_DP = 12f
private const val DEST_PAD_V_DP = 10f
private const val INFO_TEXT_SIZE_SP = 15f
private const val INFO_LINE_SPACING_MULTIPLIER = 1.15f

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
    val destinationLabel: String? = null,
    val destinationValue: String? = null,
    @StringRes val neutralButtonTextResId: Int? = null,
    val onNeutralClick: (() -> Unit)? = null,
    val onCancel: (() -> Unit)? = null,
    val callback: () -> Unit
) {
    init {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        val binding = DialogCryptoConfirmationBinding.inflate(activity.layoutInflater)
        binding.confirmationMessage.apply {
            text = message
            setTextColor(palette.text)
        }
        binding.confirmationInfo.apply {
            applyColorFilter(palette.text)
            contentDescription = activity.getString(infoTitleResId)
            setOnClickListener {
                showInfoDialog(palette)
            }
        }
        if (!destinationValue.isNullOrBlank()) {
            val padH = (DEST_PAD_H_DP * density).toInt()
            val padV = (DEST_PAD_V_DP * density).toInt()
            binding.confirmationDestinationContainer.apply {
                visibility = View.VISIBLE
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = DEST_CORNER_RADIUS_DP * density
                    setColor(palette.surface)
                    setStroke((1f * density).toInt().coerceAtLeast(1), palette.border)
                }
            }
            binding.confirmationDestinationLabel.apply {
                if (destinationLabel.isNullOrBlank()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    text = destinationLabel
                    setTextColor(palette.secondary)
                }
            }
            binding.confirmationDestinationValue.apply {
                text = destinationValue
                setTextColor(palette.text)
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
        if (neutralButtonTextResId != null && onNeutralClick != null) {
            builder.setNeutralButton(neutralButtonTextResId) { _, _ -> onNeutralClick() }
        }

        // Commons' AlertDialog theme still uses the pre-fork green colorPrimary for its
        // buttons. Override them with the active palette so all answers match the app.
        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            listOf(
                AlertDialog.BUTTON_POSITIVE,
                AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL
            ).forEach { which ->
                alertDialog.getButton(which)?.setTextColor(palette.primary)
            }
        }
    }

    private fun showInfoDialog(palette: AppearancePalette) {
        val paddingPx = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val scrollView = ScrollView(activity)
        val messageView = TextView(activity).apply {
            text = activity.getString(infoMessageResId)
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, INFO_TEXT_SIZE_SP)
            setLineSpacing(0f, INFO_LINE_SPACING_MULTIPLIER)
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
