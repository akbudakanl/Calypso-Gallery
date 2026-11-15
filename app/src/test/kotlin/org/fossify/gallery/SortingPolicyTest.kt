package org.fossify.gallery

import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SortingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SortingPolicyTest {

    @Test
    fun directoryAndFilesViewsNeverShowUseForThisAlbumCheckbox() {
        val dirScope = SortingPolicy.resolveScope(isDirectorySorting = true, path = "")
        val filesScopeEmpty = SortingPolicy.resolveScope(isDirectorySorting = false, path = "")
        val filesScopeShowAll = SortingPolicy.resolveScope(isDirectorySorting = false, path = SHOW_ALL)
        val filesScopeFlag = SortingPolicy.resolveScope(isDirectorySorting = false, path = "/storage/emulated/0/DCIM", showAll = true)

        assertEquals(SortingPolicy.Scope.DIRECTORY_LIST, dirScope)
        assertEquals(SortingPolicy.Scope.FILES_VIEW, filesScopeEmpty)
        assertEquals(SortingPolicy.Scope.FILES_VIEW, filesScopeShowAll)
        assertEquals(SortingPolicy.Scope.FILES_VIEW, filesScopeFlag)

        assertFalse(SortingPolicy.shouldShowUseForThisAlbumCheckbox(dirScope, showFolderCheckboxRequested = true))
        assertFalse(SortingPolicy.shouldShowUseForThisAlbumCheckbox(filesScopeEmpty, showFolderCheckboxRequested = true))
        assertFalse(SortingPolicy.shouldShowUseForThisAlbumCheckbox(filesScopeShowAll, showFolderCheckboxRequested = true))
        assertFalse(SortingPolicy.shouldShowUseForThisAlbumCheckbox(filesScopeFlag, showFolderCheckboxRequested = true))
    }

    @Test
    fun albumViewShowsCheckboxAndDefaultsToChecked() {
        val albumScope = SortingPolicy.resolveScope(isDirectorySorting = false, path = "/storage/emulated/0/DCIM/Camera")
        assertEquals(SortingPolicy.Scope.ALBUM_CONTENT, albumScope)
        assertTrue(SortingPolicy.shouldShowUseForThisAlbumCheckbox(albumScope, showFolderCheckboxRequested = true))
        assertFalse(SortingPolicy.shouldShowUseForThisAlbumCheckbox(albumScope, showFolderCheckboxRequested = false))

        // Default ON when untouched
        assertTrue(SortingPolicy.resolveInitialAlbumOnlyChecked(albumScope, hasCustomSorting = false, isCustomSortingDisabled = false))
        // Stays ON when custom sorting is saved
        assertTrue(SortingPolicy.resolveInitialAlbumOnlyChecked(albumScope, hasCustomSorting = true, isCustomSortingDisabled = false))
        // Respects explicit user opt-out for that album
        assertFalse(SortingPolicy.resolveInitialAlbumOnlyChecked(albumScope, hasCustomSorting = false, isCustomSortingDisabled = true))
        // Never ON for non-album scopes
        assertFalse(SortingPolicy.resolveInitialAlbumOnlyChecked(SortingPolicy.Scope.FILES_VIEW, hasCustomSorting = true, isCustomSortingDisabled = false))
        assertFalse(SortingPolicy.resolveInitialAlbumOnlyChecked(SortingPolicy.Scope.DIRECTORY_LIST, hasCustomSorting = true, isCustomSortingDisabled = false))
    }
}
