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
import org.fossify.gallery.helpers.SECRETS_DIR_NAME
import org.fossify.gallery.helpers.DATE_TIE_BREAK_ASK
import org.fossify.gallery.helpers.DATE_TIE_BREAK_KEEP_TARGET
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
        val validHeaderBytes = "FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32)

        val directDir = File(tempRoot, "direct").apply { mkdirs() }
        File(directDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        assertEquals(directDir, CryptoManager.resolveExistingVaultInDirectory(directDir))

        val childSecretsContainer = File(tempRoot, "usb_drive").apply { mkdirs() }
        val childSecretsDir = File(childSecretsContainer, SECRETS_DIR_NAME).apply { mkdirs() }
        File(childSecretsDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        assertEquals(childSecretsDir, CryptoManager.resolveExistingVaultInDirectory(childSecretsContainer))

        val appContainer = File(tempRoot, "internal_root").apply { mkdirs() }
        val appSecretsDir = File(File(appContainer, CIPHER_APP_DIR_NAME), SECRETS_DIR_NAME).apply { mkdirs() }
        File(appSecretsDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        assertEquals(appSecretsDir, CryptoManager.resolveExistingVaultInDirectory(appContainer))

        val emptyDir = File(tempRoot, "empty").apply { mkdirs() }
        assertNull(CryptoManager.resolveExistingVaultInDirectory(emptyDir))

        val badHeaderDir = File(tempRoot, "bad_magic").apply { mkdirs() }
        File(badHeaderDir, VAULT_HEADER_FILE).writeBytes("NOTVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        assertNull(CryptoManager.resolveExistingVaultInDirectory(badHeaderDir))
    }

    @Test
    fun createVaultRoot_withCustomTargetDir_createsSecretsAndNoMediaAndPersistsPath() {
        val parentPick = File(tempRoot, "UsbDrive").apply { mkdirs() }
        val created = CryptoManager.createVaultRoot(testContext, parentPick)

        assertNotNull(created)
        assertEquals(File(parentPick, SECRETS_DIR_NAME).absolutePath, created!!.absolutePath)
        assertTrue(created.isDirectory)
        assertTrue(File(created, NOMEDIA).isFile)
        assertEquals(created.absolutePath, testContext.config.vaultRootPath)
        assertTrue(CryptoManager.isConfiguredCustomVaultPath(testContext))
    }

    @Test
    fun createVaultRoot_withSecretsTargetDir_doesNotDoubleNest() {
        val alreadyNamed = File(tempRoot, "SdCard/$SECRETS_DIR_NAME").apply { mkdirs() }
        val created = CryptoManager.createVaultRoot(testContext, alreadyNamed)

        assertNotNull(created)
        assertEquals(alreadyNamed.absolutePath, created!!.absolutePath)
        assertTrue(File(alreadyNamed, NOMEDIA).isFile)
        assertFalse(File(alreadyNamed, SECRETS_DIR_NAME).exists())
    }

    @Test
    fun createVaultRoot_withExistingSecretsFolder_reusesItInsteadOfCreatingNewOne() {
        val parentPick = File(tempRoot, "UsbDriveWithSecrets").apply { mkdirs() }
        val existingFolder = File(parentPick, SECRETS_DIR_NAME).apply { mkdirs() }
        val created = CryptoManager.createVaultRoot(testContext, parentPick)

        assertNotNull(created)
        assertEquals(existingFolder.absolutePath, created!!.absolutePath)
        assertTrue(File(existingFolder, NOMEDIA).isFile)
    }

    @Test
    fun createVaultRoot_whenTargetIsExistingFile_returnsNull() {
        val regularFile = File(tempRoot, "not_a_directory").apply { writeText("data") }
        assertNull(CryptoManager.createVaultRoot(testContext, regularFile))
    }

    @Test
    fun unavailableExternalVault_preservesConfigAndBiometricState_andRestoresWhenReconnected() {
        val externalVolume = File(tempRoot, "ExternalUSB")
        val externalSecrets = File(externalVolume, SECRETS_DIR_NAME).apply { mkdirs() }
        val headerFile = File(externalSecrets, VAULT_HEADER_FILE)
        val validHeaderBytes = "FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32)
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
        val usbSecrets = File(usbRoot, SECRETS_DIR_NAME).apply { mkdirs() }
        File(usbSecrets, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))

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
        val validHeaderBytes = "FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32)
        val firstVault = File(tempRoot, "FirstVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(firstVault, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        val secondVault = File(tempRoot, "SecondCustomUsb/$SECRETS_DIR_NAME").apply { mkdirs() }
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
        // 1. USB drive root containing nested Secrets (even with direct .bin files inside Secrets)
        val usbRoot = File(tempRoot, "UsbDriveRoot").apply { mkdirs() }
        val usbSecrets = File(usbRoot, SECRETS_DIR_NAME).apply { mkdirs() }
        File(usbSecrets, "abcd1234.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(usbSecrets.absolutePath, CryptoManager.resolveSyncSourceRoot(usbRoot).absolutePath)

        // 2. Drive root containing Calypso Gallery/Secrets
        val appContainerRoot = File(tempRoot, "AppContainerRoot").apply { mkdirs() }
        val appSecrets = File(File(appContainerRoot, CIPHER_APP_DIR_NAME), SECRETS_DIR_NAME).apply { mkdirs() }
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
        val validHeaderBytes = "FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32)

        val phoneVault = File(tempRoot, "PhoneVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val phoneDefaultAlbum = CryptoManager.getOrCreateDefaultHiddenAlbum(testContext)!!
        val existingVacation = CryptoManager.createHiddenPhysicalAlbum(testContext, "vacation")!!

        // Build external drive root with nested Secrets
        val extDriveRoot = File(tempRoot, "ExtUsbRoot").apply { mkdirs() }
        val extSecrets = File(extDriveRoot, SECRETS_DIR_NAME).apply { mkdirs() }
        File(extSecrets, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)

        val rootPayload = ByteArray(32 * 1024).also { random.nextBytes(it) }
        val defaultSubPayload = ByteArray(28 * 1024).also { random.nextBytes(it) }
        val beachPayload = ByteArray(40 * 1024).also { random.nextBytes(it) }
        val mountainPayload = ByteArray(24 * 1024).also { random.nextBytes(it) }

        // Direct .bin in external Secrets -> maps to Default Secret Album
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
        val result = CryptoManager.syncVaultFromExternalDirectory(
            testContext,
            extDriveRoot,
            requirePendingManifest = false
        ) { processed, total ->
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
        val alienResult = CryptoManager.syncVaultFromExternalDirectory(testContext, alienDelta, requirePendingManifest = false)!!
        assertEquals(0, alienResult.copiedCount)
        assertEquals(1, alienResult.failedCount)
        assertEquals(subfoldersBeforeAlien, phoneVault.listFiles()!!.count { it.isDirectory })
    }

    @Test
    fun syncVaultFromExternalDirectory_deduplicatesIdenticalContentAcrossIVsAndLargeFiles() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val phoneVault = File(tempRoot, "PhoneDedup/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
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

        val syncSame = CryptoManager.syncVaultFromExternalDirectory(testContext, extFolder.parentFile!!, requirePendingManifest = false)!!
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

        // Mutate a single bit at offset 90 KB (inside old skipped gap between 64 KB and 200 KB) -> must NOT deduplicate!
        val largeGapBitFlipped = largePlaintext.copyOf().also {
            val gapIdx = 90 * 1024
            it[gapIdx] = (it[gapIdx].toInt() xor 0x01).toByte()
        }
        val extGapDiffDir = File(tempRoot, "ExtGapDiff/Media").apply { mkdirs() }
        val extGapDiffBin = encryptBytesForTest(extGapDiffDir, "large.jpg", largeGapBitFlipped)
        assertFalse(
            CryptoManager.areEncryptedFilesIdenticalContent(
                extGapDiffBin,
                CryptoManager.readMetadata(extGapDiffBin)!!,
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
    fun syncVaultFromExternalDirectory_stage0FastSkipMirroredBinAndRejectTamperedPayloadOrMac() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val validHeaderBytes = "FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32)
        val phoneVault = File(tempRoot, "PhoneStage0/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val phoneAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "Archive")!!
        val payload = ByteArray(64 * 1024).also { random.nextBytes(it) }
        val phoneBin = encryptBytesForTest(phoneAlbum, "archive_item.jpg", payload)

        // Build exact byte-for-byte mirror on external drive (same .bin name, length, header, and MAC trailer)
        val extMirrorSecrets = File(tempRoot, "ExtMirror/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(extMirrorSecrets, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        val extMirrorAlbum = File(extMirrorSecrets, phoneAlbum.name).apply { mkdirs() }
        File(phoneAlbum, VAULT_SUBFOLDER_META_FILE).copyTo(File(extMirrorAlbum, VAULT_SUBFOLDER_META_FILE))
        val extMirrorBin = File(extMirrorAlbum, phoneBin.name)
        phoneBin.copyTo(extMirrorBin)

        // Stage 0 O(1) mirror skip (clear metadataCache first so we ensure Stage 0 needs no cached metadata)
        resetCryptoManagerSingletons()
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val mirrorResult = CryptoManager.syncVaultFromExternalDirectory(testContext, extMirrorSecrets, requirePendingManifest = false)!!
        assertEquals(0, mirrorResult.copiedCount)
        assertEquals(0, mirrorResult.renamedCount)
        assertEquals(1, mirrorResult.skippedDuplicateCount)
        assertEquals(0, mirrorResult.failedCount)

        // Flip 1 bit in the mirrored MAC trailer -> Stage 0 misses & MAC verification fails (failedCount == 1)
        val corruptedTrailerBytes = extMirrorBin.readBytes().also {
            it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte()
        }
        extMirrorBin.writeBytes(corruptedTrailerBytes)

        val tamperedTrailerResult = CryptoManager.syncVaultFromExternalDirectory(testContext, extMirrorSecrets, requirePendingManifest = false)!!
        assertEquals(0, tamperedTrailerResult.copiedCount)
        assertEquals(0, tamperedTrailerResult.skippedDuplicateCount)
        assertEquals(1, tamperedTrailerResult.failedCount)

        // 1-bit flip inside payload of a new album folder -> integrity check fails before folder creation (no orphan folder)
        val extCorruptNewRoot = File(tempRoot, "ExtCorruptNew").apply { mkdirs() }
        val extCorruptNewAlbum = File(extCorruptNewRoot, "BrandNewCorruptAlbum").apply { mkdirs() }
        val corruptNewBin = encryptBytesForTest(extCorruptNewAlbum, "corrupt_new.jpg", payload)
        val binBytes = corruptNewBin.readBytes().also {
            val payloadBitIdx = it.size - 32
            it[payloadBitIdx] = (it[payloadBitIdx].toInt() xor 0x01).toByte()
        }
        corruptNewBin.writeBytes(binBytes)

        val folderCountBefore = phoneVault.listFiles()!!.count { it.isDirectory }
        val corruptNewResult = CryptoManager.syncVaultFromExternalDirectory(testContext, extCorruptNewRoot, requirePendingManifest = false)!!
        assertEquals(0, corruptNewResult.copiedCount)
        assertEquals(0, corruptNewResult.renamedCount)
        assertEquals(1, corruptNewResult.failedCount)
        assertEquals(folderCountBefore, phoneVault.listFiles()!!.count { it.isDirectory })
    }

    @Test
    fun syncVaultFromExternalDirectory_renamesNameCollisionsAndStaysIdempotent() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val phoneVault = File(tempRoot, "PhoneCollision/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
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

        val firstSync = CryptoManager.syncVaultFromExternalDirectory(testContext, extRoot1, requirePendingManifest = false)!!
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
        val repeatSync = CryptoManager.syncVaultFromExternalDirectory(testContext, extRoot1, requirePendingManifest = false)!!
        assertEquals(0, repeatSync.copiedCount)
        assertEquals(0, repeatSync.renamedCount)
        assertEquals(1, repeatSync.skippedDuplicateCount)
        assertEquals(0, repeatSync.failedCount)
        assertEquals(2, phoneAlbum.listFiles()!!.count { it.name.endsWith(".bin") })

        // New external source with a 3rd distinct payload for "photo.jpg" -> renamed to "photo(2).jpg"
        val extRoot2 = File(tempRoot, "ExtCollision2").apply { mkdirs() }
        val extCamera2 = File(extRoot2, "Camera").apply { mkdirs() }
        encryptBytesForTest(extCamera2, "photo.jpg", payload3)

        val secondDistinctSync = CryptoManager.syncVaultFromExternalDirectory(testContext, extRoot2, requirePendingManifest = false)!!
        assertEquals(0, secondDistinctSync.copiedCount)
        assertEquals(1, secondDistinctSync.renamedCount)
        assertEquals(0, secondDistinctSync.skippedDuplicateCount)

        val copy2Imported = phoneAlbum.listFiles()!!
            .filter { it.name.endsWith(".bin") }
            .first { CryptoManager.readMetadata(it)?.originalName == "photo(2).jpg" }
        assertTrue(CryptoManager.verifyFileIntegrity(copy2Imported))
        assertArrayEquals(payload3, CryptoManager.openDecryptedContentStream(copy2Imported)!!.use { it.readBytes() })
    }

    @Test
    fun arePlainFilesIdentical_and_getAlternativePlainFile_workAcrossVariants() {
        val dir = File(tempRoot, "PlainUtils").apply { mkdirs() }
        val payloadA = ByteArray(12 * 1024) { (it * 7).toByte() }
        val payloadB = payloadA.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }

        val fileA1 = File(dir, "photo.jpg").apply { writeBytes(payloadA) }
        val fileA2 = File(dir, "copy.jpg").apply { writeBytes(payloadA) }
        val fileB = File(dir, "diff.jpg").apply { writeBytes(payloadB) }

        assertTrue(CryptoManager.arePlainFilesIdentical(fileA1, fileA1))
        assertTrue(CryptoManager.arePlainFilesIdentical(fileA1, fileA2))
        assertFalse(CryptoManager.arePlainFilesIdentical(fileA1, fileB))

        val alt1 = CryptoManager.getAlternativePlainFile(dir, "photo.jpg")
        assertEquals("photo(1).jpg", alt1.name)
        alt1.writeBytes(payloadB)

        val alt2 = CryptoManager.getAlternativePlainFile(dir, "photo.jpg")
        assertEquals("photo(2).jpg", alt2.name)

        val matchedIdentical = CryptoManager.findIdenticalPlainFileInDir(File(tempRoot, "outside.jpg").apply {
            writeBytes(payloadA)
        }, dir, "photo.jpg")
        assertEquals(fileA1.canonicalPath, matchedIdentical?.canonicalPath)
    }

    @Test
    fun encryptFileToDirectory_skipsBitIdenticalAndHandlesConflicts() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val vaultRoot = File(tempRoot, "EncDedupVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)

        val albumDir = CryptoManager.createHiddenPhysicalAlbum(testContext, "Shots")!!
        val stagingDir = File(tempRoot, "StagingEnc").apply { mkdirs() }
        val payload1 = ByteArray(16 * 1024).also { random.nextBytes(it) }
        val payload2 = ByteArray(16 * 1024).also { random.nextBytes(it) }
        val payload3 = ByteArray(16 * 1024).also { random.nextBytes(it) }

        val srcInitial = File(stagingDir, "photo.jpg").apply { writeBytes(payload1) }
        val firstBin = CryptoManager.encryptFileToDirectory(
            context = testContext,
            sourceFile = srcInitial,
            targetDir = albumDir,
            wipeSource = true
        )
        assertNotNull(firstBin)
        assertFalse(srcInitial.exists())
        assertEquals(1, albumDir.listFiles()!!.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // Repeat with identical payload -> skips creating a second .bin and wipes redundant source
        val srcDuplicate = File(stagingDir, "photo.jpg").apply { writeBytes(payload1) }
        val dedupResult = CryptoManager.encryptFileToDirectory(
            context = testContext,
            sourceFile = srcDuplicate,
            targetDir = albumDir,
            wipeSource = true
        )
        assertEquals(firstBin!!.canonicalPath, dedupResult?.canonicalPath)
        assertFalse(srcDuplicate.exists())
        assertEquals(1, albumDir.listFiles()!!.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // Different payload with CONFLICT_SKIP -> returns null and keeps source intact
        val srcDifferent = File(stagingDir, "photo.jpg").apply { writeBytes(payload2) }
        val skippedResult = CryptoManager.encryptFileToDirectory(
            context = testContext,
            sourceFile = srcDifferent,
            targetDir = albumDir,
            wipeSource = true,
            conflictResolution = CONFLICT_SKIP
        )
        assertNull(skippedResult)
        assertTrue(srcDifferent.exists())
        assertEquals(1, albumDir.listFiles()!!.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // Same different payload with default CONFLICT_KEEP_BOTH -> saved as photo(1).jpg
        val keepBothResult = CryptoManager.encryptFileToDirectory(
            context = testContext,
            sourceFile = srcDifferent,
            targetDir = albumDir,
            wipeSource = false,
            conflictResolution = CONFLICT_KEEP_BOTH
        )!!
        assertEquals("photo(1).jpg", CryptoManager.readMetadata(keepBothResult)?.originalName)
        assertArrayEquals(payload2, CryptoManager.openDecryptedContentStream(keepBothResult)!!.use { it.readBytes() })

        // Repeat payload2 as "photo.jpg" -> deduplicated against photo(1).jpg (NO photo(2).jpg)
        val dedupAgainstVariant = CryptoManager.encryptFileToDirectory(
            context = testContext,
            sourceFile = srcDifferent,
            targetDir = albumDir,
            wipeSource = true,
            conflictResolution = CONFLICT_KEEP_BOTH
        )
        assertEquals(keepBothResult.canonicalPath, dedupAgainstVariant?.canonicalPath)
        assertFalse(srcDifferent.exists())
        assertEquals(2, albumDir.listFiles()!!.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // Different payload3 with CONFLICT_OVERWRITE -> replaces original photo.jpg
        val srcOverwrite = File(stagingDir, "photo.jpg").apply { writeBytes(payload3) }
        val overwriteResult = CryptoManager.encryptFileToDirectory(
            context = testContext,
            sourceFile = srcOverwrite,
            targetDir = albumDir,
            wipeSource = true,
            conflictResolution = CONFLICT_OVERWRITE
        )!!
        assertFalse(firstBin.exists())
        assertEquals("photo.jpg", CryptoManager.readMetadata(overwriteResult)?.originalName)
        assertArrayEquals(payload3, CryptoManager.openDecryptedContentStream(overwriteResult)!!.use { it.readBytes() })
    }

    @Test
    fun copyOrMoveEncryptedFileToDirectory_deduplicatesAndHandlesConflicts() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val vaultRoot = File(tempRoot, "EncMoveVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)

        val albumA = CryptoManager.createHiddenPhysicalAlbum(testContext, "AlbumA")!!
        val albumB = CryptoManager.createHiddenPhysicalAlbum(testContext, "AlbumB")!!
        val payload1 = ByteArray(14 * 1024).also { random.nextBytes(it) }
        val payload2 = ByteArray(14 * 1024).also { random.nextBytes(it) }
        val payload3 = ByteArray(14 * 1024).also { random.nextBytes(it) }

        val srcIdenticalForCopy = encryptBytesForTest(albumA, "item.jpg", payload1)
        val dstExisting = encryptBytesForTest(albumB, "item.jpg", payload1)

        // Copy identical .bin -> skips duplicate, keeps both copies across albums
        val copiedIdentical = CryptoManager.copyOrMoveEncryptedFileToDirectory(
            context = testContext,
            sourceBin = srcIdenticalForCopy,
            targetDir = albumB,
            isMove = false
        )
        assertEquals(dstExisting.canonicalPath, copiedIdentical?.canonicalPath)
        assertTrue(srcIdenticalForCopy.exists())
        assertEquals(1, albumB.listFiles()!!.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // Move identical .bin -> skips duplicate AND deletes redundant source .bin
        val movedIdentical = CryptoManager.copyOrMoveEncryptedFileToDirectory(
            context = testContext,
            sourceBin = srcIdenticalForCopy,
            targetDir = albumB,
            isMove = true
        )
        assertEquals(dstExisting.canonicalPath, movedIdentical?.canonicalPath)
        assertFalse(srcIdenticalForCopy.exists())
        assertEquals(1, albumB.listFiles()!!.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // Different payload with CONFLICT_SKIP, CONFLICT_KEEP_BOTH, CONFLICT_OVERWRITE
        val srcDiffMove = encryptBytesForTest(albumA, "item.jpg", payload2)
        assertNull(
            CryptoManager.copyOrMoveEncryptedFileToDirectory(
                context = testContext,
                sourceBin = srcDiffMove,
                targetDir = albumB,
                isMove = true,
                conflictResolution = CONFLICT_SKIP
            )
        )
        assertTrue(srcDiffMove.exists())

        val keptBoth = CryptoManager.copyOrMoveEncryptedFileToDirectory(
            context = testContext,
            sourceBin = srcDiffMove,
            targetDir = albumB,
            isMove = true,
            conflictResolution = CONFLICT_KEEP_BOTH
        )!!
        assertFalse(srcDiffMove.exists())
        assertEquals("item(1).jpg", CryptoManager.readMetadata(keptBoth)?.originalName)
        assertArrayEquals(payload2, CryptoManager.openDecryptedContentStream(keptBoth)!!.use { it.readBytes() })

        val srcOverwrite = encryptBytesForTest(albumA, "item.jpg", payload3)
        val overwritten = CryptoManager.copyOrMoveEncryptedFileToDirectory(
            context = testContext,
            sourceBin = srcOverwrite,
            targetDir = albumB,
            isMove = true,
            conflictResolution = CONFLICT_OVERWRITE
        )!!
        assertFalse(srcOverwrite.exists())
        assertFalse(dstExisting.exists())
        assertEquals("item.jpg", CryptoManager.readMetadata(overwritten)?.originalName)
        assertArrayEquals(payload3, CryptoManager.openDecryptedContentStream(overwritten)!!.use { it.readBytes() })
    }

    @Test
    fun decryptFileToDirectory_skipsBitIdenticalAndRenamesWithParentheses() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val vaultRoot = File(tempRoot, "DecVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)

        val album = CryptoManager.createHiddenPhysicalAlbum(testContext, "Out")!!
        val outDir = File(tempRoot, "PlainOut").apply { mkdirs() }
        val payload1 = ByteArray(10 * 1024).also { random.nextBytes(it) }
        val payload2 = ByteArray(10 * 1024).also { random.nextBytes(it) }
        val payload3 = ByteArray(10 * 1024).also { random.nextBytes(it) }

        val existingPlain = File(outDir, "pic.jpg").apply { writeBytes(payload1) }
        val encDup = encryptBytesForTest(album, "pic.jpg", payload1)

        // Identical content -> skips decrypting duplicate and deletes source .bin
        val decDup = CryptoManager.decryptFileToDirectory(
            context = testContext,
            encryptedFile = encDup,
            targetDir = outDir,
            deleteEncryptedSource = true
        )
        assertEquals(existingPlain.canonicalPath, decDup?.canonicalPath)
        assertFalse(encDup.exists())
        assertArrayEquals(payload1, existingPlain.readBytes())

        // Different content -> CONFLICT_SKIP keeps both intact
        val encDiff = encryptBytesForTest(album, "pic.jpg", payload2)
        assertNull(
            CryptoManager.decryptFileToDirectory(
                context = testContext,
                encryptedFile = encDiff,
                targetDir = outDir,
                deleteEncryptedSource = true,
                conflictResolution = CONFLICT_SKIP
            )
        )
        assertTrue(encDiff.exists())

        // Different content -> CONFLICT_KEEP_BOTH uses pic(1).jpg instead of pic_1.jpg
        val decRenamed = CryptoManager.decryptFileToDirectory(
            context = testContext,
            encryptedFile = encDiff,
            targetDir = outDir,
            deleteEncryptedSource = true,
            conflictResolution = CONFLICT_KEEP_BOTH
        )!!
        assertFalse(encDiff.exists())
        assertEquals("pic(1).jpg", decRenamed.name)
        assertArrayEquals(payload2, decRenamed.readBytes())

        // Different content -> CONFLICT_OVERWRITE replaces existing pic.jpg
        val encOverwrite = encryptBytesForTest(album, "pic.jpg", payload3)
        val decOverwritten = CryptoManager.decryptFileToDirectory(
            context = testContext,
            encryptedFile = encOverwrite,
            targetDir = outDir,
            deleteEncryptedSource = true,
            conflictResolution = CONFLICT_OVERWRITE
        )!!
        assertFalse(encOverwrite.exists())
        assertEquals(existingPlain.canonicalPath, decOverwritten.canonicalPath)
        assertArrayEquals(payload3, existingPlain.readBytes())
    }

    @Test
    fun encryptFolder_and_decryptFolder_mergeExistingAlbumsAndCleanupSafely() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val vaultRoot = File(tempRoot, "MergeVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)
        testContext.config.internalStoragePath = tempRoot.absolutePath

        val payloadSame = ByteArray(8 * 1024).also { random.nextBytes(it) }
        val payloadExisting = ByteArray(8 * 1024).also { random.nextBytes(it) }
        val payloadIncomingConflict = ByteArray(8 * 1024).also { random.nextBytes(it) }
        val payloadNew = ByteArray(8 * 1024).also { random.nextBytes(it) }

        val existingEncryptedAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "Travel")!!
        encryptBytesForTest(existingEncryptedAlbum, "a.jpg", payloadSame)
        encryptBytesForTest(existingEncryptedAlbum, "b.jpg", payloadExisting)

        val plainTravel = File(tempRoot, "Travel").apply { mkdirs() }
        File(plainTravel, "a.jpg").writeBytes(payloadSame)
        File(plainTravel, "b.jpg").writeBytes(payloadIncomingConflict)
        File(plainTravel, "c.jpg").writeBytes(payloadNew)

        val encryptedResultPath = CryptoManager.encryptFolder(testContext, plainTravel.absolutePath)
        assertEquals(existingEncryptedAlbum.canonicalPath, File(encryptedResultPath!!).canonicalPath)
        assertFalse(plainTravel.exists())

        val vaultAlbumsNamedTravel = vaultRoot.listFiles()!!.filter {
            it.isDirectory && CryptoManager.getDecryptedFolderName(it, testContext)?.startsWith("Travel") == true
        }
        assertEquals(1, vaultAlbumsNamedTravel.size)

        val decryptedMetaNames = existingEncryptedAlbum.listFiles()!!
            .filter { it.name.endsWith(ENCRYPTED_BIN_EXT) }
            .associate { CryptoManager.readMetadata(it)!!.originalName to CryptoManager.openDecryptedContentStream(it)!!.use { s -> s.readBytes() } }
        assertEquals(setOf("a.jpg", "b.jpg", "b(1).jpg", "c.jpg"), decryptedMetaNames.keys)
        assertArrayEquals(payloadSame, decryptedMetaNames["a.jpg"])
        assertArrayEquals(payloadExisting, decryptedMetaNames["b.jpg"])
        assertArrayEquals(payloadIncomingConflict, decryptedMetaNames["b(1).jpg"])
        assertArrayEquals(payloadNew, decryptedMetaNames["c.jpg"])

        // Merge decrypt into an existing plaintext directory without overwriting different b.jpg
        val targetPlain = File(tempRoot, "TargetPlainTravel").apply { mkdirs() }
        val payloadPlainB = ByteArray(8 * 1024).also { random.nextBytes(it) }
        File(targetPlain, "a.jpg").writeBytes(payloadSame)
        File(targetPlain, "b.jpg").writeBytes(payloadPlainB)

        val decryptedPath = CryptoManager.decryptFolder(
            context = testContext,
            folderPath = existingEncryptedAlbum.absolutePath,
            targetFolderOverride = targetPlain
        )
        assertEquals(targetPlain.canonicalPath, File(decryptedPath!!).canonicalPath)
        assertFalse(existingEncryptedAlbum.exists())

        val targetFiles = targetPlain.listFiles()!!.associateBy { it.name }
        assertEquals(setOf("a.jpg", "b.jpg", "b(1).jpg", "b(2).jpg", "c.jpg"), targetFiles.keys)
        assertArrayEquals(payloadSame, targetFiles["a.jpg"]!!.readBytes())
        assertArrayEquals(payloadPlainB, targetFiles["b.jpg"]!!.readBytes())
        assertArrayEquals(payloadNew, targetFiles["c.jpg"]!!.readBytes())
    }

    @Test
    fun cleanupEmptyEncryptedAlbumIfNeeded_removesEmptiedAlbumAndPreservesDefault() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val vaultRoot = File(tempRoot, "CleanupVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)

        val defaultAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, VAULT_DEFAULT_ALBUM_NAME)!!
        val customAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "TempAlbum")!!
        testContext.config.addEncryptedFolder(customAlbum.absolutePath)

        val item = encryptBytesForTest(customAlbum, "one.jpg", ByteArray(512) { 0x2A })
        assertFalse(CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(testContext, customAlbum))
        assertTrue(customAlbum.exists())

        item.delete()
        assertTrue(CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(testContext, customAlbum))
        assertFalse(customAlbum.exists())
        assertFalse(testContext.config.encryptedFolders.contains(customAlbum.absolutePath))

        // Default Secret Album must never be deleted even when empty
        assertFalse(CryptoManager.cleanupEmptyEncryptedAlbumIfNeeded(testContext, defaultAlbum))
        assertTrue(defaultAlbum.exists())
    }

    @Test
    fun inspectTransferConflict_plainAndEncryptedIndexesMatchExactNameSizeAndBytes() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMasterKey(masterKey)

        val srcDir = File(tempRoot, "conflict_src").apply { mkdirs() }
        val plainDestDir = File(tempRoot, "conflict_plain_dest").apply { mkdirs() }
        val encDestDir = File(tempRoot, "conflict_enc_dest").apply { mkdirs() }

        val payloadA = ByteArray(256) { (it * 7 + 3).toByte() }
        val payloadADiffByte = payloadA.copyOf().apply { this[200] = (this[200].toInt() xor 0x01).toByte() }
        val payloadShort = ByteArray(128) { 1 }

        val srcIdentical = File(srcDir, "same.jpg").apply { writeBytes(payloadA) }
        val srcSameSizeDiffByte = File(srcDir, "diff_byte.jpg").apply { writeBytes(payloadADiffByte) }
        val srcDiffSize = File(srcDir, "diff_size.jpg").apply { writeBytes(payloadShort) }
        val srcVariantOnly = File(srcDir, "photo.jpg").apply { writeBytes(payloadA) }

        File(plainDestDir, "same.jpg").writeBytes(payloadA)
        File(plainDestDir, "diff_byte.jpg").writeBytes(payloadA)
        File(plainDestDir, "diff_size.jpg").writeBytes(payloadA)
        File(plainDestDir, "photo(1).jpg").writeBytes(payloadA)

        val plainIndex = CryptoManager.TargetPlainIndex(plainDestDir)

        val plainIdentical = CryptoManager.inspectTransferConflict(
            sourceFile = srcIdentical,
            targetDir = plainDestDir,
            isTargetEncrypted = false,
            plainIndex = plainIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.IDENTICAL_SKIP, plainIdentical.status)

        val plainDiffByte = CryptoManager.inspectTransferConflict(
            sourceFile = srcSameSizeDiffByte,
            targetDir = plainDestDir,
            isTargetEncrypted = false,
            plainIndex = plainIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.NAME_CONFLICT, plainDiffByte.status)

        val plainDiffSize = CryptoManager.inspectTransferConflict(
            sourceFile = srcDiffSize,
            targetDir = plainDestDir,
            isTargetEncrypted = false,
            plainIndex = plainIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.NAME_CONFLICT, plainDiffSize.status)

        val plainVariantOnly = CryptoManager.inspectTransferConflict(
            sourceFile = srcVariantOnly,
            targetDir = plainDestDir,
            isTargetEncrypted = false,
            plainIndex = plainIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.NO_CONFLICT, plainVariantOnly.status)

        // Encrypted destination checks with TargetEncryptedIndex
        encryptBytesForTest(encDestDir, "same.jpg", payloadA)
        encryptBytesForTest(encDestDir, "diff_byte.jpg", payloadA)
        encryptBytesForTest(encDestDir, "diff_size.jpg", payloadA)
        encryptBytesForTest(encDestDir, "photo(1).jpg", payloadA)

        val encIndex = CryptoManager.TargetEncryptedIndex(encDestDir)

        val encIdentical = CryptoManager.inspectTransferConflict(
            sourceFile = srcIdentical,
            targetDir = encDestDir,
            isTargetEncrypted = true,
            encryptedIndex = encIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.IDENTICAL_SKIP, encIdentical.status)

        val encDiffByte = CryptoManager.inspectTransferConflict(
            sourceFile = srcSameSizeDiffByte,
            targetDir = encDestDir,
            isTargetEncrypted = true,
            encryptedIndex = encIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.NAME_CONFLICT, encDiffByte.status)

        val encDiffSize = CryptoManager.inspectTransferConflict(
            sourceFile = srcDiffSize,
            targetDir = encDestDir,
            isTargetEncrypted = true,
            encryptedIndex = encIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.NAME_CONFLICT, encDiffSize.status)

        val encVariantOnly = CryptoManager.inspectTransferConflict(
            sourceFile = srcVariantOnly,
            targetDir = encDestDir,
            isTargetEncrypted = true,
            encryptedIndex = encIndex
        )
        assertEquals(CryptoManager.TransferConflictStatus.NO_CONFLICT, encVariantOnly.status)
    }

    @Test
    fun syncVaultFromExternalDirectory_doesNotPolluteMetadataCacheAndRejectsCorruptMac() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val validHeaderBytes = "FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32)

        val vaultDir = File(tempRoot, "vault_sync_perf/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultDir, VAULT_HEADER_FILE).writeBytes(validHeaderBytes)
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultDir)

        val extRoot = File(tempRoot, "ext_sync_perf").apply { mkdirs() }
        val extSecrets = File(extRoot, SECRETS_DIR_NAME).apply { mkdirs() }

        val albumName = "SyncTestAlbum"
        val destAlbum = File(vaultDir, albumName).apply { mkdirs() }
        val srcAlbum = File(extSecrets, albumName).apply { mkdirs() }

        val payload1 = ByteArray(1024) { (it % 199).toByte() }
        val payload2 = ByteArray(1024) { ((it + 50) % 199).toByte() }

        // Pre-existing file with same name & size as payload2, so sync hits Stage 3 content comparison
        encryptBytesForTest(destAlbum, "colliding.jpg", payload1)

        // Fresh file in external album
        encryptBytesForTest(srcAlbum, "fresh.jpg", payload1)

        // Corrupt the final MAC byte of a colliding source file so Stage 3/4 rejects it cleanly
        val corruptSrc = encryptBytesForTest(srcAlbum, "colliding.jpg", payload2)
        val raw = corruptSrc.readBytes()
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0xFF).toByte()
        corruptSrc.writeBytes(raw)

        // Clear metadataCache before sync
        val cacheField = CryptoManager::class.java.getDeclaredField("metadataCache").apply { isAccessible = true }
        val cacheMap = cacheField.get(CryptoManager) as MutableMap<*, *>
        cacheMap.clear()

        val result = CryptoManager.syncVaultFromExternalDirectory(testContext, extRoot, requirePendingManifest = false)
        assertTrue(result != null)
        assertEquals(1, result!!.copiedCount)
        assertEquals(1, result.failedCount)
        assertEquals(0, cacheMap.size)
    }

    @Test
    fun syncVaultFromExternalDirectory_requiresAndConsumesPendingSyncManifest() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMasterKey(masterKey)

        val phoneVault = File(tempRoot, "phone_pending/Calypso Gallery/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val extSecrets = File(tempRoot, "ext_pending/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(extSecrets, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        val extAlbum = File(extSecrets, "PendingAlbum").apply { mkdirs() }

        // Unlisted corrupt .bin in archive (bit-rot) must be ignored when .pending_sync is used
        val corruptArchiveBin = File(extAlbum, "corrupt_old.bin").apply { writeBytes(ByteArray(256) { 7 }) }
        assertTrue(corruptArchiveBin.exists())

        val newBin = encryptBytesForTest(extAlbum, "new_pending.jpg", "fresh-cli-photo".toByteArray())

        // 1. Without .pending_sync, strict sync returns 0/0/0/0 and refuses to scan archive
        val withoutManifest = CryptoManager.syncVaultFromExternalDirectory(testContext, extSecrets)!!
        assertEquals(0, withoutManifest.copiedCount)
        assertEquals(0, withoutManifest.failedCount)

        // 2. Write .pending_sync listing ONLY the newly encrypted .bin
        val pendingFile = File(extSecrets, ".pending_sync")
        pendingFile.writeText("PendingAlbum/${newBin.name}\n", Charsets.UTF_8)

        val withManifest = CryptoManager.syncVaultFromExternalDirectory(testContext, extSecrets)!!
        assertEquals(1, withManifest.copiedCount)
        assertEquals(0, withManifest.failedCount)
        assertFalse(pendingFile.exists())

        // 3. Repeating sync immediately after must do nothing
        val repeatAfterClear = CryptoManager.syncVaultFromExternalDirectory(testContext, extSecrets)!!
        assertEquals(0, repeatAfterClear.copiedCount)
        assertEquals(0, repeatAfterClear.failedCount)
    }

    @Test
    fun inspectTransferConflict_fastSamplingModeAndExactModeBehavior() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMasterKey(masterKey)

        val srcDir = File(tempRoot, "fast_src").apply { mkdirs() }
        val plainDestDir = File(tempRoot, "fast_plain_dest").apply { mkdirs() }
        val encDestDir = File(tempRoot, "fast_enc_dest").apply { mkdirs() }

        // 9 MB (> 8 MB threshold)
        val totalSize = 9 * 1024 * 1024
        val basePayload = ByteArray(totalSize) { (it % 251).toByte() }
        val diffAtMiddleSample = basePayload.copyOf().apply {
            val idx = totalSize / 2
            this[idx] = (this[idx].toInt() xor 0xFF).toByte()
        }
        // Offset 512 KB lies strictly between offset 0..256 KB and size/4 (2.25 MB)
        val diffBetweenSamples = basePayload.copyOf().apply {
            val idx = 512 * 1024
            this[idx] = (this[idx].toInt() xor 0xFF).toByte()
        }

        val srcIdentical = File(srcDir, "movie.mp4").apply { writeBytes(basePayload) }
        val srcMiddleDiff = File(srcDir, "middle_diff.mp4").apply { writeBytes(diffAtMiddleSample) }
        val srcInteriorDiff = File(srcDir, "interior_diff.mp4").apply { writeBytes(diffBetweenSamples) }

        File(plainDestDir, "movie.mp4").writeBytes(basePayload)
        File(plainDestDir, "middle_diff.mp4").writeBytes(basePayload)
        File(plainDestDir, "interior_diff.mp4").writeBytes(basePayload)

        val plainIndex = CryptoManager.TargetPlainIndex(plainDestDir)

        assertEquals(
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP,
            CryptoManager.inspectTransferConflict(srcIdentical, plainDestDir, isTargetEncrypted = false, plainIndex = plainIndex, fastSampling = true).status
        )
        val plainMiddleConflict = CryptoManager.inspectTransferConflict(srcMiddleDiff, plainDestDir, isTargetEncrypted = false, plainIndex = plainIndex, fastSampling = true)
        assertEquals(CryptoManager.TransferConflictStatus.NAME_CONFLICT, plainMiddleConflict.status)
        assertEquals(File(plainDestDir, "middle_diff.mp4").absolutePath, plainMiddleConflict.existingFile?.absolutePath)
        // Fast mode skips unsampled gap -> IDENTICAL_SKIP; exact mode catches 1-byte gap difference -> NAME_CONFLICT
        assertEquals(
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP,
            CryptoManager.inspectTransferConflict(srcInteriorDiff, plainDestDir, isTargetEncrypted = false, plainIndex = plainIndex, fastSampling = true).status
        )
        assertEquals(
            CryptoManager.TransferConflictStatus.NAME_CONFLICT,
            CryptoManager.inspectTransferConflict(srcInteriorDiff, plainDestDir, isTargetEncrypted = false, plainIndex = plainIndex, fastSampling = false).status
        )

        // Plain -> Encrypted fast sampling vs exact check
        encryptBytesForTest(encDestDir, "movie.mp4", basePayload)
        val encMiddleBin = encryptBytesForTest(encDestDir, "middle_diff.mp4", basePayload)
        encryptBytesForTest(encDestDir, "interior_diff.mp4", basePayload)
        val encIndex = CryptoManager.TargetEncryptedIndex(encDestDir)

        assertEquals(
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP,
            CryptoManager.inspectTransferConflict(srcIdentical, encDestDir, isTargetEncrypted = true, encryptedIndex = encIndex, fastSampling = true).status
        )
        val encMiddleConflict = CryptoManager.inspectTransferConflict(srcMiddleDiff, encDestDir, isTargetEncrypted = true, encryptedIndex = encIndex, fastSampling = true)
        assertEquals(CryptoManager.TransferConflictStatus.NAME_CONFLICT, encMiddleConflict.status)
        assertEquals(encMiddleBin.absolutePath, encMiddleConflict.existingFile?.absolutePath)
        assertEquals(
            CryptoManager.TransferConflictStatus.NAME_CONFLICT,
            CryptoManager.inspectTransferConflict(srcInteriorDiff, encDestDir, isTargetEncrypted = true, encryptedIndex = encIndex, fastSampling = false).status
        )
    }

    @Test
    fun dateTieBreak_ask_keepsOldAndNewBitIdenticalCopies_and_deduplicatesMatchingTimestamps() {
        val masterKey = ByteArray(32) { (it + 7).toByte() }
        setMasterKey(masterKey)
        testContext.config.conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG
        testContext.config.dateTieBreakMode = DATE_TIE_BREAK_ASK

        val payload = ByteArray(4096) { (it * 13 + 3).toByte() }
        val ts2021 = 1_620_000_000_000L // May 2021
        val ts2026 = 1_770_000_000_000L // Feb 2026

        // 1. encryptFileToDirectory: keep both 2021 and 2026 copies as photo.jpg and photo(1).jpg, idempotently skip repeat 2026
        val plainSrc = File(tempRoot, "plain_src").apply { mkdirs() }
        val encTarget = File(tempRoot, "enc_target").apply { mkdirs() }

        val src2021 = File(plainSrc, "photo.jpg").apply {
            writeBytes(payload)
            setLastModified(ts2021)
        }
        val enc2021 = CryptoManager.encryptFileToDirectory(testContext, src2021, encTarget, wipeSource = false)
        assertNotNull(enc2021)
        assertEquals("photo.jpg", CryptoManager.readMetadata(enc2021!!, includeThumbnail = false, cacheResult = false)!!.originalName)

        val src2026 = File(plainSrc, "photo.jpg").apply {
            writeBytes(payload)
            setLastModified(ts2026)
        }
        val enc2026 = CryptoManager.encryptFileToDirectory(testContext, src2026, encTarget, wipeSource = false)
        assertNotNull(enc2026)
        assertFalse(enc2026!!.absolutePath == enc2021.absolutePath)
        assertEquals("photo(1).jpg", CryptoManager.readMetadata(enc2026, includeThumbnail = false, cacheResult = false)!!.originalName)

        // Repeat encrypt of 2026 copy must idempotently match photo(1).jpg (with wipeSource=true -> delete source without creating photo(2).jpg)
        val src2026Repeat = File(tempRoot, "repeat/photo.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(payload)
            setLastModified(ts2026 + 1_200L) // within FAT32 2s tolerance
        }
        val dedupResult = CryptoManager.encryptFileToDirectory(testContext, src2026Repeat, encTarget, wipeSource = true)
        assertEquals(enc2026.absolutePath, dedupResult?.absolutePath)
        assertFalse(src2026Repeat.exists())
        assertEquals(2, encTarget.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // 2. decryptFileToDirectory: keep both 2021 and 2026 plaintext copies, idempotently match 2026 on repeat
        val plainOut = File(tempRoot, "plain_out").apply { mkdirs() }
        val dec2021 = CryptoManager.decryptFileToDirectory(testContext, enc2021, plainOut, deleteEncryptedSource = false)
        assertEquals("photo.jpg", dec2021?.name)
        val dec2026 = CryptoManager.decryptFileToDirectory(testContext, enc2026, plainOut, deleteEncryptedSource = false)
        assertEquals("photo(1).jpg", dec2026?.name)
        val enc2026Copy = encryptBytesForTest(
            targetDir = File(tempRoot, "enc_extra").apply { mkdirs() },
            originalName = "photo.jpg",
            payload = payload,
            lastModifiedOverride = ts2026 + 1_000L,
            dateTakenOverride = ts2026 + 1_000L
        )
        val dec2026Repeat = CryptoManager.decryptFileToDirectory(testContext, enc2026Copy, plainOut, deleteEncryptedSource = true)
        assertEquals(dec2026?.absolutePath, dec2026Repeat?.absolutePath)
        assertFalse(enc2026Copy.exists())
        assertEquals(2, plainOut.listFiles()?.size)

        // 3. copyOrMoveEncryptedFileToDirectory: keep both 2021 and 2026 .bin inside another album, idempotently match on repeat
        val otherAlbum = File(tempRoot, "enc_other").apply { mkdirs() }
        val copied2021 = CryptoManager.copyOrMoveEncryptedFileToDirectory(testContext, enc2021, otherAlbum, isMove = false)
        val copied2026 = CryptoManager.copyOrMoveEncryptedFileToDirectory(testContext, enc2026, otherAlbum, isMove = false)
        assertNotNull(copied2021)
        assertNotNull(copied2026)
        assertEquals(2, otherAlbum.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })
        assertEquals(
            copied2026!!.absolutePath,
            CryptoManager.copyOrMoveEncryptedFileToDirectory(testContext, enc2026, otherAlbum, isMove = false)?.absolutePath
        )
        assertEquals(2, otherAlbum.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        // 4. inspectTransferConflict: different timestamp -> NAME_CONFLICT; within 2s -> IDENTICAL_SKIP; across family -> IDENTICAL_SKIP
        val probeDir = File(tempRoot, "probe").apply { mkdirs() }
        val probe2021 = File(probeDir, "photo.jpg").apply {
            writeBytes(payload)
            setLastModified(ts2021)
        }
        val probe2026 = File(tempRoot, "probe_new/photo.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(payload)
            setLastModified(ts2026)
        }
        val probe2021Tolerance = File(tempRoot, "probe_tol/photo.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(payload)
            setLastModified(ts2021 + 1_500L)
        }
        val encTargetOne = File(tempRoot, "enc_one").apply { mkdirs() }
        CryptoManager.copyOrMoveEncryptedFileToDirectory(testContext, enc2021, encTargetOne, isMove = false)

        // With only 2021 in target, incoming 2026 must trigger NAME_CONFLICT (not IDENTICAL_SKIP), while 2021+1.5s -> IDENTICAL_SKIP
        assertEquals(
            CryptoManager.TransferConflictStatus.NAME_CONFLICT,
            CryptoManager.inspectTransferConflict(probe2026, encTargetOne, isTargetEncrypted = true).status
        )
        assertEquals(
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP,
            CryptoManager.inspectTransferConflict(probe2021Tolerance, encTargetOne, isTargetEncrypted = true).status
        )
        // With both 2021 (photo.jpg) and 2026 (photo(1).jpg) in encTarget, incoming 2026 must hit IDENTICAL_SKIP across family
        assertEquals(
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP,
            CryptoManager.inspectTransferConflict(probe2026, encTarget, isTargetEncrypted = true).status
        )
        assertEquals(
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP,
            CryptoManager.inspectTransferConflict(probe2026, plainOut, isTargetEncrypted = false).status
        )
        assertEquals(
            CryptoManager.TransferConflictStatus.NAME_CONFLICT,
            CryptoManager.inspectTransferConflict(probe2026, probeDir, isTargetEncrypted = false).status
        )
        assertEquals(
            CryptoManager.TransferConflictStatus.IDENTICAL_SKIP,
            CryptoManager.inspectTransferConflict(probe2021Tolerance, probeDir, isTargetEncrypted = false).status
        )
        assertTrue(probe2021.exists())
    }

    @Test
    fun dateTieBreak_keepTarget_deduplicatesIdenticalContentWithDifferentDates() {
        val masterKey = ByteArray(32) { (it + 5).toByte() }
        setMasterKey(masterKey)
        testContext.config.conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG
        testContext.config.dateTieBreakMode = DATE_TIE_BREAK_KEEP_TARGET

        val payload = ByteArray(4096) { (it * 3 + 1).toByte() }
        val srcDir = File(tempRoot, "tie_src").apply { mkdirs() }
        val target = File(tempRoot, "tie_target").apply { mkdirs() }

        val first = CryptoManager.encryptFileToDirectory(
            testContext,
            File(srcDir, "photo.jpg").apply {
                writeBytes(payload)
                setLastModified(1_620_000_000_000L)
            },
            target,
            wipeSource = false
        )
        assertNotNull(first)

        val second = CryptoManager.encryptFileToDirectory(
            testContext,
            File(srcDir, "photo.jpg").apply {
                writeBytes(payload)
                setLastModified(1_770_000_000_000L)
            },
            target,
            wipeSource = false
        )
        assertEquals(first!!.absolutePath, second?.absolutePath)
        assertEquals(1, target.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })
    }

    @Test
    fun syncVaultFromExternalDirectory_keepsDifferentTimestampCopyAsCollisionVariant_andSkipsRepeat() {
        val masterKey = ByteArray(32) { (it + 19).toByte() }
        setMasterKey(masterKey)

        val vaultRoot = CryptoManager.createVaultRoot(testContext, File(tempRoot, "vault_host").apply { mkdirs() })!!
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)
        val vaultAlbum = File(vaultRoot, "ab12cd").apply { mkdirs() }
        writeMetaV2(vaultAlbum, masterKey, "Trips")

        val payload = ByteArray(8192) { (it * 7 + 1).toByte() }
        val ts2021 = 1_620_000_000_000L
        val ts2026 = 1_770_000_000_000L

        encryptBytesForTest(
            targetDir = vaultAlbum,
            originalName = "photo.jpg",
            payload = payload,
            lastModifiedOverride = ts2021,
            dateTakenOverride = ts2021
        )

        val extRoot = File(tempRoot, "ext_sync").apply { mkdirs() }
        val extAlbum = File(extRoot, "ef99aa").apply { mkdirs() }
        writeMetaV2(extAlbum, masterKey, "Trips")
        encryptBytesForTest(
            targetDir = extAlbum,
            originalName = "photo.jpg",
            payload = payload,
            lastModifiedOverride = ts2026,
            dateTakenOverride = ts2026
        )

        val firstSync = CryptoManager.syncVaultFromExternalDirectory(
            context = testContext,
            sourceDir = extRoot,
            requirePendingManifest = false
        )
        assertNotNull(firstSync)
        assertEquals(0, firstSync!!.skippedDuplicateCount)
        assertEquals(1, firstSync.renamedCount)
        assertEquals(2, vaultAlbum.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })

        val secondSync = CryptoManager.syncVaultFromExternalDirectory(
            context = testContext,
            sourceDir = extRoot,
            requirePendingManifest = false
        )
        assertNotNull(secondSync)
        assertEquals(1, secondSync!!.skippedDuplicateCount)
        assertEquals(0, secondSync.copiedCount + secondSync.renamedCount)
        assertEquals(2, vaultAlbum.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })
    }

    @Test
    fun encryptFolder_and_decryptFolder_preserveSameTimestampSiblingVariantsInSameFolder() {
        val masterKey = ByteArray(32) { (it + 31).toByte() }
        setMasterKey(masterKey)

        val vaultHost = File(tempRoot, "vault_siblings").apply { mkdirs() }
        val siblingVaultRoot = CryptoManager.createVaultRoot(testContext, vaultHost)!!
        File(siblingVaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        CryptoManager.setVaultRootLocation(testContext, siblingVaultRoot)
        testContext.config.internalStoragePath = tempRoot.absolutePath

        val payload = ByteArray(2048) { (it * 5 + 11).toByte() }
        val sharedTs = 1_700_000_000_000L
        val srcFolder = File(tempRoot, "SiblingAlbum").apply { mkdirs() }
        File(srcFolder, "photo.jpg").apply {
            writeBytes(payload)
            setLastModified(sharedTs)
        }
        File(srcFolder, "photo(1).jpg").apply {
            writeBytes(payload)
            setLastModified(sharedTs)
        }

        val encAlbumPath = CryptoManager.encryptFolder(testContext, srcFolder.absolutePath)
        assertNotNull(encAlbumPath)
        val encAlbum = File(encAlbumPath!!)
        val encFiles = encAlbum.listFiles()?.filter { it.name.endsWith(ENCRYPTED_BIN_EXT) }.orEmpty()
        assertEquals(2, encFiles.size)
        val encOriginalNames = encFiles.map {
            CryptoManager.readMetadata(it, includeThumbnail = false, cacheResult = false)!!.originalName
        }.toSet()
        assertEquals(setOf("photo.jpg", "photo(1).jpg"), encOriginalNames)

        val restoreTarget = File(tempRoot, "restored/SiblingAlbum").apply { parentFile!!.mkdirs() }
        val decFolderPath = CryptoManager.decryptFolder(testContext, encAlbum.absolutePath, targetFolderOverride = restoreTarget)
        assertNotNull(decFolderPath)
        val decFolder = File(decFolderPath!!)
        val decNames = decFolder.listFiles()?.map { it.name }?.toSet().orEmpty()
        assertEquals(setOf("photo.jpg", "photo(1).jpg"), decNames)
    }

    @Test
    fun config_conflictAndSkippedEncryptMode_haveExpectedDefaultsAndIndependentSettings() {
        assertEquals(CONFLICT_HANDLING_STANDARD_DIALOG, testContext.config.conflictHandlingMode)
        assertEquals(CRYPTO_SKIPPED_FILES_ASK, testContext.config.skippedEncryptFilesMode)

        testContext.config.skippedEncryptFilesMode = CRYPTO_SKIPPED_FILES_KEEP
        assertEquals(CRYPTO_SKIPPED_FILES_KEEP, testContext.config.skippedEncryptFilesMode)

        testContext.config.albumCryptoConflictMode = ALBUM_CRYPTO_CONFLICT_MERGE
        assertEquals(ALBUM_CRYPTO_CONFLICT_MERGE, testContext.config.albumCryptoConflictMode)
    }

    @Test
    fun inspectStandardFilenameConflict_matchesByNameOnlyAndIgnoresVariants() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMasterKey(masterKey)

        val srcDir = File(tempRoot, "std_src").apply { mkdirs() }
        val plainDestDir = File(tempRoot, "std_plain_dest").apply { mkdirs() }
        val encDestDir = File(tempRoot, "std_enc_dest").apply { mkdirs() }

        val payloadSrc = ByteArray(128) { 0x11 }
        val payloadOther = ByteArray(64) { 0x22 }

        val srcConflict = File(srcDir, "same_name.jpg").apply { writeBytes(payloadSrc) }
        val srcVariantOnly = File(srcDir, "unique.jpg").apply { writeBytes(payloadSrc) }

        File(plainDestDir, "same_name.jpg").writeBytes(payloadOther)
        File(plainDestDir, "unique(1).jpg").writeBytes(payloadSrc)

        val plainIndex = CryptoManager.TargetPlainIndex(plainDestDir)
        assertEquals(
            "same_name.jpg",
            CryptoManager.inspectStandardFilenameConflict(srcConflict, plainDestDir, isTargetEncrypted = false, plainIndex = plainIndex)
        )
        assertNull(
            CryptoManager.inspectStandardFilenameConflict(srcVariantOnly, plainDestDir, isTargetEncrypted = false, plainIndex = plainIndex)
        )

        encryptBytesForTest(encDestDir, "same_name.jpg", payloadOther)
        encryptBytesForTest(encDestDir, "unique(1).jpg", payloadSrc)

        val encIndex = CryptoManager.TargetEncryptedIndex(encDestDir)
        assertEquals(
            "same_name.jpg",
            CryptoManager.inspectStandardFilenameConflict(srcConflict, encDestDir, isTargetEncrypted = true, encryptedIndex = encIndex)
        )
        assertNull(
            CryptoManager.inspectStandardFilenameConflict(srcVariantOnly, encDestDir, isTargetEncrypted = true, encryptedIndex = encIndex)
        )
    }

    @Test
    fun encryptFolder_mergeWithConflictsAndDeferredCollectors_handlesStandardAndSmartModes() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val vaultRoot = File(tempRoot, "MergeEncVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)
        testContext.config.internalStoragePath = tempRoot.absolutePath

        val existingEncAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "MergeAlbum")!!
        val oldPayload = ByteArray(256) { 0x10 }
        val identicalPayload = ByteArray(256) { 0x20 }
        val newPayload = ByteArray(256) { 0x30 }

        encryptBytesForTest(existingEncAlbum, "skip.jpg", oldPayload)
        encryptBytesForTest(existingEncAlbum, "over.jpg", oldPayload)
        val identicalTargetEnc = encryptBytesForTest(existingEncAlbum, "same.jpg", identicalPayload)

        // 1) Smart merge: same.jpg identical skipped & deferred; skip.jpg CONFLICT_SKIP; over.jpg CONFLICT_OVERWRITE; fresh.jpg moved
        val srcFolderSmart = File(tempRoot, "MergeAlbum").apply { mkdirs() }
        val srcSkip = File(srcFolderSmart, "skip.jpg").apply { writeBytes(newPayload) }
        val srcOver = File(srcFolderSmart, "over.jpg").apply { writeBytes(newPayload) }
        val srcSame = File(srcFolderSmart, "same.jpg").apply { writeBytes(identicalPayload) }
        val srcFresh = File(srcFolderSmart, "fresh.jpg").apply { writeBytes(newPayload) }

        val mergeProbe = CryptoManager.findMergeTargetForEncryptFolder(testContext, srcFolderSmart.absolutePath)
        assertNotNull(mergeProbe)
        assertEquals(existingEncAlbum.absolutePath, mergeProbe!!.first.absolutePath)
        assertEquals(4, mergeProbe.second.size)

        val deferredSkippedSmart = mutableListOf<File>()
        val deferredIdenticalSmart = mutableMapOf<String, File>()
        val smartOut = CryptoManager.encryptFolder(
            context = testContext,
            folderPath = srcFolderSmart.absolutePath,
            options = CryptoManager.AlbumCryptoOptions(
                resolution = CryptoManager.AlbumConflictResolution.MERGE,
                deleteIdenticalSource = false,
                conflictResolutions = mapOf(
                    srcSkip.absolutePath to CONFLICT_SKIP,
                    srcOver.absolutePath to CONFLICT_OVERWRITE
                ),
                skipDuplicateCheck = false,
                precheckedNonIdenticalPaths = setOf(srcSkip.absolutePath, srcOver.absolutePath, srcFresh.absolutePath),
                deferredSkippedSourceCollector = deferredSkippedSmart,
                deferredIdenticalTargetMap = deferredIdenticalSmart
            )
        )
        assertEquals(existingEncAlbum.absolutePath, smartOut)
        assertTrue(srcSkip.exists())
        assertTrue(srcSame.exists())
        assertFalse(srcOver.exists())
        assertFalse(srcFresh.exists())
        assertEquals(setOf(srcSkip.absolutePath, srcSame.absolutePath), deferredSkippedSmart.map { it.absolutePath }.toSet())
        assertEquals(identicalTargetEnc.absolutePath, deferredIdenticalSmart[srcSame.absolutePath]?.absolutePath)

        val encEntriesAfterSmart = existingEncAlbum.listFiles()!!
            .filter { it.name.endsWith(ENCRYPTED_BIN_EXT) }
            .associate { CryptoManager.readMetadata(it)!!.originalName to CryptoManager.openDecryptedContentStream(it)!!.use { s -> s.readBytes() } }
        assertArrayEquals(oldPayload, encEntriesAfterSmart["skip.jpg"])
        assertArrayEquals(newPayload, encEntriesAfterSmart["over.jpg"])
        assertArrayEquals(newPayload, encEntriesAfterSmart["fresh.jpg"])

        // 2) Standard merge: skipDuplicateCheck = true still runs when same-name file has CONFLICT_KEEP_BOTH
        val srcFolderStd = File(tempRoot, "std_holder/MergeAlbum").apply { mkdirs() }
        val stdSameName = File(srcFolderStd, "same.jpg").apply { writeBytes(identicalPayload) }
        val deferredSkippedStd = mutableListOf<File>()
        val stdOut = CryptoManager.encryptFolder(
            context = testContext,
            folderPath = srcFolderStd.absolutePath,
            options = CryptoManager.AlbumCryptoOptions(
                resolution = CryptoManager.AlbumConflictResolution.MERGE,
                deleteIdenticalSource = false,
                conflictResolutions = mapOf(stdSameName.absolutePath to CONFLICT_KEEP_BOTH),
                skipDuplicateCheck = true,
                deferredSkippedSourceCollector = deferredSkippedStd
            )
        )
        assertEquals(existingEncAlbum.absolutePath, stdOut)
        assertFalse(stdSameName.exists())
        assertTrue(deferredSkippedStd.isEmpty())
        val encNamesAfterStd = existingEncAlbum.listFiles()!!
            .filter { it.name.endsWith(ENCRYPTED_BIN_EXT) }
            .mapNotNull { CryptoManager.readMetadata(it)?.originalName }
        assertTrue(encNamesAfterStd.containsAll(listOf("same.jpg", "same(1).jpg")))
    }

    @Test
    fun decryptFolder_mergeWithOverwriteSkipAndIdentical_behavesSafely() {
        val random = SecureRandom()
        val masterKey = ByteArray(32).also { random.nextBytes(it) }
        val vaultRoot = File(tempRoot, "MergeDecVault/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(vaultRoot, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        setMasterKey(masterKey)
        CryptoManager.setVaultRootLocation(testContext, vaultRoot)

        val encAlbum = CryptoManager.createHiddenPhysicalAlbum(testContext, "DecMergeAlbum")!!
        val oldPlain = ByteArray(256) { 0x41 }
        val identicalPayload = ByteArray(256) { 0x42 }
        val newDecPayload = ByteArray(256) { 0x43 }

        val encSkip = encryptBytesForTest(encAlbum, "skip.jpg", newDecPayload)
        val encOver = encryptBytesForTest(encAlbum, "over.jpg", newDecPayload)
        val encSame = encryptBytesForTest(encAlbum, "same.jpg", identicalPayload)
        encryptBytesForTest(encAlbum, "fresh.jpg", newDecPayload)

        val targetPlainDir = File(tempRoot, "PlainOut/DecMergeAlbum").apply { mkdirs() }
        val plainSkip = File(targetPlainDir, "skip.jpg").apply { writeBytes(oldPlain) }
        val plainOver = File(targetPlainDir, "over.jpg").apply { writeBytes(oldPlain) }
        val plainSame = File(targetPlainDir, "same.jpg").apply { writeBytes(identicalPayload) }

        val mergeProbe = CryptoManager.findMergeTargetForDecryptFolder(
            context = testContext,
            folderPath = encAlbum.absolutePath,
            targetFolderOverride = targetPlainDir
        )
        assertNotNull(mergeProbe)
        assertEquals(targetPlainDir.absolutePath, mergeProbe!!.first.absolutePath)
        assertEquals(4, mergeProbe.second.size)

        val deferredSkipped = mutableListOf<File>()
        val deferredIdentical = mutableMapOf<String, File>()
        val decOut = CryptoManager.decryptFolder(
            context = testContext,
            folderPath = encAlbum.absolutePath,
            targetFolderOverride = targetPlainDir,
            options = CryptoManager.AlbumCryptoOptions(
                resolution = CryptoManager.AlbumConflictResolution.MERGE,
                deleteIdenticalSource = false,
                conflictResolutions = mapOf(
                    encSkip.absolutePath to CONFLICT_SKIP,
                    encOver.absolutePath to CONFLICT_OVERWRITE
                ),
                skipDuplicateCheck = false,
                precheckedNonIdenticalPaths = setOf(encSkip.absolutePath, encOver.absolutePath),
                deferredSkippedSourceCollector = deferredSkipped,
                deferredIdenticalTargetMap = deferredIdentical
            )
        )
        assertEquals(targetPlainDir.absolutePath, decOut)
        assertArrayEquals(oldPlain, plainSkip.readBytes())
        assertArrayEquals(newDecPayload, plainOver.readBytes())
        assertArrayEquals(newDecPayload, File(targetPlainDir, "fresh.jpg").readBytes())
        assertTrue(encSkip.exists())
        assertTrue(encSame.exists())
        assertFalse(encOver.exists())
        assertEquals(setOf(encSkip.absolutePath, encSame.absolutePath), deferredSkipped.map { it.absolutePath }.toSet())
        assertEquals(plainSame.absolutePath, deferredIdentical[encSame.absolutePath]?.absolutePath)
    }

    @Test
    fun compareFilesForSmartConflict_distinguishesByteMismatchAndDateDifferences() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMasterKey(masterKey)

        val dirA = File(tempRoot, "smart_cmp_a").apply { mkdirs() }
        val dirB = File(tempRoot, "smart_cmp_b").apply { mkdirs() }

        val payload = ByteArray(512) { (it * 3 + 5).toByte() }
        val payloadFlipped = payload.copyOf().apply { this[250] = (this[250].toInt() xor 0x01).toByte() }
        val modTime1 = 1_700_000_000_000L
        val modTime2 = 1_700_100_000_000L
        val takenTime1 = 1_690_000_000_000L
        val takenTime2 = 1_695_000_000_000L

        // 1) Byte mismatch on plain files with identical timestamps -> CONTENT_DIFFERS
        val plainBitA = File(dirA, "bitrot.jpg").apply {
            writeBytes(payload)
            setLastModified(modTime1)
        }
        val plainBitB = File(dirB, "bitrot.jpg").apply {
            writeBytes(payloadFlipped)
            setLastModified(modTime1)
        }
        assertEquals(
            CryptoManager.SmartConflictDifference.CONTENT_DIFFERS,
            CryptoManager.compareFilesForSmartConflict(plainBitA, plainBitB).difference
        )

        // 2) Same bytes, only lastModified differs -> MODIFIED_DATE_DIFFERS
        val plainModA = File(dirA, "mod.jpg").apply {
            writeBytes(payload)
            setLastModified(modTime1)
        }
        val plainModB = File(dirB, "mod.jpg").apply {
            writeBytes(payload)
            setLastModified(modTime2)
        }
        val modRes = CryptoManager.compareFilesForSmartConflict(plainModA, plainModB)
        assertEquals(CryptoManager.SmartConflictDifference.MODIFIED_DATE_DIFFERS, modRes.difference)
        assertFalse(modRes.hasDistinctDateTaken)

        // 3) Same bytes & same lastModified on encrypted bins, different dateTaken -> DATE_TAKEN_DIFFERS
        val encTakenA = encryptBytesForTest(dirA, "taken.jpg", payload, lastModifiedOverride = modTime1, dateTakenOverride = takenTime1)
        val encTakenB = encryptBytesForTest(dirB, "taken.jpg", payload, lastModifiedOverride = modTime1, dateTakenOverride = takenTime2)
        val takenRes = CryptoManager.compareFilesForSmartConflict(encTakenA, encTakenB)
        assertEquals(CryptoManager.SmartConflictDifference.DATE_TAKEN_DIFFERS, takenRes.difference)
        assertTrue(takenRes.hasDistinctDateTaken)

        // 4) Same bytes, both dateTaken and lastModified differ -> BOTH_DATES_DIFFER
        val encBothA = encryptBytesForTest(dirA, "both.jpg", payload, lastModifiedOverride = modTime1, dateTakenOverride = takenTime1)
        val encBothB = encryptBytesForTest(dirB, "both.jpg", payload, lastModifiedOverride = modTime2, dateTakenOverride = takenTime2)
        val bothRes = CryptoManager.compareFilesForSmartConflict(encBothA, encBothB)
        assertEquals(CryptoManager.SmartConflictDifference.BOTH_DATES_DIFFER, bothRes.difference)
        assertTrue(bothRes.hasDistinctDateTaken)

        // 5) Byte mismatch AND date difference -> CONTENT_DIFFERS takes precedence
        val encContentAndDate = encryptBytesForTest(dirB, "taken.jpg", payloadFlipped, lastModifiedOverride = modTime2, dateTakenOverride = takenTime2)
        assertEquals(
            CryptoManager.SmartConflictDifference.CONTENT_DIFFERS,
            CryptoManager.compareFilesForSmartConflict(encTakenA, encContentAndDate).difference
        )
    }

    @Test
    fun resolveSkippedCryptoMessageRes_selectsDynamicMessageByModeAndSkipOrigin() {
        // Standard mode: always uses user-skipped phrasing regardless of flags
        assertEquals(
            R.string.skipped_crypto_files_encrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = true,
                conflictHandlingMode = CONFLICT_HANDLING_STANDARD_DIALOG,
                hasUserSkipped = true,
                hasIdenticalSkipped = false
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_encrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = true,
                conflictHandlingMode = CONFLICT_HANDLING_STANDARD_DIALOG,
                hasUserSkipped = false,
                hasIdenticalSkipped = true
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_decrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = false,
                conflictHandlingMode = CONFLICT_HANDLING_STANDARD_DIALOG,
                hasUserSkipped = true,
                hasIdenticalSkipped = true
            )
        )

        // Smart dialog: user-only, identical-only, or both
        assertEquals(
            R.string.skipped_crypto_files_encrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = true,
                conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG,
                hasUserSkipped = true,
                hasIdenticalSkipped = false
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_identical_encrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = true,
                conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG,
                hasUserSkipped = false,
                hasIdenticalSkipped = true
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_both_encrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = true,
                conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG,
                hasUserSkipped = true,
                hasIdenticalSkipped = true
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_decrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = false,
                conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG,
                hasUserSkipped = true,
                hasIdenticalSkipped = false
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_identical_decrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = false,
                conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG,
                hasUserSkipped = false,
                hasIdenticalSkipped = true
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_both_decrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = false,
                conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG,
                hasUserSkipped = true,
                hasIdenticalSkipped = true
            )
        )

        // Auto mode: always uses identical-only phrasing
        assertEquals(
            R.string.skipped_crypto_files_identical_encrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = true,
                conflictHandlingMode = CONFLICT_HANDLING_AUTO,
                hasUserSkipped = false,
                hasIdenticalSkipped = true
            )
        )
        assertEquals(
            R.string.skipped_crypto_files_identical_decrypt_message,
            resolveSkippedCryptoMessageRes(
                isEncrypt = false,
                conflictHandlingMode = CONFLICT_HANDLING_AUTO,
                hasUserSkipped = false,
                hasIdenticalSkipped = true
            )
        )
    }

    private fun encryptBytesForTest(
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

    private fun writeMetaV2(folder: File, masterKey: ByteArray, rawName: String) {
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
