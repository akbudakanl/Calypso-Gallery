package org.fossify.gallery.helpers

import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainer.ALGORITHM_PARAMS
import org.fossify.gallery.helpers.vault.VaultContainer.FILE_ID_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_MAGIC
import org.fossify.gallery.helpers.vault.VaultContainer.payloadOffset
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.deriveFileHeaderKey
import org.fossify.gallery.helpers.vault.openPayloadChannel
import org.fossify.gallery.helpers.vault.headerAad
import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Extracted from [CryptoManager]. Feature group: CryptoManagerStreams.
 */

/**
 * kanonik (Tink streaming AEAD) konteynerinin basligini okur.
 *
 * Duzen: magic(8) | version(1) | keyId(2) | fileId(16) | headerIv(12) | N(4) |
 *        encHeader(N) | Tink ciphertext(T)
 * Header GCM AAD'si cleartext onektir: magic | version | keyId | fileId | N.
 */
internal fun readContainerMetadata(
    dis: DataInputStream,
    kek: ByteArray,
    version: Byte,
    includeThumbnail: Boolean
): EncryptedFileMetadata? {
    val keyId = dis.readUnsignedShort().toShort()

    val fileId = ByteArray(FILE_ID_LENGTH)
    dis.readFully(fileId)
    if (fileId.all { it == 0.toByte() }) return null

    val headerIv = ByteArray(GCM_IV_LENGTH)
    dis.readFully(headerIv)

    val encHeaderLen = dis.readInt()
    if (encHeaderLen <= 0 || encHeaderLen > 5 * 1024 * 1024) return null
    val encHeader = ByteArray(encHeaderLen)
    dis.readFully(encHeader)

    val headerKey = deriveFileHeaderKey(kek, fileId)
    try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(headerKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, headerIv)
        )
        cipher.updateAAD(
            headerAad(
                VaultContainer.VAULT_MAGIC.toByteArray(Charsets.US_ASCII),
                version, keyId, fileId, encHeaderLen
            )
        )
        val plainHeader = cipher.doFinal(encHeader)

        DataInputStream(ByteArrayInputStream(plainHeader)).use { hdis ->
            if (hdis.readUnsignedShort() != ALGORITHM_PARAMS.toInt()) return null
            val segmentSize = hdis.readInt()
            if (!TinkStreamSpec.isSupportedSegmentSize(segmentSize)) return null

            val ciphertextLength = hdis.readLong()
            if (ciphertextLength <= 0L || ciphertextLength > Int.MAX_VALUE.toLong() * 8L) return null

            val dek = ByteArray(TinkStreamSpec.KEY_SIZE_BYTES)
            hdis.readFully(dek)

            val originalName = hdis.readUTF()
            val lastModified = hdis.readLong()
            val dateTaken = hdis.readLong()
            val originalSize = hdis.readLong()
            val mediaType = hdis.readInt()
            val videoDuration = hdis.readInt()

            // Tink akis uzunlugu tam olmali: truncation / eklenen bayt tespiti.
            if (ciphertextLength != TinkStreamSpec.expectedCiphertextSize(originalSize, segmentSize)) return null

            val thumbBytes = if (includeThumbnail && hdis.available() >= 4) {
                val thumbLen = hdis.readInt()
                if (thumbLen in 1..(5 * 1024 * 1024) && hdis.available() >= thumbLen) {
                    ByteArray(thumbLen).also { hdis.readFully(it) }
                } else null
            } else null

            return EncryptedFileMetadata(
                originalName = originalName,
                lastModified = lastModified,
                dateTaken = dateTaken,
                originalSize = originalSize,
                mediaType = mediaType,
                videoDuration = videoDuration,
                payloadOffset = payloadOffset(encHeaderLen),
                contentIv = ByteArray(0),
                fileKey = dek,
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
    } catch (_: Exception) {
        // Bozuk/yanlis AAD/anahtar: ust okuyucu da null dondurur; yardimci fonksiyon
        // kendi basina da guvenli olmali (direkt cagrilar ve testler icin).
        return null
    } finally {
        headerKey.fill(0)
    }
}


fun CryptoManager.readEmbeddedThumbnailBytes(file: File): ByteArray? {
    // Tek (kanonik) format: thumbnail kanonik header'in icinde gelir;
    // eski v1/v3/v5 header fallback'leri kaldirildi.
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

    // Kanonik format her zaman Tink streaming AEAD kullanir; CTR/legacy yollari kaldirildi.
    val fileId = meta.fileId ?: return null
    return try {
        val channel = openPayloadChannel(
            file = file,
            payloadOffset = meta.payloadOffset,
            ciphertextLength = meta.ciphertextLength,
            dek = meta.fileKey,
            fileId = fileId,
            segmentSize = meta.segmentSize
        )
        channel.position(startOffset)
        BoundedInputStream(
            java.nio.channels.Channels.newInputStream(channel),
            meta.originalSize - startOffset
        )
    } catch (e: Exception) {
        null
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
    return try {
        val channel = openPayloadChannel(
            file = file,
            payloadOffset = meta.payloadOffset,
            ciphertextLength = meta.ciphertextLength,
            dek = meta.fileKey,
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
    }
}
