package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Main-process client for opening, stepping, and closing isolated animated GIF playback sessions
 * hosted in [IsolatedMediaDecoderService].
 */
object IsolatedGifClient {
    private const val IO_BUFFER_BYTES = 64 * 1024

    fun openGifSession(
        context: Context,
        path: String,
        maxWidth: Int,
        maxHeight: Int,
    ): IsolatedGifDrawable? {
        if (ProcessIsolation.isIsolatedProcess() || path.isEmpty()) {
            return null
        }

        val appCtx = context.applicationContext
        val key = buildQuarantineKey(path)
        if (IsolatedDecoderClient.quarantine.isQuarantined(key)) {
            return null
        }

        val request = buildOpenRequest(appCtx, path, maxWidth, maxHeight) ?: return null
        val openResult = try {
            IsolatedServiceBridge.runWatchdogCall(appCtx, key, IsolatedDecoderClient.quarantine) { service ->
                service.openGifSession(request)
            }
        } finally {
            request.close()
        }

        if (openResult == null || openResult.status != IsolatedDecodeResult.STATUS_OK) {
            IsolatedPixelTransport.closeHandle(openResult?.slot0Handle)
            IsolatedPixelTransport.closeHandle(openResult?.slot1Handle)
            return null
        }

        return buildOpenedDrawable(appCtx, openResult)
    }

    internal fun fetchGifFrameIntoSlot(
        context: Context,
        sessionId: Long,
        frameIndex: Int,
        slotIndex: Int,
        slot: IsolatedGifBufferTransport.ReadableGifSlot,
        targetBitmap: Bitmap,
    ): Int? {
        val result = IsolatedServiceBridge.runGifWatchdogCall(
            context = context,
            quarantineKey = "",
            quarantine = IsolatedDecoderClient.quarantine,
        ) { service ->
            service.renderGifFrame(sessionId, frameIndex, slotIndex)
        }

        if (result == null || result.status != IsolatedDecodeResult.STATUS_OK) {
            runCatching { result?.pixelPipePfd?.close() }
            return null
        }

        if (!slot.readInto(targetBitmap, result.pixelPipePfd)) {
            return null
        }
        return IsolatedGifTimingPolicy.normalizeFrameDelayMs(result.durationMs)
    }

    internal fun closeGifSessionAsync(context: Context, sessionId: Long) {
        if (sessionId <= 0L) return
        IsolatedServiceBridge.runFireAndForget(context) { service ->
            service.closeGifSession(sessionId)
        }
    }

    private fun buildQuarantineKey(path: String): String {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            return "gif_uri:$path"
        }
        val file = File(path)
        return "gif:${file.absolutePath}:${file.lastModified()}:${file.length()}"
    }

    private fun buildOpenRequest(
        context: Context,
        path: String,
        maxWidth: Int,
        maxHeight: Int,
    ): IsolatedDecodeRequest? {
        if (CryptoManager.isEncryptedFile(path)) {
            return buildEncryptedOpenRequest(File(path), maxWidth, maxHeight)
        }

        val pfd = if (path.startsWith("content://") || path.startsWith("file://")) {
            runCatching { context.contentResolver.openFileDescriptor(Uri.parse(path), "r") }.getOrNull()
        } else {
            val file = File(path)
            if (file.isFile) {
                runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            } else {
                null
            }
        } ?: return null

        val length = pfd.statSize.coerceAtLeast(0L)
        return IsolatedDecodeRequest(
            inputPfd = pfd,
            inputLength = length,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            nameHint = path.substringAfterLast('/'),
            flags = IsolatedDecodeRequest.FLAG_NONE,
        )
    }

    private fun buildEncryptedOpenRequest(
        file: File,
        maxWidth: Int,
        maxHeight: Int,
    ): IsolatedDecodeRequest? {
        if (!CryptoManager.isVaultUnlocked) return null
        val decryptedBytes = runCatching {
            CryptoManager.openDecryptedContentStream(file)?.use { stream ->
                readBoundedStream(stream, IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES)
            }
        }.getOrNull() ?: return null

        if (decryptedBytes.isEmpty()) return null
        return try {
            val shm = IsolatedPixelTransport.packInputBytes("calypso-gif-in", decryptedBytes)
            val pipe = if (shm == null) IsolatedPixelTransport.packInputPipe(decryptedBytes) else null
            if (shm == null && pipe == null) {
                null
            } else {
                IsolatedDecodeRequest(
                    inputPfd = pipe,
                    sharedInputHandle = shm,
                    inputLength = decryptedBytes.size.toLong(),
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    nameHint = file.name,
                    flags = IsolatedDecodeRequest.FLAG_NONE,
                )
            }
        } finally {
            decryptedBytes.fill(0)
        }
    }

    private fun buildOpenedDrawable(
        appContext: Context,
        openResult: IsolatedGifOpenResult,
    ): IsolatedGifDrawable? {
        val width = openResult.frameWidth
        val height = openResult.frameHeight
        val sessionId = openResult.sessionId
        val slot0 = if (width > 0 && height > 0 && sessionId > 0L) {
            IsolatedGifBufferTransport.createReadableSlot(width, height, openResult.slot0Handle)
        } else {
            IsolatedPixelTransport.closeHandle(openResult.slot0Handle)
            null
        }
        val slot1 = if (slot0 != null) {
            IsolatedGifBufferTransport.createReadableSlot(width, height, openResult.slot1Handle)
        } else {
            IsolatedPixelTransport.closeHandle(openResult.slot1Handle)
            null
        }
        val slotsReady = slot0 != null && slot1 != null
        val bitmaps = if (slotsReady) createBitmapPair(width, height) else null
        val initialDuration = if (slot0 != null && bitmaps != null) {
            fetchGifFrameIntoSlot(appContext, sessionId, 0, 0, slot0, bitmaps.first)
        } else {
            null
        }

        if (slotsReady && bitmaps != null && initialDuration != null) {
            return IsolatedGifDrawable(
                appContext = appContext,
                sessionId = sessionId,
                meta = IsolatedGifDrawable.GifMetadata(
                    width = width,
                    height = height,
                    frameCount = openResult.frameCount.coerceAtLeast(1),
                    initialDurationMs = initialDuration,
                ),
                slots = arrayOf(slot0, slot1),
                bitmaps = arrayOf(bitmaps.first, bitmaps.second),
            )
        }

        runCatching { slot0?.close() }
        runCatching { slot1?.close() }
        bitmaps?.first?.recycle()
        bitmaps?.second?.recycle()
        closeGifSessionAsync(appContext, sessionId)
        return null
    }

    private fun createBitmapPair(width: Int, height: Int): Pair<Bitmap, Bitmap>? {
        return try {
            val first = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val second = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            first to second
        } catch (_: Throwable) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun readBoundedStream(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(IO_BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                return null
            }
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }
}
