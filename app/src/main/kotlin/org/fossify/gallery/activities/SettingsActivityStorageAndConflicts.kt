package org.fossify.gallery.activities

import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.sumByLong
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogConflictHandlingModeBinding
import org.fossify.gallery.dialogs.showSkippedCryptoFilesSettingsDialog
import org.fossify.gallery.extensions.bindSettingInfoIcon
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.emptyTheRecycleBin
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.showRecycleBinEmptyingDialog
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_ASK
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_MERGE
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_RENAME
import org.fossify.gallery.helpers.CONFLICT_HANDLING_AUTO
import org.fossify.gallery.helpers.CONFLICT_HANDLING_SMART_DIALOG
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_ASK
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_DELETE
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_KEEP
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DATE_TIE_BREAK_ASK
import org.fossify.gallery.helpers.DATE_TIE_BREAK_KEEP_TARGET
import org.fossify.gallery.helpers.DATE_TIE_BREAK_USE_SOURCE
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.emptyVaultRecycleBinWithProgress
import org.fossify.gallery.helpers.getVaultRecycleBinSize
import java.io.File

internal fun SettingsActivity.setupConflictHandlingMode() {
    binding.settingsConflictHandlingMode.text = getConflictHandlingModeText()
    binding.settingsConflictHandlingModeHolder.setOnClickListener {
        val dialogBinding = DialogConflictHandlingModeBinding.inflate(layoutInflater)
        when (config.conflictHandlingMode) {
            CONFLICT_HANDLING_SMART_DIALOG -> dialogBinding.conflictHandlingSmartDialog.isChecked = true
            CONFLICT_HANDLING_AUTO -> dialogBinding.conflictHandlingAuto.isChecked = true
            else -> dialogBinding.conflictHandlingStandardDialog.isChecked = true
        }
        dialogBinding.fastLargeDuplicateCheckCheckbox.isChecked = config.fastLargeDuplicateCheck
        bindSettingInfoIcon(
            iconView = dialogBinding.fastLargeDuplicateCheckInfo,
            titleResId = R.string.fast_large_duplicate_check_info_title,
            messageResId = R.string.fast_large_duplicate_check_info_message
        )

        val tieBreakRadios = listOf(
            dialogBinding.dateTieBreakKeepTarget,
            dialogBinding.dateTieBreakUseSource,
            dialogBinding.dateTieBreakAsk
        )
        when (config.dateTieBreakMode) {
            DATE_TIE_BREAK_USE_SOURCE -> dialogBinding.dateTieBreakUseSource.isChecked = true
            DATE_TIE_BREAK_ASK -> dialogBinding.dateTieBreakAsk.isChecked = true
            else -> dialogBinding.dateTieBreakKeepTarget.isChecked = true
        }
        bindSettingInfoIcon(
            iconView = dialogBinding.dateTieBreakAskInfo,
            titleResId = R.string.date_tie_break_ask_info_title,
            messageResId = R.string.date_tie_break_ask_info_message
        )

        fun updateDependentState() {
            val usesDuplicateCheck = !dialogBinding.conflictHandlingStandardDialog.isChecked
            val isAutomatic = dialogBinding.conflictHandlingAuto.isChecked
            // Automatic (Experimental) never shows a dialog, so "Always ask" is not offered
            // there. If it was selected before switching modes, fall back to the default.
            val askAllowed = usesDuplicateCheck && !isAutomatic
            dialogBinding.fastLargeDuplicateCheckCheckbox.isEnabled = usesDuplicateCheck
            dialogBinding.fastLargeDuplicateCheckRow.alpha = if (usesDuplicateCheck) 1f else 0.45f
            dialogBinding.fastLargeDuplicateCheckHint.alpha = if (usesDuplicateCheck) 0.75f else 0.35f

            dialogBinding.dateTieBreakLabel.alpha = if (usesDuplicateCheck) 1f else 0.45f
            tieBreakRadios.forEach { radio ->
                radio.isEnabled = if (radio === dialogBinding.dateTieBreakAsk) askAllowed else usesDuplicateCheck
                radio.alpha = if (radio.isEnabled) 1f else 0.45f
            }
            dialogBinding.dateTieBreakAskInfo.isEnabled = askAllowed
            dialogBinding.dateTieBreakAskInfo.alpha = if (askAllowed) 1f else 0.45f
            dialogBinding.dateTieBreakHint.alpha = if (usesDuplicateCheck) 0.75f else 0.35f

            if (isAutomatic && dialogBinding.dateTieBreakAsk.isChecked) {
                dialogBinding.dateTieBreakAsk.isChecked = false
                dialogBinding.dateTieBreakKeepTarget.isChecked = true
            }
        }
        updateDependentState()

        dialogBinding.conflictHandlingRadioGroup.setOnCheckedChangeListener { _, _ ->
            updateDependentState()
        }
        tieBreakRadios.forEach { radio ->
            radio.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    tieBreakRadios.filter { it !== radio }.forEach { it.isChecked = false }
                }
            }
        }

        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                config.conflictHandlingMode = when {
                    dialogBinding.conflictHandlingSmartDialog.isChecked -> CONFLICT_HANDLING_SMART_DIALOG
                    dialogBinding.conflictHandlingAuto.isChecked -> CONFLICT_HANDLING_AUTO
                    else -> CONFLICT_HANDLING_STANDARD_DIALOG
                }
                config.fastLargeDuplicateCheck = dialogBinding.fastLargeDuplicateCheckCheckbox.isChecked
                config.dateTieBreakMode = when {
                    dialogBinding.dateTieBreakAsk.isChecked -> DATE_TIE_BREAK_ASK
                    dialogBinding.dateTieBreakUseSource.isChecked -> DATE_TIE_BREAK_USE_SOURCE
                    else -> DATE_TIE_BREAK_KEEP_TARGET
                }
                binding.settingsConflictHandlingMode.text = getConflictHandlingModeText()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                setupDialogStuff(dialogBinding.root, this, R.string.conflict_handling_explanation_title)
            }
    }
}

private fun SettingsActivity.getConflictHandlingModeText() = getString(
    when (config.conflictHandlingMode) {
        CONFLICT_HANDLING_SMART_DIALOG -> R.string.conflict_handling_smart_dialog_summary
        CONFLICT_HANDLING_AUTO -> R.string.conflict_handling_auto_summary
        else -> R.string.conflict_handling_standard_dialog_summary
    }
)

internal fun SettingsActivity.setupSkippedEncryptFilesMode() {
    val valueView = binding.settingsSkippedEncryptFiles
    val updateValue = {
        valueView.text = getString(
            when (config.skippedEncryptFilesMode) {
                CRYPTO_SKIPPED_FILES_KEEP -> R.string.skipped_crypto_files_keep
                CRYPTO_SKIPPED_FILES_DELETE -> R.string.skipped_crypto_files_delete
                else -> R.string.skipped_crypto_files_ask
            }
        )
    }
    updateValue()
    binding.settingsSkippedEncryptFilesHolder.setOnClickListener {
        showSkippedCryptoFilesSettingsDialog(this) { updateValue() }
    }
}

internal fun SettingsActivity.setupAlbumCryptoConflictMode() {
    binding.settingsAlbumCryptoConflict.text = getAlbumCryptoConflictModeText()
    binding.settingsAlbumCryptoConflictHolder.setOnClickListener {
        val items = arrayListOf(
            RadioItem(ALBUM_CRYPTO_CONFLICT_ASK, getString(R.string.album_crypto_conflict_ask)),
            RadioItem(ALBUM_CRYPTO_CONFLICT_MERGE, getString(R.string.album_crypto_conflict_merge)),
            RadioItem(ALBUM_CRYPTO_CONFLICT_RENAME, getString(R.string.album_crypto_conflict_rename))
        )
        RadioGroupDialog(
            activity = this@setupAlbumCryptoConflictMode,
            items = items,
            checkedItemId = config.albumCryptoConflictMode,
            titleId = R.string.album_crypto_conflict_mode_label,
        ) {
            config.albumCryptoConflictMode = it as Int
            binding.settingsAlbumCryptoConflict.text = getAlbumCryptoConflictModeText()
        }
    }
}

private fun SettingsActivity.getAlbumCryptoConflictModeText() = getString(
    when (config.albumCryptoConflictMode) {
        ALBUM_CRYPTO_CONFLICT_MERGE -> R.string.album_crypto_conflict_merge
        ALBUM_CRYPTO_CONFLICT_RENAME -> R.string.album_crypto_conflict_rename
        else -> R.string.album_crypto_conflict_ask
    }
)

internal fun SettingsActivity.setupDeleteEmptyFolders() {
    binding.settingsDeleteEmptyFolders.isChecked = config.deleteEmptyFolders
    binding.settingsDeleteEmptyFoldersHolder.setOnClickListener {
        binding.settingsDeleteEmptyFolders.toggle()
        config.deleteEmptyFolders = binding.settingsDeleteEmptyFolders.isChecked
    }
}

internal fun SettingsActivity.setupUseRecycleBin() {
    updateRecycleBinButtons()
    binding.settingsUseRecycleBin.isChecked = config.useRecycleBin
    binding.settingsUseRecycleBinHolder.setOnClickListener {
        binding.settingsUseRecycleBin.toggle()
        config.useRecycleBin = binding.settingsUseRecycleBin.isChecked
        updateRecycleBinButtons()
    }
}

internal fun SettingsActivity.setupShowRecycleBin() {
    binding.settingsShowRecycleBin.isChecked = config.showRecycleBinAtFolders
    binding.settingsShowRecycleBinHolder.setOnClickListener {
        binding.settingsShowRecycleBin.toggle()
        config.showRecycleBinAtFolders = binding.settingsShowRecycleBin.isChecked
        updateRecycleBinButtons()
    }
}

internal fun SettingsActivity.setupShowRecycleBinLast() {
    binding.settingsShowRecycleBinLast.isChecked = config.showRecycleBinLast
    binding.settingsShowRecycleBinLastHolder.setOnClickListener {
        binding.settingsShowRecycleBinLast.toggle()
        config.showRecycleBinLast = binding.settingsShowRecycleBinLast.isChecked
        if (config.showRecycleBinLast) {
            config.removePinnedFolders(setOf(RECYCLE_BIN))
        }
    }
}

internal fun SettingsActivity.updateRecycleBinButtons() {
    binding.settingsShowRecycleBinLastHolder.beVisibleIf(config.useRecycleBin && config.showRecycleBinAtFolders)
    binding.settingsEmptyRecycleBinHolder.beVisibleIf(config.useRecycleBin)
    binding.settingsShowRecycleBinHolder.beVisibleIf(config.useRecycleBin)
    if (!isSettingUpItems) {
        refreshRowDecorations()
    }
}

internal fun SettingsActivity.setupEmptyRecycleBin() {
    ensureBackgroundThread {
        try {
            mRecycleBinContentSize = if (CryptoManager.isVaultUnlocked) {
                CryptoManager.getVaultRecycleBinSize(this)
            } else {
                mediaDB.getDeletedMedia().sumByLong { medium ->
                    val size = medium.size
                    if (size == 0L) {
                        val path = medium.path.removePrefix(RECYCLE_BIN).prependIndent(recycleBinPath)
                        File(path).length()
                    } else {
                        size
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        runOnUiThread {
            binding.settingsEmptyRecycleBinSize.text = mRecycleBinContentSize.formatSize()
        }
    }

    binding.settingsEmptyRecycleBinHolder.setOnClickListener {
        if (mRecycleBinContentSize == 0L) {
            toast(org.fossify.commons.R.string.recycle_bin_empty)
        } else {
            showRecycleBinEmptyingDialog {
                if (CryptoManager.isVaultUnlocked) {
                    emptyVaultRecycleBinWithProgress { emptied ->
                        if (emptied) {
                            toast(org.fossify.commons.R.string.recycle_bin_emptied)
                        }
                        ensureBackgroundThread {
                            val remainingSize = if (emptied) {
                                0L
                            } else {
                                CryptoManager.getVaultRecycleBinSize(this)
                            }
                            runOnUiThread {
                                mRecycleBinContentSize = remainingSize
                                binding.settingsEmptyRecycleBinSize.text = remainingSize.formatSize()
                            }
                        }
                    }
                } else {
                    emptyTheRecycleBin { _ ->
                        ensureBackgroundThread {
                            val remainingSize = runCatching {
                                mediaDB.getDeletedMedia().sumByLong { medium ->
                                    val size = medium.size
                                    if (size == 0L) {
                                        val path = medium.path.removePrefix(RECYCLE_BIN).prependIndent(recycleBinPath)
                                        File(path).length()
                                    } else {
                                        size
                                    }
                                }
                            }.getOrDefault(0L)
                            runOnUiThread {
                                mRecycleBinContentSize = remainingSize
                                binding.settingsEmptyRecycleBinSize.text = remainingSize.formatSize()
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun SettingsActivity.setupClearCache() {
    ensureBackgroundThread {
        val size = cacheDir.getProperSize(true).formatSize()
        runOnUiThread {
            binding.settingsClearCacheSize.text = size
        }
    }

    binding.settingsClearCacheHolder.setOnClickListener {
        ensureBackgroundThread {
            cacheDir.deleteRecursively()
            runOnUiThread {
                binding.settingsClearCacheSize.text = cacheDir.getProperSize(true).formatSize()
            }
        }
    }
}
