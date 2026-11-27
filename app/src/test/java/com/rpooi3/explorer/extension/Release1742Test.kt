package com.rpooi3.explorer.extension

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What 17.4.2 fixes, both from the screenshot of Settings &gt; Extensions:
 *
 *  - the list showed `__MSG_app_name_chrome__` and `__MSG_extName__` instead of the names of the
 *    extensions, because a published extension keeps its words in `_locales` and puts
 *    `__MSG_key__` in its manifest — the placeholder is now resolved (extension/Locales);
 *  - tapping an extension showed its page as raw HTML (`<!doctype html> …`) instead of drawing
 *    it, because the file was handed to the WebView with its charset inside the mime type and no
 *    encoding; the two are separate now (extension/ExtensionFiles), which is what a WebView
 *    needs to render a page rather than print its source. A tap on a row also opens the
 *    extension's own interface now (its popup, or its options page), not a list of actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1742Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val extensionId = "cjpalhdlnbpafiamejdnhcphjbkeiagm"

    /** A manifest as a published extension carries it: placeholders, and the words in `_locales`. */
    private val manifest = """
        {
          "manifest_version": 3,
          "name": "__MSG_app_name_chrome__",
          "description": "__MSG_app_desc__",
          "version": "5.0.5",
          "default_locale": "en",
          "permissions": ["storage", "tabs"],
          "host_permissions": ["http://*/*", "https://*/*"],
          "action": { "default_popup": "popup/index.html" },
          "background": { "service_worker": "background.js" }
        }
    """.trimIndent()

    private val messages = """
        {
          "app_name_chrome": { "message": "Settings : Chat with all AI models" },
          "app_desc": { "message": "A sidebar of \u0024NAME\u0024." , "placeholders": {
              "name": { "content": "Chat" } } }
        }
    """.trimIndent()

    @Before
    fun clean() {
        ExtensionStore.remove(context, extensionId)
        ExtensionStore.discard(context, extensionId)
    }

    /** A .crx with the manifest, the messages, a popup page and a background file. */
    private fun crx(withPopUp: Boolean = true): File {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            fun entry(name: String, text: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
            entry("manifest.json", manifest)
            entry("_locales/en/messages.json", messages)
            if (withPopUp) {
                entry(
                    "popup/index.html",
                    "<!doctype html><html><head><title>Settings</title></head><body>popup</body></html>",
                )
            }
            entry("popup/style.css", "body{color:red}")
            entry("popup/icon.png", "not really a png")
            entry("background.js", "chrome.runtime.onMessage.addListener(function(){});")
        }
        val bytes = ByteArrayOutputStream().apply {
            write("Cr24".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(3, 0, 0, 0))
            write(byteArrayOf(8, 0, 0, 0))
            write(ByteArray(8))
            write(zip.toByteArray())
        }.toByteArray()
        return File(context.cacheDir, "test-17.4.2.crx").apply { writeBytes(bytes) }
    }

    // 1. The words of `_locales`, not the placeholder ----------------------------------------

    @Test
    fun theName_isReadFromTheLocales_ofTheExtension() {
        val installed = ExtensionStore.install(context, crx(), extensionId)
        assertNotNull(installed)
        assertEquals("Settings : Chat with all AI models", installed!!.name)
        assertFalse("no placeholder on a screen", installed.name.contains("__MSG_"))
        // "$NAME$" is what the description of the manifest carried.
        assertEquals("A sidebar of Chat.", installed.description)
    }

    @Test
    fun aPlaceholderWithoutAMessage_fallsBackInsteadOfShowingItself() {
        val empty = Locales.resolve("__MSG_nothing_here__", emptyMap(), "fallback")
        assertEquals("fallback", empty)
        assertEquals("", Locales.resolve("__MSG_nothing_here__", emptyMap()))
        // A word that is not a placeholder is left alone.
        assertEquals("AdBlock", Locales.resolve("AdBlock", emptyMap()))
        assertTrue(Locales.hasPlaceholder("__MSG_app_name__"))
        assertTrue(Locales.hasPlaceholder("a __MSG_x__ here"))
        assertFalse(Locales.hasPlaceholder("no placeholder"))
        assertFalse(Locales.hasPlaceholder(null))
    }

    @Test
    fun theKeysOfMessagesJson_areMatchedWhateverTheirCase() {
        val messages = mapOf("app_name_chrome" to "Chat")
        assertEquals("Chat", Locales.resolve("__MSG_app_name_chrome__", messages))
        assertEquals("Chat", Locales.resolve("__MSG_APP_NAME_CHROME__", messages))
        assertEquals("Chat and Chat", Locales.resolve("__MSG_app_name_chrome__ and __MSG_app_name_chrome__", messages))
    }

    @Test
    fun anExtensionInstalledBefore17_4_2_getsItsNameBack() {
        val installed = ExtensionStore.install(context, crx(), extensionId)!!
        // What the list held before: the placeholder, written down as the name.
        val stale = installed.toJson().put("name", "__MSG_app_name_chrome__")
        context.getSharedPreferences("extensions", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("extensions_list", org.json.JSONArray().put(stale).toString())
            .commit()

        val fixed = ExtensionStore.find(context, extensionId)
        assertNotNull(fixed)
        assertEquals(
            "the manifest is read again instead of showing the placeholder",
            "Settings : Chat with all AI models",
            fixed!!.name,
        )
        // And it stays fixed in the list.
        assertEquals(
            "Settings : Chat with all AI models",
            ExtensionStore.find(context, extensionId)!!.name,
        )
    }

    // 2. A page is drawn, not printed ---------------------------------------------------------

    @Test
    fun aPageIsServedAsHtml_withTheCharsetBesideIt() {
        ExtensionStore.install(context, crx(), extensionId)
        val handler = ExtensionFiles.Handler(context)
        val page = handler.handle("$extensionId/popup/index.html")
        assertNotNull(page)
        // The mime type alone: a WebView takes "text/html; charset=utf-8" as no type at all on
        // some versions and shows the source of the page instead of drawing it.
        assertEquals("text/html", page!!.mimeType)
        assertEquals("utf-8", page.encoding)
        val body = page.data.bufferedReader().use { it.readText() }
        assertTrue(body, body.startsWith("<!doctype html>"))

        val css = handler.handle("$extensionId/popup/style.css")!!
        assertEquals("text/css", css.mimeType)
        assertEquals("utf-8", css.encoding)

        // A picture is not text and gets no encoding.
        val png = handler.handle("$extensionId/popup/icon.png")!!
        assertEquals("image/png", png.mimeType)
        assertNull(png.encoding)
    }

    @Test
    fun everyTextFile_explorerServes_hasItsCharsetApart() {
        for (name in listOf("a.html", "a.htm", "a.css", "a.js", "a.json", "a.svg", "a.txt")) {
            assertFalse(name, ExtensionFiles.mimeType(name).contains("charset"))
            assertEquals(name, "utf-8", ExtensionFiles.encodingOf(name))
        }
        assertNull(ExtensionFiles.encodingOf("a.png"))
        assertNull(ExtensionFiles.encodingOf("a.woff2"))
    }

    @Test
    fun theBackgroundPage_isServedTheSameWay() {
        ExtensionStore.install(context, crx(), extensionId)
        val page = ExtensionFiles.Handler(context)
            .handle("$extensionId/${ExtensionScripts.BACKGROUND_PAGE}")!!
        assertEquals("text/html", page.mimeType)
        assertEquals("utf-8", page.encoding)
    }

    // 3. A tap on an extension opens its own interface ---------------------------------------

    @Test
    fun thePopupOfAnExtension_isThePageOfItsFolder() {
        val installed = ExtensionStore.install(context, crx(), extensionId)!!
        val popup = ExtensionPopup(context, installed)
        popup.createView()
        val web = popup.webView()
        assertNotNull("a tap opens the extension itself", web)
        assertEquals(
            ExtensionScripts.resourceUrl(extensionId, "popup/index.html"),
            org.robolectric.Shadows.shadowOf(web!!).lastLoadedUrl,
        )
        assertEquals("Settings : Chat with all AI models", installed.name)
    }

    @Test
    fun theListOfExtensions_showsTheNameAndNotThePlaceholder() {
        ExtensionStore.install(context, crx(), extensionId)
        val activity = Robolectric.buildActivity(Settings_Activity::class.java).setup().get()
        val fragment = activity.supportFragmentManager.fragments
            .filterIsInstance<androidx.preference.PreferenceFragmentCompat>()
            .firstOrNull { it.findPreference<androidx.preference.Preference>("section_extensions") != null }
        if (fragment != null) {
            val category = fragment.findPreference<androidx.preference.PreferenceCategory>(
                "extensions_group_installed",
            )
            val titles = (0 until (category?.preferenceCount ?: 0))
                .map { category!!.getPreference(it).title.toString() }
            assertFalse("no placeholder in the list: $titles", titles.any { it.contains("__MSG_") })
        }
        // The note of the screen says what works now, not what did not work in 17.3.4.
        val note = context.getString(R.string.extensions_note_text)
        assertTrue(note, note.contains("background page"))
        assertFalse(note, note.contains("opens in a sheet"))
    }
}
