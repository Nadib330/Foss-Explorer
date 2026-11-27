package com.rpooi3.explorer.profile

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
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
import org.robolectric.annotation.Config
import java.io.File

/**
 * 18.0.0 beta 2: what the second beta of the profiles fixes and adds.
 *
 *  - the crash when *Profiles* was tapped in the settings (`ActivityNotFoundException`: an
 *    `<intent>` with a target class and no package);
 *  - the switcher asking again and again after a switch (the switch now answers the question of
 *    the start, once);
 *  - a picture for every profile, chosen from the user's files, and kept in the profile's folder;
 *  - the profile in front on the bar, left of the three-dot menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1800Beta2Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        context.getSharedPreferences("browser_profiles", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        Profiles.cacheCurrent(context)
    }

    // 1. The crash of the settings row ---------------------------------------------------------

    @Test
    fun theSettingsRowOfProfiles_opensTheScreen_andCarriesNoIntentOfItsOwn() {
        val xml = File("src/main/res/xml/preference_setting.xml").readText()
        assertTrue("the row is there", xml.contains("section_profiles"))
        // An <intent> with only a target class has no package: Preference.performClick then threw
        // ActivityNotFoundException, which is what crashed the app.
        assertFalse("no <intent> on the row", xml.contains("<intent"))
        // The screen it opens exists and can be started.
        val intent = Intent(context, ProfilesActivity::class.java)
        assertNotNull(
            "the screen of the profiles is registered",
            context.packageManager.resolveActivity(intent, 0),
        )
    }

    @Test
    fun theScreenOfTheProfiles_opens_andListsEveryProfile() {
        Profiles.create(context, "Work", 1)
        Profiles.create(context, "School", 2)
        Robolectric.buildActivity(ProfilesActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val list = activity.findViewById<LinearLayout>(R.id.profiles_list)
            // Three profiles, and the note that says what this device can keep apart.
            assertTrue("every profile is listed", list.childCount >= 3)
            val texts = ArrayList<String>()
            fun collect(view: View) {
                if (view is android.widget.TextView) texts.add(view.text.toString())
                if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            collect(list)
            assertTrue(texts.contains("Personal"))
            assertTrue(texts.contains("Work"))
            assertTrue(texts.contains("School"))
            assertTrue("the profile in front is named as such", texts.contains("Current profile"))
            // The checkbox at the bottom is the one of the switcher.
            val always = activity.findViewById<CheckBox>(R.id.profiles_always_ask)
            assertNotNull(always)
            assertTrue(always.isChecked)
        }
    }

    // 2. A switch answers the question of the start -------------------------------------------

    @Test
    fun aSwitch_isNotAskedAgain_onTheStartItCauses() {
        Profiles.setSetupDone(context)
        assertTrue("the question is asked on an ordinary start", Profiles.shouldAsk(context))

        // The browser switches to a profile and starts again.
        Profiles.setSkipsAskOnce(context, "Work")
        assertFalse("the switch answered the question", Profiles.shouldAsk(context))
        assertEquals("Work", Profiles.switchedTo(context))

        // That start takes the answer away: the next start asks again, as it always did.
        Profiles.consumeSkip(context)
        assertTrue(Profiles.shouldAsk(context))
        assertFalse(Profiles.skipsAskOnce(context))
    }

    @Test
    fun theSwitcher_hasAButtonThatLeadsToTheSettingsOfTheProfiles() {
        Profiles.setSetupDone(context)
        Robolectric.buildActivity(ProfilesActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val sheet = ProfileSwitcherSheet(
                activity,
                2,
                object : ProfileSwitcherSheet.Listener {
                    override fun onSwitchProfile(profile: BrowserProfile) = Unit
                    override fun onGuest() = Unit
                    override fun onAddProfile() = Unit
                    override fun onManageProfiles() = Unit
                },
            )
            val view = sheet.createView()
            val texts = ArrayList<String>()
            fun collect(v: View) {
                if (v is android.widget.TextView) texts.add(v.text.toString())
                if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
            }
            collect(view)
            assertEquals("the sheet is the one of 18.0.0", R.id.profile_switcher_sheet, view.id)
            assertTrue(texts.contains("Profiles"))
            assertTrue("it says which profile is in front", texts.any { it.startsWith("Current profile") })
            assertTrue("and it leads to the settings", texts.contains("Profile settings"))
            // The checkbox of the start is at the bottom of the sheet.
            val always = view.findViewById<CheckBox>(R.id.profile_always_ask)
            assertNotNull(always)
            assertTrue(always.text.toString() == "Always show on startup")
        }
    }

    // 3. A picture for a profile ---------------------------------------------------------------

    @Test
    fun aProfile_hasItsOwnPicture_inItsOwnFolder() {
        val work = Profiles.create(context, "Work", 1)!!
        assertFalse("no picture yet", ProfilePictures.has(context, work))
        assertEquals(
            "the picture lives with the data of its profile",
            File(Profiles.thingsOf(context, work.id), ProfilePictures.FILE).absolutePath,
            ProfilePictures.fileOf(context, work).absolutePath,
        )
        // The first profile keeps its picture beside the folders of the app, as its other data.
        assertEquals(
            File(context.filesDir, ProfilePictures.FILE).absolutePath,
            ProfilePictures.fileOf(context, BrowserProfile.first()).absolutePath,
        )
    }

    @Test
    fun aChosenPicture_isSquared_kept_andTakenAwayAgain() {
        val work = Profiles.create(context, "Work", 1)!!
        // A picture that is not square, put where the picker would have left it.
        val source = File(context.cacheDir, "chosen.png")
        val wide = Bitmap.createBitmap(900, 300, Bitmap.Config.ARGB_8888)
        source.outputStream().use { wide.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue("the picture is kept", ProfilePictures.save(context, work, Uri.fromFile(source)))
        assertTrue(ProfilePictures.has(context, work))
        val kept = ProfilePictures.bitmap(context, work, 512)
        assertNotNull(kept)
        // Squared around the middle, so the disc is not stretched.
        assertEquals(kept!!.width, kept.height)
        assertTrue("and at most 512 px", kept.width <= ProfilePictures.SIZE)
        // The picture of one profile is not the picture of another.
        assertFalse(ProfilePictures.has(context, BrowserProfile.first()))

        ProfilePictures.clear(context, work)
        assertFalse("taken away again", ProfilePictures.has(context, work))
        assertNull(ProfilePictures.bitmap(context, work, 512))
    }

    @Test
    fun aProfileWithoutAPicture_keepsTheDiscOfItsLetter() {
        val work = Profiles.create(context, "Work", 3)!!
        val drawable = ProfilePictures.drawable(context, work, 40)
        assertTrue("the letter, in the colour of the profile", drawable is ProfileAvatarDrawable)
        assertEquals("W", work.initial)
    }

    @Test
    fun aPicture_isNotSharedBetweenTwoProfiles() {
        val one = Profiles.create(context, "One", 0)!!
        val two = Profiles.create(context, "Two", 1)!!
        val source = File(context.cacheDir, "square.png")
        Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).let { bmp ->
            source.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(ProfilePictures.save(context, one, Uri.fromFile(source)))
        assertTrue(ProfilePictures.has(context, one))
        assertFalse(ProfilePictures.has(context, two))
        assertNotEquals(
            ProfilePictures.fileOf(context, one).absolutePath,
            ProfilePictures.fileOf(context, two).absolutePath,
        )
        // Deleting the profile deletes its picture with the rest of its folder.
        Profiles.switchTo(context, "")
        assertTrue(Profiles.delete(context, one.id))
        assertFalse(ProfilePictures.fileOf(context, one).exists())
    }

    // 4. The profile on the bar ----------------------------------------------------------------

    @Test
    fun theBar_hasTheProfileInFront_leftOfTheThreeDotMenu() {
        val xml = File("src/main/res/layout/include_appbar.xml").readText()
        val profile = xml.indexOf("android:id=\"@+id/button_profile\"")
        val menu = xml.indexOf("android:id=\"@+id/button_menu\"")
        assertTrue("the profile is on the bar", profile > 0)
        assertTrue("and sits left of the three-dot menu", profile < menu)
        // Its content description is the name of the sheet it opens, not a word of its own.
        assertTrue(xml.contains("@string/profiles_title"))
    }
}
