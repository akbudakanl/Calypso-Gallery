package org.fossify.gallery

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Commons inherits its upstream green `colorPrimary` (#106D1F); the platform uses that accent for
 * carets. No green value exists in our sources, so the guardrails below check that every field we
 * own is themed to the active appearance palette and that `@null` (which restores the green
 * default) is never used again. Library-owned dialogs fall back to the neutral colour override in
 * colors.xml.
 */
class SearchBarCursorTest {

    private fun locateAppFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val underApp = File("app", relativePath)
        if (underApp.exists()) return underApp
        error("Could not locate $relativePath from working directory ${File(".").absolutePath}")
    }

    @Test
    fun sharedSearchBarStylingThemesTheCursor() {
        val styling = locateAppFile("src/main/kotlin/org/fossify/gallery/extensions/SearchBarStyling.kt").readText()
        val searchInputBlock = styling.substringAfter("top_toolbar_search")
        assertTrue(
            "applyCalypsoSearchBarStyle must replace the inherited green caret on the search input",
            searchInputBlock.contains("applyThemedCursor("),
        )
    }

    @Test
    fun everySearchBarHostUsesSharedStyling() {
        val hosts = listOf(
            "src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt",
            "src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt",
            "src/main/kotlin/org/fossify/gallery/activities/SearchActivity.kt",
            "src/main/kotlin/org/fossify/gallery/dialogs/PickDirectoryDialog.kt",
            "src/main/kotlin/org/fossify/gallery/dialogs/VirtualAlbumDialogs.kt",
        )
        for (host in hosts) {
            val source = locateAppFile(host).readText()
            assertTrue(
                "$host must call applyCalypsoSearchBarStyle so its cursor matches the palette",
                source.contains("applyCalypsoSearchBarStyle()"),
            )
        }
    }

    @Test
    fun settingsSearchFieldThemesItsCursor() {
        val settings = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt").readText()
        val refreshBlock = settings.substringAfter("refreshSettingsSearchColors")
        assertTrue(
            "Settings' plain EditText search field must override the inherited green caret",
            refreshBlock.contains("settingsSearch.applyThemedCursor("),
        )
    }

    @Test
    fun appLayoutsNeverUseNullCursorDrawable() {
        val layoutDir = locateAppFile("src/main/res/layout")
        val offenders = layoutDir.walkTopDown().filter { it.isFile && it.extension == "xml" }
            .filter { it.readText().contains("textCursorDrawable=\"@null\"") }
            .map { it.name }
            .toList()
        assertTrue(
            "textCursorDrawable=@null restores the library's green default; use @drawable/vault_text_cursor instead. Offenders: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun dialogsWithTextInputsApplyPaletteCursors() {
        val dialogs = listOf(
            "CustomAspectRatioDialog.kt",
            "ExportFavoritesDialog.kt",
            "ResizeDialog.kt",
            "ResizeWithPathDialog.kt",
            "ResizeMultipleImagesDialog.kt",
            "SaveAsDialog.kt",
            "SlideshowDialog.kt",
            "ChangeLanguageDialog.kt",
            "RenameEncryptedDialog.kt",
            "CreateEncryptedAlbumDialog.kt",
            "VirtualAlbumDialogs.kt",
        )
        val dir = locateAppFile("src/main/kotlin/org/fossify/gallery/dialogs")
        for (name in dialogs) {
            val source = File(dir, name).readText()
            assertTrue(
                "$name must theme its input carets via applyPaletteCursors",
                source.contains("applyPaletteCursors("),
            )
        }
    }

    @Test
    fun commonsPrimaryColorIsOverriddenToNeutralFallback() {
        val colors = locateAppFile("src/main/res/values/colors.xml").readText()
        assertTrue(
            "Commons' green color_primary must be overridden for library-owned dialogs",
            colors.contains("<color name=\"color_primary\" tools:override=\"true\">"),
        )
        assertTrue(
            "The matching dark primary must be overridden too",
            colors.contains("<color name=\"color_primary_dark\" tools:override=\"true\">"),
        )
    }
}
