package org.fossify.gallery

import android.content.pm.ActivityInfo
import org.fossify.gallery.activities.OnboardingActivity
import org.fossify.gallery.dialogs.AppSecurityDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class OnboardingAndSecurityRotationTest {

    private fun locateAppFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.first { it.exists() }
    }

    @Test
    fun onboardingLocksPortraitOnCompactPhonesAndUnlocksOnTabletsAndUnfoldedFoldables() {
        // Standard phones and folded dual-screen outer displays (< 600dp smallest width)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            OnboardingActivity.resolveRequestedOrientation(320)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            OnboardingActivity.resolveRequestedOrientation(392)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            OnboardingActivity.resolveRequestedOrientation(411)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            OnboardingActivity.resolveRequestedOrientation(599)
        )

        // Unfolded dual-screen foldables (~673dp) and tablets (600dp+)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            OnboardingActivity.resolveRequestedOrientation(600)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            OnboardingActivity.resolveRequestedOrientation(673)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            OnboardingActivity.resolveRequestedOrientation(800)
        )
    }

    @Test
    fun onboardingManifestHandlesFoldableScreenSizeTransitionsAndAvoidsStaticOrientationLock() {
        val manifestFile = locateAppFile("src/main/AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        val activities = doc.getElementsByTagName("activity")
        var onboardingElement: Element? = null
        for (i in 0 until activities.length) {
            val el = activities.item(i) as Element
            if (el.getAttribute("android:name") == ".activities.OnboardingActivity") {
                onboardingElement = el
                break
            }
        }
        val onboarding = requireNotNull(onboardingElement) { "OnboardingActivity not found in AndroidManifest.xml" }

        // Must not hardcode android:screenOrientation="portrait" in manifest
        assertEquals("", onboarding.getAttribute("android:screenOrientation"))

        val configChanges = onboarding.getAttribute("android:configChanges")
            .split("|")
            .map { it.trim() }
            .toSet()
        assertTrue(configChanges.contains("orientation"))
        assertTrue(configChanges.contains("screenSize"))
        assertTrue(configChanges.contains("smallestScreenSize"))
        assertTrue(configChanges.contains("screenLayout"))
    }

    @Test
    fun bothOnboardingScreensProvideTwoColumnTabletLandscapeLayoutsWithMatchingViewIds() {
        val welcomePortraitIds = collectViewIds(locateAppFile("src/main/res/layout/layout_onboarding_welcome.xml"))
        val welcomeTabletLandIds = collectViewIds(locateAppFile("src/main/res/layout-sw600dp-land/layout_onboarding_welcome.xml"))
        assertEquals(welcomePortraitIds, welcomeTabletLandIds)

        val allFilesPortraitIds = collectViewIds(locateAppFile("src/main/res/layout/layout_onboarding_all_files.xml"))
        val allFilesTabletLandIds = collectViewIds(locateAppFile("src/main/res/layout-sw600dp-land/layout_onboarding_all_files.xml"))
        assertEquals(allFilesPortraitIds, allFilesTabletLandIds)
    }

    @Test
    fun appSecurityDialogRecomputesFullScrollHeightWhenRotatedFromLandscapeToPortrait() {
        val density = 3f // 411dp x 891dp phone at 3x density (1233 x 2673 px)
        val shortEdgePx = (411f * density).toInt()
        val longEdgePx = (891f * density).toInt()

        // Opened while phone is in landscape (width = 891dp, height = 411dp)
        val (landWidth, landHeight) = AppSecurityDialog.computeDialogDimensions(
            widthPixels = longEdgePx,
            heightPixels = shortEdgePx,
            density = density
        )
        assertEquals((440f * density).toInt(), landWidth)
        assertEquals(((411f - 196f) * density).toInt(), landHeight)

        // Rotated to portrait (width = 411dp, height = 891dp): height must expand to full 480dp
        // so the PIN keypad / pattern lock and its confirm button are never clipped.
        val (portWidth, portHeight) = AppSecurityDialog.computeDialogDimensions(
            widthPixels = shortEdgePx,
            heightPixels = longEdgePx,
            density = density
        )
        assertEquals(((411f - 32f) * density).toInt(), portWidth)
        assertEquals((480f * density).toInt(), portHeight)
        assertTrue("Portrait content height ($portHeight) must exceed landscape height ($landHeight)", portHeight > landHeight)

        // Tablet portrait & landscape (800dp x 1280dp): both orientations get the full 440dp x 480dp card
        val (tabPortW, tabPortH) = AppSecurityDialog.computeDialogDimensions(
            widthPixels = (800f * 2f).toInt(),
            heightPixels = (1280f * 2f).toInt(),
            density = 2f
        )
        val (tabLandW, tabLandH) = AppSecurityDialog.computeDialogDimensions(
            widthPixels = (1280f * 2f).toInt(),
            heightPixels = (800f * 2f).toInt(),
            density = 2f
        )
        assertEquals((440f * 2f).toInt(), tabPortW)
        assertEquals((480f * 2f).toInt(), tabPortH)
        assertEquals((440f * 2f).toInt(), tabLandW)
        assertEquals((480f * 2f).toInt(), tabLandH)
    }

    private fun collectViewIds(xmlFile: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val allNodes = doc.getElementsByTagName("*")
        val ids = LinkedHashSet<String>()
        for (i in 0 until allNodes.length) {
            val el = allNodes.item(i) as? Element ?: continue
            val rawId = el.getAttribute("android:id")
            if (rawId.isNotEmpty()) {
                ids.add(rawId.substringAfterLast("/"))
            }
        }
        return ids
    }
}
