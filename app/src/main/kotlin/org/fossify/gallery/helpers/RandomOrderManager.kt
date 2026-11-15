package org.fossify.gallery.helpers

import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.util.Locale
import kotlin.random.Random

const val RANDOM_ORDER_EXPIRY_MS = 20 * 60 * 1000L

/**
 * Maintains session-stable random orderings for directory and media views across
 * cache-to-scan updates, screen transitions, and item viewing, expiring only after
 * prolonged backgrounding (>= 20 minutes) or explicit user refresh/shuffle actions.
 */
object RandomOrderManager {
    private val lock = Any()
    private var lastBackgroundedAtMs = 0L
    private var sessionGeneration = 1L
    private val directoryOrders = HashMap<String, ArrayList<String>>()
    private val mediaOrders = HashMap<String, ArrayList<String>>()

    fun normalizeScopeKey(scopeKey: String): String {
        return if (scopeKey.isEmpty() || scopeKey == SHOW_ALL) {
            SHOW_ALL
        } else {
            scopeKey.trimEnd('/').lowercase(Locale.ROOT)
        }
    }

    fun onAppBackgrounded(nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            lastBackgroundedAtMs = nowMs
        }
    }

    fun onAppForegrounded(nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            val expired = lastBackgroundedAtMs > 0L && nowMs - lastBackgroundedAtMs >= RANDOM_ORDER_EXPIRY_MS
            lastBackgroundedAtMs = 0L
            if (expired) {
                directoryOrders.clear()
                mediaOrders.clear()
                sessionGeneration++
            }
            return expired
        }
    }

    fun getSessionGeneration(): Long {
        synchronized(lock) {
            return sessionGeneration
        }
    }

    fun sortDirectoriesRandomly(
        dirs: ArrayList<Directory>,
        scopeKey: String = "default",
        random: Random = Random.Default
    ): ArrayList<Directory> {
        if (dirs.size <= 1) {
            return ArrayList(dirs)
        }

        synchronized(lock) {
            val stored = directoryOrders[scopeKey]
            if (stored == null || stored.isEmpty()) {
                val shuffled = ArrayList(dirs)
                shuffled.shuffle(random)
                directoryOrders[scopeKey] = shuffled.mapTo(ArrayList(shuffled.size)) { it.path }
                return shuffled
            }

            val indexByPath = HashMap<String, Int>(stored.size)
            stored.forEachIndexed { index, path ->
                indexByPath[path] = index
            }

            val known = ArrayList<Directory>(dirs.size)
            val unknown = ArrayList<Directory>()
            for (dir in dirs) {
                if (indexByPath.containsKey(dir.path)) {
                    known.add(dir)
                } else {
                    unknown.add(dir)
                }
            }

            known.sortBy { indexByPath[it.path] }
            if (unknown.isNotEmpty()) {
                unknown.shuffle(random)
                unknown.mapTo(stored) { it.path }
            }

            return ArrayList<Directory>(known.size + unknown.size).apply {
                addAll(known)
                addAll(unknown)
            }
        }
    }

    fun sortMediaRandomly(
        media: ArrayList<Medium>,
        scopeKey: String,
        random: Random = Random.Default
    ) {
        if (media.size <= 1) {
            return
        }

        val key = normalizeScopeKey(scopeKey)
        synchronized(lock) {
            val stored = mediaOrders[key]
            if (stored == null || stored.isEmpty()) {
                media.shuffle(random)
                mediaOrders[key] = media.mapTo(ArrayList(media.size)) { it.path }
                return
            }

            val indexByPath = HashMap<String, Int>(stored.size)
            stored.forEachIndexed { index, path ->
                indexByPath[path] = index
            }

            val known = ArrayList<Medium>(media.size)
            val unknown = ArrayList<Medium>()
            for (medium in media) {
                if (indexByPath.containsKey(medium.path)) {
                    known.add(medium)
                } else {
                    unknown.add(medium)
                }
            }

            known.sortBy { indexByPath[it.path] }
            if (unknown.isNotEmpty()) {
                unknown.shuffle(random)
                unknown.mapTo(stored) { it.path }
            }

            media.clear()
            media.addAll(known)
            media.addAll(unknown)
        }
    }

    fun setMediaOrder(scopeKey: String, orderedPaths: List<String>) {
        val key = normalizeScopeKey(scopeKey)
        synchronized(lock) {
            mediaOrders[key] = ArrayList(orderedPaths.distinct())
        }
    }

    fun onPathRenamed(oldPath: String, newPath: String) {
        if (oldPath == newPath) {
            return
        }

        synchronized(lock) {
            for (order in directoryOrders.values) {
                for (i in order.indices) {
                    if (order[i] == oldPath) {
                        order[i] = newPath
                    }
                }
            }
            for (order in mediaOrders.values) {
                for (i in order.indices) {
                    if (order[i] == oldPath) {
                        order[i] = newPath
                    }
                }
            }
        }
    }

    fun invalidateDirectoryOrder(scopeKey: String? = null) {
        synchronized(lock) {
            if (scopeKey == null) {
                directoryOrders.clear()
            } else {
                directoryOrders.remove(scopeKey)
            }
        }
    }

    fun invalidateMediaScope(scopeKey: String) {
        val key = normalizeScopeKey(scopeKey)
        synchronized(lock) {
            mediaOrders.remove(key)
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            lastBackgroundedAtMs = 0L
            sessionGeneration = 1L
            directoryOrders.clear()
            mediaOrders.clear()
        }
    }
}
