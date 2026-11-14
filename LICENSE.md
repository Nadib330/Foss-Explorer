# License

Explorer is free software. It is built on the code of FOSS Browser, and a
little under half of its code is still FOSS Browser's (the section "What is still FOSS
Browser's code" below says which). So two licences of the GNU family apply to
it, and the licences of the parts taken from Chromium, Firefox and Mozilla:

| Part | Licence | Text |
| ---- | ------- | ---- |
| Everything written for Explorer (its own code, layouts, texts, tests, tools) | **GNU General Public License, version 3 (GPL-3.0)** | [COPYING](COPYING) |
| Everything taken from [FOSS Browser](https://codeberg.org/Gaukler_Faun/FOSS_Browser) | GNU Affero General Public License, version 3 or later (AGPL-3.0-or-later) | [COPYING.AGPL](COPYING.AGPL) |
| The offline page and its dinosaur game, from the Chromium project | BSD 3-clause | [third_party/chromium-neterror/LICENSE](third_party/chromium-neterror/LICENSE) |
| Settings texts, row layouts and icons, from the Chromium project | BSD 3-clause | [third_party/chromium-settings/LICENSE](third_party/chromium-settings/LICENSE) |
| The arrangement of the three-dot menu, of the download prompt and of the protection panel, and the texts of that prompt and that panel, from Firefox for Android | Mozilla Public License 2.0 | [third_party/firefox/LICENSE](third_party/firefox/LICENSE) |
| The tab list (ported), the tab switcher and the Incognito page, from the Chromium project | BSD 3-clause | [third_party/chromium-tabs/LICENSE](third_party/chromium-tabs/LICENSE) |
| The schema of the history and bookmarks database (unchanged) and the hash of an address (ported), from Mozilla's application-services | Mozilla Public License 2.0 | [third_party/application-services/LICENSE](third_party/application-services/LICENSE) |
| Readability, from Mozilla (the simplified view) | Apache License 2.0 | [third_party/readability/LICENSE.md](third_party/readability/LICENSE.md) |
| AndroidX, Material Components, the Kotlin standard library and Material Design Icons, which the build adds | Apache License 2.0 | (in the app under Settings, About, Legal information, Open source components) |
| Search engine logos | trademarks of their owners, not licensed by this project | see "Search engine logos" below |
| App icon and logo (`art/Explorer_Logo5.jpg` and what `tools/make_icon.py` makes of it) | part of Explorer, GPL-3.0 | [COPYING](COPYING) |

## What is still FOSS Browser's code

Explorer does not look like FOSS Browser any more: the home page, the
bars and menus, the tab switcher, history, bookmarks, downloads and the
settings were written anew, after Chromium and Firefox. Under these screens a
good part of FOSS Browser is still at work, and it stays under the AGPL:

- **Backup and the list of saved websites**, used as FOSS Browser wrote them
  (`Settings_Backup`, `Fragment_settings_Backup`, `BackupUtils`,
  `CustomItemsUtils`, `Settings_ProfileList`, `List_standard`). FOSS Browser's
  gestures and its bookmark filter were removed in 16.1.0, together with the
  screens and classes behind them.
- **The web view inside a tab**: `NinjaWebView` and its clients
  `NinjaWebViewClient`, `NinjaWebChromeClient`, `NinjaDownloadListener`,
  `WebAppInterface`, and three quarters of `BrowserActivity`, the screen that
  drives them. (The list of open tabs is no longer FOSS Browser's: since
  15.0.0 it is a port of Chromium's tab models, `tabs/TabModel.kt`.)
- **Blocking**: the ad blocker (`AdBlock`), the cookie-banner blocker
  (`BannerBlock`) and the custom redirects (`CustomRedirectsDialog`).
- **Site profiles** and the list of saved websites (`Settings_Profile`,
  `Settings_ProfileList`, `List_standard`, `AdapterProfileList`).
- **The cache of site icons** (`FaviconHelper`) and the class a bookmark or
  history entry is handed around as (`Record`). (The database of bookmarks
  and history is no longer FOSS Browser's: since 15.0.0 it is laid out as
  Firefox's Places database, `database/places/`.)
- **Deleting browsing data** (`Settings_Clear`, `DeleteUtils`), the page-info
  dialog (`WebsiteInfoDialog`), list adapters, and helper code (`HelperUtils`,
  `BrowserUnit`).

Measured with `tools/upstream_audit.py`, which asks git where every line of
`app/src/main/java` comes from; the numbers are those of 15.2.1, before the
gestures and the bookmark filter were removed in 16.1.0: of 18,377 lines in
103 files, **8,500 lines (46 %) in 44 files are FOSS Browser's**.
File by file:

| File (under `com/rpooi3/explorer/`) | Lines from FOSS Browser | Share |
| --- | --- | --- |
| `activity/BrowserActivity.java` | 2093 of 2869 | 72 % |
| `browser/NinjaWebViewClient.java` | 617 of 693 | 89 % |
| `unit/HelperUtils.java` | 553 of 566 | 97 % |
| `view/NinjaWebView.java` | 519 of 816 | 63 % |
| `unit/BackupUtils.java` | 335 of 347 | 96 % |
| `browser/AdBlock.java` | 269 of 271 | 99 % |
| `fragment/Fragment_settings_Backup.java` | 269 of 279 | 96 % |
| `view/AdapterCustomList.java` | 252 of 259 | 97 % |
| `view/AdapterSearch.java` | 231 of 242 | 95 % |
| `unit/BrowserUnit.java` | 226 of 233 | 96 % |
| `database/FaviconHelper.java` | 220 of 223 | 98 % |
| `view/WebsiteInfoDialog.java` | 215 of 218 | 98 % |
| `browser/NinjaWebChromeClient.java` | 176 of 189 | 93 % |
| `activity/Settings_ProfileList.java` | 174 of 184 | 94 % |
| `browser/BannerBlock.java` | 174 of 176 | 98 % |
| `browser/NinjaDownloadListener.java` | 166 of 246 | 67 % |
| `unit/DeleteUtils.java` | 149 of 150 | 99 % |
| `view/CustomRedirectsDialog.java` | 139 of 143 | 97 % |
| `view/AdapterRecord.java` | 132 of 137 | 96 % |
| `preferences/EditTextSwitchPreference.java` | 129 of 136 | 94 % |
| `fragment/Fragment_settings.java` | 120 of 181 | 66 % |
| `view/AdapterGrid.java` | 118 of 121 | 97 % |
| `fragment/Fragment_settings_Clear.java` | 99 of 102 | 97 % |
| `activity/Settings_Clear.java` | 94 of 100 | 94 % |
| `view/AdapterTabs.java` | 92 of 99 | 92 % |
| `unit/CustomItemsUtils.java` | 85 of 87 | 97 % |
| `browser/List_standard.java` | 62 of 66 | 93 % |
| `browser/WebAppInterface.java` | 62 of 71 | 87 % |
| `activity/Settings_Backup.java` | 61 of 66 | 92 % |
| `view/AdapterMenu.java` | 61 of 64 | 95 % |
| `database/Record.java` | 59 of 61 | 96 % |
| `activity/Settings_Profile.java` | 58 of 63 | 92 % |
| `activity/Settings_Activity.java` | 51 of 111 | 45 % |
| `preferences/BasePreferenceFragment.java` | 48 of 73 | 65 % |
| `view/AdapterProfileList.java` | 47 of 49 | 95 % |
| `fragment/Fragment_settings_Profile.java` | 45 of 50 | 90 % |
| `activity/ActivityCustomSearches.java` | 18 of 19 | 94 % |
| `activity/ActivityShare.java` | 18 of 19 | 94 % |
| `view/CatToast.java` | 13 of 25 | 52 % |

The other files contain no line of FOSS Browser. Layouts and other resources
of FOSS Browser are in use as well, mostly for the screens named above.
Explorer is not a copy of FOSS Browser any more, but it is a work derived from
it, and has to keep its licence and its authors' names; that follows from this
list.

## Why not GPL-3.0 for all of it

FOSS Browser is published by Gaukler Faun and its contributors under the
AGPL, version 3 or later (see `LICENSE.md` in the upstream repository). Only
the holders of that copyright can publish their code under another licence. A
derivative work cannot do it for them, so that code stays under the AGPL here.

What such a work can choose is the licence of what it adds. That is GPL-3.0, as the
developer of Explorer decided for release 12.1.2. The two licences were
written to work together: section 13 of each allows a GPL-3.0 work and an
AGPL-3.0 work to be combined into one program and distributed as such. Each
part keeps its own licence in the combination.

## What this means in practice

- Anyone may use, study, change and pass on Explorer.
- Who passes it on, changed or not, has to pass on the source code as well,
  under these same licences.
- A file that existed in FOSS Browser and was changed here contains code under
  both licences. Treat such a file as AGPL-3.0-or-later, unless you take only
  the lines added by Explorer.
- Files that exist only in Explorer are GPL-3.0. Under
  `app/src/main/java/com/rpooi3/explorer/` these are the packages `site`,
  `home`, `library`, `downloads`, `compat`, `theme`, `privacy`,
  `accessibility`, `tabs` and `search`, and the
  files `ExplorerApp.kt`, `browser/ChromeUserAgent.kt`,
  `browser/OfflinePage.kt`, `preferences/SettingsCards.kt`,
  `preferences/SettingsSections.kt`, `preferences/SettingsScreens.kt`,
  `preferences/ChromiumPreferences.kt`,
  `preferences/HomeCustomizationScreen.kt`, `view/MainMenuPopup.kt`,
  `view/MainMenuSheet.kt`, `view/LinkContextMenu.kt`, `view/ProtectionPanel.kt`,
  `browser/BrowserContainer.kt`, `database/RecordAction.kt`,
  `database/RecordUnit.kt`, `database/places/PlacesDatabase.kt`,
  `database/places/Places.kt`,
  `view/MaxWidthFrameLayout.kt`, `view/TabSwitcher.kt` and
  `view/TopBarController.kt`; further the tests under `app/src/test` and
  `tools/`.
- Seven Kotlin files are translations of FOSS Browser's Java files and stay
  AGPL-3.0-or-later like every other file that came from there:
  `browser/AlbumController.kt`, `browser/BrowserController.kt`,
  `database/RecordUnit.kt`, `unit/PermissionCallback.kt`,
  `unit/PermissionUtils.kt`, `view/GridItem.kt`, `view/MenuItem.kt`.
- `git log --follow` on a file shows where it comes from; upstream's last
  commit in this history is `79654ad7`.
- The extra condition of the AGPL (section 13: users who interact with a
  modified version over a network must be offered its source) concerns the
  FOSS Browser code. For an app installed on a phone it does not come into
  play.

Explorer is a modified version of FOSS Browser's code. Copyright of the original
work remains with Gaukler Faun and the FOSS Browser contributors.
Modifications for Explorer were first made in November 2026. Foss
Explorer is not affiliated with or endorsed by the upstream project.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the licence texts for details.

## Third-party parts

**Chromium offline page and dinosaur game**
`third_party/chromium-neterror/`, from Chromium 86.0.4240.198.
Copyright 2015 The Chromium Authors. BSD 3-clause licence. The licence text is
also inside the app (`assets/offline/LICENSE.chromium.txt`) and can be read
under Settings, About, Legal information, "Open source components".

**Chromium tab list, tab switcher and Incognito page**
`third_party/chromium-tabs/`, from Chromium 160.0.8161.0 (commit `f0407d7`).
Copyright The Chromium Authors. BSD 3-clause licence. `tabs/TabModel.kt` is a
Kotlin port of Chromium's `TabList` and `TabModel` and carries Chromium's
notice. The tab switcher (`include_tab_switcher.xml`, `item_tab_card.xml`,
`view/TabSwitcher.kt`) and the new tab page of a private tab
(`include_incognito_ntp.xml`, `home/IncognitoPage.kt`) follow Chromium's
arrangement and use its texts; they are written for Explorer.

**Mozilla application-services: the Places database**
`third_party/application-services/`, from
<https://github.com/mozilla/application-services> at commit
`5f95eb6bafa36c4a86ff3109dacbf9026029833f`. Copyright Mozilla and its
contributors. Mozilla Public License, version 2.0.
`app/src/main/assets/places/create_shared_schema.sql` is Mozilla's file,
unchanged; `database/places/PlacesHash.kt` is a Kotlin port of `hash.rs` and
is under the MPL as well. `database/places/PlacesDatabase.kt`,
`database/places/Places.kt` and `database/RecordAction.kt` are written for
Explorer. Firefox's component itself (Rust, a native library) is not
part of the app; `third_party/application-services/README.md` says what that
means.

**Firefox protection panel**
The panel behind the shield of the top bar (`view/ProtectionPanel.kt`,
`sheet_protection.xml`) follows the protection panel of Firefox for Android
and uses its texts (`protection_panel_*`, `connection_security_panel_*`,
`clear_site_data*`, `trackers_blocked_panel_*` in `strings.xml`; MPL-2.0, see
`third_party/firefox`). The code is written for Explorer.

**Chromium context menu**
The menu of a long press on a link or a picture (`view/LinkContextMenu.kt`,
`res/layout/dialog_context_menu.xml`) has the entries, the order and the texts
of the context menu of Chromium for Android; the texts are in
`strings_chromium.xml` with the other Chromium texts (`cr_contextmenu_*`). The
code and the layout are written for Explorer.

**Firefox for Android: main menu and download prompt**
`third_party/firefox/`, from <https://github.com/mozilla-firefox/firefox> at
commit `cd88835d4643f5cb8462654505f8c88806fb412c`. Copyright Mozilla and its
contributors. Mozilla Public License, version 2.0. The three-dot menu
(`view/MainMenuSheet.kt`, `sheet_main_menu.xml`, `item_main_menu_row.xml`,
`item_main_menu_tile.xml`) and the question before a download
(`sheet_download.xml`) follow Firefox's arrangement and are written for Foss
Explorer; the texts of the download prompt and "On" / "Off" are Firefox's. The
files in `third_party/firefox/` are Firefox's own, unchanged, for reference;
the app does not compile them. The licence text can be read in the app under
"Open source components".

**Chromium settings: texts, layouts and icons**
`third_party/chromium-settings/`, from Chromium 160.0.8161.0 (commit
`f0407d708267c4ff6a8c16a7da873d020b68f28b`). Copyright The Chromium Authors.
BSD 3-clause licence. In the app these are
`app/src/main/res/values/strings_chromium.xml` (the texts, with "Chrome"
replaced by "Explorer"), the drawables `app/src/main/res/drawable/cr_*.xml`
(the icons: the same vector paths, under shorter file names), and the layouts
`preference_radio_row.xml`, `preference_page_zoom.xml`,
`include_page_zoom_slider.xml`, `dialog_page_zoom.xml`,
`preference_text_message.xml` and `preference_expand_widget.xml`, which
follow Chromium's arrangement and are written for Explorer. The licence
text can be read in the app under Settings, About, Legal information, "Open
source components".

**Search engine logos**
`app/src/main/res/drawable-nodpi/engine_*.png` show the logos of Google,
Brave Search, Microsoft Bing, DuckDuckGo, Ecosia, Startpage and Qwant on the
Search engine screen, so that each engine can be recognised. They are made
from the icons these services publish for their own sites (Bing's, published
at 32 pixels only, is redrawn at size). The logos are trademarks of their
owners and remain theirs. They are not covered by the licences of Foss
Explorer, and their use here does not mean that these services endorse the
app. Who passes the app on under another name or for another purpose should
check whether they may keep them. To leave them out, delete the files and the
`R.drawable.engine_*` entries in `search/SearchEngines.kt`; the screen then
shows a letter sign for each engine.

**Readability**
`third_party/readability/`, version 0.6.0 (commit
`ab4027a8b37669745016869a37a504727992b2ba`), unchanged; in the app as
`assets/reader/`. Copyright (c) 2010 Arc90 Inc. Apache License, version 2.0.

**Used open source libraries (from FOSS Browser):**

"Material Design Icons"
https://fonts.google.com/icons
