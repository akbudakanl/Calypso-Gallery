package org.fossify.gallery

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ChunkIndex
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFormattedDuration
import org.fossify.gallery.helpers.CachedSeekResult
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.RandomAccessFileExtractorInput
import org.fossify.gallery.helpers.SeekIndexCacheKey
import org.fossify.gallery.helpers.SeekableExtractorsFactory
import org.fossify.gallery.helpers.SeekableFragmentedMp4Extractor
import org.fossify.gallery.helpers.SeekableMatroskaExtractor
import org.fossify.gallery.helpers.VAULT_HEADER_FILE
import org.fossify.gallery.helpers.VIDEO_FRAME_CACHE_TTL_MS
import org.fossify.gallery.helpers.VIDEO_FRAME_POSITION_TOLERANCE_MS
import org.fossify.gallery.helpers.VaultSessionManager
import org.fossify.gallery.helpers.VideoPlaybackUtils
import org.fossify.gallery.helpers.probeContainerDurationMs
import org.fossify.gallery.helpers.probeContainerDurationSeconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

@OptIn(UnstableApi::class)
class VideoPlaybackAndSeekIndexTest {

    @Before
    fun clearSeekIndexCacheBefore() {
        SeekableExtractorsFactory.clearCache()
    }

    @After
    fun clearSeekIndexCacheAfter() {
        SeekableExtractorsFactory.clearCache()
    }

    @Test
    fun getFormattedDurationGuardsTimeUnsetAndNegativeValues() {
        assertEquals("00:00", C.TIME_UNSET.getFormattedDuration())
        assertEquals("00:00", (-5_000L).getFormattedDuration())
        assertEquals("00:00", 0L.getFormattedDuration())
        assertEquals("01:05", 65_000L.getFormattedDuration())
    }

    @Test
    fun playbackUtilsSanitizeAndSkipWithoutResettingToZero() {
        assertEquals(0L, VideoPlaybackUtils.sanitizeDurationMs(C.TIME_UNSET))
        assertEquals(0, VideoPlaybackUtils.toSeekBarMax(C.TIME_UNSET))
        assertEquals(45_000L, VideoPlaybackUtils.effectiveDurationMs(C.TIME_UNSET, 45_000L))
        assertEquals(30_000L, VideoPlaybackUtils.effectiveDurationMs(30_000L, 45_000L))

        // Unknown duration: forward and backward skips must not clamp to 0L
        val fwdUnknown = VideoPlaybackUtils.computeSkipResult(
            currentPosMs = 15_000L,
            skipMs = 10_000L,
            durationMs = C.TIME_UNSET,
            forward = true,
        )
        assertFalse(fwdUnknown.reachedEnd)
        assertEquals(25_000L, fwdUnknown.targetPositionMs)

        val bwdUnknown = VideoPlaybackUtils.computeSkipResult(
            currentPosMs = 15_000L,
            skipMs = 10_000L,
            durationMs = C.TIME_UNSET,
            forward = false,
        )
        assertFalse(bwdUnknown.reachedEnd)
        assertEquals(5_000L, bwdUnknown.targetPositionMs)

        // Known duration: forward past end signals reachedEnd
        val fwdEnd = VideoPlaybackUtils.computeSkipResult(
            currentPosMs = 25_000L,
            skipMs = 10_000L,
            durationMs = 30_000L,
            forward = true,
        )
        assertTrue(fwdEnd.reachedEnd)

        // isVideoEnded must not return true just because duration is C.TIME_UNSET
        assertFalse(
            VideoPlaybackUtils.isVideoEnded(
                skippedToEnd = false,
                playbackState = Player.STATE_READY,
                currentPosMs = 15_000L,
                durationMs = C.TIME_UNSET,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isVideoEnded(
                skippedToEnd = false,
                playbackState = Player.STATE_ENDED,
                currentPosMs = 15_000L,
                durationMs = C.TIME_UNSET,
            )
        )
    }


    @Test
    fun surfaceAndPreviewRevealHelpersHandleQuickResumesAndSafetyNet() {
        // Preview cover is needed on visibility change unless a live TextureView already rendered a frame.
        assertFalse(
            VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(
                hasRenderedFirstFrame = true,
                hasAttachedTextureView = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(
                hasRenderedFirstFrame = false,
                hasAttachedTextureView = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldShowPreviewCoverOnVisibilityChange(
                hasRenderedFirstFrame = true,
                hasAttachedTextureView = false,
            )
        )

        // Resuming an already-rendered valid surface reveals immediately without waiting for onRenderedFirstFrame().
        assertTrue(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = true,
                hasRenderedFirstFrame = true,
                hasValidSurface = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = false,
                hasRenderedFirstFrame = true,
                hasValidSurface = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = true,
                hasRenderedFirstFrame = false,
                hasValidSurface = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnPlaybackResume(
                isFragmentVisible = true,
                hasRenderedFirstFrame = true,
                hasValidSurface = false,
            )
        )

        // onSurfaceTextureUpdated safety net only reveals when visible, active, valid, and matching.
        assertTrue(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = true,
                isPlayerPrepared = false,
                playWhenReady = false,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = false,
                isPlayerPrepared = true,
                playWhenReady = true,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = false,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = true,
                isPlayerPrepared = true,
                playWhenReady = true,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = false,
                isPlayerPrepared = true,
                playWhenReady = false,
                hasValidSurface = true,
                isMatchingTexture = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRevealOnSurfaceTextureUpdated(
                isFragmentVisible = true,
                isViewDestroyed = false,
                hasPlayer = true,
                isPlaying = true,
                isPlayerPrepared = true,
                playWhenReady = true,
                hasValidSurface = true,
                isMatchingTexture = false,
            )
        )
    }

    @Test
    fun frameCacheTtlLasts20MinutesAndExpiresCleanly() {
        assertEquals(20 * 60 * 1000L, VIDEO_FRAME_CACHE_TTL_MS)
        val capturedAt = 1_000L

        assertFalse(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt,
            )
        )
        assertFalse(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt + VIDEO_FRAME_CACHE_TTL_MS - 1L,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt + VIDEO_FRAME_CACHE_TTL_MS,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = null,
                nowElapsedMs = capturedAt,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt + 5_000L,
                nowElapsedMs = capturedAt,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isFrameCacheEntryExpired(
                capturedAtElapsedMs = capturedAt,
                nowElapsedMs = capturedAt,
                ttlMs = 0L,
            )
        )
    }

    @Test
    fun defaultPreviewAndExactLastFrameToleranceRulesBehaveAsExpected() {
        assertEquals(500L, VIDEO_FRAME_POSITION_TOLERANCE_MS)

        // Exact last frame tolerance: within 500ms is fresh, >500ms forces a fresh snapshot.
        assertTrue(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 5_000L))
        assertTrue(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 5_500L))
        assertFalse(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 5_501L))
        assertFalse(VideoPlaybackUtils.isCachedFramePositionFresh(5_000L, 10_000L))
        assertFalse(VideoPlaybackUtils.isCachedFramePositionFresh(null, 5_000L))

        // Unvisited or restored videos without a cached frame always load a preview (a thumbnail beats black).
        assertTrue(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = false,
                isPanorama = false,
            )
        )
        // Visited video with cached last frame keeps the cached frame instead of loading via Glide.
        assertFalse(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = true,
                isPanorama = false,
            )
        )
        // Panorama uses its own loader, not default video preview.
        assertFalse(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = false,
                isPanorama = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldLoadDefaultVideoPreview(
                hasCachedFrame = true,
                isPanorama = true,
            )
        )

        // Existing preview drawable retention: keep any loaded preview while no cached frame exists.
        assertTrue(
            VideoPlaybackUtils.shouldKeepExistingPreviewDrawable(
                hasCachedFrame = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldKeepExistingPreviewDrawable(
                hasCachedFrame = true,
            )
        )
    }

    private fun locateAppFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath"),
        )
        return candidates.first { it.exists() }
    }

    @Test
    fun restoredPreviewFrameMicrosAndPlayerInitPoliciesBehaveCorrectly() {
        assertEquals(0L, VideoPlaybackUtils.resolvePlaybackPositionMs(0L, 0L, 0L))
        assertEquals(0L, VideoPlaybackUtils.resolvePlaybackPositionMs(-10L, -5L, 0L))
        assertEquals(12_000L, VideoPlaybackUtils.resolvePlaybackPositionMs(12_000L, 8_000L, 4_000L))
        assertEquals(8_000L, VideoPlaybackUtils.resolvePlaybackPositionMs(0L, 8_000L, 4_000L))
        assertEquals(4_000L, VideoPlaybackUtils.resolvePlaybackPositionMs(0L, 0L, 4_000L))

        // Unencrypted video with restored position > 0 requests Glide frameOf(positionMs * 1000).
        assertEquals(
            15_500_000L,
            VideoPlaybackUtils.resolvePreviewFrameTimeMicros(positionMs = 15_500L, isEncrypted = false),
        )
        assertNull(VideoPlaybackUtils.resolvePreviewFrameTimeMicros(positionMs = 0L, isEncrypted = false))
        assertNull(VideoPlaybackUtils.resolvePreviewFrameTimeMicros(positionMs = -100L, isEncrypted = false))
        // Encrypted vault loader uses embedded header thumbnail and ignores frameOf; fall back cleanly.
        assertNull(VideoPlaybackUtils.resolvePreviewFrameTimeMicros(positionMs = 15_500L, isEncrypted = true))
    }

    @Test
    fun defaultPreviewCanLoadAndObsoletePoliciesBehaveCorrectly() {
        // Default preview start guard does not depend on mPendingAutoplayOnIdle.
        assertTrue(
            VideoPlaybackUtils.canLoadDefaultVideoPreview(
                isAdded = true,
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.canLoadDefaultVideoPreview(
                isAdded = false,
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.canLoadDefaultVideoPreview(
                isAdded = true,
                isViewDestroyed = true,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.canLoadDefaultVideoPreview(
                isAdded = true,
                isViewDestroyed = false,
                isBindingInitialized = false,
                hasRenderedFirstFrame = false,
                isPlaying = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.canLoadDefaultVideoPreview(
                isAdded = true,
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = true,
                isPlaying = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.canLoadDefaultVideoPreview(
                isAdded = true,
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = true,
            )
        )

        // Obsolete check drops stale async preview only once rendered/playing/cached/destroyed.
        assertFalse(
            VideoPlaybackUtils.isDefaultVideoPreviewObsolete(
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = false,
                hasCachedFrame = false,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isDefaultVideoPreviewObsolete(
                isViewDestroyed = true,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = false,
                hasCachedFrame = false,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isDefaultVideoPreviewObsolete(
                isViewDestroyed = false,
                isBindingInitialized = false,
                hasRenderedFirstFrame = false,
                isPlaying = false,
                hasCachedFrame = false,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isDefaultVideoPreviewObsolete(
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = true,
                isPlaying = false,
                hasCachedFrame = false,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isDefaultVideoPreviewObsolete(
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = true,
                hasCachedFrame = false,
            )
        )
        assertTrue(
            VideoPlaybackUtils.isDefaultVideoPreviewObsolete(
                isViewDestroyed = false,
                isBindingInitialized = true,
                hasRenderedFirstFrame = false,
                isPlaying = false,
                hasCachedFrame = true,
            )
        )
    }

    @Test
    fun playerInitAndMenuVisibilityPreviewPoliciesBehaveCorrectly() {
        // Player init must not cancel an in-flight default preview request.
        assertFalse(
            VideoPlaybackUtils.shouldClearInFlightPreviewOnPlayerInit(
                hasPreviewDrawable = false,
                isShowingDefaultPreview = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldClearInFlightPreviewOnPlayerInit(
                hasPreviewDrawable = true,
                isShowingDefaultPreview = false,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldClearInFlightPreviewOnPlayerInit(
                hasPreviewDrawable = false,
                isShowingDefaultPreview = false,
            )
        )

        // setMenuVisibility(true) drains pending previews or recovers missing pre-render cover.
        assertTrue(
            VideoPlaybackUtils.shouldEnsureDefaultPreviewOnMenuVisible(
                isPendingPreview = true,
                hasRenderedFirstFrame = true,
                isBindingInitialized = false,
                hasPreviewDrawable = true,
                isShowingDefaultPreview = true,
            )
        )
        assertTrue(
            VideoPlaybackUtils.shouldEnsureDefaultPreviewOnMenuVisible(
                isPendingPreview = false,
                hasRenderedFirstFrame = false,
                isBindingInitialized = true,
                hasPreviewDrawable = false,
                isShowingDefaultPreview = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldEnsureDefaultPreviewOnMenuVisible(
                isPendingPreview = false,
                hasRenderedFirstFrame = false,
                isBindingInitialized = true,
                hasPreviewDrawable = false,
                isShowingDefaultPreview = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldEnsureDefaultPreviewOnMenuVisible(
                isPendingPreview = false,
                hasRenderedFirstFrame = true,
                isBindingInitialized = true,
                hasPreviewDrawable = false,
                isShowingDefaultPreview = false,
            )
        )
    }

    @Test
    fun videoFragmentPreviewAndPagerContractChecks() {
        val videoFragmentText = locateAppFile("src/main/kotlin/org/fossify/gallery/fragments/VideoFragment.kt")
            .readText()
        val viewPagerActivityText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt")
            .readText()
        val constantsText = locateAppFile("src/main/kotlin/org/fossify/gallery/helpers/Constants.kt")
            .readText()

        // Cause 1: never defer preview loading to idle in onCreateView, and drain safety net in DRAGGING/menuVisible.
        assertFalse(videoFragmentText.contains("mPendingPreviewOnIdle = true"))
        assertTrue(videoFragmentText.contains("shouldEnsureDefaultPreviewOnMenuVisible("))

        // Cause 3: initExoPlayer checks shouldClearInFlightPreviewOnPlayerInit instead of clearing on drawable == null.
        assertTrue(videoFragmentText.contains("shouldClearInFlightPreviewOnPlayerInit("))

        // Cause 4: loadVideoPreview delegates to canLoadDefaultVideoPreview without mPendingAutoplayOnIdle guard.
        assertTrue(videoFragmentText.contains("canLoadDefaultVideoPreview("))

        // Cause 5: loadVideoPreview applies frameOf when resolvePreviewFrameTimeMicros returns non-null.
        assertTrue(videoFragmentText.contains("resolvePreviewFrameTimeMicros("))
        assertTrue(videoFragmentText.contains("RequestOptions.frameOf("))
        assertTrue(videoFragmentText.contains("isDefaultVideoPreviewObsolete("))

        // Surface recovery & color mode reset ordering before VideoFragment IDLE setup.
        assertTrue(videoFragmentText.contains("shouldRecoverLostSurface("))
        val resetColorIdx = viewPagerActivityText.indexOf("ColorModeHelper.resetColorMode(this)")
        val adapterIdleIdx = viewPagerActivityText.indexOf(
            "(binding.viewPager.adapter as? MyPagerAdapter)?.onPageScrollStateChanged(state)",
        )
        assertTrue(resetColorIdx in 0 until adapterIdleIdx)

        // Optimization 1: immediate reveal on first rendered frame (no 60ms fade delay).
        assertFalse(videoFragmentText.contains("PREVIEW_REVEAL_FADE_DURATION_MS"))

        // Buffers remain minimal at 50 ms.
        assertTrue(constantsText.contains("const val EXOPLAYER_PLAYBACK_BUFFER_MS = 50"))
    }

    @Test
    fun lostSurfaceRecoveryOnlyTriggersWhenActiveVisibleAndInvalid() {
        assertTrue(
            VideoPlaybackUtils.shouldRecoverLostSurface(
                isAdded = true,
                isResumed = true,
                isViewDestroyed = false,
                isFragmentVisible = true,
                isGesturePlayer = false,
                isPanorama = false,
                hasPlayer = true,
                isPlayingOrPending = true,
                hasValidSurface = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRecoverLostSurface(
                isAdded = true,
                isResumed = false,
                isViewDestroyed = false,
                isFragmentVisible = true,
                isGesturePlayer = false,
                isPanorama = false,
                hasPlayer = true,
                isPlayingOrPending = true,
                hasValidSurface = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRecoverLostSurface(
                isAdded = true,
                isResumed = true,
                isViewDestroyed = false,
                isFragmentVisible = false,
                isGesturePlayer = false,
                isPanorama = false,
                hasPlayer = true,
                isPlayingOrPending = true,
                hasValidSurface = false,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRecoverLostSurface(
                isAdded = true,
                isResumed = true,
                isViewDestroyed = false,
                isFragmentVisible = true,
                isGesturePlayer = false,
                isPanorama = false,
                hasPlayer = true,
                isPlayingOrPending = true,
                hasValidSurface = true,
            )
        )
        assertFalse(
            VideoPlaybackUtils.shouldRecoverLostSurface(
                isAdded = true,
                isResumed = true,
                isViewDestroyed = false,
                isFragmentVisible = true,
                isGesturePlayer = false,
                isPanorama = false,
                hasPlayer = true,
                isPlayingOrPending = false,
                hasValidSurface = false,
            )
        )
    }

    @Test
    fun clearEncryptedCacheOnlyEvictsEncryptedEntriesAndPreservesNormalGalleryOnBackground() {
        val dummyChunk = ChunkIndex(intArrayOf(100), longArrayOf(0L), longArrayOf(1_000_000L), longArrayOf(0L))
        val normalMp4 = SeekIndexCacheKey("/storage/emulated/0/DCIM/clip.mp4", 1024L, 100L)
        val normalWebm = SeekIndexCacheKey("/storage/emulated/0/Movies/clip.webm", 2048L, 200L)
        val encBin = SeekIndexCacheKey("/storage/emulated/0/Calypso Gallery/Secrets/.fv_1.bin", 4096L, 300L)
        val encFenc = SeekIndexCacheKey("/storage/emulated/0/Calypso Gallery/Secrets/clip.fenc", 8192L, 400L)

        SeekableExtractorsFactory.putCachedResult(normalMp4, CachedSeekResult.Hit(dummyChunk))
        SeekableExtractorsFactory.putCachedResult(normalWebm, CachedSeekResult.Hit(dummyChunk))
        SeekableExtractorsFactory.putCachedResult(encBin, CachedSeekResult.Hit(dummyChunk))
        SeekableExtractorsFactory.putCachedResult(encFenc, CachedSeekResult.Hit(dummyChunk))

        SeekableExtractorsFactory.clearEncryptedCache()

        assertNotNull(SeekableExtractorsFactory.getCachedResult(normalMp4))
        assertNotNull(SeekableExtractorsFactory.getCachedResult(normalWebm))
        assertNull(SeekableExtractorsFactory.getCachedResult(encBin))
        assertNull(SeekableExtractorsFactory.getCachedResult(encFenc))

        // Backgrounding / screen-off outside a vault session must preserve normal seek caches,
        // while backgrounding inside an unlocked vault wipes encrypted entries.
        val tempRoot = java.nio.file.Files.createTempDirectory("video_session_cache_test").toFile()
        try {
            val prefs = LinkedHashMap<String, Any?>()
            val testContext = object : ContextWrapper(null) {
                private val sp = object : SharedPreferences {
                    override fun getAll(): MutableMap<String, *> = prefs.toMutableMap()
                    override fun getString(key: String?, defValue: String?): String? = prefs[key] as? String ?: defValue
                    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
                    override fun getInt(key: String?, defValue: Int): Int = prefs[key] as? Int ?: defValue
                    override fun getLong(key: String?, defValue: Long): Long = prefs[key] as? Long ?: defValue
                    override fun getFloat(key: String?, defValue: Float): Float = prefs[key] as? Float ?: defValue
                    override fun getBoolean(key: String?, defValue: Boolean): Boolean = prefs[key] as? Boolean ?: defValue
                    override fun contains(key: String?): Boolean = prefs.containsKey(key)
                    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
                    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
                    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                        private val pending = LinkedHashMap<String, Any?>()
                        private var clearAll = false
                        override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
                        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
                        override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
                        override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
                        override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
                        override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
                        override fun remove(key: String?) = apply { if (key != null) pending[key] = this }
                        override fun clear() = apply { clearAll = true }
                        override fun commit(): Boolean { apply(); return true }
                        override fun apply() {
                            if (clearAll) prefs.clear()
                            pending.forEach { (k, v) -> if (v === this) prefs.remove(k) else prefs[k] = v }
                        }
                    }
                }
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = sp
                override fun getFilesDir(): File = File(tempRoot, "files").apply { mkdirs() }
                override fun getCacheDir(): File = File(tempRoot, "cache").apply { mkdirs() }
                override fun getExternalCacheDir(): File = File(tempRoot, "ext_cache").apply { mkdirs() }
                override fun getPackageName(): String = "org.fossify.gallery.debug"
            }

            VaultSessionManager.onVaultExited()
            CryptoManager.clearKeys()
            SeekableExtractorsFactory.putCachedResult(normalMp4, CachedSeekResult.Hit(dummyChunk))

            VaultSessionManager.onScreenOff(testContext)
            VaultSessionManager.onAppBackgrounded(testContext)
            assertNotNull(SeekableExtractorsFactory.getCachedResult(normalMp4))

            val vaultDir = File(tempRoot, "Secrets").apply { mkdirs() }
            File(vaultDir, VAULT_HEADER_FILE).writeBytes("FGVLT".toByteArray(Charsets.US_ASCII) + ByteArray(32))
            testContext.config.vaultRootPath = vaultDir.absolutePath
            testContext.config.temporarilyShowHidden = true
            CryptoManager.cachedVaultRoot = vaultDir
            CryptoManager.unlockedMasterKey = ByteArray(32) { 5 }
            SeekableExtractorsFactory.putCachedResult(encBin, CachedSeekResult.Hit(dummyChunk))

            VaultSessionManager.onAppBackgrounded(testContext)
            assertFalse(CryptoManager.isVaultUnlocked)
            assertNull(SeekableExtractorsFactory.getCachedResult(encBin))
            assertNotNull(SeekableExtractorsFactory.getCachedResult(normalMp4))
        } finally {
            VaultSessionManager.onVaultExited()
            CryptoManager.clearKeys()
            CryptoManager.cachedVaultRoot = null
            tempRoot.deleteRecursively()
        }
    }
}
