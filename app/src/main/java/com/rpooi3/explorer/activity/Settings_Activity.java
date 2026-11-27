package com.rpooi3.explorer.activity;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.Objects;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.preferences.SettingsSections;
import com.rpooi3.explorer.site.SiteCategory;
import com.rpooi3.explorer.site.SiteCategoryFragment;
import com.rpooi3.explorer.fragment.Fragment_settings;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;

public class Settings_Activity extends AppCompatActivity {

    /** Key of the topic to show (see {@link SettingsSections}); absent for the first screen. */
    public static final String EXTRA_SECTION = "com.rpooi3.explorer.settings.SECTION";


    /** Key of the {@link SiteCategory} whose screen to show (Site settings > Location and so on). */
    public static final String EXTRA_SITE_CATEGORY = "com.rpooi3.explorer.settings.SITE_CATEGORY";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HelperUtils.initTheme(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        FrameLayout content_frame = findViewById(R.id.content_frame);
        View scrollDividerTop = findViewById(R.id.scrollDividerTop);
        View scrollDividerBottom = findViewById(R.id.scrollDividerBottom);
        assert scrollDividerTop != null;
        assert scrollDividerBottom != null;
        HelperUtils.setupDialogDividers(content_frame, scrollDividerTop, scrollDividerBottom);

        // OPTIMIERUNG: commit() reicht aus, da es sich um das initiale Laden im onCreate handelt
        // Without a topic this is the first screen, which lists the topics and has the search field.
        final SettingsSections.Section section = SettingsSections.byKey(getIntent().getStringExtra(EXTRA_SECTION));
        if (section != null) setTitle(section.getTitle());
        final SiteCategory siteCategory = SiteCategory.byKey(getIntent().getStringExtra(EXTRA_SITE_CATEGORY));
        if (siteCategory != null) {
            setTitle(siteCategory.getTitle());
            if (savedInstanceState == null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.content_frame, SiteCategoryFragment.newInstance(siteCategory.getKey()))
                        .commit();
            }
            return;
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, section == null ? new Fragment_settings() : Fragment_settings.forSection(section.getKey()))
                    .commit();
        }
        if (section == null) initSearch();
    }

    /** The search field above the list filters the settings of this screen while typing. */
    private void initSearch() {
        final View searchField = findViewById(R.id.settings_search);
        final EditText input = findViewById(R.id.settings_search_input);
        final View clear = findViewById(R.id.settings_search_clear);
        final View empty = findViewById(R.id.settings_search_empty);
        searchField.setVisibility(View.VISIBLE);
        searchField.setOnClickListener(v -> {
            input.requestFocus();
            HelperUtils.showSoftKeyboard(input);
        });
        clear.setOnClickListener(v -> input.setText(""));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                final String query = s.toString();
                clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (getSupportFragmentManager().findFragmentById(R.id.content_frame) instanceof Fragment_settings settings) {
                    empty.setVisibility(0 == settings.filter(query) ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) finish();
        return true;
    }
}
