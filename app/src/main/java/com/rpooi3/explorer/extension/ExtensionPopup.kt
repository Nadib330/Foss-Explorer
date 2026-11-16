/*
 * Explorer: the popup of an extension.
 * Own code, GPL-3.0.
 *
 * Tapping the puzzle piece opens this sheet: the popup of the extension in a WebView of its own,
 * with the same bridge as a tab has, so chrome.storage works there too. Its files come from
 * [ExtensionFiles], which is why the relative addresses of a popup load at all.
 */
package com.rpooi3.explorer.extension

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.rpooi3.explorer.R

/**
 * The popup of [extension] in a sheet from the bottom.
 *
 * @param onOpenSettings what the button of the sheet does: the extension settings
 * @param onOpenTab where a link the popup follows goes, if the browser can open a tab
 * @param onClose called once the sheet has gone
 * @param page the page to show, relative to the extension; by default its popup, but the
 *   options page of an extension is shown the same way
 */
class ExtensionPopup @JvmOverloads constructor(
    private val context: Context,
    private val extension: WebExtension,
    private val onOpenSettings: (() -> Unit)? = null,
    private val onOpenTab: ((String) -> Unit)? = null,
    private val onClose: (() -> Unit)? = null,
    private val page: String? = null,
) {

    private var dialog: BottomSheetDialog? = null
    private var webView: WebView? = null
    private var bridge: ChromeApiBridge? = null
    private var loader: androidx.webkit.WebViewAssetLoader? = null

    val isShowing: Boolean get() = dialog?.isShowing == true

    /** The view of the open popup, or null while it is closed. */
    val contentView: View? get() = dialog?.findViewById(R.id.extension_popup_sheet)

    /** The WebView of the popup, for tests and for going back inside it. */
    fun webView(): WebView? = webView

    fun show() {
        if (isShowing) return
        val sheet = BottomSheetDialog(context)
        val root = createView()
        sheet.setContentView(root)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        sheet.setOnDismissListener {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
            if (dialog === sheet) dialog = null
            onClose?.invoke()
        }
        dialog = sheet
        sheet.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    /** Builds the sheet without showing it. */
    fun createView(): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(context).apply {
            id = R.id.extension_popup_sheet
            orientation = LinearLayout.VERTICAL
        }

        root.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                }
                setBackgroundResource(R.drawable.bg_sheet_handle)
            },
        )

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), dp(8))
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setImageDrawable(iconOf(extension))
        }
        val title = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            }
            text = extension.name
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTextColor(MaterialColors.getColor(context, android.R.attr.textColorPrimary, 0))
        }
        header.addView(icon)
        header.addView(title)
        if (onOpenSettings != null) {
            header.addView(
                ImageButton(context).apply {
                    id = R.id.extension_popup_settings
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    setBackground(null)
                    setImageResource(R.drawable.icon_settings)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        MaterialColors.getColor(context, android.R.attr.textColorSecondary, 0),
                    )
                    contentDescription = context.getString(R.string.extensions_settings)
                    setBackgroundResource(R.drawable.bg_menu_row)
                    setOnClickListener {
                        dismiss()
                        onOpenSettings.invoke()
                    }
                },
            )
        }
        root.addView(header)

        val target = page?.let { ExtensionScripts.resourceUrl(extension.id, it) } ?: extension.popupUrl()
        if (target == null) {
            root.addView(
                TextView(context).apply {
                    text = context.getString(R.string.extensions_no_popup)
                    setPadding(dp(16), dp(8), dp(16), dp(24))
                },
            )
            return root
        }

        val view = createWebView()
        val screen = context.resources.displayMetrics.heightPixels
        val height = minOf(dp(560), (screen * 0.72f).toInt())
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        webView = view
        view.loadUrl(target)
        return root
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
        val api = ChromeApiBridge(context.applicationContext, onOpenTab)
        bridge = api
        view.addJavascriptInterface(api, "ExplorerChrome")
        val assets = ExtensionFiles.assetLoader(context.applicationContext)
        loader = assets
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
                if (ExtensionScripts.isExtensionUrl(url)) return false
                // A link in a popup belongs to a tab, not to the sheet.
                onOpenTab?.invoke(url)
                dismiss()
                return true
            }

            override fun onPageStarted(webView: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(webView, url, favicon)
                injectShim(webView)
            }

            override fun onPageFinished(webView: WebView, url: String?) {
                super.onPageFinished(webView, url)
                injectShim(webView)
            }
        }
        return view
    }

    /** chrome.* before the popup's own script reaches for it; running twice does nothing. */
    private fun injectShim(view: WebView) {
        val api = bridge ?: return
        view.evaluateJavascript(ExtensionScripts.shimBundle(extension.id, api.pageToken()), null)
    }

    /** The icon of the extension, or the puzzle piece of Explorer. */
    private fun iconOf(installed: WebExtension): Drawable? =
        com.rpooi3.explorer.view.ExtensionIcons.orDefault(context, installed)
}
