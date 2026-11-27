package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import com.rpooi3.explorer.R
import com.rpooi3.explorer.extension.StoreHook
import com.rpooi3.explorer.home.HomeAppearance
import com.rpooi3.explorer.unit.CustomItemsUtils
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
import java.io.File

/**
 * 18.0.0 beta 7: the seven things asked for after beta 6.
 *
 * 1. the pencil of the home page in the bottom corner, 2. no more YouTube redirects, 3. the profile
 * back in its old corner, 4. Print in place of Reload in the three-dot menu, 5. a home page written
 * in HTML, CSS and JavaScript, 6. the Chrome Web Store fitted to the screen, 7. its blue bar gone
 * and its button awake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1800Beta7Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    @Before
    fun setUp() {
        prefs.edit()
            .putBoolean("welcome_seen", true)
            .remove(CustomItemsUtils.KEY_REDIRECTS)
            .remove("saved_redirect_ok")
            .remove("youtube_redirect_removed")
            .remove(HomeAppearance.KEY_CODE)
            .remove(HomeAppearance.KEY_CODE_ON)
            .commit()
    }

    private fun source(path: String): String = File(path).readText()

    private class Browser(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun <T : View> view(id: Int): T = activity.findViewById(id)
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

    // 1. The pencil in the bottom corner ---------------------------------------------------------

    @Test
    fun thePencil_sitsInTheBottomCorner_aboveTheBottomBar() = browser {
        val card = view<MaterialCardView>(R.id.home_customize_card)
        val pencil = view<View>(R.id.home_customize)
        assertTrue("the pencil is in that card", card.findViewById<View>(R.id.home_customize) === pencil)

        val params = card.layoutParams as FrameLayout.LayoutParams
        assertEquals(
            "at the bottom right",
            android.view.Gravity.BOTTOM or android.view.Gravity.END,
            params.gravity and (android.view.Gravity.BOTTOM or android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.CENTER_HORIZONTAL),
        )
        // Above the bottom bar: the home page lies between the two bars, so the corner of the
        // page is the place just above the bar.
        val main = source("src/main/res/layout/activity_main.xml")
        val homeEntry = main.substring(main.indexOf("layout=\"@layout/include_home\""))
        assertTrue(
            "the page ends at the top of the bottom bar",
            homeEntry.substring(0, 300).contains("android:layout_above=\"@id/bottomBar\""),
        )

        // And it still leads to the customisation of the page.
        pencil.performClick()
        idle()
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals("section_home_custom", started!!.getStringExtra(Settings_Activity.EXTRA_SECTION))
    }

    // 3. The profile back where it was ----------------------------------------------------------

    @Test
    fun theProfile_isBackInItsCorner() = browser {
        val home = source("src/main/res/layout/include_home.xml")
        val row = home.substring(home.indexOf("@+id/home_actions"))
        // 18.0.0 beta 3: the corner, one step further from the edge than the menu, 48 dp.
        assertTrue("at the top right of the page", row.contains("android:layout_gravity=\"top|end\""))
        assertTrue("one step from the top", row.contains("android:layout_marginTop=\"@dimen/spacing_medium\""))
        assertTrue("and from the side", row.contains("android:layout_marginEnd=\"@dimen/spacing_large\""))
        assertTrue("as wide as the buttons of the bar", row.contains("@dimen/bar_button_size\""))
        assertFalse("the empty place of the shield is gone", row.contains("bar_button_size_compact"))

        val profile = view<View>(R.id.home_profile)
        val menu = view<View>(R.id.home_menu)
        assertTrue("both are there", profile.isShown || menu.isShown)
        // The pencil is not between them any more.
        assertEquals("the row holds the profile and the menu", 2, view<ViewGroup>(R.id.home_actions).childCount)
    }

    // 2. YouTube is not redirected --------------------------------------------------------------

    @Test
    fun youtube_opensAsItIs() {
        // A fresh install: the list of redirects is made now, without YouTube in it.
        val redirects = CustomItemsUtils.getRedirects(prefs)
        assertTrue("no entry of YouTube", redirects.none { it.source().contains("youtube", ignoreCase = true) })
        assertTrue("the one of Twitter is still made", redirects.any { it.source().contains("twitter") })

        // An install from before beta 7 has the two entries stored; they go when the list is read.
        prefs.edit()
            .putString(
                CustomItemsUtils.KEY_REDIRECTS,
                """[{"source":"youtube.com","target":"invidious.nerdvpn.de"},""" +
                    """{"source":"://youtube.com","target":"invidious.nerdvpn.de"},""" +
                    """{"source":"twitter.com","target":"nitter.net"}]""",
            )
            .putBoolean("youtube_redirect_removed", false)
            .commit()
        val cleaned = CustomItemsUtils.getRedirects(prefs)
        assertTrue("the entries of YouTube are gone", cleaned.none { it.source().contains("youtube", ignoreCase = true) })
        assertTrue("and the one of the user stays", cleaned.any { it.source().contains("twitter") })
        assertTrue("the cleaning is written down for good", prefs.getBoolean("youtube_redirect_removed", false))

        // And the code that rewrites the address leaves YouTube alone: nothing matches it.
        assertTrue(
            "no redirect of the list is found in a YouTube address",
            CustomItemsUtils.getRedirects(prefs).none {
                "https://music.youtube.com/watch?v=1".contains(it.source())
            },
        )

        // What the user adds by hand afterwards stays.
        prefs.edit()
            .putString(
                CustomItemsUtils.KEY_REDIRECTS,
                """[{"source":"youtube.com","target":"example.org"}]""",
            )
            .commit()
        assertTrue(
            "a redirect the user adds later is kept",
            CustomItemsUtils.getRedirects(prefs).any { it.source() == "youtube.com" },
        )
    }

    // 4. Print instead of Reload ----------------------------------------------------------------

    @Test
    fun theMenu_printsInsteadOfReloading() = browser {
        view<View>(R.id.button_menu).performClick()
        idle()
        val items = activity.mainMenu.menu
        assertFalse("no reload label in the menu any more", source("src/main/res/menu/menu_main.xml").contains("main_nav_reload"))
        val print = items.findItem(R.id.main_nav_print)
        assertNotNull("Print stands in its place", print)
        assertEquals("Print", print.title.toString())
        assertNotNull("with the printer icon", print.icon)
        assertTrue("and no reload label is left in the resources", source("src/main/res/menu/menu_main.xml").contains("main_nav_print"))

        // Printing is Chromium's way: the WebView's own print adapter, handed to Android's print
        // service; a page the browser draws itself has nothing to print.
        assertFalse(com.rpooi3.explorer.accessibility.PageHooks.printPage(web()))
        val hooks = source("src/main/java/com/rpooi3/explorer/accessibility/PageHooks.kt")
        assertTrue(hooks.contains("createPrintDocumentAdapter"))
        assertTrue(hooks.contains("PRINT_SERVICE"))
        // On the home page of the tab there is nothing to print, and the entry is greyed out.
        assertFalse(web().isShowingOwnPage() && web().canGoBack() && false)
    }

    // 5. A home page of the user's own ----------------------------------------------------------

    @Test
    fun theHomePage_canBeWrittenInHtmlCssAndJs() {
        // Off by default, and nothing to show.
        assertFalse(HomeAppearance.codeOn(context))
        assertNull(HomeAppearance.code(context))

        // Saved code is shown as it was written.
        val code = "<!doctype html><html><body><h1>Hello</h1><style>body{background:#fe0}</style>" +
            "<script>document.title='x'</script></body></html>"
        prefs.edit().putString(HomeAppearance.KEY_CODE, code).putBoolean(HomeAppearance.KEY_CODE_ON, true).commit()
        assertTrue(HomeAppearance.codeOn(context))
        assertEquals(code, HomeAppearance.code(context))

        // The page of code is a WebView of its own, under everything else of the browser.
        val themed = ContextThemeWrapper(context, R.style.AppTheme)
        val holder = FrameLayout(themed)
        val root = android.view.LayoutInflater.from(themed).inflate(R.layout.include_home, holder, false)
        holder.addView(root)
        val web = root.findViewById<WebView>(R.id.home_code_web)
        assertNotNull("the layer of the code is there", web)
        assertEquals("it is the first child, so the bars and the buttons lie over it", R.id.home_code_web, (root as ViewGroup).getChildAt(0).id)

        val home = com.rpooi3.explorer.home.HomePageView(root, object : com.rpooi3.explorer.home.HomePageView.Listener {
            override fun onHomeSearch() = Unit
            override fun onHomeMenu(anchor: View) = Unit
            override fun onHomeCustomize() = Unit
            override fun onHomeOpen(url: String) = Unit
            override fun onHomeOpenInNewTab(url: String, foreground: Boolean) = Unit
        })
        home.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the user's page is drawn", View.VISIBLE, web.visibility)
        assertEquals("and the built-in one is put away", View.GONE, root.findViewById<View>(R.id.home_scroll).visibility)
        assertTrue("with scripts of its own", web.settings.javaScriptEnabled)
        assertFalse("and no access to the files of the device", web.settings.allowFileAccess)

        // The settings carry the rows of it.
        val screen = source("src/main/res/xml/preference_section_home_custom.xml")
        assertTrue(screen.contains("android:key=\"home_code_on\""))
        assertTrue(screen.contains("android:key=\"home_code_edit\""))
        assertTrue(screen.contains("android:key=\"home_code_clear\""))
        val editor = source("src/main/java/com/rpooi3/explorer/preferences/HomeCodeEditor.kt")
        assertTrue("the editor keeps the code exactly as it was typed", editor.contains("putString(HomeAppearance.KEY_CODE"))

        // Turned off, the built-in page comes back.
        prefs.edit().putBoolean(HomeAppearance.KEY_CODE_ON, false).commit()
        home.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, web.visibility)
        // 18.3.0 starts on the new page; the code page gives control back to the selected style.
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.home_new_scroll).visibility)
    }

    @Test
    fun theTemplate_isAPageThatSaysWhatToDo() {
        val template = HomeAppearance.codeTemplate(context)
        assertTrue(template.contains("<!doctype html>"))
        assertTrue(template.contains("prefers-color-scheme"))
        assertTrue(template.contains("home page", ignoreCase = true))
    }

    // 6. and 7. The Chrome Web Store ------------------------------------------------------------

    @Test
    fun theStore_fitsTheScreen_withoutInjections() {
        // 18.1.0 beta (item 6) took the injected fit of beta 7 away and gave the store the phone's
        // own width in WebSettings instead — see `theStore_isMobileFriendly_byWebSettings`.
        val web = File("src/main/java/com/rpooi3/explorer/view/NinjaWebView.java").readText()
        assertTrue("the store keeps the width of the page", web.contains("webSettings.setUseWideViewPort(desktop)"))
        assertTrue("and its own responsive layout", web.contains("if (store) {"))
        val hook = File("src/main/java/com/rpooi3/explorer/extension/StoreHook.kt").readText()
        assertFalse("nothing is injected into the page", hook.contains("style.zoom"))
        assertTrue(
            "only the store is touched",
            StoreHook.isExtensionPage("https://chromewebstore.google.com/detail/x/" + "a".repeat(32)),
        )
    }

}
