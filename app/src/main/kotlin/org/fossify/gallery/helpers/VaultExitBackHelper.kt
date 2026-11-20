package org.fossify.gallery.helpers

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import org.fossify.gallery.R

/**
 * Coordinates double-back vault exit so the prompt toast is cancelled immediately on the
 * second back press and accidental extra back presses during/right after the transition
 * do not exit the app.
 */
class VaultExitBackHelper(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val showToast: (Context) -> Toast? = { context ->
        VaultExitToast.show(context, context.getString(R.string.press_back_again_to_exit_vault))
    }
) {
    private var lastBackPressedAt = 0L
    private var exitedAt = 0L
    private var isExiting = false
    private var promptToast: Toast? = null

    fun handleBackPress(context: Context, onExit: () -> Unit) {
        if (isExiting) return

        val now = clock()
        if (lastBackPressedAt == 0L || now - lastBackPressedAt > VAULT_EXIT_DOUBLE_BACK_WINDOW_MS) {
            lastBackPressedAt = now
            cancelToast()
            promptToast = showToast(context)
            return
        }

        lastBackPressedAt = 0L
        isExiting = true
        exitedAt = now
        cancelToast()
        onExit()
    }

    fun markExited() {
        lastBackPressedAt = 0L
        isExiting = false
        exitedAt = clock()
        cancelToast()
    }

    fun isWithinExitGuard(): Boolean {
        if (isExiting) return true
        if (exitedAt == 0L) return false
        return clock() - exitedAt < VAULT_EXIT_BACK_GUARD_MS
    }

    fun reset() {
        lastBackPressedAt = 0L
        isExiting = false
        exitedAt = 0L
        cancelToast()
    }

    fun cancelToast() {
        promptToast?.cancel()
        promptToast = null
    }
}
