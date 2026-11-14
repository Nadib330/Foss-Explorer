/*
 * Foss Explorer: what the settings screens of 13.2.2 do.
 * Own code, GPL-3.0. The screens themselves are the preference_section_*.xml
 * files, laid out after Chromium's settings.
 */
package com.rpooi3.explorer.preferences

import androidx.core.content.edit
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.rpooi3.explorer.site.SiteCategoryFragment
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import com.rpooi3.explorer.browser.AdBlock
import com.rpooi3.explorer.accessibility.PageZoom
import com.rpooi3.explorer.privacy.IncognitoLock
import com.rpooi3.explorer.privacy.MarkedAds
import com.rpooi3.explorer.privacy.PrivacySettings
import com.rpooi3.explorer.privacy.TrackerList
import com.rpooi3.explorer.privacy.TrackingAnalysis
import com.rpooi3.explorer.search.SearchEngines
import com.rpooi3.explorer.site.SiteCategory
import com.rpooi3.explorer.site.SiteSettings
import com.rpooi3.explorer.tabs.InactiveTabs
import com.rpooi3.explorer.view.CatToast

/**
 * Gives the rows of the settings screens their state and their behaviour.
 * [bind] is called when a screen is created and again whenever it comes back
 * to the front; every part looks for its rows by key and does nothing on
 * screens that do not have them.
 */
object SettingsScreens {

    const val COPYRIGHT_YEAR = 2026

    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        if (fragment.preferenceScreen == null) return
        bindPrivacy(fragment)
        bindSafeBrowsing(fragment)
        bindPreload(fragment)
        bindJsOptimizer(fragment)
        bindTabs(fragment)
        bindAccessibility(fragment)
        bindSavedZoom(fragment)
        bindXGuard(fragment)
        bindMarkedAds(fragment)
        bindSearchEngines(fragment)
        bindAbout(fragment)
    }

    private fun radios(fragment: PreferenceFragmentCompat, vararg keys: Pair<String, String>): List<RadioRowPreference> =
        keys.mapNotNull { (key, value) ->
            fragment.findPreference<RadioRowPreference>(key)?.also { it.value = value }
        }

    // Privacy and security -------------------------------------------------------------------

    private fun bindPrivacy(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fragment.findPreference<Preference>("section_preload")?.setSummary(
            if (PrivacySettings.preload(prefs) == PrivacySettings.PRELOAD_STANDARD) R.string.cr_preload_standard_title
            else R.string.cr_preload_none_title,
        )
        fragment.findPreference<Preference>("section_safe_browsing")?.summary =
            if (PrivacySettings.isSafeBrowsingOn(prefs)) {
                context.getString(R.string.cr_safe_browsing_summary_on, context.getString(R.string.cr_safe_browsing_standard_title))
            } else {
                context.getString(R.string.cr_safe_browsing_summary_off)
            }
        fragment.findPreference<SwitchPreferenceCompat>(IncognitoLock.KEY)?.let { lock ->
            val canLock = IncognitoLock.canLock(context)
            // Without a screen lock the switch shows off, whatever is stored, and leads to Android's settings.
            if (!canLock) lock.isChecked = false
            lock.setSummary(if (canLock) R.string.cr_incognito_lock_summary_on else R.string.cr_incognito_lock_summary_off)
            lock.setOnPreferenceChangeListener { _, _ ->
                if (IncognitoLock.canLock(context)) return@setOnPreferenceChangeListener true
                try {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    CatToast.show(context, R.string.cr_incognito_lock_summary_off)
                }
                false
            }
        }
    }

    private fun bindSafeBrowsing(fragment: PreferenceFragmentCompat) {
        val rows = radios(
            fragment,
            "safe_browsing_standard" to PrivacySettings.SAFE_BROWSING_STANDARD,
            "safe_browsing_off" to PrivacySettings.SAFE_BROWSING_OFF,
        )
        if (rows.isEmpty()) return
        val context = fragment.requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fun store(value: String) = prefs.edit { putString(PrivacySettings.KEY_SAFE_BROWSING, value) }
        RadioRowPreference.group(rows, PrivacySettings.safeBrowsing(prefs)) { value ->
            if (value == PrivacySettings.SAFE_BROWSING_OFF) {
                // Chromium asks before the protection is switched off.
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.cr_safe_browsing_off_dialog_title)
                    .setMessage(R.string.cr_safe_browsing_off_dialog_message)
                    .setNegativeButton(R.string.app_cancel, null)
                    .setPositiveButton(R.string.cr_safe_browsing_off_dialog_confirm) { _, _ ->
                        store(value)
                        rows.forEach { it.isChecked = it.value == value }
                    }
                    .show()
                false
            } else {
                store(value)
                true
            }
        }
    }

    private fun bindPreload(fragment: PreferenceFragmentCompat) {
        val rows = radios(
            fragment,
            "preload_none" to PrivacySettings.PRELOAD_NONE,
            "preload_standard" to PrivacySettings.PRELOAD_STANDARD,
        )
        if (rows.isEmpty()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(fragment.requireContext())
        val supported = PrivacySettings.isPreloadSupported()
        // An Android System WebView that cannot preload leaves one choice.
        rows.firstOrNull { it.value == PrivacySettings.PRELOAD_STANDARD }?.isEnabled = supported
        fragment.findPreference<Preference>("preload_note")?.apply {
            isVisible = !supported
            setSummary(R.string.preload_unsupported)
        }
        RadioRowPreference.group(rows, if (supported) PrivacySettings.preload(prefs) else PrivacySettings.PRELOAD_NONE) { value ->
            prefs.edit { putString(PrivacySettings.KEY_PRELOAD, value) }
            true
        }
    }

    /**
     * Chromium's screen, with the choice that Explorer can honour. Android
     * System WebView gives apps no way to switch the V8 optimizer off, so the
     * second choice and the exceptions cannot be used; the note says so.
     */
    private fun bindJsOptimizer(fragment: PreferenceFragmentCompat) {
        val rows = radios(fragment, "js_optimizer_allowed" to "allowed", "js_optimizer_blocked" to "blocked")
        if (rows.isEmpty()) return
        val context = fragment.requireContext()
        rows.firstOrNull { it.value == "allowed" }?.icon = AppCompatResources.getDrawable(context, R.drawable.cr_settings_v8)
        rows.firstOrNull { it.value == "blocked" }?.apply {
            icon = SiteCategoryFragment.crossedOut(context, R.drawable.cr_settings_v8)
            iconHasColors = true
            isEnabled = false
        }
        fragment.findPreference<Preference>("js_optimizer_add")?.isEnabled = false
        RadioRowPreference.group(rows, "allowed") { it == "allowed" }
    }

    // Tabs and groups ------------------------------------------------------------------------

    private fun inactiveSummary(context: Context, days: Int): String = context.getString(
        when (days) {
            0 -> R.string.cr_archive_never
            7 -> R.string.cr_archive_7
            14 -> R.string.cr_archive_14
            else -> R.string.cr_archive_21
        },
    )

    private fun bindTabs(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fragment.findPreference<Preference>("section_inactive_tabs")?.summary =
            inactiveSummary(context, InactiveTabs.days(prefs))
        val rows = radios(fragment, "inactive_never" to "0", "inactive_7" to "7", "inactive_14" to "14", "inactive_21" to "21")
        if (rows.isEmpty()) return
        val days = InactiveTabs.days(prefs).let { if (it in intArrayOf(0, 7, 14, 21)) it else InactiveTabs.DEFAULT_DAYS }
        RadioRowPreference.group(rows, days.toString()) { value ->
            InactiveTabs.setDays(prefs, value.toInt())
            true
        }
        fragment.findPreference<Preference>(InactiveTabs.KEY_AUTO_CLOSE)?.summary =
            context.resources.getQuantityString(
                R.plurals.inactive_auto_close_summary, InactiveTabs.AUTO_CLOSE_DAYS.toInt(), InactiveTabs.AUTO_CLOSE_DAYS.toInt(),
            )
    }

    // Accessibility --------------------------------------------------------------------------

    private fun bindAccessibility(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fragment.findPreference<PageZoomPreference>("page_zoom_default_zoom")?.let { zoom ->
            zoom.zoom = PageZoom.defaultZoom(prefs)
            zoom.onZoomChanged = { PageZoom.setDefaultZoom(prefs, it) }
        }
        fragment.findPreference<Preference>("captions")?.setOnPreferenceClickListener {
            // As in Chrome: captions are a setting of Android.
            try {
                context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                CatToast.show(context, R.string.accessibility_captions_missing)
            }
            true
        }
        // Chromium shows "Show hyperlinks" as part of the simplified view.
        val reader = fragment.findPreference<SwitchPreferenceCompat>("reader_for_accessibility")
        val links = fragment.findPreference<Preference>("reader_enable_links")
        links?.isEnabled = reader?.isChecked == true
        reader?.setOnPreferenceChangeListener { _, value ->
            links?.isEnabled = value == true
            true
        }
        // 16.1.0: "Navigate pages with a text cursor when a keyboard is attached" and
        // "Get image descriptions from Google" are no longer in the list (item 15).
    }

    private fun bindSavedZoom(fragment: PreferenceFragmentCompat) {
        val screen = fragment.preferenceScreen
        if (fragment.arguments?.getString("section") != "section_saved_zoom") return
        val context = fragment.requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        screen.removeAll()
        val zooms = PageZoom.siteZooms(prefs)
        if (zooms.isEmpty()) {
            screen.addPreference(
                TextMessagePreference(context).apply {
                    key = "saved_zoom_empty"
                    setSummary(R.string.cr_zoom_info_empty)
                },
            )
            SettingsCards.style(screen)
            return
        }
        val list = PreferenceCategory(context).apply { key = "saved_zoom_list" }
        screen.addPreference(list)
        for ((site, zoom) in zooms) {
            list.addPreference(
                Preference(context).apply {
                    key = "saved_zoom:$site"
                    isPersistent = false
                    setIcon(R.drawable.icon_web)
                    title = site
                    summary = context.getString(R.string.cr_page_zoom_level, zoom)
                    widgetLayoutResource = R.layout.preference_delete_widget
                    setOnPreferenceClickListener {
                        // Chromium removes the entry with the button at its end.
                        PageZoom.setSiteZoom(prefs, site, null)
                        bindSavedZoom(fragment)
                        true
                    }
                },
            )
        }
        val actions = PreferenceCategory(context).apply { key = "saved_zoom_actions" }
        screen.addPreference(actions)
        actions.addPreference(
            Preference(context).apply {
                key = "saved_zoom_clear"
                isPersistent = false
                setIcon(R.drawable.icon_delete)
                setTitle(R.string.cr_zoom_info_clear_all)
                setOnPreferenceClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.cr_zoom_info_clear_all)
                        .setMessage(R.string.cr_zoom_info_clear_all_warning)
                        .setNegativeButton(R.string.app_cancel, null)
                        .setPositiveButton(R.string.cr_zoom_info_clear_all) { _, _ ->
                            PageZoom.clearSiteZooms(prefs)
                            bindSavedZoom(fragment)
                        }
                        .show()
                    true
                }
            },
        )
        SettingsCards.style(screen)
    }

    // Search engine --------------------------------------------------------------------------

    private fun bindSearchEngines(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val rows = ArrayList<RadioRowPreference>()
        for (engine in SearchEngines.ALL) {
            val row = fragment.findPreference<RadioRowPreference>("engine_" + engine.key) ?: continue
            row.value = engine.key
            row.radioAtEnd = true
            row.iconIsLogo = true
            row.icon = SearchEngines.icon(context, engine)
            row.summary = engine.domain
            rows += row
        }
        val custom = fragment.findPreference<RadioRowPreference>("engine_custom") ?: return
        custom.value = SearchEngines.CUSTOM
        custom.radioAtEnd = true
        custom.iconIsLogo = true
        custom.icon = SearchEngines.Logo(0xFF5F6368.toInt(), "+")
        fun customSummary() {
            val url = prefs.getString(SearchEngines.KEY_CUSTOM_URL, "").orEmpty()
            custom.summary = url.ifBlank { context.getString(R.string.engine_custom_domain) }
        }
        customSummary()
        rows += custom
        RadioRowPreference.group(rows, SearchEngines.selected(prefs)) { key ->
            if (key != SearchEngines.CUSTOM) {
                SearchEngines.select(prefs, key)
                return@group true
            }
            askForSearchAddress(context, prefs.getString(SearchEngines.KEY_CUSTOM_URL, "").orEmpty()) { url ->
                if (SearchEngines.selectCustom(prefs, url)) {
                    customSummary()
                    rows.forEach { it.isChecked = it === custom }
                } else {
                    CatToast.show(context, R.string.engine_custom_invalid)
                }
            }
            false
        }
        // The address can be changed with a tap on the row that is chosen already.
        val choose = custom.onPreferenceClickListener
        custom.setOnPreferenceClickListener { pref ->
            if (custom.isChecked) {
                askForSearchAddress(context, prefs.getString(SearchEngines.KEY_CUSTOM_URL, "").orEmpty()) { url ->
                    if (SearchEngines.selectCustom(prefs, url)) customSummary()
                    else CatToast.show(context, R.string.engine_custom_invalid)
                }
                true
            } else {
                choose?.onPreferenceClick(pref) ?: false
            }
        }
    }

    private fun askForSearchAddress(context: Context, current: String, onDone: (String) -> Unit) {
        val view = View.inflate(context, R.layout.dialog_edit_text, null)
        val input = view.findViewById<TextInputEditText>(R.id.textInput)
        input.setHint(R.string.engine_custom_hint)
        input.setText(current)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.engine_custom)
            .setMessage(R.string.engine_custom_message)
            .setView(view)
            .setNegativeButton(R.string.app_cancel, null)
            .setPositiveButton(R.string.app_ok) { _, _ -> onDone(input.text?.toString().orEmpty()) }
            .show()
    }

    // xGuard ---------------------------------------------------------------------------------

    /** The filter list whose entry is the list the ad blocker works with. */
    private const val FILTER_LIST = "ab_hosts"

    /** The list Explorer has always fetched first, kept as the default (StevenBlack's unified hosts). */
    private const val DEFAULT_FILTER_LIST = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

    /**
     * Settings > xGuard (15.2.1): the ad blocker's switch and filter list, which moved here
     * from the profile and from Site settings > Ads, the tracker blocker by kind, and the
     * tracking analysis of pages.
     */
    private fun bindXGuard(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        // Only the xGuard screen has these rows; every other screen is left alone.
        if (fragment.findPreference<Preference>("xguard_trackers_known") == null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        // The ad blocker: one switch for the profile in use, stored as the engine reads it.
        fragment.findPreference<SwitchPreferenceCompat>("xguard_block_ads")?.let { row ->
            fun refresh() {
                row.isChecked = SiteSettings.isOn(prefs, SiteCategory.ADS)
                row.setSummary(
                    if (row.isChecked) R.string.xguard_block_ads_summary_on
                    else R.string.xguard_block_ads_summary_off,
                )
            }
            refresh()
            row.setOnPreferenceChangeListener { _, value ->
                val on = value == true
                SiteSettings.setOn(prefs, SiteCategory.ADS, on)
                // Turning blocking on fetches the filter list, as the profile screen did.
                if (on) AdBlock(context).triggerHostsDownload(context)
                fragment.view?.post { refresh() }
                true
            }
        }

        // What xGuard knows, and what of it is in force.
        fun kindCounts(): String = TrackerList.entries(context).values
            .groupingBy { it }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key }))
            .joinToString(", ") { (kind, count) -> TrackingAnalysis.kindName(context, kind) + " " + count }

        fragment.findPreference<Preference>("xguard_trackers_known")?.summary =
            context.getString(R.string.xguard_trackers_known_summary, TrackerList.count(context), kindCounts())

        val trackerSwitch = fragment.findPreference<SwitchPreferenceCompat>("xguard_tracking_blocker")
        trackerSwitch?.let { row ->
            fun refresh() {
                row.isChecked = TrackerList.isOn(prefs)
                row.summary = if (row.isChecked) {
                    val kinds = TrackerList.blockedKinds(prefs)
                        .joinToString(", ") { TrackingAnalysis.kindName(context, it) }
                    context.getString(R.string.xguard_tracking_blocker_summary_on, TrackerList.count(context), kinds)
                } else {
                    context.getString(R.string.xguard_tracking_blocker_summary_off)
                }
            }
            refresh()
            row.setOnPreferenceChangeListener { _, value ->
                prefs.edit { putBoolean(TrackerList.KEY_TRACKING_BLOCKER, value == true) }
                fragment.view?.post { refresh() }
                true
            }
        }

        // The kinds with a switch of their own.
        listOf(
            "xguard_block_analytics" to TrackerList.KEY_BLOCK_ANALYTICS,
            "xguard_block_social" to TrackerList.KEY_BLOCK_SOCIAL,
        ).forEach { (key, stored) ->
            fragment.findPreference<SwitchPreferenceCompat>(key)?.setOnPreferenceChangeListener { row, value ->
                prefs.edit { putBoolean(stored, value == true) }
                fragment.view?.post { bindXGuard(fragment) }
                true
            }
        }

        // The filter list, which sat in Site settings > Ads before 15.2.1.
        fragment.findPreference<ListPreference>(FILTER_LIST)?.let { list ->
            list.setEntries(R.array.setting_entries_adBlock)
            list.setEntryValues(R.array.setting_values_adBlock)
            if (list.value.isNullOrEmpty()) list.value = DEFAULT_FILTER_LIST
            list.summaryProvider = Preference.SummaryProvider<ListPreference> {
                val date = AdBlock.getHostsDate(context).orEmpty().trim()
                val name = it.entry?.toString().orEmpty()
                if (date.isEmpty()) name else "$date:\n$name"
            }
            list.setOnPreferenceChangeListener { _, _ ->
                // The new list is fetched once the new value is stored.
                fragment.view?.post { AdBlock(context).triggerHostsDownload(context) }
                true
            }
        }

        fragment.findPreference<Preference>("xguard_filter_update")?.let { row ->
            val date = AdBlock.getHostsDate(context).orEmpty().trim()
            row.summary = if (date.isEmpty()) context.getString(R.string.xguard_filter_update_summary_off)
            else context.getString(R.string.xguard_filter_update_summary_on, date)
            row.setOnPreferenceClickListener {
                AdBlock(context).triggerHostsDownload(context)
                CatToast.show(context, R.string.xguard_filter_update_toast, 0)
                true
            }
        }

        // Which sites may load ads and trackers: the exceptions Chromium lists for "Ads".
        fragment.findPreference<Preference>("xguard_site_exceptions")?.setOnPreferenceClickListener {
            context.startActivity(
                Intent(context, Settings_Activity::class.java)
                    .putExtra(Settings_Activity.EXTRA_SITE_CATEGORY, SiteCategory.ADS.key),
            )
            true
        }

        // How many ads the user marked, and the screen that manages them.
        fragment.findPreference<Preference>("section_marked_ads")?.summary =
            if (MarkedAds.count(context) == 0) context.getString(R.string.marked_ads_none)
            else context.getString(R.string.marked_ads_summary, MarkedAds.count(context))

        // The tracking analysis of the last page, and forgetting it.
        fragment.findPreference<Preference>("xguard_analysis_row")?.let { row ->
            row.summary = TrackingAnalysis.lastReportSummary(context)
            row.setOnPreferenceClickListener {
                val activity = fragment.activity
                if (activity != null) TrackingAnalysis.showLastReport(activity)
                true
            }
        }
        fragment.findPreference<Preference>("xguard_analysis_clear")?.setOnPreferenceClickListener {
            TrackingAnalysis.clear(context)
            CatToast.show(context, R.string.xguard_analysis_cleared_toast, 0)
            fragment.findPreference<Preference>("xguard_analysis_row")?.summary =
                TrackingAnalysis.lastReportSummary(context)
            bindXGuard(fragment)
            true
        }
    }

    // Marked ads -----------------------------------------------------------------------------

    /**
     * Settings > xGuard > Marked ads (16.1.0): one row per ad marked on a page, which opens the
     * question to unmark it, and the row that adds an address by hand.
     */
    private fun bindMarkedAds(fragment: PreferenceFragmentCompat) {
        if (fragment.arguments?.getString("section") != "section_marked_ads") return
        val context = fragment.requireContext()
        val screen = fragment.preferenceScreen ?: return
        val group = screen.findPreference<PreferenceCategory>("marked_ads_group") ?: return
        // The rows of the marks are built again on every bind.
        for (i in group.preferenceCount - 1 downTo 0) {
            val row = group.getPreference(i)
            if (row.key?.startsWith("marked_ad:") == true || row.key == "marked_ads_none" || row.key == "marked_ads_note") {
                group.removePreference(row)
            }
        }
        val marks = MarkedAds.list(context)
        if (marks.isEmpty()) {
            group.addPreference(
                Preference(context).apply {
                    key = "marked_ads_none"
                    isPersistent = false
                    isSelectable = false
                    setIcon(R.drawable.icon_info)
                    summary = context.getString(R.string.marked_ads_none)
                },
            )
        }
        marks.forEach { mark ->
            group.addPreference(
                Preference(context).apply {
                    key = "marked_ad:$mark"
                    isPersistent = false
                    setIcon(R.drawable.icon_adblock)
                    title = mark
                    setOnPreferenceClickListener {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.marked_ads_dialog_title)
                            .setMessage(context.getString(R.string.marked_ads_dialog_message, mark))
                            .setNegativeButton(R.string.app_cancel, null)
                            .setPositiveButton(R.string.marked_ads_unmark) { _, _ ->
                                MarkedAds.remove(context, mark)
                                CatToast.show(context, context.getString(R.string.marked_ads_removed, mark), 0)
                                bindMarkedAds(fragment)
                            }
                            .show()
                        true
                    }
                },
            )
        }
        group.addPreference(
            Preference(context).apply {
                key = "marked_ads_note"
                isPersistent = false
                isSelectable = false
                setIcon(R.drawable.icon_shield)
                summary = context.getString(R.string.marked_ads_empty_note)
            },
        )
        // "Mark a link as an ad": a dialog with a field, as Chromium asks for an exception.
        fragment.findPreference<Preference>("marked_ads_add")?.setOnPreferenceClickListener {
            val view = View.inflate(context, R.layout.dialog_edit_text, null)
            val input = view.findViewById<TextInputEditText>(R.id.textInput)
            input.setHint(R.string.cr_site_add_url)
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.marked_ads_add_title)
                .setMessage(R.string.marked_ads_add_message)
                .setView(view)
                .setNegativeButton(R.string.app_cancel, null)
                .setPositiveButton(R.string.cr_site_add_button) { _, _ ->
                    val address = input.text?.toString()?.trim()
                    val added = MarkedAds.add(context, address)
                    when {
                        added -> {
                            CatToast.show(context, context.getString(R.string.marked_ads_added, address.orEmpty()), 0)
                            bindMarkedAds(fragment)
                        }
                        MarkedAds.isMarked(context, address) ->
                            CatToast.show(context, context.getString(R.string.marked_ads_already, address.orEmpty()), 0)
                        else -> CatToast.show(context, R.string.marked_ads_invalid, 0)
                    }
                }
                .show()
            true
        }
    }

    // About ----------------------------------------------------------------------------------

    /** "Android 16; Infinix X6850 Build/BP2A.250605.031.A3", as "About Chrome" writes it. */
    @JvmStatic
    fun operatingSystem(): String = "Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID}"

    private fun bindAbout(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        fragment.findPreference<Preference>("about_app")?.summary =
            context.getString(R.string.about_application_version, SettingsSections.versionName(context))
        fragment.findPreference<Preference>("about_os")?.summary = operatingSystem()
        fragment.findPreference<Preference>("section_legal")?.summary =
            context.getString(R.string.about_legal_summary, COPYRIGHT_YEAR.toString())
        mapOf(
            "about_license_gpl" to (R.string.about_license_gpl to "licenses/GPL-3.0.txt"),
            "about_license_agpl" to (R.string.about_license_agpl to "licenses/AGPL-3.0.txt"),
            "about_third_party" to (R.string.about_components_title to "licenses/components.txt"),
        ).forEach { (key, what) ->
            fragment.findPreference<Preference>(key)?.setOnPreferenceClickListener {
                showText(context, what.first, what.second)
                true
            }
        }
    }

    /** A text the app carries with it (a licence); null if it cannot be read. */
    @JvmStatic
    fun readAsset(context: Context, asset: String): String? = try {
        context.assets.open(asset).bufferedReader().use { it.readText() }
    } catch (e: java.io.IOException) {
        null
    }

    private fun showText(context: Context, title: Int, asset: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(readAsset(context, asset) ?: context.getString(R.string.about_third_party_unreadable))
            .setPositiveButton(R.string.app_ok, null)
            .show()
    }
}
