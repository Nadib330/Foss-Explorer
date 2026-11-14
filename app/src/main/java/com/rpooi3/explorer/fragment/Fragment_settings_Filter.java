package com.rpooi3.explorer.fragment;

import android.os.Bundle;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.preferences.BasePreferenceFragment;

public class Fragment_settings_Filter extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_filter, rootKey);
    }
}