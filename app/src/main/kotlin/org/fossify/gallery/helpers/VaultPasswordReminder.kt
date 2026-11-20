package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.R

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val ONE_WEEK_DAYS = 7
private const val TWO_WEEKS_DAYS = 14
private const val THREE_WEEKS_DAYS = 21
private const val ONE_MONTH_DAYS = 30
private const val TWO_MONTHS_DAYS = 60
private const val THREE_MONTHS_DAYS = 90

/** Pure state used by the unit tests; no Android or Keystore access. */
data class PasswordReminderState(
    val intervalDays: Int,
    val lastPasswordEntryAt: Long,
    val lastReminderAt: Long,
    val vaultExists: Boolean,
)

/**
 * Reminder is due when the user has not opened the vault with the password/PIN for the whole
 * configured interval. Biometrics are irrelevant here: a user who never adds biometrics can
 * forget the password just as easily.
 */
fun shouldShowPasswordReminder(state: PasswordReminderState, now: Long): Boolean {
    if (!state.vaultExists) return false
    if (state.intervalDays <= 0) return false
    if (state.lastPasswordEntryAt <= 0L) return false

    val intervalMs = state.intervalDays * DAY_MS
    if (now - state.lastPasswordEntryAt < intervalMs) return false
    // "Not now" keeps the password counter but postpones the notice by a full period.
    if (state.lastReminderAt > 0L && now - state.lastReminderAt < intervalMs) return false
    return true
}

fun markVaultPasswordEntered(config: Config, now: Long = System.currentTimeMillis()) {
    config.vaultLastPasswordEntryAt = now
}

fun markPasswordReminderShown(config: Config, now: Long = System.currentTimeMillis()) {
    config.vaultLastPasswordReminderAt = now
}

/** Called when the interval is changed, so a new period starts from now. */
fun resetPasswordReminderState(config: Config, now: Long = System.currentTimeMillis()) {
    config.vaultLastPasswordEntryAt = now
    config.vaultLastPasswordReminderAt = 0L
}

fun Config.shouldShowPasswordReminder(vaultExists: Boolean, now: Long = System.currentTimeMillis()): Boolean {
    return shouldShowPasswordReminder(
        PasswordReminderState(
            intervalDays = vaultPasswordReminderDays,
            lastPasswordEntryAt = vaultLastPasswordEntryAt,
            lastReminderAt = vaultLastPasswordReminderAt,
            vaultExists = vaultExists,
        ),
        now,
    )
}

fun formatPasswordReminderInterval(context: Context, days: Int): String {
    return when (days) {
        0 -> context.getString(R.string.vault_password_reminder_disabled)
        ONE_WEEK_DAYS, TWO_WEEKS_DAYS, THREE_WEEKS_DAYS -> String.format(
            context.getString(R.string.vault_password_reminder_weeks),
            days / ONE_WEEK_DAYS,
        )

        ONE_MONTH_DAYS, TWO_MONTHS_DAYS, THREE_MONTHS_DAYS -> String.format(
            context.getString(R.string.vault_password_reminder_months),
            days / ONE_MONTH_DAYS,
        )
        else -> context.getString(R.string.vault_password_reminder_disabled)
    }
}
