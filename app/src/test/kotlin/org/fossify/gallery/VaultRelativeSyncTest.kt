package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.google.gson.Gson
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CIPHER_SECRETS_DIR_NAME
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.VirtualAlbumBackup
import org.fossify.gallery.helpers.VirtualAlbumItemBackup
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.interfaces.VirtualAlbumsDao
import org.fossify.gallery.models.ITEM_STATE_OK
import org.fossify.gallery.models.ITEM_STATE_UNRESOLVED
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.VirtualAlbum
import org.fossify.gallery.models.VirtualAlbumItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import org.fossify.gallery.helpers.prepareVaultLocationSwitch

class VaultRelativeSyncTest {

    private lateinit var tempRoot: File
    private lateinit var fakeDao: InMemoryVirtualAlbumsDao
    private lateinit var fakeContext: FakeSyncContext

    @Before
    fun setUp() {
        tempRoot = createTempDirectory("vault_rel_sync_test_").toFile()
        fakeDao = InMemoryVirtualAlbumsDao()
        fakeContext = FakeSyncContext(tempRoot)
        VirtualAlbumSyncManager.daoProviderForTest = { fakeDao }
        resetCryptoManagerState()
    }

    @After
    fun tearDown() {
        VirtualAlbumSyncManager.daoProviderForTest = null
        CryptoManager.otgMountBaseOverrideForTest = null
        resetCryptoManagerState()
        tempRoot.deleteRecursively()
    }

    @Test
    fun extractRelativeVaultPath_and_resolveVaultItemPath_handleMountsAndRejectTraversal() {
        val oldVaultRoot = File("/mnt/media_rw/USB-1111/Cipher_Secrets")
        val newVaultRoot = File("/storage/USB-2222/CustomVault")

        // Relative extraction against explicit vault root
        assertEquals(
            "a1b2c3/4f8a1c9e.bin",
            VirtualAlbumSyncManager.extractRelativeVaultPath(
                "/mnt/media_rw/USB-1111/Cipher_Secrets/a1b2c3/4f8a1c9e.bin",
                oldVaultRoot
            )
        )

        // Windows separator normalization + fallback marker when vaultRoot is null or mismatched
        assertEquals(
            "a1b2c3/4f8a1c9e.bin",
            VirtualAlbumSyncManager.extractRelativeVaultPath(
                "E:\\Portable\\Cipher_Secrets\\a1b2c3\\4f8a1c9e.bin",
                null
            )
        )
        assertEquals(
            "sub/item.bin",
            VirtualAlbumSyncManager.extractRelativeVaultPath(
                "/storage/legacy/Cipher/sub/item.bin",
                null
            )
        )

        // Ignores JSON/ metadata folder
        assertNull(
            VirtualAlbumSyncManager.extractRelativeVaultPath(
                "/storage/USB-2222/CustomVault/JSON/fva_123.json",
                newVaultRoot
            )
        )

        // Resolves relative path against new vault root cleanly
        assertEquals(
            File(newVaultRoot, "a1b2c3/4f8a1c9e.bin").absolutePath,
            VirtualAlbumSyncManager.resolveVaultItemPath(
                vaultRoot = newVaultRoot,
                relativeVaultPath = "a1b2c3/4f8a1c9e.bin",
                rawFullPath = "/mnt/media_rw/USB-1111/Cipher_Secrets/a1b2c3/4f8a1c9e.bin"
            )
        )

        // Fallback via rawFullPath when relativeVaultPath is absent
        assertEquals(
            File(newVaultRoot, "a1b2c3/4f8a1c9e.bin").absolutePath,
            VirtualAlbumSyncManager.resolveVaultItemPath(
                vaultRoot = newVaultRoot,
                relativeVaultPath = null,
                rawFullPath = "/mnt/media_rw/OtherMount/Cipher_Secrets/a1b2c3/4f8a1c9e.bin"
            )
        )

        // Rejects path traversal
        assertNull(
            VirtualAlbumSyncManager.resolveVaultItemPath(
                vaultRoot = newVaultRoot,
                relativeVaultPath = "../outside.bin",
                rawFullPath = null
            )
        )
        assertNull(
            VirtualAlbumSyncManager.resolveVaultItemPath(
                vaultRoot = newVaultRoot,
                relativeVaultPath = "sub/../../outside.bin",
                rawFullPath = null
            )
        )
    }

    @Test
    fun syncVirtualAlbums_remapsStealthVaultItemsAcrossMounts_andRewritesStaleDbPaths() {
        val usb2Secrets = File(tempRoot, "Usb2/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        File(usb2Secrets, VAULT_HEADER_FILE).writeBytes("FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32))

        val subAlbumDir = File(usb2Secrets, "a1b2c3").apply { mkdirs() }
        val presentEncryptedFile = File(subAlbumDir, "item1.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val expectedPresentPath = presentEncryptedFile.absolutePath
        val expectedMissingPath = File(subAlbumDir, "missing2.bin").absolutePath

        // Write stealth backup JSON originally created when vault was mounted at /mnt/usb1/Cipher_Secrets
        val jsonDir = File(usb2Secrets, "JSON").apply { mkdirs() }
        val backup = VirtualAlbumBackup(
            version = 2,
            name = "Secret Trip",
            createdTs = 1700000100L,
            isHidden = true,
            parentPhysicalPath = "/mnt/usb1/Cipher_Secrets/a1b2c3",
            relativeParentVaultPath = "a1b2c3",
            items = listOf(
                VirtualAlbumItemBackup(
                    fullPath = "/mnt/usb1/Cipher_Secrets/a1b2c3/item1.bin",
                    relativeVaultPath = "a1b2c3/item1.bin",
                    addedTs = 1700000200L,
                    fileSize = 4L
                ),
                VirtualAlbumItemBackup(
                    fullPath = "/mnt/usb1/Cipher_Secrets/a1b2c3/missing2.bin",
                    relativeVaultPath = "a1b2c3/missing2.bin",
                    addedTs = 1700000300L,
                    fileSize = 9L
                )
            )
        )
        File(jsonDir, "fva_crossmount_test.json").writeText(Gson().toJson(backup))

        // Configure usb2 as the active vault
        fakeContext.config.vaultRootPath = usb2Secrets.absolutePath

        // Pre-populate stale DB cache pointing at the old USB1 mount path
        val staleAlbumId = fakeDao.insertAlbum(
            VirtualAlbum(
                name = "Secret Trip",
                createdTs = 1700000100L,
                isHidden = true,
                parentPhysicalPath = "/mnt/usb1/Cipher_Secrets/a1b2c3"
            )
        )
        fakeDao.insertItems(
            listOf(
                VirtualAlbumItem(
                    id = null,
                    albumId = staleAlbumId,
                    fullPath = "/mnt/usb1/Cipher_Secrets/a1b2c3/item1.bin",
                    filename = "item1.bin",
                    parentPath = "/mnt/usb1/Cipher_Secrets/a1b2c3",
                    addedTS = 1700000200L,
                    fileSize = 4L,
                    state = ITEM_STATE_UNRESOLVED
                )
            )
        )

        VirtualAlbumSyncManager.syncVirtualAlbums(fakeContext, force = true)

        val syncedAlbum = fakeDao.getAlbumByName("Secret Trip")
        assertNotNull(syncedAlbum)
        assertEquals(subAlbumDir.absolutePath, syncedAlbum!!.parentPhysicalPath)

        val syncedItems = fakeDao.getItems(syncedAlbum.id!!)
        val presentItem = syncedItems.firstOrNull { it.fullPath == expectedPresentPath }
        val missingItem = syncedItems.firstOrNull { it.fullPath == expectedMissingPath }

        assertNotNull("Expected existing stale DB row to be remapped to USB2 mount path", presentItem)
        assertEquals(subAlbumDir.absolutePath, presentItem!!.parentPath)
        assertEquals(ITEM_STATE_OK, presentItem.state)

        assertNotNull("Expected missing item from backup to be restored as unresolved under USB2 path", missingItem)
        assertEquals(ITEM_STATE_UNRESOLVED, missingItem!!.state)
    }

    @Test
    fun evictHiddenVirtualAlbumsFromDbCache_preservesPublicAlbumsAndDiskJson() {
        val vaultSecrets = File(tempRoot, "VaultA/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultSecrets, VAULT_HEADER_FILE).writeBytes("FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        val jsonFile = File(File(vaultSecrets, "JSON").apply { mkdirs() }, "fva_hidden.json").apply {
            writeText("{\"version\":2}")
        }

        val hiddenId = fakeDao.insertAlbum(VirtualAlbum(name = "HiddenOne", isHidden = true))
        val publicId = fakeDao.insertAlbum(VirtualAlbum(name = "PublicOne", isHidden = false))
        fakeDao.insertItems(
            listOf(
                VirtualAlbumItem(null, hiddenId, "/vault/a/1.bin", "1.bin", "/vault/a", 100L),
                VirtualAlbumItem(null, publicId, "/sdcard/DCIM/1.jpg", "1.jpg", "/sdcard/DCIM", 200L)
            )
        )

        fakeContext.config.vaultRootPath = vaultSecrets.absolutePath
        val newVaultSecrets = File(tempRoot, "VaultB/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        CryptoManager.prepareVaultLocationSwitch(fakeContext, newVaultSecrets)

        assertNull(fakeDao.getAlbumById(hiddenId))
        assertTrue(fakeDao.getItems(hiddenId).isEmpty())
        assertNotNull(fakeDao.getAlbumById(publicId))
        assertEquals(1, fakeDao.getItems(publicId).size)
        assertTrue("Disk JSON backup in old vault must remain intact", jsonFile.exists())
    }

    private fun resetCryptoManagerState() {
        CryptoManager.lockVault()
        runCatching {
            val cachedField = CryptoManager::class.java.getDeclaredField("cachedVaultRoot")
            cachedField.isAccessible = true
            cachedField.set(CryptoManager, null)

            val scanField = CryptoManager::class.java.getDeclaredField("lastDeepScanMs")
            scanField.isAccessible = true
            scanField.setLong(CryptoManager, 0L)
        }
    }

    private class FakeSyncContext(
        private val root: File
    ) : ContextWrapper(null) {
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

        override fun renameAlbum(id: Long, name: String) {
            albums.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { idx ->
                albums[idx] = albums[idx].copy(name = name)
            }
        }

        override fun updateAlbumVisibilityAndName(id: Long, isHidden: Boolean, name: String) {
            albums.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { idx ->
                albums[idx] = albums[idx].copy(isHidden = isHidden, name = name)
            }
        }

        override fun updateAlbumThumbnail(id: Long, thumbnail: String) {
            albums.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { idx ->
                albums[idx] = albums[idx].copy(thumbnail = thumbnail)
            }
        }

        override fun updateAlbumParentPhysicalPath(id: Long, parentPhysicalPath: String) {
            albums.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { idx ->
                albums[idx] = albums[idx].copy(parentPhysicalPath = parentPhysicalPath)
            }
        }

        override fun updateAlbumParentPhysicalPathByPrefix(oldRoot: String, newRoot: String) {}
        override fun updateAlbumParentPhysicalPathAtFolder(oldRoot: String, newRoot: String) {}
        override fun getAlbumsByParentPhysicalPath(parentPhysicalPath: String): List<VirtualAlbum> =
            albums.filter { it.parentPhysicalPath.equals(parentPhysicalPath, ignoreCase = true) }

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

        override fun updateItemPath(oldPath: String, newPath: String, newFilename: String, newParentPath: String) {
            items.replaceAll {
                if (it.fullPath.equals(oldPath, ignoreCase = true)) {
                    it.copy(fullPath = newPath, filename = newFilename, parentPath = newParentPath)
                } else it
            }
        }

        override fun updateItemPathInAlbum(
            albumId: Long,
            oldPath: String,
            newPath: String,
            newFilename: String,
            newParentPath: String
        ) {
            items.removeAll {
                it.albumId == albumId &&
                    it.fullPath.equals(newPath, ignoreCase = true) &&
                    !it.fullPath.equals(oldPath, ignoreCase = true)
            }
            items.replaceAll {
                if (it.albumId == albumId && it.fullPath.equals(oldPath, ignoreCase = true)) {
                    it.copy(fullPath = newPath, filename = newFilename, parentPath = newParentPath)
                } else it
            }
        }

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

        override fun getItemsWithPath(albumId: Long, fullPath: String): List<VirtualAlbumItem> =
            items.filter { it.albumId == albumId && it.fullPath.equals(fullPath, ignoreCase = true) }

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

        override fun getUnresolvedItems(albumId: Long): List<VirtualAlbumItem> =
            items.filter { it.albumId == albumId && it.state == ITEM_STATE_UNRESOLVED }

        override fun updateItemStateInAlbum(albumId: Long, fullPath: String, state: String) {
            items.replaceAll {
                if (it.albumId == albumId && it.fullPath.equals(fullPath, ignoreCase = true)) {
                    it.copy(state = state)
                } else it
            }
        }

        override fun updateItemAndStateInAlbum(
            albumId: Long,
            oldPath: String,
            newPath: String,
            newFilename: String,
            newParentPath: String,
            state: String
        ) {
            items.removeAll {
                it.albumId == albumId &&
                    it.fullPath.equals(newPath, ignoreCase = true) &&
                    !it.fullPath.equals(oldPath, ignoreCase = true)
            }
            items.replaceAll {
                if (it.albumId == albumId && it.fullPath.equals(oldPath, ignoreCase = true)) {
                    it.copy(fullPath = newPath, filename = newFilename, parentPath = newParentPath, state = state)
                } else it
            }
        }

        override fun getItemsByState(state: String): List<VirtualAlbumItem> =
            items.filter { it.state == state }

        override fun getAlbumIdsWithFolderPrefix(folderPrefix: String): List<Long> {
            val prefix = folderPrefix.lowercase() + "/"
            return items.filter { it.fullPath.lowercase().startsWith(prefix) }.map { it.albumId }.distinct()
        }

        override fun removeItemsByFolderPrefix(folderPrefix: String) {
            val prefix = folderPrefix.lowercase() + "/"
            items.removeAll { it.fullPath.lowercase().startsWith(prefix) }
        }
    }
}
