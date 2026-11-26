package org.fossify.gallery.helpers.operations

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.views.MyTextView
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.CryptoProgressDialog
import org.fossify.gallery.dialogs.deleteSkippedCryptoSourcesBlocking
import org.fossify.gallery.dialogs.getSkippedEncryptFilesMode
import org.fossify.gallery.dialogs.resolveSkippedCryptoFiles
import org.fossify.gallery.extensions.TransferPairSpec
import org.fossify.gallery.extensions.cleanupEmptyPlaintextAlbumIfNeeded
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.ensureVaultUnlocked
import org.fossify.gallery.extensions.markReferencesOnDecrypt
import org.fossify.gallery.extensions.markReferencesOnEncrypt
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.resolveCryptoTransferConflicts
import org.fossify.gallery.extensions.updateDBMediaPath
import org.fossify.gallery.helpers.CONFLICT_HANDLING_STANDARD_DIALOG
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_DELETE
import org.fossify.gallery.helpers.CRYPTO_SKIPPED_FILES_KEEP
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.DeleteOperationEngine
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.helpers.MediaIndexCoordinator
import org.fossify.gallery.helpers.OperationNotificationCategory
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.cleanupEmptyEncryptedAlbumIfNeeded
import org.fossify.gallery.helpers.clearAppImageCaches
import org.fossify.gallery.helpers.copyOrMoveEncryptedFileToDirectory
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.ensureNotificationPermissionThenRun
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.isEncryptedFolder
import org.fossify.gallery.helpers.purgeMediaTracesPreservingReferences
import org.fossify.gallery.helpers.reconcileVaultRecycleBin
import org.fossify.gallery.helpers.resolvePlainMediaType
import org.fossify.gallery.helpers.secureWipeAndDeleteFile
import org.fossify.gallery.helpers.verifyFileIntegrity
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.PendingOperation
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ADR 0003 §7/§9 — process-death recovery.
 *
 * Called at app start (and after vault unlock for encrypted operations). It removes orphan staging
 * files, resets interrupted items to PENDING so they are rewritten from their intact source, and
 * offers one dialog to continue every interrupted operation.
 */
object OperationRecovery {


    private val promptedOperationIds = java.util.Collections.synchronizedSet(HashSet<String>())

    private val promptCheckInFlight = AtomicBoolean(false)

    internal enum class PromptMode {
        NONE,
        STANDARD
    }

    internal data class PromptSelection(
        val operationIds: Set<String>,
        val items: List<PendingOperation>,
        val mode: PromptMode,
        val distinctKinds: List<String>
    )

    internal data class PendingSkippedDialog(
        val operationId: String,
        val isEncrypt: Boolean,
        val skippedFiles: List<File>,
        val identicalTargetMap: Map<String, File>,
        val forceCleanupEmptyAlbum: Boolean
    )

    internal data class ResumeConflictOverride(
        val resolutionsBySourcePath: Map<String, Int> = emptyMap(),
        val identicalSourcePaths: Set<String> = emptySet(),
        val identicalTargetsBySourcePath: Map<String, File> = emptyMap(),
        val nonIdenticalSourcePaths: Set<String> = emptySet(),
        val ignoreRecordedConflictResolutions: Boolean = false
    )

    /** Test/debug only: allow a fresh reconciliation pass in the same process. */
    fun resetForTests() {
        OperationReconciler.reconciled = false
        promptedOperationIds.clear()
        promptCheckInFlight.set(false)
    }


    /** Number of interrupted operations still needing a user decision. */
    fun reconcile(context: Context): Int = OperationReconciler.reconcile(context)

    fun cleanupOrphanStaging(context: Context) = OperationReconciler.cleanupOrphanStaging(context)

    internal fun reconcileSilentCopyMoveAfterCrash(context: Context, isVaultUnlocked: Boolean): Int =
        OperationReconciler.reconcileSilentCopyMoveAfterCrash(context, isVaultUnlocked)

    /** Continue every interrupted operation, one item at a time. */
    fun resume(context: Context, onDone: (Boolean) -> Unit) =
        OperationResumeExecutor.resume(
            context = context,
            operationIds = null,
            conflictOverride = null,
            skippedEncryptCollector = null,
            skippedIdenticalMap = null,
            onProgress = null,
            onDone = onDone
        )

    internal fun resume(
        context: Context,
        operationIds: Set<String>? = null,
        conflictOverride: ResumeConflictOverride? = null,
        skippedEncryptCollector: MutableList<File>? = null,
        skippedIdenticalMap: MutableMap<String, File>? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onDone: (Boolean) -> Unit
    ) = OperationResumeExecutor.resume(
        context = context,
        operationIds = operationIds,
        conflictOverride = conflictOverride,
        skippedEncryptCollector = skippedEncryptCollector,
        skippedIdenticalMap = skippedIdenticalMap,
        onProgress = onProgress,
        onDone = onDone
    )

    internal fun resumeSilentPhase2EncryptWipes(context: Context): Int =
        OperationResumeExecutor.resumeSilentPhase2EncryptWipes(context)

    /**
     * Handles persisted post-transfer skipped crypto file decisions:
     * - decrypt batches are cleared silently: skipped files are left in place, never wiped or asked about;
     * - approved deletes or setting == DELETE run silently in background without prompting;
     * - setting == KEEP clears silently;
     * - unanswered ASK batches are returned so UI can re-show [resolveSkippedCryptoFiles].
     */
    internal fun processPendingSkippedCryptoWork(context: Context): List<PendingSkippedDialog> {
        val deferred = OperationJournal.allItems().filter { OperationJournal.hasDeferredSkippedWork(it) }
        if (deferred.isEmpty()) return emptyList()

        val dialogsToReopen = ArrayList<PendingSkippedDialog>()
        deferred.groupBy { it.operationId }.forEach { (opId, rows) ->
            val first = rows.first()
            val isEncrypt = first.kind == OperationJournal.KIND_ENCRYPT
            val batch = OperationJournal.decodeBatch(first.batchPayload)
            val forceCleanupEmptyAlbum = batch?.wipeSource == true

            val skippedFiles = rows.map { File(it.sourcePath) }.filter { it.exists() && it.isFile }
            if (skippedFiles.isEmpty()) {
                OperationJournal.clear(opId)
                return@forEach
            }
            val identicalTargetMap = LinkedHashMap<String, File>()
            rows.forEach { row ->
                if (row.destPath.isNotBlank()) {
                    identicalTargetMap[File(row.sourcePath).absolutePath] = File(row.destPath)
                }
            }

            val allApproved = rows.all { it.state == OperationJournal.STATE_DELETE_SKIPPED_APPROVED }
            val mode = if (isEncrypt) {
                runCatching { context.getSkippedEncryptFilesMode() }.getOrDefault(-1)
            } else {
                -1
            }
            when {
                !isEncrypt -> OperationJournal.clear(opId)
                allApproved || mode == CRYPTO_SKIPPED_FILES_DELETE -> {
                    ForegroundOperationRegistry.withOperation(context, "skipped-crypto-wipe") {
                        deleteSkippedCryptoSourcesBlocking(
                            context = context,
                            skippedFiles = skippedFiles,
                            identicalTargetMap = identicalTargetMap,
                            forceCleanupEmptyAlbum = forceCleanupEmptyAlbum
                        )
                    }
                    OperationJournal.clear(opId)
                }
                mode == CRYPTO_SKIPPED_FILES_KEEP -> {
                    OperationJournal.clear(opId)
                }
                else -> {
                    dialogsToReopen.add(
                        PendingSkippedDialog(
                            operationId = opId,
                            isEncrypt = isEncrypt,
                            skippedFiles = skippedFiles,
                            identicalTargetMap = identicalTargetMap,
                            forceCleanupEmptyAlbum = forceCleanupEmptyAlbum
                        )
                    )
                }
            }
        }
        return dialogsToReopen
    }

    internal fun selectPromptBatch(@Suppress("UNUSED_PARAMETER") isVaultUnlocked: Boolean): PromptSelection {
        val resumable = OperationJournal.resumableItems()
        if (resumable.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val batchCache = HashMap<String, BatchOperationInfo?>()
        val promptableItems = resumable.filter { item ->
            if (item.kind != OperationJournal.KIND_ENCRYPT && item.kind != OperationJournal.KIND_DECRYPT) {
                return@filter false
            }
            val batch = batchCache.getOrPut(item.operationId) {
                OperationJournal.decodeBatch(item.batchPayload)
            }
            !OperationJournal.isEncryptPhase2Wipe(item, batch)
        }
        if (promptableItems.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val byOp = promptableItems.groupBy { it.operationId }
        val eligibleEntries = byOp.filter { (opId, _) ->
            opId !in promptedOperationIds
        }
        if (eligibleEntries.isEmpty()) {
            return PromptSelection(emptySet(), emptyList(), PromptMode.NONE, emptyList())
        }
        val selectedItems = eligibleEntries.values.flatten()
        val distinctKinds = selectedItems.map { it.kind }.distinct()
        return PromptSelection(
            operationIds = eligibleEntries.keys.toSet(),
            items = selectedItems,
            mode = PromptMode.STANDARD,
            distinctKinds = distinctKinds
        )
    }

    internal fun discardOperations(operationIds: Set<String>) {
        operationIds.forEach { OperationJournal.clearAfterCompletionOrCancel(it) }
    }

    private fun formatOperationKindsLabel(context: Context, kinds: List<String>): String {
        val labels = kinds.mapNotNull { kind ->
            when (kind) {
                OperationJournal.KIND_COPY -> context.getString(R.string.resume_op_copy)
                OperationJournal.KIND_MOVE -> context.getString(R.string.resume_op_move)
                OperationJournal.KIND_ENCRYPT -> context.getString(R.string.resume_op_encrypt)
                OperationJournal.KIND_DECRYPT -> context.getString(R.string.resume_op_decrypt)
                else -> null
            }
        }.distinct()
        return if (labels.isNotEmpty()) labels.joinToString(", ") else context.getString(R.string.resume_op_copy)
    }

    private fun showPendingSkippedDialogIfReady(
        activity: BaseSimpleActivity,
        pendingSkipDialogs: List<PendingSkippedDialog>,
        onAllDone: () -> Unit = {}
    ) {
        val next = pendingSkipDialogs.firstOrNull { it.operationId !in promptedOperationIds }
        if (next == null) {
            onAllDone()
            return
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                onAllDone()
                return@runOnUiThread
            }
            if (!CryptoManager.isVaultUnlocked) {
                onAllDone()
                return@runOnUiThread
            }
            if (!promptedOperationIds.add(next.operationId)) {
                showPendingSkippedDialogIfReady(activity, pendingSkipDialogs.drop(1), onAllDone)
                return@runOnUiThread
            }

            activity.resolveSkippedCryptoFiles(
                isEncrypt = next.isEncrypt,
                skippedFiles = next.skippedFiles,
                identicalTargetMap = next.identicalTargetMap,
                forceCleanupEmptyAlbum = next.forceCleanupEmptyAlbum,
                existingSkipOpId = next.operationId
            ) { _ ->
                if (pendingSkipDialogs.size > 1) {
                    showPendingSkippedDialogIfReady(activity, pendingSkipDialogs.drop(1), onAllDone)
                } else {
                    onAllDone()
                }
            }
        }
    }

    /**
     * Recovers interrupted operations cleanly by layer:
     * 1. Orphan `.stage_*` / `.tmp_*` / plain recycle-bin temp files are cleaned silently;
     *    COPY and MOVE operations silently settle any verified source deletes without prompting.
     * 2. Upon vault unlock, already-encrypted Phase-2 secure wipes run silently in background.
     * 3. Approved/policy-delete skipped crypto files run silently in background; unanswered ASK
     *    batches re-open [resolveSkippedCryptoFiles] on unlock.
     * 4. Incomplete Phase-1 ENCRYPT / DECRYPT transfers prompt Yes/No on app launch; tapping Yes
     *    requests the vault password via [ensureVaultUnlocked] and freshly re-asks every conflict.
     */
    fun maybePromptResume(activity: BaseSimpleActivity) {
        if (ForegroundOperationRegistry.isActive) return
        if (!promptCheckInFlight.compareAndSet(false, true)) return

        val appContext = activity.applicationContext
        ensureBackgroundThread {
            try {
                reconcile(appContext)
                val vaultUnlocked = CryptoManager.isVaultUnlocked
                val initialSkipDialogs = if (vaultUnlocked) {
                    OperationReconciler.reconcileVaultRecycleBinAfterCrash(appContext)
                    reconcileSilentCopyMoveAfterCrash(appContext, isVaultUnlocked = true)
                    resumeSilentPhase2EncryptWipes(appContext)
                    processPendingSkippedCryptoWork(appContext)
                } else {
                    emptyList()
                }

                val selection = selectPromptBatch(vaultUnlocked)
                if (selection.mode == PromptMode.NONE || selection.items.isEmpty()) {
                    if (vaultUnlocked && initialSkipDialogs.isNotEmpty()) {
                        showPendingSkippedDialogIfReady(activity, initialSkipDialogs)
                    }
                    return@ensureBackgroundThread
                }

                val targetOpIds = selection.operationIds
                val totalItems = selection.items.size
                val opLabel = formatOperationKindsLabel(appContext, selection.distinctKinds)
                val promptMessage = appContext.getString(R.string.resume_operation_interrupted_message, opLabel, totalItems)
                val abortedMessage = appContext.getString(R.string.resume_operation_aborted_message, opLabel)

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (ForegroundOperationRegistry.isActive) return@runOnUiThread
                    if (targetOpIds.any { it in promptedOperationIds }) return@runOnUiThread
                    promptedOperationIds.addAll(targetOpIds)

                    val padding = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
                    val promptView = MyTextView(activity).apply {
                        setPadding(padding, padding, padding, 0)
                        text = promptMessage
                    }
                    activity.getAlertDialogBuilder()
                        .setCancelable(false)
                        .setPositiveButton(org.fossify.commons.R.string.yes) { _, _ ->
                            activity.ensureVaultUnlocked(
                                onCancelled = {
                                    promptedOperationIds.removeAll(targetOpIds)
                                }
                            ) {
                                ensureBackgroundThread {
                                    OperationReconciler.reconcileVaultRecycleBinAfterCrash(appContext)
                                    reconcileSilentCopyMoveAfterCrash(appContext, isVaultUnlocked = true)
                                    resumeSilentPhase2EncryptWipes(appContext)
                                    val unlockedSkipDialogs = processPendingSkippedCryptoWork(appContext)
                                    val combinedSkipDialogs = (initialSkipDialogs + unlockedSkipDialogs)
                                        .distinctBy { it.operationId }

                                    val remainingItems = OperationJournal.resumableItems()
                                        .filter { it.operationId in targetOpIds }
                                    val transferItems = remainingItems.filter {
                                        it.state != OperationJournal.STATE_VERIFIED && File(it.sourcePath).isFile
                                    }

                                    val finishSession: () -> Unit = {
                                        if (combinedSkipDialogs.isNotEmpty()) {
                                            showPendingSkippedDialogIfReady(activity, combinedSkipDialogs) {
                                                VaultSessionManager.lockVaultIfOutsideSession(activity)
                                            }
                                        } else {
                                            VaultSessionManager.lockVaultIfOutsideSession(activity)
                                        }
                                    }

                                    activity.runOnUiThread {
                                        if (activity.isFinishing || activity.isDestroyed) {
                                            VaultSessionManager.lockVaultIfOutsideSession(activity)
                                            return@runOnUiThread
                                        }
                                        if (remainingItems.isEmpty()) {
                                            finishSession()
                                            return@runOnUiThread
                                        }

                                        activity.resolveCryptoTransferConflicts(
                                            allowFastLargeCheck = true,
                                            buildPairs = {
                                                transferItems.mapNotNull { item ->
                                                    val srcFile = File(item.sourcePath)
                                                    val targetDir = File(item.destPath).parentFile ?: return@mapNotNull null
                                                    val isTargetEncrypted = (item.kind == OperationJournal.KIND_ENCRYPT) ||
                                                        item.destPath.endsWith(".bin")
                                                    TransferPairSpec(
                                                        sourceFile = srcFile,
                                                        targetDir = targetDir,
                                                        isTargetEncrypted = isTargetEncrypted,
                                                        desiredPlainName = if (!isTargetEncrypted && CryptoManager.isEncryptedFile(srcFile.name)) {
                                                            null
                                                        } else {
                                                            srcFile.name
                                                        }
                                                    )
                                                }
                                            }
                                        ) { scan, _ ->
                                            if (activity.isFinishing || activity.isDestroyed) {
                                                VaultSessionManager.lockVaultIfOutsideSession(activity)
                                                return@resolveCryptoTransferConflicts
                                            }
                                            activity.ensureNotificationPermissionThenRun {
                                                if (activity.isFinishing || activity.isDestroyed) {
                                                    VaultSessionManager.lockVaultIfOutsideSession(activity)
                                                    return@ensureNotificationPermissionThenRun
                                                }
                                                val progressDialog = CryptoProgressDialog(
                                                    activity = activity,
                                                    max = remainingItems.size,
                                                    titleId = R.string.resume_operations_title,
                                                    notificationChannelId = OperationNotificationCategory.CHANNEL_CRYPTO,
                                                    enableNotification = true
                                                )
                                                val skippedEncryptFiles = ArrayList<File>()
                                                val skippedIdenticalMap = LinkedHashMap<String, File>()
                                                var lastUiProgressMs = 0L
                                                val conflictOverride = ResumeConflictOverride(
                                                    resolutionsBySourcePath = scan.resolutions,
                                                    identicalSourcePaths = scan.identicalPaths,
                                                    identicalTargetsBySourcePath = scan.identicalTargets,
                                                    nonIdenticalSourcePaths = scan.nonIdenticalPaths,
                                                    ignoreRecordedConflictResolutions = true
                                                )
                                                ensureBackgroundThread {
                                                    resume(
                                                        context = activity,
                                                        operationIds = targetOpIds,
                                                        conflictOverride = conflictOverride,
                                                        skippedEncryptCollector = skippedEncryptFiles,
                                                        skippedIdenticalMap = skippedIdenticalMap,
                                                        onProgress = { current, total ->
                                                            val now = System.currentTimeMillis()
                                                            if (current == 0 || current >= total || now - lastUiProgressMs >= 80L) {
                                                                lastUiProgressMs = now
                                                                activity.runOnUiThread {
                                                                    progressDialog.updateProgress(current, total)
                                                                }
                                                            }
                                                        }
                                                    ) { success ->
                                                        activity.runOnUiThread {
                                                            try {
                                                                progressDialog.dismiss()
                                                            } catch (_: Exception) {}
                                                            activity.toast(
                                                                if (success) R.string.resume_done else R.string.resume_failed
                                                            )
                                                            if (skippedEncryptFiles.isNotEmpty() &&
                                                                !activity.isFinishing && !activity.isDestroyed
                                                            ) {
                                                                activity.resolveSkippedCryptoFiles(
                                                                    isEncrypt = true,
                                                                    skippedFiles = skippedEncryptFiles,
                                                                    identicalTargetMap = skippedIdenticalMap,
                                                                    forceCleanupEmptyAlbum = false
                                                                ) { _ ->
                                                                    finishSession()
                                                                }
                                                            } else {
                                                                finishSession()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .setNegativeButton(org.fossify.commons.R.string.no) { _, _ ->
                            ensureBackgroundThread {
                                discardOperations(targetOpIds)
                            }
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                val abortedView = MyTextView(activity).apply {
                                    setPadding(padding, padding, padding, 0)
                                    text = abortedMessage
                                }
                                activity.getAlertDialogBuilder()
                                    .setCancelable(false)
                                    .setPositiveButton(org.fossify.commons.R.string.ok, null)
                                    .apply {
                                        activity.setupDialogStuff(
                                            abortedView,
                                            this,
                                            titleId = R.string.resume_operations_title,
                                            cancelOnTouchOutside = false
                                        )
                                    }
                            }
                        }
                        .apply {
                            activity.setupDialogStuff(
                                promptView,
                                this,
                                titleId = R.string.resume_operations_title,
                                cancelOnTouchOutside = false
                            )
                        }
                }
            } finally {
                promptCheckInFlight.set(false)
            }
        }
    }
}
