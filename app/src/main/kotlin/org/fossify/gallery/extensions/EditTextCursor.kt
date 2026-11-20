package org.fossify.gallery.extensions

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.widget.TextView
import androidx.core.graphics.ColorUtils

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
        // A non-zero resource id makes TextView recreate the library's green cursor later.
        runCatching {
            val resField = TextView::class.java.getDeclaredField("mCursorDrawableRes")
            resField.isAccessible = true
            resField.setInt(this, 0)
        }
        // Pre-36 the cursor lives in TextView's private Editor, not in TextView itself.
        val applied = runCatching {
            val editorField = TextView::class.java.getDeclaredField("mEditor")
            editorField.isAccessible = true
            val editor = editorField.get(this) ?: return@runCatching
            val cursorField = editor.javaClass.getDeclaredField("mCursorDrawable")
            cursorField.isAccessible = true
            cursorField.set(editor, arrayOf(cursor, cursor))
        }.isSuccess
        if (!applied) {
            runCatching {
                val field = TextView::class.java.getDeclaredField("mCursorDrawable")
                field.isAccessible = true
                field.set(this, arrayOf(cursor, cursor))
            }
        }
    }

    highlightColor = ColorUtils.setAlphaComponent(color, 60)
}
