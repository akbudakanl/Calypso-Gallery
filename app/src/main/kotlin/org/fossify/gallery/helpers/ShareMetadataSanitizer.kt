package org.fossify.gallery.helpers

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import org.fossify.gallery.extensions.AllNonDimensionExifAttributes
import org.fossify.gallery.extensions.config
import java.io.File

/**
 * Creates sanitized *copies* of outgoing images. The original is never modified, so the user's
 * own library keeps its capture date and camera details. Only the temporary copy handed to the
 * share intent is cleaned.
 *
 * Location-only mode removes the GPS block; all-metadata mode reuses the fork's existing
 * [AllNonDimensionExifAttributes] tag list. Orientation is deliberately not in that list, so the
 * shared photo still displays upright.
 */
object ShareMetadataSanitizer {

    const val SHARE_TEMP_DIR = "images/share_clean"

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

        val tempDir = File(context.cacheDir, SHARE_TEMP_DIR)
        runCatching {
            if (!tempDir.exists()) tempDir.mkdirs()
            File(tempDir, ".nomedia").createNewFile()
        }

        return paths.map { path -> sanitizeOne(path, tempDir, attributes) }
    }

    private fun sanitizeOne(path: String, tempDir: File, attributes: List<String>): String {
        // Content URIs and non-image files have no file-backed EXIF we can safely rewrite.
        if (path.startsWith("content://") || path.startsWith("file://")) return path
        if (path.substringAfterLast('.', "").lowercase() !in SANITIZABLE_EXTENSIONS) return path

        val source = File(path)
        if (!source.isFile) return path

        var target: File? = null
        return try {
            target = File(tempDir, "share_${System.currentTimeMillis()}_${source.name}")
            source.copyTo(target!!, overwrite = true)
            val exif = ExifInterface(target!!.absolutePath)
            // AndroidX ExifInterface removes an attribute when its value is set to null.
            attributes.forEach { tag -> runCatching { exif.setAttribute(tag, null) } }
            exif.saveAttributes()
            target!!.absolutePath
        } catch (_: Throwable) {
            // Some formats (notably HEIF/RAW on certain APIs) cannot be rewritten in place.
            // Fall back to sharing the untouched original rather than breaking the share.
            target?.delete()
            path
        }
    }

    /** Called on app start so temporary share copies never accumulate. */
    fun clearTemp(context: Context) {
        runCatching { File(context.cacheDir, SHARE_TEMP_DIR).deleteRecursively() }
    }
}
