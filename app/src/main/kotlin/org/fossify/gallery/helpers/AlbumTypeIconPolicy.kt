package org.fossify.gallery.helpers

enum class AlbumTypeIcon {
    NONE,
    FAVORITES,
    VIRTUAL,
    EXCLUDED,
    HIDDEN
}

object AlbumTypeIconPolicy {
    fun resolve(
        isFavorites: Boolean,
        isVirtualAlbum: Boolean,
        isExcluded: Boolean,
        isHidden: Boolean,
        isRecycleBin: Boolean,
        showFavoritesIcon: Boolean,
        showVirtualIcon: Boolean,
        showExcludedIcon: Boolean,
        showHiddenIcon: Boolean,
        isSelected: Boolean
    ): AlbumTypeIcon {
        if (isSelected || isRecycleBin) {
            return AlbumTypeIcon.NONE
        }
        return when {
            isFavorites -> if (showFavoritesIcon) AlbumTypeIcon.FAVORITES else AlbumTypeIcon.NONE
            isVirtualAlbum -> if (showVirtualIcon) AlbumTypeIcon.VIRTUAL else AlbumTypeIcon.NONE
            isExcluded && showExcludedIcon -> AlbumTypeIcon.EXCLUDED
            isHidden && showHiddenIcon -> AlbumTypeIcon.HIDDEN
            else -> AlbumTypeIcon.NONE
        }
    }
}
