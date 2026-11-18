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
                    } else if (!wipeSource) {
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
                    val removed = index.removeExactEntries(originalName)
                    removed.forEach { existingBin ->
                        metadataCache.remove(existingBin.absolutePath)?.fileKey?.fill(0)
                        runCatching { context.mediaDB.deleteMediumPath(existingBin.absolutePath) }
                        existingBin.delete()
                    }
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
        runCatching { context.getDuration(sourceFile.absolutePath) }.getOrNull() ?: 0
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
                outFile.delete()
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
        outFile.delete()
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

    val nestedContainerCandidates = mutableListOf(
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
