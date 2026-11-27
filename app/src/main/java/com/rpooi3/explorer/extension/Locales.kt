/*
 * Explorer: the words of an extension, which are not always in its manifest.
 * Own code, GPL-3.0.
 *
 * A published extension does not put its name in `manifest.json` as it is: it puts a placeholder
 * there — `"name": "__MSG_app_name__"` — and the words themselves in
 * `_locales/<default_locale>/messages.json`, so that the store can show the name in the language
 * of whoever looks at it. Without this file the list of installed extensions showed the
 * placeholder itself (`__MSG_app_name_chrome__`, `__MSG_extName__`), which is what 17.4.1 fixes.
 *
 * The format is Chromium's `_locales` format: one JSON object per locale, a key per message, and
 * an optional "placeholders" object whose entries take the place of `$NAME$` in a message.
 */
package com.rpooi3.explorer.extension

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Locale

object Locales {

    private const val TAG = "ExplorerExtensions"

    /** `__MSG_some_key__`, which is what a manifest carries instead of a word of its own. */
    private val PLACEHOLDER = Regex("__MSG_([A-Za-z0-9_@]+?)__", RegexOption.IGNORE_CASE)

    /** Whether [value] still asks for a message of `_locales`. */
    @JvmStatic
    fun hasPlaceholder(value: String?): Boolean =
        value != null && PLACEHOLDER.containsMatchIn(value)

    /**
     * The messages of [dir] in the language the extension was published for: the one
     * `default_locale` names, and English where that one is missing. Keys are lower case, as
     * Chromium compares them.
     */
    @JvmStatic
    fun messages(dir: File, defaultLocale: String?): Map<String, String> {
        val wanted = ArrayList<String>()
        if (!defaultLocale.isNullOrBlank()) wanted.add(defaultLocale)
        // What Chromium falls back to when the locale a manifest names is not in the .crx.
        for (fallback in listOf("en", "en_US", "en_GB")) {
            if (!wanted.contains(fallback)) wanted.add(fallback)
        }
        val out = HashMap<String, String>()
        for (locale in wanted) {
            val file = File(File(File(dir, "_locales"), locale), "messages.json")
            if (!file.isFile) continue
            try {
                val json = JSONObject(file.readText())
                for (key in json.keys()) {
                    val keyName = key.lowercase(Locale.US)
                    if (out.containsKey(keyName)) continue
                    val entry = json.optJSONObject(key) ?: continue
                    var text = entry.optString("message")
                    if (text.isEmpty()) continue
                    // "$NAME$" in a message is replaced by the content of that placeholder, which
                    // is where the words a person sees actually end up in most extensions.
                    val placeholders = entry.optJSONObject("placeholders")
                    if (placeholders != null) {
                        for (name in placeholders.keys()) {
                            val content = placeholders.optJSONObject(name)
                                ?.optString("content").orEmpty()
                            if (content.isEmpty()) continue
                            text = text.replace(
                                "$" + name.uppercase(Locale.US) + "$",
                                content,
                                ignoreCase = true,
                            )
                        }
                    }
                    out[keyName] = text
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read the messages of " + locale + ": " + e)
            }
        }
        return out
    }

    /**
     * [value] with every `__MSG_key__` of it replaced by its message. A placeholder that the
     * extension does not carry a message for resolves to [fallback] instead of being shown as it
     * is — a name of `__MSG_app_name__` is worth nothing on a screen.
     */
    @JvmStatic
    @JvmOverloads
    fun resolve(value: String, messages: Map<String, String>, fallback: String = ""): String {
        if (!PLACEHOLDER.containsMatchIn(value)) return value
        var text = value
        // A message can name another message; two passes cover what extensions do.
        for (pass in 0 until 3) {
            if (!PLACEHOLDER.containsMatchIn(text)) break
            text = PLACEHOLDER.replace(text) { match ->
                messages[match.groupValues[1].lowercase(Locale.US)] ?: "\u0000"
            }
            // A key with no message leaves its placeholder in place for the next pass.
            text = text.replace("\u0000", "")
        }
        return if (text.isBlank() && value.isNotBlank()) {
            if (PLACEHOLDER.containsMatchIn(value)) fallback else value
        } else {
            text
        }
    }

    /** [resolve] for the manifest of [dir], reading `default_locale` from [manifest]. */
    @JvmStatic
    fun resolveIn(dir: File, manifest: JSONObject, value: String, fallback: String = ""): String {
        if (!hasPlaceholder(value)) return value
        val messages = messages(dir, manifest.optString("default_locale"))
        return resolve(value, messages, fallback).ifBlank { fallback }
    }
}
