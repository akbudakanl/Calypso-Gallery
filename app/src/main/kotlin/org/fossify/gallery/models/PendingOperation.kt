package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable per-item record of an in-flight file operation (ADR 0003 §3).
 *
 * The journal is app-private and contains paths, sizes and fingerprints only — never plaintext,
 * keys or container payloads. A record survives process death; `SOURCE_DELETED` is reachable only
 * from `VERIFIED`, which is the invariant that prevents data loss.
 */
@Entity(
    tableName = "pending_operations",
    indices = [Index(value = ["operation_id", "item_index"], unique = true)]
)
data class PendingOperation(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    @ColumnInfo(name = "operation_id") var operationId: String = "",
    @ColumnInfo(name = "item_index") var itemIndex: Int = -1,
    @ColumnInfo(name = "kind") var kind: String = "",
    @ColumnInfo(name = "created_at") var createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") var updatedAt: Long = 0L,
    @ColumnInfo(name = "source_path") var sourcePath: String = "",
    @ColumnInfo(name = "dest_path") var destPath: String = "",
    /** Staging file in the same directory as [destPath]; orphaned by a dead process. */
    @ColumnInfo(name = "staging_path") var stagingPath: String = "",
    @ColumnInfo(name = "source_size") var sourceSize: Long = 0L,
    @ColumnInfo(name = "source_fingerprint") var sourceFingerprint: String = "",
    @ColumnInfo(name = "dest_fingerprint") var destFingerprint: String = "",
    @ColumnInfo(name = "batch_conflict_resolution") var batchConflictResolution: String = "",
    @ColumnInfo(name = "resolved_dest_name") var resolvedDestName: String = "",
    @ColumnInfo(name = "album_conflict_resolution") var albumConflictResolution: String = "",
    /** JSON payload shared by the whole batch (conflict decisions, album merge target, ...). */
    @ColumnInfo(name = "batch_payload") var batchPayload: String = "",
    @ColumnInfo(name = "estimated_required_bytes") var estimatedRequiredBytes: Long = 0L,
    @ColumnInfo(name = "peak_bytes") var peakBytes: Long = 0L,
    @ColumnInfo(name = "state") var state: String = "PENDING",
    @ColumnInfo(name = "message") var message: String = ""
)
