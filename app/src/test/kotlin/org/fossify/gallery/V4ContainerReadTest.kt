package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.readV4Metadata
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.v4HeaderAad
import org.fossify.gallery.helpers.vault.VaultKeyDerivation.deriveFileHeaderKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Stage 0: v4 konteynerinin OKUNABILDIRIGINI ve dogrulandigini kanitlayan testler.
 * v4 yazici henuz yok; test owner, Tink'i dogrudan kullanarak gecerli bir v4
 * konteyner uretir ve app'in okuma yollarini bu konteyner uzerinde exerciser.
 */
class V4ContainerReadTest {

    private val kek = ByteArray(32) { (it * 7 + 3).toByte() }
    private val secureRandom = SecureRandom()
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir") ?: "tmp", "v4_read_test")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }

        // Stage 0'da KEK = VMK = legacy MEK.
        val field = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        field.isAccessible = true
        field.set(CryptoManager, kek)
    }

    @Test
    fun readsBackMetadataPayloadAndVerifiesIntegrity() {
        val sizes = listOf(0, 1, 15, 16, 17, 65_535, 65_536, 65_537, 300_000)
        sizes.forEach { size ->
            val payload = ByteArray(size) { (it * 29 + 11).toByte() }
            val file = writeV4("case_$size.jpg", payload)
            val dek = lastDek
            val fileId = lastFileId

            val meta = DataInputStream(BufferedInputStream(FileInputStreamCompat(file))).use {
                skipPrefix(it)
                readV4Metadata(it, kek, VaultContainer.VERSION_TINK_STREAM, true)
            }
            assertNotNull("metadata for size $size", meta)
            meta!!
            assertEquals("case_$size.jpg", meta.originalName)
            assertEquals(size.toLong(), meta.originalSize)
            assertTrue(meta.isV4)
            assertArrayEquals(fileId, meta.fileId)
            assertArrayEquals(dek, meta.fileKey)
            assertEquals(
                TinkStreamSpec.expectedCiphertextSize(size.toLong()),
                meta.ciphertextLength
            )
            assertEquals(VaultContainer.payloadOffsetV4(lastHeaderLength), meta.payloadOffset)

            val decrypted = CryptoManager.openDecryptedContentStream(file, 0L, meta)!!
                .use { it.readBytes() }
            assertArrayEquals(payload, decrypted)

            assertTrue("integrity for size $size", CryptoManager.verifyFileIntegrity(file, meta))
        }
    }

    @Test
    fun seekReadsFromArbitraryOffset() {
        val payload = ByteArray(200_000) { (it * 13 + 1).toByte() }
        val file = writeV4("seek.jpg", payload)
        val meta = readMeta(file)

        listOf(0L, 1L, 65_520L, 65_536L, 131_040L, 199_999L).forEach { offset ->
            val expected = payload.copyOfRange(offset.toInt(), payload.size)
            val actual = CryptoManager.openDecryptedContentStream(file, offset, meta)!!.use { it.readBytes() }
            assertArrayEquals("seek at $offset", expected, actual)
        }
    }

    /**
     * Golden v4 vektoru: sabit KEK (00..1f), 100 baytlik bilinen duz metin ve bir kez
     * uretilip depoya bakilan rastgele salt/nonce/fileId/dek iceren konteyner.
     *
     * Tink akisi randomized oldugu icin ayni girdilerden ayni sifreli metin
     * uretilmez; bu yuzden "beklenen ciphertext" yerine **sabit bir fixture**
     * saklanir ve her iki dilin de (Java/Python) bunu cozmesi gerekir.
     * Parametrelerden biri degisirse (segment boyutu, AAD, header duzeni) bu test kirilir.
     */
    @Test
    fun decryptsGoldenV4Fixture() {
        // Fixture, sinif seviyesindeki kek ile uretildi (bak: writeV4).
        val goldenKek = ByteArray(32) { (it * 7 + 3).toByte() }
        val goldenFileId = "d7a50bb70224bc023a62bd22bf03c2b4".hexToBytes()
        val goldenDek = "bf493010dcc4150bfe8ecd385d0bc17d8f954c9aba052917cd41208a34c5fd8b".hexToBytes()
        val goldenBase64 = "RkdWTFRFTkMEAAHXpQu3AiS8AjpivSK/A8K0u7XnbGLzICMJAD6qAAAAbpK/IsoSpk/3FvjMmEEHYHYHIzoJBQxln383Z9VeKVxb3gsaZryF7sCrsXyAS3SMUKG14qv6+ElqV72H5sDDxcULKSiqszZIiid4XGqR2J8ncOrcrxpmDdMdeRFXXy3jNGSMHre3QX1JRwYz+Mr0KKMVVg5LvTht++TnHAN4cSVNR/xqqzKudxwXs69fpA+xAAiCdSVt23WRnNierCnxnbYe/iuHSXieL5C4p0HIph8jiRDno77rmiQOCr4UhOoC0D3s78HJjeJdl0gpWKG9G5zh8MvrBzGCGCm+NL8cTv4vPOzu9WNWUIwp67+L0YTmjxnLMCjPX5CmL4gqD4JChFk/geIm92Ke250v"

        val file = File(tempDir, "golden_v4.bin")
        file.writeBytes(java.util.Base64.getDecoder().decode(goldenBase64))

        val meta = DataInputStream(BufferedInputStream(FileInputStreamCompat(file))).use {
            skipPrefix(it)
            readV4Metadata(it, goldenKek, VaultContainer.VERSION_TINK_STREAM, true)
        }
        assertNotNull(meta)
        meta!!
        assertArrayEquals(goldenFileId, meta.fileId)
        assertArrayEquals(goldenDek, meta.fileKey)
        assertEquals(100L, meta.originalSize)
        assertEquals("golden.jpg", meta.originalName)
        assertEquals(TinkStreamSpec.expectedCiphertextSize(100L), meta.ciphertextLength)

        val expected = ByteArray(100) { (it * 17 + 3).toByte() }
        val decrypted = CryptoManager.openDecryptedContentStream(file, 0L, meta)!!.use { it.readBytes() }
        assertArrayEquals(expected, decrypted)
        assertTrue(CryptoManager.verifyFileIntegrity(file, meta))
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun rejectsTamperedPayloadTagAndTruncation() {
        val payload = ByteArray(70_000) { (it * 3 + 9).toByte() }
        val base = writeV4("tamper.jpg", payload)

        // 1) payload bit flip -> verify fails
        val tampered = File(tempDir, "tampered.bin")
        base.copyTo(tampered, overwrite = true)
        RandomAccessFile(tampered, "rw").use { raf ->
            raf.seek(readMeta(base).payloadOffset + 100L)
            val b = raf.read()
            raf.seek(readMeta(base).payloadOffset + 100L)
            raf.write(b xor 0x01)
        }
        assertFalse(CryptoManager.verifyFileIntegrity(tampered, readMeta(tampered)))

        // 2) header GCM bit flip -> metadata unreadable
        val badHeader = File(tempDir, "bad_header.bin")
        base.copyTo(badHeader, overwrite = true)
        RandomAccessFile(badHeader, "rw").use { raf ->
            raf.seek(12L) // headerIv icinde bir bit
            val b = raf.read()
            raf.seek(12L)
            raf.write(b xor 0x01)
        }
        assertEquals(
            null,
            DataInputStream(BufferedInputStream(FileInputStreamCompat(badHeader))).use {
                skipPrefix(it)
                readV4Metadata(it, kek, VaultContainer.VERSION_TINK_STREAM, false)
            }
        )

        // 3) truncation -> strict length check fails at metadata level
        val truncated = File(tempDir, "truncated.bin")
        base.copyTo(truncated, overwrite = true)
        RandomAccessFile(truncated, "rw").use { it.setLength(it.length() - 16) }
        assertFalse(CryptoManager.verifyFileIntegrity(truncated, readMeta(truncated)))
    }

    // ------------------------------------------------------------------ helpers

    private lateinit var lastDek: ByteArray
    private lateinit var lastFileId: ByteArray
    private var lastHeaderLength: Int = 0

    private fun FileInputStreamCompat(file: File) = java.io.FileInputStream(file)

    /** readV4Metadata, magic+version'in once okunmus oldugunu varsayar. */
    private fun skipPrefix(dis: DataInputStream) {
        val magic = ByteArray(8)
        dis.readFully(magic)
        dis.readByte()
    }

    private fun readMeta(file: File) =
        DataInputStream(BufferedInputStream(FileInputStreamCompat(file))).use {
            skipPrefix(it)
            readV4Metadata(it, kek, VaultContainer.VERSION_TINK_STREAM, true)!!
        }

    /** Gecerli bir v4 konteyner yazar (uretme amacli test helper'i). */
    private fun writeV4(originalName: String, payload: ByteArray): File {
        val dek = ByteArray(TinkStreamSpec.KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val fileId = ByteArray(VaultContainer.FILE_ID_LENGTH).also { secureRandom.nextBytes(it) }
        val keyId: Short = 1
        val headerIv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }

        val ciphertextLength = TinkStreamSpec.expectedCiphertextSize(payload.size.toLong())
        val headerPlain = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeShort(VaultContainer.ALGORITHM_PARAMS_V4.toInt())
                dos.writeInt(TinkStreamSpec.CIPHERTEXT_SEGMENT_SIZE)
                dos.writeLong(ciphertextLength)
                dos.write(dek)
                dos.writeUTF(originalName)
                dos.writeLong(1_700_000_000_000L) // lastModified
                dos.writeLong(1_699_000_000_000L) // dateTaken
                dos.writeLong(payload.size.toLong())
                dos.writeInt(1) // mediaType: image
                dos.writeInt(0) // videoDuration
                dos.writeInt(0) // thumbnail length
            }
            baos.toByteArray()
        }

        val headerKey = deriveFileHeaderKey(kek, fileId)
        // AAD'deki N = SIFRELI header uzunlugu (duz metin + 16 bayt GCM tag).
        val encHeaderLen = headerPlain.size + 16
        val headerCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(headerKey, "AES"),
                GCMParameterSpec(VaultContainer.GCM_TAG_BITS, headerIv)
            )
            updateAAD(
                v4HeaderAad(
                    VaultContainer.MAGIC_V2.toByteArray(Charsets.US_ASCII),
                    VaultContainer.VERSION_TINK_STREAM,
                    keyId,
                    fileId,
                    encHeaderLen
                )
            )
        }
        val encHeader = headerCipher.doFinal(headerPlain)
        check(encHeader.size == encHeaderLen)

        val file = File(tempDir, "${originalName.replace('.', '_')}_${payload.size}.bin")
        FileOutputStream(file).use { fos ->
            val dos = DataOutputStream(fos)
            dos.write(VaultContainer.MAGIC_V2.toByteArray(Charsets.US_ASCII))
            dos.writeByte(VaultContainer.VERSION_TINK_STREAM.toInt())
            dos.writeShort(keyId.toInt())
            dos.write(fileId)
            dos.write(headerIv)
            dos.writeInt(encHeader.size)
            dos.write(encHeader)
            dos.flush() // kapatma: Tink ayni fos uzerine yazmaya devam edecek

            val aead = TinkStreamSpec.streamingAead(dek)
            aead.newEncryptingStream(fos, fileId).use { out ->
                ByteArrayInputStream(payload).use { input ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                    }
                }
            }
            // Tink'in encrypting stream'i kapanirsa fos'u da kapatabilir; testte fsync gerekli degil.
        }

        lastDek = dek
        lastFileId = fileId
        lastHeaderLength = encHeader.size
        return file
    }
}
