package com.rpooi3.explorer.library

import androidx.core.view.isVisible
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R
import com.rpooi3.explorer.database.FaviconHelper
import com.rpooi3.explorer.unit.HelperUtils

/**
 * Base of the full-screen lists that open from the three-dot menu: history,
 * bookmarks and downloads. It provides what the three have in common, laid
 * out like the corresponding screens of Chrome:
 *
 *  - a title bar with a back arrow and a search button; searching replaces
 *    the title with a text field and filters the list while typing,
 *  - an optional row of filter chips,
 *  - a list of rows with an icon, two lines of text and one button at the end,
 *    optionally under date headings,
 *  - a message in the middle of the screen while there is nothing to list.
 *
 * A subclass supplies the rows ([loadRows]) and reacts to taps.
 */
abstract class LibraryActivity : AppCompatActivity() {

    /** One line of the list. */
    sealed interface Row {
        /** A heading above a group of items. */
        data class Header(val text: String) : Row

        /** A text link, e.g. "Clear browsing data…". */
        data class Link(val id: Int, val text: String) : Row

        /**
         * @param url address whose website icon is shown, or null to show [iconRes]
         * @param payload whatever the subclass needs to act on the item
         */
        data class Item(
            val title: String,
            val subtitle: String,
            val url: String?,
            @DrawableRes val iconRes: Int,
            val payload: Any,
        ) : Row
    }

    /** A filter chip. */
    data class Filter(val id: Long, val label: String)

    @get:StringRes
    protected abstract val titleRes: Int

    @get:StringRes
    protected abstract val searchHintRes: Int

    @get:StringRes
    protected abstract val emptyTextRes: Int

    @get:DrawableRes
    protected abstract val emptyIconRes: Int

    /** Icon and description of the button at the end of each item row. */
    @get:DrawableRes
    protected abstract val itemActionIconRes: Int

    @get:StringRes
    protected abstract val itemActionLabelRes: Int

    /** The rows to show for a search text (empty: everything) and the selected filter chip. */
    protected abstract fun loadRows(query: String, filterId: Long): List<Row>

    /** The filter chips; fewer than two means no chip row. The first one is selected. */
    protected open fun filters(): List<Filter> = emptyList()

    /** A line of information above the list, or null. */
    protected open fun infoText(): CharSequence? = null

    protected abstract fun onItemClick(item: Row.Item)

    /** The button at the end of a row was tapped; [anchor] is the button. */
    protected abstract fun onItemAction(item: Row.Item, anchor: View)

    protected open fun onItemLongClick(item: Row.Item, anchor: View) = onItemAction(item, anchor)

    protected open fun onLinkClick(link: Row.Link) = Unit

    /** The three-dot button of the title bar; hidden unless a subclass shows [moreButton]. */
    protected open fun onMoreClick(anchor: View) = Unit

    protected lateinit var moreButton: ImageButton
        private set

    private lateinit var bar: View
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var chipsScroll: View
    private lateinit var chips: ChipGroup
    private lateinit var info: TextView
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private val adapter = RowAdapter()
    private var filterList: List<Filter> = emptyList()

    /** The search text; empty while the search field is closed. */
    var query: String = ""
        private set

    /** The id of the selected filter chip, or [NO_FILTER]. */
    var filterId: Long = NO_FILTER
        private set

    /** The rows currently shown. */
    val rows: List<Row>
        get() = adapter.rows

    val isSearchOpen: Boolean
        get() = searchBar.isVisible

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HelperUtils.initTheme(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_library)

        bar = findViewById(R.id.library_bar)
        searchBar = findViewById(R.id.library_search_bar)
        searchInput = findViewById(R.id.library_search_input)
        chipsScroll = findViewById(R.id.library_chips_scroll)
        chips = findViewById(R.id.library_chips)
        info = findViewById(R.id.library_info)
        list = findViewById(R.id.library_list)
        empty = findViewById(R.id.library_empty)
        moreButton = findViewById(R.id.library_more)

        setTitle(titleRes)
        findViewById<TextView>(R.id.library_title).setText(titleRes)
        findViewById<TextView>(R.id.library_empty_text).setText(emptyTextRes)
        findViewById<ImageView>(R.id.library_empty_icon).setImageResource(emptyIconRes)
        searchInput.setHint(searchHintRes)

        findViewById<View>(R.id.library_back).setOnClickListener { finish() }
        findViewById<View>(R.id.library_search).setOnClickListener { openSearch() }
        findViewById<View>(R.id.library_search_back).setOnClickListener { closeSearch() }
        findViewById<View>(R.id.library_search_clear).setOnClickListener { searchInput.setText("") }
        moreButton.setOnClickListener { onMoreClick(it) }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!isSearchOpen) return
                query = s?.toString()?.trim().orEmpty()
                reload()
            }
        })

        chips.setOnCheckedStateChangeListener { _, checkedIds ->
            val selected = filterList.getOrNull((checkedIds.firstOrNull() ?: 0) - 1)?.id ?: NO_FILTER
            if (selected != filterId) {
                filterId = selected
                reload()
            }
        }

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        // Back closes the search field first, then the screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearchOpen) closeSearch() else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** Reads the rows again and redraws the list, the chips and the empty message. */
    fun reload() {
        updateFilters()
        val newRows = loadRows(query, filterId)
        adapter.submit(newRows)
        val hasItems = newRows.any { it is Row.Item }
        empty.visibility = if (hasItems) View.GONE else View.VISIBLE
        list.visibility = if (hasItems) View.VISIBLE else View.GONE
        val text = infoText()
        info.text = text
        info.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateFilters() {
        val newFilters = filters()
        if (newFilters != filterList) {
            filterList = newFilters
            chips.removeAllViews()
            newFilters.forEachIndexed { index, filter ->
                val chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                    id = index + 1 // View ids start at 1
                    text = filter.label
                    isCheckable = true
                    isCheckedIconVisible = false
                }
                chips.addView(chip)
            }
            if (newFilters.none { it.id == filterId }) filterId = newFilters.firstOrNull()?.id ?: NO_FILTER
            val selected = newFilters.indexOfFirst { it.id == filterId }
            if (selected >= 0) chips.check(selected + 1)
        }
        chipsScroll.visibility = if (newFilters.size > 1) View.VISIBLE else View.GONE
    }

    /** Selects a filter chip by its id. */
    fun selectFilter(id: Long) {
        val index = filterList.indexOfFirst { it.id == id }
        if (index >= 0) chips.check(index + 1)
    }

    fun openSearch() {
        bar.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()
        HelperUtils.showSoftKeyboard(searchInput)
    }

    fun closeSearch() {
        searchBar.visibility = View.GONE
        bar.visibility = View.VISIBLE
        searchInput.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchInput.windowToken, 0)
        if (query.isNotEmpty()) {
            query = ""
            reload()
        }
    }

    /** Types into the search field, as the user would. */
    fun search(text: String) {
        if (!isSearchOpen) openSearch()
        searchInput.setText(text)
    }

    /** Closes the screen and has the browser open [url]: in the current tab, a new tab or a private tab. */
    protected fun openInBrowser(url: String, target: String = TARGET_CURRENT_TAB) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, url).putExtra(EXTRA_TARGET, target))
        finish()
    }

    protected fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
    }

    protected fun shareLink(title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(send, null))
    }

    private inner class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<Row> = emptyList()
            private set

        fun submit(newRows: List<Row>) {
            rows = newRows
            @Suppress("NotifyDataSetChanged") // Lists are short and replaced as a whole.
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.Header -> R.layout.item_library_header
            is Row.Link -> R.layout.item_library_link
            is Row.Item -> R.layout.item_library
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView
            when (val row = rows[position]) {
                is Row.Header -> (view as TextView).text = row.text
                is Row.Link -> {
                    (view as TextView).text = row.text
                    view.setOnClickListener { onLinkClick(row) }
                }
                is Row.Item -> bindItem(view, row)
            }
        }

        private fun bindItem(view: View, item: Row.Item) {
            view.findViewById<TextView>(R.id.library_item_title).text = item.title
            val subtitle = view.findViewById<TextView>(R.id.library_item_subtitle)
            subtitle.text = item.subtitle
            subtitle.visibility = if (item.subtitle.isEmpty()) View.GONE else View.VISIBLE

            val icon = view.findViewById<ImageView>(R.id.library_icon)
            if (item.url != null) {
                // A website icon keeps its own colours.
                ImageViewCompat.setImageTintList(icon, null)
                FaviconHelper.setFavicon(view.context, view, item.url, R.id.library_icon, item.iconRes)
            } else {
                icon.tag = null
                icon.setImageResource(item.iconRes)
                ImageViewCompat.setImageTintList(
                    icon,
                    MaterialColors.getColorStateListOrNull(
                        view.context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ),
                )
            }

            val action = view.findViewById<ImageButton>(R.id.library_item_action)
            action.setImageResource(itemActionIconRes)
            action.contentDescription = getString(itemActionLabelRes)
            action.setOnClickListener { onItemAction(item, it) }
            view.setOnClickListener { onItemClick(item) }
            view.setOnLongClickListener {
                onItemLongClick(item, action)
                true
            }
        }
    }

    companion object {
        /** Result extra: the address the browser should open. */
        const val EXTRA_URL = "com.rpooi3.explorer.library.URL"

        /** Result extra: where to open it, one of the `TARGET_` values. */
        const val EXTRA_TARGET = "com.rpooi3.explorer.library.TARGET"
        const val TARGET_CURRENT_TAB = "current"
        const val TARGET_NEW_TAB = "new"
        const val TARGET_PRIVATE_TAB = "private"

        /** [filterId] while there are no filter chips. */
        const val NO_FILTER = -1L
    }
}
