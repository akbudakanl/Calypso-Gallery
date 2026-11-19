package org.fossify.gallery.helpers

import org.fossify.commons.helpers.NOMEDIA
import java.io.File
import java.util.Locale

/**
 * Pure policy and fast filesystem-discovery helpers ensuring first-launch and permission-upgrade
 * album scans find all visible media folders without blocking MediaStore display.
 */
object InitialFolderDiscoveryPolicy {
    const val SCAN_LEVEL_NONE = 0
    const val SCAN_LEVEL_SCOPED = 1
    const val SCAN_LEVEL_FULL = 2

    private const val DEFAULT_MAX_DEPTH = 8
    private const val DEFAULT_MAX_DURATION_MS = 3_000L
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
     * Returns true when a direct filesystem folder discovery pass should run AFTER Phase 1
     * MediaStore display: only when full filesystem access is available ([SCAN_LEVEL_FULL]), never
     * under scoped storage (where MediaStore already covers everything readable) and never in
     * secret vault view.
     */
    fun shouldRunStorageFolderDiscovery(
        completedLevel: Int,
        currentLevel: Int,
        appRunCount: Int,
        isSecretVaultView: Boolean
    ): Boolean {
        if (isSecretVaultView || currentLevel < SCAN_LEVEL_FULL) {
            return false
        }
        if (completedLevel < SCAN_LEVEL_FULL) {
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
     * Walks storage [roots] (internal storage, SD card) using lightweight [File.list] calls so
     * media files are detected by filename without per-file `stat()` syscalls. Skips `Android/data`
     * and `Android/obb` while still scanning `Android/media`, skips precomputed [excludedPrefixes]
     * and encrypted vault folders, skips dot-hidden and `.nomedia` subtrees unless [includeHidden]
     * is true, and enforces a [maxDurationMs] time budget so discovery can never stall.
     */
    fun discoverMediaFoldersInRoots(
        roots: Collection<File>,
        internalStoragePath: String,
        excludedPrefixes: Collection<String> = emptyList(),
        includeHidden: Boolean = false,
        includePortraits: Boolean = false,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
        isMediaFileName: (String) -> Boolean,
        shouldStop: () -> Boolean = { false }
    ): LinkedHashSet<String> {
        val discovered = LinkedHashSet<String>()
        val visitedPaths = HashSet<String>()
        val internalRootClean = internalStoragePath.trimEnd('/')
        val normalizedExcluded = excludedPrefixes
            .map { it.trimEnd('/').lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        val deadlineNanos = if (maxDurationMs > 0L) {
            System.nanoTime() + maxDurationMs * 1_000_000L
        } else {
            Long.MAX_VALUE
        }

        fun isStopped(): Boolean = shouldStop() || System.nanoTime() > deadlineNanos

        fun isExcludedPath(cleanPath: String): Boolean {
            val lower = cleanPath.lowercase(Locale.ROOT)
            for (prefix in normalizedExcluded) {
                if (lower == prefix || lower.startsWith("$prefix/")) {
                    return true
                }
            }
            return false
        }

        fun walk(dir: File, depth: Int) {
            if (isStopped() || depth > maxDepth) {
                return
            }
            val absPath = dir.absolutePath.trimEnd('/')
            if (absPath.isEmpty()) {
                return
            }
            if (HiddenFoldersPolicy.isAndroidRestrictedSubpath(absPath, internalRootClean)) {
                return
            }
            if (isExcludedPath(absPath)) {
                return
            }
            if (!visitedPaths.add(absPath.lowercase(Locale.ROOT))) {
                return
            }

            if (depth > 0) {
                val dirName = dir.name
                if (dirName.startsWith(ENCRYPTED_FOLDER_PREFIX)) {
                    return
                }
                if (!includeHidden && dirName.startsWith('.')) {
                    return
                }
            }

            val names = try {
                dir.list()
            } catch (_: Exception) {
                null
            } ?: return

            if (depth > 0) {
                for (name in names) {
                    if (name == VAULT_SUBFOLDER_META_FILE) {
                        return
                    }
                    if (!includeHidden && name == NOMEDIA) {
                        return
                    }
                }
            }

            var hasDirectMedia = false
            val isStorageRoot = depth == 0
            for (name in names) {
                if (isStopped()) {
                    return
                }
                if (!includeHidden && name.startsWith('.')) {
                    continue
                }
                if (isMediaFileName(name)) {
                    if (!hasDirectMedia) {
                        val candidate = File(dir, name)
                        if (candidate.isFile) {
                            hasDirectMedia = true
                        }
                    }
                    // Skip stat()/isDirectory on media filenames (thousands of files in Camera/WhatsApp).
                    continue
                }

                if (isStorageRoot && name.equals("Android", ignoreCase = true)) {
                    val androidMedia = File(File(dir, name), "media")
                    if (androidMedia.isDirectory) {
                        walk(androidMedia, depth + 1)
                    }
                    continue
                }

                if (name.startsWith(ENCRYPTED_FOLDER_PREFIX)) {
                    continue
                }

                val child = File(dir, name)
                if (child.isDirectory) {
                    if (!hasDirectMedia && includePortraits && name.startsWith("img_", ignoreCase = true)) {
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
                }
            }

            if (hasDirectMedia) {
                discovered.add(absPath)
            }
        }

        for (root in roots) {
            if (isStopped()) {
                break
            }
            if (root.isDirectory) {
                walk(root, 0)
            }
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
        distinctKey: (String) -> String = { it.trimEnd('/').lowercase(Locale.ROOT) }
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
