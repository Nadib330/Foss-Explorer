package com.rpooi3.explorer.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 16.1.0 (item 5): a download keeps its name and its extension. No ".bin" is appended to a
 * file that has a name and an extension of its own, and a name typed without an extension
 * gets the extension of the file.
 */
class Release1610DownloadNameTest {

    @Test
    fun aTypedName_keepsWhatItSays() {
        assertEquals("report.pdf", NinjaDownloadListener.cleanFileName("report.pdf"))
        assertEquals("my report.final.pdf", NinjaDownloadListener.cleanFileName("my report.final.pdf"))
        assertEquals("no ending", NinjaDownloadListener.cleanFileName("no ending"))
        assertEquals("no trailing dot", "report", NinjaDownloadListener.cleanFileName("report..."))
        assertEquals("a slash is no path", "a_b.pdf", NinjaDownloadListener.cleanFileName("a/b.pdf"))
        assertEquals("nothing", "", NinjaDownloadListener.cleanFileName("   "))
    }

    @Test
    fun aNameWithoutAnExtension_getsTheOneOfTheFile() {
        assertEquals("my report.pdf", NinjaDownloadListener.keepExtension("my report", "download.pdf"))
        assertEquals("my report.pdf", NinjaDownloadListener.keepExtension("my report", "download.PDF"))
        assertEquals("kept", "my report.zip", NinjaDownloadListener.keepExtension("my report.zip", "download.pdf"))
        assertEquals("nothing to add", "my report", NinjaDownloadListener.keepExtension("my report", "download"))
        assertEquals("", NinjaDownloadListener.keepExtension("", "download.pdf"))
    }
}
