package com.rpooi3.explorer.fragment;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.preferences.BasePreferenceFragment;

public class Fragment_settings_Clear extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preference_clear, rootKey);
        initSummary(getPreferenceScreen());
    }

    private void initSummary(@Nullable Preference p) {
        if (p == null) return;

        if (p instanceof PreferenceGroup pGrp) {
            int count = pGrp.getPreferenceCount();
            for (int i = 0; i < count; i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            try {
                updatePrefSummary(p);
            } catch (Exception e) {
                Log.i(TAG, "Settings:" + e);
            }
        }
    }

    private void updatePrefSummary(@NonNull Preference p) {
        if ("auto_delete_exit".equals(p.getKey())) {
            CharSequence title = p.getTitle();
            if (title != null) {
                Context context = p.getContext();
                TypedValue typedValue = new TypedValue();
                int color = 0xFFFF0000; // Fallback-Rot

                // colorError aus dem Theme holen
                if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.errorTextColor, typedValue, true)) {
                    color = typedValue.data;
                } else if (context.getTheme().resolveAttribute(android.R.attr.colorError, typedValue, true)) {
                    color = typedValue.data;
                }

                // Trick: StringBuilder verwenden und ein unsichtbares Zeichen anhängen
                android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder(title.toString());
                builder.setSpan(
                        new ForegroundColorSpan(color),
                        0,
                        builder.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                // Das unsichtbare Zeichen verhindert das "Flachklopfen" zu reinem Text im Framework
                builder.append("\u200E");

                p.setTitle(builder);
            }
        }
    }


    @Override
    public void onSharedPreferenceChanged(@Nullable SharedPreferences sharedPreferences, @Nullable String key) {
        if (key == null) return;
        Preference pref = findPreference(key);
        if (pref != null) {
            updatePrefSummary(pref);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getPreferenceScreen() != null && getPreferenceScreen().getSharedPreferences() != null) {
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getPreferenceScreen() != null && getPreferenceScreen().getSharedPreferences() != null) {
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}
