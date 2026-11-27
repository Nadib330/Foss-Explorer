/*
 * Explorer: the preferences of one profile.
 * Own code, GPL-3.0.
 *
 * A profile keeps its own site permissions, its own log of downloads and its own settings, in its
 * own file: `profile@<id>`. The first profile keeps the plain file, which is why an update to
 * 18.0.0 finds every setting exactly where it was.
 *
 * The name is prefixed rather than the keys, so a caller never has to know that profiles exist:
 * `ProfilePrefs.get(context, "site").getBoolean("site_javascript_https_example.com_1", true)` reads
 * the value of the profile in front.
 */
package com.rpooi3.explorer.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.File

object ProfilePrefs {

    /** The separator between the name of a file and the id of the profile it belongs to. */
    const val SEPARATOR = "@"

    /** The name of the file the app's settings live in, as [PreferenceManager] names it. */
    @JvmStatic
    fun defaultName(context: Context): String =
        context.applicationContext.packageName + "_preferences"

    /** The name of the file [base] has for the profile in front; the plain name for the first one. */
    @JvmStatic
    fun nameOf(context: Context, base: String?): String {
        val name = base ?: defaultName(context)
        val id = Profiles.currentId(context)
        return if (id.isEmpty()) name else name + SEPARATOR + id
    }

    /** The preferences of the profile in front: [base], or the settings if it is null. */
    @JvmStatic
    fun get(context: Context, base: String?): SharedPreferences {
        val app = context.applicationContext
        return if (base == null) PreferenceManager.getDefaultSharedPreferences(app)
        else app.getSharedPreferences(nameOf(app, base), Context.MODE_PRIVATE)
    }

    /** The preferences [base] has for [id], whoever is in front. */
    @JvmStatic
    fun getFor(context: Context, base: String?, id: String): SharedPreferences {
        val app = context.applicationContext
        val name = if (id.isEmpty()) (base ?: defaultName(app)) else (base ?: defaultName(app)) + SEPARATOR + id
        return app.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    /**
     * The settings of the profile in front: its own file (`<package>_preferences@<id>`) for what it
     * changes, the settings of the app for everything else. The first profile gets the settings of
     * the app themselves, so an update to 18.0.0 finds every setting exactly where it was.
     */
    @JvmStatic
    fun settings(context: Context): SharedPreferences {
        val app = context.applicationContext
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val id = Profiles.currentId(app)
        if (id.isEmpty()) return shared
        return ProfileFallbackPrefs(
            app.getSharedPreferences(defaultName(app) + SEPARATOR + id, Context.MODE_PRIVATE),
            shared,
        )
    }

    /**
     * 18.0.0 beta 9 (item 3): gives [id] the settings the browser has now, so a profile that is
     * made starts from the same place as the one it was made from — and every profile keeps its
     * own changes from then on. The identity of the profiles (their names, colours and the list of
     * them) is not part of it: that lives in its own file and is shared.
     */
    @JvmStatic
    fun copySettings(context: Context, id: String) {
        if (id.isEmpty()) return
        val app = context.applicationContext
        val source = PreferenceManager.getDefaultSharedPreferences(app)
        val target = settingsFor(app, id)
        val editor = target.edit()
        for ((key, value) in source.all) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
                else -> Unit
            }
        }
        editor.apply()
    }

    /** The settings of [id], whoever is in front. */
    @JvmStatic
    fun settingsFor(context: Context, id: String): SharedPreferences {
        val app = context.applicationContext
        if (id.isEmpty()) return PreferenceManager.getDefaultSharedPreferences(app)
        return app.getSharedPreferences(defaultName(app) + SEPARATOR + id, Context.MODE_PRIVATE)
    }

    /** Every file a profile has: the settings and every `name@id` beside them. */
    @JvmStatic
    fun filesOf(context: Context, id: String): List<File> {
        val dir = File(context.applicationContext.applicationInfo.dataDir, "shared_prefs")
        if (!dir.isDirectory) return emptyList()
        val suffix = SEPARATOR + id
        return (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith("$suffix.xml") }
    }

    /** Deletes everything a profile stored in its preferences files. */
    @JvmStatic
    fun deleteAllOf(context: Context, id: String) {
        for (file in filesOf(context, id)) {
            val name = file.name.removeSuffix(".xml")
            try {
                context.applicationContext
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            } catch (e: Exception) {
                // A file that cannot be opened is deleted with the rest below.
            }
            file.delete()
        }
    }
}
