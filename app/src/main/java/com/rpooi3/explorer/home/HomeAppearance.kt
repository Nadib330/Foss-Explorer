/*
 * Foss Explorer: how the home page looks (Settings > Home page customization).
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.home

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

    /** The background colour; absent for the colour of the theme. */
    const val KEY_COLOR = "home_color"
    const val KEY_SHOW_LOGO = "home_show_logo"
    const val KEY_SHOW_SHORTCUTS = "home_show_shortcuts"

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
    fun prefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

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

        val hasWallpaper = hasWallpaper(context)
        val background = if (hasWallpaper) Color.BLACK else color(prefs) ?: surface
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
