package org.fossify.gallery

import org.fossify.gallery.helpers.media.IsolatedFailureQuarantine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolatedFailureQuarantineTest {

    @Test
    fun quarantineExpiresAfterTtlAndBoundsCapacity() {
        var now = 1_000L
        val quarantine = IsolatedFailureQuarantine(
            ttlMs = 5_000L,
            maxEntries = 2,
            nowMs = { now },
        )

        quarantine.recordFailure("bad-a")
        assertTrue(quarantine.isQuarantined("bad-a"))

        now = 5_999L
        assertTrue(quarantine.isQuarantined("bad-a"))

        now = 6_000L
        assertFalse(quarantine.isQuarantined("bad-a"))

        quarantine.recordFailure("k1")
        quarantine.recordFailure("k2")
        quarantine.recordFailure("k3")
        assertFalse(quarantine.isQuarantined("k1"))
        assertTrue(quarantine.isQuarantined("k2"))
        assertTrue(quarantine.isQuarantined("k3"))
    }
}
