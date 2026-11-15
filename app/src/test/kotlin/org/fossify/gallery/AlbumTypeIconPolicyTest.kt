package org.fossify.gallery

import org.fossify.gallery.helpers.AlbumTypeIcon
import org.fossify.gallery.helpers.AlbumTypeIconPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTypeIconPolicyTest {

    private fun resolve(
        isFavorites: Boolean = false,
        isVirtualAlbum: Boolean = false,
        isExcluded: Boolean = false,
        isHidden: Boolean = false,
        isRecycleBin: Boolean = false,
        showFavoritesIcon: Boolean = true,
        showVirtualIcon: Boolean = true,
        showExcludedIcon: Boolean = true,
        showHiddenIcon: Boolean = true,
        isSelected: Boolean = false
    ): AlbumTypeIcon = AlbumTypeIconPolicy.resolve(
        isFavorites = isFavorites,
        isVirtualAlbum = isVirtualAlbum,
        isExcluded = isExcluded,
        isHidden = isHidden,
        isRecycleBin = isRecycleBin,
        showFavoritesIcon = showFavoritesIcon,
        showVirtualIcon = showVirtualIcon,
        showExcludedIcon = showExcludedIcon,
        showHiddenIcon = showHiddenIcon,
        isSelected = isSelected
    )

    @Test
    fun resolveReturnsExpectedIconWhenEnabledAndNotSelected() {
        assertEquals(AlbumTypeIcon.FAVORITES, resolve(isFavorites = true))
        assertEquals(AlbumTypeIcon.VIRTUAL, resolve(isVirtualAlbum = true))
        assertEquals(AlbumTypeIcon.EXCLUDED, resolve(isExcluded = true))
        assertEquals(AlbumTypeIcon.HIDDEN, resolve(isHidden = true))
        assertEquals(AlbumTypeIcon.NONE, resolve())
    }

    @Test
    fun selectedOrRecycleBinAlwaysHidesIcon() {
        assertEquals(
            AlbumTypeIcon.NONE,
            resolve(
                isFavorites = true,
                isVirtualAlbum = true,
                isExcluded = true,
                isHidden = true,
                isSelected = true
            )
        )

        assertEquals(
            AlbumTypeIcon.NONE,
            resolve(
                isExcluded = true,
                isHidden = true,
                isRecycleBin = true
            )
        )
    }

    @Test
    fun perTypeTogglesIndependentlyHideTheirIcons() {
        assertEquals(AlbumTypeIcon.NONE, resolve(isFavorites = true, showFavoritesIcon = false))
        assertEquals(AlbumTypeIcon.NONE, resolve(isVirtualAlbum = true, showVirtualIcon = false))
        assertEquals(AlbumTypeIcon.NONE, resolve(isExcluded = true, showExcludedIcon = false))
        assertEquals(AlbumTypeIcon.NONE, resolve(isHidden = true, showHiddenIcon = false))
    }

    @Test
    fun excludedTakesPrecedenceOverHiddenWhenBothEnabledAndHiddenShowsWhenExcludedIconDisabled() {
        assertEquals(
            AlbumTypeIcon.EXCLUDED,
            resolve(isExcluded = true, isHidden = true, showExcludedIcon = true, showHiddenIcon = true)
        )
        assertEquals(
            AlbumTypeIcon.HIDDEN,
            resolve(isExcluded = true, isHidden = true, showExcludedIcon = false, showHiddenIcon = true)
        )
        assertEquals(
            AlbumTypeIcon.FAVORITES,
            resolve(isFavorites = true, isVirtualAlbum = true, isExcluded = true, isHidden = true)
        )
        assertEquals(
            AlbumTypeIcon.VIRTUAL,
            resolve(isVirtualAlbum = true, isExcluded = true, isHidden = true)
        )
    }
}
