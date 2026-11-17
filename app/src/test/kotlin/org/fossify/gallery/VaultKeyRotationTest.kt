package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.writeVaultHeader
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.vault.VaultContainerWriter
import org.fossify.gallery.helpers.vault.VaultKeyRotator
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.BufferedInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** KEK rotasyonunun uçtan uca doğrulaması. */
class VaultKeyRotationTest {

    private val vmk = ByteArray(32) { (it + 7).toByte() }
    private val kek1 = ByteArray(32) { (it + 11).toByte() }

    private lateinit var root: File

    @Before
    fun setUp() {
        CryptoManager.lockVault()
        root = File(System.getProperty("java.io.tmpdir"), "calypso_kek_rotation_test")
        root.deleteRecursively()
        root.mkdirs()

        val ivPdk = ByteArray(12) { 3 }
        val wrappedVmK = ByteArray(48) { 4 }
        val ivVmk = ByteArray(12) { 5 }
        val wrappedKeyring = ByteArray(50) { 6 }
        CryptoManager.writeVaultHeader(
            File(root, VAULT_HEADER_FILE), ByteArray(16) { 1 }, 65536, 3, 2,
            ivPdk, wrappedVmK, ivVmk, wrappedKeyring
        )
        assertTrue(File(root, VAULT_HEADER_FILE).exists())

        setField("cachedVaultRoot", root)
        setField("unlockedMasterKey", vmk.copyOf())
        setField("unlockedKek", kek1.copyOf())
        setField("activeKeyId", 1.toShort())
        CryptoManager.unlockedKeyring.clear()
        CryptoManager.unlockedKeyring[1] = kek1.copyOf()
    }

    private fun setField(name: String, value: Any?) {
        val f = CryptoManager::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(CryptoManager, value)
    }

    private fun writeContainer(file: File, keyId: Short, kek: ByteArray) {
        val payload = ByteArray(3000) { (it * 7 + 1).toByte() }
        val prepared = VaultContainerWriter.prepare(
            kek = kek, keyId = keyId, originalName = "photo.jpg",
            lastModified = 1700000000000L, dateTaken = 1699000000000L,
            originalSize = payload.size.toLong(), mediaType = 1, videoDuration = 0,
            thumbnail = null
        )
        VaultContainerWriter.writeContainer(
            target = file, prepared = prepared,
            payloadProvider = { ByteArrayInputStream(payload) }
        )
    }

    @Test
    fun rotationRewritesHeadersAndKeepsPayloadsReadable() {
        val file = File(root, "photo.bin")
        writeContainer(file, keyId = 1, kek = kek1)

        val ok = VaultKeyRotator.rotate(null) { _, _ -> }
        assertTrue("rotasyon başarısız", ok)

        val meta = CryptoManager.readMetadata(file, includeThumbnail = false, cacheResult = false)
        assertNotNull(meta)
        assertEquals(2.toShort(), meta!!.keyId)
        assertEquals("photo.jpg", meta.originalName)
        assertTrue(CryptoManager.verifyFileIntegrity(file, meta))

        assertTrue(CryptoManager.unlockedKeyring.containsKey(1))
        assertTrue(CryptoManager.unlockedKeyring.containsKey(2))
        assertEquals(2.toShort(), CryptoManager.activeKeyId)

        // .vault üzerindeki keyring de yeni aktif anahtarı içermeli.
        val header = File(root, VAULT_HEADER_FILE)
        val activeId = DataInputStream(BufferedInputStream(FileInputStream(header))).use { dis ->
            dis.readFully(ByteArray(5))
            dis.readByte(); dis.readByte()
            dis.readInt(); dis.readInt(); dis.readInt()
            dis.readByte()
            val saltLen = dis.readInt()
            dis.readFully(ByteArray(saltLen))
            dis.readFully(ByteArray(12))
            val vmkLen = dis.readInt()
            dis.readFully(ByteArray(vmkLen))
            val ivVmk = ByteArray(12).also { dis.readFully(it) }
            val keyringLen = dis.readInt()
            val encKeyring = ByteArray(keyringLen).also { dis.readFully(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(vmk, "AES"), GCMParameterSpec(128, ivVmk))
            val plain = cipher.doFinal(encKeyring)
            val kDis = DataInputStream(java.io.ByteArrayInputStream(plain))
            val active = kDis.readUnsignedShort().toShort()
            val count = kDis.readUnsignedShort()
            assertEquals(2, count)
            active
        }
        assertEquals(2.toShort(), activeId)
    }

    @Test
    fun rotationIsIdempotent() {
        val file = File(root, "photo.bin")
        writeContainer(file, keyId = 1, kek = kek1)

        assertTrue(VaultKeyRotator.rotate(null))
        val firstMeta = CryptoManager.readMetadata(file, cacheResult = false)!!
        val payloadAfterFirst = CryptoManager.openDecryptedContentStream(file)!!.use { it.readBytes() }

        assertTrue("ikinci rotasyon da başarılı olmalı", VaultKeyRotator.rotate(null))
        val secondMeta = CryptoManager.readMetadata(file, cacheResult = false)!!
        val payloadAfterSecond = CryptoManager.openDecryptedContentStream(file)!!.use { it.readBytes() }
        assertTrue(payloadAfterFirst.contentEquals(payloadAfterSecond))
        assertTrue(secondMeta.keyId >= firstMeta.keyId)
    }
}
