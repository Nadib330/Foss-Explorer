/*
 * Foss Explorer: tracking protection as the shield of the top bar shows it.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.privacy

import android.content.Context
import androidx.core.net.toUri
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.rpooi3.explorer.site.SiteCategory
import com.rpooi3.explorer.site.SiteSettings
import com.rpooi3.explorer.view.NinjaWebView
import java.util.WeakHashMap

/**
 * What Firefox calls Enhanced Tracking Protection is, in Explorer, the
 * blocker of ads and trackers with its filter list (site setting "Ads").
 * This keeps count of what it blocked on the page in each tab, and switches
 * it on and off for a site.
 */
object TrackingProtection {
    private val blocked = WeakHashMap<NinjaWebView, LinkedHashMap<String, Int>>()

    /** A page starts to load in [tab]: the count begins again. */
    @JvmStatic
    fun onPageStarted(tab: NinjaWebView) {
        synchronized(blocked) { blocked.remove(tab) }
    }

    /** The blocker stopped a request to [url] in [tab]. Called from the engine's own threads. */
    @JvmStatic
    fun onBlocked(tab: NinjaWebView, url: String?) {
        val host = try {
            url.orEmpty().toUri().host?.lowercase()
        } catch (e: Exception) {
            null
        } ?: return
        synchronized(blocked) {
            val hosts = blocked.getOrPut(tab) { LinkedHashMap() }
            hosts[host] = (hosts[host] ?: 0) + 1
        }
    }

    /** The hosts that were blocked on the page in [tab], each with the number of requests, most first. */
    @JvmStatic
    fun blockedHosts(tab: NinjaWebView): List<Pair<String, Int>> =
        synchronized(blocked) { blocked[tab]?.toList().orEmpty() }.sortedWith(compareBy({ -it.second }, { it.first }))

    /** How many trackers (hosts) were blocked on the page in [tab]. */
    @JvmStatic
    fun blockedCount(tab: NinjaWebView): Int = synchronized(blocked) { blocked[tab]?.size ?: 0 }

    /** Whether the shield has something to say about [url]: only websites are protected. */
    @JvmStatic
    fun appliesTo(url: String?): Boolean = url != null && (url.startsWith("http://") || url.startsWith("https://"))

    @JvmStatic
    fun isSecure(url: String?): Boolean = url?.startsWith("https://") == true

    /** Whether trackers are blocked on the site of [url]. */
    @JvmStatic
    fun isProtected(context: Context, url: String?): Boolean = !SiteSettings.isAllowed(context, SiteCategory.ADS, url)

    /**
     * Switches the protection on or off for the site of [url]: an exception
     * to the general choice, or none if the general choice says the same.
     */
    @JvmStatic
    fun setProtected(context: Context, url: String?, on: Boolean) {
        val prefs = SiteSettings.prefs(context)
        // "Ads" on means ads are allowed, which is protection off.
        val generally = !SiteSettings.isOn(prefs, SiteCategory.ADS, SiteSettings.profileFor(context, url))
        SiteSettings.setException(prefs, SiteCategory.ADS, url, if (on == generally) null else !on)
    }

    /**
     * Removes the cookies and the stored data of the site of [url], as far as
     * Android System WebView lets an app do that: every cookie the site can
     * read is expired, for the host and the domains above it, and the site's
     * storage is deleted.
     */
    @JvmStatic
    fun clearSiteData(url: String?) {
        val uri = try {
            url.orEmpty().toUri()
        } catch (e: Exception) {
            return
        }
        val host = uri.host ?: return
        val manager = CookieManager.getInstance()
        val names = manager.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
        val labels = host.split('.')
        val domains = (0..maxOf(0, labels.size - 2)).map { labels.drop(it).joinToString(".") }
        val gone = "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
        for (name in names) {
            manager.setCookie(url, name + gone)
            for (domain in domains) {
                manager.setCookie(url, "$name$gone; Domain=$domain")
                manager.setCookie(url, "$name$gone; Domain=.$domain")
            }
        }
        manager.flush()
        WebStorage.getInstance().deleteOrigin(uri.scheme + "://" + uri.encodedAuthority)
    }
}
