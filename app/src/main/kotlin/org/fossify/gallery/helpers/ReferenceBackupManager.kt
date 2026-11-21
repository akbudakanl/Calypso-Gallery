package org.fossify.gallery.helpers

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.interfaces.FavoritesDao
import org.fossify.gallery.models.ITEM_STATE_ENCRYPTED
import org.fossify.gallery.models.ITEM_STATE_OK
import java.io.File

/**
 * Keeps a one-way backup copy of the favorites in the Cipher folder on shared storage.
 *
 * The app database is the single source of truth. The backup lives outside of the app sandbox so it
 * survives uninstall or clearing app data, and is automatically restored once on a fresh start once
 * shared storage becomes readable.
 *
 * The backup is only written while the app may access shared storage and the auto-backup setting is
 * enabled. Without that permission the folder cannot be read or written, and nothing is attempted.
 */
object ReferenceBackupManager {
    internal const val FAVORITES_DIR_NAME = "Favorites"
    internal const val FAVORITES_FILE_NAME = "favorites.json"

    /** The backup format version, bumped when the structure changes. */
    private const val BACKUP_VERSION = 1

    private val restoreLock = Any()

    @VisibleForTesting
    var customFavoritesDirForTest: File? = null

    @VisibleForTesting
    internal var favoritesDaoProviderForTest: ((Context) -> FavoritesDao)? = null

    @VisibleForTesting
    internal var mediaFavoriteUpdaterForTest: ((Context, String, Boolean) -> Unit)? = null

    private fun getFavoritesDao(context: Context): FavoritesDao {
        return favoritesDaoProviderForTest?.invoke(context) ?: context.favoritesDB
    }

    private fun updateMediaFavorite(context: Context, path: String, isFavorite: Boolean) {
        val updater = mediaFavoriteUpdaterForTest
        if (updater != null) {
            updater(context, path, isFavorite)
        } else {
            context.mediaDB.updateFavorite(path, isFavorite)
        }
    }

    fun buildFavoritesBackup(favorites: List<org.fossify.gallery.models.Favorite>): FavoritesBackup {
        return FavoritesBackup(
            version = BACKUP_VERSION,
            favorites = favorites.map { favorite ->
                FavoriteBackup(
                    fullPath = favorite.fullPath,
                    filename = favorite.filename,
                    parentPath = favorite.parentPath,
                    mediaStoreId = favorite.mediaStoreId,
                    fileSize = favorite.fileSize,
                    lastModified = favorite.lastModified,
                    fingerprint = favorite.fingerprint,
                    folderInode = favorite.folderInode,
                    folderSignature = favorite.folderSignature.ifEmpty { null },
                    capturedAtSec = favorite.capturedAtSec.takeIf { it > 0L },
                    state = favorite.state.takeIf { it != ITEM_STATE_OK }
                )
            }
        )
    }

    data class FavoritesBackup(
        @SerializedName("version") val version: Int = BACKUP_VERSION,
        @SerializedName("favorites") val favorites: List<FavoriteBackup>? = null
    )

    data class FavoriteBackup(
        @SerializedName("fullPath") val fullPath: String? = null,
        @SerializedName("filename") val filename: String? = null,
        @SerializedName("parentPath") val parentPath: String? = null,
        @SerializedName("mediaStoreId") val mediaStoreId: Long? = null,
        @SerializedName("fileSize") val fileSize: Long? = null,
        @SerializedName("lastModified") val lastModified: Long? = null,
        @SerializedName("fingerprint") val fingerprint: String? = null,
        @SerializedName("folderInode") val folderInode: Long? = null,
        @SerializedName("folderSignature") val folderSignature: String? = null,
        @SerializedName("capturedAtSec") val capturedAtSec: Long? = null,
        @SerializedName("state") val state: String? = null
    )

    /**
     * The folder the backups are written to. It lives inside the Cipher folder so it stays
     * together with the vault and the virtual album saves.
     */
    fun getFavoritesBackupDir(): File {
        customFavoritesDirForTest?.let { return it }
        return File(CryptoManager.getCipherRootDirectory(), FAVORITES_DIR_NAME)
    }

    fun getFavoritesBackupFile(): File {
        return File(getFavoritesBackupDir(), FAVORITES_FILE_NAME)
    }

    /**
     * Returns true when `Favorites/favorites.json` exists on shared storage and contains a valid
     * JSON backup with at least one favorite entry.
     */
    fun hasValidFavoritesBackup(context: Context): Boolean {
        if (!canAccessSharedStorage(context)) {
            return false
        }
        val backupFile = getFavoritesBackupFile().takeIf { it.isFile } ?: return false
        return try {
            val content = backupFile.readText(Charsets.UTF_8).trim()
            if (content.isEmpty()) {
                return false
            }
            val backup = Gson().fromJson(content, FavoritesBackup::class.java) ?: return false
            backup.favorites?.any { !it.fullPath.isNullOrBlank() } == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes the favorites backup JSON file from shared storage (and removes the `Favorites`
     * folder if it becomes empty).
     */
    fun deleteFavoritesBackup(context: Context): Boolean {
        if (!canAccessSharedStorage(context)) {
            return false
        }
        return try {
            val dir = getFavoritesBackupDir()
            val target = getFavoritesBackupFile()
            val deleted = if (target.exists()) target.delete() else false
            if (dir.exists() && dir.isDirectory && dir.listFiles().isNullOrEmpty()) {
                dir.delete()
            }
            deleted
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Writes the current favorites to the backup file. Does nothing when shared storage cannot
     * be accessed or auto-backup is turned off. Removes the backup file when no favorites remain.
     */
    fun saveFavorites(context: Context) {
        if (!canAccessSharedStorage(context) || !context.config.autoBackupFavoritesAndVirtualAlbums) {
            return
        }

        try {
            val favorites = getFavoritesDao(context).getAll()
            if (favorites.isEmpty()) {
                deleteFavoritesBackup(context)
                return
            }

            val backup = buildFavoritesBackup(favorites)

            if (customFavoritesDirForTest == null) {
                CryptoManager.ensureCipherRootDirectory()
            }

            val dir = getFavoritesBackupDir()
            if (!dir.exists() && !dir.mkdirs()) {
                return
            }

            val target = getFavoritesBackupFile()
            val temp = File(dir, "${target.name}.${System.currentTimeMillis()}.tmp")
            temp.writeText(GsonBuilder().setPrettyPrinting().create().toJson(backup), Charsets.UTF_8)
            if (temp.exists()) {
                if (target.exists()) {
                    target.delete()
                }
                temp.renameTo(target)
            }
        } catch (_: Exception) {
            // A failed backup must never crash the app.
        }
    }

    internal fun resolveRestoredFavoriteIdentity(
        entry: FavoriteBackup,
        diskSize: Long,
        diskLastModified: Long,
        fallbackCapture: () -> MediaIdentity
    ): MediaIdentity {
        val backupSize = entry.fileSize ?: 0L
        val backupFingerprint = entry.fingerprint.orEmpty()
        if (backupFingerprint.isNotEmpty() && backupSize > 0L && diskSize == backupSize) {
            return MediaIdentity(
                mediaStoreId = entry.mediaStoreId ?: 0L,
                size = backupSize,
                lastModified = (entry.lastModified ?: 0L).takeIf { it > 0L } ?: diskLastModified,
                fingerprint = backupFingerprint,
                folderInode = entry.folderInode ?: 0L,
                folderSignature = entry.folderSignature ?: "",
                capturedAtSec = entry.capturedAtSec ?: 0L
            )
        }
        return fallbackCapture()
    }

    /**
     * Restores the favorites from the backup file once on a fresh install / cleared app data,
     * after shared storage becomes accessible. Subsequent runs are skipped so stale backup files
     * cannot resurrect removed favorites.
     *
     * @return how many favorites were restored
     */
    fun restoreFavorites(context: Context, force: Boolean = false): Int {
        synchronized(restoreLock) {
            if (!canAccessSharedStorage(context)) {
                return 0
            }
            if (!force && (context.config.hasCompletedInitialFavoritesRestore || !context.config.autoBackupFavoritesAndVirtualAlbums)) {
                return 0
            }

            val backupFile = getFavoritesBackupFile().takeIf { it.isFile }
            if (backupFile == null) {
                context.config.hasCompletedInitialFavoritesRestore = true
                return 0
            }

            return try {
                val content = backupFile.readText(Charsets.UTF_8).trim()
                if (content.isEmpty()) {
                    context.config.hasCompletedInitialFavoritesRestore = true
                    return 0
                }

                val backup = Gson().fromJson(content, FavoritesBackup::class.java)
                val entries = backup?.favorites
                if (entries == null) {
                    context.config.hasCompletedInitialFavoritesRestore = true
                    return 0
                }

                val favoritesDao = getFavoritesDao(context)
                val folderCaptureSession = FolderIdentity.CaptureSession()
                var restored = 0
                for (entry in entries) {
                    val path = entry.fullPath ?: continue
                    val isEncryptedDormant = entry.state == ITEM_STATE_ENCRYPTED
                    val targetFile = File(path)
                    if (!isEncryptedDormant && !targetFile.isFile) {
                        continue
                    }

                    try {
                        val existing = favoritesDao.getFavorite(path)
                        if (existing == null) {
                            val identity = if (isEncryptedDormant) {
                                MediaIdentity(
                                    mediaStoreId = entry.mediaStoreId ?: 0L,
                                    size = entry.fileSize ?: 0L,
                                    lastModified = entry.lastModified ?: 0L,
                                    fingerprint = entry.fingerprint ?: "",
                                    folderInode = entry.folderInode ?: 0L,
                                    folderSignature = entry.folderSignature ?: "",
                                    capturedAtSec = entry.capturedAtSec ?: 0L
                                )
                            } else {
                                resolveRestoredFavoriteIdentity(
                                    entry = entry,
                                    diskSize = targetFile.length(),
                                    diskLastModified = targetFile.lastModified()
                                ) {
                                    MediaIdentity.ofWithFolder(
                                        context = context,
                                        file = targetFile,
                                        mediaStoreId = entry.mediaStoreId ?: 0L,
                                        captureSession = folderCaptureSession
                                    )
                                }
                            }
                            favoritesDao.insert(
                                org.fossify.gallery.models.Favorite(
                                    id = null,
                                    fullPath = path,
                                    filename = entry.filename ?: path.getFilenameFromPath(),
                                    parentPath = entry.parentPath ?: path.getParentPath(),
                                    mediaStoreId = identity.mediaStoreId,
                                    fileSize = if (identity.size > 0) identity.size else entry.fileSize ?: 0L,
                                    lastModified = if (identity.lastModified > 0) identity.lastModified else entry.lastModified ?: 0L,
                                    fingerprint = if (identity.hasFingerprint) identity.fingerprint else entry.fingerprint ?: "",
                                    folderInode = if (identity.folderInode > 0L) identity.folderInode else entry.folderInode ?: 0L,
                                    folderSignature = identity.folderSignature.ifEmpty { entry.folderSignature ?: "" },
                                    capturedAtSec = entry.capturedAtSec ?: identity.capturedAtSec,
                                    state = if (isEncryptedDormant) ITEM_STATE_ENCRYPTED else ITEM_STATE_OK
                                )
                            )
                            if (!isEncryptedDormant) {
                                try {
                                    updateMediaFavorite(context, path, true)
                                } catch (_: Exception) {
                                }
                            }
                            restored++
                        } else {
                            // The favorite exists, but the backup may know its identity better.
                            if ((existing.fingerprint.isEmpty() && !entry.fingerprint.isNullOrEmpty()) ||
                                (existing.folderSignature.isEmpty() && !entry.folderSignature.isNullOrEmpty()) ||
                                (existing.folderInode == 0L && (entry.folderInode ?: 0L) > 0L)
                            ) {
                                favoritesDao.updateFavoriteIdentity(
                                    path = path,
                                    mediaStoreId = entry.mediaStoreId ?: existing.mediaStoreId,
                                    fileSize = entry.fileSize ?: existing.fileSize,
                                    lastModified = entry.lastModified ?: existing.lastModified,
                                    fingerprint = entry.fingerprint?.ifEmpty { existing.fingerprint } ?: existing.fingerprint,
                                    folderInode = entry.folderInode ?: existing.folderInode,
                                    folderSignature = entry.folderSignature ?: existing.folderSignature,
                                    capturedAtSec = entry.capturedAtSec ?: existing.capturedAtSec
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                context.config.hasCompletedInitialFavoritesRestore = true
                restored
            } catch (_: Exception) {
                context.config.hasCompletedInitialFavoritesRestore = true
                0
            }
        }
    }

    /**
     * The shared storage can only be read and written while the app has the permission for it.
     */
    private fun canAccessSharedStorage(context: Context): Boolean {
        if (customFavoritesDirForTest != null) {
            return true
        }
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
