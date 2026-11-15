package org.fossify.gallery.activities

import android.os.Bundle
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.gallery.R
import org.fossify.gallery.adapters.ManageFoldersAdapter
import org.fossify.gallery.databinding.ActivityManageFoldersBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.isCipherSystemPath

class ExcludedFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageFoldersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateFolders()
        setupOptionsMenu()
        binding.manageFoldersToolbar.title = getString(org.fossify.commons.R.string.excluded_folders)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.manageFoldersAppbar),
            padBottomSystem = listOf(binding.manageFoldersList)
        )
        setupMaterialScrollListener(binding.manageFoldersList, binding.manageFoldersAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageFoldersAppbar, NavigationIcon.Arrow)
    }

    private fun updateFolders() {
        val folders = ArrayList<String>()
        config.excludedFolders
            .filterNot { isCipherSystemPath(it) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .mapTo(folders) { it }
        binding.manageFoldersPlaceholder.apply {
            beVisibleIf(folders.isEmpty())
            setTextColor(getProperTextColor())
            text = getString(R.string.excluded_activity_placeholder)
        }

        val adapter = ManageFoldersAdapter(this, folders, true, this, binding.manageFoldersList) {}
        binding.manageFoldersList.adapter = adapter
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
        FilePickerDialog(
            activity = this,
            internalStoragePath,
            pickFile = false,
            config.shouldShowHidden || config.temporarilyShowHiddenFolders,
            showFAB = false,
            canAddShowHiddenButton = true,
            enforceStorageRestrictions = false,
        ) {
            config.lastFilepickerPath = it
            config.addExcludedFolder(it)
            updateFolders()
        }
    }
}
