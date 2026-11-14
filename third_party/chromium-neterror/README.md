# Chromium network error page and dinosaur game

Unmodified files from Chromium 86.0.4240.198
(<https://chromium.googlesource.com/chromium/src/+/refs/tags/86.0.4240.198>):

| File here | Path in Chromium |
| --- | --- |
| `offline.js`, `neterror.css`, `neterror.html`, `neterror.js` | `components/neterror/resources/` |
| `*-offline-sprite.png`, `*-error-offline.png`, `*-disabled.png` | `components/neterror/resources/images/default_{100,200}_percent/offline/` |
| `button-press.mp3`, `hit.mp3`, `score-reached.mp3` | `components/neterror/resources/sounds/` |
| `interstitial_core.css`, `interstitial_common.css` | `components/security_interstitials/core/common/resources/` |
| `LICENSE` | `LICENSE` |

Copyright The Chromium Authors. BSD-style license, see `LICENSE`.

This is the last Chromium release in which the game is one plain script
(`offline.js`); later releases split it into modules that need Chromium's
build system.

`tools/build_offline_page.py` combines these files into the single page the
app ships, `app/src/main/assets/offline/neterror.html`: style sheets, script,
images and sounds are placed inside the page, because the page is shown
without a network and under the address of the site that could not be
reached. The game script, the style sheets, the images and the sounds go in
unchanged. Of `neterror.html` the page keeps the elements of the error
message and of the game; the parts for Chrome's own features (offline
downloads, diagnostics, saved copies) are left out, and Chromium's
`$i18n{…}` / `jsvalues` placeholders are replaced by `{{…}}` placeholders that
`OfflinePage.kt` fills in. `neterror.js` (kept here for reference) is not
included: it drives the parts that were left out. The page has its constant
`HIDDEN_CLASS` and its lines that start the game.
