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
            "src/main/kotlin/org/fossify/gallery/extensions/Activity.kt"
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
    fun switchingVaultsRequiresDeviceOwnerButFirstSelectionDoesNot() {
        val activity = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/extensions/Activity.kt"
        ).readText()
        val gate = activity.substringAfter("private fun BaseSimpleActivity.requireDeviceOwnerForVaultSwitch(")
            .substringBefore("private fun BaseSimpleActivity.selectCandidateVault(")
        assertTrue(
            "An empty vault slot must not require verification",
            gate.contains("!CryptoManager.hasExistingVault(this)")
        )
        assertTrue(
            "Selecting the already active vault must not require verification",
            gate.contains("sameVault")
        )
        assertTrue(
            "A real switch must ask the device owner",
            gate.contains("verifyDeviceOwner(onConfirmed)")
        )
        assertTrue(
            "Existing-vault selection must be gated",
            activity.contains("requireDeviceOwnerForVaultSwitch(existingVault)")
        )
        assertTrue(
            "Archived-vault selection must be gated",
            activity.contains("requireDeviceOwnerForVaultSwitch(File(entry.path))")
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
