package org.fossify.gallery.helpers.media

import java.util.concurrent.atomic.AtomicLong

/**
 * Isolated-process store holding active pure-Rust GIF sessions and their double-buffered
 * pixel slots so multi-frame GIF playback stays inside `isolatedProcess="true"` without `giflib`.
 */
internal object IsolatedGifSessionStore {
    private const val MAX_GIF_SESSIONS = 4
    private const val BYTES_PER_PIXEL = 4
    private const val MAP_LOAD_FACTOR = 0.75f

    private val nextSessionId = AtomicLong(1L)
    private val lock = Any()
    private val sessions = LinkedHashMap<Long, GifSession>(MAX_GIF_SESSIONS + 1, MAP_LOAD_FACTOR, true)

    private class GifSession(
        val rustHandle: Long,
        val frameWidth: Int,
        val frameHeight: Int,
        val frameCount: Int,
        val frameByteCount: Int,
        val slots: Array<IsolatedGifBufferTransport.WritableGifSlot>,
        @Volatile var closed: Boolean = false,
    ) {
        fun closeQuietly() {
            closed = true
            slots.forEach { slot -> runCatching { slot.close() } }
            NativeMediaDecoder.closeGifSession(rustHandle)
        }
    }

    fun openSession(request: IsolatedDecodeRequest): IsolatedGifOpenResult {
        val bytes = IsolatedPixelTransport.unpackInputBytes(
            request = request,
            maxBytes = IsolatedFormatSniffer.MAX_IN_MEMORY_INPUT_BYTES,
        )
        val hasInput = bytes != null && bytes.isNotEmpty()
        val opened = if (hasInput) {
            NativeMediaDecoder.openGifSession(bytes!!, request.maxWidth, request.maxHeight)
        } else {
            null
        }
        return when {
            !hasInput -> IsolatedGifOpenResult(status = IsolatedDecodeResult.STATUS_INVALID_INPUT)
            opened == null -> IsolatedGifOpenResult(status = IsolatedDecodeResult.STATUS_DECODE_FAILED)
            else -> createSessionWithSlots(opened)
        }
    }

    fun renderFrame(sessionId: Long, frameIndex: Int, targetSlot: Int): IsolatedGifFrameResult {
        val session = synchronized(lock) { sessions[sessionId] }
            ?: return IsolatedGifFrameResult(status = IsolatedDecodeResult.STATUS_INVALID_INPUT)

        return synchronized(session) {
            if (session.closed) {
                return@synchronized IsolatedGifFrameResult(status = IsolatedDecodeResult.STATUS_INVALID_INPUT)
            }
            renderSessionFrame(session, frameIndex, targetSlot)
        }
    }

    fun closeSession(sessionId: Long) {
        val removed = synchronized(lock) { sessions.remove(sessionId) }
        removed?.closeQuietly()
    }

    fun closeAll() {
        val toClose = synchronized(lock) {
            val copy = sessions.values.toList()
            sessions.clear()
            copy
        }
        toClose.forEach { it.closeQuietly() }
        NativeMediaDecoder.closeAllGifSessions()
    }

    private fun createSessionWithSlots(opened: RustGifOpenInfo): IsolatedGifOpenResult {
        val width = opened.width
        val height = opened.height
        val slot0 = IsolatedGifBufferTransport.createWritableSlot("calypso-gif-s0", width, height)
        val slot1 = if (slot0 != null) {
            IsolatedGifBufferTransport.createWritableSlot("calypso-gif-s1", width, height)
        } else {
            null
        }
        if (slot0 == null || slot1 == null) {
            runCatching { slot0?.close() }
            runCatching { slot1?.close() }
            NativeMediaDecoder.closeGifSession(opened.handle)
            return IsolatedGifOpenResult(status = IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }

        val byteCount = width * height * BYTES_PER_PIXEL
        val sessionId = nextSessionId.getAndIncrement()
        val session = GifSession(
            rustHandle = opened.handle,
            frameWidth = width,
            frameHeight = height,
            frameCount = opened.frameCount.coerceAtLeast(1),
            frameByteCount = byteCount,
            slots = arrayOf(slot0, slot1),
        )

        val evicted = mutableListOf<GifSession>()
        synchronized(lock) {
            while (sessions.size >= MAX_GIF_SESSIONS) {
                val oldestKey = sessions.entries.firstOrNull()?.key ?: break
                sessions.remove(oldestKey)?.let { evicted.add(it) }
            }
            sessions[sessionId] = session
        }
        evicted.forEach { it.closeQuietly() }

        return IsolatedGifOpenResult(
            status = IsolatedDecodeResult.STATUS_OK,
            sessionId = sessionId,
            frameWidth = width,
            frameHeight = height,
            frameCount = session.frameCount,
            slot0Handle = slot0.shmHandle,
            slot1Handle = slot1.shmHandle,
        )
    }

    private fun renderSessionFrame(
        session: GifSession,
        frameIndex: Int,
        targetSlot: Int,
    ): IsolatedGifFrameResult {
        val clampedFrame = frameIndex.mod(session.frameCount)
        val slotIdx = targetSlot.mod(IsolatedGifTimingPolicy.DOUBLE_BUFFER_SLOTS)
        val slot = session.slots[slotIdx]
        val directBuffer = slot.directByteBuffer()
        if (directBuffer != null) {
            val directDelay = NativeMediaDecoder.renderGifFrameIntoDirectBuffer(
                session.rustHandle,
                clampedFrame,
                directBuffer,
            )
            if (directDelay != null) {
                return IsolatedGifFrameResult(
                    status = IsolatedDecodeResult.STATUS_OK,
                    slotIndex = slotIdx,
                    durationMs = IsolatedGifTimingPolicy.normalizeFrameDelayMs(directDelay),
                    byteCount = session.frameByteCount,
                )
            }
        }

        val frame = NativeMediaDecoder.renderGifFrame(session.rustHandle, clampedFrame)
            ?: return IsolatedGifFrameResult(status = IsolatedDecodeResult.STATUS_DECODE_FAILED)

        val (written, pipePfd) = slot.writeRgbaBytes(frame.rgba)
        if (!written) {
            runCatching { pipePfd?.close() }
            return IsolatedGifFrameResult(status = IsolatedDecodeResult.STATUS_DECODE_FAILED)
        }

        val durationMs = IsolatedGifTimingPolicy.normalizeFrameDelayMs(frame.durationMs)
        return IsolatedGifFrameResult(
            status = IsolatedDecodeResult.STATUS_OK,
            slotIndex = slotIdx,
            durationMs = durationMs,
            byteCount = session.frameByteCount,
            pixelPipePfd = pipePfd,
        )
    }
}
