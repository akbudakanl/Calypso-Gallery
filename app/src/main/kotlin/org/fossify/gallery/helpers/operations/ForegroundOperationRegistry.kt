package org.fossify.gallery.helpers.operations

import android.content.Context

/**
 * ADR 0003 §7: a single owner token for long-running file operations. Two concurrent operations
 * on different threads are serialized so wipe/key decisions cannot race, while same-thread nested
 * calls (e.g. a batch loop calling `encryptFolder`) share the outer token cleanly.
 */
object ForegroundOperationRegistry {

    @Volatile
    private var currentOwner: String? = null

    @Volatile
    private var ownerThread: Thread? = null

    private var depth = 0

    @Volatile
    private var cancelRequested = false

    val isActive: Boolean get() = currentOwner != null
    val isCancelled: Boolean get() = cancelRequested

    @Synchronized
    fun acquire(context: Context, owner: String): Boolean {
        val thread = Thread.currentThread()
        if (currentOwner != null) {
            if (ownerThread === thread) {
                depth++
                return true
            }
            return false
        }
        currentOwner = owner
        ownerThread = thread
        depth = 1
        cancelRequested = false
        FileOperationService.start(context, owner)
        return true
    }

    fun requestCancel() {
        cancelRequested = true
    }

    fun updateStatus(context: Context, label: String) {
        if (isActive) {
            FileOperationService.updateStatus(context, label)
        }
    }

    @Synchronized
    fun release(context: Context, owner: String) {
        val thread = Thread.currentThread()
        if (currentOwner != null && ownerThread === thread) {
            if (depth > 1) {
                depth--
                return
            }
            currentOwner = null
            ownerThread = null
            depth = 0
            cancelRequested = false
            FileOperationService.stop(context)
            org.fossify.gallery.helpers.VaultSessionManager.onForegroundOperationEnded(context)
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
