package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.ObjectAnimator
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
        iconView.applyColorFilter(textColor())
        iconView.scaleX = 1f
        iconView.scaleY = 1f
        indicatorView.beVisible()
        indicatorView.bringToFront()
        indicatorView.alpha = 1f
        textView.text = textView.context.getString(R.string.keep_holding_to_open_vault)
    }

    private fun hideIndicator() {
        indicatorView.beGone()
        indicatorView.alpha = 1f
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

    /** One-shot bounce + tint flash. The lock itself never opens. */
    private fun playReadyAnimation() {
        armed = true
        textView.text = textView.context.getString(R.string.release_to_open_vault)
        indicatorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        armAnimators += ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.18f, 1f).apply {
            duration = 440L
            interpolator = OvershootInterpolator(1.4f)
        }
        armAnimators += ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.18f, 1f).apply {
            duration = 440L
            interpolator = OvershootInterpolator(1.4f)
        }
        armAnimators += ObjectAnimator.ofFloat(iconView, "alpha", 1f, 0.45f, 1f).apply {
            duration = 440L
            interpolator = DecelerateInterpolator()
        }
        iconView.applyColorFilter(primaryColor())
        armAnimators.forEach { it.start() }
    }

    private fun resetThresholdState() {
        thresholdMet = false
        armed = false
        cancelArm()
        iconView.scaleX = 1f
        iconView.scaleY = 1f
        iconView.alpha = 1f
    }

    private fun resetGesture() {
        containerView.animate()
            .translationY(0f)
            .setDuration(RESET_ANIM_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        indicatorView.animate()
            .alpha(0f)
            .setDuration(RESET_ANIM_MS)
            .withEndAction {
                indicatorView.beGone()
                indicatorView.alpha = 1f
            }
            .start()

        resetThresholdState()
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
        private const val RESET_ANIM_MS = 220L
    }
}
