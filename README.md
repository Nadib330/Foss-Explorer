# Explorer

A privacy-friendly, open-source Android browser with Material You styling.

Explorer is a browser of its own, not a fork: its screens are laid out after
Chromium and Firefox for Android, its history and bookmarks live in a database
laid out like Firefox's, and its list of open tabs the way Chromium keeps it.

## What comes from FOSS Browser

Explorer's first version was written on top of [FOSS Browser](https://codeberg.org/Gaukler_Faun/FOSS_Browser)
by Gaukler Faun, and parts of that AGPL-3.0 code are still in the app. They are
the parts that are the browser at its base:

| Taken from FOSS Browser | Where it is now |
| --- | --- |
| The tab and its web view, rendering with the Android System WebView | `view/NinjaWebView`, `browser/NinjaWebViewClient`, `browser/NinjaWebChromeClient` |
| Per-site privacy profiles and their storage | `unit/BrowserUnit`, `fragment/Fragment_settings`, the profile screens |
| The cookie-banner blocker and the custom redirects | `browser/BannerBlock`, `view/CustomRedirectsDialog` |
| Backups and the code that clears browsing data | `unit/Backup`, `unit/Cleaner`, *Settings > Backup* |
| The download listener and its file handling | `browser/NinjaDownloadListener` |

Everything else was written for Explorer or copied from a free project whose
licence is kept beside it: the top and bottom bars, the three-dot menu, the tab
switcher, the home page, the shield of xGuard, the settings screens, the history
and bookmarks screens and the welcome are Explorer's own. The gestures FOSS
Browser offered, its bookmark filter and its own settings layout are gone.
[LICENSE.md](LICENSE.md) lists every component and its licence.

| Property     | Value                                   |
| ------------ | --------------------------------------- |
| App name     | Explorer                                |
| Version      | 18.0.0-beta6 (version code 23)          |
| Package      | `com.rpooi3.explorer`                   |
| Languages    | Kotlin (new code) and Java (upstream)   |
| Minimum SDK  | Android 8.0 (API 26)                    |
| Target SDK   | API 37                                  |
| UI           | XML views, Material 3 / Material You    |
| License      | GPL-3.0 (own code), AGPL-3.0-or-later (FOSS Browser code) |

## What comes from Cromite

[Cromite](https://github.com/uazo/cromite) is a Chromium fork for Android (the
successor of Bromite) under the GPL-3.0, the same licence as Explorer's own
code. Two things of Explorer follow it:

| Taken from Cromite | Where it is |
| --- | --- |
| The question before an extension is added — *Add "name"?*, *It can:*, a line for each permission, Cancel and Add extension, in Chromium's words | `extension/ExtensionPrompt`, `extension/ExtensionStore` (staging), the `ext_warn_*` texts |
| The method of the ad blocker: Adblock Plus rules matched by url, path and kind of request, and element rules hidden before the page is painted | `browser/AdFilter`, `browser/AdBlocker` |

Cromite has a row of its own in *Settings > About > Legal information*, below
Chromium's, and [LICENSE.md](LICENSE.md) and the components list in the app say
what was taken.

## Extensions from the Chrome Web Store

Explorer unpacks a `.crx` itself and runs what it can, because the Android
System WebView knows no `chrome.*` interfaces:

| Part | Where it is | What works |
| --- | --- | --- |
| Unpacking | `extension/CrxExtractor` | `Cr24` header (v3 protobuf, v2 key and signature), the ZIP behind it, Zip-Slip refused |
| Getting one | `extension/CrxDownloader`, `extension/StoreHook` | the update service Chrome uses; the store's own blue button is relabelled *Add to Explorer*, and the page is told it is Chrome |
| The manifest | `extension/ManifestParser`, `extension/WebExtension` | name, version, popup, options page, icon, permissions, content-script rules |
| Running it | `extension/ExtensionScripts`, `extension/ExtensionRuntime` | content scripts and CSS at `document_start`, `document_end` and `document_idle` |
| `chrome.*` | `extension/ExtensionScripts` (shim), `extension/ChromeApiBridge` | `runtime`, `storage.local`/`sync`/`session`, `tabs`, `i18n`, `action`, `window.browser` |
| Popups | `extension/ExtensionPopup`, `extension/ExtensionFiles` | a bubble anchored under the menu, the extension's own page in it, files served from `https://explorer-extensions.local/<id>/<path>` |
| Background | `extension/ExtensionBus` | the background page or MV3 service worker file in a hidden WebView, started with the browser, woken by a message |
| Asking first | `extension/ExtensionPrompt`, `extension/ExtensionStore` | the `.crx` is staged, what it asks for is shown, and it is only added on *Add extension* |
| The list | `extension/ExtensionStore` | `files/extensions/<id>/`, kept in one preference file, on/off per extension |

A Manifest V3 *service worker* is run as a page of its own in a hidden WebView
(since 17.4.0), because a WebView cannot register one; its `chrome.runtime`
messages, its `chrome.storage` and its timers work, a lifetime after the page is
gone does not. **`declarativeNetRequest`** is still not read into
`shouldInterceptRequest`, so an extension whose blocking depends on it installs
and stays in the list while that part does nothing.

Extensions are taken from Google's servers unchanged and their signature is
**not** checked — add only what you trust. Private tabs run no extension.

## Build

Requirements: JDK 17 or newer, Android SDK Platform 37 and Build-Tools 37.
Android Studio installs both on first sync.

**Android Studio:** *File > Open*, select this folder, wait for the Gradle
sync, then *Run*.

**Command line:**

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # Robolectric tests (API 26 and 34)
./gradlew lintDebug
```

The test run also renders the browser screen to PNG files in
`app/build/outputs/screenshots/` (home page in light, dark, landscape and on
Android 8; the bars on a website, with find-in-page and hidden; the page
actions; the search dialog; the settings screens). That gives a look at the
UI without a device. Web content, shadows, the system bars and the three-dot
popup menu are not in those pictures.

### Signed release build

Create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=/absolute/or/project-relative/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then run `./gradlew assembleRelease`. Without that file the release APK is
built unsigned.

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/rpooi3/explorer/
│   ├── ExplorerApp.kt          Application class (Kotlin)
│   ├── theme/                  ExplorerTheme.kt: dynamic colour, brand palette, light/dark
│   ├── home/                   the home page: HomePage (when it shows), HomeShortcuts (which
│   │                           tiles), HomePageView (the screen), HomeAppearance (wallpaper,
│   │                           colour), MonogramDrawable
│   ├── compat/                 StorageCompat.kt: Downloads on Android 8/9 vs 10+
│   ├── activity/               BrowserActivity (the single browser screen), WelcomeActivity
│   │                           (the first start) and the settings screens
│   ├── browser/                WebView clients, ad blocking (AdFilter, AdBlocker — after
│   │                           Chromite), cookie-banner block, tab contracts,
│   │                           ChromeUserAgent.kt (the user agents), DesktopIdentity.kt (the
│   │                           in-page identity of a desktop page), OfflinePage.kt (the
│   │                           offline notice and the dinosaur game)
│   ├── site/                   site settings: SiteCategory (the list), SiteSettings (what is
│   │                           stored), SitePermissions (the question to the user),
│   │                           SiteSettingsScreen (the screens)
│   ├── view/                   NinjaWebView (the tab), TopBarController (top and bottom
│   │                           bar), MainMenuSheet (three-dot menu), LinkContextMenu (long
│   │                           press on a link or picture), TabSwitcher (the grid of open
│   │                           tabs), list adapters, dialogs
│   ├── library/                the history and bookmarks screens and their shared base
│   ├── downloads/              the downloads screen and where its list comes from
│   ├── privacy/                PrivateTabs.kt: storage separation for private tabs;
│   │                           PrivacySettings.kt (Safe Browsing, preloading), IncognitoLock.kt,
│   │                           TrackerList.kt and TrackingAnalysis.kt (xGuard), MarkedAds.kt
│   ├── accessibility/          PageZoom, ReaderMode (simplified view), TouchpadSwipe, PageHooks
│   │                           (where these meet a tab)
│   ├── tabs/                   TabModel.kt: the list of open tabs, a port of Chromium's tab
│   │                           models; InactiveTabs.kt: the inactive section
│   ├── search/                 SearchEngines.kt: the engines and their search addresses
│   ├── database/               RecordAction (what the app asks the database), favicon cache;
│   │                           places/: history and bookmarks in Firefox's Places tables
│   ├── fragment/, preferences/ settings UI; SettingsSections.kt (the topics and the screens
│   │                           behind them), SettingsScreens.kt (what their rows do),
│   │                           ChromiumPreferences.kt (Chromium's row types), SettingsCards.kt
│   │                           (draws them as groups of cards)
│   └── unit/                   helpers: URLs, backup, cleanup, permissions
└── res/                        layouts, 27 translations, palette, launcher icon
app/src/test/                   Robolectric and JUnit tests
docs/upstream/                  upstream README, FAQ, changelog, privacy policy (unchanged)
third_party/chromium-neterror/  Chromium's offline page and dinosaur game, unchanged (BSD)
third_party/chromium-settings/  Chromium's settings screens the app's are laid out after (BSD)
third_party/readability/        Mozilla's Readability, unchanged (Apache-2.0)
third_party/firefox/            Firefox's main menu and download prompt, for reference (MPL-2.0)
third_party/chromium-tabs/      Chromium's tab list, tab card and Incognito page, for reference (BSD)
third_party/application-services/  Mozilla's Places schema and address hash (MPL-2.0)
art/Explorer_Logo5.jpg          the logo; tools/make_icon.py makes the app icon from it
tools/build_offline_page.py     packs the offline page into app/src/main/assets/offline/neterror.html
tools/chromium_strings.py       writes res/values/strings_chromium.xml from Chromium's string files
tools/build_license_assets.py   writes the licence texts the app shows (assets/licenses/)
tools/make_icon.py              writes the app icon (two vector layers) and the home page logo
tools/upstream_audit.py         says how much of the code is still FOSS Browser's
```

`BrowserActivity` is the browser: it hosts a list of `NinjaWebView` tabs and
talks to them through the `BrowserController` and `AlbumController`
interfaces. History, bookmarks, downloads and the settings are activities of
their own that return to it.

Screen layout (`res/layout/activity_main.xml`): the top bar `@id/topBar`, the
bottom bar `@id/bottomBar`, web pages in `@id/main_content` between them, and
the home page (`include_home.xml`) in the same place as the web pages.

### Bars and menus

- Top bar (`include_appbar.xml`): back arrow, address pill, three-dot button.
  The pill shows the site name; tap it to edit the address, tap the share
  icon inside it to share the page. At its start a ring turns while the page
  loads; when the page is there, a reload button takes the ring's place. The home page has no top bar: it carries
  its own search box and its own three-dot button (`TopBarController.setHomeMode`).
- Bottom bar (`include_bottombar.xml`): home, new tab, tab counter. The
  counter opens the tab switcher.
- Three-dot menu (`res/menu/menu_main.xml`, drawn by `MainMenuSheet`): laid
  out like the main menu of Firefox for Android. A sheet from the bottom with
  a handle; the entries in rounded groups: New tab and New private tab; Share,
  Zoom (always there on a website, as in Chromium: the sheet has the value, a
  minus, a slider and a plus, and remembers a zoom for the site) and Desktop
  site, which says "On" or "Off" at its end;
  History, Bookmarks and Downloads as a row of tiles; Settings. Which entries
  there are is decided by the menu resource, as before.
- Page actions: long press on the address pill. Reload, find in page, save
  as bookmark, save as PDF, close tab, hide the bars and other actions for
  the current page live here, because the three-dot menu has no room for them.
  The same sheet, with a different selection, opens on a long press on a
  search suggestion (`BrowserActivity.showOverflow`).
- Long press on a link or a picture in a page (`view/LinkContextMenu`): the
  context menu of Chromium for Android. A header with the title and the
  address, then "Open in new tab", "Open in Incognito tab", "Copy link
  address", "Copy link text", "Download link", "Share link", and for a picture
  "Open image in new tab", "Copy image", "Download image", "Share image", and
  at the end "Mark as ad and hide" (see *Marked ads* below).
  Chromium's entries for tab groups, page previews and Google Lens have no
  counterpart in Explorer and are left out.
- A download is asked about in a sheet like Firefox's (`sheet_download.xml`):
  "Download file?" with the size, the file name, and the button "Download".
  Tap the name to change it.

### Address field

Tapping the address pill or the search box of the home page opens the address
field over the page (`include_search_panel.xml`): back arrow, text field,
clear button, and below it what was typed followed by matching bookmarks and
history entries. Enter or a tap on a row opens it; the back arrow or the Back
key closes the field and leaves the page as it was.

### History, bookmarks and downloads

Three screens of their own, opened from the three-dot menu. They share
`library/LibraryActivity` (title bar, search, filter chips, list, empty state)
and hand the chosen address back to `BrowserActivity`.

- `HistoryActivity`: pages grouped by day, newest first; a link to *Clear
  browsing data* at the top; the X at the end of a row removes that page; long press
  for new tab, private tab, copy, share.
- `BookmarksActivity`: name and website per row; the three-dot button of a
  row opens, edits, moves, shares or deletes the bookmark. Upstream's coloured
  bookmark categories appear as chips above the list, and "Move to…" assigns
  one. There are no nested folders.
- `DownloadsActivity`: files grouped by day with size, chips by kind of file,
  and a line with the used and free space. The list is the system download
  manager's (which only reports this app's downloads) plus `DownloadLog`, a
  small list of the files the browser wrote itself (data and blob links, PDF
  export, backups). A file is opened with the app the system offers for it.

### User agent

`browser/ChromeUserAgent` builds the default user agent: that of Chrome on
Android, with the Android version of the device and the Chrome version of the
installed WebView, e.g.
`Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36`.
The device model is `K`, as in Chrome, and neither agent carries `wv` or the
`Version/4.0` of an embedded WebView. The client hints (`Sec-CH-UA…`) name
"Google Chrome" as well, and the `X-Requested-With` header with the package
name is switched off; both need a WebView that supports it.

"Desktop site" in the three-dot menu makes the tab in front send **Chrome on
Windows** and reloads the page:
`Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36`,
with `Sec-CH-UA-Platform: "Windows"`, `mobile: false` and the platform version
of Windows 10. **The Chrome Web Store is the one exception**: it is served the
agent Explorer had before 17.3.3, `Mozilla/5.0 (X11; Linux x86_64) … Safari/537.36`
with `Sec-CH-UA-Platform: "Linux"`, because the store is where the extensions
come from and it is the desktop page it has always answered.

A page is not only what it is sent, so `browser/DesktopIdentity` hands a desktop
page a script that runs before the page's own scripts: it writes
`navigator.platform`, `navigator.vendor = "Google Inc."`,
`navigator.maxTouchPoints = 0` and a `navigator.userAgentData`
(`getHighEntropyValues` included) of the computer the page was served as —
`Win32` / `platform: "Windows"` for a website, `Linux x86_64` /
`platform: "Linux"` for the store. It is taken back the moment the tab goes
back to the mobile version. There is no setting for a user agent of one's own.

Websites can still tell a WebView from Chrome by other means (missing Google
services, JavaScript interfaces); the user agent only covers the usual checks.

### Tab switcher

The tab counter opens the tab switcher (`view/TabSwitcher`,
`include_tab_switcher.xml`), laid out like the one of Chromium for Android:
a filled button for a new tab, a switch that shows the number of tabs and
changes between normal and private tabs, the menu, a field "Search your tabs"
that narrows the cards by title and address, and below a line every open tab
as a card with its title and a picture of the page. The card of the tab in
front is drawn in the inverse colours.

Which tabs there are is kept by a port of Chromium's tab models
(`tabs/TabModel.kt`, used through `browser/BrowserContainer`): one model for
normal tabs, one for private tabs, and Chromium's rules for the tab that
comes to the front when one is closed (a background tab leaves the front tab
alone; else the tab it was opened from; else its neighbour). Tap a card to go to the tab; the cross or a
sideways swipe closes it. The menu there has New tab, New private tab, Close
all tabs and Settings. The number of columns follows the width of the screen.
A tab opened in the foreground grows into place from the lower corner
(`NewTabAnimation`).

The picture of a tab is taken when the tab leaves the screen. A tab that was
opened in the background and never shown has none yet.

Open tabs are written down and come back with the next start. A tab that was
not in front for the time chosen under *Settings > Tabs and groups* (21 days
unless changed) moves to the inactive section (`tabs/InactiveTabs`): a row at
the top of the tab switcher that lists these tabs and opens them again. The
tab in front never moves. Inactive tabs are closed for good after 60 days
unless that is switched off. Explorer has no tab groups.

### Offline page and dinosaur game

When a page cannot be loaded because there is no connection, the tab shows
Chromium's "No internet" page with its dinosaur game, and loads the page again
by itself when the connection is back. `explorer://dino` shows the game alone.

The page is Chromium's own, version 86.0.4240.198, from
`third_party/chromium-neterror/` (BSD licence): its HTML, its style sheets,
the game script `offline.js` and its pictures and sounds, none of them
changed. `tools/build_offline_page.py` packs them into one file, because a
WebView has no `chrome://` resources to load them from. Three things in that
file are not Chromium's: a stand-in for `loadTimeData` (in Chromium the
browser supplies the texts), the constant `HIDDEN_CLASS` (defined in
Chromium's `neterror.js`, which is not included because it drives buttons this
page does not have), and the lines that start the game. Run the script again
after replacing the Chromium files.

Left out are the parts of Chromium's page that need the Chrome app itself:
its buttons, the offered offline articles, and keeping the high score after
the browser is closed.

### History and bookmarks: Firefox's Places database

Since 15.0.0 history and bookmarks are in `places.sqlite`, laid out as the
database of Firefox for Android (`database/places/`). The tables are created
from Mozilla's own schema file (`assets/places/create_shared_schema.sql`): a
visit is a row in `moz_historyvisits`, a page a row in `moz_places` with its
visit count, last visit, Firefox's address hash and its rank ("frecency"); a
bookmark is a row in `moz_bookmarks` under Firefox's folder `mobile______`.
Firefox's Rust component is not included (it is a native library of tens of
megabytes with Mozilla's telemetry); the tables are written with Android's
SQLite by `Places.kt`. The colour a bookmark is filed under and the list of
saved websites are in two tables of Explorer's own beside them.

The first start after an update takes over what FOSS Browser's database
(`Ninja4.db`) held and removes that file. Backups contain `places.sqlite`;
a backup made by an earlier version can still be restored.

### The shield

On a website a shield stands left of the three-dot button
(`view/ProtectionPanel`, `privacy/TrackingProtection`). It opens a panel laid
out like the protection panel of Firefox for Android: the site, a banner
("Explorer is on guard", "You turned off protections", "Be careful on
this site"), the switch "Enhanced Tracking Protection" for this site, how many
trackers were blocked on the page (a tap lists them), the connection, "Clear
cookies and site data" and "Privacy Settings". The protection is Explorer's
blocker of ads and trackers (site setting "Ads"): switching it off adds the
site to that setting's exceptions and reloads the page; the shield is then
crossed out. Firefox sorts what it blocks into kinds (social media trackers,
fingerprinters …); Explorer's filter list has no kinds, so the panel lists
the blocked hosts.

### Private tabs

A new private tab opens on a page of its own, laid out like Chromium's
private browsing page (`include_incognito_ntp.xml`, `home/IncognitoPage`): dark,
"You've gone private browsing mode", what is not saved and who can still see what you do,
and the card "Block third-party cookies". The line "Cookies and site data" is
only shown where it is true (see below). The top bar stays on this page.


A private tab does not write to the history, the site icon cache or the list
of tabs to restore. Its cookies, site storage and cache are kept apart only if
the device's WebView supports profiles (`WebViewFeature.MULTI_PROFILE`); they
are then deleted when the last private tab is closed and at the next start.
On a WebView without profile support, private tabs share cookies with normal
tabs. The app shows which of the two applies when a private tab is opened.
Downloads, bookmarks and site permissions made in a private tab are kept.

With *Lock private tabs when you leave Explorer* (Privacy and security),
private tabs are covered as soon as the app goes to the background
(`privacy/IncognitoLock`, `include_incognito_lock.xml`). "Unlock Incognito"
asks for the device's screen lock; "See other tabs" goes to the normal tabs.
The app calls them "private tabs" throughout, as the screens of Firefox for
Android do; only the names of Chromium's own string keys (`cr_incognito_…`)
still say incognito.

### Settings

The first screen (`res/xml/preference_setting.xml`) lists topics by title
only: Search engine, Privacy and security, xGuard, Home page customization,
Tabs and groups, Appearance, Layout, Accessibility, Extensions, Site settings,
Downloads, Help and support, About Explorer. Each opens a full screen of its
own. Appearance has no switch for dynamic colour any more: the colours of the
wallpaper are always used where Android offers them. Layout controls the
address-bar position and whether the landscape-style tab strip is also shown
in portrait. Gestures and the bookmark filter are no longer settings; the
browser gestures are Chromium's own (see *Gestures*).
The topics, and the screens behind their rows, are described in
`preferences/SettingsSections`; their rows are in
`res/xml/preference_section_*.xml`, shown by `Settings_Activity` with the
extra `EXTRA_SECTION`; `preferences/SettingsScreens` gives the rows their
state and behaviour. The search field of the first screen also finds a topic
by the titles and descriptions of the settings inside it.

**Chromium's look.** Privacy and security, Tabs and groups, Accessibility,
About and the site settings are laid out after the screens of the same name in
Chromium for Android. Their texts are Chromium's, with "Chrome" replaced by
"Explorer" (`res/values/strings_chromium.xml`, written by
`tools/chromium_strings.py`). Chromium's row types are rebuilt in
`preferences/ChromiumPreferences.kt`: `TextMessagePreference` (text between
the cards), `RadioRowPreference` (radio button, icon, text, description),
`ExpandablePreference` (a list that opens and closes) and `PageZoomPreference`
(the zoom slider with its preview). Chromium's own settings code is part of
the Chrome app and cannot be linked into another app, which is why the rows
are rebuilt rather than used; `third_party/chromium-settings/` holds the
Chromium files they follow.

| Screen | What it does | Limits |
| --- | --- | --- |
| Privacy and security > Preload pages | "Standard preloading" lets the engine load ahead the pages a site names as likely next ones (`WebSettingsCompat.setSpeculativeLoadingStatus`) | Needs a recent Android System WebView, else greyed out. No "Extended preloading" (Google servers) |
| > Lock Incognito tabs | Covers private tabs after the app was left; the screen lock opens them | Needs a screen lock on the device |
| > Safe Browsing | Standard protection or No protection (`WebSettings.setSafeBrowsingEnabled`) | A Google service used through WebView; may not work without Google Play services |
| > JavaScript optimization & security | Chromium's screen | WebView cannot switch the V8 optimizer off: second choice and exceptions are greyed out |
| Tabs and groups > Inactive | When tabs move to the inactive section, duplicates, automatic closing | No tab groups in Explorer |
| Accessibility | Default zoom, zoom per site, Android font size, zoom in the menu, force zoom, simplified view, captions, touchpad swipe | Caret browsing and Image descriptions greyed out (not offered by WebView) |
| Search engine | One card of engines with logo, name, domain, radio button; a second card with the other engines and a search address of your own | The logos are trademarks of the engines (see `LICENSE.md`); Mojeek and Searx have a letter sign instead |

Zoom is applied as the size of text (`WebSettings.setTextZoom`), the one zoom
WebView offers to apps; Chrome's zoom also scales pictures and layout. The
simplified view replaces the page by its article, found by Mozilla's
Readability (`assets/reader/`, `accessibility/ReaderMode`); Back returns to
the full page.

*Help and support* has the developer's address and the questions and answers
(`faq_*` in `strings.xml`). *About* is arranged like "About Chrome": the cards
Application version, Operating system and Legal information, and below them
Developer. *Legal information* holds the licences with their texts, the open
source components (`assets/licenses/`, written by
`tools/build_license_assets.py`) and both source code addresses.

*Site settings* follows Chrome for Android (`site/`). The list has two groups,
Permissions (Location, Camera, Microphone, Apps on device) and Content
(Third-party cookies, JavaScript, Pop-ups and redirects, Sound, Ads, Protected
content, Auto-verify, On-device site data, Automatic downloads), below them
the switch "Automatically remove permissions", and the profiles. Each row
shows its state and opens a screen like Chromium's: a description, two radio
buttons with the icon for on and for off, the lists "Not Allowed" and
"Allowed" that open and close, and "Add site exception". A website that wants
the location, the camera or the microphone is asked about once; the answer is
kept as an exception for that site. With automatic removal on, answers for
sites not visited for 60 days are forgotten.

Auto-verify is shown as Chromium has it (switch, "When on", "Things to
consider") but cannot be switched on: Android System WebView does not offer it
to the app.

The Ads screen holds Explorer's ad blocker: the two choices and the sites where
ads are allowed. The blocker itself is described under
[Ad blocking](#ad-blocking-after-chromite); its lists are chosen in
*Settings > xGuard*.

#### Ad blocking, after Chromite

Explorer blocks the way Chromite does — Chromium with a filter engine in front
of its network stack — and not the way FOSS Browser did: FOSS Browser's blocker
was one file of bare host names compared whole (and that file was never in the
app, so nothing was ever blocked by it), while this one reads the lists the web
has agreed on.

| Part | Where | What it does |
| --- | --- | --- |
| The engine | `browser/AdFilter.kt` | parses a list, matches a request, builds the style sheet; no Android in it |
| The blocker | `browser/AdBlocker.kt` | fetches and keeps the lists, answers a blocked request, writes the style sheet into the page |
| The switches | *Settings > xGuard* | one switch per list, and a row that fetches them now |

- **Two levels, as in Chromite.** A request whose url a rule matches is
  answered with an empty body in `shouldInterceptRequest`, before it leaves the
  app; the element rules (`example.com##.ad-box`) are written into the page as a
  `<style>` element before the page is painted, so an ad the site serves itself
  disappears as well.
- **Adblock Plus syntax.** `||host^`, `|`, `*`, `^`, `@@` exceptions,
  `$third-party`, `$script`, `$image`, `$domain=…` and the other request
  options are understood; a rule with an option a WebView cannot honour
  (`$redirect`, `$csp`, `$removeparam`, `$popup`) is left out rather than
  misapplied. A hosts file (`0.0.0.0 ads.example.com`) is read as `||host^`.
- **The kind of a request** is taken from `Sec-Fetch-Dest` (or `Accept` where
  that header is missing), so a rule that speaks of scripts does not touch
  images, and `$third-party` is decided by the registrable domain of the page
  and of the request.
- **The lists**: EasyList and EasyPrivacy (on out of the box) and StevenBlack's
  unified hosts (off). They are fetched from their own publishers, replaced
  whole, and refreshed once a day (once a week with data saver on); only after a
  successful download, so a failed fetch never blinds the blocker. The rules are
  filed under a keyword each and a request only looks at the rules its url could
  match, which is what makes a hundred thousand rules affordable on a phone.
- **Element rules** are capped at 3 000 generic ones (`MAX_GENERIC_COSMETIC`),
  because a style element is written into every page; the rules that name one
  site are all kept, and a `#@#` exception takes a rule back on the site it
  names.

What is still a limit: a WebView gives the app the requests of a page but not a
`declarativeNetRequest` engine, so blocking is answered one request at a time
and an extension that brings its own blocker still needs its own code path.


Every settings screen is a `PreferenceFragmentCompat` based on
`BasePreferenceFragment`, which hands the drawing to `SettingsCards`: each
`PreferenceCategory` becomes one block of rounded rows.

### First start and the welcome

The first start opens a welcome in the shape of the one Firefox for Android
shows (`activity/WelcomeActivity`, `res/layout/activity_welcome.xml`): four
pages stepped through with *Next*, *Skip* in the corner, dots that say where
the user is, and *Start browsing* on the last page, which also offers the
system's screen for the default browser. It is shown once; the flag is
`welcome_seen` in the default preferences. The dialog FOSS Browser opened on
the first start is gone, as are its gesture defaults.

The first start searches with Google. *Settings > Search engine* offers
Google, Startpage, DuckDuckGo, SearX, Qwant, Ecosia, Mojeek, Brave and Bing;
the Startpage variant that searched in German is no longer in the list, while
an installation that already chose it keeps it.

### Gestures

Settings for gestures are gone, as is the code behind them: no swipe on a
button and no long press on the tab counter runs an action of the user's
choosing any more. What is left is what Chromium for Android offers
(`BrowserActivity.dispatchTouchEvent`):

- A swipe that starts in a narrow strip at the left edge of the screen and
  goes to the right goes back; from the right edge to the left goes forward.
  It has to be clearly horizontal, and a circle with an arrow follows the
  finger so that it is visible what will happen. Swipes inside a page, on the
  bars and on the menus are untouched, as are two-finger swipes on a touchpad.

### Marked ads

A long press on a picture or a link offers *Mark as ad and hide* at the end of
its menu. The address is kept by `privacy/MarkedAds` (a string set,
`xguard_marked_ads`), the element is hidden at once, and from then on every
page hides it: `browser/NinjaWebViewClient` stops the request to a marked
address as it stops a host of the filter list, and runs a script on the page
that hides the pictures, frames, ad slots and links that point at one.
Marking applies to every site, whatever the site's protection is set to; the
blocked requests are counted in the shield's panel as xGuard's other blocks
are.

*Settings > xGuard > Marked ads* lists the marks. Tap one to unmark it — the
page lets it through again — or add an address by hand without visiting the
page. The row of xGuard says how many are marked.

### Home page

The home page is a native view, not a website. A tab shows it while the tab
is on `about:blank`; the web view underneath is empty. So the home page is an
ordinary entry in a tab's history (Back returns to it), and "open the start
page" means "load `about:blank`".

The tiles are the user's bookmarks, then recently visited sites (one tile per
site), then four built-in suggestions (the Explorer GitHub repository,
OpenStreetMap, F-Droid, and Codeberg) to fill up a new installation. No
bookmark is seeded on first launch. Up to eight tiles are shown. Long-press a
tile to open it in a new tab or to remove it from the home page. The
suggestions are defined in `HomeShortcuts.DEFAULTS`.

The look of the home page is set under *Settings > Home page customization*
(`home/HomeAppearance`): a picture from the device as wallpaper (a copy is
kept in the app, scaled to 2048 pixels at most, and can be darkened a little
for readability), or one of twelve background colours; the logo and the tiles
can be hidden. The name and the menu button turn light on a dark ground and
dark on a light one.

A different start page can be set under *Settings > Home page customization* or with the
page action that saves the current page as favourite. Clear that
setting to get the home page back.

## Differences from upstream

Based on FOSS Browser commit `79654ad7` (11 commits past tag `v24`, labelled
"v 25 (WIP)" upstream).

- New identity: name, package, launcher icon, colour palette.
- Minimum SDK lowered from 29 to 26, with a storage shim so downloads work on
  Android 8 and 9.
- Dynamic colour is on wherever Android offers it (12+), with no switch to
  turn it off; older versions get the Explorer palette, and the light/dark
  setting works everywhere.
- Kotlin enabled. New code and upstream's small model/contract classes are
  Kotlin; the WebView core is still upstream's Java.
- Gradle scripts migrated to Kotlin DSL with a version catalog.
- Fixed URLs that were truncated in the upstream work in progress (cookie
  banner rules, AdBlock fallback, help links).
- Toolbar at the top of the screen instead of the bottom. Pages are laid out
  below it rather than underneath it.
- A home page with a search box and site tiles replaces the upstream wiki as
  start page.
- Top bar with back arrow, address pill and a three-dot menu; bottom bar with
  home, new tab and tab counter. Upstream's configurable grid menu and its
  settings screen are gone.
- Private tabs.
- Settings drawn as groups of cards, with a search field, sorted into topics
  that each open a full screen; Help and support and About added.
- Default user agent of Chrome on Android; a Desktop site switch in the menu
  instead of upstream's user agent settings.
- Site settings arranged like Chrome's, with one screen per permission and
  exceptions per site, instead of upstream's profile switches.
- A tab switcher over the whole screen instead of the tab list in a dialog.
- Chromium's offline page with the dinosaur game.
- Cat mode removed.
- History, bookmarks and downloads as full screens instead of pages of the tab
  list and the system's Downloads app.
- Address field at the top with suggestions below, instead of a dialog.
- Unit tests added.

Not changed: the help buttons in the title bars of the Backup, profile and
Clear browsing data screens still open the upstream FOSS Browser wiki, because
Explorer has no documentation site of its own. The wiki is also still saved as
the first bookmark. It describes upstream's bottom toolbar and its menu, which
Explorer no longer has.

Texts added by Explorer since 0.1.0 exist in English only; the 27
translations cover upstream's texts.

See [CHANGELOG.md](CHANGELOG.md) for details.

## Staying in sync with upstream

The repository is a shallow clone, so upstream history is one commit deep.

```sh
git remote add upstream https://codeberg.org/Gaukler_Faun/FOSS_Browser.git  # if missing
git fetch --unshallow upstream   # once, downloads the full history (~240 MB)
git merge upstream/master
```

Expect conflicts in files Explorer moved (`de.baumann.browser` to
`com.rpooi3.explorer`) or converted to Kotlin.

## License

Explorer's own code is under the GNU General Public License, version 3
([COPYING](COPYING)). The code it takes from FOSS Browser stays under the GNU
Affero General Public License, version 3 or later
([COPYING.AGPL](COPYING.AGPL)), because only its authors can change that.
Chromium's offline page and the settings texts, layouts and icons taken from
Chromium are under a BSD licence; Mozilla's Readability is under the Apache
License 2.0. [LICENSE.md](LICENSE.md) says which part is which and why.

Copyright of the original work remains with Gaukler Faun and the FOSS Browser
contributors. Explorer is a modified version of that work, first changed in November
2026, and is not affiliated with or endorsed by the upstream project.
