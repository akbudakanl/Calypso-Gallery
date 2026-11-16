package org.fossify.gallery

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.fossify.gallery.helpers.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * CLI (tools/fossify-cipher-cli) ile uretilen .bin dosyalarinin Android tarafi
 * tarafindan okunabilmesini dogrulayan capraz implementasyon testi.
 *
 * Fixture'lar `python tools/fossify-cipher-cli/make_fixtures.py` ile uretilir ve
 * `app/src/test/resources/fixtures/cli/` altinda commit edilir.
 *
 * Bu test su yollari dener:
 *   1. Header cozulebiliyor mu (GCM + writeUTF duzeni)
 *   2. Poly1305-AES MAC dogrulaniyor mu
 *   3. Duz metin birebir geliyor mu
 *   4. Bozulmus dosyalar reddediliyor mu
 */
class CliFixtureCompatibilityTest {

    private data class Manifest(
        @SerializedName("format") val format: String = "",
        @SerializedName("version") val version: Int = 0,
        @SerializedName("mac") val mac: String = "",
        @SerializedName("master_key_hex") val masterKeyHex: String = "",
        @SerializedName("files") val files: Map<String, FileInfo> = emptyMap()
    )

    private data class FileInfo(
        @SerializedName("source_name") val sourceName: String = "",
        @SerializedName("plaintext_sha256") val plaintextSha256: String = "",
        @SerializedName("plaintext_len") val plaintextLen: Long = 0,
        @SerializedName("bin_sha256") val binSha256: String = "",
        @SerializedName("bin_len") val binLen: Long = 0
    )

    private lateinit var fixturesDir: File
    private lateinit var manifest: Manifest

    @Before
    fun setUp() {
        fixturesDir = findFixturesDir()
        val manifestFile = File(fixturesDir, "manifest.json")
        assertTrue(
            "manifest.json bulunamadi. Once `python tools/fossify-cipher-cli/make_fixtures.py` calistir.",
            manifestFile.isFile
        )

        manifest = Gson().fromJson(manifestFile.readText(), Manifest::class.java)
        assertTrue("manifest bos", manifest.files.isNotEmpty())

        setMasterKey(hexToBytes(manifest.masterKeyHex))
    }

    @Test
    fun cliFixturesAreReadableByAndroid() {
        manifest.files.forEach { (name, info) ->
            val bin = File(fixturesDir, name)
            assertTrue("${bin.name} bulunamadi", bin.isFile)

            val meta = CryptoManager.readMetadata(bin, includeThumbnail = false, cacheResult = false)
            assertNotNull("${bin.name}: header okunamadi", meta)
            assertEquals(
                "${bin.name}: duz metin boyutu farkli",
                info.plaintextLen,
                meta!!.originalSize
            )

            assertTrue(
                "${bin.name}: Poly1305-AES MAC dogrulanmadi",
                CryptoManager.verifyFileIntegrity(bin, meta)
            )

            val plaintext = CryptoManager.openDecryptedContentStream(bin, 0L, meta)!!.use { it.readBytes() }
            assertEquals("${bin.name}: duz metin uzunlugu farkli", info.plaintextLen, plaintext.size.toLong())
            assertEquals(
                "${bin.name}: duz metin sha256 farkli",
                info.plaintextSha256,
                sha256(plaintext)
            )
        }
    }

    @Test
    fun tamperedPayloadByteIsRejected() {
        val bytes = pickFixture("chunk_plus1.bin").readBytes()
        val payloadStart = payloadOffset(bytes)

        val tampered = writeTemp(
            "tampered_payload.bin",
            bytes.copyOf().also {
                it[payloadStart + 5] = (it[payloadStart + 5].toInt() xor 0x01).toByte()
            }
        )

        assertFalse("Bozulmus payload reddedilmeli", verifyTampered(tampered))
    }

    @Test
    fun tamperedMacTagIsRejected() {
        val bytes = pickFixture("chunk_plus1.bin").readBytes()
        val tampered = writeTemp(
            "tampered_mac.bin",
            bytes.copyOf().also {
                val last = it.size - 1
                it[last] = (it[last].toInt() xor 0x01).toByte()
            }
        )

        assertFalse("Bozulmus MAC reddedilmeli", verifyTampered(tampered))
    }

    @Test
    fun tamperedContentIvIsRejected() {
        val bytes = pickFixture("chunk_plus1.bin").readBytes()
        val ivOffset = contentIvOffset(bytes)

        val tampered = writeTemp(
            "tampered_iv.bin",
            bytes.copyOf().also {
                it[ivOffset] = (it[ivOffset].toInt() xor 0x01).toByte()
            }
        )

        assertFalse("Bozulmus contentIv reddedilmeli", verifyTampered(tampered))
    }

    @Test
    fun tamperedHeaderIsRejected() {
        val bytes = pickFixture("chunk_plus1.bin").readBytes()
        // Header GCM blogunun son bayti (GCM tag'inin sonu)
        val headerEnd = payloadOffset(bytes)

        val tampered = writeTemp(
            "tampered_header.bin",
            bytes.copyOf().also {
                it[headerEnd - 1] = (it[headerEnd - 1].toInt() xor 0x01).toByte()
            }
        )

        val meta = CryptoManager.readMetadata(tampered, includeThumbnail = false, cacheResult = false)
        assertNull("Bozulmus header reddedilmeli", meta)
    }

    @Test
    fun truncatedFileIsRejected() {
        val bytes = pickFixture("chunk_plus1.bin").readBytes()
        val tampered = writeTemp("truncated.bin", bytes.copyOf(bytes.size - 1))

        assertFalse("Kesilmis dosya reddedilmeli", verifyTampered(tampered))
    }

    @Test
    fun wrongMasterKeyIsRejected() {
        // Yanlis master key ile MAC dogrulanmamali
        setMasterKey(ByteArray(32) { 0x5A })
        val bin = pickFixture("chunk_plus1.bin")
        val meta = CryptoManager.readMetadata(bin, includeThumbnail = false, cacheResult = false)
        assertFalse("Yanlis anahtarla MAC dogrulanmamali", CryptoManager.verifyFileIntegrity(bin, meta))
    }

    // ---------------- yardimcilar ----------------

    private fun verifyTampered(file: File): Boolean {
        val meta = CryptoManager.readMetadata(file, includeThumbnail = false, cacheResult = false)
            ?: return false
        return CryptoManager.verifyFileIntegrity(file, meta)
    }

    private fun pickFixture(name: String): File {
        val file = File(fixturesDir, name)
        assertTrue("$name fixture'i bulunamadi", file.isFile)
        return file
    }

    private fun writeTemp(name: String, bytes: ByteArray): File {
        val dir = File(System.getProperty("java.io.tmpdir") ?: "tmp")
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    /** v3 duzeni: magic(8) + version(1) + headerIv(12) + headerLen(4) + header + contentIv(16) */
    private fun payloadOffset(bytes: ByteArray): Int = 25 + readInt(bytes, 21)

    private fun contentIvOffset(bytes: ByteArray): Int = payloadOffset(bytes)

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun setMasterKey(key: ByteArray) {
        val field = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        field.isAccessible = true
        field.set(CryptoManager, key)
    }

    private fun findFixturesDir(): File {
        val candidates = listOf(
            File("src/test/resources/fixtures/cli"),
            File("app/src/test/resources/fixtures/cli")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("fixture dizini bulunamadi: ${candidates.joinToString()}")
    }
}
