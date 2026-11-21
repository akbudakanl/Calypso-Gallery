package org.fossify.gallery.helpers

import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.isVirtualAlbumPath

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
}
