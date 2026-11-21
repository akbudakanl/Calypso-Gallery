package org.fossify.gallery

import org.fossify.gallery.helpers.ENCRYPTED_FILE_EXT
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.VaultExportHeaderCheck
import org.fossify.gallery.helpers.checkVaultExportTarget
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VaultExportTargetCheckTest {

    private fun tempDir(): File = Files.createTempDirectory("vault-export-check").toFile()

    @Test
    fun emptyDestinationIsSafe() {
        val source = tempDir().apply { File(this, VAULT_HEADER_FILE).writeBytes(byteArrayOf(1, 2, 3)) }
        val dest = tempDir()
        assertEquals(VaultExportHeaderCheck.SAFE, checkVaultExportTarget(source, dest))
    }

    @Test
    fun identicalHeaderIsSafe() {
        val header = byteArrayOf(1, 2, 3)
        val source = tempDir().apply { File(this, VAULT_HEADER_FILE).writeBytes(header) }
        val dest = tempDir().apply { File(this, VAULT_HEADER_FILE).writeBytes(header) }
        assertEquals(VaultExportHeaderCheck.SAFE, checkVaultExportTarget(source, dest))
    }

    @Test
    fun differentHeaderIsBlocked() {
        val source = tempDir().apply { File(this, VAULT_HEADER_FILE).writeBytes(byteArrayOf(1, 2, 3)) }
        val dest = tempDir().apply { File(this, VAULT_HEADER_FILE).writeBytes(byteArrayOf(9, 8, 7)) }
        assertEquals(VaultExportHeaderCheck.DIFFERENT_VAULT, checkVaultExportTarget(source, dest))
    }

    @Test
    fun encryptedFilesWithoutHeaderAreBlocked() {
        val source = tempDir().apply { File(this, VAULT_HEADER_FILE).writeBytes(byteArrayOf(1, 2, 3)) }
        val dest = tempDir().apply { File(this, "file$ENCRYPTED_FILE_EXT").writeBytes(byteArrayOf(4, 5)) }
        assertEquals(VaultExportHeaderCheck.UNKNOWN_ENCRYPTED_CONTENT, checkVaultExportTarget(source, dest))
    }
}
