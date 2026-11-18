package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.PendingOperation

/**
 * Synchronous DAO: file operations already run on background threads and the database is small.
 * No plaintext or key material is ever stored here (ADR 0003 §3).
 */
@Dao
interface PendingOperationsDao {

    @Query("SELECT * FROM pending_operations ORDER BY created_at ASC, item_index ASC")
    fun all(): List<PendingOperation>

    @Query("SELECT * FROM pending_operations WHERE operation_id = :operationId ORDER BY item_index ASC")
    fun forOperation(operationId: String): List<PendingOperation>

    @Query("SELECT * FROM pending_operations WHERE operation_id = :operationId AND item_index = :itemIndex LIMIT 1")
    fun findItem(operationId: String, itemIndex: Int): PendingOperation?

    @Query("SELECT DISTINCT operation_id FROM pending_operations ORDER BY created_at ASC")
    fun operationIds(): List<String>

    @Query("SELECT * FROM pending_operations WHERE state IN ('PENDING','IN_FLIGHT','COPIED_UNVERIFIED','WAITING_FOR_SPACE')")
    fun activeItems(): List<PendingOperation>

    @Query("SELECT * FROM pending_operations WHERE state NOT IN ('SOURCE_DELETED','SKIPPED_CONFLICT','FAILED','SPACE_CHECK_FAILED') ORDER BY operation_id ASC, item_index ASC")
    fun resumableItems(): List<PendingOperation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: PendingOperation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(items: List<PendingOperation>)

    @Query(
        """UPDATE pending_operations
           SET state = :state,
               updated_at = :updatedAt,
               staging_path = :stagingPath,
               source_fingerprint = :sourceFingerprint,
               dest_fingerprint = :destFingerprint,
               message = :message
           WHERE operation_id = :operationId AND item_index = :itemIndex"""
    )
    fun updateState(
        operationId: String,
        itemIndex: Int,
        state: String,
        updatedAt: Long,
        stagingPath: String,
        sourceFingerprint: String,
        destFingerprint: String,
        message: String
    )

    @Query("DELETE FROM pending_operations WHERE operation_id = :operationId")
    fun deleteOperation(operationId: String)

    @Query("DELETE FROM pending_operations")
    fun clear()
}
