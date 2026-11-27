package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EncryptedVideoDataSource
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val THUMB_EMBEDDED_THRESHOLD = 600

internal sealed class EncryptedPacketOutcome {
    class Result(val result: IsolatedDecodeResult) : EncryptedPacketOutcome()
    class DirectBitmap(val bitmap: Bitmap) : EncryptedPacketOutcome()
}

internal object VaultDecoderLifecycle {
    fun onVaultLocked(context: Context? = null) {
        IsolatedServiceBridge.unbindVaultImmediately(context ?: IsolatedDecoderClient.appContextOrNull)
        IsolatedMetadataCache.clearVault()
        VaultBitmapPool.clearAndZeroize()
    }
}

internal fun shouldTryEmbeddedThumb(
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

@Suppress("LongParameterList")
internal fun decodeEncryptedEmbeddedThumbResult(
    context: Context,
    thumbBytes: ByteArray?,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    quarantineKey: String,
    priority: Int = IsolatedServiceBridge.PRIORITY_NORMAL,
    cancelFlag: AtomicBoolean? = null,
): IsolatedDecodeResult? {
    if (thumbBytes == null || thumbBytes.isEmpty()) return null
    return try {
        decodeBytesOnChannelResult(
            context = context,
            bytes = thumbBytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags,
            nameHint = "thumb.jpg",
            quarantineKey = quarantineKey,
            channel = IsolatedDecoderChannel.VAULT,
            priority = priority,
            cancelFlag = cancelFlag,
        )
    } finally {
        thumbBytes.fill(0)
    }
}

internal fun extractEncryptedVideoFrame(file: File, maxWidth: Int, maxHeight: Int): Bitmap? {
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

@Suppress("LongParameterList")
internal fun buildEncryptedStreamRequest(
    file: File,
    expectedPlainSize: Long,
    nameHint: String,
    maxWidth: Int = 0,
    maxHeight: Int = 0,
    flags: Int = IsolatedDecodeRequest.FLAG_NONE,
): IsolatedDecodeRequest? {
    if (expectedPlainSize in 1..IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES.toLong()) {
        val shm = runCatching {
            CryptoManager.openDecryptedContentStream(file)?.use { stream ->
                IsolatedPixelTransport.packInputStream("calypso-enc-in", stream, expectedPlainSize)
            }
        }.getOrNull()
        if (shm != null) {
            return IsolatedDecodeRequest(
                sharedInputHandle = shm,
                inputLength = expectedPlainSize,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                nameHint = nameHint,
                flags = flags,
            )
        }
    }

    val plainBytes = runCatching {
        CryptoManager.openDecryptedContentStream(file)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return try {
        buildBytesRequest(
            bytes = plainBytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            nameHint = nameHint,
            flags = flags,
        )
    } finally {
        plainBytes.fill(0)
    }
}

@Suppress("LongParameterList")
internal fun decodeEncryptedPayloadResult(
    context: Context,
    file: File,
    originalName: String,
    originalSize: Long,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    quarantineKey: String,
    priority: Int = IsolatedServiceBridge.PRIORITY_NORMAL,
    cancelFlag: AtomicBoolean? = null,
): IsolatedDecodeResult? {
    if (cancelFlag?.get() == true) return null
    if (ProcessIsolation.isIsolatedProcess()) return null
    if (quarantineKey.isNotEmpty() && IsolatedDecoderClient.quarantine.isQuarantined(quarantineKey)) return null
    val resolvedFlags = resolvePolicyFlags(context, flags or IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION)
    val request = buildEncryptedStreamRequest(
        file = file,
        expectedPlainSize = originalSize,
        nameHint = originalName,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        flags = resolvedFlags,
    ) ?: return null
    return executeDecodeResult(
        context = context,
        request = request,
        quarantineKey = quarantineKey,
        quarantine = IsolatedDecoderClient.quarantine,
        channel = IsolatedDecoderChannel.VAULT,
        priority = priority,
        cancelFlag = cancelFlag,
    )?.also { res ->
        if (res.sourceWidth > 0 && res.sourceHeight > 0) {
            IsolatedMetadataCache.putBounds(file, true, res.sourceWidth, res.sourceHeight, res.rotationDegrees)
        }
    }
}

internal fun probeEncryptedBounds(context: Context?, file: File): IsolatedBoundsResult? {
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess() || !CryptoManager.isVaultUnlocked) return null
    val meta = runCatching { CryptoManager.readMetadata(file) }.getOrNull() ?: return null
    val request = buildEncryptedStreamRequest(
        file = file,
        expectedPlainSize = meta.originalSize,
        nameHint = meta.originalName,
        flags = resolvePolicyFlags(ctx, IsolatedDecodeRequest.FLAG_NONE),
    ) ?: return null
    return executeProbe(ctx, request, "", IsolatedDecoderClient.quarantine, IsolatedDecoderChannel.VAULT)
        ?.also { bounds ->
            if (bounds.status == IsolatedDecodeResult.STATUS_OK) {
                IsolatedMetadataCache.putBounds(file, true, bounds.width, bounds.height, bounds.rotationDegrees)
            }
        }
}

@Suppress("LongParameterList")
internal fun decodeBytesOnChannel(
    context: Context?,
    bytes: ByteArray,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    nameHint: String,
    quarantineKey: String,
    channel: IsolatedDecoderChannel,
): Bitmap? {
    return decodeBytesOnChannelResult(
        context = context,
        bytes = bytes,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        flags = flags,
        nameHint = nameHint,
        quarantineKey = quarantineKey,
        channel = channel,
    )?.use { res -> IsolatedPixelTransport.unpackBitmap(res) }
}

@Suppress("LongParameterList")
internal fun decodeBytesOnChannelResult(
    context: Context?,
    bytes: ByteArray,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    nameHint: String,
    quarantineKey: String,
    channel: IsolatedDecoderChannel,
    priority: Int = IsolatedServiceBridge.PRIORITY_NORMAL,
    cancelFlag: AtomicBoolean? = null,
): IsolatedDecodeResult? {
    if (cancelFlag?.get() == true) return null
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess() || bytes.isEmpty()) return null
    if (quarantineKey.isNotEmpty() && IsolatedDecoderClient.quarantine.isQuarantined(quarantineKey)) return null
    val request = buildBytesRequest(
        bytes,
        maxWidth,
        maxHeight,
        nameHint,
        resolvePolicyFlags(ctx, flags),
    ) ?: return null
    return executeDecodeResult(
        context = ctx,
        request = request,
        quarantineKey = quarantineKey,
        quarantine = IsolatedDecoderClient.quarantine,
        channel = channel,
        priority = priority,
        cancelFlag = cancelFlag,
    )
}

internal fun probeBytesBoundsOnChannel(
    context: Context?,
    bytes: ByteArray,
    nameHint: String,
    channel: IsolatedDecoderChannel,
): IsolatedBoundsResult? {
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess() || bytes.isEmpty()) return null
    val request = buildBytesRequest(
        bytes,
        0,
        0,
        nameHint,
        resolvePolicyFlags(ctx, IsolatedDecodeRequest.FLAG_NONE),
    ) ?: return null
    return executeProbe(ctx, request, "", IsolatedDecoderClient.quarantine, channel)
}
