package com.rpooi3.explorer.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.browser.AlbumController;
import com.rpooi3.explorer.browser.BrowserContainer;
import com.rpooi3.explorer.browser.BrowserController;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.unit.HelperUtils;

public class AdapterTabs {

    private final Context context;
    private final AlbumController albumController;
    private View albumView;
    private TextView albumTitle;
    private TextView albumUrl;
    private BrowserController browserController;
    private MaterialCardView albumCardView;

    AdapterTabs(Context context, AlbumController albumController, BrowserController browserController) {
        this.context = context;
        this.albumController = albumController;
        this.browserController = browserController;
        initUI();
    }

    View getAlbumView() {
        return albumView;
    }

    void setAlbumTitle(String title, String url) {
        albumTitle.setText(title);
        albumUrl.setText(url);
        HelperUtils.setHighlightedText(context, albumUrl, HelperUtils.domain(url), url);
        // NEU: Lädt das Favicon für den Tab parallel im Hintergrund über deine schnelle Methode
        FaviconHelper.setFavicon(context, albumView, url, R.id.icon_start, R.drawable.icon_image_broken);
    }

    void setBrowserController(BrowserController browserController) {
        this.browserController = browserController;
    }

    @SuppressLint("InflateParams")
    private void initUI() {
        albumView = LayoutInflater.from(context).inflate(R.layout.item_list, null, false);
        albumCardView = albumView.findViewById(R.id.item_CardViewItem);
        albumTitle = albumView.findViewById(R.id.titleView);
        albumUrl = albumView.findViewById(R.id.dateView);
        ImageView albumClose = albumView.findViewById(R.id.icon_end);
        albumClose.setImageResource(R.drawable.icon_tab_remove);
        albumClose.setVisibility(View.VISIBLE);
        albumClose.setOnClickListener(view -> {
            browserController.removeAlbum(albumController);
            if (BrowserContainer.size() < 2) {
                browserController.hideOverview();
            }
        });
        assert albumCardView != null;
        albumView.setOnLongClickListener(v -> {
            browserController.showOverflow(null, albumCardView, 1, albumTitle.getText().toString(), albumUrl.getText().toString(), null, null, 0);
            return true;
        });
    }

    public void activate() {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryInverse, typedValue, true);
        int color = typedValue.data;
        albumCardView.setCardBackgroundColor(color);
        albumTitle.setTypeface(null, Typeface.BOLD);
        albumView.setOnClickListener(view -> {
            albumCardView.setCardBackgroundColor(color);
            browserController.hideOverview();
        });
    }

    void deactivate() {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, typedValue, true);
        int color = typedValue.data;
        albumCardView.setCardBackgroundColor(color);
        albumTitle.setTypeface(null, Typeface.NORMAL);
        albumView.setOnClickListener(view -> {
            browserController.showAlbum(albumController);
            browserController.hideOverview();
        });
    }
}
