package org.fossify.gallery.helpers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.fossify.gallery.extensions.AllNonDimensionExifAttributes
import org.fossify.gallery.extensions.config
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom

/** A metadata-free copy could not be produced; sharing must not continue silently. */
class ShareMetadataSanitizationException : IOException("Unable to create a metadata-free share copy")

/**
 * Creates sanitized *copies* of outgoing images and videos. The original is never modified, so
 * the user's own library keeps its capture date and camera details. Only the copy handed to the
 * share intent is cleaned.
 *
 * Location-only mode removes the GPS block for images (and container metadata for MP4/MOV);
 * all-metadata mode reuses [AllNonDimensionExifAttributes], strips JPEG/PNG/WebP container
 * metadata chunks via [ImageContainerMetadataStripper], and anonymizes the shared filename.
 * Orientation is deliberately preserved so shared media still displays upright.
 */
object ShareMetadataSanitizer {

    /** Legacy location, kept only so old cache copies are cleaned up on app start. */
    private const val SHARE_TEMP_DIR = "images/share_clean"

    private const val MEDIA_RELATIVE_PATH = "Pictures/Calypso/Shared"
    private const val VIDEO_MEDIA_RELATIVE_PATH = "Movies/Calypso/Shared"
    private const val PREFS_NAME = "share_metadata_sanitizer"
    private const val PENDING_MEDIA_URIS = "pending_media_uris"
    private const val NOMEDIA_CREATED = "nomedia_created"
    private const val RANDOM_NAME_BYTES = 4

    /**
     * Grace period for slow uploads and for target apps that open the URI only when the user
     * finally sends. Once the copy is deleted, already-open file descriptors stay readable, but
     * a later re-open would fail - hence the generous window. Leftovers are also removed on the
     * next app start, so this only affects short-term visibility in the gallery.
     */
    private const val CLEANUP_DELAY_MS = 30 * 60 * 1000L

    private val SANITIZABLE_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val SANITIZABLE_VIDEO_EXTENSIONS = setOf("mp4", "mov")
    private val secureRandom = SecureRandom()

    @Suppress("DEPRECATION")
    fun getPublicShareDirectory(): File {
        val pictures = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        }.getOrNull()
        return if (pictures != null) {
            File(pictures, "Calypso/Shared")
        } else {
            File(Environment.getExternalStorageDirectory(), MEDIA_RELATIVE_PATH)
        }
    }

    @Suppress("DEPRECATION")
    fun getPublicVideoShareDirectory(): File {
        val movies = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }.getOrNull()
        return if (movies != null) {
            File(movies, "Calypso/Shared")
        } else {
            File(Environment.getExternalStorageDirectory(), VIDEO_MEDIA_RELATIVE_PATH)
        }
    }

    fun isShareTempPath(path: String): Boolean {
        val clean = path.trimEnd('/').replace('\\', '/')
        if (clean.isEmpty()) return false
        return matchesRelativeSharePath(clean, MEDIA_RELATIVE_PATH) ||
            matchesRelativeSharePath(clean, VIDEO_MEDIA_RELATIVE_PATH)
    }

    private fun matchesRelativeSharePath(cleanPath: String, relativePath: String): Boolean =
        cleanPath.endsWith("/$relativePath", ignoreCase = true) ||
            cleanPath.contains("/$relativePath/", ignoreCase = true)

    fun sanitizedPaths(context: Context, paths: List<String>): List<String> {
        val mode = context.config.shareMetadataMode
        if (mode == SHARE_METADATA_MODE_OFF || paths.isEmpty()) return paths

        val attributes = when (mode) {
            SHARE_METADATA_MODE_LOCATION -> AllNonDimensionExifAttributes.filter { it.startsWith("GPS") }
            SHARE_METADATA_MODE_ALL -> AllNonDimensionExifAttributes
            else -> return paths
        }
        if (attributes.isEmpty()) return paths

        return paths.map { path -> sanitizeOne(context, path, mode, attributes) }
    }

    internal fun buildShareDisplayName(sourceName: String, extension: String, mode: Int): String {
        val bytes = ByteArray(RANDOM_NAME_BYTES)
        secureRandom.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        if (mode == SHARE_METADATA_MODE_ALL) {
            return "$hex.$extension"
        }
        val baseName = sourceName.substringBeforeLast('.', sourceName).ifEmpty { "file" }
        return "${baseName}_$hex.$extension"
    }

    private fun sanitizeOne(
        context: Context,
        path: String,
        mode: Int,
        attributes: List<String>
    ): String {
        // Content URIs and unsupported formats have no file-backed metadata we can safely rewrite.
        if (path.startsWith("content://") || path.startsWith("file://")) return path
        val extension = path.substringAfterLast('.', "").lowercase()
        val isImage = extension in SANITIZABLE_IMAGE_EXTENSIONS
        val isVideo = extension in SANITIZABLE_VIDEO_EXTENSIONS
        if (!isImage && !isVideo) return path

        val source = File(path)
        if (!source.isFile) return path

        return try {
            val displayName = buildShareDisplayName(source.name, extension, mode)
            val published = if (isVideo) {
                publishVideoToMediaStore(context, source, extension, displayName)
            } else {
                publishToMediaStore(context, source, extension, mode, displayName)?.also { pair ->
                    stripImageExifOrDelete(context, pair, attributes)
                }
            }
            requireNotNull(published) { "Failed to publish sanitized media copy" }

            finalizeOrDelete(context, published.second)
            recordPendingUri(context, published.second)
            scheduleCleanup(context.applicationContext, published.second)
            // Return a bare content://media URI, NOT a file path. Commons resolves a public path
            // by querying MediaStore's deprecated DATA column; when that fails on modern Android
            // it falls back to our own FileProvider URI, which messaging apps reject as a private
            // file. Do not append query parameters either: receivers derive the filename/MIME
            // from the URI and a query string makes them show the numeric _ID instead.
            published.second.toString()
        } catch (e: ShareMetadataSanitizationException) {
            throw e
        } catch (_: Throwable) {
            throw ShareMetadataSanitizationException()
        }
    }

    private fun finalizeOrDelete(context: Context, uri: Uri) {
        try {
            // CRITICAL: a pending MediaStore entry is invisible to other apps. Without this,
            // receivers only see the numeric _ID as the filename and cannot open the media.
            finalizeMediaStoreEntry(context, uri)
        } catch (_: Throwable) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw ShareMetadataSanitizationException()
        }
    }

    private fun stripImageExifOrDelete(
        context: Context,
        published: Pair<String, Uri>,
        attributes: List<String>
    ) {
        try {
            stripExif(context, published.first, published.second, attributes)
        } catch (_: Throwable) {
            // Never hand out a copy whose metadata was not actually removed.
            runCatching { context.contentResolver.delete(published.second, null, null) }
            throw ShareMetadataSanitizationException()
        }
    }

    private fun publishToMediaStore(
        context: Context,
        source: File,
        extension: String,
        mode: Int,
        displayName: String
    ): Pair<String, Uri>? {
        ensureNoMediaMarker(context)
        val mimeType = when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishImageModern(context, source, extension, mode, displayName, mimeType)
        } else {
            publishImageLegacy(context, source, extension, mode, displayName, mimeType)
        }
    }

    private fun publishImageModern(
        context: Context,
        source: File,
        extension: String,
        mode: Int,
        displayName: String,
        mimeType: String
    ): Pair<String, Uri>? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, MEDIA_RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                copyImageStream(source, output, extension, mode)
            } ?: error("MediaStore returned no output stream")
            if (!File(getPublicShareDirectory(), ".nomedia").exists()) {
                ensureNoMediaMarker(context)
            }
            @Suppress("DEPRECATION")
            val publicPath = File(
                Environment.getExternalStorageDirectory(),
                "$MEDIA_RELATIVE_PATH/$displayName"
            ).absolutePath
            publicPath to uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun publishImageLegacy(
        context: Context,
        source: File,
        extension: String,
        mode: Int,
        displayName: String,
        mimeType: String
    ): Pair<String, Uri>? {
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Calypso/Shared"
        )
        if ((!publicDir.exists() && !publicDir.mkdirs()) || !publicDir.canWrite()) return null
        val publicFile = File(publicDir, displayName)
        val copied = runCatching {
            publicFile.outputStream().use { output -> copyImageStream(source, output, extension, mode) }
        }.isSuccess
        if (!copied) {
            publicFile.delete()
            return null
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, publicFile.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            publicFile.delete()
            return null
        }
        return publicFile.absolutePath to uri
    }

    private fun copyImageStream(
        source: File,
        output: OutputStream,
        extension: String,
        mode: Int
    ) {
        source.inputStream().use { input ->
            if (mode == SHARE_METADATA_MODE_ALL) {
                ImageContainerMetadataStripper.copyStripped(input, output, extension)
            } else {
                input.copyTo(output)
            }
        }
    }

    private fun publishVideoToMediaStore(
        context: Context,
        source: File,
        extension: String,
        displayName: String
    ): Pair<String, Uri>? {
        val videoDir = getPublicVideoShareDirectory()
        ensureNoMediaMarker(context, videoDir, VIDEO_MEDIA_RELATIVE_PATH)
        val mimeType = if (extension == "mov") "video/quicktime" else "video/mp4"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishVideoModern(context, source, videoDir, displayName, mimeType)
        } else {
            publishVideoLegacy(context, source, videoDir, displayName, mimeType)
        }
    }

    private fun publishVideoModern(
        context: Context,
        source: File,
        videoDir: File,
        displayName: String,
        mimeType: String
    ): Pair<String, Uri>? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_MEDIA_RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                VideoMetadataSanitizer.remuxWithoutMetadata(source, pfd)
            } ?: error("MediaStore returned no file descriptor")
            if (!File(videoDir, ".nomedia").exists()) {
                ensureNoMediaMarker(context, videoDir, VIDEO_MEDIA_RELATIVE_PATH)
            }
            @Suppress("DEPRECATION")
            val publicPath = File(
                Environment.getExternalStorageDirectory(),
                "$VIDEO_MEDIA_RELATIVE_PATH/$displayName"
            ).absolutePath
            publicPath to uri
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun publishVideoLegacy(
        context: Context,
        source: File,
        videoDir: File,
        displayName: String,
        mimeType: String
    ): Pair<String, Uri>? {
        if ((!videoDir.exists() && !videoDir.mkdirs()) || !videoDir.canWrite()) return null
        val publicFile = File(videoDir, displayName)
        val remuxed = runCatching {
            ParcelFileDescriptor.open(
                publicFile,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE
            ).use { pfd ->
                VideoMetadataSanitizer.remuxWithoutMetadata(source, pfd)
            }
        }.isSuccess
        if (!remuxed) {
            publicFile.delete()
            return null
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DATA, publicFile.absolutePath)
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            publicFile.delete()
            return null
        }
        return publicFile.absolutePath to uri
    }

    /** Strips EXIF from the published copy itself, without creating another full copy. */
    private fun stripExif(context: Context, publicPath: String, uri: Uri, attributes: List<String>) {
        val strip: (ExifInterface) -> Unit = { exif ->
            attributes.forEach { tag -> runCatching { exif.setAttribute(tag, null) } }
            exif.saveAttributes()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.openFileDescriptor(uri, "rw").use { pfd ->
                requireNotNull(pfd)
                strip(ExifInterface(pfd.fileDescriptor))
            }
        } else {
            strip(ExifInterface(publicPath))
        }
    }

    /** Makes the published entry visible to other apps once writing and EXIF cleanup are done. */
    private fun finalizeMediaStoreEntry(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val rows = context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
        require(rows > 0) { "Failed to publish MediaStore entry" }
    }

    /** One-time hidden marker for a dedicated share folder. Never deleted with the copies. */
    private fun ensureNoMediaMarker(
        context: Context,
        dir: File = getPublicShareDirectory(),
        relativePath: String = MEDIA_RELATIVE_PATH
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val noMediaFile = File(dir, ".nomedia")
        if (noMediaFile.exists()) {
            if (!prefs.getBoolean(NOMEDIA_CREATED, false)) {
                prefs.edit().putBoolean(NOMEDIA_CREATED, true).apply()
            }
            return
        }

        val directCreated = runCatching {
            (dir.exists() || dir.mkdirs()) && (noMediaFile.exists() || noMediaFile.createNewFile())
        }.getOrDefault(false)

        val fallbackCreated = if (!directCreated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, ".nomedia")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            runCatching {
                context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) != null
            }.getOrDefault(false)
        } else {
            false
        }
        if (directCreated || fallbackCreated) {
            prefs.edit().putBoolean(NOMEDIA_CREATED, true).apply()
        }
    }

    private fun recordPendingUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet(PENDING_MEDIA_URIS, null) ?: emptySet()) +
            "${uri}|${System.currentTimeMillis()}"
        // Called on the background share thread: persist synchronously so a killed process
        // cannot leave behind an untracked MediaStore copy.
        prefs.edit().putStringSet(PENDING_MEDIA_URIS, updated).commit()
    }

    private fun scheduleCleanup(context: Context, uri: Uri) {
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { context.contentResolver.delete(uri, null, null) }
            removePendingUri(context, uri)
        }, CLEANUP_DELAY_MS)
    }

    private fun removePendingUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = prefs.getStringSet(PENDING_MEDIA_URIS, null).orEmpty()
            .filterNot { it.substringBefore('|') == uri.toString() }
            .toSet()
        prefs.edit().putStringSet(PENDING_MEDIA_URIS, updated).apply()
    }

    /** Called on app start so copies left behind by a killed process are removed as well. */
    fun clearTemp(context: Context) {
        if (getPublicShareDirectory().exists()) {
            ensureNoMediaMarker(context)
        }
        val videoDir = getPublicVideoShareDirectory()
        if (videoDir.exists()) {
            ensureNoMediaMarker(context, videoDir, VIDEO_MEDIA_RELATIVE_PATH)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getStringSet(PENDING_MEDIA_URIS, null).orEmpty().forEach { entry ->
            runCatching { context.contentResolver.delete(Uri.parse(entry.substringBefore('|')), null, null) }
        }
        prefs.edit().remove(PENDING_MEDIA_URIS).apply()

        listOfNotNull(
            File(context.cacheDir, SHARE_TEMP_DIR),
            context.externalCacheDir?.let { File(it, SHARE_TEMP_DIR) },
            context.getExternalFilesDir(null)?.let { File(it, SHARE_TEMP_DIR) }
        ).forEach { dir -> runCatching { dir.deleteRecursively() } }
    }
}
