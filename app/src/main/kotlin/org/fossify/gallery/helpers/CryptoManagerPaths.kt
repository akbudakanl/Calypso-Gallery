package org.fossify.gallery.helpers

import android.content.Context
import android.os.Environment
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import java.io.*

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerPaths.
 */

/**
 * Returns true when the media item belongs to the encrypted vault content
 * zone, i.e. it is stored as an encrypted file or it is located inside one
 * of the encrypted vault folders.
 */
fun CryptoManager.isVaultMediaPath(context: Context, path: String): Boolean {
    if (path.isEmpty()) return false
    return isEncryptedFile(path) || isEncryptedFolderPath(context, path.getParentPath())
}


fun CryptoManager.isEncryptedFolder(folder: File): Boolean {
    if (!folder.exists() || !folder.isDirectory) return false
    return File(folder, VAULT_SUBFOLDER_META_FILE).exists() ||
        folder.name.startsWith(ENCRYPTED_FOLDER_PREFIX)
}


fun CryptoManager.isEncryptedFolderPath(context: Context, folderPath: String): Boolean {
    val clean = folderPath.trimEnd('/')
    if (clean.isEmpty()) return false

    encryptedFolderCache[clean]?.let { return it }

    if (context.config.encryptedFolders.contains(clean)) {
        encryptedFolderCache[clean] = true
        return true
    }

    val vaultRoot = findVaultRoot(context)
    if (vaultRoot != null) {
        val rootPath = vaultRoot.absolutePath.trimEnd('/')
        if (clean == rootPath || clean.startsWith("$rootPath/")) {
            encryptedFolderCache[clean] = true
            return true
        }
    }

    val folder = File(clean)
    val isEnc = isEncryptedFolder(folder)
    encryptedFolderCache[clean] = isEnc
    return isEnc
}


fun CryptoManager.getDocumentsDirectory(): File {
    return try {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    } catch (_: Throwable) {
        null
    } ?: try {
        File(Environment.getExternalStorageDirectory(), "Documents")
    } catch (_: Throwable) {
        File("/storage/emulated/0/Documents")
    }
}


/**
 * The root of the shared storage (the internal storage of the device). The app container is a
 * standalone top level folder in here, it is not nested in Documents.
 */
fun CryptoManager.getStorageRootDirectory(): File {
    return try {
        Environment.getExternalStorageDirectory()
    } catch (_: Throwable) {
        null
    } ?: File("/storage/emulated/0")
}


/**
 * The container of this app: `<internal storage>/Calypso Gallery`. It holds the vault,
 * the saved virtual albums and the backups of the favorites. It is a standalone top level
 * folder, it is not nested in the Documents folder.
 */
fun CryptoManager.getCipherRootDirectory(): File {
    return File(getStorageRootDirectory(), CIPHER_APP_DIR_NAME)
}


/**
 * Creates the container of the app in the internal storage, with a .nomedia file so it is
 * never listed as a media folder by the system or by other gallery apps.
 */
fun CryptoManager.ensureCipherRootDirectory(): File {
    val dir = getCipherRootDirectory()
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val noMedia = File(dir, NOMEDIA)
    if (!noMedia.exists()) {
        try { noMedia.createNewFile() } catch (_: Exception) {}
    }
    return dir
}


fun CryptoManager.getDefaultVaultRoot(): File {
    return File(getCipherRootDirectory(), SECRETS_DIR_NAME)
}
