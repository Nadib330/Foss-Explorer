package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.extension.CrxDownloader
import com.rpooi3.explorer.extension.ExtensionFiles
import com.rpooi3.explorer.extension.ExtensionPrompt
import com.rpooi3.explorer.extension.ExtensionScripts
import com.rpooi3.explorer.extension.InstallWait
import com.rpooi3.explorer.extension.ManifestParser
import com.rpooi3.explorer.extension.StoreHook
import com.rpooi3.explorer.extension.WebExtension
import com.rpooi3.explorer.privacy.ClearOnExit
import com.rpooi3.explorer.view.NinjaWebView
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * 18.0.0 beta 8: the seven things asked for after beta 7.
 *
 * 1. the blue strip of the Chrome Web Store and its dead button, 2. the question before the browser
 * closes, 3. Settings &gt; Privacy and security &gt; Advanced with what is cleared on exit, 4. a popup
 * that is more than a coloured rectangle, 5. extensions that run on the sites they name, 6. the
 * notice that restarts Explorer after a new theme, 7. the waiting while an extension is fetched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1800Beta8Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    @Before
    fun setUp() {
        prefs.edit()
            .putBoolean("welcome_seen", true)
            .remove("exit_confirm")
            .remove("restart_changed")
            .remove("restoreOnRestart")
            .commit()
        ClearOnExit.KEYS.forEach { prefs.edit().putBoolean(it, false).commit() }
        InstallWait.dismiss()
    }

    private fun source(path: String): String = File(path).readText()

    /** The first check box of a view, wherever the dialog put it. */
    private fun firstCheckBox(view: View?): android.widget.CheckBox? {
        if (view == null) return null
        if (view is android.widget.CheckBox) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = firstCheckBox(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private class Browser(val activity: BrowserActivity) {
        val content: android.view.ViewGroup = activity.findViewById(R.id.main_content)
        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun web(): NinjaWebView = (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<NinjaWebView>()
            .first()
    }

    private fun browser(block: Browser.() -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().use { controller ->
            Browser(controller.get()).apply {
                idle()
                block()
                idle()
            }
        }
    }

    // 1. The blue strip and the dead button of the store ----------------------------------------

    @Test
    fun theStore_losesItsBlueStrip_throughTheBridge() {
        // 18.1.0 beta (item 6) took every injection off the store — the strip, the zoom, the
        // relabelled button and Explorer's own. What is left is the bridge, which is a supported
        // compatibility layer and touches no markup: the store's own button installs through it.
        val script = StoreHook.script(context, "a".repeat(32), "t")
        assertTrue("the page is told it is talking to Chrome", script.contains("window.chrome"))
        assertTrue("and the store's install call is taken", script.contains("beginInstallWithManifest3"))
        assertTrue("by the browser's own installer", script.contains("installFromStore"))
        assertTrue("only on the store", script.contains("chromewebstore.google.com"))
        // Nothing that changes the page is left.
        val hook = source("src/main/java/com/rpooi3/explorer/extension/StoreHook.kt")
        assertFalse("no style sheet is injected", hook.contains("createElement('style')"))
        assertFalse("no page is zoomed", hook.contains("style.zoom"))
        assertFalse("no element is hidden", hook.contains("display', 'none'"))
        assertFalse("and no button of Explorer's own", hook.contains("explorer-install-host"))
    }

    // 2. The question before the browser closes -------------------------------------------------

    @Test
    fun thePowerButton_asksBeforeItLeaves() = browser {
        activity.findViewById<View>(R.id.button_menu).performClick()
        idle()
        activity.mainMenu.menu.performIdentifierAction(R.id.main_exit, 0)
        idle()
        val question = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(question.isShowing)
        assertEquals("Close Explorer?", question.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
        assertFalse("it does not leave by itself", activity.isFinishing)

        // Cancel keeps the browser.
        question.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertFalse(activity.isFinishing)

        // "Do not ask again" keeps the question away from then on.
        activity.mainMenu.menu.performIdentifierAction(R.id.main_exit, 0)
        idle()
        val second = ShadowDialog.getLatestDialog() as AlertDialog
        val never = firstCheckBox(second.findViewById<View>(androidx.appcompat.R.id.custom))!!
        assertEquals("Do not ask again", never.text.toString())
        never.isChecked = true
        second.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue("the browser was left", activity.isFinishing)
        assertFalse("and the question is not asked again", prefs.getBoolean("exit_confirm", true))
    }

    // 3. Advanced: what is cleared on exit ------------------------------------------------------

    @Test
    fun privacyAndSecurity_hasAdvanced_withTheSwitchesOfTheExit() {
        val xml = source("src/main/res/xml/preference_section_privacy.xml")
        assertTrue("a section of its own", xml.contains("android:key=\"advanced_group\""))
        assertTrue("named Advanced", xml.contains("@string/cr_advanced_section_title"))
        assertTrue("with the heading of the exit", xml.contains("@string/clear_on_exit_title"))
        for (key in ClearOnExit.KEYS) {
            assertTrue("the switch $key", xml.contains("android:key=\"$key\""))
            assertTrue("$key is a switch", xml.substring(xml.indexOf("android:key=\"$key\"") - 200)
                .contains("SwitchPreferenceCompat"))
        }
        assertEquals(
            listOf(
                "clear_on_exit_cache", "clear_on_exit_tabs", "clear_on_exit_history",
                "clear_on_exit_webstorage", "clear_on_exit_appcache",
            ),
            ClearOnExit.KEYS,
        )
        assertEquals("Clear data on exit", context.getString(R.string.clear_on_exit_title))
        assertEquals("Advanced", context.getString(R.string.cr_advanced_section_title))

        // Nothing chosen: nothing is deleted.
        assertFalse(ClearOnExit.isAnyChosen(prefs))
        assertTrue("nothing carried out", ClearOnExit.run(context, prefs, null).isEmpty())

        // The cache, the history and the app cache are carried out, and only those.
        prefs.edit()
            .putBoolean(ClearOnExit.KEY_CACHE, true)
            .putBoolean(ClearOnExit.KEY_HISTORY, true)
            .putBoolean(ClearOnExit.KEY_APP_CACHE, true)
            .commit()
        assertTrue(ClearOnExit.isAnyChosen(prefs))
        val cacheFile = File(context.cacheDir, "kept-by-the-cache.txt").apply {
            parentFile?.mkdirs()
            writeText("x")
        }
        val done = ClearOnExit.run(context, prefs, null)
        assertEquals(
            listOf(ClearOnExit.KEY_CACHE, ClearOnExit.KEY_HISTORY, ClearOnExit.KEY_APP_CACHE),
            done,
        )
        assertFalse("the cache folder is empty", cacheFile.exists())

        // The tabs are closed through the browser's own way of closing them.
        prefs.edit()
            .putBoolean(ClearOnExit.KEY_TABS, true)
            .putBoolean(ClearOnExit.KEY_CACHE, false)
            .putBoolean(ClearOnExit.KEY_HISTORY, false)
            .putBoolean(ClearOnExit.KEY_APP_CACHE, false)
            .commit()
        var closed = 0
        ClearOnExit.run(context, prefs, Runnable { closed++ })
        assertEquals("the tabs were closed", 1, closed)
    }

    // 4. A popup that is more than a coloured rectangle -----------------------------------------

    @Test
    fun aPopup_findsItsFiles_andSaysSoWhenItCannotBeShown() {
        // The files of an extension are served for its own pages whether the page names them
        // beside itself or from the root — which is how a bundled popup asks for them.
        val files = source("src/main/java/com/rpooi3/explorer/extension/ExtensionFiles.kt")
        assertTrue("the loader knows whose page it serves", files.contains("defaultExtension"))
        assertTrue("a folder is its index.html", files.contains("File(file, \"index.html\")"))
        assertTrue(
            "the popup passes its own id",
            source("src/main/java/com/rpooi3/explorer/extension/ExtensionPopup.kt")
                .contains("assetLoader(context.applicationContext, extension.id)"),
        )
        // A popup that could not be shown says so.
        val popup = source("src/main/java/com/rpooi3/explorer/extension/ExtensionPopup.kt")
        assertTrue("the page is looked at", popup.contains("childElementCount===0"))
        assertTrue("and the way out is offered", popup.contains("extensions_popup_reload"))
        assertTrue("with the extension's name", popup.contains("extensions_popup_failed"))
        assertEquals(
            "The popup of %1\$s did not open. The extension brings no page of its own, " +
                "or it failed to draw one.",
            context.getString(R.string.extensions_popup_failed, "%s").replace("%s", "%1\$s"),
        )
    }

    // 5. Extensions that run on the sites they name ---------------------------------------------

    @Test
    fun aContentScript_thatFailsEarly_isTriedAgain() {
        val scripts = source("src/main/java/com/rpooi3/explorer/extension/ExtensionScripts.kt")
        // The mark is set after the run, so a run that threw is tried again with the next callback.
        assertTrue("the mark is a result", scripts.contains("window[K]='ok'"))
        assertTrue("a failed run says so", scripts.contains("window[K]='fail'"))
        assertTrue("and is only skipped when it was done", scripts.contains("window[K]==='ok'"))
        assertTrue("the run waits for a document", scripts.contains("document.documentElement"))
        // And a bundle of a page built with a framework is no longer dropped in silence.
        assertTrue("a file may be 12 MB", scripts.contains("MAX_FILE_BYTES = 12L * 1024 * 1024"))
        assertTrue("and all of them 24 MB", scripts.contains("MAX_TOTAL_BYTES = 24L * 1024 * 1024"))

        // The guard is in the script the browser hands to the page, and it runs the body.
        val root = File(context.filesDir, "extensions/beta8-test").apply { mkdirs() }
        File(root, "content.js").writeText("document.documentElement.setAttribute('data-ran','1');")
        File(root, "manifest.json").writeText(
            """{"manifest_version":2,"name":"Beta 8","version":"1.0","content_scripts":[
               {"matches":["*://*/*"],"js":["content.js"],"run_at":"document_start"}]}""",
        )
        val extension = ManifestParser.parse("beta8-test", root)
        assertNotNull(extension)
        val injection = ExtensionScripts.injectionFor(
            context, extension!!, "https://example.org/page", WebExtension.RUN_AT_START, "t",
        )
        assertNotNull("something runs on the page", injection)
        assertTrue(injection!!.contains("document.documentElement"))
        assertTrue("the extension's own file is in it", injection.contains("data-ran"))
        assertFalse("and the file is read besides", injection.contains("data-explorer-hidden"))
    }

    @Test
    fun beforeManifestV3_theSitesOfAnExtension_stoodInPermissions() {
        val root = File(context.filesDir, "extensions/beta8-mv2").apply { mkdirs() }
        File(root, "manifest.json").writeText(
            """{"manifest_version":2,"name":"Old one","version":"1.0",
               "permissions":["*://*.example.org/*","storage","tabs"],
               "content_scripts":[{"matches":["<all_urls>"],"js":[]}]}""",
        )
        val extension = ManifestParser.parse("beta8-mv2", root)
        assertNotNull(extension)
        assertEquals("the sites are apart from the interfaces", listOf("*://*.example.org/*"), extension!!.hostPermissions)
        assertEquals("and the interfaces stay", listOf("storage", "tabs"), extension.permissions)
        assertTrue("so it counts for the site it names", extension.worksOn("https://www.example.org/page"))
        assertFalse("and not for another", extension.worksOn("https://example.net/page"))

        // The background of an extension starts with the first page, not only on a page its
        // content scripts match.
        val runtime = source("src/main/java/com/rpooi3/explorer/extension/ExtensionRuntime.kt")
        assertTrue(runtime.contains("ExtensionStore.enabled(context)"))
        assertTrue(runtime.contains("startBackground"))
    }

    // 6. The notice that restarts Explorer ------------------------------------------------------

    @Test
    fun aNewTheme_isNoticed_andTheNoticeRestartsTheBrowser() {
        val browser = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        assertTrue("the notice is a dialog", browser.contains("restart_notice_title"))
        assertTrue("with one button", browser.contains("restart_notice_ok"))
        assertTrue("that restarts", browser.contains("void restartExplorer()"))
        assertTrue("and the snackbar of before is gone", !browser.contains("Snackbar.make(ninjaWebView, R.string.toast_restart"))
        assertEquals("Restart Explorer", context.getString(R.string.restart_notice_title))
        assertEquals("Restart", context.getString(R.string.restart_notice_ok))

        // 18.1.0 beta (item 5) moved the question to the settings, where the theme is changed;
        // what beta 8 keeps is the notice itself — its words, and the button that restarts.
        assertTrue(
            "the settings ask on the spot",
            source("src/main/java/com/rpooi3/explorer/fragment/Fragment_settings.java")
                .contains("RestartPrompt.show(getActivity())"),
        )
    }

    // 7. The waiting while an extension is fetched ----------------------------------------------

    @Test
    fun addingAnExtension_saysPleaseWait_untilTheQuestionIsThere() {
        assertEquals("Please wait", context.getString(R.string.extensions_wait_title))
        val prompt = source("src/main/java/com/rpooi3/explorer/extension/ExtensionPrompt.kt")
        assertTrue("the waiting starts with the fetch", prompt.contains("InstallWait.show(activity)"))
        assertTrue("and ends before the question", prompt.indexOf("InstallWait.dismiss()") < prompt.indexOf("ask(activity, result"))

        // Seen on the screen: the pop-up stands while the extension is fetched, and is gone when
        // the question of the permissions opens.
        val controller = Robolectric.buildActivity(Settings_Activity::class.java).setup()
        val activity = controller.get()
        InstallWait.show(activity)
        assertTrue("the pop-up is there", InstallWait.isShowing)
        assertEquals("Please wait", InstallWait.currentText())

        val staged = WebExtension(
            id = "abcdefghijklmnopabcdefghijklmnop",
            name = "Beta 8",
            version = "1.0",
            description = "",
            rootDir = File(context.filesDir, "staged").absolutePath,
            popupPath = null,
            optionsPath = null,
            iconPath = null,
            permissions = listOf("storage"),
            hostPermissions = listOf("*://*/*"),
            contentScripts = emptyList(),
            manifestVersion = 3,
        )
        ExtensionPrompt.ask(activity, staged, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("the waiting is over", InstallWait.isShowing)
        val question = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue("and the question is there", question.isShowing)
        assertTrue(
            question.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()
                .contains("Beta 8"),
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun theVersion_is18_0_0_beta8() {
        // 18.0.0 beta 9 moved the version on (26, 18.0.0-beta9); what beta 8 asks of it is that
        // it is at least the version of beta 8 and still of 18.0.0.
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertTrue("versionCode 25 or later", info.versionCode >= 25)
        assertTrue("18.", info.versionName.orEmpty().startsWith("18."))
    }
}
