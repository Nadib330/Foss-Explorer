package com.rpooi3.explorer.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.activity.BrowserActivity;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.unit.CustomItemsUtils;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;

public class AdapterCustomList extends RecyclerView.Adapter<AdapterCustomList.UniversalViewHolder> {

    public static final int TYPE_REDIRECTS = 0;
    public static final int TYPE_SEARCHES = 1;

    private final ArrayList<CustomItemsUtils.CustomItem> redirects;
    private final Context context;
    private final int adapterType;
    private final String searchUrl;
    private final OnSaveListener saveListener;

    public interface OnSaveListener {
        void onSave(ArrayList<CustomItemsUtils.CustomItem> list);
    }

    // Konstruktor für Redirects
    public AdapterCustomList(Context context, ArrayList<CustomItemsUtils.CustomItem> redirects, OnSaveListener saveListener) {
        this(context, redirects, TYPE_REDIRECTS, null, saveListener);
    }

    // Konstruktor für Custom Searches
    public AdapterCustomList(Context context, String searchUrl, ArrayList<CustomItemsUtils.CustomItem> redirects, OnSaveListener saveListener) {
        this(context, redirects, TYPE_SEARCHES, searchUrl, saveListener);
    }

    private AdapterCustomList(Context context, ArrayList<CustomItemsUtils.CustomItem> redirects, int adapterType, String searchUrl, OnSaveListener saveListener) {
        super();
        this.context = context;
        this.redirects = redirects;
        this.adapterType = adapterType;
        this.searchUrl = searchUrl;
        this.saveListener = saveListener;
    }
    @NonNull
    @Override
    public UniversalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Lädt standardmäßig das erweiterte item_list Layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list, parent, false);
        return new UniversalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UniversalViewHolder holder, int position) {
        SharedPreferences sp = com.rpooi3.explorer.profile.ProfilePrefs.settings(context);
        CustomItemsUtils.CustomItem current = redirects.get(position);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true);
        holder.albumCardView.setCardBackgroundColor(typedValue.data);

        holder.source.setText(current.source());
        holder.target.setText(current.target());

        // ==========================================
        // GEMEINSAMES FAVICON FÜR BEIDE MODI
        // ==========================================
        if (holder.itemIcon != null) {
            FaviconHelper.setFavicon(context, holder.itemIcon, current.target(), R.id.icon_start, R.drawable.icon_image_broken);
        }

        // ==========================================
// DYNAMISCHE SICHTBARKEITS-SCHALTUNG (KORRIGIERT)
// ==========================================
        if (adapterType == TYPE_REDIRECTS) {
            if (holder.checkboxRedirect != null) holder.checkboxRedirect.setVisibility(VISIBLE);

            // ÄNDERUNG: Wenn die Mülltonne AUCH bei Redirects da sein soll -> VISIBLE.
            // Falls sie dort unsichtbar sein sollte, stimmte dein Code, aber das Icon war standardmäßig im XML auf GONE.
            if (holder.remove != null) holder.remove.setVisibility(VISIBLE);

            if (holder.checkboxRedirect != null && holder.textGroup != null) {
                holder.checkboxRedirect.setOnCheckedChangeListener(null);
                holder.checkboxRedirect.setChecked(sp.getBoolean(current.source(), false));
                holder.checkboxRedirect.setOnCheckedChangeListener((buttonView, isChecked) -> sp.edit().putBoolean(current.source(), isChecked).apply());
                holder.textGroup.setOnClickListener(iV -> showEditDialog(holder, current, sp));
            }
            holder.itemView.setOnClickListener(null);
        } else {
            if (holder.checkboxRedirect != null) holder.checkboxRedirect.setVisibility(GONE); // Checkbox aus
            if (holder.remove != null) holder.remove.setVisibility(VISIBLE); // Mülltonne an

            HelperUtils.setHighlightedText(context, holder.target, HelperUtils.domain(current.target()), current.target());
            holder.itemView.setOnClickListener(v -> handleSearchClick(current));
        }


        // Gemeinsame Löschlogik (Greift auf das sichtbare remove-Icon im Suchmodus zu)
        if (holder.remove != null) {
            holder.remove.setOnClickListener(v -> {
                Snackbar snackbar = Snackbar.make(holder.itemView, R.string.hint_database, Snackbar.LENGTH_SHORT);
                HelperUtils.makeSnackbarRound(snackbar);
                snackbar.setAction(context.getString(R.string.app_ok), v2 -> {
                    int currentPos = holder.getBindingAdapterPosition();
                    if (currentPos == RecyclerView.NO_POSITION) return;

                    CustomItemsUtils.CustomItem targetRedirect = redirects.get(currentPos);
                    if (adapterType == TYPE_REDIRECTS) {
                        sp.edit().remove(targetRedirect.source()).apply();
                    }
                    redirects.remove(currentPos);
                    notifyItemRemoved(currentPos);
                    notifyItemRangeChanged(currentPos, getItemCount());
                    triggerSave();
                });
                snackbar.show();
            });
        }
    }

    private void handleSearchClick(CustomItemsUtils.CustomItem current) {
        NinjaWebView.getBrowserController().hideSearch();
        String targetUrl = current.target();
        if (targetUrl == null || targetUrl.isEmpty() || searchUrl == null) return;

        String encodedQuery;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                encodedQuery = URLEncoder.encode(searchUrl, StandardCharsets.UTF_8);
            } else {
                encodedQuery = URLEncoder.encode(searchUrl, StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            Log.e("CustomSearch", "Encoding failed", e);
            encodedQuery = searchUrl;
        }

        String finalUrl = targetUrl.contains("%s") ? targetUrl.replace("%s", encodedQuery) : targetUrl + encodedQuery;

        if (BrowserUnit.isURL(finalUrl)) {
            BrowserUnit.intentURL(context, Uri.parse(finalUrl));
        } else {
            CatToast.show(BrowserActivity.getAppContext(), R.string.app_error, 2);
        }
    }

    private void showEditDialog(UniversalViewHolder holder, CustomItemsUtils.CustomItem current, SharedPreferences sp) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.create_new_redirect, null);

        TextInputEditText source = dialogView.findViewById(R.id.source);
        TextInputEditText target = dialogView.findViewById(R.id.target);

        source.setText(current.source());
        target.setText(current.target());

        builder.setTitle(R.string.privacy_redirect_title);
        builder.setIcon(R.drawable.icon_redirect);
        builder.setNegativeButton(R.string.app_cancel, null);
        builder.setPositiveButton(R.string.app_ok, (dialogInterface, i) -> {
            String sourceText = Objects.requireNonNull(source.getText()).toString().trim();
            String targetText = Objects.requireNonNull(target.getText()).toString().trim();
            if (targetText.isEmpty() && sourceText.isEmpty()) return;
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                CustomItemsUtils.CustomItem oldRedirect = redirects.get(currentPos);
                sp.edit().remove(oldRedirect.source()).apply();
                CustomItemsUtils.CustomItem updatedRedirect = new CustomItemsUtils.CustomItem(sourceText, targetText);
                redirects.set(currentPos, updatedRedirect);
                sp.edit().putBoolean(sourceText, true).apply();
                notifyItemChanged(currentPos);
                triggerSave();
            }
        });
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUtils.setupDialog(context, dialog);
    }

    public void triggerSave() {
        if (saveListener != null) {
            try {
                saveListener.onSave(redirects);
            } catch (Exception e) {
                Log.e("UniversalAdapter", "Failed to save data", e);
            }
        }
    }

    @Override
    public int getItemCount() {
        return redirects.size();
    }

    public ArrayList<CustomItemsUtils.CustomItem> getRedirects() {
        return redirects;
    }

    public void addRedirect(CustomItemsUtils.CustomItem redirect) {
        redirects.add(redirect);
        if (adapterType == TYPE_REDIRECTS) {
            SharedPreferences sp = com.rpooi3.explorer.profile.ProfilePrefs.settings(context);
            sp.edit().putBoolean(redirect.source(), true).apply();
        }
        notifyItemInserted(getItemCount() - 1);
    }

    public static class UniversalViewHolder extends RecyclerView.ViewHolder {
        final TextView source;
        final TextView target;
        final ImageView remove;
        final CardView albumCardView;
        final ImageView itemIcon;
        final CheckBox checkboxRedirect;
        final LinearLayout textGroup;

        UniversalViewHolder(@NonNull View itemView) {
            super(itemView);
            source = itemView.findViewById(R.id.titleView);
            target = itemView.findViewById(R.id.dateView);
            albumCardView = itemView.findViewById(R.id.item_CardViewItem);
            itemIcon = itemView.findViewById(R.id.icon_start); // Linkes Icon (Edit oder Favicon)
            remove = itemView.findViewById(R.id.icon_end); // Rechtes Lösch-Icon (icon_end)
            checkboxRedirect = itemView.findViewById(R.id.item_checkBox); // Rechte Checkbox
            textGroup = itemView.findViewById(R.id.textGroup);
        }
    }
}