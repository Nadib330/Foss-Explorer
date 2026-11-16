package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rpooi3.explorer.R
import com.rpooi3.explorer.preferences.SettingsSections
import com.rpooi3.explorer.privacy.TrackerList
import com.rpooi3.explorer.privacy.TrackingAnalysis
import com.rpooi3.explorer.site.SiteCategory
import com.rpooi3.explorer.site.SiteSettings
import com.rpooi3.explorer.view.NinjaWebView
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
 * Release 15.2.1: the shield of the top bar is called xGuard and has a settings
 * screen of its own; the ad blocker's settings moved there; the tracker blocker
 * and the tracking analysis of pages are new; and the licences link to the
 * source code of Chromium and Firefox above FOSS Browser's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1521Test {
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

    // Settings screens -----------------------------------------------------------------

    private class Screen(val activity: androidx.appcompat.app.AppCompatActivity) {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.content_frame) as PreferenceFragmentCompat

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun pref(key: String): Preference? = fragment.findPreference(key)
        fun title(): String = activity.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).title.toString()
    }

    private fun <T> section(key: String, block: Screen.() -> T): T {
        val intent = Intent(context, Settings_Activity::class.java)
            .putExtra(Settings_Activity.EXTRA_SECTION, key)
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            val screen = Screen(controller.get())
            screen.idle()
            return screen.block()
        }
    }

    private fun firstScreen(): Screen {
        val intent = Intent(context, Settings_Activity::class.java)
        val controller = Robolectric.buildActivity(Settings_Activity::class.java, intent).setup()
        return Screen(controller.get()).also { it.idle() }
    }

    // The browser and the panel behind the shield --------------------------------------

    private class Browser(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val web: NinjaWebView get() = content.getChildAt(content.childCount - 1) as NinjaWebView

        fun idle() = shadowOf(Looper.getMainLooper()).idle()

        fun open(url: String) {
            web.loadUrl(url)
            activity.updateOmniBox()
            idle()
        }

        fun panel(): BottomSheetDialog {
            activity.findViewById<View>(R.id.button_shield).performClick()
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

    // 1. The settings tile ---------------------------------------------------------------

    @Test
    fun xGuard_isATopicOfTheFirstSettingsScreen_thatOpensAScreenOfItsOwn() {
        firstScreen().apply {
            val tile = pref("section_xguard")
            assertNotNull("the first screen has an xGuard tile", tile)
            assertEquals("xGuard", tile!!.title.toString())
            assertTrue("it shows its title only, like every topic", tile.summary.isNullOrEmpty())
            assertEquals("a topic of its own", R.xml.preference_section_xguard, SettingsSections.byKey("section_xguard")!!.xml)
        }
        section("section_xguard") {
            assertEquals("xGuard", title())
            assertNotNull("the protection switches are here", pref("xguard_block_ads"))
            assertNotNull("the filter lists are here now", pref("xguard_list_easylist"))
            assertNotNull("and the second of them", pref("xguard_list_easyprivacy"))
            assertNotNull("the tracker blocker is here", pref("xguard_tracking_blocker"))
            assertNotNull("the tracking analysis is here", pref("xguard_analysis_row"))
            // The topic is found by what its settings say, as every topic is.
            assertTrue(SettingsSections.keywords(activity, R.xml.preference_section_xguard).contains("Block known trackers"))
        }
    }

    // 2. The ad blocker's settings, moved ------------------------------------------------

    @Test
    fun theAdBlockersSettings_areOnTheXGuardScreen_andStillWork() {
        prefs.edit().putString("profile", "profileStandard").remove("profileStandard_adBlock").commit()
        section("section_xguard") {
            // 17.3.3: the single list became the blocker's own lists, switched one by one.
            val easylist = pref("xguard_list_easylist") as SwitchPreferenceCompat
            val easyprivacy = pref("xguard_list_easyprivacy") as SwitchPreferenceCompat
            val hosts = pref("xguard_list_stevenblack") as SwitchPreferenceCompat
            assertEquals("the first list is on out of the box", true, easylist.isChecked)
            assertEquals("so is the second", true, easyprivacy.isChecked)
            assertEquals("the hosts file is off until it is wanted", false, hosts.isChecked)

            val ads = pref("xguard_block_ads") as SwitchPreferenceCompat
            assertFalse("switched off until it is switched on", ads.isChecked)
            ads.performClick()
            idle()
            // The ad blocker keeps its choice per profile, the other way round: "ads allowed" is off.
            assertTrue("ad blocking is on for the profile in use", SiteSettings.isOn(prefs, SiteCategory.ADS))
            assertTrue(ads.isChecked)

            ads.performClick()
            idle()
            assertFalse(SiteSettings.isOn(prefs, SiteCategory.ADS))
            assertFalse(ads.isChecked)
        }
    }

    // 3. The tracker blocker -------------------------------------------------------------

    @Test
    fun xGuardKnowsTrackerHosts_andStopsTheKindsThatAreSwitchedOn() {
        // The list comes with the app, so it works before the first filter list download.
        assertTrue("xGuard ships a list", TrackerList.count(context) > 100)
        assertEquals(TrackerList.ANALYTICS, TrackerList.kindOf(context, "www.google-analytics.com"))
        assertEquals("a domain covers its subdomains", TrackerList.ADVERTISING, TrackerList.kindOf(context, "ad.doubleclick.net"))
        assertEquals(TrackerList.SOCIAL, TrackerList.kindOf(context, "connect.facebook.net"))
        assertEquals(TrackerList.FINGERPRINT, TrackerList.kindOf(context, "static.hotjar.com"))
        assertNull("a website is no tracker", TrackerList.kindOf(context, "example.org"))
        assertNull("a name that merely contains a tracker is no tracker", TrackerList.kindOf(context, "notdoubleclick.net.example.org"))

        // Analytics and advertising are stopped to begin with, social plugins are not.
        prefs.edit().clear().commit()
        assertTrue(TrackerList.isOn(prefs))
        assertTrue(TrackerList.blocks(context, prefs, "www.google-analytics.com"))
        assertTrue(TrackerList.blocks(context, prefs, "stats.doubleclick.net"))
        assertFalse("social plugins are left alone by default", TrackerList.blocks(context, prefs, "connect.facebook.net"))

        prefs.edit().putBoolean(TrackerList.KEY_BLOCK_SOCIAL, true).commit()
        assertTrue(TrackerList.blocks(context, prefs, "connect.facebook.net"))

        // The blocker as a whole goes first.
        prefs.edit().putBoolean(TrackerList.KEY_TRACKING_BLOCKER, false).commit()
        assertFalse(TrackerList.blocks(context, prefs, "www.google-analytics.com"))
        assertTrue("nothing is blocked then", TrackerList.blockedKinds(prefs).isEmpty())
    }

    @Test
    fun theTrackerSwitches_areOnTheXGuardScreen_andSayWhatTheyDo() {
        prefs.edit().clear().commit()
        section("section_xguard") {
            val blocker = pref("xguard_tracking_blocker") as SwitchPreferenceCompat
            assertTrue("on until it is switched off", blocker.isChecked)
            assertTrue("the summary names the hosts", blocker.summary.toString().contains("tracker hosts are known to xGuard"))

            blocker.performClick()
            idle()
            assertFalse(TrackerList.isOn(prefs))
            assertTrue(blocker.summary.toString().startsWith("Trackers load with the page"))

            val known = pref("xguard_trackers_known")!!
            assertTrue(known.summary.toString().contains("hosts:"))
            assertTrue("the kinds are counted", known.summary.toString().contains("analytics"))
            assertTrue(known.summary.toString().contains("advertising"))
        }
    }

    // 4. The tracking analysis -----------------------------------------------------------

    @Test
    fun theTrackingAnalysis_countsEveryRequest_ofAPage_andItsTrackers() = browser {
        open("https://www.example.org/article")
        val tab = web
        TrackingAnalysis.onPageStarted(tab)
        TrackingAnalysis.onRequest(tab, "https://www.example.org/app.js", false)
        TrackingAnalysis.onRequest(tab, "https://cdn.example.org/style.css", false)
        TrackingAnalysis.onRequest(tab, "https://www.google-analytics.com/collect", false)
        TrackingAnalysis.onTracker(tab, "www.google-analytics.com", TrackerList.ANALYTICS)
        TrackingAnalysis.onBlocked(tab)

        val report = TrackingAnalysis.report(tab)
        assertEquals("example.org", report.site)
        assertEquals(3, report.requests)
        assertEquals(3, report.hosts.size)
        assertEquals("one request went to another site", 1, report.thirdParty)
        assertEquals(1, report.trackers.size)
        assertEquals(TrackerList.ANALYTICS, report.trackers["www.google-analytics.com"])
        assertEquals(1, report.blocked)

        val text = TrackingAnalysis.pageText(activity, report)
        assertTrue(text, text.contains("Site: example.org"))
        assertTrue(text, text.contains("Requests: 3"))
        assertTrue(text, text.contains("Requests to other sites: 1"))
        assertTrue(text, text.contains("Trackers: 1"))
        assertTrue(text, text.contains("Stopped by xGuard: 1"))
        assertTrue("the kinds are named", text.contains("analytics 1"))
        assertTrue("the tracker is named as what it is", text.contains("(analytics)"))
    }

    @Test
    fun theAnalysis_isKeptForTheSettingsScreen_unlessTheTabIsPrivate() = browser {
        prefs.edit().clear().commit()
        open("https://www.example.org/article")
        val tab = web
        TrackingAnalysis.onPageStarted(tab)
        TrackingAnalysis.onRequest(tab, "https://www.google-analytics.com/collect", false)
        TrackingAnalysis.onTracker(tab, "www.google-analytics.com", TrackerList.ANALYTICS)
        TrackingAnalysis.onPageFinished(tab)

        assertTrue(TrackingAnalysis.hasLastReport(context))
        val summary = TrackingAnalysis.lastReportSummary(context)
        assertTrue(summary, summary.startsWith("example.org: 1 requests, 1 trackers"))
        val text = TrackingAnalysis.lastReportText(context)
        assertTrue(text, text.contains("Site: example.org"))
        assertTrue(text, text.contains("Busiest hosts:"))

        // What happens in a private tab stays there.
        TrackingAnalysis.clear(context)
        assertFalse(TrackingAnalysis.hasLastReport(context))
        assertEquals("nothing analysed yet", context.getString(R.string.xguard_analysis_none), TrackingAnalysis.lastReportSummary(context))
    }

    // 5. The panel behind the shield -----------------------------------------------------

    @Test
    fun thePanel_isCalledXGuard_andShowsTheTrackingAnalysisOfThePage() = browser {
        open("https://www.example.org/article")
        // A page that asked for something, so the analysis has something to say.
        val tab = web
        TrackingAnalysis.onPageStarted(tab)
        TrackingAnalysis.onRequest(tab, "https://www.google-analytics.com/collect", false)
        TrackingAnalysis.onTracker(tab, "www.google-analytics.com", TrackerList.ANALYTICS)
        TrackingAnalysis.onBlocked(tab)
        val panel = panel()
        assertEquals("xGuard", panel.findViewById<TextView>(R.id.protection_title)!!.text.toString())
        assertEquals(context.getString(R.string.xguard_panel_subtitle), context.getString(R.string.xguard_panel_subtitle))

        fun label(id: Int) = panel.findViewById<View>(id)!!.findViewById<TextView>(R.id.menu_row_label).text.toString()
        assertEquals("Tracking analysis of this page", label(R.id.protection_analysis))

        panel.findViewById<View>(R.id.protection_analysis)!!.performClick()
        idle()
        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertTrue(dialog.isShowing)
        val message = dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
        assertTrue(message, message.contains("Site: example.org"))
    }

    // 6. The licences --------------------------------------------------------------------

    @Test
    fun theLicences_putChromiumAndFirefoxAboveFossBrowser() {
        section("section_legal") {
            val keys = ArrayList<String>()
            fun visit(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    val pref = group.getPreference(i)
                    if (pref is androidx.preference.PreferenceGroup) visit(pref) else keys += pref.key
                }
            }
            visit(fragment.preferenceScreen)
            assertTrue(keys.contains("about_source_chromium"))
            assertTrue(keys.contains("about_source_firefox"))
            assertTrue(
                "above FOSS Browser's",
                keys.indexOf("about_source_chromium") < keys.indexOf("about_source_upstream"),
            )
            assertTrue(
                "Firefox comes after Chromium and before FOSS Browser",
                keys.indexOf("about_source_firefox") < keys.indexOf("about_source_upstream"),
            )
            // 16.1.0 (item 11): the rows say what they lead to instead of showing the address;
            // a tap opens the address.
            for (key in listOf("about_source_chromium", "about_source_firefox", "about_source_upstream")) {
                val row = pref(key)!!
                assertFalse("$key shows no address", row.summary.toString().contains("://"))
                row.performClick()
                assertTrue("$key opens the browser", shadowOf(activity as android.app.Activity).nextStartedActivity != null)
            }
            assertTrue(
                "Chromium's summary describes what it is",
                pref("about_source_chromium")!!.summary.toString().contains("Chromium"),
            )
            assertEquals("Chromium's source", "https://chromium.googlesource.com/chromium/src/", SettingsSections.URL_SOURCE_CHROMIUM)
            assertEquals("Firefox's source", "https://github.com/mozilla-firefox/firefox", SettingsSections.URL_SOURCE_FIREFOX)
            // FOSS Browser's link is still there, below them.
            assertEquals("https://codeberg.org/Gaukler_Faun/FOSS_Browser", SettingsSections.URL_SOURCE_UPSTREAM)
        }
    }
}
