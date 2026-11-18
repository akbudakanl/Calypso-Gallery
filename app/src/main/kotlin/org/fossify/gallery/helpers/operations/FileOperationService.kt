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
import org.fossify.gallery.R

/**
 * ADR 0002 §7 / ADR 0003 §7: keeps the process alive for the duration of a registered file
 * operation. Keys stay in RAM while this service runs; it is released as soon as the operation
 * ends so the normal "background → lock vault" rule applies again.
 */
class FileOperationService : Service() {

    companion object {
        private const val CHANNEL_ID = "cipher_file_operations"
        private const val NOTIFICATION_ID = 4103
        private const val EXTRA_LABEL = "label"
        private const val ACTION_CANCEL = "org.fossify.gallery.action.CANCEL_FILE_OPERATION"

        @Volatile
        private var shouldRun = false

        @Volatile
        private var activeInstance: FileOperationService? = null

        @Volatile
        private var currentLabel: String = ""

        fun start(context: Context, label: String) {
            shouldRun = true
            currentLabel = label
            val instance = activeInstance
            if (instance != null) {
                instance.updateNotification(label)
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
            activeInstance?.updateNotification(label)
        }

        fun stop(context: Context) {
            shouldRun = false
            val instance = activeInstance
            if (instance != null && instance.foregroundStarted) {
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
        ensureChannel()
        val notification = buildNotification(label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true

        if (!shouldRun) {
            stopSelfSafe()
            return START_NOT_STICKY
        }

        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cipher:file_operation")
                .also { it.acquire(30 * 60 * 1000L) }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.resume_operations_title),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
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

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(label)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(cancelAction)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(label)
                .setOngoing(true)
                .addAction(cancelAction)
                .build()
        }
    }

    private fun updateNotification(label: String) {
        if (!foregroundStarted) return
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(label))
        }
    }

    private fun stopSelfSafe() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        foregroundStarted = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}
