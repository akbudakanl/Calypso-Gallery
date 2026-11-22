package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.SIDELOADING_FALSE
import org.fossify.gallery.extensions.config

/**
 * Neutralizes Fossify Commons' sideloaded/fake-app detection and automatic donation/rating
 * prompts at runtime.
 *
 * Commons decides sideloading by probing for a marker drawable (R.drawable.ic_camera_vector)
 * that is stripped from our independent build, and caches the verdict in the shared "Prefs"
 * preferences as appSideloadingStatus (read by BaseSplashActivity). Commons also tracks
 * delayed automatic support prompts (5-day rating prompt, 10-day donation prompt) via
 * wasRatePromptShown and wasDonationPromptShown. Marking these flags in Application.onCreate()
 * prevents every runtime entry point from triggering them.
 *
 * Separate from that, Commons also performs package-name checks in BaseSimpleActivity.onCreate()
 * (showModdedAppWarning), Compose AppTheme (FakeVersionCheck / fakeVersionCheck), automatic
 * support prompt checks (SupportPromptKt.showAutomaticSupportPromptIfEligible), and Fossify
 * Thank You global theme/icon probes. Those are stripped at bytecode instrumentation time by
 * NeutralizeCommonsFakeChecksVisitorFactory in app/build.gradle.kts across all build variants.
 */
object SideloadCheck {
    fun disable(context: Context) {
        context.config.apply {
            appSideloadingStatus = SIDELOADING_FALSE
            wasDonationPromptShown = true
            wasRatePromptShown = true
        }
    }
}
