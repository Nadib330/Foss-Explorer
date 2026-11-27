package com.rpooi3.explorer.extension

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWebView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What 17.4.0 changes: an extension is not added the moment its button is pressed. Its manifest is
 * read first, what it asks for is put in front of the person adding it — the name in quotes, "It
 * can:", a line for each permission, Cancel and Add extension, as Chrome does it — and only then
 * are its files moved out of the staging folder into its own. Clicking an added extension shows its
 * popup, and the extension's own parts (its popup, its options page, its background page) run with
 * the chrome.* interfaces, so it does its work and not only sits in the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1740Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val extensionId = "cjpalhdlnbpafiamejdnhcphjbkeiagm"

    private val manifest = """
        {
          "manifest_version": 3,
          "name": "Ad blocker of the test",
          "version": "1.0",
          "description": "Blocks the ads the pages of the web carry.",
          "permissions": ["storage", "tabs", "bookmarks", "notifications", "downloads"],
          "host_permissions": ["<all_urls>"],
          "action": { "default_popup": "popup/index.html" },
          "background": { "service_worker": "background.js" },
          "chrome_url_overrides": { "newtab": "newtab.html" }
        }
    """.trimIndent()

    @Before
    fun clean() {
        ExtensionStore.remove(context, extensionId)
        ExtensionStore.discard(context, extensionId)
    }

    /** A .crx with one manifest in it, as the store sends it. */
    private fun crx(): File {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry("manifest.json"))
            out.write(manifest.toByteArray(Charsets.UTF_8))
            out.closeEntry()
            out.putNextEntry(ZipEntry("popup/index.html"))
            out.write("<html><body>popup</body></html>".toByteArray(Charsets.UTF_8))
            out.closeEntry()
            out.putNextEntry(ZipEntry("background.js"))
            out.write("chrome.runtime.onMessage.addListener(function(){});".toByteArray(Charsets.UTF_8))
            out.closeEntry()
        }
        val bytes = ByteArrayOutputStream().apply {
            write("Cr24".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(3, 0, 0, 0))
            write(byteArrayOf(8, 0, 0, 0))
            write(ByteArray(8))
            write(zip.toByteArray())
        }.toByteArray()
        return File(context.cacheDir, "test-17.4.0.crx").apply { writeBytes(bytes) }
    }

    // 1. What the extension asks for, in the words of the prompt -------------------------------

    @Test
    fun theWarnings_areChromesOwnWords() {
        val staged = ExtensionStore.stage(context, crx(), extensionId)
        assertNotNull(staged)
        val warnings = ExtensionPrompt.warnings(context, staged!!)
        // All sites first, then the new tab, then the permissions the manifest names.
        assertEquals(context.getString(R.string.ext_warn_all_sites), warnings[0])
        assertEquals(context.getString(R.string.ext_warn_newtab), warnings[1])
        assertTrue(warnings.contains("Replace the page you see when opening a new tab"))
        assertTrue(warnings.contains("Read and change all your data on all websites"))
        assertTrue(warnings.contains("Read your browsing history"))
        assertTrue(warnings.contains("Read and change your bookmarks"))
        assertTrue(warnings.contains("Display notifications"))
        assertTrue(warnings.contains("Manage your downloads"))
        // storage is not worth a line, as in Chrome.
        assertEquals("storage says nothing", 6, warnings.size)
    }

    @Test
    fun theQuestion_isTheNameAndWhatItCanDo() {
        val staged = ExtensionStore.stage(context, crx(), extensionId)!!
        val title = context.getString(R.string.extensions_prompt_title, staged.name)
        assertEquals("Add \"Ad blocker of the test\"?", title)
        val message = ExtensionPrompt.message(context, staged)
        // As in the screenshot: "It can:", then one permission per line — no description of the
        // extension and no bullets.
        assertTrue(message, message.startsWith("It can:\n\n"))
        assertFalse("no description in the sheet", message.contains("Blocks the ads"))
        assertFalse("no bullets", message.contains("\u2022"))
        assertTrue(
            message,
            message.contains(
                "Read and change all your data on all websites\n\n" +
                    "Replace the page you see when opening a new tab",
            ),
        )
        assertEquals(
            "Add extension",
            context.getString(R.string.extensions_prompt_add),
        )
        assertEquals("Cancel", context.getString(R.string.app_cancel))
    }

    @Test
    fun anExtensionThatAsksForNothing_saysSo() {
        val nothing = WebExtension(
            id = extensionId,
            name = "Quiet",
            version = "1.0",
            description = "",
            rootDir = context.filesDir.absolutePath,
            popupPath = null,
            optionsPath = null,
            iconPath = null,
            permissions = listOf("storage", "alarms"),
            hostPermissions = emptyList(),
            contentScripts = emptyList(),
            manifestVersion = 3,
        )
        assertTrue(ExtensionPrompt.warnings(context, nothing).isEmpty())
        assertEquals(
            "It can:\n\n" + context.getString(R.string.extensions_prompt_nothing),
            ExtensionPrompt.message(context, nothing),
        )
    }

    @Test
    fun theHistoryPermission_isChromesOwnLongLine() {
        // "Read and change your browsing history on all your signed-in devices", as in the sheet
        // of the screenshot; the list of open tabs is the short line.
        val history = WebExtension(
            id = extensionId,
            name = "H",
            version = "1.0",
            description = "",
            rootDir = context.filesDir.absolutePath,
            popupPath = null,
            optionsPath = null,
            iconPath = null,
            permissions = listOf("history"),
            hostPermissions = emptyList(),
            contentScripts = emptyList(),
            manifestVersion = 3,
        )
        val tabs = WebExtension(
            id = extensionId,
            name = "T",
            version = "1.0",
            description = "",
            rootDir = context.filesDir.absolutePath,
            popupPath = null,
            optionsPath = null,
            iconPath = null,
            permissions = listOf("tabs"),
            hostPermissions = emptyList(),
            contentScripts = emptyList(),
            manifestVersion = 3,
        )
        assertEquals(
            listOf("Read and change your browsing history on all your signed-in devices"),
            ExtensionPrompt.warnings(context, history),
        )
        assertEquals(listOf("Read your browsing history"), ExtensionPrompt.warnings(context, tabs))
    }

    @Test
    fun theManifest_saysWhichNewTabPageItBrings() {
        val staged = ExtensionStore.stage(context, crx(), extensionId)!!
        assertEquals("newtab.html", staged.newTabPath)
        // And it survives being written down, so a popup is told the same.
        val again = WebExtension.fromJson(staged.toJson())
        assertEquals("newtab.html", again.newTabPath)
        assertEquals("background.js", again.backgroundPath)
    }

    // 2. Two steps: staged, then added ---------------------------------------------------------

    @Test
    fun aStagedExtension_isNotInstalledYet() {
        val staged = ExtensionStore.stage(context, crx(), extensionId)
        assertNotNull(staged)
        assertNull("nothing is in the store yet", ExtensionStore.find(context, extensionId))
        assertTrue(
            "its files wait in the staging folder",
            File(staged!!.rootDir).isDirectory,
        )
        assertEquals(ExtensionStore.stagingOf(context, extensionId).absolutePath, staged.rootDir)
    }

    @Test
    fun answeringYes_movesItIntoItsPlace() {
        val staged = ExtensionStore.stage(context, crx(), extensionId)!!
        val installed = ExtensionStore.accept(context, staged)
        assertNotNull(installed)
        assertEquals(extensionId, installed!!.id)
        assertNotNull("it is in the store now", ExtensionStore.find(context, extensionId))
        assertEquals(
            ExtensionStore.directoryOf(context, extensionId).absolutePath,
            installed.rootDir,
        )
        assertTrue("its popup came with it", File(installed.rootDir, "popup/index.html").isFile)
        assertFalse(
            "and the staging folder is gone",
            ExtensionStore.stagingOf(context, extensionId).exists(),
        )
    }

    @Test
    fun answeringNo_throwsItAway_andInstallsNothing() {
        val staged = ExtensionStore.stage(context, crx(), extensionId)!!
        ExtensionStore.discard(context, extensionId)
        assertFalse(ExtensionStore.stagingOf(context, extensionId).exists())
        assertNull(ExtensionStore.find(context, extensionId))
        assertNotNull("the question can be asked again", ExtensionStore.stage(context, crx(), extensionId))
    }

    @Test
    fun anIdThatIsNotAnExtensionId_isNotStaged() {
        assertNull(ExtensionStore.stage(context, crx(), "not-an-extension"))
        assertNull(ExtensionStore.stage(context, crx(), "../../etc"))
    }

    // 3. The extension does its work, it does not only sit in the list -------------------------

    @Test
    fun clickingAnExtension_opensItsOwnPageInABubble() {
        val installed = ExtensionStore.install(context, crx(), extensionId)!!
        val popup = ExtensionPopup(context, installed)
        val view = popup.createView()
        assertNotNull(view)
        val web = popup.webView()
        assertNotNull("the popup is a page of the extension, not a text", web)
        val loaded = org.robolectric.Shadows.shadowOf(web!!).lastLoadedUrl
        assertEquals(
            "its popup page, from the extension's own folder",
            ExtensionScripts.resourceUrl(extensionId, "popup/index.html"),
            loaded,
        )
        assertTrue(ExtensionScripts.isExtensionUrl(loaded))
    }

    @Test
    fun thePopupAndTheBackground_reachTheSameInterfaces() {
        // The popup and the background page are handed the shim of their own extension, so a
        // message from one finds the listeners of the other.
        val script = ExtensionScripts.shimBundle(extensionId, "a-token")
        assertTrue(script, script.contains("chrome"))
        assertTrue(script, script.contains("runtime"))
        assertTrue(script, script.contains("storage"))
        assertTrue(script, script.contains("__explorerExtDispatch"))
        // And a page of it in a tab is recognised as one of the extension's.
        assertEquals(
            extensionId,
            ExtensionScripts.extensionIdOf(ExtensionScripts.resourceUrl(extensionId, "options.html")),
        )
        assertTrue(
            ExtensionScripts.isExtensionUrl(ExtensionScripts.backgroundUrl(extensionId)),
        )
    }

    @Test
    fun theBackgroundPage_isServed_asAPageOfTheExtension() {
        val installed = ExtensionStore.install(context, crx(), extensionId)!!
        val handler = ExtensionFiles.Handler(context)
        val response = handler.handle("$extensionId/${ExtensionScripts.BACKGROUND_PAGE}")
        assertNotNull("the background page has somewhere to run", response)
        val body = response!!.data.bufferedReader().use { it.readText() }
        assertTrue(body, body.contains("background.js"))
        assertEquals("text/html", response.mimeType.substringBefore(';'))
        // Its file too.
        assertNotNull(handler.handle("$extensionId/background.js"))
        assertNull("but nothing above its folder", handler.handle("$extensionId/../manifest.json"))
        assertEquals(installed.id, extensionId)
    }

    // 4. Cromite is named in Legal information, below Chromium ---------------------------------

    @Test
    fun legalInformation_namesCromite_underChromium() {
        val xml = File(
            "src/main/res/xml/preference_section_legal.xml",
        ).takeIf { it.isFile } ?: File("app/src/main/res/xml/preference_section_legal.xml")
        assertTrue("the section is there", xml.isFile)
        val text = xml.readText()
        val chromium = text.indexOf("about_source_chromium")
        val cromite = text.indexOf("about_source_cromite")
        val firefox = text.indexOf("about_source_firefox")
        assertTrue("Cromite is a row of its own", cromite > 0)
        assertTrue("below Chromium", cromite > chromium)
        assertTrue("and above Firefox", cromite < firefox)

        assertEquals("Source code of Cromite", context.getString(R.string.about_source_cromite_title))
        assertEquals(
            "https://github.com/uazo/cromite",
            context.getString(R.string.about_source_cromite_url),
        )
        val summary = context.getString(R.string.about_source_cromite_summary)
        assertTrue(summary, summary.contains("GPL-3.0"))
        assertTrue(summary, summary.contains("Cromite"))
    }

    @Test
    fun theStore_onlyGetsTheOldLinuxAgent_whileTheRestGetsWindows() {
        // A small reminder of the other half of 17.4.0, next to the prompt: the agent of 17.3.3
        // is no longer sent to every site in desktop mode.
        val agent =
            "Mozilla/5.0 (Linux; Android 16; X6850 Build/BP2A.250605.031.A3) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.58 Mobile Safari/537.36"
        assertTrue(
            "a website in desktop mode is Chrome on Windows",
            com.rpooi3.explorer.browser.ChromeUserAgent.desktop(agent)
                .startsWith("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"),
        )
        assertTrue(
            "and the Chrome Web Store gets the old Linux agent",
            com.rpooi3.explorer.browser.ChromeUserAgent.store(agent)
                .startsWith("Mozilla/5.0 (X11; Linux x86_64)"),
        )
        assertEquals(
            "Linux",
            com.rpooi3.explorer.browser.ChromeUserAgent.platformOf(store = true, desktop = true),
        )
        assertEquals(
            "Windows",
            com.rpooi3.explorer.browser.ChromeUserAgent.platformOf(store = false, desktop = true),
        )
    }

    /** A shadow of a WebView, so that the test can read what was loaded into it. */
    private fun shadowOf(webView: android.webkit.WebView): ShadowWebView =
        org.robolectric.Shadows.shadowOf(webView)

    private val Context.unused: Int get() = 0
}
