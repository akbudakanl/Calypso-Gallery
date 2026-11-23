package org.fossify.gallery.helpers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.fossify.gallery.extensions.AllNonDimensionExifAttributes
import org.fossify.gallery.extensions.config
import java.io.File
import java.io.IOException

/** A metadata-free copy could not be produced; sharing must not continue silently. */
class ShareMetadataSanitizationException : IOException("Unable to create a metadata-free share copy")

/**
 * Creates sanitized *copies* of outgoing images. The original is never modified, so the user's
 * own library keeps its capture date and camera details. Only the copy handed to the share
 * intent is cleaned.
 *
 * Location-only mode removes the GPS block; all-metadata mode reuses the fork's existing
 * [AllNonDimensionExifAttributes] tag list. Orientation is deliberately not in that list, so the
 * shared photo still displays upright.
 *
 * IMPORTANT: the cleaned copy must NOT be shared from the app's private internal cache. Several
 * share targets (notably AOSP Messaging/MMS and a number of chat apps) reject a
 * `content://.../cache/...` URI because it resolves into `/data/data/<pkg>` and is considered a
 * private file. With mode OFF the original lives on public storage and commons resolves it to a
 * MediaStore URI, which every target accepts. To keep the same level of compatibility with
 * metadata removal enabled, the cleaned copy is written directly into MediaStore under
 * [MEDIA_RELATIVE_PATH] and its **public file path** is returned. Commons then resolves that path
 * back to a public `content://media/...` URI via `getMediaContentUri()`.
 *
 * The bytes are streamed straight from the source into the MediaStore entry (a single copy) and
 * EXIF is stripped in place through that entry. No intermediate cache copy is created. Published
 * entries are recorded and removed on a later app start ([clearTemp]) so the user's gallery is
 * not permanently polluted with share copies.
 */
object ShareMetadataSanitizer {

    /** Legacy location, kept only so old cache copies are cleaned up on app start. */
    private const val SHARE_TEMP_DIR = "images/share_clean"

    private const val MEDIA_RELATIVE_PATH = "Pictures/Calypso/Shared"
    private const val PREFS_NAME = "share_metadata_sanitizer"
    private const val PENDING_MEDIA_URIS = "pending_media_uris"
    private const val NOMEDIA_CREATED = "nomedia_created"

    /**
     * Grace period for slow uploads and for target apps that open the URI only when the user
     * finally sends. Once the copy is deleted, already-open file descriptors stay readable, but
     * a later re-open would fail - hence the generous window. Leftovers are also removed on the
     * next app start, so this only affects short-term visibility in the gallery.
     */
    private const val CLEANUP_DELAY_MS = 30 * 60 * 1000L

    private val SANITIZABLE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun sanitizedPaths(context: Context, paths: List<String>): List<String> {
        val mode = context.config.shareMetadataMode
        if (mode == SHARE_METADATA_MODE_OFF || paths.isEmpty()) return paths

        val attributes = when (mode) {
            SHARE_METADATA_MODE_LOCATION -> AllNonDimensionExifAttributes.filter { it.startsWith("GPS") }
            SHARE_METADATA_MODE_ALL -> AllNonDimensionExifAttributes
            else -> return paths
        }
        if (attributes.isEmpty()) return paths

        return paths.map { path -> sanitizeOne(context, path, attributes) }
    }

    private fun sanitizeOne(context: Context, path: String, attributes: List<String>): String {
        // Content URIs and non-image files have no file-backed EXIF we can safely rewrite.
        if (path.startsWith("content://") || path.startsWith("file://")) return path
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension !in SANITIZABLE_EXTENSIONS) return path

        val source = File(path)
        if (!source.isFile) return path

        return try {
            val published = publishToMediaStore(context, source, extension)
                ?: throw ShareMetadataSanitizationException()
            try {
                stripExif(context, published.first, published.second, attributes)
            } catch (_: Throwable) {
                // Never hand out a copy whose metadata was not actually removed.
                runCatching { context.contentResolver.delete(published.second, null, null) }
                throw ShareMetadataSanitizationException()
            }
            recordPendingUri(context, published.second)
            scheduleCleanup(context.applicationContext, published.second)
            published.first
        } catch (e: ShareMetadataSanitizationException) {
            throw e
        } catch (_: Throwable) {
            throw ShareMetadataSanitizationException()
        }
    }

    /**
     * Creates a MediaStore image, streams [source] into it exactly once and returns its public
     * file path together with the MediaStore URI (needed for EXIF editing and cleanup).
     */
    private fun publishToMediaStore(
        context: Context,
        source: File,
        extension: String
    ): Pair<String, Uri>? {
        // Hide the folder BEFORE the image is inserted: gallery apps that respect .nomedia
        // then never show the temporary copy, even for short-lived entries.
        ensureNoMediaMarker(context)

        val resolver = context.contentResolver
        val displayName = "calypso_shared_${System.nanoTime()}_${source.name}"
        val mimeType = when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, MEDIA_RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("MediaStore returned no output stream")
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
        } else {
            @Suppress("DEPRECATION")
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Calypso/Shared"
            )
            if ((!publicDir.exists() && !publicDir.mkdirs()) || !publicDir.canWrite()) return null
            val publicFile = File(publicDir, displayName)
            if (!runCatching { source.copyTo(publicFile, overwrite = true) }.isSuccess) return null

            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, publicFile.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                publicFile.delete()
                null
            } else {
                publicFile.absolutePath to uri
            }
        }
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

    /** One-time hidden marker for the dedicated share folder. Never deleted with the copies. */
    private fun ensureNoMediaMarker(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(NOMEDIA_CREATED, false)) return

        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, ".nomedia")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, MEDIA_RELATIVE_PATH)
            }
            runCatching {
                context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) != null
            }.getOrDefault(false)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Calypso/Shared"
            )
            runCatching {
                (dir.exists() || dir.mkdirs()) && File(dir, ".nomedia").createNewFile()
            }.getOrDefault(false)
        }
        if (created) prefs.edit().putBoolean(NOMEDIA_CREATED, true).apply()
    }

    private fun recordPendingUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet(PENDING_MEDIA_URIS, null) ?: emptySet()) +
            "${uri}|${System.currentTimeMillis()}"
        prefs.edit().putStringSet(PENDING_MEDIA_URIS, updated).apply()
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
