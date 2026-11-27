/*
 * Explorer: what connects an extension's parts.
 * Own code, GPL-3.0.
 *
 * In Chrome an extension is not one document: a content script runs in the page, the popup runs in
 * its bubble, and a background service worker runs on its own, and the three talk through
 * chrome.runtime.sendMessage / chrome.tabs.sendMessage. Explorer has the same three, each in a
 * WebView, and this object is the telephone exchange between them:
 *
 *  - the **content scripts** of a tab (injected by [ExtensionRuntime], in the tab's own WebView),
 *  - the **popup**, open or not ([ExtensionPopup], a bubble at the top of the screen),
 *  - the **background page**, a hidden WebView that starts with the first page an extension runs
 *    on ([startBackground]) and stays until the browser is destroyed.
 *
 * A message is delivered by running `__explorerExtDispatch` in a receiving WebView; the value that
 * function returns comes back to Java through `WebView.evaluateJavascript`, the first answer wins,
 * and it is handed to the promise of the sender through `__explorerExtReply`. That is the shape
 * Chrome gives the call, with the message crossing a thread boundary on the way.
 */
package com.rpooi3.explorer.extension

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.rpooi3.explorer.view.NinjaWebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference

object ExtensionBus {

    private const val TAG = "ExplorerExtensions"

    /** The id of the tab in front; the only tab an extension is told about. */
    const val ACTIVE_TAB_ID = 1L

    private val handler = Handler(Looper.getMainLooper())

    /** The tab in front, set by the browser whenever an extension needs to know it. */
    @Volatile
    private var activeTab: WeakReference<NinjaWebView>? = null

    /** The popup that is open, if any, and the extension it belongs to. */
    private var popupView: WebView? = null

    /** The hidden background WebView of each extension that has one. */
    private val backgrounds = HashMap<String, WebView>()

    /** Where a background WebView can be put, so that its timers run; the browser's window. */
    private var host: WeakReference<ViewGroup>? = null

    @Volatile
    private var appContext: Context? = null

    /** Called by the browser when it starts, with its window; the background pages live there. */
    @JvmStatic
    fun attach(context: Context, hostView: ViewGroup? = null) {
        val app = context.applicationContext
        appContext = app
        host = if (hostView == null) null else WeakReference(hostView)
        // 17.4.0: the background page of every extension that brings one starts with the browser,
        // not only with the first page a content script runs on. A popup can be opened before any
        // page has been loaded, and its messages have to find the listeners of the background.
        try {
            for (extension in ExtensionStore.enabled(app)) {
                if (!extension.backgroundPath.isNullOrBlank()) startBackground(app, extension)
            }
        } catch (e: Exception) {
            Log.i(TAG, "No background pages to start: " + e)
        }
    }

    /** Runs [block] on the next turn, once the view that was just made has had a moment. */
    private fun later(delay: Long, block: () -> Unit) {
        handler.postDelayed(block, delay)
    }

    // The tab in front ------------------------------------------------------------------------

    @JvmStatic
    fun setActiveTab(tab: NinjaWebView?) {
        activeTab = if (tab == null) null else WeakReference(tab)
    }

    @JvmStatic
    fun activeTab(): NinjaWebView? = activeTab?.get()?.takeUnless { it.isDestroyed }

    /** The tab in front as `chrome.tabs.query` writes a tab; "null" if there is none. */
    @JvmStatic
    fun currentTabJson(): String = activeTab()?.let { tabJson(it).toString() } ?: "null"

    /** The tabs an extension may know of: the one in front. */
    @JvmStatic
    fun tabsJson(): String {
        val tab = activeTab() ?: return "[]"
        return JSONArray().put(tabJson(tab)).toString()
    }

    private fun tabJson(tab: NinjaWebView): JSONObject = JSONObject().apply {
        put("id", ACTIVE_TAB_ID)
        put("index", 0)
        put("windowId", 1)
        put("active", true)
        put("highlighted", true)
        put("selected", true)
        put("pinned", false)
        put("incognito", tab.isPrivateTab)
        put("url", tab.url ?: "")
        put("title", tab.title ?: "")
        put("status", "complete")
        put("favIconUrl", "")
    }

    // The popup -------------------------------------------------------------------------------

    /** Tells the bus which popup is open, so that a message can reach it. */
    @JvmStatic
    fun registerPopup(extensionId: String, view: WebView?) {
        popupView = if (view == null) null else view
    }

    @JvmStatic
    fun unregisterPopup(view: WebView?) {
        if (view != null && view === popupView) popupView = null
    }

    // The background page ---------------------------------------------------------------------

    /**
     * Starts the background page of [extension] if its manifest asks for one and it is not running
     * yet. A WebView cannot register a service worker, so the same file is loaded as a page of its
     * own instead, with the same chrome.* interfaces the popup gets — which is what an extension's
     * background code needs, whether the manifest calls it a service worker or a page.
     */
    @JvmStatic
    fun startBackground(context: Context, extension: WebExtension) {
        if (extension.backgroundPath.isNullOrBlank()) return
        if (!ExtensionStore.isEnabled(context, extension.id)) return
        val app = context.applicationContext
        onMain {
            if (backgrounds.containsKey(extension.id)) return@onMain
            val view = try {
                backgroundWebView(app)
            } catch (e: Throwable) {
                Log.w(TAG, "No background page for " + extension.id, e)
                return@onMain
            }
            backgrounds[extension.id] = view
            host?.get()?.let { parent ->
                try {
                    parent.addView(
                        view,
                        ViewGroup.LayoutParams(dp(app, 1), dp(app, 1)),
                    )
                    view.visibility = View.INVISIBLE
                } catch (e: Throwable) {
                    Log.i(TAG, "The background page is not attached: " + e)
                }
            }
            view.loadUrl(ExtensionScripts.backgroundUrl(extension.id))
            Log.i(TAG, "Background of " + extension.id + " started: " + extension.backgroundPath)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun backgroundWebView(context: Context): WebView {
        val view = WebView(context)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            userAgentString = WebSettings.getDefaultUserAgent(context)
        }
        view.setBackgroundColor(0)
        val loader = ExtensionFiles.assetLoader(context)
        val bridge = ChromeApiBridge(context, self = { view })
        view.addJavascriptInterface(bridge, "ExplorerChrome")
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                webView: WebView,
                request: WebResourceRequest,
            ) = loader.shouldInterceptRequest(request.url)

            override fun onPageStarted(webView: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(webView, url, favicon)
                handShim(webView, bridge, ExtensionScripts.extensionIdOf(url))
            }

            override fun onPageFinished(webView: WebView, url: String?) {
                super.onPageFinished(webView, url)
                handShim(webView, bridge, ExtensionScripts.extensionIdOf(url))
            }
        }
        return view
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    // Messages --------------------------------------------------------------------------------

    /**
     * A message from [from] (a popup, a background page or a tab) to the rest of the extension:
     * the other documents and the content scripts of the tab in front.
     */
    @JvmStatic
    fun sendMessage(extensionId: String, from: WebView?, requestId: String, messageJson: String) {
        onMain {
            // 17.4.0: a background page that is not running yet is started now, and the message
            // waits for it — Chrome wakes an extension's worker for the same reason. Without this
            // the first message of a popup went to a document that did not exist.
            val context = appContext
            if (context != null && !backgrounds.containsKey(extensionId)) {
                val extension = try {
                    ExtensionStore.find(context, extensionId)
                } catch (e: Exception) {
                    null
                }
                if (extension != null && !extension.backgroundPath.isNullOrBlank()) {
                    startBackground(context, extension)
                    later(900) { dispatch(extensionId, from, requestId, messageJson) }
                    return@onMain
                }
            }
            dispatch(extensionId, from, requestId, messageJson)
        }
    }

    /** Hands [messageJson] to the other documents of [extensionId] and the tab in front. */
    private fun dispatch(extensionId: String, from: WebView?, requestId: String, messageJson: String) {
        val targets = ArrayList<WebView>()
        popupView?.takeIf { it !== from }?.let { targets.add(it) }
        backgrounds[extensionId]?.takeIf { it !== from }?.let { targets.add(it) }
        activeTab()?.takeIf { it !== from }?.let { targets.add(it) }
        deliver(extensionId, from, requestId, messageJson, targets)
    }

    /** A message to the content scripts of the tab in front, as `chrome.tabs.sendMessage` means it. */
    @JvmStatic
    fun sendToTab(extensionId: String, requestId: String, messageJson: String, from: WebView? = null) {
        onMain {
            val tab = activeTab()
            val targets = if (tab == null) emptyList() else listOf<WebView>(tab)
            deliver(extensionId, from, requestId, messageJson, targets)
        }
    }

    /** Runs the listeners of [extensionId] in every target and hands the first answer back. */
    private fun deliver(
        extensionId: String,
        sender: WebView?,
        requestId: String,
        messageJson: String,
        targets: List<WebView>,
    ) {
        if (requestId.isEmpty()) return
        if (targets.isEmpty()) {
            reply(sender, requestId, null)
            return
        }
        var left = targets.size
        var answer: String? = null
        for (target in targets) {
            run(
                target,
                "(function(){try{if(window.__explorerExtDispatch){return window.__explorerExtDispatch(" +
                    JSONObject.quote(extensionId) + ", " + JSONObject.quote(messageJson) + ");}}" +
                    "catch(e){}return null;})()",
            ) { value ->
                if (answer == null && value != null && value != "null") answer = value
                left--
                if (left == 0) reply(sender, requestId, answer)
            }
        }
    }

    /** Hands [json] — or nothing — to the promise that waits in the sender's document. */
    private fun reply(sender: WebView?, requestId: String, json: String?) {
        if (sender == null) return
        run(
            sender,
            "(function(){try{if(window.__explorerExtReply){window.__explorerExtReply(" +
                JSONObject.quote(requestId) + ", " + (json ?: "null") + ");}}catch(e){}})()",
            null,
        )
    }

    // Scripts in the tab ----------------------------------------------------------------------

    /**
     * `chrome.scripting.executeScript`: runs the code of the extension — a function with its
     * arguments, or files of the extension — in the tab in front, and answers with what Chrome
     * answers, a list of `{result}` or `{error}`.
     */
    @JvmStatic
    fun executeScript(
        extensionId: String,
        requestId: String,
        payloadJson: String,
        sender: WebView? = null,
    ) {
        val context = appContext
        onMain {
            val payload = try {
                JSONObject(payloadJson)
            } catch (e: Exception) {
                JSONObject()
            }
            val code = payload.optString("code").takeIf { it.isNotEmpty() && it != "null" }
            val args = payload.optJSONArray("args")?.toString() ?: "[]"
            val files = payload.optJSONArray("files")
            val source = when {
                code != null -> "(" + code + ").apply(null, " + args + ")"
                files != null && files.length() > 0 ->
                    sourcesOf(context?.let { ExtensionStore.find(it, extensionId) }, files)
                else -> "undefined"
            }
            val tab = activeTab()
            if (tab == null || source == null) {
                reply(sender, requestId, null)
                return@onMain
            }
            val script = "(function(){try{var r=(function(){" + source + "})();" +
                "return [{result: (r === undefined ? null : r)}];}" +
                "catch(e){return [{error: String(e)}];}})()"
            run(tab, script) { result -> reply(sender, requestId, result) }
        }
    }

    private fun sourcesOf(extension: WebExtension?, files: JSONArray): String? {
        if (extension == null) return null
        val parts = ArrayList<String>()
        for (index in 0 until files.length()) {
            val name = files.optString(index)
            if (name.isBlank()) continue
            val file = File(extension.root, name)
            val text = try {
                if (!file.isFile) return null
                if (!file.canonicalPath.startsWith(extension.root.canonicalPath + File.separator)) return null
                if (file.length() > 1L * 1024 * 1024) return null
                file.readText()
            } catch (e: Exception) {
                null
            } ?: return null
            parts.add(text)
        }
        return if (parts.isEmpty()) null else parts.joinToString("\n")
    }

    /** `chrome.scripting.insertCSS`: a style element in the tab in front. */
    @JvmStatic
    fun insertCss(extensionId: String, requestId: String, payloadJson: String, sender: WebView? = null) {
        val context = appContext
        onMain {
            val tab = activeTab() ?: return@onMain
            val payload = try {
                JSONObject(payloadJson)
            } catch (e: Exception) {
                JSONObject()
            }
            val css = payload.optString("css").takeIf { it.isNotEmpty() }
            val file = payload.optString("file").takeIf { it.isNotEmpty() && it != "null" }
            val text = css ?: file?.let { name ->
                val extension = context?.let { ExtensionStore.find(it, extensionId) }
                val target = extension?.let { File(it.root, name) }
                try {
                    if (target != null && target.isFile) target.readText() else null
                } catch (e: Exception) {
                    null
                }
            }
            if (text == null) {
                reply(sender, requestId, "[]")
                return@onMain
            }
            run(tab, ExtensionScripts.styleScript(text)) { reply(sender, requestId, "[]") }
        }
    }

    @JvmStatic
    fun reloadActiveTab() {
        onMain { activeTab()?.reload() }
    }

    // Plumbing --------------------------------------------------------------------------------

    /** Runs [script] in [view] and hands its value to [then]. */
    private fun run(view: WebView, script: String, then: ((String?) -> Unit)?) {
        try {
            view.evaluateJavascript(script) { value -> then?.invoke(value) }
        } catch (e: Throwable) {
            Log.w(TAG, "Not run in the WebView: " + e)
            then?.invoke(null)
        }
    }

    /** The shim of [extensionId] in [webView], before the page's own scripts where possible. */
    private fun handShim(webView: WebView, bridge: ChromeApiBridge, extensionId: String?) {
        if (extensionId.isNullOrBlank()) return
        val bundle = ExtensionScripts.shimBundle(extensionId, bridge.pageToken())
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(webView, bundle, setOf("*"))
            } else {
                webView.evaluateJavascript(bundle, null)
            }
        } catch (e: Throwable) {
            Log.i(TAG, "Shim not handed over: " + e)
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    /** Stops the background pages; called when the browser goes away. */
    @JvmStatic
    fun destroy() {
        onMain {
            for (view in backgrounds.values) {
                try {
                    view.stopLoading()
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.loadUrl("about:blank")
                    view.destroy()
                } catch (e: Throwable) {
                    Log.w(TAG, "A background page did not stop: " + e)
                }
            }
            backgrounds.clear()
            popupView = null
            activeTab = null
        }
    }
}
