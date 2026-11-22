package org.fossify.gallery.dialogs

import android.animation.Animator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.databinding.DialogGrantAllFilesBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchGrantAllFilesIntent
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.VaultLockEmblemHelper

/**
 * Theme-aware dialog requesting All Files Access (MANAGE_EXTERNAL_STORAGE), required for creating
 * or managing the encrypted vault and working with hidden folders on Android 11+.
 *
 * Uses [AppearancePalette] for the card surface, border, animated emblem halo/badge, and buttons
 * so Light, Dark, Midnight Blue, Warm Paper, Deep Forest, and user-created Custom themes all
 * render harmoniously without Commons' default green button tint.
 */
class GrantAllFilesDialog(
    val activity: BaseSimpleActivity,
    private val onGrantedClick: (() -> Unit)? = null,
    private val onCancelled: (() -> Unit)? = null,
) {
    private var dialog: AlertDialog? = null
    private var haloAnimator: Animator? = null

    init {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()
        val strokePx = dp(1f).coerceAtLeast(1)

        val binding = DialogGrantAllFilesBinding.inflate(activity.layoutInflater)
        binding.grantAllFilesHolder.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20f).toFloat()
            setColor(palette.surface)
            setStroke(strokePx, palette.border)
        }

        // Style emblem (halo + squircle core + primary badge dot) to match the active palette
        val ringColor = ColorUtils.setAlphaComponent(palette.text, 40)
        val strongBorder = ColorUtils.blendARGB(palette.border, palette.text, 0.14f)
        val isLight = AppearancePalette.isLightBackground(palette.background)
        val coreBg = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.07f else 0.11f)

        binding.grantAllFilesHalo.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(strokePx, ringColor)
        }
        binding.grantAllFilesEmblemCore.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20f).toFloat()
            setColor(coreBg)
            setStroke(strokePx, strongBorder)
        }
        binding.grantAllFilesImage.imageTintList = ColorStateList.valueOf(palette.text)

        binding.grantAllFilesBadgeDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primary)
            setStroke(dp(2.5f).coerceAtLeast(2), palette.surface)
        }
        binding.grantAllFilesBadgeIcon.imageTintList = ColorStateList.valueOf(palette.onPrimary)

        binding.grantAllFilesTitle.setTextColor(palette.text)
        binding.grantAllFilesDescription.setTextColor(palette.secondary)

        val noteBg = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.08f else 0.12f)
        binding.grantAllFilesNote.apply {
            setTextColor(palette.text)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f).toFloat()
                setColor(noteBg)
            }
        }

        val cancelRippleColor = ColorUtils.blendARGB(palette.surface, palette.primary, if (isLight) 0.14f else 0.22f)
        val cancelMask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999f).toFloat()
            setColor(Color.WHITE)
        }
        binding.grantAllFilesNegative.apply {
            setTextColor(palette.primary)
            background = RippleDrawable(ColorStateList.valueOf(cancelRippleColor), null, cancelMask)
            setOnClickListener {
                dialog?.dismiss()
                onCancelled?.invoke()
            }
        }

        val positiveFill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999f).toFloat()
            setColor(palette.primary)
        }
        val positiveMask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999f).toFloat()
            setColor(Color.WHITE)
        }
        val positiveRipple = ColorUtils.setAlphaComponent(palette.onPrimary, 48)
        binding.grantAllFilesPositive.apply {
            setTextColor(palette.onPrimary)
            background = RippleDrawable(ColorStateList.valueOf(positiveRipple), positiveFill, positiveMask)
            setOnClickListener {
                dialog?.dismiss()
                onGrantedClick?.invoke()
                activity.launchGrantAllFilesIntent()
            }
        }

        haloAnimator = VaultLockEmblemHelper.startHaloPulse(binding.grantAllFilesHalo)

        val dialogWidth = minOf(dp(330f), activity.resources.displayMetrics.widthPixels - dp(32f))
        val builder = activity.getAlertDialogBuilder()
            .setView(binding.root)
            .setCancelable(true)

        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            dialog = alertDialog
            alertDialog.setCanceledOnTouchOutside(true)
            alertDialog.setOnCancelListener {
                onCancelled?.invoke()
            }
            alertDialog.setOnDismissListener {
                haloAnimator?.cancel()
                haloAnimator = null
            }
            alertDialog.window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }
}

