package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import org.fossify.gallery.views.VaultPinDotsView
import org.fossify.gallery.views.VaultShutterLockView

/**
 * "Shutter" unlock feedback, matching the approved motion prototype:
 *  1. the lock shackle lifts with a spring while the emblem glows,
 *  2. PIN dots pulse one after another (the password field pulses for alphanumeric secrets),
 *  3. a light sweep confirms the entry,
 *  4. the credential panel slides up and the caller opens the vault.
 *
 * Every step is skipped (and [onComplete] invoked immediately) when system animations are off.
 */
class VaultShutterAnimator(
    private val lock: VaultShutterLockView,
    private val dots: VaultPinDotsView?,
    private val sweep: View,
    private val panel: View,
    private val pulseView: View? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = mutableListOf<Animator>()
    private val posted = mutableListOf<Runnable>()
    private var onComplete: (() -> Unit)? = null
    private var finished = false

    fun play(onComplete: () -> Unit) {
        this.onComplete = onComplete
        if (!ValueAnimator.areAnimatorsEnabled()) {
            finish()
            return
        }

        val density = panel.resources.displayMetrics.density

        // 1 — shackle + confirmation glow (0-700 ms)
        track(ObjectAnimator.ofFloat(lock, "openProgress", 0f, 1f).apply {
            duration = 420L
            interpolator = OvershootInterpolator(1.6f)
        })
        track(ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700L
            interpolator = DecelerateInterpolator()
            addUpdateListener { lock.glow = it.animatedValue as Float }
        })

        // 2 — staggered dot pulses for PIN; a single field pulse for alphanumeric passwords
        if (dots != null) {
            repeat(dots.slotCount) { index -> post(70L * index) { dots.pulseDot(index) } }
        } else {
            pulseView?.let { field ->
                post(70L) {
                    track(ObjectAnimator.ofFloat(field, "scaleX", 1f, 1.05f, 1f).apply {
                        duration = 520L
                        interpolator = DecelerateInterpolator()
                    })
                    track(ObjectAnimator.ofFloat(field, "scaleY", 1f, 1.05f, 1f).apply {
                        duration = 520L
                        interpolator = DecelerateInterpolator()
                    })
                }
            }
        }

        // 3 — light sweep across the dots (380-900 ms)
        sweep.alpha = 0f
        sweep.visibility = View.VISIBLE
        val sweepDistance = (sweep.parent as? View)?.measuredWidth?.toFloat() ?: sweep.width * 3f
        track(ObjectAnimator.ofFloat(sweep, "translationX", 0f, sweepDistance).apply {
            duration = 520L
            startDelay = 380L
            interpolator = DecelerateInterpolator()
        })
        track(ObjectAnimator.ofFloat(sweep, "alpha", 0f, 1f, 0f).apply {
            duration = 520L
            startDelay = 380L
        })

        // 4 — panel exits upward, then the vault is opened (620-1460 ms)
        post(SETTLE_DELAY_MS) {
            track(ObjectAnimator.ofFloat(panel, "translationY", 0f, -PANEL_TRAVEL_DP * density).apply {
                duration = PANEL_DURATION_MS
                interpolator = DecelerateInterpolator()
            })
            track(ObjectAnimator.ofFloat(panel, "alpha", 1f, 0f).apply {
                duration = PANEL_DURATION_MS
            })
            post(PANEL_DURATION_MS) { finish() }
        }
    }

    fun cancel() {
        running.forEach { it.cancel() }
        running.clear()
        posted.forEach { mainHandler.removeCallbacks(it) }
        posted.clear()
    }

    private fun track(animator: Animator): Animator {
        running += animator
        animator.start()
        return animator
    }

    private fun post(delay: Long, action: () -> Unit) {
        val runnable = Runnable(action)
        posted += runnable
        mainHandler.postDelayed(runnable, delay)
    }

    private fun finish() {
        if (finished) return
        finished = true
        cancel()
        onComplete?.invoke()
        onComplete = null
    }

    companion object {
        private const val SETTLE_DELAY_MS = 620L
        private const val PANEL_DURATION_MS = 560L
        private const val PANEL_TRAVEL_DP = 70f
    }
}
