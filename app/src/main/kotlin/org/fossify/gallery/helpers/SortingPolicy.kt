package org.fossify.gallery.helpers

/**
 * Defines sorting scopes and checkbox behavior across directory list, all-files view,
 * and individual albums.
 */
object SortingPolicy {
    enum class Scope {
        DIRECTORY_LIST,
        FILES_VIEW,
        ALBUM_CONTENT
    }

    fun resolveScope(
        isDirectorySorting: Boolean,
        path: String,
        showAll: Boolean = false
    ): Scope = when {
        isDirectorySorting -> Scope.DIRECTORY_LIST
        showAll || path.isEmpty() || path == SHOW_ALL -> Scope.FILES_VIEW
        else -> Scope.ALBUM_CONTENT
    }

    fun shouldShowUseForThisAlbumCheckbox(
        scope: Scope,
        showFolderCheckboxRequested: Boolean
    ): Boolean = scope == Scope.ALBUM_CONTENT && showFolderCheckboxRequested

    fun resolveInitialAlbumOnlyChecked(
        scope: Scope,
        hasCustomSorting: Boolean,
        isCustomSortingDisabled: Boolean
    ): Boolean {
        if (scope != Scope.ALBUM_CONTENT) {
            return false
        }
        return hasCustomSorting || !isCustomSortingDisabled
    }
}
