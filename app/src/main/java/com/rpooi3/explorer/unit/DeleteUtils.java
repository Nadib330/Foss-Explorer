package com.rpooi3.explorer.unit;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * "Clear browsing data" (Settings, and the history screen).
 *
 * 18.0.0 beta 4, issue 8: what is cleared is the data of the profile in front, and of nothing else.
 * Until this version the screen emptied the whole app: every database of every profile, every file
 * of `shared_prefs` — including the list of the profiles itself — and the whole WebView folder, so
 * clearing one profile left the others empty as well. What belongs to a profile is known: its
 * database (`places-<id>.sqlite`), its icons (`favicon-<id>.db`), its preferences (`…@<id>.xml`)
 * and its folder; and where the WebView has profiles of its own, its cookies and its web storage.
 */
public class DeleteUtils {

    /** The files of `shared_prefs` that are never a profile's own. */
    private static final String[] GLOBAL_PREFS = {
            "clear_data_settings.xml",
            "browser_profiles.xml",
    };

    public static void executeDeletion(Context context, boolean cache, boolean cookies,
                                       boolean database, boolean settings, boolean otherWebview, boolean history) {

        if (cache) {
            clearCache(context);
        }

        if (cookies) {
            clearCookies(context);
        }

        if (database) {
            // The web storage (databases, IndexedDB, local storage) of the profile in front.
            com.rpooi3.explorer.profile.ProfileWebView.clearStorage(context);
            // The WebView's own folder, only where all profiles share one WebView.
            File webviewDir = com.rpooi3.explorer.profile.ProfileWebView.storageDirectory(context);
            if (webviewDir != null && webviewDir.exists()) {
                deleteDir(new File(webviewDir, "databases"));
                deleteDir(new File(webviewDir, "IndexedDB"));
            }
            // The databases of the profile in front: its history and its icons.
            deleteProfileDatabases(context);
        }

        if (otherWebview) {
            clearOtherWebViewData(context);
        }

        if (settings) {
            clearProfilePreferences(context);
        }

        if (history) {
            // The history of the profile in front, which is where its own database is.
            BrowserUnit.clearHistory(context);
        }
    }

    /** The cache of the app, without the folders of the other profiles. */
    private static void clearCache(Context context) {
        try {
            new WebView(context).clearCache(true);
        } catch (RuntimeException e) {
            Log.i("DeleteUtils", "The cache of the WebView stayed: " + e);
        }
        final boolean guest = com.rpooi3.explorer.profile.Profiles.isGuest(context);
        deleteChildren(context.getCacheDir(), guest ? null : "guest");
        File external = context.getExternalCacheDir();
        if (external != null) deleteChildren(external, guest ? null : "guest");
    }

    /**
     * The cookies of the profile in front. The site permissions that allow cookies on a site are
     * kept, as before: they live in the preferences of the same profile.
     */
    private static void clearCookies(Context context) {
        CookieManager cookieManager = com.rpooi3.explorer.profile.ProfileWebView.cookieManager(context);
        SharedPreferences sharedPrefs = com.rpooi3.explorer.profile.ProfilePrefs.settings(context);
        Map<String, ?> allEntries = sharedPrefs.getAll();
        Map<String, String> savedCookies = new HashMap<>();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("_cookies") && entry.getValue() instanceof Boolean && (Boolean) entry.getValue()) {
                String domainProfile = key.substring(0, key.lastIndexOf("_cookies"));
                String url = domainProfile.startsWith("http") ? domainProfile : "https://" + domainProfile;
                String cookieStr = cookieManager.getCookie(url);
                if (cookieStr != null && !cookieStr.isEmpty()) {
                    savedCookies.put(url, cookieStr);
                }
            }
        }

        cookieManager.removeAllCookies(null);
        cookieManager.flush();

        for (Map.Entry<String, String> entry : savedCookies.entrySet()) {
            for (String cookie : entry.getValue().split(";")) {
                cookieManager.setCookie(entry.getKey(), cookie.trim());
            }
        }
        cookieManager.flush();
    }

    /** Takes the history and the icons of the profile in front away, and no other profile's. */
    private static void deleteProfileDatabases(Context context) {
        final String id = com.rpooi3.explorer.profile.Profiles.currentId(context);
        final String[] names = {
                com.rpooi3.explorer.database.places.PlacesDatabase.nameOf(context, id),
                com.rpooi3.explorer.profile.Profiles.databaseName(context, "favicon.db"),
        };
        com.rpooi3.explorer.database.places.PlacesDatabase.release();
        for (String name : names) {
            for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) {
                File file = new File(context.getApplicationInfo().dataDir + "/databases", name + suffix);
                if (file.isFile() && !file.delete()) {
                    Log.w("DeleteUtils", "Could not delete " + file.getName());
                }
            }
            try {
                context.deleteDatabase(name);
            } catch (RuntimeException e) {
                Log.w("DeleteUtils", "Could not delete the database " + name + ": " + e);
            }
        }
        // The saved lists of the site profiles of this profile are in its own preference file.
    }

    /** The sign-ins and the site permissions of the device's WebView. */
    private static void clearOtherWebViewData(Context context) {
        WebViewDatabase webViewDb = WebViewDatabase.getInstance(context);
        webViewDb.clearHttpAuthUsernamePassword();
        android.webkit.GeolocationPermissions.getInstance().clearAll();

        File webviewDir = com.rpooi3.explorer.profile.ProfileWebView.storageDirectory(context);
        if (webviewDir != null && webviewDir.exists()) {
            deleteDir(new File(webviewDir, "Local Storage"));
            deleteDir(new File(webviewDir, "Session Storage"));
            deleteDir(new File(webviewDir, "blob_storage"));
            deleteDir(new File(webviewDir, "Service Worker"));
            deleteDir(new File(webviewDir, "VideoDecodeStats"));
            deleteDir(new File(webviewDir, "Web Data"));
            deleteDir(new File(webviewDir, "Web Data-journal"));
        }
    }

    /**
     * Empties the preferences of the profile in front. The list of the profiles, the settings of
     * the screen that clears data and the files of every other profile stay.
     */
    private static void clearProfilePreferences(Context context) {
        final String id = com.rpooi3.explorer.profile.Profiles.currentId(context);
        for (File file : com.rpooi3.explorer.profile.ProfilePrefs.filesOf(context, id)) {
            final String name = file.getName();
            boolean global = false;
            for (String ignored : GLOBAL_PREFS) {
                if (ignored.equals(name)) global = true;
            }
            if (global) continue;
            // A file of another profile ends in @<its id>, and is not this profile's.
            if (!com.rpooi3.explorer.profile.ProfileBackup.belongsToPreferences(name, id)) continue;
            context.getSharedPreferences(name.replace(".xml", ""), Context.MODE_PRIVATE)
                    .edit().clear().apply();
        }
    }

    private static void deleteChildren(File dir, String keep) {
        if (dir == null || !dir.isDirectory()) return;
        for (String child : Objects.requireNonNull(dir.list())) {
            if (keep != null && keep.equals(child)) continue;
            deleteDir(new File(dir, child));
        }
    }

    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    if (!deleteDir(new File(dir, child))) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }

    // Prüft beim App-Beenden, ob der Auto-Delete aktiv ist und führt ihn aus
    public static void checkAndExecuteAutoDelete(Context context) {
        // 18.0.0 beta 4: the switches are the ones of the profile in front.
        SharedPreferences prefs = com.rpooi3.explorer.profile.ProfilePrefs.settings(context);
        if (prefs.getBoolean("auto_delete_exit", false)) {
            executeDeletion(
                    context,
                    prefs.getBoolean("delete_cache", false),
                    prefs.getBoolean("delete_cookies", false),
                    prefs.getBoolean("delete_database", false),
                    prefs.getBoolean("delete_settings", false),
                    prefs.getBoolean("delete_other", false),
                    prefs.getBoolean("delete_history", false)
            );
            Log.d("Autodelete", "Data cleared on start");
        }
    }
}
