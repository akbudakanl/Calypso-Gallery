package org.fossify.gallery

import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.helpers.CopyMovePolicy
import org.fossify.gallery.helpers.PickDirectoryMode
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_PORTRAITS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.Directory
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

    @Test
    fun decryptMoveMatchesUnencryptedCopyMoveForHiddenFoldersVisibility() {
        val modes = listOf(
            PickDirectoryMode.UNENCRYPTED_COPY,
            PickDirectoryMode.UNENCRYPTED_MOVE,
            PickDirectoryMode.DECRYPT_MOVE
        )
        for (mode in modes) {
            // Vault session open (temporarilyShowHidden=true) alone must NOT expose .nomedia folders
            assertFalse(
                CopyMovePolicy.shouldIncludeUnencryptedHiddenFoldersInPicker(
                    mode = mode,
                    temporarilyShowHiddenFolders = false,
                    showHiddenMedia = false,
                    temporarilyShowHidden = true,
                    isVaultUnlocked = true,
                    isHiddenPasswordProtectionOn = true,
                    forceShowHiddenAndExcluded = false
                )
            )
            // When "Temporarily show hidden folders" is active, .nomedia folders ARE shown
            assertTrue(
                CopyMovePolicy.shouldIncludeUnencryptedHiddenFoldersInPicker(
                    mode = mode,
                    temporarilyShowHiddenFolders = true,
                    showHiddenMedia = false,
                    temporarilyShowHidden = true,
                    isVaultUnlocked = true,
                    isHiddenPasswordProtectionOn = true,
                    forceShowHiddenAndExcluded = false
                )
            )
            assertTrue(
                CopyMovePolicy.shouldIncludeUnencryptedHiddenFoldersInPicker(
                    mode = mode,
                    temporarilyShowHiddenFolders = true,
                    showHiddenMedia = false,
                    temporarilyShowHidden = false,
                    isVaultUnlocked = false,
                    isHiddenPasswordProtectionOn = true,
                    forceShowHiddenAndExcluded = false
                )
            )
        }
    }

    @Test
    fun pickerHiddenMediaFollowsShowHiddenMediaOutsideDefaultMode() {
        assertTrue(
            CopyMovePolicy.shouldShowHiddenMediaInPicker(
                mode = PickDirectoryMode.DEFAULT,
                showHiddenMedia = false,
                shouldShowHidden = true
            )
        )
        assertFalse(
            CopyMovePolicy.shouldShowHiddenMediaInPicker(
                mode = PickDirectoryMode.DECRYPT_MOVE,
                showHiddenMedia = false,
                shouldShowHidden = true
            )
        )
        assertFalse(
            CopyMovePolicy.shouldShowHiddenMediaInPicker(
                mode = PickDirectoryMode.UNENCRYPTED_COPY,
                showHiddenMedia = false,
                shouldShowHidden = true
            )
        )
        assertTrue(
            CopyMovePolicy.shouldShowHiddenMediaInPicker(
                mode = PickDirectoryMode.DECRYPT_MOVE,
                showHiddenMedia = true,
                shouldShowHidden = true
            )
        )
    }

    @Test
    fun isEncryptedVaultFileNameMatchesVaultItemsAndExcludesTempStaging() {
        assertTrue(CopyMovePolicy.isEncryptedVaultFileName("a1b2c3d4.bin"))
        assertTrue(CopyMovePolicy.isEncryptedVaultFileName("ABCDEF01.BIN"))
        assertTrue(CopyMovePolicy.isEncryptedVaultFileName(".fv_abc.fenc"))

        assertFalse(CopyMovePolicy.isEncryptedVaultFileName(".tmp_12345678.bin"))
        assertFalse(CopyMovePolicy.isEncryptedVaultFileName(".sync_tmp_12345678.bin"))
        assertFalse(CopyMovePolicy.isEncryptedVaultFileName(".meta"))
        assertFalse(CopyMovePolicy.isEncryptedVaultFileName("photo.jpg"))
    }

    @Test
    fun shouldCollectFolderEntryIgnoresBinAndFencInUnencryptedFolders() {
        val allMediaFilter = TYPE_IMAGES or TYPE_VIDEOS or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS

        assertTrue(collect(fileName = "photo.jpg", isEncryptedFolder = false, showHidden = false, filterMedia = allMediaFilter))
        assertTrue(collect(fileName = "clip.mp4", isEncryptedFolder = false, showHidden = false, filterMedia = allMediaFilter))
        assertTrue(collect(fileName = "anim.gif", isEncryptedFolder = false, showHidden = false, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = ".photo.jpg", isEncryptedFolder = false, showHidden = false, filterMedia = allMediaFilter))
        assertTrue(collect(fileName = ".photo.jpg", isEncryptedFolder = false, showHidden = true, filterMedia = allMediaFilter))

        assertFalse(collect(fileName = "download.bin", isEncryptedFolder = false, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = "UPPER.BIN", isEncryptedFolder = false, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = ".hidden.bin", isEncryptedFolder = false, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = "legacy.fenc", isEncryptedFolder = false, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = "subfolder", isDirectory = true, isEncryptedFolder = false, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = "clip.mp4", isEncryptedFolder = false, showHidden = false, filterMedia = TYPE_IMAGES))
    }

    @Test
    fun shouldCollectFolderEntryCollectsOnlyVaultEntriesInEncryptedFolders() {
        val allMediaFilter = TYPE_IMAGES or TYPE_VIDEOS or TYPE_GIFS or TYPE_RAWS or TYPE_SVGS

        assertTrue(collect(fileName = "a1b2c3d4.bin", isEncryptedFolder = true, showHidden = false, filterMedia = allMediaFilter))
        assertTrue(collect(fileName = "ABCDEF01.BIN", isEncryptedFolder = true, showHidden = false, filterMedia = allMediaFilter))
        assertTrue(collect(fileName = ".fv_abc.fenc", isEncryptedFolder = true, showHidden = false, filterMedia = allMediaFilter))

        assertFalse(collect(fileName = ".tmp_12345678.bin", isEncryptedFolder = true, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = ".sync_tmp_12345678.bin", isEncryptedFolder = true, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = ".meta", isEncryptedFolder = true, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = ".nomedia", isEncryptedFolder = true, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = "photo.jpg", isEncryptedFolder = true, showHidden = true, filterMedia = allMediaFilter))
        assertFalse(collect(fileName = "sub", isDirectory = true, isEncryptedFolder = true, showHidden = true, filterMedia = allMediaFilter))
    }

    @Test
    fun shouldIgnoreMediaFilterForPickerOnlyForCopyMoveDestinations() {
        assertTrue(CopyMovePolicy.shouldIgnoreMediaFilterForPicker(isPickingCopyMoveDestination = true))
        assertFalse(CopyMovePolicy.shouldIgnoreMediaFilterForPicker(isPickingCopyMoveDestination = false))
    }

    @Test
    fun allMediaTypesFilterIncludesAllGalleryMediaTypes() {
        val allTypes = CopyMovePolicy.allMediaTypesFilter()
        assertTrue(allTypes and TYPE_IMAGES != 0)
        assertTrue(allTypes and TYPE_VIDEOS != 0)
        assertTrue(allTypes and TYPE_GIFS != 0)
        assertTrue(allTypes and TYPE_RAWS != 0)
        assertTrue(allTypes and TYPE_SVGS != 0)
        assertTrue(allTypes and TYPE_PORTRAITS != 0)
    }

    @Test
    fun mergeDestinationDirectoriesPopulatesEmptyCacheFromLiveScan() {
        val live = listOf(
            dir("/storage/emulated/0/DCIM/Camera", mediaCnt = 12),
            dir("/storage/emulated/0/Pictures/Screenshots", mediaCnt = 4)
        )

        val merged = CopyMovePolicy.mergeDestinationDirectories(emptyList(), live)
        assertEquals(2, merged.size)
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Pictures/Screenshots"), merged.map { it.path })
    }

    @Test
    fun mergeDestinationDirectoriesDeduplicatesCaseInsensitivelyAndKeepsCachedOnly() {
        val cached = listOf(
            dir("/storage/emulated/0/DCIM/Camera", mediaCnt = 10, tmb = "/old.jpg"),
            dir("/storage/emulated/0/HiddenNoMedia", mediaCnt = 3)
        )
        val live = listOf(
            dir("/storage/emulated/0/dcim/camera/", mediaCnt = 15, tmb = "/new.jpg"),
            dir("/storage/emulated/0/Download", mediaCnt = 7)
        )

        val merged = CopyMovePolicy.mergeDestinationDirectories(cached, live)
        assertEquals(3, merged.size)
        assertEquals(15, merged[0].mediaCnt)
        assertEquals("/new.jpg", merged[0].tmb)
        assertEquals("/storage/emulated/0/HiddenNoMedia", merged[1].path)
        assertEquals("/storage/emulated/0/Download", merged[2].path)
    }

    @Test
    fun findUnpersistedDirectoriesReturnsOnlyMissingRows() {
        val displayed = listOf(
            dir("/storage/emulated/0/DCIM/Camera", mediaCnt = 10),
            dir("/storage/emulated/0/Pictures", mediaCnt = 5),
            dir("/storage/emulated/0/Pictures/", mediaCnt = 5)
        )
        val cached = listOf(
            dir("/storage/emulated/0/dcim/camera", mediaCnt = 9)
        )

        val unpersisted = CopyMovePolicy.findUnpersistedDirectories(displayed, cached)
        assertEquals(1, unpersisted.size)
        assertEquals("/storage/emulated/0/Pictures", unpersisted[0].path)
    }

    @Test
    fun resolveVirtualAlbumCopyPhysicalSourceAllowsMixedAndOtherFolders() {
        val camera = "/storage/emulated/0/DCIM/Camera"
        val screenshots = "/storage/emulated/0/Pictures/Screenshots"
        val downloads = "/storage/emulated/0/Download"

        // All items already reside in destination -> returns same folder to trigger source_and_destination_same
        assertEquals(
            camera,
            CopyMovePolicy.resolveVirtualAlbumCopyPhysicalSource(
                itemParentPaths = listOf(camera, "$camera/"),
                destinationPath = camera,
                fallbackSource = virtualAlbum
            )
        )

        // Single or mixed items where at least one is outside destination -> returns non-destination parent
        assertEquals(
            screenshots,
            CopyMovePolicy.resolveVirtualAlbumCopyPhysicalSource(
                itemParentPaths = listOf(camera, screenshots),
                destinationPath = camera,
                fallbackSource = virtualAlbum
            )
        )
        assertEquals(
            screenshots,
            CopyMovePolicy.resolveVirtualAlbumCopyPhysicalSource(
                itemParentPaths = listOf(screenshots),
                destinationPath = downloads,
                fallbackSource = virtualAlbum
            )
        )

        // Empty list falls back cleanly
        assertEquals(
            virtualAlbum,
            CopyMovePolicy.resolveVirtualAlbumCopyPhysicalSource(
                itemParentPaths = emptyList(),
                destinationPath = camera,
                fallbackSource = virtualAlbum
            )
        )
    }

    private fun dir(path: String, mediaCnt: Int = 1, tmb: String = ""): Directory {
        return Directory(
            id = null,
            path = path,
            tmb = tmb,
            name = path.trimEnd('/').substringAfterLast('/'),
            mediaCnt = mediaCnt,
            modified = 0L,
            taken = 0L,
            size = 0L,
            location = 0,
            types = TYPE_IMAGES,
            sortValue = ""
        )
    }

    private fun collect(
        fileName: String,
        isDirectory: Boolean = false,
        isEncryptedFolder: Boolean,
        showHidden: Boolean,
        filterMedia: Int
    ): Boolean {
        return CopyMovePolicy.shouldCollectFolderEntry(
            fileName = fileName,
            isDirectory = isDirectory,
            isEncryptedFolder = isEncryptedFolder,
            showHidden = showHidden,
            filterMedia = filterMedia
        )
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
