package org.fossify.gallery.helpers.vault

import android.content.Context
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.ENCRYPTED_BIN_EXT
import org.fossify.gallery.helpers.findVaultRoot
import org.fossify.gallery.helpers.randomHex
import org.fossify.gallery.helpers.updateVaultKeyring
import org.fossify.gallery.helpers.verifyFileIntegrity
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/**
 * Rotates the vault KEK: a fresh key becomes active and every canonical container header is
 * re-encrypted with it while the (Tink authenticated) payload is copied verbatim.
 *
 * The operation is crash/idempotency safe. The extended keyring (old keys + new key) is
 * committed to `.vault` BEFORE any file is touched, so both old and already-rewritten headers
 * remain readable even if the process dies mid-rotation. On the next attempt files already
 * using the new keyId are simply skipped.
 */
object VaultKeyRotator {

    fun rotate(
        context: Context?,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean {
        if (!CryptoManager.isVaultUnlocked || CryptoManager.unlockedKeyring.isEmpty()) {
            return false
        }
        val vaultRoot = CryptoManager.cachedVaultRoot ?: context?.let { CryptoManager.findVaultRoot(it) } ?: return false

        val maxId = CryptoManager.unlockedKeyring.keys.maxOrNull() ?: 0
        if (maxId == Short.MAX_VALUE) {
            return false
        }
        val newId = (maxId + 1).toShort()

        val newKek = ByteArray(32).also { SecureRandom().nextBytes(it) }

        // Make the new key readable BEFORE rewriting anything. Store a copy: the local newKek
        // is zeroized in finally, while the keyring entry must survive a failed attempt so the
        // already-rewritten files stay readable and can be skipped on a later retry.
        CryptoManager.unlockedKeyring[newId] = newKek.copyOf()

        // Deep snapshot. updateVaultKeyring() zeroizes the live keyring entries before it
        // re-populates the keyring, so a shallow map (or the live map itself) would be wiped.
        // All old keys are retained so any missed/archived file stays readable.
        val keyringSnapshot = LinkedHashMap<Short, ByteArray>()
        CryptoManager.unlockedKeyring.forEach { (id, key) -> keyringSnapshot[id] = key.copyOf() }

        // Commit first: old keys are retained, so both old and new headers stay readable
        // regardless of where the rotation is interrupted.
        if (!runCatching { CryptoManager.updateVaultKeyring(context, keyringSnapshot, newId) }.isSuccess) {
            newKek.fill(0)
            keyringSnapshot.values.forEach { it.fill(0) }
            return false
        }

        var success = false
        try {
            val files = vaultRoot.walkTopDown().filter { CryptoManager.isEncryptedBinFile(it) }.toList()
            val total = files.size
            var done = 0

            for (file in files) {
                val temp = File(file.parentFile, ".tmp_${CryptoManager.randomHex(8)}$ENCRYPTED_BIN_EXT")
                try {
                    // The new key is already in the keyring, so readMetadata can open headers
                    // written with either an old key or the pending new key.
                    val meta = CryptoManager.readMetadata(file, includeThumbnail = false, cacheResult = false)
                        ?: throw IOException("Cannot read metadata for ${file.name}")

                    if (meta.keyId == newId) {
                        // Idempotent resume: this file was already rewritten in an earlier attempt.
                        done++
                        onProgress(done, total)
                        continue
                    }

                    val fileId = meta.fileId ?: throw IOException("Missing file id for ${file.name}")
                    VaultContainerWriter.rewriteContainer(
                        source = file,
                        target = temp,
                        kek = newKek,
                        keyId = newId,
                        fileId = fileId,
                        dek = meta.fileKey,
                        version = meta.version,
                        sourcePayloadOffset = meta.payloadOffset,
                        ciphertextLength = meta.ciphertextLength,
                        segmentSize = meta.segmentSize,
                        originalName = meta.originalName,
                        lastModified = meta.lastModified,
                        dateTaken = meta.dateTaken,
                        originalSize = meta.originalSize,
                        mediaType = meta.mediaType,
                        videoDuration = meta.videoDuration,
                        thumbnail = meta.thumbnailBytes
                    )

                    val rewritten = CryptoManager.readMetadata(temp, includeThumbnail = false, cacheResult = false)
                        ?: throw IOException("Cannot read rewritten header for ${file.name}")
                    if (rewritten.keyId != newId || !CryptoManager.verifyFileIntegrity(temp, rewritten)) {
                        throw IOException("Integrity check failed for ${file.name}")
                    }

                    // Keep the on-disk timestamp stable while swapping the container in place.
                    temp.setLastModified(file.lastModified())
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)

                    // readMetadata is keyed by absolute path: drop the pre-rotation header so a
                    // later read sees the new keyId/DEK instead of the stale cached metadata.
                    CryptoManager.metadataCache.remove(file.absolutePath)

                    done++
                    onProgress(done, total)
                } finally {
                    temp.delete()
                }
            }

            success = true
        } catch (_: Exception) {
            // The extended keyring is already on disk; already-rewritten files stay readable
            // and are skipped on the next attempt.
            success = false
        } finally {
            newKek.fill(0)
            keyringSnapshot.values.forEach { it.fill(0) }
        }

        return success
    }
}
