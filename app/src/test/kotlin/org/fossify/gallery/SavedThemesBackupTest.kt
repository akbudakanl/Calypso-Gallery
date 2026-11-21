package org.fossify.gallery

import org.fossify.gallery.helpers.AppearancePalette
import org.fossify.gallery.helpers.SavedTheme
import org.fossify.gallery.helpers.SavedThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedThemesBackupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun builtInIncludesLightDarkNavyPaperAndForest() {
        assertTrue(SavedThemes.isBuiltIn(AppearancePalette.LIGHT_TEXT, AppearancePalette.LIGHT_BACKGROUND, AppearancePalette.LIGHT_PRIMARY, 0))
        assertTrue(SavedThemes.isBuiltIn(AppearancePalette.DARK_TEXT, AppearancePalette.DARK_BACKGROUND, AppearancePalette.DARK_PRIMARY, 0))
        assertTrue(SavedThemes.isBuiltIn(AppearancePalette.NAVY_TEXT, AppearancePalette.NAVY_BACKGROUND, AppearancePalette.NAVY_PRIMARY, 0))
        assertTrue(SavedThemes.isBuiltIn(AppearancePalette.PAPER_TEXT, AppearancePalette.PAPER_BACKGROUND, AppearancePalette.PAPER_PRIMARY, 0))
        assertTrue(SavedThemes.isBuiltIn(AppearancePalette.FOREST_TEXT, AppearancePalette.FOREST_BACKGROUND, AppearancePalette.FOREST_PRIMARY, 0))

        assertFalse(SavedThemes.isBuiltIn(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF778899.toInt(), 0))
    }

    @Test
    fun writeThemeFileAndReadThemesFromDirRoundTrip() {
        val dir = tempFolder.newFolder(SavedThemes.CUSTOM_THEMES_DIR_NAME)
        val theme = SavedTheme(
            name = "Özel tema 1",
            textColor = 0xFFF5F5F4.toInt(),
            backgroundColor = 0xFF1C1917.toInt(),
            primaryColor = 0xFFF59E0B.toInt(),
            appIconColor = 0xFF000000.toInt(),
        )

        val written = SavedThemes.writeThemeFile(dir, theme)
        assertNotNull(written)
        assertTrue(written!!.exists())
        assertEquals("Özel tema 1.json", written.name)

        val loaded = SavedThemes.readThemesFromDir(dir)
        assertEquals(1, loaded.size)
        assertEquals(theme.name, loaded[0].name)
        assertTrue(loaded[0].matches(theme.textColor, theme.backgroundColor, theme.primaryColor, theme.appIconColor))
    }

    @Test
    fun writeThemeFileDeduplicatesIdenticalColorsAndAvoidsNameCollisions() {
        val dir = tempFolder.newFolder("custom_themes")
        val first = SavedTheme(
            name = "My:Theme/Name",
            textColor = 0xFF111111.toInt(),
            backgroundColor = 0xFFEEEEEE.toInt(),
            primaryColor = 0xFF224466.toInt(),
            appIconColor = 0,
        )
        val identicalColorsDifferentName = first.copy(name = "Duplicate Colors")
        val sameNameDifferentColors = first.copy(primaryColor = 0xFF993322.toInt())

        val firstFile = SavedThemes.writeThemeFile(dir, first)
        val duplicateFile = SavedThemes.writeThemeFile(dir, identicalColorsDifferentName)
        val collisionFile = SavedThemes.writeThemeFile(dir, sameNameDifferentColors)

        assertNotNull(firstFile)
        assertNotNull(duplicateFile)
        assertNotNull(collisionFile)
        assertEquals(firstFile!!.absolutePath, duplicateFile!!.absolutePath)
        assertEquals("My_Theme_Name (1).json", collisionFile!!.name)

        val loaded = SavedThemes.readThemesFromDir(dir)
        assertEquals(2, loaded.size)
    }

    @Test
    fun readThemesFromDirIgnoresInvalidJsonAndBlankNames() {
        val dir = tempFolder.newFolder("mixed_themes")
        File(dir, "broken.json").writeText("{ not valid json", Charsets.UTF_8)
        File(dir, "blank.json").writeText("""{"name":"","textColor":1,"backgroundColor":2,"primaryColor":3,"appIconColor":0}""", Charsets.UTF_8)
        File(dir, "note.txt").writeText("ignored", Charsets.UTF_8)

        val valid = SavedTheme("Valid", 10, 20, 30, 0)
        SavedThemes.writeThemeFile(dir, valid)

        val loaded = SavedThemes.readThemesFromDir(dir)
        assertEquals(1, loaded.size)
        assertEquals("Valid", loaded[0].name)
    }
}
