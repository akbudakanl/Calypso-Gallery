package org.fossify.gallery.helpers.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

internal fun probeAvifBounds(bytes: ByteArray): Pair<Int, Int>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    probePlatformBounds(bytes)?.let { return it }
    val decoded = decodeWithImageDecoder(
        bytes,
        IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
        IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
    ) ?: return null
    return try {
        decoded.width to decoded.height
    } finally {
        decoded.recycle()
    }
}

internal fun decodeAvif(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return decodeWithImageDecoder(bytes, targetW, targetH)
}

internal fun probeSvgBounds(bytes: ByteArray): Pair<Int, Int>? {
    return runCatching {
        val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
        resolveSvgDimensions(svg)
    }.getOrNull()
}

internal fun decodeSvg(bytes: ByteArray, targetW: Int, targetH: Int): Bitmap? {
    return runCatching {
        val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
        val (srcW, srcH) = resolveSvgDimensions(svg)
        val ratio = minOf(
            targetW.toFloat() / srcW.toFloat(),
            targetH.toFloat() / srcH.toFloat(),
        )
        val outW = (srcW * ratio).roundToInt().coerceIn(1, IsolatedFormatSniffer.MAX_SVG_DIMENSION)
        val outH = (srcH * ratio).roundToInt().coerceIn(1, IsolatedFormatSniffer.MAX_SVG_DIMENSION)
        if (svg.documentViewBox == null && svg.documentWidth > 0f && svg.documentHeight > 0f) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also { bmp ->
            svg.renderToCanvas(Canvas(bmp))
        }
    }.getOrNull()
}

private fun resolveSvgDimensions(svg: SVG): Pair<Int, Int> {
    val docW = svg.documentWidth
    val docH = svg.documentHeight
    if (docW > 0f && docH > 0f) {
        return docW.roundToInt().coerceAtLeast(1) to docH.roundToInt().coerceAtLeast(1)
    }
    val box = svg.documentViewBox
    if (box != null && box.width() > 0f && box.height() > 0f) {
        return box.width().roundToInt().coerceAtLeast(1) to box.height().roundToInt().coerceAtLeast(1)
    }
    return IsolatedFormatSniffer.DEFAULT_SVG_DIMENSION to IsolatedFormatSniffer.DEFAULT_SVG_DIMENSION
}
