/*
 * Explorer: the colors of Settings > Appearance > Colors.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 9 (item 7): the list is Chromium's own — `kDynamicCustomizeChromeColors` of
 * `chrome/browser/ui/webui/cr_components/theme_color_picker/customize_chrome_colors.cc`
 * (BSD-3-Clause, © The Chromium Authors), the preset color schemes of the "Customize Chrome"
 * panel, with the seed color and the color-scheme variant of each of them, in the order
 * Chromium puts them in. The names are the names Chromium gives them (`IDS_NTP_COLORS_*`).
 *
 * 18.1.0 beta (items 2 and 3): the list was given the two colors Chromium puts in front of the
 * thirteen — **Default**, which is the palette of the system, and **Greyscale** — and it can now
 * hold a color of the user's own, which is seeded exactly like a preset is.
 *
 * What a scheme does here is what it does in Chrome: the seed is turned into a whole set of tones
 * by Material Color Utilities, and that set — and not the wallpaper, and not the palette of the
 * system — paints Explorer, which is why a color chosen here overrides the theme of the system.
 */
package com.rpooi3.explorer.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.StringRes
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.TonalPalette
import com.rpooi3.explorer.R

/** One preset color scheme of Chromium's color picker, or a color of the user's own. */
class ColorScheme(
    /** Chromium's id for it; [ColorSchemes.CUSTOM_ID] for a color of the user's own. */
    @JvmField val id: Int,
    @StringRes val name: Int,
    /** The seed color, exactly as `color_info.color` of Chromium. */
    @JvmField val seed: Int,
    /** Chromium's `BrowserColorVariant` for it: Tonal spot or Neutral. */
    @JvmField val neutral: Boolean,
) {
    /** Whether this is the colors of the system (Chromium's id 0). */
    val isSystem: Boolean get() = id == ColorSchemes.DEFAULT_ID

    /** Whether this is a color the user picked (18.1.0 beta, item 3). */
    val isCustom: Boolean get() = id == ColorSchemes.CUSTOM_ID

    /** Whether this is the scheme of the browser right now. */
    fun isChosen(prefs: SharedPreferences): Boolean = ColorSchemes.isChosen(prefs, this)

    companion object {
        /** The preference: the id of the chosen scheme; 0 is the default of the system. */
        const val KEY_COLOR = "theme_color_preset"

        /** The preference: whether the scheme is the neutral variant of its seed. */
        const val KEY_NEUTRAL = "theme_color_neutral"

        /** The preference: the seed of a color of the user's own (18.1.0 beta, item 3). */
        const val KEY_CUSTOM = "theme_color_custom"
    }
}

object ColorSchemes {

    /** Chromium's id of the palette of the system: the default of its picker. */
    const val DEFAULT_ID = 0

    /** Chromium's id of the greyscale scheme, the only one of its ids 0 and 1 it still offers. */
    const val GREYSCALE_ID = 1

    /** The id of a color of the user's own (18.1.0 beta, item 3): no id of Chromium's. */
    const val CUSTOM_ID = -1

    /**
     * The three colors of the swatch of the default, as Chromium paints it: the grey of its
     * surfaces, the blue of its accents, and the grey of its toolbar.
     */
    private const val DEFAULT_TOP = 0xFFE3E3E3.toInt()
    private const val DEFAULT_LEFT = 0xFF0B57D0.toInt()
    private const val DEFAULT_RIGHT = 0xFFC7C7C7.toInt()

    /** The colors of the system — Chromium's id 0, in front of every other color. */
    @JvmField
    val DEFAULT = ColorScheme(DEFAULT_ID, R.string.theme_colors_default, 0, true)

    /** The greyscale scheme: Chromium's id 1, which its picker still shows. */
    @JvmField
    val GREYSCALE = ColorScheme(GREYSCALE_ID, R.string.color_scheme_greyscale, Color.rgb(136, 136, 136), true)

    /**
     * Chromium's preset color schemes, in the order of `kDynamicCustomizeChromeColors`
     * (each `SkColorSetRGB(r, g, b)` below is that of the C++ file, the variant as it is there).
     */
    @JvmField
    val PRESETS: List<ColorScheme> = listOf(
        // 2: Blue, tonal spot
        ColorScheme(2, R.string.color_scheme_blue, Color.rgb(140, 171, 228), false),
        // 3: Cool grey, neutral
        ColorScheme(3, R.string.color_scheme_cool_grey, Color.rgb(140, 171, 228), true),
        // 4: Grey, neutral
        ColorScheme(4, R.string.color_scheme_grey, Color.rgb(136, 136, 136), true),
        // 5: Aqua, tonal spot
        ColorScheme(5, R.string.color_scheme_aqua, Color.rgb(38, 166, 154), false),
        // 6: Green, tonal spot
        ColorScheme(6, R.string.color_scheme_green, Color.rgb(0, 255, 0), false),
        // 7: Viridian, neutral
        ColorScheme(7, R.string.color_scheme_viridian, Color.rgb(135, 186, 129), true),
        // 8: Citron, tonal spot
        ColorScheme(8, R.string.color_scheme_citron, Color.rgb(250, 223, 115), false),
        // 9: Orange, tonal spot
        ColorScheme(9, R.string.color_scheme_orange, Color.rgb(255, 128, 0), false),
        // 10: Apricot, neutral
        ColorScheme(10, R.string.color_scheme_apricot, Color.rgb(252, 219, 201), true),
        // 11: Rose, tonal spot
        ColorScheme(11, R.string.color_scheme_rose, Color.rgb(243, 178, 190), false),
        // 12: Pink, neutral
        ColorScheme(12, R.string.color_scheme_pink, Color.rgb(243, 178, 190), true),
        // 13: Fuchsia, tonal spot
        ColorScheme(13, R.string.color_scheme_fuchsia, Color.rgb(255, 0, 255), false),
        // 14: Violet, tonal spot
        ColorScheme(14, R.string.color_scheme_violet, Color.rgb(229, 213, 252), false),
    )

    /** Every swatch of the picker, in Chromium's order: the two neutral ones, then the thirteen. */
    @JvmField
    val ALL: List<ColorScheme> = listOf(DEFAULT, GREYSCALE) + PRESETS

    /** The scheme with Chromium's [id], or null for an id nobody knows. */
    @JvmStatic
    fun byId(id: Int): ColorScheme? = ALL.firstOrNull { it.id == id }

    /** The scheme in front, or null when the colors of the system are used. */
    @JvmStatic
    fun chosen(prefs: SharedPreferences?): ColorScheme? {
        if (prefs == null) return null
        val id = prefs.getInt(ColorScheme.KEY_COLOR, DEFAULT_ID)
        if (id == DEFAULT_ID) return null
        val preset = byId(id)
        if (preset != null) return preset
        // A color of the user's own (18.1.0 beta, item 3).
        if (prefs.contains(ColorScheme.KEY_CUSTOM)) {
            return ColorScheme(
                CUSTOM_ID,
                R.string.theme_colors_custom,
                prefs.getInt(ColorScheme.KEY_CUSTOM, 0),
                false,
            )
        }
        return null
    }

    /** Whether [scheme] is the one that paints the browser now. */
    @JvmStatic
    fun isChosen(prefs: SharedPreferences?, scheme: ColorScheme?): Boolean {
        val now = chosen(prefs)
        if (scheme == null) return now == null
        if (scheme.isSystem) return now == null
        if (scheme.isCustom) return now != null && now.isCustom && now.seed == scheme.seed
        return now != null && !now.isCustom && now.id == scheme.id
    }

    /**
     * Chooses [scheme]; null — or the default — returns to the colors of the system, and a scheme
     * of the user's own is kept as its seed (18.1.0 beta, item 3).
     */
    @JvmStatic
    fun choose(prefs: SharedPreferences, scheme: ColorScheme?) {
        val editor = prefs.edit()
        if (scheme == null || scheme.isSystem) {
            editor.remove(ColorScheme.KEY_COLOR)
                .remove(ColorScheme.KEY_NEUTRAL)
                .remove(ColorScheme.KEY_CUSTOM)
        } else if (scheme.isCustom) {
            editor.putInt(ColorScheme.KEY_COLOR, CUSTOM_ID)
                .putInt(ColorScheme.KEY_CUSTOM, scheme.seed)
                .remove(ColorScheme.KEY_NEUTRAL)
        } else {
            editor.putInt(ColorScheme.KEY_COLOR, scheme.id)
                .putBoolean(ColorScheme.KEY_NEUTRAL, scheme.neutral)
                .remove(ColorScheme.KEY_CUSTOM)
        }
        editor.apply()
    }

    /** The name of [scheme] as the screens say it. */
    @JvmStatic
    fun name(context: Context, scheme: ColorScheme?): String =
        if (scheme == null || scheme.isSystem) context.getString(R.string.theme_colors_default)
        else context.getString(scheme.name)

    /** What the row of Settings > Appearance says: the scheme that paints the browser. */
    @JvmStatic
    fun summary(context: Context, prefs: SharedPreferences?): String {
        val scheme = chosen(prefs) ?: return context.getString(R.string.theme_colors)
        return name(context, scheme)
    }

    /** How much color a neutral variant of a scheme keeps: Material's own neutral palettes. */
    private const val NEUTRAL_CHROMA = 16.0

    /**
     * The three colors of the round swatch of a scheme, as Chromium paints it (18.1.0 beta,
     * item 2): the tone 90 of its seed on the top half (the surfaces of the light browser), the
     * tone 40 at the bottom left (its accents) and the tone 80 at the bottom right (what lies
     * between them).
     *
     * The variant of the scheme is part of it: a **neutral** scheme — *Cool grey*, *Grey*,
     * *Viridian*, *Apricot*, *Pink* — keeps only a little of the color of its seed, exactly as
     * Chromium's neutral color schemes do, so those swatches read grey beside the vivid ones. The
     * default has no seed of its own and keeps Chromium's neutral colors.
     *
     * @return three colors: top, bottom left, bottom right
     */
    @JvmStatic
    fun swatchColors(scheme: ColorScheme?): IntArray {
        if (scheme == null || scheme.isSystem) {
            return intArrayOf(DEFAULT_TOP, DEFAULT_LEFT, DEFAULT_RIGHT)
        }
        val hct = Hct.fromInt(scheme.seed)
        val chroma = if (scheme.neutral) minOf(hct.chroma, NEUTRAL_CHROMA) else hct.chroma
        val palette = TonalPalette.fromHueAndChroma(hct.hue, chroma)
        return intArrayOf(palette.tone(90), palette.tone(40), palette.tone(80))
    }

    /** The seed color of a scheme of the user's own, for the picker of item 3. */
    @JvmStatic
    fun customScheme(seed: Int): ColorScheme =
        ColorScheme(CUSTOM_ID, R.string.theme_colors_custom, seed, false)
}
