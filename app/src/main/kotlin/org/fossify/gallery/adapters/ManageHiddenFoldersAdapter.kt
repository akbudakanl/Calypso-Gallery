package org.fossify.gallery.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isPathOnSD
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyRecyclerView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ItemManageFolderBinding
import org.fossify.gallery.dialogs.GrantAllFilesDialog
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.isThisOrParentFolderHidden
import org.fossify.gallery.extensions.removeNoMedia
import org.fossify.gallery.helpers.CalypsoActionModeController
import org.fossify.gallery.helpers.CalypsoOverflowMenuPopup
import org.fossify.gallery.helpers.HiddenFoldersPolicy

class ManageHiddenFoldersAdapter(
    activity: BaseSimpleActivity,
    var folders: ArrayList<String>,
    val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private val overflowPopup = CalypsoOverflowMenuPopup(activity)
    private val calypsoActionMode = CalypsoActionModeController(
        activity = activity,
        adapter = this,
        barActionIds = CalypsoActionModeController.HIDDEN_FOLDERS_BAR_ACTION_IDS
    )

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_hidden_folders

    override fun prepareActionMode(menu: Menu) {
        calypsoActionMode.onPrepareActionMode(menu)
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_unhide -> tryUnhideFolders()
        }
    }

    override fun getSelectableItemCount() = folders.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = folders.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = folders.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {
        calypsoActionMode.onActionModeCreated(actMode)
    }

    override fun onActionModeDestroyed() {
        calypsoActionMode.onActionModeDestroyed()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemManageFolderBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.bindView(folder, true, true) { itemView, _ ->
            setupView(itemView, folder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = folders.size

    private fun getSelectedItems() = folders.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<String>

    private fun setupView(view: View, folder: String) {
        ItemManageFolderBinding.bind(view).apply {
            root.setupViewBackground(activity)
            manageFolderHolder.isSelected = selectedKeys.contains(folder.hashCode())
            manageFolderTitle.apply {
                text = folder
                setTextColor(context.getProperTextColor())
            }

            overflowMenuIcon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflowMenuIcon.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, folder)
            }
        }
    }

    private fun showPopupMenu(view: View, folder: String) {
        finishActMode()
        overflowPopup.showMenu(view, getActionMenuId()) { item ->
            val folderId = folder.hashCode()
            when (item.itemId) {
                R.id.cab_unhide -> {
                    executeItemMenuOperation(folderId) {
                        tryUnhideFolders()
                    }
                }
            }
        }
    }

    private fun executeItemMenuOperation(folderId: Int, callback: () -> Unit) {
        selectedKeys.clear()
        selectedKeys.add(folderId)
        callback()
    }

    private fun tryUnhideFolders() {
        val selected = getSelectedItems()
        val dotHidden = selected.filter { HiddenFoldersPolicy.isDotHiddenPath(it) || it.isThisOrParentFolderHidden() }
        val removable = selected.filterNot { it in dotHidden }
        if (dotHidden.isNotEmpty()) {
            ConfirmationDialog(activity, "", R.string.cant_unhide_folder, org.fossify.commons.R.string.ok, 0) {}
            dotHidden.forEach { selectedKeys.remove(it.hashCode()) }
        }

        if (removable.isEmpty()) {
            finishActMode()
            return
        }

        val sdCardPaths = removable.filter { activity.isPathOnSD(it) }
        if (sdCardPaths.isNotEmpty()) {
            activity.handleSAFDialog(sdCardPaths.first()) { granted ->
                if (granted) {
                    unhideFolders(removable)
                }
            }
        } else if (isRPlus() && !isExternalStorageManager()) {
            GrantAllFilesDialog(activity)
            finishActMode()
        } else {
            unhideFolders(removable)
        }
    }

    private fun unhideFolders(removable: List<String>) {
        val position = getSelectedItemPositions()
        removable.forEach {
            activity.removeNoMedia(it)
            activity.config.removeKnownHiddenFolder(it)
        }

        folders.removeAll(removable.toSet())
        removeSelectedItems(position)
        if (folders.isEmpty()) {
            listener?.refreshItems()
        } else {
            finishActMode()
        }
    }
}
