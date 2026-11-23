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
        assertTrue("Single share must sanitize a copy", sharePath.contains("sanitizeForSharing"))
        assertTrue("Multiple share must sanitize every copy", sharePaths.contains("sanitizeForSharing"))

        val fallback = activity.substringAfter("private fun Activity.sanitizeForSharing")
        assertTrue(
            "Sanitizer must be used",
            fallback.contains("ShareMetadataSanitizer.sanitizedPaths")
        )
        assertTrue(
            "A failed sanitization must abort sharing instead of leaking metadata",
            fallback.contains("ShareMetadataSanitizationException") &&
                fallback.contains("R.string.share_metadata_failed") &&
                fallback.contains("null")
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
    fun sanitizedCopiesArePublishedToPublicMediaStore() {
        val sanitizer = locate(
            "src/main/kotlin/org/fossify/gallery/helpers/ShareMetadataSanitizer.kt"
        ).readText()
        assertTrue(
            "Bytes must be streamed directly into the published MediaStore entry",
            sanitizer.contains("resolver.openOutputStream(uri)") &&
                sanitizer.contains("source.inputStream().use")
        )
        assertTrue(
            "No intermediate cache copy may be created",
            !sanitizer.contains("workingDirs") && !sanitizer.contains("source.copyTo(target")
        )
        assertTrue(
            "EXIF must be stripped in place on the published copy",
            sanitizer.contains("openFileDescriptor(uri, \"rw\")") &&
                sanitizer.contains("ExifInterface(pfd.fileDescriptor)")
        )
        assertTrue(
            "Cleaned copies must be published to MediaStore",
            sanitizer.contains("MediaStore.Images.Media.EXTERNAL_CONTENT_URI") &&
                sanitizer.contains("MEDIA_RELATIVE_PATH")
        )
        assertTrue(
            "The share folder must be hidden from the gallery before copies appear",
            sanitizer.contains("ensureNoMediaMarker(context)") &&
                sanitizer.contains("\".nomedia\"")
        )
        assertTrue(
            "Published copies must be cleaned up shortly after sharing and on app start",
            sanitizer.contains("scheduleCleanup(context.applicationContext, published.second)") &&
                sanitizer.contains("Uri.parse(entry.substringBefore('|'))") &&
                sanitizer.contains("CLEANUP_DELAY_MS = 30 * 60 * 1000L")
        )
    }

    @Test
    fun newStringsExistInEnglishTurkishAndAllBlindTestLocales() {
        val keys = listOf(
            "settings_group_secure_sharing",
            "settings_share_metadata_title",
            "share_metadata_off",
            "share_metadata_location_only",
            "share_metadata_all",
            "share_metadata_failed"
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
