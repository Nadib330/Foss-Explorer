package com.rpooi3.explorer.activity

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.rpooi3.explorer.R
import com.rpooi3.explorer.privacy.MarkedAds
import com.rpooi3.explorer.search.SearchEngines
import com.rpooi3.explorer.unit.BrowserUnit
import com.rpooi3.explorer.view.LinkContextMenu
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

/**
 * What 16.1.0 changed, item by item: the welcome of the first start, the marked ads, the
 * gestures that are gone and Chromium's own, the zoom entry, the search engine and the rows of
 * About and Legal information.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1610Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    // ------------------------------------------------ 1. and 3./4. the welcome

    @Test
    fun theFirstStart_opensTheWelcome_andTheDialogIsGone() {
        prefs.edit().remove("welcome_seen").commit()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, launch).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            val started = shadowOf(controller.get()).nextStartedActivity
            assertNotNull("the welcome is opened", started)
            assertEquals(WelcomeActivity::class.java.name, started.component!!.className)
            assertNull(
                "no dialog of the old first start",
                org.robolectric.shadows.ShadowDialog.getLatestDialog(),
            )
        }
    }

    @Test
    fun theSecondStart_doesNotOpenIt() {
        prefs.edit().putBoolean("welcome_seen", true).commit()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, launch).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            val started = shadowOf(controller.get()).nextStartedActivity
            assertNull("the welcome is shown once", started)
        }
    }

    @Test
    fun theWelcome_stepsThroughItsPages_andLeavesTheFlag() {
        prefs.edit().remove("welcome_seen").commit()
        Robolectric.buildActivity(WelcomeActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val pages = listOf(
                R.id.welcome_page_1, R.id.welcome_page_2, R.id.welcome_page_3, R.id.welcome_page_4,
            )
            fun visible(): List<Int> = pages.filter { activity.findViewById<View>(it).visibility == View.VISIBLE }

            assertEquals("one page at a time", listOf(R.id.welcome_page_1), visible())
            val next = activity.findViewById<MaterialButton>(R.id.welcome_next)
            val start = activity.findViewById<MaterialButton>(R.id.welcome_start)
            assertEquals(View.VISIBLE, next.visibility)
            assertEquals(View.GONE, start.visibility)

            repeat(3) {
                next.performClick()
                shadowOf(Looper.getMainLooper()).idle()
            }
            assertEquals("the last page", listOf(R.id.welcome_page_4), visible())
            assertEquals(View.GONE, next.visibility)
            assertEquals(View.VISIBLE, start.visibility)
            assertEquals("Start browsing", start.text.toString())

            // 18.0.0: the welcome ends by asking for the first profile, as Chrome's does. The
            // profile is named (or skipped) there, and only then is the welcome over.
            start.performClick()
            assertTrue("and does not come back", prefs.getBoolean("welcome_seen", false))
            assertFalse("the profile of the first start is asked for", activity.isFinishing)
            assertFalse(
                "and is asked for once",
                com.rpooi3.explorer.profile.Profiles.setupDone(activity),
            )
            // The welcome of the first start therefore stays open on the dialog that names the
            // profile; skipping it (or naming it) ends the welcome.
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull("one profile exists to be named", com.rpooi3.explorer.profile.Profiles.all(activity).firstOrNull())
            com.rpooi3.explorer.profile.Profiles.setSetupDone(activity)
        }
    }

    @Test
    fun theWelcome_hasASkip_andAPageOfDots() {
        prefs.edit().remove("welcome_seen").commit()
        Robolectric.buildActivity(WelcomeActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertEquals("Skip", activity.findViewById<MaterialButton>(R.id.welcome_skip).text.toString())
            val dots = activity.findViewById<ViewGroup>(R.id.welcome_dots)
            assertEquals("one dot per page", 4, dots.childCount)
            assertEquals("the first one is the current", 1f, dots.getChildAt(0).alpha, 0f)
            assertEquals(0.38f, dots.getChildAt(1).alpha, 0f)
        }
    }

    // ------------------------------------------------------- 2. marked ads

    @Test
    fun aMarkedAd_isKept_stopsItsRequests_andIsHidden() {
        MarkedAds.remove(context, "ads.example.com/banner.js")
        assertNull("nothing marked yet", MarkedAds.list(context).firstOrNull { it.contains("ads.example.com") })

        assertTrue(MarkedAds.add(context, "https://ads.example.com/banner.js"))
        assertFalse("a second time changes nothing", MarkedAds.add(context, "ads.example.com/banner.js"))
        assertTrue(MarkedAds.isMarked(context, "https://ads.example.com/banner.js"))

        assertTrue("the ad itself", MarkedAds.blocks(context, "https://ads.example.com/banner.js"))
        assertTrue("and what lies below it", MarkedAds.blocks(context, "https://ads.example.com/banner.js?v=2"))
        assertFalse("but not the rest of the site", MarkedAds.blocks(context, "https://example.com/"))

        val script = MarkedAds.hideScript(context)
        assertTrue("the script names the mark", script.contains("ads.example.com/banner.js"))
        assertTrue("and hides the elements", script.contains("display"))

        MarkedAds.remove(context, "https://ads.example.com/banner.js")
        assertFalse(MarkedAds.blocks(context, "https://ads.example.com/banner.js"))
    }

    @Test
    fun aBareHost_markStopsTheWholeHost() {
        MarkedAds.remove(context, "tracker.example")
        assertTrue(MarkedAds.add(context, "tracker.example"))
        assertTrue(MarkedAds.blocks(context, "https://tracker.example/t.gif"))
        assertTrue("a subdomain as well", MarkedAds.blocks(context, "https://a.tracker.example/x.js"))
        assertFalse(MarkedAds.blocks(context, "https://nottracker.example/x.js"))
        MarkedAds.remove(context, "tracker.example")
    }

    @Test
    fun theLongPressMenu_offersTheMark_andTheSettingsListIt() {
        val link = LinkContextMenu.Target("https://ads.example/ad", "An ad", null)
        assertTrue(LinkContextMenu.Item.MARK_AS_AD in LinkContextMenu.groups(link, privateTab = false).last())
        assertEquals(
            "Mark as ad and hide",
            context.getString(LinkContextMenu.Item.MARK_AS_AD.label),
        )

        settings("section_xguard") { fragment ->
            val row = fragment.findPreference<Preference>("section_marked_ads")
            assertNotNull("xGuard has the row of the marked ads", row)
            assertEquals("Marked ads", row!!.title.toString())
        }
        settings("section_marked_ads") { fragment ->
            assertNotNull("and a screen of its own", fragment.findPreference<Preference>("marked_ads_add"))
        }
    }

    // ------------------------------------------------- 12./13./14. the gestures

    @Test
    fun theGestureSettings_areGone() {
        settings("section_appearance") { fragment ->
            assertNull(fragment.findPreference<Preference>("settings_gesture"))
        }
        assertNull("no gesture screen is registered", com.rpooi3.explorer.preferences.SettingsSections.byKey("gestures"))
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, launch).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            // The defaults the old first start wrote are not written any more.
            assertFalse(prefs.contains("setting_gesture_tb_up"))
            assertNotNull("the bars still work", activity.findViewById<View>(R.id.button_tabs))
        }
    }

    // -------------------------------------------------------------- 16. the engine

    @Test
    fun aNewInstallation_searchesWithGoogle() {
        prefs.edit().remove(SearchEngines.KEY_ENGINE).putBoolean(SearchEngines.KEY_CUSTOM_ON, false).commit()
        assertEquals("google", SearchEngines.selected(prefs))
        assertEquals("https://www.google.com/search?q=wizard", BrowserUnit.queryWrapper(context, "wizard"))
    }

    // --------------------------------------------------------- 1./18./21./22. About

    @Test
    fun help_hasTheFeedbackRow_underTheDeveloperAddress() {
        // 16.1.0 (item 1) put the row in About; 17.3.2 (item 2) moved it to Help and support,
        // right under the developer's address.
        settings("section_help") { fragment ->
            val order = mutableListOf<String>()
            val category = fragment.findPreference<androidx.preference.Preference>("help_email")
            val parent = category?.parent
            assertNotNull(parent)
            for (index in 0 until (parent as androidx.preference.PreferenceGroup).preferenceCount) {
                order += (parent.getPreference(index).key ?: "")
            }
            assertTrue("the address comes first", order.indexOf("help_email") >= 0)
            assertEquals("the feedback row is right under it", "help_feedback", order[order.indexOf("help_email") + 1])

            val feedback = fragment.findPreference<Preference>("help_feedback")
            assertNotNull("the feedback row", feedback)
            assertEquals("Send feedback", feedback!!.title.toString())
            assertEquals(
                "Submit feedback by creating a new issue on GitHub",
                feedback.summary.toString(),
            )
            feedback.performClick()
            val activity = fragment.activity as android.app.Activity
            assertEquals(
                "https://github.com/nadib330/Foss-Explorer/issues/new",
                shadowOf(activity).nextStartedActivity.dataString,
            )
        }
    }

    @Test
    fun legalInformation_showsNoAddresses_andOpensThem() {
        settings("section_legal") { fragment ->
            for (key in listOf("about_source", "about_source_chromium", "about_source_firefox", "about_source_upstream")) {
                val row = fragment.findPreference<Preference>(key)!!
                assertFalse("$key shows no address", row.summary.toString().contains("://"))
                assertFalse("$key is no address either", row.title.toString().contains("://"))
            }
            assertNull("the fork card is gone", fragment.findPreference<Preference>("about_fork"))
        }
        assertEquals("https://github.com/nadib330/Foss-Explorer/", com.rpooi3.explorer.preferences.SettingsSections.URL_SOURCE)
        assertEquals("Explorer", context.getString(R.string.app_name))
    }

    @Test
    fun thePrivateWording_isTheOneOfFirefox_andExplorer() {
        assertEquals("You’ve gone private browsing mode", context.getString(R.string.cr_incognito_ntp_title))
        assertEquals("Search your private tabs", context.getString(R.string.cr_hub_search_hint_incognito))
        assertEquals("Open in private tab", context.getString(R.string.cr_contextmenu_open_in_incognito_tab))
    }

    // ------------------------------------------------------------------ helpers

    private fun settings(key: String, block: (PreferenceFragmentCompat) -> Unit) {
        val intent = Intent(context, Settings_Activity::class.java)
            .putExtra(Settings_Activity.EXTRA_SECTION, key)
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            val fragment = controller.get()
                .supportFragmentManager.findFragmentById(R.id.content_frame) as PreferenceFragmentCompat
            block(fragment)
        }
    }
}
