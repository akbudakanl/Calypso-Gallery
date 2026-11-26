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


class CustomVaultLocationTest : VaultTestBase() {

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

        // 6. CLI staging wins over a sibling Secrets folder (no "Secrets" assumption).
        val stagingRoot = File(tempRoot, "StagingRoot").apply { mkdirs() }
        File(File(stagingRoot, CIPHER_APP_DIR_NAME), SECRETS_DIR_NAME).apply { mkdirs() }
        val cliFolder = File(File(stagingRoot, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME).apply { mkdirs() }
        assertEquals(cliFolder.absolutePath, CryptoManager.resolveSyncSourceRoot(stagingRoot).absolutePath)

        // 7. CLI folder directly under the picked root, arbitrary vault location.
        val customRoot = File(tempRoot, "CustomVaultHost").apply { mkdirs() }
        val directCli = File(customRoot, CLI_STAGING_DIR_NAME).apply { mkdirs() }
        assertEquals(directCli.absolutePath, CryptoManager.resolveSyncSourceRoot(customRoot).absolutePath)
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

}
