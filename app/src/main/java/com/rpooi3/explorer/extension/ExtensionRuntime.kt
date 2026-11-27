/*
 * Explorer: runs the installed extensions in a tab.
 * Own code, GPL-3.0.
 *
 * Called from NinjaWebViewClient on both page callbacks. A private tab has no extensions, as in
 * Chrome's incognito windows; every other tab gets what the manifest of an extension asks for on
 * the page, through [ExtensionScripts].
 */
package com.rpooi3.explorer.extension

import android.webkit.WebView
import com.rpooi3.explorer.view.NinjaWebView

object ExtensionRuntime {

    /**
     * The page has started: what has to run before it is parsed. The first run may still land in
     * the empty document, so [onPageFinished] asks for the same scripts again; the guard of
     * [ExtensionScripts.injectionFor] keeps them from running twice in one document.
     */
    @JvmStatic
    fun onPageStarted(webView: WebView, url: String?) {
        inject(webView, url, listOf(WebExtension.RUN_AT_START))
    }

    /** The page is done: the rest of the content scripts, and the stylesheets that waited. */
    @JvmStatic
    fun onPageFinished(webView: WebView, url: String?) {
        inject(
            webView,
            url,
            listOf(WebExtension.RUN_AT_START, WebExtension.RUN_AT_END, WebExtension.RUN_AT_IDLE),
        )
    }

    private fun inject(webView: WebView, url: String?, runAts: List<String>) {
        if (url.isNullOrBlank()) return
        val ninja = webView as? NinjaWebView ?: return
        // 17.3.3: a tab that was closed while the scripts were being read is left alone.
        if (ninja.isDestroyed) return
        if (ninja.isPrivateTab) return
        if (ExtensionScripts.isExtensionUrl(url)) return
        val context = ninja.context.applicationContext ?: return
        val extensions = try {
            ExtensionStore.forUrl(context, url)
        } catch (e: Exception) {
            return
        }
        if (extensions.isEmpty()) return
        val bridge = ninja.extensionBridge ?: return
        val token = bridge.pageToken()
        for (extension in extensions) {
            for (runAt in runAts) {
                val script = try {
                    ExtensionScripts.injectionFor(context, extension, url, runAt, token)
                } catch (e: Exception) {
                    null
                } ?: continue
                webView.evaluateJavascript(script, null)
            }
        }
    }

    /**
     * What the extension menu shows for [url]: the installed extensions that are turned on,
     * and whether each of them has something for this site.
     */
    @JvmStatic
    fun forMenu(context: android.content.Context, url: String?): List<Pair<WebExtension, Boolean>> =
        ExtensionStore.enabled(context).map { it to it.worksOn(url) }
}
