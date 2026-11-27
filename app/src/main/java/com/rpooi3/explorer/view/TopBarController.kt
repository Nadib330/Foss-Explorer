package com.rpooi3.explorer.view

import android.content.SharedPreferences
import android.content.res.Configuration
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.google.android.material.color.MaterialColors
import androidx.core.content.edit
import com.rpooi3.explorer.R
import com.rpooi3.explorer.preferences.LayoutSettings

/**
 * Controls the browser bars and their screen position.
 *
 * The address bar is normally at the top. The Layout setting can move it to
 * the bottom of the browsing area; that choice is intentionally ignored while
 * the home page is showing, so the home page layout never changes. Landscape
 * always keeps the address bar at the top, below the compact tab strip, like
 * the desktop-style reference layout.
 */
class TopBarController(
    private val root: ViewGroup,
    private val prefs: SharedPreferences,
) {
    private val bar: View = root.findViewById(R.id.topBar)
    private val bottomBar: View = root.findViewById(R.id.bottomBar)
    private val tabBar: View = root.findViewById(R.id.tabBar)
    private val addressBar: View = root.findViewById(R.id.appBar)
    private val addressPill: View = root.findViewById(R.id.addressPill)
    private val showButton: View = root.findViewById(R.id.fab_showAppBar)
    private val landscapeForward: View? = root.findViewById(R.id.button_forward_landscape)
    private val landscapeHome: View? = root.findViewById(R.id.button_home_landscape)
    private val landscapeBookmark: View? = root.findViewById(R.id.button_bookmark_landscape)
    private val landscapeTabs: View? = root.findViewById(R.id.button_tabs_landscape)
    private val landscapeShare: View? = root.findViewById(R.id.button_share)
    private val profileButton: View? = root.findViewById(R.id.button_profile)
    private val shieldButton: View? = root.findViewById(R.id.button_shield)
    private var keyboardVisible = false

    /** Whether the user has hidden the bars. */
    val isHidden: Boolean
        get() = prefs.getBoolean(KEY_HIDDEN, false)

    /** Whether the current tab shows the home page, which has no top bar. */
    var isHomeMode: Boolean = false
        private set

    init {
        applyLandscapeControls()
        applyLayout()
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
        applyLandscapeControls()
        applyLayout()
        bar.visibility = barVisibility(isHidden)
        applyHomeColors()
    }

    /** Re-applies position and landscape controls after rotation or a layout preference change. */
    fun refreshLayout() {
        applyLandscapeControls()
        applyLayout()
        bar.visibility = barVisibility(isHidden)
        bottomBar.visibility = bottomBarVisibility(isHidden)
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

    private fun isLandscape(): Boolean =
        root.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun tabStripVisible(): Boolean = tabBar.visibility == View.VISIBLE

    private fun bottomAddressEnabled(): Boolean =
        !isLandscape() && !isHomeMode && LayoutSettings.addressBarAtBottom(prefs)

    private fun bottomBarVisibility(hidden: Boolean): Int =
        if (hidden || keyboardVisible || isLandscape()) View.GONE else View.VISIBLE

    private fun barVisibility(hidden: Boolean): Int =
        if (hidden || isHomeMode) View.GONE else View.VISIBLE

    /** Updates the arrangement of the direct children of the RelativeLayout root. */
    private fun applyLayout() {
        val bottomAddress = bottomAddressEnabled()
        positionBar(bottomAddress)
        for (id in intArrayOf(R.id.main_content, R.id.home_page, R.id.overlay_content, R.id.incognito_page)) {
            root.findViewById<View>(id)?.let { positionContent(it, bottomAddress) }
        }
    }

    private fun positionBar(bottomAddress: Boolean) {
        val params = bar.layoutParams as? RelativeLayout.LayoutParams ?: return
        params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
        params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        params.removeRule(RelativeLayout.BELOW)
        params.removeRule(RelativeLayout.ABOVE)
        if (bottomAddress) {
            params.addRule(RelativeLayout.ABOVE, R.id.bottomBar)
        } else if (tabStripVisible()) {
            params.addRule(RelativeLayout.BELOW, R.id.tabBar)
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
        }
        bar.layoutParams = params
    }

    private fun positionContent(view: View, bottomAddress: Boolean) {
        val params = view.layoutParams as? RelativeLayout.LayoutParams ?: return
        params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
        params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        params.removeRule(RelativeLayout.BELOW)
        params.removeRule(RelativeLayout.ABOVE)
        if (bottomAddress) {
            if (tabStripVisible()) params.addRule(RelativeLayout.BELOW, R.id.tabBar)
            else params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
            params.addRule(RelativeLayout.ABOVE, R.id.topBar)
        } else {
            if (isHomeMode) {
                if (tabStripVisible()) params.addRule(RelativeLayout.BELOW, R.id.tabBar)
                else params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
            } else {
                params.addRule(RelativeLayout.BELOW, R.id.topBar)
            }
            params.addRule(RelativeLayout.ABOVE, R.id.bottomBar)
        }
        view.layoutParams = params
    }

    /** Shows desktop-like navigation actions in landscape and the compact portrait actions otherwise. */
    private fun applyLandscapeControls() {
        val landscape = isLandscape()
        landscapeForward?.visibility = if (landscape) View.VISIBLE else View.GONE
        landscapeHome?.visibility = if (landscape) View.VISIBLE else View.GONE
        landscapeBookmark?.visibility = if (landscape) View.VISIBLE else View.GONE
        landscapeTabs?.visibility = if (landscape) View.VISIBLE else View.GONE
        landscapeShare?.visibility = if (landscape) View.GONE else View.VISIBLE
        if (landscape) {
            // The landscape reference uses the tab strip and a compact action row rather than the
            // portrait profile/protection controls.
            shieldButton?.visibility = View.GONE
            profileButton?.visibility = View.GONE
        } else {
            profileButton?.visibility = View.VISIBLE
            // updateShield() immediately refines this to VISIBLE or INVISIBLE for the current page.
            if (shieldButton?.visibility == View.GONE) shieldButton?.visibility = View.INVISIBLE
        }
    }

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
        applyLayout()
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
