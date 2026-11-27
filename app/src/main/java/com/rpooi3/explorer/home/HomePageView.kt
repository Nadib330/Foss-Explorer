/*
 * Explorer: the home page of 18.4.0 — the "New" one of the screenshot.
 * Own code, GPL-3.0.
 *
 * The classic home page (logo, search box, tiles) is still there: Settings > Home page
 * customization chooses between **New** and **Classic**, and a new installation starts on New.
 * Both are drawn inside the same `include_home.xml`, under the same profile, three-dot menu and
 * pencil, so everything around the page is the browser it was before.
 *
 * Nothing of this page is a website: the software name and the shortcuts are native views that
 * HomePageView fills in.
 */
package com.rpooi3.explorer.home

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R
import com.rpooi3.explorer.database.FaviconHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Drives the home page layout (`include_home.xml`): shows and hides it, fills
 * in the site tiles and reports what the user tapped.
 */
class HomePageView(
    private val root: View,
    private val listener: Listener,
) {
    /** What the home page asks the browser to do. */
    interface Listener {
        /** The search box was tapped. */
        fun onHomeSearch()

        /** The three-dot button was tapped; [anchor] is the button. */
        fun onHomeMenu(anchor: View)

        /** 18.0.0 beta 6: the pencil was tapped — the page's own customisation is asked for. */
        fun onHomeCustomize()

        /** A tile was tapped: open [url] in the current tab. */
        fun onHomeOpen(url: String)

        /** "Open in new tab" was chosen for a tile. */
        fun onHomeOpenInNewTab(url: String, foreground: Boolean)
    }

    private val context: Context = root.context
    private val card: View = root.findViewById(R.id.home_shortcuts_card)
    private val grid: LinearLayout = root.findViewById(R.id.home_shortcuts)
    /** 18.0.0 beta 7 (item 5): where the user's own page is drawn. */
    private val codeWeb: android.webkit.WebView = root.findViewById(R.id.home_code_web)

    // 18.4.0: the new page.
    private val newScroll: View? = root.findViewById(R.id.home_new_scroll)
    private val classicScroll: View? = root.findViewById(R.id.home_scroll)
    private val newGrid: LinearLayout? = root.findViewById(R.id.home_new_grid)
    private val clockView: TextView? = root.findViewById(R.id.home_new_clock)

    /** Guards against a slow load overwriting the result of a newer one. */
    private var loadGeneration = 0

    /** The tiles currently on screen. */
    var shortcuts: List<Shortcut> = emptyList()
        private set

    val isVisible: Boolean
        get() = root.visibility == View.VISIBLE

    /** The style the page shows now, from the active profile's one canonical home choice. */
    val style: String
        get() = HomePage.style(context)

    init {
        // The page of code is a page of the user's: scripts run, nothing else is opened up.
        codeWeb.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
        }
        codeWeb.isVerticalScrollBarEnabled = true
        root.findViewById<View>(R.id.home_search).setOnClickListener { listener.onHomeSearch() }
        root.findViewById<View>(R.id.home_menu).setOnClickListener { listener.onHomeMenu(it) }
        root.findViewById<View>(R.id.home_customize)?.setOnClickListener { listener.onHomeCustomize() }
        // 18.4.0: what the new page does.
        root.findViewById<View>(R.id.home_new_search)?.setOnClickListener { listener.onHomeSearch() }
        root.findViewById<View>(R.id.home_new_search_arrow)?.setOnClickListener { listener.onHomeSearch() }
    }

    /** Shows or hides the home page. Showing it reloads the tiles. */
    fun setVisible(visible: Boolean) {
        if (visible == isVisible) return
        root.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            classicScroll?.scrollTo(0, 0)
            newScroll?.scrollTo(0, 0)
            refresh()
        }
    }

    /** Reloads the tiles from bookmarks and history in the background. */
    fun refresh() {
        // Wallpaper, colour and which parts are shown: Settings > Home page customization.
        HomeAppearance.apply(root)
        applyStyle()
        if (showCodePage()) return
        // The software name is cheap: set it on the main thread.
        showClock()
        val generation = ++loadGeneration
        val app = context.applicationContext
        executor.execute {
            val loaded = HomeShortcuts.load(app)
            val icons = loadIcons(app, loaded)
            root.post { if (generation == loadGeneration) render(loaded, icons) }
        }
    }

    /**
     * Shows exactly one of the two built-in pages. They are separate layouts,
     * not two labels for one view: every refresh re-reads the active profile's
     * canonical choice before changing visibility.
     */
    private fun applyStyle() {
        val isNew = style == HomePage.STYLE_NEW
        newScroll?.isVisible = isNew
        classicScroll?.isVisible = !isNew
    }

    /**
     * 18.0.0 beta 7 (item 5): draws the page the user wrote, in place of the built-in one.
     *
     * Only the inside of the home page changes: the wallpaper, the logo, the search box and the
     * tiles are put away, and the bars of the browser are not part of this view at all.
     *
     * @return true if the user's page is what the home page shows now
     */
    private fun showCodePage(): Boolean {
        val on = HomeAppearance.codeOn(context)
        codeWeb.isVisible = on
        classicScroll?.isVisible = !on && style == HomePage.STYLE_CLASSIC
        newScroll?.isVisible = !on && style == HomePage.STYLE_NEW
        if (!on) {
            codeWeb.stopLoading()
            codeWeb.loadUrl("about:blank")
            return false
        }
        val code = HomeAppearance.code(context) ?: HomeAppearance.codeTemplate(context)
        // The page is written as it is; scripts run, and its own styles decide how it looks.
        codeWeb.loadDataWithBaseURL(null, code, "text/html", "utf-8", null)
        return true
    }

    /** Puts [list] on screen. [icons] maps a tile's address to its site icon. */
    @JvmOverloads
    fun render(list: List<Shortcut>, icons: Map<String, Bitmap> = emptyMap()) {
        // 18.4.0: the new page has four shortcuts and no editable/add-shortcut tile. Keep the
        // classic page consistent so the selector never changes the number of home shortcuts.
        val visible = list.take(SHORTCUT_COUNT)
        shortcuts = visible
        grid.removeAllViews()
        val wanted = HomeAppearance.prefs(context).getBoolean(HomeAppearance.KEY_SHOW_SHORTCUTS, true)
        card.visibility = if (visible.isEmpty() || !wanted) View.GONE else View.VISIBLE

        val inflater = LayoutInflater.from(context)
        // 18.0.0 beta 6 (item 8): how many tiles stand in a row is the user's choice now.
        val columns = HomeAppearance.shortcutColumns(context)
        for (rowItems in visible.chunked(columns)) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (shortcut in rowItems) row.addView(createTile(inflater, row, shortcut, icons[shortcut.url]))
            // Keep the columns aligned in a row that is not full.
            repeat(columns - rowItems.size) {
                row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            }
            grid.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        renderNew(visible, icons, wanted)
    }

    // --- the new home page (18.4.0) --------------------------------------------------------------

    /** The four shortcut cards of the new page, two to a row. */
    private fun renderNew(list: List<Shortcut>, icons: Map<String, Bitmap>, wanted: Boolean) {
        val grid = newGrid ?: return
        grid.removeAllViews()
        grid.isVisible = wanted
        if (!wanted) return
        val inflater = LayoutInflater.from(context)
        for (rowCards in list.take(SHORTCUT_COUNT).chunked(2)) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowCards.forEachIndexed { index, shortcut ->
                val view = createCard(inflater, shortcut, icons[shortcut.url])
                val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) params.marginStart = gap()
                row.addView(view, params)
            }
            if (rowCards.size == 1) row.addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = gap()
                },
            )
        }
    }

    private fun gap(): Int = (12 * context.resources.displayMetrics.density).toInt()

    private fun createCard(inflater: LayoutInflater, shortcut: Shortcut, icon: Bitmap?): View {
        val tile = inflater.inflate(R.layout.item_home_shortcut_card, newGrid, false)
        tile.findViewById<TextView>(R.id.home_card_title).text = shortcut.title
        val iconView = tile.findViewById<ImageView>(R.id.home_card_icon)
        if (icon != null && !icon.isRecycled) {
            iconView.setImageBitmap(icon)
        } else {
            val color = MaterialColors.getColor(tile, com.google.android.material.R.attr.colorOnSurfaceVariant)
            iconView.setImageDrawable(MonogramDrawable(shortcut.title, color))
        }
        tile.contentDescription = shortcut.title
        tile.setOnClickListener { listener.onHomeOpen(shortcut.url) }
        tile.setOnLongClickListener {
            showTileMenu(tile, shortcut)
            true
        }
        return tile
    }

    /** The software name shown by the New home page. */
    private fun showClock() {
        clockView?.text = context.getString(R.string.app_name)
    }

    private fun createTile(inflater: LayoutInflater, row: ViewGroup, shortcut: Shortcut, icon: Bitmap?): View {
        val tile = inflater.inflate(R.layout.item_home_shortcut, row, false)
        tile.findViewById<TextView>(R.id.home_tile_title).text = shortcut.title

        val iconView = tile.findViewById<ImageView>(R.id.home_tile_icon)
        if (icon != null && !icon.isRecycled) {
            iconView.setImageBitmap(icon)
        } else {
            val color = MaterialColors.getColor(tile, com.google.android.material.R.attr.colorOnSurfaceVariant)
            iconView.setImageDrawable(MonogramDrawable(shortcut.title, color))
        }

        tile.contentDescription = shortcut.title
        tile.setOnClickListener { listener.onHomeOpen(shortcut.url) }
        tile.setOnLongClickListener {
            showTileMenu(tile, shortcut)
            true
        }
        return tile
    }

    private fun showTileMenu(anchor: View, shortcut: Shortcut) {
        val popup = com.rpooi3.explorer.view.MainMenuPopup(
            anchor,
            R.menu.menu_home_shortcut,
        ) { itemId ->
            when (itemId) {
                R.id.home_shortcut_new_foreground -> listener.onHomeOpenInNewTab(shortcut.url, true)
                R.id.home_shortcut_new_background -> listener.onHomeOpenInNewTab(shortcut.url, false)
                R.id.home_shortcut_remove -> remove(shortcut)
            }
        }
        popup.show()
    }

    /** Takes a tile off the home page for good and refills the grid. */
    fun remove(shortcut: Shortcut) {
        HomeShortcuts.hide(context, shortcut)
        refresh()
    }

    private fun loadIcons(context: Context, list: List<Shortcut>): Map<String, Bitmap> {
        val icons = HashMap<String, Bitmap>()
        // Closed explicitly: SQLiteOpenHelper is only AutoCloseable from Android 10 on.
        val helper = FaviconHelper(context)
        try {
            for (shortcut in list) helper.getFavicon(shortcut.url)?.let { icons[shortcut.url] = it }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read site icons", e)
        } finally {
            helper.close()
        }
        return icons
    }

    companion object {
        /** Tiles per row, unless Settings > Home page customization says otherwise. */
        const val COLUMNS = 4

        /** The exact number of shortcuts shown on either built-in home page. */
        const val SHORTCUT_COUNT = 4
        const val MAX_CARDS = SHORTCUT_COUNT

        private const val TAG = "HomePageView"
        // The loader is independent of the Activity lifecycle. A daemon worker also lets test
        // processes and the app process finish cleanly when the screen is no longer needed.
        private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
            Thread(task, "HomePageView-loader").apply { isDaemon = true }
        }
    }
}
