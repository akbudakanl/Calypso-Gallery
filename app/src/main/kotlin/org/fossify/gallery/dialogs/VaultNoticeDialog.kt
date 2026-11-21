package org.fossify.gallery.dialogs

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.databinding.DialogVaultNoticeBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager

/**
 * Compact rounded dialog used by the periodic password reminder flow (HTML screens 3a, 3b, 4, 6).
 *
 * Commons' AlertDialog theme still uses the pre-fork green `colorPrimary`. Instead of letting the
 * platform buttons inherit it, this dialog draws its own buttons and recolours the whole card from
 * [AppearanceManager], so light/dark/custom palettes all match the rest of the app. Width is capped
 * at 330 dp, matching the HTML mockups, instead of stretching like a full-screen credential form.
 */
class VaultNoticeDialog(
    activity: Activity,
    title: String,
    message: CharSequence,
    positiveText: String,
    negativeText: String? = null,
    noteText: String? = null,
    cancelable: Boolean = false,
    showBiometricGlyph: Boolean = false,
    onPositive: () -> Unit,
    onNegative: (() -> Unit)? = null,
) {
    private var dialog: AlertDialog? = null

    init {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        val binding = DialogVaultNoticeBinding.inflate(activity.layoutInflater)
        binding.noticeRoot.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20f).toFloat()
            setColor(palette.surface)
        }
        binding.noticeTitle.apply {
            text = title
            setTextColor(palette.text)
        }
        binding.noticeMessage.apply {
            text = message
            setTextColor(palette.secondary)
        }

        if (showBiometricGlyph) {
            binding.noticeGlyphHost.beVisible()
            binding.noticeBiometricGlyph.applyPalette(palette)
            binding.noticeBiometricGlyph.post { binding.noticeBiometricGlyph.start() }
        }

        if (noteText != null) {
            binding.noticeNote.apply {
                text = noteText
                setTextColor(palette.text)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12f).toFloat()
                    setColor(ColorUtils.blendARGB(palette.surface, palette.primary, 0.08f))
                }
                visibility = View.VISIBLE
            }
        }

        binding.noticeActions.gravity = if (negativeText == null) Gravity.START else Gravity.END
        if (negativeText != null) {
            binding.noticeNegative.apply {
                text = negativeText
                setTextColor(palette.primary)
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog?.dismiss()
                    onNegative?.invoke()
                }
            }
        }
        binding.noticePositive.apply {
            text = positiveText
            setTextColor(palette.primary)
            setOnClickListener {
                dialog?.dismiss()
                onPositive()
            }
        }

        // Very long recovery text must stay inside the card; let the message area scroll.
        binding.noticeScroll.post {
            val maxHeight = dp(360f)
            if (binding.noticeScroll.height > maxHeight) {
                binding.noticeScroll.layoutParams.height = maxHeight
                binding.noticeScroll.requestLayout()
            }
        }

        val dialogWidth = minOf(dp(330f), activity.resources.displayMetrics.widthPixels - dp(32f))
        val builder = activity.getAlertDialogBuilder()
            .setView(binding.root)
            .setCancelable(cancelable)

        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            dialog = alertDialog
            alertDialog.window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }
}
