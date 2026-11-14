package com.rpooi3.explorer.activity

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.BrowserContainer
import com.rpooi3.explorer.browser.ChromeUserAgent
import com.rpooi3.explorer.browser.NinjaWebViewClient
import com.rpooi3.explorer.browser.OfflinePage
import com.rpooi3.explorer.database.RecordAction
import com.rpooi3.explorer.home.HomePage
import com.rpooi3.explorer.view.NewTabAnimation
import com.rpooi3.explorer.view.NinjaWebView
import com.rpooi3.explorer.view.TabSwitcher
import com.rpooi3.explorer.view.TabThumbnailView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Release 12.1.2 on the browser screen: reload button in place of the loading
 * ring, the flat menu with "Desktop site", the full-screen tab switcher, the
 * new-tab animation, and the offline page with the dinosaur game.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1212Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private class Screen(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val web: NinjaWebView get() = content.getChildAt(content.childCount - 1) as NinjaWebView
        val reload: View = activity.findViewById(R.id.button_reload)
        val ring: View = activity.findViewById(R.id.menu_progress_bar)
        val switcher: View = activity.findViewById(R.id.tab_switcher)
        val grid: RecyclerView = activity.findViewById(R.id.tabs_grid)

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun <T : View> view(id: Int): T = activity.findViewById(id)

        fun open(url: String) {
            web.loadUrl(url)
            activity.updateOmniBox()
            idle()
        }

        fun chooseFromMenu(id: Int) {
            view<View>(R.id.button_menu).performClick()
            idle()
            assertTrue(activity.mainMenu.menu.performIdentifierAction(id, 0))
            idle()
        }

        /** The cards of the tab switcher, built the way the grid builds them. */
        fun cards(): List<View> {
            val adapter = grid.adapter!!
            return (0 until adapter.itemCount).map { position ->
                val holder = adapter.createViewHolder(grid, adapter.getItemViewType(position))
                adapter.bindViewHolder(holder, position)
                holder.itemView
            }
        }

        fun cardTitles(): List<String> =
            cards().map { it.findViewById<TextView>(R.id.tab_card_title).text.toString() }
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

    // ------------------------------------------------------- 1. reload button

    @Test
    fun whileAPageLoads_theRingShows_andAfterwardsTheReloadButtonTakesItsPlace() = launch {
        open("https://example.org/")

        activity.updateProgress(40)
        assertEquals(View.VISIBLE, ring.visibility)
        assertEquals(View.GONE, reload.visibility)

        activity.updateProgress(100)
        idle()
        assertEquals("the ring is gone", View.GONE, ring.visibility)
        assertEquals("and the reload button is there instead", View.VISIBLE, reload.visibility)
        assertEquals("same place: both are children of one slot", ring.parent, reload.parent)
        assertEquals("Reload this page", reload.contentDescription.toString())
    }

    @Test
    fun theReloadButton_reloadsThePage() = launch {
        open("https://example.org/")
        activity.updateProgress(100)
        idle()
        val before = shadowOf(web as WebView).reloadInvocations

        reload.performClick()
        idle()

        assertEquals(before + 1, shadowOf(web as WebView).reloadInvocations)
    }

    // ------------------------------------------------- 2. and 3. three-dot menu

    @Test
    fun theMenu_hasADesktopSiteSwitch_thatChangesWhatTheTabAsksFor() = launch {
        open("https://example.org/")
        val agent = WebSettings.getDefaultUserAgent(context)

        view<View>(R.id.button_menu).performClick()
        idle()
        val item = activity.mainMenu.menu.findItem(R.id.main_desktop_site)
        assertEquals("Desktop site", item.title.toString())
        assertTrue(item.isCheckable)
        assertFalse(item.isChecked)
        val titles = (0 until activity.mainMenu.menu.size()).map { activity.mainMenu.menu.getItem(it).title.toString() }
        assertEquals("just above Settings", titles.indexOf("Settings") - 1, titles.indexOf("Desktop site"))
        activity.mainMenu.dismiss()

        chooseFromMenu(R.id.main_desktop_site)
        assertTrue(web.isDesktopSite)
        assertEquals(ChromeUserAgent.desktop(agent), web.settings.userAgentString)
        view<View>(R.id.button_menu).performClick()
        idle()
        assertTrue("the box is ticked now", activity.mainMenu.menu.findItem(R.id.main_desktop_site).isChecked)
        activity.mainMenu.dismiss()

        chooseFromMenu(R.id.main_desktop_site)
        assertFalse(web.isDesktopSite)
        assertEquals(ChromeUserAgent.mobile(agent), web.settings.userAgentString)
    }

    @Test
    fun desktopSite_belongsToOneTab() = launch {
        open("https://example.org/")
        chooseFromMenu(R.id.main_desktop_site)
        val first = web

        view<View>(R.id.button_newTab).performClick()
        idle()

        assertTrue(first.isDesktopSite)
        assertFalse("a new tab starts as a mobile one", web.isDesktopSite)
    }

    // ------------------------------------------------------- 7. tab switcher

    @Test
    fun theTabSwitcher_showsEveryTabAsACard_andMarksTheOneInFront() = launch {
        open("https://example.org/")
        view<View>(R.id.button_newTab).performClick()
        idle()
        open("https://example.com/")

        view<View>(R.id.button_tabs).performClick()
        idle()

        assertEquals(View.VISIBLE, switcher.visibility)
        assertEquals(2, grid.adapter!!.itemCount)
        assertEquals(2, (grid.layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
        val cards = cards()
        assertEquals("the tab in front is marked", listOf(false, true), cards.map { it.isSelected })
        for (card in cards) {
            assertNotNull(card.findViewById<View>(R.id.tab_card_close))
            assertNotNull(card.findViewById<TabThumbnailView>(R.id.tab_card_thumbnail))
        }
        assertNotNull("the tab in front was pictured for its card", web.thumbnail)
    }

    @Test
    fun tappingACard_goesToThatTab_andClosesTheSwitcher() = launch {
        open("https://example.org/")
        val first = web
        view<View>(R.id.button_newTab).performClick()
        idle()
        view<View>(R.id.button_tabs).performClick()
        idle()

        cards()[0].performClick()
        idle()

        assertFalse(activity.isTabSwitcherOpen)
        assertEquals(View.GONE, switcher.visibility)
        assertEquals(first, web)
        assertEquals(2, BrowserContainer.size())
    }

    @Test
    fun theCross_closesATab_andTheLastTabBecomesAFreshOne() = launch {
        open("https://example.org/")
        view<View>(R.id.button_newTab).performClick()
        idle()
        view<View>(R.id.button_tabs).performClick()
        idle()

        cards()[0].findViewById<View>(R.id.tab_card_close).performClick()
        idle()
        assertEquals(1, BrowserContainer.size())
        assertEquals(1, grid.adapter!!.itemCount)
        assertTrue("the switcher stays open", activity.isTabSwitcherOpen)

        open("https://example.net/")
        cards()[0].findViewById<View>(R.id.tab_card_close).performClick()
        idle()
        assertEquals("there is always one tab", 1, BrowserContainer.size())
        assertEquals(HomePage.URL, web.url)
    }

    @Test
    fun newTab_inTheSwitcher_opensATabAndLeavesTheSwitcher() = launch {
        view<View>(R.id.button_tabs).performClick()
        idle()

        view<View>(R.id.tabs_new).performClick()
        idle()

        assertEquals(2, BrowserContainer.size())
        assertFalse(activity.isTabSwitcherOpen)
        assertFalse(web.isPrivateTab)
    }

    @Test
    fun theSwitcher_keepsPrivateTabsApart_likeChromesIncognitoPage() = launch {
        open("https://example.org/")
        chooseFromMenu(R.id.main_new_private_tab)
        view<View>(R.id.button_tabs).performClick()
        idle()

        val modes = view<MaterialButtonToggleGroup>(R.id.tabs_mode)
        assertEquals("opens on the kind of tab in front", R.id.tabs_mode_private, modes.checkedButtonId)
        assertEquals(1, grid.adapter!!.itemCount)

        modes.check(R.id.tabs_mode_normal)
        idle()
        assertEquals(1, grid.adapter!!.itemCount)
        assertEquals(View.GONE, view<View>(R.id.tabs_empty).visibility)

        // "New tab" follows the page that is shown.
        modes.check(R.id.tabs_mode_private)
        view<View>(R.id.tabs_new).performClick()
        idle()
        assertTrue(web.isPrivateTab)
        assertEquals(3, BrowserContainer.size())
    }

    @Test
    fun withoutPrivateTabs_thePrivatePageSaysSo() = launch {
        view<View>(R.id.button_tabs).performClick()
        idle()

        view<MaterialButtonToggleGroup>(R.id.tabs_mode).check(R.id.tabs_mode_private)
        idle()

        assertEquals(0, grid.adapter!!.itemCount)
        assertEquals(View.VISIBLE, view<View>(R.id.tabs_empty).visibility)
    }

    @Test
    fun closeAllTabs_asksFirst_andLeavesOneNewTab() = launch {
        open("https://example.org/")
        view<View>(R.id.button_newTab).performClick()
        idle()
        open("https://example.com/")
        view<View>(R.id.button_tabs).performClick()
        idle()

        view<View>(R.id.tabs_menu).performClick()
        idle()
        shadowOf(context).latestPopupWindow.contentView
            .findViewById<View>(R.id.tabs_menu_close_all).performClick()
        idle()

        // Nothing is closed before the question is answered.
        val question = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertTrue(question.isShowing)
        assertEquals(2, BrowserContainer.size())
        question.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertEquals(1, BrowserContainer.size())
        assertEquals(HomePage.URL, web.url)
    }

    @Test
    fun closingThePrivateTabs_leavesTheNormalOnes() = launch {
        open("https://example.org/")
        val normal = web
        chooseFromMenu(R.id.main_new_private_tab)
        chooseFromMenu(R.id.main_new_private_tab)
        assertEquals(3, BrowserContainer.size())

        activity.closeAllTabs(true)
        idle()

        assertEquals(1, BrowserContainer.size())
        assertEquals(normal, web)
    }

    @Test
    fun back_leavesTheTabSwitcher_withoutTouchingThePage() = launch {
        open("https://example.org/")
        view<View>(R.id.button_tabs).performClick()
        idle()

        activity.onBackPressedDispatcher.onBackPressed()
        idle()

        assertFalse(activity.isTabSwitcherOpen)
        assertEquals(1, BrowserContainer.size())
        assertEquals("https://example.org/", web.url)
    }

    @Test
    fun aNewTab_growsIntoPlaceFromTheLowerCorner() = launch {
        NewTabAnimation.run(content)

        assertTrue("starts small", content.scaleX < 0.5f && content.scaleY < 0.5f)
        assertEquals("from the lower edge", content.height.toFloat(), content.pivotY, 0f)
        assertEquals("on the side where reading starts", 0f, content.pivotX, 0f)
        assertEquals(0f, content.alpha, 0f)

        idle()
        assertEquals(1f, content.scaleX, 0f)
        assertEquals(1f, content.scaleY, 0f)
        assertEquals(1f, content.alpha, 0f)
    }

    @Test
    fun openingATab_endsWithThePageAtFullSize() = launch {
        view<View>(R.id.button_newTab).performClick()
        idle()

        assertEquals(1, content.childCount)
        assertEquals(1f, content.scaleX, 0f)
        assertEquals(1f, content.alpha, 0f)
        assertEquals(1f, view<View>(R.id.home_page).alpha, 0f)
    }

    // -------------------------------------------- 9. offline notice and the game

    @Test
    fun explorerDino_showsTheGame_underItsOwnAddress() = launch {
        open("explorer://dino")

        assertEquals("explorer://dino", web.url)
        assertTrue(web.isShowingOwnPage)
        assertFalse(web.isShowingOfflinePage)
        val loaded = shadowOf(web as WebView).lastLoadDataWithBaseURL
        assertEquals("explorer://dino", loaded.baseUrl)
        assertEquals("text/html", loaded.mimeType)
        assertTrue("Chromium's game script is in the page", loaded.data.contains("function Runner(outerContainerId"))
        assertTrue(web.settings.javaScriptEnabled)
        assertEquals("the address shows in full", "explorer://dino", view<TextView>(R.id.appBar_title).text.toString())
        assertEquals("no app chooser was started for the address", null, shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun theGame_isNotPartOfTheHistory() = launch {
        open("explorer://dino")
        shadowOf(web as WebView).webViewClient.onPageFinished(web, "explorer://dino")
        idle()

        val action = RecordAction(context)
        action.open(false)
        try {
            assertFalse(action.listHistory(context).any { it.url.contains("dino") })
        } finally {
            action.close()
        }
    }

    @Test
    fun theOfflineNotice_keepsThePagesAddress_andReloadingTriesThePageAgain() = launch {
        open("https://example.org/article")

        web.showOfflinePage("https://example.org/article")
        idle()

        assertTrue(web.isShowingOfflinePage)
        assertEquals("https://example.org/article", web.url)
        val notice = shadowOf(web as WebView).lastLoadDataWithBaseURL
        assertEquals("https://example.org/article", notice.baseUrl)
        assertTrue(notice.data.contains("No internet"))
        assertTrue(notice.data.contains("ERR_INTERNET_DISCONNECTED"))

        reload.performClick()
        idle()

        assertFalse("the page is being loaded again", web.isShowingOfflinePage)
        assertEquals("https://example.org/article", shadowOf(web as WebView).lastLoadedUrl)
    }

    @Test
    fun goingSomewhereElse_leavesTheOfflineNotice() = launch {
        open("https://example.org/")
        web.showOfflinePage("https://example.org/")
        val client = shadowOf(web as WebView).webViewClient
        // The WebView reports the start of the notice's own load, then of the next page.
        client.onPageStarted(web, "https://example.org/", null)
        assertTrue(web.isShowingOfflinePage)

        client.onPageStarted(web, "https://example.com/", null)

        assertFalse(web.isShowingOfflinePage)
        assertFalse(web.isShowingOwnPage)
    }

    // ------------------------------------------------- offline: the whole way

    private fun setOnline(online: Boolean) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (online) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
    }

    private val Screen.client get() = shadowOf(web as WebView).webViewClient as NinjaWebViewClient

    @Test
    fun aPageThatFailsWithoutAConnection_getsTheOfflineNotice() = launch {
        setOnline(false)
        assertTrue(OfflinePage.isOffline(context))
        open("https://example.org/news")

        client.onMainFrameError(web, "https://example.org/news", "net::ERR_INTERNET_DISCONNECTED")
        idle()

        assertTrue(web.isShowingOfflinePage)
        assertEquals("the address stays that of the page", "https://example.org/news", web.url)
        assertTrue(shadowOf(web as WebView).lastLoadDataWithBaseURL.data.contains("function Runner(outerContainerId"))
    }

    @Test
    fun withoutAConnection_aFailedNameLookupCountsAsOffline_withOneItDoesNot() = launch {
        setOnline(false)
        open("https://example.org/")
        client.onMainFrameError(web, "https://example.org/", "net::ERR_NAME_NOT_RESOLVED")
        idle()
        assertTrue(web.isShowingOfflinePage)

        // Control: the same error while connected is a mistyped address, not a missing connection.
        setOnline(true)
        assertFalse(OfflinePage.isOffline(context))
        open("https://no-such-site.example/")
        assertFalse(web.isShowingOfflinePage)
        client.onMainFrameError(web, "https://no-such-site.example/", "net::ERR_NAME_NOT_RESOLVED")
        idle()
        assertFalse(web.isShowingOfflinePage)
    }

    @Test
    fun whenTheConnectionIsBack_thePageLoadsAgainByItself() = launch {
        setOnline(false)
        open("https://example.org/news")
        client.onMainFrameError(web, "https://example.org/news", "net::ERR_INTERNET_DISCONNECTED")
        idle()
        assertTrue(web.isShowingOfflinePage)
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callbacks = shadowOf(manager).networkCallbacks
        assertTrue("the browser listens for the network", callbacks.isNotEmpty())

        // Still offline: nothing to retry.
        activity.reloadOfflineTab()
        assertTrue(web.isShowingOfflinePage)

        setOnline(true)
        callbacks.forEach { it.onAvailable(manager.activeNetwork!!) }
        idle()

        assertFalse(web.isShowingOfflinePage)
        assertEquals("https://example.org/news", shadowOf(web as WebView).lastLoadedUrl)
    }

    @Test
    fun aTabLeftOnTheOfflineNotice_triesAgainWhenItComesToTheFront() = launch {
        setOnline(false)
        open("https://example.org/news")
        val first = web
        client.onMainFrameError(first, "https://example.org/news", "net::ERR_INTERNET_DISCONNECTED")
        idle()
        view<View>(R.id.button_newTab).performClick()
        idle()
        assertTrue(first.isShowingOfflinePage)

        setOnline(true)
        activity.showAlbum(first)
        idle()

        assertFalse(first.isShowingOfflinePage)
        assertEquals("https://example.org/news", shadowOf(first as WebView).lastLoadedUrl)
    }

    @Test
    fun typingTheGamesAddress_intoTheAddressField_startsTheGame() = launch {
        open("https://example.org/")
        view<View>(R.id.addressPill).performClick()
        idle()
        val input = view<TextView>(R.id.search_input)
        input.text = "explorer://dino"
        input.onEditorAction(EditorInfo.IME_ACTION_GO)
        idle()

        assertEquals("explorer://dino", web.url)
        assertTrue(web.isShowingOwnPage)
        assertTrue(shadowOf(web as WebView).lastLoadDataWithBaseURL.data.contains("function Runner(outerContainerId"))
        assertNull("it was not turned into a web search", shadowOf(web as WebView).lastLoadedUrl?.takeIf { "dino" in it })
    }

    @Test
    fun theGamesAddress_survivesClosingTheBrowser() = launch {
        open("explorer://dino")
        activity.updateOmniBox()
        val saved = PreferenceManager.getDefaultSharedPreferences(context).getString("openTabs", "")
        assertEquals("explorer://dino", saved)
    }

    @Test
    fun theTabCard_ofTheGame_showsItsAddress_andOfAWebsiteItsName() = launch {
        open("explorer://dino")
        view<View>(R.id.button_tabs).performClick()
        idle()
        assertEquals(listOf("explorer://dino"), cardTitles())
        view<View>(R.id.tabs_new).performClick()
        idle()
        open("https://www.example.org/some/long/path?with=query")
        view<View>(R.id.button_tabs).performClick()
        idle()
        assertEquals("a page without a title shows its site", listOf("explorer://dino", "example.org"), cardTitles())
    }

    // ---------------------------------------------- tab switcher: left-overs

    @Test
    fun theNumberOfColumns_followsTheWidth() {
        assertEquals("a phone held upright", 2, TabSwitcher.columnsFor(411f))
        assertEquals("the same phone on its side", 4, TabSwitcher.columnsFor(891f))
        assertEquals("a narrow window still has two", 2, TabSwitcher.columnsFor(240f))
        assertEquals("a wide screen is capped", 6, TabSwitcher.columnsFor(1600f))
    }

    @Test
    fun withOnlyPrivateTabs_theNormalPageSaysNoTabsOpen() = launch {
        chooseFromMenu(R.id.main_new_private_tab)
        activity.closeAllTabs(false)
        idle()
        view<View>(R.id.button_tabs).performClick()
        idle()
        assertEquals("opens on the private tabs, the kind in front", 1, cards().size)

        view<MaterialButtonToggleGroup>(R.id.tabs_mode).check(R.id.tabs_mode_normal)
        idle()

        val empty = view<TextView>(R.id.tabs_empty)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals("No tabs open", empty.text.toString())
    }
}
