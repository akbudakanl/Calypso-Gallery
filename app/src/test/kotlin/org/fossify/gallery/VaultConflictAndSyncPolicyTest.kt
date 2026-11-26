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


class VaultConflictAndSyncPolicyTest : VaultTestBase() {

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

        // Unlisted corrupt .bin in archive (bit-rot) must be ignored when pending_sync is used
        val corruptArchiveBin = File(extAlbum, "corrupt_old.bin").apply { writeBytes(ByteArray(256) { 7 }) }
        assertTrue(corruptArchiveBin.exists())

        val newBin = encryptBytesForTest(extAlbum, "new_pending.jpg", "fresh-cli-photo".toByteArray())

        // 1. Without pending_sync, strict sync returns 0/0/0/0 and refuses to scan archive
        val withoutManifest = CryptoManager.syncVaultFromExternalDirectory(testContext, extSecrets)!!
        assertEquals(0, withoutManifest.copiedCount)
        assertEquals(0, withoutManifest.failedCount)

        // 2. Write pending_sync listing ONLY the newly encrypted .bin
        val pendingFile = File(extSecrets, "pending_sync")
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
    fun syncVaultFromExternalDirectory_acceptsVisibleManifestWhenMtpDropsDotFiles() {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        setMasterKey(masterKey)

        val phoneVault = File(tempRoot, "mtp_phone/Calypso Gallery/$SECRETS_DIR_NAME").apply { mkdirs() }
        File(phoneVault, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        CryptoManager.setVaultRootLocation(testContext, phoneVault)

        val cliFolder = File(tempRoot, "mtp_stage/$CLI_STAGING_DIR_NAME").apply { mkdirs() }
        val album = File(cliFolder, "MtpAlbum").apply { mkdirs() }
        writeMetaV2(album, masterKey, "MtpAlbum")
        val newBin = encryptBytesForTest(album, "mtp.jpg", "mtp-photo".toByteArray())

        // MTP commonly skips dot-files: only the visible manifest survives the copy.
        val visibleManifest = File(cliFolder, "pending_sync")
        visibleManifest.writeText("MtpAlbum/${newBin.name}\n", Charsets.UTF_8)

        val result = CryptoManager.syncVaultFromExternalDirectory(testContext, cliFolder)!!
        assertEquals(1, result.copiedCount)
        assertEquals(0, result.failedCount)
        assertFalse(visibleManifest.exists())
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
    fun syncVaultFromExternalDirectory_skipsDifferentTimestampIdenticalContent_andKeepsTargetLastModified() {
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

        val targetBin = encryptBytesForTest(
            targetDir = vaultAlbum,
            originalName = "photo.jpg",
            payload = payload,
            lastModifiedOverride = ts2021,
            dateTakenOverride = ts2021
        )
        targetBin.setLastModified(ts2021)

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
        assertEquals(1, firstSync!!.skippedDuplicateCount)
        assertEquals(0, firstSync.copiedCount + firstSync.renamedCount)
        assertEquals(1, vaultAlbum.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })
        assertEquals(ts2021, targetBin.lastModified())

        val secondSync = CryptoManager.syncVaultFromExternalDirectory(
            context = testContext,
            sourceDir = extRoot,
            requirePendingManifest = false
        )
        assertNotNull(secondSync)
        assertEquals(1, secondSync!!.skippedDuplicateCount)
        assertEquals(0, secondSync.copiedCount + secondSync.renamedCount)
        assertEquals(1, vaultAlbum.listFiles()?.count { it.name.endsWith(ENCRYPTED_BIN_EXT) })
        assertEquals(ts2021, targetBin.lastModified())
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

}
