package com.rpooi3.explorer.preferences

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.slider.Slider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.accessibility.PageZoom
import com.rpooi3.explorer.activity.Settings_Activity
import com.rpooi3.explorer.activity.Settings_Clear
import com.rpooi3.explorer.privacy.IncognitoLock
import com.rpooi3.explorer.privacy.PrivacySettings
import com.rpooi3.explorer.search.SearchEngines
import com.rpooi3.explorer.tabs.InactiveTabs
import com.rpooi3.explorer.unit.BrowserUnit
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
import java.io.File

/**
 * Release 13.2.2, the settings screens: Privacy and security, Tabs and
 * groups, Accessibility, Search engine and About, each laid out after
 * Chromium's screen of that name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1322SettingsTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private class Screen(val activity: AppCompatActivity) {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.content_frame) as PreferenceFragmentCompat
        val title: String get() = activity.findViewById<Toolbar>(R.id.toolbar).title.toString()

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun pref(key: String): Preference = fragment.findPreference(key)!!
        fun radio(key: String): RadioRowPreference = fragment.findPreference(key)!!
        fun switch(key: String): SwitchPreferenceCompat = fragment.findPreference(key)!!
        fun groups(): List<PreferenceCategory> = (0 until fragment.preferenceScreen.preferenceCount)
            .map { fragment.preferenceScreen.getPreference(it) }.filterIsInstance<PreferenceCategory>()

        fun PreferenceGroup.titles(): List<String> = (0 until preferenceCount).map { getPreference(it).title.toString() }

        /** Everything on the screen that is not a group, in order. */
        fun all(): List<Preference> {
            val result = ArrayList<Preference>()
            fun visit(group: PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    val pref = group.getPreference(i)
                    if (pref is PreferenceGroup) visit(pref) else result += pref
                }
            }
            visit(fragment.preferenceScreen)
            return result
        }

        /** The row of [key] as the list draws it. */
        fun row(key: String): View {
            val adapter = fragment.listView.adapter!!
            val position = (adapter as androidx.preference.PreferenceGroup.PreferencePositionCallback)
                .getPreferenceAdapterPosition(key)
            val holder = adapter.createViewHolder(fragment.listView, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            return holder.itemView
        }

        fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog
        fun AlertDialog.title(): String = findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()
    }

    private fun <T> section(key: String?, block: Screen.() -> T): T {
        val intent = Intent(context, Settings_Activity::class.java)
        if (key != null) intent.putExtra(Settings_Activity.EXTRA_SECTION, key)
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            return Screen(controller.get()).run {
                val result = block()
                idle()
                result
            }
        }
    }

    private fun secureDevice(secure: Boolean) {
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceSecure(secure)
    }

    // ------------------------------------------------- 1. Privacy and security

    @Test
    fun privacyAndSecurity_hasChromiumsTwoGroups_andNothingOfBefore() = section("section_privacy") {
        assertEquals("Privacy and security", title)
        val groups = groups()
        assertEquals(listOf("Privacy", "Security"), groups.map { it.title.toString() })
        assertEquals(
            listOf("Delete browsing data", "Preload pages", "Lock private tabs when you leave Explorer"),
            groups[0].titles(),
        )
        // 16.1.0 (item 9): "JavaScript optimization & security" is gone.
        assertEquals(listOf("Safe Browsing"), groups[1].titles())
        assertEquals("Delete history, cookies, site data, cache…", pref("settings_clear").summary.toString())
        // What stood here before 13.2.2 is not here any more.
        assertNull(fragment.findPreference<Preference>("settings_profile"))
        assertNull(fragment.findPreference<Preference>("edit_standard"))
        assertEquals(4, all().size)

        pref("settings_clear").performClick()
        assertEquals(Settings_Clear::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
    }

    @Test
    fun theRowsOfPrivacyAndSecurity_sayWhatIsChosen() {
        section("section_privacy") {
            assertEquals("No preloading", pref("section_preload").summary.toString())
            assertEquals("Standard protection is on", pref("section_safe_browsing").summary.toString())
        }
        prefs.edit()
            .putString(PrivacySettings.KEY_PRELOAD, PrivacySettings.PRELOAD_STANDARD)
            .putString(PrivacySettings.KEY_SAFE_BROWSING, PrivacySettings.SAFE_BROWSING_OFF)
            .commit()
        section("section_privacy") {
            assertEquals("Standard preloading", pref("section_preload").summary.toString())
            assertEquals("Safe Browsing is off", pref("section_safe_browsing").summary.toString())
        }
    }

    @Test
    fun preloadPages_offersNoPreloadingAndStandardPreloading() = section("section_preload") {
        assertEquals("Preload pages", title)
        assertEquals(
            "You can browse and search faster when Explorer preloads pages that it thinks you might visit",
            pref("preload_description").summary.toString(),
        )
        assertEquals("No preloading", radio("preload_none").title.toString())
        assertEquals("Pages load only after you open them", radio("preload_none").summary.toString())
        assertEquals("Standard preloading", radio("preload_standard").title.toString())
        assertTrue("nothing was preloaded before this setting existed", radio("preload_none").isChecked)
        assertEquals(PrivacySettings.PRELOAD_NONE, PrivacySettings.preload(prefs))
        // Robolectric's WebView cannot preload, as an old Android System WebView cannot.
        assertFalse(PrivacySettings.isPreloadSupported())
        assertFalse("a choice the engine cannot honour is not offered", radio("preload_standard").isEnabled)
        assertTrue(pref("preload_note").isVisible)
    }

    @Test
    fun safeBrowsing_hasStandardAndNoProtection_andAsksBeforeItIsSwitchedOff() = section("section_safe_browsing") {
        assertEquals("Safe Browsing", title)
        assertEquals(
            "only these two levels, as asked",
            listOf("Standard protection", "No protection (not recommended)"),
            all().filterIsInstance<RadioRowPreference>().map { it.title.toString() },
        )
        assertTrue(radio("safe_browsing_standard").isChecked)
        assertTrue(PrivacySettings.isSafeBrowsingOn(prefs))

        radio("safe_browsing_off").performClick()
        idle()
        val dialog = latestDialog()
        assertEquals("Turn off Safe Browsing?", dialog.title())
        assertEquals("Turn off", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        assertTrue("nothing changes before the answer", PrivacySettings.isSafeBrowsingOn(prefs))
        assertTrue(radio("safe_browsing_standard").isChecked)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertTrue("Cancel keeps the protection", PrivacySettings.isSafeBrowsingOn(prefs))

        radio("safe_browsing_off").performClick()
        idle()
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertFalse(PrivacySettings.isSafeBrowsingOn(prefs))
        assertTrue(radio("safe_browsing_off").isChecked)
        assertFalse(radio("safe_browsing_standard").isChecked)

        radio("safe_browsing_standard").performClick()
        idle()
        assertTrue("switching it on needs no question", PrivacySettings.isSafeBrowsingOn(prefs))
    }

    @Test
    fun safeBrowsing_reachesTheEngine() {
        // Robolectric's settings forget this value; these remember it.
        val settings = object : org.robolectric.fakes.RoboWebSettings() {
            private var safe = false
            override fun setSafeBrowsingEnabled(enabled: Boolean) {
                safe = enabled
            }
            override fun getSafeBrowsingEnabled(): Boolean = safe
        }
        PrivacySettings.apply(prefs, settings)
        assertTrue(settings.safeBrowsingEnabled)
        prefs.edit().putString(PrivacySettings.KEY_SAFE_BROWSING, PrivacySettings.SAFE_BROWSING_OFF).commit()
        PrivacySettings.apply(prefs, settings)
        assertFalse(settings.safeBrowsingEnabled)
    }

    @Test
    fun javaScriptOptimization_isGone() {
        // 16.1.0 (item 9): the topic left the settings, and with it its screen.
        assertNull(SettingsSections.byKey("section_js_optimizer"))
        section("section_privacy") {
            assertNull(fragment.findPreference<Preference>("section_js_optimizer"))
        }
    }

    @Test
    fun theIncognitoLock_needsAScreenLock() {
        secureDevice(false)
        section("section_privacy") {
            val lock = switch(IncognitoLock.KEY)
            assertEquals("Turn on screen lock in Android settings", lock.summary.toString())
            assertFalse(lock.isChecked)
            lock.performClick()
            idle()
            assertFalse("cannot be switched on without a screen lock", lock.isChecked)
            assertFalse(IncognitoLock.isEnabled(context))
            assertEquals(Settings.ACTION_SECURITY_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        }
        secureDevice(true)
        section("section_privacy") {
            val lock = switch(IncognitoLock.KEY)
            assertEquals("Use screen lock to see open private tabs", lock.summary.toString())
            lock.performClick()
            idle()
            assertTrue(lock.isChecked)
            assertTrue(IncognitoLock.isEnabled(context))
        }
        // The screen lock was removed afterwards: the stored switch alone does not count.
        secureDevice(false)
        assertFalse(IncognitoLock.isEnabled(context))
    }

    // ----------------------------------------------------- 2. Tabs and groups

    @Test
    fun tabsAndGroups_replacesTheOldTabsSettings() {
        section(null) {
            assertEquals("Tabs and groups", pref("section_tabs").title.toString())
        }
        section("section_tabs") {
            assertEquals("Tabs and groups", title)
            assertEquals(listOf("section_inactive_tabs"), all().map { it.key })
            assertEquals("Move to inactive section", pref("section_inactive_tabs").title.toString())
            assertEquals("Chrome's default", "After 21 days inactive", pref("section_inactive_tabs").summary.toString())
        }
    }

    @Test
    fun inactive_choosesWhenTabsMove() = section("section_inactive_tabs") {
        assertEquals("Inactive", title)
        assertEquals(
            listOf("Never", "After 7 days inactive", "After 14 days inactive", "After 21 days inactive"),
            all().filterIsInstance<RadioRowPreference>().map { it.title.toString() },
        )
        assertTrue(radio("inactive_21").isChecked)
        radio("inactive_7").performClick()
        assertEquals(7, InactiveTabs.days(prefs))
        assertTrue(radio("inactive_7").isChecked)
        assertFalse(radio("inactive_21").isChecked)
        radio("inactive_never").performClick()
        assertEquals(0, InactiveTabs.days(prefs))

        assertEquals("Archive duplicate tabs", switch(InactiveTabs.KEY_DUPLICATES).title.toString())
        assertFalse(switch(InactiveTabs.KEY_DUPLICATES).isChecked)
        assertEquals("Automatically close inactive items", switch(InactiveTabs.KEY_AUTO_CLOSE).title.toString())
        assertTrue(switch(InactiveTabs.KEY_AUTO_CLOSE).isChecked)
        assertEquals("Inactive tabs are closed after 60 days", switch(InactiveTabs.KEY_AUTO_CLOSE).summary.toString())
    }

    // ------------------------------------------------------- 3. Accessibility

    @Test
    fun accessibility_standsBelowAppearance() = section(null) {
        val keys = all().map { it.key }
        assertEquals(keys.indexOf("section_appearance") + 1, keys.indexOf("section_accessibility"))
        assertEquals("Accessibility", pref("section_accessibility").title.toString())
    }

    @Test
    fun accessibility_hasEverySettingOfChromium_inItsOrder() = section("section_accessibility") {
        assertEquals("Accessibility", title)
        assertEquals(
            listOf(
                "page_zoom_default_zoom", "section_saved_zoom", "page_zoom_include_os_adjustment",
                "force_enable_zoom", "reader_for_accessibility", "reader_enable_links", "captions",
                "touchpad_overscroll_history_navigation",
            ),
            all().map { it.key },
        )
        assertEquals(
            listOf(
                "Saved zoom for sites", "Match Android font size", "Force enable zoom",
                "Simplified view for web pages", "Show hyperlinks", "Captions",
                "Swipe between pages using a touchpad",
            ),
            all().drop(1).map { it.title.toString() },
        )
        assertEquals("Override a website’s request to prevent zooming in", pref("force_enable_zoom").summary.toString())
        assertEquals(
            "Get notified when an article can be shown in simplified view",
            pref("reader_for_accessibility").summary.toString(),
        )
        // Switches where Chromium has switches, plain rows where it has rows.
        for (key in listOf(
            "page_zoom_include_os_adjustment", "force_enable_zoom", "reader_for_accessibility",
            "reader_enable_links", "touchpad_overscroll_history_navigation",
        )) {
            assertTrue(key, pref(key) is SwitchPreferenceCompat)
        }
        assertEquals(Preference::class.java, pref("captions").javaClass)
        // 16.1.0 (items 10 and 15): the zoom switch, the text cursor and the image descriptions
        // are gone from this screen.
        for (gone in listOf("page_zoom_always_show", "caret_browsing", "image_descriptions")) {
            assertNull(gone, fragment.findPreference<Preference>(gone))
        }

        pref("captions").performClick()
        assertEquals(Settings.ACTION_CAPTIONING_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun showHyperlinks_belongsToTheSimplifiedView() = section("section_accessibility") {
        assertFalse(switch("reader_for_accessibility").isChecked)
        assertFalse(pref("reader_enable_links").isEnabled)
        switch("reader_for_accessibility").performClick()
        idle()
        assertTrue(pref("reader_enable_links").isEnabled)
    }

    @Test
    fun defaultZoom_isChromiumsCard_valueMinusSliderPlus_andAPreview() = section("section_accessibility") {
        val row = row("page_zoom_default_zoom")
        val value = row.findViewById<TextView>(R.id.page_zoom_current_value_text)
        val slider = row.findViewById<Slider>(R.id.page_zoom_slider)
        assertEquals("100 %", value.text.toString())
        assertEquals(50f, slider.valueFrom)
        assertEquals(300f, slider.valueTo)
        assertEquals(100f, slider.value)
        assertNotNull(row.findViewById<View>(R.id.page_zoom_decrease_zoom_button))
        assertNotNull(row.findViewById<View>(R.id.page_zoom_increase_zoom_button))
        assertEquals("The Wonderful Wizard of Oz", row.findViewById<TextView>(R.id.page_zoom_preview_large_text).text.toString())
        val small = row.findViewById<TextView>(R.id.page_zoom_preview_small_text).textSize

        row.findViewById<View>(R.id.page_zoom_increase_zoom_button).performClick()
        idle()
        assertEquals("the next of Chromium's steps", 110, PageZoom.defaultZoom(prefs))
        val after = row("page_zoom_default_zoom")
        assertEquals("110 %", after.findViewById<TextView>(R.id.page_zoom_current_value_text).text.toString())
        assertTrue("the preview grows", after.findViewById<TextView>(R.id.page_zoom_preview_small_text).textSize > small)

        after.findViewById<View>(R.id.page_zoom_decrease_zoom_button).performClick()
        after.findViewById<View>(R.id.page_zoom_decrease_zoom_button).performClick()
        idle()
        assertEquals(100, PageZoom.defaultZoom(prefs))
    }

    @Test
    fun zoomSteps_areChromiums_andStayWithinFiftyAndThreeHundredPercent() {
        assertEquals(110, PageZoomPreference.stepUp(100))
        assertEquals(90, PageZoomPreference.stepDown(100))
        assertEquals(125, PageZoomPreference.stepUp(117))
        assertEquals(300, PageZoomPreference.stepUp(300))
        assertEquals(50, PageZoomPreference.stepDown(50))
        PageZoom.setDefaultZoom(prefs, 1000)
        assertEquals(300, PageZoom.defaultZoom(prefs))
    }

    @Test
    fun theFontSizeOfEarlierVersions_becomesTheDefaultZoom() {
        prefs.edit().putString("sp_fontSize", "150").commit()
        assertEquals(150, PageZoom.defaultZoom(prefs))
        section("section_appearance") {
            assertNull("the font size left Appearance", fragment.findPreference<Preference>("sp_fontSize"))
        }
        PageZoom.setDefaultZoom(prefs, 90)
        assertEquals("the new setting wins once it is used", 90, PageZoom.defaultZoom(prefs))
    }

    @Test
    fun aSiteCanHaveAZoomOfItsOwn_andMatchAndroidFontSizeMultipliesIn() {
        assertEquals(100, PageZoom.zoomFor(prefs, "https://www.example.org/page"))
        PageZoom.setSiteZoom(prefs, "https://www.example.org/page", 150)
        assertEquals(150, PageZoom.zoomFor(prefs, "https://example.org/other"))
        assertEquals("other sites keep the default", 100, PageZoom.zoomFor(prefs, "https://example.com/"))
        assertEquals(mapOf("example.org" to 150), PageZoom.siteZooms(prefs))
        // Back at the default, the site is not listed any more.
        PageZoom.setSiteZoom(prefs, "https://example.org/", 100)
        assertTrue(PageZoom.siteZooms(prefs).isEmpty())

        assertEquals(150, PageZoom.textZoom(context, prefs, 150))
        prefs.edit().putBoolean(PageZoom.KEY_OS_ADJUSTMENT, true).commit()
        val scaled = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).apply { fontScale = 1.3f },
        )
        assertEquals(195, PageZoom.textZoom(scaled, prefs, 150))
    }

    @Test
    fun savedZoomForSites_listsTheSites_andRemovesThem() {
        section("section_saved_zoom") {
            assertEquals("Saved zoom for sites", title)
            assertEquals(
                "Sites that you save a custom zoom level for will appear here",
                pref("saved_zoom_empty").summary.toString(),
            )
        }
        PageZoom.setSiteZoom(prefs, "news.example", 125)
        PageZoom.setSiteZoom(prefs, "blog.example", 175)
        section("section_saved_zoom") {
            assertEquals(listOf("blog.example", "news.example", "Delete all"), all().map { it.title.toString() })
            assertEquals("175 %", pref("saved_zoom:blog.example").summary.toString())

            pref("saved_zoom:blog.example").performClick()
            idle()
            assertNull(PageZoom.siteZoom(prefs, "blog.example"))
            assertNull(fragment.findPreference<Preference>("saved_zoom:blog.example"))

            pref("saved_zoom_clear").performClick()
            idle()
            latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertTrue(PageZoom.siteZooms(prefs).isEmpty())
            assertNotNull(fragment.findPreference<Preference>("saved_zoom_empty"))
        }
    }

    // ------------------------------------------------------- 4. Search engine

    @Test
    fun searchEngine_isOneCardOfLogoNameDomainAndRadioButton() = section("section_search") {
        assertEquals("Search engine", title)
        val main = groups()[0]
        assertNull("the card has no heading", main.title)
        assertEquals(
            listOf("Google", "Brave", "Microsoft Bing", "DuckDuckGo", "Ecosia", "Startpage"),
            main.titles(),
        )
        assertEquals(
            listOf("google.com", "search.brave.com", "bing.com", "duckduckgo.com", "ecosia.org", "startpage.com"),
            (0 until main.preferenceCount).map { main.getPreference(it).summary.toString() },
        )
        for (i in 0 until main.preferenceCount) {
            val engine = main.getPreference(i) as RadioRowPreference
            val row = row(engine.key)
            val logo = row.findViewById<ImageView>(android.R.id.icon)
            assertTrue("${engine.title}: its own round logo", logo.drawable is android.graphics.drawable.BitmapDrawable)
            assertEquals("${engine.title}: 40dp, as in Chrome", 120, logo.layoutParams.width)
            assertNull("${engine.title}: the logo keeps its colours", logo.imageTintList)
            assertEquals("${engine.title}: radio button at the end", View.VISIBLE, row.findViewById<View>(R.id.radio_end).visibility)
            assertEquals(View.GONE, row.findViewById<View>(R.id.radio_start).visibility)
            assertEquals(engine.isChecked, row.findViewById<RadioButton>(R.id.radio_end).isChecked)
        }
        assertEquals(
            // 16.1.0 (item 17): "Startpage (Deutsch)" is gone; the other engines keep their place.
            "the engines Explorer had before are still there, without the German Startpage",
            listOf("Qwant", "Mojeek", "Searx", "Custom search engine"),
            groups()[1].titles(),
        )
    }

    @Test
    fun choosingAnEngine_changesWhereSearchesGo() = section("section_search") {
        // 16.1.0 (item 16): Google is the engine of a new installation.
        assertTrue("Google is the default", radio("engine_google").isChecked)
        assertEquals(1, all().filterIsInstance<RadioRowPreference>().count { it.isChecked })

        val expected = mapOf(
            "engine_google" to "https://www.google.com/search?q=wizard",
            "engine_brave" to "https://search.brave.com/search?q=wizard",
            "engine_bing" to "https://www.bing.com/search?q=wizard",
            "engine_duckduckgo" to "https://duckduckgo.com/?q=wizard",
            "engine_ecosia" to "https://www.ecosia.org/search?q=wizard",
            "engine_startpage" to "https://startpage.com/do/search?query=wizard",
            "engine_qwant" to "https://www.qwant.com/?q=wizard",
            "engine_mojeek" to "https://www.mojeek.com/search?q=wizard",
        )
        for ((key, url) in expected) {
            radio(key).performClick()
            assertTrue(key, radio(key).isChecked)
            assertEquals(key, 1, all().filterIsInstance<RadioRowPreference>().count { it.isChecked })
            assertEquals(key, url, BrowserUnit.queryWrapper(context, "wizard"))
        }
    }

    @Test
    fun enginesChosenInEarlierVersions_stayChosen() {
        for ((stored, key) in mapOf(
            "0" to "startpage", "4" to "duckduckgo", "6" to "searx", "7" to "qwant", "8" to "ecosia",
            "9" to "google", "10" to "mojeek", "11" to "brave", "12" to "bing",
        )) {
            prefs.edit().putString(SearchEngines.KEY_ENGINE, stored).putBoolean(SearchEngines.KEY_CUSTOM_ON, false).commit()
            assertEquals(stored, key, SearchEngines.selected(prefs))
        }
        // 16.1.0 (item 17): the German Startpage is gone from the list; a stored "1" searches with
        // Google rather than with nothing.
        prefs.edit().putString(SearchEngines.KEY_ENGINE, "1").commit()
        assertEquals("a stored Startpage (Deutsch) falls back", "google", SearchEngines.selected(prefs))
        // A search address of the user's own, set before the update.
        prefs.edit().putBoolean(SearchEngines.KEY_CUSTOM_ON, true)
            .putString(SearchEngines.KEY_CUSTOM_URL, "https://search.example/?q=").commit()
        section("section_search") {
            assertTrue(radio("engine_custom").isChecked)
            assertEquals("https://search.example/?q=", radio("engine_custom").summary.toString())
            assertEquals("https://search.example/?q=wizard", BrowserUnit.queryWrapper(context, "wizard"))

            radio("engine_google").performClick()
            assertFalse(radio("engine_custom").isChecked)
            assertEquals("https://www.google.com/search?q=wizard", BrowserUnit.queryWrapper(context, "wizard"))
        }
    }

    @Test
    fun aCustomSearchEngine_asksForItsAddress() = section("section_search") {
        radio("engine_custom").performClick()
        idle()
        val dialog = latestDialog()
        assertEquals("Custom search engine", dialog.title())
        assertFalse("not chosen before an address is given", radio("engine_custom").isChecked)
        dialog.findViewById<TextView>(R.id.textInput)!!.text = "https://find.example/s?q="
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        assertTrue(radio("engine_custom").isChecked)
        assertFalse(radio("engine_startpage").isChecked)
        assertEquals("https://find.example/s?q=wizard", BrowserUnit.queryWrapper(context, "wizard"))

        assertFalse("not an address", SearchEngines.selectCustom(prefs, "find.example"))
    }

    // ---------------------------------------------------------------- 5. About

    @Test
    fun about_isThreeCardsLikeAboutChrome_andDeveloperBelow() = section("section_about") {
        assertEquals("About Explorer", title)
        val groups = groups()
        // 16.1.0 (item 1): the feedback row is a card of its own between Legal information and
        // the developer.
        assertEquals("every tile is a card of its own", listOf(1, 1, 1, 1, 1), groups.map { it.preferenceCount })
        assertEquals(
            listOf("Application version", "Operating system", "Legal information", "Send feedback", "Developer"),
            all().map { it.title.toString() },
        )
        assertEquals("Explorer " + SettingsSections.versionName(activity), pref("about_app").summary.toString())
        assertEquals(
            "Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID}",
            pref("about_os").summary.toString(),
        )
        assertTrue(pref("section_legal").summary.toString().startsWith("Copyright 2026 rpooi3"))
        assertEquals("rpooi3", pref("section_developer").summary.toString())
        assertFalse(pref("about_app").isSelectable)
        assertFalse(pref("about_os").isSelectable)
    }

    @Test
    fun legalInformation_holdsTheLicensesTheComponentsAndTheSources() = section("section_legal") {
        assertEquals("Legal information", title)
        assertEquals(
            listOf(
                "Open source license", "GNU General Public License, version 3",
                "GNU Affero General Public License, version 3", "Open source components",
                // 15.2.1: Chromium and Firefox are named above FOSS Browser's source.
                // 16.1.0: the card "Code from FOSS Browser" is gone (item 21), and the app calls
                // itself Explorer (item 22).
                "Source code of Explorer",
                "Source code of Chromium", "Source code of Firefox", "Source code of FOSS Browser",
            ),
            all().map { it.title.toString() },
        )
        val license = pref("about_license").summary.toString()
        assertTrue(license, license.contains("GNU General Public License, version 3"))
        assertTrue(license, license.contains("Affero General Public License, version 3 or later"))

        fun text(key: String): String {
            pref(key).performClick()
            idle()
            return latestDialog().findViewById<TextView>(android.R.id.message)!!.text.toString()
        }
        assertTrue(text("about_license_gpl").contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(text("about_license_agpl").contains("GNU AFFERO GENERAL PUBLIC LICENSE"))
        val components = text("about_third_party")
        for (part in listOf(
            "FOSS Browser", "Chromium: offline page and dinosaur game", "Chromium: settings, context menu, tabs and Incognito page",
            "Readability", "Material Components for Android", "The Chromium Authors",
            "Redistribution and use in source and binary forms", "Apache License",
        )) {
            assertTrue(part, components.contains(part))
        }

        mapOf(
            // 16.1.0 (item 18): the source of Explorer is on GitHub.
            "about_source" to "https://github.com/nadib330/Foss-Explorer/",
            "about_source_upstream" to "https://codeberg.org/Gaukler_Faun/FOSS_Browser",
        ).forEach { (key, url) ->
            pref(key).performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(key, url, intent.dataString)
            assertEquals("$key opens in this browser", activity.packageName, intent.`package`)
        }
    }

    @Test
    fun developer_namesTheDeveloperAndTheProfiles() = section("section_developer") {
        assertEquals("Developer", title)
        assertEquals(listOf("Developer", "GitHub", "Codeberg"), all().map { it.title.toString() })
        assertEquals("rpooi3", pref("about_developer").summary.toString())
        mapOf("about_github" to "https://github.com/nadib330", "about_codeberg" to "https://codeberg.org/rpooi3")
            .forEach { (key, url) ->
                pref(key).performClick()
                assertEquals(key, url, shadowOf(activity).nextStartedActivity.dataString)
            }
    }

    @Test
    fun theLicenseTextsInTheApp_areTheOnesOfTheProject() {
        // Gradle runs the tests in the module's directory.
        val root = File("..").canonicalFile
        fun asset(name: String) = SettingsScreens.readAsset(context, name)
        assertEquals(File(root, "COPYING").readText(), asset("licenses/GPL-3.0.txt"))
        assertEquals(File(root, "COPYING.AGPL").readText(), asset("licenses/AGPL-3.0.txt"))
        assertTrue(asset("licenses/components.txt")!!.contains(File(root, "third_party/chromium-settings/LICENSE").readText().trim()))
        assertEquals(
            "the article finder is Mozilla's, unchanged",
            File(root, "third_party/readability/Readability.js").readText(),
            asset("reader/Readability.js"),
        )
    }

    // ------------------------------------------------- 7. Chromium's building blocks

    @Test
    fun chromiumsTexts_sayExplorer_notChrome() {
        val fields = R.string::class.java.fields.filter { it.name.startsWith("cr_") }
        assertTrue("the texts are there", fields.size > 100)
        for (field in fields) {
            val text = context.getString(field.getInt(null))
            assertFalse("${field.name}: $text", text.contains("Chrome"))
            assertFalse("${field.name} has a link mark left: $text", text.contains("<link") || text.contains("BEGIN_LINK"))
        }
    }

    @Test
    fun radioRows_areLaidOutLikeChromiums_radioButtonThenIconThenTextAndDescription() = section("section_safe_browsing") {
        val row = row("safe_browsing_standard")
        val radio = row.findViewById<RadioButton>(R.id.radio_start)
        val title = row.findViewById<TextView>(android.R.id.title)
        val summary = row.findViewById<TextView>(android.R.id.summary)
        assertEquals(View.VISIBLE, radio.visibility)
        assertTrue(radio.isChecked)
        assertFalse("the row takes the tap, not the button", radio.isClickable)
        assertEquals("Standard protection", title.text.toString())
        assertEquals(View.VISIBLE, summary.visibility)
        assertFalse(row("safe_browsing_off").findViewById<RadioButton>(R.id.radio_start).isChecked)
    }
}
