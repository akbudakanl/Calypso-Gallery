package org.fossify.gallery.helpers.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EncryptedVideoDataSource
import org.fossify.gallery.helpers.openDecryptedContentStream
import java.io.File

private const val THUMB_EMBEDDED_THRESHOLD = 600

internal object VaultDecoderLifecycle {
    fun onVaultLocked(context: Context? = null) {
        IsolatedServiceBridge.unbindVaultImmediately(context ?: IsolatedDecoderClient.appContextOrNull)
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

internal fun decodeEncryptedEmbeddedThumb(
    context: Context,
    thumbBytes: ByteArray?,
    maxWidth: Int,
    maxHeight: Int,
    flags: Int,
    quarantineKey: String,
): Bitmap? {
    if (thumbBytes == null || thumbBytes.isEmpty()) return null
    return try {
        decodeBytesOnChannel(
            context = context,
            bytes = thumbBytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags,
            nameHint = "thumb.jpg",
            quarantineKey = quarantineKey,
            channel = IsolatedDecoderChannel.VAULT,
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

internal fun decodeEncryptedPayload(
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
        decodeBytesOnChannel(
            context = context,
            bytes = plainBytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = flags or IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
            nameHint = originalName,
            quarantineKey = quarantineKey,
            channel = IsolatedDecoderChannel.VAULT,
        )
    } finally {
        plainBytes.fill(0)
    }
}

internal fun probeEncryptedBounds(context: Context?, file: File): IsolatedBoundsResult? {
    val ctx = context ?: IsolatedDecoderClient.appContextOrNull ?: return null
    if (ProcessIsolation.isIsolatedProcess() || !CryptoManager.isVaultUnlocked) return null
    val meta = runCatching { CryptoManager.readMetadata(file) }.getOrNull() ?: return null
    val plainBytes = runCatching {
        CryptoManager.openDecryptedContentStream(file)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    return try {
        probeBytesBoundsOnChannel(
            ctx,
            plainBytes,
            meta.originalName,
            IsolatedDecoderChannel.VAULT,
        )
    } finally {
        plainBytes.fill(0)
    }
}

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
    return executeDecode(ctx, request, quarantineKey, IsolatedDecoderClient.quarantine, channel)
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
