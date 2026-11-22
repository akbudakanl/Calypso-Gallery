package org.fossify.gallery.dialogs

import org.fossify.gallery.extensions.secureVaultWindow

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogVaultNoticeBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.CompactDialogSizing

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
    canceledOnTouchOutside: Boolean = false,
    showBiometricGlyph: Boolean = false,
    showPullTutorial: Boolean = false,
    showPinDotsPreview: Boolean = false,
    showNoteShieldIcon: Boolean = false,
    filledPositiveButton: Boolean = false,
    alignSingleActionEnd: Boolean = false,
    onPositive: () -> Unit,
    onNegative: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    private var dialog: AlertDialog? = null

    init {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()

        val binding = DialogVaultNoticeBinding.inflate(activity.layoutInflater)
        val cardRadiusPx = activity.resources.getDimension(R.dimen.compact_dialog_corner_radius)
        binding.noticeRoot.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cardRadiusPx
            setColor(palette.surface)
            setStroke(dp(1f).coerceAtLeast(1), palette.border)
        }
        binding.noticeTitle.apply {
            text = title
            setTextColor(palette.text)
        }
        if (message.isEmpty()) {
            binding.noticeScroll.beGone()
        } else {
            binding.noticeMessage.apply {
                text = message
                setTextColor(palette.secondary)
            }
        }

        if (showBiometricGlyph) {
            binding.noticeGlyphHost.beVisible()
            binding.noticeBiometricGlyph.applyPalette(palette)
            binding.noticeBiometricGlyph.post {
                var parent = binding.noticeBiometricGlyph.parent
                while (parent is android.view.ViewGroup) {
                    parent.clipChildren = false
                    parent.clipToPadding = false
                    if (parent == binding.noticeRoot) break
                    parent = parent.parent
                }
                binding.noticeBiometricGlyph.start()
            }
        }

        if (showPullTutorial) {
            binding.noticePullTutorial.beVisible()
            binding.noticePullTutorial.applyPalette(palette)
            binding.noticePullTutorial.post {
                binding.noticePullTutorial.start()
            }
        }

        if (showPinDotsPreview) {
            binding.noticePinDotsPreview.beVisible()
            binding.noticePinDotsPreview.applyPalette(palette)
            binding.noticePinDotsPreview.post {
                binding.noticePinDotsPreview.start()
            }
        }

        if (noteText != null) {
            binding.noticeNote.apply {
                text = noteText
                setTextColor(palette.text)
            }
            if (showNoteShieldIcon) {
                binding.noticeNoteIcon.imageTintList = ColorStateList.valueOf(palette.primary)
                binding.noticeNoteIcon.visibility = View.VISIBLE
            }
            binding.noticeNoteHolder.apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(13f).toFloat()
                    setColor(ColorUtils.blendARGB(palette.surface, palette.primary, 0.08f))
                    if (showNoteShieldIcon) {
                        setStroke(
                            dp(1f).coerceAtLeast(1),
                            ColorUtils.blendARGB(palette.surface, palette.primary, 0.18f),
                        )
                    }
                }
                visibility = View.VISIBLE
            }
        }

        binding.noticeActions.gravity = if (negativeText == null && !alignSingleActionEnd) {
            Gravity.START
        } else {
            Gravity.END
        }
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
            if (filledPositiveButton) {
                setTextColor(palette.onPrimary)
                setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(999f).toFloat()
                    setColor(palette.primary)
                }
            } else {
                setTextColor(palette.primary)
            }
            setOnClickListener {
                dialog?.dismiss()
                onPositive()
            }
        }

        // Very long recovery text must stay inside the card; let the message area scroll.
        fun clampScrollHeight() {
            binding.noticeScroll.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            binding.noticeScroll.requestLayout()
            binding.noticeScroll.post {
                val maxHeight = CompactDialogSizing.resolveScrollMaxHeightPx(
                    activity,
                    R.dimen.compact_dialog_scroll_max_height,
                )
                if (binding.noticeScroll.height > maxHeight) {
                    binding.noticeScroll.layoutParams.height = maxHeight
                    binding.noticeScroll.requestLayout()
                }
            }
        }
        clampScrollHeight()

        val builder = activity.getAlertDialogBuilder()
            .setView(binding.root)
            .setCancelable(cancelable)

        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            alertDialog.secureVaultWindow()
            dialog = alertDialog
            alertDialog.setCanceledOnTouchOutside(canceledOnTouchOutside)
            alertDialog.setOnCancelListener { onCancel?.invoke() }
            CompactDialogSizing.applyResponsiveWidth(
                activity = activity,
                dialog = alertDialog,
                onConfigurationChangedExtra = { clampScrollHeight() },
            )
        }
    }
}
