package com.rpooi3.explorer.privacy

import android.util.Log
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Storage separation for private tabs.
 *
 * A private tab never writes to the history, the icon cache or the list of
 * tabs to restore; that part is done in the browser code and works on every
 * device. Cookies, site storage and the HTTP cache belong to the system
 * WebView. They can only be kept apart if the installed WebView supports
 * profiles ([WebViewFeature.MULTI_PROFILE]). Where it does, all private tabs
 * share one profile named [PROFILE], which is deleted once the last private
 * tab is closed and again at the next start. Where it does not, private tabs
 * share cookies with normal tabs, and the app says so when one is opened.
 */
object PrivateTabs {
    /** Name of the WebView profile used by private tabs. */
    const val PROFILE = "private"
    private const val TAG = "PrivateTabs"

    /** Whether this device's WebView can keep private tabs' data apart. */
    @JvmStatic
    fun isIsolationSupported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    } catch (e: Throwable) {
        Log.w(TAG, "Profile support could not be determined", e)
        false
    }

    /**
     * Moves a freshly created WebView into the private profile. Must be the
     * first call made on it. Returns whether the WebView is now separated.
     */
    @JvmStatic
    fun attach(webView: WebView): Boolean = try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(PROFILE)
            WebViewCompat.setProfile(webView, PROFILE)
            true
        } else {
            false
        }
    } catch (e: Throwable) {
        Log.w(TAG, "Private profile could not be used", e)
        false
    }

    /**
     * Removes everything the private profile has stored. Call when no private
     * tab is open any more. If the profile is still in use and cannot be
     * deleted, its cookies and site storage are cleared instead.
     */
    @JvmStatic
    fun wipe() {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
            val store = ProfileStore.getInstance()
            if (PROFILE !in store.allProfileNames) return
            try {
                store.deleteProfile(PROFILE)
            } catch (e: IllegalStateException) {
                Log.i(TAG, "Private profile still in use, clearing its data instead", e)
                val profile = store.getProfile(PROFILE)
                if (profile != null) {
                    profile.cookieManager.removeAllCookies(null)
                    profile.webStorage.deleteAllData()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Private profile could not be wiped", e)
        }
    }
}
