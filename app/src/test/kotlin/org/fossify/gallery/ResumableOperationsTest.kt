package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.SECRETS_DIR_NAME
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.BatchOperationInfo
import org.fossify.gallery.helpers.operations.EncryptedSizeInput
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.operations.JournalItem
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.OperationJournal
import org.fossify.gallery.helpers.operations.OperationRecovery
import org.fossify.gallery.helpers.operations.SpaceEstimator
import org.fossify.gallery.helpers.operations.TransientOperationWaiter
import org.fossify.gallery.helpers.setVaultRootLocation
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.interfaces.PendingOperationsDao
import org.fossify.gallery.models.PendingOperation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** ADR 0003 §3a / §3 — pure, JVM-only checks of space/state/staging and recovery primitives. */
class ResumableOperationsTest {

    private lateinit var tempRoot: File
    private lateinit var testContext: Context
    private lateinit var fakeDao: InMemoryPendingOperationsDao

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("resumable_ops_test_").toFile()
        testContext = FakeContext(tempRoot)
        fakeDao = InMemoryPendingOperationsDao()
        OperationJournal.initForTest(fakeDao)
        OperationRecovery.resetForTests()
        unlockTestVault()
    }

    @After
    fun tearDown() {
        OperationJournal.initForTest(null)
        OperationRecovery.resetForTests()
        CryptoManager.lockVault()
        tempRoot.deleteRecursively()
    }

    @Test
    fun plainEstimateAndMargin() {
        val estimate = SpaceEstimator.forPlain(listOf(100L, 200L))
        assertEquals(300L, estimate.requiredBytes)
        assertEquals(300L, estimate.peakBytes)
        assertEquals(300L + SpaceEstimator.MIN_MARGIN_BYTES, SpaceEstimator.requiredWithMargin(300L))
        // An exact fit is deliberately not treated as sufficient.
        assertFalse(SpaceEstimator.hasEnough(300L, 300L))
        assertTrue(SpaceEstimator.hasEnough(300L + SpaceEstimator.MIN_MARGIN_BYTES, 300L))
        assertFalse(SpaceEstimator.hasEnough(-1L, 300L))
    }

    @Test
    fun encryptedSizeIsDeterministicAndLargerThanPlaintext() {
        val input = EncryptedSizeInput(originalSize = 5_000_000L, originalName = "fotoğraf.jpg")
        val size = VaultContainerWriter.estimateEncryptedSize(input)
        assertTrue(size > input.originalSize)
        assertEquals(size, VaultContainerWriter.estimateEncryptedSize(input))
    }

    @Test
    fun albumPeakIsTheSumOfAllOutputs() {
        val items = (1..10).map { EncryptedSizeInput(it * 1_000L, "f$it.jpg") }
        val estimate = SpaceEstimator.forEncrypted(items)
        assertEquals(items.sumOf { VaultContainerWriter.estimateEncryptedSize(it) }, estimate.peakBytes)
    }

    @Test
    fun stagingNameIsStableHiddenAndJournalled() {
        val context = OperationContext(operationId = "op123", itemIndex = 2, kind = "COPY")
        assertTrue(context.isJournalled)
        assertFalse(OperationContext.NONE.isJournalled)

        val first = AtomicStaging.fileFor(File("/tmp/out.bin"), context)
        val second = AtomicStaging.fileFor(File("/tmp/out.bin"), context)
        assertEquals(first.name, second.name)
        assertTrue(first.name.startsWith(".stage_op123_2"))
        assertTrue(AtomicStaging.isStagingFile(first.name))
        assertFalse(AtomicStaging.isStagingFile("photo.jpg"))
    }

    @Test
    fun completedCopyAndFailedBatchesNeverTriggerFalseResumePrompt() {
        val opCopy = OperationContext(
            operationId = "copy_done",
            kind = OperationJournal.KIND_COPY,
            batch = BatchOperationInfo(isMove = false, wipeSource = false, deleteSource = false)
        )
        OperationJournal.beginBatch(
            opCopy,
            listOf(
                JournalItem("/src/a.jpg", "/dst/a.jpg", 10L, kind = OperationJournal.KIND_COPY),
                JournalItem("/src/b.jpg", "/dst/b.jpg", 10L, kind = OperationJournal.KIND_COPY)
            )
        )
        OperationJournal.markVerified(opCopy.copy(itemIndex = 0), "fp_a", "/dst/a.jpg")
        OperationJournal.markFailed(opCopy.copy(itemIndex = 1), "disk full")

        assertTrue(OperationJournal.resumableItems().isEmpty())
        assertEquals(0, OperationRecovery.reconcile(testContext))
        assertTrue(OperationJournal.allItems().isEmpty())
    }

    @Test
    fun reconcileSkipsWhenForegroundOperationIsActiveOnAnotherThread() {
        val op = OperationContext(
            operationId = "live_op",
            kind = OperationJournal.KIND_COPY,
            batch = BatchOperationInfo(isMove = false)
        )
        OperationJournal.beginBatch(
            op,
            listOf(JournalItem("/src/a.jpg", "/dst/a.jpg", 10L, kind = OperationJournal.KIND_COPY))
        )
        OperationJournal.markInFlight(op.copy(itemIndex = 0), "/dst/.stage_live_op_0.tmp")

        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Thread {
            assertTrue(ForegroundOperationRegistry.acquire(testContext, "copy-move"))
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            ForegroundOperationRegistry.release(testContext, "copy-move")
        }
        worker.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))

        try {
            assertEquals(0, OperationRecovery.reconcile(testContext))
            // Active IN_FLIGHT item must not be reset while the live operation owns the registry.
            assertEquals(OperationJournal.STATE_IN_FLIGHT, OperationJournal.itemsFor("live_op").single().state)
        } finally {
            release.countDown()
            worker.join(5000)
        }
    }

    @Test
    fun resumeInterruptedEncryptionCompletesPendingAndVerifiedWipeItems() {
        val srcDir = File(tempRoot, "src_album").apply { mkdirs() }
        val vaultAlbum = File(tempRoot, "vault/sub1").apply { mkdirs() }

        val file0 = File(srcDir, "img0.jpg").apply { writeBytes(ByteArray(2048) { 1 }) }
        val file1 = File(srcDir, "img1.jpg").apply { writeBytes(ByteArray(4096) { 2 }) }
        val file2 = File(srcDir, "img2.jpg").apply { writeBytes(ByteArray(1024) { 3 }) }

        val opId = "enc_op_1"
        val batchInfo = BatchOperationInfo(
            sourceRoot = srcDir.absolutePath,
            destRoot = vaultAlbum.absolutePath,
            isMove = true,
            wipeSource = true,
            deleteSource = true
        )
        val batchCtx = OperationContext(opId, kind = OperationJournal.KIND_ENCRYPT, batch = batchInfo)
        OperationJournal.beginBatch(
            batchCtx,
            listOf(file0, file1, file2).map {
                JournalItem(
                    sourcePath = it.absolutePath,
                    destPath = File(vaultAlbum, it.name).absolutePath,
                    sourceSize = it.length(),
                    resolvedDestName = it.name,
                    kind = OperationJournal.KIND_ENCRYPT,
                    conflictResolution = CONFLICT_KEEP_BOTH.toString()
                )
            }
        )

        // Item 0 was encrypted in phase 1 (wipeSource=false -> STATE_VERIFIED) before process died!
        val enc0 = CryptoManager.encryptFileToDirectory(
            context = testContext,
            sourceFile = file0,
            targetDir = vaultAlbum,
            wipeSource = false,
            operation = OperationContext(opId, 0, OperationJournal.KIND_ENCRYPT, batchInfo)
        )
        assertNotNull(enc0)
        assertTrue(file0.exists())
        val row0 = OperationJournal.itemsFor(opId).first { it.itemIndex == 0 }
        assertEquals(OperationJournal.STATE_VERIFIED, row0.state)
        assertEquals(enc0!!.absolutePath, row0.destPath)

        // Item 1 died mid-write with an orphan staging file.
        val orphan = AtomicStaging.fileFor(File(vaultAlbum, "dummy.bin"), OperationContext(opId, 1, OperationJournal.KIND_ENCRYPT, batchInfo))
        orphan.writeBytes(ByteArray(512) { 9 })
        OperationJournal.markInFlight(OperationContext(opId, 1, OperationJournal.KIND_ENCRYPT, batchInfo), orphan.absolutePath)

        // Item 2 never started (PENDING).
        assertEquals(1, OperationRecovery.reconcile(testContext))
        assertFalse("Orphan staging file should be removed", orphan.exists())

        var resumeSuccess = false
        OperationRecovery.resume(testContext) { resumeSuccess = it }

        assertTrue(resumeSuccess)
        assertFalse("Item 0 source should have been wiped on resume", file0.exists())
        assertFalse("Item 1 source should have been wiped on resume", file1.exists())
        assertFalse("Item 2 source should have been wiped on resume", file2.exists())
        val bins = vaultAlbum.listFiles()?.filter { it.name.endsWith(".bin") } ?: emptyList()
        assertEquals(3, bins.size)
        bins.forEach { assertTrue(CryptoManager.verifyFileIntegrity(it)) }
        assertTrue(OperationJournal.allItems().isEmpty())
    }

    @Test
    fun resumeInterruptedDecryptionDecryptsAndHonorsRecordedSkip() {
        val vaultAlbum = File(tempRoot, "vault/sub2").apply { mkdirs() }
        val plainOut = File(tempRoot, "restored").apply { mkdirs() }

        val origA = File(tempRoot, "a.jpg").apply { writeBytes(ByteArray(1500) { 5 }) }
        val origB = File(tempRoot, "b.jpg").apply { writeBytes(ByteArray(1500) { 6 }) }
        val binA = CryptoManager.encryptFileToDirectory(testContext, origA, vaultAlbum, wipeSource = true)!!
        val binB = CryptoManager.encryptFileToDirectory(testContext, origB, vaultAlbum, wipeSource = true)!!

        val opId = "dec_op_1"
        val batchInfo = BatchOperationInfo(
            sourceRoot = vaultAlbum.absolutePath,
            destRoot = plainOut.absolutePath,
            isMove = true,
            wipeSource = false,
            deleteSource = true
        )
        OperationJournal.beginBatch(
            OperationContext(opId, kind = OperationJournal.KIND_DECRYPT, batch = batchInfo),
            listOf(
                JournalItem(
                    sourcePath = binA.absolutePath,
                    destPath = File(plainOut, binA.name).absolutePath,
                    sourceSize = binA.length(),
                    resolvedDestName = binA.name,
                    kind = OperationJournal.KIND_DECRYPT,
                    conflictResolution = CONFLICT_KEEP_BOTH.toString()
                ),
                JournalItem(
                    sourcePath = binB.absolutePath,
                    destPath = File(plainOut, binB.name).absolutePath,
                    sourceSize = binB.length(),
                    resolvedDestName = binB.name,
                    kind = OperationJournal.KIND_DECRYPT,
                    conflictResolution = CONFLICT_SKIP.toString()
                )
            )
        )
        val orphanPlain = AtomicStaging.fileFor(File(plainOut, "a.jpg"), OperationContext(opId, 0, OperationJournal.KIND_DECRYPT, batchInfo))
        orphanPlain.writeBytes(ByteArray(300) { 7 })
        OperationJournal.markInFlight(OperationContext(opId, 0, OperationJournal.KIND_DECRYPT, batchInfo), orphanPlain.absolutePath)

        assertEquals(1, OperationRecovery.reconcile(testContext))
        assertFalse(orphanPlain.exists())

        var resumeSuccess = false
        OperationRecovery.resume(testContext) { resumeSuccess = it }

        assertTrue(resumeSuccess)
        assertTrue(File(plainOut, "a.jpg").exists())
        assertEquals(1500L, File(plainOut, "a.jpg").length())
        assertFalse(binA.exists())
        // Item B had CONFLICT_SKIP recorded, so it was skipped rather than decrypted.
        assertFalse(File(plainOut, "b.jpg").exists())
        assertTrue(binB.exists())
        assertTrue(OperationJournal.allItems().isEmpty())
    }

    @Test
    fun foregroundRegistrySupportsSameThreadReentrancyAndRejectsConcurrentThread() {
        assertFalse(ForegroundOperationRegistry.isActive)
        val outerResult = ForegroundOperationRegistry.withOperation(testContext, "album-encrypt") {
            assertTrue(ForegroundOperationRegistry.isActive)

            // Same thread (e.g. DirectoryAdapter outer batch -> CryptoManager.encryptFolder) shares token.
            val nestedResult = ForegroundOperationRegistry.withOperation(testContext, "album-encrypt") {
                assertTrue(ForegroundOperationRegistry.isActive)
                ForegroundOperationRegistry.requestCancel()
                assertTrue(ForegroundOperationRegistry.isCancelled)
                "nested-ok"
            }
            assertEquals("nested-ok", nestedResult)
            // Outer operation remains active until final release.
            assertTrue(ForegroundOperationRegistry.isActive)

            // Different thread must be rejected and receive null from withOperation.
            var concurrentResult: String? = "unassigned"
            val worker = Thread {
                concurrentResult = ForegroundOperationRegistry.withOperation(testContext, "crypto-copy-move") {
                    "should-not-run"
                }
            }
            worker.start()
            worker.join(5000)
            assertNull(concurrentResult)
            "outer-ok"
        }

        assertEquals("outer-ok", outerResult)
        assertFalse(ForegroundOperationRegistry.isActive)
        assertFalse(ForegroundOperationRegistry.isCancelled)

        // Once released, another thread can acquire cleanly.
        val afterRelease = ForegroundOperationRegistry.withOperation(testContext, "album-decrypt") { "next-ok" }
        assertEquals("next-ok", afterRelease)
        assertFalse(ForegroundOperationRegistry.isActive)
    }

    @Test
    fun resumeHonorsPinnedResolvedDestNameAndReusesMatchedDestination() {
        val srcDir = File(tempRoot, "plain_src").apply { mkdirs() }
        val dstDir = File(tempRoot, "plain_dst").apply { mkdirs() }

        // Existing different photo in dst forces "photo (1).jpg" under KEEP_BOTH.
        val existingCollision = File(dstDir, "photo.jpg").apply { writeBytes(ByteArray(900) { 1 }) }
        val srcMoved = File(srcDir, "photo.jpg").apply { writeBytes(ByteArray(1200) { 2 }) }
        val srcAlreadyLanded = File(srcDir, "done.jpg").apply { writeBytes(ByteArray(1400) { 3 }) }
        val preCommittedDest = File(dstDir, "done.jpg").apply { writeBytes(srcAlreadyLanded.readBytes()) }

        val opId = "move_pin_op"
        val batch = BatchOperationInfo(
            sourceRoot = srcDir.absolutePath,
            destRoot = dstDir.absolutePath,
            isMove = true,
            wipeSource = false,
            deleteSource = true
        )
        OperationJournal.beginBatch(
            OperationContext(opId, kind = OperationJournal.KIND_MOVE, batch = batch),
            listOf(
                JournalItem(
                    sourcePath = srcMoved.absolutePath,
                    destPath = File(dstDir, "photo (1).jpg").absolutePath,
                    sourceSize = srcMoved.length(),
                    resolvedDestName = "photo (1).jpg",
                    kind = OperationJournal.KIND_MOVE,
                    conflictResolution = CONFLICT_KEEP_BOTH.toString()
                ),
                JournalItem(
                    sourcePath = srcAlreadyLanded.absolutePath,
                    destPath = preCommittedDest.absolutePath,
                    sourceSize = srcAlreadyLanded.length(),
                    resolvedDestName = "done.jpg",
                    kind = OperationJournal.KIND_MOVE,
                    conflictResolution = CONFLICT_KEEP_BOTH.toString()
                )
            )
        )
        // Item 0 died in-flight before commit; item 1 landed atomically before markVerified persisted.
        OperationJournal.markInFlight(
            OperationContext(opId, 0, OperationJournal.KIND_MOVE, batch),
            AtomicStaging.fileFor(File(dstDir, "photo (1).jpg"), OperationContext(opId, 0)).absolutePath,
            resolvedDestName = "photo (1).jpg",
            actualDestPath = File(dstDir, "photo (1).jpg").absolutePath
        )

        OperationRecovery.reconcile(testContext)
        var resumed = false
        OperationRecovery.resume(testContext) { resumed = it }

        assertTrue(resumed)
        assertEquals(900L, existingCollision.length())
        assertTrue("Pinned name photo (1).jpg must be created", File(dstDir, "photo (1).jpg").exists())
        assertFalse("Must not drift to photo (2).jpg", File(dstDir, "photo (2).jpg").exists())
        assertFalse(srcMoved.exists())
        // Item 1 was reused without creating done (1).jpg and its source was deleted.
        assertFalse(srcAlreadyLanded.exists())
        assertFalse(File(dstDir, "done (1).jpg").exists())
        assertEquals(1400L, preCommittedDest.length())
        assertTrue(OperationJournal.allItems().isEmpty())
    }

    @Test
    fun resumeSkipsAlreadyFoldedSourceAlbumDuringMerge() {
        val srcAlbum = File(tempRoot, "album_src").apply { mkdirs() }
        val mergeTarget = File(tempRoot, "vault/target_album").apply { mkdirs() }
        val fileA = File(srcAlbum, "dup.jpg").apply { writeBytes(ByteArray(1024) { 4 }) }

        val opId = "merge_op_1"
        val batch = BatchOperationInfo(
            sourceRoot = srcAlbum.absolutePath,
            destRoot = mergeTarget.absolutePath,
            albumConflictResolution = "MERGE",
            isMove = true,
            wipeSource = true,
            deleteSource = true,
            mergeTargetRoot = mergeTarget.absolutePath
        )
        OperationJournal.beginBatch(
            OperationContext(opId, kind = OperationJournal.KIND_ENCRYPT, batch = batch),
            listOf(
                JournalItem(
                    sourcePath = fileA.absolutePath,
                    destPath = File(mergeTarget, "dup.jpg").absolutePath,
                    sourceSize = fileA.length(),
                    resolvedDestName = "dup.jpg",
                    kind = OperationJournal.KIND_ENCRYPT,
                    conflictResolution = CONFLICT_KEEP_BOTH.toString()
                )
            )
        )
        OperationJournal.markSourceAlbumFolded(opId, srcAlbum.absolutePath)

        var resumed = false
        OperationRecovery.resume(testContext) { resumed = it }

        assertTrue(resumed)
        // Must not re-encrypt or merge srcAlbum once marked folded.
        assertEquals(0, mergeTarget.listFiles()?.size ?: 0)
        assertTrue(fileA.exists())
        assertTrue(OperationJournal.allItems().isEmpty())
    }

    @Test
    fun selectPromptBatchSplitsPlainAndAutoWipesPhase2Encrypt() {
        val plainBatch = BatchOperationInfo(isMove = false, wipeSource = false, deleteSource = false)
        val plainCtx = OperationContext("plain_copy_1", kind = OperationJournal.KIND_COPY, batch = plainBatch)
        OperationJournal.beginBatch(
            plainCtx,
            listOf(JournalItem("/src/c.jpg", "/dst/c.jpg", 500L, kind = OperationJournal.KIND_COPY))
        )

        val srcDir = File(tempRoot, "phase2_src").apply { mkdirs() }
        val vaultSub = File(tempRoot, "vault/sub_phase2").apply { mkdirs() }
        val srcSecret = File(srcDir, "secret.jpg").apply { writeBytes(ByteArray(800) { 9 }) }
        val encBin = CryptoManager.encryptFileToDirectory(testContext, srcSecret, vaultSub, wipeSource = false)!!

        val encBatch = BatchOperationInfo(isMove = true, wipeSource = true, deleteSource = true)
        val encCtx = OperationContext("enc_phase2_1", kind = OperationJournal.KIND_ENCRYPT, batch = encBatch)
        OperationJournal.beginBatch(
            encCtx,
            listOf(JournalItem(srcSecret.absolutePath, encBin.absolutePath, srcSecret.length(), kind = OperationJournal.KIND_ENCRYPT))
        )
        OperationJournal.markVerified(encCtx.copy(itemIndex = 0), "fp_enc", encBin.absolutePath)

        // Locked vault: only the plain copy prompts; Phase-2 encrypt never prompts.
        val lockedSelection = OperationRecovery.selectPromptBatch(isVaultUnlocked = false)
        assertEquals(setOf("plain_copy_1"), lockedSelection.operationIds)
        assertEquals(OperationRecovery.PromptMode.STANDARD, lockedSelection.mode)
        assertEquals(listOf(OperationJournal.KIND_COPY), lockedSelection.distinctKinds)

        // Declining plain operation clears only plain_copy_1 permanently.
        OperationRecovery.discardOperations(lockedSelection.operationIds)
        assertTrue(OperationJournal.itemsFor("plain_copy_1").isEmpty())
        assertEquals(1, OperationJournal.itemsFor("enc_phase2_1").size)

        // Unlocked vault: Phase-2 encrypt is silently wiped in background with zero prompt needed.
        assertEquals(OperationRecovery.PromptMode.NONE, OperationRecovery.selectPromptBatch(isVaultUnlocked = true).mode)
        assertEquals(1, OperationRecovery.resumeSilentPhase2EncryptWipes(testContext))
        assertFalse("Original plaintext must be wiped automatically", srcSecret.exists())
        assertTrue("Encrypted container must stay intact", encBin.exists())
        assertTrue(OperationJournal.allItems().isEmpty())
    }

    @Test
    fun skippedCryptoDecisionBatchReopensAskDialogAndSilentDeletesApproved() {
        val srcDir = File(tempRoot, "skip_src").apply { mkdirs() }
        val vaultSub = File(tempRoot, "vault/sub_skip").apply { mkdirs() }
        val skippedFile = File(srcDir, "dup.jpg").apply { writeBytes(ByteArray(1024) { 4 }) }
        val targetBin = CryptoManager.encryptFileToDirectory(testContext, skippedFile, vaultSub, wipeSource = false)!!

        val skipOpId = OperationJournal.registerSkippedCryptoDecisionBatch(
            isEncrypt = true,
            skippedFiles = listOf(skippedFile),
            identicalTargetMap = mapOf(skippedFile.absolutePath to targetBin),
            approvedDelete = false
        )
        assertTrue(skipOpId.isNotEmpty())

        // Reconcile must preserve deferred skipped decision rows without counting them as Phase-1 interrupts.
        assertEquals(0, OperationRecovery.reconcile(testContext))
        assertEquals(OperationRecovery.PromptMode.NONE, OperationRecovery.selectPromptBatch(isVaultUnlocked = true).mode)

        // Under ASK mode, unanswered batch returns to re-open SkippedCryptoFilesDialog.
        val reopened = OperationRecovery.processPendingSkippedCryptoWork(testContext)
        assertEquals(1, reopened.size)
        assertEquals(skipOpId, reopened.single().operationId)
        assertTrue(skippedFile.exists())

        // Once Delete is approved (or setting is DELETE), next recovery wipes silently without UI dialog.
        OperationJournal.markSkippedCryptoApproved(skipOpId)
        val afterApprove = OperationRecovery.processPendingSkippedCryptoWork(testContext)
        assertTrue(afterApprove.isEmpty())
        assertFalse("Approved skipped plaintext must be wiped silently", skippedFile.exists())
        assertTrue(targetBin.exists())
        assertTrue(OperationJournal.allItems().isEmpty())
    }

    @Test
    fun transientRetryDiscardsAttemptAndRetriesAfterIOExceptionWhileActive() {
        val dstDir = File(tempRoot, "transient_dst")
        val opCtx = OperationContext("retry_op", 0, OperationJournal.KIND_COPY)
        OperationJournal.beginBatch(
            opCtx,
            listOf(JournalItem("/src/x.jpg", File(dstDir, "x.jpg").absolutePath, 100L, kind = OperationJournal.KIND_COPY))
        )

        var discardCount = 0
        val result = ForegroundOperationRegistry.withOperation(testContext, "copy-move") {
            TransientOperationWaiter.runWithTransientRetry(
                context = testContext,
                ctx = opCtx,
                destDir = dstDir,
                requiredBytes = 100L,
                maxWaitMs = 2_000L,
                onDiscardAttempt = { discardCount++ }
            ) { attempt ->
                if (attempt == 1) {
                    dstDir.mkdirs()
                    throw IOException("simulated transient write interruption")
                }
                "recovered-attempt-$attempt"
            }
        }

        assertEquals(1, discardCount)
        assertEquals("recovered-attempt-2", result)
    }

    private fun unlockTestVault() {
        val vaultDir = File(tempRoot, "vault").apply { mkdirs() }
        File(vaultDir, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        CryptoManager.setVaultRootLocation(testContext, vaultDir)
        val field = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        field.isAccessible = true
        field.set(CryptoManager, ByteArray(32) { (it + 1).toByte() })
    }

    private class InMemoryPendingOperationsDao : PendingOperationsDao {
        private val rows = ArrayList<PendingOperation>()
        private var nextId = 1L

        @Synchronized
        override fun all(): List<PendingOperation> =
            rows.sortedWith(compareBy({ it.createdAt }, { it.itemIndex })).map { it.copy() }

        @Synchronized
        override fun forOperation(operationId: String): List<PendingOperation> =
            rows.filter { it.operationId == operationId }.sortedBy { it.itemIndex }.map { it.copy() }

        @Synchronized
        override fun findItem(operationId: String, itemIndex: Int): PendingOperation? =
            rows.firstOrNull { it.operationId == operationId && it.itemIndex == itemIndex }?.copy()

        @Synchronized
        override fun operationIds(): List<String> =
            rows.sortedBy { it.createdAt }.map { it.operationId }.distinct()

        @Synchronized
        override fun activeItems(): List<PendingOperation> =
            rows.filter {
                it.state in setOf(
                    OperationJournal.STATE_PENDING,
                    OperationJournal.STATE_IN_FLIGHT,
                    OperationJournal.STATE_COPIED_UNVERIFIED,
                    OperationJournal.STATE_WAITING_FOR_SPACE
                )
            }.map { it.copy() }

        @Synchronized
        override fun resumableItems(): List<PendingOperation> =
            rows.filter {
                it.state !in setOf(
                    OperationJournal.STATE_SOURCE_DELETED,
                    OperationJournal.STATE_SKIPPED_CONFLICT,
                    OperationJournal.STATE_FAILED,
                    OperationJournal.STATE_SPACE_CHECK_FAILED
                )
            }.sortedWith(compareBy({ it.operationId }, { it.itemIndex })).map { it.copy() }

        @Synchronized
        override fun upsert(item: PendingOperation) {
            val idx = rows.indexOfFirst { it.operationId == item.operationId && it.itemIndex == item.itemIndex }
            val copy = item.copy(id = if (item.id == 0L) nextId++ else item.id)
            if (idx >= 0) rows[idx] = copy else rows.add(copy)
        }

        @Synchronized
        override fun upsertAll(items: List<PendingOperation>) {
            items.forEach { upsert(it) }
        }

        @Synchronized
        override fun updateState(
            operationId: String,
            itemIndex: Int,
            state: String,
            updatedAt: Long,
            stagingPath: String,
            sourceFingerprint: String,
            destFingerprint: String,
            message: String
        ) {
            val idx = rows.indexOfFirst { it.operationId == operationId && it.itemIndex == itemIndex }
            if (idx >= 0) {
                rows[idx] = rows[idx].copy(
                    state = state,
                    updatedAt = updatedAt,
                    stagingPath = stagingPath,
                    sourceFingerprint = sourceFingerprint,
                    destFingerprint = destFingerprint,
                    message = message
                )
            }
        }

        @Synchronized
        override fun deleteOperation(operationId: String) {
            rows.removeAll { it.operationId == operationId }
        }

        @Synchronized
        override fun clear() {
            rows.clear()
        }
    }

    private class FakeContext(private val root: File) : ContextWrapper(null) {
        private val prefsMap = LinkedHashMap<String, InMemoryPrefs>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            prefsMap.getOrPut(name ?: "default") { InMemoryPrefs() }
        override fun getFilesDir(): File = File(root, "app_files").apply { mkdirs() }
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            PackageManager.PERMISSION_GRANTED
        override fun getPackageName(): String = "org.fossify.gallery.debug"
    }

    private class InMemoryPrefs : SharedPreferences {
        private val map = LinkedHashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = REMOVED }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v === REMOVED) map.remove(k) else map[k] = v }
            }
        }
        private object REMOVED
    }
}
