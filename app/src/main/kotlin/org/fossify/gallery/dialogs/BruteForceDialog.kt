package org.fossify.gallery.dialogs

import android.app.Activity
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
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
import org.fossify.gallery.helpers.ARGON2_MIN_MEASURED_MS
import org.fossify.gallery.helpers.ARGON2_ULTRA_MEMORY_KB
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DeviceMemoryCapabilities
import org.fossify.gallery.helpers.calculateTieredArgon2Params
import org.fossify.gallery.helpers.deviceMemoryCapabilities
import org.fossify.gallery.helpers.ensureKdfEngineFresh
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
    private const val WARNING_ALPHA = 0.85f
    private const val WARNING_TEXT_SP = 13f

    private fun deviceId() = Build.FINGERPRINT + "|" + Build.MODEL

    /** Clears device-bound measurements when the engine changed or the saved target came from another device. */
    fun ensureFreshBaseline(activity: Activity) {
        val config = activity.config
        ensureKdfEngineFresh(config)
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

    private fun parseTargetMsOrNull(
        raw: CharSequence?,
        unit: String,
        minSec: Float
    ): Int? {
        val parsed = raw?.toString()?.replace(',', '.')?.replace(unit, "")?.trim()
            ?.toFloatOrNull() ?: return null
        val clampedSec = parsed.coerceIn(minSec, MAX_SECONDS)
        return Math.round(clampedSec * MS_PER_SECOND)
    }

    private fun shouldShowHighMemoryWarning(
        targetMs: Int,
        rawStdMs: Int,
        capabilities: DeviceMemoryCapabilities
    ): Boolean {
        val (memKb, _, _) = calculateTieredArgon2Params(targetMs, rawStdMs, capabilities)
        return memKb >= ARGON2_ULTRA_MEMORY_KB
    }

    private fun attachTargetControls(
        valueInput: MyEditText,
        seek: MySeekBar,
        minTenths: Int,
        unit: String,
        minSec: Float,
        onTargetMsChanged: (Int) -> Unit
    ) {
        fun formatted(tenths: Int) = String.format(Locale.US, "%.1f %s", tenths / TENTHS_PER_SECOND, unit)
        valueInput.isEnabled = MAX_TENTHS > minTenths
        valueInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                parseTargetMsOrNull(s, unit, minSec)?.let(onTargetMsChanged)
            }
        })
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val tenths = minTenths + p
                valueInput.setText(formatted(tenths))
                onTargetMsChanged(tenths * MS_PER_TENTH)
            }

            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
    }

    private fun showTimeDialogWithMeasurement(activity: Activity, onChanged: () -> Unit) {
        val config = activity.config
        val rawStdMs = config.cachedStdArgon2Ms
            .coerceIn(ARGON2_MIN_MEASURED_MS, Config.MAX_UNLOCK_TARGET_MS)
        val stdMsForSlider = rawStdMs
            .coerceIn(Config.MIN_UNLOCK_TARGET_MS, Config.MAX_UNLOCK_TARGET_MS)
        val minTenths = ((stdMsForSlider + TENTHS_ROUND_UP_MS) / MS_PER_TENTH)
            .coerceIn(MIN_TENTHS, MAX_TENTHS)
        val minSec = minTenths / TENTHS_PER_SECOND
        val preferredMs = config.unlockTargetMs
            .coerceIn(minTenths * MS_PER_TENTH, MAX_TENTHS * MS_PER_TENTH)
        val initialTenths = (preferredMs / MS_PER_TENTH).coerceIn(minTenths, MAX_TENTHS)
        val capabilities = deviceMemoryCapabilities(activity)

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
        val initialFormatted = String.format(Locale.US, "%.1f %s", initialTenths / TENTHS_PER_SECOND, unit)
        val valueInput = MyEditText(ContextThemeWrapper(activity, R.style.VaultCursorTheme)).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = VALUE_TEXT_SP
            gravity = Gravity.CENTER
            background = null
            setText(initialFormatted)
            applyThemedCursor(activity.config.let { AppearanceManager.resolve(activity, it) }.primary)
            applyThemedSelectionHandles(themeTextColor())
        }
        val seek = MySeekBar(activity).apply {
            max = (MAX_TENTHS - minTenths).coerceAtLeast(0)
            progress = (initialTenths - minTenths).coerceAtLeast(0)
            isEnabled = MAX_TENTHS > minTenths
        }
        val highMemoryWarning = MyTextView(activity).apply {
            setText(R.string.brute_force_high_memory_warning)
            alpha = WARNING_ALPHA
            textSize = WARNING_TEXT_SP
        }
        fun updateWarningForTarget(targetMs: Int) {
            highMemoryWarning.visibility =
                if (shouldShowHighMemoryWarning(targetMs, rawStdMs, capabilities)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
        updateWarningForTarget(initialTenths * MS_PER_TENTH)
        attachTargetControls(valueInput, seek, minTenths, unit, minSec, ::updateWarningForTarget)
        view.addView(hint)
        view.addView(valueInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = padding })
        view.addView(seek)
        view.addView(highMemoryWarning, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = padding / 2 })

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(org.fossify.commons.R.string.ok) { dialog, _ ->
                val targetMs = parseTargetMsOrNull(valueInput.text, unit, minSec)
                    ?: (initialTenths * MS_PER_TENTH)
                val (memKb, iters, clampedFlag) =
                    calculateTieredArgon2Params(targetMs, rawStdMs, capabilities)
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
