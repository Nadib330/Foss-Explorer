package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.home.HomePage
import com.rpooi3.explorer.view.TopBarController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The browser screen as a whole: toolbar at the top, home page for a new
 * tab, and the way the two react to opening pages.
 *
 * Robolectric's web view does not load anything; it only remembers the last
 * address it was given. That is enough here, because these tests are about
 * the app's own views around the web view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class BrowserChromeTest {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())

    private class Screen(val activity: BrowserActivity) {
        val main: View = activity.findViewById(R.id.main)
        val topBar: View = activity.findViewById(R.id.topBar)
        val addressBar: View = activity.findViewById(R.id.addressPill)
        val bottomBar: View = activity.findViewById(R.id.bottomBar)
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val home: View = activity.findViewById(R.id.home_page)
        val showToolbarButton: View = activity.findViewById(R.id.fab_showAppBar)
        val web: WebView get() = content.getChildAt(0) as WebView

        fun idle() = shadowOf(Looper.getMainLooper()).idle()

        /** Taps an entry of the action sheet that is currently open. */
        fun clickAction(title: Int) {
            val grid = ShadowDialog.getLatestDialog().findViewById<RecyclerView>(R.id.recyclerViewGrid)
            val adapter = grid.adapter!!
            val wanted = activity.getString(title)
            // Rows below the fold are not attached, so each row is built here the way the list would.
            for (position in 0 until adapter.itemCount) {
                val holder = adapter.createViewHolder(grid, adapter.getItemViewType(position))
                adapter.bindViewHolder(holder, position)
                if (holder.itemView.findViewById<TextView>(R.id.gridTitle).text.toString() == wanted) {
                    holder.itemView.performClick()
                    return
                }
            }
            throw AssertionError("no action \"$wanted\" in the sheet")
        }

        fun waitForTiles(): ViewGroup {
            val tiles = activity.findViewById<ViewGroup>(R.id.home_shortcuts)
            val deadline = System.currentTimeMillis() + 5_000
            while (tiles.childCount == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
                idle()
            }
            return tiles
        }
    }

    /** Starts the browser the way the launcher does and hands the screen to [block]. */
    private fun launch(block: Screen.() -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().use { controller ->
            Screen(controller.get()).apply {
                idle()
                block()
                idle()
            }
        }
    }

    @Test
    fun onAWebsite_theToolbarIsAtTheTop_andThePageStartsBelowIt() = launch {
        web.loadUrl("https://example.org/")
        idle()

        assertEquals(View.VISIBLE, topBar.visibility)
        assertEquals(0, topBar.top)
        assertEquals(activity.resources.getDimensionPixelSize(R.dimen.toolbar_height), topBar.height)
        assertEquals(main.width, topBar.width)

        assertEquals("bottom bar sits on the bottom edge", main.height, bottomBar.bottom)
        assertEquals(activity.resources.getDimensionPixelSize(R.dimen.bottom_bar_height), bottomBar.height)
        assertEquals(main.width, bottomBar.width)

        assertEquals("page starts where the top bar ends", topBar.bottom, content.top)
        assertEquals("page ends where the bottom bar starts", bottomBar.top, content.bottom)
        assertTrue(activity.findViewById<View>(R.id.button_back).isShown)
        assertTrue(activity.findViewById<View>(R.id.button_menu).isShown)
        assertEquals(View.GONE, showToolbarButton.visibility)
    }

    @Test
    fun onTheHomePage_thereIsNoTopBar_andTheHomePageStartsAtTheTop() = launch {
        assertEquals(View.VISIBLE, home.visibility)
        assertEquals(View.GONE, topBar.visibility)
        assertEquals("home page starts at the very top", 0, home.top)
        assertEquals("home page ends where the bottom bar starts", bottomBar.top, home.bottom)
        assertEquals(View.VISIBLE, bottomBar.visibility)

        // Without the bar, the three-dot menu is reached from the home page itself.
        val menuButton = home.findViewById<View>(R.id.home_menu)
        assertTrue(menuButton.isShown)
        menuButton.performClick()
        idle()
        assertTrue("the menu opens", activity.mainMenu.isShowing)
        assertNotNull(activity.mainMenu.menu.findItem(R.id.main_settings))
    }

    @Test
    fun firstLaunch_opensTheHomePage() = launch {
        assertEquals(HomePage.URL, web.url)
        assertEquals(View.VISIBLE, home.visibility)

        // The home page has its own search box and menu button; there is no top bar.
        assertEquals(View.GONE, topBar.visibility)
        assertFalse(activity.findViewById<View>(R.id.button_menu).isShown)
        assertTrue(home.findViewById<View>(R.id.home_search).isShown)
        assertTrue(home.findViewById<View>(R.id.home_menu).isShown)
        assertEquals(View.GONE, showToolbarButton.visibility)
    }

    @Test
    fun homePage_offersTiles() = launch {
        val rows = waitForTiles()

        assertTrue("at least one row of tiles", rows.childCount >= 1)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_shortcuts_card).visibility)
    }

    @Test
    fun openingAWebsite_replacesHomeWithTheAddressBar() = launch {
        web.loadUrl("https://example.org/")

        assertEquals(View.GONE, home.visibility)
        assertEquals(View.VISIBLE, addressBar.visibility)
    }

    @Test
    fun returningToTheHomeAddress_bringsHomeBack() = launch {
        web.loadUrl("https://example.org/")
        web.loadUrl(HomePage.URL)

        assertEquals(View.VISIBLE, home.visibility)
        assertEquals(View.GONE, topBar.visibility)

        web.loadUrl("https://example.org/")
        idle()
        assertEquals("the bar is back on a website", View.VISIBLE, topBar.visibility)
        assertTrue(addressBar.isShown)
    }

    @Test
    fun pageLoadCallbacks_decideWhetherHomeShows() = launch {
        val client = shadowOf(web).webViewClient

        // A link or a redirect starts a load the app did not request itself.
        client.onPageStarted(web, "https://example.org/", null)
        assertEquals(View.GONE, home.visibility)

        // Nothing was committed (the fake web view is still on the blank page), so home returns.
        client.onPageFinished(web, HomePage.URL)
        assertEquals(View.VISIBLE, home.visibility)
    }

    @Test
    fun tappingATile_opensTheSiteInTheSameTab() = launch {
        val firstRow = waitForTiles().getChildAt(0) as ViewGroup
        val tile = firstRow.getChildAt(0)
        val name = tile.findViewById<TextView>(R.id.home_tile_title).text.toString()

        tile.performClick()

        // On a fresh install the first tile is the bookmark that the first start creates.
        assertEquals("FOSS Browser - WIKI", name)
        assertEquals("https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki", web.url)
        assertEquals(1, content.childCount)
        assertEquals(View.GONE, home.visibility)
    }

    @Test
    fun searchBox_opensTheAddressFieldOnThisScreen_empty_withTheSuggestionsBelow() = launch {
        val dialogsBefore = ShadowDialog.getShownDialogs().size
        home.findViewById<View>(R.id.home_search).performClick()
        idle()

        val panel = activity.findViewById<ViewGroup>(R.id.search_panel)
        val input = panel.findViewById<TextView>(R.id.search_input)
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals("no popup is opened for it", dialogsBefore, ShadowDialog.getShownDialogs().size)
        assertEquals("", input.text.toString())
        assertTrue("the field has the focus", input.hasFocus())

        // The field is the first thing in the panel, the suggestions come after it.
        val field = panel.findViewById<View>(R.id.search_field)
        val list = panel.findViewById<View>(R.id.list_search)
        assertEquals(0, panel.indexOfChild(field))
        assertTrue(panel.indexOfChild(list.parent as View) > panel.indexOfChild(field))
    }

    @Test
    fun addressBar_opensTheAddressFieldWithTheCurrentAddress() = launch {
        web.loadUrl("https://example.org/page")

        addressBar.performClick()
        idle()

        val input = activity.findViewById<TextView>(R.id.search_input)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.search_panel).visibility)
        assertEquals("https://example.org/page", input.text.toString())
    }

    @Test
    fun addressField_showsWhatIsTyped_asFirstSuggestion_andATapOpensIt() = launch {
        home.findViewById<View>(R.id.home_search).performClick()
        idle()
        val panel = activity.findViewById<ViewGroup>(R.id.search_panel)
        val input = panel.findViewById<TextView>(R.id.search_input)
        val queryRow = panel.findViewById<View>(R.id.search_query_row)
        val clear = panel.findViewById<View>(R.id.search_clear)
        assertEquals("nothing typed, nothing to suggest", View.GONE, queryRow.visibility)
        assertEquals(View.GONE, clear.visibility)

        input.text = "https://example.org/typed"
        idle()

        assertEquals(View.VISIBLE, queryRow.visibility)
        assertEquals("https://example.org/typed", panel.findViewById<TextView>(R.id.search_query_text).text.toString())
        assertEquals(View.VISIBLE, clear.visibility)

        queryRow.performClick()
        idle()

        assertEquals("the field closes", View.GONE, panel.visibility)
        assertEquals("https://example.org/typed", web.url)
    }

    @Test
    fun addressField_clearButtonEmptiesIt_andStaysOpen() = launch {
        web.loadUrl("https://example.org/page")
        addressBar.performClick()
        idle()
        val panel = activity.findViewById<ViewGroup>(R.id.search_panel)
        val input = panel.findViewById<TextView>(R.id.search_input)
        // The current address is not offered as a suggestion for itself.
        assertEquals(View.GONE, panel.findViewById<View>(R.id.search_query_row).visibility)

        panel.findViewById<View>(R.id.search_clear).performClick()
        idle()

        assertEquals("", input.text.toString())
        assertEquals(View.VISIBLE, panel.visibility)
    }

    @Test
    fun addressField_backKeyAndBackArrowCloseIt_withoutLeavingThePage() = launch {
        web.loadUrl("https://example.org/page")
        val panel = activity.findViewById<ViewGroup>(R.id.search_panel)

        addressBar.performClick()
        idle()
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals(View.GONE, panel.visibility)
        assertEquals("https://example.org/page", web.url)
        assertEquals("the tab is still there", 1, content.childCount)

        addressBar.performClick()
        idle()
        panel.findViewById<View>(R.id.search_back).performClick()
        idle()
        assertEquals(View.GONE, panel.visibility)
        assertEquals("https://example.org/page", web.url)
    }

    @Test
    fun pageActions_hideBothBars_andTheCornerButtonBringsThemBack() = launch {
        web.loadUrl("https://example.org/")

        addressBar.performLongClick()
        idle()
        clickAction(R.string.menu_hide_bars)
        idle()

        assertEquals(View.GONE, topBar.visibility)
        assertEquals(View.GONE, bottomBar.visibility)
        assertEquals(View.VISIBLE, showToolbarButton.visibility)
        assertEquals("page uses the freed space", 0, content.top)
        assertEquals(main.height, content.bottom)
        assertTrue(prefs.getBoolean(TopBarController.KEY_HIDDEN, false))

        showToolbarButton.performClick()
        idle()

        assertEquals(View.VISIBLE, topBar.visibility)
        assertEquals(View.VISIBLE, bottomBar.visibility)
        assertEquals(View.GONE, showToolbarButton.visibility)
        assertEquals(topBar.bottom, content.top)
        assertFalse(prefs.getBoolean(TopBarController.KEY_HIDDEN, true))
    }

    @Test
    fun hiddenToolbar_staysHiddenAfterARestart() {
        prefs.edit().putBoolean(TopBarController.KEY_HIDDEN, true).commit()

        launch {
            assertEquals(View.GONE, topBar.visibility)
            assertEquals(View.GONE, bottomBar.visibility)
            assertEquals(View.VISIBLE, showToolbarButton.visibility)
            assertEquals(0, content.top)
            // The home page is still usable: it does not depend on the toolbar.
            assertEquals(View.VISIBLE, home.visibility)
        }
    }

    @Test
    fun customStartPage_isOpenedInsteadOfTheHomePage() {
        prefs.edit().putString(HomePage.KEY_START_PAGE, "https://example.org/start").commit()

        launch {
            assertEquals("https://example.org/start", web.url)
            assertEquals(View.GONE, home.visibility)
            assertEquals(View.VISIBLE, addressBar.visibility)
        }
    }
}
