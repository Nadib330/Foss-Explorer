/*
 * Explorer: the other side of the chrome.* shim.
 * Own code, GPL-3.0.
 *
 * Every tab has one of these under the name `ExplorerChrome`. The shim that runs before an
 * extension's own code calls it for what JavaScript cannot reach: the storage of the extension,
 * its manifest, and the few things an extension may ask the browser for.
 *
 * A website shares the page with the content scripts, so it can see `window.ExplorerChrome` as
 * well. Every call therefore carries the token of the page load, which only the injected scripts
 * know: it is handed to them as an argument of their own function and never stored where the
 * page could read it. A call without it is refused.
 */
package com.rpooi3.explorer.extension

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.util.UUID

class ChromeApiBridge(
    private val context: Context,
    /** Where "open in a new tab" goes, if the browser that holds the tab can open one. */
    private val openTab: ((String) -> Unit)? = null,
    /** What the store's "Add to Explorer" button does: download and install that extension. */
    private val install: ((String) -> Unit)? = null,
) {

    /** The token of the page in the tab; a new one for every load. */
    @Volatile
    private var token: String = UUID.randomUUID().toString()

    /** A new token, for the page that is about to be loaded. */
    fun newPageToken(): String {
        token = UUID.randomUUID().toString()
        return token
    }

    /** The token of the current page, for the scripts of extensions on it. */
    fun pageToken(): String = token

    private fun allowed(given: String?): Boolean = given != null && given == token

    // Storage -------------------------------------------------------------------------------

    @JavascriptInterface
    fun storageGet(token: String?, extensionId: String?): String =
        if (!allowed(token)) "{}" else storage(extensionId, local = true)

    @JavascriptInterface
    fun storageSet(token: String?, extensionId: String?, json: String?) {
        if (!allowed(token)) return
        storage(extensionId, local = true, write = json)
    }

    @JavascriptInterface
    fun sessionGet(token: String?, extensionId: String?): String =
        if (!allowed(token)) "{}" else storage(extensionId, local = false)

    @JavascriptInterface
    fun sessionSet(token: String?, extensionId: String?, json: String?) {
        if (!allowed(token)) return
        storage(extensionId, local = false, write = json)
    }

    private fun storage(extensionId: String?, local: Boolean, write: String? = null): String {
        val id = extensionId?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return "{}"
        val prefs = context.getSharedPreferences(
            if (local) "extension_storage" else "extension_session",
            Context.MODE_PRIVATE,
        )
        val key = "ext_" + id
        synchronized(prefs) {
            if (write != null) {
                // Only what parses as an object is kept, so a broken extension cannot fill the file.
                try {
                    JSONObject(write)
                    prefs.edit().putString(key, write).apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Not an object: " + write.take(80))
                }
                return write
            }
            return prefs.getString(key, "{}") ?: "{}"
        }
    }

    // The rest ------------------------------------------------------------------------------

    @JavascriptInterface
    fun getManifest(token: String?, extensionId: String?): String {
        if (!allowed(token)) return "{}"
        return ExtensionStore.find(context, extensionId ?: "")?.toJson()?.toString() ?: "{}"
    }

    @JavascriptInterface
    fun sendMessage(token: String?, extensionId: String?, message: String?): String {
        if (!allowed(token)) return "null"
        // No background worker runs for an extension, so a message finds no listener but the
        // page's own; the shim hands it to them.
        Log.d(TAG, "Message from " + extensionId + ": " + (message ?: "").take(200))
        return "null"
    }

    @JavascriptInterface
    fun openTab(token: String?, url: String?) {
        if (!allowed(token)) return
        val address = url?.trim().orEmpty()
        if (address.isEmpty() || address.startsWith("javascript:")) return
        openTab?.invoke(address)
    }

    /** The button of [StoreHook] on a page of the Chrome Web Store calls this. */
    @JavascriptInterface
    fun installFromStore(token: String?, extensionId: String?) {
        if (!allowed(token)) return
        val id = CrxDownloader.extractExtensionId(extensionId) ?: return
        install?.invoke(id)
    }

    @JavascriptInterface
    fun openOptions(token: String?, extensionId: String?) {
        if (!allowed(token)) return
        val intent = Intent(context, com.rpooi3.explorer.activity.Settings_Activity::class.java)
            .putExtra(com.rpooi3.explorer.activity.Settings_Activity.EXTRA_SECTION, "section_extensions")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "No settings screen: " + e)
        }
    }

    private companion object {
        const val TAG = "ExplorerExtensions"
    }
}
