package com.rpooi3.explorer.activity

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Acceptance checks retained for the 18.5.1 Nightly UI and release changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release1840Test {

    private fun source(path: String): String = File(path).readText()

    @Test
    fun releaseMetadata_is1851Nightly() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("versionCode = 31"))
        assertTrue(gradle.contains("versionName = \"18.5.1-nightly\""))
    }

    @Test
    fun toolbarSpacing_isTight_andPlusControlsShareASquare() {
        val dimens = source("src/main/res/values/dimens.xml")
        val appbar = source("src/main/res/layout/include_appbar.xml")
        val home = source("src/main/res/layout/include_home.xml")
        val bottom = source("src/main/res/layout/include_bottombar.xml")
        val switcher = source("src/main/res/layout/include_tab_switcher.xml")
        assertTrue(dimens.contains("<dimen name=\"bar_button_size_compact\">40dp</dimen>"))
        assertTrue(appbar.contains("android:layout_marginStart=\"1dp\""))
        assertTrue(appbar.contains("android:layout_marginEnd=\"1dp\""))
        assertTrue(home.contains("android:layout_marginStart=\"0dp\""))
        assertTrue(bottom.contains("@dimen/new_tab_button_size"))
        assertTrue(switcher.contains("android:layout_width=\"@dimen/new_tab_button_size\""))
        assertTrue(switcher.contains("android:layout_height=\"@dimen/new_tab_button_size\""))
        assertTrue(switcher.contains("android:background=\"@drawable/bg_bottom_new_tab\""))
        assertTrue(source("src/main/res/drawable/bg_bottom_new_tab.xml").contains("android:radius=\"12dp\""))
    }

    @Test
    fun touchFeedback_restoresShapedRipples_forTheThreeRequestedControls() {
        val ripple = source("src/main/res/drawable/bg_ripple_small.xml")
        val appbar = source("src/main/res/layout/include_appbar.xml")
        val home = source("src/main/res/layout/include_home.xml")
        val bottom = source("src/main/res/layout/include_bottombar.xml")
        val switcher = source("src/main/res/layout/include_tab_switcher.xml")
        assertTrue(ripple.contains("android:radius=\"18dp\""))
        assertTrue("the top three-dot menu keeps the old borderless ripple", appbar.contains("@+id/button_menu") && appbar.contains("?attr/selectableItemBackgroundBorderless"))
        assertTrue("the home three-dot menu keeps the old borderless ripple", home.contains("@+id/home_menu") && home.contains("?attr/selectableItemBackgroundBorderless"))
        assertTrue("the home customization button keeps the old borderless ripple", home.contains("@+id/home_customize") && home.contains("?attr/selectableItemBackgroundBorderless"))
        assertTrue("the bottom plus keeps its shaped ripple", bottom.contains("@+id/button_newTab") && bottom.contains("@drawable/bg_bottom_new_tab"))
        assertTrue("the tab-switcher menu keeps its old borderless ripple", switcher.contains("@+id/tabs_menu") && switcher.contains("?attr/selectableItemBackgroundBorderless"))
        assertFalse("the shaped bottom-plus drawable is not replaced by the compact ripple", source("src/main/res/drawable/bg_bottom_new_tab.xml").contains("android:radius=\"18dp\""))
    }

    @Test
    fun newHome_hasFourShortcuts_withoutThemeTileAddCardOrFocusTile() {
        val page = source("src/main/res/layout/include_home_new.xml")
        val controller = source("src/main/java/com/rpooi3/explorer/home/HomePageView.kt")
        assertTrue(controller.contains("const val SHORTCUT_COUNT = 4"))
        assertFalse(page.contains("home_new_colours"))
        assertFalse(page.contains("home_new_colour_dots"))
        assertFalse(page.contains("home_new_focus_card"))
        assertFalse(controller.contains("createAddCard"))
        assertFalse(controller.contains("askForShortcut"))
        assertFalse(controller.contains("ColorSchemes.choose"))
    }

    @Test
    fun tabSwitcher_opensAndSelectsWithoutWholeScreenAnimation_butModesSlide() {
        val switcher = source("src/main/java/com/rpooi3/explorer/view/TabSwitcher.kt")
        val show = switcher.substring(switcher.indexOf("fun show()"), switcher.indexOf("fun hide(towards"))
        val hide = switcher.substring(switcher.indexOf("fun hide(towards"), switcher.indexOf("fun refresh()"))
        assertFalse(show.contains("scaleX(ZOOM)"))
        assertFalse(show.contains("setDuration(DURATION)"))
        assertFalse(hide.contains("setDuration(DURATION)"))
        assertTrue(switcher.contains("animateModeSwitch"))
        assertTrue(switcher.contains("MODE_CONTENT_DURATION"))
        assertTrue(switcher.contains("MODE_BAR_DURATION"))
    }

    @Test
    fun finishedApk_waitsForTheOpenAction() {
        val chip = source("src/main/java/com/rpooi3/explorer/downloads/DownloadChip.kt")
        val finished = chip.substring(chip.indexOf("fun finished("), chip.indexOf("/** The download did not arrive."))
        val listener = finished.indexOf("open.setOnClickListener")
        val openCall = finished.indexOf("openFile(activity, uri)")
        assertTrue("completion wires an explicit Open action", listener >= 0 && openCall > listener)
        val layout = source("src/main/res/layout/view_download_chip.xml")
        val frameStart = layout.indexOf("<FrameLayout")
        val frameEnd = layout.indexOf("</FrameLayout>", frameStart)
        val pause = layout.indexOf("@+id/download_chip_pause")
        assertTrue("pause is drawn inside the progress frame", pause > frameStart && pause < frameEnd)
    }

    @Test
    fun firstRunMakeItYours_offersHomeStyle_andNotificationActionIsRenamed() {
        val welcome = source("src/main/java/com/rpooi3/explorer/activity/WelcomeActivity.kt")
        val strings = source("src/main/res/values/strings.xml")
        assertTrue(welcome.contains("offerHomeStyle"))
        assertTrue(welcome.contains("HomePage.STYLE_CLASSIC"))
        assertTrue(strings.contains("<string name=\"welcome_notifications\">Allow notification</string>"))
        assertTrue(strings.contains("welcome_home_style_title"))
    }

    @Test
    fun dualHomePages_useOneCanonicalSelection_acrossFirstRunSettingsAndRendering() {
        val home = source("src/main/res/layout/include_home.xml")
        val newPage = source("src/main/res/layout/include_home_new.xml")
        val page = source("src/main/java/com/rpooi3/explorer/home/HomePage.kt")
        val controller = source("src/main/java/com/rpooi3/explorer/home/HomePageView.kt")
        val welcome = source("src/main/java/com/rpooi3/explorer/activity/WelcomeActivity.kt")
        val xml = source("src/main/res/xml/preference_section_home_custom.xml")
        val selector = source("src/main/java/com/rpooi3/explorer/preferences/HomeStylePreference.kt")
        assertTrue(home.contains("@layout/include_home_new"))
        assertTrue(home.contains("@+id/home_scroll"))
        assertTrue(newPage.contains("@+id/home_new_scroll"))
        assertTrue(page.contains("const val KEY_STYLE = \"home_style\""))
        assertTrue(controller.contains("HomePage.STYLE_NEW"))
        assertTrue(controller.contains("HomePage.STYLE_CLASSIC"))
        assertTrue(welcome.contains("HomePage.setStyle"))
        assertTrue(xml.contains("android:key=\"home_style\""))
        assertFalse(xml.contains("home_style_pick"))
        assertTrue(selector.contains("HomePage.setStyle"))
        assertTrue(selector.contains("refreshSelection"))
    }

    @Test
    fun about_separatesVersionInformationFromManualUpdateChecking() {
        val xml = source("src/main/res/xml/preference_section_about.xml")
        val screens = source("src/main/java/com/rpooi3/explorer/preferences/SettingsScreens.kt")
        assertTrue(xml.contains("android:key=\"about_app\""))
        assertTrue(xml.contains("android:key=\"about_updates\""))
        assertTrue(xml.contains("@string/about_check_updates_title"))
        assertTrue(xml.indexOf("about_app") < xml.indexOf("about_updates"))
        assertTrue(screens.contains("findPreference<Preference>(\"about_updates\")"))
        assertFalse(screens.substring(screens.indexOf("findPreference<Preference>(\"about_app\")"), screens.indexOf("fragment.findPreference<Preference>(\"about_updates\")")).contains("UpdateActivity.open"))
    }

    @Test
    fun updateInfoButton_opensAndroidApplicationInfo() {
        val update = source("src/main/java/com/rpooi3/explorer/update/UpdateActivity.kt")
        assertTrue(update.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue(update.contains("Uri.parse(\"package:\$packageName\")"))
        assertTrue(update.contains("openApplicationInfo"))
    }
}
