package com.rpooi3.explorer.profile

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.extension.ChromeApiBridge
import com.rpooi3.explorer.extension.ExtensionStore
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
 * 18.0.0 beta 3: what the third beta fixes, in the order of the user's list.
 *
 *  1. and 3. the switch of profiles went round in a circle: every change of the profile in front is
 *     now written to the disk before the browser is restarted in it;
 *  2. history, bookmarks, site icons, settings and extensions belong to a profile, not to the app;
 *  4. the profile is on the home page as well, where the top bar is hidden;
 *  5. no gallery icon on every row of the settings — the pencil changes the picture;
 *  6. a picture can be chosen while a profile is being made, as while one is changed;
 *  7. a profile can be made from the home page, on the first and on every start;
 *  8. a backup is the backup of one profile;
 *  9. the store's "Add to Explorer" button installs, through the same code as the three-dot menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class Release1800Beta3Test {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        context.getSharedPreferences("browser_profiles", Context.MODE_PRIVATE)
            .edit().clear().commit()
        Profiles.cacheCurrent(context)
    }

    // 1. and 3. The switch of profiles -----------------------------------------------------------

    @Test
    fun aSwitchOfProfiles_isOnTheDisk_atOnce() {
        val work = Profiles.create(context, "Work", 1)
        assertNotNull(work)
        Profiles.switchTo(context, work!!.id)
        // The browser restarts the moment after the switch: what is in memory would be lost.
        assertEquals(
            "the profile in front is the one that was switched to",
            work.id,
            Profiles.currentId(context),
        )
        assertEquals(
            "and it is on the disk, not only in memory",
            work.id,
            context.getSharedPreferences("browser_profiles", Context.MODE_PRIVATE)
                .getString("active_profile", null),
        )
    }

    @Test
    fun theSkipOfOneStart_isOnTheDisk_and_isConsumedOnce() {
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        Profiles.setSkipsAskOnce(context, work.name)
        assertTrue("the start after a switch does not ask", Profiles.skipsAskOnce(context))
        assertEquals(
            "the name said after the switch is on the disk too",
            work.name,
            context.getSharedPreferences("browser_profiles", Context.MODE_PRIVATE)
                .getString("switched_to", null),
        )
        Profiles.consumeSkip(context)
        assertFalse("asking comes back for the start after that", Profiles.skipsAskOnce(context))
        assertFalse(Profiles.shouldAsk(context))
    }

    @Test
    fun nothingOnThePathOfASwitch_isWrittenInTheBackground() {
        // `edit { }` writes when the system gets round to it, which is after System.exit(0) —
        // that is what made beta 1 and beta 2 look as if the profile had not changed at all.
        val source = File("src/main/java/com/rpooi3/explorer/profile/Profiles.kt").readText()
        assertFalse(
            "no asynchronous write is left in the profiles",
            source.contains("store(context).edit {"),
        )
        assertTrue(source.contains(".putString(KEY_ACTIVE, id).commit()"))
        assertTrue(source.contains(".putString(KEY_LIST, array.toString()).commit()"))
    }

    // 2. What belongs to a profile ---------------------------------------------------------------

    @Test
    fun everyProfile_hasItsOwnFolder_andItsOwnExtensions() {
        val first = Profiles.current(context)
        val firstDir = Profiles.currentDir(context)
        val work = Profiles.create(context, "Work", 1)!!
        Profiles.switchTo(context, work.id)
        val workDir = Profiles.currentDir(context)

        assertNotEquals("two profiles never share a folder", firstDir, workDir)
        assertTrue(
            "the first profile keeps the folders of the app",
            firstDir.absolutePath.endsWith("files"),
        )
        assertEquals(
            "the others live below files/profiles",
            File(Profiles.profilesDir(context), work.id),
            workDir,
        )
        assertEquals(
            "the extensions of a profile are in its own folder",
            File(workDir, "extensions"),
            ExtensionStore.directory(context),
        )
        Profiles.switchTo(context, first.id)
        assertEquals(
            "and the first profile's are where they always were",
            File(firstDir, "extensions"),
            ExtensionStore.directory(context),
        )
    }

    @Test
    fun theStorageOfAnExtension_isKeptPerProfile() {
        val work = Profiles.create(context, "Work", 1)!!
        val session = arrayOf("extension_storage", "extension_session", "extensions")
        for (base in session) {
            val app = ProfilePrefs.getFor(context, base, "")
            val other = ProfilePrefs.getFor(context, base, work.id)
            app.edit().putString("ext_probe", "first").commit()
            other.edit().putString("ext_probe", "work").commit()
            assertEquals("first", app.getString("ext_probe", null))
            assertEquals("work", other.getString("ext_probe", null))
        }
    }

    // 4. The profile on the home page -------------------------------------------------------------

    @Test
    fun theProfile_isOnTheHomePage_too() {
        val layout = File("src/main/res/layout/include_home.xml").readText()
        assertTrue("the home page carries the profile", layout.contains("@+id/home_profile"))
        val browser = File(
            "src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java",
        ).readText()
        assertTrue("and the browser wires it", browser.contains("R.id.home_profile"))
        assertTrue(
            "tapping it opens the profiles",
            browser.contains("buttonProfileHome.setOnClickListener"),
        )
        // The icon of the top bar is drawn on it as well, so both show the profile in front.
        assertTrue(browser.contains("home.setImageDrawable"))
    }

    // 5. and 6. The picture of a profile ----------------------------------------------------------

    @Test
    fun theSettingsRows_carryNoGalleryIconOfTheirOwn() {
        val source = File("src/main/java/com/rpooi3/explorer/profile/ProfilesActivity.kt").readText()
        assertFalse(
            "the little gallery button of every row is gone",
            source.contains("R.drawable.icon_image"),
        )
        assertFalse("and it is not called any more", source.contains("choosePicture(profile)"))
        assertTrue("the pencil changes the picture", source.contains("R.drawable.icon_edit"))
    }

    @Test
    fun aRowOfTheSettings_isTheDiscTheNameAndThePencil() {
        Profiles.create(context, "Work", 1)
        Robolectric.buildActivity(ProfilesActivity::class.java).setup().use { controller ->
            val list = controller.get().findViewById<LinearLayout>(R.id.profiles_list)
            var rows = 0
            for (index in 0 until list.childCount) {
                val row = list.getChildAt(index) as? ViewGroup ?: continue
                val buttons = ArrayList<ImageButton>()
                fun collect(view: View) {
                    if (view is ImageButton) buttons.add(view)
                    if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
                }
                collect(row)
                if (buttons.isEmpty()) continue
                rows++
                assertEquals("only the pencil and the bin are left on a row", 2, buttons.size)
                val avatar = firstAvatar(row)
                assertNotNull("the disc is still there", avatar)
                assertFalse("and it is not a button of its own", avatar!!.isClickable)
            }
            assertTrue("there is a row for every profile", rows >= 1)
        }
    }

    private fun firstAvatar(view: View): ProfileAvatarView? {
        if (view is ProfileAvatarView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = firstAvatar(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    @Test
    fun theDialogThatMakesAProfile_offersTheSamePictureRowAsTheOneThatChangesIt() {
        val dialog = File("src/main/java/com/rpooi3/explorer/profile/ProfileSetupDialog.kt").readText()
        assertTrue("the picture row is in the one dialog", dialog.contains("R.id.profile_picture_preview"))
        assertTrue("with a way of taking it away", dialog.contains("R.id.profile_picture_remove"))
        assertTrue(
            "a new profile is made with a picture as well",
            dialog.contains("onPicture: ((Bitmap?) -> Unit)? = null"),
        )
        // The profile in front can be changed in the same dialog, which is where its picture goes.
        val screen = File("src/main/java/com/rpooi3/explorer/profile/ProfilesActivity.kt").readText()
        assertTrue(screen.contains("ProfilePictures.save(this, created, picture)"))
        assertTrue(screen.contains("ProfilePictures.clear(this, profile)"))
    }

    @Test
    fun thePickerOfPictures_isAnsweredByEveryScreenThatShowsTheDialog() {
        // One picker, one request code, and every screen passes the answer back.
        assertEquals(4310, ProfilePicturePicker.REQUEST)
        val sources = listOf(
            "src/main/java/com/rpooi3/explorer/profile/ProfilesActivity.kt",
            "src/main/java/com/rpooi3/explorer/activity/WelcomeActivity.kt",
            "src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java",
        )
        for (path in sources) {
            assertTrue(
                "$path passes the answer of the picker on",
                File(path).readText().contains("ProfilePicturePicker.handle"),
            )
        }
        assertNull("nothing is pending before a picture is asked for", ProfilePicturePicker.preview(context, null, 72))
    }

    // 7. A profile from the home page -------------------------------------------------------------

    @Test
    fun aProfile_canBeMadeFromTheHomePage() {
        val browser = File(
            "src/main/java/com/rpooi3/explorer/activity/BrowserActivity.java",
        ).readText()
        assertTrue("the sheet makes a profile", browser.contains("addProfileFromSheet"))
        assertTrue(
            "with the picture row of the settings",
            browser.contains("ProfileSetupDialog"),
        )
        assertTrue(
            "and the picture it chose is kept with the profile",
            browser.contains("pendingProfilePicture"),
        )
        // The sheet is what makes this reachable without a page being open.
        val sheet = File("src/main/java/com/rpooi3/explorer/profile/ProfileSwitcherSheet.kt").readText()
        assertTrue(sheet.contains("R.string.profiles_add") || sheet.contains("onAddProfile"))
    }

    // 8. The backup of one profile ----------------------------------------------------------------

    @Test
    fun aBackup_isRenamedIntoTheProfileItIsRestoredTo() {
        // The history of the profile 1a2b, restored into the profile 9f8e.
        assertEquals(
            "databases/places-9f8e.sqlite",
            ProfileBackup.mapPath("databases/places-1a2b.sqlite", "1a2b", "9f8e"),
        )
        assertEquals(
            "the journal goes with it",
            "databases/places-9f8e.sqlite-wal",
            ProfileBackup.mapPath("databases/places-1a2b.sqlite-wal", "1a2b", "9f8e"),
        )
        assertEquals(
            "and the site icons",
            "databases/favicon-9f8e.db",
            ProfileBackup.mapPath("databases/favicon-1a2b.db", "1a2b", "9f8e"),
        )
        assertEquals(
            "the preferences of the profile",
            "shared_prefs/settings@9f8e.xml",
            ProfileBackup.mapPath("shared_prefs/settings@1a2b.xml", "1a2b", "9f8e"),
        )
        assertEquals(
            "and its own folder",
            "files/profiles/9f8e/avatar.png",
            ProfileBackup.mapPath("files/profiles/1a2b/avatar.png", "1a2b", "9f8e"),
        )
    }

    @Test
    fun aBackup_ofTheFirstProfile_goesIntoWhicheverComesBackTo() {
        // The first profile keeps the plain names: places.sqlite, files/, settings.xml.
        assertEquals(
            "databases/places-9f8e.sqlite",
            ProfileBackup.mapPath("databases/places.sqlite", "", "9f8e"),
        )
        assertEquals(
            "files/profiles/9f8e/extensions/abc/manifest.json",
            ProfileBackup.mapPath("files/extensions/abc/manifest.json", "", "9f8e"),
        )
        assertEquals(
            "shared_prefs/site@9f8e.xml",
            ProfileBackup.mapPath("shared_prefs/site.xml", "", "9f8e"),
        )
        // And back into the first profile.
        assertEquals(
            "databases/places.sqlite",
            ProfileBackup.mapPath("databases/places-9f8e.sqlite", "9f8e", ""),
        )
        assertEquals("files/avatar.png", ProfileBackup.mapPath("files/profiles/9f8e/avatar.png", "9f8e", ""))
    }

    @Test
    fun aBackup_neverPutsAnotherProfileIn() {
        // A file of a profile that is not the one the backup was made in is left out, not renamed
        // into the profile in front: this is what two profiles sharing a history used to look like.
        assertNull(ProfileBackup.mapPath("databases/places-9f8e.sqlite", "1a2b", ""))
        assertNull(ProfileBackup.mapPath("files/profiles/9f8e/avatar.png", "", "1a2b"))
        assertNull(ProfileBackup.mapPath("shared_prefs/settings@9f8e.xml", "", "1a2b"))
        assertNull("the manifest is not a file of the profile", ProfileBackup.mapPath(ProfileBackup.MANIFEST, "", ""))
        assertNull("neither is anything else", ProfileBackup.mapPath("app_webview/x", "", "1a2b"))
    }

    @Test
    fun aBackup_saysWhichProfileItIs() {
        val manifest = ProfileBackup.parseManifest(
            "format=2\nprofile=1a2b\nname=Work and home\nsaved=1700000000000\n",
        )
        assertEquals("2", manifest["format"])
        assertEquals("1a2b", manifest["profile"])
        assertEquals("Work and home", manifest["name"])
        // A backup of a version before 18.0.0 has no manifest; it is read as the first profile's.
        assertTrue(ProfileBackup.parseManifest("").isEmpty())
        assertEquals("", ProfileBackup.parseManifest("").getOrDefault("profile", ""))
    }

    @Test
    fun whatIsBackedUpOfAProfile_isOnlyThatProfile() {
        val data = ProfileBackup.dataDir(context)
        val prefs = File(data, "shared_prefs")
        prefs.mkdirs()
        File(prefs, "site@1a2b.xml").writeText("<map/>")
        File(prefs, "site@9f8e.xml").writeText("<map/>")
        File(prefs, "site.xml").writeText("<map/>")

        val mine = ProfileBackup.filesOf(context, "1a2b", false)
        assertTrue("the settings of the profile are in", mine.contains(File(prefs, "site@1a2b.xml")))
        assertFalse("the settings of another are not", mine.contains(File(prefs, "site@9f8e.xml")))
        assertFalse("and those of the app are not either", mine.contains(File(prefs, "site.xml")))
        assertTrue(
            "a preferences file is recognised by the id it ends in",
            ProfileBackup.belongsToPreferences("site@1a2b.xml", "1a2b"),
        )
        assertFalse(ProfileBackup.belongsToPreferences("site@9f8e.xml", "1a2b"))
        assertTrue(
            "a database by the id in its name",
            ProfileBackup.belongsToDatabase("places-1a2b.sqlite", "1a2b"),
        )
        assertFalse(ProfileBackup.belongsToDatabase("places-9f8e.sqlite", "1a2b"))
        assertTrue("and the first profile's by having none", ProfileBackup.belongsToDatabase("places.sqlite", ""))
    }

    @Test
    fun theScreenOfTheBackup_asksTheProfileForItsData() {
        val utils = File("src/main/java/com/rpooi3/explorer/unit/BackupUtils.java").readText()
        assertTrue(
            "every backup goes through the profile",
            utils.contains("com.rpooi3.explorer.profile.ProfileBackup.backup("),
        )
        assertTrue(
            "and every restore too",
            utils.contains("com.rpooi3.explorer.profile.ProfileBackup.restore("),
        )
        assertFalse(
            "the glob over the databases of the whole app is gone",
            utils.contains("name.startsWith(\"Ninja4.db\")"),
        )
        val fragment = File(
            "src/main/java/com/rpooi3/explorer/fragment/Fragment_settings_Backup.java",
        ).readText()
        assertTrue("the file says which profile it is", fragment.contains("backupFileName"))
        assertTrue(
            "a restored database is loaded by a fresh start",
            fragment.contains("triggerAppRestart"),
        )
    }

    // 9. The store's button ----------------------------------------------------------------------

    @Test
    fun theStoreButton_installs_andSaysSo() {
        val installed = ArrayList<String>()
        val bridge = ChromeApiBridge(context, null, { id: String -> installed.add(id) })
        val token = bridge.newPageToken()
        val id = "abcdefghijklmnopabcdefghijklmnop"
        assertTrue("the call is taken", bridge.installFromStore(token, id))
        assertEquals("and the extension is on its way", listOf(id), installed)
    }

    @Test
    fun theStoreButton_isRefusedWithoutTheTokenOfThePage() {
        val installed = ArrayList<String>()
        val bridge = ChromeApiBridge(context, null, { id: String -> installed.add(id) })
        bridge.newPageToken()
        assertFalse(
            "a page that guesses the name of the bridge cannot install",
            bridge.installFromStore("not-the-token", "abcdefghijklmnopabcdefghijklmnop"),
        )
        assertFalse(
            "and a string that is not an extension id is refused",
            bridge.installFromStore(bridge.pageToken(), "not-an-id"),
        )
        assertTrue(installed.isEmpty())
    }

    @Test
    fun aRefusedStoreButton_becomesALinkTheBrowserInstalls() {
        val hook = File("src/main/java/com/rpooi3/explorer/extension/StoreHook.kt").readText()
        assertTrue("the script knows the link", hook.contains("explorer-install://"))
        assertTrue(
            "and only trusts the bridge when it says it took the install",
            hook.contains("=== true"),
        )
        val client = File(
            "src/main/java/com/rpooi3/explorer/browser/NinjaWebViewClient.java",
        ).readText()
        assertTrue("the browser intercepts that link", client.contains("INSTALL_LINK"))
        assertTrue(
            "and installs through the code of the three-dot menu",
            client.contains("ExtensionPrompt.request"),
        )
        // The bridge answers with a boolean, which is what the script reads.
        val bridge = File("src/main/java/com/rpooi3/explorer/extension/ChromeApiBridge.kt").readText()
        assertTrue(
            "the bridge says whether it took the install",
            bridge.contains("fun installFromStore(token: String?, extensionId: String?): Boolean"),
        )
    }
}
