package org.fossify.gallery

import org.fossify.gallery.helpers.HiddenFoldersPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenFoldersPolicyTest {

    private val internalRoot = "/storage/emulated/0"

    @Test
    fun isAndroidRestrictedSubpathDetectsDataAndObbOnly() {
        assertTrue(HiddenFoldersPolicy.isAndroidRestrictedSubpath("/storage/emulated/0/Android/data", internalRoot))
        assertTrue(HiddenFoldersPolicy.isAndroidRestrictedSubpath("/storage/emulated/0/Android/data/com.example/files/", internalRoot))
        assertTrue(HiddenFoldersPolicy.isAndroidRestrictedSubpath("/storage/emulated/0/android/obb/app", internalRoot))
        assertFalse(HiddenFoldersPolicy.isAndroidRestrictedSubpath("/storage/emulated/0/Android/media/WhatsApp", internalRoot))
        assertFalse(HiddenFoldersPolicy.isAndroidRestrictedSubpath("/storage/emulated/0/Pictures/Hidden", internalRoot))
    }

    @Test
    fun isDotHiddenPathDetectsHiddenSegments() {
        assertTrue(HiddenFoldersPolicy.isDotHiddenPath("/storage/emulated/0/.secret"))
        assertTrue(HiddenFoldersPolicy.isDotHiddenPath("/storage/emulated/0/Pictures/.thumbnails/cache/"))
        assertFalse(HiddenFoldersPolicy.isDotHiddenPath("/storage/emulated/0/Pictures/Camera"))
        assertFalse(HiddenFoldersPolicy.isDotHiddenPath(""))
    }

    @Test
    fun mergeKnownHiddenFoldersPreservesKnownEntriesWithoutAllFilesAccess() {
        val result = HiddenFoldersPolicy.mergeKnownHiddenFolders(
            cachedKnownHidden = setOf(
                "/storage/emulated/0/Pictures/OldHidden/",
                "/storage/emulated/0/Android/data/com.app",
                "/storage/emulated/0/.cipher_vault/abc"
            ),
            liveDetectedHidden = listOf("/storage/emulated/0/Download/NewNoMedia"),
            canInspectDisk = false,
            isCipherSystemPath = { it.contains(".cipher_vault") },
            isRestrictedPath = { HiddenFoldersPolicy.isAndroidRestrictedSubpath(it, internalRoot) },
            isStillHiddenOnDisk = { false }
        )

        assertEquals(
            setOf("/storage/emulated/0/Download/NewNoMedia", "/storage/emulated/0/Pictures/OldHidden"),
            result
        )
    }

    @Test
    fun mergeKnownHiddenFoldersPrunesNoLongerHiddenEntriesWhenDiskInspectionAllowed() {
        val stillHidden = setOf("/storage/emulated/0/Pictures/StillHidden")
        val result = HiddenFoldersPolicy.mergeKnownHiddenFolders(
            cachedKnownHidden = setOf(
                "/storage/emulated/0/Pictures/StillHidden",
                "/storage/emulated/0/Pictures/NowUnhidden"
            ),
            liveDetectedHidden = listOf("/storage/emulated/0/pictures/stillhidden/"),
            canInspectDisk = true,
            isCipherSystemPath = { false },
            isRestrictedPath = { HiddenFoldersPolicy.isAndroidRestrictedSubpath(it, internalRoot) },
            isStillHiddenOnDisk = { it in stillHidden }
        )

        assertEquals(setOf("/storage/emulated/0/pictures/stillhidden"), result)
    }
}
