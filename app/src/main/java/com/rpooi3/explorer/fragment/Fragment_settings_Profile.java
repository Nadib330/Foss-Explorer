package com.rpooi3.explorer.fragment;

import android.content.Context;
import android.os.Bundle;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import java.util.Objects;
import com.rpooi3.explorer.R;
import com.rpooi3.explorer.browser.AdBlocker;
import com.rpooi3.explorer.browser.BannerBlock;
import com.rpooi3.explorer.preferences.BasePreferenceFragment;

public class Fragment_settings_Profile extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_profile_standard, rootKey);
        PreferenceManager.setDefaultValues(requireContext(), R.xml.preference_profile_standard, false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        // Da standardmäßige Java-Asserts auf Android-Geräten im Release-Build ignoriert werden,
        // fangen wir einen potenziellen Null-Key direkt durch die umgedrehte String-Prüfung ab.
        if (key == null) return;
        // Einmalig den Context holen und auf null prüfen (verhindert Abstürze beim Schließen des Fragments)
        Context context = getActivity();
        if (context == null) return;
        // 1. Trigger für den AdBlocker
        if ("profileStandard_adBlock".equals(key) && sp.getBoolean("profileStandard_adBlock", false)) {
            AdBlocker.ensure(context);
        }
        // 2. Trigger für den Cookie-Banner-Blocker (Korrektur: Null-Safe & Yoda-Condition)
        if ("profileStandard_deny_cookie_banners".equals(key) && sp.getBoolean("profileStandard_deny_cookie_banners", false)) {
            BannerBlock.downloadBanners(context);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(this);
    }
}