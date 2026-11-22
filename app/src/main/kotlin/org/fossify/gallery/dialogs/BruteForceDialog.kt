package org.fossify.gallery.dialogs

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyEditText
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.applyThemedCursor
import org.fossify.gallery.extensions.applyThemedSelectionHandles
import org.fossify.gallery.extensions.config
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
 *
 * The dial is shared by both entry points (Settings and first-time vault setup) so the two sides
 * always show the same control, colours and security-level wording.
 */
object BruteForceDialog {
    private const val MS_PER_SECOND = 1000f
    private const val TENTHS_PER_SECOND = 10f
    private const val MS_PER_TENTH = 100
    private const val TENTHS_ROUND_UP_MS = 99
    private const val MIN_TENTHS = 1
    private const val MAX_TENTHS = 100
    private const val MAX_SECONDS = 10.0f
    private const val DIALOG_PADDING_DP = 20f
    private const val VALUE_TEXT_SP = 22f
    private const val WARNING_TEXT_SP = 13f
    private const val LEVEL_TEXT_SP = 12f

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

    /**
     * Security level shown above the dial and next to both row summaries. Thresholds are fixed:
     * 0–2.5 s fast, 2.5–5 s balanced, 5–7.5 s high, 7.5–10 s maximum.
     */
    fun levelForTarget(activity: Activity, targetMs: Int): String {
        val seconds = targetMs / MS_PER_SECOND
        val labelId = when {
            seconds <= 2.5f -> R.string.brute_force_level_fast
            seconds < 5f -> R.string.brute_force_level_balanced
            seconds < 7.5f -> R.string.brute_force_level_high
            else -> R.string.brute_force_level_maximum
        }
        return activity.getString(labelId)
    }

    /** Short explanation under the dial; paired with [levelForTarget] for every threshold. */
    fun levelDescriptionForTarget(activity: Activity, targetMs: Int): String {
        val seconds = targetMs / MS_PER_SECOND
        val descId = when {
            seconds <= 2.5f -> R.string.brute_force_level_fast_desc
            seconds < 5f -> R.string.brute_force_level_balanced_desc
            seconds < 7.5f -> R.string.brute_force_level_high_desc
            else -> R.string.brute_force_level_maximum_desc
        }
        return activity.getString(descId)
    }

    fun levelForCurrentTarget(activity: Activity): String = levelForTarget(activity, activity.config.unlockTargetMs)

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

    /**
     * The bar is drawn here instead of relying on the SeekBar's progress drawable: Commons'
     * themed SeekBar applies a tint to that drawable, which flattens a multi-stop gradient into
     * one solid colour. Drawing onDraw ourselves keeps the full left-to-right ramp visible while
     * AppCompatSeekBar still handles touch, key events and progress.
     */
    private class DialSeekBar(context: Context) : AppCompatSeekBar(context) {
        var trackColor: Int = Color.GRAY
        var ramp: IntArray = intArrayOf()
        var knobColor: Int = Color.WHITE
        var knobEdgeColor: Int = Color.GRAY
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val total = (width - paddingLeft - paddingRight).toFloat()
            if (total <= 0f) return
            val density = resources.displayMetrics.density
            val barHeight = (6f * density).coerceAtLeast(2f)
            val left = paddingLeft.toFloat()
            val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
            val top = cy - barHeight / 2f
            val bottom = cy + barHeight / 2f
            val radius = barHeight / 2f

            barPaint.shader = null
            barPaint.color = trackColor
            canvas.drawRoundRect(left, top, left + total, bottom, radius, radius, barPaint)

            val fraction = if (max > 0) progress.toFloat() / max.toFloat() else 0f
            val fillWidth = total * fraction.coerceIn(0f, 1f)
            if (fillWidth > 1f && ramp.size >= 2) {
                // Shader spans the whole track, so the colour at the thumb is stable while dragging.
                barPaint.shader = LinearGradient(
                    left, 0f, left + total, 0f, ramp, null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(left, top, left + fillWidth, bottom, radius, radius, barPaint)
                barPaint.shader = null
            }

            val knobRadius = 9f * density
            val kx = left + fillWidth
            knobPaint.shader = RadialGradient(
                kx - knobRadius * 0.35f, cy - knobRadius * 0.35f, knobRadius * 0.1f,
                kx, cy, knobRadius,
                longArrayOf(
                    (ramp.lastOrNull() ?: knobColor).toLong() and 0xFFFFFFFFL,
                    knobColor.toLong() and 0xFFFFFFFFL,
                    knobEdgeColor.toLong() and 0xFFFFFFFFL
                ),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(kx, cy, knobRadius, knobPaint)
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
        seek: SeekBar,
        minTenths: Int,
        unit: String,
        minSec: Float,
        onTargetMsChanged: (Int) -> Unit,
        onUiRefresh: (Int) -> Unit
    ) {
        // The number is shown without its unit inside the floating bubble.
        fun formatted(tenths: Int) = String.format(Locale.US, "%.1f", tenths / TENTHS_PER_SECOND)
        valueInput.isEnabled = MAX_TENTHS > minTenths
        valueInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                parseTargetMsOrNull(s, unit, minSec)?.let {
                    onTargetMsChanged(it)
                    onUiRefresh(it)
                }
            }
        })
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val tenths = minTenths + p
                valueInput.setText(formatted(tenths))
                val targetMs = tenths * MS_PER_TENTH
                onTargetMsChanged(targetMs)
                onUiRefresh(targetMs)
                seek.invalidate()
            }

            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
    }

    private fun showTimeDialogWithMeasurement(activity: Activity, onChanged: () -> Unit) {
        val config = activity.config
        // Calibration/default math is untouched: device limit <= 1 s -> 1 s, otherwise the limit.
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

        val palette = AppearanceManager.resolve(activity, config)
        val primary = palette.primary
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt().coerceAtLeast(1)

        val padding = dp(DIALOG_PADDING_DP)
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
            setTextColor(palette.secondary)
        }
        view.addView(hint)

        // ---- Themed dial: level label, editable value bubble riding on a coloured bar ----
        val dial = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18f) }
            minimumHeight = dp(84f)
        }

        val levelLabel = MyTextView(activity).apply {
            textSize = LEVEL_TEXT_SP
            setTextColor(primary)
            gravity = Gravity.CENTER
            text = levelForTarget(activity, initialTenths * MS_PER_TENTH)
        }
        dial.addView(levelLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = dp(2f) })

        val seek = DialSeekBar(activity).apply {
            max = (MAX_TENTHS - minTenths).coerceAtLeast(0)
            progress = (initialTenths - minTenths).coerceAtLeast(0)
            isEnabled = MAX_TENTHS > minTenths
            minimumHeight = dp(32f)
            // Same left-to-right ramp as the HTML concept: deep theme colour -> primary -> light.
            // All stops derive from the active palette, so a blue theme gives a blue dial.
            trackColor = ColorUtils.blendARGB(palette.background, palette.text, 0.14f)
            ramp = intArrayOf(
                ColorUtils.blendARGB(primary, palette.background, 0.45f),
                primary,
                ColorUtils.blendARGB(primary, Color.WHITE, 0.38f)
            )
            knobColor = primary
            knobEdgeColor = ColorUtils.blendARGB(primary, palette.background, 0.45f)
        }
        dial.addView(seek, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(32f),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(10f) })

        val unit = activity.getString(R.string.vault_unlock_time_unit)
        val valueInput = MyEditText(ContextThemeWrapper(activity, R.style.VaultCursorTheme)).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = VALUE_TEXT_SP
            gravity = Gravity.CENTER
            background = null
            // EditText inherits a theme padding and minWidth; without clearing them the bubble
            // becomes several times wider than the number and dwarfs the bar.
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            isSingleLine = true
            setText(String.format(Locale.US, "%.1f", initialTenths / TENTHS_PER_SECOND))
            setTextColor(primary)
            applyThemedCursor(primary)
            applyThemedSelectionHandles(palette.text)
            isEnabled = MAX_TENTHS > minTenths
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val unitLabel = MyTextView(activity).apply {
            text = unit
            setTextColor(palette.secondary)
            textSize = 12f
            setPadding(dp(2f), 0, 0, 0)
        }
        val editHint = MyTextView(activity).apply {
            text = "✎"
            setTextColor(palette.secondary)
            textSize = 10f
            alpha = 0.75f
            setPadding(dp(4f), 0, 0, 0)
        }
        val bubble = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8f).toFloat()
                setColor(ColorUtils.blendARGB(palette.surface, primary, 0.10f))
                setStroke(dp(1f), ColorUtils.blendARGB(palette.surface, primary, 0.55f))
            }
            setPadding(dp(8f), dp(3f), dp(7f), dp(3f))
        }
        bubble.addView(valueInput)
        bubble.addView(unitLabel)
        bubble.addView(editHint)
        dial.addView(bubble, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = dp(20f) })

        fun positionBubble() {
            seek.post {
                if (dial.width <= 0 || bubble.width <= 0) return@post
                val range = (MAX_TENTHS - minTenths).coerceAtLeast(1)
                val fraction = seek.progress.toFloat() / range
                val usable = (seek.width - seek.paddingLeft - seek.paddingRight)
                    .toFloat().coerceAtLeast(1f)
                val center = seek.left + seek.paddingLeft + usable * fraction
                val half = bubble.width / 2f
                val margin = dp(6f).toFloat()
                val clamped = center.coerceIn(half + margin, dial.width - half - margin)
                // The bubble is initially centred by gravity; translate it onto the thumb.
                bubble.translationX = clamped - dial.width / 2f
            }
        }

        view.addView(dial)

        val scale = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val minScale = MyTextView(activity).apply {
            text = String.format(Locale.US, "%.1f %s", minSec, unit)
            textSize = 11f
            setTextColor(palette.secondary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val maxScale = MyTextView(activity).apply {
            text = String.format(Locale.US, "%.1f %s", MAX_SECONDS, unit)
            textSize = 11f
            setTextColor(palette.secondary)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        scale.addView(minScale)
        scale.addView(maxScale)
        view.addView(scale, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val levelDescription = MyTextView(activity).apply {
            text = levelDescriptionForTarget(activity, initialTenths * MS_PER_TENTH)
            textSize = 11f
            setTextColor(palette.secondary)
            gravity = Gravity.CENTER
        }
        view.addView(levelDescription, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6f) })

        // The warning frame follows the active theme, never a fixed yellow.
        val highMemoryWarning = MyTextView(activity).apply {
            setText(R.string.brute_force_high_memory_warning)
            textSize = WARNING_TEXT_SP
            setTextColor(palette.text)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10f).toFloat()
                setColor(ColorUtils.blendARGB(palette.surface, primary, 0.08f))
                setStroke(dp(1f), ColorUtils.blendARGB(palette.surface, primary, 0.32f))
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
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
        attachTargetControls(valueInput, seek, minTenths, unit, minSec, ::updateWarningForTarget) { targetMs ->
            levelLabel.text = levelForTarget(activity, targetMs)
            levelDescription.text = levelDescriptionForTarget(activity, targetMs)
            positionBubble()
        }
        view.addView(highMemoryWarning, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10f) })
        dial.post { positionBubble() }

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
