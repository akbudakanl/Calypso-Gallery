package org.fossify.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IsolatedMediaDecodingPolicyTest {

    private fun locateRepoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
        )
        return candidates.first { it.exists() }
    }

    @Test
    fun buildConfigExcludesRemovedLegacyExifAndAnimatedDecoders() {
        val catalogText = locateRepoFile("gradle/libs.versions.toml").readText()
        val gradleText = locateRepoFile("app/build.gradle.kts").readText()

        for (forbidden in listOf(
            "sanselan",
            "it.sephiroth.android.exif",
            "com.github.penfeizhou.android.animation",
            "android-gif-drawable",
            "zjupure",
            "webpdecoder",
            "jxl-coder",
            "androidphotofilters",
        )) {
            assertFalse(
                "libs.versions.toml must not declare $forbidden",
                catalogText.contains(forbidden),
            )
            assertFalse(
                "app/build.gradle.kts must not depend on $forbidden",
                gradleText.contains(forbidden),
            )
        }
    }

    @Test
    fun licensesDoNotReferenceRemovedMediaAndExifLibraries() {
        val licensesText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/activities/ThirdPartyLicensesActivity.kt",
        ).readText()

        for (forbidden in listOf(
            "sanselan_title",
            "license_exif_extended_title",
            "license_animated_decoders_title",
            "license_webpdecoder_title",
            "license_jxl_coder_title",
            "gif_drawable_title",
            "filters_title",
        )) {
            assertFalse(
                "ThirdPartyLicensesActivity must not list removed entry $forbidden",
                licensesText.contains(forbidden),
            )
        }
        for (required in listOf(
            "license_rust_gif_title",
            "license_rust_image_webp_title",
            "license_rust_png_title",
            "license_rust_zune_jpeg_title",
            "license_rust_jxl_oxide_title",
            "license_rust_jni_sys_title",
        )) {
            assertTrue(
                "ThirdPartyLicensesActivity must list pure-Rust decoder $required",
                licensesText.contains(required),
            )
        }
    }

    @Test
    fun photoFragmentUsesAndroidXExifAndExcludesPenfeizhouDrawables() {
        val photoFragmentText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt",
        ).readText()

        for (forbidden in listOf("sephiroth", "sanselan", "APNGDrawable", "AVIFDrawable", "WebPDrawable")) {
            assertFalse(
                "PhotoFragment must not reference $forbidden",
                photoFragmentText.contains(forbidden),
            )
        }
    }

    @Test
    fun thumbnailGifAnimationSettingIsRemoved() {
        val files = listOf(
            "app/src/main/kotlin/org/fossify/gallery/helpers/Config.kt",
            "app/src/main/kotlin/org/fossify/gallery/helpers/Constants.kt",
            "app/src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt",
            "app/src/main/kotlin/org/fossify/gallery/extensions/Context.kt",
        )
        for (path in files) {
            val content = locateRepoFile(path).readText()
            assertFalse("$path must not contain ANIMATE_GIFS", content.contains("ANIMATE_GIFS"))
            assertFalse("$path must not contain animateGifs", content.contains("animateGifs"))
        }
    }

    @Test
    fun manifestDeclaresNonExportedIsolatedDecoderService() {
        val manifest = locateRepoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("org.fossify.gallery.helpers.media.IsolatedMediaDecoderService"))
        assertTrue(manifest.contains("android:isolatedProcess=\"true\""))
    }

    @Test
    fun appOnCreateGuardsIsolatedProcessBeforeSuperOnCreate() {
        val appText = locateRepoFile("app/src/main/kotlin/org/fossify/gallery/App.kt").readText()
        val guardIndex = appText.indexOf("ProcessIsolation.isIsolatedProcess()")
        val superIndex = appText.indexOf("super.onCreate()")
        assertTrue("App.onCreate must guard isolated processes", guardIndex in 0 until superIndex)
    }

    @Test
    fun glideModuleExcludesNativeThirdPartyModulesAndBlocksDirectStreams() {
        val svgModuleText = locateRepoFile("app/src/main/kotlin/org/fossify/gallery/svg/SvgModule.kt").readText()
        for (required in listOf(
            "AvifGlideModule::class",
            "BlockedMainProcessStreamLoader",
            "BlockedMainProcessByteBufferFileLoader",
            "VideoOnlyPfdModelLoader",
        )) {
            assertTrue("SvgModule must reference $required", svgModuleText.contains(required))
        }
    }

    @Test
    fun migratedMainProcessFilesDoNotInvokeBitmapFactoryOrJxlCoderDirectly() {
        val migratedFiles = listOf(
            "app/src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerFileCrypto.kt",
            "app/src/main/kotlin/org/fossify/gallery/helpers/CryptoManagerFileDecrypt.kt",
            "app/src/main/kotlin/org/fossify/gallery/helpers/EncryptedGlideLoader.kt",
            "app/src/main/kotlin/org/fossify/gallery/helpers/MediaDimensions.kt",
            "app/src/main/kotlin/org/fossify/gallery/dialogs/SmartFileConflictDialog.kt",
            "app/src/main/kotlin/org/fossify/gallery/fragments/ViewPagerFragment.kt",
            "app/src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt",
            "app/src/main/kotlin/org/fossify/gallery/extensions/Activity.kt",
        )
        for (path in migratedFiles) {
            val content = locateRepoFile(path).readText()
            assertFalse("$path must not reference BitmapFactory", content.contains("BitmapFactory"))
            assertFalse("$path must not reference JxlCoder", content.contains("JxlCoder"))
        }
    }

    @Test
    fun regionDecoderCropperAndResolutionCallersAreIsolated() {
        val aidlText = locateRepoFile(
            "app/src/main/aidl/org/fossify/gallery/helpers/media/IIsolatedMediaDecoder.aidl",
        ).readText()
        for (method in listOf("openRegionSession", "decodeRegionTile", "closeRegionSession")) {
            assertTrue("IIsolatedMediaDecoder.aidl must declare $method", aidlText.contains(method))
        }

        val regionDecoderText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/PicassoRegionDecoder.kt",
        ).readText()
        assertFalse(regionDecoderText.contains("BitmapRegionDecoder"))
        assertFalse(regionDecoderText.contains("BitmapFactory"))

        for (cropperFile in listOf(
            "app/src/main/kotlin/org/fossify/gallery/activities/EditActivity.kt",
            "app/src/main/kotlin/org/fossify/gallery/activities/SetWallpaperActivity.kt",
        )) {
            assertFalse(
                "$cropperFile must not call setImageUriAsync",
                locateRepoFile(cropperFile).readText().contains("setImageUriAsync"),
            )
        }

        for (resFile in listOf(
            "app/src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt",
            "app/src/main/kotlin/org/fossify/gallery/extensions/Activity.kt",
        )) {
            assertFalse(
                "$resFile must not call commons getImageResolution",
                locateRepoFile(resFile).readText().contains("getImageResolution"),
            )
        }
    }

    @Test
    fun gifPlaybackIsIsolatedAndMainProcessHasNoDirectMediaDecoders() {
        val aidlText = locateRepoFile(
            "app/src/main/aidl/org/fossify/gallery/helpers/media/IIsolatedMediaDecoder.aidl",
        ).readText()
        for (method in listOf("openGifSession", "renderGifFrame", "closeGifSession")) {
            assertTrue("IIsolatedMediaDecoder.aidl must declare $method", aidlText.contains(method))
        }

        val pagerLayout = locateRepoFile("app/src/main/res/layout/pager_photo_item.xml").readText()
        assertFalse(pagerLayout.contains("pl.droidsonroids.gif"))
        assertFalse(pagerLayout.contains("GifTextureView"))

        val mainKotlinRoot = locateRepoFile("app/src/main/kotlin")
        val forbiddenTokens = listOf(
            "BitmapFactory",
            "BitmapRegionDecoder",
            "android.graphics.ImageDecoder",
            "WebpBitmapFactory",
            "WebpDownsampler",
            "AvifDecoder",
            "AvifByteBufferBitmapDecoder",
            "SVG.getFrom",
            "pl.droidsonroids.gif",
            "GifTextureView",
            "GifImageView",
            "setImageUriAsync",
            "getImageResolution",
        )

        mainKotlinRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val normalized = file.invariantSeparatorsPath
                if (normalized.contains("/helpers/media/")) return@forEach
                val text = file.readText()
                for (token in forbiddenTokens) {
                    assertFalse("$normalized must not reference $token", text.contains(token))
                }
                assertFalse("$normalized must not reference JxlCoder", text.contains("JxlCoder"))
            }

        val resRoot = locateRepoFile("app/src/main/res")
        resRoot.walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .forEach { file ->
                val text = file.readText()
                assertFalse("${file.name} must not use GifTextureView", text.contains("GifTextureView"))
                assertFalse("${file.name} must not use GifImageView", text.contains("GifImageView"))
                assertFalse(
                    "${file.name} must not reference pl.droidsonroids.gif",
                    text.contains("pl.droidsonroids.gif"),
                )
            }
    }

    @Test
    fun pureRustMediaCrateIsConfiguredAndLegacyNativeDecodersAreAbsent() {
        val cargoToml = locateRepoFile("rust/calypso-media/Cargo.toml").readText()
        val cargoLock = locateRepoFile("rust/calypso-media/Cargo.lock").readText()

        for (requiredCrate in listOf("gif", "image-webp", "png", "zune-jpeg", "jxl-oxide")) {
            assertTrue("Cargo.toml must depend on $requiredCrate", cargoToml.contains(requiredCrate))
            assertTrue("Cargo.lock must lock $requiredCrate", cargoLock.contains("name = \"$requiredCrate\""))
        }
        for (forbiddenSys in listOf("lcms2", "libjxl", "libwebp", "giflib", "windows-sys")) {
            assertFalse("Cargo.lock must not include $forbiddenSys", cargoLock.contains(forbiddenSys))
        }

        val mainKotlinRoot = locateRepoFile("app/src/main/kotlin")
        val legacyNativeTokens = listOf(
            "pl.droidsonroids.gif",
            "WebpBitmapFactory",
            "WebpDownsampler",
            "WebpGlideLibraryModule",
            "JxlCoder",
            "JxlCoderGlideModule",
            "com.zomato.photofilters",
            "NativeImageProcessor",
        )
        mainKotlinRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                for (token in legacyNativeTokens) {
                    assertFalse("${file.invariantSeparatorsPath} must not reference $token", text.contains(token))
                }
            }
    }
}
