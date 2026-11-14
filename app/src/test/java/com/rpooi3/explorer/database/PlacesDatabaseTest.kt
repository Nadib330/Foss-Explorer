package com.rpooi3.explorer.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.database.places.Places
import com.rpooi3.explorer.database.places.PlacesDatabase
import com.rpooi3.explorer.database.places.PlacesHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 15.0.0: history and bookmarks are kept in Firefox's Places tables
 * (`places.sqlite`), and what FOSS Browser's database held is taken over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class PlacesDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db: SQLiteDatabase get() = PlacesDatabase.get(context).writableDatabase

    private fun long(sql: String, vararg args: String): Long =
        db.rawQuery(sql, args).use { if (it.moveToFirst()) it.getLong(0) else -1 }

    private fun text(sql: String, vararg args: String): String? =
        db.rawQuery(sql, args).use { if (it.moveToFirst()) it.getString(0) else null }

    @Test
    fun theDatabase_hasFirefoxsTables_inMozillasSchemaVersion() {
        assertEquals("places.sqlite", File(db.path).name)
        assertEquals(21, db.version)
        val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null)
            .use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet() }
        for (table in listOf(
            "moz_places", "moz_historyvisits", "moz_bookmarks", "moz_origins", "moz_meta", "moz_tags", "moz_keywords",
            "moz_inputhistory", "moz_places_metadata", "moz_bookmarks_synced",
        )) {
            assertTrue(table, table in tables)
        }
        assertFalse("FOSS Browser's tables are not in it", "HISTORY" in tables || "BOOKAMRK" in tables)
        // Mozilla's file is shipped as it is.
        val shipped = context.assets.open(PlacesDatabase.SCHEMA_ASSET).bufferedReader().use { it.readText() }
        assertEquals(File("../third_party/application-services/create_shared_schema.sql").readText(), shipped)
        assertTrue(shipped.contains("Mozilla Public"))
    }

    @Test
    fun theBookmarkTree_hasFirefoxsRoots() {
        val roots = db.rawQuery("SELECT guid, title, type, position FROM moz_bookmarks ORDER BY id", null).use { c ->
            generateSequence { if (c.moveToNext()) listOf(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)) else null }.toList()
        }
        assertEquals(
            listOf(
                listOf("root________", "root", 2, 0), listOf("menu________", "menu", 2, 0), listOf("toolbar_____", "toolbar", 2, 1),
                listOf("unfiled_____", "unfiled", 2, 2), listOf("mobile______", "mobile", 2, 3),
            ),
            roots,
        )
        assertEquals("the four folders hang below the root", 4, long("SELECT COUNT(*) FROM moz_bookmarks WHERE parent = 1"))
    }

    @Test
    fun addresses_areHashedAsFirefoxHashesThem() {
        // The test values of components/places/src/hash.rs.
        val dataUrl = "data:image/png;base64," + "iVBORw0KGgoAAA".repeat(500)
        val expected = mapOf(
            "http://www.example.com" to 0x7226_2c1a_3496L,
            "http://user:pass@foo:21/bar;par?b#c" to 0x7226_61d2_18a7L,
            "https://github.com/mozilla/application-services/" to 0x2b12_e7bd_7fcdL,
            "place:transition=7&sort=4" to 0xf434_ac2b_2dafL,
            "blob:36c6ded1-6190-45f4-8fcd-355d1b6c9f48" to 0x2612_0a43_1050L,
            "www.example.com" to 0x8b14_9337L,
            dataUrl to 0x9736_d65d_86d9L,
        )
        for ((url, hash) in expected) assertEquals(url.take(40), hash, PlacesHash.hashUrl(url))
        for ((scheme, top) in mapOf("http" to 0x7226L, "https" to 0x2b12L, "file" to 0xc7c9L, "data" to 0x9736L)) {
            assertEquals(scheme, top, PlacesHash.hashString(scheme) and 0xFFFF)
        }
    }

    @Test
    fun aVisit_isARowInMozHistoryvisits_andThePageKeepsCountDateAndHash() {
        val url = "https://www.example.org/article"
        Places.noteVisit(db, url, "An article", 1_000L)
        Places.noteVisit(db, url, "An article, read again", 5_000L)
        Places.noteVisit(db, "https://www.example.org/other", "Other", 3_000L)

        assertEquals(1, long("SELECT COUNT(*) FROM moz_places WHERE url = ?", url))
        assertEquals(2, long("SELECT visit_count_local FROM moz_places WHERE url = ?", url))
        assertEquals(5_000L, long("SELECT last_visit_date_local FROM moz_places WHERE url = ?", url))
        assertEquals("An article, read again", text("SELECT title FROM moz_places WHERE url = ?", url))
        assertEquals(PlacesHash.hashUrl(url), long("SELECT url_hash FROM moz_places WHERE url = ?", url))
        assertEquals(12, text("SELECT guid FROM moz_places WHERE url = ?", url)!!.length)
        assertEquals(3, long("SELECT COUNT(*) FROM moz_historyvisits"))
        assertEquals("a link that was followed", 1, long("SELECT visit_type FROM moz_historyvisits LIMIT 1"))
        assertEquals(1, long("SELECT is_local FROM moz_historyvisits LIMIT 1"))
        // Both pages belong to one origin, kept as Firefox keeps it.
        assertEquals(1, long("SELECT COUNT(*) FROM moz_origins"))
        assertEquals("https://", text("SELECT prefix FROM moz_origins"))
        assertEquals("www.example.org", text("SELECT host FROM moz_origins"))
        assertEquals("gro.elpmaxe.www.", text("SELECT rev_host FROM moz_origins"))
        assertTrue("the page has a rank", long("SELECT frecency FROM moz_places WHERE url = ?", url) > 0)

        assertEquals(listOf("https://www.example.org/other", url), Places.history(db).map { it.url })
        assertEquals(2, Places.history(db).last().visits)
    }

    @Test
    fun aPageVisitedOftenAndLately_ranksAboveOneVisitedOnceLongAgo() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        val often = Places.frecency(listOf(now to Places.VISIT_LINK, now - day to Places.VISIT_LINK, now - 2 * day to Places.VISIT_LINK), 3, false, now)
        val once = Places.frecency(listOf(now - 200 * day to Places.VISIT_LINK), 1, false, now)
        assertTrue("$often > $once", often > once)
        assertEquals("Firefox's numbers: 3 visits of 100 points each", 300, often)
        assertEquals(10, once)
        assertTrue("a typed address counts more", Places.frecency(listOf(now to Places.VISIT_TYPED), 1, false, now) > 100)
        assertTrue("a bookmark never visited still has a rank", Places.frecency(emptyList(), 0, true, now) > 0)
    }

    @Test
    fun aBookmark_isARowInMozBookmarks_underFirefoxsMobileFolder() {
        val url = "https://example.org/"
        val guid = Places.addBookmark(db, url, "Example", 3L, 9_000L)
        Places.addBookmark(db, "https://example.com/", "Second", 0L, 9_500L)

        assertEquals(Places.GUID_MOBILE, text("SELECT p.guid FROM moz_bookmarks b JOIN moz_bookmarks p ON p.id = b.parent WHERE b.guid = ?", guid))
        assertEquals(1, long("SELECT type FROM moz_bookmarks WHERE guid = ?", guid))
        assertEquals(0, long("SELECT position FROM moz_bookmarks WHERE guid = ?", guid))
        assertEquals(1, long("SELECT position FROM moz_bookmarks WHERE title = 'Second'"))
        assertEquals(9_000L, long("SELECT dateAdded FROM moz_bookmarks WHERE guid = ?", guid))
        assertEquals("the page knows that a bookmark points at it", 1, long("SELECT foreign_count FROM moz_places WHERE url = ?", url))
        assertEquals("Explorer's colour for the bookmark filter", 3, long("SELECT filter FROM explorer_bookmark_extras WHERE guid = ?", guid))
        assertTrue(Places.isBookmarked(db, url))
        assertEquals(listOf("Example", "Second"), Places.bookmarks(db).map { it.title })

        // A bookmarked page survives the clearing of the history; its visits do not.
        Places.noteVisit(db, url, "Example", 10_000L)
        Places.noteVisit(db, "https://visited.example/", "Visited", 10_000L)
        Places.clearHistory(db)
        assertEquals(0, long("SELECT COUNT(*) FROM moz_historyvisits"))
        assertTrue(Places.isBookmarked(db, url))
        assertEquals(-1, long("SELECT id FROM moz_places WHERE url = 'https://visited.example/'"))
        assertTrue(Places.history(db).isEmpty())

        Places.deleteBookmark(db, url)
        assertFalse(Places.isBookmarked(db, url))
        assertEquals("nothing points at the page any more", -1, long("SELECT id FROM moz_places WHERE url = ?", url))
        assertEquals(0, long("SELECT COUNT(*) FROM explorer_bookmark_extras WHERE guid = ?", guid))
        assertEquals("the roots stay", 5, long("SELECT COUNT(*) FROM moz_bookmarks WHERE type = 2"))
    }

    @Test
    fun theRestOfTheApp_asksAsBefore() {
        val action = RecordAction(context)
        action.open(true)
        action.addHistory(Record("First", "https://first.example/", 1_000L, 0L))
        action.addHistory(Record("Second", "https://second.example/", 2_000L, 0L))
        action.addHistory(Record("First again", "https://first.example/", 3_000L, 0L))
        assertEquals("a page is listed once, by its last visit", listOf("Second", "First again"), action.listHistory(context).map { it.title })
        assertEquals(3_000L, action.listHistory(context).last().time)
        assertTrue(action.checkUrl("https://first.example/", RecordUnit.TABLE_HISTORY))
        action.deleteURL("https://first.example/", RecordUnit.TABLE_HISTORY)
        assertFalse(action.checkUrl("https://first.example/", RecordUnit.TABLE_HISTORY))

        action.addBookmark(Record("beta", "https://b.example/", 0L, 2L))
        action.addBookmark(Record("Alpha", "https://a.example/", 0L, 5L))
        assertEquals("by title, the last first, as the lists show them", listOf("beta", "Alpha"), action.listBookmark(context, false, 0).map { it.title })
        assertEquals(listOf("Alpha"), action.listBookmark(context, true, 5L).map { it.title })
        assertEquals(5L, action.listBookmark(context, true, 5L).single().iconColor)
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("sort_bookmark", "time").commit()
        assertEquals("by colour", listOf(5L, 2L), action.listBookmark(context, false, 0).map { it.iconColor })
        assertTrue(action.checkUrl("https://a.example/", RecordUnit.TABLE_BOOKMARK))
        action.clearTable(RecordUnit.TABLE_BOOKMARK)
        assertTrue(action.listBookmark(context, false, 0).isEmpty())

        action.addDomain("example.org", RecordUnit.TABLE_STANDARD)
        action.addDomain("example.org", RecordUnit.TABLE_STANDARD)
        action.addDomain("example.com", RecordUnit.TABLE_STANDARD)
        assertEquals(listOf("example.com", "example.org"), action.listDomains(RecordUnit.TABLE_STANDARD))
        action.deleteDomain("example.com", RecordUnit.TABLE_STANDARD)
        assertEquals(listOf("example.org"), action.listDomains(RecordUnit.TABLE_STANDARD))
        action.close()
        assertEquals("the database stays open for the next caller", 1, action.listHistory(context).size)
    }

    @Test
    fun whatFossBrowsersDatabaseHeld_isTakenOverOnce_andTheOldFileGoes() {
        // The database of 14.2.3 and before, as FOSS Browser laid it out.
        val legacy = context.getDatabasePath(PlacesDatabase.LEGACY_NAME)
        legacy.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(legacy, null).use { old ->
            old.execSQL("CREATE TABLE BOOKAMRK ( TITLE text, URL text, TIME integer)")
            old.execSQL("CREATE TABLE HISTORY ( TITLE text, URL text, TIME integer)")
            old.execSQL("CREATE TABLE REMOTE ( DOMAIN text)")
            fun row(table: String, vararg values: Pair<String, Any>) = old.insert(table, null, ContentValues().apply {
                for ((key, value) in values) if (value is Long) put(key, value) else put(key, value.toString())
            })
            row("BOOKAMRK", "TITLE" to "Wikipedia", "URL" to "https://wikipedia.org/", "TIME" to 4L)
            row("BOOKAMRK", "TITLE" to "Codeberg", "URL" to "https://codeberg.org/", "TIME" to 0L)
            row("HISTORY", "TITLE" to "A page", "URL" to "https://example.org/page", "TIME" to 1_700_000_000_000L)
            row("HISTORY", "TITLE" to "Wikipedia", "URL" to "https://wikipedia.org/", "TIME" to 1_700_000_100_000L)
            row("REMOTE", "DOMAIN" to "trusted.example")
            old.version = 5
        }
        PlacesDatabase.release()

        val action = RecordAction(context)
        assertEquals(setOf("Wikipedia", "Codeberg"), action.listBookmark(context, false, 0).map { it.title }.toSet())
        assertEquals("the colour came along", 4L, action.listBookmark(context, true, 4L).single().iconColor)
        assertEquals(listOf("https://example.org/page", "https://wikipedia.org/"), action.listHistory(context).map { it.url })
        assertEquals(1_700_000_100_000L, action.listHistory(context).last().time)
        assertEquals(listOf("trusted.example"), action.listDomains(RecordUnit.TABLE_STANDARD))
        assertFalse("the old file is gone", legacy.exists())

        // Opening again takes nothing over twice.
        PlacesDatabase.release()
        assertEquals(2, RecordAction(context).listBookmark(context, false, 0).size)
        assertEquals(2, RecordAction(context).listHistory(context).size)
        assertNotEquals(-1L, long("SELECT id FROM moz_places WHERE url = 'https://wikipedia.org/' AND foreign_count = 1 AND visit_count_local = 1"))
    }
}
