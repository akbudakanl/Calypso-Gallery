package org.fossify.gallery

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.helpers.CopyMovePolicy
import org.fossify.gallery.helpers.PickDirectoryMode
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyMovePolicyTest {
    private val realAlbum = "/storage/emulated/0/DCIM/Camera"
    private val encryptedRealAlbum = "/storage/emulated/0/Calypso Gallery/Secrets/a1b2c3"
    private val virtualAlbum = "virtual_album_7"

    @Test
    fun moveToAllowedOnlyForRealAlbums() {
        assertTrue(CopyMovePolicy.canMoveFrom(realAlbum))
        assertTrue(CopyMovePolicy.canMoveFrom(encryptedRealAlbum))
        assertTrue(CopyMovePolicy.canMoveFrom(""))

        assertFalse(CopyMovePolicy.canMoveFrom(virtualAlbum))
        assertFalse(CopyMovePolicy.canMoveFrom(FAVORITES))
        assertFalse(CopyMovePolicy.canMoveFrom(RECYCLE_BIN))
        assertFalse(CopyMovePolicy.canMoveFrom(realAlbum, isInRecycleBin = true))
        assertFalse(CopyMovePolicy.canMoveFrom(encryptedRealAlbum, isVaultRecycleBin = true))
    }

    @Test
    fun encryptAllowedOnlyForUnencryptedRealAlbums() {
        assertTrue(CopyMovePolicy.canEncryptFrom(realAlbum, hasEncryptedSelection = false))
        assertFalse(CopyMovePolicy.canEncryptFrom(encryptedRealAlbum, hasEncryptedSelection = true))
        assertFalse(CopyMovePolicy.canEncryptFrom(virtualAlbum, hasEncryptedSelection = false))
        assertFalse(CopyMovePolicy.canEncryptFrom(FAVORITES, hasEncryptedSelection = false))
        assertFalse(CopyMovePolicy.canEncryptFrom(RECYCLE_BIN, hasEncryptedSelection = false))
    }

    @Test
    fun decryptAllowedOnlyForRealAlbums() {
        assertTrue(CopyMovePolicy.canDecryptFrom(encryptedRealAlbum, hasHiddenOrEncryptedSelection = true))
        assertTrue(CopyMovePolicy.canDecryptFrom(realAlbum, hasHiddenOrEncryptedSelection = true))
        assertFalse(CopyMovePolicy.canDecryptFrom(encryptedRealAlbum, hasHiddenOrEncryptedSelection = false))
        assertFalse(CopyMovePolicy.canDecryptFrom(virtualAlbum, hasHiddenOrEncryptedSelection = true))
        assertFalse(CopyMovePolicy.canDecryptFrom(FAVORITES, hasHiddenOrEncryptedSelection = true))
        assertFalse(CopyMovePolicy.canDecryptFrom(RECYCLE_BIN, hasHiddenOrEncryptedSelection = true))
        assertFalse(CopyMovePolicy.canDecryptFrom(encryptedRealAlbum, hasHiddenOrEncryptedSelection = true, isVaultRecycleBin = true))
    }

    @Test
    fun resolveCopyMoveModeDistinguishesEncryptedAndUnencryptedSources() {
        assertEquals(PickDirectoryMode.UNENCRYPTED_COPY, CopyMovePolicy.resolveCopyMoveMode(isCopyOperation = true, isEncryptedSource = false))
        assertEquals(PickDirectoryMode.UNENCRYPTED_MOVE, CopyMovePolicy.resolveCopyMoveMode(isCopyOperation = false, isEncryptedSource = false))
        assertEquals(PickDirectoryMode.ENCRYPTED_COPY, CopyMovePolicy.resolveCopyMoveMode(isCopyOperation = true, isEncryptedSource = true))
        assertEquals(PickDirectoryMode.ENCRYPTED_MOVE, CopyMovePolicy.resolveCopyMoveMode(isCopyOperation = false, isEncryptedSource = true))
    }

    @Test
    fun unencryptedCopyAllowsOnlyUnencryptedRealAlbums() {
        assertTrue(allowed(PickDirectoryMode.UNENCRYPTED_COPY, isVirtual = false, isEncrypted = false))

        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_COPY, isVirtual = true, isEncrypted = false))
        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_COPY, isVirtual = false, isEncrypted = true))
        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_COPY, isVirtual = true, isEncrypted = true))
        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_COPY, isVirtual = false, isEncrypted = false, isRecycleBin = true))
        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_COPY, isVirtual = false, isEncrypted = false, isFavorites = true))
    }

    @Test
    fun unencryptedMoveAllowsOnlyUnencryptedRealAlbums() {
        assertTrue(allowed(PickDirectoryMode.UNENCRYPTED_MOVE, isVirtual = false, isEncrypted = false))

        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_MOVE, isVirtual = true, isEncrypted = false))
        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_MOVE, isVirtual = false, isEncrypted = true))
        assertFalse(allowed(PickDirectoryMode.UNENCRYPTED_MOVE, isVirtual = true, isEncrypted = true))
    }

    @Test
    fun encryptedCopyAllowsOnlyEncryptedRealAlbums() {
        assertTrue(allowed(PickDirectoryMode.ENCRYPTED_COPY, isVirtual = false, isEncrypted = true))

        assertFalse(allowed(PickDirectoryMode.ENCRYPTED_COPY, isVirtual = true, isEncrypted = true))
        assertFalse(allowed(PickDirectoryMode.ENCRYPTED_COPY, isVirtual = false, isEncrypted = false))
        assertFalse(allowed(PickDirectoryMode.ENCRYPTED_COPY, isVirtual = true, isEncrypted = false))
    }

    @Test
    fun addToVirtualAlbumAllowedExceptMixedEncryptionAndRecycleBin() {
        assertTrue(CopyMovePolicy.canAddToVirtualAlbum(realAlbum))
        assertTrue(CopyMovePolicy.canAddToVirtualAlbum(encryptedRealAlbum))
        assertTrue(CopyMovePolicy.canAddToVirtualAlbum(virtualAlbum))
        assertTrue(CopyMovePolicy.canAddToVirtualAlbum(FAVORITES))
        assertTrue(CopyMovePolicy.canAddToVirtualAlbum(""))

        assertFalse(CopyMovePolicy.canAddToVirtualAlbum(realAlbum, isMixedEncryption = true))
        assertFalse(CopyMovePolicy.canAddToVirtualAlbum(RECYCLE_BIN))
        assertFalse(CopyMovePolicy.canAddToVirtualAlbum(realAlbum, isInRecycleBin = true))
        assertFalse(CopyMovePolicy.canAddToVirtualAlbum(encryptedRealAlbum, isVaultRecycleBin = true))
    }

    @Test
    fun encryptedMoveAllowsOnlyEncryptedRealAlbums() {
        assertTrue(allowed(PickDirectoryMode.ENCRYPTED_MOVE, isVirtual = false, isEncrypted = true))

        assertFalse(allowed(PickDirectoryMode.ENCRYPTED_MOVE, isVirtual = true, isEncrypted = true))
        assertFalse(allowed(PickDirectoryMode.ENCRYPTED_MOVE, isVirtual = false, isEncrypted = false))
        assertFalse(allowed(PickDirectoryMode.ENCRYPTED_MOVE, isVirtual = true, isEncrypted = false))
    }

    @Test
    fun decryptMoveAllowsOnlyUnencryptedRealAlbums() {
        assertTrue(allowed(PickDirectoryMode.DECRYPT_MOVE, isVirtual = false, isEncrypted = false))

        assertFalse(allowed(PickDirectoryMode.DECRYPT_MOVE, isVirtual = true, isEncrypted = false))
        assertFalse(allowed(PickDirectoryMode.DECRYPT_MOVE, isVirtual = false, isEncrypted = true))
        assertFalse(allowed(PickDirectoryMode.DECRYPT_MOVE, isVirtual = true, isEncrypted = true))
        assertFalse(allowed(PickDirectoryMode.DECRYPT_MOVE, isVirtual = false, isEncrypted = false, isRecycleBin = true))
        assertFalse(allowed(PickDirectoryMode.DECRYPT_MOVE, isVirtual = false, isEncrypted = false, isFavorites = true))
    }

    private fun allowed(
        mode: PickDirectoryMode,
        isVirtual: Boolean,
        isEncrypted: Boolean,
        isRecycleBin: Boolean = false,
        isFavorites: Boolean = false
    ): Boolean {
        return CopyMovePolicy.isAllowedDestination(
            mode = mode,
            isVirtualAlbum = isVirtual,
            isEncryptedAlbum = isEncrypted,
            isRecycleBin = isRecycleBin,
            isFavorites = isFavorites
        )
    }
}
