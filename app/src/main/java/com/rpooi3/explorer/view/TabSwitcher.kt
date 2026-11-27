package com.rpooi3.explorer.view

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

        /** The cards of the other kind of tab are shown now. */
        fun onModeChanged()
    }

    private val grid: RecyclerView = root.findViewById(R.id.tabs_grid)
    private val empty: TextView = root.findViewById(R.id.tabs_empty)
    private val inactive: TextView = root.findViewById(R.id.tabs_inactive)
    private val modes: MaterialButtonToggleGroup = root.findViewById(R.id.tabs_mode)
    private val search: EditText = root.findViewById(R.id.tabs_search)
    private val searchClear: View = root.findViewById(R.id.tabs_search_clear)
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
        root.findViewById<View>(R.id.tabs_new).setOnClickListener { host.newTab(privateMode) }
        root.findViewById<View>(R.id.tabs_menu).setOnClickListener { showMenu(it) }
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
    }

    /** Opens the switcher on the cards of the kind of tab that is in front. */
    fun show() {
        val current = host.currentTab()
        capture(current)
        hiding = false
        root.animate().cancel()
        privateMode = current?.isPrivateTab == true
        modes.check(if (privateMode) R.id.tabs_mode_private else R.id.tabs_mode_normal)
        // Every opening starts with all tabs.
        search.setText("")
        search.clearFocus()
        refresh()
        adapter.tabs.indexOf(current).takeIf { it >= 0 }?.let(grid::scrollToPosition)
        if (!root.isVisible) {
            // The cards settle into place, the reverse of going into a tab.
            root.alpha = 0f
            root.scaleX = ZOOM
            root.scaleY = ZOOM
            root.isVisible = true
        }
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(DURATION).setInterpolator(DecelerateInterpolator())
            .withEndAction(null).start()
    }

    /** Closes the switcher; with [towards], by moving into that card. */
    @JvmOverloads
    fun hide(towards: View? = null) {
        menu?.dismiss()
        if (!root.isVisible || hiding) return
        hiding = true
        if (towards != null && root.width > 0) {
            val card = IntArray(2).also(towards::getLocationInWindow)
            val own = IntArray(2).also(root::getLocationInWindow)
            root.pivotX = card[0] - own[0] + towards.width / 2f
            root.pivotY = card[1] - own[1] + towards.height / 2f
        }
        root.animate().alpha(0f).scaleX(ZOOM).scaleY(ZOOM).setDuration(DURATION)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                hiding = false
                root.isVisible = false
                root.alpha = 1f
                root.scaleX = 1f
                root.scaleY = 1f
                root.pivotX = root.width / 2f
                root.pivotY = root.height / 2f
            }.start()
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
        val waiting = InactiveTabs.list(PreferenceManager.getDefaultSharedPreferences(root.context)).size
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
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
        InactiveTabs.remove(PreferenceManager.getDefaultSharedPreferences(root.context), entry.url)
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
        privateMode = privateTabs
        refresh()
        host.onModeChanged()
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
            // The tab in front stands out as in Chromium: its card in the inverse colours.
            val selected = tab === current
            holder.card.isSelected = selected
            fun color(attr: Int) = MaterialColors.getColor(context, attr, 0)
            holder.card.setCardBackgroundColor(
                color(if (selected) com.google.android.material.R.attr.colorSurfaceInverse else com.google.android.material.R.attr.colorSurfaceContainerHighest),
            )
            val onCard = color(if (selected) com.google.android.material.R.attr.colorOnSurfaceInverse else com.google.android.material.R.attr.colorOnSurface)
            holder.title.setTextColor(onCard)
            (holder.close as ImageView).imageTintList = ColorStateList.valueOf(onCard)
            if (holder.icon.imageTintList != null) holder.icon.imageTintList = ColorStateList.valueOf(onCard)
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
        private const val DURATION = 220L
        private const val ZOOM = 1.12f
        private const val THUMBNAIL_SCALE = 0.4f

        /** Two cards side by side on a phone held upright, more on wider screens. */
        internal fun columns(context: Context): Int =
            columnsFor(context.resources.configuration.screenWidthDp.toFloat())

        internal fun columnsFor(widthDp: Float): Int = (widthDp / CARD_WIDTH_DP).toInt().coerceIn(2, 6)

        private const val CARD_WIDTH_DP = 180
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
