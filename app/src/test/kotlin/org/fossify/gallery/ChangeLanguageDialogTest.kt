package org.fossify.gallery

import org.fossify.gallery.dialogs.ChangeLanguageDialog
import org.fossify.gallery.dialogs.ChangeLanguageDialog.LanguageOption
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeLanguageDialogTest {

    private val allOptions = listOf(
        LanguageOption(code = "", label = "System default", nativeName = "System default", englishName = "System default"),
        LanguageOption(code = "de", label = "Deutsch", nativeName = "Deutsch", englishName = "German"),
        LanguageOption(code = "en", label = "English", nativeName = "English", englishName = "English"),
        LanguageOption(code = "en-GB", label = "English (United Kingdom)", nativeName = "English (United Kingdom)", englishName = "English (United Kingdom)"),
        LanguageOption(code = "fr", label = "Français", nativeName = "Français", englishName = "French"),
        LanguageOption(code = "tr", label = "Türkçe", nativeName = "Türkçe", englishName = "Turkish")
    )

    @Test
    fun findsSystemDefaultWhenPresentAndReturnsMinusOneWhenFilteredOut() {
        assertEquals(0, ChangeLanguageDialog.findSelectedIndex(allOptions, ""))

        val filtered = allOptions.filter { it.code == "de" || it.code == "fr" }
        assertEquals(-1, ChangeLanguageDialog.findSelectedIndex(filtered, ""))
    }

    @Test
    fun prefersExactMatchOverBaseLanguageFallback() {
        assertEquals(3, ChangeLanguageDialog.findSelectedIndex(allOptions, "en-GB"))
        assertEquals(2, ChangeLanguageDialog.findSelectedIndex(allOptions, "en-US"))
        assertEquals(5, ChangeLanguageDialog.findSelectedIndex(allOptions, "tr-TR"))
    }

    @Test
    fun returnsMinusOneWhenSelectedLanguageIsFilteredOut() {
        val filtered = allOptions.filter { it.code == "de" || it.code == "fr" }
        assertEquals(-1, ChangeLanguageDialog.findSelectedIndex(filtered, "tr"))
        assertEquals(-1, ChangeLanguageDialog.findSelectedIndex(filtered, "en"))
    }
}
