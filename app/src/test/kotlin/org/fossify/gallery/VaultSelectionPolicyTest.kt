package org.fossify.gallery

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Selecting an existing or archived vault must only register it; the password
 * belongs to the later open action, not to the selection step.
 */
class VaultSelectionPolicyTest {

    private fun locateAppFile(relPath: String): File {
        val direct = File(relPath)
        if (direct.exists()) return direct
        val fromRoot = File("app", relPath)
        assertTrue("Missing $relPath", fromRoot.exists())
        return fromRoot
    }

    @Test
    fun selectingExistingVaultSkipsCredentialPrompt() {
        val activity = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/extensions/ActivityVaultSetup.kt"
        ).readText()
        val selectBody = activity.substringAfter("private fun BaseSimpleActivity.selectCandidateVault(")
            .substringBefore("private fun BaseSimpleActivity.")
        assertTrue(
            "Selecting an existing vault must not show a password dialog",
            !selectBody.contains("VaultSecurityDialog(")
        )
        assertTrue(
            "Selection must register the vault without unlocking it",
            selectBody.contains("CryptoManager.selectVaultWithoutUnlock(")
        )
        val archivedBody = activity.substringAfter("private fun BaseSimpleActivity.showArchivedVaultPicker(")
            .substringBefore("fun BaseSimpleActivity.promptWhereToCreateVault(")
        assertTrue(
            "Selecting an archived vault must not show a password dialog",
            !archivedBody.contains("VaultSecurityDialog(")
        )
    }

    @Test
    fun deviceOwnerGateRunsOnVaultLocationEntryNotOnSelection() {
        val activity = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/extensions/ActivityVaultSetup.kt"
        ).readText()
        val entry = activity.substringAfter("fun BaseSimpleActivity.promptVaultSetupChoice(")
            .substringBefore("private fun BaseSimpleActivity.showArchivedVaultPicker(")
        assertTrue(
            "An empty vault slot must not require verification",
            entry.contains("config.vaultRootPath.isNotEmpty() && CryptoManager.hasExistingVault(this)")
        )
        assertTrue(
            "Verification must happen before the vault choice is shown",
            entry.contains("verifyDeviceOwner {")
        )
        assertTrue(
            "Back must not re-trigger verification",
            entry.contains("verifyDeviceOwnerOnEntry = false")
        )
        assertTrue(
            "Selection must stay free of a second gate",
            !activity.contains("requireDeviceOwnerForVaultSwitch")
        )
        val verification = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/extensions/DeviceOwnerVerification.kt"
        ).readText()
        assertTrue(
            "Devices without a secure lock must skip the prompt",
            verification.contains("keyguard?.isDeviceSecure != true")
        )
        assertTrue(
            "The gate must use device credential / biometric",
            verification.contains("DEVICE_CREDENTIAL") &&
                verification.contains("setAllowedAuthenticators(authenticators)")
        )
    }

    @Test
    fun archivedActivationOnlyRegistersTheVault() {
        val archiveManager = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/VaultArchiveManager.kt"
        ).readText()
        assertTrue(
            "In-place activation must not ask for a password",
            !archiveManager.contains("VaultSecurityDialog(")
        )
        assertTrue(
            "In-place activation must use the password-free selection helper",
            archiveManager.contains("CryptoManager.selectVaultWithoutUnlock(context, source)")
        )
        val root = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerVaultRoot.kt"
        ).readText()
        assertTrue(
            "A password-free vault registration helper must exist",
            root.contains("fun CryptoManager.selectVaultWithoutUnlock(")
        )
    }
}
