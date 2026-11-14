# Chromium settings: texts, layouts and icons

Files from Chromium `main` at commit
`f0407d708267c4ff6a8c16a7da873d020b68f28b` (version 160.0.8161.0,
<https://chromium.googlesource.com/chromium/src/+/f0407d708267c4ff6a8c16a7da873d020b68f28b>).
Copyright The Chromium Authors. BSD-style license, see `LICENSE`.

Foss Explorer's settings screens are laid out after Chromium for Android.
Chromium's settings code is part of the Chrome app (it needs Chromium's build
system, its native library and Chrome's profile and preference services), so
it cannot be linked into another app. What can be taken is taken: the texts,
the icons, and the arrangement of the screens and rows. The files here are
kept unchanged, for reference and for comparison.

| Here | Path in Chromium | Used in the app as |
| --- | --- | --- |
| `xml/privacy_preferences.xml` | `chrome/android/java/res/xml/` | order of `res/xml/preference_section_privacy.xml` |
| `xml/safe_browsing_preferences.xml` | `chrome/browser/safe_browsing/android/java/res/xml/` | `preference_section_safe_browsing.xml` |
| `xml/preload_pages_preferences.xml` | `chrome/browser/prefetch/android/java/res/xml/` | `preference_section_preload.xml` |
| `xml/tabs_settings.xml`, `xml/tab_archive_settings.xml` | `chrome/android/features/tab_ui/java/res/xml/` | `preference_section_tabs.xml`, `preference_section_inactive_tabs.xml` |
| `xml/accessibility_preferences.xml` | `components/browser_ui/accessibility/android/java/res/xml/` | `preference_section_accessibility.xml` |
| `xml/about_chrome_preferences.xml`, `xml/legal_information_preferences.xml` | `chrome/android/java/res/xml/` | `preference_section_about.xml`, `preference_section_legal.xml` |
| `xml/site_settings_preferences.xml`, `xml/website_preferences.xml` | `components/browser_ui/site_settings/android/java/res/xml/` | the screens built by `site/SiteSettingsScreen.kt` |
| `xml/main_preferences.xml` | `chrome/android/java/res/xml/` | where "Accessibility" stands on the first screen |
| `layout/radio_button_with_description.xml` | `components/browser_ui/widget/android/java/res/layout/` | `res/layout/preference_radio_row.xml` |
| `layout/binary_state_permission_preference.xml`, `layout/add_site_dialog.xml` | `components/browser_ui/site_settings/android/java/res/layout/` | the two choices and the "Add a site" dialog of a site settings screen |
| `layout/page_zoom_preference.xml`, `layout/page_zoom_slider.xml`, `layout/page_zoom_view.xml` | `components/browser_ui/accessibility/android/java/res/layout/` | `preference_page_zoom.xml`, `include_page_zoom_slider.xml`, `dialog_page_zoom.xml` |
| `drawable/*.xml` | the `res/drawable` folders of `components/browser_ui/` and `chrome/android/java/` | `res/drawable/cr_*.xml`: the icons of the site settings, the same vector paths under shorter names. One tint reference to a Chromium colour macro was replaced by `?attr/colorControlNormal`, and the one file without a copyright header was given one |

The texts come from Chromium's string files
(`chrome/browser/ui/android/strings/android_chrome_strings.grd`,
`components/browser_ui/strings/android/browser_ui_strings.grd`,
`components/browser_ui/strings/android/site_settings.grdp`,
`chrome/android/features/tab_ui/java/strings/android_chrome_tab_ui_strings.grd`).
These files are large and are not copied here. `tools/chromium_strings.py`
reads them from a Chromium checkout and writes
`app/src/main/res/values/strings_chromium.xml`; it lists which message becomes
which string. "Chrome" is replaced by "Explorer".

The layouts in the app are not copies: Chromium's use its own view classes
(`RadioButtonWithDescription`, `TextViewWithLeading`, `ChromeImageButton`),
which are part of the code that cannot be linked. They are written for Foss
Explorer with the views of Material Components, in the same order and with
the same parts.
