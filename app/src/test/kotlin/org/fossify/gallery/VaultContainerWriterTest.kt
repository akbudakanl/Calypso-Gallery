package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * [VaultContainerWriter] - yani formatin **tek yazma yolu** - testleri.
 *
 * Yazilan her konteyner, bagimsiz bir okuyucu ile dogrulanir:
 *   - CryptoManager.readMetadata()            -> header dogru cozuluyor mu
 *   - CryptoManager.verifyFileIntegrity()     -> Poly1305-AES MAC dogrulanıyor mu
 *   - CryptoManager.openDecryptedContentStream()-> duz metin birebir geliyor mu
 *
 * Ayrica bozulmus dosyalarin reddedildigi dogrulanir.
 */
class VaultContainerWriterTest {

    private val masterKey = ByteArray(32) { (it * 11 + 5).toByte() }
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir") ?: "tmp", "vault_writer_test")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }

        val field = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        field.isAccessible = true
        field.set(CryptoManager, masterKey)
    }

    @Test
    fun writesAndReadsBackPayloadsOfVariousSizes() {
        val sizes = listOf(0, 1, 15, 16, 17, 255, 4096, 65535, 65536, 65537, 300_000)

        sizes.forEach { size ->
            val payload = ByteArray(size) { (it * 31 + 7).toByte() }
            val target = File(tempDir, "case_$size.bin")

            val header = buildHeader("case_$size.jpg", size.toLong())
            val prepared = VaultContainerWriter.prepare(header, masterKey, VaultContainer.VERSION_SYMMETRIC_AUTH)
            val written = VaultContainerWriter.writeContainer(
                target = target,
                prepared = prepared,
                fileKey = masterKey,
                payloadProvider = { ByteArrayInputStream(payload) }
            )

            assertEquals("boyut $size: yazilan bayt sayisi", size.toLong(), written)

            val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)
            assertNotNull("boyut $size: header okunamadi", meta)
            assertEquals("boyut $size: ozgun boyut", size.toLong(), meta!!.originalSize)
            assertEquals("boyut $size: dosya adi", "case_$size.jpg", meta.originalName)

            assertTrue("boyut $size: MAC dogrulanmadi", CryptoManager.verifyFileIntegrity(target, meta))

            val decrypted = CryptoManager.openDecryptedContentStream(target, 0L, meta)!!.use { it.readBytes() }
            assertArrayEquals("boyut $size: duz metin farkli", payload, decrypted)
        }
    }

    @Test
    fun payloadOffsetMatchesActualFileLayout() {
        val payload = ByteArray(1000) { 1 }
        val target = File(tempDir, "offset.bin")

        val header = buildHeader("offset.jpg", payload.size.toLong())
        val prepared = VaultContainerWriter.prepare(header, masterKey, VaultContainer.VERSION_SYMMETRIC_AUTH)
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            fileKey = masterKey,
            payloadProvider = { ByteArrayInputStream(payload) }
        )

        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)!!
        // meta.payloadOffset gercek payload baslangicina isaret etmeli
        val bytes = target.readBytes()
        val realPayloadStart = meta.payloadOffset.toInt()
        assertTrue("payload ofseti dosya disina tasardi", realPayloadStart < bytes.size)
        // payload sonrasi 16 bayt MAC etiketi olmali
        assertEquals(
            "toplam boyut payload + MAC ile eslesmeli",
            bytes.size.toLong(),
            meta.payloadOffset + meta.originalSize + VaultContainer.MAC_TAG_LENGTH
        )
    }

    @Test
    fun tamperedContainerIsRejected() {
        val payload = ByteArray(4096) { 9 }
        val target = File(tempDir, "tamper.bin")

        val header = buildHeader("tamper.jpg", payload.size.toLong())
        val prepared = VaultContainerWriter.prepare(header, masterKey, VaultContainer.VERSION_SYMMETRIC_AUTH)
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            fileKey = masterKey,
            payloadProvider = { ByteArrayInputStream(payload) }
        )

        val original = target.readBytes()

        // 1) payload icindeki bir bayti boz
        val brokenPayload = original.copyOf()
        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)!!
        val payloadStart = meta.payloadOffset.toInt()
        brokenPayload[payloadStart + 10] = (brokenPayload[payloadStart + 10].toInt() xor 0x01).toByte()
        val f1 = File(tempDir, "broken_payload.bin").also { it.writeBytes(brokenPayload) }
        assertFalse("bozuk payload reddedilmeli", verify(f1))

        // 2) MAC etiketini boz
        val brokenMac = original.copyOf()
        brokenMac[brokenMac.size - 1] = (brokenMac[brokenMac.size - 1].toInt() xor 0x01).toByte()
        val f2 = File(tempDir, "broken_mac.bin").also { it.writeBytes(brokenMac) }
        assertFalse("bozuk MAC reddedilmeli", verify(f2))

        // 3) content IV'yi boz (header'in hemen ardindaki 16 bayt)
        val brokenIv = original.copyOf()
        val realIvOffset = meta.payloadOffset.toInt() - VaultContainer.CTR_IV_LENGTH
        assertTrue("content IV ofseti hesaplanmali", realIvOffset > 0)
        brokenIv[realIvOffset] = (brokenIv[realIvOffset].toInt() xor 0x01).toByte()
        val f3 = File(tempDir, "broken_iv.bin").also { it.writeBytes(brokenIv) }
        assertFalse("bozuk content IV reddedilmeli", verify(f3))
    }

    @Test
    fun headerRewriteKeepsPayloadIntact() {
        val payload = ByteArray(2048) { 3 }
        val target = File(tempDir, "rewrite.bin")

        val header = buildHeader("rewrite.jpg", payload.size.toLong())
        val prepared = VaultContainerWriter.prepare(header, masterKey, VaultContainer.VERSION_SYMMETRIC_AUTH)
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            fileKey = masterKey,
            payloadProvider = { ByteArrayInputStream(payload) }
        )

        // Ayni boyutta ama farkli dosya adi/tarih iceren bir header yaz
        // (gercek akista boyut alani gercek bayt sayisi ile guncellenir)
        val newHeader = buildHeader("renamed.jpg", payload.size.toLong())
        VaultContainerWriter.rewriteHeaderInPlace(target, newHeader, masterKey)

        val meta = CryptoManager.readMetadata(target, includeThumbnail = false, cacheResult = false)
        assertNotNull("yeniden yazilan header okunamadi", meta)
        assertEquals("dosya adi guncellenmeli", "renamed.jpg", meta!!.originalName)
        assertEquals("boyut degismemeli", payload.size.toLong(), meta.originalSize)
        assertTrue("MAC hala dogrulanmali", CryptoManager.verifyFileIntegrity(target, meta))

        val decrypted = CryptoManager.openDecryptedContentStream(target, 0L, meta)!!.use { it.readBytes() }
        assertArrayEquals("payload bozulmamali", payload, decrypted)
    }

    @Test
    fun headerWithInconsistentSizeIsRejected() {
        val payload = ByteArray(2048) { 3 }
        val target = File(tempDir, "bad_size.bin")

        val header = buildHeader("bad_size.jpg", payload.size.toLong())
        val prepared = VaultContainerWriter.prepare(header, masterKey, VaultContainer.VERSION_SYMMETRIC_AUTH)
        VaultContainerWriter.writeContainer(
            target = target,
            prepared = prepared,
            fileKey = masterKey,
            payloadProvider = { ByteArrayInputStream(payload) }
        )

        // Header'da payload ile uyumsuz bir boyut yaz
        val newHeader = buildHeader("bad_size.jpg", payload.size.toLong() + 1)
        VaultContainerWriter.rewriteHeaderInPlace(target, newHeader, masterKey)

        // Boyut tutarsizligi reddedilmeli
        assertFalse("Boyut tutarsizligi reddedilmeli", verify(target))
    }

    // ---------------- yardimcilar ----------------

    private fun verify(file: File): Boolean {
        val meta = CryptoManager.readMetadata(file, includeThumbnail = false, cacheResult = false)
            ?: return false
        return CryptoManager.verifyFileIntegrity(file, meta)
    }

    private fun buildHeader(name: String, size: Long): ByteArray =
        ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeUTF(name)
                dos.writeLong(1_700_000_000_000L)
                dos.writeLong(1_700_000_000_000L)
                dos.writeLong(size)
                dos.writeInt(1)
                dos.writeInt(0)
                dos.writeInt(0)
            }
            baos.toByteArray()
        }
}
