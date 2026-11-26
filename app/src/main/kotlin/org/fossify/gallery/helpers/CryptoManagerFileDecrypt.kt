package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_FORMAT_VERSION
import java.io.*
import java.security.SecureRandom
import org.fossify.gallery.models.DateTaken
import org.fossify.gallery.helpers.CryptoManager.TargetPlainIndex
import org.fossify.gallery.helpers.CryptoManager.TargetEncryptedIndex
import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.EncryptedSizeInput
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.operations.TransientOperationWaiter

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerFileDecrypt.
 */


/**
 * Extracted from [CryptoManagerFileCrypto]. File decryption, display names, metadata cache moves, and sync root helpers.
 */

fun CryptoManager.decryptFileToDirectory(
    context: Context,
    encryptedFile: File,
    targetDir: File,
    deleteEncryptedSource: Boolean = true,
    conflictResolution: Int = CONFLICT_KEEP_BOTH,
    targetIndex: TargetPlainIndex? = null,
    skipDuplicateCheck: Boolean = false,
    deleteSourceOnIdentical: Boolean = true,
    matchCollisionVariants: Boolean = true,
    operation: OperationContext = OperationContext.NONE,
    forcedDestName: String? = null
): File? {
    if (!encryptedFile.exists() || !isEncryptedFile(encryptedFile.name)) {
        OperationJournal.markFailed(operation, "encrypted source missing")
        return null
    }
    val meta = readMetadata(encryptedFile, includeThumbnail = false, cacheResult = false)
    if (meta == null) {
        OperationJournal.markFailed(operation, "could not read encrypted header")
        return null
    }

    if (meta.isMacAuthenticated && !verifyFileIntegrity(encryptedFile, meta)) {
        OperationJournal.markFailed(operation, "encrypted source integrity check failed")
        return null
    }

    if (!targetDir.exists()) {
        targetDir.mkdirs()
    }

    val index = targetIndex ?: TargetPlainIndex(targetDir)
    val encCanon = runCatching { encryptedFile.canonicalPath }.getOrDefault(encryptedFile.absolutePath)

    if (!skipDuplicateCheck) {
        val existingVariants = index.findCandidates(meta.originalName, matchCollisionVariants)
        for (existingPlain in existingVariants) {
            if (existingPlain.length() != meta.originalSize) continue
            val gateByDate = context.shouldGateDuplicatesByDate()
            val existingLastMod = existingPlain.lastModified()
            if (gateByDate && !areTimestampsEquivalent(existingLastMod, meta.lastModified)) continue
            if (gateByDate && meta.dateTaken > 0L) {
                val existingDateTaken = resolvePlaintextDateTaken(context, existingPlain, existingLastMod)
                if (!areMediaTimestampsEquivalent(existingLastMod, existingDateTaken, meta.lastModified, meta.dateTaken)) {
                    continue
                }
            }
            if (arePlainAndEncryptedIdentical(existingPlain, encryptedFile, meta, verifyIntegrity = false)) {
                applyDuplicateDateToPlainFile(context, existingPlain, meta.lastModified, meta.dateTaken)
                val plainCanon = runCatching { existingPlain.canonicalPath }.getOrDefault(existingPlain.absolutePath)
                val dupFingerprint = MediaIdentity.computeFingerprint(existingPlain)
                if (deleteEncryptedSource && deleteSourceOnIdentical && encCanon != plainCanon) {
                    val encPath = encryptedFile.absolutePath
                    runCatching {
                        context.markReferencesOnDecrypt(
                            encPath = encPath,
                            decryptedFile = existingPlain,
                            hideStealthReferences = true
                        )
                    }
                    metadataCache.remove(encPath)?.fileKey?.fill(0)
                    runCatching { context.mediaDB.deleteMediumPath(encPath) }
                    encryptedFile.delete()
                    OperationJournal.markVerified(operation, dupFingerprint, existingPlain.absolutePath)
                    OperationJournal.markSourceDeleted(operation)
                } else if (deleteSourceOnIdentical && encCanon != plainCanon) {
                    runCatching {
                        context.markReferencesOnDecrypt(
                            encPath = encryptedFile.absolutePath,
                            decryptedFile = existingPlain,
                            hideStealthReferences = false
                        )
                    }
                    if (!deleteEncryptedSource) {
                        OperationJournal.markVerified(operation, dupFingerprint, existingPlain.absolutePath)
                    } else {
                        OperationJournal.markSkipped(operation)
                    }
                } else if (!deleteEncryptedSource) {
                    OperationJournal.markVerified(operation, dupFingerprint, existingPlain.absolutePath)
                } else {
                    OperationJournal.markSkipped(operation)
                }
                if (deleteSourceOnIdentical) {
                    persistDecryptedDateTaken(context, existingPlain, meta.dateTaken, meta.lastModified)
                    runCatching {
                        context.rescanPath(existingPlain.absolutePath) {
                            updateMediaStoreTimestamps(context, existingPlain.absolutePath, meta.dateTaken, meta.lastModified)
                        }
                    }
                }
                return existingPlain
            }
        }
    }

    val preferredOutName = forcedDestName?.takeIf { it.isNotEmpty() } ?: meta.originalName
    var outFile = index.findExact(preferredOutName) ?: File(targetDir, preferredOutName)
    if (outFile.exists()) {
        when (conflictResolution) {
            CONFLICT_SKIP -> {
                OperationJournal.markSkipped(operation)
                return null
            }
            CONFLICT_OVERWRITE -> {
                index.removeFile(outFile)
            }
            else -> {
                outFile = index.getAlternativeFile(preferredOutName)
            }
        }
    }

    if (!TransientOperationWaiter.awaitDestinationReady(context, operation, targetDir, meta.originalSize)) {
        OperationJournal.markFailed(operation, "destination unavailable or insufficient space")
        return null
    }

    val staging = AtomicStaging.fileFor(outFile, operation)
    val encSourceFp = MediaIdentity.computeFingerprint(encryptedFile)
    var committed = false

    return try {
        val encPath = encryptedFile.absolutePath
        TransientOperationWaiter.runWithTransientRetry(
            context = context,
            ctx = operation,
            destDir = targetDir,
            requiredBytes = meta.originalSize,
            onDiscardAttempt = {
                AtomicStaging.discard(staging, containsPlaintext = true, context = context)
            }
        ) {
            OperationJournal.markInFlight(
                ctx = operation,
                stagingPath = staging.absolutePath,
                resolvedDestName = outFile.name,
                actualDestPath = outFile.absolutePath,
                sourceFingerprint = encSourceFp
            )
            openDecryptedContentStream(encryptedFile, 0L, meta)?.use { cis ->
                FileOutputStream(staging).use { fos ->
                    BufferedOutputStream(fos, 256 * 1024).use { bos ->
                        val buffer = ByteArray(256 * 1024)
                        var read: Int
                        while (cis.read(buffer).also { read = it } != -1) {
                            bos.write(buffer, 0, read)
                        }
                        bos.flush()
                        fos.fd.sync()
                    }
                }
            } ?: throw IOException("Could not open decrypted content stream")

            AtomicStaging.commit(staging, outFile, alreadyFileSynced = true)
            committed = true
        }
        if (outFile.length() != meta.originalSize) {
            throw IOException("Decrypted output size does not match the container header")
        }
        OperationJournal.markVerified(operation, MediaIdentity.computeFingerprint(outFile), outFile.absolutePath)

        outFile.setLastModified(meta.lastModified)
        persistDecryptedDateTaken(context, outFile, meta.dateTaken, meta.lastModified)
        index.recordFile(outFile)

        if (deleteEncryptedSource) {
            runCatching {
                context.markReferencesOnDecrypt(
                    encPath = encPath,
                    decryptedFile = outFile,
                    hideStealthReferences = true
                )
            }
            metadataCache.remove(encPath)?.fileKey?.fill(0)
            runCatching { context.mediaDB.deleteMediumPath(encPath) }
            encryptedFile.delete()
            OperationJournal.markSourceDeleted(operation)
        } else {
            runCatching {
                context.markReferencesOnDecrypt(
                    encPath = encPath,
                    decryptedFile = outFile,
                    hideStealthReferences = false
                )
            }
        }
        runCatching {
            context.rescanPath(outFile.absolutePath) {
                updateMediaStoreTimestamps(context, outFile.absolutePath, meta.dateTaken, meta.lastModified)
            }
        }
        outFile
    } catch (e: Exception) {
        AtomicStaging.discard(staging, containsPlaintext = true, context = context)
        // A post-commit failure can leave a bad plaintext output behind: wipe only if we actually committed it.
        if (committed) {
            runCatching { secureWipeAndDeleteFile(outFile, context, syncParentDir = false, purgeTraces = false) }
        }
        OperationJournal.markFailed(operation, e.message ?: "decrypt failed")
        null
    }
}


fun CryptoManager.getDisplayName(path: String): String {
    if (isEncryptedFile(path)) {
        readMetadata(File(path))?.originalName?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return path.getFilenameFromPath()
}


fun CryptoManager.moveMetadataCacheEntry(oldPath: String, newPath: String) {
    metadataCache.remove(oldPath)?.let { meta ->
        metadataCache[newPath] = meta
    }
}


/**
 * Renames the encrypted display name stored inside a .bin file's AES-GCM header.
 * The .bin filename on disk remains completely unrelated (random hex).
 * Re-encrypts only the header and shifts the authenticated payload without re-encrypting media bytes.
 */
fun CryptoManager.renameEncryptedFile(
    encryptedFile: File,
    newOriginalName: String,
    skipIntegrityCheck: Boolean = false,
    syncParentDir: Boolean = true
): Boolean {
    if (!encryptedFile.exists() || !encryptedFile.isFile || !isEncryptedFile(encryptedFile.name)) return false
    val trimmedName = newOriginalName.trim()
    if (trimmedName.isEmpty()) return false
    if (unlockedMasterKey == null) return false

    val meta = readMetadata(encryptedFile) ?: return false
    if (meta.originalName == trimmedName) return true

    if (!skipIntegrityCheck && meta.isMacAuthenticated && !verifyFileIntegrity(encryptedFile)) {
        return false
    }

    val parentDir = encryptedFile.parentFile ?: return false
    val tempFile = File(parentDir, ".tmp_${randomHex(8)}$ENCRYPTED_BIN_EXT")

    return try {
        val newExt = trimmedName.substringAfterLast('.', "")
        val updatedMediaType = if (newExt.isNotEmpty() && (
                trimmedName.isImageFast() || trimmedName.isVideoFast() ||
                    trimmedName.isGif() || trimmedName.isRawFast() || trimmedName.isSvg()
            )
        ) {
            getMediaTypeForName(trimmedName)
        } else {
            meta.mediaType
        }

        val thumbBytes = readEmbeddedThumbnailBytes(encryptedFile) ?: meta.thumbnailBytes ?: ByteArray(0)

        val kek = activeKek() ?: return false
        val fileId = meta.fileId ?: return false
        val newPayloadOffset = VaultContainerWriter.rewriteContainer(
            source = encryptedFile,
            target = tempFile,
            kek = kek,
            keyId = meta.keyId,
            fileId = fileId,
            dek = meta.fileKey,
            version = meta.version,
            sourcePayloadOffset = meta.payloadOffset,
            ciphertextLength = meta.ciphertextLength,
            segmentSize = meta.segmentSize,
            originalName = trimmedName,
            lastModified = meta.lastModified,
            dateTaken = meta.dateTaken,
            originalSize = meta.originalSize,
            mediaType = updatedMediaType,
            videoDuration = meta.videoDuration,
            thumbnail = if (thumbBytes.isNotEmpty()) thumbBytes else null,
            syncFile = syncParentDir
        )

        java.nio.file.Files.move(
            tempFile.toPath(),
            encryptedFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )

        encryptedFile.setLastModified(meta.lastModified)
        if (syncParentDir) {
            syncDir(parentDir)
        }

        metadataCache[encryptedFile.absolutePath] = meta.copy(
            originalName = trimmedName,
            mediaType = updatedMediaType,
            payloadOffset = newPayloadOffset,
            thumbnailBytes = if (thumbBytes.isNotEmpty()) thumbBytes else null
        )
        true
    } catch (e: Exception) {
        tempFile.delete()
        false
    }
}


// ==================== EXTERNAL VAULT SYNC ====================

internal fun CryptoManager.hasFolderMetaFile(dir: File): Boolean {
    return File(dir, VAULT_SUBFOLDER_META_FILE).exists()
}


fun CryptoManager.resolveSyncSourceRoot(selectedDir: File): File {
    if (!selectedDir.exists() || !selectedDir.isDirectory) return selectedDir

    if (hasValidVaultHeader(selectedDir) || hasFolderMetaFile(selectedDir)) {
        return selectedDir
    }

    val immediateChildren = selectedDir.listFiles() ?: emptyArray()
    if (immediateChildren.any { isEncryptedBinFile(it) }) {
        return selectedDir
    }

    // CLI staging folders are checked first: when both a staging folder and a
    // vault folder live under the same parent, the user is importing a CLI batch.
    // No assumption is made that the active vault is named "Secrets" — it may be
    // at a custom path, and several vaults may exist on the device.
    val nestedContainerCandidates = mutableListOf(
        File(File(selectedDir, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME),
        File(selectedDir, CLI_STAGING_DIR_NAME),
        File(File(selectedDir, CIPHER_APP_DIR_NAME), SECRETS_DIR_NAME),
        File(selectedDir, SECRETS_DIR_NAME)
    )
    nestedContainerCandidates += listOf(
        File(selectedDir, "Encrypted_Output"),
        File(selectedDir, "Sifreli_Cikti")
    )
    for (candidate in nestedContainerCandidates) {
        if (candidate.exists() && candidate.isDirectory) {
            return candidate
        }
    }

    val hasImmediateSubdirWithVaultContent = immediateChildren.any { sub ->
        sub.isDirectory &&
            !sub.name.equals("JSON", ignoreCase = true) &&
            (!sub.name.startsWith(".") || hasFolderMetaFile(sub)) &&
            (hasFolderMetaFile(sub) || (sub.listFiles()?.any { isEncryptedBinFile(it) } == true))
    }
    if (hasImmediateSubdirWithVaultContent) {
        return selectedDir
    }

    return selectedDir
}
