package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.virtualAlbumsDB
import org.fossify.gallery.interfaces.VirtualAlbumsDao
import org.fossify.gallery.models.ITEM_STATE_DECRYPTED
import org.fossify.gallery.models.ITEM_STATE_ENCRYPTED
import org.fossify.gallery.models.ITEM_STATE_OK
import org.fossify.gallery.models.ITEM_STATE_UNRESOLVED
import org.fossify.gallery.models.VirtualAlbum
import org.fossify.gallery.models.VirtualAlbumItem
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

data class VirtualAlbumBackup(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("name") val name: String? = null,
    @SerializedName("createdTs") val createdTs: Long? = null,
    @SerializedName("isHidden") val isHidden: Boolean = false,
    @SerializedName("items") val items: List<VirtualAlbumItemBackup>? = null,
    @SerializedName("parentPhysicalPath") val parentPhysicalPath: String? = null,
    @SerializedName("mediaPaths") val mediaPaths: List<String>? = null,
    @SerializedName("encryptedPayload") val encryptedPayload: String? = null,
    @SerializedName("relativeParentVaultPath") val relativeParentVaultPath: String? = null
)

data class VirtualAlbumItemBackup(
    @SerializedName("fullPath") val fullPath: String? = null,
    @SerializedName("addedTs") val addedTs: Long? = null,
    @SerializedName("mediaStoreId") val mediaStoreId: Long? = null,
    @SerializedName("fileSize") val fileSize: Long? = null,
    @SerializedName("lastModified") val lastModified: Long? = null,
    @SerializedName("fingerprint") val fingerprint: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("folderInode") val folderInode: Long? = null,
    @SerializedName("folderSignature") val folderSignature: String? = null,
    @SerializedName("capturedAtSec") val capturedAtSec: Long? = null,
    @SerializedName("relativeVaultPath") val relativeVaultPath: String? = null
)

object VirtualAlbumSyncManager {

    @VisibleForTesting
    var customStorageDirForTest: File? = null

    @VisibleForTesting
    var customVaultDirForTest: File? = null

    @VisibleForTesting
    internal var daoProviderForTest: ((Context) -> VirtualAlbumsDao)? = null

    private val publicRestoreLock = Any()

    @Volatile
    private var lastSyncTs = 0L

    private val directoryCacheVersion = AtomicLong(1L)

    fun invalidateDirectoryCache() {
        directoryCacheVersion.incrementAndGet()
    }

    fun getDirectoryCacheVersion(): Long = directoryCacheVersion.get()

    internal fun getDao(context: Context): VirtualAlbumsDao {
        return daoProviderForTest?.invoke(context) ?: context.virtualAlbumsDB
    }

    internal fun canAccessSharedStorage(): Boolean =
        VirtualAlbumBackupStorage.canAccessSharedStorage()

    private fun isValidPublicVirtualAlbumBackupFile(file: File, gson: Gson = Gson()): Boolean =
        VirtualAlbumBackupStorage.isValidPublicVirtualAlbumBackupFile(file, gson)

    fun hasValidPublicVirtualAlbumBackups(context: Context): Boolean =
        VirtualAlbumBackupStorage.hasValidPublicVirtualAlbumBackups(context)

    fun deletePublicVirtualAlbumBackups(context: Context): Int =
        VirtualAlbumBackupStorage.deletePublicVirtualAlbumBackups(context)

    fun backupAllPublicAlbums(context: Context) =
        VirtualAlbumBackupStorage.backupAllPublicAlbums(context)

    private fun isVaultJsonRelativePath(relPath: String): Boolean =
        VirtualAlbumBackupStorage.isVaultJsonRelativePath(relPath)

    fun extractRelativeVaultPath(fullPath: String?, vaultRoot: File? = null): String? =
        VirtualAlbumBackupStorage.extractRelativeVaultPath(fullPath, vaultRoot)

    fun resolveVaultItemPath(vaultRoot: File, relativeVaultPath: String?, rawFullPath: String?): String? =
        VirtualAlbumBackupStorage.resolveVaultItemPath(vaultRoot, relativeVaultPath, rawFullPath)

    fun evictHiddenVirtualAlbumsFromDbCache(context: Context) =
        VirtualAlbumBackupStorage.evictHiddenVirtualAlbumsFromDbCache(context)

    fun getVirtualAlbumsStorageDir(): File =
        VirtualAlbumBackupStorage.getVirtualAlbumsStorageDir()

    fun getVaultStorageDir(context: Context): File =
        VirtualAlbumBackupStorage.getVaultStorageDir(context)

    fun ensurePublicStorageDirExists(): File =
        VirtualAlbumBackupStorage.ensurePublicStorageDirExists()

    fun getPublicAlbumFileName(albumName: String, albumId: Long? = null): String =
        VirtualAlbumBackupStorage.getPublicAlbumFileName(albumName, albumId)

    fun getHiddenAlbumFileName(albumName: String): String =
        VirtualAlbumBackupStorage.getHiddenAlbumFileName(albumName)

    fun getAlbumFile(context: Context, album: VirtualAlbum): File =
        VirtualAlbumBackupStorage.getAlbumFile(context, album)

    fun toBackupItems(items: List<VirtualAlbumItem>, vaultRoot: File? = null): List<VirtualAlbumItemBackup> =
        VirtualAlbumBackupStorage.toBackupItems(items, vaultRoot)

    fun buildPublicAlbumBackup(album: VirtualAlbum, items: List<VirtualAlbumItem>): VirtualAlbumBackup =
        VirtualAlbumBackupStorage.buildPublicAlbumBackup(album, items)

    fun saveAlbumToFile(context: Context, albumId: Long) =
        VirtualAlbumBackupStorage.saveAlbumToFile(context, albumId)


    fun deleteAlbumFile(context: Context, album: VirtualAlbum) {
        invalidateDirectoryCache()
        if (!album.isHidden && (!context.config.autoBackupFavoritesAndVirtualAlbums || !canAccessSharedStorage())) {
            return
        }
        try {
            val file = getAlbumFile(context, album)
            if (file.exists()) {
                if (album.isHidden) {
                    CryptoManager.secureWipeAndDeleteFile(file, context)
                } else {
                    file.delete()
                }
            }
            // Check other location just in case
            val otherFile = if (album.isHidden) {
                File(getVirtualAlbumsStorageDir(), getPublicAlbumFileName(album.name, album.id))
            } else {
                File(getVaultStorageDir(context), getHiddenAlbumFileName(album.name))
            }
            if (otherFile.exists()) {
                CryptoManager.secureWipeAndDeleteFile(otherFile, context)
            }
            if (!album.isHidden) {
                val publicDir = getVirtualAlbumsStorageDir()
                if (publicDir.exists() && publicDir.isDirectory && publicDir.listFiles().isNullOrEmpty()) {
                    publicDir.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    fun onAlbumRenamed(context: Context, albumId: Long, oldAlbum: VirtualAlbum) {
        try {
            deleteAlbumFile(context, oldAlbum)
            saveAlbumToFile(context, albumId)
        } catch (_: Exception) {
        }
    }

    /**
     * Handles items whose file could not be found at the cached path.
     *
     * Nothing is deleted here. A missing file may simply have been renamed or moved, and the
     * identity of the item allows finding it again later. Removing the row would destroy the only
     * information needed to relocate it.
     */
    fun pruneMissingItems(context: Context, albumId: Long, missingPaths: Collection<String>) {
        if (missingPaths.isEmpty()) return
        try {
            // Only prune if the file's parent directory actually exists on disk (confirming drive is accessible)
            val actuallyMissing = missingPaths.filter { path ->
                val f = File(path)
                !f.exists() && f.parentFile?.exists() == true
            }
            if (actuallyMissing.isEmpty()) return

            // The item stays in the album and in the backup. It is only marked as unresolved so the
            // UI can tell the user that the file could not be found.
            val dao = getDao(context)
            actuallyMissing.forEach { path ->
                dao.markItemUnresolved(albumId, path)
            }
            saveAlbumToFile(context, albumId)
        } catch (_: Exception) {
        }
    }

    fun resetSyncThrottle() {
        lastSyncTs = 0L
        invalidateDirectoryCache()
    }

    fun syncVirtualAlbums(context: Context, force: Boolean = false): Boolean {
        val canReadSharedStorage = canAccessSharedStorage()
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncTs < 30_000L) {
            return false
        }
        if (canReadSharedStorage) {
            lastSyncTs = now
        }
        var anyDbChange = false

        val dao = getDao(context)
        val publicDir = getVirtualAlbumsStorageDir()

        val gson = Gson()
        val processedAlbumNames = HashSet<String>()

        // 1. Restore public non-hidden albums once on a fresh install / cleared app data.
        // Afterwards, the app database is the single source of truth and device-storage JSONs are
        // maintained as a one-way backup (never re-imported or recreated if deleted externally).
        if (canReadSharedStorage) {
            synchronized(publicRestoreLock) {
                if (!context.config.hasCompletedInitialVirtualAlbumsRestore &&
                    context.config.autoBackupFavoritesAndVirtualAlbums
                ) {
                    val candidateDirs = listOf(publicDir).filter { it.exists() && it.isDirectory }
                    for (sourceDir in candidateDirs) {
            val jsonFiles = sourceDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) } ?: emptyArray()
            for (file in jsonFiles) {
                try {
                    val content = file.readText(Charsets.UTF_8).trim()
                    if (content.isEmpty()) continue

                    val backup = try {
                        gson.fromJson(content, VirtualAlbumBackup::class.java)
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    // If a hidden album file was previously left in public folder, migrate to vault JSON
                    if (backup.isHidden && backup.encryptedPayload != null) {
                        try {
                            val vaultJsonDir = getVaultStorageDir(context)
                            val targetVaultFile = File(vaultJsonDir, file.name)
                            if (!targetVaultFile.exists()) {
                                file.copyTo(targetVaultFile, overwrite = true)
                            }
                            try { file.delete() } catch (_: Exception) {}
                        } catch (_: Exception) {}
                        continue
                    }

                    val albumName = backup.name?.trim()?.ifEmpty { null } ?: file.nameWithoutExtension.trim()
                    if (albumName.isEmpty()) continue
                    if (processedAlbumNames.contains(albumName.lowercase())) continue
                    processedAlbumNames.add(albumName.lowercase())

                    val isHidden = backup.isHidden
                    val createdTs = backup.createdTs ?: file.lastModified()

                    val parentPhysicalPath = backup.parentPhysicalPath?.trim()?.ifEmpty { null }
                        ?: backup.items?.firstOrNull()?.fullPath?.getParentPath()
                        ?: backup.mediaPaths?.firstOrNull()?.getParentPath()
                        ?: ""

                    var album = dao.getAlbumByName(albumName)
                    if (album == null) {
                        val newId = dao.insertAlbum(
                            VirtualAlbum(
                                id = null,
                                name = albumName,
                                createdTs = createdTs,
                                thumbnail = "",
                                isHidden = isHidden,
                                parentPhysicalPath = parentPhysicalPath
                            )
                        )
                        if (newId > 0) {
                            album = dao.getAlbumById(newId)
                            anyDbChange = true
                        }
                    } else if (album.parentPhysicalPath.isEmpty() && parentPhysicalPath.isNotEmpty()) {
                        album.id?.let { dao.updateAlbumParentPhysicalPath(it, parentPhysicalPath) }
                        album.parentPhysicalPath = parentPhysicalPath
                        anyDbChange = true
                    }

                    if (album?.id == null) continue
                    val albumId = album.id!!

                    val existingItems = dao.getItems(albumId)
                    val existingPaths = existingItems.map { it.fullPath.lowercase() }.toHashSet()

                    val rawItems = backup.items
                        ?: backup.mediaPaths?.map { VirtualAlbumItemBackup(fullPath = it, addedTs = createdTs) }
                        ?: emptyList()

                    val itemsToInsert = ArrayList<VirtualAlbumItem>()
                    var hadNewlyUnresolved = false

                    for (entry in rawItems) {
                        try {
                            val path = entry.fullPath ?: continue
                            if (path.isBlank()) continue
                            val lowerPath = path.lowercase()
                            if (existingPaths.contains(lowerPath)) continue

                            val targetFile = File(path)
                            val exists = targetFile.exists()
                            val isDormantState = entry.state == ITEM_STATE_ENCRYPTED || entry.state == ITEM_STATE_DECRYPTED
                            val hasUsableIdentity = (entry.mediaStoreId ?: 0L) > 0L ||
                                !entry.fingerprint.isNullOrEmpty() ||
                                (entry.fileSize ?: 0L) > 0L
                            // Legacy path-only entries without identity cannot be relocated if missing.
                            if (!isDormantState && !exists && !hasUsableIdentity && targetFile.parentFile?.exists() == true) {
                                hadNewlyUnresolved = true
                                continue
                            }

                            val resolvedState = when {
                                isDormantState -> entry.state!!
                                exists -> ITEM_STATE_OK
                                else -> ITEM_STATE_UNRESOLVED
                            }
                            itemsToInsert.add(
                                VirtualAlbumItem(
                                    id = null,
                                    albumId = albumId,
                                    fullPath = path,
                                    filename = targetFile.name,
                                    parentPath = targetFile.parent ?: path.getParentPath(),
                                    addedTS = if ((entry.addedTs ?: 0L) > 0) entry.addedTs!! else System.currentTimeMillis(),
                                    mediaStoreId = entry.mediaStoreId ?: 0L,
                                    fileSize = entry.fileSize ?: 0L,
                                    lastModified = entry.lastModified ?: 0L,
                                    fingerprint = entry.fingerprint ?: "",
                                    state = resolvedState,
                                    folderInode = entry.folderInode ?: 0L,
                                    folderSignature = entry.folderSignature ?: "",
                                    capturedAtSec = entry.capturedAtSec ?: 0L
                                )
                            )
                            existingPaths.add(lowerPath)
                        } catch (_: Exception) {
                            hadNewlyUnresolved = true
                        }
                    }

                    if (itemsToInsert.isNotEmpty()) {
                        dao.insertItems(itemsToInsert)
                        anyDbChange = true
                    }

                    val newlyMissingDbPaths = existingItems.filter {
                        it.state == ITEM_STATE_OK && !File(it.fullPath).exists() && File(it.fullPath).parentFile?.exists() == true
                    }.map { it.fullPath }
                    if (newlyMissingDbPaths.isNotEmpty()) {
                        // The item is kept, only marked as unresolved. It may have been renamed or
                        // moved, and the identity is what allows finding it again.
                        newlyMissingDbPaths.forEach { dao.markItemUnresolved(albumId, it) }
                        hadNewlyUnresolved = true
                        anyDbChange = true
                    }

                    if (hadNewlyUnresolved || itemsToInsert.isNotEmpty()) {
                        saveAlbumToFile(context, albumId)
                    }
                } catch (_: Exception) {
                }
            }
                    }
                    context.config.hasCompletedInitialVirtualAlbumsRestore = true
                }
            }
        }

        // 2. Restore stealth hidden virtual albums from vaultRoot/JSON/
        val vaultRoot = CryptoManager.findVaultRoot(context)
        if (vaultRoot != null && vaultRoot.exists()) {
            val vaultJsonDir = File(vaultRoot, VirtualAlbumBackupStorage.VAULT_JSON_SUBFOLDER)
            if (vaultJsonDir.exists() && vaultJsonDir.isDirectory) {
                val vaultFiles = vaultJsonDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) } ?: emptyArray()
                for (file in vaultFiles) {
                    try {
                        val content = file.readText(Charsets.UTF_8).trim()
                        if (content.isEmpty()) continue

                        val outerBackup = try {
                            gson.fromJson(content, VirtualAlbumBackup::class.java)
                        } catch (_: Exception) {
                            null
                        } ?: continue

                        val innerBackup: VirtualAlbumBackup = if (outerBackup.encryptedPayload != null) {
                            if (!CryptoManager.isVaultUnlocked) {
                                // Vault locked, leave file to be restored upon unlock
                                continue
                            }
                            val decrypted = CryptoManager.decryptStringFromVault(outerBackup.encryptedPayload, "album_json") ?: continue
                            try {
                                gson.fromJson(decrypted, VirtualAlbumBackup::class.java)
                            } catch (_: Exception) {
                                null
                            } ?: continue
                        } else {
                            outerBackup
                        }

                        val albumName = innerBackup.name?.trim()?.ifEmpty { null } ?: continue
                        val createdTs = innerBackup.createdTs ?: file.lastModified()

                        val effectiveParentPhysicalPath = resolveVaultItemPath(
                            vaultRoot,
                            innerBackup.relativeParentVaultPath,
                            innerBackup.parentPhysicalPath
                        )
                            ?: innerBackup.parentPhysicalPath?.trim()?.ifEmpty { null }
                            ?: innerBackup.items?.firstOrNull()?.let {
                                resolveVaultItemPath(vaultRoot, it.relativeVaultPath, it.fullPath)?.getParentPath()
                                    ?: it.fullPath?.getParentPath()
                            }
                            ?: innerBackup.mediaPaths?.firstOrNull()?.let {
                                resolveVaultItemPath(vaultRoot, null, it)?.getParentPath()
                                    ?: it.getParentPath()
                            }
                            ?: ""

                        var album = dao.getAlbumByName(albumName)
                        if (album == null) {
                            val newId = dao.insertAlbum(
                                VirtualAlbum(
                                    id = null,
                                    name = albumName,
                                    createdTs = createdTs,
                                    thumbnail = "",
                                    isHidden = true,
                                    parentPhysicalPath = effectiveParentPhysicalPath
                                )
                            )
                            if (newId > 0) {
                                album = dao.getAlbumById(newId)
                                anyDbChange = true
                            }
                        } else if (effectiveParentPhysicalPath.isNotEmpty() && album.parentPhysicalPath != effectiveParentPhysicalPath) {
                            album.id?.let { dao.updateAlbumParentPhysicalPath(it, effectiveParentPhysicalPath) }
                            album.parentPhysicalPath = effectiveParentPhysicalPath
                            anyDbChange = true
                        }

                        if (album?.id == null) continue
                        val albumId = album.id!!

                        val existingItems = dao.getItems(albumId)
                        val existingPaths = existingItems.map { it.fullPath.lowercase() }.toHashSet()
                        val existingItemsByPathLower = existingItems.associateBy { it.fullPath.lowercase() }
                        val existingItemsByRelPath = HashMap<String, VirtualAlbumItem>()
                        for (existingItem in existingItems) {
                            extractRelativeVaultPath(existingItem.fullPath, vaultRoot)?.lowercase()?.let { rel ->
                                existingItemsByRelPath[rel] = existingItem
                            }
                        }

                        val rawItems = innerBackup.items
                            ?: innerBackup.mediaPaths?.map { VirtualAlbumItemBackup(fullPath = it, addedTs = createdTs) }
                            ?: emptyList()

                        val itemsToInsert = ArrayList<VirtualAlbumItem>()
                        var hadNewlyUnresolved = false
                        var hadPathRewrite = false

                        for (entry in rawItems) {
                            try {
                                val effectiveFullPath = resolveVaultItemPath(vaultRoot, entry.relativeVaultPath, entry.fullPath)
                                    ?: entry.fullPath
                                    ?: continue
                                if (effectiveFullPath.isBlank()) continue
                                val lowerFullPath = effectiveFullPath.lowercase()
                                if (existingPaths.contains(lowerFullPath)) {
                                    val existingItem = existingItemsByPathLower[lowerFullPath]
                                    if (existingItem?.state == ITEM_STATE_UNRESOLVED && File(effectiveFullPath).exists()) {
                                        dao.markItemResolved(albumId, effectiveFullPath)
                                        anyDbChange = true
                                    }
                                    continue
                                }

                                val targetFile = File(effectiveFullPath)
                                val effectiveParentPath = targetFile.parent ?: effectiveFullPath.getParentPath()
                                val exists = targetFile.exists()
                                val isDormantState = entry.state == ITEM_STATE_ENCRYPTED || entry.state == ITEM_STATE_DECRYPTED
                                val hasUsableIdentity = (entry.mediaStoreId ?: 0L) > 0L ||
                                    !entry.fingerprint.isNullOrEmpty() ||
                                    (entry.fileSize ?: 0L) > 0L
                                if (!isDormantState && !exists && !hasUsableIdentity && targetFile.parentFile?.exists() == true) {
                                    hadNewlyUnresolved = true
                                    continue
                                }

                                val relKey = extractRelativeVaultPath(effectiveFullPath, vaultRoot)?.lowercase()
                                val matchingExisting = relKey?.let { existingItemsByRelPath[it] }
                                if (matchingExisting != null && matchingExisting.fullPath != effectiveFullPath) {
                                    dao.updateItemPathInAlbum(
                                        albumId,
                                        matchingExisting.fullPath,
                                        effectiveFullPath,
                                        targetFile.name,
                                        effectiveParentPath
                                    )
                                    if (exists && matchingExisting.state == ITEM_STATE_UNRESOLVED) {
                                        dao.markItemResolved(albumId, effectiveFullPath)
                                    }
                                    existingPaths.remove(matchingExisting.fullPath.lowercase())
                                    existingPaths.add(lowerFullPath)
                                    hadPathRewrite = true
                                    anyDbChange = true
                                } else {
                                    val resolvedState = when {
                                        isDormantState -> entry.state!!
                                        exists -> ITEM_STATE_OK
                                        else -> ITEM_STATE_UNRESOLVED
                                    }
                                    itemsToInsert.add(
                                        VirtualAlbumItem(
                                            id = null,
                                            albumId = albumId,
                                            fullPath = effectiveFullPath,
                                            filename = targetFile.name,
                                            parentPath = effectiveParentPath,
                                            addedTS = if ((entry.addedTs ?: 0L) > 0) entry.addedTs!! else System.currentTimeMillis(),
                                            mediaStoreId = entry.mediaStoreId ?: 0L,
                                            fileSize = entry.fileSize ?: 0L,
                                            lastModified = entry.lastModified ?: 0L,
                                            fingerprint = entry.fingerprint ?: "",
                                            state = resolvedState,
                                            folderInode = entry.folderInode ?: 0L,
                                            folderSignature = entry.folderSignature ?: "",
                                            capturedAtSec = entry.capturedAtSec ?: 0L
                                        )
                                    )
                                    existingPaths.add(lowerFullPath)
                                }
                            } catch (_: Exception) {
                                hadNewlyUnresolved = true
                            }
                        }

                        if (itemsToInsert.isNotEmpty()) {
                            dao.insertItems(itemsToInsert)
                            anyDbChange = true
                        }

                        val refreshedItems = dao.getItems(albumId)
                        val newlyMissingDbPaths = refreshedItems.filter {
                            it.state == ITEM_STATE_OK && !File(it.fullPath).exists() && File(it.fullPath).parentFile?.exists() == true
                        }.map { it.fullPath }
                        if (newlyMissingDbPaths.isNotEmpty()) {
                            // Kept, only marked as unresolved, so a renamed file can be found again.
                            newlyMissingDbPaths.forEach { dao.markItemUnresolved(albumId, it) }
                            hadNewlyUnresolved = true
                            anyDbChange = true
                        }

                        if (hadNewlyUnresolved || itemsToInsert.isNotEmpty() || hadPathRewrite) {
                            anyDbChange = true
                            saveAlbumToFile(context, albumId)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // 3. Reconcile hidden (vault) virtual albums in DB: clean up undecryptable rows from
        // other vaults and ensure active vault albums have their on-disk vault manifest.
        // Public virtual albums are NOT exported here so externally deleted backup files/folders
        // are never recreated automatically.
        val updatedDbAlbums = try {
            dao.getAllAlbums()
        } catch (_: Exception) {
            emptyList()
        }
        for (album in updatedDbAlbums) {
            if (!album.isHidden) continue
            val albumId = album.id ?: continue

            // A hidden virtual album whose stored name is a vault string that cannot be decrypted
            // with the current vault key belongs to a different vault (e.g. left over after a new
            // vault was created with a fresh VMK/KEK). Do not resurrect it in this vault's JSON/
            // folder as an empty album whose name shows up as a random "FVSTR:…" string. Remove
            // the stale row (and its on-disk manifest) instead. Only do this while the vault is
            // unlocked, otherwise every hidden name would look undecryptable.
            if (CryptoManager.isVaultUnlocked &&
                CryptoManager.isEncryptedVaultString(album.name) &&
                CryptoManager.decryptStringFromVault(album.name, "album") == null
            ) {
                try {
                    deleteAlbumFile(context, album)
                    dao.deleteItemsForAlbum(albumId)
                    dao.deleteAlbumById(albumId)
                    anyDbChange = true
                } catch (_: Exception) {
                }
                continue
            }

            val albumFile = getAlbumFile(context, album)
            if (!albumFile.exists() && CryptoManager.isVaultUnlocked) {
                saveAlbumToFile(context, albumId)
            }
        }
        if (anyDbChange) {
            invalidateDirectoryCache()
        }
        return anyDbChange
    }
}
