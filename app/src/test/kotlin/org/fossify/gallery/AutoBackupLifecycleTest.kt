package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.google.gson.Gson
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.VirtualAlbumBackup
import org.fossify.gallery.helpers.VirtualAlbumItemBackup
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.interfaces.FavoritesDao
import org.fossify.gallery.interfaces.VirtualAlbumsDao
import org.fossify.gallery.models.Favorite
import org.fossify.gallery.models.ITEM_STATE_OK
import org.fossify.gallery.models.ITEM_STATE_UNRESOLVED
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.VirtualAlbum
import org.fossify.gallery.models.VirtualAlbumItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AutoBackupLifecycleTest {

    private lateinit var tempRoot: File
    private lateinit var favoritesDir: File
    private lateinit var virtualAlbumsDir: File
    private lateinit var mediaDir: File
    private lateinit var fakeContext: FakeBackupContext
    private lateinit var fakeFavoritesDao: InMemoryFavoritesDao
    private lateinit var fakeVirtualAlbumsDao: InMemoryVirtualAlbumsDao

    @Before
    fun setUp() {
        tempRoot = createTempDirectory("auto_backup_lifecycle_test_").toFile()
        favoritesDir = File(tempRoot, "Favorites")
        virtualAlbumsDir = File(tempRoot, "Virtual Album Saves")
        mediaDir = File(tempRoot, "Media").apply { mkdirs() }
        fakeContext = FakeBackupContext(tempRoot)
        fakeFavoritesDao = InMemoryFavoritesDao()
        fakeVirtualAlbumsDao = InMemoryVirtualAlbumsDao()

        ReferenceBackupManager.customFavoritesDirForTest = favoritesDir
        ReferenceBackupManager.favoritesDaoProviderForTest = { fakeFavoritesDao }
        ReferenceBackupManager.mediaFavoriteUpdaterForTest = { _, _, _ -> }

        VirtualAlbumSyncManager.customStorageDirForTest = virtualAlbumsDir
        VirtualAlbumSyncManager.daoProviderForTest = { fakeVirtualAlbumsDao }
        VirtualAlbumSyncManager.resetSyncThrottle()
    }

    @After
    fun tearDown() {
        ReferenceBackupManager.customFavoritesDirForTest = null
        ReferenceBackupManager.favoritesDaoProviderForTest = null
        ReferenceBackupManager.mediaFavoriteUpdaterForTest = null

        VirtualAlbumSyncManager.customStorageDirForTest = null
        VirtualAlbumSyncManager.daoProviderForTest = null
        VirtualAlbumSyncManager.resetSyncThrottle()

        tempRoot.deleteRecursively()
    }

    @Test
    fun publicVirtualAlbums_restoredOnceOnFreshInstall_andNeverRecreatedWhenFolderDeleted() {
        val photo1 = File(mediaDir, "p1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        virtualAlbumsDir.mkdirs()
        val albumJson = File(virtualAlbumsDir, "Vacation.json")
        albumJson.writeText(
            Gson().toJson(
                VirtualAlbumBackup(
                    version = 1,
                    name = "Vacation",
                    createdTs = 1700000000L,
                    isHidden = false,
                    parentPhysicalPath = mediaDir.absolutePath,
                    items = listOf(VirtualAlbumItemBackup(fullPath = photo1.absolutePath, addedTs = 1700000001L))
                )
            )
        )

        assertFalse(fakeContext.config.hasCompletedInitialVirtualAlbumsRestore)
        assertTrue(VirtualAlbumSyncManager.syncVirtualAlbums(fakeContext, force = true))
        assertTrue(fakeContext.config.hasCompletedInitialVirtualAlbumsRestore)
        assertNotNull(fakeVirtualAlbumsDao.getAlbumByName("Vacation"))

        // User deletes the entire Virtual Album Saves folder externally.
        assertTrue(virtualAlbumsDir.deleteRecursively())
        assertFalse(virtualAlbumsDir.exists())

        // Subsequent syncs must NOT recreate the folder or JSON files from DB.
        VirtualAlbumSyncManager.syncVirtualAlbums(fakeContext, force = true)
        assertFalse("Deleted public backup folder must not be recreated by sync", virtualAlbumsDir.exists())
        assertNotNull("Album in app DB must remain intact when backup folder is deleted", fakeVirtualAlbumsDao.getAlbumByName("Vacation"))
    }

    @Test
    fun publicVirtualAlbums_removedItemNotResurrectedWhenAutoBackupDisabled() {
        val photo1 = File(mediaDir, "a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val photo2 = File(mediaDir, "b.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        val albumId = fakeVirtualAlbumsDao.insertAlbum(VirtualAlbum(name = "Trips", isHidden = false))
        fakeVirtualAlbumsDao.insertItems(
            listOf(
                VirtualAlbumItem(null, albumId, photo1.absolutePath, photo1.name, mediaDir.absolutePath, 100L),
                VirtualAlbumItem(null, albumId, photo2.absolutePath, photo2.name, mediaDir.absolutePath, 200L)
            )
        )
        VirtualAlbumSyncManager.saveAlbumToFile(fakeContext, albumId)
        fakeContext.config.hasCompletedInitialVirtualAlbumsRestore = true

        // Disable auto-backup without deleting disk files, then remove photo2 from DB.
        fakeContext.config.autoBackupFavoritesAndVirtualAlbums = false
        fakeVirtualAlbumsDao.removeItemFromAlbum(albumId, photo2.absolutePath)
        VirtualAlbumSyncManager.saveAlbumToFile(fakeContext, albumId)

        // Old JSON still has photo2, yet sync must NOT resurrect photo2 into DB.
        VirtualAlbumSyncManager.syncVirtualAlbums(fakeContext, force = true)
        val remainingPaths = fakeVirtualAlbumsDao.getItems(albumId).map { it.fullPath }
        assertEquals(listOf(photo1.absolutePath), remainingPaths)
    }

    @Test
    fun publicVirtualAlbums_validDetectionDeleteAndFullRebackupWorkCleanly() {
        virtualAlbumsDir.mkdirs()
        // Invalid / non-public files must be ignored by valid backup check and deletion.
        val corrupted = File(virtualAlbumsDir, "broken.json").apply { writeText("{ bad json") }
        val hiddenLeak = File(virtualAlbumsDir, "fva_hidden.json").apply {
            writeText(Gson().toJson(VirtualAlbumBackup(version = 2, isHidden = true, encryptedPayload = "enc")))
        }
        val readme = File(virtualAlbumsDir, "notes.txt").apply { writeText("keep me") }

        assertFalse(VirtualAlbumSyncManager.hasValidPublicVirtualAlbumBackups(fakeContext))

        // Add a valid public album backup.
        val album1Id = fakeVirtualAlbumsDao.insertAlbum(VirtualAlbum(name = "Work", isHidden = false))
        VirtualAlbumSyncManager.saveAlbumToFile(fakeContext, album1Id)
        assertTrue(VirtualAlbumSyncManager.hasValidPublicVirtualAlbumBackups(fakeContext))

        // Delete public backups: deletes only valid public JSON, leaves unrelated files untouched.
        assertEquals(1, VirtualAlbumSyncManager.deletePublicVirtualAlbumBackups(fakeContext))
        assertFalse(File(virtualAlbumsDir, "Work.json").exists())
        assertTrue(corrupted.exists())
        assertTrue(hiddenLeak.exists())
        assertTrue(readme.exists())
        assertFalse(VirtualAlbumSyncManager.hasValidPublicVirtualAlbumBackups(fakeContext))

        // When toggling auto-backup back ON, backupAllPublicAlbums exports all current public DB albums
        // and removes stale public album JSONs that no longer exist in DB.
        File(virtualAlbumsDir, "DeletedWhileOff.json").writeText(
            Gson().toJson(VirtualAlbumBackup(version = 1, name = "DeletedWhileOff", isHidden = false, items = emptyList()))
        )
        fakeVirtualAlbumsDao.insertAlbum(VirtualAlbum(name = "Family", isHidden = false))
        VirtualAlbumSyncManager.backupAllPublicAlbums(fakeContext)

        assertTrue(File(virtualAlbumsDir, "Work.json").exists())
        assertTrue(File(virtualAlbumsDir, "Family.json").exists())
        assertFalse(File(virtualAlbumsDir, "DeletedWhileOff.json").exists())
    }

    @Test
    fun favorites_restoredOnceOnFreshInstall_andNeverResurrectedAfterUnfavorite() {
        val photo1 = File(mediaDir, "fav1.jpg").apply { writeBytes(byteArrayOf(7, 8, 9, 10)) }
        favoritesDir.mkdirs()
        File(favoritesDir, ReferenceBackupManager.FAVORITES_FILE_NAME).writeText(
            Gson().toJson(
                ReferenceBackupManager.FavoritesBackup(
                    version = 1,
                    favorites = listOf(
                        ReferenceBackupManager.FavoriteBackup(
                            fullPath = photo1.absolutePath,
                            filename = photo1.name,
                            parentPath = mediaDir.absolutePath,
                            fileSize = photo1.length(),
                            lastModified = photo1.lastModified(),
                            fingerprint = "deadbeef"
                        )
                    )
                )
            )
        )

        assertTrue(ReferenceBackupManager.hasValidFavoritesBackup(fakeContext))
        assertEquals(1, ReferenceBackupManager.restoreFavorites(fakeContext))
        assertTrue(fakeContext.config.hasCompletedInitialFavoritesRestore)
        assertNotNull(fakeFavoritesDao.getFavorite(photo1.absolutePath))

        // Turn off auto-backup (leaving favorites.json on disk) and remove the favorite in DB.
        fakeContext.config.autoBackupFavoritesAndVirtualAlbums = false
        fakeFavoritesDao.deleteFavoritePath(photo1.absolutePath)
        ReferenceBackupManager.saveFavorites(fakeContext)

        // Stale favorites.json is still on disk, but restoreFavorites() must NOT resurrect it.
        assertTrue(ReferenceBackupManager.hasValidFavoritesBackup(fakeContext))
        assertEquals(0, ReferenceBackupManager.restoreFavorites(fakeContext))
        assertNull(fakeFavoritesDao.getFavorite(photo1.absolutePath))

        // Deleting the backup removes favorites.json and the now-empty Favorites directory.
        assertTrue(ReferenceBackupManager.deleteFavoritesBackup(fakeContext))
        assertFalse(ReferenceBackupManager.hasValidFavoritesBackup(fakeContext))
        assertFalse(favoritesDir.exists())
    }

    @Test
    fun favorites_emptyDbRemovesBackupFile_andEmptyJsonIsNotTreatedAsValidBackup() {
        val photo1 = File(mediaDir, "fav2.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        fakeFavoritesDao.insert(
            Favorite(
                id = null,
                fullPath = photo1.absolutePath,
                filename = photo1.name,
                parentPath = mediaDir.absolutePath
            )
        )

        ReferenceBackupManager.saveFavorites(fakeContext)
        assertTrue(ReferenceBackupManager.hasValidFavoritesBackup(fakeContext))

        // Removing the last favorite while auto-backup is ON removes favorites.json and empty dir.
        fakeFavoritesDao.clearFavorites()
        ReferenceBackupManager.saveFavorites(fakeContext)
        assertFalse(ReferenceBackupManager.getFavoritesBackupFile().exists())
        assertFalse(favoritesDir.exists())
        assertFalse(ReferenceBackupManager.hasValidFavoritesBackup(fakeContext))

        // A file with an empty favorites array or corrupt JSON must NOT count as a valid backup.
        favoritesDir.mkdirs()
        ReferenceBackupManager.getFavoritesBackupFile().writeText("{\"version\":1,\"favorites\":[]}")
        assertFalse(ReferenceBackupManager.hasValidFavoritesBackup(fakeContext))

        ReferenceBackupManager.getFavoritesBackupFile().writeText("{ not json")
        assertFalse(ReferenceBackupManager.hasValidFavoritesBackup(fakeContext))
    }

    private class FakeBackupContext(root: File) : ContextWrapper(null) {
        private val prefs = InMemorySharedPreferences()
        private val filesDirectory = File(root, "app_files").apply { mkdirs() }

        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "org.fossify.gallery.debug"
        override fun getFilesDir(): File = filesDirectory
        override fun getExternalFilesDir(type: String?): File = File(filesDirectory, type ?: "ext").apply { mkdirs() }
        override fun getExternalFilesDirs(type: String?): Array<File?> = emptyArray()
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true; pending.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) values.clear()
                pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            }
        }
    }

    private class InMemoryFavoritesDao : FavoritesDao {
        private var nextId = 1
        private val items = mutableListOf<Favorite>()

        override fun insert(favorite: Favorite) {
            items.removeAll { it.fullPath.equals(favorite.fullPath, ignoreCase = true) }
            items.add(favorite.copy(id = favorite.id ?: nextId++))
        }

        override fun insertAll(favorites: List<Favorite>) = favorites.forEach { insert(it) }
        override fun getAll(): List<Favorite> = items.toList()
        override fun getActive(): List<Favorite> = items.filter { it.state == ITEM_STATE_OK }
        override fun getEncryptedDormant(): List<Favorite> = items.filter { it.state == "ENCRYPTED" }
        override fun getFavorite(path: String): Favorite? = items.firstOrNull { it.fullPath.equals(path, ignoreCase = true) }
        override fun getValidFavoritePaths(): List<String> = getActive().map { it.fullPath }
        override fun isFavorite(path: String): Boolean = getFavorite(path) != null
        override fun updateFavorite(newFilename: String, newFullPath: String, newParentPath: String, oldPath: String) {}
        override fun updateFavoriteState(path: String, state: String) {}
        override fun restoreEncryptedFavorite(oldPath: String, newFilename: String, newFullPath: String, newParentPath: String) {}
        override fun deleteFavoritePath(path: String) {
            items.removeAll { it.fullPath.equals(path, ignoreCase = true) }
        }
        override fun deleteFavoritesByPrefix(pathPrefix: String) {}
        override fun clearFavorites() = items.clear()
        override fun updateFavoritesByFolderPrefix(oldRoot: String, newRoot: String) {}
        override fun updateFavoriteParentsAtFolder(oldRoot: String, newRoot: String) {}
        override fun updateFavoriteParentsByFolderPrefix(oldRoot: String, newRoot: String) {}
        override fun updateFavoriteIdentity(
            path: String,
            mediaStoreId: Long,
            fileSize: Long,
            lastModified: Long,
            fingerprint: String,
            folderInode: Long,
            folderSignature: String,
            capturedAtSec: Long
        ) {}
    }

    private class InMemoryVirtualAlbumsDao : VirtualAlbumsDao {
        private var nextAlbumId = 1L
        private var nextItemId = 1L
        private val albums = mutableListOf<VirtualAlbum>()
        private val items = mutableListOf<VirtualAlbumItem>()

        override fun insertAlbum(album: VirtualAlbum): Long {
            val existing = albums.firstOrNull { it.name.equals(album.name, ignoreCase = true) }
            if (existing != null) return -1L
            val id = album.id ?: nextAlbumId++
            albums.add(album.copy(id = id))
            return id
        }

        override fun getAllAlbums(): List<VirtualAlbum> = albums.sortedByDescending { it.createdTs }
        override fun getAlbumById(id: Long): VirtualAlbum? = albums.firstOrNull { it.id == id }
        override fun getAlbumByName(name: String): VirtualAlbum? =
            albums.firstOrNull { it.name.equals(name, ignoreCase = true) }

        override fun renameAlbum(id: Long, name: String) {}
        override fun updateAlbumVisibilityAndName(id: Long, isHidden: Boolean, name: String) {}
        override fun updateAlbumThumbnail(id: Long, thumbnail: String) {}
        override fun updateAlbumParentPhysicalPath(id: Long, parentPhysicalPath: String) {}
        override fun updateAlbumParentPhysicalPathByPrefix(oldRoot: String, newRoot: String) {}
        override fun updateAlbumParentPhysicalPathAtFolder(oldRoot: String, newRoot: String) {}
        override fun getAlbumsByParentPhysicalPath(parentPhysicalPath: String): List<VirtualAlbum> = emptyList()
        override fun deleteAlbumById(id: Long) {
            albums.removeAll { it.id == id }
            items.removeAll { it.albumId == id }
        }
        override fun deleteItemsForAlbum(albumId: Long) {
            items.removeAll { it.albumId == albumId }
        }
        override fun insertItem(item: VirtualAlbumItem): Long {
            val dup = items.any { it.albumId == item.albumId && it.fullPath.equals(item.fullPath, ignoreCase = true) }
            if (dup) return -1L
            val id = item.id ?: nextItemId++
            items.add(item.copy(id = id))
            return id
        }
        override fun insertItems(items: List<VirtualAlbumItem>): List<Long> = items.map { insertItem(it) }
        override fun removeItemFromAlbum(albumId: Long, fullPath: String) {
            items.removeAll { it.albumId == albumId && it.fullPath.equals(fullPath, ignoreCase = true) }
        }
        override fun removeItemFromAllAlbums(fullPath: String) {
            items.removeAll { it.fullPath.equals(fullPath, ignoreCase = true) }
        }
        override fun getAlbumIdsWithItem(fullPath: String): List<Long> =
            items.filter { it.fullPath.equals(fullPath, ignoreCase = true) }.map { it.albumId }.distinct()
        override fun updateItemPath(oldPath: String, newPath: String, newFilename: String, newParentPath: String) {}
        override fun updateItemPathInAlbum(albumId: Long, oldPath: String, newPath: String, newFilename: String, newParentPath: String) {}
        override fun getItems(albumId: Long): List<VirtualAlbumItem> =
            items.filter { it.albumId == albumId }.sortedByDescending { it.addedTS }
        override fun getAllItems(): List<VirtualAlbumItem> = items.toList()
        override fun getDeletedPathsForAlbum(albumId: Long): List<String> = emptyList()
        override fun getValidPaths(albumId: Long): List<String> = emptyList()
        override fun getValidMedia(albumId: Long): List<Medium> = emptyList()
        override fun getValidItemCount(albumId: Long): Int = 0
        override fun updateItemsByFolderPrefix(oldRoot: String, newRoot: String) {}
        override fun updateItemParentsAtFolder(oldRoot: String, newRoot: String) {}
        override fun updateItemParentsByFolderPrefix(oldRoot: String, newRoot: String) {}
        override fun updateItemIdentity(
            path: String,
            mediaStoreId: Long,
            fileSize: Long,
            lastModified: Long,
            fingerprint: String,
            folderInode: Long,
            folderSignature: String,
            capturedAtSec: Long
        ) {}
        override fun getItemsWithPath(albumId: Long, fullPath: String): List<VirtualAlbumItem> = emptyList()
        override fun markItemUnresolved(albumId: Long, fullPath: String) {
            items.replaceAll {
                if (it.albumId == albumId && it.fullPath.equals(fullPath, ignoreCase = true)) {
                    it.copy(state = ITEM_STATE_UNRESOLVED)
                } else it
            }
        }
        override fun markItemResolved(albumId: Long, fullPath: String) {
            items.replaceAll {
                if (it.albumId == albumId && it.fullPath.equals(fullPath, ignoreCase = true)) {
                    it.copy(state = ITEM_STATE_OK)
                } else it
            }
        }
        override fun getUnresolvedItems(albumId: Long): List<VirtualAlbumItem> = emptyList()
        override fun updateItemStateInAlbum(albumId: Long, fullPath: String, state: String) {}
        override fun updateItemAndStateInAlbum(
            albumId: Long,
            oldPath: String,
            newPath: String,
            newFilename: String,
            newParentPath: String,
            state: String
        ) {}
        override fun getItemsByState(state: String): List<VirtualAlbumItem> = emptyList()
        override fun getAlbumIdsWithFolderPrefix(folderPrefix: String): List<Long> = emptyList()
        override fun removeItemsByFolderPrefix(folderPrefix: String) {}
    }
}
