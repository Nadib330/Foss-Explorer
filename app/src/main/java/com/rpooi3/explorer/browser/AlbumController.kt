package com.rpooi3.explorer.browser

import android.view.View

/** A tab: something that owns a preview view and can be brought to the front. */
interface AlbumController {
    val albumView: View?
    fun activate()
    fun deactivate()
}
