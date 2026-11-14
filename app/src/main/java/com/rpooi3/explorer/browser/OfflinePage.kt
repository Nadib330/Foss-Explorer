package com.rpooi3.explorer.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.TextUtils
import android.view.View
import androidx.core.net.toUri
import com.rpooi3.explorer.R
import java.util.Locale

/**
 * The "No internet" notice with the dinosaur game, and the game on its own
 * under [DINO_URL].
 *
 * Both are Chromium's network error page (`assets/offline/neterror.html`, see
 * `third_party/chromium-neterror`); this object fills in its texts.
 */
object OfflinePage {
    /** The address under which the game can be played at any time, like `chrome://dino`. */
    const val DINO_URL = "explorer://dino"

    /** What Chromium calls the error of a device without a connection. */
    const val ERROR_CODE = "ERR_INTERNET_DISCONNECTED"

    private const val ASSET = "offline/neterror.html"
    private var template: String? = null

    /** Whether [url] is the address of the game. Case and a trailing slash do not matter. */
    @JvmStatic
    fun isDinoUrl(url: String?): Boolean {
        val address = url?.trim()?.lowercase(Locale.ROOT)?.trimEnd('/') ?: return false
        return address == DINO_URL
    }

    /** Whether [url] is one of the browser's own pages rather than a website. */
    @JvmStatic
    fun isInternalUrl(url: String?): Boolean = isDinoUrl(url)

    /** Whether the device has no network that could reach the internet. */
    @JvmStatic
    fun isOffline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: SecurityException) {
            // Without the permission to look, do not claim to be offline.
            false
        }
    }

    /**
     * Whether a failed page load should be answered with the offline notice:
     * the WebView says the connection is gone, or it failed for another
     * network reason while the device has no connection.
     *
     * @param description the error text of the WebView, e.g. `net::ERR_INTERNET_DISCONNECTED`
     */
    @JvmStatic
    fun isOfflineError(description: CharSequence?, deviceOffline: Boolean): Boolean {
        val text = description?.toString().orEmpty()
        if (text.contains(ERROR_CODE)) return true
        if (!deviceOffline) return false
        return NETWORK_ERRORS.any { text.contains(it) }
    }

    /** The notice for a page that could not be loaded because there is no connection. */
    @JvmStatic
    fun offlineHtml(context: Context, failedUrl: String?): String {
        val suggestions = listOf(
            R.string.offline_suggestion_airplane,
            R.string.offline_suggestion_data,
            R.string.offline_suggestion_signal,
        ).joinToString("\n") { "                    <li>${escape(context.getString(it))}</li>" }
        return fill(
            context,
            title = failedUrl?.toUri()?.host ?: failedUrl.orEmpty(),
            heading = context.getString(R.string.offline_heading),
            suggestionsHeader = context.getString(R.string.offline_suggestions_header),
            suggestions = suggestions,
            errorCode = ERROR_CODE,
        )
    }

    /** The game on its own, as under `chrome://dino`. */
    @JvmStatic
    fun dinoHtml(context: Context): String = fill(
        context,
        title = DINO_URL,
        heading = context.getString(R.string.dino_heading),
        suggestionsHeader = "",
        suggestions = "",
        errorCode = "",
    )

    private fun fill(
        context: Context,
        title: String,
        heading: String,
        suggestionsHeader: String,
        suggestions: String,
        errorCode: String,
    ): String {
        val locale = context.resources.configuration.locales[0]
        val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
        return template(context)
            .replace("{{dir}}", if (rtl) "rtl" else "ltr")
            .replace("{{lang}}", escape(locale.toLanguageTag()))
            .replace("{{title}}", escape(title))
            .replace("{{heading}}", escape(heading))
            .replace("{{summary}}", "")
            .replace("{{suggestionsStyle}}", if (suggestions.isEmpty()) "display:none" else "")
            .replace("{{suggestionsHeader}}", escape(suggestionsHeader))
            .replace("{{suggestions}}", suggestions)
            .replace("{{errorCode}}", escape(errorCode))
    }

    private fun template(context: Context): String =
        template ?: context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }.also { template = it }

    private fun escape(text: String): String = TextUtils.htmlEncode(text)

    /** Errors that, on a device without a connection, mean nothing else than "offline". */
    private val NETWORK_ERRORS = listOf(
        "ERR_NAME_NOT_RESOLVED",
        "ERR_ADDRESS_UNREACHABLE",
        "ERR_NETWORK_CHANGED",
        "ERR_CONNECTION_",
        "ERR_PROXY_CONNECTION_FAILED",
        "ERR_TIMED_OUT",
    )
}
