package org.fossify.gallery.extensions

import android.app.Activity
import android.os.Build
import android.text.Html
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.VaultBiometricOfferDialog
import org.fossify.gallery.dialogs.VaultNoticeDialog
import org.fossify.gallery.helpers.CIPHER_APP_DIR_NAME
import org.fossify.gallery.helpers.CLI_STAGING_DIR_NAME
import org.fossify.gallery.helpers.CLI_WRITING_SENTINEL
import org.fossify.gallery.helpers.CliBridgeManager
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.VaultExportHeaderCheck
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.checkVaultExportTarget
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.helpers.exportEncryptedVault
import org.fossify.gallery.helpers.findCandidateExternalVaultRoots
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.listExternalStorageVolumeRoots
import org.fossify.gallery.helpers.resolveExportVaultTargetDir
import org.fossify.gallery.helpers.resolveSyncSourceRoot
import org.fossify.gallery.helpers.syncVaultFromExternalDirectory
import java.io.File
import java.time.Instant

private fun BaseSimpleActivity.defaultSyncSourcePickerPath(): String {
    return CryptoManager.findCandidateExternalVaultRoots(this)
        .firstOrNull()
        ?.let { it.parentFile ?: it }
        ?.takeIf { it.exists() && it.isDirectory }
        ?.absolutePath
        ?: CryptoManager.listExternalStorageVolumeRoots(this)
            .firstOrNull { it.exists() && it.isDirectory }
            ?.absolutePath
        ?: internalStoragePath
}

private fun BaseSimpleActivity.defaultCliPickerPath(): String? {
    val internalCli = File(File(internalStoragePath, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME)
    if (internalCli.isDirectory) {
        return internalCli.parentFile?.absolutePath
    }
    for (volume in CryptoManager.listExternalStorageVolumeRoots(this)) {
        val nestedCli = File(File(volume, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME)
        val directCli = File(volume, CLI_STAGING_DIR_NAME)
        if (nestedCli.isDirectory || directCli.isDirectory) {
            return volume.absolutePath
        }
    }
    return null
}

fun BaseSimpleActivity.promptSyncVaultFromExternal(
    forcePasswordPrompt: Boolean = false,
    onSynced: (() -> Unit)? = null,
    onCancelled: (() -> Unit)? = null
) {
    if (!hasVaultCreated()) {
        toast(R.string.enable_hidden_protection_first_prompt)
        return
    }

    // Outside an active encrypted-album session the vault is unlocked only for the duration of this flow.
    // Wiping the key on every exit (success or cancel) guarantees the next visit has to prompt again.
    val wipeKeysOnExit: () -> Unit = { VaultSessionManager.lockVaultIfOutsideSession(this) }
    val onFlowCancelled: () -> Unit = {
        wipeKeysOnExit()
        onCancelled?.invoke()
    }
    val onFlowFinished: () -> Unit = {
        wipeKeysOnExit()
        onSynced?.invoke()
    }

    ensureVaultUnlocked(forcePrompt = forcePasswordPrompt, onCancelled = onFlowCancelled) {
        // The bridge is prepared on demand: no separate settings row is needed.
        CliBridgeManager.ensureActiveBridge(this)
        val internalCli = File(File(internalStoragePath, CIPHER_APP_DIR_NAME), CLI_STAGING_DIR_NAME)
        // Both CLI/ and CLI/Archived/ are app-owned transfer areas, including the
        // per-vault bridge folders directly below them. External disks are excluded.
        val cliRoots = listOf(CliBridgeManager.root(this), CliBridgeManager.archiveRoot(this))
        val isAppOwnedStaging = { dir: File ->
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(dir.path)
            cliRoots.any { root ->
                runCatching {
                    val rootPath = root.canonicalPath
                    canonical == rootPath || canonical.startsWith(rootPath + File.separator)
                }.getOrDefault(false)
            }
        }

        // Removes ONLY the files listed in the consumed manifest and their
        // now-empty album folders. Unrelated files in the staging folder stay.
        val clearStagedEntries = { stagingDir: File, entries: List<String> ->
            if (stagingDir.isDirectory) {
                val base = runCatching { stagingDir.canonicalFile }.getOrDefault(stagingDir)
                entries.forEach { rel ->
                    runCatching {
                        val bin = File(stagingDir, rel)
                        val canonical = bin.canonicalFile
                        if (canonical.path.startsWith(base.path + File.separator) && bin.isFile) {
                            bin.delete()
                            val parent = bin.parentFile
                            if (parent != null && parent != stagingDir && parent.listFiles().isNullOrEmpty()) {
                                parent.delete()
                            }
                        }
                    }
                }
            }
        }

        val runImport = runImport@{ sourceDir: File ->
            val manifestEntries: List<String> = runCatching {
                val manifest = File(sourceDir, CryptoManager.PENDING_SYNC_FILE_NAME)
                if (manifest.isFile) {
                    manifest.readLines().map { it.trim().replace('\\', '/') }
                        .filter { it.isNotEmpty() && !it.startsWith("/") && !it.contains("..") }
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())

            val resolvedSource = CryptoManager.resolveSyncSourceRoot(sourceDir)
            val activeVaultRoot = CryptoManager.findVaultRoot(this)
            val sameVault = activeVaultRoot != null && runCatching {
                resolvedSource.canonicalPath == activeVaultRoot.canonicalPath
            }.getOrDefault(false)
            if (sameVault) {
                showVaultLocationNoticeDialog(R.string.vault_sync_same_folder_error, onFlowCancelled)
                return@runImport
            }

            ensureNotificationPermissionThenRun { notificationsAllowed ->
                val progressDialog = CryptoProgressDialog(
                    activity = this,
                    max = 0,
                    titleId = R.string.syncing_vault_progress,
                    enableNotification = notificationsAllowed
                )
                ensureBackgroundThread {
                    val result = CryptoManager.syncVaultFromExternalDirectory(
                        context = this,
                        sourceDir = sourceDir
                    ) { processed, total ->
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                progressDialog.updateProgress(processed, total)
                            }
                        }
                    }
                    runOnUiThread {
                        progressDialog.dismiss()
                        if (isFinishing || isDestroyed) {
                            onFlowFinished()
                            return@runOnUiThread
                        }
                        when {
                            result == null -> toast(org.fossify.commons.R.string.unknown_error_occurred)
                            result.copiedCount == 0 &&
                                result.renamedCount == 0 &&
                                result.skippedDuplicateCount == 0 &&
                                result.failedCount == 0 -> toast(R.string.vault_sync_nothing_to_sync)
                            else -> {
                                val summaryView = layoutInflater.inflate(
                                    org.fossify.commons.R.layout.dialog_message,
                                    null
                                )
                                summaryView.findViewById<TextView>(org.fossify.commons.R.id.message).text =
                                    getString(
                                        R.string.vault_sync_summary_message,
                                        result.copiedCount,
                                        result.renamedCount,
                                        result.skippedDuplicateCount,
                                        result.failedCount
                                    )
                                getAlertDialogBuilder()
                                    .setPositiveButton(org.fossify.commons.R.string.ok, null)
                                    .apply {
                                        setupDialogStuff(
                                            summaryView,
                                            this,
                                            titleId = R.string.sync_vault_from_external
                                        )
                                    }
                            }
                        }
                        // Only the app-owned CLI staging folder is cleaned up; an external
                        // disk (possibly a full vault backup) is never modified by import.
                        if (result != null && result.failedCount == 0 &&
                            result.copiedCount + result.renamedCount + result.skippedDuplicateCount > 0 &&
                            isAppOwnedStaging(sourceDir)
                        ) {
                            clearStagedEntries(sourceDir, manifestEntries)
                        }
                        onFlowFinished()
                    }
                }
            }
        }

        val activeFingerprint = CliBridgeManager.currentFingerprint()
        // Archived bridges stay usable: a vault that is active from the archive
        // keeps its bridge under CLI/Archived instead of being copied to CLI/.
        val pendingBridges = CliBridgeManager.bridges(this, includeArchived = true).filter {
            File(it.dir, CryptoManager.PENDING_SYNC_FILE_NAME).isFile &&
                (activeFingerprint == null ||
                    it.fingerprint.equals(activeFingerprint, ignoreCase = true))
        }

        fun importBridge(bridge: CliBridgeManager.Bridge) {
            if (activeFingerprint != null && !bridge.fingerprint.equals(activeFingerprint, ignoreCase = true)) {
                showVaultLocationNoticeDialog(R.string.export_vault_different_vault, onFlowCancelled)
                return
            }
            val marker = File(bridge.dir, CLI_WRITING_SENTINEL)
            if (marker.isFile) {
                val raw = runCatching { marker.readText(Charsets.UTF_8).trim() }.getOrDefault("")
                val startedMs = runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                val isStale = startedMs != null && System.currentTimeMillis() - startedMs > 30 * 60 * 1000L
                if (startedMs == null || !isStale) {
                    showVaultLocationNoticeDialog(R.string.vault_sync_cli_busy, onFlowCancelled)
                    return
                }
                getAlertDialogBuilder()
                    .setTitle(R.string.vault_sync_stale_marker_title)
                    .setMessage(String.format(getString(R.string.vault_sync_stale_marker_message), raw))
                    .setNegativeButton(R.string.vault_sync_stale_marker_keep, null)
                    .setPositiveButton(R.string.vault_sync_stale_marker_remove) { _, _ ->
                        marker.delete()
                        runImport(bridge.dir)
                    }
                    .show()
            } else {
                runImport(bridge.dir)
            }
        }

        val startImport = {
            // No folder picker and no chooser: the bridge marked active wins.
            val target = pendingBridges.firstOrNull { it.active } ?: pendingBridges.firstOrNull()
            when {
                target != null -> importBridge(target)

                internalCli.isDirectory && File(internalCli, CLI_WRITING_SENTINEL).exists() ->
                    showVaultLocationNoticeDialog(R.string.vault_sync_cli_busy, onFlowCancelled)

                internalCli.isDirectory &&
                    File(internalCli, CryptoManager.PENDING_SYNC_FILE_NAME).isFile ->
                    runImport(internalCli)

                else -> {
                    toast(R.string.vault_sync_nothing_to_sync)
                    onFlowCancelled()
                }
            }
        }

        startImport()
    }
}

fun BaseSimpleActivity.promptExportEncryptedVault(
    onlyVaultHeader: Boolean,
    onExported: (() -> Unit)? = null
) {
    if (!hasVaultCreated()) {
        toast(R.string.enable_hidden_protection_first_prompt)
        return
    }

    // Export is a pure file copy. No vault password is needed; the caller applies the shared
    // device-owner check (Class 3 biometric or device credential) before reaching this point.
    run {
        pickCustomVaultDirectory(
            initialPath = defaultSyncSourcePickerPath(),
            requireWritable = true,
            onPicked = { pickedDir ->
                val targetExportDir = CryptoManager.resolveExportVaultTargetDir(pickedDir)
                val activeVaultRoot = CryptoManager.findVaultRoot(this)
                if (activeVaultRoot != null) {
                    when (checkVaultExportTarget(activeVaultRoot, targetExportDir)) {
                        VaultExportHeaderCheck.SAFE -> Unit
                        VaultExportHeaderCheck.DIFFERENT_VAULT -> {
                            showVaultLocationNoticeDialog(R.string.export_vault_different_vault, {})
                            return@pickCustomVaultDirectory
                        }
                        VaultExportHeaderCheck.UNKNOWN_ENCRYPTED_CONTENT -> {
                            showVaultLocationNoticeDialog(R.string.export_vault_unknown_content, {})
                            return@pickCustomVaultDirectory
                        }
                    }
                }
                val invalidTarget = activeVaultRoot != null && runCatching {
                    val exportCanonical = targetExportDir.canonicalPath
                    val vaultCanonical = activeVaultRoot.canonicalPath
                    exportCanonical == vaultCanonical || exportCanonical.startsWith("$vaultCanonical${File.separator}")
                }.getOrDefault(false)

                if (invalidTarget) {
                    showVaultLocationNoticeDialog(R.string.vault_export_same_folder_error, {})
                    return@pickCustomVaultDirectory
                }

                ensureNotificationPermissionThenRun { notificationsAllowed ->
                    val progressDialog = CryptoProgressDialog(
                        activity = this,
                        max = 0,
                        titleId = R.string.exporting_vault_progress,
                        enableNotification = notificationsAllowed
                    )
                    ensureBackgroundThread {
                        val result = CryptoManager.exportEncryptedVault(
                            context = this,
                            targetDir = pickedDir,
                            onlyVaultHeader = onlyVaultHeader
                        ) { processed, total ->
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    progressDialog.updateProgress(processed, total)
                                }
                            }
                        }
                        runOnUiThread {
                            progressDialog.dismiss()
                            if (!isFinishing && !isDestroyed) {
                                when {
                                    result == null -> toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    result.failedFilesCount > 0 && result.exportedFilesCount == 0 -> {
                                        toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    }
                                    else -> toast(org.fossify.commons.R.string.exporting_successful)
                                }
                            }
                            onExported?.invoke()
                        }
                    }
                }
            },
            onCancelled = {}
        )
    }
}

/** Shown right after vault creation / password change, before any biometric offer. */
fun BaseSimpleActivity.showVaultPasswordSavedDialog(
    isInitialCreation: Boolean = false,
    onDone: () -> Unit,
) {
    // Tapping outside does not dismiss it; Back does. Both Back and the confirmation button
    // continue to the one-time biometric offer (3b), followed by the 3c tutorial when creating
    // a vault. Backing out of 3a used to skip 3b entirely, which also skipped the callbacks that
    // refresh the vault settings screen — the "create vault" row stayed visible until the
    // settings category was reopened. Declining/backing out of 3b still leaves biometrics off
    // (the offer defaults it to off) and then finishes setup through 3c / onDone.
    val finishSetup: () -> Unit = {
        if (isInitialCreation && !isFinishing && !isDestroyed) {
            showVaultPullTutorialDialog { onDone() }
        } else {
            onDone()
        }
    }

    val proceedToBiometricOffer: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            config.vaultBiometricOfferShown = false
            VaultBiometricOfferDialog.showIfNeeded(this) {
                finishSetup()
            }
        } else {
            onDone()
        }
    }

    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.vault_password_saved_title),
        message = getString(R.string.vault_password_saved_message) +
            "\n\n" + getString(R.string.vault_password_reminder_setup_note),
        positiveText = getString(R.string.vault_password_saved_confirm),
        cancelable = true,
        onPositive = proceedToBiometricOffer,
        onCancel = proceedToBiometricOffer,
    )
}

/**
 * Screen 3c (Template A): Live mini gallery & top-scroll pull tutorial shown at the end of the
 * vault creation flow so the user discovers how to open the hidden encrypted vault.
 */
fun BaseSimpleActivity.showVaultPullTutorialDialog(onDone: () -> Unit = {}) {
    VaultNoticeDialog(
        activity = this,
        title = getString(R.string.vault_pull_tutorial_title),
        message = Html.fromHtml(
            getString(R.string.vault_pull_tutorial_message),
            Html.FROM_HTML_MODE_LEGACY,
        ),
        noteText = getString(R.string.vault_pull_tutorial_note),
        positiveText = getString(R.string.vault_pull_tutorial_confirm),
        // The final tutorial is the last mandatory step of vault setup: Back must not skip it.
        // Completion goes through the positive button only.
        cancelable = false,
        showPullTutorial = true,
        filledPositiveButton = true,
        alignSingleActionEnd = true,
        onPositive = onDone,
    )
}

/**
 * Theme-aware card shown before the system biometric enrollment prompt (HTML screen 3b).
 * The system prompt itself is drawn by Android and cannot be restyled, so the full explanation
 * lives here and the prompt only carries its own title/description.
 */
fun BaseSimpleActivity.showBiometricPasswordNoteDialog(
    title: String,
    message: String,
    note: String,
    onContinue: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    VaultNoticeDialog(
        activity = this,
        title = title,
        message = message,
        noteText = note,
        positiveText = getString(R.string.vault_biometric_setup_continue),
        negativeText = getString(org.fossify.commons.R.string.cancel),
        showPositiveCheckIcon = true,
        cancelable = true,
        showBiometricGlyph = true,
        onPositive = onContinue,
        onNegative = onCancel ?: {},
        onCancel = onCancel ?: {},
    )
}

fun Activity.applyVaultScreenProtection(enable: Boolean, force: Boolean = false) {
    // The lock screen is always protected; other screens honour the user's preference.
    // While the whole-app password is enabled we always suppress task snapshots and screenshots:
    // the system captures the Recents preview when the app is left, which happens *before* the
    // 30-second grace window expires, so deciding at lock time would be too late.
    val shouldProtect = (enable && (force || config.blockVaultScreenshots)) ||
        config.isAppPasswordProtectionOn
    try {
        if (shouldProtect) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(shouldProtect)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!shouldProtect)
        }
        val decorView = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            decorView.importantForAutofill = if (shouldProtect) {
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_AUTOFILL_AUTO
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.importantForContentCapture = if (shouldProtect) {
                View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_CONTENT_CAPTURE_AUTO
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            decorView.setAccessibilityDataSensitive(
                if (shouldProtect) {
                    View.ACCESSIBILITY_DATA_SENSITIVE_YES
                } else {
                    View.ACCESSIBILITY_DATA_SENSITIVE_AUTO
                }
            )
        }
    } catch (_: Throwable) {
    }
}
