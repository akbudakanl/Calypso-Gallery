package org.fossify.gallery

import com.google.gson.Gson
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Python CLI tarafından üretilen kanonik konteynerların Android uygulaması
 * tarafından okunabildiğini doğrular — iki dil arasındaki format uyumunun kanıtı.
 */
class CliFixtureCompatibilityTest {

    private data class Fixture(
        val bin_len: Long,
        val plaintext_len: Long,
        val plaintext_sha256: String,
        val source_name: String
    )

    private data class Manifest(
        val kek_hex: String,
        val files: Map<String, Fixture>
    )

    private lateinit var fixturesDir: File
    private lateinit var manifest: Manifest

    @Before
    fun setUp() {
        fixturesDir = File("src/test/resources/fixtures/cli")
        manifest = Gson().fromJson(
            File(fixturesDir, "manifest.json").readText(Charsets.UTF_8),
            Manifest::class.java
        )
        val kek = manifest.kek_hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        for (name in listOf("unlockedMasterKey", "unlockedKek")) {
            val field = CryptoManager::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(CryptoManager, kek.copyOf())
        }
    }

    @Test
    fun androidReadsEveryCliGeneratedFixture() {
        manifest.files.forEach { (fileName, fixture) ->
            val file = File(fixturesDir, fileName)
            assertTrue("$fileName eksik", file.exists())
            assertEquals("$fileName boyutu", fixture.bin_len, file.length())

            val meta = CryptoManager.readMetadata(file)
            assertNotNull("$fileName header okunamadi", meta)
            meta!!
            assertEquals("$fileName adi", fixture.source_name, meta.originalName)
            assertEquals("$fileName boyutu", fixture.plaintext_len, meta.originalSize)
            assertTrue("$fileName butunluk", CryptoManager.verifyFileIntegrity(file, meta))

            val plain = CryptoManager.openDecryptedContentStream(file, 0L, meta)!!.use { it.readBytes() }
            assertEquals("$fileName cozulen uzunluk", fixture.plaintext_len.toInt(), plain.size)
            val digest = MessageDigest.getInstance("SHA-256").digest(plain)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals("$fileName/plain hash", fixture.plaintext_sha256, digest)
        }
    }
}
