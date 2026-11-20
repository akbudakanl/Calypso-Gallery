package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import org.fossify.gallery.views.VaultShutterLockView

/**
 * Shared styling and ambient pulse animation for the framed vault lock emblem
 * used by both [org.fossify.gallery.dialogs.VaultSecurityDialog] and
 * [org.fossify.gallery.activities.VaultLockActivity].
 */
object VaultLockEmblemHelper {
    fun applyStyle(
        context: Context,
        palette: AppearancePalette,
        haloView: View,
        coreView: View,
        lockView: VaultShutterLockView
    ) {
        val density = context.resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        val isLightBg = ColorUtils.calculateLuminance(palette.background) > 0.5
        val strongBorder = ColorUtils.blendARGB(palette.border, palette.text, 0.14f)
        val ringColor = ColorUtils.setAlphaComponent(palette.text, 40)
        val emblemSurface = ColorUtils.setAlphaComponent(palette.surface, if (isLightBg) 232 else 214)

        haloView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(strokePx, ringColor)
        }
        coreView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(emblemSurface)
            setStroke(strokePx, strongBorder)
        }
        lockView.setColors(palette.text, palette.surface, palette.text)
    }

    fun startHaloPulse(haloView: View): Animator? {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            haloView.alpha = 0.5f
            haloView.scaleX = 1.05f
            haloView.scaleY = 1.05f
            return null
        }
        val scaleX = ObjectAnimator.ofFloat(haloView, View.SCALE_X, 0.88f, 1.24f).apply {
            duration = 3800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(1.2f)
        }
        val scaleY = ObjectAnimator.ofFloat(haloView, View.SCALE_Y, 0.88f, 1.24f).apply {
            duration = 3800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(1.2f)
        }
        val alpha = ObjectAnimator.ofFloat(haloView, View.ALPHA, 0f, 0.65f, 0f).apply {
            duration = 3800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator(1.2f)
        }
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }
}
