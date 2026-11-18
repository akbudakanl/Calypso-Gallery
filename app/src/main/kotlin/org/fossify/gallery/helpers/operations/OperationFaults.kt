package org.fossify.gallery.helpers.operations

import org.fossify.gallery.BuildConfig

/**
 * Debug-only fault injection for ADR 0003 process-death tests.
 *
 * Set the JVM/system property `cipher.failAfterItem` to an item index (optionally
 * `<operationId>:<index>`) to simulate the process dying right after that item commits. The hook
 * is compiled out of release behaviour by the [BuildConfig.DEBUG] guard and never changes
 * production data flow.
 */
object OperationFaults {

    fun abortAfterItem(ctx: OperationContext, itemIndex: Int): Boolean {
        if (!BuildConfig.DEBUG) return false
        val spec = runCatching { System.getProperty("cipher.failAfterItem", "") }.getOrDefault("")
        if (spec.isNullOrBlank()) return false

        val expectedOp = if (':' in spec) spec.substringBefore(':') else null
        val indexPart = if (':' in spec) spec.substringAfter(':') else spec
        val targetIndex = indexPart.toIntOrNull() ?: return false
        if (expectedOp != null && expectedOp != ctx.operationId) return false
        return itemIndex == targetIndex
    }

    fun isSimulatedAbort(error: Throwable): Boolean =
        BuildConfig.DEBUG && error.message?.startsWith("Fault injection: simulated process death") == true
}
