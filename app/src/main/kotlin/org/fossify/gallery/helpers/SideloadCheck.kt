package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.SIDELOADING_FALSE
import org.fossify.gallery.extensions.config

/**
 * Neutralizes Fossify Commons' "sideloaded / fake app" detection at runtime.
 *
 * Commons decides sideloading by probing for a marker drawable (R.drawable.ic_camera_vector)
 * that is stripped from our independent build, and caches the verdict in the shared "Prefs"
 * preferences as appSideloadingStatus (read by BaseSplashActivity). Marking it SIDELOADING_FALSE
 * in Application.onCreate() keeps every entry point from running the probe.
 *
 * Separate from that, Commons also performs package-name checks in BaseSimpleActivity.onCreate()
 * (showModdedAppWarning) and Compose AppTheme (FakeVersionCheck / fakeVersionCheck). Those
 * checks are stripped at bytecode instrumentation time by NeutralizeCommonsFakeChecksVisitorFactory
 * in app/build.gradle.kts across all build variants.
 */
object SideloadCheck {
    fun disable(context: Context) {
        context.config.appSideloadingStatus = SIDELOADING_FALSE
    }
}
