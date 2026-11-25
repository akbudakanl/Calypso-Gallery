package org.fossify.gallery

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.helpers.ENCRYPTED_FOLDER_PREFIX
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.SECRETS_DIR_NAME
import org.fossify.gallery.helpers.ScreenshotFolderObserver
import org.fossify.gallery.helpers.VIRTUAL_ALBUM_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScreenshotFolderObserverTest {

    @Test
    fun shouldNotifyForFilenameFiltersPendingHiddenAndNonMediaFiles() {
        assertTrue(ScreenshotFolderObserver.shouldNotifyForFilename("Screenshot_20261125_025602.png"))
        assertTrue(ScreenshotFolderObserver.shouldNotifyForFilename("IMG_001.JPG"))
        assertTrue(ScreenshotFolderObserver.shouldNotifyForFilename("clip.webm"))
        assertTrue(ScreenshotFolderObserver.shouldNotifyForFilename("screenrecord.mp4"))
        assertTrue(ScreenshotFolderObserver.shouldNotifyForFilename("photo.heic"))

        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename(null))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename(""))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename("   "))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename(".pending-1732492562-Screenshot.png"))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename(".trashed-1732492562-Screenshot.png"))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename(".nomedia"))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename("Screenshot.png.tmp"))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename("capture.part"))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename("capture.crdownload"))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename("capture.pending"))
        assertFalse(ScreenshotFolderObserver.shouldNotifyForFilename("notes.txt"))
    }

    @Test
    fun resolveWatchDirectoriesSkipsHiddenVaultAndNonExistentDirsAndDeduplicates() {
        val tempRoot = Files.createTempDirectory("screenshot_observer_test").toFile()
        try {
            val screenshots = File(tempRoot, "Pictures/Screenshots").apply { mkdirs() }
            val dcimScreenshots = File(tempRoot, "DCIM/Screenshots").apply { mkdirs() }
            val missing = File(tempRoot, "Missing/Screenshots")
            val hidden = File(tempRoot, "Pictures/.hiddenScreenshots").apply { mkdirs() }
            val secrets = File(tempRoot, "Calypso Gallery/$SECRETS_DIR_NAME").apply { mkdirs() }
            val encAlbum = File(tempRoot, "${ENCRYPTED_FOLDER_PREFIX}album").apply { mkdirs() }

            val resolved = ScreenshotFolderObserver.resolveWatchDirectories(
                listOf(
                    screenshots,
                    File(tempRoot, "Pictures/./Screenshots"),
                    dcimScreenshots,
                    missing,
                    hidden,
                    secrets,
                    encAlbum,
                )
            )

            assertEquals(2, resolved.size)
            assertEquals(
                setOf(screenshots.canonicalPath, dcimScreenshots.canonicalPath),
                resolved.map { it.canonicalPath }.toSet()
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun targetedDirectoryRefreshPolicyAcceptsPhysicalFoldersAndRejectsWildcardsOrSpecialScopes() {
        val screenshots = "/storage/emulated/0/Pictures/Screenshots"
        assertTrue(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf(screenshots),
            )
        )

        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = false,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf(screenshots),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = false,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf(screenshots),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = true,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf(screenshots),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = true,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf(screenshots),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = true,
                isWildcardChange = false,
                changedFolderPaths = listOf(screenshots),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = true,
                changedFolderPaths = listOf(screenshots),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = emptyList(),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf(FAVORITES),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf(RECYCLE_BIN),
            )
        )
        assertFalse(
            MediaIndexCoordinator.canUseTargetedDirectoryRefresh(
                loadedInitialPhotos = true,
                hasDisplayedDirs = true,
                isSecretVaultView = false,
                isSearchOpen = false,
                hasActiveInitialScan = false,
                isWildcardChange = false,
                changedFolderPaths = listOf("${VIRTUAL_ALBUM_PREFIX}abc"),
            )
        )
    }
}
