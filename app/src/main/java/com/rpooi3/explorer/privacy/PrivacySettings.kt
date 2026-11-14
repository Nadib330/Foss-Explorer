/*
 * Foss Explorer: Settings > Privacy and security, the part the engine reads.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.privacy

import android.content.SharedPreferences
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Safe Browsing and "Preload pages": what is chosen, and how a tab is set up for it. */
object PrivacySettings {

    /** "standard" or "off". */
    const val KEY_SAFE_BROWSING = "safe_browsing"
    const val SAFE_BROWSING_STANDARD = "standard"
    const val SAFE_BROWSING_OFF = "off"

    /** "none" or "standard". */
    const val KEY_PRELOAD = "preload_pages"
    const val PRELOAD_NONE = "none"
    const val PRELOAD_STANDARD = "standard"

    @JvmStatic
    fun safeBrowsing(prefs: SharedPreferences): String =
        if (prefs.getString(KEY_SAFE_BROWSING, SAFE_BROWSING_STANDARD) == SAFE_BROWSING_OFF) SAFE_BROWSING_OFF
        else SAFE_BROWSING_STANDARD

    @JvmStatic
    fun isSafeBrowsingOn(prefs: SharedPreferences): Boolean = safeBrowsing(prefs) == SAFE_BROWSING_STANDARD

    /** Pages were not preloaded before this setting existed, so that is what an update keeps. */
    @JvmStatic
    fun preload(prefs: SharedPreferences): String =
        if (prefs.getString(KEY_PRELOAD, PRELOAD_NONE) == PRELOAD_STANDARD) PRELOAD_STANDARD else PRELOAD_NONE

    /** Whether the installed Android System WebView can preload pages a site names. */
    @JvmStatic
    fun isPreloadSupported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.SPECULATIVE_LOADING)
    } catch (e: Throwable) {
        false
    }

    /** Sets up the tab that [settings] belongs to. */
    @JvmStatic
    @androidx.annotation.OptIn(markerClass = [WebSettingsCompat.ExperimentalSpeculativeLoading::class])
    fun apply(prefs: SharedPreferences, settings: WebSettings) {
        settings.safeBrowsingEnabled = isSafeBrowsingOn(prefs)
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.SPECULATIVE_LOADING)) return
            WebSettingsCompat.setSpeculativeLoadingStatus(
                settings,
                if (preload(prefs) == PRELOAD_STANDARD) WebSettingsCompat.SPECULATIVE_LOADING_PRERENDER_ENABLED
                else WebSettingsCompat.SPECULATIVE_LOADING_DISABLED,
            )
        } catch (e: RuntimeException) {
            // An older WebView that reports the feature and then refuses it: pages are not preloaded.
        }
    }
}
