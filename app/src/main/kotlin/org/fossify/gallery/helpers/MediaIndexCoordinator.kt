package org.fossify.gallery.helpers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.fossify.commons.helpers.FAVORITES
import java.util.Locale

/**
 * Central coordinator that tracks in-app media mutations (copy, move, delete, virtual album
 * updates, encrypt/decrypt) and foreground/screen-on re-checks so MainActivity and MediaActivity
 * re-index affected folders immediately without waiting for Android MediaStore.
 */
object MediaIndexCoordinator {
    data class DirtyFoldersSnapshot(
        val generation: Long,
        val folders: Set<String>,
    )

    interface Listener {
        fun onMediaIndexMutation(mutationGeneration: Long, dirtyFolders: Set<String>)
        fun onForegroundOrScreenOnCheck(checkGeneration: Long)
    }

    private const val MAX_TRACKED_DIRTY_FOLDERS = 256
    private const val DEFAULT_RECENT_DIRTY_MAX_AGE_MS = 60_000L

    private val lock = Any()
    private var mutationGeneration = 0L
    private var foregroundCheckGeneration = 0L
    private val dirtyFolderGenerations = LinkedHashMap<String, Long>()
    private val dirtyFolderTimestamps = HashMap<String, Long>()
    private val listeners = LinkedHashSet<Listener>()

    private fun elapsedNowMs(): Long =
        runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }

    private fun dispatchToMain(action: () -> Unit) {
        val posted = runCatching {
            val looper = Looper.getMainLooper()
            if (looper != null) {
                if (Looper.myLooper() == looper) {
                    action()
                } else {
                    Handler(looper).post(action)
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!posted) {
            action()
        }
    }

    fun normalizeFolderPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed == FAVORITES || trimmed == RECYCLE_BIN || trimmed.startsWith(VIRTUAL_ALBUM_PREFIX)) {
            return trimmed
        }
        return trimmed.trimEnd('/')
    }

    fun markFoldersDirty(
        folderPaths: Collection<String>,
        includeFavorites: Boolean = false,
        includeRecycleBin: Boolean = false,
    ) {
        val normalized = LinkedHashSet<String>()
        for (raw in folderPaths) {
            val clean = normalizeFolderPath(raw)
            if (clean.isNotEmpty()) {
                normalized.add(clean)
            }
        }
        if (includeFavorites) {
            normalized.add(FAVORITES)
        }
        if (includeRecycleBin) {
            normalized.add(RECYCLE_BIN)
        }
        if (normalized.isEmpty()) {
            return
        }

        val nowMs = elapsedNowMs()
        val newGen: Long
        val snapshotListeners: List<Listener>
        synchronized(lock) {
            mutationGeneration++
            newGen = mutationGeneration
            for (folder in normalized) {
                dirtyFolderGenerations.remove(folder)
                dirtyFolderGenerations[folder] = newGen
                dirtyFolderTimestamps[folder] = nowMs
            }
            while (dirtyFolderGenerations.size > MAX_TRACKED_DIRTY_FOLDERS) {
                val eldestKey = dirtyFolderGenerations.keys.firstOrNull() ?: break
                dirtyFolderGenerations.remove(eldestKey)
                dirtyFolderTimestamps.remove(eldestKey)
            }
            snapshotListeners = listeners.toList()
        }

        if (snapshotListeners.isNotEmpty()) {
            val immutableFolders = normalized.toSet()
            dispatchToMain {
                for (listener in snapshotListeners) {
                    listener.onMediaIndexMutation(newGen, immutableFolders)
                }
            }
        }
    }

    fun markPathsDirty(
        filePaths: Collection<String>,
        destinationFolder: String? = null,
        includeFavorites: Boolean = false,
        includeRecycleBin: Boolean = false,
    ) {
        val folders = LinkedHashSet<String>()
        for (raw in filePaths) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed == FAVORITES || trimmed == RECYCLE_BIN || trimmed.startsWith(VIRTUAL_ALBUM_PREFIX)) {
                folders.add(trimmed)
            } else {
                val clean = trimmed.trimEnd('/')
                val parent = clean.substringBeforeLast('/', "")
                if (parent.isNotEmpty()) {
                    folders.add(parent)
                }
            }
        }
        if (!destinationFolder.isNullOrBlank()) {
            folders.add(destinationFolder)
        }
        markFoldersDirty(
            folderPaths = folders,
            includeFavorites = includeFavorites,
            includeRecycleBin = includeRecycleBin,
        )
    }

    fun getDirtyFoldersSince(sinceGeneration: Long): DirtyFoldersSnapshot {
        synchronized(lock) {
            if (sinceGeneration >= mutationGeneration) {
                return DirtyFoldersSnapshot(mutationGeneration, emptySet())
            }
            val folders = LinkedHashSet<String>()
            for ((folder, gen) in dirtyFolderGenerations) {
                if (gen > sinceGeneration) {
                    folders.add(folder)
                }
            }
            return DirtyFoldersSnapshot(mutationGeneration, folders)
        }
    }

    fun getRecentDirtyPhysicalFolders(
        maxAgeMs: Long = DEFAULT_RECENT_DIRTY_MAX_AGE_MS,
        nowElapsedMs: Long = elapsedNowMs(),
    ): Set<String> {
        synchronized(lock) {
            if (maxAgeMs < 0L) {
                return emptySet()
            }
            val result = LinkedHashSet<String>()
            for ((folder, ts) in dirtyFolderTimestamps) {
                if (folder == FAVORITES || folder == RECYCLE_BIN || folder.startsWith(VIRTUAL_ALBUM_PREFIX)) {
                    continue
                }
                val age = nowElapsedMs - ts
                if (age in 0L..maxAgeMs) {
                    result.add(folder)
                }
            }
            return result
        }
    }

    fun getMutationGeneration(): Long = synchronized(lock) { mutationGeneration }

    fun getForegroundCheckGeneration(): Long = synchronized(lock) { foregroundCheckGeneration }

    fun notifyForegroundOrScreenOn() {
        val newGen: Long
        val snapshotListeners: List<Listener>
        synchronized(lock) {
            foregroundCheckGeneration++
            newGen = foregroundCheckGeneration
            snapshotListeners = listeners.toList()
        }

        if (snapshotListeners.isNotEmpty()) {
            dispatchToMain {
                for (listener in snapshotListeners) {
                    listener.onForegroundOrScreenOnCheck(newGen)
                }
            }
        }
    }

    fun registerListener(listener: Listener) {
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: Listener) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    fun matchesFolderScope(dirtyFolders: Set<String>, currentPath: String, showAll: Boolean): Boolean {
        if (dirtyFolders.isEmpty()) {
            return false
        }
        if (showAll || currentPath.isEmpty()) {
            return true
        }
        val normalizedCurrent = normalizeFolderPath(currentPath)
        if (normalizedCurrent == FAVORITES) {
            return FAVORITES in dirtyFolders
        }
        if (normalizedCurrent == RECYCLE_BIN) {
            return RECYCLE_BIN in dirtyFolders
        }
        val lowerCurrent = normalizedCurrent.lowercase(Locale.ROOT)
        return dirtyFolders.any { normalizeFolderPath(it).lowercase(Locale.ROOT) == lowerCurrent }
    }

    /**
     * Returns true when MainActivity can incrementally re-scan only [changedFolderPaths] instead
     * of launching a full multi-folder directory discovery scan.
     */
    fun canUseTargetedDirectoryRefresh(
        loadedInitialPhotos: Boolean,
        hasDisplayedDirs: Boolean,
        isSecretVaultView: Boolean,
        isSearchOpen: Boolean,
        hasActiveInitialScan: Boolean,
        isWildcardChange: Boolean,
        changedFolderPaths: Collection<String>,
    ): Boolean {
        if (!loadedInitialPhotos ||
            !hasDisplayedDirs ||
            isSecretVaultView ||
            isSearchOpen ||
            hasActiveInitialScan ||
            isWildcardChange ||
            changedFolderPaths.isEmpty()
        ) {
            return false
        }
        return changedFolderPaths.all { raw ->
            val clean = normalizeFolderPath(raw)
            clean.isNotEmpty() &&
                clean != FAVORITES &&
                clean != RECYCLE_BIN &&
                !clean.startsWith(VIRTUAL_ALBUM_PREFIX)
        }
    }

    fun clearVaultPaths(vaultRootPath: String? = null) {
        val normalizedRoot = vaultRootPath?.trimEnd('/')?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        synchronized(lock) {
            val iter = dirtyFolderGenerations.entries.iterator()
            while (iter.hasNext()) {
                val path = iter.next().key
                val lower = path.lowercase(Locale.ROOT)
                val isVaultPath = CryptoManager.isEncryptedFile(path) ||
                    lower.contains(ENCRYPTED_FOLDER_PREFIX) ||
                    lower.contains("/${SECRETS_DIR_NAME.lowercase(Locale.ROOT)}/") ||
                    lower.endsWith("/${SECRETS_DIR_NAME.lowercase(Locale.ROOT)}") ||
                    (normalizedRoot != null && (lower == normalizedRoot || lower.startsWith("$normalizedRoot/")))
                if (isVaultPath) {
                    iter.remove()
                    dirtyFolderTimestamps.remove(path)
                }
            }
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            mutationGeneration = 0L
            foregroundCheckGeneration = 0L
            dirtyFolderGenerations.clear()
            dirtyFolderTimestamps.clear()
            listeners.clear()
        }
    }
}
