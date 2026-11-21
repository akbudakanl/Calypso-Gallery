package org.fossify.gallery.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.fossify.gallery.extensions.config
import java.io.File

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

/**
 * Persists user-created Custom palettes and mirrors them into `Calypso Gallery/Custom Themes`
 * when storage backup is enabled and shared storage is writable.
 */
object SavedThemes {
    const val CUSTOM_THEMES_DIR_NAME = "Custom Themes"

    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val listType = object : TypeToken<MutableList<SavedTheme>>() {}.type
    private val illegalFileChars = Regex("[\\\\/:*?\"<>|]")

    fun load(config: Config): MutableList<SavedTheme> = try {
        gson.fromJson<MutableList<SavedTheme>>(config.savedThemesJson, listType) ?: mutableListOf()
    } catch (_: Exception) {
        mutableListOf()
    }

    fun save(config: Config, themes: List<SavedTheme>) {
        config.savedThemesJson = gson.toJson(themes)
    }

    fun isBuiltIn(text: Int, background: Int, primary: Int, appIcon: Int): Boolean {
        val builtIns = listOf(
            AppearancePalette.LIGHT,
            AppearancePalette.DARK,
            AppearancePalette.NAVY,
            AppearancePalette.PAPER,
            AppearancePalette.FOREST,
        )
        for (preset in builtIns) {
            if (preset.text == text && preset.background == background && preset.primary == primary) {
                return true
            }
        }
        return false
    }

    fun getBackupDir(): File = File(CryptoManager.getCipherRootDirectory(), CUSTOM_THEMES_DIR_NAME)

    fun backupThemeToStorage(context: Context, theme: SavedTheme): File? {
        if (!context.config.autoBackupSavedThemes || !FavoritesAndVirtualAlbumsBackupManager.canWriteInternalBackups(context)) {
            return null
        }
        CryptoManager.ensureCipherRootDirectory()
        return writeThemeFile(getBackupDir(), theme)
    }

    fun backupAllThemesToStorage(context: Context, themes: List<SavedTheme>) {
        if (!context.config.autoBackupSavedThemes || !FavoritesAndVirtualAlbumsBackupManager.canWriteInternalBackups(context)) {
            return
        }
        CryptoManager.ensureCipherRootDirectory()
        val dir = getBackupDir()
        themes.forEach { writeThemeFile(dir, it) }
    }

    fun restoreMissingThemesFromStorage(context: Context, config: Config): Int {
        if (!FavoritesAndVirtualAlbumsBackupManager.canWriteInternalBackups(context)) {
            return 0
        }
        val backedUp = readThemesFromDir(getBackupDir())
        if (backedUp.isEmpty()) {
            return 0
        }
        val current = load(config)
        var added = 0
        for (candidate in backedUp) {
            if (isBuiltIn(candidate.textColor, candidate.backgroundColor, candidate.primaryColor, candidate.appIconColor)) {
                continue
            }
            val alreadyPresent = current.any {
                it.matches(candidate.textColor, candidate.backgroundColor, candidate.primaryColor, candidate.appIconColor)
            }
            if (!alreadyPresent) {
                current.add(candidate)
                added++
            }
        }
        if (added > 0) {
            save(config, current)
        }
        return added
    }

    internal fun writeThemeFile(dir: File, theme: SavedTheme): File? {
        return try {
            if (!dir.exists() && !dir.mkdirs()) {
                return null
            }
            val existing = dir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
                .orEmpty()
            for (file in existing) {
                val parsed = readThemeFile(file) ?: continue
                if (parsed.matches(theme.textColor, theme.backgroundColor, theme.primaryColor, theme.appIconColor)) {
                    return file
                }
            }

            val baseName = sanitizeFileName(theme.name)
            var target = File(dir, "$baseName.json")
            var suffix = 1
            while (target.exists()) {
                target = File(dir, "$baseName ($suffix).json")
                suffix++
            }

            val temp = File(dir, "${target.name}.${System.currentTimeMillis()}.tmp")
            temp.writeText(prettyGson.toJson(theme), Charsets.UTF_8)
            if (temp.exists()) {
                if (temp.renameTo(target)) {
                    return target
                }
                temp.delete()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    internal fun readThemesFromDir(dir: File): List<SavedTheme> {
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        val files = dir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: return emptyList()
        val result = mutableListOf<SavedTheme>()
        for (file in files) {
            val parsed = readThemeFile(file) ?: continue
            val dup = result.any {
                it.matches(parsed.textColor, parsed.backgroundColor, parsed.primaryColor, parsed.appIconColor)
            }
            if (!dup) {
                result.add(parsed)
            }
        }
        return result
    }

    private fun readThemeFile(file: File): SavedTheme? {
        return try {
            val parsed = gson.fromJson(file.readText(Charsets.UTF_8), SavedTheme::class.java)
            if (parsed == null || parsed.name.isBlank()) null else parsed
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName.trim().replace(illegalFileChars, "_").trim('.')
        return cleaned.ifEmpty { "custom_theme" }
    }
}
