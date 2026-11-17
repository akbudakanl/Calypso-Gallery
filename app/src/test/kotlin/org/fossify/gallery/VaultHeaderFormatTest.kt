package org.fossify.gallery

import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.readVaultHeaderVersion
import org.fossify.gallery.helpers.writeVaultHeader
import org.fossify.gallery.helpers.vault.VaultContainer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** `.vault` başlığının kanonik format versiyonunu yazdığını doğrular. */
class VaultHeaderFormatTest {

    @Test
    fun writtenHeaderUsesCanonicalFormatVersion() {
        val header = File(System.getProperty("java.io.tmpdir"), "calypso_vault_header_test.vault")
        header.deleteOnExit()

        CryptoManager.writeVaultHeader(
            headerFile = header,
            salt = ByteArray(16) { 1 },
            memoryKb = 65_536,
            iterations = 2,
            parallelism = 2,
            ivPdk = ByteArray(12) { 2 },
            wrappedVmK = ByteArray(48) { 3 },
            ivVmk = ByteArray(12) { 4 },
            wrappedKeyring = ByteArray(48) { 5 }
        )

        assertEquals(VaultContainer.VAULT_FORMAT_VERSION.toInt(), readVaultHeaderVersion(header))
    }
}
