/*
 * Foss Explorer: the three-dot menu.
 * Own code, GPL-3.0. The arrangement follows the main menu of Firefox for
 * Android (MPL-2.0, see third_party/firefox): a sheet from the bottom, the
 * entries in rounded groups, the library as a row of tiles.
 */
package com.rpooi3.explorer.view

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.forEach
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R

/**
 * The main menu as a bottom sheet. The entries come from a menu resource, so
 * what is in the menu, and whether an entry is shown, enabled or switched on,
 * is decided where it was before; this class decides how it looks.
 *
 * @param anchor a view of the screen the menu belongs to
 * @param onItemSelected called with the id of the entry the user chose
 */
class MainMenuSheet(
    private val anchor: View,
    @MenuRes menuRes: Int,
    private val onItemSelected: (itemId: Int) -> Unit,
) {
    // Only the model: it parses the menu resource and is never shown itself.
    private val model = PopupMenu(anchor.context, anchor).apply {
        inflate(menuRes)
        setOnMenuItemClickListener { item ->
            this@MainMenuSheet.dismiss()
            onItemSelected(item.itemId)
            true
        }
    }
    private var dialog: BottomSheetDialog? = null

    /** The entries. Changes made after [show] do not appear until the menu is shown again. */
    val menu: Menu
        get() = model.menu

    /** The view of the open menu, or null while it is closed. */
    var contentView: View? = null
        private set

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    /** Called when the menu closes, for whatever reason. */
    var onDismiss: (() -> Unit)? = null

    /**
     * Builds the menu without opening it: the groups of [GROUPS] in order,
     * each with the entries of it that are visible. [show] uses it; it also
     * serves previews and tests.
     */
    fun createView(parent: ViewGroup? = null): View {
        val inflater = LayoutInflater.from(anchor.context)
        val view = inflater.inflate(R.layout.sheet_main_menu, parent, false)
        val groups = view.findViewById<LinearLayout>(R.id.main_menu_groups)
        val grouped = HashSet<Int>()
        fun visible(ids: IntArray): List<MenuItem> =
            ids.map { menu.findItem(it) }.filterNotNull().filter { it.isVisible }.onEach { grouped += it.itemId }

        for (group in GROUPS) {
            val items = visible(group.ids)
            if (items.isEmpty()) continue
            addGroup(inflater, groups, items, group.tiles)
        }
        // An entry no group knows (a menu resource with entries of its own) is listed after them.
        val rest = ArrayList<MenuItem>()
        menu.forEach { if (it.isVisible && it.itemId !in grouped) rest += it }
        if (rest.isNotEmpty()) addGroup(inflater, groups, rest, tiles = false)
        return view
    }

    private fun addGroup(inflater: LayoutInflater, groups: LinearLayout, items: List<MenuItem>, tiles: Boolean) {
        val resources = groups.resources
        val group = LinearLayout(groups.context).apply {
            orientation = if (tiles) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            // The group rounds the outer corners of its rows.
            if (!tiles) {
                setBackgroundResource(R.drawable.bg_menu_group)
                clipToOutline = true
            }
        }
        val gap = resources.getDimensionPixelSize(R.dimen.menu_item_gap)
        items.forEachIndexed { index, item ->
            val row = inflater.inflate(if (tiles) R.layout.item_main_menu_tile else R.layout.item_main_menu_row, group, false)
            bind(row, item)
            if (tiles) {
                row.setBackgroundResource(
                    when {
                        items.size == 1 -> R.drawable.bg_menu_row
                        index == 0 -> R.drawable.bg_menu_tile_start
                        index == items.lastIndex -> R.drawable.bg_menu_tile_end
                        else -> R.drawable.bg_menu_tile_middle
                    },
                )
            }
            (row.layoutParams as LinearLayout.LayoutParams).apply {
                if (index > 0) if (tiles) marginStart = gap else topMargin = gap
            }
            group.addView(row)
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (groups.isNotEmpty()) params.topMargin = resources.getDimensionPixelSize(R.dimen.menu_group_gap)
        groups.addView(group, params)
    }

    private fun bind(row: View, item: MenuItem) {
        val context = row.context
        row.id = item.itemId
        val label = row.findViewById<TextView>(R.id.menu_row_label)
        val icon = row.findViewById<ImageView>(R.id.menu_row_icon)
        label.text = item.title
        icon.setImageDrawable(item.icon)
        row.contentDescription = item.title
        // An entry that is on or off says so at its end, as "Desktop site" does in Firefox;
        // while it is on, the entry is drawn in the accent colour.
        row.findViewById<TextView?>(R.id.menu_row_badge)?.let { badge ->
            badge.isVisible = item.isCheckable
            if (item.isCheckable) {
                val state = context.getString(if (item.isChecked) R.string.menu_state_on else R.string.menu_state_off)
                badge.text = state
                badge.setBackgroundResource(if (item.isChecked) R.drawable.bg_menu_badge_active else R.drawable.bg_menu_badge)
                row.isSelected = item.isChecked
                ViewCompat.setStateDescription(row, state)
                if (item.isChecked) {
                    val accent = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
                    label.setTextColor(accent)
                    icon.imageTintList = android.content.res.ColorStateList.valueOf(accent)
                    badge.setTextColor(
                        MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0),
                    )
                }
            }
        }
        row.isEnabled = item.isEnabled
        row.alpha = if (item.isEnabled) 1f else DISABLED_ALPHA
        row.setOnClickListener { menu.performIdentifierAction(item.itemId, 0) }
    }

    fun show() {
        dismiss()
        val sheet = BottomSheetDialog(anchor.context)
        val view = createView()
        sheet.setContentView(view)
        // Opened whole: the menu is short enough, and nothing of it should hide below the edge.
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.setOnDismissListener {
            if (dialog === sheet) {
                dialog = null
                contentView = null
            }
            onDismiss?.invoke()
        }
        dialog = sheet
        contentView = view
        sheet.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    /** Entries that stand together. [tiles]: side by side, the icon above the label. */
    class Group(val ids: IntArray, val tiles: Boolean = false)

    companion object {
        const val DISABLED_ALPHA = 0.38f

        /** The groups of the main menu, in Firefox's order: tabs, tools, library, settings. */
        @JvmField
        val GROUPS: List<Group> = listOf(
            Group(intArrayOf(R.id.main_new_tab, R.id.main_new_private_tab)),
            Group(intArrayOf(R.id.main_share, R.id.main_zoom, R.id.main_desktop_site)),
            Group(intArrayOf(R.id.main_history, R.id.main_bookmarks, R.id.main_downloads), tiles = true),
            Group(intArrayOf(R.id.main_settings)),
        )
    }
}
