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
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.FOLDER_META_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_HEADER_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_STR_PREFIX
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
 * Extracted from [CryptoManager]. Feature group: CryptoManagerExportSync.
 */

fun CryptoManager.resolveExportVaultTargetDir(targetDir: File): File {
    if (targetDir.name.equals(SECRETS_DIR_NAME, ignoreCase = true)) {
        return targetDir
    }
    // Reuse an existing Secrets container inside the picked folder instead of creating a second one.
    return listOf(SECRETS_DIR_NAME)
        .map { File(targetDir, it) }
        .firstOrNull { it.isDirectory }
        ?: File(targetDir, SECRETS_DIR_NAME)
}


fun CryptoManager.exportEncryptedVault(
    context: Context,
    targetDir: File,
    onlyVaultHeader: Boolean,
    onProgress: ((processed: Int, total: Int) -> Unit)? = null
): VaultExportResult? {
    val vaultRoot = findVaultRoot(context) ?: return null
    val exportDir = resolveExportVaultTargetDir(targetDir)

    val vaultCanonical = runCatching { vaultRoot.canonicalPath }.getOrDefault(vaultRoot.absolutePath)
    val exportCanonical = runCatching { exportDir.canonicalPath }.getOrDefault(exportDir.absolutePath)
    if (exportCanonical == vaultCanonical || exportCanonical.startsWith("$vaultCanonical${File.separator}")) {
        return null
    }

    if (!exportDir.exists() && !exportDir.mkdirs()) {
        return null
    }
    if (!exportDir.isDirectory) {
        return null
    }

    val nomediaFile = File(exportDir, ".nomedia")
    if (!nomediaFile.exists()) {
        runCatching { nomediaFile.createNewFile() }
    }

    if (onlyVaultHeader) {
        val sourceVaultFile = File(vaultRoot, ".vault")
        if (!sourceVaultFile.isFile) {
            return null
        }
        onProgress?.invoke(0, 1)
        val copied = runCatching {
            sourceVaultFile.copyTo(File(exportDir, ".vault"), overwrite = true)
            true
        }.getOrDefault(false)
        onProgress?.invoke(1, 1)
        return if (copied) {
            VaultExportResult(exportedFilesCount = 1, failedFilesCount = 0, exportDirectory = exportDir)
        } else {
            VaultExportResult(exportedFilesCount = 0, failedFilesCount = 1, exportDirectory = exportDir)
        }
    }

    val allFiles = vaultRoot.walkTopDown().filter { it.isFile }.toList()
    val total = allFiles.size
    onProgress?.invoke(0, total)
    var exportedCount = 0
    var failedCount = 0

    allFiles.forEachIndexed { index, srcFile ->
        val relPath = srcFile.relativeTo(vaultRoot).path
        val destFile = File(exportDir, relPath)
        val copied = runCatching {
            destFile.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            srcFile.copyTo(destFile, overwrite = true)
            true
        }.getOrDefault(false)

        if (copied) {
            exportedCount++
        } else {
            failedCount++
        }
        onProgress?.invoke(index + 1, total)
    }

    if (!nomediaFile.exists()) {
        runCatching { nomediaFile.createNewFile() }
    }

    return VaultExportResult(
        exportedFilesCount = exportedCount,
        failedFilesCount = failedCount,
        exportDirectory = exportDir
    )
}


fun CryptoManager.syncVaultFromExternalDirectory(
    context: Context,
    sourceDir: File,
    requirePendingManifest: Boolean = true,
    onProgress: ((processed: Int, total: Int) -> Unit)? = null
): VaultSyncResult? {
    if (unlockedMasterKey == null) return null
    val vaultRoot = findVaultRoot(context) ?: return null
    val effectiveSource = resolveSyncSourceRoot(sourceDir)
    if (!effectiveSource.exists() || !effectiveSource.isDirectory) return null

    val sourceCanonical = runCatching { effectiveSource.canonicalPath }.getOrDefault(effectiveSource.absolutePath)
    val vaultCanonical = runCatching { vaultRoot.canonicalPath }.getOrDefault(vaultRoot.absolutePath)
    if (sourceCanonical == vaultCanonical) return null

    val pendingItems = ArrayList<SyncPendingItem>()
    val pendingFile = File(effectiveSource, PENDING_SYNC_FILE_NAME)
    val manifestEntryByBinPath = HashMap<String, String>()

    if (requirePendingManifest) {
        if (!pendingFile.isFile) {
            return VaultSyncResult(0, 0, 0, 0, 0)
        }
        val effectiveCanonicalDir = runCatching { effectiveSource.canonicalFile }.getOrNull() ?: effectiveSource
        val lines = runCatching { pendingFile.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
        for (rawLine in lines) {
            val rel = rawLine.trim().replace('\\', '/')
            if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) continue
            val bin = File(effectiveSource, rel)
            val binCanonical = runCatching { bin.canonicalFile }.getOrNull() ?: continue
            if (!binCanonical.path.startsWith(effectiveCanonicalDir.path + File.separator) &&
                binCanonical.path != effectiveCanonicalDir.path
            ) {
                continue
            }
            if (!bin.exists() || !bin.isFile || !isEncryptedBinFile(bin)) continue

            val parentDir = bin.parentFile ?: continue
            val parentCanonical = runCatching { parentDir.canonicalPath }.getOrDefault(parentDir.absolutePath)
            val isRootLevel = parentCanonical == sourceCanonical
            if (isRootLevel) {
                pendingItems.add(SyncPendingItem(bin, null, isDefaultTarget = true))
            } else {
                if (parentDir.name == VAULT_RECYCLE_BIN_NAME || isVaultRecycleBin(parentDir)) continue
                val raw = getDecryptedRawFolderName(parentDir)?.takeIf { it.isNotBlank() } ?: parentDir.name
                if (raw == VAULT_RECYCLE_BIN_NAME) continue
                val isDefaultTarget = isDefaultAlbumRawName(raw)
                val albumName = if (isDefaultTarget) null else raw.trim()
                pendingItems.add(SyncPendingItem(bin, albumName, isDefaultTarget))
            }
            manifestEntryByBinPath[bin.absolutePath] = rel
        }
        if (pendingItems.isEmpty()) {
            runCatching { pendingFile.delete() }
            return VaultSyncResult(0, 0, 0, 0, 0)
        }
    } else if (hasFolderMetaFile(effectiveSource)) {
        val raw = getDecryptedRawFolderName(effectiveSource)
        if (raw != VAULT_RECYCLE_BIN_NAME) {
            val bins = collectSubdirBinFiles(effectiveSource)
            val isDefaultTarget = isDefaultAlbumRawName(raw)
            val albumName = if (isDefaultTarget) {
                null
            } else {
                (raw?.takeIf { it.isNotBlank() } ?: effectiveSource.name).trim()
            }
            bins.forEach { bin ->
                pendingItems.add(SyncPendingItem(bin, albumName, isDefaultTarget))
            }
        }
    } else {
        val children = effectiveSource.listFiles() ?: emptyArray()
        children.filter { isEncryptedBinFile(it) }.forEach { bin ->
            pendingItems.add(SyncPendingItem(bin, null, isDefaultTarget = true))
        }

        children.filter { it.isDirectory }.forEach { sub ->
            if (sub.name.equals("JSON", ignoreCase = true)) return@forEach
            if (sub.name == VAULT_RECYCLE_BIN_NAME || isVaultRecycleBin(sub)) return@forEach
            if (sub.name.startsWith(".") && !hasFolderMetaFile(sub)) return@forEach

            val bins = collectSubdirBinFiles(sub)
            if (bins.isEmpty()) return@forEach

            val raw = getDecryptedRawFolderName(sub)?.takeIf { it.isNotBlank() } ?: sub.name
            if (raw == VAULT_RECYCLE_BIN_NAME) return@forEach

            val isDefaultTarget = isDefaultAlbumRawName(raw)
            val albumName = if (isDefaultTarget) null else raw.trim()
            bins.forEach { bin ->
                pendingItems.add(SyncPendingItem(bin, albumName, isDefaultTarget))
            }
        }
    }

    val total = pendingItems.size
    var copiedCount = 0
    var renamedCount = 0
    var skippedDuplicateCount = 0
    var failedCount = 0
    val failedManifestEntries = LinkedHashSet<String>()
    val touchedTargetAlbums = HashSet<String>()

    fun recordFailure(sourceBin: File) {
        failedCount++
        manifestEntryByBinPath[sourceBin.absolutePath]?.let { failedManifestEntries.add(it) }
    }

    runCryptoOperation {
        val resolvedAlbumMap = HashMap<String, File>()
        val existingAlbumLookedUp = HashSet<String>()
        val albumIndexMap = HashMap<File, TargetEncryptedIndex>()
        val albumBinNameIndex = HashMap<File, HashMap<String, File>>()

        var processed = 0
        for (item in pendingItems) {
            val sourceBin = item.sourceBin
            val albumKey = if (item.isDefaultTarget) {
                "__DEFAULT__"
            } else {
                "ALBUM:${item.sourceAlbumName?.trim()?.lowercase(Locale.ROOT) ?: ""}"
            }

            var existingTarget = resolvedAlbumMap[albumKey]
            if (existingTarget == null && existingAlbumLookedUp.add(albumKey)) {
                existingTarget = findExistingDestinationAlbum(vaultRoot, item)
                if (existingTarget != null) {
                    resolvedAlbumMap[albumKey] = existingTarget
                }
            }

            // Kanonik format: başlık okuma + Tink akış karşılaştırması ile yinelenenleri atla.
            val sourceMeta = readMetadata(sourceBin, includeThumbnail = false, cacheResult = false)
            if (sourceMeta == null) {
                metadataCache.remove(sourceBin.absolutePath)
                recordFailure(sourceBin)
                processed++
                onProgress?.invoke(processed, total)
                continue
            }

            var integrityVerified = false

            if (existingTarget != null) {
                val index = albumIndexMap.getOrPut(existingTarget) {
                    TargetEncryptedIndex(existingTarget)
                }
                val collisionCandidates = index.findCandidates(sourceMeta.originalName, includeCollisionVariants = true)
                val matchingCandidate = collisionCandidates.firstOrNull { (existingFile, existingMeta) ->
                    existingMeta.originalSize == sourceMeta.originalSize &&
                        existingMeta.mediaType == sourceMeta.mediaType &&
                        areEncryptedTimestampsEquivalent(sourceMeta, existingMeta) &&
                        areEncryptedFilesIdenticalContent(sourceBin, sourceMeta, existingFile, existingMeta)
                }
                if (matchingCandidate != null) {
                    skippedDuplicateCount++
                    processed++
                    onProgress?.invoke(processed, total)
                    continue
                }
            }

            // Hedefe kopyala ve Tink AEAD ile bütünlüğü doğrula.
            val stagingDir = existingTarget ?: vaultRoot
            val tempFile = File(stagingDir, ".sync_tmp_${randomHex(8)}$ENCRYPTED_BIN_EXT")
            val copyVerified = try {
                sourceBin.copyTo(tempFile, overwrite = true)
                verifyFileIntegrity(tempFile)
            } catch (_: Exception) {
                false
            }
            if (!copyVerified || !tempFile.exists()) {
                tempFile.delete()
                metadataCache.remove(sourceBin.absolutePath)
                recordFailure(sourceBin)
                processed++
                onProgress?.invoke(processed, total)
                continue
            }
            integrityVerified = true

            val targetAlbum = existingTarget ?: run {
                val created = if (item.isDefaultTarget || item.sourceAlbumName.isNullOrBlank()) {
                    getOrCreateDefaultHiddenAlbum(context)
                } else {
                    createHiddenPhysicalAlbum(context, item.sourceAlbumName)
                }
                if (created != null) {
                    resolvedAlbumMap[albumKey] = created
                }
                created
            }

            if (targetAlbum == null) {
                tempFile.delete()
                recordFailure(sourceBin)
                processed++
                onProgress?.invoke(processed, total)
                continue
            }

            val index = albumIndexMap.getOrPut(targetAlbum) {
                TargetEncryptedIndex(targetAlbum)
            }

            var destBin = File(targetAlbum, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
            while (destBin.exists()) {
                destBin = File(targetAlbum, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
            }

            val movedToDest = try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    destBin.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                true
            } catch (_: Exception) {
                tempFile.renameTo(destBin)
            }

            if (!movedToDest || !destBin.exists()) {
                tempFile.delete()
                destBin.delete()
                recordFailure(sourceBin)
                processed++
                onProgress?.invoke(processed, total)
                continue
            }

            val exactNameTaken = index.findExactEntries(sourceMeta.originalName).isNotEmpty()
            if (exactNameTaken) {
                val renamedName = index.getAlternativeOriginalName(sourceMeta.originalName)
                if (renameEncryptedFile(destBin, renamedName, skipIntegrityCheck = integrityVerified, syncParentDir = false)) {
                    val updatedMeta = readMetadata(destBin, includeThumbnail = false, cacheResult = false)
                        ?: sourceMeta.copy(originalName = renamedName)
                    index.addEntry(destBin, updatedMeta)
                    albumBinNameIndex[targetAlbum]?.put(destBin.name.lowercase(Locale.ROOT), destBin)
                    touchedTargetAlbums.add(targetAlbum.absolutePath)
                    renamedCount++
                } else {
                    metadataCache.remove(destBin.absolutePath)
                    destBin.delete()
                    recordFailure(sourceBin)
                }
            } else {
                destBin.setLastModified(sourceBin.lastModified())
                index.addEntry(destBin, sourceMeta)
                albumBinNameIndex[targetAlbum]?.put(destBin.name.lowercase(Locale.ROOT), destBin)
                touchedTargetAlbums.add(targetAlbum.absolutePath)
                copiedCount++
            }

            processed++
            onProgress?.invoke(processed, total)
        }
    }

    if (requirePendingManifest && pendingFile.isFile) {
        runCatching {
            if (failedManifestEntries.isEmpty()) {
                pendingFile.delete()
            } else {
                pendingFile.writeText(failedManifestEntries.joinToString("\n") + "\n", Charsets.UTF_8)
            }
        }
    }

    if (touchedTargetAlbums.isNotEmpty()) {
        touchedTargetAlbums.forEach { albumPath ->
            syncDir(File(albumPath))
        }
    }
    if (copiedCount + renamedCount > 0) {
        invalidateFolderCache()
        runCatching { clearAppImageCaches(context) }
    }

    return VaultSyncResult(
        copiedCount = copiedCount,
        renamedCount = renamedCount,
        skippedDuplicateCount = skippedDuplicateCount,
        failedCount = failedCount,
        syncedAlbumsCount = touchedTargetAlbums.size
    )
}


internal fun CryptoManager.resolveExistingEncryptTargetAlbum(vaultRoot: File, rawFolderName: String): File? {
    return if (rawFolderName.equals(VAULT_DEFAULT_ALBUM_NAME, ignoreCase = true)) {
        findExistingDefaultHiddenAlbum(vaultRoot)
    } else {
        matchExistingDestinationAlbum(vaultRoot, rawFolderName)
    }
}


internal fun CryptoManager.resolveDecryptTargetFolder(
    context: Context,
    folder: File,
    targetFolderOverride: File?
): File {
    if (targetFolderOverride != null) return targetFolderOverride
    val originalFolderName = getDecryptedFolderName(folder) ?: folder.name
    val picturesDir = File(context.internalStoragePath, Environment.DIRECTORY_PICTURES)
    if (!picturesDir.exists()) {
        picturesDir.mkdirs()
    }
    var candidate = File(picturesDir, originalFolderName)
    var idx = 1
    while (candidate.exists() && !candidate.isDirectory) {
        candidate = File(picturesDir, "$originalFolderName($idx)")
        idx++
    }
    return candidate
}


internal fun CryptoManager.getSortedEncryptableMediaFiles(folder: File): List<File> {
    return (folder.listFiles()?.filter { isEncryptableMediaFile(it) } ?: emptyList())
        .sortedWith(compareBy<File>({ stripCollisionIndex(it.name) == it.name }, { it.name }))
}


internal fun CryptoManager.getSortedEncryptedFilesForDecrypt(folder: File): List<File> {
    val rawFiles = folder.listFiles()?.filter { it.isFile && isEncryptedFile(it.name) } ?: emptyList()
    val origNameByPath = HashMap<String, String>(rawFiles.size)
    for (file in rawFiles) {
        origNameByPath[file.absolutePath] = readMetadata(file, includeThumbnail = false, cacheResult = false)?.originalName ?: file.name
    }
    return rawFiles.sortedWith(
        compareBy<File>(
            {
                val orig = origNameByPath[it.absolutePath] ?: it.name
                stripCollisionIndex(orig) == orig
            },
            { origNameByPath[it.absolutePath] ?: it.name },
            { it.name }
        )
    )
}


fun CryptoManager.findMergeTargetForEncryptFolder(context: Context, folderPath: String): Pair<File, List<File>>? {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) return null
    val vaultRoot = findVaultRoot(context) ?: return null
    val rawFolderName = getDecryptedFolderName(folder) ?: folder.name
    val existingAlbum = resolveExistingEncryptTargetAlbum(vaultRoot, rawFolderName) ?: return null
    return existingAlbum to getSortedEncryptableMediaFiles(folder)
}


fun CryptoManager.findMergeTargetForDecryptFolder(
    context: Context,
    folderPath: String,
    targetFolderOverride: File? = null
): Pair<File, List<File>>? {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) return null
    val targetFolder = resolveDecryptTargetFolder(context, folder, targetFolderOverride)
    if (!targetFolder.exists() || !targetFolder.isDirectory) return null
    return targetFolder to getSortedEncryptedFilesForDecrypt(folder)
}


internal fun CryptoManager.resolveUniqueEncryptedAlbumName(vaultRoot: File, baseName: String): String {
    var idx = 1
    var candidate = "$baseName($idx)"
    while (matchExistingDestinationAlbum(vaultRoot, candidate) != null) {
        idx++
        candidate = "$baseName($idx)"
    }
    return candidate
}


internal fun CryptoManager.resolveUniquePlainFolder(parentDir: File, baseName: String): File {
    var idx = 1
    var candidate = File(parentDir, "$baseName($idx)")
    while (candidate.exists()) {
        idx++
        candidate = File(parentDir, "$baseName($idx)")
    }
    return candidate
}


fun CryptoManager.previewEncryptAlbumConflict(context: Context, folderPath: String): AlbumConflictPreview? {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) return null
    val vaultRoot = findVaultRoot(context) ?: return null
    val rawFolderName = getDecryptedFolderName(folder) ?: folder.name
    val existingAlbum = if (rawFolderName.equals(VAULT_DEFAULT_ALBUM_NAME, ignoreCase = true)) {
        findExistingDefaultHiddenAlbum(vaultRoot)
    } else {
        matchExistingDestinationAlbum(vaultRoot, rawFolderName)
    } ?: return null
    val existingName = getDecryptedFolderName(existingAlbum, context) ?: rawFolderName
    val renamedPreview = resolveUniqueEncryptedAlbumName(vaultRoot, rawFolderName)
    return AlbumConflictPreview(existingName = existingName, renamedPreview = renamedPreview)
}


fun CryptoManager.previewDecryptAlbumConflict(
    context: Context,
    folderPath: String,
    targetFolderOverride: File? = null
): AlbumConflictPreview? {
    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) return null
    val targetFolder = if (targetFolderOverride != null) {
        targetFolderOverride
    } else {
        val originalFolderName = getDecryptedFolderName(folder) ?: folder.name
        val picturesDir = File(context.internalStoragePath, Environment.DIRECTORY_PICTURES)
        File(picturesDir, originalFolderName)
    }
    if (!targetFolder.exists() || !targetFolder.isDirectory) return null
    val parent = targetFolder.parentFile ?: return null
    val renamed = resolveUniquePlainFolder(parent, targetFolder.name)
    return AlbumConflictPreview(existingName = targetFolder.name, renamedPreview = renamed.name)
}
