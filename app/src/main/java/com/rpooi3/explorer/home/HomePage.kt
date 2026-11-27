package com.rpooi3.explorer.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rpooi3.explorer.profile.ProfilePrefs

/**
 * The built-in home page ("new tab page").
 *
 * The home page is a native view, not a website. A tab shows it whenever the
 * tab itself is on the blank page [URL]; the web view underneath stays empty.
 * That keeps the page inside the normal tab model: it is part of the back
 * stack, it is restored with the other tabs, and "open the start page" simply
 * loads [URL].
 */
object HomePage {

    /** The address a tab is on while it shows the home page. */
    const val URL = "about:blank"

    /** Preference holding the user's start page. Empty means the selected built-in home page. */
    const val KEY_START_PAGE = "favoriteURL"

    /** The one preference that selects which of the two built-in home pages is drawn. */
    const val KEY_STYLE = "home_style"

    /** The two built-in home pages. Keep these values stable: they are stored per profile. */
    const val STYLE_NEW = "new"
    const val STYLE_CLASSIC = "classic"

    /** Start page that versions before 0.1.0 used (and stored) by default: the upstream wiki. */
    private const val LEGACY_START_PAGE = "https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki"
    private const val KEY_MIGRATED = "home_page_migrated"

    /**
     * Moves an installation of 0.0.1 over to the home page. Runs once.
     *
     * 0.0.1 wrote its default start page into the preferences as soon as the
     * settings screen was opened, so the stored value says nothing about what
     * the user wanted. If it is still that default, it is cleared. Any other
     * address was chosen by the user and stays.
     */
    @JvmStatic
    fun migrate(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val stillOldDefault = prefs.getString(KEY_START_PAGE, null)?.trim() == LEGACY_START_PAGE
        prefs.edit {
            putBoolean(KEY_MIGRATED, true)
            if (stillOldDefault) remove(KEY_START_PAGE)
        }
    }

    /** Whether [url] is the address of the home page. */
    @JvmStatic
    fun isHomeUrl(url: String?): Boolean = url != null && url.trim().equals(URL, ignoreCase = true)

    /**
     * Whether a tab should show the home page.
     *
     * @param pendingUrl address of a load that was requested or has started
     *   but not finished, or `null` when nothing is loading
     * @param committedUrl what `WebView.getUrl()` reports
     *
     * A pending load wins, so the home page disappears the moment the user
     * opens something and does not flash up in a new tab that is still
     * loading its first page. A tab that has never loaded anything
     * (`committedUrl == null`) counts as home.
     */
    @JvmStatic
    fun isShowing(pendingUrl: String?, committedUrl: String?): Boolean =
        if (pendingUrl != null) isHomeUrl(pendingUrl) else committedUrl == null || isHomeUrl(committedUrl)

    /** The selected built-in page, read from the active profile. */
    @JvmStatic
    fun style(context: Context): String = normalizeStyle(ProfilePrefs.settings(context).getString(KEY_STYLE, null))

    /** Stores the selected built-in page in the active profile. */
    @JvmStatic
    fun setStyle(context: Context, style: String) {
        ProfilePrefs.settings(context).edit { putString(KEY_STYLE, normalizeStyle(style)) }
    }

    /** Whether the New built-in page is selected for the active profile. */
    @JvmStatic
    fun isNewStyle(context: Context): Boolean = style(context) == STYLE_NEW

    /** Normalizes old, missing, or otherwise invalid values to the New page. */
    @JvmStatic
    fun normalizeStyle(style: String?): String = if (style == STYLE_CLASSIC) STYLE_CLASSIC else STYLE_NEW

    /**
     * The page to open for a new tab and at start-up: the address the user
     * chose as start page, or the selected built-in home page if none is set.
     */
    @JvmStatic
    fun startUrl(context: Context): String = startUrl(ProfilePrefs.settings(context))

    /** The same lookup for callers that already hold the active profile preferences. */
    @JvmStatic
    fun startUrl(prefs: SharedPreferences): String {
        val custom = prefs.getString(KEY_START_PAGE, "")?.trim().orEmpty()
        return custom.ifEmpty { URL }
    }
}
