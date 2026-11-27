package com.rpooi3.explorer.theme

import com.rpooi3.explorer.profile.ProfilePrefs
import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
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

    private const val THEME_SYSTEM = "1"
    private const val THEME_LIGHT = "2"
    private const val THEME_DARK = "3"

    /** Whether this device can derive a colour scheme from the wallpaper. */
    @JvmStatic
    fun isDynamicColorAvailable(): Boolean = DynamicColors.isDynamicColorAvailable()

    /**
     * Whether wallpaper colours are both available and wanted.
     *
     * 16.1.0 (item 10): dynamic colour is always on. The switch is gone from the appearance
     * settings; the colour of the wallpaper is used whenever the system offers it (Android 12
     * and later), and the preference an older version may have stored is ignored.
     */
    @JvmStatic
    fun usesDynamicColor(context: Context): Boolean = isDynamicColorAvailable()

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

    /**
     * Sets the activity theme. Call before `setContentView`.
     *
     * 18.0.0 beta 9 (item 7): a color chosen in Settings &gt; Appearance &gt; Colors comes first. It
     * is the seed color of one of Chromium's preset color schemes, and Material's content-based
     * dynamic colors turns it into the whole palette of the app — light and dark, surfaces and
     * accents — so the colors of the system (and of the wallpaper) are overridden, exactly as a
     * color of Chrome's own color picker overrides them.
     */
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
        applyChosenColor(activity)
    }

    /**
     * Paints [activity] with the color scheme that was chosen, if one was. The seed is enough:
     * Material derives the rest, and the light/dark choice of the theme still decides which side
     * of the palette is used.
     */
    @JvmStatic
    fun applyChosenColor(activity: Activity) {
        // 18.1.0 beta (item 3): a color of the user's own paints the browser as a preset does.
        val scheme = ColorSchemes.chosen(
            com.rpooi3.explorer.profile.ProfilePrefs.settings(activity),
        ) ?: return
        try {
            val options = DynamicColorsOptions.Builder()
                .setContentBasedSource(scheme.seed)
                .build()
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(activity, options)
            } else {
                // Android 8 to 11 have no palette of the system to replace — the browser paints
                // itself from its own colors there — and a theme cannot be given other colors
                // while it runs. What the browser paints itself is the bars around the page, and
                // they take the two tones of the color that was chosen.
                val dark = (activity.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                val tones = com.google.android.material.color.utilities.SchemeTonalSpot(
                    com.google.android.material.color.utilities.Hct.fromInt(scheme.seed),
                    dark,
                    0.0,
                )
                activity.window.statusBarColor = tones.primaryContainer
                activity.window.navigationBarColor = tones.primaryContainer
            }
        } catch (e: Throwable) {
            // A device without dynamic color keeps the theme it was given.
        }
    }

    private fun themePreference(context: Context): String =
        ProfilePrefs.settings(context)
            .getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
}
