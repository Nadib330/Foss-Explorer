/*
 * Explorer: the disc that stands for a profile.
 * Own code, GPL-3.0.
 *
 * Chrome marks a profile with a coloured ring around its avatar. Explorer draws the same: a circle
 * in the colour of the profile with the first letter of its name in it, and a ring around the one
 * that is in front.
 */
package com.rpooi3.explorer.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors

/** The colours of the profiles, and the soft version a row is drawn in. */
object ProfileColour {

    @JvmStatic
    fun of(profile: BrowserProfile): Int =
        Profiles.COLOURS.getOrElse(profile.colour) { Profiles.COLOURS[0] }

    /** The colour of [profile], mixed well into the surface: the background of its card. */
    @JvmStatic
    fun soft(context: Context, profile: BrowserProfile): Int {
        val surface = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            Color.WHITE,
        )
        return blend(of(profile), surface, 0.14f)
    }

    private fun blend(from: Int, to: Int, ratio: Float): Int {
        fun channel(shift: Int) =
            (((from shr shift) and 0xFF) * ratio + ((to shr shift) and 0xFF) * (1 - ratio)).toInt()
        return Color.argb(0xFF, channel(16), channel(8), channel(0))
    }
}

/** The disc of a profile: a filled circle with its letter in it. */
class ProfileAvatarDrawable(private val profile: BrowserProfile) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ProfileColour.of(profile) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun draw(canvas: Canvas) {
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawCircle(cx, cy, radius, paint)
        text.textSize = radius * 0.95f
        canvas.drawText(profile.initial, cx, cy - (text.descent() + text.ascent()) / 2f, text)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        text.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        text.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** The disc of [profile] as a view, [sizeDp] across, with a ring when it is the one in front. */
class ProfileAvatarView(
    context: Context,
    private val profile: BrowserProfile,
    sizeDp: Int = 40,
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density

    init {
        val size = (sizeDp * density).toInt()
        layoutParams = LayoutParams(size, size)
        // 18.0.0 beta 2: the picture the user chose, in a circle; the letter in the colour of the
        // profile where there is none.
        addView(
            TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.42f)
                typeface = Typeface.DEFAULT_BOLD
                bind(profile, sizeDp)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        contentDescription = profile.name
    }

    /** Draws the ring of the profile in front, or takes it away. */
    fun markActive(active: Boolean) {
        val stroke = (2.5f * density).toInt().coerceAtLeast(2)
        val inset = (3 * density).toInt()
        background = if (!active) {
            null
        } else {
            InsetDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(stroke, ProfileColour.of(profile))
                },
                inset,
                inset,
                inset,
                inset,
            )
        }
    }

    private fun TextView.bind(profile: BrowserProfile, sizeDp: Int) {
        if (ProfilePictures.has(context, profile)) {
            text = ""
            background = ProfilePictures.drawable(context, profile, sizeDp)
        } else {
            text = profile.initial
            background = ProfileAvatarDrawable(profile)
        }
    }
}
