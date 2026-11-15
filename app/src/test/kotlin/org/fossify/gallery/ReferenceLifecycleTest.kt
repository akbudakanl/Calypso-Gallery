package org.fossify.gallery

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.fossify.gallery.helpers.FolderIdentity
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.MediaIdentityResolver
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.VirtualAlbumBackup
import org.fossify.gallery.helpers.VirtualAlbumItemBackup
import org.fossify.gallery.models.ITEM_STATE_DECRYPTED
import org.fossify.gallery.models.ITEM_STATE_ENCRYPTED
import org.fossify.gallery.models.ITEM_STATE_OK
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for physical folder relocation across custom roots, .vault isolation during plaintext
 * resolution, folder-prefix boundary checks, and dormant ENCRYPTED/DECRYPTED reference backups.
 */
class ReferenceLifecycleTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("ref_lifecycle_test_").toFile()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    private fun writeMediaFile(file: File, size: Int, seed: Byte): File {
        file.parentFile?.mkdirs()
        val data = ByteArray(size) { ((it * 17 + seed) and 0xFF).toByte() }
        file.writeBytes(data)
        return file
    }

    @Test
    fun resolveInFilesystem_findsAlbumMovedToCustomTopLevelRoot() {
        val storageRoot = File(tempRoot, "storage")
        val oldDir = File(storageRoot, "CustomRoot/TripAlbum")
        val source = writeMediaFile(File(oldDir, "sunset.jpg"), 8192, 11)
        val anchor = writeMediaFile(File(oldDir, "anchor.jpg"), 4096, 5)
        source.setLastModified(1_700_000_000_000L)
        anchor.setLastModified(1_700_000_000_000L)
        val folderIdentity = FolderIdentity.captureForFile(source)
        val identity = MediaIdentity.of(source).withFolder(folderIdentity)

        // Move and rename the album completely across custom top-level roots
        val newDir = File(storageRoot, "Archive/CustomMoved/CompletelyNewAlbumName")
        newDir.mkdirs()
        val moved = File(newDir, "sunset.jpg")
        val movedAnchor = File(newDir, "anchor.jpg")
        assertTrue(source.renameTo(moved))
        assertTrue(anchor.renameTo(movedAnchor))
        assertTrue(oldDir.delete())

        // Resolve searching from the storage root even though neither folder is under DCIM/Pictures
        val resolution = MediaIdentityResolver.resolveInFilesystem(
            identity = identity,
            hintPaths = listOf(File(oldDir, "sunset.jpg").absolutePath),
            searchRoots = listOf(oldDir.absolutePath, storageRoot.absolutePath)
        )

        assertNotNull(resolution)
        assertEquals(moved.absolutePath, resolution!!.path)
        assertTrue(resolution.confirmed)
    }

    @Test
    fun resolveInFilesystem_skipsVaultDirectoriesDuringPlaintextSearchButIncludesThemForVaultHints() {
        val storageRoot = File(tempRoot, "storage")
        val oldPlainDir = File(storageRoot, "MyPhotos/Vacation")
        val original = writeMediaFile(File(oldPlainDir, "clip.mp4"), 4096, 3)
        original.setLastModified(1_700_001_000_000L)
        val identity = MediaIdentity.of(original)

        // Place an exact copy in a same-named Vacation folder inside a .vault subtree, and delete plain dir
        val vaultDir = File(storageRoot, "HiddenStorage/Vacation")
        vaultDir.mkdirs()
        File(vaultDir.parentFile!!, ".vault").writeText("vault_marker")
        val inVaultCopy = writeMediaFile(File(vaultDir, "clip.mp4"), 4096, 3)
        inVaultCopy.setLastModified(1_700_001_000_000L)
        assertTrue(original.delete())
        assertTrue(oldPlainDir.delete())

        // Plaintext search must ignore HiddenStorage because it has a .vault marker
        val plainResolution = MediaIdentityResolver.resolveInFilesystem(
            identity = identity,
            hintPaths = listOf(original.absolutePath),
            searchRoots = listOf(storageRoot.absolutePath)
        )
        assertNull(plainResolution)

        // Vault-hint search IS allowed to search inside .vault roots
        val vaultHintFile = File(storageRoot, "HiddenStorage/Cipher_Secrets/Vacation/abcd1234.bin")
        val vaultResolution = MediaIdentityResolver.resolveInFilesystem(
            identity = identity,
            hintPaths = listOf(vaultHintFile.absolutePath),
            searchRoots = listOf(storageRoot.absolutePath)
        )
        assertNotNull(vaultResolution)
        assertEquals(inVaultCopy.absolutePath, vaultResolution!!.path)
    }

    @Test
    fun favoritesBackup_roundTripsDormantEncryptedStateAndLegacyOkDefault() {
        val backup = ReferenceBackupManager.FavoritesBackup(
            version = 1,
            favorites = listOf(
                ReferenceBackupManager.FavoriteBackup(
                    fullPath = "/storage/emulated/0/DCIM/Active/a.jpg",
                    filename = "a.jpg",
                    parentPath = "/storage/emulated/0/DCIM/Active",
                    state = null
                ),
                ReferenceBackupManager.FavoriteBackup(
                    fullPath = "/storage/emulated/0/DCIM/SecretOrig/b.jpg",
                    filename = "b.jpg",
                    parentPath = "/storage/emulated/0/DCIM/SecretOrig",
                    fileSize = 12345L,
                    lastModified = 1_700_002_000_000L,
                    fingerprint = "deadbeef",
                    state = ITEM_STATE_ENCRYPTED
                )
            )
        )

        val json = GsonBuilder().setPrettyPrinting().create().toJson(backup)
        val parsed = Gson().fromJson(json, ReferenceBackupManager.FavoritesBackup::class.java)

        assertEquals(2, parsed.favorites?.size)
        val first = parsed.favorites!![0]
        val second = parsed.favorites!![1]
        assertEquals(ITEM_STATE_OK, first.state ?: ITEM_STATE_OK)
        assertEquals(ITEM_STATE_ENCRYPTED, second.state)
        assertEquals("deadbeef", second.fingerprint)
        assertEquals(12345L, second.fileSize)
    }

    @Test
    fun virtualAlbumBackup_roundTripsDormantEncryptedAndDecryptedStates() {
        val backup = VirtualAlbumBackup(
            version = 1,
            name = "Holiday",
            isHidden = true,
            items = listOf(
                VirtualAlbumItemBackup(
                    fullPath = "/storage/emulated/0/DCIM/Plain/pic.jpg",
                    fileSize = 2048L,
                    lastModified = 1_700_003_000_000L,
                    fingerprint = "f1e2d3c4",
                    state = ITEM_STATE_ENCRYPTED
                ),
                VirtualAlbumItemBackup(
                    fullPath = "/storage/emulated/0/Cipher/abcd/ef012345.bin",
                    fileSize = 98765L,
                    lastModified = 1_700_004_000_000L,
                    fingerprint = "01234567",
                    state = ITEM_STATE_DECRYPTED
                )
            )
        )

        val json = GsonBuilder().setPrettyPrinting().create().toJson(backup)
        val parsed = Gson().fromJson(json, VirtualAlbumBackup::class.java)

        assertEquals(2, parsed.items?.size)
        assertEquals(ITEM_STATE_ENCRYPTED, parsed.items!![0].state)
        assertEquals(ITEM_STATE_DECRYPTED, parsed.items!![1].state)
        assertEquals("01234567", parsed.items!![1].fingerprint)
        assertEquals(98765L, parsed.items!![1].fileSize)
    }

    @Test
    fun decryptedIdentityMatchesOriginalPlaintextEvenAtDifferentFolderAndFilename() {
        val src = writeMediaFile(File(tempRoot, "Orig/photo_1.jpg"), 16384, 29)
        src.setLastModified(1_700_005_000_000L)
        val storedIdentity = MediaIdentity.of(src)

        // Simulate decrypting the same content into a brand-new folder with preserved mtime
        val decrypted = File(tempRoot, "Restored/DifferentFolder/photo_1.jpg")
        decrypted.parentFile?.mkdirs()
        src.copyTo(decrypted, overwrite = true)
        decrypted.setLastModified(src.lastModified())

        val restoredIdentity = MediaIdentity.of(decrypted)
        assertEquals(storedIdentity.size, restoredIdentity.size)
        assertEquals(storedIdentity.lastModified, restoredIdentity.lastModified)
        assertEquals(storedIdentity.fingerprint, restoredIdentity.fingerprint)
        assertTrue(restoredIdentity.hasFingerprint)
    }

    @Test
    fun folderPrefixSqlSemantics_onlyMatchStrictChildrenNotSiblingFolders() {
        fun matchesPrefix(fullPath: String, folderPrefix: String): Boolean {
            return fullPath.lowercase().startsWith(folderPrefix.trimEnd('/').lowercase() + "/")
        }

        val deletedFolder = "/storage/emulated/0/Pictures/Trip"
        assertTrue(matchesPrefix("/storage/emulated/0/Pictures/Trip/a.jpg", deletedFolder))
        assertTrue(matchesPrefix("/storage/emulated/0/Pictures/Trip/Sub/b.mp4", deletedFolder))
        assertFalse(matchesPrefix("/storage/emulated/0/Pictures/Trip2/a.jpg", deletedFolder))
        assertFalse(matchesPrefix("/storage/emulated/0/Pictures/Trip_Backup/a.jpg", deletedFolder))
        assertFalse(matchesPrefix("/storage/emulated/0/Pictures/Other/a.jpg", deletedFolder))
    }
}
