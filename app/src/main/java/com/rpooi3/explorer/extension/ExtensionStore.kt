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

    /**
     * The folder of the extensions of the profile in front: `files/extensions` for the first
     * profile, `files/profiles/<id>/extensions` for every other one (18.0.0 beta 3).
     *
     * The first profile keeps the folder it always had, so an update finds the installed extensions
     * where they were; nothing is copied and no extension is lost.
     */
    @JvmStatic
    fun directory(context: Context): File =
        File(com.rpooi3.explorer.profile.Profiles.currentDir(context.applicationContext), "extensions")

    /** The folder of [extensionId]. */
    @JvmStatic
    fun directoryOf(context: Context, extensionId: String): File =
        File(directory(context), extensionId)

    /** The list of the profile in front: `extensions` or `extensions@<id>`. */
    private fun prefs(context: Context) = com.rpooi3.explorer.profile.ProfilePrefs.get(context, PREFS)

    /** What is installed, in the order it was installed. */
    @JvmStatic
    fun installed(context: Context): List<WebExtension> {
        val text = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            val list = ArrayList<WebExtension>(array.length())
            var repaired = false
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                var extension = WebExtension.fromJson(item)
                // A folder that was removed under us does not count as installed.
                if (!File(extension.rootDir).isDirectory) continue
                // 17.4.1: what was installed before the words of `_locales` were read carries
                // `__MSG_…__` as its name. Its manifest is read again, once, and the list is
                // written back with the name the extension actually has.
                if (Locales.hasPlaceholder(extension.name) ||
                    Locales.hasPlaceholder(extension.description)
                ) {
                    val fresh = try {
                        ManifestParser.parse(extension.id, File(extension.rootDir))
                    } catch (e: Exception) {
                        null
                    }
                    if (fresh != null) {
                        extension = fresh
                        repaired = true
                    }
                }
                list.add(extension)
            }
            if (repaired) write(context, list)
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
        com.rpooi3.explorer.profile.ProfilePrefs.get(context, "extension_storage")
            .edit().remove("ext_" + extensionId).apply()
        com.rpooi3.explorer.profile.ProfilePrefs.get(context, "extension_session")
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

    /** Where a fetched extension waits while it is being asked about. */
    @JvmStatic
    fun stagingOf(context: Context, extensionId: String): File =
        File(File(context.cacheDir, "staged"), extensionId)

    /**
     * 17.4.0: unpacks [crx] into a staging folder and reads its manifest, without adding it.
     * The install prompt shows what the extension asks for, and only then is it added
     * ([accept]), so nothing of an extension that was turned down stays in place.
     *
     * @return the extension as its manifest describes it, or null if it cannot be read
     */
    @JvmStatic
    fun stage(context: Context, crx: File, extensionId: String): WebExtension? {
        if (!extensionId.matches(Regex("[a-p]{32}"))) return null
        val target = stagingOf(context, extensionId)
        target.deleteRecursively()
        target.mkdirs()
        return try {
            CrxExtractor.unpack(crx, target)
            val parsed = ManifestParser.parse(extensionId, target)
            if (parsed == null) target.deleteRecursively()
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "Cannot stage " + extensionId + ": " + e)
            target.deleteRecursively()
            null
        }
    }

    /** Throws away what [stage] unpacked, after the question was answered with no. */
    @JvmStatic
    fun discard(context: Context, extensionId: String) {
        try {
            stagingOf(context, extensionId).deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot discard the staging folder: " + e)
        }
    }

    /**
     * 17.4.0: adds what [stage] unpacked — its files move into the extension's own folder and the
     * staging folder goes.
     *
     * @return the installed extension, or null if it could not be moved or read
     */
    @JvmStatic
    fun accept(context: Context, staged: WebExtension): WebExtension? {
        val source = File(staged.rootDir)
        if (!source.isDirectory) return null
        val target = directoryOf(context, staged.id)
        return try {
            target.deleteRecursively()
            target.mkdirs()
            source.copyRecursively(target, overwrite = true)
            val parsed = ManifestParser.parse(staged.id, target) ?: run {
                target.deleteRecursively()
                return null
            }
            add(context, parsed)
            source.deleteRecursively()
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "Cannot accept " + staged.id + ": " + e)
            target.deleteRecursively()
            null
        }
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
