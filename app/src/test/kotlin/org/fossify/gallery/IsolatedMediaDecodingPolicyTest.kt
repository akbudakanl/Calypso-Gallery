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
            "avif-integration",
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
            "license_rust_zune_jpeg_title",
        )) {
            assertFalse(
                "ThirdPartyLicensesActivity must not list removed entry $forbidden",
                licensesText.contains(forbidden),
            )
        }
        for (required in listOf(
            "license_rust_gif_title",
            "license_rust_png_title",
            "license_rust_image_webp_title",
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

        for (requiredCrate in listOf("gif", "jxl-oxide", "jni-sys", "png", "image-webp")) {
            assertTrue("Cargo.toml must depend on $requiredCrate", cargoToml.contains(requiredCrate))
            assertTrue("Cargo.lock must lock $requiredCrate", cargoLock.contains("name = \"$requiredCrate\""))
        }
        for (forbiddenSys in listOf(
            "lcms2",
            "libjxl",
            "libwebp",
            "giflib",
            "windows-sys",
            "zune-jpeg",
        )) {
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
            "AvifGlideModule",
            "AvifByteBufferBitmapDecoder",
            "AvifDecoder",
        )
        mainKotlinRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                for (token in legacyNativeTokens) {
                    assertFalse("${file.invariantSeparatorsPath} must not reference $token", text.contains(token))
                }
            }

        val platformCodecsText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedPlatformCodecs.kt",
        ).readText()
        assertTrue(
            "IsolatedPlatformCodecs must decode embedded RAW previews via decodePlatform inside :media_decoder",
            platformCodecsText.contains("decodePlatform(embedded"),
        )
    }

    @Test
    fun svgDecodingIsDisabledByDefaultAndFailClosedInIsolatedProcess() {
        val decodersText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedMediaDecoders.kt",
        ).readText()
        assertTrue(decodersText.contains("isFormatAllowed(format, request.flags)"))
        assertTrue(decodersText.contains("IsolatedDecodeResult.STATUS_UNSUPPORTED"))

        val clientText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedDecoderClient.kt",
        ).readText()
        assertTrue(clientText.contains("FLAG_ALLOW_SVG"))
        assertTrue(clientText.contains("showSvgFiles"))

        val constantsText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/Constants.kt",
        ).readText()
        val defaultFilterLine = constantsText.lines()
            .firstOrNull { it.contains("fun getDefaultFileFilter()") }
            .orEmpty()
        assertFalse(
            "getDefaultFileFilter must exclude TYPE_SVGS by default",
            defaultFilterLine.contains("TYPE_SVGS"),
        )

        val mainActivityText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt",
        ).readText()
        assertFalse("MainActivity must not auto-enable TYPE_SVGS", mainActivityText.contains("wasSVGShowingHandled"))

        val filterDialogText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/dialogs/FilterMediaDialog.kt",
        ).readText()
        assertTrue(filterDialogText.contains("showSvgFiles"))

        val mediaSecurityText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/activities/SettingsActivityMediaSecurity.kt",
        ).readText()
        assertTrue(mediaSecurityText.contains("show_svg_files_warning"))
        assertTrue(mediaSecurityText.contains("enable_svg_files"))
    }

    @Test
    fun vaultDecoderIsolationMteAndHardeningCapsAreEnforced() {
        val manifest = locateRepoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("org.fossify.gallery.helpers.media.VaultIsolatedMediaDecoderService"))
        assertTrue(manifest.contains("android:process=\":vault_decoder\""))
        assertTrue(manifest.contains("android:memtagMode=\"async\""))

        val cryptoManagerText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/CryptoManager.kt",
        ).readText()
        assertTrue(cryptoManagerText.contains("VaultDecoderLifecycle.onVaultLocked"))

        val transportText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedPixelTransport.kt",
        ).readText()
        assertFalse(transportText.contains("16_384"))
        assertTrue(transportText.contains("IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION"))

        val rustLibText = locateRepoFile("rust/calypso-media/src/lib.rs").readText()
        assertTrue(rustLibText.contains("MAX_JXL_SOURCE_PIXELS"))
        assertTrue(rustLibText.contains("MAX_RUST_LOSSY_WEBP_PIXELS"))

        val decodersText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedMediaDecoders.kt",
        ).readText()
        assertTrue(decodersText.contains("RustFastOutcome.Failed"))
        assertTrue(decodersText.contains("RustFastOutcome.DeferToPlatform"))
        assertTrue(decodersText.contains("PLATFORM_DEFER_ALLOWED_FORMATS"))
    }

    @Test
    fun phase6HdrAnimationPngStreamingAndFdStreamingPoliciesAreEnforced() {
        val hdrText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedHdrTransport.kt",
        ).readText()
        assertTrue(hdrText.contains("isFinite()"))
        assertTrue(hdrText.contains("FLAG_INCLUDE_GAINMAP"))

        val glideLoadersText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedGlideLoaders.kt",
        ).readText()
        assertTrue(glideLoadersText.contains("INCLUDE_GAINMAP"))

        val photoFragmentText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt",
        ).readText()
        assertTrue(photoFragmentText.contains("IsolatedGlideOptions.INCLUDE_GAINMAP"))
        assertTrue(photoFragmentText.contains("isAnimatedMediaCandidate"))

        val fdStreamingText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedFdStreamingCodecs.kt",
        ).readText()
        assertTrue(fdStreamingText.contains("MAX_STREAMING_INPUT_BYTES"))

        val regionStoreText = locateRepoFile(
            "app/src/main/kotlin/org/fossify/gallery/helpers/media/IsolatedRegionDecoderStore.kt",
        ).readText()
        assertTrue(regionStoreText.contains("ParcelFileDescriptor.dup"))
        assertTrue(regionStoreText.contains("MAX_STREAMING_INPUT_BYTES"))

        val rustPngText = locateRepoFile("rust/calypso-media/src/png_webp_decoders.rs").readText()
        assertTrue(rustPngText.contains("read_row"))

        val rustAnimText = locateRepoFile("rust/calypso-media/src/animated_session.rs").readText()
        assertTrue(rustAnimText.contains("ApngSession"))
        assertTrue(rustAnimText.contains("WebpAnimSession"))
    }
}
