package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import org.fossify.gallery.databinding.LayoutOnboardingAllFilesBinding
import org.fossify.gallery.databinding.LayoutOnboardingMediaPermissionBinding
import org.fossify.gallery.databinding.LayoutOnboardingWelcomeBinding

/**
 * Shared Varyant A styling, edge-to-edge inset wiring, and subtle motion helpers for the
 * 3-screen Calypso onboarding & permission flow. Used by [org.fossify.gallery.activities.OnboardingActivity]
 * and the full-screen fallback permission dialogs so every entry point looks identical.
 */
object OnboardingUiHelper {

    fun isLightColor(color: Int): Boolean {
        return AppearancePalette.contrastRatio(color, Color.WHITE) <
            AppearancePalette.contrastRatio(color, Color.BLACK)
    }

    fun applySystemBars(window: Window, palette: AppearancePalette) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
        val lightBars = isLightColor(palette.background)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    fun applyInsets(scrollView: View, footerView: View) {
        val baseScrollLeft = scrollView.paddingLeft
        val baseScrollTop = scrollView.paddingTop
        val baseScrollRight = scrollView.paddingRight
        val baseScrollBottom = scrollView.paddingBottom
        val baseFooterLeft = footerView.paddingLeft
        val baseFooterTop = footerView.paddingTop
        val baseFooterRight = footerView.paddingRight
        val baseFooterBottom = footerView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val safeBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                baseScrollLeft + safeBars.left,
                baseScrollTop + safeBars.top,
                baseScrollRight + safeBars.right,
                baseScrollBottom
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(footerView) { v, insets ->
            val safeBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                baseFooterLeft + safeBars.left,
                baseFooterTop,
                baseFooterRight + safeBars.right,
                baseFooterBottom + safeBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(scrollView)
        ViewCompat.requestApplyInsets(footerView)
    }

    fun styleWelcomePane(context: Context, binding: LayoutOnboardingWelcomeBinding, palette: AppearancePalette) {
        val density = context.resources.displayMetrics.density
        val iconBoxBg = resolveIconBoxBg(palette)

        binding.onboardingWelcomeRoot.setBackgroundColor(palette.background)
        binding.onboardingWelcomeFooter.setBackgroundColor(palette.background)
        binding.onboardingHeroLogo.applyPalette(palette)

        binding.onboardingWelcomeTitle.setTextColor(palette.text)
        binding.onboardingWelcomeMotto.setTextColor(palette.secondary)
        binding.onboardingWelcomePermissionNote.setTextColor(palette.secondary)

        styleCard(binding.onboardingCardOffline, palette, density)
        styleCard(binding.onboardingCardVault, palette, density)
        styleCard(binding.onboardingCardAlbums, palette, density)

        styleIconBox(binding.onboardingIconBoxOffline, binding.onboardingIconOffline, iconBoxBg, palette.text, density)
        styleIconBox(binding.onboardingIconBoxVault, binding.onboardingIconVault, iconBoxBg, palette.text, density)
        styleIconBox(binding.onboardingIconBoxAlbums, binding.onboardingIconAlbums, iconBoxBg, palette.text, density)

        binding.onboardingOfflineTitle.setTextColor(palette.text)
        binding.onboardingOfflineDesc.setTextColor(palette.secondary)
        binding.onboardingVaultTitle.setTextColor(palette.text)
        binding.onboardingVaultDesc.setTextColor(palette.secondary)
        binding.onboardingAlbumsTitle.setTextColor(palette.text)
        binding.onboardingAlbumsDesc.setTextColor(palette.secondary)

        stylePrimaryButton(binding.onboardingWelcomeContinueBtn, palette)
    }

    fun animateWelcomeEntrance(context: Context, binding: LayoutOnboardingWelcomeBinding) {
        binding.onboardingHeroLogo.startAnimation()
        if (!ValueAnimator.areAnimatorsEnabled()) return

        val slide = 10f * context.resources.displayMetrics.density
        val interpolator = DecelerateInterpolator(2f)
        val targets = listOf(
            binding.onboardingWelcomeTitle to 220L,
            binding.onboardingWelcomeMotto to 290L,
            binding.onboardingCardOffline to 360L,
            binding.onboardingCardVault to 430L,
            binding.onboardingCardAlbums to 500L
        )
        targets.forEach { (view, delay) ->
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = slide
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420L)
                .setStartDelay(delay)
                .setInterpolator(interpolator)
                .start()
        }
    }

    fun styleMediaPermissionPane(
        context: Context,
        binding: LayoutOnboardingMediaPermissionBinding,
        palette: AppearancePalette
    ): Animator? {
        val density = context.resources.displayMetrics.density
        val iconBoxBg = resolveIconBoxBg(palette)

        binding.onboardingMediaRoot.setBackgroundColor(palette.background)
        binding.onboardingMediaFooter.setBackgroundColor(palette.background)

        styleEmblem(
            haloView = binding.onboardingMediaHalo,
            coreView = binding.onboardingMediaEmblemCore,
            coreIcon = binding.onboardingMediaEmblemIcon,
            badgeView = binding.onboardingMediaBadgeDot,
            badgeIcon = binding.onboardingMediaBadgeIcon,
            palette = palette,
            density = density
        )

        binding.onboardingMediaTitle.setTextColor(palette.text)
        binding.onboardingMediaDesc.setTextColor(palette.secondary)
        binding.onboardingMediaHint.setTextColor(palette.secondary)

        styleCard(binding.onboardingMediaStepsCard, palette, density, radiusDp = 18f)
        binding.onboardingMediaStepsHeader.setTextColor(palette.secondary)

        listOf(
            binding.onboardingStepNum1,
            binding.onboardingStepNum2,
            binding.onboardingStepNum3
        ).forEach { badge ->
            badge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(iconBoxBg)
            }
            badge.setTextColor(palette.secondary)
        }

        binding.onboardingStepNum4.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primary)
        }
        binding.onboardingStepNum4.setTextColor(palette.onPrimary)

        listOf(
            binding.onboardingStepLabel1,
            binding.onboardingStepLabel2,
            binding.onboardingStepLabel3,
            binding.onboardingStepLabel4
        ).forEach { label ->
            label.setTextColor(palette.text)
        }

        binding.onboardingStepCheckIcon.imageTintList = ColorStateList.valueOf(palette.text)

        listOf(
            binding.onboardingStepDiv1,
            binding.onboardingStepDiv2,
            binding.onboardingStepDiv3
        ).forEach { div ->
            div.setBackgroundColor(palette.border)
        }

        stylePrimaryButton(binding.onboardingMediaSettingsBtn, palette)
        styleGhostButton(binding.onboardingMediaCloseBtn, palette)

        return createHaloPulseAnimator(binding.onboardingMediaHalo)
    }

    fun styleAllFilesPane(
        context: Context,
        binding: LayoutOnboardingAllFilesBinding,
        palette: AppearancePalette
    ): Animator? {
        val density = context.resources.displayMetrics.density
        val iconBoxBg = resolveIconBoxBg(palette)

        binding.onboardingAllFilesRoot.setBackgroundColor(palette.background)
        binding.onboardingAllFilesFooter.setBackgroundColor(palette.background)

        styleEmblem(
            haloView = binding.onboardingAllFilesHalo,
            coreView = binding.onboardingAllFilesEmblemCore,
            coreIcon = binding.onboardingAllFilesEmblemIcon,
            badgeView = binding.onboardingAllFilesBadgeDot,
            badgeIcon = binding.onboardingAllFilesBadgeIcon,
            palette = palette,
            density = density
        )

        binding.onboardingAllFilesTitle.setTextColor(palette.text)
        binding.onboardingAllFilesDesc.setTextColor(palette.secondary)

        styleCard(binding.onboardingAfCardVault, palette, density)
        styleCard(binding.onboardingAfCardBackup, palette, density)
        styleCard(binding.onboardingAfCardOffline, palette, density)

        styleIconBox(binding.onboardingAfIconBoxVault, binding.onboardingAfIconVault, iconBoxBg, palette.text, density)
        styleIconBox(binding.onboardingAfIconBoxBackup, binding.onboardingAfIconBackup, iconBoxBg, palette.text, density)
        styleIconBox(binding.onboardingAfIconBoxOffline, binding.onboardingAfIconOffline, iconBoxBg, palette.text, density)

        binding.onboardingAfVaultTitle.setTextColor(palette.text)
        binding.onboardingAfVaultDesc.setTextColor(palette.secondary)
        binding.onboardingAfBackupTitle.setTextColor(palette.text)
        binding.onboardingAfBackupDesc.setTextColor(palette.secondary)
        binding.onboardingAfOfflineTitle.setTextColor(palette.text)
        binding.onboardingAfOfflineDesc.setTextColor(palette.secondary)

        stylePrimaryButton(binding.onboardingAllFilesGrantBtn, palette)
        styleGhostButton(binding.onboardingAllFilesSkipBtn, palette)

        return createHaloPulseAnimator(binding.onboardingAllFilesHalo)
    }

    private fun resolveIconBoxBg(palette: AppearancePalette): Int {
        return if (isLightColor(palette.background)) {
            ColorUtils.blendARGB(palette.surface, palette.text, 0.055f)
        } else {
            ColorUtils.blendARGB(palette.surface, Color.WHITE, 0.06f)
        }
    }

    private fun styleCard(view: View, palette: AppearancePalette, density: Float, radiusDp: Float = 16f) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(palette.surface)
            setStroke((1f * density).toInt().coerceAtLeast(1), palette.border)
        }
    }

    private fun styleIconBox(box: View, icon: ImageView, bgColor: Int, tintColor: Int, density: Float) {
        box.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 11f * density
            setColor(bgColor)
        }
        icon.imageTintList = ColorStateList.valueOf(tintColor)
    }

    private fun styleEmblem(
        haloView: View,
        coreView: View,
        coreIcon: ImageView,
        badgeView: View,
        badgeIcon: ImageView,
        palette: AppearancePalette,
        density: Float
    ) {
        val ringColor = ColorUtils.setAlphaComponent(palette.text, 40)
        val strongBorder = ColorUtils.blendARGB(palette.border, palette.text, 0.14f)

        haloView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((1f * density).toInt().coerceAtLeast(1), ringColor)
        }

        coreView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(palette.surface)
            setStroke((1f * density).toInt().coerceAtLeast(1), strongBorder)
        }
        coreIcon.imageTintList = ColorStateList.valueOf(palette.text)

        badgeView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(palette.primary)
            setStroke((2.5f * density).toInt().coerceAtLeast(2), palette.background)
        }
        badgeIcon.imageTintList = ColorStateList.valueOf(palette.onPrimary)
    }

    private fun stylePrimaryButton(button: MaterialButton, palette: AppearancePalette) {
        button.backgroundTintList = ColorStateList.valueOf(palette.primary)
        button.setTextColor(palette.onPrimary)
        button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.onPrimary, 40))
    }

    private fun styleGhostButton(button: MaterialButton, palette: AppearancePalette) {
        button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        button.setTextColor(palette.secondary)
        button.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.text, 24))
    }

    private fun createHaloPulseAnimator(haloView: View): Animator? {
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
