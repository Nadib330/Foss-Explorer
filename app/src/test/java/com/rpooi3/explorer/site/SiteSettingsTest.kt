package com.rpooi3.explorer.site

import android.Manifest
import android.content.Intent
import android.os.Looper
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.BrowserActivity
import com.rpooi3.explorer.activity.Settings_Activity
import com.rpooi3.explorer.preferences.ExpandablePreference
import com.rpooi3.explorer.preferences.RadioRowPreference
import com.rpooi3.explorer.preferences.TextMessagePreference
import com.rpooi3.explorer.site.SiteSettings.State
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
import java.util.concurrent.TimeUnit

/**
 * Settings > Site settings: the groups and rows of Chromium for Android, a
 * screen per row laid out like Chromium's (description, radio buttons, the
 * lists of sites, "Add site exception"), sites asking first, and the removal
 * of unused permissions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class SiteSettingsTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val profile = "profileStandard"

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> settings(extra: String, value: String, block: (Settings_Activity, PreferenceFragmentCompat) -> T): T {
        val intent = Intent(context, Settings_Activity::class.java).putExtra(extra, value)
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            idle()
            val activity = controller.get()
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.content_frame) as PreferenceFragmentCompat
            return block(activity, fragment)
        }
    }

    private fun <T> category(category: SiteCategory, block: (Settings_Activity, SiteCategoryFragment) -> T): T =
        settings(Settings_Activity.EXTRA_SITE_CATEGORY, category.key) { a, f -> block(a, f as SiteCategoryFragment) }

    private fun PreferenceFragmentCompat.groups(): List<PreferenceCategory> =
        (0 until preferenceScreen.preferenceCount).map { preferenceScreen.getPreference(it) as PreferenceCategory }

    private fun PreferenceCategory.titles(): List<String> =
        (0 until preferenceCount).map { getPreference(it).title.toString() }

    // ------------------------------------------------------------ the list

    @Test
    fun siteSettings_areGroupedAsAsked() {
        settings(Settings_Activity.EXTRA_SECTION, "section_site") { activity, fragment ->
            assertEquals("Site settings", activity.findViewById<Toolbar>(R.id.toolbar).title.toString())
            val groups = fragment.groups()
            assertEquals(4, groups.size)

            assertEquals("Permissions", groups[0].title.toString())
            assertEquals(
                "13.2.2: Notifications, Embedded content and Clipboard are gone",
                listOf("Location", "Camera", "Microphone", "Apps on device"),
                groups[0].titles(),
            )
            for (gone in listOf("site_notifications", "site_embedded_content", "site_clipboard")) {
                assertNull(gone, fragment.findPreference<Preference>(gone))
            }
            assertEquals(
                listOf("location", "camera", "microphone", "apps_on_device"),
                SiteCategory.entries.filter { it.group == SiteCategory.Group.PERMISSIONS }.map { it.key },
            )
            assertEquals("Content", groups[1].title.toString())
            assertEquals(
                listOf(
                    "Third-party cookies", "JavaScript", "Pop-ups and redirects", "Sound", "Ads",
                    "Protected content", "Auto-verify", "On-device site data", "Automatic downloads",
                ),
                groups[1].titles(),
            )
            // Below them, a card of its own.
            assertNull(groups[2].title)
            assertEquals(listOf("Automatically remove permissions"), groups[2].titles())
            val toggle = groups[2].getPreference(0) as SwitchPreferenceCompat
            assertEquals(
                "To protect your data, let Explorer remove permissions from sites that you haven't visited recently",
                toggle.summary.toString(),
            )
            assertTrue("on unless switched off, as in Chromium", toggle.isChecked)
            toggle.performClick()
            assertFalse(prefs.getBoolean(SiteSettings.AUTO_REVOKE, true))
            // The profiles moved here from "Privacy and security", which is Chromium's screen now.
            assertEquals("Profiles", groups[3].title.toString())
            assertNotNull(fragment.findPreference<Preference>("settings_profile"))
            assertNotNull(fragment.findPreference<Preference>("edit_standard"))
        }
    }

    @Test
    fun theUserAgentSettings_areGone() {
        settings(Settings_Activity.EXTRA_SECTION, "section_site") { _, fragment ->
            assertNull(fragment.findPreference<Preference>("sp_userAgent"))
            assertNull(fragment.findPreference<Preference>("sp_userAgent_desktop"))
            assertFalse(fragment.groups().flatMap { it.titles() }.any { it.contains("agent", ignoreCase = true) })
        }
    }

    @Test
    fun everyRow_showsItsState_theWayChromiumWordsIt() {
        prefs.edit()
            .putBoolean("${profile}_location", true)
            .putBoolean("${profile}_camera", false)
            .putBoolean("${profile}_javascript", true)
            .putBoolean("${profile}_javascriptPopUp", false)
            .putBoolean("${profile}_adBlock", true)
            .commit()
        SiteSettings.setOn(prefs, SiteCategory.SOUND, false)

        settings(Settings_Activity.EXTRA_SECTION, "section_site") { _, fragment ->
            fun state(category: SiteCategory) = fragment.findPreference<Preference>(category.rowKey)!!.summary.toString()
            assertEquals("Ask first", state(SiteCategory.LOCATION))
            assertEquals("Blocked", state(SiteCategory.CAMERA))
            assertEquals("Allowed", state(SiteCategory.JAVASCRIPT))
            assertEquals("Blocked", state(SiteCategory.POPUPS))
            assertEquals("ads are blocked while the ad blocker is on", "Blocked", state(SiteCategory.ADS))
            assertEquals("Muted", state(SiteCategory.SOUND))
            assertEquals("Ask first", state(SiteCategory.AUTOMATIC_DOWNLOADS))
            assertEquals("Not available", state(SiteCategory.AUTO_VERIFY))
        }
    }

    @Test
    fun aRow_opensTheScreenOfItsCategory() {
        settings(Settings_Activity.EXTRA_SECTION, "section_site") { activity, fragment ->
            for (category in SiteCategory.entries) {
                fragment.findPreference<Preference>(category.rowKey)!!.performClick()
                val intent = shadowOf(activity).nextStartedActivity
                assertEquals(Settings_Activity::class.java.name, intent.component!!.className)
                assertEquals(category.key, intent.getStringExtra(Settings_Activity.EXTRA_SITE_CATEGORY))
            }
        }
    }

    // -------------------------------------------------- one category's screen

    private fun PreferenceFragmentCompat.radio(key: String) = findPreference<RadioRowPreference>(key)!!

    /** Keys of everything on the screen, in order, groups opened up. */
    private fun PreferenceFragmentCompat.keys(): List<String> {
        val keys = ArrayList<String>()
        fun walk(group: androidx.preference.PreferenceGroup) {
            for (i in 0 until group.preferenceCount) {
                val pref = group.getPreference(i)
                if (pref is androidx.preference.PreferenceGroup) walk(pref) else keys += pref.key
            }
        }
        walk(preferenceScreen)
        return keys
    }

    private fun AlertDialog.titleText(): String =
        findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()

    @Test
    fun aCategoryScreen_isLaidOutLikeChromiums_descriptionThenTwoChoices() {
        prefs.edit().putBoolean("${profile}_javascript", true).commit()
        category(SiteCategory.JAVASCRIPT) { activity, fragment ->
            assertEquals("JavaScript", activity.findViewById<Toolbar>(R.id.toolbar).title.toString())
            assertEquals(
                listOf(
                    SiteCategoryFragment.KEY_DESCRIPTION, SiteCategoryFragment.KEY_ON, SiteCategoryFragment.KEY_OFF,
                    SiteCategoryFragment.KEY_ADD,
                ),
                fragment.keys(),
            )
            assertNull("no switch: Chromium has radio buttons here", fragment.findPreference<Preference>(SiteCategoryFragment.KEY_SWITCH))
            val description = fragment.findPreference<TextMessagePreference>(SiteCategoryFragment.KEY_DESCRIPTION)!!
            assertEquals(
                "Sites usually use JavaScript to display interactive features, like video games or web forms",
                description.summary.toString(),
            )
            assertEquals("Sites can use JavaScript", fragment.radio(SiteCategoryFragment.KEY_ON).title.toString())
            assertEquals("Don't allow sites to use JavaScript", fragment.radio(SiteCategoryFragment.KEY_OFF).title.toString())
            assertNotNull("each choice has its icon", fragment.radio(SiteCategoryFragment.KEY_ON).icon)
            assertNotNull(fragment.radio(SiteCategoryFragment.KEY_OFF).icon)
            assertTrue(fragment.radio(SiteCategoryFragment.KEY_ON).isChecked)
            assertFalse(fragment.radio(SiteCategoryFragment.KEY_OFF).isChecked)

            fragment.radio(SiteCategoryFragment.KEY_OFF).performClick()
            idle()

            assertFalse("stored in the profile, where the browser reads it", prefs.getBoolean("${profile}_javascript", true))
            assertFalse(fragment.radio(SiteCategoryFragment.KEY_ON).isChecked)
            assertTrue(fragment.radio(SiteCategoryFragment.KEY_OFF).isChecked)

            fragment.radio(SiteCategoryFragment.KEY_ON).performClick()
            idle()
            assertTrue(prefs.getBoolean("${profile}_javascript", false))
        }
    }

    @Test
    fun everyCategoryScreen_hasChromiumsTexts() {
        val expected = mapOf(
            SiteCategory.LOCATION to listOf(
                "Sites usually use your location for relevant features or info, like local news or nearby shops",
                "Sites can ask for your location", "Don't allow sites to see your location", null,
            ),
            SiteCategory.CAMERA to listOf(
                "Sites usually use your video camera for communication features like video chatting",
                "Sites can ask for your camera", "Don't allow sites to use your camera",
                "Features that need a camera won't work",
            ),
            SiteCategory.MICROPHONE to listOf(
                "Sites usually use your microphone for communication features like video chatting",
                "Sites can ask for your microphone", "Don't allow sites to use your microphone",
                "Features that need a microphone won't work",
            ),
            SiteCategory.THIRD_PARTY_COOKIES to listOf(
                "A site you visit can embed content from other sites, for example, images, ads, and text. " +
                    "Cookies set by these other sites are called third-party cookies.",
                "Allow third-party cookies", "Block third-party cookies", "Features on some sites may not work",
            ),
            SiteCategory.POPUPS to listOf(
                "Sites might send pop-ups to show ads, or use redirects to lead you to websites you may not want to visit",
                "Sites can send pop-ups and use redirects", "Don't allow sites to send pop-ups and use redirects", null,
            ),
            SiteCategory.SOUND to listOf(
                "Sites might play sound to provide audio for music, videos and other media",
                "Sites can play sound", "Don't allow sites to play sound", "Features that need sound won't work",
            ),
            SiteCategory.AUTOMATIC_DOWNLOADS to listOf(
                "Sites might automatically download related files together to save you time",
                "Sites can ask to automatically download multiple files",
                "Don't allow sites to automatically download multiple files", null,
            ),
        )
        for ((category, texts) in expected) {
            category(category) { _, fragment ->
                assertEquals(
                    category.key, texts[0],
                    fragment.findPreference<Preference>(SiteCategoryFragment.KEY_DESCRIPTION)!!.summary.toString(),
                )
                assertEquals(category.key, texts[1], fragment.radio(SiteCategoryFragment.KEY_ON).title.toString())
                assertNull("nothing below the first choice", fragment.radio(SiteCategoryFragment.KEY_ON).summary)
                assertEquals(category.key, texts[2], fragment.radio(SiteCategoryFragment.KEY_OFF).title.toString())
                assertEquals(category.key, texts[3], fragment.radio(SiteCategoryFragment.KEY_OFF).summary?.toString())
            }
        }
        // Every category has a description and two choices of its own.
        for (category in SiteCategory.entries) {
            assertTrue(category.key, context.getString(category.description).isNotBlank())
            assertTrue(category.key, context.getString(category.onText).isNotBlank())
            assertTrue(category.key, context.getString(category.offText).isNotBlank())
        }
    }

    @Test
    fun theAdsChoice_isTheAdBlockerTheOtherWayRound_andHasTheFilterList() {
        prefs.edit().putBoolean("${profile}_adBlock", true).commit()
        category(SiteCategory.ADS) { _, fragment ->
            assertTrue("ads are not allowed while they are blocked", fragment.radio(SiteCategoryFragment.KEY_OFF).isChecked)
            assertEquals(
                "Block ads and trackers on sites, using the filter list",
                fragment.radio(SiteCategoryFragment.KEY_OFF).title.toString(),
            )
            assertNotNull("Explorer's own ad setting", fragment.findPreference<Preference>("ab_hosts"))

            fragment.radio(SiteCategoryFragment.KEY_ON).performClick()
            idle()

            assertFalse(prefs.getBoolean("${profile}_adBlock", true))
        }
    }

    @Test
    fun onDeviceSiteData_asksBeforeItIsSwitchedOff_andSwitchesCookiesAndStorageTogether() {
        category(SiteCategory.SITE_DATA) { _, fragment ->
            fragment.radio(SiteCategoryFragment.KEY_OFF).performClick()
            idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            assertTrue("nothing changes before the answer", prefs.getBoolean("${profile}_cookies", true))
            assertTrue(fragment.radio(SiteCategoryFragment.KEY_ON).isChecked)
            assertEquals("Turn off", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertTrue(fragment.radio(SiteCategoryFragment.KEY_OFF).isChecked)
        }
        assertFalse(prefs.getBoolean("${profile}_cookies", true))
        assertFalse(prefs.getBoolean("${profile}_dom", true))
    }

    @Test
    fun protectedContent_hasThreeChoices_andCanAsk() {
        category(SiteCategory.PROTECTED_CONTENT) { _, fragment ->
            assertEquals(
                listOf("Sites can play protected content", "Sites can ask to play protected content", "Don't allow sites to play protected content"),
                listOf(SiteCategoryFragment.KEY_ON, SiteCategoryFragment.KEY_ASK, SiteCategoryFragment.KEY_OFF)
                    .map { fragment.radio(it).title.toString() },
            )
            fragment.radio(SiteCategoryFragment.KEY_ASK).performClick()
            idle()
            assertTrue(fragment.radio(SiteCategoryFragment.KEY_ASK).isChecked)
        }
        assertEquals(State.ASK, SiteSettings.state(context, SiteCategory.PROTECTED_CONTENT, "https://video.example/"))
        category(SiteCategory.PROTECTED_CONTENT) { _, fragment ->
            fragment.radio(SiteCategoryFragment.KEY_OFF).performClick()
            idle()
        }
        assertEquals(State.BLOCK, SiteSettings.state(context, SiteCategory.PROTECTED_CONTENT, "https://video.example/"))
        category(SiteCategory.PROTECTED_CONTENT) { _, fragment ->
            fragment.radio(SiteCategoryFragment.KEY_ON).performClick()
            idle()
        }
        assertEquals(State.ALLOW, SiteSettings.state(context, SiteCategory.PROTECTED_CONTENT, "https://video.example/"))
    }

    @Test
    fun autoVerify_isShownAsChromiumHasIt_butCannotBeSwitched() {
        assertEquals(listOf(SiteCategory.AUTO_VERIFY), SiteCategory.entries.filter { it.kind == SiteCategory.Kind.UNAVAILABLE })
        category(SiteCategory.AUTO_VERIFY) { _, fragment ->
            assertEquals(
                "Sites you visit can verify that you're a real person and not a bot",
                fragment.findPreference<Preference>(SiteCategoryFragment.KEY_DESCRIPTION)!!.summary.toString(),
            )
            val switch = fragment.findPreference<SwitchPreferenceCompat>(SiteCategoryFragment.KEY_SWITCH)!!
            assertFalse(switch.isEnabled)
            assertFalse(switch.isChecked)
            assertEquals("Not available in Explorer", fragment.findPreference<Preference>(SiteCategoryFragment.KEY_NOTE)!!.title.toString())
            assertEquals("When on", fragment.findPreference<Preference>("site_category_when_on")!!.title.toString())
            assertEquals("Things to consider", fragment.findPreference<Preference>("site_category_consider")!!.title.toString())
            assertNull(fragment.findPreference<Preference>(SiteCategoryFragment.KEY_ADD))
        }
        assertEquals(State.BLOCK, SiteSettings.state(context, SiteCategory.AUTO_VERIFY, "https://example.org/"))
    }

    @Test
    fun addSiteException_addsTheOppositeOfTheGeneralChoice_andListsIt() {
        prefs.edit().putBoolean("${profile}_javascript", true).commit()
        category(SiteCategory.JAVASCRIPT) { _, fragment ->
            val add = fragment.findPreference<Preference>(SiteCategoryFragment.KEY_ADD)!!
            assertEquals("Add site exception", add.title.toString())
            assertEquals("the last row of the screen", SiteCategoryFragment.KEY_ADD, fragment.keys().last())
            add.performClick()
            idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue("asks for the site", dialog.isShowing)
            assertEquals("Add a site", dialog.titleText())
            assertEquals(
                "Block JavaScript for a specific site.",
                dialog.findViewById<android.widget.TextView>(android.R.id.message)!!.text.toString(),
            )
            assertEquals("Add", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            dialog.dismiss()

            assertFalse("not a site", fragment.addSite("  ", allowed = false))
            assertTrue(fragment.addSite("https://www.Example.org/some/page", allowed = false))
            idle()

            val blocked = fragment.findPreference<ExpandablePreference>(SiteCategoryFragment.KEY_BLOCKED)!!
            assertEquals("Not Allowed (1)", blocked.title.toString())
            assertNotNull(fragment.findPreference<Preference>("blocked:example.org"))
            assertNull(fragment.findPreference<Preference>(SiteCategoryFragment.KEY_ALLOWED))
            assertEquals(
                "Not Allowed stands above Add site exception",
                listOf(SiteCategoryFragment.KEY_BLOCKED, "blocked:example.org", SiteCategoryFragment.KEY_ADD),
                fragment.keys().takeLast(3),
            )
        }
        assertEquals(State.BLOCK, SiteSettings.state(context, SiteCategory.JAVASCRIPT, "https://example.org/"))
        assertEquals(State.ALLOW, SiteSettings.state(context, SiteCategory.JAVASCRIPT, "https://example.com/"))
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun aCategoryWithoutAnOffIconOfItsOwn_showsItsIconCrossedOut_inTheColourOfIcons() {
        category(SiteCategory.THIRD_PARTY_COOKIES) { _, fragment ->
            val row = fragment.radio(SiteCategoryFragment.KEY_OFF)
            assertTrue(row.iconHasColors)
            // Shown the way the row shows it: the view must not recolour the picture as a whole.
            val image = android.widget.ImageView(fragment.requireContext())
            image.setImageDrawable(row.icon)
            image.imageTintList = null
            val bitmap = android.graphics.Bitmap.createBitmap(72, 72, android.graphics.Bitmap.Config.ARGB_8888)
            row.icon!!.setBounds(0, 0, 72, 72)
            row.icon!!.draw(android.graphics.Canvas(bitmap))
            val pixels = IntArray(72 * 72).also { bitmap.getPixels(it, 0, 72, 0, 0, 72, 72) }
            // Without its colours the picture would be white, the colour its vectors are drawn in.
            val dark = pixels.count { android.graphics.Color.alpha(it) > 200 && android.graphics.Color.red(it) < 128 }
            assertTrue("the icon is drawn in the colour of icons, $dark dark pixels", dark > 200)
            assertTrue("the stroke reaches the corner the icon leaves free", android.graphics.Color.alpha(bitmap.getPixel(64, 61)) > 0)
        }
    }

    @Test
    fun theLists_openAndClose() {
        SiteSettings.setException(prefs, SiteCategory.SOUND, "loud.example", false)
        SiteSettings.setException(prefs, SiteCategory.SOUND, "music.example", true)
        category(SiteCategory.SOUND) { _, fragment ->
            val muted = fragment.findPreference<ExpandablePreference>(SiteCategoryFragment.KEY_BLOCKED)!!
            val allowed = fragment.findPreference<ExpandablePreference>(SiteCategoryFragment.KEY_ALLOWED)!!
            assertEquals("Muted (1)", muted.title.toString())
            assertEquals("Allowed (1)", allowed.title.toString())
            assertTrue(fragment.keys().indexOf(SiteCategoryFragment.KEY_BLOCKED) < fragment.keys().indexOf(SiteCategoryFragment.KEY_ALLOWED))
            val site = fragment.findPreference<Preference>("blocked:loud.example")!!
            assertTrue(muted.isExpanded)
            assertTrue(site.isVisible)

            muted.performClick()
            assertFalse(muted.isExpanded)
            assertFalse(site.isVisible)
            assertTrue("the other list is not touched", fragment.findPreference<Preference>("allowed:music.example")!!.isVisible)

            muted.performClick()
            assertTrue(site.isVisible)
        }
    }

    @Test
    fun aListedSite_canBeSwitchedOrRemoved() {
        SiteSettings.setException(prefs, SiteCategory.SOUND, "example.org", false)
        category(SiteCategory.SOUND) { _, fragment ->
            fragment.findPreference<Preference>("blocked:example.org")!!.performClick()
            idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(listOf("Allow", "Block"), (0 until dialog.listView.count).map { dialog.listView.adapter.getItem(it).toString() })
            assertEquals("the current choice is marked", 1, dialog.listView.checkedItemPosition)

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            idle()

            assertNull(fragment.findPreference<Preference>("blocked:example.org"))
        }
        assertNull(SiteSettings.exception(prefs, SiteCategory.SOUND, "example.org"))
    }

    @Test
    fun addSiteException_isWhereChromiumHasIt() {
        fun adds(category: SiteCategory): Boolean? = category(category) { _, fragment ->
            if (fragment.findPreference<Preference>(SiteCategoryFragment.KEY_ADD) == null) null else fragment.addExceptionAllows()
        }
        // Sites that have to ask get their entry by being answered.
        assertNull(adds(SiteCategory.LOCATION))
        assertNull(adds(SiteCategory.CAMERA))
        assertNull(adds(SiteCategory.MICROPHONE))
        // Automatic downloads can be allowed for a site by hand.
        assertEquals(true, adds(SiteCategory.AUTOMATIC_DOWNLOADS))
        // Blocked in general: a site can be allowed.
        prefs.edit().putBoolean("${profile}_javascriptPopUp", false).commit()
        assertEquals(true, adds(SiteCategory.POPUPS))
        // Allowed in general: a site can be blocked.
        assertEquals(false, adds(SiteCategory.SOUND))
    }

    // ------------------------------------------------------------- the rules

    @Test
    fun aSitesOwnChoice_winsOverTheGeneralOne_andCoversItsSubdomains() {
        prefs.edit().putBoolean("${profile}_javascriptPopUp", false).commit()
        SiteSettings.setException(prefs, SiteCategory.POPUPS, "example.org", true)

        fun state(url: String) = SiteSettings.state(prefs, SiteCategory.POPUPS, url, profile)
        assertEquals(State.ALLOW, state("https://example.org/a"))
        assertEquals(State.ALLOW, state("https://www.example.org/"))
        assertEquals(State.ALLOW, state("https://shop.example.org/"))
        assertEquals("not a subdomain", State.BLOCK, state("https://notexample.org/"))
        assertEquals(State.BLOCK, state("https://example.com/"))

        // The closer entry wins.
        SiteSettings.setException(prefs, SiteCategory.POPUPS, "shop.example.org", false)
        assertEquals(State.BLOCK, state("https://shop.example.org/"))
        assertEquals(State.ALLOW, state("https://example.org/"))
    }

    @Test
    fun askCategories_ask_untilTheSiteWasAnswered() {
        prefs.edit().putBoolean("${profile}_camera", true).commit()
        assertEquals(State.ASK, SiteSettings.state(prefs, SiteCategory.CAMERA, "https://meet.example/", profile))

        SiteSettings.setException(prefs, SiteCategory.CAMERA, "meet.example", true)
        assertEquals(State.ALLOW, SiteSettings.state(prefs, SiteCategory.CAMERA, "https://meet.example/", profile))

        // Switched off, everything is blocked except the sites allowed by name.
        prefs.edit().putBoolean("${profile}_camera", false).commit()
        assertEquals(State.BLOCK, SiteSettings.state(prefs, SiteCategory.CAMERA, "https://other.example/", profile))
        assertEquals(State.ALLOW, SiteSettings.state(prefs, SiteCategory.CAMERA, "https://meet.example/", profile))
    }

    @Test
    fun siteNames_areReadFromAddressesOriginsAndPatterns() {
        assertEquals("example.org", SiteSettings.site("https://www.example.org:8443/path?x=1"))
        assertEquals("example.org", SiteSettings.site("Example.org"))
        assertEquals("example.org", SiteSettings.site("[*.]example.org"))
        assertEquals("example.org", SiteSettings.site("example.org/path"))
        assertEquals("", SiteSettings.site("not a site"))
        assertEquals("", SiteSettings.site(null))
        assertTrue(SiteSettings.isLoopback("localhost"))
        assertTrue(SiteSettings.isLoopback("127.0.0.1"))
        assertTrue(SiteSettings.isLoopback("[::1]"))
        assertFalse(SiteSettings.isLoopback("example.org"))
        assertFalse(SiteSettings.isLoopback("192.168.1.10"))
    }

    @Test
    fun unusedPermissions_areRemovedAfterSixtyDays_whileTheSwitchIsOn() {
        val day = TimeUnit.DAYS.toMillis(1)
        val start = 1_000_000_000_000L
        SiteSettings.setException(prefs, SiteCategory.LOCATION, "old.example", true)
        SiteSettings.setException(prefs, SiteCategory.LOCATION, "used.example", true)
        SiteSettings.setException(prefs, SiteCategory.LOCATION, "blocked.example", false)
        SiteSettings.setException(prefs, SiteCategory.JAVASCRIPT, "old.example", true)
        SiteSettings.recordVisit(prefs, "https://old.example/", start)
        SiteSettings.recordVisit(prefs, "https://used.example/", start)

        SiteSettings.recordVisit(prefs, "https://www.used.example/page", start + 50 * day)
        assertEquals("nothing is due yet", 0, SiteSettings.removeUnusedPermissions(prefs, start + 59 * day))

        assertEquals(1, SiteSettings.removeUnusedPermissions(prefs, start + 61 * day))
        assertNull(SiteSettings.exception(prefs, SiteCategory.LOCATION, "old.example"))
        assertEquals("visited ten days ago", true, SiteSettings.exception(prefs, SiteCategory.LOCATION, "used.example"))
        assertEquals("blocks are not permissions", false, SiteSettings.exception(prefs, SiteCategory.LOCATION, "blocked.example"))
        assertEquals("content settings stay", true, SiteSettings.exception(prefs, SiteCategory.JAVASCRIPT, "old.example"))
    }

    @Test
    fun withTheSwitchOff_permissionsStay() {
        val day = TimeUnit.DAYS.toMillis(1)
        SiteSettings.setException(prefs, SiteCategory.CAMERA, "old.example", true)
        SiteSettings.recordVisit(prefs, "https://old.example/", 0L)
        prefs.edit().putBoolean(SiteSettings.AUTO_REVOKE, false).commit()

        assertEquals(0, SiteSettings.removeUnusedPermissions(prefs, 400 * day))
        assertEquals(true, SiteSettings.exception(prefs, SiteCategory.CAMERA, "old.example"))
    }

    // ------------------------------------------------------- in the browser

    private fun browser(block: (BrowserActivity, NinjaWebView) -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().use { controller ->
            idle()
            val activity = controller.get()
            val content = activity.findViewById<ViewGroup>(R.id.main_content)
            block(activity, content.getChildAt(0) as NinjaWebView)
            idle()
        }
    }

    @Test
    fun aPage_getsTheSettingsOfItsSite() {
        prefs.edit().putBoolean("${profile}_javascript", true).putBoolean("${profile}_javascriptPopUp", false).commit()
        SiteSettings.setException(prefs, SiteCategory.JAVASCRIPT, "noscript.example", false)
        SiteSettings.setException(prefs, SiteCategory.POPUPS, "popups.example", true)

        browser { _, web ->
            web.initPreferences("https://noscript.example/")
            assertFalse(web.settings.javaScriptEnabled)
            assertFalse(web.isPopUpsAllowed)

            web.initPreferences("https://popups.example/")
            assertTrue(web.settings.javaScriptEnabled)
            assertTrue(web.settings.javaScriptCanOpenWindowsAutomatically)
            assertTrue(web.isPopUpsAllowed)
        }
    }

    @Test
    fun appsOnDevice_keepsWebsitesFromThisDevice_unlessAllowed() {
        browser { _, web ->
            web.initPreferences("https://example.org/")
            assertFalse(web.mayLoadFrom("localhost"))
            assertFalse(web.mayLoadFrom("127.0.0.1"))
            assertTrue(web.mayLoadFrom("cdn.example.net"))

            // A page served from this device may use it.
            web.initPreferences("http://localhost:8080/")
            assertTrue(web.mayLoadFrom("127.0.0.1"))

            SiteSettings.setException(prefs, SiteCategory.APPS_ON_DEVICE, "example.org", true)
            web.initPreferences("https://example.org/")
            assertTrue(web.mayLoadFrom("localhost"))
        }
    }

    @Test
    fun aSiteThatWantsTheLocation_isAskedAbout_andTheAnswerIsKept() {
        prefs.edit().putBoolean("${profile}_location", true).commit()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        browser { activity, _ ->
            val answers = ArrayList<Boolean>()
            val callback = GeolocationPermissions.Callback { _, allow, _ -> answers += allow }

            SitePermissions.handleGeolocation(activity, "https://maps.example/", true, callback)
            idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue(dialog.isShowing)
            assertEquals(
                "maps.example wants to use your device's location",
                dialog.findViewById<android.widget.TextView>(android.R.id.message)!!.text.toString(),
            )
            assertEquals("Allow", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertEquals("Block", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
            assertTrue("nothing is answered before the user has", answers.isEmpty())

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertEquals(listOf(true), answers)
            assertEquals(true, SiteSettings.exception(prefs, SiteCategory.LOCATION, "maps.example"))

            // The next request is answered without asking.
            val before = ShadowDialog.getShownDialogs().size
            SitePermissions.handleGeolocation(activity, "https://maps.example/", true, callback)
            idle()
            assertEquals(listOf(true, true), answers)
            assertEquals(before, ShadowDialog.getShownDialogs().size)
        }
    }

    @Test
    fun block_isKeptToo_andAPrivateTabKeepsNothing() {
        prefs.edit().putBoolean("${profile}_location", true).commit()
        browser { activity, _ ->
            val answers = ArrayList<Boolean>()
            val callback = GeolocationPermissions.Callback { _, allow, _ -> answers += allow }

            SitePermissions.handleGeolocation(activity, "https://ads.example/", true, callback)
            idle()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertEquals(listOf(false), answers)
            assertEquals(false, SiteSettings.exception(prefs, SiteCategory.LOCATION, "ads.example"))

            SitePermissions.handleGeolocation(activity, "https://private.example/", false, callback)
            idle()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            idle()
            assertEquals(listOf(false, false), answers)
            assertNull("asked from a private tab", SiteSettings.exception(prefs, SiteCategory.LOCATION, "private.example"))
        }
    }

    @Test
    fun withLocationBlocked_noSiteIsAskedAbout() {
        browser { activity, _ ->
            // After the first start, which sets "ask first" for a new installation.
            prefs.edit().putBoolean("${profile}_location", false).commit()
            val answers = ArrayList<Boolean>()
            val before = ShadowDialog.getShownDialogs().size

            SitePermissions.handleGeolocation(activity, "https://maps.example/", true) { _, allow, _ -> answers += allow }
            idle()

            assertEquals("the page gets its answer (it used to get none)", listOf(false), answers)
            assertEquals(before, ShadowDialog.getShownDialogs().size)
        }
    }

    @Test
    fun aNewInstallation_asksFirst_forLocationCameraAndMicrophone() {
        browser { _, _ ->
            for (category in listOf(SiteCategory.LOCATION, SiteCategory.CAMERA, SiteCategory.MICROPHONE)) {
                assertEquals(category.key, State.ASK, SiteSettings.state(context, category, "https://example.org/"))
            }
        }
    }

    @Test
    fun aSecondDownloadWithoutATap_isAnAutomaticOne() {
        browser { _, web ->
            assertFalse("the first download after a tap goes ahead", web.isFurtherDownload)
            assertTrue(web.isFurtherDownload)
            assertTrue(web.isFurtherDownload)

            val down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 10f, 10f, 0)
            web.dispatchTouchEvent(down)
            down.recycle()

            assertFalse("a tap starts the count again", web.isFurtherDownload)
        }
    }
}
