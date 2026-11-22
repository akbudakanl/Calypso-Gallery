package org.fossify.gallery

import org.fossify.gallery.helpers.CalypsoActionModeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class CalypsoActionModeMenuTest {

    private data class ParsedMenuItem(
        val idName: String,
        val appShowAsAction: String,
        val androidShowAsAction: String,
        val hasIcon: Boolean
    )

    private fun parseTopLevelMenuItems(relativePath: String): List<ParsedMenuItem> {
        val candidates = listOf(
            File(relativePath),
            File("app/src/main/res/menu/$relativePath"),
            File("../app/src/main/res/menu/$relativePath")
        )
        val xmlFile = candidates.first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val root = doc.documentElement
        val children = root.childNodes
        val result = ArrayList<ParsedMenuItem>()
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "item") {
                val rawId = node.getAttribute("android:id").substringAfterLast("/")
                result.add(
                    ParsedMenuItem(
                        idName = rawId,
                        appShowAsAction = node.getAttribute("app:showAsAction"),
                        androidShowAsAction = node.getAttribute("android:showAsAction"),
                        hasIcon = node.getAttribute("android:icon").isNotEmpty()
                    )
                )
            }
        }
        return result
    }

    @Test
    fun mediaMenuKeepsOnlyDeleteAndShareOnBarAndPlacesPropertiesFirstInMoreOptions() {
        val items = parseTopLevelMenuItems("cab_media.xml")
        val alwaysIds = items.filter { it.appShowAsAction == "always" }.map { it.idName }

        // Only confirm_selection (picker intent), delete, and share can appear on the bar.
        assertEquals(listOf("cab_confirm_selection", "cab_delete", "cab_share"), alwaysIds)

        // Every overflow item must be explicitly "never" so encrypted selection (where share is hidden)
        // never promotes properties or any other overflow item onto the bar.
        val overflowItems = items.filter { it.appShowAsAction != "always" }
        assertTrue(overflowItems.isNotEmpty())
        assertTrue(overflowItems.all { it.appShowAsAction == "never" && it.androidShowAsAction == "never" })

        // Properties must be the very first item inside More options.
        assertEquals("cab_properties", overflowItems.first().idName)

        // All top-level items have icons for the Tasarım A More options card.
        assertTrue(items.all { it.hasIcon })
    }

    @Test
    fun directoryMenuRemovesPropertiesFromBarAndPlacesItFirstInMoreOptions() {
        val items = parseTopLevelMenuItems("cab_directories.xml")
        val alwaysIds = items.filter { it.appShowAsAction == "always" }.map { it.idName }

        // Album bar actions: Delete and Pin/Unpin (Properties moved out of bar).
        assertEquals(listOf("cab_delete", "cab_pin", "cab_unpin"), alwaysIds)
        assertFalse(alwaysIds.contains("cab_properties"))

        // Excluding the drag-reorder-only move_to_top / move_to_bottom items, all overflow items are "never"
        // and Properties is the very first item in More options.
        val overflowItems = items.filter {
            it.appShowAsAction != "always" &&
                it.idName != "cab_move_to_top" &&
                it.idName != "cab_move_to_bottom"
        }
        assertTrue(overflowItems.isNotEmpty())
        assertTrue(overflowItems.all { it.appShowAsAction == "never" && it.androidShowAsAction == "never" })
        assertEquals("cab_properties", overflowItems.first().idName)
        assertTrue(items.all { it.hasIcon })
    }

    @Test
    fun controllerBarActionIdSetsMatchSpecification() {
        assertTrue(R.id.cab_delete in CalypsoActionModeController.MEDIA_BAR_ACTION_IDS)
        assertTrue(R.id.cab_share in CalypsoActionModeController.MEDIA_BAR_ACTION_IDS)
        assertFalse(R.id.cab_properties in CalypsoActionModeController.MEDIA_BAR_ACTION_IDS)

        assertTrue(R.id.cab_delete in CalypsoActionModeController.DIRECTORY_BAR_ACTION_IDS)
        assertTrue(R.id.cab_pin in CalypsoActionModeController.DIRECTORY_BAR_ACTION_IDS)
        assertTrue(R.id.cab_unpin in CalypsoActionModeController.DIRECTORY_BAR_ACTION_IDS)
        assertFalse(R.id.cab_properties in CalypsoActionModeController.DIRECTORY_BAR_ACTION_IDS)
    }
}
