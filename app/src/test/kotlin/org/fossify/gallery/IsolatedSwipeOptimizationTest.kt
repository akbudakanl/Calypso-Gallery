package org.fossify.gallery

import androidx.exifinterface.media.ExifInterface
import org.fossify.gallery.helpers.media.IsolatedMetadataCache
import org.fossify.gallery.helpers.media.PhotoSwipeOptimizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IsolatedSwipeOptimizationTest {

    private fun locateRepoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
        )
        return candidates.first { it.exists() }
    }

    @Test
    fun photoSwipeOptimizerClassifiesStaticPathsAndTargetSizes() {
        assertTrue(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/DCIM/photo.jpg"))
        assertTrue(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/DCIM/photo.HEIC"))
        assertTrue(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/DCIM/raw.dng"))

        assertFalse(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/DCIM/anim.gif"))
        assertFalse(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/DCIM/anim.png"))
        assertFalse(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/DCIM/anim.apng"))
        assertFalse(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/DCIM/anim.webp"))
        assertFalse(PhotoSwipeOptimizer.isDefinitelyStaticByPath("content://media/external/images/media/42"))
        assertFalse(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/Vault/secret.bin"))
        assertFalse(PhotoSwipeOptimizer.isDefinitelyStaticByPath("/storage/emulated/0/Vault/secret.fenc"))

        assertEquals(1080 to 2400, PhotoSwipeOptimizer.resolveGlideTargetDimensions(1080, 2400, false))
        assertNull(PhotoSwipeOptimizer.resolveGlideTargetDimensions(1080, 2400, true))
        assertNull(PhotoSwipeOptimizer.resolveGlideTargetDimensions(0, 2400, false))

        assertTrue(
            PhotoSwipeOptimizer.shouldKeepAnimatedPlaying(
                isFragmentVisible = true,
                isResumed = true,
                isPagerScrolling = false,
            ),
        )
        assertTrue(
            PhotoSwipeOptimizer.shouldKeepAnimatedPlaying(
                isFragmentVisible = false,
                isResumed = true,
                isPagerScrolling = true,
            ),
        )
        assertFalse(
            PhotoSwipeOptimizer.shouldKeepAnimatedPlaying(
                isFragmentVisible = false,
                isResumed = true,
                isPagerScrolling = false,
            ),
        )
        assertFalse(
            PhotoSwipeOptimizer.shouldKeepAnimatedPlaying(
                isFragmentVisible = true,
                isResumed = false,
                isPagerScrolling = true,
            ),
        )

        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, PhotoSwipeOptimizer.rotationDegreesToExifOrientation(90))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_180, PhotoSwipeOptimizer.rotationDegreesToExifOrientation(180))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_270, PhotoSwipeOptimizer.rotationDegreesToExifOrientation(270))
        assertEquals(ExifInterface.ORIENTATION_NORMAL, PhotoSwipeOptimizer.rotationDegreesToExifOrientation(0))
    }

    @Test
    fun isolatedMetadataCacheSegregatesVaultEntriesAndClearsOnLock() {
        val publicFile = File("C:/tmp/calypso-public-sample.jpg")
        val vaultFile = File("C:/tmp/calypso-vault-sample.enc")

        IsolatedMetadataCache.clearVault()
        IsolatedMetadataCache.putBounds(publicFile, isVault = false, width = 4000, height = 3000, rotationDegrees = 90)
        IsolatedMetadataCache.putAnimatedHint(publicFile, isVault = false, isAnimated = false)

        IsolatedMetadataCache.putBounds(vaultFile, isVault = true, width = 1920, height = 1080, rotationDegrees = 0)
        IsolatedMetadataCache.putAnimatedHint(vaultFile, isVault = true, isAnimated = true)

        val pub = IsolatedMetadataCache.get(publicFile, isVault = false)
        assertEquals(4000, pub?.width)
        assertEquals(3000, pub?.height)
        assertEquals(90, pub?.rotationDegrees)
        assertEquals(false, pub?.isAnimated)

        val vault = IsolatedMetadataCache.get(vaultFile, isVault = true)
        assertEquals(1920, vault?.width)
        assertEquals(1080, vault?.height)
        assertEquals(true, vault?.isAnimated)

        assertNull(IsolatedMetadataCache.get(publicFile, isVault = true))
        assertNull(IsolatedMetadataCache.get(vaultFile, isVault = false))

        IsolatedMetadataCache.clearVault()
        assertNull(IsolatedMetadataCache.get(vaultFile, isVault = true))
        assertEquals(4000, IsolatedMetadataCache.get(publicFile, isVault = false)?.width)
    }

    @Test
    fun swipeOptimizationSecurityAndLifecycleContractsHold() {
        val encSupportText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedEncryptedDecoderSupport.kt",
        ).readText()
        assertTrue(encSupportText.contains("IsolatedMetadataCache.clearVault()"))
        assertTrue(encSupportText.contains("VaultBitmapPool.clearAndZeroize()"))

        val vaultPoolText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/VaultBitmapPool.kt",
        ).readText()
        assertTrue(vaultPoolText.contains("eraseColor(0)"))

        val photoFragmentText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt",
        ).readText()
        assertTrue(photoFragmentText.contains("override fun onPageScrollStateChanged"))
        assertFalse(photoFragmentText.contains("toBitmapOrNull"))

        val colorModeText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/ColorModeHelper.kt",
        ).readText()
        assertTrue(colorModeText.contains("window.colorMode == colorMode"))
    }
}
