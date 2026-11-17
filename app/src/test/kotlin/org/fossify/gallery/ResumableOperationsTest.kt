package org.fossify.gallery

import org.fossify.gallery.helpers.operations.AtomicStaging
import org.fossify.gallery.helpers.operations.EncryptedSizeInput
import org.fossify.gallery.helpers.operations.OperationContext
import org.fossify.gallery.helpers.operations.SpaceEstimator
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ADR 0003 §3a / §3 — pure, JVM-only checks of the new space/state/staging primitives. */
class ResumableOperationsTest {

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
}
