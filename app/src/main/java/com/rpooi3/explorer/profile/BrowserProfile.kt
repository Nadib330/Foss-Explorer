/*
 * Explorer: a browsing profile.
 * Own code, GPL-3.0.
 *
 * Chrome keeps a profile per identity: its own cookies, history, bookmarks, permissions and open
 * tabs, with its own colour and name. Explorer's 18.0.0 brings the same idea to the phone, without
 * the account behind it — a profile here is a name, a colour and a set of separate data, and
 * nothing is ever signed in to Google (there is no sign-in at all).
 */
package com.rpooi3.explorer.profile

import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * One profile: what it is called, what colour it carries, and whether it is the guest.
 *
 * @param id the key of its data. The first profile keeps the empty id, so everything that was
 *   stored before profiles existed belongs to it and nothing has to be migrated.
 * @param name what the switcher, the settings screen and the tab striplight show
 * @param colour the index into [Profiles.COLOURS]
 * @param guest whether this is the guest session: nothing of it is kept
 */
class BrowserProfile(
    val id: String,
    val name: String,
    val colour: Int,
    val guest: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = createdAt,
) {

    /** The first letter of the name, for the disc of the avatar. */
    val initial: String
        get() = name.trim().take(1).ifBlank { "E" }.uppercase(Locale.getDefault())

    /** Where the data of this profile lives, below the profile folder. */
    val directory: String get() = if (id.isEmpty()) DEFAULT_ID else id

    /** The name this profile's WebView profile carries (see [ProfileWebView]). */
    val webViewProfileName: String get() = if (id.isEmpty()) DEFAULT_WEBVIEW_PROFILE else WEBVIEW_PREFIX + id

    /** Whether this profile is the one that keeps the data stored before 18.0.0. */
    val isDefault: Boolean get() = id == DEFAULT_ID

    fun withName(value: String): BrowserProfile = BrowserProfile(id, value, colour, guest, createdAt, lastUsed)

    fun withColour(value: Int): BrowserProfile = BrowserProfile(id, name, value, guest, createdAt, lastUsed)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("colour", colour)
        put("guest", guest)
        put("created", createdAt)
        put("used", lastUsed)
    }

    override fun equals(other: Any?): Boolean = other is BrowserProfile && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "BrowserProfile($id, $name)"

    companion object {
        /** The id of the profile that keeps what was stored before profiles existed. */
        const val DEFAULT_ID = ""

        /** The WebView profile the default profile uses: WebView's own default. */
        const val DEFAULT_WEBVIEW_PROFILE = "Default"

        /** The prefix of the WebView profile of every other profile. */
        const val WEBVIEW_PREFIX = "explorer-"

        /** A new, random id: nothing of a deleted profile can be picked up by a new one. */
        @JvmStatic
        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

        @JvmStatic
        fun fromJson(json: JSONObject): BrowserProfile? {
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            return BrowserProfile(
                id = json.optString("id"),
                name = name,
                colour = json.optInt("colour", 0),
                guest = json.optBoolean("guest", false),
                createdAt = json.optLong("created", System.currentTimeMillis()),
                lastUsed = json.optLong("used", System.currentTimeMillis()),
            )
        }

        /** The profile an app that has never had one starts with. */
        @JvmStatic
        fun first(): BrowserProfile = BrowserProfile(DEFAULT_ID, "Personal", 0)
    }
}
