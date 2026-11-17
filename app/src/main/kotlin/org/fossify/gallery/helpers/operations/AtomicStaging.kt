package org.fossify.gallery.helpers.operations

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.secureWipeAndDeleteFile
import org.fossify.gallery.helpers.syncDir
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * ADR 0003 §3a / §3: write to a staging file in the *same directory* as the final destination,
 * fsync it, then commit with an atomic rename. A dead process can therefore only leave an orphan
 * `.stage_*` file — never a partially written file under the final name.
 *
 * When ATOMIC_MOVE is unsupported the caller must mark the item FAILED. There is deliberately no
 * copy+delete fallback: it would reintroduce partial files and double peak disk usage.
 */
object AtomicStaging {

    private const val STAGE_PREFIX = ".stage_"
    private const val STAGE_SUFFIX = ".tmp"

    fun fileFor(target: File, ctx: OperationContext): File {
        val operationId = ctx.operationId ?: "adhoc"
        val index = if (ctx.itemIndex >= 0) "_${ctx.itemIndex}" else ""
        val parent = target.parentFile ?: File(".")
        return File(parent, "$STAGE_PREFIX$operationId$index$STAGE_SUFFIX")
    }

    /** Flush the staged bytes and commit them under the final name. */
    fun commit(staging: File, target: File) {
        RandomAccessFile(staging, "rw").use { it.fd.sync() }
        CryptoManager.syncDir(staging.parentFile)
        Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        CryptoManager.syncDir(target.parentFile)
    }

    /**
     * Remove an orphan staging file. Plaintext-bearing staging files (decrypt, plain copy) are
     * securely wiped; ciphertext-only staging files can be deleted normally (ADR 0002 §7).
     */
    fun discard(staging: File, containsPlaintext: Boolean, context: Context? = null) {
        if (!staging.exists()) return
        if (containsPlaintext) {
            CryptoManager.secureWipeAndDeleteFile(
                file = staging,
                context = context,
                syncParentDir = true,
                purgeTraces = false
            )
        } else {
            staging.delete()
            CryptoManager.syncDir(staging.parentFile)
        }
    }

    /** Stable names used by the startup orphan cleanup (ADR §9/6). */
    fun isStagingFile(name: String): Boolean =
        name.startsWith(STAGE_PREFIX) && name.endsWith(STAGE_SUFFIX)

    // ==================== SAF / DocumentFile targets ====================

    /** SAF staging name; mirrors the direct-File naming so journals stay readable. */
    fun safStageName(ctx: OperationContext): String {
        val operationId = ctx.operationId ?: "adhoc"
        val index = if (ctx.itemIndex >= 0) "_${ctx.itemIndex}" else ""
        return "$STAGE_PREFIX$operationId$index$STAGE_SUFFIX"
    }

    fun isSafStaging(name: String): Boolean = isStagingFile(name)

    /**
     * SAF commit: `DocumentFile.renameTo` is a directory-entry operation for tree providers.
     * A failure is reported to the caller — there is deliberately no copy+delete fallback,
     * which would reintroduce a partial file under the final name (ADR 0003 §3a).
     */
    fun commitSaf(staging: DocumentFile, finalName: String): Boolean =
        runCatching { staging.renameTo(finalName) }.getOrDefault(false)

    fun discardSaf(staging: DocumentFile) {
        runCatching { staging.delete() }
    }

    /** Full-stream SHA-256 for SAF targets, where no `File` path exists for MediaIdentity. */
    fun safFingerprint(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.use { streamFingerprint(it) } ?: ""
    }.getOrDefault("")

    fun streamFingerprint(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
