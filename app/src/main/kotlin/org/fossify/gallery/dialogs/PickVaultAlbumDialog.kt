package org.fossify.gallery.dialogs

import org.fossify.gallery.extensions.secureVaultWindow

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.VaultPhysicalAlbum
import java.io.File
import org.fossify.gallery.helpers.getOrCreateDefaultHiddenAlbum
import org.fossify.gallery.helpers.getVaultDestinationAlbums

class PickVaultAlbumDialog(
    val activity: Activity,
    val onCancel: (() -> Unit)? = null,
    val callback: (targetFolder: File, albumName: String) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var handled = false

    init {
        ensureBackgroundThread {
            CryptoManager.getOrCreateDefaultHiddenAlbum(activity)
            val albums = CryptoManager.getVaultDestinationAlbums(activity)

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    onCancel?.invoke()
                    return@runOnUiThread
                }
                show(albums)
            }
        }
    }

    private fun show(albums: List<VaultPhysicalAlbum>) {
        val palette = AppearanceManager.resolve(activity, activity.config)
        val newAlbumLabel = "+ " + activity.getString(R.string.create_new_hidden_album)
        val labels = albums.map { "${it.name} (${it.itemCount})" } + newAlbumLabel

        val listView = ListView(activity).apply {
            adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, labels) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    (view as? TextView)?.setTextColor(palette.text)
                    return view
                }
            }
            divider = ColorDrawable(ColorUtils.setAlphaComponent(palette.text, 0x26))
            dividerHeight = 1
            setOnItemClickListener { _, _, position, _ ->
                handled = true
                dialog?.dismiss()
                if (position == albums.size) {
                    CreateEncryptedAlbumDialog(activity, onCancel = onCancel, callback = callback)
                } else {
                    val selected = albums[position]
                    callback(selected.folder, selected.name)
                }
            }
        }

        activity.getAlertDialogBuilder()
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
                activity.setupDialogStuff(listView, this, R.string.select_hidden_album) { alertDialog -> alertDialog.secureVaultWindow()
                    dialog = alertDialog
                }
            }
    }
}
