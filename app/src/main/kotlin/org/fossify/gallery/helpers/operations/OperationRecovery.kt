package org.fossify.gallery.helpers.operations

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.copyOrMoveEncryptedFileToDirectory
import org.fossify.gallery.helpers.decryptFileToDirectory
import org.fossify.gallery.helpers.encryptFileToDirectory
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.MediaIdentity
import org.fossify.gallery.models.PendingOperation
import java.io.File
import java.io.RandomAccessFile

/**
 * ADR 0003 §7/§9 — process-death recovery.
 *
 * Called at app start (and after vault unlock for encrypted operations). It removes orphan staging
 * files, resets interrupted items to PENDING so they are rewritten from their intact source, and
 * offers one dialog to continue every interrupted operation.
 */
object OperationRecovery {

    private val gson = Gson()
    private val completedStates = setOf(
        OperationJournal.STATE_SOURCE_DELETED,
        OperationJournal.STATE_SKIPPED_CONFLICT,
        OperationJournal.STATE_VERIFIED
    )

    @Volatile
    private var reconciled = false

    @Volatile
    private var promptShown = false

    /** Test/debug only: allow a fresh reconciliation pass in the same process. */
    fun resetForTests() {
        reconciled = false
        promptShown = false
    }

    /** Number of interrupted operations still needing a user decision. */
    fun reconcile(context: Context): Int {
        if (reconciled) return OperationJournal.resumableItems().groupBy { it.operationId }.size
        reconciled = true

        cleanupOrphanStaging(context)
        // Fix MediaStore/Room/manifest references of items that were already committed before
        // the process died. Guarded internally so each operation is processed at most once.
        runCatching { MetadataReconciler.reconcile(context.applicationContext) }
        OperationJournal.activeItems().forEach { item ->
            val ctx = OperationContext(
                operationId = item.operationId,
                itemIndex = item.itemIndex,
                kind = item.kind,
                batch = decodeBatch(item)
            )
            OperationJournal.resetToPending(ctx)
        }
        return OperationJournal.resumableItems().groupBy { it.operationId }.size
    }

    /**
     * Staging files are never committed under the final name, so every `.stage_*` file found after
     * a process death is garbage. Ciphertext files can be deleted normally; anything that does not
     * start with the vault magic may hold plaintext and is securely wiped (ADR 0002 §7).
     */
    fun cleanupOrphanStaging(context: Context) {
        if (ForegroundOperationRegistry.isActive) return
        val roots = LinkedHashSet<File>()
        OperationJournal.resumableItems().forEach { item ->
            File(item.destPath).parentFile?.let { roots.add(it) }
            // SAF staging documents are recorded as content:// URIs in stagingPath.
            if (item.stagingPath.startsWith("content://")) {
                runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(item.stagingPath))?.delete()
                }
            }
        }
        runCatching { File(context.internalStoragePath) }.getOrNull()?.let { roots.add(it) }
        runCatching { CryptoManager.findVaultRoot(context) }.getOrNull()?.let { roots.add(it) }

        roots.forEach { root ->
            if (!root.exists() || !root.isDirectory) return@forEach
            runCatching {
                root.walkTopDown()
                    .maxDepth(2)
                    .filter { it.isFile && AtomicStaging.isStagingFile(it.name) }
                    .forEach { staging ->
                        AtomicStaging.discard(
                            staging = staging,
                            containsPlaintext = !isCiphertext(staging),
                            context = context.applicationContext
                        )
                    }
            }
        }
    }

    private fun isCiphertext(file: File): Boolean = runCatching {
        val magic = ByteArray(8)
        RandomAccessFile(file, "r").use { it.readFully(magic) }
        String(magic, Charsets.US_ASCII) == "FGVLTENC"
    }.getOrDefault(false)

    private fun decodeBatch(item: PendingOperation): BatchOperationInfo? =
        if (item.batchPayload.isBlank()) null
        else runCatching { gson.fromJson(item.batchPayload, BatchOperationInfo::class.java) }.getOrNull()

    /** Continue every interrupted operation, one item at a time. */
    fun resume(context: Context, onDone: (Boolean) -> Unit) {
        val items = OperationJournal.resumableItems()
        if (items.isEmpty()) {
            onDone(true)
            return
        }

        var allSucceeded = true
        val acquired = ForegroundOperationRegistry.acquire(context, "resume")
        if (!acquired) {
            onDone(false)
            return
        }
        try {
        items.groupBy { it.operationId }.forEach { (operationId, operationItems) ->
            val batch = decodeBatch(operationItems.first())
            operationItems.sortedBy { it.itemIndex }.forEach { item ->
                if (item.state in completedStates) return@forEach
                val ctx = OperationContext(operationId, item.itemIndex, item.kind, batch)
                val succeeded = runCatching { resumeOne(context, item, ctx, batch) }.getOrDefault(false)
                if (!succeeded) allSucceeded = false
            }

            val finalStates = OperationJournal.itemsFor(operationId).map { it.state }
            if (finalStates.isNotEmpty() && finalStates.all { it in completedStates }) {
                OperationJournal.clear(operationId)
            }
        }
        } finally {
            ForegroundOperationRegistry.release(context, "resume")
        }
        onDone(allSucceeded)
    }

    private fun resumeOne(
        context: Context,
        item: PendingOperation,
        ctx: OperationContext,
        batch: BatchOperationInfo?
    ): Boolean {
        val source = File(item.sourcePath)
        val dest = File(item.destPath)
        val destDir = dest.parentFile ?: return false

        // A committed output whose source is already gone needs no rewrite.
        if (!source.exists()) {
            return item.state == OperationJournal.STATE_VERIFIED && dest.exists()
        }

        return when (item.kind) {
            OperationJournal.KIND_ENCRYPT -> CryptoManager.encryptFileToDirectory(
                context = context,
                sourceFile = source,
                targetDir = destDir,
                wipeSource = batch?.wipeSource ?: true,
                skipDuplicateCheck = true,
                operation = ctx
            ) != null

            OperationJournal.KIND_DECRYPT -> CryptoManager.decryptFileToDirectory(
                context = context,
                encryptedFile = source,
                targetDir = destDir,
                deleteEncryptedSource = batch?.deleteSource ?: true,
                skipDuplicateCheck = true,
                operation = ctx
            ) != null

            OperationJournal.KIND_COPY, OperationJournal.KIND_MOVE -> {
                if (dest.name.endsWith(".bin")) {
                    CryptoManager.copyOrMoveEncryptedFileToDirectory(
                        context = context,
                        sourceBin = source,
                        targetDir = destDir,
                        isMove = item.kind == OperationJournal.KIND_MOVE || batch?.isMove == true,
                        skipDuplicateCheck = true,
                        operation = ctx
                    ) != null
                } else {
                    resumePlainCopy(context, item, ctx, batch)
                }
            }

            else -> false
        }
    }

    private fun resumePlainCopy(
        context: Context,
        item: PendingOperation,
        ctx: OperationContext,
        batch: BatchOperationInfo?
    ): Boolean {
        val source = File(item.sourcePath)
        val dest = File(item.destPath)
        val staging = AtomicStaging.fileFor(dest, ctx)
        OperationJournal.markInFlight(ctx, staging.absolutePath)
        return try {
            source.copyTo(staging, overwrite = true, bufferSize = 256 * 1024)
            AtomicStaging.commit(staging, dest)
            OperationJournal.markVerified(ctx, MediaIdentity.computeFingerprint(dest))
            if (batch?.isMove == true || item.kind == OperationJournal.KIND_MOVE) {
                source.delete()
                OperationJournal.markSourceDeleted(ctx)
            }
            true
        } catch (e: Exception) {
            AtomicStaging.discard(staging, containsPlaintext = true, context = context.applicationContext)
            OperationJournal.markFailed(ctx, e.message ?: "resume copy failed")
            false
        }
    }

    /** One combined prompt per process, shown on app start or — for crypto items — after unlock. */
    fun maybePromptResume(activity: BaseSimpleActivity) {
        if (promptShown) return
        val pendingCount = reconcile(activity)
        if (pendingCount == 0) return

        val hasEncrypted = OperationJournal.resumableItems().any {
            it.kind == OperationJournal.KIND_ENCRYPT ||
                it.kind == OperationJournal.KIND_DECRYPT ||
                it.destPath.endsWith(".bin")
        }
        // Encrypted operations cannot resume without the vault key; wait for unlock.
        if (hasEncrypted && !CryptoManager.isVaultUnlocked) return

        promptShown = true
        AlertDialog.Builder(activity)
            .setTitle(R.string.resume_operations_title)
            .setMessage(activity.getString(R.string.resume_operations_message, pendingCount))
            .setPositiveButton(R.string.resume_continue) { _, _ ->
                resume(activity) { success ->
                    activity.toast(
                        if (success) R.string.resume_done else R.string.resume_failed
                    )
                }
            }
            .setNegativeButton(R.string.resume_later, null)
            .show()
    }
}
