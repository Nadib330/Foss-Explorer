package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.BrowserContainer
import com.rpooi3.explorer.preferences.SettingsSections
import com.rpooi3.explorer.privacy.TrackingProtection
import com.rpooi3.explorer.site.SiteCategory
import com.rpooi3.explorer.site.SiteSettings
import com.rpooi3.explorer.tabs.TabModelSelector
import com.rpooi3.explorer.view.NinjaWebView
import com.rpooi3.explorer.view.TabCountDrawable
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

/**
 * Release 15.0.0: the private new tab page after Chromium, the tab list and
 * tab switcher after Chromium, the shield with Firefox's protection panel,
 * the home page without the icon, and the first-person note on FOSS Browser.
 * (The database is tested in PlacesDatabaseTest.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1500Test {
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

    private class Browser(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val web: NinjaWebView get() = content.getChildAt(content.childCount - 1) as NinjaWebView
        val home: View = activity.findViewById(R.id.home_page)
        val incognito: View = activity.findViewById(R.id.incognito_page)
        val shield: ImageView = activity.findViewById(R.id.button_shield)
        val grid: RecyclerView = activity.findViewById(R.id.tabs_grid)

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun <T : View> view(id: Int): T = activity.findViewById(id)

        fun open(url: String) {
            web.loadUrl(url)
            activity.updateOmniBox()
            idle()
        }

        fun chooseFromMenu(id: Int) {
            view<View>(R.id.button_menu).let { if (it.isShown) it.performClick() else view<View>(R.id.home_menu).performClick() }
            idle()
            assertTrue(activity.mainMenu.menu.performIdentifierAction(id, 0))
            idle()
        }

        fun tabs(): List<NinjaWebView> = BrowserContainer.list().map { it as NinjaWebView }

        fun showSwitcher() {
            view<View>(R.id.button_tabs).performClick()
            idle()
        }

        fun cards(): List<View> {
            val adapter = grid.adapter!!
            return (0 until adapter.itemCount).map { position ->
                val holder = adapter.createViewHolder(grid, adapter.getItemViewType(position))
                adapter.bindViewHolder(holder, position)
                holder.itemView
            }
        }

        fun cardTitles(): List<String> = cards().map { it.findViewById<TextView>(R.id.tab_card_title).text.toString() }

        fun panel(): BottomSheetDialog {
            shield.performClick()
            idle()
            return ShadowDialog.getLatestDialog() as BottomSheetDialog
        }
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

    // --------------------------------------------- 1. the private new tab page

    @Test
    fun aPrivateTab_opensOnChromiumsIncognitoPage_notOnTheHomePage() = browser {
        assertEquals("a normal tab has the home page", View.VISIBLE, home.visibility)
        assertEquals(View.GONE, incognito.visibility)

        chooseFromMenu(R.id.main_new_private_tab)
        assertTrue(web.isPrivateTab)
        assertEquals(View.VISIBLE, incognito.visibility)
        assertEquals("not the normal new tab page", View.GONE, home.visibility)
        // 16.1.0 (item 2): the app says "private", not "Incognito".
        assertEquals("You’ve gone private browsing mode", view<TextView>(R.id.new_tab_incognito_title).text.toString())
        assertTrue(view<TextView>(R.id.new_tab_incognito_subtitle).text.startsWith("Others who use this device won’t see your activity"))
        val warning = view<TextView>(R.id.new_tab_incognito_warning).text.toString()
        assertEquals(
            "Your activity might still be visible to:\nWebsites you visit\nYour employer or school\nYour internet service provider",
            warning,
        )
        assertTrue(view<TextView>(R.id.new_tab_incognito_features).text.startsWith("Explorer won’t save:\nYour browsing history"))
        // The page has no search box of its own, so the top bar stays, as in Chromium.
        assertEquals(View.VISIBLE, view<View>(R.id.topBar).visibility)
        assertEquals("dark in the light theme too", context.getColor(R.color.incognito_background), (incognito.background as android.graphics.drawable.ColorDrawable).color)

        // On a website the page is gone; back on a normal tab the home page is there again.
        open("https://example.org/")
        assertEquals(View.GONE, incognito.visibility)
        showSwitcher()
        view<MaterialButtonToggleGroup>(R.id.tabs_mode).check(R.id.tabs_mode_normal)
        idle()
        cards().first().performClick()
        idle()
        assertEquals(View.VISIBLE, home.visibility)
        assertEquals(View.GONE, incognito.visibility)
    }

    @Test
    fun thePage_promisesOnlyWhatHoldsOnThisDevice_andHasChromiumsCookieCard() = browser {
        chooseFromMenu(R.id.main_new_private_tab)
        val features = view<TextView>(R.id.new_tab_incognito_features).text.toString()
        val shared = view<View>(R.id.new_tab_incognito_shared)
        if (web.isPrivateIsolated) {
            assertTrue(features.contains("Cookies and site data"))
            assertEquals(View.GONE, shared.visibility)
        } else {
            // Robolectric's WebView, like an old one, cannot keep the cookies of private tabs apart.
            assertFalse("no promise that cannot be kept", features.contains("Cookies and site data"))
            assertEquals(View.VISIBLE, shared.visibility)
        }
        assertTrue(features.contains("Information entered in forms"))

        assertEquals("Block third-party cookies", view<TextView>(R.id.cookie_controls_card_title).text.toString())
        val toggle = view<MaterialSwitch>(R.id.cookie_controls_card_toggle)
        val allowed = SiteSettings.isOn(prefs, SiteCategory.THIRD_PARTY_COOKIES)
        assertEquals("the card is the site setting, the other way round", !allowed, toggle.isChecked)
        toggle.isChecked = allowed
        assertEquals(!allowed, SiteSettings.isOn(prefs, SiteCategory.THIRD_PARTY_COOKIES))
    }

    // ------------------------------------------- 2. tab list and tab switcher

    @Test
    fun theTabThatComesToTheFront_isChosenByChromiumsRules() {
        class Tab(val name: String, val incognito: Boolean = false, var parent: Tab? = null) {
            override fun toString() = name
        }
        val closed = ArrayList<String>()
        val selector = TabModelSelector<Tab>({ it.incognito }, { it.parent }, { closed += it.name })
        val a = Tab("a"); val b = Tab("b"); val c = Tab("c", parent = a); val d = Tab("d")
        val p = Tab("p", incognito = true); val q = Tab("q", incognito = true)
        listOf(a, b, c, d, p, q).forEach(selector::addTab)
        assertEquals(4, selector.getModel(false).count)
        assertEquals(2, selector.getModel(true).count)
        assertEquals(1, selector.getModel(true).indexOf(q))
        assertEquals(-1, selector.getModel(false).indexOf(q))

        assertEquals("closing a background tab keeps the current tab", d, selector.getNextTabIfClosed(b, d, false))
        assertEquals("else the tab it was opened from", a, selector.getNextTabIfClosed(c, c, false))
        assertEquals("but not in the tab switcher: there the neighbour", b, selector.getNextTabIfClosed(c, c, true))
        assertEquals("the neighbour before it", c, selector.getNextTabIfClosed(d, d, false))
        assertEquals("for the first tab, the one after it", b, selector.getNextTabIfClosed(a, a, false))
        assertEquals("neighbours are of the same kind", p, selector.getNextTabIfClosed(q, q, false))

        selector.selectTab(b)
        selector.closeTab(q)
        assertEquals(listOf("q"), closed)
        assertEquals("closing the last Incognito tab selects the current normal tab", b, selector.getNextTabIfClosed(p, p, false))
        selector.closeAllTabs()
        assertEquals(0, selector.totalTabCount)
    }

    @Test
    fun closingATabThatWasOpenedFromAnother_returnsToThatOne() = browser {
        open("https://one.example/")
        chooseFromMenu(R.id.main_new_tab)
        open("https://two.example/")
        // Opened from "one" while it is in front.
        showSwitcher()
        cards().first { it.findViewById<TextView>(R.id.tab_card_title).text.contains("one") }.performClick()
        idle()
        chooseFromMenu(R.id.main_new_tab)
        open("https://three.example/")
        val three = web
        // A second tab from "one": it takes the place next to it, so "three" is no longer its neighbour.
        showSwitcher()
        cards().first { it.findViewById<TextView>(R.id.tab_card_title).text.contains("one") }.performClick()
        idle()
        chooseFromMenu(R.id.main_new_tab)
        open("https://four.example/")
        assertEquals(
            listOf("https://one.example/", "https://four.example/", "https://three.example/", "https://two.example/"),
            tabs().map { it.url },
        )
        activity.showAlbum(three)
        idle()

        activity.removeAlbum(three)
        idle()
        assertEquals("its parent, not its neighbour", "https://one.example/", web.url)
    }

    @Test
    fun theTabSwitcher_looksLikeChromiums_andSearchesTheTabs() = browser {
        open("https://example.org/news")
        chooseFromMenu(R.id.main_new_tab)
        open("https://wikipedia.org/wiki/Compass")
        showSwitcher()

        // Top row: a filled button for a new tab, the switch with the number of tabs, the menu.
        assertTrue(view<View>(R.id.tabs_new) is android.widget.ImageButton)
        val counter = view<MaterialButton>(R.id.tabs_mode_normal).icon as TabCountDrawable
        assertEquals("2", counter.label)
        assertNotNull(view<MaterialButton>(R.id.tabs_mode_private).icon)
        val search = view<EditText>(R.id.tabs_search)
        assertEquals("Search your tabs", search.hint.toString())

        // The tab in front stands out in the inverse colours.
        val cards = cards()
        assertEquals(listOf(false, true), cards.map { it.isSelected })
        fun color(attr: Int) = MaterialColors.getColor(activity, attr, 0)
        assertEquals(color(com.google.android.material.R.attr.colorSurfaceInverse), (cards[1] as MaterialCardView).cardBackgroundColor.defaultColor)
        assertEquals(color(com.google.android.material.R.attr.colorOnSurfaceInverse), cards[1].findViewById<TextView>(R.id.tab_card_title).currentTextColor)
        assertEquals(color(com.google.android.material.R.attr.colorSurfaceContainerHighest), (cards[0] as MaterialCardView).cardBackgroundColor.defaultColor)

        // Searching narrows the cards by title and address.
        search.setText("wiki")
        idle()
        assertEquals(listOf("wikipedia.org"), cardTitles())
        assertEquals(View.VISIBLE, view<View>(R.id.tabs_search_clear).visibility)
        search.setText("example NEWS")
        idle()
        assertEquals(listOf("example.org"), cardTitles())
        search.setText("nothing like this")
        idle()
        assertTrue(cardTitles().isEmpty())
        assertEquals("No tabs match your search", view<TextView>(R.id.tabs_empty).text.toString())
        view<View>(R.id.tabs_search_clear).performClick()
        idle()
        assertEquals(2, cardTitles().size)

        view<MaterialButtonToggleGroup>(R.id.tabs_mode).check(R.id.tabs_mode_private)
        idle()
        assertEquals("Search your private tabs", search.hint.toString())
    }

    @Test
    fun aHundredTabsAndMore_showChromiumsSmile() {
        val counter = TabCountDrawable(context)
        counter.count = 7
        assertEquals("7", counter.label)
        counter.count = 100
        assertEquals(":D", counter.label)
    }

    // ------------------------------------------------------------ 3. the shield

    @Test
    fun theShield_standsLeftOfTheThreeDots_onWebsitesOnly() = browser {
        assertEquals("not on the home page", View.GONE, shield.visibility)
        open("https://example.org/")
        assertEquals(View.VISIBLE, shield.visibility)
        val bar = shield.parent as ViewGroup
        assertEquals("directly before the three dots", bar.indexOfChild(view(R.id.button_menu)) - 1, bar.indexOfChild(shield))
        open("explorer://dino")
        assertEquals("not on Explorer's own pages", View.GONE, shield.visibility)
    }

    @Test
    fun thePanel_saysHowTheSiteStands_inFirefoxsWords() = browser {
        open("https://www.example.org/article")
        TrackingProtection.onBlocked(web, "https://ads.tracker.example/pixel.gif")
        TrackingProtection.onBlocked(web, "https://ads.tracker.example/script.js")
        TrackingProtection.onBlocked(web, "https://metrics.example/collect")

        val panel = panel()
        assertEquals("example.org", panel.findViewById<TextView>(R.id.protection_site_host)!!.text.toString())
        // 15.2.1: the shield is called xGuard, so the banner names it.
        assertEquals("xGuard is on guard", panel.findViewById<TextView>(R.id.protection_banner_title)!!.text.toString())
        assertEquals("2 trackers blocked on this site.", panel.findViewById<TextView>(R.id.protection_banner_text)!!.text.toString())
        assertTrue(panel.findViewById<MaterialSwitch>(R.id.protection_toggle)!!.isChecked)
        assertEquals(
            "If something looks broken on this site, try turning it off.",
            panel.findViewById<TextView>(R.id.protection_toggle_text)!!.text.toString(),
        )
        fun label(id: Int) = panel.findViewById<View>(id)!!.findViewById<TextView>(R.id.menu_row_label).text.toString()
        assertEquals("2 trackers blocked on this site", label(R.id.protection_blocked))
        assertEquals("Secure connection", label(R.id.protection_connection))
        assertEquals("Clear cookies and site data", label(R.id.protection_clear))
        assertEquals("xGuard settings", label(R.id.protection_settings))

        // A tap on the number lists who was blocked.
        val list = panel.findViewById<TextView>(R.id.protection_blocked_list)!!
        assertEquals(View.GONE, list.visibility)
        panel.findViewById<View>(R.id.protection_blocked)!!.performClick()
        assertEquals(View.VISIBLE, list.visibility)
        assertEquals("ads.tracker.example  \u00d72\nmetrics.example", list.text.toString())

        panel.findViewById<View>(R.id.protection_settings)!!.performClick()
        // 15.2.1: it opens xGuard's own settings, not the Ads page of the site settings.
        assertEquals("section_xguard", shadowOf(activity).nextStartedActivity.getStringExtra(Settings_Activity.EXTRA_SECTION))
    }

    @Test
    fun theSwitch_turnsProtectionOffForThisSiteOnly_andTheShieldIsCrossedOut() = browser {
        prefs.edit().putBoolean("profileStandard_adBlock", true).commit()
        open("https://www.example.org/article")
        assertTrue(TrackingProtection.isProtected(activity, web.url))
        assertEquals(R.drawable.icon_shield, shadowOf(shield.drawable).createdFromResId)

        val panel = panel()
        panel.findViewById<View>(R.id.protection_toggle_row)!!.performClick()
        idle()
        assertFalse(panel.isShowing)
        assertFalse(TrackingProtection.isProtected(activity, "https://www.example.org/other"))
        assertTrue("other sites stay protected", TrackingProtection.isProtected(activity, "https://example.com/"))
        assertEquals(listOf("example.org"), SiteSettings.exceptions(prefs, SiteCategory.ADS, true))
        assertFalse("the tab stops blocking", web.isAdBlock)
        assertEquals(R.drawable.icon_shield_off, shadowOf(shield.drawable).createdFromResId)

        val off = panel()
        assertEquals("You turned off protections", off.findViewById<TextView>(R.id.protection_banner_title)!!.text.toString())
        assertEquals(
            // 15.2.1: the shield is called xGuard, and the banner says so.
            "xGuard is off-duty. We suggest turning protections back on.",
            off.findViewById<TextView>(R.id.protection_banner_text)!!.text.toString(),
        )
        assertFalse(off.findViewById<MaterialSwitch>(R.id.protection_toggle)!!.isChecked)
        assertEquals(
            "Trackers aren’t blocked",
            off.findViewById<View>(R.id.protection_blocked)!!.findViewById<TextView>(R.id.menu_row_label).text.toString(),
        )
        off.findViewById<View>(R.id.protection_toggle_row)!!.performClick()
        idle()
        assertTrue(TrackingProtection.isProtected(activity, web.url))
        assertTrue("no exception is left behind", SiteSettings.exceptions(prefs, SiteCategory.ADS, true).isEmpty())
    }

    @Test
    fun anInsecurePage_isWarnedAbout_andSiteDataCanBeCleared() = browser {
        assertFalse(TrackingProtection.isSecure("http://plain.example/"))
        assertTrue(TrackingProtection.isSecure("https://plain.example/"))
        assertEquals("Be careful on this site", context.getString(R.string.protection_panel_banner_not_secure_title))
        assertEquals("Connection not secure", context.getString(R.string.connection_security_panel_not_secure))
        open("https://plain.example/")
        val panel = panel()

        panel.findViewById<View>(R.id.protection_clear)!!.performClick()
        idle()
        val question = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(
            "Removing cookies and site data for plain.example might log you out of websites and clear shopping carts.",
            question.findViewById<TextView>(android.R.id.message)!!.text.toString(),
        )
        assertEquals("Clear", question.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        question.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertFalse(panel.isShowing)
    }

    @Test
    fun whatWasBlocked_isCountedPerPage() = browser {
        open("https://example.org/")
        assertEquals(0, TrackingProtection.blockedCount(web))
        TrackingProtection.onBlocked(web, "https://t.example/a")
        TrackingProtection.onBlocked(web, "not a url")
        assertEquals(1, TrackingProtection.blockedCount(web))
        TrackingProtection.onPageStarted(web)
        assertEquals("a new page starts at nothing", 0, TrackingProtection.blockedCount(web))
        assertFalse(TrackingProtection.appliesTo("about:blank"))
        assertFalse(TrackingProtection.appliesTo(null))
    }

    // ------------------------------------------------ 5. and 6. home and about

    @Test
    fun theHomePage_showsTheNameOnly() = browser {
        val logo = view<ViewGroup>(R.id.home_logo)
        assertEquals(1, logo.childCount)
        assertEquals("Explorer", (logo.getChildAt(0) as TextView).text.toString())
        fun pictures(group: ViewGroup): Int = (0 until group.childCount).sumOf {
            when (val child = group.getChildAt(it)) {
                is ImageView -> 1
                is ViewGroup -> pictures(child)
                else -> 0
            }
        }
        assertEquals("no app icon beside the name", 0, pictures(logo))
    }

    @Test
    fun theNoteOnFossBrowser_isGone_andTheSourceOfItIsNamed() {
        // 16.1.0 (item 21): no card calls Explorer a fork any more. What is still FOSS Browser's
        // is named in the components list, and Legal information opens its repository.
        val intent = Intent(context, Settings_Activity::class.java).putExtra(Settings_Activity.EXTRA_SECTION, "section_legal")
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            val fragment = controller.get().supportFragmentManager.findFragmentById(R.id.content_frame) as PreferenceFragmentCompat
            assertNull("the fork card is gone", fragment.findPreference<Preference>("about_fork"))
            val upstream = fragment.findPreference<Preference>("about_source_upstream")!!
            assertEquals("Source code of FOSS Browser", upstream.title.toString())
            assertTrue(upstream.summary.toString().startsWith("FOSS Browser by Gaukler Faun."))
        }
    }

    @Test
    fun theVersion_is17_3_2() {
        // 17.3.3 replaced 17.3.2; what changed is described in Release1733Test.
        assertEquals("17.3.3", SettingsSections.versionName(context))
        @Suppress("DEPRECATION")
        assertEquals(13, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
    }
}
