package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.deleteVirtualAlbumItemFromAllAlbums
import org.fossify.gallery.extensions.updateVirtualAlbumItemPath
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerRecycleBin.
 */



// ==================== VAULT RECYCLE BIN (MODE 2) ====================

fun CryptoManager.isVaultRecycleBin(folder: File): Boolean {
    if (vaultRecycleBinFolder != null && folder.absolutePath == vaultRecycleBinFolder?.absolutePath) {
        return true
    }
    val raw = getDecryptedRawFolderName(folder)
    if (raw == VAULT_RECYCLE_BIN_NAME) {
        vaultRecycleBinFolder = folder
        return true
    }
    return false
}


fun CryptoManager.getVaultRecycleBin(context: Context): File? {
    vaultRecycleBinFolder?.let {
        if (it.exists() && it.isDirectory) return it
    }
    val vaultRoot = findVaultRoot(context) ?: return null
    val subfolders = vaultRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: return null
    for (folder in subfolders) {
        if (isVaultRecycleBin(folder)) {
            vaultRecycleBinFolder = folder
            return folder
        }
    }
    return null
}


fun CryptoManager.getOrCreateVaultRecycleBin(context: Context): File? {
    getVaultRecycleBin(context)?.let { return it }
    val vaultRoot = findVaultRoot(context) ?: return null
    var binFolder: File
    do {
        binFolder = File(vaultRoot, randomHex(6))
    } while (binFolder.exists())
    binFolder.mkdirs()
    try {
        File(binFolder, NOMEDIA).createNewFile()
    } catch (_: Exception) {}
    writeEncryptedFolderMeta(context, binFolder, VAULT_RECYCLE_BIN_NAME)
    vaultRecycleBinFolder = binFolder
    return binFolder
}


fun CryptoManager.isVaultRecycleBinPath(context: Context, path: String): Boolean {
    if (path.isEmpty()) return false
    val cleanPath = path.trimEnd('/')
    val binFolder = getVaultRecycleBin(context)
    if (binFolder != null) {
        val binPath = binFolder.absolutePath
        if (cleanPath == binPath || cleanPath.startsWith("$binPath/")) return true
    }
    return false
}


fun CryptoManager.isVaultRecycleBinPath(path: String): Boolean {
    if (path.isEmpty()) return false
    val clean = path.trimEnd('/')
    vaultRecycleBinFolder?.let { binFolder ->
        val binPath = binFolder.absolutePath
        if (clean == binPath || clean.startsWith("$binPath/")) return true
    }
    val file = File(clean)
    val folder = if (file.isDirectory) file else file.parentFile
    if (folder != null && folder.exists()) {
        return isVaultRecycleBin(folder)
    }
    return false
}


fun CryptoManager.getVaultRecycleBinCount(context: Context): Int {
    val binFolder = getVaultRecycleBin(context) ?: return 0
    return binFolder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
}


fun CryptoManager.getVaultRecycleBinSize(context: Context): Long {
    val binFolder = getVaultRecycleBin(context) ?: return 0L
    return binFolder.listFiles()?.filter { isEncryptedFile(it.name) }?.sumOf { it.length() } ?: 0L
}


fun CryptoManager.getVaultRecycleBinRestoreMap(context: Context): HashMap<String, String> {
    val binFolder = getVaultRecycleBin(context) ?: return HashMap()
    val restoreFile = File(binFolder, VAULT_RESTORE_INFO_FILE)
    if (!restoreFile.exists()) return HashMap()
    return try {
        val encryptedContent = restoreFile.readText(Charsets.UTF_8).trim()
        if (encryptedContent.isEmpty()) return HashMap()
        val json = decryptStringFromVault(encryptedContent) ?: return HashMap()
        val type = object : TypeToken<HashMap<String, String>>() {}.type
        Gson().fromJson(json, type) ?: HashMap()
    } catch (_: Exception) {
        HashMap()
    }
}


fun CryptoManager.saveVaultRecycleBinRestoreMap(context: Context, map: HashMap<String, String>) {
    val binFolder = getOrCreateVaultRecycleBin(context) ?: return
    val restoreFile = File(binFolder, VAULT_RESTORE_INFO_FILE)
    if (map.isEmpty()) {
        if (restoreFile.exists()) {
            restoreFile.delete()
        }
        return
    }
    try {
        val json = Gson().toJson(map)
        val encryptedContent = encryptStringForVault(context, json, "restore_info")
        restoreFile.writeText(encryptedContent, Charsets.UTF_8)
    } catch (_: Exception) {}
}


fun CryptoManager.moveEncryptedFilesToVaultRecycleBin(context: Context, paths: List<String>): Boolean {
    if (paths.isEmpty()) return true
    val binFolder = getOrCreateVaultRecycleBin(context) ?: return false
    val restoreMap = getVaultRecycleBinRestoreMap(context)
    var success = true

    for (path in paths) {
        val sourceFile = File(path)
        if (!sourceFile.exists() || !isEncryptedFile(sourceFile.name)) continue
        val parentPath = sourceFile.parent ?: continue
        if (parentPath == binFolder.absolutePath) continue

        var destFile = File(binFolder, sourceFile.name)
        while (destFile.exists()) {
            destFile = File(binFolder, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        }

        val destPath = destFile.absolutePath
        if (sourceFile.renameTo(destFile)) {
            restoreMap[destFile.name] = parentPath
            context.updateVirtualAlbumItemPath(path, destPath)
            metadataCache.remove(path)?.let { meta ->
                metadataCache[destPath] = meta
            }
        } else {
            success = false
        }
    }

    saveVaultRecycleBinRestoreMap(context, restoreMap)
    return success
}


fun CryptoManager.restoreVaultRecycleBinFiles(context: Context, paths: List<String>): Int {
    if (paths.isEmpty()) return 0
    val binFolder = getVaultRecycleBin(context) ?: return 0
    val restoreMap = getVaultRecycleBinRestoreMap(context)
    val vaultRoot = findVaultRoot(context) ?: return 0
    var restoredCount = 0

    for (path in paths) {
        val binFile = File(path)
        if (!binFile.exists() || binFile.parentFile?.absolutePath != binFolder.absolutePath) continue

        val originalParentPath = restoreMap[binFile.name]
        var targetFolder = if (!originalParentPath.isNullOrEmpty()) File(originalParentPath) else null

        if (targetFolder == null || !targetFolder.exists() || !targetFolder.isDirectory) {
            val existingFolders = vaultRoot.listFiles()?.filter {
                it.isDirectory && !it.name.startsWith(".") && !isVaultRecycleBin(it)
            }
            targetFolder = existingFolders?.firstOrNull() ?: run {
                var newSub = File(vaultRoot, randomHex(6))
                while (newSub.exists()) {
                    newSub = File(vaultRoot, randomHex(6))
                }
                newSub.mkdirs()
                writeEncryptedFolderMeta(context, newSub, "Restored")
                newSub
            }
        }

        var targetFile = File(targetFolder, binFile.name)
        while (targetFile.exists()) {
            targetFile = File(targetFolder, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        }

        val targetPath = targetFile.absolutePath
        if (binFile.renameTo(targetFile)) {
            restoreMap.remove(binFile.name)
            context.updateVirtualAlbumItemPath(path, targetPath)
            metadataCache.remove(path)?.let { meta ->
                metadataCache[targetPath] = meta
            }
            restoredCount++
        }
    }

    saveVaultRecycleBinRestoreMap(context, restoreMap)
    return restoredCount
}


fun CryptoManager.permanentlyDeleteVaultRecycleBinFiles(context: Context, paths: List<String>): Int {
    if (paths.isEmpty()) return 0
    val binFolder = getVaultRecycleBin(context) ?: return 0
    val restoreMap = getVaultRecycleBinRestoreMap(context)
    var deletedCount = 0

    for (path in paths) {
        val binFile = File(path)
        if (!binFile.exists() || binFile.parentFile?.absolutePath != binFolder.absolutePath) continue

        metadataCache.remove(path)
        context.deleteVirtualAlbumItemFromAllAlbums(path)
        secureWipeAndDeleteFile(context, binFile)
        restoreMap.remove(binFile.name)
        deletedCount++
    }

    saveVaultRecycleBinRestoreMap(context, restoreMap)
    return deletedCount
}


fun CryptoManager.emptyVaultRecycleBin(context: Context): Boolean {
    val binFolder = getVaultRecycleBin(context) ?: return true
    val binFiles = binFolder.listFiles()?.filter { isEncryptedFile(it.name) } ?: emptyList()
    for (file in binFiles) {
        val path = file.absolutePath
        metadataCache.remove(path)
        context.deleteVirtualAlbumItemFromAllAlbums(path)
        secureWipeAndDeleteFile(context, file)
    }
    val restoreFile = File(binFolder, VAULT_RESTORE_INFO_FILE)
    if (restoreFile.exists()) {
        restoreFile.delete()
    }
    return true
}
