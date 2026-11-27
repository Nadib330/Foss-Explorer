/*
 * Foss Explorer: bookmarks, history and the lists of websites, as the rest of
 * the app asks for them.
 * Own code, GPL-3.0. Written for 15.0.0 in place of FOSS Browser's class of
 * the same name: the questions are the same, the answers now come from
 * Firefox's Places database (database/places).
 */
package com.rpooi3.explorer.database

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.database.places.Places
import com.rpooi3.explorer.database.places.PlacesDatabase

/**
 * What the screens of the app know of the database: bookmarks and history as
 * [Record]s, and lists of domains by name. Which of them a call means is said
 * with the names in [RecordUnit] (`TABLE_BOOKMARK`, `TABLE_HISTORY`,
 * `TABLE_STANDARD`); they are names of lists now, not of tables.
 */
class RecordAction(context: Context) {
    private val context = context.applicationContext
    private val db: SQLiteDatabase get() = PlacesDatabase.get(context).writableDatabase

    /** The database is one for the whole app and opens itself; kept for the callers that say when they start. */
    @Suppress("UNUSED_PARAMETER")
    fun open(rw: Boolean) = Unit

    /** See [open]. */
    fun close() = Unit

    fun addBookmark(record: Record?) {
        if (isInvalid(record)) return
        Places.addBookmark(db, record!!.url.trim(), record.title.trim(), record.iconColor)
    }

    /**
     * The bookmarks, sorted as the user chose ("sort_bookmark": by title or
     * by colour; "sort_bookmarkDomain": by website), last first, as the
     * lists show them. With [filter], only those of the colour [filterBy].
     */
    fun listBookmark(context: Context, filter: Boolean, filterBy: Long): List<Record> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val list = Places.bookmarks(db)
            .filter { !filter || it.filter == filterBy }
            .map { Record(it.title, it.url, 0L, it.filter) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            .toMutableList()
        if (prefs.getString("sort_bookmark", "title") == "time") {
            list.sortWith(compareBy<Record> { it.iconColor }.thenBy { it.title })
        }
        if (prefs.getBoolean("sort_bookmarkDomain", false)) list.sortBy { it.domain }
        list.reverse()
        return list
    }

    fun addHistory(record: Record?) {
        if (isInvalid(record)) return
        Places.noteVisit(db, record!!.url.trim(), record.title.trim(), record.time)
    }

    /** The pages of the history, the one visited last at the end; by website with "sort_historyDomain". */
    fun listHistory(context: Context): List<Record> {
        val list = Places.history(db).map { Record(it.title.ifEmpty { it.url }, it.url, it.lastVisit, 0L) }.toMutableList()
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sort_historyDomain", false)) {
            list.sortBy { it.domain }
        }
        return list
    }

    fun addDomain(domain: String?, table: String) {
        if (domain.isNullOrBlank()) return
        Places.addToSiteList(db, table, domain)
    }

    fun deleteDomain(domain: String?, table: String) {
        if (domain.isNullOrBlank()) return
        Places.removeFromSiteList(db, table, domain)
    }

    fun listDomains(table: String): List<String> = Places.siteList(db, table)

    fun checkUrl(url: String?, table: String): Boolean {
        val address = url?.trim().orEmpty()
        if (address.isEmpty()) return false
        return when (table) {
            RecordUnit.TABLE_BOOKMARK -> Places.isBookmarked(db, address)
            RecordUnit.TABLE_HISTORY -> Places.isInHistory(db, address)
            else -> address in Places.siteList(db, table)
        }
    }

    fun deleteURL(url: String?, table: String) {
        val address = url?.trim().orEmpty()
        if (address.isEmpty()) return
        when (table) {
            RecordUnit.TABLE_BOOKMARK -> Places.deleteBookmark(db, address)
            RecordUnit.TABLE_HISTORY -> Places.deleteHistory(db, address)
            else -> Places.removeFromSiteList(db, table, address)
        }
    }

    fun clearTable(table: String) {
        when (table) {
            RecordUnit.TABLE_BOOKMARK -> Places.clearBookmarks(db)
            RecordUnit.TABLE_HISTORY -> Places.clearHistory(db)
            else -> Places.clearSiteList(db, table)
        }
    }

    /** Bookmarks and history together, for the suggestions of the address field. */
    fun listEntries(activity: Activity): List<Record> =
        listBookmark(activity, false, 0) + listHistory(activity.applicationContext)

    private fun isInvalid(record: Record?): Boolean =
        record == null || record.title.isNullOrBlank() || record.url.isNullOrBlank() || record.time < 0L

    companion object {
        const val HISTORY_ITEM = 0
        const val BOOKMARK_ITEM = 2
    }
}
