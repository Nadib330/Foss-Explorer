package com.rpooi3.explorer.preferences;

import com.rpooi3.explorer.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;
import com.google.android.material.checkbox.MaterialCheckBox;

public class EditTextSwitchPreference extends EditTextPreference {
    private String switchKey;
    private boolean defaultValue;

    private static final int[] COLORS = {
            R.color.red, R.color.pink, R.color.purple, R.color.blue, R.color.teal,
            R.color.green, R.color.lime, R.color.yellow, R.color.orange, R.color.brown, R.color.grey
    };

    public EditTextSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_checkbox_widget);
        if (attrs != null) {
            // TypedArray is only AutoCloseable from API 31, so recycle it explicitly.
            TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EditTextSwitchPreference, 0, 0);
            try {
                switchKey = a.getString(R.styleable.EditTextSwitchPreference_editTextSwitchKey);
                defaultValue = a.getBoolean(R.styleable.EditTextSwitchPreference_editTextSwitchKeyDefaultValue, false);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        MaterialCheckBox box = (MaterialCheckBox) holder.findViewById(R.id.custom_checkbox_widget);
        Context ctx = getContext();

        if (box != null && switchKey != null) {
            SharedPreferences sp = com.rpooi3.explorer.profile.ProfilePrefs.settings(ctx);
            boolean isChecked = sp.getBoolean(switchKey, defaultValue);

            box.setOnCheckedChangeListener(null);
            box.setChecked(isChecked);
            box.setFocusable(true);
            box.setClickable(true);
            box.setEnabled(true);

            box.setOnCheckedChangeListener((b, checked) -> sp.edit().putBoolean(switchKey, checked).apply());
        }

        View.OnClickListener openDialogListener = v -> super.onClick();
        View titleView = holder.findViewById(android.R.id.title);
        if (titleView != null) {
            titleView.setOnClickListener(openDialogListener);
        }

        View summaryView = holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setOnClickListener(openDialogListener);
        }
        holder.itemView.setOnClickListener(null);
        holder.itemView.setClickable(false);

        ViewGroup.MarginLayoutParams params = null;
        if (holder.itemView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        }

        if (switchKey != null && switchKey.startsWith("filter_")) {
            try {
                int num = Integer.parseInt(switchKey.substring(7));
                int color = 0;

                if (num >= 1 && num <= 11) {
                    color = ContextCompat.getColor(ctx, COLORS[num - 1]);
                } else if (num == 12) {
                    TypedValue tv = new TypedValue();
                    ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, tv, true);
                    color = tv.data;
                }

                if (color != 0) {
                    // Erstellt ein abgerundetes Rechteck im Code
                    GradientDrawable shape = new GradientDrawable();
                    shape.setShape(GradientDrawable.RECTANGLE);

                    // Ecken-Radius festlegen (z.B. 8dp für eine schöne moderne Rundung)

                    int rounded = getContext().getResources().getDimensionPixelSize(R.dimen.spacing_large);
                    shape.setCornerRadius(rounded);
                    shape.setColor(color);

                    // Hintergrund der Zeile zuweisen
                    holder.itemView.setBackground(shape);
                }

                // 5dp Margin an JEDER Seite setzen (oben, unten, links, rechts)
                if (params != null) {
                    int hor = getContext().getResources().getDimensionPixelSize(R.dimen.spacing_large);
                    int ver = getContext().getResources().getDimensionPixelSize(R.dimen.spacing_xsmall);

                    params.setMargins(hor, ver, hor, ver);
                    holder.itemView.setLayoutParams(params);
                }
            } catch (NumberFormatException ignored) {
            }
        } else {
            // Standard-Hintergrund wiederherstellen, falls kein Filter aktiv ist
            holder.itemView.setBackground(null);

            // Margins zurücksetzen
            if (params != null) {
                params.setMargins(0, 0, 0, 0);
                holder.itemView.setLayoutParams(params);
            }
        }
    }

    @Override
    public void onClick() {
        // Bleibt leer, um den automatischen Zeilen-Klick von AndroidX zu blockieren
    }
}
