package com.rpooi3.explorer.activity;

import static android.content.ContentValues.TAG;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Objects;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.browser.List_standard;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.database.RecordAction;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;
import com.rpooi3.explorer.database.RecordUnit;
import com.rpooi3.explorer.view.AdapterProfileList;
import com.rpooi3.explorer.view.CatToast;

public class Settings_ProfileList extends AppCompatActivity {

    private List<String> list;
    private List_standard listStandard;
    AdapterProfileList adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HelperUtils.initTheme(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings_profile_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationIcon(R.drawable.icon_back_stylized);
        toolbar.setNavigationContentDescription(R.string.bar_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.whitelist);
        View scrollDividerTop = findViewById(R.id.scrollDividerTop);
        View scrollDividerBottom = findViewById(R.id.scrollDividerBottom);
        assert scrollDividerTop != null;
        assert scrollDividerBottom != null;
        HelperUtils.setupDialogDividers(listView, scrollDividerTop, scrollDividerBottom);

        listStandard = new List_standard(this);
        RecordAction action = new RecordAction(this);
        action.open(false);
        list = action.listDomains(RecordUnit.TABLE_STANDARD);
        action.close();
        //noinspection NullableProblems
        adapter = new AdapterProfileList(this, list) {
            @Override
            public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ImageView deleteEntry = v.findViewById(R.id.icon_end);
                deleteEntry.setVisibility(VISIBLE);
                TextView tv = v.findViewById(R.id.dateView);
                tv.setVisibility(GONE);
                // 1. Domain-String VOR dem Löschen aus der Liste sichern (Verhindert Index-Fehler)
                String domain = list.get(position);
                FaviconHelper.setFavicon(this.getContext(), v, domain, R.id.icon_start, R.drawable.icon_image_broken);

                deleteEntry.setOnClickListener(v1 -> {
                    Snackbar snackbarDelete = Snackbar.make(v1, R.string.hint_database, Snackbar.LENGTH_SHORT);
                    HelperUtils.makeSnackbarRound(snackbarDelete);

                    snackbarDelete.setAction(this.getContext().getString(R.string.app_ok), v2 -> {
                        try {
                            // 2. Aus der Datenbank/Liste entfernen
                            listStandard.removeDomain(domain);
                            list.remove(position);
                            notifyDataSetChanged();
                            // 3. SharedPreferences kompakt in einer Schleife löschen
                            String[] keysToClear = {
                                    "_saveData", "_images", "_adBlock", "_trackingULS", "_location",
                                    "_fingerPrintProtection", "_cookies", "_cookiesThirdParty",
                                    "_deny_cookie_banners", "_javascript", "_javascriptPopUp",
                                    "_saveHistory", "_camera", "_microphone", "_dom", "_night",
                                    "_drm", "_desktop"
                            };
                            // 18.0.0 beta 9 (item 3): the settings of a site belong to the profile
                            // in front, as every other setting does.
                            SharedPreferences.Editor editor = com.rpooi3.explorer.profile.ProfilePrefs.settings(this.getContext()).edit();
                            for (String key : keysToClear) {
                                editor.remove(domain + key);
                            }
                            editor.apply();
                            CatToast.show(Settings_ProfileList.this, R.string.app_done, 1);
                        } catch (Exception e) {
                            Log.i(TAG, "dialogCustomSearches:" + e);
                        }
                    });
                    snackbarDelete.show();
                });
                return v;
            }
        };
        listView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list_profile, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {

        if (menuItem.getItemId() == android.R.id.home) finish();
        else if (menuItem.getItemId() == R.id.menu_help) {
            Uri webpage = Uri.parse("https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki/Saved-websites");
            BrowserUnit.intentURL(this, webpage);
        }

        else if (menuItem.getItemId() == R.id.menu_delete_list) {

            View rootView = findViewById(R.id.rootView);
            Snackbar snackbarDelete = Snackbar.make(rootView, R.string.hint_database, Snackbar.LENGTH_SHORT);
            HelperUtils.makeSnackbarRound(snackbarDelete);
            snackbarDelete.setAction(this.getString(R.string.app_ok), v2 -> {
                try {
                    // 1. SharedPreferences bereinigen
                    SharedPreferences sp = com.rpooi3.explorer.profile.ProfilePrefs.settings(this);
                    SharedPreferences.Editor editor = sp.edit();
                    String[] suffixes = {
                            "_saveData", "_images", "_adBlock", "_trackingULS", "_location",
                            "_fingerPrintProtection", "_cookies", "_cookiesThirdParty",
                            "_deny_cookie_banners", "_javascript", "_javascriptPopUp",
                            "_saveHistory", "_camera", "_microphone", "_dom", "_night",
                            "_drm", "_desktop"
                    };
                    for (String key : sp.getAll().keySet()) {
                        // NEU: Überspringe den Eintrag, wenn er mit "default" beginnt
                        if (key != null && key.startsWith("default")) {
                            continue;
                        }

                        for (String suffix : suffixes) {
                            assert key != null;
                            if (key.endsWith(suffix)) {
                                editor.remove(key);
                                break;
                            }
                        }
                    }
                    editor.apply();
                    // 2. Datenbank leeren
                    listStandard.clearDomains();
                    // 3. Lokale Liste leeren
                    list.clear();
                    // 4. UI/Adapter aktualisieren
                    if (adapter != null) { // Ersetze 'adapter' mit dem Namen deiner Adapter-Variable
                        adapter.notifyDataSetChanged();
                    }
                    CatToast.show(this, R.string.app_done, 1);
                } catch (Exception e) {
                    Log.i(TAG, "clearAllFromMenu: " + e);
                }
            });
            snackbarDelete.show();
            return true;
        }
        return true;
    }
}