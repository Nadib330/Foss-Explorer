# Firefox for Android: main menu and download prompt

Files from <https://github.com/mozilla-firefox/firefox> at commit
`cd88835d4643f5cb8462654505f8c88806fb412c`, unchanged. Copyright Mozilla and
its contributors. Mozilla Public License, version 2.0, see `LICENSE`.

| Here | Path in the Firefox repository |
| --- | --- |
| `MainMenu.kt`, `MenuGroup.kt`, `MenuItem.kt`, `MenuDialogBottomSheet.kt` | `mobile/android/fenix/app/src/main/java/org/mozilla/fenix/components/menu/compose/` |
| `mozac_downloads_prompt.xml` | `mobile/android/android-components/components/feature/downloads/src/main/res/layout/` |

Foss Explorer's three-dot menu and its question before a download are laid
out after these. They are kept here for reference and for comparison; the app
does not compile them. Firefox's menu is written with Jetpack Compose and its
own design system, Foss Explorer with Android views and Material Components,
so the app's versions are written anew:

| In Foss Explorer | Follows |
| --- | --- |
| `view/MainMenuSheet.kt`, `res/layout/sheet_main_menu.xml`, `item_main_menu_row.xml`, `item_main_menu_tile.xml`, `res/drawable/bg_menu_*.xml` | the sheet from the bottom with its handle, the groups with large corners and rows 2dp apart (`MenuGroup`), rows of 52dp with icon and label (`MenuItem`), the state "On" / "Off" at the end of "Desktop site", and the library as a row of tiles with the icon above the label (`LibraryMenuGroup` in `MainMenu.kt`) |
| `res/layout/sheet_download.xml`, `NinjaDownloadListener.saveAs` | the download sign, the title with the size, the cross to close, the file name and the button, in the positions of `mozac_downloads_prompt.xml` |

Taken as they are: the texts "Download file?", "Download file? (%1$s)",
"Download" and "Close" of the download prompt
(`mobile/android/android-components/components/feature/downloads/src/main/res/values/strings.xml`),
and "On" / "Off" (`browser_feature_desktop_site_on` / `_off` in Fenix's
`strings.xml`). The entries of the menu are Foss Explorer's own.
