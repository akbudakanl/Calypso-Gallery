package org.fossify.gallery.extensions

import android.view.ViewGroup
import android.widget.TextView
import org.fossify.gallery.R

/**
 * The bottom actions are TextViews with a compound drawable on top, so hiding the whole view
 * would hide the icon too. These helpers clear/restore only the text label, keeping the icon.
 */
fun ViewGroup.applyBottomActionLabelVisibility(showNames: Boolean) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child is TextView) {
            val original = child.getTag(R.id.bottom_action_original_label) as? CharSequence
                ?: child.text.also { child.setTag(R.id.bottom_action_original_label, it) }
            child.text = if (showNames) original else ""
        }
    }
}

/** Updates a label whose text changes at runtime (Encrypt/Decrypt), preserving the setting. */
fun TextView.setBottomActionLabel(label: CharSequence, showNames: Boolean) {
    setTag(R.id.bottom_action_original_label, label)
    text = if (showNames) label else ""
}
