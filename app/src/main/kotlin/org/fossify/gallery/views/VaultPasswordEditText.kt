package org.fossify.gallery.views

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.ActionMode
import android.view.ContextMenu
import android.view.DragEvent
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.textclassifier.TextClassifier
import androidx.core.view.ViewCompat
import com.google.android.material.textfield.TextInputEditText

/**
 * Hardened password field for vault credentials.
 *
 * Disables selection ranges, copy/cut/paste, drag-and-drop, receive-content insertion,
 * autofill/text-assist menus, keyboard shortcuts, and IME extract UI / personalized learning
 * while preserving normal character entry, cursor placement, backspace, and password visibility toggle.
 */
class VaultPasswordEditText : TextInputEditText {

    constructor(context: Context) : super(context) {
        hardenClipboardAndSelection()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        hardenClipboardAndSelection()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    ) {
        hardenClipboardAndSelection()
    }

    private fun hardenClipboardAndSelection() {
        isLongClickable = false
        customSelectionActionModeCallback = NO_OP_ACTION_MODE_CALLBACK
        customInsertionActionModeCallback = NO_OP_ACTION_MODE_CALLBACK
        setTextClassifier(TextClassifier.NO_OP)
        ViewCompat.setOnReceiveContentListener(this, BLOCKED_RECEIVE_MIME_TYPES) { _, _ -> null }
    }

    override fun isSuggestionsEnabled(): Boolean = false

    override fun performLongClick(): Boolean = false

    override fun performLongClick(x: Float, y: Float): Boolean = false

    override fun onCreateContextMenu(menu: ContextMenu?) = Unit

    override fun onDragEvent(event: DragEvent?): Boolean = false

    override fun onTextContextMenuItem(id: Int): Boolean {
        return if (id in BLOCKED_CONTEXT_MENU_IDS) true else super.onTextContextMenuItem(id)
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val isCtrlShortcut = event.isCtrlPressed && keyCode in BLOCKED_CTRL_KEYCODES
        if (isCtrlShortcut || keyCode in DEDICATED_CLIPBOARD_KEYCODES) {
            return true
        }
        return super.onKeyShortcut(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode in DEDICATED_CLIPBOARD_KEYCODES) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        val currentText = text
        if (currentText != null && selStart != selEnd) {
            setSelection(selEnd.coerceIn(0, currentText.length))
            return
        }
        super.onSelectionChanged(selStart, selEnd)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        outAttrs.imeOptions = outAttrs.imeOptions or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return connection
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY)
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CUT)
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE)
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action in BLOCKED_ACCESSIBILITY_ACTIONS) {
            return false
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        // View.setOnReceiveContentListener rejects MIME types starting with '*' (API 31+):
        // "A MIME type set here must not start with *". Category wildcards such as "text/*"
        // are valid and, since the listener returns null, all of these insertions are dropped.
        val BLOCKED_RECEIVE_MIME_TYPES = arrayOf(
            "text/*",
            "image/*",
            "video/*",
            "audio/*",
            "application/*",
        )

        val NO_OP_ACTION_MODE_CALLBACK = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }

        val BLOCKED_CONTEXT_MENU_IDS = setOf(
            android.R.id.cut,
            android.R.id.copy,
            android.R.id.copyUrl,
            android.R.id.paste,
            android.R.id.pasteAsPlainText,
            android.R.id.shareText,
            android.R.id.selectAll,
            android.R.id.startSelectingText,
            android.R.id.stopSelectingText,
            android.R.id.textAssist,
            android.R.id.autofill,
        )

        val BLOCKED_CTRL_KEYCODES = setOf(
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_A,
        )

        val DEDICATED_CLIPBOARD_KEYCODES = setOf(
            KeyEvent.KEYCODE_COPY,
            KeyEvent.KEYCODE_CUT,
            KeyEvent.KEYCODE_PASTE,
        )

        val BLOCKED_ACCESSIBILITY_ACTIONS = setOf(
            AccessibilityNodeInfo.ACTION_COPY,
            AccessibilityNodeInfo.ACTION_CUT,
            AccessibilityNodeInfo.ACTION_PASTE,
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
        )
    }
}
