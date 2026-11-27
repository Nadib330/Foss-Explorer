/*
 * Foss Explorer: the panel behind the shield of the top bar, which is called xGuard
 * since 15.2.1.
 * Own code, GPL-3.0. Arrangement and texts follow the protection panel of
 * Firefox for Android (MPL-2.0, see third_party/firefox).
 */
package com.rpooi3.explorer.view

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import com.rpooi3.explorer.privacy.TrackingAnalysis
import com.rpooi3.explorer.privacy.TrackingProtection

object ProtectionPanel {
    /** The drawable of the shield for the page in [tab]: plain while the site is protected, crossed out while it is not. */
    @JvmStatic
    fun shieldIcon(tab: NinjaWebView): Int =
        if (TrackingProtection.isProtected(tab.context, tab.url)) R.drawable.icon_shield else R.drawable.icon_shield_off

    /**
     * Opens the panel for the page in [tab]. [onChanged] runs when the
     * protection was switched for the site (the page is reloaded by then).
     */
    @JvmStatic
    fun show(activity: Activity, tab: NinjaWebView, onChanged: Runnable): BottomSheetDialog {
        val sheet = BottomSheetDialog(activity)
        val view = View.inflate(activity, R.layout.sheet_protection, null)
        bind(activity, sheet, view, tab, onChanged)
        sheet.setContentView(view)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.show()
        return sheet
    }

    private fun bind(activity: Activity, sheet: BottomSheetDialog, view: View, tab: NinjaWebView, onChanged: Runnable) {
        val url = tab.url
        val host = url.orEmpty().toUri().host.orEmpty().removePrefix("www.")
        val secure = TrackingProtection.isSecure(url)
        val protectedSite = TrackingProtection.isProtected(activity, url)
        val count = TrackingProtection.blockedCount(tab)
        // 15.2.1: the shield is called xGuard, and the banner says so.
        val app = activity.getString(R.string.xguard_name)

        // The site
        view.findViewById<TextView>(R.id.protection_site_title).text = tab.title?.takeIf { it.isNotBlank() } ?: host
        view.findViewById<TextView>(R.id.protection_site_host).text = host
        tab.favicon?.let { view.findViewById<ImageView>(R.id.protection_site_icon).setImageBitmap(it) }

        // The banner: an insecure connection weighs most, then protections that are off.
        val bannerIcon = view.findViewById<ImageView>(R.id.protection_banner_icon)
        val bannerTitle = view.findViewById<TextView>(R.id.protection_banner_title)
        val bannerText = view.findViewById<TextView>(R.id.protection_banner_text)
        when {
            !secure -> {
                bannerIcon.setImageResource(R.drawable.icon_alert)
                bannerIcon.imageTintList = MaterialColors.getColorStateListOrNull(activity, androidx.appcompat.R.attr.colorError)
                bannerTitle.setText(R.string.protection_panel_banner_not_secure_title)
                bannerText.setText(R.string.protection_panel_banner_not_secure_description)
            }
            !protectedSite -> {
                bannerIcon.setImageResource(R.drawable.icon_shield_off)
                bannerIcon.imageTintList = MaterialColors.getColorStateListOrNull(activity, com.google.android.material.R.attr.colorOnSurfaceVariant)
                bannerTitle.setText(R.string.protection_panel_banner_not_protected_title)
                bannerText.text = activity.getString(R.string.protection_panel_banner_not_protected_description, app)
            }
            else -> {
                bannerTitle.text = activity.getString(R.string.protection_panel_banner_protected_title, app)
                bannerText.text = if (count > 0) {
                    activity.resources.getQuantityString(R.plurals.protection_panel_banner_protected_blocked_trackers_description, count, count)
                } else {
                    activity.getString(R.string.protection_panel_banner_protected_no_blocked_trackers_description)
                }
            }
        }

        // The switch
        val toggle = view.findViewById<MaterialSwitch>(R.id.protection_toggle)
        toggle.isChecked = protectedSite
        view.findViewById<TextView>(R.id.protection_toggle_text).setText(
            if (protectedSite) R.string.protection_panel_etp_toggle_enabled_description_2
            else R.string.protection_panel_etp_toggle_disabled_description_2,
        )
        view.findViewById<View>(R.id.protection_toggle_row).setOnClickListener {
            TrackingProtection.setProtected(activity, url, !protectedSite)
            sheet.dismiss()
            // The page is loaded again under the new rule, as in Firefox.
            tab.initPreferences(url)
            tab.reload()
            onChanged.run()
        }

        val inflater = LayoutInflater.from(activity)
        val gap = activity.resources.getDimensionPixelSize(R.dimen.menu_item_gap)
        fun row(group: LinearLayout, id: Int, icon: Int, label: CharSequence, onClick: (() -> Unit)?): View {
            val row = inflater.inflate(R.layout.item_main_menu_row, group, false)
            row.id = id
            row.findViewById<ImageView>(R.id.menu_row_icon).setImageResource(icon)
            row.findViewById<TextView>(R.id.menu_row_label).text = label
            (row.layoutParams as LinearLayout.LayoutParams).topMargin = gap
            if (onClick != null) row.setOnClickListener { onClick() } else row.isClickable = false
            group.addView(row)
            return row
        }

        // What was blocked: the number, and on a tap the hosts.
        val group = view.findViewById<LinearLayout>(R.id.protection_group)
        // The groups cut their rows to their rounded outline (set here: the attribute exists from Android 12 on only).
        group.clipToOutline = true
        val hosts = TrackingProtection.blockedHosts(tab)
        val blockedLabel = when {
            !protectedSite -> activity.getString(R.string.protection_panel_etp_disabled_no_trackers_blocked)
            else -> activity.resources.getQuantityString(R.plurals.trackers_blocked_panel_total_num_trackers_blocked_2, count, count)
        }
        val list = TextView(activity).apply {
            id = R.id.protection_blocked_list
            text = hosts.joinToString("\n") { (name, requests) -> if (requests > 1) "$name  \u00d7$requests" else name }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurfaceVariant, 0))
            setBackgroundResource(R.drawable.bg_menu_row)
            val density = resources.displayMetrics.density
            setPadding((56 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            isVisible = false
        }
        row(group, R.id.protection_blocked, R.drawable.icon_adblock, blockedLabel, if (hosts.isEmpty()) null else ({ list.isVisible = !list.isVisible }))
        group.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        row(
            group, R.id.protection_connection, if (secure) R.drawable.icon_lock else R.drawable.icon_unsecure,
            activity.getString(if (secure) R.string.connection_security_panel_secure else R.string.connection_security_panel_not_secure), null,
        )
        // xGuard's tracking analysis of this page: every request, every tracker, what was stopped.
        row(group, R.id.protection_analysis, R.drawable.icon_search_site, activity.getString(R.string.xguard_panel_tracking_analysis)) {
            TrackingAnalysis.showReport(activity, tab)
        }

        // What can be done
        val actions = view.findViewById<LinearLayout>(R.id.protection_actions)
        actions.clipToOutline = true
        row(actions, R.id.protection_clear, R.drawable.icon_delete, activity.getString(R.string.clear_site_data)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.clear_site_data)
                .setMessage(activity.getString(R.string.clear_site_data_dialog_description, host))
                .setNegativeButton(R.string.clear_site_data_dialog_negative_button_text, null)
                .setPositiveButton(R.string.clear_site_data_dialog_positive_button_text) { _, _ ->
                    TrackingProtection.clearSiteData(activity, url)
                    sheet.dismiss()
                    tab.reload()
                }
                .show()
        }.let { (it.layoutParams as LinearLayout.LayoutParams).topMargin = 0 }
        row(actions, R.id.protection_settings, R.drawable.icon_settings, activity.getString(R.string.protection_panel_privacy_and_security_settings_2)) {
            sheet.dismiss()
            activity.startActivity(
                Intent(activity, Settings_Activity::class.java).putExtra(Settings_Activity.EXTRA_SECTION, "section_xguard"),
            )
        }
    }
}
