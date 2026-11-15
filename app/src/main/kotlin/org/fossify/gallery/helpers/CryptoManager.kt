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
import android.util.Base64
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

class BoundedInputStream(
    private val wrapped: InputStream,
    private var remaining: Long
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        val b = wrapped.read()
        if (b != -1) remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val toRead = minOf(len.toLong(), remaining).toInt()
        val read = wrapped.read(b, off, toRead)
        if (read > 0) remaining -= read
        return read
    }

    override fun skip(n: Long): Long {
        if (n <= 0 || remaining <= 0) return 0L
        var totalSkipped = 0L
        var left = minOf(n, remaining)
        val skipBuffer = ByteArray(minOf(left, 4096L).toInt())
        while (left > 0) {
            val skipped = wrapped.skip(left)
            if (skipped > 0) {
                totalSkipped += skipped
                remaining -= skipped
                left -= skipped
            } else {
                val read = wrapped.read(skipBuffer, 0, minOf(left, skipBuffer.size.toLong()).toInt())
                if (read == -1) break
                totalSkipped += read
                remaining -= read
                left -= read
            }
        }
        return totalSkipped
    }

    override fun available(): Int {
        return minOf(wrapped.available().toLong(), remaining).toInt()
    }

    override fun close() {
        wrapped.close()
    }
}

data class EncryptedFileMetadata(
    val originalName: String,
    val lastModified: Long,
    val dateTaken: Long,
    val originalSize: Long,
    val mediaType: Int,
    val videoDuration: Int,
    val payloadOffset: Long,
    val contentIv: ByteArray,
    val fileKey: ByteArray,
    val version: Byte = 1,
    val isMacAuthenticated: Boolean = false,
    val thumbnailBytes: ByteArray? = null
)

data class VaultPhysicalAlbum(
    val name: String,
    val fullPath: String,
    val folder: File,
    val itemCount: Int
)

data class VaultSyncResult(
    val copiedCount: Int,
    val renamedCount: Int,
    val skippedDuplicateCount: Int,
    val failedCount: Int,
    val syncedAlbumsCount: Int
)

object CryptoManager {
    private const val MAGIC_V1 = "FVLTENC1"
    private const val MAGIC_V2 = "FGVLTENC"
    private const val VERSION_SYMMETRIC: Byte = 1
    private const val VERSION_SYMMETRIC_AUTH: Byte = 3
    private const val VERSION_V5_AUTH: Byte = 5
    private const val META_MAGIC_SYM = "FVLTMETA"
    private const val META_MAGIC_V2 = "FGMETA2"
    private const val VAULT_HEADER_MAGIC = "FGVLT2"
    private const val VAULT_STR_PREFIX = "FVSTR1:"
    const val VAULT_RECYCLE_BIN_NAME = "\$\$VAULT_RECYCLE_BIN\$\$"
    const val VAULT_RESTORE_INFO_FILE = ".restore_info"
    const val TIMESTAMP_EQUIVALENCE_TOLERANCE_MS = 2_000L

    fun areTimestampsEquivalent(
        tsA: Long,
        tsB: Long,
        toleranceMs: Long = TIMESTAMP_EQUIVALENCE_TOLERANCE_MS
    ): Boolean {
        if (tsA <= 0L && tsB <= 0L) return true
        if (tsA <= 0L || tsB <= 0L) return false
        return kotlin.math.abs(tsA - tsB) <= toleranceMs
    }

    fun areMediaTimestampsEquivalent(
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

    fun areEncryptedTimestampsEquivalent(
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

    internal fun parseExifDateTimeMs(rawDate: String?): Long {
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

    private fun parseVideoMetadataDateMs(rawDate: String?): Long {
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

    internal fun resolvePlaintextDateTaken(
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

    internal fun updateMediaStoreTimestamps(
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

    internal fun persistDecryptedDateTaken(
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

    // Cryptographic Key Separation Domains & MAC Specifications
    private const val DOMAIN_HEADER_KEY = "FossifyGallery:Header:AES256GCM"
    private const val DOMAIN_CONTENT_ENC_KEY = "FossifyGallery:Payload:AES256CTR"
    private const val DOMAIN_CONTENT_MAC_KEY = "FossifyGallery:Payload:Poly1305"
    private const val MAC_TAG_LENGTH = 16

    private fun deriveSubkey(key: ByteArray, domain: String): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(domain.toByteArray(Charsets.UTF_8))
    }

    // Argon2id Parameters (Memory-Hard Key Derivation)
    private const val ARGON2_MEMORY_KB = 65536 // 64 MB RAM cost
    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_PARALLELISM = 2
    private const val ARGON2_SALT_LENGTH = 16

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val CTR_IV_LENGTH = 16
    private const val THUMB_MAX_DIMENSION = 320
    private const val THUMB_JPEG_QUALITY = 75

    private val secureRandom = SecureRandom()

    @Volatile
    private var unlockedMasterKey: ByteArray? = null

    private val metadataCache = ConcurrentHashMap<String, EncryptedFileMetadata>()
    private val folderNameCache = ConcurrentHashMap<String, String>()
    private val encryptedFolderCache = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var cachedVaultRoot: File? = null
    @Volatile
    var vaultRecycleBinFolder: File? = null

    private val activeCryptoOperations = AtomicInteger(0)
    private val pendingLock = AtomicBoolean(false)

    fun isOperationInProgress(): Boolean = activeCryptoOperations.get() > 0

    fun <T> runCryptoOperation(block: () -> T): T {
        activeCryptoOperations.incrementAndGet()
        try {
            return block()
        } finally {
            if (activeCryptoOperations.decrementAndGet() == 0 && pendingLock.getAndSet(false)) {
                lockVaultInternal()
            }
        }
    }

    val isVaultUnlocked: Boolean
        get() = unlockedMasterKey != null

    fun getMasterKeyCopy(): ByteArray? = unlockedMasterKey?.copyOf()

    fun invalidateFolderCache() {
        encryptedFolderCache.clear()
    }

    fun lockVault() {
        if (isOperationInProgress()) {
            pendingLock.set(true)
            return
        }
        lockVaultInternal()
    }

    private fun lockVaultInternal() {
        unlockedMasterKey?.fill(0)
        unlockedMasterKey = null
        metadataCache.values.forEach { it.fileKey.fill(0) }
        metadataCache.clear()
        folderNameCache.clear()
        encryptedFolderCache.clear()
        vaultRecycleBinFolder = null
        try {
            VideoFragment.clearFrameCache()
        } catch (_: Throwable) {}
    }

    fun isEncryptedFile(path: String): Boolean {
        return path.endsWith(ENCRYPTED_BIN_EXT, ignoreCase = true) ||
            path.endsWith(ENCRYPTED_FILE_EXT, ignoreCase = true)
    }

    /**
     * Returns true when the media item belongs to the encrypted vault content
     * zone, i.e. it is stored as an encrypted file or it is located inside one
     * of the encrypted (Cipher_Secrets) folders.
     */
    fun isVaultMediaPath(context: Context, path: String): Boolean {
        if (path.isEmpty()) return false
        return isEncryptedFile(path) || isEncryptedFolderPath(context, path.getParentPath())
    }

    fun isEncryptedFolder(folder: File): Boolean {
        if (!folder.exists() || !folder.isDirectory) return false
        return File(folder, VAULT_SUBFOLDER_META_FILE).exists() ||
            File(folder, ENCRYPTED_FOLDER_META_FILE).exists() ||
            folder.name.startsWith(ENCRYPTED_FOLDER_PREFIX)
    }

    fun isEncryptedFolderPath(context: Context, folderPath: String): Boolean {
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

    fun getDocumentsDirectory(): File {
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
     * The root of the shared storage (the internal storage of the device). The Cipher folder is a
     * standalone top level folder in here, it is not nested in Documents.
     */
    fun getStorageRootDirectory(): File {
        return try {
            Environment.getExternalStorageDirectory()
        } catch (_: Throwable) {
            null
        } ?: File("/storage/emulated/0")
    }

    /**
     * The container of this app: `<internal storage>/Fossify Gallery Cipher`. It holds the vault,
     * the saved virtual albums and the backups of the favorites. It is a standalone top level
     * folder, it is not nested in the Documents folder.
     */
    fun getCipherRootDirectory(): File {
        return File(getStorageRootDirectory(), CIPHER_APP_DIR_NAME)
    }

    /**
     * Creates the container of the app in the internal storage, with a .nomedia file so it is
     * never listed as a media folder by the system or by other gallery apps.
     */
    fun ensureCipherRootDirectory(): File {
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

    fun getDefaultVaultRoot(): File {
        return File(getCipherRootDirectory(), CIPHER_SECRETS_DIR_NAME)
    }

    // ==================== VAULT EXISTENCE: THE .vault HEADER ON DISK IS THE SINGLE SOURCE OF TRUTH ====================

    private const val DEEP_SCAN_INTERVAL_MS = 5_000L

    @Volatile
    private var lastDeepScanMs = 0L

    /** True while setupOrUpdateCredentials() runs, so a concurrent check never wipes half-written state. */
    @Volatile
    private var vaultSetupInProgress = false

    /**
     * The file system can only be inspected when the app may read shared storage. Without that
     * permission "not found" would be meaningless, so nothing may be concluded (or wiped) from it.
     */
    private fun canAccessSharedStorage(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isValidVaultHeader(headerFile: File): Boolean {
        if (!headerFile.isFile || headerFile.length() <= VAULT_HEADER_MAGIC.length) return false
        return try {
            FileInputStream(headerFile).use { fis ->
                val magic = ByteArray(VAULT_HEADER_MAGIC.length)
                DataInputStream(fis).readFully(magic)
                String(magic, Charsets.US_ASCII) == VAULT_HEADER_MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasValidVaultHeader(dir: File?): Boolean {
        return dir != null && dir.isDirectory && isValidVaultHeader(File(dir, VAULT_HEADER_FILE))
    }

    /**
     * Vaults created by older versions have no .vault file: their wrapped key only lives in
     * SharedPreferences. Such a vault is recognised ONLY when that key material is still stored AND
     * the folder physically holds encrypted content. An empty folder, or one that only contains
     * .nomedia / JSON stubs, is never a vault.
     */
    private fun isLegacyVaultRoot(context: Context, dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        if (context.config.vaultWrappedKeyData.isEmpty()) return false
        val children = dir.listFiles() ?: return false
        return children.any { child ->
            (child.isFile && isEncryptedFile(child.name)) || (child.isDirectory && isEncryptedFolder(child))
        }
    }

    @VisibleForTesting
    internal var otgMountBaseOverrideForTest: List<File>? = null

    private fun normalizeVaultPath(path: String): String {
        return path.trim().trimEnd('/', '\\')
    }

    private fun isInternalStoragePath(path: String): Boolean {
        val clean = normalizeVaultPath(path)
        if (clean.isEmpty()) return false
        val internalRoot = normalizeVaultPath(getStorageRootDirectory().absolutePath)
        return clean.equals(internalRoot, ignoreCase = true) ||
            clean.startsWith("$internalRoot/", ignoreCase = true)
    }

    fun getVaultRoot(context: Context): File? = findVaultRoot(context)

    fun canWriteToDirectory(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory || !dir.canWrite()) return false
        return try {
            val probe = File(dir, ".fv_write_test_${System.nanoTime()}")
            val created = probe.createNewFile()
            if (created) {
                probe.delete()
                true
            } else {
                probe.exists()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveUriViaProcFd(context: Context, uri: Uri): File? {
        return runCatching {
            val resolvedPath = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                Os.readlink("/proc/self/fd/${pfd.fd}")
            }
            val file = resolvedPath
                ?.takeIf { it.startsWith("/") && !it.startsWith("/proc/") }
                ?.let { File(it) }
            if (file != null && file.exists() && file.isDirectory) {
                file
            } else {
                null
            }
        }.getOrNull()
    }

    private fun resolveVolumeMountPath(
        context: Context,
        partition: String,
        relativeSubpath: String
    ): File? {
        val rel = relativeSubpath.trim().replace('\\', '/').trim('/')
        val roots = ArrayList<File>()
        if (partition.isNotEmpty()) {
            otgMountBaseOverrideForTest?.forEach { base ->
                roots.add(File(base, partition))
            }
            roots.add(File("/storage/$partition"))
            roots.add(File("/mnt/media_rw/$partition"))
            roots.add(File("/mnt/runtime/write/$partition"))
            roots.add(File("/mnt/runtime/default/$partition"))
        }
        roots.addAll(listExternalStorageVolumeRoots(context))

        for (volRoot in roots.distinctBy { it.absolutePath }) {
            val candidate = if (rel.isEmpty()) volRoot else File(volRoot, rel)
            if (candidate.exists() && candidate.isDirectory) {
                return candidate
            }
        }
        return null
    }

    /**
     * Resolves a folder path returned by Fossify's [FilePickerDialog] to a real POSIX directory
     * [File]. Direct POSIX paths (including mounted `/storage/XXXX-XXXX/...` paths that Fossify's
     * `isPathOnOTG()` may flag when `config.OTGPath` shares the prefix) are returned directly;
     * `otg:` pseudo-paths and `content://` tree URIs are mapped to their POSIX mount when
     * available, or return `null` if the device only exposes virtual SAF access.
     */
    fun resolvePickedPathToFile(context: Context, pickedPath: String): File? {
        val trimmed = pickedPath.trim()
        if (trimmed.isEmpty()) return null

        val otgConfigPath = try {
            context.config.OTGPath.trim()
        } catch (_: Throwable) {
            ""
        }

        val isOtgPseudoPath = trimmed.startsWith("otg:", ignoreCase = true) ||
            (otgConfigPath.startsWith("otg:", ignoreCase = true) && trimmed.startsWith(otgConfigPath, ignoreCase = true))

        if (isOtgPseudoPath) {
            val partition = try {
                context.config.OTGPartition.trim()
            } catch (_: Throwable) {
                ""
            }
            val relCandidates = ArrayList<String>(2)
            if (otgConfigPath.startsWith("otg:", ignoreCase = true) && trimmed.startsWith(otgConfigPath, ignoreCase = true)) {
                relCandidates.add(trimmed.substring(otgConfigPath.length).trim('/'))
            }
            if (trimmed.startsWith("otg:", ignoreCase = true)) {
                relCandidates.add(trimmed.substring(4).trim('/'))
            }

            for (rel in relCandidates.distinct()) {
                resolveVolumeMountPath(context, partition, rel)?.let { return it }
            }

            // Fallback via SAF DocumentFile -> /proc/self/fd readlink
            runCatching {
                val docFile = context.getOTGFastDocumentFile(trimmed)
                if (docFile != null) {
                    resolveUriViaProcFd(context, docFile.uri)?.let { return it }
                    val childFile = docFile.listFiles().firstOrNull { it.isFile }
                    if (childFile != null) {
                        val resolvedChild = context.contentResolver.openFileDescriptor(childFile.uri, "r")?.use { pfd ->
                            Os.readlink("/proc/self/fd/${pfd.fd}")
                        }
                        val parent = resolvedChild
                            ?.takeIf { it.startsWith("/") && !it.startsWith("/proc/") }
                            ?.let { File(it).parentFile }
                        if (parent != null && parent.exists() && parent.isDirectory) {
                            return parent
                        }
                    }
                }
            }
            return null
        }

        if (trimmed.startsWith("content://", ignoreCase = true)) {
            val uri = Uri.parse(trimmed)
            resolveUriViaProcFd(context, uri)?.let { return it }

            val docId = runCatching {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.getDocumentId(uri)
                } else {
                    DocumentsContract.getTreeDocumentId(uri)
                }
            }.getOrNull() ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

            if (!docId.isNullOrBlank()) {
                if (docId.startsWith("raw:", ignoreCase = true)) {
                    val rawDir = File(docId.substring(4))
                    if (rawDir.exists() && rawDir.isDirectory) return rawDir
                }
                val volumeId = docId.substringBefore(':')
                val subPath = docId.substringAfter(':', "").trim('/')
                if (volumeId.equals("primary", ignoreCase = true)) {
                    val internal = getStorageRootDirectory()
                    val target = if (subPath.isEmpty()) internal else File(internal, subPath)
                    if (target.exists() && target.isDirectory) return target
                } else if (volumeId.isNotEmpty()) {
                    resolveVolumeMountPath(context, volumeId, subPath)?.let { return it }
                }
            }
            return null
        }

        // Case A: Direct POSIX path
        return File(trimmed)
    }

    fun isConfiguredCustomVaultPath(context: Context): Boolean {
        val stored = normalizeVaultPath(context.config.vaultRootPath)
        if (stored.isEmpty()) return false
        val defaultPath = normalizeVaultPath(getDefaultVaultRoot().absolutePath)
        return !stored.equals(defaultPath, ignoreCase = true)
    }

    fun isConfiguredExternalVaultPath(context: Context): Boolean {
        val stored = normalizeVaultPath(context.config.vaultRootPath)
        if (stored.isEmpty()) return false
        return isConfiguredCustomVaultPath(context) && !isInternalStoragePath(stored)
    }

    fun isConfiguredVaultTemporarilyUnavailable(context: Context): Boolean {
        val stored = normalizeVaultPath(context.config.vaultRootPath)
        if (stored.isEmpty()) return false
        if (!isConfiguredCustomVaultPath(context) || isInternalStoragePath(stored)) return false
        val storedFile = File(context.config.vaultRootPath)
        return !hasValidVaultHeader(storedFile) && !isLegacyVaultRoot(context, storedFile)
    }

    fun setVaultRootLocation(context: Context, vaultDir: File) {
        cachedVaultRoot = vaultDir
        encryptedFolderCache.clear()
        folderNameCache.clear()
        vaultRecycleBinFolder = null
        if (context.config.vaultRootPath != vaultDir.absolutePath) {
            context.config.vaultRootPath = vaultDir.absolutePath
        }
    }

    fun clearCachedVaultRoot() {
        cachedVaultRoot = null
        encryptedFolderCache.clear()
        folderNameCache.clear()
        vaultRecycleBinFolder = null
    }

    fun prepareVaultLocationSwitch(context: Context, newVaultRoot: File? = null) {
        val previousPath = normalizeVaultPath(cachedVaultRoot?.absolutePath ?: context.config.vaultRootPath)
        val targetPath = normalizeVaultPath(newVaultRoot?.absolutePath ?: "")
        val switchingToDifferentVault = previousPath.isNotEmpty() && !previousPath.equals(targetPath, ignoreCase = true)

        lockVault()
        cachedVaultRoot = newVaultRoot
        encryptedFolderCache.clear()
        folderNameCache.clear()
        vaultRecycleBinFolder = null
        clearBiometricKey(context)
        val cfg = context.config
        cfg.vaultRootPath = newVaultRoot?.absolutePath ?: ""
        cfg.vaultWrappedKeyData = ""
        cfg.hiddenPasswordHash = ""
        cfg.isHiddenPasswordProtectionOn = false
        cfg.temporarilyShowHidden = false
        cfg.temporarilyShowVaultExcluded = false
        if (switchingToDifferentVault) {
            runCatching { VirtualAlbumSyncManager.evictHiddenVirtualAlbumsFromDbCache(context) }
        }
        try {
            clearAppImageCaches(context)
        } catch (_: Throwable) {}
    }

    /**
     * Checks whether [dir] directly contains a vault or hosts a Cipher_Secrets folder
     * (either directly inside [dir], inside `<dir>/Fossify Gallery Cipher`, or `<dir>/Cipher`).
     */
    fun resolveExistingVaultInDirectory(dir: File): File? {
        val candidates = listOf(
            dir,
            File(dir, CIPHER_SECRETS_DIR_NAME),
            File(File(dir, CIPHER_APP_DIR_NAME), CIPHER_SECRETS_DIR_NAME),
            File(File(dir, CIPHER_ROOT_DIR_NAME), CIPHER_SECRETS_DIR_NAME)
        )
        return candidates.firstOrNull { hasValidVaultHeader(it) }
    }

    private fun internalVaultCandidates(): List<File> {
        val documentsDir = getDocumentsDirectory()
        return listOf(
            getDefaultVaultRoot(),
            File(getCipherRootDirectory(), CIPHER_ROOT_DIR_NAME),
            File(documentsDir, "$CIPHER_ROOT_DIR_NAME/$CIPHER_SECRETS_DIR_NAME"),
            File(documentsDir, CIPHER_SECRETS_DIR_NAME),
            File(documentsDir, CIPHER_ROOT_DIR_NAME)
        ).distinctBy { it.absolutePath }
    }

    private fun knownVaultRootCandidates(context: Context): List<File> {
        val candidates = ArrayList<File>(6)
        val storedPath = context.config.vaultRootPath
        if (storedPath.isNotEmpty()) {
            cachedVaultRoot?.takeIf {
                normalizeVaultPath(it.absolutePath).equals(normalizeVaultPath(storedPath), ignoreCase = true)
            }?.let { candidates.add(it) }
            candidates.add(File(storedPath))
            if (!isConfiguredCustomVaultPath(context)) {
                candidates.addAll(internalVaultCandidates())
            }
        } else {
            candidates.addAll(internalVaultCandidates())
        }
        return candidates.distinctBy { it.absolutePath }
    }

    private fun rememberVaultRoot(context: Context, root: File) {
        cachedVaultRoot = root
        if (context.config.vaultRootPath != root.absolutePath) {
            context.config.vaultRootPath = root.absolutePath
        }
    }

    fun listExternalStorageVolumeRoots(context: Context): List<File> {
        val volumeRoots = LinkedHashMap<String, File>()

        try {
            context.getExternalFilesDirs(null).filterNotNull().forEach { extDir ->
                var cur: File? = extDir
                while (cur != null) {
                    if (cur.name == "Android") {
                        cur.parentFile?.let { volRoot ->
                            volumeRoots.putIfAbsent(volRoot.absolutePath, volRoot)
                        }
                        break
                    }
                    cur = cur.parentFile
                }
            }
        } catch (_: Throwable) {}

        try {
            val storageDir = File("/storage")
            if (storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { child ->
                    if (
                        child.isDirectory &&
                        !child.name.equals("emulated", ignoreCase = true) &&
                        !child.name.equals("self", ignoreCase = true)
                    ) {
                        volumeRoots.putIfAbsent(child.absolutePath, child)
                    }
                }
            }
        } catch (_: Throwable) {}

        return volumeRoots.values.toList()
    }

    fun findCandidateExternalVaultRoots(context: Context): List<File> {
        val results = LinkedHashMap<String, File>()
        val volumeRoots = ArrayList<File>()
        volumeRoots.addAll(listExternalStorageVolumeRoots(context))
        try {
            context.sdCardPath.takeIf { it.isNotEmpty() }?.let { volumeRoots.add(File(it)) }
        } catch (_: Throwable) {}
        try {
            context.config.OTGPath
                .takeIf { it.isNotEmpty() && !it.startsWith("otg:", ignoreCase = true) && !it.startsWith("content://", ignoreCase = true) }
                ?.let { volumeRoots.add(File(it)) }
        } catch (_: Throwable) {}

        val internalRoot = normalizeVaultPath(getStorageRootDirectory().absolutePath)
        for (root in volumeRoots.distinctBy { it.absolutePath }) {
            val normRoot = normalizeVaultPath(root.absolutePath)
            if (normRoot.equals(internalRoot, ignoreCase = true)) continue
            if (!root.exists() || !root.isDirectory) continue

            resolveExistingVaultInDirectory(root)?.let { results.putIfAbsent(it.absolutePath, it) }
            root.listFiles()?.forEach { child ->
                if (hasValidVaultHeader(child)) {
                    results.putIfAbsent(child.absolutePath, child)
                }
            }
        }
        return results.values.toList()
    }

    /**
     * Looks for the vault root ON DISK.
     * - When [Config.vaultRootPath] is empty, ONLY default and legacy internal vault locations are
     *   checked (external USB/SD drives are NOT auto-adopted without explicit user selection).
     * - When [Config.vaultRootPath] is set, that configured location is checked directly.
     */
    fun locateVaultRootOnDisk(context: Context, allowDeepScan: Boolean = true): File? {
        if (isConfiguredVaultTemporarilyUnavailable(context)) {
            cachedVaultRoot = null
            return null
        }

        val known = knownVaultRootCandidates(context)

        known.firstOrNull { hasValidVaultHeader(it) }?.let {
            rememberVaultRoot(context, it)
            return it
        }
        known.firstOrNull { isLegacyVaultRoot(context, it) }?.let {
            rememberVaultRoot(context, it)
            return it
        }

        cachedVaultRoot = null
        return null
    }

    fun vaultExistsOnDisk(context: Context): Boolean = locateVaultRootOnDisk(context) != null

    fun getExistingCipherSecretsDir(context: Context): File? = locateVaultRootOnDisk(context)

    /** Anything that still claims a vault exists (keys in memory, prefs, biometric bundle). */
    fun hasStaleVaultState(context: Context): Boolean {
        val c = context.config
        return isVaultUnlocked ||
            c.vaultWrappedKeyData.isNotEmpty() ||
            c.vaultRootPath.isNotEmpty() ||
            c.hiddenPasswordHash.isNotEmpty() ||
            c.isHiddenPasswordProtectionOn ||
            c.isVaultBiometricEnabled ||
            c.vaultBiometricWrappedBundle.isNotEmpty()
    }

    /**
     * Forgets a vault whose folder/header no longer exists: locks it (wipes keys from memory),
     * clears every cache and removes all credentials and the biometric key.
     */
    fun resetStaleVaultState(context: Context) {
        lockVault()
        cachedVaultRoot = null
        encryptedFolderCache.clear()
        folderNameCache.clear()
        vaultRecycleBinFolder = null
        clearBiometricKey(context)
        val cfg = context.config
        cfg.vaultRootPath = ""
        cfg.vaultWrappedKeyData = ""
        cfg.hiddenPasswordHash = ""
        cfg.isHiddenPasswordProtectionOn = false
        cfg.isVaultBiometricEnabled = false
        cfg.temporarilyShowHidden = false
        cfg.temporarilyShowVaultExcluded = false
    }

    /**
     * The check every user-facing entry point must call (open vault, hide item/album, settings):
     * actively verifies the vault on disk and, if it is gone, wipes all leftover state so that
     * neither a password prompt nor an "empty vault" option can appear for a vault that does not exist.
     */
    fun verifyVaultOnDisk(context: Context): Boolean {
        if (!canAccessSharedStorage(context)) {
            // The disk cannot be inspected: neither wipe anything nor trust it blindly.
            return context.config.vaultWrappedKeyData.isNotEmpty()
        }
        if (isConfiguredVaultTemporarilyUnavailable(context)) {
            lockVault()
            cachedVaultRoot = null
            encryptedFolderCache.clear()
            folderNameCache.clear()
            vaultRecycleBinFolder = null
            return false
        }
        val exists = vaultExistsOnDisk(context)
        if (!exists && !vaultSetupInProgress && hasStaleVaultState(context)) {
            resetStaleVaultState(context)
        }
        return exists
    }

    fun deleteExistingVault(context: Context) {
        val targets = mutableListOf<File>()
        if (isConfiguredCustomVaultPath(context)) {
            val storedPath = context.config.vaultRootPath
            if (storedPath.isNotEmpty()) {
                targets.add(File(storedPath))
            }
            getExistingCipherSecretsDir(context)?.let { targets.add(it) }
            findVaultRoot(context)?.let { targets.add(it) }
        } else {
            targets.add(getDefaultVaultRoot())
            // Legacy locations of the vault.
            targets.add(File(getDocumentsDirectory(), "$CIPHER_ROOT_DIR_NAME/$CIPHER_SECRETS_DIR_NAME"))
            targets.add(File(getDocumentsDirectory(), CIPHER_SECRETS_DIR_NAME))
            val storedPath = context.config.vaultRootPath
            if (storedPath.isNotEmpty()) {
                targets.add(File(storedPath))
            }
            getExistingCipherSecretsDir(context)?.let { targets.add(it) }
            findVaultRoot(context)?.let { targets.add(it) }
        }

        for (dir in targets.distinct()) {
            if (dir.exists()) {
                try {
                    dir.walkBottomUp().forEach { file ->
                        if (file.isFile) {
                            secureWipeAndDeleteFile(file, context)
                        } else if (file.isDirectory) {
                            file.delete()
                        }
                    }
                    dir.delete()
                } catch (_: Exception) {}
            }
        }

        // The old Cipher container inside the Documents folder, when it is left empty.
        val legacyParentCipher = File(getDocumentsDirectory(), CIPHER_ROOT_DIR_NAME)
        if (legacyParentCipher.exists() && legacyParentCipher.isDirectory && legacyParentCipher.listFiles().isNullOrEmpty()) {
            try { legacyParentCipher.delete() } catch (_: Exception) {}
        }

        lockVault()
        unlockedMasterKey = null
        cachedVaultRoot = null
        encryptedFolderCache.clear()

        clearBiometricKey(context)
        context.config.vaultRootPath = ""
        context.config.vaultWrappedKeyData = ""
        context.config.hiddenPasswordHash = ""
        context.config.isHiddenPasswordProtectionOn = false
        context.config.isVaultBiometricEnabled = false
        context.config.temporarilyShowHidden = false
        context.config.temporarilyShowVaultExcluded = false
        clearAppImageCaches(context)
    }

    /** The vault exists if and only if its .vault header (or a legacy vault's content) is on disk. */
    fun hasExistingVault(context: Context): Boolean = vaultExistsOnDisk(context)

    // ==================== MASTER VAULT ROOT (STEALTH CONSOLIDATED CONTAINER) ====================

    /**
     * Creates the vault root folder. UI flows must go through [setupOrUpdateCredentials] so every
     * vault root immediately receives a valid `.vault` header; direct calls are intended for setup
     * and tests.
     */
    fun createVaultRoot(context: Context, customTargetDir: File? = null): File? {
        if (customTargetDir == null) {
            findVaultRoot(context)?.let { return it }

            ensureCipherRootDirectory()

            val newDir = getDefaultVaultRoot()
            if (!newDir.exists()) {
                newDir.mkdirs()
            }

            val noMedia = File(newDir, NOMEDIA)
            if (!noMedia.exists()) {
                try { noMedia.createNewFile() } catch (_: Exception) {}
            }

            if (!newDir.exists() || !newDir.isDirectory || !newDir.canWrite()) {
                return null
            }

            setVaultRootLocation(context, newDir)
            return newDir
        }

        val newDir = if (
            customTargetDir.name.equals(CIPHER_SECRETS_DIR_NAME, ignoreCase = true) ||
            isValidVaultHeader(File(customTargetDir, VAULT_HEADER_FILE))
        ) {
            customTargetDir
        } else {
            File(customTargetDir, CIPHER_SECRETS_DIR_NAME)
        }

        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        if (!newDir.exists() || !newDir.isDirectory || !newDir.canWrite()) {
            return null
        }

        val noMedia = File(newDir, NOMEDIA)
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }

        setVaultRootLocation(context, newDir)
        return newDir
    }

    /** Returns the vault root only if a vault really exists on disk; never creates anything. */
    fun findVaultRoot(context: Context): File? {
        if (isConfiguredVaultTemporarilyUnavailable(context)) {
            cachedVaultRoot = null
            return null
        }

        // Fast path: a cached root is trusted only while its .vault header is still there.
        cachedVaultRoot?.let { cached ->
            if (cached.isDirectory && File(cached, VAULT_HEADER_FILE).isFile) return cached
        }

        val root = locateVaultRootOnDisk(context) ?: return null

        // Migrate the vaults of the older versions into the standalone Cipher folder:
        // Documents/Cipher_Secrets and Documents/Cipher/Cipher_Secrets -> <internal storage>/Cipher/Cipher_Secrets
        val standardDir = getDefaultVaultRoot()
        val legacyDirs = listOf(
            File(getDocumentsDirectory(), CIPHER_SECRETS_DIR_NAME),
            File(getDocumentsDirectory(), "$CIPHER_ROOT_DIR_NAME/$CIPHER_SECRETS_DIR_NAME")
        )
        val legacyDir = legacyDirs.firstOrNull { it.absolutePath == root.absolutePath }
        if (legacyDir != null && !standardDir.exists()) {
            val moved = try {
                standardDir.parentFile?.mkdirs()
                legacyDir.renameTo(standardDir)
            } catch (_: Exception) {
                false
            }
            if (moved) {
                rememberVaultRoot(context, standardDir)
                return standardDir
            }
        }
        return root
    }

    // ==================== KEY MANAGEMENT (ARGON2id + AES-256-GCM) ====================

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val BIOMETRIC_KEYSTORE_ALIAS = "vault_biometric_hw_key"

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    fun canUnlockWithBiometrics(context: Context): Boolean {
        return context.config.isVaultBiometricEnabled &&
            context.config.vaultBiometricWrappedBundle.isNotEmpty() &&
            isBiometricHardwareAvailable(context)
    }

    fun getOrCreateBiometricKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(BIOMETRIC_KEYSTORE_ALIAS)) {
                generateBiometricKeyInternal(keyStore)
            }
            keyStore.getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey
        } catch (_: Throwable) {
            null
        }
    }

    private fun generateBiometricKeyInternal(keyStore: KeyStore) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        fun buildSpec(strongBox: Boolean): KeyGenParameterSpec {
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }

            if (strongBox) {
                builder.setIsStrongBoxBacked(true)
            }
            return builder.build()
        }

        try {
            keyGenerator.init(buildSpec(strongBox = true))
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            keyGenerator.init(buildSpec(strongBox = false))
            keyGenerator.generateKey()
        }
    }

    fun getBiometricEncryptCipher(): Cipher? {
        val key = getOrCreateBiometricKey() ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher
        } catch (_: Throwable) {
            null
        }
    }

    fun completeBiometricSetup(context: Context, cipher: Cipher): Boolean {
        val masterKey = unlockedMasterKey ?: return false
        return try {
            val encrypted = cipher.doFinal(masterKey)
            val iv = cipher.iv
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

            context.config.vaultBiometricWrappedBundle = Base64.encodeToString(combined, Base64.NO_WRAP)
            context.config.isVaultBiometricEnabled = true
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getBiometricDecryptCipher(context: Context): Cipher? {
        val wrappedB64 = context.config.vaultBiometricWrappedBundle
        if (wrappedB64.isEmpty()) return null
        val combined = try {
            Base64.decode(wrappedB64, Base64.NO_WRAP)
        } catch (_: Throwable) {
            return null
        }
        if (combined.size <= GCM_IV_LENGTH) return null
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val key = keyStore.getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearBiometricKey(context)
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun completeBiometricUnlock(context: Context, cipher: Cipher): Boolean {
        if (!verifyVaultOnDisk(context)) return false
        val wrappedB64 = context.config.vaultBiometricWrappedBundle
        if (wrappedB64.isEmpty()) return false
        val combined = try {
            Base64.decode(wrappedB64, Base64.NO_WRAP)
        } catch (_: Throwable) {
            return false
        }
        if (combined.size <= GCM_IV_LENGTH) return false
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        return try {
            val bundle = cipher.doFinal(encrypted)
            if (bundle.size < 32) {
                bundle.fill(0)
                return false
            }
            unlockedMasterKey = bundle.copyOf(32)
            bundle.fill(0)
            context.config.hiddenPasswordHash = "argon2id_aes256_gcm_v2"
            context.config.isHiddenPasswordProtectionOn = true
            findVaultRoot(context)?.let { context.config.vaultRootPath = it.absolutePath }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun clearBiometricKey(context: Context) {
        context.config.isVaultBiometricEnabled = false
        context.config.vaultBiometricWrappedBundle = ""
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(BIOMETRIC_KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEYSTORE_ALIAS)
            }
        } catch (_: Throwable) {}
    }

    fun deriveKeyArgon2id(
        secret: String,
        salt: ByteArray,
        memoryKb: Int = ARGON2_MEMORY_KB,
        iterations: Int = ARGON2_ITERATIONS,
        parallelism: Int = ARGON2_PARALLELISM
    ): SecretKeySpec {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKb)
            .withParallelism(parallelism)
        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val keyBytes = ByteArray(32)
        val secretBytes = secret.toByteArray(Charsets.UTF_8)
        try {
            generator.generateBytes(secretBytes, keyBytes, 0, keyBytes.size)
            val spec = SecretKeySpec(keyBytes, "AES")
            return spec
        } finally {
            secretBytes.fill(0)
            keyBytes.fill(0)
        }
    }

    private fun deriveWrappingKey(secret: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        try {
            val keyBytes = factory.generateSecret(spec).encoded
            val result = SecretKeySpec(keyBytes, "AES")
            keyBytes.fill(0)
            return result
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun writeVaultHeader(
        headerFile: File,
        salt: ByteArray,
        memoryKb: Int,
        iterations: Int,
        parallelism: Int,
        iv: ByteArray,
        wrappedBundle: ByteArray,
        protectionType: Int = PROTECTION_PATTERN
    ) {
        FileOutputStream(headerFile).use { fos ->
            DataOutputStream(fos).use { dos ->
                dos.write(VAULT_HEADER_MAGIC.toByteArray(Charsets.US_ASCII))
                dos.writeByte(3) // Version 3
                dos.writeByte(1) // KDF: 1 = Argon2id
                dos.writeInt(iterations)
                dos.writeInt(memoryKb)
                dos.writeInt(parallelism)
                dos.writeByte(protectionType)
                dos.writeInt(salt.size)
                dos.write(salt)
                dos.write(iv)
                dos.writeInt(wrappedBundle.size)
                dos.write(wrappedBundle)
                dos.flush()
                fos.fd.sync()
            }
        }
    }

    private fun unlockFromHeaderFile(context: Context, headerFile: File, secret: String): Boolean {
        if (!headerFile.exists()) return false
        return try {
            FileInputStream(headerFile).use { fis ->
                DataInputStream(BufferedInputStream(fis)).use { dis ->
                    val magicBytes = ByteArray(VAULT_HEADER_MAGIC.length)
                    dis.readFully(magicBytes)
                    if (String(magicBytes, Charsets.US_ASCII) != VAULT_HEADER_MAGIC) return false

                    val version = dis.readByte().toInt()
                    val kdfType = dis.readByte()
                    val iterations = dis.readInt()
                    val memoryKb = dis.readInt()
                    val parallelism = dis.readInt()
                    val storedProtectionType = if (version >= 3) dis.readByte().toInt() else -1
                    val saltLen = dis.readInt()
                    if (saltLen <= 0 || saltLen > 256) return false
                    val salt = ByteArray(saltLen)
                    dis.readFully(salt)
                    val iv = ByteArray(GCM_IV_LENGTH)
                    dis.readFully(iv)
                    val wrappedLen = dis.readInt()
                    if (wrappedLen <= 0 || wrappedLen > 65536) return false
                    val wrapped = ByteArray(wrappedLen)
                    dis.readFully(wrapped)

                    if (kdfType == 1.toByte()) {
                        if (memoryKb !in 8_192..262_144 || iterations !in 1..10 || parallelism !in 1..8) {
                            return false
                        }
                    }

                    val wrappingKey = if (kdfType == 1.toByte()) {
                        deriveKeyArgon2id(secret, salt, memoryKb, iterations, parallelism)
                    } else {
                        deriveWrappingKey(secret, salt)
                    }

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    val bundle = cipher.doFinal(wrapped)
                    if (bundle.size < 32) {
                        bundle.fill(0)
                        return false
                    }
                    unlockedMasterKey = bundle.copyOf(32)
                    bundle.fill(0)



                    context.config.hiddenPasswordHash = "argon2id_aes256_gcm_v2"
                    context.config.isHiddenPasswordProtectionOn = true
                    if (storedProtectionType in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)) {
                        context.config.hiddenProtectionType = storedProtectionType
                    }
                    if (version < 3) {
                        try {
                            setupOrUpdateCredentials(context, secret, context.config.hiddenProtectionType)
                        } catch (_: Exception) {}
                    }
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getVaultProtectionType(context: Context): Int? {
        val vaultRoot = findVaultRoot(context) ?: return null
        val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
        if (!headerFile.exists()) return null
        return try {
            FileInputStream(headerFile).use { fis ->
                DataInputStream(BufferedInputStream(fis)).use { dis ->
                    val magicBytes = ByteArray(VAULT_HEADER_MAGIC.length)
                    dis.readFully(magicBytes)
                    if (String(magicBytes, Charsets.US_ASCII) != VAULT_HEADER_MAGIC) return null
                    val version = dis.readByte().toInt()
                    if (version >= 3) {
                        dis.readByte() // kdfType
                        dis.readInt()  // iterations
                        dis.readInt()  // memoryKb
                        dis.readInt()  // parallelism
                        val protType = dis.readByte().toInt()
                        if (protType in listOf(PROTECTION_PATTERN, PROTECTION_PIN, PROTECTION_PASSWORD)) {
                            protType
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Initializes or updates the user's hidden item password/PIN/pattern credentials.
     * Uses Argon2id (32 MB, 3 iterations, 2 threads) + AES-256-GCM.
     * The master bundle (MasterKey + PrivateKey) is written directly to .vault in the root folder.
     */
    fun setupOrUpdateCredentials(
        context: Context,
        secret: String,
        protectionType: Int,
        customTargetDir: File? = null
    ): String {
        vaultSetupInProgress = true
        try {
            // A vault that no longer exists on disk must never leak its old keys into a new one.
            if (
                customTargetDir == null &&
                canAccessSharedStorage(context) &&
                !vaultExistsOnDisk(context) &&
                !isConfiguredVaultTemporarilyUnavailable(context)
            ) {
                resetStaleVaultState(context)
            }
            return setupOrUpdateCredentialsInternal(context, secret, protectionType, customTargetDir)
        } finally {
            vaultSetupInProgress = false
        }
    }

    private fun setupOrUpdateCredentialsInternal(
        context: Context,
        secret: String,
        protectionType: Int,
        customTargetDir: File? = null
    ): String {
        if (customTargetDir != null) {
            if (resolveExistingVaultInDirectory(customTargetDir) != null) {
                throw IllegalStateException("Vault already exists at target location! Unlock instead of overwriting.")
            }
            prepareVaultLocationSwitch(context, null)
        } else if (hasExistingVault(context) && !isVaultUnlocked) {
            throw IllegalStateException("Vault is locked! Cannot update credentials without unlocking first.")
        }
        val masterKey = unlockedMasterKey ?: ByteArray(32).also { secureRandom.nextBytes(it) }

        val salt = ByteArray(ARGON2_SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val wrappingKey = deriveKeyArgon2id(secret, salt, ARGON2_MEMORY_KB, ARGON2_ITERATIONS, ARGON2_PARALLELISM)
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val wrapped = cipher.doFinal(masterKey)

        clearBiometricKey(context)

        // Write vault root header (.vault) on disk for full multi-device portability
        val vaultRoot = createVaultRoot(context, customTargetDir)
            ?: throw IOException("Failed to create writable vault directory")
        val vaultHeader = File(vaultRoot, VAULT_HEADER_FILE)
        writeVaultHeader(vaultHeader, salt, ARGON2_MEMORY_KB, ARGON2_ITERATIONS, ARGON2_PARALLELISM, iv, wrapped, protectionType)

        val data = Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(wrapped, Base64.NO_WRAP)
        context.config.vaultWrappedKeyData = data
        context.config.vaultRootPath = vaultRoot.absolutePath
        cachedVaultRoot = vaultRoot
        encryptedFolderCache.clear()

        unlockedMasterKey = masterKey.copyOf()
        val marker = "argon2id_aes256_gcm_v2"
        context.config.hiddenPasswordHash = marker
        context.config.hiddenProtectionType = protectionType
        context.config.isHiddenPasswordProtectionOn = true

        try {
            getOrCreateDefaultHiddenAlbum(context)
        } catch (_: Exception) {
        }

        // Ensure any existing hidden virtual albums have their names encrypted in SQLite
        ensureBackgroundThread {
            try {
                getOrCreateDefaultHiddenAlbum(context)
                context.virtualAlbumsDB.getAllAlbums().filter { it.isHidden && !isEncryptedVaultString(it.name) }.forEach { album ->
                    val albumId = album.id ?: return@forEach
                    val encName = encryptStringForVault(context, album.name)
                    context.virtualAlbumsDB.updateAlbumVisibilityAndName(albumId, true, encName)
                }
            } catch (_: Exception) {
            }
        }

        return marker
    }

    fun isEncryptedVaultString(text: String): Boolean {
        return text.startsWith(VAULT_STR_PREFIX)
    }

    fun encryptStringForVault(context: Context, plainText: String): String {
        if (isEncryptedVaultString(plainText)) return plainText
        val masterKey = unlockedMasterKey ?: return plainText
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeByte(VERSION_SYMMETRIC.toInt())
                dos.write(iv)
                dos.writeInt(encrypted.size)
                dos.write(encrypted)
            }
            baos.toByteArray()
        }
        return VAULT_STR_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decryptStringFromVault(storedText: String): String? {
        if (!isEncryptedVaultString(storedText)) return storedText
        val masterKey = unlockedMasterKey ?: return null
        val b64 = storedText.removePrefix(VAULT_STR_PREFIX)
        return try {
            val raw = Base64.decode(b64, Base64.NO_WRAP)
            DataInputStream(ByteArrayInputStream(raw)).use { dis ->
                val version = dis.readByte()
                if (version != VERSION_SYMMETRIC) return null
                val iv = ByteArray(GCM_IV_LENGTH)
                dis.readFully(iv)
                val len = dis.readInt()
                if (len <= 0 || len > 8192) return null
                val encrypted = ByteArray(len)
                dis.readFully(encrypted)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun unlockWithSecret(context: Context, secret: String): Boolean {
        val vaultRoot = locateVaultRootOnDisk(context)
        if (vaultRoot == null) {
            // The vault is gone: there is nothing to unlock. Drop every leftover credential instead
            // of re-creating the vault from SharedPreferences.
            verifyVaultOnDisk(context)
            return false
        }

        val headerFile = File(vaultRoot, VAULT_HEADER_FILE)
        if (isValidVaultHeader(headerFile)) {
            val unlocked = unlockFromHeaderFile(context, headerFile, secret)
            if (unlocked) {
                context.config.vaultRootPath = vaultRoot.absolutePath
                cachedVaultRoot = vaultRoot
            }
            return unlocked
        }

        // Legacy vault (older app versions): the root holds encrypted content but has no .vault yet.
        return unlockLegacyVaultFromPrefs(context, secret)
    }

    /**
     * Only reachable for a legacy vault whose folder physically exists (see isLegacyVaultRoot).
     * On success the missing .vault header is written into that existing folder.
     */
    private fun unlockLegacyVaultFromPrefs(context: Context, secret: String): Boolean {
        val parts = context.config.vaultWrappedKeyData.split(":")
        if (parts.size != 3) return false
        return try {
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

            fun tryDecrypt(wrappingKey: SecretKeySpec): ByteArray? = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(ciphertext)
            } catch (_: Exception) {
                null
            }

            val bundle = tryDecrypt(deriveKeyArgon2id(secret, salt))
                ?: tryDecrypt(deriveWrappingKey(secret, salt))
                ?: return false

            if (bundle.size < 32) {
                bundle.fill(0)
                return false
            }
            unlockedMasterKey = bundle.copyOf(32)
            bundle.fill(0)

            // Upgrade to Argon2id and write the .vault header into the existing root
            setupOrUpdateCredentials(context, secret, context.config.hiddenProtectionType)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==================== FOLDER METADATA ENCRYPTION (.meta & .fmeta) ====================

    private fun writeEncryptedFolderMeta(context: Context, folder: File, originalName: String) {
        val metaFile = File(folder, VAULT_SUBFOLDER_META_FILE)
        val masterKey = unlockedMasterKey ?: return
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encryptedName = cipher.doFinal(originalName.toByteArray(Charsets.UTF_8))

        FileOutputStream(metaFile).use { fos ->
            DataOutputStream(fos).use { dos ->
                dos.write(META_MAGIC_V2.toByteArray(Charsets.US_ASCII))
                dos.write(iv)
                dos.writeInt(encryptedName.size)
                dos.write(encryptedName)
                dos.flush()
                fos.fd.sync()
            }
        }
        folderNameCache[folder.absolutePath] = originalName
    }

    fun createHiddenPhysicalAlbum(context: Context, originalName: String): File? {
        if (unlockedMasterKey == null) {
            return null
        }
        val vaultRoot = findVaultRoot(context) ?: return null
        val vaultNoMedia = File(vaultRoot, NOMEDIA)
        if (!vaultNoMedia.exists()) {
            try { vaultNoMedia.createNewFile() } catch (_: Exception) {}
        }
        var subfolder = File(vaultRoot, randomHex(6))
        while (subfolder.exists()) {
            subfolder = File(vaultRoot, randomHex(6))
        }
        if (!subfolder.mkdirs()) return null

        if (originalName.startsWith('.') && !isDefaultAlbumRawName(originalName) && originalName != VAULT_RECYCLE_BIN_NAME) {
            val noMedia = File(subfolder, NOMEDIA)
            if (!noMedia.exists()) {
                try { noMedia.createNewFile() } catch (_: Exception) {}
            }
        }

        writeEncryptedFolderMeta(context, subfolder, originalName)
        folderNameCache[subfolder.absolutePath] = originalName
        encryptedFolderCache[subfolder.absolutePath] = true
        return subfolder
    }

    fun getHiddenPhysicalAlbums(context: Context): List<VaultPhysicalAlbum> {
        val albums = ArrayList<VaultPhysicalAlbum>()
        val vaultRoot = findVaultRoot(context) ?: return emptyList()

        // 1. Vault Root (Main Vault album)
        val rootFiles = vaultRoot.listFiles() ?: emptyArray()
        val rootBinCount = rootFiles.count { isEncryptedFile(it.name) }
        albums.add(
            VaultPhysicalAlbum(
                name = context.getString(org.fossify.gallery.R.string.secret_vault),
                fullPath = vaultRoot.absolutePath,
                folder = vaultRoot,
                itemCount = rootBinCount
            )
        )

        // 2. Physical subfolders in vaultRoot (excluding virtual albums JSON/ and hidden/system folders)
        rootFiles.filter { it.isDirectory }.forEach { subfolder ->
            val folderName = subfolder.name
            if (folderName.equals("JSON", ignoreCase = true) || folderName.startsWith(".")) {
                return@forEach
            }
            if (isVaultRecycleBin(subfolder)) {
                return@forEach
            }

            val decryptedName = getDecryptedFolderName(subfolder, context) ?: folderName
            val binCount = subfolder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
            albums.add(
                VaultPhysicalAlbum(
                    name = decryptedName,
                    fullPath = subfolder.absolutePath,
                    folder = subfolder,
                    itemCount = binCount
                )
            )
        }

        // 3. Custom external encrypted physical folders from config
        for (path in context.config.encryptedFolders) {
            val folder = File(path)
            if (folder.exists() && folder.isDirectory && folder.absolutePath != vaultRoot.absolutePath) {
                if (albums.none { it.fullPath == folder.absolutePath }) {
                    val decryptedName = getDecryptedFolderName(folder) ?: folder.name
                    val binCount = folder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
                    albums.add(
                        VaultPhysicalAlbum(
                            name = decryptedName,
                            fullPath = folder.absolutePath,
                            folder = folder,
                            itemCount = binCount
                        )
                    )
                }
            }
        }

        return albums
    }

    fun getDecryptedRawFolderName(folder: File): String? {
        folderNameCache[folder.absolutePath]?.let { return it }
        val masterKey = unlockedMasterKey ?: return null

        val metaFile = when {
            File(folder, VAULT_SUBFOLDER_META_FILE).exists() -> File(folder, VAULT_SUBFOLDER_META_FILE)
            File(folder, ENCRYPTED_FOLDER_META_FILE).exists() -> File(folder, ENCRYPTED_FOLDER_META_FILE)
            else -> return null
        }

        return try {
            BufferedInputStream(FileInputStream(metaFile)).use { bis ->
                bis.mark(16)
                val headerBytes = ByteArray(8)
                val bytesRead = bis.read(headerBytes, 0, 8)
                if (bytesRead < 7) return null
                val str7 = String(headerBytes, 0, 7, Charsets.US_ASCII)
                val str8 = if (bytesRead >= 8) String(headerBytes, 0, 8, Charsets.US_ASCII) else ""

                bis.reset()
                val skipLen = when {
                    str8 == META_MAGIC_SYM -> 8
                    str7 == META_MAGIC_V2 -> 7
                    else -> return null
                }
                bis.skip(skipLen.toLong())

                DataInputStream(bis).use { dis ->
                    val iv = ByteArray(GCM_IV_LENGTH)
                    dis.readFully(iv)
                    val len = dis.readInt()
                    if (len <= 0 || len > 4096) return null
                    val encryptedName = ByteArray(len)
                    dis.readFully(encryptedName)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                    val name = String(cipher.doFinal(encryptedName), Charsets.UTF_8)
                    folderNameCache[folder.absolutePath] = name
                    name
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isDefaultAlbumRawName(raw: String?): Boolean {
        return raw == VAULT_DEFAULT_ALBUM_NAME ||
            raw == "Varsayılan Gizli Albüm" ||
            raw == "Default Hidden Album" ||
            raw == DEFAULT_SECRET_ALBUM_NAME
    }

    fun isDefaultHiddenAlbum(folder: File): Boolean {
        return isDefaultAlbumRawName(getDecryptedRawFolderName(folder))
    }

    fun isDefaultHiddenAlbumPath(path: String): Boolean {
        if (path.isEmpty()) return false
        val folder = File(path.trimEnd('/'))
        return folder.exists() && folder.isDirectory && isDefaultHiddenAlbum(folder)
    }

    fun renameEncryptedFolder(context: Context, folder: File, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || !folder.exists() || !folder.isDirectory) return false
        if (!isEncryptedFolder(folder) || isVaultRecycleBin(folder) || isDefaultHiddenAlbum(folder)) return false
        if (unlockedMasterKey == null) return false
        return try {
            writeEncryptedFolderMeta(context, folder, trimmed)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getDecryptedFolderName(folder: File, context: Context? = null): String? {
        val raw = getDecryptedRawFolderName(folder) ?: return null
        if (raw == VAULT_RECYCLE_BIN_NAME) {
            return context?.getString(org.fossify.commons.R.string.recycle_bin) ?: "Recycle Bin"
        }
        if (isDefaultAlbumRawName(raw)) {
            return DEFAULT_SECRET_ALBUM_NAME
        }
        return raw
    }

    fun getOrCreateDefaultHiddenAlbum(context: Context): File? {
        if (unlockedMasterKey == null) {
            return null
        }
        val vaultRoot = findVaultRoot(context) ?: return null

        val subfolders = vaultRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()
        for (sub in subfolders) {
            if (isDefaultHiddenAlbum(sub)) {
                val legacyNoMedia = File(sub, NOMEDIA)
                if (legacyNoMedia.exists()) {
                    try { legacyNoMedia.delete() } catch (_: Exception) {}
                }
                return sub
            }
        }

        return createHiddenPhysicalAlbum(context, VAULT_DEFAULT_ALBUM_NAME)
    }

    fun getVaultDestinationAlbums(context: Context): List<VaultPhysicalAlbum> {
        val albums = ArrayList<VaultPhysicalAlbum>()
        val defaultAlbum = getOrCreateDefaultHiddenAlbum(context)
        val defaultAlbumPath = defaultAlbum?.absolutePath
        val vaultRoot = findVaultRoot(context) ?: return emptyList()

        if (defaultAlbum != null && defaultAlbum.exists()) {
            val count = defaultAlbum.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
            albums.add(
                VaultPhysicalAlbum(
                    name = DEFAULT_SECRET_ALBUM_NAME,
                    fullPath = defaultAlbum.absolutePath,
                    folder = defaultAlbum,
                    itemCount = count
                )
            )
        }

        val otherAlbums = ArrayList<VaultPhysicalAlbum>()
        vaultRoot.listFiles()?.filter { it.isDirectory }?.forEach { subfolder ->
            if (subfolder.name.startsWith(".") || subfolder.name.equals("JSON", ignoreCase = true)) return@forEach
            if (isVaultRecycleBin(subfolder)) return@forEach
            if (subfolder.absolutePath == defaultAlbumPath) return@forEach

            val decryptedName = getDecryptedFolderName(subfolder, context) ?: subfolder.name
            val binCount = subfolder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
            otherAlbums.add(
                VaultPhysicalAlbum(
                    name = decryptedName,
                    fullPath = subfolder.absolutePath,
                    folder = subfolder,
                    itemCount = binCount
                )
            )
        }

        for (path in context.config.encryptedFolders) {
            val folder = File(path)
            if (folder.exists() && folder.isDirectory && folder.absolutePath != vaultRoot.absolutePath && folder.absolutePath != defaultAlbumPath) {
                if (otherAlbums.none { it.fullPath == folder.absolutePath } && albums.none { it.fullPath == folder.absolutePath }) {
                    val decryptedName = getDecryptedFolderName(folder, context) ?: folder.name
                    val binCount = folder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
                    otherAlbums.add(
                        VaultPhysicalAlbum(
                            name = decryptedName,
                            fullPath = folder.absolutePath,
                            folder = folder,
                            itemCount = binCount
                        )
                    )
                }
            }
        }

        otherAlbums.sortBy { it.name.lowercase() }
        albums.addAll(otherAlbums)
        return albums
    }



    // ==================== VAULT RECYCLE BIN (MODE 2) ====================

    fun isVaultRecycleBin(folder: File): Boolean {
        if (vaultRecycleBinFolder != null && folder.absolutePath == vaultRecycleBinFolder?.absolutePath) {
            return true
        }
        val raw = getDecryptedRawFolderName(folder)
        if (raw == VAULT_RECYCLE_BIN_NAME) {
            vaultRecycleBinFolder = folder
            return true
        }
        return false
    }

    fun getVaultRecycleBin(context: Context): File? {
        vaultRecycleBinFolder?.let {
            if (it.exists() && it.isDirectory) return it
        }
        val vaultRoot = findVaultRoot(context) ?: return null
        val subfolders = vaultRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: return null
        for (folder in subfolders) {
            if (isVaultRecycleBin(folder)) {
                vaultRecycleBinFolder = folder
                return folder
            }
        }
        return null
    }

    fun getOrCreateVaultRecycleBin(context: Context): File? {
        getVaultRecycleBin(context)?.let { return it }
        val vaultRoot = findVaultRoot(context) ?: return null
        var binFolder: File
        do {
            binFolder = File(vaultRoot, randomHex(6))
        } while (binFolder.exists())
        binFolder.mkdirs()
        try {
            File(binFolder, NOMEDIA).createNewFile()
        } catch (_: Exception) {}
        writeEncryptedFolderMeta(context, binFolder, VAULT_RECYCLE_BIN_NAME)
        vaultRecycleBinFolder = binFolder
        return binFolder
    }

    fun isVaultRecycleBinPath(context: Context, path: String): Boolean {
        if (path.isEmpty()) return false
        val cleanPath = path.trimEnd('/')
        val binFolder = getVaultRecycleBin(context)
        if (binFolder != null) {
            val binPath = binFolder.absolutePath
            if (cleanPath == binPath || cleanPath.startsWith("$binPath/")) return true
        }
        return false
    }

    fun isVaultRecycleBinPath(path: String): Boolean {
        if (path.isEmpty()) return false
        val clean = path.trimEnd('/')
        vaultRecycleBinFolder?.let { binFolder ->
            val binPath = binFolder.absolutePath
            if (clean == binPath || clean.startsWith("$binPath/")) return true
        }
        val file = File(clean)
        val folder = if (file.isDirectory) file else file.parentFile
        if (folder != null && folder.exists()) {
            return isVaultRecycleBin(folder)
        }
        return false
    }

    fun getVaultRecycleBinCount(context: Context): Int {
        val binFolder = getVaultRecycleBin(context) ?: return 0
        return binFolder.listFiles()?.count { isEncryptedFile(it.name) } ?: 0
    }

    fun getVaultRecycleBinSize(context: Context): Long {
        val binFolder = getVaultRecycleBin(context) ?: return 0L
        return binFolder.listFiles()?.filter { isEncryptedFile(it.name) }?.sumOf { it.length() } ?: 0L
    }

    fun getVaultRecycleBinRestoreMap(context: Context): HashMap<String, String> {
        val binFolder = getVaultRecycleBin(context) ?: return HashMap()
        val restoreFile = File(binFolder, VAULT_RESTORE_INFO_FILE)
        if (!restoreFile.exists()) return HashMap()
        return try {
            val encryptedContent = restoreFile.readText(Charsets.UTF_8).trim()
            if (encryptedContent.isEmpty()) return HashMap()
            val json = decryptStringFromVault(encryptedContent) ?: return HashMap()
            val type = object : TypeToken<HashMap<String, String>>() {}.type
            Gson().fromJson(json, type) ?: HashMap()
        } catch (_: Exception) {
            HashMap()
        }
    }

    fun saveVaultRecycleBinRestoreMap(context: Context, map: HashMap<String, String>) {
        val binFolder = getOrCreateVaultRecycleBin(context) ?: return
        val restoreFile = File(binFolder, VAULT_RESTORE_INFO_FILE)
        if (map.isEmpty()) {
            if (restoreFile.exists()) {
                restoreFile.delete()
            }
            return
        }
        try {
            val json = Gson().toJson(map)
            val encryptedContent = encryptStringForVault(context, json)
            restoreFile.writeText(encryptedContent, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun moveEncryptedFilesToVaultRecycleBin(context: Context, paths: List<String>): Boolean {
        if (paths.isEmpty()) return true
        val binFolder = getOrCreateVaultRecycleBin(context) ?: return false
        val restoreMap = getVaultRecycleBinRestoreMap(context)
        var success = true

        for (path in paths) {
            val sourceFile = File(path)
            if (!sourceFile.exists() || !isEncryptedFile(sourceFile.name)) continue
            val parentPath = sourceFile.parent ?: continue
            if (parentPath == binFolder.absolutePath) continue

            var destFile = File(binFolder, sourceFile.name)
            while (destFile.exists()) {
                destFile = File(binFolder, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
            }

            val destPath = destFile.absolutePath
            if (sourceFile.renameTo(destFile)) {
                restoreMap[destFile.name] = parentPath
                context.updateVirtualAlbumItemPath(path, destPath)
                metadataCache.remove(path)?.let { meta ->
                    metadataCache[destPath] = meta
                }
            } else {
                success = false
            }
        }

        saveVaultRecycleBinRestoreMap(context, restoreMap)
        return success
    }

    fun restoreVaultRecycleBinFiles(context: Context, paths: List<String>): Int {
        if (paths.isEmpty()) return 0
        val binFolder = getVaultRecycleBin(context) ?: return 0
        val restoreMap = getVaultRecycleBinRestoreMap(context)
        val vaultRoot = findVaultRoot(context) ?: return 0
        var restoredCount = 0

        for (path in paths) {
            val binFile = File(path)
            if (!binFile.exists() || binFile.parentFile?.absolutePath != binFolder.absolutePath) continue

            val originalParentPath = restoreMap[binFile.name]
            var targetFolder = if (!originalParentPath.isNullOrEmpty()) File(originalParentPath) else null

            if (targetFolder == null || !targetFolder.exists() || !targetFolder.isDirectory) {
                val existingFolders = vaultRoot.listFiles()?.filter {
                    it.isDirectory && !it.name.startsWith(".") && !isVaultRecycleBin(it)
                }
                targetFolder = existingFolders?.firstOrNull() ?: run {
                    var newSub = File(vaultRoot, randomHex(6))
                    while (newSub.exists()) {
                        newSub = File(vaultRoot, randomHex(6))
                    }
                    newSub.mkdirs()
                    writeEncryptedFolderMeta(context, newSub, "Restored")
                    newSub
                }
            }

            var targetFile = File(targetFolder, binFile.name)
            while (targetFile.exists()) {
                targetFile = File(targetFolder, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
            }

            val targetPath = targetFile.absolutePath
            if (binFile.renameTo(targetFile)) {
                restoreMap.remove(binFile.name)
                context.updateVirtualAlbumItemPath(path, targetPath)
                metadataCache.remove(path)?.let { meta ->
                    metadataCache[targetPath] = meta
                }
                restoredCount++
            }
        }

        saveVaultRecycleBinRestoreMap(context, restoreMap)
        return restoredCount
    }

    fun permanentlyDeleteVaultRecycleBinFiles(context: Context, paths: List<String>): Int {
        if (paths.isEmpty()) return 0
        val binFolder = getVaultRecycleBin(context) ?: return 0
        val restoreMap = getVaultRecycleBinRestoreMap(context)
        var deletedCount = 0

        for (path in paths) {
            val binFile = File(path)
            if (!binFile.exists() || binFile.parentFile?.absolutePath != binFolder.absolutePath) continue

            metadataCache.remove(path)
            context.deleteVirtualAlbumItemFromAllAlbums(path)
            secureWipeAndDeleteFile(context, binFile)
            restoreMap.remove(binFile.name)
            deletedCount++
        }

        saveVaultRecycleBinRestoreMap(context, restoreMap)
        return deletedCount
    }

    fun emptyVaultRecycleBin(context: Context): Boolean {
        val binFolder = getVaultRecycleBin(context) ?: return true
        val binFiles = binFolder.listFiles()?.filter { isEncryptedFile(it.name) } ?: emptyList()
        for (file in binFiles) {
            val path = file.absolutePath
            metadataCache.remove(path)
            context.deleteVirtualAlbumItemFromAllAlbums(path)
            secureWipeAndDeleteFile(context, file)
        }
        val restoreFile = File(binFolder, VAULT_RESTORE_INFO_FILE)
        if (restoreFile.exists()) {
            restoreFile.delete()
        }
        return true
    }

    // ==================== FILE ENCRYPTION & DECRYPTION ====================

    /**
     * True only for file formats the gallery can actually display (images, GIFs, videos, RAWs, SVGs).
     * Anything else (.pdf, .md, .txt ...) must never be encrypted, otherwise it would end up in the vault
     * as an unreadable, seemingly corrupted .bin file.
     */
    fun isSupportedMediaName(name: String): Boolean {
        return name.isImageFast() || name.isVideoFast() || name.isGif() || name.isRawFast() || name.isSvg()
    }

    /** True if [file] is a regular, not yet encrypted file of a media format supported by the gallery. */
    fun isEncryptableMediaFile(file: File): Boolean {
        return file.isFile &&
            file.name != NOMEDIA &&
            file.name != ENCRYPTED_FOLDER_META_FILE &&
            file.name != VAULT_SUBFOLDER_META_FILE &&
            file.name != VAULT_HEADER_FILE &&
            !isEncryptedFile(file.name) &&
            isSupportedMediaName(file.name)
    }

    private fun getMediaTypeForName(name: String): Int {
        return when {
            name.isGif() -> TYPE_GIFS
            name.isRawFast() -> TYPE_RAWS
            name.isVideoFast() -> TYPE_VIDEOS
            name.isSvg() -> TYPE_SVGS
            else -> TYPE_IMAGES
        }
    }

    private fun generateEmbeddedThumbnail(file: File, mediaType: Int): ByteArray {
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

    fun randomHex(bytesCount: Int = 8): String {
        val bytes = ByteArray(bytesCount)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Encrypts a single plaintext media file into targetDir as random hex .bin.
     */
    fun encryptFileToDirectory(
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
                    if (!areMediaTimestampsEquivalent(
                            sourceLastModified,
                            getSourceDateTaken(),
                            existingMeta.lastModified,
                            existingMeta.dateTaken
                        )
                    ) {
                        continue
                    }
                    if (arePlainAndEncryptedIdentical(sourceFile, existingBin, existingMeta)) {
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

        val headerKey = deriveSubkey(fileKey, DOMAIN_HEADER_KEY)
        val contentEncKey = deriveSubkey(fileKey, DOMAIN_CONTENT_ENC_KEY)
        val contentMacKey = deriveSubkey(fileKey, DOMAIN_CONTENT_MAC_KEY)

        val headerIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val headerCipher = Cipher.getInstance("AES/GCM/NoPadding")
        headerCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(headerKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, headerIv))
        val encryptedHeader = headerCipher.doFinal(headerPlainBytes)
        headerKey.fill(0)

        val contentIv = ByteArray(CTR_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val contentCipher = Cipher.getInstance("AES/CTR/NoPadding")
        contentCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentEncKey, "AES"), IvParameterSpec(contentIv))
        contentEncKey.fill(0)

        val poly1305 = Poly1305(AESEngine.newInstance())
        poly1305.init(ParametersWithIV(KeyParameter(contentMacKey), contentIv))
        contentMacKey.fill(0)

        var encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        while (encryptedFile.exists()) {
            encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        }

        return try {
            val sourcePath = sourceFile.absolutePath
            FileOutputStream(encryptedFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    DataOutputStream(bos).use { dos ->
                        dos.write(MAGIC_V2.toByteArray(Charsets.US_ASCII))
                        dos.writeByte(versionByte.toInt())
                        dos.write(headerIv)
                        dos.writeInt(encryptedHeader.size)
                        dos.write(encryptedHeader)
                        dos.write(contentIv)

                        FileInputStream(sourceFile).use { fis ->
                            val buffer = ByteArray(256 * 1024)
                            var read: Int
                            while (fis.read(buffer).also { read = it } != -1) {
                                val out = contentCipher.update(buffer, 0, read)
                                if (out != null && out.isNotEmpty()) {
                                    dos.write(out)
                                    poly1305.update(out, 0, out.size)
                                }
                            }
                            val finalBytes = contentCipher.doFinal()
                            if (finalBytes != null && finalBytes.isNotEmpty()) {
                                dos.write(finalBytes)
                                poly1305.update(finalBytes, 0, finalBytes.size)
                            }
                        }
                        val macTag = ByteArray(MAC_TAG_LENGTH)
                        poly1305.doFinal(macTag, 0)
                        dos.write(macTag)
                        dos.flush()
                        bos.flush()
                        fos.fd.sync()
                    }
                }
            }

            encryptedFile.setLastModified(lastModified)

            val payloadOffset = (MAGIC_V2.length + 1 + GCM_IV_LENGTH + 4 + encryptedHeader.size + CTR_IV_LENGTH).toLong()
            val newMeta = EncryptedFileMetadata(
                originalName = originalName,
                lastModified = lastModified,
                dateTaken = dateTaken,
                originalSize = originalSize,
                mediaType = mediaType,
                videoDuration = videoDuration,
                payloadOffset = payloadOffset,
                contentIv = contentIv,
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
    fun encryptStreamToDirectory(
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

        val headerKey = deriveSubkey(fileKey, DOMAIN_HEADER_KEY)
        val contentEncKey = deriveSubkey(fileKey, DOMAIN_CONTENT_ENC_KEY)
        val contentMacKey = deriveSubkey(fileKey, DOMAIN_CONTENT_MAC_KEY)

        val headerIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val headerCipher = Cipher.getInstance("AES/GCM/NoPadding")
        headerCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(headerKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, headerIv))
        val encryptedHeader = headerCipher.doFinal(headerPlainBytes)

        val contentIv = ByteArray(CTR_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val contentCipher = Cipher.getInstance("AES/CTR/NoPadding")
        contentCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentEncKey, "AES"), IvParameterSpec(contentIv))
        contentEncKey.fill(0)

        val poly1305 = Poly1305(AESEngine.newInstance())
        poly1305.init(ParametersWithIV(KeyParameter(contentMacKey), contentIv))
        contentMacKey.fill(0)

        var encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        while (encryptedFile.exists()) {
            encryptedFile = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        }

        val headerIvOffset = MAGIC_V2.length + 1

        return try {
            var totalBytesRead = 0L
            FileOutputStream(encryptedFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    DataOutputStream(bos).use { dos ->
                        dos.write(MAGIC_V2.toByteArray(Charsets.US_ASCII))
                        dos.writeByte(versionByte.toInt())
                        dos.write(headerIv)
                        dos.writeInt(encryptedHeader.size)
                        dos.write(encryptedHeader)
                        dos.write(contentIv)

                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            totalBytesRead += read
                            val out = contentCipher.update(buffer, 0, read)
                            if (out != null && out.isNotEmpty()) {
                                dos.write(out)
                                poly1305.update(out, 0, out.size)
                            }
                            onProgress?.invoke(totalBytesRead, contentLength)
                        }
                        val finalBytes = contentCipher.doFinal()
                        if (finalBytes != null && finalBytes.isNotEmpty()) {
                            dos.write(finalBytes)
                            poly1305.update(finalBytes, 0, finalBytes.size)
                        }
                        val macTag = ByteArray(MAC_TAG_LENGTH)
                        poly1305.doFinal(macTag, 0)
                        dos.write(macTag)
                        dos.flush()
                        bos.flush()
                        fos.fd.sync()
                    }
                }
            }

            if (contentLength <= 0L || contentLength != totalBytesRead) {
                val finalHeaderPlain = buildHeaderBytes(totalBytesRead)
                val newHeaderIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
                headerCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(headerKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, newHeaderIv))
                val newEncryptedHeader = headerCipher.doFinal(finalHeaderPlain)
                RandomAccessFile(encryptedFile, "rw").use { raf ->
                    raf.seek(headerIvOffset.toLong())
                    raf.write(newHeaderIv)
                    raf.writeInt(newEncryptedHeader.size)
                    raf.write(newEncryptedHeader)
                    raf.fd.sync()
                }
            }
            headerKey.fill(0)

            encryptedFile.setLastModified(lastModified)

            val finalThumbBytes = if (mediaType == TYPE_IMAGES) {
                generateEmbeddedThumbnailFromEncryptedFile(encryptedFile)
            } else null

            val payloadOffset = (headerIvOffset + GCM_IV_LENGTH + 4 + encryptedHeader.size + CTR_IV_LENGTH).toLong()
            metadataCache[encryptedFile.absolutePath] = EncryptedFileMetadata(
                originalName = originalName,
                lastModified = lastModified,
                dateTaken = dateTaken,
                originalSize = totalBytesRead,
                mediaType = mediaType,
                videoDuration = videoDuration,
                payloadOffset = payloadOffset,
                contentIv = contentIv,
                fileKey = fileKey.copyOf(),
                version = versionByte,
                isMacAuthenticated = true,
                thumbnailBytes = finalThumbBytes
            )

            encryptedFile
        } catch (e: Exception) {
            headerKey.fill(0)
            encryptedFile.delete()
            null
        }
    }

    private fun generateEmbeddedThumbnailFromEncryptedFile(encryptedFile: File): ByteArray? {
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

    fun decryptFileToDirectory(
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
                val existingLastMod = existingPlain.lastModified()
                if (!areTimestampsEquivalent(existingLastMod, meta.lastModified)) continue
                if (meta.dateTaken > 0L) {
                    val existingDateTaken = resolvePlaintextDateTaken(context, existingPlain, existingLastMod)
                    if (!areMediaTimestampsEquivalent(existingLastMod, existingDateTaken, meta.lastModified, meta.dateTaken)) {
                        continue
                    }
                }
                if (arePlainAndEncryptedIdentical(existingPlain, encryptedFile, meta, verifyIntegrity = false)) {
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

    fun getDisplayName(path: String): String {
        if (isEncryptedFile(path)) {
            readMetadata(File(path))?.originalName?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return path.getFilenameFromPath()
    }

    fun moveMetadataCacheEntry(oldPath: String, newPath: String) {
        metadataCache.remove(oldPath)?.let { meta ->
            metadataCache[newPath] = meta
        }
    }

    /**
     * Renames the encrypted display name stored inside a .bin file's AES-GCM header.
     * The .bin filename on disk remains completely unrelated (random hex).
     * Re-encrypts only the header and shifts the authenticated payload without re-encrypting media bytes.
     */
    fun renameEncryptedFile(
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

            val headerKey = if (meta.isMacAuthenticated) {
                deriveSubkey(meta.fileKey, DOMAIN_HEADER_KEY)
            } else {
                meta.fileKey
            }
            val headerIv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
            val headerCipher = Cipher.getInstance("AES/GCM/NoPadding")
            headerCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(headerKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, headerIv))
            val encryptedHeader = headerCipher.doFinal(headerPlainBytes)
            if (meta.isMacAuthenticated) {
                headerKey.fill(0)
            }

            val newPayloadOffset = (8 + 1 + GCM_IV_LENGTH + 4 + encryptedHeader.size + CTR_IV_LENGTH).toLong()

            FileOutputStream(tempFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    DataOutputStream(bos).use { dos ->
                        dos.write(magicBytes)
                        dos.writeByte(version.toInt())
                        dos.write(headerIv)
                        dos.writeInt(encryptedHeader.size)
                        dos.write(encryptedHeader)
                        dos.write(meta.contentIv)

                        RandomAccessFile(encryptedFile, "r").use { raf ->
                            raf.seek(meta.payloadOffset)
                            val buffer = ByteArray(64 * 1024)
                            if (version == VERSION_V5_AUTH) {
                                val macKey = deriveSubkey(meta.fileKey, DOMAIN_CONTENT_MAC_KEY)
                                val poly = Poly1305(AESEngine.newInstance())
                                poly.init(ParametersWithIV(KeyParameter(macKey), meta.contentIv))
                                macKey.fill(0)
                                poly.update(headerIv, 0, headerIv.size)
                                poly.update(encryptedHeader, 0, encryptedHeader.size)

                                var remaining = meta.originalSize
                                while (remaining > 0) {
                                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                    val read = raf.read(buffer, 0, toRead)
                                    if (read == -1) throw EOFException("Unexpected EOF copying encrypted payload")
                                    dos.write(buffer, 0, read)
                                    poly.update(buffer, 0, read)
                                    remaining -= read
                                }
                                val newTag = ByteArray(MAC_TAG_LENGTH)
                                poly.doFinal(newTag, 0)
                                dos.write(newTag)
                            } else {
                                var remaining = encryptedFile.length() - meta.payloadOffset
                                while (remaining > 0) {
                                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                    val read = raf.read(buffer, 0, toRead)
                                    if (read == -1) break
                                    dos.write(buffer, 0, read)
                                    remaining -= read
                                }
                            }
                        }

                        dos.flush()
                        bos.flush()
                        if (syncParentDir) {
                            fos.fd.sync()
                        }
                    }
                }
            }

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

    private fun hasFolderMetaFile(dir: File): Boolean {
        return File(dir, VAULT_SUBFOLDER_META_FILE).exists() ||
            File(dir, ENCRYPTED_FOLDER_META_FILE).exists()
    }

    private fun isEncryptedBinFile(file: File): Boolean {
        return file.isFile && !file.name.startsWith(".") && file.name.endsWith(ENCRYPTED_BIN_EXT, ignoreCase = true)
    }

    fun resolveSyncSourceRoot(selectedDir: File): File {
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

    internal fun formatCollisionFileName(originalName: String, copyIndex: Int): String {
        val lastDot = originalName.lastIndexOf('.')
        return if (lastDot > 0) {
            "${originalName.substring(0, lastDot)}($copyIndex)${originalName.substring(lastDot)}"
        } else {
            "$originalName($copyIndex)"
        }
    }

    internal fun isSameOrCollisionVariant(candidateName: String, incomingOriginalName: String): Boolean {
        return stripCollisionIndex(candidateName).equals(
            stripCollisionIndex(incomingOriginalName),
            ignoreCase = true
        )
    }

    internal fun stripCollisionIndex(name: String): String {
        val lastDot = name.lastIndexOf('.')
        val body = if (lastDot > 0) name.substring(0, lastDot) else name
        val ext = if (lastDot > 0) name.substring(lastDot) else ""
        if (!body.endsWith(")")) return name
        val openIdx = body.lastIndexOf('(')
        if (openIdx <= 0) return name
        val indexStr = body.substring(openIdx + 1, body.length - 1)
        if (indexStr.isNotEmpty() && indexStr.all { it in '0'..'9' } && (indexStr.toIntOrNull() ?: 0) >= 1) {
            return "${body.substring(0, openIdx)}$ext"
        }
        return name
    }

    class TargetPlainIndex(
        val targetDir: File,
        private val preExistingPaths: Set<String>? = null
    ) {
        private val byExactNameLower = HashMap<String, File>()
        private val byFamilyLower = HashMap<String, MutableList<File>>()

        init {
            if (targetDir.exists() && targetDir.isDirectory) {
                targetDir.listFiles()?.forEach { file ->
                    if (file.isFile && !isEncryptedFile(file.name)) {
                        recordFile(file)
                    }
                }
            }
        }

        fun recordFile(file: File) {
            val exactLower = file.name.lowercase(Locale.ROOT)
            byExactNameLower[exactLower] = file
            val familyLower = stripCollisionIndex(file.name).lowercase(Locale.ROOT)
            byFamilyLower.getOrPut(familyLower) { ArrayList(2) }.apply {
                removeAll { it.name.equals(file.name, ignoreCase = true) }
                add(file)
            }
        }

        fun removeFile(file: File) {
            val exactLower = file.name.lowercase(Locale.ROOT)
            byExactNameLower.remove(exactLower)
            val familyLower = stripCollisionIndex(file.name).lowercase(Locale.ROOT)
            byFamilyLower[familyLower]?.removeAll { it.name.equals(file.name, ignoreCase = true) }
        }

        fun findExact(desiredName: String): File? {
            return byExactNameLower[desiredName.lowercase(Locale.ROOT)]
        }

        fun containsName(name: String): Boolean {
            return byExactNameLower.containsKey(name.lowercase(Locale.ROOT))
        }

        fun findCandidates(desiredName: String, includeCollisionVariants: Boolean): List<File> {
            if (!includeCollisionVariants) {
                return findExact(desiredName)?.let { listOf(it) } ?: emptyList()
            }
            val familyLower = stripCollisionIndex(desiredName).lowercase(Locale.ROOT)
            val list = byFamilyLower[familyLower] ?: return emptyList()
            return list.filter { candidate ->
                isSameOrCollisionVariant(candidate.name, desiredName) &&
                    (candidate.name.equals(desiredName, ignoreCase = true) ||
                        preExistingPaths == null ||
                        candidate.absolutePath in preExistingPaths)
            }
        }

        fun getAlternativeFile(originalName: String): File {
            var copyIndex = 1
            var candidateName = formatCollisionFileName(originalName, copyIndex)
            while (containsName(candidateName) || File(targetDir, candidateName).exists()) {
                copyIndex++
                candidateName = formatCollisionFileName(originalName, copyIndex)
            }
            return File(targetDir, candidateName)
        }
    }

    class TargetEncryptedIndex(
        val targetDir: File,
        private val preExistingPaths: Set<String>? = null
    ) {
        private val byBinNameLower = HashMap<String, File>()
        private val byExactNameLower = HashMap<String, MutableList<Pair<File, EncryptedFileMetadata>>>()
        private val byFamilyLower = HashMap<String, MutableList<Pair<File, EncryptedFileMetadata>>>()

        init {
            if (targetDir.exists() && targetDir.isDirectory) {
                targetDir.listFiles()?.forEach { file ->
                    if (isEncryptedBinFile(file)) {
                        byBinNameLower[file.name.lowercase(Locale.ROOT)] = file
                        readMetadata(file, includeThumbnail = false, cacheResult = false)?.let { meta ->
                            indexMeta(file, meta)
                        }
                    }
                }
            }
        }

        private fun indexMeta(binFile: File, meta: EncryptedFileMetadata) {
            val exactLower = meta.originalName.lowercase(Locale.ROOT)
            val entry = binFile to meta
            byExactNameLower.getOrPut(exactLower) { ArrayList(1) }.add(entry)
            val familyLower = stripCollisionIndex(meta.originalName).lowercase(Locale.ROOT)
            byFamilyLower.getOrPut(familyLower) { ArrayList(2) }.add(entry)
        }

        fun addEntry(binFile: File, meta: EncryptedFileMetadata) {
            byBinNameLower[binFile.name.lowercase(Locale.ROOT)] = binFile
            indexMeta(binFile, meta)
        }

        fun findMatchingMirrorBin(sourceBinName: String): File? {
            return byBinNameLower[sourceBinName.lowercase(Locale.ROOT)]
        }

        fun findExactEntries(desiredName: String): List<Pair<File, EncryptedFileMetadata>> {
            return byExactNameLower[desiredName.lowercase(Locale.ROOT)] ?: emptyList()
        }

        fun findCandidates(
            desiredName: String,
            includeCollisionVariants: Boolean
        ): List<Pair<File, EncryptedFileMetadata>> {
            if (!includeCollisionVariants) {
                return findExactEntries(desiredName)
            }
            val familyLower = stripCollisionIndex(desiredName).lowercase(Locale.ROOT)
            val list = byFamilyLower[familyLower] ?: return emptyList()
            return list.filter { (binFile, meta) ->
                isSameOrCollisionVariant(meta.originalName, desiredName) &&
                    (meta.originalName.equals(desiredName, ignoreCase = true) ||
                        preExistingPaths == null ||
                        binFile.absolutePath in preExistingPaths)
            }
        }

        fun getAlternativeOriginalName(originalName: String): String {
            var copyIndex = 1
            var candidateName = formatCollisionFileName(originalName, copyIndex)
            while (byExactNameLower.containsKey(candidateName.lowercase(Locale.ROOT))) {
                copyIndex++
                candidateName = formatCollisionFileName(originalName, copyIndex)
            }
            return candidateName
        }

        fun removeExactEntries(desiredName: String): List<File> {
            val exactLower = desiredName.lowercase(Locale.ROOT)
            val removed = byExactNameLower.remove(exactLower) ?: return emptyList()
            val removedPaths = removed.mapTo(HashSet()) { it.first.absolutePath }
            removed.forEach { (binFile, _) ->
                byBinNameLower.remove(binFile.name.lowercase(Locale.ROOT))
            }
            val familyLower = stripCollisionIndex(desiredName).lowercase(Locale.ROOT)
            byFamilyLower[familyLower]?.removeAll { it.first.absolutePath in removedPaths }
            return removed.map { it.first }
        }
    }

    private fun readExactOrEof(stream: InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = stream.read(buffer, total, length - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private const val FAST_SAMPLING_THRESHOLD_BYTES = 8L * 1024L * 1024L
    private const val FAST_SAMPLING_CHUNK_BYTES = 256 * 1024

    private fun bufferPrefixEquals(bufA: ByteArray, bufB: ByteArray, len: Int): Boolean {
        return java.nio.ByteBuffer.wrap(bufA, 0, len) == java.nio.ByteBuffer.wrap(bufB, 0, len)
    }

    private fun computeFastSampleOffsets(size: Long, chunkSize: Int): LongArray {
        val maxOffset = (size - chunkSize).coerceAtLeast(0L)
        return longArrayOf(0L, size / 4L, size / 2L, (size * 3L) / 4L, maxOffset)
    }

    private inline fun filesMatchFastSampled(
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

    private fun readPlainChunk(raf: RandomAccessFile, offset: Long, buf: ByteArray, len: Int): Boolean {
        return try {
            raf.seek(offset)
            raf.readFully(buf, 0, len)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readEncryptedChunk(
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

    private fun streamsHaveEqualBytes(
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

    private fun isMacAuthenticatedContainerHeader(file: File): Boolean {
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

    private fun trailingMacTagsEqual(fileA: File, fileB: File): Boolean {
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

    private fun hasMatchingMacTrailer(sourceBin: File, existingFile: File): Boolean {
        return sourceBin.length() == existingFile.length() &&
            isMacAuthenticatedContainerHeader(sourceBin) &&
            isMacAuthenticatedContainerHeader(existingFile) &&
            trailingMacTagsEqual(sourceBin, existingFile)
    }

    private fun isFastMacMatchDuplicate(
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

    private fun streamsMatchBitForBit(
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

    fun areEncryptedFilesIdenticalContent(
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

    private enum class StreamCompareMacResult {
        MATCH_VALID,
        SOURCE_MAC_INVALID,
        MISMATCH
    }

    private fun compareEncryptedStreamAndVerifySourceMac(
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

    private fun copyBinAndVerifyIntegritySinglePass(
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

    private data class SyncPendingItem(
        val sourceBin: File,
        val sourceAlbumName: String?,
        val isDefaultTarget: Boolean
    )

    private fun collectSubdirBinFiles(rootSubdir: File): List<File> {
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

    private fun findExistingDefaultHiddenAlbum(vaultRoot: File): File? {
        val subfolders = vaultRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: return null
        return subfolders.firstOrNull { isDefaultHiddenAlbum(it) }
    }

    private fun matchExistingDestinationAlbum(vaultRoot: File, albumName: String): File? {
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

    private fun findExistingDestinationAlbum(vaultRoot: File, item: SyncPendingItem): File? {
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

    fun arePlainFilesIdentical(fileA: File, fileB: File, fastSampling: Boolean = false): Boolean {
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

    fun arePlainAndEncryptedIdentical(
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

    fun getAlternativePlainFile(targetDir: File, originalName: String): File {
        var copyIndex = 1
        var candidate = File(targetDir, formatCollisionFileName(originalName, copyIndex))
        while (candidate.exists()) {
            copyIndex++
            candidate = File(targetDir, formatCollisionFileName(originalName, copyIndex))
        }
        return candidate
    }

    fun findIdenticalPlainFileInDir(
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

    enum class TransferConflictStatus {
        NO_CONFLICT,
        IDENTICAL_SKIP,
        NAME_CONFLICT
    }

    data class TransferConflictCheck(
        val status: TransferConflictStatus,
        val displayName: String? = null
    )

    fun inspectTransferConflict(
        sourceFile: File,
        targetDir: File,
        isTargetEncrypted: Boolean,
        plainIndex: TargetPlainIndex? = null,
        encryptedIndex: TargetEncryptedIndex? = null,
        desiredPlainName: String? = null,
        fastSampling: Boolean = false,
        context: Context? = null
    ): TransferConflictCheck {
        if (!sourceFile.isFile || !targetDir.exists() || !targetDir.isDirectory) {
            return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
        }
        val srcParentCanon = runCatching { sourceFile.parentFile?.canonicalPath }.getOrNull()
        val targetCanon = runCatching { targetDir.canonicalPath }.getOrNull()
        if (srcParentCanon != null && srcParentCanon == targetCanon) {
            return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
        }

        val isSourceEncrypted = isEncryptedFile(sourceFile.name)
        val sourceMeta = if (isSourceEncrypted) {
            readMetadata(sourceFile, includeThumbnail = false, cacheResult = false)
        } else {
            null
        }
        if (isSourceEncrypted && sourceMeta == null) {
            return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
        }

        val desiredName = desiredPlainName ?: sourceMeta?.originalName ?: sourceFile.name
        val sourceLastMod = sourceMeta?.lastModified ?: sourceFile.lastModified()
        val sourceOrigSize = sourceMeta?.originalSize ?: sourceFile.length()
        val sourceMediaType = sourceMeta?.mediaType ?: getMediaTypeForName(desiredName)
        var resolvedSourceDateTaken: Long? = sourceMeta?.dateTaken
        fun getSourceDateTaken(): Long {
            return resolvedSourceDateTaken ?: resolvePlaintextDateTaken(
                context = context,
                sourceFile = sourceFile,
                fallbackLastModified = sourceLastMod
            ).also { resolvedSourceDateTaken = it }
        }

        if (isTargetEncrypted) {
            if (unlockedMasterKey == null) {
                return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
            }
            val index = encryptedIndex ?: TargetEncryptedIndex(targetDir)
            val exactEntries = index.findExactEntries(desiredName)
            if (exactEntries.isEmpty()) {
                return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)
            }

            val candidates = index.findCandidates(desiredName, includeCollisionVariants = true)
            for ((existingBin, existingMeta) in candidates) {
                if (existingMeta.originalSize != sourceOrigSize || existingMeta.mediaType != sourceMediaType) {
                    continue
                }
                if (!areMediaTimestampsEquivalent(
                        sourceLastMod,
                        getSourceDateTaken(),
                        existingMeta.lastModified,
                        existingMeta.dateTaken
                    )
                ) {
                    continue
                }
                val identical = if (isSourceEncrypted && sourceMeta != null) {
                    areEncryptedFilesIdenticalContent(
                        sourceFile,
                        sourceMeta,
                        existingBin,
                        existingMeta,
                        fastSampling = fastSampling
                    )
                } else {
                    // Duplicate skip never deletes or trusts external data, so skip the redundant second MAC pass
                    arePlainAndEncryptedIdentical(
                        sourceFile,
                        existingBin,
                        existingMeta,
                        verifyIntegrity = false,
                        fastSampling = fastSampling
                    )
                }
                if (identical) {
                    return TransferConflictCheck(TransferConflictStatus.IDENTICAL_SKIP, desiredName)
                }
            }
            return TransferConflictCheck(TransferConflictStatus.NAME_CONFLICT, desiredName)
        } else {
            val index = plainIndex ?: TargetPlainIndex(targetDir)
            index.findExact(desiredName)
                ?: return TransferConflictCheck(TransferConflictStatus.NO_CONFLICT)

            val candidates = index.findCandidates(desiredName, includeCollisionVariants = true)
            for (existingPlain in candidates) {
                if (existingPlain.length() != sourceOrigSize) continue
                val existingLastMod = existingPlain.lastModified()
                if (!areTimestampsEquivalent(sourceLastMod, existingLastMod)) continue
                val srcTaken = getSourceDateTaken()
                val existingTaken = resolvePlaintextDateTaken(context, existingPlain, existingLastMod)
                if (!areMediaTimestampsEquivalent(sourceLastMod, srcTaken, existingLastMod, existingTaken)) {
                    continue
                }
                val identical = if (isSourceEncrypted && sourceMeta != null) {
                    arePlainAndEncryptedIdentical(
                        existingPlain,
                        sourceFile,
                        sourceMeta,
                        verifyIntegrity = false,
                        fastSampling = fastSampling
                    )
                } else {
                    arePlainFilesIdentical(sourceFile, existingPlain, fastSampling = fastSampling)
                }
                if (identical) {
                    return TransferConflictCheck(TransferConflictStatus.IDENTICAL_SKIP, desiredName)
                }
            }
            return TransferConflictCheck(TransferConflictStatus.NAME_CONFLICT, desiredName)
        }
    }

    /**
     * Returns true if at least one (source, targetDir) pair already has a bit-identical
     * counterpart in its target directory.
     */
    fun hasAnyIdenticalTransferMatch(
        sourceTargetPairs: List<Pair<File, File>>,
        isTargetEncrypted: Boolean,
        fastSampling: Boolean = false,
        context: Context? = null
    ): Boolean {
        if (sourceTargetPairs.isEmpty()) return false
        val plainIndexes = HashMap<String, TargetPlainIndex>()
        val encIndexes = HashMap<String, TargetEncryptedIndex>()
        for ((srcFile, targetDir) in sourceTargetPairs) {
            if (!srcFile.isFile || !targetDir.exists() || !targetDir.isDirectory) continue
            val plainIndex = if (!isTargetEncrypted) {
                plainIndexes.getOrPut(targetDir.absolutePath) { TargetPlainIndex(targetDir) }
            } else {
                null
            }
            val encIndex = if (isTargetEncrypted) {
                encIndexes.getOrPut(targetDir.absolutePath) { TargetEncryptedIndex(targetDir) }
            } else {
                null
            }
            val check = inspectTransferConflict(
                sourceFile = srcFile,
                targetDir = targetDir,
                isTargetEncrypted = isTargetEncrypted,
                plainIndex = plainIndex,
                encryptedIndex = encIndex,
                fastSampling = fastSampling,
                context = context
            )
            if (check.status == TransferConflictStatus.IDENTICAL_SKIP) {
                return true
            }
        }
        return false
    }

    fun findContentConflictInTarget(
        sourceFile: File,
        targetDir: File,
        isTargetEncrypted: Boolean,
        plainIndex: TargetPlainIndex? = null,
        encryptedIndex: TargetEncryptedIndex? = null,
        includeCollisionVariants: Boolean = true,
        context: Context? = null
    ): String? {
        if (!sourceFile.isFile || !targetDir.exists() || !targetDir.isDirectory) return null
        val srcParentCanon = runCatching { sourceFile.parentFile?.canonicalPath }.getOrNull()
        val targetCanon = runCatching { targetDir.canonicalPath }.getOrNull()
        if (srcParentCanon != null && srcParentCanon == targetCanon) return null

        val isSourceEncrypted = isEncryptedFile(sourceFile.name)
        val sourceMeta = if (isSourceEncrypted) {
            readMetadata(sourceFile, includeThumbnail = false, cacheResult = false)
        } else {
            null
        }
        if (isSourceEncrypted && sourceMeta == null) return null

        val desiredName = sourceMeta?.originalName ?: sourceFile.name
        val sourceLastMod = sourceMeta?.lastModified ?: sourceFile.lastModified()
        val sourceOrigSize = sourceMeta?.originalSize ?: sourceFile.length()
        val sourceMediaType = sourceMeta?.mediaType ?: getMediaTypeForName(desiredName)
        var resolvedSourceDateTaken: Long? = sourceMeta?.dateTaken
        fun getSourceDateTaken(): Long {
            return resolvedSourceDateTaken ?: resolvePlaintextDateTaken(
                context = context,
                sourceFile = sourceFile,
                fallbackLastModified = sourceLastMod
            ).also { resolvedSourceDateTaken = it }
        }

        if (isTargetEncrypted) {
            if (unlockedMasterKey == null) return null
            val index = encryptedIndex ?: TargetEncryptedIndex(targetDir)
            val candidates = index.findCandidates(desiredName, includeCollisionVariants)

            for ((existingBin, existingMeta) in candidates) {
                if (existingMeta.originalSize != sourceOrigSize || existingMeta.mediaType != sourceMediaType) {
                    continue
                }
                if (!areMediaTimestampsEquivalent(
                        sourceLastMod,
                        getSourceDateTaken(),
                        existingMeta.lastModified,
                        existingMeta.dateTaken
                    )
                ) {
                    continue
                }
                val identical = if (isSourceEncrypted && sourceMeta != null) {
                    areEncryptedFilesIdenticalContent(sourceFile, sourceMeta, existingBin, existingMeta)
                } else {
                    arePlainAndEncryptedIdentical(sourceFile, existingBin, existingMeta)
                }
                if (identical) return null
            }

            return if (index.findExactEntries(desiredName).isNotEmpty()) desiredName else null
        } else {
            val index = plainIndex ?: TargetPlainIndex(targetDir)
            val candidates = index.findCandidates(desiredName, includeCollisionVariants)

            for (existingPlain in candidates) {
                if (existingPlain.length() != sourceOrigSize) continue
                val existingLastMod = existingPlain.lastModified()
                if (!areTimestampsEquivalent(sourceLastMod, existingLastMod)) continue
                val srcTaken = getSourceDateTaken()
                val existingTaken = resolvePlaintextDateTaken(context, existingPlain, existingLastMod)
                if (!areMediaTimestampsEquivalent(sourceLastMod, srcTaken, existingLastMod, existingTaken)) {
                    continue
                }
                val identical = if (isSourceEncrypted && sourceMeta != null) {
                    arePlainAndEncryptedIdentical(existingPlain, sourceFile, sourceMeta)
                } else {
                    arePlainFilesIdentical(sourceFile, existingPlain)
                }
                if (identical) return null
            }

            return if (index.findExact(desiredName) != null) desiredName else null
        }
    }

    fun copyOrMoveEncryptedFileToDirectory(
        context: Context,
        sourceBin: File,
        targetDir: File,
        isMove: Boolean,
        conflictResolution: Int = CONFLICT_KEEP_BOTH,
        targetIndex: TargetEncryptedIndex? = null,
        skipDuplicateCheck: Boolean = false,
        deleteSourceOnIdentical: Boolean = true,
        matchCollisionVariants: Boolean = true
    ): File? {
        if (!sourceBin.isFile || !isEncryptedFile(sourceBin.name)) return null
        val sourceMeta = readMetadata(sourceBin, includeThumbnail = false, cacheResult = false) ?: return null
        if (sourceMeta.isMacAuthenticated && !verifyFileIntegrity(sourceBin, sourceMeta)) return null
        if (!targetDir.exists() && !targetDir.mkdirs()) return null

        val srcParentCanon = runCatching { sourceBin.parentFile?.canonicalPath }.getOrNull()
        val targetCanon = runCatching { targetDir.canonicalPath }.getOrNull()
        if (srcParentCanon != null && srcParentCanon == targetCanon) return sourceBin

        val index = targetIndex ?: TargetEncryptedIndex(targetDir)

        if (!skipDuplicateCheck) {
            val candidates = index.findCandidates(sourceMeta.originalName, matchCollisionVariants)
            for ((existingBin, existingMeta) in candidates) {
                if (areEncryptedTimestampsEquivalent(sourceMeta, existingMeta) &&
                    areEncryptedFilesIdenticalContent(sourceBin, sourceMeta, existingBin, existingMeta)
                ) {
                    if (isMove && deleteSourceOnIdentical) {
                        runCatching { context.updateVirtualAlbumItemPath(sourceBin.absolutePath, existingBin.absolutePath) }
                        runCatching { context.mediaDB.deleteMediumPath(sourceBin.absolutePath) }
                        metadataCache.remove(sourceBin.absolutePath)?.fileKey?.fill(0)
                        sourceBin.delete()
                    }
                    return if (!deleteSourceOnIdentical) null else existingBin
                }
            }
        }

        var desiredName = sourceMeta.originalName
        val exactMatches = index.findExactEntries(desiredName)
        if (exactMatches.isNotEmpty()) {
            when (conflictResolution) {
                CONFLICT_SKIP -> return null
                CONFLICT_OVERWRITE -> {
                    val removed = index.removeExactEntries(desiredName)
                    removed.forEach { existingBin ->
                        metadataCache.remove(existingBin.absolutePath)?.fileKey?.fill(0)
                        runCatching { context.mediaDB.deleteMediumPath(existingBin.absolutePath) }
                        existingBin.delete()
                    }
                }
                else -> {
                    desiredName = index.getAlternativeOriginalName(desiredName)
                }
            }
        }

        var destBin = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        while (destBin.exists()) {
            destBin = File(targetDir, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
        }

        val needsRename = desiredName != sourceMeta.originalName
        val transferred = if (isMove && !needsRename && sourceBin.renameTo(destBin)) {
            true
        } else {
            try {
                FileInputStream(sourceBin).use { fis ->
                    BufferedInputStream(fis, 256 * 1024).use { bis ->
                        FileOutputStream(destBin).use { fos ->
                            BufferedOutputStream(fos, 256 * 1024).use { bos ->
                                bis.copyTo(bos, 256 * 1024)
                                bos.flush()
                                fos.fd.sync()
                            }
                        }
                    }
                }
                destBin.setLastModified(sourceBin.lastModified())
                if (needsRename && !renameEncryptedFile(destBin, desiredName, skipIntegrityCheck = true, syncParentDir = false)) {
                    destBin.delete()
                    return null
                }
                if (isMove) {
                    sourceBin.delete()
                }
                true
            } catch (_: Exception) {
                destBin.delete()
                false
            }
        }
        if (!transferred) return null

        if (isMove) {
            metadataCache.remove(sourceBin.absolutePath)?.fileKey?.fill(0)
            runCatching { context.updateVirtualAlbumItemPath(sourceBin.absolutePath, destBin.absolutePath) }
            runCatching { context.mediaDB.deleteMediumPath(sourceBin.absolutePath) }
        }
        val destMeta = readMetadata(destBin, includeThumbnail = false, cacheResult = false) ?: sourceMeta.copy(originalName = desiredName)
        index.addEntry(destBin, destMeta)
        return destBin
    }

    fun cleanupEmptyEncryptedAlbumIfNeeded(context: Context, albumDir: File): Boolean {
        if (!albumDir.exists() || !albumDir.isDirectory || !isEncryptedFolder(albumDir)) return false
        val vaultRoot = findVaultRoot(context)
        if (vaultRoot != null) {
            val rootCanon = runCatching { vaultRoot.canonicalPath }.getOrDefault(vaultRoot.absolutePath)
            val dirCanon = runCatching { albumDir.canonicalPath }.getOrDefault(albumDir.absolutePath)
            if (rootCanon == dirCanon) return false
        }
        if (isVaultRecycleBin(albumDir) || isDefaultHiddenAlbum(albumDir)) {
            return false
        }
        val metaNames = setOf(VAULT_SUBFOLDER_META_FILE, ENCRYPTED_FOLDER_META_FILE, NOMEDIA, VAULT_RESTORE_INFO_FILE)
        val remaining = albumDir.listFiles() ?: emptyArray()
        if (remaining.any { it.name !in metaNames }) {
            return false
        }
        val deleted = runCatching { albumDir.deleteRecursively() }.getOrDefault(false)
        if (deleted || !albumDir.exists()) {
            folderNameCache.remove(albumDir.absolutePath)
            encryptedFolderCache[albumDir.absolutePath] = false
            runCatching { context.config.removeEncryptedFolder(albumDir.absolutePath) }
            runCatching { context.directoryDB.deleteDirPath(albumDir.absolutePath) }
            return true
        }
        return false
    }

    private const val PENDING_SYNC_FILE_NAME = ".pending_sync"

    fun syncVaultFromExternalDirectory(
        context: Context,
        sourceDir: File,
        requirePendingManifest: Boolean = true,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): VaultSyncResult? {
        if (unlockedMasterKey == null) return null
        val vaultRoot = findVaultRoot(context) ?: return null
        val effectiveSource = resolveSyncSourceRoot(sourceDir)
        if (!effectiveSource.exists() || !effectiveSource.isDirectory) return null

        val sourceCanonical = runCatching { effectiveSource.canonicalPath }.getOrDefault(effectiveSource.absolutePath)
        val vaultCanonical = runCatching { vaultRoot.canonicalPath }.getOrDefault(vaultRoot.absolutePath)
        if (sourceCanonical == vaultCanonical) return null

        val pendingItems = ArrayList<SyncPendingItem>()
        val pendingFile = File(effectiveSource, PENDING_SYNC_FILE_NAME)
        val manifestEntryByBinPath = HashMap<String, String>()

        if (requirePendingManifest) {
            if (!pendingFile.isFile) {
                return VaultSyncResult(0, 0, 0, 0, 0)
            }
            val effectiveCanonicalDir = runCatching { effectiveSource.canonicalFile }.getOrNull() ?: effectiveSource
            val lines = runCatching { pendingFile.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
            for (rawLine in lines) {
                val rel = rawLine.trim().replace('\\', '/')
                if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) continue
                val bin = File(effectiveSource, rel)
                val binCanonical = runCatching { bin.canonicalFile }.getOrNull() ?: continue
                if (!binCanonical.path.startsWith(effectiveCanonicalDir.path + File.separator) &&
                    binCanonical.path != effectiveCanonicalDir.path
                ) {
                    continue
                }
                if (!bin.exists() || !bin.isFile || !isEncryptedBinFile(bin)) continue

                val parentDir = bin.parentFile ?: continue
                val parentCanonical = runCatching { parentDir.canonicalPath }.getOrDefault(parentDir.absolutePath)
                val isRootLevel = parentCanonical == sourceCanonical
                if (isRootLevel) {
                    pendingItems.add(SyncPendingItem(bin, null, isDefaultTarget = true))
                } else {
                    if (parentDir.name == VAULT_RECYCLE_BIN_NAME || isVaultRecycleBin(parentDir)) continue
                    val raw = getDecryptedRawFolderName(parentDir)?.takeIf { it.isNotBlank() } ?: parentDir.name
                    if (raw == VAULT_RECYCLE_BIN_NAME) continue
                    val isDefaultTarget = isDefaultAlbumRawName(raw)
                    val albumName = if (isDefaultTarget) null else raw.trim()
                    pendingItems.add(SyncPendingItem(bin, albumName, isDefaultTarget))
                }
                manifestEntryByBinPath[bin.absolutePath] = rel
            }
            if (pendingItems.isEmpty()) {
                runCatching { pendingFile.delete() }
                return VaultSyncResult(0, 0, 0, 0, 0)
            }
        } else if (hasFolderMetaFile(effectiveSource)) {
            val raw = getDecryptedRawFolderName(effectiveSource)
            if (raw != VAULT_RECYCLE_BIN_NAME) {
                val bins = collectSubdirBinFiles(effectiveSource)
                val isDefaultTarget = isDefaultAlbumRawName(raw)
                val albumName = if (isDefaultTarget) {
                    null
                } else {
                    (raw?.takeIf { it.isNotBlank() } ?: effectiveSource.name).trim()
                }
                bins.forEach { bin ->
                    pendingItems.add(SyncPendingItem(bin, albumName, isDefaultTarget))
                }
            }
        } else {
            val children = effectiveSource.listFiles() ?: emptyArray()
            children.filter { isEncryptedBinFile(it) }.forEach { bin ->
                pendingItems.add(SyncPendingItem(bin, null, isDefaultTarget = true))
            }

            children.filter { it.isDirectory }.forEach { sub ->
                if (sub.name.equals("JSON", ignoreCase = true)) return@forEach
                if (sub.name == VAULT_RECYCLE_BIN_NAME || isVaultRecycleBin(sub)) return@forEach
                if (sub.name.startsWith(".") && !hasFolderMetaFile(sub)) return@forEach

                val bins = collectSubdirBinFiles(sub)
                if (bins.isEmpty()) return@forEach

                val raw = getDecryptedRawFolderName(sub)?.takeIf { it.isNotBlank() } ?: sub.name
                if (raw == VAULT_RECYCLE_BIN_NAME) return@forEach

                val isDefaultTarget = isDefaultAlbumRawName(raw)
                val albumName = if (isDefaultTarget) null else raw.trim()
                bins.forEach { bin ->
                    pendingItems.add(SyncPendingItem(bin, albumName, isDefaultTarget))
                }
            }
        }

        val total = pendingItems.size
        var copiedCount = 0
        var renamedCount = 0
        var skippedDuplicateCount = 0
        var failedCount = 0
        val failedManifestEntries = LinkedHashSet<String>()
        val touchedTargetAlbums = HashSet<String>()

        fun recordFailure(sourceBin: File) {
            failedCount++
            manifestEntryByBinPath[sourceBin.absolutePath]?.let { failedManifestEntries.add(it) }
        }

        runCryptoOperation {
            val resolvedAlbumMap = HashMap<String, File>()
            val existingAlbumLookedUp = HashSet<String>()
            val albumIndexMap = HashMap<File, TargetEncryptedIndex>()
            val albumBinNameIndex = HashMap<File, HashMap<String, File>>()

            var processed = 0
            for (item in pendingItems) {
                val sourceBin = item.sourceBin
                val albumKey = if (item.isDefaultTarget) {
                    "__DEFAULT__"
                } else {
                    "ALBUM:${item.sourceAlbumName?.trim()?.lowercase(Locale.ROOT) ?: ""}"
                }

                var existingTarget = resolvedAlbumMap[albumKey]
                if (existingTarget == null && existingAlbumLookedUp.add(albumKey)) {
                    existingTarget = findExistingDestinationAlbum(vaultRoot, item)
                    if (existingTarget != null) {
                        resolvedAlbumMap[albumKey] = existingTarget
                    }
                }

                // Stage 0: O(1) exact-mirror .bin fast skip before reading headers or building metadata index
                if (existingTarget != null) {
                    val binNameMap = albumBinNameIndex.getOrPut(existingTarget) {
                        val map = HashMap<String, File>()
                        existingTarget.listFiles()
                            ?.filter { isEncryptedBinFile(it) }
                            ?.forEach { existingBin ->
                                map[existingBin.name.lowercase(Locale.ROOT)] = existingBin
                            }
                        map
                    }
                    val matchingMirrorBin = binNameMap[sourceBin.name.lowercase(Locale.ROOT)]
                    if (matchingMirrorBin != null && hasMatchingMacTrailer(sourceBin, matchingMirrorBin)) {
                        skippedDuplicateCount++
                        processed++
                        onProgress?.invoke(processed, total)
                        continue
                    }
                }

                // Stage 1: Small GCM-authenticated header read only (no thumbnail allocation, no cache pollution)
                val sourceMeta = readMetadata(sourceBin, includeThumbnail = false, cacheResult = false)
                if (sourceMeta == null) {
                    metadataCache.remove(sourceBin.absolutePath)
                    recordFailure(sourceBin)
                    processed++
                    onProgress?.invoke(processed, total)
                    continue
                }

                var integrityVerified = !sourceMeta.isMacAuthenticated

                if (existingTarget != null) {
                    val index = albumIndexMap.getOrPut(existingTarget) {
                        TargetEncryptedIndex(existingTarget)
                    }
                    val collisionCandidates = index.findCandidates(sourceMeta.originalName, includeCollisionVariants = true)

                    // Stage 2: O(1) length + contentIv + Poly1305 trailer + timestamp match before full integrity scan
                    if (collisionCandidates.any { (existingFile, existingMeta) ->
                            areEncryptedTimestampsEquivalent(sourceMeta, existingMeta) &&
                                isFastMacMatchDuplicate(sourceBin, sourceMeta, existingFile, existingMeta)
                        }
                    ) {
                        skippedDuplicateCount++
                        processed++
                        onProgress?.invoke(processed, total)
                        continue
                    }

                    // Stage 3/4: Filter to exact size + mediaType + timestamp matches and compare + verify MAC in 1 pass
                    val sizeMatchingCandidates = collisionCandidates.filter { (_, existingMeta) ->
                        existingMeta.originalSize == sourceMeta.originalSize &&
                            existingMeta.mediaType == sourceMeta.mediaType &&
                            areEncryptedTimestampsEquivalent(sourceMeta, existingMeta)
                    }
                    if (sizeMatchingCandidates.isNotEmpty()) {
                        var matchOutcome = StreamCompareMacResult.MISMATCH
                        for ((existingFile, existingMeta) in sizeMatchingCandidates) {
                            matchOutcome = compareEncryptedStreamAndVerifySourceMac(
                                sourceBin = sourceBin,
                                sourceMeta = sourceMeta,
                                existingFile = existingFile,
                                existingMeta = existingMeta
                            )
                            if (matchOutcome != StreamCompareMacResult.MISMATCH) {
                                break
                            }
                        }
                        when (matchOutcome) {
                            StreamCompareMacResult.MATCH_VALID -> {
                                skippedDuplicateCount++
                                processed++
                                onProgress?.invoke(processed, total)
                                continue
                            }
                            StreamCompareMacResult.SOURCE_MAC_INVALID -> {
                                metadataCache.remove(sourceBin.absolutePath)
                                recordFailure(sourceBin)
                                processed++
                                onProgress?.invoke(processed, total)
                                continue
                            }
                            StreamCompareMacResult.MISMATCH -> Unit
                        }
                    }
                }

                // Single-pass copy + inline MAC verification before creating a new destination album
                val stagingDir = existingTarget ?: vaultRoot
                val tempFile = File(stagingDir, ".sync_tmp_${randomHex(8)}$ENCRYPTED_BIN_EXT")
                val copyVerified = copyBinAndVerifyIntegritySinglePass(sourceBin, sourceMeta, tempFile)
                if (!copyVerified || !tempFile.exists()) {
                    tempFile.delete()
                    metadataCache.remove(sourceBin.absolutePath)
                    recordFailure(sourceBin)
                    processed++
                    onProgress?.invoke(processed, total)
                    continue
                }
                integrityVerified = true

                val targetAlbum = existingTarget ?: run {
                    val created = if (item.isDefaultTarget || item.sourceAlbumName.isNullOrBlank()) {
                        getOrCreateDefaultHiddenAlbum(context)
                    } else {
                        createHiddenPhysicalAlbum(context, item.sourceAlbumName)
                    }
                    if (created != null) {
                        resolvedAlbumMap[albumKey] = created
                    }
                    created
                }

                if (targetAlbum == null) {
                    tempFile.delete()
                    recordFailure(sourceBin)
                    processed++
                    onProgress?.invoke(processed, total)
                    continue
                }

                val index = albumIndexMap.getOrPut(targetAlbum) {
                    TargetEncryptedIndex(targetAlbum)
                }

                var destBin = File(targetAlbum, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
                while (destBin.exists()) {
                    destBin = File(targetAlbum, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
                }

                val movedToDest = try {
                    java.nio.file.Files.move(
                        tempFile.toPath(),
                        destBin.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    true
                } catch (_: Exception) {
                    tempFile.renameTo(destBin)
                }

                if (!movedToDest || !destBin.exists()) {
                    tempFile.delete()
                    destBin.delete()
                    recordFailure(sourceBin)
                    processed++
                    onProgress?.invoke(processed, total)
                    continue
                }

                val exactNameTaken = index.findExactEntries(sourceMeta.originalName).isNotEmpty()
                if (exactNameTaken) {
                    val renamedName = index.getAlternativeOriginalName(sourceMeta.originalName)
                    if (renameEncryptedFile(destBin, renamedName, skipIntegrityCheck = integrityVerified, syncParentDir = false)) {
                        val updatedMeta = readMetadata(destBin, includeThumbnail = false, cacheResult = false)
                            ?: sourceMeta.copy(originalName = renamedName)
                        index.addEntry(destBin, updatedMeta)
                        albumBinNameIndex[targetAlbum]?.put(destBin.name.lowercase(Locale.ROOT), destBin)
                        touchedTargetAlbums.add(targetAlbum.absolutePath)
                        renamedCount++
                    } else {
                        metadataCache.remove(destBin.absolutePath)
                        destBin.delete()
                        recordFailure(sourceBin)
                    }
                } else {
                    destBin.setLastModified(sourceBin.lastModified())
                    index.addEntry(destBin, sourceMeta)
                    albumBinNameIndex[targetAlbum]?.put(destBin.name.lowercase(Locale.ROOT), destBin)
                    touchedTargetAlbums.add(targetAlbum.absolutePath)
                    copiedCount++
                }

                processed++
                onProgress?.invoke(processed, total)
            }
        }

        if (requirePendingManifest && pendingFile.isFile) {
            runCatching {
                if (failedManifestEntries.isEmpty()) {
                    pendingFile.delete()
                } else {
                    pendingFile.writeText(failedManifestEntries.joinToString("\n") + "\n", Charsets.UTF_8)
                }
            }
        }

        if (touchedTargetAlbums.isNotEmpty()) {
            touchedTargetAlbums.forEach { albumPath ->
                syncDir(File(albumPath))
            }
        }
        if (copiedCount + renamedCount > 0) {
            invalidateFolderCache()
            runCatching { clearAppImageCaches(context) }
        }

        return VaultSyncResult(
            copiedCount = copiedCount,
            renamedCount = renamedCount,
            skippedDuplicateCount = skippedDuplicateCount,
            failedCount = failedCount,
            syncedAlbumsCount = touchedTargetAlbums.size
        )
    }

    /**
     * Encrypts an entire folder into the master random vault root directory as a randomized subfolder:
     * 1. Creates a random subfolder inside the master vault root.
     * 2. Writes .nomedia in the subfolder.
     * 3. Writes encrypted subfolder metadata (.meta) storing the original album name with MEK.
     * 4. Encrypts all media files into random hex .bin files (transactional: NO source files are deleted if any file fails).
     * 5. Only once ALL files are verified encrypted, securely wipes the originals and deletes the plaintext folder.
     */
    enum class AlbumConflictResolution {
        MERGE,
        RENAME
    }

    data class AlbumCryptoOptions(
        val resolution: AlbumConflictResolution = AlbumConflictResolution.MERGE,
        val deleteIdenticalSource: Boolean = true
    )

    data class AlbumConflictPreview(
        val existingName: String,
        val renamedPreview: String
    )

    private fun resolveUniqueEncryptedAlbumName(vaultRoot: File, baseName: String): String {
        var idx = 1
        var candidate = "$baseName($idx)"
        while (matchExistingDestinationAlbum(vaultRoot, candidate) != null) {
            idx++
            candidate = "$baseName($idx)"
        }
        return candidate
    }

    private fun resolveUniquePlainFolder(parentDir: File, baseName: String): File {
        var idx = 1
        var candidate = File(parentDir, "$baseName($idx)")
        while (candidate.exists()) {
            idx++
            candidate = File(parentDir, "$baseName($idx)")
        }
        return candidate
    }

    fun previewEncryptAlbumConflict(context: Context, folderPath: String): AlbumConflictPreview? {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return null
        val vaultRoot = findVaultRoot(context) ?: return null
        val rawFolderName = getDecryptedFolderName(folder) ?: folder.name
        val existingAlbum = if (rawFolderName.equals(VAULT_DEFAULT_ALBUM_NAME, ignoreCase = true)) {
            findExistingDefaultHiddenAlbum(vaultRoot)
        } else {
            matchExistingDestinationAlbum(vaultRoot, rawFolderName)
        } ?: return null
        val existingName = getDecryptedFolderName(existingAlbum, context) ?: rawFolderName
        val renamedPreview = resolveUniqueEncryptedAlbumName(vaultRoot, rawFolderName)
        return AlbumConflictPreview(existingName = existingName, renamedPreview = renamedPreview)
    }

    fun previewDecryptAlbumConflict(
        context: Context,
        folderPath: String,
        targetFolderOverride: File? = null
    ): AlbumConflictPreview? {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return null
        val targetFolder = if (targetFolderOverride != null) {
            targetFolderOverride
        } else {
            val originalFolderName = getDecryptedFolderName(folder) ?: folder.name
            val picturesDir = File(context.internalStoragePath, Environment.DIRECTORY_PICTURES)
            File(picturesDir, originalFolderName)
        }
        if (!targetFolder.exists() || !targetFolder.isDirectory) return null
        val parent = targetFolder.parentFile ?: return null
        val renamed = resolveUniquePlainFolder(parent, targetFolder.name)
        return AlbumConflictPreview(existingName = targetFolder.name, renamedPreview = renamed.name)
    }

    fun encryptFolder(
        context: Context,
        folderPath: String,
        options: AlbumCryptoOptions = AlbumCryptoOptions(),
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String? {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return folderPath

        if (unlockedMasterKey == null) {
            return null
        }

        val rawFolderName = getDecryptedFolderName(folder) ?: folder.name
        val vaultRoot = findVaultRoot(context) ?: return null
        val vaultNoMedia = File(vaultRoot, NOMEDIA)
        if (!vaultNoMedia.exists()) {
            try { vaultNoMedia.createNewFile() } catch (_: Exception) {}
        }

        val isExplicitlyIncluded = folder.absolutePath.isThisOrParentIncluded(context.config.includedFolders)
        val isHiddenParent = !isExplicitlyIncluded && (
            folder.absolutePath.contains("/.") ||
                folder.absolutePath.doesThisOrParentHaveNoMedia(HashMap(), null)
            )
        val shouldAddNoMedia = ExcludedMenuPolicy.shouldAddNoMediaOnEncrypt(
            folderName = folder.name,
            decryptedName = rawFolderName,
            hasNoMediaFile = File(folder, NOMEDIA).exists(),
            isPathOrParentHidden = isHiddenParent
        )

        val existingAlbum = if (rawFolderName.equals(VAULT_DEFAULT_ALBUM_NAME, ignoreCase = true)) {
            findExistingDefaultHiddenAlbum(vaultRoot)
        } else {
            matchExistingDestinationAlbum(vaultRoot, rawFolderName)
        }
        val shouldMergeIntoExisting = existingAlbum != null && options.resolution == AlbumConflictResolution.MERGE
        val targetAlbumExistedBefore = shouldMergeIntoExisting
        val originalFolderName = if (shouldMergeIntoExisting) {
            existingAlbum?.let { getDecryptedFolderName(it, context) } ?: rawFolderName
        } else if (existingAlbum != null) {
            resolveUniqueEncryptedAlbumName(vaultRoot, rawFolderName)
        } else {
            rawFolderName
        }

        // Only formats supported by the gallery are encrypted; other files (pdf, md, ...) are left untouched.
        // Process pre-numbered (n) variants before base names so colliding base files cannot steal "(1)" slots.
        val filesToEncrypt = (folder.listFiles()?.filter { isEncryptableMediaFile(it) } ?: emptyList())
            .sortedWith(compareBy<File>({ stripCollisionIndex(it.name) == it.name }, { it.name }))

        val subfolder = if (shouldMergeIntoExisting) {
            existingAlbum!!
        } else {
            var candidate = File(vaultRoot, randomHex(6))
            while (candidate.exists()) {
                candidate = File(vaultRoot, randomHex(6))
            }
            candidate
        }
        if (!subfolder.exists() && !subfolder.mkdirs()) return null

        if (shouldAddNoMedia) {
            val noMedia = File(subfolder, NOMEDIA)
            if (!noMedia.exists()) {
                try { noMedia.createNewFile() } catch (_: Exception) {}
            }
        }

        if (!File(subfolder, VAULT_SUBFOLDER_META_FILE).exists() &&
            !File(subfolder, ENCRYPTED_FOLDER_META_FILE).exists()
        ) {
            writeEncryptedFolderMeta(context, subfolder, originalFolderName)
        }

        val total = filesToEncrypt.size
        onProgress?.invoke(0, total)

        val preExistingBinPaths = subfolder.listFiles()?.mapTo(HashSet()) { it.absolutePath } ?: emptySet()
        val subfolderIndex = if (unlockedMasterKey != null) TargetEncryptedIndex(subfolder, preExistingBinPaths) else null
        val newlyCreatedBins = ArrayList<File>()
        val encryptedPairs = ArrayList<Pair<File, File>>()
        var failed = false

        for ((index, file) in filesToEncrypt.withIndex()) {
            val enc = encryptFileToDirectory(
                context = context,
                sourceFile = file,
                targetDir = subfolder,
                wipeSource = false,
                clearCachesNow = false,
                targetIndex = subfolderIndex
            )
            if (enc != null && enc.exists()) {
                encryptedPairs.add(Pair(file, enc))
                if (enc.absolutePath !in preExistingBinPaths) {
                    newlyCreatedBins.add(enc)
                }
            } else {
                failed = true
                break
            }
            onProgress?.invoke(((index + 1) * 7) / 10, total)
        }

        // ROLLBACK IF ENCRYPTION FAILED ON ANY FILE
        if (failed || encryptedPairs.size != filesToEncrypt.size) {
            if (targetAlbumExistedBefore) {
                newlyCreatedBins.forEach { createdBin ->
                    metadataCache.remove(createdBin.absolutePath)?.fileKey?.fill(0)
                    runCatching { createdBin.delete() }
                }
            } else {
                subfolder.deleteRecursively()
            }
            return null
        }

        // ALL FILES ENCRYPTED SUCCESSFULLY! WIPE AND DELETE ORIGINAL MEDIA FILES:
        val wipedPaths = ArrayList<String>(encryptedPairs.size)
        val wipedFileNames = HashSet<String>(encryptedPairs.size)
        val touchedAlbumIds = LinkedHashSet<Long>()
        var favoritesChanged = false
        val encBaseProgress = (total * 7) / 10
        val wipeWeight = total - encBaseProgress
        for ((index, pair) in encryptedPairs.withIndex()) {
            val (sourceFile, encFile) = pair
            val isNewlyEncrypted = encFile.absolutePath !in preExistingBinPaths
            if (isNewlyEncrypted || options.deleteIdenticalSource) {
                val sourcePath = sourceFile.absolutePath
                val plainIdentity = runCatching { MediaIdentity.ofWithFolder(context, sourceFile) }.getOrNull()
                runCatching {
                    val res = context.markReferencesOnEncrypt(
                        sourcePath = sourcePath,
                        encPath = encFile.absolutePath,
                        plaintextIdentity = plainIdentity,
                        markPlaintextDormant = true,
                        saveBackups = false
                    )
                    touchedAlbumIds.addAll(res.affectedAlbumIds)
                    if (res.favoritesChanged) favoritesChanged = true
                }
                if (secureWipeAndDeleteFile(sourceFile, context, syncParentDir = false, purgeTraces = false)) {
                    wipedPaths.add(sourcePath)
                    wipedFileNames.add(sourceFile.name)
                }
            }
            val wipeProgress = if (encryptedPairs.isNotEmpty()) {
                encBaseProgress + ((index + 1) * wipeWeight) / encryptedPairs.size
            } else {
                total
            }
            onProgress?.invoke(wipeProgress, total)
        }
        runCatching { syncDir(folder) }

        for (albumId in touchedAlbumIds) {
            runCatching { VirtualAlbumSyncManager.saveAlbumToFile(context, albumId) }
        }
        if (favoritesChanged) {
            runCatching { ReferenceBackupManager.saveFavorites(context) }
        }

        // Batch-purge MediaStore, Room DB, and thumbnail traces once for all wiped files
        if (wipedPaths.isNotEmpty()) {
            val cleanFolderPath = folder.absolutePath.trimEnd('/')
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Files.getContentUri("external"),
                    "${MediaStore.MediaColumns.DATA} LIKE ?",
                    arrayOf("$cleanFolderPath/%")
                )
                MediaScannerConnection.scanFile(context, wipedPaths.toTypedArray(), null, null)
            }

            runCatching {
                context.mediaDB.deleteMediaFromPath(cleanFolderPath)
                GalleryDatabase.getInstance(context.applicationContext).DateTakensDao().deleteDateTakensFromPath(cleanFolderPath)
            }

            runCatching {
                val dcimThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), ".thumbnails")
                val picThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ".thumbnails")
                val parentThumbnails = File(folder, ".thumbnails")
                listOf(dcimThumbnails, picThumbnails, parentThumbnails).forEach { tDir ->
                    if (tDir.exists() && tDir.isDirectory) {
                        tDir.listFiles()?.forEach { thumbFile ->
                            if (wipedFileNames.any { name -> thumbFile.name.contains(name) }) {
                                secureWipeAndDeleteFile(thumbFile, null, syncParentDir = false, purgeTraces = false)
                            }
                        }
                    }
                }
            }
        }

        // Preserve any existing .bin files that were already encrypted in this folder
        val existingEncryptedFiles = folder.listFiles()?.filter { file ->
            file.isFile && isEncryptedFile(file.name)
        } ?: emptyList()
        for (encFile in existingEncryptedFiles) {
            if (unlockedMasterKey != null) {
                copyOrMoveEncryptedFileToDirectory(context, encFile, subfolder, isMove = true, targetIndex = subfolderIndex)
            } else {
                var dest = File(subfolder, encFile.name)
                while (dest.exists()) {
                    dest = File(subfolder, "${randomHex(8)}$ENCRYPTED_BIN_EXT")
                }
                val oldEncPath = encFile.absolutePath
                if (encFile.renameTo(dest)) {
                    runCatching { context.updateVirtualAlbumItemPath(oldEncPath, dest.absolutePath) }
                    metadataCache.remove(oldEncPath)?.let { meta ->
                        metadataCache[dest.absolutePath] = meta
                    }
                }
            }
        }

        // Move subfolders directly to internal storage (if any)
        val internalStorageRoot = File(context.internalStoragePath)
        val movedSubfolderPaths = ArrayList<String>()

        val childFiles = folder.listFiles() ?: emptyArray()
        for (child in childFiles) {
            if (child.isDirectory) {
                val cleanName = child.name.trimStart('.')
                var targetSubfolder = File(internalStorageRoot, if (cleanName.isEmpty()) "Subfolder" else cleanName)
                var collisionIndex = 1
                while (targetSubfolder.exists()) {
                    targetSubfolder = File(internalStorageRoot, "${if (cleanName.isEmpty()) "Subfolder" else cleanName}_$collisionIndex")
                    collisionIndex++
                }
                val moved = if (child.renameTo(targetSubfolder)) {
                    true
                } else {
                    try {
                        child.copyRecursively(targetSubfolder, overwrite = false) && child.deleteRecursively()
                    } catch (_: Exception) {
                        false
                    }
                }
                if (moved) {
                    val noMediaInSub = File(targetSubfolder, NOMEDIA)
                    if (noMediaInSub.exists()) {
                        try { noMediaInSub.delete() } catch (_: Exception) {}
                        try {
                            context.contentResolver.delete(
                                MediaStore.Files.getContentUri("external"),
                                "${MediaStore.Files.FileColumns.DATA} = ?",
                                arrayOf(noMediaInSub.absolutePath)
                            )
                        } catch (_: Exception) {}
                    }
                    runCatching { context.directoryDB.deleteDirPath(child.absolutePath) }
                    movedSubfolderPaths.add(targetSubfolder.absolutePath)
                }
            }
        }

        if (movedSubfolderPaths.isNotEmpty()) {
            val pathsToRescan = ArrayList<String>()
            for (path in movedSubfolderPaths) {
                pathsToRescan.add(path)
                File(path).walkTopDown().filter { it.isFile }.forEach {
                    pathsToRescan.add(it.absolutePath)
                }
            }
            runCatching { context.rescanPaths(pathsToRescan) }
        }

        // Delete original plaintext folder - but only if nothing besides leftovers of ours remains in it.
        // Files the gallery can't display (pdf, md, ...) were intentionally NOT encrypted and must survive.
        runCatching {
            val leftoverUnsupportedFiles = folder.listFiles()?.any { child ->
                child.name != NOMEDIA && (child.isFile || child.isDirectory)
            } == true

            if (!leftoverUnsupportedFiles) {
                val oldNoMedia = File(folder, NOMEDIA)
                if (!oldNoMedia.exists()) {
                    try { oldNoMedia.createNewFile() } catch (_: Exception) {}
                }

                runCatching {
                    val contentResolver = context.contentResolver
                    val filesUri = MediaStore.Files.getContentUri("external")
                    contentResolver.delete(
                        filesUri,
                        "${MediaStore.MediaColumns.DATA} LIKE ?",
                        arrayOf("${folder.absolutePath}%")
                    )
                }

                folder.listFiles()?.forEach { child ->
                    try { child.delete() } catch (_: Exception) {}
                }

                var deletedDir = folder.delete()
                if (!deletedDir) {
                    deletedDir = folder.deleteRecursively()
                }
                if (!deletedDir) {
                    deletedDir = runCatching { context.tryFastDocumentDelete(folder.absolutePath, true) }.getOrDefault(false)
                }
            }
        }

        runCatching { context.config.removeEncryptedFolder(folder.absolutePath) }
        runCatching { context.config.addEncryptedFolder(subfolder.absolutePath) }
        if (!folder.exists()) {
            runCatching { context.config.replaceExactExcludedFolder(folder.absolutePath, subfolder.absolutePath) }
        }
        runCatching { context.directoryDB.deleteDirPath(folder.absolutePath) }
        folderNameCache[subfolder.absolutePath] = originalFolderName
        encryptedFolderCache[folder.absolutePath] = false
        encryptedFolderCache[subfolder.absolutePath] = true
        runCatching { clearAppImageCaches(context) }

        return subfolder.absolutePath
    }

    /**
     * Decrypts an encrypted subfolder back to plaintext inside Pictures/<originalFolderName>,
     * or into [targetFolderOverride] when specified by the user.
     */
    fun decryptFolder(
        context: Context,
        folderPath: String,
        targetFolderOverride: File? = null,
        options: AlbumCryptoOptions = AlbumCryptoOptions(),
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String? {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return folderPath
        if (!isVaultUnlocked) return folderPath

        val hadNoMedia = File(folder, NOMEDIA).exists()
        val initialTarget = if (targetFolderOverride != null) {
            targetFolderOverride
        } else {
            val originalFolderName = getDecryptedFolderName(folder) ?: folder.name
            val picturesDir = File(context.internalStoragePath, Environment.DIRECTORY_PICTURES)
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            var candidate = File(picturesDir, originalFolderName)
            var idx = 1
            while (candidate.exists() && !candidate.isDirectory) {
                candidate = File(picturesDir, "$originalFolderName($idx)")
                idx++
            }
            candidate
        }
        val targetFolder = if (initialTarget.exists() && initialTarget.isDirectory && options.resolution == AlbumConflictResolution.RENAME) {
            val parent = initialTarget.parentFile ?: initialTarget
            resolveUniquePlainFolder(parent, initialTarget.name)
        } else {
            initialTarget
        }
        val targetExistedBefore = targetFolder.exists()
        if (!targetExistedBefore && !targetFolder.mkdirs()) return null

        // Process pre-numbered (n) variants before base names so colliding base files do not steal "(1)" names.
        val rawFilesToDecrypt = folder.listFiles()?.filter { it.isFile && isEncryptedFile(it.name) } ?: emptyList()
        val origNameByPath = HashMap<String, String>(rawFilesToDecrypt.size)
        for (file in rawFilesToDecrypt) {
            origNameByPath[file.absolutePath] = readMetadata(file, includeThumbnail = false, cacheResult = false)?.originalName ?: file.name
        }
        val filesToDecrypt = rawFilesToDecrypt.sortedWith(
            compareBy<File>(
                {
                    val orig = origNameByPath[it.absolutePath] ?: it.name
                    stripCollisionIndex(orig) == orig
                },
                { origNameByPath[it.absolutePath] ?: it.name },
                { it.name }
            )
        )

        val total = filesToDecrypt.size
        onProgress?.invoke(0, total)

        val preExistingPlainPaths = targetFolder.listFiles()?.mapTo(HashSet()) { it.absolutePath } ?: emptySet()
        val targetPlainIndex = TargetPlainIndex(targetFolder, preExistingPlainPaths)
        val newlyCreatedPlainFiles = ArrayList<File>()
        val decryptedPairs = ArrayList<Pair<File, File>>()
        var failed = false

        for ((index, file) in filesToDecrypt.withIndex()) {
            val decrypted = decryptFileToDirectory(
                context = context,
                encryptedFile = file,
                targetDir = targetFolder,
                deleteEncryptedSource = false,
                targetIndex = targetPlainIndex
            )
            if (decrypted != null && decrypted.exists()) {
                decryptedPairs.add(Pair(file, decrypted))
                if (decrypted.absolutePath !in preExistingPlainPaths) {
                    newlyCreatedPlainFiles.add(decrypted)
                }
            } else {
                failed = true
                break
            }
            onProgress?.invoke(index + 1, total)
        }

        // ROLLBACK IF DECRYPTION FAILED
        if (failed || decryptedPairs.size != filesToDecrypt.size) {
            newlyCreatedPlainFiles.forEach { decryptedFile ->
                try { decryptedFile.delete() } catch (_: Exception) {}
            }
            if (!targetExistedBefore && targetFolder.listFiles().isNullOrEmpty()) {
                try { targetFolder.deleteRecursively() } catch (_: Exception) {}
            }
            return null
        }

        // ALL FILES DECRYPTED! Now delete encrypted files:
        val touchedAlbumIds = LinkedHashSet<Long>()
        var favoritesChanged = false
        for ((encFile, decryptedFile) in decryptedPairs) {
            val isNewlyDecrypted = decryptedFile.absolutePath !in preExistingPlainPaths
            if (!isNewlyDecrypted && !options.deleteIdenticalSource) {
                continue
            }
            val encPath = encFile.absolutePath
            runCatching {
                val res = context.markReferencesOnDecrypt(
                    encPath = encPath,
                    decryptedFile = decryptedFile,
                    hideStealthReferences = true,
                    saveBackups = false
                )
                touchedAlbumIds.addAll(res.affectedAlbumIds)
                if (res.favoritesChanged) favoritesChanged = true
            }
            metadataCache.remove(encPath)?.fileKey?.fill(0)
            runCatching { context.mediaDB.deleteMediumPath(encPath) }
            encFile.delete()
        }
        for (albumId in touchedAlbumIds) {
            runCatching { VirtualAlbumSyncManager.saveAlbumToFile(context, albumId) }
        }
        if (favoritesChanged) {
            runCatching { ReferenceBackupManager.saveFavorites(context) }
        }

        val hasRemainingEncryptedFiles = folder.listFiles()?.any { it.isFile && isEncryptedFile(it.name) } == true
        if (!hasRemainingEncryptedFiles) {
            runCatching {
                val contentResolver = context.contentResolver
                val filesUri = MediaStore.Files.getContentUri("external")
                contentResolver.delete(
                    filesUri,
                    "${MediaStore.MediaColumns.DATA} LIKE ?",
                    arrayOf("${folder.absolutePath}%")
                )
            }
            runCatching {
                if (!folder.deleteRecursively()) {
                    (context as? BaseSimpleActivity)?.deleteFileBg(
                        folder.toFileDirItem(context),
                        allowDeleteFolder = true,
                        isDeletingMultipleFiles = false
                    )
                }
            }
            folderNameCache.remove(folder.absolutePath)

            runCatching { context.config.removeEncryptedFolder(folder.absolutePath) }
            runCatching { context.config.replaceExactExcludedFolder(folder.absolutePath, targetFolder.absolutePath) }
            runCatching { context.directoryDB.deleteDirPath(folder.absolutePath) }
            encryptedFolderCache[folder.absolutePath] = false
        }
        encryptedFolderCache[targetFolder.absolutePath] = false

        if (hadNoMedia) {
            val targetNoMedia = File(targetFolder, NOMEDIA)
            if (!targetNoMedia.exists()) {
                try {
                    targetNoMedia.createNewFile()
                } catch (_: Exception) {}
            }
            if (targetNoMedia.exists()) {
                runCatching {
                    val content = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.TITLE, NOMEDIA)
                        put(MediaStore.Files.FileColumns.DATA, targetNoMedia.absolutePath)
                        put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_NONE)
                    }
                    context.contentResolver.insert(MediaStore.Files.getContentUri("external"), content)
                }
            }
        }

        val isHiddenTarget = hadNoMedia || targetFolder.name.startsWith('.')
        if (!isHiddenTarget) {
            val restoredPaths = targetFolder.listFiles()?.filter { it.isFile }?.map { it.absolutePath } ?: emptyList()
            if (restoredPaths.isNotEmpty()) {
                runCatching { context.rescanPaths(ArrayList(restoredPaths)) }
            }
        } else if (hadNoMedia) {
            val targetNoMedia = File(targetFolder, NOMEDIA)
            if (targetNoMedia.exists()) {
                runCatching { context.rescanPaths(arrayListOf(targetNoMedia.absolutePath)) }
            }
        }
        return targetFolder.absolutePath
    }

    // ==================== ON-THE-FLY HEADER & STREAM DECRYPTION ====================

    fun readMetadata(
        file: File,
        includeThumbnail: Boolean = true,
        cacheResult: Boolean = true
    ): EncryptedFileMetadata? {
        metadataCache[file.absolutePath]?.let { return it }
        val masterKey = unlockedMasterKey ?: return null
        if (!file.exists() || !file.isFile) return null

        return try {
            FileInputStream(file).use { fis ->
                DataInputStream(BufferedInputStream(fis, 8192)).use { dis ->
                    val magicBytes = ByteArray(8)
                    dis.readFully(magicBytes)
                    val magicStr = String(magicBytes, Charsets.US_ASCII)
                    if (magicStr != MAGIC_V2 && magicStr != MAGIC_V1) return null

                    val version = dis.readByte()
                    val fileKey: ByteArray = when (version) {
                        VERSION_SYMMETRIC, VERSION_SYMMETRIC_AUTH, VERSION_V5_AUTH -> masterKey.copyOf()
                        else -> return null
                    }

                    val headerIv = ByteArray(GCM_IV_LENGTH)
                    dis.readFully(headerIv)

                    val encHeaderLen = dis.readInt()
                    if (encHeaderLen <= 0 || encHeaderLen > 5 * 1024 * 1024) {
                        fileKey.fill(0)
                        return null
                    }

                    val encHeader = ByteArray(encHeaderLen)
                    dis.readFully(encHeader)

                    val contentIv = ByteArray(CTR_IV_LENGTH)
                    dis.readFully(contentIv)

                    val isMacAuth = (version == VERSION_SYMMETRIC_AUTH || version == VERSION_V5_AUTH)
                    val headerKey = if (isMacAuth) {
                        deriveSubkey(fileKey, DOMAIN_HEADER_KEY)
                    } else {
                        fileKey
                    }

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(headerKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, headerIv))
                    val plainHeader = cipher.doFinal(encHeader)
                    if (isMacAuth) {
                        headerKey.fill(0)
                    }

                    DataInputStream(ByteArrayInputStream(plainHeader)).use { hdis ->
                        val originalName = hdis.readUTF()
                        val lastModified = hdis.readLong()
                        val dateTaken = hdis.readLong()
                        val originalSize = hdis.readLong()
                        val mediaType = hdis.readInt()
                        val videoDuration = hdis.readInt()
                        val thumbBytes = if (includeThumbnail) {
                            try {
                                if (hdis.available() >= 4) {
                                    val thumbLen = hdis.readInt()
                                    if (thumbLen in 1..(5 * 1024 * 1024) && hdis.available() >= thumbLen) {
                                        val tb = ByteArray(thumbLen)
                                        hdis.readFully(tb)
                                        tb
                                    } else null
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                        val payloadOffset = (8 + 1 + GCM_IV_LENGTH + 4 + encHeaderLen + CTR_IV_LENGTH).toLong()

                        val meta = EncryptedFileMetadata(
                            originalName = originalName,
                            lastModified = lastModified,
                            dateTaken = dateTaken,
                            originalSize = originalSize,
                            mediaType = mediaType,
                            videoDuration = videoDuration,
                            payloadOffset = payloadOffset,
                            contentIv = contentIv,
                            fileKey = fileKey,
                            version = version,
                            isMacAuthenticated = isMacAuth,
                            thumbnailBytes = thumbBytes
                        )
                        if (cacheResult && includeThumbnail) {
                            metadataCache[file.absolutePath] = meta
                        }
                        meta
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun readEmbeddedThumbnailBytes(file: File): ByteArray? {
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

    fun incrementCounter(baseIv: ByteArray, blockOffset: Long): ByteArray {
        if (blockOffset == 0L) return baseIv.copyOf()
        val counterInt = BigInteger(1, baseIv).add(BigInteger.valueOf(blockOffset))
        val bytes = counterInt.toByteArray()
        val result = ByteArray(CTR_IV_LENGTH)
        val copyLen = minOf(bytes.size, CTR_IV_LENGTH)
        System.arraycopy(bytes, bytes.size - copyLen, result, CTR_IV_LENGTH - copyLen, copyLen)
        return result
    }

    fun openDecryptedContentStream(
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
    fun verifyFileIntegrity(
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

    // ==================== ANTI-RECOVERY / SECURE WIPE ====================

    private fun getPendingWipeJournalFile(context: Context): File {
        return File(context.filesDir, "pending_wipe_journal.txt")
    }

    private fun appendPendingWipe(context: Context, path: String) {
        try {
            val file = getPendingWipeJournalFile(context)
            file.appendText(path + "\n", Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun removePendingWipe(context: Context, path: String) {
        try {
            val file = getPendingWipeJournalFile(context)
            if (!file.exists()) return
            val lines = file.readLines(Charsets.UTF_8).filter { it.trim().isNotEmpty() && it.trim() != path }
            if (lines.isEmpty()) {
                secureWipeAndDeleteFile(file, null)
            } else {
                file.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
            }
        } catch (_: Exception) {}
    }

    fun processPendingWipeJournal(context: Context) {
        ensureBackgroundThread {
            try {
                val file = getPendingWipeJournalFile(context)
                if (!file.exists()) return@ensureBackgroundThread
                val lines = file.readLines(Charsets.UTF_8)
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        val target = File(trimmed)
                        if (target.exists()) {
                            secureWipeAndDeleteFile(target, context)
                        }
                    }
                }
                secureWipeAndDeleteFile(file, null)
            } catch (_: Exception) {}
        }
    }

    fun purgeMediaTracesPreservingReferences(context: Context, plaintextPath: String) {
        try {
            val contentResolver = context.contentResolver
            val filesUri = MediaStore.Files.getContentUri("external")

            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(plaintextPath)
            var mediaId: Long? = null
            contentResolver.query(filesUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    mediaId = cursor.getLong(0)
                }
            }

            if (mediaId != null) {
                try {
                    contentResolver.delete(
                        MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Images.Thumbnails.IMAGE_ID} = ?",
                        arrayOf(mediaId.toString())
                    )
                } catch (_: Exception) {}
                try {
                    contentResolver.delete(
                        MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Video.Thumbnails.VIDEO_ID} = ?",
                        arrayOf(mediaId.toString())
                    )
                } catch (_: Exception) {}
            }

            contentResolver.delete(filesUri, selection, selectionArgs)
            MediaScannerConnection.scanFile(context, arrayOf(plaintextPath), null, null)
        } catch (_: Exception) {}

        try {
            context.mediaDB.deleteMediumPath(plaintextPath)
            GalleryDatabase.getInstance(context.applicationContext).DateTakensDao().deleteDateTaken(plaintextPath)
        } catch (_: Exception) {}

        try {
            val fileName = plaintextPath.substringAfterLast('/')
            val parent = plaintextPath.substringBeforeLast('/')
            val dcimThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), ".thumbnails")
            val picThumbnails = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ".thumbnails")
            val parentThumbnails = File(parent, ".thumbnails")
            listOf(dcimThumbnails, picThumbnails, parentThumbnails).forEach { tDir ->
                if (tDir.exists() && tDir.isDirectory) {
                    tDir.listFiles { f -> f.name.contains(fileName) }?.forEach { thumbFile ->
                        secureWipeAndDeleteFile(thumbFile, null)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun purgeTracesForFile(context: Context, plaintextPath: String) {
        purgeMediaTracesPreservingReferences(context, plaintextPath)

        try {
            val hadFav = context.favoritesDB.getFavorite(plaintextPath) != null
            context.favoritesDB.deleteFavoritePath(plaintextPath)
            if (hadFav) {
                ReferenceBackupManager.saveFavorites(context)
            }
            // The extension also rewrites the backup files, so they stay in sync with the database.
            context.deleteVirtualAlbumItemFromAllAlbums(plaintextPath)
        } catch (_: Exception) {}
    }

    fun purgeFolderTraces(context: Context, folderPath: String) {
        val cleanPath = folderPath.trimEnd('/')
        try {
            context.deleteDBPaths(emptyList(), listOf(cleanPath))
            context.directoryDB.deleteDirPath(cleanPath)
            context.directoryDB.deleteDirPrefix(cleanPath)
            val db = GalleryDatabase.getInstance(context.applicationContext)
            db.DateTakensDao().deleteDateTakensFromPath(cleanPath)
            db.DateTakensDao().deleteDateTakensByPrefix(cleanPath)
            GalleryDatabase.scrubDatabase(context)
        } catch (_: Exception) {}

        try {
            context.config.scrubFolderPreferences(cleanPath)
        } catch (_: Exception) {}

        try {
            val contentResolver = context.contentResolver
            val filesUri = MediaStore.Files.getContentUri("external")
            contentResolver.delete(
                filesUri,
                "${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.DATA} = ?",
                arrayOf("$cleanPath/%", cleanPath)
            )
            MediaScannerConnection.scanFile(context, arrayOf(cleanPath), null, null)
        } catch (_: Exception) {}

        try {
            val recycleBin = File(context.filesDir, RECYCLE_BIN)
            if (recycleBin.exists() && recycleBin.isDirectory) {
                recycleBin.listFiles()?.forEach { binFile ->
                    if (binFile.absolutePath.contains(cleanPath)) {
                        secureWipeAndDeleteFile(binFile, null)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun clearAppImageCaches(context: Context) {
        val appContext = context.applicationContext
        ensureBackgroundThread {
            try {
                Glide.get(appContext).clearDiskCache()
            } catch (_: Exception) {}
            try {
                val cacheDirs = listOfNotNull(
                    File(appContext.cacheDir, "image_manager_disk_cache"),
                    File(appContext.cacheDir, "picasso-cache"),
                    File(appContext.cacheDir, "glide"),
                    appContext.externalCacheDir,
                    File(appContext.cacheDir, TEMP_FOLDER_NAME)
                )
                for (dir in cacheDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkBottomUp().forEach { f ->
                            if (f.isFile) {
                                secureWipeFileInternal(f)
                                f.delete()
                            } else if (f.isDirectory) {
                                f.delete()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    Glide.get(appContext).clearMemory()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun secureWipeAndDeleteFile(context: Context, file: File): Boolean {
        return secureWipeAndDeleteFile(file, context)
    }

    fun secureWipeAndDeleteFile(
        file: File,
        context: Context? = null,
        syncParentDir: Boolean = true,
        purgeTraces: Boolean = true
    ): Boolean {
        if (!file.exists()) return true
        val originalPath = file.absolutePath

        val wiped = secureWipeFileInternal(file)
        if (!wiped) {
            return false
        }

        var deleted = file.delete()
        if (!deleted && context != null) {
            deleted = context.tryFastDocumentDelete(originalPath, false)
        }

        if (syncParentDir) {
            syncDir(file.parentFile)
        }

        if (purgeTraces && context != null) {
            runCatching { purgeTracesForFile(context, originalPath) }
        }

        return deleted
    }

    private fun secureWipeFileInternal(file: File): Boolean {
        return try {
            val length = file.length()
            if (length > 0L) {
                val bufSize = 1024 * 1024 // 1 MiB chunk
                val buf = ByteArray(bufSize)
                RandomAccessFile(file, "rw").use { raf ->
                    val ch = raf.channel
                    var rem = length
                    ch.position(0L)
                    while (rem > 0L) {
                        val toWrite = minOf(rem, bufSize.toLong()).toInt()
                        secureRandom.nextBytes(buf)
                        val byteBuf = java.nio.ByteBuffer.wrap(buf, 0, toWrite)
                        while (byteBuf.hasRemaining()) {
                            ch.write(byteBuf)
                        }
                        rem -= toWrite
                    }
                    ch.force(true)
                    ch.truncate(0L)
                    ch.force(true)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun syncDir(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val fd = android.system.Os.open(dir.absolutePath, android.system.OsConstants.O_RDONLY, 0)
                try {
                    android.system.Os.fsync(fd)
                } finally {
                    android.system.Os.close(fd)
                }
            }
        } catch (_: Throwable) {
        }
    }
}