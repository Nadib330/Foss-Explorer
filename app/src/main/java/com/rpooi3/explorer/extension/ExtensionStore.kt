/*
 * Explorer: which extensions are installed, and where their files are.
 * Own code, GPL-3.0.
 *
 * The list is kept as JSON in one preference file, so it survives a restart and can be read
 * without unpacking anything. The files of an extension live in its own folder below
 * `files/extensions/<id>/`, and nothing there is ever written to by a page.
 */
package com.rpooi3.explorer.extension

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ExtensionStore {

    private const val TAG = "ExplorerExtensions"
    private const val PREFS = "extensions"
    private const val KEY_LIST = "extensions_list"
    private const val KEY_ENABLED = "extensions_enabled"

    /** `files/extensions`, where every extension has a folder named after its id. */
    @JvmStatic
    fun directory(context: Context): File = File(context.filesDir, "extensions")

    /** The folder of [extensionId]. */
    @JvmStatic
    fun directoryOf(context: Context, extensionId: String): File =
        File(directory(context), extensionId)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What is installed, in the order it was installed. */
    @JvmStatic
    fun installed(context: Context): List<WebExtension> {
        val text = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            val list = ArrayList<WebExtension>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val extension = WebExtension.fromJson(item)
                // A folder that was removed under us does not count as installed.
                if (File(extension.rootDir).isDirectory) list.add(extension)
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read the list: " + e)
            emptyList()
        }
    }

    /** The installed extensions that are turned on. */
    @JvmStatic
    fun enabled(context: Context): List<WebExtension> {
        val on = prefs(context).getStringSet(KEY_ENABLED, null) ?: return installed(context)
        return installed(context).filter { on.contains(it.id) }
    }

    /** The installed extensions that have something to say about [url] and are turned on. */
    @JvmStatic
    fun forUrl(context: Context, url: String?): List<WebExtension> {
        if (url.isNullOrBlank()) return emptyList()
        return enabled(context).filter { it.worksOn(url) }
    }

    @JvmStatic
    fun find(context: Context, extensionId: String): WebExtension? =
        installed(context).firstOrNull { it.id == extensionId }

    /** Whether [extensionId] is turned on; an extension nobody turned off counts as on. */
    @JvmStatic
    fun isEnabled(context: Context, extensionId: String): Boolean {
        val on = prefs(context).getStringSet(KEY_ENABLED, null) ?: return find(context, extensionId) != null
        return on.contains(extensionId)
    }

    @JvmStatic
    fun setEnabled(context: Context, extensionId: String, enabled: Boolean) {
        val installed = installed(context).map { it.id }.toMutableSet()
        val current = prefs(context).getStringSet(KEY_ENABLED, null)?.toMutableSet()
            ?: installed.toMutableSet()
        if (enabled) current.add(extensionId) else current.remove(extensionId)
        prefs(context).edit().putStringSet(KEY_ENABLED, current).apply()
    }

    /** Adds [extension], replacing an older copy of the same id. */
    @JvmStatic
    fun add(context: Context, extension: WebExtension) {
        val list = installed(context).filter { it.id != extension.id } + extension
        write(context, list)
        val current = prefs(context).getStringSet(KEY_ENABLED, null)?.toMutableSet()
            ?: installed(context).map { it.id }.toMutableSet()
        current.add(extension.id)
        prefs(context).edit().putStringSet(KEY_ENABLED, current).apply()
    }

    /** Removes [extensionId] and its files, and forgets its storage. */
    @JvmStatic
    fun remove(context: Context, extensionId: String) {
        val list = installed(context).filter { it.id != extensionId }
        write(context, list)
        val current = prefs(context).getStringSet(KEY_ENABLED, null)?.toMutableSet() ?: mutableSetOf()
        current.remove(extensionId)
        prefs(context).edit().putStringSet(KEY_ENABLED, current).apply()
        context.getSharedPreferences("extension_storage", Context.MODE_PRIVATE)
            .edit().remove("ext_" + extensionId).apply()
        context.getSharedPreferences("extension_session", Context.MODE_PRIVATE)
            .edit().remove("ext_" + extensionId).apply()
        try {
            directoryOf(context, extensionId).deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot remove the files: " + e)
        }
        // The .crx that was downloaded is of no use afterwards.
        File(context.cacheDir, "$extensionId.crx").delete()
    }

    private fun write(context: Context, list: List<WebExtension>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply()
    }

    /**
     * Unpacks [crx] into the folder of [extensionId] and adds it to the list.
     *
     * @return the extension, or null if it could not be read
     */
    @JvmStatic
    fun install(context: Context, crx: File, extensionId: String): WebExtension? {
        if (extensionId.isBlank() || !extensionId.matches(Regex("[a-p]{32}"))) {
            Log.w(TAG, "Not an extension id: " + extensionId)
            return null
        }
        val target = directoryOf(context, extensionId)
        return try {
            CrxExtractor.unpack(crx, target)
            val parsed = ManifestParser.parse(extensionId, target) ?: run {
                target.deleteRecursively()
                return null
            }
            add(context, parsed)
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "Cannot install " + extensionId + ": " + e)
            target.deleteRecursively()
            null
        }
    }

    /** The manifest of [extensionId] as the shim hands it to chrome.runtime.getManifest(). */
    @JvmStatic
    fun manifestJson(context: Context, extensionId: String): JSONObject? =
        find(context, extensionId)?.toJson()
}
