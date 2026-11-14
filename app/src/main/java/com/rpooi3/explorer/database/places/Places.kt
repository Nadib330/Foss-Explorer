/*
 * Foss Explorer: reading and writing Firefox's Places tables.
 * Own code, GPL-3.0. The meaning of the columns, the kinds of visit, the
 * roots of the bookmark tree and the way a page's rank ("frecency") is
 * worked out follow Mozilla's application-services (MPL-2.0, see
 * third_party/application-services).
 */
package com.rpooi3.explorer.database.places

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import androidx.core.net.toUri
import java.security.SecureRandom
import kotlin.math.ceil

object Places {
    // Roots of the bookmark tree (BookmarkRootGuid in application-services).
    const val GUID_ROOT = "root________"
    const val GUID_MENU = "menu________"
    const val GUID_TOOLBAR = "toolbar_____"
    const val GUID_UNFILED = "unfiled_____"

    /** Where Firefox for Android files bookmarks, and Explorer too. */
    const val GUID_MOBILE = "mobile______"

    // moz_bookmarks.type
    const val TYPE_BOOKMARK = 1
    const val TYPE_FOLDER = 2

    // moz_historyvisits.visit_type (VisitType in application-services)
    const val VISIT_LINK = 1
    const val VISIT_TYPED = 2
    const val VISIT_BOOKMARK = 3
    const val VISIT_RELOAD = 9

    /** Sync status "new": the row was never sent anywhere. Explorer does not sync; every row stays new. */
    const val SYNC_STATUS_NEW = 1

    /** A page of the history: its address, its title, when it was last visited, and how often. */
    data class Page(val url: String, val title: String, val lastVisit: Long, val visits: Int)

    /** A bookmark: [filter] is the colour it is filed under (Explorer's bookmark filter). */
    data class Bookmark(val url: String, val title: String, val filter: Long, val added: Long, val guid: String)

    private val random = SecureRandom()
    private const val GUID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** A new identifier as Firefox makes them: twelve characters of the URL-safe base64 alphabet. */
    @JvmStatic
    fun guid(): String = buildString(12) { repeat(12) { append(GUID_CHARS[random.nextInt(GUID_CHARS.length)]) } }

    // Pages ------------------------------------------------------------------------------------

    private fun placeId(db: SQLiteDatabase, url: String): Long? =
        db.rawQuery(
            // Found by the hash, as Firefox does; the address itself settles a clash.
            "SELECT id FROM moz_places WHERE url_hash = ? AND url = ?",
            arrayOf(PlacesHash.hashUrl(url).toString(), url),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

    /** `https://` and `example.org` of an address, or null if it has no host. */
    private fun origin(url: String): Pair<String, String>? {
        val uri = url.toUri()
        val scheme = uri.scheme ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val port = if (uri.port > 0) ":" + uri.port else ""
        return "$scheme://" to host + port
    }

    /** The row of moz_origins for [url], created if need be; null for an address without a host. */
    private fun originId(db: SQLiteDatabase, url: String): Long? {
        val (prefix, host) = origin(url) ?: return null
        db.rawQuery("SELECT id FROM moz_origins WHERE host = ? AND prefix = ?", arrayOf(host, prefix))
            .use { if (it.moveToFirst()) return it.getLong(0) }
        return db.insertOrThrow(
            "moz_origins", null,
            ContentValues().apply {
                put("prefix", prefix)
                put("host", host)
                // The host backwards with a dot at its end, so that the hosts of one domain lie together.
                put("rev_host", host.reversed() + ".")
                put("frecency", 0)
            },
        )
    }

    /** The row of moz_places for [url], created if need be. */
    private fun ensurePlace(db: SQLiteDatabase, url: String, title: String?): Long {
        placeId(db, url)?.let { id ->
            if (!title.isNullOrBlank()) db.execSQL("UPDATE moz_places SET title = ? WHERE id = ?", arrayOf<Any>(title.trim(), id))
            return id
        }
        return db.insertOrThrow(
            "moz_places", null,
            ContentValues().apply {
                put("url", url)
                if (!title.isNullOrBlank()) put("title", title.trim())
                put("guid", guid())
                put("url_hash", PlacesHash.hashUrl(url))
                originId(db, url)?.let { put("origin_id", it) }
            },
        )
    }

    /**
     * Records a visit of [url] at [time] (milliseconds): a row in
     * moz_historyvisits, and count, date and rank of the page brought up to date.
     */
    @JvmStatic
    @JvmOverloads
    fun noteVisit(db: SQLiteDatabase, url: String, title: String?, time: Long, type: Int = VISIT_LINK) {
        db.transaction {
            val place = ensurePlace(db, url, title)
            db.insertOrThrow(
                "moz_historyvisits", null,
                ContentValues().apply {
                    put("is_local", 1)
                    put("place_id", place)
                    put("visit_date", time)
                    put("visit_type", type)
                },
            )
            db.execSQL(
                "UPDATE moz_places SET visit_count_local = visit_count_local + 1, " +
                    "last_visit_date_local = MAX(last_visit_date_local, ?), sync_change_counter = sync_change_counter + 1 " +
                    "WHERE id = ?",
                arrayOf<Any>(time, place),
            )
            updateFrecency(db, place, System.currentTimeMillis())
        }
    }

    /**
     * The rank of a page as Firefox works it out ("frecency", frequency and
     * recency): each of the last ten visits counts by its kind and its age,
     * and the average is multiplied by the number of visits.
     */
    internal fun frecency(visits: List<Pair<Long, Int>>, visitCount: Int, bookmarked: Boolean, now: Long): Int {
        if (visits.isEmpty()) return if (bookmarked) UNVISITED_BOOKMARK_BONUS else 0
        var points = 0.0
        for ((date, type) in visits) {
            val bonus = when (type) {
                VISIT_TYPED -> TYPED_BONUS
                VISIT_BOOKMARK -> BOOKMARK_BONUS
                VISIT_RELOAD -> 0
                else -> LINK_BONUS
            } + if (bookmarked) BOOKMARK_BONUS else 0
            val ageDays = (now - date) / 86_400_000L
            val weight = when {
                ageDays <= 4 -> 100
                ageDays <= 14 -> 70
                ageDays <= 31 -> 50
                ageDays <= 90 -> 30
                else -> 10
            }
            points += bonus / 100.0 * weight
        }
        return ceil(visitCount * points / visits.size).toInt()
    }

    private fun updateFrecency(db: SQLiteDatabase, place: Long, now: Long) {
        val visits = db.rawQuery(
            "SELECT visit_date, visit_type FROM moz_historyvisits WHERE place_id = ? ORDER BY visit_date DESC LIMIT $FRECENCY_SAMPLE",
            arrayOf(place.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.getLong(0) to c.getInt(1) else null }.toList() }
        val (count, foreign) = db.rawQuery(
            "SELECT visit_count_local + visit_count_remote, foreign_count FROM moz_places WHERE id = ?", arrayOf(place.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) to it.getInt(1) else 0 to 0 }
        db.execSQL(
            "UPDATE moz_places SET frecency = ? WHERE id = ?",
            arrayOf<Any>(frecency(visits, count, foreign > 0, now), place),
        )
        db.execSQL(
            "UPDATE moz_origins SET frecency = (SELECT COALESCE(SUM(MAX(frecency, 0)), 0) FROM moz_places WHERE origin_id = moz_origins.id) " +
                "WHERE id = (SELECT origin_id FROM moz_places WHERE id = ?)",
            arrayOf<Any>(place),
        )
    }

    /** The pages of the history, the one visited longest ago first. */
    @JvmStatic
    fun history(db: SQLiteDatabase): List<Page> = db.rawQuery(
        "SELECT url, COALESCE(title, ''), last_visit_date_local, visit_count_local FROM moz_places " +
            "WHERE last_visit_date_local > 0 AND hidden = 0 ORDER BY last_visit_date_local ASC",
        null,
    ).use { c -> generateSequence { if (c.moveToNext()) Page(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3)) else null }.toList() }

    @JvmStatic
    fun isInHistory(db: SQLiteDatabase, url: String): Boolean = db.rawQuery(
        "SELECT 1 FROM moz_places WHERE url_hash = ? AND url = ? AND last_visit_date_local > 0",
        arrayOf(PlacesHash.hashUrl(url).toString(), url),
    ).use { it.moveToFirst() }

    /** Forgets the visits of [url]. The page itself goes too, unless a bookmark still points at it. */
    @JvmStatic
    fun deleteHistory(db: SQLiteDatabase, url: String) {
        val place = placeId(db, url) ?: return
        db.transaction {
            db.delete("moz_historyvisits", "place_id = ?", arrayOf(place.toString()))
            db.execSQL(
                "UPDATE moz_places SET visit_count_local = 0, last_visit_date_local = 0, frecency = " +
                    "CASE WHEN foreign_count > 0 THEN $UNVISITED_BOOKMARK_BONUS ELSE 0 END WHERE id = ?",
                arrayOf<Any>(place),
            )
            db.delete("moz_places", "id = ? AND foreign_count = 0", arrayOf(place.toString()))
            removeEmptyOrigins(db)
        }
    }

    @JvmStatic
    fun clearHistory(db: SQLiteDatabase) {
        db.transaction {
            db.delete("moz_historyvisits", null, null)
            db.execSQL(
                "UPDATE moz_places SET visit_count_local = 0, last_visit_date_local = 0, frecency = $UNVISITED_BOOKMARK_BONUS " +
                    "WHERE foreign_count > 0",
            )
            db.delete("moz_places", "foreign_count = 0", null)
            removeEmptyOrigins(db)
        }
    }

    private fun removeEmptyOrigins(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM moz_origins WHERE id NOT IN (SELECT origin_id FROM moz_places WHERE origin_id IS NOT NULL)")
    }

    // Bookmarks --------------------------------------------------------------------------------

    private fun folderId(db: SQLiteDatabase, guid: String): Long =
        db.rawQuery("SELECT id FROM moz_bookmarks WHERE guid = ?", arrayOf(guid)).use {
            check(it.moveToFirst()) { "bookmark root $guid is missing" }
            it.getLong(0)
        }

    /** Adds a bookmark for [url] at the end of Firefox's "mobile" folder. */
    @JvmStatic
    @JvmOverloads
    fun addBookmark(db: SQLiteDatabase, url: String, title: String, filter: Long, time: Long = System.currentTimeMillis()): String {
        return db.transaction {
            val place = ensurePlace(db, url, null)
            val parent = folderId(db, GUID_MOBILE)
            val position = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM moz_bookmarks WHERE parent = ?", arrayOf(parent.toString()))
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val guid = guid()
            db.insertOrThrow(
                "moz_bookmarks", null,
                ContentValues().apply {
                    put("fk", place)
                    put("type", TYPE_BOOKMARK)
                    put("parent", parent)
                    put("position", position)
                    put("title", title.trim())
                    put("dateAdded", time)
                    put("lastModified", time)
                    put("guid", guid)
                    put("syncStatus", SYNC_STATUS_NEW)
                },
            )
            db.insertOrThrow(
                PlacesDatabase.TABLE_BOOKMARK_EXTRAS, null,
                ContentValues().apply {
                    put("guid", guid)
                    put("filter", filter)
                },
            )
            // The page is told that a bookmark points at it, as Firefox's trigger does.
            db.execSQL("UPDATE moz_places SET foreign_count = foreign_count + 1 WHERE id = ?", arrayOf<Any>(place))
            updateFrecency(db, place, System.currentTimeMillis())
            guid
        }
    }

    /** All bookmarks, in the order of their folder. */
    @JvmStatic
    fun bookmarks(db: SQLiteDatabase): List<Bookmark> = db.rawQuery(
        "SELECT p.url, COALESCE(b.title, ''), COALESCE(e.filter, 0), b.dateAdded, b.guid FROM moz_bookmarks b " +
            "JOIN moz_places p ON p.id = b.fk " +
            "LEFT JOIN ${PlacesDatabase.TABLE_BOOKMARK_EXTRAS} e ON e.guid = b.guid " +
            "WHERE b.type = $TYPE_BOOKMARK ORDER BY b.parent, b.position",
        null,
    ).use { c ->
        generateSequence { if (c.moveToNext()) Bookmark(c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getString(4)) else null }.toList()
    }

    @JvmStatic
    fun isBookmarked(db: SQLiteDatabase, url: String): Boolean = db.rawQuery(
        "SELECT 1 FROM moz_bookmarks b JOIN moz_places p ON p.id = b.fk WHERE p.url_hash = ? AND p.url = ?",
        arrayOf(PlacesHash.hashUrl(url).toString(), url),
    ).use { it.moveToFirst() }

    /** Removes every bookmark of [url]. The page goes too if it was never visited. */
    @JvmStatic
    fun deleteBookmark(db: SQLiteDatabase, url: String) {
        val place = placeId(db, url) ?: return
        db.transaction {
            val removed = db.delete("moz_bookmarks", "fk = ? AND type = $TYPE_BOOKMARK", arrayOf(place.toString()))
            if (removed > 0) {
                db.execSQL("UPDATE moz_places SET foreign_count = MAX(foreign_count - ?, 0) WHERE id = ?", arrayOf<Any>(removed, place))
                db.delete("moz_places", "id = ? AND foreign_count = 0 AND last_visit_date_local = 0", arrayOf(place.toString()))
                if (placeId(db, url) != null) updateFrecency(db, place, System.currentTimeMillis())
                removeEmptyOrigins(db)
            }
        }
    }

    @JvmStatic
    fun clearBookmarks(db: SQLiteDatabase) {
        db.transaction {
            db.delete("moz_bookmarks", "type = $TYPE_BOOKMARK", null)
            db.execSQL("UPDATE moz_places SET foreign_count = 0")
            db.delete("moz_places", "last_visit_date_local = 0", null)
            removeEmptyOrigins(db)
        }
    }

    // Lists of websites (Explorer's own) -----------------------------------------------------------

    @JvmStatic
    fun addToSiteList(db: SQLiteDatabase, list: String, domain: String) {
        db.insertWithOnConflict(
            PlacesDatabase.TABLE_SITE_LISTS, null,
            ContentValues().apply {
                put("list", list)
                put("domain", domain.trim())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    @JvmStatic
    fun removeFromSiteList(db: SQLiteDatabase, list: String, domain: String) {
        db.delete(PlacesDatabase.TABLE_SITE_LISTS, "list = ? AND domain = ?", arrayOf(list, domain.trim()))
    }

    @JvmStatic
    fun siteList(db: SQLiteDatabase, list: String): List<String> = db.rawQuery(
        "SELECT domain FROM ${PlacesDatabase.TABLE_SITE_LISTS} WHERE list = ? ORDER BY domain", arrayOf(list),
    ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }

    @JvmStatic
    fun clearSiteList(db: SQLiteDatabase, list: String) {
        db.delete(PlacesDatabase.TABLE_SITE_LISTS, "list = ?", arrayOf(list))
    }

    // Firefox's numbers for the rank of a page.
    private const val FRECENCY_SAMPLE = 10
    private const val LINK_BONUS = 100
    private const val TYPED_BONUS = 2000
    private const val BOOKMARK_BONUS = 75
    private const val UNVISITED_BOOKMARK_BONUS = 140
}
