package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EncryptedVideoDataSource
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.File

private const val THUMB_EMBEDDED_THRESHOLD = 600

/**
 * Main-process broker for [IsolatedMediaDecoderService] with warm single-worker connection reuse,
 * per-request watchdog timeouts, and failure quarantine.
 */
object IsolatedDecoderClient {
    internal val quarantine = IsolatedFailureQuarantine()

    @Volatile
    private var appContext: Context? = null

    val appContextOrNull: Context?
        get() = appContext

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onAppForegrounded() = IsolatedServiceBridge.cancelPendingUnbind()

    fun onAppBackgrounded(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        IsolatedServiceBridge.scheduleUnbind(context)
    }

    fun decodeFile(
        context: Context?,
        file: File,
        maxWidth: Int,
        maxHeight: Int,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
        nameHint: String = file.name,
    ): Bitmap? {
        if (CryptoManager.isEncryptedFile(file.name)) {
            val preferThumb = (flags and IsolatedDecodeRequest.FLAG_PREFER_EMBEDDED_PREVIEW) != 0
            return decodeEncryptedFile(
                context = context,
                file = file,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                preferEmbeddedThumbnail = preferThumb,
                preferFullContent = !preferThumb,
                flags = flags,
            )
        }
        val ctx = context ?: appContext ?: return null
        if (ProcessIsolation.isIsolatedProcess() || !file.isFile) return null
        val key = "file:${file.absolutePath}:${file.lastModified()}:${file.length()}"
        if (quarantine.isQuarantined(key)) return null
        val pfd = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return null
        val request = IsolatedDecodeRequest(
            inputPfd = pfd,
            inputLength = file.length(),
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            nameHint = nameHint,
            flags = flags,
        )
        return executeDecode(ctx, request, key, quarantine)
    }

    fun decodeUri(
        context: Context?,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
        nameHint: String = uri.lastPathSegment.orEmpty(),
    ): Bitmap? {
        val ctx = context ?: appContext ?: return null
        if (ProcessIsolation.isIsolatedProcess()) return null
        val key = "uri:$uri"
        if (quarantine.isQuarantined(key)) return null
        val pfd = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
        val request = IsolatedDecodeRequest(
            inputPfd = pfd,
            inputLength = pfd.statSize.coerceAtLeast(0L),
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            nameHint = nameHint,
            flags = flags,
        )
        return executeDecode(ctx, request, key, quarantine)
    }

    fun decodeBytes(
        context: Context?,
        bytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
        nameHint: String = "",
        quarantineKey: String = "",
    ): Bitmap? {
        val ctx = context ?: appContext ?: return null
        if (ProcessIsolation.isIsolatedProcess() || bytes.isEmpty()) return null
        if (quarantineKey.isNotEmpty() && quarantine.isQuarantined(quarantineKey)) return null
        val request = buildBytesRequest(bytes, maxWidth, maxHeight, nameHint, flags) ?: return null
        return executeDecode(ctx, request, quarantineKey, quarantine)
    }

    fun decodeEncryptedFile(
        context: Context?,
        file: File,
        maxWidth: Int,
        maxHeight: Int,
        preferEmbeddedThumbnail: Boolean = false,
        preferFullContent: Boolean = false,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
    ): Bitmap? {
        val ctx = context ?: appContext ?: return null
        if (ProcessIsolation.isIsolatedProcess() || !CryptoManager.isVaultUnlocked) return null
        val key = "enc:${file.absolutePath}:${file.lastModified()}"
        if (quarantine.isQuarantined(key)) return null

        val meta = runCatching { CryptoManager.readMetadata(file) }.getOrNull() ?: return null
        val isVideo = meta.mediaType == TYPE_VIDEOS
        val tryEmbedded = shouldTryEmbeddedThumb(
            isVideo = isVideo,
            preferEmbeddedThumbnail = preferEmbeddedThumbnail,
            preferFullContent = preferFullContent,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
        )
        val embedded = if (tryEmbedded) {
            decodeEncryptedEmbeddedThumb(
                context = ctx,
                thumbBytes = meta.thumbnailBytes,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                flags = flags and IsolatedDecodeRequest.FLAG_CENTER_CROP,
                quarantineKey = "$key:thumb",
            )
        } else {
            null
        }
        return when {
            embedded != null -> embedded
            isVideo -> extractEncryptedVideoFrame(file, maxWidth, maxHeight)
            else -> decodeEncryptedPayload(ctx, file, meta.originalName, maxWidth, maxHeight, flags, key)
        }
    }

    fun probeFileBounds(context: Context?, file: File, nameHint: String = file.name): IsolatedBoundsResult? {
        if (CryptoManager.isEncryptedFile(file.name)) {
            return probeEncryptedBounds(context, file)
        }
        val ctx = context ?: appContext ?: return null
        if (ProcessIsolation.isIsolatedProcess() || !file.isFile) return null
        val key = "probe:${file.absolutePath}:${file.lastModified()}"
        if (quarantine.isQuarantined(key)) return null
        val pfd = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return null
        val request = IsolatedDecodeRequest(inputPfd = pfd, inputLength = file.length(), nameHint = nameHint)
        return executeProbe(ctx, request, key, quarantine)
    }

    fun probeUriBounds(
        context: Context?,
        uri: Uri,
        nameHint: String = uri.lastPathSegment.orEmpty(),
    ): IsolatedBoundsResult? {
        val ctx = context ?: appContext ?: return null
        if (ProcessIsolation.isIsolatedProcess()) return null
        val key = "probe_uri:$uri"
        if (quarantine.isQuarantined(key)) return null
        val pfd = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
        val request = IsolatedDecodeRequest(
            inputPfd = pfd,
            inputLength = pfd.statSize.coerceAtLeast(0L),
            nameHint = nameHint,
        )
        return executeProbe(ctx, request, key, quarantine)
    }

    fun probeBytesBounds(context: Context?, bytes: ByteArray, nameHint: String = ""): IsolatedBoundsResult? {
        val ctx = context ?: appContext ?: return null
        if (ProcessIsolation.isIsolatedProcess() || bytes.isEmpty()) return null
        val request = buildBytesRequest(bytes, 0, 0, nameHint, IsolatedDecodeRequest.FLAG_NONE) ?: return null
        return executeProbe(ctx, request, "", quarantine)
    }
}

private fun shouldTryEmbeddedThumb(
    isVideo: Boolean,
    preferEmbeddedThumbnail: Boolean,
    preferFullContent: Boolean,
    maxWidth: Int,
    maxHeight: Int,
): Boolean {
    if (isVideo) return true
    if (preferFullContent) return false
    val isThumbSize = maxWidth in 1..THUMB_EMBEDDED_THRESHOLD && maxHeight in 1..THUMB_EMBEDDED_THRESHOLD
    return preferEmbeddedThumbnail || isThumbSize
}

private fun decodeEncryptedEmbeddedThumb(
    context: Context,
    thumbBytes: ByteArray?,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    quarantineKey: String,
): Bitmap? {
    if (thumbBytes == null || thumbBytes.isEmpty()) return null
    return try {
        IsolatedDecoderClient.decodeBytes(
            context = context,
            bytes = thumbBytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags,
            nameHint = "thumb.jpg",
            quarantineKey = quarantineKey,
        )
    } finally {
        thumbBytes.fill(0)
    }
}

private fun extractEncryptedVideoFrame(file: File, maxWidth: Int, maxHeight: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    val dataSource = EncryptedVideoDataSource(file)
    return try {
        retriever.setDataSource(dataSource)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && maxWidth > 0 && maxHeight > 0) {
            retriever.getScaledFrameAtTime(
                -1L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                maxWidth,
                maxHeight,
            ) ?: retriever.getFrameAtTime(-1L)
        } else {
            retriever.getFrameAtTime(-1L)
        }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
        runCatching { dataSource.close() }
    }
}

private fun decodeEncryptedPayload(
    context: Context,
    file: File,
    originalName: String,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    quarantineKey: String,
): Bitmap? {
    val plainBytes = runCatching {
        CryptoManager.openDecryptedContentStream(file)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return try {
        IsolatedDecoderClient.decodeBytes(
            context = context,
            bytes = plainBytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags or IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
            nameHint = originalName,
            quarantineKey = quarantineKey,
        )
    } finally {
        plainBytes.fill(0)
    }
}

private fun probeEncryptedBounds(context: Context?, file: File): IsolatedBoundsResult? {
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess() || !CryptoManager.isVaultUnlocked) return null
    val meta = runCatching { CryptoManager.readMetadata(file) }.getOrNull() ?: return null
    val plainBytes = runCatching {
        CryptoManager.openDecryptedContentStream(file)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return try {
        IsolatedDecoderClient.probeBytesBounds(ctx, plainBytes, meta.originalName)
    } finally {
        plainBytes.fill(0)
    }
}

private fun buildBytesRequest(
    bytes: ByteArray,
    maxWidth: Int,
    maxHeight: Int,
    nameHint: String,
    flags: Int,
): IsolatedDecodeRequest? {
    val shm = IsolatedPixelTransport.packInputBytes("calypso-in", bytes)
    val pipe = if (shm == null) IsolatedPixelTransport.packInputPipe(bytes) else null
    if (shm == null && pipe == null) return null
    return IsolatedDecodeRequest(
        inputPfd = pipe,
        sharedInputHandle = shm,
        inputLength = bytes.size.toLong(),
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        nameHint = nameHint,
        flags = flags,
    )
}

private fun executeDecode(
    context: Context,
    request: IsolatedDecodeRequest,
    quarantineKey: String,
    quarantine: IsolatedFailureQuarantine,
): Bitmap? {
    return request.use { req ->
        IsolatedServiceBridge.runWatchdogCall(context, quarantineKey, quarantine) { decoder ->
            decoder.decodeStatic(req)?.use { res ->
                if (res.status == IsolatedDecodeResult.STATUS_OK) {
                    IsolatedPixelTransport.unpackBitmap(res)
                } else {
                    null
                }
            }
        }
    }
}

private fun executeProbe(
    context: Context,
    request: IsolatedDecodeRequest,
    quarantineKey: String,
    quarantine: IsolatedFailureQuarantine,
): IsolatedBoundsResult? {
    return request.use { req ->
        IsolatedServiceBridge.runWatchdogCall(context, quarantineKey, quarantine) { decoder ->
            val result = decoder.probeBounds(req)
            if (result != null && result.status == IsolatedDecodeResult.STATUS_OK) result else null
        }
    }
}
