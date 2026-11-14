package com.rpooi3.explorer.preferences

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.rpooi3.explorer.R
import com.rpooi3.explorer.unit.BrowserUnit
import com.rpooi3.explorer.view.CatToast
import org.xmlpull.v1.XmlPullParser

/**
 * How the settings are divided up. The first settings screen lists topics by
 * title only, like Chrome's; each topic opens on a full screen of its own.
 *
 * A [Section] is a topic whose settings are shown by the settings screen
 * itself. A few topics (gestures, bookmark filter, backup) have had their own
 * screens all along; the first screen opens those directly.
 */
object SettingsSections {

    /**
     * @param key key of the entry on the first settings screen
     * @param title heading of the topic's screen
     * @param xml the settings of the topic
     */
    data class Section(val key: String, @StringRes val title: Int, @XmlRes val xml: Int)

    @JvmField
    val ALL: List<Section> = listOf(
        Section("section_search", R.string.setting_title_search_engine, R.xml.preference_section_search),
        Section("section_privacy", R.string.setting_section_privacy, R.xml.preference_section_privacy),
        Section("section_home_custom", R.string.setting_section_home_custom, R.xml.preference_section_home_custom),
        Section("section_tabs", R.string.setting_section_tabs, R.xml.preference_section_tabs),
        Section("section_appearance", R.string.setting_section_appearance, R.xml.preference_section_appearance),
        Section("section_site", R.string.setting_section_site, R.xml.preference_section_site),
        Section("section_help", R.string.setting_section_help, R.xml.preference_section_help),
        Section("section_about", R.string.setting_section_about, R.xml.preference_section_about),
        Section("section_accessibility", R.string.setting_section_accessibility, R.xml.preference_section_accessibility),
    )

    /** Screens behind a row of a topic, laid out and opened like the topics themselves. */
    @JvmField
    val SUB: List<Section> = listOf(
        Section("section_preload", R.string.cr_preload_pages_title, R.xml.preference_section_preload),
        Section("section_safe_browsing", R.string.cr_safe_browsing_title, R.xml.preference_section_safe_browsing),
        Section("section_js_optimizer", R.string.cr_js_optimizer_title, R.xml.preference_section_js_optimizer),
        Section("section_inactive_tabs", R.string.cr_archive_title, R.xml.preference_section_inactive_tabs),
        Section("section_saved_zoom", R.string.cr_zoom_info_title, R.xml.preference_section_saved_zoom),
        Section("section_legal", R.string.cr_legal_information_title, R.xml.preference_section_legal),
        Section("section_developer", R.string.about_developer_title, R.xml.preference_section_developer),
    )

    /** Opens the screen of every row of [fragment] that stands for a topic or a screen behind one. */
    @JvmStatic
    fun bindNavigation(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        for (section in ALL + SUB) {
            fragment.findPreference<Preference>(section.key)?.setOnPreferenceClickListener {
                fragment.startActivity(
                    Intent(context, com.rpooi3.explorer.activity.Settings_Activity::class.java)
                        .putExtra(com.rpooi3.explorer.activity.Settings_Activity.EXTRA_SECTION, section.key),
                )
                true
            }
        }
    }

    /** Topics with a screen of their own: entry key to the settings that screen shows. */
    private val ownScreens: Map<String, Int> = mapOf(
        "settings_gesture" to R.xml.preference_gesture,
        "settings_filter" to R.xml.preference_filter,
        "settings_data" to R.xml.preference_backup,
    )

    /** Extra of an entry on the first screen: the words the settings search finds it by. */
    const val EXTRA_KEYWORDS = "com.rpooi3.explorer.preferences.KEYWORDS"

    const val DEVELOPER_EMAIL = "rpooi3@duck.com"
    const val URL_GITHUB = "https://github.com/nadib330"
    const val URL_CODEBERG = "https://codeberg.org/rpooi3"
    const val URL_SOURCE = "https://codeberg.org/rpooi3/FOSS_Explorer"
    const val URL_SOURCE_UPSTREAM = "https://codeberg.org/Gaukler_Faun/FOSS_Browser"
    /** Both licences of the app are published here: GPL-3.0 and AGPL-3.0. See LICENSING.md. */
    const val CHROMIUM_LICENSE_ASSET = "offline/LICENSE.chromium.txt"
    const val URL_LICENSE = "https://www.gnu.org/licenses/licenses.html"

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"

    @JvmStatic
    fun byKey(key: String?): Section? = (ALL + SUB).firstOrNull { it.key == key }

    /**
     * The titles and summaries of all settings in a settings file, one per
     * line. The first settings screen shows topics only; with these words its
     * search field still finds the topic that contains a setting.
     */
    @JvmStatic
    fun keywords(context: Context, @XmlRes xml: Int): String {
        val words = StringBuilder()
        val parser = context.resources.getXml(xml)
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    for (name in arrayOf("title", "summary")) {
                        for (namespace in arrayOf(ANDROID_NS, APP_NS)) {
                            val resource = parser.getAttributeResourceValue(namespace, name, 0)
                            val text = if (resource != 0) context.getString(resource)
                            else parser.getAttributeValue(namespace, name)
                            if (!text.isNullOrBlank()) words.append(text).append('\n')
                        }
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
        return words.toString()
    }

    /** Gives every topic of the first settings screen the words it can be found by. */
    @JvmStatic
    fun attachKeywords(screen: PreferenceGroup) {
        val context = screen.context
        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceGroup) {
                attachKeywords(pref)
                continue
            }
            val xml = byKey(pref.key)?.xml ?: ownScreens[pref.key] ?: continue
            pref.extras.putString(EXTRA_KEYWORDS, keywords(context, xml))
        }
    }

    /** Fills in and wires up the entries of "Help and support" and "About". */
    @JvmStatic
    fun bindInfo(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        fragment.findPreference<Preference>("help_email")?.setOnPreferenceClickListener {
            writeToDeveloper(context)
            true
        }
        mapOf(
            "about_github" to URL_GITHUB,
            "about_codeberg" to URL_CODEBERG,
            "about_source" to URL_SOURCE,
            "about_source_upstream" to URL_SOURCE_UPSTREAM,
            "about_license" to URL_LICENSE,
        ).forEach { (key, url) ->
            fragment.findPreference<Preference>(key)?.setOnPreferenceClickListener {
                // Opens in this browser, in a new tab.
                BrowserUnit.intentURL(context, url.toUri())
                true
            }
        }
    }

    @JvmStatic
    fun versionName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }

    /** The e-mail that "Write to the developer" starts: addressed, with the app version as subject. */
    @JvmStatic
    fun emailIntent(context: Context): Intent =
        Intent(Intent.ACTION_SENDTO, "mailto:$DEVELOPER_EMAIL".toUri())
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.help_email_subject, versionName(context)))

    private fun writeToDeveloper(context: Context) {
        try {
            context.startActivity(emailIntent(context))
        } catch (e: ActivityNotFoundException) {
            // Without an e-mail app the address is still of use on the clipboard.
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.setPrimaryClip(ClipData.newPlainText("e-mail", DEVELOPER_EMAIL))
            CatToast.show(context, R.string.help_email_no_app)
        }
    }
}
