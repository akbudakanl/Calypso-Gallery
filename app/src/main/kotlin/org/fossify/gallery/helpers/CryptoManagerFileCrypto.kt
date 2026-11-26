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
 * Extracted from [CryptoManager]. Feature group: CryptoManagerFileCrypto.
 */

// ==================== FILE ENCRYPTION & DECRYPTION ====================

/**
 * True only for file formats the gallery can actually display (images, GIFs, videos, RAWs, SVGs).
 * Anything else (.pdf, .md, .txt ...) must never be encrypted, otherwise it would end up in the vault
 * as an unreadable, seemingly corrupted .bin file.
 */
fun CryptoManager.isSupportedMediaName(name: String): Boolean {
    return name.isImageFast() || name.isVideoFast() || name.isGif() || name.isRawFast() || name.isSvg()
}


/** True if [file] is a regular, not yet encrypted file of a media format supported by the gallery. */
fun CryptoManager.isEncryptableMediaFile(file: File): Boolean {
    return file.isFile &&
        file.name != NOMEDIA &&
        file.name != VAULT_SUBFOLDER_META_FILE &&
        file.name != VAULT_SUBFOLDER_ORIGIN_FILE &&
        file.name != VAULT_HEADER_FILE &&
        !isEncryptedFile(file.name) &&
        isSupportedMediaName(file.name)
}


internal fun CryptoManager.getMediaTypeForName(name: String): Int {
    return when {
        name.isGif() -> TYPE_GIFS
        name.isRawFast() -> TYPE_RAWS
        name.isVideoFast() -> TYPE_VIDEOS
        name.isSvg() -> TYPE_SVGS
        else -> TYPE_IMAGES
    }
}


internal fun CryptoManager.generateEmbeddedThumbnail(file: File, mediaType: Int): ByteArray {
    return try {
        val bitmap: Bitmap? = if (mediaType == TYPE_VIDEOS) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(-1)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > THUMB_MAX_DIMENSION * 2 ||
                opts.outHeight / sampleSize > THUMB_MAX_DIMENSION * 2
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        }

        if (bitmap == null) return ByteArray(0)
        val width = bitmap.width
        val height = bitmap.height
        val scale = THUMB_MAX_DIMENSION.toFloat() / maxOf(width, height).coerceAtLeast(1)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, baos)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        baos.toByteArray()
    } catch (e: Exception) {
        ByteArray(0)
    }
}


fun CryptoManager.randomHex(bytesCount: Int = 8): String {
    val bytes = ByteArray(bytesCount)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}


/**
 * Encrypts a single plaintext media file into targetDir as random hex .bin.
 */
fun CryptoManager.encryptFileToDirectory(
    context: Context,
    sourceFile: File,
    targetDir: File,
    wipeSource: Boolean = true,
    clearCachesNow: Boolean = false,
    conflictResolution: Int = CONFLICT_KEEP_BOTH,
    targetIndex: TargetEncryptedIndex? = null,
    skipDuplicateCheck: Boolean = false,
    deleteSourceOnIdentical: Boolean = true,
    matchCollisionVariants: Boolean = true,
    operation: OperationContext = OperationContext.NONE,
    forcedOriginalName: String? = null
): File? {
    if (!sourceFile.exists() || !sourceFile.isFile) {
        OperationJournal.markFailed(operation, "source missing")
        return null
    }
    if (isEncryptedFile(sourceFile.name)) return sourceFile

    val kek = activeKek()
    if (kek == null) {
        OperationJournal.markFailed(operation, "vault locked")
        return null
    }
    val versionByte = VaultContainer.VAULT_FORMAT_VERSION

    if (!targetDir.exists()) {
        targetDir.mkdirs()
    }

    var originalName = forcedOriginalName?.takeIf { it.isNotEmpty() } ?: sourceFile.name
    val sourceLastModified = sourceFile.lastModified()
    val originalSize = sourceFile.length()
    val sourceMediaType = getMediaTypeForName(originalName)
    var resolvedDateTaken: Long? = null
    fun getSourceDateTaken(): Long {
        return resolvedDateTaken ?: resolvePlaintextDateTaken(
            context = context,
            sourceFile = sourceFile,
            fallbackLastModified = sourceLastModified
        ).also { resolvedDateTaken = it }
    }

    var activeIndex: TargetEncryptedIndex? = targetIndex
    var pendingOverwriteBins: List<File> = emptyList()
    run {
        val index = activeIndex ?: TargetEncryptedIndex(targetDir).also { activeIndex = it }

        if (!skipDuplicateCheck) {
            val candidates = index.findCandidates(originalName, matchCollisionVariants)
            for ((existingBin, existingMeta) in candidates) {
                if (existingMeta.originalSize != originalSize || existingMeta.mediaType != sourceMediaType) {
                    continue
                }
                if (context.shouldGateDuplicatesByDate() &&
                    !areMediaTimestampsEquivalent(
                        sourceLastModified,
                        getSourceDateTaken(),
                        existingMeta.lastModified,
                        existingMeta.dateTaken
                    )
                ) {
                    continue
                }
                if (arePlainAndEncryptedIdentical(sourceFile, existingBin, existingMeta)) {
                    applyDuplicateDateToEncryptedFile(context, existingBin, sourceLastModified)
                    val dupFingerprint = MediaIdentity.computeFingerprint(existingBin)
                    if (wipeSource && deleteSourceOnIdentical) {
                        val duplicateSourcePath = sourceFile.absolutePath
                        val plainIdentity = runCatching {
                            MediaIdentity.ofWithFolder(context, sourceFile)
                        }.getOrNull()
                        runCatching {
                            context.markReferencesOnEncrypt(
                                sourcePath = duplicateSourcePath,
                                encPath = existingBin.absolutePath,
                                plaintextIdentity = plainIdentity,
                                markPlaintextDormant = true
                            )
                        }
                        if (secureWipeAndDeleteFile(sourceFile, context, syncParentDir = true, purgeTraces = false)) {
                            runCatching { purgeMediaTracesPreservingReferences(context, duplicateSourcePath) }
                        }
                        OperationJournal.markVerified(operation, dupFingerprint, existingBin.absolutePath)
                        OperationJournal.markSourceDeleted(operation)
                    } else if (!wipeSource && deleteSourceOnIdentical) {
                        OperationJournal.markVerified(operation, dupFingerprint, existingBin.absolutePath)
                    } else {
                        OperationJournal.markSkipped(operation)
                    }
                    if (clearCachesNow && deleteSourceOnIdentical) {
                        runCatching { clearAppImageCaches(context) }
                    }
                    return existingBin
                }
            }
        }

        val exactMatches = index.findExactEntries(originalName)
        if (exactMatches.isNotEmpty()) {
            when (conflictResolution) {
                CONFLICT_SKIP -> {
                    OperationJournal.markSkipped(operation)
                    return null
                }
                CONFLICT_OVERWRITE -> {
                    // Defer deleting existing overwritten containers until the replacement commits
                    // and passes read-back verification so crashes/errors cannot lose the old copy.
                    pendingOverwriteBins = index.removeExactEntries(originalName)
                }
                else -> {
                    originalName = index.getAlternativeOriginalName(originalName)
                }
            }
        }
    }

    val lastModified = sourceLastModified
    val dateTaken = getSourceDateTaken()
    val mediaType = sourceMediaType
    val videoDuration = if (mediaType == TYPE_VIDEOS) {
        val systemDuration = runCatching { context.getDuration(sourceFile.absolutePath) }.getOrNull() ?: 0
        if (systemDuration > 0) {
            systemDuration
        } else {
            runCatching { probeContainerDurationSeconds(sourceFile) }.getOrDefault(0)
        }
    } else {
        0
    }
    val thumbBytes = runCatching { generateEmbeddedThumbnail(sourceFile, mediaType) }.getOrDefault(ByteArray(0))
    val thumbnailForContainer = if (thumbBytes.isNotEmpty()) thumbBytes else null

    val prepared = VaultContainerWriter.prepare(
        kek = kek,
        keyId = 1,
        originalName = originalName,
        lastModified = lastModified,
        dateTaken = dateTaken,
        originalSize = originalSize,
        mediaType = mediaType,
        videoDuration = videoDuration,
        thumbnail = thumbnailForContainer,
        version = versionByte
    )

    var encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    while (encryptedFile.exists()) {
        encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    }

    val expectedOutBytes = VaultContainerWriter.estimateEncryptedSize(
        EncryptedSizeInput(originalSize, originalName, thumbBytes.size)
    )
    if (!TransientOperationWaiter.awaitDestinationReady(context, operation, targetDir, expectedOutBytes)) {
        OperationJournal.markFailed(operation, "destination unavailable or insufficient space")
        return null
    }

    val staging = AtomicStaging.fileFor(encryptedFile, operation)
    val sourceFp = MediaIdentity.computeFingerprint(sourceFile)

    return try {
        val sourcePath = sourceFile.absolutePath
        TransientOperationWaiter.runWithTransientRetry(
            context = context,
            ctx = operation,
            destDir = targetDir,
            requiredBytes = expectedOutBytes,
            onDiscardAttempt = {
                AtomicStaging.discard(staging, containsPlaintext = false, context = context)
            }
        ) {
            OperationJournal.markInFlight(
                ctx = operation,
                stagingPath = staging.absolutePath,
                resolvedDestName = originalName,
                actualDestPath = encryptedFile.absolutePath,
                sourceFingerprint = sourceFp
            )
            FileInputStream(sourceFile).use { fis ->
                VaultContainerWriter.writeContainer(
                    target = staging,
                    prepared = prepared,
                    payloadProvider = { fis }
                )
            }
            AtomicStaging.commit(staging, encryptedFile, alreadyFileSynced = true)
        }

        val newMeta = EncryptedFileMetadata(
            originalName = originalName,
            lastModified = lastModified,
            dateTaken = dateTaken,
            originalSize = originalSize,
            mediaType = mediaType,
            videoDuration = videoDuration,
            payloadOffset = prepared.payloadOffset,
            contentIv = ByteArray(0),
            fileKey = prepared.dek.copyOf(),
            version = versionByte,
            isMacAuthenticated = true,
            thumbnailBytes = thumbnailForContainer,
            fileId = prepared.fileId.copyOf(),
            keyId = 1,
            ciphertextLength = prepared.ciphertextLength,
            isCanonicalFormat = true,
            segmentSize = TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE
        )

        // ADR 0003 §3: no source is wiped on the strength of an unverified container.
        if (!verifyFileIntegrity(encryptedFile, newMeta)) {
            throw IOException("Encrypted container failed read-back verification")
        }
        OperationJournal.markVerified(
            operation,
            MediaIdentity.computeFingerprint(encryptedFile),
            encryptedFile.absolutePath
        )

        if (pendingOverwriteBins.isNotEmpty()) {
            pendingOverwriteBins.forEach { existingBin ->
                metadataCache.remove(existingBin.absolutePath)?.fileKey?.fill(0)
                runCatching { context.mediaDB.deleteMediumPath(existingBin.absolutePath) }
                runCatching { existingBin.delete() }
            }
        }

        encryptedFile.setLastModified(lastModified)

        metadataCache[encryptedFile.absolutePath] = newMeta
        activeIndex?.addEntry(encryptedFile, newMeta)

        if (wipeSource) {
            val plainIdentity = runCatching {
                MediaIdentity.ofWithFolder(context, sourceFile)
            }.getOrNull()
            runCatching {
                context.markReferencesOnEncrypt(
                    sourcePath = sourcePath,
                    encPath = encryptedFile.absolutePath,
                    plaintextIdentity = plainIdentity,
                    markPlaintextDormant = true
                )
            }
            if (secureWipeAndDeleteFile(sourceFile, context, syncParentDir = true, purgeTraces = false)) {
                runCatching { purgeMediaTracesPreservingReferences(context, sourcePath) }
            }
            OperationJournal.markSourceDeleted(operation)
        }
        if (clearCachesNow) {
            runCatching { clearAppImageCaches(context) }
        }
        encryptedFile
    } catch (e: Exception) {
        AtomicStaging.discard(staging, containsPlaintext = false, context = context)
        encryptedFile.delete()
        OperationJournal.markFailed(operation, e.message ?: "encrypt failed")
        null
    }
}


/**
 * Directly encrypts a streaming InputStream (e.g. from network or content resolver)
 * into a hidden vault directory WITHOUT ever writing plaintext to disk.
 * Prevents any data remanence on flash storage.
 */
fun CryptoManager.encryptStreamToDirectory(
    context: Context? = null,
    inputStream: InputStream,
    targetDir: File,
    originalName: String,
    contentLength: Long = -1L,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    lastModifiedOverride: Long? = null,
    dateTakenOverride: Long? = null
): File? {
    fun perform(): File? {
        val kek = activeKek() ?: return null
        val versionByte = VaultContainer.VAULT_FORMAT_VERSION

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val fallbackNow = System.currentTimeMillis()
        val lastModified = lastModifiedOverride?.takeIf { it > 0L } ?: fallbackNow
        val dateTaken = dateTakenOverride?.takeIf { it > 0L } ?: lastModified
        val initialSize = if (contentLength > 0L) contentLength else 0L
        val mediaType = getMediaTypeForName(originalName)
        val videoDuration = 0
        val prepared = VaultContainerWriter.prepare(
            kek = kek,
            keyId = 1,
            originalName = originalName,
            lastModified = lastModified,
            dateTaken = dateTaken,
            originalSize = initialSize,
            mediaType = mediaType,
            videoDuration = videoDuration,
            thumbnail = null,
            version = versionByte
        )

        var encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        while (encryptedFile.exists()) {
            encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        }

        return try {
            val totalBytesRead = VaultContainerWriter.writeContainer(
                target = encryptedFile,
                prepared = prepared,
                payloadProvider = { inputStream },
                onPayloadBytes = { bytesRead -> onProgress?.invoke(bytesRead, contentLength) }
            )

            if (contentLength <= 0L || contentLength != totalBytesRead) {
                // Gerçek boyut baştan bilinmiyordu: header'daki sabit uzunluklu alanları düzelt.
                // fileId/dek korunduğu için Tink akışı geçerli kalır.
                VaultContainerWriter.rewriteHeaderInPlace(
                    file = encryptedFile,
                    kek = kek,
                    keyId = 1,
                    prepared = prepared,
                    originalName = originalName,
                    lastModified = lastModified,
                    dateTaken = dateTaken,
                    originalSize = totalBytesRead,
                    mediaType = mediaType,
                    videoDuration = videoDuration,
                    thumbnail = null
                )
            }

            encryptedFile.setLastModified(lastModified)

            val finalThumbBytes = if (mediaType == TYPE_IMAGES) {
                generateEmbeddedThumbnailFromEncryptedFile(encryptedFile)
            } else null

            metadataCache[encryptedFile.absolutePath] = EncryptedFileMetadata(
                originalName = originalName,
                lastModified = lastModified,
                dateTaken = dateTaken,
                originalSize = totalBytesRead,
                mediaType = mediaType,
                videoDuration = videoDuration,
                payloadOffset = prepared.payloadOffset,
                contentIv = ByteArray(0),
                fileKey = prepared.dek.copyOf(),
                version = versionByte,
                isMacAuthenticated = true,
                thumbnailBytes = finalThumbBytes,
                fileId = prepared.fileId.copyOf(),
                keyId = 1,
                ciphertextLength = TinkStreamSpec.expectedCiphertextSize(totalBytesRead),
                isCanonicalFormat = true,
                segmentSize = TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE
            )

            encryptedFile
        } catch (e: Exception) {
            encryptedFile.delete()
            null
        } finally {
            prepared.dek.fill(0)
        }
    }

    return if (context != null) {
        ForegroundOperationRegistry.withOperation(context, "stream-encrypt") { perform() }
    } else {
        perform()
    }
}


internal fun CryptoManager.generateEmbeddedThumbnailFromEncryptedFile(encryptedFile: File): ByteArray? {
    return try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openDecryptedContentStream(encryptedFile)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > THUMB_MAX_DIMENSION * 2 ||
            boundsOpts.outHeight / sampleSize > THUMB_MAX_DIMENSION * 2
        ) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = openDecryptedContentStream(encryptedFile)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val width = bitmap.width
        val height = bitmap.height
        val scale = THUMB_MAX_DIMENSION.toFloat() / maxOf(width, height).coerceAtLeast(1)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, baos)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        baos.toByteArray()
    } catch (_: Exception) {
        null
    }
}


