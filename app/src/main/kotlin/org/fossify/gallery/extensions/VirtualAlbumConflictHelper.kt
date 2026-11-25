package org.fossify.gallery.extensions

import android.app.Activity
import org.fossify.commons.dialogs.FileConflictDialog
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.SmartFileConflictDialog
import org.fossify.gallery.dialogs.SmartConflictItem
import org.fossify.gallery.helpers.CONFLICT_HANDLING_AUTO
import org.fossify.gallery.helpers.CONFLICT_HANDLING_SMART_DIALOG
import org.fossify.gallery.helpers.FolderIdentity
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.OperationNotificationCategory
import org.fossify.gallery.helpers.VirtualAlbumSyncManager
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.models.VirtualAlbumItem
import java.io.File

/**
 * Applies the app's three conflict-handling modes when adding files to a virtual album.
 * Virtual albums store references, so "overwrite" replaces the reference with the new source file;
 * "keep both" keeps two references with the same display name.
 */
object VirtualAlbumConflictHelper {

    fun add(
        activity: Activity,
        albumId: Long,
        fileDirItems: Collection<FileDirItem>,
        forceMarkAlbumHidden: Boolean = false,
        onComplete: (Int) -> Unit
    ) {
        val valid = fileDirItems
            .filter { !it.isDirectory && !it.path.startsWith(activity.recycleBinPath) }
            .distinctBy { it.path.lowercase() }
        if (valid.isEmpty()) {
            onComplete(0)
            return
        }

        val scanDialog = CryptoProgressDialog(
            activity = activity,
            max = valid.size,
            titleId = R.string.updating_virtual_album_progress,
            notificationChannelId = OperationNotificationCategory.CHANNEL_VIRTUAL_ALBUM
        )

        ensureBackgroundThread {
            val existing = activity.virtualAlbumsDB.getItems(albumId)
            val byName = existing.groupBy { it.filename.lowercase() }
            val session = FolderIdentity.CaptureSession()

            val pending = valid.map { item ->
                val conflict = byName[item.name.lowercase()]?.firstOrNull()
                val identical = if (conflict != null) {
                    val identity = MediaIdentity.ofWithFolder(activity, File(item.path), item.mediaStoreId, session)
                    if (conflict.fingerprint.isNotEmpty() && identity.fingerprint.isNotEmpty()) {
                        conflict.fingerprint == identity.fingerprint
                    } else {
                        conflict.fileSize == identity.size && conflict.lastModified == identity.lastModified
                    }
                } else {
                    false
                }
                Pending(item, conflict, identical)
            }

            val conflicting = pending.filter { it.conflict != null }
            if (conflicting.isEmpty()) {
                val added = ForegroundOperationRegistry.withOperation(activity, "virtual-album-add") {
                    scanDialog.updateProgress(0, valid.size)
                    val count = activity.addFileDirItemsToVirtualAlbum(albumId, valid, forceMarkAlbumHidden)
                    scanDialog.updateProgress(valid.size, valid.size)
                    count
                } ?: 0
                activity.runOnUiThread {
                    scanDialog.dismiss()
                    onComplete(added)
                }
                return@ensureBackgroundThread
            }

            val resolutions = HashMap<String, Int>()
            when (activity.config.conflictHandlingMode) {
                CONFLICT_HANDLING_AUTO -> {
                    pending.forEach { resolutions[it.item.path] = if (it.identical) CONFLICT_SKIP else CONFLICT_OVERWRITE }
                    apply(activity, albumId, pending, resolutions, forceMarkAlbumHidden, scanDialog, onComplete)
                }

                CONFLICT_HANDLING_SMART_DIALOG -> {
                    val identicalPaths = pending.filter { it.conflict != null && it.identical }.map { it.item.path }
                    identicalPaths.forEach { resolutions[it] = CONFLICT_SKIP }
                    val different = pending.filter { it.conflict != null && !it.identical }
                    if (different.isEmpty()) {
                        apply(activity, albumId, pending, resolutions, forceMarkAlbumHidden, scanDialog, onComplete)
                    } else {
                        activity.runOnUiThread {
                            scanDialog.dismiss()
                            askSmart(activity, different, 0, resolutions) {
                                apply(activity, albumId, pending, resolutions, forceMarkAlbumHidden, null, onComplete)
                            }
                        }
                    }
                }

                else -> activity.runOnUiThread {
                    scanDialog.dismiss()
                    askStandard(activity, conflicting, 0, resolutions) {
                        apply(activity, albumId, pending, resolutions, forceMarkAlbumHidden, null, onComplete)
                    }
                }
            }
        }
    }

    private fun askStandard(
        activity: Activity,
        items: List<Pending>,
        index: Int,
        resolutions: MutableMap<String, Int>,
        done: () -> Unit
    ) {
        if (index >= items.size || activity.isFinishing || activity.isDestroyed) {
            done()
            return
        }
        val pending = items[index]
        FileConflictDialog(
            activity = activity,
            fileDirItem = FileDirItem(
                path = pending.item.path,
                name = pending.item.name,
                isDirectory = false,
                modified = pending.item.modified,
                size = pending.item.size
            ),
            showApplyToAllCheckbox = items.size > 1
        ) { resolution, applyToAll ->
            resolutions[pending.item.path] = resolution
            if (applyToAll) {
                items.forEach { resolutions[it.item.path] = resolution }
                done()
            } else {
                askStandard(activity, items, index + 1, resolutions, done)
            }
        }
    }

    private fun askSmart(
        activity: Activity,
        items: List<Pending>,
        index: Int,
        resolutions: MutableMap<String, Int>,
        done: () -> Unit
    ) {
        if (index >= items.size || activity.isFinishing || activity.isDestroyed) {
            done()
            return
        }
        val pending = items[index]
        SmartFileConflictDialog(
            activity = activity,
            conflict = SmartConflictItem(
                resolutionKey = pending.item.path,
                displayName = pending.item.name,
                sourcePath = pending.item.path,
                existingPath = pending.conflict!!.fullPath
            ),
            currentIndex = index,
            totalCount = items.size
        ) { resolution, applyToAll ->
            resolutions[pending.item.path] = resolution
            if (applyToAll) {
                items.forEach { resolutions[it.item.path] = resolution }
                done()
            } else {
                askSmart(activity, items, index + 1, resolutions, done)
            }
        }
    }

    private fun apply(
        activity: Activity,
        albumId: Long,
        pending: List<Pending>,
        resolutions: Map<String, Int>,
        forceMarkAlbumHidden: Boolean,
        existingDialog: CryptoProgressDialog?,
        onComplete: (Int) -> Unit
    ) {
        val applyWithDialog = { dialog: CryptoProgressDialog ->
            ensureBackgroundThread {
                val accepted = ArrayList<FileDirItem>()
                val removePaths = ArrayList<String>()
                pending.forEach { p ->
                    when (resolutions[p.item.path] ?: CONFLICT_KEEP_BOTH) {
                        CONFLICT_SKIP -> Unit
                        CONFLICT_OVERWRITE -> {
                            p.conflict?.let { removePaths.add(it.fullPath) }
                            accepted.add(p.item)
                        }
                        else -> accepted.add(p.item)
                    }
                }
                val total = maxOf(accepted.size + removePaths.size, 1)
                val added = ForegroundOperationRegistry.withOperation(activity, "virtual-album-add") {
                    dialog.updateProgress(0, total)
                    if (!ForegroundOperationRegistry.isCancelled) {
                        removePaths.forEach { activity.virtualAlbumsDB.removeItemFromAlbum(albumId, it) }
                        val count = activity.addFileDirItemsToVirtualAlbum(albumId, accepted, forceMarkAlbumHidden)
                        if (removePaths.isNotEmpty()) {
                            VirtualAlbumSyncManager.saveAlbumToFile(activity, albumId)
                        }
                        dialog.updateProgress(total, total)
                        count
                    } else {
                        0
                    }
                } ?: 0
                activity.runOnUiThread {
                    dialog.dismiss()
                    onComplete(added)
                }
            }
        }

        if (existingDialog != null) {
            applyWithDialog(existingDialog)
        } else {
            activity.runOnUiThread {
                val dialog = CryptoProgressDialog(
                    activity = activity,
                    max = pending.size,
                    titleId = R.string.updating_virtual_album_progress,
                    notificationChannelId = OperationNotificationCategory.CHANNEL_VIRTUAL_ALBUM
                )
                applyWithDialog(dialog)
            }
        }
    }

    private data class Pending(val item: FileDirItem, val conflict: VirtualAlbumItem?, val identical: Boolean)
}
