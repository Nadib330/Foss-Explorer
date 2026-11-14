/*
 * Foss Explorer: xGuard's tracker blocker.
 * Own code, GPL-3.0.
 *
 * The ad blocker (AdBlock, the filter list) and the tracker blocker work
 * together but are chosen apart: the filter list is a single list of hosts to
 * leave alone, this one knows what kind of tracker a host is and lets the
 * settings switch the kinds one by one. Hosts come with the app
 * (assets/xguard/trackers.txt), so xGuard holds before the first filter list
 * has been downloaded and without a network connection.
 */
package com.rpooi3.explorer.privacy

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object TrackerList {

    // The kinds, as they are written in the asset.
    const val ANALYTICS = "analytics"
    const val ADVERTISING = "advertising"
    const val SOCIAL = "social"
    const val FINGERPRINT = "fingerprint"

    /** xGuard's own setting: whether the tracker blocker works at all. */
    const val KEY_TRACKING_BLOCKER = "xguard_tracking_blocker"

    /** The kinds that have a switch of their own; the others follow the blocker. */
    const val KEY_BLOCK_ANALYTICS = "xguard_block_analytics"
    const val KEY_BLOCK_SOCIAL = "xguard_block_social"

    const val ASSET = "xguard/trackers.txt"

    /** Host to kind; read once from the asset and kept for the life of the process. */
    @Volatile
    private var lookup: Map<String, String>? = null

    private fun read(context: Context): Map<String, String> {
        lookup?.let { return it }
        synchronized(this) {
            lookup?.let { return it }
            val hosts = HashMap<String, String>(512)
            try {
                context.assets.open(ASSET).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val text = line.trim()
                        if (text.isEmpty() || text.startsWith("#")) continue
                        val parts = text.split(' ', '\t').filter { it.isNotEmpty() }
                        if (parts.size < 2) continue
                        val kind = parts[0].lowercase(Locale.ROOT)
                        // A host that is named twice keeps its first kind.
                        hosts.putIfAbsent(parts[1].lowercase(Locale.ROOT), kind)
                    }
                }
            } catch (e: Exception) {
                // Without the asset xGuard simply has no list of its own; the filter list still works.
            }
            lookup = hosts
            return hosts
        }
    }

    /** All hosts xGuard knows, each with its kind. */
    @JvmStatic
    fun entries(context: Context): Map<String, String> = read(context)

    /** How many tracker hosts xGuard knows. */
    @JvmStatic
    fun count(context: Context): Int = read(context).size

    /** The kind of [host], or of the domain it belongs to; null if it is no tracker. */
    @JvmStatic
    fun kindOf(context: Context, host: String?): String? {
        var name = host?.lowercase(Locale.ROOT)?.trim()?.trim('.') ?: return null
        if (name.isEmpty()) return null
        val hosts = read(context)
        while (true) {
            hosts[name]?.let { return it }
            val dot = name.indexOf('.')
            if (dot < 0 || dot == name.length - 1) return null
            name = name.substring(dot + 1)
            if (!name.contains('.')) return null
        }
    }

    // What the settings say ----------------------------------------------------------------

    /** Whether the tracker blocker is switched on (`true` until it was switched off once). */
    @JvmStatic
    fun isOn(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_TRACKING_BLOCKER, true)

    /** Whether the kind may be blocked: the blocker is on, and so is the kind's own switch. */
    @JvmStatic
    fun isKindOn(prefs: SharedPreferences, kind: String?): Boolean {
        if (kind == null || !isOn(prefs)) return false
        return when (kind) {
            ANALYTICS -> prefs.getBoolean(KEY_BLOCK_ANALYTICS, true)
            SOCIAL -> prefs.getBoolean(KEY_BLOCK_SOCIAL, false)
            else -> true
        }
    }

    /** Whether a request to [host] is stopped by the tracker blocker. */
    @JvmStatic
    fun blocks(context: Context, prefs: SharedPreferences, host: String?): Boolean =
        isKindOn(prefs, kindOf(context, host))

    /** The kinds that are blocked now, for the summary of the xGuard screen. */
    @JvmStatic
    fun blockedKinds(prefs: SharedPreferences): List<String> {
        if (!isOn(prefs)) return emptyList()
        return listOf(ANALYTICS, ADVERTISING, SOCIAL, FINGERPRINT).filter { isKindOn(prefs, it) }
    }
}
