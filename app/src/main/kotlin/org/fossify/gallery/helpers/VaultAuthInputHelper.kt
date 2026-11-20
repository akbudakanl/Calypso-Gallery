package org.fossify.gallery.helpers

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.GetChars
import android.text.method.PasswordTransformationMethod
import android.text.method.TransformationMethod
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.textfield.TextInputLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogVaultSecurityBinding
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.config

/**
 * Shared controller for vault PIN, alphanumeric password, and biometric authentication inputs
 * backed by [DialogVaultSecurityBinding]. Used by both [VaultSecurityDialog] and [VaultLockActivity].
 */
class VaultAuthInputHelper(
    private val activity: BaseSimpleActivity,
    private val binding: DialogVaultSecurityBinding,
    expectedPinLength: Int? = null
) {
    /** Grows with the input: a PIN is no longer capped at 16 digits. */
    private var currentPin = CharArray(INITIAL_PIN_CAPACITY)

    /** 0 while a new PIN is being defined; otherwise the digit count saved at setup. */
    private val fixedPinLength: Int = maxOf(0, expectedPinLength ?: activity.config.vaultPinLength)

    /** User preference: draw one dot per digit. The view is also hidden for PINs longer than 16. */
    private val showPinDots: Boolean = activity.config.showVaultPinDots

    private var pinLength = 0
    private var isBusy = false
    private var biometricKeyInvalidated = false
    private var biometricPromptInFlight = false

    fun setInputsEnabled(enabled: Boolean) {
        val pinButtons = listOf(
            binding.pin0, binding.pin1, binding.pin2, binding.pin3, binding.pin4,
            binding.pin5, binding.pin6, binding.pin7, binding.pin8, binding.pin9,
            binding.pinBackspace, binding.pinOk
        )
        pinButtons.forEach { it.isEnabled = enabled }
        binding.vaultPasswordInput.isEnabled = enabled
        binding.vaultPasswordOkBtn.isEnabled = enabled
        binding.vaultBiometricBtn.isEnabled = enabled
        binding.vaultBruteForceHolder.isEnabled = enabled
        binding.vaultTabs.isEnabled = enabled
    }

    fun applyThemeColors(translucentSurfaces: Boolean = false) {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val hideKeyPresses = activity.config.hideVaultKeyPresses
        val isLightBg = ColorUtils.calculateLuminance(palette.background) > 0.5
        val surfaceColor = if (translucentSurfaces) {
            ColorUtils.setAlphaComponent(palette.surface, if (isLightBg) 232 else 208)
        } else {
            palette.surface
        }
        val cardBorderColor = if (translucentSurfaces) {
            ColorUtils.blendARGB(palette.border, palette.text, 0.08f)
        } else {
            palette.border
        }
        val density = activity.resources.displayMetrics.density
        val strokePx = (1f * density).toInt().coerceAtLeast(1)
        val disabledTextColor = ColorUtils.setAlphaComponent(palette.text, DISABLED_TEXT_ALPHA)
        val buttonTextColors = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(),
            ),
            intArrayOf(
                disabledTextColor,
                palette.text,
            ),
        )
        val primaryButtonTextColors = ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(),
            ),
            intArrayOf(
                ColorUtils.setAlphaComponent(palette.onPrimary, DISABLED_TEXT_ALPHA),
                palette.onPrimary,
            ),
        )
        val rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.text, BUTTON_RIPPLE_ALPHA))
        val primaryRipple = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.onPrimary, BUTTON_RIPPLE_ALPHA))
        val cornerRadiusPx = 16f * density

        val digitButtons = listOf(
            binding.pin0, binding.pin1, binding.pin2, binding.pin3, binding.pin4,
            binding.pin5, binding.pin6, binding.pin7, binding.pin8, binding.pin9
        )
        digitButtons.forEach { button ->
            button.setTextColor(buttonTextColors)
            val content = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
                setColor(surfaceColor)
                setStroke(strokePx, cardBorderColor)
            }
            if (hideKeyPresses) {
                button.stateListAnimator = null
                button.background = content
            } else {
                val mask = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerRadiusPx
                    setColor(Color.WHITE)
                }
                button.background = RippleDrawable(rippleColor, content, mask)
            }
        }

        // Backspace key: clean borderless tonal surface
        binding.pinBackspace.setTextColor(palette.secondary)
        if (hideKeyPresses) {
            binding.pinBackspace.stateListAnimator = null
            binding.pinBackspace.background = null
        } else {
            binding.pinBackspace.background = RippleDrawable(
                rippleColor,
                null,
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerRadiusPx
                    setColor(Color.WHITE)
                }
            )
        }

        // Confirm buttons (PIN ✓ and Password ✓): high-contrast primary pill/key
        listOf(binding.pinOk to cornerRadiusPx, binding.vaultPasswordOkBtn to 26f * density).forEach { (btn, radius) ->
            btn.setTextColor(primaryButtonTextColors)
            val content = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(palette.primary)
            }
            if (hideKeyPresses) {
                btn.stateListAnimator = null
                btn.background = content
            } else {
                val mask = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radius
                    setColor(Color.WHITE)
                }
                btn.background = RippleDrawable(primaryRipple, content, mask)
            }
        }

        // Rounded PIN dots box; empty slots render as dim outlined rings (see 02-vault design)
        binding.vaultPinDots.setColors(
            filledColor = palette.text,
            ringFillColor = Color.TRANSPARENT,
            ringStrokeColor = palette.secondary
        )
        binding.vaultPinDots.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(surfaceColor)
            setStroke(strokePx, cardBorderColor)
        }

        binding.vaultStatusLabel.setTextColor(palette.text)
        binding.vaultBruteForceHolder.setTextColor(palette.text)
        binding.vaultNoRecoveryWarning.setTextColor(palette.secondary)

        // Sleek pill biometric button with fingerprint icon
        binding.vaultBiometricBtn.setTextColor(palette.text)
        val bioRadius = 24f * density
        val bioContent = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = bioRadius
            setColor(surfaceColor)
            setStroke(strokePx, cardBorderColor)
        }
        val bioMask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = bioRadius
            setColor(Color.WHITE)
        }
        binding.vaultBiometricBtn.background = RippleDrawable(rippleColor, bioContent, bioMask)
        val fpIcon = ContextCompat.getDrawable(activity, R.drawable.ic_onboarding_fingerprint)?.mutate()?.apply {
            val size = (20f * density).toInt()
            setBounds(0, 0, size, size)
            setTint(palette.text)
        }
        binding.vaultBiometricBtn.setCompoundDrawablesRelative(fpIcon, null, null, null)

        binding.vaultPasswordInputLayout.setColors(palette.text, palette.primary, palette.background)
        val selStart = binding.vaultPasswordInput.selectionStart
        val selEnd = binding.vaultPasswordInput.selectionEnd
        if (hideKeyPresses) {
            binding.vaultPasswordInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
            binding.vaultPasswordInput.transformationMethod = ImmediateMaskTransformationMethod()
        } else {
            binding.vaultPasswordInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            binding.vaultPasswordInputLayout.setEndIconTintList(ColorStateList.valueOf(palette.secondary))
            if (binding.vaultPasswordInput.transformationMethod !is PasswordTransformationMethod) {
                binding.vaultPasswordInput.transformationMethod = PasswordTransformationMethod.getInstance()
            }
        }
        val textLen = binding.vaultPasswordInput.text?.length ?: 0
        if (selStart >= 0 && selEnd >= 0) {
            binding.vaultPasswordInput.setSelection(
                selStart.coerceIn(0, textLen),
                selEnd.coerceIn(0, textLen)
            )
        }
        binding.vaultPasswordInput.setTextColor(palette.text)
        binding.vaultPasswordInput.setHintTextColor(palette.secondary)
    }

    fun applyTouchFilter() {
        binding.root.filterTouchesWhenObscured = true
        binding.vaultPinHolder.filterTouchesWhenObscured = true
        binding.vaultPasswordHolder.filterTouchesWhenObscured = true
    }

    fun setupPinInput(onPinSubmit: (CharArray) -> Unit) {
        val digitButtons = listOf(
            binding.pin0 to '0',
            binding.pin1 to '1',
            binding.pin2 to '2',
            binding.pin3 to '3',
            binding.pin4 to '4',
            binding.pin5 to '5',
            binding.pin6 to '6',
            binding.pin7 to '7',
            binding.pin8 to '8',
            binding.pin9 to '9'
        )

        for ((btn, digit) in digitButtons) {
            btn.setOnClickListener {
                if (pinLength == currentPin.size) {
                    currentPin = currentPin.copyOf(currentPin.size * 2)
                }
                currentPin[pinLength++] = digit
                updatePinDots()
            }
        }

        binding.pinBackspace.setOnClickListener {
            if (pinLength > 0) {
                pinLength--
                currentPin[pinLength] = '\u0000'
                updatePinDots()
            }
        }

        binding.pinBackspace.setOnLongClickListener {
            currentPin.fill('\u0000')
            pinLength = 0
            updatePinDots()
            true
        }

        binding.pinOk.setOnClickListener {
            if (pinLength < MIN_PIN_LENGTH) {
                activity.toast(org.fossify.commons.R.string.pin_must_be_4_digits_long)
            } else {
                val chars = currentPin.copyOf(pinLength)
                try {
                    onPinSubmit(chars)
                } finally {
                    chars.fill('\u0000')
                }
            }
        }
    }

    fun setupPasswordInput(onPasswordSubmit: (CharArray) -> Unit) {
        binding.vaultPasswordOkBtn.setOnClickListener {
            submitPassword(onPasswordSubmit)
        }
        binding.vaultPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_NULL) {
                submitPassword(onPasswordSubmit)
                true
            } else {
                false
            }
        }
    }

    private fun submitPassword(onPasswordSubmit: (CharArray) -> Unit) {
        val editable = binding.vaultPasswordInput.text ?: return
        val len = editable.length
        if (len <= 0) return
        val chars = CharArray(len)
        editable.getChars(0, len, chars, 0)
        try {
            onPasswordSubmit(chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun showProtectionType(type: Int, focusWindow: Window?) {
        val resolvedType = if (type == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
        clearInputs()
        binding.vaultPatternHolder.beGone()
        binding.vaultPinHolder.beVisibleIf(resolvedType == PROTECTION_PIN)
        binding.vaultPasswordHolder.beVisibleIf(resolvedType == PROTECTION_PASSWORD)

        if (resolvedType == PROTECTION_PASSWORD) {
            focusPasswordInput(focusWindow)
        } else {
            activity.hideKeyboard()
        }
    }

    fun focusPasswordInput(window: Window?) {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.vaultPasswordInput.postDelayed({
            binding.vaultPasswordInput.requestFocus()
            activity.showKeyboard(binding.vaultPasswordInput)
        }, 100)
    }

    fun clearInputs() {
        currentPin.fill('\u0000')
        pinLength = 0
        updatePinDots()
        binding.vaultPasswordInput.text?.clear()
    }

    private fun updatePinDots() {
        // The per-digit slot indicator is opt-in. With the setting off, preserve the original
        // presentation: one bullet per entered digit, still occupying the box above the keypad.
        if (!showPinDots) {
            binding.vaultPinDots.beGone()
            binding.vaultPinDotsLegacy.beVisible()
            binding.vaultPinDotsLegacy.text = "•".repeat(pinLength)
            return
        }
        binding.vaultPinDotsLegacy.beGone()

        // Once the PIN passes the dot-indicator limit the whole view goes away, and a PIN of
        // unknown length (setup mode) keeps growing until it reaches that limit too.
        val trackedLength = if (fixedPinLength > 0) maxOf(fixedPinLength, pinLength) else pinLength
        if (!showPinDots || trackedLength > VAULT_PIN_DOTS_MAX_LENGTH) {
            binding.vaultPinDots.beGone()
            return
        }
        binding.vaultPinDots.beVisible()
        val slotCount = if (fixedPinLength > 0) fixedPinLength else maxOf(pinLength, MIN_PIN_LENGTH)
        binding.vaultPinDots.setDots(slotCount, pinLength)
    }

    fun setupBiometricButton(enabled: Boolean, onAuthenticated: () -> Unit) {
        biometricKeyInvalidated = false
        if (enabled && CryptoManager.canUnlockWithBiometrics(activity)) {
            binding.vaultBiometricBtn.beVisible()
            binding.vaultBiometricBtn.setOnClickListener {
                promptBiometricUnlock(isAutoTrigger = false, onAuthenticated = onAuthenticated)
            }
        } else {
            binding.vaultBiometricBtn.beGone()
        }
    }

    fun resetBiometricPromptState() {
        biometricPromptInFlight = false
    }

    fun promptBiometricUnlock(
        isAutoTrigger: Boolean = false,
        onAuthenticated: () -> Unit
    ) {
        if (biometricPromptInFlight || activity.isFinishing || activity.isDestroyed) return
        if (isAutoTrigger && CryptoManager.isDeviceLocked(activity)) return

        if (biometricKeyInvalidated) {
            if (!isAutoTrigger) {
                showInvalidatedBiometricPrompt()
            }
            return
        }

        if (!CryptoManager.canUnlockWithBiometrics(activity)) return

        var cipherResult = CryptoManager.getBiometricDecryptCipherResult(activity)
        if (!isAutoTrigger && cipherResult == BiometricCipherResult.TransientError) {
            cipherResult = CryptoManager.getBiometricDecryptCipherResult(activity)
        }

        when (cipherResult) {
            is BiometricCipherResult.Ready -> {
                showCryptoBiometricPrompt(cipherResult.cipher, onAuthenticated)
            }
            BiometricCipherResult.KeyInvalidated -> {
                biometricKeyInvalidated = true
                if (isAutoTrigger) {
                    activity.toast(R.string.biometric_key_invalidated)
                } else {
                    showInvalidatedBiometricPrompt()
                }
            }
            BiometricCipherResult.DeviceLocked,
            BiometricCipherResult.TransientError -> {
                // Keep biometric key and button intact so the user can retry once Keystore/keyguard settles.
                if (!isAutoTrigger) {
                    activity.toast(org.fossify.commons.R.string.an_error_occurred)
                }
            }
            BiometricCipherResult.Unavailable -> Unit
        }
    }

    private fun buildBiometricPromptInfo(): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_unlock_vault))
            .setSubtitle(activity.getString(R.string.biometric_unlock_prompt))
            .setNegativeButtonText(activity.getString(R.string.use_password_or_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }

    private fun showCryptoBiometricPrompt(
        cipher: javax.crypto.Cipher,
        onAuthenticated: () -> Unit
    ) {
        val promptInfo = buildBiometricPromptInfo()
        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPromptInFlight = false
                    val authCipher = result.cryptoObject?.cipher
                    if (authCipher != null && CryptoManager.completeBiometricUnlock(activity, authCipher)) {
                        activity.applyVaultScreenProtection(true)
                        activity.hideKeyboard()
                        onAuthenticated()
                    } else {
                        activity.toast(org.fossify.commons.R.string.an_error_occurred)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPromptInFlight = false
                    // User stays on PIN/password screen and can retry via the biometric button
                }

                override fun onAuthenticationFailed() {
                    // Fingerprint unrecognized; BiometricPrompt allows retry
                }
            }
        )
        biometricPromptInFlight = true
        try {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Throwable) {
            biometricPromptInFlight = false
            activity.toast(org.fossify.commons.R.string.an_error_occurred)
        }
    }

    private fun showInvalidatedBiometricPrompt() {
        if (!CryptoManager.isBiometricHardwareAvailable(activity)) {
            activity.toast(R.string.biometric_key_invalidated)
            return
        }
        val promptInfo = buildBiometricPromptInfo()
        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPromptInFlight = false
                    // The hardware key was permanently invalidated by Android; never unlock without a valid CryptoObject.
                    activity.toast(R.string.biometric_key_invalidated)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPromptInFlight = false
                }

                override fun onAuthenticationFailed() {
                    // Fingerprint unrecognized; BiometricPrompt allows retry
                }
            }
        )
        biometricPromptInFlight = true
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (_: Throwable) {
            biometricPromptInFlight = false
            activity.toast(R.string.biometric_key_invalidated)
        }
    }

    fun unlockWithSecretAsync(
        secret: CharArray,
        currentType: Int,
        rewrapKdfOnSuccess: Boolean = false,
        onPreExecute: (() -> Unit)? = null,
        onPostExecute: (() -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) {
        if (isBusy) return
        isBusy = true
        setInputsEnabled(false)
        onPreExecute?.invoke()
        binding.vaultStatusLabel.beVisible()
        binding.vaultStatusLabel.setText(R.string.unlocking_encrypted_vault)

        val secretCopy = secret.copyOf()
        ensureBackgroundThread {
            val unlocked = try {
                if (rewrapKdfOnSuccess) {
                    CryptoManager.rewrapVaultHeaderWithCurrentKdf(activity, secretCopy)
                } else {
                    CryptoManager.unlockWithSecret(activity, secretCopy)
                }
            } catch (_: OutOfMemoryError) {
                System.gc()
                false
            } catch (_: Throwable) {
                false
            } finally {
                secretCopy.fill('\u0000')
            }

            activity.runOnUiThread {
                isBusy = false
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                setInputsEnabled(true)
                onPostExecute?.invoke()
                if (unlocked) {
                    activity.config.hiddenProtectionType = currentType
                    if (!rewrapKdfOnSuccess) {
                        activity.applyVaultScreenProtection(true)
                    }
                    activity.hideKeyboard()
                    onResult(true)
                } else {
                    clearInputs()
                    val errId = if (currentType == PROTECTION_PASSWORD) {
                        R.string.wrong_password
                    } else {
                        org.fossify.commons.R.string.wrong_pin
                    }
                    activity.toast(errId)
                    onResult(false)
                }
            }
        }
    }

    fun unlockWithSecret(secret: CharArray, currentType: Int): Boolean {
        val unlocked = CryptoManager.unlockWithSecret(activity, secret)
        if (unlocked) {
            activity.config.hiddenProtectionType = currentType
            activity.applyVaultScreenProtection(true)
            activity.hideKeyboard()
            return true
        }
        clearInputs()
        val errId = if (currentType == PROTECTION_PASSWORD) {
            R.string.wrong_password
        } else {
            org.fossify.commons.R.string.wrong_pin
        }
        activity.toast(errId)
        return false
    }

    private class ImmediateMaskTransformationMethod : TransformationMethod {
        override fun getTransformation(source: CharSequence, view: View): CharSequence {
            return MaskedCharSequence(source)
        }

        override fun onFocusChanged(
            view: View?,
            sourceText: CharSequence?,
            focused: Boolean,
            direction: Int,
            previouslyFocusedRect: Rect?
        ) = Unit

        private class MaskedCharSequence(private val source: CharSequence) : CharSequence, GetChars {
            override val length: Int get() = source.length
            override fun get(index: Int): Char = MASK_CHAR
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
                return MaskedCharSequence(source.subSequence(startIndex, endIndex))
            }

            override fun getChars(start: Int, end: Int, dest: CharArray, destoff: Int) {
                val count = (end - start).coerceAtLeast(0)
                for (i in 0 until count) {
                    dest[destoff + i] = MASK_CHAR
                }
            }
        }

        companion object {
            private const val MASK_CHAR = '\u2022'
        }
    }

    companion object {
        private const val DISABLED_TEXT_ALPHA = 0x61
        private const val BUTTON_RIPPLE_ALPHA = 0x1F
        private const val PIN_BUTTON_RIPPLE_RADIUS_DP = 12f
        private const val INITIAL_PIN_CAPACITY = 16
        private const val MIN_PIN_LENGTH = 4
    }
}
