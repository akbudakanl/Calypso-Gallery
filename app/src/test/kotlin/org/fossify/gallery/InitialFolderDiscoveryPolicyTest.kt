package org.fossify.gallery

import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.helpers.InitialFolderDiscoveryPolicy
import org.fossify.gallery.helpers.VAULT_SUBFOLDER_META_FILE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InitialFolderDiscoveryPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun scopedStorageNeverRunsDirectFilesystemWalk() {
        val scopedLevel = InitialFolderDiscoveryPolicy.currentStorageScanLevel(
            isRPlus = true,
            hasAllFilesAccess = false
        )
        assertEquals(InitialFolderDiscoveryPolicy.SCAN_LEVEL_SCOPED, scopedLevel)
        assertFalse(
            InitialFolderDiscoveryPolicy.shouldRunStorageFolderDiscovery(
                completedLevel = InitialFolderDiscoveryPolicy.SCAN_LEVEL_NONE,
                currentLevel = scopedLevel,
                appRunCount = 2,
                isSecretVaultView = false
            )
        )
    }

    @Test
    fun freshInstallWithFullAccessRunsDiscoveryEvenWhenAppRunCountWasBumpedToTwo() {
        // App.onCreate sets appRunCount=1 and MainActivity.appLaunched increments it to 2 on first launch.
        val fullLevel = InitialFolderDiscoveryPolicy.currentStorageScanLevel(
            isRPlus = true,
            hasAllFilesAccess = true
        )
        assertEquals(InitialFolderDiscoveryPolicy.SCAN_LEVEL_FULL, fullLevel)
        assertTrue(
            InitialFolderDiscoveryPolicy.shouldRunStorageFolderDiscovery(
                completedLevel = InitialFolderDiscoveryPolicy.SCAN_LEVEL_NONE,
                currentLevel = fullLevel,
                appRunCount = 2,
                isSecretVaultView = false
            )
        )
    }

    @Test
    fun grantingAllFilesAccessTriggersUpgradeDiscoveryOnce() {
        val fullLevel = InitialFolderDiscoveryPolicy.currentStorageScanLevel(
            isRPlus = true,
            hasAllFilesAccess = true
        )
        assertEquals(InitialFolderDiscoveryPolicy.SCAN_LEVEL_FULL, fullLevel)

        // Upgrading from scoped (1) to full (2) must trigger discovery immediately
        assertTrue(
            InitialFolderDiscoveryPolicy.shouldRunStorageFolderDiscovery(
                completedLevel = InitialFolderDiscoveryPolicy.SCAN_LEVEL_SCOPED,
                currentLevel = fullLevel,
                appRunCount = 2,
                isSecretVaultView = false
            )
        )

        // Once completed at full level, normal launches skip the disk walk until run % 30 == 0
        assertFalse(
            InitialFolderDiscoveryPolicy.shouldRunStorageFolderDiscovery(
                completedLevel = fullLevel,
                currentLevel = fullLevel,
                appRunCount = 3,
                isSecretVaultView = false
            )
        )
        assertTrue(
            InitialFolderDiscoveryPolicy.shouldRunStorageFolderDiscovery(
                completedLevel = fullLevel,
                currentLevel = fullLevel,
                appRunCount = 30,
                isSecretVaultView = false
            )
        )
    }

    @Test
    fun secretVaultViewNeverRunsNormalStorageDiscovery() {
        assertFalse(
            InitialFolderDiscoveryPolicy.shouldRunStorageFolderDiscovery(
                completedLevel = InitialFolderDiscoveryPolicy.SCAN_LEVEL_NONE,
                currentLevel = InitialFolderDiscoveryPolicy.SCAN_LEVEL_FULL,
                appRunCount = 1,
                isSecretVaultView = true
            )
        )
    }

    @Test
    fun accessUpgradeForcesFullReloadOnlyOnFalseToTrueTransition() {
        assertTrue(
            InitialFolderDiscoveryPolicy.shouldForceFullReloadOnAccessChange(
                previousHasAllFilesAccess = false,
                currentHasAllFilesAccess = true
            )
        )
        assertFalse(
            InitialFolderDiscoveryPolicy.shouldForceFullReloadOnAccessChange(
                previousHasAllFilesAccess = true,
                currentHasAllFilesAccess = true
            )
        )
        assertFalse(
            InitialFolderDiscoveryPolicy.shouldForceFullReloadOnAccessChange(
                previousHasAllFilesAccess = false,
                currentHasAllFilesAccess = false
            )
        )
        assertFalse(
            InitialFolderDiscoveryPolicy.shouldForceFullReloadOnAccessChange(
                previousHasAllFilesAccess = true,
                currentHasAllFilesAccess = false
            )
        )
    }

    @Test
    fun folderSignatureStampInvalidatesOnAccessOrVisibilityChange() {
        val base = InitialFolderDiscoveryPolicy.computeFolderSignatureStamp(
            filterMedia = 31,
            directorySorting = 1024,
            tempFolderPath = "",
            isSecretVaultView = false,
            hasAllFilesAccess = false,
            temporarilyShowExcluded = false,
            temporarilyShowHiddenFolders = false
        )
        val afterAllFiles = InitialFolderDiscoveryPolicy.computeFolderSignatureStamp(
            filterMedia = 31,
            directorySorting = 1024,
            tempFolderPath = "",
            isSecretVaultView = false,
            hasAllFilesAccess = true,
            temporarilyShowExcluded = false,
            temporarilyShowHiddenFolders = false
        )
        val withExcluded = InitialFolderDiscoveryPolicy.computeFolderSignatureStamp(
            filterMedia = 31,
            directorySorting = 1024,
            tempFolderPath = "",
            isSecretVaultView = false,
            hasAllFilesAccess = true,
            temporarilyShowExcluded = true,
            temporarilyShowHiddenFolders = false
        )
        val withHidden = InitialFolderDiscoveryPolicy.computeFolderSignatureStamp(
            filterMedia = 31,
            directorySorting = 1024,
            tempFolderPath = "",
            isSecretVaultView = false,
            hasAllFilesAccess = true,
            temporarilyShowExcluded = false,
            temporarilyShowHiddenFolders = true
        )

        assertNotEquals(base, afterAllFiles)
        assertNotEquals(afterAllFiles, withExcluded)
        assertNotEquals(afterAllFiles, withHidden)
    }

    @Test
    fun discoverMediaFoldersFindsInternalAndroidMediaAndSdCardWhileSkippingRestrictedHiddenAndEncrypted() {
        val internal = tempFolder.newFolder("internal")
        val sdCard = tempFolder.newFolder("sdcard")

        val cameraDir = File(internal, "DCIM/Camera").apply { mkdirs() }
        File(cameraDir, "IMG_001.jpg").writeText("jpg")

        val customDir = File(internal, "MyTrips/Izmir").apply { mkdirs() }
        File(customDir, "beach.png").writeText("png")

        val androidMediaDir = File(internal, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images").apply { mkdirs() }
        File(androidMediaDir, "wa.jpg").writeText("jpg")

        val androidDataDir = File(internal, "Android/data/com.example/files").apply { mkdirs() }
        File(androidDataDir, "cache.jpg").writeText("jpg")

        val androidObbDir = File(internal, "Android/obb/com.game").apply { mkdirs() }
        File(androidObbDir, "asset.png").writeText("png")

        val dotHiddenDir = File(internal, "Pictures/.thumbnails").apply { mkdirs() }
        File(dotHiddenDir, "thumb.jpg").writeText("jpg")

        val noMediaDir = File(internal, "Pictures/PrivateNoMedia").apply { mkdirs() }
        File(noMediaDir, NOMEDIA).createNewFile()
        File(noMediaDir, "secret.jpg").writeText("jpg")
        val noMediaChild = File(noMediaDir, "Nested").apply { mkdirs() }
        File(noMediaChild, "nested.jpg").writeText("jpg")

        val cipherRoot = File(internal, "Calypso Gallery").apply { mkdirs() }
        val vaultDir = File(cipherRoot, "Secrets/album1").apply { mkdirs() }
        File(vaultDir, "photo.jpg").writeText("jpg")

        val standaloneMetaDir = File(internal, "CustomVaultAlbum").apply { mkdirs() }
        File(standaloneMetaDir, VAULT_SUBFOLDER_META_FILE).writeText("meta")
        File(standaloneMetaDir, "photo.jpg").writeText("jpg")

        val sdAlbumDir = File(sdCard, "Travel/2026").apply { mkdirs() }
        File(sdAlbumDir, "clip.mp4").writeText("mp4")

        val portraitParent = File(internal, "DCIM/Portraits").apply { mkdirs() }
        val portraitSub = File(portraitParent, "IMG_20260101_0001").apply { mkdirs() }
        File(portraitSub, "cover.jpg").writeText("jpg")

        val mediaExts = setOf("jpg", "png", "mp4")
        val discovered = InitialFolderDiscoveryPolicy.discoverMediaFoldersInRoots(
            roots = listOf(internal, sdCard),
            internalStoragePath = internal.absolutePath,
            excludedPrefixes = listOf(cipherRoot.absolutePath),
            includeHidden = false,
            includePortraits = true,
            isMediaFileName = { name -> name.substringAfterLast('.', "").lowercase() in mediaExts }
        )

        assertTrue(cameraDir.absolutePath in discovered)
        assertTrue(customDir.absolutePath in discovered)
        assertTrue(androidMediaDir.absolutePath in discovered)
        assertTrue(sdAlbumDir.absolutePath in discovered)
        assertTrue(portraitParent.absolutePath in discovered)

        assertFalse(androidDataDir.absolutePath in discovered)
        assertFalse(androidObbDir.absolutePath in discovered)
        assertFalse(dotHiddenDir.absolutePath in discovered)
        assertFalse(noMediaDir.absolutePath in discovered)
        assertFalse(noMediaChild.absolutePath in discovered)
        assertFalse(vaultDir.absolutePath in discovered)
        assertFalse(standaloneMetaDir.absolutePath in discovered)
    }

    @Test
    fun mergeDiscoveredFoldersPreservesOrderAndDeduplicatesCaseInsensitively() {
        val merged = InitialFolderDiscoveryPolicy.mergeDiscoveredFolders(
            baseFolders = listOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Pictures"),
            discoveredFolders = listOf(
                "/storage/emulated/0/dcim/camera/",
                "/storage/emulated/0/MyAlbums/Holiday",
                "/storage/emulated/0/Pictures"
            )
        )

        assertEquals(
            listOf(
                "/storage/emulated/0/DCIM/Camera",
                "/storage/emulated/0/Pictures",
                "/storage/emulated/0/MyAlbums/Holiday"
            ),
            merged
        )
    }
}
