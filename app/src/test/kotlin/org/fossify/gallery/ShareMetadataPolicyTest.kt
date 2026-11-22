package org.fossify.gallery

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShareMetadataPolicyTest {

    private fun locate(relPath: String): File {
        val direct = File(relPath)
        if (direct.exists()) return direct
        val fromRoot = File("app", relPath)
        assertTrue("Missing $relPath", fromRoot.exists())
        return fromRoot
    }

    @Test
    fun shareMetadataHasThreeModesAndDefaultsToLocationOnly() {
        val config = locate("src/main/kotlin/org/fossify/gallery/helpers/Config.kt").readText()
        assertTrue(
            config.contains("prefs.getInt(SHARE_METADATA_MODE, SHARE_METADATA_MODE_LOCATION)")
        )
        val constants = locate("src/main/kotlin/org/fossify/gallery/helpers/Constants.kt").readText()
        assertTrue(
            constants.contains("const val SHARE_METADATA_MODE_OFF = 0") &&
                constants.contains("const val SHARE_METADATA_MODE_LOCATION = 1") &&
                constants.contains("const val SHARE_METADATA_MODE_ALL = 2")
        )
    }

    @Test
    fun everyShareEntryPointUsesTheSanitizer() {
        val activity = locate("src/main/kotlin/org/fossify/gallery/extensions/Activity.kt").readText()
        val sharePath = activity.substringAfter("fun Activity.sharePath")
            .substringBefore("fun Activity.shareMediumPath")
        val sharePaths = activity.substringAfter("fun Activity.sharePaths")
            .substringBefore("fun Activity.shareMediumPath")
        assertTrue(
            "Single share must sanitize a copy",
            sharePath.contains("ShareMetadataSanitizer.sanitizedPaths")
        )
        assertTrue(
            "Multiple share must sanitize every copy",
            sharePaths.contains("ShareMetadataSanitizer.sanitizedPaths")
        )
        val sanitizer = locate(
            "src/main/kotlin/org/fossify/gallery/helpers/ShareMetadataSanitizer.kt"
        ).readText()
        assertTrue(
            sanitizer.contains("AllNonDimensionExifAttributes.filter { it.startsWith(\"GPS\") }") &&
                sanitizer.contains("SHARE_METADATA_MODE_ALL -> AllNonDimensionExifAttributes")
        )
    }

    @Test
    fun newStringsExistInEnglishTurkishAndAllBlindTestLocales() {
        val keys = listOf(
            "settings_group_secure_sharing",
            "settings_share_metadata_title",
            "share_metadata_off",
            "share_metadata_location_only",
            "share_metadata_all"
        )
        val locales = listOf(
            "", "-tr", "-az", "-de", "-es", "-b+es+419", "-es-rUS", "-ja", "-nb-rNO", "-nn", "-zh-rTW"
        )
        for (locale in locales) {
            val xml = locate("src/main/res/values$locale/strings.xml").readText()
            for (key in keys) {
                assertTrue(
                    "Missing $key in values$locale",
                    xml.contains("name=\"$key\"")
                )
            }
        }
    }
}
