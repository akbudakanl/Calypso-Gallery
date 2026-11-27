package org.fossify.gallery.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder
import org.fossify.gallery.helpers.media.IsolatedRegionClient

class PicassoRegionDecoder(
    val showHighestQuality: Boolean,
    val screenWidth: Int,
    val screenHeight: Int,
    val minTileDpi: Int
) : ImageRegionDecoder {
    private var appContext: Context? = null
    private var sessionId: Long = -1L

    @Volatile
    private var recycled = false
    private val decoderLock = Any()

    override fun init(context: Context, uri: Uri): Point {
        val appCtx = context.applicationContext
        appContext = appCtx
        val newUri = Uri.parse(uri.toString().replace("%", "%25").replace("#", "%23"))
        val opened = checkNotNull(IsolatedRegionClient.openRegionSession(appCtx, newUri)) {
            "Isolated region decoder failed to open $newUri"
        }
        sessionId = opened.sessionId
        return Point(opened.width, opened.height)
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap {
        synchronized(decoderLock) {
            val ctx = checkNotNull(appContext) { "Region decoder is not initialized" }
            check(!recycled && sessionId > 0L) { "Region decoder is recycled" }
            var newSampleSize = sampleSize
            if (!showHighestQuality && minTileDpi == LOW_TILE_DPI) {
                val wideMismatch = rect.width() > rect.height() && screenWidth > screenHeight
                val tallMismatch = rect.height() > rect.width() && screenHeight > screenWidth
                val exceedsScreen = rect.width() / sampleSize > screenWidth ||
                    rect.height() / sampleSize > screenHeight
                if ((wideMismatch || tallMismatch) && exceedsScreen) {
                    newSampleSize *= 2
                }
            }

            return checkNotNull(
                IsolatedRegionClient.decodeRegionTile(ctx, sessionId, rect, newSampleSize),
            ) { "Region decoder returned null bitmap - image format may not be supported" }
        }
    }

    override fun isReady(): Boolean = !recycled && sessionId > 0L

    override fun recycle() {
        val idToClose: Long
        val ctx: Context?
        synchronized(decoderLock) {
            recycled = true
            idToClose = sessionId
            sessionId = -1L
            ctx = appContext
        }
        if (ctx != null && idToClose > 0L) {
            IsolatedRegionClient.closeRegionSession(ctx, idToClose)
        }
    }
}
