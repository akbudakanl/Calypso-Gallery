package org.fossify.gallery.dialogs

import android.animation.Animator
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import com.google.android.material.tabs.TabLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogVaultSecurityBinding
import org.fossify.gallery.databinding.DialogVaultSecurityHostBinding
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.VaultAuthInputHelper
import org.fossify.gallery.helpers.VaultLockEmblemHelper
import org.fossify.gallery.helpers.VaultShutterAnimator
import org.fossify.gallery.helpers.markVaultPasswordEntered
import java.io.File
import org.fossify.gallery.helpers.canUnlockWithBiometrics
import org.fossify.gallery.helpers.getVaultProtectionType
import org.fossify.gallery.helpers.hasExistingVault
import org.fossify.gallery.helpers.isDeviceLocked
import org.fossify.gallery.helpers.VaultPasswordStrength
import org.fossify.gallery.helpers.isCurrentVaultSecret
import org.fossify.gallery.helpers.setupOrUpdateCredentials

class VaultSecurityDialog(
    private val activity: BaseSimpleActivity,
    private val requiredHash: String,
    private val showTabIndex: Int,
    private val minimalist: Boolean = false,
    private val isChangePasswordMode: Boolean = false,
    private val customTargetDir: File? = null,
    private val allowBiometrics: Boolean = true,
    private val autoPromptBiometrics: Boolean = true,
    private val rewrapKdfOnUnlock: Boolean = false,
    /** HTML screen 5: explain that this is a password check, not a vault unlock. */
    private val showVerificationMessage: Boolean = false,
    /** Verify against .vault with an ephemeral key only; never promote keys to session RAM. */
    private val verifyOnly: Boolean = false,
    /** HTML screen 5: bottom-right "I forgot my password" link to the recovery dialog. */
    private val showForgotPasswordLink: Boolean = false,
    private val onForgotPassword: (() -> Unit)? = null,
    private val onCancelCallback: (() -> Unit)? = null,
    private val callback: (hash: String, type: Int, success: Boolean) -> Unit
) {
    private var dialog: Dialog? = null
    private lateinit var hostBinding: DialogVaultSecurityHostBinding
    private var dialogSucceeded = false
    private var cancelNotified = false
    private var setupInFlight = false
    private var weakWarningInFlight = false
    private var autoPromptDecorView: View? = null
    private var autoPromptFocusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    private var shutterAnimator: VaultShutterAnimator? = null
    private var haloPulseAnimator: Animator? = null
    private val binding = DialogVaultSecurityBinding.inflate(activity.layoutInflater)
    private val isSetupMode = isChangePasswordMode || customTargetDir != null ||
        (!CryptoManager.hasExistingVault(activity) && (requiredHash.isEmpty() || showTabIndex == SHOW_ALL_TABS))
    // While defining a new PIN its length is not known yet, so dots grow until the first entry is saved.
    private val inputHelper = VaultAuthInputHelper(
        activity,
        binding,
        expectedPinLength = if (isSetupMode) 0 else null
    )
    private val biometricsEnabled = allowBiometrics && !isSetupMode && !rewrapKdfOnUnlock

    private var currentType: Int = when (showTabIndex) {
        PROTECTION_PASSWORD -> PROTECTION_PASSWORD
        PROTECTION_PIN -> PROTECTION_PIN
        else -> {
            val vaultType = if (customTargetDir != null) null else CryptoManager.getVaultProtectionType(activity)
            when {
                // Yeni kasa oluştururken alfanumerik parola varsayılan; mevcut kasada tip korunur.
                isSetupMode && !isChangePasswordMode -> PROTECTION_PASSWORD
                vaultType == PROTECTION_PASSWORD -> PROTECTION_PASSWORD
                vaultType == PROTECTION_PIN -> PROTECTION_PIN
                activity.config.hiddenProtectionType == PROTECTION_PASSWORD -> PROTECTION_PASSWORD
                else -> PROTECTION_PIN
            }
        }
    }

    private var firstStepSecret: CharArray? = null

    private fun clearFirstStepSecret() {
        firstStepSecret?.fill('\u0000')
        firstStepSecret = null
    }

    init {
        setupDialog()
    }

    private fun setupDialog() {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val primaryColor = palette.primary
        val backgroundColor = palette.background
        val density = activity.resources.displayMetrics.density

        binding.vaultPatternHolder.beGone()

        // The framed lock emblem belongs to credential entry, not to vault creation / password change.
        if (!isSetupMode) {
            binding.vaultLockEmblem.beVisible()
            VaultLockEmblemHelper.applyStyle(
                context = activity,
                palette = palette,
                haloView = binding.vaultLockHalo,
                coreView = binding.vaultLockEmblemCore,
                lockView = binding.vaultUnlockEmblem
            )
            haloPulseAnimator = VaultLockEmblemHelper.startHaloPulse(binding.vaultLockHalo)
        }

        if (isSetupMode) {
            binding.vaultNoRecoveryWarning.beVisible()
            // Palette-aware rounded row: it reads as a tappable setting, not plain body text.
            binding.vaultBruteForceHolder.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(palette.surface)
                setStroke((1f * density).toInt().coerceAtLeast(1), palette.border)
            }
            fun refreshBruteForceLabel() {
                val value = BruteForceDialog.summary(activity)
                binding.vaultBruteForceHolder.text = SpannableStringBuilder().apply {
                    append(
                        activity.getString(R.string.brute_force_time_title),
                        StyleSpan(Typeface.BOLD),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    if (value.isNotEmpty()) {
                        append('\n')
                        val start = length
                        append(value)
                        setSpan(ForegroundColorSpan(primaryColor), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            refreshBruteForceLabel()
            binding.vaultBruteForceHolder.setOnClickListener {
                BruteForceDialog.show(activity) { refreshBruteForceLabel() }
            }

            // Strength guidance: local-only estimate, never blocks vault creation. The row keeps
            // its space so nothing moves; only the bar/label appear after the first character.
            val strengthRadius = 3f * density
            val track = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = strengthRadius
                setColor(ColorUtils.blendARGB(palette.background, palette.text, 0.12f))
            }
            val fill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = strengthRadius
                setColor(primaryColor)
            }
            binding.vaultStrengthBar.progressDrawable = LayerDrawable(
                arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))
            ).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
            binding.vaultStrengthLabel.setTextColor(palette.secondary)
            // Reserve the strength row's space up front. Only its contents toggle between invisible
            // and visible: the first character reveals the bar without changing the form's height,
            // so the PIN keys and the brute-force row never shift position.
            binding.vaultStrengthHolder.beVisible()
            binding.vaultStrengthBar.beInvisible()
            binding.vaultStrengthLabel.beInvisible()
            fun refreshStrength(secret: CharArray, isPin: Boolean) {
                if (secret.isEmpty()) {
                    binding.vaultStrengthBar.beInvisible()
                    binding.vaultStrengthLabel.beInvisible()
                    return
                }
                binding.vaultStrengthBar.beVisible()
                binding.vaultStrengthLabel.beVisible()
                val result = VaultPasswordStrength.evaluate(secret, isPin)
                binding.vaultStrengthBar.progress = result.score
                binding.vaultStrengthLabel.text = activity.getString(
                    when (result.level) {
                        VaultPasswordStrength.Level.BAD -> R.string.password_strength_bad
                        VaultPasswordStrength.Level.POOR -> R.string.password_strength_poor
                        VaultPasswordStrength.Level.WEAK -> R.string.password_strength_weak
                        VaultPasswordStrength.Level.GOOD -> R.string.password_strength_good
                        VaultPasswordStrength.Level.EXCELLENT -> R.string.password_strength_excellent
                    }
                )
            }
            inputHelper.onPinChanged = { pin ->
                try {
                    refreshStrength(pin, true)
                } finally {
                    pin.fill('\u0000')
                }
            }
            binding.vaultPasswordInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val chars = CharArray(s?.length ?: 0).also { s?.getChars(0, it.size, it, 0) }
                    try {
                        refreshStrength(chars, false)
                    } finally {
                        chars.fill('\u0000')
                    }
                }
            })
            binding.vaultTabs.apply {
                beVisible()
                setBackgroundColor(backgroundColor)
                setTabTextColors(palette.secondary, primaryColor)
                setSelectedTabIndicatorColor(primaryColor)

                removeAllTabs()
                val pinTab = newTab().setText(org.fossify.commons.R.string.pin).setTag(PROTECTION_PIN)
                val passwordTab = newTab().setText(R.string.alphanumeric_password).setTag(PROTECTION_PASSWORD)

                addTab(pinTab, currentType == PROTECTION_PIN)
                addTab(passwordTab, currentType == PROTECTION_PASSWORD)

                addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        val type = tab?.tag as? Int ?: PROTECTION_PIN
                        switchProtectionType(type)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {}
                    override fun onTabReselected(tab: TabLayout.Tab?) {}
                })
            }
        } else {
            binding.vaultTabs.beGone()
        }

        if (minimalist && !isSetupMode && !showVerificationMessage) {
            binding.vaultStatusLabel.beGone()
        }
        if (showForgotPasswordLink) {
            binding.vaultForgotPasswordLink.apply {
                setTextColor(primaryColor)
                beVisible()
                setOnClickListener { onForgotPassword?.invoke() }
            }
        }
        binding.vaultPasswordInputLayout.hint = activity.getString(R.string.enter_password)

        inputHelper.setupPinInput(::handleSecretSubmission)
        inputHelper.setupPasswordInput(::handleSecretSubmission)

        switchProtectionType(currentType)

        inputHelper.setupBiometricButton(enabled = biometricsEnabled) {
            dialogSucceeded = true
            completeUnlockSuccess(viaBiometric = true)
        }

        // Host the security form on the same ambient frosted backdrop as the full-screen lock
        // gate, so every credential prompt has the blurred gallery depth instead of flat black.
        hostBinding = DialogVaultSecurityHostBinding.inflate(activity.layoutInflater)
        hostBinding.vaultSecurityBackdrop.applyPalette(palette)

        if (isSetupMode) {
            // Pin the tabs to the very top of the screen instead of carrying them inside the
            // vertically centred form. The exit button then sits just below the left (PIN) tab.
            (binding.vaultTabs.parent as? ViewGroup)?.removeView(binding.vaultTabs)
            hostBinding.vaultSecurityTabsHolder.addView(
                binding.vaultTabs,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            (hostBinding.vaultSecurityBack.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = activity.resources.getDimensionPixelSize(
                    org.fossify.commons.R.dimen.smaller_margin
                )
            }
            val setupTopPad = (104f * density).toInt()
            hostBinding.vaultSecurityHostContent.setPadding(
                hostBinding.vaultSecurityHostContent.paddingLeft,
                setupTopPad,
                hostBinding.vaultSecurityHostContent.paddingRight,
                hostBinding.vaultSecurityHostContent.paddingBottom
            )
        }

        hostBinding.vaultSecurityHostContent.addView(
            binding.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        hostBinding.vaultSecurityBack.applyColorFilter(palette.text)
        hostBinding.vaultSecurityBack.setOnClickListener { cancelDialog() }

        // A plain full-screen Dialog is used instead of AlertDialog: AlertDialog reserves space
        // for its button panel and system insets, which left a strip of the unlocked gallery
        // visible along the bottom/navigation bar.
        val credentialDialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            // A password prompt must be finished or cancelled with the explicit back button;
            // neither the hardware Back key nor tapping outside should silently drop it.
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setOnCancelListener { cancelDialog() }
            setOnDismissListener {
                clearAutoPromptFocusListener()
                haloPulseAnimator?.cancel()
                haloPulseAnimator = null
                shutterAnimator?.cancel()
                shutterAnimator = null
                if (!dialogSucceeded) {
                    cancelDialog()
                }
            }
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            setContentView(hostBinding.root)
        }
        dialog = credentialDialog

        inputHelper.applyThemeColors()
        inputHelper.applyTouchFilter()
        if (currentType == PROTECTION_PASSWORD) {
            inputHelper.focusPasswordInput(credentialDialog.window)
        }

        if (biometricsEnabled && autoPromptBiometrics && CryptoManager.canUnlockWithBiometrics(activity)) {
            scheduleAutoBiometricPrompt(credentialDialog)
        }

        credentialDialog.show()
    }

    private fun scheduleAutoBiometricPrompt(alertDialog: Dialog) {
        val decorView = alertDialog.window?.decorView ?: return
        var triggered = false

        fun tryTrigger() {
            if (triggered || dialogSucceeded || cancelNotified) return
            if (activity.isFinishing || activity.isDestroyed || !alertDialog.isShowing) return
            if (!decorView.hasWindowFocus() || CryptoManager.isDeviceLocked(activity)) return
            triggered = true
            clearAutoPromptFocusListener()
            inputHelper.promptBiometricUnlock(isAutoTrigger = true) {
                dialogSucceeded = true
                completeUnlockSuccess(viaBiometric = true)
            }
        }

        if (decorView.hasWindowFocus()) {
            decorView.post { tryTrigger() }
            return
        }

        val vto = decorView.viewTreeObserver
        if (!vto.isAlive) return
        clearAutoPromptFocusListener()
        val listener = object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus || triggered) return
                clearAutoPromptFocusListener()
                decorView.post { tryTrigger() }
            }
        }
        autoPromptDecorView = decorView
        autoPromptFocusListener = listener
        vto.addOnWindowFocusChangeListener(listener)
    }

    private fun clearAutoPromptFocusListener() {
        val decorView = autoPromptDecorView
        val listener = autoPromptFocusListener
        if (decorView != null && listener != null && decorView.viewTreeObserver.isAlive) {
            decorView.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
        autoPromptDecorView = null
        autoPromptFocusListener = null
    }

    private fun switchProtectionType(type: Int) {
        currentType = if (type == PROTECTION_PASSWORD) PROTECTION_PASSWORD else PROTECTION_PIN
        clearFirstStepSecret()
        inputHelper.showProtectionType(currentType, dialog?.window)
        updateStatusLabel()
    }

    private fun updateStatusLabel() {
        if (showVerificationMessage) {
            binding.vaultStatusLabel.apply {
                text = SpannableStringBuilder().apply {
                    append(
                        activity.getString(R.string.vault_password_reminder_verify_title),
                        StyleSpan(Typeface.BOLD),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append('\n')
                    append(activity.getString(R.string.vault_password_reminder_verify_message))
                }
                beVisible()
            }
            return
        }
        if (minimalist && !isSetupMode) {
            binding.vaultStatusLabel.beGone()
            return
        }
        val hasFirstStep = firstStepSecret?.isNotEmpty() == true
        // During creation the target time is relevant from the very first entry, not just the repeat.
        if (isSetupMode) {
            binding.vaultBruteForceHolder.beVisible()
        }
        binding.vaultStatusLabel.beVisible()
        if (isChangePasswordMode) {
            if (currentType == PROTECTION_PASSWORD) {
                binding.vaultStatusLabel.setText(
                    if (hasFirstStep) R.string.repeat_new_password
                    else R.string.enter_new_password
                )
            } else {
                binding.vaultStatusLabel.setText(
                    if (hasFirstStep) R.string.repeat_new_pin
                    else R.string.enter_new_pin
                )
            }
        } else if (currentType == PROTECTION_PASSWORD) {
            binding.vaultStatusLabel.setText(
                if (isSetupMode && hasFirstStep) R.string.repeat_password
                else R.string.enter_password
            )
        } else {
            binding.vaultStatusLabel.setText(
                if (isSetupMode && hasFirstStep) org.fossify.commons.R.string.repeat_pin
                else org.fossify.commons.R.string.enter_pin
            )
        }
    }

    private fun setDialogBusy(busy: Boolean) {
        inputHelper.setInputsEnabled(!busy)
        // The top-left arrow is the only way out of a credential prompt, but it must never be
        // usable while a KDF/setup/rewrap is running - a late result would otherwise be applied
        // after the caller has already been told the prompt was cancelled.
        hostBinding.vaultSecurityBack.isEnabled = !busy
        // Only alpha/accessibility text change here. The arrow's colour comes from
        // applyColorFilter(palette.text) in setupDialog, so it keeps following the theme.
        hostBinding.vaultSecurityBack.alpha = if (busy) VaultAuthInputHelper.DISABLED_BACK_ALPHA else 1f
        hostBinding.vaultSecurityBack.contentDescription = if (busy) {
            activity.getString(R.string.unlocking_encrypted_vault)
        } else {
            activity.getString(org.fossify.commons.R.string.back)
        }
        // Hardware Back stays a no-op for the whole lifetime of the prompt, not just while busy.
        // setDialogBusy(false) used to re-enable it after a failed attempt, which made the exit
        // behaviour depend on whether the person had tried a wrong password first.
        dialog?.setCancelable(false)
    }

    private fun handleSecretSubmission(secret: CharArray) {
        if (setupInFlight || weakWarningInFlight) return
        if (isSetupMode) {
            handleSetupSubmission(secret)
        } else if (verifyOnly) {
            setDialogBusy(true)
            ensureBackgroundThread {
                val success = CryptoManager.isCurrentVaultSecret(activity, secret)
                secret.fill('\u0000')
                activity.runOnUiThread {
                    setDialogBusy(false)
                    if (success) {
                        // PIN-dot settings need the entered length without promoting a session key.
                        VaultAuthInputHelper.lastAuthenticatedPinLength =
                            if (currentType == PROTECTION_PIN) secret.size else 0
                        markVaultPasswordEntered(activity.config)
                        dialogSucceeded = true
                        completeUnlockSuccess(viaBiometric = false)
                    } else {
                        updateStatusLabel()
                    }
                }
            }
        } else {
            inputHelper.unlockWithSecretAsync(
                secret = secret,
                currentType = currentType,
                rewrapKdfOnSuccess = rewrapKdfOnUnlock,
                onPreExecute = { setDialogBusy(true) },
                onPostExecute = { setDialogBusy(false) }
            ) { success ->
                if (success) {
                    // A real password/PIN entry restarts the "only biometrics" reminder window.
                    markVaultPasswordEntered(activity.config)
                    dialogSucceeded = true
                    completeUnlockSuccess(viaBiometric = false)
                } else {
                    updateStatusLabel()
                }
            }
        }
    }

    /** Warns about a weak first entry, then stores it and asks for confirmation. */
    private fun handleWeakFirstEntry(secret: CharArray) {
        val weak = isSetupMode &&
            VaultPasswordStrength.evaluate(secret, currentType == PROTECTION_PIN).level.isWeak
        val acceptFirstEntry: (CharArray) -> Unit = { candidate ->
            clearFirstStepSecret()
            firstStepSecret = candidate.copyOf()
            inputHelper.clearInputs()
            updateStatusLabel()
        }
        if (weak) {
            val pending = secret.copyOf()
            weakWarningInFlight = true
            val dismissWeakWarning = {
                weakWarningInFlight = false
                inputHelper.clearInputs()
                updateStatusLabel()
                pending.fill('\u0000')
            }
            VaultNoticeDialog(
                activity = activity,
                title = activity.getString(R.string.weak_password_warning_title),
                message = activity.getString(R.string.weak_password_warning_message),
                positiveText = activity.getString(R.string.weak_password_warning_continue),
                negativeText = activity.getString(org.fossify.commons.R.string.cancel),
                cancelable = true,
                onPositive = {
                    weakWarningInFlight = false
                    try {
                        acceptFirstEntry(pending)
                    } finally {
                        pending.fill('\u0000')
                    }
                },
                onNegative = dismissWeakWarning,
                onCancel = dismissWeakWarning,
            )
        } else {
            acceptFirstEntry(secret)
        }
    }

    private fun handleSetupSubmission(secret: CharArray) {
        val savedFirst = firstStepSecret
        if (savedFirst == null || savedFirst.isEmpty()) {
            if (isChangePasswordMode) {
                // Reject a "new" password that is identical to the current one. This runs the
                // real KDF, so it must stay off the UI thread.
                val newSecret = secret.copyOf()
                val candidate = newSecret.copyOf()
                weakWarningInFlight = true
                binding.vaultStatusLabel.beVisible()
                binding.vaultStatusLabel.setText(R.string.checking_password)
                try {
                    ensureBackgroundThread {
                        var postedToUi = false
                        try {
                            val same = try {
                                CryptoManager.isCurrentVaultSecret(activity, candidate)
                            } catch (_: Throwable) {
                                false
                            } finally {
                                candidate.fill('\u0000')
                            }
                            activity.runOnUiThread {
                                try {
                                    if (activity.isFinishing || activity.isDestroyed || dialog?.isShowing != true) {
                                        return@runOnUiThread
                                    }
                                    weakWarningInFlight = false
                                    binding.vaultStatusLabel.beGone()
                                    if (same) {
                                        VaultNoticeDialog(
                                            activity = activity,
                                            title = activity.getString(R.string.vault_same_password_title),
                                            message = activity.getString(R.string.vault_same_password_message),
                                            positiveText = activity.getString(org.fossify.commons.R.string.ok),
                                            onPositive = {},
                                        )
                                        inputHelper.clearInputs()
                                        return@runOnUiThread
                                    }
                                    handleWeakFirstEntry(newSecret)
                                } finally {
                                    newSecret.fill('\u0000')
                                }
                            }
                            postedToUi = true
                        } finally {
                            candidate.fill('\u0000')
                            if (!postedToUi) {
                                newSecret.fill('\u0000')
                            }
                        }
                    }
                } catch (_: Throwable) {
                    weakWarningInFlight = false
                    candidate.fill('\u0000')
                    newSecret.fill('\u0000')
                }
            } else {
                handleWeakFirstEntry(secret)
            }
            return
        }
        if (!savedFirst.contentEquals(secret)) {
            clearFirstStepSecret()
            inputHelper.clearInputs()
            updateStatusLabel()
            val errId = if (currentType == PROTECTION_PASSWORD) {
                R.string.wrong_password
            } else {
                org.fossify.commons.R.string.wrong_pin
            }
            activity.toast(errId)
            return
        }

        setupInFlight = true
        setDialogBusy(true)
        binding.vaultStatusLabel.beVisible()
        binding.vaultStatusLabel.setText(R.string.unlocking_encrypted_vault)
        val secretCopy = secret.copyOf()
        ensureBackgroundThread {
            val hash = try {
                CryptoManager.setupOrUpdateCredentials(activity, secretCopy, currentType, customTargetDir)
            } catch (_: OutOfMemoryError) {
                System.gc()
                null
            } catch (_: Exception) {
                null
            } finally {
                secretCopy.fill('\u0000')
            }

            activity.runOnUiThread {
                setupInFlight = false
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                setDialogBusy(false)
                if (hash != null) {
                    activity.config.hiddenProtectionType = currentType
                    activity.applyVaultScreenProtection(true)
                    activity.hideKeyboard()
                    clearFirstStepSecret()
                    dialogSucceeded = true
                    dialog?.dismiss()
                    callback(hash, currentType, true)
                    // The one-time biometric offer is triggered by showVaultPasswordSavedDialog's
                    // acknowledgement (HTML screen 3a → 3b), never directly from this callback.
                } else {
                    clearFirstStepSecret()
                    inputHelper.clearInputs()
                    updateStatusLabel()
                    activity.toast(R.string.vault_location_not_writable)
                }
            }
        }
    }

    /**
     * Plays the approved shutter feedback on a real unlock. Setup, password-change and KDF
     * rewrap flows keep their immediate behaviour - the motion is only for entering the vault.
     */
    private fun completeUnlockSuccess(viaBiometric: Boolean = false) {
        if (isSetupMode || isChangePasswordMode || rewrapKdfOnUnlock || verifyOnly ||
            !android.animation.ValueAnimator.areAnimatorsEnabled()
        ) {
            dialog?.dismiss()
            callback(requiredHash, currentType, true)
            return
        }

        setDialogBusy(true)
        haloPulseAnimator?.cancel()
        haloPulseAnimator = null
        val isPassword = currentType == PROTECTION_PASSWORD
        val sweepView = when {
            isPassword -> binding.vaultUnlockSweepPassword
            activity.config.showVaultPinDots -> null
            else -> binding.vaultUnlockSweep
        }
        binding.vaultUnlockSweep.visibility = View.INVISIBLE
        binding.vaultUnlockSweepPassword.visibility = View.INVISIBLE
        val animator = VaultShutterAnimator(
            lock = binding.vaultUnlockEmblem,
            dots = if (isPassword) null else binding.vaultPinDots,
            sweep = sweepView,
            panel = binding.root,
            pulseView = if (isPassword) binding.vaultPasswordInputLayout else null,
            sweepColor = AppearanceManager.resolve(activity, activity.config).primary,
            sweepLiftDp = if (!viaBiometric && currentType == PROTECTION_PIN &&
                !activity.config.showVaultPinDots
            ) {
                VaultShutterAnimator.LEGACY_SWEEP_LIFT_DP
            } else {
                0f
            },
        )
        shutterAnimator = animator
        animator.play {
            dialog?.dismiss()
            callback(requiredHash, currentType, true)
        }
    }

    private fun cancelDialog() {
        weakWarningInFlight = false
        haloPulseAnimator?.cancel()
        haloPulseAnimator = null
        shutterAnimator?.cancel()
        shutterAnimator = null
        clearFirstStepSecret()
        inputHelper.clearInputs()
        if (dialogSucceeded || cancelNotified) return
        cancelNotified = true
        activity.hideKeyboard()
        dialog?.dismiss()
        callback("", currentType, false)
        onCancelCallback?.invoke()
    }
}
