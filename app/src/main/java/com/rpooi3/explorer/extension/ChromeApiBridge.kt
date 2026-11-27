/*
 * Explorer: the other side of the chrome.* shim.
 * Own code, GPL-3.0.
 *
 * Every tab has one of these under the name `ExplorerChrome`. The shim that runs before an
 * extension's own code calls it for what JavaScript cannot reach: the storage of the extension,
 * its manifest, the tab in front, scripts that have to run in that tab, and the messages between
 * the popup, the background page and the content scripts. [ExtensionBus] does the routing; this
 * class is only the surface JavaScript can call.
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

class ChromeApiBridge @JvmOverloads constructor(
    private val context: Context,
    /** Where "open in a new tab" goes, if the browser that holds the tab can open one. */
    private val openTab: ((String) -> Unit)? = null,
    /** What the store's "Add to Explorer" button does: download and install that extension. */
    private val install: ((String) -> Unit)? = null,
    /**
     * 17.3.4: the WebView this bridge serves. A popup, a background page and a tab each have one,
     * and a message has to reach the other two kinds.
     */
    private val self: (() -> android.webkit.WebView?)? = null,
    /** The popup window, when this bridge belongs to a popup: it closes itself. */
    private val onClose: (() -> Unit)? = null,
    /** A popup telling the browser how big it wants to be, in CSS pixels. */
    private val onPopupSize: ((Int, Int) -> Unit)? = null,
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
        // 18.0.0 beta 3: what an extension stores belongs to the profile it runs in.
        val prefs = com.rpooi3.explorer.profile.ProfilePrefs.get(
            context,
            if (local) "extension_storage" else "extension_session",
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

    // The extension itself ------------------------------------------------------------------

    @JavascriptInterface
    fun getManifest(token: String?, extensionId: String?): String {
        if (!allowed(token)) return "{}"
        return ExtensionStore.find(context, extensionId ?: "")?.toJson()?.toString() ?: "{}"
    }

    /** The tab in front, as `chrome.tabs.query` writes it; "null" when there is none. */
    @JavascriptInterface
    fun currentTab(token: String?, extensionId: String?): String {
        if (!allowed(token)) return "null"
        return ExtensionBus.currentTabJson()
    }

    @JavascriptInterface
    fun tabsQuery(token: String?, extensionId: String?): String {
        if (!allowed(token)) return "[]"
        return ExtensionBus.tabsJson()
    }

    @JavascriptInterface
    fun tabsGet(token: String?, extensionId: String?, tabId: String?): String {
        if (!allowed(token)) return "null"
        val wanted = tabId?.toLongOrNull() ?: return ExtensionBus.currentTabJson()
        return if (wanted == ExtensionBus.ACTIVE_TAB_ID) ExtensionBus.currentTabJson() else "null"
    }

    /** A message from this document to everything else the extension has open. */
    @JavascriptInterface
    fun sendMessage(token: String?, extensionId: String?, requestId: String?, message: String?) {
        if (!allowed(token)) return
        val id = extensionId?.takeIf { it.isNotBlank() } ?: return
        ExtensionBus.sendMessage(id, self?.invoke(), requestId.orEmpty(), message ?: "null")
    }

    /** The same, but to the content scripts of the tab in front. */
    @JavascriptInterface
    fun tabSendMessage(
        token: String?,
        extensionId: String?,
        requestId: String?,
        tabId: String?,
        message: String?,
    ) {
        if (!allowed(token)) return
        val id = extensionId?.takeIf { it.isNotBlank() } ?: return
        ExtensionBus.sendToTab(id, requestId.orEmpty(), message ?: "null", self?.invoke())
    }

    /** `chrome.scripting.executeScript` / `chrome.tabs.executeScript` in the tab in front. */
    @JavascriptInterface
    fun executeScript(token: String?, extensionId: String?, requestId: String?, payload: String?) {
        if (!allowed(token)) return
        val id = extensionId?.takeIf { it.isNotBlank() } ?: return
        ExtensionBus.executeScript(id, requestId.orEmpty(), payload ?: "{}", self?.invoke())
    }

    /** `chrome.scripting.insertCSS` in the tab in front. */
    @JavascriptInterface
    fun insertCss(token: String?, extensionId: String?, requestId: String?, payload: String?) {
        if (!allowed(token)) return
        val id = extensionId?.takeIf { it.isNotBlank() } ?: return
        ExtensionBus.insertCss(id, requestId.orEmpty(), payload ?: "{}", self?.invoke())
    }

    @JavascriptInterface
    fun reloadTab(token: String?, extensionId: String?) {
        if (!allowed(token)) return
        ExtensionBus.reloadActiveTab()
    }

    /** The popup asks to be sized like its content, as it is in Chrome. */
    @JavascriptInterface
    fun popupSize(token: String?, width: String?, height: String?) {
        if (!allowed(token)) return
        val w = width?.toIntOrNull() ?: return
        val h = height?.toIntOrNull() ?: return
        onPopupSize?.invoke(w, h)
    }

    /** `window.close()` in a popup. */
    @JavascriptInterface
    fun closePopup(token: String?) {
        if (!allowed(token)) return
        onClose?.invoke()
    }

    @JavascriptInterface
    fun openTab(token: String?, url: String?) {
        if (!allowed(token)) return
        val address = url?.trim().orEmpty()
        if (address.isEmpty() || address.startsWith("javascript:")) return
        openTab?.invoke(address)
    }

    /** The button of [StoreHook] on a page of the Chrome Web Store calls this. */
    /**
     * What the store's *Add to Explorer* button calls.
     *
     * 18.0.0 beta 3: it answers whether the install was taken. The page used to get nothing back,
     * so a refused call (another page's token, a string that is not an id, no bridge) looked
     * exactly like a tap that did nothing — the extension was never installed from the store,
     * while the same install from the three-dot menu worked. A `false` now makes the script fall
     * back to `explorer-install://<id>`, which the browser intercepts and installs through the
     * code of the three-dot menu.
     */
    @JavascriptInterface
    fun installFromStore(token: String?, extensionId: String?): Boolean {
        val id = CrxDownloader.extractExtensionId(extensionId) ?: run {
            Log.w(TAG, "Not an extension id: " + extensionId)
            return false
        }
        if (!allowed(token)) {
            Log.w(TAG, "The store page has another token; " + id + " comes in as a link")
            return false
        }
        val sink = install ?: return false
        sink(id)
        return true
    }

    /**
     * 18.2.0 beta (item 4): what `chrome.management.getAll()` answers on a page of the store.
     *
     * The store's own page asks the browser which extensions are installed before it draws its
     * button: with the extension in the list it draws **Remove from Explorer**, without it the
     * install button. The answer is a JSON array of the shape `chrome.management.ExtensionInfo`
     * has: the id, the name, the version, whether it is enabled, and that it is an extension that
     * the page may remove.
     */
    @JavascriptInterface
    fun installedExtensions(token: String?): String {
        if (!allowed(token)) return "[]"
        return try {
            val list = org.json.JSONArray()
            for (extension in ExtensionStore.installed(context)) {
                list.put(
                    JSONObject()
                        .put("id", extension.id)
                        .put("name", extension.name)
                        .put("version", extension.version)
                        .put("description", extension.description)
                        .put("enabled", ExtensionStore.isEnabled(context, extension.id))
                        .put("type", "extension")
                        .put("installType", "normal")
                        .put("mayDisable", true)
                        .put("versionName", extension.version),
                )
            }
            list.toString()
        } catch (e: Throwable) {
            Log.w(TAG, "The installed extensions: " + e)
            "[]"
        }
    }

    /**
     * 18.2.0 beta (item 4): `chrome.management.uninstall()` — the **Remove from Explorer** button
     * of the store page, which takes an extension away again.
     */
    @JavascriptInterface
    fun uninstallFromStore(token: String?, extensionId: String?): Boolean {
        val id = CrxDownloader.extractExtensionId(extensionId) ?: run {
            Log.w(TAG, "Not an extension id: " + extensionId)
            return false
        }
        if (!allowed(token)) {
            Log.w(TAG, "The store page has another token; " + id + " is not removed")
            return false
        }
        return try {
            ExtensionStore.remove(context, id)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "The extension " + id + " stays: " + e)
            false
        }
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
