package org.fossify.gallery

import android.content.Context
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.encryptStringForVault
import org.fossify.gallery.helpers.decryptStringFromVault
import org.fossify.gallery.helpers.vault.VaultContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** v4 metadata (FVSTR2): kayda ozel HKDF anahtari + purpose AAD. */
class VaultMetadataTest {

    private val vmk = ByteArray(32) { (it * 3 + 1).toByte() }
    private val kek = ByteArray(32) { (it * 5 + 7).toByte() }
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "v4_meta_test")
        tempDir.mkdirs()
        tempDir.listFiles()?.forEach { it.delete() }
        File(tempDir, VAULT_HEADER_FILE).writeBytes(
            VaultContainer.VAULT_HEADER_MAGIC.toByteArray(Charsets.US_ASCII) +
                byteArrayOf(VaultContainer.VAULT_FORMAT_VERSION)
        )
        setField("unlockedMasterKey", vmk)
        setField("unlockedKek", kek.copyOf())
        setField("cachedVaultRoot", tempDir)
    }

    private fun setField(name: String, value: Any?) {
        val f = CryptoManager::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(CryptoManager, value)
    }

    private val ctx: Context? = null

    @Test
    fun roundTripAndPurposeBinding() {
        assertEquals(VaultContainer.VAULT_FORMAT_VERSION.toInt(), CryptoManager.getVaultFormat())

        val albumName = CryptoManager.encryptStringForVault(ctx, "Secret Album", "album")
        assertTrue(albumName.startsWith(VaultContainer.VAULT_STR_PREFIX))
        assertEquals("Secret Album", CryptoManager.decryptStringFromVault(albumName))

        val albumJson = CryptoManager.encryptStringForVault(ctx, "{\"name\":\"x\"}", "album_json")
        assertEquals("{\"name\":\"x\"}", CryptoManager.decryptStringFromVault(albumJson))

        // Purpose, kaydın içinde açıkta olduğu için decrypt kendi bağlamını okur;
        // değiştirilen purpose testi aşağıda:
        val raw = java.util.Base64.getDecoder().decode(albumName.removePrefix(VaultContainer.VAULT_STR_PREFIX))
        val tampered = raw.clone()
        val purposeIdx = 3 // version(1) + keyId(2)
        tampered[purposeIdx] = (tampered[purposeIdx].toInt() xor 1).toByte()
        val forged = VaultContainer.VAULT_STR_PREFIX + java.util.Base64.getEncoder().encodeToString(tampered)
        assertFalse(CryptoManager.decryptStringFromVault(forged) == "Secret Album")
    }
}
