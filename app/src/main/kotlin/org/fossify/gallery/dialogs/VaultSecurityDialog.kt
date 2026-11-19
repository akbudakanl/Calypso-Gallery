package org.fossify.gallery.dialogs

import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.tabs.TabLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogVaultSecurityBinding
import org.fossify.gallery.extensions.applyVaultScreenProtection
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.VaultAuthInputHelper
import java.io.File
import org.fossify.gallery.helpers.canUnlockWithBiometrics
import org.fossify.gallery.helpers.getVaultProtectionType
import org.fossify.gallery.helpers.hasExistingVault
import org.fossify.gallery.helpers.isDeviceLocked
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
    private val onCancelCallback: (() -> Unit)? = null,
    private val callback: (hash: String, type: Int, success: Boolean) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var dialogSucceeded = false
    private var cancelNotified = false
    private var setupInFlight = false
    private var autoPromptDecorView: View? = null
    private var autoPromptFocusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    private val binding = DialogVaultSecurityBinding.inflate(activity.layoutInflater)
    private val inputHelper = VaultAuthInputHelper(activity, binding)
    private val isSetupMode = isChangePasswordMode || customTargetDir != null ||
        (!CryptoManager.hasExistingVault(activity) && (requiredHash.isEmpty() || showTabIndex == SHOW_ALL_TABS))
    private val biometricsEnabled = allowBiometrics && !isSetupMode && !rewrapKdfOnUnlock

    private var currentType: Int = when (showTabIndex) {
        PROTECTION_PASSWORD -> PROTECTION_PASSWORD
        PROTECTION_PIN -> PROTECTION_PIN
        else -> {
            val vaultType = if (customTargetDir != null) null else CryptoManager.getVaultProtectionType(activity)
            when (vaultType) {
                PROTECTION_PASSWORD -> PROTECTION_PASSWORD
                PROTECTION_PIN -> PROTECTION_PIN
                else -> if (activity.config.hiddenProtectionType == PROTECTION_PASSWORD) {
                    PROTECTION_PASSWORD
                } else {
                    PROTECTION_PIN
                }
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

        binding.vaultPatternHolder.beGone()

        if (isSetupMode) {
            binding.vaultNoRecoveryWarning.beVisible()
            binding.vaultBruteForceHolder.apply {
                beVisible()
                text = activity.getString(R.string.argon2_profile_title) + "\n" + BruteForceDialog.summary(activity)
                setOnClickListener {
                    BruteForceDialog.show(activity) {
                        text = activity.getString(R.string.argon2_profile_title) + "\n" +
                            BruteForceDialog.summary(activity)
                    }
                }
            }
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

        if (minimalist && !isSetupMode) {
            binding.vaultStatusLabel.beGone()
        }
        binding.vaultPasswordInputLayout.hint = activity.getString(R.string.enter_password)

        inputHelper.setupPinInput(::handleSecretSubmission)
        inputHelper.setupPasswordInput(::handleSecretSubmission)

        switchProtectionType(currentType)

        inputHelper.setupBiometricButton(enabled = biometricsEnabled) {
            dialogSucceeded = true
            dialog?.dismiss()
            callback(requiredHash, currentType, true)
        }

        val builder = activity.getAlertDialogBuilder()
        builder.setNegativeButton(org.fossify.commons.R.string.cancel, null)
        builder.setOnCancelListener {
            cancelDialog()
        }

        builder.apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
                alertDialog.setOnDismissListener {
                    clearAutoPromptFocusListener()
                    if (!dialogSucceeded) {
                        cancelDialog()
                    }
                }
                alertDialog.window?.apply {
                    addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setDimAmount(1.0f)
                }
                inputHelper.applyThemeColors()
                inputHelper.applyTouchFilter()
                if (currentType == PROTECTION_PASSWORD) {
                    inputHelper.focusPasswordInput(alertDialog.window)
                }

                if (biometricsEnabled && autoPromptBiometrics && CryptoManager.canUnlockWithBiometrics(activity)) {
                    scheduleAutoBiometricPrompt(alertDialog)
                }
            }
        }
    }

    private fun scheduleAutoBiometricPrompt(alertDialog: AlertDialog) {
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
                dialog?.dismiss()
                callback(requiredHash, currentType, true)
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
        if (minimalist && !isSetupMode) {
            binding.vaultStatusLabel.beGone()
            return
        }
        val hasFirstStep = firstStepSecret?.isNotEmpty() == true
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
        dialog?.setCancelable(!busy)
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !busy
    }

    private fun handleSecretSubmission(secret: CharArray) {
        if (setupInFlight) return
        if (isSetupMode) {
            handleSetupSubmission(secret)
        } else {
            inputHelper.unlockWithSecretAsync(
                secret = secret,
                currentType = currentType,
                rewrapKdfOnSuccess = rewrapKdfOnUnlock,
                onPreExecute = { setDialogBusy(true) },
                onPostExecute = { setDialogBusy(false) }
            ) { success ->
                if (success) {
                    dialogSucceeded = true
                    dialog?.dismiss()
                    callback(requiredHash, currentType, true)
                } else {
                    updateStatusLabel()
                }
            }
        }
    }

    private fun handleSetupSubmission(secret: CharArray) {
        val savedFirst = firstStepSecret
        if (savedFirst == null || savedFirst.isEmpty()) {
            clearFirstStepSecret()
            firstStepSecret = secret.copyOf()
            inputHelper.clearInputs()
            updateStatusLabel()
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
                } else {
                    clearFirstStepSecret()
                    inputHelper.clearInputs()
                    updateStatusLabel()
                    activity.toast(R.string.vault_location_not_writable)
                }
            }
        }
    }

    private fun cancelDialog() {
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
