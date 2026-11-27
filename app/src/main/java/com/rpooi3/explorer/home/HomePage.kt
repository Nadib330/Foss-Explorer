package com.rpooi3.explorer.home

import android.content.SharedPreferences
import androidx.core.content.edit

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

    /** Preference holding the user's start page. Empty means the home page. */
    const val KEY_START_PAGE = "favoriteURL"

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

    /**
     * The page to open for a new tab and at start-up: the address the user
     * chose as start page, or the home page if none is set.
     */
    @JvmStatic
    fun startUrl(prefs: SharedPreferences): String {
        val custom = prefs.getString(KEY_START_PAGE, "")?.trim().orEmpty()
        return custom.ifEmpty { URL }
    }
}
