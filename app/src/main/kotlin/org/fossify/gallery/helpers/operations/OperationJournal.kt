package org.fossify.gallery.helpers.operations

import android.content.Context
import com.google.gson.Gson
import org.fossify.gallery.databases.PendingOperationsDatabase
import org.fossify.gallery.models.PendingOperation

/**
 * Thin API over the ADR 0003 `pending_operations` journal.
 *
 * Every method is a no-op when the operation is not journalled or the journal has not been
 * initialised, so existing call sites can pass [OperationContext.NONE] unchanged.
 */
object OperationJournal {

    const val STATE_PENDING = "PENDING"
    const val STATE_IN_FLIGHT = "IN_FLIGHT"
    const val STATE_COPIED_UNVERIFIED = "COPIED_UNVERIFIED"
    const val STATE_VERIFIED = "VERIFIED"
    const val STATE_SOURCE_DELETED = "SOURCE_DELETED"
    const val STATE_FAILED = "FAILED"
    const val STATE_SKIPPED_CONFLICT = "SKIPPED_CONFLICT"
    const val STATE_WAITING_FOR_SPACE = "WAITING_FOR_SPACE"
    const val STATE_SPACE_CHECK_FAILED = "SPACE_CHECK_FAILED"

    const val KIND_COPY = "COPY"
    const val KIND_MOVE = "MOVE"
    const val KIND_ENCRYPT = "ENCRYPT"
    const val KIND_DECRYPT = "DECRYPT"

    private val gson = Gson()

    fun newOperationId(): String = java.util.UUID.randomUUID().toString().replace("-", "")

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val dao
        get() = appContext?.let { PendingOperationsDatabase.getInstance(it).pendingOperationsDao() }

    private fun update(
        ctx: OperationContext,
        transform: (PendingOperation) -> Unit
    ) {
        val operationId = ctx.operationId ?: return
        val current = dao ?: return
        val existing = current.forOperation(operationId).firstOrNull { it.itemIndex == ctx.itemIndex }
            ?: PendingOperation(
                operationId = operationId,
                itemIndex = ctx.itemIndex,
                kind = ctx.kind,
                createdAt = System.currentTimeMillis()
            )
        transform(existing)
        if (existing.state != STATE_PENDING) {
            existing.updatedAt = System.currentTimeMillis()
        }
        runCatching { current.upsert(existing) }
    }

    /** Inserts all PENDING rows of a batch. Call before the first byte is written. */
    fun beginBatch(ctx: OperationContext, items: List<JournalItem>) {
        val operationId = ctx.operationId ?: return
        val current = dao ?: return
        val now = System.currentTimeMillis()
        items.forEachIndexed { index, item ->
            current.upsert(
                PendingOperation(
                    operationId = operationId,
                    itemIndex = if (ctx.itemIndex >= 0) ctx.itemIndex + index else index,
                    kind = item.kind.ifEmpty { ctx.kind },
                    createdAt = now,
                    updatedAt = now,
                    sourcePath = item.sourcePath,
                    destPath = item.destPath,
                    sourceSize = item.sourceSize,
                    sourceFingerprint = item.sourceFingerprint,
                    resolvedDestName = item.resolvedDestName,
                    batchConflictResolution = ctx.batch?.conflictResolution ?: "",
                    albumConflictResolution = ctx.batch?.albumConflictResolution ?: "",
                    batchPayload = ctx.batch?.let { gson.toJson(it) } ?: "",
                    estimatedRequiredBytes = ctx.batch?.estimatedRequiredBytes ?: 0L,
                    peakBytes = ctx.batch?.peakBytes ?: 0L,
                    state = STATE_PENDING
                )
            )
        }
    }

    fun markInFlight(ctx: OperationContext, stagingPath: String) = update(ctx) {
        it.state = STATE_IN_FLIGHT
        it.stagingPath = stagingPath
        it.kind = ctx.kind.ifEmpty { it.kind }
    }

    fun markCopiedUnverified(ctx: OperationContext, destFingerprint: String) = update(ctx) {
        it.state = STATE_COPIED_UNVERIFIED
        it.destFingerprint = destFingerprint
    }

    /** Only reachable after the atomic commit and a successful read-back verification. */
    fun markVerified(ctx: OperationContext, destFingerprint: String) = update(ctx) {
        it.state = STATE_VERIFIED
        it.destFingerprint = destFingerprint
        it.stagingPath = ""
    }

    fun markSourceDeleted(ctx: OperationContext) = update(ctx) {
        it.state = STATE_SOURCE_DELETED
    }

    fun markSkipped(ctx: OperationContext) = update(ctx) {
        it.state = STATE_SKIPPED_CONFLICT
    }

    fun markFailed(ctx: OperationContext, message: String) = update(ctx) {
        it.state = STATE_FAILED
        it.message = message.take(512)
    }

    fun markWaitingForSpace(ctx: OperationContext, message: String) = update(ctx) {
        it.state = STATE_WAITING_FOR_SPACE
        it.message = message.take(512)
    }

    fun markSpaceCheckFailed(ctx: OperationContext, message: String) = update(ctx) {
        it.state = STATE_SPACE_CHECK_FAILED
        it.message = message.take(512)
    }

    /** Reconciliation: an interrupted item is rewritten from its intact source. */
    fun resetToPending(ctx: OperationContext) = update(ctx) {
        it.state = STATE_PENDING
        it.stagingPath = ""
        it.message = ""
    }

    fun allItems(): List<PendingOperation> = dao?.all() ?: emptyList()

    fun activeItems(): List<PendingOperation> = dao?.activeItems() ?: emptyList()

    fun resumableItems(): List<PendingOperation> = dao?.resumableItems() ?: emptyList()

    fun itemsFor(operationId: String): List<PendingOperation> = dao?.forOperation(operationId) ?: emptyList()

    fun operationIds(): List<String> = dao?.operationIds() ?: emptyList()

    fun clear(operationId: String) {
        dao?.deleteOperation(operationId)
    }
}
