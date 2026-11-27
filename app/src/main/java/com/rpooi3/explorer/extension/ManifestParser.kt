/*
 * Explorer: reads the manifest.json of an unpacked extension.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.extension

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Turns the `manifest.json` of an unpacked extension into a [WebExtension]: the name and the
 * version, the popup (Manifest V3 `action` or V2 `browser_action`), the options page, the icon,
 * the permissions, and the rules of the content scripts.
 *
 * What Explorer does not run is noted and left out: the declarativeNetRequest rules and
 * web-accessible resources. The background — a service worker in Manifest V3, scripts or a page
 * before it — is read and run in a WebView of its own since 17.3.4.
 */
object ManifestParser {

    /** Reads `manifest.json` of [dir]; null if it is missing or cannot be read. */
    @JvmStatic
    fun parse(id: String, dir: File): WebExtension? {
        val manifest = File(dir, "manifest.json")
        if (!manifest.isFile) return null
        val root = try {
            JSONObject(manifest.readText())
        } catch (e: Exception) {
            return null
        }

        // Chrome needs both of them; without them the file is no extension of the store.
        if (!root.has("name") || !root.has("manifest_version")) return null

        // The popup: "action" since Manifest V3, "browser_action" before it, and "page_action"
        // in the old Firefox-and-Chrome extensions that Explorer still does not know.
        val action = root.optJSONObject("action") ?: root.optJSONObject("browser_action")
        val popup = action?.optString("default_popup")?.takeUnless { it.isNullOrBlank() }
        val options = root.optJSONObject("options_ui")?.optString("page")?.takeUnless { it.isNullOrBlank() }
            ?: root.optString("options_page").takeUnless { it.isNullOrBlank() }
        // 17.3.4: the background, which Explorer runs in a hidden WebView of its own.
        val background = root.optJSONObject("background")
        val backgroundPath = cleanPath(
            background?.optString("service_worker")?.takeUnless { it.isNullOrBlank() }
                ?: background?.optJSONArray("scripts")?.optString(0)?.takeUnless { it.isNullOrBlank() }
                ?: background?.optString("page")?.takeUnless { it.isNullOrBlank() },
        )

        val newTab = root.optJSONObject("chrome_url_overrides")?.optString("newtab")
            ?.takeUnless { it.isNullOrBlank() }

        // 17.4.1: a published extension keeps its name and its description in `_locales` and puts
        // `__MSG_key__` in the manifest. The placeholder is resolved here, once, so no screen and
        // no sheet ever shows it; without a message for it the id stands in for the name.
        val name = Locales.resolveIn(dir, root, root.optString("name", ""), id).ifBlank { id }
        val description = Locales.resolveIn(dir, root, root.optString("description", ""))

        // 18.0.0 beta 8 (item 5): before Manifest V3 the sites an extension asked for stood in
        // `permissions` beside the interfaces (`"permissions": ["*://*/*", "storage"]`), not in a
        // `host_permissions` list of their own. They are told apart here, so that an extension of
        // that kind counts for the sites it names — in the menu's badge, in the question of the
        // install, and in what runs on a page.
        val declared = strings(root.optJSONArray("permissions"))
        val apiPermissions = declared.filterNot { isHostPattern(it) }
        val hostPatterns = (
            strings(root.optJSONArray("host_permissions")) +
                declared.filter { isHostPattern(it) }
            ).distinct()

        return WebExtension(
            id = id,
            name = name,
            version = root.optString("version", ""),
            description = description,
            rootDir = dir.absolutePath,
            popupPath = cleanPath(popup),
            optionsPath = cleanPath(options),
            iconPath = cleanPath(icon(root, action)),
            permissions = apiPermissions,
            hostPermissions = hostPatterns,
            contentScripts = contentScripts(root.optJSONArray("content_scripts")),
            manifestVersion = root.optInt("manifest_version", 2),
            backgroundPath = backgroundPath,
            newTabPath = cleanPath(newTab),
        )
    }

    private fun contentScripts(array: JSONArray?): List<WebExtension.ContentScriptRule> {
        if (array == null) return emptyList()
        val rules = ArrayList<WebExtension.ContentScriptRule>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val matches = strings(item.optJSONArray("matches"))
            if (matches.isEmpty()) continue
            val js = strings(item.optJSONArray("js"))
            val css = strings(item.optJSONArray("css"))
            if (js.isEmpty() && css.isEmpty()) continue
            rules += WebExtension.ContentScriptRule(
                matches = matches,
                jsFiles = js,
                cssFiles = css,
                runAt = item.optString("run_at", WebExtension.RUN_AT_IDLE).let {
                    when (it) {
                        WebExtension.RUN_AT_START, WebExtension.RUN_AT_END -> it
                        else -> WebExtension.RUN_AT_IDLE
                    }
                },
            )
        }
        return rules
    }

    /**
     * The icon of the extension: the picture of the action, else the largest of the manifest's
     * icons. A file that is not there stays out.
     */
    private fun icon(root: JSONObject, action: JSONObject?): String? {
        action?.optString("default_icon")?.takeUnless { it.isNullOrBlank() }?.let { return it }
        val icons = root.optJSONObject("icons") ?: return null
        val sizes = icons.keys().asSequence().mapNotNull { it.toIntOrNull()?.to(it) }.sortedByDescending { it.first }
        for ((_, key) in sizes) {
            val path = icons.optString(key)
            if (!path.isNullOrBlank()) return path
        }
        return null
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it)?.takeIf { text -> text.isNotBlank() } }
    }

    /** Whether a permission of a manifest names sites rather than an interface. */
    private fun isHostPattern(permission: String): Boolean =
        permission == "<all_urls>" || permission.contains("://") || permission.startsWith("file:")

    /** A path inside the extension; null for an address that would leave it. */
    private fun cleanPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.contains("://")) return null
        val clean = path.removePrefix("/").removePrefix("./")
        if (clean.isEmpty() || clean.contains("..")) return null
        return clean
    }
}
