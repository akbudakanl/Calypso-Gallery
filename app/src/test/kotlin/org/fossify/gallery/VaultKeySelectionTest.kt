package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * KEK rotasyonunun temeli: dosya başlığındaki keyId ile doğru KEK'in seçilmesi.
 */
class VaultKeySelectionTest {

    private val kek1 = ByteArray(32) { (it + 1).toByte() }
    private val kek2 = ByteArray(32) { (it + 40).toByte() }

    @Before
    fun setUp() {
        CryptoManager.lockVault()
        setField("unlockedMasterKey", kek1.copyOf())
        setField("unlockedKek", kek1.copyOf())
        setField("activeKeyId", 2.toShort())
        val ring = CryptoManager.unlockedKeyring
        ring.clear()
        ring[1] = kek1.copyOf()
        ring[2] = kek2.copyOf()
    }

    private fun setField(name: String, value: Any?) {
        val field = CryptoManager::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(CryptoManager, value)
    }

    private fun writeContainer(target: File, keyId: Short, kek: ByteArray) {
        val prepared = VaultContainerWriter.prepare(
            kek = kek,
            keyId = keyId,
            originalName = "rotated.jpg",
            lastModified = 1_700_000_000_000L,
            dateTaken = 1_699_000_000_000L,
            originalSize = 16L,
            mediaType = 1,
            videoDuration = 0,
            thumbnail = null
        )
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            payloadProvider = { ByteArrayInputStream(ByteArray(16) { it.toByte() }) }
        )
        assertTrue(VaultContainer.VAULT_FORMAT_VERSION == prepared.version)
    }

    @Test
    fun metadataReaderSelectsKekByFileKeyId() {
        val dir = File(System.getProperty("java.io.tmpdir"), "calypso_key_selection_test")
        dir.mkdirs()
        val file = File(dir, "key2.bin")
        writeContainer(file, keyId = 2, kek = kek2)

        // Aktif KEK yanlış olsa bile keyring'deki keyId=2 doğru anahtarı seçilmelidir.
        setField("unlockedKek", kek1.copyOf())

        val meta = CryptoManager.readMetadata(file, includeThumbnail = false, cacheResult = false)
        assertNotNull("keyId=2 ile yazılan başlık okunamadı", meta)
        assertTrue(CryptoManager.verifyFileIntegrity(file, meta))
    }

    @Test
    fun metadataReaderFallsBackToActiveKekForUnknownKeyId() {
        val dir = File(System.getProperty("java.io.tmpdir"), "calypso_key_selection_fallback_test")
        dir.mkdirs()
        val file = File(dir, "key3.bin")
        writeContainer(file, keyId = 3, kek = kek2)

        CryptoManager.unlockedKeyring.remove(3)
        setField("unlockedKek", kek2.copyOf())

        val meta = CryptoManager.readMetadata(file, includeThumbnail = false, cacheResult = false)
        assertNotNull("bilinmeyen keyId için aktif KEK fallback'i çalışmadı", meta)
        assertTrue(CryptoManager.verifyFileIntegrity(file, meta))
    }
}
