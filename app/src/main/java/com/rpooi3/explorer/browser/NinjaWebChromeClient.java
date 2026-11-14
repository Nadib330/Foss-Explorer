package com.rpooi3.explorer.browser;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.activity.BrowserActivity;
import com.rpooi3.explorer.site.SitePermissions;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;
import com.rpooi3.explorer.view.CatToast;
import com.rpooi3.explorer.view.NinjaWebView;

public class NinjaWebChromeClient extends WebChromeClient {

    private final NinjaWebView ninjaWebView;

    private final BrowserActivity activity;

    public NinjaWebChromeClient(NinjaWebView ninjaWebView) {
        super();
        this.ninjaWebView = ninjaWebView;
        Context context = ninjaWebView.getContext();
        if (context instanceof BrowserActivity) {
            this.activity = (BrowserActivity) context;
        } else {
            this.activity = null;
        }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
        new MaterialAlertDialogBuilder(ninjaWebView.getContext())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                .show();
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
        new MaterialAlertDialogBuilder(ninjaWebView.getContext())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                .show();
        return true;
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
        final EditText input = new EditText(ninjaWebView.getContext());
        input.setText(defaultValue);
        FrameLayout container = new FrameLayout(ninjaWebView.getContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        int margin = (int) (20 * ninjaWebView.getContext().getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(ninjaWebView.getContext())
                .setTitle(message)
                .setView(container)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                .show();
        return true;
    }
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if (consoleMessage.message().contains("NotAllowedError: Write permission denied.")) {  //this error occurs when user copies to clipboard
            CatToast.show(ninjaWebView.getContext(), R.string.app_error_copy, 2);
            return true;
        }
        return false;
    }

    @Override
    public void onProgressChanged(WebView view, int progress) {
        super.onProgressChanged(view, progress);
        String url = ninjaWebView.getUrl();
        String title = ninjaWebView.getTitle();
        ninjaWebView.updateTitle(progress);
        assert title != null;
        if (title.isEmpty()) ninjaWebView.updateTitle(HelperUtils.domain(url), url);
        else ninjaWebView.updateTitle(title,url);
    }
    @Override
    public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message resultMsg) {
        // Site setting "Pop-ups and redirects": a window the user did not ask for is not opened.
        if (!userGesture && !ninjaWebView.isPopUpsAllowed()) return false;
        Context context = view.getContext();
        NinjaWebView newWebView = new NinjaWebView(context);
        view.addView(newWebView);
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWebView);
        resultMsg.sendToTarget();
        newWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                try {
                    BrowserUnit.intentURL(context, request.getUrl());
                } catch (Exception e) {
                    Log.i(TAG, "shouldOverrideUrlLoading Exception:" + e);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(request.getUrl().toString()));
                    context.startActivity(Intent.createChooser(intent, request.getUrl().toString()));
                }
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // 1. Logge den Fehler oder benachrichtige den Nutzer (Toast / Snackbar)
                // Log.e("WebView", "Render-Prozess abgestürzt! Ursache: " + detail.didCrash());
                // 2. Wichtig: Den alten WebView aus dem Layout entfernen oder neu initialisieren,
                // da er nicht mehr benutzbar ist.
                // 3. 'true' zurückgeben signalisiert Android: "Ich habe den Fehler abgefangen, stürze die App NICHT ab."
                return true;
            }
        });
        return true;
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        NinjaWebView.getBrowserController().onShowCustomView(view, callback);
        super.onShowCustomView(view, callback);
    }
    @Override
    public void onHideCustomView() {
        NinjaWebView.getBrowserController().onHideCustomView();
        super.onHideCustomView();
    }
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        NinjaWebView.getBrowserController().showFileChooser(filePathCallback);
        return true;
    }
    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        // Camera, microphone, protected content: the site settings decide, see SitePermissions.
        SitePermissions.handle(activity, request, !ninjaWebView.isPrivateTab());
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        SitePermissions.handleGeolocation(activity, origin, !ninjaWebView.isPrivateTab(), callback);
    }
    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        ninjaWebView.setFavicon(icon);
        super.onReceivedIcon(view, icon);
    }
}