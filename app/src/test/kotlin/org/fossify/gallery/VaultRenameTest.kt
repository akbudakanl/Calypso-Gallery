package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.renameEncryptedFile
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * `CryptoManager.renameEncryptedFile` — şifreli dosyanın görünen adını değiştirme.
 *
 * Bu yol payload'ı yeniden şifrelemez; yalnızca header'ı yeniden yazar ve
 * Tink ciphertext'ini aynen kopyalar. Dosya adı değiştiğinde header boyutu
 * değişebileceğinden payload ofseti kayar — test bu kaymanın sağlam olduğunu doğrular.
 */
class VaultRenameTest {

    private val kek = ByteArray(32) { (it * 13 + 2).toByte() }
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir") ?: "tmp", "vault_rename_test")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }

        for (name in listOf("unlockedMasterKey", "unlockedKek")) {
            val field = CryptoManager::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(CryptoManager, kek.copyOf())
        }
    }

    @Test
    fun renameKeepsPayloadIntactAndIntegrityValid() {
        val payload = ByteArray(3000) { (it * 7 + 1).toByte() }
        val target = File(tempDir, "original.bin")
        writeContainer(target, "first_name.jpg", payload)

        val renamed = CryptoManager.renameEncryptedFile(
            encryptedFile = target,
            newOriginalName = "second_name_with_a_longer_extension.jpg",
            skipIntegrityCheck = false,
            syncParentDir = false
        )
        assertTrue("yeniden adlandırma başarısız", renamed)

        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)
        assertNotNull("header okunamadı", meta)
        assertEquals("dosya adı güncellenmedi", "second_name_with_a_longer_extension.jpg", meta!!.originalName)
        assertEquals("boyut değişti", payload.size.toLong(), meta.originalSize)
        assertTrue("bütünlük doğrulanmadı", CryptoManager.verifyFileIntegrity(target, meta))

        val decrypted = CryptoManager.openDecryptedContentStream(target, 0L, meta)!!.use { it.readBytes() }
        assertArrayEquals("payload bozuldu", payload, decrypted)
    }

    @Test
    fun renameWithSameNameIsNoop() {
        val payload = ByteArray(512) { 4 }
        val target = File(tempDir, "same.bin")
        writeContainer(target, "same_name.jpg", payload)

        val result = CryptoManager.renameEncryptedFile(
            encryptedFile = target,
            newOriginalName = "same_name.jpg",
            skipIntegrityCheck = false,
            syncParentDir = false
        )
        assertTrue("aynı ad no-op olmalı", result)

        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)
        assertEquals("same_name.jpg", meta!!.originalName)
        assertTrue("bütünlük doğrulanmadı", CryptoManager.verifyFileIntegrity(target, meta))
    }

    @Test
    fun renameOfTamperedFileFails() {
        val payload = ByteArray(512) { 6 }
        val target = File(tempDir, "tampered.bin")
        writeContainer(target, "tampered.jpg", payload)

        val bytes = target.readBytes()
        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)!!
        val payloadStart = meta.payloadOffset.toInt()
        bytes[payloadStart + 3] = (bytes[payloadStart + 3].toInt() xor 0x01).toByte()
        target.writeBytes(bytes)

        val result = CryptoManager.renameEncryptedFile(
            encryptedFile = target,
            newOriginalName = "renamed.jpg",
            skipIntegrityCheck = false,
            syncParentDir = false
        )
        assertTrue("bozuk dosya yeniden adlandırılmamalı", !result)
    }

    private fun writeContainer(target: File, name: String, payload: ByteArray) {
        val prepared = VaultContainerWriter.prepare(
            kek = kek,
            keyId = 7,
            originalName = name,
            lastModified = 1_700_000_000_000L,
            dateTaken = 1_699_000_000_000L,
            originalSize = payload.size.toLong(),
            mediaType = 1,
            videoDuration = 0,
            thumbnail = null
        )
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            payloadProvider = { ByteArrayInputStream(payload) }
        )
    }
}
