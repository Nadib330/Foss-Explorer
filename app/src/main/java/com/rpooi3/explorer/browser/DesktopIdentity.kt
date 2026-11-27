/*
 * Explorer: the JavaScript side of the Chrome Web Store.
 * Own code, GPL-3.0.
 *
 * The user agent string alone is not enough for the pages Google writes today: they read
 * `navigator.platform`, `navigator.userAgentData` and `navigator.vendor` in the page as well, and
 * a page that sees an Android WebView there answers "Switch to Google Chrome" although the HTTP
 * header said Windows. This script makes the two agree: it is handed to the WebView before the
 * page's own scripts run.
 *
 * 17.4.0: the script says whatever the user agent of the page says. A website in desktop mode is
 * served the agent of Chrome on Windows (17.3.3) and is told Windows here; the Chrome Web Store is
 * served the old Linux agent and is told Linux. A page in mobile mode is told nothing of the kind
 * and has the script taken back.
 */
package com.rpooi3.explorer.browser

import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

object DesktopIdentity {

    private const val TAG = "DesktopIdentity"

    /** What a tab is told, and the script that says it, so that it can be taken back again. */
    private val handed = WeakHashMap<WebView, String>()
    private val scripts = WeakHashMap<WebView, ScriptHandler>()

    /** The platform of a page served Chrome's Windows agent. */
    const val WINDOWS = "Windows"

    /** The platform of a page served Chrome's Linux agent: the Chrome Web Store. */
    const val LINUX = "Linux"

    /**
     * The script of a desktop page: a computer, no touch screen, Chrome's brands, and the
     * platform of the agent the page was served.
     *
     * @param major the Chrome version of the user agent, so that both tell the same story
     * @param platform [WINDOWS] or [LINUX], the platform of that agent
     */
    @JvmStatic
    @JvmOverloads
    fun script(major: String, platform: String = WINDOWS): String = """
(function(){
  var MAJOR = '${'$'}{major}';
  var FULL = MAJOR + '.0.0.0';
  var WINDOWS = '${'$'}{platform}' !== 'Linux';
  var PLATFORM = WINDOWS ? 'Windows' : 'Linux';
  var NAVIGATOR_PLATFORM = WINDOWS ? 'Win32' : 'Linux x86_64';
  var PLATFORM_VERSION = WINDOWS ? '15.0.0.0' : '';
  var ARCHITECTURE = 'x86';
  function define(object, name, value){
    try {
      Object.defineProperty(object, name, { value: value, configurable: true, writable: true });
    } catch (e) {
      try { object[name] = value; } catch (ignored) {}
    }
  }
  try {
    define(navigator, 'platform', NAVIGATOR_PLATFORM);
    define(navigator, 'vendor', 'Google Inc.');
    define(navigator, 'maxTouchPoints', 0);
  } catch (e) {}
  // The client hints of Chrome on the platform this page was served as.
  try {
    var brands = [
      { brand: 'Google Chrome', version: MAJOR },
      { brand: 'Chromium', version: MAJOR },
      { brand: 'Not_A Brand', version: '24' }
    ];
    var highEntropy = function(){
      return Promise.resolve({
        architecture: ARCHITECTURE, bitness: '64', brands: brands, fullVersionList: [
          { brand: 'Google Chrome', version: FULL },
          { brand: 'Chromium', version: FULL },
          { brand: 'Not_A Brand', version: '24.0.0.0' }
        ], mobile: false, model: '', platform: PLATFORM, platformVersion: PLATFORM_VERSION,
        uaFullVersion: FULL, wow64: false
      });
    };
    var metadata = {
      brands: brands, mobile: false, platform: PLATFORM,
      getHighEntropyValues: function(hints){ return highEntropy(); },
      toJSON: function(){ return { brands: brands, mobile: false, platform: PLATFORM }; }
    };
    if (!navigator.userAgentData) {
      define(navigator, 'userAgentData', metadata);
    } else {
      try {
        define(Object.getPrototypeOf(navigator.userAgentData), 'mobile', false);
        define(Object.getPrototypeOf(navigator.userAgentData), 'platform', PLATFORM);
      } catch (e) {
        define(navigator, 'userAgentData', metadata);
      }
    }
  } catch (e) {}
  // Chrome's own object, for the pages that look for it instead of the user agent.
  if (!window.chrome) { window.chrome = {}; }
})();
"""

    /**
     * Hands the script of desktop mode to [webView] before its pages load (WebView 89 and
     * newer), so that no page script can read the Android values first. On older WebViews the
     * caller runs it from the page callbacks; coming twice is harmless.
     */
    @JvmStatic
    @JvmOverloads
    fun apply(webView: WebView, major: String, platform: String = WINDOWS) {
        val code = script(major, platform)
        synchronized(handed) {
            if (handed[webView] == code) return
            handed[webView] = code
        }
        try {
            clear(webView)
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                webView.evaluateJavascript(code, null)
                return
            }
            val handler = WebViewCompat.addDocumentStartJavaScript(webView, code, setOf("*"))
            synchronized(handed) { scripts[webView] = handler }
        } catch (e: Throwable) {
            Log.i(TAG, "Not a document-start script: " + e)
        }
    }

    /**
     * Takes the script back when a tab stops asking for desktop pages: a page of the mobile
     * version must not be told that it is talking to Windows.
     */
    @JvmStatic
    fun clear(webView: WebView) {
        val handler = synchronized(handed) {
            handed.remove(webView)
            scripts.remove(webView)
        } ?: return
        try {
            handler.remove()
        } catch (e: Throwable) {
            Log.i(TAG, "The script of desktop mode was not removed: " + e)
        }
    }

    /** Runs the script in the page that is loading, for WebViews without document-start scripts. */
    @JvmStatic
    @JvmOverloads
    fun applyNow(webView: WebView, major: String, platform: String = WINDOWS) {
        webView.evaluateJavascript(script(major, platform), null)
    }
}
