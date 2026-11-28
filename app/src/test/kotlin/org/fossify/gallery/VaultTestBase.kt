package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.dialogs.resolveSkippedCryptoMessageRes
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ALBUM_CRYPTO_CONFLICT_MERGE
import org.fossify.gallery.helpers.CIPHER_APP_DIR_NAME
import org.fossify.gallery.helpers.CLI_STAGING_DIR_NAME
import org.fossify.gallery.helpers.SECRETS_DIR_NAME
import org.fossify.gallery.helpers.DATE_TIE_BREAK_ASK
import org.fossify.gallery.helpers.DATE_TIE_BREAK_KEEP_TARGET
import org.fossify.gallery.helpers.DATE_TIE_BREAK_USE_SOURCE
import org.fossify.gallery.helpers.CONFLICT_HANDLING_AUTO
import org.fossify.gallery.helpers.CONFLICT_HANDLING_SMART_DIALOG
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DEFAULT_SECRET_ALBUM_NAME
import org.fossify.gallery.helpers.ENCRYPTED_BIN_EXT
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_ASK
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_DELETE
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_KEEP
import org.fossify.gallery.helpers.VAULT_DEFAULT_ALBUM_NAME
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.VAULT_SUBFOLDER_META_FILE
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
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.fossify.gallery.helpers.areEncryptedFilesIdenticalContent
import org.fossify.gallery.helpers.arePlainFilesIdentical
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.compareFilesForSmartConflict
import org.fossify.gallery.helpers.copyOrMoveEncryptedFileToDirectory
import org.fossify.gallery.helpers.createHiddenPhysicalAlbum
import org.fossify.gallery.helpers.createVaultRoot
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.decryptFolder
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.encryptFolder
import org.fossify.gallery.helpers.encryptStreamToDirectory
import org.fossify.gallery.helpers.findCandidateExternalVaultRoots
import org.fossify.gallery.helpers.findIdenticalPlainFileInDir
import org.fossify.gallery.helpers.findMergeTargetForDecryptFolder
import org.fossify.gallery.helpers.findMergeTargetForEncryptFolder
import org.fossify.gallery.helpers.getAlternativePlainFile
import org.fossify.gallery.helpers.getDecryptedFolderName
import org.fossify.gallery.helpers.getDecryptedRawFolderName
import org.fossify.gallery.helpers.getOrCreateDefaultHiddenAlbum
import org.fossify.gallery.helpers.inspectStandardFilenameConflict
import org.fossify.gallery.helpers.inspectTransferConflict
import org.fossify.gallery.helpers.isConfiguredCustomVaultPath
import org.fossify.gallery.helpers.isConfiguredVaultTemporarilyUnavailable
import org.fossify.gallery.helpers.locateVaultRootOnDisk
import org.fossify.gallery.helpers.openDecryptedContentStream
import org.fossify.gallery.helpers.prepareVaultLocationSwitch
import org.fossify.gallery.helpers.resolveExistingVaultInDirectory
import org.fossify.gallery.helpers.resolvePickedPathToFile
import org.fossify.gallery.helpers.resolveSyncSourceRoot
import org.fossify.gallery.helpers.setVaultRootLocation
import org.fossify.gallery.helpers.syncVaultFromExternalDirectory
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.helpers.verifyVaultOnDisk


abstract class VaultTestBase {

    protected lateinit var tempRoot: File
    protected lateinit var testContext: Context

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("custom_vault_test_").toFile()
        testContext = FakeVaultContext(tempRoot)
        resetCryptoManagerSingletons()
    }

    @After
    fun tearDown() {
        resetCryptoManagerSingletons()
        tempRoot.deleteRecursively()
    }

    protected fun encryptBytesForTest(
        targetDir: File,
        originalName: String,
        payload: ByteArray,
        lastModifiedOverride: Long? = null,
        dateTakenOverride: Long? = null
    ): File {
        return CryptoManager.encryptStreamToDirectory(
            context = null,
            inputStream = ByteArrayInputStream(payload),
            targetDir = targetDir,
            originalName = originalName,
            contentLength = payload.size.toLong(),
            lastModifiedOverride = lastModifiedOverride,
            dateTakenOverride = dateTakenOverride
        )!!
    }

    protected fun writeMetaV2(folder: File, masterKey: ByteArray, rawName: String) {
        val random = SecureRandom()
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
        val encryptedName = cipher.doFinal(rawName.toByteArray(Charsets.UTF_8))

        FileOutputStream(File(folder, VAULT_SUBFOLDER_META_FILE)).use { fos ->
            DataOutputStream(fos).use { dos ->
                dos.write(org.fossify.gallery.helpers.vault.VaultContainer.FOLDER_META_MAGIC.toByteArray(Charsets.US_ASCII))
                dos.write(iv)
                dos.writeInt(encryptedName.size)
                dos.write(encryptedName)
            }
        }
    }

    protected fun setMasterKey(key: ByteArray?) {
        if (key == null) {
            CryptoManager.lockVault()
        } else {
            CryptoManager.installSessionForTesting(key)
        }
    }

    protected fun resetCryptoManagerSingletons() {
        setMasterKey(null)
        CryptoManager.lockVault()
        runCatching {
            val cachedField = CryptoManager::class.java.getDeclaredField("cachedVaultRoot")
            cachedField.isAccessible = true
            cachedField.set(CryptoManager, null)

            val scanField = CryptoManager::class.java.getDeclaredField("lastDeepScanMs")
            scanField.isAccessible = true
            scanField.setLong(CryptoManager, 0L)

            val metaCacheField = CryptoManager::class.java.getDeclaredField("metadataCache")
            metaCacheField.isAccessible = true
            (metaCacheField.get(CryptoManager) as ConcurrentHashMap<*, *>).clear()

            val folderCacheField = CryptoManager::class.java.getDeclaredField("folderNameCache")
            folderCacheField.isAccessible = true
            (folderCacheField.get(CryptoManager) as ConcurrentHashMap<*, *>).clear()
        }
    }

    protected class FakeVaultContext(
        private val root: File,
        private val externalFilesDirs: Array<File?> = emptyArray()
    ) : ContextWrapper(null) {
        private val prefs = InMemorySharedPreferences()

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs

        override fun getExternalFilesDirs(type: String?): Array<File?> = externalFilesDirs

        override fun getFilesDir(): File = File(root, "app_files").apply { mkdirs() }

        override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
            return PackageManager.PERMISSION_GRANTED
        }

        override fun getPackageName(): String = "org.fossify.gallery.debug"
    }

    protected class InMemorySharedPreferences : SharedPreferences {
        private val map = LinkedHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
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
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = REMOVED }
            override fun clear() = apply { clearAll = true }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) ->
                    if (v === REMOVED) map.remove(k) else map[k] = v
                }
            }
        }

        private object REMOVED
    }
}
