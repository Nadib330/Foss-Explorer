package com.rpooi3.explorer.activity

import android.app.DownloadManager
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.BrowserContainer
import com.rpooi3.explorer.database.RecordAction
import com.rpooi3.explorer.home.HomePage
import com.rpooi3.explorer.view.NinjaWebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import com.rpooi3.explorer.downloads.DownloadsActivity
import com.rpooi3.explorer.library.BookmarksActivity
import com.rpooi3.explorer.library.HistoryActivity
import com.google.android.material.card.MaterialCardView
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The top bar (back, address pill with share, three-dot menu),
 * the bottom bar (home, new tab, tabs), the menu behind the three dots and
 * private tabs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class BarsAndMenuTest {
    /**
     * 16.1.0: the browser opens the welcome of the first start (item 4). These tests are about
     * the browser itself, so the welcome is marked as seen before the activity is built.
     */
    @org.junit.Before
    fun markTheWelcomeAsSeen() {
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            .edit().putBoolean("welcome_seen", true).commit()
    }


    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private class Screen(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val pill: View = activity.findViewById(R.id.addressPill)
        val siteName: TextView = activity.findViewById(R.id.appBar_title)
        val tabCount: TextView = activity.findViewById(R.id.tab_count)
        val web: WebView get() = content.getChildAt(0) as WebView

        fun idle() = shadowOf(Looper.getMainLooper()).idle()

        fun <T : View> view(id: Int): T = activity.findViewById(id)

        fun open(url: String) {
            web.loadUrl(url)
            activity.updateOmniBox()
            idle()
        }

        /** Opens the three-dot menu and returns the titles of its entries, in order. */
        fun menuTitles(): List<String> {
            view<View>(R.id.button_menu).performClick()
            idle()
            val menu = activity.mainMenu.menu
            return (0 until menu.size()).map { menu.getItem(it) }.filter { it.isVisible }.map { it.title.toString() }
        }

        /** Opens the three-dot menu and taps one entry. */
        fun chooseFromMenu(id: Int) {
            view<View>(R.id.button_menu).performClick()
            idle()
            assertTrue(activity.mainMenu.menu.performIdentifierAction(id, 0))
            idle()
        }

        /** The text handed to the system share sheet by the last share action. */
        fun sharedText(): String? {
            val chooser = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            return send.getStringExtra(Intent.EXTRA_TEXT)
        }

        /** The entries of the action sheet that is open, as (title, row to tap). */
        fun actions(): List<Pair<String, View>> {
            val grid = ShadowDialog.getLatestDialog().findViewById<RecyclerView>(R.id.recyclerViewGrid)
            val adapter = grid.adapter!!
            // Rows below the fold are not attached, so they are built here the way the list would.
            return (0 until adapter.itemCount).map { position ->
                val holder = adapter.createViewHolder(grid, adapter.getItemViewType(position))
                adapter.bindViewHolder(holder, position)
                holder.itemView.findViewById<TextView>(R.id.gridTitle).text.toString() to holder.itemView
            }
        }

    }

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

    private fun historyUrls(): List<String> {
        val action = RecordAction(context)
        action.open(false)
        try {
            return action.listHistory(context).map { it.url }
        } finally {
            action.close()
        }
    }

    // ---------------------------------------------------------------- top bar

    @Test
    fun topBar_isBackArrow_addressPill_menuButton_inThatOrder() = launch {
        open("https://example.org/")
        val back = view<View>(R.id.button_back)
        val menu = view<View>(R.id.button_menu)

        assertTrue(back.isShown && pill.isShown && menu.isShown)
        assertTrue("back, then pill", back.right <= pill.left)
        assertTrue("pill, then menu", pill.right <= menu.left)
        // On the narrowest phones (320dp) the shield (15.0.0) leaves the pill exactly half of the width.
        assertTrue("the pill takes the largest part of the width", pill.width >= content.width / 2)
        assertTrue(pill.width > 3 * menu.width)
    }

    @Test
    fun topBar_hasNoBookmarkAndNoShieldButton() = launch {
        for (name in listOf("fab_overview", "fab_menu")) {
            @Suppress("DiscouragedApi")
            val id = activity.resources.getIdentifier(name, "id", activity.packageName)
            if (id != 0) assertNull("$name is gone from the screen", activity.findViewById<View>(id))
        }
    }

    @Test
    fun addressPill_showsTheSiteName_centred_withShareInside() = launch {
        open("https://example.org/articles/long/path?x=1")

        assertEquals("example.org", siteName.text.toString())

        val share = view<View>(R.id.button_share)
        assertTrue("share is inside the pill", share.parent === pill)
        assertEquals("share is at the end of the pill", pill.width, share.right)
        val pillCentre = pill.width / 2
        // The name sits in a group with the private-tab mark; the group is a child of the pill.
        val group = siteName.parent as View
        assertTrue(group.parent === pill)
        val textCentre = group.left + (siteName.left + siteName.right) / 2
        assertTrue("site name is centred in the pill", kotlin.math.abs(pillCentre - textCentre) <= 1)
    }

    @Test
    fun shareIcon_opensTheShareDialog_forAWebsite() = launch {
        open("https://example.org/article")

        view<View>(R.id.button_share).performClick()
        idle()

        assertEquals("https://example.org/article", sharedText())
    }

    @Test
    fun backArrow_isDisabledWhenThereIsNothingToGoBackTo() = launch {
        open("https://example.org/")

        assertFalse(view<View>(R.id.button_back).isEnabled)
    }

    // ------------------------------------------------------------------- menu

    @Test
    fun menu_hasExactlyTheEightEntries_withoutHelp() = launch {
        assertEquals(
            listOf("New tab", "New private tab", "History", "Downloads", "Bookmarks", "Share", "Desktop site", "Settings"),
            menuTitles(),
        )
    }

    @Test
    fun menu_tappingARow_runsTheEntry_andClosesTheMenu() = launch {
        view<View>(R.id.button_menu).performClick()
        idle()

        activity.mainMenu.contentView!!.findViewById<View>(R.id.main_settings).performClick()
        idle()

        assertEquals(Settings_Activity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        assertFalse(activity.mainMenu.isShowing)
    }

    @Test
    fun menu_aDisabledEntry_isGreyedOut_andDoesNothing() = launch {
        // On the home page there is nothing to share.
        view<View>(R.id.button_menu).performClick()
        idle()

        val share = activity.mainMenu.contentView!!.findViewById<View>(R.id.main_share)
        assertFalse(share.isEnabled)
        assertTrue(share.alpha < 0.5f)
        share.performClick()
        idle()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun menu_newTab_opensASecondTabOnTheHomePage() = launch {
        open("https://example.org/")

        chooseFromMenu(R.id.main_new_tab)

        assertEquals(2, BrowserContainer.size())
        assertEquals(HomePage.URL, web.url)
        assertFalse((web as NinjaWebView).isPrivateTab)
        assertEquals("2", tabCount.text.toString())
    }

    @Test
    fun menu_history_opensTheHistoryScreen() = launch {
        chooseFromMenu(R.id.main_history)

        assertEquals(HistoryActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }

    @Test
    fun menu_bookmarks_opensTheBookmarksScreen() = launch {
        chooseFromMenu(R.id.main_bookmarks)

        assertEquals(BookmarksActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }

    @Test
    fun menu_downloads_opensTheBrowsersOwnDownloadsScreen() = launch {
        chooseFromMenu(R.id.main_downloads)

        assertEquals(DownloadsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }

    @Test
    fun menu_settings_opensTheSettings() = launch {
        chooseFromMenu(R.id.main_settings)

        assertEquals(Settings_Activity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }

    @Test
    fun menu_share_isOffOnTheHomePage_andOnForAWebsite() = launch {
        view<View>(R.id.button_menu).performClick()
        assertFalse(activity.mainMenu.menu.findItem(R.id.main_share).isEnabled)

        open("https://example.org/")
        view<View>(R.id.button_menu).performClick()
        assertTrue(activity.mainMenu.menu.findItem(R.id.main_share).isEnabled)

        chooseFromMenu(R.id.main_share)
        assertEquals("https://example.org/", sharedText())
    }

    // ----------------------------------------------------------- page actions

    @Test
    fun longPressOnTheAddress_offersPageActions_withoutTheMenuEntries() = launch {
        open("https://example.org/")

        pill.performLongClick()
        idle()

        val titles = actions().map { it.first }
        assertTrue(titles.toString(), titles.containsAll(listOf("Reload", "Search on site", "Save as bookmark", "Hide toolbars")))
        for (moved in listOf("Settings", "Help", "Open downloads", "Share link", "New tab (foreground)")) {
            assertFalse("$moved belongs to the three-dot menu", moved in titles)
        }
    }

    // ------------------------------------------------------------- bottom bar

    @Test
    fun bottomBar_isHome_newTab_tabs_inThatOrder() = launch {
        val home = view<View>(R.id.button_home)
        val plus = view<View>(R.id.button_newTab)
        val tabs = view<View>(R.id.button_tabs)
        fun centre(v: View): Int {
            val pos = IntArray(2)
            v.getLocationInWindow(pos)
            return pos[0] + v.width / 2
        }

        assertTrue(home.isShown && plus.isShown && tabs.isShown)
        assertTrue(centre(home) < centre(plus) && centre(plus) < centre(tabs))
        assertTrue("the + is in the middle", kotlin.math.abs(centre(plus) - content.width / 2) <= 1)
        assertEquals("1", tabCount.text.toString())
    }

    @Test
    fun bottomBar_home_returnsToTheHomePage() = launch {
        open("https://example.org/")

        view<View>(R.id.button_home).performClick()
        idle()

        assertEquals(HomePage.URL, web.url)
        assertEquals(View.VISIBLE, view<View>(R.id.home_page).visibility)
        assertEquals(1, BrowserContainer.size())
    }

    @Test
    fun bottomBar_plus_opensANewTab_andTheCounterFollows() = launch {
        view<View>(R.id.button_newTab).performClick()
        idle()
        view<View>(R.id.button_newTab).performClick()
        idle()

        assertEquals(3, BrowserContainer.size())
        assertEquals("3", tabCount.text.toString())
    }

    @Test
    fun bottomBar_tabs_opensTheTabSwitcher_overTheWholeScreen() = launch {
        view<View>(R.id.button_tabs).performClick()
        idle()

        assertTrue(activity.isTabSwitcherOpen)
        val switcher = view<View>(R.id.tab_switcher)
        assertEquals(View.VISIBLE, switcher.visibility)
        assertEquals("as large as the screen's content area", view<View>(R.id.main).height -
            view<View>(R.id.main).paddingTop - view<View>(R.id.main).paddingBottom, switcher.height)
        assertTrue(
            "not a dialog any more",
            ShadowDialog.getShownDialogs().none { it.isShowing && it.findViewById<View?>(R.id.listTabs) != null },
        )
    }

    // ----------------------------------------------------------- private tabs

    @Test
    fun privateTab_isMarked_andLeavesNoHistory() = launch {
        // Control: a normal tab does write history.
        open("https://example.org/normal")
        shadowOf(web).webViewClient.onPageFinished(web, "https://example.org/normal")
        assertTrue("normal tabs record history", "https://example.org/normal" in historyUrls())
        assertEquals(View.GONE, view<View>(R.id.icon_private).visibility)

        chooseFromMenu(R.id.main_new_private_tab)

        assertEquals(2, BrowserContainer.size())
        assertTrue((web as NinjaWebView).isPrivateTab)
        assertEquals(HomePage.URL, web.url)

        open("https://example.org/secret")
        shadowOf(web).webViewClient.onPageFinished(web, "https://example.org/secret")
        idle()

        assertEquals(View.VISIBLE, view<View>(R.id.icon_private).visibility)
        assertFalse("private tabs record no history", "https://example.org/secret" in historyUrls())
        assertFalse(
            "private tabs are not written to the restore list",
            prefs.getString("openTabs", "")!!.contains("secret"),
        )
        assertNotNull(prefs.getString("openTabs", null))
        assertTrue(prefs.getString("openTabs", "")!!.contains("https://example.org/normal"))
        assertFalse(prefs.getString("mCurrentUrl", "")!!.contains("secret"))
    }

    @Test
    fun privateTab_saysHonestlyThatCookiesAreShared_whenTheWebViewCannotSeparateThem() = launch {
        chooseFromMenu(R.id.main_new_private_tab)

        // The WebView of the test environment has no profile support.
        assertFalse((web as NinjaWebView).isPrivateIsolated)
        val snackbarText = content.rootView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        assertEquals(activity.getString(R.string.private_tab_shared), snackbarText.text.toString())
    }
}
