package com.rpooi3.explorer.view;

import static android.view.View.GONE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import java.util.List;
import com.rpooi3.explorer.R;

public class AdapterGrid extends BaseAdapter {
    private final List<GridItem> list;
    private final Context context;
    private final SharedPreferences sp; // OPTIMIERUNG: Nur einmal instanziieren

    public AdapterGrid(Context context, List<GridItem> list) {
        this.context = context;
        this.list = list;
        this.sp = com.rpooi3.explorer.profile.ProfilePrefs.settings(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        View view = convertView;

        // 1. RECYCLING: View & Holder nur erstellen, wenn nötig
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_overflow, parent, false);
            holder = new Holder();
            holder.title = view.findViewById(R.id.gridTitle);
            holder.cardView = view.findViewById(R.id.menuCardView);
            holder.iconMenu = view.findViewById(R.id.gridIcon);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }

        // 2. DATEN-BINDUNG: Muss AUSSERHALB des if-Blocks stehen, damit Scrollen funktioniert!
        GridItem item = list.get(position);
        String text = item.title();
        holder.title.setText(text);

        try {
            holder.iconMenu.setImageResource(item.data());
        } catch (Exception e) {
            Log.e("Foss Explorer", "Icon-Error: " + e);
        }

        // 3. FARB-LOGIK (Gekürzt & Performant)
        if ("true".equals(sp.getString("showFilterDialogX", "false"))) {

            int strokeWidthInPixels = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 0.5f, holder.cardView.getContext().getResources().getDisplayMetrics()
            );
            holder.cardView.setStrokeWidth(strokeWidthInPixels);

            TypedValue outlineColor = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, outlineColor, true);
            holder.cardView.setStrokeColor(outlineColor.data);

            holder.iconMenu.setVisibility(GONE);
            int colorRes;
            if (text.equals(sp.getString("icon_01", context.getString(R.string.color_red)))) colorRes = R.color.red;
            else if (text.equals(sp.getString("icon_02", context.getString(R.string.color_pink)))) colorRes = R.color.pink;
            else if (text.equals(sp.getString("icon_03", context.getString(R.string.color_purple)))) colorRes = R.color.purple;
            else if (text.equals(sp.getString("icon_04", context.getString(R.string.color_blue)))) colorRes = R.color.blue;
            else if (text.equals(sp.getString("icon_05", context.getString(R.string.color_teal)))) colorRes = R.color.teal;
            else if (text.equals(sp.getString("icon_06", context.getString(R.string.color_green)))) colorRes = R.color.green;
            else if (text.equals(sp.getString("icon_07", context.getString(R.string.color_lime)))) colorRes = R.color.lime;
            else if (text.equals(sp.getString("icon_08", context.getString(R.string.color_yellow)))) colorRes = R.color.yellow;
            else if (text.equals(sp.getString("icon_09", context.getString(R.string.color_orange)))) colorRes = R.color.orange;
            else if (text.equals(sp.getString("icon_10", context.getString(R.string.color_brown)))) colorRes = R.color.brown;
            else if (text.equals(sp.getString("icon_11", context.getString(R.string.color_grey)))) colorRes = R.color.grey;
            else {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true);
                holder.cardView.setCardBackgroundColor(typedValue.data);
                return view;
            }

            if (colorRes != 0) {
                holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), colorRes, null));
            }



        } else {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.color.transparent, typedValue, true);
            holder.iconMenu.setVisibility(View.VISIBLE);
            holder.cardView.setCardBackgroundColor(typedValue.data);
        }

        return view;
    }

    @Override
    public int getCount() { return list.size(); }
    @Override
    public Object getItem(int pos) { return list.get(pos); }
    @Override
    public long getItemId(int pos) { return pos; }

    private static class Holder {
        TextView title;
        com.google.android.material.card.MaterialCardView cardView;
        ImageView iconMenu;
    }
}
