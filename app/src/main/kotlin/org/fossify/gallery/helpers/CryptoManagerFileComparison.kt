package org.fossify.gallery.helpers

import android.content.Context
import java.io.*
import org.fossify.gallery.helpers.CryptoManager.TargetPlainIndex
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
    // Ciphertext size is fully determined by the plaintext size and
    // the segment size recorded in the encrypted header. A container whose length does
    // not match its header is truncated/padded and cannot be a valid duplicate.
    if (metaA.isCanonicalFormat && metaB.isCanonicalFormat) {
        if (metaA.ciphertextLength != metaB.ciphertextLength) return false
        if (fileA.length() != metaA.payloadOffset + metaA.ciphertextLength) return false
        if (fileB.length() != metaB.payloadOffset + metaB.ciphertextLength) return false
    }
    return streamsMatchBitForBit(fileA, fileB, metaA.originalSize, metaA, metaB, fastSampling = fastSampling)
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
    // payload length is fixed by the header; anything else is a corrupt container.
    if (resolvedMeta.isCanonicalFormat && encryptedFile.length() != resolvedMeta.payloadOffset + resolvedMeta.ciphertextLength) {
        return false
    }
    if (size == 0L) {
        return !verifyIntegrity || verifyFileIntegrity(encryptedFile, resolvedMeta)
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
    // Tink's streaming AEAD authenticates on authenticated EOF, so a full read verifies
    // the whole payload without any separate MAC trailer.
    if (verifyIntegrity && !verifyFileIntegrity(encryptedFile, resolvedMeta)) {
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
        val gateByDate = context?.shouldGateDuplicatesByDate() != false
        val candLastMod = candidate.lastModified()
        if (gateByDate && !areTimestampsEquivalent(srcLastMod, candLastMod)) return false
        val candTaken = resolvePlaintextDateTaken(context, candidate, candLastMod)
        if (gateByDate && !areMediaTimestampsEquivalent(srcLastMod, getSrcDateTaken(), candLastMod, candTaken)) {
            return false
        }
        val identical = arePlainFilesIdentical(sourcePlainFile, candidate)
        if (identical && context != null) {
            applyDuplicateDateToPlainFile(context, candidate, srcLastMod, getSrcDateTaken())
        }
        return identical
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
