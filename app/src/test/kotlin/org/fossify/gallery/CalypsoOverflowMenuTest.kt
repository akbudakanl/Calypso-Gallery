package org.fossify.gallery

import org.fossify.gallery.helpers.CalypsoOverflowMenuPopup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class CalypsoOverflowMenuTest {

    private data class ParsedMenuItem(
        val idName: String,
        val appShowAsAction: String,
        val hasIcon: Boolean
    )

    private fun parseTopLevelMenuItems(fileName: String): List<ParsedMenuItem> {
        val candidates = listOf(
            File(fileName),
            File("app/src/main/res/menu/$fileName"),
            File("../app/src/main/res/menu/$fileName")
        )
        val xmlFile = candidates.first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val children = doc.documentElement.childNodes
        val result = ArrayList<ParsedMenuItem>()
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "item") {
                result.add(
                    ParsedMenuItem(
                        idName = node.getAttribute("android:id").substringAfterLast("/"),
                        appShowAsAction = node.getAttribute("app:showAsAction"),
                        hasIcon = node.getAttribute("android:icon").isNotEmpty()
                    )
                )
            }
        }
        return result
    }

    @Test
    fun mainMenuFollowsConfirmedOrderAndHasIcons() {
        val items = parseTopLevelMenuItems("menu_main.xml")
        assertEquals(listOf("sort"), items.filter { it.appShowAsAction == "always" }.map { it.idName })

        val overflowIds = items.filter { it.appShowAsAction != "always" }.map { it.idName }
        assertEquals(
            listOf(
                "show_all",
                "refresh_menu",
                "filter",
                "change_view_type",
                "temporarily_show_hidden_folders",
                "stop_showing_hidden_folders",
                "temporarily_show_excluded",
                "stop_showing_excluded",
                "temporarily_show_hidden",
                "stop_showing_hidden",
                "create_new_folder",
                "open_recycle_bin",
                "settings"
            ),
            overflowIds
        )
        assertTrue(items.all { it.hasIcon })
    }

    @Test
    fun mediaMenuFollowsConfirmedOrderForBothAllFilesAndAlbumViews() {
        val items = parseTopLevelMenuItems("menu_media.xml")
        val allIds = items.map { it.idName }

        assertEquals(
            listOf(
                "sort",
                "show_albums",
                "refresh_menu",
                "filter",
                "toggle_filename",
                "change_view_type",
                "group",
                "temporarily_show_hidden_folders",
                "stop_showing_hidden_folders",
                "temporarily_show_excluded",
                "stop_showing_excluded",
                "temporarily_show_hidden",
                "stop_showing_hidden",
                "open_recycle_bin",
                "restore_all_files",
                "empty_recycle_bin",
                "empty_disable_recycle_bin",
                "slideshow",
                "settings"
            ),
            allIds
        )
        assertFalse(allIds.contains("create_new_folder"))
        assertTrue(items.all { it.hasIcon })
    }

    @Test
    fun viewPagerMenuFollowsConfirmedOrderAndRemovesUnnecessaryItems() {
        val items = parseTopLevelMenuItems("menu_viewpager.xml")
        val alwaysIds = items.filter { it.appShowAsAction == "always" }.map { it.idName }
        assertEquals(
            listOf("menu_save_as", "menu_add_to_favorites", "menu_remove_from_favorites"),
            alwaysIds
        )

        val overflowIds = items.filter { it.appShowAsAction != "always" }.map { it.idName }
        assertEquals(
            listOf(
                "menu_shuffle",
                "menu_properties",
                "menu_rename",
                "menu_copy_to_clipboard",
                "menu_unhide",
                "menu_hide",
                "menu_copy_to",
                "menu_move_to",
                "menu_add_to_virtual_album",
                "menu_remove_from_virtual_album",
                "menu_restore_file",
                "menu_rotate",
                "menu_change_orientation",
                "menu_open_with",
                "menu_set_as",
                "menu_slideshow",
                "menu_show_on_map",
                "menu_resize",
                "menu_print",
                "menu_create_shortcut",
                "menu_share",
                "menu_edit",
                "menu_delete"
            ),
            overflowIds
        )

        assertFalse(overflowIds.contains("menu_settings"))
        assertTrue(items.all { it.hasIcon })
    }

    @Test
    fun popupSizingUsesNativeProportionedScaleAndIdentifiesSettingsFooter() {
        assertTrue(CalypsoOverflowMenuPopup.POPUP_WIDTH_DP >= 270f)
        assertTrue(CalypsoOverflowMenuPopup.ROW_MIN_HEIGHT_DP >= 44f)
        assertTrue(CalypsoOverflowMenuPopup.ROW_TEXT_SIZE_SP >= 14f)
        assertTrue(CalypsoOverflowMenuPopup.isSettingsItem(R.id.settings))
        assertFalse(CalypsoOverflowMenuPopup.isSettingsItem(R.id.sort))
    }

    @Test
    fun settingsFooterDoesNotRenderATrailingChevron() {
        val source = File(
            "app/src/main/kotlin/org/fossify/gallery/helpers/CalypsoOverflowMenuPopup.kt"
        ).readText()
        assertFalse(source.contains("if (item.hasSubMenu() || isSettingsFooter)"))
        assertTrue(source.contains("if (item.hasSubMenu())"))
    }

    @Test
    fun semanticGroupsSeparatePrimaryViewVisibilityAndManageSections() {
        assertEquals("primary", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.show_albums))
        assertEquals("primary", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.refresh_menu))
        assertEquals("view", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.filter))
        assertEquals("view", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.group))
        assertEquals("visibility", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.temporarily_show_hidden_folders))
        assertEquals("manage", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.open_recycle_bin))
        assertEquals("manage", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.slideshow))

        assertEquals("viewer_info", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.menu_properties))
        assertEquals("viewer_file", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.menu_copy_to))
        assertEquals("viewer_display", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.menu_rotate))
        assertEquals("viewer_external", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.menu_slideshow))
        assertEquals("viewer_extra", CalypsoOverflowMenuPopup.resolveMenuItemGroup(R.id.menu_show_on_map))
    }
}
