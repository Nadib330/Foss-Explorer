/*
 * Explorer: the round swatch of a color scheme.
 * Own code, GPL-3.0.
 *
 * 18.1.0 beta (item 2): this is the swatch of Chromium's own color picker — a circle cut into
 * three pieces: the top half in the light surfaces of the scheme, the bottom left in its accents
 * and the bottom right in the tone between them, with a hairline of the card showing through where
 * the three meet. Beta 9 drew a gradient instead, which is nothing of Chromium's.
 */
package com.rpooi3.explorer.theme

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** The three-tone circle of a color scheme. */
class ColorSwatch(private val colors: IntArray) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** How much of the circle the hairline between two pieces takes, in degrees. */
    private var seam = 1.6f

    /**
     * The three pieces of the circle, as the start angle, the sweep and the color of each of them:
     * the top half, the bottom left and the bottom right. The seam between two of them is what
     * makes the pieces read as three, as Chromium draws them.
     *
     * Angles of Android: 0° at three o'clock, growing clockwise, so 90° is six o'clock.
     */
    fun arcs(): Array<FloatArray> {
        val open = 180f - 2f * seam
        val quarter = 90f - 2f * seam
        return arrayOf(
            floatArrayOf(180f + seam, open, colors[0].toFloat()),
            floatArrayOf(90f + seam, quarter, colors[1].toFloat()),
            floatArrayOf(seam, quarter, colors[2].toFloat()),
        )
    }

    /** The seam, in degrees: a hairline that follows the size of the circle. */
    fun seam(radius: Float): Float = (12f / radius).coerceIn(0.8f, 2.4f)

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = minOf(rect.width(), rect.height()) / 2f
        if (radius <= 0f) return
        seam = seam(radius)
        for (arc in arcs()) {
            paint.color = arc[2].toInt()
            canvas.drawArc(rect, arc[0], arc[1], true, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
    }
}
