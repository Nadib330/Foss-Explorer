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
import androidx.appcompat.widget.PopupMenu
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

        /** A tile was tapped: open [url] in the current tab. */
        fun onHomeOpen(url: String)

        /** "Open in new tab" was chosen for a tile. */
        fun onHomeOpenInNewTab(url: String, foreground: Boolean)
    }

    private val context: Context = root.context
    private val card: View = root.findViewById(R.id.home_shortcuts_card)
    private val grid: LinearLayout = root.findViewById(R.id.home_shortcuts)

    /** Guards against a slow load overwriting the result of a newer one. */
    private var loadGeneration = 0

    /** The tiles currently on screen. */
    var shortcuts: List<Shortcut> = emptyList()
        private set

    val isVisible: Boolean
        get() = root.visibility == View.VISIBLE

    init {
        root.findViewById<View>(R.id.home_search).setOnClickListener { listener.onHomeSearch() }
        root.findViewById<View>(R.id.home_menu).setOnClickListener { listener.onHomeMenu(it) }
    }

    /** Shows or hides the home page. Showing it reloads the tiles. */
    fun setVisible(visible: Boolean) {
        if (visible == isVisible) return
        root.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            root.findViewById<View>(R.id.home_scroll).scrollTo(0, 0)
            refresh()
        }
    }

    /** Reloads the tiles from bookmarks and history in the background. */
    fun refresh() {
        // Wallpaper, colour and which parts are shown: Settings > Home page customization.
        HomeAppearance.apply(root)
        val generation = ++loadGeneration
        val app = context.applicationContext
        executor.execute {
            val loaded = HomeShortcuts.load(app)
            val icons = loadIcons(app, loaded)
            root.post { if (generation == loadGeneration) render(loaded, icons) }
        }
    }

    /** Puts [list] on screen. [icons] maps a tile's address to its site icon. */
    @JvmOverloads
    fun render(list: List<Shortcut>, icons: Map<String, Bitmap> = emptyMap()) {
        shortcuts = list
        grid.removeAllViews()
        val wanted = HomeAppearance.prefs(context).getBoolean(HomeAppearance.KEY_SHOW_SHORTCUTS, true)
        card.visibility = if (list.isEmpty() || !wanted) View.GONE else View.VISIBLE

        val inflater = LayoutInflater.from(context)
        for (rowItems in list.chunked(COLUMNS)) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (shortcut in rowItems) row.addView(createTile(inflater, row, shortcut, icons[shortcut.url]))
            // Keep the columns aligned in a row that is not full.
            repeat(COLUMNS - rowItems.size) {
                row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            }
            grid.addView(row, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
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
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, MENU_NEW_TAB, 0, R.string.main_menu_new_tabOpen)
        popup.menu.add(0, MENU_BACKGROUND_TAB, 1, R.string.main_menu_new_tab)
        popup.menu.add(0, MENU_REMOVE, 2, R.string.home_remove_shortcut)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_NEW_TAB -> listener.onHomeOpenInNewTab(shortcut.url, true)
                MENU_BACKGROUND_TAB -> listener.onHomeOpenInNewTab(shortcut.url, false)
                MENU_REMOVE -> remove(shortcut)
            }
            true
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
        /** Tiles per row. */
        const val COLUMNS = 4

        private const val TAG = "HomePageView"
        private const val MENU_NEW_TAB = 1
        private const val MENU_BACKGROUND_TAB = 2
        private const val MENU_REMOVE = 3

        private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
