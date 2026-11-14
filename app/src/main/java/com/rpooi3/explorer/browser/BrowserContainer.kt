/*
 * Foss Explorer: the open tabs.
 * Own code, GPL-3.0. Written for 15.0.0 in place of FOSS Browser's class of
 * the same name: the list is kept by a port of Chromium's tab models
 * (tabs/TabModel.kt), one for normal tabs and one for private tabs.
 */
package com.rpooi3.explorer.browser

import com.rpooi3.explorer.tabs.TabModelSelector
import com.rpooi3.explorer.view.NinjaWebView

/** The tabs of the app, as the browser screen asks for them. */
object BrowserContainer {
    /** Chromium's two tab models. A tab's parent is the tab it was opened from. */
    @JvmStatic
    val selector: TabModelSelector<AlbumController> = TabModelSelector(
        isIncognito = { (it as? NinjaWebView)?.isPrivateTab == true },
        parentOf = { (it as? NinjaWebView)?.predecessor },
        destroy = { (it as? NinjaWebView)?.destroy() },
    )

    @JvmStatic
    fun get(index: Int): AlbumController? = selector.all().getOrNull(index)

    @JvmStatic
    fun add(controller: AlbumController?) {
        if (controller != null) selector.addTab(controller)
    }

    @JvmStatic
    fun add(controller: AlbumController?, index: Int) {
        if (controller != null) selector.addTab(controller, index)
    }

    /** Closes [controller]: it leaves the list and its page is destroyed. */
    @JvmStatic
    fun remove(controller: AlbumController?) {
        if (controller != null) selector.closeTab(controller)
    }

    @JvmStatic
    fun indexOf(controller: AlbumController?): Int = if (controller == null) -1 else selector.all().indexOf(controller)

    /** All tabs, normal and private, in the order they were opened. */
    @JvmStatic
    fun list(): List<AlbumController> = selector.all()

    @JvmStatic
    fun size(): Int = selector.totalTabCount

    @JvmStatic
    fun clear() = selector.closeAllTabs()

    /** Notes that [controller] is the tab in front. */
    @JvmStatic
    fun select(controller: AlbumController?) {
        if (controller != null) selector.selectTab(controller)
    }

    /**
     * The tab that comes to the front when [closing] is closed, by Chromium's
     * rules; null if no tab is left.
     */
    @JvmStatic
    fun nextTabIfClosed(closing: AlbumController, current: AlbumController?, inOverview: Boolean): AlbumController? =
        selector.getNextTabIfClosed(closing, current, inOverview)
}
