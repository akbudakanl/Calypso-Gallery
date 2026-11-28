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
            "src/main/kotlin/org/fossify/gallery/extensions/ActivityVaultSyncExport.kt"
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
    fun unarchiveRestoresTheBridgeFromBothVaultPaths() {
        val archiveManager = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/VaultArchiveManager.kt"
        ).readText()
        assertTrue(
            "Unarchive must restore the bridge after moving the vault",
            archiveManager.contains("CliBridgeManager.restoreFromArchive(context, target, entry.bridgeId.orEmpty())")
        )
        val activation = archiveManager.substringAfter("fun activateArchivedVault(")
        assertTrue(
            "Activating in place must never restore the bridge out of the archive",
            !activation.contains("CliBridgeManager.restoreFromArchive(")
        )
        val bridges = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CliBridgeManager.kt"
        ).readText()
        val identityBody = bridges.substringAfter("private fun findBridgeForVault(")
            .substringBefore("fun markActiveForVaultPath(")
        assertTrue(
            "Bridge identity must use the copied vault header",
            identityBody.contains("VAULT_HEADER_FILE") &&
                identityBody.contains("contentEquals(header)")
        )
        assertTrue(
            "A shared path must never identify a bridge on its own",
            identityBody.contains("pathMatches.singleOrNull()")
        )
        val restoreBody = bridges.substringAfter("fun restoreFromArchive(")
            .substringBefore("private fun moveFor(")
        assertTrue(
            "All bridges of the vault must be moved back",
            restoreBody.contains("matches.forEach")
        )
        assertTrue(
            "The recorded bridge id must take precedence over paths",
            restoreBody.contains("it.vaultId.equals(bridgeId, ignoreCase = true)")
        )
        assertTrue(
            "Archived entries must remember their bridge id",
            archiveManager.contains("entry.copy(bridgeId = archivedBridge.vaultId)") &&
                archiveManager.contains("bridgeId = CliBridgeManager.archivedBridgeIdFor(context, dir)")
        )
        assertTrue(
            "Disk-discovered entries must still be activatable",
            archiveManager.contains("archivedEntries(context).firstOrNull")
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

    @Test
    fun kdfRewrapSyncsBridgeHeaderEvenWhenLocked() {
        val credentials = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerCredentials.kt"
        ).readText()
        val rewrapBody = credentials.substringAfter("fun CryptoManager.rewrapVaultHeaderWithCurrentKdf(")
        assertTrue(
            "KDF re-wrap must sync CLI bridge header after persisting .vault",
            rewrapBody.contains("CliBridgeManager.syncRewrappedHeader(context, vaultRoot, headerBytes)")
        )

        val bridges = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CliBridgeManager.kt"
        ).readText()
        val syncBody = bridges.substringAfter("fun syncRewrappedHeader(")
            .substringBefore("private fun refreshForFingerprint(")
        assertTrue(
            "syncRewrappedHeader must fall back to matching previousHeaderBytes when locked",
            syncBody.contains("findBridgeForVault(context, vaultRoot, candidates, previousHeaderBytes)")
        )
    }

    @Test
    fun duplicateBridgesWithSameFingerprintAreConsolidated() {
        val bridges = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CliBridgeManager.kt"
        ).readText()
        val consolidateBody = bridges.substringAfter("private fun consolidateDuplicates(")
            .substringBefore("private fun selectPrimaryBridge(")
        assertTrue(
            "Empty duplicate bridges must be deleted",
            consolidateBody.contains("!hasStagedPayload(dup.dir)") &&
                consolidateBody.contains("deleteBridgeDir(context, dup.dir)")
        )
        assertTrue(
            "Non-empty duplicate bridges must have their .vault header refreshed",
            consolidateBody.contains("header.copyTo(File(dup.dir, VAULT_HEADER_FILE), overwrite = true)")
        )
    }

    @Test
    fun windowsBridgeSyncCleansTempWorkspaceBeforePull() {
        val repoRoot = if (File("tools/calypso-cli/run.bat").exists()) File(".") else File("..")
        val runBat = File(repoRoot, "tools/calypso-cli/run.bat").readText()
        val syncPs1 = File(repoRoot, "tools/calypso-cli/sync_phone.ps1").readText()

        val pullIdx = runBat.indexOf("-Action pull")
        val cleanIdx = runBat.indexOf("rmdir /s /q \"%BRIDGE_TEMP%\"")
        assertTrue("run.bat must clean BRIDGE_TEMP before pull", cleanIdx in 0 until pullIdx)

        val psPullBody = syncPs1.substringAfter("if (\$Action -eq 'pull')")
            .substringBefore("} else {")
        assertTrue(
            "sync_phone.ps1 pull must remove stale LocalRoot before copying from phone",
            psPullBody.contains("Remove-Item -LiteralPath \$LocalRoot -Recurse -Force")
        )
    }
}
