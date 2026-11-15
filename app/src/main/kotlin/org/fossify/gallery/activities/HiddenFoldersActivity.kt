package org.fossify.gallery.activities

import android.os.Bundle
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.gallery.R
import org.fossify.gallery.adapters.ManageHiddenFoldersAdapter
import org.fossify.gallery.databinding.ActivityManageFoldersBinding
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.addNoMedia
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getHiddenFoldersList

class HiddenFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageFoldersBinding::inflate)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateFolders()
        setupOptionsMenu()
        binding.manageFoldersToolbar.title = getString(R.string.hidden_folders)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.manageFoldersAppbar),
            padBottomSystem = listOf(binding.manageFoldersList)
        )
        setupMaterialScrollListener(binding.manageFoldersList, binding.manageFoldersAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageFoldersAppbar, NavigationIcon.Arrow)
        updateFolders()
    }

    private fun updateFolders() {
        getHiddenFoldersList { folders ->
            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    return@runOnUiThread
                }
                binding.manageFoldersPlaceholder.apply {
                    text = getString(R.string.hidden_folders_placeholder)
                    beVisibleIf(folders.isEmpty())
                    setTextColor(getProperTextColor())
                }

                val adapter = ManageHiddenFoldersAdapter(this, folders, this, binding.manageFoldersList) {}
                binding.manageFoldersList.adapter = adapter
            }
        }
    }

    private fun setupOptionsMenu() {
        binding.manageFoldersToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_folder -> addFolder()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun refreshItems() {
        updateFolders()
    }

    private fun addFolder() {
        if (isRPlus() && !isExternalStorageManager()) {
            GrantAllFilesDialog(this)
            return
        }

        FilePickerDialog(
            this,
            config.lastFilepickerPath,
            false,
            config.shouldShowHidden || config.temporarilyShowHiddenFolders,
            false,
            true
        ) {
            config.lastFilepickerPath = it
            ensureBackgroundThread {
                addNoMedia(it) {
                    config.addKnownHiddenFolder(it)
                    updateFolders()
                }
            }
        }
    }
}
