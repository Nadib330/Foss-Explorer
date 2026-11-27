/*
 * Explorer: WebExtensions from the Chrome Web Store.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.extension

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * An extension Explorer can run: what its `manifest.json` says, and where its files are.
 *
 * The Android System WebView knows no `chrome.*` interfaces, so Explorer is the host: it
 * unpacks the extension (see [CrxExtractor]), runs its content scripts and stylesheets in the
 * tab (see [ExtensionScripts]) and serves its popup to a WebView of its own (see
 * [ExtensionPopup]).
 */
class WebExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    /** Where the unpacked files are: `filesDir/extensions/<id>`. */
    val rootDir: String,
    /** The popup, relative to [rootDir]; null if the extension has none. */
    val popupPath: String?,
    /** The options page, relative to [rootDir]; null if the extension has none. */
    val optionsPath: String?,
    /** The icon, relative to [rootDir]; null if the manifest names none. */
    val iconPath: String?,
    val permissions: List<String>,
    val hostPermissions: List<String>,
    val contentScripts: List<ContentScriptRule>,
    val manifestVersion: Int,
) {

    /** A `content_scripts` entry of the manifest. */
    class ContentScriptRule(
        val matches: List<String>,
        val jsFiles: List<String>,
        val cssFiles: List<String>,
        /** `document_start`, `document_end` or `document_idle`. */
        val runAt: String,
    ) {
        /** Whether this rule asks for [url]. */
        fun appliesTo(url: String): Boolean = matches.any { ExtensionScripts.matches(it, url) }

        /** What the extension settings show next to the name. */
        fun sitesSummary(): String = when {
            matches.isEmpty() -> ""
            matches.size == 1 -> matches.first()
            else -> matches.joinAtMost(2)
        }
    }

    /** The files of this extension. */
    val root: File get() = File(rootDir)

    /** Whether this extension changes any page (as opposed to only having a popup). */
    val hasContentScripts: Boolean get() = contentScripts.isNotEmpty()

    /** Whether [url] is a page this extension asks for. */
    fun worksOn(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (contentScripts.any { it.appliesTo(url) }) return true
        // An extension without content_scripts that names host permissions still counts as
        // "for this site" in the menu, so its row can say so.
        return hostPermissions.any { ExtensionScripts.matches(it, url) }
    }

    /** The popup as a page of Explorer's extension server, or null. */
    fun popupUrl(): String? = popupPath?.let { ExtensionScripts.resourceUrl(id, it) }

    /** The options page as a page of Explorer's extension server, or null. */
    fun optionsUrl(): String? = optionsPath?.let { ExtensionScripts.resourceUrl(id, it) }

    /** The icon, if the manifest names one and the file is there. */
    fun iconFile(): File? = iconPath?.let { path ->
        File(root, path).takeIf { it.isFile && it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    /** What is kept for the settings and the menu. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("version", version)
        put("description", description)
        put("root", rootDir)
        put("popup", popupPath ?: JSONObject.NULL)
        put("options", optionsPath ?: JSONObject.NULL)
        put("icon", iconPath ?: JSONObject.NULL)
        put("permissions", JSONArray(permissions))
        put("hosts", JSONArray(hostPermissions))
        put("mv", manifestVersion)
        put("rules", JSONArray().apply {
            contentScripts.forEach { rule ->
                put(
                    JSONObject().apply {
                        put("matches", JSONArray(rule.matches))
                        put("js", JSONArray(rule.jsFiles))
                        put("css", JSONArray(rule.cssFiles))
                        put("runAt", rule.runAt)
                    },
                )
            }
        })
    }

    companion object {
        /** Reads back what [toJson] wrote. */
        @JvmStatic
        fun fromJson(json: JSONObject): WebExtension {
            fun strings(array: JSONArray?): List<String> {
                if (array == null) return emptyList()
                return (0 until array.length()).map { array.optString(it) }.filter { it.isNotEmpty() }
            }
            val rules = ArrayList<ContentScriptRule>()
            val rulesJson = json.optJSONArray("rules")
            if (rulesJson != null) {
                for (i in 0 until rulesJson.length()) {
                    val rule = rulesJson.optJSONObject(i) ?: continue
                    rules += ContentScriptRule(
                        matches = strings(rule.optJSONArray("matches")),
                        jsFiles = strings(rule.optJSONArray("js")),
                        cssFiles = strings(rule.optJSONArray("css")),
                        runAt = rule.optString("runAt", RUN_AT_IDLE),
                    )
                }
            }
            return WebExtension(
                id = json.optString("id"),
                name = json.optString("name"),
                version = json.optString("version"),
                description = json.optString("description", ""),
                rootDir = json.optString("root"),
                popupPath = json.optString("popup").takeUnless { it.isEmpty() || it == "null" },
                optionsPath = json.optString("options").takeUnless { it.isEmpty() || it == "null" },
                iconPath = json.optString("icon").takeUnless { it.isEmpty() || it == "null" },
                permissions = strings(json.optJSONArray("permissions")),
                hostPermissions = strings(json.optJSONArray("hosts")),
                contentScripts = rules,
                manifestVersion = json.optInt("mv", 2),
            )
        }

        private fun List<String>.joinAtMost(count: Int): String =
            if (size <= count) joinToString(", ") else take(count).joinToString(", ") + " …"

        const val RUN_AT_START = "document_start"
        const val RUN_AT_END = "document_end"
        const val RUN_AT_IDLE = "document_idle"
    }
}
