package org.fossify.gallery

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ensures that deleting an encrypted vault also removes every CLI bridge that
 * carries a copy of its header — active, additional and archived ones alike.
 */
class CliBridgeCleanupTest {

    private fun locateAppFile(relPath: String): File {
        val direct = File(relPath)
        if (direct.exists()) return direct
        val fromRoot = File("app", relPath)
        assertTrue("Missing $relPath", fromRoot.exists())
        return fromRoot
    }

    @Test
    fun vaultDeletionCleansUpBridgesBeforeKeysAreCleared() {
        val source = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerVaultRoot.kt"
        ).readText()
        val start = source.indexOf("fun CryptoManager.deleteExistingVault(context: Context)")
        assertTrue("deleteExistingVault must exist", start >= 0)
        val cleanup = source.indexOf("CliBridgeManager.deleteBridgesForVaultRoots", start)
        val keysCleared = source.indexOf("clearKeys(context)", start)
        assertTrue("Vault deletion must call the CLI bridge cleanup", cleanup > start)
        assertTrue(
            "Bridge cleanup must run while the KEK fingerprint is still available",
            keysCleared > cleanup
        )
    }

    @Test
    fun archivedBridgesAreDiscoverableAndRemainInTheArchive() {
        val bridges = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CliBridgeManager.kt"
        ).readText()
        val lookupBody = bridges.substringAfter("fun bridgeFor(")
            .substringBefore("fun prepare(")
        assertTrue(
            "Bridge lookup must see archived copies too",
            lookupBody.contains("bridges(context, includeArchived = true)")
        )
        assertTrue(
            "An active archived vault must be able to mark its archived bridge active",
            bridges.contains("fun markActiveForVaultPath(")
        )
        assertTrue(
            "An archived bridge must keep its original source path",
            bridges.contains("if (bridge.dir.parentFile == archiveRoot(context))")
        )
        val activity = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/extensions/Activity.kt"
        ).readText()
        assertTrue(
            "Import must consider archived bridges of the active vault",
            activity.contains("CliBridgeManager.bridges(this, includeArchived = true).filter")
        )
        assertTrue(
            "Both CLI roots must count as app-owned staging areas",
            activity.contains("CliBridgeManager.root(this), CliBridgeManager.archiveRoot(this)")
        )
    }

    @Test
    fun bridgeCleanupCoversArchivedCopiesAndWipesTheHeader() {
        val source = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CliBridgeManager.kt"
        ).readText()
        val body = source.substringAfter("fun deleteBridgesForVaultRoots(")
            .substringBefore("private fun deleteBridgeDir")
        assertTrue(
            "Archived bridges must be included in the cleanup",
            body.contains("bridges(context, includeArchived = true)")
        )
        assertTrue(
            "An emptied CLI/Archived folder must be removed",
            body.contains("deleteIfEmpty(archiveRoot(context))")
        )
        assertTrue(
            "An emptied CLI folder must be removed",
            body.contains("deleteIfEmpty(root(context))")
        )
        val deletion = source.substringAfter("private fun deleteBridgeDir")
            .substringBefore("private fun deleteIfEmpty")
        assertTrue(
            "The copied .vault header must be securely wiped",
            deletion.contains("CryptoManager.secureWipeAndDeleteFile(file, context)")
        )
    }
}
