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
 * Extracted from [CryptoManager]. Feature group: CryptoManagerStreams.
 */

fun CryptoManager.readEmbeddedThumbnailBytes(file: File): ByteArray? {
    val meta = readMetadata(file) ?: return null
    if (meta.thumbnailBytes != null && meta.thumbnailBytes.isNotEmpty()) {
        return meta.thumbnailBytes
    }
    if (!file.exists() || !file.isFile) return null

    return try {
        FileInputStream(file).use { fis ->
            DataInputStream(BufferedInputStream(fis, 8192)).use { dis ->
                val magicBytes = ByteArray(8)
                dis.readFully(magicBytes)
                val magicStr = String(magicBytes, Charsets.US_ASCII)
                if (magicStr != MAGIC_V2 && magicStr != MAGIC_V1) return null

                val version = dis.readByte()
                if (version != VERSION_SYMMETRIC && version != VERSION_SYMMETRIC_AUTH && version != VERSION_V5_AUTH) {
                    return null
                }

                val headerIv = ByteArray(GCM_IV_LENGTH)
                dis.readFully(headerIv)

                val encHeaderLen = dis.readInt()
                if (encHeaderLen <= 0 || encHeaderLen > 5 * 1024 * 1024) return null

                val encHeader = ByteArray(encHeaderLen)
                dis.readFully(encHeader)

                val isMacAuth = (version == VERSION_SYMMETRIC_AUTH || version == VERSION_V5_AUTH)
                val headerKey = if (isMacAuth) {
                    deriveSubkey(meta.fileKey, DOMAIN_HEADER_KEY)
                } else {
                    meta.fileKey
                }

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(headerKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, headerIv))
                val plainHeader = cipher.doFinal(encHeader)
                if (isMacAuth) {
                    headerKey.fill(0)
                }

                DataInputStream(ByteArrayInputStream(plainHeader)).use { hdis ->
                    hdis.readUTF() // originalName
                    hdis.readLong() // lastModified
                    hdis.readLong() // dateTaken
                    hdis.readLong() // originalSize
                    hdis.readInt() // mediaType
                    hdis.readInt() // videoDuration
                    val thumbLen = hdis.readInt()
                    if (thumbLen <= 0) return null
                    val thumbBytes = ByteArray(thumbLen)
                    hdis.readFully(thumbBytes)
                    thumbBytes
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}


fun CryptoManager.incrementCounter(baseIv: ByteArray, blockOffset: Long): ByteArray {
    if (blockOffset == 0L) return baseIv.copyOf()
    val counterInt = BigInteger(1, baseIv).add(BigInteger.valueOf(blockOffset))
    val bytes = counterInt.toByteArray()
    val result = ByteArray(CTR_IV_LENGTH)
    val copyLen = minOf(bytes.size, CTR_IV_LENGTH)
    System.arraycopy(bytes, bytes.size - copyLen, result, CTR_IV_LENGTH - copyLen, copyLen)
    return result
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

    return try {
        val blockIndex = startOffset / CTR_IV_LENGTH
        val byteSkipInBlock = (startOffset % CTR_IV_LENGTH).toInt()
        val fileSeekPosition = meta.payloadOffset + (blockIndex * CTR_IV_LENGTH)

        val fis = FileInputStream(file)
        try {
            fis.channel.position(fileSeekPosition)
        } catch (e: Exception) {
            fis.close()
            return null
        }

        val adjustedIv = incrementCounter(meta.contentIv, blockIndex)
        val encKey = if (meta.isMacAuthenticated) {
            deriveSubkey(meta.fileKey, DOMAIN_CONTENT_ENC_KEY)
        } else {
            meta.fileKey
        }

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(adjustedIv))
        if (meta.isMacAuthenticated) {
            encKey.fill(0)
        }

        val cis = CipherInputStream(BufferedInputStream(fis, 64 * 1024), cipher)
        var remainingSkip = byteSkipInBlock
        while (remainingSkip > 0) {
            val skipped = cis.skip(remainingSkip.toLong()).toInt()
            if (skipped <= 0) {
                if (cis.read() == -1) break
                remainingSkip--
            } else {
                remainingSkip -= skipped
            }
        }

        val remainingContentBytes = meta.originalSize - startOffset
        BoundedInputStream(cis, remainingContentBytes)
    } catch (e: Exception) {
        null
    }
}


/**
 * Verifies cryptographic integrity of the encrypted payload using Poly1305-AES MAC.
 * Guarantees tamper-resistance, authentic data origin, and bit-rot detection.
 */
fun CryptoManager.verifyFileIntegrity(
    file: File,
    preloadedMeta: EncryptedFileMetadata? = null
): Boolean {
    val meta = preloadedMeta ?: readMetadata(file, includeThumbnail = false, cacheResult = false) ?: return false
    if (!meta.isMacAuthenticated) {
        // Legacy v1/v2 payload (authenticated via GCM header only)
        return true
    }

    val expectedTotalSize = meta.payloadOffset + meta.originalSize + MAC_TAG_LENGTH
    if (file.length() != expectedTotalSize) {
        return false
    }

    val macKey = deriveSubkey(meta.fileKey, DOMAIN_CONTENT_MAC_KEY)
    val poly = Poly1305(AESEngine.newInstance())
    poly.init(ParametersWithIV(KeyParameter(macKey), meta.contentIv))
    macKey.fill(0)

    val storedTag = ByteArray(MAC_TAG_LENGTH)
    return try {
        RandomAccessFile(file, "r").use { raf ->
            if (meta.version == VERSION_V5_AUTH) {
                raf.seek(8L + 1L)
                val headerIv = ByteArray(GCM_IV_LENGTH)
                raf.readFully(headerIv)
                val encHeaderLen = raf.readInt()
                val encHeader = ByteArray(encHeaderLen)
                raf.readFully(encHeader)
                poly.update(headerIv, 0, headerIv.size)
                poly.update(encHeader, 0, encHeader.size)
            }
            raf.seek(meta.payloadOffset)
            val buffer = ByteArray(256 * 1024)
            var remaining = meta.originalSize
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) return false
                poly.update(buffer, 0, read)
                remaining -= read
            }
            raf.readFully(storedTag)
        }
        val computedTag = ByteArray(MAC_TAG_LENGTH)
        poly.doFinal(computedTag, 0)
        MessageDigest.isEqual(computedTag, storedTag)
    } catch (_: Exception) {
        false
    }
}

