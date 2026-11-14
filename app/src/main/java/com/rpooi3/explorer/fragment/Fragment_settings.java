package com.rpooi3.explorer.fragment;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import java.util.Objects;
import com.rpooi3.explorer.R;
import com.rpooi3.explorer.theme.ExplorerTheme;
import com.rpooi3.explorer.activity.*;
import com.rpooi3.explorer.browser.AdBlock;
import com.rpooi3.explorer.preferences.BasePreferenceFragment;
import com.rpooi3.explorer.preferences.SettingsCards;
import com.rpooi3.explorer.preferences.HomeCustomizationScreen;
import com.rpooi3.explorer.preferences.SettingsScreens;
import com.rpooi3.explorer.preferences.SettingsSections;
import com.rpooi3.explorer.site.SiteSettingsScreen;

public class Fragment_settings extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference sp_ad_block;
    private SharedPreferences sp;

    private static final String ARG_SECTION = "section";

    /** Android's picker for a picture; used by "Home page customization" for the wallpaper. */
    private final androidx.activity.result.ActivityResultLauncher<String> wallpaperPicker = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            uri -> HomeCustomizationScreen.onImagePicked(this, uri));

    /** The settings of one topic; see {@link SettingsSections}. */
    public static Fragment_settings forSection(String sectionKey) {
        final Fragment_settings fragment = new Fragment_settings();
        final Bundle args = new Bundle();
        args.putString(ARG_SECTION, sectionKey);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final SettingsSections.Section section = SettingsSections.byKey(
                getArguments() == null ? null : getArguments().getString(ARG_SECTION));
        // Without a topic this is the first screen: the list of topics.
        setPreferencesFromResource(section == null ? R.xml.preference_setting : section.getXml(), rootKey);
        Context context = requireContext();
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        initSummary(getPreferenceScreen());
        sp_ad_block = findPreference("ab_hosts");
        if (sp_ad_block != null) {
            updateAdBlockSummary();
        }
        if (section == null) {
            SettingsSections.attachKeywords(getPreferenceScreen());
        }
        // Rows that open a topic, or a screen behind a topic's row.
        SettingsSections.bindNavigation(this);
        SettingsScreens.bind(this);
        HomeCustomizationScreen.bind(this, () -> wallpaperPicker.launch("image/*"));
        SettingsSections.bindInfo(this);
        SiteSettingsScreen.bind(this);
        setClick("settings_profile", Settings_Profile.class);
        setClick("settings_filter", Settings_Filter.class);
        setClick("settings_data", Settings_Backup.class);
        setClick("settings_gesture", Settings_Gesture.class);
        setClick("settings_clear", Settings_Clear.class);
        setClick("edit_standard", Settings_ProfileList.class);
    }

    /**
     * Shows only the settings whose title or summary contains {@code query}.
     * Returns how many settings are left; an empty query shows all of them.
     */
    public int filter(String query) {
        if (getPreferenceScreen() == null) return 0;
        return SettingsCards.filter(getPreferenceScreen(), query == null ? "" : query,
                pref -> !"useDynamicColor".equals(pref.getKey()) || ExplorerTheme.isDynamicColorAvailable());
    }

    private void setClick(String key, Class<?> cls) {
        Preference p = findPreference(key);
        if (p != null) p.setOnPreferenceClickListener(pref -> {
            startActivity(new Intent(getActivity(), cls));
            return false;
        });
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup pGrp) {
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            try { updatePrefSummary(p); }
            catch (Exception e) { Log.i(TAG, "Settings:" + e); }
        }
    }

    private void updatePrefSummary(Preference p) {
        if (p == null) return;

        if (p instanceof EditTextPreference) {
            p.setSummaryProvider(pref -> {
                String text = ((EditTextPreference) pref).getText();
                // An empty start page is a valid choice (the home page), not a missing input.
                if ("favoriteURL".equals(pref.getKey()) && (text == null || text.trim().isEmpty())) {
                    return getString(R.string.setting_summary_fav);
                }
                return (text == null || text.trim().isEmpty()) ? getString(R.string.toast_input_empty) : text;
            });
        }

        if (p instanceof ListPreference listPref) {
            if (p.getSummaryProvider() == null) p.setSummary(listPref.getEntry());

            // Wenn die geänderte Preference die AdBlock-Liste selbst ist, aktualisieren wir sie
            if ("ab_hosts".equals(p.getKey())) {
                updateAdBlockSummary();
            }
        }

        Preference dynamicColor = findPreference("useDynamicColor");
        if (dynamicColor != null) dynamicColor.setVisible(ExplorerTheme.isDynamicColorAvailable());

    }

    // Ausgelagerte Hilfsmethode, um Code-Duplikate zu vermeiden
    private void updateAdBlockSummary() {
        if (sp_ad_block instanceof ListPreference listPref) {
            sp_ad_block.setSummary(AdBlock.getHostsDate(requireContext()) + ":\n" + listPref.getEntry());
        } else if (sp_ad_block != null) {
            sp_ad_block.setSummary(AdBlock.getHostsDate(requireContext()));
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, String key) {
        if (key == null) return;

        updatePrefSummary(findPreference(key));

        if ("sp_theme".equals(key) || "useDynamicColor".equals(key)) {
            sp.edit().putInt("restart_changed", 1).apply();
        }


        if ("ab_hosts".equals(key)) {
            Context context = getActivity();
            if (context != null) {
                new AdBlock(context).triggerHostsDownload(context);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from a category's screen: its state may have changed.
        SiteSettingsScreen.bind(this);
        // The summaries of rows that lead to a screen say what is chosen there.
        SettingsScreens.bind(this);
        HomeCustomizationScreen.refresh(this);
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(this);
    }
}
