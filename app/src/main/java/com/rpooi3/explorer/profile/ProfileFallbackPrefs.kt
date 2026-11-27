/*
 * Explorer: the preferences of a profile, over the ones every profile shares.
 * Own code, GPL-3.0.
 *
 * A profile gets its own file for the settings it changes (`settings@<id>`), and reads everything
 * else from the settings of the app. That is how Chrome's profiles behave: a new profile starts
 * with the settings the browser has, and the profile keeps whatever it changes.
 *
 * The wrapper is a [SharedPreferences] like any other, so every caller that already reads the
 * settings keeps working — `getBoolean("...", true)` answers from the profile first, then from the
 * shared file; writes always go to the profile.
 */
package com.rpooi3.explorer.profile

import android.content.SharedPreferences

/** A [SharedPreferences] that writes to [own] and reads from [own] first, then from [shared]. */
class ProfileFallbackPrefs(
    private val own: SharedPreferences,
    private val shared: SharedPreferences,
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> {
        // The shared values are the base; the profile's own values win.
        val merged = HashMap<String, Any?>(shared.all)
        merged.putAll(own.all)
        @Suppress("UNCHECKED_CAST")
        return merged as MutableMap<String, *>
    }

    override fun getString(key: String, defValue: String?): String? =
        if (own.contains(key)) own.getString(key, defValue) else shared.getString(key, defValue)

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        if (own.contains(key)) own.getStringSet(key, defValues) else shared.getStringSet(key, defValues)

    override fun getInt(key: String, defValue: Int): Int =
        if (own.contains(key)) own.getInt(key, defValue) else shared.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long): Long =
        if (own.contains(key)) own.getLong(key, defValue) else shared.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float =
        if (own.contains(key)) own.getFloat(key, defValue) else shared.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (own.contains(key)) own.getBoolean(key, defValue) else shared.getBoolean(key, defValue)

    /** Whether the value is the profile's own, rather than one it inherits. */
    fun isOwn(key: String): Boolean = own.contains(key)

    override fun contains(key: String): Boolean = own.contains(key) || shared.contains(key)

    override fun edit(): SharedPreferences.Editor = own.edit()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        own.registerOnSharedPreferenceChangeListener(listener)
        shared.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        own.unregisterOnSharedPreferenceChangeListener(listener)
        shared.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
