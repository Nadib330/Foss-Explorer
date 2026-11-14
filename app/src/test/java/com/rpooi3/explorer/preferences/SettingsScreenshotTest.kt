package com.rpooi3.explorer.preferences

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.EditText
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.rpooi3.explorer.site.SiteCategory
import com.rpooi3.explorer.site.SiteSettings
import java.io.File

/** Renders the settings screens to `app/build/outputs/screenshots/`. No device is needed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_light() = capture("settings-light", Settings_Activity::class.java)

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun settings_dark() = capture("settings-dark", Settings_Activity::class.java)

    /** Android 8: the Foss Explorer brand palette. */
    @Test
    @Config(sdk = [26], qualifiers = PHONE)
    fun settings_android8() = capture("settings-android8", Settings_Activity::class.java)

    /** Searching for a setting finds the topic it is in. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_search() = capture("settings-search", Settings_Activity::class.java) { activity ->
        activity.findViewById<EditText>(R.id.settings_search_input).setText("cookies")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_markedAds() = capture("settings-marked-ads", Settings_Activity::class.java, "section_marked_ads")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_topic_siteSettings() = capture("settings-site", Settings_Activity::class.java, "section_site")

    /** The whole list on one picture: both groups and the card below them. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1800dp-notnight-xxhdpi")
    fun settings_topic_siteSettings_whole() = capture("settings-site-whole", Settings_Activity::class.java, "section_site")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_topic_help() = capture("settings-help", Settings_Activity::class.java, "section_help")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_topic_about() = capture("settings-about", Settings_Activity::class.java, "section_about")

    /** Site settings > JavaScript, with one site blocked. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_javascript() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        SiteSettings.setException(SiteSettings.prefs(context), SiteCategory.JAVASCRIPT, "example.org", false)
        capture("settings-site-javascript", Settings_Activity::class.java, siteCategory = "javascript")
    }

    /** Site settings > Location: sites ask first; one was allowed, one blocked. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_location() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = SiteSettings.prefs(context)
        prefs.edit().putBoolean("profileStandard_location", true).commit()
        SiteSettings.setException(prefs, SiteCategory.LOCATION, "maps.example", true)
        SiteSettings.setException(prefs, SiteCategory.LOCATION, "ads.example", false)
        capture("settings-site-location", Settings_Activity::class.java, siteCategory = "location")
    }

    /** Site settings > Ads: the switch and Explorer's filter list. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_ads() = capture("settings-site-ads", Settings_Activity::class.java, siteCategory = "ads")

    /** Site settings > Sound: both lists, "Muted" and "Allowed". */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_sound() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = SiteSettings.prefs(context)
        SiteSettings.setException(prefs, SiteCategory.SOUND, "loud.example", false)
        SiteSettings.setException(prefs, SiteCategory.SOUND, "music.example", true)
        capture("settings-site-sound", Settings_Activity::class.java, siteCategory = "sound")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_camera() = capture("settings-site-camera", Settings_Activity::class.java, siteCategory = "camera")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_protectedContent() =
        capture("settings-site-protected-content", Settings_Activity::class.java, siteCategory = "protected_content")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_cookies() =
        capture("settings-site-third-party-cookies", Settings_Activity::class.java, siteCategory = "third_party_cookies")

    /** "Add site exception": Chromium's "Add a site" dialog. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_site_addSiteDialog() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), Settings_Activity::class.java)
            .putExtra(Settings_Activity.EXTRA_SITE_CATEGORY, "javascript")
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            val fragment = controller.get().supportFragmentManager.findFragmentById(R.id.content_frame)
                as androidx.preference.PreferenceFragmentCompat
            fragment.findPreference<androidx.preference.Preference>("site_category_add")!!.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            save("settings-site-add-dialog", dialog.window!!.decorView)
        }
    }

    // 13.2.2 ------------------------------------------------------------------------------------

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_preload() = capture("settings-privacy-preload", Settings_Activity::class.java, "section_preload")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_safeBrowsing() = capture("settings-privacy-safe-browsing", Settings_Activity::class.java, "section_safe_browsing")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_tabs() = capture("settings-tabs", Settings_Activity::class.java, "section_tabs")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_tabs_inactive() = capture("settings-tabs-inactive", Settings_Activity::class.java, "section_inactive_tabs")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_accessibility() = capture("settings-accessibility", Settings_Activity::class.java, "section_accessibility")

    /** The whole Accessibility screen on one picture. */
    @Test
    @Config(sdk = [34], qualifiers = TALL)
    fun settings_accessibility_whole() =
        capture("settings-accessibility-whole", Settings_Activity::class.java, "section_accessibility")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1800dp-night-xxhdpi")
    fun settings_accessibility_dark() =
        capture("settings-accessibility-dark", Settings_Activity::class.java, "section_accessibility")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_savedZoom() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = SiteSettings.prefs(context)
        com.rpooi3.explorer.accessibility.PageZoom.setSiteZoom(prefs, "news.example", 125)
        com.rpooi3.explorer.accessibility.PageZoom.setSiteZoom(prefs, "blog.example", 175)
        capture("settings-accessibility-saved-zoom", Settings_Activity::class.java, "section_saved_zoom")
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_searchEngine() = capture("settings-search-engine", Settings_Activity::class.java, "section_search")

    @Test
    @Config(sdk = [34], qualifiers = TALL)
    fun settings_searchEngine_whole() = capture("settings-search-engine-whole", Settings_Activity::class.java, "section_search")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun settings_searchEngine_dark() = capture("settings-search-engine-dark", Settings_Activity::class.java, "section_search")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_legal() = capture("settings-about-legal", Settings_Activity::class.java, "section_legal")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_developer() = capture("settings-about-developer", Settings_Activity::class.java, "section_developer")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_topic_appearance() = capture("settings-appearance", Settings_Activity::class.java, "section_appearance")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_topic_privacy() = capture("settings-privacy", Settings_Activity::class.java, "section_privacy")

    // 14.2.3 ------------------------------------------------------------------------------------

    @Test
    @Config(sdk = [34], qualifiers = TALL)
    fun settings_homeCustomization() =
        capture("settings-home-customization", Settings_Activity::class.java, "section_home_custom")

    /** Home page customization > Background color: the samples to choose from. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun settings_homeColors() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), Settings_Activity::class.java)
            .putExtra(Settings_Activity.EXTRA_SECTION, "section_home_custom")
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            val fragment = controller.get().supportFragmentManager.findFragmentById(R.id.content_frame)
                as androidx.preference.PreferenceFragmentCompat
            fragment.findPreference<androidx.preference.Preference>("home_color")!!.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            save("settings-home-colors", org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView)
        }
    }

    private fun <A : Activity> capture(
        name: String,
        type: Class<A>,
        section: String? = null,
        siteCategory: String? = null,
        prepare: (A) -> Unit = {},
    ) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), type)
        if (section != null) intent.putExtra(Settings_Activity.EXTRA_SECTION, section)
        if (siteCategory != null) intent.putExtra(Settings_Activity.EXTRA_SITE_CATEGORY, siteCategory)
        Robolectric.buildActivity(type, intent).setup().use { controller ->
            val activity = controller.get()
            prepare(activity)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))

            save(name, activity.findViewById(android.R.id.content))
        }
    }

    private fun save(name: String, root: View) {
        assertTrue("screen was laid out", root.width > 0 && root.height > 0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val PHONE = "w411dp-h891dp-notnight-xxhdpi"
        const val TALL = "w411dp-h1800dp-notnight-xxhdpi"
    }
}
