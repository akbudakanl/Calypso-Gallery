package org.fossify.gallery.activities

import com.bumptech.glide.Glide
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.VaultNoticeDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.TYPE_SVGS

internal fun SettingsActivity.setupShowSvgFiles() {
    binding.settingsShowSvgFiles.isChecked = config.showSvgFiles
    binding.settingsShowSvgFilesHolder.setOnClickListener {
        if (config.showSvgFiles) {
            config.showSvgFiles = false
            config.filterMedia = config.filterMedia and TYPE_SVGS.inv()
            binding.settingsShowSvgFiles.isChecked = false
            Glide.get(applicationContext).clearMemory()
        } else {
            showEnableSvgConfirmationDialog()
        }
    }
}

private fun SettingsActivity.showEnableSvgConfirmationDialog() {
    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.show_svg_files),
        message = getString(R.string.show_svg_files_warning),
        positiveText = getString(R.string.enable_svg_files),
        negativeText = getString(org.fossify.commons.R.string.cancel),
        cancelable = true,
        canceledOnTouchOutside = true,
        onPositive = {
            config.showSvgFiles = true
            config.filterMedia = config.filterMedia or TYPE_SVGS
            binding.settingsShowSvgFiles.isChecked = true
            Glide.get(applicationContext).clearMemory()
        },
    )
}
