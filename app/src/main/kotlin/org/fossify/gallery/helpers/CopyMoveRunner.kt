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

internal class CopyMoveRunner(
    internal val activity: BaseSimpleActivity,
    internal val config: CopyMoveConfig,
    private val progressDialog: CryptoProgressDialog
) {
    private val transferredFiles = ArrayList<FileDirItem>()
    internal val pendingSdk30Deletes = ArrayList<Pair<FileDirItem, OperationContext>>()
    internal val deletedDirectSourcePaths = ArrayList<String>()
    internal val movedPathsToRescan = ArrayList<String>()
    // Stream-copied files are collected here and indexed in one bulk pass at the end instead of
    // firing one MediaScanner request per file. Each request used to change the MediaStore ids and
    // trigger a full album re-scan via MainActivity.checkLastMediaChanged().
    internal val copiedPathsToRescan = ArrayList<PendingRescan>()
    private val documents = LinkedHashMap<String, DocumentFile?>()
    private val destIndexMap = HashMap<String, CryptoManager.TargetPlainIndex>()

    private var totalItems = 0
    private var processedItems = 0
    /** Silently skipped files whose name, bytes and timestamps all match at the destination. */
    internal var identicalSkippedCount = 0
    private var totalBytes = 0L
    private var transferredBytes = 0L
    private var lastUiUpdateMs = 0L

    /** ADR 0003: one journal context per batch, one item index per physical write. */
    private var operation: OperationContext = OperationContext.NONE
    private var nextItemIndex = 0

    internal fun getDestIndex(dir: File): CryptoManager.TargetPlainIndex {
        return destIndexMap.getOrPut(dir.absolutePath) {
            val preExistingPaths = dir.listFiles()?.mapTo(HashSet()) { it.absolutePath } ?: emptySet()
            CryptoManager.TargetPlainIndex(dir, preExistingPaths)
        }
    }


    fun execute(items: ArrayList<FileDirItem>): CopyMoveResult {
        totalItems = items.size
        var expectedCount = items.size
        var skippedConflictCount = 0
        totalBytes = computeTotalBytes(items)

        operation = OperationContext(
            operationId = OperationJournal.newOperationId(),
            kind = if (config.isCopyOperation) OperationJournal.KIND_COPY else OperationJournal.KIND_MOVE,
            batch = BatchOperationInfo(
                destRoot = config.destination,
                peakBytes = totalBytes,
                isMove = !config.isCopyOperation,
                wipeSource = false,
                deleteSource = !config.isCopyOperation
            )
        )
        val destDir = File(config.destination)
        nextItemIndex = items.size
        OperationJournal.beginBatch(
            operation,
            items.map {
                val targetPath = File(destDir, it.name).absolutePath
                val resolution = config.conflictResolutions[targetPath] ?: CONFLICT_KEEP_BOTH
                JournalItem(
                    sourcePath = it.path,
                    destPath = targetPath,
                    sourceSize = it.size,
                    resolvedDestName = it.name,
                    kind = operation.kind,
                    conflictResolution = resolution.toString()
                )
            }
        )

        var cancelled = false
        val succeeded = try {
            for ((index, item) in items.withIndex()) {
                if (ForegroundOperationRegistry.isCancelled) {
                    cancelled = true
                    break
                }
                val itemCtx = OperationContext(operation.operationId, index, operation.kind, operation.batch)
                when (val outcome = resolveTopLevelDestination(item)) {
                    TopLevelResolution.SkippedConflict -> {
                        OperationJournal.markSkipped(itemCtx)
                        expectedCount--
                        skippedConflictCount++
                        processedItems++
                        publishProgress(forceUi = true, filename = item.name)
                    }
                    is TopLevelResolution.Proceed -> {
                        publishProgress(forceUi = true, filename = item.name)
                        transferTopLevelItem(item, outcome.destItem, topLevelIndex = index)
                        processedItems++
                        publishProgress(forceUi = true, filename = item.name)
                        if (OperationFaults.abortAfterItem(operation, processedItems - 1)) {
                            throw IOException("Fault injection: simulated process death after item ${processedItems - 1}")
                        }
                    }
                }
            }

            finalizeTransferredSourcesAndMediaStore(cancelled)
            operation.operationId?.let { OperationJournal.clearAfterCompletionOrCancel(it) }
            !cancelled
        } catch (e: CopyMoveCancelledException) {
            cancelled = true
            finalizeTransferredSourcesAndMediaStore(cancelled = true)
            operation.operationId?.let { OperationJournal.clearAfterCompletionOrCancel(it) }
            false
        } catch (e: Exception) {
            if (OperationFaults.isSimulatedAbort(e)) {
                throw e
            }
            activity.showErrorToast(e)
            false
        }

        return CopyMoveResult(
            succeeded = succeeded,
            cancelled = cancelled,
            transferredCount = transferredFiles.size,
            expectedCount = expectedCount,
            skippedConflictCount = skippedConflictCount,
            identicalSkippedCount = identicalSkippedCount
        )
    }


    private fun resolveTopLevelDestination(item: FileDirItem): TopLevelResolution {
        val destDir = File(config.destination)
        val targetPath = File(destDir, item.name).absolutePath
        var destItem = FileDirItem(targetPath, item.name, item.isDirectory)

        if (item.isDirectory) {
            return TopLevelResolution.Proceed(destItem)
        }

        val destIndex = getDestIndex(destDir)
        val srcFile = File(item.path)
        if (isDuplicatePlainInTarget(srcFile, item.name, destIndex)) {
            return TopLevelResolution.SkippedConflict
        }

        val existingExact = destIndex.findExact(item.name)
        if (existingExact == null && !activity.getDoesFilePathExist(targetPath)) {
            destIndex.recordFile(File(targetPath))
            return TopLevelResolution.Proceed(destItem)
        }

        when (config.conflictResolutions[targetPath] ?: CONFLICT_KEEP_BOTH) {
            CONFLICT_SKIP -> return TopLevelResolution.SkippedConflict
            CONFLICT_KEEP_BOTH -> {
                val altFile = destIndex.getAlternativeFile(item.name)
                destIndex.recordFile(altFile)
                destItem = FileDirItem(altFile.path, altFile.name, false)
            }
            CONFLICT_OVERWRITE -> Unit
        }
        return TopLevelResolution.Proceed(destItem)
    }

    private fun transferTopLevelItem(source: FileDirItem, dest: FileDirItem, topLevelIndex: Int) {
        if (!config.useStreamCopy && !source.isDirectory) {
            val srcFile = File(source.path)
            val dstFile = File(dest.path)
            val isOverwrite = config.conflictResolutions[dstFile.absolutePath] == CONFLICT_OVERWRITE
            val movedByRename = if (!dstFile.exists()) {
                srcFile.renameTo(dstFile)
            } else if (isOverwrite) {
                runCatching {
                    Files.move(
                        srcFile.toPath(),
                        dstFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    true
                }.getOrDefault(false)
            } else {
                false
            }
            if (movedByRename) {
                if (!activity.baseConfig.keepLastModified) {
                    dstFile.setLastModified(System.currentTimeMillis())
                }
                if (!config.isCopyOperation) {
                    runCatching { activity.updateDBMediaPath(source.path, dstFile.absolutePath) }
                    srcFile.parentFile?.let { activity.cleanupEmptyPlaintextAlbumIfNeeded(it, forceIfEmpty = false) }
                }
                movedPathsToRescan.add(dstFile.absolutePath)
                deletedDirectSourcePaths.add(source.path)
                transferredFiles.add(source)
                transferredBytes += source.size
                val itemCtx = OperationContext(operation.operationId, topLevelIndex, operation.kind, operation.batch)
                OperationJournal.markVerified(itemCtx, "", dstFile.absolutePath)
                if (!config.isCopyOperation) {
                    OperationJournal.markSourceDeleted(itemCtx)
                }
                return
            }
        }
        copy(source, dest, topLevelIndex = topLevelIndex)
    }

    private fun copy(source: FileDirItem, dest: FileDirItem, topLevelIndex: Int? = null) {
        if (source.isDirectory) {
            copyDirectory(source, dest.path, topLevelIndex = topLevelIndex)
        } else {
            copyFile(source, dest, topLevelIndex = topLevelIndex)
        }
    }

    private fun copyDirectory(source: FileDirItem, destPath: String, topLevelIndex: Int? = null) {
        val dirCtx = topLevelIndex?.let {
            OperationContext(operation.operationId, it, operation.kind, operation.batch)
        }
        if (!activity.createDirectorySync(destPath)) {
            val msg = activity.getString(org.fossify.commons.R.string.could_not_create_folder)
            activity.showErrorToast(String.format(msg, destPath))
            dirCtx?.let { OperationJournal.markFailed(it, "could not create folder") }
            return
        }

        when {
            activity.isPathOnOTG(source.path) -> {
                val children = activity.getDocumentFile(source.path)?.listFiles() ?: run {
                    dirCtx?.let { OperationJournal.markSkipped(it) }
                    return
                }
                copyDocumentChildren(source, destPath, children)
            }
            activity.isRestrictedSAFOnlyRoot(source.path) -> {
                activity.getAndroidSAFFileItems(source.path, true, false) { files ->
                    copyFileDirChildren(source, destPath, files)
                }
            }
            activity.isAccessibleWithSAFSdk30(source.path) -> {
                val children = activity.getDocumentSdk30(source.path)?.listFiles() ?: run {
                    dirCtx?.let { OperationJournal.markSkipped(it) }
                    return
                }
                copyDocumentChildren(source, destPath, children)
            }
            else -> {
                val childNames = File(source.path).list() ?: run {
                    dirCtx?.let { OperationJournal.markSkipped(it) }
                    return
                }
                copyLocalChildren(source, destPath, childNames)
            }
        }
        dirCtx?.let {
            OperationJournal.markVerified(it, "", destPath)
            if (!config.isCopyOperation && !File(source.path).exists()) {
                OperationJournal.markSourceDeleted(it)
            }
        }
    }

    private fun copyDocumentChildren(
        source: FileDirItem,
        destPath: String,
        children: Array<DocumentFile>
    ) {
        children.forEach { child ->
            val childName = child.name ?: return@forEach
            val targetPath = "$destPath/$childName"
            if (!File(targetPath).exists()) {
                val srcChild = FileDirItem(
                    path = "${source.path}/$childName",
                    name = childName,
                    isDirectory = child.isDirectory,
                    size = child.length()
                )
                copy(srcChild, FileDirItem(targetPath, childName, child.isDirectory))
            }
        }
        transferredFiles.add(source)
    }

    private fun copyFileDirChildren(
        source: FileDirItem,
        destPath: String,
        children: List<FileDirItem>
    ) {
        children.forEach { file ->
            val targetPath = "$destPath/${file.name}"
            if (!activity.getDoesFilePathExist(targetPath)) {
                val srcChild = FileDirItem(
                    path = "${source.path}/${file.name}",
                    name = file.name,
                    isDirectory = file.isDirectory,
                    size = file.size
                )
                copy(srcChild, FileDirItem(targetPath, file.name, file.isDirectory))
            }
        }
        transferredFiles.add(source)
    }

    private fun copyLocalChildren(
        source: FileDirItem,
        destPath: String,
        childNames: Array<String>
    ) {
        val destDir = File(destPath)
        val destIndex = getDestIndex(destDir)
        var anyChildSkipped = false
        var anyChildTransferred = false

        childNames.forEach { childName ->
            val srcFile = File(source.path, childName)
            val srcChild = srcFile.toFileDirItem(activity)
            if (srcFile.isDirectory) {
                val targetDirPath = File(destDir, childName).absolutePath
                copy(srcChild, FileDirItem(targetDirPath, childName, true))
                anyChildTransferred = true
            } else {
                val existing = destIndex.findExact(childName)
                val isIdentical = isDuplicatePlainInTarget(srcFile, childName, destIndex)

                if (isIdentical) {
                    anyChildSkipped = true
                } else {
                    val exactTarget = existing ?: File(destDir, childName)
                    val targetFile = if (existing != null || activity.getDoesFilePathExist(exactTarget.absolutePath)) {
                        when (config.conflictResolutions[exactTarget.absolutePath] ?: CONFLICT_KEEP_BOTH) {
                            CONFLICT_SKIP -> {
                                anyChildSkipped = true
                                null
                            }
                            CONFLICT_OVERWRITE -> exactTarget
                            else -> destIndex.getAlternativeFile(childName).also { destIndex.recordFile(it) }
                        }
                    } else {
                        exactTarget.also { destIndex.recordFile(it) }
                    }
                    if (targetFile != null) {
                        val dstChild = FileDirItem(targetFile.absolutePath, targetFile.name, false)
                        copy(srcChild, dstChild)
                        anyChildTransferred = true
                    }
                }
            }
        }
        if (!config.isCopyOperation && !anyChildSkipped) {
            if (activity.cleanupEmptyPlaintextAlbumIfNeeded(File(source.path), forceIfEmpty = true)) {
                runCatching { activity.updateReferencesForFolderRename(source.path, destPath) }
            }
        }
        if (anyChildTransferred || !anyChildSkipped) {
            transferredFiles.add(source)
        }
    }

    private fun copyFile(source: FileDirItem, dest: FileDirItem, topLevelIndex: Int? = null) {
        val itemIndex = topLevelIndex ?: nextItemIndex++
        val itemContext = OperationContext(
            operationId = operation.operationId,
            itemIndex = itemIndex,
            kind = operation.kind,
            batch = operation.batch
        )

        if (config.copyMediaOnly && !source.path.isMediaFile()) {
            OperationJournal.markSkipped(itemContext)
            transferredBytes += source.size
            return
        }

        val destParent = dest.getParentPath()
        if (!activity.createDirectorySync(destParent)) {
            val msg = activity.getString(org.fossify.commons.R.string.could_not_create_folder)
            activity.showErrorToast(String.format(msg, destParent))
            OperationJournal.markFailed(itemContext, "could not create folder")
            transferredBytes += source.size
            return
        }

        val sourceFile = File(source.path)
        val capturedLastModified = sourceFile.lastModified().takeIf { it > 0L } ?: source.modified
        val capturedDateTaken = if (activity.baseConfig.keepLastModified) {
            CryptoManager.resolvePlaintextDateTaken(
                context = activity.applicationContext,
                sourceFile = sourceFile,
                fallbackLastModified = capturedLastModified
            )
        } else {
            0L
        }

        if (!documents.containsKey(destParent) && activity.needsStupidWritePermissions(dest.path)) {
            documents[destParent] = activity.getDocumentFile(destParent)
        }

        // Direct File targets use a File sibling; SAF targets use a sibling DocumentFile.
        val destDocument = documents[destParent]
        if (destDocument == null) {
            val ready = TransientOperationWaiter.awaitDestinationReady(
                context = activity,
                ctx = itemContext,
                destDir = File(destParent),
                requiredBytes = source.size
            )
            if (!ready) {
                return
            }
        }

        val fileStaging = if (destDocument == null) {
            AtomicStaging.fileFor(File(dest.path), itemContext)
        } else {
            null
        }
        val safStaging = if (destDocument != null) {
            runCatching {
                destDocument.createFile(source.path.getMimeType(), AtomicStaging.safStageName(itemContext))
            }.getOrNull()
        } else {
            null
        }
        if (destDocument != null && safStaging == null) {
            OperationJournal.markFailed(itemContext, "could not create SAF staging document")
            return
        }
        val precomputedDirectFingerprint = if (safStaging == null) {
            MediaIdentity.computeFingerprint(sourceFile)
        } else {
            ""
        }

        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var transferSuccess = false
        var committed = false
        try {
            val (bytesCopied, sourceFingerprint) = TransientOperationWaiter.runWithTransientRetry(
                context = activity,
                ctx = itemContext,
                destDir = File(destParent),
                requiredBytes = source.size,
                maxAttempts = if (destDocument == null) 2 else 1,
                onDiscardAttempt = {
                    closeQuietly(inputStream)
                    closeQuietly(outputStream)
                    inputStream = null
                    outputStream = null
                    if (fileStaging != null) {
                        AtomicStaging.discard(fileStaging, containsPlaintext = true, context = activity.applicationContext)
                    }
                    if (safStaging != null) {
                        safStaging.uri.let {
                            runCatching { activity.contentResolver.openOutputStream(it, "wt")?.close() }
                        }
                    }
                }
            ) {
                OperationJournal.markInFlight(
                    ctx = itemContext,
                    stagingPath = fileStaging?.absolutePath ?: safStaging?.uri?.toString().orEmpty(),
                    resolvedDestName = dest.name,
                    actualDestPath = dest.path,
                    sourceFingerprint = precomputedDirectFingerprint
                )
                val attemptDigest = if (safStaging != null) {
                    runCatching { MessageDigest.getInstance("SHA-256") }.getOrNull()
                } else {
                    null
                }
                outputStream = when {
                    fileStaging != null -> FileOutputStream(fileStaging)
                    safStaging != null -> activity.contentResolver.openOutputStream(safStaging.uri)
                    else -> activity.getFileOutputStreamSync(dest.path, source.path.getMimeType(), destDocument)
                }
                inputStream = activity.getFileInputStreamSync(source.path)
                val copied = streamCopyWithProgress(inputStream, outputStream, attemptDigest) { delta ->
                    transferredBytes += delta
                    publishProgress(forceUi = false, filename = source.name)
                }
                closeQuietly(inputStream)
                closeQuietly(outputStream)
                inputStream = null
                outputStream = null
                val fp = attemptDigest?.digest()?.joinToString("") { "%02x".format(it) }
                    ?: precomputedDirectFingerprint
                copied to fp
            }

            val landed = when {
                fileStaging != null -> fileStaging.exists() && fileStaging.length() == source.size
                safStaging != null -> safStaging.length() == source.size
                else -> activity.getDoesFilePathExist(dest.path)
            }
            if (source.size == bytesCopied && landed) {
                if (fileStaging != null) {
                    AtomicStaging.commit(fileStaging, File(dest.path))
                    committed = true
                } else if (safStaging != null) {
                    // DocumentFile.renameTo is a directory-entry operation. No copy+delete fallback:
                    // on failure the item is FAILED and the source stays intact (ADR 0003 §3a).
                    if (!AtomicStaging.commitSaf(safStaging, File(dest.path).name)) {
                        throw IOException("SAF atomic rename failed")
                    }
                    committed = true
                }
                val destFingerprint = if (safStaging != null) {
                    AtomicStaging.safFingerprint(activity, safStaging.uri)
                } else {
                    MediaIdentity.computeFingerprint(File(dest.path))
                }
                if (sourceFingerprint.isEmpty() || destFingerprint == sourceFingerprint) {
                    OperationJournal.markVerified(itemContext, destFingerprint, dest.path)
                    transferSuccess = true
                } else {
                    OperationJournal.markFailed(itemContext, "copied fingerprint mismatch")
                }
            } else {
                OperationJournal.markFailed(itemContext, "incomplete copy")
            }
        } catch (e: CopyMoveCancelledException) {
            OperationJournal.markSkipped(itemContext)
            if (fileStaging != null) {
                AtomicStaging.discard(fileStaging, containsPlaintext = true, context = activity.applicationContext)
            }
            if (safStaging != null) AtomicStaging.discardSaf(safStaging)
            throw e
        } catch (e: Exception) {
            activity.showErrorToast(e)
            OperationJournal.markFailed(itemContext, e.message ?: "copy failed")
        } finally {
            closeQuietly(inputStream)
            closeQuietly(outputStream)
        }

        if (!transferSuccess) {
            if (fileStaging != null) {
                AtomicStaging.discard(fileStaging, containsPlaintext = true, context = activity.applicationContext)
            }
            if (safStaging != null) AtomicStaging.discardSaf(safStaging)
            if (committed && destDocument == null) runCatching { File(dest.path).delete() }
            return
        }

        transferredFiles.add(source)
        finalizeCopiedFile(dest, capturedLastModified, capturedDateTaken)
        if (!config.isCopyOperation) {
            runCatching { activity.updateDBMediaPath(source.path, dest.path) }
            if (activity.baseConfig.keepLastModified && capturedDateTaken > 0L) {
                CryptoManager.persistDecryptedDateTaken(
                    context = activity.applicationContext,
                    outFile = File(dest.path),
                    dateTaken = capturedDateTaken,
                    lastModified = capturedLastModified
                )
            }
            deleteSourceFile(source, itemContext)
            sourceFile.parentFile?.let { activity.cleanupEmptyPlaintextAlbumIfNeeded(it, forceIfEmpty = false) }
        }
    }

    private fun finalizeCopiedFile(dest: FileDirItem, lastModified: Long, dateTaken: Long) {
        if (activity.baseConfig.keepLastModified) {
            updateLastModifiedValues(activity, dest.path, lastModified, dateTaken)
        }
        // Defer indexing to the single bulk rescan in execute(). The metadata is re-applied
        // once that scan completes, since MediaScanner may overwrite file timestamps.
        copiedPathsToRescan.add(PendingRescan(dest.path, lastModified, dateTaken))
    }

    private fun deleteSourceFile(source: FileDirItem, itemContext: OperationContext) {
        val path = source.path
        if (activity.isRestrictedWithSAFSdk30(path) && !activity.canManageMedia() && !isExternalStorageManager()) {
            pendingSdk30Deletes.add(source to itemContext)
            return
        }
        val deleted = activity.deletePlainFileDirectlySync(source, allowDeleteFolder = false) &&
            !activity.doesPlainFileExistSync(path)
        if (deleted) {
            deletedDirectSourcePaths.add(path)
            OperationJournal.markSourceDeleted(itemContext)
        } else if (isRPlus() && !activity.isPathOnOTG(path) && !activity.isRestrictedSAFOnlyRoot(path)) {
            pendingSdk30Deletes.add(source to itemContext)
        }
    }

    private fun publishProgress(forceUi: Boolean, filename: String) {
        val totalKb = (totalBytes / BYTES_PER_KB).toInt()
        val currentKb = (transferredBytes / BYTES_PER_KB).toInt()
        val barMax = if (totalKb > 0) totalKb else maxOf(totalItems, 1)
        val barCurrent = if (totalKb > 0) currentKb.coerceIn(0, barMax) else processedItems.coerceIn(0, barMax)
        val summary = OperationProgressFormatter.formatCountSummary(
            current = processedItems,
            total = totalItems,
            barCurrent = barCurrent,
            barMax = barMax
        )

        progressDialog.updateNotifierOnly(
            current = barCurrent,
            max = barMax,
            contentText = filename,
            summaryText = summary
        )

        val now = System.currentTimeMillis()
        if (forceUi || now - lastUiUpdateMs >= UI_PROGRESS_UPDATE_INTERVAL_MS) {
            lastUiUpdateMs = now
            activity.runOnUiThread {
                progressDialog.updateProgress(
                    current = processedItems,
                    total = totalItems,
                    barCurrent = barCurrent,
                    barMax = barMax,
                    notificationText = filename
                )
            }
        }
    }
}

