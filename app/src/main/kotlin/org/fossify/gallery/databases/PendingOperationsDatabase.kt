package org.fossify.gallery.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.fossify.gallery.interfaces.PendingOperationsDao
import org.fossify.gallery.models.PendingOperation

/**
 * Separate app-private database for ADR 0003 operation journals.
 *
 * Kept separate from gallery.db so the journal can be wiped/reset without a schema migration,
 * and so it is removed together with app data (acceptable: source files remain intact).
 */
@Database(entities = [PendingOperation::class], version = 1, exportSchema = false)
abstract class PendingOperationsDatabase : RoomDatabase() {

    abstract fun pendingOperationsDao(): PendingOperationsDao

    companion object {
        private const val DB_NAME = "pending_operations.db"

        @Volatile
        private var instance: PendingOperationsDatabase? = null

        fun getInstance(context: Context): PendingOperationsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PendingOperationsDatabase::class.java,
                    DB_NAME
                )
                    .setJournalMode(JournalMode.AUTOMATIC)
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }

        fun destroyInstance() {
            if (instance?.isOpen == true) instance?.close()
            instance = null
        }
    }
}
