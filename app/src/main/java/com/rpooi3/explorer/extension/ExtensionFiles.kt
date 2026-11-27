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
        return if (type.startsWith("text/") || type == "application/json" || type == "image/svg+xml") {
            "$type; charset=utf-8"
        } else {
            type
        }
    }

    /**
     * Answers the addresses of Explorer's extension server from the files of the extension the
     * address names; null for everything else, which the WebView then loads itself.
     */
    class Handler(private val context: Context) : WebViewAssetLoader.PathHandler {

        override fun handle(path: String): WebResourceResponse? {
            val clean = path.trimStart('/')
            if (clean.isEmpty()) return null
            val extensionId = clean.substringBefore('/')
            val relative = clean.substringAfter('/', "")
            if (extensionId.isEmpty() || relative.isEmpty()) return null
            if (extensionId.contains("..") || relative.contains("..")) return null
            val root = ExtensionStore.directoryOf(context.applicationContext, extensionId)
            if (!root.isDirectory) return null
            val file = File(root, relative)
            return try {
                if (!file.isFile) return null
                // The same rule as everywhere else: a file of the extension, and nothing above it.
                if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator)) return null
                WebResourceResponse(
                    mimeType(relative),
                    null,
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

    /** The loader a WebView of a popup or an extension page uses. */
    @JvmStatic
    fun assetLoader(context: Context): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(ExtensionScripts.DOMAIN)
            .addPathHandler("/", Handler(context.applicationContext))
            .build()

    /** An answer with no file behind it, for the requests that cannot be served. */
    @JvmStatic
    fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain; charset=utf-8",
        null,
        404,
        "Not Found",
        mapOf("Cache-Control" to "no-cache"),
        ByteArrayInputStream(ByteArray(0)),
    )
}
