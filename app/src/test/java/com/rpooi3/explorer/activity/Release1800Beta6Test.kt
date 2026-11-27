package com.rpooi3.explorer.activity

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.BrowserContainer
import com.rpooi3.explorer.downloads.DownloadChip
import com.rpooi3.explorer.home.HomeAppearance
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
 * 18.0.0 beta 6: the eleven things that were asked for after beta 5.
 *
 * 1. Firefox's navigation at the top of the three-dot menu, 2. the floating parts of the switcher
 * drawn flat, 3. Opera's power button, 4. Chrome's notice when a tab is closed, 5. Chrome's
 * animation of a download that starts, 6. the way to open a finished download, 7. Chrome's pencil
 * on the home page, 8. more of the home page to make one's own, 9–11. the glitches that came with
 * them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1800Beta6Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    @Before
    fun setUp() {
        prefs.edit().putBoolean("welcome_seen", true).commit()
        // The card of a download belongs to the screen it was shown on; a test starts without one.
        DownloadChip.hide()
    }

    private fun source(path: String): String = File(path).readText()

    private class Browser(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val grid: RecyclerView = activity.findViewById(R.id.tabs_grid)
        val web: NinjaWebView
            get() = (0 until content.childCount)
                .map { content.getChildAt(it) }
                .filterIsInstance<NinjaWebView>()
                .first()

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

        fun openMenu(): View {
            view<View>(R.id.button_menu).let { if (it.isShown) it.performClick() else view<View>(R.id.home_menu).performClick() }
            idle()
            return activity.mainMenu.contentView!!
        }

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

    // 1. Firefox's navigation in the menu --------------------------------------------------------

    @Test
    fun theMenu_opensWithFirefoxsNavigation() = browser {
        open("https://example.org/news")
        open("https://example.net/other")
        val content = openMenu()
        val groups = content.findViewById<LinearLayout>(R.id.main_menu_groups)

        // Back, forward and reload are the first thing in the menu, as a row of icons.
        val first = groups.getChildAt(0) as ViewGroup
        // (18.0.0 beta 7 made the third button Print, see Release1800Beta7Test.)
        assertEquals(
            listOf(R.id.main_nav_back, R.id.main_nav_forward, R.id.main_nav_print),
            (0 until first.childCount).map { first.getChildAt(it).id },
        )
        assertEquals("side by side, as Firefox has them", LinearLayout.HORIZONTAL, (first as LinearLayout).orientation)

        val menu = activity.mainMenu.menu
        // Each of them is there when the tab can do it, and only then.
        assertEquals(web.canGoBack(), menu.findItem(R.id.main_nav_back).isEnabled)
        assertEquals(web.canGoForward(), menu.findItem(R.id.main_nav_forward).isEnabled)
        assertTrue("a website can be printed", menu.findItem(R.id.main_nav_print).isEnabled)
    }

    // 2. The switcher's floating parts, drawn flat ----------------------------------------------

    @Test
    fun theSwitchersFloatingParts_areFlat() = browser {
        open("https://example.org/")
        showSwitcher()

        val pill = view<MaterialCardView>(R.id.tabs_pill)
        val fab = view<MaterialCardView>(R.id.tabs_fab_card)
        assertEquals("the pill has no shadow", 0f, pill.cardElevation, 0f)
        assertEquals("nor the round button", 0f, fab.cardElevation, 0f)
        // 18.1.0 beta (item 4): they are filled surfaces of Material 3, and a filled shape of
        // Material 3 has no hairline drawn round it.
        assertEquals("no hairline round the pill", 0, pill.strokeWidth)
        assertEquals("nor round the plus", 0, fab.strokeWidth)
        assertEquals(
            "the pill is a surface of the third tone",
            com.google.android.material.color.MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0,
            ),
            pill.cardBackgroundColor.defaultColor,
        )
    }

    // 3. Opera's power button -------------------------------------------------------------------

    @Test
    fun thePowerButton_leavesTheBrowser() = browser {
        open("https://example.org/")
        val content = openMenu()
        val groups = content.findViewById<LinearLayout>(R.id.main_menu_groups)
        val footer = groups.getChildAt(groups.childCount - 1) as ViewGroup
        assertEquals(
            "Settings, then the power button",
            listOf(R.id.main_settings, R.id.main_exit),
            (0 until footer.childCount).map { footer.getChildAt(it).id },
        )
        assertNotNull("it has the power icon", footer.findViewById<View>(R.id.main_exit).let {
            (it as ViewGroup).findViewById<android.widget.ImageView>(R.id.menu_row_icon).drawable
        })

        // 18.0.0 beta 8 (item 2): the button asks before it leaves, so the answer is given first.
        assertTrue(activity.mainMenu.menu.performIdentifierAction(R.id.main_exit, 0))
        idle()
        val question = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertTrue("the question of the exit", question.isShowing)
        question.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue("the browser was left", activity.isFinishing)
    }

    // 4. Chrome's notice when a tab is closed ---------------------------------------------------

    @Test
    fun closingATab_showsChromesNotice_withTheWayBack() = browser {
        open("https://example.org/news")
        chooseFromMenu(R.id.main_new_tab)
        open("https://wikipedia.org/wiki/Compass")
        assertEquals(2, BrowserContainer.size())
        showSwitcher()

        // The cross of a card closes its tab and says so at the foot of the screen.
        val card = cards().first()
        card.findViewById<View>(R.id.tab_card_close).performClick()
        idle()
        assertEquals("the tab is gone", 1, BrowserContainer.size())
        val notice = activity.tabClosedNotice
        assertNotNull("and the notice is up", notice)
        assertEquals(
            "Tab closed",
            notice!!.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).text.toString(),
        )

        // "Undo" brings it back.
        notice.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action).performClick()
        idle()
        assertEquals("the tab is open again", 2, BrowserContainer.size())
        assertNull("and the notice is gone", activity.tabClosedNotice)
    }

    // 5. and 6. the notice of a download --------------------------------------------------------

    @Test
    fun aDownload_risesFromTheFoot_andOffersToOpenTheFile() = browser {
        // A download the browser writes itself: the card is there while the bytes are written.
        DownloadChip.startedLocal(activity, "holiday.zip")
        idle()
        val chip: View = activity.findViewById(R.id.download_chip)
        assertNotNull("the card is over the page", chip)
        assertEquals(View.VISIBLE, chip.visibility)
        assertEquals("holiday.zip", activity.findViewById<TextView>(R.id.download_chip_title).text.toString())
        assertEquals(
            context.getString(R.string.download_chip_started),
            activity.findViewById<TextView>(R.id.download_chip_status).text.toString(),
        )
        // It starts below the edge and rises into place, as Chrome's bubble does.
        assertEquals("it rose into place", 0f, chip.translationY, 0.5f)

        // Once the file is there: "Downloaded", and the way to open it.
        DownloadChip.finished(activity, "holiday.zip", Uri.parse("content://downloads/42"))
        idle()
        assertEquals(
            context.getString(R.string.download_chip_done),
            activity.findViewById<TextView>(R.id.download_chip_status).text.toString(),
        )
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.download_chip_open).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.download_chip_progress).visibility)

        // The cross puts it away.
        activity.findViewById<View>(R.id.download_chip_close).performClick()
        idle()
        assertFalse(DownloadChip.isShowing())
    }

    // 7. Chrome's pencil on the home page -------------------------------------------------------

    @Test
    fun theHomePage_hasChromesPencil_toItsOwnSettings() = browser {
        val pencil: View = activity.findViewById(R.id.home_customize)
        assertNotNull("the pencil is on the home page", pencil)
        assertTrue(pencil.isShown)

        pencil.performClick()
        idle()
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull("it opens the settings", started)
        assertEquals(Settings_Activity::class.java.name, started!!.component?.className)
        assertEquals(
            "at the customization of the home page",
            "section_home_custom",
            started.getStringExtra(Settings_Activity.EXTRA_SECTION),
        )
    }

    // 8. More of the home page is the user's own ------------------------------------------------

    @Test
    fun theHomePage_takesMoreChoices() {
        // Sites in a row: four unless the user says otherwise, and never fewer than three or more
        // than five.
        assertEquals(4, HomeAppearance.shortcutColumns(context))
        prefs.edit().putString(HomeAppearance.KEY_COLUMNS, "3").commit()
        assertEquals(3, HomeAppearance.shortcutColumns(context))
        prefs.edit().putString(HomeAppearance.KEY_COLUMNS, "9").commit()
        assertEquals("kept within the page", 5, HomeAppearance.shortcutColumns(context))
        prefs.edit().remove(HomeAppearance.KEY_COLUMNS).commit()

        // The home page itself, inflated in the theme of the app.
        val themed = ContextThemeWrapper(context, R.style.AppTheme)
        val holder = FrameLayout(themed)
        val root = android.view.LayoutInflater.from(themed).inflate(R.layout.include_home, holder, false)
        holder.addView(root)
        HomeAppearance.prefs(context).edit()
            .putBoolean(HomeAppearance.KEY_SHOW_SEARCH, false)
            .putString(HomeAppearance.KEY_TITLE, "My page")
            .commit()
        HomeAppearance.apply(root)
        assertEquals(View.GONE, root.findViewById<View>(R.id.home_search).visibility)
        assertEquals("My page", root.findViewById<TextView>(R.id.home_title).text.toString())

        HomeAppearance.prefs(context).edit()
            .remove(HomeAppearance.KEY_SHOW_SEARCH)
            .remove(HomeAppearance.KEY_TITLE)
            .commit()
        HomeAppearance.apply(root)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.home_search).visibility)
        assertEquals(context.getString(R.string.app_name), root.findViewById<TextView>(R.id.home_title).text.toString())
    }

    @Test
    fun theSettingsOfferTheNewChoices() {
        // (The tests run in the module's directory, as those of beta 4 and beta 5 do.)
        val screen = source("src/main/res/xml/preference_section_home_custom.xml")
        assertTrue(screen.contains("android:key=\"home_show_search\""))
        assertTrue(screen.contains("android:key=\"home_title_text\""))
        assertTrue(screen.contains("android:key=\"home_shortcuts_columns\""))
        val arrays = source("src/main/res/values/arrays.xml")
        assertTrue(arrayValues(arrays, "home_columns_values").containsAll(listOf("3", "4", "5")))
    }

    private fun arrayValues(xml: String, name: String): List<String> {
        val start = xml.indexOf("<string-array name=\"$name\"")
        if (start < 0) return emptyList()
        val end = xml.indexOf("</string-array>", start)
        return Regex("<item>(.*?)</item>").findAll(xml.substring(start, end)).map { it.groupValues[1] }.toList()
    }

    // 9.–11. The glitches that came with them ---------------------------------------------------

    @Test
    fun theGlitches_areGone() {
        // The search of the switcher no longer stays open behind the page.
        val switcher = source("src/main/java/com/rpooi3/explorer/view/TabSwitcher.kt")
        val hide = switcher.substring(switcher.indexOf("fun hide(towards"), switcher.indexOf("fun refresh()"))
        assertTrue("the search goes with the switcher", hide.contains("closeSearch()"))
        assertTrue("the keyboard's search key puts the keyboard away", switcher.contains("setOnEditorActionListener"))

        // A certificate warning no longer stands over the tab switcher.
        val activity = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        val overview = activity.substring(activity.indexOf("private void showOverview()"), activity.indexOf("SWITCHER_ANIMATION_MS"))
        assertTrue("the warning goes when the switcher opens", overview.contains("hideSecurityWarning()"))
    }
}
