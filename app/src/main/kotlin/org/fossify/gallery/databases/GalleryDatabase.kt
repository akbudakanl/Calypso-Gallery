package org.fossify.gallery.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.fossify.gallery.interfaces.*
import org.fossify.gallery.models.*

@Database(entities = [Directory::class, Medium::class, Widget::class, DateTaken::class, Favorite::class, VirtualAlbum::class, VirtualAlbumItem::class], version = 1)
abstract class GalleryDatabase : RoomDatabase() {

    abstract fun DirectoryDao(): DirectoryDao

    abstract fun MediumDao(): MediumDao

    abstract fun WidgetsDao(): WidgetsDao

    abstract fun DateTakensDao(): DateTakensDao

    abstract fun FavoritesDao(): FavoritesDao

    abstract fun VirtualAlbumsDao(): VirtualAlbumsDao

    companion object {
        private var db: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase {
            if (db == null) {
                synchronized(GalleryDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, GalleryDatabase::class.java, "gallery.db")
                            .setJournalMode(JournalMode.AUTOMATIC)
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            if (db?.isOpen == true) {
                db?.close()
            }
            db = null
        }

        fun scrubDatabase(context: Context) {
            try {
                val dbInstance = getInstance(context)
                val writable = dbInstance.openHelper.writableDatabase
                writable.execSQL("PRAGMA secure_delete = ON")
                writable.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                writable.execSQL("VACUUM")
            } catch (e: Exception) {
                // Ignore or log best-effort scrub failure
            }
        }
    }
}
