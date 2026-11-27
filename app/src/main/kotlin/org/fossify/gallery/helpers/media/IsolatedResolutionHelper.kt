package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Point
import android.net.Uri
import org.fossify.gallery.helpers.MediaDimensions
import java.io.File

/**
 * Probes image dimensions via [IsolatedDecoderClient], applying EXIF orientation swaps so callers
 * never invoke `fossify-commons`'s in-process `BitmapFactory` helpers on images.
 */
fun Context.probeIsolatedImageResolution(path: String): Point? {
    val bounds = if (path.startsWith("content://")) {
        IsolatedDecoderClient.probeUriBounds(this, Uri.parse(path))
    } else {
        IsolatedDecoderClient.probeFileBounds(this, File(path.removePrefix("file://")))
    } ?: return null

    if (bounds.width <= 0 || bounds.height <= 0) return null
    return if (MediaDimensions.flipsSides(bounds.rotationDegrees)) {
        Point(bounds.height, bounds.width)
    } else {
        Point(bounds.width, bounds.height)
    }
}
