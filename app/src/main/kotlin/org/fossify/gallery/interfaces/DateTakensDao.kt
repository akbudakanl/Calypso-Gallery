package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.DateTaken

@Dao
interface DateTakensDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(dateTakens: List<DateTaken>)

    @Query("SELECT full_path, filename, parent_path, date_taken, last_fixed, last_modified FROM date_takens WHERE full_path = :path COLLATE NOCASE LIMIT 1")
    fun getDateTakenFromPath(path: String): DateTaken?

    @Query("UPDATE date_takens SET full_path = :newPath, filename = :newFilename, parent_path = :newParentPath WHERE full_path = :oldPath COLLATE NOCASE")
    fun updateDateTakenPath(oldPath: String, newParentPath: String, newFilename: String, newPath: String)

    @Query("SELECT full_path, filename, parent_path, date_taken, last_fixed, last_modified FROM date_takens WHERE parent_path = :path COLLATE NOCASE")
    fun getDateTakensFromPath(path: String): List<DateTaken>

    @Query("DELETE FROM date_takens WHERE full_path = :path COLLATE NOCASE")
    fun deleteDateTaken(path: String)

    @Query("DELETE FROM date_takens WHERE parent_path = :path COLLATE NOCASE")
    fun deleteDateTakensFromPath(path: String)

    @Query("DELETE FROM date_takens WHERE full_path LIKE :pathPrefix || '%' COLLATE NOCASE")
    fun deleteDateTakensByPrefix(pathPrefix: String)

    @Query("SELECT full_path, filename, parent_path, date_taken, last_fixed, last_modified FROM date_takens")
    fun getAllDateTakens(): List<DateTaken>
}
