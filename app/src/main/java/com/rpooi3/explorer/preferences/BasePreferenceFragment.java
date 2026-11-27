package com.rpooi3.explorer.preferences;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Objects;
import com.rpooi3.explorer.R;

public class BasePreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {}

    /**
     * 18.0.0 beta 9 (item 3): every setting belongs to the profile in front, so every settings
     * screen reads and writes the preferences of that profile — `…_preferences@<id>` for the
     * second profile and the ones after it, and the plain file for the first, which is the one an
     * update to 18.0.0 finds. The screens of the profiles themselves (their names, their colours,
     * the list of them) are not settings of a profile and stay where they were.
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final PreferenceManager manager = getPreferenceManager();
        manager.setSharedPreferencesName(
                com.rpooi3.explorer.profile.ProfilePrefs.nameOf(requireContext(), null));
        manager.setSharedPreferencesMode(android.content.Context.MODE_PRIVATE);
    }

    /** Every settings screen is drawn as groups of rounded cards, see {@link SettingsCards}. */
    @Override
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        if (preferenceScreen != null) SettingsCards.style(preferenceScreen);
        super.setPreferenceScreen(preferenceScreen);
    }

    @NonNull
    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        return SettingsCards.createAdapter(preferenceScreen);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setDivider(null);
        final RecyclerView list = getListView();
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.spacing_xlarge));
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (preference instanceof ListPreference listPref) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(listPref.getTitle())
                    .setNegativeButton(R.string.app_cancel, null)
                    .setSingleChoiceItems(listPref.getEntries(), listPref.findIndexOfValue(listPref.getValue()), (dialog, index) -> {
                        String newValue = listPref.getEntryValues()[index].toString();
                        if (listPref.callChangeListener(newValue)) listPref.setValue(newValue);
                        dialog.dismiss();
                    }).show();
        } else if (preference instanceof EditTextPreference editPref) {
            View dialogView = View.inflate(requireContext(), R.layout.dialog_edit_text, null);
            TextInputEditText input = dialogView.findViewById(R.id.textInput);
            input.setText(editPref.getText());

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(editPref.getTitle())
                    .setView(dialogView)
                    .setNegativeButton(R.string.app_cancel, null)
                    .setPositiveButton(R.string.app_ok, (d, i) -> {
                        String newValue = Objects.requireNonNull(input.getText()).toString();
                        if (editPref.callChangeListener(newValue)) editPref.setText(newValue);
                    }).show();
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}