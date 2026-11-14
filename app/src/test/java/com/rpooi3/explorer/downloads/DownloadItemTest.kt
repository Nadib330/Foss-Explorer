package com.rpooi3.explorer.downloads

import com.rpooi3.explorer.downloads.DownloadItem.Kind
import com.rpooi3.explorer.downloads.DownloadItem.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What kind of file a download is, and how far along it is. */
class DownloadItemTest {

    @Test
    fun theMimeType_decidesTheKind() {
        assertEquals(Kind.IMAGE, Kind.of("image/png", "x"))
        assertEquals(Kind.VIDEO, Kind.of("video/mp4", "x"))
        assertEquals(Kind.AUDIO, Kind.of("audio/mpeg", "x"))
        assertEquals(Kind.DOCUMENT, Kind.of("application/pdf", "x"))
        assertEquals(Kind.DOCUMENT, Kind.of("text/html; charset=utf-8", "x"))
        assertEquals(Kind.DOCUMENT, Kind.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x"))
        assertEquals(Kind.OTHER, Kind.of("application/zip", "x"))
    }

    @Test
    fun theMimeType_winsOverTheExtension() {
        assertEquals(Kind.IMAGE, Kind.of("IMAGE/JPEG", "photo.pdf"))
    }

    @Test
    fun withoutAUsefulMimeType_theExtensionDecides() {
        assertEquals(Kind.IMAGE, Kind.of(null, "Photo.JPG"))
        assertEquals(Kind.VIDEO, Kind.of("application/octet-stream", "clip.mkv"))
        assertEquals(Kind.AUDIO, Kind.of("", "song.flac"))
        assertEquals(Kind.DOCUMENT, Kind.of(null, "report.final.docx"))
        assertEquals(Kind.OTHER, Kind.of(null, "app.apk"))
        assertEquals(Kind.OTHER, Kind.of(null, "no_extension"))
        assertEquals(Kind.OTHER, Kind.of(null, null))
    }

    private fun item(total: Long, done: Long) =
        DownloadItem(1, true, "file.bin", null, total, done, 0, State.RUNNING, null, null)

    @Test
    fun percent_isTheShareThatIsLoaded() {
        assertEquals(0, item(200, 0).percent)
        assertEquals(45, item(200, 90).percent)
        assertEquals(100, item(200, 200).percent)
    }

    @Test
    fun percent_staysBetween0And100_evenWithOddNumbers() {
        assertEquals(100, item(200, 500).percent)
        assertEquals(0, item(200, -5).percent)
    }

    @Test
    fun percent_isUnknown_whileTheSizeIsUnknown() {
        assertNull(item(-1, 5000).percent)
        assertNull(item(0, 0).percent)
    }

    @Test
    fun percent_copesWithFilesOfManyGigabytes() {
        assertEquals(50, item(40_000_000_000L, 20_000_000_000L).percent)
    }
}
