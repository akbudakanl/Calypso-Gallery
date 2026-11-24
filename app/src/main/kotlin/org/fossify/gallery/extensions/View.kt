package org.fossify.gallery.extensions

import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FONT_TYPE_SYSTEM_DEFAULT
import org.fossify.commons.helpers.FontHelper

/**
 * Re-applies the current app font to an already inflated view hierarchy.
 *
 * Commons applies fonts only while inflating views, so a screen that stays alive while the font
 * changes in Appearance keeps rendering the old typeface until it is recreated. The system font
 * is set explicitly because Commons' helper skips Typeface.DEFAULT.
 */
fun View.refreshFontRecursively() {
    val config = context.config
    val typeface = if (config.fontType == FONT_TYPE_SYSTEM_DEFAULT && config.fontName.isEmpty()) {
        Typeface.DEFAULT
    } else {
        FontHelper.getTypeface(context, config.fontType, config.fontName) ?: Typeface.DEFAULT
    }
    fun apply(view: View) {
        if (view is TextView) {
            val style = view.typeface?.style ?: Typeface.NORMAL
            view.typeface = Typeface.create(typeface, style)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                apply(view.getChildAt(i))
            }
        }
    }
    apply(this)
}

fun View.sendFakeClick(x: Float, y: Float) {
    val uptime = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(uptime, uptime, MotionEvent.ACTION_DOWN, x, y, 0)
    dispatchTouchEvent(event)
    event.action = MotionEvent.ACTION_UP
    dispatchTouchEvent(event)
}

fun View.showContentDescriptionOnLongClick() {
    setOnLongClickListener {
        val contentDescription = contentDescription
        if (contentDescription != null) {
            context.toast(contentDescription.toString())
        }
        true
    }
}
