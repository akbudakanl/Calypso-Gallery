package org.fossify.gallery.helpers

import java.io.File

enum class VaultExportHeaderCheck { SAFE, DIFFERENT_VAULT, UNKNOWN_ENCRYPTED_CONTENT }

/** Pure decision: is it safe to write the source vault into this export directory? */
fun checkVaultExportTarget(sourceVaultRoot: File, exportDir: File): VaultExportHeaderCheck {
    val sourceHeader = File(sourceVaultRoot, VAULT_HEADER_FILE)
    if (!sourceHeader.isFile) return VaultExportHeaderCheck.UNKNOWN_ENCRYPTED_CONTENT

    val destHeader = File(exportDir, VAULT_HEADER_FILE)
    if (!destHeader.isFile) {
        val hasEncryptedFiles = runCatching {
            exportDir.listFiles()?.any { it.isFile && it.name.endsWith(ENCRYPTED_FILE_EXT, ignoreCase = true) }
        }.getOrDefault(false) == true
        return if (hasEncryptedFiles) {
            VaultExportHeaderCheck.UNKNOWN_ENCRYPTED_CONTENT
        } else {
            VaultExportHeaderCheck.SAFE
        }
    }

    val identical = runCatching { sourceHeader.readBytes().contentEquals(destHeader.readBytes()) }
        .getOrDefault(false)
    return if (identical) VaultExportHeaderCheck.SAFE else VaultExportHeaderCheck.DIFFERENT_VAULT
}
