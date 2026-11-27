/*
 * Explorer: adding an extension from the Chrome Web Store.
 * Own code, GPL-3.0.
 *
 * 18.1.0 beta (item 6) took the injections off the store. Beta 7 to beta 9 kept the store usable
 * by changing the page: a style sheet of a mobile layout, a `zoom` that shrank the desktop layout
 * into the screen, a walk of its shadow roots that hid the bars that offer Chrome, a relabelled
 * install button and finally a button of Explorer's own. All of that is gone. What is left is what
 * the page itself needs and nothing else:
 *
 *  * the **user agent** and the **viewport** are set per page in `NinjaWebView.initPreferences`,
 *    with `WebSettings` — the desktop agent of Chrome on Linux, which is what the store asks for
 *    before it shows its install button, and the phone's own layout width, so that the store's
 *    *own* responsive page is the mobile one. No style sheet is injected and no layout is changed.
 *  * a **JavaScript bridge**, the same compatibility layer a browser without `chrome.webstorePrivate`
 *    needs: the store calls `chrome.webstorePrivate.beginInstallWithManifest3`, and that call is
 *    handed to [CrxDownloader] through the extension bridge of the browser. Nothing of the page's
 *    markup is touched — no button is added, relabelled or hidden.
 *
 * The install itself runs outside the page, so the tab stays usable while the download runs.
 */
package com.rpooi3.explorer.extension

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.rpooi3.explorer.R
import com.rpooi3.explorer.view.CatToast
import java.util.concurrent.Executors

object StoreHook {

    private const val TAG = "ExplorerExtensions"

    /** One install at a time: two at once would be two downloads of the same file. */
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "explorer-extension-install").apply { isDaemon = true }
    }

    /** Whether [url] is a page of the store for one extension. */
    @JvmStatic
    fun isExtensionPage(url: String?): Boolean =
        CrxDownloader.isStoreUrl(url) && CrxDownloader.extractExtensionId(url) != null

    /**
     * The script a page of the store gets: the bridge, and nothing that touches the page.
     *
     * The store asks `window.chrome` (`webstore`, `webstorePrivate`, `app`, `runtime`, `csi`,
     * `loadTimes`) whether it is talking to a browser that can install extensions. Without those
     * it offers *Switch to Chrome* instead of its own install button. They are stubbed here, and
     * the install call of the store — `beginInstallWithManifest3` — is handed to the browser's
     * own installer, which fetches the `.crx` from Google and keeps it.
     */
    @JvmStatic
    @JvmOverloads
    fun script(
        context: Context,
        extensionId: String,
        token: String,
        version: String = com.rpooi3.explorer.browser.ChromeUserAgent.STORE_MIN_MAJOR,
    ): String {
        val installed = ExtensionStore.find(context, extensionId) != null
        return """
(function(){
  // Only a page of the store: the same script is handed to the WebView before every load of it.
  var host = location.hostname || '';
  if (host.indexOf('chromewebstore.google.com') < 0 && host.indexOf('chrome.google.com') < 0) { return; }
  // The id of the extension the page is about, taken from the address, so that going from one
  // extension to the next inside the store needs no new script.
  var ID = (String(location.href).match(/[a-p]{32}/) || ['${extensionId}'])[0];
  var TOKEN = '${token}';
  var INSTALLED = $installed;
  var VERSION = '${version}';

  // 18.1.0 beta (item 6): the bridge of the browser. There is no page work here at all.
  function install(id){
    if (!id) { return false; }
    if (INSTALLED || INSTALLED_IDS.indexOf(String(id)) >= 0) { return false; }
    var taken = false;
    try {
      if (window.ExplorerChrome && window.ExplorerChrome.installFromStore) {
        taken = window.ExplorerChrome.installFromStore(TOKEN, String(id)) === true;
      }
    } catch (e) { taken = false; }
    if (taken) { return true; }
    // 17.3.1, kept: a bridge that refused becomes a link the browser intercepts, which installs
    // through exactly the code of the three-dot menu.
    try { location.href = 'explorer-install://' + id; } catch (e) {}
    return false;
  }

  // --- the extensions that are here already (18.2.0 beta, item 4) -----------------------------
  // The store asks the browser which extensions it runs before it draws its button: the install
  // button when one is missing, "Remove from Explorer" when it is there. The list comes from the
  // browser as the JSON of chrome.management.getAll.
  var INSTALLED_IDS = [];
  function refresh(){
    try {
      if (window.ExplorerChrome && window.ExplorerChrome.installedExtensions) {
        var list = JSON.parse(window.ExplorerChrome.installedExtensions(TOKEN) || '[]');
        if (Object.prototype.toString.call(list) === '[object Array]') {
          INSTALLED_IDS = list;
          for (var i = 0; i < list.length; i++) {
            if (list[i] && list[i].id === ID) { INSTALLED = true; }
          }
        }
      }
    } catch (e) {}
  }
  refresh();
  function infoOf(id){
    for (var i = 0; i < INSTALLED_IDS.length; i++) {
      if (INSTALLED_IDS[i] && INSTALLED_IDS[i].id === id) { return INSTALLED_IDS[i]; }
    }
    return null;
  }

  // 18.2.0 beta (item 4): chrome.management — what the store asks before it decides that this
  // browser can install extensions at all, and what makes its button say "Remove from Explorer".
  if (!window.chrome) { window.chrome = {}; }
  if (!window.chrome.management) {
    window.chrome.management = {
      getAll: function(callback){
        refresh();
        if (typeof callback === 'function') { callback(INSTALLED_IDS.slice()); }
        return Promise.resolve(INSTALLED_IDS.slice());
      },
      get: function(id, callback){
        refresh();
        var found = infoOf(id);
        if (typeof callback === 'function') { callback(found); }
        return Promise.resolve(found);
      },
      getSelf: function(callback){
        if (typeof callback === 'function') { callback(null); }
        return Promise.resolve(null);
      },
      uninstall: function(id, options, callback){
        var done = typeof options === 'function' ? options : callback;
        var removed = false;
        if (window.ExplorerChrome && window.ExplorerChrome.uninstallFromStore) {
          try { removed = window.ExplorerChrome.uninstallFromStore(TOKEN, String(id)) === true; } catch (e) { removed = false; }
        }
        refresh();
        if (typeof done === 'function') { done(); }
        return Promise.resolve(removed ? undefined : 'not-found');
      },
      setEnabled: function(id, enabled, callback){
        if (typeof callback === 'function') { callback(); }
        return Promise.resolve();
      },
      launchApp: function(){},
      getPermissionWarningsById: function(id, callback){
        if (typeof callback === 'function') { callback([]); }
      }
    };
  }

  // --- chrome.webstorePrivate (18.2.0 beta, item 4) -------------------------------------------
  // The store's frontend greys its button out unless the browser answers the whole handshake of
  // these calls: whether it can install, what version it is, whether it is incognito, and — the
  // one that decides the state of the button — getExtensionStatus, which says "installable" for
  // an extension that is not here and "installed" for one that is.
  if (!window.chrome.webstorePrivate) {
    window.chrome.webstorePrivate = {
      getBrowserLogin: function(callback){ if (callback) { callback({ login: '' }); } },
      getStoreLogin: function(callback){ if (callback) { callback(''); } },
      setStoreLogin: function(login, callback){ if (callback) { callback(); } },
      getIsLauncherEnabled: function(callback){ if (callback) { callback(false); } },
      isInIncognitoMode: function(callback){ if (callback) { callback(false); } },
      getFullChromeVersion: function(callback){
        // The version the store is told is the one of its agent (18.2.0 beta, item 4: never
        // older than 131, which is where Manifest V3 extensions begin).
        if (callback) { callback({ version_number: VERSION + '.0.0.0', app_name: 'Explorer' }); }
      },
      getExtensionStatus: function(id, manifest, callback){
        refresh();
        var here = INSTALLED || infoOf(String(id)) !== null;
        if (typeof callback === 'function') { callback(here ? 'installed' : 'installable'); }
        return Promise.resolve(here ? 'installed' : 'installable');
      },
      beginInstallWithManifest3: function(details, callback){
        var id = (details && (details.id || details.extensionId)) || ID;
        var manifest = (details && details.manifest) || '';
        // Both ways in: the object of the browser that is registered for this page, and the
        // bridge that installs through the code of the three-dot menu.
        try {
          if (window.ExplorerStoreBridge && window.ExplorerStoreBridge.beginInstall) {
            window.ExplorerStoreBridge.beginInstall(String(id), String(manifest));
          }
        } catch (e) {}
        var taken = install(id);
        if (typeof callback === 'function') {
          try { callback(taken ? '' : 'explorer-busy'); } catch (e) {}
        }
        return taken;
      },
      completeInstall: function(id, callback){
        refresh();
        if (typeof callback === 'function') { try { callback(); } catch (e) {} }
        return true;
      }
    };
  }
  // What the store reads to decide that it is not in an embedded browser.
  if (!window.chrome.app) {
    window.chrome.app = {
      isInstalled: false,
      InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
      RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' },
      getDetails: function(){ return null; },
      getIsInstalled: function(){ return false; },
      runningState: function(){ return 'cannot_run'; }
    };
  }
  if (!window.chrome.runtime) { window.chrome.runtime = {}; }
  if (!window.chrome.csi) { window.chrome.csi = function(){ return {}; }; }
  if (!window.chrome.loadTimes) { window.chrome.loadTimes = function(){ return {}; }; }
})();
""".trimIndent()
    }

    /**
     * The two pages callbacks of the browser: the bridge has to be there before the page's own
     * scripts ask for it, so it is handed to the WebView as a document-start script where the
     * WebView can do that, and run again as soon as the page exists.
     */
    @JvmStatic
    fun onPageFinished(webView: WebView, url: String?) {
        inject(webView, url)
    }

    /** The same, from {@code onPageStarted}. Running the script twice is harmless. */
    @JvmStatic
    fun onPageStarted(webView: WebView, url: String?) {
        inject(webView, url)
    }

    /** The scripts that were handed to a WebView before its pages load, so that none is added twice. */
    private val startScripts = java.util.WeakHashMap<WebView, MutableSet<String>>()

    private fun inject(webView: WebView, url: String?) {
        if (!isExtensionPage(url)) return
        val id = CrxDownloader.extractExtensionId(url) ?: return
        val ninja = webView as? com.rpooi3.explorer.view.NinjaWebView ?: return
        if (ninja.isPrivateTab) return
        val bridge = ninja.extensionBridge ?: return
        // 18.2.0 beta (item 4): the version the page is told is the one of the agent it was
        // given, which is never older than 131 for the store.
        val js = script(
            ninja.context,
            id,
            bridge.pageToken(),
            com.rpooi3.explorer.browser.ChromeUserAgent.storeMajorVersion(
                try { webView.settings.userAgentString } catch (e: Throwable) { null },
            ),
        )
        registerForEveryPage(webView, js)
        webView.evaluateJavascript(js, null)
    }

    /**
     * Hands [js] to [webView] as a document-start script, where the WebView supports it (WebView
     * 89 and newer). Registering it again with the same text replaces nothing twice.
     */
    private fun registerForEveryPage(webView: WebView, js: String) {
        synchronized(startScripts) {
            val known = startScripts.getOrPut(webView) { HashSet() }
            if (!known.add(js)) return
        }
        try {
            if (!androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT,
                )
            ) {
                return
            }
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, js, setOf("*"))
        } catch (e: Throwable) {
            // An older WebView: the script is run from the page callbacks instead.
            Log.i(TAG, "No document-start script: " + e)
        }
    }

    /**
     * Downloads and installs [extensionId]; the result is a toast, and [onDone] is told what
     * came of it on the thread of the install (the settings screen posts it to the main one).
     */
    @JvmStatic
    @JvmOverloads
    fun install(context: Context, extensionId: String, onDone: ((WebExtension?) -> Unit)? = null) {
        val application = context.applicationContext
        CatToast.show(application, application.getString(R.string.extensions_installing, extensionId), 1)
        worker.execute {
            var reason: String? = null
            var installed: WebExtension? = null
            try {
                installed = CrxDownloader.install(application, extensionId) { why -> reason = why }
            } catch (e: Throwable) {
                // 17.3.3: whatever the download or the unpacking throws stays with the install.
                Log.w(TAG, "Install failed: " + e)
                reason = e.message
            }
            val result = installed
            val why = reason
            CatToast.show(
                application,
                if (result != null) application.getString(R.string.extensions_installed, result.name)
                else application.getString(
                    R.string.extensions_install_failed,
                    why ?: application.getString(R.string.extensions_none_in_menu),
                ),
                3,
            )
            // The browser is told on the main thread, so that reloading a tab cannot run on
            // this one (17.3.3: this is what ended the app right after an install).
            if (onDone != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        onDone.invoke(result)
                    } catch (e: Throwable) {
                        Log.w(TAG, "After the install: " + e)
                    }
                }
            }
        }
    }
}
