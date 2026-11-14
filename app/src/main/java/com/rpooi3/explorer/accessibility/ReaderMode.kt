/*
 * Foss Explorer: "Simplified view for web pages" (Chrome's Reading mode).
 * Own code, GPL-3.0. The article is found by Mozilla's Readability
 * (Apache-2.0, third_party/readability), which the app carries as an asset.
 */
package com.rpooi3.explorer.accessibility

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.webkit.WebView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Finds out whether a page is an article and, if the user wants it, shows
 * the article alone: title, author, text and pictures, without the rest of
 * the page.
 */
object ReaderMode {
    const val KEY = "reader_for_accessibility"
    const val KEY_LINKS = "reader_enable_links"

    private const val ASSET_READABILITY = "reader/Readability.js"
    private const val ASSET_READERABLE = "reader/Readability-readerable.js"

    @JvmStatic
    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY, false)

    @JvmStatic
    fun showsLinks(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_LINKS, true)

    /** Whether a page at [url] can be an article at all: a website, not one of the app's own pages. */
    @JvmStatic
    fun isCandidate(url: String?): Boolean = url != null && (url.startsWith("http://") || url.startsWith("https://"))

    private fun asset(context: Context, name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        null
    }

    /** Script that answers `true` if the page in the tab looks like an article. */
    @JvmStatic
    fun detectScript(context: Context): String? {
        val readerable = asset(context, ASSET_READERABLE) ?: return null
        return "(function(){var module=undefined;$readerable\n" +
            "try{return isProbablyReaderable(document);}catch(e){return false;}})();"
    }

    /** Script that answers the article of the page as `{title, byline, siteName, dir, content}`, or null. */
    @JvmStatic
    fun extractScript(context: Context): String? {
        val readability = asset(context, ASSET_READABILITY) ?: return null
        return "(function(){var module=undefined;$readability\n" +
            "try{var a=new Readability(document.cloneNode(true)).parse();" +
            "if(!a||!a.content)return null;" +
            "return {title:a.title||document.title||'',byline:a.byline||'',siteName:a.siteName||''," +
            "dir:a.dir||'',content:a.content};}catch(e){return null;}})();"
    }

    /**
     * The page that shows the article [json] (the answer of [extractScript]),
     * or null if there is no article in it.
     */
    @JvmStatic
    fun page(json: String?, showLinks: Boolean): String? {
        if (json.isNullOrEmpty() || json == "null") return null
        val article = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }
        val content = article.optString("content")
        if (content.isBlank()) return null
        val title = TextUtils.htmlEncode(article.optString("title"))
        val byline = listOf(article.optString("byline"), article.optString("siteName"))
            .filter { it.isNotBlank() }.joinToString(" \u00b7 ") { TextUtils.htmlEncode(it) }
        val dir = if (article.optString("dir") == "rtl") " dir=\"rtl\"" else ""
        val links = if (showLinks) "a{color:#0b57d0}@media(prefers-color-scheme:dark){a{color:#a8c7fa}}"
        else "a{color:inherit;text-decoration:none;pointer-events:none;cursor:text}"
        return "<!DOCTYPE html><html$dir><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<meta name=\"color-scheme\" content=\"light dark\">" +
            "<title>$title</title><style>" +
            ":root{color-scheme:light dark}" +
            "body{margin:0 auto;padding:24px 20px 48px;max-width:42em;font-family:sans-serif;" +
            "font-size:1.0625rem;line-height:1.6;overflow-wrap:break-word}" +
            "h1{font-size:1.6rem;line-height:1.25;margin:0 0 8px}" +
            ".byline{opacity:.7;font-size:.9rem;margin:0 0 24px}" +
            "img,video,figure,table,pre{max-width:100%;height:auto}figure{margin:16px 0}" +
            "pre{overflow:auto}blockquote{margin:16px 0;padding-left:16px;border-left:3px solid #8888}" +
            links +
            "</style></head><body><h1>$title</h1>" +
            (if (byline.isEmpty()) "" else "<p class=\"byline\">$byline</p>") +
            content +
            "</body></html>"
    }

    /**
     * Replaces the page in [web] by its article. The address stays, and Back
     * returns to the full page. [done] hears whether an article was shown.
     */
    @JvmStatic
    fun show(web: WebView, showLinks: Boolean, done: (Boolean) -> Unit) {
        val script = extractScript(web.context)
        val url = web.url
        if (script == null || !isCandidate(url)) return done(false)
        web.evaluateJavascript(script) { json ->
            val page = page(json, showLinks)
            if (page == null) {
                done(false)
            } else {
                web.loadDataWithBaseURL(url, page, "text/html", "utf-8", url)
                done(true)
            }
        }
    }
}
