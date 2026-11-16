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
 * 17.3.2, item 1, as the browser shows it: the Chrome Web Store is always the desktop one and
 * cannot be turned off there, and the extension tile of the three-dot menu is on the home page
 * as well, where it takes the whole row that "Zoom" and "Extensions" share on a website.
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
    fun theStore_isAlwaysTheDesktopOne_andCannotBeTurnedOff() = browser {
        open("https://chromewebstore.google.com/detail/x/$extensionId")

        assertTrue("the tab knows that it is on the store", web.isChromeWebStore())
        assertTrue("the store is a desktop site", web.isDesktopSite)
        val agent = web.settings.userAgentString
        // 17.3.3: the computer is one running Windows.
        assertTrue("a Chrome user agent of a computer: $agent", agent.contains("Windows NT 10.0"))
        assertFalse("not the one of a phone: $agent", agent.contains("Mobile"))

        // Turning it off does nothing there.
        web.setDesktopSite(false)
        idle()
        assertTrue("it stays the desktop site", web.isDesktopSite)

        // And the entry of the menu cannot be used either.
        openMenu()
        val storeMenu = activity.mainMenu
        assertFalse(
            "Desktop site is switched off in the menu",
            storeMenu.menu.findItem(R.id.main_desktop_site).isEnabled,
        )
        assertTrue("it shows as on", storeMenu.menu.findItem(R.id.main_desktop_site).isChecked)

        // On any other site the switch is the site's own again.
        open("https://example.com/")
        assertFalse(web.isChromeWebStore())
        assertTrue(web.isDesktopSite)
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
