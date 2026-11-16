package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * `CryptoManager.renameEncryptedFile` - sifreli dosyanin gorunen adini degistirme.
 *
 * Bu yol payload'i yeniden sifrelemez; yalnizca header'i yeniden yazar ve
 * payload'i aynen kopyalar. Dosya adi degistiginde header boyutu degisebilecegi
 * icin payload ofseti kayar - test bu kaymanin saglam oldugunu dogrular.
 */
class VaultRenameTest {

    private val masterKey = ByteArray(32) { (it * 13 + 2).toByte() }
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir") ?: "tmp", "vault_rename_test")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }

        val field = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        field.isAccessible = true
        field.set(CryptoManager, masterKey)
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
        assertTrue("yeniden adlandirma basarisiz", renamed)

        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)
        assertNotNull("header okunamadi", meta)
        assertEquals("dosya adi guncellenmedi", "second_name_with_a_longer_extension.jpg", meta!!.originalName)
        assertEquals("boyut degisti", payload.size.toLong(), meta.originalSize)

        assertTrue("MAC dogrulanmadi", CryptoManager.verifyFileIntegrity(target, meta))

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
        assertTrue("ayni ad no-op olmali", result)

        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)
        assertEquals("same_name.jpg", meta!!.originalName)
        assertTrue("MAC dogrulanmadi", CryptoManager.verifyFileIntegrity(target, meta))
    }

    @Test
    fun renameOfTamperedFileFails() {
        val payload = ByteArray(512) { 6 }
        val target = File(tempDir, "tampered.bin")
        writeContainer(target, "tampered.jpg", payload)

        // payload icindeki bir bayti boz
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
        assertTrue("bozuk dosya yeniden adlandirilmamali", !result)
    }

    // ---------------- yardimcilar ----------------

    private fun writeContainer(target: File, name: String, payload: ByteArray) {
        val header = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeUTF(name)
                dos.writeLong(1_700_000_000_000L)
                dos.writeLong(1_700_000_000_000L)
                dos.writeLong(payload.size.toLong())
                dos.writeInt(1)
                dos.writeInt(0)
                dos.writeInt(0)
            }
            baos.toByteArray()
        }

        val prepared = VaultContainerWriter.prepare(header, masterKey, VaultContainer.VERSION_SYMMETRIC_AUTH)
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            fileKey = masterKey,
            payloadProvider = { ByteArrayInputStream(payload) }
        )
    }
}
