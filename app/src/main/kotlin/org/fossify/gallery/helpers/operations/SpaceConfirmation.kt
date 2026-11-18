package org.fossify.gallery.helpers.operations

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.formatSize
import org.fossify.gallery.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ADR 0003 §3a / §10: SAF and OTG paths often cannot report free space. Instead of silently
 * proceeding, the user explicitly confirms that the operation should be attempted.
 */
object SpaceConfirmation {

    /** Non-blocking variant for flows that already have an async callback. */
    fun askAsync(
        context: Context,
        probe: SpaceProbe,
        estimate: SpaceEstimate,
        onApproved: () -> Unit,
        onRejected: () -> Unit
    ) {
        if (probe.verifiable) {
            onApproved()
            return
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.space_unverified_title)
            .setMessage(message(context, probe, estimate))
            .setPositiveButton(R.string.space_continue_anyway) { _, _ -> onApproved() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onRejected() }
            .setOnCancelListener { onRejected() }
            .show()
    }

    /**
     * Blocking variant for synchronous background operations (album encrypt/decrypt). Never
     * blocks the main thread; callers on the main thread receive `false` and can show [askAsync].
     */
    fun confirmBlocking(context: Context, probe: SpaceProbe, estimate: SpaceEstimate): Boolean {
        if (probe.verifiable) return true
        val isMainThread = runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrElse {
            // Pure JVM unit tests stub Looper with RuntimeException("Method ... not mocked").
            return true
        }
        if (isMainThread) return false

        val latch = CountDownLatch(1)
        var approved = false
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle(R.string.space_unverified_title)
                .setMessage(message(context, probe, estimate))
                .setPositiveButton(R.string.space_continue_anyway) { _, _ ->
                    approved = true
                    latch.countDown()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> latch.countDown() }
                .setOnCancelListener { latch.countDown() }
                .show()
        }
        runCatching { latch.await(5, TimeUnit.MINUTES) }
        return approved
    }

    private fun message(context: Context, probe: SpaceProbe, estimate: SpaceEstimate): String =
        context.getString(
            R.string.space_unverified_warning,
            estimate.peakBytes.formatSize(),
            probe.reason
        )
}
