package com.rpooi3.explorer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.database.Record;
import com.rpooi3.explorer.unit.HelperUtils;

public class AdapterRecord extends ArrayAdapter<Record> {
    private final Context context;
    private final int layoutResId = R.layout.item_list;
    private final List<Record> list;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
    private final FaviconHelper faviconHelper;
    private final int defaultSurfaceColor;

    // RAM-Cache für flüssiges Scrollen (0ms Verzögerung)
    private final android.util.LruCache<String, Bitmap> bitmapCache = new android.util.LruCache<>(40);

    private static final int[] COLOR_RES_IDS = {
            0, R.color.grey, R.color.brown, R.color.orange, R.color.yellow,
            R.color.lime, R.color.green, R.color.teal, R.color.blue,
            R.color.purple, R.color.pink, R.color.red
    };

    public AdapterRecord(Context context, List<Record> list) {
        super(context, R.layout.item_list, list);
        this.context = context;
        this.list = list;
        this.faviconHelper = new FaviconHelper(context.getApplicationContext()); // getApplicationContext verhindert Memory-Leaks

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true);
        this.defaultSurfaceColor = typedValue.data;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(layoutResId, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        Record record = list.get(position);
        String url = record.getUrl();
        long time = record.getTime();

        holder.title.setText(record.getTitle());

        // 1. Text setzen und Highlighting anwenden (Zeit für Verlauf / Domain für Bookmarks)
        if (time > 1) {
            String timeString = sdf.format(new java.util.Date(time));
            String fullText = timeString + ": " + url;
            holder.time.setText(fullText);
            // WICHTIG: Wir übergeben hier 'null' als URL. Dadurch greift in deiner Methode
            // automatisch die Bedingung (url == null), und der Zeitstempel wird
            // in der auffälligen Farbe 'colorError' (z.B. Rot/Orange) hervorgehoben!
            // HelperUtils.setHighlightedText(context, holder.time, timeString, null);
            HelperUtils.setHighlightedText(context, holder.time, timeString, timeString);
        } else {
            holder.time.setText(url);
            // Für Bookmarks bleibt alles wie gehabt (nutzt die Whitelist-Prüfung mit der echten URL)
            HelperUtils.setHighlightedText(context, holder.time, HelperUtils.domain(url), url);
        }

        // 2. Hintergrundfarbe über Array-Lookup bestimmen
        long filter = record.getIconColor();
        if (filter >= 1 && filter < COLOR_RES_IDS.length) {
            int color = ResourcesCompat.getColor(context.getResources(), COLOR_RES_IDS[(int) filter], null);
            holder.cardView.setCardBackgroundColor(color);
        } else {
            holder.cardView.setCardBackgroundColor(defaultSurfaceColor);
        }

        // 3. Favicon laden (Zuerst aus RAM-Cache, sonst aus DB)
        if (url != null && !url.trim().isEmpty()) {
            Bitmap cachedBitmap = bitmapCache.get(url);
            if (cachedBitmap != null && !cachedBitmap.isRecycled()) {
                holder.favicon.setImageBitmap(cachedBitmap);
            } else {
                try {
                    Bitmap bitmap = faviconHelper.getFavicon(url);
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmapCache.put(url, bitmap);
                        holder.favicon.setImageBitmap(bitmap);
                    } else {
                        holder.favicon.setImageResource(R.drawable.icon_image_broken);
                    }
                } catch (Exception e) {
                    holder.favicon.setImageResource(R.drawable.icon_image_broken);
                }
            }
        } else {
            holder.favicon.setImageResource(R.drawable.icon_image_broken);
        }

        return convertView;
    }

    private static class Holder {
        private final TextView title;
        private final TextView time;
        private final ImageView favicon;
        private final MaterialCardView cardView;

        private Holder(View view) {
            title = view.findViewById(R.id.titleView);
            time = view.findViewById(R.id.dateView);
            favicon = view.findViewById(R.id.icon_start);
            cardView = view.findViewById(R.id.item_CardViewItem);
        }
    }
}
