package com.rpooi3.explorer.extension

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import com.rpooi3.explorer.view.MainMenuSheet
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What 17.3.1 adds: the unpacking of a .crx of the Chrome Web Store, the manifest of an
 * extension, its storage, its content scripts, the file server of its popup, the two entries of
 * the menus and the settings screen behind them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1731Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val extensionId = "cjpalhdlnbpafiamejdnhcphjbkeiagm"

    private val manifest = """
        {
          "manifest_version": 3,
          "name": "Ad blocker of the test",
          "version": "1.2.3",
          "description": "Only for the tests.",
          "action": { "default_popup": "popup/index.html", "default_icon": "icon48.png" },
          "icons": { "16": "icon16.png", "48": "icon48.png" },
          "permissions": ["storage"],
          "host_permissions": ["https://example.com/*"],
          "content_scripts": [
            {
              "matches": ["https://example.com/*"],
              "js": ["content/hide.js"],
              "css": ["content/hide.css"]
            },
            {
              "matches": ["*://*.wikipedia.org/*"],
              "js": ["content/wiki.js"],
              "run_at": "document_end"
            }
          ]
        }
    """.trimIndent()

    @Before
    fun clean() {
        listOf(extensionId, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").forEach {
            ExtensionStore.remove(context, it)
        }
    }

    // The CRX file -----------------------------------------------------------------------------

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun le32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun crx(version: Int, zip: ByteArray, header: ByteArray = ByteArray(64)): File {
        val out = ByteArrayOutputStream()
        out.write("Cr24".toByteArray(Charsets.US_ASCII))
        out.write(le32(version))
        if (version == 3) {
            out.write(le32(header.size))
            out.write(header)
        } else {
            out.write(le32(header.size))
            out.write(le32(header.size))
            out.write(header)
            out.write(header)
        }
        out.write(zip)
        val file = File(context.cacheDir, "test-" + version + ".crx")
        file.writeBytes(out.toByteArray())
        return file
    }

    @Test
    fun aCrx3_isUnpacked() {
        val file = crx(3, zipOf("manifest.json" to manifest, "content/hide.js" to "document.title='x';"))
        val dest = File(context.cacheDir, "unpacked3")
        CrxExtractor.unpack(file, dest)
        assertTrue(File(dest, "manifest.json").isFile)
        assertEquals("document.title='x';", File(dest, "content/hide.js").readText())
    }

    @Test
    fun aCrx2_isUnpacked() {
        val file = crx(2, zipOf("manifest.json" to manifest))
        val dest = File(context.cacheDir, "unpacked2")
        CrxExtractor.unpack(file, dest)
        assertTrue(File(dest, "manifest.json").isFile)
    }

    @Test
    fun aFileThatIsNoCrx_isRefused() {
        val file = File(context.cacheDir, "not-a-crx.crx")
        file.writeText("this is no CRX file at all")
        assertFalse(CrxExtractor.isCrx(file))
        try {
            CrxExtractor.unpack(file, File(context.cacheDir, "unpacked-none"))
            fail("a file that is no CRX is refused")
        } catch (expected: IOException) {
            // as it should be
        }
    }

    @Test
    fun aZipSlip_isRefused() {
        val file = crx(3, zipOf("../../escape.txt" to "no"))
        try {
            CrxExtractor.unpack(file, File(context.cacheDir, "unpacked-slip"))
            fail("an entry that leaves the folder is refused")
        } catch (expected: SecurityException) {
            assertFalse(File(context.cacheDir, "escape.txt").exists())
        }
    }

    // The manifest -----------------------------------------------------------------------------

    @Test
    fun theManifest_isRead() {
        val dir = File(context.filesDir, "parse-test").apply { mkdirs() }
        File(dir, "manifest.json").writeText(manifest)
        val extension = ManifestParser.parse(extensionId, dir)
        assertNotNull(extension)
        extension!!
        assertEquals("Ad blocker of the test", extension.name)
        assertEquals("1.2.3", extension.version)
        assertEquals("popup/index.html", extension.popupPath)
        assertEquals(3, extension.manifestVersion)
        assertEquals(2, extension.contentScripts.size)
        assertEquals(listOf("content/hide.js"), extension.contentScripts[0].jsFiles)
        assertEquals(listOf("content/hide.css"), extension.contentScripts[0].cssFiles)
        // Where the manifest says nothing, a content script runs when the page is there.
        assertEquals(WebExtension.RUN_AT_IDLE, extension.contentScripts[0].runAt)
        assertEquals(WebExtension.RUN_AT_END, extension.contentScripts[1].runAt)
        assertTrue(extension.worksOn("https://example.com/page"))
        assertFalse(extension.worksOn("https://other.test/page"))
        dir.deleteRecursively()
    }

    @Test
    fun aManifestWithoutName_isNotAnExtension() {
        val dir = File(context.filesDir, "parse-broken").apply { mkdirs() }
        File(dir, "manifest.json").writeText("{\"version\": \"1\"}")
        assertNull(ManifestParser.parse(extensionId, dir))
        dir.deleteRecursively()
    }

    // The store --------------------------------------------------------------------------------

    @Test
    fun theIdOfAnExtension_isFoundInItsAddress() {
        assertEquals(
            extensionId,
            CrxDownloader.extractExtensionId("https://chromewebstore.google.com/detail/ublock-origin/$extensionId?hl=en"),
        )
        assertEquals(extensionId, CrxDownloader.extractExtensionId(extensionId))
        assertNull(CrxDownloader.extractExtensionId("https://example.com/nothing"))
        assertNull(CrxDownloader.extractExtensionId(null))
    }

    @Test
    fun theStore_isRecognised() {
        assertTrue(CrxDownloader.isStoreUrl("https://chromewebstore.google.com/detail/x/$extensionId"))
        assertTrue(CrxDownloader.isStoreUrl("https://chrome.google.com/webstore/detail/$extensionId"))
        assertFalse(CrxDownloader.isStoreUrl("https://example.com/"))
        assertTrue(CrxDownloader.isCrxUrl("https://clients2.google.com/service/update2/crx?id=x"))
        assertTrue(CrxDownloader.isCrxUrl("https://example.com/a.crx"))
        assertTrue(StoreHook.isExtensionPage("https://chromewebstore.google.com/detail/x/$extensionId"))
        assertFalse(StoreHook.isExtensionPage("https://chromewebstore.google.com/"))
    }

    @Test
    fun theButtonOfTheStorePage_asksTheStore() {
        // 17.3.2: the store's own button installs through the browser, without a second one being
        // added. 18.1.0 beta (item 6) left nothing but the bridge that call goes through.
        val script = StoreHook.script(context, extensionId, "the-token")
        assertTrue(script.contains("installFromStore(TOKEN, String(id))"))
        assertTrue(script.contains("var TOKEN = 'the-token';"))
        assertTrue(script.contains("var ID = (String(location.href).match(/[a-p]{32}/) || ['$extensionId'])[0];"))
        assertTrue("the store's install call is taken", script.contains("beginInstallWithManifest3"))
        assertFalse("and nothing of the page is marked", script.contains("data-explorer-extension"))
    }

    // Installing -------------------------------------------------------------------------------

    @Test
    fun anExtension_isInstalled_andRemovedAgain() {
        val crxFile = crx(3, zipOf("manifest.json" to manifest))
        val installed = ExtensionStore.install(context, crxFile, extensionId)
        assertNotNull(installed)
        assertEquals("Ad blocker of the test", installed!!.name)
        assertTrue(ExtensionStore.directoryOf(context, extensionId).isDirectory)
        assertEquals(1, ExtensionStore.installed(context).size)
        assertTrue(ExtensionStore.isEnabled(context, extensionId))
        assertEquals(1, ExtensionStore.forUrl(context, "https://example.com/page").size)

        ExtensionStore.setEnabled(context, extensionId, false)
        assertFalse(ExtensionStore.isEnabled(context, extensionId))
        assertTrue(ExtensionStore.forUrl(context, "https://example.com/page").isEmpty())
        ExtensionStore.setEnabled(context, extensionId, true)

        ExtensionStore.remove(context, extensionId)
        assertTrue(ExtensionStore.installed(context).isEmpty())
        assertFalse(ExtensionStore.directoryOf(context, extensionId).exists())
    }

    @Test
    fun anIdThatIsNoId_isNotInstalled() {
        val crxFile = crx(3, zipOf("manifest.json" to manifest))
        assertNull(ExtensionStore.install(context, crxFile, "not-an-id"))
    }

    @Test
    fun installFrom_reportsWhatWentWrong() {
        var message: String? = null
        val installed = CrxDownloader.installFrom(
            context,
            extensionId,
            { _, _ -> throw IOException("the store is not there") },
            { message = it },
        )
        assertNull(installed)
        assertEquals("the store is not there", message)
    }

    // The scripts ------------------------------------------------------------------------------

    @Test
    fun theMatchPatterns_areUnderstood() {
        assertTrue(ExtensionScripts.matches("<all_urls>", "https://example.com/a"))
        assertTrue(ExtensionScripts.matches("*://*.example.com/*", "https://example.com/a"))
        assertTrue(ExtensionScripts.matches("*://*.example.com/*", "https://www.example.com/a"))
        assertFalse(ExtensionScripts.matches("*://*.example.com/*", "https://sub.example.test/a"))
        assertTrue(ExtensionScripts.matches("https://example.com/path*", "https://example.com/path/deeper"))
        assertFalse(ExtensionScripts.matches("https://example.com/path*", "https://example.com/other"))
        assertFalse(ExtensionScripts.matches("nonsense", "https://example.com/"))
        assertFalse(ExtensionScripts.matches(null, "https://example.com/"))
        assertNull(ExtensionScripts.patternToRegex("no-separator"))
    }

    @Test
    fun theFilesOfAnExtension_haveAnAddressOfTheirOwn() {
        assertEquals(
            "https://${ExtensionScripts.DOMAIN}/$extensionId/popup/index.html",
            ExtensionScripts.resourceUrl(extensionId, "/popup/index.html"),
        )
        assertTrue(ExtensionScripts.isExtensionUrl(ExtensionScripts.resourceUrl(extensionId, "a.html")))
        assertEquals(
            extensionId,
            ExtensionScripts.extensionIdOf("https://${ExtensionScripts.DOMAIN}/$extensionId/a/b.css"),
        )
        assertFalse(ExtensionScripts.isExtensionUrl("https://example.com/"))
        // 17.4.2: the type alone; the charset travels beside it (ExtensionFiles.encodingOf), so
        // that a WebView draws the page instead of printing its source.
        assertEquals("text/html", ExtensionFiles.mimeType("popup/index.html"))
        assertEquals("utf-8", ExtensionFiles.encodingOf("popup/index.html"))
        assertEquals("text/javascript", ExtensionFiles.mimeType("a/b.js"))
        assertEquals("utf-8", ExtensionFiles.encodingOf("a/b.js"))
        assertEquals("image/png", ExtensionFiles.mimeType("icon48.png"))
        assertEquals(null, ExtensionFiles.encodingOf("icon48.png"))
    }

    @Test
    fun theShim_offersChromeRuntimeAndStorage() {
        val shim = ExtensionScripts.shim(extensionId, "the-token")
        assertTrue(shim.contains("chrome.runtime"))
        assertTrue(shim.contains("chrome.storage.local"))
        assertTrue(shim.contains("window.browser"))
        assertTrue(shim.contains("the-token"))
        assertTrue(shim.contains("chrome.runtime.getURL"))
        assertTrue(shim.contains(extensionId))
    }

    @Test
    fun theContentScripts_areRunAtTheRightTime() {
        val crxFile = crx(
            3,
            zipOf(
                "manifest.json" to manifest,
                "content/hide.js" to "document.body.dataset.hidden = 'yes';",
                "content/hide.css" to "body { background: red }",
                "content/wiki.js" to "document.title = document.title + ' (wiki)';",
            ),
        )
        val installed = ExtensionStore.install(context, crxFile, extensionId)!!
        val atIdle = ExtensionScripts.injectionFor(
            context,
            installed,
            "https://example.com/page",
            WebExtension.RUN_AT_IDLE,
            "the-token",
        )
        assertNotNull(atIdle)
        assertTrue(atIdle!!.contains("chrome.runtime"))
        assertTrue("the guard of the run", atIdle.contains("__explorerExt__"))
        assertTrue("the script of the rule", atIdle.contains("document.body.dataset.hidden"))
        assertTrue("the stylesheet of the rule", atIdle.contains("background: red"))
        // A page the extension does not ask for gets nothing.
        assertNull(
            ExtensionScripts.injectionFor(
                context,
                installed,
                "https://other.test/",
                WebExtension.RUN_AT_IDLE,
                "the-token",
            ),
        )
        // "document_end" is the second rule, for wikipedia only.
        val atEnd = ExtensionScripts.injectionFor(
            context,
            installed,
            "https://de.wikipedia.org/wiki/Test",
            WebExtension.RUN_AT_END,
            "the-token",
        )
        assertNotNull(atEnd)
        assertNull(
            ExtensionScripts.injectionFor(
                context,
                installed,
                "https://de.wikipedia.org/wiki/Test",
                WebExtension.RUN_AT_IDLE,
                "the-token",
            ),
        )
    }

    @Test
    fun aStylesheet_becomesAStyleElement() {
        val script = ExtensionScripts.styleScript("body{color:red}")
        assertTrue(script.contains("createElement('style')"))
        assertTrue(script.contains("\"body{color:red}\""))
    }

    // The storage of an extension --------------------------------------------------------------

    @Test
    fun theStorageOfAnExtension_isKept_andOnlyForIt() {
        val bridge = ChromeApiBridge(context)
        val token = bridge.newPageToken()
        assertEquals("{}", bridge.storageGet(token, extensionId))
        bridge.storageSet(token, extensionId, "{\"count\":2}")
        assertEquals("{\"count\":2}", bridge.storageGet(token, extensionId))
        assertEquals("{}", bridge.storageGet(token, "another"))
        // What is not an object is not kept.
        bridge.storageSet(token, extensionId, "not json")
        assertEquals("{\"count\":2}", bridge.storageGet(token, extensionId))
        // A page that does not know the token of the load gets nothing.
        assertEquals("{}", bridge.storageGet("wrong-token", extensionId))
        bridge.storageSet("wrong-token", extensionId, "{\"count\":9}")
        assertEquals("{\"count\":2}", bridge.storageGet(token, extensionId))
        // A new load has a new token; the token of the page before is no longer good.
        bridge.newPageToken()
        assertEquals("{}", bridge.storageGet(token, extensionId))
        context.getSharedPreferences("extension_storage", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theManifestOfAnExtension_isGivenToItsScripts() {
        val crxFile = crx(3, zipOf("manifest.json" to manifest))
        ExtensionStore.install(context, crxFile, extensionId)
        val bridge = ChromeApiBridge(context)
        val token = bridge.newPageToken()
        val returned = JSONObject(bridge.getManifest(token, extensionId))
        assertEquals("Ad blocker of the test", returned.getString("name"))
        assertEquals("{}", bridge.getManifest("wrong", extensionId))
    }

    // The menus and the screens ----------------------------------------------------------------

    @Test
    fun theMenu_hasTheExtensionBesideTheZoom() {
        val groups = MainMenuSheet.GROUPS
        val pair = groups.firstOrNull { group -> group.ids.contains(R.id.main_extension) }
        assertNotNull("the extension is in the menu", pair)
        assertTrue("and beside the zoom", pair!!.ids.contains(R.id.main_zoom))
        assertTrue("as two halves of a row", pair.tiles)
        assertEquals(2, pair.ids.size)
    }

    @Test
    fun theSettings_haveTheExtensionTile_andTheScreenBehindIt() {
        val sections = com.rpooi3.explorer.preferences.SettingsSections.ALL
        val section = sections.firstOrNull { it.key == "section_extensions" }
        assertNotNull("the tile is on the first screen", section)
        assertEquals("Extension", context.getString(R.string.extensions_title))
        assertEquals(R.string.extensions_title, section!!.title)
    }

    @Test
    fun theSettingsScreen_listsWhatIsInstalled() {
        val crxFile = crx(3, zipOf("manifest.json" to manifest))
        ExtensionStore.install(context, crxFile, extensionId)
        val intent = Intent(context, Settings_Activity::class.java)
            .putExtra(Settings_Activity.EXTRA_SECTION, "section_extensions")
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            val fragment = activity.supportFragmentManager.fragments
                .filterIsInstance<PreferenceFragmentCompat>()
                .first()
            val screen = fragment.preferenceScreen
            val row = screen.findPreference<Preference>("extension_row_$extensionId")
            assertNotNull("a row for the installed extension", row)
            assertEquals("Ad blocker of the test", row!!.title)
            assertTrue(
                "the row says where it works and that it is on",
                row.summary.toString().contains(context.getString(R.string.extensions_enabled)),
            )
            assertNotNull("the way to add one", screen.findPreference<Preference>("extensions_add"))
            assertNotNull("the way to the store", screen.findPreference<Preference>("extensions_store"))
            val note = screen.findPreference<Preference>("extensions_note")
            assertNotNull(note)
            assertFalse("the note is not selectable", note!!.isSelectable)
        }
    }

    @Test
    fun theJsonOfAnExtension_survivesTheRoundTrip() {
        val original = WebExtension(
            id = extensionId,
            name = "Test",
            version = "1",
            description = "",
            rootDir = "/tmp/x",
            popupPath = "popup.html",
            optionsPath = null,
            iconPath = null,
            permissions = listOf("storage"),
            hostPermissions = listOf("https://example.com/*"),
            contentScripts = listOf(
                WebExtension.ContentScriptRule(
                    listOf("https://example.com/*"),
                    listOf("a.js"),
                    listOf("a.css"),
                    WebExtension.RUN_AT_IDLE,
                ),
            ),
            manifestVersion = 3,
        )
        val copy = WebExtension.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original.id, copy.id)
        assertEquals(original.popupPath, copy.popupPath)
        assertEquals(1, copy.contentScripts.size)
        assertEquals(original.contentScripts[0].jsFiles, copy.contentScripts[0].jsFiles)
        assertEquals(original.contentScripts[0].runAt, copy.contentScripts[0].runAt)
        assertEquals(3, copy.manifestVersion)
        assertNull(copy.optionsPath)
        assertEquals(JSONArray(listOf("storage")).toString(), original.toJson().getJSONArray("permissions").toString())
    }
}
