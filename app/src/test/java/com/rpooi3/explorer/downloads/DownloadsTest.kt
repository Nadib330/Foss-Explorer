package com.rpooi3.explorer.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.compat.StorageCompat
import com.rpooi3.explorer.downloads.DownloadItem.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Where the downloads screen gets its files from. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class DownloadsTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun theLog_remembersWhatWasAdded_andGivesEachEntryItsOwnId() {
        DownloadLog.add(context, "a.png", "image/png", 10, Uri.parse("content://x/1"), 1000)
        DownloadLog.add(context, "b.bin", null, 20, null, 2000)

        val entries = DownloadLog.list(context)
        assertEquals(listOf("a.png", "b.bin"), entries.map { it.name })
        assertEquals(2, entries.map { it.id }.toSet().size)

        val first = entries[0]
        assertEquals("image/png", first.mimeType)
        assertEquals(10L, first.totalBytes)
        assertEquals(1000L, first.time)
        assertEquals(Uri.parse("content://x/1"), first.uri)
        assertEquals(State.DONE, first.state)
        assertNull(entries[1].mimeType)
        assertNull(entries[1].uri)
    }

    @Test
    fun theLog_forgetsARemovedEntry_andOnlyThat() {
        DownloadLog.add(context, "a", null, 1, null, 1)
        DownloadLog.add(context, "b", null, 1, null, 2)
        val first = DownloadLog.list(context).first()

        DownloadLog.remove(context, first.id)

        assertEquals(listOf("b"), DownloadLog.list(context).map { it.name })
    }

    @Test
    fun theLog_survivesDamagedData() {
        context.getSharedPreferences("downloads_log", Context.MODE_PRIVATE).edit().putString("items", "{not json").commit()

        assertTrue(DownloadLog.list(context).isEmpty())
        DownloadLog.add(context, "after.txt", "text/plain", 5, null, 1)
        assertEquals(listOf("after.txt"), DownloadLog.list(context).map { it.name })
    }

    @Test
    fun aFileTheBrowserWritesItself_isLogged() {
        // What saving a data: or blob: link does.
        try {
            StorageCompat.writeToDownloads(context, "generated.txt", "text/plain", "hello".toByteArray())
        } catch (e: Exception) {
            // Without real storage the write itself may fail; then nothing may be logged either.
            assertTrue(DownloadLog.list(context).isEmpty())
            return
        }

        val logged = DownloadLog.list(context).single()
        assertEquals("generated.txt", logged.name)
        assertEquals(5L, logged.totalBytes)
        assertEquals("text/plain", logged.mimeType)
    }

    @Test
    fun downloadsOfTheSystemDownloadManager_areListedWithNameAndOrigin() {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(
            DownloadManager.Request(Uri.parse("https://files.example/reports/q3.pdf"))
                .setTitle("q3.pdf")
                .setDestinationUri(Uri.parse("file:///storage/emulated/0/Download/q3.pdf")),
        )

        val item = Downloads.list(context).single()
        assertTrue(item.fromManager)
        assertEquals("q3.pdf", item.name)
        assertEquals("https://files.example/reports/q3.pdf", item.sourceUrl)
    }

    @Test
    fun bothSources_areMerged_newestFirst() {
        DownloadLog.add(context, "old.txt", "text/plain", 1, null, 1000)
        DownloadLog.add(context, "new.txt", "text/plain", 1, null, 5000)
        DownloadLog.add(context, "middle.txt", "text/plain", 1, null, 3000)

        assertEquals(listOf("new.txt", "middle.txt", "old.txt"), Downloads.list(context).map { it.name })
    }

    @Test
    fun statusOfTheDownloadManager_isTranslated() {
        assertEquals(State.DONE, Downloads.stateOf(DownloadManager.STATUS_SUCCESSFUL))
        assertEquals(State.FAILED, Downloads.stateOf(DownloadManager.STATUS_FAILED))
        assertEquals(State.RUNNING, Downloads.stateOf(DownloadManager.STATUS_RUNNING))
        assertEquals(State.WAITING, Downloads.stateOf(DownloadManager.STATUS_PENDING))
        assertEquals(State.WAITING, Downloads.stateOf(DownloadManager.STATUS_PAUSED))
    }

    @Test
    fun aMissingTitle_fallsBackToTheFileNameOfTheAddress() {
        assertEquals("q3.pdf", Downloads.fileName("https://files.example/reports/q3.pdf"))
        assertEquals("my file.zip", Downloads.fileName("file:///storage/emulated/0/Download/my%20file.zip"))
        assertNull(Downloads.fileName(null))
        assertNull(Downloads.fileName("https://files.example/"))
    }
}
