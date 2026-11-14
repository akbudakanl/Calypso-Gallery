package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CIPHER_APP_DIR_NAME
import org.fossify.gallery.helpers.CIPHER_ROOT_DIR_NAME
import org.fossify.gallery.helpers.CIPHER_SECRETS_DIR_NAME
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DEFAULT_SECRET_ALBUM_NAME
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

class CustomVaultLocationTest {

    private lateinit var tempRoot: File
    private lateinit var testContext: Context

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

    @Test
    fun resolveExistingVaultInDirectory_checksDirectAndNestedLayouts() {
        val validHeaderBytes = "FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32)

        val directDir = File(tempRoot, "direct").apply { mkdirs() }
        File(directDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        assertEquals(directDir, CryptoManager.resolveExistingVaultInDirectory(directDir))

        val childSecretsContainer = File(tempRoot, "usb_drive").apply { mkdirs() }
        val childSecretsDir = File(childSecretsContainer, CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        File(childSecretsDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        assertEquals(childSecretsDir, CryptoManager.resolveExistingVaultInDirectory(childSecretsContainer))

        val appContainer = File(tempRoot, "internal_root").apply { mkdirs() }
        val appSecretsDir = File(File(appContainer, CIPHER_APP_DIR_NAME), CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        File(appSecretsDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        assertEquals(appSecretsDir, CryptoManager.resolveExistingVaultInDirectory(appContainer))

        val legacyRootContainer = File(tempRoot, "legacy_usb").apply { mkdirs() }
        val legacySecretsDir = File(File(legacyRootContainer, CIPHER_ROOT_DIR_NAME), CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        File(legacySecretsDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        assertEquals(legacySecretsDir, CryptoManager.resolveExistingVaultInDirectory(legacyRootContainer))

        val emptyDir = File(tempRoot, "empty").apply { mkdirs() }
        assertNull(CryptoManager.resolveExistingVaultInDirectory(emptyDir))

        val badHeaderDir = File(tempRoot, "bad_magic").apply { mkdirs() }
        File(badHeaderDir, VAULT_HEADER_FILE).writeBytes("NOTVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        assertNull(CryptoManager.resolveExistingVaultInDirectory(badHeaderDir))
    }

    @Test
    fun createVaultRoot_withCustomTargetDir_createsCipherSecretsAndNoMediaAndPersistsPath() {
        val parentPick = File(tempRoot, "UsbDrive").apply { mkdirs() }
        val created = CryptoManager.createVaultRoot(testContext, parentPick)

        assertNotNull(created)
        assertEquals(File(parentPick, CIPHER_SECRETS_DIR_NAME).absolutePath, created!!.absolutePath)
        assertTrue(created.isDirectory)
        assertTrue(File(created, NOMEDIA).isFile)
        assertEquals(created.absolutePath, testContext.config.vaultRootPath)
        assertTrue(CryptoManager.isConfiguredCustomVaultPath(testContext))
    }

    @Test
    fun createVaultRoot_withCipherSecretsTargetDir_doesNotDoubleNest() {
        val alreadyNamed = File(tempRoot, "SdCard/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        val created = CryptoManager.createVaultRoot(testContext, alreadyNamed)

        assertNotNull(created)
        assertEquals(alreadyNamed.absolutePath, created!!.absolutePath)
        assertTrue(File(alreadyNamed, NOMEDIA).isFile)
        assertFalse(File(alreadyNamed, CIPHER_SECRETS_DIR_NAME).exists())
    }

    @Test
    fun createVaultRoot_whenTargetIsExistingFile_returnsNull() {
        val regularFile = File(tempRoot, "not_a_directory").apply { writeText("data") }
        assertNull(CryptoManager.createVaultRoot(testContext, regularFile))
    }

    @Test
    fun unavailableExternalVault_preservesConfigAndBiometricState_andRestoresWhenReconnected() {
        val externalVolume = File(tempRoot, "ExternalUSB")
        val externalSecrets = File(externalVolume, CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        val headerFile = File(externalSecrets, VAULT_HEADER_FILE)
        val validHeaderBytes = "FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32)
        headerFile.writeBytes(validHeaderBytes)

        CryptoManager.setVaultRootLocation(testContext, externalSecrets)
        testContext.config.vaultWrappedKeyData = "salt:iv:cipher"
        testContext.config.isVaultBiometricEnabled = true
        testContext.config.vaultBiometricWrappedBundle = "bio_iv:bio_cipher"

        val locatedBeforeUnplug = CryptoManager.locateVaultRootOnDisk(testContext, allowDeepScan = false)
        assertEquals(externalSecrets.absolutePath, locatedBeforeUnplug?.absolutePath)
        assertFalse(CryptoManager.isConfiguredVaultTemporarilyUnavailable(testContext))

        // Simulate unplugging the external USB volume.
        externalVolume.deleteRecursively()

        assertTrue(CryptoManager.isConfiguredCustomVaultPath(testContext))
        assertTrue(CryptoManager.isConfiguredVaultTemporarilyUnavailable(testContext))
        assertNull(CryptoManager.locateVaultRootOnDisk(testContext, allowDeepScan = false))

        val verifiedWhileUnplugged = try {
            CryptoManager.verifyVaultOnDisk(testContext)
        } catch (_: Throwable) {
            // Pure JVM android.jar stubs without Robolectric may throw on Process.myPid();
            // verify the underlying unavailable/locate helpers still keep config intact.
            false
        }
        assertFalse(verifiedWhileUnplugged)
        assertEquals(externalSecrets.absolutePath, testContext.config.vaultRootPath)
        assertEquals("salt:iv:cipher", testContext.config.vaultWrappedKeyData)
        assertTrue(testContext.config.isVaultBiometricEnabled)
        assertEquals("bio_iv:bio_cipher", testContext.config.vaultBiometricWrappedBundle)

        // Simulate plugging the external USB volume back in.
        externalSecrets.mkdirs()
        headerFile.writeBytes(validHeaderBytes)

        assertFalse(CryptoManager.isConfiguredVaultTemporarilyUnavailable(testContext))
        val locatedAfterReplug = CryptoManager.locateVaultRootOnDisk(testContext, allowDeepScan = false)
        assertEquals(externalSecrets.absolutePath, locatedAfterReplug?.absolutePath)
    }

    @Test
    fun locateVaultRootOnDisk_doesNotAutoDetectExternalVault_whenVaultRootPathEmpty() {
        val usbRoot = File(tempRoot, "MountedUsbRoot")
        val usbSecrets = File(usbRoot, CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        File(usbSecrets, VAULT_HEADER_FILE).writeBytes("FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32))

        val contextWithExternalDir = FakeVaultContext(tempRoot, externalFilesDirs = arrayOf(
            File(usbRoot, "Android/data/org.fossify.gallery.debug/files").apply { mkdirs() }
        ))

        val found = CryptoManager.locateVaultRootOnDisk(contextWithExternalDir, allowDeepScan = true)
        assertNull(found)
        assertEquals("", contextWithExternalDir.config.vaultRootPath)

        val candidates = CryptoManager.findCandidateExternalVaultRoots(contextWithExternalDir)
        assertTrue(candidates.any { it.absolutePath == usbSecrets.absolutePath })
    }

    @Test
    fun resolvePickedPathToFile_handlesPosixAndOtgPathsCleanly() {
        val usbRoot = File(tempRoot, "UsbMountRoot").apply { mkdirs() }
        val storageMountBase = File(tempRoot, "storage").apply { mkdirs() }
        val partitionRoot = File(storageMountBase, "1A2B-3C4D").apply { mkdirs() }
        val posixVaultFolder = File(partitionRoot, "MyVault").apply { mkdirs() }

        val ctx = FakeVaultContext(tempRoot, externalFilesDirs = arrayOf(
            File(usbRoot, "Android/data/org.fossify.gallery.debug/files").apply { mkdirs() }
        ))

        // Case A: Direct POSIX path returned even when MainActivity.checkOTGPath() sets OTGPath to that POSIX directory
        ctx.config.OTGPath = partitionRoot.absolutePath
        val resolvedPosix = CryptoManager.resolvePickedPathToFile(ctx, posixVaultFolder.absolutePath)
        assertEquals(posixVaultFolder.absolutePath, resolvedPosix?.absolutePath)

        // Case B1: otg:/ subpath resolved via OTGPartition + mount base
        try {
            CryptoManager.otgMountBaseOverrideForTest = listOf(storageMountBase)
            ctx.config.OTGPath = "otg:/"
            ctx.config.OTGPartition = "1A2B-3C4D"
            val resolvedOtgByPartition = CryptoManager.resolvePickedPathToFile(ctx, "otg:/MyVault")
            assertEquals(posixVaultFolder.absolutePath, resolvedOtgByPartition?.absolutePath)

            // Case B2: otg:/ subpath resolved via externalFilesDirs volume root fallback
            ctx.config.OTGPartition = ""
            val usbVaultFolder = File(usbRoot, "PortableVault").apply { mkdirs() }
            val resolvedOtgByExternalRoot = CryptoManager.resolvePickedPathToFile(ctx, "otg:/PortableVault")
            assertEquals(usbVaultFolder.absolutePath, resolvedOtgByExternalRoot?.absolutePath)

            // Case B3: otg:/ subpath returns null when no backing POSIX mount directory exists
            assertNull(CryptoManager.resolvePickedPathToFile(ctx, "otg:/NonExistentDir"))
        } finally {
            CryptoManager.otgMountBaseOverrideForTest = null
        }
    }

    @Test
    fun configuredCustomVault_takesPrecedenceOverOtherVaultsWhenResolving() {
        val validHeaderBytes = "FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32)
        val firstVault = File(tempRoot, "FirstVault/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        File(firstVault, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        val secondVault = File(tempRoot, "SecondCustomUsb/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        File(secondVault, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)

        CryptoManager.setVaultRootLocation(testContext, firstVault)
        assertEquals(firstVault.absolutePath, CryptoManager.locateVaultRootOnDisk(testContext, allowDeepScan = false)?.absolutePath)

        // Switching vaultRootPath clears stale cached root precedence and resolves the configured custom location.
        CryptoManager.prepareVaultLocationSwitch(testContext, secondVault)
        CryptoManager.setVaultRootLocation(testContext, secondVault)
        assertEquals(secondVault.absolutePath, CryptoManager.locateVaultRootOnDisk(testContext, allowDeepScan = false)?.absolutePath)
    }

    @Test
    fun resolveSyncSourceRoot_handlesNestedContainersSubfoldersAndAlbumFolders() {
        // 1. USB drive root containing nested Cipher_Secrets (even with direct .bin files inside Cipher_Secrets)
        val usbRoot = File(tempRoot, "UsbDriveRoot").apply { mkdirs() }
        val usbSecrets = File(usbRoot, CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        File(usbSecrets, "abcd1234.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(usbSecrets.absolutePath, CryptoManager.resolveSyncSourceRoot(usbRoot).absolutePath)

        // 2. Drive root containing Fossify Gallery Cipher/Cipher_Secrets
        val appContainerRoot = File(tempRoot, "AppContainerRoot").apply { mkdirs() }
        val appSecrets = File(File(appContainerRoot, CIPHER_APP_DIR_NAME), CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        assertEquals(appSecrets.absolutePath, CryptoManager.resolveSyncSourceRoot(appContainerRoot).absolutePath)

        // 3. Drive root containing Encrypted_Output delta folder
        val deltaContainerRoot = File(tempRoot, "DeltaContainerRoot").apply { mkdirs() }
        val encOutput = File(deltaContainerRoot, "Encrypted_Output").apply { mkdirs() }
        assertEquals(encOutput.absolutePath, CryptoManager.resolveSyncSourceRoot(deltaContainerRoot).absolutePath)

        // 4. Specific encrypted album folder picked directly (.meta present)
        val directAlbum = File(usbSecrets, "a1b2c3").apply { mkdirs() }
        File(directAlbum, VAULT_SUBFOLDER_META_FILE).writeBytes(byteArrayOf(0))
        assertEquals(directAlbum.absolutePath, CryptoManager.resolveSyncSourceRoot(directAlbum).absolutePath)

        // 5. Delta root with named subfolders containing .bin directly
        val plainDeltaRoot = File(tempRoot, "PlainDelta").apply { mkdirs() }
        val namedSub = File(plainDeltaRoot, "Vacation").apply { mkdirs() }
        File(namedSub, "efgh5678.bin").writeBytes(byteArrayOf(4, 5))
        assertEquals(plainDeltaRoot.absolutePath, CryptoManager.resolveSyncSourceRoot(plainDeltaRoot).absolutePath)
    }

    @Test
    fun syncVaultFromExternalDirectory_importsFullAndDeltaAlbumsAndMapsNames() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val validHeaderBytes = "FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32)

        val phoneVault = File(tempRoot, "PhoneVault/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val phoneDefaultAlbum = CryptoManager.getOrCreateDefaultHiddenAlbum(testContext)!!
        val existingVacation = CryptoManager.createHiddenPhysicalAlbum(testContext, "vacation")!!

        // Build external drive root with nested Cipher_Secrets
        val extDriveRoot = File(tempRoot, "ExtUsbRoot").apply { mkdirs() }
        val extSecrets = File(extDriveRoot, CIPHER_SECRETS_DIR_NAME).apply { mkdirs() }
        File(extSecrets, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)

        val rootPayload = ByteArray(32 * 1024).also { random.nextBytes(it) }
        val defaultSubPayload = ByteArray(28 * 1024).also { random.nextBytes(it) }
        val beachPayload = ByteArray(40 * 1024).also { random.nextBytes(it) }
        val mountainPayload = ByteArray(24 * 1024).also { random.nextBytes(it) }

        // Direct .bin in external Cipher_Secrets -> maps to Default Secret Album
        val extRootBin = encryptBytesForTest(extSecrets, "root_photo.jpg", rootPayload)
        // Explicit __VAULT_DEFAULT_ALBUM__ .meta subfolder -> maps to Default Secret Album
        val extDefaultDir = File(extSecrets, "d00001").apply { mkdirs() }
        writeMetaV2(extDefaultDir, masterKey, VAULT_DEFAULT_ALBUM_NAME)
        val extDefaultBin = encryptBytesForTest(extDefaultDir, "default_sub.jpg", defaultSubPayload)
        // Named .meta subfolder "VACATION" -> matches existing "vacation" case-insensitively
        val extVacationDir = File(extSecrets, "v00002").apply { mkdirs() }
        writeMetaV2(extVacationDir, masterKey, "VACATION")
        val extBeachBin = encryptBytesForTest(extVacationDir, "beach.jpg", beachPayload)
        // Plain named subfolder "New_Trips" -> creates new encrypted .meta subfolder in phone vault
        val extPlainTrips = File(extSecrets, "New_Trips").apply { mkdirs() }
        val extMountainBin = encryptBytesForTest(extPlainTrips, "mountain.jpg", mountainPayload)

        // Ignored folders/files: JSON, recycle bin, dot-prefixed temp without .meta, wrong-key album
        val extJsonDir = File(extSecrets, "JSON").apply { mkdirs() }
        File(extJsonDir, "fva_test.json").writeText("{}")
        val extRecycleDir = File(extSecrets, "r00003").apply { mkdirs() }
        writeMetaV2(extRecycleDir, masterKey, CryptoManager.VAULT_RECYCLE_BIN_NAME)
        encryptBytesForTest(extRecycleDir, "deleted.jpg", byteArrayOf(9, 8, 7))
        val dotTmpDir = File(extSecrets, ".hidden_temp").apply { mkdirs() }
        encryptBytesForTest(dotTmpDir, "ignored.jpg", byteArrayOf(5, 6, 7))

        // Self-sync rejected
        assertNull(CryptoManager.syncVaultFromExternalDirectory(testContext, phoneVault))

        var lastProgressTotal = -1
        var lastProgressProcessed = -1
        val result = CryptoManager.syncVaultFromExternalDirectory(testContext, extDriveRoot) { processed, total ->
            lastProgressProcessed = processed
            lastProgressTotal = total
        }

        assertNotNull(result)
        assertEquals(4, result!!.copiedCount)
        assertEquals(0, result.renamedCount)
        assertEquals(0, result.skippedDuplicateCount)
        assertEquals(0, result.failedCount)
        assertEquals(3, result.syncedAlbumsCount)
        assertEquals(4, lastProgressTotal)
        assertEquals(4, lastProgressProcessed)

        // Check destination files and decrypted payloads
        assertEquals(DEFAULT_SECRET_ALBUM_NAME, CryptoManager.getDecryptedFolderName(phoneDefaultAlbum))
        val defaultBins = phoneDefaultAlbum.listFiles()!!.filter { it.name.endsWith(".bin") }
        assertEquals(2, defaultBins.size)
        val rootImported = defaultBins.first { CryptoManager.readMetadata(it)?.originalName == "root_photo.jpg" }
        val defaultSubImported = defaultBins.first { CryptoManager.readMetadata(it)?.originalName == "default_sub.jpg" }
        assertTrue(CryptoManager.verifyFileIntegrity(rootImported))
        assertTrue(CryptoManager.verifyFileIntegrity(defaultSubImported))
        assertArrayEquals(rootPayload, CryptoManager.openDecryptedContentStream(rootImported)!!.use { it.readBytes() })
        assertArrayEquals(defaultSubPayload, CryptoManager.openDecryptedContentStream(defaultSubImported)!!.use { it.readBytes() })
        assertEquals(extRootBin.lastModified() / 1000L, rootImported.lastModified() / 1000L)
        assertEquals(extDefaultBin.lastModified() / 1000L, defaultSubImported.lastModified() / 1000L)

        // Check existing "vacation" folder received beach.jpg without duplicating album
        val vacationBins = existingVacation.listFiles()!!.filter { it.name.endsWith(".bin") }
        assertEquals(1, vacationBins.size)
        assertEquals("beach.jpg", CryptoManager.readMetadata(vacationBins[0])?.originalName)
        assertArrayEquals(beachPayload, CryptoManager.openDecryptedContentStream(vacationBins[0])!!.use { it.readBytes() })
        assertTrue(extBeachBin.exists())

        // Check "New_Trips" created a valid 6-hex .meta subfolder
        val newTripsFolder = phoneVault.listFiles()!!.firstOrNull {
            it.isDirectory && CryptoManager.getDecryptedRawFolderName(it) == "New_Trips"
        }
        assertNotNull(newTripsFolder)
        val tripsBins = newTripsFolder!!.listFiles()!!.filter { it.name.endsWith(".bin") }
        assertEquals(1, tripsBins.size)
        assertEquals("mountain.jpg", CryptoManager.readMetadata(tripsBins[0])?.originalName)
        assertArrayEquals(mountainPayload, CryptoManager.openDecryptedContentStream(tripsBins[0])!!.use { it.readBytes() })
        assertTrue(extMountainBin.exists())

        // Wrong-key / corrupt external album must increment failedCount and NEVER leave empty orphan folder
        val alienDelta = File(tempRoot, "AlienDelta").apply { mkdirs() }
        val alienAlbum = File(alienDelta, "AlienAlbum").apply { mkdirs() }
        File(alienAlbum, "deadbeef.bin").writeBytes(ByteArray(128))
        val subfoldersBeforeAlien = phoneVault.listFiles()!!.count { it.isDirectory }
        val alienResult = CryptoManager.syncVaultFromExternalDirectory(testContext, alienDelta)!!
        assertEquals(0, alienResult.copiedCount)
        assertEquals(1, alienResult.failedCount)
        assertEquals(subfoldersBeforeAlien, phoneVault.listFiles()!!.count { it.isDirectory })
    }

    @Test
    fun syncVaultFromExternalDirectory_deduplicatesIdenticalContentAcrossIVsAndLargeFiles() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val phoneVault = File(tempRoot, "PhoneDedup/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes("FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val phoneAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "Media")!!
        val smallPlaintext = ByteArray(128 * 1024).also { random.nextBytes(it) } // <= 256 KB
        val largePlaintext = ByteArray(400 * 1024).also { random.nextBytes(it) } // > 256 KB

        // Pre-populate phone vault with small & large files
        val phoneSmall = encryptBytesForTest(phoneAlbum, "small.jpg", smallPlaintext)
        val phoneLarge = encryptBytesForTest(phoneAlbum, "large.jpg", largePlaintext)

        // External folder encrypts EXACT SAME plaintexts with fresh random IVs
        val extFolder = File(tempRoot, "ExtDedupDelta/Media").apply { mkdirs() }
        val extSmallReencrypted = encryptBytesForTest(extFolder, "small.jpg", smallPlaintext)
        val extLargeReencrypted = encryptBytesForTest(extFolder, "large.jpg", largePlaintext)

        assertTrue(
            CryptoManager.areEncryptedFilesIdenticalContent(
                extSmallReencrypted,
                CryptoManager.readMetadata(extSmallReencrypted)!!,
                phoneSmall,
                CryptoManager.readMetadata(phoneSmall)!!
            )
        )
        assertTrue(
            CryptoManager.areEncryptedFilesIdenticalContent(
                extLargeReencrypted,
                CryptoManager.readMetadata(extLargeReencrypted)!!,
                phoneLarge,
                CryptoManager.readMetadata(phoneLarge)!!
            )
        )

        val syncSame = CryptoManager.syncVaultFromExternalDirectory(testContext, extFolder.parentFile!!)!!
        assertEquals(0, syncSame.copiedCount)
        assertEquals(0, syncSame.renamedCount)
        assertEquals(2, syncSame.skippedDuplicateCount)
        assertEquals(0, syncSame.failedCount)

        // Mutate a byte strictly inside the middle window (offset 200 KB) -> must NOT deduplicate!
        val largeMidMutated = largePlaintext.copyOf().also {
            val midIdx = (largePlaintext.size / 2) + 123
            it[midIdx] = (it[midIdx].toInt() xor 0xFF).toByte()
        }
        val extMidDiffDir = File(tempRoot, "ExtMidDiff/Media").apply { mkdirs() }
        val extMidDiffBin = encryptBytesForTest(extMidDiffDir, "large.jpg", largeMidMutated)
        assertFalse(
            CryptoManager.areEncryptedFilesIdenticalContent(
                extMidDiffBin,
                CryptoManager.readMetadata(extMidDiffBin)!!,
                phoneLarge,
                CryptoManager.readMetadata(phoneLarge)!!
            )
        )

        // Mutate a byte near the tail (last 64 KB) -> must NOT deduplicate!
        val largeTailMutated = largePlaintext.copyOf().also {
            val tailIdx = largePlaintext.size - 1024
            it[tailIdx] = (it[tailIdx].toInt() xor 0xFF).toByte()
        }
        val extTailDiffDir = File(tempRoot, "ExtTailDiff/Media").apply { mkdirs() }
        val extTailDiffBin = encryptBytesForTest(extTailDiffDir, "large.jpg", largeTailMutated)
        assertFalse(
            CryptoManager.areEncryptedFilesIdenticalContent(
                extTailDiffBin,
                CryptoManager.readMetadata(extTailDiffBin)!!,
                phoneLarge,
                CryptoManager.readMetadata(phoneLarge)!!
            )
        )
    }

    @Test
    fun syncVaultFromExternalDirectory_renamesNameCollisionsAndStaysIdempotent() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val phoneVault = File(tempRoot, "PhoneCollision/$CIPHER_SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes("FGVLT2".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val phoneAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "Camera")!!
        val payload1 = ByteArray(16 * 1024).also { random.nextBytes(it) }
        val payload2 = ByteArray(16 * 1024).also { random.nextBytes(it) }
        val payload3 = ByteArray(16 * 1024).also { random.nextBytes(it) }

        encryptBytesForTest(phoneAlbum, "photo.jpg", payload1)

        // Incoming "photo.jpg" with different content -> renamed to "photo(1).jpg"
        val extRoot1 = File(tempRoot, "ExtCollision1").apply { mkdirs() }
        val extCamera1 = File(extRoot1, "Camera").apply { mkdirs() }
        encryptBytesForTest(extCamera1, "photo.jpg", payload2)

        val firstSync = CryptoManager.syncVaultFromExternalDirectory(testContext, extRoot1)!!
        assertEquals(0, firstSync.copiedCount)
        assertEquals(1, firstSync.renamedCount)
        assertEquals(0, firstSync.skippedDuplicateCount)
        assertEquals(0, firstSync.failedCount)

        val binsAfterFirst = phoneAlbum.listFiles()!!.filter { it.name.endsWith(".bin") }
        assertEquals(2, binsAfterFirst.size)
        val originalImported = binsAfterFirst.first { CryptoManager.readMetadata(it)?.originalName == "photo.jpg" }
        val copy1Imported = binsAfterFirst.first { CryptoManager.readMetadata(it)?.originalName == "photo(1).jpg" }
        assertTrue(CryptoManager.verifyFileIntegrity(originalImported))
        assertTrue(CryptoManager.verifyFileIntegrity(copy1Imported))
        assertArrayEquals(payload1, CryptoManager.openDecryptedContentStream(originalImported)!!.use { it.readBytes() })
        assertArrayEquals(payload2, CryptoManager.openDecryptedContentStream(copy1Imported)!!.use { it.readBytes() })

        // Repeat sync of exact same external source -> skipped via collision-family deduplication, NO photo(2).jpg!
        val repeatSync = CryptoManager.syncVaultFromExternalDirectory(testContext, extRoot1)!!
        assertEquals(0, repeatSync.copiedCount)
        assertEquals(0, repeatSync.renamedCount)
        assertEquals(1, repeatSync.skippedDuplicateCount)
        assertEquals(0, repeatSync.failedCount)
        assertEquals(2, phoneAlbum.listFiles()!!.count { it.name.endsWith(".bin") })

        // New external source with a 3rd distinct payload for "photo.jpg" -> renamed to "photo(2).jpg"
        val extRoot2 = File(tempRoot, "ExtCollision2").apply { mkdirs() }
        val extCamera2 = File(extRoot2, "Camera").apply { mkdirs() }
        encryptBytesForTest(extCamera2, "photo.jpg", payload3)

        val secondDistinctSync = CryptoManager.syncVaultFromExternalDirectory(testContext, extRoot2)!!
        assertEquals(0, secondDistinctSync.copiedCount)
        assertEquals(1, secondDistinctSync.renamedCount)
        assertEquals(0, secondDistinctSync.skippedDuplicateCount)

        val copy2Imported = phoneAlbum.listFiles()!!
            .filter { it.name.endsWith(".bin") }
            .first { CryptoManager.readMetadata(it)?.originalName == "photo(2).jpg" }
        assertTrue(CryptoManager.verifyFileIntegrity(copy2Imported))
        assertArrayEquals(payload3, CryptoManager.openDecryptedContentStream(copy2Imported)!!.use { it.readBytes() })
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

    private fun writeMetaV2(folder: File, masterKey: ByteArray, rawName: String) {
        val random = SecureRandom()
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
        val encryptedName = cipher.doFinal(rawName.toByteArray(Charsets.UTF_8))

        FileOutputStream(File(folder, VAULT_SUBFOLDER_META_FILE)).use { fos ->
            DataOutputStream(fos).use { dos ->
                dos.write("FGMETA2".toByteArray(Charsets.US_ASCII))
                dos.write(iv)
                dos.writeInt(encryptedName.size)
                dos.write(encryptedName)
            }
        }
    }

    private fun setMasterKey(key: ByteArray?) {
        val field = CryptoManager::class.java.getDeclaredField("unlockedMasterKey")
        field.isAccessible = true
        field.set(CryptoManager, key)
    }

    private fun resetCryptoManagerSingletons() {
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

    private class FakeVaultContext(
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
