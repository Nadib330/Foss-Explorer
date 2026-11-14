package com.rpooi3.explorer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.database.Record;
import com.rpooi3.explorer.unit.HelperUtils;

public class AdapterSearch extends BaseAdapter implements Filterable {
    private final Context context;
    private final int layoutResId;
    private final List<CompleteItem> originalList = new ArrayList<>();
    private final CompleteFilter filter = new CompleteFilter();
    private final FaviconHelper faviconHelper;
    private final int cardBackgroundColor;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
    private List<CompleteItem> resultList = new ArrayList<>();
    private String currentSearchQuery = "";

    // Blitzschneller RAM-Cache verhindert Ruckler beim Tippen und Filtern
    private final android.util.LruCache<String, Bitmap> bitmapCache = new android.util.LruCache<>(40);

    public AdapterSearch(Context context, int layoutResId, List<Record> recordList) {
        this.context = context;
        this.layoutResId = layoutResId;
        this.faviconHelper = new FaviconHelper(context.getApplicationContext()); // getApplicationContext verhindert Memory-Leaks

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true);
        this.cardBackgroundColor = typedValue.data;

        getRecordList(recordList);
    }

    private void getRecordList(List<Record> recordList) {
        if (recordList == null) return;

        HashSet<CompleteItem> uniqueItems = new HashSet<>();
        for (Record record : recordList) {
            String title = record.getTitle();
            String url = record.getUrl();
            if (title != null && !title.trim().isEmpty() && url != null && !url.trim().isEmpty()) {
                // Zeitstempel und Formatierer werden für die Datumssuche an das Item übergeben
                uniqueItems.add(new CompleteItem(title, url, record.getTime(), sdf));
            }
        }
        originalList.addAll(uniqueItems);
    }

    @Override public int getCount() { return resultList.size(); }
    @Override public Object getItem(int position) { return resultList.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public Filter getFilter() { return filter; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(layoutResId, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        CompleteItem item = resultList.get(position);
        holder.titleView.setText(item.getTitle());

        // Anzeige-Text bestimmen: Entweder "Zeit: URL" (Verlauf) oder reine "URL" (Lesezeichen)
        // Only the address, as in Chrome. The date of a history entry can still be searched for.
        holder.urlView.setText(item.getUrl());
        // The plain suggestion rows of the address field have no card.
        if (holder.albumCardView != null) holder.albumCardView.setCardBackgroundColor(cardBackgroundColor);

        // Favicon Laden über RAM-Cache optimiert
        String url = item.getUrl();
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

        // Text Highlighting anwenden
        if (!currentSearchQuery.isEmpty()) {
            // Titel bekommt die auffällige Highlight-Farbe (null zwingt zu colorError)
            HelperUtils.setHighlightedText(context, holder.titleView, currentSearchQuery, null);

            // Subtext (Datum/URL) highlighten: Wenn es ein Verlaufseintrag ist, übergeben wir null (erzwingt colorError),
            // damit das eingegebene Datum farblich leuchtet. Bei Lesezeichen greift die normale Whitelist-Prüfung.
            HelperUtils.setHighlightedText(context, holder.urlView, currentSearchQuery, item.hasTime() ? null : item.getUrl());
        }

        return convertView;
    }

    private static class Holder {
        private final ImageView favicon;
        private final TextView titleView;
        private final TextView urlView;
        private final MaterialCardView albumCardView;

        private Holder(View view) {
            favicon = view.findViewById(R.id.icon_start);
            titleView = view.findViewById(R.id.titleView);
            urlView = view.findViewById(R.id.dateView);
            albumCardView = view.findViewById(R.id.item_CardViewItem);
        }
    }

    private class CompleteFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            if (TextUtils.isEmpty(prefix)) {
                currentSearchQuery = "";
                results.values = new ArrayList<>();
                results.count = 0;
                return results;
            }

            currentSearchQuery = prefix.toString();
            String searchStr = currentSearchQuery.toLowerCase(Locale.ROOT);
            List<CompleteItem> workList = new ArrayList<>();

            for (CompleteItem item : originalList) {
                int titleIndex = item.lowerTitle.indexOf(searchStr);
                int urlIndex = item.lowerUrl.indexOf(searchStr);
                int timeIndex = item.lowerTimeStr.indexOf(searchStr); // Suchlauf im Datum

                if (titleIndex != -1 || urlIndex != -1 || timeIndex != -1) {
                    // Besten (kleinsten) Index für die Sortierung ermitteln
                    int bestIndex = Integer.MAX_VALUE;
                    if (titleIndex != -1) bestIndex = titleIndex;
                    if (urlIndex != -1) bestIndex = Math.min(bestIndex, urlIndex);
                    if (timeIndex != -1) bestIndex = Math.min(bestIndex, timeIndex);

                    item.setIndex(bestIndex);
                    workList.add(item);
                }
            }

            // Best match first: the list hangs below the input field at the top of the screen.
            workList.sort(Comparator.comparingInt(CompleteItem::getIndex));

            results.values = workList;
            results.count = workList.size();
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (results != null && results.count > 0) {
                resultList = (List<CompleteItem>) results.values;
                notifyDataSetChanged();
            } else {
                resultList = new ArrayList<>();
                notifyDataSetInvalidated();
            }
        }
    }

    public static class CompleteItem {
        private final String title;
        private final String url;
        private final String formattedTime;
        private final String lowerTitle;
        private final String lowerUrl;
        private final String lowerTimeStr;
        private int index = Integer.MAX_VALUE;

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getFormattedTime() { return formattedTime; }
        public boolean hasTime() { return !formattedTime.isEmpty(); }

        private CompleteItem(String title, String url, long time, SimpleDateFormat sdf) {
            this.title = title;
            this.url = url;
            this.lowerTitle = title.toLowerCase(Locale.ROOT);
            this.lowerUrl = url.toLowerCase(Locale.ROOT);

            if (time > 1) {
                this.formattedTime = sdf.format(new java.util.Date(time));
                this.lowerTimeStr = this.formattedTime.toLowerCase(Locale.ROOT);
            } else {
                this.formattedTime = "";
                this.lowerTimeStr = "";
            }
        }

        int getIndex() { return index; }
        void setIndex(int index) { this.index = index; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CompleteItem item)) return false;
            return item.title.equals(title) && item.url.equals(url);
        }

        @Override
        public int hashCode() {
            return title.hashCode() ^ url.hashCode();
        }
    }
}
