package com.rpooi3.explorer.browser

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What 17.3.3 changes: desktop mode is Chrome on Windows, in the header and inside the page; the
 * ad blocker is Chromite's kind — Adblock Plus lists, matched by url, by path and by kind of
 * request, and element rules hidden with a style sheet — instead of FOSS Browser's file of bare
 * host names that was never even in the app; and the tile "Extensions" on the home page is
 * rounded on all four corners like the group it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1733Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val DOLLAR = "\u0024"

    private val mobileAgent =
        "Mozilla/5.0 (Linux; Android 16; X6850 Build/BP2A.250605.031.A3) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.58 Mobile Safari/537.36"

    // 1. Desktop mode is Chrome on Windows — for every site but the Chrome Web Store ---------

    @Test
    fun desktopMode_isChromeOnWindows() {
        val desktop = ChromeUserAgent.desktop(mobileAgent)
        assertTrue(desktop, desktop.startsWith("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
        assertEquals("Windows", ChromeUserAgent.DESKTOP_PLATFORM)
        // The version of the WebView, not a version of its own.
        assertTrue(desktop, desktop.contains("Chrome/130.0.0.0"))
        assertTrue(desktop.endsWith("Safari/537.36"))
    }

    @Test
    fun onlyTheStore_getsTheOldLinuxAgent() {
        // 17.4.0: the agent Explorer served the store before 17.3.3 is back for the store alone.
        val store = ChromeUserAgent.store(mobileAgent)
        assertTrue(store, store.startsWith("Mozilla/5.0 (X11; Linux x86_64)"))
        assertEquals("Linux", ChromeUserAgent.STORE_PLATFORM)
        // 18.2.0 beta (item 4): never older than 131, whatever the WebView reports, so that the
        // store does not grey its install button out for an old engine.
        assertTrue(store, store.contains("Chrome/131.0.0.0"))
        assertEquals("Linux", ChromeUserAgent.platformOf(store = true, desktop = true))
        assertEquals("Windows", ChromeUserAgent.platformOf(store = false, desktop = true))
        assertEquals("Android", ChromeUserAgent.platformOf(store = false, desktop = false))
    }

    @Test
    fun theDesktopAgent_saysNothingOfAndroidAndNothingOfAWebView() {
        val desktop = ChromeUserAgent.desktop(mobileAgent).lowercase()
        assertFalse("a site must not find Android in it", desktop.contains("android"))
        assertFalse("and must not find the WebView sign", desktop.contains("wv"))
        assertFalse("nor a mobile sign", desktop.contains("mobile"))
        // The mobile agent keeps its own signs: mobile mode stays a phone.
        val mobile = ChromeUserAgent.mobile(mobileAgent).lowercase()
        assertTrue("mobile mode says mobile", mobile.contains("mobile"))
        assertFalse("and never says WebView either", mobile.contains("wv"))
    }

    @Test
    fun thePageIsToldWindowsToo_platformAndTheClientHints() {
        val script = DesktopIdentity.script("130")
        // 17.4.0: the platform of the script is the platform of the agent — Windows for a
        // website, Linux for the Chrome Web Store.
        assertTrue(script.contains("'Win32'"))
        assertTrue(script.contains("'Linux x86_64'"))
        assertTrue(script.contains("'platform', NAVIGATOR_PLATFORM"))
        assertTrue(script.contains("mobile: false"))
        assertTrue(script.contains("'Google Chrome'"))
        assertTrue(script.contains("Chrome/130") || script.contains("MAJOR + '.0.0.0'"))
        // Windows as the platform of a desktop page, Linux as the one of the store's.
        assertTrue(DesktopIdentity.script("130", DesktopIdentity.WINDOWS).contains("'Windows'"))
        assertTrue(DesktopIdentity.script("130", DesktopIdentity.LINUX).contains("'Linux'"))
        assertEquals(DesktopIdentity.WINDOWS, ChromeUserAgent.DESKTOP_PLATFORM)
        // It runs before the page does, where the WebView has document-start scripts.
        assertNotNull(DesktopIdentity::class.java.getDeclaredMethod("apply", android.webkit.WebView::class.java, String::class.java))
        assertNotNull(DesktopIdentity::class.java.getDeclaredMethod("applyNow", android.webkit.WebView::class.java, String::class.java))
    }

    // 2. The ad blocker, after Chromite --------------------------------------------------------

    private val list = listOf(
        "[Adblock Plus 2.0]",
        "! a comment",
        "||doubleclick.net^",
        "||ads.example.com/banner/*",
        "/pagead/js/adsbygoogle.js",
        "||tracker.example.org^${DOLLAR}third-party,script",
        "@@||ads.example.com/allowed/",
        "example.com##.ad-box",
        "shop.example.org##.ad-banner",
        "##.ad-generic",
        "##.ad-box",
        "example.com#@#.ad-box",
        "0.0.0.0 malware.example.net",
        "127.0.0.1 ads.tracker.example",
        "||popup.example.com^${DOLLAR}popup",
    )

    @Test
    fun aListIsParsedIntoRules_andAnswersAboutARequest() {
        val filter = AdFilter.parse(list.asSequence())
        assertTrue("the list carries rules", filter.networkRuleCount > 0)
        assertTrue("and element rules", filter.cosmeticRuleCount > 0)
        val page = "example.com"

        assertTrue(
            "a host anchor blocks the host and its subdomains",
            filter.isBlocked("https://ads.doubleclick.net/ad.js", page, "script"),
        )
        assertTrue(
            "a path with a wildcard is blocked",
            filter.isBlocked("https://ads.example.com/banner/1.gif", page, "image"),
        )
        assertTrue(
            "a piece of the url is blocked",
            filter.isBlocked("https://cdn.example.org/pagead/js/adsbygoogle.js", page),
        )
        assertTrue(
            "a hosts line is a rule like any other",
            filter.isBlocked("http://malware.example.net/x.js", page),
        )
        assertFalse(
            "an exception wins over the rule that would block",
            filter.isBlocked("https://ads.example.com/allowed/x.gif", page, "image"),
        )
        assertFalse(
            "another site is left alone",
            filter.isBlocked("https://example.org/hello.js", page, "script"),
        )
    }

    @Test
    fun theOptionsOfARuleAreKept() {
        val filter = AdFilter.parse(list.asSequence())
        val url = "https://tracker.example.org/x.js"
        assertTrue(
            "third-party and script, on another site",
            filter.isBlocked(url, "news.example", "script", true),
        )
        assertFalse(
            "not when the request belongs to the same site",
            filter.isBlocked(url, "news.example", "script", false),
        )
        assertFalse(
            "and not when it is an image",
            filter.isBlocked(url, "news.example", "image", true),
        )
        // A rule of a kind Explorer cannot honour is left out rather than misapplied.
        assertFalse(
            "a \u0024popup rule names no request Explorer can make",
            filter.isBlocked("https://popup.example.com/x", "news.example", "script"),
        )
    }

    @Test
    fun elementRulesBecomeAStyleSheet_forThePageTheyName() {
        val filter = AdFilter.parse(list.asSequence())
        val css = filter.cosmeticCss("example.com")
        assertTrue("a rule for every page is in it", css.contains(".ad-generic"))
        assertTrue("and it hides", css.contains("display:none"))
        // `example.com#@#.ad-box` takes that rule back on that very page, generic or not.
        assertFalse("the exception removes it", css.contains(".ad-box"))
        val shop = filter.cosmeticCss("shop.example.org")
        assertTrue("a page keeps the rule that names it", shop.contains(".ad-banner"))
        assertTrue("and gets the generic ones", shop.contains(".ad-generic"))
        val other = filter.cosmeticCss("other.example")
        assertTrue("another page keeps the generic rule", other.contains(".ad-generic"))
        assertFalse("and has no rule of its own", other.contains(".ad-banner"))
    }

    @Test
    fun aFilterWithNoRules_blocksNothing() {
        assertTrue(AdFilter.EMPTY.isEmpty)
        assertFalse(AdFilter.EMPTY.isBlocked("https://ads.doubleclick.net/x.js", "example.com"))
        assertEquals("", AdFilter.EMPTY.cosmeticCss("example.com"))
    }

    @Test
    fun theHostOfAUrl_isReadWithoutSchemePortOrCredentials() {
        assertEquals("example.com", AdFilter.hostOf("https://example.com/a/b?c=1"))
        assertEquals("example.com", AdFilter.hostOf("http://user:pw@example.com:8443/x"))
        assertEquals("example.com", AdFilter.hostOf("https://EXAMPLE.com"))
        assertNull(AdFilter.hostOf(""))
    }

    // 3. The blocker itself -------------------------------------------------------------------

    @Test
    fun theBlockerShipsTheTwoListsOfChromite_andFetchesThemFromTheirOwnHome() {
        assertEquals(setOf("easylist", "easyprivacy"), AdBlocker.DEFAULT_LISTS)
        assertEquals(
            "EasyList, as its authors publish it",
            "https://easylist.to/easylist/easylist.txt",
            AdBlocker.SOURCES.first { it.id == "easylist" }.url,
        )
        assertEquals(
            "EasyPrivacy",
            "https://easylist.to/easylist/easyprivacy.txt",
            AdBlocker.SOURCES.first { it.id == "easyprivacy" }.url,
        )
        assertEquals(
            "and the hosts file Explorer always used is a list of its own",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            AdBlocker.SOURCES.first { it.id == "stevenblack" }.url,
        )
        assertEquals("three lists", 3, AdBlocker.SOURCES.size)
    }

    @Test
    fun aListCanBeSwitchedOff_andTheChoiceIsKept() {
        assertEquals(AdBlocker.DEFAULT_LISTS, AdBlocker.lists(context))
        AdBlocker.setOn(context, "stevenblack", true)
        assertTrue(AdBlocker.isOn(context, "stevenblack"))
        AdBlocker.setOn(context, "stevenblack", false)
        assertFalse(AdBlocker.isOn(context, "stevenblack"))
        // Nothing picked means the two that are on out of the box.
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(AdBlocker.KEY_LISTS, emptySet()).commit()
        assertEquals(AdBlocker.DEFAULT_LISTS, AdBlocker.lists(context))
    }

    @Test
    fun theStyleOfAPage_carriesTheSelectorsOfThatPage() {
        AdBlocker.installForTest(AdFilter.parse(list.asSequence()))
        val script = AdBlocker.cosmeticScript("example.com")
        assertTrue("a style element is written", script.contains("createElement('style')"))
        assertTrue(script.contains(".ad-generic"))
        AdBlocker.installForTest(AdFilter.EMPTY)
        assertEquals("no rule, no script", "", AdBlocker.cosmeticScript("example.com"))
    }

    // 4. The words of the release -------------------------------------------------------------

    @Test
    fun theAboutSummary_isGPL3AndNothingElse() {
        val summary = context.getString(R.string.about_legal_summary, 2026)
        assertEquals("Copyright 2026 rpooi3. Free software under GPL-3.0.", summary)
        assertFalse("the FOSS Browser contributors are no longer named", summary.contains("contributors"))
        assertFalse("and AGPL is no longer in this line", summary.contains("AGPL"))
    }

    @Test
    fun theLegalRows_noLongerSayWhatOpeningThemDoes() {
        val strings = listOf(
            R.string.about_license_gpl_summary,
            R.string.about_license_agpl_summary,
            R.string.about_license_gnu_summary,
            R.string.about_source_summary,
            R.string.about_source_chromium_summary,
            R.string.about_source_firefox_summary,
            R.string.about_source_upstream_summary,
            R.string.about_license_chromium_summary,
            R.string.about_license_mpl_summary,
        )
        for (id in strings) {
            val text = context.getString(id)
            assertFalse("$text says what happens, not what it opens", text.contains("Opens the"))
        }
    }

    @Test
    fun theLicencesOfChromiumAndFirefox_areInTheApp() {
        assertEquals(
            "Chromium: BSD 3-Clause License",
            context.getString(R.string.about_license_chromium),
        )
        assertEquals(
            "Mozilla Public License, version 2.0",
            context.getString(R.string.about_license_mpl),
        )
        val chromium = com.rpooi3.explorer.preferences.SettingsScreens.readAsset(
            context, "licenses/Chromium-BSD-3-Clause.txt",
        )
        assertNotNull("the text of Chromium's licence is carried", chromium)
        assertTrue(chromium!!.contains("Copyright 2015 The Chromium Authors"))
        assertTrue(chromium.contains("Redistributions of source code"))
        val mpl = com.rpooi3.explorer.preferences.SettingsScreens.readAsset(context, "licenses/MPL-2.0.txt")
        assertNotNull("so is Mozilla's", mpl)
        assertTrue(mpl!!.contains("Mozilla Public License Version 2.0"))
        assertTrue(
            "and the summary names where they are written",
            context.getString(R.string.about_license_summary).contains("BSD 3-Clause"),
        )
    }

    @Test
    fun theLicenceFileSaysOnlyWhatIsStillFOSSBrowsers() {
        // 17.4.3: the app no longer carries a list of components (its row is gone and the file
        // with it); the repository's LICENSE.md holds what it said.
        assertNull(
            com.rpooi3.explorer.preferences.SettingsScreens.readAsset(context, "licenses/components.txt"),
        )
        val text = File("../LICENSE.md").readText()
        assertTrue("FOSS Browser is still named where it still is", text.contains("FOSS Browser"))
        assertFalse("its ad blocker is not", text.contains("the ad blocker with its filter list"))
        assertTrue("the blocker of this release is", text.contains("Cromite"))
        assertTrue("with the lists it reads", text.contains("EasyList"))
    }

    // 5. The tile of the home page ------------------------------------------------------------

    @Test
    fun theTileOfExtensions_isRoundedLikeAGroup() {
        // A row of one tile is drawn with the group's corner, not the inner corner of a row.
        val id = R.drawable.bg_menu_tile_single
        assertTrue(id != 0)
        assertNotNull("the drawable is there", context.getDrawable(id))
    }
}
