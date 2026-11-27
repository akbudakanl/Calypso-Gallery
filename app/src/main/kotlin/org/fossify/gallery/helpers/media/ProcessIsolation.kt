package org.fossify.gallery.helpers.media

import android.os.Build
import android.os.Process

/**
 * Identifies whether the current OS process is running inside an Android `isolatedProcess="true"` sandbox.
 */
object ProcessIsolation {
    private const val PER_USER_RANGE = 100_000
    private const val FIRST_ISOLATED_UID = 99_000
    private const val LAST_ISOLATED_UID = 99_999

    fun isIsolatedProcess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Process.isIsolated()
        } else {
            val appId = Process.myUid() % PER_USER_RANGE
            appId in FIRST_ISOLATED_UID..LAST_ISOLATED_UID
        }
    }
}
