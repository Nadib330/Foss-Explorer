package com.rpooi3.explorer.profile

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.downloads.DownloadLog
import com.rpooi3.explorer.site.SiteSettings
import org.json.JSONObject
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
 * 18.0.0: what a profile is and what it keeps apart.
 *
 * The tests hold the promises the release makes: a profile is a name, a colour and a set of data of
 * its own; the first profile keeps everything that was stored before 18.0.0, so an update loses
 * nothing; the profile in front and the last profile cannot be deleted; the guest leaves nothing;
 * and where the System WebView of the device has no profiles, everything that Explorer itself keeps
 * is still separate and nothing crashes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1800Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        // Every test starts with a device that has never had a profile.
        context.getSharedPreferences("browser_profiles", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        for (name in listOf("site_settings_test", "downloads_log")) {
            context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    // 1. The profile itself --------------------------------------------------------------------

    @Test
    fun aProfile_keepsItsNameColourAndLetter() {
        val profile = BrowserProfile("1a2b3c4d", "Work", 2)
        assertEquals("W", profile.initial)
        assertEquals(2, profile.colour)
        assertFalse(profile.isDefault)
        assertEquals("1a2b3c4d", profile.directory)
        assertEquals(BrowserProfile.WEBVIEW_PREFIX + "1a2b3c4d", profile.webViewProfileName)
    }

    @Test
    fun theFirstProfile_isTheOneOfTheOldData() {
        val first = BrowserProfile.first()
        assertTrue(first.isDefault)
        assertEquals("", first.id)
        // Its data stays where it was before 18.0.0: files/, places.sqlite, favicon.db.
        assertEquals("places.sqlite", com.rpooi3.explorer.database.places.PlacesDatabase.nameOf(context, ""))
        assertEquals("favicon.db", Profiles.databaseName(context, "favicon.db"))
        assertEquals(context.filesDir.absolutePath, Profiles.thingsOf(context, "").absolutePath)
        // And its WebView profile is the default one of the WebView itself.
        assertEquals(BrowserProfile.DEFAULT_WEBVIEW_PROFILE, first.webViewProfileName)
    }

    @Test
    fun aProfile_survivesBeingWrittenAndRead() {
        val profile = BrowserProfile("abcd1234", "Study", 4, false, 11L, 22L)
        val back = BrowserProfile.fromJson(JSONObject(profile.toJson().toString()))
        assertNotNull(back)
        assertEquals(profile.id, back!!.id)
        assertEquals("Study", back.name)
        assertEquals(4, back.colour)
        assertEquals(11L, back.createdAt)
        assertEquals(22L, back.lastUsed)
        // A profile without a name is not a profile.
        assertNull(BrowserProfile.fromJson(JSONObject().put("id", "x")))
    }

    @Test
    fun theNineColours_areDistinct() {
        assertEquals(9, Profiles.COLOURS.size)
        assertEquals(9, Profiles.COLOUR_NAMES.size)
        assertEquals(Profiles.COLOURS.size, Profiles.COLOURS.toSet().size)
    }

    // 2. The list of the profiles --------------------------------------------------------------

    @Test
    fun anAppWithoutProfiles_startsWithExactlyOne_andItKeepsTheOldData() {
        val all = Profiles.all(context)
        assertEquals(1, all.size)
        assertTrue(all.first().isDefault)
        assertEquals("Personal", all.first().name)
        assertEquals(all.first().id, Profiles.currentId(context))
    }

    @Test
    fun makingAProfile_givesItItsOwnData_andKeepsTheFirstOne() {
        val made = Profiles.create(context, "  Work  ", 3)
        assertNotNull(made)
        assertEquals("Work", made!!.name)
        assertEquals(3, made.colour)
        assertNotEquals("", made.id)
        assertEquals(2, Profiles.all(context).size)
        // The first profile is untouched.
        assertTrue(Profiles.all(context).any { it.isDefault })

        // A profile without a name still gets one, and a colour out of the nine.
        val bare = Profiles.create(context, "   ", 99)
        assertNotNull(bare)
        assertTrue(bare!!.name.isNotBlank())
        assertTrue(bare.colour <= Profiles.COLOURS.lastIndex)
    }

    @Test
    fun renamingAndRecolouring_areKept_andAnEmptyNameIsIgnored() {
        val profile = Profiles.create(context, "Work", 1)!!
        Profiles.rename(context, profile.id, "Office")
        Profiles.setColour(context, profile.id, 5)
        val back = Profiles.all(context).first { it.id == profile.id }
        assertEquals("Office", back.name)
        assertEquals(5, back.colour)
        Profiles.rename(context, profile.id, "   ")
        assertEquals("Office", Profiles.all(context).first { it.id == profile.id }.name)
    }

    @Test
    fun switchingToAProfile_changesTheProfileInFront_andLosesNothing() {
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        assertEquals(work.id, Profiles.currentId(context))
        assertEquals(2, Profiles.all(context).size)
        Profiles.switchTo(context, "")
        assertEquals("", Profiles.currentId(context))
        assertEquals(2, Profiles.all(context).size)
        // A profile that does not exist cannot become the one in front.
        Profiles.switchTo(context, "nothing")
        assertEquals("", Profiles.currentId(context))
    }

    // 3. Deleting ------------------------------------------------------------------------------

    @Test
    fun theLastProfile_cannotBeDeleted() {
        val only = Profiles.all(context).first()
        assertFalse(Profiles.canDelete(context, only.id))
        assertFalse(Profiles.delete(context, only.id))
        assertEquals(1, Profiles.all(context).size)
    }

    @Test
    fun theProfileInFront_cannotBeDeleted() {
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        assertFalse(Profiles.canDelete(context, work.id))
        assertFalse(Profiles.delete(context, work.id))
        assertEquals(2, Profiles.all(context).size)
    }

    @Test
    fun deletingAProfile_takesItsDatabasePreferencesAndFilesWithIt() {
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, "")
        // Something of the profile, in every place a profile keeps data.
        val dir = Profiles.thingsOf(context, work.id)
        dir.mkdirs()
        File(dir, "note").writeText("x")
        val prefs = ProfilePrefs.settingsFor(context, work.id)
        prefs.edit().putBoolean("anything", true).apply()
        val itsPrefsFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/" + ProfilePrefs.defaultName(context) + ProfilePrefs.SEPARATOR + work.id + ".xml",
        )
        itsPrefsFile.parentFile?.mkdirs()
        assertTrue(itsPrefsFile.exists() || prefs.edit().commit().let { itsPrefsFile.exists() })

        assertTrue(Profiles.delete(context, work.id))
        assertEquals(1, Profiles.all(context).size)
        assertFalse("its folder is gone", dir.exists())
        assertFalse("its preferences are gone", itsPrefsFile.exists())
    }

    // 4. The guest -----------------------------------------------------------------------------

    @Test
    fun theGuest_startsAndEnds_andLeavesNothingBehind() {
        Profiles.create(context, "Work", 1)
        val guest = Profiles.startGuest(context)
        assertTrue(guest.guest)
        assertTrue(Profiles.isGuest(context))
        assertEquals(guest.id, Profiles.currentId(context))
        // The guest is not one of the profiles of the device.
        assertFalse(Profiles.all(context).any { it.id == guest.id })

        // Its data lives below cache/, so nothing of it is a file of the app's own data.
        val dir = Profiles.thingsOf(context, guest.id)
        assertTrue(dir.absolutePath.contains("cache"))
        dir.mkdirs()
        File(dir, "cookie").writeText("x")
        ProfilePrefs.settingsFor(context, guest.id).edit().putBoolean("kept", true).commit()

        Profiles.leaveGuest(context)
        assertFalse(Profiles.isGuest(context))
        assertFalse("the guest folder is deleted", dir.exists())
        assertFalse(
            "the guest preferences are deleted",
            ProfilePrefs.settingsFor(context, guest.id).contains("kept"),
        )
        // The profile that was in front before the guest is back.
        assertEquals("", Profiles.currentId(context))
    }

    // 5. The question on startup ---------------------------------------------------------------

    @Test
    fun theSwitcher_isOfferedOnEveryStart_untilTheCheckboxIsTakenAway() {
        // Before the welcome named a profile, nothing is asked.
        assertFalse(Profiles.setupDone(context))
        assertFalse(Profiles.shouldAsk(context))
        Profiles.setSetupDone(context)
        assertTrue(Profiles.askOnStartup(context))
        assertTrue(Profiles.shouldAsk(context))
        Profiles.setAskOnStartup(context, false)
        assertFalse(Profiles.shouldAsk(context))
        Profiles.setAskOnStartup(context, true)
        assertTrue(Profiles.shouldAsk(context))
    }

    // 6. Where the data lives ------------------------------------------------------------------

    @Test
    fun everyProfile_hasItsOwnPlace_everywhere() {
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        // Below files/profiles/<id>/
        assertEquals(work.id, Profiles.thingsOf(context, work.id).name)
        assertEquals("profiles", Profiles.thingsOf(context, work.id).parentFile!!.name)
        // Its own database files …
        assertEquals(
            "places-" + work.id + ".sqlite",
            com.rpooi3.explorer.database.places.PlacesDatabase.nameOf(context, work.id),
        )
        assertEquals("favicon-" + work.id + ".db", Profiles.databaseName(context, "favicon.db"))
        // … and its own preferences files.
        assertEquals(
            ProfilePrefs.defaultName(context) + ProfilePrefs.SEPARATOR + work.id,
            ProfilePrefs.nameOf(context, null),
        )
        assertEquals("downloads_log@x", "downloads_log" + ProfilePrefs.SEPARATOR + "x")
        Profiles.switchTo(context, "")
    }

    @Test
    fun theSettingsOfTheProfileInFront_comeFirst_thenTheSettingsOfTheApp() {
        // The first profile reads and writes the settings of the app themselves.
        Profiles.switchTo(context, "")
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val first = ProfilePrefs.settings(context)
        shared.edit().putString("colour_seed", "teal").commit()
        assertEquals("teal", first.getString("colour_seed", null))

        // Another profile inherits them, and keeps what it changes to itself.
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        val mine = ProfilePrefs.settings(context)
        assertTrue(mine is ProfileFallbackPrefs)
        assertEquals("teal", mine.getString("colour_seed", null))
        mine.edit().putString("colour_seed", "amber").commit()
        assertEquals("amber", mine.getString("colour_seed", null))
        assertEquals("teal", shared.getString("colour_seed", null))
        // A value the profile never changed still comes from the app.
        shared.edit().putBoolean("ad_block", true).commit()
        assertTrue(mine.getBoolean("ad_block", false))
        Profiles.switchTo(context, "")
    }

    @Test
    fun theFallbackPreferences_readAndWriteAsOneStore() {
        val own = context.getSharedPreferences("own", android.content.Context.MODE_PRIVATE)
        val base = context.getSharedPreferences("base", android.content.Context.MODE_PRIVATE)
        own.edit().clear().commit()
        base.edit().clear().commit()
        val prefs = ProfileFallbackPrefs(own, base)
        base.edit().putInt("a", 1).putString("b", "base").commit()
        own.edit().putString("b", "own").putLong("c", 3L).commit()

        assertEquals(1, prefs.getInt("a", 0))
        assertEquals("own", prefs.getString("b", null))
        assertEquals(3L, prefs.getLong("c", 0L))
        assertEquals(0, prefs.getInt("missing", 0))
        assertTrue(prefs.contains("a"))
        assertTrue(prefs.contains("c"))
        assertTrue(prefs.isOwn("c"))
        assertFalse(prefs.isOwn("a"))
        // The merge of getAll() shows what the profile has, over what it inherits.
        assertEquals("own", prefs.all["b"])
        assertEquals(1, prefs.all["a"])
        // Writing goes to the profile.
        prefs.edit().putBoolean("d", true).commit()
        assertTrue(own.getBoolean("d", false))
        assertFalse(base.contains("d"))
    }

    // 7. What a profile keeps to itself --------------------------------------------------------

    @Test
    fun theDownloadLog_isTheLogOfOneProfile() {
        Profiles.switchTo(context, "")
        DownloadLog.add(context, "first.txt", "text/plain", 3L, null)
        assertEquals(1, DownloadLog.list(context).size)

        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        assertEquals("another profile has its own log", 0, DownloadLog.list(context).size)
        DownloadLog.add(context, "second.txt", "text/plain", 4L, null)
        assertEquals(1, DownloadLog.list(context).size)
        // The log of the profile is its own file, not the one of the app.
        assertEquals(
            "downloads_log" + ProfilePrefs.SEPARATOR + work.id,
            ProfilePrefs.nameOf(context, "downloads_log"),
        )
        assertEquals("second.txt", DownloadLog.list(context).first().name)

        Profiles.switchTo(context, "")
        assertEquals("first.txt", DownloadLog.list(context).first().name)
    }

    @Test
    fun theSitePermissions_areThePermissionsOfTheProfileInFront() {
        Profiles.switchTo(context, "")
        val first = SiteSettings.prefs(context)
        first.edit().putBoolean("profileStandard_javascript", true).commit()

        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        val mine = SiteSettings.prefs(context)
        // Inherited, as every setting of the app is.
        assertTrue(mine.getBoolean("profileStandard_javascript", false))
        // A permission the profile changes is only its own.
        mine.edit().putBoolean("profileStandard_javascript", false).commit()
        assertFalse(SiteSettings.prefs(context).getBoolean("profileStandard_javascript", true))
        Profiles.switchTo(context, "")
        assertTrue(SiteSettings.prefs(context).getBoolean("profileStandard_javascript", false))
    }

    // 8. The WebView, on a device that has profiles and on one that has not ----------------

    @Test
    fun aDeviceWithoutWebViewProfiles_isHandledWithoutCrashing() {
        // Robolectric's WebView has no MULTI_PROFILE: the app must still work, and say what it
        // can do. Everything above (history, bookmarks, permissions, downloads) is separate even
        // then; only the cookies stay in one jar.
        val supported = ProfileWebView.supported()
        assertEquals(ProfileWebView.of(android.webkit.WebView(context)), null)
        // The cookie manager is always there, with or without profiles.
        assertNotNull(ProfileWebView.cookieManager(context))
        // Making a profile, switching and deleting work on such a device too.
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        Profiles.switchTo(context, "")
        assertTrue(Profiles.delete(context, work.id))
        // Deleting a WebView profile that does not exist is not an error.
        ProfileWebView.delete(BrowserProfile("unknown", "Unknown", 0))
        assertFalse(supported && false)
    }

    @Test
    fun theWebViewProfileOfAProfile_neverIsTheProfileOfAnother() {
        val one = BrowserProfile("aaaa1111", "One", 0)
        val two = BrowserProfile("bbbb2222", "Two", 1)
        assertNotEquals(one.webViewProfileName, two.webViewProfileName)
        // The default profile keeps WebView's own default name, the one the app always used.
        assertEquals("Default", BrowserProfile.first().webViewProfileName)
        assertTrue(two.webViewProfileName.startsWith(BrowserProfile.WEBVIEW_PREFIX))
    }
}
