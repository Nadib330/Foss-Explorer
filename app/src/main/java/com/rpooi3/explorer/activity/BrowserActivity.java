package com.rpooi3.explorer.activity;

import static android.content.ContentValues.TAG;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.net.ConnectivityManager;
import android.net.Network;
import com.rpooi3.explorer.browser.OfflinePage;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;

import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import com.rpooi3.explorer.downloads.DownloadsActivity;
import com.rpooi3.explorer.library.BookmarksActivity;
import com.rpooi3.explorer.library.HistoryActivity;
import com.rpooi3.explorer.library.LibraryActivity;
import com.rpooi3.explorer.view.LinkContextMenu;
import com.rpooi3.explorer.view.MainMenuSheet;
import com.rpooi3.explorer.view.ProtectionPanel;
import com.rpooi3.explorer.privacy.TrackingProtection;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.webkit.WebViewFeature;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.browser.AdBlock;
import com.rpooi3.explorer.browser.AlbumController;
import com.rpooi3.explorer.browser.BannerBlock;
import com.rpooi3.explorer.browser.BrowserContainer;
import com.rpooi3.explorer.browser.BrowserController;
import com.rpooi3.explorer.browser.List_standard;
import com.rpooi3.explorer.browser.NinjaDownloadListener;
import com.rpooi3.explorer.database.FaviconHelper;
import com.rpooi3.explorer.database.Record;
import com.rpooi3.explorer.database.RecordAction;
import com.rpooi3.explorer.database.RecordUnit;
import com.rpooi3.explorer.unit.CustomItemsUtils;
import com.rpooi3.explorer.unit.HelperUtils;
import com.rpooi3.explorer.home.HomePage;
import com.rpooi3.explorer.home.HomePageView;
import com.rpooi3.explorer.home.IncognitoPage;
import com.rpooi3.explorer.view.CatToast;
import com.rpooi3.explorer.view.CustomRedirectsDialog;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.DeleteUtils;
import com.rpooi3.explorer.unit.PermissionCallback;
import com.rpooi3.explorer.unit.PermissionUtils;
import com.rpooi3.explorer.view.AdapterCustomList;
import com.rpooi3.explorer.view.AdapterMenu;
import com.rpooi3.explorer.view.AdapterSearch;
import com.rpooi3.explorer.view.AdapterGrid;
import com.rpooi3.explorer.view.GridItem;
import com.rpooi3.explorer.view.MenuItem;
import com.rpooi3.explorer.view.TabSwitcher;
import com.rpooi3.explorer.view.NewTabAnimation;
import com.rpooi3.explorer.view.NinjaWebView;
import com.rpooi3.explorer.view.AdapterRecord;
import com.rpooi3.explorer.view.SwipeTouchListener;
import com.rpooi3.explorer.privacy.PrivateTabs;
import com.rpooi3.explorer.view.TopBarController;
import com.rpooi3.explorer.accessibility.PageHooks;
import com.rpooi3.explorer.accessibility.PageZoom;
import com.rpooi3.explorer.accessibility.ReaderMode;
import com.rpooi3.explorer.privacy.IncognitoLock;
import com.rpooi3.explorer.tabs.InactiveTabs;
import com.rpooi3.explorer.view.WebsiteInfoDialog;

public class BrowserActivity extends AppCompatActivity implements BrowserController {

    // ==========================================
    // UI VIEWS & LAYOUTS
    // ==========================================
    private TextView appBar_title;
    private View contentView;
    private View customView;
    private FrameLayout contentFrame;
    private FrameLayout fullscreenHolder;
    private LinearLayout tab_container;
    private BottomNavigationView bottom_navigation;
    private CircularProgressIndicator progressBar;
    private TopBarController topBar;
    private HomePageView homePage;

    // Listen & Adapter
    private ListView listView;
    private ListView list_search;
    private AdapterRecord adapter;
    private AdapterSearch adapterSearch;

    // Eingabefelder (Suche)
    private TextInputEditText search_input;
    private EditText searchOnSiteInput;
    private RelativeLayout searchOnSiteLayout;

    // Tasten (Buttons) & Zubehör
    private ImageButton buttonBack;
    private View buttonMenu;
    private android.widget.ImageView buttonShield;

    /** Shows the shield on websites, plain or crossed out as the site is protected or not. */
    void updateShield() {
        if (null == buttonShield || null == ninjaWebView) return;
        final boolean applies = TrackingProtection.appliesTo(ninjaWebView.getUrl())
                && !ninjaWebView.isShowingOwnPage() && !ninjaWebView.isShowingHomePage();
        buttonShield.setVisibility(applies ? VISIBLE : GONE);
        if (applies) buttonShield.setImageResource(ProtectionPanel.shieldIcon(ninjaWebView));
    }
    private View iconPrivate;
    private View buttonReload;
    private TextView tabCount;
    /** The menu behind the three-dot button while it is open, otherwise null. */
    MainMenuSheet mainMenu;

    // Multimedia
    private VideoView videoView;

    // Dialoge
    private AlertDialog dialogOverview;
    /** The full-screen overview of the open tabs. */
    private TabSwitcher tabSwitcher;
    /** Set while a tab is being opened in front: it comes in with the new-tab animation. */
    private boolean animateNewTab;
    private AlertDialog dialog_overflow;
    private AlertDialog dialogCustomSearches;
    private View dialogViewSearch;

    // ==========================================
    // ANWENDUNGS-KONTROLLE & SYSTEM (CORE)
    // ==========================================
    private Activity activity;
    private SharedPreferences sp;
    private List_standard listStandard;
    private AlbumController currentAlbumController;
    private String overViewTab;

    private long newIcon;
    private long filterBy;
    private boolean filter;

    @SuppressLint("StaticFieldLeak")
    private static Context context;

    @SuppressLint("StaticFieldLeak")
    private static NinjaWebView ninjaWebView;

    public static Context getAppContext() {
        return context;
    }

    // ==========================================
    // BERECHTIGUNGEN (PERMISSIONS)
    // ==========================================
    private PermissionCallback activeCallback;

    private final ActivityResultLauncher<String> locationLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> handleSystemResult(isGranted, "Standort"));

    private final ActivityResultLauncher<String> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> handleSystemResult(isGranted, "Kamera"));

    private final ActivityResultLauncher<String> microphoneLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> handleSystemResult(isGranted, "Mikrofon"));

    // Universelle Hilfsmethode, um den Code der 3 Einzelmethoden drastisch zu verkürzen
    /**
     * Android's own permission, the second step: whether the site may ask at
     * all has been decided before, see {@link com.rpooi3.explorer.site.SitePermissions}.
     */
    private void checkAndRequestPermission(final PermissionUtils.PermissionType type, final ActivityResultLauncher<String> launcher, final PermissionCallback callback) {
        if (PermissionUtils.hasPermission(this, type)) {
            callback.onPermissionResult(true);
        } else {
            activeCallback = callback;
            launcher.launch(PermissionUtils.getManifestString(type));
        }
    }

    public void requestLocationPermission(final PermissionCallback callback) {
        checkAndRequestPermission(PermissionUtils.PermissionType.LOCATION, locationLauncher, callback);
    }

    public void requestCameraPermission(final PermissionCallback callback) {
        checkAndRequestPermission(PermissionUtils.PermissionType.CAMERA, cameraLauncher, callback);
    }

    public void requestMicrophonePermission(final PermissionCallback callback) {
        checkAndRequestPermission(PermissionUtils.PermissionType.MICROPHONE, microphoneLauncher, callback);
    }

    private void handleSystemResult(final boolean isGranted, final String name) {
        if (null != activeCallback) {
            activeCallback.onPermissionResult(isGranted);
            activeCallback = null;
        }
        if (!isGranted) {
            String text = name + ": " + getString(R.string.app_permission);
            CatToast.show(context, text, 2);
        }
    }

    // ==========================================
    // FILE CHOOSER & FOTO-DATEIEN
    // ==========================================
    private ValueCallback<Uri[]> filePathCallback; // Unverändert gelassen, falls extern genutzt
    private ValueCallback<Uri[]> mFilePathCallback;
    private Uri cameraPhotoUri;
    private File currentPhotoFile;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;
    /** Opens the history or bookmarks screen; the result is the page the user picked there. */
    private ActivityResultLauncher<Intent> libraryLauncher;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (null == mFilePathCallback) return;
                Uri[] results = null;
                if (Activity.RESULT_OK == result.getResultCode() && null != cameraPhotoUri) {
                    if (null != currentPhotoFile && currentPhotoFile.exists() && 0 < currentPhotoFile.length()) {
                        results = new Uri[]{cameraPhotoUri};
                    }
                }
                if (null == results && Activity.RESULT_OK == result.getResultCode() && null != result.getData()) {
                    Intent data = result.getData();
                    if (null != data.getData()) {
                        results = new Uri[]{data.getData()};
                    } else if (null != data.getClipData()) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                mFilePathCallback.onReceiveValue(null != results ? results : new Uri[0]);
                mFilePathCallback = null;
                currentPhotoFile = null;
            }
    );

    // ==========================================
    // EIGENE HILFSKLASSEN & METHODEN
    // ==========================================
    private AlbumController nextAlbumController(final boolean next) {
        if (1 >= BrowserContainer.size()) return currentAlbumController;
        final List<AlbumController> list = BrowserContainer.list();
        int index = list.indexOf(currentAlbumController);
        if (next) {
            index = (index + 1) % list.size();
        } else {
            index = (0 > index - 1) ? list.size() - 1 : index - 1;
        }
        return list.get(index);
    }

    private class VideoCompletionListener implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
        @Override
        public boolean onError(final MediaPlayer mp, final int what, final int extra) {
            return false;
        }
        @Override
        public void onCompletion(final MediaPlayer mp) {
            onHideCustomView();
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Basis-Initialisierungen & Themes
        activity = this;
        context = this;
        sp = PreferenceManager.getDefaultSharedPreferences(context);

        new BannerBlock(context);
        new AdBlock(context);
        HelperUtils.initTheme(activity);

        // 2. SharedPreferences Konfiguration & Flags
        if (sp.getBoolean("sp_screenOn", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Zuerst prüfen, ob das Profil zurückgesetzt werden muss
        if (sp.getBoolean("sp_standard_restart", false)) {
            sp.edit().putString("profile", "profileStandard").apply();
        }

        // Standard-Werte für diesen App-Start setzen
        sp.edit()
                .putInt("restart_changed", 0)
                .putBoolean("pdf_create", false)
                .putString("openBackground_dialog", "show")
                .apply();

        // The overview lists the open tabs. History and bookmarks have their own screens.
        overViewTab = getString(R.string.album_title_tab);

        // 3. UI-Layout & Kantenloses Design (Edge-to-Edge)
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        contentFrame = findViewById(R.id.main_content);
        tabSwitcher = new TabSwitcher(findViewById(R.id.tab_switcher), contentFrame, findViewById(R.id.home_page), tabSwitcherHost);

        final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // The status bar strip takes the toolbar's colour (see TopBarController), so its icons follow light/dark.
        final boolean night = Configuration.UI_MODE_NIGHT_YES == (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);

        topBar = new TopBarController(findViewById(R.id.main), sp);
        homePage = new HomePageView(findViewById(R.id.home_page), new HomePageView.Listener() {
            @Override
            public void onHomeSearch() {
                openSearch();
            }

            @Override
            public void onHomeMenu(@NonNull final View anchor) {
                showMainMenu(anchor);
            }

            @Override
            public void onHomeOpen(@NonNull final String url) {
                ninjaWebView.loadUrl(url);
            }

            @Override
            public void onHomeOpenInNewTab(@NonNull final String url, final boolean foreground) {
                addAlbum(HelperUtils.domain(url), url, foreground);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            final Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            final int keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            final boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            final int paddingBottom = isKeyboardVisible ? keyboardHeight : systemBars.bottom;
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, paddingBottom);
            topBar.setKeyboardVisible(isKeyboardVisible);
            return insets;
        });

        // 4. Launcher & Dialoge initialisieren
        requestCameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::startCustomFileChooser);
        libraryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            final Intent data = result.getData();
            if (RESULT_OK != result.getResultCode() || null == data) return;
            final String url = data.getStringExtra(LibraryActivity.EXTRA_URL);
            if (null == url || url.isEmpty()) return;
            final String target = data.getStringExtra(LibraryActivity.EXTRA_TARGET);
            if (LibraryActivity.TARGET_NEW_TAB.equals(target)) {
                addAlbum(HelperUtils.domain(url), url, true);
            } else if (LibraryActivity.TARGET_PRIVATE_TAB.equals(target)) {
                setWebView(getString(R.string.private_tab), url, true, true);
            } else {
                ninjaWebView.loadUrl(url);
            }
        });

        // Dialog: Übersicht
        final MaterialAlertDialogBuilder builderOverview = new MaterialAlertDialogBuilder(context);
        final View dialogViewOverview = View.inflate(context, R.layout.dialog_overview, null);
        builderOverview.setView(dialogViewOverview);
        dialogOverview = builderOverview.create();
        bottom_navigation = dialogViewOverview.findViewById(R.id.bottom_navigation);
        tab_container = dialogViewOverview.findViewById(R.id.listTabs);
        HelperUtils.setupDialog(context, dialogOverview);
        // Never shown any more: the tabs have a screen of their own (TabSwitcher). The dialog's
        // views are still referred to by code from before.
        dialogOverview.create();

        registerNetworkCallback();
        // Site setting "Automatically remove permissions".
        com.rpooi3.explorer.site.SiteSettings.removeUnusedPermissions(sp, System.currentTimeMillis());

        // Address and search entry: a panel of this screen, not a dialog (see include_search_panel).
        dialogViewSearch = findViewById(R.id.search_panel);

        // 5. Download Receiver (Hier ist dein stabiler Snackbar-Code!)
        final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                final String text = context.getString(R.string.app_done) + ". " + context.getString(R.string.menu_download) + "?";
                CatToast.show(context, text, 1);
            }
        };

        final int flag = TIRAMISU <= Build.VERSION.SDK_INT ? Context.RECEIVER_EXPORTED : 0;
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), flag);

        // Whatever a private tab left behind when the app was killed goes now, before any tab exists.
        PrivateTabs.wipe();

        // 6. Komponenten-Inits
        initOmniBox();
        initSearchOnSite();
        initOverview();
        hideSearch();

        // 7. The tabs come back as they were left, as in Chrome. Those that were not used
        // for long go to the inactive section instead (Settings > Tabs and groups).
        {
            final String saveDefaultProfile = sp.getString("profile", "profileStandard");
            final String savedTabs = sp.getString("openTabs", "");

            if (!savedTabs.isEmpty()) {
                final List<String> openTabs = InactiveTabs.sort(sp,
                        new ArrayList<>(Arrays.asList(TextUtils.split(savedTabs, "‚‗‚"))), System.currentTimeMillis());
                for (int counter = 0; counter < openTabs.size(); counter++) {
                    addAlbum(getString(R.string.app_name), openTabs.get(counter), 1 > BrowserContainer.size());
                }
            }
            sp.edit()
                    .putString("profile", saveDefaultProfile)
                    .putBoolean("restoreOnRestart", false)
                    .apply();
        }
        initIncognitoLock();

        // 8. Zurück-Taste (OnBackPressed) Handler für moderne Geräte
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Ruft deine exakte Logik auf modernen Geräten auf
                executeBackPressLogic();
            }
        });
    }

    // Hier lebt exakt deine Logik, zentral an einem Ort
    private void executeBackPressLogic() {
        if (isIncognitoLockShown()) {
            // Locked private tabs: Back leaves the app, it does not reach the page behind the lock.
            moveTaskToBack(true);
        } else if (null != fullscreenHolder || null != customView || null != videoView) {
            Log.v(TAG, "Foss Explorer in fullscreen mode");
        } else if (isSearchOpen()) {
            hideSearch();
        } else if (isTabSwitcherOpen()) {
            // Back leaves the tab switcher for the tab that was in front.
            tabSwitcher.hide();
        } else if (VISIBLE == searchOnSiteLayout.getVisibility()) {
            searchOnSiteInput.setText("");
            searchOnSiteLayout.setVisibility(GONE);
            topBar.showAddressBar();
        } else if (ninjaWebView.canGoBack()) {
            sp.edit().putBoolean("backPressed", true).apply();
            ninjaWebView.goBack();
        } else {
            removeAlbum(currentAlbumController);
        }
    }

    // Der universelle Fallback für alle Android-Versionen (inklusive Android 15),
    // falls der Dispatcher das Event mal nicht abfängt (z. B. durch WebView-Fokus)
    @SuppressLint("GestureBackNavigation")
    @Override
    public boolean dispatchKeyEvent(final android.view.KeyEvent event) {
        if (KeyEvent.KEYCODE_BACK == event.getKeyCode()) {
            if (KeyEvent.ACTION_UP == event.getAction()) {
                // Führe die Browser-Logik aus
                executeBackPressLogic();
                return true; // Dem System sagen: Event erfolgreich verarbeitet!
            }
            // Mitzählen/Konsumieren des DOWN-Events, damit das System nicht doppelt feuert
            return true;
        }
        // Bei allen anderen Tasten arbeitet das System ganz normal weiter
        return super.dispatchKeyEvent(event);
    }



    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onStop() {
        // Leaving the app locks the private tabs, if the setting says so. The lock is put
        // over them now, so that it is there when the app comes back.
        IncognitoLock.onLeave(this, hasPrivateTabs());
        updateIncognitoLock();
        super.onStop();
    }

    private View incognitoLockView;

    private final ActivityResultLauncher<Intent> incognitoUnlockLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> { if (RESULT_OK == result.getResultCode()) unlockIncognito(); });

    private void initIncognitoLock() {
        incognitoLockView = findViewById(R.id.incognito_lock);
        findViewById(R.id.incognito_unlock).setOnClickListener(v ->
                IncognitoLock.authenticate(this, incognitoUnlockLauncher, this::unlockIncognito));
        findViewById(R.id.incognito_see_other_tabs).setOnClickListener(v -> seeOtherTabs());
        updateIncognitoLock();
    }

    /** The screen lock was passed: the private tabs are open again until the app is left the next time. */
    void unlockIncognito() {
        IncognitoLock.setLocked(false);
        updateIncognitoLock();
    }

    boolean isIncognitoLockShown() {
        return null != incognitoLockView && VISIBLE == incognitoLockView.getVisibility();
    }

    /** Covers the private tabs while they are locked: the tab in front, or their cards in the tab switcher. */
    void updateIncognitoLock() {
        if (null == incognitoLockView) return;
        if (IncognitoLock.getLocked() && !hasPrivateTabs()) IncognitoLock.setLocked(false);
        final boolean privateInFront = isTabSwitcherOpen()
                ? tabSwitcher.getPrivateMode()
                : currentAlbumController instanceof NinjaWebView tab && tab.isPrivateTab();
        final boolean show = IncognitoLock.getLocked() && privateInFront;
        incognitoLockView.setVisibility(show ? VISIBLE : GONE);
        if (show) hideSearch();
    }

    /** "See other tabs" on the lock: goes to the normal tabs and leaves the private ones locked. */
    private void seeOtherTabs() {
        if (isTabSwitcherOpen()) {
            tabSwitcher.showNormalTabs();
        } else {
            NinjaWebView normal = null;
            for (final AlbumController controller : BrowserContainer.list()) {
                if (controller instanceof NinjaWebView tab && !tab.isPrivateTab()) normal = tab;
            }
            if (null != normal) showAlbum(normal);
            else addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
        }
        updateIncognitoLock();
    }

    /**
     * Moves the open tabs that were not used for the chosen time into the inactive
     * section. The tab in front stays.
     */
    void moveInactiveTabs() {
        final long now = System.currentTimeMillis();
        InactiveTabs.closeOld(sp, now);
        final List<NinjaWebView> tabs = new ArrayList<>();
        for (final AlbumController controller : BrowserContainer.list()) {
            if (!(controller instanceof NinjaWebView tab) || tab.isPrivateTab()) continue;
            if (tab == currentAlbumController) tabs.add(0, tab);
            else tabs.add(tab);
        }
        // The first of the list is never moved; if a private tab is in front, no normal tab is.
        if (tabs.isEmpty() || tabs.get(0) != currentAlbumController) return;
        final List<String> urls = new ArrayList<>();
        for (final NinjaWebView tab : tabs) urls.add(String.valueOf(tab.getUrl()));
        final java.util.Set<Integer> due = InactiveTabs.due(sp, urls, now);
        if (due.isEmpty()) return;
        for (final int index : due) {
            final NinjaWebView tab = tabs.get(index);
            final String title = null == tab.getTitle() ? "" : tab.getTitle();
            InactiveTabs.add(sp, new InactiveTabs.Entry(urls.get(index), title, InactiveTabs.lastActive(sp, urls.get(index), now)));
            tab_container.removeView(tab.getAlbumView());
            BrowserContainer.remove(tab);
        }
        updateOmniBox();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateIncognitoLock();
        if (null != homePage && homePage.isVisible()) homePage.refresh();
        if (1 == sp.getInt("restart_changed", 1)) {
            triggerRebirth(context);
        }
        if (sp.getBoolean("pdf_create", false)) {
            sp.edit().putBoolean("pdf_create", false).apply();
            final String text = getString(R.string.app_done) + ". " + getString(R.string.menu_download) +"?";
            CatToast.show(context, text, 1);
        }
        final Intent intent = getIntent();
        if (null != intent && null != intent.getAction() && !intent.getAction().isEmpty()) {
            dispatchIntent(intent);
            intent.setAction("");
            intent.setData(null);
        }
    }

    @Override
    public void onDestroy() {
        unregisterNetworkCallback();
        DeleteUtils.checkAndExecuteAutoDelete(getApplicationContext());
        final NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(1);
        final boolean hadPrivateTabs = hasPrivateTabs();
        BrowserContainer.clear();
        if (hadPrivateTabs) PrivateTabs.wipe();
        // The list of open tabs stays: they come back with the next start.
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (KeyEvent.KEYCODE_MENU == keyCode) {
            showMainMenu();
        }
        return false;
    }

    @Override
    public synchronized void showAlbum(final AlbumController controller) {
        if (null == controller) return;

        // The tab that leaves the screen gets the picture for its card in the tab switcher.
        if (currentAlbumController instanceof NinjaWebView leaving && leaving != controller
                && -1 != BrowserContainer.indexOf(leaving) && null != tabSwitcher) {
            tabSwitcher.capture(leaving);
        }
        final boolean grow = animateNewTab && contentFrame.isLaidOut() && 1 < BrowserContainer.size();
        animateNewTab = false;

        // 1. Tab-Status umschalten
        if (null != currentAlbumController) currentAlbumController.deactivate();
        currentAlbumController = controller;
        currentAlbumController.activate();
        BrowserContainer.select(controller);
        updateIncognitoLock();
        // A tab that was left on the offline notice tries its page again once there is a connection.
        if (controller instanceof NinjaWebView shown && shown.isShowingOfflinePage() && !OfflinePage.isOffline(this)) {
            shown.reload();
        }

        final View av = (View) controller;
        if (null != av.getBackground()) {
            contentFrame.setBackground(av.getBackground());
        }
        if (null != av.getParent()) {
            ((ViewGroup) av.getParent()).removeView(av);
        }

        // 2. Ansicht mit weicher Überblendung austauschen (falls mehrere Tabs offen sind)
        if (!grow && contentFrame.isLaidOut() && 0 < contentFrame.getChildCount() && 1 < BrowserContainer.size()) {
            final View oldView = contentFrame.getChildAt(0);
            oldView.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(() -> contentFrame.removeView(oldView))
                    .start();

            av.setAlpha(0f);
            contentFrame.addView(av);
            av.animate().alpha(1f).setDuration(250).start();
        } else {
            // Sofortiger Austausch ohne Animation
            contentFrame.removeAllViews();
            contentFrame.addView(av);
            av.setAlpha(1f);
        }

        // 3. Adresszeile aktualisieren
        try {
            updateOmniBox(); } catch (final Exception ignored) {}

        // A new tab grows into place, the page together with the home page that may lie over it.
        if (grow) NewTabAnimation.run(contentFrame, findViewById(R.id.home_page));
    }

    @Override
    public synchronized void removeAlbum(AlbumController controller) {
        // Fall A: Es ist nur noch ein einziger Tab offen
        if (1 >= BrowserContainer.size()) {
            if (controller instanceof NinjaWebView last && last.isShowingHomePage() && !last.isPrivateTab()) {
                // Closing the one new tab that is left leaves the app.
                doubleTapsQuit();
                return;
            }
            // The last tab is closed: a new tab takes its place, and the app stays open.
            addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
            if (1 >= BrowserContainer.size()) return;
        }

        // Fall B: Mehrere Tabs sind offen. The tab closes at once, as in Chrome.
        ((Runnable) () -> {
            // Which tab comes to the front is decided by Chromium's rules (tabs/TabModel.kt): a background
            // tab leaves the tab in front alone; else the tab this one was opened from, else its neighbour.
            final AlbumController next = BrowserContainer.nextTabIfClosed(controller, currentAlbumController, isTabSwitcherOpen());

            tab_container.removeView(controller.getAlbumView());
            BrowserContainer.remove(controller);
            // The data of private tabs goes when the last of them is closed.
            // (BrowserContainer.remove has destroyed the WebView, so the profile is free.)
            if (controller instanceof NinjaWebView closed && closed.isPrivateTab() && !hasPrivateTabs()) {
                PrivateTabs.wipe();
            }

            if (null != next) showAlbum(next);
            if (null != tabSwitcher) tabSwitcher.refresh();
        }).run();

        updateOmniBox();
    }

    /**
     * The start of the address pill: the loading ring while a page loads, the
     * reload button the rest of the time.
     */
    private void setLoading(final boolean loading) {
        progressBar.setVisibility(loading ? VISIBLE : GONE);
        buttonReload.setVisibility(loading ? GONE : VISIBLE);
    }

    @Override
    public synchronized void updateProgress(final int progress) {
        assert null != progressBar;
        if (ninjaWebView.isForeground()) {
            if (100 > progress) {
                setLoading(true);
                progressBar.setProgressCompat(progress, true);
            } else {
                updateOmniBox();
            }
        }
    }

    @Override
    public void showFileChooser(final ValueCallback<Uri[]> filePathCallback) {
        if (null != mFilePathCallback) {
            mFilePathCallback.onReceiveValue(null);
        }
        mFilePathCallback = filePathCallback;
        checkPermissionAndProceed();
    }
    private void checkPermissionAndProceed() {
        if (PermissionUtils.hasPermission(this, PermissionUtils.PermissionType.CAMERA)) {
            startCustomFileChooser(true);
        } else {
            final String cameraPermission = PermissionUtils.getManifestString(PermissionUtils.PermissionType.CAMERA);
            requestCameraPermissionLauncher.launch(cameraPermission);
        }
    }
    private void startCustomFileChooser(final boolean includeCamera) {
        final Intent pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
        pickIntent.setType("*/*");

        final String[] mimeTypes = {"image/*", "video/*", "*/*"};
        pickIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        final Intent chooserIntent;
        if (includeCamera) {
            final Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            setupCameraUri();
            if (null != cameraPhotoUri) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                chooserIntent = Intent.createChooser(pickIntent, null);
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takePictureIntent});
            } else {
                chooserIntent = Intent.createChooser(pickIntent, null);
            }
        } else {
            CatToast.show(context, getString(R.string.app_error), 2);
            chooserIntent = Intent.createChooser(pickIntent, null);
        }
        fileChooserLauncher.launch(chooserIntent);
    }
    private void setupCameraUri() {
        try {
            currentPhotoFile = createImageFile();
            cameraPhotoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", currentPhotoFile);
        } catch (final IOException e) {
            Log.e("Upload", "Fehler beim Erstellen der Kamera-Datei", e);
            currentPhotoFile = null;
            cameraPhotoUri = null;
        }
    }
    private File createImageFile() throws IOException {
        final String imageFileName = "JPEG_" + System.currentTimeMillis() + "_";
        final File storageDir = getCacheDir();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }
    @Override
    public void onShowCustomView(final View view, final WebChromeClient.CustomViewCallback callback) {
        if (null == view) return;
        if (null != customView && null != callback) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        fullscreenHolder = new FrameLayout(context);
        fullscreenHolder.addView(
                customView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
        final FrameLayout decorView = (FrameLayout) getWindow().getDecorView();
        decorView.addView(
                fullscreenHolder,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
        customView.setKeepScreenOn(true);
        ((View) currentAlbumController).setVisibility(GONE);
        setCustomFullscreen(true);

        if (view instanceof FrameLayout) {
            if (((FrameLayout) view).getFocusedChild() instanceof VideoView) {
                videoView = (VideoView) ((FrameLayout) view).getFocusedChild();
                videoView.setOnErrorListener(new VideoCompletionListener());
                videoView.setOnCompletionListener(new VideoCompletionListener());
            }
        }
    }

    @Override
    public void onHideCustomView() {
        final FrameLayout decorView = (FrameLayout) getWindow().getDecorView();
        decorView.removeView(fullscreenHolder);
        customView.setKeepScreenOn(false);
        ((View) currentAlbumController).setVisibility(VISIBLE);
        setCustomFullscreen(false);
        fullscreenHolder = null;
        customView = null;
        if (null != videoView) {
            videoView.setOnErrorListener(null);
            videoView.setOnCompletionListener(null);
            videoView = null; }
        contentFrame.requestFocus();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initOverview() {
        listView = dialogOverview.findViewById(R.id.list_overView);

        final AtomicInteger intPage = new AtomicInteger();
        // 1. Theme-Farben für Badges auslesen
        final TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryInverse, typedValue, true);
        final int color = typedValue.data;
        final TypedValue typedValue2 = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue2, true);
        final int color2 = typedValue2.data;
        // 2. Tab-Badge konfigurieren
        final var pageZeroBadge = bottom_navigation.getOrCreateBadge(R.id.page_0);
        pageZeroBadge.setBackgroundColor(color);
        pageZeroBadge.setBadgeTextColor(color2);
        pageZeroBadge.setHorizontalOffset(0);
        pageZeroBadge.setVerticalOffset(0);

        if (1 < BrowserContainer.size()) {
            pageZeroBadge.setNumber(BrowserContainer.size());
        }
        // 3. Navigation-Listener für die Übersicht festlegen
        bottom_navigation.setOnItemSelectedListener(menuItem -> {

            final View scrollDividerBottom = dialogOverview.findViewById(R.id.scrollDividerBottom);
            final View scrollDividerTop = dialogOverview.findViewById(R.id.scrollDividerTop);
            final NestedScrollView scrollViewTabs = dialogOverview.findViewById(R.id.scrollViewTabs);
            assert null != scrollDividerBottom;
            assert null != scrollDividerTop;
            assert null != scrollViewTabs;
            HelperUtils.setupDialogDividers(listView, scrollDividerTop, scrollDividerBottom);

            final int itemId = menuItem.getItemId();

            // REITER A: Offene Tabs (Page 0)
            if (itemId == R.id.page_0) {
                overViewTab = getString(R.string.album_title_tab);
                intPage.set(R.id.page_0);
                listView.setVisibility(GONE);
                tab_container.setVisibility(VISIBLE);
                scrollDividerTop.setVisibility(GONE);
                scrollDividerBottom.setVisibility(GONE);
            }
            return true;
        });
        // History and bookmarks moved to their own screens, so there is nothing left to switch between.
        bottom_navigation.setVisibility(GONE);
        setSelectedTab();
    }

    // ==========================================
    // HOME PAGE
    // ==========================================

    /**
     * Shows the home page if the current tab is on it, and puts the toolbar
     * into the matching form. Called whenever a tab is switched or a page
     * load starts or finishes.
     */
    @Override
    public void updateHomePage() {
        if (!(currentAlbumController instanceof NinjaWebView) || null == homePage) return;
        final NinjaWebView tab = (NinjaWebView) currentAlbumController;
        // A private tab has a new tab page of its own, Chromium's Incognito page, and keeps
        // its top bar: that page has no search box.
        final boolean incognitoHome = tab.isShowingHomePage() && tab.isPrivateTab();
        final boolean home = tab.isShowingHomePage() && !incognitoHome;
        topBar.setHomeMode(home);
        homePage.setVisible(home);
        if (null == incognitoPage) incognitoPage = new IncognitoPage(findViewById(R.id.incognito_page));
        incognitoPage.setVisible(incognitoHome, tab.isPrivateIsolated());
    }

    private IncognitoPage incognitoPage;

    /**
     * Opens the address field for typing, pre-filled with the current
     * address. Suggestions fill the screen below it until a page is chosen or
     * the field is closed with the back arrow or the back key.
     */
    private void openSearch() {
        initSearch();
        sp.edit().putString("sp_search_customSearches", "").apply();
        final String url = ninjaWebView.getUrl();
        // The home page has no address worth showing or editing.
        search_input.setText(ninjaWebView.isShowingHomePage() || null == url ? "" : url);
        dialogViewSearch.setVisibility(VISIBLE);
        search_input.requestFocus();
        search_input.selectAll();
        HelperUtils.showSoftKeyboard(search_input);
    }

    // ==========================================
    // OMNIBOX INITIALISIERUNG (TEIL 1 & 2)
    // ==========================================
    @SuppressLint({"ClickableViewAccessibility", "UnsafeOptInUsageError"})
    private void initOmniBox() {
        appBar_title = findViewById(R.id.appBar_title);
        contentView = findViewById(android.R.id.content);
        final FloatingActionButton fab_showAppBar = findViewById(R.id.fab_showAppBar);

        fab_showAppBar.setOnClickListener(v1 -> topBar.setHidden(false));

        fab_showAppBar.setOnLongClickListener(v -> {
            fab_showAppBar.setEnabled(false);
            fab_showAppBar.animate()
                    .alpha(0f).scaleX(0f).scaleY(0f)
                    .setDuration(250)
                    .withEndAction(() -> {
                        fab_showAppBar.setVisibility(INVISIBLE);

                        final Runnable fadeInAction = () -> {
                            fab_showAppBar.setVisibility(VISIBLE);
                            fab_showAppBar.animate()
                                    .alpha(1f).scaleX(1f).scaleY(1f)
                                    .setDuration(250)
                                    .withEndAction(() -> fab_showAppBar.setEnabled(true))
                                    .start();
                        };

                        fab_showAppBar.postDelayed(fadeInAction, 5000);
                        fab_showAppBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(@NonNull final View v) {}
                            @Override
                            public void onViewDetachedFromWindow(@NonNull final View v) {
                                fab_showAppBar.removeCallbacks(fadeInAction);
                                fab_showAppBar.removeOnAttachStateChangeListener(this);
                            }
                        });
                    })
                    .start();
            return true;
        });

        // Top bar: back arrow, address pill with share, menu.
        final View addressPill = findViewById(R.id.addressPill);
        addressPill.setOnClickListener(view -> openSearch());
        // The actions for the current page (reload, bookmark, find in page, ...).
        addressPill.setOnLongClickListener(v -> {
            showOverflow(null, null, 0, ninjaWebView.getTitle(), ninjaWebView.getUrl(), null, null, 0);
            return true;
        });

        buttonBack = findViewById(R.id.button_back);
        buttonBack.setOnClickListener(v -> {
            if (ninjaWebView.canGoBack()) {
                sp.edit().putBoolean("backPressed", true).apply();
                ninjaWebView.goBack();
            }
        });

        findViewById(R.id.button_share).setOnClickListener(v -> shareCurrentPage());

        iconPrivate = findViewById(R.id.icon_private);

        buttonMenu = findViewById(R.id.button_menu);
        // The shield left of it: Firefox's protection panel for the page in front.
        buttonShield = findViewById(R.id.button_shield);
        buttonShield.setOnClickListener(view -> {
            if (null != ninjaWebView) ProtectionPanel.show(this, ninjaWebView, this::updateShield);
        });
        buttonMenu.setOnClickListener(view -> showMainMenu());
        buttonMenu.setOnLongClickListener(view -> {
            performGesture("setting_gesture_tabButton", ninjaWebView.getUrl());
            return true;
        });
        buttonMenu.setOnTouchListener(new SwipeTouchListener(context) {
            public void onSwipeTop() { performGesture("setting_gesture_nav_up", ninjaWebView.getUrl()); }
            public void onSwipeBottom() { performGesture("setting_gesture_nav_down", ninjaWebView.getUrl()); }
            public void onSwipeRight() { performGesture("setting_gesture_nav_right", ninjaWebView.getUrl()); }
            public void onSwipeLeft() { performGesture("setting_gesture_nav_left", ninjaWebView.getUrl()); }
        });

        // Bottom bar: home, new tab, tabs.
        findViewById(R.id.button_home).setOnClickListener(v -> ninjaWebView.loadUrl(HomePage.startUrl(sp)));
        findViewById(R.id.button_newTab).setOnClickListener(v -> addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true));

        tabCount = findViewById(R.id.tab_count);
        final View buttonTabs = findViewById(R.id.button_tabs);
        buttonTabs.setOnClickListener(v -> showOverviewPage(R.id.page_0));
        buttonTabs.setOnLongClickListener(v -> {
            performGesture("setting_gesture_overViewButton", ninjaWebView.getUrl());
            return true;
        });
        buttonTabs.setOnTouchListener(new SwipeTouchListener(context) {
            public void onSwipeTop() { performGesture("setting_gesture_tb_up", ninjaWebView.getUrl()); }
            public void onSwipeBottom() { performGesture("setting_gesture_tb_down", ninjaWebView.getUrl()); }
            public void onSwipeRight() { performGesture("setting_gesture_tb_right", ninjaWebView.getUrl()); }
            public void onSwipeLeft() { performGesture("setting_gesture_tb_left", ninjaWebView.getUrl()); }
        });

        list_search = dialogViewSearch.findViewById(R.id.list_search);
        progressBar = findViewById(R.id.menu_progress_bar);
        buttonReload = findViewById(R.id.button_reload);
        buttonReload.setOnClickListener(v -> ninjaWebView.reload());

        final View searchClear = dialogViewSearch.findViewById(R.id.search_clear);
        final View searchQueryRow = dialogViewSearch.findViewById(R.id.search_query_row);
        final TextView searchQueryText = dialogViewSearch.findViewById(R.id.search_query_text);
        search_input = dialogViewSearch.findViewById(R.id.search_input);

        dialogViewSearch.findViewById(R.id.search_back).setOnClickListener(v -> hideSearch());
        searchClear.setOnClickListener(v -> search_input.setText(""));

        // The typed text as first suggestion. A long press offers the other search engines.
        searchQueryRow.setOnClickListener(v -> handleFinalSearch(Objects.requireNonNull(search_input.getText()).toString().trim()));
        searchQueryRow.setOnLongClickListener(v -> {
            final String query = Objects.requireNonNull(search_input.getText()).toString().trim();
            if (!query.isEmpty() && !query.equals(ninjaWebView.getUrl())) {
                showDialogCustomSearches(query);
            } else {
                CatToast.show(this, R.string.toast_input_empty, 3);
            }
            return true;
        });

        search_input.setOnEditorActionListener((v, actionId, event) -> {
            handleFinalSearch(Objects.requireNonNull(search_input.getText()).toString().trim());
            return true;
        });

        search_input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {}
            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                if (null == s) return;
                final String text = s.toString().trim();
                final boolean isCurrentAddress = null != ninjaWebView && text.equals(ninjaWebView.getUrl());
                searchClear.setVisibility(TextUtils.isEmpty(s) ? GONE : VISIBLE);
                searchQueryRow.setVisibility(text.isEmpty() || isCurrentAddress ? GONE : VISIBLE);
                searchQueryText.setText(text);
                if (null != adapterSearch) adapterSearch.getFilter().filter(s);
                sp.edit().putString("searchInput", s.toString()).apply();
            }
            @Override
            public void afterTextChanged(final Editable s) {}
        });
    }
    // ==========================================
    // FINALE SUCHABFRAGE AUSFÜHREN
    // ==========================================
    private void handleFinalSearch(final String query) {
        if (!query.isEmpty() && !query.equals(ninjaWebView.getUrl())) {
            hideSearch();
            ninjaWebView.loadUrl(query);
        } else {
            CatToast.show(this, R.string.toast_input_empty, 3);
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void updateOmniBox() {
        // 1. Sicheres Casting und Early Return
        if (!(currentAlbumController instanceof NinjaWebView)) return;
        ninjaWebView = (NinjaWebView) currentAlbumController;

        final String url = ninjaWebView.getUrl();
        ninjaWebView.initPreferences(url);
        saveOpenedTabs();
        updateShield();

        searchOnSiteLayout.setVisibility(GONE);
        topBar.showAddressBar();
        searchOnSiteInput.setText("");
        updateHomePage();

        // 3. View-Referenzen einmalig außerhalb der Bedingung holen
        final int tabCount = BrowserContainer.size();
        this.tabCount.setText(99 < tabCount ? ":D" : String.valueOf(tabCount));
        buttonBack.setEnabled(ninjaWebView.canGoBack());
        buttonBack.setAlpha(ninjaWebView.canGoBack() ? 1f : 0.38f);
        iconPrivate.setVisibility(ninjaWebView.isPrivateTab() ? VISIBLE : GONE);

        // 4. Strukturierter Null-Check für URL und Sichtbarkeit
        if (null != url && ninjaWebView.isForeground()) {
            // The pill shows the site, like "example.org"; the full address is one tap away.
            // The browser's own pages have no site; they show their whole address.
            // A private tab on its new tab page has no address yet: the pill invites to type one, as in Chromium.
            if (ninjaWebView.isShowingHomePage()) appBar_title.setText(R.string.main_omnibox_input_hint);
            else appBar_title.setText(OfflinePage.isInternalUrl(url) ? url : HelperUtils.domain(url));
        }
        // The page is not loading (any more): the reload button takes the place of the loading ring.
        setLoading(false);
    }

    public void reloadWebview () {
        ninjaWebView.reload();
    }

    private void initSearchOnSite () {
        searchOnSiteLayout = findViewById(R.id.searchOnSiteLayout);
        searchOnSiteInput = findViewById(R.id.searchOnSite_input);

        final Button searchOnSite_buttonHide = findViewById(R.id.searchOnSite_buttonHide);
        searchOnSite_buttonHide.setOnClickListener(v -> topBar.setHidden(true));
        final Button searchOnSite_buttonClose = findViewById(R.id.searchOnSite_buttonClose);
        final TextInputLayout searchOnSite_textField  = findViewById(R.id.searchOnSite_textField);
        assert null != searchOnSite_textField;
        searchOnSite_buttonClose.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(searchOnSiteInput.getText())) searchOnSiteInput.setText("");
            else {
                final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchOnSiteInput.getWindowToken(), 0);
                searchOnSiteLayout.setVisibility(GONE);
                topBar.showAddressBar();
            }
        });
        searchOnSite_textField.setStartIconOnClickListener(v -> ((NinjaWebView) currentAlbumController).findNext(false));
        searchOnSite_textField.setEndIconOnClickListener(v -> ((NinjaWebView) currentAlbumController).findNext(true));
        searchOnSiteInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) { }
            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) { }
            @Override
            public void afterTextChanged(final Editable s) { if (null != currentAlbumController) ((NinjaWebView) currentAlbumController).findAllAsync(s.toString()); }
        });
    }
    public void initSearch() {

        final View scrollDividerBottom = dialogViewSearch.findViewById(R.id.scrollDividerBottom);
        final View scrollDividerTop = dialogViewSearch.findViewById(R.id.scrollDividerTop);
        assert null != scrollDividerBottom;
        assert null != scrollDividerTop;
        HelperUtils.setupDialogDividers(list_search, scrollDividerTop, scrollDividerBottom);

        final RecordAction action = new RecordAction(this);
        final List<Record> list = action.listEntries(activity);
        adapterSearch = new AdapterSearch(this, R.layout.item_suggestion, list);

        // 1. The list hangs below the input field: first entry on top, no auto-scroll to the end.
        list_search.setStackFromBottom(false);
        list_search.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);

        list_search.setAdapter(adapterSearch);
        list_search.setTextFilterEnabled(true);

        // 2. Klick-Listener optimiert (Daten direkt aus dem Adapter lesen)
        list_search.setOnItemClickListener((parent, view, position, id) -> {
            hideSearch();
            final AdapterSearch.CompleteItem item = (AdapterSearch.CompleteItem) adapterSearch.getItem(position);
            ninjaWebView.loadUrl(item.getUrl()); // Nutzt die URL direkt aus dem Objekt statt aus der View
        });

        list_search.setOnItemLongClickListener((adapterView, view, position, id) -> {
            final AdapterSearch.CompleteItem item = (AdapterSearch.CompleteItem) adapterSearch.getItem(position);
            showOverflow(null, list_search, 2, item.getTitle(), item.getUrl(), null, null, 0);
            return true;
        });
    }
    
    private void showOverview() {
        hideSearch();
        moveInactiveTabs();
        tabSwitcher.show();
        updateIncognitoLock();
    }

    /** Whether the tab switcher is open. */
    boolean isTabSwitcherOpen() {
        return null != tabSwitcher && tabSwitcher.isShowing();
    }

    private final TabSwitcher.Host tabSwitcherHost = new TabSwitcher.Host() {
        @NonNull
        @Override
        public List<NinjaWebView> tabs() {
            final List<NinjaWebView> tabs = new ArrayList<>();
            for (final AlbumController controller : BrowserContainer.list()) {
                if (controller instanceof NinjaWebView tab) tabs.add(tab);
            }
            return tabs;
        }

        @Override
        public NinjaWebView currentTab() {
            return currentAlbumController instanceof NinjaWebView tab ? tab : null;
        }

        @Override
        public void selectTab(@NonNull final NinjaWebView tab) {
            if (tab != currentAlbumController) showAlbum(tab);
        }

        @Override
        public void closeTab(@NonNull final NinjaWebView tab) {
            if (1 >= BrowserContainer.size()) {
                // There is always one tab: closing the last one leaves a fresh one.
                tab.setThumbnail(null);
                tab.loadUrl(HomePage.startUrl(sp));
                updateOmniBox();
            } else {
                removeAlbum(tab);
            }
            tabSwitcher.refresh();
        }

        @Override
        public void newTab(final boolean privateTab) {
            if (privateTab) addPrivateAlbum();
            else addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
        }

        @Override
        public void closeAllTabs(final boolean privateTabs) {
            BrowserActivity.this.closeAllTabs(privateTabs);
        }

        @Override
        public void openSettings() {
            startActivity(new Intent(BrowserActivity.this, Settings_Activity.class));
        }

        @Override
        public void openTab(@NonNull final String url) {
            addAlbum(getString(R.string.app_name), url, true);
            hideOverview();
        }

        @Override
        public void onModeChanged() {
            updateIncognitoLock();
        }
    };

    /**
     * Closes all normal tabs, or all private ones. A tab of the other kind
     * comes to the front; if none is left, a new tab is opened.
     */
    void closeAllTabs(final boolean privateTabs) {
        final List<AlbumController> closing = new ArrayList<>();
        AlbumController remaining = null;
        for (final AlbumController controller : BrowserContainer.list()) {
            if (controller instanceof NinjaWebView tab && tab.isPrivateTab() == privateTabs) closing.add(controller);
            else remaining = controller;
        }
        if (closing.isEmpty()) return;
        final boolean currentCloses = closing.contains(currentAlbumController);
        if (null == remaining) {
            // The new tab has to exist before the old ones go: there is always one tab.
            setWebView(getString(R.string.app_name), HomePage.startUrl(sp), true);
        } else if (currentCloses) {
            showAlbum(remaining);
        }
        for (final AlbumController controller : closing) {
            tab_container.removeView(controller.getAlbumView());
            BrowserContainer.remove(controller);
        }
        // The data of private tabs goes with the last of them.
        if (privateTabs) PrivateTabs.wipe();
        updateOmniBox();
        tabSwitcher.refresh();
        updateIncognitoLock();
    }

    /** Opens the overview of the open tabs. */
    private void showOverviewPage(final int pageId) {
        showOverview();
    }

    /** Tries the page again when the connection is back, as Chrome does for its offline page. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull final Network network) {
            runOnUiThread(BrowserActivity.this::reloadOfflineTab);
        }
    };
    private boolean networkCallbackRegistered;

    private void registerNetworkCallback() {
        final ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (null == manager || networkCallbackRegistered) return;
        try {
            manager.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
        } catch (final RuntimeException e) {
            // Without the callback the user reloads by hand.
            Log.w(TAG, "No network callback: " + e);
        }
    }

    private void unregisterNetworkCallback() {
        final ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (null == manager || !networkCallbackRegistered) return;
        networkCallbackRegistered = false;
        try {
            manager.unregisterNetworkCallback(networkCallback);
        } catch (final RuntimeException e) {
            Log.w(TAG, "Network callback was not registered: " + e);
        }
    }

    /** Reloads the tab in front if it shows the offline notice. Other tabs retry when they are opened. */
    void reloadOfflineTab() {
        if (null != ninjaWebView && ninjaWebView.isShowingOfflinePage() && !OfflinePage.isOffline(this)) {
            ninjaWebView.reload();
        }
    }

    /** Whether the current tab shows a website that can be shared. */
    private boolean canShareCurrentPage() {
        return null != ninjaWebView && null != ninjaWebView.getUrl() && !ninjaWebView.isShowingHomePage();
    }

    private void shareCurrentPage() {
        if (canShareCurrentPage()) shareLink(ninjaWebView.getTitle(), ninjaWebView.getUrl());
    }

    /** The menu behind the three-dot button of the top bar. */
    void showMainMenu() {
        showMainMenu(buttonMenu);
    }

    /** Opens the menu at {@code anchor}: the button of the top bar, or the one of the home page. */
    void showMainMenu(final View anchor) {
        if (null != mainMenu) mainMenu.dismiss();
        final MainMenuSheet popup = new MainMenuSheet(anchor, R.menu.menu_main, id -> {
            onMainMenuItem(id);
            return kotlin.Unit.INSTANCE;
        });
        popup.getMenu().findItem(R.id.main_share).setEnabled(canShareCurrentPage());
        popup.getMenu().findItem(R.id.main_desktop_site).setChecked(null != ninjaWebView && ninjaWebView.isDesktopSite());
        // Settings > Accessibility > "Show zoom option in main menu". Only websites have a zoom of their own.
        final android.view.MenuItem zoom = popup.getMenu().findItem(R.id.main_zoom);
        zoom.setVisible(sp.getBoolean(PageZoom.KEY_ALWAYS_SHOW, false));
        zoom.setEnabled(null != ninjaWebView && ReaderMode.isCandidate(ninjaWebView.getUrl())
                && !ninjaWebView.isShowingOwnPage() && !ninjaWebView.isShowingHomePage());
        mainMenu = popup;
        popup.show();
    }

    private void onMainMenuItem(final int id) {
        if (id == R.id.main_new_tab) {
            addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
        } else if (id == R.id.main_new_private_tab) {
            addPrivateAlbum();
        } else if (id == R.id.main_history) {
            libraryLauncher.launch(new Intent(this, HistoryActivity.class));
        } else if (id == R.id.main_downloads) {
            startActivity(new Intent(this, DownloadsActivity.class));
        } else if (id == R.id.main_bookmarks) {
            libraryLauncher.launch(new Intent(this, BookmarksActivity.class));
        } else if (id == R.id.main_share) {
            shareCurrentPage();
        } else if (id == R.id.main_zoom) {
            PageHooks.showZoomSheet(ninjaWebView);
        } else if (id == R.id.main_desktop_site) {
            ninjaWebView.setDesktopSite(!ninjaWebView.isDesktopSite());
        } else if (id == R.id.main_settings) {
            startActivity(new Intent(this, Settings_Activity.class));
        }
    }

    /**
     * The menu of a long press on a link or a picture in {@code tab}, as Chromium for
     * Android has it. Returns the open menu, or null if there is nothing to offer.
     */
    androidx.appcompat.app.AlertDialog showLinkContextMenu(final NinjaWebView tab, final LinkContextMenu.Target target) {
        return LinkContextMenu.show(this, tab, target, tab.isPrivateTab(), new LinkContextMenu.Host() {
            @Override
            public void openInNewTab(@NonNull final String url, final boolean privateTab) {
                // Behind the tab in front, as in Chrome; from a private tab the new one is private too.
                if (privateTab) setWebView(getString(R.string.private_tab), url, false, true);
                else addAlbum(HelperUtils.domain(url), url, false);
                updateOmniBox();
            }

            @Override
            public void openInIncognitoTab(@NonNull final String url) {
                setWebView(getString(R.string.private_tab), url, true, true);
            }

            @Override
            public void download(@NonNull final String url) {
                NinjaDownloadListener.saveAs(BrowserActivity.this, tab, url, null, null);
            }

            @Override
            public void shareLink(final String title, @NonNull final String url) {
                BrowserActivity.this.shareLink(null == title || title.trim().isEmpty() ? HelperUtils.domain(url) : title.trim(), url);
            }
        });
    }

    /** Opens a private tab on the home page and says what "private" covers on this device. */
    private void addPrivateAlbum() {
        setWebView(getString(R.string.private_tab), HomePage.startUrl(sp), true, true);
        final Snackbar snackbar = Snackbar.make(findViewById(R.id.main_content),
                ninjaWebView.isPrivateIsolated() ? R.string.private_tab_isolated : R.string.private_tab_shared,
                Snackbar.LENGTH_LONG);
        HelperUtils.makeSnackbarRound(snackbar);
        snackbar.show();
    }

    /** Whether any open tab is a private one. */
    private boolean hasPrivateTabs() {
        for (int i = 0; i < BrowserContainer.size(); i++) {
            if (BrowserContainer.get(i) instanceof NinjaWebView tab && tab.isPrivateTab()) return true;
        }
        return false;
    }

    /** Whether the address field is open for typing. */
    boolean isSearchOpen() {
        return null != dialogViewSearch && VISIBLE == dialogViewSearch.getVisibility();
    }

    public void hideSearch() {
        if (null != dialogViewSearch) dialogViewSearch.setVisibility(GONE);
        if (null != search_input) {
            search_input.clearFocus();
            final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (null != imm) imm.hideSoftInputFromWindow(search_input.getWindowToken(), 0);
        }
        try {
            dialogCustomSearches.cancel();} catch (final Exception e) {Log.i(TAG, "dialogCustomSearches:" + e);}
    }

    public void hideOverview() {
        if (null != tabSwitcher) tabSwitcher.hide();
        dialogOverview.cancel();
    }

    private void setSelectedTab() {
        if (overViewTab.equals(getString(R.string.album_title_tab))) bottom_navigation.setSelectedItemId(R.id.page_0);
        else if (overViewTab.equals(getString(R.string.album_title_bookmarks)))
            bottom_navigation.setSelectedItemId(R.id.page_2);
        else if (overViewTab.equals(getString(R.string.album_title_history))) bottom_navigation.setSelectedItemId(R.id.page_3);
    }

    public void hideOverflow () {
        try {
            dialog_overflow.cancel();} catch (final Exception e) {Log.i(TAG, "Overflow already closed:" + e);}
    }
    /**
     * All actions the sheet of {@link #showOverflow} can offer, in display
     * order. Which of them appear depends on what the sheet was opened for.
     * The entries of the three-dot menu (new tab, downloads, settings, help)
     * are not part of it.
     */
    private List<MenuItem> actionItems() {
        final List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(R.string.menu_reload, R.drawable.icon_refresh));
        items.add(new MenuItem(R.string.menu_other_searchSite, R.drawable.icon_search_site));
        items.add(new MenuItem(R.string.main_menu_new_tabOpen, R.drawable.icon_tab_plus));
        items.add(new MenuItem(R.string.main_menu_new_tab, R.drawable.icon_tab_background));
        items.add(new MenuItem(R.string.menu_save_bookmark, R.drawable.icon_bookmark));
        items.add(new MenuItem(R.string.menu_fav, R.drawable.icon_fav_plus));
        items.add(new MenuItem(R.string.menu_share_link, R.drawable.icon_share));
        items.add(new MenuItem(R.string.menu_shareClipboard, R.drawable.icon_clipboard));
        items.add(new MenuItem(R.string.menu_shareOpenWith, R.drawable.icon_share_open_with));
        items.add(new MenuItem(R.string.dialog_postOnWebsite, R.drawable.icon_post));
        items.add(new MenuItem(R.string.menu_save_pdf, R.drawable.icon_file));
        items.add(new MenuItem(R.string.menu_save_as, R.drawable.icon_menu_save));
        items.add(new MenuItem(R.string.menu_sc, R.drawable.icon_home));
        items.add(new MenuItem(R.string.menu_edit, R.drawable.icon_edit));
        items.add(new MenuItem(R.string.menu_delete, R.drawable.icon_delete));
        items.add(new MenuItem(R.string.menu_delete_entry, R.drawable.icon_delete_alt));
        items.add(new MenuItem(R.string.menu_hide_bars, R.drawable.icon_show));
        items.add(new MenuItem(R.string.menu_closeTab, R.drawable.icon_tab_remove));
        items.add(new MenuItem(R.string.menu_restart, R.drawable.icon_restart));
        items.add(new MenuItem(R.string.menu_quit, R.drawable.icon_close));
        return items;
    }

    public void removeItemByIcon(final int targetIconResId, final List<MenuItem> selectedItemsList, final AdapterMenu adapter) {
        int indexToRemove = -1;
        // 1. Position des Elements anhand der Icon-ID im aktuellen Grid finden
        for (int i = 0; i < selectedItemsList.size(); i++) {
            if (selectedItemsList.get(i).getIconResId() == targetIconResId) {
                indexToRemove = i;
                break;
            }
        }
        // Wenn das Element im aktuellen Grid existiert
        if (-1 != indexToRemove) {
            // 2. Aus der Liste für die Anzeige entfernen
            selectedItemsList.remove(indexToRemove);
            // 3. Den Adapter über das Entfernen informieren (zeigt eine schöne Animation)
            if (null != adapter) {
                adapter.notifyItemRemoved(indexToRemove);
            }
        }
    }


    private void setupEditSubMenu(final String title, final String url, final List<Record> recordList, final int location, final RecordAction action) {
        final MaterialAlertDialogBuilder builderSubMenuEdit = new MaterialAlertDialogBuilder(context);
        final View dialogViewSubMenu = View.inflate(context, R.layout.dialog_edit, null);

        final TextInputLayout editBottomLayout = dialogViewSubMenu.findViewById(R.id.editBottomLayout);
        final TextInputLayout editTopLayout = dialogViewSubMenu.findViewById(R.id.editTopLayout);
        editBottomLayout.setHint(activity.getString(R.string.dialog_URL_hint));
        editTopLayout.setHint(activity.getString(R.string.dialog_title_hint));

        final EditText editTop = dialogViewSubMenu.findViewById(R.id.editTop);
        final EditText editBottom = dialogViewSubMenu.findViewById(R.id.editBottom);
        editTop.setText(title);
        editBottom.setText(url);

        final MaterialCardView ib_icon = dialogViewSubMenu.findViewById(R.id.editIcon);
        final boolean isBookmark = overViewTab.equals(getString(R.string.album_title_bookmarks));
        ib_icon.setVisibility(isBookmark ? VISIBLE : GONE);

        ib_icon.setOnClickListener(v -> {
            final MaterialAlertDialogBuilder builderFilter = new MaterialAlertDialogBuilder(context);
            final View dialogViewFilter = View.inflate(context, R.layout.dialog_menu, null);
            builderFilter.setView(dialogViewFilter);
            builderFilter.setTitle(R.string.setting_filter).setIcon(R.drawable.icon_filter);

            final AlertDialog dialogFilter = builderFilter.create();
            dialogFilter.show();
            HelperUtils.setupDialog(context, dialogFilter);
            dialogViewFilter.findViewById(R.id.item_CardViewItem).setVisibility(GONE);

            final GridView menuEditFilter = dialogViewFilter.findViewById(R.id.menu_grid);
            List<GridItem> menuEditFilterList = new LinkedList<>();
            sp.edit().putString("showFilterDialogX", "true").apply();
            HelperUtils.addFilterItems(activity, menuEditFilterList);

            final int spacing = context.getResources().getDimensionPixelSize(R.dimen.spacing_small);
            final AdapterGrid menuEditFilterAdapter = new AdapterGrid(context, menuEditFilterList);
            menuEditFilter.setNumColumns(2);
            menuEditFilter.setHorizontalSpacing(spacing);
            menuEditFilter.setVerticalSpacing(spacing);
            menuEditFilter.setAdapter(menuEditFilterAdapter);

            menuEditFilter.setOnItemClickListener((parent, view2, position, id) -> {
                newIcon = menuEditFilterList.get(position).data();
                HelperUtils.setFilterIcons(context, ib_icon, newIcon);
                dialogFilter.cancel();
            });

            final int spacing_large = context.getResources().getDimensionPixelSize(R.dimen.spacing_large);
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    WRAP_CONTENT,
                    WRAP_CONTENT
            );
            params.setMargins(spacing_large, spacing_large, spacing_large, spacing_large);
            menuEditFilter.setLayoutParams(params);
            dialogFilter.setOnCancelListener(dialogInterface -> sp.edit().putString("showFilterDialogX", "false").apply());
        });

        newIcon = recordList.get(location).getIconColor();
        HelperUtils.setFilterIcons(context, ib_icon, newIcon);

        builderSubMenuEdit.setPositiveButton(activity.getString(R.string.app_ok), (dialog2, which) -> {
            action.open(true);
            action.deleteURL(url, RecordUnit.TABLE_BOOKMARK);
            action.deleteURL(editBottom.getText().toString(), RecordUnit.TABLE_BOOKMARK);
            action.addBookmark(new Record(editTop.getText().toString(), editBottom.getText().toString(), 0, newIcon));
            updateOmniBox();
            CatToast.show(activity, R.string.app_done, 1);
            action.close();
            bottom_navigation.setSelectedItemId(R.id.page_2);
            dialog2.cancel();
        });

        builderSubMenuEdit.setNegativeButton(activity.getString(R.string.app_cancel), (dialog1, which) -> dialog1.cancel());
        builderSubMenuEdit.setView(dialogViewSubMenu);
        final Dialog dialogSubMenuEdit = builderSubMenuEdit.create();
        HelperUtils.setupCardView(activity, dialogViewSubMenu, title, url, ninjaWebView, null, dialogSubMenuEdit, true);
        HelperUtils.setupDialog(context, dialogSubMenuEdit);
        dialogSubMenuEdit.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void showOverflow(final Dialog dialog, final View view, final int hideMenu, final String title, final String url, AdapterRecord adapterRecord, final List<Record> recordList, final int location) {

        if (null != dialog_overflow) {
            dialog_overflow.cancel();
        }

        final View dialogView = View.inflate(context, R.layout.dialog_menu_overflow, null);
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setView(dialogView);
        dialog_overflow = builder.create();
        HelperUtils.setupCardView(HelperUtils.getActivity(context), dialogView, title, url, ninjaWebView, null, dialog_overflow, true);
        HelperUtils.setupDialog(context, dialog_overflow);
        FaviconHelper.setFavicon(context, dialogView, url, R.id.icon_start, R.drawable.icon_image_broken);
        dialog_overflow.show();

        final List<MenuItem> selectedItemsList = actionItems();

        final RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerViewGrid);
        final View scrollDividerTop = dialogView.findViewById(R.id.scrollDividerTop);
        final View scrollDividerBottom = dialogView.findViewById(R.id.scrollDividerBottom);
        assert null != scrollDividerTop;
        assert null != scrollDividerBottom;
        HelperUtils.setupDialogDividers(recyclerView, scrollDividerTop, scrollDividerBottom);

        final int orientation = getResources().getConfiguration().orientation;
        final int spanCount = (Configuration.ORIENTATION_LANDSCAPE == orientation) ? 2 : 1;
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));

        @SuppressLint("NonConstantResourceId") final AdapterMenu adapter = new AdapterMenu(selectedItemsList, item -> {
            final String favURL = HomePage.startUrl(sp);
            final RecordAction action = new RecordAction(context);
            dialog_overflow.cancel();
            final int resId = item.getIconResId();

            if (resId == R.drawable.icon_show) {
                topBar.setHidden(true);
            } else if (resId == R.drawable.icon_tab_plus) {
                if (2 == hideMenu) { hideSearch(); }
                if (Objects.equals(url, ninjaWebView.getUrl())) {
                    addAlbum(HelperUtils.domain(url), favURL, true);
                } else {
                    addAlbum(HelperUtils.domain(url), url, true);
                }
            } else if (resId == R.drawable.icon_tab_background) {
                if (Objects.equals(url, ninjaWebView.getUrl())) {
                    addAlbum(HelperUtils.domain(url), favURL, false);
                } else {
                    addAlbum(HelperUtils.domain(url), url, false);
                }
            } else if (resId == R.drawable.icon_refresh) {
                ninjaWebView.reload();
            } else if (resId == R.drawable.icon_tab_remove) {
                removeAlbum(currentAlbumController);
                if (2 > BrowserContainer.size()) {
                    hideOverview(); }
            } else if (resId == R.drawable.icon_close) {
                doubleTapsQuit();
            } else if (resId == R.drawable.icon_bookmark) {
                saveBookmark(title, url);
            } else if (resId == R.drawable.icon_file) {
                final PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
                final PrintDocumentAdapter printAdapter = ninjaWebView.createPrintDocumentAdapter(title);
                Objects.requireNonNull(printManager).print(title, printAdapter, new PrintAttributes.Builder().build());
                sp.edit().putBoolean("pdf_create", true).apply();
            } else if (resId == R.drawable.icon_menu_save) {
                NinjaDownloadListener.saveAs(activity, ninjaWebView, url, null, null);
            } else if (resId == R.drawable.icon_fav_plus) {
                sp.edit().putString("favoriteURL", url).apply();
                CatToast.show(context, R.string.app_done, 1);
            } else if (resId == R.drawable.icon_share) {
                shareLink(title, url);
            } else if (resId == R.drawable.icon_post) {
                postLink(title + ": " + url, dialog_overflow);
            } else if (resId == R.drawable.icon_clipboard) {
                copyLink(url);
            } else if (resId == R.drawable.icon_share_open_with) {
                final Intent shareIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(Intent.createChooser(shareIntent, null));
            } else if (resId == R.drawable.icon_home) {
                HelperUtils.createShortcut(context, title, url);
            } else if (resId == R.drawable.icon_search_site) {
                searchOnSite();
            } else if (resId == R.drawable.icon_restart) {            } else if (resId == R.drawable.icon_download) {
                startActivity(new Intent(this, DownloadsActivity.class));
            } else if (resId == R.drawable.icon_settings) {
                startActivity(new Intent(this, Settings_Activity.class));
            } else if (resId == R.drawable.icon_restart) {
                triggerRebirth(context);
            } else if (resId == R.drawable.icon_delete) {
                final Snackbar snackbarSearch = Snackbar.make(view, R.string.hint_database, Snackbar.LENGTH_SHORT);
                HelperUtils.makeSnackbarRound(snackbarSearch);
                snackbarSearch.setAction(context.getString(R.string.app_ok), v -> {
                    action.open(true);
                    action.deleteURL(url, RecordUnit.TABLE_BOOKMARK);
                    action.deleteURL(url, RecordUnit.TABLE_HISTORY);
                    action.close();
                    initSearch();
                    final String getText = Objects.requireNonNull(search_input.getText()).toString();
                    search_input.setText("");
                    search_input.setText(getText);
                    search_input.setSelection(getText.length());
                });
                snackbarSearch.show();
            } else if (resId == R.drawable.icon_delete_alt) {
                final Snackbar snackbarList = Snackbar.make(view, R.string.hint_database, Snackbar.LENGTH_SHORT);
                HelperUtils.makeSnackbarRound(snackbarList);
                snackbarList.setAction(context.getString(R.string.app_ok), v -> {
                    final Record record = recordList.get(location);
                    action.open(true);
                    if (overViewTab.equals(getString(R.string.album_title_bookmarks))) action.deleteURL(record.getUrl(), RecordUnit.TABLE_BOOKMARK);
                    else if (overViewTab.equals(getString(R.string.album_title_history))) action.deleteURL(record.getUrl(), RecordUnit.TABLE_HISTORY);
                    action.close();
                    recordList.remove(location);
                    adapterRecord.notifyDataSetChanged();
                    updateOmniBox();
                    dialog_overflow.cancel();
                });
                snackbarList.show();
            } else if (resId == R.drawable.icon_edit) {
                setupEditSubMenu(title, url, recordList, location, action);
            }
        });

        recyclerView.setAdapter(adapter);

        if (0 == hideMenu) {
            // Actions for the page in the current tab. New tabs and sharing
            // have their own buttons and menu entries.
            removeItemByIcon(R.drawable.icon_tab_plus, selectedItemsList, adapter);        // main_menu_new_tabOpen
            removeItemByIcon(R.drawable.icon_tab_background, selectedItemsList, adapter);  // main_menu_new_tab
            removeItemByIcon(R.drawable.icon_share, selectedItemsList, adapter);           // menu_share_link
        } else {
            // Actions for a link or a list entry, not for the current page.
            removeItemByIcon(R.drawable.icon_refresh, selectedItemsList, adapter);         // menu_reload
            removeItemByIcon(R.drawable.icon_restart, selectedItemsList, adapter);         // menu_restart
            removeItemByIcon(R.drawable.icon_close, selectedItemsList, adapter);           // menu_quit
            removeItemByIcon(R.drawable.icon_file, selectedItemsList, adapter);            // menu_save_pdf
            removeItemByIcon(R.drawable.icon_search_site, selectedItemsList, adapter);     // menu_other_searchSite
            removeItemByIcon(R.drawable.icon_show, selectedItemsList, adapter);            // menu_hide_bars
            removeItemByIcon(R.drawable.icon_tab_remove, selectedItemsList, adapter);      // menu_closeTab
        }

        // Spezifische Filterungen nach Icon-ID sortiert
        switch (hideMenu) {
            case 0: // Current page
            case 1: // Long click
                removeItemByIcon(R.drawable.icon_delete, selectedItemsList, adapter);      // menu_delete
                removeItemByIcon(R.drawable.icon_delete_alt, selectedItemsList, adapter);  // menu_delete_entry
                removeItemByIcon(R.drawable.icon_edit, selectedItemsList, adapter);        // menu_edit
                break;
            case 2: // List search
                removeItemByIcon(R.drawable.icon_edit, selectedItemsList, adapter);        // menu_edit
                removeItemByIcon(R.drawable.icon_delete_alt, selectedItemsList, adapter);  // menu_delete_entry
                break;
            case 3: // Bookmark
                removeItemByIcon(R.drawable.icon_delete, selectedItemsList, adapter);      // menu_delete
                removeItemByIcon(R.drawable.icon_bookmark, selectedItemsList, adapter);    // menu_save_bookmark
                break;
            case 4: // History
                removeItemByIcon(R.drawable.icon_delete, selectedItemsList, adapter);      // menu_delete
                removeItemByIcon(R.drawable.icon_edit, selectedItemsList, adapter);        // menu_edit
                break;
        }

    }


    private void setupProfileCheckbox(final CheckBox checkBox, final String suffix, final boolean defaultValue, final String profile, final String url, final FloatingActionButton buttonProfile) {
        final String finalKey = listStandard.isWhite(url) ? profile : NinjaWebView.getProfile();
        checkBox.setChecked(sp.getBoolean(finalKey + suffix, defaultValue));

        checkBox.setOnClickListener(v -> {
            if (!listStandard.isWhite(url) && "profileStandard".equals(NinjaWebView.getProfile())) {
                ninjaWebView.setProfileChanged();
                setProfileIcon(buttonProfile, url);
            }
            final String currentKey = listStandard.isWhite(url) ? profile : NinjaWebView.getProfile();
            sp.edit().putBoolean(currentKey + suffix, checkBox.isChecked()).apply();
        });
    }

    public void showDialogFastToggle(final String title, final String url, final FloatingActionButton floatingActionButton) {
        // 1. Frühzeitiger Abbruch bei ungültiger URL (Null-Safety)
        if (null == url) {
            CatToast.show(context, getString(R.string.app_error), 2);
            return;
        }

        listStandard = new List_standard(context);
        ninjaWebView = (NinjaWebView) currentAlbumController;
        final String profile = listStandard.isWhite(url) ? HelperUtils.domain(url) : sp.getString("profile", "profileStandard");
        final boolean isWhite = listStandard.isWhite(url);

        // 2. Dialog initialisieren und anzeigen
        final View dialogView = View.inflate(context, R.layout.dialog_fast_toggle, null);

        final View scrollDividerTop = dialogView.findViewById(R.id.scrollDividerTop);
        final View scrollDividerBottom = dialogView.findViewById(R.id.scrollDividerBottom);
        final NestedScrollView scrollView = dialogView.findViewById(R.id.scrollView);
        assert null != scrollView;
        assert null != scrollDividerTop;
        assert null != scrollDividerBottom;
        HelperUtils.setupDialogDividers(scrollView, scrollDividerTop, scrollDividerBottom);

        final MaterialAlertDialogBuilder builderFastToggle = new MaterialAlertDialogBuilder(context);
        builderFastToggle.setView(dialogView);
        final AlertDialog dialogFastToggle = builderFastToggle.create();dialogFastToggle.setOnDismissListener(dialogInterface -> setProfileIcon(floatingActionButton, url));
        dialogFastToggle.show();

        // 3. UI-Elemente zuweisen & Basis-Sichtbarkeiten
        final FloatingActionButton buttonProfile = dialogView.findViewById(R.id.buttonProfile);
        final Button ib_save = dialogView.findViewById(R.id.ib_save);
        final Button ib_delete = dialogView.findViewById(R.id.ib_delete);
        final ImageView icon_standard = dialogView.findViewById(R.id.icon_standard);
        final RelativeLayout checkbox_reset = dialogView.findViewById(R.id.checkbox_reset);

        HelperUtils.setupCardView(HelperUtils.getActivity(context), dialogView, title, url, ninjaWebView, null, dialogFastToggle, false);
        HelperUtils.setupDialog(context, dialogFastToggle);

        ib_save.setVisibility(isWhite ? GONE : VISIBLE);
        ib_delete.setVisibility(isWhite ? VISIBLE : GONE);

        // 4. Standard-Icon-Status ermitteln
        int iconRes = R.drawable.icon_close;
        if (sp.getBoolean("sp_standard_restart", true)) iconRes = R.drawable.icon_restart;
        else if (sp.getBoolean("sp_standard_always", true)) iconRes = R.drawable.icon_check;
        icon_standard.setImageResource(iconRes);

        // 5. Standard-Reset PopupMenü
        checkbox_reset.setOnClickListener(v -> {
            final PopupMenu popupMenu = new PopupMenu(context, checkbox_reset);
            popupMenu.getMenuInflater().inflate(R.menu.menu_standard, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                boolean always = false, restart = false;
                int res = R.drawable.icon_close;

                if (menuItem.getItemId() == R.id.menu_standardAlways) {
                    always = true;
                    res = R.drawable.icon_check;
                } else if (menuItem.getItemId() == R.id.menu_standardRestart) {
                    restart = true;
                    res = R.drawable.icon_restart;
                }

                sp.edit().putBoolean("sp_standard_always", always).putBoolean("sp_standard_restart", restart).apply();
                icon_standard.setImageResource(res);
                return true;
            });
            popupMenu.show();
        });

        // 6. Weiterleitungen & ScreenOn & Links
        dialogView.findViewById(R.id.item_checkBox).setOnClickListener(v ->
                new CustomRedirectsDialog().show(getSupportFragmentManager(), "redirect"));

        final CheckBox checkbox_screenOn = dialogView.findViewById(R.id.checkbox_screenOn);
        checkbox_screenOn.setChecked(sp.getBoolean("sp_screenOn", false));
        checkbox_screenOn.setOnClickListener(v -> {
            sp.edit().putBoolean("sp_screenOn", checkbox_screenOn.isChecked()).apply();
            dialogFastToggle.cancel();
            triggerRebirth(context);
        });

        final CheckBox checkbox_links = dialogView.findViewById(R.id.checkbox_links);
        checkbox_links.setChecked(sp.getBoolean("sp_tabBackground", false));
        checkbox_links.setOnClickListener(v -> sp.edit().putBoolean("sp_tabBackground", checkbox_links.isChecked()).apply());

        final TextView titleViewSettings = dialogView.findViewById(R.id.titleViewSettings);
        titleViewSettings.setText(String.format("%s %s", context.getString(R.string.app_name), context.getString(R.string.setting_label)));

        // 7. Alle Profil-Checkboxen (Zuweisung & Initialisierung via Hilfsmethode)
        final CheckBox checkbox_image = dialogView.findViewById(R.id.checkbox_image);
        final CheckBox checkbox_adBlock = dialogView.findViewById(R.id.checkbox_adBlock);
        final CheckBox checkbox_trackingURL = dialogView.findViewById(R.id.checkbox_trackingURL);
        final CheckBox checkbox_fingerPrint = dialogView.findViewById(R.id.checkbox_fingerPrint);
        final CheckBox checkbox_cookies = dialogView.findViewById(R.id.checkbox_cookies);
        final CheckBox checkbox_cookiesThirdParty = dialogView.findViewById(R.id.checkbox_cookiesThirdParty);
        final CheckBox checkbox_cookiesBanner = dialogView.findViewById(R.id.checkbox_cookiesBanner);
        final CheckBox checkbox_java = dialogView.findViewById(R.id.checkbox_java);
        final CheckBox checkbox_javaPopUp = dialogView.findViewById(R.id.checkbox_javaPopUp);
        final CheckBox checkbox_history = dialogView.findViewById(R.id.checkbox_history);
        final CheckBox checkbox_camera = dialogView.findViewById(R.id.checkbox_camera);
        final CheckBox checkbox_mic = dialogView.findViewById(R.id.checkbox_mic);
        final CheckBox checkbox_dom = dialogView.findViewById(R.id.checkbox_dom);
        final CheckBox checkbox_desktop = dialogView.findViewById(R.id.checkbox_desktop);
        final CheckBox checkbox_nightView = dialogView.findViewById(R.id.checkbox_nightView);
        final CheckBox checkbox_saveData = dialogView.findViewById(R.id.checkbox_saveData);
        final CheckBox checkbox_location = dialogView.findViewById(R.id.checkbox_location);

        setupProfileCheckbox(checkbox_image, "_images", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_java, "_javascript", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_javaPopUp, "_javascriptPopUp", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_cookies, "_cookies", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_cookiesThirdParty, "_cookiesThirdParty", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_location, "_location", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_mic, "_microphone", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_camera, "_camera", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_dom, "_dom", false, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_desktop, "_desktop", false, profile, url, buttonProfile);

        setupProfileCheckbox(checkbox_cookiesBanner, "_deny_cookie_banners", true, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_fingerPrint, "_fingerPrintProtection", true, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_adBlock, "_adBlock", true, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_trackingURL, "_trackingULS", true, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_saveData, "_saveData", true, profile, url, buttonProfile);
        setupProfileCheckbox(checkbox_history, "_saveHistory", true, profile, url, buttonProfile);
        setupProfileCheckbox(dialogView.findViewById(R.id.checkbox_drm), "_drm", true, profile, url, buttonProfile);

        // 8. Night Mode Layout Logik
        final RelativeLayout layout_nightView = dialogView.findViewById(R.id.layout_nightView);
        final int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        layout_nightView.setVisibility((Configuration.UI_MODE_NIGHT_YES == nightModeFlags) && !"2".equals(sp.getString("sp_theme", "1")) ? VISIBLE : GONE);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            setupProfileCheckbox(checkbox_nightView, "_night", true, profile, url, buttonProfile);
        }

        // 9. Speicher-Button (ib_save)
        ib_save.setOnClickListener(v -> {
            final String domain = HelperUtils.domain(url);
            listStandard.removeDomain(domain);
            listStandard.addDomain(domain);

            sp.edit()
                    .putBoolean(domain + "_saveData", checkbox_saveData.isChecked())
                    .putBoolean(domain + "_images", checkbox_image.isChecked())
                    .putBoolean(domain + "_adBlock", checkbox_adBlock.isChecked())
                    .putBoolean(domain + "_trackingULS", checkbox_trackingURL.isChecked())
                    .putBoolean(domain + "_location", checkbox_location.isChecked())
                    .putBoolean(domain + "_fingerPrintProtection", checkbox_fingerPrint.isChecked())
                    .putBoolean(domain + "_cookies", checkbox_cookies.isChecked())
                    .putBoolean(domain + "_cookiesThirdParty", checkbox_cookiesThirdParty.isChecked())
                    .putBoolean(domain + "_deny_cookie_banners", checkbox_cookiesBanner.isChecked())
                    .putBoolean(domain + "_javascript", checkbox_java.isChecked())
                    .putBoolean(domain + "_javascriptPopUp", checkbox_javaPopUp.isChecked())
                    .putBoolean(domain + "_saveHistory", checkbox_history.isChecked())
                    .putBoolean(domain + "_camera", checkbox_camera.isChecked())
                    .putBoolean(domain + "_microphone", checkbox_mic.isChecked())
                    .putBoolean(domain + "_dom", checkbox_dom.isChecked())
                    .putBoolean(domain + "_night", checkbox_nightView.isChecked())
                    .putBoolean(domain + "_desktop", checkbox_desktop.isChecked())
                    .apply();

            if (sp.getBoolean("sp_standard_always", true)) {
                sp.edit().putString("profile", "profileStandard").apply();
            }
            setProfileIcon(buttonProfile, url);
            dialogFastToggle.cancel();
            ninjaWebView.reload();
        });

        // 10. Löschen-Button (ib_delete)
        ib_delete.setOnClickListener(view -> {
            final String domain = HelperUtils.domain(url);
            listStandard.removeDomain(domain);

            sp.edit()
                    .remove(domain + "_saveData").remove(domain + "_images").remove(domain + "_adBlock")
                    .remove(domain + "_trackingULS").remove(domain + "_location").remove(domain + "_fingerPrintProtection")
                    .remove(domain + "_cookies").remove(domain + "_cookiesThirdParty").remove(domain + "_deny_cookie_banners")
                    .remove(domain + "_javascript").remove(domain + "_javascriptPopUp").remove(domain + "_saveHistory")
                    .remove(domain + "_camera").remove(domain + "_microphone").remove(domain + "_dom")
                    .remove(domain + "_night").remove(domain + "_desktop")
                    .apply();

            if (sp.getBoolean("sp_standard_always", true)) {
                sp.edit().putString("profile", "profileStandard").apply();
            }
            setProfileIcon(buttonProfile, url);
            dialogFastToggle.cancel();
            ninjaWebView.reload();
        });

        // 11. Systemtasten (Reload, Settings, Help)
        dialogView.findViewById(R.id.ib_reload).setOnClickListener(view -> {
            if (null != ninjaWebView) { dialogFastToggle.cancel();
                ninjaWebView.reload(); }
        });

        dialogView.findViewById(R.id.ib_settings).setOnClickListener(view -> {
            if (null != ninjaWebView) {
                dialogFastToggle.cancel();
                startActivity(new Intent(this, Settings_Activity.class));
            }
        });

        dialogView.findViewById(R.id.button_help).setOnClickListener(view -> {
            dialogFastToggle.cancel();
            BrowserUnit.intentURL(this, Uri.parse("https://codeberg.org/Gaukler_Faun/FOSS_Browser/wiki/Settings-Dialog.-"));
        });

        // 12. Tiramisu Benachrichtigungs-Berechtigung
        if (TIRAMISU <= SDK_INT && sp.getBoolean("sp_tabBackground", false)) {
            if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                HelperUtils.showCustomMaterialDialogWithTwoActions(
                        context,
                        R.drawable.icon_alert,
                        getString(R.string.dialog_backGround),
                        getString(R.string.app_permission),
                        "",

                        // Mehrzeilige Funktion für den positiven Button (OK)
                        () -> {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1234567);
                            return true; // Schließt den Dialog nach dem Klick
                        },

                        () -> true // Schließt den Dialog einfach
                );

            }
        }

    }


    public void setProfileIcon (final FloatingActionButton floatingActionButton, final String url) {
        final String profile = sp.getString("profile", "profileStandard");
        final TypedValue typedValue = new TypedValue();
        final Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(androidx.appcompat.R.attr.colorError, typedValue, true);
        final int color = typedValue.data;
        // The top bar no longer has a shield; only a button inside a dialog shows the profile.
        if (null == floatingActionButton) return;
        floatingActionButton.setImageResource("profileStandard".equals(profile)
                ? R.drawable.icon_profile_standard : R.drawable.icon_profile_changed);
        listStandard = new List_standard(context);
        if (listStandard.isWhite(url)) {
            floatingActionButton.getDrawable().mutate().setTint(color);
        }
    }

    private void showDialogFilter() {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        final View dialogView = View.inflate(context, R.layout.dialog_menu, null);
        builder.setTitle(R.string.setting_filter);
        builder.setIcon(R.drawable.icon_filter);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        dialog.show();
        HelperUtils.setupDialog(context, dialog);
        final CardView cardView = dialogView.findViewById(R.id.item_CardViewItem);
        cardView.setVisibility(GONE);

        final GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
        List<GridItem> gridList = new LinkedList<>();
        sp.edit().putString("showFilterDialogX", "true").apply();
        HelperUtils.addFilterItems(activity, gridList);

        final int spacing = context.getResources().getDimensionPixelSize(R.dimen.spacing_small);
        final AdapterGrid gridAdapter = new AdapterGrid(context, gridList);
        menu_grid.setNumColumns(2);
        menu_grid.setHorizontalSpacing(spacing);
        menu_grid.setVerticalSpacing(spacing);
        menu_grid.setAdapter(gridAdapter);

        if (menu_grid.getLayoutParams() instanceof final ViewGroup.MarginLayoutParams p) {
            p.setMargins(56, 56, 56, 56);
            menu_grid.requestLayout();
        }

        gridAdapter.notifyDataSetChanged();
        menu_grid.setOnItemClickListener((parent, view, position, id) -> {
            filter = true;
            filterBy = gridList.get(position).data();
            dialog.cancel();
            bottom_navigation.setSelectedItemId(R.id.page_2);
        });
        dialog.setOnCancelListener(dialogInterface -> sp.edit().putString("showFilterDialogX", "false").apply());
    }

    // ==========================================
    // DIALOG: CUSTOM-SUCHE ANZEIGEN & ERSTELLEN
    // ==========================================
    private void showDialogCustomSearches(final String url) {
        search_input.clearFocus();

        if (null != dialogOverview && dialogOverview.isShowing()) {
            dialogOverview.cancel();
        }
        // 1. Haupt-Dialog & View initialisieren
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        final View dialogView = View.inflate(context, R.layout.custom_redirects_list, null);
        final RecyclerView recyclerView = dialogView.findViewById(R.id.redirects_recycler);
        // Konfiguration: Liste startet von unten befüllt und nach unten gescrollt
        final LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        // 2. Custom-Redirects aus den SharedPreferences laden
        ArrayList<CustomItemsUtils.CustomItem> redirects = new ArrayList<>();
        try {
            redirects = CustomItemsUtils.getSearches(sp);
        } catch (final JSONException e) {
            Log.e("Searches parsing", e.toString());
        }
        final AdapterCustomList adapter = new AdapterCustomList(context, url, redirects, list -> {
            try {
                CustomItemsUtils.saveSearches(list);
            } catch (final org.json.JSONException e) {
                android.util.Log.e("CustomSearches", "Fehler beim Speichern", e);
            }
        });
        recyclerView.setAdapter(adapter);
        // 3. Text in die TextView im Layout setzen (statt builder.setMessage)
        final TextView messageTextView = dialogView.findViewById(R.id.dialog_message_text);
        messageTextView.setText(url);
        final View spacer = dialogView.findViewById(R.id.spacer);
        if (null != spacer) {
            spacer.setVisibility(GONE);
        }
        // 5. Aktion: Neuen Custom-Suchanbieter hinzufügen (Positive Button)
        builder.setPositiveButton(R.string.create_new, (d, w) -> {
            final MaterialAlertDialogBuilder builderAddCustom = new MaterialAlertDialogBuilder(context);
            final View dialogViewAddCustom = View.inflate(context, R.layout.create_new_redirect, null);

            final TextInputEditText source = dialogViewAddCustom.findViewById(R.id.source);
            final TextInputEditText target = dialogViewAddCustom.findViewById(R.id.target);

            final TextInputLayout sourceLayout = dialogViewAddCustom.findViewById(R.id.sourceLayout);
            final TextInputLayout targetLayout = dialogViewAddCustom.findViewById(R.id.targetLayout);
            if (null != sourceLayout) sourceLayout.setHint(getString(R.string.dialog_title_hint));
            if (null != targetLayout) targetLayout.setHint(getString(R.string.dialog_URL_hint));

            builderAddCustom.setTitle(R.string.custom_searches_title);
            builderAddCustom.setIcon(R.drawable.icon_search);
            builderAddCustom.setView(dialogViewAddCustom);
            builderAddCustom.setNegativeButton(R.string.app_cancel, null);

            builderAddCustom.setPositiveButton(R.string.app_ok, (d2, w2) -> {


                final String sourceText = Objects.requireNonNull(source.getText()).toString().trim();
                final String targetText = Objects.requireNonNull(target.getText()).toString().trim();

                if (sourceText.isEmpty() || targetText.isEmpty()) return;
                // Element zum Adapter hinzufügen
                adapter.addRedirect(new CustomItemsUtils.CustomItem(sourceText, targetText));


                adapter.triggerSave();
            });

            final AlertDialog dialogCustomSearchesNew = builderAddCustom.create();
            dialogCustomSearchesNew.show();
            HelperUtils.setupDialog(context, dialogCustomSearchesNew);
        });
        // 6. Aktion: Abbrechen (Negative Button)
        builder.setNegativeButton(R.string.app_cancel, (d, w) -> {
            if (null != dialogCustomSearches) dialogCustomSearches.cancel();
        });
        // Metadaten für den Hauptdialog festlegen
        builder.setTitle(R.string.custom_searches_title);
        builder.setIcon(R.drawable.icon_search);
        builder.setView(dialogView); // Setzt unser custom XML-Layout
        builder.setCancelable(false);
        // 7. Divider initialisieren (Korrekt aus dialogView ausgelesen)
        final View scrollDividerBottom = dialogView.findViewById(R.id.scrollDividerBottom);
        final View scrollDividerTop = dialogView.findViewById(R.id.scrollDividerTop);
        if (null != scrollDividerBottom && null != scrollDividerTop) {
            HelperUtils.setupDialogDividers(recyclerView, scrollDividerTop, scrollDividerBottom);
        }
        // 8. Haupt-Dialog fertigstellen, einblenden und konfigurieren
        dialogCustomSearches = builder.create();
        dialogCustomSearches.show();
        HelperUtils.setupDialog(context, dialogCustomSearches);
    }

    /** Leaves the app. The open tabs are kept and come back with the next start. */
    private void doubleTapsQuit() {
        finishAndRemoveTask();
    }

    private void saveOpenedTabs() {
        final ArrayList<String> openTabs = new ArrayList<>();
        final List<kotlin.Pair<String, String>> used = new ArrayList<>();
        String front = null;
        for (int i = 0; i < BrowserContainer.size(); i++) {
            final NinjaWebView tab = (NinjaWebView) Objects.requireNonNull(BrowserContainer.get(i));
            // Private tabs are not restored, so they are not written down.
            if (tab.isPrivateTab()) continue;
            if (currentAlbumController == tab) {
                openTabs.add(0, tab.getUrl());
                front = String.valueOf(tab.getUrl());
            } else {
                openTabs.add(tab.getUrl());
            }
            used.add(new kotlin.Pair<>(String.valueOf(tab.getUrl()), null == tab.getTitle() ? "" : tab.getTitle()));
        }
        sp.edit().putString("openTabs", TextUtils.join("‚‗‚", openTabs)).apply();
        // For the inactive section: when each tab was last in front.
        InactiveTabs.record(sp, used, front, System.currentTimeMillis());
    }
    private void setCustomFullscreen(final boolean fullscreen) {
        final Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, !fullscreen);
        final WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (fullscreen) {
            insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars());
        }
    }
    private void copyLink(final String url) {
        final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText("text", url);
        Objects.requireNonNull(clipboard).setPrimaryClip(clip);
        CatToast.show(context, getString(R.string.app_done), 1);
    }

    public void shareLink(final String title, final String url) {

        hideOverview();
        final List_standard listStandard = new List_standard(context);
        String profile = sp.getString("profile", "profileStandard");
        if (listStandard.isWhite(url)) profile = HelperUtils.domain(url);

        final boolean removeTracking = sp.getBoolean(profile + "_trackingULS", true);

        if (removeTracking && url.contains("?") && url.contains("/")) {

            final String lastIndex = url.substring(url.lastIndexOf("/"));
            final String tracking = url.substring(url.lastIndexOf("?"));
            final String urlClean = url.replace(tracking, "");
            String m = context.getString(R.string.dialog_tracking) + " \"" + tracking + "\"" + "?";
            if (150 < m.length()) {
                m = m.substring(0, 150) + " [...]?\"";
            }

            if (lastIndex.contains(tracking)) {

                final GridItem item_01 = new GridItem(context.getString(R.string.app_ok), R.drawable.icon_check);
                final GridItem item_02 = new GridItem(context.getString(R.string.app_no), R.drawable.icon_close);
                final GridItem item_03 = new GridItem(context.getString(R.string.menu_edit), R.drawable.icon_edit);

                final MaterialAlertDialogBuilder builderTrack = new MaterialAlertDialogBuilder(context);
                final View dialogView = View.inflate(activity, R.layout.dialog_menu, null);
                builderTrack.setPositiveButton(context.getString(R.string.app_cancel), (dialog, which) -> dialog.cancel());
                builderTrack.setView(dialogView);
                final AlertDialog dialogTrack = builderTrack.create();
                HelperUtils.setupDialog(context, dialogTrack);
                HelperUtils.setupCardView(activity, dialogView, title, url, ninjaWebView, m, dialogTrack, true);
                dialogTrack.show();

                final GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
                List<GridItem> gridList = new LinkedList<>();
                gridList.add(gridList.size(), item_01);
                gridList.add(gridList.size(), item_02);
                gridList.add(gridList.size(), item_03);
                final AdapterGrid gridAdapter = new AdapterGrid(context, gridList);
                menu_grid.setAdapter(gridAdapter);
                gridAdapter.notifyDataSetChanged();
                menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                    switch (position) {

                        case 0:
                            dialogTrack.cancel();
                            final Intent sharingIntentClean;
                            sharingIntentClean = new Intent(Intent.ACTION_SEND);
                            sharingIntentClean.setType("text/plain");
                            sharingIntentClean.putExtra(Intent.EXTRA_SUBJECT, title);
                            sharingIntentClean.putExtra(Intent.EXTRA_TEXT, urlClean);
                            context.startActivity(Intent.createChooser(sharingIntentClean, (context.getString(R.string.menu_share_link))));
                            break;
                        case 1:
                            dialogTrack.cancel();
                            final Intent sharingIntent;
                            sharingIntent = new Intent(Intent.ACTION_SEND);
                            sharingIntent.setType("text/plain");
                            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                            sharingIntent.putExtra(Intent.EXTRA_TEXT, url);
                            context.startActivity(Intent.createChooser(sharingIntent, (context.getString(R.string.menu_share_link))));
                            break;
                        case 2:
                            dialogTrack.cancel();
                            final View dialogEdit = View.inflate(context, R.layout.dialog_edit, null);

                            String u = context.getString(R.string.dialog_tracking) + " \"" + tracking + "\"" + "?";

                            if (150 < u.length()) {
                                u = u.substring(0, 150) + " [...]?\"";
                            }

                            final TextInputLayout editBottomLayout = dialogEdit.findViewById(R.id.editBottomLayout);
                            final TextInputLayout editTopLayout = dialogEdit.findViewById(R.id.editTopLayout);
                            editBottomLayout.setHint(activity.getString(R.string.dialog_URL_hint));
                            editTopLayout.setVisibility(GONE);
                            final EditText input = dialogEdit.findViewById(R.id.editBottom);
                            input.setText(url);
                            HelperUtils.showSoftKeyboard(input);

                            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                            builder.setPositiveButton(activity.getString(R.string.app_ok), (dialog, which) -> {
                                dialog.dismiss();
                                final String newValue = Objects.requireNonNull(input.getText()).toString();
                                final Intent sharingIntentEdit;
                                sharingIntentEdit = new Intent(Intent.ACTION_SEND);
                                sharingIntentEdit.setType("text/plain");
                                sharingIntentEdit.putExtra(Intent.EXTRA_SUBJECT, title);
                                sharingIntentEdit.putExtra(Intent.EXTRA_TEXT, newValue);
                                context.startActivity(Intent.createChooser(sharingIntentEdit, (context.getString(R.string.menu_share_link))));
                            });
                            builder.setNegativeButton(activity.getString(R.string.app_cancel), (dialog1, which) -> dialog1.cancel());
                            builder.setView(dialogEdit);
                            final Dialog dialog = builder.create();
                            HelperUtils.setupCardView(activity, dialogEdit, title, url, ninjaWebView, u, dialog, true);
                            HelperUtils.setupDialog(context, dialog);
                            dialog.show();
                            break;
                    }
                });
            }
        } else {
            final Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            sharingIntent.putExtra(Intent.EXTRA_TEXT, url);
            context.startActivity(Intent.createChooser(sharingIntent, (context.getString(R.string.menu_share_link))));
        }
    }

    private void postLink(final String data, final Dialog dialogParent) {
        // The address is asked for every time, filled in with the one used last. That is
        // also the place to change it; there is no separate setting.
        final String urlForPosting = sp.getString("urlForPosting", "");
        {
            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            final View dialogViewSubMenu = View.inflate(context, R.layout.dialog_edit_text, null);
            final TextInputLayout textInputLayout = dialogViewSubMenu.findViewById(R.id.textInputLayout);
            textInputLayout.setHint(activity.getString(R.string.dialog_URL_hint));

            ((EditText) dialogViewSubMenu.findViewById(R.id.textInput)).setText(urlForPosting);
            builder.setView(dialogViewSubMenu);
            builder.setTitle(getString(R.string.dialog_postOnWebsite));
            builder.setMessage(getString(R.string.dialog_postOnWebsiteHint));
            builder.setIcon(R.drawable.icon_post);
            builder.setNegativeButton(R.string.app_cancel, null);
            builder.setPositiveButton(R.string.app_ok, ((dialogInterface2, i2) -> {
                final EditText textInput = dialogViewSubMenu.findViewById(R.id.textInput);
                final String shareTop = textInput.getText().toString().trim();
                if (shareTop.isEmpty()) {
                    CatToast.show(context, R.string.toast_input_empty, 3);
                    return;
                }
                sp.edit().putString("urlForPosting", shareTop).apply();
                final ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                final ClipData clip = ClipData.newPlainText("text", data);
                Objects.requireNonNull(clipboard).setPrimaryClip(clip);
                CatToast.show(context, getString(R.string.app_done), 1);
                addAlbum("", shareTop, true);
                try {
                    dialogParent.cancel();
                } catch (final Exception e) {
                    Log.i(TAG, "shouldOverrideUrlLoading Exception:" + e);
                }
            }));

            final Dialog dialog = builder.create();
            dialog.show();
            HelperUtils.setupDialog(context, dialog);
        }
    }
    private void searchOnSite() {
        findViewById(R.id.appBar).setVisibility(GONE);
        searchOnSiteLayout.setVisibility(VISIBLE);
        HelperUtils.showSoftKeyboard(searchOnSiteInput);
    }
    private void saveBookmark(final String title, final String url) {
        final RecordAction action = new RecordAction(context);
        action.open(true);
        final String message = context.getString(R.string.app_error) + ": " + context.getString(R.string.app_error_save);
        if (action.checkUrl(url, RecordUnit.TABLE_BOOKMARK))
            CatToast.show(context, message, 2);
        else {
            action.addBookmark(new Record(title, url, 0, 0));
            CatToast.show(context, getString(R.string.app_done), 1);
        }
        action.close();
    }

    private void performGesture(final String gesture, final String url) {
        final String gestureAction = Objects.requireNonNull(sp.getString(gesture, "0"));
        switch (gestureAction) {
            case "01":
                break;
            case "02":
                if (ninjaWebView.canGoForward()) {
                    ninjaWebView.stopLoading();
                    final WebBackForwardList mWebBackForwardList = ninjaWebView.copyBackForwardList();
                    final String historyUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getCurrentIndex() + 1).getUrl();
                    ninjaWebView.initPreferences(historyUrl);
                    ninjaWebView.goForward();
                }
                else CatToast.show(context, R.string.toast_webview_forward, 2);
                break;
            case "03":
                if (null != fullscreenHolder || null != customView || null != videoView) {
                    Log.v(TAG, "Foss Explorer in fullscreen mode");
                } else if (ninjaWebView.canGoBack()){
                    sp.edit().putBoolean("backPressed", true).apply();
                    ninjaWebView.goBack();
                } else removeAlbum(currentAlbumController);
                break;
            case "04":
                ninjaWebView.pageUp(true);
                break;
            case "05":
                ninjaWebView.pageDown(true);
                break;
            case "06":
                showAlbum(nextAlbumController(false));
                break;
            case "07":
                showAlbum(nextAlbumController(true));
                break;
            case "08":
                new Handler(Looper.getMainLooper()).postDelayed(this::showOverview, 100);
                break;
            case "09":
                addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
                break;
            case "10":
                removeAlbum(currentAlbumController);
                break;
            case "11":
                overViewTab = getString(R.string.album_title_tab);
                setSelectedTab();
                new Handler(Looper.getMainLooper()).postDelayed(this::showOverview, 100);
                break;
            case "12":
                shareLink(ninjaWebView.getTitle(), Objects.requireNonNull(ninjaWebView.getUrl()));
                break;
            case "13":
                searchOnSite();
                break;
            case "14":
                saveBookmark(ninjaWebView.getTitle(), url);
                break;
            case "16":
                ninjaWebView.reload();
                break;
            case "17":
                ninjaWebView.loadUrl(HomePage.startUrl(sp));
                break;
            case "18":
                bottom_navigation.setSelectedItemId(R.id.page_2);
                showDialogFilter();
                break;
            case "19":
                showDialogFastToggle(ninjaWebView.getTitle(), ninjaWebView.getUrl(), null);
                break;
            case "22":
                sp.edit().putBoolean("sp_screenOn", !sp.getBoolean("sp_screenOn", false)).apply();
                triggerRebirth(context);
                break;
            case "24":
                copyLink(ninjaWebView.getUrl());
                break;
            case "25":
                final Intent settings = new Intent(context, Settings_Activity.class);
                startActivity(settings);
                break;
            case "26":
                doubleTapsQuit();
                break;
            case "27":
                sp.edit().putString("profile", "profileStandard").apply();
                ninjaWebView.reload();
                break;
            case "29":
                startActivity(new Intent(this, DownloadsActivity.class));
                break;
            case "30":
                libraryLauncher.launch(new Intent(this, BookmarksActivity.class));
                break;
            case "31":
                libraryLauncher.launch(new Intent(this, HistoryActivity.class));
                break;
            case "32":
                WebsiteInfoDialog.show(activity, ninjaWebView, null, ninjaWebView.getTitle(), url);
                break;
        }
    }

    private File copyHtmlToCache(final Context context, final Uri uri) {
        try {
            final InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (null == inputStream) return null;
            final File cacheFile = new File(context.getCacheDir(), "temp_preview.html");
            final FileOutputStream outputStream = new FileOutputStream(cacheFile);
            final byte[] buffer = new byte[4096];
            int bytesRead;
            while (-1 != (bytesRead = inputStream.read(buffer))) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
            return cacheFile;
        } catch (final Exception e) {
            final String text = context.getString(R.string.app_error) + ": " + e;
            CatToast.show(context, text, 2);
            return null;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void dispatchIntent(final Intent intent) {
        final String action = intent.getAction();
        String url = intent.getStringExtra(Intent.EXTRA_TEXT);
        final Uri dataUri = intent.getData();
        final String mimeType = intent.getType();
        getIntent().setAction("");
        if ("".equals(action)) {
            Log.i(TAG, "resumed Foss Explorer");
        } else if (null != filePathCallback) {
            filePathCallback = null;
        } else if (Intent.ACTION_VIEW.equals(action) && null != dataUri) {
            String fileName = null;
            if ("content".equals(dataUri.getScheme())) {
                try (final Cursor cursor = getContentResolver().query(dataUri, null, null, null, null)) {
                    if (null != cursor && cursor.moveToFirst()) {
                        final int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (-1 != nameIndex) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                } catch (final Exception e) {
                    CatToast.show(context, getString(R.string.app_error), 2);
                }
            } else if ("file".equals(dataUri.getScheme())) {
                fileName = dataUri.getLastPathSegment();
            }
            if (null == fileName) {
                fileName = "Unbekannte_Datei.txt";
            }

            String extension = "";
            final int lastDot = fileName.lastIndexOf('.');
            if (0 <= lastDot) {
                extension = fileName.substring(lastDot + 1).toLowerCase();
            }

            final List<String> allowedExtensions = Arrays.asList(
                    "html", "txt", "xml", "json", "java", "md", "js", "css", "sh", "py", "org", "gpx"
            );

            if (!allowedExtensions.contains(extension)) {
                CatToast.show(context, getString(R.string.dialog_supported), 2);
                return;
            }
            // 3. Fehlerfreie Pfad-Generierung für content:// und file://
            final String filePath = dataUri.getPath();
            final String displayPath = null != filePath ? filePath : dataUri.toString();
            // Korrektur: Wenn die URI bereits eine Content-URI ist, belassen wir sie so, statt "file://" davorzuklatschen
            final String virtualFileUrl = "file".equals(dataUri.getScheme()) && null != filePath ? "file://" + filePath : dataUri.toString();
            final String fileContent = readTextFromUri(this, dataUri);

            if (!fileContent.trim().isEmpty()) {
                if ("html".equals(extension) || (null != mimeType && mimeType.contains("html"))) {
                    final File localHtmlFile = copyHtmlToCache(this, dataUri);
                    if (null != localHtmlFile && localHtmlFile.exists()) {
                        addAlbum(fileName, "file://" + localHtmlFile.getAbsolutePath(), true);
                        ninjaWebView.loadUrl("file://" + localHtmlFile.getAbsolutePath());
                    } else {
                        addAlbum(fileName, "about:blank" , true);
                        ninjaWebView.loadDataWithBaseURL(null, fileContent, "text/html", "UTF-8", null);
                    }
                } else {
                    // UNIVERSAL-METHODE für XML, JSON, TXT, JAVA, MD, etc.
                    String langClass = "language-txt";
                    String formattedContent = fileContent;
                    // NEU: Inhalts- und Endungserkennung basierend auf der extrahierten Extension
                    if ("xml".equals(extension) || fileContent.trim().startsWith("<")) {
                        langClass = "language-xml";
                    } else if ("json".equals(extension) || fileContent.trim().startsWith("{") || fileContent.trim().startsWith("[")) {
                        langClass = "language-json";
                        try {
                            if (fileContent.trim().startsWith("{")) {
                                final org.json.JSONObject jsonObject = new org.json.JSONObject(fileContent);
                                formattedContent = jsonObject.toString(2);
                            } else if (fileContent.trim().startsWith("[")) {
                                final org.json.JSONArray jsonArray = new org.json.JSONArray(fileContent);
                                formattedContent = jsonArray.toString(2);
                            }
                        } catch (final Exception ignored) {
                            CatToast.show(context, getString(R.string.app_error), 2);
                        }
                    } else if ("java".equals(extension)) {
                        langClass = "language-java";
                    } else if ("md".equals(extension)) {
                        langClass = "language-markdown";
                    } else if ("js".equals(extension)) {
                        langClass = "language-javascript";
                    } else if ("css".equals(extension)) {
                        langClass = "language-css";
                    } else if ("sh".equals(extension)) {
                        langClass = "language-bash";
                    } else if ("py".equals(extension)) {
                        langClass = "language-python";
                    }
                    // HTML-Sonderzeichen maskieren
                    final String escapedContent = formattedContent.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    final String htmlWrapper = "<html><head>"
                            + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                            + "<title>" + fileName + "</title>"
                            + "<style>"
                            + "  body { margin: 0; padding: 15px; background: #fafafa; font-family: sans-serif; color: #333; }"
                            + "  .file-info { background: #eaeaea; padding: 10px; border-radius: 5px; font-size: 12px; margin-bottom: 15px; border-left: 4px solid #007bb6; word-break: break-all; }"
                            + "  .file-info b { color: #111; }"
                            + "  pre, code { font-family: monospace !important; font-size: 13px !important; white-space: pre-wrap !important; word-wrap: break-word !important; }"
                            + "</style></head><body>"
                            + "<div class='file-info'>"
                            + "  <b>Datei:</b> " + fileName + "<br/>"
                            + "  <b>Pfad:</b> " + displayPath
                            + "</div>"
                            + "<pre class='" + langClass + "'><code class='" + langClass + "'>"
                            + escapedContent
                            + "</code></pre>"
                            + "</body></html>";

                    addAlbum(fileName, virtualFileUrl, true);
                    ninjaWebView.getSettings().setDefaultTextEncodingName("utf-8");
                    ninjaWebView.loadDataWithBaseURL(virtualFileUrl, htmlWrapper, "text/html", "UTF-8", null);
                }
            } else {
                getIntent().setAction("");
                addAlbum(null, Objects.requireNonNull(getIntent().getData()).toString(), true);
                BrowserUnit.openInBackground(activity, ninjaWebView);
            }
        } else if ("postLink".equals(action)) {
            if (1 > BrowserContainer.size()) {
                addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
            }
            postLink(url, null);
        } else if ("customSearches".equals(action)) {
            if (1 > BrowserContainer.size()) {
                addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
            }
            showDialogCustomSearches(url);
        } else if (null != intent.getAction() && intent.getAction().equals(Intent.ACTION_PROCESS_TEXT)) {
            final CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            assert null != text;
            url = text.toString();
            addAlbum(null, url, true);
        } else if (null != intent.getAction() && intent.getAction().equals(Intent.ACTION_WEB_SEARCH)) {
            url = Objects.requireNonNull(intent.getStringExtra(SearchManager.QUERY));
            addAlbum(null, url, true);
        } else if (null != url && Intent.ACTION_SEND.equals(action)) {
            addAlbum(null, url, true);
        } else if (1 > BrowserContainer.size()) {
            addAlbum(getString(R.string.app_name), HomePage.startUrl(sp), true);
        }
    }

    private String readTextFromUri(final Context context, final Uri uri) {
        final StringBuilder stringBuilder = new StringBuilder();
        try {
            final InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (null != inputStream) {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while (null != (line = reader.readLine())) {
                    stringBuilder.append(line).append("\n");
                }
                inputStream.close();
            }
        } catch (final Exception ignored) {}
        return stringBuilder.toString();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setWebView(final String title, String url, boolean foreground) {
        setWebView(title, url, foreground, false);
    }

    private void setWebView(final String title, String url, boolean foreground, final boolean privateTab) {

        ninjaWebView = new NinjaWebView(context, privateTab);

        // A long press on a link or a picture opens Chromium's context menu for it.
        ninjaWebView.setOnLongClickListener(v -> {
            final NinjaWebView tab = (NinjaWebView) v;
            return LinkContextMenu.fromLongPress(tab, target -> {
                showLinkContextMenu(tab, target);
                return kotlin.Unit.INSTANCE;
            });
        });

        if ("no".equals(Objects.requireNonNull(sp.getString("saved_key_ok", "no")))) {
            sp.edit().putString("saved_key_ok", "yes")
                    .putString("setting_gesture_tb_up", "04")
                    .putString("setting_gesture_tb_down", "05")
                    .putString("setting_gesture_tb_left", "03")
                    .putString("setting_gesture_tb_right", "02")
                    .putString("setting_gesture_nav_up", "16")
                    .putString("setting_gesture_nav_down", "10")
                    .putString("setting_gesture_nav_left", "07")
                    .putString("setting_gesture_nav_right", "06")
                    .putString("setting_gesture_tabButton", "19")
                    .putString("setting_gesture_overViewButton", "18")
                    .putBoolean("sp_autofill", true)
                    .apply();
            ninjaWebView.setProfileDefaultValues();

            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            final String m = getString(R.string.app_intro_a) + " " + getString(R.string.app_intro_b);
            builder.setTitle(R.string.app_name);
            builder.setMessage(m);
            builder.setIcon(R.drawable.icon_web);
            builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> dialog.cancel());
            final AlertDialog dialog = builder.create();
            dialog.show();
            HelperUtils.setupDialog(context, dialog);
        }

        ninjaWebView.setBrowserController(this);
        ninjaWebView.setAlbumTitle(title, url);

        if (url.isEmpty()) ninjaWebView.loadUrl("about:blank");
        else ninjaWebView.loadUrl(url);

        if (null != currentAlbumController) {
            ninjaWebView.setPredecessor(currentAlbumController);
            //save currentAlbumController and use when TAB is closed via Back button
            final int index = BrowserContainer.indexOf(currentAlbumController) + 1;
            BrowserContainer.add(ninjaWebView, index);
        }
        else BrowserContainer.add(ninjaWebView);

        if (!foreground) ninjaWebView.deactivate();
        else {
            hideOverview();
            ninjaWebView.setBrowserController(this);
            ninjaWebView.activate();
            dialogOverview.cancel();
            animateNewTab = true;
            showAlbum(ninjaWebView);
        }
        final View albumView = ninjaWebView.getAlbumView();
        tab_container.addView(albumView, WRAP_CONTENT, WRAP_CONTENT);
        updateOmniBox();
    }

    private synchronized void addAlbum(final String title, String url, boolean foreground) {
        setWebView(title, url, foreground);
    }

    private void triggerRebirth(final Context context) {
        sp.edit().putInt("restart_changed", 0).apply();
        sp.edit().putBoolean("restoreOnRestart", true).apply();
        final Snackbar snackbar = Snackbar.make(ninjaWebView, R.string.toast_restart, Snackbar.LENGTH_SHORT);
        HelperUtils.makeSnackbarRound(snackbar);
        snackbar.setAction(context.getString(R.string.app_ok), (v -> {
            final PackageManager packageManager = context.getPackageManager();
            final Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
            assert null != intent;
            final ComponentName componentName = intent.getComponent();
            final Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            context.startActivity(mainIntent);
            System.exit(0);
        }));
        snackbar.show();
    }
}