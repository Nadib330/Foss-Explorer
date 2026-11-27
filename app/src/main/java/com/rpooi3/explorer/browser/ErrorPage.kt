/*
 * Explorer: the page a tab shows when a website could not be loaded.
 * Own code, GPL-3.0.
 *
 * Until 18.0.0 beta 4 every error that was not "there is no internet" ended on the error page of
 * Android System WebView — Chromium's own page, which speaks a different language than Explorer and
 * wears no colour of the phone it is on. This object draws Explorer's own page instead
 * (`assets/error/error.html`): a picture, the heading, what happened to the address, and a way to
 * search for the site instead. It follows the system's light and dark mode and writes itself out in
 * the language of the app.
 *
 * The picture and the wording follow the shape Chrome's error page has, drawn and worded by hand:
 * no code and no asset of Chromium or Firefox is used here.
 */
package com.rpooi3.explorer.browser

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.view.View
import com.rpooi3.explorer.R
import com.rpooi3.explorer.search.SearchEngines

object ErrorPage {

    private const val ASSET = "error/error.html"
    private var template: String? = null

    /**
     * The errors this page knows by name; anything else gets the sentence every one of them
     * understands. The keys are the names the WebView reports, e.g. `ERR_NAME_NOT_RESOLVED`.
     */
    private fun summaryFor(context: Context, code: String, host: String): String = when {
        code.contains("ERR_NAME_NOT_RESOLVED") -> context.getString(R.string.error_page_check_typo, host)
        code.contains("ERR_CONNECTION_REFUSED") -> context.getString(R.string.error_page_refused, host)
        code.contains("ERR_CONNECTION_TIMED_OUT") || code.contains("ERR_TIMED_OUT") ->
            context.getString(R.string.error_page_timeout, host)
        code.contains("ERR_CONNECTION_RESET") || code.contains("ERR_CONNECTION_CLOSED") ->
            context.getString(R.string.error_page_reset, host)
        code.contains("ERR_EMPTY_RESPONSE") -> context.getString(R.string.error_page_empty, host)
        code.contains("ERR_ADDRESS_UNREACHABLE") || code.contains("ERR_INTERNET_DISCONNECTED") ->
            context.getString(R.string.error_page_unreachable, host)
        code.contains("ERR_CERT") || code.contains("ERR_SSL") -> context.getString(R.string.error_page_certificate, host)
        code.contains("ERR_TOO_MANY_REDIRECTS") -> context.getString(R.string.error_page_redirects, host)
        code.contains("ERR_FILE_NOT_FOUND") -> context.getString(R.string.error_page_file_missing)
        else -> context.getString(R.string.error_page_summary, host)
    }

    /** The sentence under "This site can't be reached", the address it was about, and the rest. */
    @JvmStatic
    fun html(context: Context, failedUrl: String?, description: CharSequence?): String {
        val url = failedUrl.orEmpty()
        val host = hostOf(url)
        val code = errorCodeOf(description)
        val locale = context.resources.configuration.locales[0]
        val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
        val searchable = host.isNotEmpty() && host != "explorer-error"
        val search = if (searchable) {
            "<a class=\"pill\" href=\"" + escape(searchUrl(context, host)) + "\">" +
                MAGNIFIER +
                "<span>" + escape(context.getString(R.string.error_page_search, host)) + "</span></a>"
        } else {
            ""
        }
        return template(context)
            .replace("{{dir}}", if (rtl) "rtl" else "ltr")
            .replace("{{lang}}", escape(locale.toLanguageTag()))
            .replace("{{title}}", escape(host))
            .replace("{{picture_alt}}", escape(context.getString(R.string.error_page_picture)))
            .replace("{{heading}}", escape(context.getString(R.string.error_page_heading)))
            .replace("{{summary}}", escape(summaryFor(context, code, host)))
            .replace("{{shown_url}}", escape(url))
            .replace("{{suggestion}}", search)
            .replace("{{code}}", escape(code))
            .replace("{{detail}}", escape(context.getString(R.string.error_page_detail)))
    }

    /** Whether [description] says something the page can name; false for a load that was cancelled. */
    @JvmStatic
    fun isWorthShowing(description: CharSequence?): Boolean {
        val text = description?.toString().orEmpty()
        if (text.isEmpty()) return false
        // Loads that were stopped on purpose, or that another part of the browser answered.
        return SILENT.none { text.contains(it) }
    }

    /** The address the browser searches for [host] with, in the search engine the user chose. */
    @JvmStatic
    fun searchUrl(context: Context, host: String): String {
        val prefs = com.rpooi3.explorer.profile.ProfilePrefs.settings(context)
        val engine = prefs.getString("sp_search_engine", "9")
        return SearchEngines.searchUrl(engine, Uri.encode(host))
    }

    /** `net::ERR_NAME_NOT_RESOLVED` → `ERR_NAME_NOT_RESOLVED`, and nothing for no error. */
    @JvmStatic
    fun errorCodeOf(description: CharSequence?): String {
        val text = description?.toString().orEmpty().trim()
        if (text.isEmpty()) return ""
        val at = text.indexOf("ERR_")
        if (at >= 0) return text.substring(at).trim()
        return text
    }

    /** The name of the site, without `www.`, or the whole address when it has no name. */
    @JvmStatic
    fun hostOf(url: String): String = try {
        val host = Uri.parse(url).host
        if (host.isNullOrEmpty()) url.replace("https://", "").replace("http://", "").trimEnd('/') else host
    } catch (e: Exception) {
        url
    }

    private fun template(context: Context): String =
        template ?: context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            .also { template = it }

    private fun escape(text: String): String = TextUtils.htmlEncode(text)

    /** The magnifier of the button, in the colours of the page. */
    private const val MAGNIFIER =
        "<svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" " +
            "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\">" +
            "<circle cx=\"11\" cy=\"11\" r=\"7\"/><line x1=\"16.5\" y1=\"16.5\" x2=\"21\" y2=\"21\"/></svg>"

    /** Errors that are not worth a page: a load the browser itself ended. */
    private val SILENT = listOf(
        "ERR_ABORTED",
        "ERR_BLOCKED_BY_CLIENT",
        "ERR_UNKNOWN_URL_SCHEME",
        "net::ERR_FAILED",
    )
}
