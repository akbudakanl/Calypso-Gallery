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
 * Extracted from [CryptoManager]. Feature group: CryptoManagerConflict.
 */

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerFileComparison.
 */

internal fun CryptoManager.readExactOrEof(stream: InputStream, buffer: ByteArray, length: Int): Int {
    var total = 0
    while (total < length) {
        val read = stream.read(buffer, total, length - total)
        if (read == -1) break
        total += read
    }
    return total
}


internal fun CryptoManager.bufferPrefixEquals(bufA: ByteArray, bufB: ByteArray, len: Int): Boolean {
    return java.nio.ByteBuffer.wrap(bufA, 0, len) == java.nio.ByteBuffer.wrap(bufB, 0, len)
}


internal fun CryptoManager.computeFastSampleOffsets(size: Long, chunkSize: Int): LongArray {
    val maxOffset = (size - chunkSize).coerceAtLeast(0L)
    return longArrayOf(0L, size / 4L, size / 2L, (size * 3L) / 4L, maxOffset)
}


internal inline fun CryptoManager.filesMatchFastSampled(
    totalSize: Long,
    readChunkA: (offset: Long, buf: ByteArray, len: Int) -> Boolean,
    readChunkB: (offset: Long, buf: ByteArray, len: Int) -> Boolean
): Boolean {
    val chunkSize = FAST_SAMPLING_CHUNK_BYTES
    val bufA = ByteArray(chunkSize)
    val bufB = ByteArray(chunkSize)
    for (offset in computeFastSampleOffsets(totalSize, chunkSize)) {
        if (!readChunkA(offset, bufA, chunkSize)) return false
        if (!readChunkB(offset, bufB, chunkSize)) return false
        if (!bufferPrefixEquals(bufA, bufB, chunkSize)) return false
    }
    return true
}


internal fun CryptoManager.readPlainChunk(raf: RandomAccessFile, offset: Long, buf: ByteArray, len: Int): Boolean {
    return try {
        raf.seek(offset)
        raf.readFully(buf, 0, len)
        true
    } catch (_: Exception) {
        false
    }
}


internal fun CryptoManager.readEncryptedChunk(
    file: File,
    meta: EncryptedFileMetadata,
    offset: Long,
    buf: ByteArray,
    len: Int
): Boolean {
    return try {
        openDecryptedContentStream(file, offset, meta)?.use { decIn ->
            readExactOrEof(decIn, buf, len) == len
        } ?: false
    } catch (_: Exception) {
        false
    }
}


internal fun CryptoManager.streamsHaveEqualBytes(
    streamA: InputStream,
    streamB: InputStream,
    byteCount: Long,
    bufferSize: Int = 256 * 1024
): Boolean {
    val effectiveBufSize = minOf(maxOf(byteCount, 1L), bufferSize.toLong()).toInt()
    val bufA = ByteArray(effectiveBufSize)
    val bufB = ByteArray(effectiveBufSize)
    var remaining = byteCount
    while (remaining > 0L) {
        val toRead = minOf(remaining, effectiveBufSize.toLong()).toInt()
        val readA = streamA.read(bufA, 0, toRead)
        if (readA <= 0) return false
        val readB = readExactOrEof(streamB, bufB, readA)
        if (readB != readA) return false
        if (!bufferPrefixEquals(bufA, bufB, readA)) {
            return false
        }
        remaining -= readA.toLong()
    }
    return true
}


internal fun CryptoManager.isMacAuthenticatedContainerHeader(file: File): Boolean {
    if (file.length() < 9L + MAC_TAG_LENGTH) return false
    return runCatching {
        val head = ByteArray(9)
        FileInputStream(file).use { fis ->
            if (readExactOrEof(fis, head, 9) != 9) return@runCatching false
        }
        val magic = String(head, 0, 8, Charsets.US_ASCII)
        val version = head[8]
        (magic == MAGIC_V2 || magic == MAGIC_V1) &&
            (version == VERSION_SYMMETRIC_AUTH || version == VERSION_V5_AUTH)
    }.getOrDefault(false)
}


internal fun CryptoManager.trailingMacTagsEqual(fileA: File, fileB: File): Boolean {
    if (fileA.length() < MAC_TAG_LENGTH || fileB.length() < MAC_TAG_LENGTH) return false
    return runCatching {
        val tagA = ByteArray(MAC_TAG_LENGTH)
        val tagB = ByteArray(MAC_TAG_LENGTH)
        RandomAccessFile(fileA, "r").use { raf ->
            raf.seek(fileA.length() - MAC_TAG_LENGTH)
            raf.readFully(tagA)
        }
        RandomAccessFile(fileB, "r").use { raf ->
            raf.seek(fileB.length() - MAC_TAG_LENGTH)
            raf.readFully(tagB)
        }
        tagA.contentEquals(tagB)
    }.getOrDefault(false)
}


internal fun CryptoManager.hasMatchingMacTrailer(sourceBin: File, existingFile: File): Boolean {
    return sourceBin.length() == existingFile.length() &&
        isMacAuthenticatedContainerHeader(sourceBin) &&
        isMacAuthenticatedContainerHeader(existingFile) &&
        trailingMacTagsEqual(sourceBin, existingFile)
}


internal fun CryptoManager.isFastMacMatchDuplicate(
    fileA: File,
    metaA: EncryptedFileMetadata,
    fileB: File,
    metaB: EncryptedFileMetadata
): Boolean {
    return metaA.originalSize > 0L &&
        metaA.originalSize == metaB.originalSize &&
        metaA.mediaType == metaB.mediaType &&
        fileA.length() == fileB.length() &&
        metaA.contentIv.contentEquals(metaB.contentIv) &&
        metaA.isMacAuthenticated &&
        metaB.isMacAuthenticated &&
        fileA.length() >= metaA.payloadOffset + metaA.originalSize + MAC_TAG_LENGTH &&
        fileB.length() >= metaB.payloadOffset + metaB.originalSize + MAC_TAG_LENGTH &&
        trailingMacTagsEqual(fileA, fileB)
}


internal fun CryptoManager.streamsMatchBitForBit(
    fileA: File,
    fileB: File,
    originalSize: Long,
    metaA: EncryptedFileMetadata? = null,
    metaB: EncryptedFileMetadata? = null,
    fastSampling: Boolean = false
): Boolean {
    if (fastSampling && originalSize >= FAST_SAMPLING_THRESHOLD_BYTES && metaA != null && metaB != null) {
        return filesMatchFastSampled(
            totalSize = originalSize,
            readChunkA = { offset, buf, len -> readEncryptedChunk(fileA, metaA, offset, buf, len) },
            readChunkB = { offset, buf, len -> readEncryptedChunk(fileB, metaB, offset, buf, len) }
        )
    }
    return try {
        val sA = openDecryptedContentStream(fileA, 0L, metaA) ?: return false
        sA.use { streamA ->
            val sB = openDecryptedContentStream(fileB, 0L, metaB) ?: return false
            sB.use { streamB ->
                streamsHaveEqualBytes(streamA, streamB, originalSize)
            }
        }
    } catch (_: Exception) {
        false
    }
}


fun CryptoManager.areEncryptedFilesIdenticalContent(
    fileA: File,
    metaA: EncryptedFileMetadata,
    fileB: File,
    metaB: EncryptedFileMetadata,
    fastSampling: Boolean = false
): Boolean {
    if (metaA.originalSize != metaB.originalSize || metaA.mediaType != metaB.mediaType) {
        return false
    }
    if (metaA.originalSize == 0L) {
        return true
    }
    if (isFastMacMatchDuplicate(fileA, metaA, fileB, metaB)) {
        return true
    }
    return streamsMatchBitForBit(fileA, fileB, metaA.originalSize, metaA, metaB, fastSampling = fastSampling)
}


internal fun CryptoManager.compareEncryptedStreamAndVerifySourceMac(
    sourceBin: File,
    sourceMeta: EncryptedFileMetadata,
    existingFile: File,
    existingMeta: EncryptedFileMetadata
): StreamCompareMacResult {
    if (sourceMeta.originalSize != existingMeta.originalSize || sourceMeta.mediaType != existingMeta.mediaType) {
        return StreamCompareMacResult.MISMATCH
    }
    if (sourceMeta.originalSize == 0L) {
        return if (!sourceMeta.isMacAuthenticated || verifyFileIntegrity(sourceBin, sourceMeta)) {
            StreamCompareMacResult.MATCH_VALID
        } else {
            StreamCompareMacResult.SOURCE_MAC_INVALID
        }
    }
    if (sourceMeta.isMacAuthenticated) {
        val expectedTotalSize = sourceMeta.payloadOffset + sourceMeta.originalSize + MAC_TAG_LENGTH
        if (sourceBin.length() != expectedTotalSize) {
            return StreamCompareMacResult.SOURCE_MAC_INVALID
        }
    }

    return try {
        val existingStream = openDecryptedContentStream(existingFile, 0L, existingMeta)
            ?: return StreamCompareMacResult.MISMATCH
        existingStream.use { otherDecIn ->
            BufferedInputStream(FileInputStream(sourceBin), 256 * 1024).use { srcIn ->
                val headerLen = sourceMeta.payloadOffset.toInt()
                val headerBuf = ByteArray(headerLen)
                if (readExactOrEof(srcIn, headerBuf, headerLen) != headerLen) {
                    return StreamCompareMacResult.SOURCE_MAC_INVALID
                }

                val poly: Poly1305? = if (sourceMeta.isMacAuthenticated) {
                    val macKey = deriveSubkey(sourceMeta.fileKey, DOMAIN_CONTENT_MAC_KEY)
                    Poly1305(AESEngine.newInstance()).apply {
                        init(ParametersWithIV(KeyParameter(macKey), sourceMeta.contentIv))
                        macKey.fill(0)
                        if (sourceMeta.version == VERSION_V5_AUTH) {
                            val headerIv = headerBuf.copyOfRange(9, 9 + GCM_IV_LENGTH)
                            val encHeaderLen = java.nio.ByteBuffer.wrap(headerBuf, 9 + GCM_IV_LENGTH, 4).int
                            val encHeader = headerBuf.copyOfRange(9 + GCM_IV_LENGTH + 4, 9 + GCM_IV_LENGTH + 4 + encHeaderLen)
                            update(headerIv, 0, headerIv.size)
                            update(encHeader, 0, encHeader.size)
                        }
                    }
                } else null

                val encKey = if (sourceMeta.isMacAuthenticated) {
                    deriveSubkey(sourceMeta.fileKey, DOMAIN_CONTENT_ENC_KEY)
                } else {
                    sourceMeta.fileKey
                }
                val sourceCipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(sourceMeta.contentIv))
                }
                if (sourceMeta.isMacAuthenticated) {
                    encKey.fill(0)
                }

                val bufSize = minOf(sourceMeta.originalSize, 256L * 1024L).toInt().coerceAtLeast(1)
                val srcCipherBuf = ByteArray(bufSize)
                val srcPlainBuf = ByteArray(bufSize)
                val otherPlainBuf = ByteArray(bufSize)
                var remaining = sourceMeta.originalSize

                while (remaining > 0L) {
                    val toRead = minOf(remaining, bufSize.toLong()).toInt()
                    val readA = srcIn.read(srcCipherBuf, 0, toRead)
                    if (readA <= 0) return StreamCompareMacResult.SOURCE_MAC_INVALID

                    poly?.update(srcCipherBuf, 0, readA)
                    val decLen = sourceCipher.update(srcCipherBuf, 0, readA, srcPlainBuf, 0)
                    val readB = readExactOrEof(otherDecIn, otherPlainBuf, decLen)
                    if (readB != decLen) return StreamCompareMacResult.MISMATCH
                    if (java.nio.ByteBuffer.wrap(srcPlainBuf, 0, decLen) != java.nio.ByteBuffer.wrap(otherPlainBuf, 0, decLen)) {
                        return StreamCompareMacResult.MISMATCH
                    }
                    remaining -= readA.toLong()
                }

                if (poly != null) {
                    val storedTag = ByteArray(MAC_TAG_LENGTH)
                    if (readExactOrEof(srcIn, storedTag, MAC_TAG_LENGTH) != MAC_TAG_LENGTH) {
                        return StreamCompareMacResult.SOURCE_MAC_INVALID
                    }
                    val computedTag = ByteArray(MAC_TAG_LENGTH)
                    poly.doFinal(computedTag, 0)
                    if (!MessageDigest.isEqual(computedTag, storedTag)) {
                        return StreamCompareMacResult.SOURCE_MAC_INVALID
                    }
                }
                StreamCompareMacResult.MATCH_VALID
            }
        }
    } catch (_: Exception) {
        StreamCompareMacResult.MISMATCH
    }
}


internal fun CryptoManager.copyBinAndVerifyIntegritySinglePass(
    sourceBin: File,
    sourceMeta: EncryptedFileMetadata,
    tempFile: File
): Boolean {
    if (sourceMeta.isMacAuthenticated) {
        val expectedTotalSize = sourceMeta.payloadOffset + sourceMeta.originalSize + MAC_TAG_LENGTH
        if (sourceBin.length() != expectedTotalSize) {
            return false
        }
    }

    return try {
        BufferedInputStream(FileInputStream(sourceBin), 256 * 1024).use { bis ->
            BufferedOutputStream(FileOutputStream(tempFile), 256 * 1024).use { bos ->
                if (!sourceMeta.isMacAuthenticated) {
                    val buf = ByteArray(256 * 1024)
                    var read: Int
                    while (bis.read(buf).also { read = it } != -1) {
                        bos.write(buf, 0, read)
                    }
                    bos.flush()
                    return true
                }

                val headerLen = sourceMeta.payloadOffset.toInt()
                val headerBuf = ByteArray(headerLen)
                if (readExactOrEof(bis, headerBuf, headerLen) != headerLen) {
                    tempFile.delete()
                    return false
                }
                bos.write(headerBuf, 0, headerLen)

                val macKey = deriveSubkey(sourceMeta.fileKey, DOMAIN_CONTENT_MAC_KEY)
                val poly = Poly1305(AESEngine.newInstance())
                poly.init(ParametersWithIV(KeyParameter(macKey), sourceMeta.contentIv))
                macKey.fill(0)

                if (sourceMeta.version == VERSION_V5_AUTH) {
                    val headerIv = headerBuf.copyOfRange(9, 9 + GCM_IV_LENGTH)
                    val encHeaderLen = java.nio.ByteBuffer.wrap(headerBuf, 9 + GCM_IV_LENGTH, 4).int
                    val encHeader = headerBuf.copyOfRange(9 + GCM_IV_LENGTH + 4, 9 + GCM_IV_LENGTH + 4 + encHeaderLen)
                    poly.update(headerIv, 0, headerIv.size)
                    poly.update(encHeader, 0, encHeader.size)
                }

                val bufSize = minOf(maxOf(sourceMeta.originalSize, 1L), 256L * 1024L).toInt()
                val buf = ByteArray(bufSize)
                var remaining = sourceMeta.originalSize
                while (remaining > 0L) {
                    val toRead = minOf(remaining, bufSize.toLong()).toInt()
                    val read = bis.read(buf, 0, toRead)
                    if (read <= 0) {
                        tempFile.delete()
                        return false
                    }
                    bos.write(buf, 0, read)
                    poly.update(buf, 0, read)
                    remaining -= read.toLong()
                }

                val storedTag = ByteArray(MAC_TAG_LENGTH)
                if (readExactOrEof(bis, storedTag, MAC_TAG_LENGTH) != MAC_TAG_LENGTH) {
                    tempFile.delete()
                    return false
                }
                bos.write(storedTag, 0, MAC_TAG_LENGTH)
                bos.flush()

                val computedTag = ByteArray(MAC_TAG_LENGTH)
                poly.doFinal(computedTag, 0)
                if (!MessageDigest.isEqual(computedTag, storedTag)) {
                    tempFile.delete()
                    return false
                }
            }
        }
        true
    } catch (_: Exception) {
        tempFile.delete()
        false
    }
}


internal fun CryptoManager.collectSubdirBinFiles(rootSubdir: File): List<File> {
    val collected = ArrayList<File>()
    rootSubdir.walkTopDown()
        .onEnter { dir ->
            if (dir == rootSubdir) {
                true
            } else {
                !dir.name.equals("JSON", ignoreCase = true) &&
                    !isVaultRecycleBin(dir) &&
                    (!dir.name.startsWith(".") || hasFolderMetaFile(dir))
            }
        }
        .forEach { entry ->
            if (isEncryptedBinFile(entry)) {
                collected.add(entry)
            }
        }
    return collected
}


internal fun CryptoManager.findExistingDefaultHiddenAlbum(vaultRoot: File): File? {
    val subfolders = vaultRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: return null
    return subfolders.firstOrNull { isDefaultHiddenAlbum(it) }
}


internal fun CryptoManager.matchExistingDestinationAlbum(vaultRoot: File, albumName: String): File? {
    val subfolders = vaultRoot.listFiles()?.filter { dir ->
        dir.isDirectory &&
            !dir.name.startsWith(".") &&
            !dir.name.equals("JSON", ignoreCase = true) &&
            !isVaultRecycleBin(dir) &&
            !isDefaultHiddenAlbum(dir)
    } ?: return null

    subfolders.firstOrNull { getDecryptedRawFolderName(it) == albumName }?.let { return it }
    return subfolders.firstOrNull {
        getDecryptedRawFolderName(it)?.trim()?.equals(albumName, ignoreCase = true) == true
    }
}


internal fun CryptoManager.findExistingDestinationAlbum(vaultRoot: File, item: SyncPendingItem): File? {
    if (item.isDefaultTarget) {
        return findExistingDefaultHiddenAlbum(vaultRoot)
    }
    val name = item.sourceAlbumName?.takeIf { it.isNotEmpty() }
    return if (name == null) {
        findExistingDefaultHiddenAlbum(vaultRoot)
    } else {
        matchExistingDestinationAlbum(vaultRoot, name)
    }
}


fun CryptoManager.arePlainFilesIdentical(fileA: File, fileB: File, fastSampling: Boolean = false): Boolean {
    if (!fileA.isFile || !fileB.isFile) return false
    val canonA = runCatching { fileA.canonicalPath }.getOrDefault(fileA.absolutePath)
    val canonB = runCatching { fileB.canonicalPath }.getOrDefault(fileB.absolutePath)
    if (canonA == canonB) return true
    val size = fileA.length()
    if (size != fileB.length()) return false
    if (size == 0L) return true
    if (fastSampling && size >= FAST_SAMPLING_THRESHOLD_BYTES) {
        return try {
            RandomAccessFile(fileA, "r").use { rafA ->
                RandomAccessFile(fileB, "r").use { rafB ->
                    filesMatchFastSampled(
                        totalSize = size,
                        readChunkA = { offset, buf, len -> readPlainChunk(rafA, offset, buf, len) },
                        readChunkB = { offset, buf, len -> readPlainChunk(rafB, offset, buf, len) }
                    )
                }
            }
        } catch (_: Exception) {
            false
        }
    }
    return try {
        FileInputStream(fileA).use { inA ->
            FileInputStream(fileB).use { inB ->
                streamsHaveEqualBytes(inA, inB, size)
            }
        }
    } catch (_: Exception) {
        false
    }
}


fun CryptoManager.arePlainAndEncryptedIdentical(
    plainFile: File,
    encryptedFile: File,
    meta: EncryptedFileMetadata? = readMetadata(encryptedFile, includeThumbnail = false, cacheResult = false),
    verifyIntegrity: Boolean = true,
    fastSampling: Boolean = false
): Boolean {
    if (!plainFile.isFile || !encryptedFile.isFile) return false
    val resolvedMeta = meta ?: return false
    val size = plainFile.length()
    if (size != resolvedMeta.originalSize) return false
    if (size == 0L) {
        return !verifyIntegrity || !resolvedMeta.isMacAuthenticated || verifyFileIntegrity(encryptedFile, resolvedMeta)
    }
    val bytesEqual = if (fastSampling && size >= FAST_SAMPLING_THRESHOLD_BYTES) {
        try {
            RandomAccessFile(plainFile, "r").use { raf ->
                filesMatchFastSampled(
                    totalSize = size,
                    readChunkA = { offset, buf, len -> readPlainChunk(raf, offset, buf, len) },
                    readChunkB = { offset, buf, len -> readEncryptedChunk(encryptedFile, resolvedMeta, offset, buf, len) }
                )
            }
        } catch (_: Exception) {
            false
        }
    } else {
        try {
            FileInputStream(plainFile).use { plainIn ->
                openDecryptedContentStream(encryptedFile, 0L, resolvedMeta)?.use { decIn ->
                    streamsHaveEqualBytes(plainIn, decIn, size)
                } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }
    if (!bytesEqual) return false
    if (verifyIntegrity && resolvedMeta.isMacAuthenticated && !verifyFileIntegrity(encryptedFile, resolvedMeta)) {
        return false
    }
    return true
}


fun CryptoManager.getAlternativePlainFile(targetDir: File, originalName: String): File {
    var copyIndex = 1
    var candidate = File(targetDir, formatCollisionFileName(originalName, copyIndex))
    while (candidate.exists()) {
        copyIndex++
        candidate = File(targetDir, formatCollisionFileName(originalName, copyIndex))
    }
    return candidate
}


fun CryptoManager.findIdenticalPlainFileInDir(
    sourcePlainFile: File,
    targetDir: File,
    desiredName: String = sourcePlainFile.name,
    targetIndex: TargetPlainIndex? = null,
    includeCollisionVariants: Boolean = true,
    context: Context? = null
): File? {
    if (!sourcePlainFile.isFile) return null
    val srcLastMod = sourcePlainFile.lastModified()
    var srcDateTaken: Long? = null
    fun getSrcDateTaken(): Long {
        return srcDateTaken ?: resolvePlaintextDateTaken(context, sourcePlainFile, srcLastMod).also {
            srcDateTaken = it
        }
    }

    fun matchesPlain(candidate: File): Boolean {
        if (!candidate.isFile || candidate.length() != sourcePlainFile.length()) return false
        val candLastMod = candidate.lastModified()
        if (!areTimestampsEquivalent(srcLastMod, candLastMod)) return false
        val candTaken = resolvePlaintextDateTaken(context, candidate, candLastMod)
        if (!areMediaTimestampsEquivalent(srcLastMod, getSrcDateTaken(), candLastMod, candTaken)) {
            return false
        }
        return arePlainFilesIdentical(sourcePlainFile, candidate)
    }

    if (targetIndex != null) {
        val candidates = targetIndex.findCandidates(desiredName, includeCollisionVariants)
        return candidates.firstOrNull { matchesPlain(it) }
    }
    if (!targetDir.exists() || !targetDir.isDirectory) return null
    val candidates = if (includeCollisionVariants) {
        targetDir.listFiles()?.filter {
            it.isFile && !isEncryptedFile(it.name) && isSameOrCollisionVariant(it.name, desiredName)
        } ?: return null
    } else {
        val exact = File(targetDir, desiredName)
        if (exact.isFile && !isEncryptedFile(exact.name)) {
            listOf(exact)
        } else {
            targetDir.listFiles()?.filter {
                it.isFile && !isEncryptedFile(it.name) && it.name.equals(desiredName, ignoreCase = true)
            } ?: return null
        }
    }
    return candidates.firstOrNull { matchesPlain(it) }
}