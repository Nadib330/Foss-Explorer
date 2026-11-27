/*
 * Explorer: the popup of an extension, in a bubble of its own.
 * Own code, GPL-3.0.
 *
 * Chrome shows the popup of an extension in a small window under its button: the page of the
 * extension, at the size the page asks for, over the page you were reading. Explorer did that in a
 * sheet from the bottom of the screen, and the extension's own markup came out as a page stretched
 * across the whole width. Since 17.3.4 it is a bubble again: `PopupWindow`, anchored under the
 * menu button, as wide and as tall as the popup says it wants to be (`__explorerPopupSize`, which
 * every popup gets from the shim), and gone as soon as it is tapped outside or closed from inside
 * (`window.close()`).
 *
 * The chrome.* interfaces of the popup are the ones a tab has — the same storage, the same
 * manifest — plus the ones a popup needs to do anything: the tab in front, scripts in that tab,
 * and messages to the content scripts and the background page (see [ExtensionBus]).
 */
package com.rpooi3.explorer.extension

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R

/**
 * The popup of [extension] in a bubble.
 *
 * @param onOpenSettings where the "no popup, but settings" button leads
 * @param onOpenTab where a link the popup follows goes, if the browser can open a tab
 * @param onClose called once the bubble has gone
 * @param page the page to show, relative to the extension; by default its popup, and the options
 *   page of the extension the same way
 * @param anchor the view the bubble hangs under; the menu button of the browser
 */
class ExtensionPopup @JvmOverloads constructor(
    private val context: Context,
    private val extension: WebExtension,
    private val onOpenSettings: (() -> Unit)? = null,
    private val onOpenTab: ((String) -> Unit)? = null,
    private val onClose: (() -> Unit)? = null,
    private val page: String? = null,
    private val anchor: View? = null,
) {

    private var window: PopupWindow? = null
    private var root: View? = null
    private var webView: WebView? = null
    private var bridge: ChromeApiBridge? = null

    /** The size the popup asked for, in CSS pixels; 0 until it has said. */
    private var wantedWidth = 0
    private var wantedHeight = 0

    /** An options page is a form, not a popup: it may take more room (17.3.4). */
    private val wide: Boolean get() = page != null

    val isShowing: Boolean get() = window?.isShowing == true

    /** The view of the open bubble, or null while it is closed. */
    val contentView: View? get() = root

    /** The WebView of the popup, for tests and for going back inside it. */
    fun webView(): WebView? = webView

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun show() {
        if (isShowing) return
        val content = createView()
        val popup = PopupWindow(content, width(), height(), true)
        popup.setBackgroundDrawable(background())
        // Extension popups are context-menu surfaces too; keep them flat like the browser menus.
        popup.elevation = 0f
        popup.isOutsideTouchable = true
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        popup.setOnDismissListener { cleanUp() }
        window = popup
        try {
            val target = anchor
            if (target != null && target.isAttachedToWindow) {
                popup.showAsDropDown(target, 0, dp(4), Gravity.END)
            } else {
                popup.showAtLocation(
                    (context as? android.app.Activity)?.window?.decorView ?: content,
                    Gravity.TOP or Gravity.END,
                    dp(8),
                    dp(64),
                )
            }
        } catch (e: Throwable) {
            // A window that cannot be shown must not take the browser with it.
            cleanUp()
            return
        }
        webView?.let { ExtensionBus.registerPopup(extension.id, it) }
    }

    fun dismiss() {
        val popup = window
        window = null
        try {
            popup?.dismiss()
        } catch (e: Throwable) {
            // Already gone.
        }
        cleanUp()
    }

    private fun cleanUp() {
        webView?.let { view ->
            ExtensionBus.unregisterPopup(view)
            try {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            } catch (e: Throwable) {
                // A WebView that is already gone needs no destroying.
            }
        }
        webView = null
        bridge = null
        val hadContent = root != null
        root = null
        if (hadContent) onClose?.invoke()
    }

    /** Grows or shrinks the bubble to what its page says it wants. */
    private fun resizeToContent(cssWidth: Int, cssHeight: Int) {
        wantedWidth = cssWidth
        wantedHeight = cssHeight
        val popup = window ?: return
        try {
            val wide = width()
            val tall = height()
            popup.update(wide, tall)
            root?.let { view ->
                view.layoutParams = (view.layoutParams ?: ViewGroup.LayoutParams(wide, tall)).apply {
                    width = wide
                    height = tall
                }
            }
        } catch (e: Throwable) {
            // A window that cannot be resized stays as it is.
        }
    }

    /**
     * The width of the bubble.
     *
     * 18.0.0 beta 9 (item 8): an extension brings its own shape and size — a calculator is narrow
     * and tall, a weather popup is square, a page of settings is wide — and the bubble used to
     * squeeze all of them into 240-480 dp, so each of them came out as the same small rectangle in
     * the upper corner. The bubble now takes the width the popup asks for, up to the width of the
     * screen (minus a small margin), and only then falls back to a sensible narrowest width.
     */
    private fun width(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        val widest = minOf(screen - dp(8), if (wide) dp(720) else dp(640))
        val narrowest = minOf(dp(180), widest)
        val asked = if (wantedWidth > 0) dp(wantedWidth) else dp(if (wide) 400 else 320)
        return asked.coerceIn(narrowest, widest)
    }

    /**
     * The height of the bubble: what the popup asks for, up to almost the whole screen (the bars
     * of the browser stay visible above it), and never less than a row of text.
     */
    private fun height(): Int {
        val screen = context.resources.displayMetrics.heightPixels
        val highest = minOf((screen * 0.86f).toInt(), if (wide) dp(900) else dp(760))
        val shortest = dp(120)
        val asked = if (wantedHeight > 0) dp(wantedHeight) else dp(240)
        return asked.coerceIn(shortest, highest)
    }

    private fun background(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE))
        setStroke(dp(1), MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY))
    }

    /** Builds the bubble without showing it. */
    fun createView(): View {
        val container = LinearLayout(context).apply {
            id = R.id.extension_popup_sheet
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
        }
        root = container

        val target = page?.let { ExtensionScripts.resourceUrl(extension.id, it) } ?: extension.popupUrl()
        if (target == null) {
            container.addView(
                TextView(context).apply {
                    text = context.getString(R.string.extensions_no_popup)
                    setPadding(dp(20), dp(20), dp(20), dp(8))
                    setTextColor(MaterialColors.getColor(context, android.R.attr.textColorPrimary, 0))
                },
            )
            if (onOpenSettings != null) {
                container.addView(
                    TextView(context).apply {
                        text = context.getString(R.string.extensions_settings)
                        setPadding(dp(20), dp(8), dp(20), dp(20))
                        setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryFixed, 0))
                        setOnClickListener {
                            dismiss()
                            onOpenSettings.invoke()
                        }
                    },
                )
            }
            return container
        }

        val view = createWebView()
        container.addView(
            view,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        webView = view
        view.loadUrl(target)
        return container
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val view = WebView(context)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = WebSettings.getDefaultUserAgent(context)
        }
        view.setBackgroundColor(
            MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface, 0),
        )
        view.isVerticalScrollBarEnabled = true
        val api = ChromeApiBridge(
            context.applicationContext,
            onOpenTab,
            null,
            self = { view },
            onClose = { dismiss() },
            onPopupSize = { width, height -> resizeToContent(width, height) },
        )
        bridge = api
        view.addJavascriptInterface(api, "ExplorerChrome")
        // 18.0.0 beta 8 (item 4): the loader is told whose page this is, so a popup that asks for
        // its script from the root of the site finds it as well (bundled pages do).
        val assets = ExtensionFiles.assetLoader(context.applicationContext, extension.id)
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                webView: WebView,
                request: WebResourceRequest,
            ) = assets.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                webView: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url.toString()
                if (ExtensionScripts.isExtensionUrl(url)) {
                    // A page of the extension itself: it stays in the bubble.
                    webView.loadUrl(url)
                    return true
                }
                // A link in a popup belongs to a tab, not to the bubble.
                onOpenTab?.invoke(url)
                dismiss()
                return true
            }

            override fun onPageStarted(webView: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(webView, url, favicon)
                handShim(webView)
            }

            override fun onPageFinished(webView: WebView, url: String?) {
                super.onPageFinished(webView, url)
                handShim(webView)
                // The popup says how big it is, and the bubble follows. 18.0.0 beta 9 (item 8):
                // asked again a moment later, and once more when its pictures are there, since a
                // popup that fills itself in is not the size of its first paint.
                val measure =
                    "(function(){try{if(window.__explorerPopupSize){window.__explorerPopupSize();}}catch(e){}})()"
                webView.evaluateJavascript(measure, null)
                webView.postDelayed({ try { webView.evaluateJavascript(measure, null) } catch (e: Throwable) {} }, 400)
                webView.postDelayed({ try { webView.evaluateJavascript(measure, null) } catch (e: Throwable) {} }, 1500)
                // 18.0.0 beta 8 (item 4): a page that is there but empty — a file the extension
                // does not bring, a script that threw before it drew anything — used to leave the
                // bubble as a plain rectangle of the theme's colour. It says what happened now.
                webView.evaluateJavascript(
                    "(function(){try{" +
                        "var b=document.body;" +
                        "if(!b){return 'empty';}" +
                        "var text=String(b.innerText||'').trim();" +
                        "if(b.childElementCount===0 && text.length===0){return 'empty';}" +
                        "return 'drawn';" +
                        "}catch(e){return 'empty';}})()",
                ) { result ->
                    if (result != null && result.contains("empty")) showFailedPage(webView)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                // Only the page of the popup itself, not a picture that a working popup misses.
                if (request.isForMainFrame) showFailedPage(view)
            }
        }
        return view
    }

    /**
     * 18.0.0 beta 8 (item 4): the popup could not be shown — the file is not in the extension, the
     * page came back empty, or the WebView could not load it. The bubble says so, with the name of
     * the extension and the way to its settings, instead of standing there as a coloured rectangle.
     */
    private fun showFailedPage(view: WebView) {
        val container = root as? android.widget.LinearLayout ?: return
        if (container.getChildAt(0) !== view) return
        container.removeAllViews()
        try {
            view.stopLoading()
            webView = null
            view.destroy()
        } catch (e: Throwable) {
            // Already gone.
        }
        container.addView(
            TextView(context).apply {
                id = R.id.extension_popup_failed
                text = context.getString(R.string.extensions_popup_failed, extension.name)
                setPadding(dp(20), dp(20), dp(20), dp(8))
                setTextColor(MaterialColors.getColor(context, android.R.attr.textColorPrimary, 0))
            },
        )
        val actions = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        actions.addView(
            TextView(context).apply {
                text = context.getString(R.string.extensions_popup_reload)
                setPadding(dp(8), dp(8), dp(8), dp(12))
                setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryFixed, 0))
                setOnClickListener {
                    dismiss()
                    show()
                }
            },
        )
        if (onOpenSettings != null) {
            actions.addView(
                TextView(context).apply {
                    text = context.getString(R.string.extensions_settings)
                    setPadding(dp(8), dp(8), dp(8), dp(12))
                    setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryFixed, 0))
                    setOnClickListener {
                        dismiss()
                        onOpenSettings.invoke()
                    }
                },
            )
        }
        container.addView(actions)
        resizeToContent(320, 150)
    }

    /**
     * chrome.* before the popup's own script reaches for it. A popup's inline script runs before
     * the page callbacks, which is why the shim is handed over as a document-start script where
     * the WebView has them; running it twice does nothing, the shim guards itself.
     */
    private fun handShim(view: WebView) {
        val api = bridge ?: return
        val bundle = ExtensionScripts.shimBundle(extension.id, api.pageToken())
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, bundle, setOf("*"))
            } else {
                view.evaluateJavascript(bundle, null)
            }
        } catch (e: Throwable) {
            // Without document-start scripts the shim still lands with the page callbacks.
            view.evaluateJavascript(bundle, null)
        }
    }
}
