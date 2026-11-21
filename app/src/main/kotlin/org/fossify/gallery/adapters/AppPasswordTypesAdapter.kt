package org.fossify.gallery.adapters

import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.auth.AuthPromptHost
import androidx.viewpager.widget.PagerAdapter
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.PROTECTION_PATTERN
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.commons.interfaces.HashListener
import org.fossify.commons.interfaces.SecurityTab
import org.fossify.commons.views.MyScrollView

/**
 * Pure-Kotlin replacement for Commons' PasswordTypesAdapter.
 *
 * Uses AndroidX Biometric (`tab_biometric_id` / `BiometricIdTab`) on all supported API levels
 * (API 26+) instead of routing API < 30 devices to the legacy Reprint fingerprint layout.
 */
class AppPasswordTypesAdapter(
    private val context: Context,
    private val requiredHash: String,
    private val hashListener: HashListener,
    private val scrollView: MyScrollView,
    private val biometricPromptHost: AuthPromptHost,
    private val showBiometricIdTab: Boolean,
    private val showBiometricAuthentication: Boolean,
) : PagerAdapter() {
    private val tabs = SparseArray<SecurityTab>()

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = LayoutInflater.from(context).inflate(layoutSelection(position), container, false)
        container.addView(view)
        val tab = view as SecurityTab
        tabs.put(position, tab)
        tab.initTab(requiredHash, hashListener, scrollView, biometricPromptHost, showBiometricAuthentication)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        tabs.remove(position)
        container.removeView(item as View)
    }

    override fun getCount(): Int = if (showBiometricIdTab) 3 else 2

    override fun isViewFromObject(view: View, item: Any): Boolean = view == item

    private fun layoutSelection(position: Int): Int = when (position) {
        PROTECTION_PATTERN -> org.fossify.commons.R.layout.tab_pattern
        PROTECTION_PIN -> org.fossify.commons.R.layout.tab_pin
        PROTECTION_FINGERPRINT -> org.fossify.commons.R.layout.tab_biometric_id
        else -> throw RuntimeException("Only 3 tabs allowed")
    }

    fun isTabVisible(position: Int, isVisible: Boolean) {
        tabs.get(position)?.visibilityChanged(isVisible)
    }
}
