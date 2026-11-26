package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_COUNT
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.sumByLong
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.RandomOrderManager
import org.fossify.gallery.models.Directory
import java.io.File
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.max

fun Context.movePinnedDirectoriesToFront(dirs: ArrayList<Directory>): ArrayList<Directory> {
    val foundFolders = ArrayList<Directory>()
    val pinnedFolders = config.pinnedFolders

    dirs.forEach {
        if (pinnedFolders.contains(it.path)) {
            foundFolders.add(it)
        }
    }

    dirs.removeAll(foundFolders)
    dirs.addAll(0, foundFolders)
    if (config.tempFolderPath.isNotEmpty()) {
        val newFolder = dirs.firstOrNull { it.path == config.tempFolderPath }
        if (newFolder != null) {
            dirs.remove(newFolder)
            dirs.add(0, newFolder)
        }
    }

    if (config.showRecycleBinAtFolders && config.showRecycleBinLast) {
        val binIndex = dirs.indexOfFirst { it.isRecycleBin() }
        if (binIndex != -1) {
            val bin = dirs.removeAt(binIndex)
            dirs.add(bin)
        }
    }
    return dirs
}

private data class SortKeys(val value: String, val path: String)

@Suppress("UNCHECKED_CAST")
fun Context.getSortedDirectories(source: ArrayList<Directory>): ArrayList<Directory> {
    val sorting = config.directorySorting
    val dirs = source.clone() as ArrayList<Directory>

    if (sorting and SORT_BY_RANDOM != 0) {
        val randomlyOrdered = RandomOrderManager.sortDirectoriesRandomly(dirs)
        return movePinnedDirectoriesToFront(randomlyOrdered)
    } else if (sorting and SORT_BY_CUSTOM != 0) {
        val newDirsOrdered = ArrayList<Directory>()
        config.customFoldersOrder.split("|||").forEach { path ->
            val index = dirs.indexOfFirst { it.path == path }
            if (index != -1) {
                val dir = dirs.removeAt(index)
                newDirsOrdered.add(dir)
            }
        }

        dirs.mapTo(newDirsOrdered, { it })
        return newDirsOrdered
    }

    // Precompute the normalized comparison keys once per directory instead of doing
    // lowercase()/normalizeString() on every single comparator invocation (O(n log n) allocations).
    // IdentityHashMap is used because Directory is mutable and must not be used as a hash key.
    val usesTextSort = sorting and SORT_BY_NAME != 0 || sorting and SORT_BY_PATH != 0
    val sortKeys = if (usesTextSort) IdentityHashMap<Directory, SortKeys>() else null
    if (sortKeys != null) {
        val byName = sorting and SORT_BY_NAME != 0
        dirs.forEach { dir ->
            if (dir.sortValue.isEmpty()) {
                dir.sortValue = if (byName) dir.name else dir.path
                dir.sortValue = dir.sortValue.lowercase(Locale.getDefault())
            }
            // NAME sorting normalizes the key, PATH sorting keeps it as plain lowercase.
            val value = if (byName) dir.sortValue.normalizeString().lowercase(Locale.getDefault()) else dir.sortValue
            sortKeys[dir] = SortKeys(value = value, path = dir.path.lowercase(Locale.getDefault()))
        }
    }

    val alphanumericComparator = if (usesTextSort && sorting and SORT_USE_NUMERIC_VALUE != 0) AlphanumericComparator() else null
    dirs.sortWith { o1, o2 ->
        o1 as Directory
        o2 as Directory

        val k1 = sortKeys?.get(o1)
        val k2 = sortKeys?.get(o2)
        var result = when {
            sorting and SORT_BY_NAME != 0 || sorting and SORT_BY_PATH != 0 -> {
                if (alphanumericComparator != null) {
                    alphanumericComparator.compare(string1 = k1!!.value, string2 = k2!!.value)
                } else {
                    k1!!.value.compareTo(k2!!.value)
                }
            }

            sorting and SORT_BY_SIZE != 0 -> o1.size.compareTo(o2.size)
            sorting and SORT_BY_COUNT != 0 -> o1.mediaCnt.compareTo(o2.mediaCnt)
            sorting and SORT_BY_DATE_MODIFIED != 0 -> o1.modified.compareTo(o2.modified)
            sorting and SORT_BY_DATE_TAKEN != 0 -> o1.taken.compareTo(o2.taken)
            else -> (o1.sortValue.toLongOrNull() ?: 0L).compareTo(o2.sortValue.toLongOrNull() ?: 0L)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }
        if (result == 0) {
            result = (k1?.path ?: o1.path.lowercase(Locale.getDefault()))
                .compareTo(k2?.path ?: o2.path.lowercase(Locale.getDefault()))
        }
        result
    }

    return movePinnedDirectoriesToFront(dirs)
}

fun Context.getDirsToShow(
    dirs: ArrayList<Directory>,
    allDirs: ArrayList<Directory>,
    currentPathPrefix: String
): ArrayList<Directory> {
    return if (config.groupDirectSubfolders) {
        dirs.forEach {
            it.subfoldersCount = 0
            it.subfoldersMediaCount = it.mediaCnt
        }

        val filledDirs = fillWithSharedDirectParents(dirs)
        val parentDirs = getDirectParentSubfolders(filledDirs, currentPathPrefix)
        updateSubfolderCounts(filledDirs, parentDirs)

        // show the current folder as an available option too, not just subfolders
        if (currentPathPrefix.isNotEmpty()) {
            val currentFolder = allDirs.firstOrNull {
                parentDirs.firstOrNull {
                    it.path.equals(currentPathPrefix, true)
                } == null && it.path.equals(currentPathPrefix, true)
            }

            currentFolder?.apply {
                subfoldersCount = 1
                parentDirs.add(this)
            }
        }

        getSortedDirectories(parentDirs)
    } else {
        dirs.forEach { it.subfoldersMediaCount = it.mediaCnt }
        dirs
    }
}

private fun Context.addParentWithoutMediaFiles(into: ArrayList<Directory>, path: String): Boolean {
    val isSortingAscending = config.directorySorting.isSortingAscending()
    val subDirs = into.filter { File(it.path).parent.equals(path, true) } as ArrayList<Directory>
    val newDirId = max(1000L, into.maxOfOrNull { it.id ?: 0L } ?: 0L)
    if (subDirs.isNotEmpty()) {
        val lastModified = if (isSortingAscending) {
            subDirs.minByOrNull { it.modified }?.modified
        } else {
            subDirs.maxByOrNull { it.modified }?.modified
        } ?: 0

        val dateTaken = if (isSortingAscending) {
            subDirs.minByOrNull { it.taken }?.taken
        } else {
            subDirs.maxByOrNull { it.taken }?.taken
        } ?: 0

        var mediaTypes = 0
        subDirs.forEach {
            mediaTypes = mediaTypes or it.types
        }

        val directory = Directory(
            id = newDirId + 1,
            path = path,
            tmb = subDirs.first().tmb,
            name = getFolderNameFromPath(path),
            mediaCnt = subDirs.sumOf { it.mediaCnt },
            modified = lastModified,
            taken = dateTaken,
            size = subDirs.sumByLong { it.size },
            location = getPathLocation(path),
            types = mediaTypes,
            sortValue = ""
        )

        directory.containsMediaFilesDirectly = false
        into.add(directory)
        return true
    }
    return false
}

fun Context.fillWithSharedDirectParents(dirs: ArrayList<Directory>): ArrayList<Directory> {
    val allDirs = ArrayList<Directory>(dirs)
    val childCounts = mutableMapOf<String, Int>()
    for (dir in dirs) {
        File(dir.path).parent?.let {
            val current = childCounts[it] ?: 0
            childCounts.put(it, current + 1)
        }
    }

    childCounts
        .filter { dir -> dir.value > 1 && dirs.none { it.path.equals(dir.key, true) } }
        .toList()
        .sortedByDescending { it.first.length }
        .forEach { (parent, _) ->
            addParentWithoutMediaFiles(allDirs, parent)
        }
    return allDirs
}

fun Context.getDirectParentSubfolders(
    dirs: ArrayList<Directory>,
    currentPathPrefix: String
): ArrayList<Directory> {
    val folders = dirs.map { it.path }.sorted().toMutableSet() as HashSet<String>
    val currentPaths = LinkedHashSet<String>()
    val foldersWithoutMediaFiles = ArrayList<String>()

    for (path in folders) {
        if (path == RECYCLE_BIN || path == FAVORITES || path.isVirtualAlbumPath()) {
            continue
        }

        if (currentPathPrefix.isNotEmpty()) {
            if (!path.startsWith(currentPathPrefix, true)) {
                continue
            }

            if (!File(path).parent.equals(currentPathPrefix, true)) {
                continue
            }
        }

        if (
            currentPathPrefix.isNotEmpty() &&
            path.equals(currentPathPrefix, true)
            || File(path).parent.equals(currentPathPrefix, true)
        ) {
            currentPaths.add(path)
        } else if (
            folders.any {
                !it.equals(path, true) && (File(path).parent.equals(it, true)
                        || File(it).parent.equals(File(path).parent, true))
            }
        ) {
            // if we have folders like
            // /storage/emulated/0/Pictures/Images and
            // /storage/emulated/0/Pictures/Screenshots,
            // but /storage/emulated/0/Pictures is empty, still Pictures with the first folders thumbnails and proper other info
            val parent = File(path).parent
            if (
                parent != null
                && !folders.contains(parent)
                && dirs.none { it.path.equals(parent, true) }
            ) {
                currentPaths.add(parent)
                if (addParentWithoutMediaFiles(dirs, parent)) {
                    foldersWithoutMediaFiles.add(parent)
                }
            }
        } else {
            currentPaths.add(path)
        }
    }

    var areDirectSubfoldersAvailable = false
    currentPaths.forEach {
        val path = it
        currentPaths.forEach {
            if (
                !foldersWithoutMediaFiles.contains(it)
                && !it.equals(path, true)
                && File(it).parent?.equals(path, true) == true
            ) {
                areDirectSubfoldersAvailable = true
            }
        }
    }

    if (currentPathPrefix.isEmpty() && folders.contains(RECYCLE_BIN)) {
        currentPaths.add(RECYCLE_BIN)
    }

    if (currentPathPrefix.isEmpty() && folders.contains(FAVORITES)) {
        currentPaths.add(FAVORITES)
    }

    // virtual albums are not part of the folder hierarchy, always show them at the top level
    if (currentPathPrefix.isEmpty()) {
        folders.filter { it.isVirtualAlbumPath() }.forEach { currentPaths.add(it) }
    }

    if (folders.size == currentPaths.size) {
        return dirs.filter { currentPaths.contains(it.path) } as ArrayList<Directory>
    }

    folders.clear()
    folders.addAll(currentPaths)

    val dirsToShow = dirs.filter { folders.contains(it.path) } as ArrayList<Directory>
    return if (areDirectSubfoldersAvailable) {
        getDirectParentSubfolders(dirsToShow, currentPathPrefix)
    } else {
        dirsToShow
    }
}

fun Context.updateSubfolderCounts(
    children: ArrayList<Directory>,
    parentDirs: ArrayList<Directory>
) {
    for (child in children) {
        var longestSharedPath = ""
        for (parentDir in parentDirs) {
            if (parentDir.path == child.path) {
                longestSharedPath = child.path
                continue
            }

            if (
                child.path.startsWith(parentDir.path, true)
                && parentDir.path.length > longestSharedPath.length
            ) {
                longestSharedPath = parentDir.path
            }
        }

        // make sure we count only the proper direct subfolders, grouped the same way as on the main screen
        parentDirs.firstOrNull { it.path == longestSharedPath }?.apply {
            if (
                path.equals(child.path, true)
                || path.equals(File(child.path).parent, true)
                || children.any { it.path.equals(File(child.path).parent, true) }
            ) {
                if (child.containsMediaFilesDirectly) {
                    subfoldersCount++
                }

                if (path != child.path) {
                    subfoldersMediaCount += child.mediaCnt
                }
            }
        }
    }
}
