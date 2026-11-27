package com.rpooi3.explorer.home

import com.rpooi3.explorer.profile.ProfilePrefs
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.database.RecordAction
import java.util.Locale

/**
 * Chooses the tiles shown on the home page.
 *
 * Order: the user's bookmarks, then recently visited sites, then a few
 * built-in suggestions so that a fresh install does not start empty. A site
 * the user bookmarked is not repeated as a visited or suggested site, and
 * tiles the user removed stay away.
 */
object HomeShortcuts {

    /** Tiles available to the home page source list; the built-in page displays four. */
    const val MAX_SHORTCUTS = 8

    private const val TAG = "HomeShortcuts"
    private const val KEY_HIDDEN = "home_hidden_shortcuts"

    /** 18.3.0 beta (item 1): the shortcuts the user added himself, as JSON. */
    private const val KEY_USER = "home_user_shortcuts"

    private const val MAX_USER_SHORTCUTS = 12

    /** Suggestions used to fill up the grid. Free and open projects only. */
    @JvmField
    val DEFAULTS: List<Shortcut> = listOf(
        Shortcut("GitHub", "https://github.com/nadib330/Foss-Explorer/"),
        Shortcut("OpenStreetMap", "https://www.openstreetmap.org/"),
        Shortcut("F-Droid", "https://f-droid.org/"),
        Shortcut("Codeberg", "https://codeberg.org/"),
    )

    /**
     * Pure selection logic.
     *
     * @param bookmarks title and address, in the order they should appear
     * @param history title and address, most recent first
     * @param hidden [Shortcut.key]s of tiles the user removed
     * @param suggestions fill the remaining places
     */
    @JvmStatic
    @JvmOverloads
    fun select(
        bookmarks: List<Shortcut>,
        history: List<Shortcut>,
        hidden: Set<String> = emptySet(),
        suggestions: List<Shortcut> = DEFAULTS,
        limit: Int = MAX_SHORTCUTS,
    ): List<Shortcut> {
        val result = ArrayList<Shortcut>()
        val urls = HashSet<String>()
        val sites = HashSet<String>()

        // Bookmarks keep their own title and exact address.
        for (bookmark in bookmarks) {
            val url = bookmark.url.trim()
            if (url.isEmpty() || HomePage.isHomeUrl(url) || !urls.add(url)) continue
            siteOf(url)?.let(sites::add)
            if (url in hidden) continue
            result.add(Shortcut(bookmark.title.trim().ifEmpty { labelOf(url) }, url, key = url))
        }

        // A visited page stands for its whole site, so that reading ten
        // articles on one site does not fill the grid with that site.
        for (entry in history + suggestions) {
            val site = siteOf(entry.url) ?: continue
            if (!sites.add(site) || site in hidden) continue
            val isSuggestion = entry in suggestions
            val url = if (isSuggestion) entry.url else siteRoot(entry.url) ?: continue
            result.add(Shortcut(if (isSuggestion) entry.title else site, url, key = site))
        }

        return result.take(limit)
    }

    /**
     * The site a web address belongs to: its host without a leading `www.`,
     * in lower case. `null` for anything that is not an http(s) address.
     */
    @JvmStatic
    fun siteOf(url: String?): String? {
        val uri = url?.trim()?.takeIf { it.isNotEmpty() }?.toUri() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        return uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
    }

    /** Short name for an address: the site if it has one, otherwise the address itself. */
    @JvmStatic
    fun labelOf(url: String?): String = siteOf(url) ?: url.orEmpty()

    private fun siteRoot(url: String): String? {
        val uri = url.trim().toUri()
        val authority = uri.encodedAuthority ?: return null
        return "${uri.scheme}://$authority/"
    }

    /**
     * Reads bookmarks, history and removed tiles and returns the tiles to
     * show. Touches the database: call it off the main thread.
     */
    @JvmStatic
    fun load(context: Context): List<Shortcut> {
        val app = context.applicationContext
        var bookmarks: List<Record> = emptyList()
        var history: List<Record> = emptyList()
        val action = RecordAction(app)
        try {
            action.open(false)
            // listBookmark() returns its list upside down for the bottom-anchored overview.
            bookmarks = action.listBookmark(app, false, 0).asReversed()
            history = action.listHistory(app).sortedByDescending { it.time }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read bookmarks or history", e)
        } finally {
            try {
                action.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not close the database", e)
            }
        }
        val users = userShortcuts(app)
        val picked = users + select(
            bookmarks.map(::toShortcut),
            history.map(::toShortcut),
            hidden(app),
        )
        return picked.distinctBy { it.url }
    }

    /**
     * A shortcut saved by an older version's "Add Shortcut" card. Keep these entries first so an
     * upgrade does not silently lose a user's home-page shortcuts, even though 18.4 no longer
     * exposes an add card.
     */
    @JvmStatic
    fun add(context: Context, title: String, url: String) {
        val address = url.trim().let { if (it.contains("://")) it else "https://$it" }
        if (address.isEmpty()) return
        val name = title.trim().ifEmpty { siteOf(address) ?: address }
        val stored = userShortcuts(context).toMutableList()
        stored.removeAll { it.url == address }
        stored.add(0, Shortcut(name, address))
        val json = org.json.JSONArray()
        for (shortcut in stored.take(MAX_USER_SHORTCUTS)) {
            json.put(org.json.JSONObject().put("title", shortcut.title).put("url", shortcut.url))
        }
        ProfilePrefs.settings(context).edit { putString(KEY_USER, json.toString()) }
    }

    /** The shortcuts the user added, newest first. */
    @JvmStatic
    fun userShortcuts(context: Context): List<Shortcut> = try {
        val raw = ProfilePrefs.settings(context).getString(KEY_USER, null) ?: return emptyList()
        val json = org.json.JSONArray(raw)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url")
            if (url.isEmpty()) null else Shortcut(item.optString("title"), url)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the shortcuts of the user", e)
        emptyList()
    }

    /** Removes a tile from the home page. The bookmark or history entry itself stays. */
    @JvmStatic
    fun hide(context: Context, shortcut: Shortcut) {
        // A shortcut the user added is taken away rather than hidden: there is nothing behind it.
        val users = userShortcuts(context)
        if (users.any { it.url == shortcut.url }) {
            val left = users.filterNot { it.url == shortcut.url }
            val json = org.json.JSONArray()
            for (item in left) json.put(org.json.JSONObject().put("title", item.title).put("url", item.url))
            ProfilePrefs.settings(context).edit { putString(KEY_USER, json.toString()) }
            return
        }
        val updated = hidden(context) + shortcut.key
        ProfilePrefs.settings(context).edit { putStringSet(KEY_HIDDEN, updated) }
    }

    private fun hidden(context: Context): Set<String> =
        ProfilePrefs.settings(context)
            .getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet()

    private fun toShortcut(record: Record): Shortcut = Shortcut(record.title.orEmpty(), record.url.orEmpty())
}
