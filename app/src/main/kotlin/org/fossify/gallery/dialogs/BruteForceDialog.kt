package org.fossify.gallery.dialogs

import android.app.Activity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/** "Brute-Force direnci" ayari: hedef acilis suresi veya hazir modlar. */
object BruteForceDialog {

    fun summary(activity: Activity): String {
        val config = activity.config
        val base = if (config.bruteForceMode == 0) {
            activity.getString(R.string.brute_force_time_value, config.unlockTargetMs / 1000f)
        } else {
            activity.getString(
                R.string.argon2_profile_current,
                arrayOf(
                    activity.getString(R.string.argon2_profile_low),
                    activity.getString(R.string.argon2_profile_default),
                    activity.getString(R.string.argon2_profile_high)
                )[config.argon2Profile]
            )
        }
        val clamped = if (config.argon2CalibrationClamped) {
            activity.getString(R.string.brute_force_clamped)
        } else ""
        val params = activity.getString(
            R.string.brute_force_actual_params,
            config.lastCalibratedMemoryKb / 1024,
            config.lastCalibratedIterations
        )
        return base + clamped + "\n" + params
    }

    fun show(activity: Activity, onChanged: () -> Unit) {
        val modes = arrayOf(
            activity.getString(R.string.brute_force_mode_time),
            activity.getString(R.string.brute_force_mode_presets)
        )
        activity.getAlertDialogBuilder()
            .setTitle(R.string.brute_force_mode_title)
            .setSingleChoiceItems(modes, activity.config.bruteForceMode) { dialog, which ->
                dialog.dismiss()
                activity.config.bruteForceMode = which
                if (which == 0) showTime(activity, onChanged) else showPresets(activity, onChanged)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPresets(activity: Activity, onChanged: () -> Unit) {
        val labels = arrayOf(
            activity.getString(R.string.argon2_profile_low),
            activity.getString(R.string.argon2_profile_default),
            activity.getString(R.string.argon2_profile_high)
        )
        activity.getAlertDialogBuilder()
            .setTitle(R.string.argon2_profile_title)
            .setMessage(R.string.argon2_profile_hint)
            .setSingleChoiceItems(labels, activity.config.argon2Profile) { dialog, which ->
                activity.config.argon2Profile = which
                onChanged()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTime(activity: Activity, onChanged: () -> Unit) {
        val padding = (20 * activity.resources.displayMetrics.density).toInt()
        val view = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val valueText = android.widget.TextView(activity).apply {
            text = summary(activity)
            textSize = 18f
        }
        val hint = android.widget.TextView(activity).apply {
            text = activity.getString(R.string.brute_force_time_hint)
        }
        val seek = android.widget.SeekBar(activity).apply {
            max = 50
            progress = (activity.config.unlockTargetMs - 2_000) / 100
        }
        val input = android.widget.EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(java.util.Locale.US, "%.1f", activity.config.unlockTargetMs / 1000f))
        }
        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val sec = 2.0 + p / 10.0
                valueText.text = activity.getString(R.string.brute_force_time_value, sec.toFloat())
                input.setText(String.format(java.util.Locale.US, "%.1f", sec))
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })
        view.addView(hint)
        view.addView(valueText)
        view.addView(seek)
        view.addView(input)

        activity.getAlertDialogBuilder()
            .setTitle(R.string.brute_force_time_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val parsed = input.text.toString().replace(',', '.').toFloatOrNull()
                    ?: (activity.config.unlockTargetMs / 1000f)
                activity.config.unlockTargetMs = Math.round(parsed.coerceIn(2.0f, 7.0f) * 1000f)
                onChanged()
                dialog.dismiss()
            }
            .show()
    }
}
