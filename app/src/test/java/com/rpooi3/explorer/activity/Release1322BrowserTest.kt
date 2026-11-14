package com.rpooi3.explorer.activity

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.accessibility.PageHooks
import com.rpooi3.explorer.accessibility.PageZoom
import com.rpooi3.explorer.accessibility.ReaderMode
import com.rpooi3.explorer.accessibility.TouchpadSwipe
import com.rpooi3.explorer.privacy.IncognitoLock
import com.rpooi3.explorer.tabs.InactiveTabs
import com.rpooi3.explorer.view.NinjaWebView
import org.junit.After
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit

/**
 * Release 13.2.2 on the browser screen: what the new settings do. Tabs come
 * back and go to the inactive section, private tabs are locked, pages are
 * zoomed, articles get a simplified view, a touchpad swipes through history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1322BrowserTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val day = TimeUnit.DAYS.toMillis(1)
    private val separator = "\u201a\u2017\u201a"

    @After
    fun unlock() {
        IncognitoLock.locked = false
    }

    private class Screen(val controller: ActivityController<BrowserActivity>) {
        val activity: BrowserActivity = controller.get()
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val web: NinjaWebView get() = content.getChildAt(content.childCount - 1) as NinjaWebView
        val lock: View = activity.findViewById(R.id.incognito_lock)
        val inactive: TextView = activity.findViewById(R.id.tabs_inactive)

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun <T : View> view(id: Int): T = activity.findViewById(id)

        fun open(url: String) {
            web.loadUrl(url)
            activity.updateOmniBox()
            idle()
        }

        fun openMenu() {
            view<View>(R.id.button_menu).performClick()
            idle()
        }

        fun chooseFromMenu(id: Int) {
            openMenu()
            assertTrue(activity.mainMenu.menu.performIdentifierAction(id, 0))
            idle()
        }

        fun tabs(): List<NinjaWebView> = (0 until com.rpooi3.explorer.browser.BrowserContainer.size())
            .map { com.rpooi3.explorer.browser.BrowserContainer.get(it) as NinjaWebView }

        fun showSwitcher() {
            view<View>(R.id.button_tabs).performClick()
            idle()
        }
    }

    private fun launch(block: Screen.() -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().use { controller ->
            Screen(controller).apply {
                idle()
                block()
                idle()
            }
        }
    }

    // ------------------------------------------------------- 2. Tabs and groups

    @Test
    fun theTabs_comeBackWithTheNextStart_withoutASettingForIt() {
        prefs.edit().putString("openTabs", listOf("https://front.example/", "https://second.example/").joinToString(separator)).commit()
        launch {
            assertEquals(listOf("https://front.example/", "https://second.example/"), tabs().map { it.url })
            assertEquals("the tab that was in front is in front again", "https://front.example/", web.url)
        }
    }

    @Test
    fun aTabNotUsedForTheChosenTime_waitsInTheInactiveSection() {
        val now = System.currentTimeMillis()
        prefs.edit().putString("openTabs", listOf("https://front.example/", "https://old.example/", "https://recent.example/").joinToString(separator)).commit()
        InactiveTabs.record(
            prefs,
            listOf("https://front.example/" to "Front", "https://old.example/" to "Old page", "https://recent.example/" to "Recent"),
            current = null, now = now - 30 * day,
        )
        InactiveTabs.record(
            prefs,
            listOf("https://front.example/" to "Front", "https://old.example/" to "Old page", "https://recent.example/" to "Recent"),
            current = "https://recent.example/", now = now - 2 * day,
        )
        launch {
            assertEquals(
                "the tab in front stays, however old",
                listOf("https://front.example/", "https://recent.example/"), tabs().map { it.url },
            )
            assertEquals(listOf("https://old.example/"), InactiveTabs.list(prefs).map { it.url })
            assertEquals("Old page", InactiveTabs.list(prefs).single().title)

            showSwitcher()
            assertEquals(View.VISIBLE, inactive.visibility)
            assertEquals("1 inactive tab", inactive.text.toString())

            // The inactive section lists the tab; a tap opens it again.
            inactive.performClick()
            idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("1 inactive tab", dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
            assertEquals("Old page\nhttps://old.example/", dialog.listView.adapter.getItem(0).toString())
            shadowOf(dialog.listView).performItemClick(0)
            idle()

            assertEquals("https://old.example/", web.url)
            assertTrue(InactiveTabs.list(prefs).isEmpty())
            assertEquals(3, tabs().size)
        }
    }

    @Test
    fun never_keepsEveryTab() {
        val now = 1_000_000_000_000L
        val urls = listOf("https://a.example/", "https://b.example/")
        InactiveTabs.record(prefs, urls.map { it to "" }, null, now)
        assertEquals(setOf(1), InactiveTabs.due(prefs, urls, now + 21 * day))
        assertEquals("not yet", emptySet<Int>(), InactiveTabs.due(prefs, urls, now + 20 * day))
        InactiveTabs.setDays(prefs, 7)
        assertEquals(setOf(1), InactiveTabs.due(prefs, urls, now + 7 * day))
        InactiveTabs.setDays(prefs, 0)
        assertEquals(emptySet<Int>(), InactiveTabs.due(prefs, urls, now + 400 * day))
    }

    @Test
    fun archiveDuplicateTabs_keepsOneTabPerPage() {
        val urls = listOf("https://a.example/", "https://b.example/", "https://a.example/", "https://b.example/")
        assertEquals(emptySet<Int>(), InactiveTabs.due(prefs, urls, 0L))
        prefs.edit().putBoolean(InactiveTabs.KEY_DUPLICATES, true).commit()
        assertEquals(setOf(2, 3), InactiveTabs.due(prefs, urls, 0L))
        assertEquals(listOf("https://a.example/", "https://b.example/"), InactiveTabs.sort(prefs, urls, 0L))
        assertEquals("a page waits there once", 2, InactiveTabs.list(prefs).size)
    }

    @Test
    fun inactiveTabs_areClosedAfterSixtyDays_whileTheSwitchIsOn() {
        val now = 1_000_000_000_000L
        InactiveTabs.add(prefs, InactiveTabs.Entry("https://old.example/", "Old", now - 61 * day))
        InactiveTabs.add(prefs, InactiveTabs.Entry("https://newer.example/", "Newer", now - 59 * day))
        prefs.edit().putBoolean(InactiveTabs.KEY_AUTO_CLOSE, false).commit()
        assertEquals(0, InactiveTabs.closeOld(prefs, now))
        prefs.edit().putBoolean(InactiveTabs.KEY_AUTO_CLOSE, true).commit()
        assertEquals(1, InactiveTabs.closeOld(prefs, now))
        assertEquals(listOf("https://newer.example/"), InactiveTabs.list(prefs).map { it.url })
    }

    @Test
    fun openTabs_moveToTheInactiveSection_whenTheTabSwitcherOpens() = launch {
        open("https://front.example/")
        chooseFromMenu(R.id.main_new_tab)
        open("https://other.example/")
        assertEquals(2, tabs().size)
        // The first tab was last in front long ago.
        prefs.edit().remove("tab_last_active").commit()
        InactiveTabs.record(prefs, listOf("https://front.example/" to "", "https://other.example/" to ""), null, System.currentTimeMillis() - 40 * day)

        showSwitcher()

        assertEquals(listOf("https://other.example/"), tabs().map { it.url })
        assertEquals(listOf("https://front.example/"), InactiveTabs.list(prefs).map { it.url })
        assertEquals("1 inactive tab", inactive.text.toString())
    }

    @Test
    fun closingATab_needsNoConfirmation_whateverWasStoredBefore() {
        prefs.edit().putBoolean("sp_close_tab_confirm", true).commit()
        launch {
            open("https://one.example/")
            chooseFromMenu(R.id.main_new_tab)
            open("https://two.example/")
            activity.removeAlbum(web)
            idle()
            assertEquals(listOf("https://one.example/"), tabs().map { it.url })
        }
    }

    @Test
    fun closingTheLastTab_leavesANewTab_andClosingThatLeavesTheApp() = launch {
        open("https://only.example/")
        assertTrue(prefs.getString("openTabs", "")!!.contains("only.example"))
        activity.removeAlbum(web)
        idle()
        assertEquals("a new tab took its place", 1, tabs().size)
        assertTrue(web.isShowingHomePage)
        assertFalse("the page does not come back with the next start", prefs.getString("openTabs", "")!!.contains("only.example"))
        assertFalse(activity.isFinishing)

        activity.removeAlbum(web)
        idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun closingTheLastTab_whenItIsPrivate_leavesANormalNewTab() = launch {
        chooseFromMenu(R.id.main_new_private_tab)
        open("https://secret.example/")
        val normal = tabs().first { !it.isPrivateTab }
        activity.removeAlbum(normal)
        idle()
        assertEquals(1, tabs().size)
        assertTrue(web.isPrivateTab)

        activity.removeAlbum(web)
        idle()
        assertEquals(1, tabs().size)
        assertFalse(web.isPrivateTab)
        assertTrue(web.isShowingHomePage)
        assertFalse(activity.isFinishing)
    }

    // ------------------------------------------------------ 1. Incognito lock

    private fun secureDevice() {
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceSecure(true)
    }

    @Test
    fun leavingTheApp_locksThePrivateTabs_andTheScreenLockOpensThem() {
        secureDevice()
        prefs.edit().putBoolean(IncognitoLock.KEY, true).commit()
        launch {
            open("https://normal.example/")
            chooseFromMenu(R.id.main_new_private_tab)
            assertTrue(web.isPrivateTab)
            assertEquals(View.GONE, lock.visibility)

            controller.pause().stop()
            assertTrue(IncognitoLock.locked)
            assertEquals("covered as soon as the app is left", View.VISIBLE, lock.visibility)

            controller.start().resume()
            idle()
            assertEquals(View.VISIBLE, lock.visibility)
            assertEquals("Unlock Incognito", view<TextView>(R.id.incognito_unlock).text.toString())
            assertEquals("See other tabs", view<TextView>(R.id.incognito_see_other_tabs).text.toString())

            // "See other tabs" goes to a normal tab; the private ones stay locked.
            view<View>(R.id.incognito_see_other_tabs).performClick()
            idle()
            assertFalse(web.isPrivateTab)
            assertEquals(View.GONE, lock.visibility)
            assertTrue(IncognitoLock.locked)

            // In the tab switcher the cards of the private tabs are covered as well.
            showSwitcher()
            assertEquals(View.GONE, lock.visibility)
            view<View>(R.id.tabs_mode_private).performClick()
            idle()
            assertEquals(View.VISIBLE, lock.visibility)

            // The screen lock was passed.
            activity.unlockIncognito()
            assertFalse(IncognitoLock.locked)
            assertEquals(View.GONE, lock.visibility)
        }
    }

    @Test
    fun withoutTheSetting_orWithoutPrivateTabs_nothingIsLocked() {
        secureDevice()
        launch {
            chooseFromMenu(R.id.main_new_private_tab)
            controller.pause().stop()
            assertFalse("the setting is off", IncognitoLock.locked)
            controller.start().resume()
        }
        prefs.edit().putBoolean(IncognitoLock.KEY, true).commit()
        launch {
            open("https://normal.example/")
            controller.pause().stop()
            assertFalse("no private tab is open", IncognitoLock.locked)
            assertEquals(View.GONE, lock.visibility)
        }
    }

    @Test
    fun whenTheLastPrivateTabIsClosed_theLockGoes() {
        secureDevice()
        prefs.edit().putBoolean(IncognitoLock.KEY, true).commit()
        launch {
            open("https://normal.example/")
            chooseFromMenu(R.id.main_new_private_tab)
            controller.pause().stop()
            controller.start().resume()
            assertEquals(View.VISIBLE, lock.visibility)

            activity.removeAlbum(web)
            idle()
            assertFalse(IncognitoLock.locked)
            assertEquals(View.GONE, lock.visibility)
        }
    }

    // ------------------------------------------------------- 3. Accessibility

    @Test
    fun theDefaultZoom_isTheTextSizeOfEveryPage_andASiteCanHaveItsOwn() {
        PageZoom.setDefaultZoom(prefs, 150)
        PageZoom.setSiteZoom(prefs, "big.example", 200)
        launch {
            open("https://example.org/")
            assertEquals(150, web.settings.textZoom)
            open("https://www.big.example/page")
            assertEquals(200, web.settings.textZoom)
            open("https://example.com/")
            assertEquals(150, web.settings.textZoom)
        }
    }

    @Test
    fun theZoomEntryOfTheMenu_followsItsSetting_andZoomsTheSite() {
        launch {
            open("https://example.org/")
            openMenu()
            assertFalse("not in the menu unless asked for", activity.mainMenu.menu.findItem(R.id.main_zoom).isVisible)
            activity.mainMenu.dismiss()
        }
        // (The tab of the first start would come back; start afresh.)
        prefs.edit().putBoolean(PageZoom.KEY_ALWAYS_SHOW, true).remove("openTabs").commit()
        launch {
            openMenu()
            val onHome = activity.mainMenu.menu.findItem(R.id.main_zoom)
            assertTrue(onHome.isVisible)
            assertEquals("Zoom", onHome.title.toString())
            assertFalse("the home page is not a site", onHome.isEnabled)
            activity.mainMenu.dismiss()

            open("https://www.example.org/article")
            openMenu()
            assertTrue(activity.mainMenu.menu.findItem(R.id.main_zoom).isEnabled)
            activity.mainMenu.dismiss()

            val sheet = PageHooks.showZoomSheet(web)
            idle()
            assertEquals("100 %", sheet.findViewById<TextView>(R.id.page_zoom_current_value_text)!!.text.toString())
            sheet.findViewById<View>(R.id.page_zoom_increase_zoom_button)!!.performClick()
            idle()
            assertEquals("110 %", sheet.findViewById<TextView>(R.id.page_zoom_current_value_text)!!.text.toString())
            assertEquals(110, web.settings.textZoom)
            assertEquals("kept for the site", 110, PageZoom.siteZoom(prefs, "https://example.org/"))
            sheet.dismiss()
        }
    }

    @Test
    fun aPrivateTab_zoomsWithoutWritingTheSiteDown() = launch {
        chooseFromMenu(R.id.main_new_private_tab)
        open("https://secret.example/")
        val sheet = PageHooks.showZoomSheet(web)
        idle()
        sheet.findViewById<View>(R.id.page_zoom_increase_zoom_button)!!.performClick()
        idle()
        assertEquals(110, web.settings.textZoom)
        assertTrue(PageZoom.siteZooms(prefs).isEmpty())
        sheet.dismiss()
    }

    @Test
    fun forceEnableZoom_rewritesWhatThePageForbids() {
        launch {
            open("https://example.org/")
            PageHooks.onPageFinished(web, "https://example.org/")
            assertNull("off: pages are left alone", shadowOf(web).lastEvaluatedJavascript)
        }
        prefs.edit().putBoolean(PageZoom.KEY_FORCE_ENABLE, true).commit()
        launch {
            open("https://example.org/")
            PageHooks.onPageFinished(web, "https://example.org/")
            val script = shadowOf(web).lastEvaluatedJavascript
            assertTrue(script, script.contains("user-scalable=yes"))
            assertTrue(script, script.contains("maximum-scale"))
        }
    }

    @Test
    fun theSimplifiedView_isOfferedForArticles_whenSwitchedOn() {
        launch {
            open("https://news.example/story")
            PageHooks.onPageFinished(web, "https://news.example/story")
            assertNull("off: no page is examined", shadowOf(web).lastEvaluatedJavascript)
        }
        prefs.edit().putBoolean(ReaderMode.KEY, true).commit()
        launch {
            open("https://news.example/story")
            PageHooks.onPageFinished(web, "https://news.example/story")
            val script = shadowOf(web).lastEvaluatedJavascript
            assertTrue("Mozilla's test for an article", script.contains("isProbablyReaderable"))
            assertNotNull(ReaderMode.extractScript(context))
            assertTrue(ReaderMode.extractScript(context)!!.contains("new Readability("))
        }
        assertFalse("the app's own pages are not articles", ReaderMode.isCandidate("explorer://dino"))
        assertFalse(ReaderMode.isCandidate(null))
    }

    @Test
    fun theSimplifiedView_showsTitleAuthorAndText_withOrWithoutLinks() {
        val article = org.json.JSONObject()
            .put("title", "Tin & <Woodman>")
            .put("byline", "L. Frank Baum")
            .put("siteName", "Oz Times")
            .put("content", "<p>The road was <a href=\"https://oz.example/\">yellow</a>.</p>")
            .toString()
        val page = ReaderMode.page(article, showLinks = true)!!
        assertTrue(page, page.contains("<h1>Tin &amp; &lt;Woodman&gt;</h1>"))
        assertTrue(page, page.contains("L. Frank Baum \u00b7 Oz Times"))
        assertTrue(page, page.contains("<a href=\"https://oz.example/\">yellow</a>"))
        assertFalse("links can be followed", page.contains("pointer-events:none"))

        val plain = ReaderMode.page(article, showLinks = false)!!
        assertTrue("\"Show hyperlinks\" off: links read as text", plain.contains("pointer-events:none"))

        assertNull(ReaderMode.page("null", true))
        assertNull(ReaderMode.page(null, true))
        assertNull(ReaderMode.page("{\"title\":\"x\",\"content\":\"\"}", true))
        assertNull(ReaderMode.page("not json", true))
    }

    private fun scroll(horizontal: Float, vertical: Float = 0f, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = 200f
                y = 300f
                setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
                setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
            },
        )
        return MotionEvent.obtain(time, time, MotionEvent.ACTION_SCROLL, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
    }

    @Test
    fun aTwoFingerSwipeOnATouchpad_goesBackOrForward_once() {
        val start = SystemClock.uptimeMillis()
        val swipe = TouchpadSwipe()
        val never = { _: Int -> false }
        // Fingers to the right: the page before.
        assertNull(swipe.onEvent(scroll(-1.5f, time = start), never))
        assertNull(swipe.onEvent(scroll(-1.5f, time = start + 20), never))
        assertEquals(TouchpadSwipe.Direction.BACK, swipe.onEvent(scroll(-1.5f, time = start + 40), never))
        assertNull("one swipe, one step", swipe.onEvent(scroll(-3f, time = start + 60), never))
        assertNull(swipe.onEvent(scroll(-3f, time = start + 80), never))

        // After a pause the next swipe counts again; to the left it goes forward.
        assertNull(swipe.onEvent(scroll(2.5f, time = start + 1000), never))
        assertEquals(TouchpadSwipe.Direction.FORWARD, swipe.onEvent(scroll(2.5f, time = start + 1020), never))
    }

    @Test
    fun aSwipe_isLeftToThePage_whenThePageScrollsSideways_orTheSwipeIsVertical() {
        val start = SystemClock.uptimeMillis()
        val swipe = TouchpadSwipe()
        for (i in 0 until 10) {
            assertNull(swipe.onEvent(scroll(-2f, time = start + i * 20L)) { true })
        }
        val vertical = TouchpadSwipe()
        for (i in 0 until 10) {
            assertNull(vertical.onEvent(scroll(-1f, vertical = 5f, time = start + i * 20L)) { false })
        }
        // A finger on the screen is not a touchpad.
        val touch = MotionEvent.obtain(start, start, MotionEvent.ACTION_MOVE, 100f, 100f, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        assertNull(TouchpadSwipe().onEvent(touch) { false })
    }

    @Test
    fun theTouchpadSwipe_followsItsSwitch() = launch {
        assertTrue("on unless switched off, as in Chrome", TouchpadSwipe.isEnabled(prefs))
        open("https://example.org/one")
        open("https://example.org/two")
        val start = SystemClock.uptimeMillis()
        prefs.edit().putBoolean(TouchpadSwipe.KEY, false).commit()
        for (i in 0 until 5) web.dispatchGenericMotionEvent(scroll(-2f, time = start + i * 20L))
        assertEquals("switched off: nothing happens", "https://example.org/two", web.url)
    }
}
