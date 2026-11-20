package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.gallery.R
import kotlin.math.abs

/**
 * Pull-to-open gesture for the encrypted vault.
 *
 * Nothing is shown until the pull crosses [HOLD_OPEN_THRESHOLD_DP]. After it has been held for
 * [ARM_DELAY_MS] a single bounce animation plays and the label switches to "release to open";
 * releasing then opens the credential prompt. The lock icon stays locked - no shackle opening,
 * because nothing has been decrypted at this point. Short/early releases only snap back.
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
    private var enterAnimators = mutableListOf<Animator>()
    private var armAnimators = mutableListOf<Animator>()
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
                                    armAfterDelay()
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

    private fun showIndicator() {
        cancelEnter()
        val idleTextColor = textColor()
        iconView.applyColorFilter(idleTextColor)
        iconView.alpha = 1f
        iconView.scaleX = 0.72f
        iconView.scaleY = 0.72f

        textView.text = textView.context.getString(R.string.keep_holding_to_open_vault)
        textView.setTextColor(idleTextColor)
        textView.alpha = 0.85f

        indicatorView.animate().cancel()
        indicatorView.alpha = 0f
        indicatorView.translationY = -10f * density
        indicatorView.beVisible()
        indicatorView.bringToFront()

        enterAnimators += ObjectAnimator.ofFloat(indicatorView, View.ALPHA, 0f, 1f).apply {
            duration = ENTER_ANIM_MS
            interpolator = DecelerateInterpolator()
        }
        enterAnimators += ObjectAnimator.ofFloat(indicatorView, View.TRANSLATION_Y, -10f * density, 0f).apply {
            duration = ENTER_ANIM_MS
            interpolator = DecelerateInterpolator()
        }
        enterAnimators += ObjectAnimator.ofFloat(iconView, View.SCALE_X, 0.72f, 1f).apply {
            duration = ICON_ENTER_MS
            interpolator = OvershootInterpolator(1.5f)
        }
        enterAnimators += ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 0.72f, 1f).apply {
            duration = ICON_ENTER_MS
            interpolator = OvershootInterpolator(1.5f)
        }
        enterAnimators.forEach { it.start() }
    }

    private fun hideIndicator() {
        cancelEnter()
        indicatorView.animate().cancel()
        indicatorView.beGone()
        indicatorView.alpha = 1f
        indicatorView.translationY = 0f
    }

    private fun armAfterDelay() {
        cancelArm()
        val runnable = Runnable {
            armRunnable = null
            if (thresholdMet) playReadyAnimation()
        }
        armRunnable = runnable
        iconView.postDelayed(runnable, ARM_DELAY_MS)
    }

    /** Smooth spring pulse and accent color transition when the vault is armed. */
    private fun playReadyAnimation() {
        armed = true
        val startColor = textColor()
        val endColor = primaryColor()
        indicatorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        textView.text = textView.context.getString(R.string.release_to_open_vault)
        textView.setTextColor(endColor)
        textView.alpha = 0.4f
        armAnimators += ObjectAnimator.ofFloat(textView, View.ALPHA, 0.4f, 1f).apply {
            duration = ARM_ANIM_MS
            interpolator = DecelerateInterpolator()
        }

        armAnimators += ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.22f, 1.06f).apply {
            duration = ARM_ANIM_MS
            interpolator = OvershootInterpolator(1.8f)
        }
        armAnimators += ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.22f, 1.06f).apply {
            duration = ARM_ANIM_MS
            interpolator = OvershootInterpolator(1.8f)
        }
        armAnimators += ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor).apply {
            duration = ARM_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                iconView.applyColorFilter(animator.animatedValue as Int)
            }
        }
        armAnimators.forEach { it.start() }
    }

    private fun resetThresholdState() {
        thresholdMet = false
        armed = false
        cancelEnter()
        cancelArm()
        iconView.scaleX = 1f
        iconView.scaleY = 1f
        iconView.alpha = 1f
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
        textView.alpha = 1f
    }

    private fun cancelEnter() {
        enterAnimators.forEach { it.cancel() }
        enterAnimators.clear()
    }

    private fun cancelArm() {
        armRunnable?.let { iconView.removeCallbacks(it) }
        armRunnable = null
        armAnimators.forEach { it.cancel() }
        armAnimators.clear()
        iconView.applyColorFilter(textColor())
    }

    companion object {
        private const val MAX_TRANSLATION_DP = 160f
        private const val HOLD_OPEN_THRESHOLD_DP = 95f
        private const val TRANSLATION_FACTOR = 0.45f
        private const val ARM_DELAY_MS = 420L
        private const val ENTER_ANIM_MS = 180L
        private const val ICON_ENTER_MS = 240L
        private const val ARM_ANIM_MS = 340L
        private const val RESET_ANIM_MS = 220L
    }
}
