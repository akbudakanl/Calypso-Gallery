package org.fossify.gallery

import org.fossify.gallery.helpers.PlaybackSpeedNeedleState
import org.fossify.gallery.helpers.VideoSpeedUiHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoSpeedAndVaultLayoutTest {

    private fun locateAppFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val underApp = File("app", relativePath)
        if (underApp.exists()) return underApp
        error("Could not locate $relativePath from working directory ${File(".").absolutePath}")
    }

    @Test
    fun resolveSpeedStateMapsSlowNormalAndFastNeedles() {
        assertEquals(PlaybackSpeedNeedleState.SLOW, VideoSpeedUiHelper.resolveSpeedState(0.25f))
        assertEquals(PlaybackSpeedNeedleState.SLOW, VideoSpeedUiHelper.resolveSpeedState(0.95f))
        assertEquals(PlaybackSpeedNeedleState.NORMAL, VideoSpeedUiHelper.resolveSpeedState(1.0f))
        assertEquals(PlaybackSpeedNeedleState.FAST, VideoSpeedUiHelper.resolveSpeedState(1.05f))
        assertEquals(PlaybackSpeedNeedleState.FAST, VideoSpeedUiHelper.resolveSpeedState(2.5f))
    }

    @Test
    fun resolveSpeedDrawableMapsDistinctNeedleDrawables() {
        assertEquals(
            R.drawable.ic_playback_speed_slow_vector,
            VideoSpeedUiHelper.resolveSpeedDrawableRes(PlaybackSpeedNeedleState.SLOW),
        )
        assertEquals(
            R.drawable.ic_playback_speed_vector,
            VideoSpeedUiHelper.resolveSpeedDrawableRes(PlaybackSpeedNeedleState.NORMAL),
        )
        assertEquals(
            R.drawable.ic_playback_speed_fast_vector,
            VideoSpeedUiHelper.resolveSpeedDrawableRes(PlaybackSpeedNeedleState.FAST),
        )
    }

    @Test
    fun playerSpeedButtonIsCenteredIconOnlyImageView() {
        val layout = locateAppFile("src/main/res/layout/bottom_video_time_holder.xml").readText()
        assertTrue(
            "video_playback_speed must be an ImageView centered inside its pill frame",
            layout.contains("<ImageView") &&
                layout.contains("android:id=\"@+id/video_playback_speed\"") &&
                layout.contains("android:scaleType=\"centerInside\""),
        )
        assertFalse(
            "video_playback_speed must not render numeric text on the player button",
            layout.contains("tools:text=\"1x\""),
        )
    }

    @Test
    fun speedSheetUsesDedicatedNonOverlappingStepIcons() {
        val sheetLayout = locateAppFile("src/main/res/layout/fragment_playback_speed.xml").readText()
        assertTrue(
            sheetLayout.contains("@drawable/ic_playback_speed_sheet_slow_vector") &&
                sheetLayout.contains("@drawable/ic_playback_speed_sheet_fast_vector"),
        )
    }

    @Test
    fun videoFragmentAndViewPagerReserveBottomSpacerOnlyWhenBottomActionsAreShown() {
        val videoFragment = locateAppFile("src/main/kotlin/org/fossify/gallery/fragments/VideoFragment.kt").readText()
        val viewPager = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt").readText()
        assertTrue(
            videoFragment.contains("VideoSpeedUiHelper.updateBottomActionsSpacerHeight(") &&
                videoFragment.contains("shouldReserveBottomActionsSpace()"),
        )
        assertTrue(
            viewPager.contains("override fun shouldReserveBottomActionsSpace(path: String): Boolean"),
        )
    }
}
