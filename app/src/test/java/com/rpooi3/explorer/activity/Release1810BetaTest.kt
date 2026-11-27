package com.rpooi3.explorer.activity

import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.ChromeUserAgent
import com.rpooi3.explorer.extension.StoreHook
import com.rpooi3.explorer.preferences.RestartPrompt
import com.rpooi3.explorer.theme.ColorScheme
import com.rpooi3.explorer.theme.ColorSchemes
import com.rpooi3.explorer.theme.ColorSwatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * 18.1.0 beta: the six things asked for after 18.0.0 beta 9.
 *
 * 1. the sound of the browser moves to the foot of the site settings, 2. the swatch of the colors
 * is Chromium's three-tone circle instead of a gradient, 3. a color of the user's own can be added,
 * 4. the foot of the tab switcher is Material 3 with a rounded-square plus button, 5. the notice
 * that the theme needs a restart is shown in the settings, 6. the Chrome Web Store is left without
 * a line of injected CSS and gets its mobile layout from WebSettings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1810BetaTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    private fun source(path: String): String = File(path).readText()

    private fun idle() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    // 0. the version ----------------------------------------------------------------------------
    @Test
    fun theVersion_hasMovedOnTo1840() {
        // 18.3.0 beta is built on top of this one: what it says about itself is in its own test.
        val gradle = source("build.gradle.kts")
        assertTrue("versionCode 31", gradle.contains("versionCode = 31"))
        assertTrue("versionName 18.5.1-nightly", gradle.contains("versionName = \"18.5.1-nightly\""))
    }

    // 1. the sound moves to the site settings ---------------------------------------------------
    @Test
    fun theSound_isAtTheFootOfTheSiteSettings_aboveThePermissions() {
        val appearance = source("src/main/res/xml/preference_section_appearance.xml")
        assertFalse(
            "Appearance no longer carries the sound",
            appearance.contains("android:key=\"background_audio\""),
        )
        val site = source("src/main/res/xml/preference_section_site.xml")
        val sound = site.indexOf("android:key=\"background_audio\"")
        val permissions = site.indexOf("android:key=\"site_auto_revoke\"")
        assertTrue("the sound is in the site settings", sound > 0)
        assertTrue("and it is above the permissions tile", permissions > 0 && sound < permissions)
        assertTrue(
            "with the words of beta 9",
            site.contains("android:title=\"@string/media_background_title\""),
        )
        // The section it sits in is the last of the screen.
        assertTrue(
            "it is a section of its own",
            site.contains("android:key=\"appearance_sound_group\""),
        )
        // The switch is still the one the browser reads.
        assertEquals("background_audio", com.rpooi3.explorer.media.BackgroundAudio.KEY)
    }

    // 2. the swatch is Chromium's ---------------------------------------------------------------
    @Test
    fun theSwatch_isChromiumsThreeTones() {
        val blue = ColorSchemes.byId(2)
        assertNotNull("the blue of Chromium is there", blue)
        val colors = ColorSchemes.swatchColors(blue)
        assertEquals("three tones", 3, colors.size)
        // Tone 90 on top (the surfaces of the light browser), 80 at the bottom right, 40 at the
        // bottom left (its accents): the order of Chromium's own swatch.
        val top = luminance(colors[0])
        val right = luminance(colors[2])
        val left = luminance(colors[1])
        assertTrue("the top is the lightest: $top", top > right)
        assertTrue("and the bottom left the darkest: $left", right > left)
        // The default keeps Chromium's neutral colors instead of a seed.
        val system = ColorSchemes.swatchColors(null)
        assertEquals(0xFFE3E3E3.toInt(), system[0])
        assertEquals(0xFF0B57D0.toInt(), system[1])
        assertEquals(0xFFC7C7C7.toInt(), system[2])

        // And it is drawn as a circle of three pieces, not as a gradient.
        val swatch = source("src/main/java/com/rpooi3/explorer/theme/ColorSwatch.kt")
        assertTrue("the top half", swatch.contains("180f + seam"))
        assertTrue("the bottom left", swatch.contains("90f + seam"))
        assertTrue("the bottom right", swatch.contains("floatArrayOf(seam, quarter"))
        val screen = source("src/main/java/com/rpooi3/explorer/preferences/ColorsScreen.kt")
        assertFalse("no gradient any more", screen.contains("GradientDrawable.Orientation.LEFT_RIGHT"))
        assertTrue("the swatch of Chromium is used", screen.contains("ColorSwatch(ColorSchemes.swatchColors(scheme))"))
        assertTrue("in a card of its own", screen.contains("colorSurfaceContainerHighest"))
        assertTrue("with a tick in the corner", screen.contains("Gravity.TOP or Gravity.END"))
    }

    /** The three pieces really are where Chromium puts them, with a seam between them. */
    @Test
    fun theSwatch_drawsItsThreeTones() {
        val drawable = ColorSwatch(intArrayOf(Color.RED, Color.GREEN, Color.BLUE))
        val arcs = drawable.arcs()
        assertEquals("three pieces", 3, arcs.size)
        // The top half: from nine o'clock onwards, a half circle.
        assertEquals(180f, arcs[0][0], 3f)
        assertEquals(180f, arcs[0][1], 6f)
        assertEquals("the surfaces of the light browser", Color.RED, arcs[0][2].toInt())
        // The bottom left: from six o'clock onwards, a quarter.
        assertEquals(90f, arcs[1][0], 3f)
        assertEquals(90f, arcs[1][1], 5f)
        assertEquals("the accents", Color.GREEN, arcs[1][2].toInt())
        // The bottom right: from three o'clock onwards, a quarter.
        assertEquals(0f, arcs[2][0], 3f)
        assertEquals(90f, arcs[2][1], 5f)
        assertEquals("the tone between them", Color.BLUE, arcs[2][2].toInt())
        // The pieces do not touch: that is the seam the card shows through.
        val swept = arcs.sumOf { it[1].toDouble() }
        assertTrue("the seam is there", swept < 360.0)
        assertTrue("but it is a hairline", 360.0 - swept < 12.0)
        // And the seam stays a hairline on any size of circle.
        assertTrue("small", drawable.seam(28f) <= 2.4f)
        assertTrue("large", drawable.seam(400f) >= 0.8f)
    }

    private fun luminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    // 3. a color of the user's own --------------------------------------------------------------
    @Test
    fun aColorOfTheUsersOwn_canBeAdded_andIsUsed() {
        val seed = Color.HSVToColor(floatArrayOf(280f, 0.55f, 1f))
        val custom = ColorSchemes.customScheme(seed)
        assertTrue("it is a color of its own", custom.isCustom)
        assertFalse("and not one of Chromium's", custom.isSystem)

        ColorSchemes.choose(prefs, custom)
        val chosen = ColorSchemes.chosen(prefs)
        assertNotNull("it is the color in use", chosen)
        assertTrue("a color of the user's own", chosen!!.isCustom)
        assertEquals("kept as its seed", seed, chosen.seed)
        assertTrue("marked as such", prefs.getBoolean("theme_color_custom_shown", false).not())
        assertEquals(
            "the preference of it",
            ColorSchemes.CUSTOM_ID,
            prefs.getInt(ColorScheme.KEY_COLOR, 99),
        )
        assertTrue("the seed is kept", prefs.contains(ColorScheme.KEY_CUSTOM))
        assertEquals(
            "and the row names it",
            context.getString(R.string.theme_colors_chosen, context.getString(R.string.theme_colors_custom)),
            ColorSchemes.summary(context, prefs),
        )
        // It paints the browser exactly as a preset does: the seed becomes the palette.
        assertTrue(
            source("src/main/java/com/rpooi3/explorer/theme/ExplorerTheme.kt")
                .contains("setContentBasedSource(scheme.seed)"),
        )

        // And a preset can be chosen over it again.
        ColorSchemes.choose(prefs, ColorSchemes.byId(14))
        assertFalse("the color of the user's own is gone", prefs.contains(ColorScheme.KEY_CUSTOM))
        assertNotEquals(ColorSchemes.CUSTOM_ID, prefs.getInt(ColorScheme.KEY_COLOR, 99))

        // The picker offers it: the pencil card, and the two sliders behind it.
        val screen = source("src/main/java/com/rpooi3/explorer/preferences/ColorsScreen.kt")
        assertTrue("a card for it", screen.contains("theme_colors_custom_card"))
        assertTrue("opened with the pencil", screen.contains("R.drawable.icon_edit"))
        assertTrue("a hue", screen.contains("theme_colors_hue"))
        assertTrue("a strength", screen.contains("theme_colors_saturation"))
        assertTrue("and a preview of what they make", screen.contains("theme_colors_preview"))
        assertEquals("Custom color", context.getString(R.string.theme_colors_custom_title))
        assertEquals("Use this color", context.getString(R.string.theme_colors_use))
    }

    // 4. the foot of the tab switcher -----------------------------------------------------------
    @Test
    fun theFootOfTheTabSwitcher_isMaterial3_andThePlusIsASquare() {
        val layout = source("src/main/res/layout/include_tab_switcher.xml")
        // The pill: a filled Material 3 surface, without the hairline of beta 6.
        assertTrue("the pill is filled", layout.contains("app:cardBackgroundColor=\"?attr/colorSurfaceContainerHigh\""))
        // 18.2.0 beta (item 1): a rounded rectangle of 16 dp now.
        assertTrue("a rounded rectangle", layout.contains("app:cardCornerRadius=\"16dp\""))
        assertFalse("no edge drawn round it", layout.contains("app:strokeColor=\"?attr/colorOutline\""))
        // The plus: a 40 × 40 rounded square with 12 dp corners, shared with the bottom bar.
        val fab = layout.substring(layout.indexOf("@+id/tabs_fab_card"))
        assertTrue("a rounded square", fab.substring(0, 600).contains("app:cardCornerRadius=\"12dp\""))
        assertTrue("as wide as the toolbar's", fab.substring(0, 600).contains("android:layout_width=\"@dimen/new_tab_button_size\""))
        assertTrue("and as tall", fab.substring(0, 600).contains("android:layout_height=\"@dimen/new_tab_button_size\""))
        // 18.2.0 beta (item 1): on a surface that can be told apart from the page.
        assertTrue("on a surface of its own", fab.substring(0, 700).contains("?attr/colorSurfaceContainerHighest"))
        // The same shape as the plus of the bottom bar (bg_bottom_new_tab: 12 dp).
        assertTrue(
            "the toolbar's plus is the same shape",
            source("src/main/res/drawable/bg_bottom_new_tab.xml").contains("android:radius=\"12dp\""),
        )
    }

    // 5. the restart is asked for where the theme was changed ------------------------------------
    @Test
    fun theRestart_isAskedForInTheSettings() {
        // The notice lives in the settings now, not on the browsing screen.
        val settings = source("src/main/java/com/rpooi3/explorer/fragment/Fragment_settings.java")
        assertTrue("the settings show it", settings.contains("RestartPrompt.show(getActivity())"))
        assertFalse(
            "and no longer mark it for the browsing screen",
            settings.contains("putInt(\"restart_changed\", 1)"),
        )
        val browser = source("src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java")
        assertFalse(
            "which no longer asks when it comes back to the front",
            browser.contains("if (1 == sp.getInt(\"restart_changed\", 1))"),
        )
        // The colors of Appearance ask for it too, where they are chosen.
        assertTrue(
            "the colors ask on the spot",
            source("src/main/java/com/rpooi3/explorer/preferences/ColorsScreen.kt")
                .contains("RestartPrompt.show(fragment.activity)"),
        )

        // And it is a real notice with a real way out.
        val activity = Robolectric.buildActivity(Settings_Activity::class.java).setup().get()
        RestartPrompt.show(activity)
        idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue("it is shown", dialog.isShowing)
        assertEquals(
            context.getString(R.string.restart_notice_title),
            dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString(),
        )
        assertEquals("Restart Explorer", context.getString(R.string.restart_notice_title))
        val buttons = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals("Restart", buttons.text.toString())
        dialog.dismiss()
    }

    // 6. the store, without a line of CSS --------------------------------------------------------
    @Test
    fun theStore_isMobileFriendly_byWebSettingsOnly() {
        // Nothing is injected into the store's page any more: no style sheet, no zoom, no button.
        val hook = source("src/main/java/com/rpooi3/explorer/extension/StoreHook.kt")
        for (banned in listOf("style.zoom", "var MOBILE = [", "explorer-install-host", "data-explorer-hidden", "function relabel(", "MutationObserver")) {
            assertFalse("the store's page is not touched: $banned", hook.contains(banned))
        }
        // What is left is the bridge the store's own install button calls.
        val script = StoreHook.script(context, "a".repeat(32), "t")
        assertTrue("beginInstallWithManifest3", script.contains("beginInstallWithManifest3"))
        assertTrue("installFromStore", script.contains("installFromStore"))
        assertTrue("webstorePrivate", script.contains("window.chrome.webstorePrivate"))

        // The layout comes from WebSettings: the desktop agent of the store, and the phone's width.
        val agent = ChromeUserAgent.store("Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
        assertTrue("Chrome on Linux", agent.contains("X11; Linux x86_64"))
        assertTrue("with the brand of this browser", agent.contains("Explorer/1.0"))
        assertTrue("and the engine of the device", agent.contains("Chrome/131.0.0.0"))

        val web = source("src/main/java/com/rpooi3/explorer/view/NinjaWebView.java")
        assertTrue("the store has a branch of its own", web.contains("if (store) {"))
        assertTrue("the desktop agent", web.contains("ChromeUserAgent.store(defaultAgent)"))
        assertTrue("the width of the page", web.contains("setUseWideViewPort(desktop)"))
        assertTrue("showing all of it", web.contains("setLoadWithOverviewMode(desktop)"))
        assertFalse(
            "and no forced desktop mode",
            web.contains("final boolean desktop = store ||"),
        )
        // The platform it is told it is running on is Linux, as its agent says.
        assertEquals("Linux", ChromeUserAgent.platformOf(true, false))
    }
}
