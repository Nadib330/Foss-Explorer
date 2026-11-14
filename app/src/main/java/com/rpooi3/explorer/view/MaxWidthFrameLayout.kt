package com.rpooi3.explorer.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.rpooi3.explorer.R

/**
 * A [FrameLayout] that never grows wider than `android:maxWidth`. Keeps
 * content readable on tablets and in landscape instead of stretching it
 * from edge to edge.
 */
class MaxWidthFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val maxWidth: Int

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.MaxWidthFrameLayout)
        try {
            maxWidth = a.getDimensionPixelSize(R.styleable.MaxWidthFrameLayout_android_maxWidth, Int.MAX_VALUE)
        } finally {
            a.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val spec = if (mode != MeasureSpec.UNSPECIFIED && available > maxWidth) {
            MeasureSpec.makeMeasureSpec(maxWidth, mode)
        } else {
            widthMeasureSpec
        }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
