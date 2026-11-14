# Chromium: tab list, tab switcher and Incognito page

Files from Chromium `main` at commit
`f0407d708267c4ff6a8c16a7da873d020b68f28b` (version 160.0.8161.0), unchanged.
Copyright The Chromium Authors. BSD-style license, see `LICENSE`.

| Here | Path in Chromium | In Foss Explorer |
| --- | --- | --- |
| `TabList.java`, `TabModel.java` | `chrome/browser/tabmodel/android/java/src/org/chromium/chrome/browser/tabmodel/` | `tabs/TabModel.kt`, a Kotlin port: the list of tabs with a current one, a model for normal tabs and one for Incognito tabs, and Chromium's rules for the tab that comes to the front when one is closed (`getNextTabIfClosed`). `browser/BrowserContainer.kt` keeps the app's tabs with it. |
| `tab_grid_card_item.xml` | `chrome/android/features/tab_ui/java/res/layout/` | `res/layout/item_tab_card.xml`: site icon, title, cross, picture of the page |
| `incognito_description_layout.xml`, `new_tab_page_incognito.xml` | `chrome/android/java/res/layout/` | `res/layout/include_incognito_ntp.xml`, the new tab page of a private tab |

What "Chromium's tab engine" can and cannot mean here: the pages of a tab are
drawn by Android System WebView, which is Chromium's engine, in every version
of Foss Explorer. Chromium's own classes for a tab (`Tab`, `TabImpl`,
`WebContents`) are part of the Chrome app and need its native library, so
they cannot be compiled into another app; the tab itself is still
`view/NinjaWebView`, which came from FOSS Browser. What is Chromium's in Foss
Explorer is the bookkeeping of the tabs (this port), the look of the tab
switcher, and the texts ("Search your tabs", "You've gone Incognito").
