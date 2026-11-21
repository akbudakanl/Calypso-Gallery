package org.fossify.gallery

import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.helpers.ARGON2_HIGH_MEMORY_KB
import org.fossify.gallery.helpers.ARGON2_MIN_MEMORY_KB
import org.fossify.gallery.helpers.ARGON2_ULTRA_MEMORY_KB
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DeviceMemoryCapabilities
import org.fossify.gallery.helpers.PROTECTION_PASSWORD
import org.fossify.gallery.helpers.calculateTieredArgon2Params
import org.fossify.gallery.helpers.deriveKeyArgon2id
import org.fossify.gallery.helpers.estimateVaultUnlockMs
import org.fossify.gallery.helpers.VaultKdfParams
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
    fun tieredCalibration_flagship64BitDevice_steps64To128To256Mib() {
        val flagship = DeviceMemoryCapabilities(totalRamMib = 7_600L, is64Bit = true)
        val std64Ms = 450

        val atFloor = calculateTieredArgon2Params(450, std64Ms, flagship)
        assertEquals(ARGON2_MIN_MEMORY_KB, atFloor.first)
        assertEquals(3, atFloor.second)
        assertFalse(atFloor.third)

        val atSubSecond = calculateTieredArgon2Params(750, std64Ms, flagship)
        assertEquals(ARGON2_MIN_MEMORY_KB, atSubSecond.first)
        assertEquals(5, atSubSecond.second)
        assertFalse(atSubSecond.third)

        val atHighTier = calculateTieredArgon2Params(1_500, std64Ms, flagship)
        assertEquals(ARGON2_HIGH_MEMORY_KB, atHighTier.first)
        assertEquals(5, atHighTier.second)
        assertFalse(atHighTier.third)

        val belowUltraThreshold = calculateTieredArgon2Params(2_900, std64Ms, flagship)
        assertEquals(ARGON2_HIGH_MEMORY_KB, belowUltraThreshold.first)
        assertEquals(10, belowUltraThreshold.second)
        assertFalse(belowUltraThreshold.third)

        val atUltraThreshold = calculateTieredArgon2Params(3_000, std64Ms, flagship)
        assertEquals(ARGON2_ULTRA_MEMORY_KB, atUltraThreshold.first)
        assertEquals(5, atUltraThreshold.second)
        assertFalse(atUltraThreshold.third)

        val atUltraTier = calculateTieredArgon2Params(7_200, std64Ms, flagship)
        assertEquals(ARGON2_ULTRA_MEMORY_KB, atUltraTier.first)
        assertEquals(12, atUltraTier.second)
        assertFalse(atUltraTier.third)

        val atTenSeconds = calculateTieredArgon2Params(10_000, std64Ms, flagship)
        assertEquals(ARGON2_ULTRA_MEMORY_KB, atTenSeconds.first)
        assertEquals(17, atTenSeconds.second)
        assertFalse(atTenSeconds.third)
    }

    @Test
    fun tieredCalibration_4GbAnd6Gb32BitDevices_capAutoMemoryAt128Mib() {
        val fourGb64Bit = DeviceMemoryCapabilities(totalRamMib = 3_800L, is64Bit = true)
        val sixGb32Bit = DeviceMemoryCapabilities(totalRamMib = 6_144L, is64Bit = false)
        val std64Ms = 2_400

        val belowFloor = calculateTieredArgon2Params(1_000, std64Ms, fourGb64Bit)
        assertEquals(ARGON2_MIN_MEMORY_KB, belowFloor.first)
        assertEquals(3, belowFloor.second)
        assertTrue(belowFloor.third)

        val fourGbAtTenSec = calculateTieredArgon2Params(10_000, std64Ms, fourGb64Bit)
        assertEquals(ARGON2_HIGH_MEMORY_KB, fourGbAtTenSec.first)
        assertEquals(6, fourGbAtTenSec.second)
        assertFalse(fourGbAtTenSec.third)

        val thirtyTwoBitAtTenSec = calculateTieredArgon2Params(10_000, 450, sixGb32Bit)
        assertEquals(ARGON2_HIGH_MEMORY_KB, thirtyTwoBitAtTenSec.first)
        assertEquals(33, thirtyTwoBitAtTenSec.second)
        assertFalse(thirtyTwoBitAtTenSec.third)
    }

    @Test
    fun tieredCalibration_lowRamAndOomProbeFallback_stepsDown256To128To64Mib() {
        val lowRam32Bit = DeviceMemoryCapabilities(totalRamMib = 1_024L, is64Bit = false)
        val flagship = DeviceMemoryCapabilities(totalRamMib = 7_600L, is64Bit = true)
        val std64Ms = 450

        val lowRamTenSec = calculateTieredArgon2Params(10_000, std64Ms, lowRam32Bit)
        assertEquals(ARGON2_MIN_MEMORY_KB, lowRamTenSec.first)
        assertEquals(67, lowRamTenSec.second)
        assertTrue(lowRamTenSec.third)

        val ultraOomFallback = calculateTieredArgon2Params(
            targetMs = 10_000,
            std64Ms = std64Ms,
            capabilities = flagship,
            measure128Ms = { 900L },
            measure256Ms = { -1L }
        )
        assertEquals(ARGON2_HIGH_MEMORY_KB, ultraOomFallback.first)
        assertEquals(33, ultraOomFallback.second)
        assertTrue(ultraOomFallback.third)

        val fullOomFallback = calculateTieredArgon2Params(
            targetMs = 10_000,
            std64Ms = std64Ms,
            capabilities = flagship,
            measure128Ms = { -1L },
            measure256Ms = { -1L }
        )
        assertEquals(ARGON2_MIN_MEMORY_KB, fullOomFallback.first)
        assertEquals(67, fullOomFallback.second)
        assertTrue(fullOomFallback.third)
    }

    @Test
    fun estimateVaultUnlockMs_roundTripsCalibratedParamsAndHandlesSlowerDevice() {
        val fastStd64Ms = 450
        val flagship = DeviceMemoryCapabilities(totalRamMib = 7_600L, is64Bit = true)

        val subSecond = calculateTieredArgon2Params(750, fastStd64Ms, flagship)
        assertEquals(
            750,
            estimateVaultUnlockMs(
                fastStd64Ms,
                VaultKdfParams(ARGON2_MIN_MEMORY_KB, subSecond.second, 2)
            )
        )

        val highTier = calculateTieredArgon2Params(1_500, fastStd64Ms, flagship)
        assertEquals(
            1_500,
            estimateVaultUnlockMs(
                fastStd64Ms,
                VaultKdfParams(ARGON2_HIGH_MEMORY_KB, highTier.second, 2)
            )
        )

        val ultraTier = calculateTieredArgon2Params(7_200, fastStd64Ms, flagship)
        assertEquals(
            7_200,
            estimateVaultUnlockMs(
                fastStd64Ms,
                VaultKdfParams(ARGON2_ULTRA_MEMORY_KB, ultraTier.second, 2)
            )
        )

        // Same portable vault params, but the new device is slower at the 64 MiB floor.
        val slowDeviceEstimate = estimateVaultUnlockMs(
            2_400,
            VaultKdfParams(ARGON2_HIGH_MEMORY_KB, highTier.second, 2)
        )
        assertEquals(8_000, slowDeviceEstimate)
    }

    @Test
    fun highIterationVaultHeader_roundTripsAboveTenIterations() {
        verifyHeaderRoundTrip(memoryKb = ARGON2_MIN_MEMORY_KB, iterations = 45)
        verifyHeaderRoundTrip(memoryKb = ARGON2_HIGH_MEMORY_KB, iterations = 15)
        verifyHeaderRoundTrip(memoryKb = ARGON2_ULTRA_MEMORY_KB, iterations = 4)
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
