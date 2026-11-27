package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.fossify.gallery.helpers.CryptoManager
import java.io.File

/**
 * Main-process client for isolated `BitmapRegionDecoder` tile sessions.
 */
object IsolatedRegionClient {
    fun isVaultUri(uri: Uri): Boolean {
        val rawPath = uri.path ?: uri.toString().removePrefix("file://")
        return CryptoManager.isEncryptedFile(rawPath)
    }

    fun openRegionSession(context: Context, uri: Uri): IsolatedRegionOpenResult? {
        if (ProcessIsolation.isIsolatedProcess()) return null
        val rawPath = uri.path ?: uri.toString().removePrefix("file://")
        val appCtx = context.applicationContext
        val key = "region:$uri"
        if (IsolatedDecoderClient.quarantine.isQuarantined(key)) return null

        val isVault = CryptoManager.isEncryptedFile(rawPath)
        val channel = if (isVault) IsolatedDecoderChannel.VAULT else IsolatedDecoderChannel.PUBLIC
        val request = if (isVault) {
            buildEncryptedRegionRequest(File(rawPath))
        } else {
            buildPlainRegionRequest(appCtx, uri, rawPath)
        } ?: return null

        return request.use { req ->
            IsolatedServiceBridge.runWatchdogCall(
                context = appCtx,
                quarantineKey = key,
                quarantine = IsolatedDecoderClient.quarantine,
                channel = channel,
                priority = IsolatedServiceBridge.PRIORITY_LOW,
            ) { decoder ->
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
        isVault: Boolean = false,
    ): Bitmap? {
        if (ProcessIsolation.isIsolatedProcess() || sessionId <= 0L) return null
        val appCtx = context.applicationContext
        val channel = if (isVault) IsolatedDecoderChannel.VAULT else IsolatedDecoderChannel.PUBLIC
        return IsolatedServiceBridge.runWatchdogCall(
            context = appCtx,
            quarantineKey = "",
            quarantine = IsolatedDecoderClient.quarantine,
            channel = channel,
            priority = IsolatedServiceBridge.PRIORITY_LOW,
        ) { decoder ->
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

    fun closeRegionSession(context: Context, sessionId: Long, isVault: Boolean = false) {
        if (ProcessIsolation.isIsolatedProcess() || sessionId <= 0L) return
        val channel = if (isVault) IsolatedDecoderChannel.VAULT else IsolatedDecoderChannel.PUBLIC
        IsolatedServiceBridge.runFireAndForget(context.applicationContext, channel = channel) { decoder ->
            decoder.closeRegionSession(sessionId)
        }
    }

    private fun buildEncryptedRegionRequest(file: File): IsolatedDecodeRequest? {
        if (!CryptoManager.isVaultUnlocked) return null
        val meta = runCatching { CryptoManager.readMetadata(file) }.getOrNull()
        return buildEncryptedStreamRequest(
            file = file,
            expectedPlainSize = meta?.originalSize ?: 0L,
            nameHint = meta?.originalName ?: file.name,
        )
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
