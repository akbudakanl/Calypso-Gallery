package org.fossify.gallery.helpers.operations

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import org.fossify.gallery.App
import org.fossify.gallery.R
import org.fossify.gallery.helpers.OperationNotificationCategory
import org.fossify.gallery.helpers.OperationProgressFormatter

/**
 * ADR 0002 §7 / ADR 0003 §7: keeps the process alive for the duration of a registered file
 * operation. Keys stay in RAM while this service runs; it is released as soon as the operation
 * ends so the normal "background → lock vault" rule applies again.
 *
 * This is also the single user-facing notification for *all* file operations. Progress is posted
 * here (instead of a second, independent progress notification) so copy/move, encrypt/decrypt,
 * delete and album updates all share one channel, one notification id and one cancel action.
 */
class FileOperationService : Service() {

    companion object {
        private const val TAG = "FileOperationService"
        internal const val CHANNEL_ID = "cipher_file_operations_v2"
        private const val LEGACY_CHANNEL_ID = "cipher_file_operations"
        private const val NOTIFICATION_ID = 4103
        private const val EXTRA_LABEL = "label"
        private const val ACTION_CANCEL = "org.fossify.gallery.action.CANCEL_FILE_OPERATION"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 250L
        private val COUNT_SUMMARY_REGEX = Regex("""^\d+ / \d+ \(\d+%\)$""")

        @Volatile
        private var shouldRun = false

        @Volatile
        private var activeInstance: FileOperationService? = null

        @Volatile
        private var currentLabel: String = ""

        @Volatile
        private var progressEnabled = true

        @Volatile
        private var progressCurrent = 0

        @Volatile
        private var progressMax = 0

        @Volatile
        private var progressText: String? = null

        @Volatile
        private var lastNotifyElapsedMs = 0L

        fun start(context: Context, label: String, showProgress: Boolean = true) {
            val appContext = context.applicationContext
            shouldRun = true
            currentLabel = label
            progressEnabled = showProgress
            progressCurrent = 0
            progressMax = 0
            progressText = null
            lastNotifyElapsedMs = 0L
            val instance = activeInstance
            if (instance != null && instance.foregroundStarted) {
                instance.refreshNotification()
                return
            }
            // Post immediately on the shared channel/id so the user sees feedback at t=0ms
            // and still gets notifications even if background FGS launch restrictions apply.
            postNotificationDirect(appContext, label)
            runCatching {
                val intent = Intent(appContext, FileOperationService::class.java).putExtra(EXTRA_LABEL, label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }.onFailure { err ->
                runCatching { Log.w(TAG, "Unable to start FileOperationService", err) }
            }
        }

        fun updateStatus(context: Context, label: String) {
            currentLabel = label
            if (!shouldRun) return
            val instance = activeInstance
            if (instance != null && instance.foregroundStarted) {
                instance.refreshNotification()
            } else {
                postNotificationDirect(context.applicationContext, label)
            }
        }

        /** Re-renders the notification when the app moves between foreground and background. */
        fun refreshVisibility(context: Context) {
            lastNotifyElapsedMs = 0L
            if (!shouldRun) return
            val instance = activeInstance
            if (instance != null && instance.foregroundStarted) {
                instance.refreshNotification()
            } else {
                postNotificationDirect(context.applicationContext, currentLabel)
            }
        }

        /** Publishes progress for the currently registered operation. */
        fun updateProgress(context: Context, current: Int, max: Int, text: String?) {
            if (!shouldRun || !progressEnabled) return
            progressCurrent = current.coerceAtLeast(0)
            progressMax = max.coerceAtLeast(0)
            // Defense in depth: only the anonymous "n / total (x%)" summary may reach a
            // notification. Even if a future caller passes a file name, it is dropped here.
            if (text != null) {
                progressText = text.takeIf { COUNT_SUMMARY_REGEX.matches(it) }
            }
            if (App.isAppInForeground) return
            val now = runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
            val isBoundary = current == 0 || (max > 0 && current >= max)
            if (isBoundary || now - lastNotifyElapsedMs >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                val instance = activeInstance
                if (instance != null && instance.foregroundStarted) {
                    instance.refreshNotification()
                } else {
                    postNotificationDirect(context.applicationContext, currentLabel)
                }
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            shouldRun = false
            val instance = activeInstance
            if (instance != null) {
                runCatching { instance.stopSelfSafe() }
            }
            // Cancel any directly posted fallback notification and ensure no completion
            // notification remains on screen after the operation finishes.
            runCatching {
                appContext.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            }
        }

        internal fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    val manager = context.getSystemService(NotificationManager::class.java) ?: return
                    runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.file_operations_notifications),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        enableLights(false)
                        enableVibration(false)
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        private fun titleFor(context: Context, label: String): String {
            val category = OperationNotificationCategory.fromOwner(label)
            return context.getString(
                when (category) {
                    OperationNotificationCategory.COPY_MOVE -> R.string.file_operation_copy_move
                    OperationNotificationCategory.CRYPTO -> R.string.file_operation_crypto
                    OperationNotificationCategory.DELETE -> R.string.file_operation_delete
                    OperationNotificationCategory.VIRTUAL_ALBUM -> R.string.file_operation_virtual_album
                    else -> R.string.file_operation_generic
                }
            )
        }

        private fun smallIconFor(label: String): Int {
            return when (OperationNotificationCategory.fromOwner(label)) {
                OperationNotificationCategory.DELETE -> R.drawable.ic_delete_calypso_vector
                OperationNotificationCategory.CRYPTO -> R.drawable.ic_encrypt_calypso_vector
                OperationNotificationCategory.VIRTUAL_ALBUM -> R.drawable.ic_album_virtual_vector
                else -> R.drawable.ic_files_vector
            }
        }

        private fun buildNotificationFor(context: Context, label: String): Notification {
            val cancelIntent = Intent(context, FileOperationService::class.java).setAction(ACTION_CANCEL)
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val cancelPending = PendingIntent.getService(context, 0, cancelIntent, pendingFlags)
            val cancelAction = Notification.Action.Builder(
                null,
                context.getString(android.R.string.cancel),
                cancelPending
            ).build()

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            builder.setSmallIcon(smallIconFor(label))
                .setContentTitle(titleFor(context, label))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(cancelAction)
            runCatching {
                context.packageManager?.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    PendingIntent.getActivity(context, 1, launchIntent, pendingFlags)
                }
            }.getOrNull()?.let { contentPending ->
                builder.setContentIntent(contentPending)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }

            // Two-tier notification structure:
            // - In foreground: minimal ongoing notification (on-screen dialog already shows details).
            // - In background: detailed progress bar + anonymous "current / total (x%)" summary.
            // File names, paths and vault contents are never posted to any notification.
            if (!App.isAppInForeground && progressEnabled) {
                val max = progressMax
                val bodyText = progressText ?: if (max > 0) {
                    OperationProgressFormatter.formatCountSummary(progressCurrent, max)
                } else {
                    context.getString(R.string.file_operation_generic)
                }
                builder.setProgress(max, progressCurrent.coerceAtMost(maxOf(max, 1)), max <= 0)
                builder.setContentText(bodyText)
            } else {
                builder.setContentText(context.getString(R.string.file_operation_generic))
            }
            return builder.build()
        }

        private fun postNotificationDirect(context: Context, label: String) {
            runCatching {
                ensureChannel(context)
                lastNotifyElapsedMs = runCatching { SystemClock.elapsedRealtime() }
                    .getOrElse { System.currentTimeMillis() }
                context.getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotificationFor(context, label))
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeInstance = this
        if (intent?.action == ACTION_CANCEL) {
            ForegroundOperationRegistry.requestCancel()
        }

        val label = intent?.getStringExtra(EXTRA_LABEL)
            ?: currentLabel.takeIf { it.isNotEmpty() }
            ?: getString(R.string.resume_operations_title)
        currentLabel = label
        ensureChannel(this)
        val notification = buildNotificationFor(this, label)
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Throwable) {
            false
        }
        foregroundStarted = started
        if (!started) {
            if (activeInstance === this) {
                activeInstance = null
            }
            releaseWakeLock()
            runCatching { stopSelf() }
            return START_NOT_STICKY
        }

        if (!shouldRun) {
            stopSelfSafe()
            return START_NOT_STICKY
        }

        acquireOrRefreshWakeLock()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        shouldRun = false
        stopSelfSafe()
    }

    private fun refreshNotification() {
        if (!foregroundStarted) {
            if (shouldRun) {
                postNotificationDirect(applicationContext, currentLabel)
            }
            return
        }
        lastNotifyElapsedMs = runCatching { SystemClock.elapsedRealtime() }
            .getOrElse { System.currentTimeMillis() }
        if (shouldRun) {
            acquireOrRefreshWakeLock()
        }
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotificationFor(this, currentLabel))
        }
    }

    private fun acquireOrRefreshWakeLock() {
        runCatching {
            val lock = wakeLock ?: run {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cipher:file_operation").apply {
                    setReferenceCounted(false)
                }.also { wakeLock = it }
            }
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        wakeLock = null
    }

    private fun stopSelfSafe() {
        if (activeInstance === this) {
            activeInstance = null
        }
        if (foregroundStarted) {
            foregroundStarted = false
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
        }
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
        releaseWakeLock()
        runCatching { stopSelf() }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        foregroundStarted = false
        releaseWakeLock()
        super.onDestroy()
    }
}
