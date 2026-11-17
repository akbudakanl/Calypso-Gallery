package org.fossify.gallery.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.fossify.gallery.R
import org.fossify.gallery.helpers.vault.VaultMigration
import java.util.Base64

/**
 * v3 -> v4 kasa donusumunu arka planda, anahtar RAM'de kalarak calistirir.
 * Tamamlaninca KEK'i pending-finalize olarak saklar; sonraki unlock sirasinda
 * .vault kanonik başlık yazilir (sifre servise tasinmaz).
 */
class VaultMigrationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cipher:vault_migration")
            .apply { acquire() }
        startForeground(NOTIFICATION_ID, notification(0, 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            VaultMigration.isRunning = true
            try {
                val result = VaultMigration.migrate(this@VaultMigrationService) { p ->
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(WAITING, p.waiting).apply()
                    notify(p.converted, p.total, p.failed, waiting = p.waiting)
                }
                if (result.done) {
                    VaultMigration.savedKek(this@VaultMigrationService)?.let { kek ->
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(PENDING_KEK, Base64.getEncoder().encodeToString(kek))
                            .apply()
                        kek.fill(0)
                    }
                    notify(result.converted, result.total, 0, finished = true)
                } else {
                    notify(result.converted, result.total, result.failed, isFailed = true)
                }
            } finally {
                VaultMigration.isRunning = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        VaultMigration.cancelRequested = true
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.vault_upgrade_entry_title), NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(
        converted: Int,
        total: Int,
        failed: Int,
        finished: Boolean = false,
        isFailed: Boolean = false,
        waiting: Boolean = false
    ): Notification {
        val text = when {
            finished -> getString(R.string.vault_upgrade_finished)
            waiting -> getString(R.string.vault_upgrade_waiting)
            isFailed -> getString(R.string.vault_upgrade_failed_count, failed)
            else -> getString(R.string.vault_upgrade_progress, converted, total)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.vault_upgrade_entry_title))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!finished && !isFailed)
            .setProgress(if (total > 0) 100 else 0, if (total > 0) converted * 100 / total else 0, total == 0)
            .build()
    }

    private fun notify(
        converted: Int,
        total: Int,
        failed: Int,
        finished: Boolean = false,
        isFailed: Boolean = false,
        waiting: Boolean = false
    ) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(converted, total, failed, finished, isFailed, waiting))
    }

    companion object {
        private const val CHANNEL_ID = "cipher_vault_migration"
        private const val NOTIFICATION_ID = 4491
        const val PREFS = "vault_migration_pending"
        const val PENDING_KEK = "pending_kek"
        const val WAITING = "migration_waiting"

        fun start(context: Context) {
            val intent = Intent(context, VaultMigrationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
