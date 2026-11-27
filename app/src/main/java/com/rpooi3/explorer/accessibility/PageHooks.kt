/*
 * Foss Explorer: what the accessibility settings do to a page and a tab.
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.accessibility

import com.rpooi3.explorer.profile.ProfilePrefs
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.rpooi3.explorer.R
import com.rpooi3.explorer.preferences.PageZoomPreference
import com.rpooi3.explorer.unit.HelperUtils
import com.rpooi3.explorer.view.CatToast
import com.rpooi3.explorer.view.NinjaWebView

object PageHooks {

    /** How long the offer of the simplified view stays. */
    private const val OFFER_MS = 8000

    /** The zoom [tab] shows [url] with, in percent. */
    @JvmStatic
    fun zoomOf(tab: NinjaWebView, url: String?): Int {
        val prefs = ProfilePrefs.settings(tab.context)
        // A private tab keeps its zoom to itself: the site is not written down.
        return if (tab.isPrivateTab && tab.sessionZoom > 0) tab.sessionZoom else PageZoom.zoomFor(prefs, url)
    }

    /** Sets the size of text for the page at [url]; called whenever a tab is set up for a page. */
    @JvmStatic
    fun applyZoom(tab: NinjaWebView, url: String?) {
        val prefs = ProfilePrefs.settings(tab.context)
        tab.settings.textZoom = PageZoom.textZoom(tab.context, prefs, zoomOf(tab, url))
    }

    /**
     * 18.0.0 beta 7 (item 4): prints the page of [tab], the way Chromium for Android does it.
     *
     * The page is not rasterised by Explorer: the WebView makes the print adapter itself
     * (`WebView.createPrintDocumentAdapter`, which is the print support of the Chromium engine
     * inside System WebView), and Android's print service — the same `PrintManager` Chromium
     * hands its job to — turns it into a document: PDF, or a printer on the network. Nothing of
     * the page leaves the device unless the user chooses a printer, and pages Explorer draws
     * itself (the home page, an error page, the offline notice) are not printed at all.
     *
     * @return false if there is nothing to print, so that the caller can say so
     */
    @JvmStatic
    fun printPage(tab: NinjaWebView): Boolean {
        val context = tab.context
        val url = tab.url
        if (url.isNullOrEmpty() || tab.isShowingOwnPage() || tab.isShowingHomePage()) return false
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        val title = tab.title?.takeIf { it.isNotBlank() } ?: HelperUtils.domain(url)
        val jobName = context.getString(R.string.print_job, title)
        return try {
            val adapter: PrintDocumentAdapter = tab.createPrintDocumentAdapter(jobName)
            manager.print(jobName, adapter, PrintAttributes.Builder().build())
            true
        } catch (e: Throwable) {
            // A WebView without print support, or a device without a print service.
            Log.w("ExplorerPrint", "No print job: " + e)
            false
        }
    }

    /** A page has finished loading in [tab]. */
    @JvmStatic
    fun onPageFinished(tab: NinjaWebView, url: String?) {
        val prefs = ProfilePrefs.settings(tab.context)
        if (!ReaderMode.isCandidate(url) || tab.isShowingOwnPage) return
        if (prefs.getBoolean(PageZoom.KEY_FORCE_ENABLE, false)) {
            tab.evaluateJavascript(PageZoom.FORCE_ENABLE_ZOOM_SCRIPT, null)
        }
        if (!ReaderMode.isEnabled(prefs)) return
        // The article view itself, or a page the view was offered for already.
        if (tab.readerUrl == url) return
        val script = ReaderMode.detectScript(tab.context) ?: return
        tab.evaluateJavascript(script) { answer ->
            if (answer == "true" && tab.isForeground && tab.url == url) offerReader(tab, url)
        }
    }

    /** Chromium's message "View Reading mode?" with the button "View". */
    private fun offerReader(tab: NinjaWebView, url: String?) {
        tab.readerUrl = url
        val root = tab.rootView?.findViewById<View>(R.id.main_content) ?: return
        val snackbar = Snackbar.make(root, R.string.cr_reader_message_title, OFFER_MS)
        HelperUtils.makeSnackbarRound(snackbar)
        snackbar.setAction(R.string.cr_reader_message_button) { showReader(tab) }
        snackbar.show()
    }

    /** Shows the article of the page in [tab]. */
    @JvmStatic
    fun showReader(tab: NinjaWebView) {
        val prefs = ProfilePrefs.settings(tab.context)
        tab.readerUrl = tab.url
        ReaderMode.show(tab, ReaderMode.showsLinks(prefs)) { shown ->
            if (!shown) CatToast.show(tab.context, R.string.reader_unavailable)
        }
    }

    private val swipes = java.util.WeakHashMap<NinjaWebView, TouchpadSwipe>()

    /** Every pointer event of [tab] passes here; a two-finger swipe on a touchpad goes back or forward. */
    @JvmStatic
    fun onPointerEvent(tab: NinjaWebView, event: MotionEvent) {
        if (event.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN)) return
        val prefs = ProfilePrefs.settings(tab.context)
        if (!TouchpadSwipe.isEnabled(prefs)) return
        val swipe = swipes.getOrPut(tab) { TouchpadSwipe() }
        when (swipe.onEvent(event) { tab.canScrollHorizontally(it) }) {
            TouchpadSwipe.Direction.BACK -> if (tab.canGoBack()) tab.goBack()
            TouchpadSwipe.Direction.FORWARD -> if (tab.canGoForward()) tab.goForward()
            null -> Unit
        }
    }

    /**
     * Chromium's zoom control for the site in [tab]: the value, minus,
     * slider and plus, in a sheet at the bottom. The zoom is kept for the site.
     */
    @JvmStatic
    fun showZoomSheet(tab: NinjaWebView): BottomSheetDialog {
        val context = tab.context
        val prefs = ProfilePrefs.settings(context)
        val sheet = BottomSheetDialog(context)
        val view = View.inflate(context, R.layout.dialog_page_zoom, null)
        fun bind() {
            PageZoomPreference.bind(view, zoomOf(tab, tab.url)) { zoom ->
                if (tab.isPrivateTab) tab.sessionZoom = zoom else PageZoom.setSiteZoom(prefs, tab.url, zoom)
                applyZoom(tab, tab.url)
                bind()
            }
        }
        bind()
        sheet.setContentView(view)
        sheet.show()
        return sheet
    }
}
