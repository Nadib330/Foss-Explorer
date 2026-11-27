package com.rpooi3.explorer.site

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.List_standard
import com.rpooi3.explorer.unit.HelperUtils
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * What websites may do: one general choice per [SiteCategory] and exceptions
 * for single sites, as in Chromium's site settings.
 *
 * The general choice of the categories that existed before lives where it
 * always did, in the settings of the profile in use (`<profile>_javascript`
 * and so on), so the profiles and this screen show the same thing. Everything
 * else is stored under keys that start with `site_`.
 */
object SiteSettings {
    enum class State { ALLOW, ASK, BLOCK }

    /** Setting "Automatically remove permissions". */
    const val AUTO_REVOKE = "site_auto_revoke"

    /** A permission counts as unused when its site was not visited for this long, as in Chromium. */
    const val UNUSED_AFTER_DAYS = 60L

    private const val VISITS = "site_visits"
    private const val DEFAULT_PROFILE = "profileStandard"

    /** Categories whose "allowed" entries are permissions that can be removed when unused. */
    private val REVOCABLE = listOf(
        SiteCategory.LOCATION, SiteCategory.CAMERA, SiteCategory.MICROPHONE, SiteCategory.AUTOMATIC_DOWNLOADS,
    )

    @JvmStatic
    fun prefs(context: Context): SharedPreferences =
        com.rpooi3.explorer.profile.ProfilePrefs.settings(context)

    /**
     * The site a web address or origin belongs to: its host in lower case
     * without a leading `www.`; empty if there is none.
     */
    @JvmStatic
    fun site(urlOrHost: String?): String {
        var text = urlOrHost?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (text.isEmpty()) return ""
        if ("://" in text) {
            text = text.toUri().host.orEmpty()
        } else {
            // "[*.]example.org", "example.org/path", "example.org:8080"
            text = text.removePrefix("[*.]").substringBefore('/').substringBefore(':')
        }
        text = text.trim('.').removePrefix("www.")
        return if (text.isEmpty() || text.any { it.isWhitespace() || it == ',' }) "" else text
    }

    /** Whether [host] names this device itself. */
    @JvmStatic
    fun isLoopback(host: String?): Boolean {
        val name = host?.lowercase(Locale.ROOT)?.trim('[', ']') ?: return false
        return name == "localhost" || name.endsWith(".localhost") || name == "::1" ||
            Regex("""127(\.\d{1,3}){3}""").matches(name)
    }

    /** The profile whose settings hold the general choices: the one in use. */
    @JvmStatic
    fun activeProfile(prefs: SharedPreferences): String =
        prefs.getString("profile", DEFAULT_PROFILE) ?: DEFAULT_PROFILE

    /** The profile that applies to [url]: the site's own if it has one, else the one in use. */
    @JvmStatic
    fun profileFor(context: Context, url: String?): String {
        val prefs = prefs(context)
        return if (url != null && List_standard(context).isWhite(url)) HelperUtils.domain(url) else activeProfile(prefs)
    }

    // General choice ------------------------------------------------------------------------

    /** The position of the category's switch. */
    @JvmStatic
    @JvmOverloads
    fun isOn(prefs: SharedPreferences, category: SiteCategory, profile: String = activeProfile(prefs)): Boolean {
        if (category.kind == SiteCategory.Kind.UNAVAILABLE) return false
        val profileKey = category.profileKeys.firstOrNull()
            ?: return prefs.getBoolean(defaultKey(category), category.defaultOn)
        val stored = prefs.getBoolean(profile + profileKey, category.defaultOn != category.inverted)
        return stored != category.inverted
    }

    @JvmStatic
    fun setOn(prefs: SharedPreferences, category: SiteCategory, on: Boolean) {
        if (category.kind == SiteCategory.Kind.UNAVAILABLE) return
        val editor = prefs.edit()
        if (category.profileKeys.isEmpty()) {
            editor.putBoolean(defaultKey(category), on)
        } else {
            val profile = activeProfile(prefs)
            category.profileKeys.forEach { editor.putBoolean(profile + it, on != category.inverted) }
        }
        editor.apply()
    }

    /**
     * Whether sites that are not blocked have to ask first. That is so for
     * the categories of [SiteCategory.Kind.ASK], and for protected content if
     * "Sites can ask to play protected content" is chosen.
     */
    @JvmStatic
    fun asksFirst(prefs: SharedPreferences, category: SiteCategory): Boolean = when {
        category.kind == SiteCategory.Kind.ASK -> true
        category.hasAskChoice -> prefs.getBoolean(askKey(category), false)
        else -> false
    }

    /** Chooses between "allowed" (false) and "sites can ask" (true) for a category with three choices. */
    @JvmStatic
    fun setAsksFirst(prefs: SharedPreferences, category: SiteCategory, ask: Boolean) {
        if (category.hasAskChoice) prefs.edit { putBoolean(askKey(category), ask) }
    }

    // Exceptions ----------------------------------------------------------------------------

    /** The sites set to "allowed" (or "blocked") for [category], sorted. */
    @JvmStatic
    fun exceptions(prefs: SharedPreferences, category: SiteCategory, allowed: Boolean): List<String> =
        prefs.getString(listKey(category, allowed), "").orEmpty().split('\n').filter { it.isNotEmpty() }.sorted()

    /**
     * The choice stored for [site] itself or for a domain it belongs to
     * (`example.org` also covers `m.example.org`); null if there is none.
     */
    @JvmStatic
    fun exception(prefs: SharedPreferences, category: SiteCategory, site: String?): Boolean? {
        val host = site(site)
        if (host.isEmpty()) return null
        fun covers(entry: String) = host == entry || host.endsWith(".$entry")
        // The entry closest to the site wins; if both lists name it, "blocked" does.
        val blocked = exceptions(prefs, category, false).filter(::covers).maxOfOrNull { it.length }
        val allowed = exceptions(prefs, category, true).filter(::covers).maxOfOrNull { it.length }
        return when {
            blocked == null && allowed == null -> null
            blocked == null -> true
            allowed == null -> false
            else -> allowed > blocked
        }
    }

    /** Stores the choice for [site]; null removes it. Returns false if [site] is not a site. */
    @JvmStatic
    fun setException(prefs: SharedPreferences, category: SiteCategory, site: String?, allowed: Boolean?): Boolean {
        val host = site(site)
        if (host.isEmpty() || category.kind == SiteCategory.Kind.UNAVAILABLE) return false
        val editor = prefs.edit()
        for (list in listOf(true, false)) {
            val sites = exceptions(prefs, category, list).toMutableSet()
            if (allowed == list) sites.add(host) else sites.remove(host)
            editor.putString(listKey(category, list), sites.sorted().joinToString("\n"))
        }
        editor.apply()
        if (allowed != null) recordVisit(prefs, host, System.currentTimeMillis())
        return true
    }

    // Decision ------------------------------------------------------------------------------

    /** What [url] may do in [category]. [profile] is the profile that applies to it. */
    @JvmStatic
    fun state(prefs: SharedPreferences, category: SiteCategory, url: String?, profile: String): State {
        if (category.kind == SiteCategory.Kind.UNAVAILABLE) return State.BLOCK
        exception(prefs, category, url)?.let { return if (it) State.ALLOW else State.BLOCK }
        return when {
            !isOn(prefs, category, profile) -> State.BLOCK
            asksFirst(prefs, category) -> State.ASK
            else -> State.ALLOW
        }
    }

    @JvmStatic
    fun state(context: Context, category: SiteCategory, url: String?): State =
        state(prefs(context), category, url, profileFor(context, url))

    @JvmStatic
    fun isAllowed(prefs: SharedPreferences, category: SiteCategory, url: String?, profile: String): Boolean =
        state(prefs, category, url, profile) == State.ALLOW

    @JvmStatic
    fun isAllowed(context: Context, category: SiteCategory, url: String?): Boolean =
        state(context, category, url) == State.ALLOW

    // Unused permissions --------------------------------------------------------------------

    /**
     * Notes that [url] was visited at [now]. Only sites that hold a permission
     * are noted, so this is not a second history.
     */
    @JvmStatic
    fun recordVisit(prefs: SharedPreferences, url: String?, now: Long) {
        val host = site(url)
        if (host.isEmpty()) return
        val holders = permissionHolders(prefs)
        val covering = holders.filter { host == it || host.endsWith(".$it") }
        if (covering.isEmpty()) return
        val visits = visits(prefs)
        covering.forEach { visits[it] = now }
        saveVisits(prefs, visits.filterKeys { it in holders })
    }

    /**
     * Removes the permissions of sites not visited for [UNUSED_AFTER_DAYS],
     * if the user has left that switched on. Returns how many were removed.
     */
    @JvmStatic
    fun removeUnusedPermissions(prefs: SharedPreferences, now: Long): Int {
        if (!prefs.getBoolean(AUTO_REVOKE, true)) return 0
        val limit = TimeUnit.DAYS.toMillis(UNUSED_AFTER_DAYS)
        val visits = visits(prefs)
        var removed = 0
        for (category in REVOCABLE) {
            for (host in exceptions(prefs, category, true)) {
                // A permission from before visits were noted starts counting now.
                val last = visits.getOrPut(host) { now }
                if (now - last > limit) {
                    setException(prefs, category, host, null)
                    removed++
                }
            }
        }
        saveVisits(prefs, visits.filterKeys { it in permissionHolders(prefs) })
        return removed
    }

    private fun permissionHolders(prefs: SharedPreferences): Set<String> =
        REVOCABLE.flatMap { exceptions(prefs, it, true) }.toSet()

    private fun visits(prefs: SharedPreferences): MutableMap<String, Long> =
        prefs.getString(VISITS, "").orEmpty().split('\n').mapNotNull { line ->
            val time = line.substringAfter('=', "").toLongOrNull() ?: return@mapNotNull null
            line.substringBefore('=') to time
        }.toMap().toMutableMap()

    private fun saveVisits(prefs: SharedPreferences, visits: Map<String, Long>) {
        prefs.edit { putString(VISITS, visits.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    }

    // Texts ---------------------------------------------------------------------------------

    /** The state shown under the category's name on the Site settings screen. */
    @JvmStatic
    fun summary(context: Context, category: SiteCategory): String {
        val on = isOn(prefs(context), category)
        return context.getString(
            when {
                category.kind == SiteCategory.Kind.UNAVAILABLE -> R.string.site_state_unavailable
                !on && category == SiteCategory.SOUND -> R.string.site_state_muted
                !on -> R.string.site_state_blocked
                asksFirst(prefs(context), category) -> R.string.site_state_ask
                else -> R.string.site_state_allowed
            },
        )
    }

    private fun defaultKey(category: SiteCategory) = "site_default_${category.key}"

    private fun askKey(category: SiteCategory) = "site_ask_${category.key}"

    private fun listKey(category: SiteCategory, allowed: Boolean) =
        (if (allowed) "site_allow_" else "site_block_") + category.key
}
