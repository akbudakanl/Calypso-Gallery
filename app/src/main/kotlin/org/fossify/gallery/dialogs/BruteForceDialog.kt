package org.fossify.gallery.dialogs

import android.app.Activity
import android.os.Build
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyEditText
import org.fossify.commons.views.MySeekBar
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.applyThemedCursor
import org.fossify.gallery.extensions.applyThemedSelectionHandles
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.themeTextColor
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.calculateTieredArgon2Params
import org.fossify.gallery.helpers.measureStandardArgon2Ms
import org.fossify.gallery.helpers.readVaultKdfParams
import java.util.Locale

/**
 * "Hedeflenen kasa açılış süresi" ayarı.
 *
 * Only seconds are exposed to the user: the chosen value is saved together with the device it was
 * calibrated for. As long as the same device and vault are present it is never recalculated; after
 * a device change or vault removal the summary stays empty and opening the dialog measures again.
 */
object BruteForceDialog {
    private const val MS_PER_SECOND = 1000f
    private const val TENTHS_PER_SECOND = 10f
    private const val MS_PER_TENTH = 100
    private const val TENTHS_ROUND_UP_MS = 99
    private const val MIN_TENTHS = 1
    private const val MAX_TENTHS = 100
    private const val MAX_SECONDS = 10.0f
    private const val DIALOG_PADDING_DP = 20
    private const val VALUE_TEXT_SP = 40f

    private fun deviceId() = Build.FINGERPRINT + "|" + Build.MODEL

    /** Clears device-bound measurements when the saved target came from another device. */
    fun ensureFreshBaseline(activity: Activity) {
        val config = activity.config
        if (config.vaultTargetDevice.isNotEmpty() && config.vaultTargetDevice != deviceId()) {
            config.vaultTargetSaved = false
            config.cachedStdArgon2Ms = 0
            config.vaultUnlockMeasuredMs = 0
            config.vaultTargetDevice = ""
        }
    }

    /** Empty until the user has chosen a value for this device; no MiB/rounds anywhere. */
    fun summary(activity: Activity): String {
        val config = activity.config
        if (CryptoManager.readVaultKdfParams(activity) == null) return ""
        ensureFreshBaseline(activity)
        if (!config.vaultTargetSaved || config.vaultTargetDevice != deviceId()) return ""
        val unit = activity.getString(R.string.vault_unlock_time_unit)
        return String.format(Locale.US, "%.1f %s", config.unlockTargetMs / MS_PER_SECOND, unit)
    }

    fun show(activity: Activity, onChanged: () -> Unit) {
        ensureFreshBaseline(activity)
        if (activity.config.cachedStdArgon2Ms > 0) {
            showTimeDialogWithMeasurement(activity, onChanged)
            return
        }
        val padding = (DIALOG_PADDING_DP * activity.resources.displayMetrics.density).toInt()
        val messageView = MyTextView(activity).apply {
            setPadding(padding, padding, padding, padding)
            setText(R.string.measuring_device_kdf_speed)
        }
        var progressDialog: AlertDialog? = null
        activity.getAlertDialogBuilder()
            .setCancelable(false)
            .apply {
                activity.setupDialogStuff(messageView, this, cancelOnTouchOutside = false) { dialog ->
                    progressDialog = dialog
                }
            }
        ensureBackgroundThread {
            CryptoManager.measureStandardArgon2Ms(activity)
            activity.runOnUiThread {
                runCatching { progressDialog?.dismiss() }
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showTimeDialogWithMeasurement(activity, onChanged)
                }
            }
        }
    }

    private fun showTimeDialogWithMeasurement(activity: Activity, onChanged: () -> Unit) {
        val config = activity.config
        val stdMs = config.cachedStdArgon2Ms
            .coerceIn(Config.MIN_UNLOCK_TARGET_MS, Config.MAX_UNLOCK_TARGET_MS)
        val minTenths = ((stdMs + TENTHS_ROUND_UP_MS) / MS_PER_TENTH).coerceIn(MIN_TENTHS, MAX_TENTHS)
        val minSec = minTenths / TENTHS_PER_SECOND
        val preferredMs = config.unlockTargetMs.coerceIn(minTenths * MS_PER_TENTH, MAX_TENTHS * MS_PER_TENTH)
        val initialTenths = (preferredMs / MS_PER_TENTH).coerceIn(minTenths, MAX_TENTHS)

        val padding = (DIALOG_PADDING_DP * activity.resources.displayMetrics.density).toInt()
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val hint = MyTextView(activity).apply {
            text = buildString {
                append(activity.getString(R.string.brute_force_time_hint))
                if (minSec > 1f) {
                    append(' ')
                    append(activity.getString(R.string.brute_force_time_min_hint))
                }
            }
        }
        // The value and the slider are one control: the number is editable in place, with no box.
        val unit = activity.getString(R.string.vault_unlock_time_unit)
        fun formatted(tenths: Int) = String.format(Locale.US, "%.1f %s", tenths / TENTHS_PER_SECOND, unit)
        val valueInput = MyEditText(ContextThemeWrapper(activity, R.style.VaultCursorTheme)).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = VALUE_TEXT_SP
            gravity = Gravity.CENTER
            background = null
            setText(formatted(initialTenths))
            applyThemedCursor(activity.config.let { AppearanceManager.resolve(activity, it) }.primary)
            applyThemedSelectionHandles(themeTextColor())
        }
        val seek = MySeekBar(activity).apply {
            max = (MAX_TENTHS - minTenths).coerceAtLeast(0)
            progress = (initialTenths - minTenths).coerceAtLeast(0)
            isEnabled = MAX_TENTHS > minTenths
        }
        valueInput.isEnabled = MAX_TENTHS > minTenths
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                valueInput.setText(formatted(minTenths + p))
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        view.addView(hint)
        view.addView(valueInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = padding })
        view.addView(seek)

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(org.fossify.commons.R.string.ok) { dialog, _ ->
                val parsed = valueInput.text.toString().replace(',', '.').replace(unit, "").trim()
                    .toFloatOrNull() ?: (initialTenths / TENTHS_PER_SECOND)
                val clampedSec = parsed.coerceIn(minSec, MAX_SECONDS)
                val targetMs = Math.round(clampedSec * MS_PER_SECOND)
                val maxHeapKb = (Runtime.getRuntime().maxMemory() / 1024L).coerceAtLeast(1L)
                val (memKb, iters, clampedFlag) = calculateTieredArgon2Params(targetMs, stdMs, maxHeapKb)
                config.unlockTargetMs = targetMs
                config.lastCalibratedMemoryKb = memKb
                config.lastCalibratedIterations = iters
                config.argon2CalibrationClamped = clampedFlag
                config.vaultTargetSaved = true
                config.vaultTargetDevice = deviceId()
                onChanged()
                dialog.dismiss()
            }
            .apply {
                activity.setupDialogStuff(view, this, titleId = R.string.brute_force_time_title)
            }
    }
}
