package org.fossify.gallery.extensions

import android.app.Activity
import android.content.Intent
import android.os.Looper
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.deleteFile
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileOutputStreamSync
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getPicturesDirectoryPath
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.helpers.DELETE_UI_PROGRESS_INTERVAL_MS
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.DeleteOperationEngine
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.OperationNotificationCategory
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.models.Medium
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

fun BaseSimpleActivity.tryDeleteFileDirItem(
    fileDirItem: FileDirItem, allowDeleteFolder: Boolean = false, deleteFromDatabase: Boolean,
    callback: ((wasSuccess: Boolean) -> Unit)? = null
) {
    deleteFile(fileDirItem, allowDeleteFolder, isDeletingMultipleFiles = false) {
        if (it) {
            MediaIndexCoordinator.markPathsDirty(listOf(fileDirItem.path))
        }
        if (deleteFromDatabase) {
            ensureBackgroundThread {
                deleteDBPath(fileDirItem.path)
                runOnUiThread {
                    callback?.invoke(it)
                }
            }
        } else {
            callback?.invoke(it)
        }
    }
}

fun BaseSimpleActivity.restoreRecycleBinPath(path: String, callback: () -> Unit) {
    restoreRecycleBinPaths(arrayListOf(path), callback)
}

fun BaseSimpleActivity.restoreRecycleBinPaths(paths: ArrayList<String>, callback: () -> Unit) {
    if (paths.isEmpty()) {
        callback()
        return
    }
    val progressDialog = createRecycleProgressDialog(
        titleId = R.string.restoring_files_progress,
        max = paths.size
    )
    ensureNotificationPermissionThenRun {
        if (isFinishing || isDestroyed) {
            progressDialog?.dismiss()
            callback()
            return@ensureNotificationPermissionThenRun
        }
        ensureBackgroundThread {
            ForegroundOperationRegistry.withOperation(this, "recycle-restore") {
                val newPaths = ArrayList<String>()
                val restoredPairs = ArrayList<Pair<String, String>>()
                val binRoot = File(recycleBinPath)
                var shownRestoringToPictures = false
                var lastUpdateMs = 0L
                val total = paths.size
                progressDialog?.updateProgress(0, total)

                for ((index, source) in paths.withIndex()) {
                    if (ForegroundOperationRegistry.isCancelled) {
                        break
                    }
                    val sourceFile = File(source)
                    if (!sourceFile.exists()) {
                        deleteDBPath(source)
                        continue
                    }

                    val origPath = source.removePrefix(recycleBinPath)
                    var destination = origPath

                    val destinationParent = destination.getParentPath()
                    if (isRestrictedWithSAFSdk30(destinationParent) && !isInDownloadDir(destinationParent)) {
                        // if the file is not writeable on SDK30+, change it to Pictures
                        val picturesDirectory = getPicturesDirectoryPath(destination)
                        destination = File(picturesDirectory, destination.getFilenameFromPath()).path
                        if (!shownRestoringToPictures) {
                            toast(getString(R.string.restore_to_path, humanizePath(picturesDirectory)))
                            shownRestoringToPictures = true
                        }
                    }

                    val lastModified = sourceFile.lastModified()

                    val isShowingSAF = handleSAFDialog(destination) {}
                    if (isShowingSAF) {
                        runOnUiThread { progressDialog?.dismiss() }
                        return@withOperation
                    }

                    val isShowingSAFSdk30 = handleSAFDialogSdk30(destination) {}
                    if (isShowingSAFSdk30) {
                        runOnUiThread { progressDialog?.dismiss() }
                        return@withOperation
                    }

                    if (getDoesFilePathExist(destination)) {
                        val newFile = getAlternativeFile(File(destination))
                        destination = newFile.path
                    }

                    val expectedSize = sourceFile.length()
                    val restored = DeleteOperationEngine.restoreFileFromRecycleBinAtomically(
                        binFile = sourceFile,
                        recycleBinRoot = binRoot,
                        copyToDest = {
                            copyRecycleFileToDestination(source, destination, expectedSize, lastModified)
                        },
                        verifyDestExists = { getDoesFilePathExist(destination) },
                        commitDb = {
                            mediaDB.updateDeleted(
                                destination.removePrefix(recycleBinPath),
                                0,
                                "$RECYCLE_BIN${source.removePrefix(recycleBinPath)}"
                            )
                            restoredPairs.add(origPath to destination)
                            newPaths.add(destination)
                        }
                    )
                    if (!restored) {
                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                    }

                    val current = index + 1
                    val now = System.currentTimeMillis()
                    if (current == total || now - lastUpdateMs >= DELETE_UI_PROGRESS_INTERVAL_MS) {
                        lastUpdateMs = now
                        progressDialog?.updateProgress(current, total)
                    }
                }

                if (restoredPairs.isNotEmpty()) {
                    restoreReferencesAfterRecycleBinRestore(restoredPairs)
                }
                if (newPaths.isNotEmpty()) {
                    MediaIndexCoordinator.markPathsDirty(newPaths, includeRecycleBin = true)
                }

                runOnUiThread {
                    progressDialog?.dismiss()
                    callback()
                }

                if (newPaths.isNotEmpty()) {
                    rescanPaths(newPaths) {
                        fixDateTaken(newPaths, false)
                    }
                }
            } ?: runOnUiThread {
                progressDialog?.dismiss()
                callback()
            }
        }
    }
}

private fun BaseSimpleActivity.copyRecycleFileToDestination(
    source: String,
    destination: String,
    expectedSize: Long,
    lastModified: Long
): Boolean {
    var inputStream: InputStream? = null
    var out: OutputStream? = null
    return try {
        out = getFileOutputStreamSync(destination, source.getMimeType())
        inputStream = getFileInputStreamSync(source)
        if (out == null || inputStream == null) {
            return false
        }

        var copiedSize = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytes = inputStream.read(buffer)
        while (bytes >= 0) {
            out.write(buffer, 0, bytes)
            copiedSize += bytes
            bytes = inputStream.read(buffer)
        }
        out.flush()

        if (expectedSize != copiedSize) {
            return false
        }
        if (config.keepLastModified && lastModified != 0L) {
            File(destination).setLastModified(lastModified)
        }
        true
    } catch (e: Exception) {
        showErrorToast(e)
        false
    } finally {
        runCatching { inputStream?.close() }
        runCatching { out?.close() }
    }
}

fun BaseSimpleActivity.emptyTheRecycleBin(callback: ((emptied: Boolean) -> Unit)? = null) {
    val progressDialog = createRecycleProgressDialog(titleId = R.string.emptying_recycle_bin_progress, max = 0)
    ensureNotificationPermissionThenRun {
        if (isFinishing || isDestroyed) {
            progressDialog?.dismiss()
            callback?.invoke(false)
            return@ensureNotificationPermissionThenRun
        }
        ensureBackgroundThread {
            var cancelled = false
            var allSucceeded = true
            val acquired = ForegroundOperationRegistry.withOperation(this, "recycle-empty") {
                try {
                    val deletedMedia = runCatching { mediaDB.getDeletedMedia() }.getOrDefault(emptyList())
                    val binRoot = File(recycleBinPath)
                    val targetFiles = collectRecycleBinFilesToEmpty(binRoot, deletedMedia)
                    val total = targetFiles.size
                    progressDialog?.updateProgress(0, total)

                    var lastUpdateMs = 0L
                    val deletedOrigPaths = ArrayList<String>(deletedMedia.size)
                    val dbKeyByFilePath = HashMap<String, String>(deletedMedia.size)
                    for (medium in deletedMedia) {
                        val rel = medium.path.removePrefix(RECYCLE_BIN)
                        val diskFile = File(binRoot, rel)
                        dbKeyByFilePath[diskFile.absolutePath] = rel
                    }

                    for ((index, file) in targetFiles.withIndex()) {
                        if (ForegroundOperationRegistry.isCancelled) {
                            cancelled = true
                            break
                        }
                        val deleted = !file.exists() || runCatching { file.delete() }.getOrDefault(false)
                        if (deleted && !file.exists()) {
                            DeleteOperationEngine.cleanupEmptyRecycleBinParents(file.parentFile, binRoot)
                            val rel = dbKeyByFilePath[file.absolutePath]
                                ?: file.absolutePath.removePrefix(recycleBinPath)
                            deletedOrigPaths.add(rel)
                        } else {
                            allSucceeded = false
                        }
                        val current = index + 1
                        val now = System.currentTimeMillis()
                        if (current == total || now - lastUpdateMs >= DELETE_UI_PROGRESS_INTERVAL_MS) {
                            lastUpdateMs = now
                            progressDialog?.updateProgress(current, total)
                        }
                    }

                    if (deletedOrigPaths.isNotEmpty()) {
                        deleteDBPaths(deletedOrigPaths)
                    }
                    if (!cancelled && allSucceeded) {
                        mediaDB.clearRecycleBin()
                        directoryDB.deleteRecycleBin()
                    }
                    MediaIndexCoordinator.markFoldersDirty(listOf(RECYCLE_BIN))
                } catch (_: Exception) {
                    allSucceeded = false
                }
            } != null

            val emptied = acquired && !cancelled && allSucceeded
            runOnUiThread {
                progressDialog?.dismiss()
                if (emptied) {
                    toast(org.fossify.commons.R.string.recycle_bin_emptied)
                } else if (!cancelled) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                callback?.invoke(emptied)
            }
        }
    }
}

private fun BaseSimpleActivity.createRecycleProgressDialog(titleId: Int, max: Int): CryptoProgressDialog? {
    if (isFinishing || isDestroyed) return null
    if (Looper.myLooper() == Looper.getMainLooper()) {
        return CryptoProgressDialog(
            activity = this,
            max = max,
            titleId = titleId,
            notificationChannelId = OperationNotificationCategory.CHANNEL_DELETE
        )
    }
    val ref = AtomicReference<CryptoProgressDialog?>()
    val latch = CountDownLatch(1)
    runOnUiThread {
        if (!isFinishing && !isDestroyed) {
            ref.set(
                CryptoProgressDialog(
                    activity = this,
                    max = max,
                    titleId = titleId,
                    notificationChannelId = OperationNotificationCategory.CHANNEL_DELETE
                )
            )
        }
        latch.countDown()
    }
    runCatching { latch.await() }
    return ref.get()
}

private fun collectRecycleBinFilesToEmpty(
    binRoot: File,
    deletedMedia: List<Medium>
): List<File> {
    val filesByPath = LinkedHashMap<String, File>()
    for (medium in deletedMedia) {
        val diskFile = File(binRoot, medium.path.removePrefix(RECYCLE_BIN))
        filesByPath[diskFile.absolutePath] = diskFile
    }
    for (storageDirName in listOf("storage", "mnt")) {
        val subRoot = File(binRoot, storageDirName)
        if (subRoot.exists() && subRoot.isDirectory) {
            subRoot.walkTopDown()
                .filter { it.isFile }
                .forEach { file -> filesByPath.putIfAbsent(file.absolutePath, file) }
        }
    }
    return filesByPath.values.toList()
}

fun BaseSimpleActivity.emptyAndDisableTheRecycleBin(callback: () -> Unit) {
    emptyTheRecycleBin { emptied ->
        if (emptied) {
            config.useRecycleBin = false
            callback()
        }
    }
}

fun BaseSimpleActivity.showRecycleBinEmptyingDialog(callback: () -> Unit) {
    ConfirmationDialog(
        this,
        "",
        org.fossify.commons.R.string.empty_recycle_bin_confirmation,
        org.fossify.commons.R.string.yes,
        org.fossify.commons.R.string.no
    ) {
        callback()
    }
}

fun BaseSimpleActivity.showRestoreConfirmationDialog(count: Int, callback: () -> Unit) {
    ConfirmationDialog(
        activity = this,
        message = resources.getQuantityString(R.plurals.restore_confirmation, count, count),
        positive = org.fossify.commons.R.string.yes,
        negative = org.fossify.commons.R.string.no
    ) {
        callback()
    }
}

fun Activity.openRecycleBin() {
    Intent(this, MediaActivity::class.java).apply {
        putExtra(DIRECTORY, RECYCLE_BIN)
        startActivity(this)
    }
}
