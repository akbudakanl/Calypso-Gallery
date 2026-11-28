package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CIPHER_APP_DIR_NAME
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.CryptoManager.AlbumConflictResolution
import org.fossify.gallery.helpers.CryptoManager.AlbumCryptoOptions
import org.fossify.gallery.helpers.DEFAULT_SECRET_ALBUM_NAME
import org.fossify.gallery.helpers.ENCRYPTED_BIN_EXT
import org.fossify.gallery.helpers.EncryptedFolderOrigin
import org.fossify.gallery.helpers.SECRETS_DIR_NAME
import org.fossify.gallery.helpers.VAULT_DEFAULT_ALBUM_NAME
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.VAULT_SUBFOLDER_META_FILE
import org.fossify.gallery.helpers.VAULT_SUBFOLDER_ORIGIN_FILE
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.createHiddenPhysicalAlbum
import org.fossify.gallery.helpers.decryptFolder
import org.fossify.gallery.helpers.encryptFolder
import org.fossify.gallery.helpers.encryptStreamToDirectory
import org.fossify.gallery.helpers.findMergeTargetForDecryptFolder
import org.fossify.gallery.helpers.getDecryptedFolderName
import org.fossify.gallery.helpers.getOrCreateDefaultHiddenAlbum
import org.fossify.gallery.helpers.vault.VaultContainer.VAULT_STR_PREFIX
import org.fossify.gallery.helpers.previewDecryptAlbumConflict
import org.fossify.gallery.helpers.readEncryptedFolderOrigin
import org.fossify.gallery.helpers.renameEncryptedFolder
import org.fossify.gallery.helpers.resolveDecryptTargetInPickedDirectory
import org.fossify.gallery.helpers.resolveOriginDecryptTargetFolder
import org.fossify.gallery.helpers.setVaultRootLocation
import org.fossify.gallery.helpers.writeEncryptedFolderMeta
import org.fossify.gallery.helpers.syncVaultFromExternalDirectory
import org.fossify.gallery.helpers.writeEncryptedFolderOrigin
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

class EncryptedFolderOriginTest {

    private lateinit var tempRoot: File
    private lateinit var internalRoot: File
    private lateinit var vaultRoot: File
    private lateinit var testContext: Context

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("folder_origin_test_").toFile()
        internalRoot = File(tempRoot, "internal").apply { mkdirs() }
        vaultRoot = File(internalRoot, "$CIPHER_APP_DIR_NAME/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        testContext = FakeVaultContext(tempRoot)
        resetCryptoManagerSingletons()
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)
        testContext.config.internalStoragePath = internalRoot.absolutePath
    }

    @After
    fun tearDown() {
        resetCryptoManagerSingletons()
        tempRoot.deleteRecursively()
    }

    @Test
    fun internalFolderRoundTrip_restoresToOriginalParentInsteadOfPictures() {
        val payload = ByteArray(4096).also { SecureRandom().nextBytes(it) }
        val cameraDir = File(internalRoot, "DCIM/Camera").apply { mkdirs() }
        File(cameraDir, "IMG_001.jpg").writeBytes(payload)

        val encryptedPath = CryptoManager.encryptFolder(testContext, cameraDir.absolutePath)
        assertNotNull(encryptedPath)
        val encDir = File(encryptedPath!!)
        assertFalse(cameraDir.exists())

        val originFile = File(encDir, VAULT_SUBFOLDER_ORIGIN_FILE)
        assertTrue(originFile.isFile)
        val rawOriginText = originFile.readText(Charsets.UTF_8)
        assertTrue(rawOriginText.startsWith(VAULT_STR_PREFIX))
        assertFalse(rawOriginText.contains("DCIM"))
        assertFalse(rawOriginText.contains("Camera"))

        val decryptedPath = CryptoManager.decryptFolder(testContext, encDir.absolutePath)
        assertNotNull(decryptedPath)
        assertEquals(cameraDir.canonicalPath, File(decryptedPath!!).canonicalPath)
        assertFalse(File(internalRoot, "Pictures/Camera").exists())
        assertArrayEquals(payload, File(cameraDir, "IMG_001.jpg").readBytes())
    }

    @Test
    fun conflictAtOriginalLocation_supportsPreviewMergeAndRename() {
        val random = SecureRandom()
        val oldPayload = ByteArray(4096).also { random.nextBytes(it) }
        val newPayload = ByteArray(4096).also { random.nextBytes(it) }

        val cameraDir = File(internalRoot, "DCIM/Camera").apply { mkdirs() }
        File(cameraDir, "old.jpg").writeBytes(oldPayload)

        val encDir = File(CryptoManager.encryptFolder(testContext, cameraDir.absolutePath)!!)
        assertFalse(cameraDir.exists())

        // Simulate user taking a new photo, recreating DCIM/Camera
        cameraDir.mkdirs()
        File(cameraDir, "new.jpg").writeBytes(newPayload)

        val preview = CryptoManager.previewDecryptAlbumConflict(testContext, encDir.absolutePath)
        assertNotNull(preview)
        assertEquals("Camera", preview!!.existingName)
        assertEquals("Camera(1)", preview.renamedPreview)
        assertEquals(cameraDir.canonicalPath, File(preview.targetPath!!).canonicalPath)

        val mergeProbe = CryptoManager.findMergeTargetForDecryptFolder(testContext, encDir.absolutePath)
        assertNotNull(mergeProbe)
        assertEquals(cameraDir.canonicalPath, mergeProbe!!.first.canonicalPath)

        // RENAME decrypt -> DCIM/Camera(1)
        val renamedOut = CryptoManager.decryptFolder(
            context = testContext,
            folderPath = encDir.absolutePath,
            options = AlbumCryptoOptions(resolution = AlbumConflictResolution.RENAME)
        )
        assertNotNull(renamedOut)
        val cameraCopy = File(internalRoot, "DCIM/Camera(1)")
        assertEquals(cameraCopy.canonicalPath, File(renamedOut!!).canonicalPath)
        assertArrayEquals(oldPayload, File(cameraCopy, "old.jpg").readBytes())
        assertArrayEquals(newPayload, File(cameraDir, "new.jpg").readBytes())

        // Encrypt DCIM/Camera(1) again and test MERGE into a fresh DCIM/Camera(1)
        val encDir2 = File(CryptoManager.encryptFolder(testContext, cameraCopy.absolutePath)!!)
        cameraCopy.mkdirs()
        val extraPayload = ByteArray(2048).also { random.nextBytes(it) }
        File(cameraCopy, "extra.jpg").writeBytes(extraPayload)

        val mergedOut = CryptoManager.decryptFolder(
            context = testContext,
            folderPath = encDir2.absolutePath,
            options = AlbumCryptoOptions(resolution = AlbumConflictResolution.MERGE)
        )
        assertEquals(cameraCopy.canonicalPath, File(mergedOut!!).canonicalPath)
        assertArrayEquals(oldPayload, File(cameraCopy, "old.jpg").readBytes())
        assertArrayEquals(extraPayload, File(cameraCopy, "extra.jpg").readBytes())
    }

    @Test
    fun renamedAlbumInsideVault_decryptsUnderOriginalParentWithNewName() {
        val payload = ByteArray(2048).also { SecureRandom().nextBytes(it) }
        val cameraDir = File(internalRoot, "DCIM/Camera").apply { mkdirs() }
        File(cameraDir, "shot.jpg").writeBytes(payload)

        val encDir = File(CryptoManager.encryptFolder(testContext, cameraDir.absolutePath)!!)
        assertTrue(CryptoManager.renameEncryptedFolder(testContext, encDir, "OldCamera"))

        val decryptedPath = CryptoManager.decryptFolder(testContext, encDir.absolutePath)
        val expected = File(internalRoot, "DCIM/OldCamera")
        assertEquals(expected.canonicalPath, File(decryptedPath!!).canonicalPath)
        assertArrayEquals(payload, File(expected, "shot.jpg").readBytes())
    }

    @Test
    fun topLevelInternalFolder_andRelocatedInternalRoot_restoreCorrectly() {
        val payload = ByteArray(2048).also { SecureRandom().nextBytes(it) }
        val topLevel = File(internalRoot, "Travel").apply { mkdirs() }
        File(topLevel, "map.jpg").writeBytes(payload)

        val encDir = File(CryptoManager.encryptFolder(testContext, topLevel.absolutePath)!!)
        assertFalse(topLevel.exists())

        // Simulate vault moved to a new internal storage root (e.g. new device / secondary user profile)
        val newInternalRoot = File(tempRoot, "internal_new_device").apply { mkdirs() }
        testContext.config.internalStoragePath = newInternalRoot.absolutePath

        val decryptedPath = CryptoManager.decryptFolder(testContext, encDir.absolutePath)
        val expected = File(newInternalRoot, "Travel")
        assertEquals(expected.canonicalPath, File(decryptedPath!!).canonicalPath)
        assertArrayEquals(payload, File(expected, "map.jpg").readBytes())
    }

    @Test
    fun exceptions_returnNullOriginInsteadOfSilentPicturesFallback() {
        val random = SecureRandom()
        val picturesDir = File(internalRoot, "Pictures")

        // 1. Vault-native album created via createHiddenPhysicalAlbum
        val nativeAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "VaultNative")!!
        val p1 = ByteArray(1024).also { random.nextBytes(it) }
        encryptBytesForTest(nativeAlbum, "n1.jpg", p1)
        assertNull(CryptoManager.resolveOriginDecryptTargetFolder(testContext, nativeAlbum))
        assertNull(CryptoManager.decryptFolder(testContext, nativeAlbum.absolutePath))
        assertFalse(File(picturesDir, "VaultNative").exists())

        // 2. Default Secret Album
        val defaultAlbum = CryptoManager.getOrCreateDefaultHiddenAlbum(testContext)!!
        val p2 = ByteArray(1024).also { random.nextBytes(it) }
        encryptBytesForTest(defaultAlbum, "d1.jpg", p2)
        assertNull(CryptoManager.resolveOriginDecryptTargetFolder(testContext, defaultAlbum))
        assertNull(CryptoManager.decryptFolder(testContext, defaultAlbum.absolutePath))
        assertFalse(File(picturesDir, DEFAULT_SECRET_ALBUM_NAME).exists())

        // 3. Legacy album (.meta only, no .origin)
        val legacyAlbum = File(vaultRoot, "leg123").apply { mkdirs() }
        CryptoManager.writeEncryptedFolderMeta(testContext, legacyAlbum, "LegacyAlbum")
        val p3 = ByteArray(1024).also { random.nextBytes(it) }
        encryptBytesForTest(legacyAlbum, "l1.jpg", p3)
        assertNull(CryptoManager.resolveOriginDecryptTargetFolder(testContext, legacyAlbum))
        assertNull(CryptoManager.decryptFolder(testContext, legacyAlbum.absolutePath))
        assertFalse(File(picturesDir, "LegacyAlbum").exists())

        // 4. CLI / external-synced album -> user picks destination via picker helper
        val cliSourceRoot = File(tempRoot, "cli_sync_src").apply { mkdirs() }
        val cliSub = File(cliSourceRoot, "cli001").apply { mkdirs() }
        CryptoManager.writeEncryptedFolderMeta(testContext, cliSub, "CliSynced")
        val p4 = ByteArray(1024).also { random.nextBytes(it) }
        encryptBytesForTest(cliSub, "c1.jpg", p4)
        val syncResult = CryptoManager.syncVaultFromExternalDirectory(
            testContext,
            cliSourceRoot,
            requirePendingManifest = false
        )
        assertNotNull(syncResult)
        assertEquals(1, syncResult!!.copiedCount)
        val syncedAlbumDir = vaultRoot.listFiles()!!.first {
            it.isDirectory && CryptoManager.getDecryptedFolderName(it, testContext) == "CliSynced"
        }
        assertFalse(File(syncedAlbumDir, VAULT_SUBFOLDER_ORIGIN_FILE).exists())
        assertNull(CryptoManager.resolveOriginDecryptTargetFolder(testContext, syncedAlbumDir))
        assertNull(CryptoManager.decryptFolder(testContext, syncedAlbumDir.absolutePath))

        val pickedBase = File(internalRoot, "Documents").apply { mkdirs() }
        val pickedTarget = CryptoManager.resolveDecryptTargetInPickedDirectory(pickedBase, "CliSynced")
        val syncedOut = CryptoManager.decryptFolder(
            testContext,
            syncedAlbumDir.absolutePath,
            targetFolderOverride = pickedTarget
        )
        assertEquals(File(pickedBase, "CliSynced").canonicalPath, File(syncedOut!!).canonicalPath)
        assertArrayEquals(p4, File(pickedTarget, "c1.jpg").readBytes())
    }

    @Test
    fun externalSdOrOtgFolder_restoresWhenMounted_andRequiresPickerWhenUnmounted() {
        val random = SecureRandom()
        val mountBase = File(tempRoot, "mnt").apply { mkdirs() }
        val sdRoot = File(mountBase, "SDCARD1").apply { mkdirs() }
        CryptoManager.otgMountBaseOverrideForTest = listOf(mountBase)

        val sdTrip = File(sdRoot, "Photos/Trip").apply { mkdirs() }
        val p1 = ByteArray(2048).also { random.nextBytes(it) }
        File(sdTrip, "sd1.jpg").writeBytes(p1)

        val encDir1 = File(CryptoManager.encryptFolder(testContext, sdTrip.absolutePath)!!)
        assertFalse(sdTrip.exists())

        // SD still mounted -> restores to <sdRoot>/Photos/Trip
        val decMounted = CryptoManager.decryptFolder(testContext, encDir1.absolutePath)
        assertEquals(sdTrip.canonicalPath, File(decMounted!!).canonicalPath)
        assertArrayEquals(p1, File(sdTrip, "sd1.jpg").readBytes())

        // Encrypt again, then unmount SD card
        val encDir2 = File(CryptoManager.encryptFolder(testContext, sdTrip.absolutePath)!!)
        sdRoot.deleteRecursively()
        CryptoManager.otgMountBaseOverrideForTest = emptyList()

        // Unmounted SD/OTG must NOT silently fall back to Pictures/
        val picturesTrip = File(internalRoot, "Pictures/Trip").apply { mkdirs() }
        assertNull(CryptoManager.resolveOriginDecryptTargetFolder(testContext, encDir2))
        assertNull(CryptoManager.previewDecryptAlbumConflict(testContext, encDir2.absolutePath))
        assertNull(
            CryptoManager.decryptFolder(
                context = testContext,
                folderPath = encDir2.absolutePath,
                options = AlbumCryptoOptions(resolution = AlbumConflictResolution.MERGE)
            )
        )
        assertFalse(sdRoot.exists())
        assertEquals(0, picturesTrip.listFiles()?.size ?: 0)

        // When user explicitly picks a destination directory, decrypt succeeds there
        val customParent = File(internalRoot, "Download").apply { mkdirs() }
        val pickedTrip = CryptoManager.resolveDecryptTargetInPickedDirectory(customParent, "Trip")
        val decPicked = CryptoManager.decryptFolder(
            context = testContext,
            folderPath = encDir2.absolutePath,
            targetFolderOverride = pickedTrip
        )
        assertEquals(File(customParent, "Trip").canonicalPath, File(decPicked!!).canonicalPath)
        assertArrayEquals(p1, File(pickedTrip, "sd1.jpg").readBytes())
    }

    @Test
    fun mergeOnEncrypt_preservesFirstEncryptedOrigin() {
        val random = SecureRandom()
        val p1 = ByteArray(1024).also { random.nextBytes(it) }
        val p2 = ByteArray(1024).also { random.nextBytes(it) }

        val firstFolder = File(internalRoot, "Download/Vacation").apply { mkdirs() }
        File(firstFolder, "one.jpg").writeBytes(p1)
        val encDir = File(CryptoManager.encryptFolder(testContext, firstFolder.absolutePath)!!)

        val secondFolder = File(internalRoot, "DCIM/Vacation").apply { mkdirs() }
        File(secondFolder, "two.jpg").writeBytes(p2)
        val mergedEncPath = CryptoManager.encryptFolder(
            testContext,
            secondFolder.absolutePath,
            options = AlbumCryptoOptions(resolution = AlbumConflictResolution.MERGE)
        )
        assertEquals(encDir.canonicalPath, File(mergedEncPath!!).canonicalPath)

        val decOut = CryptoManager.decryptFolder(testContext, encDir.absolutePath)
        assertEquals(firstFolder.canonicalPath, File(decOut!!).canonicalPath)
        assertArrayEquals(p1, File(firstFolder, "one.jpg").readBytes())
        assertArrayEquals(p2, File(firstFolder, "two.jpg").readBytes())
    }

    @Test
    fun invalidOrRestrictedOrigins_returnNullWithoutSilentPicturesFallback() {
        val random = SecureRandom()
        val picturesDir = File(internalRoot, "Pictures")

        val disallowedTargets = listOf(
            EncryptedFolderOrigin("internal", "../outside"),
            EncryptedFolderOrigin("internal", "Android/data/com.example"),
            EncryptedFolderOrigin("internal", "$CIPHER_APP_DIR_NAME/$SECRETS_DIR_NAME"),
            EncryptedFolderOrigin("internal", "BlockedByFile/Sub")
        )
        File(internalRoot, "BlockedByFile").writeText("not a directory")

        disallowedTargets.forEachIndexed { idx, badOrigin ->
            val albumName = "BadOrigin$idx"
            val encAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, albumName)!!
            val payload = ByteArray(1024).also { random.nextBytes(it) }
            encryptBytesForTest(encAlbum, "img.jpg", payload)
            CryptoManager.writeEncryptedFolderOrigin(testContext, encAlbum, badOrigin)

            assertNull(
                "Bad origin $badOrigin must return null instead of Pictures",
                CryptoManager.resolveOriginDecryptTargetFolder(testContext, encAlbum)
            )
            assertNull(CryptoManager.decryptFolder(testContext, encAlbum.absolutePath))
            assertFalse(File(picturesDir, albumName).exists())
        }
    }

    @Test
    fun resolveDecryptTargetInPickedDirectory_avoidsDoubleNestingSameAlbumName() {
        val documents = File(internalRoot, "Documents").apply { mkdirs() }
        assertEquals(
            File(documents, "Tatil").canonicalPath,
            CryptoManager.resolveDecryptTargetInPickedDirectory(documents, "Tatil").canonicalPath
        )

        val existingCamera = File(internalRoot, "DCIM/Camera").apply { mkdirs() }
        assertEquals(
            existingCamera.canonicalPath,
            CryptoManager.resolveDecryptTargetInPickedDirectory(existingCamera, "Camera").canonicalPath
        )
    }

    @Test
    fun cleanupEmptyAlbum_deletesOriginMetadataAndReunitesUnsupportedFiles() {
        val random = SecureRandom()
        val payload = ByteArray(2048).also { random.nextBytes(it) }

        // 1. Empty album with .meta, .origin, and .nomedia is cleaned up
        val emptyEncAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "EmptyWithOrigin")!!
        File(emptyEncAlbum, NOMEDIA).createNewFile()
        CryptoManager.writeEncryptedFolderOrigin(
            testContext,
            emptyEncAlbum,
            EncryptedFolderOrigin("internal", "DCIM")
        )
        assertTrue(CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(testContext, emptyEncAlbum))
        assertFalse(emptyEncAlbum.exists())

        // 2. Leftover unsupported file (.pdf) stays in Documents/Work and reunites on decrypt
        val workDir = File(internalRoot, "Documents/Work").apply { mkdirs() }
        val pdfFile = File(workDir, "notes.pdf").apply { writeText("keep me") }
        val photoFile = File(workDir, "scan.jpg").apply { writeBytes(payload) }

        val encWork = File(CryptoManager.encryptFolder(testContext, workDir.absolutePath)!!)
        assertTrue(workDir.exists())
        assertTrue(pdfFile.exists())
        assertFalse(photoFile.exists())

        val decWork = CryptoManager.decryptFolder(
            context = testContext,
            folderPath = encWork.absolutePath,
            options = AlbumCryptoOptions(resolution = AlbumConflictResolution.MERGE)
        )
        assertEquals(workDir.canonicalPath, File(decWork!!).canonicalPath)
        assertEquals("keep me", pdfFile.readText())
        assertArrayEquals(payload, photoFile.readBytes())
        assertNull(CryptoManager.readEncryptedFolderOrigin(encWork))
    }

    private fun encryptBytesForTest(targetDir: File, originalName: String, payload: ByteArray): File {
        return CryptoManager.encryptStreamToDirectory(
            context = null,
            inputStream = ByteArrayInputStream(payload),
            targetDir = targetDir,
            originalName = originalName,
            contentLength = payload.size.toLong()
        )!!
    }

    private fun setMasterKey(key: ByteArray?) {
        if (key == null) {
            CryptoManager.lockVault()
        } else {
            CryptoManager.installSessionForTesting(key)
        }
    }

    private fun resetCryptoManagerSingletons() {
        CryptoManager.otgMountBaseOverrideForTest = null
        setMasterKey(null)
        CryptoManager.lockVault()
        runCatching {
            val cachedField = CryptoManager::class.java.getDeclaredField("cachedVaultRoot")
            cachedField.isAccessible = true
            cachedField.set(CryptoManager, null)

            val metaCacheField = CryptoManager::class.java.getDeclaredField("metadataCache")
            metaCacheField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (metaCacheField.get(CryptoManager) as ConcurrentHashMap<*, *>).clear()

            val folderCacheField = CryptoManager::class.java.getDeclaredField("folderNameCache")
            folderCacheField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (folderCacheField.get(CryptoManager) as ConcurrentHashMap<*, *>).clear()
        }
    }

    private class FakeVaultContext(private val root: File) : ContextWrapper(null) {
        private val prefs = InMemorySharedPreferences()

        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getExternalFilesDirs(type: String?): Array<File?> = emptyArray()
        override fun getFilesDir(): File = File(root, "app_files").apply { mkdirs() }
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            PackageManager.PERMISSION_GRANTED
        override fun getPackageName(): String = "org.fossify.gallery.debug"
    }

    private class InMemorySharedPreferences : SharedPreferences {
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
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            // no-op
        }
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            // no-op
        }

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values?.toSet()
            }
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
