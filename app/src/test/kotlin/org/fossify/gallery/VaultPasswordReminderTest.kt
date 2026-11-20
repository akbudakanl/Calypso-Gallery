package org.fossify.gallery

import org.fossify.gallery.helpers.PasswordReminderState
import org.fossify.gallery.helpers.shouldShowPasswordReminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPasswordReminderTest {

    private val now = 90L * DAY

    private fun state(
        intervalDays: Int = 14,
        lastPasswordEntryAt: Long = 0L,
        lastReminderAt: Long = 0L,
        vaultExists: Boolean = true,
    ) = PasswordReminderState(
        intervalDays = intervalDays,
        lastPasswordEntryAt = lastPasswordEntryAt,
        lastReminderAt = lastReminderAt,
        vaultExists = vaultExists,
    )

    @Test
    fun neverRemindsWhenDisabledOrVaultMissing() {
        assertFalse(shouldShowPasswordReminder(state(intervalDays = 0), now))
        assertFalse(shouldShowPasswordReminder(state(vaultExists = false), now))
    }

    @Test
    fun neverRemindsWithoutPasswordBaseline() {
        assertFalse(shouldShowPasswordReminder(state(), now))
    }

    @Test
    fun remindsOnlyAfterTheFullPasswordFreePeriod() {
        assertFalse(shouldShowPasswordReminder(state(lastPasswordEntryAt = now - 7L * DAY), now))
        assertTrue(shouldShowPasswordReminder(state(lastPasswordEntryAt = now - 30L * DAY), now))
    }

    @Test
    fun snoozePostponesByOnePeriodWithoutResettingThePasswordCounter() {
        assertFalse(
            "A notice shown in this period must not repeat",
            shouldShowPasswordReminder(
                state(lastPasswordEntryAt = now - 30L * DAY, lastReminderAt = now - 7L * DAY),
                now,
            ),
        )
        assertTrue(
            "Not now keeps the password counter and allows a notice in the next period",
            shouldShowPasswordReminder(
                state(lastPasswordEntryAt = now - 30L * DAY, lastReminderAt = now - 21L * DAY),
                now,
            ),
        )
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
