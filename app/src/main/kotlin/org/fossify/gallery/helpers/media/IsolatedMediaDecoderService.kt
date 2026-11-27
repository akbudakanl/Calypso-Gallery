package org.fossify.gallery.helpers.media

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.util.concurrent.Semaphore

private const val MIN_CONCURRENT_HEAVY_DECODES = 2
private const val MAX_CONCURRENT_HEAVY_DECODES = 3
private const val HEAVY_DECODE_MIN_BYTES = 1_000_000L

/**
 * Runs in `android:isolatedProcess="true"` with no filesystem, network, or main-process memory access.
 */
open class IsolatedMediaDecoderService : Service() {
    private val heavyDecodeSemaphore = Semaphore(
        Runtime.getRuntime().availableProcessors().coerceIn(
            MIN_CONCURRENT_HEAVY_DECODES,
            MAX_CONCURRENT_HEAVY_DECODES,
        ),
        true,
    )

    private val binder = object : IIsolatedMediaDecoder.Stub() {
        override fun probeBounds(request: IsolatedDecodeRequest?): IsolatedBoundsResult {
            val req = request ?: return IsolatedBoundsResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
            val token = Binder.clearCallingIdentity()
            return try {
                req.use { IsolatedMediaDecoders.probeBounds(it) }
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun decodeStatic(request: IsolatedDecodeRequest?): IsolatedDecodeResult {
            val req = request ?: return IsolatedDecodeResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
            val token = Binder.clearCallingIdentity()
            val isHeavy = req.inputLength <= 0L || req.inputLength > HEAVY_DECODE_MIN_BYTES
            if (isHeavy) {
                heavyDecodeSemaphore.acquire()
            }
            return try {
                req.use { IsolatedMediaDecoders.decodeStatic(it) }
            } finally {
                if (isHeavy) {
                    heavyDecodeSemaphore.release()
                }
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun openRegionSession(request: IsolatedDecodeRequest?): IsolatedRegionOpenResult {
            val req = request ?: return IsolatedRegionOpenResult(IsolatedDecodeResult.STATUS_INVALID_INPUT)
            val token = Binder.clearCallingIdentity()
            return try {
                req.use { IsolatedRegionDecoderStore.openSession(it) }
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun decodeRegionTile(
            sessionId: Long,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            sampleSize: Int,
        ): IsolatedDecodeResult {
            val token = Binder.clearCallingIdentity()
            return try {
                IsolatedRegionDecoderStore.decodeTile(sessionId, left, top, right, bottom, sampleSize)
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun closeRegionSession(sessionId: Long) {
            val token = Binder.clearCallingIdentity()
            try {
                IsolatedRegionDecoderStore.closeSession(sessionId)
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun openGifSession(request: IsolatedDecodeRequest?): IsolatedGifOpenResult {
            val safeRequest = request ?: return IsolatedGifOpenResult(
                status = IsolatedDecodeResult.STATUS_INVALID_INPUT,
            )
            val token = Binder.clearCallingIdentity()
            return try {
                IsolatedGifSessionStore.openSession(safeRequest)
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun renderGifFrame(sessionId: Long, frameIndex: Int, targetSlot: Int): IsolatedGifFrameResult {
            val token = Binder.clearCallingIdentity()
            return try {
                IsolatedGifSessionStore.renderFrame(sessionId, frameIndex, targetSlot)
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }

        override fun closeGifSession(sessionId: Long) {
            val token = Binder.clearCallingIdentity()
            try {
                IsolatedGifSessionStore.closeSession(sessionId)
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        IsolatedRegionDecoderStore.closeAll()
        IsolatedGifSessionStore.closeAll()
        super.onDestroy()
    }
}

/**
 * Dedicated isolated decoder process for decrypted Vault media; unbound immediately on vault lock.
 */
class VaultIsolatedMediaDecoderService : IsolatedMediaDecoderService()
