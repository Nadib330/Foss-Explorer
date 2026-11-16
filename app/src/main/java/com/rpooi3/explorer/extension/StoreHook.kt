/*
 * Explorer: adding an extension from the Chrome Web Store.
 * Own code, GPL-3.0.
 *
 * The store's "Add to Chrome" button talks to Chrome, which is not there, so a page of the
 * store gets a button of Explorer's own: it takes the id out of the address, asks
 * [CrxDownloader] for the .crx of the extension, and [ExtensionStore] keeps it. The install
 * runs outside the page, so the tab stays usable while it downloads.
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
     * The script a page of the Chrome Web Store gets.
     *
     * 17.3.2: the store's *own* button is taken over instead of a second button of Explorer's
     * being put beside it. The blue **Add to Chrome** is found by its words, its label is
     * changed to **Add to Explorer**, and a tap on it installs through [CrxDownloader]. The store
     * draws its pages again and again, so the label is looked after while the page lives.
     *
     * Before any of that, the store is told that it is talking to Chrome: it asks
     * `window.chrome` (`webstore`, `app`, `runtime`, `csi`, `loadTimes`) before it offers the
     * button at all, and without those it shows "Switch to Chrome" instead.
     */
    @JvmStatic
    fun script(context: Context, extensionId: String, token: String): String {
        val installed = ExtensionStore.find(context, extensionId) != null
        val label = context.getString(if (installed) R.string.extensions_site_hook_done else R.string.extensions_site_hook)
        val note = context.getString(R.string.extensions_site_hook_note)
        val adding = context.getString(R.string.extensions_installing, "").trim()
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
  var LABEL = ${quote(label)};
  var NOTE = ${quote(note)};
  var ADDING = ${quote(adding)};

  // Chrome on a computer answers these, so the store offers its button to Explorer as well.
  if (!window.chrome) { window.chrome = {}; }
  if (!window.chrome.webstore) {
    window.chrome.webstore = {
      install: function(url, onSuccess, onFailure){
        try {
          var found = String(url || '').match(/[a-p]{32}/);
          var id = found ? found[0] : ID;
          if (INSTALLED) { throw new Error('already installed'); }
          window.ExplorerChrome.installFromStore(TOKEN, id);
          window.__explorerAdding = true;
          paint();
          if (typeof onSuccess === 'function') { onSuccess(); }
          return true;
        } catch (e) {
          if (typeof onFailure === 'function') { onFailure(e && e.message ? e.message : 'failed'); }
          return false;
        }
      },
      onInstallStageChanged: { addListener: function(){}, removeListener: function(){} },
      onDownloadProgress: { addListener: function(){}, removeListener: function(){} }
    };
  }
  if (!window.chrome.app) {
    window.chrome.app = {
      InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
      RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' },
      getDetails: function(){ return null; },
      getIsInstalled: function(){ return false; },
      runningState: function(){ return 'cannot_run'; }
    };
  }
  if (!window.chrome.runtime) {
    window.chrome.runtime = {
      id: undefined,
      getManifest: function(){ return {}; },
      getURL: function(path){ return path; },
      sendMessage: function(){},
      connect: function(){
        return { postMessage: function(){}, disconnect: function(){}, onMessage: { addListener: function(){} } };
      },
      onMessage: { addListener: function(){} },
      lastError: null
    };
  }
  if (!window.chrome.csi) {
    window.chrome.csi = function(){ return { startE: Date.now(), onloadT: Date.now() }; };
  }
  if (!window.chrome.loadTimes) {
    window.chrome.loadTimes = function(){
      var now = Date.now() / 1000;
      return {
        requestTime: now, startLoadTime: now, commitLoadTime: now, finishDocumentLoadTime: now,
        finishLoadTime: now, firstPaintTime: now, firstPaintAfterLoadTime: 0, navigationType: 'Other',
        wasFetchedViaSpdy: true, wasNpnNegotiated: true, npnNegotiatedProtocol: 'h2',
        wasAlternateProtocolAvailable: false, connectionInfo: 'h2'
      };
    };
  }

  // What the store's button says, and what it says now.
  function words(node){
    return String(node.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
  }
  function ariaWords(node){
    var value = node.getAttribute && node.getAttribute('aria-label');
    return String(value || '').replace(/\s+/g, ' ').trim().toLowerCase();
  }
  function isInstallButton(node){
    if (!node || node.nodeType !== 1) { return false; }
    var text = words(node);
    var aria = ariaWords(node);
    if (text === 'add to chrome' || text === 'add to chromium' || text === 'add extension' ||
        text === 'add to desktop' || aria === 'add to chrome' || aria === 'add extension') { return true; }
    if (text.indexOf('add to chrome') === 0 || text.indexOf('add extension') === 0) { return true; }
    return false;
  }
  // If the store does not believe that it is Chrome, the only blue thing on the page is the
  // one that offers Chrome itself; a tap on it adds the extension to Explorer instead.
  function isFallbackButton(node){
    if (!node || node.nodeType !== 1) { return false; }
    var text = words(node);
    var aria = ariaWords(node);
    return text === 'switch to chrome' || text === 'install chrome' || text === 'get chrome' ||
      aria === 'switch to chrome' || aria === 'install chrome';
  }
  function nodes(){
    var list = document.querySelectorAll('button, a, [role="button"], cr-button, paper-button');
    var found = [];
    for (var i = 0; i < list.length; i++) { found.push(list[i]); }
    // The store keeps parts of its page in a shadow root.
    var roots = document.querySelectorAll('*');
    for (var j = 0; j < roots.length && j < 4000; j++) {
      var root = roots[j].shadowRoot;
      if (!root) { continue; }
      var inner = root.querySelectorAll('button, a, [role="button"]');
      for (var k = 0; k < inner.length; k++) { found.push(inner[k]); }
    }
    return found;
  }

  /** Writes [text] over the words of the button, keeping its shape and its styles. */
  function relabel(node, text){
    var changed = false;
    try {
      var walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT, null, false);
      var current;
      while ((current = walker.nextNode())) {
        var value = current.nodeValue;
        if (!value) { continue; }
        if (/add to chrome|add to chromium|add extension|add to desktop|install chrome|switch to chrome|get chrome/i.test(value)) {
          current.nodeValue = value
            .replace(/add to chrome/gi, text)
            .replace(/add to chromium/gi, text)
            .replace(/add to desktop/gi, text)
            .replace(/add extension/gi, text)
            .replace(/install chrome/gi, text)
            .replace(/switch to chrome/gi, text)
            .replace(/get chrome/gi, text);
          changed = true;
        }
      }
      if (!changed) {
        // The words may be in an attribute only; then the button gets the text itself.
        if (String(node.textContent || '').trim() === '') {
          node.textContent = text;
          changed = true;
        }
      }
      node.setAttribute('aria-label', text);
      node.setAttribute('data-explorer-extension', ID);
      if (NOTE) { node.title = NOTE; }
    } catch (e) {}
    return changed;
  }

  /** How the store's button looks now: what a tap will do. */
  function paint(){
    var wanted = INSTALLED ? LABEL : (window.__explorerAdding ? ADDING : LABEL);
    var list = document.querySelectorAll('[data-explorer-extension]');
    for (var i = 0; i < list.length; i++) {
      relabel(list[i], wanted);
      list[i].style.cursor = INSTALLED ? 'default' : 'pointer';
      if (INSTALLED) { list[i].setAttribute('aria-disabled', 'true'); }
    }
  }

  function ask(){
    if (INSTALLED || window.__explorerAdding) { return; }
    try {
      if (!window.ExplorerChrome) { throw new Error('no bridge'); }
      window.__explorerAdding = true;
      window.ExplorerChrome.installFromStore(TOKEN, ID);
      paint();
    } catch (e) {
      window.__explorerAdding = false;
      paint();
    }
  }

  function take(node){
    if (!node.__explorerExtensionHooked) {
      node.__explorerExtensionHooked = true;
      node.addEventListener('click', function(event){
        if (event && typeof event.preventDefault === 'function') {
          event.preventDefault();
          event.stopPropagation();
        }
        ask();
      }, true);
    }
    relabel(node, INSTALLED ? LABEL : (window.__explorerAdding ? ADDING : LABEL));
    node.style.cursor = INSTALLED ? 'default' : 'pointer';
    if (INSTALLED) { node.setAttribute('aria-disabled', 'true'); }
  }

  /** The store's button, whenever it is drawn again. */
  function apply(){
    var all = nodes();
    var install = [];
    for (var i = 0; i < all.length; i++) {
      if (isInstallButton(all[i])) { install.push(all[i]); }
    }
    if (install.length === 0) {
      for (var j = 0; j < all.length; j++) {
        if (isFallbackButton(all[j])) { install.push(all[j]); }
      }
    }
    for (var k = 0; k < install.length && k < 4; k++) { take(install[k]); }
    paint();
  }

  apply();
  if (!window.__explorerStoreObserver) {
    window.__explorerStoreObserver = new MutationObserver(function(){
      if (window.__explorerStorePending) { return; }
      window.__explorerStorePending = true;
      setTimeout(function(){ window.__explorerStorePending = false; apply(); }, 120);
    });
    try {
      window.__explorerStoreObserver.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  }
  if (!window.__explorerStoreTimer) {
    // The first seconds of the store are a page under construction; a tap must never find the
    // button still saying "Add to Chrome".
    var count = 0;
    window.__explorerStoreTimer = setInterval(function(){
      apply();
      if (++count > 120) { clearInterval(window.__explorerStoreTimer); window.__explorerStoreTimer = null; }
    }, 500);
  }

  // A tap that lands before the label was looked after still installs.
  if (!window.__explorerStoreClicks) {
    window.__explorerStoreClicks = true;
    document.addEventListener('click', function(event){
      var target = event.target;
      var guard = 0;
      while (target && target !== document.body && guard++ < 12) {
        if (target.getAttribute && target.getAttribute('data-explorer-extension')) {
          if (event && typeof event.preventDefault === 'function') {
            event.preventDefault();
            event.stopPropagation();
          }
          ask();
          return;
        }
        if (isInstallButton(target) || isFallbackButton(target)) {
          if (event && typeof event.preventDefault === 'function') {
            event.preventDefault();
            event.stopPropagation();
          }
          ask();
          return;
        }
        target = target.parentElement;
      }
    }, true);
  }
})();
"""
    }


    /** Adds the button to the store page in [webView], if the page is one of an extension. */
    @JvmStatic
    fun onPageFinished(webView: WebView, url: String?) {
        inject(webView, url)
    }

    /**
     * The same, from {@code onPageStarted}: the store page needs its button as early as there
     * is a document for it. Running the script twice is harmless, it looks before it adds.
     */
    @JvmStatic
    fun onPageStarted(webView: WebView, url: String?) {
        inject(webView, url)
    }

    /** The script that was handed to a WebView before its pages load, so it is not added twice. */
    private val startScripts = java.util.WeakHashMap<WebView, String>()

    private fun inject(webView: WebView, url: String?) {
        if (!isExtensionPage(url)) return
        val id = CrxDownloader.extractExtensionId(url) ?: return
        val ninja = webView as? com.rpooi3.explorer.view.NinjaWebView ?: return
        if (ninja.isPrivateTab) return
        val bridge = ninja.extensionBridge ?: return
        val js = script(ninja.context, id, bridge.pageToken())
        // Before the page's own scripts first: the store looks at `window.chrome` to decide
        // whether it is talking to Chrome, and that has to be there already. The script asks
        // the address itself, so handing it to the WebView once is enough for every store page.
        registerForEveryPage(webView, js)
        webView.evaluateJavascript(js, null)
    }

    /**
     * Hands [js] to [webView] as a document-start script, where the WebView supports it (WebView
     * 89 and newer). Registering it again with the same text replaces nothing twice.
     */
    private fun registerForEveryPage(webView: WebView, js: String) {
        synchronized(startScripts) {
            if (startScripts[webView] == js) return
            startScripts[webView] = js
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

    private fun quote(text: String): String = org.json.JSONObject.quote(text)

    private fun quoteInstall(context: Context): String =
        context.getString(R.string.extensions_installing, "").trim()
}
