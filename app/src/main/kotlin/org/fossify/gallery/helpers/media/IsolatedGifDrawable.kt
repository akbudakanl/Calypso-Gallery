package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import java.io.Closeable

/**
 * Software double-buffered `Drawable` displaying GIF frames rendered in [IsolatedMediaDecoderService]
 * with look-ahead prefetching into the back buffer.
 */
class IsolatedGifDrawable internal constructor(
    appContext: Context,
    sessionId: Long,
    private val meta: GifMetadata,
    slots: Array<IsolatedGifBufferTransport.ReadableGifSlot>,
    bitmaps: Array<Bitmap>,
) : Drawable(), Animatable, Closeable {

    internal data class GifMetadata(
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val initialDurationMs: Int,
        val channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
    )

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val coordinator = IsolatedGifPrefetcher(
        appContext = appContext,
        sessionId = sessionId,
        meta = meta,
        slots = slots,
        bitmaps = bitmaps,
        onInvalidate = ::invalidateSelf,
    )

    override fun draw(canvas: Canvas) {
        val bitmap = coordinator.currentBitmap() ?: return
        if (!bitmap.isRecycled) {
            canvas.drawBitmap(bitmap, null, bounds, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = meta.width

    override fun getIntrinsicHeight(): Int = meta.height

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override fun isRunning(): Boolean = coordinator.isRunning()

    override fun close() = coordinator.close()
}
