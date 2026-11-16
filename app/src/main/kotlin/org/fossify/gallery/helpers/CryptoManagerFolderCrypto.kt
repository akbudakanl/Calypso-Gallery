package org.fossify.gallery.helpers

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import java.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.dateTakensDB
import org.fossify.gallery.extensions.deleteDBPaths
import org.fossify.gallery.extensions.deleteVirtualAlbumItemFromAllAlbums
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.isThisOrParentIncluded
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.updateVirtualAlbumItemPath
import org.fossify.gallery.extensions.virtualAlbumsDB
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.fossify.gallery.helpers.vault.VaultContainer.CTR_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import org.fossify.gallery.helpers.vault.VaultContainer.MAC_TAG_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.MAGIC_V1
import org.fossify.gallery.helpers.vault.VaultContainer.MAGIC_V2
import org.fossify.gallery.helpers.vault.VaultContainer.META_MAGIC_SYM
import org.fossify.gallery.helpers.vault.VaultContainer.META_MAGIC_V2
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_HEADER_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_STR_PREFIX
import org.fossify.gallery.helpers.vault.VaultContainer.VERSION_SYMMETRIC
import org.fossify.gallery.helpers.vault.VaultContainer.VERSION_SYMMETRIC_AUTH
import org.fossify.gallery.helpers.vault.VaultContainer.VERSION_V5_AUTH
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.DOMAIN_CONTENT_ENC_KEY
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.DOMAIN_CONTENT_MAC_KEY
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.DOMAIN_HEADER_KEY
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.deriveSubkey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import android.media.MediaScannerConnection
import android.os.Build
import androidx.biometric.BiometricManager
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.models.DateTaken
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.fossify.gallery.helpers.CryptoManager.TargetPlainIndex
import org.fossify.gallery.helpers.CryptoManager.TargetEncryptedIndex
import org.fossify.gallery.helpers.CryptoManager.TransferConflictStatus
import org.fossify.gallery.helpers.CryptoManager.TransferConflictCheck
import org.fossify.gallery.helpers.CryptoManager.SmartConflictDifference
import org.fossify.gallery.helpers.CryptoManager.SmartConflictComparison
import org.fossify.gallery.helpers.CryptoManager.VaultExportResult
import org.fossify.gallery.helpers.CryptoManager.AlbumConflictResolution
import org.fossify.gallery.helpers.CryptoManager.AlbumCryptoOptions
import org.fossify.gallery.helpers.CryptoManager.AlbumConflictPreview
import org.fossify.gallery.helpers.CryptoManager.StreamCompareMacResult
import org.fossify.gallery.helpers.CryptoManager.SyncPendingItem

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerFolderCrypto.
 */

fun CryptoManager.encryptFolder(
    context: Context,
    folderPath: String,
    options: AlbumCryptoOptions = AlbumCryptoOptions(),
    onProgress: ((current: Int, total: Int) -> Unit)? = null
): String? {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) return folderPath

    if (unlockedMasterKey == null) {
        return null
    }

    val rawFolderName = getDecryptedFolderName(folder) ?: folder.name
    val vaultRoot = findVaultRoot(context) ?: return null
    val vaultNoMedia = File(vaultRoot, NOMEDIA)
    if (!vaultNoMedia.exists()) {
        try { vaultNoMedia.createNewFile() } catch (_: Exception) {}
    }

    val isExplicitlyIncluded = folder.absolutePath.isThisOrParentIncluded(context.config.includedFolders)
    val isHiddenParent = !isExplicitlyIncluded && (
        folder.absolutePath.contains("/.") ||
            folder.absolutePath.doesThisOrParentHaveNoMedia(HashMap(), null)
        )
    val shouldAddNoMedia = ExcludedMenuPolicy.shouldAddNoMediaOnEncrypt(
        folderName = folder.name,
        decryptedName = rawFolderName,
        hasNoMediaFile = File(folder, NOMEDIA).exists(),
        isPathOrParentHidden = isHiddenParent
    )

    val existingAlbum = resolveExistingEncryptTargetAlbum(vaultRoot, rawFolderName)
    val shouldMergeIntoExisting = existingAlbum != null && options.resolution == AlbumConflictResolution.MERGE
    val targetAlbumExistedBefore = shouldMergeIntoExisting
    val originalFolderName = if (shouldMergeIntoExisting) {
        existingAlbum?.let { getDecryptedFolderName(it, context) } ?: rawFolderName
    } else if (existingAlbum != null) {
        resolveUniqueEncryptedAlbumName(vaultRoot, rawFolderName)
    } else {
        rawFolderName
    }

    // Only formats supported by the gallery are encrypted; other files (pdf, md, ...) are left untouched.
    // Process pre-numbered (n) variants before base names so colliding base files cannot steal "(1)" slots.
    val filesToEncrypt = getSortedEncryptableMediaFiles(folder)

    val subfolder = if (shouldMergeIntoExisting) {
        existingAlbum!!
    } else {
        var candidate = File(vaultRoot, randomHex(6))
        while (candidate.exists()) {
            candidate = File(vaultRoot, randomHex(6))
        }
        candidate
    }
    if (!subfolder.exists() && !subfolder.mkdirs()) return null

    if (shouldAddNoMedia) {
        val noMedia = File(subfolder, NOMEDIA)
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }
    }

    if (!File(subfolder, VAULT_SUBFOLDER_META_FILE).exists() &&
        !File(subfolder, ENCRYPTED_FOLDER_META_FILE).exists()
    ) {
        writeEncryptedFolderMeta(context, subfolder, originalFolderName)
    }

    val total = filesToEncrypt.size
    onProgress?.invoke(0, total)

    val preExistingBinPaths = subfolder.listFiles()?.mapTo(HashSet()) { it.absolutePath } ?: emptySet()
    val subfolderIndex = if (unlockedMasterKey != null) TargetEncryptedIndex(subfolder, preExistingBinPaths) else null
    val newlyCreatedBins = ArrayList<File>()
    val encryptedPairs = ArrayList<Triple<File, File, Boolean>>()
    val skippedConflictFiles = ArrayList<File>()
    var failed = false

    for ((index, file) in filesToEncrypt.withIndex()) {
        val fileResolution = options.conflictResolutions[file.absolutePath] ?: CONFLICT_KEEP_BOTH
        if (fileResolution == CONFLICT_SKIP) {
            skippedConflictFiles.add(file)
            options.deferredSkippedSourceCollector?.add(file)
            onProgress?.invoke(((index + 1) * 7) / 10, total)
            continue
        }
        val enc = encryptFileToDirectory(
            context = context,
            sourceFile = file,
            targetDir = subfolder,
            wipeSource = false,
            clearCachesNow = false,
            conflictResolution = fileResolution,
            targetIndex = subfolderIndex,
            skipDuplicateCheck = options.skipDuplicateCheck || (file.absolutePath in options.precheckedNonIdenticalPaths)
        )
        if (enc != null && enc.exists()) {
            val isOverwritten = (fileResolution == CONFLICT_OVERWRITE)
            encryptedPairs.add(Triple(file, enc, isOverwritten))
            if (enc.absolutePath !in preExistingBinPaths || isOverwritten) {
                newlyCreatedBins.add(enc)
            }
        } else {
            failed = true
            break
        }
        onProgress?.invoke(((index + 1) * 7) / 10, total)
    }

    // ROLLBACK IF ENCRYPTION FAILED ON ANY FILE
    val expectedProcessed = filesToEncrypt.size - skippedConflictFiles.size
    if (failed || encryptedPairs.size != expectedProcessed) {
        if (targetAlbumExistedBefore) {
            newlyCreatedBins.forEach { createdBin ->
                metadataCache.remove(createdBin.absolutePath)?.fileKey?.fill(0)
                runCatching { createdBin.delete() }
            }
        } else {
            subfolder.deleteRecursively()
        }
        return null
    }

    // ALL FILES ENCRYPTED SUCCESSFULLY! WIPE AND DELETE ORIGINAL MEDIA FILES:
    val wipedPaths = ArrayList<String>(encryptedPairs.size)
    val wipedFileNames = HashSet<String>(encryptedPairs.size)
    val touchedAlbumIds = LinkedHashSet<Long>()
    var favoritesChanged = false
    val encBaseProgress = (total * 7) / 10
    val wipeWeight = total - encBaseProgress
    for ((index, entry) in encryptedPairs.withIndex()) {
        val (sourceFile, encFile, isOverwritten) = entry
        val isIdenticalPreExisting = !isOverwritten && (encFile.absolutePath in preExistingBinPaths)
        if (!isIdenticalPreExisting || options.deleteIdenticalSource) {
            val sourcePath = sourceFile.absolutePath
            val plainIdentity = runCatching { MediaIdentity.ofWithFolder(context, sourceFile) }.getOrNull()
            runCatching {
                val res = context.markReferencesOnEncrypt(
                    sourcePath = sourcePath,
                    encPath = encFile.absolutePath,
                    plaintextIdentity = plainIdentity,
                    markPlaintextDormant = true,
                    saveBackups = false
                )
                touchedAlbumIds.addAll(res.affectedAlbumIds)
                if (res.favoritesChanged) favoritesChanged = true
            }
            if (secureWipeAndDeleteFile(sourceFile, context, syncParentDir = false, purgeTraces = false)) {
                wipedPaths.add(sourcePath)
                wipedFileNames.add(sourceFile.name)
            }
        } else {
            options.deferredSkippedSourceCollector?.add(sourceFile)
            options.deferredIdenticalTargetMap?.put(sourceFile.absolutePath, encFile)
        }
        val wipeProgress = if (encryptedPairs.isNotEmpty()) {
            encBaseProgress + ((index + 1) * wipeWeight) / encryptedPairs.size
        } else {
            total
        }
        onProgress?.invoke(wipeProgress, total)
    }
    runCatching { syncDir(folder) }

    for (albumId in touchedAlbumIds) {
        runCatching { VirtualAlbumSyncManager.saveAlbumToFile(context, albumId) }
    }
    if (favoritesChanged) {
        runCatching { ReferenceBackupManager.saveFavorites(context) }
    }

    // Batch-purge MediaStore, Room DB, and thumbnail traces once for all wiped files
    if (wipedPaths.isNotEmpty()) {
        val cleanFolderPath = folder.absolutePath.trimEnd('/')
        runCatching {
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("$cleanFolderPath/%")
            )
            MediaScannerConnection.scanFile(context, wipedPaths.toTypedArray(), null, null)
        }

        runCatching {
            context.mediaDB.deleteMediaFromPath(cleanFolderPath)
            GalleryDatabase.getInstance(context.applicationContext).DateTakensDao().deleteDateTakensFromPath(cleanFolderPath)
        }

        runCatching {
            val dcimThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), ".thumbnails")
            val picThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ".thumbnails")
            val parentThumbnails = File(folder, ".thumbnails")
            listOf(dcimThumbnails, picThumbnails, parentThumbnails).forEach { tDir ->
                if (tDir.exists() && tDir.isDirectory) {
                    tDir.listFiles()?.forEach { thumbFile ->
                        if (wipedFileNames.any { name -> thumbFile.name.contains(name) }) {
                            secureWipeAndDeleteFile(thumbFile, null, syncParentDir = false, purgeTraces = false)
                        }
                    }
                }
            }
        }
    }

    // Preserve any existing .bin files that were already encrypted in this folder
    val existingEncryptedFiles = folder.listFiles()?.filter { file ->
        file.isFile && isEncryptedFile(file.name)
    } ?: emptyList()
    for (encFile in existingEncryptedFiles) {
        if (unlockedMasterKey != null) {
            copyOrMoveEncryptedFileToDirectory(context, encFile, subfolder, isMove = true, targetIndex = subfolderIndex)
        } else {
            var dest = File(subfolder, encFile.name)
            while (dest.exists()) {
                dest = File(subfolder, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
            }
            val oldEncPath = encFile.absolutePath
            if (encFile.renameTo(dest)) {
                runCatching { context.updateVirtualAlbumItemPath(oldEncPath, dest.absolutePath) }
                metadataCache.remove(oldEncPath)?.let { meta ->
                    metadataCache[dest.absolutePath] = meta
                }
            }
        }
    }

    // Move subfolders directly to internal storage (if any)
    val internalStorageRoot = File(context.internalStoragePath)
    val movedSubfolderPaths = ArrayList<String>()

    val childFiles = folder.listFiles() ?: emptyArray()
    for (child in childFiles) {
        if (child.isDirectory) {
            val cleanName = child.name.trimStart('.')
            var targetSubfolder = File(internalStorageRoot, if (cleanName.isEmpty()) "Subfolder" else cleanName)
            var collisionIndex = 1
            while (targetSubfolder.exists()) {
                targetSubfolder = File(internalStorageRoot, "${if (cleanName.isEmpty()) "Subfolder" else cleanName}_$collisionIndex")
                collisionIndex++
            }
            val moved = if (child.renameTo(targetSubfolder)) {
                true
            } else {
                try {
                    child.copyRecursively(targetSubfolder, overwrite = false) && child.deleteRecursively()
                } catch (_: Exception) {
                    false
                }
            }
            if (moved) {
                val noMediaInSub = File(targetSubfolder, NOMEDIA)
                if (noMediaInSub.exists()) {
                    try { noMediaInSub.delete() } catch (_: Exception) {}
                    try {
                        context.contentResolver.delete(
                            MediaStore.Files.getContentUri("external"),
                            "${MediaStore.Files.FileColumns.DATA} = ?",
                            arrayOf(noMediaInSub.absolutePath)
                        )
                    } catch (_: Exception) {}
                }
                runCatching { context.directoryDB.deleteDirPath(child.absolutePath) }
                movedSubfolderPaths.add(targetSubfolder.absolutePath)
            }
        }
    }

    if (movedSubfolderPaths.isNotEmpty()) {
        val pathsToRescan = ArrayList<String>()
        for (path in movedSubfolderPaths) {
            pathsToRescan.add(path)
            File(path).walkTopDown().filter { it.isFile }.forEach {
                pathsToRescan.add(it.absolutePath)
            }
        }
        runCatching { context.rescanPaths(pathsToRescan) }
    }

    // Delete original plaintext folder - but only if nothing besides leftovers of ours remains in it.
    // Files the gallery can't display (pdf, md, ...) were intentionally NOT encrypted and must survive.
    runCatching {
        val leftoverUnsupportedFiles = folder.listFiles()?.any { child ->
            child.name != NOMEDIA && (child.isFile || child.isDirectory)
        } == true

        if (!leftoverUnsupportedFiles) {
            val oldNoMedia = File(folder, NOMEDIA)
            if (!oldNoMedia.exists()) {
                try { oldNoMedia.createNewFile() } catch (_: Exception) {}
            }

            runCatching {
                val contentResolver = context.contentResolver
                val filesUri = MediaStore.Files.getContentUri("external")
                contentResolver.delete(
                    filesUri,
                    "${MediaStore.MediaColumns.DATA} LIKE ?",
                    arrayOf("${folder.absolutePath}%")
                )
            }

            folder.listFiles()?.forEach { child ->
                try { child.delete() } catch (_: Exception) {}
            }

            var deletedDir = folder.delete()
            if (!deletedDir) {
                deletedDir = folder.deleteRecursively()
            }
            if (!deletedDir) {
                deletedDir = runCatching { context.tryFastDocumentDelete(folder.absolutePath, true) }.getOrDefault(false)
            }
        }
    }

    runCatching { context.config.removeEncryptedFolder(folder.absolutePath) }
    runCatching { context.config.addEncryptedFolder(subfolder.absolutePath) }
    if (!folder.exists()) {
        runCatching { context.config.replaceExactExcludedFolder(folder.absolutePath, subfolder.absolutePath) }
    }
    runCatching { context.directoryDB.deleteDirPath(folder.absolutePath) }
    folderNameCache[subfolder.absolutePath] = originalFolderName
    encryptedFolderCache[folder.absolutePath] = false
    encryptedFolderCache[subfolder.absolutePath] = true
    runCatching { clearAppImageCaches(context) }

    return subfolder.absolutePath
}


/**
 * Decrypts an encrypted subfolder back to plaintext inside Pictures/<originalFolderName>,
 * or into [targetFolderOverride] when specified by the user.
 */
fun CryptoManager.decryptFolder(
    context: Context,
    folderPath: String,
    targetFolderOverride: File? = null,
    options: AlbumCryptoOptions = AlbumCryptoOptions(),
    onProgress: ((current: Int, total: Int) -> Unit)? = null
): String? {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) return folderPath
    if (!isVaultUnlocked) return folderPath

    val hadNoMedia = File(folder, NOMEDIA).exists()
    val initialTarget = resolveDecryptTargetFolder(context, folder, targetFolderOverride)
    val targetFolder = if (initialTarget.exists() && initialTarget.isDirectory && options.resolution == AlbumConflictResolution.RENAME) {
        val parent = initialTarget.parentFile ?: initialTarget
        resolveUniquePlainFolder(parent, initialTarget.name)
    } else {
        initialTarget
    }
    val targetExistedBefore = targetFolder.exists()
    if (!targetExistedBefore && !targetFolder.mkdirs()) return null

    // Process pre-numbered (n) variants before base names so colliding base files do not steal "(1)" names.
    val filesToDecrypt = getSortedEncryptedFilesForDecrypt(folder)

    val total = filesToDecrypt.size
    onProgress?.invoke(0, total)

    val preExistingPlainPaths = targetFolder.listFiles()?.mapTo(HashSet()) { it.absolutePath } ?: emptySet()
    val targetPlainIndex = TargetPlainIndex(targetFolder, preExistingPlainPaths)
    val newlyCreatedPlainFiles = ArrayList<File>()
    val decryptedPairs = ArrayList<Triple<File, File, Boolean>>()
    val skippedConflictFiles = ArrayList<File>()
    var failed = false

    for ((index, file) in filesToDecrypt.withIndex()) {
        val fileResolution = options.conflictResolutions[file.absolutePath] ?: CONFLICT_KEEP_BOTH
        if (fileResolution == CONFLICT_SKIP) {
            skippedConflictFiles.add(file)
            options.deferredSkippedSourceCollector?.add(file)
            onProgress?.invoke(index + 1, total)
            continue
        }
        val decrypted = decryptFileToDirectory(
            context = context,
            encryptedFile = file,
            targetDir = targetFolder,
            deleteEncryptedSource = false,
            conflictResolution = fileResolution,
            targetIndex = targetPlainIndex,
            skipDuplicateCheck = options.skipDuplicateCheck || (file.absolutePath in options.precheckedNonIdenticalPaths)
        )
        if (decrypted != null && decrypted.exists()) {
            val isOverwritten = (fileResolution == CONFLICT_OVERWRITE)
            decryptedPairs.add(Triple(file, decrypted, isOverwritten))
            if (decrypted.absolutePath !in preExistingPlainPaths) {
                newlyCreatedPlainFiles.add(decrypted)
            }
        } else {
            failed = true
            break
        }
        onProgress?.invoke(index + 1, total)
    }

    // ROLLBACK IF DECRYPTION FAILED
    val expectedProcessed = filesToDecrypt.size - skippedConflictFiles.size
    if (failed || decryptedPairs.size != expectedProcessed) {
        newlyCreatedPlainFiles.forEach { decryptedFile ->
            try { decryptedFile.delete() } catch (_: Exception) {}
        }
        if (!targetExistedBefore && targetFolder.listFiles().isNullOrEmpty()) {
            try { targetFolder.deleteRecursively() } catch (_: Exception) {}
        }
        return null
    }

    // ALL FILES DECRYPTED! Now delete encrypted files:
    val touchedAlbumIds = LinkedHashSet<Long>()
    var favoritesChanged = false
    for ((encFile, decryptedFile, isOverwritten) in decryptedPairs) {
        val isIdenticalPreExisting = !isOverwritten && (decryptedFile.absolutePath in preExistingPlainPaths)
        if (isIdenticalPreExisting && !options.deleteIdenticalSource) {
            options.deferredSkippedSourceCollector?.add(encFile)
            options.deferredIdenticalTargetMap?.put(encFile.absolutePath, decryptedFile)
            continue
        }
        val encPath = encFile.absolutePath
        runCatching {
            val res = context.markReferencesOnDecrypt(
                encPath = encPath,
                decryptedFile = decryptedFile,
                hideStealthReferences = true,
                saveBackups = false
            )
            touchedAlbumIds.addAll(res.affectedAlbumIds)
            if (res.favoritesChanged) favoritesChanged = true
        }
        metadataCache.remove(encPath)?.fileKey?.fill(0)
        runCatching { context.mediaDB.deleteMediumPath(encPath) }
        encFile.delete()
    }
    for (albumId in touchedAlbumIds) {
        runCatching { VirtualAlbumSyncManager.saveAlbumToFile(context, albumId) }
    }
    if (favoritesChanged) {
        runCatching { ReferenceBackupManager.saveFavorites(context) }
    }

    val hasRemainingEncryptedFiles = folder.listFiles()?.any { it.isFile && isEncryptedFile(it.name) } == true
    if (!hasRemainingEncryptedFiles) {
        runCatching {
            val contentResolver = context.contentResolver
            val filesUri = MediaStore.Files.getContentUri("external")
            contentResolver.delete(
                filesUri,
                "${MediaStore.MediaColumns.DATA} LIKE ?",
                arrayOf("${folder.absolutePath}%")
            )
        }
        runCatching {
            if (!folder.deleteRecursively()) {
                (context as? BaseSimpleActivity)?.deleteFileBg(
                    folder.toFileDirItem(context),
                    allowDeleteFolder = true,
                    isDeletingMultipleFiles = false
                )
            }
        }
        folderNameCache.remove(folder.absolutePath)

        runCatching { context.config.removeEncryptedFolder(folder.absolutePath) }
        runCatching { context.config.replaceExactExcludedFolder(folder.absolutePath, targetFolder.absolutePath) }
        runCatching { context.directoryDB.deleteDirPath(folder.absolutePath) }
        encryptedFolderCache[folder.absolutePath] = false
    }
    encryptedFolderCache[targetFolder.absolutePath] = false

    if (hadNoMedia) {
        val targetNoMedia = File(targetFolder, NOMEDIA)
        if (!targetNoMedia.exists()) {
            try {
                targetNoMedia.createNewFile()
            } catch (_: Exception) {}
        }
        if (targetNoMedia.exists()) {
            runCatching {
                val content = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.TITLE, NOMEDIA)
                    put(MediaStore.Files.FileColumns.DATA, targetNoMedia.absolutePath)
                    put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_NONE)
                }
                context.contentResolver.insert(MediaStore.Files.getContentUri("external"), content)
            }
        }
    }

    val isHiddenTarget = hadNoMedia || targetFolder.name.startsWith('.')
    if (!isHiddenTarget) {
        val restoredPaths = targetFolder.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
        if (restoredPaths.isNotEmpty()) {
            runCatching { context.rescanPaths(ArrayList(restoredPaths)) }
        }
    } else if (hadNoMedia) {
        val targetNoMedia = File(targetFolder, NOMEDIA)
        if (targetNoMedia.exists()) {
            runCatching { context.rescanPaths(arrayListOf(targetNoMedia.absolutePath)) }
        }
    }
    return targetFolder.absolutePath
}

