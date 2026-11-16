package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.extensions.config
import java.io.File

/**
 * The date tie-break preference only applies to Smart dialog and Automatic mode.
 * The Standard dialog keeps its original filename-only behaviour.
 */
private fun Context.usesDateTieBreak(): Boolean {
    return config.conflictHandlingMode != CONFLICT_HANDLING_STANDARD_DIALOG
}

/**
 * "Always ask": a same-name, identical-content pair is still reported as a conflict
 * when its dates differ. All other options skip it silently.
 */
fun Context.shouldGateDuplicatesByDate(): Boolean {
    return usesDateTieBreak() && config.dateTieBreakMode == DATE_TIE_BREAK_ASK
}

/**
 * "Use the incoming file's date": after an identical duplicate is skipped, copy the
 * source date onto the file that remains in the destination.
 */
fun Context.shouldUseSourceDateOnDuplicate(): Boolean {
    return usesDateTieBreak() && config.dateTieBreakMode == DATE_TIE_BREAK_USE_SOURCE
}

/**
 * Updates the filesystem date and the app's date records for a plain destination file.
 */
internal fun CryptoManager.applyDuplicateDateToPlainFile(
    context: Context,
    target: File,
    lastModified: Long,
    dateTaken: Long
): Boolean {
    if (!context.shouldUseSourceDateOnDuplicate() || !target.isFile) return false
    if (lastModified > 0L) {
        runCatching { target.setLastModified(lastModified) }
    }
    persistDecryptedDateTaken(context, target, dateTaken, lastModified)
    return true
}

/**
 * For an encrypted destination only the container file date can be adjusted without
 * re-encrypting the authenticated header, so this is a best-effort update.
 */
internal fun CryptoManager.applyDuplicateDateToEncryptedFile(
    context: Context,
    target: File,
    lastModified: Long
): Boolean {
    if (!context.shouldUseSourceDateOnDuplicate() || !target.isFile) return false
    if (lastModified > 0L) {
        runCatching { target.setLastModified(lastModified) }
    }
    return true
}
