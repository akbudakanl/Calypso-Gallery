package org.fossify.gallery

import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.RANDOM_ORDER_EXPIRY_MS
import org.fossify.gallery.helpers.RandomOrderManager
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class RandomOrderManagerTest {

    @Before
    fun setUp() {
        RandomOrderManager.resetForTests()
    }

    @After
    fun tearDown() {
        RandomOrderManager.resetForTests()
    }

    private fun dir(path: String) = Directory(
        id = null,
        path = path,
        tmb = "$path/1.jpg",
        name = path.substringAfterLast('/'),
        mediaCnt = 1,
        modified = 100L,
        taken = 100L,
        size = 100L,
        location = LOCATION_INTERNAL,
        types = TYPE_IMAGES,
        sortValue = "0"
    )

    private fun med(path: String) = Medium(
        id = null,
        name = path.substringAfterLast('/'),
        path = path,
        parentPath = path.substringBeforeLast('/'),
        modified = 100L,
        taken = 100L,
        size = 100L,
        type = TYPE_IMAGES,
        videoDuration = 0,
        isFavorite = false,
        deletedTS = 0L,
        mediaStoreId = 0L
    )

    @Test
    fun directoryRandomOrderStaysStableAcrossRepeatedCallsAndAppendsNewItems() {
        val initial = arrayListOf(dir("/a"), dir("/b"), dir("/c"), dir("/d"))
        val first = RandomOrderManager.sortDirectoriesRandomly(initial, random = Random(42))

        val second = RandomOrderManager.sortDirectoriesRandomly(
            arrayListOf(dir("/d"), dir("/b"), dir("/a"), dir("/c")),
            random = Random(99)
        )
        assertEquals(first.map { it.path }, second.map { it.path })

        val withNew = arrayListOf(dir("/e"), dir("/a"), dir("/b"), dir("/c"), dir("/d"))
        val third = RandomOrderManager.sortDirectoriesRandomly(withNew, random = Random(123))
        assertEquals(listOf("/e") + first.map { it.path }, third.map { it.path })
    }

    @Test
    fun mediaRandomOrderStaysStableAcrossCacheAndScanAndNormalizesScope() {
        val albumPath = "/storage/emulated/0/DCIM/Camera"
        val cached = arrayListOf(med("$albumPath/1.jpg"), med("$albumPath/2.jpg"), med("$albumPath/3.jpg"), med("$albumPath/4.jpg"))
        RandomOrderManager.sortMediaRandomly(cached, albumPath, random = Random(7))
        val firstOrder = cached.map { it.path }

        val scanned = arrayListOf(med("$albumPath/4.jpg"), med("$albumPath/3.jpg"), med("$albumPath/2.jpg"), med("$albumPath/1.jpg"))
        RandomOrderManager.sortMediaRandomly(scanned, "$albumPath/", random = Random(999))
        assertEquals(firstOrder, scanned.map { it.path })

        val withNewMedia = arrayListOf(
            med("$albumPath/1.jpg"),
            med("$albumPath/2.jpg"),
            med("$albumPath/3.jpg"),
            med("$albumPath/4.jpg"),
            med("$albumPath/5_screenshot.jpg")
        )
        RandomOrderManager.sortMediaRandomly(withNewMedia, albumPath, random = Random(456))
        assertEquals(listOf("$albumPath/5_screenshot.jpg") + firstOrder, withNewMedia.map { it.path })

        val allFilesEmptyScope = arrayListOf(med("/a/1.jpg"), med("/b/2.jpg"), med("/c/3.jpg"))
        RandomOrderManager.sortMediaRandomly(allFilesEmptyScope, "", random = Random(11))
        val showAllScope = arrayListOf(med("/c/3.jpg"), med("/a/1.jpg"), med("/b/2.jpg"))
        RandomOrderManager.sortMediaRandomly(showAllScope, SHOW_ALL, random = Random(22))
        assertEquals(allFilesEmptyScope.map { it.path }, showAllScope.map { it.path })
    }

    @Test
    fun backgroundBelow20MinutesPreservesOrderWhile20MinutesOrMoreExpiresIt() {
        val media = arrayListOf(med("/a/1.jpg"), med("/a/2.jpg"), med("/a/3.jpg"), med("/a/4.jpg"))
        RandomOrderManager.sortMediaRandomly(media, "/a", random = Random(5))
        val initialOrder = media.map { it.path }
        val gen1 = RandomOrderManager.getSessionGeneration()

        RandomOrderManager.onAppBackgrounded(nowMs = 1_000_000L)
        val expiredShort = RandomOrderManager.onAppForegrounded(nowMs = 1_000_000L + RANDOM_ORDER_EXPIRY_MS - 1L)
        assertFalse(expiredShort)
        assertEquals(gen1, RandomOrderManager.getSessionGeneration())

        val afterShortBg = arrayListOf(med("/a/4.jpg"), med("/a/3.jpg"), med("/a/2.jpg"), med("/a/1.jpg"))
        RandomOrderManager.sortMediaRandomly(afterShortBg, "/a", random = Random(12))
        assertEquals(initialOrder, afterShortBg.map { it.path })

        RandomOrderManager.onAppBackgrounded(nowMs = 5_000_000L)
        val expiredLong = RandomOrderManager.onAppForegrounded(nowMs = 5_000_000L + RANDOM_ORDER_EXPIRY_MS)
        assertTrue(expiredLong)
        assertEquals(gen1 + 1, RandomOrderManager.getSessionGeneration())

        val afterLongBg = arrayListOf(med("/a/1.jpg"), med("/a/2.jpg"), med("/a/3.jpg"), med("/a/4.jpg"))
        RandomOrderManager.sortMediaRandomly(afterLongBg, "/a", random = Random(1234))
        // Sorted again fresh (not forced to match initialOrder)
        assertEquals(afterLongBg.size, 4)
        assertNotEquals(gen1, RandomOrderManager.getSessionGeneration())
    }

    @Test
    fun manualShuffleSetMediaOrderPersistsAcrossSubsequentSorts() {
        val manualOrder = listOf("/a/3.jpg", "/a/1.jpg", "/a/4.jpg", "/a/2.jpg")
        RandomOrderManager.setMediaOrder("/a", manualOrder)

        val queried = arrayListOf(med("/a/1.jpg"), med("/a/2.jpg"), med("/a/3.jpg"), med("/a/4.jpg"))
        RandomOrderManager.sortMediaRandomly(queried, "/a", random = Random(55))
        assertEquals(manualOrder, queried.map { it.path })
    }

    @Test
    fun pathRenameUpdatesStoredRandomOrderInPlace() {
        RandomOrderManager.setMediaOrder("/a", listOf("/a/1.jpg", "/a/2.jpg", "/a/3.jpg"))
        RandomOrderManager.onPathRenamed("/a/2.jpg", "/a/2_renamed.jpg")

        val queried = arrayListOf(med("/a/3.jpg"), med("/a/1.jpg"), med("/a/2_renamed.jpg"))
        RandomOrderManager.sortMediaRandomly(queried, "/a", random = Random(77))
        assertEquals(listOf("/a/1.jpg", "/a/2_renamed.jpg", "/a/3.jpg"), queried.map { it.path })
    }
}
