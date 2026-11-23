package org.fossify.gallery.dialogs

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyEditText
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R
import org.fossify.gallery.adapters.DirectoryAdapter
import org.fossify.gallery.databinding.DialogDirectoryPickerBinding
import org.fossify.gallery.extensions.*
import org.fossify.gallery.models.Directory

private fun Activity.buildAlbumNameField(initialText: String): Pair<FrameLayout, MyEditText> {
    val padding = (20 * resources.displayMetrics.density).toInt()
    val editText = MyEditText(this).apply {
        hint = getString(R.string.virtual_folder_name)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine(true)
        setText(initialText)
        setSelection(text?.length ?: 0)
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    val container = FrameLayout(this).apply {
        setPadding(padding, padding, padding, 0)
        addView(editText)
    }
    return container to editText
}

/** Asks for a name and creates a new virtual album. [callback] receives the id of the new album. */
class CreateVirtualAlbumDialog(
    val activity: Activity,
    val isHidden: Boolean = false,
    val parentPhysicalPath: String = "",
    val callback: (albumId: Long) -> Unit
) {
    init {
        val (container, editText) = activity.buildAlbumNameField("")
        val titleId = if (isHidden) {
            R.string.create_new_encrypted_virtual_album
        } else {
            R.string.create_virtual_album
        }
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(container, this, titleId = titleId) { alertDialog ->
                    alertDialog.showKeyboard(editText)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = editText.text.toString().trim()
                        if (name.isEmpty()) {
                            activity.toast(R.string.virtual_album_name_empty)
                            return@setOnClickListener
                        }

                        ensureBackgroundThread {
                            if (activity.virtualAlbumNameExists(name)) {
                                activity.runOnUiThread { activity.toast(R.string.virtual_album_exists) }
                                return@ensureBackgroundThread
                            }

                            val proceedCreate = {
                                ensureBackgroundThread {
                                    val albumId = activity.createVirtualAlbum(name, isHidden = isHidden, parentPhysicalPath = parentPhysicalPath)
                                    activity.runOnUiThread {
                                        if (albumId == -1L) {
                                            activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                                        } else {
                                            callback(albumId)
                                        }
                                    }
                                }
                            }

                            activity.runOnUiThread {
                                alertDialog.dismiss()
                                proceedCreate()
                            }
                        }
                    }
                }
            }
    }
}

class RenameVirtualAlbumDialog(
    val activity: Activity,
    val albumId: Long,
    val currentName: String,
    val callback: () -> Unit
) {
    init {
        val (container, editText) = activity.buildAlbumNameField(currentName)
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(container, this, R.string.rename_virtual_album) { alertDialog ->
                    alertDialog.showKeyboard(editText)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = editText.text.toString().trim()
                        if (name.isEmpty()) {
                            activity.toast(R.string.virtual_album_name_empty)
                            return@setOnClickListener
                        }

                        if (name == currentName) {
                            alertDialog.dismiss()
                            return@setOnClickListener
                        }

                        ensureBackgroundThread {
                            val renamed = activity.renameVirtualAlbum(albumId, name)
                            activity.runOnUiThread {
                                if (renamed) {
                                    alertDialog.dismiss()
                                    callback()
                                } else {
                                    activity.toast(R.string.virtual_album_exists)
                                }
                            }
                        }
                    }
                }
            }
    }
}

/**
 * Lets the user pick one of the existing virtual albums (with cover thumbnails, item counts, and search),
 * or create a new one in the matching encryption scope.
 * [callback] receives the id and the name of the chosen album.
 * @param sourceAlbumId the album the items are being added from. It stays visible in the picker just like
 *   a physical source folder, but selecting it shows a warning instead of adding the items to itself.
 */
class PickVirtualAlbumDialog(
    val activity: BaseSimpleActivity,
    val sourceAlbumId: Long? = null,
    val isHiddenSource: Boolean = false,
    val sourcePath: String = "",
    val callback: (albumId: Long, albumName: String) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var allAlbums = ArrayList<Directory>()
    private var hasLoadedAlbums = false
    private val binding = DialogDirectoryPickerBinding.inflate(activity.layoutInflater)
    private val isGridViewType = activity.config.viewTypeFolders == VIEW_TYPE_GRID
    private val searchView = binding.folderSearchView
    private val searchEditText = searchView.binding.topToolbarSearch
    private val searchBarContainer = searchView.binding.searchBarContainer

    init {
        (binding.directoriesGrid.layoutManager as MyGridLayoutManager).apply {
            orientation = if (activity.config.scrollHorizontally && isGridViewType) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
            spanCount = if (isGridViewType) activity.config.dirColumnCnt else 1
        }

        binding.directoriesFastscroller.updateColors(activity.getProperPrimaryColor())
        configureSearchView()

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setNeutralButton(R.string.new_album) { _, _ -> openCreateVirtualAlbum() }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.add_to_virtual_album) { alertDialog ->
                    dialog = alertDialog
                    binding.directoriesShowHidden.beGone()

                    alertDialog.onBackPressedDispatcher.addCallback(alertDialog) {
                        if (searchView.isSearchOpen) {
                            searchView.closeSearch()
                        } else {
                            isEnabled = false
                            alertDialog.onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }

        loadAlbums()
    }

    private fun configureSearchView() = with(searchView) {
        updateHintText(context.getString(R.string.search_virtual_albums))
        searchEditText.imeOptions = EditorInfo.IME_ACTION_DONE

        toggleHideOnScroll(!activity.config.scrollHorizontally)
        setupMenu()
        setSearchViewListeners()
        updateSearchViewUi()
    }

    private fun MySearchMenu.updateSearchViewUi() {
        requireToolbar().beInvisible()
        updateColors()
        applyCalypsoSearchBarStyle()
        setBackgroundColor(Color.TRANSPARENT)
        searchBarContainer.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun MySearchMenu.setSearchViewListeners() {
        onSearchOpenListener = {
            updateSearchViewLeftIcon(org.fossify.commons.R.drawable.ic_cross_vector)
        }

        onSearchClosedListener = {
            searchEditText.clearFocus()
            activity.hideKeyboard(searchEditText)
            updateSearchViewLeftIcon(org.fossify.commons.R.drawable.ic_search_vector)
        }

        onSearchTextChangedListener = { text ->
            filterAlbumsBySearchQuery(text)
        }
    }

    private fun updateSearchViewLeftIcon(iconResId: Int) = with(searchView.binding.topToolbarSearchIcon) {
        post {
            setImageResource(iconResId)
        }
    }

    private fun loadAlbums() {
        ensureBackgroundThread {
            val rawDirs = activity.getVirtualAlbumDirectories(
                isSecretVaultView = isHiddenSource,
                syncFromDisk = true
            )
            // Keep strictly within the matching encryption scope even when the vault is unlocked.
            val filtered = rawDirs.filter { dir ->
                if (dir.path.getVirtualAlbumId() == null) {
                    return@filter false
                }
                activity.isHiddenVirtualAlbumPath(dir.path) == isHiddenSource
            }
            val sorted = activity.getSortedDirectories(ArrayList(filtered))
            // DirectoryAdapter renders subfoldersMediaCount (not mediaCnt). Virtual albums have no
            // real subfolders, so mirror the resolved media count as PickDirectoryDialog does;
            // otherwise every album would be shown with a stale 0 in this picker.
            sorted.forEach { it.subfoldersMediaCount = it.mediaCnt }

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }

                hasLoadedAlbums = true
                allAlbums = sorted
                val currentQuery = searchEditText.text?.toString().orEmpty()
                val initialDirs = filterList(sorted, currentQuery)
                checkPlaceholderVisibility(initialDirs)

                val adapter = DirectoryAdapter(
                    activity = activity,
                    dirs = initialDirs.clone() as ArrayList<Directory>,
                    listener = null,
                    recyclerView = binding.directoriesGrid,
                    isPickIntent = true
                ) { clicked ->
                    val clickedDir = clicked as Directory
                    val albumId = clickedDir.path.getVirtualAlbumId() ?: return@DirectoryAdapter
                    if (albumId == sourceAlbumId) {
                        activity.toast(org.fossify.commons.R.string.source_and_destination_same)
                        return@DirectoryAdapter
                    }
                    activity.hideKeyboard(searchEditText)
                    dialog?.dismiss()
                    callback(albumId, clickedDir.name)
                }

                val scrollHorizontally = activity.config.scrollHorizontally && isGridViewType
                binding.apply {
                    directoriesGrid.adapter = adapter
                    directoriesFastscroller.setScrollVertically(!scrollHorizontally)
                }
            }
        }
    }

    private fun filterList(source: ArrayList<Directory>, query: String): ArrayList<Directory> {
        return if (query.isEmpty()) {
            source
        } else {
            source.filterTo(ArrayList()) { it.name.contains(query, ignoreCase = true) }
        }
    }

    private fun filterAlbumsBySearchQuery(query: String) {
        if (!hasLoadedAlbums) {
            return
        }
        val adapter = binding.directoriesGrid.adapter as? DirectoryAdapter
        val dirsToShow = filterList(allAlbums, query)
        checkPlaceholderVisibility(dirsToShow)

        if (adapter?.dirs != dirsToShow) {
            adapter?.updateDirs(dirsToShow)
            binding.directoriesGrid.post {
                binding.directoriesGrid.scrollToPosition(0)
            }
        }
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Directory>) = with(binding) {
        directoriesEmptyPlaceholder.beVisibleIf(dirs.isEmpty())
        val hasQuery = folderSearchView.isSearchOpen && !searchEditText.text.isNullOrEmpty()
        directoriesEmptyPlaceholder.setText(
            if (hasQuery) org.fossify.commons.R.string.no_items_found else R.string.no_virtual_albums_yet
        )
        directoriesFastscroller.beVisibleIf(directoriesEmptyPlaceholder.isGone())
    }

    private fun openCreateVirtualAlbum() {
        activity.hideKeyboard(searchEditText)
        dialog?.dismiss()
        ensureBackgroundThread {
            val effectiveSource = activity.resolveEffectiveSourcePhysicalPath(sourcePath)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }
                CreateVirtualAlbumDialog(
                    activity = activity,
                    isHidden = isHiddenSource,
                    parentPhysicalPath = effectiveSource
                ) { albumId ->
                    ensureBackgroundThread {
                        val name = activity.getVirtualAlbumName(getVirtualAlbumPath(albumId)) ?: ""
                        activity.runOnUiThread { callback(albumId, name) }
                    }
                }
            }
        }
    }
}
