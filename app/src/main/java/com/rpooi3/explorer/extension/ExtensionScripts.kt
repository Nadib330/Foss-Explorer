/*
 * Explorer: runs an extension's content scripts in a tab.
 * Own code, GPL-3.0.
 *
 * The Android System WebView has no chrome.* interfaces, so before an extension's own code is
 * run, [shim] hands it the ones it needs: chrome.runtime (its id, getURL, sendMessage and the
 * listeners), chrome.storage over [ChromeApiBridge], chrome.i18n and stubs for the rest. Without
 * that, most extensions stop with "Cannot read properties of undefined".
 */
package com.rpooi3.explorer.extension

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ExtensionScripts {

    /** The host Explorer serves extension files from: popups, icons and the pages of getURL. */
    const val DOMAIN = "explorer-extensions.local"

    /** How much of one extension may be run on a page, so a big one cannot freeze a tab. */
    /**
     * 18.0.0 beta 8 (item 5): how much of a content script is run. A page built with a framework
     * brings a bundle of one to three megabytes, and the old limits — one megabyte for a file, three
     * for all of them — dropped exactly those, so the extension sat in the menu and did nothing on
     * the pages it named. What is read of an extension is its own files, unpacked from its own
     * `.crx`, so the limit is only there to keep a broken package from being read forever.
     */
    private const val MAX_FILE_BYTES = 12L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 24L * 1024 * 1024

    private val patterns = ConcurrentHashMap<String, Regex>()

    /** The address of a file of [extensionId] on Explorer's own extension server. */
    @JvmStatic
    fun resourceUrl(extensionId: String, path: String): String =
        "https://$DOMAIN/$extensionId/" + path.trimStart('/')

    /** The page Explorer serves for the background file of an extension (17.3.4). */
    const val BACKGROUND_PAGE = "__explorer_background__.html"

    /**
     * The address of the background page of [extensionId]: a page of Explorer's own that runs the
     * `background.service_worker` (or the first of `background.scripts`) of the extension.
     */
    @JvmStatic
    fun backgroundUrl(extensionId: String): String = resourceUrl(extensionId, BACKGROUND_PAGE)

    /** Whether [url] is a file of an extension rather than a page of the web. */
    @JvmStatic
    fun isExtensionUrl(url: String?): Boolean = url != null && url.startsWith("https://$DOMAIN/")

    /** The id of the extension a file of Explorer's extension server belongs to, or null. */
    @JvmStatic
    fun extensionIdOf(url: String?): String? {
        if (!isExtensionUrl(url)) return null
        val rest = url!!.removePrefix("https://$DOMAIN/")
        return rest.substringBefore('/').takeIf { it.isNotEmpty() }
    }

    /**
     * Whether a match pattern of a manifest (a scheme, a host and a path, each with wildcards,
     * or `<all_urls>`) asks for [url]. The patterns of the extensions are compiled once.
     */
    @JvmStatic
    fun matches(pattern: String?, url: String?): Boolean {
        if (pattern.isNullOrBlank() || url.isNullOrBlank()) return false
        val regex = patterns.getOrPut(pattern) { patternToRegex(pattern) ?: NEVER } ?: return false
        if (regex === NEVER) return false
        return regex.containsMatchIn(url)
    }

    private val NEVER = Regex("(?!)")

    /** The end of a regular expression, as a name: a dollar sign at the end of a string literal
     *  would start an interpolation in Kotlin. */
    private const val END_OF_PATTERN = "\u0024"

    /** [pattern] as a regular expression; null if it is not a match pattern Explorer understands. */
    @JvmStatic
    fun patternToRegex(pattern: String): Regex? {
        if (pattern == "<all_urls>") {
            return Regex("^(https?|file|ftp)://[^\\s]+" + END_OF_PATTERN, RegexOption.IGNORE_CASE)
        }
        val separator = pattern.indexOf("://")
        if (separator <= 0) return null
        val scheme = pattern.substring(0, separator)
        val rest = pattern.substring(separator + 3)
        val slash = rest.indexOf('/')
        val host = if (slash >= 0) rest.substring(0, slash) else rest
        val path = if (slash >= 0) rest.substring(slash) else "/"
        val schemeRegex = when (scheme) {
            "*" -> "https?"
            "http", "https", "file", "ftp" -> Regex.escape(scheme)
            else -> return null
        }
        if (host.isEmpty()) return null
        val hostRegex = when {
            host == "*" -> "[^/]+"
            host.startsWith("*.") -> "(?:[^/]+\\.)?" + Regex.escape(host.substring(2))
            else -> Regex.escape(host)
        }
        val pathRegex = path.split("*").joinToString(".*") { Regex.escape(it) }
        return Regex("^" + schemeRegex + "://" + hostRegex + pathRegex + END_OF_PATTERN, RegexOption.IGNORE_CASE)
    }

    /**
     * What has to be run in a tab for [extension] on [url] at [runAt], or null if the extension
     * asks for nothing here. The result begins with [shim] and then has one block per file, so a
     * script that throws does not stop the next one.
     */
    @JvmStatic
    fun injectionFor(
        context: Context,
        extension: WebExtension,
        url: String?,
        runAt: String,
        token: String,
    ): String? {
        if (url.isNullOrBlank() || isExtensionUrl(url)) return null
        val rules = extension.contentScripts.filter { it.runAt == runAt && it.appliesTo(url) }
        if (rules.isEmpty()) return null
        val root = File(extension.rootDir)
        val canonicalRoot = try {
            root.canonicalPath + File.separator
        } catch (e: Exception) {
            return null
        }
        val body = StringBuilder()
        var total = 0L
        for (rule in rules) {
            for (cssFile in rule.cssFiles) {
                val text = read(root, canonicalRoot, cssFile) ?: continue
                total += text.length
                if (total > MAX_TOTAL_BYTES) break
                body.append(styleScript(text))
            }
            for (jsFile in rule.jsFiles) {
                val text = read(root, canonicalRoot, jsFile) ?: continue
                total += text.length
                if (total > MAX_TOTAL_BYTES) break
                body.append("try{\n").append(text).append("\n}catch(e){console.warn('Explorer extension ")
                    .append(extension.id).append(": ', e && e.message ? e.message : e);}\n")
            }
        }
        if (body.isEmpty()) return null
        // The guard makes the script safe to run in both page callbacks.
        //
        // 18.0.0 beta 8 (item 5): the mark is set **after** the script ran, and only if it got
        // through. It used to be set before, so a content script that threw at `document_start` —
        // and a great many of them reach for `document.body` or a `<head>` that is not parsed
        // yet — was marked as done and never ran again on that page: the extension did nothing on
        // the whole site. Now a failed run is tried again with the next callback, and the run
        // also waits for a document to exist before it starts.
        val guardKey = "'__explorerExt__" + extension.id + "__" + runAt + "__' + location.href"
        return "(function(){var K=" + guardKey + ";if(window[K]==='ok'){return;}" +
            "function run(){try{\n" + shim(extension.id, token) + body +
            "\nwindow[K]='ok';}catch(e){window[K]='fail';" +
            "if(window.console){console.warn('Explorer extension " + extension.id + ":'," +
            "e&&e.message?e.message:e);}}}" +
            "if(document.documentElement){run();}else{var tries=0;" +
            "var timer=setInterval(function(){if(document.documentElement||++tries>200){" +
            "clearInterval(timer);run();}},10);}})();"
    }

    /** The files of [extension] that have to be run before the page is parsed, and after it. */
    @JvmStatic
    fun inject(
        context: Context,
        extension: WebExtension,
        url: String?,
        runAt: String,
        token: String,
    ): String? = injectionFor(context, extension, url, runAt, token)

    /** A `<style>` element with [css] in it. */
    @JvmStatic
    fun styleScript(css: String): String =
        "(function(){try{var s=document.createElement('style');s.setAttribute('data-explorer-extension','');" +
            "s.textContent=" + JSONObject.quote(css) + ";" +
            "(document.head||document.documentElement).appendChild(s);}catch(e){}})();\n"

    private fun read(root: File, canonicalRoot: String, name: String): String? {
        val file = File(root, name)
        return try {
            if (!file.isFile) return null
            if (!file.canonicalPath.startsWith(canonicalRoot)) return null
            if (file.length() > MAX_FILE_BYTES) return null
            file.readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The chrome.* interfaces of [extension], as JavaScript. It runs before the extension's own
     * code, in the page, and talks to [ChromeApiBridge] through `window.ExplorerChrome`.
     */
    @JvmStatic
    fun shim(extension: WebExtension, token: String): String = shim(extension.id, token)

    /**
     * The chrome.* interfaces of [extensionId], with [token] as the key of the bridge calls.
     * The token is an argument of the function that holds the shim, not a name of the page, so a
     * script of the website cannot read it.
     */
    /**
     * Just the shim, guarded the way [injectionFor] guards its scripts, for a popup: it may be
     * handed to the WebView on both page callbacks without running twice in one document.
     */
    @JvmStatic
    fun shimBundle(extensionId: String, token: String): String =
        "(function(){var K='__explorerShim__" + extensionId + "__' + location.href;" +
            "if(window[K]){return;}window[K]=1;\n" + shim(extensionId, token) + "})();"

    @JvmStatic
    fun shim(extensionId: String, token: String): String = SHIM
        .replace("__EXTENSION_ID__", extensionId)
        .replace("__TOKEN__", token)

    private const val SHIM = """
(function(TOKEN){
  var EXT = "__EXTENSION_ID__";
  var bridge = window.ExplorerChrome || {};
  var G = window.__explorerExt || (window.__explorerExt = {
    listeners: {}, pending: {}, nextId: 1
  });
  function parse(text, fallback){ try { return JSON.parse(text); } catch(e){ return fallback; } }
  function json(value){ try { return JSON.stringify(value === undefined ? null : value); } catch(e){ return "null"; } }
  function call(name, args, fallback){
    try {
      if (bridge && typeof bridge[name] === "function") { return bridge[name].apply(bridge, args); }
    } catch(e){}
    return fallback;
  }
  // ---- what a message can reach in this document -------------------------------------------
  function listeners(){ return G.listeners[EXT] || (G.listeners[EXT] = []); }
  window.__explorerExtDispatch = function(extId, jsonText){
    var list = G.listeners[extId] || [];
    var message = parse(jsonText, null);
    var sender = {
      id: extId,
      url: location.href,
      tab: parse(call("currentTab", [TOKEN, extId], "null"), null)
    };
    var answer;
    var responded = false;
    function sendResponse(value){ if (!responded) { responded = true; answer = value; } }
    for (var i = 0; i < list.length; i++) {
      try {
        var reply = list[i](message, sender, sendResponse);
        if (reply !== undefined && answer === undefined) { answer = reply; }
      } catch(e){}
    }
    return answer === undefined ? undefined : answer;
  };
  window.__explorerExtReply = function(id, jsonText){
    var resolve = G.pending[id];
    if (resolve) { delete G.pending[id]; try { resolve(parse(jsonText, undefined)); } catch(e){} }
  };
  function request(){
    var id = String(G.nextId++);
    var promise = new Promise(function(resolve){ G.pending[id] = resolve; });
    return { id: id, promise: promise };
  }
  function callbackOf(){ 
    for (var i = 0; i < arguments.length; i++) {
      if (typeof arguments[i] === "function") { return arguments[i]; }
    }
    return null;
  }
  function answer(promise, callback){
    if (callback) { promise.then(function(value){ try { callback(value); } catch(e){} }); }
    return promise;
  }
  function readArea(kind){
    return parse(kind === "session" ? call("sessionGet", [TOKEN, EXT], "{}") : call("storageGet", [TOKEN, EXT], "{}"), {});
  }
  function writeArea(kind, data){
    if (kind === "session") { call("sessionSet", [TOKEN, EXT, json(data)], ""); }
    else { call("storageSet", [TOKEN, EXT, json(data)], ""); }
  }
  function storageArea(kind){
    return {
      get: function(keys, callback){
        var all = readArea(kind);
        var out = {};
        if (keys === null || keys === undefined) { out = all; }
        else if (typeof keys === "string") {
          if (Object.prototype.hasOwnProperty.call(all, keys)) { out[keys] = all[keys]; }
        }
        else if (Object.prototype.toString.call(keys) === "[object Array]") {
          for (var i = 0; i < keys.length; i++) {
            if (Object.prototype.hasOwnProperty.call(all, keys[i])) { out[keys[i]] = all[keys[i]]; }
          }
        }
        else if (typeof keys === "object") {
          for (var k in keys) {
            out[k] = Object.prototype.hasOwnProperty.call(all, k) ? all[k] : keys[k];
          }
        }
        if (typeof callback === "function") { try { callback(out); } catch(e){} }
        return Promise.resolve(out);
      },
      set: function(items, callback){
        var all = readArea(kind);
        for (var k in items) { all[k] = items[k]; }
        writeArea(kind, all);
        if (typeof callback === "function") { try { callback(); } catch(e){} }
        return Promise.resolve();
      },
      remove: function(keys, callback){
        var all = readArea(kind);
        var list = Object.prototype.toString.call(keys) === "[object Array]" ? keys : [keys];
        for (var i = 0; i < list.length; i++) { delete all[list[i]]; }
        writeArea(kind, all);
        if (typeof callback === "function") { try { callback(); } catch(e){} }
        return Promise.resolve();
      },
      clear: function(callback){
        writeArea(kind, {});
        if (typeof callback === "function") { try { callback(); } catch(e){} }
        return Promise.resolve();
      },
      getBytesInUse: function(keys, callback){
        var size = json(readArea(kind)).length;
        if (typeof callback === "function") { callback(size); }
        return Promise.resolve(size);
      },
      onChanged: { addListener: function(){}, removeListener: function(){} }
    };
  }
  // ---- chrome.* ----------------------------------------------------------------------------
  var chrome = window.chrome || {};
  window.chrome = chrome;
  chrome.runtime = chrome.runtime || {};
  chrome.runtime.id = EXT;
  chrome.runtime.getURL = function(path){
    return "https://$DOMAIN/" + EXT + "/" + String(path || "").replace(/^\/+/, "");
  };
  chrome.runtime.getManifest = function(){ return parse(call("getManifest", [TOKEN, EXT], "{}"), {}); };
  chrome.runtime.lastError = null;
  chrome.runtime.sendMessage = function(message, options, callback){
    var cb = callbackOf(options, callback);
    var request_ = request();
    call("sendMessage", [TOKEN, EXT, request_.id, json(message)], "");
    return answer(request_.promise, cb);
  };
  chrome.runtime.onMessage = {
    addListener: function(fn){ if (typeof fn === "function") { listeners().push(fn); } },
    removeListener: function(fn){ var list = listeners(); var i = list.indexOf(fn); if (i >= 0) { list.splice(i, 1); } },
    hasListener: function(fn){ return listeners().indexOf(fn) >= 0; }
  };
  chrome.runtime.onInstalled = { addListener: function(){} };
  chrome.runtime.onStartup = { addListener: function(){} };
  chrome.runtime.onConnect = { addListener: function(){}, removeListener: function(){} };
  chrome.runtime.connect = function(){
    var port = {
      name: "", postMessage: function(){}, disconnect: function(){},
      onMessage: { addListener: function(){} },
      onDisconnect: { addListener: function(){} }
    };
    return port;
  };
  chrome.runtime.getPlatformInfo = function(callback){
    var info = { os: "android", arch: "arm64", nacl_arch: "arm", client: "android" };
    if (typeof callback === "function") { callback(info); }
    return Promise.resolve(info);
  };
  chrome.runtime.openOptionsPage = function(){ call("openOptions", [TOKEN, EXT], ""); };
  chrome.storage = chrome.storage || {};
  chrome.storage.local = chrome.storage.local || storageArea("local");
  chrome.storage.sync = chrome.storage.sync || storageArea("local");
  chrome.storage.session = chrome.storage.session || storageArea("session");
  chrome.storage.managed = chrome.storage.managed || storageArea("local");
  chrome.storage.onChanged = chrome.storage.onChanged || { addListener: function(){}, removeListener: function(){} };
  chrome.i18n = chrome.i18n || {
    getMessage: function(name){ return typeof name === "string" ? name : ""; },
    getUILanguage: function(){ return (navigator.language || "en"); },
    getAcceptLanguages: function(cb){ var l = [(navigator.language || "en")]; if (cb) { cb(l); } return Promise.resolve(l); }
  };

  function runScript(payload, callback){
    var request_ = request();
    call("executeScript", [TOKEN, EXT, request_.id, json(payload)], "");
    return answer(request_.promise, callback);
  }

  var tabsApi = {
    query: function(info, callback){
      var list = parse(call("tabsQuery", [TOKEN, EXT], "[]"), []);
      if (typeof info === "function") { info(list); } else if (typeof callback === "function") { callback(list); }
      return Promise.resolve(list);
    },
    get: function(id, callback){
      var tab = parse(call("tabsGet", [TOKEN, EXT, String(id)], "null"), null);
      if (typeof callback === "function") { callback(tab); }
      return Promise.resolve(tab);
    },
    sendMessage: function(tabId, message, options, callback){
      var cb = callbackOf(options, callback);
      var request_ = request();
      call("tabSendMessage", [TOKEN, EXT, request_.id, String(tabId), json(message)], "");
      return answer(request_.promise, cb);
    },
    create: function(options, callback){
      if (options && options.url) { call("openTab", [TOKEN, String(options.url)], ""); }
      var tab = parse(call("currentTab", [TOKEN, EXT], "null"), null);
      if (typeof callback === "function") { callback(tab); }
      return Promise.resolve(tab);
    },
    update: function(id, options, callback){
      if (options && options.url) { call("openTab", [TOKEN, String(options.url)], ""); }
      var tab = parse(call("currentTab", [TOKEN, EXT], "null"), null);
      if (typeof callback === "function") { callback(tab); }
      return Promise.resolve(tab);
    },
    reload: function(id, properties, callback){ call("reloadTab", [TOKEN, EXT], ""); },
    captureVisibleTab: function(a, b, c){ return Promise.resolve(""); },
    executeScript: function(tabId, details, callback){
      if (typeof tabId === "object" && tabId !== null) { callback = details; details = tabId; }
      var payload = { files: [], args: [], code: null, allFrames: false };
      if (details) {
        if (details.file) { payload.files = [String(details.file)]; }
        if (details.files) { payload.files = details.files; }
        if (typeof details.code === "string") { payload.code = "function(){" + details.code + "}"; }
        if (typeof details.func === "function") { payload.code = details.func.toString(); }
        if (details.args) { payload.args = details.args; }
      }
      return runScript(payload, callback);
    },
    onUpdated: { addListener: function(){}, removeListener: function(){} },
    onRemoved: { addListener: function(){}, removeListener: function(){} },
    onActivated: { addListener: function(){}, removeListener: function(){} },
    onCreated: { addListener: function(){}, removeListener: function(){} }
  };
  chrome.tabs = chrome.tabs || tabsApi;
  chrome.scripting = chrome.scripting || {};
  chrome.scripting.executeScript = chrome.scripting.executeScript || function(injection, callback){
    var payload = { files: [], args: [], code: null, allFrames: false };
    if (injection) {
      payload.args = injection.args || [];
      payload.files = injection.files || [];
      if (typeof injection.func === "function") { payload.code = injection.func.toString(); }
      else if (typeof injection.code === "string") { payload.code = "function(){" + injection.code + "}"; }
    }
    return runScript(payload, callback);
  };
  chrome.scripting.insertCSS = chrome.scripting.insertCSS || function(injection, callback){
    var request_ = request();
    call("insertCss", [TOKEN, EXT, request_.id, json(injection || {})], "");
    return answer(request_.promise, callback);
  };
  chrome.action = chrome.action || {};
  chrome.action.setBadgeText = chrome.action.setBadgeText || function(){};
  chrome.action.setBadgeBackgroundColor = chrome.action.setBadgeBackgroundColor || function(){};
  chrome.action.setIcon = chrome.action.setIcon || function(){};
  chrome.action.setTitle = chrome.action.setTitle || function(){};
  chrome.action.openPopup = chrome.action.openPopup || function(){};
  chrome.action.onClicked = chrome.action.onClicked || { addListener: function(){} };
  chrome.browserAction = chrome.browserAction || chrome.action;
  chrome.pageAction = chrome.pageAction || chrome.action;
  chrome.commands = chrome.commands || { onCommand: { addListener: function(){} } };
  chrome.contextMenus = chrome.contextMenus || {
    create: function(){}, update: function(){}, remove: function(){}, removeAll: function(){},
    onClicked: { addListener: function(){} }
  };
  chrome.notifications = chrome.notifications || {
    create: function(){}, clear: function(){}, onClicked: { addListener: function(){} }
  };
  chrome.permissions = chrome.permissions || {
    contains: function(permissions, callback){ if (typeof callback === "function") { callback(true); } return Promise.resolve(true); },
    request: function(permissions, callback){ if (typeof callback === "function") { callback(true); } return Promise.resolve(true); },
    remove: function(permissions, callback){ if (typeof callback === "function") { callback(true); } return Promise.resolve(true); },
    getAll: function(callback){ var all = { permissions: [], origins: [] }; if (typeof callback === "function") { callback(all); } return Promise.resolve(all); }
  };
  chrome.declarativeNetRequest = chrome.declarativeNetRequest || {
    getDynamicRules: function(callback){ if (typeof callback === "function") { callback([]); } return Promise.resolve([]); },
    updateDynamicRules: function(rules, callback){ if (typeof callback === "function") { callback(); } return Promise.resolve(); },
    isRegexSupported: function(regex, callback){ if (typeof callback === "function") { callback({ isSupported: false }); } return Promise.resolve({ isSupported: false }); }
  };
  chrome.omnibox = chrome.omnibox || { setDefaultSuggestion: function(){}, onInputChanged: { addListener: function(){} } };
  chrome.windows = chrome.windows || {
    getCurrent: function(callback){ var w = { id: 1, focused: true, type: "normal" }; if (typeof callback === "function") { callback(w); } return Promise.resolve(w); },
    getAll: function(callback){ var l = [{ id: 1, focused: true, type: "normal" }]; if (typeof callback === "function") { callback(l); } return Promise.resolve(l); }
  };
  chrome.webNavigation = chrome.webNavigation || {
    getAllFrames: function(details, callback){ if (typeof callback === "function") { callback([]); } return Promise.resolve([]); },
    onCommitted: { addListener: function(){} }, onCompleted: { addListener: function(){} }
  };
  // A popup closes itself with window.close(), as it does in Chrome.
  var nativeClose = window.close;
  window.close = function(){
    call("closePopup", [TOKEN], "");
    try { if (nativeClose) { nativeClose.call(window); } } catch(e){}
  };
  // How big the popup wants to be; the browser sizes its window with this.
  // 18.0.0 beta 9 (item 8): the size the popup asks for is read from what the extension itself
  // wrote — a `min-width` on `html` or on `body`, a fixed `width`, the scroll size — and not from
  // the viewport, which is only as wide as the bubble happens to be and used to make every popup
  // look the same size. The widest of them is the width the popup wants.
  window.__explorerPopupSize = function(){
    try {
      var root = document.documentElement;
      var body = document.body;
      function px(value){ var n = parseFloat(value); return isNaN(n) ? 0 : n; }
      function asked(node, which){
        if (!node || !window.getComputedStyle) { return 0; }
        var style = window.getComputedStyle(node);
        return Math.ceil(Math.max(
          px(style[which]),
          px(style["min-" + which]) || 0
        ));
      }
      var w = Math.ceil(Math.max(
        root ? root.scrollWidth : 0, body ? body.scrollWidth : 0,
        asked(root, "width"), asked(body, "width"),
        root ? Math.ceil(root.getBoundingClientRect().width) : 0,
        body ? Math.ceil(body.getBoundingClientRect().width) : 0
      ));
      var h = Math.ceil(Math.max(
        root ? root.scrollHeight : 0, body ? body.scrollHeight : 0,
        asked(root, "height"), asked(body, "height"),
        body ? Math.ceil(body.getBoundingClientRect().height) : 0
      ));
      // The bubble is told again whenever the popup grows — a picture that arrives late, a list
      // that is filled in — so a popup that is taller than its first paint is shown whole.
      call("popupSize", [TOKEN, String(w), String(h)], "");
    } catch(e){}
  };
  window.browser = window.browser || chrome;
  window.browser.runtime = chrome.runtime;
  window.browser.storage = chrome.storage;
  window.browser.tabs = chrome.tabs;
  window.browser.scripting = chrome.scripting;
})("__TOKEN__");
"""
}
