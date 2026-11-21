package org.fossify.gallery.dialogs

import android.app.Activity
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.biometric.auth.AuthPromptHost
import androidx.fragment.app.FragmentActivity
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.interfaces.HashListener
import org.fossify.commons.views.MyDialogViewPager
import org.fossify.gallery.adapters.AppPasswordTypesAdapter
import org.fossify.gallery.databinding.DialogAppSecurityBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager

/**
 * In-app replacement for Commons' SecurityDialog.
 *
 * Three differences matter here:
 *  - the PIN / password / pattern tabs are outside the ScrollView, so they stay pinned;
 *  - the dialog is not cancelable by Back or by tapping outside — a password prompt must be
 *    finished (or explicitly cancelled with the button) instead of vanishing while the user
 *    scrolls the settings behind it;
 *  - secret hashing and biometric auth use [AppPasswordTypesAdapter], which always uses
 *    AndroidX Biometric across API 26–36 instead of the legacy Reprint library on API < 30.
 */
class AppSecurityDialog(
    private val activity: Activity,
    private val requiredHash: String,
    private val showTabIndex: Int,
    private val callback: (hash: String, type: Int, success: Boolean) -> Unit,
) : HashListener {
    private var dialog: AlertDialog? = null
    private val binding = DialogAppSecurityBinding.inflate(activity.layoutInflater, null, false)
    private var tabsAdapter: AppPasswordTypesAdapter
    private var viewPager: MyDialogViewPager

    init {
        val palette = AppearanceManager.resolve(activity, activity.config)

        // A wrap_content ScrollView passes the dialog's height limit down to the ViewPager,
        // which squeezes the 3x3 pattern and hides its last row. A bounded height makes the
        // child measure at its natural size and keeps it reachable by scrolling.
        // Fixed dp sizing keeps the card visually identical in portrait and landscape.
        // Previously the card inherited the screen width (huge in landscape) and its height
        // was computed for the orientation it was first opened in, so rotating it filled the
        // whole display.
        val metrics = activity.resources.displayMetrics
        val cardWidth = minOf(
            (440f * metrics.density).toInt(),
            metrics.widthPixels - (32f * metrics.density).toInt()
        )
        val contentHeight = minOf(
            (480f * metrics.density).toInt(),
            (metrics.heightPixels * 0.7f).toInt()
        )
        binding.dialogScrollview.layoutParams = binding.dialogScrollview.layoutParams.apply {
            height = contentHeight
        }

        binding.apply {
            viewPager = dialogTabViewPager
            viewPager.offscreenPageLimit = 2
            tabsAdapter = AppPasswordTypesAdapter(
                context = root.context,
                requiredHash = requiredHash,
                hashListener = this@AppSecurityDialog,
                scrollView = dialogScrollview,
                biometricPromptHost = AuthPromptHost(activity as FragmentActivity),
                // Always expose the third page: on devices without biometrics Commons renders
                // an explanation there instead of hiding the tab entirely.
                showBiometricIdTab = true,
                showBiometricAuthentication = showTabIndex == PROTECTION_FINGERPRINT,
            )
            viewPager.adapter = tabsAdapter
            viewPager.onPageChangeListener { dialogTabLayout.getTabAt(it)?.select() }
            viewPager.onGlobalLayout { updateTabVisibility() }

            dialogTabLayout.setBackgroundColor(palette.background)
            dialogTabLayout.setTabTextColors(palette.secondary, palette.text)
            dialogTabLayout.setSelectedTabIndicatorColor(palette.primary)

            if (showTabIndex == SHOW_ALL_TABS) {
                dialogTabLayout.addTab(
                    dialogTabLayout.newTab().setText(org.fossify.commons.R.string.biometrics),
                    PROTECTION_FINGERPRINT
                )
                dialogTabLayout.onTabSelectionChanged(tabSelectedAction = { tab ->
                    viewPager.currentItem = when {
                        tab.text.toString().equals(root.context.getString(org.fossify.commons.R.string.pattern), true) -> PROTECTION_PATTERN
                        tab.text.toString().equals(root.context.getString(org.fossify.commons.R.string.pin), true) -> PROTECTION_PIN
                        else -> PROTECTION_FINGERPRINT
                    }
                    updateTabVisibility()
                })
            } else {
                dialogTabLayout.beGone()
                viewPager.currentItem = showTabIndex
                viewPager.allowSwiping = false
            }
        }

        val builder = activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ -> onCancelFail() }
            .setCancelable(false)

        activity.setupDialogStuff(binding.root, builder) { alertDialog ->
            dialog = alertDialog
            alertDialog.setCanceledOnTouchOutside(false)
            alertDialog.window?.setLayout(cardWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            alertDialog.window?.attributes = alertDialog.window?.attributes?.apply {
                gravity = Gravity.CENTER
                y = 0
            }
        }
    }

    private fun onCancelFail() {
        callback("", 0, false)
        dialog?.dismiss()
    }

    override fun receivedHash(hash: String, type: Int) {
        callback(hash, type, true)
        if (!activity.isFinishing) {
            try {
                dialog?.dismiss()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun updateTabVisibility() {
        for (i in 0..2) {
            tabsAdapter.isTabVisible(i, viewPager.currentItem == i)
        }
    }
}
