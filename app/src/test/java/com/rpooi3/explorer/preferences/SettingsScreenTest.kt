package com.rpooi3.explorer.preferences

import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import com.rpooi3.explorer.activity.Settings_Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.activity.Settings_Backup
import com.rpooi3.explorer.activity.Settings_Filter
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config

/**
 * The settings: a first screen that lists topics by title only, a full screen
 * per topic, "Help and support" and "About" at the end, all drawn as groups
 * of cards, and a search field on the first screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class SettingsScreenTest {

    private class Screen(val activity: androidx.appcompat.app.AppCompatActivity) {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.content_frame) as PreferenceFragmentCompat
        val list: ViewGroup get() = fragment.listView
        val search: View = activity.findViewById(R.id.settings_search)
        val input: EditText = activity.findViewById(R.id.settings_search_input)
        val empty: View = activity.findViewById(R.id.settings_search_empty)

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun pref(key: String): Preference = fragment.findPreference(key)!!

        /** All settings of the screen, without the groups, in display order. */
        fun allPrefs(): List<Preference> {
            val result = ArrayList<Preference>()
            fun visit(group: androidx.preference.PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    val pref = group.getPreference(i)
                    if (pref is androidx.preference.PreferenceGroup) visit(pref) else result += pref
                }
            }
            visit(fragment.preferenceScreen)
            return result
        }

        fun type(text: String) {
            input.setText(text)
            idle()
        }

        /** The rows that are on screen, as (title, row view). */
        fun rows(): List<Pair<String, View>> = (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filter { it.findViewById<View?>(R.id.icon_frame) != null }
            .map { it.findViewById<TextView>(android.R.id.title).text.toString() to it }
    }

    /** Opens the screen of one topic, as the first settings screen does. */
    private fun <T> section(key: String, block: Screen.() -> T): T {
        val intent = Intent(ApplicationProvider.getApplicationContext(), Settings_Activity::class.java)
            .putExtra(Settings_Activity.EXTRA_SECTION, key)
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            return Screen(controller.get()).run {
                val result = block()
                idle()
                result
            }
        }
    }

    private fun <A : androidx.appcompat.app.AppCompatActivity> launch(type: Class<A>, block: Screen.() -> Unit) {
        Robolectric.buildActivity(type).setup().use { controller ->
            shadowOf(Looper.getMainLooper()).idle()
            Screen(controller.get()).apply {
                block()
                idle()
            }
        }
    }

    @Test
    fun mainScreen_hasTitleBackArrowAndSearchField() = launch(Settings_Activity::class.java) {
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        assertEquals("Settings", toolbar.title.toString())
        assertTrue("back arrow", toolbar.navigationIcon != null)

        assertEquals(View.VISIBLE, search.visibility)
        assertEquals("Search settings", input.hint.toString())
        assertTrue("search field sits above the list", search.bottom <= activity.findViewById<View>(R.id.content_frame).top)
    }

    @Test
    fun mainScreen_listsTheTopics_byTitleOnly_endingWithHelpAndAbout() = launch(Settings_Activity::class.java) {
        val topics = allPrefs()
        assertEquals(
            listOf(
                "Search engine", "Privacy and security", "Home page customization", "Tabs and groups",
                "Appearance", "Accessibility", "Gestures", "Bookmark filter", "Site settings", "Backup",
                "Help and support", "About Foss Explorer",
            ),
            topics.map { it.title.toString() },
        )
        for (topic in topics) {
            assertTrue("${topic.title} shows its title only", topic.summary.isNullOrEmpty())
            assertEquals("${topic.title} is a plain entry, not a setting", Preference::class.java, topic.javaClass)
        }
        assertEquals("section_help", topics[topics.size - 2].key)
        assertEquals("section_about", topics.last().key)
        assertNull("Post on website is gone", fragment.findPreference<Preference>("urlForPosting"))
    }

    @Test
    fun rows_areCards_withARoundIcon_andATitle() = launch(Settings_Activity::class.java) {
        val rows = rows()
        assertTrue("several rows are on screen", rows.size >= 5)
        assertEquals("Search engine", rows.first().first)

        val side = activity.resources.getDimensionPixelSize(R.dimen.settings_card_margin)
        for ((title, row) in rows) {
            assertTrue("$title has a card background", row.background is RippleDrawable)
            assertEquals("$title is inset from the left edge", side, row.left)
            assertEquals("$title is inset from the right edge", list.width - side, row.right)
            assertTrue("$title has a title", title.isNotBlank())
            assertFalse("$title shows no second line", row.findViewById<View>(android.R.id.summary).isShown)
            val frame = row.findViewById<View>(R.id.icon_frame)
            assertEquals("$title has a round icon area", frame.width, frame.height)
            assertTrue(frame.isShown)
        }
    }

    @Test
    fun groups_haveNoHeading_andAGapBetweenThem() = launch(Settings_Activity::class.java) {
        val screen = fragment.preferenceScreen
        assertEquals(3, screen.preferenceCount)
        for (i in 0 until screen.preferenceCount) {
            val group = screen.getPreference(i) as PreferenceCategory
            assertTrue("group $i has no heading", group.title.isNullOrEmpty())
            assertTrue("group $i has settings", group.preferenceCount > 0)
        }
    }

    @Test
    fun everyTopic_opensAFullScreenOfItsOwn() = launch(Settings_Activity::class.java) {
        for (section in SettingsSections.ALL) {
            pref(section.key).performClick()
            val started = shadowOf(activity).nextStartedActivity
            assertEquals(section.key, Settings_Activity::class.java.name, started.component!!.className)
            assertEquals(section.key, started.getStringExtra(Settings_Activity.EXTRA_SECTION))
        }
        mapOf(
            "settings_gesture" to Settings_Gesture::class.java,
            "settings_filter" to Settings_Filter::class.java,
            "settings_data" to Settings_Backup::class.java,
        ).forEach { (key, screen) ->
            pref(key).performClick()
            assertEquals(key, screen.name, shadowOf(activity).nextStartedActivity.component!!.className)
        }
    }

    @Test
    fun everySetting_isSomewhere_exceptTheRemovedOnes() {
        val keys = (SettingsSections.ALL + SettingsSections.SUB)
            .flatMap { section -> section(section.key) { allPrefs().map { it.key } } }
        for (key in listOf("favoriteURL", "sp_theme", "useDynamicColor", "settings_profile", "edit_standard", "settings_clear")) {
            assertEquals("$key appears exactly once", 1, keys.count { it == key })
        }
        // 12.1.2: cat mode and the typed user agents are gone; the filter list moved to Site settings > Ads.
        for (key in listOf("cat_mode_enabled", "sp_userAgent", "sp_userAgent_desktop", "ab_hosts")) {
            assertFalse("$key is gone", key in keys)
        }
        // 13.2.2: the old Tabs switches are gone; the font size became Accessibility > Default zoom;
        // the search engine list became a screen of radio buttons.
        for (key in listOf(
            "sp_restoreTabs", "sp_reloadTabs", "sp_close_tab_confirm", "sp_close_browser_confirm", "start_overview",
            "sp_fontSize", "sp_search_engine", "sp_search_engine_custom",
        )) {
            assertFalse("$key is gone", key in keys)
        }
        assertFalse("Post on website is gone", "urlForPosting" in keys)
        assertFalse("the overview has no pages to choose from any more", "start_tab" in keys)
    }

    @Test
    fun everySubScreen_isReachedFromARow_andHasATitle() {
        val owner = mapOf(
            "section_preload" to "section_privacy", "section_safe_browsing" to "section_privacy",
            "section_js_optimizer" to "section_privacy", "section_inactive_tabs" to "section_tabs",
            "section_saved_zoom" to "section_accessibility", "section_legal" to "section_about",
            "section_developer" to "section_about",
        )
        assertEquals(owner.keys, SettingsSections.SUB.map { it.key }.toSet())
        for ((sub, topic) in owner) {
            section(topic) {
                pref(sub).performClick()
                val started = shadowOf(activity).nextStartedActivity
                assertEquals(sub, Settings_Activity::class.java.name, started.component!!.className)
                assertEquals(sub, started.getStringExtra(Settings_Activity.EXTRA_SECTION))
            }
            section(sub) {
                val title = activity.findViewById<Toolbar>(R.id.toolbar).title.toString()
                assertEquals(sub, activity.getString(SettingsSections.byKey(sub)!!.title), title)
                assertEquals("no search field on a sub screen", View.GONE, search.visibility)
            }
        }
    }

    @Test
    fun helpScreen_hasTheDevelopersAddress_andQuestionsWithAnswers() {
        section("section_help") {
            assertEquals("Help and support", activity.findViewById<Toolbar>(R.id.toolbar).title.toString())
            val email = pref("help_email")
            // The row is headed by the address itself, with the note below it.
            assertEquals("rpooi3@duck.com", email.title.toString())
            assertEquals(
                "You can email me if it is extremely necessary. But I'm sorry I may not respond.",
                email.summary.toString(),
            )

            email.performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_SENDTO, intent.action)
            assertEquals("mailto:rpooi3@duck.com", intent.dataString)

            val questions = allPrefs().filter { it.key.startsWith("faq_") }
            assertTrue("several questions", questions.size >= 5)
            for (question in questions) {
                assertTrue(question.key, question.title.toString().endsWith("?"))
                assertTrue(question.key, question.summary.toString().length > 20)
            }
        }
    }

    @Test
    fun search_findsATopicByItsTitle_andClearingBringsAllBack() = launch(Settings_Activity::class.java) {
        type("gest")

        assertEquals(listOf("Gestures"), rows().map { it.first })
        assertFalse(pref("section_appearance").isVisible)
        assertEquals(View.GONE, empty.visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_search_clear).visibility)

        activity.findViewById<View>(R.id.settings_search_clear).performClick()
        idle()

        assertEquals("", input.text.toString())
        assertTrue(pref("section_appearance").isVisible)
        assertEquals(12, allPrefs().count { it.isVisible })
    }

    @Test
    fun search_findsTheTopicThatContainsASetting_ignoringCase() = launch(Settings_Activity::class.java) {
        // "Pop-ups and redirects" is a row of "Site settings".
        type("POP-UPS")
        assertEquals(listOf("Site settings"), rows().map { it.first })

        // "Automatically remove permissions" is found by its description as well.
        type("haven't visited recently")
        assertEquals(listOf("Site settings"), rows().map { it.first })

        // The profiles moved to "Site settings" with 13.2.2.
        type("saved websites")
        assertEquals(listOf("Site settings"), rows().map { it.first })

        // Chromium's rows are found inside their topics as well.
        type("safe browsing")
        assertEquals(listOf("Privacy and security"), rows().map { it.first })
        type("caret")
        assertEquals(listOf("Accessibility"), rows().map { it.first })

        // What was removed is not found any more.
        type("user agent")
        assertEquals(emptyList<String>(), rows().map { it.first })

        // A question of the help screen.
        type("default browser")
        assertEquals(listOf("Help and support"), rows().map { it.first })
    }

    @Test
    fun search_withoutAMatch_saysSo() = launch(Settings_Activity::class.java) {
        type("zzzzzz")

        assertEquals(View.VISIBLE, empty.visibility)
        assertTrue(rows().isEmpty())

        type("")
        assertEquals(View.GONE, empty.visibility)
    }

    @Test
    @Config(sdk = [26])
    fun appearanceScreen_doesNotOfferWallpaperColours_beforeAndroid12() {
        section("section_appearance") {
            assertFalse(pref("useDynamicColor").isVisible)
            assertTrue(pref("sp_theme").isVisible)
        }
    }

    @Test
    fun subScreens_useTheSameCards_withoutSearch_andKeepTheirHeadings() = launch(Settings_Gesture::class.java) {
        assertEquals(View.GONE, search.visibility)

        val first = fragment.preferenceScreen.getPreference(0) as PreferenceCategory
        assertEquals("Tabs button (bottom bar)", first.title.toString())
        val second = fragment.preferenceScreen.getPreference(1) as PreferenceCategory
        assertEquals("Menu button (top bar)", second.title.toString())

        val rows = rows()
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.second.background is RippleDrawable })
    }

    @Test
    fun blockEdges_followTheNeighbours() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val row = Preference(context)
        val group = PreferenceCategory(context)

        assertTrue(SettingsCards.isFirstInBlock(null))
        assertTrue(SettingsCards.isFirstInBlock(group))
        assertFalse(SettingsCards.isFirstInBlock(row))
        assertTrue(SettingsCards.isLastInBlock(null))
        assertTrue(SettingsCards.isLastInBlock(group))
        assertFalse(SettingsCards.isLastInBlock(row))
    }
}
