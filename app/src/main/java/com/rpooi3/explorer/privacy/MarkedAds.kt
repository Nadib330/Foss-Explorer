/*
 * Explorer: xGuard's marked ads.
 * Own code, GPL-3.0.
 *
 * From 16.1.0 an ad can be marked on the page itself: a long press on the
 * picture or the link, then "Mark as ad and hide". The mark is kept here, the
 * element is hidden on every page by a script, and the request that loads it
 * is stopped, as if the host were on the filter list. The list is managed in
 * Settings > xGuard > Marked ads, where a mark can be removed and an address
 * can be added by hand.
 */
package com.rpooi3.explorer.privacy

import com.rpooi3.explorer.profile.ProfilePrefs
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import java.util.Locale

object MarkedAds {

    /** The marks, one address each, in a string set. Hosts written by hand are kept as they are. */
    private const val KEY = "xguard_marked_ads"

    private fun prefs(context: Context): SharedPreferences = ProfilePrefs.settings(context)

    /** The marked addresses, sorted. */
    @JvmStatic
    fun list(context: Context): List<String> =
        prefs(context).getStringSet(KEY, emptySet()).orEmpty()
            .map { it.trim() }.filter { it.isNotEmpty() }.sorted()

    /** How many ads are marked. */
    @JvmStatic
    fun count(context: Context): Int = list(context).size

    /**
     * Marks [address]. False if it is no web address, or if it is marked
     * already (compared without the scheme, so a link and its host match).
     */
    @JvmStatic
    fun add(context: Context, address: String?): Boolean {
        val url = normalise(address) ?: return false
        val marks = prefs(context).getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (marks.any { it.equals(url, ignoreCase = true) || stripScheme(it) == stripScheme(url) }) return false
        marks += url
        prefs(context).edit { putStringSet(KEY, marks) }
        return true
    }

    /** Removes [address] from the marks. */
    @JvmStatic
    fun remove(context: Context, address: String?) {
        val marks = prefs(context).getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        marks.removeAll { it == address || stripScheme(it) == stripScheme(address.orEmpty()) }
        prefs(context).edit { putStringSet(KEY, marks) }
    }

    /** Whether [address] is marked itself. */
    @JvmStatic
    fun isMarked(context: Context, address: String?): Boolean {
        val url = stripScheme(address.orEmpty())
        if (url.isEmpty()) return false
        return list(context).any { stripScheme(it) == url }
    }

    /**
     * Whether a request to [url] belongs to a marked ad: it is the marked
     * address or lies below it, or the mark is a bare host and the request
     * goes to that host or a subdomain of it.
     */
    @JvmStatic
    fun blocks(context: Context, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val target = stripScheme(url).substringBefore('#')
        if (target.isEmpty()) return false
        val targetHost = hostOf(url)
        for (mark in list(context)) {
            val marked = stripScheme(mark).substringBefore('#')
            if (marked.isEmpty()) continue
            if (hasPath(marked)) {
                // An address with a path: exactly that request, or anything below it.
                if (target == marked || target.startsWith("$marked?") || target.startsWith("$marked/")) return true
            } else {
                // A bare host: everything from it.
                if (targetHost.isNotEmpty() && (targetHost == marked || targetHost.endsWith(".$marked"))) return true
            }
        }
        return false
    }

    /** What the page hides: every element that loads or points at a marked address. */
    @JvmStatic
    fun hideScript(context: Context): String {
        val marks = list(context)
        if (marks.isEmpty()) return ""
        val needles = marks.map { stripScheme(it) }.joinToString("\",\"") { it.replace("\"", "\\\"") }
        return """
            (function(){
              var marks = ["$needles"];
              function marked(v){
                if (!v) return false;
                v = String(v).toLowerCase().replace(/^[a-z]+:\/\//, '');
                for (var i = 0; i < marks.length; i++) {
                  if (!marks[i]) continue;
                  if (v === marks[i] || v.indexOf(marks[i]) === 0) return true;
                  if (marks[i].indexOf('/') < 0) {
                    var host = v.split('/')[0];
                    if (host === marks[i] || host.slice(-(marks[i].length + 1)) === '.' + marks[i]) return true;
                  }
                }
                return false;
              }
              var nodes = document.querySelectorAll('img, iframe, ins, object, embed, video, amp-ad, a[href]');
              var hidden = 0;
              for (var i = 0; i < nodes.length; i++) {
                var el = nodes[i];
                var ref = el.currentSrc || el.src || el.data || el.href || '';
                if (marked(ref)) {
                  el.style.setProperty('display', 'none', 'important');
                  el.style.setProperty('visibility', 'hidden', 'important');
                  hidden++;
                }
              }
              return hidden;
            })();
        """.trimIndent()
    }

    /** Marks a link or a picture from the context menu and hides it at once. */
    @JvmStatic
    fun markAndHide(context: Context, url: String?): Boolean {
        val added = add(context, url)
        if (!added) return false
        return true
    }

    private fun normalise(address: String?): String? {
        var text = address?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!text.contains("://")) text = "https://$text"
        val uri = try {
            text.toUri()
        } catch (e: Exception) {
            return null
        }
        val host = uri.host ?: return null
        if (host.isEmpty() || !host.contains('.')) return null
        val path = uri.path.orEmpty()
        return if (path.isEmpty() || path == "/") "https://${host.lowercase(Locale.ROOT)}"
        else "https://${host.lowercase(Locale.ROOT)}${path}"
    }

    private fun stripScheme(url: String): String =
        url.trim().lowercase(Locale.ROOT)
            .removePrefix("https://").removePrefix("http://").removePrefix("//")
            .trimEnd('/')

    private fun hasPath(url: String): Boolean = url.substringAfter('/', "").isNotEmpty()

    private fun hostOf(url: String): String = try {
        url.toUri().host?.lowercase(Locale.ROOT).orEmpty()
    } catch (e: Exception) {
        stripScheme(url).substringBefore('/').substringBefore('?')
    }
}
