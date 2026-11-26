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


class VaultTransferCryptoTest : VaultTestBase() {

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

}
