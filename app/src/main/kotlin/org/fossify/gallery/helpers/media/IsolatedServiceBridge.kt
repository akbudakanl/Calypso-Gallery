package org.fossify.gallery.helpers.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 20_000L
private const val GIF_REQUEST_TIMEOUT_MS = 5_000L
private const val BACKGROUND_UNBIND_DELAY_MS = 15_000L
private const val DECODE_POOL_THREADS = 4
private const val GIF_POOL_THREADS = 2

internal enum class IsolatedDecoderChannel {
    PUBLIC,
    VAULT,
}

internal object IsolatedServiceBridge {
    private val bindExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "calypso-iso-bind").apply { isDaemon = true }
    }
    private val decodeExecutor: ExecutorService = Executors.newFixedThreadPool(DECODE_POOL_THREADS) { runnable ->
        Thread(runnable, "calypso-iso-decode").apply { isDaemon = true }
    }
    private val gifExecutor: ExecutorService = Executors.newFixedThreadPool(GIF_POOL_THREADS) { runnable ->
        Thread(runnable, "calypso-iso-gif").apply { isDaemon = true }
    }
    private val mainHandler = runCatching { Handler(Looper.getMainLooper()) }.getOrNull()

    private val publicChannel = ChannelConnection(IsolatedMediaDecoderService::class.java)
    private val vaultChannel = ChannelConnection(VaultIsolatedMediaDecoderService::class.java)

    private fun channelFor(channel: IsolatedDecoderChannel): ChannelConnection {
        return if (channel == IsolatedDecoderChannel.VAULT) vaultChannel else publicChannel
    }

    fun cancelPendingUnbind() {
        publicChannel.cancelPendingUnbind()
        vaultChannel.cancelPendingUnbind()
    }

    fun scheduleUnbind(context: Context) {
        publicChannel.scheduleUnbind(context)
        vaultChannel.scheduleUnbind(context)
    }

    fun unbindVaultImmediately(context: Context?) {
        val appCtx = context?.applicationContext ?: return
        vaultChannel.cancelPendingUnbind()
        vaultChannel.resetConnection(appCtx)
    }

    fun runFireAndForget(
        context: Context,
        channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
        block: (IIsolatedMediaDecoder) -> Unit,
    ) {
        val conn = channelFor(channel)
        decodeExecutor.execute {
            val decoder = conn.ensureConnected(context) ?: return@execute
            runCatching { block(decoder) }
        }
    }

    fun <T> runWatchdogCall(
        context: Context,
        quarantineKey: String,
        quarantine: IsolatedFailureQuarantine,
        channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
        block: (IIsolatedMediaDecoder) -> T?,
    ): T? {
        return channelFor(channel).runWatchdogOnExecutor(
            executor = decodeExecutor,
            timeoutMs = REQUEST_TIMEOUT_MS,
            context = context,
            quarantineKey = quarantineKey,
            quarantine = quarantine,
            block = block,
        )
    }

    fun <T> runGifWatchdogCall(
        context: Context,
        quarantineKey: String,
        quarantine: IsolatedFailureQuarantine,
        channel: IsolatedDecoderChannel = IsolatedDecoderChannel.PUBLIC,
        block: (IIsolatedMediaDecoder) -> T?,
    ): T? {
        return channelFor(channel).runWatchdogOnExecutor(
            executor = gifExecutor,
            timeoutMs = GIF_REQUEST_TIMEOUT_MS,
            context = context,
            quarantineKey = quarantineKey,
            quarantine = quarantine,
            block = block,
        )
    }

    private class ChannelConnection(
        private val serviceClass: Class<out IsolatedMediaDecoderService>,
    ) {
        private val lock = Any()
        private val connectionGen = AtomicLong(0L)

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

        fun <T> runWatchdogOnExecutor(
            executor: ExecutorService,
            timeoutMs: Long,
            context: Context,
            quarantineKey: String,
            quarantine: IsolatedFailureQuarantine,
            block: (IIsolatedMediaDecoder) -> T?,
            allowRetry: Boolean = true,
        ): T? {
            val genBefore = connectionGen.get()
            val decoder = ensureConnected(context) ?: return null
            val future = executor.submit<T?> { block(decoder) }
            var outcome: T? = null
            var shouldRetry = false
            try {
                outcome = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                quarantine.recordFailure(quarantineKey)
                resetConnection(context)
            } catch (err: ExecutionException) {
                shouldRetry = handleWatchdogExecutionError(
                    err = err,
                    genBefore = genBefore,
                    context = context,
                    quarantineKey = quarantineKey,
                    quarantine = quarantine,
                    allowRetry = allowRetry,
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (shouldRetry) {
                return runWatchdogOnExecutor(executor, timeoutMs, context, quarantineKey, quarantine, block, false)
            }
            return outcome
        }

        private fun handleWatchdogExecutionError(
            err: ExecutionException,
            genBefore: Long,
            context: Context,
            quarantineKey: String,
            quarantine: IsolatedFailureQuarantine,
            allowRetry: Boolean,
        ): Boolean {
            val cause = err.cause
            if (cause !is DeadObjectException && cause !is RemoteException) {
                return false
            }
            val genChanged = connectionGen.get() != genBefore
            if (!genChanged) {
                if (quarantineKey.isNotEmpty()) quarantine.recordFailure(quarantineKey)
                resetConnection(context)
            }
            return allowRetry && (genChanged || quarantineKey.isEmpty())
        }

        fun ensureConnected(context: Context): IIsolatedMediaDecoder? {
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
                        connectionGen.incrementAndGet()
                        connectLatch?.countDown()
                    }
                }
                connectLatch = newLatch
                activeConnection = conn
                val bound = runCatching {
                    val intent = Intent(appCtx, serviceClass)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appCtx.bindService(intent, Context.BIND_AUTO_CREATE, bindExecutor, conn)
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

        fun resetConnection(context: Context) {
            val conn: ServiceConnection?
            synchronized(lock) {
                service = null
                isBound = false
                connectionGen.incrementAndGet()
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
}
