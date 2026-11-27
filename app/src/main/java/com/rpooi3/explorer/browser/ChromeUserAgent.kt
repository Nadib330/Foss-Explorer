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

    /**
     * 18.2.0 beta (item 4): the oldest Chrome the Chrome Web Store is told about.
     *
     * The store greys its install button out for a browser that reports an engine older than the
     * one Manifest V3 extensions are meant for, whatever the rest of the handshake says. A WebView
     * that has not been updated for a while would be told so and lose the button, so the store is
     * told 131 or the version of the device, whichever is newer.
     */
    const val STORE_MIN_MAJOR = "131"

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
     * The user agent of Chrome on a computer, which is what "Desktop site" sends to every
     * website: Chrome on **Windows**, as it has been since 17.3.3.
     *
     * The string carries neither `wv` nor `Android`, so nothing can tell the WebView from Chrome.
     */
    @JvmStatic
    fun desktop(webViewUserAgent: String?): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/${majorVersion(webViewUserAgent)}.0.0.0 Safari/537.36"

    /**
     * The user agent of Chrome on **Linux**, sent to the Chrome Web Store and to nothing else
     * (17.4.0): the desktop agent Explorer had before 17.3.3.
     *
     * The store is where extensions are handed out, and it does not go by the user agent alone —
     * it draws its own page and reads `navigator.platform` in it. The old Linux agent is the one
     * the store's pages have always been served by Explorer, and it is what they get back here;
     * every other website keeps the Windows agent of [desktop].
     */
    @JvmStatic
    fun store(webViewUserAgent: String?): String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/${storeMajorVersion(webViewUserAgent)}.0.0.0 Safari/537.36 " +
            // 18.1.0 beta (item 6): the brand token of this browser, as the store's own pages
            // expect one beside the engine they were given.
            "Explorer/1.0"

    /** The version the store is told: [STORE_MIN_MAJOR], or the engine here if it is newer. */
    @JvmStatic
    fun storeMajorVersion(webViewUserAgent: String?): String {
        val here = majorVersion(webViewUserAgent)
        val floor = STORE_MIN_MAJOR
        if (here.length != floor.length) return if (here.length > floor.length) here else floor
        return if (here >= floor) here else floor
    }

    /** The platform of desktop mode, for the client hints that have to agree with the agent. */
    const val DESKTOP_PLATFORM = "Windows"

    /** Windows 10, as Chrome reports it in the client hints of a desktop page. */
    const val DESKTOP_PLATFORM_VERSION = "15.0.0"

    /** The platform the Chrome Web Store is told it is running on. */
    const val STORE_PLATFORM = "Linux"

    /** Chrome on Linux reports no platform version; the store's pages expect none. */
    const val STORE_PLATFORM_VERSION = ""

    /** The platform that goes with [store] and [desktop]; `null` for the mobile agent. */
    @JvmStatic
    fun platformOf(store: Boolean, desktop: Boolean): String = when {
        store -> STORE_PLATFORM
        desktop -> DESKTOP_PLATFORM
        else -> "Android"
    }

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
    fun applyClientHints(settings: WebSettings, webViewUserAgent: String?, platform: String) {
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
                .setPlatform(platform)
                .setPlatformVersion(
                    when (platform) {
                        STORE_PLATFORM -> STORE_PLATFORM_VERSION
                        DESKTOP_PLATFORM -> DESKTOP_PLATFORM_VERSION
                        else -> cleanRelease(Build.VERSION.RELEASE)
                    },
                )
                .setModel("")
                .setMobile(platform == "Android")
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
