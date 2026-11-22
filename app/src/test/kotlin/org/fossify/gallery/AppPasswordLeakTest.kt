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
                unlock.contains("App.markAppUnlocked()")
        )
    }
}
