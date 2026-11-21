package org.fossify.gallery.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import org.fossify.gallery.R

/**
 * A [LinearLayout] that caps its measured width to [maxWidthPx] while still filling
 * narrower viewports cleanly. Used to keep centered content columns readable and well
 * proportioned on tablets, foldables, and landscape phones without affecting phone layouts.
 */
class MaxWidthLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var maxWidthPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MaxWidthLinearLayout, defStyleAttr, 0)
            maxWidthPx = a.getDimensionPixelSize(R.styleable.MaxWidthLinearLayout_maxWidth, 0)
            a.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val constrainedWidthSpec = if (maxWidthPx > 0) {
            val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            if (mode != MeasureSpec.UNSPECIFIED && measuredWidth > maxWidthPx) {
                MeasureSpec.makeMeasureSpec(maxWidthPx, mode)
            } else {
                widthMeasureSpec
            }
        } else {
            widthMeasureSpec
        }
        super.onMeasure(constrainedWidthSpec, heightMeasureSpec)
    }
}
