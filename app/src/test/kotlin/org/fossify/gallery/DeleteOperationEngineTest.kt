package org.fossify.gallery

import org.fossify.gallery.helpers.DeleteOperationEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class DeleteOperationEngineTest {

    private lateinit var tempRoot: File
    private lateinit var sourceRoot: File
    private lateinit var recycleBinRoot: File

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("delete_engine_test_").toFile()
        sourceRoot = File(tempRoot, "storage/emulated/0/Pictures/Trip")
        recycleBinRoot = File(tempRoot, "filesDir")
        sourceRoot.mkdirs()
        recycleBinRoot.mkdirs()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    private fun createSourceFile(name: String, size: Int = 256, mtime: Long = 1_700_000_000_000L): File {
        val file = File(sourceRoot, name)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(size) { (it and 0xFF).toByte() })
        file.setLastModified(mtime)
        return file
    }

    private fun binDestFor(source: File): File {
        val relative = source.absolutePath.removePrefix(tempRoot.absolutePath)
        return File(recycleBinRoot, relative)
    }

    @Test
    fun moveFileToRecycleBinTransactionally_happyPathCommitsDbAndLeavesNoTempFiles() {
        val mtime = 1_700_123_456_000L
        val src = createSourceFile("photo.jpg", size = 512, mtime = mtime)
        val dest = binDestFor(src)
        val committed = ArrayList<Pair<String, String>>()

        val ok = DeleteOperationEngine.moveFileToRecycleBinTransactionally(
            sourcePath = src.absolutePath,
            destFile = dest,
            recycleBinRoot = recycleBinRoot,
            copyToBin = { target ->
                DeleteOperationEngine.copyToRecycleBinAtomically(
                    expectedSize = src.length(),
                    lastModified = src.lastModified(),
                    destFile = target,
                    keepLastModified = true
                ) { src.inputStream() }
            },
            deleteOriginal = { path -> File(path).delete() },
            doesOriginalExist = { path -> File(path).exists() },
            commitDb = { orig, bin -> committed.add(orig to bin.absolutePath) }
        )

        assertTrue(ok)
        assertFalse(src.exists())
        assertTrue(dest.exists())
        assertEquals(512L, dest.length())
        assertEquals(mtime, dest.lastModified())
        assertEquals(listOf(src.absolutePath to dest.absolutePath), committed)
        val leftoverTmp = dest.parentFile?.listFiles()?.any { it.name.contains(".tmp_") } ?: false
        assertFalse(leftoverTmp)
    }

    @Test
    fun moveFileToRecycleBinTransactionally_rollsBackBinCopyWhenOriginalCannotBeDeleted() {
        val src = createSourceFile("locked.jpg", size = 300)
        val dest = binDestFor(src)
        val committed = ArrayList<String>()

        val ok = DeleteOperationEngine.moveFileToRecycleBinTransactionally(
            sourcePath = src.absolutePath,
            destFile = dest,
            recycleBinRoot = recycleBinRoot,
            copyToBin = { target ->
                DeleteOperationEngine.copyToRecycleBinAtomically(
                    expectedSize = src.length(),
                    lastModified = src.lastModified(),
                    destFile = target,
                    keepLastModified = true
                ) { src.inputStream() }
            },
            deleteOriginal = { false },
            doesOriginalExist = { path -> File(path).exists() },
            commitDb = { orig, _ -> committed.add(orig) }
        )

        assertFalse(ok)
        assertTrue(src.exists())
        assertFalse(dest.exists())
        assertTrue(committed.isEmpty())
        assertTrue(recycleBinRoot.exists())
        assertTrue(recycleBinRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun moveFileToRecycleBinTransactionally_handlesCopyFailureWithoutTouchingOriginalOrDb() {
        val src = createSourceFile("broken.jpg", size = 200)
        val dest = binDestFor(src)
        var dbCommitted = false

        val ok = DeleteOperationEngine.moveFileToRecycleBinTransactionally(
            sourcePath = src.absolutePath,
            destFile = dest,
            recycleBinRoot = recycleBinRoot,
            copyToBin = { target ->
                DeleteOperationEngine.copyToRecycleBinAtomically(
                    expectedSize = 999L,
                    lastModified = src.lastModified(),
                    destFile = target,
                    keepLastModified = true
                ) { ByteArrayInputStream(ByteArray(10)) }
            },
            deleteOriginal = { File(it).delete() },
            doesOriginalExist = { File(it).exists() },
            commitDb = { _, _ -> dbCommitted = true }
        )

        assertFalse(ok)
        assertTrue(src.exists())
        assertFalse(dest.exists())
        assertFalse(dbCommitted)
        assertTrue(recycleBinRoot.listFiles().isNullOrEmpty())
    }

    @Test
    fun moveBatchToRecycleBinChunked_processesMultiChunkAndVerifiesEachFile() {
        val sources = (1..55).map { idx -> createSourceFile("img_$idx.jpg", size = 128 + idx) }
        val chunkSizesSeen = ArrayList<Int>()
        val committed = ArrayList<String>()
        var progressSum = 0

        val result = DeleteOperationEngine.moveBatchToRecycleBinChunked(
            sourcePaths = sources.map { it.absolutePath },
            recycleBinRoot = recycleBinRoot,
            destFileFor = { path -> binDestFor(File(path)) },
            isCancelled = { false },
            copyToBin = { path, dest ->
                val f = File(path)
                DeleteOperationEngine.copyToRecycleBinAtomically(
                    expectedSize = f.length(),
                    lastModified = f.lastModified(),
                    destFile = dest,
                    keepLastModified = true
                ) { f.inputStream() }
            },
            deleteChunkOriginals = { chunk ->
                chunkSizesSeen.add(chunk.size)
                chunk.all { File(it).delete() }
            },
            doesOriginalExist = { path -> File(path).exists() },
            commitDb = { orig, _ -> committed.add(orig) },
            onItemProgress = { delta -> progressSum += delta },
            chunkSize = 20
        )

        assertTrue(result.allSucceeded)
        assertEquals(listOf(20, 20, 15), chunkSizesSeen)
        assertEquals(55, result.deletedPaths.size)
        assertEquals(55, committed.size)
        assertEquals(55, progressSum)
        assertTrue(sources.none { it.exists() })
        assertTrue(sources.all { binDestFor(it).exists() })
    }

    @Test
    fun moveBatchToRecycleBinChunked_rollsBackSurvivingOriginalsWithinPartialChunk() {
        val files = (1..4).map { idx -> createSourceFile("part_$idx.jpg") }
        val survivingPath = files[2].absolutePath
        val committed = ArrayList<String>()

        val result = DeleteOperationEngine.moveBatchToRecycleBinChunked(
            sourcePaths = files.map { it.absolutePath },
            recycleBinRoot = recycleBinRoot,
            destFileFor = { path -> binDestFor(File(path)) },
            isCancelled = { false },
            copyToBin = { path, dest ->
                val f = File(path)
                DeleteOperationEngine.copyToRecycleBinAtomically(
                    expectedSize = f.length(),
                    lastModified = f.lastModified(),
                    destFile = dest,
                    keepLastModified = true
                ) { f.inputStream() }
            },
            deleteChunkOriginals = { chunk ->
                chunk.forEach { path ->
                    if (path != survivingPath) File(path).delete()
                }
                true
            },
            doesOriginalExist = { path -> File(path).exists() },
            commitDb = { orig, _ -> committed.add(orig) },
            onItemProgress = {},
            chunkSize = 10
        )

        assertFalse(result.allSucceeded)
        assertFalse(result.cancelled)
        assertEquals(3, result.deletedPaths.size)
        assertEquals(listOf(survivingPath), result.failedPaths)
        assertEquals(3, committed.size)
        assertTrue(File(survivingPath).exists())
        assertFalse(binDestFor(File(survivingPath)).exists())
    }

    @Test
    fun moveBatchToRecycleBinChunked_cancellingOnSecondChunkRollsBackAndStops() {
        val files = (1..5).map { idx -> createSourceFile("cancel_$idx.jpg") }
        var chunkIndex = 0
        val committed = ArrayList<String>()

        val result = DeleteOperationEngine.moveBatchToRecycleBinChunked(
            sourcePaths = files.map { it.absolutePath },
            recycleBinRoot = recycleBinRoot,
            destFileFor = { path -> binDestFor(File(path)) },
            isCancelled = { false },
            copyToBin = { path, dest ->
                val f = File(path)
                DeleteOperationEngine.copyToRecycleBinAtomically(
                    expectedSize = f.length(),
                    lastModified = f.lastModified(),
                    destFile = dest,
                    keepLastModified = true
                ) { f.inputStream() }
            },
            deleteChunkOriginals = { chunk ->
                chunkIndex++
                if (chunkIndex == 1) {
                    chunk.all { File(it).delete() }
                } else {
                    // User denied system delete prompt on chunk 2.
                    false
                }
            },
            doesOriginalExist = { path -> File(path).exists() },
            commitDb = { orig, _ -> committed.add(orig) },
            onItemProgress = {},
            chunkSize = 2
        )

        assertTrue(result.cancelled)
        assertFalse(result.allSucceeded)
        assertEquals(2, result.deletedPaths.size)
        assertEquals(2, committed.size)
        // Chunk 2 files must still exist on disk and must NOT remain in the recycle bin.
        assertTrue(files[2].exists())
        assertTrue(files[3].exists())
        assertFalse(binDestFor(files[2]).exists())
        assertFalse(binDestFor(files[3]).exists())
        // Chunk 3 file was never touched.
        assertTrue(files[4].exists())
        assertFalse(binDestFor(files[4]).exists())
    }

    @Test
    fun deleteFilesPermanentlyDirect_verifiesDeletionAndStopsOnCancel() {
        val files = (1..4).map { idx -> createSourceFile("perm_$idx.jpg") }
        val dbDeleted = ArrayList<String>()
        var calls = 0

        val result = DeleteOperationEngine.deleteFilesPermanentlyDirect(
            sourcePaths = files.map { it.absolutePath },
            isCancelled = { calls >= 2 },
            deleteOriginal = { path ->
                calls++
                if (path == files[1].absolutePath) {
                    false
                } else {
                    File(path).delete()
                }
            },
            doesOriginalExist = { path -> File(path).exists() },
            onItemDeleted = { path -> dbDeleted.add(path) },
            onItemProgress = {}
        )

        assertTrue(result.cancelled)
        assertEquals(listOf(files[0].absolutePath), result.deletedPaths)
        assertEquals(listOf(files[1].absolutePath), result.failedPaths)
        assertEquals(listOf(files[0].absolutePath), dbDeleted)
        assertFalse(files[0].exists())
        assertTrue(files[1].exists())
        assertTrue(files[2].exists())
        assertTrue(files[3].exists())
    }

    @Test
    fun deleteFilesPermanentlyChunked_commitsOnlyActuallyDeletedPaths() {
        val files = (1..3).map { idx -> createSourceFile("chunk_perm_$idx.jpg") }
        val dbDeleted = ArrayList<String>()

        val result = DeleteOperationEngine.deleteFilesPermanentlyChunked(
            sourcePaths = files.map { it.absolutePath },
            isCancelled = { false },
            deleteChunkOriginals = { chunk ->
                File(chunk[0]).delete()
                File(chunk[2]).delete()
                true
            },
            doesOriginalExist = { path -> File(path).exists() },
            onItemDeleted = { path -> dbDeleted.add(path) },
            onItemProgress = {},
            chunkSize = 5
        )

        assertFalse(result.allSucceeded)
        assertEquals(listOf(files[0].absolutePath, files[2].absolutePath), result.deletedPaths)
        assertEquals(listOf(files[1].absolutePath), result.failedPaths)
        assertEquals(listOf(files[0].absolutePath, files[2].absolutePath), dbDeleted)
    }

    @Test
    fun restoreFileFromRecycleBinAtomically_deletesBinCopyAndCleansEmptyParents() {
        val binFile = File(recycleBinRoot, "storage/emulated/0/DCIM/Camera/restored.jpg")
        binFile.parentFile?.mkdirs()
        binFile.writeBytes(ByteArray(128) { 7 })
        val journalMarker = File(recycleBinRoot, "pending_wipe_journal.txt")
        journalMarker.writeText("keep-me")

        val restoredTarget = File(tempRoot, "restored_out/restored.jpg")
        var dbUpdated = false

        val ok = DeleteOperationEngine.restoreFileFromRecycleBinAtomically(
            binFile = binFile,
            recycleBinRoot = recycleBinRoot,
            copyToDest = {
                restoredTarget.parentFile?.mkdirs()
                binFile.copyTo(restoredTarget, overwrite = true)
                true
            },
            verifyDestExists = { restoredTarget.exists() && restoredTarget.length() == 128L },
            commitDb = { dbUpdated = true }
        )

        assertTrue(ok)
        assertTrue(dbUpdated)
        assertTrue(restoredTarget.exists())
        assertFalse(binFile.exists())
        assertFalse(File(recycleBinRoot, "storage").exists())
        assertTrue(recycleBinRoot.exists())
        assertTrue(journalMarker.exists())
    }

    @Test
    fun cleanupEmptyRecycleBinParents_stopsAtNonEmptyDirAndPreservesRoot() {
        val deepDir = File(recycleBinRoot, "a/b/c")
        deepDir.mkdirs()
        val siblingFile = File(recycleBinRoot, "a/keep.txt")
        siblingFile.writeText("stay")

        DeleteOperationEngine.cleanupEmptyRecycleBinParents(deepDir, recycleBinRoot)

        assertFalse(deepDir.exists())
        assertFalse(File(recycleBinRoot, "a/b").exists())
        assertTrue(File(recycleBinRoot, "a").exists())
        assertTrue(siblingFile.exists())
        assertTrue(recycleBinRoot.exists())
    }

    @Test
    fun reconcileRecycleBinState_cleansTempRollsBackDuplicatesAndAdoptsOrphanDeletes() {
        val journalMarker = File(recycleBinRoot, "pending_wipe_journal.txt").apply { writeText("keep") }

        val tmpFile = File(recycleBinRoot, "storage/emulated/0/DCIM/.a.jpg.tmp_123").apply {
            parentFile?.mkdirs()
            writeText("partial")
        }

        val duplicateOrig = "/storage/emulated/0/DCIM/dup.jpg"
        val duplicateBin = File(recycleBinRoot, "storage/emulated/0/DCIM/dup.jpg").apply {
            parentFile?.mkdirs()
            writeText("dup-copy")
        }

        val orphanOrig = "/storage/emulated/0/DCIM/orphan_deleted.jpg"
        val orphanBin = File(recycleBinRoot, "storage/emulated/0/DCIM/orphan_deleted.jpg").apply {
            parentFile?.mkdirs()
            writeText("only-in-bin")
        }

        val registeredOrig = "/storage/emulated/0/DCIM/already_registered.jpg"
        val registeredBin = File(recycleBinRoot, "storage/emulated/0/DCIM/already_registered.jpg").apply {
            parentFile?.mkdirs()
            writeText("registered")
        }

        val adopted = ArrayList<Pair<String, String>>()
        val result = DeleteOperationEngine.reconcileRecycleBinState(
            recycleBinRoot = recycleBinRoot,
            registeredDeletedOrigPaths = setOf(registeredOrig),
            doesOriginalExist = { origPath -> origPath == duplicateOrig },
            adoptMissingDbEntry = { origPath, binFile -> adopted.add(origPath to binFile.absolutePath) }
        )

        assertEquals(1, result.tempFilesRemoved)
        assertEquals(1, result.orphanBinCopiesRolledBack)
        assertEquals(1, result.orphanedDeletesAdopted)
        assertFalse(tmpFile.exists())
        assertFalse(duplicateBin.exists())
        assertTrue(orphanBin.exists())
        assertTrue(registeredBin.exists())
        assertTrue(journalMarker.exists())
        assertEquals(listOf(orphanOrig to orphanBin.absolutePath), adopted)
    }
}
