package org.fossify.gallery.dialogs

import android.app.Activity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogAlbumCryptoConflictBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_MERGE
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_RENAME
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

        val binding = DialogAlbumCryptoConflictBinding.inflate(activity.layoutInflater)
        binding.albumCryptoConflictMessage.apply {
            text = if (isMultiple) {
                activity.getString(R.string.album_crypto_conflict_message_multiple, conflicts.size)
            } else {
                activity.getString(R.string.album_crypto_conflict_message, first.existingName)
            }
            setTextColor(activity.getProperTextColor())
        }
        binding.albumCryptoConflictRename.text = if (isMultiple) {
            activity.getString(R.string.album_crypto_conflict_rename_option_multiple)
        } else {
            activity.getString(R.string.album_crypto_conflict_rename_option, first.renamedPreview)
        }
        binding.albumCryptoConflictMerge.text = if (isMultiple) {
            activity.getString(R.string.album_crypto_conflict_merge_option_multiple)
        } else {
            activity.getString(R.string.album_crypto_conflict_merge_option, first.existingName)
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
                activity.setupDialogStuff(binding.root, this, R.string.album_crypto_conflict_title)
            }
    }
}
