package com.rpooi3.explorer.activity

import android.Manifest
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.rpooi3.explorer.R
import com.rpooi3.explorer.accessibility.PageZoom
import com.rpooi3.explorer.browser.BrowserContainer
import com.rpooi3.explorer.browser.NinjaDownloadListener
import com.rpooi3.explorer.home.HomeAppearance
import com.rpooi3.explorer.preferences.HomeCustomizationScreen
import com.rpooi3.explorer.preferences.SettingsScreens
import com.rpooi3.explorer.preferences.SettingsSections
import com.rpooi3.explorer.view.LinkContextMenu
import com.rpooi3.explorer.view.LinkContextMenu.Item
import com.rpooi3.explorer.view.LinkContextMenu.Target
import com.rpooi3.explorer.view.MainMenuSheet
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
import java.io.File

/**
 * Release 14.2.3: the three-dot menu after Firefox, the download question
 * after Firefox, the long-press menu after Chromium, "Home page
 * customization" in place of "Home page", the developer's GitHub address, the
 * licences, and the new app icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34], qualifiers = "w411dp-h891dp-notnight-xxhdpi")
class Release1423Test {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private class Browser(val activity: BrowserActivity) {
        val content: ViewGroup = activity.findViewById(R.id.main_content)
        val web: NinjaWebView get() = content.getChildAt(content.childCount - 1) as NinjaWebView
        val home: View = activity.findViewById(R.id.home_page)

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun <T : View> view(id: Int): T = activity.findViewById(id)

        fun open(url: String) {
            web.loadUrl(url)
            activity.updateOmniBox()
            idle()
        }

        fun openMenu(): View {
            view<View>(R.id.button_menu).performClick()
            idle()
            return activity.mainMenu.contentView!!
        }

        fun tabs(): List<NinjaWebView> = (0 until BrowserContainer.size()).map { BrowserContainer.get(it) as NinjaWebView }

        /** The texts of the rows of the open long-press menu, in order. */
        fun contextRows(dialog: AlertDialog): List<TextView> {
            val rows = dialog.findViewById<LinearLayout>(R.id.context_menu_rows)!!
            return (0 until rows.childCount).map { rows.getChildAt(it) }.filterIsInstance<TextView>()
        }
    }

    private fun browser(block: Browser.() -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().use { controller ->
            Browser(controller.get()).apply {
                idle()
                block()
                idle()
            }
        }
    }

    private class Settings(val activity: AppCompatActivity) {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.content_frame) as PreferenceFragmentCompat
        val title: String get() = activity.findViewById<Toolbar>(R.id.toolbar).title.toString()
        fun pref(key: String): Preference = fragment.findPreference(key)!!
        fun all(): List<Preference> {
            val result = ArrayList<Preference>()
            fun visit(group: PreferenceGroup) {
                for (i in 0 until group.preferenceCount) {
                    val pref = group.getPreference(i)
                    if (pref is PreferenceGroup) visit(pref) else result += pref
                }
            }
            visit(fragment.preferenceScreen)
            return result
        }
    }

    private fun <T> settings(section: String?, block: Settings.() -> T): T {
        val intent = Intent(context, Settings_Activity::class.java)
        if (section != null) intent.putExtra(Settings_Activity.EXTRA_SECTION, section)
        Robolectric.buildActivity(Settings_Activity::class.java, intent).setup().use { controller ->
            idle()
            return Settings(controller.get()).run {
                val result = block()
                idle()
                result
            }
        }
    }

    // ------------------------------------------------------ 1. three-dot menu

    @Test
    fun theMenu_isASheetFromTheBottom_withTheEntriesInRoundedGroups() = browser {
        open("https://example.org/")
        val content = openMenu()

        assertTrue("a bottom sheet, as in Firefox", ShadowDialog.getLatestDialog() is BottomSheetDialog)
        assertNotNull("with a handle", content.findViewById<View>(R.id.main_menu_handle))
        val groups = content.findViewById<LinearLayout>(R.id.main_menu_groups)
        fun ids(group: Int) = (groups.getChildAt(group) as ViewGroup).let { g -> (0 until g.childCount).map { g.getChildAt(it).id } }
        assertEquals(4, groups.childCount)
        assertEquals("tabs", listOf(R.id.main_new_tab, R.id.main_new_private_tab), ids(0))
        assertEquals("tools", listOf(R.id.main_share, R.id.main_desktop_site), ids(1))
        assertEquals("library", listOf(R.id.main_history, R.id.main_bookmarks, R.id.main_downloads), ids(2))
        assertEquals(listOf(R.id.main_settings), ids(3))

        // The same entries as before, nothing added and nothing lost.
        val labels = (0 until groups.childCount).flatMap { g ->
            (groups.getChildAt(g) as ViewGroup).let { group ->
                (0 until group.childCount).map { group.getChildAt(it).findViewById<TextView>(R.id.menu_row_label).text.toString() }
            }
        }
        assertEquals(
            setOf("New tab", "New private tab", "History", "Downloads", "Bookmarks", "Share", "Desktop site", "Settings"),
            labels.toSet(),
        )
        assertEquals(8, labels.size)

        // Groups are rounded and cut their rows; rows lie one below the other, 2dp apart.
        val tools = groups.getChildAt(1) as LinearLayout
        assertEquals(LinearLayout.VERTICAL, tools.orientation)
        assertTrue(tools.clipToOutline)
        val density = activity.resources.displayMetrics.density
        assertEquals((2 * density).toInt(), (tools.getChildAt(1).layoutParams as LinearLayout.LayoutParams).topMargin)
        assertEquals((52 * density).toInt(), tools.getChildAt(0).minimumHeight)
        assertNotNull("every row has its icon", tools.getChildAt(0).findViewById<ImageView>(R.id.menu_row_icon).drawable)
    }

    @Test
    fun theLibrary_isARowOfTiles_iconAboveLabel() = browser {
        val content = openMenu()
        val library = content.findViewById<LinearLayout>(R.id.main_menu_groups).getChildAt(2) as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, library.orientation)
        assertEquals(3, library.childCount)
        for (i in 0 until 3) {
            val tile = library.getChildAt(i) as LinearLayout
            assertEquals(LinearLayout.VERTICAL, tile.orientation)
            assertEquals("equal widths", 1f, (tile.layoutParams as LinearLayout.LayoutParams).weight, 0f)
            assertTrue(tile.indexOfChild(tile.findViewById(R.id.menu_row_icon)) < tile.indexOfChild(tile.findViewById(R.id.menu_row_label)))
        }
        assertEquals(
            listOf("History", "Bookmarks", "Downloads"),
            (0 until 3).map { library.getChildAt(it).findViewById<TextView>(R.id.menu_row_label).text.toString() },
        )

        library.getChildAt(2).performClick()
        idle()
        assertEquals(
            com.rpooi3.explorer.downloads.DownloadsActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component!!.className,
        )
        assertFalse("choosing an entry closes the menu", activity.mainMenu.isShowing)
    }

    @Test
    fun desktopSite_saysOnOrOffAtItsEnd() = browser {
        open("https://example.org/")
        var row = openMenu().findViewById<View>(R.id.main_desktop_site)
        var badge = row.findViewById<TextView>(R.id.menu_row_badge)
        assertEquals(View.VISIBLE, badge.visibility)
        assertEquals("Off", badge.text.toString())
        assertFalse(row.isSelected)
        assertEquals("only an entry that is on or off has it", View.GONE, openMenu().findViewById<View>(R.id.main_share).findViewById<View>(R.id.menu_row_badge).visibility)

        openMenu().findViewById<View>(R.id.main_desktop_site).performClick()
        idle()
        assertTrue(web.isDesktopSite)

        row = openMenu().findViewById(R.id.main_desktop_site)
        badge = row.findViewById(R.id.menu_row_badge)
        assertEquals("On", badge.text.toString())
        assertTrue(row.isSelected)
    }

    @Test
    fun whatCannotBeUsed_isFaded_andZoomJoinsTheTools() {
        browser {
            // On the home page there is nothing to share.
            val share = openMenu().findViewById<View>(R.id.main_share)
            assertFalse(share.isEnabled)
            assertEquals(MainMenuSheet.DISABLED_ALPHA, share.alpha, 0f)
            activity.mainMenu.dismiss()
        }
        prefs.edit().putBoolean(PageZoom.KEY_ALWAYS_SHOW, true).remove("openTabs").commit()
        browser {
            open("https://example.org/")
            val tools = openMenu().findViewById<LinearLayout>(R.id.main_menu_groups).getChildAt(1) as ViewGroup
            assertEquals(
                listOf(R.id.main_share, R.id.main_zoom, R.id.main_desktop_site),
                (0 until tools.childCount).map { tools.getChildAt(it).id },
            )
        }
    }

    // ----------------------------------------------------- 2. download question

    private fun latestSheet(): BottomSheetDialog = ShadowDialog.getLatestDialog() as BottomSheetDialog

    /** The one download that was handed to Android's download manager. */
    private fun org.robolectric.shadows.ShadowDownloadManager.onlyRequest() =
        shadowOf((0L..4L).firstNotNullOf { getRequest(it) })

    @Test
    fun aDownload_isAskedAboutInFirefoxsSheet_andStartsWithTheButton() = browser {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        open("https://example.org/")
        NinjaDownloadListener(web).onDownloadStart("https://example.org/files/report.pdf", "agent", null, "application/pdf", 1_500_000L)
        idle()

        val sheet = latestSheet()
        assertTrue(sheet.isShowing)
        assertEquals(
            "Download file? (" + Formatter.formatShortFileSize(context, 1_500_000L) + ")",
            sheet.findViewById<TextView>(R.id.download_title)!!.text.toString(),
        )
        assertEquals("report.pdf", sheet.findViewById<EditText>(R.id.download_filename)!!.text.toString())
        assertEquals("Download", sheet.findViewById<TextView>(R.id.download_button)!!.text.toString())
        assertEquals("Close", sheet.findViewById<View>(R.id.download_close)!!.contentDescription.toString())
        assertNull("the old dialog with two fields is gone", sheet.findViewById<View>(R.id.editTop))

        val manager = shadowOf(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
        assertEquals("nothing is fetched before the answer", 0, manager.requestCount)

        sheet.findViewById<View>(R.id.download_button)!!.performClick()
        idle()
        assertFalse(sheet.isShowing)
        assertEquals(1, manager.requestCount)
        assertEquals("https://example.org/files/report.pdf", manager.onlyRequest().uri.toString())
    }

    @Test
    fun theCross_closesTheQuestion_withoutDownloading_andANameCanBeTyped() = browser {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        open("https://example.org/")
        val manager = shadowOf(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)

        NinjaDownloadListener.saveAs(activity, web, "https://example.org/files/report.pdf", null, "application/pdf")
        idle()
        assertEquals("size not known", "Download file?", latestSheet().findViewById<TextView>(R.id.download_title)!!.text.toString())
        latestSheet().findViewById<View>(R.id.download_close)!!.performClick()
        idle()
        assertFalse(latestSheet().isShowing)
        assertEquals(0, manager.requestCount)

        NinjaDownloadListener.saveAs(activity, web, "https://example.org/files/report.pdf", null, "application/pdf", 10L)
        idle()
        val sheet = latestSheet()
        sheet.findViewById<EditText>(R.id.download_filename)!!.setText("  my report  ")
        sheet.findViewById<View>(R.id.download_button)!!.performClick()
        idle()
        assertEquals("a name without an ending gets one", "my report.bin", manager.onlyRequest().title.toString())

        // An empty name does not start anything, and the question stays.
        NinjaDownloadListener.saveAs(activity, web, "https://example.org/files/report.pdf", null, "application/pdf", 10L)
        idle()
        latestSheet().findViewById<EditText>(R.id.download_filename)!!.setText("   ")
        latestSheet().findViewById<View>(R.id.download_button)!!.performClick()
        idle()
        assertTrue(latestSheet().isShowing)
        assertEquals(1, manager.requestCount)
    }

    // -------------------------------------------------------- 3. long-press menu

    private val link = Target("https://example.org/article", "An article", null)
    private val picture = Target(null, null, "https://example.org/cat.png")
    private val pictureLink = Target("https://example.org/article", null, "https://example.org/cat.png")

    @Test
    fun theLongPressMenu_hasChromiumsEntries_inChromiumsOrder() {
        assertEquals(
            listOf(listOf(Item.OPEN_IN_NEW_TAB, Item.OPEN_IN_INCOGNITO_TAB, Item.COPY_LINK_ADDRESS, Item.COPY_LINK_TEXT, Item.SAVE_LINK, Item.SHARE_LINK)),
            LinkContextMenu.groups(link, privateTab = false),
        )
        assertEquals(
            listOf(listOf(Item.OPEN_IMAGE_IN_NEW_TAB, Item.COPY_IMAGE, Item.SAVE_IMAGE, Item.SHARE_IMAGE)),
            LinkContextMenu.groups(picture, privateTab = false),
        )
        val both = LinkContextMenu.groups(pictureLink, privateTab = false)
        assertEquals("a picture that is a link has both groups, the link first", 2, both.size)
        assertFalse("no text, nothing to copy", Item.COPY_LINK_TEXT in both[0])
        assertFalse("a private tab opens private tabs anyway", Item.OPEN_IN_INCOGNITO_TAB in LinkContextMenu.groups(link, privateTab = true)[0])
        assertTrue(LinkContextMenu.groups(Target(null, null, null), false).isEmpty())
        assertEquals(
            listOf(
                "Open in new tab", "Open in Incognito tab", "Copy link address", "Copy link text", "Download link", "Share link",
                "Open image in new tab", "Copy image", "Download image", "Share image",
            ),
            Item.entries.map { context.getString(it.label) },
        )
    }

    @Test
    fun theMenu_showsWhatWasPressed_andItsEntriesAsPlainRows() = browser {
        open("https://example.org/")
        val dialog = activity.showLinkContextMenu(web, link)!!
        idle()
        assertTrue(dialog.isShowing)
        assertEquals("An article", dialog.findViewById<TextView>(R.id.context_menu_title)!!.text.toString())
        val url = dialog.findViewById<TextView>(R.id.context_menu_url)!!
        assertEquals("https://example.org/article", url.text.toString())
        assertEquals(1, url.maxLines)
        url.performClick()
        assertTrue("a tap shows the address whole", url.maxLines > 1)
        assertEquals(
            listOf("Open in new tab", "Open in Incognito tab", "Copy link address", "Copy link text", "Download link", "Share link"),
            contextRows(dialog).map { it.text.toString() },
        )
        for (row in contextRows(dialog)) assertEquals("text only, as in Chromium", 0, row.compoundDrawablesRelative.count { it != null })
        dialog.dismiss()

        val both = activity.showLinkContextMenu(web, pictureLink)!!
        idle()
        assertEquals(9, contextRows(both).size)
        assertEquals(View.GONE, both.findViewById<View>(R.id.context_menu_title)!!.visibility)
        val rows = both.findViewById<LinearLayout>(R.id.context_menu_rows)!!
        assertEquals("a line under the header and one between the groups", 2, (0 until rows.childCount).count {
            rows.getChildAt(it) is com.google.android.material.divider.MaterialDivider
        })
        both.dismiss()
    }

    @Test
    fun theEntries_doWhatTheySay() = browser {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        open("https://example.org/")
        val first = web
        fun choose(target: Target, label: String) {
            val dialog = activity.showLinkContextMenu(first, target)!!
            idle()
            contextRows(dialog).first { it.text.toString() == label }.performClick()
            idle()
            assertFalse("the menu closes", dialog.isShowing)
        }

        choose(link, "Open in new tab")
        assertEquals(listOf("https://example.org/", "https://example.org/article"), tabs().map { it.url })
        assertEquals("behind the tab in front, as in Chrome", "https://example.org/", web.url)

        choose(link, "Copy link address")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("https://example.org/article", clipboard.primaryClip!!.getItemAt(0).text.toString())
        choose(link, "Copy link text")
        assertEquals("An article", clipboard.primaryClip!!.getItemAt(0).text.toString())

        choose(link, "Share link")
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        assertEquals("https://example.org/article", chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getStringExtra(Intent.EXTRA_TEXT))

        choose(link, "Download link")
        assertEquals("the download question", "article.bin", latestSheet().findViewById<EditText>(R.id.download_filename)!!.text.toString().let {
            if (it.contains('.')) it else "$it.bin"
        })
        latestSheet().dismiss()

        choose(picture, "Open image in new tab")
        assertTrue(tabs().any { it.url == "https://example.org/cat.png" })
        choose(picture, "Download image")
        assertEquals("cat.png", latestSheet().findViewById<EditText>(R.id.download_filename)!!.text.toString())
        latestSheet().dismiss()

        choose(link, "Open in Incognito tab")
        assertTrue("a private tab, in front", web.isPrivateTab)
        assertEquals("https://example.org/article", web.url)

        // From a private tab, "Open in new tab" stays private.
        val before = tabs().size
        val dialog = activity.showLinkContextMenu(web, link)!!
        idle()
        assertFalse(contextRows(dialog).any { it.text.toString() == "Open in Incognito tab" })
        contextRows(dialog).first { it.text.toString() == "Open in new tab" }.performClick()
        idle()
        assertEquals(before + 1, tabs().size)
        assertEquals("the new tab is private too", 2, tabs().count { it.isPrivateTab })
    }

    @Test
    fun aLongPressOnPlainText_opensNoMenu_andPicturesGetAFileName() = browser {
        open("https://example.org/")
        assertFalse(LinkContextMenu.fromLongPress(web) { throw AssertionError("nothing was pressed") })
        val png = java.io.ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        assertEquals("cat.png", LinkContextMenu.imageFileName("https://example.org/pictures/cat.png?size=large", png))
        assertTrue(LinkContextMenu.imageFileName("data:image/png;base64,AAAA", png).startsWith("image."))
        assertNotNull(LinkContextMenu.imageBytes("data:image/png;base64," + android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP), null))
    }

    // ------------------------------------------- 4. and 5. home page customization

    @Test
    fun homePage_isGoneFromTheSettings_andHomePageCustomizationIsThere() {
        settings(null) {
            val titles = all().map { it.title.toString() }
            assertFalse("the tile \"Home page\" is removed", "Home page" in titles)
            assertTrue("Home page customization" in titles)
            assertNull(fragment.findPreference<Preference>("section_home"))
            assertNull(SettingsSections.byKey("section_home"))
            pref("section_home_custom").performClick()
            assertEquals("section_home_custom", shadowOf(activity).nextStartedActivity.getStringExtra(Settings_Activity.EXTRA_SECTION))
        }
        settings("section_home_custom") {
            assertEquals("Home page customization", title)
            assertEquals(
                listOf(
                    "home_custom_description", "home_wallpaper_pick", "home_wallpaper_remove", "home_wallpaper_dim",
                    "home_color", "home_show_logo", "home_show_shortcuts", "favoriteURL",
                ),
                all().map { it.key },
            )
            assertEquals("Choose a picture", pref("home_wallpaper_pick").title.toString())
            assertEquals("No wallpaper. Tap to choose a picture from your device.", pref("home_wallpaper_pick").summary.toString())
            assertFalse("nothing to remove yet", pref("home_wallpaper_remove").isEnabled)
            assertEquals("Background color", pref("home_color").title.toString())
            assertEquals("Theme", pref("home_color").summary.toString())
        }
    }

    @Test
    fun aColor_isChosenFromRoundSamples_andPaintsTheHomePage() {
        settings("section_home_custom") {
            pref("home_color").performClick()
            idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("Background color", dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
            val grid = (dialog.findViewById<View>(androidx.appcompat.R.id.custom) as ViewGroup).getChildAt(0) as ViewGroup
            assertEquals(HomeAppearance.PALETTE.size, grid.childCount)
            assertEquals(12, grid.childCount)
            val olive = (0 until grid.childCount).map { grid.getChildAt(it) }.first { it.contentDescription == "Olive" }
            olive.performClick()
            idle()
            assertFalse(dialog.isShowing)
            assertEquals(0xFF5E603B.toInt(), HomeAppearance.color(prefs))
            assertEquals("Olive", pref("home_color").summary.toString())
        }
        browser {
            assertEquals(0xFF5E603B.toInt(), (home.background as ColorDrawable).color)
            assertEquals("light text on a dark colour", Color.WHITE, view<TextView>(R.id.home_title).currentTextColor)
        }
        HomeAppearance.setColor(prefs, 0xFFFFFFFF.toInt())
        browser {
            assertEquals(Color.BLACK, view<TextView>(R.id.home_title).currentTextColor)
        }
        HomeAppearance.setColor(prefs, null)
        assertNull("back to the colour of the theme", HomeAppearance.color(prefs))
    }

    @Test
    fun aPictureOfTheUsers_becomesTheWallpaper_andCanBeRemoved() {
        // A wide photo as a file, the way Android's picker hands one over.
        val photo = File(context.cacheDir, "photo.png")
        photo.outputStream().use { Bitmap.createBitmap(4096, 64, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertFalse(HomeAppearance.hasWallpaper(context))

        assertTrue(HomeAppearance.setWallpaper(context, Uri.fromFile(photo)))
        assertTrue(HomeAppearance.hasWallpaper(context))
        val kept = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(HomeAppearance.wallpaperFile(context).path, kept)
        assertTrue("kept as a copy, no larger than needed: ${kept.outWidth}", kept.outWidth in 1..HomeAppearance.MAX_SIDE)
        photo.delete()
        assertTrue("the copy stays when the original is deleted", HomeAppearance.hasWallpaper(context))
        assertFalse("not a picture", HomeAppearance.setWallpaper(context, Uri.fromFile(File(context.cacheDir, "missing.png"))))

        browser {
            assertEquals(View.VISIBLE, view<View>(R.id.home_wallpaper).visibility)
            assertEquals("darkened unless switched off", View.VISIBLE, view<View>(R.id.home_scrim).visibility)
            assertEquals(Color.WHITE, view<TextView>(R.id.home_title).currentTextColor)
            assertEquals(ImageView.ScaleType.CENTER_CROP, view<ImageView>(R.id.home_wallpaper).scaleType)
        }
        prefs.edit().putBoolean(HomeAppearance.KEY_DIM, false).commit()
        browser { assertEquals(View.GONE, view<View>(R.id.home_scrim).visibility) }

        settings("section_home_custom") {
            assertEquals("A picture of yours is the wallpaper. Tap to choose another.", pref("home_wallpaper_pick").summary.toString())
            assertTrue(pref("home_color").summary.toString().endsWith("Hidden while a wallpaper is set."))
            assertTrue(pref("home_wallpaper_remove").isEnabled)
            pref("home_wallpaper_remove").performClick()
            idle()
            assertFalse(HomeAppearance.hasWallpaper(context))
            assertFalse(pref("home_wallpaper_remove").isEnabled)
        }
        browser { assertEquals(View.GONE, view<View>(R.id.home_wallpaper).visibility) }
    }

    @Test
    fun logoAndShortcuts_canBeHidden_andTheStartPageSettingIsKept() {
        browser { assertEquals(View.VISIBLE, view<View>(R.id.home_title).visibility) }
        prefs.edit().putBoolean(HomeAppearance.KEY_SHOW_LOGO, false).putBoolean(HomeAppearance.KEY_SHOW_SHORTCUTS, false).commit()
        browser {
            assertEquals(View.GONE, view<View>(R.id.home_logo).visibility)
            assertEquals("the switch says what it hides", "Show the name", context.getString(R.string.home_show_logo))
            activity.findViewById<View>(R.id.home_shortcuts_card).let { assertEquals(View.GONE, it.visibility) }
        }
        settings("section_home_custom") {
            assertEquals("Open a web page instead", pref("favoriteURL").title.toString())
        }
        settings("section_home_custom") {
            // Choosing a picture goes to Android's picker; its answer is taken up.
            assertNotNull(pref(HomeCustomizationScreen.KEY_PICK).onPreferenceClickListener)
            HomeCustomizationScreen.onImagePicked(fragment, null)
            assertFalse("nothing picked, nothing changed", HomeAppearance.hasWallpaper(context))
        }
    }

    // ------------------------------------------------- 6. and 7. about and licences

    @Test
    fun theDevelopersGitHubAddress_isNadib330_andCodebergStays() = settings("section_developer") {
        assertEquals("github.com/nadib330", pref("about_github").summary.toString())
        pref("about_github").performClick()
        assertEquals("https://github.com/nadib330", shadowOf(activity).nextStartedActivity.dataString)
        assertEquals("codeberg.org/rpooi3", pref("about_codeberg").summary.toString())
        pref("about_codeberg").performClick()
        assertEquals("https://codeberg.org/rpooi3", shadowOf(activity).nextStartedActivity.dataString)
    }

    @Test
    fun theLicences_nameEverything_andSayWhatIsStillFossBrowsers() {
        settings("section_legal") {
            assertEquals("Code from FOSS Browser", pref("about_fork").title.toString())
            val summary = pref("about_fork").summary.toString()
            for (part in listOf("bookmark filter", "gestures", "backup", "web view inside a tab", "ad and cookie-banner blockers", "AGPL")) {
                assertTrue(part, summary.contains(part))
            }
        }
        val components = SettingsScreens.readAsset(context, "licenses/components.txt")!!
        for (part in listOf(
            "FOSS Browser", "the bookmark filter, the gestures and the backup", "the web view inside a tab that loads the pages",
            "Chromium: offline page and dinosaur game", "Chromium: settings, context menu, tabs and Incognito page",
            "Firefox for Android: main menu, download prompt and protection panel", "Mozilla Public License Version 2.0",
            "Readability", "AndroidX", "Material Components for Android", "Kotlin standard library",
            "Material Design Icons", "Search engine logos", "App icon and logo",
            "Redistribution and use in source and binary forms", "Apache License",
        )) {
            assertTrue(part, components.contains(part))
        }
        val root = File("..").canonicalFile
        assertTrue(components.contains(File(root, "third_party/firefox/LICENSE").readText().trim()))
        val license = File(root, "LICENSE.md").readText()
        for (part in listOf("third_party/firefox", "third_party/chromium-settings", "third_party/readability", "BrowserActivity.java", "tools/upstream_audit.py")) {
            assertTrue(part, license.contains(part))
        }
    }

    // ------------------------------------------------------------ 8. app icon

    @Test
    fun theAppIcon_isTheNewLogo_inTwoLayers() {
        val icon = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        // The drawings are made from the picture: its colours are in them.
        val root = File("..").canonicalFile
        val foreground = File(root, "app/src/main/res/drawable/ic_launcher_foreground.xml").readText()
        val background = File(root, "app/src/main/res/drawable/ic_launcher_background.xml").readText()
        assertTrue("the olive of the compass", foreground.contains("#5E603B"))
        for (cream in listOf("#F6F2E9", "#EEE8D8", "#E6E0D0")) assertTrue(cream, background.contains(cream))
        assertTrue(File(root, "art/Explorer_Logo5.jpg").isFile)
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
    }
}
