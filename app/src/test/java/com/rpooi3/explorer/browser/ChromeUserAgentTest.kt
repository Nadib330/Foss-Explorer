package com.rpooi3.explorer.browser

import android.content.Intent
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.activity.BrowserActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The default identity of the browser: Chrome on Android, with the device's Android version. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class ChromeUserAgentTest {
    /**
     * 16.1.0: the browser opens the welcome of the first start (item 4). These tests are about
     * the browser itself, so the welcome is marked as seen before the activity is built.
     */
    @org.junit.Before
    fun markTheWelcomeAsSeen() {
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            .edit().putBoolean("welcome_seen", true).commit()
    }


    /** What a WebView reports on a real phone. */
    private val webView = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/126.0.6478.134 Mobile Safari/537.36"

    @Test
    fun mobile_isExactlyWhatChromeOnAndroidSends() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36",
            ChromeUserAgent.mobile(webView, "14"),
        )
    }

    @Test
    fun mobile_carriesNothingThatGivesAWebViewAway() {
        val userAgent = ChromeUserAgent.mobile(webView, "14")

        assertFalse("WebView marker", userAgent.contains("wv"))
        assertFalse("WebView version token", userAgent.contains("Version/"))
        assertFalse("build number", userAgent.contains("Build/"))
        assertFalse("device model", userAgent.contains("Pixel"))
    }

    @Test
    fun mobile_usesTheAndroidVersionItIsGiven() {
        assertTrue(ChromeUserAgent.mobile(webView, "8.0.0").contains("(Linux; Android 8.0.0; K)"))
        assertTrue(ChromeUserAgent.mobile(webView, "16").contains("(Linux; Android 16; K)"))
    }

    @Test
    fun mobile_byDefaultUsesTheAndroidVersionOfThisDevice() {
        assertTrue(ChromeUserAgent.mobile(webView).contains("(Linux; Android ${Build.VERSION.RELEASE}; K)"))
    }

    @Test
    fun aCodeNameInsteadOfAVersionNumber_isReplacedByANumber() {
        assertEquals("10", ChromeUserAgent.cleanRelease("Baklava"))
        assertEquals("10", ChromeUserAgent.cleanRelease(null))
        assertEquals("10", ChromeUserAgent.cleanRelease(" "))
        assertEquals("9", ChromeUserAgent.cleanRelease("9"))
        assertEquals("8.1.0", ChromeUserAgent.cleanRelease("8.1.0"))
    }

    @Test
    fun theChromeVersion_comesFromTheWebView_withAFallback() {
        assertEquals("126", ChromeUserAgent.majorVersion(webView))
        assertEquals(ChromeUserAgent.FALLBACK_MAJOR_VERSION, ChromeUserAgent.majorVersion("something else"))
        assertEquals(ChromeUserAgent.FALLBACK_MAJOR_VERSION, ChromeUserAgent.majorVersion(null))
    }

    @Test
    fun desktop_isChromeOnLinux_withoutAndroidOrMobile() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            ChromeUserAgent.desktop(webView),
        )
    }

    // ------------------------------------------------------------ in the app

    private fun firstTab(block: (WebView) -> Unit) {
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Robolectric.buildActivity(BrowserActivity::class.java, launch).setup().use { controller ->
            val content = controller.get().findViewById<ViewGroup>(R.id.main_content)
            block(content.getChildAt(0) as WebView)
        }
    }

    @Test
    fun aTab_identifiesItselfAsChrome_withThisDevicesAndroidVersion() = firstTab { web ->
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val userAgent = web.settings.userAgentString

        assertEquals(ChromeUserAgent.mobile(WebSettings.getDefaultUserAgent(context)), userAgent)
        assertTrue(userAgent, userAgent.startsWith("Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; K) "))
        assertTrue(userAgent, Regex("""Chrome/\d+\.0\.0\.0 Mobile Safari/537\.36$""").containsMatchIn(userAgent))
    }

    @Test
    fun aUserAgentStoredByAnOlderVersion_isNotUsedAnyMore() {
        // 12.1.2 removed the setting; what it left behind must not go on changing the user agent.
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("userAgentSwitch", true)
            .putString("sp_userAgent", "MyOwnBrowser/1.0")
            .commit()

        firstTab { web ->
            assertEquals(ChromeUserAgent.mobile(WebSettings.getDefaultUserAgent(context)), web.settings.userAgentString)
        }
    }
}
