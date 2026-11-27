package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.File

/**
 * Main-process client for isolated `BitmapRegionDecoder` tile sessions.
 */
object IsolatedRegionClient {
    fun openRegionSession(context: Context, uri: Uri): IsolatedRegionOpenResult? {
        if (ProcessIsolation.isIsolatedProcess()) return null
        val rawPath = uri.path ?: uri.toString().removePrefix("file://")
        val appCtx = context.applicationContext
        val key = "region:$uri"
        if (IsolatedDecoderClient.quarantine.isQuarantined(key)) return null

        val request = if (CryptoManager.isEncryptedFile(rawPath)) {
            buildEncryptedRegionRequest(File(rawPath))
        } else {
            buildPlainRegionRequest(appCtx, uri, rawPath)
        } ?: return null

        return request.use { req ->
            IsolatedServiceBridge.runWatchdogCall(appCtx, key, IsolatedDecoderClient.quarantine) { decoder ->
                val res = decoder.openRegionSession(req)
                if (res != null && res.status == IsolatedDecodeResult.STATUS_OK && res.sessionId > 0L) {
                    res
                } else {
                    null
                }
            }
        }
    }

    fun decodeRegionTile(
        context: Context,
        sessionId: Long,
        rect: Rect,
        sampleSize: Int,
    ): Bitmap? {
        if (ProcessIsolation.isIsolatedProcess() || sessionId <= 0L) return null
        val appCtx = context.applicationContext
        return IsolatedServiceBridge.runWatchdogCall(appCtx, "", IsolatedDecoderClient.quarantine) { decoder ->
            decoder.decodeRegionTile(
                sessionId,
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                sampleSize,
            )?.use { res ->
                if (res.status == IsolatedDecodeResult.STATUS_OK) {
                    IsolatedPixelTransport.unpackBitmap(res)
                } else {
                    null
                }
            }
        }
    }

    fun closeRegionSession(context: Context, sessionId: Long) {
        if (ProcessIsolation.isIsolatedProcess() || sessionId <= 0L) return
        IsolatedServiceBridge.runFireAndForget(context.applicationContext) { decoder ->
            decoder.closeRegionSession(sessionId)
        }
    }

    private fun buildEncryptedRegionRequest(file: File): IsolatedDecodeRequest? {
        if (!CryptoManager.isVaultUnlocked) return null
        val plainBytes = runCatching {
            CryptoManager.openDecryptedContentStream(file)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        return try {
            val shm = IsolatedPixelTransport.packInputBytes("calypso-reg", plainBytes)
            val pipe = if (shm == null) IsolatedPixelTransport.packInputPipe(plainBytes) else null
            if (shm == null && pipe == null) {
                null
            } else {
                IsolatedDecodeRequest(
                    inputPfd = pipe,
                    sharedInputHandle = shm,
                    inputLength = plainBytes.size.toLong(),
                    nameHint = file.name,
                )
            }
        } finally {
            plainBytes.fill(0)
        }
    }

    private fun buildPlainRegionRequest(context: Context, uri: Uri, rawPath: String): IsolatedDecodeRequest? {
        val file = File(rawPath)
        val pfd = if (uri.scheme == "file" || (uri.scheme == null && file.isFile)) {
            runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        } else {
            runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        } ?: return null
        return IsolatedDecodeRequest(
            inputPfd = pfd,
            inputLength = pfd.statSize.coerceAtLeast(0L),
            nameHint = uri.lastPathSegment ?: file.name,
        )
    }
}
