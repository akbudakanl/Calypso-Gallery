package org.fossify.gallery.helpers

import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.isVirtualAlbumPath
import org.fossify.gallery.models.Directory

enum class PickDirectoryMode {
    DEFAULT,
    UNENCRYPTED_COPY,
    UNENCRYPTED_MOVE,
    ENCRYPTED_COPY,
    ENCRYPTED_MOVE,
    DECRYPT_MOVE
}

/**
 * Centralizes visibility and destination filtering rules for Copy to, Move to, Add to virtual album, Encrypt, and Decrypt.
 */
object CopyMovePolicy {
    fun isVirtualOrFavoritesPath(directoryPath: String): Boolean {
        return directoryPath == FAVORITES || directoryPath.isVirtualAlbumPath()
    }

    fun isRealAlbumSource(
        directoryPath: String,
        isInRecycleBin: Boolean = false,
        isVaultRecycleBin: Boolean = false
    ): Boolean {
        return !isVirtualOrFavoritesPath(directoryPath) &&
            directoryPath != RECYCLE_BIN &&
            !isInRecycleBin &&
            !isVaultRecycleBin
    }

    fun canMoveFrom(
        directoryPath: String,
        isInRecycleBin: Boolean = false,
        isVaultRecycleBin: Boolean = false
    ): Boolean {
        return isRealAlbumSource(
            directoryPath = directoryPath,
            isInRecycleBin = isInRecycleBin,
            isVaultRecycleBin = isVaultRecycleBin
        )
    }

    fun canAddToVirtualAlbum(
        directoryPath: String,
        isMixedEncryption: Boolean = false,
        isInRecycleBin: Boolean = false,
        isVaultRecycleBin: Boolean = false
    ): Boolean {
        return !isMixedEncryption &&
            directoryPath != RECYCLE_BIN &&
            !isInRecycleBin &&
            !isVaultRecycleBin
    }

    fun canEncryptFrom(
        directoryPath: String,
        hasEncryptedSelection: Boolean,
        isInRecycleBin: Boolean = false,
        isVaultRecycleBin: Boolean = false
    ): Boolean {
        return !hasEncryptedSelection &&
            isRealAlbumSource(
                directoryPath = directoryPath,
                isInRecycleBin = isInRecycleBin,
                isVaultRecycleBin = isVaultRecycleBin
            )
    }

    fun canDecryptFrom(
        directoryPath: String,
        hasHiddenOrEncryptedSelection: Boolean,
        isInRecycleBin: Boolean = false,
        isVaultRecycleBin: Boolean = false
    ): Boolean {
        return hasHiddenOrEncryptedSelection &&
            isRealAlbumSource(
                directoryPath = directoryPath,
                isInRecycleBin = isInRecycleBin,
                isVaultRecycleBin = isVaultRecycleBin
            )
    }

    fun resolveCopyMoveMode(isCopyOperation: Boolean, isEncryptedSource: Boolean): PickDirectoryMode {
        return when {
            isEncryptedSource && isCopyOperation -> PickDirectoryMode.ENCRYPTED_COPY
            isEncryptedSource && !isCopyOperation -> PickDirectoryMode.ENCRYPTED_MOVE
            !isEncryptedSource && isCopyOperation -> PickDirectoryMode.UNENCRYPTED_COPY
            else -> PickDirectoryMode.UNENCRYPTED_MOVE
        }
    }

    fun isCopyMode(mode: PickDirectoryMode): Boolean {
        return mode == PickDirectoryMode.UNENCRYPTED_COPY || mode == PickDirectoryMode.ENCRYPTED_COPY
    }

    fun isEncryptedTargetMode(mode: PickDirectoryMode): Boolean {
        return mode == PickDirectoryMode.ENCRYPTED_COPY || mode == PickDirectoryMode.ENCRYPTED_MOVE
    }

    /**
     * Unencrypted copy/move and vault decrypt all target normal storage folders, so `.nomedia`
     * hidden folders must follow [temporarilyShowHiddenFolders] (plus explicit [showHiddenMedia] /
     * [forceShowHiddenAndExcluded]), never the vault-unlock session flag [temporarilyShowHidden].
     */
    fun shouldIncludeUnencryptedHiddenFoldersInPicker(
        mode: PickDirectoryMode,
        temporarilyShowHiddenFolders: Boolean,
        showHiddenMedia: Boolean,
        temporarilyShowHidden: Boolean,
        isVaultUnlocked: Boolean,
        isHiddenPasswordProtectionOn: Boolean,
        forceShowHiddenAndExcluded: Boolean
    ): Boolean {
        if (temporarilyShowHiddenFolders || showHiddenMedia || forceShowHiddenAndExcluded) {
            return true
        }
        return mode == PickDirectoryMode.DEFAULT &&
            temporarilyShowHidden &&
            (!isHiddenPasswordProtectionOn || isVaultUnlocked)
    }

    /**
     * In copy/move/decrypt pickers, dot-prefixed media files follow [showHiddenMedia] rather than
     * the vault-unlock session flag inside [shouldShowHidden].
     */
    fun shouldShowHiddenMediaInPicker(
        mode: PickDirectoryMode,
        showHiddenMedia: Boolean,
        shouldShowHidden: Boolean
    ): Boolean {
        return if (mode == PickDirectoryMode.DEFAULT) shouldShowHidden else showHiddenMedia
    }

    fun isAllowedDestination(
        mode: PickDirectoryMode,
        isVirtualAlbum: Boolean,
        isEncryptedAlbum: Boolean,
        isRecycleBin: Boolean,
        isFavorites: Boolean
    ): Boolean {
        if (isRecycleBin || isFavorites) {
            return false
        }

        return when (mode) {
            PickDirectoryMode.DEFAULT -> true
            PickDirectoryMode.UNENCRYPTED_COPY -> !isEncryptedAlbum && !isVirtualAlbum
            PickDirectoryMode.UNENCRYPTED_MOVE -> !isEncryptedAlbum && !isVirtualAlbum
            PickDirectoryMode.ENCRYPTED_COPY -> isEncryptedAlbum && !isVirtualAlbum
            PickDirectoryMode.ENCRYPTED_MOVE -> isEncryptedAlbum && !isVirtualAlbum
            PickDirectoryMode.DECRYPT_MOVE -> !isEncryptedAlbum && !isVirtualAlbum
        }
    }

    fun isEncryptedVaultFileName(fileName: String): Boolean {
        return (!fileName.startsWith('.') && fileName.endsWith(ENCRYPTED_BIN_EXT, ignoreCase = true)) ||
            fileName.endsWith(ENCRYPTED_FILE_EXT, ignoreCase = true)
    }

    fun shouldCollectFolderEntry(
        fileName: String,
        isDirectory: Boolean,
        isEncryptedFolder: Boolean,
        showHidden: Boolean,
        filterMedia: Int
    ): Boolean {
        if (isDirectory || fileName.isEmpty()) {
            return false
        }
        if (isEncryptedFolder) {
            return isEncryptedVaultFileName(fileName)
        }
        if (fileName.endsWith(ENCRYPTED_BIN_EXT, ignoreCase = true) ||
            fileName.endsWith(ENCRYPTED_FILE_EXT, ignoreCase = true)
        ) {
            return false
        }
        if (!showHidden && fileName.startsWith('.')) {
            return false
        }
        if (!fileName.isMediaFile()) {
            return false
        }
        return (fileName.isImageFast() && filterMedia and TYPE_IMAGES != 0) ||
            (fileName.isVideoFast() && filterMedia and TYPE_VIDEOS != 0) ||
            (fileName.isGif() && filterMedia and TYPE_GIFS != 0) ||
            (fileName.isRawFast() && filterMedia and TYPE_RAWS != 0) ||
            (fileName.isSvg() && filterMedia and TYPE_SVGS != 0)
    }

    fun shouldIgnoreMediaFilterForPicker(isPickingCopyMoveDestination: Boolean): Boolean {
        return isPickingCopyMoveDestination
    }

    fun allMediaTypesFilter(): Int {
        return TYPE_IMAGES or TYPE_VIDEOS or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS or TYPE_PORTRAITS
    }

    fun mergeDestinationDirectories(
        cachedDirs: List<Directory>,
        liveDirs: List<Directory>,
        keyOf: (String) -> String = { it.trimEnd('/').lowercase() }
    ): ArrayList<Directory> {
        val merged = ArrayList<Directory>(cachedDirs.size + liveDirs.size)
        val indexByKey = HashMap<String, Int>(cachedDirs.size + liveDirs.size)

        for (dir in cachedDirs) {
            val key = keyOf(dir.path)
            if (!indexByKey.containsKey(key)) {
                indexByKey[key] = merged.size
                merged.add(dir)
            }
        }

        for (liveDir in liveDirs) {
            val key = keyOf(liveDir.path)
            val existingIndex = indexByKey[key]
            if (existingIndex != null) {
                merged[existingIndex] = liveDir
            } else {
                indexByKey[key] = merged.size
                merged.add(liveDir)
            }
        }

        return merged
    }

    fun findUnpersistedDirectories(
        displayedDirs: List<Directory>,
        cachedDirs: List<Directory>,
        keyOf: (String) -> String = { it.trimEnd('/').lowercase() }
    ): ArrayList<Directory> {
        val cachedKeys = HashSet<String>(cachedDirs.size)
        for (dir in cachedDirs) {
            cachedKeys.add(keyOf(dir.path))
        }

        val missing = ArrayList<Directory>()
        val seenKeys = HashSet<String>()
        for (dir in displayedDirs) {
            val key = keyOf(dir.path)
            if (key !in cachedKeys && seenKeys.add(key)) {
                missing.add(dir)
            }
        }
        return missing
    }

    fun resolveVirtualAlbumCopyPhysicalSource(
        itemParentPaths: List<String>,
        destinationPath: String,
        fallbackSource: String
    ): String {
        val cleanDest = destinationPath.trimEnd('/')
        val differentParent = itemParentPaths.firstOrNull { parent ->
            parent.isNotEmpty() && !parent.trimEnd('/').equals(cleanDest, ignoreCase = true)
        }
        return differentParent ?: itemParentPaths.firstOrNull { it.isNotEmpty() } ?: fallbackSource
    }
}
