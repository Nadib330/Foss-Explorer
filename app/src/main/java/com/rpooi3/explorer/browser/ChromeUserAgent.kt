package com.rpooi3.explorer.browser

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * The identity the browser shows to websites by default: Google Chrome.
 *
 * A WebView announces itself with a "; wv" marker, a "Version/4.0" token, the
 * device model and build number. Websites use these to tell an embedded
 * WebView from Chrome, and some refuse to work in a WebView. The strings built
 * here have the exact shape of Chrome's own user agent instead.
 *
 * The Chrome version is taken from the WebView that is installed on the
 * device, so the announced version always matches what the engine can do.
 */
object ChromeUserAgent {

    /** Used when the WebView does not report a Chrome version (should not happen on a real device). */
    const val FALLBACK_MAJOR_VERSION = "140"

    /** Chrome reports this fixed model name instead of the real device model. */
    private const val MODEL = "K"

    private const val TAG = "ChromeUserAgent"
    private val chromeVersion = Regex("""Chrome/(\d+)""")
    private val androidVersion = Regex("""^\d+(\.\d+){0,2}$""")

    /** The major Chrome version named in a WebView user agent, e.g. "140". */
    @JvmStatic
    fun majorVersion(webViewUserAgent: String?): String =
        webViewUserAgent?.let { chromeVersion.find(it)?.groupValues?.get(1) } ?: FALLBACK_MAJOR_VERSION

    /**
     * The user agent of Chrome on an Android phone.
     *
     * @param webViewUserAgent the WebView's own user agent, the source of the Chrome version
     * @param androidRelease the Android version of this device, e.g. "14"
     */
    @JvmStatic
    @JvmOverloads
    fun mobile(webViewUserAgent: String?, androidRelease: String? = Build.VERSION.RELEASE): String =
        "Mozilla/5.0 (Linux; Android ${cleanRelease(androidRelease)}; $MODEL) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/${majorVersion(webViewUserAgent)}.0.0.0 Mobile Safari/537.36"

    /**
     * The user agent of Chrome on **Windows**, which is what "Desktop site" sends since 17.3.3.
     *
     * A desktop browser is what a website expects in desktop mode, and the Chrome Web Store goes
     * further: it offers its extensions to a browser on a computer and to no other. The string
     * carries neither `wv` nor `Android`, so nothing can tell the WebView from Chrome on Windows.
     */
    @JvmStatic
    fun desktop(webViewUserAgent: String?): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/${majorVersion(webViewUserAgent)}.0.0.0 Safari/537.36"

    /** The platform of the desktop user agent, for the client hints that have to agree with it. */
    const val DESKTOP_PLATFORM = "Windows"

    /** Windows 10, as Chrome reports it in the client hints. */
    const val DESKTOP_PLATFORM_VERSION = "15.0.0"

    /** Pre-release builds report a code name such as "Baklava"; websites expect a number. */
    internal fun cleanRelease(release: String?): String {
        val value = release?.trim().orEmpty()
        return if (androidVersion.matches(value)) value else "10"
    }

    /**
     * Makes the client hints (the `Sec-CH-UA` headers and `navigator.userAgentData`)
     * tell the same story as the user agent string. Without this they would
     * still name "Android WebView".
     */
    @JvmStatic
    fun applyClientHints(settings: WebSettings, webViewUserAgent: String?, desktop: Boolean) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
            val major = majorVersion(webViewUserAgent)
            val full = "$major.0.0.0"
            fun brand(name: String, majorVersion: String, fullVersion: String) =
                UserAgentMetadata.BrandVersion.Builder()
                    .setBrand(name).setMajorVersion(majorVersion).setFullVersion(fullVersion).build()
            val metadata = UserAgentMetadata.Builder()
                .setBrandVersionList(
                    listOf(
                        brand("Google Chrome", major, full),
                        brand("Chromium", major, full),
                        brand("Not_A Brand", "24", "24.0.0.0"),
                    ),
                )
                .setFullVersion(full)
                .setPlatform(if (desktop) DESKTOP_PLATFORM else "Android")
                .setPlatformVersion(if (desktop) DESKTOP_PLATFORM_VERSION else cleanRelease(Build.VERSION.RELEASE))
                .setModel("")
                .setMobile(!desktop)
                .build()
            WebSettingsCompat.setUserAgentMetadata(settings, metadata)
        } catch (e: Throwable) {
            // Older or unusual WebView builds: the user agent string alone still applies.
            Log.i(TAG, "Client hints not set: $e")
        }
    }

    /**
     * Stops the WebView from adding the `X-Requested-With` header, which
     * carries the app's package name and which Chrome never sends.
     *
     * @return whether the installed WebView supports switching the header off
     */
    // The feature constant is marked as library-internal while the header control is still experimental.
    // It is only asked for here, and any failure leaves the header as it was.
    @SuppressLint("RestrictedApi")
    @JvmStatic
    fun hideRequestedWithHeader(settings: WebSettings): Boolean =
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.i(TAG, "X-Requested-With not switched off: $e")
            false
        }
}
