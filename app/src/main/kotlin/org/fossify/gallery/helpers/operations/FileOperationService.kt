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
import org.fossify.gallery.App
import org.fossify.gallery.R
import org.fossify.gallery.helpers.OperationNotificationCategory

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
        private const val CHANNEL_ID = "cipher_file_operations"
        private const val NOTIFICATION_ID = 4103
        private const val EXTRA_LABEL = "label"
        private const val ACTION_CANCEL = "org.fossify.gallery.action.CANCEL_FILE_OPERATION"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
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

        fun start(context: Context, label: String, showProgress: Boolean = true) {
            shouldRun = true
            currentLabel = label
            progressEnabled = showProgress
            progressCurrent = 0
            progressMax = 0
            progressText = null
            val instance = activeInstance
            if (instance != null) {
                instance.refreshNotification()
                return
            }
            runCatching {
                val intent = Intent(context, FileOperationService::class.java).putExtra(EXTRA_LABEL, label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun updateStatus(context: Context, label: String) {
            currentLabel = label
            activeInstance?.refreshNotification()
        }

        /** Re-renders the notification when the app moves between foreground and background. */
        fun refreshVisibility(context: Context) {
            activeInstance?.refreshNotification()
        }

        /** Publishes progress for the currently registered operation. */
        fun updateProgress(current: Int, max: Int, text: String?) {
            if (!progressEnabled) return
            progressCurrent = current.coerceAtLeast(0)
            progressMax = max.coerceAtLeast(0)
            // Defense in depth: only the anonymous "n / total (x%)" summary may reach a
            // notification. Even if a future caller passes a file name, it is dropped here.
            progressText = text?.takeIf { COUNT_SUMMARY_REGEX.matches(it) }
            activeInstance?.refreshNotification()
        }

        fun stop(context: Context) {
            shouldRun = false
            val instance = activeInstance
            if (instance != null) {
                runCatching { instance.stopSelfSafe() }
            }
            // If activeInstance is null, startForegroundService() may still be in flight;
            // onStartCommand() will call startForeground() and immediately stopSelfSafe().
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
        ensureChannel()
        val notification = buildNotification(label)
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

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.file_operations_notifications),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun titleFor(label: String): String {
        val category = OperationNotificationCategory.fromOwner(label)
        return getString(
            when (category) {
                OperationNotificationCategory.COPY_MOVE -> R.string.file_operation_copy_move
                OperationNotificationCategory.CRYPTO -> R.string.file_operation_crypto
                OperationNotificationCategory.DELETE -> R.string.file_operation_delete
                OperationNotificationCategory.VIRTUAL_ALBUM -> R.string.file_operation_virtual_album
                else -> R.string.file_operation_generic
            }
        )
    }

    private fun buildNotification(label: String): Notification {
        val cancelIntent = Intent(this, FileOperationService::class.java).setAction(ACTION_CANCEL)
        val cancelFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val cancelPending = PendingIntent.getService(this, 0, cancelIntent, cancelFlags)
        val cancelAction = Notification.Action.Builder(
            null,
            getString(android.R.string.cancel),
            cancelPending
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(titleFor(label))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(cancelAction)

        // Android requires a foreground-service notification for the whole operation, so it cannot
        // be removed while the app is in the foreground. What we *can* do is keep it minimal there
        // (the on-screen progress dialog is already visible) and only show detailed progress after
        // the user actually backgrounds the app. No file names or paths are ever posted.
        if (progressEnabled && !App.isAppInForeground) {
            val max = progressMax
            builder.setProgress(max, progressCurrent.coerceAtMost(maxOf(max, 1)), max <= 0)
            progressText?.let { builder.setContentText(it) }
        }
        return builder.build()
    }

    private fun refreshNotification() {
        if (!foregroundStarted) return
        if (shouldRun) {
            acquireOrRefreshWakeLock()
        }
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(currentLabel))
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
