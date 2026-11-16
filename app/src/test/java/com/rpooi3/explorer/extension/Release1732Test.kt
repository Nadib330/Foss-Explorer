package com.rpooi3.explorer.extension

import android.app.Application
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What 17.3.2 changes: the address the .crx is fetched from (17.3.1 built it with
 * `String.format`, which read the `%3D` and `%26` of it as format specifiers and threw), the
 * button of a Chrome Web Store page that now survives the page drawing itself again, the way
 * into an extension from the sheet of the three-dot menu, and "Send feedback" in Help and
 * support instead of About.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1732Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val extensionId = "cjpalhdlnbpafiamejdnhcphjbkeiagm"

    private val manifest = """
        {
          "manifest_version": 3,
          "name": "Ad blocker of the test",
          "version": "1.0",
          "action": { "default_popup": "popup/index.html" },
          "content_scripts": [ { "matches": ["<all_urls>"], "js": ["a.js"] } ]
        }
    """.trimIndent()

    @Before
    fun clean() {
        ExtensionStore.remove(context, extensionId)
    }

    // 1. The store ----------------------------------------------------------------------------

    @Test
    fun theUpdateAddress_carriesTheIdEncoded() {
        val url = CrxDownloader.updateUrl(extensionId)
        // This is the whole of item 1 of 17.3.2: no format specifier, the encoded id as it is.
        assertTrue(url, url.startsWith("https://clients2.google.com/service/update2/crx?"))
        assertTrue(url, url.contains("response=redirect"))
        assertTrue(url, url.contains("prodversion=130.0.0.0"))
        assertTrue(url, url.contains("acceptformat=crx2,crx3"))
        assertTrue(url, url.endsWith("x=id%3D$extensionId%26uc"))
        assertEquals(1, Regex("%3D").findAll(url).count())
        assertEquals(1, Regex("%26").findAll(url).count())
        // And it is an address, not an exception.
        assertEquals("clients2.google.com", URL(url).host)
    }

    @Test
    fun theStorePage_isStillRecognised_whateverItsForm() {
        listOf(
            "https://chromewebstore.google.com/detail/ublock-origin/$extensionId",
            "https://chromewebstore.google.com/detail/$extensionId?hl=en",
            "https://chrome.google.com/webstore/detail/$extensionId/ublock",
            "https://chromewebstore.google.com/detail/some-slug/$extensionId?authuser=0",
        ).forEach { address ->
            assertTrue(address, StoreHook.isExtensionPage(address))
            assertEquals(address, extensionId, CrxDownloader.extractExtensionId(address))
        }
        assertFalse(StoreHook.isExtensionPage("https://chromewebstore.google.com/category/extensions"))
        assertFalse(StoreHook.isExtensionPage("https://example.com/$extensionId"))
    }

    @Test
    fun theStorePage_isToldThatItIsChrome() {
        val script = StoreHook.script(context, extensionId, "the-token")
        // Without these the store shows "Switch to Chrome" instead of its install button.
        assertTrue(script, script.contains("window.chrome.webstore"))
        assertTrue(script, script.contains("window.chrome.app"))
        assertTrue(script, script.contains("window.chrome.runtime"))
        assertTrue(script, script.contains("window.chrome.csi"))
        assertTrue(script, script.contains("window.chrome.loadTimes"))
        // A tap on the store's own button calls the bridge, with the token and the id.
        assertTrue(script, script.contains("installFromStore(TOKEN, ID)"))
        assertTrue(script, script.contains("var TOKEN = 'the-token';"))
    }

    @Test
    fun theStoresOwnButton_saysAddToExplorer() {
        val script = StoreHook.script(context, extensionId, "the-token")
        // 17.3.2: no button of Explorer's own any more — the store's blue one is relabelled.
        assertFalse("no second button is put on the page", script.contains("createElement('button')"))
        assertFalse(script.contains("explorer-add-extension"))
        assertTrue("the store's button is found by its words", script.contains("'add to chrome'"))
        assertTrue("its label is written over", script.contains("data-explorer-extension"))
        assertTrue(script.contains("function relabel("))
        assertTrue(script.contains("LABEL"))
        assertEquals(
            "Add to Explorer",
            context.getString(R.string.extensions_site_hook),
        )
        assertTrue(script, script.contains("Add to Explorer"))
        // And it is looked after while the page lives, because the store draws it again.
        assertTrue(script, script.contains("MutationObserver"))
        assertTrue(script, script.contains("function apply()"))
        // Whatever is left of the old labels is still understood.
        assertTrue(script, script.contains("switch to chrome"))
    }

    @Test
    fun theButtonSaysAlreadyAdded_onceTheExtensionIsIn() {
        val before = StoreHook.script(context, extensionId, "t")
        assertTrue(before, before.contains(context.getString(R.string.extensions_site_hook)))
        assertTrue(before, before.contains("var INSTALLED = false;"))

        ExtensionStore.directoryOf(context, extensionId).mkdirs()
        ExtensionStore.add(
            context,
            WebExtension(
                id = extensionId,
                name = "Installed",
                version = "1",
                description = "",
                rootDir = ExtensionStore.directoryOf(context, extensionId).absolutePath,
                popupPath = null,
                optionsPath = null,
                iconPath = null,
                permissions = emptyList(),
                hostPermissions = emptyList(),
                contentScripts = emptyList(),
                manifestVersion = 3,
            ),
        )
        val after = StoreHook.script(context, extensionId, "t")
        assertTrue(after, after.contains("var INSTALLED = true;"))
        assertTrue(after, after.contains(context.getString(R.string.extensions_site_hook_done)))
    }

    // 2. The sheet of the three-dot menu ------------------------------------------------------

    @Test
    fun theSheet_offersToAddTheExtensionOfTheStorePageInFront() {
        val storePage = "https://chromewebstore.google.com/detail/x/$extensionId"
        Robolectric.buildActivity(Settings_Activity::class.java).setup().use { controller ->
            val themed = controller.get()
            var asked: String? = null
            val sheet = ExtensionMenuSheet(
                themed,
                storePage,
                {},
                {},
                {},
                { id -> asked = id },
            )
            val view = sheet.createView()
            val labels = labelsOf(view)
            assertTrue(labels.toString(), labels.contains(themed.getString(R.string.extensions_menu_add_page)))

            // A tap on it asks for that extension by its id.
            val row = rowWithLabel(view, themed.getString(R.string.extensions_menu_add_page))
            assertNotNull(row)
            row!!.performClick()
            assertEquals(extensionId, asked)
        }
    }

    @Test
    fun theSheet_offersNothingToAdd_onAnOrdinaryPage() {
        Robolectric.buildActivity(Settings_Activity::class.java).setup().use { controller ->
            val themed = controller.get()
            val sheet = ExtensionMenuSheet(themed, "https://example.com/", {}, {}, {}, {})
            val labels = labelsOf(sheet.createView())
            assertFalse(labels.toString(), labels.contains(themed.getString(R.string.extensions_menu_add_page)))
        }
    }

    // 3. Install, as the bridge does it -------------------------------------------------------

    @Test
    fun anInstallFromADownload_landsInTheStore_andTheCrxIsGone() {
        val crx = File(context.cacheDir, "test.crx")
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { out ->
            out.putNextEntry(ZipEntry("manifest.json"))
            out.write(manifest.toByteArray(Charsets.UTF_8))
            out.closeEntry()
        }
        val bytes = ByteArrayOutputStream().apply {
            write("Cr24".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(3, 0, 0, 0))       // version 3, little endian
            write(byteArrayOf(8, 0, 0, 0))       // length of the protobuf header
            write(ByteArray(8))                  // the header itself
            write(zip.toByteArray())
        }.toByteArray()
        crx.writeBytes(bytes)

        var downloaded: String? = null
        var downloadedTo: File? = null
        val installed = CrxDownloader.installFrom(
            context,
            "https://chromewebstore.google.com/detail/x/$extensionId",
            { id, target ->
                downloaded = id
                downloadedTo = target
                target.writeBytes(bytes)
                target
            },
        )
        assertNotNull(installed)
        assertEquals(extensionId, downloaded)
        assertEquals(File(context.cacheDir, "$extensionId.crx"), downloadedTo)
        assertEquals("Ad blocker of the test", installed!!.name)
        assertNotNull(ExtensionStore.find(context, extensionId))
        assertFalse("the .crx is deleted after it was unpacked", downloadedTo!!.exists())
    }

    @Test
    fun aStoreAnswerThatIsNoCrx_isReported() {
        var reason: String? = null
        val installed = CrxDownloader.installFrom(
            context,
            extensionId,
            { _, target ->
                target.writeText("<html>not for you</html>")
                target
            },
            { reason = it },
        )
        assertNull(installed)
        assertNotNull("it says what went wrong", reason)
    }

    // The little helpers of the two sheet tests -----------------------------------------------

    private fun labelsOf(view: View): List<String> {
        val labels = ArrayList<String>()
        fun walk(node: View) {
            if (node is TextView && node.id == R.id.menu_row_label) labels += node.text.toString()
            if (node is LinearLayout) {
                for (index in 0 until node.childCount) walk(node.getChildAt(index))
            }
        }
        walk(view)
        return labels
    }

    /** Every view below [view], itself included. */
    private fun views(view: View): List<View> {
        val all = ArrayList<View>()
        fun walk(node: View) {
            all += node
            if (node is LinearLayout) {
                for (index in 0 until node.childCount) walk(node.getChildAt(index))
            }
        }
        walk(view)
        return all
    }

    /** The row of the sheet that carries [label]: a row says its own name for accessibility. */
    private fun rowWithLabel(view: View, label: String): View? =
        views(view).firstOrNull { node ->
            node.contentDescription?.toString() == label &&
                node.findViewById<TextView?>(R.id.menu_row_label) != null
        }
}
