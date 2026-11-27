package com.rpooi3.explorer.view

import com.rpooi3.explorer.profile.ProfilePrefs
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.tabs.InactiveTabs
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.OfflinePage

/**
 * The tab switcher: every open tab as a card with a picture of its page, over
 * the whole screen, as in Chrome for Android.
 *
 * Tapping a card goes to the tab, the cross or a sideways swipe closes it.
 * The top row has "New tab", the switch between tabs and private tabs, and a
 * menu (new tab, new private tab, close all tabs, settings).
 *
 * @param root the switcher's layout (`include_tab_switcher`)
 * @param content the view pages are shown in; pictured for the cards
 * @param home the home page, which lies over [content] while a tab shows it
 */
class TabSwitcher(
    private val root: View,
    private val content: View,
    private val home: View?,
    private val host: Host,
) {
    /** What the switcher needs from the screen that owns the tabs. */
    interface Host {
        /** All open tabs in their order. */
        fun tabs(): List<NinjaWebView>
        fun currentTab(): NinjaWebView?
        fun selectTab(tab: NinjaWebView)

        /** Closes [tab]; the switcher is told by [refresh] when that has happened. */
        fun closeTab(tab: NinjaWebView)
        fun newTab(privateTab: Boolean)
        fun closeAllTabs(privateTabs: Boolean)
        fun openSettings()

        /** Opens [url] in a new tab in front; used for tabs that come back from the inactive section. */
        fun openTab(url: String)

        /** Closes the switcher and goes back to the page, as the arrow of the title row does. */
        fun hideSwitcher()

        /** The cards of the other kind of tab are shown now. */
        fun onModeChanged()
    }

    private val grid: RecyclerView = root.findViewById(R.id.tabs_grid)
    private val empty: TextView = root.findViewById(R.id.tabs_empty)
    private val inactive: TextView = root.findViewById(R.id.tabs_inactive)
    private val modes: MaterialButtonToggleGroup = root.findViewById(R.id.tabs_mode)
    private val search: EditText = root.findViewById(R.id.tabs_search)
    private val searchClear: View = root.findViewById(R.id.tabs_search_clear)
    private val titleRow: View = root.findViewById(R.id.tabs_title_row)
    private val searchRow: View = root.findViewById(R.id.tabs_search_row)
    private val counter = TabCountDrawable(root.context)

    /** What is typed into "Search your tabs". */
    var query: String = ""
        private set
    private val adapter = Adapter()
    private var menu: MainMenuPopup? = null

    /** Whether the cards of the private tabs are shown rather than those of the normal ones. */
    var privateMode: Boolean = false
        private set

    val isShowing: Boolean get() = root.isVisible && !hiding
    private var hiding = false

    init {
        val layout = GridLayoutManager(root.context, columns(root.context))
        grid.layoutManager = layout
        // The activity stays alive when the device is turned, so the number of columns follows the width.
        root.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left == oldRight - oldLeft) return@addOnLayoutChangeListener
            val wanted = columnsFor((right - left) / root.resources.displayMetrics.density)
            if (wanted != layout.spanCount) root.post { layout.spanCount = wanted }
        }
        grid.adapter = adapter
        ItemTouchHelper(SwipeToClose()).attachToRecyclerView(grid)
        root.findViewById<View>(R.id.tabs_menu).setOnClickListener { showMenu(it) }
        // 18.0.0 beta 5: the arrow of the title row goes back to the page, the rounded-square
        // button beside the pill opens a new tab, and the magnifier turns the title row into search.
        root.findViewById<View>(R.id.tabs_back).setOnClickListener { host.hideSwitcher() }
        root.findViewById<View>(R.id.tabs_fab).setOnClickListener { host.newTab(privateMode) }
        root.findViewById<View>(R.id.tabs_search_button).setOnClickListener { openSearch() }
        root.findViewById<View>(R.id.tabs_search_back).setOnClickListener { closeSearch() }
        inactive.setOnClickListener { showInactiveTabs() }
        modes.addOnButtonCheckedListener { _, id, checked ->
            if (checked) setMode(id == R.id.tabs_mode_private)
        }
        // The first half of the switch shows how many tabs are open, as the button of the bottom bar does.
        root.findViewById<MaterialButton>(R.id.tabs_mode_normal).icon = counter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val typed = s?.toString().orEmpty()
                if (typed == query) return
                query = typed
                refresh()
            }
        })
        searchClear.setOnClickListener { search.setText("") }
        // 18.0.0 beta 6 (item 10): the search key of the keyboard puts the keyboard away, as in
        // Chrome; the cards stay narrowed until the field is emptied.
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            ) {
                val manager = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                manager?.hideSoftInputFromWindow(search.windowToken, 0)
                search.clearFocus()
                true
            } else {
                false
            }
        }
    }

    /** Turns the title row into the search field, as the magnifier of Chrome does. */
    private fun openSearch() {
        titleRow.isVisible = false
        searchRow.isVisible = true
        search.requestFocus()
        val manager = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        manager?.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    /** Puts the title row back, with every tab shown again. */
    private fun closeSearch() {
        val manager = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        manager?.hideSoftInputFromWindow(search.windowToken, 0)
        search.setText("")
        search.clearFocus()
        searchRow.isVisible = false
        titleRow.isVisible = true
    }

    /** Opens the switcher on the cards of the kind of tab that is in front. */
    fun show() {
        val current = host.currentTab()
        capture(current)
        hiding = false
        root.animate().cancel()
        privateMode = current?.isPrivateTab == true
        modes.check(if (privateMode) R.id.tabs_mode_private else R.id.tabs_mode_normal)
        // Every opening starts with the title row and with all tabs.
        closeSearch()
        refresh()
        adapter.tabs.indexOf(current).takeIf { it >= 0 }?.let(grid::scrollToPosition)
        // 18.4.0: entering the tab switcher is immediate. Chromium keeps the overview stable
        // while it captures and lays out cards; no zoom or fade is applied to the whole screen.
        root.animate().cancel()
        root.isVisible = true
        root.alpha = 1f
        root.scaleX = 1f
        root.scaleY = 1f
        root.pivotX = root.width / 2f
        root.pivotY = root.height / 2f
    }

    /** Closes the switcher; with [towards], by moving into that card. */
    @JvmOverloads
    fun hide(towards: View? = null) {
        menu?.dismiss()
        // 18.0.0 beta 6 (item 9): the search is put away with the switcher, keyboard and all — it
        // used to stay open behind the page and came back with the next opening.
        closeSearch()
        if (!root.isVisible || hiding) return
        // 18.4.0: opening a card is a direct hand-off to the page. The card itself supplies the
        // user's visual target; Chromium does not zoom the entire overview behind it.
        if (towards != null) {
            root.animate().cancel()
            hiding = false
            root.isVisible = false
            root.alpha = 1f
            root.scaleX = 1f
            root.scaleY = 1f
            root.pivotX = root.width / 2f
            root.pivotY = root.height / 2f
            return
        }
        // The overview closes in the same frame as the page is restored. This also keeps the
        // back arrow and a newly selected card on one Chromium-style, non-animated hand-off.
        hiding = false
        root.animate().cancel()
        root.isVisible = false
        root.alpha = 1f
        root.scaleX = 1f
        root.scaleY = 1f
        root.pivotX = root.width / 2f
        root.pivotY = root.height / 2f
    }

    /** Shows the tabs as they are now. Call after tabs were opened or closed. */
    fun refresh() {
        val ofThisKind = host.tabs().filter { it.isPrivateTab == privateMode }
        adapter.tabs = ofThisKind.filter { matches(it, query) }
        adapter.current = host.currentTab()
        adapter.notifyDataSetChanged()
        counter.count = host.tabs().count { !it.isPrivateTab }
        search.setHint(if (privateMode) R.string.cr_hub_search_hint_incognito else R.string.cr_hub_search_hint)
        searchClear.isVisible = query.isNotEmpty()
        empty.isVisible = adapter.tabs.isEmpty()
        empty.setText(
            when {
                ofThisKind.isNotEmpty() -> R.string.tabs_search_empty
                privateMode -> R.string.tabs_empty_private
                else -> R.string.tabs_empty_normal
            },
        )
        empty.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, if (privateMode) R.drawable.icon_private else R.drawable.icon_tab, 0, 0,
        )
        val waiting = InactiveTabs.list(ProfilePrefs.settings(root.context)).size
        inactive.isVisible = !privateMode && waiting > 0
        inactive.text = root.resources.getQuantityString(R.plurals.tabs_inactive, waiting, waiting)
    }

    /** Shows the cards of the normal tabs. */
    fun showNormalTabs() {
        modes.check(R.id.tabs_mode_normal)
    }

    /**
     * The inactive section: the tabs in it by title and address. A tap opens
     * one again; "Close all" closes them for good.
     */
    internal fun showInactiveTabs() {
        val context = root.context
        val prefs = ProfilePrefs.settings(context)
        val entries = InactiveTabs.list(prefs)
        if (entries.isEmpty()) return refresh()
        val labels = entries.map { if (it.title.isBlank()) it.url else it.title + "\n" + it.url }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(context.resources.getQuantityString(R.plurals.tabs_inactive, entries.size, entries.size))
            .setItems(labels) { _, which -> openInactive(entries[which]) }
            .setNeutralButton(R.string.tabs_inactive_close_all) { _, _ ->
                InactiveTabs.clear(prefs)
                refresh()
            }
            .setNegativeButton(R.string.app_cancel, null)
            .show()
    }

    /** Brings [entry] back as a tab in front. */
    internal fun openInactive(entry: InactiveTabs.Entry) {
        InactiveTabs.remove(ProfilePrefs.settings(root.context), entry.url)
        host.openTab(entry.url)
        refresh()
    }

    /** Takes the picture for the card of [tab], which has to be the tab on screen. */
    fun capture(tab: NinjaWebView?) {
        if (tab == null || content.width <= 0 || content.height <= 0) return
        try {
            val bitmap = Bitmap.createBitmap(
                (content.width * THUMBNAIL_SCALE).toInt().coerceAtLeast(1),
                (content.height * THUMBNAIL_SCALE).toInt().coerceAtLeast(1),
                Bitmap.Config.RGB_565,
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(MaterialColors.getColor(content, com.google.android.material.R.attr.colorSurface))
            canvas.scale(THUMBNAIL_SCALE, THUMBNAIL_SCALE)
            content.draw(canvas)
            if (home?.isVisible == true) home.draw(canvas)
            tab.thumbnail = bitmap
        } catch (e: RuntimeException) {
            // A page that cannot be drawn this way keeps its old picture, or none.
        } catch (e: OutOfMemoryError) {
            tab.thumbnail = null
        }
    }

    private fun setMode(privateTabs: Boolean) {
        if (privateMode == privateTabs) return
        // Chromium's tab model is a horizontal two-page surface: update the model immediately,
        // then let the newly selected section slide into place while the bottom mode bar settles.
        privateMode = privateTabs
        refresh()
        host.onModeChanged()
        animateModeSwitch(if (privateTabs) 1f else -1f)
    }

    /** Slides the selected tab section in and gives the bottom mode bar Chromium's short settle. */
    private fun animateModeSwitch(direction: Float) {
        val distance = 32f * root.resources.displayMetrics.density
        grid.animate().cancel()
        grid.translationX = direction * distance
        grid.alpha = 0f
        grid.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(MODE_CONTENT_DURATION)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val modeBar = root.findViewById<View>(R.id.tabs_pill)
        modeBar.animate().cancel()
        modeBar.scaleX = MODE_BAR_START_SCALE
        modeBar.scaleY = MODE_BAR_START_SCALE
        modeBar.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(MODE_BAR_DURATION)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun showMenu(anchor: View) {
        menu?.dismiss()
        val popup = MainMenuPopup(anchor, R.menu.menu_tab_switcher) { id -> onMenuItem(id) }
        popup.menu.findItem(R.id.tabs_menu_close_all).apply {
            setTitle(if (privateMode) R.string.tabs_close_all_private else R.string.tabs_close_all)
            isEnabled = adapter.tabs.isNotEmpty()
        }
        menu = popup
        popup.show()
    }

    internal fun onMenuItem(id: Int) {
        when (id) {
            R.id.tabs_menu_new_tab -> host.newTab(false)
            R.id.tabs_menu_new_private_tab -> host.newTab(true)
            R.id.tabs_menu_close_all -> confirmCloseAll()
            R.id.tabs_menu_settings -> host.openSettings()
        }
    }

    private fun confirmCloseAll() {
        val private = privateMode
        MaterialAlertDialogBuilder(root.context)
            .setTitle(if (private) R.string.tabs_close_all_private_question else R.string.tabs_close_all_question)
            .setMessage(R.string.tabs_close_all_message)
            .setNegativeButton(R.string.app_cancel, null)
            .setPositiveButton(R.string.tabs_close_all_confirm) { _, _ -> host.closeAllTabs(private) }
            .show()
    }

    private inner class Adapter : RecyclerView.Adapter<Card>() {
        var tabs: List<NinjaWebView> = emptyList()
        var current: NinjaWebView? = null

        override fun getItemCount(): Int = tabs.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Card =
            Card(LayoutInflater.from(parent.context).inflate(R.layout.item_tab_card, parent, false))

        override fun onBindViewHolder(holder: Card, position: Int) {
            val tab = tabs[position]
            val context = holder.itemView.context
            holder.tab = tab
            val title = titleOf(tab)
            holder.title.text = title
            val favicon = tab.favicon
            if (favicon != null && !tab.isShowingHomePage) {
                holder.icon.imageTintList = null
                holder.icon.setImageBitmap(favicon)
            } else {
                holder.icon.setImageResource(if (tab.isPrivateTab) R.drawable.icon_private else R.drawable.icon_web)
                holder.icon.imageTintList = MaterialColors.getColorStateListOrNull(
                    context, com.google.android.material.R.attr.colorOnSurfaceVariant,
                )
            }
            holder.thumbnail.setImageBitmap(tab.thumbnail)
            // 18.0.0 beta 4: the band under the picture carries the colour of the site, as the card
            // of Chrome does — the colour of the top of the page, read from its own picture.
            fun color(attr: Int) = MaterialColors.getColor(context, attr, 0)
            val band = bandColour(tab.thumbnail) ?: color(
                if (tab === current) com.google.android.material.R.attr.colorSurfaceContainerHigh
                else com.google.android.material.R.attr.colorSurfaceContainerHighest,
            )
            holder.band.setBackgroundColor(band)
            val onBand = if (isDark(band)) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            holder.title.setTextColor(onBand)
            // The tab in front is ringed, as Chromium rings it: the picture alone would not say it.
            val selected = tab === current
            holder.card.isSelected = selected
            holder.card.strokeWidth = if (selected) (2 * context.resources.displayMetrics.density).toInt() else 0
            holder.card.strokeColor = color(androidx.appcompat.R.attr.colorPrimary)
            holder.card.setCardBackgroundColor(band)
            (holder.close as ImageView).imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
            if (holder.icon.imageTintList != null) holder.icon.imageTintList = ColorStateList.valueOf(onBand)
            holder.card.contentDescription = title
            holder.card.setOnClickListener {
                host.selectTab(tab)
                hide(holder.itemView)
            }
            holder.close.setOnClickListener { host.closeTab(tab) }
        }
    }

    /** The name a tab goes by in the switcher. */
    internal fun titleOf(tab: NinjaWebView): String =
        if (tab.isShowingHomePage) root.context.getString(R.string.menu_new_tab)
        else tab.title?.takeIf { it.isNotBlank() && it != tab.url }
            ?: tab.url?.takeIf { OfflinePage.isInternalUrl(it) }
            ?: tab.url?.toUri()?.host?.removePrefix("www.")
            ?: tab.url.orEmpty()

    /** Whether [tab] has every word of [text] in its title or address. */
    internal fun matches(tab: NinjaWebView, text: String): Boolean {
        val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = (titleOf(tab) + " " + tab.url.orEmpty()).lowercase()
        return words.all { it in hay }
    }

    private class Card(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.tab_card)
        val icon: ImageView = view.findViewById(R.id.tab_card_icon)
        val title: TextView = view.findViewById(R.id.tab_card_title)
        val close: View = view.findViewById(R.id.tab_card_close)
        val thumbnail: ImageView = view.findViewById(R.id.tab_card_thumbnail)
        val band: View = view.findViewById(R.id.tab_card_bar)
        var tab: NinjaWebView? = null
    }

    /** Swiping a card off to either side closes its tab. */
    private inner class SwipeToClose : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val tab = (viewHolder as Card).tab
            if (tab != null) host.closeTab(tab)
            // If the tab is still there (closing has to be confirmed), its card comes back.
            refresh()
        }
    }

    companion object {
        private const val MODE_CONTENT_DURATION = 220L
        private const val MODE_BAR_DURATION = 180L
        private const val MODE_BAR_START_SCALE = 0.94f
        private const val THUMBNAIL_SCALE = 0.4f

        /** Two cards side by side on a phone held upright, more on wider screens. */
        internal fun columns(context: Context): Int =
            columnsFor(context.resources.configuration.screenWidthDp.toFloat())

        internal fun columnsFor(widthDp: Float): Int = (widthDp / CARD_WIDTH_DP).toInt().coerceIn(2, 6)

        private const val CARD_WIDTH_DP = 180

        /**
         * The colour of the band under a card: the average of the top of the picture of the page, which
         * is where a site paints its own colour. Null when the picture cannot say it, and the card then
         * keeps the colours of the theme.
         */
        @JvmStatic
        internal fun bandColour(thumbnail: Bitmap?): Int? {
            val picture = thumbnail ?: return null
            if (picture.width < 4 || picture.height < 4) return null
            val rows = (picture.height * 0.06f).toInt().coerceIn(1, picture.height)
            val step = (picture.width / 12).coerceAtLeast(1)
            var red = 0L
            var green = 0L
            var blue = 0L
            var taken = 0
            for (y in 0 until rows) {
                var x = step / 2
                while (x < picture.width) {
                    val pixel = try {
                        picture.getPixel(x, y)
                    } catch (e: RuntimeException) {
                        return null
                    }
                    red += (pixel shr 16) and 0xFF
                    green += (pixel shr 8) and 0xFF
                    blue += pixel and 0xFF
                    taken++
                    x += step
                }
            }
            if (taken == 0) return null
            val colour = android.graphics.Color.rgb(
                (red / taken).toInt(),
                (green / taken).toInt(),
                (blue / taken).toInt(),
            )
            // A page that was never drawn is black, and a white one is white: neither says what the
            // site's colour is, so the card keeps the colours of the theme instead of a black band.
            val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(colour)
            if (luminance < 0.02 || luminance > 0.98) return null
            return colour
        }

        /** Whether white writing reads better than black on [colour]. */
        @JvmStatic
        internal fun isDark(colour: Int): Boolean =
            androidx.core.graphics.ColorUtils.calculateLuminance(colour) < 0.5

    }
}

/** The picture of a page on a tab card: as wide as the card, showing the top of the page. */
class TabThumbnailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width * HEIGHT_PER_WIDTH).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    override fun setImageBitmap(bitmap: Bitmap?) {
        super.setImageBitmap(bitmap)
        fit()
    }

    /** Scales the picture to the card's width and keeps its top edge, as Chrome's cards do. */
    private fun fit() {
        val picture = drawable ?: return
        if (width <= 0 || picture.intrinsicWidth <= 0) return
        val scale = width / picture.intrinsicWidth.toFloat()
        imageMatrix = Matrix().apply { setScale(scale, scale) }
    }

    companion object {
        const val HEIGHT_PER_WIDTH = 1.1f
    }
}

/**
 * How a new tab comes onto the screen, as in Chrome: its page grows out of
 * the lower corner on the side where reading starts until it fills the place
 * of the page before.
 */
object NewTabAnimation {
    const val DURATION = 300L
    private const val START_SCALE = 0.25f

    /** Lets [views] (the page and what lies over it) grow into place together. */
    @JvmStatic
    fun run(vararg views: View?) {
        for (view in views) {
            if (view == null || !view.isLaidOut) continue
            view.animate().cancel()
            val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL
            view.pivotX = if (rtl) view.width.toFloat() else 0f
            view.pivotY = view.height.toFloat()
            view.scaleX = START_SCALE
            view.scaleY = START_SCALE
            view.alpha = 0f
            view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(DURATION)
                // Fast at first, settling slowly: Material's "emphasized decelerate".
                .setInterpolator(android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
                .withEndAction {
                    view.pivotX = view.width / 2f
                    view.pivotY = view.height / 2f
                }
                .start()
        }
    }
}

/**
 * The number of open tabs in a rounded box: Chromium's tab counter, as the
 * icon of the first half of the switch at the top of the tab switcher.
 */
class TabCountDrawable(context: Context) : android.graphics.drawable.Drawable() {
    private val density = context.resources.displayMetrics.density
    private val box = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textSize = 11f * density
    }

    var count: Int = 0
        set(value) {
            field = value
            invalidateSelf()
        }

    /** What stands in the box: the number, or ":D" for a hundred tabs and more, as in Chromium. */
    val label: String get() = if (count > 99) ":D" else count.toString()

    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        val inset = 3f * density
        val r = 5f * density
        canvas.drawRoundRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, r, r, box)
        canvas.drawText(label, b.exactCenterX(), b.exactCenterY() - (text.descent() + text.ascent()) / 2f, text)
    }

    override fun setTintList(tint: ColorStateList?) {
        val color = tint?.defaultColor ?: android.graphics.Color.BLACK
        box.color = color
        text.color = color
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = (24 * density).toInt()
    override fun getIntrinsicHeight(): Int = (24 * density).toInt()
}
