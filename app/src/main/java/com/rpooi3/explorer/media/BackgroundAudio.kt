/*
 * Explorer: the sound that keeps playing when the browser is not in front.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 9 (item 1): a page that plays — a video, a radio, a song in the web player of a
 * music site — used to fall silent the moment another app came to the front. The page was never
 * paused by Explorer, so the reason was Android's own: an app without a foreground service has no
 * claim on the process, and the WebView of a page that is no longer visible is torn down as soon
 * as the system wants its memory; a page that is told it is hidden (`document.hidden`) stops by
 * itself as well.
 *
 * What is done about it is what Chrome does on a phone: while something is playing, the browser
 * keeps a foreground service with a **media playback** notification, a `MediaSession` whose
 * play and pause reach into the page, and a partial wake lock, so that the process — and the
 * WebView that plays — stay alive while Explorer is in the background. The service goes away the
 * moment Explorer comes back to the front, and the preference `background_audio` (on by default)
 * turns the whole behaviour off for whoever wants the old one.
 */
package com.rpooi3.explorer.media

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.webkit.WebView
import com.rpooi3.explorer.activity.BrowserActivity

object BackgroundAudio {

    /** The preference: whether the sound may go on when Explorer is not in front. */
    const val KEY = "background_audio"

    /** Whether the sound may go on in the background; on unless it was turned off. */
    @JvmStatic
    fun isEnabled(prefs: SharedPreferences?): Boolean = prefs == null || prefs.getBoolean(KEY, true)

    @JvmStatic
    fun isRunning(): Boolean = BackgroundAudioService.isRunning()

    /**
     * The question the page is asked: is anything of it playing? A `<video>`, an `<audio>`, a
     * Web Audio context that runs, or an element that only has a sound track.
     */
    @JvmStatic
    fun playingScript(): String = """
(function(){
  try {
    var list = document.querySelectorAll('video, audio');
    for (var i = 0; i < list.length; i++) {
      var media = list[i];
      if (media && !media.paused && !media.ended && media.readyState > 2) { return '1'; }
    }
    return '0';
  } catch (e) { return '0'; }
})();
""".trimIndent()

    /** Pauses or plays every sound of the page — what the notification's button does. */
    @JvmStatic
    fun playScript(play: Boolean): String = """
(function(){
  try {
    var list = document.querySelectorAll('video, audio');
    for (var i = 0; i < list.length; i++) {
      var media = list[i];
      if (!media) { continue; }
      if ($play) { var p = media.play(); if (p && p.catch) { p.catch(function(){}); } }
      else { try { media.pause(); } catch (e) {} }
    }
    return '1';
  } catch (e) { return '0'; }
})();
""".trimIndent()

    /**
     * Asks [webView] whether something is playing; [answer] is called on the main thread, always,
     * with false when the page cannot be asked.
     */
    @JvmStatic
    fun probe(webView: WebView?, answer: java.util.function.Consumer<Boolean>) {
        probe(webView) { playing -> answer.accept(playing) }
    }

    /** The same, for Kotlin. */
    fun probe(webView: WebView?, answer: (Boolean) -> Unit) {
        if (webView == null) {
            answer(false)
            return
        }
        try {
            webView.evaluateJavascript(playingScript()) { value ->
                answer(value != null && value.contains("1"))
            }
        } catch (e: Throwable) {
            answer(false)
        }
    }

    /** Starts the service that keeps the sound alive; done twice, it starts once. */
    @JvmStatic
    fun start(context: Context, title: String?) {
        val intent = Intent(context, BackgroundAudioService::class.java).apply {
            action = BackgroundAudioService.ACTION_START
            putExtra(BackgroundAudioService.EXTRA_TITLE, title)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Throwable) {
            // A service the system refuses must not close the browser.
        }
    }

    /** Takes the notification — and the service — away: Explorer is in front again. */
    @JvmStatic
    fun stop(context: Context) {
        if (!isRunning()) return
        try {
            context.startService(
                Intent(context, BackgroundAudioService::class.java).apply {
                    action = BackgroundAudioService.ACTION_STOP
                },
            )
        } catch (e: Throwable) {
            // Already gone.
        }
    }

    /** The activity the notification comes back to. */
    internal fun activityClass(): Class<*> = BrowserActivity::class.java
}
