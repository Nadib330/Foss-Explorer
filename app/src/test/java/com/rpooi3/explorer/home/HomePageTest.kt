package com.rpooi3.explorer.home

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomePageTest {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun homeUrl_isRecognised() {
        assertTrue(HomePage.isHomeUrl("about:blank"))
        assertTrue(HomePage.isHomeUrl(" ABOUT:BLANK "))
        assertFalse(HomePage.isHomeUrl(null))
        assertFalse(HomePage.isHomeUrl(""))
        assertFalse(HomePage.isHomeUrl("https://example.org/about:blank"))
    }

    @Test
    fun freshTab_showsHome() {
        assertTrue(HomePage.isShowing(pendingUrl = null, committedUrl = null))
        assertTrue(HomePage.isShowing(pendingUrl = null, committedUrl = "about:blank"))
    }

    @Test
    fun openingAPage_hidesHomeBeforeThePageArrives() {
        // The web view still reports the blank page while the first bytes are on their way.
        assertFalse(HomePage.isShowing(pendingUrl = "https://example.org/", committedUrl = "about:blank"))
        assertFalse(HomePage.isShowing(pendingUrl = "cats", committedUrl = null))
    }

    @Test
    fun goingBackToHome_showsHomeWhileTheOldPageIsStillThere() {
        assertTrue(HomePage.isShowing(pendingUrl = "about:blank", committedUrl = "https://example.org/"))
    }

    @Test
    fun loadedPage_isNotHome() {
        assertFalse(HomePage.isShowing(pendingUrl = null, committedUrl = "https://example.org/"))
    }

    @Test
    fun startUrl_defaultsToHome() {
        assertEquals(HomePage.URL, HomePage.startUrl(prefs))

        prefs.edit().putString(HomePage.KEY_START_PAGE, "   ").commit()
        assertEquals(HomePage.URL, HomePage.startUrl(prefs))
    }

    @Test
    fun upgradeFrom001_replacesTheOldDefaultStartPage() {
        prefs.edit().clear().putString(HomePage.KEY_START_PAGE, "https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki").commit()

        HomePage.migrate(prefs)

        assertEquals(HomePage.URL, HomePage.startUrl(prefs))
    }

    @Test
    fun upgradeFrom001_keepsAStartPageTheUserChose() {
        prefs.edit().clear().putString(HomePage.KEY_START_PAGE, "https://example.org/").commit()

        HomePage.migrate(prefs)

        assertEquals("https://example.org/", HomePage.startUrl(prefs))
    }

    @Test
    fun migration_runsOnlyOnce() {
        prefs.edit().clear().commit()
        HomePage.migrate(prefs)

        // Choosing the wiki as start page later on is the user's decision and must stick.
        prefs.edit().putString(HomePage.KEY_START_PAGE, "https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki").commit()
        HomePage.migrate(prefs)

        assertEquals("https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki", HomePage.startUrl(prefs))
    }

    @Test
    fun startUrl_usesTheUsersChoice() {
        prefs.edit().putString(HomePage.KEY_START_PAGE, " https://example.org/ ").commit()
        assertEquals("https://example.org/", HomePage.startUrl(prefs))
    }
}
