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
import org.fossify.gallery.helpers.withEphemeralVaultKey
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


class VaultLockPolicyTest : VaultLockPolicyTestBase() {

    @Test
    fun armsReauthOnlyWhenVaultIsUnlockedExistsAndInVaultSession() {
        assertTrue(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = true, hasExistingVault = true, isInVaultSession = true))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = true, hasExistingVault = true, isInVaultSession = false))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = false, hasExistingVault = true, isInVaultSession = true))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = true, hasExistingVault = false, isInVaultSession = true))
        assertFalse(VaultLockPolicy.shouldArmReauth(isVaultUnlocked = false, hasExistingVault = false, isInVaultSession = false))
    }

    @Test
    fun showsLockScreenOnlyForNonLockNonSplashWhenPendingAndNotAlreadyOpen() {
        assertTrue(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = false,
                isSplashActivity = false,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = false,
                isLockScreenActivity = false,
                isSplashActivity = false,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = true,
                isSplashActivity = false,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = false,
                isSplashActivity = true,
                isLockScreenAlreadyActive = false
            )
        )
        assertFalse(
            VaultLockPolicy.shouldShowLockScreen(
                requiresVaultReauth = true,
                isLockScreenActivity = false,
                isSplashActivity = false,
                isLockScreenAlreadyActive = true
            )
        )
    }

    @Test
    fun clearKeysZeroizesMasterKeyKekAndCachedFileKeys() {
        val vmk = ByteArray(32) { (it + 1).toByte() }
        val kek = ByteArray(32) { (it + 40).toByte() }
        val fileKey = ByteArray(32) { (it + 90).toByte() }
        CryptoManager.unlockedMasterKey = vmk
        CryptoManager.unlockedKek = kek
        CryptoManager.metadataCache["/vault/sample.bin"] = EncryptedFileMetadata(
            originalName = "sample.jpg",
            lastModified = 100L,
            dateTaken = 100L,
            originalSize = 10L,
            mediaType = 1,
            videoDuration = 0,
            payloadOffset = 100L,
            contentIv = ByteArray(0),
            fileKey = fileKey
        )

        CryptoManager.clearKeys(testContext)

        assertFalse(CryptoManager.isVaultUnlocked)
        assertNull(CryptoManager.unlockedMasterKey)
        assertNull(CryptoManager.unlockedKek)
        assertTrue(CryptoManager.metadataCache.isEmpty())
        assertTrue(vmk.all { it == 0.toByte() })
        assertTrue(kek.all { it == 0.toByte() })
        assertTrue(fileKey.all { it == 0.toByte() })
    }

    @Test
    fun withEphemeralVaultKeyZeroizesKeysWithoutPromotingSession() {
        val vaultDir = File(tempRoot, "Secrets").apply { mkdirs() }
        val expectedVmk = ByteArray(32) { (it + 11).toByte() }
        val expectedKek = ByteArray(32) { (it + 77).toByte() }
        val secretChars = "p@ssw0rd!".toCharArray()
        writeSyntheticVaultHeader(vaultDir, secretChars, expectedVmk, expectedKek)
        testContext.config.vaultRootPath = vaultDir.absolutePath
        CryptoManager.cachedVaultRoot = vaultDir

        var observedVmk: ByteArray? = null
        var observedKek: ByteArray? = null
        val result = CryptoManager.withEphemeralVaultKey(testContext, secretChars) { vmk, kek ->
            assertFalse("Ephemeral unwrap must never populate unlockedMasterKey", CryptoManager.isVaultUnlocked)
            assertArrayEquals(expectedVmk, vmk)
            assertArrayEquals(expectedKek, kek)
            observedVmk = vmk
            observedKek = kek
            "ok"
        }

        assertEquals("ok", result)
        assertFalse(CryptoManager.isVaultUnlocked)
        assertNotNull(observedVmk)
        assertNotNull(observedKek)
        assertTrue(observedVmk!!.all { it == 0.toByte() })
        assertTrue(observedKek!!.all { it == 0.toByte() })
    }

    @Test
    fun backgroundAndScreenOffWipeRamKeysImmediatelyWhileKeepingReauthSession() {
        val vaultDir = File(tempRoot, "Secrets").apply { mkdirs() }
        File(vaultDir, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        testContext.config.vaultRootPath = vaultDir.absolutePath
        testContext.config.temporarilyShowHidden = true
        CryptoManager.cachedVaultRoot = vaultDir

        val vmk = ByteArray(32) { 5 }
        CryptoManager.unlockedMasterKey = vmk

        VaultSessionManager.onAppBackgrounded(testContext)

        // Keys are wiped from RAM immediately, while re-auth UI state remains armed for 30 minutes.
        assertFalse(CryptoManager.isVaultUnlocked)
        assertTrue(vmk.all { it == 0.toByte() })
        assertTrue(VaultSessionManager.isReauthPending())
        assertTrue(testContext.config.temporarilyShowHidden)

        // Foregrounding before 30 minutes keeps re-auth pending so VaultLockActivity resumes in place.
        VaultSessionManager.onAppForegrounded(testContext)
        assertTrue(VaultSessionManager.isReauthPending())
        VaultSessionManager.onVaultReauthenticated(testContext)
        assertFalse(VaultSessionManager.isReauthPending())
        assertTrue(testContext.config.temporarilyShowHidden)

        // Screen off also wipes RAM keys immediately.
        val vmk2 = ByteArray(32) { 7 }
        CryptoManager.unlockedMasterKey = vmk2
        VaultSessionManager.onScreenOff(testContext)
        assertFalse(CryptoManager.isVaultUnlocked)
        assertTrue(vmk2.all { it == 0.toByte() })
        assertTrue(VaultSessionManager.isReauthPending())
    }

    @Test
    fun registeredForegroundOperationDefersRamWipeUntilOperationEnds() {
        val vaultDir = File(tempRoot, "Secrets").apply { mkdirs() }
        File(vaultDir, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        testContext.config.vaultRootPath = vaultDir.absolutePath
        testContext.config.temporarilyShowHidden = true
        CryptoManager.cachedVaultRoot = vaultDir

        val vmk = ByteArray(32) { 9 }
        CryptoManager.unlockedMasterKey = vmk

        ForegroundOperationRegistry.withOperation(testContext, "album-encrypt") {
            VaultSessionManager.onAppBackgrounded(testContext)
            assertTrue("RAM key must stay alive during active foreground operation", CryptoManager.isVaultUnlocked)
            assertFalse(vmk.all { it == 0.toByte() })
        }

        // Once the operation finishes while app is still in background, RAM keys are wiped right away.
        assertFalse(CryptoManager.isVaultUnlocked)
        assertTrue(vmk.all { it == 0.toByte() })
        assertTrue(VaultSessionManager.isReauthPending())
    }

    @Test
    fun encryptedGlideCachePolicyDisablesDiskAndMemoryCache() {
        val encOptions = RequestOptions().applyEncryptedCachePolicy(true)
        assertEquals(DiskCacheStrategy.NONE, encOptions.diskCacheStrategy)
        assertFalse(encOptions.isMemoryCacheable)

        val plainOptions = RequestOptions().applyEncryptedCachePolicy(false)
        assertTrue(plainOptions.isMemoryCacheable)
    }

    @Test
    fun manifestBackupRulesAndPasswordImeHygieneMatchAdr0002() {
        val manifestText = locateAppFile("src/main/AndroidManifest.xml").readText()
        assertTrue(manifestText.contains("android:allowBackup=\"false\""))
        assertTrue(manifestText.contains("android:fullBackupContent=\"false\""))
        assertTrue(manifestText.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))

        val rulesText = locateAppFile("src/main/res/xml/data_extraction_rules.xml").readText()
        listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
            assertTrue("Missing backup exclusion for $domain", rulesText.contains("<exclude domain=\"$domain\""))
        }

        val securityLayoutText = locateAppFile("src/main/res/layout/dialog_vault_security.xml").readText()
        assertTrue(securityLayoutText.contains("android:importantForAutofill=\"no\""))
        assertTrue(securityLayoutText.contains("textNoSuggestions"))
        assertTrue(securityLayoutText.contains("flagNoPersonalizedLearning"))

        val decoderText = locateAppFile("src/main/kotlin/org/fossify/gallery/helpers/MyGlideImageDecoder.kt").readText()
        assertFalse(decoderText.contains("skipMemoryCache(false)"))
    }

    @Test
    fun productionCryptoUsesUnseededSecureRandomOnly() {
        val mainKotlinDir = locateAppFile("src/main/kotlin")
        val allSources = mainKotlinDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Expected production Kotlin sources", allSources.isNotEmpty())

        for (src in allSources) {
            val content = src.readText()
            assertFalse("${src.name} must never call SecureRandom.setSeed()", content.contains("setSeed("))
            assertFalse("${src.name} must never call Math.random()", content.contains("Math.random("))

            val isCryptoModule = src.name.startsWith("CryptoManager") ||
                src.absolutePath.contains("${File.separator}vault${File.separator}")
            if (isCryptoModule) {
                assertFalse("${src.name} must not use kotlin.random.Random", content.contains("kotlin.random"))
                assertFalse("${src.name} must not use java.util.Random", content.contains("java.util.Random"))
            }
        }
    }

    @Test
    fun foregroundOperationRegistryNotifiesStateChangesAndSerializesConcurrentOwners() {
        val states = ArrayList<Boolean>()
        ForegroundOperationRegistry.onStateChanged = { states.add(it) }

        assertEquals("ok", ForegroundOperationRegistry.withOperation(testContext, "vault-sync") {
            assertTrue(ForegroundOperationRegistry.isActive)
            // Same-thread re-entrant nesting must not emit duplicate state transitions.
            val nested = ForegroundOperationRegistry.withOperation(testContext, "secure-wipe") {
                assertTrue(ForegroundOperationRegistry.isActive)
                "nested"
            }
            assertEquals("nested", nested)

            // Different-thread acquisition must be rejected while outer owner holds token.
            var otherThreadAcquired = true
            val t = Thread {
                otherThreadAcquired = ForegroundOperationRegistry.acquire(testContext, "vault-export")
            }
            t.start()
            t.join()
            assertFalse(otherThreadAcquired)
            "ok"
        })

        assertFalse(ForegroundOperationRegistry.isActive)
        assertEquals(listOf(true, false), states)
    }

    @Test
    fun foregroundOperationRegistryCoversExportSyncStreamWipeAndVaultRecycleBinOps() {
        val vaultDir = File(tempRoot, "Secrets").apply { mkdirs() }
        val albumDir = File(vaultDir, "album01").apply { mkdirs() }
        File(vaultDir, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
        testContext.config.vaultRootPath = vaultDir.absolutePath
        CryptoManager.cachedVaultRoot = vaultDir
        CryptoManager.unlockedMasterKey = ByteArray(32) { 3 }
        CryptoManager.unlockedKek = ByteArray(32) { 4 }

        val stateTransitions = ArrayList<Boolean>()
        ForegroundOperationRegistry.onStateChanged = { stateTransitions.add(it) }

        // 1. encryptStreamToDirectory registers foreground operation during streaming write.
        var activeDuringStream = false
        val encFile = CryptoManager.encryptStreamToDirectory(
            context = testContext,
            inputStream = "<svg/>".byteInputStream(Charsets.UTF_8),
            targetDir = albumDir,
            originalName = "sample.svg",
            contentLength = 6L,
            onProgress = { _, _ ->
                activeDuringStream = activeDuringStream || ForegroundOperationRegistry.isActive
            }
        )
        assertNotNull(encFile)
        assertTrue(activeDuringStream)
        assertFalse(ForegroundOperationRegistry.isActive)

        // 2. exportEncryptedVault registers foreground operation across file copy.
        val exportTarget = File(tempRoot, "export_out").apply { mkdirs() }
        var activeDuringExport = false
        val exportResult = CryptoManager.exportEncryptedVault(
            context = testContext,
            targetDir = exportTarget,
            onlyVaultHeader = false
        ) { _, _ ->
            activeDuringExport = activeDuringExport || ForegroundOperationRegistry.isActive
        }
        assertNotNull(exportResult)
        assertTrue(activeDuringExport)
        assertFalse(ForegroundOperationRegistry.isActive)

        // 3. syncVaultFromExternalDirectory registers foreground operation.
        val syncSource = File(tempRoot, "sync_src").apply { mkdirs() }
        File(syncSource, "dummy.bin").writeBytes(ByteArray(4))
        File(syncSource, CryptoManager.PENDING_SYNC_FILE_NAME).writeText("dummy.bin\n")
        val syncResult = CryptoManager.syncVaultFromExternalDirectory(
            context = testContext,
            sourceDir = syncSource,
            requirePendingManifest = true
        )
        assertNotNull(syncResult)
        assertFalse(ForegroundOperationRegistry.isActive)

        // 4. Vault recycle bin move / restore / permanent delete / empty + standalone secure wipe.
        val moved = CryptoManager.moveEncryptedFilesToVaultRecycleBin(testContext, listOf(encFile!!.absolutePath))
        assertTrue(moved)
        val binDir = CryptoManager.getVaultRecycleBin(testContext)!!
        val binnedFile = binDir.listFiles()?.firstOrNull { it.name.endsWith(".bin") }
        assertNotNull(binnedFile)
        assertEquals(1, CryptoManager.restoreVaultRecycleBinFiles(testContext, listOf(binnedFile!!.absolutePath)))

        val restoredFile = albumDir.listFiles()?.firstOrNull { it.name.endsWith(".bin") }
        assertNotNull(restoredFile)
        assertTrue(CryptoManager.moveEncryptedFilesToVaultRecycleBin(testContext, listOf(restoredFile!!.absolutePath)))
        val reBinned = binDir.listFiles()?.firstOrNull { it.name.endsWith(".bin") }!!
        assertEquals(1, CryptoManager.permanentlyDeleteVaultRecycleBinFiles(testContext, listOf(reBinned.absolutePath)))
        assertFalse(reBinned.exists())
        assertTrue(CryptoManager.emptyVaultRecycleBin(testContext))

        val wipeTarget = File(tempRoot, "wipe_me.tmp").apply { writeBytes(ByteArray(128) { 9 }) }
        assertTrue(CryptoManager.secureWipeAndDeleteFile(wipeTarget, testContext))
        assertFalse(wipeTarget.exists())
        assertFalse(ForegroundOperationRegistry.isActive)

        // Every registered operation emitted a balanced true -> false transition pair.
        assertTrue("Expected at least 7 operation cycles", stateTransitions.size >= 14)
        for (i in stateTransitions.indices step 2) {
            assertTrue(stateTransitions[i])
            assertFalse(stateTransitions[i + 1])
        }
    }

    @Test
    fun durableWipeJournalPersistsEntriesOnFailureAndClearsOnlyAfterDeletion() {
        val journalFile = CryptoManager.getPendingWipeJournalFile(testContext)
        assertFalse(journalFile.exists())

        // Idempotent append: repeated path does not duplicate lines.
        val targetA = File(tempRoot, "secret_a.tmp").apply { writeBytes(ByteArray(4096) { 0x5A }) }
        val targetB = File(tempRoot, "secret_b.tmp").apply { writeBytes(ByteArray(2048) { 0x3C }) }
        CryptoManager.appendPendingWipe(testContext, targetA.absolutePath)
        CryptoManager.appendPendingWipe(testContext, targetA.absolutePath)
        CryptoManager.appendPendingWipe(testContext, targetB.absolutePath)
        assertEquals(listOf(targetA.absolutePath, targetB.absolutePath), journalFile.readLines())

        // Wiping a read-only file fails and must leave both targets still flagged in the journal.
        assertTrue(targetA.setWritable(false))
        try {
            assertFalse(CryptoManager.secureWipeAndDeleteFile(targetA, testContext))
            assertTrue(targetA.exists())
            assertTrue(journalFile.exists())
            assertTrue(targetA.absolutePath in journalFile.readLines())

            // Startup retry wipes targetB cleanly while preserving un-wiped targetA.
            assertEquals(1, CryptoManager.processPendingWipeJournalSync(testContext))
            assertFalse(targetB.exists())
            assertTrue(targetA.exists())
            assertEquals(listOf(targetA.absolutePath), journalFile.readLines())
        } finally {
            targetA.setWritable(true)
        }

        // Making targetA writable again lets retry wipe & delete it and wipe the empty journal.
        assertEquals(1, CryptoManager.processPendingWipeJournalSync(testContext))
        assertFalse(targetA.exists())
        assertFalse(journalFile.exists())

        // Direct standalone secureWipeAndDeleteFile also removes any existing journal traces cleanly.
        val targetC = File(tempRoot, "secret_c.tmp").apply { writeBytes(ByteArray(512) { 0x7E }) }
        assertTrue(CryptoManager.secureWipeAndDeleteFile(targetC, testContext))
        assertFalse(targetC.exists())
        assertFalse(journalFile.exists())
    }

    @Test
    fun classifyBiometricCipherFailureDistinguishesInvalidatedFromLockedAndTransient() {
        class FakeKeyInvalidated : Exception("invalidated")
        class FakeUserNotAuth : Exception("not auth")

        val directInvalidated = classifyBiometricCipherFailure(
            error = FakeKeyInvalidated(),
            deviceLocked = false,
            isKeyInvalidatedError = { it is FakeKeyInvalidated },
            isUserNotAuthenticatedError = { it is FakeUserNotAuth }
        )
        assertEquals(BiometricCipherResult.KeyInvalidated, directInvalidated)

        val wrappedInvalidated = classifyBiometricCipherFailure(
            error = RuntimeException("wrapper", FakeKeyInvalidated()),
            deviceLocked = true,
            isKeyInvalidatedError = { it is FakeKeyInvalidated },
            isUserNotAuthenticatedError = { it is FakeUserNotAuth }
        )
        assertEquals(BiometricCipherResult.KeyInvalidated, wrappedInvalidated)

        val userNotAuthAfterUnlock = classifyBiometricCipherFailure(
            error = FakeUserNotAuth(),
            deviceLocked = false,
            isKeyInvalidatedError = { it is FakeKeyInvalidated },
            isUserNotAuthenticatedError = { it is FakeUserNotAuth }
        )
        assertEquals(BiometricCipherResult.DeviceLocked, userNotAuthAfterUnlock)

        val lockedTransient = classifyBiometricCipherFailure(
            error = IllegalStateException("Keystore operation failed"),
            deviceLocked = true,
            isKeyInvalidatedError = { it is FakeKeyInvalidated },
            isUserNotAuthenticatedError = { it is FakeUserNotAuth }
        )
        assertEquals(BiometricCipherResult.DeviceLocked, lockedTransient)

        val strongBoxBusy = classifyBiometricCipherFailure(
            error = IllegalStateException("StrongBox backend busy"),
            deviceLocked = false,
            isKeyInvalidatedError = { it is FakeKeyInvalidated },
            isUserNotAuthenticatedError = { it is FakeUserNotAuth }
        )
        assertEquals(BiometricCipherResult.TransientError, strongBoxBusy)
    }

}
