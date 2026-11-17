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
 * Extracted from [CryptoManager]. Feature group: CryptoManagerTimestamps.
 */

fun CryptoManager.areTimestampsEquivalent(
    tsA: Long,
    tsB: Long,
    toleranceMs: Long = TIMESTAMP_EQUIVALENCE_TOLERANCE_MS
): Boolean {
    if (tsA <= 0L && tsB <= 0L) return true
    if (tsA <= 0L || tsB <= 0L) return false
    return kotlin.math.abs(tsA - tsB) <= toleranceMs
}


fun CryptoManager.areMediaTimestampsEquivalent(
    lastModifiedA: Long,
    dateTakenA: Long,
    lastModifiedB: Long,
    dateTakenB: Long,
    toleranceMs: Long = TIMESTAMP_EQUIVALENCE_TOLERANCE_MS
): Boolean {
    if (!areTimestampsEquivalent(lastModifiedA, lastModifiedB, toleranceMs)) {
        return false
    }
    if (dateTakenA > 0L && dateTakenB > 0L &&
        !areTimestampsEquivalent(dateTakenA, dateTakenB, toleranceMs)
    ) {
        return false
    }
    return true
}


fun CryptoManager.areEncryptedTimestampsEquivalent(
    metaA: EncryptedFileMetadata,
    metaB: EncryptedFileMetadata,
    toleranceMs: Long = TIMESTAMP_EQUIVALENCE_TOLERANCE_MS
): Boolean {
    return areMediaTimestampsEquivalent(
        metaA.lastModified,
        metaA.dateTaken,
        metaB.lastModified,
        metaB.dateTaken,
        toleranceMs
    )
}


internal fun CryptoManager.parseExifDateTimeMs(rawDate: String?): Long {
    val trimmed = rawDate?.trim()?.takeIf { it.length >= 19 } ?: return 0L
    if (trimmed.startsWith("0000")) return 0L
    val candidate = trimmed.substring(0, 19)
    val normalized = buildString(19) {
        for (i in candidate.indices) {
            val c = candidate[i]
            append(
                when {
                    i == 4 || i == 7 -> if (c == '-') ':' else c
                    i == 10 -> if (c == 'T' || c == 't') ' ' else c
                    else -> c
                }
            )
        }
    }
    return runCatching {
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
            isLenient = false
        }.parse(normalized)?.time ?: 0L
    }.getOrDefault(0L)
}


internal fun CryptoManager.parseVideoMetadataDateMs(rawDate: String?): Long {
    val trimmed = rawDate?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
    val compact = trimmed.replace(".000", "")
    val patterns = listOf(
        "yyyyMMdd'T'HHmmss'Z'" to true,
        "yyyyMMdd'T'HHmmss" to false,
        "yyyy-MM-dd'T'HH:mm:ss'Z'" to true
    )
    for ((pattern, useUtc) in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                if (useUtc) {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
            }.parse(compact)?.time ?: 0L
        }.getOrDefault(0L)
        if (parsed > 0L) return parsed
    }
    return parseExifDateTimeMs(trimmed)
}


internal fun CryptoManager.resolvePlaintextDateTaken(
    context: Context?,
    sourceFile: File,
    fallbackLastModified: Long = sourceFile.lastModified()
): Long {
    if (context != null) {
        runCatching {
            val dbTaken = context.dateTakensDB.getDateTakenFromPath(sourceFile.absolutePath)?.taken ?: 0L
            if (dbTaken > 0L) return dbTaken
        }
        runCatching {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            context.contentResolver?.query(
                uri,
                projection,
                selection,
                arrayOf(sourceFile.absolutePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    if (idx >= 0) {
                        val storeTaken = cursor.getLong(idx)
                        if (storeTaken > 0L) return storeTaken
                    }
                }
            }
        }
    }
    val mediaType = getMediaTypeForName(sourceFile.name)
    if (mediaType == TYPE_IMAGES) {
        runCatching {
            val exif = ExifInterface(sourceFile.absolutePath)
            val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val parsed = parseExifDateTimeMs(rawDate)
            if (parsed > 0L) return parsed
        }
    } else if (mediaType == TYPE_VIDEOS) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(sourceFile.absolutePath)
                val rawDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                val parsed = parseVideoMetadataDateMs(rawDate)
                if (parsed > 0L) return parsed
            } finally {
                retriever.release()
            }
        }
    }
    return if (fallbackLastModified > 0L) fallbackLastModified else System.currentTimeMillis()
}


internal fun CryptoManager.updateMediaStoreTimestamps(
    context: Context,
    filePath: String,
    dateTaken: Long,
    lastModified: Long
) {
    runCatching {
        val values = ContentValues().apply {
            if (dateTaken > 0L) {
                put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
            }
            if (lastModified > 0L) {
                put(MediaStore.MediaColumns.DATE_MODIFIED, lastModified / 1000L)
            }
        }
        if (values.size() > 0) {
            val uri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            context.contentResolver?.update(uri, values, selection, arrayOf(filePath))
        }
    }
}


internal fun CryptoManager.persistDecryptedDateTaken(
    context: Context,
    outFile: File,
    dateTaken: Long,
    lastModified: Long
) {
    val fullPath = outFile.absolutePath
    if (dateTaken > 0L) {
        runCatching {
            val row = DateTaken(
                id = null,
                fullPath = fullPath,
                filename = outFile.name,
                parentPath = outFile.parent ?: fullPath.getParentPath(),
                taken = dateTaken,
                lastFixed = (System.currentTimeMillis() / 1000L).toInt(),
                lastModified = if (lastModified > 0L) lastModified else outFile.lastModified()
            )
            context.dateTakensDB.deleteDateTaken(fullPath)
            context.dateTakensDB.insertAll(listOf(row))
        }
    }
    updateMediaStoreTimestamps(context, fullPath, dateTaken, lastModified)
}
