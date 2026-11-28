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


class VaultBiometricAndHardeningTest : VaultLockPolicyTestBase() {

    @Test
    fun biometricUnavailableReasonDistinguishesWeakFaceHardwareAndKeyFailures() {
        assertEquals(
            R.string.vault_biometric_unavailable_no_hardware,
            biometricUnavailableReason(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, false)
        )
        assertEquals(
            R.string.vault_biometric_unavailable_none_enrolled,
            biometricUnavailableReason(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, true)
        )
        assertEquals(
            R.string.vault_biometric_unavailable_hw_unavailable,
            biometricUnavailableReason(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE, false)
        )
        assertEquals(
            R.string.vault_biometric_unavailable_no_secure_key,
            biometricUnavailableReason(BiometricManager.BIOMETRIC_SUCCESS, false)
        )
        assertEquals(
            R.string.vault_biometric_unavailable_generic,
            biometricUnavailableReason(BiometricManager.BIOMETRIC_STATUS_UNKNOWN, false)
        )
    }

    @Test
    fun vaultBiometricSettingStaysVisibleAndExplainsUnavailability() {
        val settingsText = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/activities/SettingsActivityVault.kt"
        ).readText() + "\n" + locateAppFile(
            "src/main/kotlin/org/fossify/gallery/activities/SettingsActivityVaultSecurity.kt"
        ).readText()
        val rowGate = "binding.settingsVaultBiometricHolder.beVisibleIf(hasStoragePermission && vaultExists)"
        assertTrue(
            "The biometric row must not be hidden when strong hardware is missing",
            settingsText.contains(rowGate) &&
                settingsText.contains("biometricUnavailableReason(biometricStatus, biometricKeyAvailable)")
        )

        assertTrue(
            "The long explanation must be a dialog, never a truncated toast",
            !settingsText.contains("toast(biometricUnavailableReason")
        )

        val setupBody = settingsText.substringAfter("internal fun SettingsActivity.setupVaultBiometric()")
            .substringBefore("internal fun SettingsActivity.setupVaultBiometricAutoReturn()")
        assertTrue(
            "setupVaultBiometric() must not re-hide the row with the old hardware/key gate",
            !setupBody.contains("settingsVaultBiometricHolder.beVisibleIf") &&
                !setupBody.contains("isBiometricHardwareAvailable")
        )

        assertTrue(
            "The switch must be passive while no strong biometric is usable",
            settingsText.contains("binding.settingsVaultBiometric.isEnabled = vaultExists && biometricUsable")
        )

        val layoutText = locateAppFile("src/main/res/layout/activity_settings.xml").readText()
        assertTrue(
            "The row needs an info icon that opens the reason dialog on demand",
            layoutText.contains("settings_vault_biometric_info") &&
                layoutText.contains("@drawable/ic_info_vector")
        )
    }

    @Test
    fun biometricOfferStaysSilentUntilAStrongBiometricIsEnrolled() {
        val offerText = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/dialogs/VaultBiometricOfferDialog.kt"
        ).readText()
        assertTrue(
            "The offer must be gated on an actually enrolled strong biometric",
            offerText.contains("CryptoManager.ensureBiometricKeyAvailable(activity)")
        )
        val earlyReturn = offerText.substringAfter("if (!CryptoManager.ensureBiometricKeyAvailable(activity)) return")
            .substringBefore("// Mark and default to off before showing")
        assertFalse(
            "No config may be rewritten when no strong biometric is enrolled",
            earlyReturn.contains("isVaultBiometricEnabled = false") ||
                earlyReturn.contains("vaultBiometricWrappedBundle = \"\"")
        )
    }

    @Test
    fun archiveChecksDeviceOwnerButResetStaysPasswordOnly() {
        val vaultSettings = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/SettingsActivityVault.kt").readText()
        val securitySettings = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/SettingsActivityVaultSecurity.kt").readText()
        assertTrue(
            "Archiving must verify the device owner with Class 3 biometric or device credential",
            securitySettings.contains("internal fun SettingsActivity.requireDeviceOwner") &&
                securitySettings.contains("keyguard?.isDeviceSecure != true") &&
                securitySettings.contains("BIOMETRIC_STRONG or")
        )
        val archiveBody = securitySettings.substringAfter("internal fun SettingsActivity.setupArchiveVault()")
            .substringBefore("internal fun deviceOwnerAuthenticators")
        assertTrue(
            "The archive action must run only after device-owner verification",
            archiveBody.contains("requireDeviceOwner {")
        )
        val exportBody = vaultSettings.substringAfter("private fun SettingsActivity.setupExportVault()")
            .substringBefore("private fun SettingsActivity.setupChangeVaultPassword()")
        assertTrue(
            "Exporting the encrypted vault must also verify the device owner",
            exportBody.contains("requireDeviceOwner {")
        )
        val resetBody = vaultSettings.substringAfter("private fun SettingsActivity.setupEmptyVault()")
        assertTrue(
            "Destructive reset must stay behind the current password, never biometrics",
            resetBody.contains("requireVaultPassword(allowBiometrics = false)")
        )

        val authenticatorsBody = securitySettings.substringAfter("internal fun deviceOwnerAuthenticators()")
            .substringBefore("internal fun SettingsActivity.requireDeviceOwner")
        val requireOwnerBody = securitySettings.substringAfter("internal fun SettingsActivity.requireDeviceOwner")
            .substringBefore("internal fun SettingsActivity.setupUnarchiveVault")
        val disableBiometricBody = securitySettings.substringAfter("private fun SettingsActivity.confirmDisableBiometric")
            .substringBefore("private fun SettingsActivity.showBiometricEnrollmentPrompt")
        assertTrue(
            "Device-owner prompt must fall back to BIOMETRIC_WEAK | DEVICE_CREDENTIAL on API 28-29 to avoid AndroidX IllegalArgumentException",
            authenticatorsBody.contains("Build.VERSION_CODES.P..Build.VERSION_CODES.Q") &&
                authenticatorsBody.contains("BIOMETRIC_WEAK or") &&
                requireOwnerBody.contains("setAllowedAuthenticators(deviceOwnerAuthenticators())") &&
                disableBiometricBody.contains("setAllowedAuthenticators(deviceOwnerAuthenticators())")
        )
    }

    @Test
    fun vaultLockReturnDefaultsToManualBiometricAndExposesBackExitButton() {
        assertFalse(
            "autoBiometricPromptOnVaultReturn must default to false for security",
            testContext.config.autoBiometricPromptOnVaultReturn
        )
        testContext.config.autoBiometricPromptOnVaultReturn = true
        assertTrue(testContext.config.autoBiometricPromptOnVaultReturn)

        val lockActivityText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/VaultLockActivity.kt").readText()
        assertTrue(
            "VaultLockActivity must gate auto biometric prompt behind config, window focus, and unlocked device",
            lockActivityText.contains("config.autoBiometricPromptOnVaultReturn") &&
                lockActivityText.contains("hasWindowFocus()") &&
                lockActivityText.contains("CryptoManager.isDeviceLocked(this)")
        )
        assertTrue(
            "VaultLockActivity must expose a top-left back button to exit back to the normal gallery",
            lockActivityText.contains("vaultLockBackBtn") && lockActivityText.contains("exitVaultToNormalGallery")
        )

        val lockLayoutText = locateAppFile("src/main/res/layout/activity_vault_lock.xml").readText()
        assertTrue(lockLayoutText.contains("vault_lock_back_btn"))

        val resumeText = locateAppFile("src/main/kotlin/org/fossify/gallery/extensions/VaultResume.kt").readText()
        assertTrue(
            "VaultResume must respect config.autoBiometricPromptOnVaultReturn",
            resumeText.contains("autoPromptBiometrics = config.autoBiometricPromptOnVaultReturn")
        )

        val settingsText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/SettingsActivityVaultSecurity.kt").readText()
        val autoReturnToggleBody = settingsText.substringAfter("internal fun SettingsActivity.setupVaultBiometricAutoReturn()")
            .substringBefore("internal fun SettingsActivity.setupArchiveVault")
        assertTrue(
            "Toggling auto biometric return must require verification with password/PIN or biometric, in both directions",
            autoReturnToggleBody.contains("requireVaultPassword(verifyOnly = true)")
        )
    }

    @Test
    fun reprintIsCompletelyRemovedAndAppSecurityUsesAndroidXBiometric() {
        val initializerFile = File("src/main/kotlin/org/fossify/gallery/helpers/LegacyFingerprintInitializer.kt")
            .takeIf { File("src/main").exists() }
            ?: File("app/src/main/kotlin/org/fossify/gallery/helpers/LegacyFingerprintInitializer.kt")
        assertFalse("LegacyFingerprintInitializer.kt must be deleted", initializerFile.exists())

        val mainKotlinDir = locateAppFile("src/main/kotlin")
        val forbiddenRefs = listOf(
            "com.github.ajalt.reprint",
            "Reprint.",
            "org.fossify.commons.dialogs.SecurityDialog",
            "org.fossify.commons.adapters.PasswordTypesAdapter",
            "org.fossify.commons.adapters.AppLockAdapter",
            "org.fossify.commons.extensions.handleLockedFolderOpening",
            "org.fossify.commons.extensions.handleDeletePasswordProtection",
            "org.fossify.commons.extensions.handleHiddenFolderPasswordProtection",
            "org.fossify.commons.extensions.handleAppPasswordProtection"
        )
        for (src in mainKotlinDir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val content = src.readText()
            for (forbidden in forbiddenRefs) {
                assertFalse("${src.name} must not reference legacy $forbidden", content.contains(forbidden))
            }
        }

        val adapterText = locateAppFile("src/main/kotlin/org/fossify/gallery/adapters/AppPasswordTypesAdapter.kt").readText()
        assertTrue(
            "AppPasswordTypesAdapter must route PROTECTION_FINGERPRINT to AndroidX tab_biometric_id",
            adapterText.contains("tab_biometric_id") && !adapterText.contains("tab_fingerprint")
        )

        val unlockActivityText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/AppUnlockActivity.kt").readText()
        assertTrue(
            "AppUnlockActivity must route PROTECTION_FINGERPRINT to AndroidX tab_biometric_id",
            unlockActivityText.contains("tab_biometric_id") && !unlockActivityText.contains("tab_fingerprint")
        )

        val gradleText = locateAppFile("build.gradle.kts").readText()
        assertTrue(
            "app/build.gradle.kts must exclude Reprint transitive artifacts",
            gradleText.contains("exclude(group = \"com.github.tibbi\", module = \"reprint\")") &&
                gradleText.contains("exclude(group = \"com.github.tibbi.reprint\")")
        )

        val manifestText = locateAppFile("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml must declare .activities.AppUnlockActivity and remove Commons AppLockActivity",
            manifestText.contains("android:name=\".activities.AppUnlockActivity\"") &&
                manifestText.contains("android:name=\"org.fossify.commons.activities.AppLockActivity\"") &&
                manifestText.contains("tools:node=\"remove\"")
        )
        val fingerprintPermissionMatch = Regex(
            """<uses-permission[^>]*android\.permission\.USE_FINGERPRINT[^>]*/>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(manifestText)
        assertTrue(
            "AndroidManifest.xml must cap legacy USE_FINGERPRINT permission at maxSdkVersion=29",
            fingerprintPermissionMatch?.value?.contains("android:maxSdkVersion=\"29\"") == true
        )

        val licensesText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/ThirdPartyLicensesActivity.kt").readText()
        assertFalse(
            "ThirdPartyLicensesActivity must not list removed Reprint library",
            licensesText.contains("reprint_title")
        )
    }

    @Test
    fun emptyPendingWipeJournalDoesNotActivateForegroundOperationRegistry() {
        val states = ArrayList<Boolean>()
        ForegroundOperationRegistry.onStateChanged = { states.add(it) }

        assertFalse(CryptoManager.getPendingWipeJournalFile(testContext).exists())
        assertEquals(0, CryptoManager.processPendingWipeJournalSync(testContext))
        assertTrue(
            "Empty pending wipe journal must not trigger ForegroundOperationRegistry or FileOperationService",
            states.isEmpty()
        )
    }

    @Test
    fun android16RuntimeHardeningContractsHold() {
        val appText = locateAppFile("src/main/kotlin/org/fossify/gallery/App.kt").readText()
        assertTrue(
            "App.kt must register screen-off receiver with ContextCompat.RECEIVER_NOT_EXPORTED",
            appText.contains("ContextCompat.registerReceiver") &&
                appText.contains("ContextCompat.RECEIVER_NOT_EXPORTED")
        )
        val onCreateToNextMethod = appText.substringAfter("override fun onCreate()")
            .substringBefore("private fun syncKeepScreenOnForTrackedActivities")
        assertTrue(
            "App.kt must schedule pending wipe journal once on foreground start via pendingWipeJournalScheduled",
            appText.contains("pendingWipeJournalScheduled.compareAndSet(false, true)") &&
                !onCreateToNextMethod.substringBefore("registerActivityLifecycleCallbacks").contains("processPendingWipeJournal") &&
                !onCreateToNextMethod.substringAfterLast("})").contains("processPendingWipeJournal")
        )

        val bootText = locateAppFile("src/main/kotlin/org/fossify/gallery/receivers/BootCompletedReceiver.kt").readText()
        assertFalse(
            "BootCompletedReceiver must not trigger processPendingWipeJournal (avoids FGS start from boot)",
            bootText.contains("processPendingWipeJournal")
        )
        assertTrue(
            "BootCompletedReceiver must guard background scan with goAsync() and finish()",
            bootText.contains("goAsync()") && bootText.contains("pendingResult.finish()")
        )

        val serviceText = locateAppFile("src/main/kotlin/org/fossify/gallery/helpers/operations/FileOperationService.kt").readText()
        assertTrue(
            "FileOperationService must guard startForeground against ForegroundServiceStartNotAllowedException",
            serviceText.contains("startForeground(NOTIFICATION_ID") && serviceText.contains("catch (_: Throwable)")
        )
        assertTrue(
            "FileOperationService must override Android 15+ onTimeout(startId, fgsType)",
            serviceText.contains("override fun onTimeout(startId: Int, fgsType: Int)")
        )
        val stopSelfBody = serviceText.substringAfter("private fun stopSelfSafe()")
            .substringBefore("override fun onDestroy()")
        val onDestroyBody = serviceText.substringAfter("override fun onDestroy()")
        assertTrue(
            "FileOperationService must release wake lock in both stopSelfSafe() and onDestroy()",
            stopSelfBody.contains("releaseWakeLock()") && onDestroyBody.contains("releaseWakeLock()")
        )

        val lockActivityText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/VaultLockActivity.kt").readText()
        assertTrue(
            "VaultLockActivity must apply displayCutout and ime insets",
            lockActivityText.contains("WindowInsetsCompat.Type.displayCutout()") &&
                lockActivityText.contains("WindowInsetsCompat.Type.ime()")
        )

        val onboardingHelperText = locateAppFile("src/main/kotlin/org/fossify/gallery/helpers/OnboardingUiHelper.kt").readText()
        assertTrue(
            "OnboardingUiHelper must apply displayCutout insets and guard deprecated bar color setters on API 35+",
            onboardingHelperText.contains("WindowInsetsCompat.Type.displayCutout()") &&
                onboardingHelperText.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM")
        )
    }

    @Test
    fun samePasswordCheckUsesFullKdfAndAlwaysZeroizesTemporaryBuffers() {
        val dialogText = locateAppFile("src/main/kotlin/org/fossify/gallery/dialogs/VaultSecurityDialog.kt").readText()
        val changeBranch = dialogText.substringAfter("private fun handleSetupSubmission(secret: CharArray)")
            .substringAfter("if (isChangePasswordMode) {")
            .substringBefore("} else {\r\n                handleWeakFirstEntry(secret)")
            .substringBefore("} else {\n                handleWeakFirstEntry(secret)")

        assertTrue(
            "Password change must keep running full Argon2 verification via CryptoManager.isCurrentVaultSecret",
            changeBranch.contains("CryptoManager.isCurrentVaultSecret(activity, candidate)")
        )
        val uiThreadBlock = changeBranch.substringAfter("activity.runOnUiThread {")
        assertTrue(
            "newSecret must be zeroized in a finally block inside runOnUiThread so same==true / early returns cannot leak it",
            uiThreadBlock.contains("finally {\n                                    newSecret.fill('\\u0000')") ||
                uiThreadBlock.contains("finally {\r\n                                    newSecret.fill('\\u0000')")
        )
        assertTrue(
            "newSecret must also be zeroized if posting to UI thread fails (!postedToUi)",
            changeBranch.contains("if (!postedToUi)") && changeBranch.contains("newSecret.fill('\\u0000')")
        )
    }

    @Test
    fun pinDotsNoticeIsOneTimeAndVerificationPromptsStayUsable() {
        val settingsText = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/activities/SettingsActivityVault.kt"
        ).readText()
        val pinDotsBody = settingsText.substringAfter("private fun SettingsActivity.setupShowVaultPinDots()")
            .substringBefore("internal fun SettingsActivity.setupShareMetadata")
        assertTrue(
            "The comparison preview must be gated on the persisted one-time flag",
            pinDotsBody.contains("if (enabling && !config.pinDotsNoticeShown)")
        )
        assertTrue(
            "Re-enabling later must still open the verification prompt",
            pinDotsBody.contains("} else {\n            proceedWithAuth()") ||
                pinDotsBody.contains("} else {\r\n            proceedWithAuth()")
        )

        val dialogText = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/dialogs/VaultSecurityDialog.kt"
        ).readText()
        val verifyOnlyBranch = dialogText.substringAfter("} else if (verifyOnly) {")
            .substringBefore("} else {\n            inputHelper.unlockWithSecretAsync(")
            .substringBefore("} else {\r\n            inputHelper.unlockWithSecretAsync(")
        assertTrue(
            "verifyOnly must copy the caller's buffer before the async KDF, which otherwise reads an already-zeroed array",
            verifyOnlyBranch.contains("val secretCopy = secret.copyOf()") &&
                verifyOnlyBranch.contains("CryptoManager.isCurrentVaultSecret(activity, secretCopy)")
        )
        assertTrue(
            "The entered PIN length must come from the copy taken before the buffer is cleared",
            verifyOnlyBranch.contains("val enteredLength = secretCopy.size") &&
                verifyOnlyBranch.contains("if (currentType == PROTECTION_PIN) enteredLength else 0")
        )
    }


    @Test
    fun shutterSweepLiftAppliesOnlyToManualPinEntry() {
        val layout = locateAppFile("src/main/res/layout/dialog_vault_security.xml").readText()
        val sweepBlock = layout
            .substringAfter("android:id=\"@+id/vault_unlock_sweep\"")
            .substringBefore("/>")
        assertTrue(
            "Biometric unlock must keep the original centered sweep",
            sweepBlock.contains("android:layout_gravity=\"center_vertical\"")
        )

        val animator = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/VaultShutterAnimator.kt"
        ).readText()
        assertTrue(
            "Manual PIN entry lifts the legacy sweep above the bullets",
            animator.contains("sweepView.translationY = -sweepLiftDp * density") &&
                animator.contains("const val LEGACY_SWEEP_LIFT_DP = 24f")
        )

        for (relative in listOf(
            "src/main/kotlin/org/fossify/gallery/activities/VaultLockActivity.kt",
            "src/main/kotlin/org/fossify/gallery/dialogs/VaultSecurityDialog.kt"
        )) {
            val source = locateAppFile(relative).readText()
            assertTrue(
                "$relative must mark biometric unlocks so they keep the original sweep",
                source.contains("completeUnlockSuccess(viaBiometric = true)") ||
                    source.contains("onAuthenticated(viaBiometric = true)")
            )
            assertTrue(
                "$relative must pass the lift for manual PIN entry only",
                source.contains("sweepLiftDp = if (!viaBiometric")
            )
        }
    }

    @Test
    fun biometricProbeDefersWhileLockedAndNeverDeletesTheAliasOnTransientErrors() {
        val availability = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/BiometricAvailability.kt"
        ).readText()
        assertTrue(
            "Locked or transient Keystore failures must be deferred, not reported as missing keys",
            availability.contains("enum class BiometricKeyProbe") &&
                availability.contains("BiometricKeyProbe.DEFERRED")
        )
        val biometrics = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerBiometrics.kt"
        ).readText()
        val deletionBlock = biometrics
            .substringAfter("BiometricHardwareBacking.SOFTWARE_ONLY -> {")
            .substringBefore("BiometricHardwareBacking.TRANSIENT_ERROR")
        assertTrue(
            "Alias deletion must require an unlocked device and a confirmed software-only key",
            deletionBlock.contains("if (!isDeviceLocked(context))") &&
                deletionBlock.contains("keyStore.deleteEntry")
        )
        val settings = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt"
        ).readText() + "\n" + locateAppFile(
            "src/main/kotlin/org/fossify/gallery/activities/SettingsActivityVault.kt"
        ).readText()
        assertTrue(
            "Settings must re-probe when the keyguard is dismissed",
            settings.contains("Intent.ACTION_USER_PRESENT") &&
                settings.contains("BiometricKeyProbe.DEFERRED -> Unit") &&
                settings.contains("BiometricKeyProbe.READY -> biometricKeyAvailable = true")
        )
    }

    @Test
    fun fossifyBiometricFlowsReuseVaultBiometricPillButtonCentered() {
        val unlockActivity = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/activities/AppUnlockActivity.kt"
        ).readText()
        val adapter = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/adapters/AppPasswordTypesAdapter.kt"
        ).readText()
        val extensions = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/extensions/AppSecurityExtensions.kt"
        ).readText()
        val authHelper = locateAppFile(
            "src/main/kotlin/org/fossify/gallery/helpers/VaultAuthInputHelper.kt"
        ).readText()

        assertTrue(
            "AppUnlockActivity must restyle the biometric tab with styleBiometricIdTab",
            unlockActivity.contains("styleBiometricIdTab(tabView, this)")
        )
        assertTrue(
            "AppPasswordTypesAdapter must restyle the biometric tab with styleBiometricIdTab",
            adapter.contains("styleBiometricIdTab(view, context)")
        )
        assertTrue(
            "styleBiometricIdTab must reuse VaultAuthInputHelper.applyBiometricButtonStyle, set use_biometrics label, and keep centered constraints",
            extensions.contains("VaultAuthInputHelper.applyBiometricButtonStyle(") &&
                extensions.contains("R.string.use_biometrics") &&
                extensions.contains("params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID") &&
                extensions.contains("params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID") &&
                extensions.contains("params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID") &&
                extensions.contains("params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID")
        )
        assertTrue(
            "VaultAuthInputHelper must share applyBiometricButtonStyle with vaultBiometricBtn",
            authHelper.contains("fun applyBiometricButtonStyle(") &&
                authHelper.contains("button = binding.vaultBiometricBtn")
        )
    }

}
