package com.rpooi3.explorer.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayInputStream;
import java.util.Objects;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.database.Record;
import com.rpooi3.explorer.database.RecordAction;
import com.rpooi3.explorer.home.HomePage;
import com.rpooi3.explorer.site.SiteSettings;
import com.rpooi3.explorer.unit.BrowserUnit;
import com.rpooi3.explorer.unit.HelperUtils;
import com.rpooi3.explorer.database.RecordUnit;
import com.rpooi3.explorer.view.CatToast;
import com.rpooi3.explorer.view.NinjaWebView;

public class NinjaWebViewClient extends WebViewClient {

    private static final String TAG = "NinjaWebViewClient";

    /** The link a store button becomes when the chrome.* bridge cannot take the install. */
    private static final String INSTALL_LINK = "explorer-install://";

    private final NinjaWebView ninjaWebView;
    private final Context context;
    private final SharedPreferences sp;

    public NinjaWebViewClient(NinjaWebView ninjaWebView) {
        this.ninjaWebView = ninjaWebView;
        this.context = ninjaWebView.getContext();
        this.sp = com.rpooi3.explorer.profile.ProfilePrefs.settings(context);
    }

    @Override
    public boolean onRenderProcessGone (WebView view, RenderProcessGoneDetail detail){
        String text = context.getString(R.string.app_error) + ": " + detail.toString();
        CatToast.show(context, text, 2);
        view.reload();
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);

        // The load is over: from here on WebView.getUrl() says where the tab is.
        ninjaWebView.setPendingUrl(null);

        if (ninjaWebView.isForeground()) ninjaWebView.invalidate();
        else ninjaWebView.postInvalidate();
        com.rpooi3.explorer.profile.ProfileWebView
                .cookieManager(ninjaWebView.getContext()).flush();

        // The address of a private tab is not written to the preferences.
        if (!ninjaWebView.isPrivateTab()) sp.edit().putString("mCurrentUrl", url).apply();
        // For "Automatically remove permissions": sites with a permission note when they were last used.
        if (!ninjaWebView.isPrivateTab()) SiteSettings.recordVisit(sp, url, System.currentTimeMillis());

        // 17.3.1: the content scripts of the extensions that ask for this page, and the button
        // that adds the extension of a Chrome Web Store page to Explorer.
        com.rpooi3.explorer.extension.ExtensionRuntime.onPageFinished(view, url);
        com.rpooi3.explorer.extension.StoreHook.onPageFinished(view, url);

        // Accessibility: "Force enable zoom" and the offer of the simplified view.
        com.rpooi3.explorer.accessibility.PageHooks.onPageFinished(ninjaWebView, url);

        // 16.1.0, item 20: the marked ads that are elements of the page itself (a picture, a
        // frame, a link) are hidden here, before the user sees them moving in.
        final String hideMarked = com.rpooi3.explorer.privacy.MarkedAds.hideScript(context);
        if (!hideMarked.isEmpty()) view.evaluateJavascript(hideMarked, null);

        // xGuard: the page is done, so its tracking analysis can be read (and kept, unless private).
        com.rpooi3.explorer.privacy.TrackingAnalysis.onPageFinished(ninjaWebView);

        if (ninjaWebView.isSaveData())
            view.evaluateJavascript("var links=document.getElementsByTagName('video'); for(let i=0;i<links.length;i++){links[i].pause()};", null);

        // The home page is not a website and does not belong in the history.
        // Neither are the offline notice and the game.
        if (ninjaWebView.isHistory() && !HomePage.isHomeUrl(ninjaWebView.getUrl()) && !ninjaWebView.isShowingOwnPage()) {
            RecordAction action = new RecordAction(ninjaWebView.getContext());
            action.open(true);
            // Every visit is kept (Firefox's Places database); the history list shows a page once, by its last visit.
            action.addHistory(new Record(ninjaWebView.getTitle(), ninjaWebView.getUrl(), System.currentTimeMillis(), 0));
            action.close();
        }

        String profile = NinjaWebView.getProfile();
        if (sp.getBoolean(profile + "_deny_cookie_banners",false)){
            //click opt-out if possible
            String bannerBlockScript = BannerBlock.getBannerBlockScriptPageFinished();
            if (bannerBlockScript != null) view.evaluateJavascript(bannerBlockScript,null);
        }
    }

    /**
     * A page could not be loaded. Without a connection the tab shows the
     * "No internet" notice with the dinosaur game; other errors keep the
     * WebView's own error page.
     */
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (null == request || !request.isForMainFrame() || null == request.getUrl()) return;
        onMainFrameError(view, request.getUrl().toString(), null == error ? null : error.getDescription());
    }

    /**
     * Answers a page that could not be loaded: with the game if its address
     * was asked for, with the offline notice if the connection is missing, and
     * otherwise — since 18.0.0 beta 4 — with Explorer's own error page, in the
     * colours of the phone, instead of the error page of Android System WebView.
     */
    public void onMainFrameError(final WebView view, final String failedUrl, final CharSequence description) {
        if (OfflinePage.isDinoUrl(failedUrl)) {
            // The WebView was asked for the game by its address, e.g. when going back to it.
            view.post(ninjaWebView::showDinoPage);
            return;
        }
        if (!failedUrl.startsWith("http:") && !failedUrl.startsWith("https:")) return;
        if (OfflinePage.isOfflineError(description, OfflinePage.isOffline(context))) {
            // Not from inside the callback: the failed load is still being wound up.
            view.post(() -> ninjaWebView.showOfflinePage(failedUrl));
            return;
        }
        // Every other error the WebView reports is answered by Explorer's own page as well: one that
        // says what happened, in the language of the app and in its colours.
        if (com.rpooi3.explorer.browser.ErrorPage.isWorthShowing(description)) {
            view.post(() -> {
                // A page that was already replaced by another load is not replaced again.
                final String shown = ninjaWebView.getUrl();
                if (null != shown && !shown.equals(failedUrl)) return;
                ninjaWebView.showErrorPage(failedUrl, description);
            });
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        // A new page: what was blocked on the page before is not counted for it.
        com.rpooi3.explorer.privacy.TrackingProtection.onPageStarted(ninjaWebView);
        com.rpooi3.explorer.privacy.TrackingAnalysis.onPageStarted(ninjaWebView);
        // 17.4.0: a page is told inside itself what its user agent said — Windows for a website in
        // desktop mode, Linux for the Chrome Web Store. Where the WebView has document-start
        // scripts this has already happened; coming twice is harmless.
        // 18.2.0 beta (item 2): the store keeps its Linux identity even when "Desktop site" is
        // turned off on it — its agent is the desktop one either way.
        if (ninjaWebView.isDesktopSite() || ninjaWebView.isChromeWebStore()) {
            final String agent = view.getSettings().getUserAgentString();
            com.rpooi3.explorer.browser.DesktopIdentity.applyNow(
                    view,
                    com.rpooi3.explorer.browser.ChromeUserAgent.majorVersion(agent),
                    ninjaWebView.isChromeWebStore()
                            ? com.rpooi3.explorer.browser.DesktopIdentity.LINUX
                            : com.rpooi3.explorer.browser.DesktopIdentity.WINDOWS);
        }
        // 17.3.3: the element rules of the lists, written into the page before it is painted —
        // Chromite's other half of blocking, which stops the ad that is served by the site itself.
        if (ninjaWebView.isAdBlock()) {
            AdBlocker.applyCosmetic(view, AdFilter.hostOf(url));
        }
        // 17.3.1: what an extension asks to run before the page is parsed.
        com.rpooi3.explorer.extension.ExtensionRuntime.onPageStarted(view, url);
        // 17.3.4: a page of an extension in a tab has the chrome.* interfaces of that extension.
        if (com.rpooi3.explorer.extension.ExtensionScripts.isExtensionUrl(url)) {
            com.rpooi3.explorer.extension.ExtensionRuntime.onExtensionPage(view, url);
        }
        // 17.3.2: the button of a Chrome Web Store page, as early as there is a page for it.
        com.rpooi3.explorer.extension.StoreHook.onPageStarted(view, url);

        // Hier definierst du alle Problem-Seiten, bei denen der Android-Treiber abstürzt
        boolean isProblemSite = url.contains("ntv.de") ||
                url.contains("n-tv.de") ||
                url.contains("rtl.de"); // Optional, da gleiches Mediennetzwerk

        // Das Skript läuft JETZT NUR, wenn wir uns auf einer dieser Problem-Seiten befinden
        if (isProblemSite) {
            view.evaluateJavascript(
                    "delete window.MediaKeys; " +
                            "delete window.WebKitMediaKeys; " +
                            "if (navigator.requestMediaKeySystemAccess) { " +
                            "  navigator.requestMediaKeySystemAccess = null; " +
                            "}", null);
        }

        super.onPageStarted(view, url, favicon);
        ninjaWebView.onLoadStarted();
        ninjaWebView.setPendingUrl(url);
        ninjaWebView.resetFavicon();

        String profile = NinjaWebView.getProfile();
        if (sp.getBoolean(profile + "_deny_cookie_banners",false)){
            //click opt-out if possible
            String bannerBlockScript = BannerBlock.getBannerBlockScriptPageStarted();
            if (bannerBlockScript != null) view.evaluateJavascript(bannerBlockScript,null);
        }

        if (ninjaWebView.isFingerPrintProtection()) {
            //Block WebRTC requests which can reveal local IP address
            //Tested with https://diafygi.github.io/webrtc-ips/
            view.evaluateJavascript("""
                    ['createOffer', 'createAnswer','setLocalDescription', 'setRemoteDescription'].forEach(function(method) {
                        webkitRTCPeerConnection.prototype[method] = function() {
                          console.log('webRTC snoop');
                          return null;
                        };
                      });""", null);

            //Prevent canvas fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "Canvas Fingerprint Defender", Firefox plugin, Version 0.1.9, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("""
                    
                      const toBlob = HTMLCanvasElement.prototype.toBlob;
                      const toDataURL = HTMLCanvasElement.prototype.toDataURL;
                      const getImageData = CanvasRenderingContext2D.prototype.getImageData;
                      //
                      var noisify = function (canvas, context) {
                        if (context) {
                          const shift = {
                            'r': Math.floor(Math.random() * 10) - 5,
                            'g': Math.floor(Math.random() * 10) - 5,
                            'b': Math.floor(Math.random() * 10) - 5,
                            'a': Math.floor(Math.random() * 10) - 5
                          };
                          //
                          const width = canvas.width;
                          const height = canvas.height;
                          if (width && height) {
                            const imageData = getImageData.apply(context, [0, 0, width, height]);
                            for (let i = 0; i < height; i++) {
                              for (let j = 0; j < width; j++) {
                                const n = ((i * (width * 4)) + (j * 4));
                                imageData.data[n + 0] = imageData.data[n + 0] + shift.r;
                                imageData.data[n + 1] = imageData.data[n + 1] + shift.g;
                                imageData.data[n + 2] = imageData.data[n + 2] + shift.b;
                                imageData.data[n + 3] = imageData.data[n + 3] + shift.a;
                              }
                            }
                            //
                            window.top.postMessage("canvas-fingerprint-defender-alert", '*');
                            context.putImageData(imageData, 0, 0);\s
                          }
                        }
                      };
                      //
                      Object.defineProperty(HTMLCanvasElement.prototype, "toBlob", {
                        "value": function () {
                          noisify(this, this.getContext("2d"));
                          return toBlob.apply(this, arguments);
                        }
                      });
                      //
                      Object.defineProperty(HTMLCanvasElement.prototype, "toDataURL", {
                        "value": function () {
                          noisify(this, this.getContext("2d"));
                          return toDataURL.apply(this, arguments);
                        }
                      });
                      //
                      Object.defineProperty(CanvasRenderingContext2D.prototype, "getImageData", {
                        "value": function () {
                          noisify(this.canvas, this);
                          return getImageData.apply(this, arguments);
                        }
                      });\
                    """, null);

            //Prevent WebGL fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "WebGL Fingerprint Defender", Firefox plugin, Version 0.1.5, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("""
                    
                      var config = {
                        "random": {
                          "value": function () {
                            return Math.random();
                          },
                          "item": function (e) {
                            var rand = e.length * config.random.value();
                            return e[Math.floor(rand)];
                          },
                          "number": function (power) {
                            var tmp = [];
                            for (var i = 0; i < power.length; i++) {
                              tmp.push(Math.pow(2, power[i]));
                            }
                            /*  */
                            return config.random.item(tmp);
                          },
                          "int": function (power) {
                            var tmp = [];
                            for (var i = 0; i < power.length; i++) {
                              var n = Math.pow(2, power[i]);
                              tmp.push(new Int32Array([n, n]));
                            }
                            /*  */
                            return config.random.item(tmp);
                          },
                          "float": function (power) {
                            var tmp = [];
                            for (var i = 0; i < power.length; i++) {
                              var n = Math.pow(2, power[i]);
                              tmp.push(new Float32Array([1, n]));
                            }
                            /*  */
                            return config.random.item(tmp);
                          }
                        },
                        "spoof": {
                          "webgl": {
                            "buffer": function (target) {
                              var proto = target.prototype ? target.prototype : target.__proto__;
                              const bufferData = proto.bufferData;
                              Object.defineProperty(proto, "bufferData", {
                                "value": function () {
                                  var index = Math.floor(config.random.value() * arguments[1].length);
                                  var noise = arguments[1][index] !== undefined ? 0.1 * config.random.value() * arguments[1][index] : 0;
                                  //
                                  arguments[1][index] = arguments[1][index] + noise;
                                  window.top.postMessage("webgl-fingerprint-defender-alert", '*');
                                  //
                                  return bufferData.apply(this, arguments);
                                }
                              });
                            },
                            "parameter": function (target) {
                              var proto = target.prototype ? target.prototype : target.__proto__;
                              const getParameter = proto.getParameter;
                              Object.defineProperty(proto, "getParameter", {
                                "value": function () {
                                  window.top.postMessage("webgl-fingerprint-defender-alert", '*');
                                  //
                                  if (arguments[0] === 3415) return 0;
                                  else if (arguments[0] === 3414) return 24;
                                  else if (arguments[0] === 36348) return 30;
                                  else if (arguments[0] === 7936) return "WebKit";
                                  else if (arguments[0] === 37445) return "Google Inc.";
                                  else if (arguments[0] === 7937) return "WebKit WebGL";
                                  else if (arguments[0] === 3379) return config.random.number([14, 15]);
                                  else if (arguments[0] === 36347) return config.random.number([12, 13]);
                                  else if (arguments[0] === 34076) return config.random.number([14, 15]);
                                  else if (arguments[0] === 34024) return config.random.number([14, 15]);
                                  else if (arguments[0] === 3386) return config.random.int([13, 14, 15]);
                                  else if (arguments[0] === 3413) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 3412) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 3411) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 3410) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 34047) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 34930) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 34921) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 35660) return config.random.number([1, 2, 3, 4]);
                                  else if (arguments[0] === 35661) return config.random.number([4, 5, 6, 7, 8]);
                                  else if (arguments[0] === 36349) return config.random.number([10, 11, 12, 13]);
                                  else if (arguments[0] === 33902) return config.random.float([0, 10, 11, 12, 13]);
                                  else if (arguments[0] === 33901) return config.random.float([0, 10, 11, 12, 13]);
                                  else if (arguments[0] === 37446) return config.random.item(["Graphics", "HD Graphics", "Intel(R) HD Graphics"]);
                                  else if (arguments[0] === 7938) return config.random.item(["WebGL 1.0", "WebGL 1.0 (OpenGL)", "WebGL 1.0 (OpenGL Chromium)"]);
                                  else if (arguments[0] === 35724) return config.random.item(["WebGL", "WebGL GLSL", "WebGL GLSL ES", "WebGL GLSL ES (OpenGL Chromium"]);
                                  //
                                  return getParameter.apply(this, arguments);
                                }
                              });
                            }
                          }
                        }
                      };
                      //
                      config.spoof.webgl.buffer(WebGLRenderingContext);
                      config.spoof.webgl.buffer(WebGL2RenderingContext);
                      config.spoof.webgl.parameter(WebGLRenderingContext);
                      config.spoof.webgl.parameter(WebGL2RenderingContext);\
                    """, null);

            //Prevent AudioContext fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "AudioContext Fingerprint Defender", Firefox plugin, Version 0.1.6, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("""
                    
                        const context = {
                        "BUFFER": null,
                        "getChannelData": function (e) {
                          const getChannelData = e.prototype.getChannelData;
                          Object.defineProperty(e.prototype, "getChannelData", {
                            "value": function () {
                              const results_1 = getChannelData.apply(this, arguments);
                              if (context.BUFFER !== results_1) {
                                context.BUFFER = results_1;
                                for (var i = 0; i < results_1.length; i += 100) {
                                  let index = Math.floor(Math.random() * i);
                                  results_1[index] = results_1[index] + Math.random() * 0.0000001;
                                }
                              }
                              //
                              return results_1;
                            }
                          });
                        },
                        "createAnalyser": function (e) {
                          const createAnalyser = e.prototype.__proto__.createAnalyser;
                          Object.defineProperty(e.prototype.__proto__, "createAnalyser", {
                            "value": function () {
                              const results_2 = createAnalyser.apply(this, arguments);
                              const getFloatFrequencyData = results_2.__proto__.getFloatFrequencyData;
                              Object.defineProperty(results_2.__proto__, "getFloatFrequencyData", {
                                "value": function () {
                                  const results_3 = getFloatFrequencyData.apply(this, arguments);
                                  for (var i = 0; i < arguments[0].length; i += 100) {
                                    let index = Math.floor(Math.random() * i);
                                    arguments[0][index] = arguments[0][index] + Math.random() * 0.1;
                                  }
                                  //
                                  return results_3;
                                }
                              });
                              //
                              return results_2;
                            }
                          });
                        }
                      };
                      //
                      context.getChannelData(AudioBuffer);
                      context.createAnalyser(AudioContext);
                      context.getChannelData(OfflineAudioContext);
                      context.createAnalyser(OfflineAudioContext);  \
                    """, null);

            //Prevent Font fingerprinting by randomizing
            //can be tested e.g. at https://webbrowsertools.com
            //
            //The Javascript part below is taken from "Font Fingerprint Defender", Firefox plugin, Version 0.1.3, by ilGur
            //The source code has been published originally under Mozilla Public License V2.0. You can obtain a copy of the license at https://mozilla.org/MPL/2.0/
            //The author has given explicit written permission to use his code under GPL V3 in this project.

            view.evaluateJavascript("""
                    
                      var rand = {
                        "noise": function () {
                          var SIGN = Math.random() < Math.random() ? -1 : 1;
                          return Math.floor(Math.random() + SIGN * Math.random());
                        },
                        "sign": function () {
                          const tmp = [-1, -1, -1, -1, -1, -1, +1, -1, -1, -1];
                          const index = Math.floor(Math.random() * tmp.length);
                          return tmp[index];
                        }
                      };
                      //
                      Object.defineProperty(HTMLElement.prototype, "offsetHeight", {
                        get () {
                          const height = Math.floor(this.getBoundingClientRect().height);
                          const valid = height && rand.sign() === 1;
                          const result = valid ? height + rand.noise() : height;
                          //
                          if (valid && result !== height) {
                            window.top.postMessage("font-fingerprint-defender-alert", '*');
                          }
                          //
                          return result;
                        }
                      });
                      //
                      Object.defineProperty(HTMLElement.prototype, "offsetWidth", {
                        get () {
                          const width = Math.floor(this.getBoundingClientRect().width);
                          const valid = width && rand.sign() === 1;
                          const result = valid ? width + rand.noise() : width;
                          //
                          if (valid && result !== width) {
                            window.top.postMessage("font-fingerprint-defender-alert", '*');
                          }
                          //
                          return result;
                        }
                      });\
                    """, null);

            //Spoof screen resolution, color depth: set values like in Tor browser, random values for device memory, hardwareConcurrency, remove battery, network connection, keyboard, media devices info, prevent sendBeacon
            //noinspection ConcatenationWithEmptyString
            view.evaluateJavascript("" +
                    "Object.defineProperty(window, 'devicePixelRatio',{value:1});" +
                    "Object.defineProperty(window.screen, 'width',{value:1000});" +
                    "Object.defineProperty(window.screen, 'availWidth',{value:1000});" +
                    "Object.defineProperty(window.screen, 'height',{value:900});" +
                    "Object.defineProperty(window.screen, 'availHeight',{value:900});" +
                    "Object.defineProperty(window.screen, 'colorDepth',{value:24});" +
                    "Object.defineProperty(window, 'outerWidth',{value:1000});" +
                    "Object.defineProperty(window, 'outerHeight',{value:900});" +
                    "Object.defineProperty(window, 'innerWidth',{value:1000});" +
                    "Object.defineProperty(window, 'innerHeight',{value:900});" +
                    "Object.defineProperty(navigator, 'getBattery',{value:function(){}});" +
                    "const ram=Math.pow(2,Math.floor(Math.random() * 4));Object.defineProperty(navigator, 'deviceMemory',{value:ram});" +
                    "const hw=Math.pow(2,Math.floor(Math.random() * 4));Object.defineProperty(navigator, 'hardwareConcurrency',{value:hw});" +
                    "Object.defineProperty(navigator, 'connection',{value:null});" +
                    "Object.defineProperty(navigator, 'keyboard',{value:null});" +
                    "Object.defineProperty(navigator, 'sendBeacon',{value:null});", null);

            if (!ninjaWebView.isCamera())
                //noinspection ConcatenationWithEmptyString
                view.evaluateJavascript("" + "Object.defineProperty(navigator, 'mediaDevices',{value:null});", null);
        }
    }

    @Override
    public void onLoadResource(WebView view, String url) {
        if (ninjaWebView.isFingerPrintProtection()) {
            view.evaluateJavascript("var test=document.querySelector(\"a[ping]\"); if(test!==null){test.removeAttribute('ping')};", null);
            //do not allow ping on http only pages (tested with http://tests.caniuse.com)
            if (view.getSettings().getUseWideViewPort() && (view.getWidth() < 1300)){
                view.evaluateJavascript("document.querySelector('meta[name=\"viewport\"]').setAttribute('content', 'width=1200px');", null);
            }
            //  Client-side detection for GlobalPrivacyControl
            view.evaluateJavascript("if (navigator.globalPrivacyControl === undefined) { Object.defineProperty(navigator, 'globalPrivacyControl', { value: true, writable: false,configurable: false});} else {try { navigator.globalPrivacyControl = true;} catch (e) { console.error('globalPrivacyControl is not writable: ', e); }};", null);
            //  Script taken from:
            //
            //  donotsell.js
            //  DuckDuckGo
            //
            //  Copyright © 2020 DuckDuckGo. All rights reserved.
            //
            //  Licensed under the Apache License, Version 2.0 (the "License");
            //  you may not use this file except in compliance with the License.
            //  You may obtain a copy of the License at
            //
            //  http://www.apache.org/licenses/LICENSE-2.0
            //
            //  Unless required by applicable law or agreed to in writing, software
            //  distributed under the License is distributed on an "AS IS" BASIS,
            //  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            //  See the License for the specific language governing permissions and
            //  limitations under the License.
            //
            view.evaluateJavascript("if (navigator.doNotTrack === null) { Object.defineProperty(navigator, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { navigator.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null);
            view.evaluateJavascript("if (window.doNotTrack === undefined) { Object.defineProperty(window, 'doNotTrack', { value: 1, writable: false,configurable: false});} else {try { window.doNotTrack = 1;} catch (e) { console.error('doNotTrack is not writable: ', e); }};", null);
            view.evaluateJavascript("if (navigator.msDoNotTrack === undefined) { Object.defineProperty(navigator, 'msDoNotTrack', { value: 1, writable: false,configurable: false});} else {try { navigator.msDoNotTrack = 1;} catch (e) { console.error('msDoNotTrack is not writable: ', e); }};", null);
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        final Uri uri = request.getUrl();
        String url = uri.toString();
        // 18.0.0 beta 3: the store page's own button, when the bridge could not take the install.
        // The script turns that tap into this link; the extension is then added through the very
        // code the three-dot menu uses, and the page stays where it is.
        if (url.startsWith(INSTALL_LINK)) {
            final String extensionId = url.substring(INSTALL_LINK.length());
            final Context host = view.getContext();
            if (host instanceof Activity) {
                final Activity activity = (Activity) host;
                activity.runOnUiThread(() ->
                        com.rpooi3.explorer.extension.ExtensionPrompt.request(
                                activity,
                                extensionId,
                                installed -> {
                                    if (null != installed) {
                                        try {
                                            view.reload();
                                        } catch (RuntimeException e) {
                                            Log.w(TAG, "Cannot reload after the install: " + e);
                                        }
                                    }
                                    return kotlin.Unit.INSTANCE;
                                }));
            } else {
                com.rpooi3.explorer.extension.StoreHook.install(host, extensionId, null);
            }
            return true;
        }
        if (OfflinePage.isDinoUrl(url)) {
            // The game is a page of this browser, not something for another app.
            view.loadUrl(OfflinePage.DINO_URL);
            return true;
        }
        if (url.startsWith("http:") || url.startsWith("https:")) {

            String mCurrentUrl = sp.getString("mCurrentUrl", "");
            if(url.equals(mCurrentUrl) && sp.getBoolean("backPressed", false)) {
                String historyUrl="";
                WebBackForwardList mWebBackForwardList = view.copyBackForwardList();
                if (mWebBackForwardList.getCurrentIndex() > 0) historyUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getCurrentIndex()-1).getUrl();
                view.loadUrl(historyUrl);
                sp.edit().putString("mCurrentUrl", "").apply();
                sp.edit().putBoolean("backPressed", false).apply();
            } else {
                view.loadUrl(url);
            }
            return false;
        } else{
            // 1. Prüfen, ob es sich um eine fehlerhafte lokale Google-Weiterleitung handelt
            if (url.startsWith("file:///httpservice/")) {
                // Ignorieren, da es ein ungültiger lokaler Pfad ist
                return true;
            }

            // 2. Sicherheits-Check für alle anderen file:// URLs
            if (url.startsWith("file://")) {
                return true;
            }

            // Normaler Ablauf für http/https Links
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            context.startActivity(Intent.createChooser(intent, url));
            return true;
        }
    }

    /** Serves the files of the extensions (see ExtensionFiles); built on first use. */
    private androidx.webkit.WebViewAssetLoader extensionFiles;

    private androidx.webkit.WebViewAssetLoader extensionFiles() {
        if (null == extensionFiles)
            extensionFiles = com.rpooi3.explorer.extension.ExtensionFiles.assetLoader(context);
        return extensionFiles;
    }

    /**
     * 17.3.2: the Chrome Web Store draws its pages itself, so going from one extension to the
     * next inside it loads no page and calls no onPageFinished. The button that adds the
     * extension is put in here as well.
     */
    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        super.doUpdateVisitedHistory(view, url, isReload);
        com.rpooi3.explorer.extension.StoreHook.onPageFinished(view, url);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        final String url = request.getUrl().toString();
        final String host = request.getUrl().getHost();
        // 17.3.1: the files of an extension (its popup, its stylesheet, what chrome.runtime
        // getURL() names) come from the extension's folder, not from the web.
        if (com.rpooi3.explorer.extension.ExtensionScripts.isExtensionUrl(url)) {
            return extensionFiles().shouldInterceptRequest(request.getUrl());
        }
        // xGuard's tracking analysis sees every request of the page, blocked or not.
        com.rpooi3.explorer.privacy.TrackingAnalysis.onRequest(ninjaWebView, url, request.isForMainFrame());

        // Site setting "Apps on device": a website does not get to load from this device itself.
        if (!request.isForMainFrame() && !ninjaWebView.mayLoadFrom(host))
            return new WebResourceResponse(
                    BrowserUnit.MIME_TYPE_TEXT_PLAIN,
                    BrowserUnit.URL_ENCODING,
                    403,
                    "Blocked",
                    java.util.Collections.emptyMap(),
                    new ByteArrayInputStream(new byte[0])
            );

        // 16.1.0, item 20: what the user marked as an ad is stopped like the filter list stops
        // an ad, on every site and whatever the site's protection is set to. A mark is an
        // instruction of the user, not a guess of the list.
        if (com.rpooi3.explorer.privacy.MarkedAds.blocks(context, url)) {
            com.rpooi3.explorer.privacy.TrackingProtection.onBlocked(ninjaWebView, url);
            com.rpooi3.explorer.privacy.TrackingAnalysis.onBlocked(ninjaWebView);
            return blockedResponse();
        }

        // xGuard, as the panel behind the shield of the top bar shows it: with the site's
        // protection on, the filter list of the ad blocker and the tracker blocker both work.
        if (ninjaWebView.isAdBlock()) {
            // 17.3.3: Chromite's blocker. The lists are Adblock Plus lists, so a rule can name a
            // path, a part of a host, or the kind of request, which a list of bare hosts cannot.
            if (AdBlocker.shouldBlock(url, host, request)) {
                // Counted for xGuard's panel.
                com.rpooi3.explorer.privacy.TrackingProtection.onBlocked(ninjaWebView, url);
                com.rpooi3.explorer.privacy.TrackingAnalysis.onBlocked(ninjaWebView);
                return blockedResponse();
            }
            // Trackers xGuard knows by name; which kinds are stopped is a setting of its own.
            final String kind = com.rpooi3.explorer.privacy.TrackerList.kindOf(context, host);
            if (kind != null) {
                com.rpooi3.explorer.privacy.TrackingAnalysis.onTracker(ninjaWebView, host, kind);
                if (com.rpooi3.explorer.privacy.TrackerList.isKindOn(sp, kind)) {
                    com.rpooi3.explorer.privacy.TrackingProtection.onBlocked(ninjaWebView, url);
                    com.rpooi3.explorer.privacy.TrackingAnalysis.onBlocked(ninjaWebView);
                    return blockedResponse();
                }
            }
        }
        return super.shouldInterceptRequest(view, request);
    }

    /** What a blocked request gets: an empty answer, so the page carries on without it. */
    private static WebResourceResponse blockedResponse() {
        return new WebResourceResponse(
                BrowserUnit.MIME_TYPE_TEXT_PLAIN,
                BrowserUnit.URL_ENCODING,
                new ByteArrayInputStream("".getBytes())
        );
    }
    @Override
    public void onFormResubmission(WebView view, @NonNull final Message doNotResend, final Message resend) {
        HelperUtils.showCustomMaterialDialogWithTwoActions(
                context,
                R.drawable.icon_alert,
                view.getTitle(),
                context.getString(R.string.dialog_content_resubmission),
                view.getUrl(),

                () -> {
                    resend.sendToTarget();
                    return true;
                },
                () -> {
                    doNotResend.sendToTarget();
                    return true;
                }
        );
    }

    /**
     * A certificate this device cannot trust.
     *
     * 18.0.0 beta 4: the site is not answered with a dialog any more. Firefox for Android, whose
     * shape Explorer follows, shows the warning as a page of its own — "Warning: Potential
     * Security Risk Ahead", with "Go Back" and, behind "Advanced", the error code and the way on.
     * Nothing of the site is loaded until the user says so, and if the tab goes somewhere else, the
     * waiting answer is dropped (see {@code NinjaWebView#loadUrl}).
     */
    @SuppressLint({"WebViewClientOnReceivedSslError", "SetJavaScriptEnabled"})
    @Override
    public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
        view.stopLoading();
        final String url = null != error.getUrl() && !error.getUrl().isEmpty()
                ? error.getUrl()
                : ninjaWebView.getUrl();
        if (null == url || url.isEmpty()) {
            // Nothing to warn about: the load is simply refused.
            handler.cancel();
            return;
        }
        final int primaryError = null == error ? SslError.SSL_UNTRUSTED : error.getPrimaryError();
        view.post(() -> ninjaWebView.showSslWarning(url, handler, primaryError));
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, @NonNull final HttpAuthHandler handler, String host, String realm) {
        // Gestoppt wird der Ladevorgang sofort, das ist gut!
        view.stopLoading();

        BrowserController browserController = NinjaWebView.getBrowserController();
        assert browserController != null;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.dialog_edit, null);

        TextInputLayout editBottomLayout = dialogView.findViewById(R.id.editBottomLayout);
        TextInputLayout editTopLayout = dialogView.findViewById(R.id.editTopLayout);
        editBottomLayout.setHint(this.context.getString(R.string.dialog_sign_in_password));
        editTopLayout.setHint(this.context.getString(R.string.dialog_sign_in_username));
        EditText editTop = dialogView.findViewById(R.id.editTop);
        EditText editBottom = dialogView.findViewById(R.id.editBottom);

        editTop.setText("");
        editBottom.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editBottom.setText("");

        builder.setPositiveButton(context.getString(R.string.app_ok), (dialog, which) -> {
            String user = Objects.requireNonNull(editTop.getText()).toString().trim();
            String pass = Objects.requireNonNull(editBottom.getText()).toString().trim();
            handler.proceed(user, pass);
            dialog.cancel();
        });
        builder.setNegativeButton(context.getString(R.string.app_cancel), (dialog, which) -> dialog.cancel());
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        Activity activity = HelperUtils.getActivity(ninjaWebView.getContext());
        if (activity != null) {
            HelperUtils.setupCardView(activity, dialogView, ninjaWebView.getTitle(), ninjaWebView.getUrl(), ninjaWebView, null, dialog, true);
        }
        HelperUtils.setupDialog(context, dialog);
        dialog.show();

        // WICHTIG: Wenn der Dialog abgebrochen (weggeklickt) wird, MUSS die WebView Bescheid wissen!
        dialog.setOnCancelListener(dialog1 -> {
            handler.cancel();
            dialog1.cancel();
        });
    }
}