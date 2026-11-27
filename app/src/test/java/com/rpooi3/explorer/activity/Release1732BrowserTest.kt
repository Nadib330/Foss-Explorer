package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.NinjaWebView
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

/**
 * 17.3.2, item 1, as the browser shows it, and 18.2.0 beta (item 2) which reverses the first
 * half of it: the store is given its desktop agent but "Desktop site" can be turned off on it,
 * and the extension tile of the three-dot menu is on the home page as well, where it takes the
 * whole row that "Zoom" and "Extensions" share on a website.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1732BrowserTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val extensionId = "cjpalhdlnbpafiamejdnhcphjbkeiagm"

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private class Browser(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val web: NinjaWebView get() = content.getChildAt(content.childCount - 1) as NinjaWebView
        fun open(url: String) {
            web.loadUrl(url)
            activity.updateOmniBox()
            shadowOf(Looper.getMainLooper()).idle()
        }

        fun openMenu(): View {
            activity.findViewById<View>(R.id.button_menu).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            return activity.mainMenu.contentView!!
        }
    }

    private fun browser(block: Browser.() -> Unit) {
        // The welcome of the first start is not what these tests are about.
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean("welcome_seen", true).commit()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().use { controller ->
            Browser(controller.get()).apply {
                idle()
                block()
                idle()
            }
        }
    }

    @Test
    fun theStore_keepsItsDesktopAgent_butDesktopSiteCanBeTurnedOff() = browser {
        open("https://chromewebstore.google.com/detail/x/$extensionId")

        assertTrue("the tab knows that it is on the store", web.isChromeWebStore())
        // 18.2.0 beta (item 2): 17.3.2 had forced the store into the desktop layout and greyed
        // the entry of the menu out. The store is given its desktop agent either way — that is
        // what offers its install button (18.1.0 beta, item 6; 18.2.0 beta, item 4) — but the
        // layout is the user's.
        assertFalse("a new tab is a mobile one, even on the store", web.isDesktopSite)
        val mobileAgent = web.settings.userAgentString
        assertTrue("a Chrome user agent of a computer: $mobileAgent", mobileAgent.contains("X11; Linux x86_64"))
        assertFalse("not the one of a phone: $mobileAgent", mobileAgent.contains("Mobile"))
        // The phone's width: the store's own responsive page is the mobile one.
        assertFalse("the store is not laid out as a desktop window", web.settings.useWideViewPort)

        // It can be turned on, and that is a desktop window.
        web.setDesktopSite(true)
        idle()
        assertTrue("the store is the desktop site", web.isDesktopSite)
        assertTrue("laid out as a desktop window", web.settings.useWideViewPort)
        assertTrue(
            "and told so inside itself",
            web.settings.userAgentString.contains("X11; Linux x86_64"),
        )

        // And off again — the store is not an exception any more.
        web.setDesktopSite(false)
        idle()
        assertFalse("it goes back to the phone's layout", web.isDesktopSite)
        assertFalse("with the width of the page", web.settings.useWideViewPort)

        // The entry of the menu is the ordinary one: it can be used, and it shows the state.
        openMenu()
        val menu = activity.mainMenu
        assertTrue("Desktop site is a switch like anywhere else", menu.menu.findItem(R.id.main_desktop_site).isEnabled)
        assertFalse("and shows that it is off", menu.menu.findItem(R.id.main_desktop_site).isChecked)

        // On any other site the switch is the same, and the computer is one running Windows.
        open("https://example.com/")
        assertFalse(web.isChromeWebStore())
        assertFalse("a website starts as the mobile one too", web.isDesktopSite)
        web.setDesktopSite(true)
        idle()
        val other = web.settings.userAgentString
        assertTrue("a website is served Windows: $other", other.contains("Windows NT 10.0"))
        assertFalse("and not the Linux one of the store: $other", other.contains("X11; Linux"))
    }

    @Test
    fun theExtensionTile_isOnTheHomePage_too_andTakesTheWholeRow() = browser {
        val content = openMenu()
        val menu = activity.mainMenu

        assertTrue("Extensions on the home page", menu.menu.findItem(R.id.main_extension).isVisible)
        assertTrue(menu.menu.findItem(R.id.main_extension).isEnabled)
        assertFalse("nothing to zoom on the home page", menu.menu.findItem(R.id.main_zoom).isVisible)

        // The tile stands alone in its group, so it takes the whole row.
        val groups = content.findViewById<LinearLayout>(R.id.main_menu_groups)
        val row = (0 until groups.childCount)
            .map { groups.getChildAt(it) as ViewGroup }
            .firstOrNull { group ->
                (0 until group.childCount).any { group.getChildAt(it).id == R.id.main_extension }
            }
        assertNotNull("the extension is in a row of the menu", row)
        assertEquals("it is alone in it", 1, row!!.childCount)
    }

    @Test
    fun onAWebsite_theExtensionTile_isBesideTheZoom() = browser {
        open("https://example.org/")
        val content = openMenu()
        val menu = activity.mainMenu

        assertTrue(menu.menu.findItem(R.id.main_extension).isVisible)
        assertTrue(menu.menu.findItem(R.id.main_zoom).isVisible)

        val groups = content.findViewById<LinearLayout>(R.id.main_menu_groups)
        val row = (0 until groups.childCount)
            .map { groups.getChildAt(it) as ViewGroup }
            .firstOrNull { group ->
                (0 until group.childCount).any { group.getChildAt(it).id == R.id.main_extension }
            }
        assertNotNull(row)
        assertEquals(
            "half the row each",
            listOf(R.id.main_zoom, R.id.main_extension),
            (0 until row!!.childCount).map { row.getChildAt(it).id },
        )
        for (index in 0 until row.childCount) {
            assertEquals(
                1f,
                (row.getChildAt(index).layoutParams as LinearLayout.LayoutParams).weight,
                0f,
            )
        }
    }
}
