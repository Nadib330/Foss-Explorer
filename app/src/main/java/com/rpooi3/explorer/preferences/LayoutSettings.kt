package com.rpooi3.explorer.preferences

import android.content.SharedPreferences

/** Preferences that change the arrangement of the browser bars. */
object LayoutSettings {
    const val KEY_ADDRESS_BAR_BOTTOM = "layout_address_bar_bottom"
    const val KEY_SHOW_TAB_BAR = "layout_show_tab_bar"

    @JvmStatic
    fun addressBarAtBottom(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ADDRESS_BAR_BOTTOM, false)

    @JvmStatic
    fun tabBarEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SHOW_TAB_BAR, false)
}
