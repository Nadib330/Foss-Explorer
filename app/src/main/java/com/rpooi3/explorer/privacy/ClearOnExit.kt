/*
 * Explorer: what is deleted when the browser is closed.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 8 (item 3): Settings > Privacy and security has a section of its own, **Advanced**,
 * with the switches of "Clear data on exit": the cache, the open tabs, the history, the web storage
 * and the app cache. Nothing of it is a new kind of deletion — each switch is one of the choices
 * the "Delete browsing data" screen has, made once and kept, and it runs through the same
 * [com.rpooi3.explorer.unit.DeleteUtils] that screen uses, so clearing on exit touches exactly what
 * clearing by hand touches (the profile in front, and no other).
 *
 * The exit itself is in [com.rpooi3.explorer.activity.BrowserActivity.exitBrowser]: the switches are
 * read there, the work is done, and only then is the browser closed.
 */
package com.rpooi3.explorer.privacy

import com.rpooi3.explorer.profile.ProfilePrefs
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.unit.DeleteUtils
import java.io.File

object ClearOnExit {

    /** The pages and pictures a site kept for a faster load. */
    const val KEY_CACHE = "clear_on_exit_cache"

    /** Every tab is closed, so the next start begins with one new tab. */
    const val KEY_TABS = "clear_on_exit_tabs"

    /** The list of the pages that were visited. */
    const val KEY_HISTORY = "clear_on_exit_history"

    /** Cookies, local storage, IndexedDB: what a website keeps on this device. */
    const val KEY_WEB_STORAGE = "clear_on_exit_webstorage"

    /** The files Explorer kept for itself in its cache folder. */
    const val KEY_APP_CACHE = "clear_on_exit_appcache"

    /** The switches, in the order the screen shows them. */
    @JvmStatic
    val KEYS: List<String> = listOf(
        KEY_CACHE, KEY_TABS, KEY_HISTORY, KEY_WEB_STORAGE, KEY_APP_CACHE,
    )

    /** Whether [key] is one of the switches. */
    @JvmStatic
    fun isKey(key: String?): Boolean = key != null && KEYS.contains(key)

    /** Whether anything is chosen to be deleted at all. */
    @JvmStatic
    fun isAnyChosen(prefs: SharedPreferences): Boolean = KEYS.any { prefs.getBoolean(it, false) }

    /** Whether [key] is switched on. */
    @JvmStatic
    fun isOn(prefs: SharedPreferences, key: String): Boolean = prefs.getBoolean(key, false)

    /**
     * Deletes what was chosen and nothing else. [closeTabs] is the browser's own way of closing
     * a tab, so that the tabs are closed with the same bookkeeping as by hand.
     *
     * @return the keys that were carried out, for the tests and the log
     */
    @JvmStatic
    fun run(
        context: Context,
        prefs: SharedPreferences = ProfilePrefs.settings(context),
        closeTabs: Runnable? = null,
    ): List<String> {
        val done = ArrayList<String>()
        val cache = prefs.getBoolean(KEY_CACHE, false)
        val history = prefs.getBoolean(KEY_HISTORY, false)
        val storage = prefs.getBoolean(KEY_WEB_STORAGE, false)
        if (cache || history || storage) {
            try {
                // The same three choices as on the "Delete browsing data" screen; the rest of it
                // (settings, other profiles) is never touched by leaving the browser.
                DeleteUtils.executeDeletion(
                    context,
                    cache,          // the cache of the pages
                    storage,        // the cookies of the profile in front
                    storage,        // the web storage and the databases of the profile in front
                    false,          // never the settings
                    false,          // never the data of the other profiles
                    history,        // the list of the pages that were visited
                )
                if (cache) done.add(KEY_CACHE)
                if (history) done.add(KEY_HISTORY)
                if (storage) done.add(KEY_WEB_STORAGE)
            } catch (e: Exception) {
                Log.w("ExplorerClearOnExit", "The data of the exit: " + e)
            }
        }
        if (prefs.getBoolean(KEY_APP_CACHE, false)) {
            clearAppCache(context)
            done.add(KEY_APP_CACHE)
        }
        if (prefs.getBoolean(KEY_TABS, false)) {
            try {
                closeTabs?.run()
                done.add(KEY_TABS)
            } catch (e: Exception) {
                Log.w("ExplorerClearOnExit", "The tabs of the exit: " + e)
            }
        }
        return done
    }

    /** The files Explorer kept in its own cache folders, and nothing of a profile's storage. */
    @JvmStatic
    fun clearAppCache(context: Context) {
        for (directory in listOfNotNull(context.cacheDir, context.externalCacheDir)) {
            val children = directory.listFiles() ?: continue
            for (child in children) {
                if (!child.name.startsWith("extensions", ignoreCase = true)) {
                    try {
                        child.deleteRecursively()
                    } catch (e: Exception) {
                        Log.w("ExplorerClearOnExit", "Left in the cache: " + child.name)
                    }
                }
            }
        }
        try {
            File(context.cacheDir, "webviewCache").deleteRecursively()
        } catch (e: Exception) {
            // A cache that stays is not a failure.
        }
    }

    /** The summary under a switch, so the row says what it deletes. */
    @JvmStatic
    fun summaryRes(key: String): Int = when (key) {
        KEY_CACHE -> com.rpooi3.explorer.R.string.clear_on_exit_cache_summary
        KEY_TABS -> com.rpooi3.explorer.R.string.clear_on_exit_tabs_summary
        KEY_HISTORY -> com.rpooi3.explorer.R.string.clear_on_exit_history_summary
        KEY_WEB_STORAGE -> com.rpooi3.explorer.R.string.clear_on_exit_webstorage_summary
        KEY_APP_CACHE -> com.rpooi3.explorer.R.string.clear_on_exit_appcache_summary
        else -> 0
    }
}
