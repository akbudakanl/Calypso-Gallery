package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.FolderIdentity
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.VIRTUAL_ALBUM_PREFIX
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.helpers.decryptStringFromVault
import org.fossify.gallery.helpers.encryptStringForVault
import org.fossify.gallery.helpers.getVaultRecycleBin
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.isEncryptedVaultString
import org.fossify.gallery.helpers.isVaultMediaPath
import org.fossify.gallery.interfaces.VirtualAlbumsDao
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.ITEM_STATE_DECRYPTED
import org.fossify.gallery.models.ITEM_STATE_ENCRYPTED
import org.fossify.gallery.models.ITEM_STATE_UNRESOLVED
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.VirtualAlbum
import org.fossify.gallery.models.VirtualAlbumItem
import java.io.File

/*
 * Virtual albums: user created albums that only hold references (paths) to files which live in their
 * real folders. Adding a file to an album never copies or moves it, so albums do not use extra disk space.
 */

fun String.isVirtualAlbumPath() = startsWith(VIRTUAL_ALBUM_PREFIX) && removePrefix(VIRTUAL_ALBUM_PREFIX).toLongOrNull() != null

fun String.getVirtualAlbumId(): Long? = if (startsWith(VIRTUAL_ALBUM_PREFIX)) removePrefix(VIRTUAL_ALBUM_PREFIX).toLongOrNull() else null

fun getVirtualAlbumPath(albumId: Long) = "$VIRTUAL_ALBUM_PREFIX$albumId"

fun Directory.isVirtualAlbum() = path.isVirtualAlbumPath()

val Context.virtualAlbumsDB: VirtualAlbumsDao
    get() = GalleryDatabase.getInstance(applicationContext).VirtualAlbumsDao()

/**
 * Returns true only if hidden items are currently allowed to be viewed AND (if password protection is enabled)
 * the cryptographic vault has been unlocked in memory.
 */
fun Context.canAccessHiddenVirtualAlbums(): Boolean {
    return config.shouldShowHidden && (!config.isHiddenPasswordProtectionOn || CryptoManager.isVaultUnlocked)
}

fun Context.isHiddenVirtualAlbumPath(path: String): Boolean {
    val albumId = path.getVirtualAlbumId() ?: return false
    return try {
        virtualAlbumsDB.getAlbumById(albumId)?.isHidden == true
    } catch (_: Exception) {
        false
    }
}

fun Context.isPathHiddenOrEncrypted(path: String): Boolean {
    if (path.isEmpty()) return false
    if (path.isVirtualAlbumPath()) {
        return isHiddenVirtualAlbumPath(path)
    }
    if (CryptoManager.isEncryptedFile(path)) {
        return true
    }
    if (path.contains("/.")) {
        return true
    }
    val parent = path.getParentPath()
    if (parent.isNotEmpty() && CryptoManager.isEncryptedFolderPath(this, parent)) {
        return true
    }
    return false
}

fun Context.getResolvedVirtualAlbumName(album: VirtualAlbum): String? {
    if (!album.isHidden) {
        return album.name
    }
    if (!canAccessHiddenVirtualAlbums()) {
        return null
    }
    return CryptoManager.decryptStringFromVault(album.name, "album") ?: album.name
}

/** Existing, not deleted files of the given virtual album path, newest additions first. Must run on a background thread. */
fun Context.getVirtualAlbumPaths(albumPath: String): ArrayList<String> {
    val albumId = albumPath.getVirtualAlbumId() ?: return ArrayList()
    return try {
        val album = virtualAlbumsDB.getAlbumById(albumId) ?: return ArrayList()
        if (album.isHidden && !canAccessHiddenVirtualAlbums()) {
            return ArrayList()
        }
        val items = virtualAlbumsDB.getItems(albumId)
        if (items.isEmpty()) return ArrayList()

        val deletedPaths = virtualAlbumsDB.getDeletedPathsForAlbum(albumId).toHashSet()
        val canAccessHidden = canAccessHiddenVirtualAlbums()
        val validPaths = ArrayList<String>()
        val newlyMissingPaths = ArrayList<String>()
        var repairedAny = false

        for (item in items) {
            if (item.state == ITEM_STATE_ENCRYPTED || item.state == ITEM_STATE_DECRYPTED) continue
            val path = item.fullPath
            if (deletedPaths.contains(path) || path.startsWith(recycleBinPath)) continue
            // Only vault content is protected inside a virtual album. Files coming from a hidden
            // folder are explicitly referenced by the user, so they stay visible.
            val isVaultContent = CryptoManager.isVaultMediaPath(this, path)
            if (!album.isHidden && isVaultContent) continue
            if (isVaultContent && !canAccessHidden) continue
            val file = File(path)
            if (file.exists()) {
                if (item.state == ITEM_STATE_UNRESOLVED) {
                    if (!doesVirtualItemMatchDiskFile(item, file, album.isHidden)) continue
                    try {
                        virtualAlbumsDB.markItemResolved(albumId, path)
                        repairedAny = true
                    } catch (_: Exception) {
                    }
                }
                validPaths.add(path)
            } else if (item.state != ITEM_STATE_UNRESOLVED) {
                // The item stays in the album and is only marked as unresolved, so it can be
                // repaired later in the background repair pass.
                newlyMissingPaths.add(path)
            }
        }

        if (newlyMissingPaths.isNotEmpty() || repairedAny) {
            ensureBackgroundThread {
                if (newlyMissingPaths.isNotEmpty()) {
                    VirtualAlbumSyncManager.pruneMissingItems(this, albumId, newlyMissingPaths)
                } else {
                    VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
                }
            }
        }

        validPaths
    } catch (e: Exception) {
        ArrayList()
    }
}

internal fun Context.doesVirtualItemMatchDiskFile(
    item: VirtualAlbumItem,
    file: File,
    isHiddenAlbum: Boolean
): Boolean {
    if (!file.isFile) return false
    val path = file.absolutePath
    return when {
        CryptoManager.isEncryptedFile(path) -> {
            if (!isHiddenAlbum || !canAccessHiddenVirtualAlbums() || !CryptoManager.isVaultUnlocked) {
                false
            } else if (item.fileSize <= 0L) {
                true
            } else {
                val meta = CryptoManager.readMetadata(file)
                meta != null && (meta.originalSize == item.fileSize || file.length() == item.fileSize)
            }
        }
        item.fingerprint.isNotEmpty() -> {
            val diskId = MediaIdentity.of(file)
            diskId.size == item.fileSize && diskId.fingerprint == item.fingerprint
        }
        item.fileSize > 0L -> file.length() == item.fileSize
        else -> true
    }
}

/**
 * Resolves the `Medium` list for a virtual album without requiring encrypted `.fenc` or hidden files
 * to be stored in the `media` SQLite table.
 */
fun Context.getVirtualAlbumMedia(
    albumId: Long,
    getVideoDurations: Boolean = false,
    preloadedAlbum: VirtualAlbum? = null,
    preloadedFavoritePaths: Set<String>? = null,
    preloadedDeletedOrigPaths: Set<String>? = null
): ArrayList<Medium> {
    return try {
        val album = preloadedAlbum ?: virtualAlbumsDB.getAlbumById(albumId) ?: return ArrayList()
        val canAccessHidden = canAccessHiddenVirtualAlbums()
        if (album.isHidden && !canAccessHidden) {
            return ArrayList()
        }

        val items = virtualAlbumsDB.getItems(albumId)
        if (items.isEmpty()) return ArrayList()

        val cachedMediaByPath = virtualAlbumsDB.getValidMedia(albumId).associateBy { it.path }
        val deletedPaths = virtualAlbumsDB.getDeletedPathsForAlbum(albumId).toHashSet()
        val favoritePaths = preloadedFavoritePaths ?: try {
            favoritesDB.getValidFavoritePaths().toHashSet()
        } catch (_: Exception) {
            emptySet()
        }

        val deletedOrigPaths = preloadedDeletedOrigPaths ?: try {
            mediaDB.getDeletedMedia().mapTo(HashSet()) { it.path.removePrefix(RECYCLE_BIN).lowercase() }
        } catch (_: Exception) {
            emptySet()
        }

        val result = ArrayList<Medium>()
        val newlyMissingPaths = ArrayList<String>()
        var repairedAny = false
        val vaultRecycleBinPrefix = CryptoManager.getVaultRecycleBin(this)?.absolutePath?.trimEnd('/')

        for (item in items) {
            if (item.state == ITEM_STATE_ENCRYPTED || item.state == ITEM_STATE_DECRYPTED) continue
            val path = item.fullPath
            val isInVaultBin = vaultRecycleBinPrefix != null &&
                (path == vaultRecycleBinPrefix || path.startsWith("$vaultRecycleBinPrefix/"))
            if (deletedPaths.contains(path) ||
                path.lowercase() in deletedOrigPaths ||
                path.startsWith(recycleBinPath) ||
                isInVaultBin
            ) {
                continue
            }

            val file = File(path)
            if (!file.exists()) {
                if (item.state != ITEM_STATE_UNRESOLVED) {
                    newlyMissingPaths.add(path)
                }
                continue
            } else if (item.state == ITEM_STATE_UNRESOLVED) {
                if (!doesVirtualItemMatchDiskFile(item, file, album.isHidden)) continue
                try {
                    virtualAlbumsDB.markItemResolved(albumId, path)
                    repairedAny = true
                } catch (_: Exception) {
                }
            }

            if (CryptoManager.isEncryptedFile(path)) {
                if (!album.isHidden || !canAccessHidden || !CryptoManager.isVaultUnlocked) continue
                val meta = CryptoManager.readMetadata(file) ?: continue
                result.add(
                    Medium(
                        id = null,
                        name = meta.originalName,
                        path = path,
                        parentPath = file.parent ?: item.parentPath,
                        modified = meta.lastModified,
                        taken = meta.dateTaken,
                        size = meta.originalSize,
                        type = meta.mediaType,
                        videoDuration = meta.videoDuration,
                        isFavorite = favoritePaths.contains(path),
                        deletedTS = 0L,
                        mediaStoreId = 0L
                    )
                )
                continue
            }

            // Only vault content is protected inside a virtual album. Files coming from a hidden
            // folder are explicitly referenced by the user, so they stay visible.
            val isVaultContent = CryptoManager.isVaultMediaPath(this, path)
            if (!album.isHidden && isVaultContent) continue
            if (isVaultContent && !canAccessHidden) continue

            val cached = cachedMediaByPath[path]
            if (cached != null) {
                cached.isFavorite = favoritePaths.contains(path)
                result.add(cached)
            } else if (file.isFile) {
                val isImage = path.isImageFast()
                val isVideo = if (isImage) false else path.isVideoFast()
                val isGif = if (isImage || isVideo) false else path.isGif()
                val isRaw = if (isImage || isVideo || isGif) false else path.isRawFast()
                val isSvg = if (isImage || isVideo || isGif || isRaw) false else path.isSvg()
                if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) continue

                val type = when {
                    isGif -> TYPE_GIFS
                    isRaw -> TYPE_RAWS
                    isVideo -> TYPE_VIDEOS
                    isSvg -> TYPE_SVGS
                    else -> TYPE_IMAGES
                }
                val lastMod = file.lastModified()
                val duration = if (isVideo && getVideoDurations) (getDuration(path) ?: 0) else 0
                result.add(
                    Medium(
                        id = null,
                        name = file.name,
                        path = path,
                        parentPath = file.parent ?: item.parentPath,
                        modified = lastMod,
                        taken = lastMod,
                        size = file.length(),
                        type = type,
                        videoDuration = duration,
                        isFavorite = favoritePaths.contains(path),
                        deletedTS = 0L,
                        mediaStoreId = 0L
                    )
                )
            }
        }

        if (newlyMissingPaths.isNotEmpty() || repairedAny) {
            ensureBackgroundThread {
                if (newlyMissingPaths.isNotEmpty()) {
                    VirtualAlbumSyncManager.pruneMissingItems(this, albumId, newlyMissingPaths)
                } else {
                    VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
                }
            }
        }

        result
    } catch (e: Exception) {
        ArrayList()
    }
}

fun Context.getVirtualAlbumName(albumPath: String): String? {
    val albumId = albumPath.getVirtualAlbumId() ?: return null
    return try {
        val album = virtualAlbumsDB.getAlbumById(albumId) ?: return null
        getResolvedVirtualAlbumName(album)
    } catch (e: Exception) {
        null
    }
}

fun Context.getVirtualAlbums(): List<VirtualAlbum> {
    return try {
        virtualAlbumsDB.getAllAlbums()
    } catch (e: Exception) {
        emptyList()
    }
}

fun Context.virtualAlbumNameExists(name: String, ignoreAlbumId: Long? = null): Boolean {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return false
    return try {
        val allAlbums = virtualAlbumsDB.getAllAlbums()
        allAlbums.any { album ->
            if (album.id == ignoreAlbumId) return@any false
            val resolved = if (album.isHidden) {
                CryptoManager.decryptStringFromVault(album.name, "album") ?: album.name
            } else {
                album.name
            }
            resolved.equals(trimmed, ignoreCase = true)
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * The folder the items of an album mostly came from.
 *
 * This is only a hint, for example to preselect the folder of the copy dialog. It does not
 * restrict the album: an album may hold items from any number of physical folders, exactly like
 * the favorites do.
 */
fun Context.getVirtualAlbumOwnerPhysicalPath(albumId: Long): String {
    val album = virtualAlbumsDB.getAlbumById(albumId) ?: return ""
    if (album.parentPhysicalPath.isNotEmpty()) {
        return album.parentPhysicalPath
    }
    val items = virtualAlbumsDB.getItems(albumId)
    val firstItemParent = items.firstOrNull()?.parentPath ?: ""
    if (firstItemParent.isNotEmpty()) {
        album.parentPhysicalPath = firstItemParent
        ensureBackgroundThread {
            try {
                virtualAlbumsDB.updateAlbumParentPhysicalPath(albumId, firstItemParent)
            } catch (_: Exception) {}
        }
        return firstItemParent
    }
    return ""
}

fun Context.resolveEffectiveSourcePhysicalPath(sourcePath: String): String {
    if (sourcePath.isVirtualAlbumPath()) {
        val albumId = sourcePath.getVirtualAlbumId() ?: return ""
        return getVirtualAlbumOwnerPhysicalPath(albumId)
    }
    return sourcePath
}

/** @return id of the new album, or -1 if the name is blank or already used */
fun Context.createVirtualAlbum(name: String, isHidden: Boolean = false, parentPhysicalPath: String = ""): Long {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || virtualAlbumNameExists(trimmed)) {
        return -1L
    }

    val storedName = if (isHidden) {
        CryptoManager.encryptStringForVault(this, trimmed, "album")
    } else {
        trimmed
    }

    val albumId = try {
        virtualAlbumsDB.insertAlbum(
            VirtualAlbum(
                id = null,
                name = storedName,
                createdTs = System.currentTimeMillis(),
                thumbnail = "",
                isHidden = isHidden,
                parentPhysicalPath = parentPhysicalPath
            )
        )
    } catch (e: Exception) {
        -1L
    }

    if (albumId > 0) {
        VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
    }
    return albumId
}

fun Context.renameVirtualAlbum(albumId: Long, newName: String): Boolean {
    val trimmed = newName.trim()
    if (trimmed.isEmpty() || virtualAlbumNameExists(trimmed, albumId)) {
        return false
    }

    return try {
        val oldAlbum = virtualAlbumsDB.getAlbumById(albumId) ?: return false
        val storedName = if (oldAlbum.isHidden) {
            CryptoManager.encryptStringForVault(this, trimmed, "album")
        } else {
            trimmed
        }
        virtualAlbumsDB.renameAlbum(albumId, storedName)
        VirtualAlbumSyncManager.onAlbumRenamed(this, albumId, oldAlbum)
        true
    } catch (e: Exception) {
        false
    }
}

/** Deletes only the album and its references, the files stay untouched. */
fun Context.deleteVirtualAlbum(albumId: Long) {
    try {
        val album = virtualAlbumsDB.getAlbumById(albumId)
        if (album != null) {
            VirtualAlbumSyncManager.deleteAlbumFile(this, album)
        }
        virtualAlbumsDB.deleteAlbumById(albumId)
        directoryDB.deleteDirPath(getVirtualAlbumPath(albumId))
        VirtualAlbumSyncManager.invalidateDirectoryCache()
    } catch (ignored: Exception) {
    }
}

/**
 * Adds references to the given media to the album. Nothing is copied on disk.
 *
 * An album may hold items from any number of physical folders, exactly like the favorites. The
 * folder the items came from is only remembered as a hint for the copy dialog.
 *
 * Vault content is NEVER inserted into `mediaDB`. If any added item is vault content (or
 * `forceMarkAlbumHidden` is true), the album itself is marked hidden with an encrypted name.
 * Files coming from a hidden folder are not vault content: they are explicitly referenced by the
 * user and stay visible in the album.
 *
 * @return how many items were not already in the album
 */
fun Context.addMediaToVirtualAlbum(albumId: Long, media: Collection<Medium>, forceMarkAlbumHidden: Boolean = false): Int {
    val validMedia = media.filter { !it.path.startsWith(recycleBinPath) }.distinctBy { it.path }
    if (validMedia.isEmpty()) {
        return 0
    }

    return try {
        val album = virtualAlbumsDB.getAlbumById(albumId)

        // Remember where the items came from, but never use it to reject items.
        if (album != null && album.parentPhysicalPath.isEmpty()) {
            val firstParent = validMedia.firstOrNull()?.parentPath ?: ""
            if (firstParent.isNotEmpty()) {
                album.parentPhysicalPath = firstParent
                virtualAlbumsDB.updateAlbumParentPhysicalPath(albumId, firstParent)
            }
        }

        val allowedMedia = validMedia
        val existingPaths = virtualAlbumsDB.getItems(albumId).map { it.fullPath.lowercase() }.toHashSet()
        val alreadyInAlbum = allowedMedia.count { existingPaths.contains(it.path.lowercase()) }
        var hasVaultItem = forceMarkAlbumHidden

        allowedMedia.forEach { medium ->
            if (CryptoManager.isVaultMediaPath(this, medium.path)) {
                hasVaultItem = true
                // Ensure no plaintext metadata of encrypted files exists in mediaDB
                if (CryptoManager.isEncryptedFile(medium.path)) {
                    mediaDB.deleteMediumPath(medium.path)
                }
            } else {
                // Only cache public files in mediaDB
                mediaDB.insert(medium.copy(id = null, deletedTS = 0L))
            }
        }

        if (hasVaultItem) {
            val currentAlbum = virtualAlbumsDB.getAlbumById(albumId)
            if (currentAlbum != null && (!currentAlbum.isHidden || !CryptoManager.isEncryptedVaultString(currentAlbum.name))) {
                val plainName = CryptoManager.decryptStringFromVault(currentAlbum.name, "album") ?: currentAlbum.name
                val encName = CryptoManager.encryptStringForVault(this, plainName, "album")
                virtualAlbumsDB.updateAlbumVisibilityAndName(albumId, true, encName)
                directoryDB.deleteDirPath(getVirtualAlbumPath(albumId))
            }
        }

        val now = System.currentTimeMillis()
        val items = allowedMedia.map { medium ->
            // Always store the on-disk filename (e.g. .fv_xxx.fenc), never decrypted originalName
            val identity = MediaIdentity.of(this, medium)
            VirtualAlbumItem(
                id = null,
                albumId = albumId,
                fullPath = medium.path,
                filename = medium.path.getFilenameFromPath(),
                parentPath = medium.path.getParentPath(),
                addedTS = now,
                mediaStoreId = identity.mediaStoreId,
                fileSize = identity.size,
                lastModified = identity.lastModified,
                fingerprint = identity.fingerprint
            )
        }
        virtualAlbumsDB.insertItems(items)
        VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
        allowedMedia.size - alreadyInAlbum
    } catch (e: Exception) {
        0
    }
}

/**
 * Adds references to the given `FileDirItem`s to the virtual album without copying anything on disk
 * and without writing hidden/encrypted item metadata to `mediaDB`.
 * @return how many items were not already in the album
 */
fun Context.addFileDirItemsToVirtualAlbum(
    albumId: Long,
    fileDirItems: Collection<FileDirItem>,
    forceMarkAlbumHidden: Boolean = false
): Int {
    val validItems = fileDirItems.filter { !it.isDirectory && !it.path.startsWith(recycleBinPath) }.distinctBy { it.path }
    if (validItems.isEmpty()) {
        return 0
    }

    return try {
        val album = virtualAlbumsDB.getAlbumById(albumId)

        // Remember where the items came from, but never use it to reject items.
        if (album != null && album.parentPhysicalPath.isEmpty()) {
            val firstParent = validItems.firstOrNull()?.path?.getParentPath() ?: ""
            if (firstParent.isNotEmpty()) {
                album.parentPhysicalPath = firstParent
                virtualAlbumsDB.updateAlbumParentPhysicalPath(albumId, firstParent)
            }
        }

        val allowedItems = validItems
        val existingPaths = virtualAlbumsDB.getItems(albumId).map { it.fullPath.lowercase() }.toHashSet()
        val alreadyInAlbum = allowedItems.count { existingPaths.contains(it.path.lowercase()) }
        var hasVaultItem = forceMarkAlbumHidden

        allowedItems.forEach { item ->
            val path = item.path
            if (CryptoManager.isVaultMediaPath(this, path)) {
                hasVaultItem = true
                if (CryptoManager.isEncryptedFile(path)) {
                    mediaDB.deleteMediumPath(path)
                }
            }
        }

        if (hasVaultItem) {
            val currentAlbum = virtualAlbumsDB.getAlbumById(albumId)
            if (currentAlbum != null && (!currentAlbum.isHidden || !CryptoManager.isEncryptedVaultString(currentAlbum.name))) {
                val plainName = CryptoManager.decryptStringFromVault(currentAlbum.name, "album") ?: currentAlbum.name
                val encName = CryptoManager.encryptStringForVault(this, plainName, "album")
                virtualAlbumsDB.updateAlbumVisibilityAndName(albumId, true, encName)
                directoryDB.deleteDirPath(getVirtualAlbumPath(albumId))
            }
        }

        val now = System.currentTimeMillis()
        val folderSession = FolderIdentity.CaptureSession()
        val items = allowedItems.map { item ->
            val identity = MediaIdentity.ofWithFolder(this, File(item.path), item.mediaStoreId, folderSession)
            VirtualAlbumItem(
                id = null,
                albumId = albumId,
                fullPath = item.path,
                filename = item.path.getFilenameFromPath(),
                parentPath = item.path.getParentPath(),
                addedTS = now,
                mediaStoreId = identity.mediaStoreId,
                fileSize = identity.size,
                lastModified = identity.lastModified,
                fingerprint = identity.fingerprint,
                folderInode = identity.folderInode,
                folderSignature = identity.folderSignature,
                capturedAtSec = identity.capturedAtSec
            )
        }
        virtualAlbumsDB.insertItems(items)
        VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
        MediaIndexCoordinator.markFoldersDirty(listOf(getVirtualAlbumPath(albumId)))
        allowedItems.size - alreadyInAlbum
    } catch (e: Exception) {
        0
    }
}

fun Context.removePathsFromVirtualAlbum(albumId: Long, paths: Collection<String>) {
    try {
        paths.forEach { virtualAlbumsDB.removeItemFromAlbum(albumId, it) }
        VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
        MediaIndexCoordinator.markFoldersDirty(listOf(getVirtualAlbumPath(albumId)))
    } catch (ignored: Exception) {
    }
}

fun Context.updateVirtualAlbumItemPath(oldPath: String, newPath: String) {
    try {
        val albumIds = virtualAlbumsDB.getAlbumIdsWithItem(oldPath)
        virtualAlbumsDB.updateItemPath(oldPath, newPath, newPath.getFilenameFromPath(), newPath.getParentPath())
        // The file moved, so its identity has to be captured again at the new location.
        updateReferenceIdentitiesForPath(newPath)
        albumIds.forEach { VirtualAlbumSyncManager.saveAlbumToFile(this, it) }
    } catch (ignored: Exception) {
    }
}

fun Context.deleteVirtualAlbumItemFromAllAlbums(path: String) {
    try {
        val albumIds = virtualAlbumsDB.getAlbumIdsWithItem(path)
        virtualAlbumsDB.removeItemFromAllAlbums(path)
        VirtualAlbumSyncManager.invalidateDirectoryCache()
        albumIds.forEach { VirtualAlbumSyncManager.saveAlbumToFile(this, it) }
    } catch (ignored: Exception) {
    }
}

private data class VirtualAlbumDirCacheKey(
    val cacheVersion: Long,
    val isSecretVaultView: Boolean,
    val getVideosOnly: Boolean,
    val getImagesOnly: Boolean,
    val canAccessHidden: Boolean,
    val isVaultUnlocked: Boolean,
    val filterMedia: Int,
    val directorySorting: Int,
    val albumCoversJson: String,
    val deletedCount: Int,
    val albumsSignature: Int
)

private val virtualAlbumDirCacheLock = Any()
private var cachedVirtualAlbumDirKey: VirtualAlbumDirCacheKey? = null
private var cachedVirtualAlbumDirs: List<Directory> = emptyList()
private var cachedVirtualAlbumDirsAtMs: Long = 0L
private const val VIRTUAL_ALBUM_DIR_CACHE_TTL_MS = 15_000L

/**
 * Builds the main screen entries of all virtual albums, so they can be shown immediately.
 * Hidden virtual albums are strictly omitted unless `canAccessHiddenVirtualAlbums()` is true.
 * Empty albums are included too, otherwise a freshly created album could not be found. Must run on a background thread.
 */
fun Context.getVirtualAlbumDirectories(
    isSecretVaultView: Boolean = false,
    getVideosOnly: Boolean = false,
    getImagesOnly: Boolean = false,
    syncFromDisk: Boolean = true
): ArrayList<Directory> {
    var albums = getVirtualAlbums()
    if (syncFromDisk || (isSecretVaultView && CryptoManager.isVaultUnlocked && albums.none { it.isHidden })) {
        VirtualAlbumSyncManager.syncVirtualAlbums(this)
        albums = getVirtualAlbums()
    }
    val directories = ArrayList<Directory>()
    if (albums.isEmpty()) {
        return directories
    }

    val canAccessHidden = canAccessHiddenVirtualAlbums()
    val filterMedia = config.filterMedia
    val deletedOrigPaths = try {
        mediaDB.getDeletedMedia().mapTo(HashSet()) { it.path.removePrefix(RECYCLE_BIN).lowercase() }
    } catch (_: Exception) {
        emptySet()
    }

    var albumsSig = 1
    for (album in albums) {
        val id = album.id ?: continue
        val path = getVirtualAlbumPath(id)
        albumsSig = 31 * albumsSig + id.hashCode()
        albumsSig = 31 * albumsSig + album.name.hashCode()
        albumsSig = 31 * albumsSig + album.isHidden.hashCode()
        albumsSig = 31 * albumsSig + config.getFolderSorting(path)
        albumsSig = 31 * albumsSig + config.getFolderGrouping(path)
    }

    val cacheKey = VirtualAlbumDirCacheKey(
        cacheVersion = VirtualAlbumSyncManager.getDirectoryCacheVersion(),
        isSecretVaultView = isSecretVaultView,
        getVideosOnly = getVideosOnly,
        getImagesOnly = getImagesOnly,
        canAccessHidden = canAccessHidden,
        isVaultUnlocked = CryptoManager.isVaultUnlocked,
        filterMedia = filterMedia,
        directorySorting = config.directorySorting,
        albumCoversJson = config.albumCovers,
        deletedCount = deletedOrigPaths.size,
        albumsSignature = albumsSig
    )

    val now = System.currentTimeMillis()
    synchronized(virtualAlbumDirCacheLock) {
        if (cachedVirtualAlbumDirKey == cacheKey && now - cachedVirtualAlbumDirsAtMs < VIRTUAL_ALBUM_DIR_CACHE_TTL_MS) {
            return cachedVirtualAlbumDirs.mapTo(ArrayList(cachedVirtualAlbumDirs.size)) { it.copy() }
        }
    }

    val albumCovers = config.parseAlbumCovers()
    val includedFolders = config.includedFolders
    val getProperFileSize = true
    val hiddenString = getString(R.string.hidden)
    val mediaFetcher = MediaFetcher(this)
    val favoritePaths = try {
        favoritesDB.getValidFavoritePaths().toHashSet()
    } catch (_: Exception) {
        emptySet()
    }

    for (album in albums) {
        val albumId = album.id ?: continue
        if (isSecretVaultView) {
            if (!album.isHidden || !canAccessHidden) {
                continue
            }
        } else if (album.isHidden && !canAccessHidden) {
            continue
        }
        val resolvedName = getResolvedVirtualAlbumName(album) ?: continue
        val path = getVirtualAlbumPath(albumId)
        val rawMedia = getVirtualAlbumMedia(
            albumId = albumId,
            getVideoDurations = false,
            preloadedAlbum = album,
            preloadedFavoritePaths = favoritePaths,
            preloadedDeletedOrigPaths = deletedOrigPaths
        )
        val media = (when {
            getVideosOnly -> rawMedia.filter { it.type == TYPE_VIDEOS }
            getImagesOnly -> rawMedia.filter { it.type == TYPE_IMAGES }
            else -> rawMedia.filter {
                (filterMedia and TYPE_IMAGES != 0 && it.type == TYPE_IMAGES) ||
                    (filterMedia and TYPE_VIDEOS != 0 && it.type == TYPE_VIDEOS) ||
                    (filterMedia and TYPE_GIFS != 0 && it.type == TYPE_GIFS) ||
                    (filterMedia and TYPE_RAWS != 0 && it.type == TYPE_RAWS) ||
                    (filterMedia and TYPE_SVGS != 0 && it.type == TYPE_SVGS) ||
                    (filterMedia and TYPE_PORTRAITS != 0 && it.type == TYPE_PORTRAITS)
            }
        }) as ArrayList<Medium>

        val directory = if (media.isEmpty()) {
            Directory(null, path, "", resolvedName, 0, album.createdTs, album.createdTs, 0L, LOCATION_INTERNAL, 0, "")
        } else {
            mediaFetcher.sortMedia(media, config.getFolderSorting(path), enableRandom = false)
            createDirectoryFromMedia(
                path = path,
                curMedia = media,
                albumCovers = albumCovers,
                hiddenString = hiddenString,
                includedFolders = includedFolders,
                getProperFileSize = getProperFileSize,
                noMediaFolders = ArrayList()
            )
        }

        directory.name = resolvedName
        directories.add(directory)
    }

    synchronized(virtualAlbumDirCacheLock) {
        if (VirtualAlbumSyncManager.getDirectoryCacheVersion() == cacheKey.cacheVersion) {
            cachedVirtualAlbumDirKey = cacheKey
            cachedVirtualAlbumDirs = directories.map { it.copy() }
            cachedVirtualAlbumDirsAtMs = System.currentTimeMillis()
        }
    }

    return directories
}
