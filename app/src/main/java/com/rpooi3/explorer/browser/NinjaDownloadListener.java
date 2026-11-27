package com.rpooi3.explorer.browser;


import android.text.format.Formatter;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDateTime;
import java.util.Locale;
import java.time.format.DateTimeFormatter;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.compat.StorageCompat;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;
import com.rpooi3.explorer.view.CatToast;
import com.rpooi3.explorer.site.SiteCategory;
import com.rpooi3.explorer.site.SitePermissions;
import com.rpooi3.explorer.site.SiteSettings;
import com.rpooi3.explorer.view.NinjaWebView;

public class NinjaDownloadListener implements DownloadListener {
    private final WebView webView;

    public NinjaDownloadListener(WebView webView) {
        super();
        this.webView = webView;
    }

    /**
     * The name the sheet offers. The server's own name wins (Content-Disposition), then the
     * last part of the address, then a name made of the site and the time. The extension is
     * never invented as ".bin" while the address or the type says what the file is — that
     * was the bug of 15.2.1 and before, which renamed every download.
     */
    private static String prepareInitialFilename(WebView webView, String url, String customName,
                                                 String contentDisposition, String mimeType) {
        if (customName != null && !customName.trim().isEmpty()) return customName.trim();
        String safeUrl = url == null ? "" : url;
        if (safeUrl.startsWith("blob:") || safeUrl.startsWith("data:")) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String domain = webView != null ? HelperUtils.domain(webView.getUrl()) : "download";
            if (domain == null || domain.isEmpty()) domain = "download";
            String ext = extensionOfMimeType(mimeType);
            return domain + "_" + timestamp + (ext.isEmpty() ? "" : "." + ext);
        }
        // What the server says the file is called, and what the address says, if it says more.
        String fromServer = nameFromDisposition(contentDisposition);
        String fromUrl = nameFromUrl(safeUrl);
        String name;
        if (fromServer != null && !fromServer.isEmpty()) name = fromServer;
        else if (fromUrl != null && !fromUrl.isEmpty()) name = fromUrl;
        else name = URLUtil.guessFileName(safeUrl, contentDisposition, mimeType);
        name = decodeName(name);
        // 16.1.0 (item 5): a name without an extension takes the real one, from the address or
        // from the type; nothing is invented.
        if (!hasExtension(name)) {
            String ext = extensionOf(nameFromUrl(safeUrl));
            if (ext.isEmpty()) ext = extensionOfMimeType(mimeType);
            if (!ext.isEmpty()) name = name + "." + ext;
        }
        // The WebView's guess ends in ".bin" when it knows nothing about the file. That ending is
        // dropped rather than kept: a file called "report" stays "report", and where the address
        // or the type does give an extension, that one is used.
        if (name.toLowerCase(Locale.ROOT).endsWith(".bin") && name.length() > 4) {
            String ext = extensionOf(nameFromUrl(safeUrl));
            if (ext.isEmpty()) ext = extensionOfMimeType(mimeType);
            String base = name.substring(0, name.length() - 4);
            name = ext.isEmpty() ? base : base + "." + ext;
        }
        return name;
    }

    /** The file name the server announced ("attachment; filename=..."), decoded; null if none. */
    private static String nameFromDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isEmpty()) return null;
        // filename*=UTF-8''name and filename="name"
        java.util.regex.Matcher extended = java.util.regex.Pattern
                .compile("filename\\*\\s*=\\s*[^']*''([^;]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(contentDisposition);
        if (extended.find()) return safeDecode(extended.group(1).trim().replace("\"", ""));
        java.util.regex.Matcher plain = java.util.regex.Pattern
                .compile("filename\\s*=\\s*\"?([^\";]+)\"?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(contentDisposition);
        if (plain.find()) return safeDecode(plain.group(1).trim());
        return null;
    }

    /** The last part of [url], without the query, decoded; null if it is not a name. */
    private static String nameFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            String path = url;
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);
            int hash = path.indexOf('#');
            if (hash >= 0) path = path.substring(0, hash);
            int slash = path.lastIndexOf('/');
            if (slash >= 0) path = path.substring(slash + 1);
            String name = safeDecode(path).trim();
            if (name.isEmpty() || name.equals(".") || name.equals("..")) return null;
            if (!name.contains(".")) return null;
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeDecode(String text) {
        try {
            return android.net.Uri.decode(text);
        } catch (Exception e) {
            return text;
        }
    }

    private static String decodeName(String name) {
        String decoded = safeDecode(name);
        return decoded == null || decoded.isEmpty() ? name : decoded;
    }

    /** The extension of [name] in lower case, without the dot; empty if it has none. */
    private static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,8}") ? ext : "";
    }

    private static boolean hasExtension(String name) {
        return !extensionOf(name).isEmpty();
    }

    /** The extension of [mimeType] ("image/png" -> "png"); empty if Android does not know it. */
    private static String extensionOfMimeType(String mimeType) {
        if (mimeType == null) return "";
        String type = mimeType.split(";")[0].trim();
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    // Erforderliche Interface-Methode vollständig implementiert
    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        Context context = webView.getContext();
        if (context instanceof Activity activity) {
            // Site setting "Automatic downloads": the first download after a tap goes ahead; for
            // further ones the site has to be allowed.
            if (webView instanceof NinjaWebView tab && tab.isFurtherDownload()) {
                final String site = tab.getUrl();
                SitePermissions.request(activity, SiteCategory.AUTOMATIC_DOWNLOADS, site, !tab.isPrivateTab(), allowed -> {
                    if (allowed) saveAs(activity, webView, url, null, contentDisposition, mimeType, contentLength);
                    else CatToast.show(activity, activity.getString(R.string.site_download_blocked, SiteSettings.site(site)));
                });
                return;
            }
            saveAs(activity, webView, url, null, contentDisposition, mimeType, contentLength);
        } else {
            Log.e("Foss Explorer", "Context is not an Activity, cannot show saveAs dialog.");
        }
    }

    /** Asks before a download whose size is not known (a link the user chose to save). */
    public static void saveAs(final Activity activity, final WebView webView, final String url, final String name, final String mimeType) {
        saveAs(activity, webView, url, name, null, mimeType, -1);
    }

    /** As above, with the name the server announced in [contentDisposition]. */
    public static void saveAs(final Activity activity, final WebView webView, final String url, final String name,
                              final String contentDisposition, final String mimeType) {
        saveAs(activity, webView, url, name, contentDisposition, mimeType, -1);
    }

    /**
     * The question before a download, as Firefox for Android asks it: a sheet from the
     * bottom with "Download file? (size)", the name of the file and the button "Download".
     * The name can be changed by tapping it.
     *
     * @param contentLength the size the server announced, or -1 if it is not known
     */
    public static void saveAs(final Activity activity, final WebView webView, final String url, final String name,
                              final String mimeType, final long contentLength) {
        saveAs(activity, webView, url, name, null, mimeType, contentLength);
    }

    public static void saveAs(final Activity activity, final WebView webView, final String url, final String name,
                              final String contentDisposition, final String mimeType, final long contentLength) {
        if (url == null) return;
        try {
            final String filename = prepareInitialFilename(webView, url, name, contentDisposition, mimeType);
            final BottomSheetDialog sheet = new BottomSheetDialog(activity);
            final View view = View.inflate(activity, R.layout.sheet_download, null);
            final TextView title = view.findViewById(R.id.download_title);
            title.setText(contentLength > 0
                    ? activity.getString(R.string.download_prompt_title_size, Formatter.formatShortFileSize(activity, contentLength))
                    : activity.getString(R.string.download_prompt_title));
            final EditText nameView = view.findViewById(R.id.download_filename);
            nameView.setText(filename);
            view.findViewById(R.id.download_close).setOnClickListener(v -> sheet.dismiss());
            view.findViewById(R.id.download_button).setOnClickListener(v -> {
                // 16.1.0 (item 5): the name keeps the extension of the file. A name the user typed
                // without one gets the extension of the file rather than a made-up ".bin".
                final String chosen = keepExtension(cleanFileName(nameView.getText().toString()), filename);
                if (chosen.isEmpty()) {
                    CatToast.show(activity, activity.getString(R.string.toast_input_empty), 3);
                    return;
                }
                sheet.dismiss();
                startDownload(activity, webView, url, chosen, mimeType);
            });
            sheet.setContentView(view);
            sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            sheet.getBehavior().setSkipCollapsed(true);
            sheet.show();
        } catch (Exception e) {
            Log.e("Foss Explorer", "SaveAs Error: " + e);
        }
    }

    /**
     * A name a file can have: no line breaks or path separators. The name the user chose is
     * kept as it is, extension and all; a name without an extension stays without one.
     */
    static String cleanFileName(final String typed) {
        String name = typed == null ? "" : typed.replaceAll("[\\r\\n\\t]", " ").replace("/", "_").replace("\\", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return "";
        // A trailing dot would make Android hide the file.
        while (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        return name;
    }

    /** [chosen] with the extension of [initial] if the user typed a name without one. */
    static String keepExtension(final String chosen, final String initial) {
        if (chosen == null || chosen.isEmpty() || hasExtension(chosen)) return chosen;
        final String ext = extensionOf(initial);
        return ext.isEmpty() ? chosen : chosen + "." + ext;
    }

    /** Saves [url] to Downloads as [finalFileName]: pages' own data by script, everything else by the system's download manager. */
    static void startDownload(final Activity activity, final WebView webView, final String url, final String finalFileName, final String mimeType) {
        final int lastDot = finalFileName.lastIndexOf('.');
        final String extInput = lastDot > 0 ? finalFileName.substring(lastDot) : "";
        // Android 8/9 need the storage permission before anything can be written to Downloads.
        if (!StorageCompat.ensureDownloadsWritable(activity)) return;
        // VERARBEITUNG NACH DEM OK-KLICK
        if (url.startsWith("blob:")) {
            if (webView == null) return;
            // 1. Signal im Interface zurücksetzen (Entwarnung auf false)
            WebAppInterface.isScriptRunning = false;
            // 2. Das JavaScript: Meldet sich SOFORT im ersten Schritt bei Java an
            String jsCode = "javascript:(function() {" +
                    "   try {" +
                    "       if (window.AndroidInterface) window.AndroidInterface.registerScriptStarted();" + // Sofortiges Lebenszeichen
                    "       fetch('" + url + "').then(r => r.blob()).then(b => {" +
                    "           var reader = new FileReader();" +
                    "           reader.onloadend = () => {" +
                    "               if (window.AndroidInterface) window.AndroidInterface.processBlobSaveAs(reader.result, '" + mimeType + "', '" + finalFileName + "');" +
                    "           };" +
                    "           reader.readAsDataURL(b);" +
                    "       });" +
                    "   } catch (err) { console.error(err); }" +
                    "})();";

            activity.runOnUiThread(() -> webView.evaluateJavascript(jsCode, null));
            // 3. DER ERSEHNTE CATCH-BLOCK (Verschlankter Timer-Check)
            webView.postDelayed(() -> {
                // Wenn das Skript nicht angesprungen ist (z.B. weil das Interface fehlte oder abgestürzt ist)
                if (!WebAppInterface.isScriptRunning) {
                    Snackbar snackbar = Snackbar.make(webView, R.string.hint_blob, Snackbar.LENGTH_SHORT);
                    HelperUtils.makeSnackbarRound(snackbar);
                    snackbar.setAction(activity.getString(R.string.app_ok), v -> {
                        Uri webpage = Uri.parse(webView.getUrl());
                        BrowserUnit.intentURL(activity, webpage);
                    });
                    snackbar.show();
                }
            }, 150);
        } else if (url.startsWith("data:")) {
            saveBytesToMediaStore(activity, webView, url, finalFileName, mimeType);
        } else {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setTitle(finalFileName);
                String cleanExt = extInput.length() > 1 ? extInput.substring(1).toLowerCase(Locale.ROOT) : "";
                String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(cleanExt);
                // Without an extension the type the server announced keeps the file openable.
                r.setMimeType(type != null ? type : mimeType);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName);
                DownloadManager m = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                if (m != null) m.enqueue(r);
            } catch (Exception e) {
                Log.e("Foss Explorer", "DownloadManager Error: " + e);
            }
        }
    }

    // 3. UNIVERSELLE SCHREIBMETHODE: Verarbeitet Bytes asynchron und zeigt die fertige Snackbar an
    public static void saveBytesToMediaStore(Context context, WebView webView, String dataUrl, String savedFileName, String mimeType) {
        new Thread(() -> {
            try {
                DataURIParser parser = new DataURIParser(dataUrl);
                byte[] decodedBytes = parser.getImagedata();
                if (decodedBytes == null || decodedBytes.length == 0) return;

                StorageCompat.writeToDownloads(context, savedFileName, mimeType, decodedBytes);

                if (webView != null) {
                    webView.post(() -> {
                        String text = context.getString(R.string.app_done) + ". " + context.getString(R.string.menu_download) + "?";
                        CatToast.show(context, text, 1);
                    });
                }
            } catch (Exception e) {
                Log.e("Foss Explorer", "Write Error: " + e);
            }
        }).start();
    }

    public static class DataURIParser {
        private final byte[] imagedata;

        public DataURIParser(String url) {
            if (url == null || !url.startsWith("data:")) {
                this.imagedata = new byte[0];
                return;
            }
            int commaIndex = url.indexOf(",");
            String data = (commaIndex != -1) ? url.substring(commaIndex + 1) : "";
            byte[] decodedBytes;
            try {
                if (url.contains(";base64")) {
                    decodedBytes = Base64.decode(data, Base64.DEFAULT);
                } else {
                    decodedBytes = android.net.Uri.decode(data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                decodedBytes = new byte[0];
            }
            this.imagedata = decodedBytes;
        }
        public byte[] getImagedata() {
            return imagedata;
        }
    }
}