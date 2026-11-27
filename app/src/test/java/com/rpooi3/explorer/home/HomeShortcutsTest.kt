package com.rpooi3.explorer.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.database.RecordAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeShortcutsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun urls(list: List<Shortcut>) = list.map { it.url }

    @Test
    fun emptyProfile_showsSuggestions() {
        assertEquals(HomeShortcuts.DEFAULTS.map { it.title }, HomeShortcuts.select(emptyList(), emptyList()).map { it.title })
    }

    @Test
    fun order_isBookmarksThenHistoryThenSuggestions() {
        val result = HomeShortcuts.select(
            bookmarks = listOf(Shortcut("My blog", "https://blog.example/posts")),
            history = listOf(Shortcut("An article", "https://news.example/2026/article?id=1")),
            suggestions = listOf(Shortcut("Wikipedia", "https://www.wikipedia.org/")),
        )
        assertEquals(
            listOf(
                Shortcut("My blog", "https://blog.example/posts", key = "https://blog.example/posts"),
                Shortcut("news.example", "https://news.example/", key = "news.example"),
                Shortcut("Wikipedia", "https://www.wikipedia.org/", key = "wikipedia.org"),
            ),
            result,
        )
    }

    @Test
    fun history_showsEachSiteOnce() {
        val result = HomeShortcuts.select(
            bookmarks = emptyList(),
            history = listOf(
                Shortcut("A", "https://www.news.example/a"),
                Shortcut("B", "https://news.example/b"),
                Shortcut("C", "http://NEWS.example/c"),
            ),
            suggestions = emptyList(),
        )
        assertEquals(listOf("https://www.news.example/"), urls(result))
    }

    @Test
    fun bookmarkedSite_isNotRepeatedFromHistoryOrSuggestions() {
        val result = HomeShortcuts.select(
            bookmarks = listOf(Shortcut("Wiki main page", "https://www.wikipedia.org/wiki/Main")),
            history = listOf(Shortcut("Some article", "https://wikipedia.org/wiki/Android")),
            suggestions = listOf(Shortcut("Wikipedia", "https://www.wikipedia.org/")),
        )
        assertEquals(listOf("https://www.wikipedia.org/wiki/Main"), urls(result))
    }

    @Test
    fun twoBookmarksOnOneSite_areBothShown() {
        val result = HomeShortcuts.select(
            bookmarks = listOf(
                Shortcut("Repo one", "https://codeberg.org/a/one"),
                Shortcut("Repo two", "https://codeberg.org/a/two"),
                Shortcut("Repo one again", "https://codeberg.org/a/one"),
            ),
            history = emptyList(),
            suggestions = emptyList(),
        )
        assertEquals(listOf("Repo one", "Repo two"), result.map { it.title })
    }

    @Test
    fun removedTiles_stayAway() {
        val bookmark = Shortcut("My blog", "https://blog.example/posts")
        val result = HomeShortcuts.select(
            bookmarks = listOf(bookmark),
            history = listOf(Shortcut("An article", "https://news.example/article")),
            hidden = setOf("https://blog.example/posts", "news.example"),
            suggestions = listOf(Shortcut("Wikipedia", "https://www.wikipedia.org/")),
        )
        assertEquals(listOf("https://www.wikipedia.org/"), urls(result))
    }

    @Test
    fun removedBookmarkTile_doesNotComeBackAsVisitedSite() {
        val result = HomeShortcuts.select(
            bookmarks = listOf(Shortcut("My blog", "https://blog.example/posts")),
            history = listOf(Shortcut("Post", "https://blog.example/posts/1")),
            hidden = setOf("https://blog.example/posts"),
            suggestions = emptyList(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun pagesThatAreNotWebsites_areLeftOut() {
        val result = HomeShortcuts.select(
            bookmarks = listOf(Shortcut("Home", "about:blank"), Shortcut("Blank", "  ")),
            history = listOf(
                Shortcut("Home", "about:blank"),
                Shortcut("A file", "file:///sdcard/page.html"),
                Shortcut("Mail", "mailto:someone@example.org"),
            ),
            suggestions = emptyList(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun bookmarkWithoutTitle_isNamedAfterItsSite() {
        val result = HomeShortcuts.select(listOf(Shortcut(" ", "https://www.example.org/x")), emptyList(), suggestions = emptyList())
        assertEquals("example.org", result.single().title)
    }

    @Test
    fun grid_isLimitedToTwoRows() {
        val history = (1..20).map { Shortcut("Site $it", "https://site$it.example/") }
        val result = HomeShortcuts.select(emptyList(), history)
        assertEquals(HomeShortcuts.MAX_SHORTCUTS, result.size)
        assertEquals("https://site1.example/", result.first().url)
    }

    @Test
    fun siteOf_normalisesHosts() {
        assertEquals("example.org", HomeShortcuts.siteOf("https://WWW.Example.org:8080/path?q=1"))
        assertNull(HomeShortcuts.siteOf("about:blank"))
        assertNull(HomeShortcuts.siteOf("example.org"))
        assertNull(HomeShortcuts.siteOf(null))
    }

    @Test
    fun load_readsBookmarksAndHistoryFromTheDatabase() {
        val action = RecordAction(context)
        action.open(true)
        action.addBookmark(Record("Beta bookmark", "https://beta.example/b", 0, 0))
        action.addBookmark(Record("Alpha bookmark", "https://alpha.example/a", 0, 0))
        action.addHistory(Record("Older visit", "https://older.example/page", 1_000, 0))
        action.addHistory(Record("Newer visit", "https://newer.example/page", 2_000, 0))
        action.close()

        val loaded = HomeShortcuts.load(context)

        // Bookmarks in the order of the bookmark list (by title), then the most recent visit first.
        assertEquals(
            listOf("Alpha bookmark", "Beta bookmark", "newer.example", "older.example"),
            loaded.take(4).map { it.title },
        )
        assertEquals(HomeShortcuts.DEFAULTS.map { it.title }, loaded.drop(4).map { it.title })
    }

    @Test
    fun hide_isRemembered() {
        // The repository shortcut replaced the old first-run wiki bookmark. Pick the current
        // built-in suggestion rather than coupling persistence to a historical title.
        val suggestion = HomeShortcuts.load(context).first { it.url == HomeShortcuts.DEFAULTS.first().url }

        HomeShortcuts.hide(context, suggestion)

        assertTrue(HomeShortcuts.load(context).none { it.url == suggestion.url })
    }
}
