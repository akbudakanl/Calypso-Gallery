package org.fossify.gallery.extensions

import android.app.Dialog
import android.view.WindowManager

/**
 * Dialog windows do not inherit the host Activity's `FLAG_SECURE`, so a dialog showing vault
 * content must protect its own window. Kept in one place so every vault dialog can opt in with a
 * single call, independent of the user's `blockVaultScreenshots` preference.
 */
fun Dialog.secureVaultWindow() {
    window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}
