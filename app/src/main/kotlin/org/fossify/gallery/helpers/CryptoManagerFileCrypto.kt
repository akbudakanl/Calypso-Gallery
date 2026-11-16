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
        file.name != ENCRYPTED_FOLDER_META_FILE &&
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
    matchCollisionVariants: Boolean = true
): File? {
    if (!sourceFile.exists() || !sourceFile.isFile) return null
    if (isEncryptedFile(sourceFile.name)) return sourceFile

    val masterKey = unlockedMasterKey ?: return null
    val fileKey: ByteArray = masterKey
    val versionByte: Byte = VERSION_SYMMETRIC_AUTH

    if (!targetDir.exists()) {
        targetDir.mkdirs()
    }

    var originalName = sourceFile.name
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
                CONFLICT_SKIP -> return null
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

    val headerPlainBytes = ByteArrayOutputStream().use { baos ->
        DataOutputStream(baos).use { dos ->
            dos.writeUTF(originalName)
            dos.writeLong(lastModified)
            dos.writeLong(dateTaken)
            dos.writeLong(originalSize)
            dos.writeInt(mediaType)
            dos.writeInt(videoDuration)
            dos.writeInt(thumbBytes.size)
            if (thumbBytes.isNotEmpty()) {
                dos.write(thumbBytes)
            }
        }
        baos.toByteArray()
    }

    val prepared = VaultContainerWriter.prepare(headerPlainBytes, fileKey, versionByte)

    var encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    while (encryptedFile.exists()) {
        encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    }

    return try {
        val sourcePath = sourceFile.absolutePath
        FileInputStream(sourceFile).use { fis ->
            VaultContainerWriter.writeContainer(
                target = encryptedFile,
                prepared = prepared,
                fileKey = fileKey,
                payloadProvider = { fis },
                bufferSize = 256 * 1024
            )
        }

        encryptedFile.setLastModified(lastModified)

        val payloadOffset = VaultContainer.payloadOffset(prepared.encryptedHeader.size)
        val newMeta = EncryptedFileMetadata(
            originalName = originalName,
            lastModified = lastModified,
            dateTaken = dateTaken,
            originalSize = originalSize,
            mediaType = mediaType,
            videoDuration = videoDuration,
            payloadOffset = payloadOffset,
            contentIv = prepared.contentIv,
            fileKey = fileKey.copyOf(),
            version = versionByte,
            isMacAuthenticated = true,
            thumbnailBytes = if (thumbBytes.isNotEmpty()) thumbBytes else null
        )
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
        }
        if (clearCachesNow) {
            runCatching { clearAppImageCaches(context) }
        }
        encryptedFile
    } catch (e: Exception) {
        encryptedFile.delete()
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
    val masterKey = unlockedMasterKey ?: return null
    val fileKey: ByteArray = masterKey
    val versionByte: Byte = VERSION_SYMMETRIC_AUTH

    if (!targetDir.exists()) {
        targetDir.mkdirs()
    }

    val fallbackNow = System.currentTimeMillis()
    val lastModified = lastModifiedOverride?.takeIf { it > 0L } ?: fallbackNow
    val dateTaken = dateTakenOverride?.takeIf { it > 0L } ?: lastModified
    val initialSize = if (contentLength > 0L) contentLength else 0L
    val mediaType = getMediaTypeForName(originalName)
    val videoDuration = 0
    val thumbBytes = ByteArray(0)

    fun buildHeaderBytes(size: Long): ByteArray {
        return ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeUTF(originalName)
                dos.writeLong(lastModified)
                dos.writeLong(dateTaken)
                dos.writeLong(size)
                dos.writeInt(mediaType)
                dos.writeInt(videoDuration)
                dos.writeInt(thumbBytes.size)
                if (thumbBytes.isNotEmpty()) {
                    dos.write(thumbBytes)
                }
            }
            baos.toByteArray()
        }
    }

    val headerPlainBytes = buildHeaderBytes(initialSize)

    val prepared = VaultContainerWriter.prepare(headerPlainBytes, fileKey, versionByte)

    var encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    while (encryptedFile.exists()) {
        encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
    }

    return try {
        val totalBytesRead = VaultContainerWriter.writeContainer(
            target = encryptedFile,
            prepared = prepared,
            fileKey = fileKey,
            payloadProvider = { inputStream },
            bufferSize = 64 * 1024,
            onPayloadBytes = { bytesRead -> onProgress?.invoke(bytesRead, contentLength) }
        )

        if (contentLength <= 0L || contentLength != totalBytesRead) {
            // Gercek boyut bastan bilinmiyordu: header icindeki boyut alanini
            // duzelt. Header alanlari sabit boyutlu oldugu icin payload ofseti kaymaz.
            VaultContainerWriter.rewriteHeaderInPlace(
                encryptedFile,
                buildHeaderBytes(totalBytesRead),
                fileKey
            )
        }

        encryptedFile.setLastModified(lastModified)

        val finalThumbBytes = if (mediaType == TYPE_IMAGES) {
            generateEmbeddedThumbnailFromEncryptedFile(encryptedFile)
        } else null

        val payloadOffset = VaultContainer.payloadOffset(prepared.encryptedHeader.size)
        metadataCache[encryptedFile.absolutePath] = EncryptedFileMetadata(
            originalName = originalName,
            lastModified = lastModified,
            dateTaken = dateTaken,
            originalSize = totalBytesRead,
            mediaType = mediaType,
            videoDuration = videoDuration,
            payloadOffset = payloadOffset,
            contentIv = prepared.contentIv,
            fileKey = fileKey.copyOf(),
            version = versionByte,
            isMacAuthenticated = true,
            thumbnailBytes = finalThumbBytes
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
    matchCollisionVariants: Boolean = true
): File? {
    if (!encryptedFile.exists() || !isEncryptedFile(encryptedFile.name)) return null
    val meta = readMetadata(encryptedFile, includeThumbnail = false, cacheResult = false) ?: return null

    if (meta.isMacAuthenticated && !verifyFileIntegrity(encryptedFile, meta)) {
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
                } else if (deleteSourceOnIdentical && encCanon != plainCanon) {
                    runCatching {
                        context.markReferencesOnDecrypt(
                            encPath = encryptedFile.absolutePath,
                            decryptedFile = existingPlain,
                            hideStealthReferences = false
                        )
                    }
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

    var outFile = index.findExact(meta.originalName) ?: File(targetDir, meta.originalName)
    if (outFile.exists()) {
        when (conflictResolution) {
            CONFLICT_SKIP -> return null
            CONFLICT_OVERWRITE -> {
                outFile.delete()
                index.removeFile(outFile)
            }
            else -> {
                outFile = index.getAlternativeFile(meta.originalName)
            }
        }
    }

    return try {
        val encPath = encryptedFile.absolutePath
        openDecryptedContentStream(encryptedFile, 0L, meta)?.use { cis ->
            FileOutputStream(outFile).use { fos ->
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
        } ?: return null

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
        outFile.delete()
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
        val magicBytes = ByteArray(8)
        val version: Byte

        RandomAccessFile(encryptedFile, "r").use { raf ->
            raf.readFully(magicBytes)
            version = raf.readByte()
        }

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
        val headerPlainBytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeUTF(trimmedName)
                dos.writeLong(meta.lastModified)
                dos.writeLong(meta.dateTaken)
                dos.writeLong(meta.originalSize)
                dos.writeInt(updatedMediaType)
                dos.writeInt(meta.videoDuration)
                dos.writeInt(thumbBytes.size)
                if (thumbBytes.isNotEmpty()) {
                    dos.write(thumbBytes)
                }
            }
            baos.toByteArray()
        }

        val newPayloadOffset = VaultContainerWriter.rewriteContainer(
            source = encryptedFile,
            target = tempFile,
            sourcePayloadOffset = meta.payloadOffset,
            sourcePayloadLength = meta.originalSize,
            sourceContentIv = meta.contentIv,
            newHeaderPlainBytes = headerPlainBytes,
            fileKey = meta.fileKey,
            version = version,
            macCoversHeader = version == VERSION_V5_AUTH,
            magic = magicBytes,
            useDerivedHeaderKey = meta.isMacAuthenticated,
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
    return File(dir, VAULT_SUBFOLDER_META_FILE).exists() ||
        File(dir, ENCRYPTED_FOLDER_META_FILE).exists()
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

    val nestedContainerCandidates = listOf(
        File(File(selectedDir, CIPHER_APP_DIR_NAME), CIPHER_SECRETS_DIR_NAME),
        File(File(selectedDir, CIPHER_ROOT_DIR_NAME), CIPHER_SECRETS_DIR_NAME),
        File(selectedDir, CIPHER_SECRETS_DIR_NAME),
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

