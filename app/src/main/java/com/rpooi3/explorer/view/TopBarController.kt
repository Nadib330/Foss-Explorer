package com.rpooi3.explorer.view

import android.content.SharedPreferences
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import com.google.android.material.color.MaterialColors
import android.view.ViewGroup
import androidx.core.content.edit
import com.rpooi3.explorer.R

/**
 * Controls the two bars of the browser screen.
 *
 * The top bar (`@id/topBar`) holds the back arrow, the address pill, the menu
 * button and the find-in-page bar. The bottom bar (`@id/bottomBar`) holds
 * home, new tab and the tab counter. Web pages are laid out between them, so
 * nothing of a page is covered. The user can hide both bars to read full
 * screen; a small button in the top corner brings them back. The choice is
 * remembered.
 *
 * The home page has no top bar at all: it brings its own search box and its
 * own three-dot button. While the keyboard is open the bottom bar steps
 * aside, so that it does not sit on top of the keyboard.
 */
class TopBarController(
    private val root: ViewGroup,
    private val prefs: SharedPreferences,
) {
    private val bar: View = root.findViewById(R.id.topBar)
    private val bottomBar: View = root.findViewById(R.id.bottomBar)
    private val addressBar: View = root.findViewById(R.id.appBar)
    private val addressPill: View = root.findViewById(R.id.addressPill)
    private val showButton: View = root.findViewById(R.id.fab_showAppBar)
    private var keyboardVisible = false

    /** Whether the user has hidden the bars. */
    val isHidden: Boolean
        get() = prefs.getBoolean(KEY_HIDDEN, false)

    /** Whether the current tab shows the home page, which has no top bar. */
    var isHomeMode: Boolean = false
        private set

    init {
        applyHidden(isHidden, animate = false)
    }

    /** Hides or shows both bars and remembers the choice. */
    fun setHidden(hidden: Boolean) {
        prefs.edit { putBoolean(KEY_HIDDEN, hidden) }
        applyHidden(hidden, animate = true)
    }

    /** Removes the top bar for the home page and brings it back for websites. */
    fun setHomeMode(home: Boolean) {
        if (home == isHomeMode) return
        isHomeMode = home
        bar.visibility = barVisibility(isHidden)
        applyHomeColors()
    }

    /** Makes the address bar visible again, e.g. after find-in-page was closed. */
    fun showAddressBar() {
        addressBar.visibility = View.VISIBLE
        addressPill.visibility = View.VISIBLE
    }

    /** Tells the controller whether the on-screen keyboard is open. */
    fun setKeyboardVisible(visible: Boolean) {
        if (visible == keyboardVisible) return
        keyboardVisible = visible
        bottomBar.visibility = bottomBarVisibility(isHidden)
    }

    private fun bottomBarVisibility(hidden: Boolean): Int =
        if (hidden || keyboardVisible) View.GONE else View.VISIBLE

    private fun barVisibility(hidden: Boolean): Int =
        if (hidden || isHomeMode) View.GONE else View.VISIBLE

    /**
     * The strip behind the status bar is the root's background. Without a top
     * bar it has to continue the home page's colour instead of the bar's.
     */
    private fun applyHomeColors() {
        val attr = if (isHomeMode) com.google.android.material.R.attr.colorSurface
        else com.google.android.material.R.attr.colorSurfaceContainer
        root.setBackgroundColor(MaterialColors.getColor(root, attr))
    }

    private fun applyHidden(hidden: Boolean, animate: Boolean) {
        val target = barVisibility(hidden)
        if (animate && root.isLaidOut && bar.visibility != target) {
            TransitionManager.beginDelayedTransition(
                root,
                TransitionSet()
                    .addTransition(Slide(Gravity.TOP).addTarget(bar))
                    .addTransition(Slide(Gravity.BOTTOM).addTarget(bottomBar))
                    .setDuration(SLIDE_DURATION_MS),
            )
        }
        bar.visibility = target
        bottomBar.visibility = bottomBarVisibility(hidden)
        showButton.visibility = if (hidden) View.VISIBLE else View.GONE
    }

    companion object {
        /** Preference that stores whether the bars are hidden. */
        const val KEY_HIDDEN = "hideToolbar"
        private const val SLIDE_DURATION_MS = 200L
    }
}
