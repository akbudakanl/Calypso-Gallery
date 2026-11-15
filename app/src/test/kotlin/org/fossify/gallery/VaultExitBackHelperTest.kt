package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import org.fossify.gallery.helpers.VAULT_EXIT_BACK_GUARD_MS
import org.fossify.gallery.helpers.VAULT_EXIT_DOUBLE_BACK_WINDOW_MS
import org.fossify.gallery.helpers.VaultExitBackHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultExitBackHelperTest {

    private fun dummyContext(): Context = ContextWrapper(null)

    @Test
    fun firstBackShowsPromptAndSecondWithinWindowExitsOnce() {
        var now = 10_000L
        var toastCount = 0
        var exitCount = 0
        val helper = VaultExitBackHelper(
            clock = { now },
            showToast = {
                toastCount++
                null
            }
        )
        val context = dummyContext()

        helper.handleBackPress(context) { exitCount++ }
        assertEquals(1, toastCount)
        assertEquals(0, exitCount)
        assertFalse(helper.isWithinExitGuard())

        now += 500L
        helper.handleBackPress(context) { exitCount++ }
        assertEquals(1, toastCount)
        assertEquals(1, exitCount)
        assertTrue(helper.isWithinExitGuard())

        // Duplicate back press during exit transition does not re-trigger exit
        now += 100L
        helper.handleBackPress(context) { exitCount++ }
        assertEquals(1, exitCount)
    }

    @Test
    fun expiredDoubleBackWindowRequiresPromptAgain() {
        var now = 10_000L
        var toastCount = 0
        var exitCount = 0
        val helper = VaultExitBackHelper(
            clock = { now },
            showToast = {
                toastCount++
                null
            }
        )
        val context = dummyContext()

        helper.handleBackPress(context) { exitCount++ }
        assertEquals(1, toastCount)
        assertEquals(0, exitCount)

        now += VAULT_EXIT_DOUBLE_BACK_WINDOW_MS + 1L
        helper.handleBackPress(context) { exitCount++ }
        assertEquals(2, toastCount)
        assertEquals(0, exitCount)

        now += 300L
        helper.handleBackPress(context) { exitCount++ }
        assertEquals(2, toastCount)
        assertEquals(1, exitCount)
    }

    @Test
    fun exitBackGuardAbsorbsAccidentalThirdPressAndExpiresAfterWindow() {
        var now = 20_000L
        val helper = VaultExitBackHelper(
            clock = { now },
            showToast = { null }
        )

        assertFalse(helper.isWithinExitGuard())
        helper.markExited()
        assertTrue(helper.isWithinExitGuard())

        now += VAULT_EXIT_BACK_GUARD_MS - 1L
        assertTrue(helper.isWithinExitGuard())

        now += 1L
        assertFalse(helper.isWithinExitGuard())
    }
}
