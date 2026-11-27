/*
 * Explorer: the floating-pill home-page selector used by Settings and first launch.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.home.HomePage

class HomeStylePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs), OwnLayout {

    init {
        layoutResource = R.layout.preference_home_style
        isSelectable = false
        key = HomePage.KEY_STYLE
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        HomeStyleSelector.bind(holder.itemView, holder.itemView.context)
    }

    /** Called when Settings returns from another screen. */
    fun refreshSelection() {
        notifyChanged()
    }
}
