/*
 * Foss Explorer: Settings > Accessibility, zoom.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.accessibility

import androidx.core.content.edit
import android.content.Context
import android.content.SharedPreferences
import com.rpooi3.explorer.preferences.PageZoomPreference
import com.rpooi3.explorer.site.SiteSettings
import kotlin.math.roundToInt

/**
 * The zoom of pages: one default for all sites, and a zoom of its own for
 * every site the user has set one for (the "Zoom" entry of the main menu).
 * The engine applies it as the size of text.
 */
object PageZoom {
    const val KEY_DEFAULT = "page_zoom_default_zoom_percent"
    const val KEY_OS_ADJUSTMENT = "page_zoom_include_os_adjustment"
    const val KEY_ALWAYS_SHOW = "page_zoom_always_show"
    const val KEY_FORCE_ENABLE = "force_enable_zoom"

    /** The font size setting of versions before 13.2.2 ("100", "125" …). */
    private const val KEY_LEGACY = "sp_fontSize"
    private const val SITE_PREFIX = "page_zoom_site:"

    @JvmStatic
    fun defaultZoom(prefs: SharedPreferences): Int {
        if (prefs.contains(KEY_DEFAULT)) return prefs.getInt(KEY_DEFAULT, 100).coerceIn(PageZoomPreference.MIN, PageZoomPreference.MAX)
        val legacy = try {
            prefs.getString(KEY_LEGACY, null)?.toIntOrNull()
        } catch (e: ClassCastException) {
            null
        }
        return (legacy ?: 100).coerceIn(PageZoomPreference.MIN, PageZoomPreference.MAX)
    }

    @JvmStatic
    fun setDefaultZoom(prefs: SharedPreferences, zoom: Int) {
        prefs.edit { putInt(KEY_DEFAULT, zoom.coerceIn(PageZoomPreference.MIN, PageZoomPreference.MAX)) }
    }

    /** The zoom saved for the site of [url], or null if it uses the default. */
    @JvmStatic
    fun siteZoom(prefs: SharedPreferences, url: String?): Int? {
        val host = SiteSettings.site(url)
        if (host.isEmpty() || !prefs.contains(SITE_PREFIX + host)) return null
        return prefs.getInt(SITE_PREFIX + host, 100)
    }

    /** Saves [zoom] for the site of [url]; null, or the default zoom, removes the entry. */
    @JvmStatic
    fun setSiteZoom(prefs: SharedPreferences, url: String?, zoom: Int?) {
        val host = SiteSettings.site(url)
        if (host.isEmpty()) return
        val standard = defaultZoom(prefs)
        prefs.edit {
            if (zoom == null || zoom == standard) remove(SITE_PREFIX + host)
            else putInt(SITE_PREFIX + host, zoom.coerceIn(PageZoomPreference.MIN, PageZoomPreference.MAX))
        }
    }

    /** All sites with a zoom of their own, by name. */
    @JvmStatic
    fun siteZooms(prefs: SharedPreferences): Map<String, Int> =
        prefs.all.filterKeys { it.startsWith(SITE_PREFIX) }
            .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(SITE_PREFIX) to it } }
            .sortedBy { it.first }
            .toMap()

    @JvmStatic
    fun clearSiteZooms(prefs: SharedPreferences) {
        val sites = prefs.all.keys.filter { it.startsWith(SITE_PREFIX) }
        prefs.edit { sites.forEach { remove(it) } }
    }

    /** The zoom that applies to [url], in percent. */
    @JvmStatic
    fun zoomFor(prefs: SharedPreferences, url: String?): Int = siteZoom(prefs, url) ?: defaultZoom(prefs)

    /**
     * What the engine is given for [zoom]: with "Match Android font size" the
     * font size of the system settings is multiplied in.
     */
    @JvmStatic
    fun textZoom(context: Context, prefs: SharedPreferences, zoom: Int): Int {
        val scale = if (prefs.getBoolean(KEY_OS_ADJUSTMENT, false)) context.resources.configuration.fontScale else 1f
        return (zoom * scale).roundToInt().coerceIn(25, 500)
    }

    /**
     * Run in a page, lets the user zoom with two fingers although the page
     * forbids it ("Force enable zoom").
     */
    const val FORCE_ENABLE_ZOOM_SCRIPT =
        "(function(){var m=document.querySelector('meta[name=\"viewport\"]');if(!m)return;" +
            "var c=(m.getAttribute('content')||'').split(',').map(function(p){return p.trim();})" +
            ".filter(function(p){return p&&!/^(user-scalable|maximum-scale|minimum-scale)\\s*=/i.test(p);});" +
            "c.push('user-scalable=yes');c.push('maximum-scale=10');" +
            "var n=c.join(', ');if(m.getAttribute('content')!==n)m.setAttribute('content',n);})();"
}
