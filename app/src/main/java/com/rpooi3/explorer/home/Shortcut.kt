package com.rpooi3.explorer.home

/**
 * A tile on the home page.
 *
 * @property key identifies the tile when the user removes it: the full
 *   address for a bookmark, the host name for a visited or suggested site
 */
data class Shortcut(val title: String, val url: String, val key: String = url)
