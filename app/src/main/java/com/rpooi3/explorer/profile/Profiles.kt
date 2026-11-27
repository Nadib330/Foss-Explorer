/*
 * Explorer: the profiles of the browser, and which one is in front.
 * Own code, GPL-3.0.
 *
 * One profile is active at a time (18.0.0; side-by-side windows can come later), and everything a
 * website leaves behind belongs to the profile it was left in:
 *
 *  - cookies, web storage, the HTTP cache and service workers: the WebView profile the tab is
 *    bound to (see [ProfileWebView], `WebViewCompat.setProfile`);
 *  - history, bookmarks and the lists of saved websites: `places-<id>.sqlite`;
 *  - site permissions and the per-site choices: a preferences file per profile ([ProfilePrefs]);
 *  - the log of downloads Explorer wrote itself: a preferences file per profile;
 *  - open tabs: the browser closes them when the profile changes and opens the new profile's
 *    home page, so a tab never shows a page of a profile that is not in front.
 *
 * The guest is a profile that is thrown away: its data lives in a folder below `cache/`, and
 * leaving it deletes that folder.
 */
package com.rpooi3.explorer.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONArray
import java.io.File

object Profiles {

    private const val TAG = "ExplorerProfiles"
    private const val PREFS = "browser_profiles"
    private const val KEY_LIST = "profiles"
    private const val KEY_ACTIVE = "active_profile"
    private const val KEY_ASK = "ask_on_startup"
    private const val KEY_SET_UP = "profile_setup_done"
    private const val KEY_GUEST = "guest_profile"
    private const val KEY_SKIP_ONCE = "skip_ask_once"
    private const val KEY_SWITCHED_TO = "switched_to"

    /** The nine colours a profile can carry, as Chrome's colours are picked: a name and a colour. */
    @JvmField
    val COLOURS: IntArray = intArrayOf(
        0xFF1A73E8.toInt(), // blue, the one of the first profile
        0xFFD93025.toInt(), // red
        0xFF188038.toInt(), // green
        0xFFF9AB00.toInt(), // yellow
        0xFF9334E6.toInt(), // purple
        0xFF12B5CB.toInt(), // cyan
        0xFFE8710A.toInt(), // orange
        0xFF5F6368.toInt(), // grey
        0xFFC5221F.toInt(), // dark red
    )

    /** How a colour is named in the picker. */
    @JvmField
    val COLOUR_NAMES: Array<String> = arrayOf(
        "Blue", "Red", "Green", "Yellow", "Purple", "Cyan", "Orange", "Grey", "Crimson",
    )

    /** The file the profiles themselves are kept in: shared by every profile, of course. */
    private fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // The list ---------------------------------------------------------------------------------

    /** Every profile on the device, the guest not among them, in the order they were made. */
    @JvmStatic
    fun all(context: Context): List<BrowserProfile> {
        val text = store(context).getString(KEY_LIST, null)
        if (text == null) {
            // An app that had no profile yet has exactly one, and it keeps the old data.
            val first = BrowserProfile.first()
            save(context, listOf(first))
            return listOf(first)
        }
        return try {
            val array = JSONArray(text)
            val list = ArrayList<BrowserProfile>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                BrowserProfile.fromJson(item)?.let { list.add(it) }
            }
            if (list.isEmpty()) {
                val first = BrowserProfile.first()
                save(context, listOf(first))
                return listOf(first)
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read the profiles: " + e)
            listOf(BrowserProfile.first())
        }
    }

    /**
     * Writes the list and waits for it. A profile that was just made is switched to at once and the
     * browser is restarted in it; an asynchronous write would be lost on that restart, which is
     * what made beta 1 and beta 2 look as if the profile had never changed and left no new profile.
     */
    private fun save(context: Context, list: List<BrowserProfile>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        store(context).edit().putString(KEY_LIST, array.toString()).commit()
    }

    /** The profile in front; the first one if none was chosen yet. */
    @JvmStatic
    fun current(context: Context): BrowserProfile {
        val guest = guestProfile(context)
        if (guest != null) return guest
        val active = store(context).getString(KEY_ACTIVE, null)
        return all(context).firstOrNull { it.id == active } ?: all(context).first()
    }

    /** The id of the data in front: the empty string for the first profile. */
    @JvmStatic
    fun currentId(context: Context): String = current(context).id

    /** Whether the browser is in a guest session right now. */
    @JvmStatic
    fun isGuest(context: Context): Boolean = guestProfile(context) != null

    /**
     * Makes [id] the profile in front. Nothing of the profile it leaves is deleted, and the data
     * of the new one is exactly what was there when it was left.
     */
    @JvmStatic
    fun switchTo(context: Context, id: String) {
        val target = all(context).firstOrNull { it.id == id }
        if (target == null) {
            Log.w(TAG, "No profile " + id)
            return
        }
        // Closing a guest session throws its data away before the next profile opens.
        if (isGuest(context)) leaveGuest(context)
        // Committed: the browser is restarted in the new profile a moment from now.
        store(context).edit().putString(KEY_ACTIVE, id).commit()
    }

    // Making, renaming, deleting ---------------------------------------------------------------

    /** A new profile with its own empty data. Returns it, or null when there are too many. */
    @JvmStatic
    fun create(context: Context, name: String, colour: Int): BrowserProfile? {
        val list = all(context).toMutableList()
        if (list.size >= MAX_PROFILES) return null
        val cleaned = name.trim().take(32).ifBlank { "Profile " + (list.size + 1) }
        val profile = BrowserProfile(
            id = BrowserProfile.newId(),
            name = cleaned,
            colour = colour.coerceIn(0, COLOURS.lastIndex),
        )
        list.add(profile)
        save(context, list)
        // 18.0.0 beta 9 (item 3): a new profile begins with the settings the browser has, as a new
        // profile of Chrome does, and changes them from there on its own.
        try {
            ProfilePrefs.copySettings(context, profile.id)
        } catch (e: Exception) {
            Log.w(TAG, "The settings of the new profile: " + e)
        }
        return profile
    }

    @JvmStatic
    fun rename(context: Context, id: String, name: String) {
        val cleaned = name.trim().take(32)
        if (cleaned.isEmpty()) return
        save(context, all(context).map { if (it.id == id) it.withName(cleaned) else it })
    }

    @JvmStatic
    fun setColour(context: Context, id: String, colour: Int) {
        save(context, all(context).map { if (it.id == id) it.withColour(colour) else it })
    }

    /** Whether [id] can be taken away: never the last one, and never while it is the one in front. */
    @JvmStatic
    fun canDelete(context: Context, id: String): Boolean {
        val list = all(context)
        if (list.size <= 1) return false
        return list.any { it.id == id } && id != currentId(context)
    }

    /**
     * Deletes [id] and everything that belongs to it: its database, its preferences, its files.
     * The last profile cannot be deleted, and neither can the one in front — a browser without a
     * profile has nowhere to put anything, and a profile cannot be deleted out from under the page
     * that is open.
     */
    @JvmStatic
    fun delete(context: Context, id: String): Boolean {
        if (!canDelete(context, id)) return false
        save(context, all(context).filterNot { it.id == id })
        // The data goes with it.
        try {
            ProfilePrefs.deleteAllOf(context, id)
            thingsOf(context, id).deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot delete the data of " + id + ": " + e)
        }
        store(context).edit().remove("seen_" + id).commit()
        return true
    }

    // The guest --------------------------------------------------------------------------------

    /** Starts a guest session: a profile whose data is thrown away when it ends. */
    @JvmStatic
    fun startGuest(context: Context): BrowserProfile {
        // 18.0.0 beta 4, issue 7: nothing of an earlier guest session is still here. A session whose
        // end never came — the app was killed, the battery went — leaves its databases and its
        // WebView profile behind, and the next guest must not find them.
        wipeGuestLeftovers(context)
        val guest = BrowserProfile(
            id = "guest-" + BrowserProfile.newId(),
            name = "Guest",
            colour = 7,
            guest = true,
        )
        store(context).edit().apply {
            putString(KEY_GUEST, guest.toJson().toString())
            putString(KEY_ACTIVE, guest.id)
        }.commit()
        return guest
    }

    /** The guest session that is running, if one is. */
    @JvmStatic
    fun guestProfile(context: Context): BrowserProfile? {
        val text = store(context).getString(KEY_GUEST, null) ?: return null
        return try {
            org.json.JSONObject(text).let { BrowserProfile.fromJson(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Ends the guest session: its folder and its shared preferences are deleted, and the profile
     * that was in front before it comes back.
     */
    @JvmStatic
    fun leaveGuest(context: Context) {
        val guest = guestProfile(context) ?: return
        store(context).edit().remove(KEY_GUEST).commit()
        try {
            ProfilePrefs.deleteAllOf(context, guest.id)
            thingsOf(context, guest.id).deleteRecursively()
            // 18.0.0 beta 4, issue 7: its history, its icons and its WebView profile go with it.
            deleteGuestDatabases(context, guest.id)
            ProfileWebView.delete(guest)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot delete the guest data: " + e)
        }
        val previous = store(context).getString(KEY_ACTIVE, null)
        if (previous == null || previous == guest.id) {
            store(context).edit().putString(KEY_ACTIVE, all(context).first().id).commit()
        }
    }

    // The question on startup ------------------------------------------------------------------

    /** Whether the switcher opens every time the browser starts. */
    @JvmStatic
    fun askOnStartup(context: Context): Boolean = store(context).getBoolean(KEY_ASK, true)

    @JvmStatic
    fun setAskOnStartup(context: Context, ask: Boolean) {
        store(context).edit().putBoolean(KEY_ASK, ask).commit()
    }

    /** Whether the switcher has to be shown now. */
    @JvmStatic
    fun shouldAsk(context: Context): Boolean =
        askOnStartup(context) && setupDone(context) && !skipsAskOnce(context)

    /**
     * The profile the user has just chosen is the one to open: the browser is started again in it,
     * and it must not ask the same question again. The flag is good for one start.
     */
    @JvmStatic
    fun setSkipsAskOnce(context: Context, profileName: String?) {
        // Committed: the browser is restarted the moment after this, and the flag is what keeps it
        // from asking the same question again (18.0.0 beta 3).
        store(context).edit().apply {
            putBoolean(KEY_SKIP_ONCE, true)
            if (profileName.isNullOrEmpty()) remove(KEY_SWITCHED_TO) else putString(KEY_SWITCHED_TO, profileName)
        }.commit()
    }

    /** Whether this start of the browser was caused by a switch of profiles. */
    @JvmStatic
    fun skipsAskOnce(context: Context): Boolean = store(context).getBoolean(KEY_SKIP_ONCE, false)

    /** The name of the profile that was switched to, if this start came from a switch. */
    @JvmStatic
    fun switchedTo(context: Context): String? = store(context).getString(KEY_SWITCHED_TO, null)

    /** Takes the flag of the switch away: the next start asks again, as it always does. */
    @JvmStatic
    fun consumeSkip(context: Context) {
        store(context).edit().remove(KEY_SKIP_ONCE).commit()
    }

    /** Whether the first profile has been set up (the welcome asked for it). */
    @JvmStatic
    fun setupDone(context: Context): Boolean = store(context).getBoolean(KEY_SET_UP, false)

    @JvmStatic
    fun setSetupDone(context: Context, done: Boolean = true) {
        store(context).edit().putBoolean(KEY_SET_UP, done).commit()
    }

    /**
     * Removes what an earlier guest session left on the disk: its databases, its preferences, its
     * folder and its WebView profile. Nothing of it may be read by the guest that comes next.
     */
    @JvmStatic
    fun wipeGuestLeftovers(context: Context) {
        val app = context.applicationContext
        val dbDir = File(app.applicationInfo.dataDir, "databases")
        (dbDir.listFiles() ?: emptyArray()).forEach { file ->
            if (file.name.contains("-guest-")) file.delete()
        }
        val prefsDir = File(app.applicationInfo.dataDir, "shared_prefs")
        (prefsDir.listFiles() ?: emptyArray()).forEach { file ->
            if (file.name.contains(ProfilePrefs.SEPARATOR + "guest-")) file.delete()
        }
        val guestCache = File(app.cacheDir, "guest")
        (guestCache.listFiles() ?: emptyArray()).forEach { it.deleteRecursively() }
        if (ProfileWebView.supported()) {
            try {
                // The WebView profile of a guest session is named after its id, and goes too.
                val store = androidx.webkit.ProfileStore.getInstance()
                for (name in store.allProfileNames) {
                    if (name.startsWith(BrowserProfile.WEBVIEW_PREFIX + "guest-")) store.deleteProfile(name)
                }
            } catch (e: Throwable) {
                Log.i(TAG, "The WebView profiles of the guests stay: " + e)
            }
        }
    }

    /** Takes the history and the icons of a guest session away; they are not files of a profile. */
    @JvmStatic
    fun deleteGuestDatabases(context: Context, id: String) {
        if (!id.startsWith("guest-")) return
        // The file can only go once nothing reads it.
        com.rpooi3.explorer.database.places.PlacesDatabase.release()
        val dbDir = File(context.applicationContext.applicationInfo.dataDir, "databases")
        (dbDir.listFiles() ?: emptyArray()).forEach { file ->
            if (file.name.contains("-" + id)) file.delete()
        }
    }

    // Where the data is ------------------------------------------------------------------------

    /** `files/profiles`: the data of every profile below it. */
    @JvmStatic
    fun profilesDir(context: Context): File = File(context.applicationContext.filesDir, "profiles")

    /**
     * The folder of one profile. The first profile keeps the folders the app had before profiles
     * existed, so an update finds history, bookmarks and extensions where they were.
     */
    @JvmStatic
    fun thingsOf(context: Context, id: String): File {
        val app = context.applicationContext
        if (id.isEmpty()) return app.filesDir
        if (id.startsWith("guest-")) return File(File(app.cacheDir, "guest"), id)
        return File(profilesDir(app), id)
    }

    /** The folder of the profile in front: `files/` for the first one, `files/profiles/<id>/`. */
    @JvmStatic
    fun currentDir(context: Context): File = thingsOf(context, currentId(context))

    /** The database file of [base] for the profile in front, e.g. `places-1a2b3c4d.sqlite`. */
    @JvmStatic
    fun databaseName(context: Context, base: String): String {
        val id = currentId(context)
        if (id.isEmpty()) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val suffix = if (dot > 0) base.substring(dot) else ""
        return stem + "-" + id + suffix
    }

    /** The profile in front, without asking the preferences: for the hot paths of a page load. */
    @Volatile
    private var cachedId: String? = null

    @JvmStatic
    fun cachedId(): String = cachedId ?: ""

    @JvmStatic
    fun cacheCurrent(context: Context) {
        cachedId = currentId(context)
    }

    /** The largest number of profiles a phone is expected to carry. */
    const val MAX_PROFILES = 12

    /** A colour for a new profile: the one after the last, so two profiles never look alike. */
    @JvmStatic
    fun nextColour(context: Context): Int = all(context).size % COLOURS.size

    /** The preferences the app keeps for the profile in front. */
    @JvmStatic
    fun prefs(context: Context): SharedPreferences = ProfilePrefs.get(context, null)

    /** A preferences file of the profile in front, as [ProfilePrefs] scopes it. */
    @JvmStatic
    fun prefs(context: Context, base: String): SharedPreferences = ProfilePrefs.get(context, base)

    /**
     * The settings of the profile in front: its own file for the settings it changes, the settings
     * of the app for the rest. The first profile gets the settings of the app themselves, so an
     * update to 18.0.0 finds everything where it was.
     */
    @JvmStatic
    fun settings(context: Context): SharedPreferences = ProfilePrefs.settings(context)

    /** The settings every profile shares: the switcher's own question, the theme, the language. */
    @JvmStatic
    fun sharedPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /** Closes the databases of the profile that is being left, before its files are touched. */
    @JvmStatic
    fun releaseDatabases() {
        try {
            com.rpooi3.explorer.database.places.PlacesDatabase.release()
        } catch (e: Exception) {
            Log.i(TAG, "No database to close: " + e)
        }
    }
}
