package org.fossify.gallery.helpers

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.gallery.views.VaultPullIndicatorView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pull-to-open gesture for the encrypted vault (AD-1 sequential light handoff).
 *
 * 1. While pulling past [PRE_PULL_START_DP], a thin horizontal light slit softly opens at the top
 *    edge under the search bar in the active theme's `primary` hue.
 * 2. Crossing [HOLD_OPEN_THRESHOLD_DP] drops the lock icon smoothly through the slit with a single
 *    spring settle; the top slit closes completely before the ringless radial aura blooms around
 *    the settled lock so only one light focus is active at a time.
 * 3. Throughout the motion the lock keeps a constant theme hue, shifting smoothly from a calmer
 *    muted shade while descending to the vivid armed tone when ready.
 * 4. Short/early releases only snap back; releasing once armed opens the vault.
 */
class VaultPullGestureHandler(
    private val containerView: View,
    private val indicatorView: VaultPullIndicatorView,
    private val isListAtTop: () -> Boolean,
    private val paletteProvider: () -> AppearancePalette,
    private val dispatchToChildren: (MotionEvent) -> Unit,
    private val onVaultOpen: () -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(containerView.context).scaledTouchSlop
    private val density = containerView.context.resources.displayMetrics.density
    private val prePullStart = PRE_PULL_START_DP * density
    private val holdThreshold = HOLD_OPEN_THRESHOLD_DP * density
    private val retractThreshold = RETRACT_HIDE_THRESHOLD_DP * density

    private var startRawX = 0f
    private var startRawY = 0f
    private var gestureStarted = false
    private var cancelDispatched = false
    private var thresholdMet = false
    private var armed = false
    private var armRunnable: Runnable? = null
    private val choreographyAnimators = mutableListOf<Animator>()
    private var breatheAnimator: ValueAnimator? = null

    private val easeOutInterpolator = DecelerateInterpolator(1.5f)

    private val dropSettleInterpolator = TimeInterpolator { t ->
        if (t < DROP_SETTLE_SPLIT) {
            val u = t / DROP_SETTLE_SPLIT
            val quad = if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f) * (-2f * u + 2f) / 2f
            quad * (1f + DROP_SETTLE_OVERSHOOT)
        } else {
            val u = (t - DROP_SETTLE_SPLIT) / (1f - DROP_SETTLE_SPLIT)
            val inv = 1f - u
            (1f + DROP_SETTLE_OVERSHOOT) - DROP_SETTLE_OVERSHOOT * (1f - inv * inv * inv)
        }
    }

    fun handleTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStarted = isListAtTop()
                if (gestureStarted) {
                    startRawX = ev.rawX
                    startRawY = ev.rawY
                    refreshPalette()
                }
                resetThresholdState()
                cancelDispatched = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (gestureStarted) {
                    val deltaX = abs(ev.rawX - startRawX)
                    val deltaY = ev.rawY - startRawY
                    if (deltaY > 0 && (cancelDispatched || deltaY > deltaX)) {
                        val translation = computeRubberBandTranslationPx(deltaY, density)
                        if (translation > touchSlop || cancelDispatched) {
                            if (!cancelDispatched) {
                                cancelDispatched = true
                                val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                                dispatchToChildren(cancelEvent)
                                cancelEvent.recycle()
                            }
                            containerView.translationY = translation
                            indicatorView.pullTranslationPx = translation

                            when {
                                translation >= holdThreshold -> {
                                    if (!thresholdMet) {
                                        thresholdMet = true
                                        triggerThresholdEnter(translation)
                                    }
                                }

                                thresholdMet && translation >= retractThreshold -> {
                                    // Stay active within the 76..95dp hysteresis band so minor
                                    // finger jitter near the threshold never cancels an in-progress arm.
                                }

                                translation >= prePullStart -> {
                                    if (thresholdMet || armed) {
                                        thresholdMet = false
                                        armed = false
                                        cancelChoreography()
                                        indicatorView.thresholdActive = false
                                        indicatorView.dropProgress = 0f
                                        indicatorView.toneProgress = 0f
                                        indicatorView.auraProgress = 0f
                                        indicatorView.breathePhase = 0f
                                        indicatorView.armPopScale = 1f
                                    }
                                    val preFraction = ((translation - prePullStart) / (holdThreshold - prePullStart))
                                        .coerceIn(0f, 1f)
                                    showPrePullSlit(preFraction)
                                }

                                else -> {
                                    thresholdMet = false
                                    armed = false
                                    hideIndicator()
                                }
                            }
                            return true
                        }
                    } else if (deltaY <= 0 && containerView.translationY > 0f) {
                        containerView.translationY = 0f
                        thresholdMet = false
                        armed = false
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

    private fun refreshPalette() {
        indicatorView.applyTonePalette(VaultPullTonePalette.resolve(paletteProvider()))
    }

    private fun showPrePullSlit(preFraction: Float) {
        indicatorView.animate().cancel()
        indicatorView.alpha = 1f
        if (indicatorView.visibility != View.VISIBLE) {
            indicatorView.beVisible()
            indicatorView.bringToFront()
        }
        indicatorView.thresholdActive = false
        indicatorView.slitEnergy = preFraction * PRE_SLIT_MAX_ENERGY
        indicatorView.slitToneBoost = preFraction * PRE_SLIT_TONE_BOOST
    }

    private fun triggerThresholdEnter(translation: Float) {
        cancelChoreography()
        refreshPalette()
        indicatorView.animate().cancel()
        indicatorView.alpha = 1f
        indicatorView.pullTranslationPx = translation
        indicatorView.thresholdActive = true
        indicatorView.armPopScale = 1f
        indicatorView.breathePhase = 0f
        if (indicatorView.visibility != View.VISIBLE) {
            indicatorView.beVisible()
            indicatorView.bringToFront()
        }

        if (!ValueAnimator.areAnimatorsEnabled()) {
            indicatorView.dropProgress = 1f
            indicatorView.slitEnergy = 0f
            indicatorView.toneProgress = 1f
            indicatorView.auraProgress = 1f
            armed = true
            indicatorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }

        indicatorView.dropProgress = 0f
        indicatorView.toneProgress = 0f
        indicatorView.auraProgress = 0f
        indicatorView.slitEnergy = SLIT_PEAK_ENERGY
        indicatorView.slitToneBoost = 1f

        // 1. Lock drops through the top light slit and settles calmly into the gap center (0..690ms)
        choreographyAnimators += ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DROP_DURATION_MS
            interpolator = dropSettleInterpolator
            addUpdateListener { indicatorView.dropProgress = it.animatedValue as Float }
        }

        // 2. Top slit closes completely (140..460ms) BEFORE the center aura begins (480ms)
        choreographyAnimators += ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = SLIT_CLOSE_DELAY_MS
            duration = SLIT_CLOSE_DURATION_MS
            interpolator = easeOutInterpolator
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                indicatorView.slitEnergy = SLIT_PEAK_ENERGY * (1f - t)
                indicatorView.slitToneBoost = 0.75f + 0.25f * (1f - t)
            }
        }

        // 3. Constant-hue tonal rise from calmer descending shade to vivid armed shade (380..900ms)
        choreographyAnimators += ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = TONE_RISE_DELAY_MS
            duration = TONE_RISE_DURATION_MS
            interpolator = easeOutInterpolator
            addUpdateListener { indicatorView.toneProgress = it.animatedValue as Float }
        }

        // 4. Ringless soft radial aura blooms only after the top slit has closed (480..980ms)
        choreographyAnimators += ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = AURA_BLOOM_DELAY_MS
            duration = AURA_BLOOM_DURATION_MS
            interpolator = easeOutInterpolator
            addUpdateListener { indicatorView.auraProgress = it.animatedValue as Float }
        }

        choreographyAnimators.forEach { it.start() }
        armAfterDelay()
    }

    private fun armAfterDelay() {
        val runnable = Runnable {
            armRunnable = null
            if (thresholdMet) {
                armed = true
                indicatorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                playArmedFeedback()
            }
        }
        armRunnable = runnable
        indicatorView.postDelayed(runnable, ARM_DELAY_MS)
    }

    private fun playArmedFeedback() {
        if (!ValueAnimator.areAnimatorsEnabled()) return

        val popAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ARM_POP_DURATION_MS
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                indicatorView.armPopScale = 1f + ARM_POP_AMPLITUDE * sin(t * PI).toFloat()
            }
        }
        choreographyAnimators += popAnimator
        popAnimator.start()

        breatheAnimator?.cancel()
        breatheAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BREATHE_HALF_CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = easeOutInterpolator
            addUpdateListener { indicatorView.breathePhase = it.animatedValue as Float }
            start()
        }
    }

    private fun hideIndicator() {
        cancelChoreography()
        indicatorView.animate().cancel()
        indicatorView.resetVisualState()
        indicatorView.beGone()
    }

    private fun resetThresholdState() {
        thresholdMet = false
        armed = false
        cancelChoreography()
        indicatorView.resetVisualState()
    }

    private fun resetGesture() {
        containerView.animate()
            .translationY(0f)
            .setDuration(RESET_ANIM_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()

        cancelChoreography()
        if (indicatorView.visibility == View.VISIBLE) {
            indicatorView.animate()
                .alpha(0f)
                .setDuration(RESET_ANIM_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    indicatorView.resetVisualState()
                    indicatorView.beGone()
                }
                .start()
        } else {
            indicatorView.resetVisualState()
        }

        thresholdMet = false
        armed = false
    }

    private fun cancelChoreography() {
        armRunnable?.let { indicatorView.removeCallbacks(it) }
        armRunnable = null
        choreographyAnimators.forEach { it.cancel() }
        choreographyAnimators.clear()
        breatheAnimator?.cancel()
        breatheAnimator = null
    }

    companion object {
        const val MAX_TRANSLATION_DP = 115f
        private const val PRE_PULL_START_DP = 26f
        const val HOLD_OPEN_THRESHOLD_DP = 95f
        const val RETRACT_HIDE_THRESHOLD_DP = 76f
        const val TRANSLATION_FACTOR = 0.45f
        private const val RUBBER_BAND_RESISTANCE = 0.45f

        /**
         * Linear pull up to [HOLD_OPEN_THRESHOLD_DP] (95dp), then smooth asymptotic rubber-band
         * damping toward [MAX_TRANSLATION_DP] (115dp) so the list feels cushioned instead of
         * hitting a hard wall or sagging unnecessarily far down the screen.
         */
        fun computeRubberBandTranslationPx(deltaYPx: Float, density: Float): Float {
            if (deltaYPx <= 0f || density <= 0f) return 0f
            val rawDp = (deltaYPx / density) * TRANSLATION_FACTOR
            if (rawDp <= HOLD_OPEN_THRESHOLD_DP) {
                return rawDp * density
            }
            val extraDp = rawDp - HOLD_OPEN_THRESHOLD_DP
            val budgetDp = MAX_TRANSLATION_DP - HOLD_OPEN_THRESHOLD_DP
            val scaledExtra = extraDp * RUBBER_BAND_RESISTANCE
            val dampedExtra = budgetDp * scaledExtra / (budgetDp + scaledExtra)
            return (HOLD_OPEN_THRESHOLD_DP + dampedExtra)
                .coerceAtMost(MAX_TRANSLATION_DP) * density
        }

        private const val PRE_SLIT_MAX_ENERGY = 0.72f
        private const val PRE_SLIT_TONE_BOOST = 0.50f
        private const val SLIT_PEAK_ENERGY = 0.88f

        private const val DROP_DURATION_MS = 690L
        private const val DROP_SETTLE_SPLIT = 0.62f
        private const val DROP_SETTLE_OVERSHOOT = 0.04f
        private const val SLIT_CLOSE_DELAY_MS = 140L
        private const val SLIT_CLOSE_DURATION_MS = 320L
        private const val TONE_RISE_DELAY_MS = 380L
        private const val TONE_RISE_DURATION_MS = 520L
        private const val AURA_BLOOM_DELAY_MS = 480L
        private const val AURA_BLOOM_DURATION_MS = 500L
        private const val ARM_DELAY_MS = 880L
        private const val ARM_POP_DURATION_MS = 340L
        private const val ARM_POP_AMPLITUDE = 0.045f
        private const val BREATHE_HALF_CYCLE_MS = 2100L
        private const val RESET_ANIM_MS = 270L
    }
}
