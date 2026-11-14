package com.rpooi3.explorer.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Clear
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.database.RecordAction
import com.rpooi3.explorer.downloads.DownloadLog
import com.rpooi3.explorer.downloads.DownloadsActivity
import com.rpooi3.explorer.library.LibraryActivity.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The history, bookmarks and downloads screens that open from the three-dot menu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class LibraryScreensTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val day = 24 * 60 * 60 * 1000L
    private val now = System.currentTimeMillis()

    private class Screen<A : LibraryActivity>(val activity: A) {
        val list: RecyclerView = activity.findViewById(R.id.library_list)
        val empty: View = activity.findViewById(R.id.library_empty)
        val chips: ViewGroup = activity.findViewById(R.id.library_chips)
        val chipRow: View = activity.findViewById(R.id.library_chips_scroll)

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun items(): List<Row.Item> = activity.rows.filterIsInstance<Row.Item>()
        fun titles(): List<String> = items().map { it.title }
        fun headers(): List<String> = activity.rows.filterIsInstance<Row.Header>().map { it.text }
        fun chipLabels(): List<String> = (0 until chips.childCount).map { (chips.getChildAt(it) as TextView).text.toString() }

        /** The row view of an item, as it is on screen. */
        fun rowOf(title: String): View {
            idle()
            return (0 until list.childCount).map { list.getChildAt(it) }
                .first { it.findViewById<TextView?>(R.id.library_item_title)?.text?.toString() == title }
        }

        /** The address handed back to the browser, and where it should open. */
        fun result(): Pair<String?, String?> {
            val shadow = shadowOf(activity)
            assertEquals(Activity.RESULT_OK, shadow.resultCode)
            assertTrue("the screen closes", activity.isFinishing)
            return shadow.resultIntent.getStringExtra(LibraryActivity.EXTRA_URL) to
                shadow.resultIntent.getStringExtra(LibraryActivity.EXTRA_TARGET)
        }
    }

    private fun <A : LibraryActivity> launch(type: Class<A>, block: Screen<A>.() -> Unit) {
        Robolectric.buildActivity(type).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            Screen(controller.get()).apply {
                block()
                idle()
            }
        }
    }

    private fun database(write: RecordAction.() -> Unit) {
        val action = RecordAction(context)
        action.open(true)
        try {
            action.write()
        } finally {
            action.close()
        }
    }

    private fun seedHistory() = database {
        addHistory(Record("Older page", "https://old.example/a", now - 5 * day, 0))
        addHistory(Record("First page", "https://first.example/", now - 2000, 0))
        addHistory(Record("Yesterday's page", "https://yesterday.example/x", now - day, 0))
        addHistory(Record("Second page", "https://second.example/path", now - 1000, 0))
    }

    // ---------------------------------------------------------------- history

    @Test
    fun history_hasTitleSearchAndBack_likeAFullScreen() = launch(HistoryActivity::class.java) {
        assertEquals("History", activity.findViewById<TextView>(R.id.library_title).text.toString())
        assertTrue(activity.findViewById<View>(R.id.library_back).isShown)
        assertTrue(activity.findViewById<View>(R.id.library_search).isShown)
        assertFalse("no extra menu on the history screen", activity.findViewById<View>(R.id.library_more).isShown)
    }

    @Test
    fun history_startsWithTheClearLink_thenTheDays_newestFirst() {
        seedHistory()
        launch(HistoryActivity::class.java) {
            assertEquals(Row.Link(HistoryActivity.LINK_CLEAR, "Clear browsing data…"), activity.rows.first())
            assertEquals(listOf("Second page", "First page", "Yesterday's page", "Older page"), titles())

            val headers = headers()
            assertEquals(3, headers.size)
            assertTrue(headers[0], headers[0].startsWith("Today – "))
            assertTrue(headers[1], headers[1].startsWith("Yesterday – "))
            assertFalse(headers[2], headers[2].contains("–"))

            // Both pages of today are under the one "Today" heading.
            val rows = activity.rows
            assertTrue(rows[1] is Row.Header && rows[2] is Row.Item && rows[3] is Row.Item && rows[4] is Row.Header)
        }
    }

    @Test
    fun history_rowsShowTitleAndWebsite_andEndInARemoveButton() {
        seedHistory()
        launch(HistoryActivity::class.java) {
            val row = rowOf("Second page")
            assertEquals("second.example", row.findViewById<TextView>(R.id.library_item_subtitle).text.toString())
            val remove = row.findViewById<View>(R.id.library_item_action)
            assertEquals("Remove from history", remove.contentDescription.toString())

            remove.performClick()
            idle()

            assertEquals(listOf("First page", "Yesterday's page", "Older page"), titles())
        }
        // It is gone from the database, not only from the screen.
        launch(HistoryActivity::class.java) { assertFalse("Second page" in titles()) }
    }

    @Test
    fun history_tapOpensThePageInTheCurrentTab() {
        seedHistory()
        launch(HistoryActivity::class.java) {
            rowOf("First page").performClick()

            assertEquals("https://first.example/" to LibraryActivity.TARGET_CURRENT_TAB, result())
        }
    }

    @Test
    fun history_searchFiltersByTitleAndAddress_andHidesTheClearLink() {
        seedHistory()
        launch(HistoryActivity::class.java) {
            activity.search("SECOND")
            idle()
            assertEquals(listOf("Second page"), titles())
            assertTrue("no link among search results", activity.rows.none { it is Row.Link })

            activity.search("old.example")
            idle()
            assertEquals(listOf("Older page"), titles())

            activity.search("nothing like this")
            idle()
            assertTrue(titles().isEmpty())
            assertEquals(View.VISIBLE, empty.visibility)

            // Back closes the search first and brings everything back; the screen stays open.
            activity.onBackPressedDispatcher.onBackPressed()
            idle()
            assertFalse(activity.isSearchOpen)
            assertFalse(activity.isFinishing)
            assertEquals(4, titles().size)

            activity.onBackPressedDispatcher.onBackPressed()
            assertTrue(activity.isFinishing)
        }
    }

    @Test
    fun history_whenEmpty_saysSo_andStillOffersTheClearLink() = launch(HistoryActivity::class.java) {
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(
            "Pages you visit appear here",
            activity.findViewById<TextView>(R.id.library_empty_text).text.toString(),
        )
        assertEquals(listOf<Row>(Row.Link(HistoryActivity.LINK_CLEAR, "Clear browsing data…")), activity.rows)
    }

    @Test
    fun history_clearLink_opensTheClearScreen() {
        seedHistory()
        launch(HistoryActivity::class.java) {
            idle()
            val link = (0 until list.childCount).map { list.getChildAt(it) }.first { it.id == R.id.library_link }
            link.performClick()

            assertEquals(Settings_Clear::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        }
    }

    // -------------------------------------------------------------- bookmarks

    private fun seedBookmarks() = database {
        addBookmark(Record("Zebra news", "https://zebra.example/", 0, 0))
        addBookmark(Record("Apple pie", "https://recipes.example/apple", 0, RED))
        addBookmark(Record("Mango shake", "https://recipes.example/mango", 0, RED))
    }

    @Test
    fun bookmarks_listNameAndWebsite_sortedByName_withAThreeDotButtonPerRow() {
        seedBookmarks()
        launch(BookmarksActivity::class.java) {
            assertEquals("Bookmarks", activity.findViewById<TextView>(R.id.library_title).text.toString())
            assertEquals(listOf("Apple pie", "Mango shake", "Zebra news"), titles())
            assertTrue("no date headings", headers().isEmpty())

            val row = rowOf("Apple pie")
            assertEquals("recipes.example", row.findViewById<TextView>(R.id.library_item_subtitle).text.toString())
            assertEquals("More actions", row.findViewById<View>(R.id.library_item_action).contentDescription.toString())
            assertTrue("sorting and delete-all are in the title bar", activity.findViewById<View>(R.id.library_more).isShown)
        }
    }

    @Test
    fun bookmarks_tapOpensThePage() {
        seedBookmarks()
        launch(BookmarksActivity::class.java) {
            rowOf("Zebra news").performClick()

            assertEquals("https://zebra.example/" to LibraryActivity.TARGET_CURRENT_TAB, result())
        }
    }

    @Test
    fun bookmarks_categoriesInUse_areChips_andFilterTheList() {
        seedBookmarks()
        launch(BookmarksActivity::class.java) {
            assertEquals(View.VISIBLE, chipRow.visibility)
            assertEquals(listOf("All", "Red"), chipLabels())

            activity.selectFilter(RED)
            idle()
            assertEquals(listOf("Apple pie", "Mango shake"), titles())

            activity.selectFilter(BookmarksActivity.FILTER_ALL)
            idle()
            assertEquals(3, titles().size)
        }
    }

    @Test
    fun bookmarks_withoutCategories_showNoChips() {
        database { addBookmark(Record("Plain", "https://plain.example/", 0, 0)) }
        launch(BookmarksActivity::class.java) {
            assertEquals(View.GONE, chipRow.visibility)
            assertEquals(listOf("Plain"), titles())
        }
    }

    @Test
    fun bookmarks_edit_changesNameAndAddress_andKeepsTheCategory() {
        seedBookmarks()
        launch(BookmarksActivity::class.java) {
            val apple = items().first { it.title == "Apple pie" }.payload as Record

            activity.save(apple, "Apple crumble", "https://recipes.example/crumble")
            idle()

            assertEquals(listOf("Apple crumble", "Mango shake", "Zebra news"), titles())
            val edited = items().first { it.title == "Apple crumble" }.payload as Record
            assertEquals("https://recipes.example/crumble", edited.url)
            assertEquals(RED, BookmarksActivity.categoryOf(edited))
        }
    }

    @Test
    fun bookmarks_edit_refusesAnEmptyAddress() {
        seedBookmarks()
        launch(BookmarksActivity::class.java) {
            val apple = items().first { it.title == "Apple pie" }.payload as Record

            activity.save(apple, "Whatever", "")
            idle()

            assertEquals(listOf("Apple pie", "Mango shake", "Zebra news"), titles())
        }
    }

    @Test
    fun bookmarks_moveTo_putsABookmarkIntoACategory_andOutOfItAgain() {
        seedBookmarks()
        launch(BookmarksActivity::class.java) {
            val zebra = items().first { it.title == "Zebra news" }.payload as Record

            activity.moveTo(zebra, RED)
            idle()
            activity.selectFilter(RED)
            idle()
            assertEquals(listOf("Apple pie", "Mango shake", "Zebra news"), titles())

            val moved = items().first { it.title == "Zebra news" }.payload as Record
            activity.moveTo(moved, 0)
            idle()
            assertEquals(listOf("Apple pie", "Mango shake"), titles())
        }
    }

    @Test
    fun bookmarks_delete_removesOne_andTheChipsFollow() {
        seedBookmarks()
        launch(BookmarksActivity::class.java) {
            for (title in listOf("Apple pie", "Mango shake")) {
                activity.delete(items().first { it.title == title }.payload as Record)
                idle()
            }

            assertEquals(listOf("Zebra news"), titles())
            assertEquals("no category left in use", View.GONE, chipRow.visibility)
        }
    }

    @Test
    fun bookmarks_sortByWebsite_isRemembered() {
        seedBookmarks()
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("sort_bookmarkDomain", true).commit()
        launch(BookmarksActivity::class.java) {
            assertEquals("recipes.example comes before zebra.example", "Zebra news", titles().last())
        }
    }

    @Test
    fun bookmarks_whenEmpty_saySo() = launch(BookmarksActivity::class.java) {
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(
            "Bookmarks you add appear here",
            activity.findViewById<TextView>(R.id.library_empty_text).text.toString(),
        )
        assertTrue(activity.rows.isEmpty())
    }

    // -------------------------------------------------------------- downloads

    private fun seedDownloads() {
        DownloadLog.add(context, "holiday.jpg", "image/jpeg", 2_500_000, Uri.parse("content://media/external/downloads/11"), now - 3000)
        DownloadLog.add(context, "invoice.pdf", "application/pdf", 120_000, Uri.parse("content://media/external/downloads/12"), now - day)
        DownloadLog.add(context, "setup.apk", null, 48_000_000, null, now - 1000)
    }

    @Test
    fun downloads_whenEmpty_saySo_withoutChipsOrStorageLine() = launch(DownloadsActivity::class.java) {
        assertEquals("Downloads", activity.findViewById<TextView>(R.id.library_title).text.toString())
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(
            "Files you download appear here",
            activity.findViewById<TextView>(R.id.library_empty_text).text.toString(),
        )
        assertEquals(View.GONE, chipRow.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.library_info).visibility)
    }

    @Test
    fun downloads_listTheFilesUnderDays_newestFirst_withSizeAndStorageLine() {
        seedDownloads()
        launch(DownloadsActivity::class.java) {
            assertEquals(listOf("setup.apk", "holiday.jpg", "invoice.pdf"), titles())
            assertEquals(2, headers().size)
            assertTrue(headers()[0].startsWith("Today – "))
            assertTrue(headers()[1].startsWith("Yesterday – "))

            val size = rowOf("holiday.jpg").findViewById<TextView>(R.id.library_item_subtitle).text.toString()
            assertTrue(size, size.contains("MB"))

            val info = activity.findViewById<TextView>(R.id.library_info)
            assertEquals(View.VISIBLE, info.visibility)
            assertTrue(info.text.toString(), info.text.contains("downloaded") && info.text.contains("free on this device"))
        }
    }

    @Test
    fun downloads_chipsFilterByKindOfFile() {
        seedDownloads()
        launch(DownloadsActivity::class.java) {
            assertEquals(listOf("All", "Video", "Audio", "Images", "Documents", "Other"), chipLabels())

            activity.selectFilter(com.rpooi3.explorer.downloads.DownloadItem.Kind.IMAGE.ordinal.toLong())
            idle()
            assertEquals(listOf("holiday.jpg"), titles())

            activity.selectFilter(com.rpooi3.explorer.downloads.DownloadItem.Kind.DOCUMENT.ordinal.toLong())
            idle()
            assertEquals(listOf("invoice.pdf"), titles())

            activity.selectFilter(com.rpooi3.explorer.downloads.DownloadItem.Kind.OTHER.ordinal.toLong())
            idle()
            assertEquals(listOf("setup.apk"), titles())

            activity.selectFilter(com.rpooi3.explorer.downloads.DownloadItem.Kind.VIDEO.ordinal.toLong())
            idle()
            assertTrue(titles().isEmpty())
            assertEquals(View.VISIBLE, empty.visibility)
            assertEquals("the chips stay, to get back", View.VISIBLE, chipRow.visibility)
        }
    }

    @Test
    fun downloads_searchFiltersByFileName() {
        seedDownloads()
        launch(DownloadsActivity::class.java) {
            activity.search("INVOICE")
            idle()

            assertEquals(listOf("invoice.pdf"), titles())
        }
    }

    @Test
    fun downloads_tapOpensTheFile_withReadPermissionForTheOtherApp() {
        seedDownloads()
        launch(DownloadsActivity::class.java) {
            rowOf("invoice.pdf").performClick()

            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("content://media/external/downloads/12", intent.dataString)
            assertEquals("application/pdf", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertFalse("the downloads screen stays open", activity.isFinishing)
        }
    }

    @Test
    fun downloads_delete_removesTheEntry() {
        seedDownloads()
        launch(DownloadsActivity::class.java) {
            val apk = items().first { it.title == "setup.apk" }.payload as com.rpooi3.explorer.downloads.DownloadItem

            activity.delete(apk)
            idle()

            assertEquals(listOf("holiday.jpg", "invoice.pdf"), titles())
        }
        assertNull(DownloadLog.list(context).firstOrNull { it.name == "setup.apk" })
    }

    private companion object {
        /** Id of the first bookmark category, "Red". */
        const val RED = 11L
    }
}
