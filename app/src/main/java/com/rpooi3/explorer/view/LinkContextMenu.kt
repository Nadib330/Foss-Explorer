/*
 * Foss Explorer: the menu of a long press on a link or a picture.
 * Own code, GPL-3.0. Entries, their order and their texts are those of the
 * context menu of Chromium for Android (ChromeContextMenuPopulator; BSD, see
 * third_party/chromium-settings).
 */
package com.rpooi3.explorer.view

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.rpooi3.explorer.R
import com.rpooi3.explorer.browser.NinjaDownloadListener
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object LinkContextMenu {

    /**
     * What was pressed.
     *
     * @property linkUrl where the link leads; null if it is a picture without a link
     * @property linkText the text of the link, if it has any
     * @property imageUrl the address of the picture; null if it is a link without a picture
     */
    data class Target(val linkUrl: String?, val linkText: String?, val imageUrl: String?) {
        val isLink: Boolean get() = !linkUrl.isNullOrBlank()
        val isImage: Boolean get() = !imageUrl.isNullOrBlank()
    }

    /** What the menu asks the browser to do. */
    interface Host {
        /** Opens [url] in a new tab behind the one in front; a private tab if [privateTab]. */
        fun openInNewTab(url: String, privateTab: Boolean)

        /** Opens [url] in a new private tab and goes there. */
        fun openInIncognitoTab(url: String)

        /** Asks whether to download [url]. */
        fun download(url: String)

        /** Shares the link [url]. */
        fun shareLink(title: String?, url: String)
    }

    /** The entries of Chromium's menu that Explorer has, in Chromium's order. */
    enum class Item(@StringRes val label: Int) {
        OPEN_IN_NEW_TAB(R.string.cr_contextmenu_open_in_new_tab),
        OPEN_IN_INCOGNITO_TAB(R.string.cr_contextmenu_open_in_incognito_tab),
        COPY_LINK_ADDRESS(R.string.cr_contextmenu_copy_link_address),
        COPY_LINK_TEXT(R.string.cr_contextmenu_copy_link_text),
        SAVE_LINK(R.string.cr_contextmenu_save_link),
        SHARE_LINK(R.string.cr_contextmenu_share_link),
        OPEN_IMAGE_IN_NEW_TAB(R.string.cr_contextmenu_open_image_in_new_tab),
        COPY_IMAGE(R.string.cr_contextmenu_copy_image),
        SAVE_IMAGE(R.string.cr_contextmenu_save_image),
        SHARE_IMAGE(R.string.cr_contextmenu_share_image),
    }

    /**
     * The groups of the menu for [target]: the entries for the link, then the
     * entries for the picture. In a private tab there is no "Open in
     * Incognito tab": "Open in new tab" stays private there, as in Chromium.
     */
    @JvmStatic
    fun groups(target: Target, privateTab: Boolean): List<List<Item>> {
        val groups = ArrayList<List<Item>>()
        if (target.isLink) {
            groups += listOfNotNull(
                Item.OPEN_IN_NEW_TAB,
                Item.OPEN_IN_INCOGNITO_TAB.takeUnless { privateTab },
                Item.COPY_LINK_ADDRESS,
                Item.COPY_LINK_TEXT.takeUnless { target.linkText.isNullOrBlank() },
                Item.SAVE_LINK,
                Item.SHARE_LINK,
            )
        }
        if (target.isImage) {
            groups += listOf(Item.OPEN_IMAGE_IN_NEW_TAB, Item.COPY_IMAGE, Item.SAVE_IMAGE, Item.SHARE_IMAGE)
        }
        return groups
    }

    /**
     * Finds out what is under the finger in [web] and hands it to [onTarget];
     * returns false if it is neither a link nor a picture.
     */
    @JvmStatic
    fun fromLongPress(web: WebView, onTarget: (Target) -> Unit): Boolean {
        val hit = web.hitTestResult
        val extra = hit.extra
        return when (hit.type) {
            WebView.HitTestResult.IMAGE_TYPE -> {
                onTarget(Target(null, null, extra))
                true
            }
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // The page is asked for the rest: the address and text of the link and the picture in it.
                val isImageLink = hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                var answered = false
                val handler = object : Handler(Looper.getMainLooper()) {
                    override fun handleMessage(message: Message) {
                        if (answered) return
                        answered = true
                        val data = message.data
                        val link = data.getString("url") ?: extra.takeUnless { isImageLink }
                        val image = data.getString("src") ?: extra.takeIf { isImageLink }
                        onTarget(Target(link, data.getString("title"), image.takeIf { isImageLink }))
                    }
                }
                web.requestFocusNodeHref(handler.obtainMessage())
                // An engine that does not answer leaves what the press itself told.
                handler.postDelayed({
                    if (!answered) {
                        answered = true
                        onTarget(if (isImageLink) Target(null, null, extra) else Target(extra, null, null))
                    }
                }, ANSWER_TIMEOUT_MS)
                true
            }
            else -> false
        }
    }

    /** Opens the menu for [target]. */
    @JvmStatic
    fun show(activity: Activity, web: WebView?, target: Target, privateTab: Boolean, host: Host): AlertDialog? {
        val groups = groups(target, privateTab)
        if (groups.isEmpty() || activity.isFinishing) return null
        val view = View.inflate(activity, R.layout.dialog_context_menu, null)
        val rows = view.findViewById<LinearLayout>(R.id.context_menu_rows)
        val dialog = MaterialAlertDialogBuilder(activity).setView(view).create()

        // Header: what the menu is about. A tap on the address shows it whole.
        val address = target.linkUrl ?: target.imageUrl.orEmpty()
        val title = view.findViewById<TextView>(R.id.context_menu_title)
        val url = view.findViewById<TextView>(R.id.context_menu_url)
        title.text = target.linkText?.trim().orEmpty()
        title.isVisible = title.text.isNotEmpty()
        url.text = if (address.startsWith("data:")) activity.getString(R.string.context_menu_embedded_image) else address
        url.setOnClickListener { url.maxLines = if (url.maxLines == 1) EXPANDED_LINES else 1 }
        val image = view.findViewById<ImageView>(R.id.context_menu_image)
        if (target.isImage) {
            image.setImageResource(R.drawable.icon_image)
            showThumbnail(activity, image, target.imageUrl!!)
        }

        // A line below the header and between the groups, as in Chromium.
        for (group in groups) {
            rows.addView(MaterialDivider(activity), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            for (item in group) {
                rows.addView(row(activity, item) {
                    dialog.dismiss()
                    onItem(activity, web, target, item, privateTab, host)
                })
            }
        }
        dialog.show()
        return dialog
    }

    private fun row(context: Context, item: Item, onClick: () -> Unit): TextView = TextView(context).apply {
        id = View.generateViewId()
        tag = item
        setText(item.label)
        val density = resources.displayMetrics.density
        minHeight = (ROW_HEIGHT_DP * density).toInt()
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative((24 * density).toInt(), 0, (24 * density).toInt(), 0)
        val appearance = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.textAppearanceBodyLarge, appearance, true)
        setTextAppearance(appearance.resourceId)
        setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0))
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    /** Does what [item] says. */
    @JvmStatic
    fun onItem(activity: Activity, web: WebView?, target: Target, item: Item, privateTab: Boolean, host: Host) {
        val link = target.linkUrl.orEmpty()
        val image = target.imageUrl.orEmpty()
        when (item) {
            Item.OPEN_IN_NEW_TAB -> host.openInNewTab(link, privateTab)
            Item.OPEN_IN_INCOGNITO_TAB -> host.openInIncognitoTab(link)
            Item.COPY_LINK_ADDRESS -> copyText(activity, link)
            Item.COPY_LINK_TEXT -> copyText(activity, target.linkText.orEmpty().trim())
            Item.SAVE_LINK -> host.download(link)
            Item.SHARE_LINK -> host.shareLink(target.linkText, link)
            Item.OPEN_IMAGE_IN_NEW_TAB -> host.openInNewTab(image, privateTab)
            Item.SAVE_IMAGE -> host.download(image)
            Item.COPY_IMAGE -> withImageFile(activity, web, image) { uri ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newUri(activity.contentResolver, "image", uri))
                CatToast.show(activity, R.string.app_done)
            }
            Item.SHARE_IMAGE -> withImageFile(activity, web, image) { uri ->
                val send = Intent(Intent.ACTION_SEND)
                    .setType(activity.contentResolver.getType(uri) ?: "image/*")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newUri(activity.contentResolver, "image", uri)
                activity.startActivity(Intent.createChooser(send, null))
            }
        }
    }

    private fun copyText(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
        CatToast.show(context, R.string.app_done)
    }

    // Pictures ---------------------------------------------------------------------------------

    private val executor = Executors.newSingleThreadExecutor()

    /** The bytes of the picture at [url]: read from the address itself, or fetched with the site's cookies. */
    internal fun imageBytes(url: String, userAgent: String?): ByteArray? = try {
        if (url.startsWith("data:")) {
            NinjaDownloadListener.DataURIParser(url).imagedata.takeIf { it.isNotEmpty() }
        } else {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { connection.setRequestProperty("Cookie", it) }
            try {
                connection.inputStream.use { stream ->
                    val bytes = stream.readBytes()
                    bytes.takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES }
                }
            } finally {
                connection.disconnect()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the picture", e)
        null
    }

    /** A file name for the picture at [url] whose bytes are [bytes]. */
    internal fun imageFileName(url: String, bytes: ByteArray): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val guessed = if (url.startsWith("data:")) "image" else URLUtil.guessFileName(url, null, options.outMimeType)
        // The ending says what the bytes are; if they do not tell, the address is believed.
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(options.outMimeType)
            ?: guessed.substringAfterLast('.', "").takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
            ?: "img"
        val base = guessed.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_").take(MAX_NAME).ifEmpty { "image" }
        return "$base.$extension"
    }

    /**
     * Fetches the picture into the app's cache and hands its address for
     * other apps to [onReady], on the main thread. Says so if it cannot be read.
     */
    private fun withImageFile(activity: Activity, web: WebView?, url: String, onReady: (Uri) -> Unit) {
        val userAgent = web?.settings?.userAgentString
        val app = activity.applicationContext
        executor.execute {
            val bytes = imageBytes(url, userAgent)
            val uri = bytes?.let {
                try {
                    val dir = File(app.cacheDir, SHARED_DIR).apply { mkdirs() }
                    val file = File(dir, imageFileName(url, it))
                    file.writeBytes(it)
                    FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not keep the picture", e)
                    null
                }
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (uri == null) CatToast.show(activity, R.string.app_error) else onReady(uri)
            }
        }
    }

    /** Shows the picture itself in the header, once it has been read. */
    private fun showThumbnail(activity: Activity, view: ImageView, url: String) {
        executor.execute {
            val bytes = imageBytes(url, null) ?: return@execute
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= THUMBNAIL_PX && bounds.outHeight / (sample * 2) >= THUMBNAIL_PX) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@execute
            activity.runOnUiThread {
                if (!view.isAttachedToWindow) return@runOnUiThread
                view.imageTintList = null
                view.setPadding(0, 0, 0, 0)
                view.clipToOutline = true
                view.setImageBitmap(bitmap)
            }
        }
    }

    private const val TAG = "LinkContextMenu"
    private const val ANSWER_TIMEOUT_MS = 300L
    private const val TIMEOUT_MS = 15_000
    private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024
    private const val MAX_NAME = 60
    private const val THUMBNAIL_PX = 160
    private const val ROW_HEIGHT_DP = 48
    private const val EXPANDED_LINES = 10
    private const val SHARED_DIR = "shared_images"
}
