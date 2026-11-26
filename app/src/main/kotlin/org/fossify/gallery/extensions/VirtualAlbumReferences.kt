package org.fossify.gallery.extensions

import android.content.Context
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.MediaIdentityResolver
import org.fossify.gallery.helpers.ReferenceBackupManager
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.helpers.areTimestampsEquivalent
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.isVaultMediaPath
import org.fossify.gallery.models.ITEM_STATE_DECRYPTED
import org.fossify.gallery.models.ITEM_STATE_ENCRYPTED
import org.fossify.gallery.models.ITEM_STATE_OK
import org.fossify.gallery.models.ITEM_STATE_UNRESOLVED
import org.fossify.gallery.models.VirtualAlbumItem
import java.io.File

data class ReferenceTransitionResult(
    val affectedAlbumIds: Set<Long> = emptySet(),
    val favoritesChanged: Boolean = false
)

private fun Context.applyIdentityToVirtualItem(path: String, identity: MediaIdentity?) {
    if (identity == null || !identity.isUsable) return
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
}

private fun Context.transitionVirtualItem(
    albumId: Long,
    oldPath: String,
    newFile: File,
    newState: String,
    identity: MediaIdentity?
) {
    val newPath = newFile.absolutePath
    virtualAlbumsDB.updateItemAndStateInAlbum(
        albumId = albumId,
        oldPath = oldPath,
        newPath = newPath,
        newFilename = newFile.name,
        newParentPath = newFile.parent ?: newPath.getParentPath(),
        state = newState
    )
    applyIdentityToVirtualItem(newPath, identity)
}

/**
 * Transitions virtual album and favorite references when a plaintext media file is encrypted into [encPath].
 *
 * - Stealth (hidden) virtual albums re-link any active or dormant (`DECRYPTED`) matching entry to [encPath].
 * - Public virtual albums and favorites preserve the entry covertly (`ITEM_STATE_ENCRYPTED`) rather than
 *   deleting it when [markPlaintextDormant] is true, hiding it until the source is decrypted again.
 */
fun Context.markReferencesOnEncrypt(
    sourcePath: String,
    encPath: String,
    plaintextIdentity: MediaIdentity? = null,
    markPlaintextDormant: Boolean = true,
    saveBackups: Boolean = true
): ReferenceTransitionResult {
    val touchedAlbums = LinkedHashSet<Long>()
    var favoritesChanged = false
    val plainFile = File(sourcePath)
    val encFile = File(encPath)
    val resolvedPlainIdentity = plaintextIdentity ?: if (plainFile.isFile) {
        MediaIdentity.ofWithFolder(this, plainFile)
    } else {
        null
    }
    val encIdentity = if (encFile.isFile) MediaIdentity.ofWithFolder(this, encFile) else null

    try {
        val directAlbumIds = virtualAlbumsDB.getAlbumIdsWithItem(sourcePath)
        for (albumId in directAlbumIds) {
            val album = virtualAlbumsDB.getAlbumById(albumId) ?: continue
            if (album.isHidden) {
                transitionVirtualItem(albumId, sourcePath, encFile, ITEM_STATE_OK, encIdentity)
                touchedAlbums.add(albumId)
            } else if (markPlaintextDormant) {
                val existingItem = virtualAlbumsDB.getItemsWithPath(albumId, sourcePath).firstOrNull()
                if (existingItem != null && existingItem.fingerprint.isEmpty()) {
                    applyIdentityToVirtualItem(sourcePath, resolvedPlainIdentity)
                }
                virtualAlbumsDB.updateItemStateInAlbum(albumId, sourcePath, ITEM_STATE_ENCRYPTED)
                touchedAlbums.add(albumId)
            }
        }

        // Reconnect stealth virtual album entries that were temporarily decrypted and are now re-encrypted,
        // even if the decrypted file was renamed or moved before re-encryption.
        val dormantDecrypted = virtualAlbumsDB.getItemsByState(ITEM_STATE_DECRYPTED)
        if (dormantDecrypted.isNotEmpty()) {
            val srcParentName = plainFile.parentFile?.name ?: ""
            for ((albumId, candidates) in dormantDecrypted.groupBy { it.albumId }) {
                if (albumId in touchedAlbums) continue
                val album = virtualAlbumsDB.getAlbumById(albumId) ?: continue
                if (!album.isHidden) continue

                val exactMatch = candidates.firstOrNull { it.fullPath.equals(sourcePath, ignoreCase = true) }
                val identityMatch = if (exactMatch == null && resolvedPlainIdentity != null && resolvedPlainIdentity.size > 0L && resolvedPlainIdentity.hasFingerprint) {
                    candidates
                        .filter {
                            it.fileSize == resolvedPlainIdentity.size &&
                                it.fingerprint == resolvedPlainIdentity.fingerprint &&
                                (it.lastModified <= 0L ||
                                    resolvedPlainIdentity.lastModified <= 0L ||
                                    CryptoManager.areTimestampsEquivalent(it.lastModified, resolvedPlainIdentity.lastModified))
                        }
                        .sortedByDescending { it.parentPath.getFilenameFromPath().equals(srcParentName, ignoreCase = true) }
                        .firstOrNull()
                } else {
                    null
                }
                val match = exactMatch ?: identityMatch ?: continue
                transitionVirtualItem(albumId, match.fullPath, encFile, ITEM_STATE_OK, encIdentity)
                touchedAlbums.add(albumId)
            }
        }

        if (markPlaintextDormant) {
            val existingFav = favoritesDB.getFavorite(sourcePath)
            if (existingFav != null) {
                if (resolvedPlainIdentity != null && resolvedPlainIdentity.isUsable && existingFav.fingerprint.isEmpty()) {
                    favoritesDB.updateFavoriteIdentity(
                        path = sourcePath,
                        mediaStoreId = resolvedPlainIdentity.mediaStoreId,
                        fileSize = resolvedPlainIdentity.size,
                        lastModified = resolvedPlainIdentity.lastModified,
                        fingerprint = resolvedPlainIdentity.fingerprint,
                        folderInode = resolvedPlainIdentity.folderInode,
                        folderSignature = resolvedPlainIdentity.folderSignature,
                        capturedAtSec = resolvedPlainIdentity.capturedAtSec
                    )
                }
                favoritesDB.updateFavoriteState(sourcePath, ITEM_STATE_ENCRYPTED)
                runCatching { mediaDB.updateFavorite(sourcePath, false) }
                favoritesChanged = true
            }
        }

        if (touchedAlbums.isNotEmpty()) {
            VirtualAlbumSyncManager.invalidateDirectoryCache()
        }
        if (saveBackups) {
            touchedAlbums.forEach { VirtualAlbumSyncManager.saveAlbumToFile(this, it) }
            if (favoritesChanged) {
                ReferenceBackupManager.saveFavorites(this)
            }
        }
    } catch (_: Exception) {
    }
    return ReferenceTransitionResult(touchedAlbums, favoritesChanged)
}

/**
 * Transitions virtual album and favorite references when an encrypted vault file at [encPath] is
 * decrypted to [decryptedFile].
 *
 * - Stealth (hidden) virtual albums keep the reference in dormant `ITEM_STATE_DECRYPTED` state
 *   (storing plaintext size + fingerprint so a subsequent re-encryption restores it cleanly).
 * - Dormant (`ITEM_STATE_ENCRYPTED`) public virtual album items and favorites matching [decryptedFile]
 *   are restored to `ITEM_STATE_OK` and become visible again.
 */
fun Context.markReferencesOnDecrypt(
    encPath: String,
    decryptedFile: File,
    hideStealthReferences: Boolean = true,
    saveBackups: Boolean = true
): ReferenceTransitionResult {
    val touchedAlbums = LinkedHashSet<Long>()
    var favoritesChanged = false
    val decryptedPath = decryptedFile.absolutePath
    val decryptedParent = decryptedFile.parent ?: decryptedPath.getParentPath()
    val decryptedFolderName = decryptedFile.parentFile?.name ?: ""
    val plainIdentity = if (decryptedFile.isFile) MediaIdentity.ofWithFolder(this, decryptedFile) else null

    try {
        val directAlbumIds = virtualAlbumsDB.getAlbumIdsWithItem(encPath)
        for (albumId in directAlbumIds) {
            val album = virtualAlbumsDB.getAlbumById(albumId) ?: continue
            if (album.isHidden) {
                if (hideStealthReferences) {
                    transitionVirtualItem(albumId, encPath, decryptedFile, ITEM_STATE_DECRYPTED, plainIdentity)
                    touchedAlbums.add(albumId)
                }
            } else {
                transitionVirtualItem(albumId, encPath, decryptedFile, ITEM_STATE_OK, plainIdentity)
                touchedAlbums.add(albumId)
            }
        }

        // Restore any dormant ENCRYPTED public virtual album items matching the decrypted file.
        val dormantEncryptedItems = virtualAlbumsDB.getItemsByState(ITEM_STATE_ENCRYPTED)
        if (dormantEncryptedItems.isNotEmpty()) {
            for ((albumId, candidates) in dormantEncryptedItems.groupBy { it.albumId }) {
                if (albumId in touchedAlbums) continue
                val album = virtualAlbumsDB.getAlbumById(albumId) ?: continue
                if (album.isHidden) continue

                val exactMatch = candidates.firstOrNull { it.fullPath.equals(decryptedPath, ignoreCase = true) }
                val identityMatch = if (exactMatch == null && plainIdentity != null && plainIdentity.size > 0L && plainIdentity.hasFingerprint) {
                    candidates
                        .filter {
                            it.fileSize == plainIdentity.size &&
                                it.fingerprint == plainIdentity.fingerprint &&
                                (it.lastModified <= 0L ||
                                    plainIdentity.lastModified <= 0L ||
                                    CryptoManager.areTimestampsEquivalent(it.lastModified, plainIdentity.lastModified))
                        }
                        .sortedByDescending { it.parentPath.getFilenameFromPath().equals(decryptedFolderName, ignoreCase = true) }
                        .firstOrNull()
                } else {
                    null
                }
                val match = exactMatch ?: identityMatch ?: continue
                transitionVirtualItem(albumId, match.fullPath, decryptedFile, ITEM_STATE_OK, plainIdentity)
                touchedAlbums.add(albumId)
            }
        }

        // Restore any dormant ENCRYPTED favorite matching the decrypted file.
        val dormantFavs = favoritesDB.getEncryptedDormant()
        if (dormantFavs.isNotEmpty()) {
            val exactFav = dormantFavs.firstOrNull { it.fullPath.equals(decryptedPath, ignoreCase = true) }
            val identityFav = if (exactFav == null && plainIdentity != null && plainIdentity.size > 0L && plainIdentity.hasFingerprint) {
                dormantFavs
                    .filter {
                        it.fileSize == plainIdentity.size &&
                            it.fingerprint == plainIdentity.fingerprint &&
                            (it.lastModified <= 0L ||
                                plainIdentity.lastModified <= 0L ||
                                CryptoManager.areTimestampsEquivalent(it.lastModified, plainIdentity.lastModified))
                    }
                    .sortedByDescending { it.parentPath.getFilenameFromPath().equals(decryptedFolderName, ignoreCase = true) }
                    .firstOrNull()
            } else {
                null
            }
            val match = exactFav ?: identityFav
            if (match != null) {
                favoritesDB.restoreEncryptedFavorite(match.fullPath, decryptedFile.name, decryptedPath, decryptedParent)
                if (plainIdentity != null && plainIdentity.isUsable) {
                    favoritesDB.updateFavoriteIdentity(
                        path = decryptedPath,
                        mediaStoreId = plainIdentity.mediaStoreId,
                        fileSize = plainIdentity.size,
                        lastModified = plainIdentity.lastModified,
                        fingerprint = plainIdentity.fingerprint,
                        folderInode = plainIdentity.folderInode,
                        folderSignature = plainIdentity.folderSignature,
                        capturedAtSec = plainIdentity.capturedAtSec
                    )
                }
                runCatching { mediaDB.updateFavorite(decryptedPath, true) }
                favoritesChanged = true
            }
        }

        if (touchedAlbums.isNotEmpty()) {
            VirtualAlbumSyncManager.invalidateDirectoryCache()
        }
        if (saveBackups) {
            touchedAlbums.forEach { VirtualAlbumSyncManager.saveAlbumToFile(this, it) }
            if (favoritesChanged) {
                ReferenceBackupManager.saveFavorites(this)
            }
        }
    } catch (_: Exception) {
    }
    return ReferenceTransitionResult(touchedAlbums, favoritesChanged)
}

/**
 * Updates virtual album references when an encrypted vault file is decrypted back to plaintext.
 */
fun Context.updateVirtualAlbumItemPathOnDecrypt(oldEncPath: String, newPlaintextPath: String) {
    markReferencesOnDecrypt(oldEncPath, File(newPlaintextPath), hideStealthReferences = true, saveBackups = true)
}

/**
 * Updates virtual album references when a plaintext file is encrypted into the vault.
 */
fun Context.updateVirtualAlbumItemPathOnEncrypt(oldPlaintextPath: String, newEncPath: String) {
    markReferencesOnEncrypt(oldPlaintextPath, newEncPath, markPlaintextDormant = true, saveBackups = true)
}

/**
 * Rewrites every reference below [oldRoot] so it points below [newRoot].
 *
 * Called when a physical folder is renamed or moved. Without this the favorites and the virtual
 * album items would keep pointing at the old location and appear empty.
 */
fun Context.updateReferencesForFolderRename(oldRoot: String, newRoot: String) {
    if (oldRoot.isEmpty() || newRoot.isEmpty() || oldRoot.equals(newRoot, ignoreCase = true)) {
        return
    }

    try {
        virtualAlbumsDB.updateItemsByFolderPrefix(oldRoot, newRoot)
        virtualAlbumsDB.updateItemParentsByFolderPrefix(oldRoot, newRoot)
        virtualAlbumsDB.updateItemParentsAtFolder(oldRoot, newRoot)
        virtualAlbumsDB.updateAlbumParentPhysicalPathByPrefix(oldRoot, newRoot)
        virtualAlbumsDB.updateAlbumParentPhysicalPathAtFolder(oldRoot, newRoot)
        updateFavoritePathsForFolderRename(oldRoot, newRoot)
    } catch (ignored: Exception) {
    }

    // The albums whose items just moved need their backup files rewritten.
    try {
        virtualAlbumsDB.getAllAlbums().forEach { album ->
            album.id?.let { VirtualAlbumSyncManager.saveAlbumToFile(this, it) }
        }
        ReferenceBackupManager.saveFavorites(this)
    } catch (ignored: Exception) {
    }
}

/**
 * Looks for the file of an album item at its current location.
 *
 * When the file was renamed or moved outside of the app, the cached path is stale. The identity of
 * the item is used to find it again, and the album is updated so the new path is used from now on.
 *
 * @return the current path of the file, or null when it could not be found
 */
fun Context.resolveVirtualAlbumItemPath(
    item: VirtualAlbumItem,
    session: MediaIdentityResolver.ResolutionSession? = null,
    saveBackup: Boolean = true,
    excludedPaths: Set<String> = emptySet(),
    allowAmbiguous: Boolean = true,
    vaultOnly: Boolean = false
): String? {
    if (item.state == ITEM_STATE_ENCRYPTED || item.state == ITEM_STATE_DECRYPTED) {
        return null
    }
    val identity = item.toIdentity()
    if (!identity.isUsable) {
        return null
    }

    val resolution = if (vaultOnly) {
        val vaultRoot = CryptoManager.findVaultRoot(this)?.takeIf { it.isDirectory } ?: return null
        val vaultHints = listOf(item.fullPath).filter { CryptoManager.isVaultMediaPath(this, it) }
        MediaIdentityResolver.resolveInFilesystem(
            identity = identity,
            hintPaths = vaultHints,
            searchRoots = listOf(vaultRoot.absolutePath),
            session = session,
            extraRoots = emptyList(),
            excludedPaths = excludedPaths,
            allowAmbiguous = allowAmbiguous
        )?.takeIf { CryptoManager.isVaultMediaPath(this, it.path) } ?: return null
    } else {
        MediaIdentityResolver.resolve(
            context = this,
            identity = identity,
            hintPaths = listOf(item.fullPath),
            searchRoots = listOf(item.parentPath),
            session = session,
            excludedPaths = excludedPaths,
            allowAmbiguous = allowAmbiguous
        )?.takeIf {
            !CryptoManager.isVaultMediaPath(this, it.path) && !CryptoManager.isEncryptedFile(it.path)
        } ?: return null
    }

    if (resolution.path == item.fullPath) {
        if (item.state == ITEM_STATE_UNRESOLVED) {
            try {
                virtualAlbumsDB.markItemResolved(item.albumId, item.fullPath)
                VirtualAlbumSyncManager.invalidateDirectoryCache()
            } catch (_: Exception) {
            }
        }
        return item.fullPath
    }

    try {
        virtualAlbumsDB.updateItemPath(
            item.fullPath,
            resolution.path,
            resolution.path.getFilenameFromPath(),
            resolution.path.getParentPath()
        )
        virtualAlbumsDB.markItemResolved(item.albumId, resolution.path)
        updateReferenceIdentitiesForPath(resolution.path, resolution.mediaStoreId)
        VirtualAlbumSyncManager.invalidateDirectoryCache()
        if (saveBackup) {
            VirtualAlbumSyncManager.saveAlbumToFile(this, item.albumId)
        }
    } catch (_: Exception) {
    }

    return resolution.path
}

/**
 * Resolves missing virtual album items in two passes inside one [MediaIdentityResolver.ResolutionSession]:
 * first resolving unambiguous items (so sibling folder relocations are discovered before duplicates),
 * then resolving remaining items using full candidate scoring and scoped path exclusion.
 */
private fun Context.resolveVirtualAlbumItemsTwoPass(
    allItems: List<VirtualAlbumItem>,
    missingItems: List<VirtualAlbumItem>,
    session: MediaIdentityResolver.ResolutionSession?,
    vaultOnly: Boolean = false
): Map<String, String> {
    if (missingItems.isEmpty()) return emptyMap()

    val claimedPaths = HashSet<String>(allItems.size)
    for (item in allItems) {
        if (File(item.fullPath).isFile) {
            claimedPaths.add(item.fullPath.lowercase())
        }
    }

    val activeSession = session ?: MediaIdentityResolver.ResolutionSession().apply {
        if (!vaultOnly) {
            preloadMediaStore(this@resolveVirtualAlbumItemsTwoPass, missingItems.map { it.toIdentity() })
        }
    }

    val resolvedByOldPath = HashMap<String, String>(missingItems.size)
    val deferred = ArrayList<VirtualAlbumItem>()
    val firstPassAllowAmbiguous = missingItems.size <= 1

    for (item in missingItems) {
        val resolved = resolveVirtualAlbumItemPath(
            item = item,
            session = activeSession,
            saveBackup = false,
            excludedPaths = claimedPaths,
            allowAmbiguous = firstPassAllowAmbiguous,
            vaultOnly = vaultOnly
        )
        if (resolved != null) {
            resolvedByOldPath[item.fullPath] = resolved
            claimedPaths.add(resolved.lowercase())
        } else if (!firstPassAllowAmbiguous) {
            deferred.add(item)
        }
    }

    for (item in deferred) {
        val resolved = resolveVirtualAlbumItemPath(
            item = item,
            session = activeSession,
            saveBackup = false,
            excludedPaths = claimedPaths,
            allowAmbiguous = true,
            vaultOnly = vaultOnly
        )
        if (resolved != null) {
            resolvedByOldPath[item.fullPath] = resolved
            claimedPaths.add(resolved.lowercase())
        }
    }

    return resolvedByOldPath
}

/**
 * Looks for the items of a virtual album whose file could not be found at the cached path and tries
 * to find them at their new location.
 *
 * @return how many items were repaired
 */
fun Context.repairVirtualAlbumItems(
    albumId: Long,
    session: MediaIdentityResolver.ResolutionSession? = null,
    saveBackup: Boolean = true
): Int {
    val items = try {
        virtualAlbumsDB.getItems(albumId)
    } catch (_: Exception) {
        return 0
    }

    val missing = ArrayList<VirtualAlbumItem>()
    var repaired = 0

    val isHiddenAlbum = try {
        virtualAlbumsDB.getAlbumById(albumId)?.isHidden == true
    } catch (_: Exception) {
        false
    }

    for (item in items) {
        if (item.state == ITEM_STATE_ENCRYPTED || item.state == ITEM_STATE_DECRYPTED) {
            continue
        }
        val diskFile = File(item.fullPath)
        if (diskFile.isFile) {
            if (item.state == ITEM_STATE_UNRESOLVED && doesVirtualItemMatchDiskFile(item, diskFile, isHiddenAlbum)) {
                try {
                    virtualAlbumsDB.markItemResolved(albumId, item.fullPath)
                    repaired++
                } catch (_: Exception) {
                }
            }
        } else {
            missing.add(item)
        }
    }

    if (missing.isNotEmpty()) {
        val resolvedMap = resolveVirtualAlbumItemsTwoPass(items, missing, session, vaultOnly = isHiddenAlbum)
        for ((oldPath, newPath) in resolvedMap) {
            if (newPath != oldPath) {
                repaired++
            }
        }
    }

    if (repaired > 0) {
        VirtualAlbumSyncManager.invalidateDirectoryCache()
        if (saveBackup) {
            VirtualAlbumSyncManager.saveAlbumToFile(this, albumId)
        }
    }

    return repaired
}
