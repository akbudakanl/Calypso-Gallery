package org.fossify.gallery.helpers.media

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.HIGH_TILE_DPI
import org.fossify.gallery.helpers.LOW_TILE_DPI
import org.fossify.gallery.helpers.NORMAL_TILE_DPI
import org.fossify.gallery.helpers.WEIRD_TILE_DPI
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal object PhotoSwipeOptimizer {
    const val ZOOMABLE_VIEW_IDLE_DELAY_MS = 250L

    private const val ROTATION_90 = 90
    private const val ROTATION_180 = 180
    private const val ROTATION_270 = 270
    private const val JPEG_CACHE_QUALITY = 100
    private const val HIGH_DPI_THRESHOLD = 400
    private const val NORMAL_DPI_THRESHOLD = 300
    private val WEIRD_DEVICES = setOf("motorola xt1685", "google nexus 5x")
    private val ANIMATED_CHECK_EXTENSIONS = setOf("gif", "png", "apng", "webp")

    fun isDefinitelyStaticByPath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("content://") || CryptoManager.isEncryptedFile(path)) {
            return false
        }
        val clean = path.substringBefore('?').substringBefore('#')
        val dot = clean.lastIndexOf('.')
        if (dot < 0 || dot == clean.lastIndex) return false
        val ext = clean.substring(dot + 1).lowercase(Locale.US)
        return ext.isNotEmpty() && ext !in ANIMATED_CHECK_EXTENSIONS
    }

    fun resolveGlideTargetDimensions(
        screenW: Int,
        screenH: Int,
        showHighestQuality: Boolean,
    ): Pair<Int, Int>? {
        if (showHighestQuality || screenW <= 0 || screenH <= 0) {
            return null
        }
        return screenW to screenH
    }

    fun resolveMinTileDpi(context: Context): Int {
        val metrics = context.resources.displayMetrics
        val averageDpi = (metrics.xdpi + metrics.ydpi) / 2
        val device = "${Build.BRAND} ${Build.MODEL}".lowercase(Locale.getDefault())
        return when {
            device in WEIRD_DEVICES -> WEIRD_TILE_DPI
            averageDpi > HIGH_DPI_THRESHOLD -> HIGH_TILE_DPI
            averageDpi > NORMAL_DPI_THRESHOLD -> NORMAL_TILE_DPI
            else -> LOW_TILE_DPI
        }
    }

    fun applyProperColorMode(activity: Activity?, drawable: Drawable?, isFragmentVisible: Boolean) {
        if (!isFragmentVisible || activity == null) return
        ColorModeHelper.setColorModeForImage(
            activity = activity,
            bitmap = (drawable as? BitmapDrawable)?.bitmap,
            ultraHdr = activity.config.ultraHdrRendering,
        )
    }

    fun reapplyColorModeIfNeeded(
        activity: Activity?,
        wasInit: Boolean,
        isFragmentVisible: Boolean,
        drawable: Drawable?,
        isGesturesViewVisible: Boolean,
    ) {
        if (!wasInit || !isFragmentVisible) return
        if (drawable != null && isGesturesViewVisible) {
            applyProperColorMode(activity, drawable, true)
        } else {
            ColorModeHelper.resetColorMode(activity)
        }
    }

    fun shouldKeepAnimatedPlaying(
        isFragmentVisible: Boolean,
        isResumed: Boolean,
        isPagerScrolling: Boolean,
    ): Boolean {
        return isResumed && (isFragmentVisible || isPagerScrolling)
    }

    fun rotationDegreesToExifOrientation(degrees: Int): Int {
        return when (degrees) {
            ROTATION_90 -> ExifInterface.ORIENTATION_ROTATE_90
            ROTATION_180 -> ExifInterface.ORIENTATION_ROTATE_180
            ROTATION_270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }
    }

    fun cachedExifOrientation(path: String): Int? {
        if (path.isEmpty() || path.startsWith("content://")) return null
        val file = File(path.removePrefix("file://"))
        val meta = IsolatedMetadataCache.get(file, CryptoManager.isEncryptedFile(path)) ?: return null
        if (meta.width <= 0 || meta.height <= 0) return null
        return rotationDegreesToExifOrientation(meta.rotationDegrees)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun readImageOrientation(context: Context, path: String): Int {
        var orient = ExifInterface.ORIENTATION_NORMAL
        try {
            if (CryptoManager.isEncryptedFile(path)) {
                CryptoManager.openDecryptedContentStream(File(path))?.use { stream ->
                    orient = ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            } else if (path.startsWith("content:/") || context.isPathOnOTG(path)) {
                val uri = if (path.startsWith("content:/")) Uri.parse(path) else Uri.fromFile(File(path))
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    orient = ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            } else {
                orient = ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (_: Exception) {
        } catch (_: OutOfMemoryError) {
        }
        return orient
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun cacheEmptyContentUriToFile(context: Context, rawUri: String): String? {
        return try {
            val uri = Uri.parse(rawUri)
            val rotated = IsolatedDecoderClient.decodeUri(
                context = context,
                uri = uri,
                maxWidth = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
                maxHeight = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
                flags = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
            ) ?: return null
            val file = File(context.externalCacheDir, uri.lastPathSegment ?: "cached_photo.jpg")
            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_CACHE_QUALITY, out)
            }
            rotated.recycle()
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
