package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

private const val ANIM_SNIFF_HEADER_BYTES = 1024
private val ANIM_CANDIDATE_EXTENSIONS = setOf("gif", "png", "apng", "webp")

/**
 * Main-process client for opening, stepping, and closing isolated animated GIF playback sessions
 * hosted in [IsolatedMediaDecoderService].
 */
object IsolatedGifClient {
    private const val IO_BUFFER_BYTES = 64 * 1024

    fun isAnimatedMediaCandidate(context: Context, path: String): Boolean {
        return AnimatedCandidateProbe.isAnimatedCandidate(context, path)
    }

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

        val channel = if (CryptoManager.isEncryptedFile(path)) {
            IsolatedDecoderChannel.VAULT
        } else {
            IsolatedDecoderChannel.PUBLIC
        }
        val request = buildOpenRequest(appCtx, path, maxWidth, maxHeight) ?: return null
        val openResult = try {
            IsolatedServiceBridge.runWatchdogCall(
                context = appCtx,
                quarantineKey = key,
                quarantine = IsolatedDecoderClient.quarantine,
                channel = channel,
            ) { service ->
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

        return buildOpenedDrawable(appCtx, openResult, channel)
    }

    internal fun fetchGifFrameIntoSlot(
        context: Context,
        sessionId: Long,
        frameIndex: Int,
        slotIndex: Int,
        slot: IsolatedGifBufferTransport.ReadableGifSlot,
        targetBitmap: Bitmap,
        channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
    ): Int? {
        val result = IsolatedServiceBridge.runGifWatchdogCall(
            context = context,
            quarantineKey = "",
            quarantine = IsolatedDecoderClient.quarantine,
            channel = channel,
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

    internal fun closeGifSessionAsync(
        context: Context,
        sessionId: Long,
        channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
    ) {
        if (sessionId <= 0L) return
        IsolatedServiceBridge.runFireAndForget(context, channel = channel) { service ->
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
        val meta = runCatching { CryptoManager.readMetadata(file) }.getOrNull()
        val expectedSize = meta?.originalSize ?: 0L
        val nameHint = meta?.originalName ?: file.name
        if (expectedSize > IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES.toLong()) {
            return null
        }
        tryStreamEncryptedOpenRequest(file, expectedSize, maxWidth, maxHeight, nameHint)?.let {
            return it
        }
        val decryptedBytes = runCatching {
            CryptoManager.openDecryptedContentStream(file)?.use { stream ->
                GifOpenIoHelper.readBoundedStream(stream, IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES)
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
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
                    nameHint = nameHint,
                    flags = IsolatedDecodeRequest.FLAG_NONE,
                )
            }
        } finally {
            decryptedBytes.fill(0)
        }
    }

    private fun tryStreamEncryptedOpenRequest(
        file: File,
        expectedSize: Long,
        maxWidth: Int,
        maxHeight: Int,
        nameHint: String,
    ): IsolatedDecodeRequest? {
        if (expectedSize <= 0L) return null
        val shm = runCatching {
            CryptoManager.openDecryptedContentStream(file)?.use { stream ->
                IsolatedPixelTransport.packInputStream("calypso-gif-in", stream, expectedSize)
            }
        }.getOrNull() ?: return null
        return IsolatedDecodeRequest(
            sharedInputHandle = shm,
            inputLength = expectedSize,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            nameHint = nameHint,
            flags = IsolatedDecodeRequest.FLAG_NONE,
        )
    }

    private fun buildOpenedDrawable(
        appContext: Context,
        openResult: IsolatedGifOpenResult,
        channel: IsolatedDecoderChannel,
    ): IsolatedGifDrawable? {
        val (width, height, sessionId) = Triple(openResult.frameWidth, openResult.frameHeight, openResult.sessionId)
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
        val bitmaps = if (slotsReady) GifOpenIoHelper.createBitmapPair(width, height) else null
        val initialDuration = if (slot0 != null && bitmaps != null) {
            fetchGifFrameIntoSlot(appContext, sessionId, 0, 0, slot0, bitmaps.first, channel)
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
                    channel = channel,
                ),
                slots = arrayOf(slot0, slot1),
                bitmaps = arrayOf(bitmaps.first, bitmaps.second),
            )
        }

        runCatching { slot0?.close() }
        runCatching { slot1?.close() }
        bitmaps?.first?.recycle()
        bitmaps?.second?.recycle()
        closeGifSessionAsync(appContext, sessionId, channel)
        return null
    }
}

private object GifOpenIoHelper {
    private const val IO_BUFFER_BYTES = 64 * 1024

    fun createBitmapPair(width: Int, height: Int): Pair<Bitmap, Bitmap>? {
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

    fun readBoundedStream(input: InputStream, maxBytes: Int): ByteArray? {
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

private object AnimatedCandidateProbe {
    fun isAnimatedCandidate(context: Context, path: String): Boolean {
        if (path.isEmpty() || IsolatedFormatSniffer.isVideoFileHint(path)) return false
        val isEncrypted = CryptoManager.isEncryptedFile(path)
        val localFile = if (!path.startsWith("content://")) {
            File(path.removePrefix("file://"))
        } else {
            null
        }
        if (localFile != null) {
            IsolatedMetadataCache.get(localFile, isEncrypted)?.isAnimated?.let { return it }
        }
        val effectiveName = resolveEffectiveName(path, isEncrypted) ?: return false
        val ext = extractExtension(effectiveName)
        val animated = when {
            ext == "gif" -> true
            ext.isNotEmpty() && ext !in ANIM_CANDIDATE_EXTENSIONS && !path.startsWith("content://") -> false
            else -> readHeaderBytes(context, path, isEncrypted)
                ?.let { IsolatedFormatSniffer.hasAnimatedContainerHint(it) } == true
        }
        if (localFile != null) {
            IsolatedMetadataCache.putAnimatedHint(localFile, isEncrypted, animated)
        }
        return animated
    }

    private fun resolveEffectiveName(path: String, isEncrypted: Boolean): String? {
        if (!isEncrypted) return path
        if (!CryptoManager.isVaultUnlocked) return null
        return runCatching { CryptoManager.readMetadata(File(path))?.originalName }.getOrNull().orEmpty()
    }

    private fun extractExtension(nameOrPath: String): String {
        val clean = nameOrPath.substringBefore('?').substringBefore('#')
        val dot = clean.lastIndexOf('.')
        if (dot < 0 || dot == clean.lastIndex) return ""
        return clean.substring(dot + 1).lowercase(Locale.US)
    }

    private fun readHeaderBytes(context: Context, path: String, isEncrypted: Boolean): ByteArray? {
        return runCatching {
            val stream: InputStream? = when {
                isEncrypted -> CryptoManager.openDecryptedContentStream(File(path))
                path.startsWith("content://") || path.startsWith("file://") ->
                    context.contentResolver.openInputStream(Uri.parse(path))
                else -> FileInputStream(File(path))
            }
            stream?.use { input ->
                val buf = ByteArray(ANIM_SNIFF_HEADER_BYTES)
                var total = 0
                while (total < buf.size) {
                    val read = input.read(buf, total, buf.size - total)
                    if (read <= 0) break
                    total += read
                }
                if (total > 0) buf.copyOf(total) else null
            }
        }.getOrNull()
    }
}
