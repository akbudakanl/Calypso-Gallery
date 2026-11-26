package org.fossify.gallery.helpers

import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.canManageMedia
import org.fossify.commons.extensions.createDirectorySync
import org.fossify.commons.extensions.getAndroidSAFFileItems
import org.fossify.commons.extensions.getDocumentFile
import org.fossify.commons.extensions.getDocumentSdk30
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getFileOutputStreamSync
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.isAccessibleWithSAFSdk30
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.extensions.updateReferencesForFolderRename
import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.BatchOperationInfo
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.JournalItem
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationFaults
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.operations.TransientOperationWaiter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Stream copying, duplicate detection, and source/MediaStore finalization extracted from [CopyMoveRunner].
 */

internal fun CopyMoveRunner.isDuplicatePlainInTarget(
    srcFile: File,
    desiredName: String,
    destIndex: CryptoManager.TargetPlainIndex
): Boolean {
    if (!srcFile.isFile) return false
    if (activity.config.conflictHandlingMode == CONFLICT_HANDLING_STANDARD_DIALOG) return false
    if (srcFile.absolutePath in config.precheckedIdenticalPaths) {
        identicalSkippedCount++
        return true
    }
    if (srcFile.absolutePath in config.precheckedNonIdenticalPaths) return false

    val candidates = destIndex.findCandidates(desiredName, includeCollisionVariants = true)
    if (candidates.isEmpty()) return false

    val srcLastMod = srcFile.lastModified()
    val srcSize = srcFile.length()
    var resolvedSrcDateTaken: Long? = null
    fun getSrcDateTaken(): Long {
        return resolvedSrcDateTaken ?: CryptoManager.resolvePlaintextDateTaken(
            context = activity.applicationContext,
            sourceFile = srcFile,
            fallbackLastModified = srcLastMod
        ).also { resolvedSrcDateTaken = it }
    }

    val gateByDate = activity.applicationContext.shouldGateDuplicatesByDate()
    return candidates.any { existing ->
        if (!existing.isFile || existing.length() != srcSize) return@any false
        val existingLastMod = existing.lastModified()
        if (gateByDate && !CryptoManager.areTimestampsEquivalent(srcLastMod, existingLastMod)) return@any false
        val existingTaken = CryptoManager.resolvePlaintextDateTaken(
            context = activity.applicationContext,
            sourceFile = existing,
            fallbackLastModified = existingLastMod
        )
        if (gateByDate && !CryptoManager.areMediaTimestampsEquivalent(
                srcLastMod,
                getSrcDateTaken(),
                existingLastMod,
                existingTaken
            )
        ) {
            return@any false
        }
        val identical = CryptoManager.arePlainFilesIdentical(
            srcFile,
            existing,
            fastSampling = activity.config.fastLargeDuplicateCheck
        )
        if (identical) {
            identicalSkippedCount++
            CryptoManager.applyDuplicateDateToPlainFile(
                context = activity.applicationContext,
                target = existing,
                lastModified = srcLastMod,
                dateTaken = getSrcDateTaken()
            )
        }
        identical
    }
}

internal fun CopyMoveRunner.finalizeTransferredSourcesAndMediaStore(cancelled: Boolean) {
    if (deletedDirectSourcePaths.isNotEmpty()) {
        activity.batchDeleteFromMediaStore(deletedDirectSourcePaths)
    }

    if (!cancelled && pendingSdk30Deletes.isNotEmpty()) {
        val ctxByPath = pendingSdk30Deletes.associate { it.first.path to it.second }
        val itemMap = pendingSdk30Deletes.associate { it.first.path to it.first }
        val sdkRes = DeleteOperationEngine.deleteFilesPermanentlyChunked(
            sourcePaths = pendingSdk30Deletes.map { it.first.path },
            isCancelled = { ForegroundOperationRegistry.isCancelled },
            deleteChunkOriginals = { chunkPaths -> activity.deleteChunkWithSdk30Sync(chunkPaths, itemMap) },
            doesOriginalExist = { path -> activity.doesPlainFileExistSync(path) },
            onItemDeleted = { path ->
                ctxByPath[path]?.let { OperationJournal.markSourceDeleted(it) }
            },
            onItemProgress = {}
        )
        if (sdkRes.deletedPaths.isNotEmpty()) {
            activity.batchDeleteFromMediaStore(sdkRes.deletedPaths)
        }
    }

    // One bulk MediaScanner pass for every transferred file (rename fast paths plus
    // stream copies) instead of one request per copied file. A single pass changes the
    // MediaStore ids once, so the album grid is reloaded once instead of N times.
    val allPathsToRescan = ArrayList<String>(movedPathsToRescan.size + copiedPathsToRescan.size)
    allPathsToRescan.addAll(movedPathsToRescan)
    allPathsToRescan.addAll(copiedPathsToRescan.map { it.path })
    if (allPathsToRescan.isNotEmpty()) {
        activity.rescanPaths(allPathsToRescan) {
            if (activity.baseConfig.keepLastModified) {
                copiedPathsToRescan.forEach {
                    updateLastModifiedValues(activity, it.path, it.lastModified, it.dateTaken)
                }
            }
        }
    }
}

internal fun CopyMoveRunner.computeTotalBytes(items: ArrayList<FileDirItem>): Long {
    val destDir = File(config.destination)
    val destIndex = getDestIndex(destDir)
    var bytes = 0L
    for (item in items) {
        if (!item.isDirectory && item.path in config.precheckedIdenticalPaths) {
            continue
        }
        if (item.size == 0L) {
            item.size = item.getProperSize(activity.applicationContext, config.copyHidden)
        }
        val targetPath = File(destDir, item.name).absolutePath
        val exists = destIndex.containsName(item.name) || activity.getDoesFilePathExist(targetPath)
        val resolution = config.conflictResolutions[targetPath] ?: CONFLICT_KEEP_BOTH
        if (resolution != CONFLICT_SKIP || !exists) {
            bytes += item.size
        }
    }
    return bytes
}

internal fun streamCopyWithProgress(
    inputStream: InputStream?,
    outputStream: OutputStream?,
    digest: MessageDigest? = null,
    onChunkCopied: (Int) -> Unit
): Long {
    if (inputStream == null || outputStream == null) {
        return 0L
    }
    var bytesCopied = 0L
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var bytesRead = inputStream.read(buffer)
    while (bytesRead >= 0) {
        if (ForegroundOperationRegistry.isCancelled) {
            throw CopyMoveCancelledException()
        }
        outputStream.write(buffer, 0, bytesRead)
        digest?.update(buffer, 0, bytesRead)
        bytesCopied += bytesRead
        onChunkCopied(bytesRead)
        bytesRead = inputStream.read(buffer)
    }
    outputStream.flush()
    return bytesCopied
}

internal fun closeQuietly(stream: Closeable?) {
    try {
        stream?.close()
    } catch (_: Exception) {
    }
}

internal fun updateLastModifiedValues(
    activity: BaseSimpleActivity,
    destPath: String,
    lastModified: Long,
    dateTaken: Long
) {
    if (lastModified > 0L) {
        File(destPath).setLastModified(lastModified)
    }
    CryptoManager.persistDecryptedDateTaken(
        context = activity.applicationContext,
        outFile = File(destPath),
        dateTaken = dateTaken,
        lastModified = lastModified
    )
}
