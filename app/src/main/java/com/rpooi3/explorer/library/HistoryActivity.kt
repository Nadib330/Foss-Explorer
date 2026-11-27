package com.rpooi3.explorer.library

import android.content.Intent
import android.view.View
import com.rpooi3.explorer.view.MainMenuPopup
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Clear
import com.rpooi3.explorer.database.Record
import com.rpooi3.explorer.database.RecordAction
import com.rpooi3.explorer.database.RecordUnit
import com.rpooi3.explorer.unit.HelperUtils

/**
 * The history screen, laid out like Chrome's: a "Clear browsing data…" link,
 * then the visited pages under a heading per day, newest first. Each row
 * shows the page title and its website, and ends in an X that removes the
 * entry. A tap opens the page; a long press offers more.
 */
class HistoryActivity : LibraryActivity() {

    override val titleRes = R.string.album_title_history
    override val searchHintRes = R.string.history_search_hint
    override val emptyTextRes = R.string.history_empty
    override val emptyIconRes = R.drawable.icon_history
    override val itemActionIconRes = R.drawable.icon_close
    override val itemActionLabelRes = R.string.history_remove

    override fun loadRows(query: String, filterId: Long): List<Row> {
        val records = readHistory()
            .filter { matches(it, query) }
            .sortedByDescending { it.time }
        val rows = ArrayList<Row>(records.size + 8)
        // Like Chrome, the link is always there, also while the history is empty.
        if (query.isEmpty()) rows += Row.Link(LINK_CLEAR, getString(R.string.history_clear_data))

        val now = System.currentTimeMillis()
        val today = getString(R.string.date_today)
        val yesterday = getString(R.string.date_yesterday)
        var lastDay = Int.MIN_VALUE
        for (record in records) {
            val day = DateGroups.dayKey(record.time)
            if (day != lastDay) {
                lastDay = day
                rows += Row.Header(DateGroups.heading(record.time, now, today, yesterday))
            }
            rows += Row.Item(
                title = record.title?.takeIf { it.isNotBlank() } ?: record.url,
                subtitle = HelperUtils.domain(record.url).orEmpty(),
                url = record.url,
                iconRes = R.drawable.icon_web,
                payload = record,
            )
        }
        return rows
    }

    private fun readHistory(): List<Record> {
        val action = RecordAction(this)
        action.open(false)
        try {
            return action.listHistory(this).filter { !it.url.isNullOrBlank() }
        } finally {
            action.close()
        }
    }

    private fun matches(record: Record, query: String): Boolean =
        query.isEmpty() ||
            record.title?.contains(query, ignoreCase = true) == true ||
            record.url.contains(query, ignoreCase = true)

    override fun onItemClick(item: Row.Item) = openInBrowser((item.payload as Record).url)

    /** The X at the end of a row. */
    override fun onItemAction(item: Row.Item, anchor: View) = remove(item.payload as Record)

    override fun onItemLongClick(item: Row.Item, anchor: View) {
        val record = item.payload as Record
        val popup = MainMenuPopup(anchor) { itemId ->
            when (itemId) {
                MENU_NEW_TAB -> openInBrowser(record.url, TARGET_NEW_TAB)
                MENU_PRIVATE_TAB -> openInBrowser(record.url, TARGET_PRIVATE_TAB)
                MENU_COPY -> copyToClipboard(record.url)
                MENU_SHARE -> shareLink(item.title, record.url)
                MENU_REMOVE -> remove(record)
            }
        }
        popup.menu.add(0, MENU_NEW_TAB, 0, R.string.library_open_new_tab)
        popup.menu.add(0, MENU_PRIVATE_TAB, 1, R.string.library_open_private_tab)
        popup.menu.add(0, MENU_COPY, 2, R.string.library_copy_link)
        popup.menu.add(0, MENU_SHARE, 3, R.string.menu_share_link)
        popup.menu.add(0, MENU_REMOVE, 4, R.string.history_remove)
        popup.show()
    }

    override fun onLinkClick(link: Row.Link) {
        if (link.id == LINK_CLEAR) startActivity(Intent(this, Settings_Clear::class.java))
    }

    private fun remove(record: Record) {
        val action = RecordAction(this)
        action.open(true)
        try {
            action.deleteURL(record.url, RecordUnit.TABLE_HISTORY)
        } finally {
            action.close()
        }
        reload()
    }

    companion object {
        const val LINK_CLEAR = 1
        private const val MENU_NEW_TAB = 1
        private const val MENU_PRIVATE_TAB = 2
        private const val MENU_COPY = 3
        private const val MENU_SHARE = 4
        private const val MENU_REMOVE = 5
    }
}
