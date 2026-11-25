package org.fossify.gallery.helpers

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

const val SDK30_DELETE_CHUNK_SIZE = 250
const val MEDIASTORE_DELETE_CHUNK_SIZE = 400
const val DELETE_COPY_BUFFER_SIZE = 256 * 1024
const val DELETE_UI_PROGRESS_INTERVAL_MS = 80L

data class DeleteBatchResult(
    val deletedPaths: List<String>,
    val failedPaths: List<String>,
    val cancelled: Boolean
) {
    val allSucceeded: Boolean get() = failedPaths.isEmpty() && !cancelled
}

data class RecycleBinReconcileResult(
    val tempFilesRemoved: Int,
    val orphanBinCopiesRolledBack: Int,
    val orphanedDeletesAdopted: Int
)

/**
 * Pure-JVM transactional helpers for moving files to the recycle bin, deleting permanently,
 * cleaning empty recycle-bin parent folders, and restoring files without leaving duplicates.
 */
object DeleteOperationEngine {

    fun copyToRecycleBinAtomically(
        expectedSize: Long,
        lastModified: Long,
        destFile: File,
        keepLastModified: Boolean,
        openInput: () -> InputStream?
    ): Boolean {
        val parent = destFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) {
            return false
        }

        val tmpFile = File(parent, ".${destFile.name}.tmp_${System.nanoTime()}")
        var succeeded = false
        try {
            val copiedOk = streamCopyToTempFile(tmpFile, expectedSize, openInput)
            val replaced = copiedOk && (!destFile.exists() || destFile.delete())
            if (replaced && tmpFile.renameTo(destFile) && destFile.exists()) {
                if (keepLastModified && lastModified > 0L) {
                    destFile.setLastModified(lastModified)
                }
                succeeded = true
            }
        } catch (_: Exception) {
            succeeded = false
        } finally {
            if (!succeeded) {
                runCatching { tmpFile.delete() }
            }
        }
        return succeeded
    }

    private fun streamCopyToTempFile(
        tmpFile: File,
        expectedSize: Long,
        openInput: () -> InputStream?
    ): Boolean {
        val input = openInput() ?: return false
        var copiedSize = 0L
        input.use { inStream ->
            FileOutputStream(tmpFile).use { outStream ->
                val buffer = ByteArray(DELETE_COPY_BUFFER_SIZE)
                var read = inStream.read(buffer)
                while (read >= 0) {
                    outStream.write(buffer, 0, read)
                    copiedSize += read
                    read = inStream.read(buffer)
                }
                outStream.flush()
            }
        }
        return expectedSize < 0L || copiedSize == expectedSize
    }

    fun moveFileToRecycleBinTransactionally(
        sourcePath: String,
        destFile: File,
        recycleBinRoot: File,
        copyToBin: (File) -> Boolean,
        deleteOriginal: (String) -> Boolean,
        doesOriginalExist: (String) -> Boolean,
        commitDb: (String, File) -> Unit
    ): Boolean {
        if (!copyToBin(destFile) || !destFile.exists()) {
            runCatching { destFile.delete() }
            cleanupEmptyRecycleBinParents(destFile.parentFile, recycleBinRoot)
            return false
        }

        val deleted = runCatching { deleteOriginal(sourcePath) }.getOrDefault(false)
        val stillExists = runCatching { doesOriginalExist(sourcePath) }.getOrDefault(true)
        if (!deleted || stillExists) {
            // Roll back the recycle-bin copy so the file never exists in both places at once.
            runCatching { destFile.delete() }
            cleanupEmptyRecycleBinParents(destFile.parentFile, recycleBinRoot)
            return false
        }

        commitDb(sourcePath, destFile)
        return true
    }

    fun moveBatchToRecycleBinChunked(
        sourcePaths: List<String>,
        recycleBinRoot: File,
        destFileFor: (String) -> File,
        isCancelled: () -> Boolean,
        copyToBin: (String, File) -> Boolean,
        deleteChunkOriginals: (List<String>) -> Boolean,
        doesOriginalExist: (String) -> Boolean,
        commitDb: (String, File) -> Unit,
        onItemProgress: (processedDelta: Int) -> Unit,
        chunkSize: Int = SDK30_DELETE_CHUNK_SIZE
    ): DeleteBatchResult {
        if (sourcePaths.isEmpty()) {
            return DeleteBatchResult(emptyList(), emptyList(), false)
        }

        val deletedPaths = ArrayList<String>(sourcePaths.size)
        val failedPaths = ArrayList<String>()
        var cancelled = false
        val chunks = sourcePaths.chunked(chunkSize.coerceAtLeast(1))
        var chunkIdx = 0

        while (chunkIdx < chunks.size && !cancelled) {
            if (isCancelled()) {
                cancelled = true
            } else {
                val staged = ArrayList<Pair<String, File>>()
                cancelled = stageChunkToRecycleBin(
                    chunk = chunks[chunkIdx],
                    recycleBinRoot = recycleBinRoot,
                    destFileFor = destFileFor,
                    isCancelled = isCancelled,
                    copyToBin = copyToBin,
                    staged = staged,
                    failedPaths = failedPaths,
                    onItemProgress = onItemProgress
                )
                if (cancelled) {
                    rollbackStagedCopies(staged, recycleBinRoot)
                } else if (staged.isNotEmpty()) {
                    val chunkDeleted = runCatching {
                        deleteChunkOriginals(staged.map { it.first })
                    }.getOrDefault(false)
                    verifyChunkOriginalsDeleted(
                        staged = staged,
                        recycleBinRoot = recycleBinRoot,
                        doesOriginalExist = doesOriginalExist,
                        commitDb = commitDb,
                        deletedPaths = deletedPaths,
                        failedPaths = failedPaths,
                        onItemProgress = onItemProgress
                    )
                    cancelled = !chunkDeleted || isCancelled()
                }
                chunkIdx++
            }
        }

        return DeleteBatchResult(deletedPaths, failedPaths, cancelled)
    }

    private fun stageChunkToRecycleBin(
        chunk: List<String>,
        recycleBinRoot: File,
        destFileFor: (String) -> File,
        isCancelled: () -> Boolean,
        copyToBin: (String, File) -> Boolean,
        staged: MutableList<Pair<String, File>>,
        failedPaths: MutableList<String>,
        onItemProgress: (processedDelta: Int) -> Unit
    ): Boolean {
        var idx = 0
        var cancelled = false
        while (idx < chunk.size && !cancelled) {
            if (isCancelled()) {
                cancelled = true
            } else {
                val path = chunk[idx]
                val destFile = destFileFor(path)
                val copied = runCatching { copyToBin(path, destFile) && destFile.exists() }.getOrDefault(false)
                if (copied) {
                    staged.add(path to destFile)
                } else {
                    runCatching { destFile.delete() }
                    cleanupEmptyRecycleBinParents(destFile.parentFile, recycleBinRoot)
                    failedPaths.add(path)
                    onItemProgress(1)
                }
                idx++
            }
        }
        return cancelled
    }

    private fun verifyChunkOriginalsDeleted(
        staged: List<Pair<String, File>>,
        recycleBinRoot: File,
        doesOriginalExist: (String) -> Boolean,
        commitDb: (String, File) -> Unit,
        deletedPaths: MutableList<String>,
        failedPaths: MutableList<String>,
        onItemProgress: (processedDelta: Int) -> Unit
    ) {
        for ((path, destFile) in staged) {
            val stillExists = runCatching { doesOriginalExist(path) }.getOrDefault(true)
            if (!stillExists) {
                commitDb(path, destFile)
                deletedPaths.add(path)
            } else {
                runCatching { destFile.delete() }
                cleanupEmptyRecycleBinParents(destFile.parentFile, recycleBinRoot)
                failedPaths.add(path)
            }
            onItemProgress(1)
        }
    }

    fun deleteFilesPermanentlyDirect(
        sourcePaths: List<String>,
        isCancelled: () -> Boolean,
        deleteOriginal: (String) -> Boolean,
        doesOriginalExist: (String) -> Boolean,
        onItemDeleted: (String) -> Unit,
        onItemProgress: (processedDelta: Int) -> Unit
    ): DeleteBatchResult {
        if (sourcePaths.isEmpty()) {
            return DeleteBatchResult(emptyList(), emptyList(), false)
        }

        val deletedPaths = ArrayList<String>(sourcePaths.size)
        val failedPaths = ArrayList<String>()
        var cancelled = false

        for (path in sourcePaths) {
            if (isCancelled()) {
                cancelled = true
                break
            }
            val deleted = runCatching { deleteOriginal(path) }.getOrDefault(false)
            val stillExists = runCatching { doesOriginalExist(path) }.getOrDefault(true)
            if (deleted && !stillExists) {
                onItemDeleted(path)
                deletedPaths.add(path)
            } else {
                failedPaths.add(path)
            }
            onItemProgress(1)
        }

        return DeleteBatchResult(deletedPaths, failedPaths, cancelled)
    }

    fun deleteFilesPermanentlyChunked(
        sourcePaths: List<String>,
        isCancelled: () -> Boolean,
        deleteChunkOriginals: (List<String>) -> Boolean,
        doesOriginalExist: (String) -> Boolean,
        onItemDeleted: (String) -> Unit,
        onItemProgress: (processedDelta: Int) -> Unit,
        chunkSize: Int = SDK30_DELETE_CHUNK_SIZE
    ): DeleteBatchResult {
        if (sourcePaths.isEmpty()) {
            return DeleteBatchResult(emptyList(), emptyList(), false)
        }

        val deletedPaths = ArrayList<String>(sourcePaths.size)
        val failedPaths = ArrayList<String>()
        var cancelled = false
        val safeChunkSize = chunkSize.coerceAtLeast(1)

        for (chunk in sourcePaths.chunked(safeChunkSize)) {
            if (isCancelled()) {
                cancelled = true
                break
            }

            val chunkDeleted = runCatching { deleteChunkOriginals(chunk) }.getOrDefault(false)
            for (path in chunk) {
                val stillExists = runCatching { doesOriginalExist(path) }.getOrDefault(true)
                if (!stillExists) {
                    onItemDeleted(path)
                    deletedPaths.add(path)
                } else {
                    failedPaths.add(path)
                }
                onItemProgress(1)
            }

            if (!chunkDeleted || isCancelled()) {
                cancelled = true
                break
            }
        }

        return DeleteBatchResult(deletedPaths, failedPaths, cancelled)
    }

    fun restoreFileFromRecycleBinAtomically(
        binFile: File,
        recycleBinRoot: File,
        copyToDest: () -> Boolean,
        verifyDestExists: () -> Boolean,
        commitDb: () -> Unit
    ): Boolean {
        val copied = runCatching { copyToDest() }.getOrDefault(false)
        val existsAtDest = if (copied) runCatching { verifyDestExists() }.getOrDefault(false) else false
        if (!copied || !existsAtDest) {
            return false
        }

        commitDb()
        if (binFile.exists()) {
            runCatching { binFile.delete() }
        }
        cleanupEmptyRecycleBinParents(binFile.parentFile, recycleBinRoot)
        return true
    }

    fun cleanupEmptyRecycleBinParents(startDir: File?, recycleBinRoot: File) {
        val rootPath = normalizeDirPath(recycleBinRoot)
        var current = startDir
        while (current != null) {
            val currentPath = normalizeDirPath(current)
            if (currentPath == rootPath || !currentPath.startsWith("$rootPath/")) {
                break
            }
            if (!current.isDirectory) {
                break
            }
            val children = current.listFiles()
            if (children != null && children.isEmpty()) {
                if (!runCatching { current.delete() }.getOrDefault(false)) {
                    break
                }
                current = current.parentFile
            } else {
                break
            }
        }
    }

    fun reconcileRecycleBinState(
        recycleBinRoot: File,
        registeredDeletedOrigPaths: Set<String>,
        doesOriginalExist: (String) -> Boolean,
        adoptMissingDbEntry: (origPath: String, binFile: File) -> Unit
    ): RecycleBinReconcileResult {
        val rootNorm = normalizeDirPath(recycleBinRoot)
        val registeredLower = registeredDeletedOrigPaths.mapTo(HashSet(registeredDeletedOrigPaths.size)) {
            it.replace('\\', '/').trimEnd('/').lowercase()
        }

        var tempRemoved = 0
        var rolledBack = 0
        var adopted = 0

        for (subName in listOf("storage", "mnt")) {
            val subRoot = File(recycleBinRoot, subName)
            if (!subRoot.exists() || !subRoot.isDirectory) continue
            val files = subRoot.walkTopDown().filter { it.isFile }.toList()
            for (file in files) {
                if (file.name.contains(".tmp_")) {
                    if (runCatching { file.delete() }.getOrDefault(false) || !file.exists()) {
                        cleanupEmptyRecycleBinParents(file.parentFile, recycleBinRoot)
                        tempRemoved++
                    }
                    continue
                }
                val fileNorm = normalizeDirPath(file)
                if (!fileNorm.startsWith("$rootNorm/")) continue
                val origPath = fileNorm.removePrefix(rootNorm)
                if (origPath.trimEnd('/').lowercase() in registeredLower) continue

                if (runCatching { doesOriginalExist(origPath) }.getOrDefault(true)) {
                    if (runCatching { file.delete() }.getOrDefault(false) || !file.exists()) {
                        cleanupEmptyRecycleBinParents(file.parentFile, recycleBinRoot)
                        rolledBack++
                    }
                } else {
                    runCatching { adoptMissingDbEntry(origPath, file) }
                    adopted++
                }
            }
        }
        return RecycleBinReconcileResult(tempRemoved, rolledBack, adopted)
    }

    private fun rollbackStagedCopies(staged: List<Pair<String, File>>, recycleBinRoot: File) {
        for ((_, destFile) in staged) {
            runCatching { destFile.delete() }
            cleanupEmptyRecycleBinParents(destFile.parentFile, recycleBinRoot)
        }
    }

    private fun normalizeDirPath(dir: File): String {
        val raw = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        return raw.replace('\\', '/').trimEnd('/')
    }
}
