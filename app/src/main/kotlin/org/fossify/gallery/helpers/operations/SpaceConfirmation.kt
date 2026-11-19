package org.fossify.gallery.helpers.operations

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.views.MyTextView
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
        showConfirmationDialog(
            context = context,
            text = message(context, probe, estimate),
            onApproved = onApproved,
            onRejected = onRejected
        )
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
            showConfirmationDialog(
                context = context,
                text = message(context, probe, estimate),
                onApproved = {
                    approved = true
                    latch.countDown()
                },
                onRejected = { latch.countDown() }
            )
        }
        runCatching { latch.await(5, TimeUnit.MINUTES) }
        return approved
    }

    private fun showConfirmationDialog(
        context: Context,
        text: String,
        onApproved: () -> Unit,
        onRejected: () -> Unit
    ) {
        val activity = context as? Activity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            val padding = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            val messageView = MyTextView(activity).apply {
                setPadding(padding, padding, padding, 0)
                this.text = text
            }
            activity.getAlertDialogBuilder()
                .setPositiveButton(R.string.space_continue_anyway) { _, _ -> onApproved() }
                .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> onRejected() }
                .setOnCancelListener { onRejected() }
                .apply {
                    activity.setupDialogStuff(messageView, this, titleId = R.string.space_unverified_title)
                }
        } else {
            AlertDialog.Builder(context)
                .setTitle(R.string.space_unverified_title)
                .setMessage(text)
                .setPositiveButton(R.string.space_continue_anyway) { _, _ -> onApproved() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> onRejected() }
                .setOnCancelListener { onRejected() }
                .show()
        }
    }

    private fun message(context: Context, probe: SpaceProbe, estimate: SpaceEstimate): String =
        context.getString(
            R.string.space_unverified_warning,
            estimate.peakBytes.formatSize(),
            probe.reason
        )
}
