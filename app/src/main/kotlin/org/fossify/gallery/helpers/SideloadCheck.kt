package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.helpers.SIDELOADING_FALSE
import org.fossify.gallery.extensions.config

/**
 * Central place for neutralizing Fossify Commons' "sideloaded / fake app" detection.
 *
 * Commons considers a build sideloaded when it is not signed with the official Fossify
 * signing key, which is always the case for this independently maintained fork with its
 * own applicationId. It then shows an "App Corrupt" dialog linking to the Play Store
 * listing built from R.string.package_name.
 *
 * The verdict is cached in the shared "Prefs" preferences as appSideloadingStatus and is
 * read by BaseSplashActivity as well as by every checkAppSideloading() call. Marking it as
 * SIDELOADING_FALSE once in Application.onCreate() disables the check at all entry points,
 * including the splash screen and the editor/viewer/wallpaper activities.
 */
object SideloadCheck {
    fun disable(context: Context) {
        context.config.appSideloadingStatus = SIDELOADING_FALSE
    }
}
