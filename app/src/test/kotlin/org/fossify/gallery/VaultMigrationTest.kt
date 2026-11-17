package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.fossify.gallery.helpers.vault.VaultMigration
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/** v3 -> v4 dosya donusumunun dogrulama oncesi/sonrasi davranisi. */
class VaultMigrationTest {

    private val vmk = ByteArray(32) { (it * 13 + 2).toByte() }
    private val kek = ByteArray(32) { (it * 7 + 9).toByte() }
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "vault_migration_test")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }
        val f = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        f.isAccessible = true
        f.set(CryptoManager, vmk)
        val kf = CryptoManager::class.java.getDeclaredField("unlockedKek")
        kf.isAccessible = true
        kf.set(CryptoManager, kek.copyOf())
    }

    @Test
    fun convertsV3FileToV4AndKeepsPayload() {
        val payload = ByteArray(70_000) { (it * 19 + 5).toByte() }
        val file = File(tempDir, "photo.bin")
        val header = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeUTF("photo.jpg")
                dos.writeLong(1_700_000_000_000L)
                dos.writeLong(1_699_000_000_000L)
                dos.writeLong(payload.size.toLong())
                dos.writeInt(1)
                dos.writeInt(0)
                dos.writeInt(0)
            }
            baos.toByteArray()
        }
        val prepared = VaultContainerWriter.prepare(header, vmk, VaultContainer.VERSION_SYMMETRIC_AUTH)
        VaultContainerWriter.writeContainer(
            target = file, prepared = prepared, fileKey = vmk,
            payloadProvider = { ByteArrayInputStream(payload) }
        )

        assertTrue(VaultMigration.convertFile(file, kek, vmk))
        val meta = CryptoManager.readMetadata(file)!!
        assertTrue(meta.isV4)
        assertEquals(VaultContainer.VERSION_TINK_STREAM, meta.version)
        assertEquals(TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE, meta.segmentSize)
        assertArrayEquals(payload, CryptoManager.openDecryptedContentStream(file)!!.use { it.readBytes() })

        // Idempotent: ikinci cagri basarili olmali ve dosyayi bozmamali.
        assertTrue(VaultMigration.convertFile(file, kek, vmk))
        assertArrayEquals(payload, CryptoManager.openDecryptedContentStream(file)!!.use { it.readBytes() })
    }

    @Test
    fun rejectsTamperedV3Source() {
        val payload = ByteArray(100) { (it * 3).toByte() }
        val file = File(tempDir, "bad.bin")
        val header = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeUTF("bad.jpg")
                dos.writeLong(1L); dos.writeLong(1L); dos.writeLong(payload.size.toLong())
                dos.writeInt(1); dos.writeInt(0); dos.writeInt(0)
            }
            baos.toByteArray()
        }
        val prepared = VaultContainerWriter.prepare(header, vmk, VaultContainer.VERSION_SYMMETRIC_AUTH)
        VaultContainerWriter.writeContainer(
            target = file, prepared = prepared, fileKey = vmk,
            payloadProvider = { ByteArrayInputStream(payload) }
        )

        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)

        assertFalse(VaultMigration.convertFile(file, kek, vmk))
        assertArrayEquals(bytes, file.readBytes())
    }
}
