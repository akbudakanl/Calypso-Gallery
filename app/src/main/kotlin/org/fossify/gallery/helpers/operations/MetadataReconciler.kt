package org.fossify.gallery.helpers.operations

import android.content.Context
import org.fossify.commons.extensions.rescanPaths
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateReferenceIdentitiesForPath
import org.fossify.gallery.extensions.virtualAlbumsDB
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import java.io.File

/**
 * ADR 0003 §9/6 — idempotent reconciliation of the stores that live *outside* the journal.
 *
 * A committed file can be correct on disk while MediaStore, Room (medium, date_takens, favorites,
 * virtual albums) and the JSON manifests still reference the old source path — for example when
 * the process died after the atomic rename but before the DB/manifest updates completed.
 *
 * Every operation is processed at most once, tracked in app-private SharedPreferences. The
 * underlying update helpers are UPDATE-by-old-path, so re-running never duplicates rows.
 */
object MetadataReconciler {

    private const val PREFS_NAME = "cipher_metadata_reconciliation"
    // v2 tracks individual items: a second process death during resume must be able to reconcile
    // items that completed after the first pass (the v1 operation-level marker could not).
    private const val KEY_DONE = "reconciled_items_v2"

    data class Result(val operations: Int, val items: Int)

    val completedStates = setOf(
        OperationJournal.STATE_VERIFIED,
        OperationJournal.STATE_SOURCE_DELETED
    )

    fun reconcile(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val done = prefs.getStringSet(KEY_DONE, emptySet())?.toMutableSet() ?: mutableSetOf()

        var operations = 0
        var items = 0
        val touchedOperations = HashSet<String>()

        OperationJournal.allItems()
            .filter { it.state in completedStates && it.sourcePath != it.destPath }
            .forEach { item ->
                if (item.operationId.isBlank()) return@forEach
                val itemKey = "${item.operationId}:${item.itemIndex}"
                if (itemKey in done) return@forEach

                val dest = File(item.destPath)
                if (!dest.exists()) return@forEach

                if (item.kind == OperationJournal.KIND_COPY) {
                    // A copy leaves the source intact: only the new destination needs an identity.
                    runCatching { context.updateReferenceIdentitiesForPath(item.destPath) }
                } else {
                    // Move/encrypt/decrypt migrated the path: update media, dates, favorites,
                    // virtual albums and save the affected manifests in one existing helper.
                    runCatching { context.updateDBMediaPath(item.sourcePath, item.destPath) }
                }
                fixAlbumParents(context, item.sourcePath, item.destPath)

                if (!dest.name.endsWith(".bin")) {
                    runCatching { context.rescanPaths(arrayListOf(item.destPath)) {} }
                }
                done.add(itemKey)
                items++
                touchedOperations.add(item.operationId)
            }

        val activeOperationIds = OperationJournal.operationIds().toHashSet()
        val prunedCount = done.count { key -> key.substringBefore(':') !in activeOperationIds }
        if (prunedCount > 0) {
            done.removeAll { key -> key.substringBefore(':') !in activeOperationIds }
        }
        operations = touchedOperations.size
        if (items > 0 || prunedCount > 0) {
            prefs.edit().putStringSet(KEY_DONE, done).apply()
            // Favorites JSON may have been rewritten by updateDBMediaPath.
            if (items > 0) {
                runCatching { ReferenceBackupManager.saveFavorites(context) }
            }
        }
        return Result(operations, items)
    }

    /** The album's own parent path can stay stale even when all of its items were migrated. */
    private fun fixAlbumParents(context: Context, sourcePath: String, destPath: String) {
        val oldParent = File(sourcePath).parent ?: return
        val newParent = File(destPath).parent ?: return
        if (oldParent == newParent) return

        runCatching {
            context.virtualAlbumsDB.getAlbumIdsWithItem(destPath).forEach { albumId ->
                val album = context.virtualAlbumsDB.getAlbumById(albumId) ?: return@forEach
                if (album.parentPhysicalPath == oldParent) {
                    context.virtualAlbumsDB.updateAlbumParentPhysicalPath(albumId, newParent)
                    VirtualAlbumSyncManager.saveAlbumToFile(context, albumId)
                }
            }
        }
    }
}
