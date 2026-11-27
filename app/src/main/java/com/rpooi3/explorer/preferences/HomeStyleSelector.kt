/*
 * Explorer: the shared spring-morphing Classic/New selector.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.preferences

import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.rpooi3.explorer.R
import com.rpooi3.explorer.home.HomePage
import com.rpooi3.explorer.view.SpringPillSelector

/**
 * Binds the one home-page style control used by Settings and first launch. Both entry points use
 * the same active-profile preference and the same spring-morphing pill, not two subtly different
 * selectors.
 */
object HomeStyleSelector {
    @JvmStatic
    fun bind(root: View, context: android.content.Context, onChosen: ((String) -> Unit)? = null) {
        val track = root.findViewById<ViewGroup>(R.id.home_style_pill)
        val group = root.findViewById<MaterialButtonToggleGroup>(R.id.home_style_group)
        val classic = root.findViewById<MaterialButton>(R.id.home_style_classic_button)
        val newButton = root.findViewById<MaterialButton>(R.id.home_style_new_button)
        val motion = SpringPillSelector(track, group, listOf(classic, newButton))

        fun refresh() {
            val selected = HomePage.style(context)
            motion.select(
                if (selected == HomePage.STYLE_CLASSIC) classic.id else newButton.id,
                animate = false,
            )
        }

        fun choose(style: String) {
            HomePage.setStyle(context, style)
            motion.select(
                if (style == HomePage.STYLE_CLASSIC) classic.id else newButton.id,
                animate = true,
            )
            onChosen?.invoke(style)
        }

        classic.setOnClickListener { choose(HomePage.STYLE_CLASSIC) }
        newButton.setOnClickListener { choose(HomePage.STYLE_NEW) }
        refresh()
    }
}
