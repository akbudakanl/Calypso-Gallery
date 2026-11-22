package org.fossify.gallery

import org.fossify.gallery.helpers.CompactDialogSizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TabletLayoutAndDialogSizingTest {

    private fun locateAppFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.first { it.exists() }
    }

    private fun appRoot(): File = locateAppFile("src/main/AndroidManifest.xml").parentFile!!.parentFile!!

    private fun parseIntResources(relativePath: String): Map<String, Int> {
        val file = locateAppFile(relativePath)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("integer")
        val map = LinkedHashMap<String, Int>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            map[el.getAttribute("name")] = el.textContent.trim().toInt()
        }
        return map
    }

    private fun parseCompactDialogDimens(relativePath: String): Map<String, Double> {
        val file = locateAppFile(relativePath)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("dimen")
        val map = LinkedHashMap<String, Double>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            if (name.startsWith("compact_dialog_")) {
                val numeric = el.textContent.trim().removeSuffix("dp").removeSuffix("sp").toDouble()
                map[name] = numeric
            }
        }
        return map
    }

    @Test
    fun tabletGridDefaultsUsePortrait4Albums5MediaAndLandscape5Albums7Media() {
        val phone = parseIntResources("src/main/res/values/integers.xml")
        assertEquals(2, phone["directory_columns_vertical_scroll"])
        assertEquals(3, phone["media_columns_vertical_scroll"])

        val tabletPortrait = parseIntResources("src/main/res/values-w600dp/integers.xml")
        assertEquals(4, tabletPortrait["directory_columns_vertical_scroll"])
        assertEquals(5, tabletPortrait["media_columns_vertical_scroll"])

        val tabletLandscape = parseIntResources("src/main/res/values-w900dp-land/integers.xml")
        assertEquals(5, tabletLandscape["directory_columns_vertical_scroll"])
        assertEquals(7, tabletLandscape["media_columns_vertical_scroll"])

        // Unoriented values-w900dp must not exist, otherwise large tablets (w >= 900dp in portrait)
        // would stay at 5 albums / 7 media items in portrait mode instead of dropping to 4 / 5.
        val unorientedW900 = File(appRoot(), "src/main/res/values-w900dp/integers.xml")
        assertFalse(
            "values-w900dp/integers.xml must be landscape-qualified (values-w900dp-land)",
            unorientedW900.exists()
        )
    }

    @Test
    fun gridActivitiesOverrideOnConfigurationChangedForLiveColumnUpdates() {
        val activities = listOf(
            "src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt",
            "src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt",
            "src/main/kotlin/org/fossify/gallery/activities/SearchActivity.kt",
        )
        activities.forEach { path ->
            val source = locateAppFile(path).readText()
            assertTrue(
                "$path must override onConfigurationChanged to refresh spanCount on rotation",
                source.contains("override fun onConfigurationChanged(newConfig: Configuration)")
            )
        }
    }

    @Test
    fun compactDialogSizingScalesOnTabletsAndUnfoldedFoldablesWhilePreservingPhones() {
        // Phone portrait (411dp x 891dp @ 3x) with 330dp baseline
        val phoneDensity = 3f
        val phoneWidth = CompactDialogSizing.computeWidthPx(
            maxWidthPx = (330f * phoneDensity).toInt(),
            widthPixels = (411f * phoneDensity).toInt(),
            density = phoneDensity
        )
        assertEquals((330f * phoneDensity).toInt(), phoneWidth)

        // Very narrow phone (320dp @ 2x): clamps to widthPixels - 32dp = 288dp
        val narrowPhoneWidth = CompactDialogSizing.computeWidthPx(
            maxWidthPx = (330f * 2f).toInt(),
            widthPixels = (320f * 2f).toInt(),
            density = 2f
        )
        assertEquals((288f * 2f).toInt(), narrowPhoneWidth)

        // Tablet portrait (800dp x 1280dp @ 2x) with 480dp sw600dp cap
        val tabletPortraitWidth = CompactDialogSizing.computeWidthPx(
            maxWidthPx = (480f * 2f).toInt(),
            widthPixels = (800f * 2f).toInt(),
            density = 2f
        )
        assertEquals((480f * 2f).toInt(), tabletPortraitWidth)

        // Unfolded dual-screen foldable (673dp @ 2.5x) with 480dp sw600dp cap
        val foldableWidth = CompactDialogSizing.computeWidthPx(
            maxWidthPx = (480f * 2.5f).toInt(),
            widthPixels = (673f * 2.5f).toInt(),
            density = 2.5f
        )
        assertEquals((480f * 2.5f).toInt(), foldableWidth)

        // Scroll viewport cap protects landscape phones while allowing full height in portrait & tablets
        assertEquals(
            (520f * 2f).toInt(),
            CompactDialogSizing.computeScrollMaxHeightPx((520f * 2f).toInt(), (1280f * 2f).toInt())
        )
        assertEquals(
            ((411f * 3f) * 0.55f).toInt(),
            CompactDialogSizing.computeScrollMaxHeightPx((360f * 3f).toInt(), (411f * 3f).toInt())
        )
    }

    @Test
    fun sw600dpCompactDialogDimensMatchAndStrictlyEnlargePhoneBaselines() {
        val phoneDimens = parseCompactDialogDimens("src/main/res/values/dimens.xml")
        val tabletDimens = parseCompactDialogDimens("src/main/res/values-sw600dp/dimens.xml")

        assertTrue("Expected compact_dialog_* dimens in values/dimens.xml", phoneDimens.isNotEmpty())
        assertEquals(
            "values/dimens.xml and values-sw600dp/dimens.xml must define the exact same compact_dialog_* keys",
            phoneDimens.keys,
            tabletDimens.keys
        )

        phoneDimens.forEach { (key, phoneVal) ->
            val tabletVal = requireNotNull(tabletDimens[key])
            assertTrue(
                "Expected sw600dp $key ($tabletVal) > phone baseline ($phoneVal)",
                tabletVal > phoneVal
            )
        }
    }

    @Test
    fun noDialogHardcodes330dpPhoneWidthCap() {
        val dialogsDir = File(appRoot(), "src/main/kotlin/org/fossify/gallery/dialogs")
        val offenders = dialogsDir.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("dp(330f)") }
            .map { it.name }

        assertEquals(
            "All compact dialogs must use CompactDialogSizing instead of hardcoding dp(330f)",
            emptyList<String>(),
            offenders
        )
    }
}
