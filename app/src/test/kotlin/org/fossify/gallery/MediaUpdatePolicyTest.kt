package org.fossify.gallery

import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.MediaUpdatePolicy
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUpdatePolicyTest {

    private fun medium(
        id: Long?,
        path: String,
        modified: Long = 1000L,
        isFavorite: Boolean = false,
        gridPosition: Int = 0
    ) = Medium(
        id = id,
        name = path.substringAfterLast('/'),
        path = path,
        parentPath = path.substringBeforeLast('/'),
        modified = modified,
        taken = modified,
        size = 2048L,
        type = TYPE_IMAGES,
        videoDuration = 0,
        isFavorite = isFavorite,
        deletedTS = 0L,
        mediaStoreId = 10L,
        gridPosition = gridPosition
    )

    private fun directory(
        id: Long?,
        path: String,
        tmb: String = "$path/cover.jpg",
        mediaCnt: Int = 5,
        modified: Long = 1000L,
        sortValue: String = "1000"
    ) = Directory(
        id = id,
        path = path,
        tmb = tmb,
        name = path.substringAfterLast('/'),
        mediaCnt = mediaCnt,
        modified = modified,
        taken = modified,
        size = 4096L,
        location = LOCATION_INTERNAL,
        types = TYPE_IMAGES,
        sortValue = sortValue
    )

    @Test
    fun contentHashIgnoresDatabaseIdAndGridPosition() {
        val cached = listOf(
            ThumbnailSection("Today"),
            medium(id = 1L, path = "/storage/emulated/0/DCIM/a.jpg", gridPosition = 0),
            medium(id = 2L, path = "/storage/emulated/0/DCIM/b.jpg", gridPosition = 1)
        )
        val scanned = listOf(
            ThumbnailSection("Today"),
            medium(id = null, path = "/storage/emulated/0/DCIM/a.jpg", gridPosition = 5),
            medium(id = null, path = "/storage/emulated/0/DCIM/b.jpg", gridPosition = 6)
        )

        assertEquals(
            MediaUpdatePolicy.computeMediaContentHash(cached),
            MediaUpdatePolicy.computeMediaContentHash(scanned)
        )
    }

    @Test
    fun contentHashChangesWhenMediaContentOrOrderChanges() {
        val initial = listOf(
            medium(id = 1L, path = "/storage/emulated/0/DCIM/a.jpg"),
            medium(id = 2L, path = "/storage/emulated/0/DCIM/b.jpg")
        )
        val reordered = listOf(
            medium(id = null, path = "/storage/emulated/0/DCIM/b.jpg"),
            medium(id = null, path = "/storage/emulated/0/DCIM/a.jpg")
        )
        val modifiedItem = listOf(
            medium(id = null, path = "/storage/emulated/0/DCIM/a.jpg", modified = 2000L),
            medium(id = null, path = "/storage/emulated/0/DCIM/b.jpg")
        )

        assertNotEquals(
            MediaUpdatePolicy.computeMediaContentHash(initial),
            MediaUpdatePolicy.computeMediaContentHash(reordered)
        )
        assertNotEquals(
            MediaUpdatePolicy.computeMediaContentHash(initial),
            MediaUpdatePolicy.computeMediaContentHash(modifiedItem)
        )
    }

    @Test
    fun reconcileSelectedKeysKeepsRemainingItemsAndDropsRemovedOnes() {
        val pathA = "/storage/emulated/0/DCIM/a.jpg"
        val pathB = "/storage/emulated/0/DCIM/b.jpg"
        val pathC = "/storage/emulated/0/DCIM/c.jpg"
        val selectedKeys = linkedSetOf(pathA.hashCode(), pathB.hashCode())

        val updatedItems = listOf(
            ThumbnailSection("Today"),
            medium(id = null, path = pathA),
            medium(id = null, path = pathC)
        )

        MediaUpdatePolicy.reconcileSelectedKeys(selectedKeys, updatedItems)

        assertEquals(linkedSetOf(pathA.hashCode()), selectedKeys)
    }

    @Test
    fun reconcileSelectedKeysBecomesEmptyWhenAllSelectedItemsRemoved() {
        val pathA = "/storage/emulated/0/DCIM/a.jpg"
        val pathB = "/storage/emulated/0/DCIM/b.jpg"
        val selectedKeys = linkedSetOf(pathA.hashCode())

        MediaUpdatePolicy.reconcileSelectedKeys(
            selectedKeys,
            listOf(medium(id = null, path = pathB))
        )

        assertTrue(selectedKeys.isEmpty())
    }

    @Test
    fun directoryContentHashIgnoresDatabaseIdAndTimestamps() {
        val cached = listOf(
            directory(id = 1L, path = "/storage/emulated/0/DCIM/Camera", modified = 5000L, sortValue = "5000"),
            directory(id = 2L, path = "/storage/emulated/0/Pictures", modified = 4000L, sortValue = "4000")
        )
        val scanned = listOf(
            directory(id = null, path = "/storage/emulated/0/DCIM/Camera", modified = 0L, sortValue = "0"),
            directory(id = null, path = "/storage/emulated/0/Pictures", modified = 0L, sortValue = "0")
        )

        assertEquals(
            MediaUpdatePolicy.computeDirectoryContentHash(cached),
            MediaUpdatePolicy.computeDirectoryContentHash(scanned)
        )
    }

    @Test
    fun directoryContentHashChangesWhenDisplayFieldsOrOrderChange() {
        val initial = listOf(
            directory(id = 1L, path = "/a", mediaCnt = 5),
            directory(id = 2L, path = "/b", mediaCnt = 3)
        )
        val reordered = listOf(
            directory(id = null, path = "/b", mediaCnt = 3),
            directory(id = null, path = "/a", mediaCnt = 5)
        )
        val countChanged = listOf(
            directory(id = null, path = "/a", mediaCnt = 6),
            directory(id = null, path = "/b", mediaCnt = 3)
        )

        assertNotEquals(
            MediaUpdatePolicy.computeDirectoryContentHash(initial),
            MediaUpdatePolicy.computeDirectoryContentHash(reordered)
        )
        assertNotEquals(
            MediaUpdatePolicy.computeDirectoryContentHash(initial),
            MediaUpdatePolicy.computeDirectoryContentHash(countChanged)
        )
    }

    @Test
    fun reconcileSelectedDirectoryKeysKeepsRemainingDirectories() {
        val pathA = "/storage/emulated/0/DCIM/Camera"
        val pathB = "/storage/emulated/0/Pictures"
        val pathC = "/storage/emulated/0/Download"
        val selectedKeys = linkedSetOf(pathA.hashCode(), pathB.hashCode())

        MediaUpdatePolicy.reconcileSelectedDirectoryKeys(
            selectedKeys,
            listOf(directory(id = null, path = pathA), directory(id = null, path = pathC))
        )

        assertEquals(linkedSetOf(pathA.hashCode()), selectedKeys)
    }

    @Test
    fun mergeCachedMediaMetadataPreventsZeroOverwriteHashMismatch() {
        val cachedItem = Medium(
            id = 1L,
            name = "vid.mp4",
            path = "/storage/emulated/0/DCIM/vid.mp4",
            parentPath = "/storage/emulated/0/DCIM",
            modified = 9000L,
            taken = 8500L,
            size = 4096L,
            type = TYPE_IMAGES,
            videoDuration = 12,
            isFavorite = true,
            deletedTS = 0L,
            mediaStoreId = 77L
        )
        val scannedWithZeros = Medium(
            id = null,
            name = "vid.mp4",
            path = "/storage/emulated/0/DCIM/vid.mp4",
            parentPath = "/storage/emulated/0/DCIM",
            modified = 0L,
            taken = 0L,
            size = 0L,
            type = TYPE_IMAGES,
            videoDuration = 0,
            isFavorite = true,
            deletedTS = 0L,
            mediaStoreId = 0L
        )

        MediaUpdatePolicy.mergeCachedMediaMetadata(listOf(scannedWithZeros), listOf(cachedItem))

        assertEquals(
            MediaUpdatePolicy.computeMediaContentHash(listOf(cachedItem)),
            MediaUpdatePolicy.computeMediaContentHash(listOf(scannedWithZeros))
        )
        assertTrue(MediaUpdatePolicy.haveSameMediaPaths(listOf(cachedItem), listOf(scannedWithZeros)))
    }

    @Test
    fun haveSameDirectoryPathsDetectsOrderOrPathChanges() {
        val a = directory(id = 1L, path = "/a")
        val b = directory(id = 2L, path = "/b")
        val aUpdated = directory(id = null, path = "/a", mediaCnt = 10)

        assertTrue(MediaUpdatePolicy.haveSameDirectoryPaths(listOf(a, b), listOf(aUpdated, b)))
        assertFalse(MediaUpdatePolicy.haveSameDirectoryPaths(listOf(a, b), listOf(b, a)))
    }
}
