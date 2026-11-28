package org.fossify.gallery.helpers

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import org.fossify.commons.helpers.isUpsideDownCakePlus

/**
 * Helper class to manage color modes for HDR and wide color gamut images.
 */
object ColorModeHelper {

    fun isGainmapSupported() = isUpsideDownCakePlus()

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun hasHdrContent(bitmap: Bitmap?): Boolean {
        return bitmap?.hasGainmap() == true
    }

    fun hasWideColorGamut(bitmap: Bitmap?): Boolean {
        return bitmap?.colorSpace?.isWideGamut == true
    }

    fun setColorMode(activity: Activity, colorMode: Int) {
        val window = activity.window ?: return
        if (window.colorMode == colorMode) return
        window.setColorMode(colorMode)
    }

    fun setColorModeForImage(activity: Activity, bitmap: Bitmap?, ultraHdr: Boolean = true) {
        setColorMode(
            activity = activity,
            colorMode = when {
                ultraHdr && isGainmapSupported() && hasHdrContent(bitmap) -> ActivityInfo.COLOR_MODE_HDR
                hasWideColorGamut(bitmap) -> ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                else -> ActivityInfo.COLOR_MODE_DEFAULT
            }
        )
    }

    fun resetColorMode(activity: Activity?) {
        activity?.let { setColorMode(it, ActivityInfo.COLOR_MODE_DEFAULT) }
    }

    fun shouldPreferGesturesZoomForHdr(drawable: Drawable?, ultraHdrEnabled: Boolean): Boolean {
        if (!ultraHdrEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return false
        return hasHdrContent(bitmap)
    }
}
