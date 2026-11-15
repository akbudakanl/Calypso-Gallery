package org.fossify.gallery.dialogs

import android.app.Activity
import android.graphics.Color
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogSkippedCryptoFilesBinding
import org.fossify.gallery.databinding.DialogSkippedCryptoFilesModeBinding
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteDBPaths
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateVirtualAlbumItemPath
import org.fossify.gallery.helpers.CONFLICT_HANDLING_AUTO
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_ASK
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_DELETE
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_KEEP
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.MediaIdentity
import java.io.File

fun resolveSkippedCryptoMessageRes(
    isEncrypt: Boolean,
    conflictHandlingMode: Int,
    hasUserSkipped: Boolean,
    hasIdenticalSkipped: Boolean
): Int {
    val messageKind = when (conflictHandlingMode) {
        CONFLICT_HANDLING_STANDARD_DIALOG -> 0
        CONFLICT_HANDLING_AUTO -> 1
        else -> when {
            hasUserSkipped && hasIdenticalSkipped -> 2
            hasIdenticalSkipped -> 1
            else -> 0
        }
    }
    return when (messageKind) {
        1 -> if (isEncrypt) R.string.skipped_crypto_files_identical_encrypt_message else R.string.skipped_crypto_files_identical_decrypt_message
        2 -> if (isEncrypt) R.string.skipped_crypto_files_both_encrypt_message else R.string.skipped_crypto_files_both_decrypt_message
        else -> if (isEncrypt) R.string.skipped_crypto_files_encrypt_message else R.string.skipped_crypto_files_decrypt_message
    }
}

fun Activity.getDestructiveRedColor(): Int {
    val isDarkBg = getProperBackgroundColor().getContrastColor() == Color.WHITE
    return if (isDarkBg) Color.parseColor("#FF5252") else Color.parseColor("#D32F2F")
}

fun showDeleteSkippedFilesConfirmation(
    activity: Activity,
    onConfirm: () -> Unit,
    onCancel: () -> Unit = {}
) {
    if (activity.isFinishing || activity.isDestroyed) {
        onCancel()
        return
    }

    val padding = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
    val mediumPadding = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
    val messageView = MyTextView(activity).apply {
        setPadding(padding, mediumPadding, padding, mediumPadding)
        text = activity.getString(R.string.delete_skipped_files_confirm_message)
        setTextColor(activity.getProperTextColor())
    }

    var handled = false
    activity.getAlertDialogBuilder()
        .setNegativeButton(org.fossify.commons.R.string.yes) { _, _ ->
            handled = true
            onConfirm()
        }
        .setPositiveButton(org.fossify.commons.R.string.no) { _, _ ->
            handled = true
            onCancel()
        }
        .setOnCancelListener {
            if (!handled) {
                handled = true
                onCancel()
            }
        }
        .apply {
            activity.setupDialogStuff(
                view = messageView,
                dialog = this,
                titleId = R.string.delete_skipped_files_confirm_title
            ) { alertDialog ->
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(activity.getDestructiveRedColor())
            }
        }
}

private fun Activity.getSkippedCryptoFilesMode(isEncrypt: Boolean): Int {
    return if (isEncrypt) config.skippedEncryptFilesMode else config.skippedDecryptFilesMode
}

private fun Activity.setSkippedCryptoFilesMode(isEncrypt: Boolean, mode: Int) {
    if (isEncrypt) {
        config.skippedEncryptFilesMode = mode
    } else {
        config.skippedDecryptFilesMode = mode
    }
}

fun showSkippedCryptoFilesSettingsDialog(
    activity: Activity,
    isEncrypt: Boolean,
    onUpdated: (Int) -> Unit
) {
    val binding = DialogSkippedCryptoFilesModeBinding.inflate(activity.layoutInflater)
    val checkCurrentSelection = {
        when (activity.getSkippedCryptoFilesMode(isEncrypt)) {
            CRYPTO_SKIPPED_FILES_KEEP -> binding.skippedCryptoFilesModeKeep.isChecked = true
            CRYPTO_SKIPPED_FILES_DELETE -> binding.skippedCryptoFilesModeDelete.isChecked = true
            else -> binding.skippedCryptoFilesModeAsk.isChecked = true
        }
    }
    checkCurrentSelection()

    val titleResId = if (isEncrypt) {
        R.string.skipped_encrypt_files_mode_label
    } else {
        R.string.skipped_decrypt_files_mode_label
    }

    activity.getAlertDialogBuilder()
        .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        .apply {
            activity.setupDialogStuff(
                view = binding.root,
                dialog = this,
                titleId = titleResId
            ) { alertDialog ->
                val redColor = activity.getDestructiveRedColor()
                val deleteRadio: RadioButton = binding.skippedCryptoFilesModeDelete
                deleteRadio.setTextColor(redColor)

                binding.skippedCryptoFilesModeAsk.setOnClickListener {
                    activity.setSkippedCryptoFilesMode(isEncrypt, CRYPTO_SKIPPED_FILES_ASK)
                    onUpdated(CRYPTO_SKIPPED_FILES_ASK)
                    alertDialog.dismiss()
                }
                binding.skippedCryptoFilesModeKeep.setOnClickListener {
                    activity.setSkippedCryptoFilesMode(isEncrypt, CRYPTO_SKIPPED_FILES_KEEP)
                    onUpdated(CRYPTO_SKIPPED_FILES_KEEP)
                    alertDialog.dismiss()
                }
                binding.skippedCryptoFilesModeDelete.setOnClickListener {
                    showDeleteSkippedFilesConfirmation(
                        activity = activity,
                        onConfirm = {
                            activity.setSkippedCryptoFilesMode(isEncrypt, CRYPTO_SKIPPED_FILES_DELETE)
                            onUpdated(CRYPTO_SKIPPED_FILES_DELETE)
                            alertDialog.dismiss()
                        },
                        onCancel = {
                            checkCurrentSelection()
                        }
                    )
                }
            }
        }
}

fun BaseSimpleActivity.resolveSkippedCryptoFiles(
    isEncrypt: Boolean,
    skippedFiles: List<File>,
    identicalTargetMap: Map<String, File> = emptyMap(),
    identicalSkippedPaths: Set<String> = identicalTargetMap.keys,
    forceCleanupEmptyAlbum: Boolean = false,
    onComplete: (deletedCount: Int) -> Unit
) {
    val existingSkipped = skippedFiles.filter { it.exists() && it.isFile }
    if (existingSkipped.isEmpty()) {
        onComplete(0)
        return
    }
    val hasIdenticalSkipped = existingSkipped.any { it.absolutePath in identicalSkippedPaths }
    val hasUserSkipped = existingSkipped.any { it.absolutePath !in identicalSkippedPaths }

    val performDelete = {
        ensureBackgroundThread {
            var deletedCount = 0
            val deletedPaths = ArrayList<String>(existingSkipped.size)
            val sourceDirs = LinkedHashSet<File>()

            for (file in existingSkipped) {
                file.parentFile?.let { sourceDirs.add(it) }
                val sourcePath = file.absolutePath
                val isEnc = CryptoManager.isEncryptedFile(file.name)
                val identicalTarget = identicalTargetMap[sourcePath]
                if (identicalTarget != null) {
                    val isTargetEnc = CryptoManager.isEncryptedFile(identicalTarget.name)
                    when {
                        !isEnc && isTargetEnc -> {
                            val plainIdentity = runCatching { MediaIdentity.ofWithFolder(this, file) }.getOrNull()
                            runCatching {
                                markReferencesOnEncrypt(
                                    sourcePath = sourcePath,
                                    encPath = identicalTarget.absolutePath,
                                    plaintextIdentity = plainIdentity,
                                    markPlaintextDormant = true
                                )
                            }
                        }
                        isEnc && !isTargetEnc -> {
                            runCatching {
                                markReferencesOnDecrypt(
                                    encPath = sourcePath,
                                    decryptedFile = identicalTarget,
                                    hideStealthReferences = true
                                )
                            }
                        }
                        isEnc && isTargetEnc -> {
                            runCatching {
                                updateVirtualAlbumItemPath(sourcePath, identicalTarget.absolutePath)
                            }
                        }
                        else -> {
                            runCatching {
                                updateDBMediaPath(sourcePath, identicalTarget.absolutePath)
                            }
                        }
                    }
                }
                val deleted = if (isEnc) {
                    CryptoManager.deleteEncryptedFile(this, file)
                } else {
                    CryptoManager.secureWipeAndDeleteFile(this, file)
                }
                if (deleted || !file.exists()) {
                    deletedCount++
                    deletedPaths.add(sourcePath)
                }
            }

            if (deletedPaths.isNotEmpty()) {
                runCatching { deleteDBPaths(deletedPaths) }
            }
            sourceDirs.forEach { dir ->
                if (CryptoManager.isEncryptedFolder(dir)) {
                    CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(this, dir)
                } else {
                    cleanupEmptyPlaintextAlbumIfNeeded(dir, forceIfEmpty = forceCleanupEmptyAlbum)
                }
            }
            CryptoManager.clearAppImageCaches(this)

            runOnUiThread {
                onComplete(deletedCount)
            }
        }
    }

    when (getSkippedCryptoFilesMode(isEncrypt)) {
        CRYPTO_SKIPPED_FILES_KEEP -> onComplete(0)
        CRYPTO_SKIPPED_FILES_DELETE -> performDelete()
        else -> {
            if (isFinishing || isDestroyed) {
                onComplete(0)
                return
            }

            val binding = DialogSkippedCryptoFilesBinding.inflate(layoutInflater)
            binding.skippedCryptoFilesMessage.text = getString(
                resolveSkippedCryptoMessageRes(
                    isEncrypt = isEncrypt,
                    conflictHandlingMode = config.conflictHandlingMode,
                    hasUserSkipped = hasUserSkipped,
                    hasIdenticalSkipped = hasIdenticalSkipped
                )
            )

            var handled = false
            getAlertDialogBuilder()
                .setNegativeButton(org.fossify.commons.R.string.delete, null)
                .setPositiveButton(R.string.skipped_crypto_files_keep) { _, _ ->
                    handled = true
                    if (!binding.skippedCryptoFilesAlwaysAsk.isChecked) {
                        setSkippedCryptoFilesMode(isEncrypt, CRYPTO_SKIPPED_FILES_KEEP)
                    }
                    onComplete(0)
                }
                .setOnCancelListener {
                    if (!handled) {
                        handled = true
                        onComplete(0)
                    }
                }
                .apply {
                    setupDialogStuff(binding.root, this) { alertDialog ->
                        val deleteBtn = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        deleteBtn?.setTextColor(getDestructiveRedColor())
                        deleteBtn?.setOnClickListener {
                            showDeleteSkippedFilesConfirmation(
                                activity = this@resolveSkippedCryptoFiles,
                                onConfirm = {
                                    handled = true
                                    if (!binding.skippedCryptoFilesAlwaysAsk.isChecked) {
                                        setSkippedCryptoFilesMode(isEncrypt, CRYPTO_SKIPPED_FILES_DELETE)
                                    }
                                    alertDialog.dismiss()
                                    performDelete()
                                }
                            )
                        }
                    }
                }
        }
    }
}
