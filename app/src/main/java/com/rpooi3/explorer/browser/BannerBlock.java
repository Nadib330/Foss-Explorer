package com.rpooi3.explorer.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BannerBlock {
    private static final String TAG = "BannerBlock";
    private static final String FILE_NAME = "banners.txt";
    private static volatile String configString = "";
    private static volatile boolean isLoading = false;
    private static volatile boolean isDownloading = false; // Zentrale Sperre gegen Mehrfach-Downloads
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BannerBlock(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        // 1. Prüfen, ob ein Eintrag auf '_deny_cookie_banners' und 'true' steht
        boolean isEnabled = false;
        for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
            if (entry.getKey().endsWith("_deny_cookie_banners") && entry.getValue() instanceof Boolean) {
                if ((Boolean) entry.getValue()) {
                    isEnabled = true;
                    break;
                }
            }
        }

        // Falls kein passender True-Eintrag existiert, bricht der Blocker sofort ab
        if (!isEnabled) {
            configString = ""; // Cache leeren, falls es vorher aktiv war
            return;
        }

        // 2. Wenn aktiv, prüfen ob der tägliche Check bereits gelaufen ist
        long lastCheckTime = sp.getLong("banner_last_check_time", 0);
        long currentTime = System.currentTimeMillis();
        long oneDayInMillis = 24 * 60 * 60 * 1000L; // 24 Stunden in Millisekunden

        // Wenn seit dem letzten Check weniger als ein Tag vergangen ist, überspringen wir das Update
        if (currentTime - lastCheckTime < oneDayInMillis) {
            // Falls die Config im Speicher leer ist, laden wir sie trotzdem aus der lokalen Datei
            if (configString.isEmpty() && !isLoading) {
                loadHosts(context);
            }
            return;
        }

        // 3. Fortfahren mit Download / Laden der Regeln (maximal einmal am Tag)
        File file = new File(context.getFilesDir(), FILE_NAME);
        boolean checkPerformed = false;

        if (!file.exists()) {
            downloadBanners(context);
            checkPerformed = true;
        } else {
            Calendar time = Calendar.getInstance();
            time.add(Calendar.DAY_OF_YEAR, -3);
            if (new java.util.Date(file.lastModified()).before(time.getTime())) {
                if (sp.getBoolean("profileStandard_adBlock", true)) {
                    downloadBanners(context);
                    checkPerformed = true;
                }
            }
        }

        // Zeitstempel nur aktualisieren, wenn tatsächlich ein Update-Versuch stattgefunden hat
        if (checkPerformed) {
            sp.edit().putLong("banner_last_check_time", currentTime).apply();
        }

        if (configString.isEmpty() && !isLoading) {
            loadHosts(context);
        }
    }

    private static void loadHosts(final Context context) {
        if (isLoading) return;
        isLoading = true;

        executor.execute(() -> {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) { isLoading = false; return; }
            // Verwende ein stabiles, abwärtskompatibles Einlesen der Datei
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                int read = fis.read(bytes);
                if (read != -1) {
                    String jsonDataString = new String(bytes, StandardCharsets.UTF_8);
                    JSONArray data = new JSONObject(jsonDataString).getJSONArray("data");
                    configString = data.toString().replaceAll("\\\\\"", "\\\\\\\\\"");
                    Log.d(TAG, "Cookie banner rules loaded successfully.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading banner hosts", e);
            } finally {
                isLoading = false;
            }
        });
    }

    public static void downloadBanners(final Context context) {
        // HIER ist die zentrale Stelle: Wenn schon geladen wird, sofort abbrechen!
        if (isDownloading) return;
        isDownloading = true;
        Log.d(TAG, "Downloading Mozilla cookie banner rules...");
        executor.execute(() -> {
            try {
                Log.d(TAG, "Downloading Mozilla cookie banner rules...");
                // KORREKTUR: Vollständiger Link zur echten JSON-Datei gesetzt
                URLConnection ucon = new URL("https://raw.githubusercontent.com/mozilla/cookie-banner-rules-list/main/cookie-banner-rules-list.json").openConnection();
                ucon.setConnectTimeout(10000);
                ucon.setReadTimeout(5000);

                File bannerFile = new File(context.getFilesDir(), FILE_NAME);
                try (InputStream is = ucon.getInputStream();
                     BufferedInputStream inStream = new BufferedInputStream(is, 8192);
                     FileOutputStream outStream = new FileOutputStream(bannerFile)) {
                    byte[] buff = new byte[8192];
                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                    }
                    outStream.flush();
                }
                loadHosts(context);
                Log.w(TAG, "Mozilla cookie banner rules updated on storage.");
            } catch (IOException e) {
                Log.w(TAG, "Error updating Mozilla cookie banner rules", e);
            } finally {
                isDownloading = false; // Sperre wieder freigeben, wenn fertig (oder bei Fehler)
            }
        });
    }

    private static String getBaseJsLogic() {
        return "var config = JSON.parse('" + configString + "');\n" +
                "var curDom = window.location.hostname;\n" +
                "var match = (item) => !item.domains || item.domains.length === 0 || item.domains.some(d => curDom.endsWith('.' + d) || curDom === d);\n";
    }

    public static String getBannerBlockScriptPageStarted() {
        if (configString == null || configString.isEmpty()) return null;
        return getBaseJsLogic() +
                "config.forEach(item => {\n" +
                "    if (match(item) && item.cookies && Array.isArray(item.cookies.optOut)) {\n" +
                "        item.cookies.optOut.forEach(c => document.cookie = c.name + '=' + c.value + '; path=/; domain=' + curDom);\n" +
                "    }\n" +
                "});";
    }

    public static String getBannerBlockScriptPageFinished() {
        if (configString == null || configString.isEmpty()) return null;
        return getBaseJsLogic() +
                "for (var i = 0; i < config.length; i++) {\n" +
                "    var item = config[i];\n" +
                "    if (match(item) && item.click && item.click.presence && item.click.optOut) {\n" +
                "        if (document.querySelectorAll(item.click.presence).length > 0) {\n" +
                "            setTimeout(() => document.querySelectorAll(item.click.optOut).forEach(el => el.click()), 300);\n" +
                "            break;\n" +
                "        }\n" +
                "    }\n" +
                "}";
    }
}