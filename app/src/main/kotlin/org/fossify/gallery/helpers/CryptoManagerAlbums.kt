package org.fossify.gallery.helpers

import android.content.Context
import com.google.gson.Gson
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CryptoManager.FOLDER_ORIGIN_PURPOSE
import org.fossify.gallery.helpers.vault.VaultContainer.FOLDER_META_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val ORIGIN_STORAGE_INTERNAL = "internal"
private const val ORIGIN_STORAGE_EXTERNAL = "external"

internal data class EncryptedFolderOrigin(
    val storageType: String,
    val parentPath: String
)

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

// ==================== FOLDER ORIGIN METADATA (.origin) ====================

private fun normalizeOriginPath(path: String): String {
    val forward = path.trim().replace('\\', '/')
    if (forward == "/") return "/"
    return forward.trimEnd('/')
}

private fun isSameOrChildPath(pathNorm: String, rootNorm: String): Boolean {
    if (rootNorm.isEmpty()) return false
    return pathNorm.equals(rootNorm, ignoreCase = true) ||
        pathNorm.startsWith("$rootNorm/", ignoreCase = true)
}

private fun hasValidRelativeSegments(relPath: String): Boolean {
    if (relPath.isEmpty()) return true
    if (relPath.startsWith('/') || relPath.contains('\\') || relPath.contains('\u0000')) return false
    return relPath.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

private fun extractRelativeSubpath(pathNorm: String, rootNorm: String): String? {
    if (!isSameOrChildPath(pathNorm, rootNorm)) return null
    return if (pathNorm.equals(rootNorm, ignoreCase = true)) {
        ""
    } else {
        pathNorm.substring(rootNorm.length + 1).takeIf { hasValidRelativeSegments(it) }
    }
}

internal fun CryptoManager.buildEncryptedFolderOrigin(
    context: Context,
    sourceFolder: File
): EncryptedFolderOrigin? {
    val parentFile = sourceFolder.parentFile ?: return null
    val parentNorm = normalizeOriginPath(parentFile.absolutePath)
    if (parentNorm.isEmpty()) return null

    val internalRootRaw = runCatching { context.internalStoragePath }.getOrDefault("")
    val internalRootNorm = normalizeOriginPath(internalRootRaw)
    var internalRel: String? = null
    if (internalRootNorm.isNotEmpty()) {
        internalRel = extractRelativeSubpath(parentNorm, internalRootNorm)
        if (internalRel == null) {
            val parentCanon = runCatching { normalizeOriginPath(parentFile.canonicalPath) }.getOrNull()
            val internalCanon = runCatching {
                normalizeOriginPath(File(internalRootRaw).canonicalPath)
            }.getOrNull()
            if (parentCanon != null && internalCanon != null) {
                internalRel = extractRelativeSubpath(parentCanon, internalCanon)
            }
        }
    }

    return if (internalRel != null) {
        EncryptedFolderOrigin(ORIGIN_STORAGE_INTERNAL, internalRel)
    } else {
        EncryptedFolderOrigin(ORIGIN_STORAGE_EXTERNAL, parentNorm)
    }
}

internal fun CryptoManager.writeEncryptedFolderOrigin(
    context: Context,
    encryptedFolder: File,
    origin: EncryptedFolderOrigin
) {
    if (activeKek() == null) return
    val json = Gson().toJson(origin)
    val encrypted = encryptStringForVault(context, json, FOLDER_ORIGIN_PURPOSE)
    if (!isEncryptedVaultString(encrypted)) return

    val originFile = File(encryptedFolder, VAULT_SUBFOLDER_ORIGIN_FILE)
    FileOutputStream(originFile).use { fos ->
        fos.write(encrypted.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.fd.sync()
    }
}

internal fun CryptoManager.readEncryptedFolderOrigin(folder: File): EncryptedFolderOrigin? {
    if (activeKek() == null) return null
    val originFile = File(folder, VAULT_SUBFOLDER_ORIGIN_FILE)
    if (!originFile.isFile) return null

    return try {
        val content = originFile.readText(Charsets.UTF_8).trim()
        val json = if (isEncryptedVaultString(content)) {
            decryptStringFromVault(content, FOLDER_ORIGIN_PURPOSE)
        } else {
            null
        }
        val parsed = json?.let { Gson().fromJson(it, EncryptedFolderOrigin::class.java) }
        val validType = parsed != null && (
            parsed.storageType == ORIGIN_STORAGE_INTERNAL ||
                parsed.storageType == ORIGIN_STORAGE_EXTERNAL
            )
        if (validType && !parsed!!.parentPath.contains('\u0000')) parsed else null
    } catch (_: Exception) {
        null
    }
}

private fun CryptoManager.listMountedExternalOriginRoots(
    context: Context,
    internalRootNorm: String
): List<File> {
    val candidates = ArrayList<File>()
    candidates.addAll(listExternalStorageVolumeRoots(context))
    runCatching { context.sdCardPath }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?.let { candidates.add(File(it)) }
    runCatching { context.config.OTGPath }.getOrNull()
        ?.takeIf {
            it.isNotEmpty() &&
                !it.startsWith("otg:", ignoreCase = true) &&
                !it.startsWith("content://", ignoreCase = true)
        }
        ?.let { candidates.add(File(it)) }
    otgMountBaseOverrideForTest?.forEach { base ->
        base.listFiles()?.filter { it.isDirectory }?.let { candidates.addAll(it) }
    }

    val unique = LinkedHashMap<String, File>()
    for (root in candidates) {
        val norm = normalizeOriginPath(root.absolutePath)
        if (norm.isEmpty() || norm.equals(internalRootNorm, ignoreCase = true)) continue
        if (root.exists() && root.isDirectory) {
            unique.putIfAbsent(norm.lowercase(), root)
        }
    }
    return unique.values.toList()
}

private fun resolveInternalOriginCandidate(
    origin: EncryptedFolderOrigin,
    internalRoot: File,
    internalRootNorm: String
): Triple<File, File, Boolean>? {
    if (!hasValidRelativeSegments(origin.parentPath)) return null
    val candidate = if (origin.parentPath.isEmpty()) internalRoot else File(internalRoot, origin.parentPath)
    val candidateNorm = normalizeOriginPath(candidate.absolutePath)
    return if (isSameOrChildPath(candidateNorm, internalRootNorm)) {
        Triple(candidate, internalRoot, false)
    } else {
        null
    }
}

private fun CryptoManager.resolveExternalOriginCandidate(
    context: Context,
    origin: EncryptedFolderOrigin,
    internalRootNorm: String
): Triple<File, File, Boolean>? {
    val originParentNorm = normalizeOriginPath(origin.parentPath)
    if (originParentNorm.isEmpty() || originParentNorm.split('/').any { it == "." || it == ".." }) {
        return null
    }
    val matchedRoot = listMountedExternalOriginRoots(context, internalRootNorm).firstOrNull { root ->
        isSameOrChildPath(originParentNorm, normalizeOriginPath(root.absolutePath))
    } ?: return null

    val rootNorm = normalizeOriginPath(matchedRoot.absolutePath)
    val rel = extractRelativeSubpath(originParentNorm, rootNorm) ?: return null
    val candidate = if (rel.isEmpty()) matchedRoot else File(matchedRoot, rel)
    return Triple(candidate, matchedRoot, true)
}

private fun CryptoManager.resolveOriginCandidateAndRoot(
    context: Context,
    origin: EncryptedFolderOrigin,
    internalRoot: File,
    internalRootNorm: String
): Triple<File, File, Boolean>? {
    return if (origin.storageType == ORIGIN_STORAGE_INTERNAL) {
        resolveInternalOriginCandidate(origin, internalRoot, internalRootNorm)
    } else {
        resolveExternalOriginCandidate(context, origin, internalRootNorm)
    }
}

private fun CryptoManager.isDisallowedDecryptParent(
    context: Context,
    candidateParent: File,
    internalRoot: File,
    internalRootNorm: String,
    storageRoot: File
): Boolean {
    val candidateNorm = normalizeOriginPath(candidateParent.absolutePath)
    val storageRootNorm = normalizeOriginPath(storageRoot.absolutePath)
    val cipherRootNorm = normalizeOriginPath(File(internalRoot, CIPHER_APP_DIR_NAME).absolutePath)
    val vaultRootNorm = findVaultRoot(context)?.let { normalizeOriginPath(it.absolutePath) }
    val canonicalEscape = if (candidateParent.exists()) {
        val candidateCanon = runCatching { normalizeOriginPath(candidateParent.canonicalPath) }.getOrNull()
        val storageCanon = runCatching { normalizeOriginPath(storageRoot.canonicalPath) }.getOrNull()
        candidateCanon != null && storageCanon != null && !isSameOrChildPath(candidateCanon, storageCanon)
    } else {
        false
    }

    return !isSameOrChildPath(candidateNorm, storageRootNorm) ||
        HiddenFoldersPolicy.isAndroidRestrictedSubpath(candidateNorm, internalRootNorm) ||
        isSameOrChildPath(candidateNorm, cipherRootNorm) ||
        (vaultRootNorm != null && isSameOrChildPath(candidateNorm, vaultRootNorm)) ||
        canonicalEscape
}

private fun findExistingAncestorWithinRoot(candidateParent: File, storageRoot: File): File? {
    val rootNorm = normalizeOriginPath(storageRoot.absolutePath)
    var current: File? = candidateParent
    var found: File? = null
    var done = false
    while (current != null && !done) {
        val currentNorm = normalizeOriginPath(current.absolutePath)
        if (!isSameOrChildPath(currentNorm, rootNorm)) {
            done = true
        } else if (current.exists()) {
            found = if (current.isDirectory) current else null
            done = true
        } else if (currentNorm.equals(rootNorm, ignoreCase = true)) {
            done = true
        } else {
            current = current.parentFile
        }
    }
    return found
}

internal fun CryptoManager.resolveValidOriginParentDir(context: Context, folder: File): File? {
    if (isDefaultHiddenAlbum(folder) || isVaultRecycleBin(folder)) return null
    val origin = readEncryptedFolderOrigin(folder) ?: return null
    val internalRootRaw = runCatching { context.internalStoragePath }.getOrDefault("").trimEnd('/', '\\')
    if (internalRootRaw.isEmpty()) return null

    val internalRoot = File(internalRootRaw)
    val internalRootNorm = normalizeOriginPath(internalRoot.absolutePath)
    val resolved = resolveOriginCandidateAndRoot(
        context = context,
        origin = origin,
        internalRoot = internalRoot,
        internalRootNorm = internalRootNorm
    )
    var validParent: File? = null
    if (resolved != null) {
        val (candidateParent, storageRoot, isExternal) = resolved
        val disallowed = isDisallowedDecryptParent(
            context = context,
            candidateParent = candidateParent,
            internalRoot = internalRoot,
            internalRootNorm = internalRootNorm,
            storageRoot = storageRoot
        )
        val ancestor = if (disallowed) null else findExistingAncestorWithinRoot(candidateParent, storageRoot)
        val writable = ancestor != null && if (isExternal) canWriteToDirectory(ancestor) else ancestor.canWrite()
        if (writable) {
            validParent = candidateParent
        }
    }
    return validParent
}
