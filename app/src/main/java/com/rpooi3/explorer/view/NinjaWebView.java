package com.rpooi3.explorer.view;

import com.rpooi3.explorer.privacy.PrivateTabs;
import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.rpooi3.explorer.R;
import android.view.MotionEvent;
import androidx.webkit.WebViewCompat;
import com.rpooi3.explorer.site.SiteCategory;
import com.rpooi3.explorer.site.SiteSettings;
import com.rpooi3.explorer.browser.OfflinePage;
import com.rpooi3.explorer.browser.ChromeUserAgent;
import com.rpooi3.explorer.browser.AlbumController;
import com.rpooi3.explorer.browser.BrowserController;
import com.rpooi3.explorer.browser.List_standard;
import com.rpooi3.explorer.browser.NinjaDownloadListener;
import com.rpooi3.explorer.browser.NinjaWebChromeClient;
import com.rpooi3.explorer.browser.NinjaWebViewClient;
import com.rpooi3.explorer.browser.WebAppInterface;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.database.Record;
import com.rpooi3.explorer.home.HomePage;
import com.rpooi3.explorer.database.RecordAction;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;

public class NinjaWebView extends WebView implements AlbumController {

    /**
     * 17.3.3: whether this tab has been closed. WebView has no isDestroyed() of its own, and
     * callbacks that come back late — a store install that finished, a bridge call — need to know
     * that the tab they want to reload is gone.
     */
    private volatile boolean destroyed;

    public boolean fingerPrintProtection;
    public boolean history;
    public boolean adBlock;
    public boolean saveData;
    public boolean camera;
    private Context context;
    private boolean stopped;
    private AdapterTabs album;
    private AlbumController predecessor;
    private NinjaWebViewClient webViewClient;
    private NinjaWebChromeClient webChromeClient;
    private NinjaDownloadListener downloadListener;
    private static String profile;
    private List_standard listStandard;
    private Bitmap favicon;
    private static SharedPreferences sp;
    private boolean foreground;
    private boolean privateTab;
    private boolean privateIsolated;
    private static BrowserController browserController;

    // Address of a load that was requested or has started but not finished; null when idle.
    private String pendingUrl;
    // Site settings of the page in this tab; read by the web view's clients, also off the main thread.
    private volatile boolean popUps;
    private volatile boolean appsOnDevice;
    private volatile boolean pageIsOnDevice;
    private int downloadsSinceGesture;
    private Bitmap thumbnail;
    // "Desktop site" of the three-dot menu, for this tab only. Null: as the site's profile says.
    private Boolean desktopSite;
    // The address the tab stands for while it shows one of the browser's own pages (the offline
    // notice of a website, or the game); null on ordinary pages.
    private String standInUrl;
    private boolean standInIsOffline;
    // True from the moment such a page is handed to the WebView until its load has started.
    private boolean standInLoadPending;
    // 17.3.1: what the chrome.* shim of the extensions talks to (see ChromeApiBridge).
    private com.rpooi3.explorer.extension.ChromeApiBridge extensionBridge;

    public NinjaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NinjaWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NinjaWebView(Context context) {
        this(context, false);
    }

    /**
     * @param privateTab a private tab keeps no history, icons or restore entry
     *                   and, where the WebView supports it, its own cookies
     *                   and site data (see {@link PrivateTabs})
     */
    public NinjaWebView(Context context, boolean privateTab) {
        super(context);
        // Must happen before anything else is done with the WebView.
        this.privateTab = privateTab;
        this.privateIsolated = privateTab && PrivateTabs.attach(this);
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        String profile = sp.getString("profile", "standard");
        this.context = context;
        this.foreground = false;
        this.fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", true);
        this.history = sp.getBoolean(profile + "_history", true);
        this.adBlock = sp.getBoolean(profile + "_adBlock", false);
        this.saveData = sp.getBoolean(profile + "_saveData", false);
        this.camera = sp.getBoolean(profile + "_camera", false);

        this.stopped = false;
        this.listStandard = new List_standard(this.context);
        this.album = new AdapterTabs(this.context, this, browserController);
        this.webViewClient = new NinjaWebViewClient(this);
        this.webChromeClient = new NinjaWebChromeClient(this);
        this.downloadListener = new NinjaDownloadListener( this);

        initWebView();
        initAlbum();
    }

    public boolean isForeground() {
        return foreground;
    }

    /** What the chrome.* shim of the extensions calls; null before {@link #initWebView()}. */
    public com.rpooi3.explorer.extension.ChromeApiBridge getExtensionBridge() {
        return extensionBridge;
    }

    /** Whether this is a private tab. */
    public boolean isPrivateTab() {
        return privateTab;
    }

    /** Whether this private tab has its own cookies and site data. */
    public boolean isPrivateIsolated() {
        return privateIsolated;
    }

    /**
     * Records that a page load was requested, has started ({@code url} is its
     * address) or has finished ({@code null}), and lets the activity show or
     * hide the home page accordingly.
     */
    public void setPendingUrl(@Nullable String url) {
        pendingUrl = url;
        if (foreground && browserController != null) browserController.updateHomePage();
    }

    /** Site setting "Sound": mutes the tab where the installed WebView can do that. */
    private void setSoundAllowed(boolean allowed) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) return;
        try {
            WebViewCompat.setAudioMuted(this, !allowed);
        } catch (RuntimeException e) {
            Log.w(TAG, "The tab cannot be muted: " + e.getMessage());
        }
    }

    /** Site setting "Pop-ups and redirects" for the page in this tab. */
    public boolean isPopUpsAllowed() {
        return popUps;
    }

    /**
     * Site setting "Apps on device": whether the page in this tab may load
     * {@code host}. Websites are kept from reaching into this device
     * (localhost); a page that is itself served from this device may.
     */
    public boolean mayLoadFrom(String host) {
        return appsOnDevice || pageIsOnDevice || !SiteSettings.isLoopback(host);
    }

    /**
     * Counts a download the page starts and tells whether it is a further
     * one since the user last touched the page or went somewhere, which is
     * what the site setting "Automatic downloads" is about.
     */
    public boolean isFurtherDownload() {
        return ++downloadsSinceGesture > 1;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (MotionEvent.ACTION_DOWN == event.getActionMasked()) downloadsSinceGesture = 0;
        // Android 14 and later report a two-finger swipe on a touchpad as a touch gesture.
        com.rpooi3.explorer.accessibility.PageHooks.onPointerEvent(this, event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // Earlier versions report it as the scrolling of a mouse.
        com.rpooi3.explorer.accessibility.PageHooks.onPointerEvent(this, event);
        return super.dispatchGenericMotionEvent(event);
    }

    /** Zoom of this tab alone, in percent; 0 if it uses the saved zoom. Private tabs zoom this way. */
    private int sessionZoom;

    public int getSessionZoom() {
        return sessionZoom;
    }

    public void setSessionZoom(int zoom) {
        sessionZoom = zoom;
    }

    /** The page the simplified view was offered for or is shown of; it is not offered twice in a row. */
    @Nullable
    private String readerUrl;

    @Nullable
    public String getReaderUrl() {
        return readerUrl;
    }

    public void setReaderUrl(@Nullable String url) {
        readerUrl = url;
    }

    /**
     * Shows the "No internet" notice with the dinosaur game instead of the
     * page at {@code failedUrl}. The tab keeps that address: reloading tries
     * the page again.
     */
    public void showOfflinePage(@NonNull String failedUrl) {
        showOwnPage(failedUrl, true, OfflinePage.offlineHtml(context, failedUrl));
    }

    /** Shows the dinosaur game on its own, under {@link OfflinePage#DINO_URL}. */
    public void showDinoPage() {
        showOwnPage(OfflinePage.DINO_URL, false, OfflinePage.dinoHtml(context));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showOwnPage(String url, boolean offline, String html) {
        standInUrl = url;
        standInIsOffline = offline;
        standInLoadPending = true;
        stopped = false;
        // The page is the browser's own and needs its script and its sounds, whatever the site
        // settings say. The settings of the next website are applied when that is loaded.
        getSettings().setJavaScriptEnabled(true);
        setSoundAllowed(true);
        loadDataWithBaseURL(url, html, "text/html", "UTF-8", url);
    }

    /** Whether this tab shows the offline notice of a website. */
    public boolean isShowingOfflinePage() {
        return null != standInUrl && standInIsOffline;
    }

    /** Whether this tab shows one of the browser's own pages, which is not part of the history. */
    public boolean isShowingOwnPage() {
        return null != standInUrl;
    }

    /** Called when the WebView starts loading a page, of whatever kind. */
    public void onLoadStarted() {
        if (standInLoadPending) standInLoadPending = false;
        else standInUrl = null;
    }

    /**
     * The address of the tab. For the browser's own pages this is the address
     * they stand for, whatever the WebView calls the page it was given.
     */
    @Nullable
    @Override
    public String getUrl() {
        return null != standInUrl ? standInUrl : super.getUrl();
    }

    /** The picture of this tab's page for its card in the tab switcher; null until one was taken. */
    @Nullable
    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(@Nullable Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }

    /**
     * 17.3.2: whether the tab is on the Chrome Web Store.
     *
     * The store treats anything that is not Chrome on a desktop as the wrong browser and shows
     * "Switch to Google Chrome" in place of its install button, so Explorer shows it the way
     * Chrome on a computer does, and the extensions are added by Explorer itself.
     */
    public boolean isChromeWebStore() {
        return com.rpooi3.explorer.extension.CrxDownloader.isStoreUrl(null != pendingUrl ? pendingUrl : getUrl());
    }

    /** Whether this tab asks websites for their desktop version. */
    public boolean isDesktopSite() {
        // The store is always asked for its desktop version; there is no other one that installs.
        if (isChromeWebStore()) return true;
        return null != desktopSite ? desktopSite : sp.getBoolean(getProfile() + "_desktop", false);
    }

    /** Switches this tab between the mobile and the desktop version of websites and reloads the page. */
    public void setDesktopSite(boolean desktop) {
        // 17.3.2: on the Chrome Web Store "Desktop site" is always on and cannot be turned off.
        if (isChromeWebStore()) desktop = true;
        desktopSite = desktop;
        reload();
    }

    /** Whether this tab shows the built-in home page instead of a website. */
    public boolean isShowingHomePage() {
        return HomePage.isShowing(pendingUrl, getUrl());
    }

    public static BrowserController getBrowserController() {
        return browserController;
    }

    public void setBrowserController(BrowserController browserController) {
        NinjaWebView.browserController = browserController;
        this.album.setBrowserController(browserController);
    }

    private synchronized void initWebView() {
        setWebViewClient(webViewClient);
        setWebChromeClient(webChromeClient);
        setDownloadListener(downloadListener);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public synchronized void initPreferences(String url) {

        this.setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, true);
        WebSettings webSettings = getSettings();
        webSettings.setDefaultTextEncodingName("utf-8");
        // Safe Browsing and preloading, as chosen under Privacy and security.
        com.rpooi3.explorer.privacy.PrivacySettings.apply(sp, webSettings);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        setVerticalScrollBarEnabled(true);
        setHorizontalScrollBarEnabled(true);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        setScrollbarFadingEnabled(false);

        webSettings.setSupportMultipleWindows(true);
        webSettings.setAllowFileAccess(true);
        // Accessibility: the default zoom, or the zoom saved for the site.
        com.rpooi3.explorer.accessibility.PageHooks.applyZoom(this, url);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        profile = sp.getString("profile", "profileStandard");
        String profileOriginal = profile;

        if (listStandard.isWhite(url)) {
            profile = HelperUtils.domain(url);
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            try {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, sp.getBoolean(profile + "_night", true));
            } catch (UnsupportedOperationException e) {
                // Darkening web content is optional; a WebView that cannot do it must not stop the tab from opening.
                Log.w(TAG, "Algorithmic darkening is not available: " + e.getMessage());
            }
        }

        // Websites see Google Chrome on Android (with this device's Android version) instead of
        // an embedded WebView; "Desktop site" sends what Chrome sends for it. See ChromeUserAgent.
        final String defaultAgent = WebSettings.getDefaultUserAgent(context);
        // 17.3.2: the Chrome Web Store is always the desktop one, whatever this tab is set to.
        final boolean store = com.rpooi3.explorer.extension.CrxDownloader.isStoreUrl(url);
        final boolean desktop = store || (null != desktopSite ? desktopSite : sp.getBoolean(profile + "_desktop", false));
        // The client hints have to agree with the user agent.
        ChromeUserAgent.applyClientHints(webSettings, defaultAgent, desktop);
        requestedWithHidden = ChromeUserAgent.hideRequestedWithHeader(webSettings);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        if (desktop) {
            webSettings.setUserAgentString(ChromeUserAgent.desktop(defaultAgent));
            // Lay the page out as wide as a desktop window and show all of it.
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
            this.setInitialScale(100);
            // 17.3.3: navigator.platform and the client hints of the page say Windows as well.
            com.rpooi3.explorer.browser.DesktopIdentity.apply(
                    this, com.rpooi3.explorer.browser.ChromeUserAgent.majorVersion(defaultAgent));
        } else {
            webSettings.setUserAgentString(ChromeUserAgent.mobile(defaultAgent));
            webSettings.setUseWideViewPort(false);
            webSettings.setLoadWithOverviewMode(false);
            this.setInitialScale(0);
            // 17.3.3: a mobile page is told nothing of Windows; the script of desktop mode goes.
            com.rpooi3.explorer.browser.DesktopIdentity.clear(this);
        }
        // What this site may do: the site settings (general choice of the profile, or the site's own exception).
        final boolean siteData = SiteSettings.isAllowed(sp, SiteCategory.SITE_DATA, url, profile);
        webSettings.setDomStorageEnabled(siteData && sp.getBoolean(profile + "_dom", true));
        webSettings.setMediaPlaybackRequiresUserGesture(sp.getBoolean(profile + "_saveData", true));
        webSettings.setBlockNetworkImage(!sp.getBoolean(profile + "_images", true));
        // "Ask first" has to reach the page's request, so only "blocked" switches the feature off here.
        webSettings.setGeolocationEnabled(SiteSettings.State.BLOCK != SiteSettings.state(sp, SiteCategory.LOCATION, url, profile));
        webSettings.setJavaScriptEnabled(SiteSettings.isAllowed(sp, SiteCategory.JAVASCRIPT, url, profile));
        popUps = SiteSettings.isAllowed(sp, SiteCategory.POPUPS, url, profile);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(popUps);
        appsOnDevice = SiteSettings.isAllowed(sp, SiteCategory.APPS_ON_DEVICE, url, profile);
        pageIsOnDevice = SiteSettings.isLoopback(SiteSettings.site(url));
        setSoundAllowed(SiteSettings.isAllowed(sp, SiteCategory.SOUND, url, profile));

        fingerPrintProtection = sp.getBoolean(profile + "_fingerPrintProtection", false);
        history = sp.getBoolean(profile + "_saveHistory", true);
        adBlock = !SiteSettings.isAllowed(sp, SiteCategory.ADS, url, profile);
        saveData = sp.getBoolean(profile + "_saveData", true);
        camera = SiteSettings.State.BLOCK != SiteSettings.state(sp, SiteCategory.CAMERA, url, profile);

        try {
            CookieManager manager = CookieManager.getInstance();
            manager.setAcceptCookie(siteData);
            manager.setAcceptThirdPartyCookies(this, SiteSettings.isAllowed(sp, SiteCategory.THIRD_PARTY_COOKIES, url, profile));
            manager.getCookie(url);
        } catch (Exception e) {
            Log.i(TAG, "Error loading cookies: " + e);
        }
        this.addJavascriptInterface(new WebAppInterface(context), "AndroidInterface");
        // 17.3.1: the other side of the chrome.* shim of the extensions. A tab keeps one bridge
        // for its whole life, so the token of a page load is the same for every script of it.
        this.extensionBridge = new com.rpooi3.explorer.extension.ChromeApiBridge(
                context.getApplicationContext(),
                null,
                extensionId -> {
                    // 17.3.2: once the extension is in, the page is loaded again, so the button
                    // of the store says that it is there and its content scripts start working.
                    final NinjaWebView tab = NinjaWebView.this;
                    com.rpooi3.explorer.extension.StoreHook.install(
                            context.getApplicationContext(), extensionId, installed -> {
                                // 17.3.3: only a tab that is still there is loaded again; the
                                // callback comes from the install, and the tab may be gone.
                                if (null != installed && !tab.isDestroyed()) {
                                    try {
                                        tab.reload();
                                    } catch (RuntimeException e) {
                                        Log.w(TAG, "Cannot reload after the install: " + e);
                                    }
                                }
                                return kotlin.Unit.INSTANCE;
                            });
                    return kotlin.Unit.INSTANCE;
                });
        this.addJavascriptInterface(this.extensionBridge, "ExplorerChrome");
        profile = profileOriginal;
    }

    public void setProfileDefaultValues() {

        RecordAction action = new RecordAction(context);
        action.open(true);
        action.addBookmark(new Record("FOSS Browser - WIKI", "https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki", 0, 0));
        action.close();

        sp.edit()
                .putBoolean("profileStandard_saveData", true)
                .putBoolean("profileStandard_images", true)
                .putBoolean("profileStandard_adBlock", true)
                .putBoolean("profileStandard_trackingULS", false)
                // Location, camera and microphone: sites ask first (see SiteSettings).
                .putBoolean("profileStandard_location", true)
                .putBoolean("profileStandard_fingerPrintProtection", false)
                .putBoolean("profileStandard_cookies", true)
                .putBoolean("profileStandard_cookiesThirdParty", false)
                .putBoolean("profileStandard_deny_cookie_banners", true)
                .putBoolean("profileStandard_javascript", true)
                .putBoolean("profileStandard_javascriptPopUp", false)
                .putBoolean("profileStandard_saveHistory", true)
                .putBoolean("profileStandard_camera", true)
                .putBoolean("profileStandard_microphone", true)
                .putBoolean("profileStandard_dom", true)
                .putBoolean("profileStandard_night", true)
                .putBoolean("profileStandard_desktop", false).apply();
    }

    public void setProfileChanged () {
        sp.edit()
                .putString("profile", "profileChanged")
                .putBoolean("profileChanged_saveData", sp.getBoolean( "profileStandard_saveData", true))
                .putBoolean("profileChanged_images", sp.getBoolean( "profileStandard_images", true))
                .putBoolean("profileChanged_adBlock", sp.getBoolean( "profileStandard_adBlock", true))
                .putBoolean("profileChanged_trackingULS", sp.getBoolean( "profileStandard_trackingULS", true))
                .putBoolean("profileChanged_location", sp.getBoolean( "profileStandard_location", false))
                .putBoolean("profileChanged_fingerPrintProtection", sp.getBoolean( "profileStandard_fingerPrintProtection", true))
                .putBoolean("profileChanged_cookies", sp.getBoolean( "_cookies", false))
                .putBoolean("profileChanged_cookiesThirdParty", sp.getBoolean( "profileStandard_cookiesThirdParty", false))
                .putBoolean("profileChanged_deny_cookie_banners", sp.getBoolean( "profileStandard_deny_cookie_banners", false))
                .putBoolean("profileChanged_javascript", sp.getBoolean( "profileStandard_javascript", true))
                .putBoolean("profileChanged_javascriptPopUp", sp.getBoolean( "profileStandard_javascriptPopUp", false))
                .putBoolean("profileChanged_saveHistory", sp.getBoolean( "profileStandard_saveHistory", true))
                .putBoolean("profileChanged_camera", sp.getBoolean( "profileStandard_camera", false))
                .putBoolean("profileChanged_microphone", sp.getBoolean( "profileStandard_microphone", false))
                .putBoolean("profileChanged_dom", sp.getBoolean( "profileStandard_dom", true))
                .putBoolean("profileChanged_night", sp.getBoolean( "profileStandard_night", true))
                .putBoolean("profileChanged_desktop", sp.getBoolean( "profileStandard_desktop", false)).apply();
    }

    private synchronized void initAlbum() {
        album.setBrowserController(browserController);
    }

    private boolean requestedWithHidden = false;

    public synchronized HashMap<String, String> getRequestHeaders() {
        HashMap<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("DNT", "1");
        //  Server-side detection for GlobalPrivacyControl
        requestHeaders.put("Sec-GPC", "1");
        // Chrome sends no X-Requested-With header. Where the WebView cannot switch its own
        // header off, a neutral value at least keeps this app's name out of it.
        if (!requestedWithHidden) requestHeaders.put("X-Requested-With", "com.duckduckgo.mobile.android");
        requestHeaders.put("Referer", this.getUrl());
        profile = sp.getString("profile", "profileStandard");
        if (sp.getBoolean(profile + "_saveData", true)) requestHeaders.put("Save-Data", "on");
        return requestHeaders;
    }

    @Override
    public synchronized void stopLoading() {
        stopped = true;
        super.stopLoading();
    }

    @SuppressLint("ApplySharedPref")
    public synchronized void goBack() {
        WebBackForwardList mWebBackForwardList = this.copyBackForwardList();
        int currentIndex = mWebBackForwardList.getCurrentIndex();
        if (currentIndex > 0) {
            WebHistoryItem historyItem = mWebBackForwardList.getItemAtIndex(currentIndex - 1);
            if (historyItem != null) {
                String historyUrl = historyItem.getUrl();
                initPreferences(historyUrl);
                String currentUrl = this.getUrl();
                if (currentUrl != null && !Objects.equals(HelperUtils.domain(currentUrl), HelperUtils.domain(historyUrl))
                        && sp.getBoolean("sp_standard_always", true)) {
                    sp.edit().putString("profile", "profileStandard").commit();
                }
            }
        }
        super.goBack();
    }

    @Override
    public synchronized void reload() {
        if (null != standInUrl) {
            // Reloading the offline notice means trying the website again; the game starts over.
            final String url = standInUrl;
            if (standInIsOffline) {
                standInUrl = null;
                loadUrl(url);
            } else {
                showDinoPage();
            }
            return;
        }
        stopped = false;
        this.initPreferences(this.getUrl());
        super.reload();
    }

    @Override
    public synchronized void loadUrl(@NonNull String url) {

        InputMethodManager imm = (InputMethodManager) this.context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getWindowToken(), 0);

        if (OfflinePage.isDinoUrl(url)) {
            setPendingUrl(OfflinePage.DINO_URL);
            showDinoPage();
            return;
        }
        // Going somewhere else ends the offline notice or the game at once, not only when the
        // WebView reports the new page.
        standInUrl = null;
        standInLoadPending = false;

        if (url.contains(";jsessionid=")) {
            String tracking = url.substring(url.lastIndexOf(";"));
            url = url.replace(tracking, "");
        }

        String urlToLoad = BrowserUnit.redirectURL( this, sp, url);
        setPendingUrl(urlToLoad);
        downloadsSinceGesture = 0;

        if (!Objects.equals(HelperUtils.domain(this.getUrl()), HelperUtils.domain(urlToLoad)) && sp.getBoolean("sp_standard_always", true)) {
            sp.edit().putString("profile", "profileStandard").apply();
        }

        favicon = null;
        stopped = false;

        listStandard = new List_standard(context);
        profile = sp.getString("profile", "profileStandard");
        if (listStandard.isWhite(url)) profile = HelperUtils.domain(urlToLoad);

        if (sp.getBoolean(profile + "_trackingULS", true) && urlToLoad.contains("?") && urlToLoad.contains("/")) {

            String lastIndex = urlToLoad.substring(urlToLoad.lastIndexOf("/"));
            String tracking = urlToLoad.substring(urlToLoad.lastIndexOf("?"));
            String urlClean = urlToLoad.replace(tracking, "");
            if (lastIndex.contains(tracking)) {

                stopLoading();
                String m = context.getString(R.string.dialog_tracking) + " \"" + tracking + "\"" + "?";

                if (m.length() > 150) {
                    m = m.substring(0, 150) + " [...]?\"";
                }

                GridItem item_01 = new GridItem(context.getString(R.string.app_ok), R.drawable.icon_check);
                GridItem item_02 = new GridItem( context.getString(R.string.app_no), R.drawable.icon_close);
                GridItem item_03 = new GridItem( context.getString(R.string.menu_edit), R.drawable.icon_edit);

                View dialogView = View.inflate(context, R.layout.dialog_menu, null);
                MaterialAlertDialogBuilder builderTrack = new MaterialAlertDialogBuilder(context);
                builderTrack.setPositiveButton(context.getString(R.string.app_cancel), (dialog, which) -> {
                    dialog.cancel();
                    loadUrl("about:blank");
                });
                builderTrack.setView(dialogView);

                AlertDialog dialogTrack = builderTrack.create();
                HelperUtils.setupCardView(HelperUtils.getActivity(context), dialogView, HelperUtils.domain(url), url,this, m, dialogTrack, true);
                HelperUtils.setupDialog(context, dialogTrack);
                dialogTrack.show();
                dialogTrack.setCancelable(false);

                GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
                final List<GridItem> gridList = new LinkedList<>();
                gridList.add(gridList.size(), item_01);
                gridList.add(gridList.size(), item_02);
                gridList.add(gridList.size(), item_03);
                AdapterGrid gridAdapter = new AdapterGrid(context, gridList);
                menu_grid.setAdapter(gridAdapter);
                gridAdapter.notifyDataSetChanged();
                menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                    switch (position) {
                        case 0:
                            dialogTrack.cancel();
                            initPreferences(BrowserUnit.queryWrapper(context, urlClean));
                            super.loadUrl(BrowserUnit.queryWrapper(context, urlClean), getRequestHeaders());
                            break;
                        case 1:
                            dialogTrack.cancel();
                            initPreferences(BrowserUnit.queryWrapper(context, urlToLoad));
                            super.loadUrl(BrowserUnit.queryWrapper(context, urlToLoad), getRequestHeaders());
                            break;
                        case 2:
                            dialogTrack.cancel();

                            View dialogEdit = View.inflate(context, R.layout.dialog_edit, null);
                            String u = context.getString(R.string.dialog_tracking) + " \"" + tracking + "\"" + "?";
                            if (u.length() > 150) {
                                u = u.substring(0, 150) + " [...]?\"";
                            }

                            TextInputLayout editBottomLayout = dialogEdit.findViewById(R.id.editBottomLayout);
                            TextInputLayout editTopLayout = dialogEdit.findViewById(R.id.editTopLayout);
                            editBottomLayout.setHint(context.getString(R.string.dialog_URL_hint));
                            editTopLayout.setVisibility(GONE);
                            EditText input = dialogEdit.findViewById(R.id.editBottom);
                            input.setText(urlToLoad);
                            HelperUtils.showSoftKeyboard(input);

                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                            builder.setPositiveButton(context.getString(R.string.app_ok), (dialog, which) -> {
                                dialog.dismiss();
                                String newValue = Objects.requireNonNull(input.getText()).toString();
                                initPreferences(BrowserUnit.queryWrapper(context, newValue));
                                super.loadUrl(BrowserUnit.queryWrapper(context, newValue), getRequestHeaders());
                            });
                            builder.setNegativeButton(context.getString(R.string.app_cancel), (dialog1, which) -> dialog1.cancel());
                            builder.setView(dialogEdit);
                            Dialog dialog = builder.create();
                            HelperUtils.setupCardView(HelperUtils.getActivity(context), dialogEdit, HelperUtils.domain(urlToLoad), urlToLoad, this, u, dialog, true);
                            HelperUtils.setupDialog(context, dialog);
                            dialog.setCancelable(false);
                            dialog.show();
                            HelperUtils.showSoftKeyboard(input);
                            break;
                    }
                });
            }

        } else if (url.startsWith("http://") && sp.getString("dialog_neverAsk", "no").equals("no")) {

            stopLoading();

            GridItem item_01 = new GridItem("https://", R.drawable.icon_secure);
            GridItem item_02 = new GridItem( "http://", R.drawable.icon_unsecure);
            GridItem item_03 = new GridItem( context.getString(R.string.app_never), R.drawable.icon_close);

            View dialogView = View.inflate(context, R.layout.dialog_menu, null);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            builder.setPositiveButton(context.getString(R.string.app_cancel), (dialog, which) -> {
                dialog.cancel();
                loadUrl("about:blank");
            });
            builder.setView(dialogView);

            String secure = url.replace("http://", "https://");
            String unSecure = url;

            AlertDialog dialog = builder.create();
            HelperUtils.setupCardView(HelperUtils.getActivity(context), dialogView, HelperUtils.domain(url), url, this, context.getString(R.string.toast_unsecured), dialog, true);
            HelperUtils.setupDialog(context, dialog);
            dialog.show();
            dialog.setCancelable(false);

            GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
            final List<GridItem> gridList = new LinkedList<>();
            gridList.add(gridList.size(), item_01);
            gridList.add(gridList.size(), item_02);
            gridList.add(gridList.size(), item_03);
            AdapterGrid gridAdapter = new AdapterGrid(context, gridList);
            menu_grid.setAdapter(gridAdapter);
            gridAdapter.notifyDataSetChanged();
            menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                switch (position) {
                    case 0:
                        dialog.cancel();
                        initPreferences(BrowserUnit.queryWrapper(context, secure));
                        super.loadUrl(BrowserUnit.queryWrapper(context, secure), getRequestHeaders());
                        break;
                    case 1:
                        dialog.cancel();
                        initPreferences(BrowserUnit.queryWrapper(context, unSecure));
                        super.loadUrl(BrowserUnit.queryWrapper(context, unSecure), getRequestHeaders());
                        break;
                    case 2:
                        sp.edit().putString("dialog_neverAsk", "yes").apply();
                        initPreferences(BrowserUnit.queryWrapper(context, unSecure));
                        super.loadUrl(BrowserUnit.queryWrapper(context, unSecure), getRequestHeaders());
                        break;
                }
            });
        } else {
            initPreferences(BrowserUnit.queryWrapper(context, urlToLoad));
            super.loadUrl(BrowserUnit.queryWrapper(context, urlToLoad), getRequestHeaders());
        }
    }

    @Override
    public View getAlbumView() {
        return album.getAlbumView();
    }

    public void setAlbumTitle(String title, String url) {
        album.setAlbumTitle(title, url);
    }

    @Override
    public synchronized void activate() {
        requestFocus();
        foreground = true;
        album.activate();
    }

    @Override
    public synchronized void deactivate() {
        clearFocus();
        foreground = false;
        album.deactivate();
    }

    public synchronized void updateTitle(int progress) {
        if (foreground && !stopped) browserController.updateProgress(progress);
        else if (foreground) browserController.updateProgress(BrowserUnit.LOADING_STOPPED);
    }

    public synchronized void updateTitle(String title, String url) {
        // In the tab list the home page goes by the app's name, not by "about:blank".
        if (HomePage.isHomeUrl(url)) title = context.getString(R.string.app_name);
        album.setAlbumTitle(title, url);
        FaviconHelper.setFavicon(context, album.getAlbumView(), url, R.id.icon_start, R.drawable.icon_image_broken);
    }

    /** Whether this tab was closed; a tab that is gone may not be reloaded or written into. */
    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public synchronized void destroy() {
        destroyed = true;
        stopLoading();
        onPause();
        clearHistory();
        setVisibility(GONE);
        removeAllViews();
        super.destroy();
    }

    public boolean isFingerPrintProtection() {
        return fingerPrintProtection;
    }

    public boolean isHistory() {
        return history && !privateTab;
    }

    public boolean isAdBlock() {
        return adBlock;
    }

    public boolean isSaveData() {
        return saveData;
    }

    public boolean isCamera() {
        return camera;
    }

    public void resetFavicon() {
        this.favicon = null;
    }
    @Override
    public Bitmap getFavicon() {
        return favicon;
    }

    public void setFavicon(Bitmap favicon) {
        this.favicon = favicon;
        FaviconHelper faviconHelper = new FaviconHelper(context);
        RecordAction action = new RecordAction(context);
        action.open(false);
        action.close();
        // A private tab leaves no trace of the sites it visited in the icon cache.
        if (!privateTab) faviconHelper.addFavicon(this.context, getUrl(), getFavicon());
    }

    public static String getProfile() {
        return sp.getString("profile", "profileStandard");
    }

    public AlbumController getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(AlbumController predecessor) {
        this.predecessor = predecessor;
    }
}