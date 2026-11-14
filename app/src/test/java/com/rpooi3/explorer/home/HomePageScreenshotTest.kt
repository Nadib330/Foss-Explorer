package com.rpooi3.explorer.home

import com.rpooi3.explorer.view.TopBarController
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.BrowserActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.widget.TextView
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.database.RecordAction
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * Renders the browser screen to PNG files, using Robolectric's native
 * graphics. No device or emulator is needed.
 *
 * The pictures land in `app/build/outputs/screenshots/`. They show layout,
 * colours and text as Android draws them. Not in the picture: shadows, the
 * system bars, and web content (the web view of the test environment is
 * empty).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomePageScreenshotTest {

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun home_light() = capture("home-light")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun home_dark() = capture("home-dark")

    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-notnight-xxhdpi")
    fun home_landscape() = capture("home-landscape")

    /** Android 8: no wallpaper colours, so this is the Foss Explorer brand palette. */
    @Test
    @Config(sdk = [26], qualifiers = PHONE)
    fun home_android8() = capture("home-android8")

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun toolbar_onAWebsite() = capture("toolbar-website") { activity ->
        openExamplePage(activity)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun toolbar_findInPage() = capture("toolbar-find-in-page") { activity ->
        openExamplePage(activity)
        // What BrowserActivity.searchOnSite() does.
        activity.findViewById<View>(R.id.appBar).visibility = View.GONE
        activity.findViewById<View>(R.id.searchOnSiteLayout).visibility = View.VISIBLE
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun toolbar_hidden() = capture("toolbar-hidden") { activity ->
        openExamplePage(activity)
        // What the "Hide toolbars" entry of the page actions does.
        TopBarController(activity.findViewById(R.id.main), prefs(activity)).setHidden(true)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun pageActions() = capture("page-actions", dialog = true) { activity ->
        openExamplePage(activity)
        activity.findViewById<View>(R.id.addressPill).performLongClick()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun toolbar_onAWebsite_dark() = capture("toolbar-website-dark") { activity ->
        openExamplePage(activity)
    }

    private fun prefs(activity: BrowserActivity) = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)

    /** The address field while typing: it takes the place of the top bar, suggestions fill the screen. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun addressField() = capture("address-field") { activity ->
        seedHistory(activity)
        activity.findViewById<View>(R.id.home_search).performClick()
        activity.findViewById<TextView>(R.id.search_input).text = "exa"
    }

    /** The row of the open three-dot menu that stands for the entry [id]. */
    private fun menuRow(id: Int): View = ShadowDialog.getLatestDialog().findViewById(id)

    private fun openMenu(activity: BrowserActivity) {
        activity.findViewById<View>(R.id.button_menu).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The three-dot menu, open over a website. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun mainMenu() = capture("menu", dialog = true) { activity ->
        openExamplePage(activity)
        openMenu(activity)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun mainMenu_dark() = capture("menu-dark", dialog = true) { activity ->
        openExamplePage(activity)
        openMenu(activity)
    }

    /** The three-dot menu with "Desktop site" switched on. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun mainMenu_desktopSiteOn() = capture("menu-desktop-site", dialog = true) { activity ->
        openExamplePage(activity)
        openMenu(activity)
        menuRow(R.id.main_desktop_site).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        openMenu(activity)
    }

    /** A loaded page: the reload button stands where the loading ring was. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun toolbar_reloadButton() = capture("toolbar-reload") { activity ->
        openExamplePage(activity)
        activity.updateProgress(100)
    }

    /** While the page loads: the ring. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun toolbar_loading() = capture("toolbar-loading") { activity ->
        openExamplePage(activity)
        activity.updateProgress(40)
    }

    /** The tab switcher with three tabs. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun tabSwitcher() = capture("tab-switcher") { activity ->
        openExamplePage(activity)
        activity.findViewById<View>(R.id.button_newTab).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.button_newTab).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.button_tabs).performClick()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun tabSwitcher_dark() = capture("tab-switcher-dark") { activity ->
        openExamplePage(activity)
        activity.findViewById<View>(R.id.button_newTab).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.button_tabs).performClick()
    }

    /** The private page of the tab switcher. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun tabSwitcher_private() = capture("tab-switcher-private") { activity ->
        activity.findViewById<View>(R.id.button_tabs).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.tabs_mode)
            .check(R.id.tabs_mode_private)
    }

    // 13.2.2 ------------------------------------------------------------------------------------

    /** The tab switcher with the inactive section above the cards. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun tabSwitcher_inactiveSection() = capture("tab-switcher-inactive") { activity ->
        val prefs = prefs(activity)
        val since = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
        com.rpooi3.explorer.tabs.InactiveTabs.add(prefs, com.rpooi3.explorer.tabs.InactiveTabs.Entry("https://old.example/", "An old page", since))
        com.rpooi3.explorer.tabs.InactiveTabs.add(prefs, com.rpooi3.explorer.tabs.InactiveTabs.Entry("https://older.example/", "An older page", since))
        openExamplePage(activity)
        activity.findViewById<View>(R.id.button_newTab).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.button_tabs).performClick()
    }

    /** Private tabs, locked after the app was left ("Lock Incognito tabs when you leave Explorer"). */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun incognitoLock() = capture("incognito-lock") { activity ->
        shadowOf(activity.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager)
            .setIsDeviceSecure(true)
        prefs(activity).edit().putBoolean(com.rpooi3.explorer.privacy.IncognitoLock.KEY, true).commit()
        openMenu(activity)
        menuRow(R.id.main_new_private_tab).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        try {
            com.rpooi3.explorer.privacy.IncognitoLock.onLeave(activity, true)
            activity.onResume()
        } finally {
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** The three-dot menu with "Zoom" (Accessibility > "Show zoom option in main menu"). */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun mainMenu_zoom() = capture("menu-zoom", dialog = true) { activity ->
        prefs(activity).edit().putBoolean(com.rpooi3.explorer.accessibility.PageZoom.KEY_ALWAYS_SHOW, true).commit()
        openExamplePage(activity)
        openMenu(activity)
    }

    /** Main menu > Zoom: the zoom of the site in the tab. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun zoomSheet() = capture("zoom-sheet", dialog = true) { activity ->
        openExamplePage(activity)
        val web = activity.findViewById<ViewGroup>(R.id.main_content).getChildAt(0) as com.rpooi3.explorer.view.NinjaWebView
        com.rpooi3.explorer.accessibility.PageHooks.showZoomSheet(web)
    }

    // 14.2.3 ------------------------------------------------------------------------------------

    /** The home page with a picture of the user's as wallpaper. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun home_wallpaper() {
        val context = RuntimeEnvironment.getApplication()
        // A stand-in for a photo: evening sky over hills.
        val photo = Bitmap.createBitmap(540, 1170, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, 1170f, intArrayOf(0xFF1B2A49.toInt(), 0xFFB5527A.toInt(), 0xFFF2A65A.toInt()), null,
                android.graphics.Shader.TileMode.CLAMP,
            )
            drawRect(0f, 0f, 540f, 1170f, paint)
            paint.shader = null
            paint.color = 0xFF27374D.toInt()
            drawCircle(120f, 1250f, 420f, paint)
            paint.color = 0xFF1B2638.toInt()
            drawCircle(470f, 1330f, 430f, paint)
        }
        com.rpooi3.explorer.home.HomeAppearance.setWallpaper(context, photo)
        capture("home-wallpaper")
    }

    /** The home page in one of the background colours. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun home_color() {
        val context = RuntimeEnvironment.getApplication()
        com.rpooi3.explorer.home.HomeAppearance.setColor(com.rpooi3.explorer.home.HomeAppearance.prefs(context), 0xFF5E603B.toInt())
        capture("home-color-olive")
    }

    /** The question before a download, as Firefox asks it. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun downloadPrompt() = capture("download-prompt", dialog = true) { activity ->
        openExamplePage(activity)
        val web = activity.findViewById<ViewGroup>(R.id.main_content).getChildAt(0) as com.rpooi3.explorer.view.NinjaWebView
        com.rpooi3.explorer.browser.NinjaDownloadListener(web)
            .onDownloadStart("https://example.org/files/annual-report-2026.pdf", "agent", null, "application/pdf", 85_700_000L)
    }

    /** Long press on a link: Chromium's context menu. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun contextMenu_link() = capture("context-menu-link", dialog = true) { activity ->
        openExamplePage(activity)
        val web = activity.findViewById<ViewGroup>(R.id.main_content).getChildAt(0) as com.rpooi3.explorer.view.NinjaWebView
        com.rpooi3.explorer.view.LinkContextMenu.show(
            activity, web,
            com.rpooi3.explorer.view.LinkContextMenu.Target("https://example.org/articles/the-wonderful-wizard-of-oz", "The Wonderful Wizard of Oz", null),
            false, NO_HOST,
        )
    }

    /** Long press on a picture that is a link: both groups. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun contextMenu_imageLink() = capture("context-menu-image-link", dialog = true) { activity ->
        openExamplePage(activity)
        val web = activity.findViewById<ViewGroup>(R.id.main_content).getChildAt(0) as com.rpooi3.explorer.view.NinjaWebView
        com.rpooi3.explorer.view.LinkContextMenu.show(
            activity, web,
            com.rpooi3.explorer.view.LinkContextMenu.Target("https://example.org/gallery", null, "data:image/png;base64,AAAA"),
            false, NO_HOST,
        )
    }

    /** The app icon as a launcher with round icons shows it, and the two layers it is made of. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun appIcon() {
        val context = RuntimeEnvironment.getApplication()
        val size = 432
        val icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.mipmap.ic_launcher)
            as android.graphics.drawable.AdaptiveIconDrawable
        val sheet = Bitmap.createBitmap(size * 3 + 96, size + 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF202124.toInt())
        // Cut round, the way most launchers show it: the middle two thirds of the layers.
        val round = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(round).apply {
            val clip = android.graphics.Path().apply { addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW) }
            clipPath(clip)
            val inset = -size / 4
            for (layer in listOf(icon.background, icon.foreground)) {
                layer.setBounds(inset, inset, size - inset, size - inset)
                layer.draw(this)
            }
        }
        canvas.drawBitmap(round, 24f, 24f, null)
        for ((index, layer) in listOf(icon.background, icon.foreground).withIndex()) {
            canvas.save()
            canvas.translate(24f + (index + 1) * (size + 24f), 24f)
            layer.setBounds(0, 0, size, size)
            layer.draw(canvas)
            canvas.restore()
        }
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "app-icon.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // 15.0.0 ------------------------------------------------------------------------------------

    /** A new private tab: Chromium's Incognito page. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun incognitoPage() = capture("incognito-page") { activity ->
        openMenu(activity)
        menuRow(R.id.main_new_private_tab).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The shield and its panel on a protected site with trackers blocked. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun protectionPanel() = capture("protection-panel", dialog = true) { activity ->
        openExamplePage(activity)
        val web = activity.findViewById<ViewGroup>(R.id.main_content).getChildAt(0) as com.rpooi3.explorer.view.NinjaWebView
        for (url in listOf("https://ads.tracker.example/a", "https://ads.tracker.example/b", "https://metrics.example/c", "https://pixel.example/d")) {
            com.rpooi3.explorer.privacy.TrackingProtection.onBlocked(web, url)
        }
        activity.findViewById<View>(R.id.button_shield).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        ShadowDialog.getLatestDialog().findViewById<View>(R.id.protection_blocked).performClick()
    }

    /** The panel after protection was switched off for the site. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun protectionPanel_off() = capture("protection-panel-off", dialog = true) { activity ->
        openExamplePage(activity)
        val web = activity.findViewById<ViewGroup>(R.id.main_content).getChildAt(0) as com.rpooi3.explorer.view.NinjaWebView
        com.rpooi3.explorer.privacy.TrackingProtection.setProtected(activity, web.url, false)
        activity.updateOmniBox()
        activity.findViewById<View>(R.id.button_shield).performClick()
    }

    /** The top bar on a website: the shield left of the three dots. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun toolbar_shield() = capture("toolbar-shield") { activity ->
        openExamplePage(activity)
    }

    /** The tab switcher while "Search your tabs" narrows the cards. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun tabSwitcher_search() = capture("tab-switcher-search") { activity ->
        openExamplePage(activity)
        activity.findViewById<View>(R.id.button_newTab).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<View>(R.id.button_tabs).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<android.widget.EditText>(R.id.tabs_search).setText("example")
    }

    private fun seedHistory(activity: BrowserActivity) {
        val action = RecordAction(activity)
        action.open(true)
        action.addHistory(Record("Example Domain", "https://example.org/", System.currentTimeMillis(), 0))
        action.addHistory(Record("Example articles", "https://example.org/articles", System.currentTimeMillis(), 0))
        action.close()
    }

    private fun openExamplePage(activity: BrowserActivity) {
        val web = activity.findViewById<ViewGroup>(R.id.main_content).getChildAt(0) as WebView
        web.loadUrl("https://example.org/articles/top-toolbar")
        activity.updateOmniBox()
    }

    /**
     * @param overlay a view to draw in the top right corner on top of the screen, the way
     *   the menu popup appears (a popup is a window of its own and not part of the screen's view tree)
     */
    private fun capture(
        name: String,
        dialog: Boolean = false,
        overlay: ((BrowserActivity) -> View)? = null,
        prepare: (BrowserActivity) -> Unit = {},
    ) {
        // Started the way the launcher starts it; without the action no first tab is opened.
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, launch).setup().use { controller ->
            val activity = controller.get()
            val tiles = activity.findViewById<ViewGroup>(R.id.home_shortcuts)

            // The tiles are loaded on a background thread and posted back.
            val deadline = System.currentTimeMillis() + 5_000
            while (tiles.childCount == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
                shadowOf(Looper.getMainLooper()).idle()
            }
            assertTrue("tiles were loaded", tiles.childCount > 0)

            prepare(activity)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))

            val root: View = if (dialog) {
                ShadowDialog.getLatestDialog().window!!.decorView
            } else {
                activity.findViewById(R.id.main)
            }
            assertTrue("screen was laid out", root.width > 0 && root.height > 0)

            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            root.draw(canvas)
            overlay?.invoke(activity)?.let { popup ->
                if (popup.width == 0) {
                    val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    popup.measure(unspecified, unspecified)
                    popup.layout(0, 0, popup.measuredWidth, popup.measuredHeight)
                }
                canvas.translate((root.width - popup.width).toFloat(), 0f)
                popup.draw(canvas)
            }

            val dir = File("build/outputs/screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private companion object {
        /** A menu that is only looked at. */
        val NO_HOST = object : com.rpooi3.explorer.view.LinkContextMenu.Host {
            override fun openInNewTab(url: String, privateTab: Boolean) = Unit
            override fun openInIncognitoTab(url: String) = Unit
            override fun download(url: String) = Unit
            override fun shareLink(title: String?, url: String) = Unit
        }

        const val PHONE = "w411dp-h891dp-notnight-xxhdpi"
    }
}
