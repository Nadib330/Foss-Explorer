#!/usr/bin/env python3
"""Builds app/src/main/assets/offline/neterror.html from third_party/chromium-neterror.

Run from the project root after changing anything in third_party/chromium-neterror:

    python3 tools/build_offline_page.py
"""
import base64
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "third_party" / "chromium-neterror"
OUT = ROOT / "app" / "src" / "main" / "assets" / "offline" / "neterror.html"


def text(name):
    return (SRC / name).read_text(encoding="utf-8")


def data_uri(name, mime):
    return "data:%s;base64,%s" % (mime, base64.b64encode((SRC / name).read_bytes()).decode("ascii"))


IMAGES = {
    "images/default_100_percent/offline/100-error-offline.png": "100-error-offline.png",
    "images/default_200_percent/offline/200-error-offline.png": "200-error-offline.png",
    "images/default_100_percent/offline/100-disabled.png": "100-disabled.png",
    "images/default_200_percent/offline/200-disabled.png": "200-disabled.png",
}


def css():
    sheets = [text("interstitial_core.css"), text("interstitial_common.css"), text("neterror.css")]
    combined = "\n".join(sheets)
    for path, name in IMAGES.items():
        assert path in combined, path
        combined = combined.replace("url(%s)" % path, "url(%s)" % data_uri(name, "image/png"))
    # Images of Chrome features the page does not have (search box, download and
    # offline-copy buttons). Their rules never match; empty addresses keep the page from asking for files.
    combined = re.sub(r"url\((?!data:)[^)]*\)", "url()", combined)
    return combined


PAGE = """<!doctype html>
<!-- Built by tools/build_offline_page.py from Chromium's network error page. Do not edit; see third_party/chromium-neterror/README.md. -->
<html dir="{{dir}}" lang="{{lang}}">
<head>
  <meta charset="utf-8">
  <meta name="theme-color" content="#fff">
  <meta name="viewport" content="width=device-width, initial-scale=1.0,
                                 maximum-scale=1.0, user-scalable=no">
  <title>{{title}}</title>
  <style>
%(css)s
  </style>
  <script>
// Stands in for Chromium's load_time_data.js: the game only asks whether it has been disabled by an administrator.
var loadTimeData = {
  valueExists: function() { return false; },
  getValue: function() { return ''; }
};
  </script>
  <script>
%(js)s
  </script>
  <script>
// Stands in for neterror.js: the constant the game takes from it, and the lines that start the
// game when the page shows the offline icon.
const HIDDEN_CLASS = 'hidden';
document.addEventListener('DOMContentLoaded', function() {
  document.firstElementChild.classList.add('offline');
  new Runner('.interstitial-wrapper');
});
  </script>
</head>
<body id="t" style="font-family: sans-serif; font-size: 75%%">
  <div id="main-frame-error" class="interstitial-wrapper">
    <div id="main-content">
      <div class="icon icon-offline" alt=""></div>
      <div id="main-message">
        <h1>
          <span>{{heading}}</span>
        </h1>
        <p>{{summary}}</p>
        <div id="error-information-popup-container">
          <div id="error-information-popup">
            <div id="error-information-popup-box">
              <div id="error-information-popup-content">
                <div id="suggestions-list" style="{{suggestionsStyle}}">
                  <p>{{suggestionsHeader}}</p>
                  <ul>
{{suggestions}}
                  </ul>
                </div>
                <div class="error-code">{{errorCode}}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <div id="offline-resources">
    <img id="offline-resources-1x" src="%(sprite1)s">
    <img id="offline-resources-2x" src="%(sprite2)s">
    <template id="audio-resources">
      <audio id="offline-sound-press" src="%(press)s"></audio>
      <audio id="offline-sound-hit" src="%(hit)s"></audio>
      <audio id="offline-sound-reached" src="%(reached)s"></audio>
    </template>
  </div>
</body>
</html>
"""


def main():
    page = PAGE % {
        "css": css(),
        "js": text("offline.js"),
        "sprite1": data_uri("100-offline-sprite.png", "image/png"),
        "sprite2": data_uri("200-offline-sprite.png", "image/png"),
        "press": data_uri("button-press.mp3", "audio/mpeg"),
        "hit": data_uri("hit.mp3", "audio/mpeg"),
        "reached": data_uri("score-reached.mp3", "audio/mpeg"),
    }
    assert "</script>" not in text("offline.js")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(page, encoding="utf-8")
    print("wrote %s (%d bytes)" % (OUT.relative_to(ROOT), len(page.encode("utf-8"))))


if __name__ == "__main__":
    main()
