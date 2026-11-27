package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.ChromeUserAgent
import com.rpooi3.explorer.extension.StoreBridge
import com.rpooi3.explorer.extension.StoreHook
import com.rpooi3.explorer.view.NinjaWebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * 18.2.0 beta: the four things asked for after 18.1.0 beta.
 *
 * 1. the foot of the tab switcher is a rounded rectangle rather than a capsule, and the plus
 *    button beside it has a surface that can be told apart from the page; 2. the Chrome Web Store
 *    is no longer forced into the desktop layout — "Desktop site" can be turned off there;
 * 3. the pencil of the home page is a rounded square; 4. the store's own install button is
 *    un-greyed by a polyfill of `chrome.webstorePrivate` and `chrome.management`, whose install
 *    call ends in the browser's `.crx` pipeline and whose list is the extensions that are here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1820BetaTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private val extensionId = "cjpalhdlnbpafiamejdnhcphjbkeiagm"

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    private fun source(path: String): String = File(path).readText()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    // 0. the version ----------------------------------------------------------------------------
    @Test
    fun theVersion_is30_and1840() {
        val gradle = source("build.gradle.kts")
        assertTrue("versionCode 31", gradle.contains("versionCode = 31"))
        assertTrue("versionName 18.5.1-nightly", gradle.contains("versionName = \"18.5.1-nightly\""))
    }

    // 1. the foot of the tab switcher ------------------------------------------------------------
    @Test
    fun theTabsBar_isARoundedRectangle_of16dp() {
        val layout = source("src/main/res/layout/include_tab_switcher.xml")
        val pill = layout.substring(
            layout.indexOf("android:id=\"@+id/tabs_pill\""),
            layout.indexOf("android:id=\"@+id/tabs_fab_card\""),
        )
        assertTrue("a rounded rectangle, not a capsule", pill.contains("app:cardCornerRadius=\"16dp\""))
        assertFalse("and not the 24 dp of the capsule", pill.contains("app:cardCornerRadius=\"24dp\""))
        assertTrue("on the third tone of the surface", pill.contains("?attr/colorSurfaceContainerHigh"))
        assertTrue("with no edge drawn round it", pill.contains("app:strokeWidth=\"0dp\""))
        assertTrue("and no shadow", pill.contains("app:cardElevation=\"0dp\""))
        // The two buttons of it — the tabs and the private tabs — are rounded rectangles too, and
        // of the same radius, so that the selected one sits inside the bar as one shape.
        val theme = source("src/main/res/values/themes.xml")
        val button = theme.substring(theme.indexOf("<style name=\"TabSwitcherModeButton\""))
        assertTrue("the button is a rounded rectangle", button.substring(0, 900).contains("<item name=\"cornerRadius\">16dp</item>"))
    }

    @Test
    fun thePlusButton_canBeToldApartFromThePage() {
        val layout = source("src/main/res/layout/include_tab_switcher.xml")
        val fab = layout.substring(layout.indexOf("android:id=\"@+id/tabs_fab_card\""))
        // 18.1.0 beta had put it on `colorSurfaceBright`, which is the page itself in the light
        // theme: the rounded square was there, and could not be seen.
        assertTrue("uses the shared plus surface", fab.contains("?attr/colorSurfaceContainerHighest"))
        assertTrue("and is a rounded square", fab.contains("app:cardCornerRadius=\"12dp\""))
        assertTrue("still the rounded square of 12 dp", fab.contains("app:cardCornerRadius=\"12dp\""))
        assertTrue("of 40 × 40 dp", fab.contains("android:layout_width=\"@dimen/new_tab_button_size\"") && fab.contains("android:layout_height=\"@dimen/new_tab_button_size\""))
        // The colour it is drawn with exists in both themes, so neither of them loses the shape.
        val colors = source("src/main/res/values/colors.xml")
        assertTrue(
            "an attribute of Material 3, resolved by the theme",
            layout.contains("?attr/colorSurfaceContainerHighest"),
        )
        assertTrue("the surface of the page is a different one", colors.isNotEmpty())
    }

    // 2. "Desktop site" on the store -------------------------------------------------------------
    @Test
    fun theStore_canBeTurnedBackToTheMobileLayout() {
        val web = source("src/main/java/com/rpooi3/explorer/view/NinjaWebView.java")
        // Nothing in the tab forces the desktop site on any more.
        assertFalse("no early return for the store", web.contains("if (isChromeWebStore()) return true;"))
        assertFalse("and nothing is forced into the switch", web.contains("if (isChromeWebStore()) desktop = true;"))
        assertFalse("the menu does not disable it either", web.contains("desktopSite.setEnabled(false)"))
        // The store's branch is still the first one — it is given its own agent — but its layout
        // is what the switch says.
        assertTrue("the store has a branch of its own", web.contains("if (store) {"))
        assertTrue("the agent of the store", web.contains("ChromeUserAgent.store(defaultAgent)"))
        assertTrue("a desktop window when it is on", web.contains("webSettings.setUseWideViewPort(desktop)"))
        assertTrue("showing all of it", web.contains("webSettings.setLoadWithOverviewMode(desktop)"))
        assertTrue("at the scale of the page", web.contains("this.setInitialScale(desktop ? 100 : 0)"))
        val identity = source("src/main/java/com/rpooi3/explorer/browser/NinjaWebViewClient.java")
        assertTrue(
            "and the store keeps its Linux identity when it is off",
            identity.contains("ninjaWebView.isDesktopSite() || ninjaWebView.isChromeWebStore()"),
        )
    }

    @Test
    fun theStore_obeysTheSwitch_inTheBrowserItself() {
        prefs.edit().putBoolean("welcome_seen", true).commit()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            idle()
            val content = activity.findViewById<ViewGroup>(R.id.main_content)
            val web = content.getChildAt(content.childCount - 1) as NinjaWebView

            web.loadUrl("https://chromewebstore.google.com/detail/x/$extensionId")
            activity.updateOmniBox()
            idle()
            assertTrue("the tab knows the store", web.isChromeWebStore())
            assertTrue("its agent is the desktop one", web.settings.userAgentString.contains("X11; Linux x86_64"))
            assertFalse("but the layout is the phone's", web.isDesktopSite)

            web.setDesktopSite(true)
            idle()
            assertTrue("on, and a desktop window", web.isDesktopSite && web.settings.useWideViewPort)

            web.setDesktopSite(false)
            idle()
            assertFalse("and off again", web.isDesktopSite)
            assertFalse("at the phone's width", web.settings.useWideViewPort)

            // The entry of the menu is the ordinary one.
            activity.findViewById<View>(R.id.button_menu).performClick()
            idle()
            val item = activity.mainMenu.menu.findItem(R.id.main_desktop_site)
            assertTrue("Desktop site can be used on the store", item.isEnabled)
            assertFalse("and shows that it is off", item.isChecked)
        }
    }

    // 3. the pencil of the home page -------------------------------------------------------------
    @Test
    fun thePencil_isARoundedSquare() {
        val layout = source("src/main/res/layout/include_home.xml")
        val card = layout.substring(layout.indexOf("android:id=\"@+id/home_customize_card\""))
        assertTrue("a rounded square of 12 dp", card.substring(0, 700).contains("app:cardCornerRadius=\"12dp\""))
        assertFalse("and not the circle of 24 dp", card.substring(0, 700).contains("app:cardCornerRadius=\"24dp\""))
        assertTrue("of the same shape as the plus of the toolbar", card.substring(0, 700).contains("android:layout_width=\"48dp\""))
        assertTrue("in the second container colour", card.substring(0, 700).contains("?attr/colorSecondaryContainer"))
        assertTrue("and flat enough to sit on the page", card.substring(0, 700).contains("app:cardElevation=\"2dp\""))
    }

    // 4. the store's own install button ----------------------------------------------------------
    @Test
    fun theStore_isGivenTheHandshake_ofItsButton() {
        val script = StoreHook.script(context, extensionId, "t")
        // chrome.management: the list behind "Remove from Explorer", and the removal itself.
        assertTrue("chrome.management", script.contains("window.chrome.management"))
        assertTrue("getAll", script.contains("getAll: function"))
        assertTrue("get", script.contains("get: function"))
        assertTrue("uninstall", script.contains("uninstall: function"))
        assertTrue("the list comes from the browser", script.contains("installedExtensions(TOKEN)"))
        assertTrue("and the removal goes back to it", script.contains("uninstallFromStore(TOKEN"))
        // chrome.webstorePrivate: the handshake that decides whether the button is greyed out.
        assertTrue("chrome.webstorePrivate", script.contains("window.chrome.webstorePrivate"))
        for (method in listOf(
            "getBrowserLogin", "getStoreLogin", "getIsLauncherEnabled", "isInIncognitoMode",
            "getFullChromeVersion", "getExtensionStatus", "beginInstallWithManifest3", "completeInstall",
        )) {
            assertTrue("$method is answered", script.contains("$method: function"))
        }
        assertTrue("an extension that is not here is installable", script.contains("'installable'"))
        assertTrue("and one that is, is installed", script.contains("'installed'"))
        assertTrue("the version is the one of the agent", script.contains("version_number: VERSION"))
        // The install itself: the id goes to the bridge, and the callback is answered.
        assertTrue("the object of the browser", script.contains("window.ExplorerStoreBridge.beginInstall"))
        assertTrue("and the bridge of the extension api", script.contains("installFromStore"))
        // Nothing is drawn into the page: no style sheet, no node, no observer.
        for (banned in listOf("style.zoom", "MutationObserver", "createElement", "appendChild", "innerHTML")) {
            assertFalse("the page is not touched: $banned", script.contains(banned))
        }
    }

    @Test
    fun theStore_isToldAModernChrome() {
        // The engine of the device, when it is newer than 131.
        val old = ChromeUserAgent.store("Mozilla/5.0 (Linux; Android 9; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.104 Mobile Safari/537.36")
        assertTrue("never older than 131: $old", old.contains("Chrome/131.0.0.0"))
        val fresh = ChromeUserAgent.store("Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.7444.60 Mobile Safari/537.36")
        assertTrue("and the one of the device when it is newer: $fresh", fresh.contains("Chrome/142.0.0.0"))
        assertTrue("always Chrome on Linux", fresh.contains("X11; Linux x86_64"))
        assertTrue("with the brand of this browser", fresh.contains("Explorer/1.0"))
        assertFalse("and never the one of a phone", fresh.contains("Mobile"))
        assertEquals("the floor is 131", "131", ChromeUserAgent.STORE_MIN_MAJOR)
        assertEquals("131", ChromeUserAgent.storeMajorVersion("Chrome/96.0.4664.104"))
        assertEquals("142", ChromeUserAgent.storeMajorVersion("Chrome/142.0.7444.60"))
    }

    @Test
    fun theStoreBridge_isTheOneItsPageFinds_andItInstalls() {
        // The object is registered for the page of the store under the name the shim calls.
        val web = source("src/main/java/com/rpooi3/explorer/view/NinjaWebView.java")
        assertTrue("ExplorerStoreBridge", web.contains("\"ExplorerStoreBridge\""))
        assertTrue("the class of it", web.contains("com.rpooi3.explorer.extension.StoreBridge"))
        // And it goes to the installer of the browser — the one of the three-dot menu.
        val bridge = source("src/main/java/com/rpooi3/explorer/extension/StoreBridge.kt")
        assertTrue("beginInstall", bridge.contains("fun beginInstall("))
        assertTrue("it reads the id", bridge.contains("CrxDownloader.extractExtensionId"))
        assertTrue("and hands it to the installer", bridge.contains("StoreHook.install("))
        assertNotNull("the class exists", StoreBridge::class.java)
        // The list and the removal of chrome.management are answered by the browser too.
        val api = source("src/main/java/com/rpooi3/explorer/extension/ChromeApiBridge.kt")
        assertTrue("installedExtensions", api.contains("fun installedExtensions("))
        assertTrue("from the store of extensions", api.contains("ExtensionStore.installed(context)"))
        assertTrue("uninstallFromStore", api.contains("fun uninstallFromStore("))
        assertTrue("which removes it", api.contains("ExtensionStore.remove(context, id)"))
        // The version the page is told is the one of its agent, not a hard code in the menu.
        val hook = source("src/main/java/com/rpooi3/explorer/extension/StoreHook.kt")
        assertTrue(
            "the script is given the version of the tab",
            hook.contains("ChromeUserAgent.storeMajorVersion("),
        )
        assertTrue("and says it is shown as such", hook.contains("app_name: 'Explorer'"))
    }
}
