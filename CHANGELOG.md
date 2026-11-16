# Changelog

## 17.3.3

### Desktop mode is a computer running Chrome, and it says so
- **"Desktop site" sends the user agent of Chrome on Windows**:
  `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)
  Chrome/<version of the WebView>.0.0.0 Safari/537.36`. Neither `wv` nor `Android` is in it
  any more, so a site of the desktop version gets what Chrome on a computer sends. Mobile mode
  keeps a mobile agent of Chrome on Android, also without `wv`. The store, which is always the
  desktop one, now sees a Windows browser as well.
- **The page is told the same, inside the page.** The user agent is only what the request
  carries; the scripts of a page read `navigator.platform` and the client hints and answered
  "Switch to Google Chrome" to an Android WebView. A script now runs before the page's own
  scripts (a document-start script; the page callbacks where the WebView has none) and writes
  `navigator.platform = "Win32"`, `navigator.vendor = "Google Inc."`, `navigator.maxTouchPoints = 0`
  and a `navigator.userAgentData` that says `mobile: false`, `platform: "Windows"` with the
  brands and `getHighEntropyValues` of Chrome. It is taken back as soon as the tab goes back
  to the mobile version, so a mobile page is never told that it is on Windows.
- The client hints that go out with the request agree: platform `Windows`, platform version
  `15.0.0`, `mobile: false`.

### Extensions
- The install itself no longer takes the browser down with it: the message that says the
  extension is being fetched is shown on the main thread (`view/CatToast`), the callback of the
  install is handed back to the main looper (`extension/StoreHook`), and the tab is loaded again
  only if it still exists. A tab that was closed while its extension was being installed is left
  alone, in the sheet, in the activity and in the extension runtime.

### Ad blocking, after Chromite
- **FOSS Browser's ad blocker is gone and Chromite's method is in its place.** The old one
  compared the bare host of a request with a file of hosts (`assets/hosts.txt`, which was never
  in the app, so a fresh install blocked nothing); the new one reads the lists the web has
  agreed on and works on two levels, as Chromite does: **a request that a rule matches is
  answered empty** before it leaves the app, and **an element that a rule names is hidden** by a
  style sheet written into the page before it is painted.
  - `browser/AdFilter.kt` is the engine and holds no Android: Adblock Plus syntax (`||host^`,
    `|`, `*`, `^`, `@@` exceptions, `$third-party`, `$script`, `$image`, `$domain=…`), a hosts
    file read as `||host^`, and `domain##selector` element rules with their `#@#` exceptions. A
    rule with an option a WebView cannot honour (`$redirect`, `$csp`, `$removeparam`, `$popup`)
    is left out rather than misapplied, and rules are filed under a keyword so that a request
    only looks at the rules its url could match.
  - `browser/AdBlocker.kt` fetches and keeps the lists, decides the kind of a request from
    `Sec-Fetch-Dest` (or `Accept`), and counts what it blocks for xGuard's panel.
  - **The lists are EasyList and EasyPrivacy** (on out of the box) and StevenBlack's unified
    hosts (off). They are fetched from their publishers, refreshed daily (weekly with data saver)
    and only after a successful download, so a failed fetch never blinds the blocker.
- **xGuard's screen shows the lists themselves** — one switch per list and *Update the filter
  lists now* — where FOSS Browser's single list used to be chosen from a dropdown of sixteen
  StefanBlack variants. The profile switch and Site settings > Ads are unchanged.

### The three-dot menu
- **The tile "Extensions" on the home page is rounded on all four corners.** It fills its row
  alone, so it is drawn like a group of one (`bg_menu_tile_single`) instead of with the inner
  corners of a row.

### Legal information
- **Chromium's and Firefox's licences are in the app**: *Chromium: BSD 3-Clause License*
  (`licenses/Chromium-BSD-3-Clause.txt` — the offline page, the game and the layout of the
  browser's settings) and *Mozilla Public License, version 2.0* (`licenses/MPL-2.0.txt` — the
  main menu, the download prompt, the protection panel and the Places database), beside
  GPL-3.0 and AGPL-3.0-or-later.
- **Open source components lists only what is still FOSS Browser's** — the tab and its web view,
  the per-site profiles, the cookie-banner blocker and the custom redirects, the site-icon
  cache, downloads and the backup — instead of the whole composition of the project it started
  from, and it names the filter lists of the blocker with the licences they come under.
- **The About tile says what it is**: "Copyright 2026 rpooi3. Free software under GPL-3.0." The
  FOSS Browser contributors and AGPL-3.0 are no longer in that line, and the rows no longer end
  in "Opens the text" or "Opens the source repository" — a row says what it is, not what opening
  it does.

## 17.3.2

### Adding an extension from the Chrome Web Store works now
- **The download was broken, and that is fixed.** The address of the update service was put
  together with `String.format`, which read the `%3D` and the `%26` of `x=id%3D<id>%26uc` as
  format specifiers and threw before anything was fetched. The address is built by hand now, and
  the redirect to `clients2.googleusercontent.com` is followed hop by hop (five at most), because
  the connection does not always do it on its own.
- **The store is told that it is talking to Chrome.** It asks `window.chrome` — `webstore`,
  `app`, `runtime`, `csi`, `loadTimes` — before it offers its install button, and without them it
  answers with "Switch to Google Chrome". The page gets those before its own scripts run
  (a document-start script, and the page callbacks as a fallback), and `chrome.webstore.install()`
  hands the install to Explorer.
- **The store's own blue button is taken over** instead of a second button of Explorer's being
  put over the page: **Add to Chrome** is found by its words (its `aria-label` too, and inside a
  shadow root), its label is written over as **Add to Explorer**, and a tap installs the
  extension through Explorer. The store draws its page again and again, so the label is looked
  after while the page lives (a `MutationObserver` and a short interval), and a tap that lands
  before that still installs. Once the extension is in, the button says **Already added** and the
  tab is loaded again, so the new content scripts start working at once.
- **The Chrome Web Store is always the desktop one.** Its pages get the desktop Chrome user
  agent and the wide layout whatever the tab is set to, and **Desktop site cannot be turned off**
  there: the entry of the three-dot menu is disabled and shows *On*, and `setDesktopSite(false)`
  is ignored on the store. No other version of the store offers extensions.
- A way that cannot fail: the extension sheet now offers **Add this extension to Explorer** when
  the page in front is a store page of an extension that is not in yet, whatever the store's page
  does with its own button.

### The three-dot menu
- **Extensions** is in the menu on the home page as well: there it takes the whole row, because
  there is nothing to zoom. On a website it is half width, beside **Zoom**, as in 17.3.1.

### Settings
- **Send feedback** moved from **About Explorer** to **Help and support**, where it stands under
  the developer's address; it opens the same new GitHub issue as before. About is four cards
  again.

## 17.3.1

### Extensions from the Chrome Web Store
- **Explorer can run extensions now.** The `.crx` of an extension is unpacked by
  Explorer itself: the `Cr24` header is read (version 3 with its protobuf header,
  version 2 with its key and signature), the ZIP behind it is written below
  `files/extensions/<id>/`, and an entry that would leave that folder stops the
  install (`SecurityException`, the Zip-Slip rule).
- **Adding one:** the `.crx` comes from the same update service Chrome uses,
  `clients2.google.com/service/update2/crx` with `x=id%3D<id>%26uc` and a desktop
  Chrome user agent, and is deleted after it was unpacked. On a page of the
  Chrome Web Store the button **Add to Explorer** appears in the corner, and the
  store's own **Add to Chrome** button is taken over.
- **A host, not a hack:** the manifest is read with `org.json` (name, version,
  `action`/`browser_action` popup, `options_ui`/`options_page`, icons,
  permissions and the `content_scripts` rules with their `run_at`), and the
  content scripts and stylesheets are run in the tab on both page callbacks —
  `document_start` when the page starts, `document_end` and `document_idle` when
  it is done. A guard keeps a script from running twice in one document.
- **`chrome.*` in the page:** before any extension code runs, a shim gives it
  `chrome.runtime` (`id`, `getURL`, `getManifest`, `sendMessage` and its
  listeners, `openOptionsPage`), `chrome.storage.local`/`sync`/`session` with the
  callbacks *and* promises, `chrome.tabs`, `chrome.i18n`, `chrome.action` and
  stubs for the rest, plus `window.browser`. The storage of an extension is kept
  in Explorer (`ext_store`-style preferences per extension, `extension_storage`).
  Every call to the bridge carries the token of the page load, which only the
  injected scripts know, so a script of the website cannot read an extension's
  storage.
- **Popups** open in a sheet of their own: a second WebView with the same
  bridge, and `https://explorer-extensions.local/<id>/<path>` served from
  `files/extensions/<id>/`, so the relative CSS, JS and pictures of a popup load
  without CORS errors. The same server answers `chrome.runtime.getURL()`.
- **What needs an extra step** is said where it matters: MV3 background service
  workers and `declarativeNetRequest` are not run, and the extension screen
  explains it. Content scripts, stylesheets, popups, options pages and
  `chrome.storage` work out of the box.
- Extensions are taken from Google's servers unchanged and their signature is
  not checked; the screen says so, so that only extensions one trusts are added.
- **Private tabs run no extension**, as Chrome's incognito windows do not.

### Settings
- A new topic **Extension** (between Appearance and Site settings, with the
  puzzle piece as its icon, searchable like the others): one row per installed
  extension that says whether it is on, its version and where it works, with a
  tap for on/off, its options page and removing it; a row that adds one by its
  address or its 32 letters; a row that opens the Chrome Web Store; and a note
  that says what Explorer can and cannot run.

### Site menu
- In the three-dot menu on a website, **Zoom** is half width and **Extensions**
  stands beside it as the other half. The sheet behind it lists the installed
  extensions (with the mark *This site* for those that ask for the page in
  front), opens a popup on a tap, and leads to **Extension settings** and to the
  Chrome Web Store.

## 16.1.0

### First start
- **Firefox for Android's welcome** opens on the first start: four pages that
  are stepped through with "Next", "Skip" in the corner, dots that say where
  the user is, and "Start browsing" at the end, which also offers the system's
  screen for the default browser. FOSS Browser's first-start dialog is gone,
  and with it the default gestures it wrote.
- **Google is the search engine of a new installation.**

### Browser
- The **gestures of FOSS Browser are gone**: no swipes on the buttons, no long
  presses on the bars and no gesture settings. What is left is what Chromium
  for Android has: a swipe in from the left edge goes back, one from the right
  edge goes forward, with the round arrow following the finger.
- The tab switcher opens and closes with **Chromium's transition**: the page
  steps back and fades as the grid comes forward, and comes back when a tab is
  chosen.
- **Zoom** is always in the three-dot menu on a website, as in Chromium: the
  sheet has the value, a minus, a slider and a plus, and remembers a zoom for
  each site. The switch that used to hide the entry is gone.
- A long press on a link or a picture offers **"Mark as ad and hide"**. The
  marked address is stopped like a host of the filter list on every page, and
  the element is hidden, also on the pages that follow.
- **Downloads keep their name and their extension.** No ".bin" is appended any
  more; the name comes from the server, then from the address, and a name typed
  without an extension gets the extension of the file.
- Private tabs say **"You've gone private browsing mode"**, the tab switcher
  says **"Search your private tabs"**, and the app calls them "private tabs"
  throughout.

### Settings
- **About**: a **Send feedback** row that opens a new issue on GitHub.
- **Legal information**: the card "Code from FOSS Browser" is gone; the rows
  say what they lead to instead of showing an address, and a tap opens it.
- **xGuard > Marked ads**: the ads marked on pages, with a row per mark that
  unmarks it and a row that adds an address by hand.
- Removed: **Gestures**, the **bookmark filter**, **JavaScript optimization &
  security**, the site setting **Auto-verify** and the **profile settings**
  (Standard profile, Saved websites), and in Accessibility the text cursor,
  the image descriptions and the switch for the zoom entry.
- **Appearance** has no switch for dynamic colour any more: the colours of the
  wallpaper are always used where Android offers them.
- **Search engine**: the German Startpage is no longer in the list; an
  installation that chose it keeps working and falls back to Google.
- The app calls itself **Explorer** everywhere, the source code is linked at
  `https://github.com/nadib330/Foss-Explorer/`, and `README.md`, `LICENSE.md`
  and the components list say which parts are still FOSS Browser's.

### Tests
- `Release1610Test` covers the welcome, the marked ads, the edge swipes, the
  zoom entry, the download names and the rows of About and Legal information;
  the tests that named the removed settings were brought up to date.

## 15.2.1

### xGuard
- The shield of the top bar is called **xGuard**. The panel behind it says so:
  its heading, "xGuard is on guard", "xGuard is off-duty", and the row that
  leads to the settings, which is "xGuard settings" now.

### Settings
- New topic on the first settings screen: **xGuard**, with a screen of its own.
  The ad blocker's settings moved there from the profile and from
  Site settings > Ads: the switch "Block ads and trackers" (kept per profile,
  as the engine reads it) and the filter list with its update. Site
  settings > Ads keeps the choice for a site, the exceptions, and a row that
  leads to xGuard.

### Tracking protection
- **Tracker blocker**: xGuard knows which hosts are trackers and what kind
  they are (analytics, advertising, social, fingerprinting). Analytics and
  advertising are stopped, social plugins can be switched on, and the whole
  blocker has a switch. The list ships with the app, so it works before the
  first filter list download and without a network connection.
- **Tracking analysis**: every request a page makes is written down — how many
  went to other sites than the page's own, which of those hosts are known
  trackers and what xGuard did with them. The panel behind the shield shows the
  analysis of the page in front; the xGuard screen shows the last page it saw
  and can forget it. Private tabs are never written down.

### Legal information
- The source code of **Chromium** and of **Firefox** are linked above FOSS
  Browser's: the engines and browsers this app is built on.

### Tests
- `Release1521Test` covers the new screen, the tracker list, the analysis and
  the order of the source links; the tests of 15.0.0 that named the shield were
  brought up to date.


## 15.0.0

### Private tabs
- A new private tab opens on a page like Chromium's Incognito page instead of
  the normal home page: dark, "You've gone Incognito", what is not saved, who
  can still see what you do, and the card "Block third-party cookies".

### Tabs
- The tab switcher is laid out like Chromium's: a filled button for a new tab,
  a switch with the number of tabs, "Search your tabs", and the tab in front
  as a dark card.
- The list of open tabs is kept by a port of Chromium's tab models instead of
  FOSS Browser's list. When a tab is closed, the tab it was opened from comes
  to the front, or else its neighbour, as in Chrome.

### Shield
- New on websites, left of the three-dot button: a shield that opens a panel
  like the protection panel of Firefox. It shows whether the site is
  protected and how many trackers were blocked, switches the protection off
  or on for the site, and clears the site's cookies and data.

### History and bookmarks
- Both are now kept in a database laid out as Firefox's (`places.sqlite`,
  Mozilla's schema). What the old database held is taken over at the first
  start. Every visit is recorded; the history still lists a page once.

### Home page
- The app icon beside the name is removed; the name stands alone.

### About
- "Code from FOSS Browser" under Legal information is written in the first
  person and says what changed in this release.

## 14.2.3

### Three-dot menu
- The menu is laid out like the main menu of Firefox for Android: a sheet from
  the bottom, the entries in rounded groups. New tab and New private tab;
  Share, Zoom and Desktop site (with "On" / "Off" at its end); History,
  Bookmarks and Downloads as a row of tiles; Settings. The entries are the
  same as before.

### Downloads
- A site that starts a download is answered with a sheet like Firefox's:
  "Download file?" with the size, the file name and the button "Download". It
  replaces the dialog with two text fields. The name can still be changed, by
  tapping it.

### Long press on links and pictures
- The menu is the context menu of Chromium for Android: a header with title
  and address, then Open in new tab, Open in Incognito tab, Copy link address,
  Copy link text, Download link, Share link; for pictures Open image in new
  tab, Copy image, Download image, Share image. The former sheet of page
  actions for links and the dialog with a picture's description are gone.

### Settings
- "Home page" is removed. New: "Home page customization", with a picture from
  the device as wallpaper, a background colour, switches for the logo and the
  shortcuts, and the start page setting that "Home page" had.

### About and licences
- The developer's GitHub address is github.com/nadib330.
- The licence information names every part and where it comes from, including
  the parts taken from Firefox (MPL-2.0), and lists the code that is still
  FOSS Browser's, file by file (`LICENSE.md`, `tools/upstream_audit.py`). The
  app is described as having begun as a fork; "A fork of FOSS Browser" in the
  settings became "Code from FOSS Browser".

### App icon
- New icon and logo: the cream disc with the olive compass
  (`art/Explorer_Logo5.jpg`).

## 13.2.2

The settings screens of this release are laid out after Chromium for Android
and use its texts ("Chrome" reads "Explorer"), its row types (description
text, radio buttons with a description, switches, lists that open and close)
and, in the site settings, its icons. See `third_party/chromium-settings/`.

### Privacy and security
- The screen has Chromium's two groups. Privacy: Delete browsing data, Preload
  pages, Lock Incognito tabs when you leave Explorer. Security: Safe Browsing,
  JavaScript optimization & security. What stood here before is gone; the
  profile settings moved to Site settings.
- Preload pages: "No preloading" or "Standard preloading" (pages a site names
  as a likely next page are loaded ahead). Standard preloading needs a recent
  Android System WebView; where it is missing the choice is greyed out.
  Chromium's "Extended preloading" goes through Google servers and is left out.
- Lock Incognito tabs when you leave Explorer: private tabs are covered when
  the app is left and opened again with the device's screen lock. Needs a
  screen lock on the device.
- Safe Browsing: "Standard protection" or "No protection (not recommended)",
  with Chromium's question before it is switched off. Before, it was always on.
- JavaScript optimization & security: Chromium's screen with both choices and
  "Add site exception". Android System WebView gives apps no way to switch the
  V8 optimizer off, so only the first choice can be used; the screen says so.

### Tabs and groups
- "Tabs" is now "Tabs and groups" and holds Chromium's "Move to inactive
  section": Never, or after 7, 14 or 21 days (default 21), "Archive duplicate
  tabs" and "Automatically close inactive items" (after 60 days).
- Tabs not used for the chosen time leave the tab switcher and wait in the
  inactive section, a row at its top. A tap there lists them; a tap on one
  opens it again.
- The old Tabs switches are gone, and what they chose is now fixed, as in
  Chrome: open tabs come back with the next start; closing a tab and leaving
  the app need no confirmation; the app opens on the tab, not the tab switcher.
- Closing the last tab opens a new tab in its place instead of leaving the
  app. Closing that new tab leaves the app.
- Explorer has no tab groups. The other rows of Chromium's screen (tab groups
  from other devices, sharing tabs with the operating system) have nothing to
  act on and are left out.

### Accessibility (new, below Appearance)
- Default zoom with Chromium's slider (50 % to 300 %) and preview. It replaces
  "Font size" under Appearance and takes over its value.
- Saved zoom for sites, Match Android font size, Show zoom option in main menu
  (adds "Zoom" to the three-dot menu; the zoom is kept per site), Force enable
  zoom, Simplified view for web pages with "Show hyperlinks" (articles are
  offered in a reading view, found by Mozilla's Readability), Captions (opens
  Android's caption settings), Swipe between pages using a touchpad.
- Caret browsing and Image descriptions are listed and greyed out: Android
  System WebView does not offer them to apps.

### Search engine
- The screen looks like Chrome's: one card, a row per engine with its round
  logo, the name, the domain and a radio button. Google, Brave and Microsoft
  Bing are new; DuckDuckGo, Ecosia and Startpage follow. The other engines
  Explorer had, and a search address of your own, are in a second card.

### About Foss Explorer
- Three cards as in "About Chrome": Application version, Operating system,
  Legal information; below them a fourth, Developer.
- Legal information holds the open source license, the texts of the GPL and
  the AGPL, the open source components with their licenses, and the source
  code addresses. Developer holds the name and the GitHub and Codeberg pages.

### Site settings
- Notifications, Embedded content and Clipboard are removed.
- Every category's screen is laid out like Chromium's: what the category is
  about, two radio buttons with the icon for on and off (three for Protected
  content, which can now ask), the lists "Not Allowed" / "Muted" and "Allowed"
  that open and close, and "Add site exception" with the "Add a site" dialog.
- On-device site data asks before it is switched off for all sites.

## 12.1.2

### Top bar
- When a page has finished loading, a reload button stands where the loading
  ring was. It loads the page again. While a page loads, the ring is back.

### Three-dot menu
- The menu is flat: an outline, no shadow.
- New entry "Desktop site" with a check box. It makes the tab in front ask for
  the desktop version of websites and reloads the page.

### Site settings
- The settings for a user agent of one's own are gone. The browser introduces
  itself as Chrome on Android, or as Chrome on a computer with "Desktop site".
- The screen is arranged like Chrome's. Permissions: Location, Camera,
  Microphone, Notifications, Embedded content, Clipboard, Apps on device.
  Content: Third-party cookies, JavaScript, Pop-ups and redirects, Sound, Ads,
  Protected content, Auto-verify, On-device site data, Automatic downloads.
- Each entry opens a screen with a switch for all sites, the list of blocked
  and allowed sites, and "Add site exception".
- Websites that want the location, camera or microphone are asked about, and
  the answer is remembered for that site.
- New: Sound (mute all sites or single ones), Apps on device (pages may not
  reach servers on the device itself unless allowed), Automatic downloads (a
  site that starts a second download by itself is asked about).
- The ad blocker's switch and filter list are on the Ads screen.
- New card "Automatically remove permissions", on by default: permissions of
  sites not visited for 60 days are removed.
- Notifications, Embedded content, Clipboard and Auto-verify are listed but
  marked "Not available", because Android System WebView does not offer them.
- The profile settings moved to Privacy and security.

### Customization
- Cat mode is removed.

### Tabs
- The tab counter opens a tab switcher over the whole screen: a grid of cards
  with a picture of each page, a switch between normal and private tabs, and a
  menu with New tab, New private tab, Close all tabs and Settings. It replaces
  the list in a dialog.
- A new tab grows into place from the lower corner of the screen.

### Help and support
- The first entry shows the e-mail address itself, and below it: "You can
  email me if it is extremely necessary. But I'm sorry I may not respond."

### Offline
- Without a connection, a page that cannot be loaded is replaced by
  Chromium's "No internet" page with the dinosaur game. The page is loaded
  again when the connection returns.
- `explorer://dino` opens the game.

### License
- Foss Explorer's own code is now under GPL-3.0. The code from FOSS Browser
  remains under AGPL-3.0-or-later, as its authors published it. `COPYING` is
  the GPL text, `COPYING.AGPL` the AGPL text; see `LICENSE.md`.

## 11.3.0

### User agent
- By default the browser now introduces itself as Google Chrome on Android,
  with the Android version of the device and the Chrome version of the
  installed WebView. Before, websites saw the WebView's own user agent, which
  names the device model and marks the app as a WebView.
- The client hints name Google Chrome too, and the `X-Requested-With` header,
  which carried the package name, is no longer sent. Both depend on the
  WebView of the device supporting it.
- Desktop mode sends Chrome on Linux. A user agent entered in the settings
  still takes precedence.

### Three-dot menu
- The menu is a card with 24dp corners.
- "Help" is gone from the menu; help is now in the settings.

### Home page
- The home page has no top bar. Its search box opens the address field, and
  a three-dot button in its corner opens the menu.

### Address field
- The address is edited in a field at the top of the screen with the
  suggestions listed below it, instead of in a dialog at the bottom. The first
  suggestion is the typed text itself.
- Suggestions show the address without the date of the visit.

### History, bookmarks, downloads
- History is a screen of its own: grouped by day, searchable, with a remove
  button per page and a link to delete browsing data.
- Bookmarks are a screen of their own: searchable, with a three-dot menu per
  bookmark (open in new tab, open in private tab, edit, move to, copy link,
  share, delete) and sorting by name, category or website. The bookmark
  categories are chips above the list.
- Downloads opens a list inside the browser instead of the system's Downloads
  app: grouped by day, with file size, filter chips (video, audio, images,
  documents, other), search, and open, share and delete per file.
- The tab list now shows tabs only; its bookmark and history pages are
  replaced by the screens above.
- Fixed: a file saved from a `blob:` link is written through the same storage
  code as other files and so works on Android 10 and later without the
  storage permission.

### Settings
- The first screen lists topics by title only; each opens a full screen:
  Search engine, Privacy and security, Home page, Tabs, Appearance, Gestures,
  Bookmark filter, Site settings, Backup, Help and support, About.
- The search finds a topic by the settings inside it.
- New: Help and support, with the developer's e-mail address and frequently
  asked questions.
- New: About Foss Explorer, with version, origin (fork of FOSS Browser),
  developer, source code of Foss Explorer and of FOSS Browser, and licence.
- Removed: "Post on website". The page action of the same name is still
  there and asks for the address each time.
- Removed: "Start tab of the overview", which no longer had an effect.
- An empty user agent field says what is sent instead of "Please input
  something."

## 1.2.0

### Top bar
- New top bar: back arrow, a pill-shaped address field with the site name in
  the middle and a share icon at its end, and a three-dot menu button.
- The bookmark/tab button and the shield (profile) button are gone from the
  top bar. The profile dialog is still reachable from the round shield button
  in the page actions and as a gesture.
- The pill shows the site, for example `example.org`, instead of page title
  plus full address. Tap it to see and edit the full address.
- Private tabs show a crossed-out eye at the start of the pill; the loading
  ring appears in the same place.

### Bottom bar
- New bottom bar: home (left), new tab (middle), tab counter (right). The
  counter shows the number of open tabs and opens the tab list.
- Pages are laid out between the two bars. The bottom bar steps aside while
  the keyboard is open.
- "Hide toolbars" hides both bars; the round button in the top corner brings
  both back.

### Three-dot menu
- The three-dot button opens a menu with exactly these entries: New tab, New
  private tab, History, Downloads, Bookmarks, Share, Settings, Help.
- Removed: the configurable grid menu that the shield button used to open,
  and its settings screen (Settings > Menu). A stored menu configuration is
  ignored.
- The actions of the old menu that concern the current page (reload, search
  on site, save as bookmark, save as favourite, copy link, open with, post on
  website, save as PDF, save as, home-screen shortcut, hide toolbars, close
  tab, restart, quit) moved to a sheet that opens with a long press on the
  address pill. The entries are fixed.
- Long press on a link, a bookmark, a history entry or a search suggestion
  opens the same kind of sheet as before, now with fixed entries.

### Private tabs
- New: "New private tab". A private tab writes nothing to the history, the
  site icon cache or the list of tabs to restore.
- Where the system WebView supports profiles, private tabs also have their
  own cookies, site storage and cache, deleted when the last private tab is
  closed and at the next start. Where it does not, cookies are shared with
  normal tabs. A message says which applies when a private tab is opened.

### Settings
- New look after the supplied concept: title bar with back arrow and help,
  a pill-shaped "Search settings" field, and settings as groups of rounded
  cards, each with a round tinted icon, a title and the current value.
- The main screen is regrouped into six blocks without headings: search and
  start page, appearance, tabs, privacy and websites, tools, data.
- The search field filters the main screen while typing (title and summary).
  It does not search inside the sub-screens.
- All sub-screens built from preferences (profiles, gestures, filters,
  backup, delete) use the same cards.
- Checkbox settings now have their text as title; search engine, theme and
  font size show their current value.
- Gestures: the two groups are labelled "Tabs button (bottom bar)" and "Menu
  button (top bar)", the buttons that took over from the removed ones.

### Project
- `versionCode 3`, `versionName 1.2.0`.
- New: `privacy/PrivateTabs.kt`, `preferences/SettingsCards.kt`,
  `include_bottombar.xml`, `menu_main.xml`. Removed: `Settings_Menu`,
  `AdapterSettingsMenu` and their layout.
- 151 tests (was 83), including rendered screenshots of the settings.

### Known limitations
- Not run on a phone or emulator; checked with Robolectric tests and rendered
  screenshots only. The three-dot popup, the keyboard behaviour of the bottom
  bar, bar animations and the storage separation of private tabs have not
  been exercised on a real WebView.
- New texts are English only.
- Help still opens the upstream FOSS Browser wiki, which describes the old
  toolbar and menu.

## 0.1.0

### Toolbar at the top
- The toolbar (page title, address, tab/bookmark button, menu button) is at
  the top of the screen. It is 64dp high instead of 80dp.
- Pages start below the toolbar. Before, the toolbar lay on top of the page.
- The address/search dialog opens at the top: input field first, suggestions
  below it with the best match first.
- Find in page appears in the toolbar's place.
- The status bar area takes the toolbar's colour, with dark icons in the light
  theme. It used to be black.
- Hiding the toolbar (long press on it, or the button in the find-in-page
  bar) now removes the whole bar and gives the space to the page. A round
  button in the top left corner brings it back. The choice survives a
  restart.
- Removed: the toolbar no longer slides away while scrolling down a page. See
  "Known limitations" below.

### Home page
- New tabs and the first start open a home page with the Foss Explorer logo,
  a search box and up to eight site tiles, modelled on the new tab page of
  Chrome for Android. It replaces the FOSS Browser wiki as start page.
- Tiles: bookmarks, then recently visited sites, then four suggestions
  (Wikipedia, OpenStreetMap, F-Droid, Codeberg). A tile shows the site icon if
  one is cached, otherwise the first letter of its name.
- Long press on a tile: open in a new tab (foreground or background), or
  remove the tile from the home page.
- On the home page the toolbar shows only its two buttons; the search box on
  the page takes the address bar's role.
- The "Favorite" setting is empty by default, which means the home page. Any
  address entered there is used as start page instead.
- Upgrading from 0.0.1: a stored start page that is still the old default (the
  upstream wiki) is replaced by the home page once. A start page the user
  chose is kept.
- The home page is not written to the history, and it is called "Foss
  Explorer" in the tab list.

### Fixes
- A WebView that reports dark-mode support for web content but then rejects
  the call no longer stops a tab from opening.

### Translations
- Two new texts are English only: "Remove from home page" and the summary of
  the "Favorite" setting.
- The welcome message of the first start was rewritten, because it described
  the wiki as the open page. Its 26 translations were removed, so it is shown
  in English in every language.

### Project
- New Kotlin code: `home/` (HomePage, HomeShortcuts, HomePageView,
  MonogramDrawable), `view/TopBarController`, `view/MaxWidthFrameLayout`.
- `androidx.core:core-ktx` 1.16.0 is declared as a dependency. It was already
  part of the app through Material, at the same version.

### Tests
- 83 tests (was 14): home page rules, tile selection, the home page view, and
  the browser screen with toolbar and home page on API 26 and 34.
- The test run renders the screen to PNG files, see the README.

### Known limitations
- Not run on a phone or emulator. Checked by build, lint, the tests above and
  the rendered pictures.
- The toolbar stays in place while scrolling. Upstream's bottom toolbar could
  slide away because it lay on top of the page. A top toolbar that slides
  away has to give its space to the page while the page is moving, which
  needs testing on real devices to get right.
- The overview (tabs, bookmarks, history) and the menus still open from the
  bottom of the screen.

## 0.0.1

First Foss Explorer build. Forked from FOSS Browser at commit `79654ad7`
(tag `v24` plus 11 "v 25 (WIP)" commits). The upstream history is in
[docs/upstream/CHANGELOG.md](docs/upstream/CHANGELOG.md).

### Identity
- Application ID and source package `com.rpooi3.explorer`, version 0.0.1.
- App name "Foss Explorer" in every locale.
- New adaptive launcher icon with a themed (monochrome) layer.
- Backup files are named `Foss_Explorer_backup_*`.

### Material You
- Dynamic colour is enabled by default on Android 12 and newer.
- New brand palette (Material 3 tonal spot, seed `#006A6A`) for Android 8 - 11
  and for users who switch dynamic colour off.
- The light/dark setting no longer depends on dynamic colour.
- The dynamic colour setting is hidden on devices that cannot use it.

### Platform support
- Minimum SDK lowered from 29 to 26.
- Downloads on Android 8 and 9: the storage permission is requested when
  needed and files are written without the Android 10+ MediaStore API.

### Fixes to the upstream work in progress
- Cookie banner rules and the fallback AdBlock list are downloaded from
  `raw.githubusercontent.com` again; the host had been truncated and did not
  resolve.
- Help links in the overview, the menu and the site-settings dialog open the
  matching wiki pages again instead of the Codeberg front page or a 404.

### Project
- Kotlin enabled; `GridItem`, `MenuItem`, `RecordUnit`, `PermissionCallback`,
  `PermissionUtils`, `AlbumController` and `BrowserController` converted.
- Gradle Kotlin DSL and version catalog.
- Robolectric tests that start every screen on API 26 and API 34.
- Removed upstream's prebuilt APKs, store listing and CI configuration.

### Known limitations
- Strings that contained a translated form of the upstream name fall back to
  English in 14 locales (45 strings in total) until they are re-translated.
- Help links and the first-run page point to the upstream FOSS Browser wiki.
