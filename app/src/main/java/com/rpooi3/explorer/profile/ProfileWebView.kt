/*
 * Explorer: the WebView side of a profile.
 * Own code, GPL-3.0.
 *
 * Android System WebView has had profiles of its own since WebView 123: a WebView can be bound to
 * a named profile, and then reads and writes its own cookies, its own web storage, its own cache
 * and its own service workers. That is what separates two Explorer profiles on the web side, and it
 * is the only way to do it — an app cannot clear or swap the cookie jar of the default WebView for
 * one page and put it back for the next.
 *
 * Two rules come with it:
 *
 *  - `WebViewCompat.setProfile` has to be called **before** anything else is done with the WebView:
 *    before it is loaded, before `evaluateJavascript`, before its profile is read. Explorer binds a
 *    tab, a popup and a background page the moment they are made ([bind]).
 *  - the feature needs a multi-process WebView ([WebViewFeature.MULTI_PROFILE]). Where the WebView
 *    of the device is older, the cookies stay in one jar and only Explorer's own data (history,
 *    bookmarks, permissions) is separated — [supported] says which is the case, and the settings
 *    screen of the profiles says so in words.
 */
package com.rpooi3.explorer.profile

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Collections
import java.util.WeakHashMap

object ProfileWebView {

    private const val TAG = "ExplorerProfiles"

    /** The WebViews that were already bound, so that a second bind is a no-op. */
    private val bound: MutableSet<WebView> = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<WebView, Boolean>()),
    )

    /** Whether this WebView can carry a profile of its own — the device, not the app, decides. */
    @JvmStatic
    fun supported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    } catch (e: Throwable) {
        false
    }

    /**
     * Binds [webView] to the profile in front. Call it straight after the WebView is made: after a
     * page was loaded or a script run, WebView refuses to change the profile.
     *
     * @return whether the WebView now carries the profile of its own (false on a WebView without
     *   multi-profile support, where the cookies are shared and only Explorer's data is separate)
     */
    @JvmStatic
    fun bind(context: Context, webView: WebView): Boolean {
        if (!supported()) return false
        if (bound.contains(webView)) return true
        val profile = Profiles.current(context.applicationContext)
        return try {
            WebViewCompat.setProfile(webView, profile.webViewProfileName)
            bound.add(webView)
            true
        } catch (e: Throwable) {
            // A WebView that has already been used keeps the default profile; it is logged, not
            // thrown, because a browsing session must not end over a cookie jar.
            Log.w(TAG, "No profile for this WebView: " + e)
            false
        }
    }

    /** The profile [webView] carries, or null where the WebView has none of its own. */
    @JvmStatic
    fun of(webView: WebView): Profile? {
        if (!supported()) return null
        return try {
            WebViewCompat.getProfile(webView)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * The cookie manager of the profile in front. Where the WebView has no profiles, this is the
     * one manager of the app, as before 18.0.0.
     */
    @JvmStatic
    fun cookieManager(context: Context): CookieManager {
        if (supported()) {
            try {
                val name = Profiles.current(context.applicationContext).webViewProfileName
                val profile = ProfileStore.getInstance().getOrCreateProfile(name)
                return profile.cookieManager
            } catch (e: Throwable) {
                Log.w(TAG, "No cookie manager for the profile: " + e)
            }
        }
        return CookieManager.getInstance()
    }

    /** Empties the cookies, the web storage and the cache of the profile in front. */
    @JvmStatic
    fun clear(context: Context) {
        val app = context.applicationContext
        if (supported()) {
            try {
                val store = ProfileStore.getInstance()
                val name = Profiles.current(app).webViewProfileName
                val profile = store.getOrCreateProfile(name)
                profile.cookieManager.removeAllCookies(null)
                profile.cookieManager.flush()
                profile.webStorage.deleteAllData()
                return
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot clear the profile: " + e)
            }
        }
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.flush()
    }

    /**
     * Empties the web storage of the profile in front — its databases, its IndexedDB, its local
     * storage — and nothing of any other profile (18.0.0 beta 4, issue 8).
     */
    @JvmStatic
    fun clearStorage(context: Context) {
        val app = context.applicationContext
        if (supported()) {
            try {
                val profile = ProfileStore.getInstance()
                    .getOrCreateProfile(Profiles.current(app).webViewProfileName)
                profile.webStorage.deleteAllData()
                return
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot clear the storage of the profile: " + e)
            }
        }
        android.webkit.WebStorage.getInstance().deleteAllData()
    }

    /**
     * The folder of the WebView profile in front: `app_webview/Default` where the WebView has no
     * profiles of its own. Null where the WebView keeps the data of a named profile in a place
     * Explorer does not know — the API above empties it there.
     */
    @JvmStatic
    fun storageDirectory(context: Context): java.io.File? {
        if (supported()) return null
        return java.io.File(context.applicationContext.filesDir.parentFile, "app_webview/Default")
    }

    /** Deletes the WebView profile of [profile], for a profile that is being taken away. */
    @JvmStatic
    fun delete(profile: BrowserProfile) {
        if (!supported()) return
        try {
            ProfileStore.getInstance().deleteProfile(profile.webViewProfileName)
        } catch (e: Throwable) {
            // The profile may never have been opened; nothing to delete, or still in use.
            Log.i(TAG, "No WebView profile to delete for " + profile.id + ": " + e)
        }
    }
}
