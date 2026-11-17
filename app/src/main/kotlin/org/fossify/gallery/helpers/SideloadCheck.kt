package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.SIDELOADING_FALSE
import org.fossify.gallery.extensions.config

/**
 * Neutralizes Fossify Commons' "sideloaded / fake app" detection.
 *
 * Commons decides this by probing for a marker drawable (R.drawable.ic_camera_vector) that
 * is stripped from our independent build. It then shows an "App Corrupt" dialog linking to
 * the Play Store listing built from R.string.package_name. The check does not inspect the
 * signing key, installer, namespace or applicationId.
 *
 * The verdict is cached in the shared "Prefs" preferences as appSideloadingStatus and is
 * read by BaseSplashActivity. Marking it SIDELOADING_FALSE in Application.onCreate() keeps
 * every entry point from running the probe.
 */
object SideloadCheck {
    fun disable(context: Context) {
        context.config.appSideloadingStatus = SIDELOADING_FALSE
    }
}
