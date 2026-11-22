package org.fossify.gallery.extensions

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.FONT_TYPE_SYSTEM_DEFAULT
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.AppSecurityDialog
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.VaultAuthInputHelper

/**
 * Pure-Kotlin replacements for Commons' `handle*PasswordProtection` / `handleLockedFolderOpening`
 * extensions, routing prompts through [AppSecurityDialog] so every API level uses AndroidX
 * Biometric instead of the legacy Reprint library.
 */
fun Activity.handleHiddenFolderPasswordProtection(callback: () -> Unit) {
    if (baseConfig.isHiddenPasswordProtectionOn) {
        AppSecurityDialog(this, baseConfig.hiddenPasswordHash, baseConfig.hiddenProtectionType) { _, _, success ->
            if (success) {
                callback()
            }
        }
    } else {
        callback()
    }
}

fun Activity.handleAppPasswordProtection(callback: (success: Boolean) -> Unit) {
    if (baseConfig.isAppPasswordProtectionOn) {
        AppSecurityDialog(this, baseConfig.appPasswordHash, baseConfig.appProtectionType) { _, _, success ->
            callback(success)
        }
    } else {
        callback(true)
    }
}

fun Activity.handleDeletePasswordProtection(callback: () -> Unit) {
    if (baseConfig.isDeletePasswordProtectionOn) {
        AppSecurityDialog(this, baseConfig.deletePasswordHash, baseConfig.deleteProtectionType) { _, _, success ->
            if (success) {
                callback()
            }
        }
    } else {
        callback()
    }
}

fun Activity.handleLockedFolderOpening(path: String, callback: (success: Boolean) -> Unit) {
    if (baseConfig.isFolderProtected(path)) {
        AppSecurityDialog(
            activity = this,
            requiredHash = baseConfig.getFolderProtectionHash(path),
            showTabIndex = baseConfig.getFolderProtectionType(path)
        ) { _, _, success ->
            callback(success)
        }
    } else {
        callback(true)
    }
}

/**
 * Restyles Commons' `tab_biometric_id` button (`open_biometric_dialog`) so Fossify's standard
 * password flows (app lock, excluded folders, delete protection, locked albums) present the same
 * theme-aware minimalist pill button used on the encrypted vault unlock screen, kept centered
 * in place.
 */
internal fun styleBiometricIdTab(tabView: View, context: Context) {
    val button = tabView.findViewById<TextView>(org.fossify.commons.R.id.open_biometric_dialog) ?: return
    val config = context.config
    val palette = AppearanceManager.resolve(context, config)
    val density = context.resources.displayMetrics.density
    val padHorizontal = (20f * density).toInt()
    val maxWidthPx = (
        context.resources.getDimensionPixelSize(R.dimen.vault_security_max_width) -
            2 * context.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_margin)
        ).coerceAtLeast((240f * density).toInt())

    button.apply {
        setText(R.string.use_biometrics)
        isAllCaps = false
        stateListAnimator = null
        elevation = 0f
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        if (config.fontType == FONT_TYPE_SYSTEM_DEFAULT && config.fontName.isEmpty()) {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPaddingRelative(padHorizontal, 0, padHorizontal, 0)
        compoundDrawablePadding = (10f * density).toInt()
        VaultAuthInputHelper.applyBiometricButtonStyle(
            context = context,
            button = this,
            palette = palette,
        )
        val params = layoutParams
        if (params is ConstraintLayout.LayoutParams) {
            params.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
            params.height = (48f * density).toInt()
            params.matchConstraintMaxWidth = maxWidthPx
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams = params
        }
    }
}
