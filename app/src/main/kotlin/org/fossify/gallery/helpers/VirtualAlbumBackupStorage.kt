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


/**
 * Backup file paths, JSON serialization, and storage operations extracted from [VirtualAlbumSyncManager].
 */
internal object VirtualAlbumBackupStorage {
    internal const val VAULT_JSON_SUBFOLDER = "JSON"

    internal fun canAccessSharedStorage(): Boolean {
        return VirtualAlbumSyncManager.customStorageDirForTest != null ||
            VirtualAlbumSyncManager.customVaultDirForTest != null ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
    }

    internal fun isValidPublicVirtualAlbumBackupFile(file: File, gson: Gson = Gson()): Boolean {
        if (!file.isFile || !file.extension.equals("json", ignoreCase = true)) {
            return false
        }
        return try {
            val content = file.readText(Charsets.UTF_8).trim()
            if (content.isEmpty()) {
                return false
            }
            val backup = gson.fromJson(content, VirtualAlbumBackup::class.java) ?: return false
            !backup.isHidden && backup.encryptedPayload == null && !backup.name.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun hasValidPublicVirtualAlbumBackups(context: Context): Boolean {
        if (!canAccessSharedStorage()) {
            return false
        }
        val publicDir = getVirtualAlbumsStorageDir()
        if (!publicDir.exists() || !publicDir.isDirectory) {
            return false
        }
        val jsonFiles = publicDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: return false
        val gson = Gson()
        return jsonFiles.any { isValidPublicVirtualAlbumBackupFile(it, gson) }
    }

    fun deletePublicVirtualAlbumBackups(context: Context): Int {
        if (!canAccessSharedStorage()) {
            return 0
        }
        val publicDir = getVirtualAlbumsStorageDir()
        if (!publicDir.exists() || !publicDir.isDirectory) {
            return 0
        }
        val jsonFiles = publicDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: return 0
        val gson = Gson()
        var deletedCount = 0
        for (file in jsonFiles) {
            if (isValidPublicVirtualAlbumBackupFile(file, gson)) {
                if (try { file.delete() } catch (_: Exception) { false }) {
                    deletedCount++
                }
            }
        }
        if (publicDir.listFiles().isNullOrEmpty()) {
            try {
                publicDir.delete()
            } catch (_: Exception) {
            }
        }
        return deletedCount
    }

    fun backupAllPublicAlbums(context: Context) {
        if (!canAccessSharedStorage() || !context.config.autoBackupFavoritesAndVirtualAlbums) {
            return
        }
        try {
            val publicAlbums = VirtualAlbumSyncManager.getDao(context).getAllAlbums().filter { !it.isHidden }
            val expectedFileNames = publicAlbums.map {
                getPublicAlbumFileName(it.name, it.id).lowercase()
            }.toHashSet()

            val publicDir = getVirtualAlbumsStorageDir()
            if (publicDir.exists() && publicDir.isDirectory) {
                val gson = Gson()
                val existingFiles = publicDir.listFiles { file ->
                    file.isFile && file.extension.equals("json", ignoreCase = true)
                } ?: emptyArray()
                for (file in existingFiles) {
                    if (!expectedFileNames.contains(file.name.lowercase()) &&
                        isValidPublicVirtualAlbumBackupFile(file, gson)
                    ) {
                        try {
                            file.delete()
                        } catch (_: Exception) {
                        }
                    }
                }
                if (publicAlbums.isEmpty() && publicDir.listFiles().isNullOrEmpty()) {
                    try {
                        publicDir.delete()
                    } catch (_: Exception) {
                    }
                }
            }

            publicAlbums.forEach { album ->
                album.id?.let { albumId ->
                    saveAlbumToFile(context, albumId)
                }
            }
        } catch (_: Exception) {
        }
    }

    internal fun isVaultJsonRelativePath(relPath: String): Boolean {
        return relPath.equals(VAULT_JSON_SUBFOLDER, ignoreCase = true) ||
            relPath.startsWith("$VAULT_JSON_SUBFOLDER/", ignoreCase = true)
    }

    fun extractRelativeVaultPath(fullPath: String?, vaultRoot: File? = null): String? {
        if (fullPath.isNullOrBlank()) return null
        val normFull = fullPath.trim().replace('\\', '/').trimEnd('/')
        if (normFull.isEmpty()) return null

        if (vaultRoot != null) {
            val prefixes = listOfNotNull(
                vaultRoot.absolutePath,
                runCatching { vaultRoot.canonicalPath }.getOrNull()
            )
                .map { it.trim().replace('\\', '/').trimEnd('/') }
                .filter { it.isNotEmpty() }
                .distinct()

            for (prefix in prefixes) {
                if (normFull.startsWith("$prefix/", ignoreCase = true)) {
                    val suffix = normFull.substring(prefix.length + 1).trim('/')
                    if (suffix.isNotEmpty() && !isVaultJsonRelativePath(suffix)) {
                        val segments = suffix.split('/')
                        if (segments.none { it.isEmpty() || it == "." || it == ".." }) {
                            return suffix
                        }
                    }
                }
            }
        }

        val marker = "/$SECRETS_DIR_NAME/"
        val idx = normFull.lowercase().lastIndexOf(marker.lowercase())
        if (idx >= 0) {
            val suffix = normFull.substring(idx + marker.length).trim('/')
            if (suffix.isNotEmpty() && !isVaultJsonRelativePath(suffix)) {
                val segments = suffix.split('/')
                if (segments.none { it.isEmpty() || it == "." || it == ".." }) {
                    return suffix
                }
            }
        }
        return null
    }

    fun resolveVaultItemPath(vaultRoot: File, relativeVaultPath: String?, rawFullPath: String?): String? {
        val rawRel = relativeVaultPath?.takeIf { it.isNotBlank() }
            ?: extractRelativeVaultPath(rawFullPath, vaultRoot)
            ?: return null
        val normalizedRel = rawRel.trim().replace('\\', '/').trim('/')
        if (normalizedRel.isEmpty() || isVaultJsonRelativePath(normalizedRel)) return null

        val segments = normalizedRel.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null

        var resolved = vaultRoot
        for (segment in segments) {
            resolved = File(resolved, segment)
        }
        return resolved.absolutePath
    }

    fun evictHiddenVirtualAlbumsFromDbCache(context: Context) {
        VirtualAlbumSyncManager.invalidateDirectoryCache()
        try {
            val dao = VirtualAlbumSyncManager.getDao(context)
            dao.getAllAlbums().filter { it.isHidden }.forEach { album ->
                val albumId = album.id ?: return@forEach
                dao.deleteItemsForAlbum(albumId)
                dao.deleteAlbumById(albumId)
            }
        } catch (_: Exception) {
        }
    }

    fun getVirtualAlbumsStorageDir(): File {
        VirtualAlbumSyncManager.customStorageDirForTest?.let { return it }
        return File(CryptoManager.getCipherRootDirectory(), CIPHER_VIRTUAL_ALBUMS_DIR_NAME)
    }

    fun getVaultStorageDir(context: Context): File {
        VirtualAlbumSyncManager.customVaultDirForTest?.let { return it }
        // If the vault is gone, return the would-be path WITHOUT creating anything on disk.
        val vaultRoot = CryptoManager.findVaultRoot(context)
            ?: return File(CryptoManager.getDefaultVaultRoot(), VAULT_JSON_SUBFOLDER)
        val jsonDir = File(vaultRoot, VAULT_JSON_SUBFOLDER)
        if (!jsonDir.exists()) {
            jsonDir.mkdirs()
        }
        val noMedia = File(jsonDir, NOMEDIA)
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }
        return jsonDir
    }

    fun ensurePublicStorageDirExists(): File {
        if (VirtualAlbumSyncManager.customStorageDirForTest == null) {
            CryptoManager.ensureCipherRootDirectory()
        }
        val dir = getVirtualAlbumsStorageDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getPublicAlbumFileName(albumName: String, albumId: Long? = null): String {
        val sanitized = albumName.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").trim().trimStart('.')
        val baseName = sanitized.ifEmpty { "album_${albumId ?: System.currentTimeMillis()}" }
        return "$baseName.json"
    }

    fun getHiddenAlbumFileName(albumName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest("FVA_STEALTH_$albumName".toByteArray(Charsets.UTF_8))
        val hex = hashBytes.take(8).joinToString("") { "%02x".format(it) }
        return "fva_$hex.json"
    }

    fun getAlbumFile(context: Context, album: VirtualAlbum): File {
        return if (album.isHidden) {
            File(getVaultStorageDir(context), getHiddenAlbumFileName(album.name))
        } else {
            File(getVirtualAlbumsStorageDir(), getPublicAlbumFileName(album.name, album.id))
        }
    }

    fun toBackupItems(items: List<VirtualAlbumItem>, vaultRoot: File? = null): List<VirtualAlbumItemBackup> {
        return items.map {
            VirtualAlbumItemBackup(
                fullPath = it.fullPath,
                addedTs = it.addedTS,
                mediaStoreId = it.mediaStoreId,
                fileSize = it.fileSize,
                lastModified = it.lastModified,
                fingerprint = it.fingerprint,
                state = it.state,
                folderInode = it.folderInode.takeIf { inode -> inode > 0L },
                folderSignature = it.folderSignature.ifEmpty { null },
                capturedAtSec = it.capturedAtSec.takeIf { ts -> ts > 0L },
                relativeVaultPath = if (vaultRoot != null) extractRelativeVaultPath(it.fullPath, vaultRoot) else null
            )
        }
    }

    fun buildPublicAlbumBackup(album: VirtualAlbum, items: List<VirtualAlbumItem>): VirtualAlbumBackup {
        return VirtualAlbumBackup(
            version = 1,
            name = album.name,
            createdTs = album.createdTs,
            isHidden = false,
            parentPhysicalPath = album.parentPhysicalPath,
            items = toBackupItems(items)
        )
    }

    fun saveAlbumToFile(context: Context, albumId: Long) {
        VirtualAlbumSyncManager.invalidateDirectoryCache()
        try {
            val dao = VirtualAlbumSyncManager.getDao(context)
            val album = dao.getAlbumById(albumId) ?: return
            if (!album.isHidden && (!context.config.autoBackupFavoritesAndVirtualAlbums || !canAccessSharedStorage())) {
                return
            }
            val items = dao.getItems(albumId)

            val gson = GsonBuilder().setPrettyPrinting().create()

            if (album.isHidden) {
                val vaultRoot = CryptoManager.getVaultRoot(context)
                // Keep all items (including UNRESOLVED) so their MediaIdentity survives in the backup
                // until the user explicitly removes them or deletes the underlying file.
                val backupItems = toBackupItems(items, vaultRoot)

                // Stealth Vault Virtual Album:
                // 1. Remove any old plaintext file in public Documents
                val publicDir = getVirtualAlbumsStorageDir()
                if (publicDir.exists()) {
                    val oldPublicFile = File(publicDir, getPublicAlbumFileName(album.name, album.id))
                    if (oldPublicFile.exists()) {
                        CryptoManager.secureWipeAndDeleteFile(oldPublicFile, context)
                    }
                }

                // 2. Encrypt the inner JSON payload containing album name and all media paths
                val innerBackup = VirtualAlbumBackup(
                    version = 1,
                    name = album.name,
                    createdTs = album.createdTs,
                    isHidden = true,
                    parentPhysicalPath = album.parentPhysicalPath,
                    items = backupItems,
                    relativeParentVaultPath = extractRelativeVaultPath(album.parentPhysicalPath, vaultRoot)
                )
                val innerJson = gson.toJson(innerBackup)
                val encryptedPayload = CryptoManager.encryptStringForVault(context, innerJson, "album_json")

                // 3. Write outer wrapper to vaultRoot/JSON/fva_<hash>.json
                val outerBackup = VirtualAlbumBackup(
                    version = 2,
                    isHidden = true,
                    encryptedPayload = encryptedPayload
                )
                val targetDir = getVaultStorageDir(context)
                val targetFile = File(targetDir, getHiddenAlbumFileName(album.name))
                val tempFile = File(targetDir, "${targetFile.name}.${System.currentTimeMillis()}.tmp")

                tempFile.writeText(gson.toJson(outerBackup), Charsets.UTF_8)
                if (tempFile.exists()) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    tempFile.renameTo(targetFile)
                }
            } else {
                // Normal (Non-hidden) Virtual Album:
                val targetDir = ensurePublicStorageDirExists()
                val targetFile = File(targetDir, getPublicAlbumFileName(album.name, album.id))
                val tempFile = File(targetDir, "${targetFile.name}.${System.currentTimeMillis()}.tmp")

                val backup = buildPublicAlbumBackup(album, items)

                tempFile.writeText(gson.toJson(backup), Charsets.UTF_8)
                if (tempFile.exists()) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    tempFile.renameTo(targetFile)
                }
            }
        } catch (_: Exception) {
            // Failure to write backup should not crash the app
        }
    }
}
