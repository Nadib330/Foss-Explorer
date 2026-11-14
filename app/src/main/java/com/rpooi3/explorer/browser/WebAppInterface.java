package com.rpooi3.explorer.browser;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileOutputStream;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.compat.StorageCompat;
import com.rpooi3.explorer.view.CatToast;

@Keep
public class WebAppInterface {
    private final Context mContext;

    // Ganz oben in der Klasse als statisches Flag deklarieren
    public static boolean isScriptRunning = false;

    @JavascriptInterface
    public void registerScriptStarted() {
        isScriptRunning = true;
    }

    public WebAppInterface(Context context) {
        this.mContext = context;
    }

    @JavascriptInterface
    public void processBlob(String base64Data, String ignoredMimeType, String fileName) {
        if (base64Data == null || base64Data.isEmpty()) return;

        try {
            String pureBase64 = base64Data;
            // SICHERES ABSCHNEIDEN: Verhindert den Absturz, falls kein Komma vorhanden ist
            if (pureBase64.contains(",")) {
                int commaIndex = pureBase64.indexOf(",");
                pureBase64 = pureBase64.substring(commaIndex + 1);
            }
            // Bereinigt eventuelle Zeilenumbrüche oder Leerzeichen
            pureBase64 = pureBase64.replace(" ", "").replace("\n", "").replace("\r", "");
            // Base64 zu Bytes dekodieren
            byte[] fileBytes = Base64.decode(pureBase64, Base64.DEFAULT);
            // Saved the same way as every other file the browser writes itself, so it also
            // works with scoped storage and shows up on the downloads screen.
            final String mimeType = (ignoredMimeType == null || ignoredMimeType.trim().isEmpty()) ? null : ignoredMimeType.trim();
            StorageCompat.writeToDownloads(mContext, fileName, mimeType, fileBytes);
            String text = mContext.getString(R.string.app_done) + ". " + mContext.getString(R.string.menu_download) + "?";
            CatToast.show(mContext, text, 1);
        } catch (Exception e) {
            Log.e("Foss Explorer", "Blob-Speicherfehler: " + e.getMessage());
            if (mContext instanceof Activity activity) {
                activity.runOnUiThread(() ->
                        CatToast.show(mContext,  mContext.getString(R.string.app_error) + ": " + e.getLocalizedMessage(), 2)
                );
            }
        }
    }
    // Die JavaScript-Schnittstelle für den "Speichern unter..."-Dialog
    @JavascriptInterface
    public void processBlobSaveAs(String base64Data, String mimeType, String finalFileName) {
        // Leitet die Daten stabil und sicher an den bereinigten Schreibprozess weiter
        processBlob(base64Data, mimeType, finalFileName);
    }
}
