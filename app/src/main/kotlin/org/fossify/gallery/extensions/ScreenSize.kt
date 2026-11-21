package org.fossify.gallery.extensions

import android.content.Context

/**
 * Android 16 (API 36) ignores screenOrientation / setRequestedOrientation() and
 * resizability restrictions on displays with a smallest width of 600dp or more
 * (tablets, foldable inner screens, desktop windowing).
 *
 * Hide the "force orientation" controls there instead of offering actions the
 * platform silently ignores. See https://developer.android.com/about/versions/16/behavior-changes-16
 */
val Context.isLargeScreen: Boolean
    get() = resources.configuration.smallestScreenWidthDp >= 600
