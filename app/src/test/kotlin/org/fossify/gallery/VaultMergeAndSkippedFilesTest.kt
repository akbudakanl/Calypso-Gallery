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


class VaultMergeAndSkippedFilesTest : VaultTestBase() {

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
    fun automaticMode_neverAllowsAlwaysAskDateTieBreak_andFallsBackToKeepTarget() {
        testContext.config.conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG
        testContext.config.dateTieBreakMode = DATE_TIE_BREAK_ASK
        assertEquals(DATE_TIE_BREAK_ASK, testContext.config.dateTieBreakMode)

        // Switching to Automatic falls back to the default date behaviour.
        testContext.config.conflictHandlingMode = CONFLICT_HANDLING_AUTO
        assertEquals(DATE_TIE_BREAK_KEEP_TARGET, testContext.config.dateTieBreakMode)

        // "Always ask" cannot be set while Automatic is active, but the other two options can.
        testContext.config.dateTieBreakMode = DATE_TIE_BREAK_ASK
        assertEquals(DATE_TIE_BREAK_KEEP_TARGET, testContext.config.dateTieBreakMode)
        testContext.config.dateTieBreakMode = DATE_TIE_BREAK_USE_SOURCE
        assertEquals(DATE_TIE_BREAK_USE_SOURCE, testContext.config.dateTieBreakMode)

        // It becomes available again after returning to the Smart dialog.
        testContext.config.conflictHandlingMode = CONFLICT_HANDLING_SMART_DIALOG
        assertEquals(DATE_TIE_BREAK_USE_SOURCE, testContext.config.dateTieBreakMode)
        testContext.config.dateTieBreakMode = DATE_TIE_BREAK_ASK
        assertEquals(DATE_TIE_BREAK_ASK, testContext.config.dateTieBreakMode)
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
}
