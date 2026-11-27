package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.TYPE_VIDEOS
import java.io.File

/**
 * Main-process broker for [IsolatedMediaDecoderService] and [VaultIsolatedMediaDecoderService]
 * with warm worker connection reuse, per-request watchdog timeouts, and failure quarantine.
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
            flags = resolvePolicyFlags(ctx, flags),
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
            flags = resolvePolicyFlags(ctx, flags),
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
        return decodeBytesOnChannel(
            context = context,
            bytes = bytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags,
            nameHint = nameHint,
            quarantineKey = quarantineKey,
            channel = IsolatedDecoderChannel.PUBLIC,
        )
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
        val request = IsolatedDecodeRequest(
            inputPfd = pfd,
            inputLength = file.length(),
            nameHint = nameHint,
            flags = resolvePolicyFlags(ctx, IsolatedDecodeRequest.FLAG_NONE),
        )
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
            flags = resolvePolicyFlags(ctx, IsolatedDecodeRequest.FLAG_NONE),
        )
        return executeProbe(ctx, request, key, quarantine)
    }

    fun probeBytesBounds(context: Context?, bytes: ByteArray, nameHint: String = ""): IsolatedBoundsResult? {
        return probeBytesBoundsOnChannel(context, bytes, nameHint, IsolatedDecoderChannel.PUBLIC)
    }
}

internal fun resolvePolicyFlags(context: Context, flags: Int): Int {
    return if (context.config.showSvgFiles) {
        flags or IsolatedDecodeRequest.FLAG_ALLOW_SVG
    } else {
        flags and IsolatedDecodeRequest.FLAG_ALLOW_SVG.inv()
    }
}

internal fun buildBytesRequest(
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

internal fun executeDecode(
    context: Context,
    request: IsolatedDecodeRequest,
    quarantineKey: String,
    quarantine: IsolatedFailureQuarantine,
    channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
): Bitmap? {
    return request.use { req ->
        IsolatedServiceBridge.runWatchdogCall(context, quarantineKey, quarantine, channel) { decoder ->
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

internal fun executeProbe(
    context: Context,
    request: IsolatedDecodeRequest,
    quarantineKey: String,
    quarantine: IsolatedFailureQuarantine,
    channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
): IsolatedBoundsResult? {
    return request.use { req ->
        IsolatedServiceBridge.runWatchdogCall(context, quarantineKey, quarantine, channel) { decoder ->
            val result = decoder.probeBounds(req)
            if (result != null && result.status == IsolatedDecodeResult.STATUS_OK) result else null
        }
    }
}
