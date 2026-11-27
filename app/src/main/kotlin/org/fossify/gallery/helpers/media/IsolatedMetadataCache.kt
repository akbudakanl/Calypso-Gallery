package org.fossify.gallery.helpers.media

import java.io.File

internal data class CachedMediaMeta(
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val isAnimated: Boolean? = null,
)

/**
 * Separately keyed in-memory LRU metadata cache for public vs encrypted vault media.
 * Vault entries are purged immediately on vault lock via [clearVault].
 */
internal object IsolatedMetadataCache {
    private const val MAX_PUBLIC_ENTRIES = 256
    private const val MAX_VAULT_ENTRIES = 128
    private const val MAP_INITIAL_CAPACITY = 32
    private const val MAP_LOAD_FACTOR = 0.75f

    private val publicLock = Any()
    private val vaultLock = Any()

    private val publicCache = createLruMap(MAX_PUBLIC_ENTRIES)
    private val vaultCache = createLruMap(MAX_VAULT_ENTRIES)

    fun get(file: File, isVault: Boolean): CachedMediaMeta? {
        val key = fileKey(file)
        return if (isVault) {
            synchronized(vaultLock) { vaultCache[key] }
        } else {
            synchronized(publicLock) { publicCache[key] }
        }
    }

    fun putBounds(
        file: File,
        isVault: Boolean,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ) {
        if (width <= 0 || height <= 0) return
        val key = fileKey(file)
        val lock = if (isVault) vaultLock else publicLock
        val map = if (isVault) vaultCache else publicCache
        synchronized(lock) {
            val existing = map[key]
            map[key] = CachedMediaMeta(
                width = width,
                height = height,
                rotationDegrees = rotationDegrees,
                isAnimated = existing?.isAnimated,
            )
        }
    }

    fun putAnimatedHint(file: File, isVault: Boolean, isAnimated: Boolean) {
        val key = fileKey(file)
        val lock = if (isVault) vaultLock else publicLock
        val map = if (isVault) vaultCache else publicCache
        synchronized(lock) {
            val existing = map[key]
            map[key] = CachedMediaMeta(
                width = existing?.width ?: 0,
                height = existing?.height ?: 0,
                rotationDegrees = existing?.rotationDegrees ?: 0,
                isAnimated = isAnimated,
            )
        }
    }

    fun clearVault() {
        synchronized(vaultLock) {
            vaultCache.clear()
        }
    }

    private fun fileKey(file: File): String {
        return "${file.absolutePath}|${file.lastModified()}|${file.length()}"
    }

    private fun createLruMap(maxEntries: Int): LinkedHashMap<String, CachedMediaMeta> {
        return object : LinkedHashMap<String, CachedMediaMeta>(
            MAP_INITIAL_CAPACITY,
            MAP_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMediaMeta>?): Boolean {
                return size > maxEntries
            }
        }
    }
}
