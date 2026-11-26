package org.fossify.gallery.extensions

import android.content.Context
import android.os.Process
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.RandomOrderManager
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.ShareMetadataSanitizer
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.helpers.getVaultRecycleBin
import org.fossify.gallery.helpers.getVaultRecycleBinCount
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.isVaultMediaPath
import org.fossify.gallery.helpers.isVaultRecycleBinPath
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.io.File
import java.util.Locale

fun Context.getCachedDirectories(
    getVideosOnly: Boolean = false,
    getImagesOnly: Boolean = false,
    forceShowHidden: Boolean = false,
    forceShowExcluded: Boolean = false,
    includeVirtualAlbums: Boolean = false,
    isSecretVaultView: Boolean = false,
    ignoreMediaFilter: Boolean = false,
    callback: (ArrayList<Directory>) -> Unit,
) {
    ensureBackgroundThread {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
        } catch (ignored: Exception) {
        }

        val directories = try {
            directoryDB.getAll() as ArrayList<Directory>
        } catch (e: Exception) {
            ArrayList()
        }

        if (!config.showRecycleBinAtFolders) {
            directories.removeAll { it.isRecycleBin() }
        }

        // virtual albums are always built from their own tables, never from the directories cache
        directories.removeAll { it.isVirtualAlbum() }

        val shouldShowHidden = config.shouldShowHidden || forceShowHidden
        val showExcludedInScope = !isSecretVaultView && (config.temporarilyShowExcluded || forceShowExcluded)
        val showHiddenFoldersInScope = !isSecretVaultView && (config.temporarilyShowHiddenFolders || forceShowHidden)
        var filteredDirectories = if (directories.isEmpty()) {
            ArrayList()
        } else {
            val excludedPaths = if (showExcludedInScope) {
                HashSet()
            } else {
                config.excludedFolders
            }

            val includedPaths = config.includedFolders

            val folderNoMediaStatuses = HashMap<String, Boolean>()
            val noMediaFolders = getNoMediaFoldersSync()
            noMediaFolders.forEach { folder ->
                folderNoMediaStatuses["$folder/$NOMEDIA"] = true
            }

            val visibleDirectories = directories.filter {
                if (ShareMetadataSanitizer.isShareTempPath(it.path)) {
                    false
                } else if (CryptoManager.isEncryptedFolderPath(this, it.path)) {
                    shouldShowHidden && CryptoManager.isVaultUnlocked
                } else {
                    it.path.shouldFolderBeVisible(
                        excludedPaths = excludedPaths,
                        includedPaths = includedPaths,
                        showHidden = showHiddenFoldersInScope,
                        folderNoMediaStatuses = folderNoMediaStatuses
                    ) { path, hasNoMedia ->
                        folderNoMediaStatuses[path] = hasNoMedia
                    }
                }
            } as ArrayList<Directory>

            val filterMedia = config.filterMedia
            (when {
                ignoreMediaFilter -> visibleDirectories
                getVideosOnly -> visibleDirectories.filter { it.types and TYPE_VIDEOS != 0 }
                getImagesOnly -> visibleDirectories.filter { it.types and TYPE_IMAGES != 0 }
                else -> visibleDirectories.filter {
                    (filterMedia and TYPE_IMAGES != 0 && it.types and TYPE_IMAGES != 0)
                            || (filterMedia and TYPE_VIDEOS != 0 && it.types and TYPE_VIDEOS != 0)
                            || (filterMedia and TYPE_GIFS != 0 && it.types and TYPE_GIFS != 0)
                            || (filterMedia and TYPE_RAWS != 0 && it.types and TYPE_RAWS != 0)
                            || (filterMedia and TYPE_SVGS != 0 && it.types and TYPE_SVGS != 0)
                            || (filterMedia and TYPE_PORTRAITS != 0 && it.types and TYPE_PORTRAITS != 0)
                }
            }) as ArrayList<Directory>
        }

        if (shouldShowHidden || showHiddenFoldersInScope) {
            val hiddenString = resources.getString(R.string.hidden)
            filteredDirectories.forEach {
                it.name = it.name.removeSuffix(hiddenString).trim()
            }
        }

        if (isSecretVaultView) {
            filteredDirectories = ArrayList(filteredDirectories.filter {
                isEncryptedVaultAlbum(it.path)
            })
            if (config.showRecycleBinAtFolders && CryptoManager.isVaultUnlocked) {
                val binFolder = CryptoManager.getVaultRecycleBin(this)
                val binCount = CryptoManager.getVaultRecycleBinCount(this)
                if (binFolder != null && binCount > 0) {
                    val binDir = Directory(
                        id = null,
                        path = binFolder.absolutePath,
                        tmb = "",
                        name = getString(org.fossify.commons.R.string.recycle_bin),
                        mediaCnt = binCount,
                        modified = binFolder.lastModified(),
                        taken = binFolder.lastModified(),
                        size = 0L,
                        location = LOCATION_INTERNAL,
                        types = TYPE_IMAGES or TYPE_VIDEOS,
                        sortValue = ""
                    )
                    if (config.showRecycleBinLast) {
                        filteredDirectories.add(binDir)
                    } else {
                        filteredDirectories.add(0, binDir)
                    }
                }
            }
        } else {
            filteredDirectories.removeAll { CryptoManager.isVaultRecycleBinPath(this, it.path) }
        }

        val clone = ArrayList(filteredDirectories)
        val result = ArrayList(clone.distinctBy { it.path.getDistinctPath() })
        if (includeVirtualAlbums) {
            result.addAll(
                getVirtualAlbumDirectories(
                    isSecretVaultView = isSecretVaultView,
                    getVideosOnly = getVideosOnly,
                    getImagesOnly = getImagesOnly,
                    syncFromDisk = false
                )
            )
        }

        callback(result)
        if (filteredDirectories.isNotEmpty()) {
            removeInvalidDBDirectories(filteredDirectories)
        }
    }
}

fun Context.getCachedMedia(
    path: String,
    getVideosOnly: Boolean = false,
    getImagesOnly: Boolean = false,
    enableRandom: Boolean = false,
    callback: (ArrayList<ThumbnailItem>) -> Unit
) {
    ensureBackgroundThread {
        val mediaFetcher = MediaFetcher(this)
        val shouldShowHidden = config.shouldShowHidden
        var media = ArrayList<Medium>()
        var loadedFromFastAllMedia = false

        if (path.isEmpty()) {
            val cachedDirs = try {
                directoryDB.getAll()
            } catch (_: Exception) {
                emptyList()
            }
            if (cachedDirs.isNotEmpty()) {
                val showExcluded = config.temporarilyShowExcluded
                val excludedPaths = if (showExcluded) {
                    HashSet()
                } else {
                    config.excludedFolders
                }
                val includedPaths = config.includedFolders
                val folderNoMediaStatuses = HashMap<String, Boolean>()
                val noMediaFolders = getNoMediaFoldersSync()
                noMediaFolders.forEach { folder ->
                    folderNoMediaStatuses["$folder/$NOMEDIA"] = true
                }

                val visibleFoldersLower = HashSet<String>(cachedDirs.size)
                for (dir in cachedDirs) {
                    val dirPath = dir.path
                    if (dir.isRecycleBin() ||
                        dir.areFavorites() ||
                        dir.isVirtualAlbum() ||
                        config.isFolderProtected(dirPath) ||
                        ShareMetadataSanitizer.isShareTempPath(dirPath)
                    ) {
                        continue
                    }
                    val isVisible = if (CryptoManager.isEncryptedFolderPath(this, dirPath)) {
                        shouldShowHidden && CryptoManager.isVaultUnlocked &&
                            !CryptoManager.isVaultRecycleBinPath(this, dirPath)
                    } else {
                        dirPath.shouldFolderBeVisible(
                            excludedPaths = excludedPaths,
                            includedPaths = includedPaths,
                            showHidden = config.temporarilyShowHiddenFolders,
                            folderNoMediaStatuses = folderNoMediaStatuses
                        ) { noMediaPath, hasNoMedia ->
                            folderNoMediaStatuses[noMediaPath] = hasNoMedia
                        }
                    }
                    if (isVisible) {
                        visibleFoldersLower.add(dirPath.lowercase(Locale.getDefault()))
                    }
                }

                if (visibleFoldersLower.isNotEmpty()) {
                    val allCachedMedia = try {
                        mediaDB.getAllMedia()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    for (item in allCachedMedia) {
                        val parentLower = item.parentPath.lowercase(Locale.getDefault())
                        if (parentLower in visibleFoldersLower ||
                            (item.type == TYPE_PORTRAITS && File(item.parentPath).parent?.lowercase(Locale.getDefault()) in visibleFoldersLower)
                        ) {
                            media.add(item)
                        }
                    }
                    if (media.isNotEmpty()) {
                        loadedFromFastAllMedia = true
                    }
                }
            }
        }

        if (!loadedFromFastAllMedia) {
            val foldersToScan = if (path.isEmpty()) {
                mediaFetcher.getFoldersToScan().filter {
                    it != RECYCLE_BIN &&
                        it != FAVORITES &&
                        !config.isFolderProtected(it) &&
                        (!CryptoManager.isVaultUnlocked || !CryptoManager.isVaultRecycleBinPath(this, it))
                }.toMutableList() as ArrayList<String>
            } else {
                arrayListOf(path)
            }

            if (path == FAVORITES) {
                val validFavoritePaths = getFavoritePaths().toHashSet()
                media.addAll(mediaDB.getFavorites().filter { it.path in validFavoritePaths })
            }

            if (path == RECYCLE_BIN) {
                media.addAll(getUpdatedDeletedMedia())
            }

            val virtualAlbumId = path.getVirtualAlbumId()
            if (virtualAlbumId != null) {
                try {
                    media.addAll(getVirtualAlbumMedia(virtualAlbumId))
                } catch (ignored: Exception) {
                }
            }

            if (config.filterMedia and TYPE_PORTRAITS != 0) {
                val foldersToAdd = ArrayList<String>()
                for (folder in foldersToScan) {
                    val allFiles = File(folder).listFiles() ?: continue
                    allFiles.filter { it.name.startsWith("img_", true) && it.isDirectory }.forEach {
                        foldersToAdd.add(it.absolutePath)
                    }
                }
                foldersToScan.addAll(foldersToAdd)
            }

            foldersToScan.filter { !it.isVirtualAlbumPath() && (path.isNotEmpty() || !config.isFolderProtected(it)) }.forEach {
                try {
                    val currMedia = mediaDB.getMediaFromPath(it)
                    media.addAll(currMedia)
                } catch (ignored: Exception) {
                }
            }
        }

        if (!shouldShowHidden || !CryptoManager.isVaultUnlocked) {
            // Favorites and virtual album items point at their files explicitly, so a file coming
            // from a hidden folder stays visible there. Only vault content is protected.
            val isExplicitlyReferenced = path == FAVORITES || path.isVirtualAlbumPath()
            media = if (isExplicitlyReferenced) {
                media.filter { !CryptoManager.isVaultMediaPath(this, it.path) } as ArrayList<Medium>
            } else if (config.temporarilyShowHiddenFolders) {
                media.filter { !CryptoManager.isVaultMediaPath(this, it.path) && !CryptoManager.isEncryptedFile(it.path) } as ArrayList<Medium>
            } else {
                media.filter { !it.path.contains("/.") && !CryptoManager.isEncryptedFile(it.path) } as ArrayList<Medium>
            }
        }

        val filterMedia = config.filterMedia
        media = (when {
            getVideosOnly -> media.filter { it.type == TYPE_VIDEOS }
            getImagesOnly -> media.filter { it.type == TYPE_IMAGES }
            else -> media.filter {
                (filterMedia and TYPE_IMAGES != 0 && it.type == TYPE_IMAGES)
                        || (filterMedia and TYPE_VIDEOS != 0 && it.type == TYPE_VIDEOS)
                        || (filterMedia and TYPE_GIFS != 0 && it.type == TYPE_GIFS)
                        || (filterMedia and TYPE_RAWS != 0 && it.type == TYPE_RAWS)
                        || (filterMedia and TYPE_SVGS != 0 && it.type == TYPE_SVGS)
                        || (filterMedia and TYPE_PORTRAITS != 0 && it.type == TYPE_PORTRAITS)
            }
        }) as ArrayList<Medium>

        val pathToUse = path.ifEmpty { SHOW_ALL }
        mediaFetcher.sortMedia(media, config.getEffectiveMediaSorting(pathToUse), enableRandom, pathToUse)
        val grouped = mediaFetcher.groupMedia(media, pathToUse)
        callback(grouped.clone() as ArrayList<ThumbnailItem>)
        val OTGPath = config.OTGPath

        try {
            val mediaToDelete = ArrayList<Medium>()
            // creating a new thread intentionally, do not reuse the common background thread
            Thread {
                media.filter { !getDoesFilePathExist(it.path, OTGPath) }.forEach {
                    if (it.path.startsWith(recycleBinPath)) {
                        deleteDBPath(it.path)
                    } else {
                        mediaToDelete.add(it)
                    }
                }

                if (mediaToDelete.isNotEmpty()) {
                    try {
                        mediaDB.deleteMedia(*mediaToDelete.toTypedArray())
                    } catch (ignored: Exception) {
                    }
                }
            }.start()
        } catch (ignored: Exception) {
        }
    }
}

fun Context.removeInvalidDBDirectories(dirs: ArrayList<Directory>? = null) {
    val dirsToCheck = dirs ?: directoryDB.getAll()
    val OTGPath = config.OTGPath
    dirsToCheck.filter {
        !it.areFavorites()
                && !it.isRecycleBin()
                && !getDoesFilePathExist(it.path, OTGPath)
                && it.path != config.tempFolderPath
    }.forEach {
        try {
            directoryDB.deleteDirPath(it.path)
        } catch (ignored: Exception) {
        }
    }
}

fun Context.updateDBMediaPath(oldPath: String, newPath: String) {
    RandomOrderManager.onPathRenamed(oldPath, newPath)
    val newFilename = newPath.getFilenameFromPath()
    val newParentPath = newPath.getParentPath()
    try {
        if (!isPathHiddenOrEncrypted(newPath)) {
            mediaDB.updateMedium(oldPath, newParentPath, newFilename, newPath)
            dateTakensDB.deleteDateTaken(newPath)
            dateTakensDB.updateDateTakenPath(oldPath, newParentPath, newFilename, newPath)
        } else {
            mediaDB.deleteMediumPath(oldPath)
            dateTakensDB.deleteDateTaken(oldPath)
        }
        favoritesDB.updateFavorite(newFilename, newPath, newParentPath, oldPath)
        virtualAlbumsDB.updateItemPath(oldPath, newPath, newFilename, newParentPath)
        updateReferenceIdentitiesForPath(newPath)
    } catch (ignored: Exception) {
    }

    // The backup files have to follow the database, otherwise they would bring the old path back
    // on the next start.
    try {
        val albumIds = virtualAlbumsDB.getAlbumIdsWithItem(newPath)
        albumIds.forEach { VirtualAlbumSyncManager.saveAlbumToFile(this, it) }
        ReferenceBackupManager.saveFavorites(this)
    } catch (ignored: Exception) {
    }
}

/**
 * Stores the identity of the file at [path] for every reference that points to it, so it can be
 * found again after a rename or move.
 */
fun Context.updateReferenceIdentitiesForPath(path: String, mediaStoreId: Long = 0L) {
    val file = File(path)
    if (!file.isFile) {
        return
    }

    try {
        val identity = MediaIdentity.ofWithFolder(this, file, mediaStoreId)
        if (!identity.isUsable) {
            return
        }

        favoritesDB.updateFavoriteIdentity(
            path = path,
            mediaStoreId = identity.mediaStoreId,
            fileSize = identity.size,
            lastModified = identity.lastModified,
            fingerprint = identity.fingerprint,
            folderInode = identity.folderInode,
            folderSignature = identity.folderSignature,
            capturedAtSec = identity.capturedAtSec
        )
        virtualAlbumsDB.updateItemIdentity(
            path = path,
            mediaStoreId = identity.mediaStoreId,
            fileSize = identity.size,
            lastModified = identity.lastModified,
            fingerprint = identity.fingerprint,
            folderInode = identity.folderInode,
            folderSignature = identity.folderSignature,
            capturedAtSec = identity.capturedAtSec
        )
    } catch (_: Exception) {
    }
}

fun Context.updateDBDirectory(directory: Directory) {
    if (directory.isVirtualAlbum() || CryptoManager.isEncryptedFolderPath(this, directory.path)) {
        return
    }
    try {
        directoryDB.updateDirectory(
            path = directory.path,
            thumbnail = directory.tmb,
            mediaCnt = directory.mediaCnt,
            lastModified = directory.modified,
            dateTaken = directory.taken,
            size = directory.size,
            mediaTypes = directory.types,
            sortValue = directory.sortValue
        )
    } catch (ignored: Exception) {
    }
}

fun Context.getOTGFolderChildren(path: String) = getDocumentFile(path)?.listFiles()

fun Context.getOTGFolderChildrenNames(path: String): MutableList<String?>? {
    return getOTGFolderChildren(path)?.map { it.name }?.toMutableList()
}

fun Context.getFavoritePaths(): ArrayList<String> {
    return try {
        // A favorite whose file was deleted outside of the app must not show up in the list. The
        // parent folder is checked as well, so a favorite on an unmounted card is kept.
        favoritesDB.getValidFavoritePaths().filter { path ->
            File(path).isFile || File(path).parentFile?.exists() != true
        } as ArrayList<String>
    } catch (e: Exception) {
        ArrayList()
    }
}

fun Context.updateFavorite(path: String, isFavorite: Boolean) {
    try {
        if (isFavorite) {
            // Favorites never hold vault content. Files from a hidden folder are fine, they are
            // explicitly referenced by the user.
            if (CryptoManager.isVaultMediaPath(this, path)) {
                return
            }
            favoritesDB.insert(getFavoriteFromPath(path))
            mediaDB.updateFavorite(path, true)
            ReferenceBackupManager.saveFavorites(this)
        } else {
            favoritesDB.deleteFavoritePath(path)
            mediaDB.updateFavorite(path, false)
            ReferenceBackupManager.saveFavorites(this)
        }
    } catch (e: Exception) {
        toast(org.fossify.commons.R.string.unknown_error_occurred)
    }
}

// remove the "recycle_bin" from the file path prefix, replace it with real bin path /data/user...
fun Context.getUpdatedDeletedMedia(): ArrayList<Medium> {
    val media = try {
        mediaDB.getDeletedMedia() as ArrayList<Medium>
    } catch (ignored: Exception) {
        ArrayList()
    }

    media.forEach {
        it.path = File(recycleBinPath, it.path.removePrefix(RECYCLE_BIN)).toString()
    }
    return media
}

fun Context.deleteDBPath(path: String) {
    deleteDBPaths(listOf(path))
}

fun Context.deleteDBPaths(
    paths: Collection<String>,
    folderPrefixes: Collection<String> = emptyList()
) {
    val cleanFilePaths = LinkedHashSet<String>()
    for (rawPath in paths) {
        if (rawPath.isBlank()) continue
        deleteMediumWithPath(rawPath.replaceFirst(recycleBinPath, RECYCLE_BIN))
        val clean = if (rawPath.startsWith(recycleBinPath)) {
            val stripped = rawPath.removePrefix(recycleBinPath)
            if (stripped.startsWith("/")) stripped else "/$stripped"
        } else {
            rawPath
        }
        cleanFilePaths.add(clean)
    }

    val cleanFolderPrefixes = folderPrefixes
        .map { it.trimEnd('/') }
        .filter { it.isNotEmpty() }
        .distinct()

    if (cleanFilePaths.isEmpty() && cleanFolderPrefixes.isEmpty()) {
        return
    }

    val affectedAlbumIds = LinkedHashSet<Long>()
    val initialFavCount = try {
        favoritesDB.getAll().size
    } catch (_: Exception) {
        0
    }

    for (path in cleanFilePaths) {
        try {
            favoritesDB.deleteFavoritePath(path)
        } catch (_: Exception) {
        }
        try {
            affectedAlbumIds.addAll(virtualAlbumsDB.getAlbumIdsWithItem(path))
            virtualAlbumsDB.removeItemFromAllAlbums(path)
        } catch (_: Exception) {
        }
    }

    for (prefix in cleanFolderPrefixes) {
        try {
            mediaDB.deleteMediaFromPath(prefix)
            mediaDB.deleteMediaByPrefix(prefix)
        } catch (_: Exception) {
        }
        try {
            favoritesDB.deleteFavoritesByPrefix(prefix)
        } catch (_: Exception) {
        }
        try {
            affectedAlbumIds.addAll(virtualAlbumsDB.getAlbumIdsWithFolderPrefix(prefix))
            virtualAlbumsDB.removeItemsByFolderPrefix(prefix)
        } catch (_: Exception) {
        }
    }

    for (albumId in affectedAlbumIds) {
        VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
    }

    val afterFavCount = try {
        favoritesDB.getAll().size
    } catch (_: Exception) {
        initialFavCount
    }
    if (afterFavCount != initialFavCount) {
        try {
            ReferenceBackupManager.saveFavorites(this)
        } catch (_: Exception) {
        }
    }
}

fun Context.deleteMediumWithPath(path: String) {
    try {
        mediaDB.deleteMediumPath(path)
    } catch (ignored: Exception) {
    }
}
