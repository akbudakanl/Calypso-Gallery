package org.fossify.gallery.helpers.operations

/**
 * Carries ADR 0003 journal identifiers through the low-level write functions without changing
 * their public signatures (all callers pass [NONE] by default).
 */
data class OperationContext(
    val operationId: String? = null,
    val itemIndex: Int = -1,
    val kind: String = "",
    val batch: BatchOperationInfo? = null
) {
    val isJournalled: Boolean get() = operationId != null && itemIndex >= 0

    companion object {
        val NONE = OperationContext()

        fun forItem(
            operationId: String,
            itemIndex: Int,
            kind: String,
            batch: BatchOperationInfo? = null
        ) = OperationContext(operationId, itemIndex, kind, batch)
    }
}

/** Batch-level decisions and space estimates shared by every item of one operation. */
data class BatchOperationInfo(
    val sourceRoot: String = "",
    val destRoot: String = "",
    val conflictResolution: String = "",
    val albumConflictResolution: String = "",
    val estimatedRequiredBytes: Long = 0L,
    val peakBytes: Long = 0L,
    /** Original move/copy intent, needed to rebuild the operation after process death. */
    val isMove: Boolean = false,
    /** Encrypt: wipe the plaintext source. Decrypt: delete the encrypted source. */
    val wipeSource: Boolean = true,
    val deleteSource: Boolean = true,
    val payload: String = ""
)

/** One item handed to [OperationJournal.beginBatch]. */
data class JournalItem(
    val sourcePath: String,
    val destPath: String,
    val sourceSize: Long = 0L,
    val sourceFingerprint: String = "",
    val resolvedDestName: String = "",
    val kind: String = "",
    val conflictResolution: String = ""
)
