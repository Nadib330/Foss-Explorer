package com.rpooi3.explorer.activity

import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.extension.StoreHook
import com.rpooi3.explorer.media.BackgroundAudio
import com.rpooi3.explorer.profile.ProfilePrefs
import com.rpooi3.explorer.theme.ColorScheme
import com.rpooi3.explorer.theme.ColorSchemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 * 18.0.0 beta 9: the eight things asked for after beta 8.
 *
 * 1. media that is playing stays audible in the background, 2. Light theme and Dark theme instead
 * of Day view and Night view, 3. every setting stored per profile, 4. the button of Explorer on
 * the store that stays visible, 5. a store that reads on a phone without losing the fit, 6. a
 * private tab whose menu carries no extensions, 7. the preset color schemes of Chrome under
 * Appearance, 8. extensions whose popups keep their own shape and size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1800Beta9Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        ProfilePrefs.settingsFor(context, "2").edit().clear().commit()
    }

    private fun source(path: String): String = File(path).readText()

    private fun manifest(): String = source("src/main/AndroidManifest.xml")

    // 0. the version ---------------------------------------------------------------------------
    @Test
    fun theVersion_is26_and1800Beta9() {
        // 18.1.0 beta moved the version on (27, 18.1.0-beta); beta 9 asks that it is not behind.
        val gradle = source("build.gradle.kts")
        val code = Regex("versionCode = (\\d+)").find(gradle)!!.groupValues[1].toInt()
        assertTrue("versionCode 26 or later: $code", code >= 26)
        assertTrue("a name of 18.", gradle.contains("versionName = \"18."))
    }

    // 1. media that is playing keeps playing in the background ---------------------------------
    @Test
    fun media_keepsPlaying_whenAnotherAppComesToTheFront() {
        // The setting, and its default: a page that is playing stays audible.
        assertTrue("on unless it is turned off", BackgroundAudio.isEnabled(prefs))
        assertTrue("and not running before it is started", !BackgroundAudio.isRunning())
        prefs.edit().putBoolean(BackgroundAudio.KEY, false).commit()
        assertFalse("off when it is turned off", BackgroundAudio.isEnabled(prefs))
        prefs.edit().putBoolean(BackgroundAudio.KEY, true).commit()

        // The page is asked what it is doing, and play/pause is sent into it.
        val probe = BackgroundAudio.playingScript()
        assertTrue("the videos of the page are looked at", probe.contains("video"))
        assertTrue("and what is playing is found", probe.contains("paused"))
        assertTrue("play is sent into the page", BackgroundAudio.playScript(true).contains("play()"))
        assertTrue("and pause as well", BackgroundAudio.playScript(false).contains("pause()"))

        // A service of the foreground, of the kind that plays media, and the right to keep awake.
        val xml = manifest()
        assertTrue("FOREGROUND_SERVICE", xml.contains("android.permission.FOREGROUND_SERVICE\""))
        assertTrue(
            "FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            xml.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK\""),
        )
        assertTrue("WAKE_LOCK", xml.contains("android.permission.WAKE_LOCK\""))
        assertTrue(
            "the service is of the media-playback kind",
            xml.contains("foregroundServiceType=\"mediaPlayback\""),
        )
        assertTrue(
            "and it is BackgroundAudioService",
            xml.contains("BackgroundAudioService"),
        )

        // Leaving the browser asks the page, and coming back stops the service.
        val activity = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        assertTrue("onPause probes the tab", activity.contains("BackgroundAudio.probe("))
        assertTrue("and starts the service", activity.contains("BackgroundAudio.start("))
        assertTrue("onResume stops it", activity.contains("BackgroundAudio.stop(this)"))

        // The switch is offered — in the site settings since 18.1.0 beta (item 1).
        val site = source("src/main/res/xml/preference_section_site.xml")
        assertTrue("the switch is offered", site.contains("android:key=\"background_audio\""))
        assertEquals(
            "and it starts on",
            "true",
            Regex("android:key=\"background_audio\"[^/]*android:defaultValue=\"([^\"]+)\"")
                .find(site)?.groupValues?.get(1) ?: "",
        )

        // The notification of the service opens the browser again.
        assertEquals(
            "the service opens Explorer",
            "com.rpooi3.explorer.activity.BrowserActivity",
            BackgroundAudio.activityClass().name,
        )
    }

    // 2. Light theme and Dark theme ------------------------------------------------------------
    @Test
    fun theTheme_isonsTheNamesLightThemeAndDarkTheme() {
        assertEquals("Light theme", context.getString(R.string.setting_theme_light))
        assertEquals("Dark theme", context.getString(R.string.setting_theme_dark))

        val entries = context.resources.getStringArray(
            context.resources.getIdentifier(
                "setting_entries_theme", "array", context.packageName,
            ),
        )
        assertEquals("three of them", 3, entries.size)
        assertEquals("Light theme", entries[1])
        assertEquals("Dark theme", entries[2])
        // The values stay what they were: 1 system, 2 light, 3 dark — the numbers of
        // ExplorerTheme, which is where the theme is read.
        val theme = source("src/main/java/com/rpooi3/explorer/theme/ExplorerTheme.kt")
        assertTrue("system is 1", theme.contains("THEME_SYSTEM = \"1\""))
        assertTrue("light is 2", theme.contains("THEME_LIGHT = \"2\""))
        assertTrue("dark is 3", theme.contains("THEME_DARK = \"3\""))
        // The old words are gone from what the screen says.
        val strings = source("src/main/res/values/strings.xml")
        assertFalse(
            "\"Day view\" is not offered any more",
            Regex("<string name=\"setting_theme_[a-z]*day\">Day view<").containsMatchIn(strings),
        )
    }

    // 3. every setting belongs to a profile ----------------------------------------------------
    @Test
    fun everySetting_isStoredPerProfile() {
        // The first profile keeps the plain file, so that an update finds its settings.
        assertEquals(
            ProfilePrefs.defaultName(context),
            ProfilePrefs.nameOf(context, null),
        )
        val second = ProfilePrefs.defaultName(context) + ProfilePrefs.SEPARATOR + "2"
        assertNotEquals(
            "a second profile has its own file",
            ProfilePrefs.nameOf(context, null),
            second,
        )
        assertTrue("marked as the second", second.endsWith(ProfilePrefs.SEPARATOR + "2"))
        assertEquals(
            "and the file of a profile is that of its id",
            second,
            ProfilePrefs.settingsFor(context, "2").let { _ ->
                ProfilePrefs.defaultName(context) + ProfilePrefs.SEPARATOR + "2"
            },
        )

        // What one profile sets, the other does not see.
        ProfilePrefs.settingsFor(context, "2").edit().putString("search_engine", "duckduckgo").commit()
        assertFalse(
            "the first profile is untouched",
            prefs.contains("search_engine"),
        )
        assertEquals(
            "and the second has it",
            "duckduckgo",
            ProfilePrefs.settingsFor(context, "2").getString("search_engine", ""),
        )

        // A profile that is made begins with the settings the browser has (as Chrome does).
        prefs.edit().putString("theme_setting", "3").putBoolean("java", false).commit()
        ProfilePrefs.settingsFor(context, "3").edit().clear().commit()
        ProfilePrefs.copySettings(context, "3")
        assertEquals(
            "the settings were copied",
            "3",
            ProfilePrefs.settingsFor(context, "3").getString("theme_setting", ""),
        )
        assertFalse(
            "with their values",
            ProfilePrefs.settingsFor(context, "3").getBoolean("java", true),
        )
        ProfilePrefs.deleteAllOf(context, "3")

        // The settings screens of a profile are the settings of that profile: the fragment gives
        // its preference manager the file of the profile in front.
        val fragment = source("src/main/java/com/rpooi3/explorer/preferences/BasePreferenceFragment.java")
        assertTrue(
            "the screens read and write the profile's file",
            fragment.contains("ProfilePrefs.nameOf(requireContext(), null)"),
        )
        assertTrue(
            "set on the manager",
            fragment.contains("manager.setSharedPreferencesName("),
        )
        // A new profile copies them, so nothing of the browser's own code may still reach for the
        // file of the device: the count of direct reads is down to the one place that owns it.
        val direct = Regex("PreferenceManager\\.getDefaultSharedPreferences\\(")
        val hits = File("src/main/java").walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
            .sumOf { direct.findAll(it.readText()).count() }
        assertEquals(
            "the profile code and the application-wide updater name the device-wide file",
            6,
            hits,
        )
    }

    // 4. the button of Explorer stays on the store ---------------------------------------------
    @Test
    fun theStore_installsThroughTheBridge_thatIsLeftOfIt() {
        // 18.1.0 beta (item 6) removed every injection from the store, item 4 of beta 9 included:
        // what installs now is the store's *own* button, through the bridge of the browser.
        val script = StoreHook.script(context, "a".repeat(32), "t")
        assertTrue("the store is told it is Chrome", script.contains("window.chrome.webstore"))
        assertTrue("with the call it makes", script.contains("beginInstallWithManifest3"))
        assertTrue("handed to the installer", script.contains("installFromStore"))
        assertTrue("and the install is refused twice", script.contains("INSTALLED"))

        val hook = source("src/main/java/com/rpooi3/explorer/extension/StoreHook.kt")
        assertFalse("no button of Explorer's own any more", hook.contains("explorer-install-host"))
        assertFalse("and nothing is looked after on a timer", hook.contains("postDelayed"))
        // A page that is not one of the store is left alone.
        assertFalse(StoreHook.isExtensionPage("https://example.com/"))
    }

    // 5. the store reads on a phone ------------------------------------------------------------
    @Test
    fun theStore_isMobileFriendly_byWebSettings() {
        val web = source("src/main/java/com/rpooi3/explorer/view/NinjaWebView.java")
        val hook = source("src/main/java/com/rpooi3/explorer/extension/StoreHook.kt")

        // 18.1.0 beta (item 6): nothing is injected into the page.
        assertFalse("no style sheet", hook.contains("var MOBILE = ["))
        assertFalse("no zoom", hook.contains("style.zoom"))
        assertFalse("no shadow-root walk", hook.contains("function all(root, out, depth)"))

        // The desktop agent of the store is a WebSettings matter, and so is the width it is
        // given: wide viewport off, so the store's own responsive layout is the mobile one.
        assertTrue("the store is its own branch", web.contains("if (store) {"))
        assertTrue("with the agent of Chrome on Linux", web.contains("webSettings.setUserAgentString(ChromeUserAgent.store(defaultAgent))"))
        // 18.2.0 beta (item 2): the width is what "Desktop site" says now.
        assertTrue("and the width of the page", web.contains("webSettings.setUseWideViewPort(desktop)"))
        assertTrue("showing the whole page", web.contains("webSettings.setLoadWithOverviewMode(false)"))
        // The forced desktop layout of the store is gone.
        assertFalse(
            "the store is no longer forced into the desktop layout",
            web.contains("final boolean desktop = store ||"),
        )
    }

    // 6. a private tab carries no extensions in its menu ---------------------------------------
    @Test
    fun aPrivateTab_hasNoExtensionsInTheMenu_andAWholeZoomTile() {
        val activity = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        assertTrue(
            "the extensions of the menu are asked about the tab",
            activity.contains("ninjaWebView.isPrivateTab()"),
        )
        assertTrue(
            "and hidden on a private one",
            activity.contains(".setVisible(!privateTab)") || activity.contains("setVisible(!privateTab)"),
        )
        assertTrue(
            "as are their settings",
            activity.contains(".setEnabled(!privateTab)") || activity.contains("setEnabled(!privateTab)"),
        )
        // The zoom tile takes the whole row on its own.
        val sheet = source("src/main/java/com/rpooi3/explorer/view/MainMenuSheet.kt")
        assertTrue("one tile fills the row", sheet.contains("bg_menu_tile_single"))
    }

    // 7. the preset color schemes of Chrome -----------------------------------------------------
    @Test
    fun theColors_areThePresetColorSchemesOfChrome() {
        // Chromium's thirteen: the seeds, the variants, and the order of
        // kDynamicCustomizeChromeColors.
        val expected = listOf(
            Triple(2, android.graphics.Color.rgb(140, 171, 228), false),
            Triple(3, android.graphics.Color.rgb(140, 171, 228), true),
            Triple(4, android.graphics.Color.rgb(136, 136, 136), true),
            Triple(5, android.graphics.Color.rgb(38, 166, 154), false),
            Triple(6, android.graphics.Color.rgb(0, 255, 0), false),
            Triple(7, android.graphics.Color.rgb(135, 186, 129), true),
            Triple(8, android.graphics.Color.rgb(250, 223, 115), false),
            Triple(9, android.graphics.Color.rgb(255, 128, 0), false),
            Triple(10, android.graphics.Color.rgb(252, 219, 201), true),
            Triple(11, android.graphics.Color.rgb(243, 178, 190), false),
            Triple(12, android.graphics.Color.rgb(243, 178, 190), true),
            Triple(13, android.graphics.Color.rgb(255, 0, 255), false),
            Triple(14, android.graphics.Color.rgb(229, 213, 252), false),
        )
        assertEquals("thirteen colors, as Chrome has them", 13, ColorSchemes.PRESETS.size)
        ColorSchemes.PRESETS.forEachIndexed { index, scheme ->
            val (id, seed, neutral) = expected[index]
            assertEquals("the id of the color at $index", id, scheme.id)
            assertEquals("the seed of the color at $index", seed, scheme.seed)
            assertEquals("the variant of the color at $index", neutral, scheme.neutral)
        }
        // The names are Chrome's names.
        assertEquals("Blue", context.getString(R.string.color_scheme_blue))
        assertEquals("Cool grey", context.getString(R.string.color_scheme_cool_grey))
        assertEquals("Grey", context.getString(R.string.color_scheme_grey))
        assertEquals("Aqua", context.getString(R.string.color_scheme_aqua))
        assertEquals("Green", context.getString(R.string.color_scheme_green))
        assertEquals("Viridian", context.getString(R.string.color_scheme_viridian))
        assertEquals("Citron", context.getString(R.string.color_scheme_citron))
        assertEquals("Orange", context.getString(R.string.color_scheme_orange))
        assertEquals("Apricot", context.getString(R.string.color_scheme_apricot))
        assertEquals("Rose", context.getString(R.string.color_scheme_rose))
        assertEquals("Pink", context.getString(R.string.color_scheme_pink))
        assertEquals("Fuchsia", context.getString(R.string.color_scheme_fuchsia))
        assertEquals("Violet", context.getString(R.string.color_scheme_violet))

        // The choice of the system is the default, and it overrides the theme of the system.
        assertNull("nothing is chosen at first", ColorSchemes.chosen(prefs))
        assertEquals(
            "and the row says so",
            context.getString(R.string.theme_colors_summary),
            ColorSchemes.summary(context, prefs),
        )
        val violet = ColorSchemes.byId(14)
        assertNotNull("Chrome's violet is there", violet)
        ColorSchemes.choose(prefs, violet)
        assertEquals("the choice is kept", 14, prefs.getInt(ColorScheme.KEY_COLOR, 0))
        assertEquals("and read back", violet?.id, ColorSchemes.chosen(prefs)?.id)
        assertTrue("with the variant", prefs.getBoolean(ColorScheme.KEY_NEUTRAL, false).not())
        assertTrue(
            "the row names it",
            ColorSchemes.summary(context, prefs).contains("Violet"),
        )
        ColorSchemes.choose(prefs, null)
        assertNull("and it can go back to the system", ColorSchemes.chosen(prefs))
        assertFalse("leaving nothing behind", prefs.contains(ColorScheme.KEY_COLOR))

        // 18.1.0 beta (item 2) put Chromium's two neutral swatches in front of the thirteen.
        assertEquals("the palette of the system", 0, ColorSchemes.byId(0)?.id)
        assertEquals("the greyscale", 1, ColorSchemes.byId(1)?.id)
        assertEquals("fifteen swatches", 15, ColorSchemes.ALL.size)
        assertNull("no other id is offered", ColorSchemes.byId(15))

        // The row is in Appearance, and the scheme paints the app over the theme of the system.
        val appearance = source("src/main/res/xml/preference_section_appearance.xml")
        assertTrue("Appearance has Colors", appearance.contains("android:key=\"theme_colors\""))
        val theme = source("src/main/java/com/rpooi3/explorer/theme/ExplorerTheme.kt")
        assertTrue("the color comes first", theme.contains("applyChosenColor(activity)"))
        assertTrue(
            "the seed becomes the palette",
            theme.contains("setContentBasedSource(scheme.seed)"),
        )
        val screen = source("src/main/java/com/rpooi3/explorer/preferences/ColorsScreen.kt")
        assertTrue("the picker is Chrome's", screen.contains("for (scheme in ColorSchemes.ALL)"))
        assertTrue("with a tick on the one in use", screen.contains("theme_colors_tick"))
        assertTrue("and a card for a color of your own", screen.contains("theme_colors_custom_card"))
    }

    // 8. a popup keeps its own shape and size ---------------------------------------------------
    @Test
    fun anExtensionPopup_keepsItsOwnShapeAndSize() {
        val scripts = source("src/main/java/com/rpooi3/explorer/extension/ExtensionScripts.kt")
        // The size the popup itself asks for, not the size of the window it was put in.
        assertTrue("the computed style is read", scripts.contains("getComputedStyle"))
        assertTrue(
            "the width it asks for and its minimum",
            scripts.contains("px(style[which])") && scripts.contains("style[\"min-\" + which]"),
        )
        assertTrue("and it is measured", scripts.contains("asked(root, \"width\")"))
        val popup = source("src/main/java/com/rpooi3/explorer/extension/ExtensionPopup.kt")
        // And the window it is given can be as large as the shape asks for.
        assertTrue("the window is not a fixed rectangle", popup.contains("screen - "))
        assertTrue("it is measured again once the page settled", popup.contains("+400") || popup.contains("400"))
        assertTrue("and once more after that", popup.contains("1500"))
        assertTrue("the width has a floor", popup.contains("180"))
    }
}
