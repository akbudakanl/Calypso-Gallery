package org.fossify.gallery.extensions

import android.app.Activity
import org.fossify.commons.extensions.baseConfig
import org.fossify.gallery.dialogs.AppSecurityDialog

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
