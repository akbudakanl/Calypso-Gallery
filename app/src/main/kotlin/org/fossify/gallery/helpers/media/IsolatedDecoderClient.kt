package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.TYPE_VIDEOS
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
        return IsolatedDecodeRequestClient.decodeFileResult(
            context = context,
            file = file,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags,
            nameHint = nameHint,
        )?.use { res -> IsolatedPixelTransport.unpackBitmap(res) }
    }

    fun decodeUri(
        context: Context?,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
        nameHint: String = uri.lastPathSegment.orEmpty(),
    ): Bitmap? {
        return IsolatedDecodeRequestClient.decodeUriResult(
            context = context,
            uri = uri,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags,
            nameHint = nameHint,
        )?.use { res -> IsolatedPixelTransport.unpackBitmap(res) }
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
        return when (
            val outcome = IsolatedDecodeRequestClient.decodeEncryptedPacket(
                context = context,
                file = file,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                preferEmbeddedThumbnail = preferEmbeddedThumbnail,
                preferFullContent = preferFullContent,
                flags = flags,
            )
        ) {
            is EncryptedPacketOutcome.Result -> outcome.result.use { IsolatedPixelTransport.unpackBitmap(it) }
            is EncryptedPacketOutcome.DirectBitmap -> outcome.bitmap
            null -> null
        }
    }

    fun probeFileBounds(context: Context?, file: File, nameHint: String = file.name): IsolatedBoundsResult? {
        val isVault = CryptoManager.isEncryptedFile(file.name)
        val cached = IsolatedMetadataCache.get(file, isVault)
            ?.takeIf { it.width > 0 && it.height > 0 }
        return when {
            cached != null -> IsolatedBoundsResult(
                status = IsolatedDecodeResult.STATUS_OK,
                width = cached.width,
                height = cached.height,
                rotationDegrees = cached.rotationDegrees,
            )
            isVault -> probeEncryptedBounds(context, file)
            else -> probePublicFileBounds(context, file, nameHint)
        }
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

internal object IsolatedDecodeRequestClient {
    @Suppress("LongParameterList")
    fun decodeFileResult(
        context: Context?,
        file: File,
        maxWidth: Int,
        maxHeight: Int,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
        nameHint: String = file.name,
        priority: Int = IsolatedServiceBridge.PRIORITY_NORMAL,
        cancelFlag: AtomicBoolean? = null,
    ): IsolatedDecodeResult? = decodeLocalFileResult(
        context = context,
        file = file,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        flags = flags,
        nameHint = nameHint,
        priority = priority,
        cancelFlag = cancelFlag,
    )

    @Suppress("LongParameterList")
    fun decodeUriResult(
        context: Context?,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
        nameHint: String = uri.lastPathSegment.orEmpty(),
        priority: Int = IsolatedServiceBridge.PRIORITY_NORMAL,
        cancelFlag: AtomicBoolean? = null,
    ): IsolatedDecodeResult? = decodeContentUriResult(
        context = context,
        uri = uri,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        flags = flags,
        nameHint = nameHint,
        priority = priority,
        cancelFlag = cancelFlag,
    )

    @Suppress("LongParameterList")
    fun decodeEncryptedPacket(
        context: Context?,
        file: File,
        maxWidth: Int,
        maxHeight: Int,
        preferEmbeddedThumbnail: Boolean = false,
        preferFullContent: Boolean = false,
        flags: Int = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
        priority: Int = IsolatedServiceBridge.PRIORITY_NORMAL,
        cancelFlag: AtomicBoolean? = null,
    ): EncryptedPacketOutcome? {
        if (cancelFlag?.get() == true) return null
        val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
        if (ProcessIsolation.isIsolatedProcess() || !CryptoManager.isVaultUnlocked) return null
        val key = "enc:${file.absolutePath}:${file.lastModified()}"
        if (IsolatedDecoderClient.quarantine.isQuarantined(key)) return null

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
            decodeEncryptedEmbeddedThumbResult(
                context = ctx,
                thumbBytes = meta.thumbnailBytes,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                flags = flags and IsolatedDecodeRequest.FLAG_CENTER_CROP,
                quarantineKey = "$key:thumb",
                priority = priority,
                cancelFlag = cancelFlag,
            )
        } else {
            null
        }
        return when {
            embedded != null -> EncryptedPacketOutcome.Result(embedded)
            isVideo -> extractEncryptedVideoFrame(file, maxWidth, maxHeight)?.let {
                EncryptedPacketOutcome.DirectBitmap(it)
            }
            else -> decodeEncryptedPayloadResult(
                context = ctx,
                file = file,
                originalName = meta.originalName,
                originalSize = meta.originalSize,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                flags = flags,
                quarantineKey = key,
                priority = priority,
                cancelFlag = cancelFlag,
            )?.let { EncryptedPacketOutcome.Result(it) }
        }
    }
}

@Suppress("LongParameterList")
private fun decodeLocalFileResult(
    context: Context?,
    file: File,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    nameHint: String,
    priority: Int,
    cancelFlag: AtomicBoolean?,
): IsolatedDecodeResult? {
    if (cancelFlag?.get() == true) return null
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess() || !file.isFile) return null
    val key = "file:${file.absolutePath}:${file.lastModified()}:${file.length()}"
    if (IsolatedDecoderClient.quarantine.isQuarantined(key)) return null
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
    val cacheBounds = (flags and IsolatedDecodeRequest.FLAG_PREFER_EMBEDDED_PREVIEW) == 0
    return executeDecodeResult(
        context = ctx,
        request = request,
        quarantineKey = key,
        quarantine = IsolatedDecoderClient.quarantine,
        priority = priority,
        cancelFlag = cancelFlag,
    )?.also { res ->
        if (cacheBounds && res.sourceWidth > 0 && res.sourceHeight > 0) {
            IsolatedMetadataCache.putBounds(file, false, res.sourceWidth, res.sourceHeight, res.rotationDegrees)
        }
    }
}

@Suppress("LongParameterList")
private fun decodeContentUriResult(
    context: Context?,
    uri: Uri,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    nameHint: String,
    priority: Int,
    cancelFlag: AtomicBoolean?,
): IsolatedDecodeResult? {
    if (cancelFlag?.get() == true) return null
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess()) return null
    val key = "uri:$uri"
    if (IsolatedDecoderClient.quarantine.isQuarantined(key)) return null
    val pfd = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
    val request = IsolatedDecodeRequest(
        inputPfd = pfd,
        inputLength = pfd.statSize.coerceAtLeast(0L),
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        nameHint = nameHint,
        flags = resolvePolicyFlags(ctx, flags),
    )
    return executeDecodeResult(
        context = ctx,
        request = request,
        quarantineKey = key,
        quarantine = IsolatedDecoderClient.quarantine,
        priority = priority,
        cancelFlag = cancelFlag,
    )
}

private fun probePublicFileBounds(context: Context?, file: File, nameHint: String): IsolatedBoundsResult? {
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess() || !file.isFile) return null
    val key = "probe:${file.absolutePath}:${file.lastModified()}"
    if (IsolatedDecoderClient.quarantine.isQuarantined(key)) return null
    val pfd = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }.getOrNull() ?: return null
    val request = IsolatedDecodeRequest(
        inputPfd = pfd,
        inputLength = file.length(),
        nameHint = nameHint,
        flags = resolvePolicyFlags(ctx, IsolatedDecodeRequest.FLAG_NONE),
    )
    return executeProbe(ctx, request, key, IsolatedDecoderClient.quarantine)?.also { bounds ->
        if (bounds.status == IsolatedDecodeResult.STATUS_OK) {
            IsolatedMetadataCache.putBounds(file, false, bounds.width, bounds.height, bounds.rotationDegrees)
        }
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
    return executeDecodeResult(context, request, quarantineKey, quarantine, channel)
        ?.use { res -> IsolatedPixelTransport.unpackBitmap(res) }
}

@Suppress("LongParameterList")
internal fun executeDecodeResult(
    context: Context,
    request: IsolatedDecodeRequest,
    quarantineKey: String,
    quarantine: IsolatedFailureQuarantine,
    channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
    priority: Int = IsolatedServiceBridge.PRIORITY_NORMAL,
    cancelFlag: AtomicBoolean? = null,
): IsolatedDecodeResult? {
    return request.use { req ->
        if (cancelFlag?.get() == true) return@use null
        IsolatedServiceBridge.runWatchdogCall(
            context = context,
            quarantineKey = quarantineKey,
            quarantine = quarantine,
            channel = channel,
            priority = priority,
            cancelFlag = cancelFlag,
        ) { decoder ->
            val res = decoder.decodeStatic(req)
            if (res == null || res.status != IsolatedDecodeResult.STATUS_OK) {
                res?.close()
                null
            } else {
                res
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
        IsolatedServiceBridge.runWatchdogCall(
            context = context,
            quarantineKey = quarantineKey,
            quarantine = quarantine,
            channel = channel,
            priority = IsolatedServiceBridge.PRIORITY_LOW,
        ) { decoder ->
            val result = decoder.probeBounds(req)
            if (result != null && result.status == IsolatedDecodeResult.STATUS_OK) result else null
        }
    }
}
