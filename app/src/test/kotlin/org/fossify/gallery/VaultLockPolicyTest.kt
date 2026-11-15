package org.fossify.gallery

import org.fossify.gallery.helpers.VaultLockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultLockPolicyTest {

    @Test
    fun armsReauthOnlyWhenVaultIsUnlockedExistsAndInVaultSession() {
        assertTrue(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = true, hasExistingVault = true, isInVaultSession = true))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = true, hasExistingVault = true, isInVaultSession = false))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = false, hasExistingVault = true, isInVaultSession = true))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = true, hasExistingVault = false, isInVaultSession = true))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = false, hasExistingVault = false, isInVaultSession = false))
    }

    @Test
    fun showsLockScreenOnlyForNonLockNonSplashWhenPendingAndNotAlreadyOpen() {
        assertTrue(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = false,
                isSplashActivity = false,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = false,
                isLockScreenActivity = false,
                isSplashActivity = false,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = true,
                isSplashActivity = false,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = false,
                isSplashActivity = true,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = false,
                isSplashActivity = false,
                isLockScreenAlreadyActive = true
            )
        )
    }
}
