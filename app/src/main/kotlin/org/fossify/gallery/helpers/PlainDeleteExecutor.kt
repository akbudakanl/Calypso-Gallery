package org.fossify.gallery.helpers

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.deleteAndroidSAFDirectory
import org.fossify.commons.extensions.getAndroidSAFDocument
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDocumentSdk30
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileUrisFromFileDirItems
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getSomeDocumentFile
import org.fossify.commons.extensions.hasProperStoredDocumentUriSdk30
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.tryFastDocumentDelete
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.deleteDBPath
import org.fossify.gallery.extensions.deleteDBPaths
import org.fossify.gallery.extensions.deleteVirtualAlbumItemFromAllAlbums
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.isDownloadsFolder
import org.fossify.gallery.extensions.isPathHiddenOrEncrypted
import org.fossify.gallery.extensions.isVirtualAlbumPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.models.Medium
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong


/**
 * Plaintext and SDK 30 batch deletion helpers extracted from [DeleteOperationHelper].
 */
internal fun BaseSimpleActivity.processPlainBatches(
    partition: DeletePartition,
    ctx: DeleteExecutionContext,
    alreadyCancelled: Boolean
): DeleteBatchResult {
    if (alreadyCancelled) {
        return DeleteBatchResult(emptyList(), emptyList(), true)
    }

    val deletedPaths = ArrayList<String>()
    val failedPaths = ArrayList<String>()
    var cancelled = false
    val directMap = partition.directPlainItems.associateBy { it.path }
    val sdk30FallbackItems = ArrayList<FileDirItem>()

    if (partition.directPlainItems.isNotEmpty()) {
        val directRes = runDirectPlainBatch(partition.directPlainItems, directMap, sdk30FallbackItems, ctx)
        deletedPaths.addAll(directRes.deletedPaths)
        failedPaths.addAll(directRes.failedPaths)
        cancelled = directRes.cancelled
    }

    val allSdk30Items = partition.sdk30PlainItems + sdk30FallbackItems
    if (!cancelled && allSdk30Items.isNotEmpty()) {
        val sdk30Res = runSdk30PlainBatch(allSdk30Items, ctx)
        deletedPaths.addAll(sdk30Res.deletedPaths)
        failedPaths.addAll(sdk30Res.failedPaths)
        cancelled = sdk30Res.cancelled
    }

    return DeleteBatchResult(deletedPaths, failedPaths, cancelled)
}

private fun BaseSimpleActivity.runDirectPlainBatch(
    items: List<FileDirItem>,
    itemMap: Map<String, FileDirItem>,
    sdk30FallbackItems: MutableList<FileDirItem>,
    ctx: DeleteExecutionContext
): DeleteBatchResult {
    val recycleBinRoot = File(recycleBinPath)
    val deletedPaths = ArrayList<String>(items.size)
    val failedPaths = ArrayList<String>()
    var cancelled = false
    val deletedTs = System.currentTimeMillis()

    for (item in items) {
        if (ForegroundOperationRegistry.isCancelled) {
            cancelled = true
            break
        }
        val ok = if (ctx.useRecycleBin) {
            DeleteOperationEngine.moveFileToRecycleBinTransactionally(
                sourcePath = item.path,
                destFile = File(recycleBinPath, item.path),
                recycleBinRoot = recycleBinRoot,
                copyToBin = { destFile -> copyPlainToRecycleBinSync(item, destFile) },
                deleteOriginal = { path -> deletePlainFileDirectlySync(itemMap[path] ?: item) },
                doesOriginalExist = { path -> doesPlainFileExistSync(path) },
                commitDb = { path, binFile -> commitPlainRecycleBinDb(path, binFile, deletedTs) }
            )
        } else {
            val deleted = deletePlainFileDirectlySync(item)
            deleted && !doesPlainFileExistSync(item.path)
        }

        if (ok) {
            deletedPaths.add(item.path)
            ctx.tracker.advance(1)
        } else if (shouldRetryWithSdk30(item.path)) {
            sdk30FallbackItems.add(item)
        } else {
            failedPaths.add(item.path)
            ctx.tracker.advance(1)
        }
    }
    return DeleteBatchResult(deletedPaths, failedPaths, cancelled)
}

private fun BaseSimpleActivity.shouldRetryWithSdk30(path: String): Boolean {
    return isRPlus() &&
        !isPathOnOTG(path) &&
        !isRestrictedSAFOnlyRoot(path) &&
        !isPathHiddenOrEncrypted(path) &&
        !path.startsWith(recycleBinPath)
}

private fun BaseSimpleActivity.runSdk30PlainBatch(
    items: List<FileDirItem>,
    ctx: DeleteExecutionContext
): DeleteBatchResult {
    val itemMap = items.associateBy { it.path }
    val recycleBinRoot = File(recycleBinPath)
    val deletedTs = System.currentTimeMillis()

    return if (ctx.useRecycleBin) {
        DeleteOperationEngine.moveBatchToRecycleBinChunked(
            sourcePaths = items.map { it.path },
            recycleBinRoot = recycleBinRoot,
            destFileFor = { path -> File(recycleBinPath, path) },
            isCancelled = { ForegroundOperationRegistry.isCancelled },
            copyToBin = { path, destFile ->
                val item = itemMap[path] ?: FileDirItem(path, path.getFilenameFromPath())
                copyPlainToRecycleBinSync(item, destFile)
            },
            deleteChunkOriginals = { chunkPaths -> deleteChunkWithSdk30Sync(chunkPaths, itemMap) },
            doesOriginalExist = { path -> doesPlainFileExistSync(path) },
            commitDb = { path, binFile -> commitPlainRecycleBinDb(path, binFile, deletedTs) },
            onItemProgress = { delta -> ctx.tracker.advance(delta) }
        )
    } else {
        DeleteOperationEngine.deleteFilesPermanentlyChunked(
            sourcePaths = items.map { it.path },
            isCancelled = { ForegroundOperationRegistry.isCancelled },
            deleteChunkOriginals = { chunkPaths -> deleteChunkWithSdk30Sync(chunkPaths, itemMap) },
            doesOriginalExist = { path -> doesPlainFileExistSync(path) },
            onItemDeleted = {},
            onItemProgress = { delta -> ctx.tracker.advance(delta) }
        )
    }
}

internal fun BaseSimpleActivity.finalizePlainMetadata(
    deletedPlainPaths: List<String>,
    deletedNormalBinPaths: List<String>,
    ctx: DeleteExecutionContext
) {
    val nonBinDeleted = deletedPlainPaths.filterNot { it.startsWith(recycleBinPath) }
    if (nonBinDeleted.isNotEmpty()) {
        batchDeleteFromMediaStore(nonBinDeleted)
        if (!ctx.useRecycleBin) {
            deleteDBPaths(nonBinDeleted)
        }
        val isVirtualScope = ctx.currentDirectoryPath.isVirtualAlbumPath()
        for (path in nonBinDeleted) {
            if (isPathHiddenOrEncrypted(path) || isVirtualScope) {
                if (ctx.useRecycleBin) {
                    runCatching { mediaDB.deleteMediumPath(path) }
                }
                CryptoManager.purgeTracesForFile(this, path)
            }
        }
        MediaIndexCoordinator.markPathsDirty(
            filePaths = nonBinDeleted,
            includeRecycleBin = ctx.useRecycleBin
        )
    }

    if (deletedNormalBinPaths.isNotEmpty()) {
        deleteDBPaths(deletedNormalBinPaths)
        MediaIndexCoordinator.markPathsDirty(
            filePaths = deletedNormalBinPaths,
            includeRecycleBin = true
        )
    }
}

private fun BaseSimpleActivity.copyPlainToRecycleBinSync(item: FileDirItem, destFile: File): Boolean {
    if (isPathOnOTG(item.path)) {
        val doc = getSomeDocumentFile(item.path) ?: return false
        val expectedSize = if (item.size > 0L) item.size else doc.length()
        return DeleteOperationEngine.copyToRecycleBinAtomically(
            expectedSize = expectedSize,
            lastModified = item.modified,
            destFile = destFile,
            keepLastModified = config.keepLastModified
        ) {
            runCatching { applicationContext.contentResolver.openInputStream(doc.uri) }.getOrNull()
        }
    }

    val srcFile = File(item.path)
    val expectedSize = if (srcFile.exists()) srcFile.length() else -1L
    val lastModified = if (srcFile.exists()) srcFile.lastModified() else item.modified
    return DeleteOperationEngine.copyToRecycleBinAtomically(
        expectedSize = expectedSize,
        lastModified = lastModified,
        destFile = destFile,
        keepLastModified = config.keepLastModified
    ) {
        runCatching { getFileInputStreamSync(item.path) }.getOrNull()
    }
}

internal fun BaseSimpleActivity.deletePlainFileDirectlySync(
    item: FileDirItem,
    allowDeleteFolder: Boolean = false
): Boolean {
    val path = item.path
    if (isRestrictedSAFOnlyRoot(path)) {
        val docDeleted = runCatching { getAndroidSAFDocument(path)?.delete() == true }.getOrDefault(false)
        if (docDeleted) return true
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        deleteAndroidSAFDirectory(path, allowDeleteFolder) {
            ok.set(it)
            latch.countDown()
        }
        runCatching { latch.await() }
        return ok.get()
    }

    if (isPathOnOTG(path)) {
        return runCatching { getSomeDocumentFile(path)?.delete() == true }.getOrDefault(false)
    }

    val file = File(path)
    var deleted = (!file.exists() && file.length() == 0L) || runCatching { file.delete() }.getOrDefault(false)
    if (!deleted && allowDeleteFolder && file.isDirectory) {
        deleted = runCatching { file.deleteRecursively() }.getOrDefault(false)
    }
    if (!deleted && needsStupidWritePermissions(path)) {
        deleted = runCatching {
            tryFastDocumentDelete(path, allowDeleteFolder) || getDocumentFile(path)?.delete() == true
        }.getOrDefault(false)
    }
    if (!deleted && isAccessibleWithSAFSdk30(path)) {
        deleted = runCatching { getDocumentSdk30(path)?.delete() == true }.getOrDefault(false)
    }
    return deleted
}

internal fun BaseSimpleActivity.doesPlainFileExistSync(path: String): Boolean {
    return when {
        isPathOnOTG(path) -> runCatching { getSomeDocumentFile(path)?.exists() == true }.getOrDefault(false)
        isRestrictedSAFOnlyRoot(path) ->
            runCatching { getAndroidSAFDocument(path)?.exists() == true }.getOrDefault(false)
        else -> File(path).exists()
    }
}

private fun BaseSimpleActivity.commitPlainRecycleBinDb(sourcePath: String, binFile: File, deletedTs: Long) {
    val recycledKey = "$RECYCLE_BIN$sourcePath"
    val updated = runCatching { mediaDB.updateDeleted(recycledKey, deletedTs, sourcePath) }.getOrDefault(0)
    if (updated > 0) {
        return
    }
    val medium = Medium(
        id = null,
        name = sourcePath.getFilenameFromPath(),
        path = recycledKey,
        parentPath = sourcePath.getParentPath(),
        modified = binFile.lastModified(),
        taken = binFile.lastModified(),
        size = binFile.length(),
        type = resolvePlainMediaType(sourcePath),
        videoDuration = 0,
        isFavorite = false,
        deletedTS = deletedTs,
        mediaStoreId = 0L
    )
    runCatching { mediaDB.insert(medium) }
}

internal fun resolvePlainMediaType(path: String): Int = when {
    path.isVideoFast() -> TYPE_VIDEOS
    path.isGif() -> TYPE_GIFS
    path.isRawFast() -> TYPE_RAWS
    path.isSvg() -> TYPE_SVGS
    else -> TYPE_IMAGES
}

internal fun BaseSimpleActivity.deleteChunkWithSdk30Sync(
    chunkPaths: List<String>,
    itemMap: Map<String, FileDirItem>
): Boolean {
    val host = this as? SimpleActivity ?: return false
    val uris = resolveMediaStoreUrisForPaths(chunkPaths, itemMap)
    if (uris.isEmpty()) {
        return false
    }
    val latch = CountDownLatch(1)
    val result = AtomicBoolean(false)
    host.launchSdk30DeleteRequest(uris) { granted ->
        result.set(granted)
        latch.countDown()
    }
    return try {
        latch.await()
        result.get()
    } catch (_: InterruptedException) {
        false
    }
}

private fun BaseSimpleActivity.resolveMediaStoreUrisForPaths(
    paths: List<String>,
    itemMap: Map<String, FileDirItem>
): List<Uri> {
    val uriByPath = LinkedHashMap<String, Uri>(paths.size)
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATA,
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )
    val filesUri = MediaStore.Files.getContentUri("external")

    for (chunk in paths.chunked(MEDIASTORE_DELETE_CHUNK_SIZE)) {
        val placeholders = chunk.joinToString(",") { "?" }
        val selection = "${MediaStore.MediaColumns.DATA} IN ($placeholders)"
        runCatching {
            contentResolver.query(filesUri, projection, selection, chunk.toTypedArray(), null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val typeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                while (cursor.moveToNext()) {
                    val data = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                    val mediaType = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                    if (!data.isNullOrEmpty() && id >= 0L) {
                        val contentUri = when (mediaType) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> filesUri
                        }
                        uriByPath[data] = ContentUris.withAppendedId(contentUri, id)
                    }
                }
            }
        }
    }

    val unresolved = paths.mapNotNull { path ->
        if (uriByPath.containsKey(path)) null else (itemMap[path] ?: FileDirItem(path, path.getFilenameFromPath()))
    }
    if (unresolved.isNotEmpty()) {
        val fallbackUris = runCatching { getFileUrisFromFileDirItems(unresolved) }.getOrDefault(emptyList())
        val result = ArrayList<Uri>(uriByPath.size + fallbackUris.size)
        result.addAll(uriByPath.values)
        result.addAll(fallbackUris)
        return result
    }
    return uriByPath.values.toList()
}

internal fun BaseSimpleActivity.batchDeleteFromMediaStore(paths: List<String>) {
    val filesUri = MediaStore.Files.getContentUri("external")
    for (chunk in paths.chunked(MEDIASTORE_DELETE_CHUNK_SIZE)) {
        val placeholders = chunk.joinToString(",") { "?" }
        val where = "${MediaStore.MediaColumns.DATA} IN ($placeholders)"
        runCatching {
            contentResolver.delete(filesUri, where, chunk.toTypedArray())
        }
    }
}
