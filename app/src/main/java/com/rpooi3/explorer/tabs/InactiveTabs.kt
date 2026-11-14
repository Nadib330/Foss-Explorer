/*
 * Foss Explorer: Settings > Tabs and groups > Inactive.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.tabs

import androidx.core.content.edit
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tabs that were not looked at for a while leave the tab switcher and wait in
 * the inactive section, from where they can be opened again. This keeps
 * when each open tab was last in front, decides which tabs are due, and holds
 * the list of inactive tabs.
 */
object InactiveTabs {
    /** Days without use after which a tab becomes inactive; 0 is "Never". */
    const val KEY_DAYS = "inactive_after_days"
    const val KEY_DUPLICATES = "inactive_archive_duplicates"
    const val KEY_AUTO_CLOSE = "inactive_auto_close"

    /** Chrome's default. */
    const val DEFAULT_DAYS = 21

    /** Inactive tabs are closed for good after this many days, if "Automatically close inactive items" is on. */
    const val AUTO_CLOSE_DAYS = 60L

    private const val KEY_LIST = "inactive_tabs"
    private const val KEY_LAST_ACTIVE = "tab_last_active"

    /** A tab in the inactive section. [since] is when it was last in front. */
    data class Entry(val url: String, val title: String, val since: Long)

    @JvmStatic
    fun days(prefs: SharedPreferences): Int = prefs.getInt(KEY_DAYS, DEFAULT_DAYS)

    @JvmStatic
    fun setDays(prefs: SharedPreferences, days: Int) {
        prefs.edit { putInt(KEY_DAYS, days) }
    }

    // The inactive section ---------------------------------------------------------------------

    @JvmStatic
    fun list(prefs: SharedPreferences): List<Entry> {
        val stored = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(stored)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val url = item.optString("url")
                if (url.isEmpty()) null else Entry(url, item.optString("title"), item.optLong("since"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun save(prefs: SharedPreferences, entries: List<Entry>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(JSONObject().put("url", entry.url).put("title", entry.title).put("since", entry.since))
        }
        prefs.edit { putString(KEY_LIST, array.toString()) }
    }

    /** Puts a tab into the inactive section. A page that is there already is kept once, with the later time. */
    @JvmStatic
    fun add(prefs: SharedPreferences, entry: Entry) {
        val entries = list(prefs).toMutableList()
        val old = entries.firstOrNull { it.url == entry.url }
        if (old != null) {
            entries.remove(old)
            entries.add(0, entry.copy(since = maxOf(old.since, entry.since), title = entry.title.ifEmpty { old.title }))
        } else {
            entries.add(0, entry)
        }
        save(prefs, entries)
    }

    /** Takes a tab out of the inactive section (it was opened again, or closed). */
    @JvmStatic
    fun remove(prefs: SharedPreferences, url: String) {
        save(prefs, list(prefs).filterNot { it.url == url })
    }

    @JvmStatic
    fun clear(prefs: SharedPreferences) {
        prefs.edit { remove(KEY_LIST) }
    }

    /** Closes the inactive tabs that are older than [AUTO_CLOSE_DAYS], if the setting says so. Returns how many. */
    @JvmStatic
    fun closeOld(prefs: SharedPreferences, now: Long): Int {
        if (!prefs.getBoolean(KEY_AUTO_CLOSE, true)) return 0
        val entries = list(prefs)
        val kept = entries.filter { now - it.since < TimeUnit.DAYS.toMillis(AUTO_CLOSE_DAYS) }
        if (kept.size != entries.size) save(prefs, kept)
        return entries.size - kept.size
    }

    // When the open tabs were last used --------------------------------------------------------

    private fun lastActive(prefs: SharedPreferences): MutableMap<String, Pair<Long, String>> {
        val map = LinkedHashMap<String, Pair<Long, String>>()
        val stored = prefs.getString(KEY_LAST_ACTIVE, null) ?: return map
        try {
            val json = JSONObject(stored)
            for (url in json.keys()) {
                val item = json.optJSONObject(url) ?: continue
                map[url] = item.optLong("time") to item.optString("title")
            }
        } catch (e: JSONException) {
            // Unreadable: every tab counts as used now.
        }
        return map
    }

    /**
     * Notes the open tabs: the tab in front ([current]) was used [now]; the
     * others keep the time they have, or get [now] if they are new. Pages
     * that are no longer open are forgotten.
     *
     * @param tabs address and title of every open normal tab
     */
    @JvmStatic
    fun record(prefs: SharedPreferences, tabs: List<Pair<String, String>>, current: String?, now: Long) {
        val known = lastActive(prefs)
        val json = JSONObject()
        for ((url, title) in tabs) {
            if (url.isEmpty()) continue
            val time = if (url == current) now else known[url]?.first ?: now
            json.put(url, JSONObject().put("time", time).put("title", title.ifEmpty { known[url]?.second.orEmpty() }))
        }
        prefs.edit { putString(KEY_LAST_ACTIVE, json.toString()) }
    }

    /** When the open tab at [url] was last in front; [now] if that is not known. */
    @JvmStatic
    fun lastActive(prefs: SharedPreferences, url: String, now: Long): Long = lastActive(prefs)[url]?.first ?: now

    /**
     * Which of the open tabs [urls] are due for the inactive section: those
     * not used for the chosen number of days and, with "Archive duplicate
     * tabs", every further tab on a page that is open already. The first tab
     * of the list is the one in front and always stays.
     */
    @JvmStatic
    fun due(prefs: SharedPreferences, urls: List<String>, now: Long): Set<Int> {
        val due = LinkedHashSet<Int>()
        val days = days(prefs)
        val known = lastActive(prefs)
        if (days > 0) {
            val limit = TimeUnit.DAYS.toMillis(days.toLong())
            for (i in 1 until urls.size) {
                val time = known[urls[i]]?.first ?: continue
                if (now - time >= limit) due.add(i)
            }
        }
        if (prefs.getBoolean(KEY_DUPLICATES, false)) {
            // Tabs on one page share their time of use, so the first of them (the one
            // in front, if it is among them) stays and the others go.
            val seen = HashSet<String>()
            for (i in urls.indices) if (!seen.add(urls[i]) && i > 0) due.add(i)
        }
        return due
    }

    /**
     * Moves the tabs of [urls] that are due into the inactive section and
     * returns the ones that stay open, in their order.
     */
    @JvmStatic
    fun sort(prefs: SharedPreferences, urls: List<String>, now: Long): List<String> {
        closeOld(prefs, now)
        val due = due(prefs, urls, now)
        if (due.isEmpty()) return urls
        val known = lastActive(prefs)
        // Later entries are added in front; go backwards so that the list keeps the order of the tabs.
        for (i in due.reversed()) {
            val info = known[urls[i]]
            add(prefs, Entry(urls[i], info?.second.orEmpty(), info?.first ?: now))
        }
        return urls.filterIndexed { i, _ -> i !in due }
    }
}
