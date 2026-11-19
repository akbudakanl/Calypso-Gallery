package org.fossify.gallery

import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.helpers.ARGON2_HIGH_MEMORY_KB
import org.fossify.gallery.helpers.ARGON2_MIN_MEMORY_KB
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.calculateTieredArgon2Params
import org.fossify.gallery.helpers.deriveKeyArgon2id
import org.fossify.gallery.helpers.readVaultHeaderVersion
import org.fossify.gallery.helpers.readVaultProtectionType
import org.fossify.gallery.helpers.unwrapVaultKeysEphemeral
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_IV_LENGTH
import org.fossify.gallery.helpers.vault.VaultContainer.GCM_TAG_BITS
import org.fossify.gallery.helpers.writeVaultHeader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** `.vault` başlığı formatını, KDF kademeli kalibrasyonunu ve yüksek turlu açılışı doğrular. */
class VaultHeaderFormatTest {

    @Test
    fun writtenHeaderUsesCanonicalFormatVersion() {
        val header = File(System.getProperty("java.io.tmpdir"), "calypso_vault_header_test.vault")
        header.deleteOnExit()

        CryptoManager.writeVaultHeader(
            headerFile = header,
            salt = ByteArray(16) { 1 },
            memoryKb = 65_536,
            iterations = 2,
            parallelism = 2,
            ivPdk = ByteArray(12) { 2 },
            wrappedVmK = ByteArray(48) { 3 },
            ivVmk = ByteArray(12) { 4 },
            wrappedKeyring = ByteArray(48) { 5 }
        )

        assertEquals(VaultContainer.VAULT_FORMAT_VERSION.toInt(), readVaultHeaderVersion(header))
    }

    @Test
    fun vaultHeader_roundTripsStoredProtectionType() {
        val header = File.createTempFile("calypso_prot_type_", ".vault")
        header.deleteOnExit()

        listOf(PROTECTION_PIN, PROTECTION_PASSWORD).forEach { type ->
            CryptoManager.writeVaultHeader(
                headerFile = header,
                salt = ByteArray(16) { 1 },
                memoryKb = 65_536,
                iterations = 3,
                parallelism = 2,
                ivPdk = ByteArray(GCM_IV_LENGTH) { 2 },
                wrappedVmK = ByteArray(48) { 3 },
                ivVmk = ByteArray(GCM_IV_LENGTH) { 4 },
                wrappedKeyring = ByteArray(48) { 5 },
                protectionType = type
            )
            assertEquals("Header must preserve the chosen unlock method", type, readVaultProtectionType(header))
        }
    }

    @Test
    fun tieredCalibration_fastDevice_allowsSubSecondAndCapsMemoryAt128Mib() {
        val maxHeapKb = 512L * 1024L
        val std64Ms = 450

        val atFloor = calculateTieredArgon2Params(450, std64Ms, maxHeapKb)
        assertEquals(ARGON2_MIN_MEMORY_KB, atFloor.first)
        assertEquals(3, atFloor.second)
        assertFalse(atFloor.third)

        val atSubSecond = calculateTieredArgon2Params(750, std64Ms, maxHeapKb)
        assertEquals(ARGON2_MIN_MEMORY_KB, atSubSecond.first)
        assertEquals(5, atSubSecond.second)
        assertFalse(atSubSecond.third)

        val atHighTier = calculateTieredArgon2Params(1_500, std64Ms, maxHeapKb)
        assertEquals(ARGON2_HIGH_MEMORY_KB, atHighTier.first)
        assertEquals(5, atHighTier.second)
        assertFalse(atHighTier.third)

        val atMaxTarget = calculateTieredArgon2Params(7_000, std64Ms, maxHeapKb)
        assertEquals(ARGON2_HIGH_MEMORY_KB, atMaxTarget.first)
        assertEquals(23, atMaxTarget.second)
        assertFalse(atMaxTarget.third)

        val atTenSeconds = calculateTieredArgon2Params(10_000, std64Ms, maxHeapKb)
        assertEquals(ARGON2_HIGH_MEMORY_KB, atTenSeconds.first)
        assertEquals(33, atTenSeconds.second)
        assertFalse(atTenSeconds.third)
    }

    @Test
    fun tieredCalibration_midRangeDevice_enforcesRfc9106FloorAndStepsTo128Mib() {
        val maxHeapKb = 512L * 1024L
        val std64Ms = 2_400

        val belowFloor = calculateTieredArgon2Params(1_000, std64Ms, maxHeapKb)
        assertEquals(ARGON2_MIN_MEMORY_KB, belowFloor.first)
        assertEquals(3, belowFloor.second)
        assertTrue(belowFloor.third)

        val atMaxTarget = calculateTieredArgon2Params(7_000, std64Ms, maxHeapKb)
        assertEquals(ARGON2_HIGH_MEMORY_KB, atMaxTarget.first)
        assertEquals(4, atMaxTarget.second)
        assertFalse(atMaxTarget.third)

        val atTenSeconds = calculateTieredArgon2Params(10_000, std64Ms, maxHeapKb)
        assertEquals(ARGON2_HIGH_MEMORY_KB, atTenSeconds.first)
        assertEquals(6, atTenSeconds.second)
        assertFalse(atTenSeconds.third)
    }

    @Test
    fun tieredCalibration_constrainedHeapOrOom_staysAt64MibAndScalesIterations() {
        val constrainedHeapKb = 192L * 1024L
        val std64Ms = 450

        val constrained = calculateTieredArgon2Params(7_000, std64Ms, constrainedHeapKb)
        assertEquals(ARGON2_MIN_MEMORY_KB, constrained.first)
        assertEquals(47, constrained.second)
        assertTrue(constrained.third)

        val constrainedTenSeconds = calculateTieredArgon2Params(10_000, std64Ms, constrainedHeapKb)
        assertEquals(ARGON2_MIN_MEMORY_KB, constrainedTenSeconds.first)
        assertEquals(67, constrainedTenSeconds.second)
        assertTrue(constrainedTenSeconds.third)

        val oomFallback = calculateTieredArgon2Params(10_000, std64Ms, 512L * 1024L) { -1L }
        assertEquals(ARGON2_MIN_MEMORY_KB, oomFallback.first)
        assertEquals(67, oomFallback.second)
        assertTrue(oomFallback.third)
    }

    @Test
    fun highIterationVaultHeader_roundTripsAboveTenIterations() {
        verifyHeaderRoundTrip(memoryKb = ARGON2_MIN_MEMORY_KB, iterations = 45)
        verifyHeaderRoundTrip(memoryKb = ARGON2_HIGH_MEMORY_KB, iterations = 15)
    }

    private fun verifyHeaderRoundTrip(memoryKb: Int, iterations: Int) {
        val header = File.createTempFile("calypso_high_iter_", ".vault")
        header.deleteOnExit()

        val secret = "TestVaultSecret!23".toCharArray()
        val salt = ByteArray(16) { (it + 7).toByte() }
        val vmk = ByteArray(32) { (it * 3 + 1).toByte() }
        val kek = ByteArray(32) { (it * 5 + 2).toByte() }
        val ivPdk = ByteArray(GCM_IV_LENGTH) { (it + 11).toByte() }
        val ivVmk = ByteArray(GCM_IV_LENGTH) { (it + 29).toByte() }

        val pdk = CryptoManager.deriveKeyArgon2id(
            secret = secret,
            salt = salt,
            memoryKb = memoryKb,
            iterations = iterations,
            parallelism = 2
        )
        val pdkCipher = Cipher.getInstance("AES/GCM/NoPadding")
        pdkCipher.init(Cipher.ENCRYPT_MODE, pdk, GCMParameterSpec(GCM_TAG_BITS, ivPdk))
        val wrappedVmk = pdkCipher.doFinal(vmk)

        val keyringBytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeShort(1)
                dos.write(kek)
            }
            baos.toByteArray()
        }
        val vmkCipher = Cipher.getInstance("AES/GCM/NoPadding")
        vmkCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(vmk, "AES"), GCMParameterSpec(GCM_TAG_BITS, ivVmk))
        val wrappedKeyring = vmkCipher.doFinal(keyringBytes)

        CryptoManager.writeVaultHeader(
            headerFile = header,
            salt = salt,
            memoryKb = memoryKb,
            iterations = iterations,
            parallelism = 2,
            ivPdk = ivPdk,
            wrappedVmK = wrappedVmk,
            ivVmk = ivVmk,
            wrappedKeyring = wrappedKeyring
        )

        val unwrapped = CryptoManager.unwrapVaultKeysEphemeral(header, secret)
        assertNotNull("Expected header with $iterations iterations to unwrap", unwrapped)
        assertArrayEquals(vmk, unwrapped!!.vmk)
        assertArrayEquals(kek, unwrapped.kek)
        assertEquals(memoryKb, unwrapped.memoryKb)
        assertEquals(iterations, unwrapped.iterations)
    }
}
