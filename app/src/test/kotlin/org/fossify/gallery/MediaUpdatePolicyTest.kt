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

    @Test
    fun mergeCachedMediaMetadataPreservesDistinctCachedTakenOverModifiedFallback() {
        val cachedItem = medium(id = 10L, path = "/a/1.jpg", modified = 2000L).copy(taken = 1500L)
        val scannedFallback = medium(id = null, path = "/a/1.jpg", modified = 2000L) // taken == modified == 2000L

        MediaUpdatePolicy.mergeCachedMediaMetadata(listOf(scannedFallback), listOf(cachedItem))

        assertEquals(1500L, scannedFallback.taken)

        val cachedZeroTaken = medium(id = 11L, path = "/a/2.jpg", modified = 3000L).copy(taken = 0L)
        val scannedReal = medium(id = null, path = "/a/2.jpg", modified = 3000L).copy(taken = 2500L)

        MediaUpdatePolicy.mergeCachedMediaMetadata(listOf(scannedReal), listOf(cachedZeroTaken))

        assertEquals(2500L, scannedReal.taken)
    }

    @Test
    fun isFolderMediaCacheInSyncDetectsMatchingAndDriftedCaches() {
        val a = medium(id = 1L, path = "/a/1.jpg", modified = 1000L).copy(taken = 900L, size = 500L)
        val b = medium(id = 2L, path = "/a/2.jpg", modified = 2000L, isFavorite = true).copy(taken = 1800L, size = 800L)

        assertTrue(MediaUpdatePolicy.isFolderMediaCacheInSync(listOf(a, b), listOf(b.copy(), a.copy())))
        assertFalse(MediaUpdatePolicy.isFolderMediaCacheInSync(listOf(a, b), listOf(a.copy())))
        assertFalse(MediaUpdatePolicy.isFolderMediaCacheInSync(listOf(a, b), listOf(a.copy(), b.copy(size = 999L))))
        assertFalse(MediaUpdatePolicy.isFolderMediaCacheInSync(listOf(a, b), listOf(a.copy(), b.copy(path = "/a/3.jpg"))))
    }

    @Test
    fun findVisualChangedIndicesSkipsNonVisualMetadataChanges() {
        val m0 = medium(id = 1L, path = "/a/1.jpg", modified = 1000L)
        val m1 = medium(id = 2L, path = "/a/2.jpg", modified = 2000L, isFavorite = false)
        assertEquals(
            emptyList<Int>(),
            MediaUpdatePolicy.findVisualChangedMediaIndices(
                listOf(m0, m1),
                listOf(m0.copy(taken = 500L), m1.copy(taken = 600L))
            )
        )
        assertEquals(
            listOf(1),
            MediaUpdatePolicy.findVisualChangedMediaIndices(
                listOf(m0, m1),
                listOf(m0.copy(taken = 500L), m1.copy(isFavorite = true))
            )
        )

        val d0 = directory(id = 1L, path = "/d0", mediaCnt = 5, modified = 1000L, sortValue = "1000")
        val d1 = directory(id = 2L, path = "/d1", tmb = "/d1/a.jpg", mediaCnt = 3)
        assertEquals(
            emptyList<Int>(),
            MediaUpdatePolicy.findVisualChangedDirectoryIndices(
                listOf(d0, d1),
                listOf(d0.copy(modified = 9999L, sortValue = "9999"), d1.copy(taken = 1234L))
            )
        )
        assertEquals(
            listOf(0, 1),
            MediaUpdatePolicy.findVisualChangedDirectoryIndices(
                listOf(d0, d1),
                listOf(d0.copy(mediaCnt = 6), d1.copy(tmb = "/d1/b.jpg"))
            )
        )
    }

    @Test
    fun mergeCachedMediaMetadataReconcilesMillisecondVsSecondDrift() {
        val cachedSeconds = medium(id = 1L, path = "/a/sec.jpg", modified = 1000L)
        val freshMillis = medium(id = null, path = "/a/sec.jpg", modified = 1450L).copy(taken = 1450L)

        MediaUpdatePolicy.mergeCachedMediaMetadata(listOf(freshMillis), listOf(cachedSeconds))

        assertEquals(1000L, freshMillis.modified)
        assertEquals(1000L, freshMillis.taken)

        val cachedMillis = medium(id = 2L, path = "/a/ms.jpg", modified = 1450L)
        val freshSeconds = medium(id = null, path = "/a/ms.jpg", modified = 1000L).copy(taken = 0L)

        MediaUpdatePolicy.mergeCachedMediaMetadata(listOf(freshSeconds), listOf(cachedMillis))

        assertEquals(1450L, freshSeconds.modified)
        assertEquals(1450L, freshSeconds.taken)

        val cachedOlder = medium(id = 3L, path = "/a/real_change.jpg", modified = 1000L)
        val freshNewer = medium(id = null, path = "/a/real_change.jpg", modified = 2000L)

        MediaUpdatePolicy.mergeCachedMediaMetadata(listOf(freshNewer), listOf(cachedOlder))

        assertEquals(2000L, freshNewer.modified)
    }

    @Test
    fun hasSameVisualThumbnailChecksPathTypeSizeAndModifiedOnly() {
        val base = medium(id = 1L, path = "/a/1.jpg", modified = 1000L, isFavorite = false, gridPosition = 0)
        val metadataOnlyChange = base.copy(
            id = 99L,
            name = "renamed.jpg",
            taken = 500L,
            videoDuration = 12,
            isFavorite = true,
            mediaStoreId = 999L,
            gridPosition = 4
        )
        assertTrue(MediaUpdatePolicy.hasSameVisualThumbnail(base, metadataOnlyChange))
        assertFalse(MediaUpdatePolicy.hasSameVisualThumbnail(base, base.copy(path = "/a/2.jpg")))
        assertFalse(MediaUpdatePolicy.hasSameVisualThumbnail(base, base.copy(type = TYPE_IMAGES + 1)))
        assertFalse(MediaUpdatePolicy.hasSameVisualThumbnail(base, base.copy(size = base.size + 1L)))
        assertFalse(MediaUpdatePolicy.hasSameVisualThumbnail(base, base.copy(modified = base.modified + 1000L)))
    }

    @Test
    fun hasSameVisualDirectoryChecksVisualFieldsOnly() {
        val base = directory(id = 1L, path = "/d0", tmb = "/d0/1.jpg", mediaCnt = 5, modified = 1000L, sortValue = "1000")
        val nonVisualChange = base.copy(id = 42L, modified = 9999L, taken = 8888L, size = 123456L, sortValue = "9999")
        assertTrue(MediaUpdatePolicy.hasSameVisualDirectory(base, nonVisualChange))
        assertFalse(MediaUpdatePolicy.hasSameVisualDirectory(base, base.copy(tmb = "/d0/2.jpg")))
        assertFalse(MediaUpdatePolicy.hasSameVisualDirectory(base, base.copy(name = "Other")))
        assertFalse(MediaUpdatePolicy.hasSameVisualDirectory(base, base.copy(mediaCnt = 6)))
        assertFalse(MediaUpdatePolicy.hasSameVisualDirectory(base, base.copy(subfoldersCount = 2)))
        assertFalse(MediaUpdatePolicy.hasSameVisualDirectory(base, base.copy(subfoldersMediaCount = 10)))
        assertFalse(MediaUpdatePolicy.hasSameVisualDirectory(base, base.copy(location = LOCATION_INTERNAL + 1)))
        assertFalse(MediaUpdatePolicy.hasSameVisualDirectory(base, base.copy(types = TYPE_IMAGES + 2)))
    }

    @Test
    fun computeViewerMediaContentHashIgnoresTransientFieldsAndDetectsContentChanges() {
        val a = medium(id = 1L, path = "/a/1.jpg", modified = 1000L, isFavorite = false, gridPosition = 0)
        val b = medium(id = 2L, path = "/a/2.mp4", modified = 2000L, isFavorite = true, gridPosition = 1)
            .copy(videoDuration = 15, size = 4096L)

        val transientChanged = listOf(
            a.copy(id = 50L, gridPosition = 10, mediaStoreId = 500L, taken = 999L),
            b.copy(id = 51L, gridPosition = 11, mediaStoreId = 501L, taken = 888L)
        )
        assertEquals(
            MediaUpdatePolicy.computeViewerMediaContentHash(listOf(a, b)),
            MediaUpdatePolicy.computeViewerMediaContentHash(transientChanged)
        )

        val baseHash = MediaUpdatePolicy.computeViewerMediaContentHash(listOf(a, b))
        assertNotEquals(baseHash, MediaUpdatePolicy.computeViewerMediaContentHash(listOf(b, a)))
        assertNotEquals(baseHash, MediaUpdatePolicy.computeViewerMediaContentHash(listOf(a.copy(path = "/a/3.jpg"), b)))
        assertNotEquals(baseHash, MediaUpdatePolicy.computeViewerMediaContentHash(listOf(a.copy(isFavorite = true), b)))
        assertNotEquals(baseHash, MediaUpdatePolicy.computeViewerMediaContentHash(listOf(a, b.copy(videoDuration = 20))))
        assertNotEquals(baseHash, MediaUpdatePolicy.computeViewerMediaContentHash(listOf(a.copy(size = 9999L), b)))
        assertNotEquals(baseHash, MediaUpdatePolicy.computeViewerMediaContentHash(listOf(a.copy(modified = 5000L), b)))
    }
}
