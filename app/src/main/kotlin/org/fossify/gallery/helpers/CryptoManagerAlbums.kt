package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.vault.VaultContainer.FOLDER_META_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerAlbums.
 */

// ==================== FOLDER METADATA ENCRYPTION (.meta & .fmeta) ====================

internal fun CryptoManager.writeEncryptedFolderMeta(context: Context, folder: File, originalName: String) {
    val metaFile = File(folder, VAULT_SUBFOLDER_META_FILE)
    val masterKey = unlockedMasterKey ?: return
    val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
    val encryptedName = cipher.doFinal(originalName.toByteArray(Charsets.UTF_8))

    FileOutputStream(metaFile).use { fos ->
        DataOutputStream(fos).use { dos ->
            dos.write(FOLDER_META_MAGIC.toByteArray(Charsets.US_ASCII))
            dos.write(iv)
            dos.writeInt(encryptedName.size)
            dos.write(encryptedName)
            dos.flush()
            fos.fd.sync()
        }
    }
    folderNameCache[folder.absolutePath] = originalName
}


fun CryptoManager.createHiddenPhysicalAlbum(context: Context, originalName: String): File? {
    if (unlockedMasterKey == null) {
        return null
    }
    val vaultRoot = findVaultRoot(context) ?: return null
    val vaultNoMedia = File(vaultRoot, NOMEDIA)
    if (!vaultNoMedia.exists()) {
        try { vaultNoMedia.createNewFile() } catch (_: Exception) {}
    }
    var subfolder = File(vaultRoot, randomHex(6))
    while (subfolder.exists()) {
        subfolder = File(vaultRoot, randomHex(6))
    }
    if (!subfolder.mkdirs()) return null

    if (originalName.startsWith('.') && !isDefaultAlbumRawName(originalName) && originalName != VAULT_RECYCLE_BIN_NAME) {
        val noMedia = File(subfolder, NOMEDIA)
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }
    }

    writeEncryptedFolderMeta(context, subfolder, originalName)
    folderNameCache[subfolder.absolutePath] = originalName
    encryptedFolderCache[subfolder.absolutePath] = true
    return subfolder
}


fun CryptoManager.getHiddenPhysicalAlbums(context: Context): List<VaultPhysicalAlbum> {
    val albums = ArrayList<VaultPhysicalAlbum>()
    val vaultRoot = findVaultRoot(context) ?: return emptyList()

    // 1. Vault Root (Main Vault album)
    val rootFiles = vaultRoot.listFiles() ?: emptyArray()
    val rootBinCount = rootFiles.count { isEncryptedFile(it.name) }
    albums.add(
        VaultPhysicalAlbum(
            name = context.getString(org.fossify.gallery.R.string.secret_vault),
            fullPath = vaultRoot.absolutePath,
            folder = vaultRoot,
            itemCount = rootBinCount
        )
    )

    // 2. Physical subfolders in vaultRoot (excluding virtual albums JSON/ and hidden/system folders)
    rootFiles.filter { it.isDirectory }.forEach { subfolder ->
        val folderName = subfolder.name
        if (folderName.equals("JSON", ignoreCase = true) || folderName.startsWith(".")) {
            return@forEach
        }
        if (isVaultRecycleBin(subfolder)) {
            return@forEach
        }

        val decryptedName = getDecryptedFolderName(subfolder, context) ?: folderName
        val binCount = subfolder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
        albums.add(
            VaultPhysicalAlbum(
                name = decryptedName,
                fullPath = subfolder.absolutePath,
                folder = subfolder,
                itemCount = binCount
            )
        )
    }

    // 3. Custom external encrypted physical folders from config
    for (path in context.config.encryptedFolders) {
        val folder = File(path)
        if (folder.exists() && folder.isDirectory && folder.absolutePath != vaultRoot.absolutePath) {
            if (albums.none { it.fullPath == folder.absolutePath }) {
                val decryptedName = getDecryptedFolderName(folder) ?: folder.name
                val binCount = folder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
                albums.add(
                    VaultPhysicalAlbum(
                        name = decryptedName,
                        fullPath = folder.absolutePath,
                        folder = folder,
                        itemCount = binCount
                    )
                )
            }
        }
    }

    return albums
}


fun CryptoManager.getDecryptedRawFolderName(folder: File): String? {
    folderNameCache[folder.absolutePath]?.let { return it }
    val masterKey = unlockedMasterKey ?: return null

    val metaFile = File(folder, VAULT_SUBFOLDER_META_FILE)
    if (!metaFile.exists()) return null

    return try {
        DataInputStream(BufferedInputStream(FileInputStream(metaFile))).use { dis ->
            // Kanonik meta biçimi: FOLDER_META_MAGIC (6 bayt) | IV | length | ciphertext.
            val magicBytes = ByteArray(FOLDER_META_MAGIC.length)
            dis.readFully(magicBytes)
            if (String(magicBytes, Charsets.US_ASCII) != FOLDER_META_MAGIC) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            dis.readFully(iv)
            val len = dis.readInt()
            if (len <= 0 || len > 4096) return null
            val encryptedName = ByteArray(len)
            dis.readFully(encryptedName)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val name = String(cipher.doFinal(encryptedName), Charsets.UTF_8)
            folderNameCache[folder.absolutePath] = name
            name
        }
    } catch (_: Exception) {
        null
    }
}


internal fun CryptoManager.isDefaultAlbumRawName(raw: String?): Boolean {
    return raw == VAULT_DEFAULT_ALBUM_NAME ||
        raw == "Varsayılan Gizli Albüm" ||
        raw == "Default Hidden Album" ||
        raw == DEFAULT_SECRET_ALBUM_NAME
}


fun CryptoManager.isDefaultHiddenAlbum(folder: File): Boolean {
    return isDefaultAlbumRawName(getDecryptedRawFolderName(folder))
}


fun CryptoManager.isDefaultHiddenAlbumPath(path: String): Boolean {
    if (path.isEmpty()) return false
    val folder = File(path.trimEnd('/'))
    return folder.exists() && folder.isDirectory && isDefaultHiddenAlbum(folder)
}


fun CryptoManager.renameEncryptedFolder(context: Context, folder: File, newName: String): Boolean {
    val trimmed = newName.trim()
    if (trimmed.isEmpty() || !folder.exists() || !folder.isDirectory) return false
    if (!isEncryptedFolder(folder) || isVaultRecycleBin(folder) || isDefaultHiddenAlbum(folder)) return false
    if (unlockedMasterKey == null) return false
    return try {
        writeEncryptedFolderMeta(context, folder, trimmed)
        true
    } catch (_: Exception) {
        false
    }
}


fun CryptoManager.getDecryptedFolderName(folder: File, context: Context? = null): String? {
    val raw = getDecryptedRawFolderName(folder) ?: return null
    if (raw == VAULT_RECYCLE_BIN_NAME) {
        return context?.getString(org.fossify.commons.R.string.recycle_bin) ?: "Recycle Bin"
    }
    if (isDefaultAlbumRawName(raw)) {
        return DEFAULT_SECRET_ALBUM_NAME
    }
    return raw
}


fun CryptoManager.getOrCreateDefaultHiddenAlbum(context: Context): File? {
    if (unlockedMasterKey == null) {
        return null
    }
    val vaultRoot = findVaultRoot(context) ?: return null

    val subfolders = vaultRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()
    for (sub in subfolders) {
        if (isDefaultHiddenAlbum(sub)) {
            val legacyNoMedia = File(sub, NOMEDIA)
            if (legacyNoMedia.exists()) {
                try { legacyNoMedia.delete() } catch (_: Exception) {}
            }
            return sub
        }
    }

    return createHiddenPhysicalAlbum(context, VAULT_DEFAULT_ALBUM_NAME)
}


fun CryptoManager.getVaultDestinationAlbums(context: Context): List<VaultPhysicalAlbum> {
    val albums = ArrayList<VaultPhysicalAlbum>()
    val defaultAlbum = getOrCreateDefaultHiddenAlbum(context)
    val defaultAlbumPath = defaultAlbum?.absolutePath
    val vaultRoot = findVaultRoot(context) ?: return emptyList()

    if (defaultAlbum != null && defaultAlbum.exists()) {
        val count = defaultAlbum.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
        albums.add(
            VaultPhysicalAlbum(
                name = DEFAULT_SECRET_ALBUM_NAME,
                fullPath = defaultAlbum.absolutePath,
                folder = defaultAlbum,
                itemCount = count
            )
        )
    }

    val otherAlbums = ArrayList<VaultPhysicalAlbum>()
    vaultRoot.listFiles()?.filter { it.isDirectory }?.forEach { subfolder ->
        if (subfolder.name.startsWith(".") || subfolder.name.equals("JSON", ignoreCase = true)) return@forEach
        if (isVaultRecycleBin(subfolder)) return@forEach
        if (subfolder.absolutePath == defaultAlbumPath) return@forEach

        val decryptedName = getDecryptedFolderName(subfolder, context) ?: subfolder.name
        val binCount = subfolder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
        otherAlbums.add(
            VaultPhysicalAlbum(
                name = decryptedName,
                fullPath = subfolder.absolutePath,
                folder = subfolder,
                itemCount = binCount
            )
        )
    }

    for (path in context.config.encryptedFolders) {
        val folder = File(path)
        if (folder.exists() && folder.isDirectory && folder.absolutePath != vaultRoot.absolutePath && folder.absolutePath != defaultAlbumPath) {
            if (otherAlbums.none { it.fullPath == folder.absolutePath } && albums.none { it.fullPath == folder.absolutePath }) {
                val decryptedName = getDecryptedFolderName(folder, context) ?: folder.name
                val binCount = folder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
                otherAlbums.add(
                    VaultPhysicalAlbum(
                        name = decryptedName,
                        fullPath = folder.absolutePath,
                        folder = folder,
                        itemCount = binCount
                    )
                )
            }
        }
    }

    otherAlbums.sortBy { it.name.lowercase() }
    albums.addAll(otherAlbums)
    return albums
}
