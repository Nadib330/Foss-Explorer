/*
 * Foss Explorer: how the home page looks (Settings > Home page customization).
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.home

import com.rpooi3.explorer.profile.ProfilePrefs
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R
import java.io.File
import java.util.concurrent.Executors

/**
 * The look of the home page: a picture of the user's as wallpaper, or a
 * background colour, and which parts of the page are shown. The picture is
 * copied into the app, so it stays when the original is moved or deleted.
 */
object HomeAppearance {
    /** Whether a wallpaper is set. The picture itself is the file [wallpaperFile]. */
    const val KEY_WALLPAPER = "home_wallpaper"

    /** When the wallpaper was set; a new value tells the home page to read the file again. */
    const val KEY_WALLPAPER_TIME = "home_wallpaper_time"

    /** Whether the wallpaper is darkened a little so that what stands on it can be read. */
    const val KEY_DIM = "home_wallpaper_dim"

    /**
     * The built-in home-page choice is owned by [HomePage]. These aliases keep
     * the appearance API source-compatible while ensuring every caller uses the
     * same key and the same normalization rules.
     */
    const val KEY_STYLE = HomePage.KEY_STYLE

    /** The compact built-in page. */
    const val STYLE_NEW = HomePage.STYLE_NEW

    /** The classic built-in page with the app name, search box and tiles. */
    const val STYLE_CLASSIC = HomePage.STYLE_CLASSIC

    /** The background colour; absent for the colour of the theme. */
    const val KEY_COLOR = "home_color"
    const val KEY_SHOW_LOGO = "home_show_logo"
    const val KEY_SHOW_SHORTCUTS = "home_show_shortcuts"

    // 18.0.0 beta 6 (item 8): more of the home page can be the user's own.
    /** Whether the search box is shown. */
    const val KEY_SHOW_SEARCH = "home_show_search"

    /** The name that stands at the top; absent means the name of the app. */
    const val KEY_TITLE = "home_title_text"

    /** How many site tiles stand in a row: 3, 4 or 5. */
    const val KEY_COLUMNS = "home_shortcuts_columns"

    // 18.0.0 beta 7 (item 5): a home page written by the user.
    /** Whether the user's own page is shown in place of the built-in one. */
    const val KEY_CODE_ON = "home_code_on"

    /** The code itself: HTML, with `<style>` and `<script>` in it as the user likes. */
    const val KEY_CODE = "home_code"

    /** Whether a page of code is there to be shown. */
    @JvmStatic
    fun hasCode(context: Context): Boolean = !code(context).isNullOrBlank()

    /** The code the user wrote, or null. */
    @JvmStatic
    fun code(context: Context): String? =
        prefs(context).getString(KEY_CODE, null)?.takeIf { it.isNotBlank() }

    /** Whether the code is to be shown instead of the built-in page. */
    @JvmStatic
    fun codeOn(context: Context): Boolean = prefs(context).getBoolean(KEY_CODE_ON, false) && hasCode(context)

    /** What a page of code gets when the user saved nothing yet: a page that says so. */
    @JvmStatic
    fun codeTemplate(context: Context): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            html, body { height: 100%; margin: 0; }
            body {
              display: flex; align-items: center; justify-content: center;
              font-family: system-ui, sans-serif; background: transparent; color: #1b1b1f;
            }
            @media (prefers-color-scheme: dark) { body { color: #e6e1e5; } }
            .page { text-align: center; padding: 24px; }
            h1 { font-size: 24px; font-weight: 600; margin: 0 0 8px; }
            p { margin: 0; opacity: .7; font-size: 14px; }
          </style>
        </head>
        <body>
          <div class="page">
            <h1>Your own home page</h1>
            <p>Write HTML, CSS and JavaScript in Settings &rsaquo; Home page customization.</p>
          </div>
        </body>
        </html>
    """.trimIndent()

    private const val FILE = "home_wallpaper.jpg"
    private const val TAG = "HomeAppearance"

    /** The longest side a wallpaper is kept with; larger pictures are scaled down. */
    const val MAX_SIDE = 2048

    /** A colour to choose from. [color] null stands for the colour of the theme. */
    class Swatch(@StringRes val name: Int, @ColorInt val color: Int?)

    /** The colours offered: the theme's own, the three creams and the olive of the logo, and a round of others. */
    @JvmField
    val PALETTE: List<Swatch> = listOf(
        Swatch(R.string.home_color_default, null),
        Swatch(R.string.home_color_cream, 0xFFF6F2E9.toInt()),
        Swatch(R.string.home_color_sand, 0xFFE6E0D0.toInt()),
        Swatch(R.string.home_color_olive, 0xFF5E603B.toInt()),
        Swatch(R.string.home_color_white, 0xFFFFFFFF.toInt()),
        Swatch(R.string.home_color_rose, 0xFFFCE4EC.toInt()),
        Swatch(R.string.home_color_sky, 0xFFE3F2FD.toInt()),
        Swatch(R.string.home_color_mint, 0xFFE0F2F1.toInt()),
        Swatch(R.string.home_color_lavender, 0xFFEDE7F6.toInt()),
        Swatch(R.string.home_color_teal, 0xFF00696A.toInt()),
        Swatch(R.string.home_color_navy, 0xFF1A2B4C.toInt()),
        Swatch(R.string.home_color_black, 0xFF000000.toInt()),
    )

    @JvmStatic
    fun prefs(context: Context): SharedPreferences = ProfilePrefs.settings(context)

    /** The selected built-in page; kept here as a compatibility facade for appearance callers. */
    @JvmStatic
    fun style(context: Context): String = HomePage.style(context)

    @JvmStatic
    fun setStyle(context: Context, style: String) = HomePage.setStyle(context, style)

    /** Whether the new home page is the one shown. */
    @JvmStatic
    fun isNewStyle(context: Context): Boolean = HomePage.isNewStyle(context)

    @JvmStatic
    fun shortcutColumns(context: Context): Int {
        val stored = prefs(context).getString(KEY_COLUMNS, null)?.toIntOrNull() ?: return COLUMNS_DEFAULT
        return stored.coerceIn(COLUMNS_MIN, COLUMNS_MAX)
    }

    const val COLUMNS_DEFAULT = 4
    const val COLUMNS_MIN = 3
    const val COLUMNS_MAX = 5

    @JvmStatic
    fun wallpaperFile(context: Context): File = File(context.filesDir, FILE)

    @JvmStatic
    fun hasWallpaper(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WALLPAPER, false) && wallpaperFile(context).isFile

    /** The chosen background colour, or null for the colour of the theme. */
    @JvmStatic
    fun color(prefs: SharedPreferences): Int? = if (prefs.contains(KEY_COLOR)) prefs.getInt(KEY_COLOR, 0) else null

    @JvmStatic
    fun setColor(prefs: SharedPreferences, color: Int?) {
        prefs.edit { if (color == null) remove(KEY_COLOR) else putInt(KEY_COLOR, color) }
    }

    /** The name of the chosen colour as the settings show it. */
    @JvmStatic
    fun colorName(context: Context): String {
        val color = color(prefs(context))
        val swatch = PALETTE.firstOrNull { it.color == color } ?: PALETTE.first()
        return context.getString(swatch.name)
    }

    /**
     * Makes the picture at [uri] the wallpaper: reads it, scales it down to
     * [MAX_SIDE] if it is larger, and keeps a copy. Returns false if it is
     * not a picture that can be read. Not for the main thread.
     */
    @JvmStatic
    fun setWallpaper(context: Context, uri: Uri): Boolean {
        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the picture", e)
            null
        } ?: return false
        return setWallpaper(context, bitmap)
    }

    /** Makes [bitmap] the wallpaper. */
    @JvmStatic
    fun setWallpaper(context: Context, bitmap: Bitmap): Boolean = try {
        // Written beside the file and moved into place, so that a picture is never half there.
        val target = wallpaperFile(context)
        val temp = File(target.parentFile, "$FILE.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        prefs(context).edit { putBoolean(KEY_WALLPAPER, true).putLong(KEY_WALLPAPER_TIME, System.currentTimeMillis()) }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not keep the picture", e)
        false
    }

    @JvmStatic
    fun clearWallpaper(context: Context) {
        wallpaperFile(context).delete()
        prefs(context).edit { remove(KEY_WALLPAPER).remove(KEY_WALLPAPER_TIME) }
    }

    /** 18.3.0 beta (item 2): white in the light theme, black in the dark one. */
    @JvmStatic
    fun defaultGround(context: Context): Int =
        if (isNight(context)) Color.BLACK else Color.WHITE

    @JvmStatic
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Whether text on [background] should be light. */
    @JvmStatic
    fun needsLightText(@ColorInt background: Int): Boolean = ColorUtils.calculateLuminance(background) < 0.4

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Gives the home page [root] (`include_home.xml`) its look. Cheap to call
     * again: the wallpaper is read only when it has changed.
     */
    @JvmStatic
    fun apply(root: View) {
        val context = root.context
        val prefs = prefs(context)
        val wallpaper = root.findViewById<ImageView>(R.id.home_wallpaper)
        val scrim = root.findViewById<View>(R.id.home_scrim)
        val title = root.findViewById<TextView>(R.id.home_title)
        val menu = root.findViewById<ImageView>(R.id.home_menu)
        val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)

        root.findViewById<View>(R.id.home_logo).isVisible = prefs.getBoolean(KEY_SHOW_LOGO, true)
        // 18.0.0 beta 6 (item 8): the search box, and the name the user gave the page.
        root.findViewById<View>(R.id.home_search)?.isVisible = prefs.getBoolean(KEY_SHOW_SEARCH, true)
        val titleText = prefs.getString(KEY_TITLE, null)?.trim()
        if (title != null) {
            title.text = if (titleText.isNullOrEmpty()) context.getString(R.string.app_name) else titleText
        }

        val hasWallpaper = hasWallpaper(context)
        // 18.3.0 beta (item 2): the ground of the home page is white in the light theme and black in
        // the dark one, unless the user picked a colour or a picture for it.
        val background = if (hasWallpaper) Color.BLACK else color(prefs) ?: defaultGround(context)
        root.setBackgroundColor(background)
        wallpaper.isVisible = hasWallpaper
        scrim.isVisible = hasWallpaper && prefs.getBoolean(KEY_DIM, true)

        // What stands directly on the background follows it: light on dark, dark on light.
        val custom = hasWallpaper || color(prefs) != null
        val light = hasWallpaper || needsLightText(background)
        title.setTextColor(if (!custom) onSurface else if (light) Color.WHITE else Color.BLACK)
        menu.imageTintList = ColorStateList.valueOf(if (!custom) onSurfaceVariant else if (light) Color.WHITE else Color.BLACK)

        if (!hasWallpaper) {
            wallpaper.setImageDrawable(null)
            wallpaper.tag = null
            return
        }
        val stamp = prefs.getLong(KEY_WALLPAPER_TIME, 0L)
        if (wallpaper.tag == stamp && wallpaper.drawable != null) return
        wallpaper.tag = stamp
        val file = wallpaperFile(context)
        executor.execute {
            val bitmap = try {
                BitmapFactory.decodeFile(file.path)
            } catch (e: OutOfMemoryError) {
                null
            }
            wallpaper.post { if (wallpaper.tag == stamp) wallpaper.setImageBitmap(bitmap) }
        }
    }
}
