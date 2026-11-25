package org.fossify.gallery.dialogs

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.CompoundButtonCompat
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogAlbumCryptoConflictBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_MERGE
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_RENAME
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.CryptoManager

/**
 * Resolves how to handle existing destination album(s) with the same name during
 * album encryption or decryption, honoring the user's Settings preference.
 */
fun resolveAlbumCryptoOptions(
    activity: Activity,
    conflicts: List<CryptoManager.AlbumConflictPreview>,
    onCancel: (() -> Unit)? = null,
    callback: (CryptoManager.AlbumCryptoOptions) -> Unit
) {
    if (conflicts.isEmpty()) {
        callback(
            CryptoManager.AlbumCryptoOptions(
                resolution = CryptoManager.AlbumConflictResolution.MERGE,
                deleteIdenticalSource = false
            )
        )
        return
    }

    when (activity.config.albumCryptoConflictMode) {
        ALBUM_CRYPTO_CONFLICT_RENAME -> {
            callback(
                CryptoManager.AlbumCryptoOptions(
                    resolution = CryptoManager.AlbumConflictResolution.RENAME,
                    deleteIdenticalSource = false
                )
            )
        }
        ALBUM_CRYPTO_CONFLICT_MERGE -> {
            callback(
                CryptoManager.AlbumCryptoOptions(
                    resolution = CryptoManager.AlbumConflictResolution.MERGE,
                    deleteIdenticalSource = false
                )
            )
        }
        else -> {
            AlbumCryptoConflictDialog(activity, conflicts, onCancel, callback)
        }
    }
}

private const val MAX_PREVIEW_TARGET_PATHS = 4
private const val PATH_CHIP_RADIUS_DP = 8f
private const val PATH_CHIP_PAD_H_DP = 10f
private const val PATH_CHIP_PAD_V_DP = 8f

class AlbumCryptoConflictDialog(
    val activity: Activity,
    val conflicts: List<CryptoManager.AlbumConflictPreview>,
    val onCancel: (() -> Unit)? = null,
    val callback: (CryptoManager.AlbumCryptoOptions) -> Unit
) {
    private var handled = false

    init {
        val isMultiple = conflicts.size > 1
        val first = conflicts.first()
        val palette = AppearanceManager.resolve(activity, activity.config)
        val density = activity.resources.displayMetrics.density
        val controlTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(palette.primary, palette.secondary)
        )

        val binding = DialogAlbumCryptoConflictBinding.inflate(activity.layoutInflater)
        val targetPaths = conflicts.mapNotNull { it.targetPath?.takeIf(String::isNotBlank) }
        binding.albumCryptoConflictMessage.apply {
            text = if (isMultiple) {
                activity.getString(R.string.album_crypto_conflict_message_multiple, conflicts.size)
            } else {
                activity.getString(R.string.album_crypto_conflict_message, first.existingName)
            }
            setTextColor(palette.text)
            if (targetPaths.isNotEmpty()) {
                val smallPad = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
                setPadding(paddingLeft, paddingTop, paddingRight, smallPad)
            }
        }
        if (targetPaths.isNotEmpty()) {
            val padH = (PATH_CHIP_PAD_H_DP * density).toInt()
            val padV = (PATH_CHIP_PAD_V_DP * density).toInt()
            binding.albumCryptoConflictTargetPath.apply {
                visibility = View.VISIBLE
                setTextColor(palette.secondary)
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = PATH_CHIP_RADIUS_DP * density
                    setColor(palette.surface)
                    setStroke((1f * density).toInt().coerceAtLeast(1), palette.border)
                }
                text = if (targetPaths.size == 1) {
                    activity.humanizePath(targetPaths.first())
                } else {
                    val shown = targetPaths.take(MAX_PREVIEW_TARGET_PATHS)
                        .joinToString("\n") { "• ${activity.humanizePath(it)}" }
                    if (targetPaths.size > MAX_PREVIEW_TARGET_PATHS) {
                        "$shown\n+${targetPaths.size - MAX_PREVIEW_TARGET_PATHS}"
                    } else {
                        shown
                    }
                }
            }
        }
        binding.albumCryptoConflictRename.apply {
            text = if (isMultiple) {
                activity.getString(R.string.album_crypto_conflict_rename_option_multiple)
            } else {
                activity.getString(R.string.album_crypto_conflict_rename_option, first.renamedPreview)
            }
            setTextColor(palette.text)
            CompoundButtonCompat.setButtonTintList(this, controlTint)
        }
        binding.albumCryptoConflictMerge.apply {
            text = if (isMultiple) {
                activity.getString(R.string.album_crypto_conflict_merge_option_multiple)
            } else {
                activity.getString(R.string.album_crypto_conflict_merge_option, first.existingName)
            }
            setTextColor(palette.text)
            CompoundButtonCompat.setButtonTintList(this, controlTint)
        }
        binding.albumCryptoDontAskAgain.apply {
            setTextColor(palette.secondary)
            CompoundButtonCompat.setButtonTintList(this, controlTint)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                handled = true
                val isMerge = binding.albumCryptoConflictMerge.isChecked
                if (binding.albumCryptoDontAskAgain.isChecked) {
                    activity.config.albumCryptoConflictMode = if (isMerge) {
                        ALBUM_CRYPTO_CONFLICT_MERGE
                    } else {
                        ALBUM_CRYPTO_CONFLICT_RENAME
                    }
                }
                callback(
                    CryptoManager.AlbumCryptoOptions(
                        resolution = if (isMerge) {
                            CryptoManager.AlbumConflictResolution.MERGE
                        } else {
                            CryptoManager.AlbumConflictResolution.RENAME
                        },
                        deleteIdenticalSource = false
                    )
                )
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ ->
                if (!handled) {
                    handled = true
                    onCancel?.invoke()
                }
            }
            .setOnCancelListener {
                if (!handled) {
                    handled = true
                    onCancel?.invoke()
                }
            }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.album_crypto_conflict_title) { alertDialog ->
                    listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).forEach { which ->
                        alertDialog.getButton(which)?.setTextColor(palette.primary)
                    }
                }
            }
    }
}
