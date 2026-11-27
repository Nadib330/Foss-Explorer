/*
 * Explorer: serves the files of an extension to a WebView.
 * Own code, GPL-3.0.
 *
 * A popup is a page of the extension, and it asks for its stylesheet, its script and its
 * pictures the way any page does: relative to itself, with fetch or XHR. Those addresses belong
 * to no server, so Explorer answers them: `https://explorer-extensions.local/<id>/<path>` is
 * read from `files/extensions/<id>/`, and only from there.
 */
package com.rpooi3.explorer.extension

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

object ExtensionFiles {

    /** The type a WebView needs for [path], with the charset where it matters. */
    @JvmStatic
    fun mimeType(path: String): String {
        val name = path.substringAfterLast('/').lowercase(Locale.US)
        val type = when (name.substringAfterLast('.', "")) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json", "map" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "wasm" -> "application/wasm"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
        return type
    }

    /**
     * 17.4.1: the encoding of [path] as a separate value, which is what a WebView wants — the
     * mime type alone, and the charset beside it. Handing it `"text/html; charset=utf-8"` as the
     * type and no encoding made some WebViews treat the page as plain text and show its source
     * (`<!doctype html> …`) instead of the popup itself.
     */
    @JvmStatic
    fun encodingOf(path: String): String? =
        when (mimeType(path)) {
            "text/html", "text/css", "text/javascript", "text/plain", "application/json",
            "image/svg+xml", "application/wasm" -> "utf-8"
            else -> null
        }

    /**
     * Answers the addresses of Explorer's extension server from the files of the extension the
     * address names; null for everything else, which the WebView then loads itself.
     */
    class Handler(
        private val context: Context,
        /**
         * 18.0.0 beta 8 (item 4): the extension whose page is being served, if it is known. A
         * popup asks for its files the way a page does — `popup.js` beside it, but also
         * `/popup.js` from the root of the site, which is what a bundled page writes. The second
         * form names no extension, so it was answered with nothing and the popup stayed an empty
         * coloured rectangle; with this it is read from the extension whose page is open.
         */
        private val defaultExtension: String? = null,
    ) : WebViewAssetLoader.PathHandler {

        override fun handle(path: String): WebResourceResponse? {
            // 17.3.4: a popup asks for its files with a query string (?v=1, ?tabId=…), exactly as
            // Chrome accepts it. The query and the fragment are not part of the file name, and a
            // path arrives encoded; both used to send the request to a file that does not exist.
            var clean = android.net.Uri.decode(path).trimStart('/')
            clean = clean.substringBefore('#').substringBefore('?')
            if (clean.isEmpty()) return null
            var extensionId = clean.substringBefore('/')
            var relative = clean.substringAfter('/', "")
            // A path from the root of the site, without the name of an extension: the page that
            // asked belongs to [defaultExtension], so that is where the answer is read.
            val named = defaultExtension != null &&
                ExtensionStore.directoryOf(context.applicationContext, extensionId).isDirectory
            if (!named && defaultExtension != null) {
                relative = clean
                extensionId = defaultExtension
            }
            if (extensionId.isEmpty() || relative.isEmpty()) return null
            if (extensionId.contains("..") || relative.contains("..")) return null
            val root = ExtensionStore.directoryOf(context.applicationContext, extensionId)
            if (!root.isDirectory) return null
            if (relative == ExtensionScripts.BACKGROUND_PAGE) {
                return backgroundPage(context, extensionId)
            }
            var file = File(root, relative)
            // A folder is its own page, as on a web server: `.../options/` shows its index.html.
            if (file.isDirectory) file = File(file, "index.html")
            return try {
                if (!file.isFile) return null
                // The same rule as everywhere else: a file of the extension, and nothing above it.
                if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator)) return null
                WebResourceResponse(
                    mimeType(relative),
                    encodingOf(relative),
                    200,
                    "OK",
                    mapOf(
                        "Cache-Control" to "no-cache",
                        "Access-Control-Allow-Origin" to "*",
                    ),
                    file.inputStream(),
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * The page that runs the background file of [extensionId]: a document of Explorer's own, so
     * that the manifest's service worker or background page has somewhere to run with the shim in
     * place. An extension without one gets no page at all.
     */
    private fun backgroundPage(context: Context, extensionId: String): WebResourceResponse? {
        val extension = ExtensionStore.find(context.applicationContext, extensionId) ?: return null
        val script = extension.backgroundPath?.takeIf { it.isNotBlank() } ?: return null
        val safe = script.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
        val html = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<script src=\"" + safe + "\"></script></head><body></body></html>"
        return WebResourceResponse(
            // The type alone and the charset beside it, as for every other page served here.
            "text/html",
            "utf-8",
            200,
            "OK",
            mapOf("Cache-Control" to "no-cache"),
            ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)),
        )
    }

    /** The loader a WebView of a popup or an extension page uses. */
    @JvmStatic
    fun assetLoader(context: Context): WebViewAssetLoader = assetLoader(context, null)

    /**
     * The loader for a WebView that shows the pages of [extensionId]: its own files are found
     * whether the page names them beside itself or from the root, which is how a bundled popup
     * asks for them (18.0.0 beta 8, item 4).
     */
    @JvmStatic
    fun assetLoader(context: Context, extensionId: String?): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(ExtensionScripts.DOMAIN)
            .addPathHandler("/", Handler(context.applicationContext, extensionId))
            .build()

    /** An answer with no file behind it, for the requests that cannot be served. */
    @JvmStatic
    fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        mapOf("Cache-Control" to "no-cache"),
        ByteArrayInputStream(ByteArray(0)),
    )
}
