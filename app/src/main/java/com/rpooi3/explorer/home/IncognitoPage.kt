/*
 * Foss Explorer: the new tab page of a private tab.
 * Own code, GPL-3.0. The page says what Chromium's Incognito page says, in
 * Chromium's words (BSD, see third_party/chromium-settings), as far as it is
 * true of Explorer on this device.
 */
package com.rpooi3.explorer.home

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.materialswitch.MaterialSwitch
import com.rpooi3.explorer.R
import com.rpooi3.explorer.site.SiteCategory
import com.rpooi3.explorer.site.SiteSettings

/**
 * Drives `include_incognito_ntp.xml`: shown in place of the home page while a
 * private tab is on the home address.
 */
class IncognitoPage(private val root: View) {
    private val features: TextView = root.findViewById(R.id.new_tab_incognito_features)
    private val warning: TextView = root.findViewById(R.id.new_tab_incognito_warning)
    private val shared: TextView = root.findViewById(R.id.new_tab_incognito_shared)
    private val cookies: MaterialSwitch = root.findViewById(R.id.cookie_controls_card_toggle)

    val isVisible: Boolean get() = root.isVisible

    init {
        // The card is the site setting "Third-party cookies", the other way round.
        cookies.setOnCheckedChangeListener { _, blocked ->
            val prefs = SiteSettings.prefs(root.context)
            if (SiteSettings.isOn(prefs, SiteCategory.THIRD_PARTY_COOKIES) == blocked) {
                SiteSettings.setOn(prefs, SiteCategory.THIRD_PARTY_COOKIES, !blocked)
            }
        }
    }

    /**
     * Shows or hides the page.
     *
     * @param isolated whether the cookies and site data of private tabs are
     *   kept apart on this device; only then the page promises not to save them
     */
    fun setVisible(visible: Boolean, isolated: Boolean) {
        root.isVisible = visible
        if (!visible) return
        root.scrollTo(0, 0)
        features.text = list(
            R.string.incognito_ntp_not_saved, R.string.incognito_ntp_not_saved_emphasis,
            listOfNotNull(
                R.string.incognito_ntp_not_saved_history,
                R.string.incognito_ntp_not_saved_cookies.takeIf { isolated },
                R.string.incognito_ntp_not_saved_forms,
            ),
        )
        warning.text = list(
            R.string.incognito_ntp_visible, R.string.incognito_ntp_visible_emphasis,
            listOf(R.string.incognito_ntp_visible_sites, R.string.incognito_ntp_visible_employer, R.string.incognito_ntp_visible_isp),
        )
        shared.isVisible = !isolated
        cookies.isChecked = !SiteSettings.isOn(SiteSettings.prefs(root.context), SiteCategory.THIRD_PARTY_COOKIES)
    }

    /** A heading with its emphasised words in bold, and the items below it as a bulleted list. */
    private fun list(@StringRes heading: Int, @StringRes emphasis: Int, items: List<Int>): CharSequence {
        val context = root.context
        val text = SpannableStringBuilder(context.getString(heading))
        val bold = context.getString(emphasis)
        val at = text.indexOf(bold)
        if (at >= 0) text.setSpan(StyleSpan(android.graphics.Typeface.BOLD), at, at + bold.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val gap = (8 * context.resources.displayMetrics.density).toInt()
        for (item in items) {
            text.append('\n')
            val start = text.length
            text.append(context.getString(item))
            text.setSpan(BulletSpan(gap), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }
}
