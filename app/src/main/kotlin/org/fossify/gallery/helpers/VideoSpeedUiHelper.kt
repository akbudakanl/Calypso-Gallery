package org.fossify.gallery.helpers

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import org.fossify.gallery.R
import org.fossify.gallery.extensions.getBottomActionsHeight
import java.text.DecimalFormat

internal enum class PlaybackSpeedNeedleState {
    SLOW,
    NORMAL,
    FAST,
}

internal object VideoSpeedUiHelper {
    private const val NORMAL_SPEED_TOLERANCE = 0.01f

    fun resolveSpeedState(speed: Float): PlaybackSpeedNeedleState {
        return when {
            speed < 1f - NORMAL_SPEED_TOLERANCE -> PlaybackSpeedNeedleState.SLOW
            speed > 1f + NORMAL_SPEED_TOLERANCE -> PlaybackSpeedNeedleState.FAST
            else -> PlaybackSpeedNeedleState.NORMAL
        }
    }

    fun resolveSpeedDrawableRes(state: PlaybackSpeedNeedleState): Int {
        return when (state) {
            PlaybackSpeedNeedleState.SLOW -> R.drawable.ic_playback_speed_slow_vector
            PlaybackSpeedNeedleState.NORMAL -> R.drawable.ic_playback_speed_vector
            PlaybackSpeedNeedleState.FAST -> R.drawable.ic_playback_speed_fast_vector
        }
    }

    /**
     * Updates the player's speed button icon needle direction without rendering numeric text
     * on the button itself; the exact value is shown only inside [org.fossify.gallery.fragments.PlaybackSpeedFragment].
     */
    fun updateSpeedButton(button: ImageView, speed: Float) {
        val state = resolveSpeedState(speed)
        if (button.tag != state) {
            button.tag = state
            button.setImageResource(resolveSpeedDrawableRes(state))
        }
        val formattedSpeed = "${DecimalFormat("#.##").format(speed)}x"
        button.contentDescription = "${button.context.getString(R.string.playback_speed)}: $formattedSpeed"
    }

    /**
     * Collapses the reserved bottom-actions spacer when bottom actions are hidden (encrypted vault,
     * hidden files, or disabled in settings) so the ExoPlayer progress bar sits cleanly at the bottom.
     */
    fun updateBottomActionsSpacerHeight(
        spacer: View,
        systemBottomInset: Int,
        reserveBottomActions: Boolean,
    ) {
        val targetHeight = if (reserveBottomActions) {
            spacer.resources.getBottomActionsHeight() + systemBottomInset
        } else {
            systemBottomInset + spacer.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.tiny_margin)
        }
        if (spacer.layoutParams?.height != targetHeight) {
            spacer.updateLayoutParams<ViewGroup.LayoutParams> {
                height = targetHeight
            }
        }
    }
}
