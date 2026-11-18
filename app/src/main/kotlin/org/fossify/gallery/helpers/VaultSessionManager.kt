package org.fossify.gallery.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry

/**
 * Decides WHEN the unlocked vault keys (master key, KEK, decrypted metadata, image caches) are wiped
 * from RAM vs when the UI viewing session expires.
 *
 * Security & UX rule:
 *  - Whenever the user leaves the app or turns off the screen while NO registered foreground file
 *    operation is active, vault keys and image caches are wiped from RAM immediately.
 *  - If the user was actively inside the vault (e.g. watching a video or browsing an encrypted
 *    album), the underlying activity and session state are kept resumable behind the isolated
 *    [org.fossify.gallery.activities.VaultLockActivity] gate for [VAULT_AUTO_LOCK_TIMEOUT_MS]
 *    (30 minutes), so re-authenticating returns directly to the exact media item.
 *  - If the app stays in the background past [VAULT_AUTO_LOCK_TIMEOUT_MS] (or expires once a
 *    background operation ends), the resumable UI session expires as well.
 */
object VaultSessionManager {
    private val handler: Handler? by lazy {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    }

    private fun elapsedNowMs(): Long =
        runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }

    @Volatile
    private var lastActiveAt = elapsedNowMs()

    @Volatile
    private var isInBackground = false

    /**
     * Set when the person dismissed the "unlock to continue" prompt, so that the next screen does not ask again
     * right away. Consumed once via [consumeUnlockDeclined].
     */
    @Volatile
    private var unlockDeclinedAt = 0L

    private const val DECLINE_WINDOW_MS = 3000L

    /**
     * True after the timeout wiped an unlocked vault, until the person unlocked it again or declined. Only an
     * automatic wipe leads to the "enter the password to continue where you left off" prompt; locking on purpose
     * (settings, leaving the vault) must not.
     */
    @Volatile
    private var autoWiped = false

    @Volatile
    private var preserveVaultOnSwitch = false

    @Volatile
    private var requiresVaultReauth = false

    @Volatile
    private var lockScreenOpen = false

    @Volatile
    private var lockScreenLaunching = false

    @Volatile
    private var restoreTemporarilyShowHidden = false

    fun markPreserveVaultOnSwitch() {
        preserveVaultOnSwitch = true
    }

    fun consumePreserveVaultOnSwitch(): Boolean {
        val preserve = preserveVaultOnSwitch
        preserveVaultOnSwitch = false
        return preserve
    }

    private fun timeoutMs() = VAULT_AUTO_LOCK_TIMEOUT_MS

    private fun isExpired(): Boolean {
        val timeout = timeoutMs()
        return timeout > 0 && elapsedNowMs() - lastActiveAt >= timeout
    }

    private fun armReauthIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val isInVaultSession = appContext.config.temporarilyShowHidden
        if (VaultLockPolicy.shouldArmReauth(
                isVaultUnlocked = CryptoManager.isVaultUnlocked,
                hasExistingVault = CryptoManager.hasExistingVault(appContext),
                isInVaultSession = isInVaultSession
            )
        ) {
            requiresVaultReauth = true
            restoreTemporarilyShowHidden = true
        }
    }

    /** Called when the first activity of the app becomes visible. */
    fun onAppForegrounded(context: Context) {
        handler?.removeCallbacksAndMessages(null)
        if (isInBackground && isExpired() && !ForegroundOperationRegistry.isActive) {
            lockNow(context)
        }
        isInBackground = false
        lastActiveAt = elapsedNowMs()
    }

    /** Called when the last visible activity of the app stopped (not for rotation / theme changes). */
    fun onAppBackgrounded(context: Context) {
        armReauthIfNeeded(context)
        isInBackground = true
        lastActiveAt = elapsedNowMs()
        handler?.removeCallbacksAndMessages(null)

        wipeRamKeysPreservingSession(context)

        val timeout = timeoutMs()
        if (timeout > 0) {
            val appContext = context.applicationContext
            handler?.postDelayed({ lockNow(appContext) }, timeout)
        }
    }

    /** Called when the device screen turns off while the app is running. */
    fun onScreenOff(context: Context) {
        armReauthIfNeeded(context)
        wipeRamKeysPreservingSession(context)
    }

    /**
     * Called when the last registered foreground operation finishes. If the app was backgrounded
     * during the transfer, wipe RAM keys immediately now that protection is no longer needed.
     */
    fun onForegroundOperationEnded(context: Context) {
        if (isInBackground && !ForegroundOperationRegistry.isActive) {
            wipeRamKeysPreservingSession(context)
        }
    }

    /**
     * Zeroizes vault keys and decrypted image caches from RAM immediately on background/screen-off,
     * while preserving [requiresVaultReauth] and UI state for the 30-minute re-auth window.
     */
    private fun wipeRamKeysPreservingSession(context: Context) {
        if (ForegroundOperationRegistry.isActive) return
        try {
            val appContext = context.applicationContext
            CryptoManager.clearKeys(appContext)
            CryptoManager.clearAppImageCaches(appContext)
            if (!requiresVaultReauth) {
                preserveVaultOnSwitch = false
                appContext.config.temporarilyShowHidden = false
                appContext.config.temporarilyShowVaultExcluded = false
            }
        } catch (_: Throwable) {
        }
    }

    fun isReauthPending(): Boolean = requiresVaultReauth

    fun shouldShowLockScreen(isLockScreenActivity: Boolean, isSplashActivity: Boolean): Boolean {
        return VaultLockPolicy.shouldShowLockScreen(
            requiresVaultReauth = requiresVaultReauth,
            isLockScreenActivity = isLockScreenActivity,
            isSplashActivity = isSplashActivity,
            isLockScreenAlreadyActive = lockScreenOpen || lockScreenLaunching
        )
    }

    fun markLockScreenLaunching() {
        lockScreenLaunching = true
    }

    fun setLockScreenOpen(open: Boolean) {
        lockScreenOpen = open
        lockScreenLaunching = false
    }

    fun onVaultReauthenticated(context: Context) {
        requiresVaultReauth = false
        autoWiped = false
        lockScreenOpen = false
        lockScreenLaunching = false
        lastActiveAt = elapsedNowMs()
        if (restoreTemporarilyShowHidden) {
            context.applicationContext.config.temporarilyShowHidden = true
            restoreTemporarilyShowHidden = false
        }
    }

    /** Expires the full vault session (wipes RAM keys and closes resumable UI flags after 30 min). */
    fun lockNow(context: Context) {
        if (ForegroundOperationRegistry.isActive) {
            // ADR 0002 §7 & ADR 0003 §2: keep vault keys in RAM while a registered foreground
            // file operation is running; reschedule the background timeout check.
            val timeout = timeoutMs()
            if (timeout > 0) {
                val appContext = context.applicationContext
                handler?.postDelayed({ lockNow(appContext) }, timeout)
            }
            return
        }
        try {
            val appContext = context.applicationContext
            if (CryptoManager.isVaultUnlocked || requiresVaultReauth) {
                autoWiped = true
                if (appContext.config.temporarilyShowHidden) {
                    restoreTemporarilyShowHidden = true
                }
            }
            requiresVaultReauth = false
            preserveVaultOnSwitch = false
            CryptoManager.clearKeys(appContext)
            CryptoManager.clearAppImageCaches(appContext)
            appContext.config.temporarilyShowHidden = false
            appContext.config.temporarilyShowVaultExcluded = false
        } catch (_: Throwable) {
        }
    }

    fun isAutoWiped() = autoWiped

    fun clearAutoWipe() {
        autoWiped = false
    }

    fun onVaultExited() {
        handler?.removeCallbacksAndMessages(null)
        requiresVaultReauth = false
        autoWiped = false
        preserveVaultOnSwitch = false
        lockScreenOpen = false
        lockScreenLaunching = false
        restoreTemporarilyShowHidden = false
    }

    /**
     * Wipes the vault key from RAM and clears session state when the user is not in an active vault
     * viewing session (e.g. after encrypting files or albums from the normal gallery).
     */
    fun lockVaultIfOutsideSession(context: Context) {
        val appContext = context.applicationContext
        if (!appContext.config.temporarilyShowHidden) {
            CryptoManager.clearKeys(appContext)
            onVaultExited()
        }
    }

    fun markUnlockDeclined() {
        unlockDeclinedAt = elapsedNowMs()
    }

    /** True only if the prompt was dismissed a moment ago (the screen above finished right after it). */
    fun consumeUnlockDeclined(): Boolean {
        val declinedAt = unlockDeclinedAt
        unlockDeclinedAt = 0L
        return declinedAt != 0L && elapsedNowMs() - declinedAt < DECLINE_WINDOW_MS
    }
}
