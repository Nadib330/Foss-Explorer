package com.rpooi3.explorer.library

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.database.RecordAction
import com.rpooi3.explorer.database.RecordUnit
import com.rpooi3.explorer.unit.BrowserUnit
import com.rpooi3.explorer.unit.HelperUtils
import com.rpooi3.explorer.view.CatToast
import com.rpooi3.explorer.view.GridItem

/**
 * The bookmarks screen, laid out like Chrome's: one row per bookmark with the
 * website icon, the name and the website, and a three-dot button with the
 * actions for that bookmark. A tap opens the page.
 *
 * Chrome sorts bookmarks into folders. This browser has categories instead
 * (the "bookmark filter" of the settings); they appear as chips above the
 * list, and "Move to…" puts a bookmark into one.
 */
class BookmarksActivity : LibraryActivity() {

    override val titleRes = R.string.album_title_bookmarks
    override val searchHintRes = R.string.bookmarks_search_hint
    override val emptyTextRes = R.string.bookmarks_empty
    override val emptyIconRes = R.drawable.icon_bookmark
    override val itemActionIconRes = R.drawable.icon_more_vert
    override val itemActionLabelRes = R.string.library_more_actions

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moreButton.visibility = View.VISIBLE
    }

    private fun readBookmarks(): List<Record> {
        val action = RecordAction(this)
        action.open(false)
        try {
            // The query returns the list upside down, for the old list that grew from the bottom of the screen.
            return action.listBookmark(this, false, 0).asReversed().filter { !it.url.isNullOrBlank() }
        } finally {
            action.close()
        }
    }

    /** The categories that are switched on in the settings, by id. */
    private fun categories(): List<GridItem> = ArrayList<GridItem>().also { HelperUtils.addFilterItems(this, it) }

    /** "All" plus every category that holds at least one bookmark. */
    override fun filters(): List<Filter> {
        val used = readBookmarks().map { categoryOf(it) }.toSet()
        val chips = categories()
            .filter { it.data.toLong() in used }
            .map { Filter(it.data.toLong(), it.title.orEmpty()) }
        return if (chips.isEmpty()) emptyList() else listOf(Filter(FILTER_ALL, getString(R.string.library_filter_all))) + chips
    }

    override fun loadRows(query: String, filterId: Long): List<Row> =
        readBookmarks()
            .filter { filterId == FILTER_ALL || filterId == NO_FILTER || categoryOf(it) == filterId }
            .filter {
                query.isEmpty() ||
                    it.title?.contains(query, ignoreCase = true) == true ||
                    it.url.contains(query, ignoreCase = true)
            }
            .map {
                Row.Item(
                    title = it.title?.takeIf { title -> title.isNotBlank() } ?: it.url,
                    subtitle = HelperUtils.domain(it.url).orEmpty(),
                    url = it.url,
                    iconRes = R.drawable.icon_bookmark,
                    payload = it,
                )
            }

    override fun onItemClick(item: Row.Item) = openInBrowser((item.payload as Record).url)

    override fun onItemAction(item: Row.Item, anchor: View) {
        val record = item.payload as Record
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_NEW_TAB, 0, R.string.library_open_new_tab)
            menu.add(0, MENU_PRIVATE_TAB, 1, R.string.library_open_private_tab)
            menu.add(0, MENU_EDIT, 2, R.string.menu_edit)
            if (categories().isNotEmpty()) menu.add(0, MENU_MOVE, 3, R.string.bookmarks_move)
            menu.add(0, MENU_COPY, 4, R.string.library_copy_link)
            menu.add(0, MENU_SHARE, 5, R.string.menu_share_link)
            menu.add(0, MENU_DELETE, 6, R.string.menu_delete)
            setOnMenuItemClickListener { choice ->
                when (choice.itemId) {
                    MENU_NEW_TAB -> openInBrowser(record.url, TARGET_NEW_TAB)
                    MENU_PRIVATE_TAB -> openInBrowser(record.url, TARGET_PRIVATE_TAB)
                    MENU_EDIT -> edit(record)
                    MENU_MOVE -> move(record)
                    MENU_COPY -> copyToClipboard(record.url)
                    MENU_SHARE -> shareLink(item.title, record.url)
                    MENU_DELETE -> delete(record)
                }
                true
            }
        }.show()
    }

    override fun onMoreClick(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_SORT_NAME, 0, R.string.bookmarks_sort_name)
            menu.add(0, MENU_SORT_CATEGORY, 1, R.string.bookmarks_sort_category)
            menu.add(0, MENU_SORT_SITE, 2, R.string.bookmarks_sort_site)
            menu.add(0, MENU_DELETE_ALL, 3, R.string.bookmarks_delete_all)
            setOnMenuItemClickListener { choice ->
                when (choice.itemId) {
                    MENU_SORT_NAME -> sortBy("title", byWebsite = false)
                    MENU_SORT_CATEGORY -> sortBy("time", byWebsite = false)
                    MENU_SORT_SITE -> sortBy(prefs.getString(KEY_SORT, "title") ?: "title", byWebsite = true)
                    MENU_DELETE_ALL -> confirmDeleteAll()
                }
                true
            }
        }.show()
    }

    /** These are the preferences the bookmark query itself reads; see RecordAction.listBookmark. */
    private fun sortBy(column: String, byWebsite: Boolean) {
        prefs.edit {
            putString(KEY_SORT, column)
            putBoolean(KEY_SORT_WEBSITE, byWebsite)
        }
        reload()
    }

    /** Asks for a new name and address. */
    fun edit(record: Record) {
        val view = layoutInflater.inflate(R.layout.dialog_bookmark_edit, null)
        val titleInput = view.findViewById<EditText>(R.id.bookmark_edit_title)
        val urlInput = view.findViewById<EditText>(R.id.bookmark_edit_url)
        titleInput.setText(record.title)
        urlInput.setText(record.url)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bookmarks_edit_title)
            .setView(view)
            .setNegativeButton(R.string.app_cancel, null)
            .setPositiveButton(R.string.app_ok) { _, _ ->
                save(record, titleInput.text.toString().trim(), urlInput.text.toString().trim())
            }
            .show()
    }

    /** Replaces [record] by a bookmark with the given name and address; the category stays. */
    fun save(record: Record, title: String, url: String) {
        if (url.isEmpty()) {
            CatToast.show(this, R.string.toast_input_empty, 3)
            return
        }
        replace(record, Record(title.ifEmpty { url }, url, 0, record.iconColor))
    }

    /** Offers the categories to put the bookmark into. */
    private fun move(record: Record) {
        val categories = categories()
        val labels = (listOf(getString(R.string.bookmarks_no_category)) + categories.map { it.title.orEmpty() })
            .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bookmarks_move)
            .setItems(labels) { _, which ->
                moveTo(record, if (which == 0) 0L else categories[which - 1].data.toLong())
            }
            .setNegativeButton(R.string.app_cancel, null)
            .show()
    }

    /** Puts [record] into the category with this id; 0 takes it out of all categories. */
    fun moveTo(record: Record, category: Long) {
        // The low four bits hold the category; anything above them is kept as it is.
        val iconColor = (record.iconColor and CATEGORY_MASK.inv()) or (category and CATEGORY_MASK)
        replace(record, Record(record.title, record.url, 0, iconColor))
    }

    private fun replace(old: Record, new: Record) {
        val action = RecordAction(this)
        action.open(true)
        try {
            action.deleteURL(old.url, RecordUnit.TABLE_BOOKMARK)
            action.deleteURL(new.url, RecordUnit.TABLE_BOOKMARK)
            action.addBookmark(new)
        } finally {
            action.close()
        }
        reload()
    }

    fun delete(record: Record) {
        val action = RecordAction(this)
        action.open(true)
        try {
            action.deleteURL(record.url, RecordUnit.TABLE_BOOKMARK)
        } finally {
            action.close()
        }
        reload()
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bookmarks_delete_all)
            .setMessage(R.string.bookmarks_delete_all_message)
            .setNegativeButton(R.string.app_cancel, null)
            .setPositiveButton(R.string.menu_delete) { _, _ ->
                BrowserUnit.clearBookmark(this)
                reload()
            }
            .show()
    }

    companion object {
        /** Id of the "All" chip. */
        const val FILTER_ALL = 0L

        private const val CATEGORY_MASK = 15L
        private const val KEY_SORT = "sort_bookmark"
        private const val KEY_SORT_WEBSITE = "sort_bookmarkDomain"

        private const val MENU_NEW_TAB = 1
        private const val MENU_PRIVATE_TAB = 2
        private const val MENU_EDIT = 3
        private const val MENU_MOVE = 4
        private const val MENU_COPY = 5
        private const val MENU_SHARE = 6
        private const val MENU_DELETE = 7
        private const val MENU_SORT_NAME = 11
        private const val MENU_SORT_CATEGORY = 12
        private const val MENU_SORT_SITE = 13
        private const val MENU_DELETE_ALL = 14

        /** The category a bookmark is in, or 0. */
        @JvmStatic
        fun categoryOf(record: Record): Long = record.iconColor and CATEGORY_MASK
    }
}
