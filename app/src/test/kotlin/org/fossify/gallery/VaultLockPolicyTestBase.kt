package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.helpers.PROTECTION_PIN
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.BiometricCipherResult
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EncryptedFileMetadata
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.VaultLockPolicy
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.appendPendingWipe
import org.fossify.gallery.helpers.applyEncryptedCachePolicy
import org.fossify.gallery.helpers.biometricUnavailableReason
import org.fossify.gallery.helpers.classifyBiometricCipherFailure
import org.fossify.gallery.helpers.deriveKeyArgon2id
import org.fossify.gallery.helpers.emptyVaultRecycleBin
import org.fossify.gallery.helpers.encryptStreamToDirectory
import org.fossify.gallery.helpers.exportEncryptedVault
import org.fossify.gallery.helpers.getPendingWipeJournalFile
import org.fossify.gallery.helpers.getVaultRecycleBin
import org.fossify.gallery.helpers.moveEncryptedFilesToVaultRecycleBin
import org.fossify.gallery.helpers.operations.ForegroundOperationRegistry
import org.fossify.gallery.helpers.permanentlyDeleteVaultRecycleBinFiles
import org.fossify.gallery.helpers.processPendingWipeJournalSync
import org.fossify.gallery.helpers.restoreVaultRecycleBinFiles
import org.fossify.gallery.helpers.secureWipeAndDeleteFile
import org.fossify.gallery.helpers.syncVaultFromExternalDirectory
import org.fossify.gallery.helpers.vault.VaultContainer
import org.fossify.gallery.helpers.writeVaultHeader
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec


abstract class VaultLockPolicyTestBase {

    protected lateinit var tempRoot: File
    protected lateinit var testContext: Context

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("vault_lock_policy_test").toFile()
        testContext = FakeContext(tempRoot)
        CryptoManager.clearKeys()
        CryptoManager.cachedVaultRoot = null
        CryptoManager.vaultRecycleBinFolder = null
        ForegroundOperationRegistry.onStateChanged = null
        VaultSessionManager.onVaultExited()
    }

    @After
    fun tearDown() {
        ForegroundOperationRegistry.onStateChanged = null
        VaultSessionManager.onVaultExited()
        CryptoManager.clearKeys()
        CryptoManager.cachedVaultRoot = null
        CryptoManager.vaultRecycleBinFolder = null
        tempRoot.deleteRecursively()
    }

    protected fun locateAppFile(relPath: String): File {
        val direct = File(relPath)
        if (direct.exists()) return direct
        val fromRoot = File("app", relPath)
        assertTrue("Missing $relPath", fromRoot.exists())
        return fromRoot
    }

    protected fun writeSyntheticVaultHeader(
        vaultDir: File,
        secret: CharArray,
        vmk: ByteArray,
        kek: ByteArray
    ) {
        val salt = ByteArray(16) { 1 }
        val pdk = CryptoManager.deriveKeyArgon2id(secret, salt, memoryKb = 19 * 1024, iterations = 2, parallelism = 1)
        val ivPdk = ByteArray(VaultContainer.GCM_IV_LENGTH) { 2 }
        val pdkCipher = Cipher.getInstance("AES/GCM/NoPadding")
        pdkCipher.init(Cipher.ENCRYPT_MODE, pdk, GCMParameterSpec(VaultContainer.GCM_TAG_BITS, ivPdk))
        val wrappedVmk = pdkCipher.doFinal(vmk)

        val keyringBytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeShort(1)
                dos.write(kek)
            }
            baos.toByteArray()
        }
        val ivVmk = ByteArray(VaultContainer.GCM_IV_LENGTH) { 3 }
        val vmkCipher = Cipher.getInstance("AES/GCM/NoPadding")
        vmkCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(vmk, "AES"), GCMParameterSpec(VaultContainer.GCM_TAG_BITS, ivVmk))
        val wrappedKeyring = vmkCipher.doFinal(keyringBytes)

        CryptoManager.writeVaultHeader(
            headerFile = File(vaultDir, VAULT_HEADER_FILE),
            salt = salt,
            memoryKb = 19 * 1024,
            iterations = 2,
            parallelism = 1,
            ivPdk = ivPdk,
            wrappedVmK = wrappedVmk,
            ivVmk = ivVmk,
            wrappedKeyring = wrappedKeyring,
            protectionType = PROTECTION_PIN
        )
    }

    protected class FakeContext(private val root: File) : ContextWrapper(null) {
        private val prefs = InMemoryPrefs()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getFilesDir(): File = File(root, "app_files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getExternalCacheDir(): File = File(root, "ext_cache").apply { mkdirs() }
        override fun getPackageName(): String = "org.fossify.gallery.debug"
    }

    protected class InMemoryPrefs : SharedPreferences {
        private val map = LinkedHashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = REMOVED }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v === REMOVED) map.remove(k) else map[k] = v }
            }
        }
        private object REMOVED
    }
}
