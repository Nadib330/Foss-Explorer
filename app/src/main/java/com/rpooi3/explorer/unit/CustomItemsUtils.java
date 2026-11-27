package com.rpooi3.explorer.unit;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import com.rpooi3.explorer.activity.BrowserActivity;

public class CustomItemsUtils {

    public final static String KEY_REDIRECTS = "customRedirects";
    public final static String KEY_SEARCHES = "customSearches";

    /**
     * @param source Oder Name/Titel bei Suchen
     * @param target Oder Such-URL bei Suchen
     */ // Das gemeinsame Datenmodell für Suchen und Weiterleitungen
        public record CustomItem(String source, String target) {
    }

    // --- LOGIK FÜR WEITERLEITUNGEN (REDIRECTS) ---
    public static ArrayList<CustomItem> getRedirects(SharedPreferences preferences) throws JSONException {
        ArrayList<CustomItem> list = new ArrayList<>();
        String prefData = preferences.getString(KEY_REDIRECTS, "[]");

        if (Objects.requireNonNull(preferences.getString("saved_redirect_ok", "no")).equals("no")) {
            list.add(new CustomItem("://youtube.com", preferences.getString("sp_youTube_string_domain", "invidious.nerdvpn.de")));
            list.add(new CustomItem("youtube.com", preferences.getString("sp_youTube_string_domain", "invidious.nerdvpn.de")));
            list.add(new CustomItem("twitter.com", preferences.getString("sp_twitter_string_domain", "nitter.net")));
            saveRedirects(list);
            preferences.edit().putString("saved_redirect_ok", "yes").apply();
        }

        loadJsonItems(prefData, list);
        return list;
    }

    public static void saveRedirects(ArrayList<CustomItem> list) throws JSONException {
        saveJsonItems(KEY_REDIRECTS, list);
    }

    // --- LOGIK FÜR SUCHMASCHINEN (SEARCHES) ---
    public static ArrayList<CustomItem> getSearches(SharedPreferences preferences) throws JSONException {
        ArrayList<CustomItem> list = new ArrayList<>();
        String prefData = preferences.getString(KEY_SEARCHES, "[]");

        if (Objects.requireNonNull(preferences.getString("saved_searches_ok", "no")).equals("no")) {
            list.add(new CustomItem("Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?go=Go&search="));
            list.add(new CustomItem("Fairtranslate ", "https://fairtranslate.eu/?source=auto&q="));
            saveSearches(list);
            preferences.edit().putString("saved_searches_ok", "yes").apply(); // gefixt: direkt beim Initialisieren setzen
        }

        loadJsonItems(prefData, list);
        return list;
    }

    public static void saveSearches(ArrayList<CustomItem> list) throws JSONException {
        saveJsonItems(KEY_SEARCHES, list);
    }

    // --- INTERNE GENERISCHE ARBEITSMETHODEN (VERHINDERT CODE-DOPPLUNG) ---
    private static void loadJsonItems(String jsonString, ArrayList<CustomItem> targetList) throws JSONException {
        JSONArray array = new JSONArray(jsonString);
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            targetList.add(new CustomItem(obj.getString("source"), obj.getString("target")));
        }
        targetList.sort(Comparator.comparing(CustomItem::source)); // Einmalige Sortierung am Ende
    }

    private static void saveJsonItems(String prefKey, ArrayList<CustomItem> list) throws JSONException {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.getAppContext());
        JSONArray array = new JSONArray();
        for (CustomItem item : list) {
            JSONObject object = new JSONObject();
            object.put("source", item.source());
            object.put("target", item.target());
            array.put(object);
        }
        preferences.edit().putString(prefKey, array.toString()).apply();
    }
}