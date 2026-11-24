package org.fossify.gallery.helpers

import kotlin.math.abs
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection

/**
 * Pure helpers for diffing media/directory adapter datasets and keeping multi-selection intact
 * across background refreshes.
 */
object MediaUpdatePolicy {

    /**
     * Computes a content hash of [items] ignoring transient/persistence-only fields such as
     * Room's auto-generated [Medium.id] and layout [Medium.gridPosition], so transitioning from
     * cached DB items to freshly scanned [Medium] items with identical content does not trigger
     * a redundant adapter reset.
     */
    fun computeMediaContentHash(items: List<ThumbnailItem>): Int {
        var result = 1
        for (item in items) {
            val itemHash = when (item) {
                is Medium -> {
                    var h = item.name.hashCode()
                    h = 31 * h + item.path.hashCode()
                    h = 31 * h + item.parentPath.hashCode()
                    h = 31 * h + item.modified.hashCode()
                    h = 31 * h + item.taken.hashCode()
                    h = 31 * h + item.size.hashCode()
                    h = 31 * h + item.type
                    h = 31 * h + item.videoDuration
                    h = 31 * h + item.isFavorite.hashCode()
                    h = 31 * h + item.deletedTS.hashCode()
                    h = 31 * h + item.mediaStoreId.hashCode()
                    h
                }

                else -> item.hashCode()
            }
            result = 31 * result + itemHash
        }
        return result
    }

    /**
     * Computes a content hash of [items] for viewer refreshes, ignoring [Medium.id],
     * [Medium.gridPosition], [Medium.mediaStoreId], and [Medium.taken].
     */
    fun computeViewerMediaContentHash(items: List<Medium>): Int {
        var result = 1
        for (item in items) {
            var h = item.name.hashCode()
            h = 31 * h + item.path.hashCode()
            h = 31 * h + item.parentPath.hashCode()
            h = 31 * h + item.modified.hashCode()
            h = 31 * h + item.size.hashCode()
            h = 31 * h + item.type
            h = 31 * h + item.videoDuration
            h = 31 * h + item.isFavorite.hashCode()
            h = 31 * h + item.deletedTS.hashCode()
            result = 31 * result + h
        }
        return result
    }

    /**
     * Merges non-zero cached metadata from [cachedItems] into [freshItems] when the live scan
     * skipped fetching a field (e.g. modified, taken, size, videoDuration, mediaStoreId == 0),
     * and reconciles sub-second millisecond-vs-second timestamp drift.
     */
    fun mergeCachedMediaMetadata(freshItems: List<ThumbnailItem>, cachedItems: List<ThumbnailItem>) {
        if (freshItems.isEmpty() || cachedItems.isEmpty()) {
            return
        }

        val cachedByPath = HashMap<String, Medium>(cachedItems.size)
        for (item in cachedItems) {
            if (item is Medium) {
                cachedByPath[item.path] = item
            }
        }

        for (item in freshItems) {
            if (item is Medium) {
                val cached = cachedByPath[item.path] ?: continue
                val previousModified = item.modified
                if (item.modified == 0L && cached.modified != 0L) {
                    item.modified = cached.modified
                } else if (item.modified > 0L &&
                    cached.modified > 0L &&
                    abs(item.modified - cached.modified) < 1000L &&
                    (cached.modified % 1000L == 0L || item.modified % 1000L == 0L)
                ) {
                    item.modified = cached.modified
                }
                if (cached.taken != 0L) {
                    val hasTakenDrift = item.taken > 0L &&
                        abs(item.taken - cached.taken) < 1000L &&
                        (cached.taken % 1000L == 0L || item.taken % 1000L == 0L)
                    val hadModifiedFallback = item.taken == previousModified && cached.taken != cached.modified
                    if (item.taken == 0L || hadModifiedFallback || hasTakenDrift) {
                        item.taken = cached.taken
                    }
                }
                if (item.size == 0L && cached.size != 0L) {
                    item.size = cached.size
                }
                if (item.videoDuration == 0 && cached.videoDuration != 0) {
                    item.videoDuration = cached.videoDuration
                }
                if (item.mediaStoreId == 0L && cached.mediaStoreId != 0L) {
                    item.mediaStoreId = cached.mediaStoreId
                }
            }
        }
    }

    /**
     * Returns true when [oldItem] and [newItem] render the exact same thumbnail bitmap.
     */
    fun hasSameVisualThumbnail(oldItem: Medium, newItem: Medium): Boolean {
        return oldItem.path == newItem.path &&
            oldItem.type == newItem.type &&
            oldItem.size == newItem.size &&
            oldItem.modified == newItem.modified
    }

    /**
     * Returns true when [oldDir] and [newDir] have identical visual directory display fields.
     */
    fun hasSameVisualDirectory(oldDir: Directory, newDir: Directory): Boolean {
        return oldDir.tmb == newDir.tmb &&
            oldDir.name == newDir.name &&
            oldDir.mediaCnt == newDir.mediaCnt &&
            oldDir.subfoldersCount == newDir.subfoldersCount &&
            oldDir.subfoldersMediaCount == newDir.subfoldersMediaCount &&
            oldDir.location == newDir.location &&
            oldDir.types == newDir.types
    }

    /**
     * Returns true when [oldItems] and [newItems] represent the exact same sequence of displayable
     * media paths/names/favorite flags and section headers.
     */
    fun haveSameMediaPaths(oldItems: List<ThumbnailItem>, newItems: List<ThumbnailItem>): Boolean {
        if (oldItems.size != newItems.size) {
            return false
        }

        for (i in oldItems.indices) {
            val oldItem = oldItems[i]
            val newItem = newItems[i]
            when {
                oldItem is Medium && newItem is Medium -> {
                    if (oldItem.path != newItem.path ||
                        oldItem.name != newItem.name ||
                        oldItem.isFavorite != newItem.isFavorite
                    ) {
                        return false
                    }
                }

                oldItem is ThumbnailSection && newItem is ThumbnailSection -> {
                    if (oldItem.title != newItem.title) {
                        return false
                    }
                }

                else -> return false
            }
        }
        return true
    }

    /**
     * Computes a deterministic content hash over [dirs] using display-relevant fields,
     * intentionally ignoring Room's auto-generated [Directory.id] as well as [Directory.modified],
     * [Directory.taken], and [Directory.sortValue] (since ordering is captured by list sequence).
     */
    fun computeDirectoryContentHash(dirs: List<Directory>): Int {
        var result = 1
        for (dir in dirs) {
            var h = dir.path.hashCode()
            h = 31 * h + dir.tmb.hashCode()
            h = 31 * h + dir.name.hashCode()
            h = 31 * h + dir.mediaCnt
            h = 31 * h + dir.size.hashCode()
            h = 31 * h + dir.location
            h = 31 * h + dir.types
            h = 31 * h + dir.subfoldersCount
            h = 31 * h + dir.subfoldersMediaCount
            result = 31 * result + h
        }
        return result
    }

    /**
     * Returns true when [oldDirs] and [newDirs] have the exact same ordered sequence of directory paths.
     */
    fun haveSameDirectoryPaths(oldDirs: List<Directory>, newDirs: List<Directory>): Boolean {
        if (oldDirs.size != newDirs.size) {
            return false
        }

        for (i in oldDirs.indices) {
            if (oldDirs[i].path != newDirs[i].path) {
                return false
            }
        }
        return true
    }

    /**
     * Removes any key from [selectedKeys] that no longer exists among [items].
     */
    fun reconcileSelectedKeys(selectedKeys: MutableSet<Int>, items: List<ThumbnailItem>) {
        if (selectedKeys.isEmpty()) {
            return
        }

        val validKeys = HashSet<Int>(items.size)
        for (item in items) {
            if (item is Medium) {
                validKeys.add(item.path.hashCode())
            }
        }
        selectedKeys.retainAll(validKeys)
    }

    /**
     * Removes any key from [selectedKeys] that no longer exists among [dirs].
     */
    fun reconcileSelectedDirectoryKeys(selectedKeys: MutableSet<Int>, dirs: List<Directory>) {
        if (selectedKeys.isEmpty()) {
            return
        }

        val validKeys = HashSet<Int>(dirs.size)
        for (dir in dirs) {
            validKeys.add(dir.path.hashCode())
        }
        selectedKeys.retainAll(validKeys)
    }

    /**
     * Returns true when [freshItems] and [cachedItems] have identical counts and matching
     * persisted metadata for every path.
     */
    fun isFolderMediaCacheInSync(freshItems: List<Medium>, cachedItems: List<Medium>): Boolean {
        if (freshItems.size != cachedItems.size) {
            return false
        }

        val cachedByPath = HashMap<String, Medium>(cachedItems.size)
        for (cached in cachedItems) {
            cachedByPath[cached.path] = cached
        }

        for (fresh in freshItems) {
            val cached = cachedByPath[fresh.path] ?: return false
            if (cached.name != fresh.name ||
                cached.modified != fresh.modified ||
                cached.taken != fresh.taken ||
                cached.size != fresh.size ||
                cached.type != fresh.type ||
                cached.videoDuration != fresh.videoDuration ||
                cached.isFavorite != fresh.isFavorite
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Returns indices in [oldItems]/[newItems] (assuming [haveSameMediaPaths] is true) whose
     * visual display fields changed.
     */
    fun findVisualChangedMediaIndices(oldItems: List<ThumbnailItem>, newItems: List<ThumbnailItem>): List<Int> {
        val changedIndices = ArrayList<Int>()
        for (i in oldItems.indices) {
            val oldItem = oldItems[i]
            val newItem = newItems.getOrNull(i) ?: break
            if (oldItem is Medium && newItem is Medium) {
                if (oldItem.modified != newItem.modified ||
                    oldItem.size != newItem.size ||
                    oldItem.videoDuration != newItem.videoDuration ||
                    oldItem.isFavorite != newItem.isFavorite ||
                    oldItem.name != newItem.name ||
                    oldItem.type != newItem.type
                ) {
                    changedIndices.add(i)
                }
            }
        }
        return changedIndices
    }

    /**
     * Returns indices in [oldDirs]/[newDirs] (assuming [haveSameDirectoryPaths] is true) whose
     * visual display fields changed.
     */
    fun findVisualChangedDirectoryIndices(oldDirs: List<Directory>, newDirs: List<Directory>): List<Int> {
        val changedIndices = ArrayList<Int>()
        for (i in oldDirs.indices) {
            val oldDir = oldDirs[i]
            val newDir = newDirs.getOrNull(i) ?: break
            if (oldDir.tmb != newDir.tmb ||
                oldDir.name != newDir.name ||
                oldDir.mediaCnt != newDir.mediaCnt ||
                oldDir.subfoldersCount != newDir.subfoldersCount ||
                oldDir.subfoldersMediaCount != newDir.subfoldersMediaCount ||
                oldDir.location != newDir.location ||
                oldDir.types != newDir.types
            ) {
                changedIndices.add(i)
            }
        }
        return changedIndices
    }
}
