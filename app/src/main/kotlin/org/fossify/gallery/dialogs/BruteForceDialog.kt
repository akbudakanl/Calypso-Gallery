package org.fossify.gallery.dialogs

import android.app.Activity
import android.text.InputType
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
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.calculateTieredArgon2Params
import org.fossify.gallery.helpers.effectiveVaultUnlockMs
import org.fossify.gallery.helpers.measureStandardArgon2Ms
import org.fossify.gallery.helpers.readVaultKdfParams
import java.util.Locale

/** "Brute-Force direnci" ayari: hedef kasa acilis suresi. */
object BruteForceDialog {
    private const val KIB_PER_MIB = 1024
    private const val BYTES_PER_KIB = 1024L
    private const val MS_PER_SECOND = 1000f
    private const val TENTHS_PER_SECOND = 10f
    private const val MS_PER_TENTH = 100
    private const val TENTHS_ROUND_UP_MS = 99
    private const val MIN_TENTHS = 1
    private const val MAX_TENTHS = 100
    private const val MAX_SECONDS = 10.0f
    private const val DIALOG_PADDING_DP = 20
    private const val VALUE_TEXT_SP = 18f

    fun summary(activity: Activity): String {
        val config = activity.config
        val vaultParams = CryptoManager.readVaultKdfParams(activity)
        val vaultMs = CryptoManager.effectiveVaultUnlockMs(activity, vaultParams)
        // A vault exists but this device has neither a cached baseline nor a real unlock
        // measurement: show only the portable stored parameters, never a misleading time.
        if (vaultParams != null && vaultMs <= 0) {
            return activity.getString(
                R.string.brute_force_actual_params,
                vaultParams.memoryKb / KIB_PER_MIB,
                vaultParams.iterations
            )
        }
        val hasVault = vaultMs > 0
        val shownMs = if (hasVault) vaultMs else config.unlockTargetMs
        val valueRes = if (hasVault) R.string.brute_force_time_value_device else R.string.brute_force_time_value
        val base = activity.getString(valueRes, shownMs / MS_PER_SECOND)
        val clamped = if (!hasVault && config.argon2CalibrationClamped) {
            activity.getString(R.string.brute_force_clamped)
        } else {
            ""
        }
        val memMb = (vaultParams?.memoryKb ?: config.lastCalibratedMemoryKb) / KIB_PER_MIB
        val iters = vaultParams?.iterations ?: config.lastCalibratedIterations
        val params = activity.getString(R.string.brute_force_actual_params, memMb, iters)
        // Sub-second values are selectable (down to the KDF floor); only values above the
        // 10 s slider maximum need an explicit note.
        val outOfRange = if (hasVault && shownMs > Config.MAX_UNLOCK_TARGET_MS) {
            "\n" + activity.getString(R.string.brute_force_time_out_of_range)
        } else {
            ""
        }
        return "$base$clamped\n$params$outOfRange"
    }

    fun show(activity: Activity, onChanged: () -> Unit) {
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
        val stdMs = activity.config.cachedStdArgon2Ms
            .coerceIn(Config.MIN_UNLOCK_TARGET_MS, Config.MAX_UNLOCK_TARGET_MS)
        val minTenths = ((stdMs + TENTHS_ROUND_UP_MS) / MS_PER_TENTH).coerceIn(MIN_TENTHS, MAX_TENTHS)
        val minSec = minTenths / TENTHS_PER_SECOND
        val maxHeapKb = (Runtime.getRuntime().maxMemory() / BYTES_PER_KIB).coerceAtLeast(1L)
        // After a reinstall/transfer the local target is just a default; start from the
        // actual (measured or estimated) cost of the existing vault on this device.
        val effectiveMs = CryptoManager.effectiveVaultUnlockMs(activity)
        val preferredMs = if (effectiveMs > 0) effectiveMs else activity.config.unlockTargetMs
        val initialTenths = (preferredMs / MS_PER_TENTH).coerceIn(minTenths, MAX_TENTHS)

        val padding = (DIALOG_PADDING_DP * activity.resources.displayMetrics.density).toInt()
        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val valueText = MyTextView(activity).apply {
            textSize = VALUE_TEXT_SP
        }
        val hint = MyTextView(activity).apply {
            text = buildString {
                append(activity.getString(R.string.brute_force_time_hint))
                // The lower-bound sentence is only useful when the safe floor is above 1 s.
                if (minSec > 1f) {
                    append(' ')
                    append(activity.getString(R.string.brute_force_time_min_hint))
                }
            }
        }
        val input = MyEditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.1f", initialTenths / TENTHS_PER_SECOND))
        }

        fun updatePreview(tenths: Int) {
            val (memKb, iters, _) = calculateTieredArgon2Params(tenths * MS_PER_TENTH, stdMs, maxHeapKb)
            valueText.text = activity.getString(
                R.string.brute_force_time_with_params,
                tenths / TENTHS_PER_SECOND,
                memKb / KIB_PER_MIB,
                iters
            )
        }
        updatePreview(initialTenths)

        val seek = MySeekBar(activity).apply {
            max = (MAX_TENTHS - minTenths).coerceAtLeast(0)
            progress = (initialTenths - minTenths).coerceAtLeast(0)
            isEnabled = MAX_TENTHS > minTenths
        }
        input.isEnabled = MAX_TENTHS > minTenths
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val tenths = minTenths + p
                updatePreview(tenths)
                input.setText(String.format(Locale.US, "%.1f", tenths / TENTHS_PER_SECOND))
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        view.addView(hint)
        view.addView(valueText)
        view.addView(seek)
        view.addView(input)

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(org.fossify.commons.R.string.ok) { dialog, _ ->
                val parsed = input.text.toString().replace(',', '.').toFloatOrNull()
                    ?: (initialTenths / TENTHS_PER_SECOND)
                val clampedSec = parsed.coerceIn(minSec, MAX_SECONDS)
                val targetMs = Math.round(clampedSec * MS_PER_SECOND)
                val (memKb, iters, clampedFlag) = calculateTieredArgon2Params(targetMs, stdMs, maxHeapKb)
                activity.config.unlockTargetMs = targetMs
                activity.config.lastCalibratedMemoryKb = memKb
                activity.config.lastCalibratedIterations = iters
                activity.config.argon2CalibrationClamped = clampedFlag
                onChanged()
                dialog.dismiss()
            }
            .apply {
                activity.setupDialogStuff(view, this, titleId = R.string.brute_force_time_title)
            }
    }
}
