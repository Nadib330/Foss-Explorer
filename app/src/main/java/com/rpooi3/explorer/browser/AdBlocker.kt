/*
 * Explorer: the ad blocker, after Chromite's.
 * Own code, GPL-3.0.
 *
 * Chromite is Chromium with an ad blocker built into it, and it works on two levels: a request
 * whose url a filter list matches is answered empty before it leaves the browser, and an element
 * a list names is hidden by a style sheet written into the page before the page is shown. Explorer
 * does the same two things in the one place a WebView offers them: shouldInterceptRequest for the
 * requests, and a script that runs at document start for the style.
 *
 * The lists are Adblock Plus lists (EasyList, EasyPrivacy) and the hosts file Explorer always
 * fetched. FOSS Browser's blocker — a file of bare host names compared whole — is gone, and with
 * it the reason nothing was ever blocked: that file was never in the app.
 */
package com.rpooi3.explorer.browser

import com.rpooi3.explorer.profile.ProfilePrefs
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.preference.PreferenceManager
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object AdBlocker {

    private const val TAG = "AdBlocker"

    /** The preference of the lists the user picked, as a set of ids. */
    const val KEY_LISTS = "adblock_lists"

    /** Per-list file stamps, so that "last update" says something true about the list in force. */
    private const val KEY_STAMP_PREFIX = "adblock_stamp_"

    private const val DIRECTORY = "adblock"

    /** One filter list: where it comes from and what it is called. */
    class Source(val id: String, val url: String)

    /** The lists first turned on, which are the two Chromite ships with, plus the hosts file. */
    @JvmField
    val SOURCES = listOf(
        Source("easylist", "https://easylist.to/easylist/easylist.txt"),
        Source("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        Source("stevenblack", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
    )

    @JvmField
    val DEFAULT_LISTS = setOf("easylist", "easyprivacy")

    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    @Volatile
    private var filter: AdFilter? = null

    @Volatile
    private var loadedFor: String? = null

    /** How many requests this run has answered empty, for the panel that reports it. */
    @Volatile
    var blocked: Long = 0
        private set

    @Volatile
    private var blockedCount: Long = 0

    // Settings ------------------------------------------------------------------------------

    /** The lists in force: what the user picked, or the two that are on out of the box. */
    @JvmStatic
    fun lists(context: Context): Set<String> {
        val prefs = ProfilePrefs.settings(context.applicationContext)
        val stored = prefs.getStringSet(KEY_LISTS, null) ?: return DEFAULT_LISTS
        val known = SOURCES.map { it.id }.toSet()
        val picked = stored.filter { it in known }.toSet()
        return picked.ifEmpty { DEFAULT_LISTS }
    }

    /** Whether a list is in force. */
    @JvmStatic
    fun isOn(context: Context, id: String): Boolean = id in lists(context)

    /** Turns a list on or off, and rebuilds what the blocker matches with. */
    @JvmStatic
    fun setOn(context: Context, id: String, on: Boolean) {
        val app = context.applicationContext
        val next = lists(app).toMutableSet()
        if (on) next.add(id) else next.remove(id)
        ProfilePrefs.settings(app).edit()
            .putStringSet(KEY_LISTS, next).apply()
        ensure(app, force = true)
    }

    /** When the lists in force were last written, as "12 Nov 2026"; empty if never. */
    @JvmStatic
    fun listDate(context: Context): String {
        val prefs = ProfilePrefs.settings(context.applicationContext)
        var newest = 0L
        for (id in lists(context)) {
            val stamp = prefs.getLong(KEY_STAMP_PREFIX + id, 0L)
            if (stamp > newest) newest = stamp
        }
        if (newest == 0L) return ""
        return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(newest))
    }

    /** How many rules the lists in force carry, for the screen that lists them. */
    @JvmStatic
    fun ruleCount(): Int = filter?.let { it.networkRuleCount + it.cosmeticRuleCount } ?: 0

    // Loading -------------------------------------------------------------------------------

    /**
     * Makes the blocker ready. Called where the browser starts: the lists are read from the files
     * they were saved as, and fetched when there is no file yet or the file is a day old.
     */
    @JvmStatic
    @JvmOverloads
    fun ensure(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val wanted = lists(app).sorted().joinToString(",")
        val daily = !ProfilePrefs.settings(app)
            .getBoolean("sp_savedata", false)
        val stale = isStale(app, daily)
        if (!force && filter != null && wanted == loadedFor && !stale) return
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                if (stale || force || !hasFiles(app)) download(app)
                load(app, wanted)
            } catch (e: Throwable) {
                Log.w(TAG, "Could not prepare the lists", e)
            } finally {
                busy.set(false)
            }
        }
    }

    /** Fetches every list in force now and rebuilds the engine with them. */
    @JvmStatic
    fun update(context: Context) {
        val app = context.applicationContext
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                download(app)
                load(app, lists(app).sorted().joinToString(","))
            } catch (e: Throwable) {
                Log.w(TAG, "Could not update the lists", e)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun fileOf(context: Context, id: String): File = File(directory(context), "$id.txt")

    private fun hasFiles(context: Context): Boolean =
        lists(context).any { fileOf(context, it).isFile }

    /** Whether the lists in force were written longer ago than a day (a week on data saver). */
    private fun isStale(context: Context, daily: Boolean): Boolean {
        val days = if (daily) 1 else 7
        val limit = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        for (id in lists(context)) {
            val file = fileOf(context, id)
            if (!file.isFile) return true
            if (file.lastModified() < limit) return true
        }
        return false
    }

    private fun download(context: Context) {
        val prefs = ProfilePrefs.settings(context)
        var wrote = false
        for (source in SOURCES) {
            if (source.id !in lists(context)) continue
            val target = fileOf(context, source.id)
            val temp = File(directory(context), source.id + ".part")
            try {
                Log.i(TAG, "Fetching " + source.url)
                val connection = URL(source.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Explorer-Android")
                connection.inputStream.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output, 1 shl 15) }
                }
                if (temp.length() < 1024) {
                    Log.w(TAG, source.id + ": the answer is too short to be a list")
                    temp.delete()
                    continue
                }
                if (target.exists()) target.delete()
                if (temp.renameTo(target)) {
                    prefs.edit().putLong(KEY_STAMP_PREFIX + source.id, System.currentTimeMillis()).apply()
                    wrote = true
                    Log.i(TAG, source.id + ": " + target.length() + " bytes")
                } else {
                    temp.delete()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not fetch " + source.id, e)
                temp.delete()
            }
        }
        if (!wrote) Log.w(TAG, "No list was fetched; the lists of the last update stay in force")
    }

    /** Reads the lists in force from disk and swaps in the parsed engine. */
    private fun load(context: Context, key: String) {
        val files = lists(context).map { fileOf(context, it) }.filter { it.isFile }
        if (files.isEmpty()) {
            filter = AdFilter.EMPTY
            loadedFor = key
            Log.w(TAG, "No list on disk; nothing is blocked until one is fetched")
            return
        }
        val started = System.currentTimeMillis()
        val rules = sequence {
            for (file in files) {
                file.bufferedReader().useLines { lines -> yieldAll(lines) }
            }
        }
        val parsed = AdFilter.parse(rules)
        filter = parsed
        loadedFor = key
        Log.i(
            TAG,
            "Loaded " + parsed.networkRuleCount + " rules and " + parsed.cosmeticRuleCount +
                " element rules in " + (System.currentTimeMillis() - started) + " ms",
        )
    }

    // Blocking ------------------------------------------------------------------------------

    /** The engine; never null, and empty until a list has been read. */
    @JvmStatic
    fun filter(): AdFilter = filter ?: AdFilter.EMPTY

    /**
     * Whether a request of [pageHost]'s page must be answered empty. The kind of request is read
     * from the headers the WebView sends, so a rule that only speaks of scripts stays off images.
     */
    @JvmStatic
    fun shouldBlock(url: String, pageHost: String?, request: WebResourceRequest?): Boolean {
        val engine = filter ?: return false
        if (engine.isEmpty) return false
        val kind = resourceType(request)
        val thirdParty = pageHost?.let { AdFilter.hostOf(url)?.let { host -> !sameSite(host, it) } }
        val hits = engine.isBlocked(url, pageHost, kind, thirdParty)
        if (hits) {
            blockedCount += 1
            blocked = blockedCount
        }
        return hits
    }

    /** The answer to a blocked request: an empty body of the right kind. */
    @JvmStatic
    fun emptyResponse(url: String): WebResourceResponse {
        val mime = when {
            url.endsWith(".js") -> "application/javascript"
            url.endsWith(".css") -> "text/css"
            url.endsWith(".png") -> "image/png"
            url.endsWith(".gif") -> "image/gif"
            url.endsWith(".svg") -> "image/svg+xml"
            url.endsWith(".webp") -> "image/webp"
            else -> "text/plain"
        }
        return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /** What Chromium calls the request: script, image, stylesheet, and so on. */
    private fun resourceType(request: WebResourceRequest?): String? {
        if (request == null) return null
        val dest = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Sec-Fetch-Dest", true) || it.key.equals("sec-fetch-dest", true) }
            ?.value
            ?.lowercase()
        when (dest) {
            "script" -> return "script"
            "image" -> return "image"
            "style" -> return "stylesheet"
            "font" -> return "font"
            "document" -> return "document"
            "iframe", "frame" -> return "subdocument"
            "audio", "video", "track" -> return "media"
            "empty" -> return "xmlhttprequest"
            null, "" -> Unit
            else -> return "other"
        }
        val accept = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Accept", true) }
            ?.value
            ?.lowercase()
            ?: return null
        return when {
            accept.startsWith("image/") -> "image"
            accept.contains("text/css") -> "stylesheet"
            accept.contains("text/html") -> "document"
            accept.contains("javascript") -> "script"
            accept.contains("font") -> "font"
            accept.contains("video/") || accept.contains("audio/") -> "media"
            accept == "*/*" -> "xmlhttprequest"
            else -> "other"
        }
    }

    /** Whether two hosts belong to the same site, in the way `$third-party` means it. */
    private fun sameSite(first: String, second: String): Boolean {
        if (first == second) return true
        val a = registrable(first)
        val b = registrable(second)
        return a != null && a == b
    }

    /** The last two labels of a host, or three where the second is a known suffix like "co.uk". */
    private fun registrable(host: String): String? {
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return host
        val twoLabelSuffixes = setOf(
            "co.uk", "org.uk", "ac.uk", "gov.uk", "co.jp", "ne.jp", "or.jp", "com.au", "net.au",
            "org.au", "co.nz", "co.in", "com.br", "com.mx", "com.tr", "co.za", "com.cn", "com.sg",
            "com.hk", "co.kr", "com.ar", "com.pl", "com.ua", "com.bd", "co.id", "com.my", "com.ph",
        )
        val tail = labels.takeLast(2).joinToString(".")
        return if (tail in twoLabelSuffixes && labels.size >= 3) {
            labels.takeLast(3).joinToString(".")
        } else {
            tail
        }
    }

    // Element hiding ------------------------------------------------------------------------

    /** The style Explorer writes into a page of [pageHost]; empty when no rule names it. */
    @JvmStatic
    fun cosmeticCss(pageHost: String?): String {
        val css = filter?.cosmeticCss(pageHost) ?: return ""
        // A WebView's evaluateJavascript takes a string of a sane size; the lists fit with room.
        return if (css.length > 200_000) css.substring(0, css.lastIndexOf(',', 200_000)) else css
    }

    /** The script that writes [cosmeticCss] into the page as a style element, at document start. */
    @JvmStatic
    fun cosmeticScript(pageHost: String?): String {
        val css = cosmeticCss(pageHost)
        if (css.isEmpty()) return ""
        return """
(function(){
  var css = ${JSONObject.quote(css)};
  function add(){
    try {
      var style = document.createElement('style');
      style.setAttribute('data-explorer-adblock', '');
      style.textContent = css;
      (document.head || document.documentElement).appendChild(style);
    } catch (e) {}
  }
  if (document.documentElement) { add(); }
  document.addEventListener('DOMContentLoaded', add);
})();
"""
    }

    /**
     * Hides the elements a list names, before the page is painted: a document-start script where
     * the WebView has one, the page callback otherwise. Coming twice costs nothing.
     */
    @JvmStatic
    fun applyCosmetic(webView: android.webkit.WebView, pageHost: String?) {
        val script = cosmeticScript(pageHost)
        if (script.isEmpty()) return
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
            } else {
                webView.evaluateJavascript(script, null)
            }
        } catch (e: Throwable) {
            Log.i(TAG, "Element hiding was not applied: " + e)
        }
    }

    /** The browser reports the count it has blocked since it started; used by the panel. */
    @JvmStatic
    fun resetCount() {
        blockedCount = 0
        blocked = 0
    }

    /** For a test: the lists are held in memory, so a unit test can put its own list in place. */
    @JvmStatic
    fun installForTest(engine: AdFilter) {
        filter = engine
        loadedFor = "test"
    }

    /** For a test: Android's Build is only read through the version check of the lists. */
    private fun sdk(): Int = Build.VERSION.SDK_INT
}
