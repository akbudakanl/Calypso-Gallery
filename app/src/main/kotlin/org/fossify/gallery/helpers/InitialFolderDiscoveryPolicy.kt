package org.fossify.gallery.helpers

import org.fossify.commons.helpers.NOMEDIA
import java.io.File

/**
 * Pure policy and filesystem-discovery helpers ensuring first-launch and permission-upgrade album
 * scans find all visible media folders immediately rather than deferring them to a later run.
 */
object InitialFolderDiscoveryPolicy {
    const val SCAN_LEVEL_NONE = 0
    const val SCAN_LEVEL_SCOPED = 1
    const val SCAN_LEVEL_FULL = 2

    private const val DEFAULT_MAX_DEPTH = 10
    private const val PERIODIC_SCAN_RUN_INTERVAL = 30

    /**
     * Returns the highest storage discovery level reachable with the app's current permissions.
     * Pre-Android 11 devices and Android 11+ devices with All Files Access can inspect the full
     * filesystem ([SCAN_LEVEL_FULL]); Android 11+ scoped storage only sees [SCAN_LEVEL_SCOPED].
     */
    fun currentStorageScanLevel(isRPlus: Boolean, hasAllFilesAccess: Boolean): Int {
        return if (!isRPlus || hasAllFilesAccess) SCAN_LEVEL_FULL else SCAN_LEVEL_SCOPED
    }

    /**
     * Returns true when a direct storage folder discovery pass should run as part of the current
     * album load: on first install, after upgrading from scoped storage to All Files Access, or
     * periodically every [PERIODIC_SCAN_RUN_INTERVAL] runs.
     */
    fun shouldRunStorageFolderDiscovery(
        completedLevel: Int,
        currentLevel: Int,
        appRunCount: Int,
        isSecretVaultView: Boolean
    ): Boolean {
        if (isSecretVaultView) {
            return false
        }
        if (completedLevel < currentLevel) {
            return true
        }
        return appRunCount > 0 && appRunCount % PERIODIC_SCAN_RUN_INTERVAL == 0
    }

    /**
     * Returns true when the app just gained All Files Access while returning from system settings,
     * requiring a full non-incremental refresh of folders, virtual albums, and favorites.
     */
    fun shouldForceFullReloadOnAccessChange(
        previousHasAllFilesAccess: Boolean,
        currentHasAllFilesAccess: Boolean
    ): Boolean {
        return !previousHasAllFilesAccess && currentHasAllFilesAccess
    }

    /**
     * Settings and permission stamp controlling whether cached MediaStore folder signatures can be
     * reused to skip Phase 2 folder re-scans.
     */
    fun computeFolderSignatureStamp(
        filterMedia: Int,
        directorySorting: Int,
        tempFolderPath: String,
        isSecretVaultView: Boolean,
        hasAllFilesAccess: Boolean,
        temporarilyShowExcluded: Boolean,
        temporarilyShowHiddenFolders: Boolean
    ): Int {
        var stamp = filterMedia
        stamp = 31 * stamp + directorySorting
        stamp = 31 * stamp + tempFolderPath.hashCode()
        stamp = 31 * stamp + if (isSecretVaultView) 1 else 0
        stamp = 31 * stamp + if (hasAllFilesAccess) 1 else 0
        stamp = 31 * stamp + if (temporarilyShowExcluded) 1 else 0
        stamp = 31 * stamp + if (temporarilyShowHiddenFolders) 1 else 0
        return stamp
    }

    /**
     * Walks storage [roots] (internal storage, SD card) and returns every directory containing at
     * least one displayable media file. Skips `Android/data` and `Android/obb` while still scanning
     * `Android/media` (used by messaging apps on Android 11+), skips encrypted/vault subtrees, and
     * skips dot-hidden or `.nomedia` subtrees unless [includeHidden] is true.
     */
    fun discoverMediaFoldersInRoots(
        roots: Collection<File>,
        internalStoragePath: String,
        includeHidden: Boolean = false,
        includePortraits: Boolean = false,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        isExcludedSubtree: (File) -> Boolean = { false },
        isMediaFile: (File) -> Boolean,
        shouldStop: () -> Boolean = { false }
    ): LinkedHashSet<String> {
        val discovered = LinkedHashSet<String>()
        val visitedCanonical = HashSet<String>()
        val internalRootClean = internalStoragePath.trimEnd('/')

        fun walk(dir: File, depth: Int) {
            if (shouldStop() || depth > maxDepth) {
                return
            }
            if (!dir.exists() || !dir.isDirectory) {
                return
            }
            val absPath = dir.absolutePath.trimEnd('/')
            if (HiddenFoldersPolicy.isAndroidRestrictedSubpath(absPath, internalRootClean)) {
                return
            }
            if (isExcludedSubtree(dir)) {
                return
            }

            val canonical = try {
                dir.canonicalPath
            } catch (_: Exception) {
                absPath
            }
            if (!visitedCanonical.add(canonical)) {
                return
            }

            if (depth > 0 && !includeHidden) {
                if (dir.name.startsWith('.') || File(dir, NOMEDIA).exists()) {
                    return
                }
            }

            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                null
            } ?: return

            var hasDirectMedia = false
            val isStorageRoot = depth == 0
            for (child in children) {
                if (shouldStop()) {
                    return
                }
                if (child.isDirectory) {
                    if (isStorageRoot && child.name.equals("Android", ignoreCase = true)) {
                        val androidMedia = File(child, "media")
                        if (androidMedia.isDirectory) {
                            walk(androidMedia, depth + 1)
                        }
                        continue
                    }
                    if (!hasDirectMedia && includePortraits && child.name.startsWith("img_", ignoreCase = true)) {
                        val portraitChildren = try {
                            child.list()
                        } catch (_: Exception) {
                            null
                        }
                        if (!portraitChildren.isNullOrEmpty()) {
                            hasDirectMedia = true
                        }
                    }
                    walk(child, depth + 1)
                } else if (!hasDirectMedia && child.isFile) {
                    if ((includeHidden || !child.name.startsWith('.')) && isMediaFile(child)) {
                        hasDirectMedia = true
                    }
                }
            }

            if (hasDirectMedia) {
                discovered.add(dir.absolutePath)
            }
        }

        for (root in roots) {
            if (shouldStop()) {
                break
            }
            walk(root, 0)
        }
        return discovered
    }

    /**
     * Merges [discoveredFolders] into [baseFolders] without adding duplicate folder keys, keeping
     * all [baseFolders] in their original order.
     */
    fun mergeDiscoveredFolders(
        baseFolders: List<String>,
        discoveredFolders: Collection<String>,
        distinctKey: (String) -> String = { it.trimEnd('/').lowercase() }
    ): ArrayList<String> {
        val merged = ArrayList<String>(baseFolders.size + discoveredFolders.size)
        val seenKeys = HashSet<String>(baseFolders.size + discoveredFolders.size)

        for (folder in baseFolders) {
            if (folder.isEmpty()) continue
            if (seenKeys.add(distinctKey(folder))) {
                merged.add(folder)
            }
        }
        for (folder in discoveredFolders) {
            if (folder.isEmpty()) continue
            if (seenKeys.add(distinctKey(folder))) {
                merged.add(folder)
            }
        }
        return merged
    }
}
