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
import androidx.preference.Preference
import com.rpooi3.explorer.site.SiteCategoryFragment
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.rpooi3.explorer.R
import com.rpooi3.explorer.accessibility.PageZoom
import com.rpooi3.explorer.privacy.IncognitoLock
import com.rpooi3.explorer.privacy.PrivacySettings
import com.rpooi3.explorer.search.SearchEngines
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
        // Not offered to apps by Android System WebView: shown as Chromium has them, switched off.
        fragment.findPreference<SwitchPreferenceCompat>("caret_browsing")?.apply {
            isChecked = false
            isEnabled = false
        }
        fragment.findPreference<Preference>("image_descriptions")?.isEnabled = false
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
