package org.fossify.gallery.helpers

import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainer.ALGORITHM_PARAMS
import org.fossify.gallery.helpers.vault.VaultContainer.FILE_ID_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.payloadOffset
import org.fossify.gallery.helpers.vault.openPayloadChannel
import java.io.*

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerStreams.
 */

private const val MAX_HEADER_BYTES = 5 * 1024 * 1024
private const val STREAM_BUFFER_BYTES = 8192
private const val MAX_CIPHERTEXT_SCALE = 8L

private data class ContainerHeaderEnvelope(
    val version: Byte,
    val keyId: Short,
    val fileId: ByteArray,
    val headerIv: ByteArray,
    val encHeaderLen: Int,
    val encHeader: ByteArray
)

private fun readHeaderEnvelopeFromStream(dis: DataInputStream): ContainerHeaderEnvelope? {
    val magicBytes = ByteArray(VaultContainer.VAULT_MAGIC.length)
    dis.readFully(magicBytes)
    if (String(magicBytes, Charsets.US_ASCII) != VaultContainer.VAULT_MAGIC) return null

    val version = dis.readByte()
    if (version != VaultContainer.VAULT_FORMAT_VERSION) return null

    val keyId = dis.readUnsignedShort().toShort()
    val fileId = ByteArray(FILE_ID_LENGTH).also { dis.readFully(it) }
    val headerIv = ByteArray(GCM_IV_LENGTH).also { dis.readFully(it) }
    val encHeaderLen = dis.readInt()
    val valid = !fileId.all { it == 0.toByte() } && encHeaderLen in 1..MAX_HEADER_BYTES
    return if (valid) {
        val encHeader = ByteArray(encHeaderLen).also { dis.readFully(it) }
        ContainerHeaderEnvelope(version, keyId, fileId, headerIv, encHeaderLen, encHeader)
    } else {
        null
    }
}

private fun parseStrippedHeaderMetadata(
    strippedHeader: ByteArray,
    encHeaderLen: Int,
    fileId: ByteArray,
    keyId: Short,
    version: Byte,
    includeThumbnail: Boolean
): EncryptedFileMetadata? = DataInputStream(ByteArrayInputStream(strippedHeader)).use { hdis ->
    val algoParams = hdis.readUnsignedShort()
    val segmentSize = hdis.readInt()
    val ciphertextLength = hdis.readLong()
    val validPrefix = algoParams == ALGORITHM_PARAMS.toInt() &&
        TinkStreamSpec.isSupportedSegmentSize(segmentSize) &&
        ciphertextLength in 1L..(Int.MAX_VALUE.toLong() * MAX_CIPHERTEXT_SCALE)
    if (!validPrefix) return null

    val originalName = hdis.readUTF()
    val lastModified = hdis.readLong()
    val dateTaken = hdis.readLong()
    val originalSize = hdis.readLong()
    val mediaType = hdis.readInt()
    val videoDuration = hdis.readInt()

    // Tink akis uzunlugu tam olmali: truncation / eklenen bayt tespiti.
    if (ciphertextLength != TinkStreamSpec.expectedCiphertextSize(originalSize, segmentSize)) return null

    val thumbBytes = if (includeThumbnail && hdis.available() >= Int.SIZE_BYTES) {
        val thumbLen = hdis.readInt()
        if (thumbLen in 1..MAX_HEADER_BYTES && hdis.available() >= thumbLen) {
            ByteArray(thumbLen).also { hdis.readFully(it) }
        } else null
    } else null

    EncryptedFileMetadata(
        originalName = originalName,
        lastModified = lastModified,
        dateTaken = dateTaken,
        originalSize = originalSize,
        mediaType = mediaType,
        videoDuration = videoDuration,
        payloadOffset = payloadOffset(encHeaderLen),
        contentIv = ByteArray(0),
        version = version,
        isMacAuthenticated = true,
        thumbnailBytes = thumbBytes,
        fileId = fileId,
        keyId = keyId,
        ciphertextLength = ciphertextLength,
        isCanonicalFormat = true,
        segmentSize = segmentSize
    )
}

/**
 * kanonik (Tink streaming AEAD) konteynerinin basligini Rust uzerinden okur.
 * DEK Rust icinde soyulup sifirlanir; JVM tarafina yalnizca gizli olmayan metadata doner.
 *
 * Duzen: magic(8) | version(1) | keyId(2) | fileId(16) | headerIv(12) | N(4) |
 *        encHeader(N) | Tink ciphertext(T)
 */
internal fun readContainerMetadata(
    dis: DataInputStream,
    version: Byte,
    includeThumbnail: Boolean
): EncryptedFileMetadata? {
    val keyId = dis.readUnsignedShort().toShort()
    val fileId = ByteArray(FILE_ID_LENGTH).also { dis.readFully(it) }
    val headerIv = ByteArray(GCM_IV_LENGTH).also { dis.readFully(it) }
    val encHeaderLen = dis.readInt()
    val validEnvelope = !fileId.all { it == 0.toByte() } && encHeaderLen in 1..MAX_HEADER_BYTES
    if (!validEnvelope) return null

    val encHeader = ByteArray(encHeaderLen).also { dis.readFully(it) }
    val strippedHeader = NativeVaultSession.decryptFileHeaderMetadataNative(
        version = version.toInt(),
        keyId = keyId.toInt(),
        fileId = fileId,
        headerIv = headerIv,
        encHeader = encHeader
    ) ?: return null

    return try {
        parseStrippedHeaderMetadata(strippedHeader, encHeaderLen, fileId, keyId, version, includeThumbnail)
    } catch (_: Exception) {
        null
    }
}

private fun unwrapFileDekFromContainer(file: File): ByteArray? {
    if (!CryptoManager.isVaultUnlocked || !file.exists() || !file.isFile) return null
    return try {
        val env = DataInputStream(BufferedInputStream(FileInputStream(file), STREAM_BUFFER_BYTES)).use { dis ->
            readHeaderEnvelopeFromStream(dis)
        } ?: return null
        NativeVaultSession.unwrapFileDekNative(
            version = env.version.toInt(),
            keyId = env.keyId.toInt(),
            fileId = env.fileId,
            headerIv = env.headerIv,
            encHeader = env.encHeader
        )
    } catch (_: Exception) {
        null
    }
}


fun CryptoManager.readEmbeddedThumbnailBytes(file: File): ByteArray? {
    // Thumbnail kanonik header'in icinde gelir.
    val meta = readMetadata(file) ?: return null
    return meta.thumbnailBytes?.takeIf { it.isNotEmpty() }
}


fun CryptoManager.openDecryptedContentStream(
    file: File,
    startOffset: Long = 0L,
    preloadedMeta: EncryptedFileMetadata? = null
): InputStream? {
    val meta = preloadedMeta ?: readMetadata(file) ?: return null
    if (startOffset < 0L || startOffset >= meta.originalSize) {
        return ByteArrayInputStream(ByteArray(0))
    }

    // Kanonik format her zaman Tink streaming AEAD kullanir; DEK yalnizca bu stream icin cozulur.
    val fileId = meta.fileId ?: return null
    val dek = unwrapFileDekFromContainer(file) ?: return null
    return try {
        val channel = openPayloadChannel(
            file = file,
            payloadOffset = meta.payloadOffset,
            ciphertextLength = meta.ciphertextLength,
            dek = dek,
            fileId = fileId,
            segmentSize = meta.segmentSize
        )
        channel.position(startOffset)
        BoundedInputStream(
            java.nio.channels.Channels.newInputStream(channel),
            meta.originalSize - startOffset
        )
    } catch (_: Exception) {
        null
    } finally {
        dek.fill(0)
    }
}


/**
 * Verifies cryptographic integrity of the encrypted payload using the canonical
 * Tink streaming AEAD authentication. The whole stream is read so that the final
 * segment's authentication tag is verified (authenticated EOF).
 */
fun CryptoManager.verifyFileIntegrity(
    file: File,
    preloadedMeta: EncryptedFileMetadata? = null
): Boolean {
    val meta = preloadedMeta ?: readMetadata(file, includeThumbnail = false, cacheResult = false) ?: return false

    val fileId = meta.fileId ?: return false
    if (file.length() != meta.payloadOffset + meta.ciphertextLength) return false
    val dek = unwrapFileDekFromContainer(file) ?: return false
    return try {
        val channel = openPayloadChannel(
            file = file,
            payloadOffset = meta.payloadOffset,
            ciphertextLength = meta.ciphertextLength,
            dek = dek,
            fileId = fileId,
            segmentSize = meta.segmentSize
        )
        channel.use { ch ->
            val buf = java.nio.ByteBuffer.allocate(256 * 1024)
            while (ch.read(buf) != -1) {
                buf.clear()
            }
            // -1 donusu son segmentin tag'ini dogrular (authenticated EOF).
        }
        true
    } catch (_: Exception) {
        false
    } finally {
        dek.fill(0)
    }
}
