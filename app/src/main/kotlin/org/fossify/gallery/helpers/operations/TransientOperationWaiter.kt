package org.fossify.gallery.helpers.operations

import android.content.Context
import org.fossify.gallery.R
import java.io.File

/**
 * ADR 0003 §2 / §9/6 — transient-wait layer while the process is alive.
 *
 * When a destination directory disappears mid-operation (e.g. an external SD card or USB OTG
 * volume is temporarily unmounted) or verifiable free space temporarily drops below the item's
 * requirement + margin, this helper keeps the foreground service and vault keys alive, updates
 * the status notification, and polls until the volume/space returns or the user cancels.
 */
object TransientOperationWaiter {

    private const val DEFAULT_POLL_INTERVAL_MS = 1000L
    private const val DEFAULT_MAX_WAIT_MS = 120_000L

    /**
     * Checks whether [destDir] is ready for writing [requiredBytes]. If the destination root
     * disappeared mid-batch or verifiable free space dropped below threshold, polls until it
     * recovers, the operation is cancelled, or [maxWaitMs] elapses.
     *
     * Returns `true` if ready to proceed, `false` if cancelled or timed out.
     */
    fun awaitDestinationReady(
        context: Context,
        ctx: OperationContext,
        destDir: File,
        requiredBytes: Long,
        maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        onStatus: ((String) -> Unit)? = null
    ): Boolean {
        if (ForegroundOperationRegistry.isCancelled) {
            OperationJournal.markFailed(ctx, "cancelled")
            return false
        }
        if (isDestinationReady(destDir, requiredBytes)) return true

        // Only enter the polling loop when a live foreground operation owns this thread and
        // waiting is enabled; otherwise fail fast rather than blocking callers or unit tests.
        if (!ForegroundOperationRegistry.isActive || maxWaitMs <= 0L) {
            OperationJournal.markFailed(ctx, "destination unavailable or insufficient space")
            return false
        }

        val waitLabel = runCatching { context.getString(R.string.resume_operations_title) }
            .getOrDefault("Waiting for storage")
        OperationJournal.markWaitingForSpace(ctx, waitLabel)
        ForegroundOperationRegistry.updateStatus(context, waitLabel)
        onStatus?.invoke(waitLabel)

        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            if (ForegroundOperationRegistry.isCancelled) {
                OperationJournal.markFailed(ctx, "cancelled while waiting for storage")
                return false
            }
            if (isDestinationReady(destDir, requiredBytes)) {
                return true
            }
            try {
                Thread.sleep(pollIntervalMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }

        OperationJournal.markSpaceCheckFailed(ctx, "timed out waiting for storage or free space")
        return false
    }

    private fun isDestinationReady(destDir: File, requiredBytes: Long): Boolean {
        if (!destDir.exists() && !destDir.mkdirs()) return false
        if (!destDir.canWrite()) return false
        if (requiredBytes <= 0L) return true
        val probe = SpaceEstimator.probe(destDir.absolutePath)
        return !probe.verifiable || SpaceEstimator.hasEnough(probe.availableBytes, requiredBytes)
    }
}
