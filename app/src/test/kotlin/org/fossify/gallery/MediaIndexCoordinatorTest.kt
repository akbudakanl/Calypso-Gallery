package org.fossify.gallery

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.VIRTUAL_ALBUM_PREFIX
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaIndexCoordinatorTest {

    @Before
    fun setUp() {
        MediaIndexCoordinator.resetForTests()
    }

    @After
    fun tearDown() {
        MediaIndexCoordinator.resetForTests()
    }

    @Test
    fun tracksDirtyFoldersPerConsumerGenerationIndependently() {
        val mainInitialGen = MediaIndexCoordinator.getMutationGeneration()
        val mediaInitialGen = MediaIndexCoordinator.getMutationGeneration()

        MediaIndexCoordinator.markPathsDirty(
            filePaths = listOf("/storage/emulated/0/DCIM/Camera/IMG_1.jpg"),
            destinationFolder = "/storage/emulated/0/Pictures/NewAlbum/",
        )

        val mainSnapshot = MediaIndexCoordinator.getDirtyFoldersSince(mainInitialGen)
        assertEquals(1L, mainSnapshot.generation)
        assertEquals(
            setOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Pictures/NewAlbum"),
            mainSnapshot.folders
        )
        assertTrue(MediaIndexCoordinator.getDirtyFoldersSince(mainSnapshot.generation).folders.isEmpty())

        MediaIndexCoordinator.markFoldersDirty(
            folderPaths = listOf("${VIRTUAL_ALBUM_PREFIX}42"),
            includeFavorites = true,
            includeRecycleBin = true,
        )

        val mediaSnapshot = MediaIndexCoordinator.getDirtyFoldersSince(mediaInitialGen)
        assertEquals(2L, mediaSnapshot.generation)
        assertEquals(
            setOf(
                "/storage/emulated/0/DCIM/Camera",
                "/storage/emulated/0/Pictures/NewAlbum",
                "${VIRTUAL_ALBUM_PREFIX}42",
                FAVORITES,
                RECYCLE_BIN,
            ),
            mediaSnapshot.folders
        )
    }

    @Test
    fun matchesFolderScopeHandlesExactCaseInsensitiveTrailingSlashShowAllAndSpecialAlbums() {
        val dirty = setOf(
            "/storage/emulated/0/Pictures/Vacation",
            "${VIRTUAL_ALBUM_PREFIX}7",
            FAVORITES,
            RECYCLE_BIN,
        )

        assertTrue(MediaIndexCoordinator.matchesFolderScope(dirty, "/storage/emulated/0/Pictures/Vacation", showAll = false))
        assertTrue(MediaIndexCoordinator.matchesFolderScope(dirty, "/storage/emulated/0/pictures/vacation/", showAll = false))
        assertTrue(MediaIndexCoordinator.matchesFolderScope(dirty, "${VIRTUAL_ALBUM_PREFIX}7", showAll = false))
        assertTrue(MediaIndexCoordinator.matchesFolderScope(dirty, FAVORITES, showAll = false))
        assertTrue(MediaIndexCoordinator.matchesFolderScope(dirty, RECYCLE_BIN, showAll = false))
        assertTrue(MediaIndexCoordinator.matchesFolderScope(dirty, "/storage/emulated/0/Other", showAll = true))
        assertTrue(MediaIndexCoordinator.matchesFolderScope(dirty, "", showAll = false))

        assertFalse(MediaIndexCoordinator.matchesFolderScope(dirty, "/storage/emulated/0/DCIM/Camera", showAll = false))
        assertFalse(MediaIndexCoordinator.matchesFolderScope(dirty, "${VIRTUAL_ALBUM_PREFIX}8", showAll = false))
        assertFalse(MediaIndexCoordinator.matchesFolderScope(emptySet(), "/storage/emulated/0/Pictures/Vacation", showAll = true))
    }

    @Test
    fun recentDirtyPhysicalFoldersFiltersSpecialAlbumsAndRespectsAge() {
        MediaIndexCoordinator.markFoldersDirty(
            folderPaths = listOf(
                "/storage/emulated/0/Pictures/CreatedFolder/",
                "${VIRTUAL_ALBUM_PREFIX}12",
            ),
            includeFavorites = true,
            includeRecycleBin = true,
        )

        val recent = MediaIndexCoordinator.getRecentDirtyPhysicalFolders(maxAgeMs = 60_000L)
        assertEquals(setOf("/storage/emulated/0/Pictures/CreatedFolder"), recent)

        val expired = MediaIndexCoordinator.getRecentDirtyPhysicalFolders(maxAgeMs = 5_000L, nowElapsedMs = Long.MAX_VALUE / 2)
        assertTrue(expired.isEmpty())
    }

    @Test
    fun listenersReceiveMutationsAndForegroundChecksUntilUnregistered() {
        val mutationEvents = ArrayList<Pair<Long, Set<String>>>()
        val foregroundChecks = ArrayList<Long>()

        val listener = object : MediaIndexCoordinator.Listener {
            override fun onMediaIndexMutation(mutationGeneration: Long, dirtyFolders: Set<String>) {
                mutationEvents.add(mutationGeneration to dirtyFolders)
            }

            override fun onForegroundOrScreenOnCheck(checkGeneration: Long) {
                foregroundChecks.add(checkGeneration)
            }
        }

        MediaIndexCoordinator.registerListener(listener)
        MediaIndexCoordinator.markFoldersDirty(listOf("/storage/emulated/0/DCIM/Screenshots"))
        MediaIndexCoordinator.notifyForegroundOrScreenOn()

        assertEquals(1, mutationEvents.size)
        assertEquals(setOf("/storage/emulated/0/DCIM/Screenshots"), mutationEvents[0].second)
        assertEquals(listOf(1L), foregroundChecks)

        MediaIndexCoordinator.unregisterListener(listener)
        MediaIndexCoordinator.markFoldersDirty(listOf("/storage/emulated/0/DCIM/Camera"))
        MediaIndexCoordinator.notifyForegroundOrScreenOn()

        assertEquals(1, mutationEvents.size)
        assertEquals(1, foregroundChecks.size)
        assertEquals(2L, MediaIndexCoordinator.getForegroundCheckGeneration())
    }

    @Test
    fun clearVaultPathsRemovesVaultFoldersWhilePreservingNormalGalleryFolders() {
        val initialGen = MediaIndexCoordinator.getMutationGeneration()
        val normalFolder = "/storage/emulated/0/DCIM/Camera"
        val secretsAlbum = "/storage/emulated/0/Calypso Gallery/Secrets/abc123"
        val externalVaultAlbum = "/storage/emulated/0/CustomVault/xyz789"
        val dotVaultFolder = "/storage/emulated/0/Pictures/.fvault_test"

        MediaIndexCoordinator.markFoldersDirty(
            listOf(normalFolder, secretsAlbum, externalVaultAlbum, dotVaultFolder)
        )
        MediaIndexCoordinator.clearVaultPaths(vaultRootPath = "/storage/emulated/0/CustomVault")

        val dirtyAfterClear = MediaIndexCoordinator.getDirtyFoldersSince(initialGen).folders
        assertEquals(setOf(normalFolder), dirtyAfterClear)
        assertEquals(setOf(normalFolder), MediaIndexCoordinator.getRecentDirtyPhysicalFolders())
    }
}
