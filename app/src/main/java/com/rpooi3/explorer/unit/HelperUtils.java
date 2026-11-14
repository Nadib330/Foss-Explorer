/*
    This file is part of the browser WebApp.

    browser WebApp is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    browser WebApp is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with the browser webview app.

    If not, see <http://www.gnu.org/licenses/>.
 */

package com.rpooi3.explorer.unit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.theme.ExplorerTheme;
import com.rpooi3.explorer.activity.BrowserActivity;
import com.rpooi3.explorer.browser.List_standard;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.view.CatToast;
import com.rpooi3.explorer.view.GridItem;
import com.rpooi3.explorer.view.MenuItem;
import com.rpooi3.explorer.view.NinjaWebView;
import com.rpooi3.explorer.view.WebsiteInfoDialog;

public class HelperUtils {
    private static SharedPreferences sp;

    // Hilfsmethode, um SharedPreferences sicher ohne Memory-Leaks zu holen
    private static SharedPreferences getSp(Context context) {
        if (sp == null && context != null) {
            sp = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        }
        return sp;
    }

    public static void createShortcut(Context context, String title, String url) {
        if (context == null || url == null) return;

        Icon icon = null;
        try (FaviconHelper faviconHelper = new FaviconHelper(context)) {
            Bitmap favicon = faviconHelper.getFavicon(url);
            if (favicon != null && !favicon.isRecycled()) {
                icon = Icon.createWithBitmap(favicon);
            }
        } catch (Exception e) {
            android.util.Log.e("Foss Explorer", "Shortcut-Icon Fehler: " + e.getMessage());
        }

        // Zentraler Fallback, falls das Favicon nicht geladen werden konnte
        if (icon == null) {
            int appIconResId = context.getApplicationInfo().icon;
            icon = Icon.createWithResource(context, appIconResId != 0 ? appIconResId : android.R.drawable.sym_def_app_icon);
        }

        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
            Intent shortcutIntent = new Intent(context, BrowserActivity.class)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse(url));

            ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(context, url)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(icon)
                    .setIntent(shortcutIntent)
                    .build();

            shortcutManager.requestPinShortcut(pinShortcutInfo, null);
        }
    }

    public static String domain(String url) {
        if (url == null) return "";

        String trimmedUrl = url.trim().toLowerCase();
        if (trimmedUrl.isEmpty()) return "";
        // Regex für ein reines Domain-Format (z. B. xy.de, www.xy.de, ://domain.com)
        // Erlaubt optionale Subdomains, verlangt aber eine Endung (2-12 Zeichen) und KEINE Pfade (/)
        Pattern domainPattern = Pattern.compile("^(?:www\\.)?([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,12})$");
        Matcher matcher = domainPattern.matcher(trimmedUrl);
        // Wenn es dem Domain-Format entspricht, direkt die Domain (ohne www.) zurückgeben
        if (matcher.matches()) {
            return matcher.group(1); // group(1) enthält die Domain bereits OHNE "www."
        }
        // --- Ab hier läuft der normale Code für komplexe URLs (z.B. mit http:// oder /pfad) ---
        if (!trimmedUrl.contains("://") && !trimmedUrl.startsWith("//")) {
            trimmedUrl = "https://" + trimmedUrl;
        }
        try {
            android.net.Uri uri = android.net.Uri.parse(trimmedUrl);
            String host = uri.getHost();
            return host != null ? host.replace("www.", "").trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void initTheme(Activity context) {
        if (context == null) return;
        ExplorerTheme.applyTo(context);
    }

    public static void addFilterItems(Activity activity, List<GridItem> gridList) {
        if (activity == null || gridList == null) return;
        SharedPreferences preferences = getSp(activity);

        // R.string.setting_theme_system wurde hier entfernt (jetzt 11 Elemente)
        int[] colorResIds = {
                R.string.color_red, R.string.color_pink, R.string.color_purple, R.string.color_blue,
                R.string.color_teal, R.string.color_green, R.string.color_lime, R.string.color_yellow,
                R.string.color_orange, R.string.color_brown, R.string.color_grey
        };

        // Die Schleife läuft jetzt nur noch bis 11 (i <= 11)
        for (int i = 1; i <= 11; i++) {
            String suffix = String.format(java.util.Locale.US, "%02d", i);
            if (preferences.getBoolean("filter_" + suffix, true)) {
                String defaultColorName = activity.getString(colorResIds[i - 1]);
                String title = preferences.getString("icon_" + suffix, defaultColorName);
                gridList.add(new GridItem(title, 12 - i));
            }
        }
    }

    public static void setFilterIcons(Context context, MaterialCardView ib_icon, long newIcon) {
        if (context == null || ib_icon == null) return;
        int iconIndex = (int) (newIcon & 15);

        int[] colors = {
                0, R.color.grey, R.color.brown, R.color.orange, R.color.yellow,
                R.color.lime, R.color.green, R.color.teal, R.color.blue,
                R.color.purple, R.color.pink, R.color.red
        };

        if (iconIndex >= 1 && iconIndex <= 11) {
            ib_icon.setCardBackgroundColor(ContextCompat.getColor(context, colors[iconIndex]));
        } else if (iconIndex == 0) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true);
            ib_icon.setCardBackgroundColor(typedValue.data);
        }
    }

    public static void showSoftKeyboard(EditText editText) {
        if (editText == null) return;
        editText.requestFocus();
        editText.postDelayed(() -> {
            Context context = editText.getContext();
            if (context instanceof Activity activity) {
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
                controller.show(WindowInsetsCompat.Type.ime());
            } else {
                InputMethodManager imm = context.getSystemService(InputMethodManager.class);
                if (imm != null) {
                    imm.showSoftInput(editText, 0);
                }
            }
            editText.selectAll();
        }, 200);
    }

    public static void setupDialog(Context context, Dialog dialog) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorError, tv, true);
        ImageView iv = dialog.findViewById(android.R.id.icon);
        if (iv != null) iv.setColorFilter(tv.data, PorterDuff.Mode.SRC_IN);
        if (dialog.getWindow() != null) dialog.getWindow().setGravity(Gravity.BOTTOM);
    }

    public static void setHighlightedText(@NonNull Context context, @NonNull TextView tv, @NonNull String textToHighlight, @Nullable String url) {
        if (textToHighlight.isEmpty()) return;

        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setSingleLine(true);

        String text = tv.getText().toString();
        SpannableString wordToSpan = new SpannableString(text);
        boolean isWhite = false;

        // OPTIMIERUNG: Nur prüfen, wenn überhaupt eine URL übergeben wurde
        if (url != null) {
            try {
                isWhite = new List_standard(context).isWhite(url);
            } catch (Exception e) {
                android.util.Log.e("HelperUtils", "Error loading lists: " + e);
            }
        }

        int attr = (url == null || isWhite) ? androidx.appcompat.R.attr.colorError : com.google.android.material.R.attr.colorOnSurface;
        int color = resolveThemeColor(context, attr);

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerSearch = textToHighlight.toLowerCase(Locale.ROOT);
        int len = lowerSearch.length();

        // Schleife läuft nur, wenn der Text auch wirklich existiert
        if (len > 0) {
            int index = lowerText.indexOf(lowerSearch);
            while (index != -1) {
                wordToSpan.setSpan(new ForegroundColorSpan(color), index, index + len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = lowerText.indexOf(lowerSearch, index + len);
            }
        }

        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE);
    }


    private static int resolveThemeColor(Context context, int attributeResId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attributeResId, typedValue, true);
        return typedValue.data;
    }

    public static void showCustomMaterialDialogWithTwoActions(
            Context context, int dialogIconResId, String title, String text, String link,
            BooleanSupplier positiveActionListener, BooleanSupplier negativeActionListener) {

        // 1. Text und Link für die Nachricht zusammenbauen
        StringBuilder dialogMessage = new StringBuilder();
        if (!TextUtils.isEmpty(text)) {
            dialogMessage.append(text);
        }
        if (!TextUtils.isEmpty(link)) {
            if (!android.text.TextUtils.isEmpty(dialogMessage)) {
                dialogMessage.append("\n\n");
            }
            dialogMessage.append(link);
        }

        // 2. Dialog mit Titel und dem Standard-Icon aufbauen
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(dialogMessage.toString())
                .setCancelable(true);

        // Setzt das normale Icon aus deinem drawable-Ordner, falls übergeben (ungleich 0)
        if (dialogIconResId != 0) {
            builder.setIcon(dialogIconResId);
        }

        // 3. Positiver Button (OK)
        if (positiveActionListener != null) {
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                if (positiveActionListener.getAsBoolean()) {
                    dialog.dismiss();
                }
            });
        }

        // 4. Negativer Button (Abbrechen)
        if (negativeActionListener != null) {
            builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                if (negativeActionListener.getAsBoolean()) {
                    dialog.dismiss();
                }
            });
        }

        // 5. Dialog anzeigen
        androidx.appcompat.app.AlertDialog dialog = builder.show();
        HelperUtils.setupDialog(context, dialog);

        // 6. Links im Text automatisch klickbar machen
        TextView messageTextView = dialog.findViewById(android.R.id.message);
        if (messageTextView != null) {
            Linkify.addLinks(messageTextView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            messageTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    public static void makeSnackbarRound(Snackbar snackbar) {
        View snackbarView = snackbar.getView();
        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) textView.setMaxLines(30);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(60f);

        if (snackbarView.getBackground() instanceof ColorDrawable colorDrawable) {
            background.setColor(colorDrawable.getColor());
        } else {
            background.setColor(Color.parseColor("#323232"));
        }

        snackbarView.setBackground(background);
        snackbar.setTextMaxLines(100);
    }

    public static void setupCardView(Activity activity, View rootLayout, String title, String url, WebView webView, String u, Dialog dialog, boolean show) {

        LinearLayout textGroup = rootLayout.findViewById(R.id.textGroup);
        TextView overflowURL = rootLayout.findViewById(R.id.overflowURL);
        TextView overflowMessage = rootLayout.findViewById(R.id.overflowMessage);
        TextView menuTitle = rootLayout.findViewById(R.id.overflowTitle);
        FloatingActionButton buttonProfile = rootLayout.findViewById(R.id.buttonProfile);

        String onlyURL = (url != null && url.contains("http")) ? url.substring(url.indexOf("http")) : url;

        if (overflowURL != null) overflowURL.setText(onlyURL);
        if (overflowMessage != null) {
            if (u != null) overflowMessage.setText(u);
            else overflowMessage.setVisibility(View.GONE);
        }
        if (menuTitle != null) menuTitle.setText(title);

        if (overflowURL != null && url != null) {
            HelperUtils.setHighlightedText(activity, overflowURL, HelperUtils.domain(url), url);
        }

        if (textGroup != null && url != null) {
            textGroup.setOnClickListener(view -> WebsiteInfoDialog.show(activity, webView, null, title, url));
        }

        if (buttonProfile != null && onlyURL != null) {
            NinjaWebView.getBrowserController().setProfileIcon(buttonProfile, onlyURL);
        }
        FaviconHelper.setFavicon(activity, rootLayout, onlyURL, R.id.icon_start, R.drawable.icon_image_broken);

        if (buttonProfile != null && url != null) {
            buttonProfile.setOnClickListener(v -> {
                if (show) {
                    NinjaWebView.getBrowserController().showDialogFastToggle(HelperUtils.domain(url), url, buttonProfile);
                } else {
                    CatToast.show(activity, "It is how it is.", 4);
                }
            });

            buttonProfile.setOnLongClickListener(v -> {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
                preferences.edit().putString("profile", "profileStandard").apply();
                NinjaWebView.getBrowserController().setProfileIcon(buttonProfile, url);
                boolean isWhite = new List_standard(activity).isWhite(url);
                if (!isWhite) {
                    NinjaWebView.getBrowserController().reloadWebview();
                }
                if (dialog != null) {
                    dialog.cancel();
                }
                return true;
            });
        }
    }

    public static void setupDialogDividers(@NonNull final View scrollView, final View topDivider, final View bottomDivider) {

        // --- 1. RECYCLERVIEW ---
        if (scrollView instanceof RecyclerView) {
            ((RecyclerView) scrollView).addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (recyclerView.getVisibility() != View.VISIBLE) return;
                    toggleDivider(topDivider, recyclerView.canScrollVertically(-1));
                    toggleDivider(bottomDivider, recyclerView.canScrollVertically(1));
                }
            });

            // --- 2. NESTEDSCROLLVIEW ---
        } else if (scrollView instanceof NestedScrollView) {
            ((NestedScrollView) scrollView).setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        if (v.getVisibility() != View.VISIBLE) return;
                        toggleDivider(topDivider, v.canScrollVertically(-1));
                        toggleDivider(bottomDivider, v.canScrollVertically(1));
                    });

            // --- 3. LISTVIEW & GRIDVIEW (AbsListView) ---
        } else if (scrollView instanceof ListView || scrollView instanceof GridView) {
            ((AbsListView) scrollView).setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {}

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (view.getVisibility() != View.VISIBLE) return;
                    if (totalItemCount == 0) {
                        toggleDivider(topDivider, false);
                        toggleDivider(bottomDivider, false);
                        return;
                    }

                    // Oben prüfen
                    boolean canScrollUp = false;
                    if (firstVisibleItem > 0) {
                        canScrollUp = true;
                    } else {
                        View firstChild = view.getChildAt(0);
                        if (firstChild != null) {
                            canScrollUp = firstChild.getTop() < 0;
                        }
                    }

                    // Unten prüfen
                    boolean lastItemVisible = (firstVisibleItem + visibleItemCount) >= totalItemCount;
                    boolean canScrollDown = true;
                    if (lastItemVisible) {
                        View lastChild = view.getChildAt(view.getChildCount() - 1);
                        if (lastChild != null) {
                            canScrollDown = lastChild.getBottom() > view.getHeight();
                        } else {
                            canScrollDown = false;
                        }
                    }

                    toggleDivider(topDivider, canScrollUp);
                    toggleDivider(bottomDivider, canScrollDown);
                }
            });

            // --- 4. FRAMELAYOUT / CONTAINER (z.B. für Einstellungs-Fragmente) ---
        } else if (scrollView instanceof ViewGroup) {
            final RecyclerView internalRecyclerView = findRecyclerView((ViewGroup) scrollView);

            if (internalRecyclerView != null) {
                internalRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        if (recyclerView.getVisibility() != View.VISIBLE) return;
                        toggleDivider(topDivider, recyclerView.canScrollVertically(-1));
                        toggleDivider(bottomDivider, recyclerView.canScrollVertically(1));
                    }
                });
            } else {
                scrollView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int l, int t, int r, int b, int oL, int oT, int oR, int oB) {
                        RecyclerView rv = findRecyclerView((ViewGroup) scrollView);
                        if (rv != null) {
                            scrollView.removeOnLayoutChangeListener(this);
                            setupDialogDividers(rv, topDivider, bottomDivider);
                        }
                    }
                });
            }
        }

        // Initiale Prüfung direkt nach dem Layout-Aufbau (jetzt korrigiert für verschachtelte Views)
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (scrollView.getVisibility() != View.VISIBLE) return;

                // Hier suchen wir die echte scrollbare View für den Initialisierungs-Check
                View actualScrollView = scrollView;
                if (scrollView instanceof ViewGroup && !(scrollView instanceof RecyclerView || scrollView instanceof ListView || scrollView instanceof GridView || scrollView instanceof NestedScrollView)) {
                    RecyclerView rv = findRecyclerView((ViewGroup) scrollView);
                    if (rv != null) {
                        actualScrollView = rv;
                    }
                }

                toggleDivider(topDivider, actualScrollView.canScrollVertically(-1));
                toggleDivider(bottomDivider, actualScrollView.canScrollVertically(1));
            }
        });
    }

    private static RecyclerView findRecyclerView(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof RecyclerView) {
                return (RecyclerView) child;
            } else if (child instanceof ViewGroup) {
                RecyclerView rv = findRecyclerView((ViewGroup) child);
                if (rv != null) return rv;
            }
        }
        return null;
    }

    private static void toggleDivider(final View divider, boolean show) {
        // Wenn 'null' übergeben wurde, machen wir einfach gar nichts
        if (divider == null) {
            return;
        }

        if (show) {
            if (divider.getVisibility() != View.VISIBLE) {
                divider.setVisibility(View.VISIBLE);
                divider.setAlpha(0f);
                divider.animate().alpha(1f).setDuration(140).start();
            }
        } else {
            if (divider.getVisibility() == View.VISIBLE) {
                divider.animate().alpha(0f).setDuration(140).withEndAction(() -> divider.setVisibility(View.INVISIBLE)).start();
            }
        }
    }

    public static Activity getActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity activity) return activity;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
