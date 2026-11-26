package org.fossify.gallery.helpers

import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.ArrayDeque


/**
 * MediaStore batch queries and bounded filesystem size-index builder extracted from [MediaIdentityResolver].
 */
internal object MediaIdentityIndex {

    internal fun findByMediaStoreId(context: Context, mediaStoreId: Long): String? {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        val selection = "${MediaStore.MediaColumns._ID} = ?"
        val args = arrayOf(mediaStoreId.toString())

        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, selection, args, null)
            if (cursor != null && cursor.moveToFirst()) {
                val path = cursor.getString(0)
                if (!path.isNullOrEmpty() && File(path).isFile) path else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    internal fun queryMediaStoreByIds(
        context: Context,
        ids: List<Long>,
        out: MutableMap<Long, String>
    ) {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA)
        for (chunk in ids.chunked(MediaIdentityResolver.MEDIA_STORE_BATCH_SIZE)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.MediaColumns._ID} IN ($placeholders)"
            val args = chunk.map { it.toString() }.toTypedArray()
            try {
                context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val path = cursor.getString(1)
                        if (!path.isNullOrEmpty() && File(path).isFile) {
                            out[id] = path
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    internal fun queryMediaStoreBySingleSize(context: Context, size: Long): List<MediaIdentityResolver.MediaStoreCandidate> {
        val map = HashMap<Long, MutableList<MediaIdentityResolver.MediaStoreCandidate>>()
        queryMediaStoreBySizes(context, listOf(size), map)
        return map[size].orEmpty()
    }

    internal fun queryMediaStoreBySizes(
        context: Context,
        sizes: List<Long>,
        out: MutableMap<Long, MutableList<MediaIdentityResolver.MediaStoreCandidate>>
    ) {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        for (chunk in sizes.chunked(MediaIdentityResolver.MEDIA_STORE_BATCH_SIZE)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.MediaColumns.SIZE} IN ($placeholders)"
            val args = chunk.map { it.toString() }.toTypedArray()
            try {
                context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val path = cursor.getString(1) ?: continue
                        val name = cursor.getString(2) ?: File(path).name
                        val size = cursor.getLong(3)
                        val dateAddedSec = cursor.getLong(4)
                        if (path.isNotEmpty() && size > 0L) {
                            out.getOrPut(size) { ArrayList(2) }.add(
                                MediaIdentityResolver.MediaStoreCandidate(id, path, name, dateAddedSec)
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    internal fun buildAndroidFallbackRoots(context: Context, hintPaths: List<String>): List<String> {
        val roots = LinkedHashSet<String>()
        val isVaultHint = hintPaths.any { CryptoManager.isVaultMediaPath(context, it) || CryptoManager.isEncryptedFile(it) }
        if (isVaultHint) {
            CryptoManager.findVaultRoot(context)?.takeIf { it.isDirectory }?.let {
                roots.add(it.absolutePath)
            }
            roots.add(CryptoManager.getCipherRootDirectory().absolutePath)
            return roots.toList()
        }
        try {
            val externalRoot = Environment.getExternalStorageDirectory()
            if (externalRoot != null && externalRoot.isDirectory) {
                listOf(
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DOCUMENTS
                ).forEach { dirName ->
                    val sub = File(externalRoot, dirName)
                    if (sub.isDirectory) {
                        roots.add(sub.absolutePath)
                    }
                }
                // Include top-level storage roots after standard media directories so physical albums moved
                // into custom folders (or external SD volumes) can still be recovered.
                roots.add(externalRoot.absolutePath)
            }
        } catch (_: Exception) {
        }
        try {
            CryptoManager.listExternalStorageVolumeRoots(context).forEach { volumeRoot ->
                if (volumeRoot.isDirectory) {
                    roots.add(volumeRoot.absolutePath)
                }
            }
        } catch (_: Exception) {
        }
        return roots.toList()
    }

    internal fun expandSearchRoots(
        hintPaths: List<String>,
        searchRoots: List<String>,
        extraRoots: List<String>
    ): List<File> {
        val result = ArrayList<File>()
        val seen = HashSet<String>()

        fun addRoot(dir: File?) {
            if (dir == null || !dir.isDirectory) return
            val path = dir.absolutePath
            if (isStorageBoundary(path)) return
            if (seen.add(path.lowercase())) {
                result.add(dir)
            }
        }

        // 1. Direct searchRoots or their nearest existing ancestor (up to 2 levels up when a folder was renamed/moved).
        for (raw in searchRoots) {
            if (raw.isEmpty()) continue
            var dir: File? = File(raw)
            if (dir != null && dir.isDirectory) {
                addRoot(dir)
                continue
            }
            var steps = 0
            while (dir != null && steps < 2) {
                dir = dir.parentFile
                steps++
                if (dir != null && dir.isDirectory) {
                    addRoot(dir)
                    break
                }
            }
        }

        // 2. Hint path parents or nearest existing ancestor.
        for (raw in hintPaths) {
            if (raw.isEmpty()) continue
            var dir: File? = File(raw).parentFile
            var steps = 0
            while (dir != null && steps < 3) {
                if (dir.isDirectory) {
                    addRoot(dir)
                    break
                }
                dir = dir.parentFile
                steps++
            }
        }

        // 3. Broad extra fallback roots (e.g. Vault root, DCIM, Pictures, Documents).
        for (raw in extraRoots) {
            if (raw.isNotEmpty()) {
                addRoot(File(raw))
            }
        }

        return result
    }

    internal fun isStorageBoundary(path: String): Boolean {
        val trimmed = path.trimEnd('/', '\\')
        return trimmed.isEmpty() ||
            trimmed == "/storage" ||
            trimmed == "/storage/emulated" ||
            trimmed == "/mnt"
    }

    internal fun buildSizeIndex(
        roots: List<File>,
        targetSizes: Set<Long>
    ): HashMap<Long, MutableList<File>> {
        val index = HashMap<Long, MutableList<File>>()
        val visitedDirs = HashSet<String>()
        var filesVisited = 0

        val explicitRootPaths = roots.mapTo(HashSet()) { it.absolutePath.lowercase() }
        val queue = ArrayDeque<Pair<File, Int>>()
        for (root in roots) {
            if (root.isDirectory && visitedDirs.add(root.absolutePath.lowercase())) {
                queue.add(root to 0)
            }
        }

        while (queue.isNotEmpty() && filesVisited < MediaIdentityResolver.MAX_FALLBACK_FILES_VISITED) {
            val (folder, depth) = queue.removeFirst()
            if (depth > MediaIdentityResolver.MAX_SCAN_DEPTH) continue

            val children = folder.listFiles() ?: continue
            for (child in children) {
                if (filesVisited >= MediaIdentityResolver.MAX_FALLBACK_FILES_VISITED) break

                if (child.isDirectory) {
                    val lowerName = child.name.lowercase()
                    val isAndroidSystemDir = lowerName == "android" &&
                        (File(child, "data").isDirectory || File(child, "obb").isDirectory)
                    val isForeignVaultContainer = child.absolutePath.lowercase() !in explicitRootPaths &&
                        File(child, ".vault").isFile
                    if (lowerName in MediaIdentityResolver.SKIPPED_DIR_NAMES || isAndroidSystemDir || isForeignVaultContainer) continue
                    if (depth + 1 <= MediaIdentityResolver.MAX_SCAN_DEPTH && visitedDirs.add(child.absolutePath.lowercase())) {
                        queue.add(child to (depth + 1))
                    }
                    continue
                }

                if (!child.isFile) continue
                filesVisited++

                val len = child.length()
                if (len <= 0L) continue
                if (targetSizes.isNotEmpty() && len !in targetSizes) continue

                val bucket = index.getOrPut(len) { ArrayList(2) }
                if (bucket.size < MediaIdentityResolver.MAX_CANDIDATES_PER_SIZE) {
                    bucket.add(child)
                }
            }
        }

        return index
    }

    /**
     * Cheap pre-filter: the size has to match exactly and the modification time has to match.
     * Only used when no fingerprint is available.
     */
    internal fun matchesCheapSignature(file: File, identity: MediaIdentity): Boolean {
        if (identity.size > 0L && file.length() != identity.size) {
            return false
        }

        if (identity.lastModified > 0L &&
            !CryptoManager.areTimestampsEquivalent(file.lastModified(), identity.lastModified)
        ) {
            return false
        }

        return true
    }

    /**
     * Looks for a file that matches the given identity in the given folder.
     */
}
