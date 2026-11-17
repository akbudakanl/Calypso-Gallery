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
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_FORMAT_VERSION
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
    val thumbnailBytes: ByteArray? = null,
    // ---- kanonik (Tink streaming AEAD) alanlari ----
    val fileId: ByteArray? = null,
    val keyId: Short = -1,
    val ciphertextLength: Long = 0L,
    val isCanonicalFormat: Boolean = false,
    /** Tink segment boyutu (header'dan okunur; sabit değil). */
    val segmentSize: Int = 0
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
    const val VAULT_RECYCLE_BIN_NAME = "\$\$VAULT_RECYCLE_BIN\$\$"

    const val VAULT_RESTORE_INFO_FILE = ".restore_info"

    const val TIMESTAMP_EQUIVALENCE_TOLERANCE_MS = 2_000L


    // Cryptographic Key Separation Domains & MAC Specifications
    // -> org.fossify.gallery.helpers.vault.VaultKeyDerivation
    //    (alan adlari ve deriveSubkey orada tanimlidir)

    // Argon2id Parameters (Memory-Hard Key Derivation)
    internal const val ARGON2_MEMORY_KB = 65536 // 64 MB RAM cost

    internal const val ARGON2_ITERATIONS = 3

    internal const val ARGON2_PARALLELISM = 2

    internal const val ARGON2_SALT_LENGTH = 16

    internal const val KEY_LENGTH_BITS = 256

    internal const val THUMB_MAX_DIMENSION = 320

    internal const val THUMB_JPEG_QUALITY = 75


    internal val secureRandom = SecureRandom()


    @Volatile
    internal var unlockedMasterKey: ByteArray? = null

    /**
     * kanonik kasada keyring'den çözülen KEK.
     * [activeKek] eski MEK'i (unlockedMasterKey) kullanir.
     */
    @Volatile
    internal var unlockedKek: ByteArray? = null

    /** Dosya basliklarini sifrelemek icin aktif KEK. */
    internal fun activeKek(): ByteArray? = unlockedKek ?: unlockedMasterKey

    internal val metadataCache = ConcurrentHashMap<String, EncryptedFileMetadata>()

    internal val folderNameCache = ConcurrentHashMap<String, String>()

    internal val encryptedFolderCache = ConcurrentHashMap<String, Boolean>()


    @Volatile
    internal var cachedVaultRoot: File? = null

    @Volatile
    var vaultRecycleBinFolder: File? = null


    internal val activeCryptoOperations = AtomicInteger(0)

    internal val pendingLock = AtomicBoolean(false)


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
        unlockedKek?.fill(0)
        unlockedKek = null
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


    // ==================== VAULT EXISTENCE: THE .vault HEADER ON DISK IS THE SINGLE SOURCE OF TRUTH ====================

    internal const val DEEP_SCAN_INTERVAL_MS = 5_000L


    @Volatile
    internal var lastDeepScanMs = 0L


    /** True while setupOrUpdateCredentials() runs, so a concurrent check never wipes half-written state. */
    @Volatile
    internal var vaultSetupInProgress = false


    @VisibleForTesting
    internal var otgMountBaseOverrideForTest: List<File>? = null


    // ==================== KEY MANAGEMENT (ARGON2id + AES-256-GCM) ====================

    internal const val ANDROID_KEYSTORE = "AndroidKeyStore"

    internal const val BIOMETRIC_KEYSTORE_ALIAS = "vault_biometric_hw_key"


    internal fun isEncryptedBinFile(file: File): Boolean {
        return file.isFile && !file.name.startsWith(".") && file.name.endsWith(ENCRYPTED_BIN_EXT, ignoreCase = true)
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


    internal const val FAST_SAMPLING_THRESHOLD_BYTES = 8L * 1024L * 1024L

    internal const val FAST_SAMPLING_CHUNK_BYTES = 256 * 1024


    internal enum class StreamCompareMacResult {
        MATCH_VALID,
        SOURCE_MAC_INVALID,
        MISMATCH
    }


    internal data class SyncPendingItem(
        val sourceBin: File,
        val sourceAlbumName: String?,
        val isDefaultTarget: Boolean
    )


    enum class TransferConflictStatus {
        NO_CONFLICT,
        IDENTICAL_SKIP,
        NAME_CONFLICT
    }


    data class TransferConflictCheck(
        val status: TransferConflictStatus,
        val displayName: String? = null,
        val existingFile: File? = null
    )


    enum class SmartConflictDifference {
        CONTENT_DIFFERS,
        DATE_TAKEN_DIFFERS,
        MODIFIED_DATE_DIFFERS,
        BOTH_DATES_DIFFER,
        IDENTICAL_MATCH
    }


    data class SmartConflictComparison(
        val difference: SmartConflictDifference,
        val sourceSize: Long,
        val existingSize: Long,
        val sourceLastModified: Long,
        val existingLastModified: Long,
        val sourceDateTaken: Long,
        val existingDateTaken: Long,
        val hasDistinctDateTaken: Boolean
    )


    data class VaultExportResult(
        val exportedFilesCount: Int,
        val failedFilesCount: Int,
        val exportDirectory: File
    )


    internal const val PENDING_SYNC_FILE_NAME = ".pending_sync"


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
        val deleteIdenticalSource: Boolean = true,
        val conflictResolutions: Map<String, Int> = emptyMap(),
        val skipDuplicateCheck: Boolean = false,
        val precheckedNonIdenticalPaths: Set<String> = emptySet(),
        val deferredSkippedSourceCollector: MutableList<File>? = null,
        val deferredIdenticalTargetMap: MutableMap<String, File>? = null
    )


    data class AlbumConflictPreview(
        val existingName: String,
        val renamedPreview: String
    )


    // ==================== ON-THE-FLY HEADER & STREAM DECRYPTION ====================

    fun readMetadata(
        file: File,
        includeThumbnail: Boolean = true,
        cacheResult: Boolean = true
    ): EncryptedFileMetadata? {
        metadataCache[file.absolutePath]?.let { return it }
        val kek = activeKek() ?: return null
        if (!file.exists() || !file.isFile) return null

        return try {
            FileInputStream(file).use { fis ->
                DataInputStream(BufferedInputStream(fis, 8192)).use { dis ->
                    val magicBytes = ByteArray(VaultContainer.VAULT_MAGIC.length)
                    dis.readFully(magicBytes)
                    if (String(magicBytes, Charsets.US_ASCII) != VaultContainer.VAULT_MAGIC) return null

                    val version = dis.readByte()
                    if (version != VaultContainer.VAULT_FORMAT_VERSION) return null

                    val meta = readContainerMetadata(dis, kek, version, includeThumbnail) ?: return null
                    if (cacheResult && includeThumbnail) {
                        metadataCache[file.absolutePath] = meta
                    }
                    meta
                }
            }
        } catch (_: Exception) {
            null
        }
    }

}
