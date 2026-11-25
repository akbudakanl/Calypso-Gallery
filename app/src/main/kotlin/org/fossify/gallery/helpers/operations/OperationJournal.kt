package org.fossify.gallery.helpers.operations

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import org.fossify.gallery.databases.PendingOperationsDatabase
import org.fossify.gallery.interfaces.PendingOperationsDao
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
    const val STATE_AWAITING_SKIP_CONFIRM = "AWAITING_SKIP_CONFIRM"
    const val STATE_DELETE_SKIPPED_APPROVED = "DELETE_SKIPPED_APPROVED"

    const val KIND_COPY = "COPY"
    const val KIND_MOVE = "MOVE"
    const val KIND_ENCRYPT = "ENCRYPT"
    const val KIND_DECRYPT = "DECRYPT"

    private val gson = Gson()

    fun newOperationId(): String = java.util.UUID.randomUUID().toString().replace("-", "")

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var daoOverride: PendingOperationsDao? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @VisibleForTesting
    fun initForTest(dao: PendingOperationsDao?) {
        daoOverride = dao
    }

    private val dao
        get() = daoOverride ?: appContext?.let { PendingOperationsDatabase.getInstance(it).pendingOperationsDao() }

    private fun update(
        ctx: OperationContext,
        transform: (PendingOperation) -> Unit
    ) {
        val operationId = ctx.operationId ?: return
        val current = dao ?: return
        val existing = current.findItem(operationId, ctx.itemIndex)
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

    /** Inserts all PENDING rows of a batch in one transaction. Call before the first byte is written. */
    fun beginBatch(ctx: OperationContext, items: List<JournalItem>) {
        val operationId = ctx.operationId ?: return
        if (items.isEmpty()) return
        val current = dao ?: return
        val now = System.currentTimeMillis()
        val startIndex = if (ctx.itemIndex >= 0) ctx.itemIndex else 0
        val batchJson = ctx.batch?.let { gson.toJson(it) } ?: ""
        val defaultConflict = ctx.batch?.conflictResolution ?: ""
        val albumConflict = ctx.batch?.albumConflictResolution ?: ""
        val estimatedBytes = ctx.batch?.estimatedRequiredBytes ?: 0L
        val peakBytes = ctx.batch?.peakBytes ?: 0L

        val rows = items.mapIndexed { index, item ->
            PendingOperation(
                operationId = operationId,
                itemIndex = startIndex + index,
                kind = item.kind.ifEmpty { ctx.kind },
                createdAt = now,
                updatedAt = now,
                sourcePath = item.sourcePath,
                destPath = item.destPath,
                sourceSize = item.sourceSize,
                sourceFingerprint = item.sourceFingerprint,
                resolvedDestName = item.resolvedDestName,
                batchConflictResolution = item.conflictResolution.ifEmpty { defaultConflict },
                albumConflictResolution = albumConflict,
                batchPayload = batchJson,
                estimatedRequiredBytes = estimatedBytes,
                peakBytes = peakBytes,
                state = STATE_PENDING
            )
        }
        runCatching { current.upsertAll(rows) }
    }

    fun markInFlight(
        ctx: OperationContext,
        stagingPath: String,
        resolvedDestName: String = "",
        actualDestPath: String = "",
        sourceFingerprint: String = ""
    ) = update(ctx) {
        it.state = STATE_IN_FLIGHT
        it.stagingPath = stagingPath
        it.kind = ctx.kind.ifEmpty { it.kind }
        if (resolvedDestName.isNotEmpty()) {
            it.resolvedDestName = resolvedDestName
        }
        if (actualDestPath.isNotEmpty()) {
            it.destPath = actualDestPath
        }
        if (sourceFingerprint.isNotEmpty()) {
            it.sourceFingerprint = sourceFingerprint
        }
    }

    fun markCopiedUnverified(ctx: OperationContext, destFingerprint: String) = update(ctx) {
        it.state = STATE_COPIED_UNVERIFIED
        it.destFingerprint = destFingerprint
    }

    /** Only reachable after the atomic commit and a successful read-back verification. */
    fun markVerified(
        ctx: OperationContext,
        destFingerprint: String,
        actualDestPath: String = ""
    ) = update(ctx) {
        it.state = STATE_VERIFIED
        it.destFingerprint = destFingerprint
        if (actualDestPath.isNotEmpty()) {
            it.destPath = actualDestPath
        }
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

    fun decodeBatch(payload: String): BatchOperationInfo? =
        if (payload.isBlank()) null
        else runCatching { gson.fromJson(payload, BatchOperationInfo::class.java) }.getOrNull()

    fun requiresSourceDeletion(item: PendingOperation, batch: BatchOperationInfo?): Boolean = when (item.kind) {
        KIND_MOVE -> true
        KIND_ENCRYPT -> batch?.wipeSource ?: true
        KIND_DECRYPT -> batch?.deleteSource ?: true
        else -> batch?.isMove == true
    }

    fun needsResume(item: PendingOperation, batch: BatchOperationInfo?): Boolean = when (item.state) {
        STATE_PENDING,
        STATE_IN_FLIGHT,
        STATE_COPIED_UNVERIFIED,
        STATE_WAITING_FOR_SPACE -> true
        STATE_VERIFIED -> requiresSourceDeletion(item, batch)
        else -> false
    }

    fun isEncryptPhase2Wipe(item: PendingOperation, batch: BatchOperationInfo?): Boolean =
        item.state == STATE_VERIFIED &&
            item.kind == KIND_ENCRYPT &&
            requiresSourceDeletion(item, batch)

    fun hasDeferredSkippedWork(item: PendingOperation): Boolean =
        item.state == STATE_AWAITING_SKIP_CONFIRM || item.state == STATE_DELETE_SKIPPED_APPROVED

    fun registerSkippedCryptoDecisionBatch(
        isEncrypt: Boolean,
        skippedFiles: List<java.io.File>,
        identicalTargetMap: Map<String, java.io.File>,
        approvedDelete: Boolean,
        forceCleanupEmptyAlbum: Boolean = false
    ): String {
        val current = dao ?: return ""
        if (skippedFiles.isEmpty()) return ""
        val opId = "skipdec_${newOperationId()}"
        val now = System.currentTimeMillis()
        val kind = if (isEncrypt) KIND_ENCRYPT else KIND_DECRYPT
        val state = if (approvedDelete) STATE_DELETE_SKIPPED_APPROVED else STATE_AWAITING_SKIP_CONFIRM
        val batchJson = gson.toJson(
            BatchOperationInfo(
                isMove = true,
                wipeSource = forceCleanupEmptyAlbum,
                deleteSource = true
            )
        )
        val rows = skippedFiles.mapIndexed { index, srcFile ->
            val target = identicalTargetMap[srcFile.absolutePath]
            PendingOperation(
                operationId = opId,
                itemIndex = index,
                kind = kind,
                createdAt = now,
                updatedAt = now,
                sourcePath = srcFile.absolutePath,
                destPath = target?.absolutePath.orEmpty(),
                sourceSize = srcFile.length(),
                batchPayload = batchJson,
                state = state
            )
        }
        runCatching { current.upsertAll(rows) }
        return opId
    }

    fun markSkippedCryptoApproved(operationId: String) {
        if (operationId.isBlank()) return
        val current = dao ?: return
        val rows = current.forOperation(operationId)
        if (rows.isEmpty()) return
        val now = System.currentTimeMillis()
        runCatching {
            current.upsertAll(
                rows.map {
                    if (it.state == STATE_AWAITING_SKIP_CONFIRM) {
                        it.copy(state = STATE_DELETE_SKIPPED_APPROVED, updatedAt = now)
                    } else {
                        it
                    }
                }
            )
        }
    }

    fun allItems(): List<PendingOperation> = dao?.all() ?: emptyList()

    fun activeItems(): List<PendingOperation> = dao?.activeItems() ?: emptyList()

    fun resumableItems(): List<PendingOperation> {
        val raw = dao?.resumableItems() ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        val batchCache = HashMap<String, BatchOperationInfo?>()
        return raw.filter { item ->
            val batch = batchCache.getOrPut(item.operationId) { decodeBatch(item.batchPayload) }
            needsResume(item, batch)
        }
    }

    fun itemsFor(operationId: String): List<PendingOperation> = dao?.forOperation(operationId) ?: emptyList()

    fun operationIds(): List<String> = dao?.operationIds() ?: emptyList()

    fun markSourceAlbumFolded(operationId: String, sourceRoot: String) {
        if (operationId.isBlank() || sourceRoot.isBlank()) return
        val current = dao ?: return
        val rows = current.forOperation(operationId)
        if (rows.isEmpty()) return
        val baseBatch = rows.firstNotNullOfOrNull { decodeBatch(it.batchPayload) } ?: BatchOperationInfo()
        val existing = baseBatch.foldedRoots()
        if (sourceRoot in existing) return
        val updatedBatch = baseBatch.copy(foldedSourceRoots = existing + sourceRoot)
        val batchJson = gson.toJson(updatedBatch)
        val now = System.currentTimeMillis()
        current.upsertAll(rows.map { it.copy(batchPayload = batchJson, updatedAt = now) })
    }

    fun clear(operationId: String) {
        runCatching { dao?.deleteOperation(operationId) }
    }

    /**
     * Clears [operationId] once complete or cancelled, while preserving any `STATE_VERIFIED` item
     * whose source still needs deletion/wiping. Any unstarted (`PENDING`, `IN_FLIGHT`,
     * `COPIED_UNVERIFIED`, `WAITING_FOR_SPACE`) items are marked skipped so deliberate
     * cancellation never triggers a false resume prompt on next launch.
     */
    fun clearAfterCompletionOrCancel(operationId: String) {
        val current = dao ?: return
        val rows = current.forOperation(operationId)
        if (rows.isEmpty()) return
        val batch = rows.firstNotNullOfOrNull { decodeBatch(it.batchPayload) }
        val hasPendingSourceDelete = rows.any {
            it.state == STATE_VERIFIED && requiresSourceDeletion(it, batch)
        }
        if (!hasPendingSourceDelete) {
            clear(operationId)
            return
        }
        val now = System.currentTimeMillis()
        val updated = rows.map { row ->
            when (row.state) {
                STATE_PENDING,
                STATE_IN_FLIGHT,
                STATE_COPIED_UNVERIFIED,
                STATE_WAITING_FOR_SPACE -> row.copy(state = STATE_SKIPPED_CONFLICT, updatedAt = now)
                else -> row
            }
        }
        runCatching { current.upsertAll(updated) }
    }
}
