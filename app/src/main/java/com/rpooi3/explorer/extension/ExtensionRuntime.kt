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

    /**
     * 17.3.4: a page of an extension itself — its options page, a page a popup linked to — opened
     * in a tab. No content script runs there; the chrome.* interfaces of the extension do, so the
     * page can reach its own storage and the tab behind it.
     */
    @JvmStatic
    fun onExtensionPage(webView: WebView, url: String?) {
        val ninja = webView as? NinjaWebView ?: return
        if (ninja.isDestroyed) return
        val extensionId = ExtensionScripts.extensionIdOf(url) ?: return
        val bridge = ninja.extensionBridge ?: return
        webView.evaluateJavascript(
            ExtensionScripts.shimBundle(extensionId, bridge.pageToken()),
            null,
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
        // 18.0.0 beta 8 (item 5): the background of an extension is started with the first page
        // that is loaded after the browser starts, whether the extension names that page in its
        // `content_scripts` or not. Until now it waited for a page the extension's content
        // scripts matched, so an extension that works through its background alone — the kind
        // that asks for `scripting` and does its work from there — never started on most sites.
        for (extension in ExtensionStore.enabled(context)) {
            if (extension.backgroundPath.isNullOrBlank()) continue
            try {
                ExtensionBus.startBackground(context, extension)
            } catch (e: Exception) {
                // A background page that cannot start must not stop the rest.
            }
        }
        if (extensions.isEmpty()) return
        val bridge = ninja.extensionBridge ?: return
        val token = bridge.pageToken()
        for (extension in extensions) {
            // 17.3.4: the background page of an extension starts with the first page it runs on,
            // so that a message from a popup or a content script finds its listeners there.
            if (!extension.backgroundPath.isNullOrBlank()) {
                try {
                    ExtensionBus.startBackground(context, extension)
                } catch (e: Exception) {
                    // A background page that cannot start must not stop the content scripts.
                }
            }
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
