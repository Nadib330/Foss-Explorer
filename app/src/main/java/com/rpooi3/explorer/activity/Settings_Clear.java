package com.rpooi3.explorer.activity;

import com.rpooi3.explorer.R;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.rpooi3.explorer.fragment.Fragment_settings_Clear;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.DeleteUtils;
import com.rpooi3.explorer.unit.HelperUtils;

public class Settings_Clear extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HelperUtils.initTheme(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings_clear);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        NestedScrollView scrollView = findViewById(R.id.scrollView);
        View scrollDividerTop = findViewById(R.id.scrollDividerTop);
        View scrollDividerBottom = findViewById(R.id.scrollDividerBottom);
        assert scrollDividerTop != null;
        assert scrollDividerBottom != null;
        HelperUtils.setupDialogDividers(scrollView, scrollDividerTop, scrollDividerBottom);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        MaterialButton btnClearNow = findViewById(R.id.btn_clear_now);
        btnClearNow.setOnClickListener(v -> {
            CoordinatorLayout root = findViewById(R.id.root);
            Snackbar snackbarBottom = Snackbar.make(root, R.string.hint_database, Snackbar.LENGTH_SHORT);
            HelperUtils.makeSnackbarRound(snackbarBottom);
            snackbarBottom.setAction(this.getString(R.string.app_ok), r -> {

                DeleteUtils.executeDeletion(
                        this,
                        prefs.getBoolean("delete_cache", false),
                        prefs.getBoolean("delete_cookies", false),
                        prefs.getBoolean("delete_database", false),
                        prefs.getBoolean("delete_settings", false),
                        prefs.getBoolean("delete_other", false),
                        prefs.getBoolean("delete_history", false)
                );

                if (prefs.getBoolean("delete_settings", false)) {
                    finish();
                }
            });

            snackbarBottom.show();
        });

        // OPTIMIERUNG: commit() reicht aus, da es sich um das initiale Laden im onCreate handelt
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, new Fragment_settings_Clear())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_help, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) finish();
        else if (menuItem.getItemId() == R.id.menu_help) {
            Uri webpage = Uri.parse("https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki/Delete");
            BrowserUnit.intentURL(this, webpage);
        }
        return true;
    }
}