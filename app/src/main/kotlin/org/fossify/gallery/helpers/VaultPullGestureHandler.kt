package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.gallery.R
import kotlin.math.abs

/**
 * Pull-to-open gesture for the encrypted vault.
 *
 * Nothing is shown until the pull crosses [HOLD_OPEN_THRESHOLD_DP]. At that moment the framed
 * badge appears and the lock icon drops in from above with a single spring settle (the approved
 * prototype motion); the label only switches to "release to open" once the drop has completed.
 * The lock stays locked - no shackle opening, because nothing has been decrypted at this point.
 * Short/early releases only snap back.
 */
class VaultPullGestureHandler(
    private val containerView: View,
    private val indicatorView: View,
    private val iconView: ImageView,
    private val textView: TextView,
    private val isListAtTop: () -> Boolean,
    private val primaryColor: () -> Int,
    private val textColor: () -> Int,
    private val dispatchToChildren: (MotionEvent) -> Unit,
    private val onVaultOpen: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(containerView.context).scaledTouchSlop
    private val density = containerView.context.resources.displayMetrics.density
    private val maxTranslation = MAX_TRANSLATION_DP * density
    private val holdThreshold = HOLD_OPEN_THRESHOLD_DP * density

    private var startRawX = 0f
    private var startRawY = 0f
    private var gestureStarted = false
    private var cancelDispatched = false
    private var thresholdMet = false
    private var armRunnable: Runnable? = null
    private var dropAnimators = mutableListOf<Animator>()
    private var armAnimators = mutableListOf<Animator>()
    private var badgeDrawable: GradientDrawable? = null
    private var armed = false

    fun handleTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStarted = isListAtTop()
                if (gestureStarted) {
                    startRawX = ev.rawX
                    startRawY = ev.rawY
                }
                resetThresholdState()
                cancelDispatched = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureStarted) {
                    val deltaX = abs(ev.rawX - startRawX)
                    val deltaY = ev.rawY - startRawY
                    if (deltaY > 0 && deltaY > deltaX) {
                        val translation = (deltaY * TRANSLATION_FACTOR).coerceIn(0f, maxTranslation)
                        if (translation > touchSlop) {
                            if (!cancelDispatched) {
                                cancelDispatched = true
                                val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                                dispatchToChildren(cancelEvent)
                                cancelEvent.recycle()
                            }
                            containerView.translationY = translation

                            if (translation >= holdThreshold) {
                                if (!thresholdMet) {
                                    thresholdMet = true
                                    showIndicator()
                                }
                            } else if (!armed) {
                                // Keep the pull completely silent until the vault threshold.
                                hideIndicator()
                            }
                            return true
                        }
                    } else if (deltaY < 0 && containerView.translationY > 0f) {
                        containerView.translationY = 0f
                        hideIndicator()
                        if (isListAtTop()) gestureStarted = false
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (gestureStarted) {
                    val shouldOpenVault = thresholdMet && armed
                    resetGesture()
                    gestureStarted = false
                    if (shouldOpenVault) onVaultOpen()
                    if (cancelDispatched) return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (gestureStarted) {
                    resetGesture()
                    gestureStarted = false
                }
            }
        }
        return false
    }

    /**
     * Sizes the icon like the prototype emblem but keeps the background transparent. The circle
     * exists only while the one-shot glow is playing; afterwards the icon sits on the bare screen.
     */
    private fun styleBadge() {
        val badgeSize = (BADGE_SIZE_DP * density).toInt()
        val inset = ((BADGE_SIZE_DP - ICON_SIZE_DP) * density / 2f).toInt()
        iconView.layoutParams = iconView.layoutParams.apply {
            width = badgeSize
            height = badgeSize
        }
        iconView.setPadding(inset, inset, inset, inset)
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        badgeDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(0, Color.TRANSPARENT)
        }
        iconView.background = badgeDrawable
    }

    private fun showIndicator() {
        cancelEnter()
        styleBadge()
        val idleColor = textColor()
        iconView.applyColorFilter(idleColor)
        iconView.alpha = 0f
        iconView.translationY = -DROP_TRAVEL_DP * density
        iconView.scaleX = 1f
        iconView.scaleY = 1f

        textView.text = textView.context.getString(R.string.keep_holding_to_open_vault)
        textView.setTextColor(idleColor)
        textView.alpha = 1f

        indicatorView.animate().cancel()
        indicatorView.alpha = 1f
        indicatorView.translationY = 0f
        indicatorView.beVisible()
        indicatorView.bringToFront()

        // One-shot drop: badge is already visible, the locked icon springs into place.
        dropAnimators += ObjectAnimator.ofFloat(iconView, View.TRANSLATION_Y, -DROP_TRAVEL_DP * density, 0f).apply {
            duration = DROP_DURATION_MS
            interpolator = OvershootInterpolator(1.45f)
        }
        dropAnimators += ObjectAnimator.ofFloat(iconView, View.ALPHA, 0f, 1f).apply {
            duration = FADE_IN_MS
        }
        dropAnimators.forEach { it.start() }
        playBadgeGlow()
        armAfterDelay()
    }

    /** Single soft ring burst when the badge lands - no repeated or looping glow. */
    private fun playBadgeGlow() {
        val badge = badgeDrawable ?: return
        val maxStroke = (14f * density).toInt()
        armAnimators += ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLOW_DURATION_MS
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                val alpha = (110 * (1f - t)).toInt().coerceIn(0, 255)
                badge.setStroke(
                    (maxStroke * t).toInt().coerceAtLeast(0),
                    ColorUtils.setAlphaComponent(primaryColor(), alpha)
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    badge.setStroke(0, Color.TRANSPARENT)
                }
            })
        }.also { it.start() }
    }

    private fun hideIndicator() {
        cancelEnter()
        cancelArm()
        indicatorView.animate().cancel()
        indicatorView.beGone()
        indicatorView.alpha = 1f
        indicatorView.translationY = 0f
    }

    /** The label flips only after the drop has settled - releasing earlier just snaps back. */
    private fun armAfterDelay() {
        val runnable = Runnable {
            armRunnable = null
            if (thresholdMet) {
                armed = true
                indicatorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                textView.text = textView.context.getString(R.string.release_to_open_vault)
                textView.setTextColor(primaryColor())
                armAnimators += ObjectAnimator.ofFloat(textView, View.ALPHA, 1f, 0.45f, 1f).apply {
                    duration = LABEL_PULSE_MS
                    interpolator = DecelerateInterpolator()
                }.also { it.start() }
            }
        }
        armRunnable = runnable
        // Arm exactly when the drop and glow finish - no extra hold window after the settle.
        iconView.postDelayed(runnable, maxOf(DROP_DURATION_MS, GLOW_DURATION_MS))
    }

    private fun resetThresholdState() {
        thresholdMet = false
        armed = false
        cancelEnter()
        cancelArm()
        iconView.scaleX = 1f
        iconView.scaleY = 1f
        iconView.alpha = 1f
        iconView.translationY = 0f
        textView.alpha = 1f
        indicatorView.translationY = 0f
    }

    private fun resetGesture() {
        containerView.animate()
            .translationY(0f)
            .setDuration(RESET_ANIM_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        cancelEnter()
        cancelArm()
        indicatorView.animate()
            .alpha(0f)
            .translationY(-8f * density)
            .setDuration(RESET_ANIM_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                indicatorView.beGone()
                indicatorView.alpha = 1f
                indicatorView.translationY = 0f
            }
            .start()

        thresholdMet = false
        armed = false
        iconView.scaleX = 1f
        iconView.scaleY = 1f
        iconView.alpha = 1f
        iconView.translationY = 0f
        textView.alpha = 1f
    }

    private fun cancelEnter() {
        dropAnimators.forEach { it.cancel() }
        dropAnimators.clear()
    }

    private fun cancelArm() {
        armRunnable?.let { iconView.removeCallbacks(it) }
        armRunnable = null
        armAnimators.forEach { it.cancel() }
        armAnimators.clear()
        badgeDrawable?.setStroke(0, Color.TRANSPARENT)
        iconView.applyColorFilter(textColor())
    }

    companion object {
        private const val MAX_TRANSLATION_DP = 160f
        private const val HOLD_OPEN_THRESHOLD_DP = 95f
        private const val TRANSLATION_FACTOR = 0.45f
        private const val BADGE_SIZE_DP = 64f
        private const val ICON_SIZE_DP = 34f
        private const val DROP_TRAVEL_DP = 72f
        private const val DROP_DURATION_MS = 720L
        private const val FADE_IN_MS = 260L
        private const val GLOW_DURATION_MS = 700L
        private const val LABEL_PULSE_MS = 340L
        private const val RESET_ANIM_MS = 220L
    }
}
