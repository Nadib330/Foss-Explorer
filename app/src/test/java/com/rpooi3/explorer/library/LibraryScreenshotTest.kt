package com.rpooi3.explorer.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.database.RecordAction
import com.rpooi3.explorer.downloads.DownloadLog
import com.rpooi3.explorer.downloads.DownloadsActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the history, bookmarks and downloads screens to
 * `app/build/outputs/screenshots/`, so that their look can be checked without
 * a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryScreenshotTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

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
        addHistory(Record("Codeberg – FOSS_Explorer", "https://codeberg.org/rpooi3/FOSS_Explorer", now - 60_000, 0))
        addHistory(Record("Wikipedia, the free encyclopedia", "https://en.wikipedia.org/wiki/Main_Page", now - 5 * 60_000, 0))
        addHistory(Record("OpenStreetMap", "https://www.openstreetmap.org/", now - 20 * 60_000, 0))
        addHistory(Record("Weather for Chattogram", "https://weather.example/chattogram", now - 26 * hour, 0))
        addHistory(Record("Android Developers", "https://developer.android.com/", now - 27 * hour, 0))
        addHistory(Record("A very long page title that does not fit on one line of the list", "https://long.example/", now - 100 * hour, 0))
    }

    private fun seedBookmarks() = database {
        addBookmark(Record("Codeberg", "https://codeberg.org/", 0, 8))
        addBookmark(Record("Wikipedia", "https://en.wikipedia.org/", 0, 0))
        addBookmark(Record("OpenStreetMap", "https://www.openstreetmap.org/", 0, 0))
        addBookmark(Record("F-Droid", "https://f-droid.org/", 0, 8))
        addBookmark(Record("Android Developers", "https://developer.android.com/", 0, 11))
    }

    private fun seedDownloads() {
        DownloadLog.add(context, "holiday-photo.jpg", "image/jpeg", 2_500_000, Uri.parse("content://media/external/downloads/1"), now - 60_000)
        DownloadLog.add(context, "podcast-episode-12.mp3", "audio/mpeg", 48_200_000, Uri.parse("content://media/external/downloads/2"), now - 2 * hour)
        DownloadLog.add(context, "invoice-2026-11.pdf", "application/pdf", 120_000, Uri.parse("content://media/external/downloads/3"), now - 26 * hour)
        DownloadLog.add(context, "FossExplorer-11.3.0.apk", null, 7_800_000, Uri.parse("content://media/external/downloads/4"), now - 27 * hour)
        DownloadLog.add(context, "talk-recording.mp4", "video/mp4", 312_000_000, Uri.parse("content://media/external/downloads/5"), now - 100 * hour)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun history() {
        seedHistory()
        capture("history", HistoryActivity::class.java)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun history_dark() {
        seedHistory()
        capture("history-dark", HistoryActivity::class.java)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun history_search() {
        seedHistory()
        capture("history-search", HistoryActivity::class.java) { it.search("org") }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun history_empty() = capture("history-empty", HistoryActivity::class.java)

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun bookmarks() {
        seedBookmarks()
        capture("bookmarks", BookmarksActivity::class.java)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun downloads() {
        seedDownloads()
        capture("downloads", DownloadsActivity::class.java)
    }

    /** Android 8: the brand palette instead of wallpaper colours. */
    @Test
    @Config(sdk = [26], qualifiers = PHONE)
    fun downloads_android8() {
        seedDownloads()
        capture("downloads-android8", DownloadsActivity::class.java)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun downloads_empty() = capture("downloads-empty", DownloadsActivity::class.java)

    private fun <A : LibraryActivity> capture(name: String, type: Class<A>, prepare: (A) -> Unit = {}) {
        Robolectric.buildActivity(type).setup().use { controller ->
            val activity = controller.get()
            prepare(activity)
            // Website icons are looked up on a background thread.
            Thread.sleep(200)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))

            val root: View = activity.findViewById(android.R.id.content)
            assertTrue("screen was laid out", root.width > 0 && root.height > 0)
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))

            val dir = File("build/outputs/screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private companion object {
        const val PHONE = "w411dp-h891dp-notnight-xxhdpi"
    }
}
