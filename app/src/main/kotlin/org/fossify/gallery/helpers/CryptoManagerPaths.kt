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
 * Extracted from [CryptoManager]. Feature group: CryptoManagerPaths.
 */

/**
 * Returns true when the media item belongs to the encrypted vault content
 * zone, i.e. it is stored as an encrypted file or it is located inside one
 * of the encrypted vault folders.
 */
fun CryptoManager.isVaultMediaPath(context: Context, path: String): Boolean {
    if (path.isEmpty()) return false
    return isEncryptedFile(path) || isEncryptedFolderPath(context, path.getParentPath())
}


fun CryptoManager.isEncryptedFolder(folder: File): Boolean {
    if (!folder.exists() || !folder.isDirectory) return false
    return File(folder, VAULT_SUBFOLDER_META_FILE).exists() ||
        folder.name.startsWith(ENCRYPTED_FOLDER_PREFIX)
}


fun CryptoManager.isEncryptedFolderPath(context: Context, folderPath: String): Boolean {
    val clean = folderPath.trimEnd('/')
    if (clean.isEmpty()) return false

    encryptedFolderCache[clean]?.let { return it }

    if (context.config.encryptedFolders.contains(clean)) {
        encryptedFolderCache[clean] = true
        return true
    }

    val vaultRoot = findVaultRoot(context)
    if (vaultRoot != null) {
        val rootPath = vaultRoot.absolutePath.trimEnd('/')
        if (clean == rootPath || clean.startsWith("$rootPath/")) {
            encryptedFolderCache[clean] = true
            return true
        }
    }

    val folder = File(clean)
    val isEnc = isEncryptedFolder(folder)
    encryptedFolderCache[clean] = isEnc
    return isEnc
}


fun CryptoManager.getDocumentsDirectory(): File {
    return try {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    } catch (_: Throwable) {
        null
    } ?: try {
        File(Environment.getExternalStorageDirectory(), "Documents")
    } catch (_: Throwable) {
        File("/storage/emulated/0/Documents")
    }
}


/**
 * The root of the shared storage (the internal storage of the device). The app container is a
 * standalone top level folder in here, it is not nested in Documents.
 */
fun CryptoManager.getStorageRootDirectory(): File {
    return try {
        Environment.getExternalStorageDirectory()
    } catch (_: Throwable) {
        null
    } ?: File("/storage/emulated/0")
}


/**
 * The container of this app: `<internal storage>/Calypso Gallery`. It holds the vault,
 * the saved virtual albums and the backups of the favorites. It is a standalone top level
 * folder, it is not nested in the Documents folder.
 */
fun CryptoManager.getCipherRootDirectory(): File {
    return File(getStorageRootDirectory(), CIPHER_APP_DIR_NAME)
}


/**
 * Creates the container of the app in the internal storage, with a .nomedia file so it is
 * never listed as a media folder by the system or by other gallery apps.
 */
fun CryptoManager.ensureCipherRootDirectory(): File {
    val dir = getCipherRootDirectory()
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val noMedia = File(dir, NOMEDIA)
    if (!noMedia.exists()) {
        try { noMedia.createNewFile() } catch (_: Exception) {}
    }
    return dir
}


fun CryptoManager.getDefaultVaultRoot(): File {
    return File(getCipherRootDirectory(), SECRETS_DIR_NAME)
}
