/*
 * Foss Explorer: xGuard's tracking analysis.
 * Own code, GPL-3.0.
 *
 * Protection that cannot be seen is protection nobody trusts. This keeps
 * count of every request a page makes, which hosts they went to, which of
 * them are trackers and what xGuard did with them, for the page in each tab
 * and for the page that was analysed last. The panel behind the shield and
 * the xGuard settings both show it.
 */
package com.rpooi3.explorer.privacy

import com.rpooi3.explorer.profile.ProfilePrefs
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rpooi3.explorer.R
import com.rpooi3.explorer.site.SiteSettings
import com.rpooi3.explorer.view.NinjaWebView
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap

object TrackingAnalysis {

    /**
     * What a page did: how many requests it made, which hosts they went to,
     * and which of those hosts xGuard knows as trackers (host to kind).
     */
    class Report(
        /** The site the page belongs to; empty when the page has no host. */
        @JvmField val site: String,
        /** Requests the page made, the page itself not counted. */
        @JvmField val requests: Int,
        /** The hosts those requests went to, each with the number of requests. */
        @JvmField val hosts: Map<String, Int>,
        /** The hosts that are known trackers, each with its kind. */
        @JvmField val trackers: Map<String, String>,
        /** Requests xGuard stopped. */
        @JvmField val blocked: Int,
    ) {
        /** How many requests went to other sites than the page's own. */
        val thirdParty: Int
            get() = hosts.entries.sumOf { (host, count) -> if (isThirdParty(host)) count else 0 }

        fun isThirdParty(host: String): Boolean {
            if (site.isEmpty()) return false
            return host != site && !host.endsWith(".$site")
        }

        /** The hosts with the most requests first. */
        fun hostsBySize(): List<Pair<String, Int>> = hosts.entries
            .sortedWith(compareBy({ -it.value }, { it.key }))
            .map { it.key to it.value }

        /** The trackers that were stopped, the busiest first. */
        fun blockedTrackers(): List<Pair<String, String>> = trackers.entries
            .filter { (host, _) -> !host.isEmpty() }
            .sortedBy { it.key }
            .map { it.key to it.value }

        companion object {
            @JvmField
            val EMPTY = Report("", 0, emptyMap(), emptyMap(), 0)
        }
    }

    private class Page {
        var requests = 0
        var blocked = 0
        val hosts = LinkedHashMap<String, Int>()
        val trackers = LinkedHashMap<String, String>()
    }

    private val pages = WeakHashMap<NinjaWebView, Page>()

    // What the xGuard settings show about the last page that was analysed ------

    private const val KEY_SITE = "xguard_analysis_site"
    private const val KEY_REQUESTS = "xguard_analysis_requests"
    private const val KEY_TRACKERS = "xguard_analysis_trackers"
    private const val KEY_BLOCKED = "xguard_analysis_blocked"
    private const val KEY_TIME = "xguard_analysis_time"
    private const val KEY_TOP = "xguard_analysis_top"

    /** A page starts to load in [tab]: its count begins again. */
    @JvmStatic
    fun onPageStarted(tab: NinjaWebView) {
        synchronized(pages) { pages[tab] = Page() }
    }

    /**
     * [tab] asked for [url]. Called for every request of the page from the
     * engine's own threads; [mainFrame] is true for the page itself, which is
     * not a request the page made.
     */
    @JvmStatic
    fun onRequest(tab: NinjaWebView, url: String?, mainFrame: Boolean) {
        val host = hostOf(url) ?: return
        synchronized(pages) {
            val page = pages.getOrPut(tab) { Page() }
            if (mainFrame) {
                // A new page in the same tab: the count belongs to the old one.
                if (page.hosts.isNotEmpty() || page.requests > 0) {
                    page.requests = 0
                    page.blocked = 0
                    page.hosts.clear()
                    page.trackers.clear()
                }
                return
            }
            page.requests++
            page.hosts[host] = (page.hosts[host] ?: 0) + 1
        }
    }

    /** xGuard knows [host] as a tracker of [kind]: it is written down for the report. */
    @JvmStatic
    fun onTracker(tab: NinjaWebView, host: String?, kind: String?) {
        if (host.isNullOrEmpty() || kind.isNullOrEmpty()) return
        synchronized(pages) {
            pages.getOrPut(tab) { Page() }.trackers[host] = kind
        }
    }

    /** One more request of [tab] was stopped. */
    @JvmStatic
    fun onBlocked(tab: NinjaWebView) {
        synchronized(pages) { pages.getOrPut(tab) { Page() }.blocked++ }
    }

    /** What [tab]'s page did; empty if nothing is known about it. */
    @JvmStatic
    fun report(tab: NinjaWebView): Report = synchronized(pages) {
        val page = pages[tab] ?: return Report.EMPTY
        Report(
            site = SiteSettings.site(tab.url),
            requests = page.requests,
            hosts = LinkedHashMap(page.hosts),
            trackers = LinkedHashMap(page.trackers),
            blocked = page.blocked,
        )
    }

    /**
     * The page of [tab] is loaded: its report is kept for the xGuard settings,
     * unless the tab is private — what happens in a private tab stays there.
     */
    @JvmStatic
    fun onPageFinished(tab: NinjaWebView) {
        if (tab.isPrivateTab) return
        val report = report(tab)
        if (report.site.isEmpty()) return
        val prefs = SiteSettings.prefs(tab.context)
        val top = report.hostsBySize().take(5).joinToString("\n") { (host, count) -> "$host\t$count" }
        prefs.edit {
            putString(KEY_SITE, report.site)
            putInt(KEY_REQUESTS, report.requests)
            putInt(KEY_TRACKERS, report.trackers.size)
            putInt(KEY_BLOCKED, report.blocked)
            putLong(KEY_TIME, System.currentTimeMillis())
            putString(KEY_TOP, top)
        }
        // What the panel shows for the page is kept as well, so the settings
        // can tell how much of it xGuard stopped.
        prefs.edit { putInt("xguard_analysis_page_blocked", TrackingProtection.blockedCount(tab)) }
    }

    // What the settings screen says -------------------------------------------------

    /** Whether anything has been analysed yet. */
    @JvmStatic
    fun hasLastReport(context: Context): Boolean =
        prefs(context).getString(KEY_SITE, "").orEmpty().isNotEmpty()

    /**
     * The last analysed page as lines for a dialog: the site and when it was
     * seen, what it asked for, what of that xGuard knew and stopped, and the
     * busiest hosts.
     */
    @JvmStatic
    fun lastReportText(context: Context): String {
        val prefs = prefs(context)
        val site = prefs.getString(KEY_SITE, "").orEmpty()
        if (site.isEmpty()) return context.getString(R.string.xguard_analysis_empty)
        val requests = prefs.getInt(KEY_REQUESTS, 0)
        val trackers = prefs.getInt(KEY_TRACKERS, 0)
        val blocked = prefs.getInt(KEY_BLOCKED, 0)
        val time = prefs.getLong(KEY_TIME, 0L)
        val lines = ArrayList<String>()
        lines += context.getString(R.string.xguard_analysis_site, site)
        if (time > 0L) {
            val stamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
            lines += context.getString(R.string.xguard_analysis_time, stamp)
        }
        lines += context.getString(R.string.xguard_analysis_requests, requests)
        lines += context.getString(R.string.xguard_analysis_trackers, trackers)
        lines += context.getString(R.string.xguard_analysis_blocked, blocked)
        val top = prefs.getString(KEY_TOP, "").orEmpty().lines().filter { it.isNotEmpty() }
        if (top.isNotEmpty()) {
            lines += ""
            lines += context.getString(R.string.xguard_analysis_top_hosts)
            top.forEach { line ->
                val host = line.substringBefore('\t')
                val count = line.substringAfter('\t', "1").toIntOrNull() ?: 1
                lines += "  \u2022  $host  \u00d7$count"
            }
        }
        return lines.joinToString("\n")
    }

    /** The last analysed page in one line, for the row that opens the report. */
    @JvmStatic
    fun lastReportSummary(context: Context): String {
        val prefs = prefs(context)
        val site = prefs.getString(KEY_SITE, "").orEmpty()
        if (site.isEmpty()) return context.getString(R.string.xguard_analysis_none)
        return context.getString(
            R.string.xguard_analysis_row_summary,
            site,
            prefs.getInt(KEY_REQUESTS, 0),
            prefs.getInt(KEY_TRACKERS, 0),
        )
    }

    /** Forgets the last analysed page, and the counts of all tabs. */
    @JvmStatic
    fun clear(context: Context) {
        synchronized(pages) { pages.clear() }
        prefs(context).edit {
            remove(KEY_SITE)
            remove(KEY_REQUESTS)
            remove(KEY_TRACKERS)
            remove(KEY_BLOCKED)
            remove(KEY_TIME)
            remove(KEY_TOP)
            remove("xguard_analysis_page_blocked")
        }
    }

    /** The report of the page in [tab], as a dialog. */
    @JvmStatic
    fun showReport(activity: Activity, tab: NinjaWebView) {
        val report = report(tab)
        val text = if (report.site.isEmpty() && report.requests == 0) {
            activity.getString(R.string.xguard_analysis_empty)
        } else {
            pageText(activity, report)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.xguard_analysis_title)
            .setMessage(text)
            .setPositiveButton(R.string.app_ok, null)
            .show()
    }

    /** The last analysed page, as a dialog. */
    @JvmStatic
    fun showLastReport(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.xguard_analysis_title)
            .setMessage(lastReportText(activity))
            .setPositiveButton(R.string.app_ok, null)
            .show()
    }

    /** One page's report, in words. */
    @JvmStatic
    fun pageText(context: Context, report: Report): String {
        val lines = ArrayList<String>()
        if (report.site.isNotEmpty()) lines += context.getString(R.string.xguard_analysis_site, report.site)
        lines += context.getString(R.string.xguard_analysis_requests, report.requests)
        lines += context.getString(R.string.xguard_analysis_third_party, report.thirdParty)
        lines += context.getString(R.string.xguard_analysis_trackers, report.trackers.size)
        if (report.trackers.isNotEmpty()) {
            val kinds = report.trackers.values.groupingBy { it }.eachCount()
            lines += "  " + trackerKinds(context, kinds)
        }
        lines += context.getString(R.string.xguard_analysis_blocked, report.blocked)
        val hosts = report.hostsBySize().take(8)
        if (hosts.isNotEmpty()) {
            lines += ""
            lines += context.getString(R.string.xguard_analysis_top_hosts)
            hosts.forEach { (host, count) ->
                val kind = report.trackers[host]
                val mark = if (kind == null) "" else "  (" + kindName(context, kind) + ")"
                lines += "  \u2022  $host  \u00d7$count$mark"
            }
        }
        return lines.joinToString("\n")
    }

    /** "analytics 3, advertising 2" */
    @JvmStatic
    fun trackerKinds(context: Context, kinds: Map<String, Int>): String =
        kinds.entries.sortedBy { it.key }
            .joinToString(", ") { (kind, count) -> kindName(context, kind) + " " + count }

    /** The name of a kind of tracker, as the settings show it. */
    @JvmStatic
    fun kindName(context: Context, kind: String?): String = context.getString(
        when (kind?.lowercase(Locale.ROOT)) {
            TrackerList.ANALYTICS -> R.string.xguard_kind_analytics
            TrackerList.ADVERTISING -> R.string.xguard_kind_advertising
            TrackerList.SOCIAL -> R.string.xguard_kind_social
            TrackerList.FINGERPRINT -> R.string.xguard_kind_fingerprint
            else -> R.string.xguard_kind_unknown
        },
    )

    private fun hostOf(url: String?): String? = try {
        url.orEmpty().toUri().host?.lowercase(Locale.ROOT)
    } catch (e: Exception) {
        null
    }

    private fun prefs(context: Context): SharedPreferences = ProfilePrefs.settings(context)
}
