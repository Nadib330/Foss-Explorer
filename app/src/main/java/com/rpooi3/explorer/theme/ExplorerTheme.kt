package com.rpooi3.explorer.theme

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.rpooi3.explorer.R

/**
 * Decides how Foss Explorer is themed.
 *
 * - Android 12+: Material You. Colours follow the wallpaper unless the user
 *   switches dynamic colour off in the settings.
 * - Android 8 - 11, or dynamic colour switched off: the static Foss Explorer
 *   brand palette defined in `res/values/colors.xml`.
 *
 * The light/dark choice (`sp_theme`) works in both cases. That matters on
 * Android 8 and 9, which have no system-wide dark mode to follow.
 */
object ExplorerTheme {

    private const val KEY_THEME = "sp_theme"
    private const val KEY_DYNAMIC_COLOR = "useDynamicColor"

    private const val THEME_SYSTEM = "1"
    private const val THEME_LIGHT = "2"
    private const val THEME_DARK = "3"

    /** Whether this device can derive a colour scheme from the wallpaper. */
    @JvmStatic
    fun isDynamicColorAvailable(): Boolean = DynamicColors.isDynamicColorAvailable()

    /** Whether wallpaper colours are both available and wanted by the user. */
    @JvmStatic
    fun usesDynamicColor(context: Context): Boolean =
        isDynamicColorAvailable() &&
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_DYNAMIC_COLOR, true)

    /** Applies the stored light/dark preference to the whole process. */
    @JvmStatic
    fun applyNightMode(context: Context) {
        val mode = when (themePreference(context)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Sets the activity theme. Call before `setContentView`. */
    @JvmStatic
    fun applyTo(activity: Activity) {
        val theme = if (usesDynamicColor(activity)) {
            when (themePreference(activity)) {
                THEME_LIGHT -> R.style.AppTheme_wallpaper_day
                THEME_DARK -> R.style.AppTheme_wallpaper_night
                else -> R.style.AppTheme_wallpaper
            }
        } else {
            R.style.AppTheme
        }
        activity.setTheme(theme)
    }

    private fun themePreference(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
}
