package org.fossify.gallery.extensions

import android.content.Context
import android.os.Environment
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.MediaIdentityResolver
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.models.ITEM_STATE_DECRYPTED
import org.fossify.gallery.models.ITEM_STATE_ENCRYPTED
import org.fossify.gallery.models.ITEM_STATE_OK
import org.fossify.gallery.models.ITEM_STATE_UNRESOLVED
import java.io.File
import org.fossify.gallery.helpers.isConfiguredVaultTemporarilyUnavailable
import org.fossify.gallery.helpers.isVaultMediaPath
import org.fossify.gallery.helpers.isVaultRecycleBinPath

private const val MIN_REPAIR_INTERVAL_MS = 5_000L
private const val MIN_FALLBACK_SCAN_INTERVAL_MS = 30_000L

@Volatile
private var lastRepairPassTs = 0L

@Volatile
private var lastFallbackScanTs = 0L

private val repairLock = Any()

/**
 * Repairs the references to files that were renamed or moved outside of the app.
 *
 * The app can only follow a rename it performed itself. When the file was renamed by a file
 * manager, by another app or by a desktop tool, the cached path becomes stale. The identity of the
 * reference is what makes it possible to find the file again.
 *
 * Nothing is deleted here: unresolvable favorites and virtual album items stay preserved so a
 * temporarily moved or unmounted folder can be recovered later. Backups are only rewritten when
 * something actually changed.
 */
fun Context.repairReferencesAfterExternalChange(force: Boolean = false): Int {
    synchronized(repairLock) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRepairPassTs < MIN_REPAIR_INTERVAL_MS) {
            return 0
        }
        lastRepairPassTs = now

        val allowFallback = force || (now - lastFallbackScanTs >= MIN_FALLBACK_SCAN_INTERVAL_MS)

        val deletedOrigPaths = try {
            mediaDB.getDeletedMedia().mapTo(HashSet()) { it.path.removePrefix(RECYCLE_BIN) }
        } catch (_: Exception) {
            emptySet()
        }

        val missingFavorites = try {
            favoritesDB.getActive().filter { it.fullPath !in deletedOrigPaths && !File(it.fullPath).isFile }
        } catch (_: Exception) {
            emptyList()
        }

        val albums = try {
            virtualAlbumsDB.getAllAlbums()
        } catch (_: Exception) {
            emptyList()
        }

        val allItems = try {
            virtualAlbumsDB.getAllItems()
        } catch (_: Exception) {
            emptyList()
        }

        val missingItems = allItems.filter {
            (it.state == ITEM_STATE_OK || it.state == ITEM_STATE_UNRESOLVED) &&
                it.fullPath !in deletedOrigPaths &&
                !it.fullPath.startsWith(recycleBinPath) &&
                !CryptoManager.isVaultRecycleBinPath(this, it.fullPath) &&
                !File(it.fullPath).isFile
        }
        val reappearedAlbumIds = allItems
            .filter { it.state == ITEM_STATE_UNRESOLVED && File(it.fullPath).isFile }
            .mapTo(HashSet()) { it.albumId }

        if (missingFavorites.isEmpty() && missingItems.isEmpty() && reappearedAlbumIds.isEmpty()) {
            return 0
        }

        val missingIdentities = ArrayList<MediaIdentity>(missingFavorites.size + missingItems.size)
        missingFavorites.forEach { missingIdentities.add(it.toIdentity()) }
        missingItems.forEach { missingIdentities.add(it.toIdentity()) }

        val session = MediaIdentityResolver.ResolutionSession(allowFilesystemFallback = allowFallback)
        if (missingIdentities.isNotEmpty()) {
            session.preloadMediaStore(this, missingIdentities)
        }

        var favoritesRepaired = 0
        if (missingFavorites.isNotEmpty()) {
            try {
                favoritesRepaired = repairFavorites(session = session)
            } catch (_: Exception) {
            }
        }

        val candidateAlbumIds = LinkedHashSet<Long>()
        missingItems.forEach { candidateAlbumIds.add(it.albumId) }
        candidateAlbumIds.addAll(reappearedAlbumIds)

        var albumsRepaired = 0
        for (album in albums) {
            val albumId = album.id ?: continue
            if (albumId !in candidateAlbumIds) continue
            try {
                val repairedInAlbum = repairVirtualAlbumItems(albumId, session = session, saveBackup = false)
                if (repairedInAlbum > 0) {
                    albumsRepaired += repairedInAlbum
                    VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
                }
            } catch (_: Exception) {
            }
        }

        var favoritesChanged = favoritesRepaired > 0
        if (allowFallback) {
            for (fav in missingFavorites) {
                if (fav.fullPath in deletedOrigPaths) continue
                val stillMissing = try {
                    val current = favoritesDB.getFavorite(fav.fullPath)
                    current != null && current.state == ITEM_STATE_OK && !File(current.fullPath).isFile
                } catch (_: Exception) {
                    false
                }
                if (!stillMissing || !isStorageVolumeMountedAndReadable(fav.fullPath)) continue
                if (CryptoManager.isVaultMediaPath(this, fav.fullPath) &&
                    (!CryptoManager.isVaultUnlocked || CryptoManager.isConfiguredVaultTemporarilyUnavailable(this))
                ) {
                    continue
                }
                try {
                    favoritesDB.deleteFavoritePath(fav.fullPath)
                    mediaDB.updateFavorite(fav.fullPath, false)
                    favoritesChanged = true
                } catch (_: Exception) {
                }
            }

            val itemsByAlbum = missingItems.groupBy { it.albumId }
            for ((albumId, origMissing) in itemsByAlbum) {
                val album = albums.firstOrNull { it.id == albumId } ?: continue
                if (album.isHidden && (!CryptoManager.isVaultUnlocked || CryptoManager.isConfiguredVaultTemporarilyUnavailable(this))) {
                    continue
                }
                var removedFromAlbum = false
                for (item in origMissing) {
                    if (item.fullPath in deletedOrigPaths) continue
                    val currentItems = try {
                        virtualAlbumsDB.getItemsWithPath(albumId, item.fullPath)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val stillUnresolvedMissing = currentItems.any {
                        it.state != ITEM_STATE_ENCRYPTED &&
                            it.state != ITEM_STATE_DECRYPTED &&
                            !File(it.fullPath).isFile
                    }
                    if (!stillUnresolvedMissing || !isStorageVolumeMountedAndReadable(item.fullPath)) continue
                    if (CryptoManager.isVaultMediaPath(this, item.fullPath) &&
                        (!CryptoManager.isVaultUnlocked || CryptoManager.isConfiguredVaultTemporarilyUnavailable(this))
                    ) {
                        continue
                    }
                    try {
                        virtualAlbumsDB.removeItemFromAlbum(albumId, item.fullPath)
                        removedFromAlbum = true
                    } catch (_: Exception) {
                    }
                }
                if (removedFromAlbum) {
                    try {
                        VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (allowFallback && missingIdentities.isNotEmpty()) {
            lastFallbackScanTs = System.currentTimeMillis()
        }

        if (favoritesChanged) {
            try {
                ReferenceBackupManager.saveFavorites(this)
            } catch (_: Exception) {
            }
        }

        return favoritesRepaired + albumsRepaired
    }
}

internal fun isStorageVolumeMountedAndReadable(path: String): Boolean {
    val norm = path.replace('\\', '/')
    val volumeRoot = when {
        norm.startsWith("/storage/emulated/") -> {
            val parts = norm.split('/').filter { it.isNotEmpty() }
            if (parts.size >= 3) File("/${parts[0]}/${parts[1]}/${parts[2]}") else Environment.getExternalStorageDirectory()
        }
        norm.startsWith("/storage/") -> {
            val parts = norm.split('/').filter { it.isNotEmpty() }
            if (parts.size >= 2) File("/${parts[0]}/${parts[1]}") else return false
        }
        norm.startsWith("/mnt/media_rw/") -> {
            val parts = norm.split('/').filter { it.isNotEmpty() }
            if (parts.size >= 3) File("/${parts[0]}/${parts[1]}/${parts[2]}") else return false
        }
        else -> {
            var candidate = File(path).parentFile
            var steps = 0
            while (candidate != null && !candidate.exists() && steps < 4) {
                candidate = candidate.parentFile
                steps++
            }
            candidate?.takeIf { it.exists() && it.parentFile != null } ?: return false
        }
    }
    return volumeRoot.isDirectory && volumeRoot.canRead() && !(volumeRoot.listFiles().isNullOrEmpty())
}

/**
 * Keeps Favorites and Virtual Album references synchronized when Recycle Bin items are restored
 * back onto disk (including cases where collisions or Android storage rules altered the restored path).
 */
fun Context.restoreReferencesAfterRecycleBinRestore(restoredPairs: List<Pair<String, String>>) {
    if (restoredPairs.isEmpty()) return
    val touchedAlbumIds = LinkedHashSet<Long>()
    var favoritesChanged = false

    for ((origPath, restoredPath) in restoredPairs) {
        if (!restoredPath.equals(origPath, ignoreCase = true)) {
            val newName = restoredPath.getFilenameFromPath()
            val newParent = restoredPath.getParentPath()
            try {
                if (favoritesDB.getFavorite(origPath) != null) {
                    favoritesDB.updateFavorite(newName, restoredPath, newParent, origPath)
                    favoritesChanged = true
                }
            } catch (_: Exception) {
            }
            try {
                val albumIds = virtualAlbumsDB.getAlbumIdsWithItem(origPath)
                if (albumIds.isNotEmpty()) {
                    virtualAlbumsDB.updateItemPath(origPath, restoredPath, newName, newParent)
                    touchedAlbumIds.addAll(albumIds)
                }
            } catch (_: Exception) {
            }
        } else {
            try {
                val albumIds = virtualAlbumsDB.getAlbumIdsWithItem(origPath)
                for (albumId in albumIds) {
                    val items = virtualAlbumsDB.getItemsWithPath(albumId, origPath)
                    if (items.any { it.state == ITEM_STATE_UNRESOLVED }) {
                        virtualAlbumsDB.markItemResolved(albumId, origPath)
                        touchedAlbumIds.add(albumId)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    for (albumId in touchedAlbumIds) {
        try {
            VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
        } catch (_: Exception) {
        }
    }
    if (favoritesChanged) {
        try {
            ReferenceBackupManager.saveFavorites(this)
        } catch (_: Exception) {
        }
    }
}

/**
 * Looks for a file that is referenced by a favorite or a virtual album item but that cannot be
 * found at the cached path.
 *
 * The MediaStore is asked first, because it knows the current location of every indexed file.
 * Only when that fails the folders around the reference are scanned.
 */
fun Context.findReferenceLocation(mediaStoreId: Long, fingerprint: String, size: Long, hintParent: String): String? {
    val identity = org.fossify.gallery.helpers.MediaIdentity(
        mediaStoreId = mediaStoreId,
        size = size,
        lastModified = 0L,
        fingerprint = fingerprint
    )

    val resolution = MediaIdentityResolver.resolve(
        context = this,
        identity = identity,
        hintPaths = emptyList(),
        searchRoots = listOf(hintParent)
    )

    return resolution?.path
}

/**
 * Writes the backups of the favorites and the virtual albums to the Cipher folder.
 *
 * The backups live outside of the app sandbox, so they survive a wipe of the app data.
 */
fun Context.saveReferenceBackups() {
    try {
        ReferenceBackupManager.saveFavorites(this)
    } catch (_: Exception) {
    }

    try {
        virtualAlbumsDB.getAllAlbums().forEach { album ->
            album.id?.let { VirtualAlbumSyncManager.saveAlbumToFile(this, it) }
        }
    } catch (_: Exception) {
    }
}

/**
 * Restores the favorites from the backup in the Cipher folder. Used when the app starts without
 * any data, for example after the user cleared the app data.
 *
 * @return how many favorites were restored
 */
fun Context.restoreFavoritesFromBackup(): Int {
    return try {
        ReferenceBackupManager.restoreFavorites(this)
    } catch (_: Exception) {
        0
    }
}

/**
 * True when the file behind the reference is still there. Used to decide if a backup entry may be
 * restored.
 */
fun Context.doesReferenceStillExist(path: String): Boolean {
    return File(path).isFile
}
