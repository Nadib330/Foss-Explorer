package com.rpooi3.explorer.view;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Objects;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.unit.CustomItemsUtils;
import com.rpooi3.explorer.unit.HelperUtils;

public class CustomRedirectsDialog extends DialogFragment {
    AdapterCustomList adapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = View.inflate(getContext(), R.layout.custom_redirects_list, null);

        // 1. RecyclerView finden und LayoutManager im Code setzen (Standard: von oben nach unten)
        RecyclerView recyclerView = dialogView.findViewById(R.id.redirects_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // 2. UI-Elemente anpassen (Nachricht ausblenden, Spacer einblenden)
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message_text);
        if (messageTextView != null) messageTextView.setVisibility(View.GONE);

        View spacer = dialogView.findViewById(R.id.spacer);
        if (spacer != null) spacer.setVisibility(View.VISIBLE);

        // 3. Daten laden & Adapter setzen
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
        ArrayList<CustomItemsUtils.CustomItem> redirects = new ArrayList<>();
        try {
            redirects = CustomItemsUtils.getRedirects(sp);
        } catch (JSONException e) {
            Log.e("Redirects parsing", e.toString());
        }

        adapter = new AdapterCustomList(requireContext(), redirects, list -> {
            try {
                CustomItemsUtils.saveRedirects(list);
            } catch (JSONException e) {
                Log.e("Redirects saving", e.toString());
            }
        });
        recyclerView.setAdapter(adapter);

        // 4. Dialog-Metadaten und Buttons zuweisen
        builder.setTitle(R.string.privacy_redirect_title);
        builder.setIcon(R.drawable.icon_redirect);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.app_cancel, null);
        builder.setPositiveButton(R.string.create_new, null);

        // 5. Divider initialisieren
        View scrollDividerBottom = dialogView.findViewById(R.id.scrollDividerBottom);
        View scrollDividerTop = dialogView.findViewById(R.id.scrollDividerTop);
        if (scrollDividerBottom != null && scrollDividerTop != null) {
            HelperUtils.setupDialogDividers(recyclerView, scrollDividerTop, scrollDividerBottom);
        }

        // 6. Dialog erstellen und formatieren
        AlertDialog dialog = builder.create();
        HelperUtils.setupDialog(requireContext(), dialog);

        // Klick-Verhalten für "Erstellen" überschreiben
        dialog.setOnShowListener(dI -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (b != null) {
                b.setOnClickListener(view -> showCreateNewDialog(recyclerView));
            }
        });

        return dialog;
    }


    private void showCreateNewDialog(RecyclerView recyclerView) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = View.inflate(getContext(), R.layout.create_new_redirect, null);
        TextInputEditText source = dialogView.findViewById(R.id.source);
        TextInputEditText target = dialogView.findViewById(R.id.target);

        TextInputLayout sourceLayout = dialogView.findViewById(R.id.sourceLayout);
        TextInputLayout targetLayout = dialogView.findViewById(R.id.targetLayout);

        if (sourceLayout != null) sourceLayout.setHint(getString(R.string.source));
        if (targetLayout != null) targetLayout.setHint(getString(R.string.target));

        builder.setTitle(R.string.privacy_redirect_title);
        builder.setIcon(R.drawable.icon_redirect);
        builder.setNegativeButton(R.string.app_cancel, null);

        builder.setPositiveButton(R.string.app_ok, ((dialogInterface, i) -> {
            String sourceText = Objects.requireNonNull(source.getText()).toString().trim();
            String targetText = Objects.requireNonNull(target.getText()).toString().trim();

            if (targetText.isEmpty() && sourceText.isEmpty()) {
                CatToast.show(requireContext(), R.string.toast_input_empty, 3);
                return;
            }

            adapter.addRedirect(new CustomItemsUtils.CustomItem(sourceText, targetText));
            try {
                CustomItemsUtils.saveRedirects(adapter.getRedirects());

                // Automatisches Scrollen ans Ende der Liste erzwingen
                if (recyclerView != null && adapter != null) {
                    recyclerView.post(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }));

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUtils.setupDialog(requireContext(), dialog);
    }
}
