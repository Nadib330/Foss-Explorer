package com.rpooi3.explorer.browser

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** The offline notice and the game page built from Chromium's network error page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class OfflinePageTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun theGamesAddress_isRecognisedInItsSpellings() {
        for (url in listOf("explorer://dino", "explorer://dino/", "EXPLORER://DINO", "  explorer://dino ")) {
            assertTrue(url, OfflinePage.isDinoUrl(url))
        }
        for (url in listOf(null, "", "explorer://", "explorer://dinosaur", "https://dino", "chrome://dino")) {
            assertFalse("$url", OfflinePage.isDinoUrl(url))
        }
    }

    @Test
    fun onlyAMissingConnection_countsAsOffline() {
        assertTrue(OfflinePage.isOfflineError("net::ERR_INTERNET_DISCONNECTED", false))
        // Errors that have other causes while the device is online.
        assertFalse(OfflinePage.isOfflineError("net::ERR_NAME_NOT_RESOLVED", false))
        assertFalse(OfflinePage.isOfflineError("net::ERR_CONNECTION_REFUSED", false))
        // Without a connection they are what a WebView reports for it.
        assertTrue(OfflinePage.isOfflineError("net::ERR_NAME_NOT_RESOLVED", true))
        assertTrue(OfflinePage.isOfflineError("net::ERR_ADDRESS_UNREACHABLE", true))
        // Never for errors of the site itself.
        assertFalse(OfflinePage.isOfflineError("net::ERR_CERT_DATE_INVALID", true))
        assertFalse(OfflinePage.isOfflineError(null, false))
    }

    @Test
    fun theNotice_saysWhatChromeSays() {
        val html = OfflinePage.offlineHtml(context, "https://example.org/page")

        assertTrue(html.contains("<span>No internet</span>"))
        assertTrue(html.contains("<p>Try:</p>"))
        for (line in listOf("Turning off airplane mode", "Turning on mobile data or Wi-Fi", "Checking the signal in your area")) {
            assertTrue(line, html.contains("<li>$line</li>"))
        }
        assertTrue(html.contains("""<div class="error-code">ERR_INTERNET_DISCONNECTED</div>"""))
        assertTrue(html.contains("<title>example.org</title>"))
        assertFalse("every placeholder was filled", Regex("""\{\{\w+\}\}""").containsMatchIn(html))
    }

    @Test
    fun theGamePage_hasNoErrorText() {
        val html = OfflinePage.dinoHtml(context)

        assertTrue(html.contains("<span>Press space to play</span>"))
        assertFalse(html.contains("ERR_INTERNET_DISCONNECTED</div>"))
        assertTrue(html.contains("""id="suggestions-list" style="display:none""""))
        assertFalse(Regex("""\{\{\w+\}\}""").containsMatchIn(html))
    }

    @Test
    fun thePage_carriesChromiumsGameUnchanged_andEverythingItNeeds() {
        val html = OfflinePage.dinoHtml(context)
        val script = File("../third_party/chromium-neterror/offline.js").readText()

        assertEquals("the copy is Chromium 86's offline.js", "82643", script.toByteArray().size.toString())
        assertTrue("and it is in the page byte for byte", html.contains(script))
        // What Runner looks up in the page.
        for (needle in listOf(
            """class="interstitial-wrapper"""", """class="icon icon-offline"""", """id="main-message"""",
            """id="offline-resources-1x" src="data:image/png;base64,""",
            """id="offline-resources-2x" src="data:image/png;base64,""",
            """<template id="audio-resources">""",
            """id="offline-sound-press" src="data:audio/mpeg;base64,""",
            """id="offline-sound-hit" src="data:audio/mpeg;base64,""",
            """id="offline-sound-reached" src="data:audio/mpeg;base64,""",
            "new Runner('.interstitial-wrapper')",
            // The game takes this constant from Chromium's neterror.js, which the page does not include.
            "const HIDDEN_CLASS = 'hidden';",
        )) {
            assertTrue(needle, html.contains(needle))
        }
        assertFalse("nothing is fetched from anywhere", Regex("""(src|href)="(?!data:)""").containsMatchIn(html))
        assertFalse(Regex("""url\((?!data:|\))""").containsMatchIn(html))
    }

    @Test
    fun theImagesInThePage_areChromiumsFiles() {
        val html = OfflinePage.dinoHtml(context)
        for (name in listOf("100-offline-sprite.png", "200-offline-sprite.png", "100-error-offline.png", "200-error-offline.png")) {
            val bytes = File("../third_party/chromium-neterror/$name").readBytes()
            val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
            assertTrue("$name ${sha256(bytes)}", html.contains(encoded))
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(12)
}
