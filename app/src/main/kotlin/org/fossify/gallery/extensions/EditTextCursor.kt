package org.fossify.gallery.extensions

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.widget.TextView

/** Replaces the library's green text cursor with one drawn in the active palette color. */
fun TextView.applyThemedCursor(color: Int) {
    val density = resources.displayMetrics.density
    val cursor = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        setSize((2f * density).toInt().coerceAtLeast(1), 0)
    }
    if (Build.VERSION.SDK_INT >= 36) {
        textCursorDrawable = cursor
    } else {
        runCatching {
            val field = TextView::class.java.getDeclaredField("mCursorDrawable")
            field.isAccessible = true
            field.set(this, arrayOf(cursor, cursor))
        }
    }
}
