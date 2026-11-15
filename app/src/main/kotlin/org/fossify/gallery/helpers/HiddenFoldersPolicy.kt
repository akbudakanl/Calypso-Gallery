package org.fossify.gallery.helpers

import java.util.Locale

/**
 * Pure policy for identifying `.nomedia` / dot-hidden folders on normal storage, excluding
 * Android scoped-storage system directories and encrypted vault paths.
 */
object HiddenFoldersPolicy {
    fun isAndroidRestrictedSubpath(path: String, internalStoragePath: String): Boolean {
        val clean = path.trimEnd('/')
        val root = internalStoragePath.trimEnd('/')
        if (root.isEmpty()) return false
        val androidData = "$root/Android/data"
        val androidObb = "$root/Android/obb"
        return clean.equals(androidData, ignoreCase = true) ||
            clean.startsWith("$androidData/", ignoreCase = true) ||
            clean.equals(androidObb, ignoreCase = true) ||
            clean.startsWith("$androidObb/", ignoreCase = true)
    }

    fun isDotHiddenPath(path: String): Boolean {
        val clean = path.trimEnd('/')
        if (clean.isEmpty()) return false
        return clean.split('/').any { it.startsWith('.') && it != "." && it != ".." }
    }

    /**
     * Reconciles previously known hidden folders with live MediaStore/disk detections.
     * When [canInspectDisk] is false (Android 11+ without All Files Access), previously known
     * hidden folders are preserved so the user can still see them in Manage Hidden Folders.
     */
    fun mergeKnownHiddenFolders(
        cachedKnownHidden: Set<String>,
        liveDetectedHidden: Collection<String>,
        canInspectDisk: Boolean,
        isCipherSystemPath: (String) -> Boolean,
        isRestrictedPath: (String) -> Boolean,
        isStillHiddenOnDisk: (String) -> Boolean
    ): LinkedHashSet<String> {
        val result = LinkedHashSet<String>()
        val seenNormalized = HashSet<String>()

        fun addNormalized(raw: String) {
            val clean = raw.trimEnd('/')
            if (clean.isEmpty() || isCipherSystemPath(clean) || isRestrictedPath(clean)) return
            val key = clean.lowercase(Locale.getDefault())
            if (seenNormalized.add(key)) {
                result.add(clean)
            }
        }

        for (detected in liveDetectedHidden) {
            addNormalized(detected)
        }

        for (cached in cachedKnownHidden) {
            val clean = cached.trimEnd('/')
            if (clean.isEmpty() || isCipherSystemPath(clean) || isRestrictedPath(clean)) continue
            if (!canInspectDisk || isStillHiddenOnDisk(clean)) {
                addNormalized(clean)
            }
        }

        return result
    }
}
