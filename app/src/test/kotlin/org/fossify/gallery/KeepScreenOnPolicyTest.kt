package org.fossify.gallery

import org.fossify.gallery.helpers.KeepScreenOnPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeepScreenOnPolicyTest {

    private fun locateAppFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.first { it.exists() }
    }

    @Test
    fun fullscreenPhotoFollowsKeepScreenOnSettingWhileResumed() {
        assertTrue(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = true,
                isCurrentItemVideo = false,
                isCurrentVideoActive = false,
            )
        )
        assertFalse(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = false,
                isCurrentItemVideo = false,
                isCurrentVideoActive = false,
            )
        )
        assertFalse(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = false,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = true,
                isCurrentItemVideo = false,
                isCurrentVideoActive = false,
            )
        )
    }

    @Test
    fun playingVideoKeepsScreenOnRegardlessOfPhotoSetting() {
        assertTrue(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = false,
                isCurrentItemVideo = true,
                isCurrentVideoActive = true,
            )
        )
        assertTrue(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = true,
                isCurrentItemVideo = true,
                isCurrentVideoActive = true,
            )
        )
        assertFalse(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = true,
                isCurrentItemVideo = true,
                isCurrentVideoActive = false,
            )
        )
    }

    @Test
    fun pagerScrollPreservesActiveKeepScreenOnUntilSettled() {
        assertTrue(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = false,
                isCurrentItemVideo = true,
                isCurrentVideoActive = false,
                isPagerScrolling = true,
                wasKeepScreenOnActive = true,
            )
        )
        assertFalse(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = false,
                keepScreenOnSetting = false,
                isCurrentItemVideo = true,
                isCurrentVideoActive = false,
                isPagerScrolling = false,
                wasKeepScreenOnActive = true,
            )
        )
    }

    @Test
    fun slideshowAndForegroundOperationsAlwaysKeepScreenOn() {
        assertTrue(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = true,
                isForegroundOperationActive = false,
                isSlideshowActive = true,
                keepScreenOnSetting = false,
                isCurrentItemVideo = true,
                isCurrentVideoActive = false,
            )
        )
        assertTrue(
            KeepScreenOnPolicy.shouldKeepViewerScreenOn(
                isActivityResumed = false,
                isForegroundOperationActive = true,
                isSlideshowActive = false,
                keepScreenOnSetting = false,
                isCurrentItemVideo = true,
                isCurrentVideoActive = false,
            )
        )
    }

    @Test
    fun photoFragmentNeverDirectlyMutatesWindowKeepScreenOnFlag() {
        val photoFragmentText = locateAppFile("src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt").readText()
        assertFalse(
            "PhotoFragment must not add/clear FLAG_KEEP_SCREEN_ON directly on the shared activity window",
            photoFragmentText.contains("FLAG_KEEP_SCREEN_ON")
        )
    }

    @Test
    fun viewerActivitiesAndAppDelegateKeepScreenOnToBaseViewerActivity() {
        val viewPagerText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt").readText()
        assertFalse(
            "ViewPagerActivity must not track a separate mVideoKeepScreenOn flag that desyncs from window flags",
            viewPagerText.contains("mVideoKeepScreenOn")
        )
        assertFalse(
            "ViewPagerActivity must not call window.addFlags/clearFlags(FLAG_KEEP_SCREEN_ON) directly",
            viewPagerText.contains("addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)") ||
                viewPagerText.contains("clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)")
        )

        val videoPlayerText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/VideoPlayerActivity.kt").readText()
        assertFalse(
            "VideoPlayerActivity must delegate FLAG_KEEP_SCREEN_ON to BaseViewerActivity.syncKeepScreenOn()",
            videoPlayerText.contains("FLAG_KEEP_SCREEN_ON")
        )

        val appText = locateAppFile("src/main/kotlin/org/fossify/gallery/App.kt").readText()
        assertTrue(
            "App.syncKeepScreenOn must delegate BaseViewerActivity instances to activity.syncKeepScreenOn()",
            appText.contains("if (activity is BaseViewerActivity)") &&
                appText.contains("activity.syncKeepScreenOn()")
        )
    }
}
