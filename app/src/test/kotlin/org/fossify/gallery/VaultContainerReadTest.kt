package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.readContainerMetadata
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.helpers.vault.TinkStreamSpec
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.headerAad
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
class VaultContainerReadTest {

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
                readContainerMetadata(it, { kek }, VaultContainer.VAULT_FORMAT_VERSION, true)
            }
            assertNotNull("metadata for size $size", meta)
            meta!!
            assertEquals("case_$size.jpg", meta.originalName)
            assertEquals(size.toLong(), meta.originalSize)
            assertTrue(meta.isCanonicalFormat)
            assertArrayEquals(fileId, meta.fileId)
            assertArrayEquals(dek, meta.fileKey)
            assertEquals(
                TinkStreamSpec.expectedCiphertextSize(size.toLong()),
                meta.ciphertextLength
            )
            assertEquals(VaultContainer.payloadOffset(lastHeaderLength), meta.payloadOffset)

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
                readContainerMetadata(it, { kek }, VaultContainer.VAULT_FORMAT_VERSION, false)
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

    /** readContainerMetadata, magic+version'in once okunmus oldugunu varsayar. */
    private fun skipPrefix(dis: DataInputStream) {
        val magic = ByteArray(8)
        dis.readFully(magic)
        dis.readByte()
    }

    private fun readMeta(file: File) =
        DataInputStream(BufferedInputStream(FileInputStreamCompat(file))).use {
            skipPrefix(it)
            readContainerMetadata(it, { kek }, VaultContainer.VAULT_FORMAT_VERSION, true)!!
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
                dos.writeShort(VaultContainer.ALGORITHM_PARAMS.toInt())
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
                headerAad(
                    VaultContainer.VAULT_MAGIC.toByteArray(Charsets.US_ASCII),
                    VaultContainer.VAULT_FORMAT_VERSION,
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
            dos.write(VaultContainer.VAULT_MAGIC.toByteArray(Charsets.US_ASCII))
            dos.writeByte(VaultContainer.VAULT_FORMAT_VERSION.toInt())
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
