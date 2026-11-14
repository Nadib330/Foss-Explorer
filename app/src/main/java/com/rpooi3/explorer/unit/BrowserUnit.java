package com.rpooi3.explorer.unit;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebView;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.activity.BrowserActivity;
import com.rpooi3.explorer.database.RecordAction;
import com.rpooi3.explorer.database.RecordUnit;

public class BrowserUnit {

    public static final int LOADING_STOPPED = 101;  //Must be > PROGRESS_MAX !
    public static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
    public static final String URL_ENCODING = "UTF-8";

    public static boolean isURL(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }
        urlString = urlString.trim();
        if ("about:blank".equalsIgnoreCase(urlString)) {
            return true;
        }
        try {
            URI uri = new URI(urlString);
            if (uri.getScheme() != null) {
                String scheme = uri.getScheme().toLowerCase();
                return "http".equals(scheme) || "https".equals(scheme) || "file".equals(scheme) || "content".equals(scheme);
            }
            Pattern domainPattern = Pattern.compile("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$");
            if (domainPattern.matcher(urlString).matches()) {
                URI fallbackUri = new URI("http://" + urlString);
                return fallbackUri.getHost() != null && fallbackUri.getHost().contains(".");
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String queryWrapper(Context context, String query) {

        if (query.contains(";jsessionid=")) {
            String tracking = query.substring(query.lastIndexOf(";"));
            query = query.replace(tracking, "");
        }

        if (isURL(query) || query.isEmpty()) {
            if (query.startsWith("about:blank") || query.startsWith("mailto:")) {
                return query;
            }
            if (!query.contains("://")) {
                query = "https://" + query;
            }
            return query;
        } else {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String customSearchEngine = sp.getString("sp_search_engine_custom", "");
            String customSearches = sp.getString("sp_search_customSearches", "");
            query = query.replace("&", "%26");
            query = query.replace("#", "");
            //Override UserAgent if own UserAgent is defined
            if (!sp.contains("searchEngineSwitch")) {
                //if new switch_text_preference has never been used initialize the switch
                if (customSearchEngine.isEmpty()) {
                    sp.edit().putBoolean("searchEngineSwitch", false).apply();
                } else {
                    sp.edit().putBoolean("searchEngineSwitch", true).apply();
                }
            }

            if (!customSearches.isEmpty()) {
                return customSearches + query;
            } else if (sp.getBoolean("searchEngineSwitch", false)) {
                //if new switch_text_preference has never been used initialize the switch
                return customSearchEngine + query;
            } else {
                return com.rpooi3.explorer.search.SearchEngines.searchUrl(sp.getString("sp_search_engine", "0"), query);
            }
        }
    }

    public static void clearBookmark(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(true);
        action.clearTable(RecordUnit.TABLE_BOOKMARK);
        action.close();
    }

    public static void clearHistory(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(true);
        action.clearTable(RecordUnit.TABLE_HISTORY);
        action.close();
    }

    public static void intentURL(Context context, Uri uri) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW);
        browserIntent.setData(uri);
        browserIntent.setPackage(context.getPackageName());
        context.startActivity(browserIntent);
    }

    public static String redirectURL (WebView ninjaWebView, SharedPreferences sp, String url) {
        try {
            List<CustomItemsUtils.CustomItem> redirects = CustomItemsUtils.getRedirects(sp);
            for (int i = 0; i < redirects.size(); i++) {
                CustomItemsUtils.CustomItem customRedirect = redirects.get(i);
                if (url.contains(customRedirect.source()) && sp.getBoolean(customRedirect.source(), true)) {
                    ninjaWebView.stopLoading();
                    url = url.replace(customRedirect.source(), customRedirect.target());
                    return url;
                }
            }
        } catch (JSONException e) {
            Log.e("Redirect error", e.toString());
        }
        return url;
    }

    public static void openInBackground(Activity activity, WebView webView) {
        if (activity == null || webView == null) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!sp.getBoolean("sp_tabBackground", false)) return;
        String dialogSetting = sp.getString("openBackground_dialog", "show");
        if ("never".equals(dialogSetting)) return;
        // Notification-Inhalt vorbereiten
        String url = webView.getUrl();
        String text = activity.getString(R.string.dialog_backGround);
        NotificationManager mNotifyMgr = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        // PendingIntent für Klick auf die Benachrichtigung
        Intent intentP = new Intent(activity, BrowserActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0, intentP, PendingIntent.FLAG_IMMUTABLE);
        // Notification Channel erstellen (nur ab Android 8/Orest)
        if (mNotifyMgr != null) {
            NotificationChannel channel = new NotificationChannel("1", "Links background", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Open links in background -> click to open");
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            mNotifyMgr.createNotificationChannel(channel);
        }
        Notification buildNotification = new NotificationCompat.Builder(activity, "1")
                .setSmallIcon(R.drawable.icon_web)
                .setAutoCancel(true)
                .setContentTitle(HelperUtils.domain(url))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .build();
        // Verzweigung für Snackbar oder direkten Aufruf
        if ("show".equals(dialogSetting)) {
            HelperUtils.showCustomMaterialDialogWithTwoActions(
                    activity,
                    R.drawable.icon_alert, // 0 für kein Icon oben (oder setze z. B. R.drawable.icon_check)
                    text, // Nutzt den Text als Titel des Dialogs (wie im alten Code)
                    activity.getString(R.string.app_session), // Der Beschreibungstext
                    url, // Die URL wird automatisch klickbar unten angehängt

                    // Mehrzeilige Funktion für den positiven Button (Immer im Hintergrund öffnen)
                    () -> {
                        sp.edit().putString("openBackground_dialog", "always").apply();
                        displayNotification(activity, mNotifyMgr, buildNotification);
                        return true; // Schließt den Dialog
                    },

                    // Mehrzeilige Funktion für den negativen Button (Niemals im Hintergrund öffnen)
                    () -> {
                        sp.edit().putString("openBackground_dialog", "never").apply();
                        return true; // Schließt den Dialog
                    }
            );

        } else {
            displayNotification(activity, mNotifyMgr, buildNotification);
        }
    }

    private static void displayNotification(Activity activity, NotificationManager mNotifyMgr, Notification buildNotification) {

        if (activity == null || mNotifyMgr == null || buildNotification == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new MaterialAlertDialogBuilder(activity)
                    .setIcon(R.drawable.icon_alert)
                    .setTitle(R.string.app_permission_notification)
                    .setMessage(R.string.app_permission)
                    .setPositiveButton(R.string.app_ok, (dialog, whichButton) -> {
                        dialog.dismiss();
                        try {
                            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
                            activity.startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", activity.getPackageName(), null));
                            activity.startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel())
                    .show(); // Direkt anzeigen über Fluent-API
            return;
        }
        // Berechtigung vorhanden oder älteres Android -> Benachrichtigung senden
        mNotifyMgr.notify(4, buildNotification);
        activity.moveTaskToBack(true);
    }
}