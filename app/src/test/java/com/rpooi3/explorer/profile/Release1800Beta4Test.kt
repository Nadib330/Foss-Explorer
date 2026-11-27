package com.rpooi3.explorer.profile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.browser.ErrorPage
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.SecurityWarning
import com.rpooi3.explorer.unit.DeleteUtils
import com.rpooi3.explorer.view.TabSwitcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 18.0.0 beta 4: the nine things that were reported against beta 3.
 *
 *  1. a website that cannot be loaded gets Explorer's own page, not the one of Android System WebView;
 *  2. the cards of the tab switcher are Chrome's: the page, the colour of the site under it;
 *  3. the profile and the menu of the home page no longer crowd into each other;
 *  4. a tab that is left behind does not come back washed out in the colour of the theme;
 *  5. the actions of the top bar sit close together, and the profile never moves sideways;
 *  6. an untrusted certificate gets Firefox's warning page instead of the old dialog;
 *  7. a guest session shares nothing with the profiles, and leaves nothing behind;
 *  8. clearing the data of a profile clears that profile only;
 *  9. the smaller things that came with them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1800Beta4Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun source(path: String): String = File(path).readText()

    @Before
    fun setUp() {
        context.getSharedPreferences("browser_profiles", Context.MODE_PRIVATE)
            .edit().clear().commit()
        Profiles.cacheCurrent(context)
    }

    // 1. The page of a website that could not be loaded -----------------------------------------

    @Test
    fun aSiteThatCannotBeLoaded_getsOurOwnPage() {
        val html = ErrorPage.html(context, "https://music.invidious.nerdvpn.de/", "net::ERR_NAME_NOT_RESOLVED")
        assertTrue("the heading of Chrome's page", html.contains("This site can&#39;t be reached") || html.contains("This site can't be reached"))
        assertTrue("the address it was about", html.contains("music.invidious.nerdvpn.de"))
        assertTrue("what is wrong with it", html.contains("Check if there is a typo"))
        assertTrue("a way to search instead", html.contains("Search for"))
        assertTrue("and the name of the error", html.contains("ERR_NAME_NOT_RESOLVED"))
        // Our own page, not the one of the WebView: it carries the picture Explorer draws.
        assertTrue(html.contains("<svg"))
        assertFalse("no script of Chromium's error page", html.contains("neterror"))
    }

    @Test
    fun thePageOfAnError_saysWhatHappened_inTheWordsOfTheLanguage() {
        val refused = ErrorPage.html(context, "http://localhost:8080/", "net::ERR_CONNECTION_REFUSED")
        assertTrue(refused.contains("refused to connect"))
        val timeout = ErrorPage.html(context, "https://example.com/", "net::ERR_CONNECTION_TIMED_OUT")
        assertTrue(timeout.contains("took too long to respond"))
    }

    @Test
    fun aLoadThatWasStoppedOnPurpose_getsNoPage() {
        assertFalse("nothing to say about a load the browser ended", ErrorPage.isWorthShowing("net::ERR_ABORTED"))
        assertFalse(ErrorPage.isWorthShowing("net::ERR_BLOCKED_BY_CLIENT"))
        assertFalse(ErrorPage.isWorthShowing(null))
        assertTrue(ErrorPage.isWorthShowing("net::ERR_NAME_NOT_RESOLVED"))
        assertEquals("ERR_NAME_NOT_RESOLVED", ErrorPage.errorCodeOf("net::ERR_NAME_NOT_RESOLVED"))
        assertEquals("music.invidious.nerdvpn.de", ErrorPage.hostOf("https://music.invidious.nerdvpn.de/"))
    }

    // 2. The cards of the tab switcher -----------------------------------------------------------

    @Test
    fun aCardOfTheSwitcher_isThePage_andTheColourOfTheSite() {
        val card = source("src/main/res/layout/item_tab_card.xml")
        assertTrue("the picture comes first", card.indexOf("tab_card_thumbnail") < card.indexOf("tab_card_bar"))
        assertTrue("the site's colour runs under it", card.contains("@+id/tab_card_bar"))
        assertTrue("the cross sits on the picture", card.contains("bg_tab_close"))
        assertTrue("with the icon and the name of the site", card.contains("@+id/tab_card_icon"))
        // The band takes the colour of the top of the page, as Chrome takes the site's colour.
        val page = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.rgb(30, 120, 200))
        assertEquals("the blue of the page", Color.rgb(30, 120, 200), TabSwitcher.bandColour(page))
        assertNull("no picture, no colour of its own", TabSwitcher.bandColour(null))
        assertTrue("white writing on a dark band", TabSwitcher.isDark(Color.rgb(20, 20, 20)))
        assertFalse("black writing on a light one", TabSwitcher.isDark(Color.rgb(250, 250, 250)))
    }

    // 3. and 5. The two buttons of the home page, and the row of the top bar ---------------------

    @Test
    fun theProfileAndTheMenu_ofTheHomePage_standWhereTheBarStands() {
        val home = source("src/main/res/layout/include_home.xml")
        assertTrue("one row for both", home.contains("@+id/home_actions"))
        val row = home.indexOf("@+id/home_actions")
        assertTrue("the profile is in it", home.indexOf("@+id/home_profile") > row)
        assertTrue("and the menu too", home.indexOf("@+id/home_menu") > row)
        // 18.0.0 beta 7 put the profile back where beta 3 had it: the corner of the page, one step
        // from the top and from the side, as wide as the buttons of the bar — and the empty place
        // of the shield that beta 4 put in front of it is gone with it.
        val cluster = home.substring(row, home.indexOf("</LinearLayout>", row))
        assertFalse("no empty place in front of the profile any more", cluster.contains("<View"))
        assertTrue("the buttons are as wide as the ones of the bar", cluster.contains("@dimen/bar_button_size\""))
        assertTrue("at the top right", cluster.contains("android:layout_gravity=\"top|end\""))
        // Nothing of the page lies above the row: it comes last, so it can be tapped.
        assertTrue(
            "the row is the last thing on the home page",
            home.indexOf("@+id/home_scroll") < row,
        )
        assertFalse(
            "the menu is not inside the page any more",
            home.substring(0, home.indexOf("</ScrollView>")).contains("@+id/home_menu"),
        )
    }

    @Test
    fun theActionsOfTheTopBar_sitClose_andTheProfileNeverMoves() {
        val bar = source("src/main/res/layout/include_appbar.xml")
        assertTrue("the narrower buttons", bar.contains("@dimen/bar_button_size_compact"))
        assertFalse(
            "none of them is as wide as it was",
            bar.contains("android:layout_width=\"@dimen/bar_button_size\""),
        )
        val browser = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        assertTrue(
            "the shield keeps its place when there is nothing to show",
            browser.contains("buttonShield.setVisibility(applies ? VISIBLE : INVISIBLE)"),
        )
    }

    // 4. A tab that comes back is not washed out -------------------------------------------------

    @Test
    fun aTabThatComesBack_isNotLeftFaded() {
        val browser = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        assertTrue(
            "choosing a card puts the page back to its size",
            browser.contains("public void selectTab(@NonNull final NinjaWebView tab) {")
                && browser.contains("animateContentForSwitcher(false);"),
        )
        assertTrue("and there is a way to undo the step, without an animation", browser.contains("resetContentTransform()"))
        assertTrue(
            "a new tab grows on its own, not with the container the switcher moves",
            browser.contains("NewTabAnimation.run(av, findViewById(R.id.home_page))"),
        )
        val animation = source("src/main/java/com/rpooi3/explorer/view/TabSwitcher.kt")
        assertTrue("the switcher itself still puts its cards away cleanly", animation.contains("root.alpha = 1f"))
    }

    // 6. An untrusted certificate ----------------------------------------------------------------

    @Test
    fun anUntrustedSite_getsTheWarningOfFirefox() {
        // 18.0.0 beta 5: the same words as the page of Firefox had, now on a screen of Explorer's
        // own (a page inside the WebView could not be left: it kept the certificate pending).
        val body = context.getString(R.string.ssl_body, SecurityWarning.hostOf("https://self-signed.badssl.com/"))
        assertTrue("the site it is about", body.contains("self-signed.badssl.com"))
        assertEquals("Warning: Potential Security Risk Ahead", context.getString(R.string.ssl_heading))
        assertEquals("Go Back", context.getString(R.string.ssl_go_back))
        assertEquals("Accept the Risk and Continue", context.getString(R.string.ssl_accept))
        assertTrue(
            "the error code of Firefox",
            SecurityWarning.reason(context, SslError.SSL_UNTRUSTED).contains("certificate"),
        )
        assertEquals("SEC_ERROR_EXPIRED_CERTIFICATE", SecurityWarning.errorCode(SslError.SSL_EXPIRED))
        assertEquals("SSL_ERROR_BAD_CERT_DOMAIN", SecurityWarning.errorCode(SslError.SSL_IDMISMATCH))
        assertEquals("SEC_ERROR_UNKNOWN_ISSUER", SecurityWarning.errorCode(SslError.SSL_UNTRUSTED))
        // It does not go through a page any more, and the old dialog is gone.
        val view = source("src/main/java/com/rpooi3/explorer/view/NinjaWebView.java")
        assertTrue(view.contains("acceptSslWarning"))
        assertTrue(view.contains("cancelSslWarning"))
        val client = source("src/main/java/com/rpooi3/explorer/browser/NinjaWebViewClient.java")
        assertTrue(client.contains("showSslWarning"))
        assertFalse(
            "no dialog in the middle of the screen any more",
            client.substring(client.indexOf("onReceivedSslError"), client.indexOf("onReceivedHttpAuthRequest"))
                .contains("showCustomMaterialDialogWithTwoActions"),
        )
    }

    // 7. The guest session -----------------------------------------------------------------------

    @Test
    fun aGuestSession_leavesNothingOfItsOwnBehind() {
        val data = File(context.applicationInfo.dataDir, "databases")
        data.mkdirs()
        val stale = File(data, "places-guest-1234abcd.sqlite")
        stale.writeText("history of a session that ended badly")
        val stalePrefs = File(File(context.applicationInfo.dataDir, "shared_prefs"), "site@guest-1234abcd.xml")
        stalePrefs.parentFile?.mkdirs()
        stalePrefs.writeText("<map/>")

        val guest = Profiles.startGuest(context)
        assertEquals(guest.id, Profiles.currentId(context))
        // 18.0.0 beta 4: the next guest does not find the data of the one before.
        assertFalse("an earlier session's history is gone", stale.exists())
        assertFalse("and its preferences too", stalePrefs.exists())
        assertTrue("the new guest has a database of its own", Profiles.databaseName(context, "places.sqlite").contains(guest.id))

        Profiles.create(context, "Work", 1)
        Profiles.leaveGuest(context)
        assertFalse(Profiles.isGuest(context))
        assertEquals("", Profiles.currentId(context))
    }

    // 8. Clearing the data of a profile -----------------------------------------------------------

    @Test
    fun clearingTheDataOfAProfile_clearsThatProfileOnly() {
        val work = Profiles.create(context, "Work", 1)!!
        // Two profiles, each with a setting of its own.
        ProfilePrefs.getFor(context, "site", "").edit().putString("probe", "first").commit()
        ProfilePrefs.getFor(context, "site", work.id).edit().putString("probe", "work").commit()

        Profiles.switchTo(context, work.id)
        Profiles.cacheCurrent(context)
        DeleteUtils.executeDeletion(context, false, false, false, true, false, false)

        assertTrue(
            "the settings of the profile in front are gone",
            ProfilePrefs.getFor(context, "site", work.id).all.isEmpty(),
        )
        assertEquals(
            "the other profile keeps its own",
            "first",
            ProfilePrefs.getFor(context, "site", "").getString("probe", null),
        )
        assertTrue("and the list of the profiles is still there", Profiles.all(context).size >= 2)
    }

    @Test
    fun theScreenThatClearsData_isScopedToTheProfileInFront() {
        val utils = source("src/main/java/com/rpooi3/explorer/unit/DeleteUtils.java")
        assertFalse(
            "it no longer wipes every database of the app",
            utils.contains("context.databaseList()"),
        )
        assertTrue("only the databases of the profile", utils.contains("PlacesDatabase.nameOf(context, id)"))
        assertTrue("the web storage of the profile", utils.contains("ProfileWebView.clearStorage(context)"))
        assertTrue("its preferences, by their id", utils.contains("ProfileBackup.belongsToPreferences(name, id)"))
        assertTrue("the list of the profiles is never cleared", utils.contains("browser_profiles.xml"))
        assertTrue("the cache keeps the folders of the other profiles", utils.contains("deleteChildren(context.getCacheDir()"))
    }

    // 9. The smaller things ----------------------------------------------------------------------

    @Test
    fun theVersion_isStill18_0_0_andPastBeta4() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        // 18.0.0 beta 5 builds on the files of beta 4 and keeps its number going.
        assertTrue("versionCode " + info.versionCode, info.versionCode >= 21)
        assertTrue(info.versionName ?: "", (info.versionName ?: "").startsWith("18."))
    }

    @Test
    fun theErrorPageAndTheWarning_arePagesOfTheirOwn() {
        // The two pages are in the assets of the app and are read once.
        assertNotNull(context.assets.open("error/error.html").use { it.readBytes() })
        // 18.0.0 beta 5: the warning of a certificate is a screen of the app and not a page, so
        // there is no ssl.html any more; its layout takes the colours of the theme instead.
        val warning = source("src/main/res/layout/view_security_warning.xml")
        assertTrue("the theme's colours", warning.contains("?attr/colorSurface"))
        assertTrue("and its text sizes", warning.contains("?attr/textAppearanceHeadlineSmall"))
        val error = context.assets.open("error/error.html").bufferedReader().use { it.readText() }
        assertTrue(error.contains("prefers-color-scheme: dark"))
    }
}
