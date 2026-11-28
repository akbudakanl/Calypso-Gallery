package org.fossify.gallery

import org.fossify.gallery.helpers.CliBridgeManager
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.NativeVaultSession
import org.fossify.gallery.helpers.decryptStringFromVault
import org.fossify.gallery.helpers.encryptStreamToDirectory
import org.fossify.gallery.helpers.encryptStringForVault
import org.fossify.gallery.helpers.getDecryptedRawFolderName
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.renameEncryptedFile
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.vault.VaultKeyDerivation
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.helpers.writeEncryptedFolderMeta
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ADR 0010 security contract and cross-implementation parity tests for the Rust-native
 * vault session (`VMK` / `KEK` isolation, zero DEK caching, biometric-only VMK export,
 * and byte-for-byte compatibility with JCA / Tink).
 */
class RustVaultSessionPolicyTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        CryptoManager.lockVault()
        tempDir = Files.createTempDirectory("rust_vault_policy_test").toFile()
    }

    @After
    fun tearDown() {
        CryptoManager.lockVault()
        tempDir.deleteRecursively()
    }

    private fun locateRepoFile(relPath: String): File {
        val direct = File(relPath)
        if (direct.exists()) return direct
        val fromParent = File("..", relPath)
        assertTrue("Missing $relPath", fromParent.exists())
        return fromParent
    }

    @Test
    fun jvmHeapDoesNotRetainMasterKeysOrPerFileDeks() {
        val cryptoManagerSrc = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/CryptoManager.kt"
        ).readText()
        assertFalse(cryptoManagerSrc.contains("unlockedMasterKey"))
        assertFalse(cryptoManagerSrc.contains("unlockedKek"))
        assertFalse(cryptoManagerSrc.contains("getMasterKeyCopy"))
        assertFalse(cryptoManagerSrc.contains("val fileKey"))
        assertTrue(cryptoManagerSrc.contains("NativeVaultSession.isSessionUnlockedNative()"))
        assertTrue(cryptoManagerSrc.contains("NativeVaultSession.lockSessionNative()"))
    }

    @Test
    fun testSessionInstallerIsNeverCalledInProductionSources() {
        val mainDir = locateRepoFile("app/src/main/kotlin")
        mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                if (file.name != "CryptoManager.kt" && file.name != "NativeVaultSession.kt") {
                    assertFalse(
                        "${file.name} must never call installSessionForTesting",
                        text.contains("installSessionForTesting")
                    )
                    assertFalse(
                        "${file.name} must never call installSessionKeysForTestNative",
                        text.contains("installSessionKeysForTestNative")
                    )
                }
            }
    }

    @Test
    fun biometricVmkExportIsStrictlyScopedAndZeroedInFinally() {
        val mainDir = locateRepoFile("app/src/main/kotlin")
        mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                if (file.name != "CryptoManagerBiometrics.kt" && file.name != "NativeVaultSession.kt") {
                    assertFalse(
                        "${file.name} must never call exportVmkForBiometricWrapNative",
                        text.contains("exportVmkForBiometricWrapNative")
                    )
                }
            }

        val biometricsSrc = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerBiometrics.kt"
        ).readText()
        assertTrue(biometricsSrc.contains("exportVmkForBiometricWrapNative()"))
        assertTrue(biometricsSrc.contains("masterKey.fill(0)"))
        assertTrue(biometricsSrc.contains("vmk?.fill(0)"))
    }

    @Test
    fun ephemeralFileDeksAreZeroedInFinallyAndRustEnablesZeroize() {
        val streamsSrc = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerStreams.kt"
        ).readText()
        assertTrue(streamsSrc.contains("unwrapFileDekNative"))
        assertTrue(streamsSrc.contains("dek.fill(0)"))

        val fileCryptoSrc = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerFileCrypto.kt"
        ).readText()
        assertTrue(fileCryptoSrc.contains("prepared.wipe()"))

        val cargoToml = locateRepoFile("rust/calypso-argon2/Cargo.toml").readText()
        assertTrue(cargoToml.contains("aes-gcm") && cargoToml.contains("zeroize"))
        assertTrue(cargoToml.contains("argon2") && cargoToml.contains("zeroize"))

        val sessionRs = locateRepoFile("rust/calypso-argon2/src/session.rs").readText()
        assertTrue(sessionRs.contains("#[derive(Zeroize, ZeroizeOnDrop)]"))
    }

    @Test
    fun rustVaultSessionMatchesJcaPrimitivesAndInvalidatesOnLock() {
        val vmk = ByteArray(32) { (it * 7 + 1).toByte() }
        val kek = ByteArray(32) { (it * 11 + 3).toByte() }
        CryptoManager.installSessionForTesting(vmk, kek, keyId = 1)
        assertTrue(CryptoManager.isVaultUnlocked)

        // 1. CLI bridge HMAC-SHA256 fingerprint parity
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(kek, "HmacSHA256"))
        }
        val expectedFp = mac.doFinal("calypso-cli-bridge".toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        assertEquals(expectedFp, CliBridgeManager.currentFingerprint())

        // 2. FVSTR: double-prefix HKDF parity with Kotlin VaultKeyDerivation + JCA AES-GCM
        val encStr = CryptoManager.encryptStringForVault(null, "Stealth Album", "album")
        assertTrue(encStr.startsWith(VaultContainer.VAULT_STR_PREFIX))
        assertEquals("Stealth Album", CryptoManager.decryptStringFromVault(encStr, "album"))
        val raw = Base64.getDecoder().decode(encStr.removePrefix(VaultContainer.VAULT_STR_PREFIX))
        DataInputStream(raw.inputStream()).use { dis ->
            dis.readByte()
            dis.readUnsignedShort()
            val purpose = dis.readUTF()
            val iv = ByteArray(VaultContainer.GCM_IV_LENGTH).also { dis.readFully(it) }
            val len = dis.readInt()
            val ct = ByteArray(len).also { dis.readFully(it) }
            val ctx = "CalypsoGallery:Metadata:AESGCM|$purpose"
            val jcaKey = VaultKeyDerivation.deriveMetadataKey(kek, ctx)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(jcaKey, "AES"), GCMParameterSpec(128, iv))
                    updateAAD(ctx.toByteArray(Charsets.UTF_8))
                }
                assertEquals("Stealth Album", String(cipher.doFinal(ct), Charsets.UTF_8))
            } finally {
                jcaKey.fill(0)
            }
        }

        // 3. Folder .meta round-trip
        val albumDir = File(tempDir, "abc123").apply { mkdirs() }
        CryptoManager.writeEncryptedFolderMeta(
            context = VaultLockPolicyTestContext(tempDir),
            folder = albumDir,
            originalName = "My Secret Folder"
        )
        CryptoManager.folderNameCache.clear()
        assertEquals("My Secret Folder", CryptoManager.getDecryptedRawFolderName(albumDir))

        // 4. Container write + zero-DEK rename + integrity
        val payload = ByteArray(48 * 1024) { (it * 5 + 9).toByte() }
        val bin = CryptoManager.encryptStreamToDirectory(
            context = null,
            inputStream = ByteArrayInputStream(payload),
            targetDir = albumDir,
            originalName = "before.jpg",
            contentLength = payload.size.toLong()
        )!!
        assertTrue(CryptoManager.renameEncryptedFile(bin, "after_longer_name.jpg"))
        assertTrue(CryptoManager.verifyFileIntegrity(bin))
        assertArrayEquals(payload, CryptoManager.openDecryptedContentStream(bin)!!.use { it.readBytes() })

        // 5. Locking wipes Rust session and blocks all subsequent decrypts
        CryptoManager.lockVault()
        assertFalse(CryptoManager.isVaultUnlocked)
        assertNull(CliBridgeManager.currentFingerprint())
        assertNull(CryptoManager.decryptStringFromVault(encStr, "album"))
        assertNull(CryptoManager.getDecryptedRawFolderName(albumDir))
        assertNull(CryptoManager.readMetadata(bin))
        assertNull(CryptoManager.openDecryptedContentStream(bin))
        assertFalse(CryptoManager.verifyFileIntegrity(bin))
    }

    private class VaultLockPolicyTestContext(root: File) : android.content.ContextWrapper(null) {
        private val files = File(root, "files").apply { mkdirs() }
        override fun getFilesDir(): File = files
        override fun getApplicationContext(): android.content.Context = this
    }
}
