package org.fossify.gallery.helpers

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.gallery.R
import org.fossify.gallery.views.VaultPullOrbitalView
import kotlin.math.abs

/**
 * Handles the pull gesture at the top of a list that opens the encrypted vault.
 *
 * Pull-to-refresh has been removed from this gesture: a short pull simply snaps back, while a
 * sustained pull past [HOLD_OPEN_THRESHOLD_DP] arms the orbital indicator and opens the vault on
 * release. Refresh remains available from the overflow menu / Settings toggle.
 */
class VaultPullGestureHandler(
    private val containerView: View,
    private val indicatorView: View,
    private val iconView: VaultPullOrbitalView,
    private val textView: TextView,
    /** Returns true while the underlying list cannot scroll up, i.e. it is at the top. */
    private val isListAtTop: () -> Boolean,
    private val primaryColor: () -> Int,
    private val textColor: () -> Int,
    /** Lets the owning activity deliver the generated ACTION_CANCEL to its children. */
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

    /** True once the sustained pull has armed the "release to open" state. */
    private var armed = false

    /** Feeds a touch event into the gesture. Returns true when the event has been consumed. */
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
                            indicatorView.beVisible()
                            indicatorView.bringToFront()
                            iconView.setColors(textColor(), primaryColor(), textColor())

                            val progress = (translation / holdThreshold).coerceIn(0f, 1f)
                            iconView.progress = progress
                            indicatorView.alpha = (progress * 1.4f).coerceIn(0.1f, 1f)

                            if (translation >= holdThreshold) {
                                if (!thresholdMet) {
                                    thresholdMet = true
                                    armAfterDelay()
                                }
                                textView.text = textView.context.getString(
                                    if (armed) R.string.release_to_open_vault
                                    else R.string.keep_holding_to_open_vault
                                )
                            } else {
                                resetThresholdState()
                                textView.text = textView.context.getString(R.string.pull_down_to_open_vault)
                            }
                            return true
                        }
                    } else if (deltaY < 0) {
                        if (containerView.translationY > 0f) {
                            containerView.translationY = 0f
                            indicatorView.beGone()
                        }
                        if (isListAtTop()) {
                            gestureStarted = false
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (gestureStarted) {
                    val shouldOpenVault = thresholdMet && armed
                    resetGesture()
                    gestureStarted = false

                    if (shouldOpenVault) {
                        onVaultOpen()
                    }
                    // A pull that was not held long enough only snaps back - it never refreshes.
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

    private fun armAfterDelay() {
        cancelArm()
        val runnable = Runnable {
            armRunnable = null
            if (thresholdMet) {
                armVaultOpen()
            }
        }
        armRunnable = runnable
        iconView.postDelayed(runnable, VAULT_ARM_DELAY_MS)
    }

    private fun armVaultOpen() {
        armed = true
        iconView.animateArm()
        textView.text = textView.context.getString(R.string.release_to_open_vault)
        indicatorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun resetThresholdState() {
        thresholdMet = false
        armed = false
        cancelArm()
        iconView.resetState()
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
    }

    companion object {
        private const val MAX_TRANSLATION_DP = 160f
        private const val HOLD_OPEN_THRESHOLD_DP = 95f
        private const val TRANSLATION_FACTOR = 0.45f

        /** How long the threshold must be held before the orbital lock arms. */
        private const val VAULT_ARM_DELAY_MS = 625L

        private const val RESET_ANIM_MS = 220L
    }
}
