// Copyright 2014 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file (third_party/chromium-settings/LICENSE).
//
// Kotlin port, for Foss Explorer, of the tab list of Chromium for Android:
// the interfaces TabList and TabModel
// (chrome/browser/tabmodel/android/java/src/org/chromium/chrome/browser/tabmodel/)
// and the rules of TabModelImpl.getNextTabIfClosed for the tab that comes to
// the front when one is closed. Chromium's Tab is replaced by a type
// parameter: what a tab is and how it loads pages is not part of this file.
package com.rpooi3.explorer.tabs

/** Chromium's TabList: an ordered list of tabs, one of which is the current one. */
interface TabList<T : Any> {
    /** Number of tabs in the list. */
    val count: Int

    /** The tab at [index], or null if there is none. */
    fun getTabAt(index: Int): T?

    /** The index of [tab], or [INVALID_TAB_INDEX] if it is not in the list. */
    fun indexOf(tab: T?): Int

    /** The index of the current tab, or [INVALID_TAB_INDEX] if there are no tabs. */
    fun index(): Int

    companion object {
        const val INVALID_TAB_INDEX = -1
    }
}

/**
 * Chromium's TabModel: the tabs of one kind, normal or Incognito. Chromium
 * keeps the two kinds in two models side by side; so does [TabModelSelector],
 * of which this is one half.
 */
class TabModel<T : Any> internal constructor(
    /** Whether this is the model of the Incognito (private) tabs. */
    val isIncognito: Boolean,
    private val selector: TabModelSelector<T>,
) : TabList<T> {
    private val tabs: List<T> get() = selector.all().filter { selector.isIncognito(it) == isIncognito }

    /** The tab of this model that was in front last. */
    internal var current: T? = null

    override val count: Int get() = tabs.size
    override fun getTabAt(index: Int): T? = tabs.getOrNull(index)
    override fun indexOf(tab: T?): Int = if (tab == null) TabList.INVALID_TAB_INDEX else tabs.indexOf(tab)
    override fun index(): Int = indexOf(current?.takeIf { it in tabs } ?: tabs.firstOrNull())

    /** Chromium's TabModelUtils.getCurrentTab. */
    fun currentTab(): T? = getTabAt(index())

    /**
     * The tab to select when [tabToClose] is closed. Chromium's rules, in
     * its words:
     *
     *  - If closing a background tab, keep the current tab selected.
     *  - Otherwise, if not in overview mode, select the parent tab if it exists.
     *  - Otherwise, select an adjacent tab if one exists.
     *  - Otherwise, if closing the last incognito tab, select the current normal tab.
     *  - Otherwise, select nothing.
     *
     * @param currentTab the tab in front, of whichever model
     * @param inOverview whether the tab switcher is open ("overview mode")
     */
    fun getNextTabIfClosed(tabToClose: T, currentTab: T?, inOverview: Boolean): T? {
        val closingTabIndex = indexOf(tabToClose)
        if (closingTabIndex == TabList.INVALID_TAB_INDEX) return currentTab
        val adjacentTab = getTabAt(if (closingTabIndex == 0) 1 else closingTabIndex - 1)
        val parentTab = selector.parentOf(tabToClose)?.takeIf { it !== tabToClose && it in selector.all() }

        return when {
            tabToClose !== currentTab && currentTab != null -> currentTab
            parentTab != null && !inOverview -> parentTab
            adjacentTab != null -> adjacentTab
            isIncognito -> selector.getModel(false).currentTab()
            else -> null
        }
    }
}

/**
 * Chromium's TabModelSelector: the model of the normal tabs and the model of
 * the Incognito tabs. The tabs themselves are kept here in the order they
 * were opened.
 *
 * @param isIncognito whether a tab is a private one
 * @param parentOf the tab a tab was opened from, if any (Chromium: Tab.getParentId)
 * @param destroy called for a tab that leaves the list for good
 */
class TabModelSelector<T : Any>(
    internal val isIncognito: (T) -> Boolean,
    internal val parentOf: (T) -> T?,
    private val destroy: (T) -> Unit,
) {
    // Read from several threads (the pages' callbacks), changed on the main one.
    private val tabs = java.util.concurrent.CopyOnWriteArrayList<T>()
    private val normal = TabModel(false, this)
    private val incognito = TabModel(true, this)

    /** All tabs of both models, in the order they were opened. */
    fun all(): List<T> = java.util.Collections.unmodifiableList(tabs)

    /** Chromium's getModel(incognito). */
    fun getModel(incognito: Boolean): TabModel<T> = if (incognito) this.incognito else normal

    /** The model [tab] belongs to. */
    fun getModelForTab(tab: T): TabModel<T> = getModel(isIncognito(tab))

    val totalTabCount: Int get() = tabs.size

    fun addTab(tab: T) {
        tabs.add(tab)
    }

    fun addTab(tab: T, index: Int) {
        if (index in 0..tabs.size) tabs.add(index, tab)
    }

    /** Notes that [tab] is in front now (Chromium: TabModel.setIndex). */
    fun selectTab(tab: T) {
        if (tab in tabs) getModelForTab(tab).current = tab
    }

    /** Takes [tab] out of its model and destroys it. */
    fun closeTab(tab: T) {
        destroy(tab)
        tabs.remove(tab)
        for (model in listOf(normal, incognito)) if (model.current === tab) model.current = null
    }

    fun closeAllTabs() {
        for (tab in tabs) destroy(tab)
        tabs.clear()
        normal.current = null
        incognito.current = null
    }

    /**
     * The tab that comes to the front when [tabToClose] is closed: what its
     * model says ([TabModel.getNextTabIfClosed]); if that is nothing (the
     * last normal tab was closed while private tabs are open), any tab that is left.
     */
    fun getNextTabIfClosed(tabToClose: T, currentTab: T?, inOverview: Boolean): T? =
        getModelForTab(tabToClose).getNextTabIfClosed(tabToClose, currentTab, inOverview)?.takeIf { it !== tabToClose }
            ?: tabs.lastOrNull { it !== tabToClose }
}
