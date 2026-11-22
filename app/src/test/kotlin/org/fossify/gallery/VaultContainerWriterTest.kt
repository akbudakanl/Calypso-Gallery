package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/** Stage 2: v4 yazicinin urettigi konteyneri v4 okuyucu dogrular. */
class VaultContainerWriterTest {

    private val kek = ByteArray(32) { (it * 5 + 11).toByte() }
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "v4_writer_test")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }
        val field = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        field.isAccessible = true
        field.set(CryptoManager, kek)
        val kekField = CryptoManager::class.java.getDeclaredField("unlockedKek")
        kekField.isAccessible = true
        kekField.set(CryptoManager, kek.copyOf())
    }

    @Test
    fun writesAndReadsV4Containers() {
        listOf(0, 1, 15, 16, 17, 65_535, 65_536, 65_537, 300_000).forEach { size ->
            val payload = ByteArray(size) { (it * 17 + 3).toByte() }
            val name = "case_$size.jpg"
            val target = File(tempDir, "case_$size.bin")

            val prepared = VaultContainerWriter.prepare(
                kek = kek,
                keyId = 1,
                originalName = name,
                lastModified = 1_700_000_000_000L,
                dateTaken = 1_699_000_000_000L,
                originalSize = size.toLong(),
                mediaType = 1,
                videoDuration = 0,
                thumbnail = null
            )
            val written = VaultContainerWriter.writeContainer(
                target = target,
                prepared = prepared,
                payloadProvider = { ByteArrayInputStream(payload) }
            )
            assertEquals(size.toLong(), written)
            assertEquals(prepared.ciphertextLength, target.length() - prepared.payloadOffset)
            assertEquals(TinkStreamSpec.expectedCiphertextSize(size.toLong()), prepared.ciphertextLength)

            val meta = DataInputStream(BufferedInputStream(FileInputStream(target))).use { dis ->
                val magic = ByteArray(8)
                dis.readFully(magic)
                assertEquals(VaultContainer.VAULT_MAGIC, String(magic, Charsets.US_ASCII))
                assertEquals(VaultContainer.VAULT_FORMAT_VERSION, dis.readByte())
                // skipPrefix sonrasi readContainerMetadata dogrudan cagrilamadigi icin
                // tam okuma yolunu unlockedMasterKey uzerinden kullaniriz.
                null
            }

            val fullMeta = CryptoManager.readMetadata(target)!!
            assertTrue(fullMeta.isCanonicalFormat)
            assertEquals(name, fullMeta.originalName)
            assertEquals(size.toLong(), fullMeta.originalSize)
            assertArrayEquals(prepared.fileId, fullMeta.fileId)
            assertArrayEquals(prepared.dek, fullMeta.fileKey)
            assertEquals(prepared.payloadOffset, fullMeta.payloadOffset)
            assertEquals(TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE, fullMeta.segmentSize)

            val decrypted = CryptoManager.openDecryptedContentStream(target, 0L, fullMeta)!!.use { it.readBytes() }
            assertArrayEquals(payload, decrypted)
            assertTrue(CryptoManager.verifyFileIntegrity(target, fullMeta))
        }
    }

    @Test
    fun inPlaceHeaderRewriteKeepsContainerReadable() {
        assertEquals(27, VaultContainer.HEADER_IV_OFFSET)
        assertEquals(43, VaultContainer.FIXED_PREFIX_LENGTH)

        val payload = ByteArray(70_000) { (it * 7 + 5).toByte() }
        // Aynı uzunlukta bir ad: header uzunluğu sabit kalır, yerinde yazım güvenlidir.
        val prepared = VaultContainerWriter.prepare(
            kek = kek, keyId = 1, originalName = "a.jpg",
            lastModified = 1L, dateTaken = 2L, originalSize = payload.size.toLong(),
            mediaType = 1, videoDuration = 0, thumbnail = null
        )
        val target = File(tempDir, "in_place.bin")
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            payloadProvider = { ByteArrayInputStream(payload) }
        )

        VaultContainerWriter.rewriteHeaderInPlace(
            file = target,
            kek = kek,
            keyId = 1,
            prepared = prepared,
            originalName = "b.jpg",
            lastModified = 3L,
            dateTaken = 4L,
            originalSize = payload.size.toLong(),
            mediaType = 1,
            videoDuration = 0,
            thumbnail = null
        )

        // Eski kod IV'yi offset 43'e (header'ın kendisine) yazdığı için burada okuma
        // başarısız olur veya bozuk bir başlık döner.
        val meta = CryptoManager.readMetadata(target)!!
        assertEquals("b.jpg", meta.originalName)
        assertEquals(3L, meta.lastModified)
        assertEquals(prepared.payloadOffset, meta.payloadOffset)
        assertEquals(prepared.ciphertextLength, target.length() - prepared.payloadOffset)
        assertArrayEquals(payload, CryptoManager.openDecryptedContentStream(target, 0L, meta)!!.use { it.readBytes() })
        assertTrue(CryptoManager.verifyFileIntegrity(target, meta))
    }

    @Test
    fun rejectsTamperedV4Payload() {
        val payload = ByteArray(70_000) { (it * 3 + 9).toByte() }
        val prepared = VaultContainerWriter.prepare(
            kek = kek, keyId = 1, originalName = "tamper.jpg",
            lastModified = 1L, dateTaken = 1L, originalSize = payload.size.toLong(),
            mediaType = 1, videoDuration = 0, thumbnail = null
        )
        val target = File(tempDir, "tamper.bin")
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            payloadProvider = { ByteArrayInputStream(payload) }
        )
        val meta = CryptoManager.readMetadata(target)!!

        RandomAccessFile(target, "rw").use { raf ->
            raf.seek(meta.payloadOffset + 100L)
            val b = raf.read()
            raf.seek(meta.payloadOffset + 100L)
            raf.write(b xor 0x01)
        }
        org.junit.Assert.assertFalse(CryptoManager.verifyFileIntegrity(target, meta))
    }
}
