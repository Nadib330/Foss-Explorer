package com.rpooi3.explorer.activity

import android.content.Intent
import android.net.http.SslError
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.NinjaWebView
import com.rpooi3.explorer.view.TabCountDrawable
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
 * 18.0.0 beta 5: the two things that were reported against beta 4.
 *
 *  1. the tab switcher follows the screenshot of beta 5 — the title row with the way back, the bold
 *     "Tabs", the search and the menu; the search in place of that row; the cards; and at the foot
 *     the pill with the tabs and the private tabs with the round button for a new tab beside it;
 *  2. the warning of an untrusted certificate no longer reloads itself, and its back arrow works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1800Beta5Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("welcome_seen", true).commit()
    }

    private fun source(path: String): String = File(path).readText()

    private class Browser(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val web: NinjaWebView
            get() = (0 until content.childCount)
                .map { content.getChildAt(it) }
                .filterIsInstance<NinjaWebView>()
                .first()
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

        fun showSwitcher() {
            view<View>(R.id.button_tabs).performClick()
            idle()
        }

        fun cardTitles(): List<String> {
            val adapter = grid.adapter!!
            return (0 until adapter.itemCount).map { position ->
                val holder = adapter.createViewHolder(grid, adapter.getItemViewType(position))
                adapter.bindViewHolder(holder, position)
                holder.itemView.findViewById<TextView>(R.id.tab_card_title).text.toString()
            }
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

    // 1. The tab switcher of the screenshot ------------------------------------------------------

    @Test
    fun theSwitcher_hasTheTitleRowOfTheScreenshot() = browser {
        open("https://example.org/news")
        showSwitcher()

        // Back, "Tabs" in a bold heading, the magnifier and the menu, in that order.
        val titleRow = view<ViewGroup>(R.id.tabs_title_row)
        val title = view<TextView>(R.id.tabs_title)
        assertEquals("Tabs", title.text.toString())
        assertEquals(android.graphics.Typeface.BOLD, title.typeface.style)
        assertEquals(
            "the heading takes the place between the two icons",
            listOf(R.id.tabs_back, R.id.tabs_title, R.id.tabs_search_button, R.id.tabs_menu),
            (0 until titleRow.childCount).map { titleRow.getChildAt(it).id },
        )
        assertTrue(view<View>(R.id.tabs_back) is ImageButton)
        assertTrue(view<View>(R.id.tabs_search_button) is ImageButton)
        assertTrue(view<View>(R.id.tabs_menu) is ImageButton)
        assertTrue("the way back is shown", view<View>(R.id.tabs_back).isShown)

        // The search is put away until the magnifier is tapped.
        assertEquals(View.GONE, view<View>(R.id.tabs_search_row).visibility)
        assertEquals(View.VISIBLE, titleRow.visibility)

        // The title row comes first, the cards fill the middle, the pill and the round button sit
        // at the foot.
        val switcher = view<ViewGroup>(R.id.tab_switcher)
        assertEquals(R.id.tabs_title_row, switcher.getChildAt(0).id)
        val footer = view<View>(R.id.tabs_fab_card).parent as ViewGroup
        assertEquals(R.id.tabs_fab_card, footer.getChildAt(footer.childCount - 1).id)
    }

    @Test
    fun theSearch_replacesTheTitleRow_andNarrowsTheCards() = browser {
        open("https://example.org/news")
        chooseFromMenu(R.id.main_new_tab)
        open("https://wikipedia.org/wiki/Compass")
        showSwitcher()
        assertEquals(2, grid.adapter!!.itemCount)

        // The magnifier turns the title row into the search field, as in Chrome.
        view<View>(R.id.tabs_search_button).performClick()
        idle()
        assertEquals(View.GONE, view<View>(R.id.tabs_title_row).visibility)
        assertEquals(View.VISIBLE, view<View>(R.id.tabs_search_row).visibility)
        assertEquals("the field has the keyboard", true, view<View>(R.id.tabs_search).hasFocus())

        // Typing narrows the cards; the cross empties the field.
        view<EditText>(R.id.tabs_search).setText("wiki")
        idle()
        assertEquals(listOf("wikipedia.org"), cardTitles())
        assertEquals(View.VISIBLE, view<View>(R.id.tabs_search_clear).visibility)
        view<View>(R.id.tabs_search_clear).performClick()
        idle()
        assertEquals(2, grid.adapter!!.itemCount)

        // The arrow beside the field puts the title row back, with every tab.
        view<View>(R.id.tabs_search_back).performClick()
        idle()
        assertEquals(View.VISIBLE, view<View>(R.id.tabs_title_row).visibility)
        assertEquals(View.GONE, view<View>(R.id.tabs_search_row).visibility)
        assertEquals("", view<EditText>(R.id.tabs_search).text.toString())
    }

    @Test
    fun theBackArrow_ofTheSwitcher_returnsToThePage() = browser {
        open("https://example.org/news")
        showSwitcher()
        assertEquals(View.VISIBLE, view<View>(R.id.tab_switcher).visibility)

        view<View>(R.id.tabs_back).performClick()
        idle()
        assertEquals(View.GONE, view<View>(R.id.tab_switcher).visibility)
        assertTrue("the tab switcher is the one of the activity", !activity.isTabSwitcherOpen())
    }

    @Test
    fun theFoot_hasThePillWithTheTabsAndThePrivateTabs_andTheRoundNewTabButton() = browser {
        open("https://example.org/news")
        showSwitcher()

        // Left of the pill: how many tabs are open, drawn with the same ringed counter as the
        // bottom bar; right of it: the private tabs, with the icon Explorer has always used.
        val normal = view<MaterialButton>(R.id.tabs_mode_normal)
        val counter = normal.icon as TabCountDrawable
        assertEquals("1", counter.label)
        val private = view<MaterialButton>(R.id.tabs_mode_private)
        assertNotNull("the private tab keeps its icon", private.icon)
        val layout = source("src/main/res/layout/include_tab_switcher.xml")
        assertTrue(
            "the private button carries Explorer's own icon",
            layout.contains("app:icon=\"@drawable/icon_private\""),
        )
        // 18.1.0 beta (item 4): 24 dp, the rounded shape of Material 3 without the outline.
        // 18.2.0 beta (item 1): a rounded rectangle of 16 dp.
        assertTrue("the pill is a rounded card", layout.contains("app:cardCornerRadius=\"16dp\""))

        // The pill sits in the middle of the foot, the round button at its end.
        val pill = view<MaterialCardView>(R.id.tabs_pill)
        val pillLayout = pill.layoutParams as android.widget.FrameLayout.LayoutParams
        assertTrue(
            "the pill sits in the middle",
            0 != (pillLayout.gravity and android.view.Gravity.CENTER_HORIZONTAL),
        )
        val fab = view<ImageButton>(R.id.tabs_fab)
        assertEquals(
            "the round button opens a new tab",
            context.getString(R.string.menu_new_tab),
            fab.contentDescription.toString(),
        )
        assertTrue("and it carries the plus", layout.contains("android:src=\"@drawable/icon_plus\""))
        val fabCard = view<MaterialCardView>(R.id.tabs_fab_card)
        val density = context.resources.displayMetrics.density
        assertEquals("a rounded-square button of 40 dp", (40 * density).toInt(), fabCard.layoutParams.width)

        // It opens a new tab of the kind that is in front, and the switcher counts it.
        val before = com.rpooi3.explorer.browser.BrowserContainer.size()
        fab.performClick()
        idle()
        assertEquals(before + 1, com.rpooi3.explorer.browser.BrowserContainer.size())
    }

    // 2. The warning of an untrusted certificate -------------------------------------------------

    @Test
    fun theWarning_isAScreenOfExplorer_andNotAPageThatReloads() {
        val view = source("src/main/java/com/rpooi3/explorer/view/NinjaWebView.java")
        assertTrue("the load is stopped first", view.contains("stopLoading();"))
        assertTrue("the activity draws the warning", view.contains("browser.showSecurityWarning(url, primaryError)"))
        assertFalse("nothing is loaded as a page of its own", view.contains("SslWarningPage"))
        assertFalse("no page of assets is loaded under the address", view.contains("error/ssl.html"))
        assertFalse("and there is no such asset left", File("app/src/main/assets/error/ssl.html").exists())

        val client = source("src/main/java/com/rpooi3/explorer/browser/NinjaWebViewClient.java")
        val ssl = client.substring(client.indexOf("public void onReceivedSslError"), client.indexOf("onReceivedHttpAuthRequest"))
        assertTrue("the answer waits for the user", ssl.contains("showSslWarning"))
        assertFalse("the certificate is not proceeded behind the warning", ssl.contains("handler.proceed()"))

        val activity = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        assertTrue("the warning covers the page", activity.contains("public void showSecurityWarning("))
        assertTrue("and back leaves it", activity.contains("ninjaWebView.cancelSslWarning()"))
    }

    @Test
    fun theWarning_showsFirefoxsWords_andBothButtonsAnswerIt() = browser {
        activity.showSecurityWarning("https://self-signed.badssl.com/", SslError.SSL_UNTRUSTED)
        idle()
        assertTrue(activity.isSecurityWarningShown())

        val warning: View = activity.findViewById(R.id.security_warning)
        assertNotNull("the warning is drawn over the page", warning)
        assertEquals(View.VISIBLE, warning.visibility)
        val body = view<TextView>(R.id.warning_body).text.toString()
        assertTrue("the site it is about: $body", body.contains("self-signed.badssl.com"))
        assertEquals(
            "Warning: Potential Security Risk Ahead",
            view<TextView>(R.id.warning_heading).text.toString(),
        )
        // Why, and the code of Firefox, are behind "Advanced".
        assertEquals(View.GONE, view<View>(R.id.warning_more).visibility)
        view<View>(R.id.warning_advanced).performClick()
        idle()
        assertEquals(View.VISIBLE, view<View>(R.id.warning_more).visibility)
        assertTrue(view<TextView>(R.id.warning_code).text.toString().contains("SEC_ERROR_UNKNOWN_ISSUER"))

        // "Go Back" takes it away; nothing of the site is loaded.
        view<View>(R.id.warning_go_back).performClick()
        idle()
        assertFalse(activity.isSecurityWarningShown())
        assertNull(activity.findViewById<View>(R.id.security_warning))

        // And so does the back button of the device, which did nothing in beta 4.
        activity.showSecurityWarning("https://expired.badssl.com/", SslError.SSL_EXPIRED)
        idle()
        assertTrue(activity.isSecurityWarningShown())
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        idle()
        assertFalse("back leaves the warning", activity.isSecurityWarningShown())
    }

    @Test
    fun theWarning_canBeAcceptedAfterAll() = browser {
        activity.showSecurityWarning("https://self-signed.badssl.com/", SslError.SSL_UNTRUSTED)
        idle()
        view<View>(R.id.warning_advanced).performClick()
        idle()
        view<View>(R.id.warning_accept).performClick()
        idle()
        assertFalse(activity.isSecurityWarningShown())
    }
}
