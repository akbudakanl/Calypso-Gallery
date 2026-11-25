package org.fossify.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppPasswordLeakTest {

    private fun locate(relPath: String): File {
        val direct = File(relPath)
        if (direct.exists()) return direct
        val fromRoot = File("app", relPath)
        assertTrue("Missing $relPath", fromRoot.exists())
        return fromRoot
    }

    @Test
    fun appPasswordGateIsRaisedBeforeOnResumeWithoutChangingTheGracePeriod() {
        val app = locate("src/main/kotlin/org/fossify/gallery/App.kt").readText()
        assertTrue(
            app.contains("maybeLaunchAppUnlockScreen(activity)") &&
                app.contains("SystemClock.elapsedRealtime() - appBackgroundElapsedMs >= config.unlockTimeoutDurationMs")
        )
        assertFalse(
            "The Commons 30s tolerance must stay untouched",
            app.contains("unlockTimeoutDurationMs =") || app.contains("DEFAULT_UNLOCK_TIMEOUT_DURATION =")
        )
        val curtainBranch = app.substringAfter("if (VaultSessionManager.isReauthPending()")
        assertTrue(
            "The privacy curtain must also cover the app-password gate",
            curtainBranch.substringBefore(") {").contains("|| isAppPasswordLockExpired()")
        )
    }

    @Test
    fun appPasswordSuppressesRecentsPreviewAndDuplicatePrompt() {
        val activity = locate("src/main/kotlin/org/fossify/gallery/extensions/Activity.kt").readText()
        val protection = activity.substringAfter("fun Activity.applyVaultScreenProtection")
        assertTrue(
            protection.contains("setRecentsScreenshotEnabled(!shouldProtect)") &&
                protection.contains("config.isAppPasswordProtectionOn")
        )

        val simple = locate("src/main/kotlin/org/fossify/gallery/activities/SimpleActivity.kt").readText()
        assertTrue(simple.contains("App.shouldSuppressAppUnlockLaunch()"))

        val unlock = locate("src/main/kotlin/org/fossify/gallery/activities/AppUnlockActivity.kt").readText()
        assertTrue(
            unlock.contains("overrideActivityTransition(0, 0, false)") &&
                unlock.contains("WindowManager.LayoutParams.FLAG_SECURE") &&
                unlock.contains("App.markAppUnlocked()") &&
                unlock.contains("App.markAppLockGateClosed()")
        )
    }

    @Test
    fun everyEntryModeLocksImmediatelyOnBackgroundAndScreenOff() {
        val app = locate("src/main/kotlin/org/fossify/gallery/App.kt").readText()
        val screenOffBranch = app.substringAfter("Intent.ACTION_SCREEN_OFF ->")
            .substringBefore("syncPrivacyCurtainForTrackedActivities()")
        assertTrue(
            "Screen-off must lock appLockManager immediately when APP_LOCK_MODE_EVERY_ENTRY is active",
            screenOffBranch.contains("Config.APP_LOCK_MODE_EVERY_ENTRY") &&
                screenOffBranch.contains("appLockManager.lock()")
        )

        val stoppedBranch = app.substringAfter("override fun onActivityStopped")
            .substringBefore("VaultSessionManager.onAppBackgrounded")
        assertTrue(
            "Backgrounding must lock appLockManager immediately when APP_LOCK_MODE_EVERY_ENTRY is active",
            stoppedBranch.contains("Config.APP_LOCK_MODE_EVERY_ENTRY") &&
                stoppedBranch.contains("appLockManager.lock()")
        )
    }
}
