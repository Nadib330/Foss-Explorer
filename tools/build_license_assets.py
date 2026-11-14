#!/usr/bin/env python3
"""Writes the licence texts the app shows under Settings > About > Legal information.

    python3 tools/build_license_assets.py

Copies COPYING and COPYING.AGPL into app/src/main/assets/licenses/ and puts
together components.txt: the parts by others that the app contains, each with
its licence. Run it after any of these files changed; LegalInformationTest
fails if the assets are out of date.

Own code of Foss Explorer, GPL-3.0.
"""
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'licenses')


def read(*path):
    with open(os.path.join(ROOT, *path), encoding='utf-8') as f:
        return f.read()


COMPONENTS = """Open source components in Foss Explorer
=======================================

Foss Explorer's own code is under the GNU General Public License, version 3.
It contains the parts by others listed here, each under its own license.

FOSS Browser
  I started Foss Explorer as a fork of FOSS Browser. Since then I have rebuilt
  its screens (home page, menus, tab switcher, history, bookmarks, downloads,
  settings) after Chromium and Firefox, moved history and bookmarks into a
  database laid out like Firefox's, and I keep the list of open tabs the way
  Chromium does. I still use FOSS Browser's code for:
    - the bookmark filter, the gestures and the backup;
    - the web view inside a tab that loads the pages (NinjaWebView and its
      WebView clients) and a large part of the browser screen that drives it;
    - the ad blocker, the cookie-banner blocker and the custom redirects;
    - the site profiles and the list of saved websites;
    - the cache of site icons;
    - saving downloads, deleting browsing data, and helper code.
  LICENSE.md in the source code lists the files and how much of each.
  Copyright Gaukler Faun and the FOSS Browser contributors.
  GNU Affero General Public License, version 3 or later.
  https://codeberg.org/Gaukler_Faun/FOSS_Browser

Chromium: offline page and dinosaur game
  From Chromium 86.0.4240.198.
  Copyright 2015 The Chromium Authors. BSD 3-clause license (below).

Chromium: settings, context menu, tabs and Incognito page
  The texts of the settings screens, of the menu of a long press on a link or
  a picture, of the tab switcher and of the new tab page of a private tab;
  the arrangement of these screens; the icons of the site settings; and the
  bookkeeping of the open tabs (TabList and TabModel, ported to Kotlin).
  From Chromium 160.0.8161.0 (commit f0407d7).
  Copyright The Chromium Authors. BSD 3-clause license (below).
  https://chromium.googlesource.com/chromium/src

Firefox for Android: main menu, download prompt and protection panel
  The arrangement of the three-dot menu, of the question before a download
  and of the panel behind the shield, and the texts of that question and of
  that panel, from Firefox for Android (commit cd88835).
  Copyright Mozilla and its contributors.
  Mozilla Public License, version 2.0 (below).
  https://github.com/mozilla-firefox/firefox

Mozilla application-services: the Places database
  The tables of the database of history and bookmarks (Mozilla's schema file,
  unchanged) and the hash of an address (ported to Kotlin), from the
  component Firefox keeps history and bookmarks with (commit 5f95eb6).
  Copyright Mozilla and its contributors.
  Mozilla Public License, version 2.0 (below).
  https://github.com/mozilla/application-services

Readability
  Finds the article of a page for the simplified view. Version 0.6.0.
  Copyright (c) 2010 Arc90 Inc. Apache License, version 2.0 (below).
  https://github.com/mozilla/readability

AndroidX (core, appcompat, preference, webkit, recyclerview, documentfile)
  Copyright The Android Open Source Project. Apache License, version 2.0.
  https://developer.android.com/jetpack/androidx

Material Components for Android
  Copyright The Android Open Source Project. Apache License, version 2.0.
  https://github.com/material-components/material-components-android

Kotlin standard library
  Copyright JetBrains s.r.o. and Kotlin Programming Language contributors.
  Apache License, version 2.0.
  https://kotlinlang.org

Material Design Icons
  Copyright Google LLC. Apache License, version 2.0.
  https://fonts.google.com/icons

Search engine logos
  The logos of Google, Brave Search, Microsoft Bing, DuckDuckGo, Ecosia,
  Startpage and Qwant on the Search engine screen are trademarks of their
  owners. They are shown so that each engine can be recognised, and are not
  covered by the licenses of Foss Explorer.

App icon and logo
  By the developer of Foss Explorer, part of Foss Explorer.


The Chromium license
====================

%s

Mozilla Public License, version 2.0
===================================

%s

Apache License, version 2.0
===========================
%s"""


def main():
    os.makedirs(OUT, exist_ok=True)
    files = {
        'GPL-3.0.txt': read('COPYING'),
        'AGPL-3.0.txt': read('COPYING.AGPL'),
        'components.txt': COMPONENTS % (
            read('third_party', 'chromium-settings', 'LICENSE').strip(),
            read('third_party', 'firefox', 'LICENSE').strip(),
            read('third_party', 'readability', 'LICENSE.md').rstrip() + '\n',
        ),
    }
    for name, text in files.items():
        with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
            f.write(text)
        print('%s: %d bytes' % (name, len(text.encode('utf-8'))))


if __name__ == '__main__':
    main()
