package org.fossify.gallery.dialogs

import android.app.Activity
import android.view.View
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogCryptoDuplicateSourceBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CRYPTO_DUPLICATE_SOURCE_DELETE
import org.fossify.gallery.helpers.CRYPTO_DUPLICATE_SOURCE_KEEP

/**
 * Resolves whether identical source files should be kept or deleted when the target
 * folder already contains an identical counterpart during file encryption or decryption.
 *
 * [onResolved] and [onCancelled] are always invoked on the UI thread.
 */
fun resolveCryptoDuplicateSourceOption(
    activity: Activity,
    isEncrypt: Boolean,
    hasIdenticalInTarget: () -> Boolean,
    onCancelled: (() -> Unit)? = null,
    onResolved: (deleteSourceOnIdentical: Boolean) -> Unit
) {
    when (activity.config.cryptoDuplicateSourceMode) {
        CRYPTO_DUPLICATE_SOURCE_KEEP -> onResolved(false)
        CRYPTO_DUPLICATE_SOURCE_DELETE -> onResolved(true)
        else -> {
            ensureBackgroundThread {
                val hasDuplicate = runCatching { hasIdenticalInTarget() }.getOrDefault(false)
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) {
                        onCancelled?.invoke()
                        return@runOnUiThread
                    }
                    if (!hasDuplicate) {
                        onResolved(true)
                    } else {
                        CryptoDuplicateSourceDialog(
                            activity = activity,
                            isEncrypt = isEncrypt,
                            onCancelled = onCancelled,
                            callback = onResolved
                        )
                    }
                }
            }
        }
    }
}

class CryptoDuplicateSourceDialog(
    val activity: Activity,
    val isEncrypt: Boolean,
    val onCancelled: (() -> Unit)? = null,
    val callback: (deleteSourceOnIdentical: Boolean) -> Unit
) {
    init {
        val binding = DialogCryptoDuplicateSourceBinding.inflate(activity.layoutInflater)
        binding.cryptoDuplicateSourceMessage.apply {
            text = activity.getString(
                if (isEncrypt) {
                    R.string.crypto_duplicate_source_encrypt_message
                } else {
                    R.string.crypto_duplicate_source_decrypt_message
                }
            )
            setTextColor(activity.getProperTextColor())
        }

        binding.cryptoDuplicateSourceWarning.apply {
            if (isEncrypt) {
                visibility = View.VISIBLE
                text = activity.getString(R.string.crypto_duplicate_source_encrypt_keep_warning)
                setTextColor(activity.getProperPrimaryColor())
            } else {
                visibility = View.GONE
            }
        }

        var handled = false
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                handled = true
                val deleteSource = binding.cryptoDuplicateSourceDelete.isChecked
                if (!binding.cryptoDuplicateSourceAlwaysAsk.isChecked) {
                    activity.config.cryptoDuplicateSourceMode = if (deleteSource) {
                        CRYPTO_DUPLICATE_SOURCE_DELETE
                    } else {
                        CRYPTO_DUPLICATE_SOURCE_KEEP
                    }
                }
                callback(deleteSource)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ ->
                handled = true
                onCancelled?.invoke()
            }
            .setOnCancelListener {
                if (!handled) {
                    handled = true
                    onCancelled?.invoke()
                }
            }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.crypto_duplicate_source_title)
            }
    }
}
