package org.fossify.gallery.helpers.operations

import android.content.Context
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.OperationNotificationCategory

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

    @Volatile
    var onStateChanged: ((isActive: Boolean) -> Unit)? = null

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
        val category = OperationNotificationCategory.fromOwner(owner)
        FileOperationService.start(context, owner, context.config.shouldShowOperationProgress(category))
        runCatching { onStateChanged?.invoke(true) }
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

    /** Called by App on foreground/background transitions to refresh notification detail level. */
    fun onAppVisibilityChanged(context: Context) {
        if (isActive) {
            FileOperationService.refreshVisibility(context)
        }
    }

    /** Publishes progress using the category of the currently registered operation. */
    fun updateProgress(context: Context, current: Int, max: Int, text: String?) {
        val owner = currentOwner ?: return
        updateProgress(context, OperationNotificationCategory.fromOwner(owner), current, max, text)
    }

    /** Publishes progress for an explicit category (used by the pre-flight check dialog). */
    fun updateProgress(context: Context, category: String, current: Int, max: Int, text: String?) {
        if (!isActive || !context.config.shouldShowOperationProgress(category)) return
        FileOperationService.updateProgress(context, current, max, text)
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
            runCatching { onStateChanged?.invoke(false) }
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
