package com.rpooi3.explorer.activity;

import android.view.Menu;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.Objects;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.fragment.Fragment_settings_Backup;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;

public class Settings_Backup extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HelperUtils.initTheme(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationIcon(R.drawable.icon_back_stylized);
        toolbar.setNavigationContentDescription(R.string.bar_back);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        FrameLayout content_frame = findViewById(R.id.content_frame);
        View scrollDividerTop = findViewById(R.id.scrollDividerTop);
        View scrollDividerBottom = findViewById(R.id.scrollDividerBottom);
        assert scrollDividerTop != null;
        assert scrollDividerBottom != null;
        HelperUtils.setupDialogDividers(content_frame, scrollDividerTop, scrollDividerBottom);

        // OPTIMIERUNG: commit() reicht aus, da es sich um das initiale Laden im onCreate handelt
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, new Fragment_settings_Backup())
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
        if (menuItem.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        } else if (menuItem.getItemId() == R.id.menu_help) {
            Uri webpage = Uri.parse("https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki/Backup");
            BrowserUnit.intentURL(this, webpage);
        }
        return true;
    }
}