package org.fossify.gallery.dialogs

import android.app.Activity
import android.content.Context
import android.os.BatteryManager
import android.os.StatFs
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.getVaultRoot
import org.fossify.gallery.helpers.vault.VaultMigration

/**
 * v3 kasa girisinde gosterilen iki asamali guncelleme uyarisi.
 * Kullanici devam etmezse kasa v3 olarak calismaya devam eder.
 */
class VaultUpgradeWarningDialog(private val activity: Activity) {

    init {
        show()
    }

    private fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (CryptoManager.getVaultFormat() != 3) return
        if (!activity.config.vaultUpgradeWarningAlwaysShow) return

        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            gravity = Gravity.START
        }
        val message = TextView(activity).apply {
            text = activity.getString(R.string.vault_upgrade_entry_message)
        }
        val alwaysShow = CheckBox(activity).apply {
            text = activity.getString(R.string.vault_upgrade_always_show)
            isChecked = activity.config.vaultUpgradeWarningAlwaysShow
        }
        view.addView(message)
        view.addView(alwaysShow)

        activity.getAlertDialogBuilder()
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_upgrade_continue) { dialog, _ ->
                activity.config.vaultUpgradeWarningAlwaysShow = alwaysShow.isChecked
                dialog.dismiss()
                showDetails()
            }
            .apply {
                activity.setupDialogStuff(
                    view = view,
                    dialog = this,
                    titleId = R.string.vault_upgrade_entry_title
                )
            }
    }

    private fun showDetails() {
        if (activity.isFinishing || activity.isDestroyed) return

        val batteryOk = runCatching {
            val bm = activity.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) >= 20
        }.getOrDefault(true)

        val spaceOk = runCatching {
            val root = CryptoManager.getVaultRoot(activity) ?: return@runCatching true
            val maxBin = root.walkTopDown().filter { it.isFile && it.extension == "bin" }
                .maxOfOrNull { it.length() } ?: 0L
            StatFs(root.path).availableBytes >= maxBin + 256L * 1024 * 1024
        }.getOrDefault(true)

        activity.getAlertDialogBuilder()
            .setTitle(R.string.vault_upgrade_details_title)
            .setMessage(R.string.vault_upgrade_details_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_upgrade_continue) { dialog, _ ->
                val acknowledged = activity.config.vaultUpgradePreflightAcknowledged
                when {
                    !acknowledged && !batteryOk -> {
                        Toast.makeText(activity, R.string.vault_upgrade_low_battery, Toast.LENGTH_LONG).show()
                    }
                    !acknowledged && !spaceOk -> {
                        Toast.makeText(activity, R.string.vault_upgrade_low_space, Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        activity.config.vaultUpgradePreflightAcknowledged = true
                        dialog.dismiss()
                        VaultMigrationProgressDialog(activity).show()
                        org.fossify.gallery.services.VaultMigrationService.start(activity)
                    }
                }
            }
            .show()
    }
}

/**
 * Yarida kalan bir migrasyon sonraki unlock'da sorulur. Kesinti tespit edildiginde
 * "her zaman sor" ayari zorla acilir — kullanici hâlâ kapatabilir, ancak her yeni
 * kesintide yeniden acilir.
 */
class VaultUpgradeResumeDialog(private val activity: Activity) {

    fun show(done: Int, total: Int, onContinue: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return

        // Kesinti sonrasi ayar zorla default on: kullanici asla habersiz kalmasin.
        activity.config.vaultUpgradeWarningAlwaysShow = true

        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            gravity = Gravity.START
        }
        val message = TextView(activity).apply {
            text = activity.getString(R.string.vault_upgrade_resume_message)
        }
        val alwaysAsk = CheckBox(activity).apply {
            text = activity.getString(R.string.vault_upgrade_always_show)
            isChecked = activity.config.vaultUpgradeWarningAlwaysShow
        }
        view.addView(message)
        view.addView(alwaysAsk)

        activity.getAlertDialogBuilder()
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_upgrade_continue) { dialog, _ ->
                activity.config.vaultUpgradeWarningAlwaysShow = alwaysAsk.isChecked
                dialog.dismiss()
                onContinue()
            }
            .apply {
                activity.setupDialogStuff(
                    view = view,
                    dialog = this,
                    titleId = R.string.vault_upgrade_resume_title
                )
            }
    }
}

/** Modal ilerleme ekrani: kullanicinin hareketini engeller ve kalici uyari gosterir. */
class VaultMigrationProgressDialog(private val activity: Activity) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dialog: android.app.Dialog? = null

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            gravity = Gravity.CENTER
        }
        val label = TextView(activity).apply {
            text = activity.getString(R.string.vault_upgrade_progress, 0, 0)
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val warning = TextView(activity).apply {
            text = activity.getString(R.string.vault_upgrade_progress_warning)
            gravity = Gravity.CENTER
        }
        view.addView(label)
        view.addView(bar)
        view.addView(warning)

        dialog = activity.getAlertDialogBuilder()
            .setTitle(R.string.vault_upgrade_entry_title)
            .setView(view)
            .setCancelable(false)
            .create()
            .apply {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                show()
            }

        scope.launch {
            while (true) {
                val progress = withContext(Dispatchers.IO) {
                    VaultMigration.incompleteProgress(activity)
                }
                if (progress == null) {
                    dismiss()
                    break
                }
                val waiting = activity.getSharedPreferences(
                    org.fossify.gallery.services.VaultMigrationService.PREFS,
                    android.content.Context.MODE_PRIVATE
                ).getBoolean(org.fossify.gallery.services.VaultMigrationService.WAITING, false)
                label.text = if (waiting) {
                    activity.getString(R.string.vault_upgrade_waiting)
                } else {
                    activity.getString(R.string.vault_upgrade_progress, progress.first, progress.second)
                }
                bar.isIndeterminate = false
                bar.progress = progress.first * 100 / progress.second.coerceAtLeast(1)
                delay(500L)
            }
        }
    }

    fun dismiss() {
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dialog?.dismiss()
        dialog = null
        scope.cancel()
    }
}
