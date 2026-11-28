package org.fossify.gallery.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import com.google.android.material.textfield.TextInputLayout
import org.fossify.gallery.R
import org.fossify.gallery.helpers.AppearanceManager

/** Themes the caret with the active palette's accent colour. */
fun TextView.applyThemedCursor() = applyThemedCursor(
    AppearanceManager.resolve(context, context.config).primary
)

/**
 * Replaces the library's green text cursor with one drawn in the active palette color.
 *
 * Android's Editor caches the cursor Drawable on first use (`loadCursorDrawable()` only assigns
 * when its field is null) and ignores later `setTextCursorDrawable()` calls. Recolouring the
 * Drawable TextView handed out is therefore the only reliable way to change an already-focused
 * field; replacing it only works before the first draw.
 */
fun TextView.applyThemedCursor(color: Int) {
    val density = resources.displayMetrics.density
    fun newCursor() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        setSize((2f * density).toInt().coerceAtLeast(1), 0)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val current = textCursorDrawable
        if (current == null) {
            // No cached cursor yet: assign ours before Editor loads its default.
            textCursorDrawable = newCursor()
        } else {
            // Editor already holds this instance; mutate it so the cached draw follows the theme.
            current.mutate().setTint(color)
        }
    } else {
        // API 26-28 have no public setter; retain the legacy fallback.
        val cursor = newCursor()
        runCatching {
            val resField = TextView::class.java.getDeclaredField("mCursorDrawableRes")
            resField.isAccessible = true
            resField.setInt(this, 0)
        }
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
    applyThemedSelectionHandles(color)
    invalidate()
}

/**
 * The platform's text-selection handles are tinted with colorControlActivated, which is the
 * library's green accent in this fork. Replace them explicitly with theme-coloured copies.
 */
fun TextView.applyThemedSelectionHandles(color: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val tint = ColorStateList.valueOf(color)
    listOf(textSelectHandle, textSelectHandleLeft, textSelectHandleRight).forEach { existing ->
        existing?.mutate()?.setTintList(tint)
    }
    fun handle(resId: Int) = AppCompatResources.getDrawable(context, resId)?.mutate()?.apply {
        setTintList(tint)
    }
    handle(R.drawable.calypso_text_select_handle_middle)?.let { setTextSelectHandle(it) }
    handle(R.drawable.calypso_text_select_handle_left)?.let { setTextSelectHandleLeft(it) }
    handle(R.drawable.calypso_text_select_handle_right)?.let { setTextSelectHandleRight(it) }
}

/** Resolves the theme's text colour, matching [applyThemedCursor] in light and dark modes. */
fun TextView.themeTextColor(): Int = runCatching {
    val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
    val resolved = attrs.getColor(0, Color.WHITE)
    attrs.recycle()
    resolved
}.getOrDefault(Color.WHITE)

/**
 * Applies the palette-aware caret to every EditText in a hierarchy, including Material
 * TextInputEditText fields whose TextInputLayout repaints the caret with colorControlActivated
 * on focus. Dialogs inflate their own window, so each one has to call this on its root view.
 */
fun View.applyPaletteCursors(context: Context) {
    val color = AppearanceManager.resolve(context, context.config).primary
    fun walk(view: View) {
        if (view is EditText) {
            view.applyThemedCursor(color)
            var ancestor = view.parent
            while (ancestor is View) {
                if (ancestor is TextInputLayout) {
                    ancestor.setCursorColor(ColorStateList.valueOf(color))
                }
                ancestor = ancestor.parent
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                walk(view.getChildAt(index))
            }
        }
    }
    walk(this)
}
