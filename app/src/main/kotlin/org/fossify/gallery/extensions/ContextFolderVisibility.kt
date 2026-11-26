package org.fossify.gallery.extensions

import android.content.Context
import android.database.Cursor
import android.os.Environment
import org.fossify.commons.extensions.doesThisOrParentHaveNoMedia
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isPortrait
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.ExcludedMenuPolicy
import org.fossify.gallery.helpers.HiddenFoldersPolicy
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ShareMetadataSanitizer
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.getCipherRootDirectory
import org.fossify.gallery.helpers.getDecryptedFolderName
import org.fossify.gallery.helpers.isDefaultHiddenAlbum
import org.fossify.gallery.helpers.isEncryptedFolder
import org.fossify.gallery.helpers.isEncryptedFolderPath
import org.fossify.gallery.helpers.isVaultRecycleBin
import org.fossify.gallery.helpers.isVaultRecycleBinPath
import org.fossify.gallery.models.Directory
import java.io.File

fun Context.isFolderOrAlbumHidden(
    path: String,
    folderNoMediaStatuses: HashMap<String, Boolean>? = null
): Boolean {
    if (path.isEmpty() || path == FAVORITES || path == RECYCLE_BIN) {
        return false
    }

    if (path.isVirtualAlbumPath()) {
        return isHiddenVirtualAlbumPath(path)
    }

    if (CryptoManager.isEncryptedFolderPath(this, path)) {
        return true
    }

    val file = File(path)
    if (CryptoManager.isEncryptedFolder(file)) {
        return true
    }

    if (file.name.startsWith('.') || path.contains("/.")) {
        return true
    }

    if (path.isThisOrParentFolderHidden()) {
        return true
    }

    val noMediaPath = "$path/$NOMEDIA"
    if (folderNoMediaStatuses != null && folderNoMediaStatuses.containsKey(noMediaPath)) {
        if (folderNoMediaStatuses[noMediaPath] == true) {
            return true
        }
    } else if (file.exists() && File(file, NOMEDIA).exists()) {
        folderNoMediaStatuses?.put(noMediaPath, true)
        return true
    }

    val statuses = folderNoMediaStatuses ?: HashMap()
    if (path.doesThisOrParentHaveNoMedia(statuses, null)) {
        return true
    }

    return false
}

fun Context.isFolderOrAlbumHidden(
    directory: Directory,
    folderNoMediaStatuses: HashMap<String, Boolean>? = null
): Boolean = isFolderOrAlbumHidden(directory.path, folderNoMediaStatuses)

fun Context.isEncryptedVaultAlbum(path: String): Boolean {
    if (path.isEmpty() || path == FAVORITES || path == RECYCLE_BIN) {
        return false
    }

    if (path.isVirtualAlbumPath()) {
        return isHiddenVirtualAlbumPath(path)
    }

    return CryptoManager.isEncryptedFolderPath(this, path)
}

fun Context.isEncryptedVaultAlbum(directory: Directory): Boolean = isEncryptedVaultAlbum(directory.path)

fun Context.isCipherSystemPath(path: String): Boolean {
    val clean = path.trimEnd('/')
    if (clean.isEmpty()) return false
    if (ShareMetadataSanitizer.isShareTempPath(clean)) {
        return true
    }

    val cipherRoot = CryptoManager.getCipherRootDirectory().absolutePath.trimEnd('/')
    if (clean.equals(cipherRoot, ignoreCase = true) || clean.startsWith("$cipherRoot/", ignoreCase = true)) {
        return true
    }

    val vaultRoot = CryptoManager.findVaultRoot(this)?.absolutePath?.trimEnd('/')
    if (vaultRoot != null && (clean.equals(vaultRoot, ignoreCase = true) || clean.startsWith("$vaultRoot/", ignoreCase = true))) {
        return true
    }

    val configuredVaultRoot = config.vaultRootPath.trimEnd('/')
    if (configuredVaultRoot.isNotEmpty() && (clean.equals(configuredVaultRoot, ignoreCase = true) || clean.startsWith("$configuredVaultRoot/", ignoreCase = true))) {
        return true
    }

    return CryptoManager.isEncryptedFolderPath(this, clean) || isEncryptedVaultAlbum(clean)
}

/**
 * Returns true if [path] is a Cipher/vault system directory or a restricted Android storage
 * subpath that must never be added to or scanned via `includedFolders`.
 */
fun Context.isIncludedFolderDisallowed(path: String): Boolean {
    val clean = path.trimEnd('/')
    if (clean.isEmpty()) return true
    return isCipherSystemPath(clean) ||
        HiddenFoldersPolicy.isAndroidRestrictedSubpath(clean, internalStoragePath)
}

fun Context.isEncryptedAlbumExcluded(path: String): Boolean {
    if (path.isEmpty() || path == FAVORITES || path == RECYCLE_BIN || path.isVirtualAlbumPath()) {
        return false
    }
    val folder = File(path.trimEnd('/'))
    if (!folder.exists() || !folder.isDirectory) {
        return false
    }
    if (!CryptoManager.isEncryptedFolder(folder) && !CryptoManager.isEncryptedFolderPath(this, folder.absolutePath)) {
        return false
    }
    return ExcludedMenuPolicy.isEncryptedAlbumExcluded(
        albumPath = folder.absolutePath,
        folderNameOnDisk = folder.name,
        decryptedName = CryptoManager.getDecryptedFolderName(folder, this),
        hasNoMediaFile = File(folder, NOMEDIA).exists(),
        isVaultRecycleBin = CryptoManager.isVaultRecycleBin(folder),
        isDefaultAlbum = CryptoManager.isDefaultHiddenAlbum(folder),
        excludedFolders = config.excludedFolders
    )
}

fun Context.hasExcludedVaultFolders(): Boolean {
    if (!CryptoManager.isVaultUnlocked) {
        return false
    }
    val filterMedia = config.filterMedia
    if (filterMedia == 0) {
        return false
    }

    val candidates = LinkedHashSet<File>()
    val vaultRoot = CryptoManager.findVaultRoot(this)
    if (vaultRoot != null && vaultRoot.isDirectory) {
        vaultRoot.listFiles()?.forEach { child ->
            if (child.isDirectory && !child.name.equals("JSON", ignoreCase = true) && CryptoManager.isEncryptedFolder(child)) {
                candidates.add(child)
            }
        }
    }
    config.encryptedFolders.forEach { path ->
        val folder = File(path)
        if (folder.exists() && folder.isDirectory) {
            candidates.add(folder)
        }
    }

    return candidates.any { folder ->
        isEncryptedAlbumExcluded(folder.absolutePath) && folderHasMatchingVaultMedia(folder, filterMedia)
    }
}

fun Context.hasExcludedNormalFolders(): Boolean {
    val filterMedia = config.filterMedia
    if (filterMedia == 0) {
        return false
    }

    val excludedFolders = config.excludedFolders
    if (excludedFolders.isEmpty()) {
        return false
    }

    val otgPath = config.OTGPath
    val includedPaths = config.includedFolders
    val showHiddenFiles = config.shouldShowHidden || config.temporarilyShowHiddenFolders

    for (rawPath in excludedFolders) {
        val excludedPath = rawPath.trimEnd('/')
        if (excludedPath.isEmpty() || isCipherSystemPath(excludedPath)) continue
        if (excludedPath.isThisOrParentIncluded(includedPaths)) continue
        if (!getDoesFilePathExist(excludedPath, otgPath)) continue
        val dir = File(excludedPath)
        if (!dir.isDirectory) continue
        if (folderTreeHasDisplayableMedia(dir, filterMedia, showHiddenFiles) ||
            hasMediaInExcludedSubtreeViaMediaStore(excludedPath, filterMedia, includedPaths, showHiddenFiles)
        ) {
            return true
        }
    }
    return false
}

fun Context.hasHiddenNormalFolders(): Boolean {
    val filterMedia = config.filterMedia
    if (filterMedia == 0) {
        return false
    }

    val otgPath = config.OTGPath
    val includedPaths = config.includedFolders
    val canInspectDisk = !isRPlus() || isExternalStorageManager()

    val noMediaFolders = getNoMediaFoldersSync()
    for (rawPath in noMediaFolders) {
        val folderPath = rawPath.trimEnd('/')
        if (folderPath.isEmpty() ||
            isCipherSystemPath(folderPath) ||
            HiddenFoldersPolicy.isAndroidRestrictedSubpath(folderPath, internalStoragePath) ||
            folderPath.isThisOrParentIncluded(includedPaths) ||
            !getDoesFilePathExist(folderPath, otgPath)
        ) {
            continue
        }
        val dir = File(folderPath)
        if (!canInspectDisk) {
            if (dir.exists()) return true
        } else if (dir.isDirectory && folderTreeHasDisplayableMedia(dir, filterMedia, showHiddenFiles = true)) {
            return true
        }
    }

    val knownParents = collectKnownNormalParentFolders()
    if (findHiddenSubfoldersOnDisk(knownParents, requireDisplayableMedia = canInspectDisk).isNotEmpty()) {
        return true
    }

    if (!canInspectDisk) {
        return config.knownHiddenFolders.any { rawPath ->
            val clean = rawPath.trimEnd('/')
            clean.isNotEmpty() &&
                !isCipherSystemPath(clean) &&
                !HiddenFoldersPolicy.isAndroidRestrictedSubpath(clean, internalStoragePath) &&
                !clean.isThisOrParentIncluded(includedPaths)
        }
    }

    return false
}

fun Context.getHiddenFoldersList(callback: (folders: ArrayList<String>) -> Unit) {
    ensureBackgroundThread {
        callback(getHiddenFoldersListSync())
    }
}

fun Context.getHiddenFoldersListSync(): ArrayList<String> {
    val canInspectDisk = !isRPlus() || isExternalStorageManager()
    val liveDetected = LinkedHashSet<String>()
    val otgPath = config.OTGPath

    getNoMediaFoldersSync().forEach { rawPath ->
        val clean = rawPath.trimEnd('/')
        if (clean.isNotEmpty() &&
            !isCipherSystemPath(clean) &&
            !HiddenFoldersPolicy.isAndroidRestrictedSubpath(clean, internalStoragePath)
        ) {
            if (!canInspectDisk || getDoesFilePathExist(clean, otgPath)) {
                liveDetected.add(clean)
            }
        }
    }

    val knownParents = collectKnownNormalParentFolders()
    findHiddenSubfoldersOnDisk(knownParents, requireDisplayableMedia = false).forEach { rawPath ->
        val clean = rawPath.trimEnd('/')
        if (clean.isNotEmpty() &&
            !isCipherSystemPath(clean) &&
            !HiddenFoldersPolicy.isAndroidRestrictedSubpath(clean, internalStoragePath)
        ) {
            liveDetected.add(clean)
        }
    }

    if (canInspectDisk) {
        val roots = LinkedHashSet<String>()
        if (internalStoragePath.isNotEmpty()) roots.add(internalStoragePath.trimEnd('/'))
        if (sdCardPath.isNotEmpty()) roots.add(sdCardPath.trimEnd('/'))
        val visited = HashSet<String>()
        for (root in roots) {
            val rootFile = File(root)
            if (rootFile.isDirectory) {
                collectNestedHiddenFolders(rootFile, depth = 0, maxDepth = 3, visited = visited, outFolders = liveDetected)
            }
        }
    }

    val noMediaCache = HashMap<String, Boolean>()
    val merged = HiddenFoldersPolicy.mergeKnownHiddenFolders(
        cachedKnownHidden = config.knownHiddenFolders,
        liveDetectedHidden = liveDetected,
        canInspectDisk = canInspectDisk,
        isCipherSystemPath = { isCipherSystemPath(it) },
        isRestrictedPath = { HiddenFoldersPolicy.isAndroidRestrictedSubpath(it, internalStoragePath) },
        isStillHiddenOnDisk = { path ->
            val dir = File(path)
            dir.exists() && dir.isDirectory && (
                HiddenFoldersPolicy.isDotHiddenPath(path) ||
                    File(dir, NOMEDIA).exists() ||
                    path.doesThisOrParentHaveNoMedia(noMediaCache, null)
                )
        }
    )

    if (merged != config.knownHiddenFolders) {
        config.knownHiddenFolders = merged
    }

    return ArrayList(merged.sortedWith(String.CASE_INSENSITIVE_ORDER))
}

fun Context.findHiddenSubfoldersOnDisk(
    knownParentFolders: Collection<String>,
    requireDisplayableMedia: Boolean = true
): ArrayList<String> {
    val result = LinkedHashSet<String>()
    val filterMedia = config.filterMedia
    if (requireDisplayableMedia && filterMedia == 0) {
        return ArrayList()
    }

    val includedPaths = config.includedFolders
    val parentCandidates = LinkedHashSet<String>()

    fun addStorageRoots(rootPath: String) {
        val cleanRoot = rootPath.trimEnd('/')
        if (cleanRoot.isEmpty()) return
        parentCandidates.add(cleanRoot)
        listOf(
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS
        ).forEach { sub ->
            parentCandidates.add("$cleanRoot/$sub")
        }
    }

    addStorageRoots(internalStoragePath)
    if (sdCardPath.isNotEmpty()) {
        addStorageRoots(sdCardPath)
    }
    knownParentFolders.forEach { raw ->
        val clean = raw.trimEnd('/')
        if (clean.isNotEmpty() &&
            clean != FAVORITES &&
            clean != RECYCLE_BIN &&
            !clean.isVirtualAlbumPath() &&
            !isCipherSystemPath(clean)
        ) {
            parentCandidates.add(clean)
        }
    }

    val internalRootClean = internalStoragePath.trimEnd('/')
    val visitedDotDirs = HashSet<String>()

    for (parentPath in parentCandidates) {
        if (isCipherSystemPath(parentPath) ||
            HiddenFoldersPolicy.isAndroidRestrictedSubpath(parentPath, internalStoragePath)
        ) {
            continue
        }
        val parentDir = File(parentPath)
        if (!parentDir.isDirectory) continue
        val children = parentDir.listFiles() ?: continue
        for (child in children) {
            if (!child.isDirectory) continue
            if (parentPath.equals(internalRootClean, ignoreCase = true) && child.name.equals("Android", ignoreCase = true)) {
                continue
            }
            val childPath = child.absolutePath
            if (isCipherSystemPath(childPath) || (requireDisplayableMedia && childPath.isThisOrParentIncluded(includedPaths))) {
                continue
            }

            if (child.name.startsWith('.')) {
                if (requireDisplayableMedia) {
                    collectDotTreeMediaFolders(child, filterMedia, showHiddenFiles = true, includedPaths, depth = 0, maxDepth = 2, visitedDotDirs, result)
                } else {
                    result.add(childPath)
                }
            } else if (File(child, NOMEDIA).exists()) {
                if (!requireDisplayableMedia || folderTreeHasDisplayableMedia(child, filterMedia, showHiddenFiles = true)) {
                    result.add(childPath)
                }
            }
        }
    }

    return ArrayList(result)
}

private fun Context.collectKnownNormalParentFolders(): LinkedHashSet<String> {
    val parents = LinkedHashSet<String>()
    config.everShownFolders.forEach { path ->
        if (path != FAVORITES && path != RECYCLE_BIN && !path.isVirtualAlbumPath() && !isCipherSystemPath(path)) {
            parents.add(path)
        }
    }
    try {
        directoryDB.getAll().forEach { dir ->
            if (!dir.isVirtualAlbum() && !dir.areFavorites() && !dir.isRecycleBin() && !isCipherSystemPath(dir.path)) {
                parents.add(dir.path)
            }
        }
    } catch (_: Exception) {}
    return parents
}

private fun Context.collectDotTreeMediaFolders(
    dir: File,
    filterMedia: Int,
    showHiddenFiles: Boolean,
    includedPaths: MutableSet<String>,
    depth: Int,
    maxDepth: Int,
    visited: HashSet<String>,
    outFolders: LinkedHashSet<String>
) {
    val path = dir.absolutePath
    if (!visited.add(path)) return
    if (isCipherSystemPath(path) || path.isThisOrParentIncluded(includedPaths)) return

    val children = dir.listFiles() ?: return
    var hasDirectMedia = false
    for (child in children) {
        if (child.isFile) {
            if (!hasDirectMedia && (showHiddenFiles || !child.name.startsWith('.')) && child.absolutePath.matchesFilterMedia(filterMedia)) {
                hasDirectMedia = true
            }
        } else if (child.isDirectory) {
            if (filterMedia and TYPE_PORTRAITS != 0 && child.name.startsWith("img_", true)) {
                val portraitFiles = child.listFiles()
                if (portraitFiles != null && portraitFiles.any { it.name.contains("cover", true) || it.isFile }) {
                    hasDirectMedia = true
                }
            }
            if (depth < maxDepth) {
                collectDotTreeMediaFolders(child, filterMedia, showHiddenFiles, includedPaths, depth + 1, maxDepth, visited, outFolders)
            }
        }
    }
    if (hasDirectMedia) {
        outFolders.add(path)
    }
}

private fun Context.collectNestedHiddenFolders(
    dir: File,
    depth: Int,
    maxDepth: Int,
    visited: HashSet<String>,
    outFolders: LinkedHashSet<String>
) {
    val path = dir.absolutePath.trimEnd('/')
    val canonical = try {
        dir.canonicalPath
    } catch (_: Exception) {
        path
    }
    if (!visited.add(canonical)) return
    if (isCipherSystemPath(path) ||
        HiddenFoldersPolicy.isAndroidRestrictedSubpath(path, internalStoragePath)
    ) {
        return
    }

    if (depth > 0 && (dir.name.startsWith('.') || File(dir, NOMEDIA).exists())) {
        outFolders.add(path)
        return
    }

    if (depth >= maxDepth) return
    val children = dir.listFiles() ?: return
    val internalRootClean = internalStoragePath.trimEnd('/')
    for (child in children) {
        if (!child.isDirectory) continue
        if (depth == 0 && path.equals(internalRootClean, ignoreCase = true) && child.name.equals("Android", ignoreCase = true)) {
            val androidMedia = File(child, "media")
            if (androidMedia.isDirectory) {
                collectNestedHiddenFolders(androidMedia, depth + 2, maxDepth + 2, visited, outFolders)
            }
            continue
        }
        collectNestedHiddenFolders(child, depth + 1, maxDepth, visited, outFolders)
    }
}

fun Context.isDirectoryExcludedForBadge(
    path: String,
    excludedFolders: MutableSet<String> = config.excludedFolders,
    includedFolders: MutableSet<String> = config.includedFolders
): Boolean {
    if (path.isEmpty() ||
        path == FAVORITES ||
        path == RECYCLE_BIN ||
        path.isVirtualAlbumPath() ||
        isCipherSystemPath(path) ||
        isEncryptedVaultAlbum(path) ||
        CryptoManager.isVaultRecycleBinPath(this, path)
    ) {
        return false
    }
    if (path.isThisOrParentIncluded(includedFolders)) {
        return false
    }
    return path.isThisOrParentExcluded(excludedFolders)
}

fun Context.isDirectoryHiddenForBadge(
    path: String,
    includedFolders: MutableSet<String> = config.includedFolders,
    noMediaStatuses: HashMap<String, Boolean> = HashMap()
): Boolean {
    if (path.isEmpty() ||
        path == FAVORITES ||
        path == RECYCLE_BIN ||
        path.isVirtualAlbumPath() ||
        isCipherSystemPath(path) ||
        isEncryptedVaultAlbum(path) ||
        CryptoManager.isVaultRecycleBinPath(this, path)
    ) {
        return false
    }
    if (path.isThisOrParentIncluded(includedFolders)) {
        return false
    }
    if (HiddenFoldersPolicy.isDotHiddenPath(path) || path.isThisOrParentFolderHidden()) {
        return true
    }
    if (File(path, NOMEDIA).exists() || path.doesThisOrParentHaveNoMedia(noMediaStatuses, null)) {
        return true
    }
    return false
}

private fun Context.hasMediaInExcludedSubtreeViaMediaStore(
    excludedPath: String,
    filterMedia: Int,
    includedPaths: MutableSet<String>,
    showHiddenFiles: Boolean
): Boolean {
    val cleanExcluded = excludedPath.trimEnd('/')
    val uri = android.provider.MediaStore.Files.getContentUri("external")
    val projection = arrayOf(android.provider.MediaStore.Files.FileColumns.DATA)
    val selection = "${android.provider.MediaStore.Files.FileColumns.DATA} LIKE ?"
    val likeArg = if (cleanExcluded.isEmpty()) "/%" else "$cleanExcluded/%"
    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(uri, projection, selection, arrayOf(likeArg), null)
        if (cursor?.moveToFirst() == true) {
            do {
                val filePath = cursor.getStringValue(android.provider.MediaStore.Files.FileColumns.DATA) ?: continue
                val file = File(filePath)
                if (!showHiddenFiles && file.name.startsWith('.')) continue
                if (!filePath.matchesFilterMedia(filterMedia)) continue
                val parentPath = file.parent ?: continue
                if (isCipherSystemPath(parentPath) || parentPath.isThisOrParentIncluded(includedPaths)) continue
                if (file.isFile) {
                    return true
                }
            } while (cursor.moveToNext())
        }
    } catch (_: Exception) {
    } finally {
        cursor?.close()
    }
    return false
}

private fun folderHasMatchingVaultMedia(folder: File, filterMedia: Int): Boolean {
    val children = folder.listFiles() ?: return false
    for (child in children) {
        if (!child.isFile) continue
        if (CryptoManager.isEncryptedFile(child.name)) {
            val meta = CryptoManager.readMetadata(child) ?: continue
            val matches = when (meta.mediaType) {
                TYPE_VIDEOS -> filterMedia and TYPE_VIDEOS != 0
                TYPE_IMAGES -> filterMedia and TYPE_IMAGES != 0
                TYPE_GIFS -> filterMedia and TYPE_GIFS != 0
                TYPE_RAWS -> filterMedia and TYPE_RAWS != 0
                TYPE_SVGS -> filterMedia and TYPE_SVGS != 0
                else -> false
            }
            if (matches) return true
        } else if (child.absolutePath.matchesFilterMedia(filterMedia)) {
            return true
        }
    }
    return false
}

private fun folderTreeHasDisplayableMedia(
    dir: File,
    filterMedia: Int,
    showHiddenFiles: Boolean,
    depth: Int = 0,
    maxDepth: Int = 3
): Boolean {
    if (directoryHasDirectDisplayableMedia(dir, filterMedia, showHiddenFiles)) {
        return true
    }
    if (depth >= maxDepth) {
        return false
    }
    val children = dir.listFiles() ?: return false
    for (child in children) {
        if (child.isDirectory && folderTreeHasDisplayableMedia(child, filterMedia, showHiddenFiles, depth + 1, maxDepth)) {
            return true
        }
    }
    return false
}

private fun directoryHasDirectDisplayableMedia(dir: File, filterMedia: Int, showHiddenFiles: Boolean): Boolean {
    val children = dir.listFiles() ?: return false
    for (child in children) {
        if (child.isFile) {
            if (!showHiddenFiles && child.name.startsWith('.')) continue
            if (child.absolutePath.matchesFilterMedia(filterMedia)) {
                return true
            }
        } else if (child.isDirectory && filterMedia and TYPE_PORTRAITS != 0 && child.name.startsWith("img_", true)) {
            val portraitFiles = child.listFiles()
            if (portraitFiles != null && portraitFiles.isNotEmpty()) {
                return true
            }
        }
    }
    return false
}

private fun String.matchesFilterMedia(filterMedia: Int): Boolean {
    val isImage = isImageFast()
    val isVideo = if (isImage) false else isVideoFast()
    val isGif = if (isImage || isVideo) false else isGif()
    val isRaw = if (isImage || isVideo || isGif) false else isRawFast()
    val isSvg = if (isImage || isVideo || isGif || isRaw) false else isSvg()
    if (!isImage && !isVideo && !isGif && !isRaw && !isSvg) {
        return false
    }
    return when {
        isVideo -> filterMedia and TYPE_VIDEOS != 0
        isImage -> filterMedia and TYPE_IMAGES != 0
        isGif -> filterMedia and TYPE_GIFS != 0
        isRaw -> filterMedia and TYPE_RAWS != 0
        isSvg -> filterMedia and TYPE_SVGS != 0
        else -> false
    }
}
