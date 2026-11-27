package org.fossify.gallery.helpers.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.readEmbeddedThumbnailBytes
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 10_000L
private const val BACKGROUND_UNBIND_DELAY_MS = 15_000L
private const val WORKER_POOL_THREADS = 4
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
            decodeEncryptedEmbeddedThumb(ctx, file, maxWidth, maxHeight, "$key:thumb")
        } else {
            null
        }
        return when {
            embedded != null -> embedded
            isVideo -> null
            else -> decodeEncryptedPayload(ctx, file, meta.originalName, maxWidth, maxHeight, key)
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
    file: File,
    maxWidth: Int,
    maxHeight: Int,
    quarantineKey: String,
): Bitmap? {
    val thumbBytes = runCatching { CryptoManager.readEmbeddedThumbnailBytes(file) }.getOrNull()
    if (thumbBytes == null || thumbBytes.isEmpty()) return null
    return try {
        IsolatedDecoderClient.decodeBytes(
            context = context,
            bytes = thumbBytes,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            flags = IsolatedDecodeRequest.FLAG_NONE,
            nameHint = "thumb.jpg",
            quarantineKey = quarantineKey,
        )
    } finally {
        thumbBytes.fill(0)
    }
}

private fun decodeEncryptedPayload(
    context: Context,
    file: File,
    originalName: String,
    maxWidth: Int,
    maxHeight: Int,
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
            flags = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION,
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

internal object IsolatedServiceBridge {
    private val lock = Any()
    private val workerExecutor = Executors.newFixedThreadPool(WORKER_POOL_THREADS) { runnable ->
        Thread(runnable, "calypso-iso-decode").apply { isDaemon = true }
    }
    private val mainHandler = runCatching { Handler(Looper.getMainLooper()) }.getOrNull()

    @Volatile
    private var scheduledContext: Context? = null

    @Volatile
    private var service: IIsolatedMediaDecoder? = null

    @Volatile
    private var isBound = false

    private var activeConnection: ServiceConnection? = null
    private var connectLatch: CountDownLatch? = null
    private val pendingUnbind = Runnable {
        scheduledContext?.let { resetConnection(it) }
    }

    fun cancelPendingUnbind() {
        mainHandler?.removeCallbacks(pendingUnbind)
    }

    fun scheduleUnbind(context: Context) {
        scheduledContext = context.applicationContext
        mainHandler?.removeCallbacks(pendingUnbind)
        mainHandler?.postDelayed(pendingUnbind, BACKGROUND_UNBIND_DELAY_MS)
    }

    fun runFireAndForget(context: Context, block: (IIsolatedMediaDecoder) -> Unit) {
        workerExecutor.execute {
            val decoder = ensureConnected(context) ?: return@execute
            runCatching { block(decoder) }
        }
    }

    fun <T> runWatchdogCall(
        context: Context,
        quarantineKey: String,
        quarantine: IsolatedFailureQuarantine,
        block: (IIsolatedMediaDecoder) -> T?,
    ): T? {
        val decoder = ensureConnected(context) ?: return null
        val future = workerExecutor.submit<T?> { block(decoder) }
        return try {
            future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            quarantine.recordFailure(quarantineKey)
            resetConnection(context)
            null
        } catch (err: ExecutionException) {
            if (err.cause is DeadObjectException || err.cause is RemoteException) {
                quarantine.recordFailure(quarantineKey)
                resetConnection(context)
            }
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private fun ensureConnected(context: Context): IIsolatedMediaDecoder? {
        val alive = service?.takeIf { it.asBinder().isBinderAlive }
        if (alive != null) return alive
        val latch = bindIfNeeded(context.applicationContext) ?: return null
        runCatching { latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        return service?.takeIf { it.asBinder().isBinderAlive }
    }

    private fun bindIfNeeded(appCtx: Context): CountDownLatch? {
        synchronized(lock) {
            if (isBound) return connectLatch
            val newLatch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    service = IIsolatedMediaDecoder.Stub.asInterface(binder)
                    connectLatch?.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }

                override fun onBindingDied(name: ComponentName?) {
                    service = null
                    isBound = false
                    connectLatch?.countDown()
                }
            }
            connectLatch = newLatch
            activeConnection = conn
            val bound = runCatching {
                val intent = Intent(appCtx, IsolatedMediaDecoderService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appCtx.bindService(intent, Context.BIND_AUTO_CREATE, workerExecutor, conn)
                } else {
                    appCtx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                }
            }.getOrDefault(false)
            if (!bound) {
                activeConnection = null
                connectLatch = null
                return null
            }
            isBound = true
            return newLatch
        }
    }

    private fun resetConnection(context: Context) {
        val conn: ServiceConnection?
        synchronized(lock) {
            service = null
            isBound = false
            connectLatch?.countDown()
            connectLatch = null
            conn = activeConnection
            activeConnection = null
        }
        if (conn != null) {
            runCatching { context.applicationContext.unbindService(conn) }
        }
    }
}
