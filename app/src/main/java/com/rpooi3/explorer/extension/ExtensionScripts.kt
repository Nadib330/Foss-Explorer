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
    private const val MAX_FILE_BYTES = 1L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 3L * 1024 * 1024

    private val patterns = ConcurrentHashMap<String, Regex>()

    /** The address of a file of [extensionId] on Explorer's own extension server. */
    @JvmStatic
    fun resourceUrl(extensionId: String, path: String): String =
        "https://$DOMAIN/$extensionId/" + path.trimStart('/')

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
        // The guard makes the script safe to run in both page callbacks: if it already ran in
        // this document, the second run does nothing. Should the first run have landed before
        // the document was there, the second one finds no guard and does the work.
        val guardKey = "'__explorerExt__" + extension.id + "__" + runAt + "__' + location.href"
        return "(function(){var K=" + guardKey + ";if(window[K]){return;}window[K]=1;\n" +
            shim(extension.id, token) + body + "})();"
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
  function parse(text, fallback){ try { return JSON.parse(text); } catch(e){ return fallback; } }
  function json(value){ try { return JSON.stringify(value === undefined ? null : value); } catch(e){ return "null"; } }
  function b1(fn, arg, fallback){
    try { return (bridge && typeof bridge[fn] === "function") ? bridge[fn](TOKEN, arg) : fallback; }
    catch(e){ return fallback; }
  }
  function b2(fn, arg, extra, fallback){
    try { return (bridge && typeof bridge[fn] === "function") ? bridge[fn](TOKEN, arg, extra) : fallback; }
    catch(e){ return fallback; }
  }
  function readArea(kind){
    return parse(kind === "session" ? b1("sessionGet", EXT, "{}") : b1("storageGet", EXT, "{}"), {});
  }
  function writeArea(kind, data){
    if (kind === "session") { b2("sessionSet", EXT, json(data), ""); }
    else { b2("storageSet", EXT, json(data), ""); }
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
      getBytesInUse: function(keys, callback){ if (typeof callback === "function") callback(0); return Promise.resolve(0); },
      onChanged: { addListener: function(){}, removeListener: function(){} }
    };
  }
  var listeners = [];
  var chrome = window.chrome || {};
  window.chrome = chrome;
  chrome.runtime = chrome.runtime || {};
  chrome.runtime.id = EXT;
  chrome.runtime.getURL = function(path){
    return "https://$DOMAIN/" + EXT + "/" + String(path || "").replace(/^\/+/, "");
  };
  chrome.runtime.getManifest = function(){ return parse(b1("getManifest", EXT, "{}"), {}); };
  chrome.runtime.lastError = null;
  chrome.runtime.sendMessage = function(message, options, callback){
    var answer = parse(b2("sendMessage", EXT, json(message), "null"), null);
    if (answer === null) { answer = undefined; }
    for (var i = 0; i < listeners.length; i++) {
      try {
        var reply = listeners[i](message, {}, function(){});
        if (reply !== undefined) { answer = reply; }
      } catch(e){}
    }
    if (typeof options === "function") { options(answer); }
    else if (typeof callback === "function") { callback(answer); }
    return Promise.resolve(answer);
  };
  chrome.runtime.onMessage = {
    addListener: function(fn){ if (typeof fn === "function") { listeners.push(fn); } },
    removeListener: function(fn){ var i = listeners.indexOf(fn); if (i >= 0) { listeners.splice(i, 1); } },
    hasListener: function(fn){ return listeners.indexOf(fn) >= 0; }
  };
  chrome.runtime.onInstalled = { addListener: function(){} };
  chrome.runtime.onStartup = { addListener: function(){} };
  chrome.runtime.connect = function(){
    return {
      postMessage: function(){}, disconnect: function(){},
      onMessage: { addListener: function(){} },
      onDisconnect: { addListener: function(){} }
    };
  };
  chrome.runtime.openOptionsPage = function(){ b1("openOptions", EXT, ""); };
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
  chrome.tabs = chrome.tabs || {
    create: function(options){ if (options && options.url) { b1("openTab", String(options.url), ""); } },
    update: function(id, options){ if (options && options.url) { b1("openTab", String(options.url), ""); } },
    query: function(info, callback){ if (typeof callback === "function") { callback([]); } return Promise.resolve([]); },
    sendMessage: function(){ return Promise.resolve(); },
    onUpdated: { addListener: function(){} },
    onRemoved: { addListener: function(){} }
  };
  chrome.action = chrome.action || {};
  chrome.action.setBadgeText = chrome.action.setBadgeText || function(){};
  chrome.action.setBadgeBackgroundColor = chrome.action.setBadgeBackgroundColor || function(){};
  chrome.action.setIcon = chrome.action.setIcon || function(){};
  chrome.action.setTitle = chrome.action.setTitle || function(){};
  chrome.action.onClicked = chrome.action.onClicked || { addListener: function(){} };
  chrome.browserAction = chrome.browserAction || chrome.action;
  chrome.pageAction = chrome.pageAction || chrome.action;
  chrome.scripting = chrome.scripting || { executeScript: function(){ return Promise.resolve([]); } };
  chrome.commands = chrome.commands || { onCommand: { addListener: function(){} } };
  chrome.contextMenus = chrome.contextMenus || { create: function(){}, removeAll: function(){}, onClicked: { addListener: function(){} } };
  chrome.notifications = chrome.notifications || { create: function(){} };
  window.browser = window.browser || chrome;
  window.browser.runtime = chrome.runtime;
  window.browser.storage = chrome.storage;
})("__TOKEN__");
"""
}
