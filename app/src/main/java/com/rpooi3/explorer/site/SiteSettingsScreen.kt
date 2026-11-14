package com.rpooi3.explorer.site

import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.google.android.material.color.MaterialColors
import android.graphics.drawable.LayerDrawable
import androidx.appcompat.content.res.AppCompatResources
import com.rpooi3.explorer.preferences.ExpandablePreference
import com.rpooi3.explorer.preferences.RadioRowPreference
import com.rpooi3.explorer.preferences.TextMessagePreference
import com.rpooi3.explorer.view.CatToast
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.Settings_Activity
import com.rpooi3.explorer.browser.AdBlock
import com.rpooi3.explorer.preferences.BasePreferenceFragment

/** Settings > Site settings: the list of categories (`preference_section_site.xml`). */
object SiteSettingsScreen {
    /** Gives every category row its state ("Ask first", "Blocked" …) and opens its screen on a tap. */
    @JvmStatic
    fun bind(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        for (category in SiteCategory.entries) {
            val row = fragment.findPreference<Preference>(category.rowKey) ?: continue
            row.summary = SiteSettings.summary(context, category)
            row.setOnPreferenceClickListener {
                fragment.startActivity(
                    Intent(context, Settings_Activity::class.java)
                        .putExtra(Settings_Activity.EXTRA_SITE_CATEGORY, category.key),
                )
                true
            }
        }
    }
}

/**
 * The screen of one category, laid out like Chromium's
 * (SingleCategorySettings): what the category is about, the two choices as
 * radio buttons with the icon for "on" and the crossed-out icon for "off",
 * the lists "Not Allowed" and "Allowed" that open and close, and "Add site
 * exception".
 */
class SiteCategoryFragment : BasePreferenceFragment() {
    private lateinit var category: SiteCategory
    private var blockedOpen = true
    private var allowedOpen = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        category = SiteCategory.byKey(requireArguments().getString(ARG_CATEGORY)) ?: SiteCategory.LOCATION
        build()
    }

    /** The choice that is in force: [KEY_ON], [KEY_ASK] or [KEY_OFF]. */
    internal fun choice(): String {
        val prefs = SiteSettings.prefs(requireContext())
        return when {
            !SiteSettings.isOn(prefs, category) -> KEY_OFF
            category.hasAskChoice && SiteSettings.asksFirst(prefs, category) -> KEY_ASK
            else -> KEY_ON
        }
    }

    /** Makes [choice] the general choice of the category and redraws the screen. */
    internal fun choose(choice: String) {
        val prefs = SiteSettings.prefs(requireContext())
        SiteSettings.setOn(prefs, category, choice != KEY_OFF)
        if (category.hasAskChoice && choice != KEY_OFF) SiteSettings.setAsksFirst(prefs, category, choice == KEY_ASK)
        // "Add site exception" depends on the choice.
        listView?.post { if (isAdded) build() } ?: build()
    }

    private fun build() {
        val context = requireContext()
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(
            TextMessagePreference(context).apply {
                key = KEY_DESCRIPTION
                setSummary(category.description)
            },
        )
        if (category.kind == SiteCategory.Kind.UNAVAILABLE) {
            buildUnavailable(screen)
            preferenceScreen = screen
            return
        }

        val rows = ArrayList<RadioRowPreference>()
        fun radio(rowKey: String, text: Int, description: Int, off: Boolean) {
            rows += RadioRowPreference(context).apply {
                key = rowKey
                value = rowKey
                setTitle(text)
                if (description != 0) setSummary(description)
                val offIcon = category.offIcon
                icon = when {
                    !off -> AppCompatResources.getDrawable(context, category.onIcon)
                    offIcon != null -> AppCompatResources.getDrawable(context, offIcon)
                    else -> crossedOut(context, category.onIcon).also { iconHasColors = true }
                }
            }
        }
        radio(KEY_ON, category.onText, 0, off = false)
        if (category.hasAskChoice) radio(KEY_ASK, R.string.cr_site_protected_ask, 0, off = false)
        radio(KEY_OFF, category.offText, category.offDescription, off = true)
        rows.forEach { screen.addPreference(it) }
        RadioRowPreference.group(rows, choice()) { chosen ->
            if (chosen == KEY_OFF && category == SiteCategory.SITE_DATA) {
                // Chromium asks before site data is switched off for all sites.
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.cr_site_data_block_title)
                    .setMessage(R.string.cr_site_data_block_message)
                    .setNegativeButton(R.string.app_cancel, null)
                    .setPositiveButton(R.string.cr_site_data_block_confirm) { _, _ -> choose(chosen) }
                    .show()
                false
            } else {
                choose(chosen)
                true
            }
        }

        if (category == SiteCategory.ADS) {
            PreferenceCategory(context).also { screen.addPreference(it) }.addPreference(filterList(context))
        }

        addSites(screen, allowed = false)
        addSites(screen, allowed = true)

        addExceptionAllows()?.let { allows ->
            PreferenceCategory(context).also { screen.addPreference(it) }.addPreference(
                Preference(context).apply {
                    key = KEY_ADD
                    isPersistent = false
                    setIcon(R.drawable.icon_plus)
                    setTitle(R.string.cr_site_add)
                    setOnPreferenceClickListener {
                        askForSite(allows)
                        true
                    }
                },
            )
        }
        preferenceScreen = screen
    }

    /**
     * Which exception "Add site exception" adds: the one that differs from
     * the general choice. Null where Chromium has no such row: sites that
     * have to ask get their entry by being answered.
     */
    internal fun addExceptionAllows(): Boolean? {
        val on = choice() != KEY_OFF
        return when {
            category == SiteCategory.AUTOMATIC_DOWNLOADS -> true
            category.kind == SiteCategory.Kind.ASK -> null
            category.hasAskChoice && choice() == KEY_ASK -> true
            else -> !on
        }
    }

    /** Auto-verify as Chromium shows it: the switch, "When on", "Things to consider". The switch cannot be used. */
    private fun buildUnavailable(screen: androidx.preference.PreferenceScreen) {
        val context = requireContext()
        screen.addPreference(
            SwitchPreferenceCompat(context).apply {
                key = KEY_SWITCH
                isPersistent = false
                widgetLayoutResource = R.layout.preference_switch_widget
                setIcon(category.icon)
                setTitle(category.title)
                isChecked = false
                isEnabled = false
            },
        )
        screen.addPreference(
            Preference(context).apply {
                key = KEY_NOTE
                isPersistent = false
                isSelectable = false
                setIcon(R.drawable.icon_info)
                setTitle(R.string.site_unavailable_title)
                setSummary(R.string.site_unavailable_text)
            },
        )
        fun bullets(vararg lines: Int) = lines.joinToString("\n\n") { "\u2022  " + getString(it) }
        screen.addPreference(
            TextMessagePreference(context).apply {
                key = "site_category_when_on"
                setTitle(R.string.cr_site_auto_verify_when_on)
                summary = bullets(
                    R.string.cr_site_auto_verify_on_1,
                    R.string.cr_site_auto_verify_on_2,
                    R.string.cr_site_auto_verify_on_3,
                )
            },
        )
        screen.addPreference(
            TextMessagePreference(context).apply {
                key = "site_category_consider"
                setTitle(R.string.cr_site_auto_verify_consider)
                summary = bullets(R.string.cr_site_auto_verify_consider_1)
            },
        )
    }

    /** The list the ad blocker works with; Explorer's ad setting besides the switch. */
    private fun filterList(context: Context): Preference = ListPreference(context).apply {
        key = "ab_hosts"
        setIcon(R.drawable.icon_filter)
        setTitle(R.string.site_ads_filter_list)
        setEntries(R.array.setting_entries_adBlock)
        setEntryValues(R.array.setting_values_adBlock)
        setDefaultValue(DEFAULT_FILTER_LIST)
        summaryProvider = Preference.SummaryProvider<ListPreference> {
            // The date the list was fetched comes first; before the first download there is none.
            val date = AdBlock.getHostsDate(context).orEmpty().trim()
            val name = it.entry?.toString().orEmpty()
            if (date.isEmpty()) name else "$date:\n$name"
        }
        setOnPreferenceChangeListener { _, _ ->
            // The new value is stored after this returns; fetch the list then.
            listView.post { AdBlock(context).triggerHostsDownload(context) }
            true
        }
    }

    private fun addSites(screen: androidx.preference.PreferenceScreen, allowed: Boolean) {
        val context = requireContext()
        val sites = SiteSettings.exceptions(SiteSettings.prefs(context), category, allowed)
        if (sites.isEmpty()) return
        val open = if (allowed) allowedOpen else blockedOpen
        val group = PreferenceCategory(context).also { screen.addPreference(it) }
        val rows = ArrayList<Preference>()
        group.addPreference(
            ExpandablePreference(context).apply {
                key = if (allowed) KEY_ALLOWED else KEY_BLOCKED
                val heading = when {
                    allowed -> R.string.cr_site_allowed_heading
                    category == SiteCategory.SOUND -> R.string.cr_site_muted_heading
                    else -> R.string.cr_site_not_allowed_heading
                }
                // As in Chromium: heading and number in the accent colour.
                title = SpannableString(getString(R.string.site_list_heading, getString(heading), sites.size)).apply {
                    setSpan(
                        ForegroundColorSpan(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)),
                        0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                isExpanded = open
                setOnPreferenceClickListener {
                    isExpanded = !isExpanded
                    if (allowed) allowedOpen = isExpanded else blockedOpen = isExpanded
                    rows.forEach { it.isVisible = isExpanded }
                    true
                }
            },
        )
        for (site in sites) {
            val row = Preference(context).apply {
                key = (if (allowed) "allowed:" else "blocked:") + site
                isPersistent = false
                setIcon(R.drawable.icon_web)
                title = site
                isVisible = open
                setOnPreferenceClickListener {
                    editSite(site, allowed)
                    true
                }
            }
            rows += row
            group.addPreference(row)
        }
    }

    /** Chromium's dialog for one site: Allow, Block, or remove the exception. */
    private fun editSite(site: String, allowed: Boolean) {
        val context = requireContext()
        val prefs = SiteSettings.prefs(context)
        val choices = arrayOf(getString(R.string.site_prompt_allow), getString(R.string.site_prompt_block))
        MaterialAlertDialogBuilder(context)
            .setTitle(site)
            .setSingleChoiceItems(choices, if (allowed) 0 else 1) { dialog, which ->
                SiteSettings.setException(prefs, category, site, which == 0)
                dialog.dismiss()
                build()
            }
            .setNeutralButton(R.string.site_remove) { _, _ ->
                SiteSettings.setException(prefs, category, site, null)
                build()
            }
            .setNegativeButton(R.string.app_cancel, null)
            .show()
    }

    /** Chromium's "Add a site" dialog: what the entry does, the field "Site URL", Cancel and Add. */
    private fun askForSite(allowed: Boolean) {
        val context = requireContext()
        val view = View.inflate(context, R.layout.dialog_edit_text, null)
        val input = view.findViewById<TextInputEditText>(R.id.textInput)
        input.setHint(R.string.cr_site_add_url)
        val text = (if (allowed) category.addAllowText else category.addBlockText).takeIf { it != 0 }
            ?: if (allowed) R.string.site_add_exception_allow else R.string.site_add_exception_block
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cr_site_add_dialog_title)
            .setMessage(text)
            .setView(view)
            .setNegativeButton(R.string.app_cancel, null)
            .setPositiveButton(R.string.cr_site_add_button) { _, _ -> addSite(input.text?.toString(), allowed) }
            .show()
    }

    /** Adds the exception; returns false if [text] does not name a site. */
    internal fun addSite(text: String?, allowed: Boolean): Boolean {
        val context = requireContext()
        val added = SiteSettings.setException(SiteSettings.prefs(context), category, text, allowed)
        if (added) {
            CatToast.show(context, getString(R.string.cr_site_add_toast, SiteSettings.site(text)), 0)
            build()
        }
        return added
    }

    companion object {
        private const val ARG_CATEGORY = "category"
        const val KEY_DESCRIPTION = "site_category_description"
        const val KEY_ON = "site_category_on"
        const val KEY_ASK = "site_category_ask"
        const val KEY_OFF = "site_category_off"
        const val KEY_SWITCH = "site_category_switch"
        const val KEY_NOTE = "site_category_note"
        const val KEY_ADD = "site_category_add"
        const val KEY_ALLOWED = "site_category_allowed"
        const val KEY_BLOCKED = "site_category_blocked"
        private const val DEFAULT_FILTER_LIST = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

        /**
         * [icon] with a stroke through it, the way Material draws "off" icons:
         * the stroke, and beside it a gap in the colour of the card so that the
         * stroke shows on filled icons as well. The picture comes in its final
         * colours (see [RadioRowPreference.iconHasColors]).
         */
        internal fun crossedOut(context: Context, icon: Int): Drawable {
            val ink = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
            val card = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceBright, 0)
            fun layer(id: Int, color: Int): Drawable = AppCompatResources.getDrawable(context, id)!!.mutate().apply {
                setTintList(android.content.res.ColorStateList.valueOf(color))
            }
            val layers = arrayOf(layer(icon, ink), layer(R.drawable.icon_slash_gap, card), layer(R.drawable.icon_slash, ink))
            // The parts keep their own colours: a view that shows the picture may not recolour it as a whole.
            return object : LayerDrawable(layers) {
                override fun setTintList(tint: android.content.res.ColorStateList?) = Unit
            }
        }

        @JvmStatic
        fun newInstance(categoryKey: String): SiteCategoryFragment = SiteCategoryFragment().apply {
            arguments = Bundle().apply { putString(ARG_CATEGORY, categoryKey) }
        }
    }
}
