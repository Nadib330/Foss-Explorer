package com.rpooi3.explorer.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import java.util.Locale

/**
 * The first letter of a site name, centred. Stands in for a site icon that
 * has not been downloaded yet.
 */
class MonogramDrawable(label: String, textColor: Int) : Drawable() {

    val letter: String = label.trim().firstOrNull { it.isLetterOrDigit() }
        ?.toString()?.uppercase(Locale.getDefault()) ?: "?"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun draw(canvas: Canvas) {
        val area = bounds
        paint.textSize = area.height() * 0.75f
        val baseline = area.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, area.exactCenterX(), baseline, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
