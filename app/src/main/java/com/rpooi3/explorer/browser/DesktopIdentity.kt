/*
 * Explorer: the JavaScript side of "Desktop site".
 * Own code, GPL-3.0.
 *
 * The user agent string alone is not enough for the pages Google writes today: they read
 * `navigator.platform`, `navigator.userAgentData` and `navigator.vendor` in the page as well, and
 * a page that sees an Android WebView there answers "Switch to Google Chrome" although the HTTP
 * header said Windows. This script makes the two agree: it is handed to the WebView before the
 * page's own scripts run, and only in desktop mode.
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

    /**
     * The script of a desktop page: Windows, a computer, no touch screen, Chrome's brands.
     *
     * @param major the Chrome version of the user agent, so that both tell the same story
     */
    @JvmStatic
    fun script(major: String): String = """
(function(){
  var MAJOR = '${'$'}{major}';
  var FULL = MAJOR + '.0.0.0';
  function define(object, name, value){
    try {
      Object.defineProperty(object, name, { value: value, configurable: true, writable: true });
    } catch (e) {
      try { object[name] = value; } catch (ignored) {}
    }
  }
  try {
    define(navigator, 'platform', 'Win32');
    define(navigator, 'vendor', 'Google Inc.');
    define(navigator, 'maxTouchPoints', 0);
  } catch (e) {}
  // The client hints of a Chrome on Windows.
  try {
    var brands = [
      { brand: 'Google Chrome', version: MAJOR },
      { brand: 'Chromium', version: MAJOR },
      { brand: 'Not_A Brand', version: '24' }
    ];
    var highEntropy = function(){
      return Promise.resolve({
        architecture: 'x86', bitness: '64', brands: brands, fullVersionList: [
          { brand: 'Google Chrome', version: FULL },
          { brand: 'Chromium', version: FULL },
          { brand: 'Not_A Brand', version: '24.0.0.0' }
        ], mobile: false, model: '', platform: 'Windows', platformVersion: '15.0.0.0',
        uaFullVersion: FULL, wow64: false
      });
    };
    var metadata = {
      brands: brands, mobile: false, platform: 'Windows',
      getHighEntropyValues: function(hints){ return highEntropy(); },
      toJSON: function(){ return { brands: brands, mobile: false, platform: 'Windows' }; }
    };
    if (!navigator.userAgentData) {
      define(navigator, 'userAgentData', metadata);
    } else {
      try {
        define(Object.getPrototypeOf(navigator.userAgentData), 'mobile', false);
        define(Object.getPrototypeOf(navigator.userAgentData), 'platform', 'Windows');
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
    fun apply(webView: WebView, major: String) {
        val code = script(major)
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
    fun applyNow(webView: WebView, major: String) {
        webView.evaluateJavascript(script(major), null)
    }
}
