package org.fossify.gallery.helpers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedTheme(
    val name: String,
    val textColor: Int,
    val backgroundColor: Int,
    val primaryColor: Int,
    val appIconColor: Int,
) {
    fun matches(text: Int, background: Int, primary: Int, appIcon: Int): Boolean =
        textColor == text && backgroundColor == background && primaryColor == primary
}

/** Persists user-created Custom palettes so they survive process restarts. */
object SavedThemes {
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<SavedTheme>>() {}.type

    fun load(config: Config): MutableList<SavedTheme> = try {
        gson.fromJson<MutableList<SavedTheme>>(config.savedThemesJson, listType) ?: mutableListOf()
    } catch (_: Exception) {
        mutableListOf()
    }

    fun save(config: Config, themes: List<SavedTheme>) {
        config.savedThemesJson = gson.toJson(themes)
    }

    fun isBuiltIn(text: Int, background: Int, primary: Int, appIcon: Int): Boolean {
        for (preset in listOf(AppearancePalette.LIGHT, AppearancePalette.DARK)) {
            if (preset.text == text && preset.background == background &&
                preset.primary == primary
            ) {
                return true
            }
        }
        return false
    }
}
