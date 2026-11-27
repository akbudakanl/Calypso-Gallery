package org.fossify.gallery.helpers.media

/**
 * Temporarily quarantines media keys that crashed or timed out the isolated decoder process so
 * gallery scrolling cannot enter a crash loop.
 */
class IsolatedFailureQuarantine(
    private val ttlMs: Long = DEFAULT_QUARANTINE_MS,
    private val maxEntries: Int = MAX_ENTRIES,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val entries = LinkedHashMap<String, Long>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    @Synchronized
    fun recordFailure(key: String) {
        if (key.isBlank()) return
        val now = nowMs()
        pruneExpired(now)
        entries[key] = now + ttlMs
        while (entries.size > maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    @Synchronized
    fun isQuarantined(key: String): Boolean {
        if (key.isBlank()) return false
        val now = nowMs()
        val expiresAt = entries[key] ?: return false
        return if (expiresAt <= now) {
            entries.remove(key)
            false
        } else {
            true
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun pruneExpired(now: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= now) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_QUARANTINE_MS = 60_000L
        const val MAX_ENTRIES = 256
        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
