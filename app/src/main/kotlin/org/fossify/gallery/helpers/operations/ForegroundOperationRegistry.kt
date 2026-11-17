package org.fossify.gallery.helpers.operations

import android.content.Context

/**
 * ADR 0003 §7: a single owner token for long-running file operations. Two concurrent operations
 * are serialized so wipe/key decisions cannot race, and startup orphan cleanup never runs while
 * an operation is active.
 */
object ForegroundOperationRegistry {

    @Volatile
    private var currentOwner: String? = null

    val isActive: Boolean get() = currentOwner != null

    @Synchronized
    fun acquire(context: Context, owner: String): Boolean {
        if (currentOwner != null) return false
        currentOwner = owner
        FileOperationService.start(context, owner)
        return true
    }

    @Synchronized
    fun release(context: Context, owner: String) {
        if (currentOwner == owner) {
            currentOwner = null
            FileOperationService.stop(context)
        }
    }

    /** Runs [block] under the registry token, releasing it in every exit path. */
    inline fun <T> withOperation(context: Context, owner: String, block: () -> T): T? {
        if (!acquire(context, owner)) return null
        return try {
            block()
        } finally {
            release(context, owner)
        }
    }
}
