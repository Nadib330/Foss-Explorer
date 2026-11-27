package com.rpooi3.explorer.view

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.BrowserContainer
import com.rpooi3.explorer.home.HomePage
import com.rpooi3.explorer.preferences.LayoutSettings

/**
 * The compact tab strip used in landscape and optionally in portrait.
 * It is deliberately separate from the full-screen tab switcher: enabling it
 * adds only this strip and leaves the portrait bottom toolbar in place.
 */
class TabBarController(
    private val root: View,
    private val prefs: android.content.SharedPreferences,
    private val host: Host,
) {
    interface Host {
        fun tabs(): List<NinjaWebView>
        fun currentTab(): NinjaWebView?
        fun selectTab(tab: NinjaWebView)
        fun closeTab(tab: NinjaWebView)
        fun newTab()
    }

    private val scroll: HorizontalScrollView = root.findViewById(R.id.tab_bar_scroll)
    private val tabsView: LinearLayout = root.findViewById(R.id.tab_bar_tabs)
    private val newButton: View = root.findViewById(R.id.tab_bar_new)
    private var landscape = false

    init {
        newButton.setOnClickListener { host.newTab() }
        setLandscape(root.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    /** Recomputes whether the strip is present after rotation or a settings change. */
    fun setLandscape(isLandscape: Boolean) {
        landscape = isLandscape
        root.isVisible = landscape || LayoutSettings.tabBarEnabled(prefs)
        refresh()
    }

    fun refreshVisibility() {
        root.isVisible = landscape || LayoutSettings.tabBarEnabled(prefs)
        refresh()
    }

    fun refresh() {
        if (!root.isVisible) return
        tabsView.removeAllViews()
        val context = root.context
        val current = host.currentTab()
        host.tabs().forEachIndexed { index, tab ->
            tabsView.addView(tabView(context, tab, tab === current, index))
        }
        if (tabsView.childCount > 0) {
            val selected = host.tabs().indexOf(current)
            if (selected >= 0) scroll.post { scroll.smoothScrollTo(selected * dp(context, 188), 0) }
        }
    }

    private fun tabView(context: Context, tab: NinjaWebView, selected: Boolean, index: Int): View {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 12), 0, dp(context, 4), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(
                    MaterialColors.getColor(
                        context,
                        if (selected) com.google.android.material.R.attr.colorSecondaryContainer
                        else com.google.android.material.R.attr.colorSurfaceContainerHighest,
                        0,
                    ),
                )
                if (selected) {
                    setStroke(dp(context, 1), MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0))
                }
            }
            contentDescription = titleOf(context, tab)
            isClickable = true
            isFocusable = true
            setOnClickListener { host.selectTab(tab) }
        }
        val params = LinearLayout.LayoutParams(dp(context, 184), dp(context, 44)).apply {
            marginStart = if (index == 0) dp(context, 8) else dp(context, 4)
            marginEnd = dp(context, 4)
        }
        item.layoutParams = params

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 20), dp(context, 20))
            setImageResource(if (tab.isPrivateTab) R.drawable.icon_private else R.drawable.icon_web)
            imageTintList = android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0),
            )
        }
        item.addView(icon)

        val title = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(context, 8)
            }
            text = titleOf(context, tab)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                MaterialColors.getColor(
                    context,
                    if (selected) com.google.android.material.R.attr.colorOnSecondaryContainer
                    else com.google.android.material.R.attr.colorOnSurface,
                    0,
                ),
            )
        }
        item.addView(title)

        val close = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 36), dp(context, 36))
            background = R.drawable.bg_ripple_small.let { context.getDrawable(it) }
            setImageResource(R.drawable.icon_cross)
            imageTintList = android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0),
            )
            contentDescription = context.getString(R.string.tabs_close_tab)
            setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
            setOnClickListener { host.closeTab(tab) }
        }
        item.addView(close)
        return item
    }

    private fun titleOf(context: Context, tab: NinjaWebView): String =
        if (tab.isShowingHomePage()) context.getString(R.string.app_name)
        else tab.title?.takeIf { it.isNotBlank() && it != tab.url }
            ?: tab.url?.toUri()?.host?.removePrefix("www.")
            ?: tab.url.orEmpty()

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
