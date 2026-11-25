package org.fossify.gallery.helpers

/**
 * Pure decision policy for whether a media viewer window should hold
 * `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`.
 */
object KeepScreenOnPolicy {
    fun shouldKeepViewerScreenOn(
        isActivityResumed: Boolean,
        isForegroundOperationActive: Boolean,
        isSlideshowActive: Boolean,
        keepScreenOnSetting: Boolean,
        isCurrentItemVideo: Boolean,
        isCurrentVideoActive: Boolean,
        isPagerScrolling: Boolean = false,
        wasKeepScreenOnActive: Boolean = false,
    ): Boolean {
        if (isForegroundOperationActive) {
            return true
        }
        if (!isActivityResumed) {
            return false
        }
        if (isSlideshowActive || isCurrentVideoActive) {
            return true
        }
        if (keepScreenOnSetting && !isCurrentItemVideo) {
            return true
        }
        // Keep the flag steady across an in-flight ViewPager drag/settle when it was already on,
        // avoiding a transient drop between leaving a playing video and settling on the next page.
        if (isPagerScrolling && wasKeepScreenOnActive) {
            return true
        }
        return false
    }
}
