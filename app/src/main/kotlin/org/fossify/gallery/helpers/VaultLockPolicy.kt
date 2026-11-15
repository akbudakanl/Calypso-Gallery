package org.fossify.gallery.helpers

/**
 * Determines when an active vault session must be gated behind an isolated re-authentication screen
 * upon returning from background or turning the screen back on.
 */
object VaultLockPolicy {
    fun shouldArmReauth(
        isVaultUnlocked: Boolean,
        hasExistingVault: Boolean,
        isInVaultSession: Boolean
    ): Boolean {
        return isVaultUnlocked && hasExistingVault && isInVaultSession
    }

    fun shouldShowLockScreen(
        requiresVaultReauth: Boolean,
        isLockScreenActivity: Boolean,
        isSplashActivity: Boolean,
        isLockScreenAlreadyActive: Boolean
    ): Boolean {
        return requiresVaultReauth && !isLockScreenActivity && !isSplashActivity && !isLockScreenAlreadyActive
    }
}
