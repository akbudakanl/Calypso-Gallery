package org.fossify.gallery.helpers

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * A stable fingerprint of the MediaStore `files` table.
 *
 * The upstream helpers `getLatestMediaId()` / `getLatestMediaByDateId()` return the `_ID` of a
 * single row ordered by `DATE_TAKEN`. Many files share the same timestamp (often 0), so the row
 * returned for that ORDER BY is not deterministic and a polling loop can report a "change" even
 * when nothing was added, removed or modified. That false positive caused an unnecessary full
 * re-index of the album/file grids shortly after every navigation.
 *
 * Aggregate values are deterministic, so comparing them detects real content changes only.
 */
data class MediaStoreSnapshot(
    val count: Long = 0L,
    val maxId: Long = 0L,
    val maxDateModified: Long = 0L,
    val generation: Long = -1L
) {
    fun differsFrom(other: MediaStoreSnapshot): Boolean {
        return if (generation >= 0L || other.generation >= 0L) {
            generation != other.generation
        } else {
            count != other.count || maxId != other.maxId || maxDateModified != other.maxDateModified
        }
    }
}

fun Context.getMediaStoreSnapshot(): MediaStoreSnapshot {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return try {
            MediaStoreSnapshot(generation = MediaStore.getGeneration(this, MediaStore.VOLUME_EXTERNAL))
        } catch (_: Exception) {
            MediaStoreSnapshot()
        }
    }

    val projection = arrayOf(
        "COUNT(${MediaStore.MediaColumns._ID})",
        "MAX(${MediaStore.MediaColumns._ID})",
        "MAX(${MediaStore.MediaColumns.DATE_MODIFIED})"
    )

    return try {
        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                MediaStoreSnapshot(
                    count = cursor.getLong(0),
                    maxId = cursor.getLong(1),
                    maxDateModified = cursor.getLong(2)
                )
            } else {
                MediaStoreSnapshot()
            }
        } ?: MediaStoreSnapshot()
    } catch (_: Exception) {
        MediaStoreSnapshot()
    }
}
